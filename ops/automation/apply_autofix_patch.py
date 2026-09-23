"""Extract, constrain and apply a model-proposed patch in a secret-free job."""

import os
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath


MAX_PATCH_CHARS = 200_000
DIFF_FENCE = re.compile(r"\A\s*```diff\s*\n(?P<patch>.+?)\n```\s*\Z", re.DOTALL)
DIFF_HEADER = re.compile(r"^diff --git a/(.+) b/(.+)$", re.MULTILINE)
ALLOWED_ROOTS = ("src/main/java/", "src/test/java/")


def extract_patch(proposal):
    match = DIFF_FENCE.fullmatch(proposal)
    if not match:
        raise ValueError("Codex response must contain exactly one diff fence")
    patch = match.group("patch") + "\n"
    if len(patch) > MAX_PATCH_CHARS or "GIT binary patch" in patch:
        raise ValueError("Patch is too large or binary")
    if any(marker in patch for marker in ("old mode ", "new mode ", "rename from ", "rename to ")):
        raise ValueError("Mode changes and renames are blocked")
    headers = DIFF_HEADER.findall(patch)
    if not headers:
        raise ValueError("Patch has no file changes")
    for before, after in headers:
        if before != after:
            raise ValueError("Renames are blocked")
        path = PurePosixPath(before)
        if path.is_absolute() or ".." in path.parts:
            raise ValueError("Unsafe patch path")
        normalized = str(path)
        if not normalized.startswith(ALLOWED_ROOTS) or path.suffix != ".java":
            raise ValueError(f"Blocked patch path: {normalized}")
    return patch


def apply_patch(proposal, repository):
    repo = Path(repository).resolve()
    patch_path = repo / ".codex-autofix.patch"
    patch_path.write_text(extract_patch(proposal), encoding="utf-8")
    try:
        for args in (["git", "apply", "--check", "--whitespace=error-all", str(patch_path)],
                     ["git", "apply", "--whitespace=error-all", str(patch_path)]):
            result = subprocess.run(args, cwd=repo, capture_output=True, text=True, check=False)
            if result.returncode != 0:
                raise ValueError("Patch could not be applied cleanly")
    finally:
        patch_path.unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        apply_patch(os.environ.get("CODEX_PROPOSAL", ""), sys.argv[1])
        print("Codex patch applied within the allowed Java source/test paths")
    except (IndexError, OSError, ValueError):
        print("Codex patch rejected; no publish step will run.", file=sys.stderr)
        sys.exit(1)
