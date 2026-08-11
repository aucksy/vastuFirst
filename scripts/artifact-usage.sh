#!/usr/bin/env bash
# GitHub Actions artifact store — report, and optionally prune.
#
# WHY THIS EXISTS. The free-tier artifact store is 500 MB and it is shared across EVERY repo on the
# account, not per repo. It never resets on its own: artifacts sit until they expire or are deleted.
# When it fills, `actions/upload-artifact` fails with "Artifact storage quota has been hit" — and
# because our two uploads are `continue-on-error` (deliberately — see the comments on them in
# .github/workflows/ci.yml), the build stays GREEN and the failure is easy to miss entirely.
#
# What that costs us specifically: when the render gate finds a golden mismatch it writes side-by-side
# *_compare.png diffs into app/build/outputs/roborazzi, and the artifact is the ONLY way those images
# reach a human. The goldens themselves are committed back into the repo by a separate step, so
# nothing is blocked — but "what actually changed on screen" becomes invisible. Given CLAUDE.md §2b
# ("the build must be able to see"), a silently-full store quietly disables the one gate that exists
# because CI compiled the UI without ever rendering it.
#
# This happened on 4 Aug 2026 (332 artifacts, 6.3 GB, cleared by hand) and again on 11 Aug 2026 —
# because nothing stopped it refilling. Two things fix that:
#   1. `retention-days` on every upload step, so artifacts expire on their own. Done, in both
#      workflows. That stops the SLOW refill.
#   2. This script, for when it fills anyway (a burst of pushes, or another repo doing the eating).
#
# WHY IT IS NOT A CI GATE. It needs a token that can see every repo on the account. CI's token is
# scoped to this one repo, by design, and widening it would be a far worse trade than running this
# by hand on the rare day it is needed. Run it locally.
#
# USAGE
#   scripts/artifact-usage.sh                      # report only — never deletes, safe to run anytime
#   scripts/artifact-usage.sh --older-than 30      # report, then offer to delete artifacts >30 days
#   scripts/artifact-usage.sh --older-than 30 --yes   # same, no confirmation prompt
#
# Deletion ALWAYS prints the full list of what it is about to remove and waits for a typed "yes"
# unless --yes is passed. Nothing is deleted that has not been shown to you first.
#
# REQUIRES: gh (authenticated: `gh auth login`) and jq.

set -euo pipefail

OLDER_THAN=""
ASSUME_YES=0

while [ $# -gt 0 ]; do
  case "$1" in
    --older-than) OLDER_THAN="${2:-}"; shift 2 ;;
    --yes|-y)     ASSUME_YES=1; shift ;;
    -h|--help)    sed -n '1,40p' "$0"; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

if [ -n "$OLDER_THAN" ] && ! [[ "$OLDER_THAN" =~ ^[0-9]+$ ]]; then
  echo "--older-than takes a whole number of days (e.g. --older-than 30)" >&2
  exit 2
fi

for tool in gh jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "Missing required tool: $tool" >&2; exit 2; }
done

gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated. Run: gh auth login" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Reading the artifact store across every repo on the account..."
echo

gh api --paginate user/repos?affiliation=owner --jq '.[].full_name' | sort > "$WORK/repos.txt"
echo "Repos to check: $(wc -l < "$WORK/repos.txt" | tr -d ' ')"
echo

: > "$WORK/artifacts.tsv"   # repo <TAB> id <TAB> bytes <TAB> created <TAB> expired <TAB> name

while read -r repo; do
  # A repo with Actions disabled 404s here; that is normal and not an error worth stopping for.
  gh api --paginate "repos/$repo/actions/artifacts" --jq \
    ".artifacts[] | [\"$repo\", (.id|tostring), (.size_in_bytes|tostring), .created_at, (.expired|tostring), .name] | @tsv" \
    2>/dev/null >> "$WORK/artifacts.tsv" || true
  printf '.'
done < "$WORK/repos.txt"
printf '\n\n'

if [ ! -s "$WORK/artifacts.tsv" ]; then
  echo "No artifacts found on any repo."
  echo "If uploads are still failing, GitHub recalculates usage every 6-12 h — the number lags reality."
  exit 0
fi

TOTAL_BYTES=$(awk -F'\t' '{s+=$3} END {print s+0}' "$WORK/artifacts.tsv")
TOTAL_COUNT=$(wc -l < "$WORK/artifacts.tsv" | tr -d ' ')

echo "================ WHO IS HOLDING THE SPACE ================"
printf '%-42s %7s  %10s\n' "REPO" "COUNT" "SIZE"
awk -F'\t' '{c[$1]++; b[$1]+=$3} END {for (r in b) printf "%s\t%d\t%d\n", r, c[r], b[r]}' "$WORK/artifacts.tsv" \
  | sort -t$'\t' -k3 -rn \
  | while IFS=$'\t' read -r repo count bytes; do
      printf '%-42s %7d  %7.1f MB\n' "$repo" "$count" "$(echo "$bytes" | awk '{print $1/1048576}')"
    done
echo "----------------------------------------------------------"
printf '%-42s %7d  %7.1f MB\n' "TOTAL" "$TOTAL_COUNT" "$(echo "$TOTAL_BYTES" | awk '{print $1/1048576}')"
echo
awk -v b="$TOTAL_BYTES" 'BEGIN {printf "That is %.0f%% of the 500 MB free-tier store.\n", (b/524288000)*100}'
echo

if [ -z "$OLDER_THAN" ]; then
  echo "Report only — nothing was deleted."
  echo "To free space:  scripts/artifact-usage.sh --older-than 30"
  exit 0
fi

# ---- deletion path: list first, always ----
CUTOFF=$(date -u -d "$OLDER_THAN days ago" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
      || date -u -v-"${OLDER_THAN}"d +%Y-%m-%dT%H:%M:%SZ)   # GNU date, then BSD/macOS date

awk -F'\t' -v cutoff="$CUTOFF" '$4 < cutoff' "$WORK/artifacts.tsv" > "$WORK/stale.tsv" || true

if [ ! -s "$WORK/stale.tsv" ]; then
  echo "Nothing is older than $OLDER_THAN days. Nothing to delete."
  exit 0
fi

STALE_BYTES=$(awk -F'\t' '{s+=$3} END {print s+0}' "$WORK/stale.tsv")
STALE_COUNT=$(wc -l < "$WORK/stale.tsv" | tr -d ' ')

echo "============ ABOUT TO DELETE (older than $OLDER_THAN days) ============"
while IFS=$'\t' read -r repo id bytes created expired name; do
  printf '%-30s %8.1f MB  %s  %s\n' "$repo" "$(echo "$bytes" | awk '{print $1/1048576}')" "${created%T*}" "$name"
done < "$WORK/stale.tsv"
echo "-----------------------------------------------------------------------"
awk -v c="$STALE_COUNT" -v b="$STALE_BYTES" 'BEGIN {printf "%d artifacts, %.1f MB to be freed.\n", c, b/1048576}'
echo

if [ "$ASSUME_YES" -ne 1 ]; then
  printf 'Delete these %s artifacts? Type yes to confirm: ' "$STALE_COUNT"
  read -r reply
  [ "$reply" = "yes" ] || { echo "Cancelled. Nothing was deleted."; exit 0; }
fi

DELETED=0
FAILED=0
while IFS=$'\t' read -r repo id bytes created expired name; do
  if gh api -X DELETE "repos/$repo/actions/artifacts/$id" >/dev/null 2>&1; then
    DELETED=$((DELETED+1))
  else
    FAILED=$((FAILED+1))
    echo "  could not delete: $repo artifact $id ($name)" >&2
  fi
done < "$WORK/stale.tsv"

echo
echo "Deleted $DELETED artifacts."
[ "$FAILED" -gt 0 ] && echo "$FAILED could not be deleted (already expired, or no permission on that repo)."
echo "GitHub recalculates storage usage every 6-12 h, so the quota may not free up immediately."
