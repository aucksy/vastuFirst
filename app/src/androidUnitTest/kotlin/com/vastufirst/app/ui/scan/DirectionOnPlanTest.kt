package com.vastufirst.app.ui.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.RenderFixtures
import com.vastufirst.app.ui.common.ROOM_DIRECTION_TAG
import com.vastufirst.app.ui.common.ROOM_MARKER_CHOICE_TAG
import com.vastufirst.app.ui.common.RoomMarker
import com.vastufirst.app.ui.common.RoomMarkerChoice
import com.vastufirst.app.ui.common.centreOrNull
import com.vastufirst.app.ui.common.directionWords
import com.vastufirst.app.ui.common.planFit
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Intent
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScanReaderConfigLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ THE DIRECTION TRIAL, DONE WITH A FINGER — a tapped room shows its direction on the plan, in the
 * very words its own row prints.
 *
 * The owner, 29 Sep 2026: *"when I tap a room (on the plan or in the list) ... show its direction ON
 * the room, on the plan, instead of drawing a box ... The direction on the plan must be the SAME engine
 * words as the row and the report, never a second calculation ... One plan drawing is used by both
 * 'Check what we read' and the report ... keep the two screens consistent."*
 *
 * Every test here performs a real tap. `startSelected` writes a selection without ever entering the
 * tap handler, and a trial proven only through it would be a trial nobody had ever tapped.
 *
 * ⚠ The trial is switched on HERE the way the app switches it on — from the bundled reader config —
 * so the day the data says `false` these tests say so too, instead of passing on a hand-set flag.
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

    /** The switch exactly as the app reads it: the bundled reader config, through the app's holder. */
    private fun shippedChoice() = RoomMarkerChoice(trialOn = ScanReaderConfigLoader.load().config.directionTrial)

    private val clean: ScanOutcome.Placed =
        ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply) as ScanOutcome.Placed

    /** The rows' readings worked out the way the app works them out — North marked at 0. */
    private val readings: Map<String, RoomReading> = run {
        val grid = toGridRooms(clean.rooms, clean.cols, clean.rows)
        val plan = com.vastufirst.app.ui.newplan.buildEnginePlan(
            rooms = grid,
            door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(grid),
            intent = Intent.BUILDING,
            propertyType = com.vastufirst.shared.PropertyType.FLAT,
            north = 0,
            planId = "direction-trial-test",
        )!!
        roomReadings(com.vastufirst.engine.VastuEngine().analyze(plan))
    }

    private fun photo() =
        android.graphics.Bitmap.createBitmap(1400, 990, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    /** A room whose caption is on no other row, so a text match finds exactly one row. */
    private fun uniqueRoomIndex(): Int {
        val labels = clean.rooms.map { it.label }
        return labels.indices.first { i ->
            val l = labels[i]
            l.isNotBlank() && labels.none { it != l && it.contains(l) } && readings[scanRoomId(i)] != null
        }
    }

    @Test
    fun `the build he installs runs the trial`() {
        val choice = shippedChoice()
        assertTrue("the trial must be ON in the shipped data", choice.offered)
        assertEquals(RoomMarker.DIRECTION, choice.current)
    }

    @Test
    fun `tapping a room's row puts that row's own direction on the plan`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val image = photo()
        setContent {
            VastuTheme {
                var marker by remember { mutableStateOf(choice.current) }
                ScanReviewContent(
                    image = image,
                    rooms = clean.rooms,
                    readings = readings,
                    roomMarker = marker,
                    onRoomMarkerChange = { marker = it },
                )
            }
        }
        // Nothing tapped yet: no words on the plan, and the chooser is there to compare with.
        onAllNodesWithTag(ROOM_DIRECTION_TAG).assertCountEquals(0)
        onNodeWithTag(ROOM_MARKER_CHOICE_TAG).assertExists()

        val i = uniqueRoomIndex()
        onNodeWithText(clean.rooms[i].label, substring = true).performScrollTo().performClick()

        // The words on the plan ARE the words on the row — the same engine result, one spelling.
        onNodeWithTag(ROOM_DIRECTION_TAG).assertTextEquals(readings.getValue(scanRoomId(i)).direction)
    }

    @Test
    fun `tapping the room on the plan itself shows the same words`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(image = image, rooms = clean.rooms, readings = readings, roomMarker = choice.current)
            }
        }
        val i = uniqueRoomIndex()
        val (cx, cy) = planRoomsOf(clean.rooms)[i].centreOrNull()!!
        val picture = onNode(hasContentDescription("Tap a room to see which direction it is in", substring = true))
        val size = picture.fetchSemanticsNode().size
        val fit = planFit(size.width.toFloat(), size.height.toFloat(), 1400, 990, zoom = 1f, pan = Offset.Zero)
        // A finger on the room's own dot, exactly where the picture draws it.
        picture.performTouchInput { click(Offset(fit.ox + cx * fit.w, fit.oy + cy * fit.h)) }

        onNodeWithTag(ROOM_DIRECTION_TAG).assertTextEquals(readings.getValue(scanRoomId(i)).direction)
    }

    @Test
    fun `flipping the chooser to the box takes the words off the plan and brings the box way back`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val image = photo()
        setContent {
            VastuTheme {
                var marker by remember { mutableStateOf(choice.current) }
                ScanReviewContent(
                    image = image,
                    rooms = clean.rooms,
                    readings = readings,
                    roomMarker = marker,
                    onRoomMarkerChange = { marker = it },
                )
            }
        }
        val i = uniqueRoomIndex()
        onNodeWithText(clean.rooms[i].label, substring = true).performScrollTo().performClick()
        onNodeWithTag(ROOM_DIRECTION_TAG).assertExists()

        onNodeWithText("Its box").performScrollTo().performClick()

        onAllNodesWithTag(ROOM_DIRECTION_TAG).assertCountEquals(0)
        // The picture now says what the box way has always said about the same room.
        onNode(hasContentDescription("showing roughly where", substring = true)).assertExists()
    }

    @Test
    fun `with the switch off the screen is the box way exactly and offers no chooser`() = runComposeUiTest {
        phoneSized()
        val off = RoomMarkerChoice(trialOn = false)
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(
                    image = image,
                    rooms = clean.rooms,
                    readings = readings,
                    roomMarker = off.current,
                    onRoomMarkerChange = if (off.offered) ({ m: RoomMarker -> off.current = m }) else null,
                )
            }
        }
        onAllNodesWithTag(ROOM_MARKER_CHOICE_TAG).assertCountEquals(0)
        val i = uniqueRoomIndex()
        onNodeWithText(clean.rooms[i].label, substring = true).performScrollTo().performClick()
        onAllNodesWithTag(ROOM_DIRECTION_TAG).assertCountEquals(0)
    }

    /**
     * ⭐ THE SWEEP. The report draws the same photograph through the same component, so a tapped room
     * there must show the same kind of marker — and the words its OWN row prints on the report.
     */
    @Test
    fun `on the report, a tapped room's words on the photograph are its own row's words`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val sheet = android.graphics.Bitmap.createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()
        setContent {
            VastuTheme {
                var marker by remember { mutableStateOf(choice.current) }
                ReportContent(
                    analysis = RenderFixtures.scannedAnalysis,
                    intent = Intent.BUILDING,
                    rooms = RenderFixtures.scannedRooms,
                    north = 0,
                    cols = RenderFixtures.scannedCols,
                    rows = RenderFixtures.scannedRows,
                    planImage = sheet,
                    planRooms = RenderFixtures.scannedPlanRooms,
                    doorAtPage = RenderFixtures.scannedDoorAtPage,
                    roomMarker = marker,
                    onRoomMarkerChange = { marker = it },
                )
            }
        }
        onNodeWithTag(ROOM_MARKER_CHOICE_TAG).assertExists()

        // ⚠ Tapped ON THE PICTURE, which is the end the two screens share. The report's room names
        // also appear in its finding cards above the list, so a text match on a row is not a
        // reliable way to reach one row there; a finger on the room's own dot is.
        // ⚠ Scrolled into view FIRST: a touch below the fold lands nowhere and raises no error.
        val picture = onNode(hasContentDescription("Tap a room to see which direction it is in", substring = true))
        picture.performScrollTo()
        val size = picture.fetchSemanticsNode().size
        val fit = planFit(size.width.toFloat(), size.height.toFloat(), 1399, 1389, zoom = 1f, pan = Offset.Zero)
        val results = RenderFixtures.scannedAnalysis.roomResults.associateBy { it.roomId }
        val door = RenderFixtures.scannedDoorAtPage
        // A room whose dot is well clear of the front-door E, so the finger cannot land on the door.
        val target = RenderFixtures.scannedPlanRooms.first { pr ->
            val c = pr.centreOrNull() ?: return@first false
            val clearOfDoor = door == null ||
                kotlin.math.hypot((c.first - door.first) * fit.w, (c.second - door.second) * fit.h) > 160f
            results[pr.id] != null && clearOfDoor
        }
        val (cx, cy) = target.centreOrNull()!!
        picture.performTouchInput { click(Offset(fit.ox + cx * fit.w, fit.oy + cy * fit.h)) }

        onNodeWithTag(ROOM_DIRECTION_TAG).assertTextEquals(results.getValue(target.id).directionWords())
    }
}
