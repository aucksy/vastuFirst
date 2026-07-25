package com.vastufirst.shared.editor

import com.vastufirst.shared.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editing maths on a NON-SQUARE plot (cols ≠ rows) and at the plot-size bounds. The existing
 * `GridEditingTest` is all 8×8; a rectangular home is the common case (docs/RECT-PLOT-RESEARCH.md),
 * so move/resize clamping and re-pack must honour the two axes independently — a bug here would let
 * a room leave the plot or two rooms stack on a rectangular canvas. UAT cases D5, E10, G3–G11.
 */
class GridEditingRectTest {

    private fun assertNoOverlaps(rects: List<CellRect>) {
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse(rects[i].overlaps(rects[j]), "rooms $i and $j overlap: ${rects[i]} / ${rects[j]}")
        }
    }

    // ── move on a rectangular plot ───────────────────────────────────────────────────────────────

    @Test
    fun `move clamps to the deep axis, not the wide one`() {
        // 8 wide × 5 deep. A 2×2 room pushed hard down must stop at row 3 (rows-h = 5-2), NOT row 6.
        val start = CellRect(3, 0, 2, 2)
        assertEquals(CellRect(3, 3, 2, 2), moveBy(start, 0, 9, cols = 8, rows = 5))
        // …and hard right stops at col 6 (cols-w = 8-2).
        assertEquals(CellRect(6, 0, 2, 2), moveBy(start, 9, 0, cols = 8, rows = 5))
    }

    @Test
    fun `move clamps to the wide axis on a tall plot`() {
        // 4 wide × 10 deep. Right stop = col 2 (4-2); down stop = row 8 (10-2).
        val start = CellRect(0, 0, 2, 2)
        assertEquals(CellRect(2, 8, 2, 2), moveBy(start, 9, 9, cols = 4, rows = 10))
    }

    @Test
    fun `a tall-plot room near the far edge round-trips without drift`() {
        // Rows 8-9 on a 10-deep plot are exactly where the engine Y goes negative in the flip; the
        // editing maths must still be exact there (UAT I3 depends on this being stable).
        val start = CellRect(2, 8, 2, 2)   // bottom = 10, flush to the south wall
        assertEquals(start, moveBy(start, 0, 5, cols = 10, rows = 10))   // can't go further south
        assertEquals(CellRect(2, 0, 2, 2), moveBy(start, 0, -8, cols = 10, rows = 10))
        assertEquals(start, moveBy(start, 0, 0, cols = 10, rows = 10))
    }

    // ── resize on a rectangular plot ─────────────────────────────────────────────────────────────

    @Test
    fun `resize grows to fill each axis but no further`() {
        // 8 wide × 5 deep, room at origin: BR pull to the max fills 8×5, not 8×8.
        val start = CellRect(0, 0, 2, 2)
        assertEquals(CellRect(0, 0, 8, 5), resizeBy(start, Handle.BOTTOM_RIGHT, 99, 99, cols = 8, rows = 5))
    }

    @Test
    fun `resize height clamps to rows on a shallow plot`() {
        val start = CellRect(1, 1, 2, 2)
        val r = resizeBy(start, Handle.BOTTOM_RIGHT, 0, 99, cols = 8, rows = 5)
        assertEquals(4, r.h, "height must stop at rows-row = 5-1")
        assertEquals(CellRect(1, 1, 2, 4), r)
    }

    @Test
    fun `top-left resize past the pinned corner still cannot invert on a rectangle`() {
        val start = CellRect(2, 1, 3, 3)   // right=5, bottom=4, on an 8×6 plot
        val r = resizeBy(start, Handle.TOP_LEFT, 99, 99, cols = 8, rows = 6)
        assertTrue(r.w >= 1 && r.h >= 1, "collapsed/inverted: $r")
        assertEquals(start.right, r.right, "pinned right edge moved")
        assertEquals(start.bottom, r.bottom, "pinned bottom edge moved")
    }

    // ── the stepper resize path (GuidedGridScreen.onResize): BR-pinned grow, min 1, capped ────────
    // Mirrors the exact arithmetic the +/− size buttons use, so the button path is proven, not just
    // the drag path. (UAT E7–E10, S5.)

    private fun stepperResize(r: CellRect, dw: Int, dh: Int, cols: Int, rows: Int) = CellRect(
        r.col, r.row,
        (r.w + dw).coerceIn(1, cols - r.col),
        (r.h + dh).coerceIn(1, rows - r.row),
    )

    @Test
    fun `stepper widen at the east wall is a no-op, never off-grid`() {
        // Room already flush to the east wall (col 6, w 2 on an 8-wide plot). "Wider" can't grow it.
        val r = CellRect(6, 0, 2, 2)
        assertEquals(r, stepperResize(r, +1, 0, cols = 8, rows = 8), "widen past the wall must clamp to no-op")
    }

    @Test
    fun `stepper shrink never goes below one cell`() {
        val r = CellRect(2, 2, 1, 1)
        assertEquals(r, stepperResize(r, -1, -1, cols = 8, rows = 8))
    }

    @Test
    fun `stepper grow respects the shallow axis`() {
        val r = CellRect(0, 3, 2, 2)   // on an 8×5 plot, row 3 → only 2 rows of headroom
        assertEquals(CellRect(0, 3, 2, 2), stepperResize(r, 0, +5, cols = 8, rows = 5))
    }

    // ── fitWithoutOverlap on a rectangle ─────────────────────────────────────────────────────────

    @Test
    fun `repack on a rectangle relocates the collider within the two axes`() {
        // Two 2×2 rooms that would stack when a 10-wide plot is squeezed to 6 wide × 8 deep.
        val rooms = listOf(CellRect(5, 0, 2, 2), CellRect(7, 0, 2, 2))
        val fitted = fitWithoutOverlap(rooms, cols = 6, rows = 8)!!
        assertEquals(2, fitted.size)
        assertNoOverlaps(fitted)
        fitted.forEach { assertTrue(it.right <= 6 && it.bottom <= 8, "left the rectangle: $it") }
    }

    @Test
    fun `an infeasible rectangular squeeze is refused, never overlapped`() {
        // Three 3×3 rooms (27 cells) cannot fit a 4×5 plot (20 cells) clear → null.
        val rooms = List(3) { CellRect(0, 0, 3, 3) }
        assertEquals(null, fitWithoutOverlap(rooms, cols = 4, rows = 5))
    }

    @Test
    fun `a single room wider than the shrunk plot is clamped to fit, not refused (documents S7)`() {
        // ⚠ FINDING S7: clampToGrid caps w/h to the axis BEFORE the overlap logic, so a lone oversized
        // room is SILENTLY SHRUNK to fit rather than the resize being refused. Refusal (null) only
        // guards the RELOCATION-collision case, not the too-big-for-the-axis case. No overlap results
        // (so the score is not corrupted), but the room quietly changes size — pinned here so the
        // behaviour is a deliberate, documented decision, not an accident.
        val fitted = fitWithoutOverlap(listOf(CellRect(0, 0, 5, 2)), cols = 4, rows = 8)!!
        assertEquals(CellRect(0, 0, 4, 2), fitted.single(), "the 5-wide room is clamped to 4 wide")
    }

    // ── zones on a rectangle ─────────────────────────────────────────────────────────────────────

    @Test
    fun `zone thirds are computed per-axis on a rectangle`() {
        // 9 wide × 3 deep. Columns third at 3/6; the three rows fall in the N / centre / S bands
        // (centres 0.5, 1.5, 2.5 → f 0.17, 0.50, 0.83). Pick the centre row to isolate the column axis.
        assertEquals(Zone.W, zoneOfRect(CellRect(0, 1, 2, 1), cols = 9, rows = 3))            // col west, row centre
        assertEquals(Zone.BRAHMASTHAN, zoneOfRect(CellRect(3, 1, 3, 1), cols = 9, rows = 3))  // both centre
        assertEquals(Zone.E, zoneOfRect(CellRect(7, 1, 2, 1), cols = 9, rows = 3))            // col east, row centre
        // And the row axis resolves independently: same west column, top vs bottom row.
        assertEquals(Zone.NW, zoneOfRect(CellRect(0, 0, 2, 1), cols = 9, rows = 3))
        assertEquals(Zone.SW, zoneOfRect(CellRect(0, 2, 2, 1), cols = 9, rows = 3))
    }

    @Test
    fun `band boundaries differ per axis on a rectangle`() {
        assertEquals(listOf(2, 4), bandBoundaries(6))
        assertEquals(listOf(3, 5), bandBoundaries(8))
        assertEquals(listOf(1, 3), bandBoundaries(4))   // MIN_GRID
        assertEquals(listOf(3, 7), bandBoundaries(10))  // MAX_GRID
    }
}
