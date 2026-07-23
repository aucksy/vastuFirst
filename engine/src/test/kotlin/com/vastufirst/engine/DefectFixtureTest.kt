package com.vastufirst.engine

import com.vastufirst.shared.Door
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.FixtureType
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Known-fixture defect tests (Impl PRD §Phase 1 gate). */
class DefectFixtureTest {

    private val engine = VastuEngine()

    private fun planWith(rooms: List<Room>, fixtures: List<Fixture> = emptyList()): Plan {
        val door = Door("main", Point(50.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), isMainEntrance = true)
        return Plan(
            id = "t", propertyType = PropertyType.INDEPENDENT_HOUSE, intent = Intent.BUILDING,
            levels = listOf(Level(0, Fixtures.rect(0.0, 0.0, 100.0, 100.0), rooms, listOf(door), fixtures)),
            northOffsetDegrees = 0,
        )
    }

    @Test
    fun `toilet in the North-East raises X-01 at MAJOR`() {
        val a = engine.analyze(planWith(listOf(Room("t", RoomType.TOILET, Fixtures.rect(70.0, 70.0, 25.0, 25.0)))))
        val d = a.defects.first { it.id == "X-01" }
        assertEquals(Severity.MAJOR, d.severity)
        assertTrue(d.remedies.isNotEmpty())
    }

    @Test
    fun `staircase in the Brahmasthan raises X-03 at MAJOR`() {
        val a = engine.analyze(planWith(listOf(Room("s", RoomType.STAIRCASE, Fixtures.rect(40.0, 40.0, 20.0, 20.0)))))
        assertEquals(Severity.MAJOR, a.defects.first { it.id == "X-03" }.severity)
    }

    @Test
    fun `a prohibited placement with no specific id falls back to X-GEN`() {
        // GARAGE prohibits SW but §8.4 names no X-id for it → generic defect.
        val a = engine.analyze(planWith(listOf(Room("g", RoomType.GARAGE, Fixtures.rect(5.0, 5.0, 25.0, 25.0)))))
        assertTrue(a.defects.any { it.id == "X-GEN" }, "expected a generic X-GEN for garage-in-SW")
    }

    @Test
    fun `overhead tank in NE is assessed (X-09) and no longer 'not assessed'`() {
        val tank = Fixture("tank", FixtureType.OVERHEAD_TANK, Point(85.0, 85.0))
        val a = engine.analyze(planWith(listOf(Room("l", RoomType.LIVING, Fixtures.rect(60.0, 60.0, 30.0, 30.0))), listOf(tank)))
        assertTrue(a.defects.any { it.id == "X-09" })
        assertFalse("X-09" in a.notAssessed)
    }

    @Test
    fun `absent fixtures are reported not-assessed, never clear`() {
        val a = engine.analyze(planWith(listOf(Room("l", RoomType.LIVING, Fixtures.rect(60.0, 60.0, 30.0, 30.0)))))
        assertTrue(a.notAssessed.containsAll(listOf("X-09", "X-11", "X-12", "X-13")))
    }
}
