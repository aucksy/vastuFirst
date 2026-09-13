#!/usr/bin/env bash
# The scoring path must stay compilable to JavaScript.
#
# ⭐ WHY. The admin panel previews a rule change by running the REAL engine in a browser
# (:enginejs compiles these very files). That only works while the scoring path uses nothing the
# JVM alone provides. The first time it was built, one call — `Map.merge`, which reads as ordinary
# Kotlin and is actually java.util — cost a full cloud round trip to discover. This script finds
# that class of mistake in a second, before anything is pushed.
#
# It is a GREP, not a compiler. It cannot catch everything, and it is not meant to: the real proof
# is `tools/verify-enginejs.mjs`, which runs the built bundle and checks it answers 31. This exists
# so the common mistakes never reach CI at all.
#
# Scope: exactly the files :enginejs compiles — shared's top level, rules, and engine. The scan and
# editor packages are excluded here because they are excluded there.
set -euo pipefail

cd "$(dirname "$0")/.."

FILES=$(
  {
    ls shared/src/main/kotlin/com/vastufirst/shared/*.kt
    find rules/src/main -name '*.kt'
    find engine/src/main -name '*.kt'
  } | grep -v 'rules/src/main/kotlin/com/vastufirst/rules/RuleSetResources.kt'
)

# Each entry: a regex, then the plain-English reason and what to write instead.
PROBLEMS=0
flag() {
  local pattern="$1" reason="$2"
  local hits
  hits=$(grep -nE "$pattern" $FILES 2>/dev/null | grep -vE '^\S+: *(//|\*|/\*)' || true)
  if [ -n "$hits" ]; then
    echo ""
    echo "  $reason"
    echo "$hits" | sed 's/^/    /'
    PROBLEMS=$((PROBLEMS + 1))
  fi
}

flag '\bMath\.[a-zA-Z]' \
  "java.lang.Math does not exist in a browser. Use the helpers in engine/Rounding.kt — and never kotlin.math.round, which rounds a tie to the nearest EVEN number and would move scores."

flag '\.(merge|computeIfAbsent|computeIfPresent|putIfAbsent|getOrDefault)\(' \
  "These map methods come from java.util and do not exist in a browser. Read the value, change it, put it back: map[k] = (map[k] ?: 0.0) + v"

flag '\bjava\.|\bjavax\.' \
  "Nothing under java.* reaches a browser."

flag '::class\.java|\.javaClass|@Jvm(Static|Field|Overloads|Name)|@Throws' \
  "These are JVM-only. If a file genuinely needs them it does not belong in the scoring path."

flag '\b(Thread|System|Collections|Arrays|TreeMap|BigDecimal|NumberFormat|Locale|Pattern)\.' \
  "JVM-only class. Use the Kotlin standard library instead."

flag '\bString\.format\(|\.toSortedMap\(|\bsynchronized\s*\(' \
  "JVM-only. String.format has no browser equivalent; build the text with a template instead."

if [ "$PROBLEMS" -gt 0 ]; then
  echo ""
  echo "Engine portability check FAILED — the scoring path uses something a browser cannot run."
  echo "This would break the admin panel's rule preview, which runs this code in a browser so an"
  echo "expert can see what a change does before publishing it."
  exit 1
fi

echo "Engine portability check passed: the scoring path uses nothing JVM-only."
