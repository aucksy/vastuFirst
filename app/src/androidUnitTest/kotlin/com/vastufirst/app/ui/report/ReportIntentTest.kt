package com.vastufirst.app.ui.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐ WHO IS OFFERED A LAYOUT CHANGE, pinned (v0.6.6).
 *
 * The owner's ruling: only someone still BUILDING can move a wall, so only they are shown layout
 * changes. Someone BUYING is looking at a home that is already standing and someone ALREADY LIVING
 * there cannot rebuild it — for both, the report is remedies and nothing else, with no "change the
 * layout" and no "if you ever renovate".
 *
 * ⚠ Pinned as TEXT, not only as a picture. The goldens show the top of the document; a layout block
 * on the fourth problem sits well below the fold of every one of them, so a screenshot alone could
 * not prove the branch. This walks the whole rendered tree.
 *
 * The analysis is the bundled sample scored by the REAL engine, and it genuinely carries layout
 * fixes — otherwise this test would pass by having nothing to find.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ReportIntentTest {

    private val analysis = RenderFixtures.sampleAnalysis

    /** The guard on the guard: if the sample stopped carrying layout advice, the rest proves nothing. */
    @Test
    fun the_sample_home_really_does_carry_layout_advice() {
        assertTrue(
            "this fixture must have something for the BUILDING branch to show, or the other tests " +
                "here would pass for the wrong reason",
            analysis.defects.any { !it.layoutFix.isNullOrBlank() },
        )
    }

    /**
     * ⚠ `expandAll` on every case here, and the test is worthless without it. Since the report was
     * rebuilt (9 Aug 2026) a finding's reasoning lives inside a collapsed card, and a collapsed card
     * puts nothing in the semantics tree. Without expanding, `assertNoLayoutAdvice` would find no
     * layout advice for BUYING because it finds nothing at all — a green test proving only that the
     * cards were shut.
     */
    @Test
    fun someone_building_is_still_told_what_to_change() = runComposeUiTest {
        setContent { VastuTheme { ReportContent(analysis = analysis, intent = Intent.BUILDING, expandAll = true) } }
        onNodeWithText("Nothing is built yet", substring = true).assertExists()
        assertTrue(
            "the building branch must keep its layout advice",
            // ⚠ ignoreCase, because the heading is drawn through SectionLabel, which UPPERCASES it.
            // Matching the sentence case would silently find nothing and pass by accident.
            onAllNodesWithText("Change the layout", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun someone_buying_is_shown_remedies_and_never_a_layout_change() = runComposeUiTest {
        setContent { VastuTheme { ReportContent(analysis = analysis, intent = Intent.BUYING, expandAll = true) } }
        onNodeWithText("without moving a wall", substring = true).assertExists()
        assertNoLayoutAdvice("buying")
    }

    @Test
    fun someone_already_living_there_is_shown_remedies_and_never_a_renovation_note() = runComposeUiTest {
        setContent { VastuTheme { ReportContent(analysis = analysis, intent = Intent.LIVING, expandAll = true) } }
        onNodeWithText("without moving a wall", substring = true).assertExists()
        assertNoLayoutAdvice("already living here")
    }

    /**
     * ⭐ The guard on the new gate: the paid reasoning must genuinely be absent when unpaid.
     *
     * A locked row still shows its room and its verdict — that is deliberate and honest — but the
     * explanation and the remedies behind it must not be sitting in the tree where a screen reader,
     * a copy-all, or the next refactor could surface them.
     */
    @Test
    fun an_unpaid_report_does_not_carry_the_locked_reasoning() = runComposeUiTest {
        val paidOnly = analysis.defects.firstOrNull { !it.isFreeToRead(analysis.roomResults) }
        assertTrue(
            "the sample must have at least one paid-only defect or this proves nothing",
            paidOnly != null,
        )
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUYING, unlocked = false, expandAll = true)
            }
        }
        val sentence = paidOnly!!.explanation.take(40)
        assertTrue(
            "a locked finding must not print its explanation: \"$sentence…\"",
            onAllNodesWithText(sentence, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * ⭐⭐ THE "START HERE" CARD MUST NOT GIVE AWAY A PAID ROOM'S FIX — the revenue leak, pinned.
     *
     * Until 16 Aug 2026 this card printed the top defect's layout change (or its first remedy) with
     * no lock check at all. Whenever a home's worst problem sat on a room outside the free three,
     * the free reader was handed that room's fix above the fold, before the pay bar.
     *
     * ⚠ TWO REASONS NOTHING CAUGHT IT, both of which this test is shaped to defeat:
     *
     *   · The bundled sample's worst defect is a TOILET, and toilets are free — so the leak was
     *     invisible on the only home any test or golden has ever drawn. This test therefore builds a
     *     home whose worst defect sits on a PAID room, rather than trusting the sample's ordering.
     *   · [an_unpaid_report_does_not_carry_the_locked_reasoning] above asserts on `explanation`.
     *     This card never prints `explanation` — it prints `layoutFix` and the remedy line. Guarding
     *     one field said nothing about the other.
     *
     * Both directions are asserted. A test that only checks the text is absent when locked would
     * pass just as happily if the card stopped rendering, or if the fixture carried no fix at all.
     */
    @Test
    fun the_start_here_card_hides_a_paid_room_s_fix_until_it_is_paid_for() = runComposeUiTest {
        val paidTop = analysis.defects.firstOrNull {
            !it.isFreeToRead(analysis.roomResults) && !it.layoutFix.isNullOrBlank()
        }
        assertTrue(
            "the sample must carry a paid-room defect that has a layout fix, or this proves nothing",
            paidTop != null,
        )
        // Worst-problem-first is what the card reads, so put the paid one there deliberately rather
        // than hoping the engine's ranking happens to.
        val worstIsPaid = analysis.copy(
            defects = listOf(paidTop!!) + analysis.defects.filter { it !== paidTop },
        )
        val fix = paidTop.layoutFix!!.take(40)

        setContent {
            VastuTheme {
                ReportContent(
                    analysis = worstIsPaid, intent = Intent.BUILDING, unlocked = false, expandAll = true,
                )
            }
        }
        assertTrue(
            "\"Start here\" must not print a paid room's fix while the report is locked: \"$fix…\"",
            onAllNodesWithText(fix, substring = true).fetchSemanticsNodes().isEmpty(),
        )
        // …and the card is still there, still naming the problem. Hiding the row entirely would be
        // the "hidden wall" the free tier forbids.
        onNodeWithText("Start here", substring = true, ignoreCase = true).assertExists()
    }

    /** The other half: once paid, that same sentence must actually appear. */
    @Test
    fun the_start_here_card_shows_the_fix_once_the_report_is_unlocked() = runComposeUiTest {
        val paidTop = analysis.defects.first {
            !it.isFreeToRead(analysis.roomResults) && !it.layoutFix.isNullOrBlank()
        }
        val worstIsPaid = analysis.copy(
            defects = listOf(paidTop) + analysis.defects.filter { it !== paidTop },
        )
        val fix = paidTop.layoutFix!!.take(40)
        setContent {
            VastuTheme {
                ReportContent(
                    analysis = worstIsPaid, intent = Intent.BUILDING, unlocked = true, expandAll = true,
                )
            }
        }
        assertTrue(
            "a PAID report must still show the fix — otherwise the locked assertion above could " +
                "pass because the card renders nothing at all: \"$fix…\"",
            onAllNodesWithText(fix, substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    /**
     * Neither the layout block, nor the "if you ever renovate" demotion it used to become — nor the
     * MOVE_IT remedy.
     *
     * ⚠ That last one is the lesson. This list used to hold four phrases, and a real piece of layout
     * advice walked straight past all four because it is worded differently: the rule data's rank-0
     * remedy says *"Move or resize the element on the drawing … while the plan is still on paper"*.
     * Being rank 0 it sorted FIRST, so it reached every buyer and every resident from the first paid
     * report until 9 Aug 2026 — and no gate saw it; a person looking at the rendered screen did. A
     * ban-list only ever catches the sentences someone thought of, so the filter it guards
     * ([advisableRemedies]) is the real protection and this is the tripwire.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.assertNoLayoutAdvice(who: String) {
        listOf(
            "Change the layout", "renovate", "still free to make", "Nothing is built yet",
            "Move or resize", "still on paper", "on the drawing",
        ).forEach { banned ->
            assertTrue(
                "\"$banned\" must not appear anywhere in the report for $who — walls are already up",
                onAllNodesWithText(banned, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }
}
