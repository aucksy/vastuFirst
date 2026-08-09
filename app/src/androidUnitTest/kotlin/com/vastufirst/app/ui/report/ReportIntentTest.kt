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

    /** Neither the layout block, nor the "if you ever renovate" demotion it used to become. */
    private fun androidx.compose.ui.test.ComposeUiTest.assertNoLayoutAdvice(who: String) {
        listOf("Change the layout", "renovate", "still free to make", "Nothing is built yet").forEach { banned ->
            assertTrue(
                "\"$banned\" must not appear anywhere in the report for $who — walls are already up",
                onAllNodesWithText(banned, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes().isEmpty(),
            )
        }
    }
}
