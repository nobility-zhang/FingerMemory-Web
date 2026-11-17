package com.fingermemory.server.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fingermemory.server.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class GmSandboxEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GmSandboxEngine engine = new GmSandboxEngine();

    @Test
    void jumpsBetweenKnownNodesAndResetsIncompatibleEncounter() {
        GmSandboxState initial = GmSandboxCatalog.initialState();
        GmSandboxState camp = engine.apply(
                initial,
                engine.parse("jump-node", argsWith("nodeId", "camp")));
        assertThat(camp.currentNodeId()).isEqualTo("camp");
        assertThat(camp.encounterId()).isNull();

        GmSandboxState bridge = engine.apply(
                camp,
                engine.parse("jump-node", argsWith("nodeId", "bridge")));
        assertThat(bridge.encounterId()).isEqualTo("bridge-guardian");
    }

    @Test
    void grantsKnownCardsWithinSandboxLimits() {
        GmSandboxState initial = GmSandboxCatalog.initialState();
        ObjectNode args = objectMapper.createObjectNode();
        args.putArray("cardDefinitionIds").add("traveler-strike").add("fina");

        assertThatThrownBy(() -> engine.parse("grant-cards", args))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("卡牌");

        ObjectNode validArgs = objectMapper.createObjectNode();
        validArgs.putArray("cardDefinitionIds").add("traveler-strike");
        GmSandboxState granted = engine.apply(
                initial,
                engine.parse("grant-cards", validArgs));
        assertThat(granted.deck()).hasSize(initial.deck().size() + 1);
        assertThat(granted.deck()).contains("traveler-strike");
    }

    @Test
    void unlocksOnlyOnceAndSupportsFixedAndRealClock() {
        GmSandboxState initial = GmSandboxCatalog.initialState();
        GmSandboxState unlocked = engine.apply(
                initial,
                engine.parse("unlock-character", argsWith("characterKey", "fina")));
        assertThat(unlocked.unlockedCharacters()).contains("fina");
        assertThatThrownBy(() -> engine.apply(
                unlocked,
                engine.parse("unlock-character", argsWith("characterKey", "fina"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("已");

        ObjectNode fixedArgs = objectMapper.createObjectNode();
        fixedArgs.put("mode", "fixed");
        fixedArgs.put("nowMs", 1_700_000_000_000L);
        GmSandboxState fixed = engine.apply(
                initial,
                engine.parse("set-clock", fixedArgs));
        assertThat(fixed.clockMode()).isEqualTo("fixed");
        assertThat(fixed.virtualNowMs()).isEqualTo(1_700_000_000_000L);

        GmSandboxState real = engine.apply(
                fixed,
                engine.parse("set-clock", argsWith("mode", "real")));
        assertThat(real.clockMode()).isEqualTo("real");
        assertThat(real.virtualNowMs()).isNull();
    }

    @Test
    void resetRestoresTheFixtureAndRejectsUnknownArgs() {
        GmSandboxState changed = engine.apply(
                GmSandboxCatalog.initialState(),
                engine.parse("jump-node", argsWith("nodeId", "camp")));
        GmSandboxEngine.Command reset = engine.parse(
                "reset-sandbox",
                objectMapper.createObjectNode());
        assertThat(engine.isReset(reset)).isTrue();
        assertThat(engine.apply(changed, reset)).isEqualTo(GmSandboxCatalog.initialState());

        assertThatThrownBy(() -> engine.parse(
                "jump-node",
                argsWith("nodeId", "camp", "extra", "value")))
                .isInstanceOf(ApiException.class);
    }

    private ObjectNode argsWith(String... values) {
        ObjectNode args = objectMapper.createObjectNode();
        for (int index = 0; index < values.length; index += 2) {
            args.put(values[index], values[index + 1]);
        }
        return args;
    }
}
