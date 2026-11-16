package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
class OnlineLearningIT {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
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
    void completesAnIdempotentFormalReviewAndRejectsAnOldReviewVersion() throws Exception {
        RegisteredUser user = registerAndLogin("review-user");
        String lexiconId = createLexicon(user.session(), "在线复习词库");
        CreatedStudyItem created = createWord(
                user.session(), lexiconId, "memory", "记忆");

        SessionEnvelope sessionA = createSession(user.session(), created.dataVersion());
        SessionEnvelope sessionB = createSession(user.session(), sessionA.dataVersion());
        String sessionAId = sessionA.session().path("id").asText();
        String sessionBId = sessionB.session().path("id").asText();

        SessionEnvelope currentA = getSession(user.session(), sessionAId);
        PracticePending practiceA = submitResponse(
                user.session(),
                sessionAId,
                created.studyItemId(),
                "memory",
                "attempt-a",
                "review-a",
                currentA.dataVersion(),
                currentA.session().path("version").asLong());

        SessionEnvelope currentB = getSession(user.session(), sessionBId);
        PracticePending practiceB = submitResponse(
                user.session(),
                sessionBId,
                created.studyItemId(),
                "memory",
                "attempt-b",
                "review-b",
                currentB.dataVersion(),
                currentB.session().path("version").asLong());

        SessionEnvelope reviewReadyA = getSession(user.session(), sessionAId);
        ObjectNode previewBody = objectMapper.createObjectNode();
        previewBody.put("studyItemId", created.studyItemId());
        previewBody.put("expectedDataVersion", reviewReadyA.dataVersion());
        previewBody.put("expectedSessionVersion",
                reviewReadyA.session().path("version").asLong());
        previewBody.put("expectedReviewVersion", 0);
        Csrf previewCsrf = csrf(user.session());
        mockMvc.perform(post("/api/v1/sessions/" + sessionAId + "/review-preview")
                        .cookie(previewCsrf.cookie())
                        .header(previewCsrf.headerName(), previewCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previews.again.dueAt").isNumber())
                .andExpect(jsonPath("$.previews.hard.dueAt").isNumber())
                .andExpect(jsonPath("$.previews.good.dueAt").isNumber())
                .andExpect(jsonPath("$.previews.easy.dueAt").isNumber());
        ObjectNode reviewA = reviewBody(
                "good",
                reviewReadyA.dataVersion(),
                0,
                reviewReadyA.session().path("version").asLong());
        MvcResult firstReview = submitReview(
                user.session(), sessionAId, "review-a", reviewA, 200)
                .andExpect(jsonPath("$.outcome").value("created"))
                .andExpect(jsonPath("$.state.version").value(1))
                .andExpect(jsonPath("$.log.submissionId").value("review-a"))
                .andReturn();
        ObjectNode firstReviewResponse = (ObjectNode) objectMapper.readTree(
                firstReview.getResponse().getContentAsString());

        MvcResult duplicate = submitReview(
                user.session(), sessionAId, "review-a", reviewA, 200)
                .andExpect(jsonPath("$.outcome").value("created"))
                .andReturn();
        assertThat(objectMapper.readTree(duplicate.getResponse().getContentAsString()))
                .isEqualTo(firstReviewResponse);

        ObjectNode conflictingReview = reviewA.deepCopy();
        conflictingReview.put("rating", "hard");
        submitReview(user.session(), sessionAId, "review-a", conflictingReview, 409)
                .andExpect(jsonPath("$.code").value("REVIEW_SUBMISSION_CONFLICT"));

        SessionEnvelope reviewReadyB = getSession(user.session(), sessionBId);
        assertThat(practiceB.sessionVersion()).isEqualTo(1);
        assertThat(practiceA.sessionVersion()).isEqualTo(1);
        ObjectNode staleReview = reviewBody(
                "good",
                reviewReadyB.dataVersion(),
                0,
                reviewReadyB.session().path("version").asLong());
        submitReview(user.session(), sessionBId, "review-b", staleReview, 409)
                .andExpect(jsonPath("$.code").value("REVIEW_VERSION_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_review_receipt WHERE owner_id = ?",
                Integer.class, user.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event WHERE owner_id = ? AND reward_eligible = 1",
                Integer.class, user.id())).isEqualTo(1);
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM fm_user_data WHERE owner_id = ?",
                String.class, user.id());
        JsonNode tables = objectMapper.readTree(payload).path("tables");
        assertThat(tables.path("reviewStates")).hasSize(1);
        assertThat(tables.path("reviewLogs")).hasSize(1);
        assertThat(tables.path("reviewStates").get(0).path("version").asLong()).isEqualTo(1);
    }

    @Test
    void importsLocalV4IdempotentlyAndMapsIdsPerUser() throws Exception {
        RegisteredUser alice = registerAndLogin("migration-alice");
        ObjectNode snapshot = loadSnapshot();
        ObjectNode previewRequest = objectMapper.createObjectNode();
        previewRequest.set("snapshot", snapshot);
        Csrf previewCsrf = csrf(alice.session());
        MvcResult preview = mockMvc.perform(post("/api/v1/migrations/local/preview")
                        .cookie(previewCsrf.cookie())
                        .header(previewCsrf.headerName(), previewCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.lexicons").value(1))
                .andExpect(jsonPath("$.counts.words").value(1))
                .andExpect(jsonPath("$.schedulerWeights").value(34))
                .andReturn();
        String previewFingerprint = JsonPath.read(
                preview.getResponse().getContentAsString(), "$.snapshotFingerprint");

        MvcResult imported = importSnapshot(alice.session(), "local-batch-1", snapshot, 201)
                .andExpect(jsonPath("$.snapshotFingerprint").value(previewFingerprint))
                .andExpect(jsonPath("$.idMappings").isNumber())
                .andReturn();
        Number importedVersionValue = JsonPath.read(
                imported.getResponse().getContentAsString(), "$.dataVersion");
        long importedVersion = importedVersionValue.longValue();
        Number mappingsValue = JsonPath.read(
                imported.getResponse().getContentAsString(), "$.idMappings");
        int mappings = mappingsValue.intValue();
        String aliceLexiconId = jdbcTemplate.queryForObject(
                "SELECT id FROM fm_lexicon WHERE owner_id = ?",
                String.class, alice.id());
        assertThat(aliceLexiconId).startsWith("m");

        importSnapshot(alice.session(), "local-batch-1", snapshot, 201)
                .andExpect(jsonPath("$.dataVersion").value(importedVersion))
                .andExpect(jsonPath("$.idMappings").value(mappings));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon WHERE owner_id = ?",
                Integer.class, alice.id())).isEqualTo(1);

        ObjectNode changed = snapshot.deepCopy();
        ((ObjectNode) changed.path("tables").path("notes").get(0))
                .put("content", "changed but same batch");
        ObjectNode changedRequest = objectMapper.createObjectNode();
        changedRequest.set("snapshot", changed);
        Csrf changedCsrf = csrf(alice.session());
        mockMvc.perform(post("/api/v1/migrations/local")
                        .cookie(changedCsrf.cookie())
                        .header(changedCsrf.headerName(), changedCsrf.token())
                        .header("Idempotency-Key", "local-batch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changedRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MIGRATION_BATCH_CONFLICT"));

        RegisteredUser bob = registerAndLogin("migration-bob");
        importSnapshot(bob.session(), "local-batch-1", snapshot, 201);
        String bobLexiconId = jdbcTemplate.queryForObject(
                "SELECT id FROM fm_lexicon WHERE owner_id = ?",
                String.class, bob.id());
        assertThat(bobLexiconId).isNotEqualTo(aliceLexiconId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon WHERE owner_id IN (?, ?)",
                Integer.class, alice.id(), bob.id())).isEqualTo(2);
    }

    @Test
    void exportsAndRestoresOnlyTheCurrentUsersData() throws Exception {
        RegisteredUser alice = registerAndLogin("backup-alice");
        ObjectNode snapshot = loadSnapshot();
        importSnapshot(alice.session(), "backup-source-batch", snapshot, 201);

        MvcResult exported = mockMvc.perform(get("/api/v1/backup/export")
                        .cookie(alice.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupVersion").value(1))
                .andExpect(jsonPath("$.tables.lexicons").isArray())
                .andExpect(jsonPath("$.tables.lexicons.length()").value(1))
                .andReturn();
        ObjectNode backup = (ObjectNode) objectMapper.readTree(
                exported.getResponse().getContentAsString());

        String extraLexiconId = createLexicon(alice.session(), "恢复时应移除");
        createWord(alice.session(), extraLexiconId, "temporary", "临时的");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon WHERE owner_id = ?",
                Integer.class, alice.id())).isEqualTo(2);

        ObjectNode previewRequest = objectMapper.createObjectNode();
        previewRequest.set("backup", backup);
        Csrf previewCsrf = csrf(alice.session());
        mockMvc.perform(post("/api/v1/backup/import/preview")
                        .cookie(previewCsrf.cookie())
                        .header(previewCsrf.headerName(), previewCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.lexicons").value(1))
                .andExpect(jsonPath("$.counts.words").value(1));

        MvcResult restored = restoreBackup(
                alice.session(), "backup-restore-1", backup, 201)
                .andExpect(jsonPath("$.outcome").value("restored"))
                .andExpect(jsonPath("$.counts.lexicons").value(1))
                .andReturn();
        Number restoredVersionValue = JsonPath.read(
                restored.getResponse().getContentAsString(), "$.dataVersion");
        long restoredVersion = restoredVersionValue.longValue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon WHERE owner_id = ?",
                Integer.class, alice.id())).isEqualTo(1);

        restoreBackup(alice.session(), "backup-restore-1", backup, 201)
                .andExpect(jsonPath("$.dataVersion").value(restoredVersion));

        ObjectNode invalid = backup.deepCopy();
        ((ObjectNode) invalid.path("tables").path("studyItems").get(0))
                .put("wordEntryId", "missing-word");
        restoreBackup(alice.session(), "backup-invalid", invalid, 400)
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon WHERE owner_id = ?",
                Integer.class, alice.id())).isEqualTo(1);

        RegisteredUser bob = registerAndLogin("backup-bob");
        mockMvc.perform(get("/api/v1/backup/export").cookie(bob.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tables.lexicons.length()").value(0))
                .andExpect(jsonPath("$.tables.words.length()").value(0));
        mockMvc.perform(get("/api/v1/lexicons").cookie(bob.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rollsBackStateLogAndSessionWhenRewardPersistenceFails() throws Exception {
        RegisteredUser user = registerAndLogin("rollback-user");
        String lexiconId = createLexicon(user.session(), "事务回滚词库");
        CreatedStudyItem created = createWord(
                user.session(), lexiconId, "rollback", "回滚");
        SessionEnvelope session = createSession(user.session(), created.dataVersion());
        String sessionId = session.session().path("id").asText();
        SessionEnvelope current = getSession(user.session(), sessionId);
        submitResponse(
                user.session(),
                sessionId,
                created.studyItemId(),
                "rollback",
                "rollback-attempt",
                "rollback-review",
                current.dataVersion(),
                current.session().path("version").asLong());
        SessionEnvelope reviewReady = getSession(user.session(), sessionId);
        jdbcTemplate.update("""
                INSERT INTO fm_reward_event (
                  owner_id, event_type, event_key, reward_eligible, created_at_ms, payload_json
                ) VALUES (?, 'review', 'rollback-review', 1, ?, '{}')
                """, user.id(), System.currentTimeMillis());

        ObjectNode body = reviewBody(
                "good",
                reviewReady.dataVersion(),
                0,
                reviewReady.session().path("version").asLong());
        submitReview(user.session(), sessionId, "rollback-review", body, 409)
                .andExpect(jsonPath("$.code").value("REWARD_EVENT_CONFLICT"));

        JsonNode payload = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT payload_json FROM fm_user_data WHERE owner_id = ?",
                String.class, user.id()));
        JsonNode tables = payload.path("tables");
        assertThat(tables.path("reviewStates")).isEmpty();
        assertThat(tables.path("reviewLogs")).isEmpty();
        JsonNode persistedSession = tables.path("practiceSessions").get(0);
        assertThat(persistedSession.path("version").asLong()).isEqualTo(1);
        assertThat(persistedSession.path("pendingAnswer").path("reviewSubmissionId").asText())
                .isEqualTo("rollback-review");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_review_receipt WHERE owner_id = ?",
                Integer.class, user.id())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event WHERE owner_id = ?",
                Integer.class, user.id())).isEqualTo(1);
    }

    @Test
    void migratesExistingReviewStateAndLogWithoutGrantingImportRewards() throws Exception {
        RegisteredUser source = registerAndLogin("migration-review-source");
        String lexiconId = createLexicon(source.session(), "带历史的本地词库");
        CreatedStudyItem created = createWord(
                source.session(), lexiconId, "history", "历史");
        SessionEnvelope session = createSession(source.session(), created.dataVersion());
        String sessionId = session.session().path("id").asText();
        SessionEnvelope current = getSession(source.session(), sessionId);
        submitResponse(
                source.session(),
                sessionId,
                created.studyItemId(),
                "history",
                "history-attempt",
                "history-review",
                current.dataVersion(),
                current.session().path("version").asLong());
        SessionEnvelope ready = getSession(source.session(), sessionId);
        submitReview(
                source.session(),
                sessionId,
                "history-review",
                reviewBody("good", ready.dataVersion(), 0,
                        ready.session().path("version").asLong()),
                200);

        MvcResult exported = mockMvc.perform(get("/api/v1/backup/export")
                        .cookie(source.session().cookie()))
                .andExpect(status().isOk())
                .andReturn();
        ObjectNode backup = (ObjectNode) objectMapper.readTree(
                exported.getResponse().getContentAsString());
        ObjectNode localSnapshot = accountBackupAsLocalSnapshot(backup);

        RegisteredUser target = registerAndLogin("migration-review-target");
        importSnapshot(target.session(), "history-local-batch", localSnapshot, 201)
                .andExpect(jsonPath("$.importedCounts.reviewStates").value(1))
                .andExpect(jsonPath("$.importedCounts.reviewLogs").value(1));
        JsonNode targetData = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT payload_json FROM fm_user_data WHERE owner_id = ?",
                String.class, target.id())).path("tables");
        assertThat(targetData.path("reviewStates")).hasSize(1);
        assertThat(targetData.path("reviewLogs")).hasSize(1);
        assertThat(targetData.path("reviewStates").get(0).path("version").asLong()).isEqualTo(1);
        assertThat(targetData.path("reviewLogs").get(0).path("schedulerModelVersion").asText())
                .isEqualTo("fsrs-7");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event WHERE owner_id = ? AND reward_eligible = 0",
                Integer.class, target.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_review_receipt WHERE owner_id = ?",
                Integer.class, target.id())).isEqualTo(1);
    }

    @Test
    void completesHintedPracticeWithoutWritingFormalReviewStateOrReward() throws Exception {
        RegisteredUser user = registerAndLogin("practice-only-user");
        String lexiconId = createLexicon(user.session(), "练习门禁词库");
        CreatedStudyItem created = createWord(
                user.session(), lexiconId, "reveal", "揭示");
        SessionEnvelope session = createSession(user.session(), created.dataVersion());
        String sessionId = session.session().path("id").asText();
        SessionEnvelope current = getSession(user.session(), sessionId);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("practiceAttemptId", "practice-only-attempt");
        body.put("studyItemId", created.studyItemId());
        body.put("response", "reveal");
        body.put("answerWasHidden", true);
        body.put("hintUsed", true);
        body.put("answerRevealed", true);
        body.put("abandoned", false);
        body.put("outcome", "completed");
        body.put("expectedDataVersion", current.dataVersion());
        body.put("expectedSessionVersion", current.session().path("version").asLong());
        Csrf csrf = csrf(user.session());
        MvcResult first = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/practice")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attempt.hintUsed").value(true))
                .andExpect(jsonPath("$.attempt.answerRevealed").value(true))
                .andExpect(jsonPath("$.session.status").value("completed"))
                .andReturn();
        long firstVersion = objectMapper.readTree(
                first.getResponse().getContentAsString()).path("dataVersion").asLong();

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/practice")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataVersion").value(firstVersion));

        JsonNode tables = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT payload_json FROM fm_user_data WHERE owner_id = ?",
                String.class, user.id())).path("tables");
        assertThat(tables.path("practiceAttempts")).hasSize(1);
        assertThat(tables.path("reviewStates")).isEmpty();
        assertThat(tables.path("reviewLogs")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_reward_event WHERE owner_id = ?",
                Integer.class, user.id())).isZero();
    }

    private ObjectNode reviewBody(String rating, long dataVersion,
                                  long reviewVersion, long sessionVersion) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("rating", rating);
        body.put("expectedDataVersion", dataVersion);
        body.put("expectedReviewVersion", reviewVersion);
        body.put("expectedSessionVersion", sessionVersion);
        ObjectNode evidence = body.putObject("evidence");
        evidence.put("userConfirmedRating", true);
        evidence.put("answerWasHidden", true);
        evidence.put("hintUsed", false);
        evidence.put("answerRevealed", false);
        evidence.put("abandoned", false);
        return body;
    }

    private org.springframework.test.web.servlet.ResultActions submitReview(
            SessionCookie session,
            String sessionId,
            String submissionId,
            ObjectNode body,
            int expectedStatus) throws Exception {
        Csrf csrf = csrf(session);
        return mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/reviews")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", submissionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions importSnapshot(
            SessionCookie session, String batchId, ObjectNode snapshot, int expectedStatus)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("snapshot", snapshot);
        Csrf csrf = csrf(session);
        return mockMvc.perform(post("/api/v1/migrations/local")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions restoreBackup(
            SessionCookie session, String key, ObjectNode backup, int expectedStatus)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("backup", backup);
        Csrf csrf = csrf(session);
        return mockMvc.perform(post("/api/v1/backup/import")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private PracticePending submitResponse(
            SessionCookie session,
            String sessionId,
            String studyItemId,
            String response,
            String attemptId,
            String reviewSubmissionId,
            long dataVersion,
            long sessionVersion) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("practiceAttemptId", attemptId);
        body.put("reviewSubmissionId", reviewSubmissionId);
        body.put("studyItemId", studyItemId);
        body.put("response", response);
        body.put("answerWasHidden", true);
        body.put("hintUsed", false);
        body.put("answerRevealed", false);
        body.put("abandoned", false);
        body.put("expectedDataVersion", dataVersion);
        body.put("expectedSessionVersion", sessionVersion);
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/responses")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pendingAnswer.reviewSubmissionId")
                        .value(reviewSubmissionId))
                .andReturn();
        JsonNode responseBody = objectMapper.readTree(result.getResponse().getContentAsString());
        return new PracticePending(
                responseBody.path("session").path("version").asLong(),
                responseBody.path("dataVersion").asLong(),
                (ObjectNode) responseBody.path("pendingAnswer"));
    }

    private SessionEnvelope createSession(SessionCookie session, long expectedMinimumVersion)
            throws Exception {
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
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("dataVersion").asLong()).isGreaterThanOrEqualTo(expectedMinimumVersion);
        return new SessionEnvelope(
                (ObjectNode) response.path("session"), response.path("dataVersion").asLong());
    }

    private SessionEnvelope getSession(SessionCookie session, String sessionId)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/sessions/" + sessionId)
                        .cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new SessionEnvelope(
                (ObjectNode) response.path("session"), response.path("dataVersion").asLong());
    }

    private CreatedStudyItem createWord(
            SessionCookie session, String lexiconId, String term, String meaning)
            throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("term", term);
        body.put("meaning", meaning);
        body.putArray("acceptedAnswers").add(term);
        body.putArray("hints");
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/lexicons/" + lexiconId + "/words")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new CreatedStudyItem(
                response.path("studyItem").path("id").asText(),
                response.path("dataVersion").asLong());
    }

    private String createLexicon(SessionCookie session, String name) throws Exception {
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/lexicons")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"P2 online test"}
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
        String userId = JsonPath.read(registration.getResponse().getContentAsString(), "$.id");
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
        String body = result.getResponse().getContentAsString();
        return new Csrf(
                new SessionCookie(cookie),
                JsonPath.read(body, "$.headerName"),
                JsonPath.read(body, "$.token"));
    }

    private ObjectNode loadSnapshot() throws Exception {
        return (ObjectNode) objectMapper.readTree(
                Files.readString(Path.of("src/test/resources/migration/local-v4.json")));
    }

    private ObjectNode accountBackupAsLocalSnapshot(ObjectNode backup) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("snapshotVersion", 1);
        snapshot.put("schemaVersion", 4);
        snapshot.put("dataFormatVersion", 4);
        snapshot.put("exportedAt", backup.path("exportedAt").asLong());
        ObjectNode sourceTables = (ObjectNode) backup.path("tables");
        ObjectNode tables = snapshot.putObject("tables");
        for (String table : List.of(
                "appMeta", "words", "studyItems", "lexiconItems", "notes",
                "practiceAttempts", "practiceSessions", "reviewStates", "reviewLogs")) {
            ArrayNode target = tables.putArray(table);
            sourceTables.path(table).forEach(value -> target.add(value.deepCopy()));
        }
        ArrayNode appMeta = (ArrayNode) tables.get("appMeta");
        boolean hasDataFormat = false;
        for (JsonNode record : appMeta) {
            hasDataFormat = hasDataFormat
                    || "data.formatVersion".equals(record.path("key").asText());
        }
        if (!hasDataFormat) {
            ObjectNode dataFormat = appMeta.addObject();
            dataFormat.put("key", "data.formatVersion");
            dataFormat.put("value", "4");
            dataFormat.put("updatedAt", backup.path("exportedAt").asLong());
        }
        ArrayNode lexicons = tables.putArray("lexicons");
        for (JsonNode source : sourceTables.path("lexicons")) {
            ObjectNode lexicon = lexicons.addObject();
            lexicon.put("id", source.path("id").asText());
            lexicon.put("name", source.path("name").asText());
            lexicon.put("description", source.path("description").asText());
            lexicon.put("createdAt", source.path("createdAt").asLong());
            lexicon.put("updatedAt", source.path("updatedAt").asLong());
            JsonNode archivedAt = source.get("archivedAt");
            if (archivedAt == null || archivedAt.isNull()) {
                lexicon.putNull("archivedAt");
            } else {
                lexicon.put("archivedAt", archivedAt.asLong());
            }
        }
        return snapshot;
    }

    private record RegisteredUser(String id, SessionCookie session) {
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie session, String headerName, String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }

    private record CreatedStudyItem(String studyItemId, long dataVersion) {
    }

    private record SessionEnvelope(ObjectNode session, long dataVersion) {
    }

    private record PracticePending(long sessionVersion, long dataVersion, ObjectNode pending) {
    }
}
