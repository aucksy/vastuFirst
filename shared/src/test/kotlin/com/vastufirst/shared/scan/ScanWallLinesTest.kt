package com.vastufirst.shared.scan

import com.vastufirst.shared.editor.Cell
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.Footprint
import com.vastufirst.shared.editor.overlaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * ⭐⭐ A SCANNED HOME MUST ARRIVE AS A HOME, NOT AS A DOZEN ISLANDS.
 *
 * ⚠ The defect this file exists for, photographed by the owner on his own flat (Tower D1/D2, 2B+2T,
 * 1262 sq ft). Every room on that sheet shares a wall with its neighbour — the master bedroom's
 * right wall IS the bedroom's left wall, the master toilet sits directly under the master bedroom,
 * the living room runs straight off the bedroom below it. What the app drew was ten small boxes with
 * empty squares between every one of them, and then it asked him whether his home was "cut off" in
 * the north-west — pointing at the loose space beside the very room that occupies that corner.
 *
 * **The cause was rounding each edge on its own.** The reader draws inside the wall thickness and
 * jitters every edge, so a shared wall comes back as 3.48 from one room and 3.52 from the other,
 * which round to 3 and 4 — a one-cell moat between two rooms that touch. Every room does it to every
 * neighbour, so the whole plan falls apart rather than merely loosening.
 *
 * The boxes below are that sheet read the way a real reader reads it: undersized, and not quite
 * flush. They are deliberately NOT tidy — a tidy fixture cannot reproduce this defect at all, which
 * is exactly why the existing fixtures never caught it.
 */
class ScanWallLinesTest {

    /** The owner's flat, as a reader that draws inside the walls and jitters every edge sees it. */
    private fun towerD1() = ScanDraft(
        planType = PlanImageType.TWO_D_PLAN,
        hasRoomLabels = true,
        planConfidence = 0.95,
        rooms = listOf(
            ScanBox("BALCONY 5'-5\" WIDE", 0.183, 0.128, 0.141, 0.089, 0.9),
            ScanBox("BALCONY 5'-5\" WIDE", 0.428, 0.058, 0.135, 0.081, 0.9),
            ScanBox("MASTER BEDROOM 11'-0\"X12'-3\"", 0.161, 0.238, 0.206, 0.196, 0.9),
            ScanBox("BEDROOM 10'-0\"X12'-0\"", 0.396, 0.161, 0.191, 0.212, 0.9),
            ScanBox("BALCONY 5'-5\" WIDE", 0.645, 0.288, 0.182, 0.096, 0.9),
            ScanBox("MASTER TOILET 8'-6\"X5'-5\"", 0.159, 0.461, 0.143, 0.120, 0.9),
            ScanBox("TOILET 8'-0\"X5'-5\"", 0.351, 0.459, 0.099, 0.143, 0.9),
            ScanBox("LIVING/DINING 19'-0\"X12'-1\"", 0.404, 0.408, 0.419, 0.268, 0.9),
            ScanBox("KITCHEN 11'-0\"X7'-0\"", 0.545, 0.626, 0.216, 0.124, 0.9),
            ScanBox("SERVICE BALCONY 4'-11\" WIDE", 0.414, 0.643, 0.106, 0.079, 0.9),
        ),
    )

    private fun placed(): ScanOutcome.Placed {
        val out = ScanMapper.map(towerD1(), imageAspect = 1725.0 / 2000.0)
        assertIs<ScanOutcome.Placed>(out, "a ten-room single-home plan must place")
        return out
    }

    private fun ScanOutcome.Placed.rect(caption: String): CellRect =
        rooms.firstOrNull { it.label.startsWith(caption) }?.rect
            ?: throw AssertionError("$caption did not survive: ${rooms.map { it.label }}")

    /** Connected patches of the bounding box no room covers — the holes the owner could see. */
    private fun holes(p: ScanOutcome.Placed): List<Set<Cell>> =
        Footprint.gaps(p.rooms.mapNotNull { it.rect }).map { it.cells }

    @Test
    fun `⭐ the rooms that share a wall on the sheet share a grid line here`() {
        val p = placed()
        val master = p.rect("MASTER BEDROOM")
        val bedroom = p.rect("BEDROOM")
        assertEquals(
            master.right, bedroom.col,
            "the master bedroom's right wall IS the bedroom's left wall — they must touch, not sit " +
                "a cell apart (master $master, bedroom $bedroom)",
        )
        val toilet = p.rect("MASTER TOILET")
        assertEquals(
            master.bottom, toilet.row,
            "the master toilet is directly under the master bedroom (master $master, toilet $toilet)",
        )
    }

    @Test
    fun `⭐⭐ the home does not arrive as a scatter of islands`() {
        val p = placed()
        val patches = holes(p)
        val empty = patches.sumOf { it.size }
        val boxCells = Footprint.boundingCells(p.rooms.mapNotNull { it.rect }).size
        // Measured on this plan: rounding each edge alone left 41 of 80 squares empty — more than
        // half the home — in one hole 28 squares across, big enough to swallow three rooms. Shared
        // wall lines take it to 26 in 4 holes, and what remains is the genuine shape of this flat:
        // it really does have white space at the top-right and below the toilets.
        assertTrue(
            empty * 5 <= boxCells * 2,
            "no more than two fifths of the home may be empty squares; got $empty of $boxCells " +
                "in ${patches.size} holes (sizes ${patches.map { it.size }.sortedDescending()})",
        )
        assertTrue(
            patches.none { it.size > 12 },
            "no single hole may be room-sized — that is a placement failure wearing a shape's " +
                "clothes; got ${patches.map { it.size }.sortedDescending()}",
        )
    }

    @Test
    fun `⭐⭐ the loose space between rooms is never offered up to be cut away`() {
        // ⚠ The owner's exact complaint. He was asked whether his home was "cut off" in the
        // north-west, pointing at a five-square patch beside his master bedroom. It was slack from
        // loose placement, not a missing corner, and NEITHER answer to that question was right.
        //
        // A missing corner contains a corner. Anything else is floor with no room drawn on it, and
        // the app now states that instead of asking. This pins both halves: interior slack exists on
        // this plan, and it is not askable.
        val p = placed()
        val gaps = Footprint.gaps(p.rooms.mapNotNull { it.rect })
        val interior = gaps.filter { !it.atCorner }
        assertTrue(interior.isNotEmpty(), "this plan really does leave slack between its rooms")
        assertTrue(
            interior.none { it.looksLikeMissingCorner },
            "slack between rooms must never be offered up as a missing corner: " +
                "${interior.filter { it.looksLikeMissingCorner }.map { it.cells }}",
        )
        for (gap in gaps.filter { it.looksLikeMissingCorner }) {
            assertTrue(
                gap.atCorner && gap.cells.size >= 2,
                "every question the app asks must be about a real corner; got ${gap.cells}",
            )
        }
    }

    @Test
    fun `every room still lands inside the grid and none overlaps`() {
        val p = placed()
        for (r in p.rooms) {
            val rect = r.rect!!
            assertTrue(rect.w >= 1 && rect.h >= 1, "${r.label} collapsed to $rect")
            assertTrue(
                rect.col >= 0 && rect.row >= 0 && rect.right <= p.cols && rect.bottom <= p.rows,
                "${r.label} at $rect is outside the ${p.cols}x${p.rows} grid",
            )
        }
        val rects = p.rooms.mapNotNull { it.rect }
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertTrue(
                !rects[i].overlaps(rects[j]),
                "two rooms overlap: ${rects[i]} and ${rects[j]}",
            )
        }
    }

    @Test
    fun `⭐ the biggest room on the sheet is still the biggest room on the grid`() {
        // The living/dining is 19'0" x 12'1" — nearly a third of the flat. If shared wall lines ever
        // crushed it, the layout would tile beautifully and mean nothing.
        val p = placed()
        val living = p.rect("LIVING/DINING")
        val others = p.rooms.filterNot { it.label.startsWith("LIVING") }.mapNotNull { it.rect }
        assertTrue(
            others.all { living.w * living.h > it.w * it.h },
            "the living/dining must stay the biggest room; got $living against ${others.maxByOrNull { it.w * it.h }}",
        )
    }

}
