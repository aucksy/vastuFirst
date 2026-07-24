# VastuFirst — Phase 3 refinement plan (numbered)

**Base:** v0.2.1 (audit round 1 shipped — see `PHASE-3-AUDIT.md`). **Milestone:** 4 Aug 2026 client
delivery. **Rule:** don't touch the engine before screens unless an item explicitly says engine.
Every item is a cloud build → green CI → tag. Ranked by impact; accuracy first.

Sources this folds in: the accuracy risk map (`SCORE-ACCURACY-CAVEATS.md`), the Phase-2 known-minors
(`PHASE-2-PROGRESS.md`), and the audit deferrals (`PHASE-3-AUDIT.md`).

## A. Accuracy — stop the silent wrong-score cases (highest priority)

1. **Outline capture for L-shaped / notched homes** *(caveat #1 — the worst: silent + too generous +
   common)*. Today the footprint = bounding box of rooms, so a missing corner is scored as filled.
   Add a way to capture the true footprint (trace-the-outline step, or infer the rooms' union) and
   feed it to the engine so cut / missing-corner / extension checks fire. **Needs engine-side
   validation** (union can over-report on gappy layouts) — treat as the one item that may touch the
   engine/geometry, gated on tests. **Build first.**
2. **Compass helper on Mark North** *(caveat #3)*. Add the device-compass assist (omitted in Ph2) +
   a "double-check your North" confirmation, since a wrong North silently shifts every zone.
3. **Coverage framing** *(caveat #4)*. The score only reflects rooms + door + shape (no fixtures/site).
   Label the free score honestly ("based on your rooms, door & shape") and/or add an optional step to
   collect key fixtures/site (septic, water, roads, stair-under) so more rules can run.
4. **Placement resolution** *(caveat #2)*. The 8-cell editor is coarser than the 9-pada zones. Add a
   finer grid or a zone-snap nudge, or at minimum a note that placement is approximate near a boundary.

## B. Robustness

5. **Draft survives process death** — persist the in-progress draft (rooms/door/North/intent) via
   `SavedStateHandle` (today only an unsaved draft is lost under "don't keep activities"/low memory).
6. **Forward-compatible persistence** — tolerant DB decode (quarantine a bad row instead of crashing
   the list) + a versioned SQLDelight migration before any schema change.

## C. UX / review-gate follow-ups

7. **Resize-by-drag** option on the grid editor (currently steppers only) — same outcome, more direct.
8. **Physical-device pass** at 360 dp width + a real TalkBack run (the audit was static; this is the
   on-device confirmation).

## D. Owner-gated (parked at owner's request — still needed before 4 Aug)

9. **Report price** — confirm ₹699 or change (data-only).
10. **The 8 expert rulings (§13)** — config/data edit, no rebuild. Two of them move the score
    (M-05 Brahmasthan extent, M-07 door method); the rest surface as "schools disagree." Owner sends
    the expert's answers in any format → applied to `rules/.../ruleset/*.json`.

## Explicitly Phase 4+ (NOT Phase 3)
16-zone toggle made real, the 5 non-English languages, AI plan reading, payments (Razorpay), iOS.

## Immediately pending
Owner is sharing a **few fixes for v0.2.1** first — apply those, then proceed down this list from A1.
