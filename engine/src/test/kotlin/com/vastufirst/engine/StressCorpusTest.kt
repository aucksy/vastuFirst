package com.vastufirst.engine

import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisQuality
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
import com.vastufirst.shared.Site
import com.vastufirst.shared.Zone
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The production stress corpus — realistic Indian plans (angled plots, L/U/T/plus footprints,
 * courtyard homes, flats, elongated and extreme-scale plots) AND deliberately broken input (empty,
 * degenerate, NaN, self-intersecting, duplicate/missing markers). The universal contract, asserted
 * for EVERY case: the engine never throws, always returns a score in 0..100, and never falls back
 * to a scary dead-end — an angled clean rectangle in particular scores normally, not "irregular".
 */
class StressCorpusTest {

    private val engine = VastuEngine()

    private fun rot(p: Point, deg: Double, cx: Double, cy: Double): Point {
        val t = Math.toRadians(deg)
        val dx = p.x - cx; val dy = p.y - cy
        return Point(cx + dx * cos(t) - dy * sin(t), cy + dx * sin(t) + dy * cos(t))
    }

    private fun plan(
        outline: List<Point>,
        rooms: List<Room> = emptyList(),
        doors: List<Door> = listOf(Door("d", Point(50.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), true)),
        fixtures: List<Fixture> = emptyList(),
        site: Site? = null,
        north: Int = 0,
        id: String = "t",
    ) = Plan(id, PropertyType.INDEPENDENT_HOUSE, Intent.BUILDING, listOf(Level(0, outline, rooms, doors, fixtures)), site, north)

    private fun assertUsable(a: Analysis) {
        assertTrue(a.score in 0..100, "score out of range: ${a.score}")
    }

    private val goodRooms = listOf(
        Room("living", RoomType.LIVING, Fixtures.rect(66.0, 66.0, 28.0, 28.0)),
        Room("kitchen", RoomType.KITCHEN, Fixtures.rect(66.0, 6.0, 28.0, 26.0)),
        Room("master", RoomType.MASTER_BEDROOM, Fixtures.rect(6.0, 6.0, 30.0, 28.0)),
    )

    // ---------- Realistic scenarios ----------

    @Test
    fun `an angled clean rectangle scores normally — never 'irregular', no false cut`() {
        // A perfectly rectangular home tilted 30° to the compass (the common Indian case).
        val square = Fixtures.rect(10.0, 10.0, 80.0, 60.0)
        val tilted = square.map { rot(it, 30.0, 50.0, 40.0) }
        val a = engine.analyze(plan(tilted, goodRooms.map { it.copy(polygon = it.polygon.map { p -> rot(p, 30.0, 50.0, 40.0) }) }))
        assertUsable(a)
        assertFalse(a.shapeIrregular, "a clean rectangle must never read as irregular, at any angle")
        assertTrue(a.cuts.isEmpty() && a.extensions.isEmpty(), "a clean rectangle has no cut/extension")
        assertTrue(a.footprintTiltDegrees in 28.0..32.0, "tilt should be detected ~30°, was ${a.footprintTiltDegrees}")
        assertEquals(AnalysisQuality.OK, a.quality)
    }

    @Test
    fun `angled clean rectangles at many angles are never irregular and never falsely cut`() {
        val square = Fixtures.rect(15.0, 20.0, 70.0, 45.0)
        for (deg in intArrayOf(5, 12, 15, 22, 30, 45, 60, 75, 80)) {
            val tilted = square.map { rot(it, deg.toDouble(), 50.0, 42.0) }
            val a = engine.analyze(plan(tilted, goodRooms.map { it.copy(polygon = it.polygon.map { p -> rot(p, deg.toDouble(), 50.0, 42.0) }) }))
            assertUsable(a)
            assertFalse(a.shapeIrregular, "clean rectangle flagged irregular at $deg°")
            assertTrue(a.cuts.isEmpty() && a.extensions.isEmpty(), "false cut/extension at $deg°")
        }
    }

    @Test
    fun `an angled L-shape still detects its missing corner`() {
        val l = listOf(
            Point(0.0, 0.0), Point(100.0, 0.0), Point(100.0, 62.0),
            Point(62.0, 62.0), Point(62.0, 100.0), Point(0.0, 100.0),
        ).map { rot(it, 20.0, 44.0, 44.0) }
        val a = engine.analyze(plan(l, listOf(Room("m", RoomType.MASTER_BEDROOM, Fixtures.rect(6.0, 6.0, 25.0, 25.0).map { rot(it, 20.0, 44.0, 44.0) }))))
        assertUsable(a)
        assertFalse(a.shapeIrregular)
        assertTrue(a.cuts.isNotEmpty(), "an angled L-shape must still register its cut")
    }

    @Test
    fun `a courtyard home (open centre marked as a room) scores without crashing`() {
        val rooms = goodRooms + Room("court", RoomType.COURTYARD, Fixtures.rect(38.0, 38.0, 24.0, 24.0))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), rooms)))
    }

    @Test
    fun `U, T and plus footprints score without crashing`() {
        val u = listOf(Point(0.0, 0.0), Point(30.0, 0.0), Point(30.0, 60.0), Point(70.0, 60.0), Point(70.0, 0.0), Point(100.0, 0.0), Point(100.0, 100.0), Point(0.0, 100.0))
        val plus = listOf(
            Point(33.0, 0.0), Point(67.0, 0.0), Point(67.0, 33.0), Point(100.0, 33.0), Point(100.0, 67.0),
            Point(67.0, 67.0), Point(67.0, 100.0), Point(33.0, 100.0), Point(33.0, 67.0), Point(0.0, 67.0),
            Point(0.0, 33.0), Point(33.0, 33.0),
        )
        assertUsable(engine.analyze(plan(u, listOf(Room("m", RoomType.MASTER_BEDROOM, Fixtures.rect(4.0, 70.0, 22.0, 22.0))))))
        assertUsable(engine.analyze(plan(plus, listOf(Room("l", RoomType.LIVING, Fixtures.rect(70.0, 70.0, 25.0, 25.0))))))
    }

    @Test
    fun `an elongated plot is flagged but still scores`() {
        val long = Fixtures.rect(0.0, 0.0, 100.0, 28.0)   // ~3.6:1
        val a = engine.analyze(plan(long, listOf(Room("m", RoomType.MASTER_BEDROOM, Fixtures.rect(4.0, 4.0, 20.0, 20.0)))))
        assertUsable(a)
        assertTrue(a.aspectRatio > 2.0)
        assertTrue(a.defects.any { it.id == "X-15" }, "over-elongated footprint should raise X-15")
    }

    @Test
    fun `a corner plot with a road thrust is assessed`() {
        val site = Site(Fixtures.rect(0.0, 0.0, 100.0, 100.0), roads = listOf(Road("r", 225, pointsAtPlot = true)))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, site = site)))
    }

    @Test
    fun `a small flat-sized unit and extreme-scale plots all score (scale-free)`() {
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 1.0, 1.0), listOf(Room("l", RoomType.LIVING, Fixtures.rect(0.6, 0.6, 0.3, 0.3))),
            doors = listOf(Door("d", Point(0.5, 1.0), Point(0.0, 1.0), Point(1.0, 1.0), true)))))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100000.0, 80000.0), listOf(Room("l", RoomType.LIVING, Fixtures.rect(60000.0, 60000.0, 20000.0, 15000.0))),
            doors = listOf(Door("d", Point(50000.0, 80000.0), Point(0.0, 80000.0), Point(100000.0, 80000.0), true)))))
    }

    // ---------- Broken / degenerate input — must never crash ----------

    @Test
    fun `empty outline with no rooms is graceful (INSUFFICIENT), never a crash`() {
        val a = engine.analyze(plan(emptyList(), emptyList(), doors = emptyList()))
        assertEquals(AnalysisQuality.INSUFFICIENT, a.quality)
        assertEquals(0, a.score)
        assertTrue(a.notes.isNotEmpty())
    }

    @Test
    fun `empty outline but rooms present rebuilds the outline and scores`() {
        val a = engine.analyze(plan(emptyList(), goodRooms))
        assertUsable(a)
        assertEquals(AnalysisQuality.DEGRADED, a.quality)
        assertTrue(a.notes.any { it.code == "outline-rebuilt" })
    }

    @Test
    fun `two-point, collinear and zero-area outlines never crash`() {
        assertUsable(engine.analyze(plan(listOf(Point(0.0, 0.0), Point(10.0, 0.0)), goodRooms)))
        assertUsable(engine.analyze(plan(listOf(Point(0.0, 0.0), Point(50.0, 0.0), Point(100.0, 0.0)), goodRooms)))
        assertUsable(engine.analyze(plan(listOf(Point(5.0, 5.0), Point(5.0, 5.0), Point(5.0, 5.0), Point(5.0, 5.0)), goodRooms)))
    }

    @Test
    fun `NaN and infinite coordinates are cleaned, never crash`() {
        val nanOutline = listOf(Point(0.0, 0.0), Point(Double.NaN, 0.0), Point(100.0, 0.0), Point(100.0, 100.0), Point(Double.POSITIVE_INFINITY, 50.0), Point(0.0, 100.0))
        assertUsable(engine.analyze(plan(nanOutline, goodRooms)))
        // Entirely non-finite outline falls back to the rooms.
        assertUsable(engine.analyze(plan(listOf(Point(Double.NaN, Double.NaN), Point(Double.NaN, 1.0)), goodRooms)))
    }

    @Test
    fun `a self-intersecting (bowtie) outline is tidied, never crash`() {
        val bowtie = listOf(Point(0.0, 0.0), Point(100.0, 100.0), Point(100.0, 0.0), Point(0.0, 100.0))
        val a = engine.analyze(plan(bowtie, goodRooms))
        assertUsable(a)
        assertEquals(AnalysisQuality.DEGRADED, a.quality)
    }

    @Test
    fun `duplicate room ids, a zero-area room, and a room outside the footprint never crash`() {
        val rooms = listOf(
            Room("dup", RoomType.LIVING, Fixtures.rect(66.0, 66.0, 28.0, 28.0)),
            Room("dup", RoomType.KITCHEN, Fixtures.rect(66.0, 6.0, 28.0, 26.0)),      // duplicate id
            Room("flat", RoomType.BEDROOM, listOf(Point(5.0, 5.0), Point(25.0, 5.0), Point(25.0, 5.0), Point(5.0, 5.0))), // zero-area
            Room("far", RoomType.STORE, Fixtures.rect(5000.0, 5000.0, 20.0, 20.0)),   // outside footprint
        )
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), rooms)))
    }

    @Test
    fun `missing, multiple, and NaN-positioned markers never crash`() {
        // no main door
        val noDoor = engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, doors = emptyList()))
        assertUsable(noDoor)
        assertTrue(noDoor.doorResult == null)
        assertTrue(noDoor.notes.any { it.code == "no-main-door" })
        // multiple main doors
        val twoDoors = listOf(
            Door("a", Point(50.0, 100.0), Point(0.0, 100.0), Point(100.0, 100.0), true),
            Door("b", Point(100.0, 50.0), Point(100.0, 0.0), Point(100.0, 100.0), true),
        )
        val multi = engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, doors = twoDoors))
        assertUsable(multi)
        assertTrue(multi.notes.any { it.code == "multiple-main-doors" })
        // NaN fixture
        val fx = listOf(Fixture("f", FixtureType.OVERHEAD_TANK, Point(Double.NaN, Double.NaN)))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, fixtures = fx)))
    }

    @Test
    fun `out-of-range north offsets are normalized, no rooms and no door still score`() {
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, north = 725)))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), goodRooms, north = -30)))
        assertUsable(engine.analyze(plan(Fixtures.rect(0.0, 0.0, 100.0, 100.0), emptyList(), doors = emptyList())))
    }

    @Test
    fun `sample-01 is untouched by all the hardening — still exactly 31`() {
        val a = engine.analyze(Fixtures.sample01())
        assertEquals(31, a.score)
        assertEquals(AnalysisQuality.OK, a.quality)
        assertEquals(listOf("X-01", "X-03"), a.defects.map { it.id }.sorted())
        assertEquals(Zone.NE, a.defects.first { it.id == "X-01" }.zone)
    }
}
