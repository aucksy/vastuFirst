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

        // ---- 7. snap to the grid ---------------------------------------------------------------
        val (cols, rows) = gridFor(imageAspect)
        val snapped = ArrayList<Pair<Candidate, CellRect>>(rooms.size)
        for (r in rooms) {
            val rect = snap(r.box, cols, rows)
            if (rect == null) dropped += DroppedSpace(r.box.label, DropReason.DEGENERATE)
            else snapped += r to rect
        }

        // ---- 8. overlaps: TRIM the less confident room, never relocate it -----------------------
        val placed = resolveOverlaps(snapped, dropped)

        if (placed.size < MIN_PLACED_ROOMS || placed.size < rooms.size * MIN_PLACED_FRACTION) {
            // The grid could not hold what we read. Hand the rooms over unplaced rather than show a
            // half-eaten layout — the room list is still the bulk of the saving.
            return ScanOutcome.Assisted(identified, AssistReason.TOO_FEW_PLACED, notes())
        }

        val out = placed
            .map { (c, rect) -> ScannedRoom(c.type, c.box.label, rect, c.flags.toSet()) }
            .sortedWith(compareBy({ it.rect!!.row }, { it.rect!!.col }))
        return ScanOutcome.Placed(cols, rows, out, notes())
    }

    // ---- pieces, each independently testable --------------------------------------------------

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
     * The drawing grid's shape, from the source image's proportions. The longer side gets the most
     * cells we allow, because a scanned home carries 9–25 rooms and resolution is what stops small
     * ones (a toilet, a pooja niche) rounding away — see [snap] and plan doc §7.
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
     * Normalised rectangle → whole cells. Each EDGE is rounded independently rather than rounding
     * the position and the size: rounding the size makes two rooms that were flush on the plan drift
     * apart or overlap, and adjacency is what the footprint is made of. A rectangle whose edges round
     * together has no cells left and is dropped (and reported).
     */
    internal fun snap(b: ScanBox, cols: Int, rows: Int): CellRect? {
        val left = (b.x * cols).roundToInt().coerceIn(0, cols)
        val right = ((b.x + b.w) * cols).roundToInt().coerceIn(0, cols)
        val top = (b.y * rows).roundToInt().coerceIn(0, rows)
        val bottom = ((b.y + b.h) * rows).roundToInt().coerceIn(0, rows)
        if (right <= left || bottom <= top) return null
        return CellRect(left, top, right - left, bottom - top)
    }

    /**
     * Resolve overlaps by TRIMMING, in descending confidence order. The most confident rectangle
     * keeps its cells; a later one is cut back along whichever single edge costs it least, and
     * flagged so the user checks it. If no cut leaves it a cell, it is dropped and reported.
     *
     * Confidence is used here and nowhere else: this is a *relative* comparison between two rooms in
     * one reply, not a quality gate on the reply. S2 forbids the latter, not the former.
     */
    private fun resolveOverlaps(
        snapped: List<Pair<Candidate, CellRect>>,
        dropped: MutableList<DroppedSpace>,
    ): List<Pair<Candidate, CellRect>> {
        val order = snapped.sortedWith(
            compareByDescending<Pair<Candidate, CellRect>> { it.first.box.confidence }
                .thenByDescending { it.second.w * it.second.h }
                .thenBy { it.first.box.label },
        )
        val out = ArrayList<Pair<Candidate, CellRect>>(snapped.size)
        for ((cand, rect) in order) {
            val trimmed = trimAgainst(rect, out.map { it.second })
            if (trimmed == null) {
                dropped += DroppedSpace(cand.box.label, DropReason.OVERLAP_UNRESOLVABLE)
                continue
            }
            if (trimmed != rect) cand.flags += RoomFlag.OVERLAP_TRIMMED
            out += cand to trimmed
        }
        return out
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
