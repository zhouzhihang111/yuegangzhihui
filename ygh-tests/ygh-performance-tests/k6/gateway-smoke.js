import http from "k6/http";
import { check } from "k6";
import { Rate, Trend } from "k6/metrics";

const errors = new Rate("ygh_errors");
const latency = new Trend("ygh_gateway_latency", true);

export const options = {
  scenarios: { smoke: { executor: "constant-vus", vus: 5, duration: "30s" } },
  thresholds: {
    ygh_errors: ["rate<0.01"],
    ygh_gateway_latency: ["p(95)<500"],
  },
};

export default function () {
  const base = __ENV.YGH_GATEWAY_URL || "http://127.0.0.1:8080";
  const response = http.get(`${base}/actuator/health/readiness`);
  const ok = check(response, { "gateway is ready": (result) => result.status === 200 });
  errors.add(!ok);
  latency.add(response.timings.duration);
}
