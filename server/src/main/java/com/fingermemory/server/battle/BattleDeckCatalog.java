package com.fingermemory.server.battle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BattleDeckCatalog {
    private static final int MIN_DECK_SIZE = 12;
    private static final int MAX_DECK_SIZE = 18;
    private static final int MAX_COPIES = 3;

    private BattleDeckCatalog() {
    }

    public static List<String> teachingDeck() {
        return BattleCatalog.TEACHING_DECK_ORDER;
    }

    public static boolean supports(String definitionId) {
        return BattleCatalog.CARDS.containsKey(definitionId);
    }

    public static String displayName(String definitionId) {
        BattleCatalog.CardDefinition definition =
                BattleCatalog.CARDS.get(definitionId);
        return definition == null ? definitionId : definition.name();
    }

    public static void validateAdventureDeck(List<String> deck) {
        if (deck == null
                || deck.size() < MIN_DECK_SIZE
                || deck.size() > MAX_DECK_SIZE) {
            throw new BattleEngine.BattleRuleException(
                    "adventure deck must contain 12 to 18 cards");
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String definitionId : deck) {
            if (!supports(definitionId)) {
                throw new BattleEngine.BattleRuleException(
                        "adventure deck contains an unsupported card");
            }
            int count = counts.merge(definitionId, 1, Integer::sum);
            if (count > MAX_COPIES) {
                throw new BattleEngine.BattleRuleException(
                        "adventure deck exceeds the three-copy limit");
            }
        }
    }
}
