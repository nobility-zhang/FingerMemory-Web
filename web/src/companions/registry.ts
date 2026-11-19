import type {
  AdventureCharacterView,
  AdventureRunView,
} from '../api/contracts';
import {
  companions as builtInCompanions,
  type CompanionProfile,
  type CompanionPortraitSet,
} from './catalog';

export function contentAssetUrl(
  installationId: string,
  assetPath: string,
): string {
  const encodedPath = assetPath
    .split('/')
    .map((segment) => encodeURIComponent(segment))
    .join('/');
  return `/api/v1/content-packages/${encodeURIComponent(installationId)}/assets/${encodedPath}`;
}

function portraitSet(
  installationId: string,
  raw: Record<string, string>,
): CompanionPortraitSet {
  return Object.fromEntries(
    Object.entries(raw).map(([expression, path]) => [
      expression,
      contentAssetUrl(installationId, path),
    ]),
  ) as CompanionPortraitSet;
}

export function profileFromCharacter(
  character: AdventureCharacterView,
): CompanionProfile {
  const costumes = Object.fromEntries(
    Object.entries(character.costumes).map(([costumeId, portraits]) => [
      costumeId,
      portraitSet(character.installationId, portraits),
    ]),
  ) as Record<string, CompanionPortraitSet>;
  const defaultPortraits = costumes[character.defaultCostumeId]
    ?? Object.values(costumes)[0];
  if (defaultPortraits === undefined) {
    throw new Error(`Character ${character.characterKey} has no portrait set`);
  }
  return {
    id: character.characterKey,
    name: character.name,
    epithet: character.epithet,
    age: character.age || undefined,
    description: character.description,
    source: 'content-package',
    defaultCostumeId: character.defaultCostumeId,
    portraits: defaultPortraits,
    costumes,
    dialogue: character.dialogue,
    skill: character.skill ?? undefined,
  };
}

export function mergeCompanions(
  base: readonly CompanionProfile[],
  characters: readonly AdventureCharacterView[] = [],
): CompanionProfile[] {
  const merged = new Map(base.map((companion) => [companion.id, companion]));
  for (const character of characters) {
    if (!merged.has(character.characterKey)) {
      merged.set(character.characterKey, profileFromCharacter(character));
    }
  }
  return [...merged.values()];
}

export function companionsForRun(
  run: AdventureRunView,
  base: readonly CompanionProfile[] = builtInCompanions,
): CompanionProfile[] {
  return mergeCompanions(base, run.characters ?? []);
}
