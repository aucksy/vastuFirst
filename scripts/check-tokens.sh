#!/usr/bin/env bash
# Token guard (Impl PRD §3.3, §6 review gate — the two greppable lines).
# The ONLY home for raw colour/size literals is the design-system theme package.
# Everywhere else must read from VastuTheme.colors / .spacing / .shapes / .type.
#   - No raw hex:  Color(0x……)
#   - No raw .dp in a spacing position
#   - No raw .sp text size
set -euo pipefail

# Allowed home for raw literals (theme definitions live here and nowhere else).
ALLOW='designsystem/src/commonMain/kotlin/com/vastufirst/designsystem/theme/'

violations=0

# All Kotlin sources across modules, excluding build output and the theme package.
files=$(find app designsystem engine rules shared data -name '*.kt' -not -path '*/build/*' 2>/dev/null \
        | grep -v "$ALLOW" || true)

for f in $files; do
  if grep -Enq 'Color\(0x' "$f"; then
    echo "RAW HEX (use VastuTheme.colors) in $f:"; grep -En 'Color\(0x' "$f"; violations=1
  fi
  if grep -Enq '[0-9]+\.dp\b' "$f"; then
    echo "RAW .dp (use VastuTheme.spacing/shapes) in $f:"; grep -En '[0-9]+\.dp\b' "$f"; violations=1
  fi
  if grep -Enq '[0-9]+\.sp\b' "$f"; then
    echo "RAW .sp (use VastuTheme.type) in $f:"; grep -En '[0-9]+\.sp\b' "$f"; violations=1
  fi
done

if [ "$violations" -ne 0 ]; then
  echo ""
  echo "Token check FAILED — raw hex/dp/sp found outside the theme package."
  exit 1
fi
echo "Token check passed: no raw hex / dp / sp outside the theme package."
