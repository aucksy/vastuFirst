package com.vastufirst.app.ui.home

import com.vastufirst.data.SavedPlan
import com.vastufirst.shared.Intent
import com.vastufirst.shared.Level
import com.vastufirst.shared.Plan
import com.vastufirst.shared.Point
import com.vastufirst.shared.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐ "YOUR SCORE CHANGED, AND HERE IS WHY" — the promise that a Vastu ruling never moves somebody's
 * number in silence.
 *
 * ⚠ Why this file exists: ruling on where a prayer room belongs (1 August 2026) started scoring a
 * room the app had always skipped. Every home already saved on a phone was therefore about to be
 * re-scored. Someone who wrote their score down last week and opens the app to a different one, with
 * no word about it, cannot tell a considered ruling from a bug — and this product's whole claim is
 * that it does not make Vastu up.
 *
 * JUnit here, not kotlin.test — the message is the FIRST argument in this module.
 */
class ScoreChangeTest {

    private fun saved(id: String, name: String, score: Int, version: String) = SavedPlan(
        id = id,
        name = name,
        intent = Intent.BUILDING,
        propertyType = PropertyType.INDEPENDENT_HOUSE,
        plan = Plan(
            id = id,
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            intent = Intent.BUILDING,
            levels = listOf(
                Level(
                    index = 0,
                    outline = listOf(Point(0.0, 0.0), Point(8.0, 0.0), Point(8.0, 8.0), Point(0.0, 8.0)),
                ),
            ),
            northOffsetDegrees = 0,
        ),
        score = score,
        ruleSetVersion = version,
        unlocked = false,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val reason = "We decided where a prayer room belongs and started scoring it."

    @Test
    fun a_home_scored_under_the_current_rules_is_left_completely_alone() {
        val notice = scoreChangesFor(
            plans = listOf(saved("a", "Home 1", 31, "2026.08.01-1")),
            currentVersion = "2026.08.01-1",
            reason = reason,
            rescore = { 99 },
        )
        assertNull("nothing changed, so the user must not be told anything changed", notice)
    }

    @Test
    fun a_home_scored_under_an_older_ruleset_is_re_run_and_reported_with_both_numbers() {
        val notice = scoreChangesFor(
            plans = listOf(saved("a", "Dwarka flat", 31, "2026.07.19-1")),
            currentVersion = "2026.08.01-1",
            reason = reason,
            rescore = { 34 },
        )!!
        assertEquals(1, notice.changes.size)
        val change = notice.changes.first()
        assertEquals(31, change.oldScore)
        assertEquals(34, change.newScore)
        assertTrue("the card must know this one really moved", change.moved)
        assertEquals("Dwarka flat: 3.1 → 3.4", scoreChangeLine(change) { s -> "${s / 10}.${s % 10}" })
    }

    /**
     * ⭐ The case that matters most, and the easiest one to get wrong. The prayer-room ruling leaves
     * the bundled sample on the SAME number — the new room pulls the weighted average down by a
     * quarter of a point, which rounds away. Staying silent because the arithmetic happened to land
     * where it started would be luck presented as integrity: their READING changed, and their report
     * now contains a room it never mentioned before.
     */
    @Test
    fun a_home_whose_number_did_not_move_is_still_told_the_rules_did() {
        val notice = scoreChangesFor(
            plans = listOf(saved("a", "Home 1", 31, "2026.07.19-1")),
            currentVersion = "2026.08.01-1",
            reason = reason,
            rescore = { 31 },
        )!!
        assertEquals(1, notice.changes.size)
        assertEquals(0, notice.movedCount)
        assertTrue("the same number is said in words, not hidden", !notice.changes.first().moved)
        assertEquals(
            "Home 1: 3.1 — unchanged",
            scoreChangeLine(notice.changes.first()) { s -> "${s / 10}.${s % 10}" },
        )
        assertEquals("We changed a rule — your score is unchanged", scoreChangeTitle(notice))
    }

    @Test
    fun the_heading_never_claims_more_than_happened() {
        fun titleFor(vararg pairs: Pair<Int, Int>) = scoreChangeTitle(
            ScoreChangeNotice(
                reason,
                pairs.mapIndexed { i, (old, new) -> ScoreChange("h$i", "Home $i", old, new) },
            ),
        )
        assertEquals("Your score has changed", titleFor(31 to 34))
        assertEquals("We changed a rule — some scores moved", titleFor(31 to 34, 50 to 50))
        assertEquals("We changed a rule — your score is unchanged", titleFor(31 to 31, 50 to 50))
    }

    /**
     * ⭐ A "your score changed" card with no reason under it is worse than saying nothing: it tells
     * somebody their number moved and then refuses to say why. If the rule data ever ships without
     * its explanation, the honest behaviour is to leave the old number on screen.
     */
    @Test
    fun without_a_plain_words_reason_the_user_is_told_nothing_at_all() {
        val plans = listOf(saved("a", "Home 1", 31, "2026.07.19-1"))
        assertNull(scoreChangesFor(plans, "2026.08.01-1", reason = null, rescore = { 34 }))
        assertNull(scoreChangesFor(plans, "2026.08.01-1", reason = "   ", rescore = { 34 }))
    }

    /**
     * A home this build cannot re-run is dropped from the card entirely — old score, old version, no
     * claim made about it. Inventing a number for a home we could not read would be the one thing
     * worse than a silent change.
     */
    @Test
    fun a_home_that_cannot_be_re_run_is_left_out_rather_than_guessed_at() {
        val notice = scoreChangesFor(
            plans = listOf(
                saved("ok", "Home 1", 31, "2026.07.19-1"),
                saved("bad", "Home 2", 44, "2026.07.19-1"),
            ),
            currentVersion = "2026.08.01-1",
            reason = reason,
            rescore = { plan -> if (plan.id == "bad") null else 34 },
        )!!
        assertEquals(listOf("ok"), notice.changes.map { it.id })
    }

    /** ⭐ The real dataset must carry its own explanation, or the card above can never appear. */
    @Test
    fun the_shipped_rule_data_explains_itself_in_plain_words() {
        val ruleSet = com.vastufirst.rules.RuleSetLoader.loadDefault()
        val note = ruleSet.changeNote
        assertTrue("the shipped rules must say what changed in them", !note.isNullOrBlank())
        assertTrue(
            "a real explanation, not a label — this is the only thing a user gets to read",
            note!!.length > 120,
        )
    }
}
