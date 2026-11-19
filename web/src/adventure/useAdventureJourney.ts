import {
  computed,
  ref,
  type ComputedRef,
  type Ref,
} from 'vue';
import type {
  AccountClient,
  AdventureRunView,
  SessionEnvelope,
} from '../api/client';

type AdventureApi = Pick<
  AccountClient,
  | 'getSession'
  | 'startAdventure'
  | 'getAdventure'
  | 'startAdventureEncounter'
  | 'actAdventure'
>;

interface UseAdventureJourneyOptions {
  api: () => AdventureApi;
  describeError(cause: unknown): string;
  createActionId?: () => string;
}

interface PendingRequest {
  signature: string;
  actionId: string;
}

export interface AdventureJourney {
  run: Ref<AdventureRunView | undefined>;
  session: Ref<SessionEnvelope | undefined>;
  node: ComputedRef<AdventureRunView['currentNode'] | undefined>;
  loading: Ref<boolean>;
  busy: Ref<boolean>;
  error: Ref<string | null>;
  notice: Ref<string | null>;
  start(input: {
    installationId: string;
    companionKey: string;
    reduceMotion: boolean;
  }): Promise<boolean>;
  resume(runId: string): Promise<boolean>;
  refresh(): Promise<void>;
  setSession(session: SessionEnvelope): void;
  choose(choiceId: string): Promise<void>;
  advance(): Promise<void>;
  startEncounter(): Promise<void>;
  resolveEncounter(): Promise<void>;
  claimReward(choiceId: string): Promise<void>;
  selectCompanion(companionKey: string): Promise<void>;
  completeChapter(): Promise<void>;
  reset(): void;
  dispose(): void;
}

export function useAdventureJourney({
  api,
  describeError,
  createActionId = newActionId,
}: UseAdventureJourneyOptions): AdventureJourney {
  const run = ref<AdventureRunView>();
  const session = ref<SessionEnvelope>();
  const loading = ref(false);
  const busy = ref(false);
  const error = ref<string | null>(null);
  const notice = ref<string | null>(null);
  const node = computed(() => run.value?.currentNode);
  let generation = 0;
  let pendingStart: PendingRequest | undefined;
  let pendingEncounter: PendingRequest | undefined;
  let pendingAction: PendingRequest | undefined;

  function reusableAction(
    pending: PendingRequest | undefined,
    signature: string,
  ): PendingRequest {
    if (pending?.signature === signature) {
      return pending;
    }
    return { signature, actionId: createActionId() };
  }

  async function loadSession(
    requestGeneration: number,
    sessionId: string,
  ): Promise<void> {
    const next = await api().getSession(sessionId);
    if (requestGeneration === generation) {
      session.value = next;
    }
  }

  async function start(input: {
    installationId: string;
    companionKey: string;
    reduceMotion: boolean;
  }): Promise<boolean> {
    const requestGeneration = ++generation;
    busy.value = true;
    error.value = null;
    notice.value = null;
    const signature = [
      'start',
      input.installationId,
      input.companionKey,
      String(input.reduceMotion),
    ].join(':');
    pendingStart = reusableAction(pendingStart, signature);
    try {
      const result = await api().startAdventure({
        ...input,
        actionId: pendingStart.actionId,
      });
      if (requestGeneration !== generation) {
        return false;
      }
      run.value = result.run;
      session.value = undefined;
      await loadSession(requestGeneration, result.run.sessionId);
      pendingStart = undefined;
      notice.value = result.outcome === 'created'
        ? '冒险存档已创建，当前内容版本已经锁定。'
        : '已回到继续进行中的冒险。';
      return true;
    } catch (cause) {
      if (requestGeneration === generation) {
        error.value = describeError(cause);
      }
      return false;
    } finally {
      if (requestGeneration === generation) {
        busy.value = false;
      }
    }
  }

  async function resume(runId: string): Promise<boolean> {
    const requestGeneration = ++generation;
    busy.value = true;
    error.value = null;
    notice.value = null;
    try {
      const next = await api().getAdventure(runId);
      if (requestGeneration !== generation) {
        return false;
      }
      run.value = next;
      session.value = undefined;
      await loadSession(requestGeneration, next.sessionId);
      notice.value = '已从最近检查点恢复冒险。';
      return true;
    } catch (cause) {
      if (requestGeneration === generation) {
        error.value = describeError(cause);
      }
      return false;
    } finally {
      if (requestGeneration === generation) {
        busy.value = false;
      }
    }
  }

  async function refresh(): Promise<void> {
    const current = run.value;
    if (current === undefined) {
      return;
    }
    const requestGeneration = ++generation;
    loading.value = true;
    error.value = null;
    try {
      const next = await api().getAdventure(current.id);
      if (requestGeneration !== generation) {
        return;
      }
      run.value = next;
      await loadSession(requestGeneration, next.sessionId);
    } catch (cause) {
      if (requestGeneration === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (requestGeneration === generation) {
        loading.value = false;
      }
    }
  }

  function setSession(next: SessionEnvelope): void {
    session.value = next;
  }

  async function submitAction(
    signature: string,
    action:
      | 'advance'
      | 'choose'
      | 'resolve-encounter'
      | 'claim-reward'
      | 'select-companion'
      | 'complete',
    choiceId?: string,
    battleId?: string,
    companionKey?: string,
  ): Promise<void> {
    const current = run.value;
    if (current === undefined || busy.value) {
      return;
    }
    const requestGeneration = ++generation;
    busy.value = true;
    error.value = null;
    notice.value = null;
    pendingAction = reusableAction(
      pendingAction,
      [
        signature,
        current.id,
        String(current.runVersion),
        action,
        choiceId ?? '',
        battleId ?? '',
        companionKey ?? '',
      ].join(':'),
    );
    try {
      const result = await api().actAdventure(
        current.id,
        pendingAction.actionId,
        {
          expectedVersion: current.runVersion,
          action,
          choiceId,
          battleId,
          companionKey,
        },
      );
      if (requestGeneration !== generation) {
        return;
      }
      run.value = result.run;
      pendingAction = undefined;
      notice.value = actionNotice(
        action,
        result.run.currentNode.type,
        result.run.rewardOffer !== undefined,
      );
    } catch (cause) {
      if (requestGeneration === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (requestGeneration === generation) {
        busy.value = false;
      }
    }
  }

  async function choose(choiceId: string): Promise<void> {
    await submitAction(`choose:${choiceId}`, 'choose', choiceId);
  }

  async function advance(): Promise<void> {
    await submitAction('advance', 'advance');
  }

  async function startEncounter(): Promise<void> {
    const current = run.value;
    if (current === undefined || busy.value) {
      return;
    }
    const requestGeneration = ++generation;
    busy.value = true;
    error.value = null;
    notice.value = null;
    pendingEncounter = reusableAction(
      pendingEncounter,
      `encounter:${current.id}:${current.runVersion}:${current.currentNode.nodeId}`,
    );
    try {
      const result = await api().startAdventureEncounter(
        current.id,
        pendingEncounter.actionId,
      );
      if (requestGeneration !== generation) {
        return;
      }
      run.value = {
        ...current,
        battleId: result.battleId,
        runVersion: result.runVersion,
        updatedAtMs: current.updatedAtMs,
      };
      pendingEncounter = undefined;
      notice.value = '战斗与学习会话已经绑定，先完成本轮回忆。';
    } catch (cause) {
      if (requestGeneration === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (requestGeneration === generation) {
        busy.value = false;
      }
    }
  }

  async function resolveEncounter(): Promise<void> {
    const battleId = run.value?.battleId;
    if (battleId === undefined) {
      return;
    }
    await submitAction(
      `resolve-encounter:${battleId}`,
      'resolve-encounter',
      undefined,
      battleId,
    );
  }

  async function completeChapter(): Promise<void> {
    await submitAction('complete', 'complete');
  }

  async function claimReward(choiceId: string): Promise<void> {
    await submitAction(
      `claim-reward:${choiceId}`,
      'claim-reward',
      choiceId,
    );
  }

  async function selectCompanion(
    companionKey: string,
  ): Promise<void> {
    await submitAction(
      `select-companion:${companionKey}`,
      'select-companion',
      undefined,
      undefined,
      companionKey,
    );
  }

  function reset(): void {
    generation += 1;
    run.value = undefined;
    session.value = undefined;
    loading.value = false;
    busy.value = false;
    error.value = null;
    notice.value = null;
    pendingStart = undefined;
    pendingEncounter = undefined;
    pendingAction = undefined;
  }

  function dispose(): void {
    generation += 1;
  }

  return {
    run,
    session,
    node,
    loading,
    busy,
    error,
    notice,
    start,
    resume,
    refresh,
    setSession,
    choose,
    advance,
    startEncounter,
    resolveEncounter,
    claimReward,
    selectCompanion,
    completeChapter,
    reset,
    dispose,
  };
}

function actionNotice(
  action: string,
  nodeType: AdventureRunView['currentNode']['type'],
  rewardPending: boolean,
): string {
  if (action === 'resolve-encounter') {
    return rewardPending
      ? '战斗胜利，选择一项牌组奖励后继续。'
      : '战斗结算已经写入冒险存档。';
  }
  if (action === 'claim-reward') {
    return '奖励已加入本次冒险牌组。';
  }
  if (action === 'select-companion') {
    return '带队伙伴已经更新，并在当前营地保存。';
  }
  switch (nodeType) {
    case 'chapter-end':
      return '第一章的检查点已经写入存档。';
    case 'camp':
      return '营火旁的状态已经保存。';
    case 'encounter':
      return '遭遇节点已恢复，继续完成战斗。';
    default:
      return '剧情进度已经保存。';
  }
}

function newActionId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
