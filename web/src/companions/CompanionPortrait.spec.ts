import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import {
  companionCostumeIds,
  companionExpressions,
  companions,
  portraitsFor,
} from './catalog';
import CompanionPortrait from './CompanionPortrait.vue';

let mountedApp: ReturnType<typeof createApp> | null = null;

const portraitCases = companions.flatMap((companion) =>
  companionExpressions.map(
    (expression) => [companion.id, expression, companion] as const,
  ),
);

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('CompanionPortrait', () => {
  it.each(portraitCases)(
    'renders %s expression %s through the stable portrait contract',
    async (_companionId, expression, companion) => {
      const root = document.createElement('div');
      document.body.append(root);
      mountedApp = createApp(CompanionPortrait, {
        companion,
        expression,
      });
      mountedApp.mount(root);
      await nextTick();

      const figure = root.querySelector(
        `[data-companion="${companion.id}"][data-expression="${expression}"]`,
      );
      const image = root.querySelector<HTMLImageElement>('img');
      expect(figure).not.toBeNull();
      expect(image?.getAttribute('src')).toBe(
        companion.portraits[expression],
      );
      expect(figure?.getAttribute('data-costume-id')).toBe('summer-travel');
      expect(image?.alt).toContain(companion.name);
      expect(image?.draggable).toBe(false);
    },
  );

  it.each([
    ['lia', 'beach-swimwear'],
    ['serin', 'beach-swimwear'],
  ] as const)(
    'renders %s in the authored %s costume',
    async (companionId, costumeId) => {
      const companion = companions.find((candidate) => candidate.id === companionId)!;
      const root = document.createElement('div');
      document.body.append(root);
      mountedApp = createApp(CompanionPortrait, {
        companion,
        costumeId,
        expression: 'cheer',
      });
      mountedApp.mount(root);
      await nextTick();

      const figure = root.querySelector(
        `[data-companion="${companionId}"][data-costume-id="${costumeId}"]`,
      );
      const image = root.querySelector<HTMLImageElement>('img');
      expect(figure).not.toBeNull();
      expect(image?.getAttribute('src')).toBe(
        portraitsFor(companion, costumeId).cheer,
      );
      expect(image?.getAttribute('src')).not.toBe(
        companion.portraits.cheer,
      );
    },
  );

  it('falls back to summer travel when a companion has no authored costume', async () => {
    const fina = companions.find((companion) => companion.id === 'fina')!;
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(CompanionPortrait, {
      companion: fina,
      costumeId: companionCostumeIds[1],
      expression: 'neutral',
    });
    mountedApp.mount(root);
    await nextTick();

    const image = root.querySelector<HTMLImageElement>('img');
    expect(image?.getAttribute('src')).toBe(fina.portraits.neutral);
  });
});
