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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.app.ui.common.NotesStrip
import com.vastufirst.app.ui.common.PlanRoom
import com.vastufirst.app.ui.common.PlanWithRooms
import com.vastufirst.app.ui.common.buildZoneMapModel
import com.vastufirst.app.ui.common.defectTitle
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.details.SiteAnswers
import com.vastufirst.app.ui.details.SiteItem
import com.vastufirst.app.ui.details.addDetailsLabel
import com.vastufirst.app.ui.details.coverageLine
import com.vastufirst.app.ui.newplan.GRID
import com.vastufirst.app.ui.newplan.GridRoom
import com.vastufirst.app.ui.common.readingOrder
import com.vastufirst.app.ui.common.roomDisplayNames
import com.vastufirst.app.ui.common.roomNamesById
import com.vastufirst.app.ui.common.NOT_RATED_MEANS
import com.vastufirst.app.ui.common.rowStatus
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
import com.vastufirst.designsystem.components.VastuRoomStatus
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
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.Verdict
import com.vastufirst.shared.ZoneInfo
import com.vastufirst.app.ui.common.screenRoot
import kotlinx.coroutines.delay

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
    /**
     * ⭐ How long the "reading your home" bar runs before the report appears — see the same
     * parameter on [ReportContent].
     *
     * ⚠ ZERO when the reader OPENED a home from their saved list (owner, 17 Aug 2026). The beat
     * exists to cover the hand-off at the end of the drawing flow, where the engine finishes in
     * about fifty milliseconds and a reader who tapped "read my home" would otherwise see a flash.
     * A home that was read last week is not being read again, and saying it is makes the app look
     * slower than it is.
     */
    introMillis: Long = READING_MILLIS,
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
        introMillis = introMillis,
    )
}

/**
 * The beat between "read my home" and the report. Long enough to read the line above the bar, short
 * enough that nobody taps again thinking it missed them.
 */
const val READING_MILLIS = 1600L

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
    /**
     * ⭐ How long the "reading your home" animation runs before the report appears, and whether the
     * score counts up when it does (owner, 10 Aug 2026: "let there be some animation with progress
     * bar before we show the score.. and the let score populate with animation").
     *
     * ⚠ DEFAULT ZERO, and that is deliberate. A screenshot harness photographs a screen the instant
     * it settles: with an animation running, every golden in the matrix would either capture a
     * progress bar instead of the report, or a score frozen at 0.0 partway through counting. Zero
     * means "no animation" and is what every test uses; the real screen passes the real duration,
     * and the animation gets its OWN golden through [ReadingProgress] so it is still photographed.
     *
     * ⚠ It is also a DELIBERATE PAUSE. The engine finishes in about 50 milliseconds, so without this
     * the reader would see a flash and nothing else. It is not covering up slow work.
     */
    introMillis: Long = 0L,
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
                // ⚠ Copy cut 11 Aug 2026 (451 → 375 words of our own prose). Both claims survive:
                // the rooms are safe, and the one thing missing is the first question.
                rooms.isNotEmpty() -> GuidanceState(
                    title = "One answer went missing",
                    body = "Your rooms are safe. Tell us again what brings you to Vastu.",
                    action = { VastuButton("Answer the first question", onClick = onRestart) },
                )
                // Nothing on screen and nothing computed — the phone reclaimed the in-progress plan.
                else -> GuidanceState(
                    title = "Pick up where you left off",
                    // ⚠ Names the plan rather than opening on "It". Nothing above this sentence on
                    // this card says what "it" is.
                    body = "This plan may have closed in the background. Open it from your saved plans.",
                    action = { VastuButton("Go to my plans", onClick = onDone) },
                )
            }
        }
        return
    }
    val a = analysis

    // ⭐ THE READING ANIMATION. Held here rather than on its own route so the report is already
    // composed behind it — the reader watches a progress bar and then sees their own home, with no
    // second navigation and no chance of landing back on it with the Back button.
    var reading by remember(a) { mutableStateOf(introMillis > 0L) }
    if (reading) {
        LaunchedEffect(a) { delay(introMillis); reading = false }
        ReadingProgress(introMillis)
        return
    }

    if (a.quality == AnalysisQuality.INSUFFICIENT) {
        Box(
            Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6),
            contentAlignment = Alignment.Center,
        ) {
            GuidanceState(
                title = "Finish your plan",
                body = a.notes.firstOrNull()?.message
                    ?: "Add rooms and your front door, and we'll read it.",
                action = { VastuButton("Change your plan", onClick = onEditEntry) },
            )
        }
        return
    }

    val zones = a.zoneInfo
    val defects = a.defects
    /**
     * ⭐⭐ WHAT THIS REPORT CALLS EACH ROOM — the words the reader's own plan prints, when it was
     * scanned from one (owner, 11 Aug 2026: his picture showed "MASTER BEDROOM 1" and the report
     * said "Master"). A home drawn by hand has no printed captions and every row falls back to its
     * numbered kind, exactly as before.
     */
    val roomNames = remember(rooms) { roomNamesById(rooms) }
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
    val rowMarginPx = with(LocalDensity.current) { VastuTheme.spacing.s6.roundToPx() }

    /**
     * ⭐⭐ BOTH ENDS OPEN THE SAME ROOM THE SAME WAY — the owner's rule: *"Build a common UI/UX for
     * this room highlight in the list which works the same way if user taps the room in list or room
     * on floor plan"*. It holds: [tapRoom] is the whole of "open this room", and both ends call it.
     *
     * ⚠ ONLY THE SCROLL DIFFERS, AND IT MUST — the identical amendment the check-what-we-read screen
     * carries, arriving here for the identical reason (owner, 17 Aug 2026: *"Same issue with list
     * auto-scrolling on 'Your Report' screen also"*). Scrolling is not part of opening a room; it is
     * how the PICTURE reaches a row that is off the screen. A row the reader has just put their
     * finger on is already under it, and moving it is the jump he reported. Do not merge these back.
     */
    fun tapRoom(id: String) {
        val closing = openRoomId == id
        openRoomId = if (closing) null else id
    }

    /**
     * ⭐⭐ TAPPING A ROOM **ON THE PICTURE** — open it, then bring its row into view.
     *
     * ⚠ THE TARGET IS READ AFTER THE PAGE HAS MOVED, NOT BEFORE (owner, 17 Aug 2026: *"after
     * unlocking the full report, tapping a room on the floor plan is not scrolling down to correct
     * position"*). Opening a room does two things to this page at once: the newly opened row grows
     * by its whole reasoning, and the row that WAS open shrinks back to a heading. Every row below
     * the shrinking one therefore moves, and the position recorded for the room we are scrolling to
     * was measured before any of that happened. The page settles somewhere else.
     *
     * ⚠ AND IT ONLY LOOKED WRONG ONCE PAID, which is why it survived. Locked, a room opens onto a
     * single grey sentence, so the error is a few dp. Unlocked it opens onto provenance, the zone's
     * meaning, the whole explanation, the layout change and every remedy — hundreds of dp of it —
     * and the reader lands a screen and a half away from the room they tapped.
     *
     * So the id is parked here and the scroll is done by the effect below, one frame later, from
     * the position the row actually ended up at.
     */
    var revealRoomId by remember { mutableStateOf<String?>(null) }
    fun tapRoomOnPlan(id: String) {
        val opening = openRoomId != id
        tapRoom(id)
        revealRoomId = if (opening) id else null
    }
    LaunchedEffect(revealRoomId) {
        val id = revealRoomId ?: return@LaunchedEffect
        // ⚠ Wait for a frame to be drawn. onGloballyPositioned reports after LAYOUT, and a
        // LaunchedEffect body runs after COMPOSITION — so reading the map immediately would read
        // exactly the stale numbers this exists to avoid. One frame is all it takes.
        withFrameNanos { }
        rowY[id]?.let { y ->
            scroll.animateScrollTo((scroll.value + y - rowMarginPx).coerceAtLeast(0))
        }
        revealRoomId = null
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
            // ⚠ The number counts up ONLY when the animation ran. In a still photograph a counting
            // number is a number caught mid-count, and every golden would show a score that is not
            // this home's score.
            VerdictHeader(a.score, defects.size, remediesOnly, unlocked, countUp = introMillis > 0L)

            Spacer(Modifier.height(VastuTheme.spacing.s6))
            // ⭐⭐ ALL THREE NUMBERS COUNT ROOMS. The third one used to count PROBLEMS, beside two
            // that count rooms, under a heading about rooms and above a label reading "need fixing".
            // A single room with two problems made the bar read 2, and a plan-wide problem with no
            // room behind it (the centre of the home, the road outside) added to a room tally it was
            // never part of — so the three could add up to more rooms than the home has. The
            // problems themselves are untouched and every one is still listed below, ranked; this is
            // only the meter finally measuring what its own labels say it measures.
            BalanceMeter(
                right = good.size,
                notIdeal = notIdeal.size,
                needsFixing = a.roomResults.count { it.verdict == Verdict.DEFECT },
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
                        // The picture is the end that scrolls — see [tapRoomOnPlan].
                        onTapRoom = { id -> tapRoomOnPlan(id) },
                        maxPlanHeight = VastuTheme.sizes.planPane,
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
                    "Not right? Change it; your score follows.",
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
            // themselves.
            //
            // ⚠ The comment here used to read "Always free: it is the single most useful sentence on
            // the screen", and the code matched it — no lock check anywhere. That was the revenue
            // leak: this card prints the top problem's layout change or first remedy, which is the
            // one thing the ₹699 sells. Free readers whose worst problem sat on a paid room got that
            // room's fix handed to them here, above the pay bar. The card is still always SHOWN and
            // still always names the problem; only the fix now honours the lock.
            defects.firstOrNull()?.let { top ->
                Spacer(Modifier.height(VastuTheme.spacing.s6))
                StartHere(top, a.roomResults, remediesOnly, roomNames, unlocked = unlocked)
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
                names = roomNames,
                defects = defects,
                zones = zones,
                unlocked = unlocked,
                remediesOnly = remediesOnly,
                expandAll = expandAll,
                openRoomId = openRoomId,
                // Select only. The row is already under the finger — see [tapRoom].
                onTapRoom = ::tapRoom,
                onRowPlaced = { id, y -> rowY[id] = y },
                onUnlock = onUnlock,
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
                // ⭐ SECOND HOME, NOT ONLY HOME (owner, 17 Aug 2026: *"This 'Answer a few more and
                // check more' does not belong on Report screen… we should nudge them to do this as
                // optional below the 'These are my rooms' button — if they choose to skip then we
                // continue to show it here also"*). The offer is now made at the end of "Check what
                // we read", where the reader is still describing their home; this stays for
                // everyone who skipped it there, and for every home drawn by hand, which never
                // passes that screen at all.
                //
                // ⚠ The label is [addDetailsLabel] and nothing else. It used to be a literal here
                // that three rules quoted word for word; both ends now read the one function.
                VastuButton(
                    addDetailsLabel(siteAnswers),
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
                    // Copy cut (10 Aug 2026): 36 words -> 24, then 23 -> 18 on 11 Aug. All four
                    // claims survive whole — traditional practice, guidance not a promise, our own
                    // summary, and not part of the tradition.
                    "Vastu is traditional practice; this is guidance, not a promise. " +
                        "The score is our summary, not the tradition's.",
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

/**
 * ⭐⭐ "READING YOUR HOME" — the moment between the North dial and the report (owner, 10 Aug 2026).
 *
 * ⚠ PUBLIC, and it takes its duration, for the same reason the report's chapters used to be public:
 * a screen the harness cannot reach is a screen no picture has ever contained. Rendered with a
 * duration of zero it draws its first frame and settles, which is what the golden photographs.
 *
 * ⚠ The bar is HONEST about what it is: it fills over a known, fixed time, because the work behind
 * it takes about fifty milliseconds and finishes long before the bar does. It is a beat to let the
 * reader arrive, not a measurement of progress, so it never pretends to report one — no percentage,
 * no "almost there", nothing that would be a lie.
 */
@Composable
fun ReadingProgress(durationMillis: Long, modifier: Modifier = Modifier) {
    val colors = VastuTheme.colors
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val fill by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis.toInt().coerceAtLeast(0)),
        label = "reading",
    )
    Column(
        modifier
            .screenRoot(colors.paper)
            .padding(VastuTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        VText("Reading your home", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(
            // ⛔ "weighed" IS the sentence. Cutting it to save one word left "Every room on the
            // traditional grid, against the rules" — which reads as an accusation that every room
            // BREAKS the rules, on the one screen a reader stares at while waiting for their score.
            // A joining word is a word you can lose; a verb is not. Caught by review, 11 Aug 2026.
            "Every room on the traditional grid, weighed against the rules.",
            style = VastuTheme.type.body,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s6))
        Box(
            Modifier
                .fillMaxWidth()
                .height(VastuTheme.sizes.progressTrack)
                .clip(VastuTheme.shapes.full)
                .background(colors.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .height(VastuTheme.sizes.progressTrack)
                    .clip(VastuTheme.shapes.full)
                    .background(colors.primary),
            )
        }
    }
}

/** How long the score takes to climb to its real value once the report appears. */
private const val SCORE_COUNT_MILLIS = 900

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
private fun VerdictHeader(
    score: Int,
    defectCount: Int,
    remediesOnly: Boolean,
    unlocked: Boolean,
    countUp: Boolean = false,
) {
    val colors = VastuTheme.colors
    val mark = LocalDecimalMark.current
    // The number climbs to the real score; the band word and colour follow it up, so the whole
    // header settles together instead of the pill snapping to green over a number still at two.
    val shown by animateIntAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = if (countUp) SCORE_COUNT_MILLIS else 0),
        label = "score",
    )
    Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        // ⭐ THE SCORE IS LOCKED TO LEFT-TO-RIGHT, exactly as the compass is (GuidedGridScreen).
        // "3.1 / 10" is a fraction, not a sentence: under an RTL locale the row mirrored and the
        // reader was shown "10 / 3.1" — the same number read upside down, on the one line the whole
        // product is about. A slash between digits is neutral to the bidi algorithm, so this cannot
        // be fixed by wording; the direction has to be pinned. The band word rides along so it stays
        // beside the number it describes.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // Baseline-aligned so "6.4" and "/ 10" sit on one line; FlowRow so the band word drops to its
        // own line at 200 % font rather than squeezing the number.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
            verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
        ) {
            VText(
                scoreOutOfTen(shown, mark),
                style = VastuTheme.type.display,
                color = scoreBandColor(shown),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            VText(
                "/ 10",
                style = VastuTheme.type.caption,
                color = colors.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            TagPill(
                text = bandWord(shown),
                color = scoreBandColor(shown),
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
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
    // ⚠ Copy cut 11 Aug 2026. Every band keeps its own verdict and none has been merged with
    // another — a home with no defects still says so, and a home with several still says so.
    val head = when {
        score >= 75 && defectCount == 0 -> "Reads well throughout — no defect the tradition counts."
        score >= 75 -> "Most of this home reads well."
        score >= 50 -> "Workable, with real problems to address."
        else -> "Several core placements work against this home."
    }
    val tail = when {
        defectCount == 0 -> ""
        // ⚠ "without moving a wall" is PINNED by two tests in ReportIntentTest — it is the sentence
        // that proves a buyer and a resident are never handed layout advice. Shorten around it.
        remediesOnly -> " Here is what you can do without moving a wall."
        else -> " Nothing is built yet — the layout is still yours."
    }
    val free = if (unlocked) "" else " Entrance, kitchen and toilets are below in full, free."
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
private fun StartHere(
    d: Defect,
    rooms: List<RoomResult>,
    remediesOnly: Boolean,
    names: Map<String, String> = emptyMap(),
    /**
     * ⭐⭐ THE PAYWALL, and the reason this parameter has no default.
     *
     * This card prints the top defect's **layout change or first remedy** — which is precisely the
     * reasoning [FreeTier] sells. It rendered that with no lock check at all until 16 Aug 2026, so
     * whenever a home's worst problem sat on a room outside the free three (a bedroom, the living
     * room, a staircase, the pooja room…) the free reader was handed that room's fix, above the
     * fold, before ever reaching the pay bar.
     *
     * It was invisible for two reasons, and both are worth keeping in mind before trusting a golden
     * here. The bundled demo home's worst defect is a toilet in the North-East, and a toilet is
     * free — so no screenshot ever photographed the leak. And the one test that guarded the locked
     * report asserted only that a paid room's `explanation` was absent, while this card never
     * prints `explanation`; it prints `layoutFix` and `remedyLine`.
     *
     * The NAME and the VERDICT stay free, exactly as they do on every locked room row — the heading,
     * "this moves your score most", and the problem's own title are all still shown. Only the fix is
     * withheld, and it says so rather than vanishing, because a card that silently loses a line is
     * the "hidden wall" Product PRD §6.4 forbids.
     */
    unlocked: Boolean,
) {
    val colors = VastuTheme.colors
    val free = unlocked || d.isFreeToRead(rooms)
    VastuCard(accent = colors.verdictDefect, background = colors.surfaceRaised) {
        SectionLabel("Start here", color = colors.verdictDefect)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(defectTitle(d, rooms, names), style = VastuTheme.type.h3, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // ⚠ "Of the problems ranked below", NOT "of everything below" — the wider claim is one
            // this report cannot make. The front door is scored too, at the ENTRANCE weight, which
            // is the heaviest single weight there is; a defect instead adds a severity penalty. The
            // engine never puts those two on one scale, so nothing here knows whether an
            // unfavourable door outranks the worst defect. Ranking only what is actually ranked is
            // the honest sentence, and it stayed honest when the door moved into this chapter.
            "Of those ranked below, this moves your score most.",
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
                if (free) {
                    VText(first, style = VastuTheme.type.bodySm, color = colors.textPrimary)
                } else {
                    VText(LOCKED_REASONING, style = VastuTheme.type.bodySm, color = colors.textTertiary)
                }
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
    /**
     * ⭐ What the PLAN calls each room, by room id — "MASTER BEDROOM 1", straight off the sheet.
     * Empty for a home drawn by hand, and then every row falls back to its numbered kind.
     */
    names: Map<String, String>,
    defects: List<Defect>,
    zones: List<ZoneInfo>,
    unlocked: Boolean,
    remediesOnly: Boolean,
    expandAll: Boolean,
    openRoomId: String?,
    onTapRoom: (String) -> Unit,
    onRowPlaced: (String, Int) -> Unit,
    /** Opens checkout, from inside a locked row — see the button in the row body below. */
    onUnlock: () -> Unit,
) {
    val colors = VastuTheme.colors
    if (rooms.isEmpty()) return

    // Defects keyed by the room they belong to, so a room row can open onto its own finding.
    val defectsByRoom = defects.filter { it.roomId != null }.groupBy { it.roomId }

    // ⚠ The fallback name is worked out over the WHOLE list before sorting, so "Bedroom 2" is the
    // second bedroom on the plan — not the second one that happened to score badly. The plan's own
    // printed caption wins over it whenever the sheet supplied one.
    val fallback = roomDisplayNames(rooms.map { it.type })
    val ordered = rooms
        .mapIndexed { i, r -> r to (names[r.roomId]?.takeIf { n -> n.isNotBlank() } ?: fallback[i]) }
        .sortedBy { (r, _) -> r.rowStatus().readingOrder() }

    SectionLabel("Your rooms (${rooms.size})")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Worst first. Tap one for where and why.",
        style = VastuTheme.type.bodySm, color = colors.textSecondary,
    )
    // ⭐ Said here, once, whenever a room actually carries that word — see [NOT_RATED_MEANS]. It
    // used to be readable only by opening one of those rooms, so the pill looked like the app had
    // given up rather than like an honest boundary of the rule data.
    if (ordered.any { (r, _) -> r.rowStatus() == VastuRoomStatus.NOT_RATED }) {
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(NOT_RATED_MEANS, style = VastuTheme.type.bodySm, color = colors.textTertiary)
    }
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
                // ⚠ [rowStatus], not the bare verdict — the entrance is scored as the front door and
                // must not be stamped "Not rated" on the page that judges it.
                status = r.rowStatus(),
                selected = open,
                expanded = open,
                modifier = Modifier.onGloballyPositioned { onRowPlaced(r.roomId, it.positionInRoot().y.toInt()) },
                onTap = { onTapRoom(r.roomId) },
                body = {
                    if (!free) {
                        // ⭐⭐ THE WAY OUT IS INSIDE THE ROW (owner, 17 Aug 2026: *"For all other
                        // rooms paywalled, expanding them should show them same Unlock full report
                        // button but smaller and well aligned inside the pill"*).
                        //
                        // ⚠ Before this, opening a locked room produced one grey sentence and a dead
                        // end: the only control that could act on it was the bar at the foot of the
                        // screen, which is out of sight on a long list and is not obviously about
                        // the room the reader is looking at. The sentence stays — it says what is
                        // behind the price, which Product PRD §6.4 requires — and the button under
                        // it opens the same checkout the bar does.
                        //
                        // ⚠ `large = false` and full width of the row's body: the small size is the
                        // one the design system already owns, and filling the body is what keeps it
                        // aligned inside the card at 320 dp and at a 200 % font, where a button
                        // sized to its own words drifts off the column the text sits in.
                        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                            VText(
                                LOCKED_REASONING,
                                style = VastuTheme.type.bodySm, color = colors.textTertiary,
                            )
                            VastuButton(
                                "Unlock the full report",
                                onClick = onUnlock,
                                large = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (roomDefects.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                            roomDefects.forEach { DefectBody(it, zones, remediesOnly) }
                        }
                    } else {
                        RoomBody(
                            r,
                            zones,
                            when {
                                r.verdict == Verdict.SUBOPTIMAL -> whyNotIdeal(r)
                                // ⭐⭐ THE ENTRANCE IS NOT "UNJUDGED" — it is judged higher up this
                                // very page. The engine scores it as the FRONT DOOR, on the 32-named
                                // positions, and the report's own words two sections above call the
                                // door "the highest-weighted single element in the whole reading".
                                // But an ENTRANCE room carries excludeFromScore, so it came back
                                // NOT_SCORED like a passage, and the generic line told the reader
                                // the tradition does not place this kind of room and we had not
                                // judged it — on the same page that judges it. One document, two
                                // opposite answers about the one thing that moves the score most.
                                //
                                // ⚠ Copy cut 11 Aug 2026, 42 words -> 26, and BOTH claims are
                                // load-bearing and both survive: it is read on the 32 named
                                // positions, and it is not scored twice. Neither may ever be
                                // dropped to make a word count — they are the honesty of the page.
                                r.verdict == Verdict.NOT_SCORED && r.type == RoomType.ENTRANCE ->
                                    "Read in full under “Your front door” above, on the " +
                                        "tradition's 32 named positions — finer than the eight " +
                                        "directions a room gets. Not scored twice."
                                r.verdict == Verdict.NOT_SCORED -> NOT_RATED_REASON
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
    "The tradition does not place this kind of room, so we have not judged it — " +
        "neither a problem nor an approval."

/**
 * ⭐ What a LOCKED row says instead of its reasoning — written once and shown in two places (a room
 * row and a finding card), because it is one sentence to a reader and had been two literals in the
 * code. A locked row is never a blurred teaser: it still names the room, the direction and the
 * verdict, and only the reasoning is behind the price (Product PRD §6.4).
 */
private const val LOCKED_REASONING: String = "Reasoning and remedies — in the full report"

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
    SectionLabel("We couldn't check these")
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(
        "Neither passed nor failed — we lacked details.",
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
                    locked -> LOCKED_REASONING
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
 * The money bar's one sentence, as a pure function so it can be read and tested on its own — this
 * is the last thing a reader sees before deciding to spend ₹699, and every clause in it has to be
 * true of what unlocking actually reveals.
 *
 * A problem carries a remedy; a room carries the reading behind its verdict, and most locked rooms
 * are rooms nothing is wrong with. So the two are named separately and only the problems are
 * promised remedies.
 */
internal fun payBarPromise(problems: Int, rooms: Int): String {
    // ⚠ The plurals are pulled out of the sentences rather than nested inside them. Nested, each
    // sentence was three broken fragments to anything reading this file — including the word
    // counter, which is how the same promise came to be counted three times over.
    val p = if (problems == 1) "problem" else "problems"
    val r = if (rooms == 1) "room" else "rooms"
    // ⚠ The POSSESSIVE stays. "3 more problems with remedies" reads as a filter — three of the
    // problems happen to have a remedy — which is a different, smaller promise than "three more
    // problems, and the remedies for them". This is the last sentence before ₹699 is spent.
    val pWithRemedies = if (problems == 1) "problem with its remedies" else "problems with their remedies"
    // ⭐⭐ "STILL LOCKED", NOT "MORE" (owner, 17 Aug 2026: *"there is line above Unlock the full
    // report which says 4 more problems.. but I see more than 4"*).
    //
    // ⚠ The number was never wrong — it counts the problems whose reasoning is behind the price, and
    // the ones he could see belong to the entrance, kitchen and toilets, which are free. But "4 more"
    // invites exactly the arithmetic he did: count the warnings on screen, compare, and find the
    // sentence untrue. "More" is a word only we can resolve, because only we know which of the rows
    // on screen were already free.
    //
    // Naming the state instead of the difference is answerable from the page: every locked row says
    // "Reasoning and remedies — in the full report" in those words, so the reader can count the
    // locked ones if they want to. This is the last sentence read before ₹699 is spent, and it has
    // to survive being checked.
    return when {
        problems > 0 && rooms > 0 -> "Still locked: $problems $pWithRemedies, and $rooms $r read in full"
        problems > 0 -> "Still locked: $problems $p, with the whole reason and the remedies"
        rooms > 0 -> "Still locked: $rooms $r, each read in full"
        else -> "The whole reading, and every verdict's reason"
    }
}

/**
 * Sticky, and it names what is still locked rather than shouting a price.
 *
 * ⚠ It sits over the scroll, so the column above reserves clearance for it — a bottom bar that
 * covers the last control is the unreachable-CTA defect UI-POLISH §3.B exists to prevent.
 */
@Composable
private fun PayBar(modifier: Modifier, a: Analysis, onUnlock: () -> Unit) {
    val colors = VastuTheme.colors
    // ⭐ Problems and rooms counted apart — see the note on these two in FreeTier. Added together
    // and sold as "N more findings, with the reason and remedies for each", the number counted
    // rooms with nothing wrong as findings, promised them a remedy they can never have, and counted
    // a room that DOES have a problem twice. Two numbers, two true clauses, no bait.
    val lockedProblems = a.lockedProblemCount()
    val lockedRooms = a.lockedRoomCount()
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .border(VastuTheme.borders.regular, colors.borderDefault)
            .padding(VastuTheme.spacing.s4),
        verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2),
    ) {
        VText(
            payBarPromise(lockedProblems, lockedRooms),
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
// ⚠ The trailing phrase must NOT be one of the strings ReportIntentTest bans for a buyer or a
// resident ("still on paper", "on the drawing", "still free to make", …). This heading only ever
// renders on the building branch, but a banned phrase here is one refactor away from a red gate.
private fun LayoutBlock(text: String) = AdviceBlock("✦ Change the layout — before it's built", text, VastuTheme.colors.primary)

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
        "Both readings, no winner, and which your score follows.",
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
