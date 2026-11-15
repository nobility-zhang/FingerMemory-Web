import assert from 'node:assert/strict';

const baseUrl = (process.env.FM_BASE_URL ?? 'http://127.0.0.1:5173/api/v1')
  .replace(/\/$/, '');
const suffix = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
const username = `p3-ui-${suffix}`;
const password = 'browser fixture battery';
const cookies = new Map();
let csrf;

async function request(path, init = {}) {
  const headers = new Headers(init.headers);
  if (cookies.size > 0) {
    headers.set(
      'Cookie',
      [...cookies].map(([name, value]) => `${name}=${value}`).join('; '),
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
      cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
  }
  return response;
}

async function getCsrf() {
  const response = await request('/auth/csrf');
  assert.equal(response.status, 200);
  csrf = await response.json();
}

async function mutate(path, init) {
  if (csrf === undefined) {
    await getCsrf();
  }
  return request(path, {
    ...init,
    headers: {
      ...(init.headers ?? {}),
      [csrf.headerName]: csrf.token,
    },
  });
}

await getCsrf();
const registration = await mutate('/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username,
    password,
    displayName: 'P3 Browser Fixture',
  }),
});
assert.equal(registration.status, 201);

csrf = undefined;
await getCsrf();
const login = await mutate('/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ username, password }),
});
assert.equal(login.status, 200);
csrf = undefined;
await getCsrf();

const lexiconResponse = await mutate('/lexicons', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    name: `P3 大厅词库 ${suffix}`,
    description: '隔离浏览器验收夹具',
  }),
});
assert.equal(lexiconResponse.status, 201);
const lexicon = await lexiconResponse.json();

const wordResponse = await mutate(`/lexicons/${lexicon.id}/words`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    term: 'memory',
    meaning: '记忆',
    acceptedAnswers: ['memory'],
    hints: ['以 m 开头'],
  }),
});
assert.equal(wordResponse.status, 201);

const sessionResponse = await mutate('/sessions', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    modeId: 'keyboard-recall',
    timeZone: 'Asia/Shanghai',
    newLimit: 10,
    reviewLimit: 50,
    reduceMotion: false,
  }),
});
assert.equal(sessionResponse.status, 201);
const session = await sessionResponse.json();

console.log(JSON.stringify({
  username,
  sessionId: session.session.id,
  queuedItems: session.session.queue.length,
  term: 'memory',
  meaning: '记忆',
}));
