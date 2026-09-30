package com.vastufirst.app.ui.scan

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.PlanSheet
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE SCAN RESULT, NOW THE SCREEN WHERE A READ HOME IS AGREED (owner, 30 Sep 2026: *"I specially
 * think the 'Check what we read' screen is not needed. We show the list of rooms detected on this
 * screen anyways"*).
 *
 * What these hold, each the reason it exists:
 *  · the rooms are FOLDED SHUT under a heading that says how many — and how many the reader was unsure
 *    of — and open with one tap onto the rows where a room's kind is changed;
 *  · North is asked on this same screen, on the reader's own plan;
 *  · the button names the screen it opens — the report when the plan printed its own entrance, the
 *    front-door screen when it did not — because the checking screen that used to sit in between is
 *    gone;
 *  · every size a plan prints for a room is on its row, including all the parts of a room the sheet
 *    measures in pieces — the one job only "Check what we read" used to do.
 *
 * ⚠ org.junit.Assert: the MESSAGE comes FIRST.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class)
class ScanResultTest {

    private fun phoneSized() = org.robolectric.RuntimeEnvironment.setQualifiers("+w412dp-h915dp-port-xhdpi")

    /** The owner's own sheet: it prints FOYER, carries four unsure rooms and a balcony read in three parts. */
    private val owners: ScanOutcome.Placed =
        ScanMapper.map(RecordedScans.load(RecordedScans.PLAN_020)!!.reply, imageAspect = 1399.0 / 1389.0)
            as ScanOutcome.Placed

    private fun beige() = android.graphics.Bitmap.createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
        .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
        .asImageBitmap()

    private fun northFor(outcome: ScanOutcome.Placed, photo: androidx.compose.ui.graphics.ImageBitmap): NorthOnResult {
        val grid = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(grid)
        val analysis = com.vastufirst.app.ui.newplan.buildEnginePlan(
            rooms = grid, door = door, intent = Intent.BUYING, propertyType = PropertyType.FLAT,
            north = 0, planId = "scan-result-test",
        )?.let { com.vastufirst.engine.VastuEngine().analyze(it) }
        return NorthOnResult(
            rooms = grid, north = 0, analysis = analysis, cols = outcome.cols, rows = outcome.rows,
            planImage = photo, doorKnown = door != null,
        )
    }

    @Test
    fun `a fused room prints every part the plan measured, and a plain one its one size`() {
        val fused = owners.rooms.firstOrNull { it.readInParts.size > 1 }
        assertTrue("the owner's sheet must still read a room in parts, or this proves nothing", fused != null)
        val words = printedSizeWords(fused!!)
        fused.readInParts.forEach { part ->
            assertTrue("\"${shortSize(part)}\" must be on the row, got \"$words\"", words.contains(shortSize(part)))
        }
        assertTrue("the row must say the parts are one space: \"$words\"", words.startsWith("one space: "))
        val plain = owners.rooms.first { it.readInParts.size <= 1 && it.printedSize.isNotBlank() }
        assertEquals("a room with one printed size shows it, trimmed", shortSize(plain.printedSize), printedSizeWords(plain))
    }

    @Test
    fun `the rooms start folded, say how many are unsure, and open onto their rows with one tap`() = runComposeUiTest {
        phoneSized()
        val unsure = owners.rooms.count {
            com.vastufirst.shared.scan.RoomFlag.OVERLAP_TRIMMED in it.flags ||
                com.vastufirst.shared.scan.RoomFlag.LOOSE_LABEL_MATCH in it.flags
        }
        setContent {
            VastuTheme {
                ScanScreen(
                    state = ScanUiState.Done(owners),
                    onPickImage = {}, onTakePhoto = {}, onRetry = {},
                    onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
                    north = northFor(owners, beige()),
                )
            }
        }
        assertEquals(
            "shut, no room row may be on the screen",
            0, onAllNodesWithText("Change").fetchSemanticsNodes().size,
        )
        onNodeWithText("Rooms we read (${owners.rooms.size})", substring = true, ignoreCase = true).assertExists()
        if (unsure > 0) onNodeWithText("$unsure to check", substring = true).assertExists()

        onNodeWithTag("scan.rooms").performScrollTo().performClick()
        assertEquals(
            "open, every room has its row and its way to change the kind",
            owners.rooms.size, onAllNodesWithText("Change").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `North is asked on the reader's own plan, on the result itself`() = runComposeUiTest {
        phoneSized()
        setContent {
            VastuTheme {
                ScanScreen(
                    state = ScanUiState.Done(owners),
                    onPickImage = {}, onTakePhoto = {}, onRetry = {},
                    onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
                    north = northFor(owners, beige()),
                )
            }
        }
        onNodeWithText("Which way is North?").assertExists()
        onNodeWithContentDescription("Floor plan compass", substring = true).assertExists()
    }

    @Test
    fun `a plan that names its entrance goes straight to the report, and one that does not asks for the door`() {
        fun buttonFor(outcome: ScanOutcome.Placed, photo: androidx.compose.ui.graphics.ImageBitmap): String {
            var label = ""
            runComposeUiTest {
                phoneSized()
                setContent {
                    VastuTheme {
                        ScanScreen(
                            state = ScanUiState.Done(outcome),
                            onPickImage = {}, onTakePhoto = {}, onRetry = {},
                            onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
                            north = northFor(outcome, photo),
                        )
                    }
                }
                label = listOf("Yes — read my home", "Read my home", "Yes — next, your front door", "Next — your front door")
                    .first { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
            }
            return label
        }
        assertTrue(
            "the owner's sheet prints FOYER, so its button must open the report",
            buttonFor(owners, beige()).endsWith("read my home"),
        )
        assertTrue(
            "the sample sheet names no entrance, so its button must open the front-door screen",
            buttonFor(PlanSheet.outcome(), PlanSheet.bitmap().asImageBitmap()).endsWith("your front door"),
        )
    }
}
