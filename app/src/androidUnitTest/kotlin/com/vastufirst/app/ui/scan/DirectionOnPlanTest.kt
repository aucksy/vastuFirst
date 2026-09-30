package com.vastufirst.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.PlanSheet
import com.vastufirst.app.ui.common.ROOM_CODE_TAG
import com.vastufirst.app.ui.common.centreOrNull
import com.vastufirst.app.ui.common.code
import com.vastufirst.app.ui.common.directionWords
import com.vastufirst.app.ui.common.planFit
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE DIRECTION ON THE PLAN, DONE WITH A FINGER — as the owner redrew it on 30 Sep 2026: *"it should
 * not show any short or long form direction until tapped on a room bellow in the list.. and it should
 * only show the short form on the floor plan and full direction name on the card below only .. and try
 * to improve the placement of direction short form to be in center of the room and 20% smaller"*.
 *
 * So: at rest the plan carries no direction; a tapped room — by its card, or on the plan itself, one
 * selection under one rule — shows its short form on the plan, the engine's own zone, where MOST of the
 * room is; its full name is in its card and nowhere on the plan.
 *
 * ⚠ IT IS THE ONLY WAY SINCE v0.36.0 (owner, 30 Sep 2026: *"END THE DIRECTION TRIAL. Keep directions.
 * Remove the Boxes choice and the switch."*). The tests of the switch and of the chooser went with them;
 * the first test below pins that nothing is left to choose.
 *
 * ⚠ ON THE REPORT ONLY, SINCE 30 SEP 2026. These tests were written for two screens — "Check what we
 * read" and the report — and pushed ALONE first (9a412ed), failing on the app as it was. The checking
 * screen is gone (owner: *"I specially think the 'Check what we read' screen is not needed"*), so
 * every one of them now holds the report, which draws the same picture through the same component.
 *
 * Every test here performs a real tap.
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class DirectionOnPlanTest {

    /** The same string the render matrix calls its baseline — a real phone's window. */
    private fun phoneSized() = org.robolectric.RuntimeEnvironment.setQualifiers("+w412dp-h915dp-port-xhdpi")

    /**
     * Every direction label on the plan, whatever its kind — each kind the plan has ever drawn carries
     * a tag under "plan.room." — as the words each one prints, sorted.
     */
    private fun ComposeUiTest.labelsOnPlan(): List<String> =
        onAllNodes(isPlanLabel).fetchSemanticsNodes()
            .map { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
            .sorted()

    private val isPlanLabel = SemanticsMatcher("a direction label on the plan") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("plan.room.") == true
    }

    /** A room's card in the report's list: the one control whose tap says it acts on "this room". */
    private val isRoomCard = SemanticsMatcher("a room's card") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label?.contains("this room") == true
    }

    /** The report, drawn on the repository's one real plan picture — see PlanSheet. A SAMPLE sheet. */
    private class SheetReport {
        val outcome = PlanSheet.outcome()
        val grid = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(grid)
        val analysis = com.vastufirst.engine.VastuEngine().analyze(
            com.vastufirst.app.ui.newplan.buildEnginePlan(
                rooms = grid,
                door = door,
                intent = Intent.BUILDING,
                propertyType = com.vastufirst.shared.PropertyType.FLAT,
                north = 0,
                planId = "direction-trial-report",
            )!!,
        )
        val planRooms = planRoomsOf(outcome.rooms)
        val picture = PlanSheet.bitmap().asImageBitmap()

        fun resultNamed(name: String) = planRooms.first { it.name == name }.id.let { id -> analysis.roomResults.first { it.roomId == id } }
    }

    /** The report exactly as the app draws it for a scanned home: nothing handed in but the home. */
    @Composable
    private fun SheetReportContent(s: SheetReport) {
        ReportContent(
            analysis = s.analysis,
            intent = Intent.BUILDING,
            rooms = s.grid,
            north = 0,
            cols = s.outcome.cols,
            rows = s.outcome.rows,
            planImage = s.picture,
            planRooms = s.planRooms,
            doorAtPage = s.door?.let { doorMarkerOnPage(it, s.outcome.rooms) },
        )
    }

    /**
     * ⭐ THE TRIAL IS OVER (owner, 30 Sep 2026: *"END THE DIRECTION TRIAL. Keep directions. Remove the
     * Boxes choice and the switch."*). The report, drawn with nothing but its own defaults — no switch
     * handed in, no chooser — is the direction way: a screen reader is told a tap gives a direction, a
     * tapped room shows its short form, and there is no "Trial" or "Boxes" anywhere on it.
     *
     * Pushed ALONE first (4c75c12), on v0.35.0, where the report's own default was still the box way.
     */
    @Test
    fun `the report's photograph shows directions on its own, with no choice left to make`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s) } }
        onNode(hasContentDescription("Your scanned plan. Tap a room to hear its direction.", substring = true))
            .assertExists()
        listOf("Trial", "Boxes").forEach { gone ->
            onAllNodes(hasText(gone, substring = true)).assertCountEquals(0)
        }
        onNode(isRoomCard and hasText("KITCHEN", substring = true)).performScrollTo().performClick()
        assertEquals(
            "a tapped room must show its short form on the photograph",
            listOf(s.resultNamed("KITCHEN").mainZone.code()),
            labelsOnPlan(),
        )
    }

    @Test
    fun `at rest the photograph is bare, a tapped card puts only its short form there, and tapped again leaves it bare`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s) } }
        assertEquals("at rest the report's photograph may carry no direction", emptyList<String>(), labelsOnPlan())

        val kitchen = s.resultNamed("KITCHEN")
        val card = onNode(isRoomCard and hasText("KITCHEN", substring = true))
        card.performScrollTo().performClick()
        assertEquals(
            "the photograph must carry the tapped room's short form and nothing else",
            listOf(kitchen.mainZone.code()),
            labelsOnPlan(),
        )
        // The full name is in the room's own card, and never on the plan.
        card.assert(hasText(kitchen.directionWords(), substring = true))
        assertTrue("the full name must not be on the plan", labelsOnPlan().none { it.contains(kitchen.directionWords()) })

        card.performScrollTo().performClick()
        assertEquals("the same card tapped again must leave the plan bare", emptyList<String>(), labelsOnPlan())
    }

    /** Kept on purpose: one selection, one rule — a tap on the plan shows what a tap on the card shows. */
    @Test
    fun `a tap on the room in the photograph shows the same short form`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s) } }
        val kitchen = s.planRooms.first { it.name == "KITCHEN" }
        // ⚠ Scrolled into view first: a touch below the fold lands nowhere and raises no error.
        val picture = onNode(hasContentDescription("Your scanned plan", substring = true))
        picture.performScrollTo()
        val size = picture.fetchSemanticsNode().size
        val fit = planFit(size.width.toFloat(), size.height.toFloat(), 1000, 1400)
        val (cx, cy) = kitchen.centreOrNull()!!
        picture.performTouchInput { click(Offset(fit.ox + cx * fit.w, fit.oy + cy * fit.h)) }

        assertEquals(
            "a tap on the photograph must show the same one short form",
            listOf(s.resultNamed("KITCHEN").mainZone.code()),
            labelsOnPlan(),
        )
    }

    @Test
    fun `a crossing room shows where most of it is on the plan, and what it crosses only in its card`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        val living = s.resultNamed("LIVING ROOM")
        assertTrue("the sample sheet's living room must cross into another zone, or this proves nothing", living.zone != living.mainZone)
        assertTrue(
            "the finding for the living room must still name the zone it crosses into",
            s.analysis.defects.any { it.roomId == living.roomId && it.zone == living.zone },
        )
        setContent { VastuTheme { SheetReportContent(s) } }
        val card = onNode(isRoomCard and hasText("LIVING ROOM", substring = true))
        card.performScrollTo().performClick()

        assertEquals("the plan must show where most of the room is, short", listOf(living.mainZone.code()), labelsOnPlan())
        val words = living.mainZone.short().replaceFirstChar { it.uppercase() } + " · crosses the " + living.zone.short()
        card.assert(hasText(words, substring = true))
    }

    /**
     * The short form is for the eye. A screen reader touching the label hears its card's words — the
     * crossing's middle dot said as words — never two letters to decode.
     */
    @Test
    fun `a screen reader touching the label hears the room's direction in words, not letters`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s) } }
        val words = s.resultNamed("LIVING ROOM").directionWords()
        assertTrue("the living room must be a crossing room, so its words carry the middle dot", words.contains(" · "))
        onNode(isRoomCard and hasText("LIVING ROOM", substring = true)).performScrollTo().performClick()

        val label = onAllNodes(isPlanLabel).fetchSemanticsNodes().single()
        assertEquals(
            "the label must be described in its card's words, the dot said as words",
            listOf(words.replace(" · ", ", and ")),
            label.config.getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    /** The one tag the label carries, so a golden or a test elsewhere can find it by name. */
    @Test
    fun `the one label on the plan is the one the tag names`() = runComposeUiTest {
        phoneSized()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s) } }
        onNode(isRoomCard and hasText("KITCHEN", substring = true)).performScrollTo().performClick()
        onAllNodesWithTag(ROOM_CODE_TAG).assertCountEquals(1)
    }
}
