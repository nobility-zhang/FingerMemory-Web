import {
  computed,
  ref,
  watch,
  type ComputedRef,
  type Ref,
} from 'vue';
import {
  companions as builtInCompanions,
  type CompanionProfile,
} from '../companions/catalog';

export type CompanionDensity = 'quiet' | 'standard' | 'rich';

type CompanionPreferenceStorage = Pick<Storage, 'getItem' | 'setItem'>;

interface UseCompanionPreferencesOptions {
  profiles?: () => readonly CompanionProfile[];
  storage?: CompanionPreferenceStorage | null;
}

const COMPANION_KEY = 'fm.companion';
const DENSITY_KEY = 'fm.companionDensity';

function browserPreferenceStorage(): CompanionPreferenceStorage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function loadCompanionId(
  storage: CompanionPreferenceStorage | null,
  profiles: readonly CompanionProfile[],
): string {
  try {
    const stored = storage?.getItem(COMPANION_KEY);
    if (
      stored !== undefined
      && stored !== null
      && profiles.some((profile) => profile.id === stored)
    ) {
      return stored;
    }
  } catch {
    // Fall back to the first available companion.
  }
  return profiles[0]?.id ?? 'lia';
}

function loadCompanionDensity(
  storage: CompanionPreferenceStorage | null,
): CompanionDensity {
  try {
    const stored = storage?.getItem(DENSITY_KEY);
    return stored === 'quiet' || stored === 'rich' ? stored : 'standard';
  } catch {
    return 'standard';
  }
}

function savePreference(
  storage: CompanionPreferenceStorage | null,
  key: string,
  value: string,
): void {
  try {
    storage?.setItem(key, value);
  } catch {
    // The current game session can still use an in-memory preference.
  }
}

export function useCompanionPreferences(
  options: UseCompanionPreferencesOptions = {},
): {
  selectedCompanion: ComputedRef<CompanionProfile>;
  companionDensity: Ref<CompanionDensity>;
  selectCompanion(companionId: string): void;
  selectCompanionDensity(density: CompanionDensity): void;
} {
  const getProfiles = options.profiles ?? (() => builtInCompanions);
  const storage = options.storage === undefined
    ? browserPreferenceStorage()
    : options.storage;
  const selectedCompanionId = ref(
    loadCompanionId(storage, getProfiles()),
  );
  const companionDensity = ref<CompanionDensity>(
    loadCompanionDensity(storage),
  );
  const selectedCompanion = computed<CompanionProfile>(() => {
    const profiles = getProfiles();
    return profiles.find((profile) => profile.id === selectedCompanionId.value)
      ?? profiles[0]
      ?? builtInCompanions[0]!;
  });

  watch(
    () => getProfiles().map((profile) => profile.id).join('|'),
    () => {
      const profiles = getProfiles();
      if (
        profiles.length > 0
        && !profiles.some((profile) => profile.id === selectedCompanionId.value)
      ) {
        selectCompanion(profiles[0]!.id);
      }
    },
  );

  function selectCompanion(companionId: string): void {
    const profiles = getProfiles();
    if (
      profiles.length > 0
      && !profiles.some((profile) => profile.id === companionId)
    ) {
      return;
    }
    selectedCompanionId.value = companionId;
    savePreference(storage, COMPANION_KEY, companionId);
  }

  function selectCompanionDensity(density: CompanionDensity): void {
    companionDensity.value = density;
    savePreference(storage, DENSITY_KEY, density);
  }

  return {
    selectedCompanion,
    companionDensity,
    selectCompanion,
    selectCompanionDensity,
  };
}
