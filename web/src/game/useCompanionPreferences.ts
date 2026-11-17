import {
  computed,
  ref,
  type ComputedRef,
  type Ref,
} from 'vue';
import {
  companionById,
  type CompanionId,
  type CompanionProfile,
} from '../companions/catalog';

export type CompanionDensity = 'quiet' | 'standard' | 'rich';

type CompanionPreferenceStorage = Pick<Storage, 'getItem' | 'setItem'>;

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
): CompanionId {
  try {
    return storage?.getItem(COMPANION_KEY) === 'serin' ? 'serin' : 'lia';
  } catch {
    return 'lia';
  }
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
  storage: CompanionPreferenceStorage | null = browserPreferenceStorage(),
): {
  selectedCompanion: ComputedRef<CompanionProfile>;
  companionDensity: Ref<CompanionDensity>;
  selectCompanion(companionId: CompanionId): void;
  selectCompanionDensity(density: CompanionDensity): void;
} {
  const selectedCompanionId = ref<CompanionId>(
    loadCompanionId(storage),
  );
  const companionDensity = ref<CompanionDensity>(
    loadCompanionDensity(storage),
  );
  const selectedCompanion = computed<CompanionProfile>(
    () => companionById[selectedCompanionId.value],
  );

  function selectCompanion(companionId: CompanionId): void {
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
