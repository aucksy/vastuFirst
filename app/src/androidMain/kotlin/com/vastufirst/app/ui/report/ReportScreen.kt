package com.vastufirst.app.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.PlanRoom
import com.vastufirst.app.ui.common.PlanWithRooms
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.app.ui.details.coverageLine
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.common.readingOrder
import com.vastufirst.app.ui.common.roomDisplayNames
import com.vastufirst.app.ui.common.roomStatus
import com.vastufirst.app.ui.common.short
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.toVastu
import com.vastufirst.app.ui.grid.microLabel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.designsystem.components.BalanceMeter
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.LoadingState
import com.vastufirst.designsystem.components.ProvenanceTag
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.TagPill
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuRoomRow
import com.vastufirst.designsystem.components.ZoneMap
import com.vastufirst.designsystem.components.VerdictPill
import com.vastufirst.designsystem.components.VastuVerdict
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.components.scoreOutOfTen
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.Analysis
import com.vastufirst.shared.AnalysisQuality
import com.vastufirst.shared.Defect
import com.vastufirst.shared.Dispute
import com.vastufirst.shared.DoorResult
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PadaVerdict
import com.vastufirst.shared.RoomResult
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.ZoneInfo
import com.vastufirst.app.ui.common.screenRoot
import kotlinx.coroutines.launch

/**
 * Full report (§6.5/§6.6) — branches on intent (§2).
 *
 * ⭐ ONLY "I am building" is offered layout changes, and that is the owner's ruling (v0.6.6). Someone
 * BUYING is looking at a home that is already standing, and someone ALREADY LIVING there cannot
 * rebuild it — telling either of them to move the kitchen is advice they cannot act on, and it
 * crowds out the advice they can. For both, this screen is remedies only.
 *
 * ⭐⭐ REBUILT TO BE READ (9 Aug 2026). The content did not change; the way it is delivered did,
 * because the old one was a twenty-screen wall of prose that opened on a front-door card and never
 * once told the reader how their home had done overall. Four things changed and each is load-bearing:
 *
 *  1. **It opens with a verdict.** Score, band, one sentence — before any finding.
 *  2. **[BalanceMeter] shows what is RIGHT before what is wrong.** A reader with a decent home used
 *     to close this feeling accused, because "already right" sat below every problem.
 *  3. **⛔ The three chapters are GONE (10 Aug 2026, owner) — do not reinstate them.** "Fix first /
 *     already right / good to know" made the reader pick a chapter before they could see their own
 *     home, and put the same room in a different place depending on how it had scored, so "where is
 *     my kitchen?" had three possible answers. [RoomsSection] replaces all three with ONE list of
 *     every room, worst first, each carrying its direction and a single word. The ranking the "fix
 *     first" chapter existed for survives as the sort order — nothing is re-scored and no finding is
 *     dropped; a finding with no room behind it moves to [StructuralSection] rather than vanishing.
 *  4. **Every finding collapses.** The headline, room, zone and verdict are always visible; the whole
 *     reason, the zone's meaning, the layout change and every remedy are one tap down. ⚠ Nothing is
 *     CUT — the Sanskrit, the deities, the provenance on every line and the sentence admitting when
 *     the texts record no remedy are all still there. Cutting the Vastu vocabulary to make this
 *     shorter would remove the only thing worth ₹699.
 *
 * ⭐ FREE vs PAID lives in [FreeTier] and nowhere else. With [unlocked] false, the entrance, kitchen
 * and toilets read in full and every other room still shows its name and verdict with its reasoning
 * locked. The score and the counts are never hidden — see the note in [FreeTier].
 */
@Composable
fun ReportScreen(
    vm: NewPlanViewModel,
    onDone: () -> Unit,
    onUnlock: () -> Unit = {},
    onEditNorth: () -> Unit = {},
    onEditEntry: () -> Unit = {},
    onAddDetails: () -> Unit = {},
    onRestart: () -> Unit = {},
    /** The scanned photograph and its room rectangles, when this home arrived by scan. */
    planImage: ImageBitmap? = null,
    planRooms: List<PlanRoom> = emptyList(),
) {
    // Thin wrapper: the ONLY thing that touches the ViewModel, so the report renders headlessly from
    // fixture state in the screenshot harness (UI-POLISH §6).
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    ReportContent(
        analysis = analysis,
        intent = vm.intent,
        unlocked = vm.unlocked,
        rooms = vm.rooms,
        north = vm.north,
        cols = vm.gridCols,
        rows = vm.gridRows,
        siteAnswers = vm.siteAnswers,
        onUnlock = onUnlock,
        onDone = onDone,
        onEditNorth = onEditNorth,
        onEditEntry = onEditEntry,
        onAddDetails = onAddDetails,
        onRestart = onRestart,
        planImage = planImage,
        planRooms = planRooms,
    )
}

/**
 * ⭐ THE END OF THE DOCUMENT, SO THE HARNESS CAN SCROLL TO IT.
 *
 * ⚠ Scrolling to the last *control* is NOT the same thing and would photograph a lie. The pay bar
 * floats over this column, so bringing "Done — see all my plans" minimally into view lands it at the
 * bottom edge of the window with the bar squarely on top of it — a picture of the very defect this
 * clearance exists to prevent, taken of a screen where it does not happen. The clearance itself is
 * the true last element, so scrolling to THAT is what a reader reaching the end of the page sees.
 */
const val TAG_PAY_CLEARANCE = "report.payClearance"

/**
 * ⭐ THE END OF THE ROOM LIST, SO THE HARNESS CAN SCROLL TO IT — and this tag is the whole reason
 * the list is photographed at all.
 *
 * ⚠ FOUND BY LOOKING, 10 Aug 2026, the first time the list was rendered. Every report golden starts
 * at the top and every bottom-half golden ends at the last control, and the room list sits between
 * them — so on the run that introduced it, the one thing the change was FOR appeared in no picture
 * at any of the eight configurations. That is the "a golden is a viewport, not a document" defect
 * (UI-POLISH §6.4) landing on brand-new UI, and the geometry gate cannot stand in for it: rows
 * shatter by drawing ink wider than a box every number agrees is the right size (§6.7b).
 *
 * Tagged rather than anchored on a room's name because the names come from the fixture's own rooms
 * and would change with it, and an anchor that silently stops matching photographs the wrong place.
 */
const val TAG_ROOMS_END = "report.roomsEnd"

/**
 * How much of the width the zone map takes. Carried over from the score screen unchanged: the dial
 * is a circle, so its width is also its height, and full width made it taller than the fold on a
 * 320 dp phone.
 */
private const val ZONE_MAP_WIDTH_FRACTION = 0.62f

/** Full report as a pure function of its state — no ViewModel — so the render harness can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportContent(
    analysis: Analysis?,
    intent: Intent?,
    unlocked: Boolean = true,
    /**
     * Open every finding at once.
     *
     * ⚠ A testing and photography seam, and a necessary one. Collapsed, a card's reasoning is not in
     * the semantics tree at all — so the test that pins "only someone BUILDING is ever offered a
     * layout change" would find nothing and pass for the wrong reason, and no golden would ever
     * contain an opened card. Both of those are how a paid feature quietly stops working.
     */
    expandAll: Boolean = false,
    /**
     * ⭐ The user's placed rooms and their North — inherited from the free score screen this report
     * REPLACED (owner, 10 Aug 2026: "After the North is marked, jump straight to Report screen").
     * They drive the zone map, which is the only picture of the reader's own home in the flow.
     */
    rooms: List<GridRoom> = emptyList(),
    north: Int = 0,
    cols: Int = GRID,
    rows: Int = GRID,
    /** Drives the honest "what this covers" line — the score's caveat, moved here with it. */
    siteAnswers: SiteAnswers = SiteAnswers(),
    /**
     * ⭐ The user's OWN scanned plan, and the rectangles each room was read from — present only when
     * this home arrived by scan. With them, the picture of the home is the photograph they took and
     * every room on it is tappable; without them (a home drawn by hand) it is the zone map.
     */
    planImage: ImageBitmap? = null,
    planRooms: List<PlanRoom> = emptyList(),
    onUnlock: () -> Unit = {},
    onDone: () -> Unit = {},
    onEditNorth: () -> Unit = {},
    onEditEntry: () -> Unit = {},
    onAddDetails: () -> Unit = {},
    onRestart: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    val resolvedIntent = intent ?: Intent.BUILDING
    val remediesOnly = resolvedIntent != Intent.BUILDING

    // ⭐⭐ THE RECOVERY STATES THAT ARRIVE HERE INSTEAD OF A SPINNER — inherited whole from the free
    // score screen this report REPLACED (owner, 10 Aug 2026: "After the North is marked, jump
    // straight to Report screen"). This screen is now where the flow LANDS, so every dead end the
    // score screen had learned to catch is a dead end a reader can still reach. Removing that screen
    // without carrying these would have turned four honest guidance cards back into the forever
    // spinner they were each written to replace.
    if (analysis == null) {
        Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Draft present AND complete, engine still computing (normal, ~50 ms).
                // ⚠ `intent != null` is part of "complete": without it the engine can never produce a
                // result, so this would be a spinner with no exit — the process-killed session's trap.
                rooms.isNotEmpty() && intent != null -> LoadingState("Reading your home…")
                // Rooms survived a process kill but the first answer did not. Send the reader to
                // answer exactly that, keeping their rooms.
                rooms.isNotEmpty() -> GuidanceState(
                    title = "One answer went missing",
                    body = "Your rooms are safe. The first question — what brings you to Vastu — was lost when the app closed. Answer it again and we'll read your home.",
                    action = { VastuButton("Answer the first question", onClick = onRestart) },
                )
                // Nothing on screen and nothing computed — the phone reclaimed the in-progress plan.
                else -> GuidanceState(
                    title = "Let's pick up where you left off",
                    body = "We couldn't find this plan on screen — it may have closed in the background. Head back to your saved plans to reopen it.",
                    action = { VastuButton("Go to my plans", onClick = onDone) },
                )
            }
        }
        return
    }
    val a = analysis
    if (a.quality == AnalysisQuality.INSUFFICIENT) {
        Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            GuidanceState(
                title = "Let's finish your plan",
                body = a.notes.firstOrNull()?.message
                    ?: "Add a few rooms and your front door, and we'll read your home.",
                action = { VastuButton("Change your plan", onClick = onEditEntry) },
            )
        }
        return
    }

    val zones = a.zoneInfo
    val defects = a.defects
    val notIdeal = a.roomResults.filter { it.verdict == Verdict.SUBOPTIMAL }
    val good = a.roomResults.filter { it.verdict == Verdict.IDEAL || it.verdict == Verdict.ACCEPTABLE }

    // ⚠ Hoisted ABOVE the `when` that swaps chapters (UI-POLISH §3.B). Scroll state declared inside a
    // branch is torn down and recreated every time the branch changes, which silently jumps the
    // reader back to the top — the same defect the room palette had in v0.2.1.
    val scroll = rememberScrollState()

    /**
     * ⭐ THE ONE ROOM THE READER IS LOOKING AT — the whole of the shared behaviour, held here.
     *
     * One tap opens a room AND marks it, because the owner asked for a single behaviour that works
     * the same from the list and from the plan: *"Build a common UI/UX for this room highlight in
     * the list which works the same way if user taps the room in list or room on floor plan"*. Two
     * separate ideas — "which one is open" and "which one is tinted" — is exactly how those two ends
     * drift apart, so there is only one, and the plan above will read this same value.
     *
     * ⚠ rememberSaveable: an open room must survive a rotation and a brief process reclaim, or the
     * reader loses their place mid-report — the same reason the old finding cards used it.
     */
    var openRoomId by rememberSaveable { mutableStateOf<String?>(null) }

    /**
     * Where each room's row currently sits, so tapping the room ON THE PICTURE can bring its row
     * into view. Held in root coordinates and combined with the live scroll position, which makes it
     * independent of how deeply the row is nested inside the page.
     */
    val rowY = remember { mutableStateMapOf<String, Int>() }
    val scope = rememberCoroutineScope()
    val rowMarginPx = with(LocalDensity.current) { VastuTheme.spacing.s6.roundToPx() }

    /**
     * ⭐⭐ THE ONE HANDLER BOTH ENDS CALL — the whole of what the owner asked for: *"Build a common
     * UI/UX for this room highlight in the list which works the same way if user taps the room in
     * list or room on floor plan"*. Tapping a room on the picture and tapping its row do the
     * identical thing, because they are the same function. Two handlers is how that quietly breaks.
     */
    fun tapRoom(id: String) {
        val closing = openRoomId == id
        openRoomId = if (closing) null else id
        if (!closing) {
            rowY[id]?.let { y -> scope.launch { scroll.animateScrollTo((scroll.value + y - rowMarginPx).coerceAtLeast(0)) } }
        }
    }

    // ⚠ The pay bar's own MEASURED height, not a guess at it. It is two lines of text plus a 52 dp
    // button, so at 200 % font it stands about 230 dp tall — three and a half times the fixed 64 dp
    // of clearance an earlier draft reserved, which left it sitting squarely on top of "Done — see
    // all my plans". A bottom bar that covers the last control is the unreachable-CTA defect
    // UI-POLISH §3.B exists to prevent, and a constant can never track a bar that grows with the
    // reader's font size. Seen in the 200 % golden before it shipped.
    // ⚠ Held in PIXELS, not Dp. A zero-valued Dp literal is still a raw literal, and
    // check-tokens.sh rightly fails the build for one outside the theme package — it greps the
    // source text, so even naming it in a comment trips it. An Int carries no literal at all and
    // converts at the use site.
    val density = LocalDensity.current
    var payBarPx by remember { mutableIntStateOf(0) }

    Box(Modifier.screenRoot(colors.paper)) {
        Column(
            modifier = Modifier
                .verticalScroll(scroll)
                .padding(VastuTheme.spacing.s6),
        ) {
            // FlowRow, not Row: at 200 % font the label and the intent pill are together wider than
            // the screen, and a Row draws them into each other.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
            ) {
                SectionLabel(
                    if (unlocked) "Full report" else "Your report",
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                IntentBadge(resolvedIntent)
            }

            Spacer(Modifier.height(VastuTheme.spacing.s4))
            VerdictHeader(a.score, defects.size, remediesOnly, unlocked)

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            BalanceMeter(
                right = good.size,
                notIdeal = notIdeal.size,
                needsFixing = defects.size,
            )

            if (a.notes.isNotEmpty()) {
                Spacer(Modifier.height(VastuTheme.spacing.s4))
                NotesStrip(a.notes)
            }

            // ⭐ THE PICTURE OF THEIR OWN HOME, and the way back into it — both inherited from the
            // score screen this report replaced. The buttons sit directly under the picture on
            // purpose: that picture is where someone NOTICES something is wrong (a room facing the
            // wrong way, North pointing the wrong way), and it is where they should be able to say so.
            //
            // ⚠ NORTH AND THE FRONT DOOR ONLY — never the rooms (owner, 10 Aug 2026: "allow them to
            // change North or Main Entry but not the room because that approach is gone"). The
            // room-by-room editor left the scan flow on 6 Aug; offering a way back into it from here
            // would be a control leading to a screen this flow no longer has.
            if (rooms.isNotEmpty() || planImage != null) {
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                SectionLabel("Your home, as we read it")
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                // ⭐⭐ THE PHOTOGRAPH WINS WHEN WE HAVE ONE. A scanned home's picture of record is
                // the sheet the reader photographed — this screen never redraws it — and every room
                // on it is tappable, which is the other half of the shared highlight behaviour: the
                // SAME handler the list rows call, so the two ends cannot drift apart. A home drawn
                // by hand has no photograph, so it keeps the zone map.
                //
                // ⚠ One picture, not two. Showing the photo AND the zone map would be two drawings
                // of the same home stacked on the screen the owner asked to make shorter.
                if (planImage != null && planRooms.isNotEmpty()) {
                    PlanWithRooms(
                        image = planImage,
                        rooms = planRooms,
                        selectedId = openRoomId,
                        onTapRoom = { id -> tapRoom(id) },
                        maxHeight = VastuTheme.sizes.planPane,
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ZoneMap(
                            model = buildZoneMapModel(rooms, a, north, cols, rows),
                            modifier = Modifier.fillMaxWidth(ZONE_MAP_WIDTH_FRACTION),
                            showLabels = false,
                            contentDescription = "Your plan with Vastu zones, North at $north degrees.",
                        )
                    }
                }
                Spacer(Modifier.height(VastuTheme.spacing.s4))
                VText(
                    "Not quite right? Change it — your score and this report follow.",
                    style = VastuTheme.type.bodySm, color = colors.textSecondary,
                )
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                VastuButton(
                    "Change which way North is",
                    onClick = onEditNorth,
                    style = VastuButtonStyle.SECONDARY,
                    large = false,
                    modifier = Modifier.testTag("report.edit.north"),
                )
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                VastuButton(
                    "Change where the front door is",
                    onClick = onEditEntry,
                    style = VastuButtonStyle.SECONDARY,
                    large = false,
                    modifier = Modifier.testTag("report.edit.entry"),
                )
            }

            // ⭐ START HERE — the one thing to do first. The old report ranked its problems but never
            // said "begin with this", so a reader facing eight cards had to work out the entry point
            // themselves. Always free: it is the single most useful sentence on the screen.
            defects.firstOrNull()?.let { top ->
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                StartHere(top, a.roomResults, remediesOnly)
            }

            // ---- one list, not three chapters ------------------------------------------------
            // ⭐ THE THREE CHAPTERS ARE GONE (owner, 10 Aug 2026, with a picture of what replaces
            // them). "Fix first / Already right / Good to know" made the reader choose a chapter
            // before they could see their own home, and the same room could only ever appear in one
            // of them — so "where is my kitchen?" was a question the report answered in three
            // different places depending on how the kitchen had scored.
            //
            // One list of every room, worst first, each carrying its direction and a single word.
            // The ranking the "fix first" chapter existed for is still here: it is the sort order.
            Spacer(Modifier.height(VastuTheme.spacing.s6))
            a.doorResult?.let { DoorSection(it, zones, remediesOnly) }

            RoomsSection(
                rooms = a.roomResults,
                defects = defects,
                zones = zones,
                unlocked = unlocked,
                remediesOnly = remediesOnly,
                expandAll = expandAll,
                openRoomId = openRoomId,
                onTapRoom = ::tapRoom,
                onRowPlaced = { id, y -> rowY[id] = y },
            )

            // ⚠ A defect with no room behind it — a cut corner, an extension, a water tank, the
            // centre of the home — cannot ride on a room row, and dropping it would delete a real
            // finding from the report. It gets its own section, kept below the rooms because it is
            // rarer and harder to act on.
            val structural = defects.filter { it.roomId == null }
            if (structural.isNotEmpty()) {
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                StructuralSection(structural, a.roomResults, zones, unlocked, remediesOnly, expandAll)
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            DisputesSection(a.disputes)

            if (a.notChecked.isNotEmpty()) {
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                NotCheckedSection(a.notChecked)
            }

            // ⭐ WHAT THE READING ACTUALLY LOOKED AT — the score screen's honesty line, moved here
            // with everything else it owned. The score has only ever come from rooms, the front door
            // and the shape, but it reads as a complete verdict when it is really a CEILING: a home
            // with its water tank in the worst possible corner scored the same as one with it in the
            // best, because nothing ever asked. Saying so is not optional for a paid product, and the
            // sentence comes with the way to close the gap.
            //
            // ⚠ The old wording sent the reader to "the score screen" for those extra questions. That
            // screen no longer exists, so the sentence would have pointed at nothing — the button
            // below IS the way there now.
            Spacer(Modifier.height(VastuTheme.spacing.s6))
            SectionLabel("What this covers")
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(coverageLine(siteAnswers), style = VastuTheme.type.body, color = colors.textSecondary)
            if (siteAnswers.answeredCount < SiteItem.entries.size) {
                Spacer(Modifier.height(VastuTheme.spacing.s3))
                VastuButton(
                    "Answer a few more and check more",
                    onClick = onAddDetails,
                    style = VastuButtonStyle.SECONDARY,
                    large = false,
                )
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            Box(
                Modifier.fillMaxWidth().clip(VastuTheme.shapes.md)
                    .background(colors.surface).padding(VastuTheme.spacing.s4),
            ) {
                VText(
                    "Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome. " +
                        "The score is our own summary, not a measurement and not part of the tradition.",
                    style = VastuTheme.type.body, color = colors.textPrimary,
                )
            }

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            VastuButton("Done — see all my plans", onClick = onDone)

            // Clearance equal to the bar that overlaps this column, so the last control always
            // clears it at every font size. Tagged because it is the document's true last element —
            // see [TAG_PAY_CLEARANCE].
            // ⚠ fillMaxWidth is load-bearing, not decoration. A tag turns this Spacer into a real
            // semantics node, and a bare Spacer given only a height measures ZERO WIDE — which the
            // geometry gate fails on sight, on every report screen at once.
            Spacer(
                Modifier
                    .testTag(TAG_PAY_CLEARANCE)
                    .fillMaxWidth()
                    .height(VastuTheme.spacing.s4 + with(density) { payBarPx.toDp() }),
            )
        }

        if (!unlocked) {
            PayBar(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { payBarPx = it.height },
                a,
                onUnlock,
            )
        }
    }
}

/* ─────────────────────────── the opening verdict ─────────────────────────── */

/**
 * Score, band and one sentence — the first thing on the screen.
 *
 * The old report opened straight into the front-door card, so the one question a reader actually
 * arrived with ("is my home all right?") was never answered on the screen they paid for.
 */
// ⚠ Its own opt-in. FlowRow is experimental and the annotation does NOT travel from the caller —
// ReportContent having it is exactly why this compiled in my head and not on the runner.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictHeader(score: Int, defectCount: Int, remediesOnly: Boolean, unlocked: Boolean) {
    val colors = VastuTheme.colors
    val mark = LocalDecimalMark.current
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        // Baseline-aligned so "6.4" and "/ 10" sit on one line; FlowRow so the band word drops to its
        // own line at 200 % font rather than squeezing the number.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VText(
                scoreOutOfTen(score, mark),
                style = VastuTheme.type.display,
                color = scoreBandColor(score),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            VText(
                "/ 10",
                style = VastuTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TagPill(
                text = bandWord(score),
                color = scoreBandColor(score),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
        VText(verdictSentence(score, defectCount, remediesOnly, unlocked), style = VastuTheme.type.body, color = colors.textSecondary)
    }
}

private fun bandWord(score: Int): String = when {
    score >= 75 -> "Strong"
    score >= 50 -> "Workable"
    else -> "Needs work"
}

/**
 * One honest sentence about the whole home. It leads with what is working, because the counts
 * immediately below break the same home down and a reader needs the shape before the detail.
 */
private fun verdictSentence(score: Int, defectCount: Int, remediesOnly: Boolean, unlocked: Boolean): String {
    val head = when {
        score >= 75 && defectCount == 0 -> "This home reads well throughout, with nothing the tradition counts as a defect."
        score >= 75 -> "Most of this home reads well."
        score >= 50 -> "This home reads workably, with real problems worth addressing."
        else -> "Several core placements work against this home."
    }
    val tail = when {
        defectCount == 0 -> ""
        remediesOnly -> " Everything below can be done without moving a wall."
        else -> " Nothing is built yet, so every change below is still free to make."
    }
    val free = if (unlocked) "" else " Your entrance, kitchen and toilets are below in full, free."
    return head + tail + free
}

/* ─────────────────────────── advice, filtered by who is reading ─────────────────────────── */

/**
 * ⭐⭐ THE REMEDIES THIS READER CAN ACTUALLY ACT ON.
 *
 * ⚠ FOUND BY LOOKING AT THE RENDERED FREE REPORT, 9 Aug 2026 — not by any gate, and not by the test
 * written to prevent exactly this.
 *
 * The rule data attaches a `MOVE_IT` remedy to defects — *"Move or resize the element on the drawing
 * so it leaves the wrong zone — free while the plan is still on paper"* — and it carries **rank 0**,
 * so it sorts FIRST. That is layout advice. Someone BUYING a built flat, or already living in one,
 * cannot act on it, and the owner's v0.6.6 ruling is that they are shown remedies and nothing else.
 *
 * It had been reaching them since the report was written: the remedy block simply printed whatever
 * `remediesFor` returned. `ReportIntentTest` did not catch it because it bans four phrases —
 * "Change the layout", "renovate", "still free to make", "Nothing is built yet" — and this sentence
 * happens to use none of them. The rebuild then promoted it to the "Do this first" headline of the
 * free screen, which is where it finally became visible.
 *
 * So the filter lives here, once, and the test now bans the sentence itself.
 */
private fun advisableRemedies(d: Defect, remediesOnly: Boolean): List<com.vastufirst.shared.Remedy> =
    if (remediesOnly) d.remedies.filter { it.kind != com.vastufirst.shared.FixKind.MOVE_IT } else d.remedies

/* ─────────────────────────── start here ─────────────────────────── */

@Composable
private fun StartHere(d: Defect, rooms: List<RoomResult>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = colors.verdictDefect, background = colors.surfaceRaised) {
        SectionLabel("Start here", color = colors.verdictDefect)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(defectTitle(d, rooms), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // ⚠ "Of the problems ranked below", NOT "of everything below" — the wider claim is one
            // this report cannot make. The front door is scored too, at the ENTRANCE weight, which
            // is the heaviest single weight there is; a defect instead adds a severity penalty. The
            // engine never puts those two on one scale, so nothing here knows whether an
            // unfavourable door outranks the worst defect. Ranking only what is actually ranked is
            // the honest sentence, and it stayed honest when the door moved into this chapter.
            "Of the problems ranked below, this is the one that moves your score most.",
            style = VastuTheme.type.bodySm, color = colors.textSecondary,
        )
        val first = if (remediesOnly) {
            advisableRemedies(d, true).firstOrNull()?.let { remedyLine(it) }
        } else {
            d.layoutFix
        }
        if (!first.isNullOrBlank()) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Column(
                Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
                    .background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
            ) {
                SectionLabel("Do this first", color = colors.secondaryText)
                VText(first, style = VastuTheme.type.bodySm, color = colors.textPrimary)
            }
        }
    }
}

/* ─────────────────────────── the sections ─────────────────────────── */

/**
 * The front door's own block — first, always, and always free.
 *
 * ⭐ It leads the report because it is the highest-weighted single element in the whole reading
 * ([DOOR_IS_FREE]), and it now has ONE home rather than being filed into whichever chapter matched
 * its verdict. That filing existed to stop an unfavourable door leading a chapter called "already
 * right" — a problem the chapters created and their removal takes away with them.
 */
@Composable
private fun DoorSection(door: DoorResult, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    SectionLabel("Your front door")
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    DoorCard(door, zones, remediesOnly)
    Spacer(Modifier.height(VastuTheme.spacing.s6))
}

/**
 * ⭐⭐ EVERY ROOM, ONCE, WORST FIRST — the list the owner drew.
 *
 * Each row is the room's kind, the direction it sits in, and one word: **Review**, **Aligned** or
 * **Not rated**. Tapping it opens that room's whole reasoning, and marks it on the plan above.
 *
 * ⚠ WHAT THIS MUST NEVER DO, and the reason the mapping below is written out room by room rather
 * than filtered: **lose a finding.** The three chapters between them showed every defect, every
 * "not ideal" room and every already-right room. This shows every ROOM — so a room carrying a
 * defect must open onto the DEFECT's reasoning (its remedies, its provenance, its rank), not onto
 * the milder room-level explanation. A room with more than one defect shows all of them. The only
 * finding that cannot live on a room row is one with no room behind it, and that has its own
 * section ([StructuralSection]) rather than being dropped.
 *
 * ⚠ The free tier is unchanged and still decided in ONE place ([FreeTier]): entrance, kitchen and
 * toilets read in full, every other room still named with its verdict and its direction, reasoning
 * behind the price. A locked row is not a blurred teaser — Product PRD §6.4.
 */
@Composable
private fun RoomsSection(
    rooms: List<RoomResult>,
    defects: List<Defect>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    remediesOnly: Boolean,
    expandAll: Boolean,
    openRoomId: String?,
    onTapRoom: (String) -> Unit,
    onRowPlaced: (String, Int) -> Unit,
) {
    val colors = VastuTheme.colors
    if (rooms.isEmpty()) return

    // Defects keyed by the room they belong to, so a room row can open onto its own finding.
    val defectsByRoom = defects.filter { it.roomId != null }.groupBy { it.roomId }

    // ⚠ The display name is worked out over the WHOLE list before sorting, so "Bedroom 2" is the
    // second bedroom on the plan — not the second one that happened to score badly.
    val names = roomDisplayNames(rooms.map { it.type })
    val ordered = rooms
        .mapIndexed { i, r -> r to names[i] }
        .sortedBy { (r, _) -> r.verdict.roomStatus().readingOrder() }

    SectionLabel("Your rooms (${rooms.size})")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Worst first. Tap a room to see where it sits and why.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        ordered.forEachIndexed { i, (r, name) ->
            val roomDefects = defectsByRoom[r.roomId].orEmpty()
            val free = unlocked || r.isFreeToRead()
            // The first readable row starts open, so a reader meets the depth of this report at once
            // instead of a column of shut rows they have to guess their way into.
            val open = expandAll || openRoomId == r.roomId || (openRoomId == null && i == 0 && free)
            VastuRoomRow(
                name = name,
                code = r.type.microLabel(),
                codeColor = r.type.editorColor(),
                // ⚠ Capitalised HERE, not in [short], which also feeds running prose where "the
                // centre" must stay lowercase ("Toilet — centre"). A pill is a label, not a sentence.
                direction = r.zone.short().replaceFirstChar { it.uppercase() },
                status = r.verdict.roomStatus(),
                selected = open,
                expanded = open,
                modifier = Modifier.onGloballyPositioned { onRowPlaced(r.roomId, it.positionInRoot().y.toInt()) },
                onTap = { onTapRoom(r.roomId) },
                body = {
                    if (!free) {
                        VText(
                            "Reasoning and remedies — in the full report",
                            style = VastuTheme.type.bodySm, color = colors.textTertiary,
                        )
                    } else if (roomDefects.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                            roomDefects.forEach { DefectBody(it, zones, remediesOnly) }
                        }
                    } else {
                        RoomBody(
                            r,
                            zones,
                            when (r.verdict) {
                                Verdict.SUBOPTIMAL -> whyNotIdeal(r)
                                Verdict.NOT_SCORED -> NOT_RATED_REASON
                                else -> whyRight(r)
                            },
                        )
                    }
                },
            )
        }
    }
    // The list's true last element, so a scrolled capture lands on the tail of the list rather than
    // minimally into its first row. ⚠ fillMaxWidth is load-bearing: a tag makes this a real semantics
    // node, and a node given only a height measures ZERO WIDE, which the geometry gate fails on sight.
    Spacer(
        Modifier
            .testTag(TAG_ROOMS_END)
            .fillMaxWidth()
            .height(VastuTheme.spacing.s1),
    )
}

/** Said once, on every room the tradition does not place — the report's existing wording. */
private const val NOT_RATED_REASON: String =
    "The tradition does not say where this kind of room belongs, so we have not judged it. " +
        "It is not a problem and it is not an approval — it simply is not covered."

/**
 * Findings with no room behind them — a cut corner, an extension, a fixture, the centre of the home.
 *
 * ⚠ These are real defects and they were previously ranked in "fix first" alongside the room ones.
 * They keep their ranking and their full reasoning; only their place on the page has changed.
 */
@Composable
private fun StructuralSection(
    structural: List<Defect>,
    rooms: List<RoomResult>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    remediesOnly: Boolean,
    expandAll: Boolean,
) {
    val colors = VastuTheme.colors
    SectionLabel("Your home's shape and surroundings")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "These are about the building itself, not any one room.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        structural.forEachIndexed { i, d ->
            val free = unlocked || d.isFreeToRead(rooms)
            FindingRow(
                title = defectTitle(d, rooms),
                meta = zoneLine(d.zone, zones),
                accent = if (remediesOnly) colors.secondary else colors.verdictDefect,
                verdict = VastuVerdict.DEFECT,
                rank = i + 1,
                locked = !free,
                startOpen = expandAll,
            ) {
                DefectBody(d, zones, remediesOnly)
            }
        }
    }
}

@Composable
private fun NotCheckedSection(notChecked: List<com.vastufirst.shared.NotChecked>) {
    val colors = VastuTheme.colors
    SectionLabel("We could not check these")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Neither passed nor failed — we did not have the details.",
        style = VastuTheme.type.bodySm, color = colors.textTertiary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        notChecked.forEach {
            VText("· ${notCheckedLine(it)}", style = VastuTheme.type.bodySm, color = colors.textSecondary)
        }
        notCheckedHow(notChecked).forEach {
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
    }
}

/* ─────────────────────────── the expandable finding ─────────────────────────── */

/**
 * One finding: always shows what it is and how it read; opens to the whole reasoning.
 *
 * ⚠ [rememberSaveable], not [androidx.compose.runtime.remember] — an open card must survive a
 * rotation and a brief process reclaim, or a reader loses their place mid-report.
 *
 * ⚠ A [locked] row is NOT a blurred teaser. It still names the room, the zone and the verdict,
 * because the free score already counts it and hiding that would be the bait Product PRD §6.4
 * forbids. Only the reasoning is behind the price, and the row says exactly that.
 */
@Composable
private fun FindingRow(
    title: String,
    meta: String?,
    accent: Color,
    verdict: VastuVerdict,
    rank: Int?,
    locked: Boolean,
    startOpen: Boolean = false,
    body: @Composable () -> Unit,
) {
    val colors = VastuTheme.colors
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }

    VastuCard(accent = if (locked) colors.borderStrong else accent, background = colors.surfaceRaised) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickableTap(
                    role = Role.Button,
                    onClickLabel = if (locked) null else if (open) "collapse this" else "read the whole reason",
                    enabled = !locked,
                ) { open = !open },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VerdictPill(verdict)
                if (rank != null) VText("#$rank", style = VastuTheme.type.caption, color = colors.textTertiary)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(title, style = VastuTheme.type.h3, color = colors.textPrimary)
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(VastuTheme.spacing.s1))
                VText(meta, style = VastuTheme.type.bodySm, color = colors.textTertiary)
            }
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            // ⚠ NOT "Tap to read…". Google's ATF RedundantDescriptionCheck fires on it, and rightly:
            // TalkBack already announces the role and the gesture, so a visible "tap to" makes a
            // screen-reader user hear the instruction twice. UI-POLISH says the same thing for
            // sighted readers — name the OUTCOME, never the gesture. The chevron carries the
            // affordance; `onClickLabel` above carries it for the screen reader.
            VText(
                when {
                    locked -> "Reasoning and remedies — in the full report"
                    open -> "Close  ⌃"
                    else -> "The whole reason  ⌄"
                },
                style = VastuTheme.type.caption,
                color = if (locked) colors.textTertiary else colors.primaryDark,
            )
        }
        if (open && !locked) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            Box(Modifier.fillMaxWidth().height(VastuTheme.borders.regular).background(colors.borderDefault))
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            body()
        }
    }
}

/** The whole of a defect's reasoning — unchanged in substance from the first paid report. */
@Composable
private fun DefectBody(d: Defect, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    ProvenanceTag(d.provenance.toVastu())
    zoneMeaning(d.zone, zones)?.let {
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
    }
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(d.explanation, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    // ⭐ Each remedy carries its OWN provenance, not the defect's — a rite from the Mayamatam and a
    // 20th-century rock-salt bowl can sit two lines apart.
    val remedies = advisableRemedies(d, remediesOnly).map { remedyLine(it) }
    if (remediesOnly) {
        RemedyBlock(remedies, d.remedyNote)
    } else {
        d.layoutFix?.let { LayoutBlock(it); Spacer(Modifier.height(VastuTheme.spacing.s2)) }
        RemedyBlock(remedies, d.remedyNote)
    }
}

@Composable
private fun RoomBody(r: RoomResult, zones: List<ZoneInfo>, reason: String) {
    val colors = VastuTheme.colors
    r.rule?.provenance?.let {
        ProvenanceTag(it.toVastu())
        Spacer(Modifier.height(VastuTheme.spacing.s2))
    }
    zoneMeaning(r.zone, zones)?.let {
        VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
    }
    if (reason.isNotBlank()) {
        VText(reason, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

/** The one-line zone identity under a finding's title — Sanskrit name and what it governs. */
private fun zoneLine(zone: com.vastufirst.shared.Zone, zones: List<ZoneInfo>): String? =
    zones.firstOrNull { it.zone == zone }?.let { info ->
        listOfNotNull(info.sanskrit, info.deity).joinToString(" · ").takeIf { it.isNotBlank() }
    }

/* ─────────────────────────── the pay bar ─────────────────────────── */

/**
 * Sticky, and it names what is still locked rather than shouting a price.
 *
 * ⚠ It sits over the scroll, so the column above reserves clearance for it — a bottom bar that
 * covers the last control is the unreachable-CTA defect UI-POLISH §3.B exists to prevent.
 */
@Composable
private fun PayBar(modifier: Modifier, a: Analysis, onUnlock: () -> Unit) {
    val colors = VastuTheme.colors
    val locked = a.lockedCount()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .border(VastuTheme.borders.regular, colors.borderDefault)
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        VText(
            if (locked > 0) "$locked more findings, with the reason and remedies for each"
            else "The whole reading, with the reason and remedies for each finding",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        VastuButton(
            "Unlock the full report",
            onClick = onUnlock,
            modifier = Modifier.testTag("report.unlock"),
        )
    }
}

/* ─────────────────────────── unchanged pieces ─────────────────────────── */

/** The front door, read on the 32-position table the tradition actually uses. */
@Composable
private fun DoorCard(d: DoorResult, zones: List<ZoneInfo>, remediesOnly: Boolean) {
    val colors = VastuTheme.colors
    VastuCard(accent = padaAccent(d.verdict), background = colors.surfaceRaised) {
        TagPill(text = padaBadge(d.verdict), color = padaAccent(d.verdict))
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorTitle(d), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorPlaceLine(d), style = VastuTheme.type.body, color = colors.textPrimary)
        doorUnnamedNote(d)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        zoneMeaning(d.pada.side, zones)?.let {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(it, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(doorExplanation(d, remediesOnly), style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

@Composable
private fun padaAccent(v: PadaVerdict) = with(VastuTheme.colors) {
    when (v) {
        PadaVerdict.AUSPICIOUS -> verdictIdeal
        PadaVerdict.MODERATE -> verdictAcceptable
        PadaVerdict.MIXED -> verdictSuboptimal
        PadaVerdict.INAUSPICIOUS -> verdictDefect
    }
}

/** Only ever drawn for someone still BUILDING — see `remediesOnly` at the top of this file. */
@Composable
private fun LayoutBlock(text: String) = AdviceBlock("✦ Change the layout — free now", text, VastuTheme.colors.primary)

/**
 * The remedies for THIS problem — and, where the classical texts record none, the sentence that says
 * so. Filling the table with an invented remedy would destroy the one thing this product is for.
 */
@Composable
private fun RemedyBlock(remedies: List<String>, note: String?) {
    val colors = VastuTheme.colors
    if (remedies.isEmpty() && note.isNullOrBlank()) return
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
            .background(colors.secondary.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel("If it cannot move — remedies", color = colors.secondaryText)
        if (!note.isNullOrBlank()) VText(note, style = VastuTheme.type.bodySm, color = colors.textTertiary)
        remedies.forEach { VText("· $it", style = VastuTheme.type.bodySm, color = colors.textSecondary) }
    }
}

@Composable
private fun AdviceBlock(heading: String, text: String, accent: Color) {
    val colors = VastuTheme.colors
    Column(
        Modifier.fillMaxWidth().clip(VastuTheme.shapes.sm)
            .background(accent.copy(alpha = 0.10f)).padding(VastuTheme.spacing.s3),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1),
    ) {
        SectionLabel(heading, color = accent)
        VText(text, style = VastuTheme.type.bodySm, color = colors.textPrimary)
    }
}

@Composable
private fun ReadingRow(label: String, text: String) {
    val colors = VastuTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s1)) {
        VText(label, style = VastuTheme.type.label, color = colors.textPrimary)
        VText(text, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

@Composable
private fun IntentBadge(intent: Intent) {
    val colors = VastuTheme.colors
    val (text, color) = when (intent) {
        Intent.BUILDING -> "BUILDING" to colors.verdictIdeal
        Intent.BUYING -> "BUYING" to colors.info
        Intent.LIVING -> "ALREADY LIVING HERE" to colors.secondary
    }
    TagPill(text = text, color = color)
}

/**
 * ⭐ "WHERE THE SCHOOLS DISAGREE" — both readings, and which one the number uses.
 *
 * ⚠ Public, and rendered on its own in the screenshot harness, for a reason. It sits at the bottom of
 * a long document, so no full-screen golden has ever contained it — a golden is a viewport, not a
 * document. It also carries the product's whole promise: we have ruled on some of these questions
 * and we still show the reader both sides.
 */
@Composable
fun DisputesSection(disputes: List<Dispute>) {
    if (disputes.isEmpty()) return
    val colors = VastuTheme.colors
    SectionLabel("Where the schools disagree")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Both readings, no winner declared — and which one your score follows.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    Spacer(Modifier.height(VastuTheme.spacing.s3))
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
        disputes.forEach { disp ->
            VastuCard(accent = colors.provenanceDisp, background = colors.surfaceRaised) {
                VText(disp.title, style = VastuTheme.type.h3, color = colors.textPrimary)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingA.label, disp.readingA.text)
                Spacer(Modifier.height(VastuTheme.spacing.s2))
                ReadingRow(disp.readingB.label, disp.readingB.text)
                // ⭐ Where the NUMBER stands, on the disputes we have ruled on. Showing both readings
                // and staying silent about which one moved the score would be a half-truth. Absent on
                // every dispute the score genuinely skips, so it never claims a position we lack.
                disp.howWeScore?.let {
                    Spacer(Modifier.height(VastuTheme.spacing.s2))
                    ReadingRow("What your score uses", it)
                }
            }
        }
    }
}
