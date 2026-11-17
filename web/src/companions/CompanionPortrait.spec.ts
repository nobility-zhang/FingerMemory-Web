import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import {
  companionExpressions,
  companions,
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
      expect(image?.alt).toContain(companion.name);
      expect(image?.draggable).toBe(false);
    },
  );
});
