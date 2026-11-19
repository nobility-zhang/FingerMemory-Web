package com.fingermemory.server.battle;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class BattleEngine {
    private static final int DEFAULT_SEED = 0x5eed1234;

    private final ObjectMapper objectMapper;

    public BattleEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BattleTransition create(CreateBattleCommand command) {
        BattleCatalog.CompanionSkill companionSkill = command.companionSkill() == null
                ? BattleCatalog.COMPANION_SKILLS.get(command.companionKey())
                : new BattleCatalog.CompanionSkill(
                        command.companionSkill().name(),
                        command.companionSkill().description(),
                        command.companionSkill().type(),
                        command.companionSkill().amount());
        if (companionSkill == null) {
            throw new BattleRuleException("unsupported companion skill");
        }
        List<String> deckOrder = command.deck() == null
                ? BattleCatalog.TEACHING_DECK_ORDER
                : List.copyOf(command.deck());
        BattleDeckCatalog.validateAdventureDeck(deckOrder);
        List<BattleState.CardInstance> deck = new ArrayList<>();
        for (int index = 0; index < deckOrder.size(); index += 1) {
            deck.add(new BattleState.CardInstance(
                    "card-%02d".formatted(index + 1),
                    deckOrder.get(index)));
        }
        long seed = normalizeSeed(command.seed() == null ? DEFAULT_SEED : command.seed());
        ShuffleResult shuffled = command.shuffle()
                ? shuffle(deck, seed)
                : new ShuffleResult(List.copyOf(deck), seed);
        BattleState.LearningTask learningTask = new BattleState.LearningTask(
                command.learningTaskId(),
                command.sessionId(),
                command.studyItemId(),
                false,
                null);
        BattleState initial = new BattleState(
                BattleCatalog.SCHEMA_VERSION,
                BattleCatalog.RULES_VERSION,
                command.battleId(),
                command.encounterId(),
                1,
                "learning",
                0,
                shuffled.rngState(),
                new BattleState.Party(30, 30, 0),
                List.of(new BattleState.Enemy(
                        command.encounterId(), 36, 36, 0, null)),
                0,
                shuffled.cards(),
                0,
                List.of(),
                List.of(),
                List.of(),
                new BattleState.Intent(command.encounterId(), "attack", 6),
                List.of(
                        new BattleState.IntentTemplate("attack", 6),
                        new BattleState.IntentTemplate("defend", 6),
                        new BattleState.IntentTemplate("attack", 8)),
                0,
                null,
                learningTask,
                new BattleState.Companion(
                        command.companionKey(),
                        false,
                        companionSkill.name(),
                        companionSkill.description(),
                        companionSkill.type(),
                        companionSkill.amount()));
        List<ObjectNode> events = new ArrayList<>();
        ObjectNode started = event("battle-started");
        started.put("turnId", 1);
        started.put("learningTaskId", command.learningTaskId());
        events.add(started);
        DrawResult draw = drawCards(initial, BattleCatalog.TURN_DRAW);
        events.addAll(draw.events());
        return new BattleTransition(draw.state(), List.copyOf(events));
    }

    public BattleTransition claimLearning(
            BattleState state,
            long expectedVersion,
            String submissionId) {
        requireVersion(state, expectedVersion);
        if (!"learning".equals(state.phase())) {
            throw new BattleRuleException("learning claim requires learning phase");
        }
        BattleState.LearningTask task = state.learningTask();
        if (task == null || task.energyGranted()) {
            throw new BattleRuleException("learning task has already granted energy");
        }
        BattleState.LearningTask grantedTask = new BattleState.LearningTask(
                task.taskId(),
                task.sessionId(),
                task.studyItemId(),
                true,
                submissionId);
        BattleState next = copy(
                state,
                state.stateVersion() + 1,
                state.party(),
                state.enemies(),
                BattleCatalog.BASE_ENERGY,
                state.drawPile(),
                state.drawCursor(),
                state.hand(),
                state.discardPile(),
                state.exhaustPile(),
                "player-action",
                grantedTask,
                state.companion());
        ObjectNode awarded = event("learning-energy-awarded");
        awarded.put("taskId", task.taskId());
        awarded.put("submissionId", submissionId);
        awarded.put("amount", BattleCatalog.BASE_ENERGY);
        ObjectNode phase = event("turn-phase-changed");
        phase.put("from", "learning");
        phase.put("to", "player-action");
        return new BattleTransition(next, List.of(awarded, phase));
    }

    public BattleTransition playCard(
            BattleState state,
            long expectedVersion,
            String cardInstanceId,
            String targetId) {
        requireVersion(state, expectedVersion);
        requirePlayerAction(state);
        int handIndex = indexOfCard(state.hand(), cardInstanceId);
        if (handIndex < 0) {
            throw new BattleRuleException("card is not in hand");
        }
        BattleState.CardInstance card = state.hand().get(handIndex);
        BattleCatalog.CardDefinition definition = BattleCatalog.CARDS.get(card.definitionId());
        if (definition == null) {
            throw new BattleRuleException("unknown card definition");
        }
        if (state.energy() < definition.cost()) {
            throw new BattleRuleException("not enough energy");
        }
        if ("enemy".equals(definition.target()) && findLivingEnemy(state, targetId) == null) {
            throw new BattleRuleException("valid enemy target required");
        }

        List<BattleState.CardInstance> hand = new ArrayList<>(state.hand());
        hand.remove(handIndex);
        List<BattleState.CardInstance> discard = new ArrayList<>(state.discardPile());
        List<BattleState.CardInstance> exhaust = new ArrayList<>(state.exhaustPile());
        if (definition.exhaust()) {
            exhaust.add(card);
        } else {
            discard.add(card);
        }
        List<BattleState.Enemy> enemies = copyEnemies(state.enemies());
        BattleState.Party party = state.party();
        DrawResult draw = new DrawResult(
                state.drawPile(), state.drawCursor(), state.discardPile(), state.rngState(),
                hand, List.of());
        List<ObjectNode> events = new ArrayList<>();
        ObjectNode played = event("card-played");
        played.put("cardInstanceId", card.instanceId());
        played.put("definitionId", card.definitionId());
        played.put("energySpent", definition.cost());
        events.add(played);

        for (BattleCatalog.CardEffect effect : definition.effects()) {
            switch (effect.type()) {
                case "damage" -> {
                    BattleState.Enemy enemy = findLivingEnemy(enemies, targetId);
                    if (enemy != null) {
                        DamageResult damage = damageEnemy(enemy, effect.amount());
                        replaceEnemy(enemies, damage.enemy());
                        events.add(damage.event("card"));
                    }
                }
                case "block" -> {
                    party = new BattleState.Party(
                            party.hp(), party.maxHp(), party.block() + effect.amount());
                    ObjectNode block = event("block-gained");
                    block.put("targetId", "party");
                    block.put("amount", effect.amount());
                    block.put("totalBlock", party.block());
                    events.add(block);
                }
                case "heal" -> {
                    int healed = Math.min(effect.amount(), party.maxHp() - party.hp());
                    party = new BattleState.Party(
                            party.hp() + healed, party.maxHp(), party.block());
                    ObjectNode heal = event("healed");
                    heal.put("targetId", "party");
                    heal.put("amount", healed);
                    heal.put("totalHp", party.hp());
                    events.add(heal);
                }
                case "draw" -> {
                    BattleState provisional = copy(
                            state,
                            state.stateVersion(),
                            party,
                            enemies,
                            state.energy() - definition.cost(),
                            draw.drawPile(),
                            draw.drawCursor(),
                            draw.hand(),
                            discard,
                            exhaust,
                            state.phase(),
                            state.learningTask(),
                            state.companion());
                    DrawResult nextDraw = drawCards(provisional, effect.amount());
                    draw = new DrawResult(
                            nextDraw.drawPile(),
                            nextDraw.drawCursor(),
                            nextDraw.discardPile(),
                            nextDraw.rngState(),
                            nextDraw.hand(),
                            nextDraw.events());
                    events.addAll(nextDraw.events());
                }
                case "apply-burn" -> {
                    BattleState.Enemy enemy = findLivingEnemy(enemies, targetId);
                    if (enemy != null) {
                        enemy = new BattleState.Enemy(
                                enemy.id(),
                                enemy.hp(),
                                enemy.maxHp(),
                                enemy.block(),
                                new BattleState.Burn(
                                        effect.remainingTurns(),
                                        effect.damagePerTurn()));
                        replaceEnemy(enemies, enemy);
                        ObjectNode burn = event("burn-applied");
                        burn.put("targetId", enemy.id());
                        burn.put("remainingTurns", effect.remainingTurns());
                        burn.put("damagePerTurn", effect.damagePerTurn());
                        events.add(burn);
                    }
                }
                default -> throw new BattleRuleException("unsupported card effect");
            }
        }

        BattleState next = copy(
                state,
                state.stateVersion() + 1,
                party,
                enemies,
                state.energy() - definition.cost(),
                draw.drawPile(),
                draw.drawCursor(),
                draw.hand(),
                draw.discardPile(),
                exhaust,
                state.phase(),
                state.learningTask(),
                state.companion());
        next = applyTerminal(next, events);
        return new BattleTransition(next, List.copyOf(events));
    }

    public BattleTransition useCompanionSkill(
            BattleState state,
            long expectedVersion) {
        requireVersion(state, expectedVersion);
        requirePlayerAction(state);
        if (state.companion() == null || state.companion().skillUsed()) {
            throw new BattleRuleException("companion skill has already been used");
        }
        BattleState.Party party = state.party();
        List<ObjectNode> events = new ArrayList<>();
        DrawResult draw = new DrawResult(
                state.drawPile(), state.drawCursor(), state.discardPile(), state.rngState(),
                state.hand(), List.of());
        BattleCatalog.CompanionSkill skill = resolveCompanionSkill(state);
        switch (skill.type()) {
            case "heal" -> {
                int healed = Math.min(
                        skill.amount(),
                        party.maxHp() - party.hp());
                party = new BattleState.Party(party.hp() + healed, party.maxHp(), party.block());
                ObjectNode event = event("healed");
                event.put("targetId", "party");
                event.put("amount", healed);
                event.put("totalHp", party.hp());
                events.add(event);
            }
            case "block" -> {
                party = new BattleState.Party(
                        party.hp(),
                        party.maxHp(),
                        party.block() + skill.amount());
                ObjectNode event = event("block-gained");
                event.put("targetId", "party");
                event.put("amount", skill.amount());
                event.put("totalBlock", party.block());
                events.add(event);
            }
            case "draw" -> {
                draw = drawCards(state, skill.amount());
                events.addAll(draw.events());
            }
            case "damage" -> {
                BattleState.Enemy target = state.enemies().stream()
                        .filter(enemy -> enemy.hp() > 0)
                        .findFirst()
                        .orElseThrow(() ->
                                new BattleRuleException("no living enemy target"));
                DamageResult damaged = damageEnemy(target, skill.amount());
                List<BattleState.Enemy> enemies = new ArrayList<>(state.enemies());
                replaceEnemy(enemies, damaged.enemy());
                events.add(damaged.event("companion"));
                BattleState next = copy(
                        state,
                        state.stateVersion() + 1,
                        party,
                        enemies,
                        state.energy(),
                        draw.drawPile(),
                        draw.drawCursor(),
                        draw.hand(),
                        state.discardPile(),
                        state.exhaustPile(),
                        state.phase(),
                        state.learningTask(),
                        new BattleState.Companion(
                                state.companion().key(),
                                true,
                                skill.name(),
                                skill.description(),
                                skill.type(),
                                skill.amount()));
                next = applyTerminal(next, events);
                ObjectNode used = event("companion-skill-used");
                used.put("companionKey", state.companion().key());
                used.put("skillName", skill.name());
                used.put("skillType", skill.type());
                used.put("amount", skill.amount());
                events.add(0, used);
                return new BattleTransition(next, List.copyOf(events));
            }
            default -> throw new BattleRuleException("unsupported companion skill");
        }
        ObjectNode used = event("companion-skill-used");
        used.put("companionKey", state.companion().key());
        used.put("skillName", skill.name());
        used.put("skillType", skill.type());
        used.put("amount", skill.amount());
        events.add(0, used);
        BattleState.Companion companion = new BattleState.Companion(
                state.companion().key(),
                true,
                skill.name(),
                skill.description(),
                skill.type(),
                skill.amount());
        BattleState next = copy(
                state,
                state.stateVersion() + 1,
                party,
                state.enemies(),
                state.energy(),
                draw.drawPile(),
                draw.drawCursor(),
                draw.hand(),
                draw.discardPile(),
                state.exhaustPile(),
                state.phase(),
                state.learningTask(),
                companion);
        return new BattleTransition(next, List.copyOf(events));
    }

    public BattleTransition endTurn(
            BattleState state,
            long expectedVersion,
            NextLearningTask nextLearningTask) {
        requireVersion(state, expectedVersion);
        requirePlayerAction(state);
        List<ObjectNode> events = new ArrayList<>();
        List<BattleState.CardInstance> discard = new ArrayList<>(state.discardPile());
        discard.addAll(state.hand());
        ObjectNode ended = event("turn-ended");
        ended.put("turnId", state.turnId());
        ended.put("discardedCount", state.hand().size());
        events.add(ended);

        List<BattleState.Enemy> enemies = copyEnemies(state.enemies());
        for (BattleState.Enemy enemy : enemies) {
            if (enemy.hp() <= 0 || enemy.burn() == null) {
                continue;
            }
            DamageResult damage = damageEnemy(enemy, enemy.burn().damagePerTurn());
            events.add(damage.event("burn"));
            int remaining = enemy.burn().remainingTurns() - 1;
            replaceEnemy(enemies, new BattleState.Enemy(
                    damage.enemy().id(),
                    damage.enemy().hp(),
                    damage.enemy().maxHp(),
                    damage.enemy().block(),
                    remaining <= 0
                            ? null
                            : new BattleState.Burn(
                                    remaining, enemy.burn().damagePerTurn())));
            ObjectNode tick = event("burn-ticked");
            tick.put("targetId", enemy.id());
            tick.put("remainingTurns", Math.max(0, remaining));
            events.add(tick);
        }
        BattleState afterBurn = copy(
                state,
                state.stateVersion(),
                state.party(),
                enemies,
                0,
                state.drawPile(),
                state.drawCursor(),
                List.of(),
                discard,
                state.exhaustPile(),
                "enemy-action",
                state.learningTask(),
                state.companion());
        afterBurn = applyTerminal(afterBurn, events);
        if (afterBurn.terminal() != null) {
            BattleState terminal = copy(
                    afterBurn,
                    state.stateVersion() + 1,
                    afterBurn.party(),
                    afterBurn.enemies(),
                    afterBurn.energy(),
                    afterBurn.drawPile(),
                    afterBurn.drawCursor(),
                    afterBurn.hand(),
                    afterBurn.discardPile(),
                    afterBurn.exhaustPile(),
                    afterBurn.phase(),
                    afterBurn.learningTask(),
                    afterBurn.companion());
            return new BattleTransition(terminal, List.copyOf(events));
        }

        BattleState.Party party = afterBurn.party();
        ObjectNode intentEvent = event("intent-executed");
        intentEvent.put("enemyId", state.currentIntent().enemyId());
        intentEvent.put("intentType", state.currentIntent().type());
        intentEvent.put("value", state.currentIntent().value());
        events.add(intentEvent);
        if ("attack".equals(state.currentIntent().type())) {
            int blocked = Math.min(party.block(), state.currentIntent().value());
            int hpLost = Math.min(
                    party.hp(), state.currentIntent().value() - blocked);
            party = new BattleState.Party(
                    party.hp() - hpLost, party.maxHp(), party.block() - blocked);
            ObjectNode damage = event("damage");
            damage.put("source", "enemy");
            damage.put("targetId", "party");
            damage.put("amount", state.currentIntent().value());
            damage.put("blocked", blocked);
            damage.put("hpLoss", hpLost);
            events.add(damage);
        } else {
            BattleState.Enemy acting = findLivingEnemy(
                    enemies, state.currentIntent().enemyId());
            if (acting != null) {
                acting = new BattleState.Enemy(
                        acting.id(),
                        acting.hp(),
                        acting.maxHp(),
                        acting.block() + state.currentIntent().value(),
                        acting.burn());
                replaceEnemy(enemies, acting);
                ObjectNode block = event("block-gained");
                block.put("targetId", acting.id());
                block.put("amount", state.currentIntent().value());
                block.put("totalBlock", acting.block());
                events.add(block);
            }
        }

        BattleState afterIntent = copy(
                afterBurn,
                state.stateVersion(),
                party,
                enemies,
                0,
                afterBurn.drawPile(),
                afterBurn.drawCursor(),
                afterBurn.hand(),
                afterBurn.discardPile(),
                afterBurn.exhaustPile(),
                "enemy-action",
                afterBurn.learningTask(),
                afterBurn.companion());
        afterIntent = applyTerminal(afterIntent, events);
        if (afterIntent.terminal() != null) {
            BattleState terminal = copy(
                    afterIntent,
                    state.stateVersion() + 1,
                    afterIntent.party(),
                    afterIntent.enemies(),
                    afterIntent.energy(),
                    afterIntent.drawPile(),
                    afterIntent.drawCursor(),
                    afterIntent.hand(),
                    afterIntent.discardPile(),
                    afterIntent.exhaustPile(),
                    afterIntent.phase(),
                    afterIntent.learningTask(),
                    afterIntent.companion());
            return new BattleTransition(terminal, List.copyOf(events));
        }
        if (nextLearningTask == null) {
            throw new BattleRuleException("next learning task is required");
        }

        int nextIntentIndex = (afterIntent.intentIndex() + 1) % afterIntent.intentCycle().size();
        BattleState.IntentTemplate nextIntent =
                afterIntent.intentCycle().get(nextIntentIndex);
        BattleState.LearningTask learningTask = new BattleState.LearningTask(
                nextLearningTask.taskId(),
                nextLearningTask.sessionId(),
                nextLearningTask.studyItemId(),
                false,
                null);
        BattleState beforeDraw = copy(
                afterIntent,
                state.stateVersion(),
                new BattleState.Party(party.hp(), party.maxHp(), 0),
                enemies,
                0,
                afterIntent.drawPile(),
                afterIntent.drawCursor(),
                afterIntent.hand(),
                afterIntent.discardPile(),
                afterIntent.exhaustPile(),
                "learning",
                learningTask,
                afterIntent.companion());
        DrawResult draw = drawCards(beforeDraw, BattleCatalog.TURN_DRAW);
        ObjectNode turnStarted = event("turn-started");
        turnStarted.put("turnId", state.turnId() + 1);
        turnStarted.put("intentType", nextIntent.type());
        turnStarted.put("intentValue", nextIntent.value());
        turnStarted.put("learningTaskId", nextLearningTask.taskId());
        events.add(turnStarted);
        events.addAll(draw.events());
        BattleState next = new BattleState(
                draw.state().schemaVersion(),
                draw.state().battleRulesVersion(),
                draw.state().battleId(),
                draw.state().encounterId(),
                state.turnId() + 1,
                "learning",
                state.stateVersion() + 1,
                draw.state().rngState(),
                draw.state().party(),
                draw.state().enemies(),
                draw.state().energy(),
                draw.state().drawPile(),
                draw.state().drawCursor(),
                draw.state().hand(),
                draw.state().discardPile(),
                draw.state().exhaustPile(),
                new BattleState.Intent(
                        afterIntent.enemies().get(0).id(),
                        nextIntent.type(),
                        nextIntent.value()),
                draw.state().intentCycle(),
                nextIntentIndex,
                null,
                draw.state().learningTask(),
                draw.state().companion());
        return new BattleTransition(next, List.copyOf(events));
    }

    private DrawResult drawCards(BattleState state, int amount) {
        List<BattleState.CardInstance> hand = new ArrayList<>(state.hand());
        List<BattleState.CardInstance> drawPile = new ArrayList<>(state.drawPile());
        List<BattleState.CardInstance> discard = new ArrayList<>(state.discardPile());
        int drawCursor = state.drawCursor();
        long rngState = state.rngState();
        List<BattleState.CardInstance> drawn = new ArrayList<>();
        boolean recycled = false;
        while (drawn.size() < amount && hand.size() < BattleCatalog.HAND_LIMIT) {
            if (drawCursor >= drawPile.size()) {
                if (discard.isEmpty()) {
                    break;
                }
                ShuffleResult shuffle = shuffle(discard, rngState);
                drawPile = new ArrayList<>(shuffle.cards());
                discard = new ArrayList<>();
                drawCursor = 0;
                rngState = shuffle.rngState();
                recycled = true;
            }
            BattleState.CardInstance card = drawPile.get(drawCursor);
            drawCursor += 1;
            hand.add(card);
            drawn.add(card);
        }
        List<ObjectNode> events = new ArrayList<>();
        if (!drawn.isEmpty()) {
            ObjectNode event = event("cards-drawn");
            event.put("count", drawn.size());
            event.put("recycled", recycled);
            events.add(event);
        }
        BattleState next = copy(
                state,
                state.stateVersion(),
                state.party(),
                state.enemies(),
                state.energy(),
                List.copyOf(drawPile),
                drawCursor,
                List.copyOf(hand),
                List.copyOf(discard),
                state.exhaustPile(),
                state.phase(),
                state.learningTask(),
                state.companion());
        return new DrawResult(
                next.drawPile(),
                next.drawCursor(),
                next.discardPile(),
                rngState,
                next.hand(),
                List.copyOf(events),
                next);
    }

    private BattleState copy(
            BattleState source,
            long stateVersion,
            BattleState.Party party,
            List<BattleState.Enemy> enemies,
            int energy,
            List<BattleState.CardInstance> drawPile,
            int drawCursor,
            List<BattleState.CardInstance> hand,
            List<BattleState.CardInstance> discard,
            List<BattleState.CardInstance> exhaust,
            String phase,
            BattleState.LearningTask learningTask,
            BattleState.Companion companion) {
        return new BattleState(
                source.schemaVersion(),
                source.battleRulesVersion(),
                source.battleId(),
                source.encounterId(),
                source.turnId(),
                phase,
                stateVersion,
                source.rngState(),
                party,
                enemies,
                energy,
                drawPile,
                drawCursor,
                hand,
                discard,
                exhaust,
                source.currentIntent(),
                source.intentCycle(),
                source.intentIndex(),
                source.terminal(),
                learningTask,
                companion);
    }

    private BattleState applyTerminal(BattleState state, List<ObjectNode> events) {
        String terminal = null;
        if (state.party().hp() <= 0) {
            terminal = "defeat";
        } else if (state.enemies().stream().allMatch(enemy -> enemy.hp() <= 0)) {
            terminal = "victory";
        }
        if (terminal == null) {
            return state;
        }
        BattleState next = new BattleState(
                state.schemaVersion(),
                state.battleRulesVersion(),
                state.battleId(),
                state.encounterId(),
                state.turnId(),
                terminal,
                state.stateVersion(),
                state.rngState(),
                state.party(),
                state.enemies(),
                state.energy(),
                state.drawPile(),
                state.drawCursor(),
                state.hand(),
                state.discardPile(),
                state.exhaustPile(),
                state.currentIntent(),
                state.intentCycle(),
                state.intentIndex(),
                terminal,
                state.learningTask(),
                state.companion());
        ObjectNode event = event("battle-ended");
        event.put("result", terminal);
        events.add(event);
        return next;
    }

    private void requireVersion(BattleState state, long expectedVersion) {
        if (state.stateVersion() != expectedVersion) {
            throw new BattleRuleException("battle state version conflict");
        }
    }

    private void requirePlayerAction(BattleState state) {
        if (!"player-action".equals(state.phase())) {
            throw new BattleRuleException("command requires player-action phase");
        }
    }

    private int indexOfCard(List<BattleState.CardInstance> hand, String cardInstanceId) {
        for (int index = 0; index < hand.size(); index += 1) {
            if (hand.get(index).instanceId().equals(cardInstanceId)) {
                return index;
            }
        }
        return -1;
    }

    private BattleState.Enemy findLivingEnemy(BattleState state, String targetId) {
        return findLivingEnemy(state.enemies(), targetId);
    }

    private BattleState.Enemy findLivingEnemy(
            List<BattleState.Enemy> enemies,
            String targetId) {
        if (targetId == null) {
            return null;
        }
        for (BattleState.Enemy enemy : enemies) {
            if (enemy.id().equals(targetId) && enemy.hp() > 0) {
                return enemy;
            }
        }
        return null;
    }

    private void replaceEnemy(
            List<BattleState.Enemy> enemies,
            BattleState.Enemy replacement) {
        for (int index = 0; index < enemies.size(); index += 1) {
            if (enemies.get(index).id().equals(replacement.id())) {
                enemies.set(index, replacement);
                return;
            }
        }
        throw new BattleRuleException("enemy does not exist");
    }

    private List<BattleState.Enemy> copyEnemies(List<BattleState.Enemy> enemies) {
        return new ArrayList<>(enemies);
    }

    private BattleCatalog.CompanionSkill resolveCompanionSkill(BattleState state) {
        if (state.companion().skillType() != null
                && state.companion().skillAmount() > 0) {
            return new BattleCatalog.CompanionSkill(
                    state.companion().skillName(),
                    state.companion().skillDescription(),
                    state.companion().skillType(),
                    state.companion().skillAmount());
        }
        BattleCatalog.CompanionSkill fallback =
                BattleCatalog.COMPANION_SKILLS.get(state.companion().key());
        if (fallback == null) {
            throw new BattleRuleException("unsupported companion skill");
        }
        return fallback;
    }

    private DamageResult damageEnemy(BattleState.Enemy enemy, int amount) {
        int blocked = Math.min(enemy.block(), amount);
        int hpLoss = Math.min(enemy.hp(), amount - blocked);
        BattleState.Enemy updated = new BattleState.Enemy(
                enemy.id(),
                enemy.hp() - hpLoss,
                enemy.maxHp(),
                enemy.block() - blocked,
                enemy.burn());
        return new DamageResult(updated, amount, blocked, hpLoss);
    }

    private ObjectNode event(String type) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("type", type);
        return event;
    }

    private long normalizeSeed(long seed) {
        long normalized = Integer.toUnsignedLong((int) seed);
        return normalized == 0 ? 0x9e3779b9L : normalized;
    }

    private ShuffleResult shuffle(List<BattleState.CardInstance> values, long seed) {
        List<BattleState.CardInstance> shuffled = new ArrayList<>(values);
        long state = normalizeSeed(seed);
        for (int index = shuffled.size() - 1; index > 0; index -= 1) {
            state ^= (state << 13) & 0xffffffffL;
            state ^= state >>> 17;
            state ^= (state << 5) & 0xffffffffL;
            state &= 0xffffffffL;
            double value = state / 4_294_967_296.0;
            int swapIndex = (int) Math.floor(value * (index + 1));
            BattleState.CardInstance current = shuffled.get(index);
            shuffled.set(index, shuffled.get(swapIndex));
            shuffled.set(swapIndex, current);
        }
        return new ShuffleResult(List.copyOf(shuffled), state);
    }

    public record CreateBattleCommand(
            String battleId,
            String encounterId,
            String learningTaskId,
            String sessionId,
            String studyItemId,
            String companionKey,
            Long seed,
            boolean shuffle,
            List<String> deck,
            CompanionSkillDefinition companionSkill) {

        public CreateBattleCommand(
                String battleId,
                String encounterId,
                String learningTaskId,
                String sessionId,
                String studyItemId,
                String companionKey,
                Long seed,
                boolean shuffle) {
            this(
                    battleId,
                    encounterId,
                    learningTaskId,
                    sessionId,
                    studyItemId,
                    companionKey,
                    seed,
                    shuffle,
                    null,
                    null);
        }
    }

    public record CompanionSkillDefinition(
            String name,
            String description,
            String type,
            int amount) {
    }

    public record NextLearningTask(
            String taskId,
            String sessionId,
            String studyItemId) {
    }

    private record ShuffleResult(List<BattleState.CardInstance> cards, long rngState) {
    }

    private record DrawResult(
            List<BattleState.CardInstance> drawPile,
            int drawCursor,
            List<BattleState.CardInstance> discardPile,
            long rngState,
            List<BattleState.CardInstance> hand,
            List<ObjectNode> events,
            BattleState state) {
        private DrawResult(
                List<BattleState.CardInstance> drawPile,
                int drawCursor,
                List<BattleState.CardInstance> discardPile,
                long rngState,
                List<BattleState.CardInstance> hand,
                List<ObjectNode> events) {
            this(
                    drawPile,
                    drawCursor,
                    discardPile,
                    rngState,
                    hand,
                    events,
                    null);
        }
    }

    private record DamageResult(
            BattleState.Enemy enemy,
            int amount,
            int blocked,
            int hpLoss) {
        private ObjectNode event(String source) {
            ObjectNode event = new ObjectMapper().createObjectNode();
            event.put("type", "damage");
            event.put("source", source);
            event.put("targetId", enemy.id());
            event.put("amount", amount);
            event.put("blocked", blocked);
            event.put("hpLost", hpLoss);
            return event;
        }
    }

    public static class BattleRuleException extends RuntimeException {
        public BattleRuleException(String message) {
            super(message);
        }
    }
}
