import assert from 'node:assert/strict';

const baseUrl = (process.env.FM_BASE_URL ?? 'http://127.0.0.1:5173/api/v1').replace(/\/$/, '');
const suffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const username = process.env.FM_TEST_USERNAME ?? `p2-cross-${suffix}`;
const password = process.env.FM_TEST_PASSWORD ?? 'cross client battery';

class ApiSession {
  #cookies = new Map();
  #csrf;

  async csrf() {
    const response = await this.fetch('/auth/csrf');
    this.#csrf = await response.json();
    return this.#csrf;
  }

  async mutate(path, init) {
    if (this.#csrf === undefined) {
      await this.csrf();
    }
    return this.fetch(path, {
      ...init,
      headers: {
        ...(init.headers ?? {}),
        [this.#csrf.headerName]: this.#csrf.token,
      },
    });
  }

  async fetch(path, init = {}) {
    const headers = new Headers(init.headers);
    if (this.#cookies.size > 0) {
      headers.set(
        'Cookie',
        [...this.#cookies].map(([name, value]) => `${name}=${value}`).join('; '),
      );
    }
    const response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers,
      redirect: 'manual',
    });
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';');
      const separator = pair.indexOf('=');
      if (separator > 0) {
        this.#cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
      }
    }
    return response;
  }

  async json(response, expectedStatus) {
    assert.equal(response.status, expectedStatus);
    if (response.status === 204) {
      return null;
    }
    return response.json();
  }

  clearCsrf() {
    this.#csrf = undefined;
  }
}

async function login(session) {
  await session.csrf();
  const response = await session.mutate('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }),
  });
  await session.json(response, 200);
  session.clearCsrf();
}

function csrfHeaders(session) {
  return session.csrf();
}

const registrar = new ApiSession();
await registrar.csrf();
const registration = await registrar.mutate('/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password, displayName: 'P2 Cross Client' }),
});
const registeredUser = await registrar.json(registration, 201);

const clientA = new ApiSession();
const clientB = new ApiSession();
await Promise.all([login(clientA), login(clientB)]);

const lexiconResponse = await clientA.mutate('/lexicons', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ name: `跨端词库 ${suffix}`, description: 'P2 cross-client' }),
});
const lexicon = await clientA.json(lexiconResponse, 201);

const wordResponse = await clientA.mutate(`/lexicons/${lexicon.id}/words`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    term: 'portable',
    meaning: '可跨端的',
    acceptedAnswers: ['portable'],
    hints: [],
  }),
});
const createdWord = await clientA.json(wordResponse, 201);

const planA = await clientA.json(
  await clientA.fetch('/queue/plan?timeZone=UTC&newLimit=10&reviewLimit=50'),
  200,
);
const planB = await clientB.json(
  await clientB.fetch('/queue/plan?timeZone=UTC&newLimit=10&reviewLimit=50'),
  200,
);
assert.equal(planA.newAvailableCount, 1);
assert.equal(planB.newAvailableCount, 1);

const createSession = async (client) => {
  await csrfHeaders(client);
  return client.json(
    await client.mutate('/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        modeId: 'keyboard-recall',
        timeZone: 'UTC',
        newLimit: 10,
        reviewLimit: 50,
        reduceMotion: false,
      }),
    }),
    201,
  );
};
const [sessionA, sessionB] = await Promise.all([createSession(clientA), createSession(clientB)]);
const studyItemId = createdWord.studyItem.id;

const submitResponse = async (client, session, attemptId, submissionId) => {
  const current = await client.json(
    await client.fetch(`/sessions/${session.session.id}`),
    200,
  );
  const response = await client.mutate(`/sessions/${session.session.id}/responses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      practiceAttemptId: attemptId,
      reviewSubmissionId: submissionId,
      studyItemId,
      response: 'portable',
      answerWasHidden: true,
      hintUsed: false,
      answerRevealed: false,
      abandoned: false,
      expectedDataVersion: current.dataVersion,
      expectedSessionVersion: current.session.version,
    }),
  });
  return client.json(response, 201);
};
await submitResponse(clientA, sessionA, `attempt-a-${suffix}`, `review-a-${suffix}`);
await submitResponse(clientB, sessionB, `attempt-b-${suffix}`, `review-b-${suffix}`);

const readyA = await clientA.json(
  await clientA.fetch(`/sessions/${sessionA.session.id}`),
  200,
);
const preview = await clientA.json(
  await clientA.mutate(`/sessions/${sessionA.session.id}/review-preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      studyItemId,
      expectedDataVersion: readyA.dataVersion,
      expectedSessionVersion: readyA.session.version,
      expectedReviewVersion: 0,
    }),
  }),
  200,
);
assert.deepEqual(Object.keys(preview.previews).sort(), ['again', 'easy', 'good', 'hard']);

const reviewBody = {
  rating: 'good',
  expectedDataVersion: readyA.dataVersion,
  expectedReviewVersion: 0,
  expectedSessionVersion: readyA.session.version,
  evidence: {
    userConfirmedRating: true,
    answerWasHidden: true,
    hintUsed: false,
    answerRevealed: false,
    abandoned: false,
  },
};
const reviewPath = `/sessions/${sessionA.session.id}/reviews`;
const reviewA = await clientA.json(
  await clientA.mutate(reviewPath, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `review-a-${suffix}`,
    },
    body: JSON.stringify(reviewBody),
  }),
  200,
);
const duplicateA = await clientA.json(
  await clientA.mutate(reviewPath, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `review-a-${suffix}`,
    },
    body: JSON.stringify(reviewBody),
  }),
  200,
);
assert.deepEqual(duplicateA, reviewA);

const readyB = await clientB.json(
  await clientB.fetch(`/sessions/${sessionB.session.id}`),
  200,
);
const staleReview = await clientB.mutate(`/sessions/${sessionB.session.id}/reviews`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Idempotency-Key': `review-b-${suffix}`,
  },
  body: JSON.stringify({
    ...reviewBody,
    expectedDataVersion: readyB.dataVersion,
    expectedSessionVersion: readyB.session.version,
    expectedReviewVersion: 0,
  }),
});
const staleError = await clientB.json(staleReview, 409);
assert.equal(staleError.code, 'REVIEW_VERSION_CONFLICT');

const backupA = await clientA.json(await clientA.fetch('/backup/export'), 200);
const backupB = await clientB.json(await clientB.fetch('/backup/export'), 200);
const stateA = backupA.tables.reviewStates[0];
const stateB = backupB.tables.reviewStates[0];
assert.equal(stateA.version, 1);
assert.equal(stateB.version, 1);
assert.equal(stateA.dueAt, stateB.dueAt);
assert.equal(backupA.tables.reviewLogs.length, 1);
assert.equal(backupB.tables.reviewLogs.length, 1);

console.log(JSON.stringify({
  user: registeredUser.username,
  clients: 2,
  queueNewAvailable: planA.newAvailableCount,
  previewRatings: Object.keys(preview.previews).sort(),
  idempotentSubmission: duplicateA.log.submissionId,
  staleVersionRejected: staleError.code,
  sharedDueAt: stateA.dueAt,
  reviewLogs: backupB.tables.reviewLogs.length,
}));
