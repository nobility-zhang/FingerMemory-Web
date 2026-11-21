import { createApp, nextTick } from 'vue';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type {
  AccessTokenApi,
  AccountClient,
  PersonalAccessTokenView,
} from '../api/client';
import CliTokenPanel from './CliTokenPanel.vue';

const activeToken: PersonalAccessTokenView = {
  id: 'token-1',
  name: 'Laptop',
  prefix: 'fm_pat_abcd',
  scope: 'account',
  createdAtMs: 1_763_200_000_000,
  expiresAtMs: 1_770_000_000_000,
  lastUsedAtMs: null,
  revokedAtMs: null,
};

let mountedApp: ReturnType<typeof createApp> | null = null;

afterEach(() => {
  mountedApp?.unmount();
  mountedApp = null;
  document.body.innerHTML = '';
});

function createApi() {
  const listPersonalAccessTokens = vi
    .fn<AccessTokenApi['listPersonalAccessTokens']>()
    .mockResolvedValue([activeToken]);
  const createPersonalAccessToken = vi
    .fn<AccessTokenApi['createPersonalAccessToken']>()
    .mockResolvedValue({
      token: {
        ...activeToken,
        id: 'token-2',
        name: 'CLI',
        prefix: 'fm_pat_new1',
      },
      plaintextToken: 'fm_pat_new1_only-returned-once',
    });
  const revokePersonalAccessToken = vi
    .fn<AccessTokenApi['revokePersonalAccessToken']>()
    .mockResolvedValue(undefined);
  return {
    listPersonalAccessTokens,
    createPersonalAccessToken,
    revokePersonalAccessToken,
  };
}

describe('CliTokenPanel', () => {
  it('shows metadata and reveals created plaintext only in the create state', async () => {
    const api = createApi();
    mountPanel(api);
    await settle();
    expect(document.body.textContent).toContain('Laptop');
    expect(document.body.textContent).toContain('fm_pat_abcd…');

    const name = document.querySelector<HTMLInputElement>('input[type="text"]');
    expect(name).not.toBeNull();
    name!.value = 'Build server';
    name!.dispatchEvent(new Event('input'));
    document.querySelector('form')?.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );
    await settle();

    expect(api.createPersonalAccessToken).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Build server' }),
    );
    expect(document.body.textContent).toContain(
      'fm_pat_new1_only-returned-once',
    );
    expect(document.body.textContent).toContain('只显示一次');
    expect(document.body.textContent).not.toContain('tokenHash');
  });

  it('revokes an active token and refreshes the list', async () => {
    const api = createApi();
    api.listPersonalAccessTokens
      .mockResolvedValueOnce([activeToken])
      .mockResolvedValueOnce([]);
    mountPanel(api);
    await settle();

    const revoke = [...document.querySelectorAll('button')].find((button) =>
      button.textContent?.includes('撤销'));
    expect(revoke).toBeDefined();
    revoke?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await settle();

    expect(api.revokePersonalAccessToken).toHaveBeenCalledWith('token-1');
    expect(document.body.textContent).toContain('还没有 CLI 令牌');
  });
});

function mountPanel(api: ReturnType<typeof createApi>) {
  const root = document.createElement('div');
  document.body.append(root);
  mountedApp = createApp(CliTokenPanel, {
    api: api as unknown as AccountClient & AccessTokenApi,
  });
  mountedApp.mount(root);
}

async function settle() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}
