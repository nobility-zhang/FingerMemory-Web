import type {
  AdventureNodeView,
  AdventureSceneView,
} from '../api/contracts';
import campBackground from '../assets/scenes/camp/runtime/camp-fire.jpg';
import bridgeBackground from '../assets/scenes/bridge/runtime/bridge-guardian.jpg';
import coastBeachBackground from '../assets/scenes/coast/runtime/coast-beach.jpg';
import coastLighthouseBackground from '../assets/scenes/coast/runtime/coast-lighthouse.jpg';
import coastTidepoolsBackground from '../assets/scenes/coast/runtime/coast-tidepools.jpg';
import forestBackground from '../assets/scenes/forest/runtime/forest-path.jpg';
import innBackground from '../assets/scenes/inn/runtime/inn-warm-lantern.jpg';
import type { GameSoundKey } from '../game-runtime/soundCatalog';
import { contentAssetUrl } from '../companions/registry';

export type AdventureSceneId = string;

export type AdventureBattleTheme = string;

export interface AdventureScene {
  id: AdventureSceneId;
  label: string;
  background: string;
  battleTheme: AdventureBattleTheme;
  ambientKey?: GameSoundKey;
}

const SCENES: Record<AdventureSceneId, AdventureScene> = {
  'inn-warm-lantern': {
    id: 'inn-warm-lantern',
    label: '暖灯旅店',
    background: innBackground,
    battleTheme: 'bridge',
  },
  'forest-path': {
    id: 'forest-path',
    label: '失语之森',
    background: forestBackground,
    battleTheme: 'bridge',
  },
  'bridge-guardian': {
    id: 'bridge-guardian',
    label: '雾中溪桥',
    background: bridgeBackground,
    battleTheme: 'bridge',
  },
  'camp-fire': {
    id: 'camp-fire',
    label: '营火',
    background: campBackground,
    battleTheme: 'bridge',
  },
  'coast-beach': {
    id: 'coast-beach',
    label: '潮声海湾',
    background: coastBeachBackground,
    battleTheme: 'coast',
    ambientKey: 'ambient-coast',
  },
  'coast-tidepools': {
    id: 'coast-tidepools',
    label: '潮汐浅滩',
    background: coastTidepoolsBackground,
    battleTheme: 'coast',
    ambientKey: 'ambient-coast',
  },
  'coast-lighthouse': {
    id: 'coast-lighthouse',
    label: '潮声灯塔',
    background: coastLighthouseBackground,
    battleTheme: 'coast',
    ambientKey: 'ambient-coast',
  },
};

export function sceneForNode(
  node: AdventureNodeView,
  authoredScenes: readonly AdventureSceneView[] = [],
  installationId?: string,
): AdventureScene {
  const sceneId = node.sceneId;
  const authored = authoredScenes.find((scene) => scene.sceneId === sceneId);
  if (authored !== undefined) {
    return {
      id: authored.sceneId,
      label: authored.name,
      background: installationId === undefined
        ? authored.background
        : contentAssetUrl(installationId, authored.background),
      battleTheme: authored.battleTheme,
      ambientKey: authored.ambientKey as GameSoundKey | undefined,
    };
  }
  if (sceneId !== undefined && sceneId in SCENES) {
    return SCENES[sceneId]!;
  }
  if (node.nodeId.startsWith('inn.')) {
    return SCENES['inn-warm-lantern']!;
  }
  if (node.type === 'encounter') {
    return SCENES['bridge-guardian']!;
  }
  if (node.type === 'camp' || node.type === 'chapter-end') {
    return SCENES['camp-fire']!;
  }
  return SCENES['forest-path']!;
}
