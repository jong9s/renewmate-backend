import json
import io
import os
import unittest
from datetime import datetime, timezone
from unittest.mock import Mock, patch
from urllib.error import URLError

import ai_gate
import check_ec2_logs
import apply_autofix_patch
import notify
import prepare_incident
import scan_container_logs
import sanitize_logs


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

    def test_sanitized_log_summary(self):
        encoded = __import__("base64").b64encode(json.dumps({
            "scanned_lines": 120,
            "error_count": 2,
            "exception_types": [{"name": "IllegalStateException", "count": 1}],
        }).encode()).decode()
        summary = notify.log_summary(encoded)
        self.assertIn("2 ERROR", summary)
        self.assertIn("IllegalStateException", summary)

    def test_log_summary_rejects_raw_or_empty_content(self):
        for encoded in ["", "not-base64", __import__("base64").b64encode(b'{"raw":"password=secret"}').decode()]:
            with self.subTest(encoded=encoded), self.assertRaises(ValueError):
                notify.log_summary(encoded)

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

    def test_ec2_log_event_notifies_without_raw_logs(self):
        encoded = __import__("base64").b64encode(json.dumps({
            "scanned_lines": 10, "error_count": 1, "exception_types": []
        }).encode()).decode()
        send = self.invoke({}, "workflow_dispatch", MONITOR_EVENT="ec2_logs", LOG_SUMMARY_B64=encoded)
        message = json.dumps(send.call_args.args[0])
        self.assertIn("1 ERROR", message)
        self.assertNotIn("password", message)


class ContainerLogScanTests(unittest.TestCase):
    def test_summarizes_counts_without_returning_log_text(self):
        lines = [
            "INFO started user@example.com\n",
            "2026 ERROR failed password=secret java.lang.IllegalStateException: bad\n",
            "Caused by: java.sql.SQLException: hidden detail\n",
        ]
        result = scan_container_logs.summarize(lines)
        self.assertEqual(result["scanned_lines"], 3)
        self.assertEqual(result["error_count"], 1)
        self.assertEqual(result["exception_types"][0], {"name": "IllegalStateException", "count": 1})
        self.assertNotIn("secret", json.dumps(result))
        self.assertNotIn("email", json.dumps(result))

    def test_validates_remote_summary_shape(self):
        summary = {"scanned_lines": 1, "error_count": 0, "exception_types": []}
        self.assertEqual(check_ec2_logs.validate_summary(summary), summary)
        for invalid in [{}, {**summary, "raw": "secret"}, {**summary, "error_count": -1}]:
            with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                check_ec2_logs.validate_summary(invalid)

    def test_ssm_scan_returns_only_validated_summary(self):
        summary = {"scanned_lines": 25, "error_count": 1,
                   "exception_types": [{"name": "RuntimeException", "count": 1}]}
        responses = [
            json.dumps({"Command": {"CommandId": "command-1"}}),
            json.dumps({"Status": "Pending"}),
            json.dumps({"Status": "Success", "StandardOutputContent": json.dumps(summary)}),
        ]
        aws_call = Mock(side_effect=responses)
        result = check_ec2_logs.scan("i-07a88d17c5a0e1a4d", aws_call, Mock())
        self.assertEqual(result, summary)
        self.assertEqual(aws_call.call_count, 3)

    def test_ssm_failure_does_not_return_stderr(self):
        responses = [
            json.dumps({"Command": {"CommandId": "command-1"}}),
            json.dumps({"Status": "Failed", "StandardErrorContent": "password=secret"}),
        ]
        with self.assertRaises(RuntimeError) as error:
            check_ec2_logs.scan("i-07a88d17c5a0e1a4d", Mock(side_effect=responses), Mock())
        self.assertNotIn("secret", str(error.exception))


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

    def test_invalid_workflow_name_blocks_before_network(self):
        env = {"AI_AUTOFIX_ENABLED": "true", "GITHUB_RUN_ID": "99",
               "GITHUB_REPOSITORY": "test/repo", "GITHUB_TOKEN": "fake",
               "AI_WORKFLOW_FILE": "../unsafe.yml"}
        with patch.dict(os.environ, env, clear=True), patch.object(ai_gate, "urlopen") as fetch:
            with self.assertRaises(ValueError):
                ai_gate.main()
            fetch.assert_not_called()


class IncidentPreparationTests(unittest.TestCase):
    def valid_run(self):
        return {
            "id": 123, "name": "Backend CI", "status": "completed",
            "conclusion": "failure", "event": "pull_request",
            "head_repository": {"full_name": "jong9s/renewmate-backend"},
            "head_sha": "a" * 40, "head_branch": "codex/fixture",
        }

    def test_accepts_failed_run_from_same_repository(self):
        values = prepare_incident.validate_run(
            self.valid_run(), "jong9s/renewmate-backend", "123"
        )
        self.assertEqual(values["head_sha"], "a" * 40)

    def test_rejects_success_fork_and_arbitrary_branch(self):
        mutations = [
            {"conclusion": "success"},
            {"head_repository": {"full_name": "attacker/fork"}},
            {"head_branch": "feature/untrusted"},
        ]
        for mutation in mutations:
            run = {**self.valid_run(), **mutation}
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                prepare_incident.validate_run(run, "jong9s/renewmate-backend", "123")


class LogSanitizerTests(unittest.TestCase):
    def test_redacts_common_credentials_and_email(self):
        raw = ("user@example.com password=hunter2 token=abc123456789 "
               "Bearer abcdefghijklmnop eyJabcdefgh.ijklmnop.qrstuvwxyz")
        value = sanitize_logs.sanitize(raw)
        for secret in ("user@example.com", "hunter2", "abc123456789", "abcdefghijklmnop", "eyJabcdefgh"):
            self.assertNotIn(secret, value)
        self.assertIn("UNTRUSTED CI LOG DATA", value)

    def test_bounds_output(self):
        value = sanitize_logs.sanitize("x" * (sanitize_logs.MAX_OUTPUT_CHARS + 100))
        self.assertLessEqual(len(value), sanitize_logs.MAX_OUTPUT_CHARS)
        self.assertIn("truncated", value)


class AutofixPatchTests(unittest.TestCase):
    def valid_proposal(self, path="src/main/java/com/renewmate/Test.java"):
        return (
            "```diff\n"
            f"diff --git a/{path} b/{path}\n"
            "--- a/src/main/java/com/renewmate/Test.java\n"
            "+++ b/src/main/java/com/renewmate/Test.java\n"
            "@@ -1 +1 @@\n"
            "-old\n"
            "+new\n"
            "```"
        )

    def test_accepts_java_source_patch(self):
        patch_text = apply_autofix_patch.extract_patch(self.valid_proposal())
        self.assertIn("diff --git", patch_text)

    def test_rejects_prose_config_traversal_and_binary(self):
        invalid = [
            "Here is a fix\n" + self.valid_proposal(),
            self.valid_proposal(".github/workflows/backend-ci.yml"),
            self.valid_proposal("src/main/java/../../application.properties"),
            "```diff\nGIT binary patch\n```",
        ]
        for proposal in invalid:
            with self.subTest(proposal=proposal[:30]), self.assertRaises(ValueError):
                apply_autofix_patch.extract_patch(proposal)


if __name__ == "__main__":
    unittest.main()
