"""Dependency-free notifier. Never forwards raw logs or response bodies."""

import json
import base64
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(url, data=None):
    req = Request(url, data=data, headers={"Content-Type": "application/json"})
    with build_opener(NoRedirect()).open(req, timeout=8) as response:
        return response.status, response.read(16385)


def health(url, fetch=request, sleep=time.sleep):
    parsed = urlsplit(url)
    if (parsed.scheme not in ("http", "https") or not parsed.hostname
            or parsed.username or parsed.password or parsed.query or parsed.fragment):
        raise ValueError("HEALTHCHECK_URL must be an approved HTTP(S) URL without credentials/query")
    reason = "unreachable"
    for attempt in range(3):
        try:
            status, body = fetch(url)
            data = json.loads(body)
            if status == 200 and isinstance(data, dict) and data.get("status") == "UP":
                return True, "UP"
            reason = "health response is not UP"
        except (URLError, OSError, ValueError):
            reason = "timeout, connection failure, HTTP error or invalid JSON"
        if attempt < 2:
            sleep(5)
    return False, reason


def payload(summary, timestamp, link):
    # Plain text prevents untrusted metadata from triggering Slack mentions.
    text = f"RenewMate backend | {timestamp}\n{summary}"
    return {
        "text": "RenewMate backend notification",
        "blocks": [
            {"type": "section", "text": {"type": "plain_text", "text": text}},
            {"type": "section", "text": {"type": "plain_text", "text": f"Logs / run: {link}"}},
        ],
        "unfurl_links": False,
        "unfurl_media": False,
    }


def log_summary(encoded):
    try:
        data = json.loads(base64.b64decode(encoded, validate=True))
        errors = data["error_count"]
        scanned = data["scanned_lines"]
        if not isinstance(errors, int) or errors < 1 or not isinstance(scanned, int):
            raise ValueError("Invalid log summary")
        types = []
        for item in data.get("exception_types", [])[:5]:
            name = item["name"]
            count = item["count"]
            if (not isinstance(name, str)
                    or not re.fullmatch(r"[A-Za-z_$][\w$]*(?:Exception|Error)", name)
                    or not isinstance(count, int)):
                raise ValueError("Invalid exception summary")
            types.append(f"{name} ({count})")
        suffix = f" Types: {', '.join(types)}." if types else ""
        return f"EC2 Spring log scan found {errors} ERROR entries in {scanned} lines.{suffix}"
    except (ValueError, KeyError, TypeError, json.JSONDecodeError):
        raise ValueError("Invalid EC2 log summary") from None


def send(message, webhook, fetch=request):
    url = urlsplit(webhook)
    if (url.scheme != "https" or url.netloc != "hooks.slack.com"
            or not url.path.startswith("/services/") or url.query or url.fragment):
        raise ValueError("SLACK_WEBHOOK_URL is missing or invalid")
    try:
        status, body = fetch(webhook, json.dumps(message).encode("utf-8"))
        if status != 200 or body.strip() != b"ok":
            raise ValueError("Slack rejected notification")
    except (URLError, OSError):
        # Exception URLs can contain webhook credentials: never print them.
        raise ValueError("Slack delivery failed; check secret and channel access") from None


def main():
    event_name = os.environ.get("GITHUB_EVENT_NAME", "")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    now = datetime.now(timezone.utc).isoformat()
    run_link = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    if os.environ.get("MONITOR_EVENT") == "ec2_logs":
        summary = log_summary(os.environ.get("LOG_SUMMARY_B64", ""))
    elif event_name == "workflow_run":
        run = event["workflow_run"]
        if run.get("conclusion") not in ("failure", "timed_out"):
            return
        if run.get("run_attempt", 1) != 1:
            return
        workflow = run.get("name")
        if workflow not in ("Backend CI", "EC2 Log Alerts"):
            workflow = "GitHub workflow"
        summary = f"{workflow} {run['conclusion']} (run {run['id']}). Open the run for failing job logs."
        now = run.get("updated_at") or now
        run_link = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{run['id']}"
    elif event_name == "workflow_dispatch" and os.environ.get("MONITOR_MODE") == "test":
        summary = "[TEST] Simulated incident. No production failure, AI call or deployment."
    else:
        if os.environ.get("HEALTHCHECK_ENABLED") != "true":
            raise ValueError("Health checks are disabled")
        ok, reason = health(os.environ.get("HEALTHCHECK_URL", ""))
        if ok:
            print("Health UP; no notification")
            return
        summary = f"Health check failed after 3 attempts: {reason}"
    send(payload(summary, now, run_link), os.environ.get("SLACK_WEBHOOK_URL", ""))
    print("Slack notification delivered")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError):
        print("Notification failed. Check configuration, Slack access and workflow logs; secrets omitted.", file=sys.stderr)
        sys.exit(1)
