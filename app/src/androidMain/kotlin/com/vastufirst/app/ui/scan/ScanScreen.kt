package com.vastufirst.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastufirst.app.ui.common.RoomTypePicker
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuChip
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.AssistReason
import com.vastufirst.shared.scan.DropReason
import com.vastufirst.shared.scan.RefusalReason
import com.vastufirst.shared.scan.RoomFlag
import com.vastufirst.shared.scan.ScanOutcome
import com.vastufirst.shared.scan.ScannedRoom

/**
 * Scan your plan (§6.2b). Upload a photo or PDF, we read the ROOMS, and you confirm them on the
 * guided grid — **there is no automatic path to a score** (Implementation PRD §232).
 *
 * ⭐ The copy on this screen is load-bearing, not decoration. Everything measured about the model
 * says the same thing three different ways: it reads room *names* superbly (~95 %) and does not
 * reliably know *where* they are (40–70 %). So the screen must promise identification and never
 * promise placement — "the AI draws your plan" would ship a confidently wrong ₹699 score. The
 * guided grid is offered on every single state, because it is fully offline and always works.
 *
 * The front door is never mentioned as something we read: the user taps it, as they do today.
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
    /** Render with room N's type list already unfolded. The screenshot harness needs it — a golden
     *  cannot tap a row, and the wrapped list of nineteen chips is narrower here than in the editor
     *  (it sits inside a card), so it is a genuinely different layout risk and must be looked at. */
    startOpenRow: Int = -1,
    /**
     * True once "take a photo" has been pressed on a phone with no camera app at all — an emulator,
     * a stripped ROM. The screen then says so in one line instead of the button doing nothing, which
     * is the shape of the very defect this release fixes. Kept after the positional params:
     * everything above it is called positionally by the accessibility pass.
     */
    cameraUnavailable: Boolean = false,
    /**
     * ⭐ How long the current read has been going, so a wait that outgrows "a few seconds" can say
     * so — see [ReadingBody].
     *
     * ⚠ DEFAULT 0, AND THE CLOCK LIVES OUTSIDE THIS SCREEN, for the same reason every animation in
     * this app is off at the stateless seam: a composition that never stops ticking never goes idle,
     * and the screenshot harness waits for idle before it photographs. It hung a whole cloud build
     * for forty minutes once. The harness draws this composable with the default and gets the still
     * first frame; only [ScanRoute] runs the clock, and even there it stops after the last step.
     */
    readingElapsedMillis: Long = 0L,
    /** ⭐ Carry on with a refused reply's own alternative reading — see `ScanOutcome.ifRead`. */
    onReadAnyway: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        // ⛔ No "Step 1 of 3" here either — see the note on AddHomeScreen. This screen and the one
        // before it both claimed to be step one, and the scan path is five screens, not three.
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        when (state) {
            ScanUiState.Idle ->
                IdleBody(onPickImage, onTakePhoto, onDrawInstead, cameraUnavailable)
            ScanUiState.Reading -> ReadingBody(readingElapsedMillis)
            is ScanUiState.Done ->
                DoneBody(
                    state.outcome, onUseRooms, onCorrectRoom, onRetry, onDrawInstead, startOpenRow,
                    onReadAnyway = onReadAnyway,
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
        // Copy cut (4 Aug 2026): 29 words -> 20; every claim kept - we read, you check, no
        // score until you say so.
        "We read the room names so you don't type them. " +
            "You place and check every room before anything is scored.",
        style = VastuTheme.type.body, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s6))

    // ⭐ Steer to the good input. Measured (§3e): skew is what destroys the read, not compression —
    // a PDF or a screenshot reads far better than a photo taken at an angle, and the difference is
    // worth more than any model choice. So the PDF is the primary button, not the camera.
    VastuButton("Choose a PDF or picture", onClick = onPickImage)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    // ⭐ This opens the CAMERA (v0.6.6). It used to open the gallery — the same picker as the button
    // above, under a different name — which left someone holding a printed plan with no way to
    // photograph it, and made the button look broken. The label now describes what happens.
    // Disabled once the phone has shown it has no camera app (audit C14): a normal-looking button
    // directly above "no camera app on this phone" invited a tap that does nothing. The line below
    // says why it is off and where to go instead — never a silent grey mystery.
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

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    VastuCard {
        VText("What works best", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        Bullet("The PDF your architect or builder sent, or a clear screenshot of it.")
        Bullet("A flat, top-down plan — not a 3D picture of the finished home.")
        Bullet("Room names printed on the plan, like KITCHEN or BEDROOM.")
        Bullet("One home per picture. If the sheet shows several flats, crop to yours.")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            "If you photograph a printed plan, hold the phone flat above it. " +
                "A picture taken at an angle is the one thing we struggle with.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )
    }

    // ⛔ THE READER PICKER IS GONE FROM THIS SCREEN (owner, 10 Aug 2026: "Remove the Luna and Gemini
    // model selection and put them in settings for now") — do not put it back. It was a row of model
    // names on the screen a customer meets when they are about to photograph their home: a comparison
    // lever for us, and one more reason to hesitate for them, on the screen the whole paid flow
    // depends on. It lives in Settings now, where choosing one also survives a retry.

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    OfflineAlternative(onDrawInstead)
}

/**
 * ⭐⭐ THE WAIT SAYS SOMETHING NEW WHEN IT GETS LONG (11 Aug 2026).
 *
 * ⚠ WHAT WAS WRONG. The screen was two lines of static text — "Reading your plan…" and "This
 * usually takes a few seconds." — and NOTHING on it moves; the shared loading surface is centred
 * text with no indicator at all. A normal read really is a few seconds, but the reader is allowed
 * 20 s to connect plus 120 s to answer, and a plan the first model calls "not a flat plan" is sent
 * to a second one afterwards. So a person can sit in front of a completely motionless screen for
 * over two minutes having been promised "a few seconds" — which is indistinguishable from a frozen
 * app, and the natural response is to kill it and lose the scan.
 *
 * Nothing here makes the read faster. It stops the app lying about how long it is taking.
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
                // usually IS the second look. Saying so is both true and the most reassuring thing
                // available — it tells the reader something is still happening.
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
) {
    when (outcome) {
        is ScanOutcome.Placed -> RoomsBody(
            title = "We read ${outcome.rooms.size} rooms",
            // ⚠ Both honesty claims stay: "roughly", and nothing is scored until confirmed. The
            // order it names is the real one since 11 Aug 2026 — North is marked first, so the
            // checking screen after it can show each room's direction and result.
            body = "We've put them roughly where they appear on your plan. " +
                "Mark North, then check each one — nothing is scored until you say it's right.",
            rooms = outcome.rooms,
            dropped = outcome.notes.dropped.map { it.label to it.reason },
            // A placed read carries its own rectangles and never packs, so nothing can fall off.
            offGrid = emptyList(),
            gridCapacity = gridCapacityFor(outcome),
            // The button names the screen it opens, and that is the North dial now.
            cta = "Next — which way is North?",
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            onDrawInstead = onDrawInstead,
            startOpenRow = startOpenRow,
            // A placed read goes to the North dial and never opens the editor at all.
            canAddOnNextScreen = false,
        )

        is ScanOutcome.Assisted -> RoomsBody(
            // ⭐ The honest headline for the mode that most real plans land in. It claims the room
            // list, which is measured to be excellent, and claims nothing about placement.
            title = "We found ${outcome.rooms.size} rooms",
            body = when (outcome.reason) {
                // Copy cut (4 Aug 2026), and a truth fix with it: the old body claimed the plan
                // "doesn't print their sizes", which is false for a >20-room fully-sized sheet
                // (the room-count ceiling reuses this reason). The new body claims nothing about
                // sizes, so it is true in both sub-cases.
                AssistReason.TOO_MANY_ROOMS ->
                    "We read every room name, but we can't trust where each one sits — and we " +
                        "don't guess. Drag each one to its real place."
                // ⚠ SHORTER than the line it replaces, deliberately. This screen already carries a
                // row per room, and with a room's type list open the geometry gate measured three
                // more elements pushed out of view when this paragraph was merely the same length.
                // A caption on a crowded screen is not a free place to explain things.
                AssistReason.FLOOR_PLATE ->
                    "This sheet shows a whole floor, not one home. Drag the rooms that are " +
                        "yours to where they belong."
                AssistReason.TOO_FEW_PLACED ->
                    "We read the room names but not where they sit — and we don't guess. " +
                        "Drag each one to its real place."
                AssistReason.UNIFORM_BOXES ->
                    "We read the room names, but the shapes came back identical — not really " +
                        "measured. Drag each one to its real place."
                // ⚠ Says out loud that we kept the names and binned the shapes, because this list
                // arrives from a picture we told the user looked angled. Claiming placement here
                // would be the confident-and-wrong layout the whole gate exists to prevent.
                AssistReason.ANGLED_VIEW ->
                    "The picture looked angled, so we kept the room names and threw the shapes " +
                        "away. Drag each one to its real place."
            },
            rooms = outcome.rooms,
            dropped = outcome.notes.dropped.map { it.label to it.reason },
            // ⭐ An assisted read is the ONE path that packs rooms onto the grid, so it is the one
            // path where a room can fall off the end of it.
            // ⚠ The plan's own caption, falling back to the room type — a ScannedRoom's label is
            // verbatim sheet text and CAN be blank, and "Not on it: , ." names nothing.
            offGrid = roomsOffTheGrid(outcome).map { it.label.ifBlank { it.type.label() } },
            gridCapacity = gridCapacityFor(outcome),
            cta = "Place them on the grid",
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            onDrawInstead = onDrawInstead,
            startOpenRow = startOpenRow,
            // An assisted read DOES open the grid next, which is where a room can be added.
            canAddOnNextScreen = true,
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
 * The model id, spoken like a product name: "openai/gpt-5.6-luna" → "GPT 5.6 Luna",
 * "google/gemini-3.1-pro-preview" → "Gemini 3.1 Pro". Vendor prefix and the "-preview" suffix are
 * dropped; nobody comparing two readers needs either.
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
 * ⛔ THE "WHICH AI READ IT · TESTING" SECTION IS GONE (owner, 11 Aug 2026) — DO NOT PUT IT BACK.
 *
 * It sat on the screen a paying customer lands on the moment their plan has been read: a heading
 * with the word "testing" in it, a sentence naming the model that produced their reading, and a
 * button per model offering to send their plan again. Nothing on a customer's path may name a model.
 *
 * ⭐ WHAT REPLACES IT, so the comparison the owner uses is not lost: **Settings → "Which AI reads a
 * plan"**, which has been there since 10 Aug 2026. Picking a named reader there makes every scan use
 * that one reader and, unlike the chip this screen used to carry, the choice SURVIVES a retry — so
 * comparing two readers on one plan is: pick the first, scan; pick the second, scan the same file.
 * One place, one setting, and no model name anywhere a customer will ever look.
 *
 * ⚠ `readBy` still travels on [ScanUiState.Done] and nothing draws it. That is deliberate: it is the
 * record of which reader answered, and a future crash report or diagnostic may want it. It is data,
 * not copy.
 */

/**
 * The printed size, short enough to sit on one caption line beside the room's name.
 *
 * ⚠ Indian sheets usually print the SAME measurement twice — `3.72m X 4.50m ( 12'-2" x 14'-9" )` —
 * and the whole string on a row that repeats per room is several wrapped lines on a 320 dp screen at
 * 200 % font, pushing the button that leaves this screen out of reach. The trailing repeat is
 * dropped; nothing else is touched, because this is the number the user is checking against their
 * own paper.
 *
 * A caption that OPENS with a bracket is a compound space — `(3.17mX0.90m)+(1.51mX0.40m)` for an
 * L-shaped utility — where the brackets are the measurement rather than a repeat of it, so it is
 * left whole and allowed to wrap.
 */
internal fun shortSize(printed: String): String {
    val s = printed.trim()
    if (s.startsWith("(")) return s
    val bracket = s.indexOf('(')
    return if (bracket > 0) s.substring(0, bracket).trim() else s
}

@Composable
private fun RoomsBody(
    title: String,
    body: String,
    rooms: List<ScannedRoom>,
    dropped: List<Pair<String, DropReason>>,
    /**
     * ⭐ The names of the rooms that will not fit on the drawing grid — see [roomsOffTheGrid].
     * Empty on every ordinary read, and empty for a placed read by construction.
     */
    offGrid: List<String>,
    /** How many rooms the grid can hold, so the sentence states the real number, not a guess. */
    gridCapacity: Int,
    cta: String,
    onUseRooms: () -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    startOpenRow: Int,
    /**
     * True only when the screen after this one is the GRID EDITOR — the one place a missing room can
     * actually be added. False for a placed read, which goes to the North dial and never opens the
     * editor at all, so the note under the dropped rooms must not promise otherwise.
     */
    canAddOnNextScreen: Boolean,
) {
    val colors = VastuTheme.colors
    // At most one row's type list is open at a time: two open lists on a phone is more chips than
    // screen, and the question being answered is about ONE room.
    var openRow by rememberSaveable { mutableStateOf(startOpenRow) }

    VText(title, style = VastuTheme.type.h2, color = colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(body, style = VastuTheme.type.body, color = colors.textSecondary)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    // ⭐ The screen has always asked the user to check our reading; until this build it gave them no
    // way to answer, which spends the attention the CHECK marks are for and leaves a wrong room type
    // in a paid score. Said plainly, right above the list it refers to.
    VText(
        "Tap any room to change what kind of room it is.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s6))

    VastuCard {
        rooms.forEachIndexed { index, room ->
            ReadRoomRow(
                room = room,
                expanded = openRow == index,
                onExpandedChange = { open -> openRow = if (open) index else -1 },
                onPick = { type -> onCorrectRoom(index, type) },
            )
        }
    }

    // ⭐ THE ROOMS THE GRID ITSELF CANNOT HOLD, said out loud. Different from the list below it and
    // shown first, because this loss happens LATER than that one and for a reason that is ours, not
    // the plan's: the reader read these rooms perfectly well, and the drawing grid ran out of room.
    // Until this build they simply were not on the next screen, and nothing anywhere said so.
    if (offGrid.isNotEmpty()) {
        val one = offGrid.size == 1
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VastuCard(background = colors.surface) {
            VText(
                if (one) "1 room won't fit on the grid" else "${offGrid.size} rooms won't fit on the grid",
                style = VastuTheme.type.bodyLg, color = colors.textPrimary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            // ⚠ THREE LINES, and the names run INTO the sentence rather than down a bullet list.
            // This screen is the tallest in the app and its copy has twice been cut because the
            // geometry gate measured controls pushed off the bottom at 320 dp and 200 % font. A
            // bullet per room would grow this card with the very plans that reach it.
            VText(
                "The grid holds $gridCapacity rooms and your plan has ${rooms.size}. " +
                    "Not on it: ${offGrid.joinToString(", ")}.",
                style = VastuTheme.type.bodySm, color = colors.textSecondary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                // ⚠ The price and the way out, in that order. A room that is not on the grid is not
                // scored, and saying only the first half is a warning with no answer in it.
                if (one) "A room that is not on the grid is not scored. Make space on the next screen and add it yourself."
                else "A room that is not on the grid is not scored. Make space on the next screen and add them yourself.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
        }
    }

    // Anything we did NOT carry through is shown, never silently swallowed: a dropped space changes
    // the footprint the engine scores, so the user has to be able to see it and add it back.
    val notable = dropped.filter { it.second != DropReason.NOT_HABITABLE }
    if (notable.isNotEmpty()) {
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VastuCard(background = colors.surface) {
            VText("We also saw, but didn't add", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            notable.forEach { (label, reason) ->
                VText("• $label — ${reason.plain()}", style = VastuTheme.type.bodySm, color = colors.textSecondary)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                // ⚠ "the next screen" is only true where the next screen is the GRID — the one
                // surface that can add a room. A placed read never opens it (owner, 6 Aug 2026), and
                // since 11 Aug the button underneath says "which way is North?", so the promise and
                // the control contradicted each other a centimetre apart. Both sentences are true
                // of the path they belong to, and the grid is still one tap away either way.
                if (canAddOnNextScreen) "If any of these is a real room, add it yourself on the next screen."
                else "If any of these is a real room, draw your home on a grid instead — this reading cannot add it.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
        }
    }

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    VastuButton(cta, onClick = onUseRooms)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    VastuButton("Try a different picture", onClick = onRetry, style = VastuButtonStyle.SECONDARY)
    Spacer(Modifier.height(VastuTheme.spacing.s6))
    OfflineAlternative(onDrawInstead)
}

/**
 * One room we read, and the way to tell us we read it wrong.
 *
 * The whole row is the control rather than a separate button at its end: it is one node to a screen
 * reader (one thing, one action), it is a target the size of the row for someone whose eyes and hands
 * are not what they were — a large part of this audience — and it leaves the CHECK mark meaning only
 * what it says instead of doubling as a button.
 */
@Composable
private fun ReadRoomRow(
    room: ScannedRoom,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (RoomType) -> Unit,
) {
    val colors = VastuTheme.colors
    val needsCheck = RoomFlag.OVERLAP_TRIMMED in room.flags || RoomFlag.LOOSE_LABEL_MATCH in room.flags
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = VastuTheme.sizes.minTouch)   // a11y: the row is now a tap target
                // ⚠ The action goes in onClickLabel, NOT in the description below. Writing
                // "double tap to…" into a contentDescription is an accessibility defect: TalkBack
                // already announces the role and the gesture, so the user hears it twice, and ATF's
                // RedundantDescriptionCheck fired once per room row when it was written that way.
                // Here TalkBack says "…, button, double tap to change the room type."
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = "change the room type",
                ) { onExpandedChange(!expanded) }
                .padding(vertical = VastuTheme.spacing.s2)
                // One node per room for a screen reader, phrased as what we did, not as a fact:
                // "we read X as Y" invites the correction §6.2b requires.
                .semantics(mergeDescendants = true) {
                    this.contentDescription =
                        "We read \"${room.label}\" as ${room.type.label()}" +
                            (if (room.printedSize.isNotBlank()) ", ${room.printedSize}" else "") +
                            (if (needsCheck) ". Please check this one." else "")
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            Column(Modifier.weight(1f)) {
                VText(room.type.label(), style = VastuTheme.type.body, color = colors.textPrimary)
                // ⚠ textSecondary, NOT textTertiary. The ATF contrast check fails the lightest text at
                // caption size, and this is the worst line in the app to make hard to read: it is the
                // caption we read off the user's own plan, and checking it is the whole job §6.2b gives
                // them. Caught only because the Assisted golden was switched to a real 24-space plan —
                // with 8 rooms it was already failing and I had baselined it without looking.
                // ⭐ The caption AND the size the plan printed beside it. The size is what every
                // room's shape is now built from — and therefore which direction it lands in — so
                // hiding it would leave the user confirming an answer whose working they cannot see.
                // Joined on one line rather than stacked: this row already repeats per room on the
                // tallest screen in the app, and a second line each pushes the button off a 320 dp
                // phone at 200 % font.
                VText(
                    listOf(room.label, shortSize(room.printedSize))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = VastuTheme.type.caption, color = colors.textSecondary,
                )
            }
            if (needsCheck) CheckPill()
            // A visible affordance, so the row does not rely on the reader guessing it is tappable.
            // Not an icon: "Change" is a word, and this screen's audience reads words more reliably
            // than glyphs. Not announced separately — the row sets its own description, which is what
            // a screen reader speaks instead of the merged text.
            //
            // ⚠ MEASURED, not chosen by eye. The obvious styling — the sage accent, `primaryDark` —
            // is **3.64 : 1** on this card where 4.5 is required at this size: the same defect the
            // CHECK pill shipped with, on the same screen, for the same reason. `textSecondary` is
            // **6.90 : 1**. So the "this is a control" signal is carried by the label TYPE STYLE
            // rather than by colour, which is the more robust signal anyway.
            VText("Change", style = VastuTheme.type.label, color = colors.textSecondary)
        }
        if (expanded) {
            RoomTypePicker(
                current = room.type,
                expanded = true,
                // Only the row collapses it; the picker itself never renders its closed state here.
                onExpandedChange = { onExpandedChange(it) },
                onPick = onPick,
                modifier = Modifier.padding(bottom = VastuTheme.spacing.s3),
            )
        }
    }
}

/**
 * ⚠ The label is `textSecondary`, not the accent colour the tint is made from.
 *
 * Measured: accent-on-accent-tint reads **3.71 : 1** against the card, where the accessibility check
 * requires 4.5 for text this size. `textSecondary` on the same tint is **5.82 : 1** (6.68 on a raised
 * card) and keeps the pill's colour identity in the background.
 *
 * It had been failing since the pill was written and only surfaced when a flagged room moved above
 * the screenshot fold. That makes it the second contrast defect on this screen, both on the copy that
 * matters most: this pill is the app asking the user to check a specific room, and a "CHECK" nobody
 * can read is the same as no flag at all.
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
    // Each refusal names the ONE thing the user can change. A generic "couldn't read it" would leave
    // them with nothing to do, and these are the three failures real uploads actually hit.
    val (title, body) = when (reason) {
        // ⚠ Reworded 5 Aug 2026. The old words — "a picture of the finished home rather than a
        // plan" — were flatly WRONG on the sheet the owner was blocked by: a labelled, dimensioned,
        // top-down plan that simply happened to be a coloured render. Copy that tells someone their
        // own plan is not a plan is worse than no copy. This version claims only what we saw (a
        // tilted view) and, when the read is there, offers it instead of arguing.
        RefusalReason.NOT_2D -> "This looks like a tilted 3D view" to
            if (onReadAnyway != null) {
                "We read it as an angled picture rather than a flat plan — but we did get room " +
                    "names off it. Have a look."
            } else {
                // ⚠ SHORTER than the words it replaces, on purpose. The render gate measured the
                // longer draft pushing one more element off the first screenful of a screen that
                // scrolls. A refusal is the last place to spend a reader's patience.
                "Rooms seen at an angle can't be measured. Please send the flat, top-down plan — " +
                    "a colourful, furnished one is fine."
            }
        RefusalReason.NOT_A_PLAN -> "That doesn't look like a floor plan" to
            "We couldn't find a floor plan in that picture. It might be an elevation, a brochure " +
                "page or a site map. Please upload the flat, top-down plan."
        RefusalReason.NO_LABELS -> "We can't see the room names" to
            "The rooms on this plan aren't named, or the names are too small to read. Please upload " +
                "a plan with the rooms named — or draw your home instead, which takes a few minutes."
        RefusalReason.MULTI_UNIT -> "There's more than one home on this sheet" to
            "This sheet shows several flats. Please crop the picture to just your own home " +
                "and try again."
        RefusalReason.NO_ROOMS -> "We couldn't pick out any rooms" to
            "The plan looks right, but nothing on it came through as a room we recognise. " +
                "A clearer picture often fixes it."
    }
    GuidanceState(title = title, body = body) {
        Column {
            // ⭐ When there IS a read behind the refusal it leads, because it is the only one of the
            // three that finishes the job the user came to do. Nothing is scored from it: it lands
            // on the same room-by-room confirmation every other read lands on.
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
    // ⛔ No model buttons under a refusal either. A refusal IS the case where comparing two readers
    // pays best — one refuses a furnished overhead render, the other reads it — so the comparison
    // moved to Settings rather than being dropped. It is not offered to a customer who has just been
    // told we could not read their plan.
}

@Composable
private fun BusyBody(retryAfterSeconds: Int?, onRetry: () -> Unit, onDrawInstead: () -> Unit) {
    // ⭐ NOT an error state. The free tier allows roughly three scans a minute across everyone using
    // the app, so this is expected and temporary, and it says so calmly with a real number when the
    // service gives us one.
    val wait = when {
        retryAfterSeconds == null -> "Please try again in a minute."
        retryAfterSeconds <= 60 -> "Please try again in about $retryAfterSeconds seconds."
        else -> "Please try again in about ${(retryAfterSeconds + 59) / 60} minutes."
    }
    GuidanceState(
        title = "We're reading a lot of plans right now",
        body = "We haven't looked at your plan yet — too many plans are being read at once. $wait " +
            "Or draw your home on the grid — it works now, no internet needed.",
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
    // ⚠ DOES NOT BLAME THE PHONE ANY MORE (11 Aug 2026). Every failure that is not a rate limit
    // arrives here — the reader timing out, the service being down, a bad key — and the old words
    // sent all of them to the same place: "Check you're online". So a person on perfect Wi-Fi was
    // told to go and fix their Wi-Fi whenever OUR reader was slow, and the one thing they could
    // actually do about it — try again, or draw it — read as an afterthought.
    //
    // Our own failure is named first because it is the likely one; theirs is still named, because
    // it is real and the app cannot tell the two apart from here.
    GuidanceState(
        title = "Our reader didn't answer in time",
        body = "That is usually us, not you — try again in a moment. If your phone is offline, " +
            "reading a plan needs a connection, but drawing your home on the grid does not.",
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
        body = "The picture or PDF wouldn't open. If it's a PDF, check it isn't password-protected, " +
            "or take a screenshot of the page and upload that instead.",
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
    // ⭐ The screen refuses to look like a working one. There is no picker here, because offering a
    // file chooser that leads nowhere is how the previous two builds convinced their owner the
    // feature was broken rather than absent.
    GuidanceState(
        title = "This copy of the app can't read plans",
        body = "Plan reading is switched off in this version, so there's nothing " +
            "behind plan reading. It isn't your plan or your phone. Drawing your home on the " +
            "grid works normally and is scored by exactly the same rules.",
    ) {
        Column {
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead)
        }
    }
}

/**
 * The guided grid, offered on every state of this screen.
 *
 * Two reasons, and both are requirements rather than politeness: it is the fully offline path that
 * DPDP consent has to have an alternative to (NFR §10 — a floor plan is personal data), and it is
 * the fallback §6.2b demands whenever a read fails, "without an error state".
 */
@Composable
private fun OfflineAlternative(onDrawInstead: () -> Unit) {
    val colors = VastuTheme.colors
    Box(
        Modifier
            .clip(VastuTheme.shapes.md)
            .background(colors.surface)
            .padding(VastuTheme.spacing.s4),
    ) {
        Column {
            VText("Rather not upload anything?", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                "Drawing on the grid takes a few minutes, stays on your phone, " +
                    "and is scored by exactly the same rules.",
                style = VastuTheme.type.bodySm, color = colors.textSecondary,
            )
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

@Composable
private fun Bullet(text: String) {
    VText("• $text", style = VastuTheme.type.bodySm, color = VastuTheme.colors.textSecondary)
    Spacer(Modifier.height(VastuTheme.spacing.s1))
}

/** Plain English for why a space didn't make it onto the grid. Never a code, never blank. */
private fun DropReason.plain(): String = when (this) {
    DropReason.NOT_HABITABLE -> "it isn't a room we score"
    DropReason.UNKNOWN_LABEL -> "we didn't recognise the name"
    DropReason.DEGENERATE -> "it was too small to place on the grid"
    DropReason.INVALID_GEOMETRY -> "we couldn't make sense of its shape"
    DropReason.OVERLAP_UNRESOLVABLE -> "it overlapped another room"
}
