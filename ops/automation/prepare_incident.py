"""Validate a user-selected failed CI run before any paid AI call."""

import json
import os
import re
import sys
from pathlib import Path
from urllib.request import Request, urlopen


SHA = re.compile(r"^[0-9a-f]{40}$")


def validate_run(run, repository, requested_id):
    if str(run.get("id")) != str(requested_id) or not str(requested_id).isdigit():
        raise ValueError("Run id mismatch")
    if run.get("name") != "Backend CI" or run.get("status") != "completed":
        raise ValueError("Only completed Backend CI runs are allowed")
    if run.get("conclusion") not in ("failure", "timed_out"):
        raise ValueError("The selected CI run did not fail")
    if run.get("event") not in ("push", "pull_request"):
        raise ValueError("Unsupported CI event")
    head_repository = run.get("head_repository") or {}
    if head_repository.get("full_name") != repository:
        raise ValueError("Forked or unknown repository runs are blocked")
    sha = run.get("head_sha")
    branch = run.get("head_branch")
    if not isinstance(sha, str) or not SHA.fullmatch(sha):
        raise ValueError("Invalid head SHA")
    if not isinstance(branch, str) or (branch != "develop" and not branch.startswith("codex/")):
        raise ValueError("Only develop or codex/* runs are allowed")
    return {"failed_run_id": str(requested_id), "head_sha": sha, "head_branch": branch}


def fetch_run(repository, run_id, token):
    request = Request(
        f"https://api.github.com/repos/{repository}/actions/runs/{run_id}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urlopen(request, timeout=10) as response:
        return json.load(response)


def write_outputs(values, path):
    with Path(path).open("a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


if __name__ == "__main__":
    try:
        run_id = os.environ.get("FAILED_RUN_ID", "")
        repository = os.environ["GITHUB_REPOSITORY"]
        run = fetch_run(repository, run_id, os.environ["GITHUB_TOKEN"])
        write_outputs(validate_run(run, repository, run_id), os.environ["GITHUB_OUTPUT"])
        print("Selected failed CI run is eligible for manual AI analysis")
    except Exception:
        print("Incident validation failed; AI execution blocked.", file=sys.stderr)
        sys.exit(1)
