// ScanMapper.kt — a model reply → rooms the guided grid can hold. PURE, and where the risk lives.
//
// Everything the model says arrives here as numbers in a unit square and printed captions. Nothing
// downstream ever asks the model anything again: zones, the front door and the score are all our own
// code (docs/SCAN-PLAN-READING-PLAN.md §3i, layers L3/L4).
//
// ⭐ THE TWO QUALITY GATES ARE OBJECTIVE, AND NEITHER IS THE MODEL'S CONFIDENCE.
//    `planConfidence` came back 0.95 on a 100 % read, on a 25 % read, and on fifteen rectangles it
//    had plainly invented — measured three separate times (§3e, §3f, §3j D2). Instead:
//      · COVERAGE     — do the rectangles actually tile the footprint? Threshold DERIVED FROM THE
//                       24 REAL 2D PLANS, not from intuition (see [PLACED_COVERAGE]).
//      · AREA SPREAD  — real homes have rooms of different sizes. Fifteen identical boxes is a
//                       fabrication, whatever the coverage says (see [UNIFORM_AREA_VARIATION]).
//
// ⚠ THIS FILE DOES NOT USE `fitWithoutOverlap`. That function RELOCATES a room to the nearest free
//   slot, which is exactly right while a user is dragging (they are watching it move) and exactly
//   wrong for a scan (they are not). A relocated room silently misreports where the kitchen is, and
//   the kitchen's zone is a scored input. Scan TRIMS the lower-confidence room instead, and flags it.
//   Never move a room the user has not seen yet.
package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.overlaps
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object ScanMapper {

    // ---- the drawing grid ------------------------------------------------------------------
    // ⚠ MIRRORS the constants in :app's NewPlanViewModel (GRID / MIN_GRID / MAX_GRID). :shared is
    // pure Kotlin and cannot see :app, and the editor is on hold, so these are duplicated rather
    // than moved — exactly as `CellRect` mirrors `GridRoom`. `ScanGridConstantsTest` in :app fails
    // the build if the two ever drift.
    const val MIN_GRID = 4
    const val MAX_GRID = 10

    /**
     * ⭐ A scan draws on the FINEST grid the editor allows, where a person drawing by hand starts at
     * 8×8. Measured, not chosen: on the recorded `plan-01` read, an 8×8 grid rounds the toilet and
     * the bath — both 0.1 of the plan deep, so 0.8 of a cell — away to nothing, and two rooms with a
     * combined Vastu weight of 2.5 + 0.8 vanish from a paid score. At 10×10 all eight rooms survive.
     * This is plan doc §7's question ("does MAX_GRID = 10 have the resolution?") answered from the
     * data: **10 is enough, and 8 is not** — so scan needs no editor change, which is what §7 was
     * worried about.
     */
    const val DEFAULT_GRID = MAX_GRID

    /**
     * ⭐⭐ How many of a reply's rooms must carry a size the PLAN PRINTED before its arrangement is
     * trusted, however many rooms there are.
     *
     * ⚠ This is the gate that let the owner's own flat through, and the reasoning is the whole
     * architecture in one line: **the rectangles are an arrangement and the printed text is the
     * measurement.** Where every room states its own size, the reader's stock rectangles decide only
     * which room is left of which — the thing it demonstrably gets right — and every actual
     * dimension comes from text it reads at ~95 %. Counting rooms was protecting against bad
     * rectangles; with the sizes present there is far less for it to protect.
     *
     * Two thirds, and nothing in the corpus sits anywhere near it: real plans come back at 0 %
     * (a sheet that prints no sizes at all) or 85–100 %. It is a cliff, not a tuned dial.
     */
    const val SIZED_SHARE_TO_TRUST = 2.0 / 3.0

    /**
     * How many rooms must print a size before a claim rests on the sizes at all.
     *
     * Three, so a judgement rests on at least three captions rather than one room's mis-transcribed
     * one. Below it the sheet is treated as printing nothing, which is the honest answer: a
     * conclusion drawn from one caption is a guess wearing a measurement's clothes.
     */
    const val MIN_PRINTED_TO_FIT = 3

    /**
     * What share of a reply's rooms must carry a size the SHEET PRINTED before we will believe a
     * layout the reader called a 3D render. See [sheetPrintsItsOwnSizes] for the measurement — the
     * two classes it separates sit at 0 % and 100 %, so this is a wide-margin cut, not a tuned dial.
     */
    const val MIN_SIZED_SHARE = 0.7

    /**
     * …and an absolute ceiling that no reply passes, sizes or not: **what the drawing grid can
     * actually hold.**
     *
     * ⚠ It was 20 until 16 Aug 2026. **The note that raised it argued from the wrong number, and
     * the re-justification is recorded here rather than the raise being quietly kept.** The old note
     * said "the largest genuine single home in the corpus has 21 named spaces", so the ceiling sat
     * one *below* a real house. That compared a RAW caption count against a gate that counts
     * IDENTIFIED rooms — the ones left after ducts, lifts and unreadable words are dropped. Re-run
     * against HEAD (`audit-mapper.mjs --only=corpus`), the largest identified single home in the
     * whole corpus is **19**, not 21. So 20 was never refusing a real house; it had one room spare.
     *
     * The raise still stands, on three grounds that survive the correction:
     *
     *   · one room of headroom above the largest plan we have ever seen is not headroom;
     *   · the reason for wanting a floor-plate cut at all went with the lift rule (see the note in
     *     [map]); several homes on one page is [RoomLabels.isUnitLabel]'s question, not a count's;
     *   · a long reply that states no sizes is already stopped by [SIZED_SHARE_TO_TRUST] one line
     *     below — that is the case a raw count was ever good evidence for, and it is what
     *     `RecordedScans.UNSIZED` (21 rooms, no printed size) still lands in.
     *
     * So the only honest remaining limit is the editor's: the grid holds twenty-five rooms
     * (`RoomsOffTheGridTest` asserts `unplacedStripCapacity(10, 10) == 25`), and past that the screen
     * already names what will not fit rather than dropping it silently.
     *
     * **Corpus effect of 20 → 25, measured both ways on 16 Aug 2026: none.** 27 placed · 6 assisted ·
     * 11 refused of 44 under either value, orientation agreement 205/211 under either. It is headroom
     * for a large real home, not a new behaviour — and the owner's own sheet, which one telling of
     * this raise leaned on, in fact reads **15** rooms, not the 20 that was claimed. That claim was
     * never load-bearing for the constant, and it is retired here.
     */
    const val MAX_ROOMS_EVER = 25

    /**
     * ⭐ A reply may run this far past the edge of the page before we stop treating it as a layout.
     *
     * ⚠ The reader routinely returns rooms outside the unit square — three of the fifteen on the
     * owner's plan — because it is laying out a template rather than measuring, and the template
     * runs long. [sanitise] used to CUT those rooms off at the edge, which on his sheet deleted one
     * of his three balconies outright and flattened the utility to a third of its depth before
     * anything else had a chance to run. A deleted room changes the footprint the engine scores and
     * the user never knew to look for it.
     *
     * Shrinking the whole reply uniformly instead is **provably neutral on everything that already
     * fits**: the grid is framed on the rooms' own bounding box and fitted to it uniformly, so a
     * single scale and offset applied to every room cancels out exactly. It changes nothing except
     * that rooms stop being amputated by an edge that was never a wall.
     *
     * Twice the page is generous on purpose — the observed overflow is 1.25× — and beyond it the
     * reply is not a layout at all, so [sanitise]'s clamp deals with it as before.
     */
    const val MAX_OVERFLOW = 2.0

    /**
     * ⭐⭐ …and how many rooms must run past the edge before it counts as the LAYOUT running long
     * rather than one room being wrong.
     *
     * ⚠ This distinction is the whole safety of [shrinkToPage], and the tests caught its absence.
     * **One** room outside the page is that room's problem: a hallucinated rectangle out on its own,
     * which has always been clamped back in and flagged, or dropped if it is entirely off the sheet.
     * Shrinking the whole home to accommodate it would let a single bad box pull every real room
     * smaller — the exact "one stray rectangle inflates the frame" failure this file already warns
     * about elsewhere.
     *
     * **Several** rooms outside the page is a different animal, and it is what the owner's sheet
     * produced: the reader lays out a template, the template runs long, and the last three rooms in
     * the sequence fall off the bottom together. Nothing is wrong with any one of them — the whole
     * thing is simply drawn at the wrong scale, which is the one case a scale can fix.
     */
    const val MIN_SPILLED_ROOMS = 2

    /**
     * ⭐⭐ The Placed/Assisted gate when the plan prints NO sizes: **how many rooms it has**. Above
     * this, the geometry is not trusted and the rooms are handed over unplaced.
     *
     * ⚠ No longer the first gate — see [SIZED_SHARE_TO_TRUST]. A sheet that prints its own room
     * sizes is trusted however many rooms it names, up to [MAX_ROOMS_EVER].
     * It now applies only to a reply we have no printed measurements for, which is the case it was
     * always really about: nothing but the reader's own rectangles, and those degrade with count.
     *
     * **This replaced a coverage threshold, and the replacement was forced by looking at pictures.**
     * (E3, `tools/scan-eval/exp-place.py`; overlays in `out/overlay/`.) Until then, no one had ever
     * checked a single real placement — `batch-real.py` recorded room counts, names and a coverage
     * number but threw the rectangles away. Drawing the model's rectangles back over the plans and
     * judging them by eye said this:
     *
     * | plan | rooms | coverage | placement, by eye |
     * |---|---|---|---|
     * | plan-008 | 11 | 0.390 | **all 11 boxes on the right room** |
     * | plan-006 | 10 | 0.421 | good — 7 of 10 |
     * | plan-018 | 15 | 0.204 | bad — boxes over a 3D render and a legend |
     * | plan-003 | 17 | 0.574 | bad — one "balcony" landed on the title block |
     * | plan-005 | 21 | **0.760** | mixed — dining, kitchen and puja all wrong |
     * | plan-002 | 24 | 0.421 | bad — scattered |
     *
     * ⚠ **Coverage is not merely mis-calibrated, it is non-monotonic with quality.** Two plans with
     * *identical* coverage (0.421) placed well and badly; the corpus's *highest* coverage placed
     * worse than its lowest-but-one. A simple house with a garden and a porch legitimately has a lot
     * of area that is not a labelled room, so a good read of a real home scores LOW. The old 0.577
     * gate — the upper quartile of that distribution — was therefore wrong in both directions: it
     * threw away plan-008's flawless read and trusted plan-005's wrong one.
     *
     * Room count separates cleanly where nothing else does (good 10–11 · bad 15–24, no overlap), and
     * it matches what the published benchmarks say: these models degrade as the number of separate
     * items to count grows. It is also the difference between a **single-family house plan** (10–12
     * named spaces) and an **apartment floor plate** (17–25, thick with ducts, shafts, lobbies and
     * lifts) — which is the real distinction the eye is picking up.
     *
     * ⚠ Honest limits, stated rather than buried: this rests on **6 plans judged by eye**, 2 of them
     * good. 13 and 14 rooms were never observed, so the exact cut between 11 and 15 is a judgement,
     * not a measurement. And it does **not** catch a badly skewed photograph of a simple plan — that
     * read had 8 rooms and would be trusted here. Nothing available catches it: the measured photo
     * read's coverage (0.569) sits *above* both good real plans. The mandatory confirmation step is
     * what catches it, which is exactly what §6.2b requires the confirmation step to be for.
     */
    const val MAX_TRUSTED_ROOMS = 12

    /**
     * stdev(area) ÷ mean(area) below this means the rectangles were invented rather than measured.
     *
     * From §3j D2: a numbered-legend plan returned all 15 rooms at **exactly w 0.15 × h 0.15**, on a
     * tidy 0.05 grid, at planConfidence 0.95. A plausible-looking spread, not a measurement. Real
     * homes never have a dozen identically sized rooms, so near-zero variation is a fabrication
     * signature — cheap, deterministic, and it catches a case coverage alone would wave through.
     */
    const val UNIFORM_AREA_VARIATION = 0.15

    /** Below this many rooms, identical sizes are plausible (a row of three shops) — don't judge. */
    const val UNIFORM_MIN_ROOMS = 4

    // ⚠ MEASURED AND REMOVED: the geometric sub-area drop (a rectangle ≥90 % inside a bigger one
    // was deleted as a dressing-area). Run across every recorded real reply — 41 plans,
    // `tools/scan-eval/audit-mapper.mjs` — it fired 24 times and EVERY ONE was a real scored room:
    // nine toilets, a pooja, balconies, a study, a servant room. Not one genuine dressing area ever
    // reached it, because genuine sub-areas are caught BY NAME (dress/wardrobe/duct, RoomLabels)
    // before geometry runs. The reader lays out template rectangles, so "wholly inside another
    // room" describes the reader's sloppiness, not the home — the owner's own master toilet was
    // deleted by it. Owner decision D1's intent (a dressing area never becomes a room) is carried
    // entirely by the name table; a typed room now always survives to placement, where the overlap
    // resolver trims the pair apart and flags them.

    // ⚠ MEASURED AND NOT BUILT: a separate "fill the grid" scale.
    //
    // A room's drawn size comes from the cells the reader's own rectangles occupied
    // (`cellTotal / printedTotal` in reshapeToPrinted), so the printed sizes REDISTRIBUTE area
    // rather than setting it. That looked like the cause of the owner's under-sized rooms, and the
    // obvious fix was to scale the dimensioned rooms up to fill some target fraction of the grid.
    //
    // It was built as a four-rung ladder (0.95 / 0.90 / 0.85 / 0.80, take the largest that costs no
    // room) and measured across the real corpus plus both of the owner's own sheets. IT CHANGED
    // NOTHING: identical orientation errors, identical rooms lost, identical fill, identical
    // coverage, and his 4'-11" toilet came out 2x2 either way. Once the grid is framed on the HOME
    // rather than on the picture, the reader's own total already fills it, so the extra scale has
    // nothing left to do. Machinery that cannot be shown to change an answer does not ship.
    // `tools/scan-eval/exp-frame.py` is the record.

    /**
     * ⭐⭐ TWO ROOM EDGES THIS CLOSE ARE THE SAME WALL.
     *
     * ⚠ This is the fix for the defect the owner photographed: a scanned home arriving as a dozen
     * islands with empty squares between every one of them, on a plan whose rooms all share walls.
     *
     * **Rounding each edge on its own was never enough.** It keeps two rooms flush only when the
     * reader reports their shared wall at the *identical* number. The reader does not: it draws
     * inside the wall thickness and jitters every edge, so one room's right edge comes back at 3.48
     * cells and its neighbour's left edge at 3.52 — a difference of four hundredths of a cell that
     * rounds to 3 and 4, and opens a one-cell moat between two rooms that touch in the real home.
     * Every room in a plan does this to every neighbour, which is why the whole layout falls apart
     * into islands rather than merely being a little loose.
     *
     * Half a cell is the honest threshold and not a tuned one: two edges closer than that round
     * *arbitrarily* — a hundredth of a cell either way flips the answer — so they carry no
     * information about being different walls. Beyond half a cell, rounding is decided and we leave
     * it alone.
     *
     * Measured on a faithful sloppy read of the owner's own sheet: 36 empty cells in 3 holes → 15,
     * and the biggest hole 25 cells → 9. What survives is the genuine notch in that plan's shape.
     */
    const val WALL_TOLERANCE = 0.5

    /**
     * …and a cluster may never grow wider than this in total.
     *
     * Without it, edges at 0.0, 0.4, 0.8, 1.2 chain into one "wall" a cell and a half wide, and a
     * one-cell room between two of them is crushed out of existence. Single-linkage clustering
     * always needs this guard; it is the difference between merging near-duplicates and merging
     * everything.
     */
    const val WALL_MAX_SPAN = 0.75

    /**
     * ⭐ How far a re-shaped room's edge will reach to CLICK ONTO a wall line: one cell.
     *
     * Re-shaping to the printed size anchors a room's top-left corner and replaces its extent, so
     * it shrinks away from the neighbour on its right and below — re-opening the moat the wall
     * lines just closed. Letting the new edge click onto a wall line within one cell keeps the
     * printed proportions and the shared wall. One cell, because a room whose true size differs
     * from its neighbour's wall by more than that is telling us something we should not overrule.
     */
    const val MAGNET_REACH = 1

    /** Fewer than this many rooms surviving the grid snap isn't a layout worth showing. */
    const val MIN_PLACED_ROOMS = 2

    /** …nor is one where the grid's resolution swallowed most of the home. Measured, see §7. */
    const val MIN_PLACED_FRACTION = 0.6

    /** Per-room self-report below this earns an advisory flag. Never a gate — S2. */
    const val LOW_ROOM_CONFIDENCE = 0.5

    /**
     * Coverage is sampled on a 140×140 lattice of the unit square, counting the UNION of the
     * rectangles. ⚠ This is the identical formula `tools/scan-eval/batch-real.py` used to produce the
     * numbers [PLACED_COVERAGE] is calibrated against — same lattice, same `(i+0.5)/n` centres, same
     * half-open containment test. An "exact" analytic union area would be a better number and a
     * WORSE one to compare against the calibration, so it is deliberately not used.
     */
    const val COVERAGE_SAMPLES = 140

    // -----------------------------------------------------------------------------------------

    private class Candidate(
        val box: ScanBox,
        val type: RoomType,
        val flags: MutableSet<RoomFlag>,
        /** The printed sizes of the captions this candidate was fused from. Empty unless merged. */
        val parts: List<String> = emptyList(),
    )

    // ---- one space, captioned in sections ----------------------------------------------------
    /**
     * ⭐⭐ WHY A RUN IS ONE ROOM (owner, 6 Aug 2026, of his own flat: *"one long balcony is detected
     * 3 times — there should be only 2 extracted balcony"*).
     *
     * His sheet prints FOUR captions reading BALCONY. One is the long strip across the top. The other
     * three run edge to edge along the bottom at three different printed depths — `17'-4"x8'-3"`,
     * `13'-9"x10'-0"`, `20'-2"x6'-0"` — because three rooms open onto **one continuous balcony** and
     * the architect dimensioned it in the three pieces that belong to each. The reader is not wrong;
     * it copied the sheet faithfully. We were the ones treating the caption count as the room count.
     *
     * Two things were wrong with that, and only the first is cosmetic:
     *   · The check-what-we-read list showed the same balcony three times, which reads as a bug.
     *   · The engine scored it three times. A balcony carries weight 0.8 and earns a positive verdict
     *       in the zone it most overlaps, so one physical strip was collecting three verdicts.
     *
     * ⚠ SAFE FOR THE DEFECT SIDE, which is the half that must not move. "Balcony in the South-West"
     * fires on ANY_ENCROACHMENT at `roomAreaFraction` = 0.02 — two per cent of the room's own area —
     * so the fused strip still trips it on the third of itself that lies in the South-West. Merging
     * removes double-counted credit; it cannot hide a defect.
     *
     * ⚠ RESTRICTED TO BALCONIES, and that restriction is load-bearing. Fusing by TYPE alone would
     * weld two side-by-side bedrooms into one bedroom the size of both. A balcony is the one room
     * type whose sections genuinely form a single space along one wall — and the label must match
     * as well, so a `BALCONY` never fuses with a `SIT OUT` that merely maps to the same type.
     */
    private val MERGEABLE_RUN_TYPES = setOf(RoomType.BALCONY)

    /**
     * How much of the thinner section's DEPTH the two must share to count as the same run. At 0.6 a
     * balcony that steps in or out along its length still fuses, while a balcony on the north wall
     * never fuses with one on the south — they share no band at all.
     */
    const val RUN_BAND_SHARE = 0.6

    /**
     * The largest gap along the run, as a fraction of the sheet, that still counts as continuous.
     * The owner's three sections abut exactly (gap 0.0); this allows a wall's thickness between them
     * without letting two balconies at opposite ends of the same wall reach each other.
     */
    const val RUN_MAX_GAP = 0.02

    /**
     * Turn one model reply into an outcome.
     *
     * [imageAspect] is width ÷ height of the source image, supplied by the platform layer — the model
     * is never asked, because measuring is the thing it cannot do. It only chooses the shape of the
     * drawing grid; `null` gives a square one.
     *
     * ⭐⭐ THE PICTURE AND THE SCORE TAKE THEIR SHAPES FROM DIFFERENT PLACES, ON PURPOSE (15 Aug
     * 2026). The GRID is a statement about the home, so it is shaped from the sizes the sheet
     * PRINTS ([reshapeToPrinted]) — text the reader transcribes at ~95 % against rectangles it
     * guesses at 40–70 %. The boxes drawn on the PHOTOGRAPH are a statement about the photograph,
     * so they are the reader's own rectangles, untouched. See [ScannedRoom.source] for the
     * measurement that separated the two.
     */
    fun map(draft: ScanDraft, imageAspect: Double? = null): ScanOutcome {
        val dropped = ArrayList<DroppedSpace>()

        // ---- 1. sanitise geometry ------------------------------------------------------------
        // ⭐ …but SHRINK a reply that overruns the page before cutting anything off it. See
        // [MAX_OVERFLOW]: the clamp below was deleting whole rooms off the bottom of the owner's
        // plan, and a room that never arrives is one the user cannot correct.
        val fitted = shrinkToPage(draft.rooms)
        val clean = ArrayList<Pair<ScanBox, MutableSet<RoomFlag>>>(fitted.size)
        for (b in fitted) {
            val flags = mutableSetOf<RoomFlag>()
            val c = sanitise(b, flags)
            if (c == null) {
                dropped += DroppedSpace(b.label, DropReason.INVALID_GEOMETRY)
            } else {
                if (c.confidence < LOW_ROOM_CONFIDENCE) flags += RoomFlag.LOW_MODEL_CONFIDENCE
                clean += c to flags
            }
        }

        // ---- 2. measure BEFORE any of our own edits, so the number is comparable to the corpus --
        val boxes = clean.map { it.first }
        val coverage = coverageOf(boxes)
        val variation = areaVariationOf(boxes)
        fun notes() = ScanNotes(coverage, variation, draft.planConfidence, dropped.toList())

        // ---- 3. triage (L0) — the refusals a user can actually act on --------------------------
        triage(draft, boxes)?.let { return ScanOutcome.Refused(it, notes(), ifRead(it, draft, imageAspect)) }

        // ---- 4. captions → room types (L1: the model's 95 %-accurate skill) --------------------
        val typed = ArrayList<Candidate>(clean.size)
        var unknownLabels = 0
        // A couple of captions can only be read against the rest of the plan — "LOBBY" is the living
        // room when nothing else is, and circulation when the plan already names a living room.
        // Measured on the 30-plan corpus: four of the six plans printing a lobby also print a living
        // room, so a fixed mapping is wrong either way round.
        val context = RoomLabels.contextOf(clean.map { it.first.label })
        for ((box, flags) in clean) {
            when (val m = RoomLabels.resolve(box.label, context)) {
                is LabelMatch.Room -> {
                    if (m.loose) flags += RoomFlag.LOOSE_LABEL_MATCH
                    typed += Candidate(box, m.type, flags)
                }
                LabelMatch.NotHabitable -> dropped += DroppedSpace(box.label, DropReason.NOT_HABITABLE)
                LabelMatch.Unknown -> {
                    unknownLabels++
                    dropped += DroppedSpace(box.label, DropReason.UNKNOWN_LABEL)
                }
            }
        }

        // ---- 4b. ⭐ a run of sections under one caption is ONE room ------------------------------
        // See [MERGEABLE_RUN_TYPES]: the owner's plan captions one continuous balcony three times,
        // and we were making three rooms — and three scored verdicts — out of it.
        val fused = mergeRuns(typed)

        // ---- 5. every typed room survives — sub-areas were dropped BY NAME in step 4 -----------
        // (The geometric containment drop that used to live here was measured against the whole
        // recorded corpus and deleted; see the note above where its threshold used to be.)
        val rooms = fused

        if (rooms.isEmpty()) {
            // Everything the plan said was unreadable as a room name — a numbered legend whose key
            // could not be resolved, or captions in a language we did not read. That is the same
            // failure the user fixes the same way, so it says "no labels", not "no rooms".
            val reason = if (unknownLabels > 0) RefusalReason.NO_LABELS else RefusalReason.NO_ROOMS
            return ScanOutcome.Refused(reason, notes())
        }

        // Top-to-bottom, left-to-right — the order someone reads a plan, so the palette makes sense.
        val ordered = rooms.sortedWith(compareBy({ it.box.y }, { it.box.x }))
        val identifiedPages = ordered.map { pageSource(draft.building, it.box) }
        val identified = ordered.mapIndexed { i, cand ->
            ScannedRoom(
                cand.type, cand.box.label, rect = null, flags = cand.flags.toSet(),
                printedSize = cand.box.printedSize, source = identifiedPages[i],
                readInParts = cand.parts,
            )
        }

        // ---- 6. the objective gates (L2 is a bonus, never a promise) ---------------------------
        // ⚠ Coverage is deliberately NOT a gate — see [MAX_TRUSTED_ROOMS]. It is still measured and
        // carried in the notes, because it is worth being able to answer "why did it do that?", but
        // it decides nothing.
        if (rooms.size >= UNIFORM_MIN_ROOMS && variation < UNIFORM_AREA_VARIATION) {
            return ScanOutcome.Assisted(identified, AssistReason.UNIFORM_BOXES, notes())
        }
        // ⭐⭐ THERE IS NO LIFT GATE HERE ANY MORE (16 Aug 2026), and it is not an oversight.
        //
        // "A sheet that names a LIFT shows a whole floor, not one home" was checked against the four
        // corpus sheets that name one, and it is WRONG ON THREE. plan-002, plan-003 and plan-004 are
        // each a single large apartment with the tower's lift core drawn beside or through it — one
        // kitchen, one living room, one dining room apiece. Only plan-001 is genuinely several
        // homes, and it is refused as MULTI_UNIT long before this point, so the lift rule never
        // caught anything the unit rule did not.
        //
        // What it DID do was throw away three good reads. With it gone (audit-mapper.mjs, whole
        // corpus): placed 24 -> 27, assisted 9 -> 6, refusals unchanged, and the newly placed plans
        // agree with their own printed sheets on 19 of 19 and 10 of 10 rooms. Both were rendered and
        // looked at before this shipped — every room lands in the right quarter of the home, and the
        // lift core becomes an empty middle, which is the honest drawing of an unread space.
        //
        // The word still matters where it belongs: a LIFT caption is dropped as not habitable, so no
        // lift is ever scored as a room. What is gone is condemning the whole sheet for naming one.
        // A tower is where this app's customers live.
        if (rooms.size > MAX_ROOMS_EVER) {
            return ScanOutcome.Assisted(identified, AssistReason.TOO_MANY_ROOMS, notes())
        }
        // ⭐⭐ …and below that ceiling the room count only decides a plan whose rooms state no sizes.
        // Where they do, the reader's rectangles supply the arrangement and its own printed text
        // supplies every measurement — see [SIZED_SHARE_TO_TRUST].
        val sized = rooms.count { RoomDimensions.of(it.box) != null }
        if (sized < rooms.size * SIZED_SHARE_TO_TRUST && rooms.size > MAX_TRUSTED_ROOMS) {
            return ScanOutcome.Assisted(identified, AssistReason.TOO_MANY_ROOMS, notes())
        }

        // ---- 7. ⭐⭐ the drawing grid is the HOME, not the picture -------------------------------
        // A builder's sheet is mostly not the home: a logo, a title block, a legend, white space.
        // Mapping the reader's coordinates straight onto the grid handed the home whatever fraction
        // of the SHEET it happened to occupy — 40 % on the owner's Green Court plan, 71 % on average
        // across the real corpus — so it arrived in a corner at half size with the rest of the grid
        // empty, and its small rooms rounded away. Framing on the rooms' own bounding box instead
        // takes that to 98 %, and because rooms stop rounding to nothing it LOSES FEWER ROOMS, not
        // more (10 rooms lost across the corpus today, 0 after — `tools/scan-eval/exp-frame.py`).
        //
        // ⚠ This was tried and reverted once, for two stated reasons. Both are answered here rather
        // than argued away:
        //   · "one stray rectangle inflates the frame and shrinks the whole home" — measured across
        //     20 real replies: removing the single most extreme room shrinks the frame by a median
        //     1.12× and at worst 1.29×, and the fill ladder above means a room's SIZE no longer
        //     follows the frame's area anyway. A stray box now costs tightness, not scale.
        //   · "it removed the fuzz suite's `no-clamp` bite" — the frame is CLAMPED TO THE PICTURE, so
        //     an unclamped box still falls outside it. The suite also gained a direct check that
        //     every box reaching this point lies inside the unit square, which is the property
        //     `sanitise` exists to guarantee and which that injection now fails outright.
        val frame = frameOf(rooms.map { it.box })
            ?: return ScanOutcome.Assisted(identified, AssistReason.TOO_FEW_PLACED, notes())
        val (cols, rows) = widenForWidest(gridFor(homeAspect(frame, imageAspect)), rooms.map { it.box })
        // ⭐ Snapped TOGETHER, not one at a time — see [WALL_TOLERANCE]. Rooms that share a wall on
        // the plan must share a grid line here, or the home arrives as a scatter of islands.
        val rects = snapAll(rooms.map { it.box }, frame, imageAspect, cols, rows)
        val snapped = rooms.mapIndexed { i, c -> c to rects[i] }

        // ---- 7b/8. ⭐ shape each room to the size PRINTED on the plan, then place them -------------
        // The reader reads text at ~95 % and guesses rectangles at 40–70 %, so where a caption states
        // the room's size that number beats the rectangle it came with. See RoomDimensions: on the
        // owner's flat four of six dimensioned rooms had their orientation inverted, and correcting
        // them moves the kitchen and the bedroom into different Vastu directions.
        //
        // ⭐ A room that could not be placed at all is RESCUED: it goes to the front of the queue and
        // the whole placement is redone. That keeps the ordinary rule — the smallest room keeps its
        // cells — while making "re-shaping must never cost a room" structural instead of hopeful.
        // Measured across the real corpus: 10 rooms silently lost today, 0 after. Nothing is mutated
        // while trying, so a discarded attempt leaves no trace on the rooms.
        val shaped = reshapeToPrinted(snapped, cols, rows)
        val items = shaped.mapIndexed { i, (c, rect) -> Triple(c, rect, snapped[i].second) }
        var rescued = emptySet<Candidate>()
        var chosen = resolveOverlaps(items, rescued)
        var guard = items.size
        while (guard-- > 0 && chosen.dropped.isNotEmpty() && !rescued.containsAll(chosen.dropped)) {
            rescued = rescued + chosen.dropped
            chosen = resolveOverlaps(items, rescued)
        }

        for (c in chosen.dropped) dropped += DroppedSpace(c.box.label, DropReason.OVERLAP_UNRESOLVABLE)
        for ((c, _, wasTrimmed) in chosen.rooms) if (wasTrimmed) c.flags += RoomFlag.OVERLAP_TRIMMED

        if (chosen.rooms.size < MIN_PLACED_ROOMS || chosen.rooms.size < rooms.size * MIN_PLACED_FRACTION) {
            // The grid could not hold what we read. Hand the rooms over unplaced rather than show a
            // half-eaten layout — the room list is still the bulk of the saving.
            return ScanOutcome.Assisted(identified, AssistReason.TOO_FEW_PLACED, notes())
        }

        // ⭐ A grid row or column that ends up entirely OUTSIDE the rooms is deleted, edge-inward.
        //
        // The per-axis fill guarantees the READ frame fills the grid — but the re-shapes then
        // shrink rooms to the size their sheet PRINTS, and where the sketch was inflated (the
        // template's fat strips, a room read wider than its caption) whole edge strips of grid go
        // dead. A dead edge strip is a statement — "this is part of your home and we found nothing
        // in it" — and it is false: the print evidence says the home ENDS where the rooms do. It
        // is also exactly what the owner photographs (Q14: a full empty strip down any side).
        //
        // The engine is untouched: it scores the FOOTPRINT — the bounding box of the placed rooms
        // — which collapse preserves cell-for-cell. Only the drawing gets smaller, which on a
        // small flat also means bigger, more tappable cells. Interior holes are never touched: an
        // empty region BETWEEN rooms is honest (an unread corridor), an empty region outside them
        // is not. Never below the editor's MIN_GRID. Corpus (audit-mapper.mjs): dead edge cells
        // 54 → 0, no room, orientation or outcome changes anywhere. The mirror pins the collapsed
        // grid on greencourt-336 and reshape-flat; `--inject=no-edge-collapse` goes red there.
        val minC = chosen.rooms.minOf { it.second.col }
        val minR = chosen.rooms.minOf { it.second.row }
        val maxC = chosen.rooms.maxOf { it.second.right }
        val maxR = chosen.rooms.maxOf { it.second.bottom }
        val outCols: Int
        val outRows: Int
        val placedRooms: List<Triple<Candidate, CellRect, Boolean>>
        if (minC > 0 || minR > 0 || maxC < cols || maxR < rows) {
            outCols = max(maxC - minC, MIN_GRID)
            outRows = max(maxR - minR, MIN_GRID)
            placedRooms = chosen.rooms.map { (c, r, t) ->
                Triple(c, CellRect(r.col - minC, r.row - minR, r.w, r.h), t)
            }
        } else {
            outCols = cols
            outRows = rows
            placedRooms = chosen.rooms
        }

        // The on-photo box for each room: the reader's own rectangle, composed onto the page. See
        // the note on [ScannedRoom.source] for why the printed caption does NOT get a say here even
        // though it decides the grid.
        val pageBoxes = placedRooms.map { pageSource(draft.building, it.first.box) }

        val out = placedRooms
            .mapIndexed { i, (c, rect, _) ->
                ScannedRoom(
                    c.type, c.box.label, rect, c.flags.toSet(), c.box.printedSize,
                    source = pageBoxes[i],
                    readInParts = c.parts,
                )
            }
            .sortedWith(compareBy({ it.rect!!.row }, { it.rect!!.col }))
        return ScanOutcome.Placed(outCols, outRows, out, notes())
    }

    /**
     * ⭐ The tint fix (owner, 4 Aug 2026: the tapped-room boxes are "mostly bigger"). Room boxes are
     * BUILDING-framed by prompt contract; the on-photo review draws [ScannedRoom.source] on the
     * whole photo. When the reply carries a sane building box (prompt v4), compose the room onto
     * the page: page = building.origin + room × building.size. Measured on 35 approved scans:
     * Green Court centre error 0.18–0.28 → 0.007–0.033 of the sheet; every overlay eyeballed.
     *
     * Without a sane box (older replies, recordings, a model that omits it) the room box passes
     * through untouched — exactly today's behaviour, which the "roughly" caption already covers.
     * Sanity: a real box, mostly inside the page, no sliver — a mad box must not fling every tint
     * into one corner.
     */
    fun pageSource(building: ScanBox?, box: ScanBox): ScanBox {
        val b = building ?: return box
        val sane = b.w > 0.05 && b.h > 0.05 && b.w <= 1.0 && b.h <= 1.0 &&
            b.x >= -0.02 && b.y >= -0.02 && b.x + b.w <= 1.05 && b.y + b.h <= 1.05
        if (!sane) return box
        return box.copy(
            x = b.x + box.x * b.w,
            y = b.y + box.y * b.h,
            w = box.w * b.w,
            h = box.h * b.h,
        )
    }

    /** One complete placement pass. Pure: it never touches a [Candidate]'s flags. */
    private class Attempt(
        val rooms: List<Triple<Candidate, CellRect, Boolean>>,
        val dropped: List<Candidate>,
    )

    // ---- pieces, each independently testable --------------------------------------------------

    /**
     * ⭐ Re-shape every room whose caption printed a size, to that size.
     *
     * Three deliberate choices, each of which could reasonably have gone the other way:
     *
     *  - **Total area is preserved, not imposed.** The scale is derived from what the reader already
     *    drew (cells it used ÷ square millimetres those rooms actually are), so a plan cannot suddenly
     *    demand more grid than it had. Only the *distribution* of area between rooms changes — which
     *    is the point, because a 127 sq ft lobby and a 15 sq ft toilet were being drawn three cells
     *    apart in size instead of nine.
     *  - **The top-left corner stays where the reader put it**, and the extent is replaced. This is
     *    not arbitrary: the reader locates a caption far better than it measures the box around it,
     *    so the origin is its stronger output and the extent its weaker one — anchor the strong,
     *    replace the weak. Measured against anchoring on the centre or on the nearest corner, it also
     *    produces the fewest collisions on the owner's plan (2 against 2 and 3), and rooms are never
     *    relocated, which is the standing rule the trim step exists to keep.
     *  - ⭐ **Orientation comes from the PRINTED ORDER — first number is the width — not from the
     *    drawing.** This was the other way round first, resolving each room against the rectangle the
     *    reader drew, and it quietly defeated the entire feature: the reader had called the passage
     *    three times wider than tall, so matching its sense kept it horizontal and merely restated the
     *    error at a new ratio. Trusting the printed order corrects **four of the six** dimensioned
     *    rooms on the owner's plan; deferring to the drawing corrected none of them. A sheet that
     *    prints depth first would flip every room the same way — a visible, consistent error the user
     *    can see and fix, which is a far better failure than the arbitrary one it replaces.
     *
     * Rooms whose caption prints no size are left exactly as they were, so a mixed plan works and a
     * plan with no dimensions at all is untouched.
     */
    /**
     * Fuse each contiguous run of same-labelled sections into one room. Everything else passes
     * through untouched and in its original order. See [MERGEABLE_RUN_TYPES] for why this is
     * balconies only.
     */
    private fun mergeRuns(typed: List<Candidate>): List<Candidate> {
        if (typed.size < 2) return typed
        val out = ArrayList<Candidate>(typed.size)
        val taken = BooleanArray(typed.size)
        for (i in typed.indices) {
            if (taken[i]) continue
            taken[i] = true
            val seed = typed[i]
            if (seed.type !in MERGEABLE_RUN_TYPES) { out += seed; continue }
            // Grow the run transitively: three sections in a line are two adjacencies, and the
            // middle one is what connects the ends. A queue rather than a single pass, so the order
            // the reader happened to list them in cannot change the answer.
            val run = ArrayList<Candidate>().apply { add(seed) }
            val queue = ArrayDeque<Candidate>().apply { add(seed) }
            val name = runKey(seed.box.label)
            while (queue.isNotEmpty()) {
                val head = queue.removeFirst()
                for (j in typed.indices) {
                    if (taken[j]) continue
                    val c = typed[j]
                    if (c.type != seed.type || runKey(c.box.label) != name) continue
                    if (!adjoins(head.box, c.box)) continue
                    taken[j] = true
                    run += c
                    queue += c
                }
            }
            out += if (run.size == 1) seed else combineRun(run)
        }
        return out
    }

    /** Captions compared for fusing: case and spacing are the sheet's, not the room's. */
    private fun runKey(label: String): String = label.uppercase().replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    /**
     * Do these two sections form part of one strip? True when they share most of their depth on one
     * axis AND touch along the other. Tested both ways round, so a run may lie along either axis.
     */
    private fun adjoins(a: ScanBox, b: ScanBox): Boolean =
        // side by side: share the y band, touch along x.
        continuous(a.y, a.h, b.y, b.h, a.x, a.w, b.x, b.w) ||
            // stacked: share the x band, touch along y.
            continuous(a.x, a.w, b.x, b.w, a.y, a.h, b.y, b.h)

    /** `band*` is the axis the two must SHARE; `run*` is the axis they must be continuous along. */
    private fun continuous(
        aBand: Double, aBandSize: Double, bBand: Double, bBandSize: Double,
        aRun: Double, aRunSize: Double, bRun: Double, bRunSize: Double,
    ): Boolean {
        val thinner = min(aBandSize, bBandSize)
        if (thinner <= 0.0) return false
        val shared = min(aBand + aBandSize, bBand + bBandSize) - max(aBand, bBand)
        if (shared < thinner * RUN_BAND_SHARE) return false
        val gap = max(aRun, bRun) - min(aRun + aRunSize, bRun + bRunSize)
        return gap <= RUN_MAX_GAP
    }

    /**
     * One room from a run of sections: the box that encloses them all.
     *
     * ⚠ [ScanBox.printedSize] is deliberately left EMPTY. It is the number [reshapeToPrinted] shapes
     * a room from, and no single section's printed size describes the whole strip — carrying one
     * would shrink the fused balcony back to a third of itself. The sizes are not lost: they move to
     * [Candidate.parts] and the check-what-we-read screen prints all of them, so the user still
     * checks our reading against their own paper, which is the point of that screen.
     */
    private fun combineRun(run: List<Candidate>): Candidate {
        val ordered = run.sortedWith(compareBy({ it.box.y }, { it.box.x }))
        val x = ordered.minOf { it.box.x }
        val y = ordered.minOf { it.box.y }
        val right = ordered.maxOf { it.box.x + it.box.w }
        val bottom = ordered.maxOf { it.box.y + it.box.h }
        return Candidate(
            box = ScanBox(
                label = ordered.first().box.label,
                x = x,
                y = y,
                w = right - x,
                h = bottom - y,
                // The least sure section speaks for the strip: fusing must not invent confidence.
                confidence = ordered.minOf { it.box.confidence },
                printedSize = "",
            ),
            type = ordered.first().type,
            flags = ordered.flatMapTo(mutableSetOf()) { it.flags },
            parts = ordered.map { it.box.printedSize }.filter { it.isNotBlank() },
        )
    }

    private fun reshapeToPrinted(
        snapped: List<Pair<Candidate, CellRect>>,
        cols: Int,
        rows: Int,
    ): List<Pair<Candidate, CellRect>> {
        val printed = snapped.map { RoomDimensions.of(it.first.box) }
        val cellTotal = snapped.indices.filter { printed[it] != null }
            .sumOf { (snapped[it].second.w * snapped[it].second.h).toDouble() }
        val printedTotal = printed.filterNotNull().sumOf { it.area }
        if (cellTotal <= 0.0 || printedTotal <= 0.0) return snapped
        val cellsPerSquareMm = cellTotal / printedTotal

        // ⭐ The plan's own wall lines, as the shared snap left them. A re-shaped room's new edge
        // clicks onto one of these when it lands within [MAGNET_REACH] — otherwise re-shaping
        // re-opens the very moats the shared snap just closed, because it anchors the top-left
        // corner and pulls the other two edges inward. See [MAGNET_REACH].
        val xLines = snapped.flatMapTo(HashSet()) { listOf(it.second.col, it.second.right) }
        val yLines = snapped.flatMapTo(HashSet()) { listOf(it.second.row, it.second.bottom) }

        // ⭐ Where the printed size and the observed walls disagree by ONE cell, the gap breaks
        // toward the UNSIZED side. A room read flush between two walls four cells apart whose
        // caption prints three has to give one flushness up; giving up the wrong one reopens the
        // §2c moat — the master toilet a cell adrift of the master bedroom, the defect the owner
        // photographed. A neighbour that carries a printed size is position evidence (its own
        // re-shape anchors that shared wall), so the re-shaped room SLIDES one cell to stay flush
        // with the sized neighbour and lets the gap open against the unsized one. The slide stays
        // inside the room's own read span (never relocation), never resizes (printed proportions
        // untouched), is skipped when both sides are sized or both unsized (no evidence either
        // way), and only enters cells no other read rectangle holds (it may close a rounding gap,
        // never open a fight with a neighbour the reader drew there). Proven on the jittered
        // tower-D1 sheet (ScanWallLinesTest): both §2c adjacencies hold AND the living/dining stays
        // strictly the biggest room; corpus-wide the orientation agreement holds at 140/148 and one
        // previously-lost room returns.
        fun flushSized(test: (CellRect) -> Boolean) =
            snapped.indices.any { j -> printed[j] != null && test(snapped[j].second) }
        fun rightFlushSized(r: CellRect) =
            flushSized { o -> o !== r && o.col == r.right && o.row < r.bottom && r.row < o.bottom }
        fun leftFlushSized(r: CellRect) =
            flushSized { o -> o !== r && o.right == r.col && o.row < r.bottom && r.row < o.bottom }
        fun bottomFlushSized(r: CellRect) =
            flushSized { o -> o !== r && o.row == r.bottom && o.col < r.right && r.col < o.right }
        fun topFlushSized(r: CellRect) =
            flushSized { o -> o !== r && o.bottom == r.row && o.col < r.right && r.col < o.right }
        fun stripFree(self: CellRect, c0: Int, r0: Int, ww: Int, hh: Int) =
            snapped.none { (_, o) -> o !== self && o.overlaps(CellRect(c0, r0, ww, hh)) }

        val slidDown = mutableListOf<CellRect>()   // READ rects of rooms the slide moved — the
        val slidRight = mutableListOf<CellRect>()  // unsized cascade below re-anchors to them
        val shaped = snapped.mapIndexed { i, (candidate, rect) ->
            val size = printed[i] ?: return@mapIndexed candidate to rect
            val targetCells = size.area * cellsPerSquareMm
            if (!targetCells.isFinite() || targetCells <= 0.0) return@mapIndexed candidate to rect

            val ratio = size.ratio
            if (!ratio.isFinite() || ratio <= 0.0) return@mapIndexed candidate to rect

            // ⚠ Shrink to fit the grid PROPORTIONALLY. Computing the width, then clamping the height
            // to the grid, silently INVERTS a tall room in a shallow grid — a 2 950 × 4 200 kitchen
            // came out 6 wide × 5 deep, which is the very error this is here to remove. The fuzz
            // suite's PRINTED-ORIENTATION check found it. When area and proportion cannot both be
            // honoured, proportion wins: it is the part that decides the room's Vastu direction.
            var wf = sqrt(targetCells * ratio)
            var hf = sqrt(targetCells / ratio)
            if (!wf.isFinite() || !hf.isFinite() || wf <= 0.0 || hf <= 0.0) return@mapIndexed candidate to rect
            val fit = min(1.0, min(cols / wf, rows / hf))
            wf *= fit
            hf *= fit
            // Rounding is monotonic, so it can flatten a near-square room to a square but can never
            // turn a taller-than-wide room into a wider-than-tall one.
            val w = wf.roundToInt().coerceIn(1, cols)
            val h = hf.roundToInt().coerceIn(1, rows)
            // Keep the corner the reader gave us; clamp back in if the new extent runs off the grid.
            val col = rect.col.coerceIn(0, cols - w)
            val row = rect.row.coerceIn(0, rows - h)
            // …then let the far edges click onto the plan's own walls if they are within reach.
            //
            // ⚠ But NEVER at the cost of the orientation. Found by the fuzz suite in 363 of 20 000
            // random replies: a magnet that stretches a 1 350 × 2 250 toilet one cell wider turns a
            // room the plan prints deeper-than-wide into a wider-than-deep one — which is the exact
            // error the whole printed-size feature exists to remove. Closing a gap must never buy
            // itself a room facing the wrong way, so the candidates are tried most-magnetic first
            // and the first one that still agrees with the sheet wins. Doing nothing always agrees,
            // because the rounding above is monotonic, so there is always an answer.
            // ⭐ The home's OUTER WALL is the strongest position evidence there is. The frame IS
            // the home (the letterbox fix), so a room READ flush against the grid's east or south
            // edge was read against the building's own outer wall — and nothing can stand between
            // a room and the outside of the building. The top-left anchor was abandoning exactly
            // that: the owner's 336 sq ft bedroom, read flush to the east wall his sheet puts it
            // on, shrank to its printed width and drifted two columns off it, leaving a dead block
            // on the home's east edge. The shrink slack belongs INTERIOR (walls, wardrobes, the
            // sketch's own inflation), never between a room and an outer wall it was read
            // touching. West and north flushness survive automatically (the top-left corner is
            // the anchor); this is the same promise for the other two walls. A room read flush
            // BOTH sides of an axis keeps the top-left default — either end abandons an outer
            // wall, so the evidence ties.
            //
            // ⚠ Same discipline as the slide below: the anchor may only enter cells NO other read
            // rectangle holds, and its band is CLIPPED to the room's own read span in the cross
            // axis — an east anchor owns only the horizontal displacement, so a fight the shaped
            // rect's GROWTH picks (a grown row reaching a neighbour below, which it does at
            // either position) stays the trimmer's business. Without the guard it shoved a hall,
            // read mid-plan, into the bedroom read beside it, and the trimmer crushed the hall to
            // a sliver. Corpus (audit-mapper.mjs): orientation 157/166 → 159/166, and the owner's
            // 336 sq ft plan draws its bedroom, lobby and passage on the east wall his sheet puts
            // them on. The mirror pins this on greencourt-336-clean; `--inject=no-edge-anchor`
            // restores the drift and goes red there.
            fun bandFree(self: CellRect, c0: Int, r0: Int, ww: Int, hh: Int) =
                ww <= 0 || hh <= 0 || stripFree(self, c0, r0, ww, hh)
            val bandETop = max(row, rect.row)
            val bandEBottom = min(row + h, rect.bottom)
            val anchoredE = rect.right == cols && rect.col > 0 && col + w != cols &&
                bandFree(rect, col + w, bandETop, (cols - w) - col, bandEBottom - bandETop)
            var col2 = if (anchoredE) cols - w else col
            val bandSLeft = max(col2, rect.col)
            val bandSRight = min(col2 + w, rect.right)
            val anchoredS = rect.bottom == rows && rect.row > 0 && row + h != rows &&
                bandFree(rect, bandSLeft, row + h, bandSRight - bandSLeft, (rows - h) - row)
            var row2 = if (anchoredS) rows - h else row
            if (!anchoredE && col2 + w == rect.right - 1 && rightFlushSized(rect) && !leftFlushSized(rect) &&
                stripFree(rect, col2 + w, row2, 1, h)
            ) { col2 += 1; slidRight += rect }
            if (!anchoredS && row2 + h == rect.bottom - 1 && bottomFlushSized(rect) && !topFlushSized(rect) &&
                stripFree(rect, col2, row2 + h, w, 1)
            ) { row2 += 1; slidDown += rect }
            val freeRight = col2 + w
            val freeBottom = row2 + h
            val right = magnet(freeRight, xLines, col2 + 1, cols)
            val bottom = magnet(freeBottom, yLines, row2 + 1, rows)
            val free = agreement(w, h, ratio)
            val (r, b) = listOf(
                right to bottom,
                right to freeBottom,
                freeRight to bottom,
                freeRight to freeBottom,
            ).first { (rr, bb) -> agreement(rr - col2, bb - row2, ratio) >= free }
            candidate to CellRect(col2, row2, r - col2, b - row2)
        }
        // ⭐ The unsized cascade: a room with no printed size that was read FLUSH on the side a
        // slide vacated rides one cell along with it. Without this the slide preserved the sized
        // wall but set its unsized neighbour adrift — on tower-D1 each balcony floated one cell off
        // the bedroom it belongs to (a fresh §2c moat) and the vacated strips merged into a
        // seventeen-cell hole along the north edge. One pass, unsized rooms only, one cell, only
        // into cells that are free once the slide is accounted for; the moved rect becomes the
        // room's own basis, exactly as a re-shaped rect does.
        val cascaded = shaped.mapIndexed { i, pair ->
            if (printed[i] != null) return@mapIndexed pair
            val (candidate, r) = pair
            var col = r.col
            var row = r.row
            if (slidDown.any { it.row == r.bottom && it.col < r.right && r.col < it.right } &&
                shaped.none { (_, o) -> o !== r && o.overlaps(CellRect(col, row + 1, r.w, r.h)) } &&
                row + 1 + r.h <= rows
            ) row += 1
            if (slidRight.any { it.col == r.right && it.row < r.bottom && r.row < it.bottom } &&
                shaped.none { (_, o) -> o !== r && o.overlaps(CellRect(col + 1, row, r.w, r.h)) } &&
                col + 1 + r.w <= cols
            ) col += 1
            if (col == r.col && row == r.row) pair else candidate to CellRect(col, row, r.w, r.h)
        }
        // ⭐ The STRIP pass — a room whose caption prints ONE dimension (`BALCONY 1825 WIDE`) gets
        // that dimension as its printed DEPTH, on the same linear scale the pair-sized rooms set.
        // Its length and its position stay the reader's (arrangement evidence, exactly like a pair
        // room's corner), so only the strip's narrow axis changes — a strip may never flip against
        // the way it was read, and the depth is also capped by the grid's cross axis (a 10x4
        // letterbox grid cannot hold a 5-cell depth; the fuzz found it trying).
        //
        // The ANCHOR is the slide doctrine applied to a resize: the strip keeps the long wall it
        // shares with a PAIR-SIZED room (that neighbour's own re-shape anchors the shared wall —
        // position evidence) and the gap opens toward the sketch side. Both sides sized → the two
        // walls pin the strip and its caption is outvoted: skipped. Neither → the grid edge is the
        // home's outer wall, so an edge-flush strip keeps the edge. Runs AFTER the cascade so a
        // strip that rode a slide re-shapes from where it now sits. Before this rule the owner's
        // 336 sq ft plan drew its balcony three rows deep — a quarter of the whole grid, printed
        // 1 825 mm — towering over the bedroom it belongs to. The mirror pins the strip's exact
        // depth on greencourt-336-clean and reshape-flat; `--inject=no-strip-captions` goes red.
        val linear = sqrt(cellsPerSquareMm)
        if (!linear.isFinite() || linear <= 0.0) return cascaded
        return cascaded.mapIndexed { i, pair ->
            if (printed[i] != null) return@mapIndexed pair
            val depthMm = RoomDimensions.stripDepthOf(pair.first.box) ?: return@mapIndexed pair
            val (candidate, r) = pair
            if (r.w == r.h) return@mapIndexed pair   // no long axis — which way it runs is unreadable
            val wide = r.w > r.h                     // a wide strip's depth is its HEIGHT; a tall one's, its WIDTH
            val long = if (wide) r.w else r.h
            val d = (depthMm * linear).roundToInt().coerceIn(1, min(long, if (wide) rows else cols))
            if (d == (if (wide) r.h else r.w)) return@mapIndexed pair
            fun sizedAt(test: (CellRect) -> Boolean) = cascaded.indices.any { j ->
                cascaded[j].second !== r && printed[j] != null && test(cascaded[j].second)
            }
            val out: CellRect = if (wide) {
                val below = sizedAt { o -> o.row == r.bottom && o.col < r.right && r.col < o.right }
                val above = sizedAt { o -> o.bottom == r.row && o.col < r.right && r.col < o.right }
                if (below && above) return@mapIndexed pair
                val rowOut = when {
                    below -> r.bottom - d
                    above -> r.row
                    r.bottom == rows && r.row > 0 -> rows - d
                    else -> r.row
                }
                CellRect(r.col, rowOut.coerceIn(0, rows - d), r.w, d)
            } else {
                val rightS = sizedAt { o -> o.col == r.right && o.row < r.bottom && r.row < o.bottom }
                val leftS = sizedAt { o -> o.right == r.col && o.row < r.bottom && r.row < o.bottom }
                if (rightS && leftS) return@mapIndexed pair
                val colOut = when {
                    rightS -> r.right - d
                    leftS -> r.col
                    r.right == cols && r.col > 0 -> cols - d
                    else -> r.col
                }
                CellRect(colOut.coerceIn(0, cols - d), r.row, d, r.h)
            }
            candidate to out
        }
    }

    /** The nearest wall line to [edge] within [MAGNET_REACH], or [edge] itself when none is close. */
    private fun magnet(edge: Int, lines: Set<Int>, lowest: Int, highest: Int): Int {
        val best = lines.filter { it in lowest..highest }.minByOrNull { kotlin.math.abs(it - edge) }
        return if (best != null && kotlin.math.abs(best - edge) <= MAGNET_REACH) best else edge
    }

    /**
     * How well a [w] × [h] rectangle runs the way a room printed at this width-to-depth [ratio]
     * should: **2** it runs the right way, **1** it is square, **0** it runs the wrong way.
     *
     * ⚠ A SCORE, not a yes/no, and the difference matters. A plain "does it agree?" test let the
     * magnet stretch a kitchen printed 6'-11" × 9'-7" from deeper-than-wide to *square* — still not
     * wrong, but no longer right, and `ScanReshapeTest` caught it. The rule is that clicking onto a
     * wall may never make a room agree with its own plan LESS than it already did. Doing nothing
     * always scores the same as itself, so there is always an answer.
     */
    private fun agreement(w: Int, h: Int, ratio: Double): Int = when {
        ratio == 1.0 -> 2
        w == h -> 1
        (ratio > 1.0) == (w > h) -> 2
        else -> 0
    }


    /** Drop non-finite / inverted / off-plan rectangles; clamp the merely sloppy ones back in. */
    private fun sanitise(b: ScanBox, flags: MutableSet<RoomFlag>): ScanBox? {
        if (!b.x.isFinite() || !b.y.isFinite() || !b.w.isFinite() || !b.h.isFinite()) return null
        if (b.w <= 0.0 || b.h <= 0.0) return null
        val x0 = b.x.coerceIn(0.0, 1.0)
        val y0 = b.y.coerceIn(0.0, 1.0)
        val x1 = (b.x + b.w).coerceIn(0.0, 1.0)
        val y1 = (b.y + b.h).coerceIn(0.0, 1.0)
        if (x1 <= x0 || y1 <= y0) return null
        val clamped = b.copy(x = x0, y = y0, w = x1 - x0, h = y1 - y0)
        if (clamped.x != b.x || clamped.y != b.y || clamped.w != b.w || clamped.h != b.h) {
            flags += RoomFlag.OUT_OF_BOUNDS_CLAMPED
        }
        val conf = if (b.confidence.isFinite()) b.confidence.coerceIn(0.0, 1.0) else 0.0
        return clamped.copy(confidence = conf)
    }

    /**
     * ⭐ What the SAME reply reads as with the 2D gate ignored — the escape hatch behind the "read
     * it anyway" offer. Null unless there is a real layout or room list behind it, so the offer is
     * never made when it leads nowhere.
     *
     * Only [RefusalReason.NOT_2D] gets one, and that is the point. Every other refusal is a fact
     * about the reply we can check ourselves — no room names came back, two homes on the sheet,
     * nothing mapped to a room — and arguing with those would be arguing with our own arithmetic.
     * "This is a 3D picture" is the one refusal that is purely the model's opinion, and it is a
     * measurably unreliable one: on `plan-007` the primary model answers 3D with 0.99 confidence
     * while admitting in the same breath that it CAN read the room names, and the sheet is a
     * perfectly flat overhead render with every size printed on it (plan doc §3u).
     *
     * ⚠ The recursion is finite by construction: the retry runs with `planType` already rewritten
     * to [PlanImageType.TWO_D_PLAN], so its own triage can never return [RefusalReason.NOT_2D]
     * again and can never ask for a third pass.
     */
    private fun ifRead(
        reason: RefusalReason,
        draft: ScanDraft,
        imageAspect: Double?,
    ): ScanOutcome? {
        if (reason != RefusalReason.NOT_2D) return null
        return when (
            val retry = map(draft.copy(planType = PlanImageType.TWO_D_PLAN), imageAspect)
        ) {
            is ScanOutcome.Refused -> null
            is ScanOutcome.Assisted -> retry
            // ⭐⭐ A DRAWN LAYOUT IS NEVER OFFERED HERE — see [AssistReason.ANGLED_VIEW]. If the
            // picture really was taken at an angle, its rectangles describe the camera and not the
            // floor, and a confident wrong layout is the precise harm the 2D gate exists to stop.
            // Measured on the two genuinely tilted sheets in the corpus: both map to Placed, and
            // the aerial lands only 4 of the 14 rooms it read. We keep what the reader is good at
            // (the names) and hand the placing back to the person who knows the answer.
            // `rect = null` IS unplaced — the same shape every other Assisted outcome hands over.
            // ⭐ …UNLESS THE SHEET PRINTS ITS OWN DIMENSIONS (15 Aug 2026). A marketing render is
            // drawn to look nice, never to be built from, so it never prints "10'0" X 12'0"" on a
            // room. A plan sheet always does. That is evidence off the SHEET, not the reader's
            // opinion of the camera — which is the one thing the 2D gate was already documented as
            // not trusting.
            //
            // Measured on freshly recorded reads of the corpus, and the separation is total:
            //   plan-030 — the genuinely tilted street aerial, roof off, cars and neighbours:
            //              14 rooms read, 0 carry a printed size   →   0 %  → still stripped
            //   plan-031 — flat sheet the reader mislabelled 3D:    9 of 9 sized  → 100 %
            //   plan-018 — flat sheet the reader mislabelled 3D:   15 of 15 sized → 100 %
            // Placement share does NOT separate them (the aerial places 11 of its 14 rooms when
            // forced, 79 %) — which is why the obvious "trust it if most rooms placed" rule was
            // measured and rejected. Printed sizes do separate them, absolutely.
            //
            // ⚠ The refusal itself is UNCHANGED: the user is still told and still chooses. All that
            // changes is what the "read it anyway" button hands over — a real layout instead of a
            // bare room list. An unplaced hand-over routes to the guided grid, and a scanned home
            // must never land there (owner, 15 Aug 2026).
            is ScanOutcome.Placed ->
                if (sheetPrintsItsOwnSizes(draft.rooms)) retry
                else ScanOutcome.Assisted(
                    retry.rooms.map { it.copy(rect = null) }, AssistReason.ANGLED_VIEW, retry.notes,
                )
        }
    }

    /**
     * Does this sheet print its own room dimensions? The test that tells a PLAN apart from a
     * PICTURE OF A HOUSE when the reader cannot.
     *
     * Deliberately blunt — a share, not a score, and set with a wide margin on both sides. The
     * measured cases sit at 0 % (tilted aerial) and 100 % (two mislabelled plan sheets), so
     * anything between roughly a third and nine tenths would separate them equally well; [MIN_SIZED_SHARE]
     * is placed where a sheet has to be MOSTLY dimensioned to qualify. The floor of
     * [MIN_PRINTED_TO_FIT] stops a two-room reply scoring 100 % on one caption.
     *
     * ⚠ Only ever used to decide whether a LAYOUT survives the 2D gate. It must never become a
     * refusal of its own: plenty of honest plan sheets print no sizes at all, and they are read
     * today exactly as they always were.
     */
    internal fun sheetPrintsItsOwnSizes(rooms: List<ScanBox>): Boolean {
        if (rooms.size < MIN_PRINTED_TO_FIT) return false
        val sized = rooms.count { RoomDimensions.of(it) != null }
        return sized >= MIN_PRINTED_TO_FIT && sized.toDouble() / rooms.size >= MIN_SIZED_SHARE
    }

    /** The three refusals, in the order a user would want to hear them. */
    private fun triage(draft: ScanDraft, boxes: List<ScanBox>): RefusalReason? {
        when (draft.planType) {
            PlanImageType.THREE_D_RENDER -> return RefusalReason.NOT_2D
            PlanImageType.NOT_A_PLAN -> return RefusalReason.NOT_A_PLAN
            // UNKNOWN means the reply predates the triage prompt — "not stated", never "it's fine"
            // and never "it's 3D". The remaining gates still apply.
            PlanImageType.TWO_D_PLAN, PlanImageType.UNKNOWN -> Unit
        }
        // A sheet with several homes on it: UNIT-1 … UNIT-4 (seen on plan-001 of the real corpus).
        // Two is enough — one "UNIT 1" caption on a single-home plan is a title, not a second home.
        if (boxes.count { RoomLabels.isUnitLabel(it.label) } >= 2) return RefusalReason.MULTI_UNIT
        if (draft.unreadable || !draft.hasRoomLabels) return RefusalReason.NO_LABELS
        if (boxes.isEmpty()) return RefusalReason.NO_ROOMS
        return null
    }

    /**
     * The part of the picture the HOME occupies: the bounding box of the rooms we are going to draw,
     * clamped to the picture itself.
     *
     * ⭐ The clamp is not tidiness. It is what keeps the `no-clamp` fault injection biting: a box the
     * reader put off the page still falls outside a frame that can never leave the unit square, so
     * removing [sanitise]'s clamp still produces a visibly wrong layout rather than a plausible one.
     */
    internal fun frameOf(boxes: List<ScanBox>): ScanBox? {
        if (boxes.isEmpty()) return null
        val x0 = max(0.0, boxes.minOf { it.x })
        val y0 = max(0.0, boxes.minOf { it.y })
        val x1 = min(1.0, boxes.maxOf { it.x + it.w })
        val y1 = min(1.0, boxes.maxOf { it.y + it.h })
        if (x1 <= x0 || y1 <= y0) return null
        return ScanBox("", x0, y0, x1 - x0, y1 - y0, 1.0)
    }

    /** Width ÷ height of the HOME in real pixels — the frame's proportions, not the sheet's. */
    internal fun homeAspect(frame: ScanBox, imageAspect: Double?): Double {
        val a = if (imageAspect != null && imageAspect.isFinite() && imageAspect > 0.0) imageAspect else 1.0
        return (frame.w * a) / frame.h
    }

    /**
     * The drawing grid's shape, from the HOME's proportions. The longer side gets the most cells we
     * allow, because a scanned home carries 9–25 rooms and resolution is what stops small ones (a
     * toilet, a pooja niche) rounding away — see [snap] and plan doc §7.
     *
     * ⚠ It used to take the SHEET's proportions, which is a different question with a different
     * answer: a square sheet carrying a tall narrow flat gave a square grid, and the flat then used
     * six of its ten columns with four standing empty.
     */
    fun gridFor(imageAspect: Double?): Pair<Int, Int> {
        val a = imageAspect
        if (a == null || !a.isFinite() || a <= 0.0) return DEFAULT_GRID to DEFAULT_GRID
        return if (a >= 1.0) {
            MAX_GRID to (MAX_GRID / a).roundToInt().coerceIn(MIN_GRID, MAX_GRID)
        } else {
            (MAX_GRID * a).roundToInt().coerceIn(MIN_GRID, MAX_GRID) to MAX_GRID
        }
    }

    /**
     * ⭐ A reply that overruns the page is SHRUNK to fit it, uniformly. See [MAX_OVERFLOW] for why
     * this replaced cutting the overrun off, and for why it cannot disturb a reply that already fits.
     *
     * One scale for both axes, so the arrangement is carried across untouched — a per-axis fit would
     * squash the home in whichever direction the reader happened to overrun, which is the same class
     * of distortion the printed sizes exist to remove.
     */
    internal fun shrinkToPage(boxes: List<ScanBox>): List<ScanBox> {
        val real = boxes.filter {
            it.x.isFinite() && it.y.isFinite() && it.w.isFinite() && it.h.isFinite()
        }
        if (real.isEmpty()) return boxes
        // ⭐ ONE room outside the page is that room being wrong; SEVERAL is the layout running long.
        // See [MIN_SPILLED_ROOMS] — without this a single hallucinated rectangle shrinks every real
        // room to make space for it.
        val spilled = real.count { it.x < 0.0 || it.y < 0.0 || it.x + it.w > 1.0 || it.y + it.h > 1.0 }
        if (spilled < MIN_SPILLED_ROOMS) return boxes
        val x0 = min(0.0, real.minOf { it.x })
        val y0 = min(0.0, real.minOf { it.y })
        val x1 = max(1.0, real.maxOf { it.x + it.w })
        val y1 = max(1.0, real.maxOf { it.y + it.h })
        val span = max(x1 - x0, y1 - y0)
        // Already inside the page, or so far outside that it is not a layout: leave it exactly alone
        // and let [sanitise] do what it has always done.
        if (span <= 1.0 || span > MAX_OVERFLOW) return boxes
        val s = 1.0 / span
        return boxes.map {
            if (!it.x.isFinite() || !it.y.isFinite() || !it.w.isFinite() || !it.h.isFinite()) it
            else it.copy(x = (it.x - x0) * s, y = (it.y - y0) * s, w = it.w * s, h = it.h * s)
        }
    }

    /**
     * ⭐ Widen the drawing grid until the home's BIGGEST room can be drawn the way its own plan
     * prints it. Never narrows, never exceeds [MAX_GRID], and does nothing at all unless the sizes
     * printed on the plan prove the grid is too narrow to be honest.
     *
     * ⚠ Measured on the owner's flat. The reader's rectangles put fifteen rooms at four distinct
     * x positions, so the home's apparent proportions came out far narrower than the flat really is
     * and the grid was built four columns wide. His living/dining is printed 7.25 m × 4.30 m —
     * plainly wider than deep — and in four columns there was no way to draw it that way, so it came
     * out taller than wide and the overlap trimmer ate it. Four of his eleven rooms were drawn
     * against their own printed shape; widening the grid takes that to two, and it changes no other
     * plan in the corpus, because on every one of them the grid was already wide enough.
     *
     * The room's fair share of the grid is its printed area over the total printed area — arithmetic
     * on text, so it never asks the model anything (S1 untouched).
     */
    internal fun widenForWidest(grid: Pair<Int, Int>, boxes: List<ScanBox>): Pair<Int, Int> {
        val (cols, rows) = grid
        val sizes = boxes.mapNotNull { RoomDimensions.of(it) }.filter { it.area > 0.0 }
        if (sizes.isEmpty()) return grid
        val total = sizes.sumOf { it.area }
        val widest = sizes.maxByOrNull { it.area } ?: return grid
        val ratio = widest.ratio
        if (!ratio.isFinite() || ratio <= 1.0) return grid   // not a wider-than-deep room; nothing to protect
        val share = widest.area / total
        if (!share.isFinite() || share <= 0.0) return grid
        // The cells that room should get, and how wide it must be to keep its printed proportions.
        val need = sqrt(cols * rows * share * ratio).roundToInt()
        // It needs a column to spare, or it fills the grid edge to edge and its neighbours have
        // nowhere to be — which is the state the trimmer turns into a lost or inverted room.
        if (need < cols) return grid
        return min(MAX_GRID, need + 1) to rows
    }

    /**
     * Normalised rectangle → whole cells, measured against [frame] rather than against the picture.
     *
     * ⭐ The home FILLS the grid — one scale per axis, no letterbox. The old uniform fit (one scale,
     * centred in the slack axis) protected room proportions back when the reader's rectangles were
     * the only source of shape; since the printed sizes took that job (§3k), every sized room's
     * proportions are re-imposed from the sheet's own text and the protection protected nothing.
     * What the letterbox DID do, measured on the owner's flat: the widened five-column grid held
     * 3.64 columns of content, the 0.68-cell margin rounded into a full empty column on the WEST —
     * his home drawn off the west wall his sheet puts it on — and the very column [widenForWidest]
     * added for his living/dining was eaten as margin, so the living room stayed square. Per-axis
     * fill hands the widened column to the rooms. Corpus (audit-mapper.mjs), together with the
     * widest-gap wall split and the sized-flush slide in reshapeToPrinted: final orientation
     * agreement 138/148 → 140/148, dead edge cells 62 → 32, rooms lost outright 2 → 1, no pinned
     * plan changes outcome.
     * The stretch is bounded: the grid's whole-cell shape comes from the home's own aspect, so the
     * slack is rounding plus deliberate widening — under one cell per axis on every recorded plan.
     *
     * Each EDGE is rounded independently rather than rounding the position and the size: rounding the
     * size makes two rooms that were flush on the plan drift apart or overlap, and adjacency is what
     * the footprint is made of.
     *
     * ⭐ A room may round SMALL, never away. It used to be dropped when both its edges rounded
     * together — silently costing the plan a scored room, and costing it most often to the smallest
     * rooms, which are the toilets. One cell in the right place is a room the user can see and
     * correct; a dropped room is one they never knew to look for.
     */
    /**
     * ⭐⭐ Every room snapped to the plan's own WALL LINES, in one pass.
     *
     * This is [snap] applied to the whole plan at once, with one addition that turns out to be the
     * difference between a home and a scatter of boxes: **edges within [WALL_TOLERANCE] of each
     * other are first agreed to be the same wall, and then rounded once.** Two rooms that touch on
     * the sheet therefore touch on the grid, instead of landing a cell apart because one edge
     * rounded up and the other down.
     *
     * Nothing is moved to make it fit and no room is created or destroyed; the only thing that
     * changes is that a set of near-equal numbers becomes one number before it is rounded. On a
     * tidy fixture — where the reader already reports shared walls at identical values — this is a
     * no-op, which is why it does not disturb the plans already pinned by tests.
     */
    internal fun snapAll(
        boxes: List<ScanBox>,
        frame: ScanBox,
        imageAspect: Double?,
        cols: Int,
        rows: Int,
    ): List<CellRect> {
        val a = if (imageAspect != null && imageAspect.isFinite() && imageAspect > 0.0) imageAspect else 1.0
        val pw = frame.w * a
        val ph = frame.h
        val sx = cols / pw
        val sy = rows / ph
        fun cx(v: Double) = ((v - frame.x) * a) * sx
        fun cy(v: Double) = (v - frame.y) * sy

        val xLine = wallLines(boxes.flatMap { listOf(cx(it.x), cx(it.x + it.w)) }, cols)
        val yLine = wallLines(boxes.flatMap { listOf(cy(it.y), cy(it.y + it.h)) }, rows)

        return boxes.map { b ->
            // The same "may round SMALL, never away" rule [snap] documents: a room the grid cannot
            // resolve becomes one cell the user can see and correct, never nothing at all.
            val left = xLine(cx(b.x)).coerceIn(0, cols - 1)
            val right = xLine(cx(b.x + b.w)).coerceIn(left + 1, cols)
            val top = yLine(cy(b.y)).coerceIn(0, rows - 1)
            val bottom = yLine(cy(b.y + b.h)).coerceIn(top + 1, rows)
            CellRect(left, top, right - left, bottom - top)
        }
    }

    /**
     * Group near-equal edge positions into shared wall lines and round each group once.
     *
     * Single-linkage with a hard cap on how wide a group may grow ([WALL_MAX_SPAN]) — without the
     * cap, a run of edges each within tolerance of the last chains into one enormous "wall" and
     * crushes the rooms between them.
     *
     * ⭐ A chain that outgrows the cap is split at its LARGEST INTERNAL GAP, recursively — never cut
     * off at whichever edge happened to arrive when the cap was reached. The greedy cut was
     * order-fragile: on the jittered tower-D1 sheet the living room's top edge chained onto the
     * master bedroom's bottom edge, dragged the span over the cap, and the cut then landed BETWEEN
     * the master bedroom's bottom and the master toilet's top — the same physical wall — putting
     * the two rooms on different lines. Splitting at the widest gap puts the seam where the
     * geometry says a seam is; tolerance and cap semantics are unchanged.
     */
    private fun wallLines(edges: List<Double>, max: Int): (Double) -> Int {
        val sorted = edges.distinct().sorted()
        val assigned = HashMap<Double, Int>(sorted.size)
        fun emit(lo: Int, hi: Int) {
            if (hi > lo && sorted[hi] - sorted[lo] > WALL_MAX_SPAN) {
                var cut = lo
                var widest = -1.0
                for (k in lo until hi) {
                    val gap = sorted[k + 1] - sorted[k]
                    if (gap > widest) { widest = gap; cut = k }
                }
                emit(lo, cut)
                emit(cut + 1, hi)
                return
            }
            val line = ((lo..hi).sumOf { sorted[it] } / (hi - lo + 1)).roundToInt().coerceIn(0, max)
            for (k in lo..hi) assigned[sorted[k]] = line
        }
        var i = 0
        while (i < sorted.size) {
            var j = i + 1
            while (j < sorted.size && sorted[j] - sorted[j - 1] <= WALL_TOLERANCE) j++
            emit(i, j - 1)
            i = j
        }
        return { e -> assigned[e] ?: e.roundToInt().coerceIn(0, max) }
    }

    // The single-box snap() that used to sit here was dead code — snapAll replaced every caller —
    // and it still carried the letterboxed fit. Deleted rather than updated, so the old behaviour
    // has no body left to be resurrected from.

    /**
     * Resolve overlaps by TRIMMING, never by relocating. A room is cut back along whichever single
     * edge costs it least, and flagged so the user checks it.
     *
     * ⭐⭐ **The SMALLEST room goes first**, where it used to be the most confident. What a cut costs
     * is proportional: a cell off a twenty-cell living room is 5 % of it, the same cell off a
     * two-cell toilet is half of it, and one more cut is the whole room. Measured across the real
     * corpus plus both of the owner's sheets, with the grid framed on the home: confidence-first
     * still loses **4** rooms outright and smallest-first loses **none**.
     *
     * ⚠ **It is not free, and the cost is stated rather than buried.** Because the biggest room is
     * then the one squeezed, and the biggest room is usually the living room, the big rooms absorb
     * the trims. [bestFreeSubRect] keeps that cost as low as it can be kept — the search is exact,
     * and its tie-break refuses to give up a room's printed orientation for zero cells — but a
     * blocker sitting mid-rectangle still costs real cells. The trade is taken deliberately: a room
     * drawn the wrong shape is visible, flagged and correctable by the user, while a room that
     * vanishes changes the footprint the engine scores and cannot be re-added by someone who never
     * saw it. It is the standing rule of this file, applied to the one case where the two harms
     * compete.
     *
     * ⭐ [rescued] rooms jump the queue. A room only lands there by having been lost outright on a
     * previous pass, so it is the narrowest possible exception: it fires for a room that would
     * otherwise not exist, and for no other.
     *
     * Confidence still breaks ties, and is used here and nowhere else: a *relative* comparison
     * between two rooms in one reply, never a quality gate on the reply. S2 forbids the latter.
     */
    private fun resolveOverlaps(
        snapped: List<Triple<Candidate, CellRect, CellRect>>,
        rescued: Set<Candidate>,
    ): Attempt {
        val order = snapped.sortedWith(
            compareBy<Triple<Candidate, CellRect, CellRect>> { if (it.first in rescued) 0 else 1 }
                .thenBy { it.second.w * it.second.h }
                .thenByDescending { it.first.box.confidence }
                .thenBy { it.first.box.label },
        )
        val out = ArrayList<Triple<Candidate, CellRect, Boolean>>(snapped.size)
        val lost = ArrayList<Candidate>()
        for ((cand, rect, asRead) in order) {
            val blockers = out.map { it.second }
            // The printed shape first; then the shape it was read with. Keeping a room of the wrong
            // SIZE beats losing it — the user can re-shape a room they can see and cannot re-add one
            // that never arrived — and neither search ever MOVES a room beyond its own rectangle,
            // which is the rule the whole step exists to keep. A one-cell survivor is simply the
            // smallest sub-rectangle, so the old corner fallback is subsumed.
            // A strip has no printed pair, but its shaped rect IS its printed sense (depth from the
            // caption, length as read) — hand that to the trim so a cut prefers keeping it a strip.
            val ratio = RoomDimensions.of(cand.box)?.ratio?.takeIf { it.isFinite() && it > 0.0 }
                ?: rect.takeIf { it.w != it.h && RoomDimensions.stripDepthOf(cand.box) != null }
                    ?.let { it.w.toDouble() / it.h }
            val fitted = bestFreeSubRect(rect, blockers, ratio)
                ?: bestFreeSubRect(asRead, blockers, ratio)
            if (fitted == null) {
                lost += cand
                continue
            }
            out += Triple(cand, fitted, fitted != rect)
        }
        return Attempt(out, lost)
    }

    /**
     * ⭐⭐ The LARGEST free sub-rectangle of [cand] against [blockers] — exact, not greedy.
     *
     * This replaced a chain of single-edge retractions, each locally cheapest, whose sum was often
     * terrible: measured on the corpus, one plan's living/dining went from 5×4 to a 2×3 drawn the
     * WRONG way round, and another's lobby/dining from 4×3 to 1×3 — each retraction threw away a
     * whole slice when the blocker only touched a corner of it. Every sub-rectangle is checked
     * instead (≤ ~3 000 for a 10×10 room, O(1) apiece via prefix sums), keeping the best by: most
     * cells, then agreement with the PRINTED orientation, then least displaced from where the room
     * was read. The tie-break means a room never gives up the way its own sheet says it runs to
     * save zero cells. Post-trim orientation agreement across the recorded corpus went 120/132 →
     * 127/138, and rooms lost outright 2 → 1 (the one loss is two rectangles read at the same
     * cells, which no trim can fix).
     */
    private fun bestFreeSubRect(cand: CellRect, blockers: List<CellRect>, printedRatio: Double?): CellRect? {
        val w = cand.w
        val h = cand.h
        if (w < 1 || h < 1) return null
        // pre[r][c] = blocked cells above-left of (r, c) in the candidate's own frame.
        val pre = Array(h + 1) { IntArray(w + 1) }
        for (r in 0 until h) {
            for (c in 0 until w) {
                val cell = CellRect(cand.col + c, cand.row + r, 1, 1)
                val hit = if (blockers.any { it.overlaps(cell) }) 1 else 0
                pre[r + 1][c + 1] = hit + pre[r][c + 1] + pre[r + 1][c] - pre[r][c]
            }
        }
        fun agree(ww: Int, hh: Int): Int = when {
            printedRatio == null || printedRatio == 1.0 -> 1
            ww == hh -> 1
            (printedRatio > 1.0) == (ww > hh) -> 2
            else -> 0
        }
        var best: CellRect? = null
        var bestScore: IntArray? = null
        for (r0 in 0 until h) for (r1 in r0 + 1..h) for (c0 in 0 until w) for (c1 in c0 + 1..w) {
            if (pre[r1][c1] - pre[r0][c1] - pre[r1][c0] + pre[r0][c0] > 0) continue
            val ww = c1 - c0
            val hh = r1 - r0
            val score = intArrayOf(ww * hh, agree(ww, hh), -(r0 + c0), -r0, -c0)
            val prev = bestScore
            var better = prev == null
            if (prev != null) {
                for (i in score.indices) {
                    if (score[i] == prev[i]) continue
                    better = score[i] > prev[i]
                    break
                }
            }
            if (better) {
                best = CellRect(cand.col + c0, cand.row + r0, ww, hh)
                bestScore = score
            }
        }
        return best
    }

    /**
     * Fraction of the unit square covered by the union of [boxes] — the objective "do these rooms
     * tile the footprint?" signal. See [COVERAGE_SAMPLES] for why it is sampled and not analytic.
     */
    fun coverageOf(boxes: List<ScanBox>, n: Int = COVERAGE_SAMPLES): Double {
        if (boxes.isEmpty()) return 0.0
        var hit = 0
        for (gy in 0 until n) {
            val py = (gy + 0.5) / n
            for (gx in 0 until n) {
                val px = (gx + 0.5) / n
                for (b in boxes) {
                    if (b.x <= px && px < b.x + b.w && b.y <= py && py < b.y + b.h) { hit++; break }
                }
            }
        }
        return hit.toDouble() / (n * n)
    }

    /** stdev(area) ÷ mean(area) over [boxes] — the fabricated-geometry signature. 0.0 when empty. */
    fun areaVariationOf(boxes: List<ScanBox>): Double {
        if (boxes.isEmpty()) return 0.0
        val areas = boxes.map { it.w * it.h }
        val mean = areas.average()
        if (mean <= 0.0) return 0.0
        val variance = areas.sumOf { (it - mean) * (it - mean) } / areas.size
        return sqrt(variance) / mean
    }
}
