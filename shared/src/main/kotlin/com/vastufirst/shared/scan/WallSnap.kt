// WallSnap.kt — each edge of a room's rectangle is moved onto the WALL the photograph draws there.
//
// ⭐⭐ WHY THIS EXISTS (owner, 8 Sep 2026: "check bedroom detection, it's incorrect… I want this to
// be automatically accurate"). The reader transcribes text at ~95 % and guesses rectangles at
// 40–70 %: on his furnished render its bedroom box ran a fifth of the room's height past the
// bottom wall, over the balcony. Everything downstream — the tint on "Check what we read", the
// grid, the score — was built from that guess, while the one thing on the sheet that says exactly
// where a room ends, its wall, was carried along only to be shown.
//
// So the picture is read. For every edge of every box, the strong lines near it are found, the ones
// that are WALLS are kept, and the edge moves onto the nearest wall's inner face. No caption is
// consulted here — this is a statement about the PICTURE made from the picture, which is the exact
// opposite of the 15 Aug ruling on ScannedRoom.source (the caption must never shape the on-photo box,
// because sheets are not always drawn to their captions). A wall is where the sheet draws it.
//
// WHAT MAKES A LINE A WALL, on every style of Indian sheet this was measured on (a clean CAD-style
// drawing, its JPEG copy, a skewed phone photo of it, and the owner's furnished builder's render):
//   · it is CONTINUOUS along at least 60 % of the room's side (a door opening is allowed);
//   · it runs on PAST the room — over a band a quarter longer at each end it is still 60 % ink,
//     which furniture inside the room never is;
//   · it is GREY — furniture in a render is wood-brown, plant-green, fabric-blue; walls are not;
//   · it has a DARK core somewhere across it (a rug is grey and has none);
//   · it is at least 45 % as THICK as the home's outer wall and no thicker than 6 % of the home
//     (a floor-tile hairline is a tenth of a wall; a rug is wider than any wall), and that
//     thickness holds along its whole length (a tile line that happens to line up with a bed's
//     edge is thin for a third of its run).
// The thickness calibrates itself on each sheet — the outer wall is found first — so the same
// rule reads a 6-pixel line drawing and a 30-pixel render.
//
// WHEN IN DOUBT, NOTHING MOVES. An edge with no qualifying wall within reach keeps the reader's
// value; a snap that would halve a box or grow it past one and a half times is thrown away for that
// axis. Measured on the fixture with exact ground truth (`WallSnapTest`): mean overlap with the true
// rooms 79 % → 97 % on the clean drawing, 73 % → 97 % on its compressed copy, and on the skewed photo
// every move was toward a wall. The mirror in `tools/scan-eval/wall-snap.mjs` runs the identical
// arithmetic on the whole recorded corpus once the sheet images are beside it.
package com.vastufirst.shared.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object WallSnap {

    // ---- the rule, as numbers. Every one was measured; none is a guess to be tuned by hand. ------

    /** How far from the reader's edge a wall is looked for: this share of the room's own side… */
    const val REACH_ROOM = 0.30
    /** …or this share of the whole home's side, whichever is larger (small rooms are read worst). */
    const val REACH_FRAME = 0.06
    const val MIN_REACH = 6
    /** The band a line is judged along is the room's side with this much cut off each end. */
    const val INSET = 0.12
    /** A line must be ink along this share of the band. Below it, it is not a line at all. */
    const val MIN_SCORE = 0.6
    /** A wall is at least this share of the outer wall's thickness… */
    const val THICK_SHARE = 0.45
    const val MIN_THICK = 2
    /** …and at most this share of the home's shorter side. */
    const val MAX_THICK_SHARE = 0.06
    /** Share of a wall's ink that must be grey (saturation under [SAT_MAX]). */
    const val GREY_SHARE = 0.7
    const val SAT_MAX = 40
    /** Share of positions along the line where a genuinely dark pixel sits inside its run. */
    const val DARK_CORE = 0.5
    /** Ink is this much darker than the local floor… */
    const val INK_DROP = 35
    /** …and the floor is never taken more than this far below the sheet's white. */
    const val FLOOR_DROP = 30
    /** How much a candidate loses, per full reach of distance, against a nearer one. */
    const val PROXIMITY = 0.35
    const val SHRINK_MIN = 0.5
    const val GROW_MAX = 1.5
    /** Where the outer wall is looked for: this share of the home's side either way from its edge. */
    const val FRAME_REACH = 0.08
    /** A wall keeps going: the band is extended by this share at each end and must still score. */
    const val EXTEND = 0.25
    const val EXT_MIN = 0.6
    const val WHITE_PERCENTILE = 0.95

    /** Pixel edges of a box; [x1] and [y1] are exclusive. */
    data class Edges(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w: Int get() = x1 - x0
        val h: Int get() = y1 - y0
    }

    /** One strong line near an edge, with everything the wall test looks at. */
    class Candidate(
        val c: Int,
        val score: Double,
        val ext: Double,
        val thick: Int,
        val lo: Int,
        val hi: Int,
        val dark: Double,
        val grey: Double,
    )

    /**
     * The reply with every room's rectangle moved onto the walls the picture shows.
     *
     * Rooms are composed onto the page exactly as the on-photo tint composes them
     * ([ScanMapper.pageSource]), snapped in pixels, and put back in the reader's own frame — so the
     * grid, the tint and the door all see one rectangle. Returns **this same draft** when there is
     * no picture, no rooms, or nothing to move, so callers can compare instances.
     */
    fun refine(draft: ScanDraft, picture: PlanImage?, trace: ((String) -> Unit)? = null): ScanDraft {
        val img = picture ?: return draft
        if (draft.rooms.isEmpty()) return draft
        val building = ScanMapper.saneBuilding(draft.building)
        val page = draft.rooms.map { ScanMapper.pageSource(draft.building, it) }
        val frameBox = building ?: unionOf(page) ?: return draft
        val w = img.width
        val h = img.height
        fun edgesOf(b: ScanBox) = Edges(
            (b.x * w).roundToInt().coerceIn(0, w),
            (b.y * h).roundToInt().coerceIn(0, h),
            ((b.x + b.w) * w).roundToInt().coerceIn(0, w),
            ((b.y + b.h) * h).roundToInt().coerceIn(0, h),
        )
        val before = page.map(::edgesOf)
        val after = snapAll(img, before, edgesOf(frameBox), trace)
        var changed = false
        val rooms = draft.rooms.mapIndexed { i, room ->
            val e = after[i]
            if (e == before[i]) return@mapIndexed room
            changed = true
            var x = e.x0.toDouble() / w
            var y = e.y0.toDouble() / h
            var bw = e.w.toDouble() / w
            var bh = e.h.toDouble() / h
            if (building != null) {
                x = (x - building.x) / building.w
                y = (y - building.y) / building.h
                bw /= building.w
                bh /= building.h
            }
            room.copy(x = x, y = y, w = bw, h = bh)
        }
        return if (changed) draft.copy(rooms = rooms) else draft
    }

    private fun unionOf(boxes: List<ScanBox>): ScanBox? {
        val good = boxes.filter { it.w > 0.0 && it.h > 0.0 }
        if (good.isEmpty()) return null
        val x0 = good.minOf { it.x }.coerceIn(0.0, 1.0)
        val y0 = good.minOf { it.y }.coerceIn(0.0, 1.0)
        val x1 = good.maxOf { it.x + it.w }.coerceIn(0.0, 1.0)
        val y1 = good.maxOf { it.y + it.h }.coerceIn(0.0, 1.0)
        if (x1 <= x0 || y1 <= y0) return null
        return ScanBox(x = x0, y = y0, w = x1 - x0, h = y1 - y0)
    }

    // ---- the picture, in pixels ----------------------------------------------------------------

    /** The sheet's white: the 95th percentile of brightness. Paper on a photo is not 255. */
    internal fun whiteLevel(img: PlanImage): Int {
        val hist = IntArray(256)
        for (b in img.luma) hist[b.toInt() and 0xFF]++
        val target = kotlin.math.ceil(WHITE_PERCENTILE * img.luma.size).toInt()
        var acc = 0
        for (v in 0..255) { acc += hist[v]; if (acc >= target) return v }
        return 255
    }

    private fun darkLevel(white: Int): Int = (0.55 * white).toInt().coerceIn(70, 140)

    private class Sheet(val img: PlanImage, val white: Int, val dark: Int) {
        val w = img.width
        val h = img.height
        /** Index of (x, y), or -1 off the picture. */
        fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) -1 else y * w + x
        fun luma(i: Int): Int = img.luma[i].toInt() and 0xFF
        fun sat(i: Int): Int = img.saturation[i].toInt() and 0xFF
        /** Ink: darker than the floor by [INK_DROP] and grey. A coloured pixel is furniture. */
        fun ink(i: Int, bg: Int): Boolean = i >= 0 && luma(i) < bg - INK_DROP && sat(i) < SAT_MAX
        /** (c, a) → pixel index, where c is the coordinate ACROSS the line and a the position ALONG it. */
        fun px(vertical: Boolean, c: Int, a: Int): Int = if (vertical) at(c, a) else at(a, c)
    }

    /** The floor level for one edge: the 90th percentile of brightness over its whole search region. */
    private fun regionBg(s: Sheet, vertical: Boolean, from: Int, to: Int, a0: Int, a1: Int, cap: Int): Int {
        val hist = IntArray(256)
        var n = 0
        val c0 = max(0, from - cap)
        val c1 = min((if (vertical) s.w else s.h) - 1, to + cap)
        for (c in c0..c1) for (a in a0 until a1) {
            val i = s.px(vertical, c, a)
            if (i >= 0) { hist[s.luma(i)]++; n++ }
        }
        if (n == 0) return s.white
        val target = kotlin.math.ceil(0.9 * n).toInt()
        var acc = 0
        var p90 = 255
        for (v in 0..255) { acc += hist[v]; if (acc >= target) { p90 = v; break } }
        return min(s.white, max(s.white - FLOOR_DROP, p90))
    }

    /** Share of the band [a0, a1) that has ink within one pixel of the line at [c]. */
    private fun continuity(s: Sheet, vertical: Boolean, c: Int, a0: Int, a1: Int, bg: Int): Double {
        var d = 0
        var n = 0
        for (a in a0 until a1) {
            n++
            for (j in -1..1) if (s.ink(s.px(vertical, c + j, a), bg)) { d++; break }
        }
        return if (n == 0) 0.0 else d.toDouble() / n
    }

    private class Run(val lo: Int, val hi: Int, val dark: Boolean, val grey: Double)

    /** The unbroken ink run through (c, a) across the line, up to [cap] each way; null if (c, a) is not ink. */
    private fun runAcross(s: Sheet, vertical: Boolean, c: Int, a: Int, cap: Int, bg: Int): Run? {
        if (!s.ink(s.px(vertical, c, a), bg)) return null
        var lo = c
        var hi = c
        while (c - lo < cap && s.ink(s.px(vertical, lo - 1, a), bg)) lo--
        while (hi - c < cap && s.ink(s.px(vertical, hi + 1, a), bg)) hi++
        var dark = false
        var grey = 0
        var n = 0
        for (cc in lo..hi) {
            val i = s.px(vertical, cc, a)
            n++
            if (s.luma(i) < s.dark) dark = true
            if (s.sat(i) < SAT_MAX) grey++
        }
        return Run(lo, hi, dark, grey.toDouble() / n)
    }

    private fun percentile(values: MutableList<Int>, q: Double): Int {
        if (values.isEmpty()) return 0
        values.sort()
        return values[(q * (values.size - 1)).toInt()]
    }

    private fun candidate(s: Sheet, vertical: Boolean, c: Int, score: Double, a0: Int, a1: Int, cap: Int, bg: Int): Candidate {
        val len = a1 - a0
        val ext = continuity(s, vertical, c, (a0 - EXTEND * len).roundToInt(), (a1 + EXTEND * len).roundToInt(), bg)
        val thick = ArrayList<Int>()
        val los = ArrayList<Int>()
        val his = ArrayList<Int>()
        var darkN = 0
        var greySum = 0.0
        var n = 0
        var a = a0
        while (a < a1) {
            val r = runAcross(s, vertical, c, a, cap, bg)
            if (r != null) {
                n++
                thick += r.hi - r.lo + 1
                los += r.lo
                his += r.hi
                if (r.dark) darkN++
                greySum += r.grey
            }
            a += 2
        }
        return Candidate(
            c = c, score = score, ext = ext,
            // ⚠ The 10th percentile, not the median: a wall is thick along its WHOLE length, and a
            // tile hairline that lines up with a bed's edge is thin for a third of its run.
            thick = percentile(thick, 0.1), lo = percentile(los, 0.5), hi = percentile(his, 0.5),
            dark = if (n == 0) 0.0 else darkN.toDouble() / n,
            grey = if (n == 0) 0.0 else greySum / n,
        )
    }

    /** Every strong line within [reach] of [edge], between [lo] and [hi] inclusive. */
    private fun candidates(
        s: Sheet, vertical: Boolean, edge: Int, a0: Int, a1: Int, reach: Double, lo: Int, hi: Int, cap: Int,
    ): List<Candidate> {
        val from = max(lo, (edge - reach).roundToInt())
        val to = min(hi, (edge + reach).roundToInt())
        if (to < from) return emptyList()
        val bg = regionBg(s, vertical, from, to, a0, a1, cap)
        val scores = DoubleArray(to - from + 1) { continuity(s, vertical, from + it, a0, a1, bg) }
        val out = ArrayList<Candidate>()
        var start = -1
        for (i in 0..scores.size) {
            val strong = i < scores.size && scores[i] >= MIN_SCORE
            if (strong && start < 0) start = i
            if (!strong && start >= 0) {
                val c = (from + (start + i - 1) / 2.0).roundToInt()
                var best = 0.0
                for (k in start until i) best = max(best, scores[k])
                out += candidate(s, vertical, c, best, a0, a1, cap, bg)
                start = -1
            }
        }
        return out
    }

    private fun isWall(q: Candidate, tMin: Double, tMax: Int): Boolean =
        q.thick >= tMin && q.thick <= tMax && q.grey >= GREY_SHARE && q.dark >= DARK_CORE && q.ext >= EXT_MIN

    /** The outer wall's thickness: the thickest grey, dark-cored line near each frame edge, median of the four. */
    private fun outerWallThickness(s: Sheet, f: Edges, tMax: Int): Int? {
        val found = ArrayList<Int>()
        val bx0 = (f.y0 + 0.1 * f.h).roundToInt()
        val bx1 = (f.y1 - 0.1 * f.h).roundToInt()
        val by0 = (f.x0 + 0.1 * f.w).roundToInt()
        val by1 = (f.x1 - 0.1 * f.w).roundToInt()
        val rw = FRAME_REACH * f.w
        val rh = FRAME_REACH * f.h
        val probes = listOf(
            Probe(true, f.x0, bx0, bx1, rw), Probe(true, f.x1, bx0, bx1, rw),
            Probe(false, f.y0, by0, by1, rh), Probe(false, f.y1, by0, by1, rh),
        )
        for (p in probes) {
            val limit = (if (p.vertical) s.w else s.h) - 1
            val cs = candidates(s, p.vertical, p.edge, p.a0, p.a1, p.reach, 0, limit, cap = tMax)
                .filter { it.grey >= GREY_SHARE && it.dark >= DARK_CORE && it.thick <= tMax && it.score >= 0.5 }
            if (cs.isNotEmpty()) found += cs.maxOf { it.thick }
        }
        return if (found.isEmpty()) null else percentile(found, 0.5)
    }

    private class Probe(val vertical: Boolean, val edge: Int, val a0: Int, val a1: Int, val reach: Double)

    private class Side(val vertical: Boolean, val edge: Int, val reach: Double, val innerIsHigh: Boolean, val c: List<Candidate>)

    /**
     * Snap every box's four edges. Pure pixel arithmetic; boxes keep their order. Boxes narrower
     * than two pixels either way are handed back untouched.
     */
    fun snapAll(img: PlanImage, boxes: List<Edges>, frame: Edges, trace: ((String) -> Unit)? = null): List<Edges> {
        val white = whiteLevel(img)
        val s = Sheet(img, white, darkLevel(white))
        val tMax = max(6, (MAX_THICK_SHARE * min(frame.w, frame.h)).roundToInt())
        val sides: List<List<Side>?> = boxes.map { b ->
            if (b.w < 2 || b.h < 2) return@map null
            val cx = (b.x0 + b.x1) / 2.0
            val cy = (b.y0 + b.y1) / 2.0
            val rx = maxOf(MIN_REACH.toDouble(), REACH_ROOM * b.w, REACH_FRAME * frame.w)
            val ry = maxOf(MIN_REACH.toDouble(), REACH_ROOM * b.h, REACH_FRAME * frame.h)
            val bx0 = (b.y0 + INSET * b.h).roundToInt()
            val bx1 = (b.y1 - INSET * b.h).roundToInt()
            val by0 = (b.x0 + INSET * b.w).roundToInt()
            val by1 = (b.x1 - INSET * b.w).roundToInt()
            listOf(
                Side(true, b.x0, rx, innerIsHigh = true, candidates(s, true, b.x0, bx0, bx1, rx, 0, kotlin.math.floor(cx).toInt() - 1, tMax)),
                Side(true, b.x1, rx, innerIsHigh = false, candidates(s, true, b.x1, bx0, bx1, rx, kotlin.math.ceil(cx).toInt() + 1, s.w - 1, tMax)),
                Side(false, b.y0, ry, innerIsHigh = true, candidates(s, false, b.y0, by0, by1, ry, 0, kotlin.math.floor(cy).toInt() - 1, tMax)),
                Side(false, b.y1, ry, innerIsHigh = false, candidates(s, false, b.y1, by0, by1, ry, kotlin.math.ceil(cy).toInt() + 1, s.h - 1, tMax)),
            )
        }
        // The reference thickness: the outer wall, or the thick end of the grey lines found beside the
        // rooms themselves — whichever is thicker. A cropped picture may show no outer wall, or a
        // stray thin line where one should be; the rooms' own walls then say what a wall is here.
        val outer = outerWallThickness(s, frame, tMax)
        val roomLines = ArrayList<Int>()
        for (box in sides) if (box != null) for (side in box) for (q in side.c) {
            if (q.grey >= GREY_SHARE && q.dark >= DARK_CORE && q.thick >= 2 && q.thick <= tMax) roomLines += q.thick
        }
        val rooms = if (roomLines.size >= 4) percentile(roomLines, 0.9) else null
        val tRef = max(outer ?: 0, rooms ?: 0)
        if (tRef == 0) {
            trace?.invoke("no wall reference: nothing snapped")
            return boxes
        }
        val tMin = max(MIN_THICK.toDouble(), THICK_SHARE * tRef)
        trace?.invoke("white ${s.white} dark<${s.dark} wall ref ${tRef}px (outer $outer rooms $rooms) walls $tMin..${tMax}px")
        return boxes.mapIndexed { i, b ->
            val box = sides[i] ?: return@mapIndexed b
            val picked = IntArray(4)
            for ((k, side) in box.withIndex()) {
                var best: Candidate? = null
                var bestValue = Double.NEGATIVE_INFINITY
                for (q in side.c) {
                    if (!isWall(q, tMin, tMax)) continue
                    val v = q.score - PROXIMITY * abs(q.c - side.edge) / side.reach
                    if (v > bestValue) { bestValue = v; best = q }
                }
                // The wall's INNER face: the room ends where the wall begins.
                picked[k] = when {
                    best == null -> side.edge
                    side.innerIsHigh -> best.hi + 1
                    else -> best.lo
                }
                trace?.invoke(
                    "  box $i ${listOf("left", "right", "top", "bottom")[k]} ${side.edge} -> ${picked[k]}  " +
                        side.c.joinToString(" ") { q ->
                            "[${q.c} s%.2f t${q.thick} g%.2f d%.2f e%.2f${if (isWall(q, tMin, tMax)) "*" else ""}]"
                                .format(q.score, q.grey, q.dark, q.ext)
                        },
                )
            }
            var x0 = picked[0]
            var x1 = picked[1]
            var y0 = picked[2]
            var y1 = picked[3]
            if (x1 - x0 < SHRINK_MIN * b.w || x1 - x0 > GROW_MAX * b.w) { x0 = b.x0; x1 = b.x1 }
            if (y1 - y0 < SHRINK_MIN * b.h || y1 - y0 > GROW_MAX * b.h) { y0 = b.y0; y1 = b.y1 }
            Edges(x0, y0, x1, y1)
        }
    }
}
