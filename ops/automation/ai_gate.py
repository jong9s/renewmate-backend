"""Preflight only: no OpenAI requests, writes, commits, pushes or PRs."""

import json
import os
import sys
from datetime import datetime, timezone
from urllib.request import Request, urlopen


def allowed(runs, now, current_id):
    """Conservatively count all dispatches, including failed/skipped preflights."""
    daily = monthly = 0
    for run in runs:
        if str(run["id"]) == str(current_id):
            continue
        created = datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
        if (created.year, created.month) == (now.year, now.month):
            monthly += max(1, int(run.get("run_attempt", 1)))
            if created.date() == now.date():
                daily += max(1, int(run.get("run_attempt", 1)))
    return daily < 1 and monthly < 3


def main():
    if os.environ.get("AI_AUTOFIX_ENABLED") != "true":
        raise ValueError("AI disabled (default)")
    if os.environ.get("GITHUB_RUN_ATTEMPT", "1") != "1":
        raise ValueError("Reruns are blocked")
    now = datetime.now(timezone.utc)
    month = now.strftime("%Y-%m-01")
    repo = os.environ["GITHUB_REPOSITORY"]
    # Fail closed instead of using incomplete API results.
    url = f"https://api.github.com/repos/{repo}/actions/workflows/ai-preflight.yml/runs?per_page=100&created=%3E%3D{month}"
    req = Request(url, headers={
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urlopen(req, timeout=10) as response:
        data = json.load(response)
    if data["total_count"] > 100 or len(data["workflow_runs"]) != data["total_count"]:
        raise ValueError("Incomplete run history; refusing to proceed")
    if not any(str(run["id"]) == os.environ["GITHUB_RUN_ID"] for run in data["workflow_runs"]):
        raise ValueError("Current run missing from history; refusing to proceed")
    if not allowed(data["workflow_runs"], now, os.environ["GITHUB_RUN_ID"]):
        raise ValueError("Daily (1) or monthly (3) run limit reached")
    print("Preflight passed. Paid AI and PR creation are NOT connected.")


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("AI preflight blocked: disabled, rerun, limit, or unverifiable API result.", file=sys.stderr)
        sys.exit(1)
