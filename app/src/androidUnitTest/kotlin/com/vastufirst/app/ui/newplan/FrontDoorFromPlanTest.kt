package com.vastufirst.app.ui.newplan

import com.vastufirst.app.ui.scan.doorForPhotoTap
import com.vastufirst.app.ui.scan.doorMarkerOnPage
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanBox
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The front door, read off the plan and marked on the photograph — the two halves of the owner's
 * 6 August 2026 request: *"we are asking user to mark Entry Door but cant we do it ourselves when
 * Entry is clearly marked? we ask only if its not"*, and *"marking the Door and north should happen
 * only on this actual floor plan"*.
 *
 * ⚠ WHY THESE ARE ORDINARY TESTS AND NOT SCREENSHOTS. Both halves are decided by arithmetic a golden
 * cannot see: a screenshot cannot tap a photo, and the front door is the most heavily weighted single
 * input the engine scores. The render gate covers the half these cannot — that the marker and the
 * home's outline are actually visible on the picture.
 */
class FrontDoorFromPlanTest {

    /** `org.junit.Assert.assertNotNull` returns Unit, so this is what makes it usable as a value. */
    private fun <T : Any> required(message: String, value: T?): T {
        assertNotNull(message, value)
        return value!!
    }

    private fun room(type: RoomType, col: Int, row: Int, w: Int, h: Int, id: String = "$type-$col-$row") =
        GridRoom(id = id, type = type, col = col, row = row, w = w, h = h)

    // ---- reading the door off the plan's own entrance -------------------------------------------

    /**
     * ⭐ The owner's own flat, from the recorded reply for it. His sheet prints FOYER hard against
     * the left-hand wall of the drawing, and we were still making him tap a wall to say so.
     */
    @Test
    fun `the owner's plan names its entrance, so the door is read and never asked for`() {
        val placed = ScanMapper.map(
            RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as ScanOutcome.Placed
        val grid = toGridRooms(placed.rooms, placed.cols, placed.rows)
        assertTrue(
            "the sheet prints FOYER, which must resolve to an entrance",
            grid.any { it.type == RoomType.ENTRANCE },
        )
        val door = required(
            "an entrance flush with one outer wall is a front door we can read",
            frontDoorFromEntrance(grid),
        )
        assertEquals("his foyer runs down the left-hand edge of the drawing", DoorSide.W, door.side)
    }

    @Test
    fun `an entrance flush with one wall gives that wall, at its own middle`() {
        val door = required(
            "flush west, and nowhere near any other wall",
            frontDoorFromEntrance(
                listOf(
                    room(RoomType.ENTRANCE, col = 0, row = 2, w = 1, h = 4),
                    room(RoomType.LIVING, col = 1, row = 0, w = 5, h = 8),
                ),
            ),
        )
        assertEquals(DoorSide.W, door.side)
        assertEquals("the middle of the entrance's own span, not of the wall", 4, door.cell)
    }

    /**
     * ⭐ The four refusals. Each exists because a wrong front door is the most expensive single
     * mistake this app can make, so "we are not sure" has to cost one tap rather than a wrong score.
     */
    @Test
    fun `it refuses whenever the plan has not actually said where the door is`() {
        assertNull(
            "no entrance named at all — the ordinary case, and the reason the ask still exists",
            frontDoorFromEntrance(listOf(room(RoomType.LIVING, 0, 0, 4, 4))),
        )
        assertNull(
            "two entrances is a plan we do not understand well enough to choose between them",
            frontDoorFromEntrance(
                listOf(
                    room(RoomType.ENTRANCE, 0, 0, 1, 2, id = "a"),
                    room(RoomType.ENTRANCE, 5, 4, 1, 2, id = "b"),
                    room(RoomType.LIVING, 1, 0, 4, 6),
                ),
            ),
        )
        assertNull(
            "an entrance touching no outer wall is an inner lobby, not a way in",
            frontDoorFromEntrance(
                listOf(
                    room(RoomType.LIVING, 0, 0, 6, 2, id = "top"),
                    room(RoomType.ENTRANCE, 2, 2, 2, 2),
                    room(RoomType.LIVING, 0, 4, 6, 2, id = "bottom"),
                    room(RoomType.BEDROOM, 0, 2, 2, 2, id = "left"),
                    room(RoomType.BEDROOM, 4, 2, 2, 2, id = "right"),
                ),
            ),
        )
        assertNull(
            "a square entrance in a corner has two equal claims and gets neither",
            frontDoorFromEntrance(
                listOf(
                    room(RoomType.ENTRANCE, 0, 0, 2, 2),
                    room(RoomType.LIVING, 2, 0, 4, 6, id = "l"),
                    room(RoomType.BEDROOM, 0, 2, 2, 4, id = "b"),
                ),
            ),
        )
        assertNull("nothing to attach a wall to", frontDoorFromEntrance(emptyList()))
    }

    @Test
    fun `in a corner, the longer contact wins`() {
        // Six cells of contact down the west wall against two across the north wall.
        val door = required(
            "flush on two walls, but one of them by three times as much",
            frontDoorFromEntrance(
                listOf(
                    room(RoomType.ENTRANCE, col = 0, row = 0, w = 2, h = 6),
                    room(RoomType.LIVING, col = 2, row = 0, w = 4, h = 6),
                ),
            ),
        )
        assertEquals(DoorSide.W, door.side)
    }

    // ---- marking the door on the photograph -----------------------------------------------------

    /** Rooms carrying BOTH a page box and a cell rect — the two spaces a tap on the photo bridges. */
    private fun photoRooms(): List<ScannedRoom> = listOf(
        ScannedRoom(
            type = RoomType.LIVING, label = "LIVING", rect = CellRect(0, 0, 4, 4),
            source = ScanBox(x = 0.2, y = 0.2, w = 0.4, h = 0.6),
        ),
        ScannedRoom(
            type = RoomType.BEDROOM, label = "BED", rect = CellRect(4, 0, 4, 4),
            source = ScanBox(x = 0.6, y = 0.2, w = 0.4, h = 0.6),
        ),
    )

    /** The home covers x 0.2–1.0 and y 0.2–0.8 of the sheet; a tap just inside each of its edges. */
    @Test
    fun `a tap near an edge of the picture lands on that wall`() {
        val rooms = photoRooms()
        assertEquals(DoorSide.N, required("top", doorForPhotoTap(0.5f, 0.22f, rooms)).side)
        assertEquals(DoorSide.S, required("bottom", doorForPhotoTap(0.5f, 0.78f, rooms)).side)
        assertEquals(DoorSide.W, required("left", doorForPhotoTap(0.22f, 0.5f, rooms)).side)
        assertEquals(DoorSide.E, required("right", doorForPhotoTap(0.98f, 0.5f, rooms)).side)
    }

    /**
     * ⭐ The round trip. A door tapped at a point must be DRAWN at that point — a marker that lands
     * some way from the finger is the kind of small lie that makes somebody tap again and again, and
     * it would be invisible to every other test we have. Half a cell is the honest limit: the door
     * is stored as a cell along a wall, so its marker sits at that cell's middle.
     */
    @Test
    fun `a door drawn back onto the page lands where it was tapped`() {
        val rooms = photoRooms()
        val door = required("a tap inside the top edge", doorForPhotoTap(0.4f, 0.22f, rooms))
        assertEquals(DoorSide.N, door.side)
        val marker = required("a placed door must be drawable", doorMarkerOnPage(door, rooms))
        assertEquals(
            "hard against the wall it was placed on",
            0.2, marker.second.toDouble(), 0.001,
        )
        assertEquals(
            "within half a cell of the finger, measured across the home",
            0.4, marker.first.toDouble(), 0.07,
        )
    }

    @Test
    fun `with no page geometry at all the whole sheet is the home, and a tap still works`() {
        val door = required(
            "older replies carry no page boxes, and must still be tappable",
            doorForPhotoTap(0.5f, 0.02f, photoRooms().map { it.copy(source = null) }),
        )
        assertEquals(DoorSide.N, door.side)
    }

    @Test
    fun `unplaced rooms have no walls to attach a door to`() {
        assertNull(doorForPhotoTap(0.5f, 0.2f, photoRooms().map { it.copy(rect = null) }))
    }
}
