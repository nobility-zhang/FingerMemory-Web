package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BattleApiIT {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM fm_battle_event");
        jdbcTemplate.update("DELETE FROM fm_battle_receipt");
        jdbcTemplate.update("DELETE FROM fm_battle_learning_task");
        jdbcTemplate.update("DELETE FROM fm_battle");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        jdbcTemplate.update("DELETE FROM fm_gm_action_receipt");
        jdbcTemplate.update("DELETE FROM fm_gm_audit_event");
        jdbcTemplate.update("DELETE FROM fm_review_receipt");
        jdbcTemplate.update("DELETE FROM fm_reward_event");
        jdbcTemplate.update("DELETE FROM fm_migration_receipt");
        jdbcTemplate.update("DELETE FROM fm_backup_receipt");
        jdbcTemplate.update("DELETE FROM fm_import_id_map");
        jdbcTemplate.update("DELETE FROM fm_admin_bootstrap_receipt");
        jdbcTemplate.update("DELETE FROM fm_user_data");
        jdbcTemplate.update("DELETE FROM fm_lexicon");
        jdbcTemplate.update("DELETE FROM fm_user_preferences");
        jdbcTemplate.update("DELETE FROM fm_user");
    }

    @Test
    void commitsLearningEnergyCardsSkillAndTurnTransitionIdempotently() throws Exception {
        RegisteredUser user = registerAndLogin("battle-user");
        String lexiconId = createLexicon(user.session(), "战斗词库");
        CreatedStudyItem first = createWord(
                user.session(), lexiconId, "memory", "记忆");
        createWord(user.session(), lexiconId, "promise", "承诺");
        SessionEnvelope session = createSession(user.session(), first.dataVersion());

        ObjectNode createBody = createBattleBody(
                "battle-owner-1",
                session.sessionId(),
                first.studyItemId(),
                "serin");
        MvcResult created = createBattle(
                user.session(), "battle-create-1", createBody, 201)
                .andExpect(jsonPath("$.stateVersion").value(0))
                .andExpect(jsonPath("$.battle.phase").value("learning"))
                .andExpect(jsonPath("$.battle.energy").value(0))
                .andExpect(jsonPath("$.battle.learningTask.energyGranted").value(false))
                .andReturn();
        String firstResponse = created.getResponse().getContentAsString();
        JsonNode createdTree = objectMapper.readTree(firstResponse);
        String firstCardId = createdTree.path("battle").path("hand").get(0)
                .path("instanceId").asText();

        createBattle(user.session(), "battle-create-1", createBody, 201);
        ObjectNode changedCreate = createBody.deepCopy();
        changedCreate.put("companionKey", "lia");
        createBattle(user.session(), "battle-create-1", changedCreate, 409)
                .andExpect(jsonPath("$.code").value("BATTLE_ACTION_CONFLICT"));

        ObjectNode duplicateTask = createBattleBody(
                "battle-owner-2",
                session.sessionId(),
                first.studyItemId(),
                "lia");
        createBattle(user.session(), "battle-create-2", duplicateTask, 409)
                .andExpect(jsonPath("$.code").value("BATTLE_LEARNING_TASK_CONFLICT"));

        PracticeReady practice = submitFormalPractice(
                user.session(),
                session.sessionId(),
                first.studyItemId(),
                "battle-attempt-1",
                "battle-review-1");
        submitReview(
                user.session(),
                session.sessionId(),
                "battle-review-1",
                practice,
                200);

        jdbcTemplate.update("""
                INSERT INTO fm_reward_event (
                  owner_id, event_type, event_key, reward_eligible, created_at_ms, payload_json
                ) VALUES (?, 'battle-energy', 'battle-review-1', 1, ?, '{}')
                """, user.id(), System.currentTimeMillis());

        claimLearning(
                user.session(),
                "battle-owner-1",
                "battle-claim-rollback",
                "battle-review-1",
                0,
                409)
                .andExpect(jsonPath("$.code").value("BATTLE_ENERGY_CONFLICT"));
        getBattle(user.session(), "battle-owner-1")
                .andExpect(jsonPath("$.stateVersion").value(0))
                .andExpect(jsonPath("$.battle.phase").value("learning"))
                .andExpect(jsonPath("$.battle.energy").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_battle_receipt WHERE action_id = 'battle-claim-rollback'",
                Integer.class)).isZero();
        jdbcTemplate.update(
                "DELETE FROM fm_reward_event WHERE owner_id = ? AND event_type = 'battle-energy'",
                user.id());

        MvcResult claimed = claimLearning(
                user.session(),
                "battle-owner-1",
                "battle-claim-1",
                "battle-review-1",
                0,
                200)
                .andExpect(jsonPath("$.stateVersion").value(1))
                .andExpect(jsonPath("$.battle.phase").value("player-action"))
                .andExpect(jsonPath("$.battle.energy").value(3))
                .andExpect(jsonPath("$.battle.learningTask.energyGranted").value(true))
                .andReturn();
        String claimedResponse = claimed.getResponse().getContentAsString();
        claimLearning(
                user.session(),
                "battle-owner-1",
                "battle-claim-1",
                "battle-review-1",
                0,
                200);
        assertThat(objectMapper.readTree(claimedResponse)).isEqualTo(objectMapper.readTree(
                claimLearning(
                        user.session(),
                        "battle-owner-1",
                        "battle-claim-1",
                        "battle-review-1",
                        0,
                        200)
                        .andReturn()
                        .getResponse()
                        .getContentAsString()));
        claimLearning(
                user.session(),
                "battle-owner-1",
                "battle-claim-2",
                "battle-review-1",
                1,
                409)
                .andExpect(jsonPath("$.code").value("LEARNING_ALREADY_CLAIMED"));

        ObjectNode playBody = objectMapper.createObjectNode();
        playBody.put("expectedStateVersion", 1);
        playBody.put("cardInstanceId", firstCardId);
        playBattleCommand(
                user.session(),
                "battle-owner-1",
                "cards",
                "battle-play-1",
                playBody,
                200)
                .andExpect(jsonPath("$.stateVersion").value(2))
                .andExpect(jsonPath("$.battle.party.block").value(5))
                .andExpect(jsonPath("$.battle.energy").value(2));
        playBattleCommand(
                user.session(),
                "battle-owner-1",
                "cards",
                "battle-play-stale",
                playBody,
                409)
                .andExpect(jsonPath("$.code").value("BATTLE_VERSION_CONFLICT"));

        ObjectNode skillBody = objectMapper.createObjectNode();
        skillBody.put("expectedStateVersion", 2);
        playBattleCommand(
                user.session(),
                "battle-owner-1",
                "companion-skill",
                "battle-skill-1",
                skillBody,
                200)
                .andExpect(jsonPath("$.stateVersion").value(3))
                .andExpect(jsonPath("$.battle.party.block").value(13))
                .andExpect(jsonPath("$.battle.companion.skillUsed").value(true));
        skillBody.put("expectedStateVersion", 3);
        playBattleCommand(
                user.session(),
                "battle-owner-1",
                "companion-skill",
                "battle-skill-2",
                skillBody,
                409)
                .andExpect(jsonPath("$.code").value("BATTLE_RULE_VIOLATION"));

        SessionEnvelope currentSession = getSession(user.session(), session.sessionId());
        ObjectNode endTurnBody = objectMapper.createObjectNode();
        endTurnBody.put("expectedStateVersion", 3);
        endTurnBody.put("nextSessionId", session.sessionId());
        endTurnBody.put("nextStudyItemId", currentSession.studyItemId());
        playBattleCommand(
                user.session(),
                "battle-owner-1",
                "end-turn",
                "battle-end-turn-1",
                endTurnBody,
                200)
                .andExpect(jsonPath("$.stateVersion").value(4))
                .andExpect(jsonPath("$.battle.phase").value("learning"))
                .andExpect(jsonPath("$.battle.turnId").value(2))
                .andExpect(jsonPath("$.battle.energy").value(0))
                .andExpect(jsonPath("$.battle.currentIntent.type").value("defend"));

        MvcResult battle = getBattle(user.session(), "battle-owner-1")
                .andExpect(jsonPath("$.stateVersion").value(4))
                .andReturn();
        JsonNode battleEvents = objectMapper.readTree(
                battle.getResponse().getContentAsString()).path("events");
        assertThat(battleEvents.size()).isGreaterThan(10);
        assertThat(battleEvents.get(battleEvents.size() - 1).path("eventSequence").asInt())
                .isEqualTo(battleEvents.size());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_battle_learning_task WHERE owner_id = ?",
                Integer.class, user.id())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event WHERE owner_id = ? AND event_type = 'battle-energy'",
                Integer.class, user.id())).isEqualTo(1);

        RegisteredUser other = registerAndLogin("battle-other");
        mockMvc.perform(get("/api/v1/battles/battle-owner-1")
                        .cookie(other.session().cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BATTLE_NOT_FOUND"));
    }

    @Test
    void returnsTheSameReceiptForConcurrentIdenticalLearningClaims() throws Exception {
        PreparedBattle battle = prepareBattleReady("battle-concurrent-replay");
        Csrf csrf = csrf(battle.user().session());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<MvcResult> first = executor.submit(() -> claimAfterLatch(
                    start,
                    csrf,
                    "battle-battle-concurrent-replay",
                    "battle-concurrent-replay-review",
                    "battle-concurrent-replay-1"));
            Future<MvcResult> second = executor.submit(() -> claimAfterLatch(
                    start,
                    csrf,
                    "battle-battle-concurrent-replay",
                    "battle-concurrent-replay-review",
                    "battle-concurrent-replay-1"));
            start.countDown();

            MvcResult firstResult = first.get(20, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(secondResult.getResponse().getContentAsString()))
                    .isEqualTo(objectMapper.readTree(firstResult.getResponse().getContentAsString()));
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT state_version FROM fm_battle WHERE owner_id = ?",
                Long.class,
                battle.user().id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event "
                        + "WHERE owner_id = ? AND event_type = 'battle-energy'",
                Integer.class,
                battle.user().id())).isEqualTo(1);
    }

    @Test
    void rejectsAConcurrentClaimOnceTheFirstClaimAdvancesTheBattleVersion() throws Exception {
        PreparedBattle battle = prepareBattleReady("battle-concurrent-conflict");
        Csrf csrf = csrf(battle.user().session());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<MvcResult> first = executor.submit(() -> claimAfterLatch(
                    start,
                    csrf,
                    "battle-battle-concurrent-conflict",
                    "battle-concurrent-conflict-review",
                    "battle-concurrent-conflict-1"));
            Future<MvcResult> second = executor.submit(() -> claimAfterLatch(
                    start,
                    csrf,
                    "battle-battle-concurrent-conflict",
                    "battle-concurrent-conflict-review",
                    "battle-concurrent-conflict-2"));
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(20, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(20, TimeUnit.SECONDS).getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT state_version FROM fm_battle WHERE owner_id = ?",
                Long.class,
                battle.user().id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event "
                        + "WHERE owner_id = ? AND event_type = 'battle-energy'",
                Integer.class,
                battle.user().id())).isEqualTo(1);
    }

    private PreparedBattle prepareBattleReady(String username) throws Exception {
        RegisteredUser user = registerAndLogin(username);
        String lexiconId = createLexicon(user.session(), "并发战斗词库");
        CreatedStudyItem first = createWord(
                user.session(), lexiconId, "memory", "记忆");
        createWord(user.session(), lexiconId, "promise", "承诺");
        SessionEnvelope session = createSession(user.session(), first.dataVersion());
        createBattle(
                user.session(),
                username + "-create",
                createBattleBody("battle-" + username, session.sessionId(), first.studyItemId(), "lia"),
                201);
        PracticeReady practice = submitFormalPractice(
                user.session(),
                session.sessionId(),
                first.studyItemId(),
                username + "-attempt",
                username + "-review");
        submitReview(
                user.session(),
                session.sessionId(),
                username + "-review",
                practice,
                200);
        return new PreparedBattle(user, session);
    }

    private MvcResult claimAfterLatch(
            CountDownLatch start,
            Csrf csrf,
            String battleId,
            String submissionId,
            String actionId) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expectedStateVersion", 0);
        body.put("submissionId", submissionId);
        return mockMvc.perform(post(
                                "/api/v1/battles/" + battleId + "/learning-claims")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private PracticeReady submitFormalPractice(
            SessionCookie session,
            String sessionId,
            String studyItemId,
            String attemptId,
            String reviewId) throws Exception {
        SessionEnvelope current = getSession(session, sessionId);
        ObjectNode responseBody = objectMapper.createObjectNode();
        responseBody.put("practiceAttemptId", attemptId);
        responseBody.put("reviewSubmissionId", reviewId);
        responseBody.put("studyItemId", studyItemId);
        responseBody.put("response", "memory");
        responseBody.put("answerWasHidden", true);
        responseBody.put("hintUsed", false);
        responseBody.put("answerRevealed", false);
        responseBody.put("assessmentType", "typed-recall");
        responseBody.put("assessmentPolicyVersion", 2);
        responseBody.put("assessmentStage", "attempt");
        responseBody.put("priorExposure", false);
        responseBody.put("explicitNotRemembered", false);
        responseBody.put("abandoned", false);
        responseBody.put("expectedDataVersion", current.dataVersion());
        responseBody.put("expectedSessionVersion", current.sessionVersion());
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post(
                                "/api/v1/sessions/" + sessionId + "/responses")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(responseBody)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return new PracticeReady(
                response.path("dataVersion").asLong(),
                response.path("session").path("version").asLong());
    }

    private void submitReview(
            SessionCookie session,
            String sessionId,
            String reviewId,
            PracticeReady practice,
            int expectedStatus) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("rating", "good");
        body.put("expectedDataVersion", practice.dataVersion());
        body.put("expectedReviewVersion", 0);
        body.put("expectedSessionVersion", practice.sessionVersion());
        ObjectNode evidence = body.putObject("evidence");
        evidence.put("userConfirmedRating", true);
        evidence.put("answerWasHidden", true);
        evidence.put("hintUsed", false);
        evidence.put("answerRevealed", false);
        evidence.put("assessmentType", "typed-recall");
        evidence.put("assessmentPolicyVersion", 2);
        evidence.put("assessmentStage", "attempt");
        evidence.put("priorExposure", false);
        evidence.put("explicitNotRemembered", false);
        evidence.put("abandoned", false);
        Csrf csrf = csrf(session);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/reviews")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", reviewId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions createBattle(
            SessionCookie session,
            String actionId,
            ObjectNode body,
            int expectedStatus) throws Exception {
        Csrf csrf = csrf(session);
        return mockMvc.perform(post("/api/v1/battles")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions claimLearning(
            SessionCookie session,
            String battleId,
            String actionId,
            String submissionId,
            long expectedVersion,
            int expectedStatus) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expectedStateVersion", expectedVersion);
        body.put("submissionId", submissionId);
        return playBattleCommand(
                session, battleId, "learning-claims", actionId, body, expectedStatus);
    }

    private org.springframework.test.web.servlet.ResultActions playBattleCommand(
            SessionCookie session,
            String battleId,
            String suffix,
            String actionId,
            ObjectNode body,
            int expectedStatus) throws Exception {
        Csrf csrf = csrf(session);
        return mockMvc.perform(post(
                                "/api/v1/battles/" + battleId + "/" + suffix)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions getBattle(
            SessionCookie session,
            String battleId) throws Exception {
        return mockMvc.perform(get("/api/v1/battles/" + battleId)
                        .cookie(session.cookie()))
                .andExpect(status().isOk());
    }

    private ObjectNode createBattleBody(
            String battleId,
            String sessionId,
            String studyItemId,
            String companionKey) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("battleId", battleId);
        body.put("encounterId", "bridge-guardian");
        body.put("sessionId", sessionId);
        body.put("studyItemId", studyItemId);
        body.put("companionKey", companionKey);
        body.put("seed", 42);
        body.put("shuffle", false);
        return body;
    }

    private SessionEnvelope getSession(SessionCookie session, String sessionId)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                        .cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return new SessionEnvelope(
                sessionId,
                response.path("dataVersion").asLong(),
                response.path("session").path("version").asLong(),
                response.path("session").path("currentStudyItemId").asText());
    }

    private SessionEnvelope createSession(
            SessionCookie session,
            long expectedMinimumVersion) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("modeId", "keyboard-recall");
        body.put("timeZone", "UTC");
        body.put("newLimit", 10);
        body.put("reviewLimit", 50);
        body.put("reduceMotion", false);
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/sessions")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        long dataVersion = response.path("dataVersion").asLong();
        assertThat(dataVersion).isGreaterThanOrEqualTo(expectedMinimumVersion);
        return new SessionEnvelope(
                response.path("session").path("id").asText(),
                dataVersion,
                response.path("session").path("version").asLong(),
                response.path("session").path("currentStudyItemId").asText());
    }

    private CreatedStudyItem createWord(
            SessionCookie session,
            String lexiconId,
            String term,
            String meaning) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("term", term);
        body.put("meaning", meaning);
        body.putArray("acceptedAnswers").add(term);
        body.putArray("hints");
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post(
                                "/api/v1/lexicons/" + lexiconId + "/words")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return new CreatedStudyItem(
                response.path("studyItem").path("id").asText(),
                response.path("dataVersion").asLong());
    }

    private String createLexicon(SessionCookie session, String name)
            throws Exception {
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/lexicons")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"battle integration test"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private RegisteredUser registerAndLogin(String username) throws Exception {
        Csrf registrationCsrf = csrf(null);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"correct horse battery"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = JsonPath.read(
                registration.getResponse().getContentAsString(), "$.id");
        return new RegisteredUser(userId, login(username));
    }

    private SessionCookie login(String username) throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null) {
            throw new AssertionError("FM_SESSION missing");
        }
        return new SessionCookie(cookie);
    }

    private Csrf csrf(SessionCookie existing) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (existing != null) {
            request.cookie(existing.cookie());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null && existing != null) {
            cookie = existing.cookie();
        }
        if (cookie == null) {
            throw new AssertionError("FM_SESSION missing");
        }
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString());
        return new Csrf(
                new SessionCookie(cookie),
                body.path("headerName").asText(),
                body.path("token").asText());
    }

    private record RegisteredUser(String id, SessionCookie session) {
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(
            SessionCookie session,
            String headerName,
            String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }

    private record CreatedStudyItem(String studyItemId, long dataVersion) {
    }

    private record SessionEnvelope(
            String sessionId,
            long dataVersion,
            long sessionVersion,
            String studyItemId) {
    }

    private record PracticeReady(long dataVersion, long sessionVersion) {
    }

    private record PreparedBattle(RegisteredUser user, SessionEnvelope session) {
    }
}
