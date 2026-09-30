package com.vastufirst.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import com.vastufirst.app.render.PlanSheet
import com.vastufirst.app.ui.common.ROOM_CODE_TAG
import com.vastufirst.app.ui.common.ROOM_MARKER_CHOICE_TAG
import com.vastufirst.app.ui.common.RoomMarker
import com.vastufirst.app.ui.common.RoomMarkerChoice
import com.vastufirst.app.ui.common.centreOrNull
import com.vastufirst.app.ui.common.code
import com.vastufirst.app.ui.common.directionWords
import com.vastufirst.app.ui.common.planFit
import com.vastufirst.app.ui.common.short
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
 * ⭐⭐ THE DIRECTION TRIAL, DONE WITH A FINGER — as the owner redrew it on 30 Sep 2026: *"it should not
 * show any short or long form direction until tapped on a room bellow in the list.. and it should only
 * show the short form on the floor plan and full direction name on the card below only .. and try to
 * improve the placement of direction short form to be in center of the room and 20% smaller"*.
 *
 * So: at rest the plan carries no direction; a tapped room — by its card, or on the plan itself, one
 * selection under one rule — shows its short form on the plan, the engine's own zone, where MOST of the
 * room is; its full name is in its card and nowhere on the plan. On both screens that draw the plan.
 *
 * Every test here performs a real tap. `startSelected` writes a selection without ever entering the
 * tap handler, and a trial proven only through it would be a trial nobody had ever tapped. The tests
 * of the new design were pushed ALONE first (9a412ed) and failed on the app as it was, each for the
 * reason its name gives; only then was the app changed.
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

    /** The engine's reading of the sample plan, the way the app works it out — North marked at 0. */
    private val analysis: com.vastufirst.shared.Analysis = run {
        val grid = toGridRooms(clean.rooms, clean.cols, clean.rows)
        val plan = com.vastufirst.app.ui.newplan.buildEnginePlan(
            rooms = grid,
            door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(grid),
            intent = Intent.BUILDING,
            propertyType = com.vastufirst.shared.PropertyType.FLAT,
            north = 0,
            planId = "direction-trial-test",
        )!!
        com.vastufirst.engine.VastuEngine().analyze(plan)
    }

    /** The rows' readings worked out the way the app works them out. */
    private val readings: Map<String, RoomReading> = roomReadings(analysis)

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

    /** A room's card in either screen's list: the one control whose tap says it acts on "this room". */
    private val isRoomCard = SemanticsMatcher("a room's card") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label?.contains("this room") == true
    }

    @Test
    fun `the build he installs runs the trial`() {
        val choice = shippedChoice()
        assertTrue("the trial must be ON in the shipped data", choice.offered)
        assertEquals(RoomMarker.DIRECTION, choice.current)
    }

    @Test
    fun `at rest the plan shows no direction at all, and a card tapped off again leaves it bare`() = runComposeUiTest {
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
        assertEquals("at rest no room may carry a direction on the plan, short or long", emptyList<String>(), labelsOnPlan())

        val card = onNode(isRoomCard and hasText(clean.rooms[uniqueRoomIndex()].label, substring = true))
        card.performScrollTo().performClick()
        assertEquals("a tapped card puts exactly one direction on the plan", 1, labelsOnPlan().size)
        card.performScrollTo().performClick()
        assertEquals("the same card tapped again must leave the plan bare", emptyList<String>(), labelsOnPlan())
    }

    @Test
    fun `a tapped card puts only its room's short form on the plan, and the full name only in the card`() = runComposeUiTest {
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
        val reading = readings.getValue(scanRoomId(i))
        val card = onNode(isRoomCard and hasText(clean.rooms[i].label, substring = true))
        card.performScrollTo().performClick()

        assertEquals(
            "the plan must carry the tapped room's short form and nothing else",
            listOf(reading.zone!!.code()),
            labelsOnPlan(),
        )
        // The full name is in the room's own card…
        card.assert(hasText(reading.direction, substring = true))
        // …and never on the plan.
        assertTrue("the full name must not be on the plan", labelsOnPlan().none { it.contains(reading.direction) })
    }

    /** Kept on purpose: one selection, one rule — a tap on the plan shows what a tap on the card shows. */
    @Test
    fun `a tap on the room in the plan itself shows the same short form its card would`() = runComposeUiTest {
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
        val picture = onNode(hasContentDescription("Your scanned plan", substring = true))
        val size = picture.fetchSemanticsNode().size
        val fit = planFit(size.width.toFloat(), size.height.toFloat(), 1400, 990, zoom = 1f, pan = Offset.Zero)
        picture.performTouchInput { click(Offset(fit.ox + cx * fit.w, fit.oy + cy * fit.h)) }

        assertEquals(
            "a tap on the plan must show the same one short form a tap on the card shows",
            listOf(readings.getValue(scanRoomId(i)).zone!!.code()),
            labelsOnPlan(),
        )
    }

    @Test
    fun `a crossing room shows where most of it is on the plan, and what it crosses only in its card`() = runComposeUiTest {
        phoneSized()
        val crossing = analysis.roomResults.filter { it.zone != it.mainZone }
        assertTrue("the sample plan must have a room crossing into another zone, or this proves nothing", crossing.isNotEmpty())
        for (r in crossing) {
            assertTrue(
                "the finding for ${r.roomId} must still name the zone it crosses into",
                analysis.defects.any { it.roomId == r.roomId && it.zone == r.zone },
            )
        }
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(image = image, rooms = clean.rooms, readings = readings, roomMarker = RoomMarker.DIRECTION)
            }
        }
        val labels = clean.rooms.map { it.label }
        val index = clean.rooms.indices.firstOrNull { i ->
            val l = labels[i]
            val r = analysis.roomResults.firstOrNull { it.roomId == scanRoomId(i) }
            r != null && r.zone != r.mainZone && l.isNotBlank() &&
                labels.count { it == l } == 1 && labels.none { it != l && it.contains(l) }
        }
        assertTrue("a crossing room with a caption of its own is needed to tap", index != null)
        val r = analysis.roomResults.first { it.roomId == scanRoomId(index!!) }
        val card = onNode(isRoomCard and hasText(labels[index!!], substring = true))
        card.performScrollTo().performClick()

        assertEquals("the plan must show where most of the room is, short", listOf(r.mainZone.code()), labelsOnPlan())
        val words = r.mainZone.short().replaceFirstChar { it.uppercase() } + " · crosses the " + r.zone.short()
        card.assert(hasText(words, substring = true))
    }

    /**
     * The short form is for the eye. A screen reader touching the label hears its card's words — the
     * crossing's middle dot said as words — never two letters to decode.
     */
    @Test
    fun `a screen reader touching the label hears the room's direction in words, not letters`() = runComposeUiTest {
        phoneSized()
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(image = image, rooms = clean.rooms, readings = readings, roomMarker = RoomMarker.DIRECTION)
            }
        }
        val crossingIndex = clean.rooms.indices.first { i ->
            val r = readings[scanRoomId(i)]
            val l = clean.rooms[i].label
            r != null && r.direction.contains(" · ") && l.isNotBlank() &&
                clean.rooms.count { it.label == l } == 1 && clean.rooms.none { it.label != l && it.label.contains(l) }
        }
        val words = readings.getValue(scanRoomId(crossingIndex)).direction
        onNode(isRoomCard and hasText(clean.rooms[crossingIndex].label, substring = true)).performScrollTo().performClick()

        val label = onAllNodes(isPlanLabel).fetchSemanticsNodes().single()
        assertEquals(
            "the label must be described in its card's words, the dot said as words",
            listOf(words.replace(" · ", ", and ")),
            label.config.getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    @Test
    fun `flipping the chooser to boxes takes the direction off the plan and brings the box way back`() = runComposeUiTest {
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
        onNode(isRoomCard and hasText(clean.rooms[i].label, substring = true)).performScrollTo().performClick()
        assertEquals("the trial shows the tapped room's short form", 1, labelsOnPlan().size)

        onNodeWithText("Boxes").performScrollTo().performClick()

        assertEquals("the box way writes no direction on the plan", emptyList<String>(), labelsOnPlan())
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
        assertEquals("the box way writes no direction on the plan", emptyList<String>(), labelsOnPlan())
        val i = uniqueRoomIndex()
        onNode(isRoomCard and hasText(clean.rooms[i].label, substring = true)).performScrollTo().performClick()
        assertEquals("…not even for a tapped room", emptyList<String>(), labelsOnPlan())
        onNode(hasContentDescription("showing roughly where", substring = true)).assertExists()
    }

    /** The report, drawn on the repository's one real plan picture — see PlanSheet. */
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

    @Composable
    private fun SheetReportContent(s: SheetReport, marker: RoomMarker, onMarkerChange: ((RoomMarker) -> Unit)?) {
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
            roomMarker = marker,
            onRoomMarkerChange = onMarkerChange,
        )
    }

    /** ⭐ The sweep: the report draws the same photograph through the same component. */
    @Test
    fun `on the report, the photograph is bare at rest and a tapped card puts only its short form there`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val s = SheetReport()
        setContent {
            VastuTheme {
                var marker by remember { mutableStateOf(choice.current) }
                SheetReportContent(s, marker) { marker = it }
            }
        }
        assertEquals("at rest the report's photograph may carry no direction", emptyList<String>(), labelsOnPlan())

        val kitchen = s.resultNamed("KITCHEN")
        val card = onNode(isRoomCard and hasText("KITCHEN", substring = true))
        card.performScrollTo().performClick()
        assertEquals(
            "the photograph must carry the tapped room's short form and nothing else",
            listOf(kitchen.mainZone.code()),
            labelsOnPlan(),
        )
        card.assert(hasText(kitchen.directionWords(), substring = true))
    }

    @Test
    fun `on the report, a tap on the room in the photograph shows the same short form`() = runComposeUiTest {
        phoneSized()
        val choice = shippedChoice()
        val s = SheetReport()
        setContent { VastuTheme { SheetReportContent(s, choice.current, null) } }
        val kitchen = s.planRooms.first { it.name == "KITCHEN" }
        // ⚠ Scrolled into view first: a touch below the fold lands nowhere and raises no error.
        val picture = onNode(hasContentDescription("Your scanned plan", substring = true))
        picture.performScrollTo()
        val size = picture.fetchSemanticsNode().size
        val fit = planFit(size.width.toFloat(), size.height.toFloat(), 1000, 1400, zoom = 1f, pan = Offset.Zero)
        val (cx, cy) = kitchen.centreOrNull()!!
        picture.performTouchInput { click(Offset(fit.ox + cx * fit.w, fit.oy + cy * fit.h)) }

        assertEquals(
            "a tap on the photograph must show the same one short form",
            listOf(s.resultNamed("KITCHEN").mainZone.code()),
            labelsOnPlan(),
        )
    }

    /** The switch, proven on the report too: off is the box way, no chooser, no direction anywhere on it. */
    @Test
    fun `on the report with the switch off the photograph is the box way and offers no chooser`() = runComposeUiTest {
        phoneSized()
        val off = RoomMarkerChoice(trialOn = false)
        val s = SheetReport()
        setContent {
            VastuTheme {
                SheetReportContent(s, off.current, if (off.offered) ({ m: RoomMarker -> off.current = m }) else null)
            }
        }
        onAllNodesWithTag(ROOM_MARKER_CHOICE_TAG).assertCountEquals(0)
        assertEquals("the box way writes no direction on the photograph", emptyList<String>(), labelsOnPlan())
        onNode(isRoomCard and hasText("KITCHEN", substring = true)).performScrollTo().performClick()
        assertEquals("…not even for a tapped room", emptyList<String>(), labelsOnPlan())
        onNode(hasContentDescription("showing roughly where KITCHEN was read", substring = true)).assertExists()
    }

    /** The one tag the label carries, so a golden or a test elsewhere can find it by name. */
    @Test
    fun `the one label on the plan is the one the tag names`() = runComposeUiTest {
        phoneSized()
        val image = photo()
        setContent {
            VastuTheme {
                ScanReviewContent(image = image, rooms = clean.rooms, readings = readings, roomMarker = RoomMarker.DIRECTION)
            }
        }
        onNode(isRoomCard and hasText(clean.rooms[uniqueRoomIndex()].label, substring = true)).performScrollTo().performClick()
        onAllNodesWithTag(ROOM_CODE_TAG).assertCountEquals(1)
    }
}
