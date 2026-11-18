package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fingermemory.server.config.AppProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdventureApiIT {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void cleanDatabaseAndStorage() throws Exception {
        jdbcTemplate.update("DELETE FROM fm_adventure_recollection");
        jdbcTemplate.update("DELETE FROM fm_adventure_character_unlock");
        jdbcTemplate.update("DELETE FROM fm_adventure_reward_offer");
        jdbcTemplate.update("DELETE FROM fm_adventure_event");
        jdbcTemplate.update("DELETE FROM fm_adventure_receipt");
        jdbcTemplate.update("DELETE FROM fm_adventure_battle_binding");
        jdbcTemplate.update("DELETE FROM fm_adventure_run");
        jdbcTemplate.update("DELETE FROM fm_battle_event");
        jdbcTemplate.update("DELETE FROM fm_battle_receipt");
        jdbcTemplate.update("DELETE FROM fm_battle_learning_task");
        jdbcTemplate.update("DELETE FROM fm_battle");
        jdbcTemplate.update("DELETE FROM fm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_content_installation_receipt");
        jdbcTemplate.update("DELETE FROM fm_content_installation");
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
        deleteRecursively(Path.of(appProperties.content().packageRoot()));
    }

    @Test
    void runsChapterThroughVictoryWithIdempotentReceiptsAndOwnerIsolation()
            throws Exception {
        RegisteredUser alice = registerAndLogin("adventure-alice");
        Csrf csrf = csrf(alice.session());
        InstalledPackage v1 = install(csrf, "暖灯与失语之森", "1.0.0", "v1");

        MvcResult started = start(csrf, v1.id(), "serin", "start-chapter-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("created"))
                .andExpect(jsonPath("$.run.companionKey").value("serin"))
                .andExpect(jsonPath("$.run.contentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.run.contentHash").value(v1.hash()))
                .andExpect(jsonPath("$.run.currentNode.nodeId").value("inn.arrival"))
                .andReturn();
        String runId = JsonPath.read(
                started.getResponse().getContentAsString(),
                "$.run.id");
        String sessionId = JsonPath.read(
                started.getResponse().getContentAsString(),
                "$.run.sessionId");

        start(csrf, v1.id(), "serin", "start-chapter-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.id").value(runId));
        start(csrf, v1.id(), "lia", "start-chapter-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_IDEMPOTENCY_CONFLICT"));
        start(csrf, v1.id(), "serin", "start-other-key")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("existing"))
                .andExpect(jsonPath("$.run.id").value(runId));

        action(csrf, runId, "chapter-choice", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("inn.preparation"))
                .andExpect(jsonPath("$.run.runVersion").value(1));
        action(csrf, runId, "chapter-choice", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.runVersion").value(1));
        action(csrf, runId, "chapter-choice", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "other"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_IDEMPOTENCY_CONFLICT"));
        action(csrf, runId, "chapter-advance", Map.of(
                "expectedVersion", 1,
                "action", "advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("forest.signpost"))
                .andExpect(jsonPath("$.run.runVersion").value(2));
        action(csrf, runId, "chapter-cross", Map.of(
                "expectedVersion", 2,
                "action", "choose",
                "choiceId", "cross"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("bridge.guardian"))
                .andExpect(jsonPath("$.run.runVersion").value(3));

        MvcResult encounter = mockMvc.perform(post(
                        "/api/v1/adventures/{runId}/encounters",
                        runId)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "chapter-encounter"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("started"))
                .andExpect(jsonPath("$.runVersion").value(4))
                .andReturn();
        String battleId = JsonPath.read(
                encounter.getResponse().getContentAsString(),
                "$.battleId");

        mockMvc.perform(post("/api/v1/adventures/{runId}/encounters", runId)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "chapter-encounter"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.battleId").value(battleId));
        mockMvc.perform(post("/api/v1/adventures/{runId}/encounters", runId)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "chapter-encounter-other"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("existing"))
                .andExpect(jsonPath("$.battleId").value(battleId));

        action(csrf, runId, "resolve-too-early", Map.of(
                "expectedVersion", 4,
                "action", "resolve-encounter",
                "battleId", battleId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("ADVENTURE_ENCOUNTER_UNRESOLVED"));

        setBattleTerminal(alice.id(), battleId, "victory");
        action(csrf, runId, "resolve-victory", Map.of(
                "expectedVersion", 4,
                "action", "resolve-encounter",
                "battleId", battleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("bridge.crossed"))
                .andExpect(jsonPath("$.run.battleId").doesNotExist())
                .andExpect(jsonPath("$.run.runVersion").value(5));
        action(csrf, runId, "resolve-victory", Map.of(
                "expectedVersion", 4,
                "action", "resolve-encounter",
                "battleId", battleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("bridge.crossed"));

        action(csrf, runId, "to-companion", Map.of(
                "expectedVersion", 5,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("camp.letter"));
        action(csrf, runId, "to-camp", Map.of(
                "expectedVersion", 6,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("camp.fire"));
        action(csrf, runId, "to-ending", Map.of(
                "expectedVersion", 7,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter1.end"));
        action(csrf, runId, "finish-chapter", Map.of(
                "expectedVersion", 8,
                "action", "complete"))
                .andExpect(jsonPath("$.run.status").value("completed"))
                .andExpect(jsonPath("$.run.runVersion").value(9));

        mockMvc.perform(get("/api/v1/adventures").cookie(alice.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/adventures/{runId}", runId)
                        .cookie(alice.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
        mockMvc.perform(get("/api/v1/sessions/{id}", sessionId)
                        .cookie(alice.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.id").value(sessionId));

        RegisteredUser bob = registerAndLogin("adventure-bob");
        mockMvc.perform(get("/api/v1/adventures/{runId}", runId)
                        .cookie(bob.session().cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADVENTURE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/adventures").cookie(bob.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_adventure_event WHERE owner_id = ? AND run_id = ?",
                Integer.class,
                alice.id(),
                runId)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_adventure_receipt WHERE owner_id = ? AND run_id = ?",
                Integer.class,
                alice.id(),
                runId)).isEqualTo(10);
    }

    @Test
    void keepsRunLockedToInstalledVersionAndFollowsRescueBranch() throws Exception {
        RegisteredUser user = registerAndLogin("adventure-version");
        Csrf csrf = csrf(user.session());
        InstalledPackage v1 = install(csrf, "暖灯旧版", "1.0.0", "v1");
        InstalledPackage v2 = install(csrf, "暖灯新版", "1.1.0", "v2");

        String oldRunId = JsonPath.read(start(
                        csrf,
                        v1.id(),
                        "lia",
                        "version-old-start")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.run.id");
        String newRunId = JsonPath.read(start(
                        csrf,
                        v2.id(),
                        "lia",
                        "version-new-start")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.run.id");

        mockMvc.perform(get("/api/v1/adventures/{runId}", oldRunId)
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion").value("1.0.0"))
                .andExpect(jsonPath("$.contentHash").value(v1.hash()))
                .andExpect(jsonPath("$.currentNode.text").value("莉娅把旧版暖灯递给你。"));
        mockMvc.perform(get("/api/v1/adventures/{runId}", newRunId)
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentVersion").value("1.1.0"))
                .andExpect(jsonPath("$.contentHash").value(v2.hash()))
                .andExpect(jsonPath("$.currentNode.text").value("莉娅把新版暖灯递给你。"));

        action(csrf, newRunId, "rescue-choice", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "prepare"))
                .andExpect(status().isOk());
        action(csrf, newRunId, "rescue-advance", Map.of(
                "expectedVersion", 1,
                "action", "advance"))
                .andExpect(status().isOk());
        action(csrf, newRunId, "rescue-cross", Map.of(
                "expectedVersion", 2,
                "action", "choose",
                "choiceId", "cross"))
                .andExpect(status().isOk());
        String battleId = JsonPath.read(mockMvc.perform(post(
                                "/api/v1/adventures/{runId}/encounters",
                                newRunId)
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .header("Idempotency-Key", "rescue-encounter"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.battleId");
        setBattleTerminal(user.id(), battleId, "defeat");
        action(csrf, newRunId, "rescue-resolve", Map.of(
                "expectedVersion", 4,
                "action", "resolve-encounter",
                "battleId", battleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("bridge.assisted"))
                .andExpect(jsonPath(
                        "$.run.flags['encounter.bridge-guardian.assisted']")
                        .value(true));

        action(csrf, newRunId, "stale-action", Map.of(
                "expectedVersion", 4,
                "action", "advance"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_VERSION_CONFLICT"));
    }

    @Test
    void completesThreeChaptersWithRewardDeckFinaUnlockAndDedupedRecollections()
            throws Exception {
        RegisteredUser user = registerAndLogin("adventure-three-chapters");
        Csrf csrf = csrf(user.session());
        MvcResult installed = mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(new MockMultipartFile(
                                "file",
                                "warm-lantern-2.0.0.fmbook",
                                "application/zip",
                                zip(progressPackageFiles())))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "install-three-chapters"))
                .andExpect(status().isCreated())
                .andReturn();
        String installationId = JsonPath.read(
                installed.getResponse().getContentAsString(),
                "$.installation.id");

        mockMvc.perform(get("/api/v1/adventures/companions")
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(
                        org.hamcrest.Matchers.contains("lia", "serin")));

        MvcResult started = start(
                csrf,
                installationId,
                "serin",
                "three-chapter-start")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.deck.length()").value(12))
                .andExpect(jsonPath("$.run.unlockedCharacters").value(
                        org.hamcrest.Matchers.contains("lia", "serin")))
                .andReturn();
        String runId = JsonPath.read(
                started.getResponse().getContentAsString(),
                "$.run.id");
        String sessionId = JsonPath.read(
                started.getResponse().getContentAsString(),
                "$.run.sessionId");

        action(csrf, runId, "three-chapter-prepare", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("three.camp1"));
        action(csrf, runId, "three-chapter-to-bridge", Map.of(
                "expectedVersion", 1,
                "action", "advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("three.bridge"));

        String firstBattle = startEncounter(csrf, runId, "three-battle-1", 2);
        setBattleTerminal(user.id(), firstBattle, "victory");
        action(csrf, runId, "three-resolve-1", Map.of(
                "expectedVersion", 3,
                "action", "resolve-encounter",
                "battleId", firstBattle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("three.bridge"))
                .andExpect(jsonPath("$.run.rewardOffer.choices.length()")
                        .value(3))
                .andExpect(jsonPath("$.run.rewardOffer.choices[0].choiceId")
                        .value("herb-pouch"));
        action(csrf, runId, "three-blocked-by-reward", Map.of(
                "expectedVersion", 4,
                "action", "advance"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADVENTURE_REWARD_PENDING"));
        action(csrf, runId, "three-claim-1", Map.of(
                "expectedVersion", 4,
                "action", "claim-reward",
                "choiceId", "party-strike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("three.after-bridge"))
                .andExpect(jsonPath("$.run.deck.length()").value(13))
                .andExpect(jsonPath("$.run.deck[12]").value("party-strike"))
                .andExpect(jsonPath("$.run.rewardOffer").doesNotExist());
        advanceSessionStudyItem(user.id(), sessionId);

        action(csrf, runId, "three-to-end-1", Map.of(
                "expectedVersion", 5,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("three.end1"));
        action(csrf, runId, "three-complete-1", Map.of(
                "expectedVersion", 6,
                "action", "complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("active"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.start"))
                .andExpect(jsonPath("$.run.unlockedCharacters").value(
                        org.hamcrest.Matchers.hasItem("fina")));

        mockMvc.perform(get("/api/v1/adventures/companions")
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(
                        org.hamcrest.Matchers.hasItem("fina")));

        action(csrf, runId, "three-to-camp-2", Map.of(
                "expectedVersion", 7,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.camp"));
        action(csrf, runId, "three-switch-fina", Map.of(
                "expectedVersion", 8,
                "action", "select-companion",
                "companionKey", "fina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.companionKey").value("fina"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.camp"));

        action(csrf, runId, "three-to-bridge-2", Map.of(
                "expectedVersion", 9,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.bridge"));
        String secondBattle = startEncounter(csrf, runId, "three-battle-2", 10);
        setBattleTerminal(user.id(), secondBattle, "victory");
        action(csrf, runId, "three-resolve-2", Map.of(
                "expectedVersion", 11,
                "action", "resolve-encounter",
                "battleId", secondBattle))
                .andExpect(jsonPath("$.run.rewardOffer.choices.length()")
                        .value(3));
        action(csrf, runId, "three-claim-2", Map.of(
                "expectedVersion", 12,
                "action", "claim-reward",
                "choiceId", "observe-signpost"))
                .andExpect(jsonPath("$.run.deck.length()").value(14))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.after"));
        advanceSessionStudyItem(user.id(), sessionId);
        action(csrf, runId, "three-complete-2", Map.of(
                "expectedVersion", 13,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter2.end"));
        action(csrf, runId, "three-complete-2-end", Map.of(
                "expectedVersion", 14,
                "action", "complete"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter3.start"));

        action(csrf, runId, "three-to-bridge-3", Map.of(
                "expectedVersion", 15,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter3.bridge"));
        String thirdBattle = startEncounter(csrf, runId, "three-battle-3", 16);
        setBattleTerminal(user.id(), thirdBattle, "victory");
        action(csrf, runId, "three-resolve-3", Map.of(
                "expectedVersion", 17,
                "action", "resolve-encounter",
                "battleId", thirdBattle))
                .andExpect(jsonPath("$.run.rewardOffer.choices.length()")
                        .value(3));
        action(csrf, runId, "three-claim-3", Map.of(
                "expectedVersion", 18,
                "action", "claim-reward",
                "choiceId", "herb-pouch"))
                .andExpect(jsonPath("$.run.deck.length()").value(15))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter3.after"));
        action(csrf, runId, "three-to-final-end", Map.of(
                "expectedVersion", 19,
                "action", "advance"))
                .andExpect(jsonPath("$.run.currentNode.nodeId")
                        .value("chapter3.end"));
        action(csrf, runId, "three-complete-book", Map.of(
                "expectedVersion", 20,
                "action", "complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.run.status").value("completed"));

        mockMvc.perform(get("/api/v1/adventures/recollections")
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        MvcResult replay = start(
                csrf,
                installationId,
                "fina",
                "three-chapter-replay")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.run.companionKey").value("fina"))
                .andExpect(jsonPath("$.run.deck.length()").value(12))
                .andReturn();
        String replayRunId = JsonPath.read(
                replay.getResponse().getContentAsString(),
                "$.run.id");
        action(csrf, replayRunId, "replay-prepare", Map.of(
                "expectedVersion", 0,
                "action", "choose",
                "choiceId", "prepare"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/adventures/recollections")
                        .cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    private org.springframework.test.web.servlet.ResultActions start(
            Csrf csrf,
            String installationId,
            String companionKey,
            String actionId) throws Exception {
        return mockMvc.perform(post("/api/v1/adventures")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .header("Idempotency-Key", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "installationId", installationId,
                        "companionKey", companionKey,
                        "reduceMotion", true))));
    }

    private org.springframework.test.web.servlet.ResultActions action(
            Csrf csrf,
            String runId,
            String actionId,
            Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/adventures/{runId}/actions", runId)
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .header("Idempotency-Key", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private String startEncounter(
            Csrf csrf,
            String runId,
            String actionId,
            long runVersion) throws Exception {
        return JsonPath.read(mockMvc.perform(post(
                                "/api/v1/adventures/{runId}/encounters",
                                runId)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", actionId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runVersion").value(runVersion + 1))
                .andReturn()
                .getResponse()
                .getContentAsString(),
                "$.battleId");
    }

    private InstalledPackage install(
            Csrf csrf,
            String title,
            String version,
            String variant) throws Exception {
        MvcResult installed = mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(fmbook(title, version, variant))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "install-" + variant))
                .andExpect(status().isCreated())
                .andReturn();
        String body = installed.getResponse().getContentAsString();
        return new InstalledPackage(
                JsonPath.read(body, "$.installation.id"),
                JsonPath.read(body, "$.installation.contentHash"));
    }

    private void setBattleTerminal(
            String ownerId,
            String battleId,
            String terminal) {
        int updated = jdbcTemplate.update(
                """
                UPDATE fm_battle
                SET state_json = JSON_SET(
                    state_json,
                    '$.phase', ?,
                    '$.terminal', ?
                )
                WHERE owner_id = ? AND battle_id = ?
                """,
                terminal,
                terminal,
                ownerId,
                battleId);
        assertThat(updated).isEqualTo(1);
    }

    private void advanceSessionStudyItem(String ownerId, String sessionId) {
        String raw = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM fm_user_data WHERE owner_id = ?",
                String.class,
                ownerId);
        ObjectNode document = (ObjectNode) objectMapper.readTree(raw);
        ObjectNode session = null;
        for (var value : document.path("tables").path("practiceSessions")) {
            if (value.isObject()
                    && sessionId.equals(value.path("id").asText())) {
                session = (ObjectNode) value;
                break;
            }
        }
        assertThat(session).isNotNull();
        int currentIndex = session.path("currentIndex").asInt();
        var queue = session.path("queue");
        ObjectNode current = (ObjectNode) queue.get(currentIndex);
        current.put("status", "completed");
        int nextIndex = currentIndex + 1;
        if (nextIndex >= queue.size()) {
            throw new AssertionError("test session has no next study item");
        }
        session.put("currentIndex", nextIndex);
        session.put(
                "currentStudyItemId",
                queue.get(nextIndex).path("studyItemId").asText());
        session.put("version", session.path("version").asInt() + 1);
        jdbcTemplate.update(
                """
                UPDATE fm_user_data
                SET payload_json = ?, data_version = data_version + 1
                WHERE owner_id = ?
                """,
                objectMapper.writeValueAsString(document),
                ownerId);
    }

    private RegisteredUser registerAndLogin(String username) throws Exception {
        Csrf registrationCsrf = csrf(null);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = JsonPath.read(
                registration.getResponse().getContentAsString(),
                "$.id");
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

    private MockMultipartFile fmbook(
            String title,
            String version,
            String variant) throws IOException {
        return new MockMultipartFile(
                "file",
                "warm-lantern-" + variant + ".fmbook",
                "application/zip",
                zip(packageFiles(title, version, variant)));
    }

    private Map<String, byte[]> packageFiles(
            String title,
            String version,
            String variant) {
        String arrivalText = variant.equals("v2")
                ? "莉娅把新版暖灯递给你。"
                : "莉娅把旧版暖灯递给你。";
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "manifest.json",
                """
                {"schemaVersion":1,"namespace":"fm.demo","bookId":"warm-lantern",
                 "contentVersion":"%s","title":"%s",
                 "engineRange":{"minInclusive":"0.3.0","maxExclusive":"0.4.0"},
                 "entryNodeId":"inn.arrival","seriesId":"warm-lantern",
                 "capabilities":["card-battle-v1"],"requires":[]}
                """.formatted(version, title).getBytes(StandardCharsets.UTF_8));
        files.put(
                "vocabulary.tsv",
                ("wordKey\tterm\tmeaning\tacceptedAnswers\thints\n"
                        + "lantern\tlantern\t提灯\t\"[\"\"lantern\"\"]\"\t\"[\"\"照明工具\"\"]\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "bindings.json",
                """
                {"bindings":[{"nodeId":"inn.preparation","wordKeys":["lantern"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn.arrival","type":"choice","title":"暖灯旅店",
                    "text":"%s",
                    "choices":[{"choiceId":"prepare","label":"拿起暖灯，准备出发",
                      "targetNodeId":"inn.preparation"}]},
                  {"nodeId":"inn.preparation","type":"dialogue","speaker":"莉娅",
                    "title":"临行前的笔记","text":"莉娅在便笺上写下 lantern，又把纸角折进你的地图。",
                    "nextNodeId":"forest.signpost"},
                  {"nodeId":"forest.signpost","type":"choice","title":"失语之森",
                    "text":"两道路痕都通向溪桥，远处传来雾里的木桥声。",
                    "choices":[{"choiceId":"cross","label":"沿提灯照亮的旧路前进",
                      "targetNodeId":"bridge.guardian"}]},
                  {"nodeId":"bridge.guardian","type":"encounter","title":"雾中溪桥",
                    "text":"桥雾聚成守卫，先完成一次真正的回忆，才能取得行动能量。",
                    "encounterId":"bridge-guardian",
                    "victoryNodeId":"bridge.crossed",
                    "rescueNodeId":"bridge.assisted"},
                  {"nodeId":"bridge.crossed","type":"dialogue","speaker":"旅伴",
                    "title":"桥后的灯影","text":"迷雾散开时，旅伴替你把暖灯重新举高。",
                    "nextNodeId":"camp.letter"},
                  {"nodeId":"bridge.assisted","type":"camp","speaker":"旅伴",
                    "title":"溪桥救援","text":"你被带回安全的路边，旅伴没有责怪，只把水袋递给你。",
                    "nextNodeId":"camp.fire"},
                  {"nodeId":"camp.letter","type":"dialogue","speaker":"旅伴",
                    "title":"伙伴的对话","text":"“你刚才真的想起来了。我们不是靠猜测走过这座桥的。”",
                    "nextNodeId":"camp.fire"},
                  {"nodeId":"camp.fire","type":"camp","title":"营火",
                    "text":"暖灯挂在树梢，营火把第一章的路标照得清清楚楚。",
                    "nextNodeId":"chapter1.end"},
                  {"nodeId":"chapter1.end","type":"chapter-end","title":"第一章完成",
                    "text":"第一段旅途已经写入存档，下一次可以从旅店继续。"}
                ]}]}
                """.formatted(arrivalText).getBytes(StandardCharsets.UTF_8));
        files.put("cards.json", "{\"cards\":[]}".getBytes(StandardCharsets.UTF_8));
        files.put(
                "encounters.json",
                """
                {"encounters":[{"encounterId":"bridge-guardian",
                  "battleRulesVersion":"card-battle-v1","rewardCardIds":[]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "characters.json",
                "{\"characters\":[]}".getBytes(StandardCharsets.UTF_8));
        files.put("assets.json", "{\"assets\":[]}".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private Map<String, byte[]> progressPackageFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "manifest.json",
                """
                {"schemaVersion":1,"namespace":"fm.demo","bookId":"three-chapters",
                 "contentVersion":"2.0.0","title":"三章旅途",
                 "engineRange":{"minInclusive":"0.3.0","maxExclusive":"0.4.0"},
                 "entryNodeId":"three.start","seriesId":"three-chapters",
                 "capabilities":["card-battle-v1"],"requires":[]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "vocabulary.tsv",
                ("wordKey\tterm\tmeaning\tacceptedAnswers\thints\n"
                        + "lantern\tlantern\t提灯\t\"[\"\"lantern\"\"]\"\t\"[\"\"灯\"\"]\"\n"
                        + "bridge\tbridge\t桥\t\"[\"\"bridge\"\"]\"\t\"[\"\"桥\"\"]\"\n"
                        + "home\thome\t家\t\"[\"\"home\"\"]\"\t\"[\"\"家\"\"]\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "bindings.json",
                """
                {"bindings":[
                  {"nodeId":"three.start","wordKeys":["lantern"]},
                  {"nodeId":"chapter2.start","wordKeys":["bridge"]},
                  {"nodeId":"chapter3.start","wordKeys":["home"]}
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[
                  {"chapterId":"chapter-1","title":"第一章","nodes":[
                    {"nodeId":"three.start","type":"choice","title":"起点",
                      "text":"准备出发。","choices":[
                        {"choiceId":"prepare","label":"前进","targetNodeId":"three.camp1"}
                      ]},
                    {"nodeId":"three.camp1","type":"camp","title":"营地一",
                      "speaker":"旅伴","text":"第一夜的营火。",
                      "recollectionId":"camp-1","recollectionTitle":"第一夜",
                      "recollectionText":"第一夜的营火记录。","nextNodeId":"three.bridge"},
                    {"nodeId":"three.bridge","type":"encounter","title":"桥一",
                      "text":"第一场战斗。","encounterId":"bridge-guardian",
                      "victoryNodeId":"three.after-bridge","rescueNodeId":"three.rescue"},
                    {"nodeId":"three.after-bridge","type":"dialogue","title":"桥后",
                      "speaker":"旅伴","text":"第一场胜利。","nextNodeId":"three.end1"},
                    {"nodeId":"three.rescue","type":"camp","title":"救援一",
                      "speaker":"旅伴","text":"第一场救援。","nextNodeId":"three.end1"},
                    {"nodeId":"three.end1","type":"chapter-end","title":"第一章完成",
                      "text":"进入第二章。","nextNodeId":"chapter2.start"}
                  ]},
                  {"chapterId":"chapter-2","title":"第二章","nodes":[
                    {"nodeId":"chapter2.start","type":"dialogue","title":"菲妮",
                      "speaker":"菲妮","portraitCharacterId":"fina",
                      "unlockCharacterId":"fina","text":"菲妮加入旅团。",
                      "recollectionId":"fina-met","recollectionTitle":"相遇",
                      "recollectionText":"在钟塔下遇见菲妮。","nextNodeId":"chapter2.camp"},
                    {"nodeId":"chapter2.camp","type":"camp","title":"营地二",
                      "speaker":"旅伴","text":"可以切换伙伴。","nextNodeId":"chapter2.bridge"},
                    {"nodeId":"chapter2.bridge","type":"encounter","title":"桥二",
                      "text":"第二场战斗。","encounterId":"clockwork-sentinel",
                      "victoryNodeId":"chapter2.after","rescueNodeId":"chapter2.rescue"},
                    {"nodeId":"chapter2.after","type":"camp","title":"钟塔营火",
                      "speaker":"旅伴","text":"第二场胜利。",
                      "recollectionId":"clock","recollectionTitle":"钟塔",
                      "recollectionText":"钟塔重新走动。","nextNodeId":"chapter2.end"},
                    {"nodeId":"chapter2.rescue","type":"camp","title":"救援二",
                      "speaker":"旅伴","text":"第二场救援。","nextNodeId":"chapter2.end"},
                    {"nodeId":"chapter2.end","type":"chapter-end","title":"第二章完成",
                      "text":"进入第三章。","nextNodeId":"chapter3.start"}
                  ]},
                  {"chapterId":"chapter-3","title":"第三章","nodes":[
                    {"nodeId":"chapter3.start","type":"dialogue","title":"树心",
                      "speaker":"旅伴","text":"最后一段路。","nextNodeId":"chapter3.bridge"},
                    {"nodeId":"chapter3.bridge","type":"encounter","title":"树心战",
                      "text":"最后一场战斗。","encounterId":"heartwood-guardian",
                      "victoryNodeId":"chapter3.after","rescueNodeId":"chapter3.rescue"},
                    {"nodeId":"chapter3.after","type":"dialogue","title":"回信",
                      "speaker":"旅伴","text":"最后一封信。","nextNodeId":"chapter3.end"},
                    {"nodeId":"chapter3.rescue","type":"camp","title":"救援三",
                      "speaker":"旅伴","text":"最后的救援。","nextNodeId":"chapter3.end"},
                    {"nodeId":"chapter3.end","type":"chapter-end","title":"全书完成",
                      "text":"三章旅途完成。"}
                  ]}
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "cards.json",
                """
                {"cards":[
                  {"cardId":"traveler-strike","name":"旅人突刺","cost":1,"target":"enemy",
                    "effects":[{"type":"damage","amount":6}],"description":"造成 6 点伤害。"},
                  {"cardId":"companion-guard","name":"同伴守护","cost":1,"target":"none",
                    "effects":[{"type":"block","amount":5}],"description":"获得 5 点格挡。"},
                  {"cardId":"ember-rune","name":"余烬符文","cost":1,"target":"enemy",
                    "effects":[{"type":"damage","amount":3},
                      {"type":"apply-burn","remainingTurns":2,"damagePerTurn":2}],
                    "description":"造成伤害与灼烧。"},
                  {"cardId":"herb-pouch","name":"采药包","cost":1,"target":"none",
                    "effects":[{"type":"heal","amount":4}],"description":"回复生命。"},
                  {"cardId":"observe-signpost","name":"观察路标","cost":0,"target":"none",
                    "effects":[{"type":"draw","amount":1}],"description":"抽 1 张牌。"},
                  {"cardId":"party-strike","name":"旅团合击","cost":2,"target":"enemy",
                    "effects":[{"type":"damage","amount":10}],"description":"造成 10 点伤害。"}
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "encounters.json",
                """
                {"encounters":[
                  {"encounterId":"bridge-guardian","battleRulesVersion":"card-battle-v1",
                    "rewardCardIds":["herb-pouch","observe-signpost","party-strike"]},
                  {"encounterId":"clockwork-sentinel","battleRulesVersion":"card-battle-v1",
                    "rewardCardIds":["herb-pouch","observe-signpost","ember-rune"]},
                  {"encounterId":"heartwood-guardian","battleRulesVersion":"card-battle-v1",
                    "rewardCardIds":["ember-rune","herb-pouch","observe-signpost"]}
                ]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "characters.json",
                "{\"characters\":[]}".getBytes(StandardCharsets.UTF_8));
        files.put("assets.json", "{\"assets\":[]}".getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipArchiveEntry entry = new ZipArchiveEntry(file.getKey());
                entry.setSize(file.getValue().length);
                zip.putArchiveEntry(entry);
                zip.write(file.getValue());
                zip.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie session, String headerName, String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }

    private record RegisteredUser(String id, SessionCookie session) {
    }

    private record InstalledPackage(String id, String hash) {
    }
}
