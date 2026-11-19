package com.fingermemory.server.content;

public record ContentRequirement(
        String bookId,
        String endingId,
        String endingTitle,
        String minContentVersion,
        String maxContentVersionExclusive) {
}
