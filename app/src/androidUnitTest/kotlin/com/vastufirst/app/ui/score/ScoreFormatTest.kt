package com.vastufirst.app.ui.score

import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.components.spokenScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The score is COMPUTED out of a hundred and SHOWN out of ten. This pins the showing.
 *
 * Pure — no Robolectric, no Compose. `scoreOutOfTen` is deliberately a plain function taking the
 * decimal mark as an argument precisely so it can be tested at every value and every mark without
 * rendering anything.
 *
 * ⭐ Nothing here touches the engine. The engine still returns whole 0–100 numbers and its own
 * tests still assert the worked example scores exactly 31; the case below just states what a
 * person then reads on screen.
 */
class ScoreFormatTest {

    @Test fun the_worked_example_reads_three_point_one() {
        assertEquals("the §15 worked example scores 31 and must read as 3.1", "3.1", scoreOutOfTen(31))
    }

    @Test fun every_score_keeps_exactly_one_decimal() {
        assertEquals("a zero must not read as a bare 0", "0.0", scoreOutOfTen(0))
        assertEquals("single digits are tenths", "0.8", scoreOutOfTen(8))
        assertEquals("a round five must not read as 5", "0.5", scoreOutOfTen(5))
        assertEquals("a round fifty must not read as 5", "5.0", scoreOutOfTen(50))
        assertEquals("the example from the brief", "4.7", scoreOutOfTen(47))
        assertEquals("a perfect home", "10.0", scoreOutOfTen(100))
        assertEquals("just under perfect", "9.9", scoreOutOfTen(99))
    }

    /** Every 0–100 value must produce a number that reads back as the same score. No gaps, no
     *  duplicates, no rounding drift — the display is a rewriting, not an approximation. */
    @Test fun every_value_round_trips() {
        val seen = mutableSetOf<String>()
        for (s in 0..100) {
            val shown = scoreOutOfTen(s)
            assertTrue("score $s produced a duplicate reading $shown", seen.add(shown))
            assertEquals("score $s must read back as itself", s, (shown.replace(".", "")).toInt())
            assertEquals("score $s must have exactly one decimal", 1, shown.substringAfter('.').length)
        }
    }

    /** The band thresholds stay on the engine's 0–100, so a home lands in exactly the band it did
     *  before this change. Stated here as the two boundaries a user can see. */
    @Test fun band_boundaries_are_unmoved() {
        assertEquals("the 'strong' boundary was 75 and now reads 7.5", "7.5", scoreOutOfTen(75))
        assertEquals("the 'workable' boundary was 50 and now reads 5.0", "5.0", scoreOutOfTen(50))
    }

    @Test fun a_score_outside_the_range_can_never_print_nonsense() {
        assertEquals("below the floor clamps", "0.0", scoreOutOfTen(-7))
        assertEquals("above the ceiling clamps", "10.0", scoreOutOfTen(140))
    }

    @Test fun the_decimal_mark_is_not_baked_in() {
        assertEquals("a comma language must get a comma", "4,7", scoreOutOfTen(47, ','))
        assertEquals("and so must a whole number", "10,0", scoreOutOfTen(100, ','))
    }

    @Test fun a_screen_reader_hears_the_number_and_its_scale() {
        assertEquals("TalkBack must say the scale, not a stray slash", "Score 4.7 out of 10", spokenScore(47))
        assertEquals("and the same in a comma language", "Score 4,7 out of 10", spokenScore(47, ','))
    }

    /**
     * ⭐ Why the mark is looked up rather than typed in — and why this test survives the app being
     * English only (CLAUDE.md §2e). Every phone locale in India writes a dot, so hard-coding "."
     * would LOOK correct forever and be wrong the first time the app is opened on a phone set to a
     * comma language. The app's own words never change; the PHONE's number format is not ours to
     * assume. This asserts the platform's own data, which is what `deviceDecimalMark()` reads.
     */
    @Test fun the_platform_knows_the_mark_for_the_phones_we_land_on() {
        listOf("en-IN", "hi-IN", "ta-IN", "te-IN", "mr-IN", "bn-IN").forEach { tag ->
            assertEquals("$tag writes a dot", '.', markFor(tag))
        }
        assertEquals("German writes a comma — the case a hard-coded dot would get wrong", ',', markFor("de-DE"))
    }

    private fun markFor(tag: String): Char =
        DecimalFormatSymbols.getInstance(Locale.forLanguageTag(tag)).decimalSeparator
}
