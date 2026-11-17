package com.fingermemory.server.battle;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class BattleCatalog {
    static final String RULES_VERSION = "card-battle-v1";
    static final int SCHEMA_VERSION = 1;
    static final int BASE_ENERGY = 3;
    static final int TURN_DRAW = 4;
    static final int HAND_LIMIT = 6;

    static final List<String> TEACHING_DECK_ORDER = List.of(
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

    static final Map<String, CardDefinition> CARDS = Map.of(
            "traveler-strike", new CardDefinition(
                    "traveler-strike", "旅人突刺", 1, "enemy", false,
                    List.of(new CardEffect("damage", 6, 0, 0))),
            "companion-guard", new CardDefinition(
                    "companion-guard", "同伴守护", 1, "none", false,
                    List.of(new CardEffect("block", 5, 0, 0))),
            "ember-rune", new CardDefinition(
                    "ember-rune", "余烬符文", 1, "enemy", false,
                    List.of(
                            new CardEffect("damage", 3, 0, 0),
                            new CardEffect("apply-burn", 0, 2, 2))),
            "herb-pouch", new CardDefinition(
                    "herb-pouch", "采药包", 1, "none", false,
                    List.of(new CardEffect("heal", 4, 0, 0))),
            "observe-signpost", new CardDefinition(
                    "observe-signpost", "观察路标", 0, "none", true,
                    List.of(new CardEffect("draw", 1, 0, 0))),
            "party-strike", new CardDefinition(
                    "party-strike", "旅团合击", 2, "enemy", false,
                    List.of(new CardEffect("damage", 10, 0, 0))));

    static final Set<String> COMPANIONS = Set.of("lia", "serin", "fina");

    private BattleCatalog() {
    }

    record CardDefinition(
            String id,
            String name,
            int cost,
            String target,
            boolean exhaust,
            List<CardEffect> effects) {
    }

    record CardEffect(
            String type,
            int amount,
            int remainingTurns,
            int damagePerTurn) {
    }
}
