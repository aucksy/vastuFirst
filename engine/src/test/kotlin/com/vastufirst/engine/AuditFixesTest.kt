package com.vastufirst.engine

import com.vastufirst.shared.Door
import com.vastufirst.shared.Fixture
import com.vastufirst.shared.FixtureType
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Road
import com.vastufirst.shared.Room
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.SchoolProfile
import com.vastufirst.shared.Site
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the Phase 1 audit fixes: rules that were silently reported "clear" when
 * they were never actually evaluated (X-10, X-11), and config knobs that used to lie.
 */
class AuditFixesTest {

    private val engine = VastuEngine()

    private fun plan(rooms: List<Room>, fixtures: List<Fixture> = emptyList(), site: Site? = null): Plan {
        val door = Door("main", Point(50.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), isMainEntrance = true)
        return Plan(
            id = "t", propertyType = PropertyType.INDEPENDENT_HOUSE, intent = Intent.BUILDING,
            levels = listOf(Level(0, Fixtures.rect(0.0, 0.0, 100.0, 100.0), rooms, listOf(door), fixtures)),
            site = site, northOffsetDegrees = 0,
        )
    }

    // ---- X-10: pooja sharing a wall with a toilet ----

    @Test
    fun `a pooja sharing a wall with a toilet raises X-10`() {
        val rooms = listOf(
            Room("pooja", RoomType.POOJA, Fixtures.rect(6.0, 74.0, 28.0, 20.0)),
            Room("toilet", RoomType.TOILET, Fixtures.rect(34.0, 74.0, 26.0, 20.0)),   // shares wall x=34
        )
        assertTrue(engine.analyze(plan(rooms)).defects.any { it.id == "X-10" })
    }

    @Test
    fun `a pooja NOT touching a toilet does not raise X-10`() {
        val rooms = listOf(
            Room("pooja", RoomType.POOJA, Fixtures.rect(6.0, 74.0, 28.0, 20.0)),
            Room("toilet", RoomType.TOILET, Fixtures.rect(70.0, 74.0, 20.0, 20.0)),   // gap between them
        )
        assertFalse(engine.analyze(plan(rooms)).defects.any { it.id == "X-10" })
    }

    @Test
    fun `a fixture flagged beneath a staircase raises X-10`() {
        val rooms = listOf(Room("st", RoomType.STAIRCASE, Fixtures.rect(5.0, 5.0, 20.0, 20.0)))
        val under = Fixture("bed", FixtureType.BED, Point(15.0, 15.0), underRoomId = "st")
        assertTrue(engine.analyze(plan(rooms, listOf(under))).defects.any { it.id == "X-10" })
    }

    // ---- X-11: Veedhi Shoola (road thrust), and honest not-assessed ----

    @Test
    fun `a road thrusting from the SW raises X-11 and clears it from not-assessed`() {
        val site = Site(Fixtures.rect(0.0, 0.0, 100.0, 100.0), roads = listOf(Road("r", 225, pointsAtPlot = true)))
        val a = engine.analyze(plan(listOf(Room("l", RoomType.LIVING, Fixtures.rect(60.0, 60.0, 30.0, 30.0))), site = site))
        assertTrue(a.defects.any { it.id == "X-11" })
        assertFalse("X-11" in a.notAssessed)
    }

    @Test
    fun `a benign road from the NE is assessed but raises no X-11`() {
        val site = Site(Fixtures.rect(0.0, 0.0, 100.0, 100.0), roads = listOf(Road("r", 45, pointsAtPlot = true)))
        val a = engine.analyze(plan(listOf(Room("l", RoomType.LIVING, Fixtures.rect(60.0, 60.0, 30.0, 30.0))), site = site))
        assertFalse(a.defects.any { it.id == "X-11" })
        assertFalse("X-11" in a.notAssessed)   // assessed and clear, not "not assessed"
    }

    @Test
    fun `with no site, X-11 stays not-assessed (never silently clear)`() {
        val a = engine.analyze(plan(listOf(Room("l", RoomType.LIVING, Fixtures.rect(60.0, 60.0, 30.0, 30.0)))))
        assertTrue("X-11" in a.notAssessed)
        assertFalse(a.defects.any { it.id == "X-11" })
    }

    // ---- Config honesty ----

    @Test
    fun `a room straddling two prohibited zones raises a defect for each (section 4-6)`() {
        // A kitchen big enough to violate BOTH the Brahmasthan and the NE (both prohibited for
        // R-02). §4.6 requires every violated (RoomType, Zone) pair to resolve to a defect.
        val kitchen = Room("k", RoomType.KITCHEN, Fixtures.rect(40.0, 40.0, 50.0, 50.0))
        val defects = engine.analyze(plan(listOf(kitchen))).defects.map { it.id }
        assertTrue("X-02" in defects, "kitchen in NE → X-02")
        assertTrue("X-GEN" in defects, "kitchen in Brahmasthan → generic defect (no specific id)")
    }

    @Test
    fun `an unimplemented school profile degrades to the default reading with a note, never an error`() {
        val a = engine.analyze(Fixtures.sample01().copy(schoolProfile = SchoolProfile.SIXTEEN_ZONE))
        assertEquals(31, a.score, "falls back to the classic 8-direction score")
        assertTrue(a.notes.any { it.code == "school-default" })
        // Must report the reading it ACTUALLY gave, never mislabel it as the requested school.
        assertEquals(SchoolProfile.TRADITIONAL_8, a.schoolProfile)
    }

    @Test
    fun `the audit fixes leave sample-01 at exactly 31 with the same two defects`() {
        val a = engine.analyze(Fixtures.sample01())
        assertEquals(31, a.score)
        assertEquals(listOf("X-01", "X-03"), a.defects.map { it.id }.sorted())
    }
}
