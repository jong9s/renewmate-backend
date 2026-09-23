import http from "k6/http";
import { check } from "k6";

const baseUrl = __ENV.BASE_URL || "http://127.0.0.1:18081";
const token = __ENV.ACCESS_TOKEN;

if (!token) {
  throw new Error("ACCESS_TOKEN is required");
}

export const options = {
  discardResponseBodies: true,
  scenarios: {
    subscriptions: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || "30s",
      gracefulStop: "5s",
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/subscriptions`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { endpoint: "GET /api/subscriptions" },
  });

  check(response, {
    "status is 200": (res) => res.status === 200,
  });
}
