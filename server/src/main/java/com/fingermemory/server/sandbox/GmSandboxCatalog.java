package com.fingermemory.server.sandbox;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GmSandboxCatalog {
    public static final String TEMPLATE_ID = "card-battle-v1-tutorial";
    public static final int SCHEMA_VERSION = 1;
    public static final int FIXTURE_VERSION_NUMBER = 1;
    public static final String FIXTURE_VERSION = "bridge-tutorial-v1";
    public static final String INITIAL_NODE_ID = "bridge";
    public static final String INITIAL_ENCOUNTER_ID = "bridge-guardian";
    public static final long INITIAL_RANDOM_SEED = 0x5eed1234L;
    public static final int MAX_DECK_SIZE = 18;
    public static final int MAX_CARD_COPIES = 6;

    public static final List<String> NODES = List.of(
            "prologue", "bridge", "camp", "chapter-end");
    public static final List<String> CARDS = List.of(
            "traveler-strike",
            "companion-guard",
            "ember-rune",
            "herb-pouch",
            "observe-signpost",
            "party-strike");
    public static final List<String> CHARACTERS = List.of("lia", "serin", "fina");
    public static final List<String> INITIAL_DECK = List.of(
            "companion-guard",
            "traveler-strike",
            "ember-rune",
            "observe-signpost",
            "traveler-strike",
            "companion-guard",
            "herb-pouch",
            "traveler-strike",
            "ember-rune",
            "party-strike",
            "companion-guard",
            "party-strike");
    public static final List<String> INITIAL_CHARACTERS = List.of("lia", "serin");

    private static final Map<String, String> ENCOUNTERS = Map.of(
            "bridge", INITIAL_ENCOUNTER_ID);

    private GmSandboxCatalog() {
    }

    public static GmSandboxState initialState() {
        return new GmSandboxState(
                SCHEMA_VERSION,
                FIXTURE_VERSION_NUMBER,
                FIXTURE_VERSION,
                INITIAL_NODE_ID,
                INITIAL_ENCOUNTER_ID,
                "real",
                null,
                INITIAL_RANDOM_SEED,
                INITIAL_DECK,
                INITIAL_CHARACTERS);
    }

    public static String encounterForNode(String nodeId) {
        return ENCOUNTERS.get(nodeId);
    }

    public static Set<String> nodes() {
        return Set.copyOf(NODES);
    }

    public static Set<String> cards() {
        return Set.copyOf(CARDS);
    }

    public static Set<String> characters() {
        return Set.copyOf(CHARACTERS);
    }
}
