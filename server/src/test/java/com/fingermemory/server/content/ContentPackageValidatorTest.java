package com.fingermemory.server.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fingermemory.server.common.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ContentPackageValidatorTest {
    private static final byte[] ONE_PIXEL_PNG = HexFormat.of().parseHex(
            "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
                    + "0000000d49444154789c6360000000020001e221bc330000000049454e44ae426082");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentPackageValidator validator =
            new ContentPackageValidator(objectMapper);

    @TempDir
    Path tempDirectory;

    @Test
    void acceptsValidPackageAndReturnsStableSummary() throws IOException {
        byte[] archive = validPackage();
        Path workspace = Files.createDirectory(tempDirectory.resolve("valid"));

        ValidatedContentPackage result = validator.validate(
                new ByteArrayInputStream(archive),
                archive.length,
                "warm-lantern.fmbook",
                workspace,
                "0.3.0");

        assertThat(result.title()).isEqualTo("暖灯与失语之森");
        assertThat(result.namespace()).isEqualTo("fm.demo");
        assertThat(result.bookId()).isEqualTo("warm-lantern");
        assertThat(result.contentVersion()).isEqualTo("1.0.0");
        assertThat(result.contentHash()).matches("[a-f0-9]{64}");
        assertThat(result.wordCount()).isEqualTo(1);
        assertThat(result.chapterCount()).isEqualTo(1);
        assertThat(result.nodeCount()).isEqualTo(3);
        assertThat(result.characterCount()).isEqualTo(1);
        assertThat(result.cardCount()).isEqualTo(1);
        assertThat(result.encounterCount()).isEqualTo(1);
        assertThat(result.assets()).singleElement()
                .satisfies(asset -> assertThat(asset.path())
                        .isEqualTo("assets/lantern-keeper.png"));
        assertThat(Files.isRegularFile(
                result.extractedDirectory().resolve("manifest.json"))).isTrue();
    }

    @Test
    void rejectsPathTraversalAndExecutableContent() throws Exception {
        Map<String, byte[]> traversalFiles = validFiles();
        traversalFiles.put("../escape.js", "alert(1)".getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(traversalFiles), "路径");

        Map<String, byte[]> scriptFiles = validFiles();
        scriptFiles.put("assets/run.js", "alert(1)".getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(scriptFiles), "禁止");
    }

    @Test
    void rejectsUnknownCapabilityAndIncompatibleEngineRange() throws Exception {
        Map<String, byte[]> files = validFiles();
        files.put(
                "manifest.json",
                manifestJson("0.3.0", "0.4.0", "custom-script")
                        .getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(files), "未支持能力");

        Map<String, byte[]> incompatible = validFiles();
        incompatible.put(
                "manifest.json",
                manifestJson("1.0.0", "2.0.0", "card-battle-v1")
                        .getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(incompatible), "不支持当前引擎版本");
    }

    @Test
    void rejectsAssetHashMismatchAndUndeclaredAssets() throws Exception {
        Map<String, byte[]> files = validFiles();
        files.put(
                "assets.json",
                assetsJson("0".repeat(64)).getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(files), "资源哈希");

        Map<String, byte[]> undeclared = validFiles();
        undeclared.put("assets/extra.png", ONE_PIXEL_PNG);
        assertInvalid(zip(undeclared), "未声明资源");
    }

    @Test
    void rejectsWrongTsvHeaderAndUnboundVocabulary() throws Exception {
        Map<String, byte[]> files = validFiles();
        files.put(
                "vocabulary.tsv",
                "word,term,meaning,acceptedAnswers,hints\n"
                        .getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(files), "表头");

        Map<String, byte[]> unbound = validFiles();
        unbound.put(
                "bindings.json",
                "{\"bindings\":[]}".getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(unbound), "没有剧情绑定");
    }

    private void assertInvalid(byte[] archive, String message) throws IOException {
        Path workspace = Files.createTempDirectory(tempDirectory, "invalid-");
        assertThatThrownBy(() -> validator.validate(
                new ByteArrayInputStream(archive),
                archive.length,
                "invalid.fmbook",
                workspace,
                "0.3.0"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(message);
    }

    private byte[] validPackage() throws IOException {
        return zip(validFiles());
    }

    private Map<String, byte[]> validFiles() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(
                "manifest.json",
                manifestJson("0.3.0", "0.4.0", "card-battle-v1")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "vocabulary.tsv",
                ("wordKey\tterm\tmeaning\tacceptedAnswers\thints\n"
                        + "lantern\tlantern\t提灯\t\"[\"\"lantern\"\",\"\"lamp\"\"]\"\t\"[\"\"照明工具\"\"]\"\n")
                        .getBytes(StandardCharsets.UTF_8));
        files.put(
                "bindings.json",
                """
                {"bindings":[{"nodeId":"bridge","wordKeys":["lantern"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn","type":"dialogue","nextNodeId":"bridge"},
                  {"nodeId":"bridge","type":"encounter","choices":[{"label":"休息","targetNodeId":"rest"}]},
                  {"nodeId":"rest","type":"chapter-end"}
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
                assetsJson(sha256(ONE_PIXEL_PNG)).getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private String manifestJson(
            String minInclusive,
            String maxExclusive,
            String capability) {
        return """
                {"schemaVersion":1,"namespace":"fm.demo","bookId":"warm-lantern",
                 "contentVersion":"1.0.0","title":"暖灯与失语之森",
                 "engineRange":{"minInclusive":"%s","maxExclusive":"%s"},
                 "entryNodeId":"inn","seriesId":"warm-lantern",
                 "capabilities":["%s"],"requires":[]}
                """.formatted(minInclusive, maxExclusive, capability);
    }

    private String assetsJson(String sha256) {
        return """
                {"assets":[{"path":"assets/lantern-keeper.png","sha256":"%s",
                  "mime":"image/png","byteSize":%d,"kind":"portrait",
                  "license":"project-original"}]}
                """.formatted(sha256, ONE_PIXEL_PNG.length);
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

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
