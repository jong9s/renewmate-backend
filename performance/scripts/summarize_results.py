from __future__ import annotations

import json
import statistics
import sys
from pathlib import Path


def read_run(path: Path) -> dict[str, float]:
    with path.open(encoding="utf-8") as file:
        metrics = json.load(file)["metrics"]

    duration = metrics["http_req_duration{expected_response:true}"]
    return {
        "p50_ms": float(duration["med"]),
        "average_ms": float(duration["avg"]),
        "p95_ms": float(duration["p(95)"]),
        "rps": float(metrics["http_reqs"]["rate"]),
        "failure_rate": float(metrics["http_req_failed"]["value"]),
    }


def summarize(result_dir: Path, prefix: str) -> None:
    runs = [read_run(path) for path in sorted(result_dir.glob(f"{prefix}-run-*.json"))]
    if len(runs) != 5:
        raise ValueError(f"Expected 5 {prefix} runs, found {len(runs)}")

    print(prefix)
    for key in runs[0]:
        values = [run[key] for run in runs]
        print(
            f"  {key}: median={statistics.median(values):.6f}, "
            f"mean={statistics.fmean(values):.6f}"
        )


def main() -> None:
    result_dir = Path(sys.argv[1] if len(sys.argv) > 1 else "performance/results")
    summarize(result_dir, "before")
    summarize(result_dir, "after")


if __name__ == "__main__":
    main()
