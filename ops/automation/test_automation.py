import json
import io
import os
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock, patch
from urllib.error import URLError

import ai_gate
import notify


class NotificationTests(unittest.TestCase):
    def test_healthy(self):
        fetch = Mock(return_value=(200, b'{"status":"UP"}'))
        self.assertEqual(notify.health("https://example.com/health", fetch)[0], True)
        fetch.assert_called_once()

    def test_retries_transient_failure(self):
        fetch = Mock(side_effect=[URLError("offline"), (200, b'{"status":"UP"}')])
        sleep = Mock()
        self.assertTrue(notify.health("http://example.com/health", fetch, sleep)[0])
        sleep.assert_called_once_with(5)

    def test_bad_responses_are_not_healthy(self):
        for result in [(200, b'[]'), (200, b'{}'), (200, b'html'),
                       (200, b'{"status":"DOWN"}'), (503, b'{"status":"UP"}')]:
            with self.subTest(result=result):
                fetch = Mock(return_value=result)
                self.assertFalse(notify.health("http://example.com/health", fetch, Mock())[0])
                self.assertEqual(fetch.call_count, 3)

    def test_transport_error_does_not_leak_body(self):
        fetch = Mock(side_effect=URLError("password=secret"))
        _, reason = notify.health("http://example.com/health", fetch, Mock())
        self.assertNotIn("secret", reason)

    def test_rejects_unsafe_health_url(self):
        for url in ["", "file:///etc/passwd", "https://user:pass@example.com", "http://example.com?token=secret"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                notify.health(url, Mock())

    def test_slack_success(self):
        fetch = Mock(return_value=(200, b"ok"))
        notify.send({"text": "test"}, "https://hooks.slack.com/services/TEST", fetch)
        self.assertEqual(json.loads(fetch.call_args.args[1]), {"text": "test"})

    def test_slack_missing_or_wrong_host(self):
        for url in ["", "http://hooks.slack.com/services/x", "https://hooks.slack.com.evil.test/services/x"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                notify.send({}, url, Mock())

    def test_slack_rejection_fails(self):
        with self.assertRaises(ValueError):
            notify.send({}, "https://hooks.slack.com/services/TEST", Mock(return_value=(200, b"invalid_payload")))

    def test_slack_transport_error_hides_secret(self):
        with self.assertRaises(ValueError) as result:
            notify.send({}, "https://hooks.slack.com/services/TEST", Mock(side_effect=URLError("SECRET")))
        self.assertNotIn("SECRET", str(result.exception))

    def test_payload_uses_plain_text(self):
        data = notify.payload("<!channel>", "2026-09-21", "https://github.com/example")
        self.assertEqual(data["blocks"][0]["text"]["type"], "plain_text")
        self.assertFalse(data["unfurl_links"])

    def invoke(self, event, event_name="workflow_run", **extra):
        env = {"GITHUB_EVENT_NAME": event_name, "GITHUB_EVENT_PATH": "unused",
               "GITHUB_REPOSITORY": "jong9s/renewmate-backend", "GITHUB_RUN_ID": "99", **extra}
        with patch.dict(os.environ, env, clear=True), patch.object(notify.Path, "read_text", return_value=json.dumps(event)), patch.object(notify, "send") as send:
            notify.main()
            return send

    def test_ci_failure_uses_run_link_not_raw_logs(self):
        send = self.invoke({"workflow_run": {"id": 42, "conclusion": "failure", "updated_at": "2026-09-21T00:00:00Z", "logs": "secret"}})
        message = json.dumps(send.call_args.args[0])
        self.assertIn("/actions/runs/42", message)
        self.assertNotIn("secret", message)

    def test_ci_success_is_silent(self):
        self.invoke({"workflow_run": {"conclusion": "success"}}).assert_not_called()

    def test_ci_rerun_is_silent(self):
        self.invoke({"workflow_run": {"conclusion": "failure", "run_attempt": 2}}).assert_not_called()

    def test_simulated_incident_has_no_health_call(self):
        with patch.object(notify, "health") as health:
            send = self.invoke({}, "workflow_dispatch", MONITOR_MODE="test")
            health.assert_not_called()
            self.assertIn("[TEST]", json.dumps(send.call_args.args[0]))

    def test_schedule_disabled(self):
        with self.assertRaises(ValueError):
            self.invoke({}, "schedule")

    def test_schedule_healthy_is_silent(self):
        with patch.object(notify, "health", return_value=(True, "UP")):
            self.invoke({}, "schedule", HEALTHCHECK_ENABLED="true").assert_not_called()

    def test_schedule_unhealthy_notifies(self):
        with patch.object(notify, "health", return_value=(False, "not UP")):
            self.invoke({}, "schedule", HEALTHCHECK_ENABLED="true").assert_called_once()


class GateTests(unittest.TestCase):
    now = datetime(2026, 9, 21, tzinfo=timezone.utc)

    def run_record(self, day=21, month=9, id=1, attempt=1):
        return {"id": id, "created_at": f"2026-{month:02d}-{day:02d}T00:00:00Z", "run_attempt": attempt}

    def test_first_run_allowed(self):
        self.assertTrue(ai_gate.allowed([], self.now, 99))

    def test_current_run_excluded(self):
        self.assertTrue(ai_gate.allowed([self.run_record(id=99)], self.now, 99))

    def test_daily_limit(self):
        self.assertFalse(ai_gate.allowed([self.run_record()], self.now, 99))

    def test_monthly_limit(self):
        runs = [self.run_record(day=day, id=day) for day in [1, 2, 3]]
        self.assertFalse(ai_gate.allowed(runs, self.now, 99))

    def test_previous_month_excluded(self):
        self.assertTrue(ai_gate.allowed([self.run_record(month=8)], self.now, 99))

    def test_attempts_counted_conservatively(self):
        self.assertFalse(ai_gate.allowed([self.run_record(day=1, attempt=3)], self.now, 99))

    def test_default_off_before_network(self):
        with patch.dict(os.environ, {}, clear=True), patch.object(ai_gate, "urlopen") as fetch:
            with self.assertRaises(ValueError):
                ai_gate.main()
            fetch.assert_not_called()

    def test_rerun_blocked_before_network(self):
        with patch.dict(os.environ, {"AI_AUTOFIX_ENABLED": "true", "GITHUB_RUN_ATTEMPT": "2"}, clear=True), patch.object(ai_gate, "urlopen") as fetch:
            with self.assertRaises(ValueError):
                ai_gate.main()
            fetch.assert_not_called()

    def test_incomplete_history_blocks(self):
        env = {"AI_AUTOFIX_ENABLED": "true", "GITHUB_RUN_ID": "99",
               "GITHUB_REPOSITORY": "test/repo", "GITHUB_TOKEN": "fake"}
        for data in [{"total_count": 101, "workflow_runs": []},
                     {"total_count": 1, "workflow_runs": []},
                     {"total_count": 0, "workflow_runs": []}]:
            with self.subTest(data=data), patch.dict(os.environ, env, clear=True), patch.object(ai_gate, "urlopen", return_value=io.BytesIO(json.dumps(data).encode())):
                with self.assertRaises(ValueError):
                    ai_gate.main()

    def test_history_api_failure_does_not_allow_run(self):
        env = {"AI_AUTOFIX_ENABLED": "true", "GITHUB_RUN_ID": "99",
               "GITHUB_REPOSITORY": "test/repo", "GITHUB_TOKEN": "fake"}
        with patch.dict(os.environ, env, clear=True), patch.object(ai_gate, "urlopen", side_effect=URLError("unavailable")):
            with self.assertRaises(URLError):
                ai_gate.main()


if __name__ == "__main__":
    unittest.main()
