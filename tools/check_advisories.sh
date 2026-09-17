#!/usr/bin/env bash
set -euo pipefail

# Dumps the resolved release dependency closure and checks each coordinate
# against OSV (https://api.osv.dev). Fails closed on unresolved entries or
# reported vulnerabilities. Network access to api.osv.dev is required;
# set MIDROID_SBOM_OFFLINE=1 to dump the closure without querying.
#
# Usage:
#   bash tools/check_advisories.sh [--offline]
#   MIDROID_SBOM_OFFLINE=1 bash tools/check_advisories.sh

OFFLINE="${MIDROID_SBOM_OFFLINE:-0}"
if [[ "${1:-}" == "--offline" ]]; then
  OFFLINE=1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${MIDROID_SBOM_OUT:-$REPO_ROOT/build/sbom}"
mkdir -p "$OUT_DIR"
CLOSURE_FILE="$OUT_DIR/release-closure.txt"
REPORT_FILE="$OUT_DIR/advisory-report.txt"

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set; Gradle needs a JDK 17+. Export JAVA_HOME first." >&2
  exit 2
fi

if ! command -v ./gradlew >/dev/null 2>&1 && [[ ! -x "$REPO_ROOT/gradlew" ]]; then
  echo "gradlew was not found at the repository root." >&2
  exit 2
fi

echo "Resolving releaseRuntimeClasspath closure..."
(
  cd "$REPO_ROOT"
  ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath
) >"$OUT_DIR/dependencies.txt"

grep -oE "[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+( -> [a-zA-Z0-9_.-]+)?" "$OUT_DIR/dependencies.txt" \
  | sed -E 's/ -> .*//' \
  | grep -E ':' \
  | sort -u >"$CLOSURE_FILE"

COUNT="$(wc -l <"$CLOSURE_FILE" | tr -d ' ')"
echo "Resolved $COUNT unique coordinates -> $CLOSURE_FILE"
if [[ "$COUNT" -eq 0 ]]; then
  echo "No coordinates resolved; refusing to report clean." >&2
  exit 1
fi

: >"$REPORT_FILE"
{
  echo "Midroid advisory report"
  echo "generated_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "coordinates=$COUNT"
  echo ""
} >>"$REPORT_FILE"

if [[ "$OFFLINE" == "1" ]]; then
  echo "OFFLINE mode: closure dumped, OSV queries skipped." | tee -a "$REPORT_FILE"
  echo "Closure: $CLOSURE_FILE"
  exit 0
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for OSV queries." >&2
  exit 2
fi

FAILURES=0
while IFS= read -r coord; do
  name="${coord%:*}"
  version="${coord##*:}"
  payload="$(printf '{"package":{"name":%s,"ecosystem":"Maven"},"version":%s}' \
    "$(printf '%s' "$name" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')" \
    "$(printf '%s' "$version" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')")"
  response="$(curl -s -m 30 https://api.osv.dev/v1/query -H 'Content-Type: application/json' -d "$payload" || true)"
  if [[ -z "$response" ]]; then
    echo "QUERY-FAILED $coord (empty response)" | tee -a "$REPORT_FILE"
    FAILURES=$((FAILURES + 1))
    continue
  fi
  vulns="$(printf '%s' "$response" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d.get("vulns", [])))' 2>/dev/null || echo "PARSE-ERROR")"
  if [[ "$vulns" == "PARSE-ERROR" ]]; then
    echo "QUERY-FAILED $coord (unparseable response)" | tee -a "$REPORT_FILE"
    FAILURES=$((FAILURES + 1))
  elif [[ "$vulns" != "0" ]]; then
    echo "VULNERABLE $coord vulns=$vulns" | tee -a "$REPORT_FILE"
    printf '%s' "$response" | python3 -c 'import json,sys; [print("  -", v.get("id"), (v.get("summary") or "")[:120]) for v in json.load(sys.stdin).get("vulns", [])]' | tee -a "$REPORT_FILE"
    FAILURES=$((FAILURES + 1))
  else
    echo "clean $coord" >>"$REPORT_FILE"
  fi
done <"$CLOSURE_FILE"

echo "" >>"$REPORT_FILE"
if [[ "$FAILURES" -ne 0 ]]; then
  echo "RESULT: $FAILURES coordinate(s) need attention. See $REPORT_FILE" | tee -a "$REPORT_FILE"
  exit 1
fi
echo "RESULT: all $COUNT coordinates clean per OSV at query time. See $REPORT_FILE" | tee -a "$REPORT_FILE"
echo "Report: $REPORT_FILE"
