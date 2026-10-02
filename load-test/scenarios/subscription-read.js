import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL?.replace(/\/$/, '');
const email = __ENV.TEST_EMAIL;
const password = __ENV.TEST_PASSWORD;

if (!baseUrl || !email || !password) {
  throw new Error('BASE_URL, TEST_EMAIL, TEST_PASSWORD를 지정하세요.');
}

export const options = {
  scenarios: {
    subscriptionRead: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TARGET_RPS || 3),
      timeUnit: '1s',
      duration: __ENV.TEST_DURATION || '1m',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (response.status !== 200) {
    throw new Error(`로그인 실패: HTTP ${response.status}`);
  }

  const token = response.json().accessToken;
  if (!token) throw new Error('로그인 응답에 accessToken이 없습니다.');

  return { token };
}

export default function (data) {
  const response = http.get(`${baseUrl}/api/subscriptions`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });

  let subscriptions = null;
  try {
    subscriptions = response.json();
  } catch (_) {
    // 아래 check에서 실패로 기록
  }

  check(response, {
    '구독 조회 200': (r) => r.status === 200,
    '구독 10개 조회': () =>
      Array.isArray(subscriptions) && subscriptions.length === 10,
  });
}