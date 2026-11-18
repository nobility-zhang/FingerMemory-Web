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
