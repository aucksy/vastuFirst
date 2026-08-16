package com.vastufirst.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How big the entrance mark is drawn — the one number every picture of a home now shares.
 *
 * ⚠⚠ THE FIRST TEST HERE IS A CRASH TEST, and it is why the sizing was rewritten at all. The old
 * arithmetic read
 *
 *     radius = (share of the plan).coerceIn(floorMeasuredFromTheFONT, capMeasuredFromTheTOUCH TARGET)
 *
 * — a floor that grew with the reader's text size against a ceiling that did not. `coerceIn` throws
 * `IllegalArgumentException` the moment the minimum passes the maximum, and at a 200 % font the floor
 * measured about 18.8 dp against a 19.2 dp cap: four tenths of a dp from crashing "Check what we
 * read" on the phones of exactly the readers this app is built for, and nothing in the render gate
 * or the geometry gate would have said a word. Both bounds are now constants, so the range cannot
 * close — and this test is what keeps them that way.
 *
 * ⚠ A SCREENSHOT CANNOT STAND IN FOR THIS. Every plan fixture in the harness is a blank coloured
 * rectangle of roughly one size, so no golden exercises a tiny plan, a huge one, or a landscape
 * sliver. The bounds are arithmetic and are tested as arithmetic.
 */
class DoorMarkTest {

    /** 48 dp of touch target at the harness's ordinary density — the mark's only reference point. */
    private val touch = 48f

    @Test
    fun the_size_never_asks_for_an_impossible_range() {
        // Every plausible picture, from a sliver to a wall poster, plus the degenerate cases a
        // not-yet-measured layout hands the draw pass on its first frame.
        val sides = listOf(0f, 1f, 12f, 40f, 92f, 167f, 320f, 1080f, 4000f)
        for (w in sides) {
            for (h in sides) {
                val r = doorMarkRadiusPx(w, h, touch)
                assertTrue(
                    "the mark must never be drawn at or below nothing at ${w}x$h: $r",
                    r > 0f,
                )
            }
        }
    }

    @Test
    fun a_big_sheet_is_held_at_the_cap_and_a_small_one_at_the_floor() {
        val cap = touch * PLAN_DOOR_MARK_MAX_OF_TOUCH
        val floor = touch * PLAN_DOOR_MARK_MIN_OF_TOUCH

        // ⚠ THE CAP IS WHAT THE READER ACTUALLY SEES on any ordinary near-square plan, because the
        // proportional size runs past it long before. That is the trap this project paid for once:
        // cutting the SHARE alone made the mark five per cent smaller and looked unchanged.
        assertEquals("an ordinary plan sits on the cap", cap, doorMarkRadiusPx(320f, 340f, touch), 0.01f)
        assertEquals("a huge one still sits on the cap", cap, doorMarkRadiusPx(4000f, 4000f, touch), 0.01f)

        // And a plan drawn small — a landscape phone, or the report's stand-in — never disappears.
        assertEquals("a tiny plan sits on the floor", floor, doorMarkRadiusPx(40f, 40f, touch), 0.01f)

        assertTrue("the floor must stay under the cap or the range closes", floor < cap)
    }

    @Test
    fun the_mark_is_measured_from_the_shorter_side() {
        // A landscape sliver is bounded by its HEIGHT, or the mark is taller than the picture.
        assertEquals(
            "a wide, shallow plan must be sized by its height",
            doorMarkRadiusPx(2000f, 60f, touch),
            doorMarkRadiusPx(60f, 2000f, touch),
            0.01f,
        )
        assertTrue(
            "and that size must still fit inside the shorter side",
            doorMarkRadiusPx(2000f, 60f, touch) * 2f <= 60f,
        )
    }

    @Test
    fun the_cap_is_well_under_the_touch_target_it_sits_in() {
        // The drawn mark and the target are two different sizes on purpose: you SEE one and HIT the
        // other. A mark as big as its own 48 dp target reads as a blot on the plan, which is what
        // the owner reported on 18 Aug 2026.
        assertTrue(
            "the drawn disc must stay comfortably inside its touch target",
            touch * PLAN_DOOR_MARK_MAX_OF_TOUCH * 2f < touch,
        )
    }

    @Test
    fun every_screen_spells_the_entrance_the_same_way() {
        // The hand-drawn editor printed "D" while three other screens printed "E", for one door.
        assertEquals("E", DOOR_MARK_LETTER)
    }
}
