# VastuFirst — where the score / info can be inaccurate (honest risk map)

**Purpose:** an exhaustive, honest list of every scenario where a user could be shown an inaccurate
score or statement, ranked by danger. "Danger" = silent (no warning) × affects the number ×
likelihood. For a paid product this is the list to close down before/through Phase 3.

**Framing:** the engine's *maths* is correct (verified). Inaccuracy enters where (a) the simplified
grid drawing differs from the real home, (b) the user supplies something the app can't verify (North,
room labels), or (c) genuine Vastu experts disagree and we run on a default. Most of the dangerous
cases are inherent to a "draw your home on a grid" input model, not bugs.

---

## GROUP 1 — Silent AND moves the number (the dangerous ones)

### 1. L-shaped / notched / non-rectangular homes → scored as a filled rectangle  ⭐ worst
`buildPlan()` builds the footprint as the **bounding box of the placed rooms**. If the real home has
a genuinely missing corner (an L, a notch, a cut), but the drawn rooms happen to fill a clean
rectangle, the engine sees a regular rectangle, the cut / missing-corner / extension checks never
fire, and **no "unusual shape" warning is shown**. The score comes out **too generous** and confident.
- Silent: yes (if the bounding box is a clean rectangle). Moves number: yes. Common in India: yes.
- Fix path: an **outline-capture step** (trace the actual footprint), or infer the footprint as the
  rooms' *union* (risk: over-reports cuts on layouts with gaps between rooms — needs engine-side
  validation). **Refinement candidate #1.**

### 2. Coarse 8-cell editor vs 9-pada zones → a room near a boundary can land in the wrong zone
The editor snaps rooms to an **8×8 grid**; Vastu zones are a **9×9 pada grid** (and the door is 32
angular segments). The engine computes exact overlaps, but the *input resolution is coarser than the
zones*, so a room the user pictures as "NE corner" can be placed such that it scores as N or E. No
warning that placement is approximate.
- Silent: yes. Moves number: near zone boundaries, yes. Fix path: finer grid or a "nudge into
  zone" affordance; at minimum a note that placement is approximate.
- **v0.2.3 mitigates the *silent* half:** the editor now names the zone live in the chip above the
  plan ("Kitchen · 2×2 · North-East") and draws the two band boundaries stronger, so a room landing a
  cell away from the zone the user intends is visible **while placing it** rather than discovered in
  the report. It does not make the input finer — that remains the open fix.

### 2b. The editor's zone chip is a placement aid, not the scored zone
`zoneOfRect()` names the zone from the room's **centre on the 8×8 drawing grid**. The engine scores on
the **footprint** (the bounding box of the placed rooms, which is smaller than the grid until the
drawing fills it) and credits the **largest overlap**, not the centre. So the chip and the report can
name different zones for the same room — most visibly when only one or two rooms have been placed and
the footprint is therefore tiny.
- Silent: the chip never claims to be the score and the report is the authority. Moves number: **no** —
  the chip is display-only; the engine is untouched by it.
- Fix path: derive the chip from the live footprint using the engine's own largest-overlap rule. Not
  done in v0.2.3 because it makes the chip's answer jump as the footprint grows while rooms are being
  added, which defeats the thing the chip exists to do. Revisit alongside the outline-capture step
  (#1), which fixes the footprint properly.

### 2c. ⭐ A scanned plan used to arrive as islands — closed 2 August 2026
Every room's edges were rounded to the grid **independently**, which keeps two rooms flush only when
the plan reader reports their shared wall at the identical number. It never does — it draws inside the
wall thickness and jitters every edge — so a shared wall came back as 3.48 from one room and 3.52 from
its neighbour, rounded to 3 and 4, and opened a one-cell moat between two rooms that touch. Every room
did it to every neighbour, so a scanned home arrived as a scatter of boxes rather than a floor plan.
- Silent: **no** — it is glaringly visible, which is how the owner found it. Moves number: yes, via
  the footprint and therefore every zone band.
- Fixed: edges within half a cell are now agreed to be the same wall before rounding. Measured on the
  owner's own sheet, 41 of 80 empty squares → 26, biggest hole 28 → 11, every real adjacency restored.
- ⚠ **Residual:** a wall thinner than one cell still becomes a whole empty cell — that is #2 above,
  and it is why the confirmation step exists.

### 2d. ⭐⭐ The reader does not MEASURE a plan — it lays out a template. Contained 2 August 2026
On roughly half of real plans the reply is not a reading at all. On the owner's second flat: fifteen
rooms sharing **three** distinct sizes, **four** distinct left edges, every single coordinate on a
0.05 lattice, three rooms placed past the bottom of the page — and `planConfidence` 0.95, exactly as
on a perfect read. **11 of the 23 real 2D plans in the corpus have more than 90 % of every coordinate
on that lattice.** It cannot be prompted away: a variant explicitly forbidding round coordinates was
measured and produced the identical lattice with *more* rooms off the page.
- Silent: **yes, completely** — a template looks like a floor plan. Moves number: yes, via every
  room's direction. The existing uniform-box detector does not catch it, because three stock sizes
  give an area variation of 0.62 against a threshold of 0.15.
- Contained rather than fixed, and the distinction is the point: **the rectangles are now used only
  for arrangement — which room is left of which, which is above which, and the reader gets that
  right — while every dimension comes from the size the plan PRINTS**, which is text, which is the
  one thing this model is reliable at. On the owner's flat, 15 of 15 rooms carry a printed size; 116
  of 129 across nine real plans.
- ⚠ **Residual, and it is the live one:** a plan that prints **no** sizes has nothing but the template
  to go on. Those plans are still gated on room count and mostly arrive unplaced, which is honest but
  is the weaker product. Three narrower faces of the same residual were seen on real sheets
  (2 August 2026), and one of them closed the same day: a caption printing ONE dimension
  (`BALCONY 1825 WIDE` / `5'-0" WIDE`) **is now read as the strip's printed depth** (v0.6.5) — the
  strip keeps the wall it was read along and its narrow axis comes from the caption, so the balcony
  no longer towers over rooms that shrank to print scale. Still open: a BRANDED page (logo and title
  taking a third of the sheet) measurably degrades the reader's ARRANGEMENT — the same unit mapped
  cleanly from a plain copy and badly from the branded one, and it was measured (plan doc §3n E11)
  that no signal in a single reply separates the two, so a scrambled arrangement is drawn honestly
  rather than gated, and a cleaner copy or crop remains the fix; and on the owner's flat the
  reader's arrangement crams the kitchen's corner of the home, so the kitchen lands at a single cell
  — present, right place, too small — which the flagged confirmation step exists to catch.

### 2e. ⭐ An attached toilet drawn inside its parent room used to be dropped — closed 2 August 2026
The reader put the owner's master-bedroom toilet wholly inside the rectangle it gave the living room,
and the geometric sub-area rule deleted it — a TOILET, a 2.5-weight scored room, gone from the grid.
- The "signal we do not yet have" turned out to already exist: the room's **name**. Measured across
  every recorded real reply (41 plans), the geometric rule fired 24 times and every one was a real
  scored room — nine toilets, a pooja, balconies, a study — while every genuine dressing area was
  already being dropped by name before geometry ran. The geometric rule is deleted; a named room now
  always survives, keeps the cells it was read at, and the room it collided with is trimmed around
  it and flagged.
- ⚠ **Residual:** the rescued room sits where the reader read it, which on the owner's flat is inside
  the living room's area rather than against the west wall where his sheet puts it. Moving it there
  would mean relocating a room the user has never seen — the harm the no-relocation rule exists to
  prevent — so it arrives visibly misplaced and flagged, one drag from right.

### 3. North is set by the user and cannot be verified
The whole zone assignment rotates with North. The user sets it by dial / slider / degree — if their
compass reading is a few degrees off, rooms near a boundary shift zones. The **device-compass helper
was intentionally omitted** this phase, so there's no assist or cross-check.
- Silent: yes. Moves number: yes. Fix path: add the compass-sensor helper (a Phase-3 candidate) and/or
  a "double-check your North" confirmation.

### 4b. ⚠ The report used to SELL issues it never showed  — closed in v0.5.0
Not an accuracy bug in the number, but the same family of dishonesty and worth recording next to
them. Rooms rated SUBOPTIMAL ("not ideal") were counted by the free score screen into "N more issues"
to justify the ₹699 — and then filtered out of the paid report entirely, falling between the problems
list and the already-right list. The report now has a section for them, and a test asserts the number
the free screen counts equals the number the report shows.

### 4. Only rooms + door + shape are scored — fixture/site rules are never collected
The guided grid captures rooms, the front door, North, intent, property type. It does **not** collect
fixtures or site data (septic tank, borewell/water, roads/Veedhi-shoola, staircase-under-fixture,
pooja↔toilet shared wall, etc.). Those rules land in "Couldn't check these yet" (honestly shown) — but
the **headline score is computed only from what was collected** and can read as a complete verdict.
- Silent: partially (the "couldn't check" list is shown, but the number doesn't say "partial"). Moves
  number: it's a *ceiling* — real defects in uncollected areas aren't penalised. Fix path: collect key
  fixtures/site in a later step, and/or label the free score "based on rooms, door & shape."

### 5. Single level only
`buildPlan` scores `levels[0]`. A multi-storey home is assessed as its one drawn floor, with no
statement that upper/lower floors weren't considered.
- Silent: yes. Fix path: multi-level capture (later), or copy that says "this floor."

### 6. Door position is coarse and can be silently relocated
The door is captured as *which wall + a cell index* and placed at that cell's wall-midpoint. Its exact
bearing decides the 32-pada verdict (11.25° segments), so a door near a segment boundary is sensitive
to the coarse placement. If the user puts the door beyond the room footprint, `buildPlan` **coerces it
into the footprint span** — the scored door isn't quite where they tapped.
- Silent: yes. Moves number: the door is the highest-weighted element, so near a segment edge, yes.

---

## GROUP 2 — Already disclosed to the user (accuracy limits they're told about)

### 7. Irregular footprint → corner checks skipped (now shown)
When the footprint genuinely isn't a rectangle, the engine sets `shapeIrregular` and shows the
"unusual shape — corner checks need a clearer outline" note. The rooms + entrance are still scored;
the shape sub-checks are honestly declared as not run. (This is the *disclosed* cousin of #1.)

### 8. Angled homes
Hardened in Phase 1: a clean rectangle at any angle scores normally (shape judged in the building's
own frame; cut/extension attributed to cardinal zones). A ">1° off compass" note is shown. No known
inaccuracy for angled *regular* homes; irregular+angled falls under #7.

### 9. "Couldn't check these yet" list
Disputed/uncollected rules are shown as not-assessed rather than pass/fail — honest, but see #4 for
how it interacts with the headline number.

---

## GROUP 3 — Depends on what the user enters (garbage-in)

### 10. Room labels are the user's judgment
If a room is mislabelled (a store marked as a bedroom), the score reflects the label, not reality. Not
a bug; a data-quality caveat. Fix path: none needed beyond clear labelling UI.

### 11. Rooms not drawn / drawn out of proportion
Unplaced spaces (corridors, open areas) or wrong proportions shift the footprint, the centre
(Brahmasthan) and the zone bands away from the real home. Fix path: outline capture (#1) mitigates.

---

## GROUP 4 — Expert-judgment / by-construction (the number is a model)

### 12. The expert rulings are the owner's choices, not facts — and two of them move the number
Where schools genuinely disagree, the engine runs on a ruled position and shows "schools disagree"
where it surfaces. **Two rulings shift the score**: M-05 (how big the Brahmasthan centre is) and M-07
(the hybrid square-grid-rooms + angular-door method). Both are disclosed in `docs/EXPERT-RULINGS.md`
with the alternative measured rather than guessed. The score-level effect is not spelled out to the
user on screen.

**Closed as an "unruled default" risk on 1 August 2026:** the prayer room (W-12) was the last question
running unresolved, and it ran by *not scoring the room at all*. It is now ruled for the modern
North-East, with nothing prohibited. What remains is not a hidden default but a stated choice between
two living traditions — and the report shows both readings plus which one the number uses.

⚠ **The residual honest caveat:** a prayer room is now scored on the modern reading. A customer whose
consultant follows the classical centre reading will disagree with that part of the number. Nothing is
called a defect on either reading, so the disagreement is bounded — but it is a real one, and the
report says so rather than presenting the modern reading as the tradition.

### 13. The 0–100 score is VastuFirst's own construction
Weights and points live in the rule JSON — a modelling choice. Two Vastu consultants would not
produce the same 0–100. Disclosed on the score screen ("our own way of summarising… not part of the
tradition"). It's a consistent internal yardstick, not a canonical Vastu number.

### 14. Missing pada names (E7 / S7)
Absent from the source knowledge base (left null). Display-only; doesn't move the number.
**Closed as a display risk in v0.5.0:** the report's front-door section now states outright that the
sources leave that position unnamed, rather than rendering a blank or inventing a name. The position
number, the wall and the verdict are all still shown, so the reading is complete either way.

---

## Priority to close (recommended for the Phase 3 refinement plan)
1. **#1 L-shape/notch** — outline capture. Biggest silent, score-moving, common case.
2. **#3 North** — compass helper + confirmation.
3. **#4 coverage framing** — label the free score as "rooms, door & shape," collect key fixtures later.
4. **#2 placement resolution** — finer grid or zone-snap nudge.
5. **#12** — resolved by the owner sending the 8 rulings (data edit, no rebuild).
