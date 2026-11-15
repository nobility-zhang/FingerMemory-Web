package com.fingermemory.server.lexicon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class LexiconDtos {
    private LexiconDtos() {
    }

    public record CreateLexiconRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description) {
    }

    public record UpdateLexiconRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @PositiveOrZero long version) {
    }

    public record ArchiveLexiconRequest(@PositiveOrZero long version) {
    }

    public record LexiconView(
            String id,
            String name,
            String description,
            String source,
            Long archivedAtMs,
            long createdAtMs,
            long updatedAtMs,
            long version) {
        public static LexiconView from(Lexicon lexicon) {
            return new LexiconView(
                    lexicon.getId(),
                    lexicon.getName(),
                    lexicon.getDescription(),
                    lexicon.getSource(),
                    lexicon.getArchivedAtMs(),
                    lexicon.getCreatedAtMs(),
                    lexicon.getUpdatedAtMs(),
                    lexicon.getVersion());
        }
    }
}
