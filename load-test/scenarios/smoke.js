import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL?.replace(/\/$/, '');
if (!baseUrl) throw new Error('BASE_URL을 지정하세요.');

export const options = {
  vus: 1,
  iterations: 3,
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/actuator/health`);

  check(response, {
    'health is UP': (r) =>
      r.status === 200 && r.json().status === 'UP',
  });
}