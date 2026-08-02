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
     * ⭐⭐ The Placed/Assisted gate: **how many rooms the plan has**. Above this, the geometry is not
     * trusted and the rooms are handed over unplaced.
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

    /** A rectangle at least this covered by a bigger one is a sub-area of it, not a room. */
    const val CONTAINMENT_FRACTION = 0.90

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

    private class Candidate(val box: ScanBox, val type: RoomType, val flags: MutableSet<RoomFlag>)

    /**
     * Turn one model reply into an outcome.
     *
     * [imageAspect] is width ÷ height of the source image, supplied by the platform layer — the model
     * is never asked, because measuring is the thing it cannot do. It only chooses the shape of the
     * drawing grid; `null` gives a square one.
     */
    fun map(draft: ScanDraft, imageAspect: Double? = null): ScanOutcome {
        val dropped = ArrayList<DroppedSpace>()

        // ---- 1. sanitise geometry ------------------------------------------------------------
        val clean = ArrayList<Pair<ScanBox, MutableSet<RoomFlag>>>(draft.rooms.size)
        for (b in draft.rooms) {
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
        triage(draft, boxes)?.let { return ScanOutcome.Refused(it, notes()) }

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

        // ---- 5. sub-areas, by GEOMETRY not by name --------------------------------------------
        val rooms = dropSubAreas(typed, dropped)

        if (rooms.isEmpty()) {
            // Everything the plan said was unreadable as a room name — a numbered legend whose key
            // could not be resolved, or captions in a language we did not read. That is the same
            // failure the user fixes the same way, so it says "no labels", not "no rooms".
            val reason = if (unknownLabels > 0) RefusalReason.NO_LABELS else RefusalReason.NO_ROOMS
            return ScanOutcome.Refused(reason, notes())
        }

        // Top-to-bottom, left-to-right — the order someone reads a plan, so the palette makes sense.
        val identified = rooms
            .sortedWith(compareBy({ it.box.y }, { it.box.x }))
            .map { ScannedRoom(it.type, it.box.label, rect = null, flags = it.flags.toSet()) }

        // ---- 6. the two objective gates (L2 is a bonus, never a promise) -----------------------
        // ⚠ Coverage is deliberately NOT a gate — see [MAX_TRUSTED_ROOMS]. It is still measured and
        // carried in the notes, because it is worth being able to answer "why did it do that?", but
        // it decides nothing.
        if (rooms.size >= UNIFORM_MIN_ROOMS && variation < UNIFORM_AREA_VARIATION) {
            return ScanOutcome.Assisted(identified, AssistReason.UNIFORM_BOXES, notes())
        }
        if (rooms.size > MAX_TRUSTED_ROOMS) {
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
        val (cols, rows) = gridFor(homeAspect(frame, imageAspect))
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
        // the whole placement is redone. That keeps the ordinary rule — the most confident room keeps
        // its cells — while making "re-shaping must never cost a room" structural instead of hopeful.
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

        val out = chosen.rooms
            .map { (c, rect, _) -> ScannedRoom(c.type, c.box.label, rect, c.flags.toSet()) }
            .sortedWith(compareBy({ it.rect!!.row }, { it.rect!!.col }))
        return ScanOutcome.Placed(cols, rows, out, notes())
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
    private fun reshapeToPrinted(
        snapped: List<Pair<Candidate, CellRect>>,
        cols: Int,
        rows: Int,
    ): List<Pair<Candidate, CellRect>> {
        val printed = snapped.map { RoomDimensions.parse(it.first.box.label) }
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

        return snapped.mapIndexed { i, (candidate, rect) ->
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
            val freeRight = col + w
            val freeBottom = row + h
            val right = magnet(freeRight, xLines, col + 1, cols)
            val bottom = magnet(freeBottom, yLines, row + 1, rows)
            val (r, b) = listOf(
                right to bottom,
                right to freeBottom,
                freeRight to bottom,
                freeRight to freeBottom,
            ).first { (rr, bb) -> orientationAgrees(rr - col, bb - row, ratio) }
            candidate to CellRect(col, row, r - col, b - row)
        }
    }

    /** The nearest wall line to [edge] within [MAGNET_REACH], or [edge] itself when none is close. */
    private fun magnet(edge: Int, lines: Set<Int>, lowest: Int, highest: Int): Int {
        val best = lines.filter { it in lowest..highest }.minByOrNull { kotlin.math.abs(it - edge) }
        return if (best != null && kotlin.math.abs(best - edge) <= MAGNET_REACH) best else edge
    }

    /** Does a [w] × [h] rectangle run the way a room printed at this width-to-depth [ratio] should? */
    private fun orientationAgrees(w: Int, h: Int, ratio: Double): Boolean = when {
        ratio > 1.0 -> w >= h
        ratio < 1.0 -> h >= w
        else -> true
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
     * Drop a rectangle that sits wholly inside a bigger one — a dressing area, a walk-in wardrobe, a
     * niche. **By geometry, not by name** (owner decision D1): the parent room already occupies that
     * floor, and the engine only ever measures a room's own footprint, so the sub-area is invisible
     * to scoring anyway.
     *
     * ⚠ Known cost, deliberately accepted: the model's rectangles are sloppy, so an *attached* toilet
     * drawn slightly inside its bedroom would be dropped here — and TOILET is a scored input. That is
     * why every drop is recorded in [ScanNotes.dropped] and shown to the user ("we also saw…"),
     * instead of vanishing. The user confirms every room before anything is scored (§6.2b).
     */
    private fun dropSubAreas(typed: List<Candidate>, dropped: MutableList<DroppedSpace>): List<Candidate> {
        val keep = ArrayList<Candidate>(typed.size)
        for (c in typed) {
            val inside = typed.any { it !== c && containedIn(c.box, it.box) }
            if (inside) dropped += DroppedSpace(c.box.label, DropReason.SUB_AREA) else keep += c
        }
        return keep
    }

    /** True when [inner] is smaller than [outer] and at least [CONTAINMENT_FRACTION] of it is inside. */
    internal fun containedIn(inner: ScanBox, outer: ScanBox): Boolean {
        val innerArea = inner.w * inner.h
        val outerArea = outer.w * outer.h
        if (innerArea <= 0.0 || innerArea >= outerArea) return false
        val ix = max(0.0, min(inner.x + inner.w, outer.x + outer.w) - max(inner.x, outer.x))
        val iy = max(0.0, min(inner.y + inner.h, outer.y + outer.h) - max(inner.y, outer.y))
        return (ix * iy) / innerArea >= CONTAINMENT_FRACTION
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
     * Normalised rectangle → whole cells, measured against [frame] rather than against the picture.
     *
     * The fit is UNIFORM — one scale for both axes, centred in whichever axis is slack — so a room's
     * proportions survive the mapping. Stretching the frame to fill the grid in both directions would
     * distort every room by the same amount the home's shape differs from the grid's, which is
     * exactly the orientation error the printed sizes exist to remove.
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
        val s = min(cols / pw, rows / ph)
        val ox = (cols - pw * s) / 2.0
        val oy = (rows - ph * s) / 2.0
        fun cx(v: Double) = ((v - frame.x) * a) * s + ox
        fun cy(v: Double) = (v - frame.y) * s + oy

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
     */
    private fun wallLines(edges: List<Double>, max: Int): (Double) -> Int {
        val sorted = edges.distinct().sorted()
        val assigned = HashMap<Double, Int>(sorted.size)
        var i = 0
        while (i < sorted.size) {
            var j = i + 1
            while (j < sorted.size &&
                sorted[j] - sorted[j - 1] <= WALL_TOLERANCE &&
                sorted[j] - sorted[i] <= WALL_MAX_SPAN
            ) j++
            val line = ((i until j).sumOf { sorted[it] } / (j - i)).roundToInt().coerceIn(0, max)
            for (k in i until j) assigned[sorted[k]] = line
            i = j
        }
        return { e -> assigned[e] ?: e.roundToInt().coerceIn(0, max) }
    }

    internal fun snap(b: ScanBox, frame: ScanBox, imageAspect: Double?, cols: Int, rows: Int): CellRect {
        val a = if (imageAspect != null && imageAspect.isFinite() && imageAspect > 0.0) imageAspect else 1.0
        val pw = frame.w * a
        val ph = frame.h
        val s = min(cols / pw, rows / ph)
        val ox = (cols - pw * s) / 2.0
        val oy = (rows - ph * s) / 2.0
        val left = (((b.x - frame.x) * a) * s + ox).roundToInt().coerceIn(0, cols - 1)
        val right = ((((b.x + b.w) - frame.x) * a) * s + ox).roundToInt().coerceIn(left + 1, cols)
        val top = ((b.y - frame.y) * s + oy).roundToInt().coerceIn(0, rows - 1)
        val bottom = (((b.y + b.h) - frame.y) * s + oy).roundToInt().coerceIn(top + 1, rows)
        return CellRect(left, top, right - left, bottom - top)
    }

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
     * then the one squeezed, and the biggest room is usually the living room, one more room across
     * that whole set ends up drawn against the orientation its plan prints — 5 where confidence-first
     * gives 4, out of 57 dimensioned rooms. That trade is taken deliberately: a room drawn the wrong
     * shape is visible, flagged and correctable by the user, while a room that vanishes changes the
     * footprint the engine scores and cannot be re-added by someone who never saw it. It is the
     * standing rule of this file, applied to the one case where the two harms compete.
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
            // The printed shape first; then the shape it was read with; then a single cell at its own
            // corner. Keeping a room of the wrong SIZE beats losing it — the user can re-shape a room
            // they can see and cannot re-add one that never arrived — and none of these three ever
            // MOVES it, which is the rule the whole step exists to keep.
            val fitted = trimAgainst(rect, blockers)
                ?: trimAgainst(asRead, blockers)
                ?: CellRect(rect.col, rect.row, 1, 1).takeIf { one -> blockers.none { it.overlaps(one) } }
            if (fitted == null) {
                lost += cand
                continue
            }
            out += Triple(cand, fitted, fitted != rect)
        }
        return Attempt(out, lost)
    }

    /** Shrink [cand] until it clears every blocker, or give up. Terminates: every step loses area. */
    private fun trimAgainst(cand: CellRect, blockers: List<CellRect>): CellRect? {
        var r = cand
        var guard = cand.w * cand.h + 4
        while (guard-- > 0) {
            val hit = blockers.firstOrNull { it.overlaps(r) } ?: return r
            r = retract(r, hit) ?: return null
        }
        return null
    }

    /** The single-edge retraction that clears [o] while losing the fewest cells. */
    private fun retract(r: CellRect, o: CellRect): CellRect? {
        val options = ArrayList<CellRect>(4)
        if (o.right > r.col && o.right < r.right) options += CellRect(o.right, r.row, r.right - o.right, r.h)
        if (o.col > r.col && o.col < r.right) options += CellRect(r.col, r.row, o.col - r.col, r.h)
        if (o.bottom > r.row && o.bottom < r.bottom) options += CellRect(r.col, o.bottom, r.w, r.bottom - o.bottom)
        if (o.row > r.row && o.row < r.bottom) options += CellRect(r.col, r.row, r.w, o.row - r.row)
        return options.maxByOrNull { it.w * it.h }
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
