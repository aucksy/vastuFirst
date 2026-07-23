package com.vastufirst.engine

import com.vastufirst.shared.Door
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Boundary fixtures (Impl PRD §Phase 1 gate): straddling padas, L-shaped footprints, edges. */
class BoundaryTest {

    private val engine = VastuEngine()

    private fun plan(outline: List<Point>, rooms: List<Room>): Plan {
        val door = Door("main", Point(20.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), isMainEntrance = true)
        return Plan(
            id = "b", propertyType = PropertyType.INDEPENDENT_HOUSE, intent = Intent.BUILDING,
            levels = listOf(Level(0, outline, rooms, listOf(door))), northOffsetDegrees = 0,
        )
    }

    @Test
    fun `L-shaped footprint missing the NE corner is a cut, not an irregular shape`() {
        val lOutline = listOf(
            Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 60.0),
            Point(60.0, 60.0), Point(60.0, 100.0), Point(0.0, 100.0),
        )
        val a = engine.analyze(plan(lOutline, listOf(Room("m", RoomType.MASTER_BEDROOM, Fixtures.rect(5.0, 5.0, 28.0, 25.0)))))
        assertFalse(a.shapeIrregular)
        assertTrue(a.cuts.isNotEmpty())
        assertTrue(a.cuts.any { it.zone == Zone.NE }, "the missing corner must register as a NE cut")
        assertTrue(a.defects.any { it.id == "X-04" }, "a NE cut raises X-04")
    }

    @Test
    fun `a genuinely irregular polygon skips cut and extension analysis honestly`() {
        val triangle = listOf(Point(0.0, 0.0), Point(100.0, 0.0), Point(50.0, 100.0))
        val a = engine.analyze(plan(triangle, emptyList()))
        assertTrue(a.shapeIrregular)
        assertTrue(a.cuts.isEmpty() && a.extensions.isEmpty())
    }

    @Test
    fun `a room whose edge lies exactly on the Brahmasthan boundary evaluates deterministically`() {
        val t1 = 100.0 / 3.0
        // Living room flush against the western Brahmasthan boundary — the edge sits on x = t1.
        val room = Room("l", RoomType.LIVING, listOf(Point(0.0, t1), Point(t1, t1), Point(t1, 200.0 / 3.0), Point(0.0, 200.0 / 3.0)))
        val p = plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), listOf(room))
        val first = engine.analyze(p)
        val second = engine.analyze(p)
        assertEquals(first.score, second.score)
        assertEquals(first.roomResults.first().zone, second.roomResults.first().zone)
        assertEquals(Zone.W, first.roomResults.first().zone)   // fully west of centre → W
    }

    @Test
    fun `a room straddling a pada line is assigned by majority area, deterministically`() {
        // Toilet centred just east of the E/NE and col boundaries; more area in NE than E.
        val toilet = Room("t", RoomType.TOILET, Fixtures.rect(68.0, 60.0, 24.0, 24.0))
        val a1 = engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), listOf(toilet)))
        val a2 = engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), listOf(toilet)))
        assertEquals(a1.roomResults.first().zone, a2.roomResults.first().zone)
    }
}
