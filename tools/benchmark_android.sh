#!/usr/bin/env bash
set -euo pipefail

PKG="${1:-dev.midroid.app}"
DURATION="${2:-60}"
LABEL="${3:-$(date +%Y%m%d-%H%M%S)}"
OUT="${4:-benchmarks/${LABEL}}"
ADB="${ADB:-adb}"
SAMPLE_INTERVAL="${MIDROID_SAMPLE_INTERVAL:-10}"
FAKE_UNPLUG="${MIDROID_BATTERY_UNPLUG:-0}"
SCENARIO="${MIDROID_SCENARIO:-foreground-idle}"
WARMUP_SECONDS="${MIDROID_WARMUP_SECONDS:-5}"

if ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb was not found. Set ADB=/path/to/adb or add it to PATH." >&2
  exit 2
fi

if ! [[ "$DURATION" =~ ^[0-9]+$ ]] || [ "$DURATION" -lt 1 ]; then
  echo "duration must be a positive integer number of seconds" >&2
  exit 2
fi

if ! [[ "$SAMPLE_INTERVAL" =~ ^[0-9]+$ ]] || [ "$SAMPLE_INTERVAL" -lt 1 ]; then
  echo "MIDROID_SAMPLE_INTERVAL must be a positive integer" >&2
  exit 2
fi

if ! [[ "$WARMUP_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "MIDROID_WARMUP_SECONDS must be a non-negative integer" >&2
  exit 2
fi

case "$SCENARIO" in
  foreground-idle|background|manual) ;;
  *)
    echo "MIDROID_SCENARIO must be foreground-idle, background, or manual" >&2
    exit 2
    ;;
esac

if [ "$("$ADB" get-state 2>/dev/null || true)" != "device" ]; then
  echo "No adb device is ready." >&2
  exit 3
fi

mkdir -p "$OUT"

cleanup() {
  if [ "$FAKE_UNPLUG" = "1" ]; then
    "$ADB" shell dumpsys battery reset >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

capture() {
  local name="$1"
  shift
  "$ADB" shell "$@" >"$OUT/$name" 2>&1 || true
}

{
  echo "label=$LABEL"
  echo "package=$PKG"
  echo "scenario=$SCENARIO"
  echo "duration_seconds=$DURATION"
  echo "warmup_seconds=$WARMUP_SECONDS"
  echo "sample_interval_seconds=$SAMPLE_INTERVAL"
  echo "fake_unplug=$FAKE_UNPLUG"
  echo "captured_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "adb_serial=$($ADB get-serialno 2>/dev/null || echo unknown)"
  echo "device=$($ADB shell getprop ro.product.device 2>/dev/null | tr -d '\r')"
  echo "model=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  echo "sdk=$($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
  echo "build=$($ADB shell getprop ro.build.fingerprint 2>/dev/null | tr -d '\r')"
  echo "screen_brightness=$($ADB shell settings get system screen_brightness 2>/dev/null | tr -d '\r')"
  echo "peak_refresh_rate=$($ADB shell settings get system peak_refresh_rate 2>/dev/null | tr -d '\r')"
  echo "min_refresh_rate=$($ADB shell settings get system min_refresh_rate 2>/dev/null | tr -d '\r')"
} >"$OUT/manifest.txt"

capture package.txt dumpsys package "$PKG"
capture battery-before.txt dumpsys battery
capture display-before.txt dumpsys display
capture webview.txt dumpsys webviewupdate
capture activity-processes-before.txt dumpsys activity processes
capture netstats-before.txt dumpsys netstats detail

"$ADB" logcat -c >/dev/null 2>&1 || true
"$ADB" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1 || true
"$ADB" shell dumpsys batterystats --reset >/dev/null 2>&1 || true

if [ "$FAKE_UNPLUG" = "1" ]; then
  "$ADB" shell dumpsys battery unplug >/dev/null 2>&1 || true
fi

"$ADB" shell am force-stop "$PKG" >/dev/null 2>&1 || true
"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >"$OUT/launch.txt" 2>&1 || true
sleep "$WARMUP_SECONDS"

case "$SCENARIO" in
  foreground-idle)
    echo "scenario_transition=none" >"$OUT/scenario.txt"
    ;;
  background)
    "$ADB" shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
    sleep 2
    echo "scenario_transition=home" >"$OUT/scenario.txt"
    capture activity-after-transition.txt dumpsys activity activities
    ;;
  manual)
    echo "scenario_transition=manual" >"$OUT/scenario.txt"
    ;;
esac

START_EPOCH=$(date +%s)
END_EPOCH=$((START_EPOCH + DURATION))

{
  echo "# top snapshots during benchmark"
  while [ "$(date +%s)" -lt "$END_EPOCH" ]; do
    echo
    echo "===== $(date -u +%Y-%m-%dT%H:%M:%SZ) ====="
    "$ADB" shell top -b -n 1 -m 80 2>&1 || true
    NOW=$(date +%s)
    REMAIN=$((END_EPOCH - NOW))
    if [ "$REMAIN" -le 0 ]; then
      break
    fi
    if [ "$REMAIN" -lt "$SAMPLE_INTERVAL" ]; then
      sleep "$REMAIN"
    else
      sleep "$SAMPLE_INTERVAL"
    fi
  done
} >"$OUT/top-samples.txt"

capture meminfo-after.txt dumpsys meminfo "$PKG"
capture gfxinfo-after.txt dumpsys gfxinfo "$PKG" framestats
capture cpuinfo-after.txt dumpsys cpuinfo
capture procstats-after.txt dumpsys procstats "$PKG"
capture batterystats-after.txt dumpsys batterystats --charged "$PKG"
capture battery-after.txt dumpsys battery
capture display-after.txt dumpsys display
capture activity-processes-after.txt dumpsys activity processes
capture netstats-after.txt dumpsys netstats detail
capture jobscheduler-after.txt dumpsys jobscheduler "$PKG"

"$ADB" logcat -d -v threadtime MidroidDiag:I '*:S' >"$OUT/midroid-diag.log" 2>&1 || true

if [ "$PKG" = "dev.midroid.app" ] && [ "$SCENARIO" = "background" ]; then
  if grep -q "event=hidden" "$OUT/midroid-diag.log"; then
    echo "deep_suspend_transition=observed" >>"$OUT/scenario.txt"
  else
    echo "deep_suspend_transition=not_observed" >>"$OUT/scenario.txt"
    echo "Warning: MidroidDiag did not show event=hidden during the background scenario." >&2
  fi
fi

cat <<EOF
Benchmark capture complete.
Package: $PKG
Scenario: $SCENARIO
Duration: ${DURATION}s
Output: $OUT

For fair A/B testing, repeat the same scenario at least three times for each target and keep brightness, refresh-rate settings, network, account/timeline, and device temperature as constant as practical.
EOF
