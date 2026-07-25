package com.vastufirst.app.ui.newplan

import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plot-resize decision and the reopen grid-shape derivation (GridResize.kt), pure-tested.
 * These are the behaviours the owner is judging on the plot-size steppers and on reopen — UAT
 * cases G1, G4–G10, I5/S2.
 */
class GridResizeTest {

    private fun room(id: String, col: Int, row: Int, w: Int, h: Int) =
        GridRoom(id, RoomType.BEDROOM, col, row, w, h)

    // ── grow / shrink / bounds ───────────────────────────────────────────────────────────────────

    @Test
    fun `a pure grow moves nothing and is flagged as no score change (G1, G10)`() {
        val rooms = listOf(room("a", 0, 0, 2, 2), room("b", 3, 0, 2, 2))
        val res = resolveGridResize(rooms, null, curCols = 8, curRows = 8, reqCols = 10, reqRows = 10)!!
        assertEquals(10, res.cols)
        assertEquals(10, res.rows)
        assertEquals(rooms, res.rooms)
        assertSame("a grow must not rebuild the room list", rooms, res.rooms)
        assertTrue("a pure grow must not be flagged as a score change", !res.changed)
    }

    @Test
    fun `a same-size request is a harmless no-op`() {
        val rooms = listOf(room("a", 0, 0, 2, 2))
        val res = resolveGridResize(rooms, null, 8, 8, 8, 8)!!
        assertEquals(8, res.cols); assertEquals(8, res.rows)
        assertSame(rooms, res.rooms)
        assertTrue(!res.changed)
    }

    @Test
    fun `the request is clamped to MIN_GRID and MAX_GRID (G5, G6)`() {
        val rooms = listOf(room("a", 0, 0, 2, 2))
        assertEquals(MIN_GRID, resolveGridResize(rooms, null, 8, 8, 1, 1)!!.cols)
        assertEquals(MIN_GRID, resolveGridResize(rooms, null, 8, 8, 1, 1)!!.rows)
        assertEquals(MAX_GRID, resolveGridResize(rooms, null, 8, 8, 99, 99)!!.cols)
        assertEquals(MAX_GRID, resolveGridResize(rooms, null, 8, 8, 99, 99)!!.rows)
    }

    @Test
    fun `a feasible shrink re-packs and flags a change (G3)`() {
        // Two 2×2 rooms at cols 6-7 and 8-9 on a 10-wide plot; shrink to 8 wide → the later one moves.
        val rooms = listOf(room("a", 6, 0, 2, 2), room("b", 8, 0, 2, 2))
        val res = resolveGridResize(rooms, null, curCols = 10, curRows = 10, reqCols = 8, reqRows = 8)!!
        assertEquals(8, res.cols)
        assertTrue("a shrink that relocates a room must be flagged changed", res.changed)
        assertTrue("no room may leave the plot", res.rooms.all { it.col + it.w <= 8 && it.row + it.h <= 8 })
        // No overlap between the packed rooms.
        val a = res.rooms[0]; val b = res.rooms[1]
        assertTrue(
            "packed rooms overlap",
            a.col >= b.col + b.w || b.col >= a.col + a.w || a.row >= b.row + b.h || b.row >= a.row + a.h,
        )
    }

    @Test
    fun `an infeasible shrink is refused, leaving the plot untouched (G4)`() {
        // Five 3×3 rooms cannot fit a 6×6 grid clear → refuse (null). The caller keeps the old size.
        val rooms = List(5) { room("r$it", 0, 0, 3, 3) }
        // (they start non-overlapping on a big grid; the point is the *target* can't hold them)
        val spread = rooms.mapIndexed { i, r -> r.copy(col = (i % 3) * 3, row = (i / 3) * 3) }
        assertNull(resolveGridResize(spread, null, curCols = 10, curRows = 10, reqCols = 6, reqRows = 6))
    }

    // ── door clearing (G8, G9) ───────────────────────────────────────────────────────────────────

    @Test
    fun `a door on a wall the shrink removes is cleared (G8)`() {
        // Door on the East wall at cell 6 (a row index). Shrink rows 10 → 5: cell 6 no longer exists.
        val rooms = listOf(room("a", 0, 0, 2, 2))
        val door = GridDoor(DoorSide.E, 6)
        val res = resolveGridResize(rooms, door, curCols = 10, curRows = 10, reqCols = 10, reqRows = 5)!!
        assertNull("a door past the new depth must be cleared", res.door)
        assertTrue(res.changed)
    }

    @Test
    fun `a door still within range survives the shrink (G9)`() {
        val rooms = listOf(room("a", 0, 0, 2, 2))
        val door = GridDoor(DoorSide.N, 1)   // cell 1 fits any width ≥ 2
        val res = resolveGridResize(rooms, door, curCols = 10, curRows = 10, reqCols = 8, reqRows = 8)!!
        assertEquals(door, res.door)
    }

    // ── reopen grid-shape derivation (I5 / S2) ───────────────────────────────────────────────────

    @Test
    fun `reopen derives the tightest grid enclosing the rooms`() {
        val rooms = listOf(room("a", 0, 0, 2, 2), room("b", 4, 3, 3, 3))   // reaches col 7, row 6
        assertEquals(7 to 6, gridSizeForRooms(rooms))
    }

    @Test
    fun `reopen clamps the derived grid to MIN and MAX`() {
        assertEquals(MIN_GRID to MIN_GRID, gridSizeForRooms(listOf(room("a", 0, 0, 1, 1))))   // ≥ 4
        assertEquals(GRID to GRID, gridSizeForRooms(emptyList()))                              // default
        assertEquals(
            MAX_GRID to MAX_GRID,
            gridSizeForRooms(listOf(room("a", 0, 0, 10, 10))),                                 // ≤ 10
        )
    }

    // ── door re-clamp on a room edit (F4) ────────────────────────────────────────────────────────

    @Test
    fun `a door past the shrunken footprint is pulled back onto it (F4)`() {
        // Door on N at column 6; then the rooms shrink so the footprint only reaches column 4.
        // The door must be pulled to the last footprint column (3), so displayed == scored == reloaded.
        val shrunkRooms = listOf(room("a", 0, 0, 4, 3))   // footprint cols 0..4 → valid N cells 0..3
        val clamped = clampDoorToRooms(GridDoor(DoorSide.N, 6), shrunkRooms)!!
        assertEquals(DoorSide.N, clamped.side)
        assertEquals(3, clamped.cell)
    }

    @Test
    fun `a door already inside the footprint is left exactly where it is (F4)`() {
        val rooms = listOf(room("a", 0, 0, 6, 6))
        val door = GridDoor(DoorSide.E, 2)
        assertEquals(door, clampDoorToRooms(door, rooms))   // unchanged → normal editing never nudges it
    }

    @Test
    fun `removing the last room clears the door (F4)`() {
        assertNull(clampDoorToRooms(GridDoor(DoorSide.S, 3), emptyList()))
    }

    @Test
    fun `reopen does NOT restore plot margin beyond the rooms (documents S2)`() {
        // ⚠ FINDING S2/I5: the user drew a 10-wide plot but the rooms only reach column 6. On reopen
        // the plot comes back ~6 wide, not 10 — the empty margin is lost because plot proportions are
        // not persisted (only the rooms are). Rooms + score are exact; only the surrounding canvas
        // shrinks. Pinned so this is a known, decidable limitation — the fix is to persist cols/rows.
        val rooms = listOf(room("a", 0, 0, 2, 2), room("b", 4, 0, 2, 2))   // widest extent = col 6
        val (cols, _) = gridSizeForRooms(rooms)
        assertEquals("margin beyond the rooms is not restored", 6, cols)
        assertNotNull(rooms)
    }
}
