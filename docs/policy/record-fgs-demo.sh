#!/usr/bin/env bash
# Record ≤60s FGS demo for Play Console (requires USB device + scrcpy/ffmpeg).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/docs/policy/fgs-special-use-demo.mp4"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG=com.zenithblue.nativecode

adb wait-for-device
adb install -r "$APK"
# Allow notifications (API 33+)
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity"
sleep 2

# Prefer scrcpy if available; else screenrecord → pull
if command -v scrcpy >/dev/null 2>&1; then
  # 50s hard cap
  timeout 50 scrcpy --no-audio --record="$OUT" --max-size=1280 || true
else
  adb shell screenrecord --time-limit 50 /sdcard/fgs-demo.mp4 &
  SR_PID=$!
  sleep 3
  # Start app terminal FGS for visibility if UI automation is unavailable
  adb shell am start-foreground-service -n "$PKG/.AppTerminalService" --ei SESSION_COUNT 1 || true
  sleep 8
  adb shell am start-foreground-service -n "$PKG/.ProjectTerminalService" \
    --ei SESSION_COUNT 1 --es PROJECT_NAME Demo --es PROJECT_PATH /demo || true
  sleep 8
  adb shell am start-foreground-service -n "$PKG/.BackgroundService" || true
  sleep 8
  wait $SR_PID || true
  adb pull /sdcard/fgs-demo.mp4 "$OUT"
  adb shell rm -f /sdcard/fgs-demo.mp4
fi

ls -la "$OUT"
ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$OUT" || true
echo "Wrote $OUT"
