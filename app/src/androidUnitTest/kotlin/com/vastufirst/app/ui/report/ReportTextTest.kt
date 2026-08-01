package com.vastufirst.app.ui.report

import com.vastufirst.app.ui.newplan.SamplePlans
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ⭐ WHAT THE ₹699 REPORT ACTUALLY SAYS — pinned against the bundled sample home, read through the
 * real engine, exactly as the report screen reads it.
 *
 * Every assertion here is one of the things the owner found by paying for his own report:
 *  - thirteen of fifteen problems offered the identical two remedies;
 *  - the reasons were one line and sometimes circular;
 *  - rooms that were already right carried no reason at all;
 *  - ⭐ rooms rated "not ideal" appeared NOWHERE, while the free score counted them to justify ₹699;
 *  - the "couldn't check" list printed raw rule codes to the customer.
 *
 * JUnit here, not kotlin.test — the message is the FIRST argument in this module.
 */
class ReportTextTest {

    private val sample = SamplePlans.all.first()

    private val analysis: Analysis = VastuEngine().analyze(
        buildEnginePlan(
            rooms = sample.rooms,
            door = sample.door,
            intent = Intent.BUILDING,
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            north = sample.north,
            planId = "report-text-test",
        )!!,
    )

    private val universal = setOf("structural-correction", "vastu-shanti")

    // ── the problems ───────────────────────────────────────────────────────────────────────────

    @Test
    fun every_problem_offers_a_remedy_of_its_own_or_says_why_it_cannot() {
        assertTrue("the sample home must actually raise problems", analysis.defects.isNotEmpty())
        analysis.defects.forEach { d ->
            val specific = d.remedies.filterNot { it.id in universal }
            assertTrue(
                "problem ${d.id} offers only 'move it' and Vastu Shanti, with no note saying why",
                specific.isNotEmpty() || !d.remedyNote.isNullOrBlank(),
            )
        }
    }

    @Test
    fun two_different_problems_do_not_get_the_same_two_remedies() {
        // Deduped by rule first: the same rule firing twice (two toilets in the same corner) SHOULD
        // read the same. What must never happen is two DIFFERENT problems reading identically.
        val byRule = analysis.defects.distinctBy { it.id }
        val sets = byRule.map { d -> d.remedies.map { it.id }.toSet() }
        assertTrue("two different problems must not read identically: $sets", sets.size == sets.distinct().size)
    }

    /**
     * ⚠ The report rendered `remedy.text` and dropped the provenance — so a rock-salt bowl invented
     * in the 20th century and a rite prescribed in the Mayamatam reached the page looking alike.
     */
    @Test
    fun every_remedy_says_where_it_comes_from() {
        val lines = analysis.defects.flatMap { d -> d.remedies.map { remedyLine(it) } }
        assertTrue("the sample home must offer remedies", lines.isNotEmpty())
        val allowed = listOf("(From classical text)", "(Traditional practice)", "(Modern practice)", "(Schools disagree)")
        lines.forEach { line ->
            assertTrue("'$line' reaches the reader with no provenance", allowed.any { line.endsWith(it) })
        }
        // And the tag must be the remedy's own, not the defect's — within one problem they differ.
        val kinds = analysis.defects.first().remedies.map { it.provenance }.distinct()
        assertTrue("a problem's remedies must not all share one provenance here", kinds.size > 1)
    }

    @Test
    fun every_reason_explains_rather_than_restating_the_rule() {
        analysis.defects.forEach { d ->
            // The old catch-all was 52 characters: "This room sits in a zone its placement rule
            // prohibits." A reason that names the direction, its element and where the thing belongs
            // cannot be that short.
            assertTrue(
                "the reason for ${d.id} is too short to be explaining anything (${d.explanation.length} chars)",
                d.explanation.length > 150,
            )
            assertFalse(
                "the reason for ${d.id} is circular — it just restates that the rule prohibits it",
                d.explanation.contains("sits in a zone its placement rule prohibits"),
            )
        }
    }

    @Test
    fun a_problem_names_the_direction_it_is_about() {
        val meaning = zoneMeaning(Zone.NE, analysis.zoneInfo)
        assertNotNull("the report must be able to say what a direction IS", meaning)
        assertTrue("the Sanskrit name is in the data and must reach the reader", meaning!!.contains("Ishanya"))
        assertTrue("the presiding deity must reach the reader", meaning.contains("Shiva"))
        assertTrue("the element must reach the reader", meaning.contains("water"))
    }

    // ── the rooms that are right, and the rooms that are merely not ideal ──────────────────────

    @Test
    fun a_room_that_is_already_right_says_why() {
        val good = analysis.roomResults.filter { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }
        assertTrue("the sample home must have rooms that are already right", good.isNotEmpty())
        good.forEach {
            val why = whyRight(it)
            assertTrue("${it.type} in ${it.zone} is ticked with no reason at all", why.length > 80)
        }
    }

    /**
     * ⭐ THE ONE THAT MATTERED MOST. A room rated "not ideal" was counted by the free score screen
     * to justify the price and then appeared nowhere in the thing that was paid for.
     */
    @Test
    fun every_not_ideal_room_has_something_to_show_and_a_reason() {
        val notIdeal = analysis.roomResults.filter { it.verdict == Verdict.SUBOPTIMAL }
        assertTrue("the sample home must exercise the not-ideal band", notIdeal.isNotEmpty())
        notIdeal.forEach {
            val why = whyNotIdeal(it)
            assertTrue("${it.type} in ${it.zone} is counted but never explained", why.length > 80)
            assertTrue(
                "${it.type} in ${it.zone} must be told where the tradition does put one",
                why.contains("that is the") || why.contains("neither"),
            )
        }
    }

    @Test
    fun the_free_screen_counts_exactly_what_the_paid_report_shows() {
        val counted = analysis.roomResults.count { it.verdict == Verdict.SUBOPTIMAL }
        val previewed = unlockPreviewLines(analysis, shownFree = 3).filter { it.contains("not ideal") }
        assertTrue("the not-ideal band is counted, so it must be previewed exactly once", previewed.size == 1)
        assertTrue(
            "the preview must name the real number of not-ideal rooms",
            previewed.first().startsWith("$counted "),
        )
    }

    @Test
    fun the_paywall_never_promises_a_section_this_home_does_not_have() {
        val lines = unlockPreviewLines(analysis, shownFree = 3)
        if (analysis.disputes.isEmpty()) {
            assertTrue("no disputes on this home, so none may be advertised", lines.none { it.contains("disagree") })
        }
        if (analysis.doorResult == null) {
            assertTrue("no door on this home, so none may be advertised", lines.none { it.contains("front door") })
        }
        assertTrue("the paywall must say something concrete", lines.isNotEmpty())
    }

    // ── the front door ─────────────────────────────────────────────────────────────────────────

    @Test
    fun the_front_door_is_named_not_coded() {
        val door = analysis.doorResult
        assertNotNull("the sample home has a front door", door)
        val title = doorTitle(door!!)
        assertFalse("the wall must be a word, never a letter", Regex("\\b[NESW]\\b").containsMatchIn(title))
        assertTrue("the wall must be spelled out", title.contains("wall"))
        assertTrue("the reader must be told which of the 32 positions it is", doorPlaceLine(door).contains("of 32"))
        val explanation = doorExplanation(door)
        assertTrue("the door's reading must explain the 32-position table", explanation.contains("32 named positions"))
        // ⚠ Only the South-West corner arc raises a problem of its own, so a door can read
        // unfavourably and appear in no problems list. It must not be a dead end for the reader.
        if (door.verdict != com.vastufirst.shared.PadaVerdict.AUSPICIOUS) {
            assertTrue(
                "an unfavourable door must be given something to do about it",
                explanation.contains("same wall"),
            )
        }
        // ⭐ The bundled sample's own door lands on one of the two positions the sources leave
        // unnamed, so this is the path a demo actually walks. It must SAY so, not show a blank.
        if (door.pada.name == null) {
            assertTrue("an unnamed position must be admitted, not left blank", !doorUnnamedNote(door).isNullOrBlank())
        } else {
            assertTrue("a named position needs no apology", doorUnnamedNote(door) == null)
        }
    }

    @Test
    fun the_paywall_preview_never_grows_past_three_lines() {
        // Every extra line pushes the payment notice below the fold on a 320 dp phone at large font.
        assertTrue(
            "the unlock card's preview must stay within three lines",
            unlockPreviewLines(analysis, shownFree = 3).size <= 3,
        )
    }

    // ── nothing internal reaches the reader ────────────────────────────────────────────────────

    @Test
    fun the_couldnt_check_list_never_shows_a_rule_code() {
        assertTrue("the sample home leaves fixture rules unchecked", analysis.notChecked.isNotEmpty())
        // These are the very ids the report used to print: "· X-09".
        assertEquals(
            "the reader's list and the engine's list must cover the same rules",
            analysis.notAssessed.toSet(), analysis.notChecked.map { it.id }.toSet(),
        )
        analysis.notChecked.forEach {
            val line = notCheckedLine(it)
            assertFalse("'$line' is a rule code, not a sentence", Regex("^X-\\d+$").matches(line))
            assertTrue("'$line' is too terse to mean anything to a reader", line.length > 12)
        }
        assertTrue("the reader must be told how to get these checked", notCheckedHow(analysis.notChecked).isNotEmpty())
    }

    // ── the free/paid split ────────────────────────────────────────────────────────────────────

    @Test
    fun the_free_screen_shows_the_opening_line_and_the_report_shows_the_rest() {
        analysis.defects.forEach { d ->
            val opening = firstSentence(d.explanation)
            assertTrue("${d.id}: the free preview must be shorter than the whole reason", opening.length < d.explanation.length)
            assertTrue("${d.id}: the free preview must still be a whole sentence", opening.trim().endsWith("."))
            // ⚠ One reason once opened with a 201-character sentence, and the free score screen —
            // which shows the opening sentence and nothing else — became a wall of text that pushed
            // the payment notice below the fold on a 320 dp phone.
            assertTrue("${d.id}: the opening sentence is ${opening.length} chars, too long to headline", opening.length <= 120)
        }
    }

    /**
     * ⚠ The screenshot that shows "Not ideal" and "Already right" only works if that fixture really
     * has no problems — one defect and both sections drop below the fold again, unseen, which is the
     * state this release exists to end. So the fixture's shape is asserted rather than assumed.
     */
    @Test
    fun the_fixture_that_shows_the_lower_sections_really_has_no_problems() {
        val clean = com.vastufirst.app.render.RenderFixtures.cleanAnalysis
        assertTrue("the clean fixture must raise no problems, or both sections fall below the fold: " +
            clean.defects.map { it.id }, clean.defects.isEmpty())
        assertTrue("it must exercise the not-ideal band", clean.roomResults.any { it.verdict == Verdict.SUBOPTIMAL })
        assertTrue(
            "it must exercise the already-right section too",
            clean.roomResults.any { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE },
        )
        assertNotNull("and the favourable door reading", clean.doorResult)
    }

    /**
     * ⭐ The same guarantee for the picture that shows the PRAYER-ROOM ruling. If that fixture ever
     * grows a defect, the not-ideal card and the disputes card fall below the fold and the ruling
     * ships unphotographed — which is the exact failure the last release ended.
     */
    @Test
    fun the_prayer_room_fixture_really_shows_the_ruling_and_both_readings() {
        val a = com.vastufirst.app.render.RenderFixtures.prayerRoomAnalysis
        assertTrue(
            "no problems, or everything below them drops out of frame: " + a.defects.map { it.id },
            a.defects.isEmpty(),
        )
        val pooja = a.roomResults.first { it.type == com.vastufirst.shared.RoomType.POOJA }
        assertEquals("the prayer room must be in the North-West, as the bundled demo puts it", Zone.NW, pooja.zone)
        assertEquals(
            "and it must now be SCORED — 'not scored' is the state the ruling ended",
            Verdict.SUBOPTIMAL, pooja.verdict,
        )
        assertTrue(
            "the already-right section must have something in it too",
            a.roomResults.any { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE },
        )
        val w12 = a.disputes.firstOrNull { it.id == "W-12" }
        assertNotNull("both readings must still reach the reader after the ruling", w12)
        assertNotNull(
            "⭐ and the card must say which reading the score uses — showing both sides while " +
                "staying silent about where the number stands is a half-truth",
            w12!!.howWeScore,
        )
    }

    /**
     * ⭐ A dispute we have NOT ruled on must not claim we scored it. The "what your score uses" line
     * is drawn only when the rule data supplies one, so the eight surfaced-not-scored questions stay
     * exactly what they were: both readings, no winner.
     */
    @Test
    fun only_the_disputes_we_actually_ruled_on_say_what_the_score_uses() {
        val ruleSet = com.vastufirst.rules.RuleSetLoader.loadDefault()
        val ruled = ruleSet.disputes.filter { it.howWeScore != null }.map { it.id }
        assertEquals("W-12 is the only ruling that moved into the score", listOf("W-12"), ruled)
        ruleSet.disputes.filter { it.howWeScore == null }.forEach {
            assertTrue(
                "${it.id} is surfaced, not scored, so it must still carry both readings",
                it.readingA.text.isNotBlank() && it.readingB.text.isNotBlank(),
            )
        }
    }

    @Test
    fun directions_are_listed_in_words() {
        assertEquals("the North or the East", zoneList(listOf(Zone.N, Zone.E)))
        assertEquals("the South-West", zoneList(listOf(Zone.SW)))
        assertEquals("the West, the North-West or the North", zoneList(listOf(Zone.W, Zone.NW, Zone.N)))
    }
}
