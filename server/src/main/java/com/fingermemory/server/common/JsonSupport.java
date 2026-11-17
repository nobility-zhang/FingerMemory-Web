package com.fingermemory.server.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class JsonSupport {
    private JsonSupport() {
    }

    public static ObjectNode emptyUserData(ObjectMapper objectMapper) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", 5);
        root.put("dataFormatVersion", 5);
        ObjectNode tables = root.putObject("tables");
        for (String name : List.of(
                "appMeta",
                "words",
                "studyItems",
                "lexiconItems",
                "notes",
                "practiceAttempts",
                "practiceSessions",
                "reviewStates",
                "reviewLogs")) {
            tables.putArray(name);
        }
        return root;
    }

    public static ObjectNode copyObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED", label + " must be an object");
        }
        return ((ObjectNode) node).deepCopy();
    }

    public static ArrayNode requireArray(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw validation(field + " must be an array");
        }
        return (ArrayNode) value;
    }

    public static String requireText(ObjectNode parent, String field, int maxLength) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw validation(field + " must be a string");
        }
        String text = value.textValue();
        if (text.isBlank() || text.length() > maxLength) {
            throw validation(field + " is empty or too long");
        }
        return text;
    }

    public static String optionalText(ObjectNode parent, String field, int maxLength) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        if (!value.isTextual() || value.textValue().length() > maxLength) {
            throw validation(field + " must be a bounded string");
        }
        return value.textValue();
    }

    public static long requireLong(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw validation(field + " must be an integer");
        }
        return value.longValue();
    }

    public static int requireInt(ObjectNode parent, String field) {
        long value = requireLong(parent, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw validation(field + " is out of range");
        }
        return (int) value;
    }

    public static boolean requireBoolean(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw validation(field + " must be a boolean");
        }
        return value.booleanValue();
    }

    public static String requireIdentifier(ObjectNode parent, String field) {
        return requireText(parent, field, 128);
    }

    public static long requireTimestamp(ObjectNode parent, String field) {
        long value = requireLong(parent, field);
        if (value <= 0) {
            throw validation(field + " must be a positive timestamp");
        }
        return value;
    }

    public static ObjectNode findById(ArrayNode array, String field, String id) {
        for (JsonNode value : array) {
            if (value.isObject() && id.equals(value.path(field).asText(null))) {
                return (ObjectNode) value;
            }
        }
        return null;
    }

    public static ObjectNode findById(ArrayNode array, String id) {
        return findById(array, "id", id);
    }

    public static void replaceById(ArrayNode array, String field, ObjectNode replacement) {
        String id = requireIdentifier(replacement, field);
        for (int index = 0; index < array.size(); index += 1) {
            JsonNode value = array.get(index);
            if (value.isObject() && id.equals(value.path(field).asText(null))) {
                array.set(index, replacement);
                return;
            }
        }
        throw new IllegalStateException("Cannot replace missing " + field + ": " + id);
    }

    public static String fingerprint(ObjectMapper objectMapper, JsonNode value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = objectMapper.writeValueAsString(canonicalize(value))
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static JsonNode canonicalize(JsonNode value) {
        if (value == null) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (value.isObject()) {
            Map<String, JsonNode> fields = new TreeMap<>();
            value.properties().forEach(entry -> fields.put(entry.getKey(), canonicalize(entry.getValue())));
            ObjectNode canonical = JsonNodeFactory.instance.objectNode();
            fields.forEach(canonical::set);
            return canonical;
        }
        if (value.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            value.forEach(child -> values.add(canonicalize(child)));
            ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
            values.forEach(canonical::add);
            return canonical;
        }
        return value;
    }

    public static List<ObjectNode> objectValues(ArrayNode array, String label) {
        List<ObjectNode> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isObject()) {
                throw validation(label + " must contain only objects");
            }
            values.add((ObjectNode) value);
        }
        return values;
    }

    public static void requireUniqueIds(ArrayNode array, String field, String label) {
        List<String> ids = objectValues(array, label).stream()
                .map(value -> requireIdentifier(value, field))
                .toList();
        if (ids.size() != ids.stream().distinct().count()) {
            throw validation(label + " contains duplicate ids");
        }
    }

    public static ApiException validation(String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED", message);
    }
}
