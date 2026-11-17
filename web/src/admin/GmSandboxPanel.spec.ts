import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  GmCommandReceipt,
  GmSandbox,
  GmSandboxCommandInput,
  GmSandboxState,
} from '../api/client';
import GmSandboxPanel from './GmSandboxPanel.vue';

function sandbox(overrides: Partial<GmSandbox> = {}): GmSandbox {
  return {
    id: 'sandbox-1',
    scope: 'sandbox',
    ownerAdminId: 'admin-1',
    templateId: 'card-battle-v1-tutorial',
    contentInstallationId: null,
    version: 0,
    dataEpoch: 'epoch-1',
    state: {
      schemaVersion: 1,
      fixtureVersion: 1,
      fixtureKey: 'bridge-tutorial-v1',
      currentNodeId: 'bridge',
      encounterId: 'bridge-guardian',
      clockMode: 'real',
      virtualNowMs: null,
      randomSeed: 1_592_676_916,
      deck: [
        'companion-guard',
        'traveler-strike',
        'ember-rune',
        'observe-signpost',
        'traveler-strike',
        'companion-guard',
        'herb-pouch',
        'traveler-strike',
        'ember-rune',
        'party-strike',
        'companion-guard',
        'party-strike',
      ],
      unlockedCharacters: ['lia', 'serin'],
      availableNodes: ['prologue', 'bridge', 'camp', 'chapter-end'],
      availableCards: [
        'traveler-strike',
        'companion-guard',
        'ember-rune',
        'herb-pouch',
        'observe-signpost',
        'party-strike',
      ],
      availableCharacters: ['lia', 'serin', 'fina'],
    },
    ...overrides,
  };
}

function buttonByText(root: ParentNode, text: string): HTMLButtonElement {
  return [...root.querySelectorAll<HTMLButtonElement>('button')].find(
    (button) => button.textContent?.trim().includes(text),
  )!;
}

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

describe('GmSandboxPanel', () => {
  it('shows isolated state and submits jump, clock and reset commands', async () => {
    let current = sandbox();
    const executeGmSandboxCommand = vi.fn(async (
      _sandboxId: string,
      _key: string,
      input: GmSandboxCommandInput,
    ): Promise<GmCommandReceipt> => {
      if (input.command === 'jump-node') {
        current = sandbox({
          ...current,
          version: current.version + 1,
          state: {
            ...current.state,
            currentNodeId: input.args.nodeId,
            encounterId: input.args.nodeId === 'bridge' ? 'bridge-guardian' : null,
          },
        });
      }
      if (input.command === 'set-clock') {
        current = sandbox({
          ...current,
          version: current.version + 1,
          state: {
            ...current.state,
            clockMode: input.args.mode,
            virtualNowMs: input.args.mode === 'fixed' ? input.args.nowMs : null,
          },
        });
      }
      if (input.command === 'grant-cards') {
        current = sandbox({
          ...current,
          version: current.version + 1,
          state: {
            ...current.state,
            deck: [...current.state.deck, ...input.args.cardDefinitionIds],
          },
        });
      }
      if (input.command === 'unlock-character') {
        current = sandbox({
          ...current,
          version: current.version + 1,
          state: {
            ...current.state,
            unlockedCharacters: [
              ...current.state.unlockedCharacters,
              input.args.characterKey,
            ],
          },
        });
      }
      if (input.command === 'reset-sandbox') {
        current = sandbox({
          version: current.version + 1,
          dataEpoch: 'epoch-2',
        });
      }
      return {
        commandId: input.actionId,
        status: 'applied',
        scope: 'sandbox',
        sandbox: current,
      };
    });
    const api = {
      listGmSandboxes: vi.fn(async () => ({
        items: [current],
        nextCursor: null,
      })),
      createGmSandbox: vi.fn(),
      executeGmSandboxCommand,
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmSandboxPanel, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('TEST · SANDBOX');
      expect(root.querySelector('[data-field="node"]')?.textContent).toContain('bridge');
    });

    const nodeSelect = root.querySelector<HTMLSelectElement>(
      'select[aria-label="目标节点"]',
    )!;
    nodeSelect.value = 'camp';
    nodeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    buttonByText(root, '跳转').click();
    await vi.waitFor(() => {
      expect(root.querySelector('[data-field="node"]')?.textContent).toContain('camp');
      expect(executeGmSandboxCommand).toHaveBeenCalledWith(
        'sandbox-1',
        expect.any(String),
        expect.objectContaining({
          expectedVersion: 0,
          dataEpoch: 'epoch-1',
          command: 'jump-node',
          args: { nodeId: 'camp' },
        }),
      );
    });

    buttonByText(root, '加入牌组').click();
    await vi.waitFor(() => {
      expect(executeGmSandboxCommand).toHaveBeenLastCalledWith(
        'sandbox-1',
        expect.any(String),
        expect.objectContaining({
          expectedVersion: 1,
          command: 'grant-cards',
          args: { cardDefinitionIds: ['traveler-strike'] },
        }),
      );
      expect(root.querySelector('[data-field="version"]')?.textContent).toBe('2');
    });

    buttonByText(root, '解锁').click();
    await vi.waitFor(() => {
      expect(executeGmSandboxCommand).toHaveBeenLastCalledWith(
        'sandbox-1',
        expect.any(String),
        expect.objectContaining({
          expectedVersion: 2,
          command: 'unlock-character',
          args: { characterKey: 'fina' },
        }),
      );
      expect(root.querySelector('[data-field="version"]')?.textContent).toBe('3');
    });

    const clockModeSelect = root.querySelector<HTMLSelectElement>(
      'select[aria-label="时钟模式"]',
    )!;
    clockModeSelect.value = 'fixed';
    clockModeSelect.dispatchEvent(new Event('change', { bubbles: true }));
    await nextTick();
    const clockInput = root.querySelector<HTMLInputElement>(
      'input[aria-label="固定 UTC 时间"]',
    )!;
    clockInput.value = '2030-01-01T00:00';
    clockInput.dispatchEvent(new Event('input', { bubbles: true }));
    buttonByText(root, '设置时钟').click();
    await vi.waitFor(() => {
      expect(root.querySelector('[data-field="clock"]')?.textContent)
        .toContain('2030-01-01T00:00:00.000Z');
    });
    expect(executeGmSandboxCommand).toHaveBeenLastCalledWith(
      'sandbox-1',
      expect.any(String),
      expect.objectContaining({
        expectedVersion: 3,
        command: 'set-clock',
        args: { mode: 'fixed', nowMs: Date.parse('2030-01-01T00:00:00Z') },
      }),
    );

    buttonByText(root, '重置并切换 epoch').click();
    await vi.waitFor(() => {
      expect(root.querySelector('[data-field="epoch"]')?.textContent).toBe('epoch-2');
      expect(root.querySelector('[data-field="version"]')?.textContent).toBe('5');
    });
  });

  it('creates a sandbox when none exists', async () => {
    const created = sandbox();
    const api = {
      listGmSandboxes: vi.fn(async () => ({ items: [], nextCursor: null })),
      createGmSandbox: vi.fn(async () => created),
      executeGmSandboxCommand: vi.fn(),
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmSandboxPanel, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.textContent).toContain('还没有隔离 sandbox');
    });
    buttonByText(root, '创建 sandbox').click();

    await vi.waitFor(() => {
      expect(api.createGmSandbox).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          templateId: 'card-battle-v1-tutorial',
          contentInstallationId: null,
        }),
      );
      expect(root.querySelector('[data-field="node"]')?.textContent).toContain('bridge');
    });
  });

  it('accepts legacy responses that omit null clock and encounter fields', async () => {
    const raw = sandbox();
    const state = { ...raw.state };
    delete (state as Partial<GmSandboxState>).virtualNowMs;
    delete (state as Partial<GmSandboxState>).encounterId;
    const api = {
      listGmSandboxes: vi.fn(async () => ({
        items: [{ ...raw, state }],
        nextCursor: null,
      })),
      createGmSandbox: vi.fn(),
      executeGmSandboxCommand: vi.fn(),
    };
    const root = document.createElement('div');
    document.body.append(root);
    mountedApp = createApp(GmSandboxPanel, { api });
    mountedApp.mount(root);

    await vi.waitFor(() => {
      expect(root.querySelector('[data-field="clock"]')?.textContent).toContain('real');
      expect(root.querySelector('[data-field="encounter"]')?.textContent).toContain('无');
    });
  });
});
