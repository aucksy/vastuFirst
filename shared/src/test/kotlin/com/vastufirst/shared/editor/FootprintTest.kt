package com.vastufirst.shared.editor

import com.vastufirst.shared.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The home's true outline (docs/SCORE-ACCURACY-CAVEATS.md #1).
 *
 * The engine could always score an L-shape; it was never given one. These cases prove the two
 * things that has to be true for that to change safely:
 *
 *  1. **A home with no gaps produces EXACTLY the polygon the app has always produced** — same four
 *     corners, same order. That is what keeps the §15 worked example on 31 without opening a single
 *     engine test.
 *  2. **A shape the app cannot describe honestly is refused**, not guessed at. A hole, a home in two
 *     pieces and a diagonal pinch each come back null, so they can be stopped at the moment the user
 *     makes one rather than becoming a plausible polygon the engine scores.
 */
class FootprintTest {

    private fun rect(col: Int, row: Int, w: Int, h: Int) = CellRect(col, row, w, h)

    private fun cellsOf(c0: Int, r0: Int, c1: Int, r1: Int): Set<Cell> =
        (c0 until c1).flatMap { c -> (r0 until r1).map { r -> Cell(c, r) } }.toSet()

    /** Area of a rectilinear ring by the shoelace rule — must equal the number of cells it encloses. */
    private fun ringArea(ring: List<GridPoint>): Int {
        var twice = 0
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            twice += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(twice) / 2
    }

    // ── the no-gap case must not move ───────────────────────────────────────────────────────────

    @Test
    fun `a solidly filled home traces to exactly four corners`() {
        val rooms = listOf(rect(1, 2, 2, 3), rect(3, 2, 2, 3))
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, emptySet())))
        assertEquals(
            listOf(GridPoint(1, 2), GridPoint(5, 2), GridPoint(5, 5), GridPoint(1, 5)), ring,
            "a rectangle must come back as its four corners, in a fixed order — anything else " +
                "would change the polygon the engine has always been given",
        )
        assertEquals(12, ringArea(ring), "the ring must enclose the twelve cells the two rooms cover")
    }

    @Test
    fun `a home with an undecided gap still traces as the full rectangle`() {
        // Nothing cut out yet ⇒ the shape is unchanged, so an unanswered question can never quietly
        // make a home smaller than the user drew it.
        val rooms = listOf(rect(0, 0, 4, 2), rect(0, 2, 2, 2))
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, emptySet())))
        assertEquals(4, ring.size, "an undecided gap must not change the outline")
        assertEquals(16, ringArea(ring), "the outline is still the whole 4x4 bounding box")
    }

    // ── the L-shape this whole file exists for ─────────────────────────────────────────────────

    @Test
    fun `cutting the north-east corner produces an L`() {
        val rooms = listOf(rect(0, 0, 5, 8), rect(5, 3, 3, 5))
        val cut = cellsOf(5, 0, 8, 3)
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, cut)))
        assertEquals(6, ring.size, "an L has six corners")
        assertEquals(64 - 9, ringArea(ring), "the nine cut cells must be gone from the enclosed area")
    }

    @Test
    fun `a notch in one wall is traced, not smoothed away`() {
        val rooms = listOf(rect(0, 0, 4, 4))
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, setOf(Cell(1, 0)))))
        assertEquals(8, ring.size, "a single-cell notch adds four corners")
        assertEquals(15, ringArea(ring))
    }

    // ── shapes that must be refused rather than guessed ────────────────────────────────────────

    @Test
    fun `a hole through the middle is refused`() {
        val cells = cellsOf(0, 0, 4, 4) - setOf(Cell(1, 1))
        assertNull(Footprint.trace(cells), "a courtyard is not a single ring and must not be invented")
    }

    @Test
    fun `a home in two pieces is refused`() {
        val cells = cellsOf(0, 0, 2, 4) + cellsOf(3, 0, 5, 4)
        assertNull(Footprint.trace(cells), "two separate blocks have no single outline")
    }

    @Test
    fun `two blocks meeting only at a corner are refused`() {
        val cells = cellsOf(0, 0, 2, 2) + cellsOf(2, 2, 4, 4)
        assertNull(Footprint.trace(cells), "a diagonal pinch has no unambiguous outline")
    }

    @Test
    fun `nothing placed traces to nothing`() {
        assertNull(Footprint.trace(emptySet()))
        assertTrue(Footprint.boundingCells(emptyList()).isEmpty())
    }

    // ── gaps: the questions the app asks the user ──────────────────────────────────────────────

    @Test
    fun `a filled home has no questions to ask`() {
        assertTrue(Footprint.gaps(listOf(rect(0, 0, 3, 3))).isEmpty())
    }

    @Test
    fun `a missing corner is one removable gap, named by its direction`() {
        // Rooms cover everything except the north-east 3x3 of an 8x8 home.
        val rooms = listOf(rect(0, 0, 5, 8), rect(5, 3, 3, 5))
        val gaps = Footprint.gaps(rooms)
        assertEquals(1, gaps.size, "one empty patch means one question")
        val gap = gaps.single()
        assertEquals(9, gap.cells.size)
        assertEquals(Zone.NE, gap.zone, "the gap is in the north-east and must be named so")
        assertTrue(gap.removable, "cutting a corner leaves one solid home")
        assertFalse(gap.enclosed, "a corner touches the outer edge")
    }

    @Test
    fun `a gap in the middle is reported as enclosed and not removable`() {
        val rooms = listOf(
            rect(0, 0, 3, 1), rect(0, 1, 1, 1), rect(2, 1, 1, 1), rect(0, 2, 3, 1),
        )
        val gap = Footprint.gaps(rooms).single()
        assertEquals(setOf(Cell(1, 1)), gap.cells)
        assertTrue(gap.enclosed, "it never touches the outer edge — it is a courtyard, not a cut")
        assertFalse(gap.removable, "cutting it would punch a hole the outline cannot express")
    }

    @Test
    fun `a gap the front door sits on cannot be cut away`() {
        val rooms = listOf(rect(0, 0, 5, 8), rect(5, 3, 3, 5))
        val gaps = Footprint.gaps(rooms, protectedCells = setOf(Cell(6, 0)))
        assertFalse(
            gaps.single().removable,
            "the door is scored on the outline, so cutting the wall it stands on must be refused",
        )
    }

    @Test
    fun `two separate gaps are two separate questions`() {
        val rooms = listOf(rect(1, 0, 2, 4), rect(0, 1, 4, 2))
        val gaps = Footprint.gaps(rooms)
        assertEquals(4, gaps.size, "the four corners of a plus-shape are four independent questions")
        assertTrue(gaps.all { it.removable }, "each corner can go on its own")
        assertEquals(
            setOf(Zone.NW, Zone.NE, Zone.SW, Zone.SE), gaps.map { it.zone }.toSet(),
            "each corner is named by its own direction",
        )
    }

    @Test
    fun `diagonally touching empty cells are separate gaps`() {
        val rooms = listOf(rect(1, 0, 1, 1), rect(0, 1, 1, 1))
        val gaps = Footprint.gaps(rooms)
        assertEquals(2, gaps.size, "cells meeting only at a corner are two questions, not one")
    }

    // ── cuts are re-derived from the saved outline on reopen ───────────────────────────────────

    @Test
    fun `a saved L reopens with the same cells cut out`() {
        val rooms = listOf(rect(0, 0, 5, 8), rect(5, 3, 3, 5))
        val cut = cellsOf(5, 0, 8, 3)
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, cut)))
        assertEquals(cut, Footprint.cutOutFromOutline(rooms, ring), "reopening must restore the shape")
    }

    @Test
    fun `a saved rectangle reopens with nothing cut out`() {
        val rooms = listOf(rect(2, 1, 3, 3))
        val ring = assertNotNull(Footprint.trace(Footprint.homeCells(rooms, emptySet())))
        assertTrue(Footprint.cutOutFromOutline(rooms, ring).isEmpty())
    }

    // ── a cut can never eat a room ─────────────────────────────────────────────────────────────

    @Test
    fun `a stale cut under a room is ignored`() {
        // The user cut a corner, then enlarged a room over it. The cut must lapse, not punch a hole.
        val rooms = listOf(rect(0, 0, 4, 4))
        val cells = Footprint.homeCells(rooms, setOf(Cell(3, 0)))
        assertEquals(16, cells.size, "a room's own cells always belong to the home")
        assertEquals(4, assertNotNull(Footprint.trace(cells)).size)
    }
}
