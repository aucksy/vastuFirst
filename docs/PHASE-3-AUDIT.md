# VastuFirst — Phase 3 end-to-end audit + fixes (round 1)

**Date:** 2026-07-23 · **Base:** v0.2.0 (`ad95b42`) · **Ships as:** v0.2.1
**Method:** four parallel read-only audits (score-conversion, review-gate/a11y, nav/state/persistence,
product-rules/no-error-state) + first-hand review of the grid→engine seam. Cloud-build only.

## Headline

The two things that would have been disasters are clean:
- **Score math is correct.** The grid→engine conversion (row-flip, door, North, off-main debounce,
  room-type mapping) is faithful — cross-checked by reproducing the engine's own `sample-01`
  fixture through `buildPlan()`. No P1/P2 in the conversion.
- **Design system is spotless.** Zero token violations (no raw hex / dp / sp outside the theme
  package). Colour-alone, compass-centre, and no-best-angle product rules all pass.

Every real defect was the **UI discarding the engine's safety data**, not the engine failing.

## Fixed in v0.2.1

### 🔴 P1
1. **No-scary-error rule was defeated in the UI.** The engine returns a friendly `notes` message +
   `quality = INSUFFICIENT` for an unreadable plan, but Score/Report never read `quality`/`notes`,
   so an INSUFFICIENT plan rendered as a red **0 / 100** with contradictory text and an empty map.
   *Fix:* `ScoreScreen`/`ReportScreen` now branch on `quality` — INSUFFICIENT → a `GuidanceState`
   ("Let's finish your plan" + the engine's message + a button back to the grid); `null` →
   `LoadingState`, never a bare 0. (`ScoreScreen.kt`, `ReportScreen.kt`, `Feedback.kt` LoadingState.)
2. **Stored list-view score could disagree with the plan.** `save()` read the debounced cache
   (`_analysis.value`), which can lag a North change or be `null` (→ stored score 0) for a valid
   plan. *Fix:* `save()` now `engine.analyze(plan)` on the exact plan being persisted, inside the
   save coroutine — `storedScore == analyze(storedPlan).score` by construction. (`NewPlanViewModel.kt`.)

### 🟠 P2
3. **DEGRADED / unusual-shape / tilt / elongated notes silently dropped** → now shown as a quiet,
   non-alarming `NotesStrip` near the top of Score and Report. (`AnalysisFeedback.kt`.)
4. **Reopened saved home showed a blank zone map** (`load()` didn't repopulate `rooms`). *Fix:*
   `load()` rebuilds the grid rooms + door from the stored `Plan` (exact inverse of `buildPlan()`).
5. **`ruleSetVersion` stored but never compared** → silent re-score on reopen (violates §5). *Fix:*
   `load()` compares saved vs current ruleset version and refreshes the stored score/version if they
   differ, instead of silently showing a number from a different ruleset.
6. **Score verdict ignored intent** ("…on paper" shown to LIVING). *Fix:* `verdictLine` branches on
   intent — LIVING points to remedies, drops "on paper".
7. **Unlock CTAs implied a real charge.** "Pay ₹699 & unlock" → "Unlock on this device"; the Score
   unlock card gained the "no payment taken yet — unlocks on this device" preview note.
8. **A11y — unlabelled icon buttons** (Home ⚙, Settings/Legal back) now carry `Role.Button` +
   `contentDescription` (new `IconTapButton` component, 48dp). **Sub-48dp targets** (degree steppers,
   grid size chips) raised to the 48dp floor with spoken labels. **Slider** gained
   `contentDescription` + `progressBarRangeInfo`. **Buttons** `.height` → `.heightIn(min=)` so long /
   translated labels don't clip.

### ⚪ Polish also applied
- Provenance tag added to "already right — leave alone" rows (every shown rule now tagged).
- "Not assessed" → "Couldn't check these yet"; traditional-guidance disclaimer added to the free
  Score screen; small explanatory text moved off the lowest-contrast tertiary grey where it read.
- "Delete all my data" now asks for confirmation (owned `Dialog`), emphasising the safe choice.

## Deferred (not silently changed — needs owner input or its own pass)

- **L-shaped / notched homes are scored as a filled rectangle.** `buildPlan()` synthesises the
  footprint as the rooms' bounding box, so a genuinely missing corner never triggers the engine's
  cut/missing-corner checks — the score can be slightly generous for non-rectangular homes (common
  in India). A correct fix needs an outline-capture step (or a rooms-union footprint, which risks
  over-reporting on gappy layouts). **Flagged for the Phase 3 refinement plan**, not hot-patched,
  because it moves scores and deserves engine-side validation.
- **Draft lost on process death** (unsaved draft only; rotation is safe). Persist via
  `SavedStateHandle` — a Phase 3 robustness item.
- 8-cell editor vs 9-pada zone resolution; no DB migrations yet; forward-compat enum decode.

## Not changed (confirmed correct)
Score conversion, token discipline, no-best-angle, intent branch in the report body, disputes not
penalised, DI/context, nav back-stack, lifecycle-aware collection, serialization round-trip.
