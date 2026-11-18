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
            return new AdventurePackage(Map.copyOf(nodes), List.copyOf(vocabulary));
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
                List.copyOf(choices));
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
        return value.isString() && !value.stringValue().isBlank()
                ? value.stringValue()
                : fallback;
    }

    public record AdventurePackage(
            Map<String, AdventureNode> nodes,
            List<Vocabulary> vocabulary) {
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
}
