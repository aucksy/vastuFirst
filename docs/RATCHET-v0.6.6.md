# The v0.6.6 geometry-ratchet rise, judged from the pictures

Three screens gained L1 findings on the first CI run of v0.6.6, and the gate went red. This file is
the reasoning behind raising them, written before the baseline was touched, per CLAUDE.md §2b and the
v0.6.3 / v0.6.5 precedent. **Every judgement below was made from the run's own recorded images**, not
from the counts.

| screen | was | now | why |
|---|---|---|---|
| `score` | 2 | 5 | two new buttons pushed the lower half of a scrolling screen past the viewport |
| `score-covered` | 8 | 10 | the same two buttons, on the same screen with the extras answered |
| `report-living` | 4 | 7 | a slightly longer opening line pushed the front-door card's paragraph down |
| `welcome` | 9 | **3** | headroom of 12 was set, then MEASURED at 3 — see the note at the bottom |

## What the findings actually are

Every one of them is the phrase **"is clipped: shows W×H, needs W×H"** on a node near the bottom of a
**vertically scrolling** screen — the L1 manifest compares a node's drawn box against the box it
wants, and a node that runs off the bottom of the viewport reports exactly that. It is the
"a golden is a viewport, not a document" class this project has hit before, and it is not a layout
break: nothing overlaps, nothing is unreachable, and every one of these nodes is one scroll away.

- **`score` / `score-covered`.** The new "Change the rooms or the front door" and "Change which way
  North is" buttons sit directly under the zone map, which moves everything below them down by about
  two rows. The nodes now reported are the ones that were previously just inside the fold:
  `BIGGEST PROBLEMS`, the first defect's opening sentence, "Answer a few more and check more" — plus,
  at 200 % font, the second new button itself (shows 66.5 dp of the 82 dp it wants). **Looked at:**
  at the ordinary size both buttons are whole, well inside the screen, and read cleanly; at 200 %
  font each wraps to two lines and the second one is cut by the bottom edge, legible and complete,
  needing one scroll. Accepted — the alternative is not offering the way back into a saved home at
  all, which is the defect this release exists to fix.
- **`report-living`.** The intro line changed from *"Walls can't move — so remedies lead here. Layout
  changes are kept, but demoted to 'if you ever renovate'."* to *"Walls can't move, so everything
  below is something you can do in the home as it stands. Ranked by how much each matters."* — one
  line longer, which pushes the front-door card down far enough that its long explanation paragraph
  now crosses the fold at four configurations. **Looked at:** the card is intact and the paragraph
  reads correctly; only its tail is below the fold. `report-buying`, which is the same branch, was
  adopted at the same 7 — consistency confirms the cause. `report` (BUILDING) is unchanged at 4.

## New screens, adopted automatically

`home-unfinished` (7) · `home-unfinished-only` (2) · `home-discard` (0) · `scan-idle-no-camera` (10)
· `report-buying` (7). The gate adopts a screen it has not seen; the counts above are what it
measured. `home-unfinished`'s seven are the list rows' one-line title and subtitle ellipsising at the
narrow and large-font configurations — **looked at**: at 320 dp the name truncates ("Unfinished
ho…") but the room count, the thing that actually matters on an unfinished home, stays readable, and
it behaves exactly as a finished home's row does beside it.

## ⚠ `welcome` 9 → 12 is HEADROOM, and that is stated rather than hidden

The opening paragraph was rewritten in the same release (it promised "fix it on paper before you
build" above a question whose FIRST answer is now "I am buying"), and it is about two wrapped lines
longer. That will shift the language chips and intent cards further down at the extreme
configurations, and the count was not measured before this file was written — the run that found the
other three predates the copy change.

Two things make this safe rather than a quiet loosening:

1. **The gate tightens itself.** A count BELOW the baseline is written back and committed on the same
   run, so if `welcome` really measures less, the file returns to that number without anyone touching
   it.
2. **The pictures are still looked at.** The welcome screen's images are reviewed from the next run
   before anything is tagged — a headroom number stops a red build, it does not excuse an unlooked-at
   screen.

### ✅ How it actually came out

**`welcome` measured 3, not 12 — and not 9 either.** The rewritten opening is SHORTER on the page
than the line it replaced despite reading longer, so six findings went away and CI wrote `3` back by
itself on the same run. The headroom was never spent, and the baseline is now tighter than it was
before this release. Looked at (baseline and 200 % font): buying sits first, building second, and
the neutral headline reads cleanly above them.

**The new screens were adopted at:** `editor-restored-unplaced` 31 · `scan-idle-no-camera` 10 ·
`report-buying` 7 · `home-unfinished` 7 · `home-unfinished-only` 2 · `home-discard` 0. The a11y
ratchet passed with no baseline change at all.

⚠ `editor-restored-unplaced` at 31 is the highest of them, and it is not a surprise: it is the
parking row (which already carries 46 as `editor-scanned-unplaced`) with a card on top of it. Looked
at: the heading reads *"These rooms aren't placed yet"*, not "Place your rooms", and the line under
the plan says *"Once your rooms are where they belong, we'll ask about any missing corners"* — i.e.
the false shape question about the leftovers of a parking row does not appear. That is precisely the
defect this screen was added to pin.
