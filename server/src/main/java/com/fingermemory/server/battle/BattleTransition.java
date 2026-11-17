package com.fingermemory.server.battle;

import java.util.List;
import tools.jackson.databind.node.ObjectNode;

public record BattleTransition(
        BattleState state,
        List<ObjectNode> events) {
}
