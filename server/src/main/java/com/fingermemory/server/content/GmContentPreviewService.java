package com.fingermemory.server.content;

import com.fingermemory.server.common.ApiException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class GmContentPreviewService {
    private final ObjectMapper objectMapper;

    public GmContentPreviewService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GmContentDtos.Preview read(
            GmContentRow row,
            GmContentDtos.Content content,
            Path packageRoot,
            List<GmContentAssetRow> assets) {
        try {
            List<GmContentDtos.Chapter> chapters =
                    readChapters(packageRoot.resolve("story.json"));
            List<GmContentDtos.Character> characters =
                    readCharacters(row.id(), packageRoot.resolve("characters.json"));
            List<GmContentDtos.Scene> scenes =
                    readScenes(row.id(), packageRoot.resolve("scenes.json"));
            List<GmContentDtos.Asset> assetViews = assets.stream()
                    .map(asset -> new GmContentDtos.Asset(
                            asset.assetPath(),
                            asset.mime(),
                            asset.byteSize(),
                            asset.kind(),
                            asset.license(),
                            assetUrl(row.id(), asset.assetPath())))
                    .toList();
            return new GmContentDtos.Preview(
                    content,
                    chapters,
                    characters,
                    scenes,
                    assetViews);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "GM_CONTENT_PREVIEW_INVALID",
                    "草稿详情无法读取");
        }
    }

    private List<GmContentDtos.Chapter> readChapters(Path path)
            throws IOException {
        JsonNode root = readJson(path);
        List<GmContentDtos.Chapter> chapters = new ArrayList<>();
        for (JsonNode chapter : root.path("chapters")) {
            String chapterId = text(chapter, "chapterId", "");
            List<GmContentDtos.Node> nodes = new ArrayList<>();
            for (JsonNode node : chapter.path("nodes")) {
                nodes.add(new GmContentDtos.Node(
                        text(node, "nodeId", ""),
                        chapterId,
                        text(node, "type", ""),
                        text(node, "title", ""),
                        text(node, "speaker", null),
                        text(node, "text", ""),
                        text(node, "nextNodeId", null),
                        text(node, "encounterId", null),
                        text(node, "encounterName", null),
                        text(node, "encounterDescription", null),
                        text(node, "portraitCharacterId", null),
                        text(node, "sceneId", null),
                        text(node, "costumeId", null),
                        text(node, "endingId", "main"),
                        text(node, "endingTitle", null),
                        strings(node.path("inheritedFlags")),
                        choices(node.path("choices"))));
            }
            chapters.add(new GmContentDtos.Chapter(
                    chapterId,
                    text(chapter, "title", chapterId),
                    List.copyOf(nodes)));
        }
        return List.copyOf(chapters);
    }

    private List<GmContentDtos.Character> readCharacters(String contentId, Path path)
            throws IOException {
        JsonNode root = readJson(path);
        List<GmContentDtos.Character> characters = new ArrayList<>();
        for (JsonNode character : root.path("characters")) {
            Map<String, Map<String, String>> costumes = costumes(contentId, character);
            JsonNode skill = character.path("skill");
            characters.add(new GmContentDtos.Character(
                    text(character, "characterKey", ""),
                    text(character, "name", ""),
                    text(character, "epithet", ""),
                    text(character, "description", ""),
                    character.path("age").asInt(0),
                    text(
                            character,
                            "defaultCostumeId",
                            costumes.keySet().stream().findFirst().orElse("")),
                    costumes,
                    dialogue(character.path("dialogue")),
                    skill.isObject()
                            ? new GmContentDtos.Skill(
                                    text(skill, "name", ""),
                                    text(skill, "description", ""),
                                    text(skill, "type", ""),
                                    skill.path("amount").asInt(0))
                            : null));
        }
        return List.copyOf(characters);
    }

    private Map<String, Map<String, String>> costumes(
            String contentId,
            JsonNode character) {
        Map<String, Map<String, String>> costumes = new LinkedHashMap<>();
        JsonNode costumeNode = character.path("costumes");
        if (costumeNode.isObject()) {
            costumeNode.properties().forEach(costume -> {
                Map<String, String> portraits = new LinkedHashMap<>();
                costume.getValue().properties().forEach(portrait ->
                        portraits.put(
                                portrait.getKey(),
                                assetUrl(contentId, portrait.getValue().asText())));
                costumes.put(costume.getKey(), portraits);
            });
            return costumes;
        }
        Map<String, String> portraits = new LinkedHashMap<>();
        character.path("portraits").properties().forEach(portrait ->
                portraits.put(
                        portrait.getKey(),
                        assetUrl(contentId, portrait.getValue().asText())));
        costumes.put("summer-travel", portraits);
        return costumes;
    }

    private List<GmContentDtos.Scene> readScenes(String contentId, Path path)
            throws IOException {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        JsonNode root = readJson(path);
        List<GmContentDtos.Scene> scenes = new ArrayList<>();
        for (JsonNode scene : root.path("scenes")) {
            scenes.add(new GmContentDtos.Scene(
                    text(scene, "sceneId", ""),
                    text(scene, "name", ""),
                    assetUrl(contentId, text(scene, "background", "")),
                    text(scene, "battleTheme", "bridge"),
                    text(scene, "ambientKey", null)));
        }
        return List.copyOf(scenes);
    }

    private GmContentDtos.Dialogue dialogue(JsonNode dialogue) {
        return new GmContentDtos.Dialogue(
                text(dialogue, "welcome", ""),
                text(dialogue, "again", ""),
                text(dialogue, "rest", ""),
                text(dialogue, "training", ""),
                text(dialogue, "practice", ""),
                text(dialogue, "result", ""));
    }

    private List<GmContentDtos.Choice> choices(JsonNode values) {
        List<GmContentDtos.Choice> choices = new ArrayList<>();
        for (JsonNode choice : values) {
            String target = text(choice, "targetNodeId", "");
            choices.add(new GmContentDtos.Choice(
                    text(choice, "choiceId", target),
                    text(choice, "label", "继续"),
                    target));
        }
        return List.copyOf(choices);
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (!values.isArray()) {
            return List.of();
        }
        for (JsonNode value : values) {
            if (value.isString() && !value.stringValue().isBlank()) {
                result.add(value.stringValue());
            }
        }
        return List.copyOf(result);
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank()
                ? value.stringValue()
                : fallback;
    }

    private JsonNode readJson(Path path) throws IOException {
        return objectMapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
    }

    private String assetUrl(String contentId, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String encodedPath = Arrays.stream(path.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                        .replace("+", "%20"))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
        return "/api/v1/gm/content/"
                + URLEncoder.encode(contentId, StandardCharsets.UTF_8)
                + "/assets/"
                + encodedPath;
    }
}
