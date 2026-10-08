"""Build an incident brief from a GitHub event and decide whether the AI auto-fix may run.

Inputs come from repository_dispatch (production 5xx reported by the app), workflow_call
from backend-ci.yml (CI failure) or workflow_dispatch (manual Slack test). Every field is treated as
untrusted: values are validated, masked and length-limited before they reach Slack, the
AI prompt or a shell step. Dependency-free so it runs on a bare GitHub runner.
"""

import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

FINGERPRINT = re.compile(r"^[0-9a-f]{12}$")
BRANCH = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._/-]{0,99}$")
SHA = re.compile(r"^[0-9a-f]{40}$")
FIX_BRANCH_PREFIX = "ai-fix/"
MAX_LOG_LINES = 300

SECRET_PATTERNS = [
    (re.compile(r"(?i)(password|secret|token|authorization|api[_-]?key)\s*[=:]\s*\S+"), r"\1=<masked>"),
    (re.compile(r"(?i)bearer\s+[A-Za-z0-9._\-]+"), "Bearer <masked>"),
    (re.compile(r"gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}"), "<masked>"),
    (re.compile(r"https://hooks\.slack\.com/\S+"), "<masked>"),
    (re.compile(r"[\w.+-]+@[\w-]+(\.[\w-]+)+"), "<email>"),
]


def sanitize(text, limit, suffix="\n...(truncated)"):
    value = str(text or "")
    for pattern, replacement in SECRET_PATTERNS:
        value = pattern.sub(replacement, value)
    value = value.replace("\x00", "")
    return value if len(value) <= limit else value[:limit] + suffix


def one_line(text, limit=200):
    # GitHub step output으로 쓰이므로 줄바꿈 없는 한 줄을 보장한다.
    return sanitize(" ".join(str(text or "").split()), limit, "...")


def gh(args):
    result = subprocess.run(["gh", *args], capture_output=True, text=True, timeout=60, check=False)
    if result.returncode != 0:
        raise RuntimeError(f"gh {args[0]} {args[1] if len(args) > 1 else ''} failed")
    return result.stdout


def production_incident(event, base_branch):
    payload = event.get("client_payload") or {}
    fingerprint = str(payload.get("fingerprint", ""))
    if not FINGERPRINT.fullmatch(fingerprint):
        raise ValueError("Invalid incident fingerprint")

    exception_type = one_line(payload.get("exceptionType"), 150)
    location = one_line(payload.get("location"), 250)
    method = one_line(payload.get("method"), 10)
    path = one_line(payload.get("path"), 200)
    message = one_line(payload.get("message"), 300)
    stack = sanitize(payload.get("stackTrace"), 4000)

    brief = f"""# Production incident (5xx)

- Exception: {exception_type}
- First application frame: {location}
- Request: {method} {path}
- Occurred at: {one_line(payload.get("occurredAt"), 40)}
- Fingerprint: {fingerprint}

## Message (masked)
```
{message}
```

## Stack trace (top frames)
```
{stack}
```
"""
    return {
        "kind": "production",
        "incident_id": f"prod-{fingerprint}",
        "title": one_line(f"{exception_type.rsplit('.', 1)[-1]} at {method} {path}", 120),
        "summary": one_line(f"{exception_type} at {location} ({method} {path}): {message}", 300),
        "base_branch": base_branch,
        "checkout_ref": base_branch,
        "brief": brief,
        "fixable": True,
        "skip_reason": "",
    }


def failed_job_logs(repository, run_id):
    """Fetch logs of failed jobs. The run is still in progress (this workflow is called
    from it), so `gh run view --log-failed` is unavailable; per-job logs are."""
    jobs = json.loads(gh(["api", f"repos/{repository}/actions/runs/{run_id}/jobs?per_page=50"]))
    logs = []
    for job in jobs.get("jobs", []):
        if job.get("conclusion") != "failure":
            continue
        try:
            text = gh(["api", f"repos/{repository}/actions/jobs/{job['id']}/logs"])
        except (RuntimeError, subprocess.SubprocessError):
            text = "(log could not be fetched)"
        logs.append(f"===== job: {one_line(job.get('name'), 80)} =====\n{text}")
    return "\n".join(logs) or "(no failed job found)"


def ci_incident(event, repository, run_id):
    """CI failure reported by backend-ci.yml through workflow_call."""
    head_branch = os.environ.get("CI_BRANCH", "")
    head_sha = os.environ.get("CI_SHA", "")
    if not run_id.isdigit() or not BRANCH.fullmatch(head_branch) or not SHA.fullmatch(head_sha):
        raise ValueError("Invalid CI metadata")

    fixable, skip_reason = True, ""
    if head_branch.startswith(FIX_BRANCH_PREFIX):
        fixable, skip_reason = False, "AI 수정 브랜치의 실패라 재귀 실행하지 않음"

    try:
        raw_log = failed_job_logs(repository, run_id)
    except (RuntimeError, subprocess.SubprocessError, json.JSONDecodeError):
        raw_log = "(failed job log could not be fetched)"
    tail = "\n".join(raw_log.splitlines()[-MAX_LOG_LINES:])
    commit_message = one_line((event.get("head_commit") or {}).get("message")
                              or (event.get("pull_request") or {}).get("title"), 200)

    brief = f"""# Backend CI failure

- Workflow run: {run_id} (event: {one_line(os.environ.get("GITHUB_EVENT_NAME"), 30)})
- Branch: {head_branch}
- Commit: {head_sha}
- Commit message: {commit_message}

## Failed job log (last {MAX_LOG_LINES} lines, masked)
```
{sanitize(tail, 30000)}
```
"""
    return {
        "kind": "ci",
        "incident_id": f"ci-{run_id}",
        "title": one_line(f"Backend CI failed on {head_branch}", 120),
        "summary": one_line(f"Backend CI failed on {head_branch} ({head_sha[:7]}), run {run_id}", 300),
        "base_branch": head_branch,
        "checkout_ref": head_sha,
        "brief": brief,
        "fixable": fixable,
        "skip_reason": skip_reason,
    }


def test_incident(run_id):
    return {
        "kind": "test",
        "incident_id": f"test-{run_id}",
        "title": "Slack notification test",
        "summary": "[TEST] 수동 실행한 알림 테스트입니다. 실제 장애가 아니며 AI 수정은 실행하지 않습니다.",
        "base_branch": "",
        "checkout_ref": "",
        "brief": "",
        "fixable": False,
        "skip_reason": "테스트 이벤트",
    }


def autofix_gate(incident, repository):
    """Returns (should_fix, reason). Dedup by open PR and enforce a daily PR budget."""
    if not incident["fixable"]:
        return False, incident["skip_reason"]
    if os.environ.get("AI_AUTOFIX_ENABLED") != "true":
        return False, "AI_AUTOFIX_ENABLED 변수가 true가 아님"

    branch = FIX_BRANCH_PREFIX + incident["incident_id"]
    open_prs = json.loads(gh(["pr", "list", "--repo", repository, "--head", branch,
                              "--state", "open", "--json", "url"]) or "[]")
    if open_prs:
        return False, f"이미 열린 AI 수정 PR이 있음: {open_prs[0]['url']}"

    limit = int(os.environ.get("AI_AUTOFIX_DAILY_LIMIT") or "3")
    today = datetime.now(timezone.utc).date().isoformat()
    recent = json.loads(gh(["pr", "list", "--repo", repository, "--state", "all", "--limit", "50",
                            "--json", "headRefName,createdAt"]) or "[]")
    created_today = sum(1 for pr in recent
                        if pr["headRefName"].startswith(FIX_BRANCH_PREFIX)
                        and pr["createdAt"].startswith(today))
    if created_today >= limit:
        return False, f"오늘 AI 수정 PR 한도({limit}개) 도달"
    return True, ""


def write_outputs(values, path):
    with Path(path).open("a", encoding="utf-8") as output:
        for key, value in values.items():
            value = str(value)
            if "\n" in value:
                raise ValueError(f"Output {key} must be single-line")
            output.write(f"{key}={value}\n")


def main():
    event_name = os.environ["GITHUB_EVENT_NAME"]
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    repository = os.environ["GITHUB_REPOSITORY"]
    base_branch = os.environ.get("AI_FIX_BASE_BRANCH") or "develop"
    if not BRANCH.fullmatch(base_branch):
        raise ValueError("Invalid AI_FIX_BASE_BRANCH")

    # workflow_call에서는 event_name이 호출한 CI의 이벤트(push/pull_request)이므로 입력값으로 구분한다.
    if os.environ.get("INCIDENT_SOURCE") == "ci":
        incident = ci_incident(event, repository, os.environ["GITHUB_RUN_ID"])
    elif event_name == "repository_dispatch":
        incident = production_incident(event, base_branch)
    elif event_name == "workflow_dispatch":
        incident = test_incident(os.environ["GITHUB_RUN_ID"])
    else:
        raise ValueError(f"Unsupported event: {event_name}")

    should_fix, reason = autofix_gate(incident, repository)

    brief_path = Path(sys.argv[1])
    brief_path.parent.mkdir(parents=True, exist_ok=True)
    brief_path.write_text(incident["brief"], encoding="utf-8")

    write_outputs({
        "kind": incident["kind"],
        "incident_id": incident["incident_id"],
        "title": incident["title"],
        "summary": incident["summary"],
        "base_branch": incident["base_branch"],
        "checkout_ref": incident["checkout_ref"],
        "should_fix": "true" if should_fix else "false",
        "skip_reason": one_line(reason, 200),
    }, os.environ["GITHUB_OUTPUT"])
    print(f"{incident['incident_id']}: should_fix={should_fix} {reason}")


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError, RuntimeError, OSError, json.JSONDecodeError) as error:
        print(f"Incident preparation failed: {type(error).__name__}: {error}", file=sys.stderr)
        sys.exit(1)
