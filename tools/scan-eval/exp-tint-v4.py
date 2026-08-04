#!/usr/bin/env python
r"""
The tint-box experiment (owner request, 4 Aug 2026): does asking the reader for ONE more
rectangle — the building's outer wall as a box on the PAGE — let the on-photo review draw
tints the right size?

Why: the on-photo review tints reply.room boxes across the WHOLE photo, but the prompt
defines them as fractions of the BUILDING (ScanReviewScreen.kt header admits this).
Measured on stored replies (4 Aug 2026, free): the boxes claim 72–97 % of the page,
and their centres sit up to 0.28 of the sheet away from the rooms' true centres on
margin-heavy sheets — while a full-bleed plan (plan-006) lands centres at 0.008.
The frame, not the reader, is the error. Prompt v4 (prompt-v4-building-box.txt) adds
`"building":{x,y,w,h}` measured on the image; page position = building ∘ room.

⚠ HARD RULE (CLAUDE.md §2c): every invocation below is a PAID scan. This script prints
the exact count and cost and REFUSES to run without --approved. The owner's yes covers
the printed count only.

    python tools/scan-eval/exp-tint-v4.py            # print the plan + count, run nothing
    python tools/scan-eval/exp-tint-v4.py --approved # run exactly that plan

Replies land in out/live/<stem>.<model>.v4.json — the live class, never the frozen corpus.
Grade afterwards with exp-tint-v4-grade.py (free, offline).
"""
import os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
PLANS = os.path.join(ROOT, "Documents", "Sample Floor plans")
PROMPT = os.path.join(HERE, "prompt-v4-building-box.txt")

LUNA = "openai/gpt-5.6-luna"          # ~Rs 0.09 a scan (READER-CANDIDATES.md)
GEMINI = "google/gemini-3.1-pro-preview"  # ~Rs 1.4 a scan

# The six truth-annotated sheets: graded against paper truth, two runs for stability.
TRUTH_SHEETS = [
    "greencourt-336-clean.png", "greencourt-336-branded.png", "greencourt-526.png",
    "plan-006.webp", "plan-007.jpg", "plan-010.webp",
]
# More real sheets, one run each; graded on the truth-free metrics (building-box
# sanity, boxes-vs-building area, box-on-box overlap). towerEF-1854 also has truth
# centres. Sheets that refuse as 3D still consume their scan (§2c counts failures).
OTHER_2D = [
    "towerEF-1854.jpg", "plan-001.jpg", "plan-002.webp", "plan-003.jpg", "plan-004.jpg",
    "plan-005.webp", "plan-008.webp", "plan-009.webp", "plan-011.webp", "plan-012.jpg",
    "plan-014.jpg", "plan-015.webp", "plan-016.jpg", "plan-017.webp", "plan-019.jpg",
    "3bhk_1605.jpg", "hlv9vurn.jpg",
]

def main():
    jobs = []
    for s in TRUTH_SHEETS:
        jobs += [(s, LUNA, "v4a"), (s, LUNA, "v4b")]
    for s in OTHER_2D:
        jobs += [(s, LUNA, "v4a")]
    for s in TRUTH_SHEETS:
        jobs += [(s, GEMINI, "v4a")]

    luna = sum(1 for j in jobs if j[1] == LUNA)
    gem = sum(1 for j in jobs if j[1] == GEMINI)
    print("PLAN: %d scans total = %d on %s (~Rs %.1f) + %d on %s (~Rs %.1f) = about Rs %.0f"
          % (len(jobs), luna, LUNA, luna * 0.09, gem, GEMINI, gem * 1.4,
             luna * 0.09 + gem * 1.4))

    if "--approved" not in sys.argv:
        print("\nNOT RUNNING. This is the §2c count for the owner's yes. "
              "Re-run with --approved after he approves exactly this count.")
        return 0

    for i, (sheet, model, tag) in enumerate(jobs, 1):
        img = os.path.join(PLANS, sheet)
        print("[%d/%d]" % (i, len(jobs)), sheet, model, tag)
        subprocess.run([sys.executable, os.path.join(HERE, "scan-candidate.py"), img,
                        "--model=" + model, "--tag=" + tag, "--prompt=" + PROMPT],
                       check=False)
    print("done — grade with exp-tint-v4-grade.py")
    return 0

if __name__ == "__main__":
    sys.exit(main())
