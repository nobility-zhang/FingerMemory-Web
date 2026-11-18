package com.fingermemory.server.content;

import com.fingermemory.server.common.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ContentPackageValidator {
    static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024;
    static final long MAX_EXPANDED_BYTES = 200L * 1024 * 1024;
    static final long MAX_SINGLE_FILE_BYTES = 20L * 1024 * 1024;
    static final int MAX_ENTRIES = 1000;
    static final int MAX_WORDS = 5000;
    static final long MAX_JSON_BYTES = 4L * 1024 * 1024;
    static final long MAX_TSV_BYTES = 16L * 1024 * 1024;
    static final long MAX_COMPRESSION_RATIO = 200;

    private static final Pattern IDENTIFIER =
            Pattern.compile("^[a-z0-9][a-z0-9._-]{1,127}$");
    private static final Pattern VERSION =
            Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern HASH = Pattern.compile("^[a-f0-9]{64}$");
    private static final Set<String> REQUIRED_FILES = Set.of(
            "manifest.json",
            "vocabulary.tsv",
            "bindings.json",
            "story.json",
            "cards.json",
            "encounters.json",
            "characters.json",
            "assets.json");
    private static final Set<String> OPTIONAL_ROOT_FILES =
            Set.of("README.md", "LICENSE.txt");
    private static final Set<String> NODE_TYPES = Set.of(
            "dialogue", "choice", "explore", "encounter", "camp", "chapter-end");
    private static final Set<String> CARD_EFFECTS = Set.of(
            "damage", "block", "heal", "draw", "apply-burn");
    private static final Set<String> EXPRESSIONS = Set.of(
            "neutral", "welcome", "cheer", "concern", "relief", "surprise");
    private static final Set<String> REJECTED_EXTENSIONS = Set.of(
            "svg", "html", "htm", "js", "mjs", "cjs", "glb", "gltf", "exe", "sh");
    private static final Set<String> ASSET_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "wav", "ogg", "mp3");

    private final ObjectMapper objectMapper;

    public ContentPackageValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidatedContentPackage validate(
            InputStream packageInput,
            long declaredSize,
            String fileName,
            Path workspace,
            String engineVersion) {
        requireText(fileName, "文件名不能为空");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".fmbook")) {
            throw invalid("内容包必须使用 .fmbook 扩展名");
        }
        if (declaredSize <= 0 || declaredSize > MAX_ARCHIVE_BYTES) {
            throw invalid("内容包必须大于 0 且不超过 50 MiB");
        }
        Path expanded = workspace.resolve("expanded");
        Path archive = workspace.resolve("package.fmbook");
        try {
            Files.createDirectories(expanded);
            copyLimited(
                    packageInput,
                    archive,
                    MAX_ARCHIVE_BYTES,
                    "内容包超过 50 MiB 上限");
            extract(archive, expanded);
            requireCoreFiles(expanded);
            String contentHash = contentHash(expanded);
            JsonNode manifest = readJson(expanded, "manifest.json", MAX_JSON_BYTES);
            PackageIdentity identity = validateManifest(manifest, engineVersion);
            List<VocabularyRow> vocabulary = validateVocabulary(expanded);
            StorySummary story = validateStory(expanded);
            if (!story.nodeIds().contains(identity.entryNodeId())) {
                throw invalid("manifest.entryNodeId 不在 story 节点中");
            }
            validateBindings(expanded, vocabulary, story.nodeIds());
            CardSummary cards = validateCards(expanded);
            EncounterSummary encounters = validateEncounters(expanded, cards.cardIds());
            validateStoryReferences(story, encounters.encounterIds());
            CharacterSummary characters = validateCharacters(expanded, cards.cardIds());
            List<ContentPackageDtos.AssetView> assets =
                    validateAssets(expanded, characters.assetPaths());
            validateReachability(story, identity.entryNodeId());
            return new ValidatedContentPackage(
                    fileName,
                    identity.title(),
                    identity.namespace(),
                    identity.bookId(),
                    identity.contentVersion(),
                    contentHash,
                    engineVersion,
                    identity.entryNodeId(),
                    identity.schemaVersion(),
                    objectMapper.writeValueAsString(manifest),
                    vocabulary.size(),
                    story.chapterCount(),
                    story.nodeIds().size(),
                    characters.count(),
                    cards.cardIds().size(),
                    encounters.encounterIds().size(),
                    assets,
                    expanded);
        } catch (ZipException exception) {
            throw invalid("内容包不是有效的未加密 ZIP");
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CONTENT_PACKAGE_INVALID",
                    "内容包读取失败：" + exception.getMessage());
        }
    }

    private void extract(Path archive, Path expanded) throws IOException {
        Set<String> normalizedPaths = new HashSet<>();
        long totalExpanded = 0;
        long totalCompressed = 0;
        int countedEntries = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            var entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                countedEntries += 1;
                if (countedEntries > MAX_ENTRIES) {
                    throw invalid("内容包文件数超过 1000 个上限");
                }
                if (entry.getGeneralPurposeBit().usesEncryption()) {
                    throw invalid("内容包不得加密");
                }
                if (entry.isUnixSymlink()) {
                    throw invalid("内容包不得包含符号链接");
                }
                String path = normalizeEntryName(entry.getName());
                String duplicateKey = path.toLowerCase(Locale.ROOT);
                if (!normalizedPaths.add(duplicateKey)) {
                    throw invalid("内容包包含重复或大小写冲突路径：" + path);
                }
                rejectExecutableContent(path);
                long declaredExpandedSize = entry.getSize();
                if (declaredExpandedSize < 0 || declaredExpandedSize > MAX_SINGLE_FILE_BYTES) {
                    throw invalid("单个内容文件超过 20 MiB 上限：" + path);
                }
                totalExpanded = Math.addExact(totalExpanded, declaredExpandedSize);
                if (totalExpanded > MAX_EXPANDED_BYTES) {
                    throw invalid("内容包解压总量超过 200 MiB 上限");
                }
                totalCompressed = Math.addExact(totalCompressed, Math.max(0, entry.getCompressedSize()));
                Path target = expanded.resolve(path).normalize();
                if (!target.startsWith(expanded)) {
                    throw invalid("内容包路径越界：" + path);
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    copyLimited(
                            input,
                            target,
                            Math.min(MAX_SINGLE_FILE_BYTES, declaredExpandedSize + 1),
                            "内容文件实际大小超过声明或上限：" + path);
                }
                if (Files.size(target) != declaredExpandedSize) {
                    throw invalid("内容文件实际大小与 ZIP 声明不一致：" + path);
                }
            }
        }
        if (countedEntries == 0) {
            throw invalid("内容包为空");
        }
        if (totalCompressed == 0 && totalExpanded > 0) {
            throw invalid("内容包压缩信息无效");
        }
        if (totalCompressed > 0
                && totalExpanded / Math.max(1, totalCompressed) > MAX_COMPRESSION_RATIO) {
            throw invalid("内容包压缩比异常");
        }
    }

    private void requireCoreFiles(Path expanded) throws IOException {
        for (String required : REQUIRED_FILES) {
            if (!Files.isRegularFile(expanded.resolve(required))) {
                throw invalid("缺少内容包文件：" + required);
            }
        }
        try (Stream<Path> paths = Files.walk(expanded)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = expanded.relativize(path).toString().replace('\\', '/');
                if (!relative.contains("/")
                        && !REQUIRED_FILES.contains(relative)
                        && !OPTIONAL_ROOT_FILES.contains(relative)) {
                    throw invalid("内容包根目录包含未知文件：" + relative);
                }
            }
        }
    }

    private PackageIdentity validateManifest(JsonNode manifest, String engineVersion) {
        requireObject(manifest, "manifest.json 必须是对象");
        int schemaVersion = requiredInt(manifest, "schemaVersion", 1, 1);
        String namespace = requiredIdentifier(manifest, "namespace");
        String bookId = requiredIdentifier(manifest, "bookId");
        String contentVersion = requiredVersion(manifest, "contentVersion");
        String title = requiredText(manifest, "title", 200);
        String entryNodeId = requiredIdentifier(manifest, "entryNodeId");
        JsonNode engineRange = requiredObjectField(manifest, "engineRange");
        String minInclusive = requiredVersion(engineRange, "minInclusive");
        String maxExclusive = requiredVersion(engineRange, "maxExclusive");
        int compareMin = compareVersions(engineVersion, minInclusive);
        int compareMax = compareVersions(engineVersion, maxExclusive);
        if (compareMin < 0 || compareMax >= 0) {
            throw invalid(
                    "内容包不支持当前引擎版本 "
                            + engineVersion
                            + "，要求 ["
                            + minInclusive
                            + ", "
                            + maxExclusive
                            + ")");
        }
        JsonNode capabilities = requiredArray(manifest, "capabilities");
        Set<String> supported = Set.of("card-battle-v1");
        boolean hasBattle = false;
        for (JsonNode capability : capabilities) {
            String value = requireTextNode(capability, "capability");
            if (!supported.contains(value)) {
                throw invalid("内容包声明了未支持能力：" + value);
            }
            hasBattle |= "card-battle-v1".equals(value);
        }
        if (!hasBattle) {
            throw invalid("内容包必须声明 card-battle-v1 能力");
        }
        if (manifest.path("requires").isArray()) {
            for (JsonNode requirement : manifest.path("requires")) {
                requiredIdentifier(requirement, "bookId");
                requiredText(requirement, "endingId", 128);
            }
        }
        return new PackageIdentity(
                schemaVersion,
                namespace,
                bookId,
                contentVersion,
                title,
                entryNodeId);
    }

    private List<VocabularyRow> validateVocabulary(Path expanded) throws IOException {
        Path path = expanded.resolve("vocabulary.tsv");
        if (Files.size(path) > MAX_TSV_BYTES) {
            throw invalid("vocabulary.tsv 超过 16 MiB 上限");
        }
        List<VocabularyRow> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(
                     reader,
                     CSVFormat.TDF.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .get())) {
            List<String> headers = parser.getHeaderNames();
            if (!headers.equals(List.of(
                    "wordKey", "term", "meaning", "acceptedAnswers", "hints"))) {
                throw invalid("vocabulary.tsv 表头不符合固定格式");
            }
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_WORDS) {
                    throw invalid("词条数超过 5000 上限");
                }
                String wordKey = identifier(record.get("wordKey"), "wordKey");
                if (!keys.add(wordKey)) {
                    throw invalid("wordKey 重复：" + wordKey);
                }
                String term = bounded(record.get("term"), 1, 512, "term");
                String meaning = bounded(record.get("meaning"), 1, 4000, "meaning");
                List<String> accepted = stringArrayCell(
                        record.get("acceptedAnswers"),
                        "acceptedAnswers",
                        20,
                        512);
                List<String> hints =
                        stringArrayCell(record.get("hints"), "hints", 20, 2000);
                rows.add(new VocabularyRow(wordKey, term, meaning, accepted, hints));
            }
        }
        if (rows.isEmpty()) {
            throw invalid("内容包至少需要一个词条");
        }
        return rows;
    }

    private StorySummary validateStory(Path expanded) throws IOException {
        JsonNode story = readJson(expanded, "story.json", MAX_JSON_BYTES);
        JsonNode chapters = requiredArray(story, "chapters");
        if (chapters.isEmpty()) {
            throw invalid("story.chapters 不能为空");
        }
        Set<String> nodeIds = new HashSet<>();
        Map<String, Set<String>> transitions = new HashMap<>();
        Map<String, String> encounterByNode = new HashMap<>();
        boolean hasEnding = false;
        for (JsonNode chapter : chapters) {
            requiredIdentifier(chapter, "chapterId");
            requiredText(chapter, "title", 200);
            JsonNode nodes = requiredArray(chapter, "nodes");
            if (nodes.isEmpty()) {
                throw invalid("每个章节至少需要一个节点");
            }
            for (JsonNode node : nodes) {
                String nodeId = requiredIdentifier(node, "nodeId");
                String type = requiredText(node, "type", 32);
                if (!NODE_TYPES.contains(type)) {
                    throw invalid("未知节点类型：" + type);
                }
                if (!nodeIds.add(nodeId)) {
                    throw invalid("节点 ID 重复：" + nodeId);
                }
                hasEnding |= "chapter-end".equals(type);
                Set<String> targets = transitions.computeIfAbsent(
                        nodeId,
                        ignored -> new HashSet<>());
                if ("choice".equals(type) && requiredArray(node, "choices").isEmpty()) {
                    throw invalid("choice 节点至少需要一个选项：" + nodeId);
                }
                if (node.has("nextNodeId") && !node.path("nextNodeId").isNull()) {
                    targets.add(requiredIdentifier(node, "nextNodeId"));
                }
                if (node.has("choices")) {
                    JsonNode choices = requiredArray(node, "choices");
                    Set<String> choiceIds = new HashSet<>();
                    for (JsonNode choice : choices) {
                        String choiceId = requiredIdentifier(choice, "choiceId");
                        if (!choiceIds.add(choiceId)) {
                            throw invalid("节点 " + nodeId + " 的 choiceId 重复：" + choiceId);
                        }
                        requiredText(choice, "label", 512);
                        targets.add(requiredIdentifier(choice, "targetNodeId"));
                    }
                }
                if ("encounter".equals(type)) {
                    encounterByNode.put(
                            nodeId,
                            requiredIdentifier(node, "encounterId"));
                    targets.add(requiredIdentifier(node, "victoryNodeId"));
                    targets.add(requiredIdentifier(node, "rescueNodeId"));
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : transitions.entrySet()) {
            for (String target : entry.getValue()) {
                if (!nodeIds.contains(target)) {
                    throw invalid("节点 " + entry.getKey() + " 指向不存在节点 " + target);
                }
            }
        }
        if (!hasEnding) {
            throw invalid("故事至少需要一个 chapter-end 节点");
        }
        return new StorySummary(
                chapters.size(),
                nodeIds,
                transitions,
                encounterByNode);
    }

    private void validateBindings(
            Path expanded,
            List<VocabularyRow> vocabulary,
            Set<String> nodeIds) throws IOException {
        JsonNode root = readJson(expanded, "bindings.json", MAX_JSON_BYTES);
        JsonNode bindings = requiredArray(root, "bindings");
        Set<String> wordKeys = new HashSet<>();
        for (VocabularyRow row : vocabulary) {
            wordKeys.add(row.wordKey());
        }
        Set<String> boundWords = new HashSet<>();
        for (JsonNode binding : bindings) {
            String nodeId = requiredIdentifier(binding, "nodeId");
            if (!nodeIds.contains(nodeId)) {
                throw invalid("词条绑定引用不存在节点：" + nodeId);
            }
            JsonNode keys = requiredArray(binding, "wordKeys");
            if (keys.isEmpty()) {
                throw invalid("词条绑定不能为空：" + nodeId);
            }
            for (JsonNode keyNode : keys) {
                String wordKey = identifier(requireTextNode(keyNode, "wordKey"), "wordKey");
                if (!wordKeys.contains(wordKey)) {
                    throw invalid("词条绑定引用不存在 wordKey：" + wordKey);
                }
                boundWords.add(wordKey);
            }
        }
        if (!boundWords.equals(wordKeys)) {
            Set<String> missing = new TreeSet<>(wordKeys);
            missing.removeAll(boundWords);
            throw invalid("以下词条没有剧情绑定：" + String.join(", ", missing));
        }
    }

    private CardSummary validateCards(Path expanded) throws IOException {
        JsonNode root = readJson(expanded, "cards.json", MAX_JSON_BYTES);
        JsonNode cards = requiredArray(root, "cards");
        Set<String> cardIds = new HashSet<>();
        for (JsonNode card : cards) {
            String cardId = requiredIdentifier(card, "cardId");
            if (!cardIds.add(cardId)) {
                throw invalid("卡牌 ID 重复：" + cardId);
            }
            requiredText(card, "name", 120);
            requiredInt(card, "cost", 0, 9);
            requiredText(card, "target", 16);
            JsonNode effects = requiredArray(card, "effects");
            if (effects.isEmpty()) {
                throw invalid("卡牌效果不能为空：" + cardId);
            }
            for (JsonNode effect : effects) {
                String type = requiredText(effect, "type", 32);
                if (!CARD_EFFECTS.contains(type)) {
                    throw invalid("未知卡牌效果：" + type);
                }
            }
        }
        return new CardSummary(cardIds);
    }

    private EncounterSummary validateEncounters(Path expanded, Set<String> cardIds)
            throws IOException {
        JsonNode root = readJson(expanded, "encounters.json", MAX_JSON_BYTES);
        JsonNode encounters = requiredArray(root, "encounters");
        Set<String> encounterIds = new HashSet<>();
        for (JsonNode encounter : encounters) {
            String encounterId = requiredIdentifier(encounter, "encounterId");
            if (!encounterIds.add(encounterId)) {
                throw invalid("遭遇 ID 重复：" + encounterId);
            }
            String rulesVersion = requiredText(encounter, "battleRulesVersion", 64);
            if (!"card-battle-v1".equals(rulesVersion)) {
                throw invalid("不支持的战斗规则版本：" + rulesVersion);
            }
            JsonNode rewardCardIds = requiredArray(encounter, "rewardCardIds");
            validateReferences(rewardCardIds, cardIds, "奖励卡牌");
        }
        return new EncounterSummary(encounterIds);
    }

    private void validateStoryReferences(
            StorySummary story,
            Set<String> encounterIds) {
        for (Map.Entry<String, String> entry : story.encounterByNode().entrySet()) {
            String encounterId = entry.getValue();
            if (!encounterIds.contains(encounterId)) {
                throw invalid(
                        "节点 " + entry.getKey()
                                + " 引用了不存在的 encounterId：" + encounterId);
            }
        }
    }

    private CharacterSummary validateCharacters(Path expanded, Set<String> cardIds)
            throws IOException {
        JsonNode root = readJson(expanded, "characters.json", MAX_JSON_BYTES);
        JsonNode characters = requiredArray(root, "characters");
        Set<String> characterKeys = new HashSet<>();
        Set<String> assetPaths = new HashSet<>();
        for (JsonNode character : characters) {
            String characterKey = requiredIdentifier(character, "characterKey");
            if (!characterKeys.add(characterKey)) {
                throw invalid("角色 ID 重复：" + characterKey);
            }
            requiredText(character, "name", 120);
            requiredVersion(character, "definitionVersion");
            JsonNode skillCardIds = character.path("skillCardIds");
            if (!skillCardIds.isMissingNode()) {
                validateReferences(skillCardIds, cardIds, "角色技能卡牌");
            }
            JsonNode portraits = requiredObjectField(character, "portraits");
            Set<String> expressions = new HashSet<>();
            portraits.properties().forEach(property -> {
                String expression = property.getKey();
                if (!EXPRESSIONS.contains(expression)) {
                    throw invalid("未知角色表情：" + expression);
                }
                String assetPath = requireTextNode(property.getValue(), "portrait path");
                if (!assetPath.startsWith("assets/")) {
                    throw invalid("角色立绘必须引用包内 assets/ 资源");
                }
                expressions.add(expression);
                assetPaths.add(assetPath);
            });
            if (!expressions.contains("neutral")) {
                throw invalid("角色 " + characterKey + " 缺少 neutral 立绘");
            }
        }
        return new CharacterSummary(characterKeys.size(), assetPaths);
    }

    private List<ContentPackageDtos.AssetView> validateAssets(
            Path expanded,
            Set<String> referencedAssets) throws IOException {
        JsonNode root = readJson(expanded, "assets.json", MAX_JSON_BYTES);
        JsonNode assets = requiredArray(root, "assets");
        List<ContentPackageDtos.AssetView> views = new ArrayList<>();
        Set<String> declaredAssets = new HashSet<>();
        for (JsonNode asset : assets) {
            String path = normalizeEntryName(requiredText(asset, "path", 512));
            if (!path.startsWith("assets/")) {
                throw invalid("资源路径必须位于 assets/：" + path);
            }
            if (!declaredAssets.add(path)) {
                throw invalid("资源清单重复：" + path);
            }
            String sha = requiredText(asset, "sha256", 64).toLowerCase(Locale.ROOT);
            if (!HASH.matcher(sha).matches()) {
                throw invalid("资源哈希格式无效：" + path);
            }
            String mime = requiredText(asset, "mime", 128);
            long byteSize = requiredLong(asset, "byteSize", 1, MAX_SINGLE_FILE_BYTES);
            requiredText(asset, "kind", 64);
            requiredText(asset, "license", 512);
            Path file = expanded.resolve(path).normalize();
            if (!file.startsWith(expanded) || !Files.isRegularFile(file)) {
                throw invalid("资源清单引用不存在文件：" + path);
            }
            if (Files.size(file) != byteSize) {
                throw invalid("资源大小与清单不一致：" + path);
            }
            String actualHash = fileHash(file);
            if (!actualHash.equals(sha)) {
                throw invalid("资源哈希与清单不一致：" + path);
            }
            validateAssetSignature(file, path, mime);
            views.add(new ContentPackageDtos.AssetView(
                    path,
                    sha,
                    mime,
                    byteSize,
                    requiredText(asset, "kind", 64),
                    requiredText(asset, "license", 512)));
        }
        Path assetsDirectory = expanded.resolve("assets");
        if (Files.isDirectory(assetsDirectory)) {
            try (Stream<Path> files = Files.walk(assetsDirectory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String relative =
                            expanded.relativize(file).toString().replace('\\', '/');
                    if (!declaredAssets.contains(relative)) {
                        throw invalid("assets/ 中存在未声明资源：" + relative);
                    }
                }
            }
        }
        for (String referenced : referencedAssets) {
            if (!declaredAssets.contains(referenced)) {
                throw invalid("角色立绘没有资源清单记录：" + referenced);
            }
        }
        return List.copyOf(views);
    }

    private void validateReachability(StorySummary story, String entryNodeId) {
        Set<String> reached = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(entryNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            if (!reached.add(nodeId)) {
                continue;
            }
            for (String target : story.transitions().getOrDefault(nodeId, Set.of())) {
                queue.addLast(target);
            }
        }
        if (!reached.equals(story.nodeIds())) {
            Set<String> unreachable = new TreeSet<>(story.nodeIds());
            unreachable.removeAll(reached);
            throw invalid("存在不可达节点：" + String.join(", ", unreachable));
        }
    }

    private JsonNode readJson(Path expanded, String file, long maxBytes) throws IOException {
        Path path = expanded.resolve(file);
        if (Files.size(path) > maxBytes) {
            throw invalid(file + " 超过 JSON 大小上限");
        }
        try {
            JsonNode node = objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            if (node == null) {
                throw invalid(file + " 不能为空");
            }
            return node;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid(file + " 不是有效 JSON");
        }
    }

    private void validateAssetSignature(Path file, String path, String mime)
            throws IOException {
        String extension = extension(path);
        String expectedMime = switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "wav" -> "audio/wav";
            case "ogg" -> "audio/ogg";
            case "mp3" -> "audio/mpeg";
            default -> throw invalid("不支持的资源扩展名：" + path);
        };
        if (!expectedMime.equals(mime)) {
            throw invalid("资源 MIME 与扩展名不一致：" + path);
        }
        byte[] header;
        try (InputStream input = Files.newInputStream(file)) {
            header = input.readNBytes(16);
        }
        boolean valid = switch (extension) {
            case "png" -> startsWith(header, new int[] {0x89, 0x50, 0x4E, 0x47});
            case "jpg", "jpeg" -> startsWith(header, new int[] {0xFF, 0xD8, 0xFF});
            case "webp" -> ascii(header, 0, 4).equals("RIFF")
                    && ascii(header, 8, 4).equals("WEBP");
            case "wav" -> ascii(header, 0, 4).equals("RIFF")
                    && ascii(header, 8, 4).equals("WAVE");
            case "ogg" -> ascii(header, 0, 4).equals("OggS");
            case "mp3" -> ascii(header, 0, 3).equals("ID3")
                    || (header.length >= 2
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xE0) == 0xE0);
            default -> false;
        };
        if (!valid) {
            throw invalid("资源内容与声明格式不一致：" + path);
        }
    }

    private List<String> stringArrayCell(
            String raw,
            String field,
            int maxItems,
            int maxLength) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode value = objectMapper.readTree(raw);
            if (!value.isArray() || value.size() > maxItems) {
                throw invalid(field + " 必须是 JSON 字符串数组");
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : value) {
                String text = requireTextNode(item, field);
                if (text.length() > maxLength) {
                    throw invalid(field + " 单项超过 " + maxLength + " 字符");
                }
                result.add(text);
            }
            return result;
        } catch (RuntimeException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw invalid(field + " 不是有效 JSON 字符串数组");
        }
    }

    private void validateReferences(
            JsonNode values,
            Set<String> allowed,
            String description) {
        if (!values.isArray()) {
            throw invalid(description + " 必须是数组");
        }
        for (JsonNode value : values) {
            String id = identifier(requireTextNode(value, description), description);
            if (!allowed.contains(id)) {
                throw invalid(description + " 引用不存在 ID：" + id);
            }
        }
    }

    private String contentHash(Path expanded) throws IOException {
        MessageDigest digest = sha256();
        try (Stream<Path> paths = Files.walk(expanded)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .sorted((left, right) -> expanded.relativize(left).toString()
                            .compareTo(expanded.relativize(right).toString()))
                    .toList();
            for (Path file : files) {
                String relative = expanded.relativize(file).toString().replace('\\', '/');
                digest.update(relative.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(fileHashBytes(file));
                digest.update((byte) 0);
                digest.update(Long.toString(Files.size(file)).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String fileHash(Path path) throws IOException {
        return HexFormat.of().formatHex(fileHashBytes(path));
    }

    private byte[] fileHashBytes(Path path) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return digest.digest();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void copyLimited(
            InputStream input,
            Path target,
            long maxBytes,
            String message) throws IOException {
        long total = 0;
        try (OutputStream output = Files.newOutputStream(
                target,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > maxBytes) {
                    throw invalid(message);
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static String normalizeEntryName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("内容包包含空路径");
        }
        String value = raw.replace('\\', '/');
        if (value.startsWith("/") || value.matches("^[A-Za-z]:/.*")) {
            throw invalid("内容包不得使用绝对路径：" + raw);
        }
        Path normalized = Path.of(value).normalize();
        String path = normalized.toString().replace('\\', '/');
        if (path.isEmpty()
                || path.equals(".")
                || path.startsWith("../")
                || path.contains("/../")
                || path.equals("..")) {
            throw invalid("内容包路径越界：" + raw);
        }
        if (path.length() > 512) {
            throw invalid("内容包路径超过 512 字符：" + path);
        }
        return path;
    }

    private static void rejectExecutableContent(String path) {
        String extension = extension(path);
        if (REJECTED_EXTENSIONS.contains(extension)) {
            throw invalid("内容包禁止可执行或非白名单资源：" + path);
        }
        if (path.startsWith("assets/") && !ASSET_EXTENSIONS.contains(extension)) {
            throw invalid("assets/ 只允许白名单图片与音频：" + path);
        }
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private JsonNode requiredObjectField(JsonNode node, String field) {
        JsonNode value = node.path(field);
        requireObject(value, field + " 必须是对象");
        return value;
    }

    private JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw invalid(field + " 必须是数组");
        }
        return value;
    }

    private String requiredIdentifier(JsonNode node, String field) {
        return identifier(requiredText(node, field, 128), field);
    }

    private String requiredVersion(JsonNode node, String field) {
        String value = requiredText(node, field, 32);
        if (!VERSION.matcher(value).matches()) {
            throw invalid(field + " 必须使用 x.y.z 版本格式");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        return bounded(requireTextNode(node.path(field), field), 1, maxLength, field);
    }

    private int requiredInt(JsonNode node, String field, int min, int max) {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.intValue() < min || value.intValue() > max) {
            throw invalid(field + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return value.intValue();
    }

    private long requiredLong(JsonNode node, String field, long min, long max) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber()
                || value.longValue() < min
                || value.longValue() > max) {
            throw invalid(field + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return value.longValue();
    }

    private static String identifier(String value, String field) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw invalid(field + " 只能使用小写字母、数字、点、下划线和连字符");
        }
        return value;
    }

    private static String bounded(String value, int min, int max, String field) {
        if (value == null || value.length() < min || value.length() > max) {
            throw invalid(field + " 长度必须在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private static void requireObject(JsonNode node, String message) {
        if (node == null || !node.isObject()) {
            throw invalid(message);
        }
    }

    private static String requireTextNode(JsonNode node, String field) {
        if (node == null || !node.isString() || node.stringValue().isBlank()) {
            throw invalid(field + " 必须是非空字符串");
        }
        return node.stringValue();
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < 3; index += 1) {
            int compare = Integer.compare(
                    Integer.parseInt(leftParts[index]),
                    Integer.parseInt(rightParts[index]));
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }

    private static boolean startsWith(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index += 1) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        if (bytes.length < offset + length) {
            return "";
        }
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "CONTENT_PACKAGE_INVALID",
                message);
    }

    private record PackageIdentity(
            int schemaVersion,
            String namespace,
            String bookId,
            String contentVersion,
            String title,
            String entryNodeId) {
    }

    private record VocabularyRow(
            String wordKey,
            String term,
            String meaning,
            List<String> acceptedAnswers,
            List<String> hints) {
    }

    private record StorySummary(
            int chapterCount,
            Set<String> nodeIds,
            Map<String, Set<String>> transitions,
            Map<String, String> encounterByNode) {
    }

    private record CardSummary(Set<String> cardIds) {
    }

    private record EncounterSummary(Set<String> encounterIds) {
    }

    private record CharacterSummary(int count, Set<String> assetPaths) {
    }
}
