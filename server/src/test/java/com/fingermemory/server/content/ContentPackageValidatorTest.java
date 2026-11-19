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
    private static final byte[] MINIMAL_JPEG = HexFormat.of().parseHex("ffd8ffd9");

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
        assertThat(result.nodeCount()).isEqualTo(6);
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
    void acceptsAuthoredCompanionCostumesScenesAndDamageSkill() throws IOException {
        byte[] archive = zip(dynamicCompanionFiles());
        Path workspace = Files.createDirectory(tempDirectory.resolve("dynamic-companion"));

        ValidatedContentPackage result = validator.validate(
                new ByteArrayInputStream(archive),
                archive.length,
                "windmill-post.fmbook",
                workspace,
                "0.3.0");

        assertThat(result.characterCount()).isEqualTo(1);
        assertThat(result.assets()).hasSize(7);
        assertThat(result.assets())
                .extracting(ContentPackageDtos.AssetView::path)
                .contains(
                        "assets/characters/nora/wind-courier/cheer.png",
                        "assets/scenes/windmill-post.jpg");
        assertThat(Files.isRegularFile(
                result.extractedDirectory().resolve("scenes.json"))).isTrue();
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
    void rejectsInvalidContinuationVersionRange() throws Exception {
        Map<String, byte[]> files = validFiles();
        files.put(
                "manifest.json",
                """
                {"schemaVersion":1,"namespace":"fm.demo","bookId":"sequel",
                 "contentVersion":"1.0.0","title":"续作",
                 "engineRange":{"minInclusive":"0.3.0","maxExclusive":"0.4.0"},
                 "entryNodeId":"inn","seriesId":"lineage",
                 "capabilities":["card-battle-v1"],"requires":[
                   {"bookId":"warm-lantern","endingId":"main",
                    "endingTitle":"主线结局",
                    "minContentVersion":"1.1.0",
                    "maxContentVersionExclusive":"1.0.0"}
                 ]}
                """.getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(files), "前作版本范围");
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

    @Test
    void rejectsEncounterWithoutContinuationBranchesAndDuplicateChoiceIds()
            throws Exception {
        Map<String, byte[]> missingBranches = validFiles();
        missingBranches.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn","type":"choice","choices":[
                    {"choiceId":"prepare","label":"准备出发","targetNodeId":"bridge"}
                  ]},
                  {"nodeId":"bridge","type":"encounter","encounterId":"bridge-guardian",
                    "victoryNodeId":"camp"},
                  {"nodeId":"camp","type":"dialogue","nextNodeId":"end"},
                  {"nodeId":"rest","type":"dialogue","nextNodeId":"end"},
                  {"nodeId":"end","type":"chapter-end"}
                ]}]}
                """.getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(missingBranches), "rescueNodeId");

        Map<String, byte[]> duplicateChoices = validFiles();
        duplicateChoices.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn","type":"choice","choices":[
                    {"choiceId":"prepare","label":"准备出发","targetNodeId":"bridge"},
                    {"choiceId":"prepare","label":"检查提灯","targetNodeId":"bridge"}
                  ]},
                  {"nodeId":"bridge","type":"encounter","encounterId":"bridge-guardian",
                    "victoryNodeId":"camp","rescueNodeId":"rest"},
                  {"nodeId":"camp","type":"dialogue","nextNodeId":"end"},
                  {"nodeId":"rest","type":"dialogue","nextNodeId":"end"},
                  {"nodeId":"end","type":"chapter-end"}
                ]}]}
                """.getBytes(StandardCharsets.UTF_8));
        assertInvalid(zip(duplicateChoices), "choiceId 重复");
    }

    @Test
    void rejectsEncounterThatDoesNotExistInEncounterManifest() throws Exception {
        Map<String, byte[]> files = validFiles();
        files.put(
                "story.json",
                storyJson("missing-encounter").getBytes(StandardCharsets.UTF_8));

        assertInvalid(zip(files), "不存在的 encounterId");
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
                {"bindings":[{"nodeId":"preparation","wordKeys":["lantern"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                storyJson("bridge-guardian").getBytes(StandardCharsets.UTF_8));
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

    private Map<String, byte[]> dynamicCompanionFiles() throws IOException {
        Map<String, byte[]> files = validFiles();
        files.remove("assets/lantern-keeper.png");
        files.put(
                "bindings.json",
                """
                {"bindings":[{"nodeId":"inn","wordKeys":["lantern"]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "story.json",
                """
                {"chapters":[{"chapterId":"windmill-post-chapter","title":"风车邮路","nodes":[
                  {"nodeId":"inn","type":"dialogue","speaker":"诺拉",
                    "portraitCharacterId":"nora","sceneId":"windmill-post",
                    "costumeId":"wind-courier","unlockCharacterId":"nora",
                    "title":"路中央的信使","text":"诺拉在风车下等待。",
                    "nextNodeId":"bridge"},
                  {"nodeId":"bridge","type":"encounter","title":"逆风邮站",
                    "encounterId":"bridge-guardian","victoryNodeId":"camp",
                    "rescueNodeId":"rest"},
                  {"nodeId":"camp","type":"camp","title":"风停之后",
                    "text":"信已经送达。","nextNodeId":"end"},
                  {"nodeId":"rest","type":"camp","title":"避风",
                    "text":"你们退回邮站。","nextNodeId":"end"},
                  {"nodeId":"end","type":"chapter-end","title":"完成",
                    "text":"诺拉加入旅团。"}
                ]}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "characters.json",
                """
                {"characters":[{"characterKey":"nora","definitionVersion":"1.0.0",
                  "name":"诺拉","age":15,"availability":"companion",
                  "defaultCostumeId":"wind-courier",
                  "costumes":{"wind-courier":{
                    "neutral":"assets/characters/nora/wind-courier/neutral.png",
                    "welcome":"assets/characters/nora/wind-courier/welcome.png",
                    "cheer":"assets/characters/nora/wind-courier/cheer.png",
                    "concern":"assets/characters/nora/wind-courier/concern.png",
                    "relief":"assets/characters/nora/wind-courier/relief.png",
                    "surprise":"assets/characters/nora/wind-courier/surprise.png"}},
                  "dialogue":{"welcome":"风向正好。","again":"下一次再送。",
                    "rest":"邮站会等到明天。","training":"先看路标。",
                    "practice":"这次算熟悉路线。","result":"信送到了。"},
                  "skill":{"name":"顺风投递","description":"造成 6 点伤害。",
                    "type":"damage","amount":6}}]}
                """.getBytes(StandardCharsets.UTF_8));
        files.put(
                "scenes.json",
                """
                {"scenes":[{"sceneId":"windmill-post","name":"逆风邮站",
                  "background":"assets/scenes/windmill-post.jpg",
                  "battleTheme":"bridge"}]}
                """.getBytes(StandardCharsets.UTF_8));
        for (String expression : new String[] {
                "neutral", "welcome", "cheer", "concern", "relief", "surprise"}) {
            files.put(
                    "assets/characters/nora/wind-courier/" + expression + ".png",
                    ONE_PIXEL_PNG);
        }
        files.put("assets/scenes/windmill-post.jpg", MINIMAL_JPEG);
        files.put(
                "assets.json",
                dynamicAssetsJson().getBytes(StandardCharsets.UTF_8));
        return files;
    }

    private String storyJson(String encounterId) {
        return """
                {"chapters":[{"chapterId":"chapter-1","title":"暖灯与失语之森","nodes":[
                  {"nodeId":"inn","type":"choice","title":"旅店","text":"莉娅把暖灯递给你。",
                    "choices":[{"choiceId":"prepare","label":"准备出发","targetNodeId":"preparation"}]},
                  {"nodeId":"preparation","type":"dialogue","speaker":"莉娅",
                    "title":"行囊","text":"带上提灯，沿旧路去溪桥。","nextNodeId":"bridge"},
                  {"nodeId":"bridge","type":"encounter","title":"溪桥","text":"雾中守卫挡住去路。",
                    "encounterId":"%s","victoryNodeId":"camp","rescueNodeId":"rest"},
                  {"nodeId":"camp","type":"camp","speaker":"莉娅","title":"营火",
                    "text":"火光照亮找回的路标。","nextNodeId":"end"},
                  {"nodeId":"rest","type":"camp","speaker":"瑟琳","title":"救援",
                    "text":"伙伴把你带回营火。","nextNodeId":"end"},
                  {"nodeId":"end","type":"chapter-end","title":"第一章结束",
                    "text":"暖灯仍在燃烧。"}
                ]}]}
                """.formatted(encounterId);
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

    private String dynamicAssetsJson() {
        String pngHash = sha256(ONE_PIXEL_PNG);
        String jpegHash = sha256(MINIMAL_JPEG);
        StringBuilder assets = new StringBuilder("{\"assets\":[");
        String[] expressions = {
            "neutral", "welcome", "cheer", "concern", "relief", "surprise"
        };
        for (int index = 0; index < expressions.length; index += 1) {
            if (index > 0) {
                assets.append(',');
            }
            assets.append("""
                    {"path":"assets/characters/nora/wind-courier/%s.png",
                     "sha256":"%s","mime":"image/png","byteSize":%d,
                     "kind":"character-portrait","license":"project-original"}
                    """.formatted(expressions[index], pngHash, ONE_PIXEL_PNG.length));
        }
        assets.append("""
                ,{"path":"assets/scenes/windmill-post.jpg","sha256":"%s",
                  "mime":"image/jpeg","byteSize":%d,"kind":"scene-background",
                  "license":"project-original"}
                ]}
                """.formatted(jpegHash, MINIMAL_JPEG.length));
        return assets.toString();
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
