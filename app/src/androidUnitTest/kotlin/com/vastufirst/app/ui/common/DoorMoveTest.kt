package com.vastufirst.app.ui.common

import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.doorForTap
import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind "the E follows the finger round the house" (owner, 27 Sep 2026: *"I am also
 * not able to move it to other walls.. its stuck on same wall where you put it"*).
 *
 * ⚠ The assertion that matters most is the last one: the wall the E is DRAWN on (nearestOnOutline)
 * and the wall the door is SCORED on (doorForTap) must agree at every point a finger can reach. If
 * they ever disagreed, the picture would lie about the heaviest input in the whole score.
 */
class DoorMoveTest {

    // A home 4 cells wide and 3 deep, top-left at (0, 0).
    private val left = 0f
    private val top = 0f
    private val right = 4f
    private val bottom = 3f

    private fun at(x: Float, y: Float) = nearestOnOutline(x, y, left, top, right, bottom)

    @Test
    fun `a finger just inside the top wall puts the E on the top wall, under the finger`() {
        val p = at(2.3f, 0.4f)
        assertEquals(DoorSide.N, p.side)
        assertEquals("slides along the wall with the finger", 2.3f, p.x, 0.001f)
        assertEquals("sits ON the wall line", top, p.y, 0.0001f)
    }

    @Test
    fun `a finger nearer the right wall moves the E onto the right wall`() {
        val p = at(3.7f, 1.6f)
        assertEquals(DoorSide.E, p.side)
        assertEquals(right, p.x, 0.0001f)
        assertEquals(1.6f, p.y, 0.001f)
    }

    @Test
    fun `a finger above the house keeps the E on the top wall, clamped to its length`() {
        val p = at(-2f, -0.2f)
        // Two cells beyond the west wall and only a fifth beyond the north: west is the wall it is
        // furthest beyond, so west wins — the same rule doorForTap uses for a tap out in the margin.
        assertEquals(DoorSide.W, p.side)
        val q = at(1.5f, -3f)
        assertEquals(DoorSide.N, q.side)
        assertEquals(1.5f, q.x, 0.001f)
    }

    @Test
    fun `a finger beyond the right wall at mid height puts the E on the right wall`() {
        val p = at(6f, 1.5f)
        assertEquals(DoorSide.E, p.side)
        assertEquals(1.5f, p.y, 0.001f)
    }

    @Test
    fun `dragging along the top and round the corner reaches the right wall`() {
        // The owner's report, as a path: start on the top wall, run along it, round the top-right
        // corner and down the east side. The E must visit north, then east — and nothing else.
        val path = (0..40).map { i ->
            val t = i / 40f
            if (t < 0.5f) (0.5f + t * 2f * 4f) to 0.2f          // along the top, past the corner
            else 4.3f to (0.2f + (t - 0.5f) * 2f * 2.5f)       // down beyond the east wall
        }
        val sides = path.map { (x, y) -> at(x, y).side }.distinct()
        assertEquals("north, then east, in that order", listOf(DoorSide.N, DoorSide.E), sides)
    }

    @Test
    fun `the E is always ON the outline and never past a corner`() {
        var y = -2f
        while (y <= 5f) {
            var x = -2f
            while (x <= 6f) {
                val p = at(x, y)
                assertTrue("x within the wall's length at ($x,$y)", p.x in left..right)
                assertTrue("y within the wall's length at ($x,$y)", p.y in top..bottom)
                val onALine = p.x == left || p.x == right || p.y == top || p.y == bottom
                assertTrue("on a wall line at ($x,$y)", onALine)
                x += 0.37f
            }
            y += 0.41f
        }
    }

    @Test
    fun `the wall the E is drawn on is the wall the door is scored on, everywhere`() {
        // Checked for an ordinary home AND a thin one-row home, whose north and south walls are one
        // cell apart — the case doorForTap's own notes single out.
        val homes = listOf(
            listOf(GridRoom("a", RoomType.LIVING, 0, 0, 2, 3), GridRoom("b", RoomType.BEDROOM, 2, 0, 2, 3)),
            listOf(GridRoom("c", RoomType.CORRIDOR, 1, 2, 5, 1)),
        )
        homes.forEach { rooms ->
            val l = rooms.minOf { it.col }.toFloat(); val r = rooms.maxOf { it.col + it.w }.toFloat()
            val t = rooms.minOf { it.row }.toFloat(); val b = rooms.maxOf { it.row + it.h }.toFloat()
            var y = t - 2f
            while (y <= b + 2f) {
                var x = l - 2f
                while (x <= r + 2f) {
                    val p = nearestOnOutline(x, y, l, t, r, b)
                    val scored = doorForTap(p.x, p.y, rooms)
                    assertEquals("drawn and scored wall must agree at ($x,$y)", p.side, scored?.side)
                    x += 0.23f
                }
                y += 0.29f
            }
        }
    }

    @Test
    fun `an exact corner goes to the top wall, as doorForTap would say`() {
        assertEquals(DoorSide.N, at(0f, 0f).side)
    }

    @Test
    fun `the editor carries the E along the middle of the wall's own cells`() {
        val north = editorDoorCentre(at(2.3f, 0.1f), 0, 0, 4, 3)
        assertEquals(2.3f, north.first, 0.001f)
        assertEquals("the middle of the top row of cells", 0.5f, north.second, 0.001f)

        val east = editorDoorCentre(at(3.9f, 1.7f), 0, 0, 4, 3)
        assertEquals("the middle of the last column of cells", 3.5f, east.first, 0.001f)
        assertEquals(1.7f, east.second, 0.001f)

        val corner = editorDoorCentre(at(0f, 0f), 0, 0, 4, 3)
        assertEquals("never outside the corner cell", 0.5f, corner.first, 0.001f)
    }
}
