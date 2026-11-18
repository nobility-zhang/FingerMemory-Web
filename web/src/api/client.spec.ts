import { describe, expect, it, vi } from 'vitest';
import { AccountApi } from './client';

const user = {
  id: 'user-a',
  username: 'alice',
  displayName: 'Alice',
  preferences: {
    nickname: 'Alice',
    avatarKey: 'starter',
    timezone: 'UTC',
    dailyGoal: 20,
    theme: 'starlit',
    soundEnabled: true,
    shortcutsJson: '{}',
  },
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('AccountApi', () => {
  it('treats an unauthenticated session as logged out', async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({ code: 'AUTH_REQUIRED', message: '请先登录' }, 401),
    );
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await expect(api.loadCurrentUser()).resolves.toBeNull();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/me',
      expect.objectContaining({ credentials: 'same-origin' }),
    );
  });

  it('keeps CSRF in memory and refreshes it after a session-changing login', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: `csrf-${calls.length}`,
        });
      }
      if (String(url).endsWith('/auth/login')) {
        return jsonResponse(user);
      }
      if (String(url).endsWith('/lexicons')) {
        return jsonResponse({
          id: 'lexicon-a',
          name: '私人词库',
          description: '',
          source: 'user',
          archivedAtMs: null,
          createdAtMs: 1,
          updatedAtMs: 1,
          version: 0,
        }, 201);
      }
      return jsonResponse([]);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.login('alice', 'correct horse battery');
    await api.createLexicon('私人词库', '');

    const csrfCalls = calls.filter((call) => call.url.endsWith('/auth/csrf'));
    expect(csrfCalls).toHaveLength(2);
    const loginCall = calls.find((call) => call.url.endsWith('/auth/login'));
    const loginHeaders = new Headers(loginCall?.init?.headers);
    expect(loginHeaders.get('X-CSRF-TOKEN')).toBe('csrf-1');
    expect(loginCall?.init?.body).toBeInstanceOf(URLSearchParams);
    const createCall = calls.find(
      (call) => call.url.endsWith('/lexicons') && call.init?.method === 'POST',
    );
    expect(new Headers(createCall?.init?.headers).get('X-CSRF-TOKEN')).toBe('csrf-3');
    expect(localStorage.length).toBe(0);
  });

  it('drops a stale CSRF token after an expired session response', async () => {
    const calls: string[] = [];
    const fetchMock = vi.fn(async (url: string | URL | Request) => {
      const path = String(url);
      calls.push(path);
      if (path.endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: `csrf-${calls.length}`,
        });
      }
      if (path.endsWith('/auth/login')) {
        return jsonResponse(user);
      }
      if (path.includes('/lexicons')) {
        return jsonResponse([]);
      }
      if (path.endsWith('/auth/logout')) {
        return jsonResponse({ code: 'AUTH_REQUIRED', message: '请先登录' }, 401);
      }
      return jsonResponse({}, 404);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.login('alice', 'correct horse battery');
    await api.listLexicons();
    await expect(api.logout()).rejects.toMatchObject({ status: 401 });
    await api.login('alice', 'correct horse battery');

    const csrfCalls = calls.filter((path) => path.endsWith('/auth/csrf'));
    expect(csrfCalls).toHaveLength(3);
  });

  it('sends the stable submission id and versions for a formal review', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-review',
        });
      }
      return jsonResponse({
        outcome: 'created',
        state: { dueAt: 2, version: 1 },
        log: { submissionId: 'submission-1', reviewedAt: 1 },
        session: null,
        dataVersion: 3,
        rewardEligible: true,
      });
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.submitReview({
      sessionId: 'session-1',
      submissionId: 'submission-1',
      rating: 'good',
      assessmentType: 'flashcard-self-report',
      assessmentPolicyVersion: 2,
      assessmentStage: 'reveal-for-check',
      priorExposure: false,
      explicitNotRemembered: false,
      answerWasHidden: true,
      answerRevealed: true,
      expectedDataVersion: 2,
      expectedReviewVersion: 0,
      expectedSessionVersion: 1,
    });

    const reviewCall = calls.find((call) => call.url.endsWith('/reviews'));
    const headers = new Headers(reviewCall?.init?.headers);
    expect(headers.get('Idempotency-Key')).toBe('submission-1');
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-review');
    expect(JSON.parse(String(reviewCall?.init?.body))).toMatchObject({
      rating: 'good',
      expectedDataVersion: 2,
      expectedReviewVersion: 0,
      expectedSessionVersion: 1,
      evidence: {
        userConfirmedRating: true,
        answerWasHidden: true,
        answerRevealed: true,
        assessmentType: 'flashcard-self-report',
        assessmentPolicyVersion: 2,
        assessmentStage: 'reveal-for-check',
      },
    });
  });

  it('keeps CSRF and idempotency on battle commands', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const battleResponse = {
      battle: { battleId: 'battle-1' },
      events: [],
      stateVersion: 0,
    };
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-battle',
        });
      }
      return jsonResponse(battleResponse);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.createBattle({
      battleId: 'battle-1',
      encounterId: 'bridge-guardian',
      sessionId: 'session-1',
      studyItemId: 'study-1',
      companionKey: 'serin',
      seed: 42,
      shuffle: false,
      actionId: 'battle-create-1',
    });
    await api.getBattle('battle/one');
    await api.claimBattleLearning({
      battleId: 'battle-1',
      actionId: 'battle-claim-1',
      expectedStateVersion: 0,
      submissionId: 'review-1',
    });
    await api.playBattleCard({
      battleId: 'battle-1',
      actionId: 'battle-play-1',
      expectedStateVersion: 1,
      cardInstanceId: 'card-07',
      targetId: 'bridge-guardian',
    });
    await api.useBattleCompanionSkill({
      battleId: 'battle-1',
      actionId: 'battle-skill-1',
      expectedStateVersion: 2,
    });
    await api.endBattleTurn({
      battleId: 'battle-1',
      actionId: 'battle-end-1',
      expectedStateVersion: 3,
      nextSessionId: 'session-2',
      nextStudyItemId: 'study-2',
    });

    const createCall = calls.find(
      (call) => call.url.endsWith('/battles') && call.init?.method === 'POST',
    );
    expect(new Headers(createCall?.init?.headers).get('Idempotency-Key'))
      .toBe('battle-create-1');
    expect(new Headers(createCall?.init?.headers).get('X-CSRF-TOKEN'))
      .toBe('csrf-battle');
    expect(JSON.parse(String(createCall?.init?.body))).toEqual({
      battleId: 'battle-1',
      encounterId: 'bridge-guardian',
      sessionId: 'session-1',
      studyItemId: 'study-1',
      companionKey: 'serin',
      seed: 42,
      shuffle: false,
    });

    const getCall = calls.find((call) => call.url.endsWith('/battles/battle%2Fone'));
    expect(getCall?.init?.method).toBe('GET');
    expect(new Headers(getCall?.init?.headers).has('Idempotency-Key')).toBe(false);

    const claimCall = calls.find((call) => call.url.endsWith('/learning-claims'));
    expect(new Headers(claimCall?.init?.headers).get('Idempotency-Key'))
      .toBe('battle-claim-1');
    expect(JSON.parse(String(claimCall?.init?.body))).toEqual({
      expectedStateVersion: 0,
      submissionId: 'review-1',
    });

    const playCall = calls.find((call) => call.url.endsWith('/cards'));
    expect(JSON.parse(String(playCall?.init?.body))).toEqual({
      expectedStateVersion: 1,
      cardInstanceId: 'card-07',
      targetId: 'bridge-guardian',
    });

    const skillCall = calls.find((call) => call.url.endsWith('/companion-skill'));
    expect(JSON.parse(String(skillCall?.init?.body))).toEqual({
      expectedStateVersion: 2,
    });

    const endCall = calls.find((call) => call.url.endsWith('/end-turn'));
    expect(JSON.parse(String(endCall?.init?.body))).toEqual({
      expectedStateVersion: 3,
      nextSessionId: 'session-2',
      nextStudyItemId: 'study-2',
    });
    for (const commandCall of [claimCall, playCall, skillCall, endCall]) {
      expect(new Headers(commandCall?.init?.headers).get('X-CSRF-TOKEN'))
        .toBe('csrf-battle');
    }
  });

  it('keeps CSRF and idempotency on adventure checkpoints', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-adventure',
        });
      }
      if (String(url).includes('/adventures?')) {
        return jsonResponse([]);
      }
      if (String(url).endsWith('/adventures/companions')) {
        return jsonResponse(['lia', 'serin', 'fina']);
      }
      if (String(url).includes('/adventures/recollections?')) {
        return jsonResponse([]);
      }
      return jsonResponse({
        outcome: 'created',
        run: { id: 'run-1', runVersion: 0 },
      }, 201);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.listAdventures();
    await api.listUnlockedCompanions();
    await api.listAdventureRecollections();
    await api.startAdventure({
      installationId: 'installation-1',
      companionKey: 'serin',
      reduceMotion: true,
      actionId: 'adventure-start-1',
    });
    await api.getAdventure('run/one');
    await api.startAdventureEncounter('run-1', 'adventure-encounter-1');
    await api.actAdventure('run-1', 'adventure-choice-1', {
      expectedVersion: 2,
      action: 'choose',
      choiceId: 'cross',
    });
    await api.actAdventure('run-1', 'adventure-claim-1', {
      expectedVersion: 3,
      action: 'claim-reward',
      choiceId: 'party-strike',
    });
    await api.actAdventure('run-1', 'adventure-companion-1', {
      expectedVersion: 4,
      action: 'select-companion',
      companionKey: 'fina',
    });

    expect(calls.some(
      (call) => call.url.endsWith('/adventures?limit=20')
        && call.init?.method === 'GET',
    )).toBe(true);
    expect(calls.some(
      (call) => call.url.endsWith('/adventures/companions')
        && call.init?.method === 'GET',
    )).toBe(true);
    expect(calls.some(
      (call) => call.url.endsWith('/adventures/recollections?limit=50')
        && call.init?.method === 'GET',
    )).toBe(true);
    const startCall = calls.find(
      (call) => call.url.endsWith('/adventures') && call.init?.method === 'POST',
    );
    expect(new Headers(startCall?.init?.headers).get('Idempotency-Key'))
      .toBe('adventure-start-1');
    expect(new Headers(startCall?.init?.headers).get('X-CSRF-TOKEN'))
      .toBe('csrf-adventure');
    expect(JSON.parse(String(startCall?.init?.body))).toEqual({
      installationId: 'installation-1',
      companionKey: 'serin',
      reduceMotion: true,
    });

    expect(calls.some(
      (call) => call.url.endsWith('/adventures/run%2Fone')
        && call.init?.method === 'GET',
    )).toBe(true);
    const encounterCall = calls.find(
      (call) => call.url.endsWith('/adventures/run-1/encounters'),
    );
    expect(new Headers(encounterCall?.init?.headers).get('Idempotency-Key'))
      .toBe('adventure-encounter-1');
    const actionCall = calls.find(
      (call) => call.url.endsWith('/adventures/run-1/actions'),
    );
    expect(new Headers(actionCall?.init?.headers).get('Idempotency-Key'))
      .toBe('adventure-choice-1');
    expect(new Headers(actionCall?.init?.headers).get('X-CSRF-TOKEN'))
      .toBe('csrf-adventure');
    expect(JSON.parse(String(actionCall?.init?.body))).toEqual({
      expectedVersion: 2,
      action: 'choose',
      choiceId: 'cross',
    });
    const claimCall = calls.find(
      (call) => call.url.endsWith('/adventures/run-1/actions')
        && new Headers(call.init?.headers).get('Idempotency-Key')
          === 'adventure-claim-1',
    );
    expect(JSON.parse(String(claimCall?.init?.body))).toEqual({
      expectedVersion: 3,
      action: 'claim-reward',
      choiceId: 'party-strike',
    });
    const companionCall = calls.find(
      (call) => call.url.endsWith('/adventures/run-1/actions')
        && new Headers(call.init?.headers).get('Idempotency-Key')
          === 'adventure-companion-1',
    );
    expect(JSON.parse(String(companionCall?.init?.body))).toEqual({
      expectedVersion: 4,
      action: 'select-companion',
      companionKey: 'fina',
    });
  });

  it('uploads content packages as multipart with CSRF and idempotency', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const preview = {
      fileName: 'warm-lantern.fmbook',
      bookId: 'warm-lantern',
      contentVersion: '1.0.0',
      contentHash: 'a'.repeat(64),
    };
    const installation = {
      id: 'installation-1',
      bookId: 'warm-lantern',
      contentVersion: '1.0.0',
    };
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-content',
        });
      }
      if (String(url).endsWith('/content-packages/preview')) {
        return jsonResponse(preview);
      }
      if (String(url).endsWith('/content-packages')) {
        return jsonResponse({
          outcome: 'installed',
          installation,
        }, 201);
      }
      return jsonResponse([installation]);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');
    const file = new File(['archive'], 'warm-lantern.fmbook', {
      type: 'application/zip',
    });

    await api.previewContentPackage(file);
    await api.installContentPackage(file, 'content-install-1');
    await api.listContentPackages(20);

    const previewCall = calls.find(
      (call) => call.url.endsWith('/content-packages/preview'),
    );
    const previewHeaders = new Headers(previewCall?.init?.headers);
    expect(previewCall?.init?.body).toBeInstanceOf(FormData);
    expect(previewHeaders.get('X-CSRF-TOKEN')).toBe('csrf-content');
    expect(previewHeaders.has('Content-Type')).toBe(false);

    const installCall = calls.find(
      (call) => call.url.endsWith('/content-packages')
        && call.init?.method === 'POST',
    );
    const installHeaders = new Headers(installCall?.init?.headers);
    expect(installCall?.init?.body).toBeInstanceOf(FormData);
    expect(installHeaders.get('Idempotency-Key')).toBe('content-install-1');
    expect(installHeaders.get('X-CSRF-TOKEN')).toBe('csrf-content');
    expect(installHeaders.has('Content-Type')).toBe(false);

    expect(calls.some(
      (call) => call.url.endsWith('/content-packages?limit=20')
        && call.init?.method === 'GET',
    )).toBe(true);
  });

  it('keeps CSRF and epoch commands on GM sandbox requests', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-sandbox',
        });
      }
      if (String(url).includes('/gm/sandboxes?')) {
        return jsonResponse({ items: [], nextCursor: null });
      }
      if (String(url).endsWith('/gm/sandboxes')) {
        return jsonResponse({ id: 'sandbox-1' }, 201);
      }
      return jsonResponse({
        commandId: 'sandbox-create-1',
        status: 'applied',
        scope: 'sandbox',
        sandbox: { id: 'sandbox-1' },
      });
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.listGmSandboxes(10);
    await api.createGmSandbox('sandbox-create-1', {
      templateId: 'card-battle-v1-tutorial',
      contentInstallationId: null,
      reason: 'create sandbox',
    });
    await api.executeGmSandboxCommand('sandbox-1', 'sandbox-create-2', {
      actionId: 'sandbox-create-2',
      expectedVersion: 0,
      dataEpoch: 'epoch-1',
      reason: 'set sandbox clock',
      command: 'set-clock',
      args: { mode: 'fixed', nowMs: 1_700_000_000_000 },
    });

    const listCall = calls.find((call) => call.url.includes('/gm/sandboxes?'));
    expect(listCall?.init?.method).toBe('GET');
    const createCall = calls.find(
      (call) => call.url.endsWith('/gm/sandboxes') && call.init?.method === 'POST',
    );
    expect(new Headers(createCall?.init?.headers).get('Idempotency-Key'))
      .toBe('sandbox-create-1');
    expect(new Headers(createCall?.init?.headers).get('X-CSRF-TOKEN'))
      .toBe('csrf-sandbox');
    expect(JSON.parse(String(createCall?.init?.body))).toEqual({
      templateId: 'card-battle-v1-tutorial',
      contentInstallationId: null,
      reason: 'create sandbox',
    });

    const commandCall = calls.find((call) => call.url.endsWith('/commands'));
    expect(new Headers(commandCall?.init?.headers).get('Idempotency-Key'))
      .toBe('sandbox-create-2');
    expect(JSON.parse(String(commandCall?.init?.body))).toEqual({
      command: 'set-clock',
      expectedVersion: 0,
      dataEpoch: 'epoch-1',
      reason: 'set sandbox clock',
      args: { mode: 'fixed', nowMs: 1_700_000_000_000 },
    });
  });

  it('keeps CSRF and idempotency on GM account commands', async () => {
    const calls: Array<{ url: string; init: RequestInit | undefined }> = [];
    const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      if (String(url).endsWith('/auth/csrf')) {
        return jsonResponse({
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf',
          token: 'csrf-gm',
        });
      }
      return jsonResponse({
        id: 'managed-user',
        username: 'sandbox-user',
        displayName: 'Sandbox User',
        role: 'USER',
        status: 'active',
        accountKind: 'test',
        version: 0,
      }, 201);
    });
    const api = new AccountApi(fetchMock as typeof fetch, '/api/v1');

    await api.createGmUser('gm-create-1', {
      username: 'sandbox-user',
      displayName: 'Sandbox User',
      password: 'sandbox horse battery',
      role: 'USER',
      accountKind: 'test',
      reason: 'create isolated sandbox account',
    });

    const createCall = calls.find((call) => call.url.endsWith('/gm/users'));
    const headers = new Headers(createCall?.init?.headers);
    expect(headers.get('Idempotency-Key')).toBe('gm-create-1');
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf-gm');
    expect(JSON.parse(String(createCall?.init?.body))).toMatchObject({
      username: 'sandbox-user',
      accountKind: 'test',
      role: 'USER',
    });
  });
});
