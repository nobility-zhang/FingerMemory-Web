package com.fingermemory.server.adventure;

public record AdventureRewardOfferRow(
        String ownerId,
        String runId,
        String offerId,
        String nodeId,
        String targetNodeId,
        String candidatesJson,
        String status,
        String selectedChoiceId,
        String selectedCardDefinitionId,
        long createdAtMs,
        long updatedAtMs) {
}
