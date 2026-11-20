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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
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
class GmContentIT {
    private static final String PASSWORD = "correct horse battery";
    private static final byte[] PREVIEW_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8"
                    + "/x8AAusB9Y9Zx4sAAAAASUVORK5CYII=");

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
        jdbcTemplate.update("DELETE FROM fm_adventure_completion");
        jdbcTemplate.update("DELETE FROM fm_adventure_recollection");
        jdbcTemplate.update("DELETE FROM fm_adventure_character_unlock");
        jdbcTemplate.update("DELETE FROM fm_adventure_reward_offer");
        jdbcTemplate.update("DELETE FROM fm_adventure_event");
        jdbcTemplate.update("DELETE FROM fm_adventure_receipt");
        jdbcTemplate.update("DELETE FROM fm_adventure_battle_binding");
        jdbcTemplate.update("DELETE FROM fm_adventure_run");
        jdbcTemplate.update("DELETE FROM fm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_content_installation_receipt");
        jdbcTemplate.update("DELETE FROM fm_content_installation");
        jdbcTemplate.update("DELETE FROM fm_gm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_gm_content");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        jdbcTemplate.update("DELETE FROM fm_gm_action_receipt");
        jdbcTemplate.update("DELETE FROM fm_gm_audit_event");
        jdbcTemplate.update("DELETE FROM fm_user_data");
        jdbcTemplate.update("DELETE FROM fm_lexicon");
        jdbcTemplate.update("DELETE FROM fm_user_preferences");
        jdbcTemplate.update("DELETE FROM fm_user");
        deleteRecursively(Path.of(appProperties.content().packageRoot()));
    }

    @Test
    void validatesDraftPublishesImmutableVersionAndArchivesWithoutBreakingRuns()
            throws Exception {
        RegisteredUser admin = registerAndLogin("content-admin");
        jdbcTemplate.update(
                "UPDATE fm_user SET role = 'ADMIN' WHERE id = ?",
                admin.id());
        admin = new RegisteredUser(admin.id(), login("content-admin"));
        Csrf adminCsrf = csrf(admin.session());

        MvcResult uploaded = upload(
                adminCsrf,
                "upload-content-v1",
                packageBytes("1.0.0", "风车测试书", "旧文本"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();
        String contentId = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.id");
        String contentHash = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.contentHash");
        mockMvc.perform(get("/api/v1/gm/content/{id}/preview", contentId)
                        .cookie(admin.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.status").value("draft"))
                .andExpect(jsonPath("$.chapters[0].title").value("管理内容"))
                .andExpect(jsonPath("$.chapters[0].nodes[0].sceneId")
                        .value("managed-scene"))
                .andExpect(jsonPath("$.characters[0].characterKey").value("guide"))
                .andExpect(jsonPath("$.characters[0].costumes.summer-travel.neutral")
                        .value("/api/v1/gm/content/" + contentId
                                + "/assets/assets/managed.png"))
                .andExpect(jsonPath("$.scenes[0].background")
                        .value("/api/v1/gm/content/" + contentId
                                + "/assets/assets/managed.png"));
        mockMvc.perform(get(
                        "/api/v1/gm/content/{id}/assets/assets/managed.png",
                        contentId)
                        .cookie(admin.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .isEqualTo("image/png"));

        RegisteredUser reader = registerAndLogin("content-reader");
        Csrf readerCsrf = csrf(reader.session());
        mockMvc.perform(get("/api/v1/content-packages/catalog")
                        .cookie(reader.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(multipart("/api/v1/gm/content/drafts")
                        .file(fmbook(packageBytes("1.0.0", "风车测试书", "旧文本")))
                        .param("reason", "ordinary user cannot upload")
                        .cookie(reader.session().cookie())
                        .header("Idempotency-Key", "ordinary-upload"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/gm/content/{id}/preview", contentId)
                        .cookie(reader.session().cookie()))
                .andExpect(status().isForbidden());

        RegisteredUser secondAdmin = registerAndLogin("content-admin-second");
        jdbcTemplate.update(
                "UPDATE fm_user SET role = 'ADMIN' WHERE id = ?",
                secondAdmin.id());
        secondAdmin = new RegisteredUser(
                secondAdmin.id(),
                login("content-admin-second"));
        mockMvc.perform(get("/api/v1/gm/content/{id}/preview", contentId)
                        .cookie(secondAdmin.session().cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GM_CONTENT_NOT_FOUND"));
        action(
                csrf(secondAdmin.session()),
                contentId,
                "second-admin-cannot-validate",
                "validate",
                contentHash)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GM_CONTENT_NOT_FOUND"));

        action(adminCsrf, contentId, "validate-content", "validate", contentHash)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("validated"))
                .andExpect(jsonPath("$.issues.length()").value(0));
        mockMvc.perform(get("/api/v1/content-packages/catalog")
                        .cookie(reader.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        action(adminCsrf, contentId, "publish-content", "publish", contentHash)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"));
        mockMvc.perform(get("/api/v1/content-packages/catalog")
                        .cookie(reader.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(contentId))
                .andExpect(jsonPath("$[0].title").value("风车测试书"));

        MvcResult installed = mockMvc.perform(post(
                        "/api/v1/content-packages/catalog/{id}/install",
                        contentId)
                        .cookie(readerCsrf.cookie())
                        .header(readerCsrf.headerName(), readerCsrf.token())
                        .header("Idempotency-Key", "reader-catalog-install"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.installation.catalogContentId")
                        .value(contentId))
                .andReturn();
        String installationId = JsonPath.read(
                installed.getResponse().getContentAsString(),
                "$.installation.id");
        String runId = JsonPath.read(mockMvc.perform(post("/api/v1/adventures")
                                .cookie(readerCsrf.cookie())
                                .header(readerCsrf.headerName(), readerCsrf.token())
                                .header("Idempotency-Key", "reader-start-before-archive")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "installationId", installationId,
                                        "companionKey", "lia",
                                        "reduceMotion", true))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.run.id");

        RegisteredUser futureReader = registerAndLogin("content-future");
        Csrf futureCsrf = csrf(futureReader.session());
        MvcResult futureInstall = mockMvc.perform(post(
                        "/api/v1/content-packages/catalog/{id}/install",
                        contentId)
                        .cookie(futureCsrf.cookie())
                        .header(futureCsrf.headerName(), futureCsrf.token())
                        .header("Idempotency-Key", "future-catalog-install"))
                .andExpect(status().isCreated())
                .andReturn();
        String futureInstallationId = JsonPath.read(
                futureInstall.getResponse().getContentAsString(),
                "$.installation.id");

        action(adminCsrf, contentId, "archive-content", "archive", contentHash)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("archived"));
        mockMvc.perform(get("/api/v1/content-packages/catalog")
                        .cookie(reader.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/adventures/{id}", runId)
                        .cookie(reader.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(post("/api/v1/adventures")
                        .cookie(readerCsrf.cookie())
                        .header(readerCsrf.headerName(), readerCsrf.token())
                        .header("Idempotency-Key", "reader-resume-after-archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", installationId,
                                "companionKey", "lia",
                                "reduceMotion", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("existing"))
                .andExpect(jsonPath("$.run.id").value(runId));
        mockMvc.perform(post("/api/v1/adventures")
                        .cookie(futureCsrf.cookie())
                        .header(futureCsrf.headerName(), futureCsrf.token())
                        .header("Idempotency-Key", "future-start-after-archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "installationId", futureInstallationId,
                                "companionKey", "lia",
                                "reduceMotion", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("CONTENT_CATALOG_UNAVAILABLE"));
        RegisteredUser lateReader = registerAndLogin("content-late");
        Csrf lateCsrf = csrf(lateReader.session());
        mockMvc.perform(post(
                        "/api/v1/content-packages/catalog/{id}/install",
                        contentId)
                        .cookie(lateCsrf.cookie())
                        .header(lateCsrf.headerName(), lateCsrf.token())
                        .header("Idempotency-Key", "late-catalog-install"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_CATALOG_UNAVAILABLE"));

        mockMvc.perform(get("/api/v1/gm/content")
                        .cookie(admin.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("archived"));
        mockMvc.perform(get("/api/v1/gm/audit")
                        .cookie(admin.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action")
                        .value("gm.content.archive"));
    }

    @Test
    void rejectsBadPackageAndSameVersionWithDifferentHash() throws Exception {
        RegisteredUser admin = registerAndLogin("content-admin-conflict");
        jdbcTemplate.update(
                "UPDATE fm_user SET role = 'ADMIN' WHERE id = ?",
                admin.id());
        admin = new RegisteredUser(admin.id(), login("content-admin-conflict"));
        Csrf csrf = csrf(admin.session());

        Map<String, byte[]> bad = packageFiles("1.0.0", "坏包", "坏包");
        bad.put("../escape.js", "alert(1)".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/gm/content/drafts")
                        .file(fmbook(zip(bad)))
                        .param("reason", "upload bad package")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "upload-bad-package"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_PACKAGE_INVALID"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_content",
                Integer.class)).isZero();

        upload(
                csrf,
                "upload-conflict-v1",
                packageBytes("1.0.0", "版本冲突", "第一份"))
                .andExpect(status().isCreated());
        upload(
                csrf,
                "upload-conflict-v1-other",
                packageBytes("1.0.0", "版本冲突", "第二份"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GM_CONTENT_VERSION_CONFLICT"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_content",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void publishRevalidatesTheCanonicalArchive() throws Exception {
        RegisteredUser admin = registerAndLogin("content-admin-publish");
        promoteToAdmin(admin);
        admin = new RegisteredUser(admin.id(), login("content-admin-publish"));
        Csrf csrf = csrf(admin.session());

        MvcResult uploaded = upload(
                csrf,
                "upload-publish-revalidate",
                packageBytes("1.0.0", "发布复验", "第一份"))
                .andExpect(status().isCreated())
                .andReturn();
        String contentId = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.id");
        String contentHash = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.contentHash");
        action(csrf, contentId, "validate-publish-revalidate", "validate", contentHash)
                .andExpect(status().isOk());

        Path storage = storageRoot(contentId);
        Files.write(
                storage.resolve("package.fmbook"),
                packageBytes("1.0.0", "发布复验", "被替换"));
        action(csrf, contentId, "publish-publish-revalidate", "publish", contentHash)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GM_CONTENT_RESOURCE_INVALID"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM fm_gm_content WHERE id = ?",
                String.class,
                contentId)).isEqualTo("validated");
    }

    @Test
    void publishedCatalogInstallRejectsAChangedArchive() throws Exception {
        RegisteredUser admin = registerAndLogin("content-admin-install");
        promoteToAdmin(admin);
        admin = new RegisteredUser(admin.id(), login("content-admin-install"));
        Csrf csrf = csrf(admin.session());
        MvcResult uploaded = upload(
                csrf,
                "upload-install-revalidate",
                packageBytes("1.0.0", "安装复验", "第一份"))
                .andExpect(status().isCreated())
                .andReturn();
        String contentId = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.id");
        String contentHash = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.contentHash");
        action(csrf, contentId, "validate-install-revalidate", "validate", contentHash)
                .andExpect(status().isOk());
        action(csrf, contentId, "publish-install-revalidate", "publish", contentHash)
                .andExpect(status().isOk());

        Files.write(
                storageRoot(contentId).resolve("package.fmbook"),
                packageBytes("1.0.0", "安装复验", "被替换"));
        RegisteredUser reader = registerAndLogin("content-reader-install");
        Csrf readerCsrf = csrf(reader.session());
        mockMvc.perform(post(
                        "/api/v1/content-packages/catalog/{id}/install",
                        contentId)
                        .cookie(readerCsrf.cookie())
                        .header(readerCsrf.headerName(), readerCsrf.token())
                        .header("Idempotency-Key", "tampered-catalog-install"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTENT_CATALOG_UNAVAILABLE"));
    }

    @Test
    void previewAssetRejectsAFileOutsideTheRegisteredHash() throws Exception {
        RegisteredUser admin = registerAndLogin("content-admin-asset");
        promoteToAdmin(admin);
        admin = new RegisteredUser(admin.id(), login("content-admin-asset"));
        Csrf csrf = csrf(admin.session());
        MvcResult uploaded = upload(
                csrf,
                "upload-asset-revalidate",
                packageBytes("1.0.0", "资源复验", "第一份"))
                .andExpect(status().isCreated())
                .andReturn();
        String contentId = JsonPath.read(
                uploaded.getResponse().getContentAsString(),
                "$.id");
        Files.write(
                storageRoot(contentId).resolve("expanded/assets/managed.png"),
                new byte[] {0x01, 0x02, 0x03});

        mockMvc.perform(get(
                        "/api/v1/gm/content/{id}/assets/assets/managed.png",
                        contentId)
                        .cookie(admin.session().cookie()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GM_CONTENT_RESOURCE_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            Csrf csrf,
            String idempotencyKey,
            byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/v1/gm/content/drafts")
                .file(fmbook(bytes))
                .param("reason", "publish managed content")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .header("Idempotency-Key", idempotencyKey));
    }

    private org.springframework.test.web.servlet.ResultActions action(
            Csrf csrf,
            String contentId,
            String actionId,
            String action,
            String contentHash) throws Exception {
        return mockMvc.perform(post("/api/v1/gm/content/{id}/actions", contentId)
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .header("Idempotency-Key", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "action", action,
                        "expectedContentHash", contentHash,
                        "reason", "content lifecycle " + action))));
    }

    private byte[] packageBytes(
            String version,
            String title,
            String arrivalText) throws IOException {
        return zip(packageFiles(version, title, arrivalText));
    }

    private Map<String, byte[]> packageFiles(
            String version,
            String title,
            String arrivalText) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "manifest.json",
                """
                {"schemaVersion":1,"namespace":"fm.gmtest","bookId":"managed-book",
                 "contentVersion":"%s","title":"%s",
                 "engineRange":{"minInclusive":"0.3.0","maxExclusive":"0.4.0"},
                 "entryNodeId":"managed.start","seriesId":"managed-series",
                 "capabilities":["card-battle-v1"],"requires":[]}
                """.formatted(version, title).getBytes(StandardCharsets.UTF_8));
        files.put(
                "vocabulary.tsv",
                ("wordKey\tterm\tmeaning\tacceptedAnswers\thints\n"
                        + "route\troute\t路线\t\"[\"\"route\"\"]\"\t\"[\"\"走法\"\"]\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "bindings.json",
                """
                {"bindings":[{"nodeId":"managed.start","wordKeys":["route"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"managed-chapter","title":"管理内容","nodes":[
                  {"nodeId":"managed.start","type":"choice","title":"入口",
                    "text":"%s","portraitCharacterId":"guide",
                    "costumeId":"summer-travel","sceneId":"managed-scene","choices":[
                      {"choiceId":"go","label":"出发","targetNodeId":"managed.end"}
                    ]},
                  {"nodeId":"managed.end","type":"chapter-end","title":"完成",
                    "text":"内容完成。","endingId":"main"}
                ]}]}
                """.formatted(arrivalText).getBytes(StandardCharsets.UTF_8));
        files.put("cards.json", "{\"cards\":[]}".getBytes(StandardCharsets.UTF_8));
        files.put(
                "encounters.json",
                "{\"encounters\":[]}".getBytes(StandardCharsets.UTF_8));
        files.put(
                "characters.json",
                """
                {"characters":[{
                  "characterKey":"guide","definitionVersion":"1.0.0",
                  "name":"引路人","epithet":"测试向导","description":"用于管理预览。",
                  "age":18,"availability":"npc","defaultCostumeId":"summer-travel",
                  "portraits":{"neutral":"assets/managed.png"}
                }]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "scenes.json",
                """
                {"scenes":[{
                  "sceneId":"managed-scene","name":"预览场景",
                  "background":"assets/managed.png","battleTheme":"bridge"
                }]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put("assets/managed.png", PREVIEW_PNG);
        files.put(
                "assets.json",
                """
                {"assets":[{
                  "path":"assets/managed.png",
                  "sha256":"%s",
                  "mime":"image/png",
                  "byteSize":%d,
                  "kind":"scene-background",
                  "license":"Project original; test fixture"
                }]}
                """.formatted(sha256(PREVIEW_PNG), PREVIEW_PNG.length)
                        .getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private void promoteToAdmin(RegisteredUser user) {
        jdbcTemplate.update(
                "UPDATE fm_user SET role = 'ADMIN' WHERE id = ?",
                user.id());
    }

    private Path storageRoot(String contentId) {
        String relative = jdbcTemplate.queryForObject(
                "SELECT storage_relative_path FROM fm_gm_content WHERE id = ?",
                String.class,
                contentId);
        return Path.of(appProperties.content().packageRoot())
                .toAbsolutePath()
                .normalize()
                .resolve(relative)
                .normalize();
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private MockMultipartFile fmbook(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "managed-content.fmbook",
                "application/zip",
                bytes);
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

    private record Csrf(
            SessionCookie session,
            String headerName,
            String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }

    private record RegisteredUser(String id, SessionCookie session) {
    }
}
