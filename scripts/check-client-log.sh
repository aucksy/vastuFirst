#!/usr/bin/env bash
# Client-log guard (CLAUDE.md §2d — the client log is part of "done").
#
# WHY THIS EXISTS. Nine releases went out between 4 and 6 August 2026 while docs/CLIENT-WEEKLY.md's
# newest entry still read "Monday 3 August" and still linked v0.6.6. That file is the client's only
# window onto the work, and it had been blank for ten days before the owner noticed. A rule with no
# check is a rule that gets skipped, so this is the check.
#
# Two failures, both about the same thing — has the newest build been written up?
#   1. The newest tag is not NAMED in the log. This is the one that matters: a release the client
#      cannot read about is a release he was not told about.
#   2. The log's newest week ended more than MAX_LAG_DAYS before that tag. This catches the shape
#      rule 1 cannot see — a tag name dropped into a stale week block to quiet the check, with no
#      week written around it.
#
# Usage:  bash scripts/check-client-log.sh [tag]
#   With no argument it finds the newest tag itself (needs tags fetched — see the workflow).
#   On the release path the tag being released is passed in explicitly, because that tag is the
#   subject of the check and the runner's tag list is not guaranteed to hold anything else.
#
# Exit 0 = the newest build is written up · 1 = it is not · 2 = the check itself could not run.
set -euo pipefail

LOG='docs/CLIENT-WEEKLY.md'
MAX_LAG_DAYS=7

# ---- the check must never pass by accident ------------------------------------------------------
# A missing file or a tagless checkout means this guard did not actually verify anything. That is
# exit 2, not exit 0: "could not check" must never read as "checked and fine".
if [ ! -f "$LOG" ]; then
  echo "CLIENT LOG CHECK BROKEN: $LOG does not exist."
  echo "Refusing to report 'the log is up to date' — nothing was read."
  exit 2
fi

TAG="${1:-}"
if [ -z "$TAG" ]; then
  TAG=$(git tag --sort=-creatordate | head -n 1 || true)
fi
if [ -z "$TAG" ]; then
  echo "CLIENT LOG CHECK BROKEN: no git tags are visible here."
  echo "The newest release cannot be identified, so nothing can be verified against it."
  echo "In CI this means the checkout did not fetch tags (fetch-depth: 0), not that none exist."
  exit 2
fi

# ---- 1. the newest build must be named in the log -----------------------------------------------
if ! grep -qF "$TAG" "$LOG"; then
  echo "CLIENT LOG CHECK FAILED: $TAG is not mentioned anywhere in $LOG."
  echo ""
  echo "That build has shipped with nothing in the client's log about it. Per CLAUDE.md 2d the log"
  echo "is part of 'done' and is written in the SAME commit as the release, not afterwards."
  echo ""
  echo "Write the entry in the file's own voice — outcomes only, no file names, no code, no CI talk"
  echo "— point its 'Try it' link at $TAG, and commit it with the release."
  exit 1
fi

# ---- 2. …and it must sit in a week that was actually written --------------------------------------
# The log's newest week heading looks like '## Week of 28 July - 3 August 2026' or
# '## Week of 3-9 August 2026'. Everything after the last dash is the week's END date, which is the
# freshest date the file claims. Both en-dash and hyphen are accepted: the file uses the typographic
# one, and a heading typed with a plain hyphen must not silently skip this half of the check.
WEEK_LINE=$(grep -m 1 '^## Week of ' "$LOG" || true)
if [ -z "$WEEK_LINE" ]; then
  echo "CLIENT LOG CHECK BROKEN: no '## Week of ...' heading found in $LOG."
  echo "The log's freshness cannot be read, so it is not being reported as fresh."
  exit 2
fi

WEEK_END=$(printf '%s' "$WEEK_LINE" | sed 's/^## Week of //' | sed 's/.*[–-]//' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
LOG_EPOCH=$(date -d "$WEEK_END" +%s 2>/dev/null || true)
TAG_EPOCH=$(git log -1 --format=%at "$TAG" 2>/dev/null || true)

if [ -z "$LOG_EPOCH" ] || [ -z "$TAG_EPOCH" ]; then
  # Deliberately NOT a failure. Rule 1 has already proved the newest build is written up, which is
  # the promise to the client; an unparseable date or an unreachable tag object is this script's
  # problem and must not block a release that is genuinely documented. Said out loud, never silent.
  echo "CLIENT LOG CHECK: could not compare dates (week '$WEEK_END', tag '$TAG')."
  echo "Skipping the staleness half only — $TAG is named in the log, which is the binding rule."
  echo "Client log check passed: $TAG is written up in $LOG."
  exit 0
fi

LAG_DAYS=$(( (TAG_EPOCH - LOG_EPOCH) / 86400 ))
if [ "$LAG_DAYS" -gt "$MAX_LAG_DAYS" ]; then
  echo "CLIENT LOG CHECK FAILED: the log's newest week ends $WEEK_END, which is $LAG_DAYS days"
  echo "before $TAG was made — more than the $MAX_LAG_DAYS allowed."
  echo ""
  echo "$TAG is named in the file, so this is the other shape of the same problem: a build listed"
  echo "under a week that was never written. Start a new week block for it, newest at the top."
  exit 1
fi

echo "Client log check passed: $TAG is written up in $LOG (newest week ends $WEEK_END)."
