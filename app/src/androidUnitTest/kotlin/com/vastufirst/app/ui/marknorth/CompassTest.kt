package com.vastufirst.app.ui.marknorth

import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The compass helper's arithmetic and the double-check sentence.
 *
 * ⚠ This file exists because a compass that is WRONG is worse than no compass at all. Setting North
 * by hand at least leaves the user aware they were guessing; a confident "North is at 271°" that is
 * off by ninety degrees puts precise, authoritative, wrong directions on every room in a report
 * somebody paid ₹699 for. So the sign convention is pinned two ways: against the North dial's own
 * emit maths, and against the ENGINE, by actually scoring a home and reading back where its rooms
 * landed. If those two ever disagree, this file fails.
 */
class CompassTest {

    private val engine = VastuEngine()

    // ── the conversion ─────────────────────────────────────────────────────────────────────────

    @Test fun pointing_the_phone_north_means_the_plan_is_already_north_up() {
        assertEquals("a phone pointing north means the top of the plan IS north", 0, northOffsetForHeading(0f))
    }

    @Test fun the_conversion_mirrors_the_bearing() {
        // The user aims the phone the way the top of the plan faces, so North measured INSIDE the
        // drawing runs the other way round the circle.
        assertEquals(270, northOffsetForHeading(90f))    // plan faces east  ⇒ north is 270° round the plan
        assertEquals(180, northOffsetForHeading(180f))   // plan faces south ⇒ north is behind you
        assertEquals(90, northOffsetForHeading(270f))    // plan faces west
        assertEquals(315, northOffsetForHeading(45f))
    }

    @Test fun the_conversion_never_returns_360_or_a_negative() {
        for (tenth in 0..3600) {
            val n = northOffsetForHeading(tenth / 10f)
            assertTrue("heading ${tenth / 10f} produced $n", n in 0..359)
        }
        assertEquals("a heading of 0.4° must wrap to 0, not 360", 0, northOffsetForHeading(0.4f))
        assertEquals("negative headings normalise", 10, northOffsetForHeading(-10f))
        assertEquals("headings past a full turn normalise", 0, northOffsetForHeading(720f))
    }

    /**
     * ⭐⭐ THE CROSS-CHECK THAT MATTERS. Not "does the formula match itself" but "does the engine
     * agree with the sentence printed on screen".
     *
     * A home is drawn with one room hard against the TOP of the plan. The user is told to point the
     * phone the way the top of the plan faces. So if they face EAST, that top room is physically on
     * the east side of the home — and the engine, given the North this produces, must say East.
     */
    @Test fun the_engine_puts_the_top_room_where_the_phone_was_pointed() {
        val rooms = listOf(
            GridRoom("top", RoomType.POOJA, 3, 0, 2, 2),        // hard against the top edge
            GridRoom("body", RoomType.LIVING, 0, 2, 8, 6),      // the rest of the home
        )
        val expected = mapOf(
            0f to Zone.N, 90f to Zone.E, 180f to Zone.S, 270f to Zone.W,
        )
        for ((heading, zone) in expected) {
            val north = northOffsetForHeading(heading)
            val plan = buildEnginePlan(rooms, null, Intent.BUILDING, PropertyType.INDEPENDENT_HOUSE, north, "t")!!
            val top = engine.analyze(plan).roomResults.first { it.roomId == "top" }
            assertEquals(
                "with the phone pointed at $heading° the top room must come out in the $zone — " +
                    "it came out in the ${top.zone}. The compass sign is backwards.",
                zone, top.zone,
            )
        }
    }

    // ── smoothing ──────────────────────────────────────────────────────────────────────────────

    @Test fun the_first_reading_is_taken_as_is() {
        assertEquals(123f, smoothHeading(null, 123f), 1e-4f)
    }

    @Test fun smoothing_crosses_north_without_swinging_to_the_opposite_direction() {
        // ⚠ The bug this prevents: a plain average of 350° and 10° is 180° — due SOUTH. It only shows
        // up when the user happens to be facing north, which is exactly the case a Vastu app cannot
        // afford to get wrong.
        val out = smoothHeading(350f, 10f, alpha = 0.5f)
        assertTrue("smoothing 350° toward 10° gave $out; it must land near 0°, not near 180°", out < 1f || out > 359f)
        val back = smoothHeading(10f, 350f, alpha = 0.5f)
        assertTrue("smoothing 10° toward 350° gave $back", back < 1f || back > 359f)
    }

    @Test fun smoothing_moves_toward_the_new_reading_and_settles_on_it() {
        var v: Float? = null
        repeat(40) { v = smoothHeading(v, 90f) }
        assertEquals("a steady reading must converge on itself", 90f, v!!, 0.5f)
    }

    @Test fun smoothing_never_leaves_the_circle() {
        var v: Float? = null
        for (i in 0 until 200) {
            v = smoothHeading(v, (i * 37f) % 360f)
            assertTrue("smoothed heading escaped 0..360: $v", v!! >= 0f && v!! < 360f)
        }
    }

    // ── the spoken word ────────────────────────────────────────────────────────────────────────

    @Test fun the_compass_word_names_the_eight_points() {
        assertEquals("North", compassWord(0f))
        assertEquals("North", compassWord(359f))
        assertEquals("North-East", compassWord(45f))
        assertEquals("East", compassWord(90f))
        assertEquals("South-East", compassWord(135f))
        assertEquals("South", compassWord(180f))
        assertEquals("South-West", compassWord(225f))
        assertEquals("West", compassWord(270f))
        assertEquals("North-West", compassWord(315f))
        assertEquals("a bearing just under the boundary stays north", "North", compassWord(22f))
        assertEquals("and just over it turns the corner", "North-East", compassWord(23f))
    }

    // ── the double-check sentence ──────────────────────────────────────────────────────────────

    @Test fun the_check_states_facts_a_person_can_verify_by_standing_in_the_room() {
        val rooms = listOf(
            GridRoom("k", RoomType.KITCHEN, 5, 5, 3, 3),
            GridRoom("p", RoomType.POOJA, 0, 0, 2, 2),
            GridRoom("l", RoomType.LIVING, 2, 2, 3, 3),
        )
        val plan = buildEnginePlan(rooms, GridDoor(DoorSide.W, 3), Intent.BUILDING, PropertyType.FLAT, 0, "t")!!
        val analysis = engine.analyze(plan)
        val claims = NorthCheck.claims(analysis)

        assertTrue("the front door must be quoted first — it is the easiest thing to check", claims.first().startsWith("your front door"))
        assertTrue("at most three claims, or nobody reads them", claims.size <= 3)
        assertTrue(
            "the kitchen must be named: ${claims.joinToString("; ")}",
            claims.any { it.contains("kitchen") },
        )
        assertTrue(
            "nothing may be reported as being 'in the centre' — that is not a direction you can turn to face",
            claims.none { it.contains("centre") },
        )
        val sentence = NorthCheck.sentence(analysis)
        assertTrue("the sentence must join the claims readably: $sentence", sentence.contains(", and "))
    }

    @Test fun the_check_says_nothing_when_there_is_nothing_to_say() {
        assertEquals(emptyList<String>(), NorthCheck.claims(null))
        assertEquals("", NorthCheck.sentence(null))
    }

    @Test fun the_check_follows_north_round_the_compass() {
        // Turn the home relative to north and the sentence must turn with it — otherwise it is
        // decoration rather than a check.
        val rooms = listOf(
            GridRoom("k", RoomType.KITCHEN, 6, 0, 2, 2),
            GridRoom("l", RoomType.LIVING, 0, 0, 6, 8),
        )
        fun kitchenClaim(north: Int): String {
            val plan = buildEnginePlan(rooms, null, Intent.BUILDING, PropertyType.FLAT, north, "t")!!
            return NorthCheck.claims(engine.analyze(plan)).first { it.contains("kitchen") }
        }
        val atZero = kitchenClaim(0)
        val atNinety = kitchenClaim(90)
        assertTrue(
            "the kitchen claim did not change when north moved 90°: '$atZero' vs '$atNinety'",
            atZero != atNinety,
        )
    }

    @Test fun a_bearing_maps_to_its_45_degree_sector() {
        assertEquals(Zone.N, NorthCheck.zoneOfBearing(0.0))
        assertEquals(Zone.N, NorthCheck.zoneOfBearing(22.4))
        assertEquals(Zone.NE, NorthCheck.zoneOfBearing(22.6))
        assertEquals(Zone.E, NorthCheck.zoneOfBearing(90.0))
        assertEquals(Zone.SW, NorthCheck.zoneOfBearing(225.0))
        assertEquals(Zone.N, NorthCheck.zoneOfBearing(359.9))
        assertEquals("negative bearings normalise", Zone.NW, NorthCheck.zoneOfBearing(-45.0))
    }

    @Test fun the_smoothed_reading_and_the_offset_agree_end_to_end() {
        // The two pure pieces used together: a stream of noisy samples around due east must end up
        // setting a north offset of 270, not something 90° out.
        var v: Float? = null
        for (i in 0 until 60) v = smoothHeading(v, 90f + (if (i % 2 == 0) 3f else -3f))
        val n = northOffsetForHeading(v!!)
        assertTrue("noisy readings around east produced north offset $n", abs(n - 270) <= 1)
    }
}
