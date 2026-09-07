#!/usr/bin/env bash
set -euo pipefail

code_99="$(tools/release_version_code.sh 99)"
code_100="$(tools/release_version_code.sh 100)"
code_101="$(tools/release_version_code.sh 101)"
repeat_100="$(tools/release_version_code.sh 100)"

(( code_99 < code_100 ))
(( code_100 < code_101 ))
[[ "$code_100" == "$repeat_100" ]]
[[ "$code_100" == "1000100" ]]

echo "release versionCode monotonicity checks passed"
