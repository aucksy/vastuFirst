package com.vastufirst.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.vastufirst.app.ui.common.RoomTypePicker
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.marknorth.NORTH_DIAL_SCREEN_SHARE
import com.vastufirst.app.ui.marknorth.NorthCheck
import com.vastufirst.app.ui.marknorth.NorthControls
import com.vastufirst.app.ui.marknorth.NorthInstruction
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuFoldSection
import com.vastufirst.designsystem.components.VastuInfoLine
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.AssistReason
import com.vastufirst.shared.scan.DropReason
import com.vastufirst.shared.scan.RefusalReason
import com.vastufirst.shared.scan.RoomFlag
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom

/**
 * ⭐ NORTH, SET ON THE SCAN RESULT ITSELF — what a PLACED read needs to draw the dial under "We read N
 * rooms" (owner, 30 Sep 2026: *"decreasing the number of screens … let the user get to the point
 * quickly"*).
 *
 * The scan result and the North dial used to be two screens, and a third ("Check what we read") sat
 * after them listing the same rooms again. Now the result leads with the answer, keeps its room list
 * folded shut under it, and asks the one question left — which way is North — on the reader's own
 * photograph, with the very same [NorthControls] the North screen draws.
 *
 * The defaults draw an empty dial; the app always passes the home the read has just become.
 */
class NorthOnResult(
    /** The read rooms as the engine scores them — the same home the report will show. */
    val rooms: List<GridRoom> = emptyList(),
    val north: Int = 0,
    val analysis: Analysis? = null,
    val cols: Int = GRID,
    val rows: Int = GRID,
    /** The reader's own photograph, drawn inside the dial. */
    val planImage: ImageBitmap? = null,
    val onNorthChange: (Int) -> Unit = {},
    /**
     * True when the plan named its own entrance, so the next screen is the report. False sends the
     * reader to the front-door screen first — and the button says so.
     */
    val doorKnown: Boolean = false,
    /** ⚠ The dial's nudge is an infinite animation: only the real screen turns it on. */
    val hintPulse: Boolean = false,
)

/**
 * Scan your plan (§6.2b). Upload a photo or PDF, we read the ROOMS, and you confirm them — **there is
 * no automatic path to a score** (Implementation PRD §232): nothing is scored until the reader taps
 * "Read my home".
 *
 * ⭐ The copy on this screen is load-bearing, not decoration. The model reads room *names* superbly
 * (~95 %) and does not reliably know *where* they are (40–70 %). So the screen promises identification
 * and never promises placement, and the grid is always one tap away.
 */
@Composable
fun ScanScreen(
    state: ScanUiState,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRetry: () -> Unit,
    onUseRooms: (ScanOutcome) -> Unit,
    /** The user says room N is really a different kind — §6.2b's "confirm **or correct** each one". */
    onCorrectRoom: (Int, RoomType) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
    /**
     * Render with room N's type list already unfolded (and its list open). The screenshot harness
     * needs it — a golden cannot tap a row.
     */
    startOpenRow: Int = -1,
    /**
     * True once "take a photo" has been pressed on a phone with no camera app at all. The screen then
     * says so in one line instead of the button doing nothing. Kept after the positional params:
     * everything above it is called positionally by the accessibility pass.
     */
    cameraUnavailable: Boolean = false,
    /**
     * ⭐ How long the current read has been going, so a wait that outgrows "a few seconds" can say so
     * — see [ReadingBody]. DEFAULT 0, AND THE CLOCK LIVES OUTSIDE THIS SCREEN: a composition that
     * never stops ticking never goes idle, and the screenshot harness waits for idle.
     */
    readingElapsedMillis: Long = 0L,
    /** ⭐ Carry on with a refused reply's own alternative reading — see `ScanOutcome.ifRead`. */
    onReadAnyway: () -> Unit = {},
    /** ⭐ North, on this same screen, for a placed read — see [NorthOnResult]. */
    north: NorthOnResult = NorthOnResult(),
) {
    val colors = VastuTheme.colors
    BoxWithConstraints(Modifier.screenRoot(colors.paper)) {
        // The most of the screen the dial may take — see NorthControls. Only a placed read draws one.
        val dialCap = maxHeight * NORTH_DIAL_SCREEN_SHARE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(VastuTheme.spacing.s6),
        ) {
            // ⛔ No "Step 1 of 3" here either — see the note on AddHomeScreen.
            Spacer(Modifier.height(VastuTheme.spacing.s3))

            when (state) {
                ScanUiState.Idle ->
                    IdleBody(onPickImage, onTakePhoto, onDrawInstead, cameraUnavailable)
                ScanUiState.Reading -> ReadingBody(readingElapsedMillis)
                is ScanUiState.Done ->
                    DoneBody(
                        state.outcome, onUseRooms, onCorrectRoom, onRetry, onDrawInstead, startOpenRow,
                        onReadAnyway = onReadAnyway,
                        north = north,
                        dialCap = dialCap,
                    )
                is ScanUiState.Busy -> BusyBody(state.retryAfterSeconds, onRetry, onDrawInstead)
                ScanUiState.Unavailable -> UnavailableBody(onRetry, onDrawInstead)
                ScanUiState.BadImage -> BadImageBody(onRetry, onDrawInstead)
                ScanUiState.NotConfigured -> NotConfiguredBody(onDrawInstead)
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            VastuButton("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY, large = false)
        }
    }
}

/**
 * ⭐ THE UPLOAD SCREEN IS NOW A FALLBACK (30 Sep 2026). "Upload a plan" and "Photograph your plan" on
 * Add a home open the phone's picker or camera straight away, so most readers never see this. It is
 * what shows when a pick is cancelled after a retry, or when the phone turns out to have no camera app.
 *
 * ⚠ Its promise changed with the flow. It used to say "You place and check every room before anything
 * is scored" — the checking screen that sentence named is gone. What stays true: the rooms are listed on
 * the result, any of them can be changed there, and nothing is scored until the reader says so.
 */
@Composable
private fun IdleBody(
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onDrawInstead: () -> Unit,
    cameraUnavailable: Boolean,
) {
    val colors = VastuTheme.colors
    VText("Upload your plan", style = VastuTheme.type.h2, color = colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "We read the rooms off your plan. You can change any of them before your report.",
        style = VastuTheme.type.body, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s6))

    // ⭐ Steer to the good input. Skew is what destroys the read, not compression — a PDF or a
    // screenshot reads far better than a photo taken at an angle — so the PDF is the primary button.
    VastuButton("Choose a PDF or picture", onClick = onPickImage)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    // Disabled once the phone has shown it has no camera app (audit C14), with the reason beside it.
    VastuButton(
        "Take a photo of it now",
        onClick = onTakePhoto,
        style = VastuButtonStyle.SECONDARY,
        enabled = !cameraUnavailable,
    )
    if (cameraUnavailable) {
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "No camera app on this phone. Choose a picture or PDF above — it reads " +
                "better anyway.",
            style = VastuTheme.type.bodySm, color = colors.verdictSuboptimal,
        )
    }
    Spacer(Modifier.height(VastuTheme.spacing.s4))
    // ⭐ The tips moved behind the i (owner's standing rule: explanation, moved, never deleted). They
    // are the same words Add a home keeps behind its own i.
    VastuInfoLine(label = "What works best", info = WHAT_WORKS_BEST, tag = "scan.tips")

    // ⛔ THE READER PICKER IS GONE FROM THIS SCREEN (owner, 10 Aug 2026) — do not put it back. It lives
    // in Settings.

    Spacer(Modifier.height(VastuTheme.spacing.s4))
    VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY, large = false)
}

/**
 * The advice that makes a plan read well, in one place: behind the **i** on Add a home and on the
 * upload screen. Every sentence of the old "What works best" card, unchanged in substance.
 */
const val WHAT_WORKS_BEST: String =
    "The PDF your architect or builder sent, or a clear screenshot of it, reads best. " +
        "Use a flat, top-down plan — not a 3D picture of the finished home — with the room names " +
        "printed on it, like KITCHEN or BEDROOM. One home per picture: if the sheet shows several " +
        "flats, crop to yours. If you photograph a printed plan, hold the phone flat above it; a " +
        "picture taken at an angle is the one thing we struggle with."

/**
 * ⭐⭐ THE WAIT SAYS SOMETHING NEW WHEN IT GETS LONG (11 Aug 2026). A normal read is a few seconds, but
 * the reader is allowed 20 s to connect plus 120 s to answer, and a motionless screen that promised "a
 * few seconds" is indistinguishable from a frozen app. Nothing here makes the read faster; it stops
 * the app lying about how long it is taking.
 */
@Composable
private fun ReadingBody(elapsedMillis: Long) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        LoadingState("Reading your plan…")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            when {
                // The second model is only asked once the first has answered, so a wait this long
                // usually IS the second look.
                elapsedMillis >= SECOND_LOOK_AFTER_MILLIS -> "Taking a second look at your plan."
                elapsedMillis >= STILL_READING_AFTER_MILLIS ->
                    "Still reading — a detailed plan can take a couple of minutes."
                else -> "This usually takes a few seconds."
            },
            style = VastuTheme.type.bodySm, color = VastuTheme.colors.textTertiary,
        )
    }
}

/** When the wait stops being "a few seconds" and says so. */
internal const val STILL_READING_AFTER_MILLIS = 8_000L

/** When it is long enough that the second reader is the likely explanation. */
internal const val SECOND_LOOK_AFTER_MILLIS = 35_000L

@Composable
private fun DoneBody(
    outcome: ScanOutcome,
    onUseRooms: (ScanOutcome) -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    startOpenRow: Int,
    onReadAnyway: () -> Unit = {},
    north: NorthOnResult,
    dialCap: Dp,
) {
    when (outcome) {
        is ScanOutcome.Placed -> PlacedBody(
            outcome = outcome,
            north = north,
            dialCap = dialCap,
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            onDrawInstead = onDrawInstead,
            startOpenRow = startOpenRow,
        )

        is ScanOutcome.Assisted -> AssistedBody(
            outcome = outcome,
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            startOpenRow = startOpenRow,
        )

        is ScanOutcome.Refused ->
            RefusedBody(
                outcome.reason, onRetry, onDrawInstead,
                // The offer exists only when there is a real read behind it — see ScanOutcome.ifRead.
                onReadAnyway = onReadAnyway.takeIf { outcome.ifRead != null },
            )
    }
}

/**
 * ⭐⭐ A PLACED READ: the answer, the rooms folded shut under it, and North on the reader's own plan.
 *
 * What it replaces, in the owner's words (30 Sep 2026): *"I specially think the 'Check what we read'
 * screen is not needed. We show the list of rooms detected on this screen anyways"*. The rooms were
 * listed here, then North was asked on its own screen, then the same rooms were listed a second time
 * to be checked, then a third time in the report. Now: here, where a room's KIND is corrected; then
 * the report, where its direction is read.
 *
 * ⚠ THE LIST IS FOLDED SHUT, by the owner's standing rule for a result screen (the answer leads; a
 * long supporting list folds under a heading that says what it is and how many). When the reader was
 * unsure of some rooms, the shut heading says so — measured on the owner's own sheet, 4 of its 11
 * rooms carry the CHECK pill, so opening the list by itself would have meant opening it almost always.
 */
@Composable
private fun PlacedBody(
    outcome: ScanOutcome.Placed,
    north: NorthOnResult,
    dialCap: Dp,
    onUseRooms: () -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    startOpenRow: Int,
) {
    val colors = VastuTheme.colors
    VText("We read ${outcome.rooms.size} rooms", style = VastuTheme.type.h2, color = colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    RoomsFold(
        label = "Rooms we read",
        rooms = outcome.rooms,
        onCorrectRoom = onCorrectRoom,
        startOpenRow = startOpenRow,
        tag = "scan.rooms",
    )
    DroppedSpaces(outcome.notes.dropped.map { it.label to it.reason }, onDrawInstead = onDrawInstead)

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    VText("Which way is North?", style = VastuTheme.type.h3, color = colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s1))
    NorthInstruction()
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    NorthControls(
        rooms = north.rooms,
        north = north.north,
        analysis = north.analysis,
        onNorthChange = north.onNorthChange,
        cols = north.cols,
        rows = north.rows,
        planImage = north.planImage,
        hintPulse = north.hintPulse,
        maxDialHeight = dialCap,
    )
    val answersCard = NorthCheck.claims(north.analysis).isNotEmpty()
    // ⭐ The button names the screen it opens: the report when the plan named its own entrance, the
    // front-door screen when it did not. A control naming a screen it does not open is a defect this
    // project has logged more than once.
    VastuButton(
        when {
            north.doorKnown && answersCard -> "Yes — read my home"
            north.doorKnown -> "Read my home"
            answersCard -> "Yes — next, your front door"
            else -> "Next — your front door"
        },
        onClick = onUseRooms,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    VastuButton("Try a different picture", onClick = onRetry, style = VastuButtonStyle.SECONDARY)
}

/**
 * An ASSISTED read: we have the room names but not where they sit, so the next screen is the grid,
 * where every room is placed by hand. The list folds shut like a placed read's — the grid shows every
 * room anyway.
 */
@Composable
private fun AssistedBody(
    outcome: ScanOutcome.Assisted,
    onUseRooms: () -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    startOpenRow: Int,
) {
    val colors = VastuTheme.colors
    // ⭐ The honest headline for the mode many real plans land in. It claims the room list, which is
    // measured to be excellent, and claims nothing about placement.
    VText("We found ${outcome.rooms.size} rooms", style = VastuTheme.type.h2, color = colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        when (outcome.reason) {
            // ⚠ Claims nothing about sizes, so it is true of a >20-room fully-sized sheet too (the
            // room-count ceiling reuses this reason).
            AssistReason.TOO_MANY_ROOMS ->
                "We read every room name, but not where each one sits — and we don't guess. " +
                    "You place them on the next screen."
            AssistReason.TOO_FEW_PLACED ->
                "We read the room names but not where they sit — and we don't guess. " +
                    "You place them on the next screen."
            AssistReason.UNIFORM_BOXES ->
                "We read the room names, but their shapes came back identical, not measured. " +
                    "You place them on the next screen."
            // ⚠ Says out loud that we kept the names and not the shapes, because this list arrives
            // from a picture we told the user looked angled.
            AssistReason.ANGLED_VIEW ->
                "The picture looked angled, so we kept the room names and not their shapes. " +
                    "You place them on the next screen."
        },
        style = VastuTheme.type.body, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s4))
    RoomsFold(
        label = "Rooms we found",
        rooms = outcome.rooms,
        onCorrectRoom = onCorrectRoom,
        startOpenRow = startOpenRow,
        tag = "scan.rooms",
    )
    // ⭐ An assisted read is the ONE path that packs rooms onto the grid, so it is the one path where a
    // room can fall off the end of it. Said out loud, never silently.
    val offGrid = roomsOffTheGrid(outcome).map { it.label.ifBlank { it.type.label() } }
    if (offGrid.isNotEmpty()) {
        val one = offGrid.size == 1
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VastuCard(background = colors.surface) {
            VText(
                if (one) "1 room won't fit on the grid" else "${offGrid.size} rooms won't fit on the grid",
                style = VastuTheme.type.bodyLg, color = colors.textPrimary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                "The grid holds ${gridCapacityFor(outcome)} rooms and your plan has ${outcome.rooms.size}. " +
                    "Not on it: ${offGrid.joinToString(", ")}.",
                style = VastuTheme.type.bodySm, color = colors.textSecondary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                // The price and the way out, in that order.
                if (one) "A room that is not on the grid is not scored. Make space on the next screen and add it yourself."
                else "A room that is not on the grid is not scored. Make space on the next screen and add them yourself.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
        }
    }
    DroppedSpaces(outcome.notes.dropped.map { it.label to it.reason }, onDrawInstead = null)

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    VastuButton("Place them on the grid", onClick = onUseRooms)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    VastuButton("Try a different picture", onClick = onRetry, style = VastuButtonStyle.SECONDARY)
}

/**
 * ⭐ Every room we read, FOLDED SHUT under a heading that says what it is and how many — the one place
 * a room's kind is corrected. When the reader was unsure of some rooms the heading says how many
 * ("4 to check"); the harness can ask for a row to be open, which opens the list.
 */
@Composable
private fun RoomsFold(
    label: String,
    rooms: List<ScannedRoom>,
    onCorrectRoom: (Int, RoomType) -> Unit,
    startOpenRow: Int,
    tag: String,
) {
    // At most one row's type list is open at a time: the question being answered is about ONE room.
    var openRow by rememberSaveable { mutableStateOf(startOpenRow) }
    val unsure = rooms.count { needsCheck(it) }
    VastuFoldSection(
        label = label,
        count = rooms.size,
        info = "Tap a room to change what kind of room it is.",
        startOpen = startOpenRow >= 0,
        tag = tag,
        // ⭐ Shut, but never silent about a room the reader was unsure of: the heading says how many
        // want a look, and each of those rows still carries its own CHECK pill inside.
        flag = if (unsure > 0) "$unsure to check" else null,
    ) {
        VastuCard {
            // ⭐⭐ TELLING APART THE ROWS A PLAN CAPTIONS IDENTICALLY — for a screen reader only. The
            // VISIBLE caption is left alone: a position spoken aloud is not a caption.
            val spokenOrdinals = ordinalsForDuplicateLabels(rooms)
            rooms.forEachIndexed { index, room ->
                ReadRoomRow(
                    room = room,
                    ordinal = spokenOrdinals[index],
                    expanded = openRow == index,
                    onExpandedChange = { open -> openRow = if (open) index else -1 },
                    onPick = { type -> onCorrectRoom(index, type) },
                )
            }
        }
    }
}

/**
 * Anything we did NOT carry through is shown, never silently swallowed: a dropped space changes the
 * footprint the engine scores, so the reader has to be able to see it.
 *
 * [onDrawInstead] is the way to add one back where this reading cannot — a placed read never opens
 * the grid, so the card carries the grid itself. Null on an assisted read, whose next screen IS the
 * grid.
 */
@Composable
private fun DroppedSpaces(dropped: List<Pair<String, DropReason>>, onDrawInstead: (() -> Unit)?) {
    val colors = VastuTheme.colors
    val notable = dropped.filter { it.second != DropReason.NOT_HABITABLE }
    if (notable.isEmpty()) return
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    VastuCard(background = colors.surface) {
        VText("We also saw, but didn't add", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        notable.forEach { (label, reason) ->
            VText("• $label — ${reason.plain()}", style = VastuTheme.type.bodySm, color = colors.textSecondary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            if (onDrawInstead == null) "If any of these is a real room, add it yourself on the next screen."
            else "If any of these is a real room, draw your home on a grid instead — this reading cannot add it.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )
        if (onDrawInstead != null) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton(
                "Draw it on a grid instead",
                onClick = onDrawInstead,
                style = VastuButtonStyle.SECONDARY,
                large = false,
            )
        }
    }
}

/**
 * The model id, spoken like a product name: "openai/gpt-5.6-luna" → "GPT 5.6 Luna",
 * "google/gemini-3.1-pro-preview" → "Gemini 3.1 Pro". Used by Settings only — nothing a customer walks
 * through names a model (owner, 11 Aug 2026).
 */
internal fun shortModelName(id: String): String =
    id.substringAfterLast('/')
        .split('-')
        .filter { it.isNotBlank() && !it.equals("preview", ignoreCase = true) }
        .joinToString(" ") { word ->
            when {
                // An acronym has no vowels (GPT, GLM); "pro" and "luna" are words and stay words.
                word.none { it.isDigit() } && word.none { it in "aeiou" } -> word.uppercase()
                word.first().isDigit() -> word
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }

/**
 * The printed size, short enough to sit on one caption line beside the room's name.
 *
 * ⚠ Indian sheets usually print the SAME measurement twice — `3.72m X 4.50m ( 12'-2" x 14'-9" )` — and
 * the whole string on a row that repeats per room is several wrapped lines on a 320 dp screen at 200 %
 * font. The trailing repeat is dropped; nothing else is touched, because this is the number the user
 * is checking against their own paper.
 *
 * A caption that OPENS with a bracket is a compound space — `(3.17mX0.90m)+(1.51mX0.40m)` for an
 * L-shaped utility — where the brackets are the measurement rather than a repeat of it, so it is left
 * whole and allowed to wrap.
 */
internal fun shortSize(printed: String): String {
    val s = printed.trim()
    if (s.startsWith("(")) return s
    val bracket = s.indexOf('(')
    return if (bracket > 0) s.substring(0, bracket).trim() else s
}

/**
 * ⭐ WHAT THE PLAN PRINTED FOR THIS ROOM — every part of it.
 *
 * A room fused from a run of sections has no single printed size — it has all of them (the owner's
 * balcony runs the width of his flat and his sheet dimensions it in three pieces). "Check what we read"
 * was the only screen that printed them all; since it is gone (30 Sep 2026), this row does, so none of
 * the three real measurements is lost. Empty when the plan printed no size.
 */
internal fun printedSizeWords(room: ScannedRoom): String = when {
    room.readInParts.size > 1 -> "one space: " + room.readInParts.joinToString(" + ") { shortSize(it) }
    else -> shortSize(room.printedSize)
}

/**
 * ⭐ Where each room sits among the ones this plan captions IDENTICALLY — "2 of 5" — or null when its
 * caption already stands alone. Two rooms count as the same only when the caption AND the printed size
 * are both identical.
 */
internal fun ordinalsForDuplicateLabels(rooms: List<ScannedRoom>): List<String?> {
    val key = { r: ScannedRoom -> r.label.trim().uppercase() + "|" + r.printedSize.trim().uppercase() }
    val total = rooms.groupingBy(key).eachCount()
    val seen = mutableMapOf<String, Int>()
    return rooms.map { r ->
        val k = key(r)
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        val t = total[k] ?: 0
        if (t > 1) "$n of $t" else null
    }
}

/** The reader was unsure of this room — the CHECK pill on its row, and the count on the list's heading. */
private fun needsCheck(room: ScannedRoom): Boolean =
    RoomFlag.OVERLAP_TRIMMED in room.flags || RoomFlag.LOOSE_LABEL_MATCH in room.flags

/**
 * One room we read, and the way to tell us we read it wrong. The whole row is the control: one node
 * to a screen reader, and a target the size of the row.
 */
@Composable
private fun ReadRoomRow(
    room: ScannedRoom,
    /** "2 of 5" when this plan captions several rooms the same, null when unique. Spoken only. */
    ordinal: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (RoomType) -> Unit,
) {
    val colors = VastuTheme.colors
    val unsure = needsCheck(room)
    val size = printedSizeWords(room)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = VastuTheme.sizes.minTouch)
                // ⚠ The action goes in onClickLabel, NOT in the description below — TalkBack already
                // announces the role and the gesture.
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = "change the room type",
                ) { onExpandedChange(!expanded) }
                .padding(vertical = VastuTheme.spacing.s2)
                // One node per room, phrased as what we did: "we read X as Y" invites the correction.
                .semantics(mergeDescendants = true) {
                    this.contentDescription =
                        "We read \"${room.label}\" as ${room.type.label()}" +
                            (if (ordinal != null) " ($ordinal)" else "") +
                            (if (size.isNotBlank()) ", $size" else "") +
                            (if (unsure) ". Please check this one." else "")
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            Column(Modifier.weight(1f)) {
                VText(room.type.label(), style = VastuTheme.type.body, color = colors.textPrimary)
                // ⚠ textSecondary, NOT textTertiary: the ATF contrast check fails the lightest text at
                // caption size, and this is the caption read off the user's own plan.
                // ⭐ The caption AND every size the plan printed for it, on one line.
                VText(
                    listOf(room.label, size).filter { it.isNotBlank() }.joinToString(" · "),
                    style = VastuTheme.type.caption, color = colors.textSecondary,
                )
            }
            if (unsure) CheckPill()
            // A visible affordance, as a word. ⚠ textSecondary: the sage accent measured 3.64 : 1 here.
            VText("Change", style = VastuTheme.type.label, color = colors.textSecondary)
        }
        if (expanded) {
            RoomTypePicker(
                current = room.type,
                expanded = true,
                onExpandedChange = { onExpandedChange(it) },
                onPick = onPick,
                modifier = Modifier.padding(bottom = VastuTheme.spacing.s3),
            )
        }
    }
}

/**
 * ⚠ The label is `textSecondary`, not the accent colour the tint is made from: accent-on-accent-tint
 * reads 3.71 : 1 where 4.5 is required. A "CHECK" nobody can read is the same as no flag at all.
 */
@Composable
private fun CheckPill() {
    VText(
        "CHECK",
        style = VastuTheme.type.caption,
        color = VastuTheme.colors.textSecondary,
        modifier = Modifier
            .clip(VastuTheme.shapes.full)
            .background(VastuTheme.colors.provenanceMod.copy(alpha = 0.14f))
            .padding(horizontal = VastuTheme.spacing.s2, vertical = VastuTheme.spacing.s1),
    )
}

@Composable
private fun RefusedBody(
    reason: RefusalReason,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    /**
     * ⭐ Non-null only on the 2D gate, and only when the same reply DID come back with rooms on it.
     * Then this refusal stops being a wall: the user can look at what we read and judge it.
     */
    onReadAnyway: (() -> Unit)? = null,
) {
    // Each refusal names the ONE thing the user can change.
    val (title, body) = when (reason) {
        // ⚠ Claims only what we saw (a tilted view) and, when the read is there, offers it instead of
        // arguing. The old words told the owner his own labelled, top-down plan was not a plan.
        RefusalReason.NOT_2D -> "This looks like a tilted 3D view" to
            if (onReadAnyway != null) {
                "It looks angled, but we did read room names off it. Have a look."
            } else {
                "Rooms seen at an angle can't be measured. Please send the flat, top-down plan — " +
                    "a colourful, furnished one is fine."
            }
        RefusalReason.NOT_A_PLAN -> "That doesn't look like a floor plan" to
            "It might be an elevation, a brochure page or a site map. Please upload the flat, " +
                "top-down plan."
        RefusalReason.NO_LABELS -> "We can't see the room names" to
            "The rooms aren't named, or the names are too small to read. Upload a plan with room " +
                "names — or draw your home instead."
        RefusalReason.MULTI_UNIT -> "There's more than one home on this sheet" to
            "This sheet shows several flats. Please crop the picture to just your own home " +
                "and try again."
        RefusalReason.NO_ROOMS -> "We couldn't pick out any rooms" to
            "The plan looks right, but nothing on it came through as a room we recognise. " +
                "A clearer picture often fixes it."
    }
    GuidanceState(title = title, body = body) {
        Column {
            // ⭐ When there IS a read behind the refusal it leads: nothing is scored from it, and it
            // lands on the same result every other read lands on.
            if (onReadAnyway != null) {
                VastuButton("Show me what you read", onClick = onReadAnyway)
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                VastuButton(
                    "Try a different picture", onClick = onRetry,
                    style = VastuButtonStyle.SECONDARY,
                )
            } else {
                VastuButton("Try a different picture", onClick = onRetry)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY)
        }
    }
    // ⛔ No model buttons under a refusal either — comparing readers lives in Settings.
}

@Composable
private fun BusyBody(retryAfterSeconds: Int?, onRetry: () -> Unit, onDrawInstead: () -> Unit) {
    // ⭐ NOT an error state: a rate limit is expected and temporary, said calmly with a real number.
    val wait = when {
        retryAfterSeconds == null -> "Please try again in a minute."
        retryAfterSeconds <= 60 -> "Please try again in about $retryAfterSeconds seconds."
        else -> "Please try again in about ${(retryAfterSeconds + 59) / 60} minutes."
    }
    GuidanceState(
        title = "We're reading a lot of plans right now",
        body = "We haven't looked at your plan yet. $wait Or draw your home on the grid — no internet needed.",
    ) {
        Column {
            VastuButton("Try again", onClick = onRetry)
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun UnavailableBody(onRetry: () -> Unit, onDrawInstead: () -> Unit) {
    // ⚠ DOES NOT BLAME THE PHONE (11 Aug 2026). Our own failure is named first because it is the
    // likely one; theirs is still named, because it is real and the app cannot tell the two apart.
    GuidanceState(
        title = "Our reader didn't answer in time",
        body = "That is usually us, not you — try again in a moment. Reading a plan needs a " +
            "connection; drawing your home on the grid does not.",
    ) {
        Column {
            VastuButton("Try again", onClick = onRetry)
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun BadImageBody(onRetry: () -> Unit, onDrawInstead: () -> Unit) {
    GuidanceState(
        title = "We couldn't open that file",
        body = "If it's a PDF, check it isn't password-protected, or take a screenshot of the page " +
            "and upload that instead.",
    ) {
        Column {
            VastuButton("Choose another file", onClick = onRetry)
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun NotConfiguredBody(onDrawInstead: () -> Unit) {
    // ⭐ The screen refuses to look like a working one: there is no picker here.
    GuidanceState(
        title = "This copy of the app can't read plans",
        body = "Plan reading is switched off in this version. It isn't your plan or your phone. " +
            "Drawing your home on the grid works normally and is scored by exactly the same rules.",
    ) {
        Column {
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead)
        }
    }
}

/** Plain English for why a space didn't make it onto the grid. Never a code, never blank. */
private fun DropReason.plain(): String = when (this) {
    DropReason.NOT_HABITABLE -> "it isn't a room we score"
    DropReason.UNKNOWN_LABEL -> "we didn't recognise the name"
    DropReason.DEGENERATE -> "it was too small to place on the grid"
    DropReason.INVALID_GEOMETRY -> "we couldn't make sense of its shape"
    DropReason.OVERLAP_UNRESOLVABLE -> "it overlapped another room"
}
