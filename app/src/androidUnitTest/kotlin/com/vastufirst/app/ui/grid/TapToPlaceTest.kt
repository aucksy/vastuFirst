package com.vastufirst.app.ui.grid

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import com.vastufirst.shared.editor.overlaps
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐⭐ A ROOM TAPPED ON THE PLAN LANDS IN THE GRID CELLS UNDER THAT TAP (tap-to-place, 14 Oct 2026).
 *
 * The maths behind "Show us on your plan" — see TapToPlace.kt. Written before the card: the card only
 * collects taps, and everything the score depends on happens here.
 */
class TapToPlaceTest {

    private fun parked(vararg types: RoomType): List<GridRoom> = types.mapIndexed { i, t ->
        GridRoom(id = "scan-$i", type = t, col = (i * 2) % 10, row = (i * 2) / 10 * 2, w = 2, h = 2, label = t.name)
    }

    private fun GridRoom.rect() = CellRect(col, row, w, h)

    @Test
    fun `each room goes to the corner of the grid it was tapped in`() {
        val rooms = parked(RoomType.KITCHEN, RoomType.MASTER_BEDROOM, RoomType.LIVING, RoomType.POOJA)
        val taps = linkedMapOf(
            "scan-0" to PlanPoint(0.85f, 0.15f),   // kitchen: top right of the plan = north-east
            "scan-1" to PlanPoint(0.15f, 0.85f),   // master: bottom left = south-west
            "scan-2" to PlanPoint(0.15f, 0.15f),   // living: top left = north-west
            "scan-3" to PlanPoint(0.85f, 0.85f),   // pooja: bottom right = south-east
        )
        val out = placeFromTaps(rooms, taps, cols = 10, rows = 10).associateBy { it.id }
        assertEquals(CellRect(8, 0, 2, 2), out.getValue("scan-0").rect())
        assertEquals(CellRect(0, 8, 2, 2), out.getValue("scan-1").rect())
        assertEquals(CellRect(0, 0, 2, 2), out.getValue("scan-2").rect())
        assertEquals(CellRect(8, 8, 2, 2), out.getValue("scan-3").rect())
    }

    @Test
    fun `a room tapped in the middle of the others lands between them in proportion`() {
        val rooms = parked(RoomType.LIVING, RoomType.KITCHEN, RoomType.BEDROOM)
        val taps = linkedMapOf(
            "scan-0" to PlanPoint(0.2f, 0.2f),
            "scan-1" to PlanPoint(0.8f, 0.8f),
            "scan-2" to PlanPoint(0.5f, 0.5f),
        )
        val bed = placeFromTaps(rooms, taps, 10, 10).single { it.id == "scan-2" }
        assertEquals("the middle tap is the middle of the grid", CellRect(4, 4, 2, 2), bed.rect())
    }

    @Test
    fun `nothing moves until every room has a tap`() {
        val rooms = parked(RoomType.LIVING, RoomType.KITCHEN)
        val out = placeFromTaps(rooms, mapOf("scan-0" to PlanPoint(0.1f, 0.1f)), 10, 10)
        assertSame("a room with no tap has no honest place, so nothing is placed yet", rooms, out)
    }

    @Test
    fun `two rooms tapped on the same spot never overlap and both are kept`() {
        val rooms = parked(RoomType.TOILET, RoomType.BATHROOM, RoomType.LIVING)
        val taps = linkedMapOf(
            "scan-0" to PlanPoint(0.5f, 0.5f),
            "scan-1" to PlanPoint(0.5f, 0.5f),
            "scan-2" to PlanPoint(0.1f, 0.1f),
        )
        val out = placeFromTaps(rooms, taps, 10, 10)
        assertEquals(3, out.size)
        assertFalse(out[0].rect().overlaps(out[1].rect()))
        // The first tapped keeps the exact spot; the second goes right beside it.
        val a = out[0].rect()
        val b = out[1].rect()
        assertTrue("next to it, not flung across the plan: $a vs $b", kotlin.math.abs(a.col - b.col) + kotlin.math.abs(a.row - b.row) <= 3)
    }

    @Test
    fun `a real unplaced read of 21 rooms keeps every room its kind and its name with no overlap`() {
        val outcome = ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)
        check(outcome is ScanOutcome.Assisted) { "the unsized recording must stay unplaced" }
        val rooms = toGridRooms(outcome.rooms, 10, 10)
        // Taps spread over the plan in a scattered but repeatable pattern.
        val taps = LinkedHashMap<String, PlanPoint>()
        rooms.forEachIndexed { i, r -> taps[r.id] = PlanPoint(((i * 37) % 100) / 100f, ((i * 61) % 100) / 100f) }
        val out = placeFromTaps(rooms, taps, 10, 10)

        assertEquals(rooms.map { it.id }, out.map { it.id })
        assertEquals("never retyped", rooms.map { it.type }, out.map { it.type })
        assertEquals("never renamed", rooms.map { it.label }, out.map { it.label })
        for (i in out.indices) for (j in i + 1 until out.size) {
            assertFalse("${out[i].id} overlaps ${out[j].id}", out[i].rect().overlaps(out[j].rect()))
        }
        out.forEach { assertTrue("inside the grid: $it", it.col >= 0 && it.row >= 0 && it.col + it.w <= 10 && it.row + it.h <= 10) }
    }

    @Test
    fun `a skipped room comes back after the others`() {
        val rooms = parked(RoomType.LIVING, RoomType.KITCHEN, RoomType.BEDROOM)
        assertEquals("scan-0", nextToTap(rooms, emptyMap(), emptyList())?.id)
        assertEquals("scan-1", nextToTap(rooms, emptyMap(), listOf("scan-0"))?.id)
        val tapped = mapOf("scan-1" to PlanPoint(0f, 0f), "scan-2" to PlanPoint(1f, 1f))
        assertEquals("only the skipped one is left, so it is asked again", "scan-0", nextToTap(rooms, tapped, listOf("scan-0"))?.id)
        assertEquals(null, nextToTap(rooms, tapped + ("scan-0" to PlanPoint(0.5f, 0.5f)), emptyList()))
    }

    @Test
    fun `the room is asked for by what the plan prints`() {
        assertEquals("MASTER BEDROOM 1", tapName(GridRoom("scan-0", RoomType.MASTER_BEDROOM, 0, 0, 2, 2, label = " MASTER BEDROOM 1 ")))
        assertEquals("a room with no caption is asked for by its kind", "Kitchen", tapName(GridRoom("scan-1", RoomType.KITCHEN, 0, 0, 2, 2)))
    }
}
