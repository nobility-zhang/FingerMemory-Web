import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import {
  companionExpressions,
  lia,
} from './catalog';
import CompanionPortrait from './CompanionPortrait.vue';

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('CompanionPortrait', () => {
  it.each(companionExpressions)(
    'renders Lia expression %s through the stable portrait contract',
    async (expression) => {
      const root = document.createElement('div');
      document.body.append(root);
      mountedApp = createApp(CompanionPortrait, {
        companion: lia,
        expression,
      });
      mountedApp.mount(root);
      await nextTick();

      const figure = root.querySelector(
        `[data-companion="lia"][data-expression="${expression}"]`,
      );
      const image = root.querySelector<HTMLImageElement>('img');
      expect(figure).not.toBeNull();
      expect(image?.getAttribute('src')).toBe(lia.portraits[expression]);
      expect(image?.alt).toContain('莉娅');
      expect(image?.draggable).toBe(false);
    },
  );
});
