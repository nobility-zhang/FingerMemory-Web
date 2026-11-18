import { describe, expect, it } from 'vitest';
import type { AdventureNodeView } from '../api/contracts';
import { sceneForNode } from './sceneCatalog';

function node(overrides: Partial<AdventureNodeView>): AdventureNodeView {
  return {
    nodeId: 'forest.signpost',
    chapterId: 'chapter-1',
    type: 'dialogue',
    title: '林间路标',
    speaker: '旅伴',
    text: '风穿过树梢。',
    choices: [],
    ...overrides,
  };
}

describe('adventure scene catalog', () => {
  it('resolves authored coast scenes and their ambient track', () => {
    const scene = sceneForNode(node({
      nodeId: 'coast.tidepools',
      sceneId: 'coast-tidepools',
      costumeId: 'beach-swimwear',
    }));

    expect(scene.id).toBe('coast-tidepools');
    expect(scene.battleTheme).toBe('coast');
    expect(scene.ambientKey).toBe('ambient-coast');
    expect(scene.background).toContain('coast-tidepools');
  });

  it('keeps older packages on their existing fallback art', () => {
    expect(sceneForNode(node({ nodeId: 'inn.arrival' })).id)
      .toBe('inn-warm-lantern');
    expect(sceneForNode(node({ type: 'encounter' })).id)
      .toBe('bridge-guardian');
    expect(sceneForNode(node({ type: 'camp' })).id)
      .toBe('camp-fire');
    expect(sceneForNode(node({ nodeId: 'chapter3.grove' })).id)
      .toBe('forest-path');
  });
});
