#!/bin/sh
# Offline AI-tool-adjacent smoke: pure local logic (no network, no API keys).
# Simulates "agent can run a local transform pipeline" inside the guest.
set -eu

TMP=${TMPDIR:-/tmp}/nc_ai_smoke_$$
mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT

# 1) Local "prompt" file
printf '%s\n' 'Summarize: NativeCode proot regression' >"$TMP/prompt.txt"

# 2) Python offline transform (stand-in for local agent step)
python3 - <<'PY' "$TMP/prompt.txt" "$TMP/out.txt"
import sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding="utf-8").read().strip()
# trivial extractive "summary"
words = text.split()
summary = " ".join(words[:8]) + ("…" if len(words) > 8 else "")
open(dst, "w", encoding="utf-8").write(summary + "\n")
print("offline-ai-ok", len(words))
PY

# 3) Assert output
grep -q 'NativeCode' "$TMP/out.txt" || grep -q 'proot' "$TMP/out.txt" || {
  echo "AI offline smoke: unexpected output" >&2
  cat "$TMP/out.txt" >&2
  exit 1
}

# 4) JSON tool path (jq or python)
python3 - <<'PY'
import json
print(json.dumps({"ok": True, "tool": "offline-smoke"}))
PY

echo "ai_offline_smoke PASS"
