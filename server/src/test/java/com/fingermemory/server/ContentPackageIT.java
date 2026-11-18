package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class ContentPackageIT {
    private static final String PASSWORD = "correct horse battery";
    private static final byte[] ONE_PIXEL_PNG = HexFormat.of().parseHex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
                    + "0000000d49444154789c6360000000020001e221bc330000000049454e44ae426082");

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
    void installsIdempotentlyAndKeepsFilesAndMetadataOwnerScoped() throws Exception {
        RegisteredUser alice = registerAndLogin("content-alice");
        Csrf aliceCsrf = csrf(alice.session());
        byte[] packageBytes = validPackage("暖灯与失语之森", "1.0.0");
        MockMultipartFile file = fmbook(packageBytes);

        mockMvc.perform(multipart("/api/v1/content-packages/preview")
                        .file(file)
                        .cookie(aliceCsrf.cookie())
                        .header(aliceCsrf.headerName(), aliceCsrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value("warm-lantern"))
                .andExpect(jsonPath("$.wordCount").value(1))
                .andExpect(jsonPath("$.assetCount").value(1));

        MvcResult installed = mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(file)
                        .cookie(aliceCsrf.cookie())
                        .header(aliceCsrf.headerName(), aliceCsrf.token())
                        .header("Idempotency-Key", "content-install-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("installed"))
                .andExpect(jsonPath("$.installation.bookId").value("warm-lantern"))
                .andReturn();
        String installationId = JsonPath.read(
                installed.getResponse().getContentAsString(),
                "$.installation.id");

        mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(file)
                        .cookie(aliceCsrf.cookie())
                        .header(aliceCsrf.headerName(), aliceCsrf.token())
                        .header("Idempotency-Key", "content-install-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installation.id").value(installationId));

        mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(file)
                        .cookie(aliceCsrf.cookie())
                        .header(aliceCsrf.headerName(), aliceCsrf.token())
                        .header("Idempotency-Key", "content-install-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("duplicate"))
                .andExpect(jsonPath("$.installation.id").value(installationId));

        mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(fmbook(validPackage("暖灯与失语之森 修订", "1.0.0")))
                        .cookie(aliceCsrf.cookie())
                        .header(aliceCsrf.headerName(), aliceCsrf.token())
                        .header("Idempotency-Key", "content-install-3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_VERSION_CONFLICT"));

        MvcResult asset = mockMvc.perform(get(
                        "/api/v1/content-packages/{id}/assets/{path}",
                        installationId,
                        "assets/lantern-keeper.png")
                        .cookie(alice.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();
        assertThat(asset.getResponse().getContentAsByteArray()).isEqualTo(ONE_PIXEL_PNG);

        RegisteredUser bob = registerAndLogin("content-bob");
        mockMvc.perform(get("/api/v1/content-packages")
                        .cookie(bob.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get(
                        "/api/v1/content-packages/{id}",
                        installationId)
                        .cookie(bob.session().cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_INSTALLATION_NOT_FOUND"));
        mockMvc.perform(get(
                        "/api/v1/content-packages/{id}/assets/{path}",
                        installationId,
                        "assets/lantern-keeper.png")
                        .cookie(bob.session().cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_INSTALLATION_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_content_installation WHERE owner_id = ?",
                Integer.class,
                alice.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_content_asset WHERE owner_id = ?",
                Integer.class,
                alice.id())).isEqualTo(1);
    }

    @Test
    void rejectsBadPackageBeforeWritingInstallationOrFiles() throws Exception {
        RegisteredUser user = registerAndLogin("content-bad");
        Csrf csrf = csrf(user.session());
        Map<String, byte[]> files = validFiles();
        files.put("../escape.js", "alert(1)".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/content-packages/preview")
                        .file(fmbook(zip(files)))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_PACKAGE_INVALID"));
        mockMvc.perform(multipart("/api/v1/content-packages")
                        .file(fmbook(zip(files)))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "bad-install"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_PACKAGE_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_content_installation",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_content_installation_receipt",
                Integer.class)).isZero();
    }

    @Test
    void concurrentSameIdempotencyKeyReturnsTheFirstInstallation() throws Exception {
        RegisteredUser user = registerAndLogin("content-concurrent");
        Csrf csrf = csrf(user.session());
        byte[] packageBytes = validPackage("暖灯与失语之森", "1.0.0");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(multipart("/api/v1/content-packages")
                                .file(fmbook(packageBytes))
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .header("Idempotency-Key", "content-concurrent-1"))
                        .andReturn();
            });
            Future<MvcResult> second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(multipart("/api/v1/content-packages")
                                .file(fmbook(packageBytes))
                                .cookie(csrf.cookie())
                                .header(csrf.headerName(), csrf.token())
                                .header("Idempotency-Key", "content-concurrent-1"))
                        .andReturn();
            });
            start.countDown();
            MvcResult firstResult = first.get(10, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(201);
            String firstId = JsonPath.read(
                    firstResult.getResponse().getContentAsString(),
                    "$.installation.id");
            String secondId = JsonPath.read(
                    secondResult.getResponse().getContentAsString(),
                    "$.installation.id");
            assertThat(secondId).isEqualTo(firstId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fm_content_installation WHERE owner_id = ?",
                    Integer.class,
                    user.id())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM fm_content_installation_receipt WHERE owner_id = ?",
                    Integer.class,
                    user.id())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
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

    private MockMultipartFile fmbook(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "warm-lantern.fmbook",
                "application/zip",
                bytes);
    }

    private byte[] validPackage(String title, String version) throws IOException {
        Map<String, byte[]> files = validFiles();
        files.put(
                "manifest.json",
                manifestJson(title, version).getBytes(StandardCharsets.UTF_8));
        return zip(files);
    }

    private Map<String, byte[]> validFiles() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "manifest.json",
                manifestJson("暖灯与失语之森", "1.0.0")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "vocabulary.tsv",
                ("wordKey\tterm\tmeaning\tacceptedAnswers\thints\n"
                        + "lantern\tlantern\t提灯\t\"[\"\"lantern\"\"]\"\t\"[\"\"照明工具\"\"]\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "bindings.json",
                """
                {"bindings":[{"nodeId":"preparation","wordKeys":["lantern"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn","type":"choice","title":"旅店","text":"莉娅把暖灯递给你。",
                    "choices":[{"choiceId":"prepare","label":"准备出发","targetNodeId":"preparation"}]},
                  {"nodeId":"preparation","type":"dialogue","speaker":"莉娅",
                    "title":"行囊","text":"带上提灯，沿旧路去溪桥。","nextNodeId":"bridge"},
                  {"nodeId":"bridge","type":"encounter","title":"溪桥","text":"雾中守卫挡住去路。",
                    "encounterId":"bridge-guardian","victoryNodeId":"camp","rescueNodeId":"rest"},
                  {"nodeId":"camp","type":"camp","speaker":"莉娅","title":"营火",
                    "text":"火光照亮找回的路标。","nextNodeId":"end"},
                  {"nodeId":"rest","type":"camp","speaker":"瑟琳","title":"救援",
                    "text":"伙伴把你带回营火。","nextNodeId":"end"},
                  {"nodeId":"end","type":"chapter-end","title":"第一章结束",
                    "text":"暖灯仍在燃烧。"}
                ]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "cards.json",
                """
                {"cards":[{"cardId":"guard","name":"同伴守护","cost":1,"target":"self",
                  "effects":[{"type":"block","amount":5}]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "encounters.json",
                """
                {"encounters":[{"encounterId":"bridge-guardian","battleRulesVersion":"card-battle-v1",
                  "rewardCardIds":["guard"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "characters.json",
                """
                {"characters":[{"characterKey":"lia","name":"莉娅","definitionVersion":"1.0.0",
                  "skillCardIds":["guard"],"portraits":{"neutral":"assets/lantern-keeper.png"}}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put("assets/lantern-keeper.png", ONE_PIXEL_PNG);
        files.put(
                "assets.json",
                """
                {"assets":[{"path":"assets/lantern-keeper.png","sha256":"%s",
                  "mime":"image/png","byteSize":%d,"kind":"portrait",
                  "license":"project-original"}]}
                """.formatted(sha256(ONE_PIXEL_PNG), ONE_PIXEL_PNG.length)
                        .getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private String manifestJson(String title, String version) {
        return """
                {"schemaVersion":1,"namespace":"fm.demo","bookId":"warm-lantern",
                 "contentVersion":"%s","title":"%s",
                 "engineRange":{"minInclusive":"0.3.0","maxExclusive":"0.4.0"},
                 "entryNodeId":"inn","seriesId":"warm-lantern",
                 "capabilities":["card-battle-v1"],"requires":[]}
                """.formatted(version, title);
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
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
}
