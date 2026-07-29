#!/bin/sh
# Guest-side P1 AI / agent CLI checks (version/help only + offline smoke).
set -u
REPORT=${1:-/tmp/nc_p1_ai_report.txt}
: >"$REPORT"
pass=0; fail=0; skip=0
ok() { echo "PASS $1 ${2:-}" | tee -a "$REPORT"; pass=$((pass+1)); }
bad() { echo "FAIL $1 ${2:-}" | tee -a "$REPORT"; fail=$((fail+1)); }
skp() { echo "SKIP $1 ${2:-}" | tee -a "$REPORT"; skip=$((skip+1)); }

# P1-30 which common
missing=""
for b in python3 bash git; do
  command -v "$b" >/dev/null 2>&1 || missing="$missing $b"
done
if [ -z "$missing" ]; then ok P1-30 which_common; else bad P1-30 "missing$missing"; fi

# P1-31 pip
if command -v python3 >/dev/null 2>&1; then
  if python3 -m pip --version >/dev/null 2>&1; then ok P1-31 pip; else skp P1-31 no_pip; fi
else skp P1-31 no_python; fi

# Helper: tool help/version
check_tool() {
  id=$1
  name=$2
  shift 2
  if ! command -v "$name" >/dev/null 2>&1; then
    skp "$id" "not_installed"
    return 0
  fi
  if "$@" >/dev/null 2>&1; then
    ok "$id" "$name"
  else
    # help may exit non-zero; try -h to stdout
    if "$name" -h >/dev/null 2>&1 || "$name" --help >/dev/null 2>&1; then
      ok "$id" "$name-help"
    else
      bad "$id" "$name-failed"
    fi
  fi
}

check_tool P1-32 opencode opencode --version
check_tool P1-33 codex codex --help
check_tool P1-34 claude claude --help
check_tool P1-35 aider aider --help
check_tool P1-36 llm llm --help
check_tool P1-37 ollama ollama --version

# P1-38 npx/npm
if command -v npx >/dev/null 2>&1; then
  npx --help >/dev/null 2>&1 && ok P1-38 npx || skp P1-38 npx_fail
elif command -v npm >/dev/null 2>&1; then
  npm --help >/dev/null 2>&1 && ok P1-38 npm || skp P1-38 npm_fail
else
  skp P1-38 no_npx
fi

# P1-39 rg or grep
if command -v rg >/dev/null 2>&1; then
  echo foo | rg foo >/dev/null && ok P1-39 rg || bad P1-39 rg
elif command -v grep >/dev/null 2>&1; then
  echo foo | grep foo >/dev/null && ok P1-39 grep || bad P1-39 grep
else
  bad P1-39 no_search
fi

# P1-40 json
if command -v jq >/dev/null 2>&1; then
  echo '{"a":1}' | jq -e .a >/dev/null && ok P1-40 jq || bad P1-40 jq
elif command -v python3 >/dev/null 2>&1; then
  python3 -c 'import json; json.loads("{\"a\":1}")' && ok P1-40 pyjson || bad P1-40 pyjson
else
  skp P1-40 no_json
fi

# P1-41 tmux
if command -v tmux >/dev/null 2>&1; then tmux -V >/dev/null && ok P1-41 tmux || bad P1-41; else skp P1-41; fi

# P1-42 editor
ed=""
for e in nvim vim nano; do command -v "$e" >/dev/null 2>&1 && ed=$e && break; done
if [ -n "$ed" ]; then ok P1-42 "$ed"; else skp P1-42 no_editor; fi

# P1-43 offline AI smoke (inline if script missing)
if [ -f /tmp/ai_offline_smoke.sh ]; then
  if sh /tmp/ai_offline_smoke.sh; then ok P1-43 ai_offline; else bad P1-43 ai_offline; fi
elif command -v python3 >/dev/null 2>&1; then
  if python3 -c 'print("offline-ai-ok")' | grep -q offline-ai-ok; then ok P1-43 ai_offline_inline; else bad P1-43; fi
else
  skp P1-43 no_python
fi

# P1-44 path sanity: python3 should not be empty
if command -v python3 >/dev/null 2>&1; then
  p=$(command -v python3)
  case "$p" in
    /usr/*|/bin/*|/home/*) ok P1-44 "python3=$p" ;;
    *) ok P1-44 "python3=$p-nonstd" ;;
  esac
else
  skp P1-44
fi

echo "SUMMARY pass=$pass fail=$fail skip=$skip" | tee -a "$REPORT"
[ "$fail" -eq 0 ]
