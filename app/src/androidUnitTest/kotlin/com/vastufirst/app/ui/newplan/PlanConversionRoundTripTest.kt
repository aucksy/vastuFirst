package com.vastufirst.app.ui.newplan

import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid ⇄ engine flip, proven both directions, plus the score invariants the flip depends on.
 * Pure JVM (no Android) — `buildEnginePlan` and its inverses (`gridRoomsFromPlan`/`gridDoorFromPlan`)
 * are pure, and the engine is headless. UAT cases I1–I4, I7, H1–H6, J2/J3.
 *
 * The reopen path is: stored engine [Plan] → `gridRoomsFromPlan` / `gridDoorFromPlan` → the exact
 * grid the user drew. A drift here is a home that changes shape when you open it — one of the
 * "too many issues" this QA pass exists to catch.
 */
class PlanConversionRoundTripTest {

    private val engine = VastuEngine()

    private fun plan(rooms: List<GridRoom>, door: GridDoor?, north: Int = 0): Plan =
        buildEnginePlan(rooms, door, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, north, "t")!!

    // ── rooms round-trip exactly (I1, I3, I4) ────────────────────────────────────────────────────

    @Test
    fun `the sample home's rooms come back byte-for-byte`() {
        val rooms = SamplePlans.all.first().rooms
        val recovered = gridRoomsFromPlan(plan(rooms, null))
        assertEquals(rooms, recovered)
    }

    @Test
    fun `every bundled sample round-trips its rooms and its door`() {
        for (sample in SamplePlans.all) {
            val p = plan(sample.rooms, sample.door)
            assertEquals("rooms drifted for ${sample.id}", sample.rooms, gridRoomsFromPlan(p))
            assertEquals("door drifted for ${sample.id}", sample.door, gridDoorFromPlan(p, sample.rooms))
        }
    }

    @Test
    fun `rooms high on a 10-deep plot round-trip despite negative engine-Y`() {
        // Rows 8-9 are exactly where ey(row)=GRID-row goes negative. The flip must still be exact —
        // this is the "fixed GRID=8 y-origin could bite" case the brief calls out (UAT I3).
        val rooms = listOf(
            GridRoom("a", RoomType.BEDROOM, 2, 8, 2, 2),      // bottom = 10 → engine y down to -2
            GridRoom("b", RoomType.KITCHEN, 6, 7, 3, 3),      // bottom = 10
            GridRoom("c", RoomType.LIVING, 0, 0, 2, 2),
        )
        assertEquals(rooms, gridRoomsFromPlan(plan(rooms, null)))
    }

    @Test
    fun `a single 1x1 room round-trips`() {
        val rooms = listOf(GridRoom("a", RoomType.TOILET, 4, 4, 1, 1))
        assertEquals(rooms, gridRoomsFromPlan(plan(rooms, null)))
    }

    // ── door round-trips on every wall, incl. thin footprints (H1–H6) ────────────────────────────

    @Test
    fun `a door on each wall round-trips to the same side and cell`() {
        // A footprint with room on all edges so every wall exists and a mid-cell is inside it.
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 1, 1, 4, 4))   // cols 1..5, rows 1..5
        for (door in listOf(
            GridDoor(DoorSide.N, 2), GridDoor(DoorSide.S, 3),
            GridDoor(DoorSide.E, 2), GridDoor(DoorSide.W, 3),
        )) {
            val recovered = gridDoorFromPlan(plan(rooms, door), rooms)
            assertEquals("door on ${door.side} drifted", door, recovered)
        }
    }

    @Test
    fun `door side is never confused on a one-cell-deep footprint (N vs S)`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 3, 4, 1))   // 1 cell deep, rows 3..4
        assertEquals(DoorSide.N, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.N, 1)), rooms)!!.side)
        assertEquals(DoorSide.S, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.S, 1)), rooms)!!.side)
    }

    @Test
    fun `door side is never confused on a one-cell-wide footprint (E vs W)`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 3, 0, 1, 4))   // 1 cell wide, cols 3..4
        assertEquals(DoorSide.E, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.E, 1)), rooms)!!.side)
        assertEquals(DoorSide.W, gridDoorFromPlan(plan(rooms, GridDoor(DoorSide.W, 1)), rooms)!!.side)
    }

    @Test
    fun `a door tapped past the rooms snaps onto the footprint and stays put on reload (C15)`() {
        // Footprint cols 2..6. A door on N "at cell 9" is clamped to the footprint when the plan is
        // built; the reloaded door must equal what was actually built (no jump), i.e. within 2..5.
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 2, 0, 4, 4))
        val built = plan(rooms, GridDoor(DoorSide.N, 9))
        val recovered = gridDoorFromPlan(built, rooms)!!
        assertEquals(DoorSide.N, recovered.side)
        assertTrue("door cell ${recovered.cell} not clamped to footprint 2..5", recovered.cell in 2..5)
        // Idempotent: rebuilding from the recovered door yields the identical door (stable on reopen).
        assertEquals(recovered, gridDoorFromPlan(plan(rooms, recovered), rooms))
    }

    // ── empty / degenerate input (I7, J4 safety) ─────────────────────────────────────────────────

    @Test
    fun `a stored room with an empty polygon is skipped, not crashed`() {
        val p = Plan(
            id = "t", propertyType = PropertyType.INDEPENDENT_HOUSE, intent = Intent.BUILDING,
            levels = listOf(Level(index = 0, outline = emptyList(), rooms = listOf(
                Room(id = "ghost", type = RoomType.BEDROOM, polygon = emptyList()),
                Room(id = "real", type = RoomType.KITCHEN, polygon = listOf(
                    Point(0.0, 6.0), Point(2.0, 6.0), Point(2.0, 8.0), Point(0.0, 8.0),
                )),
            ))),
            northOffsetDegrees = 0,
        )
        val recovered = gridRoomsFromPlan(p)
        assertEquals(1, recovered.size)
        assertEquals("real", recovered.single().id)
    }

    @Test
    fun `no rooms means no plan (empty state)`() {
        assertNull(buildEnginePlan(emptyList(), null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, 0, "t"))
    }

    @Test
    fun `no door round-trips to no door`() {
        val rooms = listOf(GridRoom("a", RoomType.LIVING, 0, 0, 3, 3))
        assertNull(gridDoorFromPlan(plan(rooms, null), rooms))
    }

    // ── score is translation-invariant (J3) — the headline invariant ─────────────────────────────

    @Test
    fun `the same rooms score identically wherever they sit on the grid`() {
        // Move the whole home around the canvas — including down into negative engine-Y — and the
        // score must not budge. The engine lays its pada grid on the rooms' bounding box, so position
        // is not a scoring term; if it were, a home would score differently just for being drawn lower.
        val base = listOf(
            GridRoom("a", RoomType.POOJA, 0, 0, 2, 2),
            GridRoom("b", RoomType.KITCHEN, 3, 0, 2, 2),
            GridRoom("c", RoomType.MASTER_BEDROOM, 0, 3, 2, 2),
            GridRoom("d", RoomType.TOILET, 3, 3, 2, 2),
        )
        val reference = engine.analyze(plan(base, null)).score
        assertTrue("reference score out of range: $reference", reference in 0..100)
        for ((dCol, dRow) in listOf(1 to 0, 0 to 1, 2 to 2, 4 to 4, 0 to 5, 3 to 6)) {
            val shifted = base.map { it.copy(col = it.col + dCol, row = it.row + dRow) }
            assertEquals(
                "score changed after shifting by ($dCol,$dRow) — position must not affect the score",
                reference, engine.analyze(plan(shifted, null)).score,
            )
        }
    }

    @Test
    fun `translation invariance holds with the door too`() {
        val rooms = listOf(
            GridRoom("a", RoomType.LIVING, 0, 0, 3, 3),
            GridRoom("b", RoomType.KITCHEN, 4, 0, 2, 2),
        )
        val door = GridDoor(DoorSide.S, 1)
        val reference = engine.analyze(plan(rooms, door)).score
        for ((dCol, dRow) in listOf(1 to 1, 0 to 4, 2 to 5)) {
            val sr = rooms.map { it.copy(col = it.col + dCol, row = it.row + dRow) }
            val sd = GridDoor(door.side, door.cell + dCol)   // shift the door along the same wall
            assertEquals(
                "score changed after shifting by ($dCol,$dRow) with a door",
                reference, engine.analyze(plan(sr, sd)).score,
            )
        }
    }

    @Test
    fun `the analysis is never an error and always usable for any single room`() {
        for (type in RoomType.values()) {
            val a = engine.analyze(plan(listOf(GridRoom("a", type, 3, 3, 2, 2)), null))
            assertNotNull("null analysis for $type", a)
            assertTrue("score out of range for $type: ${a.score}", a.score in 0..100)
        }
    }
}
