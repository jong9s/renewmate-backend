import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL?.replace(/\/$/, '');
const password = __ENV.TEST_PASSWORD;
const runId = __ENV.RUN_ID;
const phase = __ENV.PHASE;
const users = Number(__ENV.USERS || 10);

if (!baseUrl || !password || !/^[a-z0-9-]+$/.test(runId || '') ||
    !['signup', 'login', 'cleanup'].includes(phase) ||
    !Number.isInteger(users) || users < 1 || users > 100) {
  throw new Error('BASE_URL, TEST_PASSWORD, RUN_ID, PHASE, USERS 값을 확인하세요.');
}

const failures = new Counter('auth_failures');
const authDuration = new Trend('auth_duration', true);

export const options = {
  scenarios: {
    authBurst: {
      executor: 'per-vu-iterations',
      vus: users,
      iterations: 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate==0'],
  },
};

function emailForVu() {
  return `loadtest-${runId}-${__VU}@example.com`;
}

function login(email) {
  return http.post(`${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' }, tags: { phase: 'login' } });
}

export default function () {
  const email = emailForVu();
  let response;
  let success;

  if (phase === 'signup') {
    response = http.post(`${baseUrl}/api/auth/signup`,
      JSON.stringify({ name: `Load Test ${__VU}`, email, password, passwordConfirm: password }),
      { headers: { 'Content-Type': 'application/json' }, tags: { phase } });
    success = check(response, { 'signup 201': (r) => r.status === 201 });
    authDuration.add(response.timings.duration);
  } else {
    response = login(email);
    success = check(response, { 'login 200 + token': (r) => r.status === 200 && !!r.json('accessToken') });
    authDuration.add(response.timings.duration);

    if (phase === 'cleanup' && success) {
      const token = response.json('accessToken');
      const deleted = http.del(`${baseUrl}/api/users/me`,
        JSON.stringify({ currentPassword: password }),
        { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, tags: { phase: 'cleanup' } });
      success = check(deleted, { 'withdraw 204': (r) => r.status === 204 }) && success;
    }
  }

  if (!success) {
    failures.add(1);
    console.error(`${phase} failed: VU=${__VU}, status=${response.status}`);
  }
}
