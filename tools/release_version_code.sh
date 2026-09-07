#!/usr/bin/env bash
set -euo pipefail

run_number="${1:?usage: release_version_code.sh GITHUB_RUN_NUMBER}"
if [[ ! "$run_number" =~ ^[0-9]+$ ]]; then
  echo "run number must be a positive integer" >&2
  exit 1
fi
run=$((10#$run_number))
if (( run <= 0 )); then
  echo "run number must be positive" >&2
  exit 1
fi

# Keep public release codes well above historical small CI codes while leaving
# ample room below Android's 2,100,000,000 versionCode ceiling.
base=1000000
version_code=$((base + run))
if (( version_code <= base || version_code > 2100000000 )); then
  echo "generated versionCode is outside the supported range: $version_code" >&2
  exit 1
fi
printf '%d\n' "$version_code"
