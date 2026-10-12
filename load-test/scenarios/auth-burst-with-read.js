import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

// 구독 조회를 일정한 속도로 보내는 도중 로그인(또는 회원가입) 요청을 한꺼번에 몰아,
// 인증(BCrypt) 부하가 다른 API의 응답 시간에 주는 영향을 측정한다.
// BURST_ACTION=login이면 auth-concurrency.js(PHASE=signup)로 만든 계정을 사용한다.

const baseUrl = __ENV.BASE_URL?.replace(/\/$/, '');
const readEmail = __ENV.TEST_EMAIL;
const password = __ENV.TEST_PASSWORD;
const runId = __ENV.RUN_ID;
const users = Number(__ENV.USERS || 100);
const readRps = Number(__ENV.READ_RPS || 15);
const burstStart = Number(__ENV.BURST_START_SECONDS || 15);
const burstWindow = Number(__ENV.BURST_WINDOW_SECONDS || 8);
const burstAction = __ENV.BURST_ACTION || 'login'; // login | signup

if (!baseUrl || !readEmail || !password || !/^[a-z0-9-]+$/.test(runId || '')) {
  throw new Error('BASE_URL, TEST_EMAIL, TEST_PASSWORD, RUN_ID 값을 확인하세요.');
}

// 조회 응답 시간을 로그인 폭주 구간 안/밖으로 나눠 기록한다
const readOutsideBurst = new Trend('read_outside_burst', true);
const readDuringBurst = new Trend('read_during_burst', true);
const burstLogin = new Trend('burst_login', true);

export const options = {
  scenarios: {
    read: {
      executor: 'constant-arrival-rate',
      exec: 'read',
      rate: readRps,
      timeUnit: '1s',
      duration: `${burstStart + burstWindow + 10}s`,
      preAllocatedVUs: 20,
      maxVUs: 200,
    },
    burst: {
      executor: 'per-vu-iterations',
      exec: 'login',
      vus: users,
      iterations: 1,
      startTime: `${burstStart}s`,
      maxDuration: '1m',
    },
  },
  thresholds: {
    checks: ['rate==1'],
  },
};

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

export function setup() {
  const response = http.post(`${baseUrl}/api/auth/login`,
    JSON.stringify({ email: readEmail, password }), jsonHeaders());
  if (response.status !== 200) throw new Error(`조회용 계정 로그인 실패: HTTP ${response.status}`);
  return { token: response.json('accessToken') };
}

export function read(data) {
  const response = http.get(`${baseUrl}/api/subscriptions`, jsonHeaders(data.token));
  check(response, { 'read 200': (r) => r.status === 200 });

  const elapsedSeconds = exec.instance.currentTestRunDuration / 1000;
  const inBurst = elapsedSeconds >= burstStart && elapsedSeconds < burstStart + burstWindow;
  (inBurst ? readDuringBurst : readOutsideBurst).add(response.timings.duration);
}

export function login() {
  // burst 시나리오 안에서의 반복 순번(0부터)으로 계정 1~USERS를 하나씩 사용
  const index = exec.scenario.iterationInTest + 1;

  if (burstAction === 'signup') {
    // 폭주 중 회원가입: 실행마다 새 계정을 만든다 (RUN_ID-burst-N)
    const email = `loadtest-${runId}-burst-${index}@example.com`;
    const response = http.post(`${baseUrl}/api/auth/signup`,
      JSON.stringify({ name: `Burst ${index}`, email, password, passwordConfirm: password }), jsonHeaders());
    check(response, { 'signup 201': (r) => r.status === 201 });
    burstLogin.add(response.timings.duration);
    return;
  }

  // 폭주 중 로그인: auth-concurrency.js(PHASE=signup)로 만든 계정을 사용
  const email = `loadtest-${runId}-${index}@example.com`;
  const response = http.post(`${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }), jsonHeaders());
  check(response, { 'login 200': (r) => r.status === 200 });
  burstLogin.add(response.timings.duration);
}
