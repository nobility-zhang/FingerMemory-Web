package com.fingermemory.server.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class GmContentDtos {
    private GmContentDtos() {
    }

    public record Content(
            String id,
            String namespace,
            String bookId,
            String contentVersion,
            String contentHash,
            String title,
            String seriesId,
            String entryNodeId,
            int wordCount,
            int chapterCount,
            int nodeCount,
            int characterCount,
            int cardCount,
            int encounterCount,
            int assetCount,
            String status,
            List<String> issues,
            long version,
            long createdAtMs,
            long updatedAtMs,
            Long publishedAtMs,
            Long archivedAtMs) {
    }

    public record ContentPage(List<Content> items, String nextCursor) {
    }

    public record Preview(
            Content content,
            List<Chapter> chapters,
            List<Character> characters,
            List<Scene> scenes,
            List<Asset> assets) {
    }

    public record Chapter(
            String chapterId,
            String title,
            List<Node> nodes) {
    }

    public record Node(
            String nodeId,
            String chapterId,
            String type,
            String title,
            String speaker,
            String text,
            String nextNodeId,
            String encounterId,
            String encounterName,
            String encounterDescription,
            String portraitCharacterId,
            String sceneId,
            String costumeId,
            String endingId,
            String endingTitle,
            List<String> inheritedFlags,
            List<Choice> choices) {
    }

    public record Choice(
            String choiceId,
            String label,
            String targetNodeId) {
    }

    public record Character(
            String characterKey,
            String name,
            String epithet,
            String description,
            int age,
            String defaultCostumeId,
            Map<String, Map<String, String>> costumes,
            Dialogue dialogue,
            Skill skill) {
    }

    public record Dialogue(
            String welcome,
            String again,
            String rest,
            String training,
            String practice,
            String result) {
    }

    public record Skill(
            String name,
            String description,
            String type,
            int amount) {
    }

    public record Scene(
            String sceneId,
            String name,
            String background,
            String battleTheme,
            String ambientKey) {
    }

    public record Asset(
            String path,
            String mime,
            long byteSize,
            String kind,
            String license,
            String url) {
    }

    public record ContentAction(
            @NotBlank
            @Pattern(regexp = "^(validate|publish|archive)$")
            String action,
            @NotBlank @Size(max = 64) String expectedContentHash,
            @NotBlank @Size(max = 300) String reason) {
    }

    public record Catalog(
            String id,
            String title,
            String namespace,
            String bookId,
            String contentVersion,
            String contentHash,
            String seriesId,
            String entryNodeId,
            int wordCount,
            int chapterCount,
            int nodeCount,
            int characterCount,
            int cardCount,
            int encounterCount,
            int assetCount,
            long publishedAtMs) {
    }
}
