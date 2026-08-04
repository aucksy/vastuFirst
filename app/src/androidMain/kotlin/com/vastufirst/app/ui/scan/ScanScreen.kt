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
import com.vastufirst.designsystem.components.SectionLabel
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
     * ⭐ The reader-comparison levers (owner request, 4 Aug 2026 — a testing aid he asked to keep on
     * the screen): the model ids on offer (from `reader-config.json`; empty hides every picker),
     * which one the next scan must use (null = Auto, today's shipping path), the picker callback,
     * and rescan-this-picture-with-a-named-model from any result. Defaults keep old call sites and
     * the accessibility pass unchanged.
     */
    modelChoices: List<String> = emptyList(),
    chosenModel: String? = null,
    onChooseModel: (String?) -> Unit = {},
    onRescanWith: (String) -> Unit = {},
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Step 1 of 3")
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        when (state) {
            ScanUiState.Idle ->
                IdleBody(
                    onPickImage, onTakePhoto, onDrawInstead, cameraUnavailable,
                    modelChoices, chosenModel, onChooseModel,
                )
            ScanUiState.Reading -> ReadingBody()
            is ScanUiState.Done ->
                DoneBody(
                    state.outcome, onUseRooms, onCorrectRoom, onRetry, onDrawInstead, startOpenRow,
                    readBy = state.readBy, modelChoices = modelChoices, onRescanWith = onRescanWith,
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
    modelChoices: List<String> = emptyList(),
    chosenModel: String? = null,
    onChooseModel: (String?) -> Unit = {},
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

    if (modelChoices.isNotEmpty()) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        ReaderPicker(modelChoices, chosenModel, onChooseModel)
    }

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    OfflineAlternative(onDrawInstead)
}

/**
 * ⭐ Which AI reads the plan — the owner's comparison lever (4 Aug 2026: "I should be able to change
 * the model before scanning too"). Auto is today's shipping path (the primary model, with the
 * second-opinion escalation when its own reply says the image is not a 2D plan); a named pick reads
 * with that one model only, deterministically. A wrapping FlowRow, per the design rule that chips
 * that can overflow never side-scroll.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReaderPicker(
    modelChoices: List<String>,
    chosenModel: String?,
    onChooseModel: (String?) -> Unit,
) {
    val colors = VastuTheme.colors
    SectionLabel("Which AI reads it · testing")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        VastuChip("Auto", selected = chosenModel == null, onClick = { onChooseModel(null) })
        modelChoices.forEach { m ->
            VastuChip(shortModelName(m), selected = chosenModel == m, onClick = { onChooseModel(m) })
        }
    }
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Auto is the normal choice — it asks ${shortModelName(modelChoices.first())} and gets a " +
            "second opinion when needed.",
        style = VastuTheme.type.bodySm, color = colors.textTertiary,
    )
}

@Composable
private fun ReadingBody() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        LoadingState("Reading your plan…")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            "This usually takes a few seconds.",
            style = VastuTheme.type.bodySm, color = VastuTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun DoneBody(
    outcome: ScanOutcome,
    onUseRooms: (ScanOutcome) -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    startOpenRow: Int,
    readBy: String? = null,
    modelChoices: List<String> = emptyList(),
    onRescanWith: (String) -> Unit = {},
) {
    when (outcome) {
        is ScanOutcome.Placed -> RoomsBody(
            title = "We read ${outcome.rooms.size} rooms",
            // ⚠ Destination-neutral on purpose: the next screen is the grid OR the on-photo review,
            // decided by the Settings choice at the moment of the tap (VastuNav). "Check them on the
            // grid" was written before the toggle existed and named the wrong place for half the
            // users. Both honesty claims stay: "roughly", and nothing scored until confirmed.
            body = "We've put them roughly where they appear on your plan. " +
                "Check each one — nothing is scored until you say it's right.",
            rooms = outcome.rooms,
            dropped = outcome.notes.dropped.map { it.label to it.reason },
            cta = "Check what we read",
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            onDrawInstead = onDrawInstead,
            startOpenRow = startOpenRow,
            readBy = readBy,
            modelChoices = modelChoices,
            onRescanWith = onRescanWith,
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
            },
            rooms = outcome.rooms,
            dropped = outcome.notes.dropped.map { it.label to it.reason },
            cta = "Place them on the grid",
            onUseRooms = { onUseRooms(outcome) },
            onCorrectRoom = onCorrectRoom,
            onRetry = onRetry,
            onDrawInstead = onDrawInstead,
            startOpenRow = startOpenRow,
            readBy = readBy,
            modelChoices = modelChoices,
            onRescanWith = onRescanWith,
        )

        is ScanOutcome.Refused ->
            RefusedBody(outcome.reason, onRetry, onDrawInstead, readBy, modelChoices, onRescanWith)
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
 * ⭐ "Rescan with …" — the owner's model-comparison lever on every scan RESULT (4 Aug 2026: the new
 * reader "Not Good still"; he compares the two configured models on the same picture, from the
 * phone). Says which model produced the read on screen, offers each configured model as one honest
 * button, and says plainly that a rescan sends the same picture again — each send is a paid read.
 */
@Composable
private fun RescanWithSection(
    readBy: String?,
    modelChoices: List<String>,
    onRescanWith: (String) -> Unit,
) {
    val colors = VastuTheme.colors
    SectionLabel("Which AI read it · testing")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        if (readBy != null) "This read came from ${shortModelName(readBy)}."
        else "This read used the automatic choice.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    modelChoices.forEachIndexed { i, m ->
        if (i > 0) Spacer(Modifier.height(VastuTheme.spacing.s3))
        VastuButton(
            "Rescan with ${shortModelName(m)}",
            onClick = { onRescanWith(m) },
            style = VastuButtonStyle.SECONDARY,
            large = false,
        )
    }
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "A rescan sends the same picture again.",
        style = VastuTheme.type.bodySm, color = colors.textTertiary,
    )
}

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
    cta: String,
    onUseRooms: () -> Unit,
    onCorrectRoom: (Int, RoomType) -> Unit,
    onRetry: () -> Unit,
    onDrawInstead: () -> Unit,
    startOpenRow: Int,
    readBy: String? = null,
    modelChoices: List<String> = emptyList(),
    onRescanWith: (String) -> Unit = {},
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
                "If any of these is a real room, add it yourself on the next screen.",
                style = VastuTheme.type.bodySm, color = colors.textTertiary,
            )
        }
    }

    Spacer(Modifier.height(VastuTheme.spacing.s6))
    VastuButton(cta, onClick = onUseRooms)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    VastuButton("Try a different picture", onClick = onRetry, style = VastuButtonStyle.SECONDARY)
    if (modelChoices.isNotEmpty()) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        RescanWithSection(readBy, modelChoices, onRescanWith)
    }
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
    readBy: String? = null,
    modelChoices: List<String> = emptyList(),
    onRescanWith: (String) -> Unit = {},
) {
    // Each refusal names the ONE thing the user can change. A generic "couldn't read it" would leave
    // them with nothing to do, and these are the three failures real uploads actually hit.
    val (title, body) = when (reason) {
        RefusalReason.NOT_2D -> "That looks like a 3D picture" to
            "It's a picture of the finished home rather than a plan. Please upload the flat, " +
                "top-down floor plan — the one with the rooms drawn as boxes."
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
            VastuButton("Try a different picture", onClick = onRetry)
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton("Draw it on a grid instead", onClick = onDrawInstead, style = VastuButtonStyle.SECONDARY)
        }
    }
    // A refusal is a result too — and the place model comparison matters most, because the
    // second-opinion class (a furnished overhead render) is exactly what one model refuses
    // and the other reads.
    if (modelChoices.isNotEmpty()) {
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        RescanWithSection(readBy, modelChoices, onRescanWith)
    }
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
        body = "Your plan is fine — we're just busy. $wait " +
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
    GuidanceState(
        title = "We couldn't read your plan just now",
        body = "Reading a plan needs an internet connection. Check you're online and try again — " +
            "or draw your home on the grid, which works completely offline.",
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
            "behind the upload button. It isn't your plan or your phone. Drawing your home on the " +
            "grid works normally and gives exactly the same score.",
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
                    "and gives exactly the same score.",
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
