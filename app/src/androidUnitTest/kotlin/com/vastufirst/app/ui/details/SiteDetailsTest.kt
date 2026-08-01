package com.vastufirst.app.ui.details

import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.newplan.siteAnswersFromSavedPlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Plan
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone
import com.vastufirst.shared.editor.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The optional extras — water tank, tree, road (docs/SCORE-ACCURACY-CAVEATS.md #4).
 *
 * ⭐ THE TEST THAT MATTERS is not "does the arithmetic agree with itself" but "does the ENGINE agree
 * with what the user was told". The user taps "north-east"; the engine must then raise the very
 * defect that a north-east water tank raises. Everything else here is scaffolding around that.
 *
 * ⚠ And it is checked at SEVERAL ANGLES, because the failure this guards against is invisible on a
 * square home: a plan drawn at an angle to the compass has a bigger analysis rectangle than its own
 * footprint, so a fixture placed by naive fractions lands in the wrong zone — silently, and only for
 * the users whose homes are not square to the compass, which in India is most of them.
 */
class SiteDetailsTest {

    private val engine = VastuEngine()

    /** A plain 8×8 home with no problems of its own, so any defect that appears came from an extra. */
    private val rooms = listOf(
        GridRoom("living", RoomType.LIVING, 0, 0, 3, 3),
        GridRoom("dining", RoomType.DINING, 3, 0, 2, 3),
        GridRoom("store-w", RoomType.STORE, 0, 3, 3, 2),
        GridRoom("hall", RoomType.CORRIDOR, 3, 3, 2, 2),
        GridRoom("master", RoomType.MASTER_BEDROOM, 0, 5, 3, 3),
        GridRoom("store-s", RoomType.STORE, 3, 5, 2, 3),
        GridRoom("kitchen", RoomType.KITCHEN, 5, 5, 3, 3),
        GridRoom("dining-e", RoomType.DINING, 5, 3, 3, 2),
        GridRoom("bed", RoomType.BEDROOM, 5, 0, 3, 3),
    )

    private fun plan(answers: SiteAnswers, north: Int = 0, cut: Set<Cell> = emptySet()): Plan =
        buildEnginePlan(rooms, null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, north, "t", cut, answers)!!

    private fun answers(vararg pairs: Pair<SiteItem, Zone>) = SiteAnswers(answers = pairs.toMap())

    // ── the engine must land the fixture where the user pointed ────────────────────────────────

    @Test
    fun a_fixture_lands_in_the_zone_the_user_named_at_every_angle() {
        // Read back through the engine's own zone lookup by asking whether the defect that belongs to
        // that zone fired. An overhead tank is a defect in the NORTH-EAST and fine in the SOUTH-WEST.
        for (north in listOf(0, 30, 45, 90, 137, 180, 270, 315)) {
            val bad = engine.analyze(plan(answers(SiteItem.OVERHEAD_TANK to Zone.NE), north))
            val good = engine.analyze(plan(answers(SiteItem.OVERHEAD_TANK to Zone.SW), north))
            assertTrue(
                "a tank the user put in the north-east did not raise its defect at $north° — " +
                    "the fixture is being filed in the wrong zone on angled homes",
                bad.defects.any { it.id == "X-09" },
            )
            assertFalse(
                "a tank in the south-west must NOT raise a defect at $north°",
                good.defects.any { it.id == "X-09" },
            )
        }
    }

    @Test
    fun underground_water_is_judged_by_its_own_rule_not_the_tanks() {
        assertTrue(
            "underground water in the south-west is the defect",
            engine.analyze(plan(answers(SiteItem.UNDERGROUND_WATER to Zone.SW))).defects.any { it.id == "X-09" },
        )
        assertFalse(
            "underground water in the north-east is where it belongs",
            engine.analyze(plan(answers(SiteItem.UNDERGROUND_WATER to Zone.NE))).defects.any { it.id == "X-09" },
        )
    }

    @Test
    fun a_heavy_tree_is_only_a_problem_where_the_rule_says() {
        assertTrue(
            engine.analyze(plan(answers(SiteItem.HEAVY_TREE to Zone.NE))).defects.any { it.id == "X-12" },
        )
        assertFalse(
            "a tree in the south-west weighs on nothing",
            engine.analyze(plan(answers(SiteItem.HEAVY_TREE to Zone.SW))).defects.any { it.id == "X-12" },
        )
    }

    @Test
    fun a_road_pointing_at_the_home_fires_only_from_the_harmful_directions() {
        for (zone in listOf(Zone.S, Zone.SW, Zone.SE, Zone.NW)) {
            assertTrue(
                "a road thrusting from the ${zone.name} must be reported",
                engine.analyze(plan(answers(SiteItem.ROAD to zone))).defects.any { it.id == "X-11" },
            )
        }
        for (zone in listOf(Zone.N, Zone.NE, Zone.E, Zone.W)) {
            assertFalse(
                "a road from the ${zone.name} is benign and must not be reported",
                engine.analyze(plan(answers(SiteItem.ROAD to zone))).defects.any { it.id == "X-11" },
            )
        }
    }

    // ── the honest half: unanswered means "not checked", never "clear" ─────────────────────────

    @Test
    fun with_nothing_answered_the_rules_are_reported_as_not_checked() {
        val a = engine.analyze(plan(SiteAnswers()))
        assertTrue("water storage must be listed as unchecked", "X-09" in a.notAssessed)
        assertTrue("the road rule must be listed as unchecked", "X-11" in a.notAssessed)
        assertTrue("trees must be listed as unchecked", "X-12" in a.notAssessed)
        assertTrue("and none of them may appear as a defect", a.defects.none { it.id in setOf("X-09", "X-11", "X-12") })
    }

    @Test
    fun answering_removes_a_rule_from_the_unchecked_list() {
        val a = engine.analyze(plan(answers(SiteItem.OVERHEAD_TANK to Zone.SW)))
        assertFalse(
            "once we know where the tank is, that rule has been checked — it must not still say otherwise",
            "X-09" in a.notAssessed,
        )
    }

    @Test
    fun saying_there_is_no_road_counts_as_checking_it() {
        val a = engine.analyze(plan(SiteAnswers(declined = setOf(SiteItem.ROAD))))
        assertFalse("'there isn't one' is an answer, not a skip", "X-11" in a.notAssessed)
        assertTrue("and it cannot produce a defect", a.defects.none { it.id == "X-11" })
    }

    // ── nothing answered must change nothing at all ────────────────────────────────────────────

    @Test
    fun an_unanswered_home_is_scored_exactly_as_before() {
        val without = buildEnginePlan(rooms, null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, 0, "t")!!
        val with = plan(SiteAnswers())
        assertEquals("adding the step must not change the plan for anyone who skips it", without, with)
        assertNull("no site is recorded until something is said about one", with.site)
        assertTrue(with.levels.first().fixtures.isEmpty())
    }

    // ── reopening a saved home must not drop what it was scored with ───────────────────────────

    @Test
    fun the_extras_come_back_when_a_saved_home_is_reopened() {
        for (north in listOf(0, 45, 137, 250)) {
            val given = answers(
                SiteItem.OVERHEAD_TANK to Zone.SW,
                SiteItem.UNDERGROUND_WATER to Zone.NE,
                SiteItem.HEAVY_TREE to Zone.S,
                SiteItem.ROAD to Zone.NW,
            )
            val recovered = siteAnswersFromSavedPlan(plan(given, north))
            assertEquals(
                "reopening at $north° lost or moved an answer",
                given.answers, recovered.answers,
            )
        }
    }

    @Test
    fun reopening_a_home_with_no_extras_reports_no_extras() {
        val recovered = siteAnswersFromSavedPlan(plan(SiteAnswers()))
        assertTrue(recovered.isEmpty)
    }

    @Test
    fun the_extras_survive_a_reopen_without_changing_the_score() {
        // The real failure this catches: reopen a home, touch one room, and the plan is rebuilt from
        // an empty answer sheet — quietly dropping every extra the home was scored with.
        val given = answers(SiteItem.OVERHEAD_TANK to Zone.NE, SiteItem.HEAVY_TREE to Zone.NE)
        val first = plan(given, north = 60)
        val reopened = plan(siteAnswersFromSavedPlan(first), north = 60)
        assertEquals(engine.analyze(first).score, engine.analyze(reopened).score)
        assertEquals(
            engine.analyze(first).defects.map { it.id }.sorted(),
            engine.analyze(reopened).defects.map { it.id }.sorted(),
        )
    }

    @Test
    fun an_extra_on_an_L_shaped_home_is_placed_against_the_real_shape() {
        // The cut corner changes the outline the fixture is measured against, so the two features
        // have to work together rather than each assuming a rectangle.
        val cut = (5..7).flatMap { c -> (0..2).map { r -> Cell(c, r) } }.toSet()
        val lShaped = rooms.filterNot { it.id == "bed" }
        val p = buildEnginePlan(
            lShaped, null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, 0, "t", cut,
            answers(SiteItem.OVERHEAD_TANK to Zone.SW),
        )!!
        assertEquals("the L must still be an L", 6, p.levels.first().outline.size)
        assertEquals(1, p.levels.first().fixtures.size)
        assertEquals(
            "the tank must come back out of the L in the corner it went into",
            Zone.SW, siteAnswersFromSavedPlan(p).answers[SiteItem.OVERHEAD_TANK],
        )
    }

    // ── the plain words on screen ──────────────────────────────────────────────────────────────

    @Test
    fun the_coverage_line_says_what_was_and_was_not_looked_at() {
        val none = coverageLine(SiteAnswers())
        assertTrue("it must name what the score IS built from: $none", none.contains("front door"))
        assertTrue("and what it is NOT: $none", none.contains("water tank"))

        val some = coverageLine(answers(SiteItem.OVERHEAD_TANK to Zone.SW))
        assertTrue("a partial answer must be counted honestly: $some", some.contains("1 of 4"))

        val all = coverageLine(
            SiteAnswers(declined = SiteItem.entries.toSet()),
        )
        assertFalse("once everything is answered it must stop listing gaps: $all", all.contains("haven't"))
    }

    @Test
    fun a_direction_and_a_bearing_are_the_same_answer_read_two_ways() {
        for (zone in ANSWERABLE_ZONES) {
            assertEquals(
                "the direction chosen must survive the trip through a compass bearing",
                zone, zoneOfBearing(bearingOf(zone)),
            )
        }
    }

    @Test
    fun the_centre_is_never_offered_as_an_answer() {
        assertEquals(8, ANSWERABLE_ZONES.size)
        assertFalse(
            "'in the centre' is not a direction anybody can turn to face",
            Zone.BRAHMASTHAN in ANSWERABLE_ZONES,
        )
    }

    @Test
    fun a_site_is_only_recorded_once_something_is_said_about_the_road() {
        assertNull(siteFor(answers(SiteItem.OVERHEAD_TANK to Zone.SW), square()))
        assertNotNull(siteFor(answers(SiteItem.ROAD to Zone.S), square()))
        assertNotNull(
            "saying there is no road is still an answer",
            siteFor(SiteAnswers(declined = setOf(SiteItem.ROAD)), square()),
        )
    }

    private fun square() = plan(SiteAnswers()).levels.first().outline
}
