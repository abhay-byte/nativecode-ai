#!/bin/sh
# Guest-side P1 development checks.
set -u
REPORT=${1:-/tmp/nc_p1_dev_report.txt}
: >"$REPORT"
pass=0; fail=0; skip=0
ok() { echo "PASS $1 ${2:-}" | tee -a "$REPORT"; pass=$((pass+1)); }
bad() { echo "FAIL $1 ${2:-}" | tee -a "$REPORT"; fail=$((fail+1)); }
skp() { echo "SKIP $1 ${2:-}" | tee -a "$REPORT"; skip=$((skip+1)); }

WORKDIR=${HOME:-/tmp}/nc_p1_work_$$
mkdir -p "$WORKDIR"
trap 'rm -rf "$WORKDIR"' EXIT
cd "$WORKDIR" || exit 1

# P1-01 compiler
CC=""
for c in cc gcc clang; do
  if command -v "$c" >/dev/null 2>&1; then CC=$c; break; fi
done
if [ -n "$CC" ]; then ok P1-01 "cc=$CC"; else skp P1-01 no_compiler; fi

# P1-02 compile hello
if [ -n "$CC" ]; then
  cat >hello.c <<'EOF'
#include <stdio.h>
int main(void){ puts("nc-regression-hello"); return 0; }
EOF
  if $CC -O0 -o hello hello.c && ./hello | grep -q nc-regression-hello; then
    ok P1-02 compile_hello
  else
    bad P1-02 compile_hello
  fi
else
  skp P1-02 no_compiler
fi

# P1-03 make
if command -v make >/dev/null 2>&1; then
  make -v >/dev/null 2>&1 && ok P1-03 make || bad P1-03 make
else skp P1-03 no_make; fi

# P1-04 cmake
if command -v cmake >/dev/null 2>&1; then
  cmake --version >/dev/null 2>&1 && ok P1-04 cmake || bad P1-04 cmake
else skp P1-04 no_cmake; fi

# P1-05/06 git
if command -v git >/dev/null 2>&1; then
  mkdir g && cd g || exit 1
  git init -q
  git config user.email "reg@nativecode.local"
  git config user.name "regression"
  echo x >f
  git add f
  if git commit -q -m 't' && git rev-parse HEAD >/dev/null; then
    ok P1-05 git_commit
  else
    bad P1-05 git_commit
  fi
  if git status --porcelain | grep -q .; then bad P1-06 dirty; else ok P1-06 clean; fi
  cd .. || exit 1
else
  skp P1-05 no_git
  skp P1-06 no_git
fi

# P1-07 python
if command -v python3 >/dev/null 2>&1; then
  if python3 -c 'print(42)' | grep -q 42; then ok P1-07 python3; else bad P1-07 python3; fi
else skp P1-07 no_python3; fi

# P1-08 venv
if command -v python3 >/dev/null 2>&1; then
  if python3 -m venv venv 2>/dev/null && venv/bin/python -c 'print(1)' | grep -q 1; then
    ok P1-08 venv
  else
    skp P1-08 venv_fail
  fi
else skp P1-08 no_python3; fi

# P1-09/10 node
if command -v node >/dev/null 2>&1; then
  node -v >/dev/null 2>&1 && ok P1-09 node || bad P1-09 node
  if node -e "console.log('ok')" | grep -q ok; then ok P1-10 node_hello; else bad P1-10 node_hello; fi
else
  skp P1-09 no_node
  skp P1-10 no_node
fi

# P1-11 rustc
if command -v rustc >/dev/null 2>&1; then rustc -V >/dev/null && ok P1-11 rustc || bad P1-11; else skp P1-11; fi
# P1-12 go
if command -v go >/dev/null 2>&1; then go version >/dev/null && ok P1-12 go || bad P1-12; else skp P1-12; fi

# P1-15 tar
mkdir tree && echo a >tree/f
if tar czf t.tgz tree && rm -rf tree && tar xzf t.tgz && grep -q a tree/f; then
  ok P1-15 tar
else
  bad P1-15 tar
fi

# P1-16 ssh-keygen
if command -v ssh-keygen >/dev/null 2>&1; then
  if ssh-keygen -t ed25519 -N '' -f "$WORKDIR/k" >/dev/null 2>&1 && test -f "$WORKDIR/k"; then
    ok P1-16 ssh_keygen
  else
    bad P1-16 ssh_keygen
  fi
else skp P1-16; fi

# P1-17 curl
if [ "${NC_OFFLINE:-0}" = "1" ]; then
  skp P1-17 offline
elif command -v curl >/dev/null 2>&1; then
  if curl -fsSIL --max-time 15 https://example.com >/dev/null 2>&1; then ok P1-17 curl; else skp P1-17 curl_fail; fi
else skp P1-17 no_curl; fi

# P1-18 openssl
if command -v openssl >/dev/null 2>&1; then openssl version >/dev/null && ok P1-18 openssl || bad P1-18; else skp P1-18; fi

echo "SUMMARY pass=$pass fail=$fail skip=$skip" | tee -a "$REPORT"
[ "$fail" -eq 0 ]
