package com.vastufirst.app.ui.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.scan.ScannedRoom
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ⭐ A ROOM MAY NOT LEAVE THE FLOW WITHOUT BEING MENTIONED.
 *
 * An unplaced reading is laid out as a holding block of two-by-two tiles, and the grid it is laid
 * out on holds twenty-five of them. Room twenty-six was simply returned as nothing: the screen
 * before said "we found 26 rooms", the next screen drew 25, and no sentence anywhere connected the
 * two. The room was gone and only a careful count would have shown it.
 *
 * It was reachable, which is the part that made it worth fixing. A plan over twenty rooms is not
 * refused — it is downgraded to an assisted reading, which keeps every room it named and is exactly
 * the reading that goes through this layout.
 *
 * These tests pin the count and the list of names against the layout itself, so the two cannot drift
 * apart: if a later change fits more tiles on the grid, the sentence on the screen follows it.
 */
class RoomsOffTheGridTest {

    private fun rooms(n: Int, placed: Boolean = false): List<ScannedRoom> =
        (1..n).map { i ->
            ScannedRoom(
                type = RoomType.BEDROOM,
                label = "Room $i",
                rect = if (placed) CellRect(0, 0, 1, 1) else null,
            )
        }

    @Test fun `the standard grid holds twenty-five rooms`() {
        assertEquals(25, unplacedStripCapacity(10, 10))
    }

    @Test fun `what the grid holds is what the layout actually draws`() {
        // ⚠ The point of this one. The count the screen quotes and the tiles the next screen draws
        // come from two different pieces of code; this is what stops them disagreeing.
        for (cols in 4..10) for (rows in 4..10) {
            val drawn = toGridRooms(rooms(200), cols, rows).size
            assertEquals(
                expected = drawn,
                actual = unplacedStripCapacity(cols, rows),
                message = "grid ${cols}x$rows: the layout drew $drawn tiles",
            )
        }
    }

    @Test fun `an ordinary reading loses nothing`() {
        assertTrue(roomsOffTheGrid(rooms(20), 10, 10).isEmpty())
    }

    @Test fun `a reading exactly filling the grid loses nothing`() {
        assertTrue(roomsOffTheGrid(rooms(25), 10, 10).isEmpty())
    }

    @Test fun `the twenty-sixth room is reported, not swallowed`() {
        val off = roomsOffTheGrid(rooms(26), 10, 10)
        assertEquals(1, off.size)
        assertEquals("Room 26", off.single().label)
    }

    @Test fun `every room past the grid is reported, in plan order`() {
        val off = roomsOffTheGrid(rooms(30), 10, 10)
        assertEquals(5, off.size)
        assertEquals(listOf("Room 26", "Room 27", "Room 28", "Room 29", "Room 30"), off.map { it.label })
    }

    @Test fun `the reported rooms are exactly the ones the layout left out`() {
        val all = rooms(30)
        val drawn = toGridRooms(all, 10, 10).size
        assertEquals(all.size - drawn, roomsOffTheGrid(all, 10, 10).size)
    }

    @Test fun `a reading that came with its own places loses nothing at any size`() {
        // Those rooms carry the plan's own rectangles and never go through the holding layout, so
        // there is nothing for this to report — whatever the room count.
        assertTrue(roomsOffTheGrid(rooms(40, placed = true), 10, 10).isEmpty())
    }

    @Test fun `an empty reading reports nothing rather than failing`() {
        assertTrue(roomsOffTheGrid(emptyList(), 10, 10).isEmpty())
    }

    @Test fun `a small grid holds fewer and says so`() {
        // The smallest plot the editor allows. Two-by-two tiles give two across and two down.
        assertEquals(4, unplacedStripCapacity(4, 4))
        assertEquals(2, roomsOffTheGrid(rooms(6), 4, 4).size)
    }
}
