package com.vastufirst.app.ui.common

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.ScanBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ⭐⭐ THE DIRECTION ON THE PLAN, ITS ARITHMETIC — pure, so every case is pinned without drawing anything.
 *
 * The owner, 29 Sep 2026: *"when I tap a room, show its direction ON the room, on the plan, instead of
 * drawing a box"* — and on 30 Sep, ending the trial: *"Keep directions. Remove the Boxes choice and the
 * switch."* Two things here would each fail silently in a picture:
 *
 *  · the tap — with no box drawn, the finger aims at the room itself, and a smallest-box-first rule
 *    would pick the WRONG room exactly where the reader's boxes are worst;
 *  · the label — centred on the room's middle (owner, 30 Sep 2026), and wholly inside the picture when
 *    the room is hard against its edge.
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST in every assertion below.
 */
class RoomDirectionTest {

    private fun room(id: String, type: RoomType, x: Double, y: Double, w: Double, h: Double) =
        PlanRoom(id = id, type = type, name = id, box = ScanBox(x = x, y = y, w = w, h = h))

    /**
     * ⭐ The case the plan's tap rule exists for. The reader's boxes swallow each other — 118 rooms
     * across the recorded corpus sit inside another room's box — so a toilet's box often spills over
     * the middle of the living room, where the smallest box containing the tap is the TOILET's. With no
     * box on screen the finger aims at the room itself: a tap on the LIVING room's middle must select
     * the living room.
     */
    @Test
    fun `a tap on the living room's dot picks the living room, even under a toilet's spilled box`() {
        val living = room("living", RoomType.LIVING, 0.0, 0.0, 0.6, 0.6)       // dot at 0.30, 0.30
        val toilet = room("toilet", RoomType.TOILET, 0.25, 0.25, 0.2, 0.2)     // dot at 0.35, 0.35
        val rooms = listOf(living, toilet)

        // The nearest middle among the boxes containing the tap.
        assertEquals(
            "a tap on a room's own dot must select that room",
            "living",
            roomNearestCentre(rooms, 0.30f, 0.30f, pxPerX = 400f, pxPerY = 400f, maxPx = 48f)?.id,
        )
        // …and a tap on the toilet's dot still gets the toilet.
        assertEquals("toilet", roomNearestCentre(rooms, 0.35f, 0.35f, 400f, 400f, 48f)?.id)
    }

    @Test
    fun `a tap outside every box takes the nearest dot within a finger's reach, and nothing further`() {
        val kitchen = room("kitchen", RoomType.KITCHEN, 0.6, 0.6, 0.2, 0.2)    // dot at 0.70, 0.70
        val rooms = listOf(kitchen)
        // Just right of the kitchen's box: 0.13 of a 400 px picture = 52 px from its dot, inside a
        // 64 px reach.
        assertEquals("kitchen", roomNearestCentre(rooms, 0.83f, 0.70f, 400f, 400f, 64f)?.id)
        // The same tap with a reach shorter than the distance selects nothing.
        assertNull("a tap in empty margin must not select a far room", roomNearestCentre(rooms, 0.95f, 0.95f, 400f, 400f, 48f))
    }

    @Test
    fun `a room the reader gave no rectangle has no dot and cannot be tapped`() {
        val placed = room("bed", RoomType.BEDROOM, 0.0, 0.0, 0.5, 0.5)
        val unplaced = PlanRoom(id = "ghost", type = RoomType.STORE, name = "ghost", box = null)
        assertNull(unplaced.centreOrNull())
        assertEquals("bed", roomNearestCentre(listOf(unplaced, placed), 0.2f, 0.2f, 400f, 400f, 48f)?.id)
    }

    /**
     * A room hard against a wall of the picture: the label moves only as far as it must to stay wholly
     * inside it, glow and all ([insetPx]) — so it is never cut off by the picture's own edge.
     */
    @Test
    fun `a label at the picture's edge stays wholly inside it, glow included`() {
        val inset = 4f
        // Top-left corner: pushed right and down, exactly to the inset.
        val corner = directionLabelTopLeft(5f, 5f, 100, 30, 400f, 400f, insetPx = inset)
        assertEquals("pushed in from the left wall to the inset", 4, corner.x)
        assertEquals("pushed down from the top wall to the inset", 4, corner.y)
        // Bottom-right corner: pulled back so its far edges sit one inset inside.
        val far = directionLabelTopLeft(398f, 398f, 100, 30, 400f, 400f, insetPx = inset)
        assertEquals("pulled in from the right wall", 400 - 100 - 4, far.x)
        assertEquals("pulled up from the bottom wall", 400 - 30 - 4, far.y)
        // Only the axis that needs it moves: hard against the top, still centred across.
        val top = directionLabelTopLeft(200f, 2f, 100, 30, 400f, 400f, insetPx = inset)
        assertEquals("still centred across", 150, top.x)
        assertEquals(4, top.y)
        // A label wider than the whole picture starts inside it rather than throwing.
        assertEquals(4, directionLabelTopLeft(40f, 200f, 100, 30, 80f, 400f, insetPx = inset).x)
    }

    /**
     * The owner: *"by default ... SW or N or E on the floor plan ... when you tap ... it converts to
     * southwest or north or east"*. The short form and the long one must come from ONE zone, so a room
     * can never read "SW" on the plan and "North-West" when tapped.
     */
    @Test
    fun `the short direction on the plan and the spelled-out one come from the same zone`() {
        assertEquals("SW", RoomDirection(com.vastufirst.shared.Zone.SW, "South-West").code)
        assertEquals("NE", RoomDirection(com.vastufirst.shared.Zone.NE, "North-East").code)
        assertEquals("N", RoomDirection(com.vastufirst.shared.Zone.N, "North").code)
        // The middle of the home is the one zone with no compass letters.
        assertEquals("C", RoomDirection(com.vastufirst.shared.Zone.BRAHMASTHAN, "Centre").code)
    }

    @Test
    fun `a screen reader hears the room's direction in the same words the row prints`() {
        assertEquals(
            "Your plan. Kitchen is in the North-East.",
            buildPlanDescription("Kitchen", hasDoor = false, selectedDirection = "North-East"),
        )
        // A room crossing into another zone: the row's middle dot is said as words, never read out.
        assertEquals(
            "Your plan. Living is in the North-West, and crosses the centre.",
            buildPlanDescription(
                "Living", hasDoor = false,
                selectedDirection = "North-West · crosses the centre",
            ),
        )
    }

    /**
     * ⭐ THE OWNER'S PLACEMENT (30 Sep 2026): *"improve the placement of direction short form to be in
     * center of the room"*. One label shows at a time now and its card names the room, so it no longer
     * has to keep clear of the name the plan prints at the middle.
     */
    @Test
    fun `the label's centre sits on the room's middle`() {
        val at = directionLabelTopLeft(200f, 200f, 100, 30, 400f, 400f, 4f)
        assertEquals("centred across the room's middle", 200f, at.x + 100 / 2f, 0.5f)
        assertEquals("centred on the room's middle, not above it", 200f, at.y + 30 / 2f, 0.5f)
    }

    /** At rest nothing is written on the plan any more, so the sentence must stop saying there is. */
    @Test
    fun `at rest a screen reader hears that a tap gives a room's direction, not that every room carries one`() {
        assertEquals(
            "Your scanned plan. Tap a room to hear its direction.",
            buildPlanDescription(null, hasDoor = false),
        )
    }
}
