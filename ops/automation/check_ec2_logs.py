"""Run a bounded Docker log scan through SSM without exposing raw logs."""

import base64
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path


INSTANCE_ID = re.compile(r"^i-[0-9a-f]{8,17}$")
SUMMARY_KEYS = {"scanned_lines", "error_count", "exception_types"}


def aws(args):
    result = subprocess.run(
        ["aws", *args], capture_output=True, text=True, timeout=30, check=False
    )
    if result.returncode != 0:
        raise RuntimeError("AWS command failed; inspect IAM/SSM configuration")
    return result.stdout


def validate_summary(value):
    if not isinstance(value, dict) or set(value) != SUMMARY_KEYS:
        raise ValueError("Invalid scanner summary")
    if not isinstance(value["scanned_lines"], int) or value["scanned_lines"] < 0:
        raise ValueError("Invalid scanned line count")
    if not isinstance(value["error_count"], int) or value["error_count"] < 0:
        raise ValueError("Invalid error count")
    if not isinstance(value["exception_types"], list) or len(value["exception_types"]) > 5:
        raise ValueError("Invalid exception type list")
    for item in value["exception_types"]:
        if (not isinstance(item, dict) or set(item) != {"name", "count"}
                or not re.fullmatch(r"[A-Za-z_$][\w$]*(?:Exception|Error)", str(item["name"]))
                or not isinstance(item["count"], int) or item["count"] < 1):
            raise ValueError("Invalid exception type")
    return value


def scan(instance_id, aws_call=aws, sleep=time.sleep):
    if not INSTANCE_ID.fullmatch(instance_id):
        raise ValueError("Invalid EC2 instance id")
    script = """set -eu
umask 077
LOG_FILE=$(mktemp)
trap 'rm -f "$LOG_FILE"' EXIT
if ! docker logs --since 6h --tail 5000 renewmate-backend >"$LOG_FILE" 2>&1; then
  exit 1
fi
python3 -B /home/ubuntu/renewmate-backend/ops/automation/scan_container_logs.py <"$LOG_FILE"
"""
    request = json.dumps({"commands": [script]}, separators=(",", ":"))
    sent = json.loads(aws_call([
        "ssm", "send-command", "--instance-ids", instance_id,
        "--document-name", "AWS-RunShellScript", "--timeout-seconds", "120",
        "--parameters", request, "--output", "json",
    ]))
    command_id = sent.get("Command", {}).get("CommandId")
    if not isinstance(command_id, str) or not command_id:
        raise RuntimeError("SSM did not return a command id")

    invocation = None
    for attempt in range(12):
        try:
            invocation = json.loads(aws_call([
                "ssm", "get-command-invocation", "--command-id", command_id,
                "--instance-id", instance_id, "--output", "json",
            ]))
        except RuntimeError:
            invocation = None
        if invocation and invocation.get("Status") in {
            "Success", "Failed", "Cancelled", "TimedOut", "Cancelling"
        }:
            break
        if attempt < 11:
            sleep(5)
    if not invocation or invocation.get("Status") != "Success":
        raise RuntimeError("SSM log scan failed or timed out")
    # The remote scanner is the only process allowed to write stdout.
    return validate_summary(json.loads(invocation.get("StandardOutputContent", "")))


def write_outputs(summary, path):
    encoded = base64.b64encode(
        json.dumps(summary, separators=(",", ":")).encode("utf-8")
    ).decode("ascii")
    with Path(path).open("a", encoding="utf-8") as output:
        output.write(f"error_count={summary['error_count']}\n")
        output.write(f"summary_b64={encoded}\n")


if __name__ == "__main__":
    try:
        result = scan(os.environ.get("INSTANCE_ID", ""))
        write_outputs(result, os.environ["GITHUB_OUTPUT"])
        print(f"Scanned {result['scanned_lines']} lines; ERROR entries: {result['error_count']}")
    except (KeyError, ValueError, RuntimeError, OSError, subprocess.SubprocessError, json.JSONDecodeError):
        print("EC2 log scan failed; raw logs and credentials were omitted.", file=sys.stderr)
        sys.exit(1)
