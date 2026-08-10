#!/usr/bin/env bash
# Ruleset-version guard (CLAUDE.md "rules are data"; Product PRD §5.1, §4f).
#
# WHY THIS EXISTS. Every saved home stores the rule version it was scored under. When that version
# moves, the app re-scores those homes and shows the reader a plain sentence explaining why their
# number changed. That machinery works — and on 9 August 2026 it was bypassed completely. v0.9.0
# changed how EVERY home is scored (partial credit, a new penalty ramp) and never touched the
# version or the change note. So saved scores moved in silence, and the note the app would have
# shown still talked about prayer rooms, the release before.
#
# Nothing caught it. The two tests that named the version pinned it as a literal, which fails on a
# deliberate bump and stays green on exactly this failure: the rules changed, the version did not,
# so the literal still matched. A test that reads only the current dataset CANNOT see this — the
# fault is in the relationship between two files across a commit, which is what git can see.
#
# THE RULE: if any rule file changes, meta.json changes with it. No exceptions — a rule edit that
# does not move a user's score is still a rule edit, and proving it was harmless costs more than
# bumping the stamp.
#
# Usage:  bash scripts/check-ruleset-version.sh [BEFORE_REF [AFTER_REF]]
#   Default range is HEAD~1..HEAD. CI passes the push range so a multi-commit push is checked whole
#   (a rules edit in one commit and the bump in the next is fine — the release is what ships).
#
# Exit 0 = fine · 1 = rules moved without the stamp · 2 = the check could not run.
set -euo pipefail

DIR='rules/src/main/resources/ruleset'
META="$DIR/meta.json"

if [ ! -d "$DIR" ]; then
  echo "RULESET VERSION CHECK BROKEN: $DIR does not exist."
  echo "Refusing to report 'the stamp is fine' — nothing was compared."
  exit 2
fi

BEFORE="${1:-HEAD~1}"
AFTER="${2:-HEAD}"

# A shallow clone or the very first commit has no parent to compare against. Say so out loud and
# pass: "could not compare" must never be silent, but it must also not block a legitimate release.
if ! git rev-parse --verify --quiet "$BEFORE^{commit}" >/dev/null; then
  echo "RULESET VERSION CHECK: no commit to compare against ('$BEFORE')."
  echo "Skipping — this is a shallow checkout or the first commit, not a passing result."
  exit 0
fi

CHANGED=$(git diff --name-only "$BEFORE" "$AFTER" -- "$DIR" || true)
if [ -z "$CHANGED" ]; then
  echo "Ruleset version check passed: no rule files changed in $BEFORE..$AFTER."
  exit 0
fi

RULES_CHANGED=$(printf '%s\n' "$CHANGED" | grep -v "^$META$" || true)
META_CHANGED=$(printf '%s\n' "$CHANGED" | grep "^$META$" || true)

if [ -n "$RULES_CHANGED" ] && [ -z "$META_CHANGED" ]; then
  echo "RULESET VERSION CHECK FAILED: rule files changed but the version stamp did not."
  echo ""
  echo "Changed:"
  printf '  %s\n' $RULES_CHANGED
  echo ""
  echo "Every saved home stores the rule version it was scored under. Leaving the stamp still means"
  echo "those homes are re-scored in silence, and the explanation the app shows describes the"
  echo "PREVIOUS change. That is exactly what happened on 9 August 2026 and it reached users."
  echo ""
  echo "Fix: in $META bump 'version' and rewrite 'changeNote' to say, in plain words, what moved"
  echo "and why — it is the only thing a person gets to read about their own score changing."
  exit 1
fi

echo "Ruleset version check passed: rule files changed and the version stamp moved with them."
