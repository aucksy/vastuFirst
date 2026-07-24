package com.vastufirst.shared.editor

import com.vastufirst.shared.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editor's arithmetic, proven on every push.
 *
 * These are the acceptance criteria of docs/EDITOR-REWORK-PLAN.md §6 that do not need a screen:
 * no drift after clamping, no inverted rectangle, a 1×1 room stays usable, overlaps are refused,
 * and the chip names a zone. The gesture plumbing on top of this is reviewed by eye; the maths
 * that decides where a room lands is not left to review.
 */
class GridEditingTest {

    private val grid = 8

    // ── overlap ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `rooms sharing only a wall do not overlap`() {
        val a = CellRect(0, 0, 2, 2)
        val b = CellRect(2, 0, 2, 2)   // starts exactly where a ends
        assertFalse(a.overlaps(b))
        assertFalse(b.overlaps(a))
    }

    @Test
    fun `rooms sharing area overlap, in both directions`() {
        val a = CellRect(0, 0, 3, 3)
        val b = CellRect(2, 2, 3, 3)   // one shared cell
        assertTrue(a.overlaps(b))
        assertTrue(b.overlaps(a))
        assertTrue(a.overlaps(a))
    }

    @Test
    fun `a fully enclosed room overlaps its container`() {
        assertTrue(CellRect(1, 1, 1, 1).overlaps(CellRect(0, 0, 4, 4)))
    }

    @Test
    fun `anyOverlap ignores an empty neighbourhood`() {
        assertFalse(anyOverlap(CellRect(0, 0, 2, 2), emptyList()))
        assertTrue(anyOverlap(CellRect(0, 0, 2, 2), listOf(CellRect(4, 4, 2, 2), CellRect(1, 1, 1, 1))))
    }

    // ── fitWithoutOverlap (the plot-resize guard) ────────────────────────────────────────────────

    /** Every pair in a layout must be clear of every other — the invariant the score depends on. */
    private fun assertNoOverlaps(rects: List<CellRect>) {
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse(rects[i].overlaps(rects[j]), "rooms $i and $j overlap: ${rects[i]} / ${rects[j]}")
        }
    }

    @Test
    fun `a non-overlapping layout is returned unchanged`() {
        val rooms = listOf(CellRect(0, 0, 2, 2), CellRect(3, 0, 2, 2), CellRect(0, 3, 2, 2))
        assertEquals(rooms, fitWithoutOverlap(rooms, 8, 8))
    }

    @Test
    fun `shrinking the plot that would stack two rooms relocates one instead`() {
        // The audit's exact repro: two 2-wide rooms at cols 6-7 and 8-9 in a 10-wide grid. Shrink to
        // 8 wide and a naive independent clamp puts BOTH at col 6 (fully overlapping → double-count).
        val rooms = listOf(CellRect(6, 0, 2, 2), CellRect(8, 0, 2, 2))
        val fitted = fitWithoutOverlap(rooms, 8, 8)
        assertEquals(2, fitted.size)
        assertNoOverlaps(fitted)
    }

    @Test
    fun `every room survives a resize (never dropped) and none overlap`() {
        val rooms = listOf(
            CellRect(0, 0, 3, 3), CellRect(4, 0, 3, 3), CellRect(0, 4, 3, 3),
            CellRect(4, 4, 3, 3), CellRect(7, 7, 2, 2),
        )
        val fitted = fitWithoutOverlap(rooms, 6, 6)   // a hard squeeze from 9-wide down to 6
        assertEquals(rooms.size, fitted.size)
        assertNoOverlaps(fitted)
        fitted.forEach { assertTrue(it.col >= 0 && it.row >= 0 && it.right <= 6 && it.bottom <= 6, "out of grid: $it") }
    }

    @Test
    fun `an earlier room keeps its clamped spot; a later collider moves`() {
        val a = CellRect(0, 0, 2, 2)
        val b = CellRect(0, 0, 2, 2)   // identical → must be relocated
        val fitted = fitWithoutOverlap(listOf(a, b), 8, 8)
        assertEquals(CellRect(0, 0, 2, 2), fitted[0])
        assertFalse(fitted[0].overlaps(fitted[1]))
    }

    // ── handles ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a one-cell room gets a single grip`() {
        assertEquals(listOf(Handle.BOTTOM_RIGHT), handlesFor(CellRect(3, 3, 1, 1)))
    }

    @Test
    fun `anything larger than one cell gets four corners`() {
        assertEquals(4, handlesFor(CellRect(0, 0, 2, 1)).size)
        assertEquals(4, handlesFor(CellRect(0, 0, 1, 2)).size)
        assertEquals(4, handlesFor(CellRect(0, 0, 3, 3)).size)
    }

    @Test
    fun `handle anchors sit on the room's four corners`() {
        val r = CellRect(2, 3, 2, 4)
        assertEquals(2 to 3, handleAnchor(r, Handle.TOP_LEFT))
        assertEquals(4 to 3, handleAnchor(r, Handle.TOP_RIGHT))
        assertEquals(2 to 7, handleAnchor(r, Handle.BOTTOM_LEFT))
        assertEquals(4 to 7, handleAnchor(r, Handle.BOTTOM_RIGHT))
    }

    // ── move ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a move keeps the room inside the grid`() {
        val start = CellRect(6, 6, 2, 2)
        assertEquals(CellRect(6, 6, 2, 2), moveBy(start, 5, 5, grid))
        assertEquals(CellRect(0, 0, 2, 2), moveBy(start, -9, -9, grid))
    }

    /**
     * THE DRIFT TEST. A whole drag, replayed as raw pointer deltas — the shape is pushed hard into
     * the corner and dragged back. Because every step is derived from the FROZEN start rect, the
     * room must be exactly where the finger is at every moment, including the moment it returns to
     * where it began. Accumulating the snapped value instead would leave it stuck against the wall.
     */
    @Test
    fun `a room dragged into a wall and back does not drift`() {
        val start = CellRect(3, 3, 2, 2)
        val path = listOf(1, 2, 3, 4, 5, 9, 12, 9, 5, 2, 0, -2, -5, -9, -12, -5, 0)
        for (d in path) {
            val expected = CellRect(
                col = (3 + d).coerceIn(0, 6),
                row = (3 + d).coerceIn(0, 6),
                w = 2, h = 2,
            )
            assertEquals(expected, moveBy(start, d, d, grid), "delta $d")
        }
        assertEquals(start, moveBy(start, 0, 0, grid))
    }

    @Test
    fun `a one-cell room can still be moved`() {
        assertEquals(CellRect(5, 2, 1, 1), moveBy(CellRect(0, 0, 1, 1), 5, 2, grid))
    }

    // ── resize ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `dragging a top-left handle past the bottom-right does not invert the room`() {
        val start = CellRect(2, 2, 3, 3)          // right = 5, bottom = 5
        val r = resizeBy(start, Handle.TOP_LEFT, 99, 99, grid)
        assertTrue(r.w >= 1 && r.h >= 1, "size collapsed or inverted: $r")
        assertEquals(CellRect(4, 4, 1, 1), r)
        assertEquals(start.right, r.right, "the pinned right edge moved")
        assertEquals(start.bottom, r.bottom, "the pinned bottom edge moved")
    }

    @Test
    fun `each corner pins the opposite corner`() {
        val start = CellRect(2, 2, 3, 3)

        val tl = resizeBy(start, Handle.TOP_LEFT, -1, -1, grid)
        assertEquals(CellRect(1, 1, 4, 4), tl)

        val tr = resizeBy(start, Handle.TOP_RIGHT, 2, -1, grid)
        assertEquals(CellRect(2, 1, 5, 4), tr)

        val bl = resizeBy(start, Handle.BOTTOM_LEFT, -2, 1, grid)
        assertEquals(CellRect(0, 2, 5, 4), bl)

        val br = resizeBy(start, Handle.BOTTOM_RIGHT, 1, 1, grid)
        assertEquals(CellRect(2, 2, 4, 4), br)
    }

    @Test
    fun `a resize cannot leave the grid`() {
        val start = CellRect(5, 5, 2, 2)
        assertEquals(CellRect(5, 5, 3, 3), resizeBy(start, Handle.BOTTOM_RIGHT, 9, 9, grid))
        assertEquals(CellRect(0, 0, 7, 7), resizeBy(start, Handle.TOP_LEFT, -9, -9, grid))
    }

    @Test
    fun `a resize never shrinks below one cell`() {
        val start = CellRect(1, 1, 4, 4)
        val r = resizeBy(start, Handle.BOTTOM_RIGHT, -9, -9, grid)
        assertEquals(CellRect(1, 1, 1, 1), r)
    }

    @Test
    fun `a one-cell room can still be resized by its single grip`() {
        val r = resizeBy(CellRect(0, 0, 1, 1), Handle.BOTTOM_RIGHT, 2, 3, grid)
        assertEquals(CellRect(0, 0, 3, 4), r)
    }

    /** Same invariant as the move drift test: resize is a pure function of the frozen start rect. */
    @Test
    fun `a resize clamped at the grid edge returns exactly on the way back`() {
        val start = CellRect(4, 4, 2, 2)
        for (d in listOf(1, 3, 6, 12, 6, 3, 1, 0, -1, 0)) {
            val expected = CellRect(4, 4, (2 + d).coerceIn(1, 4), (2 + d).coerceIn(1, 4))
            assertEquals(expected, resizeBy(start, Handle.BOTTOM_RIGHT, d, d, grid), "delta $d")
        }
    }

    // ── snapping ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hysteresis holds the current cell through the midpoint`() {
        // Sitting exactly on the boundary must NOT flip — that is the shiver.
        assertEquals(0, snapWithHysteresis(0.5f, current = 0))
        assertEquals(0, snapWithHysteresis(0.6f, current = 0))
        assertEquals(1, snapWithHysteresis(0.7f, current = 0))
        assertEquals(1, snapWithHysteresis(1.0f, current = 0))
    }

    @Test
    fun `hysteresis is symmetric going backwards`() {
        assertEquals(0, snapWithHysteresis(-0.5f, current = 0))
        assertEquals(0, snapWithHysteresis(-0.6f, current = 0))
        assertEquals(-1, snapWithHysteresis(-0.7f, current = 0))
    }

    @Test
    fun `staying inside the current cell is always sticky`() {
        assertEquals(3, snapWithHysteresis(3.4f, current = 3))
        assertEquals(3, snapWithHysteresis(2.6f, current = 3))
        assertEquals(4, snapWithHysteresis(3.7f, current = 3))
    }

    @Test
    fun `cellIndex clamps to the grid and survives a zero-sized cell`() {
        assertEquals(0, cellIndex(-40f, 34f, grid))
        assertEquals(1, cellIndex(40f, 34f, grid))
        assertEquals(7, cellIndex(9_999f, 34f, grid))
        assertEquals(0, cellIndex(100f, 0f, grid))   // never divide by a zero-height grid
    }

    // ── zones ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the zone map matches the engine's, row 0 being north`() {
        assertEquals(Zone.NW, zoneOfRect(CellRect(0, 0, 2, 2), grid))
        assertEquals(Zone.N, zoneOfRect(CellRect(3, 0, 2, 2), grid))
        assertEquals(Zone.NE, zoneOfRect(CellRect(6, 0, 2, 2), grid))
        assertEquals(Zone.W, zoneOfRect(CellRect(0, 3, 2, 2), grid))
        assertEquals(Zone.BRAHMASTHAN, zoneOfRect(CellRect(3, 3, 2, 2), grid))
        assertEquals(Zone.E, zoneOfRect(CellRect(6, 3, 2, 2), grid))
        assertEquals(Zone.SW, zoneOfRect(CellRect(0, 6, 2, 2), grid))
        assertEquals(Zone.S, zoneOfRect(CellRect(3, 6, 2, 2), grid))
        assertEquals(Zone.SE, zoneOfRect(CellRect(6, 6, 2, 2), grid))
    }

    @Test
    fun `the stronger band lines fall exactly where the zones change`() {
        assertEquals(listOf(3, 5), bandBoundaries(8))
        // Every drawn band line must be a genuine zone change for a cell either side of it.
        for (i in bandBoundaries(8)) {
            assertTrue(
                zoneOfRect(CellRect(0, i - 1, 1, 1), 8) != zoneOfRect(CellRect(0, i, 1, 1), 8),
                "line at $i is not a zone boundary",
            )
        }
    }

    @Test
    fun `a room's centre decides its zone, not its top-left corner`() {
        // Its top-left corner is dead centre, but its bulk — and its centre — is south-east.
        assertEquals(Zone.SE, zoneOfRect(CellRect(4, 4, 4, 4), grid))
        // A room covering the whole grid is centred on the Brahmasthan.
        assertEquals(Zone.BRAHMASTHAN, zoneOfRect(CellRect(0, 0, 8, 8), grid))
    }
}
