import {
  computed,
  ref,
  type ComputedRef,
  type Ref,
} from 'vue';
import type {
  AccountClient,
  AdventureRunView,
  ContentPackageInstallation,
  LexiconView,
  QueuePlanView,
  SessionEnvelope,
} from '../api/client';

type GameLobbyApi = Pick<
  AccountClient,
  | 'loadQueuePlan'
  | 'listLexicons'
  | 'listActiveSessions'
  | 'listContentPackages'
  | 'listAdventures'
>;

interface UseGameLobbyOptions {
  api: () => GameLobbyApi;
  describeError(cause: unknown): string;
  timeZone(): string;
}

export function useGameLobby({
  api,
  describeError,
  timeZone,
}: UseGameLobbyOptions): {
  plan: Ref<QueuePlanView | undefined>;
  lexicons: Ref<LexiconView[]>;
  sessions: Ref<SessionEnvelope[]>;
  contentPackages: Ref<ContentPackageInstallation[]>;
  adventures: Ref<AdventureRunView[]>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  activeSession: ComputedRef<SessionEnvelope | undefined>;
  load(): Promise<void>;
  fetchActiveSessions(): Promise<SessionEnvelope[]>;
  dispose(): void;
} {
  const plan = ref<QueuePlanView>();
  const lexicons = ref<LexiconView[]>([]);
  const sessions = ref<SessionEnvelope[]>([]);
  const contentPackages = ref<ContentPackageInstallation[]>([]);
  const adventures = ref<AdventureRunView[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);
  let generation = 0;

  const activeSession = computed(() => sessions.value[0]);

  async function load(): Promise<void> {
    const request = ++generation;
    error.value = null;
    loading.value = true;
    try {
      const [
        nextPlan,
        nextLexicons,
        nextSessions,
        nextContentPackages,
        nextAdventures,
      ] = await Promise.all([
        api().loadQueuePlan({ timeZone: timeZone() }),
        api().listLexicons(),
        api().listActiveSessions(),
        api().listContentPackages(),
        api().listAdventures(),
      ]);
      if (request !== generation) {
        return;
      }
      plan.value = nextPlan;
      lexicons.value = nextLexicons;
      sessions.value = nextSessions.sessions;
      contentPackages.value = nextContentPackages;
      adventures.value = nextAdventures;
    } catch (cause) {
      if (request === generation) {
        error.value = describeError(cause);
      }
    } finally {
      if (request === generation) {
        loading.value = false;
      }
    }
  }

  async function fetchActiveSessions(): Promise<SessionEnvelope[]> {
    const response = await api().listActiveSessions();
    sessions.value = response.sessions;
    return response.sessions;
  }

  function dispose(): void {
    generation += 1;
  }

  return {
    plan,
    lexicons,
    sessions,
    contentPackages,
    adventures,
    loading,
    error,
    activeSession,
    load,
    fetchActiveSessions,
    dispose,
  };
}
