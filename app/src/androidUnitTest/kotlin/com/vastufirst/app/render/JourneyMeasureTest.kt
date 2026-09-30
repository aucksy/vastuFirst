package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.common.RoomMarker
import com.vastufirst.app.ui.details.MoreDetailsContent
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.grid.GuidedGridContent
import com.vastufirst.app.ui.home.DeleteHomeDialogContent
import com.vastufirst.app.ui.home.HomeContent
import com.vastufirst.app.ui.home.RenameDialogContent
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.legal.PrivacyScreen
import com.vastufirst.app.ui.marknorth.MarkNorthContent
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.newplan.frontDoorFromEntrance
import com.vastufirst.app.ui.newplan.frontDoorRead
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.scan.ScanConsentScreen
import com.vastufirst.app.ui.scan.ScanDoorContent
import com.vastufirst.app.ui.scan.ScanReviewContent
import com.vastufirst.app.ui.scan.ScanScreen
import com.vastufirst.app.ui.scan.ScanUiState
import com.vastufirst.app.ui.scan.doorMarkerOnPage
import com.vastufirst.app.ui.scan.doorOnWall
import com.vastufirst.app.ui.scan.gridForOutcome
import com.vastufirst.app.ui.scan.planRoomsOf
import com.vastufirst.app.ui.scan.roomReadings
import com.vastufirst.app.ui.scan.scannedRooms
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.app.ui.settings.SettingsContent
import com.vastufirst.app.ui.welcome.WelcomeContent
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.RefusalReason
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanNotes
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐ Every journey a first-time reader can take, counted screen by screen — see [measureJourneys] for
 * what is counted and how. The journeys are read off the navigation graph (VastuNav.kt), not off a
 * list somebody remembered.
 *
 * ⚠ ONE PLAN PER JOURNEY. A scan journey is the same home on every screen it passes through, so the
 * words on the report are the words that home's report really prints:
 *  · a plan that NAMES its entrance — the owner's own sheet (`plan-020`, it prints FOYER), with a flat
 *    beige stand-in for the photograph, because his picture is not in this repository;
 *  · a plan that names NO entrance — the synthetic sample sheet `plan-01`, whose picture IS in the
 *    repository, so its strip shows a real-looking plan. It is a SAMPLE, not anybody's home.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class JourneyMeasureTest {

    @Test
    fun whatEachJourneyCosts() {
        measureJourneys("now", JourneyFixtures.journeys())
    }
}

/** A read home, carried through every screen of a scan journey. */
internal class ReadHome(
    val outcome: ScanOutcome.Placed,
    val photo: ImageBitmap,
    propertyType: PropertyType,
    /** The door when the plan named none and the reader tapped a wall — see the no-entrance journey. */
    tappedDoor: DoorSide? = null,
) {
    val grid: List<GridRoom> = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
    val readDoor: GridDoor? = frontDoorFromEntrance(grid)
    val door: GridDoor? = readDoor ?: tappedDoor?.let { doorOnWall(it, outcome.rooms) }
    val doorFromCaption: String? = frontDoorRead(grid)?.fromCaption
    val analysis: Analysis = VastuEngine().analyze(
        buildEnginePlan(
            rooms = grid,
            door = door,
            intent = Intent.BUYING,
            propertyType = propertyType,
            north = 0,
            planId = "journey",
        )!!,
    )
    /** What the check screen and the dial see BEFORE a door is tapped — the plan as it was read. */
    val analysisAsRead: Analysis = VastuEngine().analyze(
        buildEnginePlan(
            rooms = grid,
            door = readDoor,
            intent = Intent.BUYING,
            propertyType = propertyType,
            north = 0,
            planId = "journey",
        )!!,
    )
}

internal object JourneyFixtures {

    private fun beige(w: Int, h: Int): ImageBitmap =
        android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    /** The owner's own sheet: it prints FOYER, so its front door is read, not asked for. */
    val entrance: ReadHome by lazy {
        ReadHome(
            outcome = ScanMapper.map(
                RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
                imageAspect = 1399.0 / 1389.0,
            ) as ScanOutcome.Placed,
            photo = beige(1399, 1389),
            propertyType = PropertyType.FLAT,
        ).also { check(it.readDoor != null) { "plan-020 stopped naming its entrance; this journey would be the wrong one" } }
    }

    /** The synthetic SAMPLE sheet: no entrance printed, so the door screen is part of its journey. */
    val noEntrance: ReadHome by lazy {
        ReadHome(
            outcome = PlanSheet.outcome(),
            photo = PlanSheet.bitmap().asImageBitmap(),
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            tappedDoor = DoorSide.S,
        ).also { check(it.readDoor == null) { "plan-01 now names an entrance; this journey would be the wrong one" } }
    }

    /** A plan the reader read but could not place — the grid fallback. */
    private val unplaced: ScanOutcome by lazy { ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply) }

    private val sampleRooms get() = RenderFixtures.sampleRooms

    // --- the screens, each one a pure render of its real content -----------------------------------

    private val welcome = JourneyStep("Welcome", 2, "pick why you are here · Continue") {
        WelcomeContent(intent = null, onIntentChange = {}, onContinue = {})
    }

    private fun addHome(taps: Int, how: String) = JourneyStep("Add a home", taps, how) {
        AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {})
    }

    private val consent = JourneyStep("Your plan leaves this phone (first scan only)", 1, "I agree") {
        ScanConsentScreen(onAgree = {}, onDrawInstead = {}, onBack = {})
    }

    private fun scan(state: ScanUiState, screen: String, taps: Int, how: String) = JourneyStep(screen, taps, how) {
        ScanScreen(
            state = state,
            onPickImage = {}, onTakePhoto = {}, onRetry = {},
            onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
        )
    }

    private val upload = scan(ScanUiState.Idle, "Upload your plan", 2, "Choose a PDF or picture · pick the file")
    private val reading = scan(ScanUiState.Reading, "Reading your plan", 0, "wait")

    private fun result(home: ReadHome) = scan(
        ScanUiState.Done(home.outcome), "We read ${home.outcome.rooms.size} rooms", 1, "Next — which way is North?",
    )

    private fun northOnPhoto(home: ReadHome) = JourneyStep("Which way is North? (on the photo)", 2, "turn the dial · Yes") {
        MarkNorthContent(
            rooms = home.grid, north = 0, analysis = home.analysisAsRead,
            onNorthChange = {}, onRead = {}, onBack = {},
            cols = home.outcome.cols, rows = home.outcome.rows,
            planImage = home.photo, nextIsCheck = true,
        )
    }

    private fun checkStep(home: ReadHome) = JourneyStep(
        "Check what we read" + if (home.readDoor == null) " (no entrance)" else "",
        1,
        if (home.readDoor == null) "These are my rooms — set the front door" else "These are my rooms — read my home",
    ) {
        ScanReviewContent(
            image = home.photo,
            rooms = home.outcome.rooms,
            door = home.readDoor,
            doorFromCaption = home.doorFromCaption,
            readings = roomReadings(home.analysisAsRead),
            roomMarker = RoomMarker.DIRECTION,
            onRoomMarkerChange = {},
        )
    }

    private fun doorAsked(home: ReadHome) = JourneyStep("Where is your front door?", 2, "tap your door's wall · Read my home") {
        ScanDoorContent(image = home.photo, rooms = home.outcome.rooms, door = null)
    }

    private fun report(home: ReadHome, unlocked: Boolean = false) = JourneyStep(
        if (unlocked) "Full report (paid)" else "Your report" + if (home === noEntrance) " (sample sheet)" else "",
        0, "",
    ) {
        ReportContent(
            analysis = home.analysis,
            intent = Intent.BUYING,
            unlocked = unlocked,
            rooms = home.grid,
            north = 0,
            cols = home.outcome.cols,
            rows = home.outcome.rows,
            planImage = home.photo,
            planRooms = planRoomsOf(home.outcome.rooms),
            doorAtPage = home.door?.let { doorMarkerOnPage(it, home.outcome.rooms) },
            roomMarker = RoomMarker.DIRECTION,
            onRoomMarkerChange = {},
        )
    }

    private fun reportStart(home: ReadHome, taps: Int, how: String): JourneyStep {
        val r = report(home)
        return JourneyStep(r.screen, taps, how, r.content)
    }

    private val drawnReport = JourneyStep("Your report (drawn home)", 0, "") {
        ReportContent(
            analysis = RenderFixtures.sampleAnalysis, intent = RenderFixtures.sampleIntent, unlocked = false,
            rooms = sampleRooms, north = RenderFixtures.sampleNorth,
        )
    }

    private val northDrawn = JourneyStep("Which way is North?", 2, "turn the dial · Yes — read my home") {
        MarkNorthContent(
            rooms = sampleRooms, north = RenderFixtures.sampleNorth, analysis = RenderFixtures.sampleAnalysis,
            onNorthChange = {}, onRead = {}, onBack = {},
        )
    }

    private val gridEmpty = JourneyStep(
        "Draw your rooms (empty grid)",
        2 * RenderFixtures.sampleRooms.size + 1,
        "pick a room and tap the grid, once per room (${RenderFixtures.sampleRooms.size} rooms) · Next",
    ) {
        GuidedGridContent(rooms = emptyList(), door = null, onRoomsChange = {}, onDoorChange = {}, onNext = {})
    }

    private val gridDoor = JourneyStep("Mark the front door (grid)", 2, "tap your door's wall · Next — mark North") {
        GuidedGridContent(
            rooms = sampleRooms, door = null, onRoomsChange = {}, onDoorChange = {}, onNext = {},
            startInDoorMode = true, returnsToReport = false,
        )
    }

    private val gridUnplaced: JourneyStep by lazy {
        val outcome = unplaced
        val (cols, rows) = gridForOutcome(outcome)
        val parked = toGridRooms(outcome.scannedRooms(), cols, rows)
        JourneyStep(
            "Place the rooms we found (grid)", parked.size + 1,
            "drag each room to its place (${parked.size} rooms) · Next",
        ) {
            GuidedGridContent(
                rooms = parked, door = null, onRoomsChange = {}, onDoorChange = {}, onNext = {},
                cols = cols, rows = rows, roomsUnplaced = true,
            )
        }
    }

    private val home = JourneyStep("Your plans", 1, "tap a home") {
        HomeContent(
            plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
            onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
        )
    }

    private fun homeThen(taps: Int, how: String) = JourneyStep(home.screen, taps, how, home.content)

    private val openedReport = JourneyStep("Your report (opened from the list)", 0, "") {
        ReportContent(
            analysis = RenderFixtures.sampleAnalysis, intent = RenderFixtures.sampleIntent, unlocked = false,
            rooms = sampleRooms, north = RenderFixtures.sampleNorth,
        )
    }

    fun journeys(): List<Journey> {
        val e = entrance
        val n = noEntrance
        return listOf(
            Journey(
                "Scan, plan names its entrance",
                listOf(welcome, addHome(1, "Upload a plan"), consent, upload, reading, result(e), northOnPhoto(e), checkStep(e), report(e)),
            ),
            Journey(
                "Scan, plan names no entrance",
                listOf(welcome, addHome(1, "Upload a plan"), consent, upload, reading, result(n), northOnPhoto(n), checkStep(n), doorAsked(n), report(n)),
            ),
            Journey(
                "Scan that cannot be placed",
                listOf(
                    welcome, addHome(1, "Upload a plan"), consent, upload, reading,
                    scan(ScanUiState.Done(unplaced), "We found ${(unplaced as ScanOutcome.Assisted).rooms.size} rooms", 1, "Place them on the grid"),
                    gridUnplaced, gridDoor, northDrawn, drawnReport,
                ),
            ),
            Journey(
                "Scan that is refused",
                listOf(
                    welcome, addHome(1, "Upload a plan"), consent, upload, reading,
                    scan(
                        ScanUiState.Done(ScanOutcome.Refused(RefusalReason.NOT_2D, ScanNotes(0.0, 0.0, 0.0))),
                        "This looks like a tilted 3D view", 1, "Draw it on a grid instead",
                    ),
                    gridEmpty, gridDoor, northDrawn, drawnReport,
                ),
            ),
            Journey("Draw by hand", listOf(welcome, addHome(1, "Draw it on a grid"), gridEmpty, gridDoor, northDrawn, drawnReport)),
            Journey("Try the sample", listOf(welcome, addHome(1, "Try a sample plan"), northDrawn, drawnReport)),
            Journey(
                "Unlock the full report",
                listOf(
                    reportStart(e, 1, "Unlock the full report"),
                    JourneyStep("Unlock the full report", 1, "Unlock on this device — free") { com.vastufirst.app.ui.unlock.UnlockContent(state = BillingState()) },
                    report(e, unlocked = true),
                ),
            ),
            Journey(
                "Change North",
                listOf(
                    reportStart(e, 1, "Change North"),
                    JourneyStep("Which way is North? (from the report)", 2, "turn the dial · Yes — read my home") {
                        MarkNorthContent(
                            rooms = e.grid, north = 0, analysis = e.analysis,
                            onNorthChange = {}, onRead = {}, onBack = {},
                            cols = e.outcome.cols, rows = e.outcome.rows,
                            // What ships today: opened from the report, the dial is NOT given the photo.
                            planImage = null,
                        )
                    },
                    report(e),
                ),
            ),
            Journey(
                "Change front door",
                listOf(
                    reportStart(e, 1, "Change front door"),
                    JourneyStep("Where is your front door? (from the report)", 2, "drag the E · Done — back to my report") {
                        ScanDoorContent(image = e.photo, rooms = e.outcome.rooms, door = e.door, returnsToReport = true)
                    },
                    report(e),
                ),
            ),
            Journey(
                "Answer 4 more questions",
                listOf(
                    reportStart(e, 1, "Tell us about these 4"),
                    JourneyStep("A few more things", 5, "one answer per question · Done") {
                        MoreDetailsContent(answers = SiteAnswers(), onAnswer = { _, _ -> }, onDecline = {}, onDone = {}, onBack = {})
                    },
                    report(e),
                ),
            ),
            Journey("Open a saved home", listOf(homeThen(1, "tap a home"), openedReport)),
            Journey(
                "Finish a home after closing the app",
                listOf(
                    JourneyStep("Your plans (an unfinished home)", 1, "Carry on") {
                        HomeContent(
                            plans = RenderFixtures.savedPlans, onAddHome = {}, onOpenPlan = {}, onSettings = {},
                            onRename = { _, _ -> }, now = RenderFixtures.FIXED_NOW,
                            drafts = RenderFixtures.savedDrafts, onOpenDraft = { _, _ -> }, onDiscardDraft = {},
                        )
                    },
                    JourneyStep("Which way is North? (resumed, on the photo)", 2, "turn the dial · Yes — read my home") {
                        MarkNorthContent(
                            rooms = e.grid, north = 0, analysis = e.analysis,
                            onNorthChange = {}, onRead = {}, onBack = {},
                            cols = e.outcome.cols, rows = e.outcome.rows, planImage = e.photo,
                        )
                    },
                    report(e),
                ),
            ),
            Journey(
                "Rename a saved home",
                listOf(
                    homeThen(1, "tap the pencil"),
                    JourneyStep("Rename this home", 2, "type the name · Save") {
                        RenameDialogContent(currentName = "Compact 2BHK flat", onCancel = {}, onSave = {})
                    },
                ),
            ),
            Journey(
                "Delete a saved home",
                listOf(
                    homeThen(1, "press and hold the home"),
                    JourneyStep("Delete this home?", 1, "Delete it") {
                        DeleteHomeDialogContent(name = "Compact 2BHK flat", paid = false, onCancel = {}, onDelete = {})
                    },
                ),
            ),
            Journey(
                "Settings, privacy and sources",
                listOf(
                    homeThen(1, "the gear"),
                    JourneyStep("Settings", 1, "Privacy") { SettingsContent(onLegal = {}, onBack = {}, onDeleteAll = {}) },
                    JourneyStep("Privacy", 0, "") { PrivacyScreen(onBack = {}) },
                    JourneyStep("Honesty & sources", 0, "") { LegalScreen(onBack = {}) },
                ),
            ),
        )
    }
}
