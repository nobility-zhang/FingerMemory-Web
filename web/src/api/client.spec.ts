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
      },
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
