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

### 12. The 8 unresolved expert rulings run on default guesses
Where schools genuinely disagree, the engine uses a mainstream default and shows "schools disagree"
where it surfaces. **Two of the eight can shift the score**: M-05 (how big the Brahmasthan centre is)
and M-07 (the hybrid square-grid-rooms + angular-door method). Until the client's expert rules, the
number embeds our defaults. Disclosed where a dispute is relevant to the plan; the score-level effect
(M-05/M-07) is not spelled out to the user.

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
