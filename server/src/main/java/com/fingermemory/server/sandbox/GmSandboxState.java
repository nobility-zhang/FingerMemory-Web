package com.fingermemory.server.sandbox;

import java.util.List;

public record GmSandboxState(
        int schemaVersion,
        int fixtureVersion,
        String fixtureKey,
        String currentNodeId,
        String encounterId,
        String clockMode,
        Long virtualNowMs,
        long randomSeed,
        List<String> deck,
        List<String> unlockedCharacters) {

    public GmSandboxState {
        deck = List.copyOf(deck);
        unlockedCharacters = List.copyOf(unlockedCharacters);
    }
}
