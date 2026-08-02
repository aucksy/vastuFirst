// Footprint.kt — the TRUE outline of the home, not the bounding box around its rooms.
//
// WHY THIS EXISTS (docs/SCORE-ACCURACY-CAVEATS.md #1 — the worst accuracy hole in the product):
// the engine has always been able to score an L-shape correctly. AnomalyDetector derives a reference
// rectangle from the footprint's modal edges and attributes the difference to cardinal zones, so a
// missing North-East corner fires X-04 exactly as it should. It has simply never been GIVEN one:
// buildEnginePlan handed it the bounding box of the placed rooms, so a genuinely L-shaped home was
// scored as a filled rectangle — silently, too generously, and it is the commonest real Indian home
// shape. Nothing in the engine changes here. Only what it is fed.
//
// The model is deliberately the smallest one that can express a real home: the drawing grid is made
// of whole cells, so the home is a SET OF CELLS and its outline is the boundary of that set. That
// keeps the outline rectilinear by construction (real walls meet at right angles), keeps it exactly
// representable in integers (no floating-point wall that nearly closes), and makes "is this bit part
// of my home?" a question about a cell rather than a trace the user has to draw with a fingertip.
//
// Zero Android, zero Compose — `scripts/check-boundaries.sh` enforces it.
package com.vastufirst.shared.editor

import com.vastufirst.shared.Zone
import kotlinx.serialization.Serializable

/**
 * One cell of the drawing grid. [col] grows EAST; [row] grows SOUTH, so row 0 is the North edge.
 *
 * Serializable because the home's shape is part of an unfinished draft written to disk
 * ([DraftSnapshot]). Serialization is metadata only and changes no behaviour.
 */
@Serializable
data class Cell(val col: Int, val row: Int)

/** An integer CORNER of the drawing grid — where cell edges meet. Cell (c,r) spans x c..c+1, y r..r+1. */
data class GridPoint(val x: Int, val y: Int)

/**
 * A connected patch of grid inside the home's bounding box that no room covers — i.e. a gap the user
 * left. Every one of these is a question the app must ask rather than answer for itself: it is either
 * a part of the home that simply has no room drawn on it (a hall, a landing) or a piece of the world
 * that is not the home at all (the missing corner of an L).
 */
data class Gap(
    val cells: Set<Cell>,
    /** Which direction of the home this gap sits in, for naming it to the user. */
    val zone: Zone,
    /** True when removing this whole gap still leaves ONE solid home with no hole through it. */
    val removable: Boolean,
    /** True when the gap never touches the bounding box edge — a courtyard, not a missing corner. */
    val enclosed: Boolean,
    /**
     * True when the patch actually contains a CORNER of the bounding box — which is the shape a
     * missing corner has, and the only shape the engine's cut/extension checks can read.
     */
    val atCorner: Boolean = false,
) {
    /**
     * ⭐⭐ Is this worth asking "is this part of your home?" about?
     *
     * ⚠ Why this test exists. The app used to offer to cut away ANY removable gap, and after a scan
     * that meant offering to amputate the loose space between rooms the reader had drawn slightly
     * apart. The owner was shown a five-square patch in the north-west of a flat whose north-west
     * corner is its master bedroom, and asked whether his home was cut off there. Answering "yes"
     * would have scored a shape his home does not have; answering "no" left the question looking
     * like the app had misread everything. Neither answer was right, because the question was wrong.
     *
     * A real missing corner is a corner: it reaches the edge of the bounding box, it *contains* one
     * of its four corners, and it is big enough to be a piece of building rather than a rounding
     * artefact. Anything else is unlabelled floor — a passage, a hall, a lobby — and belongs to the
     * home whether or not a room has been drawn on it. We say so instead of asking.
     */
    val looksLikeMissingCorner: Boolean
        get() = removable && !enclosed && atCorner && cells.size >= MIN_CUT_CELLS

    companion object {
        /**
         * A single stray square is never a missing corner. Two is the smallest patch that can be a
         * genuine notch on a grid this coarse, and it is also the smallest that survives the loose
         * placement a scan produces.
         */
        const val MIN_CUT_CELLS = 2
    }
}

/**
 * The home's shape on the drawing grid.
 *
 * Everything here is pure integer arithmetic on cells, so `:shared:test` can prove the whole of it
 * without a screen, an engine or an Android runtime.
 */
object Footprint {

    /** Every cell of the smallest rectangle enclosing [rooms]. Empty when nothing is placed. */
    fun boundingCells(rooms: List<CellRect>): Set<Cell> {
        if (rooms.isEmpty()) return emptySet()
        val minC = rooms.minOf { it.col }
        val maxC = rooms.maxOf { it.right }
        val minR = rooms.minOf { it.row }
        val maxR = rooms.maxOf { it.bottom }
        val out = HashSet<Cell>((maxC - minC) * (maxR - minR))
        for (c in minC until maxC) for (r in minR until maxR) out += Cell(c, r)
        return out
    }

    /** Every cell any room actually covers. */
    fun occupiedCells(rooms: List<CellRect>): Set<Cell> {
        val out = HashSet<Cell>()
        for (rect in rooms) {
            for (c in rect.col until rect.right) for (r in rect.row until rect.bottom) out += Cell(c, r)
        }
        return out
    }

    /**
     * The cells the home is actually made of: the bounding box, minus the cells the user has said are
     * not part of their home. A cell a room covers can never be cut out, so a stale cut (left behind
     * when a room was enlarged over it) is ignored rather than able to punch a hole through a room.
     */
    fun homeCells(rooms: List<CellRect>, cutOut: Set<Cell>): Set<Cell> {
        val occupied = occupiedCells(rooms)
        return boundingCells(rooms) - (cutOut - occupied)
    }

    /**
     * Trace the outline of a set of cells as ONE simple rectilinear ring of grid corners, or **null
     * when the set is not a single solid region** — it has a hole through it, it is in two or more
     * pieces, or two parts of it meet only at a corner (a diagonal pinch, which has no single
     * unambiguous outline). Returning null rather than a best guess is the point: a shape the app
     * cannot describe honestly must be refused at the moment the user makes it, not turned into a
     * plausible-looking polygon the engine then scores.
     *
     * The ring is emitted with the interior on a consistent side and collinear runs collapsed, so a
     * plain rectangle comes back as exactly four corners.
     */
    fun trace(cells: Set<Cell>): List<GridPoint>? {
        if (cells.isEmpty()) return null
        // Each boundary edge is directed so the interior stays on one side; a corner with two
        // outgoing edges is a diagonal pinch and is refused.
        val next = HashMap<GridPoint, GridPoint>(cells.size * 2)
        fun add(a: GridPoint, b: GridPoint): Boolean = next.put(a, b) == null
        for (cell in cells) {
            val (c, r) = cell
            if (Cell(c, r - 1) !in cells && !add(GridPoint(c, r), GridPoint(c + 1, r))) return null
            if (Cell(c + 1, r) !in cells && !add(GridPoint(c + 1, r), GridPoint(c + 1, r + 1))) return null
            if (Cell(c, r + 1) !in cells && !add(GridPoint(c + 1, r + 1), GridPoint(c, r + 1))) return null
            if (Cell(c - 1, r) !in cells && !add(GridPoint(c, r + 1), GridPoint(c, r))) return null
        }
        // Start from the lexicographically smallest corner so the ring is deterministic — the same
        // shape must produce the same polygon on every run, or a golden screenshot would flap.
        val start = next.keys.minWithOrNull(compareBy<GridPoint>({ it.x }, { it.y })) ?: return null
        val ring = ArrayList<GridPoint>(next.size)
        ring += start
        var cur = next.getValue(start)
        while (cur != start) {
            ring += cur
            cur = next[cur] ?: return null
            if (ring.size > next.size) return null
        }
        // Fewer edges walked than exist ⇒ there is a second closed loop, i.e. a hole or a second piece.
        if (ring.size != next.size) return null
        return dropCollinear(ring)
    }

    private fun dropCollinear(ring: List<GridPoint>): List<GridPoint> {
        val n = ring.size
        val out = ArrayList<GridPoint>(n)
        for (i in 0 until n) {
            val p = ring[(i + n - 1) % n]
            val q = ring[i]
            val s = ring[(i + 1) % n]
            val turns = (q.x - p.x) * (s.y - q.y) != (q.y - p.y) * (s.x - q.x)
            if (turns) out += q
        }
        return out
    }

    /**
     * The gaps in the home — connected patches of the bounding box that no room covers. Diagonally
     * touching cells are deliberately treated as SEPARATE gaps (4-connectivity): they are separate
     * questions to a person looking at the grid, and joining them would produce a "gap" that cannot
     * be cut out anyway.
     *
     * [protectedCells] are cells that must stay part of the home whatever the user says — today that
     * is the cell the front door marker sits on, because the door is scored on the outline and cutting
     * its cell away would leave it floating off the wall.
     */
    fun gaps(rooms: List<CellRect>, protectedCells: Set<Cell> = emptySet()): List<Gap> {
        val box = boundingCells(rooms)
        if (box.isEmpty()) return emptyList()
        val empty = box - occupiedCells(rooms)
        if (empty.isEmpty()) return emptyList()

        val minC = box.minOf { it.col }; val maxC = box.maxOf { it.col }
        val minR = box.minOf { it.row }; val maxR = box.maxOf { it.row }

        val seen = HashSet<Cell>()
        val out = ArrayList<Gap>()
        // Scan in a fixed order so the list of questions is stable between recompositions.
        for (r in minR..maxR) for (c in minC..maxC) {
            val cell = Cell(c, r)
            if (cell !in empty || cell in seen) continue
            val patch = HashSet<Cell>()
            // A plain ArrayList used as a stack: `removeLast()` on a List is the one collection call
            // this project avoids, because desugaring it against Java 21's SequencedCollection has
            // bitten Android builds before.
            val stack = ArrayList<Cell>()
            stack += cell
            seen += cell
            while (stack.isNotEmpty()) {
                val cur = stack.removeAt(stack.size - 1)
                patch += cur
                for (n in listOf(
                    Cell(cur.col + 1, cur.row), Cell(cur.col - 1, cur.row),
                    Cell(cur.col, cur.row + 1), Cell(cur.col, cur.row - 1),
                )) {
                    if (n in empty && seen.add(n)) stack += n
                }
            }
            val touchesEdge = patch.any { it.col == minC || it.col == maxC || it.row == minR || it.row == maxR }
            val blocked = patch.any { it in protectedCells }
            val removable = !blocked && trace(box - patch) != null
            // A missing corner has to contain a corner. Everything else is floor with no room drawn
            // on it — see [Gap.looksLikeMissingCorner] for why that distinction is load-bearing.
            val corners = setOf(Cell(minC, minR), Cell(maxC, minR), Cell(minC, maxR), Cell(maxC, maxR))
            out += Gap(
                cells = patch,
                zone = zoneOfCells(patch, minC, maxC + 1, minR, maxR + 1),
                removable = removable,
                enclosed = !touchesEdge,
                atCorner = patch.any { it in corners },
            )
        }
        return out
    }

    /**
     * Which direction of the home a patch of cells sits in, judged against the home's own bounding
     * box in thirds — the same 3×3 map [zoneOfRect] and the engine's pada grid use, so a gap is
     * named with the words the report will use.
     */
    fun zoneOfCells(cells: Set<Cell>, minC: Int, maxC: Int, minR: Int, maxR: Int): Zone {
        if (cells.isEmpty()) return Zone.BRAHMASTHAN
        val cx = cells.sumOf { it.col + 0.5 } / cells.size
        val cy = cells.sumOf { it.row + 0.5 } / cells.size
        fun band(v: Double, lo: Int, hi: Int): Int {
            val span = (hi - lo).toDouble()
            if (span <= 0.0) return 1
            val f = (v - lo) / span
            return when {
                f < 1.0 / 3.0 -> 0
                f < 2.0 / 3.0 -> 1
                else -> 2
            }
        }
        return ZONE_MAP[band(cy, minR, maxR)][band(cx, minC, maxC)]
    }

    /**
     * Which cells of the home's bounding box fall OUTSIDE a stored outline — the inverse of
     * [homeCells], used to rebuild a reopened home's shape from the polygon that was saved with it.
     *
     * The outline is supplied as grid corners; a cell counts as inside when its CENTRE is inside the
     * ring. Centres sit at half-integers and the ring's edges are on integers, so no test point ever
     * lands on an edge and the crossing count is exact.
     */
    fun cutOutFromOutline(rooms: List<CellRect>, outline: List<GridPoint>): Set<Cell> {
        if (outline.size < 4) return emptySet()
        return boundingCells(rooms).filterNot { contains(outline, it.col + 0.5, it.row + 0.5) }.toSet()
    }

    /** Ray-cast point-in-polygon. [x]/[y] must not lie exactly on an edge (call sites use centres). */
    fun contains(ring: List<GridPoint>, x: Double, y: Double): Boolean {
        var inside = false
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            if ((a.y > y) != (b.y > y)) {
                val t = (y - a.y).toDouble() / (b.y - a.y).toDouble()
                if (x < a.x + t * (b.x - a.x)) inside = !inside
            }
        }
        return inside
    }

    /** row 0 = North, col 0 = West. Identical to the engine's PadaGrid.ZONE_MAP. */
    private val ZONE_MAP = arrayOf(
        arrayOf(Zone.NW, Zone.N, Zone.NE),
        arrayOf(Zone.W, Zone.BRAHMASTHAN, Zone.E),
        arrayOf(Zone.SW, Zone.S, Zone.SE),
    )
}
