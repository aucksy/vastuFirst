#!/usr/bin/env bash
# Module-boundary guard (Product PRD §3.1 / Impl PRD §3.1).
# The four pure logic modules must stay Android-free so the future iOS port is a
# build-file change, not a rewrite. Fail the build if any of them applies an Android
# plugin or imports android.* / androidx.*.
set -euo pipefail

PURE_MODULES="engine rules shared data"
fail=0

for m in $PURE_MODULES; do
  bf="$m/build.gradle.kts"
  if [ ! -f "$bf" ]; then
    echo "MISSING: $bf"; fail=1; continue
  fi
  if grep -Eq 'com\.android|androidLibrary|androidApplication|androidTarget|kotlin\("android"\)' "$bf"; then
    echo "BOUNDARY VIOLATION: $bf references an Android plugin/target."; fail=1
  fi
  if grep -rEnq '^[[:space:]]*import[[:space:]]+(android|androidx)\.' "$m/src" 2>/dev/null; then
    echo "BOUNDARY VIOLATION: android/androidx import found in pure module '$m':"
    grep -rEn '^[[:space:]]*import[[:space:]]+(android|androidx)\.' "$m/src"
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "Module-boundary check FAILED — engine/rules/shared/data must resolve zero Android."
  exit 1
fi
echo "Module-boundary check passed: engine/rules/shared/data are Android-free."
