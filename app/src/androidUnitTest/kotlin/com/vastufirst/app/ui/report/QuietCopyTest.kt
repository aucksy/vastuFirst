package com.vastufirst.app.ui.report

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.app.ui.details.MoreDetailsContent
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.designsystem.components.VastuRevealAll
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE PAGE GOT QUIETER AND NOTHING WAS DELETED — both halves, pinned.
 *
 * Owner, 19 September 2026: *"The app still looks too busy with too much to read and see… Decrease
 * the copy and hide text behind i symbol wherever possible."* Eleven standing sentences moved off
 * the page and behind an **i**.
 *
 * ⚠ THIS TEST EXISTS BECAUSE EACH HALF ALONE IS A LIE THAT PASSES.
 *
 *   · Assert only that a sentence is GONE from the page, and deleting it outright passes — which is
 *     exactly what `CLAUDE.md` §2h forbids, and what a later session tidying "dead copy" would do.
 *   · Assert only that it is REACHABLE, and putting it straight back on the page passes — the
 *     screen creeps back to where it started and nothing notices.
 *
 * So every sentence here is checked twice: absent when the page first draws, present once it is
 * opened. That pair is the whole of the change, and it is the only shape of it that can be wrong
 * in a way a person would notice.
 *
 * ⚠ AND ONE OF THEM IS OPENED BY A REAL TAP, not by the photography seam. [VastuRevealAll] proves
 * the text still exists; only a tap proves a reader can actually get to it. A heading whose note
 * renders under the seam and does nothing under a finger would pass every other assertion here.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class QuietCopyTest {

    private val analysis = RenderFixtures.sampleAnalysis
    private val rooms = RenderFixtures.sampleRooms
    private val north = RenderFixtures.sampleNorth

    /**
     * Every sentence that left the page on 19 Sep 2026, with the screen it left. A fragment each,
     * long enough to be unique and short enough to survive a reword of the rest of the sentence.
     */
    private val movedOffTheReport = listOf(
        "Worst first",
        "Both readings, no winner",
        "Neither passed nor failed",
        "Not right? Change it",
        "this moves your score most",
    )

    private fun SemanticsNodeInteractionsProvider.has(fragment: String): Boolean =
        onAllNodesWithText(fragment, substring = true, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty()

    // ── half one: the page is quieter ──────────────────────────────────────────────────────────

    @Test
    fun the_report_no_longer_prints_its_standing_instructions() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north)
            }
        }
        movedOffTheReport.forEach { fragment ->
            assertFalse(
                "\"$fragment\" is printed on the report again. It belongs behind the i on its own " +
                    "heading — see QuietCopyTest's own note for why putting it back is a regression.",
                has(fragment),
            )
        }
    }

    @Test
    fun the_disputes_and_the_unchecked_list_start_folded() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north)
            }
        }
        // The headings are on the page, with their counts. Their contents are not.
        assertTrue("the disputes heading must stay visible", has("Where the schools disagree"))
        assertFalse(
            "a dispute's reading is printed with the section folded — the fold is doing nothing",
            has("the worshipper faces East"),
        )
    }

    // ── half two: and not one word was deleted ─────────────────────────────────────────────────

    @Test
    fun every_sentence_that_left_the_page_is_still_in_the_report() = runComposeUiTest {
        setContent {
            VastuTheme {
                VastuRevealAll {
                    ReportContent(
                        analysis = analysis, intent = Intent.BUILDING,
                        rooms = rooms, north = north, expandAll = true,
                    )
                }
            }
        }
        movedOffTheReport.forEach { fragment ->
            assertTrue(
                "\"$fragment\" has been DELETED from the report, not moved behind the i. " +
                    "CLAUDE.md §2h: no change may leave a reader with less than the report told " +
                    "them before. Put the sentence back as an `info` on its heading.",
                has(fragment),
            )
        }
    }

    @Test
    fun a_folded_list_still_holds_every_word_it_held_before() = runComposeUiTest {
        setContent {
            VastuTheme {
                VastuRevealAll {
                    ReportContent(analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north)
                }
            }
        }
        assertTrue("both sides of a dispute must survive the fold", has("the worshipper faces East"))
        assertTrue("and the modern reading with it", has("facing West"))
    }

    // ── and a finger, not only the seam, opens one ─────────────────────────────────────────────

    @Test
    fun tapping_the_heading_opens_its_note() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north)
            }
        }
        assertFalse("the note must start shut", has("Worst first"))
        // ⚠ SCROLL TO IT FIRST, and this is not a formality — it is what the first run of this
        // test proved. A tap is injected at the node's centre in the window's own coordinates, so
        // a heading sitting below the fold gets a tap delivered to empty space: the click reports
        // no error, the note never opens, and the failure reads exactly like a broken control.
        // Both of these headings are most of a page down a report. ReportListScrollTest already
        // does the same thing for the same reason.
        onNodeWithTag("report.rooms.header").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(
            "tapping the rooms heading did not open its note — the i is decoration, and every " +
                "sentence moved behind one on this screen is unreachable on a real phone",
            has("Worst first"),
        )
    }

    @Test
    fun tapping_a_folded_heading_opens_the_list() = runComposeUiTest {
        setContent {
            VastuTheme {
                ReportContent(analysis = analysis, intent = Intent.BUILDING, rooms = rooms, north = north)
            }
        }
        onNodeWithTag("report.disputes.header").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(
            "tapping the disputes heading did not unfold the list",
            has("the worshipper faces East"),
        )
    }

    // ── the sweep: the same change, on the other screens that carried the same shape ───────────

    @Test
    fun the_extra_questions_screen_keeps_its_preamble_behind_the_i() = runComposeUiTest {
        setContent {
            VastuTheme {
                MoreDetailsContent(
                    answers = SiteAnswers(), onAnswer = { _, _ -> }, onDecline = {},
                    onDone = {}, onBack = {},
                )
            }
        }
        assertFalse("the preamble is printed again", has("we say so, we don't guess"))
        onNodeWithTag("details.why").performScrollTo().performClick()
        assertTrue("and it must still be reachable", has("we say so, we don't guess"))
    }

    /**
     * ⭐ THE FLAT NOTE'S SECOND HALF, specifically. It reads as *"these are the building's, so why
     * are you asking me"* without it, and it is the half that is easiest to lose when two
     * paragraphs are folded into one note.
     */
    @Test
    fun a_flat_still_gets_both_halves_of_its_note() = runComposeUiTest {
        setContent {
            VastuTheme {
                VastuRevealAll {
                    MoreDetailsContent(
                        answers = SiteAnswers(), onAnswer = { _, _ -> }, onDecline = {},
                        onDone = {}, onBack = {}, isFlat = true,
                    )
                }
            }
        }
        assertTrue("the flat note's first half", has("belong to the whole building"))
        assertTrue(
            "the flat note's SECOND half — without it the first reads as a reason not to answer",
            has("it is worth answering what you know"),
        )
    }
}
