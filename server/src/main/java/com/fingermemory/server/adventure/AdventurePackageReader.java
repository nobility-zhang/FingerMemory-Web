package com.fingermemory.server.adventure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdventurePackageReader {
    private final ObjectMapper objectMapper;

    public AdventurePackageReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AdventurePackage read(Path root) {
        try {
            JsonNode story = objectMapper.readTree(
                    Files.readString(root.resolve("story.json"), StandardCharsets.UTF_8));
            Map<String, AdventureNode> nodes = new LinkedHashMap<>();
            for (JsonNode chapter : story.path("chapters")) {
                String chapterId = chapter.path("chapterId").asText();
                for (JsonNode node : chapter.path("nodes")) {
                    AdventureNode parsed = parseNode(chapterId, node);
                    nodes.put(parsed.nodeId(), parsed);
                }
            }
            List<Vocabulary> vocabulary = readVocabulary(root.resolve("vocabulary.tsv"));
            Map<String, AdventureCard> cards = readCards(root.resolve("cards.json"));
            Map<String, AdventureEncounter> encounters =
                    readEncounters(root.resolve("encounters.json"));
            Map<String, AdventureCharacter> characters =
                    readCharacters(root.resolve("characters.json"));
            Map<String, AdventureScene> scenes = readScenes(root.resolve("scenes.json"));
            return new AdventurePackage(
                    Map.copyOf(nodes),
                    List.copyOf(vocabulary),
                    Map.copyOf(cards),
                    Map.copyOf(encounters),
                    Map.copyOf(characters),
                    Map.copyOf(scenes));
        } catch (IOException exception) {
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_CONTENT_UNAVAILABLE",
                    "已安装内容无法读取");
        } catch (RuntimeException exception) {
            if (exception instanceof AdventureException adventureException) {
                throw adventureException;
            }
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_CONTENT_INVALID",
                    "已安装内容结构不再兼容");
        }
    }

    private AdventureNode parseNode(String chapterId, JsonNode node) {
        List<AdventureChoice> choices = new ArrayList<>();
        for (JsonNode choice : node.path("choices")) {
            choices.add(new AdventureChoice(
                    text(choice, "choiceId", choice.path("targetNodeId").asText()),
                    text(choice, "label", "继续"),
                    text(choice, "targetNodeId", "")));
        }
        return new AdventureNode(
                text(node, "nodeId", ""),
                chapterId,
                text(node, "type", ""),
                text(node, "title", ""),
                text(node, "speaker", ""),
                text(node, "text", ""),
                text(node, "nextNodeId", null),
                text(node, "encounterId", null),
                text(node, "victoryNodeId", null),
                text(node, "rescueNodeId", null),
                text(node, "unlockCharacterId", null),
                text(node, "recollectionId", null),
                text(node, "recollectionTitle", null),
                text(node, "recollectionText", null),
                text(node, "portraitCharacterId", null),
                text(node, "sceneId", null),
                text(node, "costumeId", null),
                text(node, "encounterName", null),
                text(node, "encounterDescription", null),
                List.copyOf(choices));
    }

    private Map<String, AdventureCard> readCards(Path path) throws IOException {
        JsonNode root = readJson(path);
        Map<String, AdventureCard> cards = new LinkedHashMap<>();
        for (JsonNode card : root.path("cards")) {
            String cardId = text(card, "cardId", "");
            cards.put(cardId, new AdventureCard(
                    cardId,
                    text(card, "name", cardId),
                    text(card, "description", "")));
        }
        return cards;
    }

    private Map<String, AdventureEncounter> readEncounters(Path path)
            throws IOException {
        JsonNode root = readJson(path);
        Map<String, AdventureEncounter> encounters = new LinkedHashMap<>();
        for (JsonNode encounter : root.path("encounters")) {
            String encounterId = text(encounter, "encounterId", "");
            List<String> rewardCardIds = new ArrayList<>();
            for (JsonNode rewardCardId : encounter.path("rewardCardIds")) {
                if (rewardCardId.isString()) {
                    rewardCardIds.add(rewardCardId.stringValue());
                }
            }
            encounters.put(
                    encounterId,
                    new AdventureEncounter(
                            encounterId,
                            List.copyOf(rewardCardIds)));
        }
        return encounters;
    }

    private Map<String, AdventureCharacter> readCharacters(Path path)
            throws IOException {
        JsonNode root = readJson(path);
        Map<String, AdventureCharacter> characters = new LinkedHashMap<>();
        for (JsonNode character : root.path("characters")) {
            String characterKey = text(character, "characterKey", "");
            Map<String, Map<String, String>> costumes = new LinkedHashMap<>();
            JsonNode costumeNode = character.path("costumes");
            if (costumeNode.isObject()) {
                costumeNode.properties().forEach(costume -> {
                    Map<String, String> portraits = new LinkedHashMap<>();
                    costume.getValue().properties().forEach(portrait ->
                            portraits.put(
                                    portrait.getKey(),
                                    stringValue(portrait.getValue(), "")));
                    costumes.put(costume.getKey(), Map.copyOf(portraits));
                });
            } else {
                Map<String, String> portraits = new LinkedHashMap<>();
                character.path("portraits").properties().forEach(portrait ->
                        portraits.put(
                                portrait.getKey(),
                                stringValue(portrait.getValue(), "")));
                costumes.put("summer-travel", Map.copyOf(portraits));
            }
            JsonNode skill = character.path("skill");
            AdventureSkill parsedSkill = skill.isObject()
                    ? new AdventureSkill(
                            text(skill, "name", "伙伴协助"),
                            text(skill, "description", ""),
                            text(skill, "type", "block"),
                            skill.path("amount").asInt(0))
                    : null;
            characters.put(
                    characterKey,
                    new AdventureCharacter(
                            characterKey,
                            text(character, "name", characterKey),
                            text(character, "epithet", ""),
                            text(character, "description", ""),
                            character.path("age").asInt(0),
                            text(character, "defaultCostumeId", "summer-travel"),
                            Map.copyOf(costumes),
                            readDialogue(character.path("dialogue")),
                            parsedSkill));
        }
        return characters;
    }

    private AdventureDialogue readDialogue(JsonNode dialogue) {
        return new AdventureDialogue(
                text(dialogue, "welcome", ""),
                text(dialogue, "again", ""),
                text(dialogue, "rest", ""),
                text(dialogue, "training", ""),
                text(dialogue, "practice", ""),
                text(dialogue, "result", ""));
    }

    private Map<String, AdventureScene> readScenes(Path path) throws IOException {
        Map<String, AdventureScene> scenes = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) {
            return scenes;
        }
        JsonNode root = readJson(path);
        for (JsonNode scene : root.path("scenes")) {
            String sceneId = text(scene, "sceneId", "");
            scenes.put(sceneId, new AdventureScene(
                    sceneId,
                    text(scene, "name", sceneId),
                    text(scene, "background", ""),
                    text(scene, "battleTheme", "bridge"),
                    text(scene, "ambientKey", null)));
        }
        return scenes;
    }

    private List<Vocabulary> readVocabulary(Path path) throws IOException {
        List<Vocabulary> rows = new ArrayList<>();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(
                     reader,
                     CSVFormat.TDF.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .get())) {
            for (CSVRecord record : parser) {
                rows.add(new Vocabulary(
                        record.get("wordKey"),
                        record.get("term"),
                        record.get("meaning"),
                        record.get("acceptedAnswers"),
                        record.get("hints")));
            }
        }
        return rows;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return stringValue(value, fallback);
    }

    private static String stringValue(JsonNode value, String fallback) {
        return value.isString() && !value.stringValue().isBlank()
                ? value.stringValue()
                : fallback;
    }

    private JsonNode readJson(Path path) throws IOException {
        return objectMapper.readTree(
                Files.readString(path, StandardCharsets.UTF_8));
    }

    public record AdventurePackage(
            Map<String, AdventureNode> nodes,
            List<Vocabulary> vocabulary,
            Map<String, AdventureCard> cards,
            Map<String, AdventureEncounter> encounters,
            Map<String, AdventureCharacter> characters,
            Map<String, AdventureScene> scenes) {
    }

    public record AdventureNode(
            String nodeId,
            String chapterId,
            String type,
            String title,
            String speaker,
            String text,
            String nextNodeId,
            String encounterId,
            String victoryNodeId,
            String rescueNodeId,
            String unlockCharacterId,
            String recollectionId,
            String recollectionTitle,
            String recollectionText,
            String portraitCharacterId,
            String sceneId,
            String costumeId,
            String encounterName,
            String encounterDescription,
            List<AdventureChoice> choices) {
    }

    public record AdventureChoice(
            String choiceId,
            String label,
            String targetNodeId) {
    }

    public record Vocabulary(
            String wordKey,
            String term,
            String meaning,
            String acceptedAnswersJson,
            String hintsJson) {
    }

    public record AdventureCard(
            String cardId,
            String name,
            String description) {
    }

    public record AdventureEncounter(
            String encounterId,
            List<String> rewardCardIds) {
    }

    public record AdventureCharacter(
            String characterKey,
            String name,
            String epithet,
            String description,
            int age,
            String defaultCostumeId,
            Map<String, Map<String, String>> costumes,
            AdventureDialogue dialogue,
            AdventureSkill skill) {
    }

    public record AdventureDialogue(
            String welcome,
            String again,
            String rest,
            String training,
            String practice,
            String result) {
    }

    public record AdventureSkill(
            String name,
            String description,
            String type,
            int amount) {
    }

    public record AdventureScene(
            String sceneId,
            String name,
            String background,
            String battleTheme,
            String ambientKey) {
    }
}
