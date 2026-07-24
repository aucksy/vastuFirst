#!/usr/bin/env bash
# Window-inset guard (UI-POLISH.md §3.A, §6.7).
#
# WHY THIS IS A GREP AND NOT A TEST: JVM screenshot tests (Robolectric/LayoutLib) report
# effectively ZERO window insets, so a screen with the status bar sitting on top of its title
# renders perfectly in CI. The single worst defect in v0.2.1 is invisible to the screenshot gate.
# It is, however, trivially visible in the source — so it is checked here.
#
# Rules:
#   1. MainActivity must opt into edge-to-edge (targetSdk 35 = Android 15 enforces it anyway).
#   2. Every screen composable's root must go through Modifier.screenRoot(...) — or, if it has a
#      good reason not to, apply an explicit inset modifier.
set -euo pipefail

APP_UI="app/src/androidMain/kotlin/com/vastufirst/app/ui"
MAIN_ACTIVITY="app/src/androidMain/kotlin/com/vastufirst/app/MainActivity.kt"
# The one file allowed to define the helper rather than call it.
HELPER="$APP_UI/common/ScreenRoot.kt"

violations=0

# ---- 1. edge-to-edge opt-in -------------------------------------------------------------------
if ! grep -q 'enableEdgeToEdge' "$MAIN_ACTIVITY"; then
  echo "MISSING enableEdgeToEdge() in $MAIN_ACTIVITY"
  echo "  targetSdk 35 draws edge-to-edge with no opt-out; without this the system bars overlap"
  echo "  every screen. See docs/UI-POLISH.md §3.A."
  violations=1
fi

# ---- 2. every screen root is inset ------------------------------------------------------------
# NUL-delimited so a path containing a space can never word-split (the latent bug in the older
# token guard, which used an unquoted `for f in $files`).
while IFS= read -r -d '' f; do
  [ "$f" = "$HELPER" ] && continue
  # Only files that actually declare a full-screen root are in scope.
  grep -q 'fillMaxSize()' "$f" || grep -q 'screenRoot(' "$f" || continue
  if grep -qE 'screenRoot\(|safeDrawingPadding\(|systemBarsPadding\(|windowInsetsPadding\(|contentWindowInsets' "$f"; then
    continue
  fi
  echo "SCREEN ROOT WITHOUT INSET HANDLING: $f"
  grep -nE 'fillMaxSize\(\)' "$f" | head -3
  violations=1
done < <(find "$APP_UI" -name '*.kt' -not -path '*/build/*' -print0)

if [ "$violations" -ne 0 ]; then
  echo ""
  echo "Screen-root check FAILED. Use Modifier.screenRoot(colors.paper) at the root of every screen."
  exit 1
fi
echo "Screen-root check passed: edge-to-edge opted in, every screen root handles its insets."
