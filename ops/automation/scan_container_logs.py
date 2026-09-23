"""Convert Spring container logs into a small, non-sensitive JSON summary."""

import json
import re
import sys
from collections import Counter


ERROR_LEVEL = re.compile(r"(?:^|\s)ERROR(?:\s|$)")
EXCEPTION_TYPE = re.compile(r"(?:[A-Za-z_$][\w$]*\.)*([A-Za-z_$][\w$]*(?:Exception|Error))\b")


def summarize(lines):
    scanned = 0
    errors = 0
    types = Counter()
    for line in lines:
        scanned += 1
        if ERROR_LEVEL.search(line):
            errors += 1
        for match in EXCEPTION_TYPE.finditer(line):
            types[match.group(1)] += 1
    return {
        "scanned_lines": scanned,
        "error_count": errors,
        "exception_types": [
            {"name": name, "count": count}
            for name, count in types.most_common(5)
        ],
    }


if __name__ == "__main__":
    print(json.dumps(summarize(sys.stdin), separators=(",", ":")))
