import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it } from 'vitest';
import BattleRulesAcceptance from './BattleRulesAcceptance.vue';

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function mountPage(): HTMLElement {
  const root = document.createElement('div');
  document.body.append(root);
  const app = createApp(BattleRulesAcceptance);
  mountedApp = app;
  app.mount(root);
  return root;
}

function buttonByText(
  root: HTMLElement,
  text: string,
): HTMLButtonElement | undefined {
  return [...root.querySelectorAll('button')].find(
    (button) => button.textContent?.trim() === text,
  );
}

describe('BattleRulesAcceptance', () => {
  it('runs the deterministic fixture and exposes the complete turn result', async () => {
    const root = mountPage();
    const page = root.querySelector<HTMLElement>('.battle-acceptance')!;
    expect(page.dataset).toMatchObject({
      rulesVersion: 'card-battle-v1',
      stateVersion: '0',
      phase: 'player-action',
      enemyHp: '36',
      partyHp: '30',
      intent: 'attack:6',
      exhaustCount: '0',
      fixturePassed: 'not-run',
    });

    buttonByText(root, '运行完整回合 fixture')?.click();
    await nextTick();

    expect(page.dataset).toMatchObject({
      stateVersion: '5',
      phase: 'player-action',
      enemyHp: '25',
      partyHp: '29',
      intent: 'defend:6',
      exhaustCount: '1',
      fixturePassed: 'true',
    });
    expect(root.textContent).toContain('fixture 通过');
    expect(root.textContent).toContain('敌人意图 attack 6');
    expect(root.textContent).toContain('回合 2 开始');
    expect(root.textContent).toContain('灼烧结算');

    buttonByText(root, '重置')?.click();
    await nextTick();
    expect(page.dataset).toMatchObject({
      stateVersion: '0',
      enemyHp: '36',
      fixturePassed: 'not-run',
    });
  });
});
