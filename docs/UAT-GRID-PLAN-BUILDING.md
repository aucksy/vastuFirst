# UAT — Guided-Grid Plan Builder (exhaustive)

**Purpose.** The owner is seeing too many issues when building a home on the grid. This is an
adversarial, not-happy-path QA catalogue of **everything a user can do on the guided grid**, with a
route to prove each case in CI (or a labelled device-only fallback).

**Scope.** The "Place your rooms" / "Mark your front door" editor:
`GuidedGridScreen.kt` / `GuidedGridContent`, its state in `NewPlanViewModel.kt`
(`updateRooms` / `updateDoor` / `updateGrid` / `save` / `load` / autosave), the grid→engine flip in
`PlanConversion.kt`, and the pure editing maths in `shared/…/editor/GridEditing.kt`.

---

## How to read the Status column

The Status here is a **provisional, code-reading assessment** — the point of Step 2 is to replace it
with a real green/red from CI. Values:

| Mark | Meaning |
|---|---|
| `✅ exp-pass` | Code reading says this should pass; a Step-2 test will prove it. |
| `⚠ SUSPECT` | Code reading found a plausible defect or ambiguity. **Priority to test first.** |
| `📱 device` | Needs raw finger gesture / haptic / real process-death — can't be faked headlessly. Goes in the Owner device-test checklist. |
| `🚧 gap` | Behaviour is a known limitation or unimplemented; expected, documented, not a regression. |

**Test route** column: `shared` = pure `:shared` unit test; `convert` = `buildEnginePlan` ↔ inverse
round-trip test; `compose` = headless `runComposeUiTest` driving `GuidedGridContent` /
`NewPlanViewModel`; `render` = Roborazzi screenshot / L1 manifest; `owner` = device checklist.

---

## 0. Suspected-defect shortlist — with CI verdicts (2026-07-25)

Read the code, wrote a test for each, ran it on CI. Verdicts:

- **S1 (J3) — score translation-invariance on a tall plot.** ✅ **NOT A BUG (proven).** The engine
  lays its pada grid on the rooms' bounding box, so position isn't a scoring term. The same home
  scores identically wherever it sits, including rows 8–9 where engine-Y goes negative.
  (`PlanConversionRoundTripTest` — passes.)
- **S2 (I5) — plot proportions lost on reopen.** ⚠ **CONFIRMED, real but low-severity.** `load()`
  re-derives the *tightest* grid around the rooms, so any empty plot margin the user drew beyond the
  rooms is not restored. Rooms + score are exact; only the surrounding canvas shrinks. Pinned by
  `GridResizeTest`. **Fix needs a saved-data change → owner proposal (below).**
- **S3 (K4) — unsaved draft lost on process death.** ⚠ **CONFIRMED (code).** A brand-new draft isn't
  autosaved and the VM holds it in plain state (no SavedStateHandle); a low-RAM OS kill loses the
  rooms while `selectedId` restores dangling. **Owner proposal (below).**
- **S4 (H7) — door mode / button with zero rooms.** ✅ **FIXED (Batch 1).** The door button is now
  hidden on the empty grid (dead-end removed). `GuidedGridInteractionTest` asserts it's absent.
- **S5 (E9) — stepper vs drag resize asymmetry.** ✅ **NOT A BUG (intended).** Steppers grow toward
  bottom-right; a "Wider" press flush to the east wall is a clean no-op (clamped), never off-grid.
  (`GridEditingRectTest` — passes.)
- **S6 (H6) — door side re-classification on a thin footprint.** ✅ **NOT A BUG (proven).**
  `gridDoorFromPlan` never mislabels N↔S or E↔W on 1-cell-deep/wide footprints.
  (`PlanConversionRoundTripTest` — passes.)
- **S7 (G4-family) — plot-shrink silently shrinks a lone oversized room.** ⚠ **CONFIRMED, benign.**
  A room wider/taller than the shrunk plot is clamped to fit (no overlap, so the score isn't
  *corrupted*) rather than the resize being refused. Pinned by `GridEditingRectTest`. Arguably correct
  (a room can't exceed its plot); left as-is, documented.
  ⚠ **Correction (v0.3.10):** an earlier note here said "score intact". That is wrong — a room that is
  clamped smaller *does* move the score, because the engine scores the rooms. Nothing is corrupted and
  no rooms overlap, but the user's number changes with no notice. The behaviour is unchanged (it is a
  judgement call: shrink the room, or refuse the plot change as an infeasible pack already does), and
  the **silent** half is now partly closed — a plot key that outright refuses buzzes (see below).
  Worth an owner decision alongside S2 if they'd rather the plot key refused than resized the room.
- **F4 — door not re-clamped when a room is removed/moved.** ✅ **FIXED (Batch 1).** The door now
  follows the footprint on any room edit, so displayed == scored == reloaded (no jump). Pinned by
  `GridResizeTest`.

---

## A. Empty state → first room

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| A1 | Empty grid is visible & non-zero | Open editor with no rooms | Square 8×8 grid drawn, caption "Pick a room below…", grid has non-zero measured height (the v0.2.1 regression) | render (`editor-empty`) | ✅ exp-pass |
| A2 | Empty-state copy | Same | Title "Place your rooms"; body "Pick a room below, then press the plan to place it." | compose | ✅ exp-pass |
| A3 | Place the very first room | Arm a type, press grid, lift | Room appears, lands **already selected**, chip shows "type · w×h · zone", armed clears | compose + render (`editor`) | ✅ exp-pass |
| A4 | "Next" disabled until a room exists | Empty vs one room | "Next — mark North" disabled at 0 rooms, enabled at ≥1 | compose | ✅ exp-pass |
| A5 | `canScore()` gate | 0 rooms then 1 | `vm.canScore()` false→true | compose/vm | ✅ exp-pass |
| A6 | `buildPlan()` null when empty | No rooms | `buildEnginePlan` returns null (no plan, no score) | convert | ✅ exp-pass |

## B. Placing rooms — every type, and many

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| B1 | Each room type places | For every `GRID_ROOM_TYPES` entry: arm, place | Every type lands with its label + editor colour; `type` preserved into the engine `Room` | compose + convert | ✅ exp-pass |
| B2 | Default ghost size 2×2, trimmed at edges | Place near S/E edge | Ghost is 2×2 where it fits, `min(2, cols−col)` / `min(2, rows−row)` at the edge (never off-grid) | shared (`placementAt` logic) / compose | ✅ exp-pass |
| B3 | Place many rooms (fill the grid) | Place 8–16 rooms tiling the grid | All persist, none overlap, unique ids (`newRoomId` never collides after deletes) | compose + shared | ✅ exp-pass |
| B4 | 1×1 room is placeable & labelled | Shrink a ghost / place in a 1-cell gap | 1×1 lands; single BR grip (`handlesFor`); no text label inside (chip/TalkBack name it) | shared + compose | ✅ exp-pass |
| B5 | Placing onto an occupied cell is refused | Arm, press over an existing room, lift | Ghost turns red, chip "Rooms can't overlap", **nothing is added** on lift | compose | ⚠ SUSPECT → prove refuse-on-lift |
| B6 | Lift on a blocked ghost gives reject feedback, no room | As B5 | `onRoomsChange` never called; reject haptic path | compose | ⚠ SUSPECT |
| B7 | id survives delete-then-add in same cell | Place, delete, place again | New id (`room-n` scan skips taken ids), no ghost id reuse bug | shared (`newRoomId`) | ✅ exp-pass |

## C. Overlap prevention (the score-integrity invariant)

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| C1 | Rooms sharing only a wall don't overlap | Adjacent 2×2 rooms | `overlaps` false (touching edge is legal) | shared (exists) | ✅ pass |
| C2 | Rooms sharing area overlap both ways | 1-cell shared | `overlaps` true symmetric | shared (exists) | ✅ pass |
| C3 | Move a room INTO another (drag) | Drag over neighbour | Preview freezes at last clear cell; red preview + "can't overlap"; commit uses the clear rect | compose + shared | ⚠ SUSPECT → prove drag-block |
| C4 | Move INTO another via arrows | Select, press arrow toward neighbour | `applyToSelected` refuses (overlap), reject haptic, room does not move | compose | ✅ exp-pass |
| C5 | Resize INTO another (drag grip) | Pull grip across neighbour | Blocked at last clear size; red preview; commit is the clear rect | compose | ⚠ SUSPECT |
| C6 | Resize INTO another via steppers | "Wider"/"Taller" toward neighbour | `applyToSelected` refuses; no change | compose | ✅ exp-pass |
| C7 | The buried-room double-count can never occur | Any op | No API path (place/move/resize/plot-resize) ever yields an overlapping list | shared + compose | ✅ exp-pass |

## D. Move — drag, arrows, clamping

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| D1 | Drag drift into a wall and back | Push to corner, drag back | Room tracks finger exactly, returns to origin (frozen-start invariant) | shared (exists: drift test) | ✅ pass |
| D2 | Move clamps at all four plot edges | moveBy huge deltas | Stays fully in grid; `coerceIn(0, cols−w)` / `(0, rows−h)` | shared (exists) | ✅ pass |
| D3 | Arrows ◀▲▼▶ move one cell | Select, each arrow | Moves 1 cell in the right direction (`moveBy(±1)`) | compose | ✅ exp-pass |
| D4 | Arrow at the wall is a no-op | Room at left wall, press ◀ | No move; (note: silent, no haptic — see S-note) | compose | ✅ exp-pass |
| D5 | Arrow move respects non-square bounds | On an 8×5 plot, move to S edge | Clamps at `rows−h`, not `cols−h` | compose/shared | ✅ exp-pass |
| D6 | 1×1 room still movable by body/arrows | Place 1×1, move | Moves freely | shared (exists) | ✅ pass |
| D7 | Move a non-selected room selects it first | Touch an unselected room and slide | Selects on down, moves same gesture | 📱 device (touch-slop) | 📱 device |

## E. Resize — grips, steppers, min/max, inversion

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| E1 | Each corner pins the opposite corner (drag) | Pull each of 4 grips | Opposite corner fixed (`resizeBy`) | shared (exists) | ✅ pass |
| E2 | Top-left drag past bottom-right does NOT invert | Pull TL by +99,+99 | Collapses to 1×1 at the pinned corner, never negative size | shared (exists) | ✅ pass |
| E3 | Resize clamps to grid | Pull BR/TL beyond edge | Stays in grid | shared (exists) | ✅ pass |
| E4 | Min size 1×1 | Shrink hard | Never below 1×1 | shared (exists) | ✅ pass |
| E5 | 1×1 room resizes by its single grip | Pull BR of a 1×1 | Grows from BR | shared (exists) | ✅ pass |
| E6 | Resize clamp returns exactly on the way back | Over-pull then back | Pure-of-frozen-start, exact return | shared (exists) | ✅ pass |
| E7 | Stepper W−/W+/H−/H+ resize | Select, press each | ±1 in each dimension, min 1, capped at `cols−col`/`rows−row` | compose | ✅ exp-pass |
| E8 | Stepper resize refused on overlap | Grow toward neighbour | Rejected, no change | compose | ✅ exp-pass |
| E9 | Stepper "Wider" flush to east wall | Room right edge at `cols`, press W+ | Silent no-op (`coerceIn(1, cols−col)` caps it) — confirm intended | compose | ⚠ SUSPECT |
| E10 | Max size = whole plot | Grow to fill | Reaches `cols×rows`, not beyond | shared/compose | ✅ exp-pass |

## F. Remove a room · palette scroll position

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| F1 | Remove selected room | Select, "Remove" | Room gone, selection cleared, score recomputes | compose | ✅ exp-pass |
| F2 | Remove last room | One room, remove | Back to empty state; "Next" disables; `buildPlan` null | compose | ✅ exp-pass |
| F3 | Palette scroll not reset after a place | Scroll palette right, place a room, deselect | Palette returns at the same scroll offset (`paletteScroll` hoisted) | compose | ⚠ SUSPECT → prove offset held |
| F4 | Remove then the door-footprint shifts | Room removed changes the bounding box | Door re-snaps to new footprint on next build (no orphan door outside outline) | convert | ⚠ SUSPECT |

## G. Plot size — widen, deepen, shrink, re-pack, refuse, bounds, non-square

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| G1 | Widen / deepen (pure grow) | +wide, +deep | Canvas grows; **no room moves, score unchanged, no autosave bump** (`changed==false`) | compose/vm | ✅ exp-pass |
| G2 | Shrink with slack | Rooms far from edge, shrink | Grid shrinks, rooms stay put | compose/vm | ✅ exp-pass |
| G3 | Shrink that would stack two rooms re-packs | Two rooms, shrink so naive clamp collides | `fitWithoutOverlap` relocates the later one; never overlap | shared (exists) | ✅ pass |
| G4 | Shrink below what rooms need is REFUSED | Rooms fill the grid, press "−" | Resize refused (`fitWithoutOverlap`→null→return), grid unchanged, no overlap, no drop | shared (exists) + compose | ✅ exp-pass |
| G5 | Min plot 4×4 | Press "−" repeatedly | Stops at `MIN_GRID=4` | compose/vm | ✅ exp-pass |
| G6 | Max plot 10×10 | Press "+" repeatedly | Stops at `MAX_GRID=10` | compose/vm | ✅ exp-pass |
| G7 | Non-square plot (8×5, 5×9) | Set unequal cols/rows | Square cells, true aspect ratio, per-axis band lines | render (`editor-wide`) | ✅ exp-pass |
| G8 | Door on a wall a shrink removes is cleared | Door on S, shrink so that row is gone / cell out of range | `door=null` on resize (`fits` check) | compose/vm | ⚠ SUSPECT → prove clear |
| G9 | Shrink re-pack still fits the door | Rooms re-pack, door still on a valid wall | Door kept if its cell still in range | compose/vm | ✅ exp-pass |
| G10 | A pure grow does not recompute score | Grow only | `_analysis` instance unchanged (perf/F2) | vm | ✅ exp-pass |
| G11 | Feasible tight squeeze keeps full sizes | 4×2×2 into 4×4 | All rooms full size, no overlap | shared (exists) | ✅ pass |

## H. Front door — every wall, past rooms, none, move, re-place

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| H1 | Door on N wall | Door mode, tap near top | Marker on N wall; engine door on north edge of footprint | compose + convert | ✅ exp-pass |
| H2 | Door on E / S / W walls | Tap near each | Correct side chosen by nearest-wall (`distN/E/S/W`) | compose + convert | ✅ exp-pass |
| H3 | Door near rooms (within footprint) | Tap on the footprint edge | Placed at that cell, stable on reload | convert | ✅ exp-pass |
| H4 | Door PAST the rooms snaps to footprint | Tap outside the rooms' bbox | Clamped to footprint at placement (C15); displayed==stored==reloaded (no jump) | compose + convert | ⚠ SUSPECT → prove no-jump |
| H5 | Move the door | Place, re-enter door mode, tap elsewhere | Door relocates; only one main entrance | compose | ✅ exp-pass |
| H6 | Door side survives round-trip on thin footprint | 1-cell-deep footprint, door N vs S | `gridDoorFromPlan` never mis-labels N↔S / E↔W | convert | ⚠ SUSPECT (S6) |
| H7 | Door mode with zero rooms | Enter door mode, tap | `placeDoor` returns early — silent no-op with a "tap the wall" prompt | compose | ⚠ SUSPECT (S4) |
| H8 | Re-place door after a plot shrink cleared it | G8 then set again | New door places cleanly on the smaller plot | compose | ✅ exp-pass |
| H9 | Door "Set/Move" button label | No door vs door present | "Set the front door" ↔ "Move the front door" | compose | ✅ exp-pass |

## I. Reopen / reload round-trip (exact recovery)

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| I1 | Rooms round-trip exactly | Build → `buildEnginePlan` → `gridRoomsFromPlan` | Every room's col/row/w/h/type recovered identically | convert | ⚠ SUSPECT → prove exact |
| I2 | Door round-trips exactly | Build door → plan → `gridDoorFromPlan` | Same side + cell recovered | convert | ⚠ SUSPECT |
| I3 | Round-trip at MAX plot (10×10), rooms high on grid | Rooms at rows 8–9, reopen | Rooms recover despite negative engine Y (fixed `GRID=8` origin cancels both ways) | convert | ⚠ SUSPECT (S1-adjacent) |
| I4 | Round-trip after a resize repack | Repack, save, reopen | Recovered rooms == on-screen rooms | convert | ✅ exp-pass |
| I5 | Plot proportions after reopen | 10-wide plot, rooms reach col 6, reopen | Grid re-derives to the tightest box (≈6-wide) — **proportions not persisted** | convert/vm | ⚠ SUSPECT (S2) — likely documented limitation |
| I6 | Reopen re-scores under a new ruleset | Saved under old version, reopen | Score + version refreshed (`load()` §5) | vm | ✅ exp-pass |
| I7 | Empty polygon room is skipped on load | Degenerate stored room | `gridRoomsFromPlan` drops it (mapNotNull), no crash | convert | ✅ exp-pass |

## J. Live score · bounding-box / translation invariance

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| J1 | Score updates live while editing | Place/move/resize | `analysis` recomputes (debounced 50 ms), never throws | vm | ✅ exp-pass |
| J2 | Score is bounding-box based | Same shape, different grid size | Grid size never enters the score (RECT-PLOT-RESEARCH) | convert + engine | ✅ exp-pass |
| J3 | **Score is translation-invariant** | Same room set placed top-left vs bottom-right (incl. rows 8–9 → negative Y) | **Identical score** both places | convert + engine | ⚠ SUSPECT (S1) — top priority |
| J4 | Engine never errors | Any draft incl. 1 room, whole-grid room | Total function, no "not assessed"/error state | vm/engine | ✅ exp-pass |
| J5 | Sample-01 still scores 31 | Bundled sample | Exactly 31 (Phase-1 exit gate) | convert + engine (exists) | ✅ pass |

## K. Rotation / process death

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| K1 | Rotation keeps selection | Select room, rotate | `selectedId` restored (`rememberSaveable`) | 📱 device / compose-saver | 📱 device |
| K2 | Rotation keeps door step / armed room | In door mode or armed, rotate | `doorMode` / `armedType` restored | 📱 device | 📱 device |
| K3 | Rotation keeps the draft (saved home) | Edit a saved home, rotate | Rooms/door survive (VM survives config change; autosave covers) | 📱 device / vm | ✅ exp-pass |
| K4 | **Process death on an UNSAVED draft** | New draft (not yet at Mark North), OS kills app, return | **Rooms are LOST** (no SavedStateHandle, autosave gated on planId); `selectedId` restores dangling | code + 📱 device | ⚠ SUSPECT (S3) — real gap |
| K5 | Process death on a saved home | Saved home edited, killed, reopened | Autosave (planId≠null) persisted the last edit; reopen restores | vm / 📱 device | ✅ exp-pass |

## L. Accessibility

| ID | Scenario | Steps | Expected | Route | Status |
|---|---|---|---|---|---|
| L1 | Room tiles announce as buttons | TalkBack over a room | Role.Button, contentDescription "type, w by h cells, zone", state "Selected/Not selected" | compose/a11y | ✅ exp-pass |
| L2 | Every drag action has a button path (WCAG 2.2 SC 2.5.7) | Move/resize | Arrows + steppers do everything a drag does | compose | ✅ exp-pass |
| L3 | TalkBack can place & move via buttons | Arm, place, arrow, resize | Achievable without a raw gesture | compose | ✅ exp-pass |
| L4 | Editor keys are 48 dp & labelled | Measure arrow/stepper/plot keys | ≥48 dp touch target, spoken `contentDescription`, glyph decorative | render L1 manifest / a11y | ✅ exp-pass |
| L5 | Door marker labelled | TalkBack on door | "Front door on the N/E/S/W wall" | compose/a11y | ✅ exp-pass |
| L6 | Palette chips are reachable & labelled | TalkBack across palette | Each room type labelled, chip is a button | a11y | ✅ exp-pass |
| L7 | Font-scale 2.0 doesn't clip/overlap | Render at 200% | Two-row plot + size controls don't overflow 320 dp; labels ellipsise, don't collide | render (font2_0) | ✅ exp-pass |
| L8 | 320 dp width layout holds | Render at 360/320 dp | Grid + toolbar fit; no horizontal clip | render | ✅ exp-pass |
| L9 | Compass never mirrors under RTL | Render ar-XB | NORTH/WEST/SOUTH/EAST stay LTR (locked) | render (ar-XB) | ✅ exp-pass |
| L10 | ATF a11y gate (contrast/targets/labels) | a11y baseline | No new ATF findings on editor/editor-empty/editor-wide | a11y baseline | ✅ exp-pass |

---

## Owner device-test checklist (can't be faked headlessly)

⭐ **MOVED — the canonical running list is now `docs/DEVICE-TEST-CHECKLIST.md`.** It is the single
place manual tests accumulate (appending to it is part of "done" for any fix a machine can't prove),
it is written for the owner's phone, and it carries every item below plus the ones added since.
**Add new manual tests there, not here**, so there is never a second, staler list. The items below are
kept only as the UAT catalogue's own record of which cases route to a device.

Raw finger gestures, real haptics, and true process-death need a phone. Everything else is CI-proven.

1. **Touch-and-slide to move** (D7): touch an unselected room and keep sliding — it should select on
   the way down and move in the same motion, no extra tap.
2. **Corner-grip drag resize** (E1–E6 by finger): pull each grip; the opposite corner should stay put;
   pull one grip past the far corner — it should stop at 1×1, never flip inside-out.
3. **Drag-into-another-room** (C3/C5 by finger): the shape should freeze at the last clear spot and the
   preview go red; lifting should leave it in the clear spot, not on top.
4. **Haptics**: a soft tick each time the shape jumps a cell; a firmer "no" buzz when a move/resize is
   refused; a confirm when a room or door lands.
5. **Rotation** (K1–K3): rotate the phone mid-edit — selection, the room you were placing, and the
   door step should all still be there.
6. **Process death** (K4/K5): with an **unsaved new** home part-built, force-stop the app from
   Settings and reopen — note whether the rooms are still there (expected: currently **lost** — S3).
   Repeat with a **saved** home (expected: restored).
7. **Real TalkBack pass**: swipe through the editor with the screen reader actually on; every control
   should be announced and operable. The front door should now say **"front door on the north wall"**,
   not "on the N wall" (v0.3.10).
8. **A plot key that cannot act should BUZZ** (v0.3.10): press "−" on Plot size until it stops at 4,
   then press once more — you should feel the same short "no" buzz you get when you drag a room onto
   another. Same when the rooms already fill the plot and it refuses to shrink. The *decision* is
   unit-tested; only the buzz itself needs a finger, because haptics are silent in the test harness
   by design.
9. **Rest a second finger on the plan while dragging a room.** The in-flight drag only tracks the
   first finger, so a second one sliding vertically is not consumed and the page may scroll under the
   drag. This is how Compose's own drag detector behaves and it can't be reproduced headlessly — worth
   one try to see whether it is noticeable in practice before deciding to special-case it.

---

## Open findings needing an owner decision

Two confirmed findings aren't auto-fixed because the fix is a judgement call with a persistence/DI
change I don't want to make unsupervised on a client build. Both are low-frequency; neither corrupts
the score.

### S2 — a reopened home can come back with a smaller plot outline

**What happens.** The plot's width/depth isn't saved (only the rooms are). On reopen we re-draw the
smallest plot that still contains the rooms. If you drew a big plot with empty space around the rooms,
that empty margin isn't restored — the canvas comes back tighter. Your rooms and your score are exactly
the same; only the blank border shrinks.

**Options.**
1. **Leave it.** Simplest, zero risk. The plot in this app is effectively "the rooms"; the score only
   ever looks at the rooms, so the margin has no meaning. Most users draw rooms to the plot edge, so
   they'd never notice.
2. **Persist the plot shape** (recommended if the owner has seen this and dislikes it). Store two extra
   numbers (plot width + depth) with each saved home. ~½ day: one small database column + migration,
   save/load wiring, one test. Reversible.

### S3 — a half-built new home can be lost if the phone kills the app mid-draft

**What happens.** While you're still building a *brand-new* home (before the "Read my home" step), the
draft lives only in memory. If Android force-kills the app to reclaim memory (more likely on cheap
phones) and you come back, the rooms are gone. A home you've already scored once is safe (it auto-saves).

**Options.**
1. **Leave it, document it.** The window is small (only before the first score) and a returning user
   just re-draws.
2. **Keep the draft across a kill** (recommended for budget-phone testers). Save the in-progress draft
   to Android's saved-instance state so it survives a kill. ~½–1 day; touches the draft's plumbing, so
   it needs its own careful test pass. No new database rows.

*My recommendation:* fold both into a small "Batch 2" only if the owner confirms they want them —
otherwise they stay documented. They are **not** blockers for 4 Aug.

### S8 — "Tap the outer wall" but the outer wall isn't drawn — ✅ FIXED in v0.3.11

**What happens.** In the door step the instruction reads *"Tap the outer wall where your main
entrance is."* The house's outline — the bounding box of the placed rooms, which is the wall the
engine actually scores the door against — is **never drawn**. When the plot is bigger than the house
the user sees separate room tiles floating on a grid and has to guess where "the outer wall" is.

**Found by** rendering the door step from `harness.html` and looking — it had never been rendered.

**✅ FIXED in v0.3.11 — the owner chose option (3), the fuller fix.** Both halves landed:

1. **The wall a tap means is now measured from the HOUSE, not the plot.** The old code compared the
   tap against the plot's four edges, so with a plot bigger than the house a tap directly above the
   rooms could resolve to *West* simply because the plot's west edge happened to be nearer than its
   north edge — a wall the user never aimed at, on the highest-weighted element the engine scores.
   The decision moved out of the Composable into pure `doorForTap(xCells, yCells, rooms)`
   (PlanConversion.kt), which **takes no plot dimensions at all** — the fix stated structurally.
   Distances are **signed**, so a tap out in the margin is negative for the wall it lies beyond and
   that wall wins. They are in **fractional cells**, because a 1-cell-deep house's north and south
   walls are half a cell apart: rounded to whole cells the tie always resolved north, so a south door
   was unreachable on a thin house.
2. **The house is outlined during the door step**, and the copy names it ("Your home is outlined
   below. Tap the wall where your main entrance is."). Drawn only in door mode — the rooms' own
   borders carry the boundary while placing rooms, and a second always-on frame would compete.

⚠ The outline is the footprint **bounding box**, so on a home with a gap between rooms it reads larger
than the rooms. That is honest — it is exactly the rectangle the engine scores — and it has the side
benefit of making the Group D "L-shapes are scored as a filled rectangle" caveat *visible* on screen
instead of only in the report. Worth the owner's eye (checklist B6).

**Proof:** 5 new `PlanConversionRoundTripTest` cases (tap beyond each wall picks that wall; the plot
plays no part; taps inside pick the nearest wall; a thin house takes a south door; every tap in a
441-point sweep is already footprint-clamped and survives reopen byte-for-byte) + a new fuzz invariant
in `sim.mjs` Suite C sweeping taps across the whole plot including all four margins. Both proven to
bite: re-measuring to the plot edges fails ~89 % of footprints ("beyond S but chose W" — literally the
bug), and rounding the tap to whole cells fails ~66 %. New golden `editor-door` renders the step.

## Step 2 — automation plan (numbered)

1. **Extend `:shared` `GridEditingTest`** — adversarial geometry the current suite doesn't cover:
   stepper-style BR-pinned resize helper, `placementAt` edge-trim, `newRoomId` after deletes, non-square
   clamp bounds (D5/E9-adjacent). *(shared)*
2. **New `PlanConversionRoundTripTest`** (`convert`) — `buildEnginePlan` ↔ inverse
   (`gridRoomsFromPlan`/`gridDoorFromPlan`). Requires exposing the two inverse fns as `internal`
   `@VisibleForTesting` (or a thin testable seam). Covers I1–I4, I7, H1–H3, H6, and **J2/J3/J5**
   (translation-invariance + bbox + sample-01=31) by scoring both ends with the real engine.
3. **New `GuidedGridInteractionTest`** (`compose`, Robolectric NATIVE, `runComposeUiTest`) — drive
   `GuidedGridContent` + a real `NewPlanViewModel` (or its callbacks): tap-place, arrow-move,
   stepper-resize, remove, door-place, plot resize/refuse/clear, palette-scroll-held, overlap-refuse
   on the button paths. Covers A2–A5, B1–B7, C4/C6, D3–D5, E7–E10, F1–F4, G1–G10, H5/H7/H9, L1–L6.
4. **New `NewPlanViewModelTest`** (`compose`/vm) — `updateGrid` grow-vs-shrink/refuse/door-clear,
   `save`/`load` round-trip through the repo (fake), autosave-on-planid, `load()` grid re-derivation
   (I5/S2), ruleset refresh (I6). Covers G1/G8/G10, I5/I6, K3/K5, J1/J4.
5. **Render/L1/a11y** — confirm existing `editor` / `editor-empty` / `editor-wide` cover L4/L7–L10;
   add a **`editor-selected`** state (a room selected → grips + SelectedRoomTools visible) worth seeing,
   and a **`editor-door`** state, if not already golden. New screens auto-adopt the ratchet.
6. **Triage report** — map every green/red back to this catalogue, split real user-facing bugs (with
   concrete repro) from test artefacts, then plan-first fixes one at a time with the full CI +
   screenshot + adversarial-review ritual, tagging a build per meaningful batch.

*Provisional Status marks above are replaced by real CI results as each test lands.*
