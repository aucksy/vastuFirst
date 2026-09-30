// ScanDoorScreen.kt — the front door, marked on the user's OWN plan.
//
// ⭐ WHY THIS SCREEN EXISTS (owner, 6 Aug 2026): *"Marking the Door and north should happen only on
// this actual floor plan and not on the floor plan builder and modifier screen… I intend to remove
// the floor plan builder / modifier from the Scan flow completely."*
//
// Until now a scan that had been checked on the photo jumped into the GRID editor to ask for the
// door — so the one screen the whole on-photo flow exists to avoid was still the screen that asked
// the most important question. Every objection ever raised about the redrawing ("everything clumped
// left", "a huge mostly-empty grid") applied hardest exactly here, because the user was being asked
// to point at a wall on a picture that was not their home.
//
// ⚠ THIS SCREEN IS OFTEN SKIPPED, AND THAT IS THE POINT. When the plan prints its own entrance —
// ENTRY, FOYER, ENTRANCE — `frontDoorFromEntrance` has already read the door off it and the flow goes
// straight to the report, which draws the E and says where it was read from. This screen is what
// happens when the plan says nothing, plus the way back in from the report's "Change front door".
//
// ⭐⭐ AND THE E NOW MOVES UNDER THE FINGER (owner, 27 Sep 2026: *"it should just seamlessly move
// around realtime when tapping and dragging it"*). This screen used to understand one gesture — a tap
// on a wall — so a finger that held the E and pulled it moved nothing at all. See DoorMove.kt for
// the rule every door screen now shares.
package com.vastufirst.app.ui.scan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vastufirst.app.ui.common.DoorMarkTarget
import com.vastufirst.app.ui.common.DoorNoteOverlay
import com.vastufirst.app.ui.common.DoorNoteText
import com.vastufirst.app.ui.common.doorMarkRadiusPx
import com.vastufirst.app.ui.common.drawDoorMark
import com.vastufirst.app.ui.common.nearestOnOutline
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuInfoLine
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.ScannedRoom

/**
 * ⭐ The picture is sized by its OWN shape and the page scrolls. Sharing the height instead leaves the
 * plan sized by what is left over, which on a square builder's sheet is a slot with dead margin above
 * and below it, and which collapses at a 200 % font precisely where somebody needs to aim at a wall.
 *
 * ⚠ But never taller than [DOOR_PLAN_SCREEN_SHARE] of the screen — see [ScanDoorContent].
 */
private fun doorPlanAspect(image: ImageBitmap?): Float =
    image?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { (it.width.toFloat() / it.height).coerceIn(0.7f, 1.8f) }
        ?: 1.2f

/** The photo drawn to fit its box, centred — the frame every overlay is then measured in. */
private class PhotoFit(val originX: Float, val originY: Float, val width: Float, val height: Float)

private fun fitPhoto(imageW: Int, imageH: Int, boxW: Float, boxH: Float): PhotoFit? {
    if (imageW <= 0 || imageH <= 0 || boxW <= 0f || boxH <= 0f) return null
    val scale = minOf(boxW / imageW, boxH / imageH)
    return PhotoFit(
        originX = (boxW - imageW * scale) / 2f,
        originY = (boxH - imageH * scale) / 2f,
        width = imageW * scale,
        height = imageH * scale,
    )
}

/**
 * What the E says when it is tapped on this screen — the sentence that used to sit under the plan
 * as "Your front door: marked on…", moved into the E's own note (owner, 27 Sep 2026).
 */
internal fun scanDoorNote(door: GridDoor): DoorNoteText = DoorNoteText(
    title = "Your front door",
    body = "Marked on ${doorSideWords(door.side)}. Drag the E along the outline to move it, or tap another wall.",
)

@Composable
fun ScanDoorScreen(
    handover: ScanPictureSlot,
    door: GridDoor?,
    onDoor: (GridDoor) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    /** See [ScanDoorContent.returnsToReport] — opened from a report, this screen goes back to it. */
    returnsToReport: Boolean = false,
) {
    val data = handover.data
    val image = remember(data) { data?.decodeImage() }
    ScanDoorContent(
        image = image,
        rooms = data?.rooms.orEmpty(),
        door = door,
        onDoor = onDoor,
        // The real screen nudges; the harness never does. See [ScanDoorContent.hintPulse].
        hintPulse = true,
        returnsToReport = returnsToReport,
        onNext = onNext,
        onBack = onBack,
    )
}

/** The screen as a pure function of its inputs — the seam the render harness draws. */
@Composable
fun ScanDoorContent(
    image: ImageBitmap?,
    rooms: List<ScannedRoom>,
    door: GridDoor?,
    onDoor: (GridDoor) -> Unit = {},
    onNext: () -> Unit = {},
    onBack: () -> Unit = {},
    /**
     * ⚠⚠ DEFAULT OFF, AND THE HARNESS MUST NEVER TURN IT ON. The travelling hint is an INFINITE
     * animation, and an infinite animation never lets a composition go idle — the screenshot harness
     * waits for idle before it photographs, so a screen with one running is a screen it waits on
     * forever. It hung a cloud build for forty minutes on 10 Aug 2026, mid-way through re-recording
     * every golden, with no error at all: just a step that never finished.
     *
     * The real screen turns it on; every test leaves it off.
     */
    hintPulse: Boolean = false,
    /**
     * ⭐ TRUE when the reader came here from a finished report by tapping "change where the front
     * door is". Nothing about the marking changes — only what the button at the bottom is allowed to
     * say: opened that way it hands the reader straight back to the report they came from, and in
     * the flow it opens their report for the first time. Either way the button names the screen it
     * actually opens, which is the rule this flag exists to keep.
     */
    returnsToReport: Boolean = false,
    /** For the harness: open the E's note on first draw, so a golden can photograph it. */
    startDoorNoteOpen: Boolean = false,
) {
    val colors = VastuTheme.colors
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeDp = VastuTheme.spacing.s1
    val aspect = doorPlanAspect(image)
    // The shared entrance mark's ingredients — see [drawDoorMark]. The size on this style is
    // ignored; the mark derives its own from the disc so the letter cannot outgrow it.
    val doorTouch = VastuTheme.sizes.minTouch
    val measurer = rememberTextMeasurer()
    val doorLetterStyle = VastuTheme.type.caption
    val density = LocalDensity.current
    val touchRadiusPx = with(density) { doorTouch.toPx() } / 2f

    /** Where the E is while a finger carries it, in page fractions; null at rest. See DoorMove.kt. */
    var draggedDoor by remember(image) { mutableStateOf<Pair<Float, Float>?>(null) }
    var noteOpen by remember(image) { mutableStateOf(startDoorNoteOpen) }
    // ⚠ Read LIVE inside the gesture reader, which is keyed on the picture and never on the door —
    // re-keying it on the door would cancel the drag the moment the drag moved the door.
    val liveDoor by rememberUpdatedState(door)
    val liveDragged by rememberUpdatedState(draggedDoor)
    val liveOnDoor by rememberUpdatedState(onDoor)

    // ⭐⭐ THE PLAN NEVER OUTGROWS THE SCREEN (30 Sep 2026). Sized by width alone, a square sheet on a
    // phone turned sideways came out about 806 dp tall on a 480 dp screen, so the wall a reader had to
    // tap could be below the bottom edge. The screen's own height caps it; in portrait the cap is far
    // above the width-derived size and changes nothing.
    BoxWithConstraints(Modifier.screenRoot(colors.paper)) {
    val planCap = maxHeight * DOOR_PLAN_SCREEN_SHARE
    val planWidth = maxWidth - VastuTheme.spacing.s6 * 2
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Where is your front door?", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // ⭐ One instruction, and the reason behind the **i** (owner, 19 and 27 Sep 2026 — decrease
        // the copy). "Of everything we read, this changes your score the most" is true and worth
        // saying once; it is not what somebody needs in order to do the task.
        // "or drag the E" only once there IS an E: with no door marked the plan shows none, and the
        // line would send somebody looking for a mark that is not there.
        VastuInfoLine(
            label = if (door != null) "Tap the wall you walk in through, or drag the E."
            else "Tap the wall you walk in through.",
            info = "Of everything we read, this changes your score the most.",
            tag = "door.help",
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        if (image != null) {
            val marker = door?.let { doorMarkerOnPage(it, rooms) }
            val shown = draggedDoor ?: marker
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(minOf(planWidth / aspect, planCap))
                    .onSizeChanged { boxSize = it }
                    .pointerInput(rooms, image) {
                        // ⭐⭐ ONE GESTURE READER FOR THE PICTURE — the same shape as the review
                        // screen's (see planGestures in PlanWithRooms):
                        //   · down ON the E  → the E is this finger's. Past touch slop it follows,
                        //                      every movement, along the outline; a tap opens its note.
                        //   · down elsewhere → a tap places the door on the nearest wall, as it
                        //                      always has. A drag that starts here is the PAGE's —
                        //                      nothing is consumed, so the screen still scrolls.
                        awaitEachGesture {
                            // ⚠ NOTHING may return from this block before the first down: the block
                            // re-runs as soon as it returns, and only suspends while a pointer is held.
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val fit = fitPhoto(
                                image.width, image.height,
                                size.width.toFloat(), size.height.toFloat(),
                            ) ?: return@awaitEachGesture
                            val frame = homeFrameOnPage(rooms)
                            val nowAt = (liveDragged ?: liveDoor?.let { doorMarkerOnPage(it, rooms) })
                                ?.let { (x, y) -> Offset(fit.originX + x * fit.width, fit.originY + y * fit.height) }
                            val onMark = nowAt != null && (down.position - nowAt).getDistance() <= touchRadiusPx
                            // The E keeps its place under the finger rather than leaping to the tip.
                            val grab = if (onMark) nowAt?.minus(down.position) ?: Offset.Zero else Offset.Zero
                            var dragging = false
                            var scrolled = false
                            var letGo: Pair<Float, Float>? = null

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val travelled = (change.position - down.position).getDistance() >
                                    viewConfiguration.touchSlop
                                if (!onMark) {
                                    // Not ours: a finger that travels, or that the page has taken, was
                                    // a scroll — and a scroll must never also place a door.
                                    if (travelled || change.isConsumed) { scrolled = true; break }
                                    continue
                                }
                                if (!dragging && travelled) {
                                    dragging = true
                                    // Dragging is not a question about what the E is.
                                    noteOpen = false
                                }
                                if (!dragging) continue
                                val at = change.position + grab
                                val p = nearestOnOutline(
                                    (at.x - fit.originX) / fit.width,
                                    (at.y - fit.originY) / fit.height,
                                    frame.x.toFloat(), frame.y.toFloat(),
                                    (frame.x + frame.w).toFloat(), (frame.y + frame.h).toFloat(),
                                )
                                // ONLY the E moves while the finger is down — see PlanWithRooms'
                                // note on why the door itself is set on lifting.
                                letGo = p.x to p.y
                                draggedDoor = letGo
                                change.consume()
                            }

                            if (dragging) {
                                // Lifted: the door is set where the E was let go, and the E settles
                                // onto the spot that is scored. Read from this gesture's own record,
                                // not from composed state, which may be a frame behind a quick flick.
                                letGo?.let { (x, y) ->
                                    doorForPhotoTap(x, y, rooms)?.let { if (it != liveDoor) liveOnDoor(it) }
                                }
                                draggedDoor = null
                                return@awaitEachGesture
                            }
                            if (scrolled) return@awaitEachGesture
                            if (onMark) {
                                noteOpen = !noteOpen
                                return@awaitEachGesture
                            }
                            noteOpen = false
                            // The tap, as a fraction of the PICTURE — the units every box in a reply is
                            // written in. Outside the picture entirely is not a wall.
                            val pageX = (down.position.x - fit.originX) / fit.width
                            val pageY = (down.position.y - fit.originY) / fit.height
                            if (pageX < 0f || pageX > 1f || pageY < 0f || pageY > 1f) {
                                return@awaitEachGesture
                            }
                            doorForPhotoTap(pageX, pageY, rooms)?.let(liveOnDoor)
                        }
                    }
                    .semantics {
                        contentDescription = door
                            ?.let { "Your plan. Your front door is marked on ${doorSideWords(it.side)}." }
                            ?: "Your plan. Tap the wall your front door is on."
                    },
            ) {
                // The travelling hint's position, 0..1 around the outline. Runs only while no door
                // has been marked, so it stops for good the moment the question is answered.
                val hintPhase by if (shown == null && hintPulse) {
                    rememberInfiniteTransition(label = "door-hint").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
                        label = "door-hint-phase",
                    )
                } else {
                    remember { mutableStateOf(0f) }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val fit = fitPhoto(image.width, image.height, size.width, size.height)
                        ?: return@Canvas
                    // ⭐ THE NUDGE (owner, 10 Aug 2026: "Mark your main entry screen when used should
                    // have some intuitive animation which indicates what they need to do"). Until a
                    // door is marked, a ring travels around the home's own outline — the exact
                    // rectangle a tap is measured against — so "tap the wall" stops being an
                    // instruction with no visible target and becomes a thing moving where you tap.
                    //
                    // ⚠ It stops the moment a door is marked. A hint still running after the task is
                    // done is a distraction, and worse, it competes with the marker for attention on
                    // the one screen where the marker is the answer.
                    //
                    // ⚠ A still photograph cannot show this moving; what a golden can show is that it
                    // sits ON the outline and never covers the marker.
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(fit.originX.toInt(), fit.originY.toInt()),
                        dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
                    )
                    // ⭐ The home's own outline, drawn faintly over the sheet. Without it "tap the
                    // wall" is an instruction with no visible target on a builder's sheet that is
                    // mostly title block and margin — and the outline is exactly the rectangle the
                    // tap is measured against, and the line a dragged E slides along.
                    val frame = homeFrameOnPage(rooms)
                    drawRect(
                        color = colors.textTertiary,
                        topLeft = Offset(
                            fit.originX + frame.x.toFloat() * fit.width,
                            fit.originY + frame.y.toFloat() * fit.height,
                        ),
                        size = Size(frame.w.toFloat() * fit.width, frame.h.toFloat() * fit.height),
                        style = Stroke(width = strokeDp.toPx() / 2f),
                    )
                    if (shown == null) {
                        val fx = frame.x.toFloat(); val fy = frame.y.toFloat()
                        val fw = frame.w.toFloat(); val fh = frame.h.toFloat()
                        // One lap of the perimeter, in fractions of the outline.
                        val p = hintPhase
                        val (hx, hy) = when {
                            p < 0.25f -> (fx + fw * (p / 0.25f)) to fy
                            p < 0.50f -> (fx + fw) to (fy + fh * ((p - 0.25f) / 0.25f))
                            p < 0.75f -> (fx + fw * (1f - (p - 0.50f) / 0.25f)) to (fy + fh)
                            else -> fx to (fy + fh * (1f - (p - 0.75f) / 0.25f))
                        }
                        val at = Offset(fit.originX + hx * fit.width, fit.originY + hy * fit.height)
                        // ⭐ THE HINT IS A GHOST OF THE MARK ITSELF, at the mark's own size (18 Aug
                        // 2026). It used to be a dot a third that size, so the travelling nudge and
                        // the thing it was inviting you to place looked nothing like each other —
                        // the hint said "tap along here" without ever showing what would appear.
                        val ghost = doorMarkRadiusPx(fit.width, fit.height, doorTouch.toPx())
                        drawCircle(color = colors.primary.copy(alpha = 0.22f), radius = ghost, center = at)
                        drawCircle(
                            color = colors.primary.copy(alpha = 0.55f),
                            radius = ghost,
                            center = at,
                            style = Stroke(width = strokeDp.toPx() / 2f),
                        )
                    }
                    if (shown != null) {
                        val at = Offset(
                            fit.originX + shown.first * fit.width,
                            fit.originY + shown.second * fit.height,
                        )
                        // ⭐⭐ THE SAME MARK THE NEXT SCREEN SHOWS (owner, 18 Aug 2026: *"if its not
                        // auto-detected then it falls back to older circle"*). One drawing, in
                        // [drawDoorMark]; the ring shows while it is held or its note is open.
                        drawDoorMark(
                            center = at,
                            radius = doorMarkRadiusPx(fit.width, fit.height, doorTouch.toPx()),
                            selected = noteOpen || draggedDoor != null,
                            colors = colors,
                            strokePx = strokeDp.toPx(),
                            measurer = measurer,
                            letterStyle = doorLetterStyle,
                        )
                    }
                }

                // ⭐ THE E'S OWN NODE AND ITS NOTE — see DoorMarkTarget and DoorNoteCard.
                val fitNow = fitPhoto(image.width, image.height, boxSize.width.toFloat(), boxSize.height.toFloat())
                if (door != null && shown != null && fitNow != null) {
                    val centre = Offset(
                        fitNow.originX + shown.first * fitNow.width,
                        fitNow.originY + shown.second * fitNow.height,
                    )
                    DoorMarkTarget(
                        centrePx = centre,
                        description = "Your front door, on ${doorSideWords(door.side)}",
                        onOpen = { noteOpen = true },
                        moveTo = { side -> doorOnWall(side, rooms)?.let(onDoor) },
                        wallWords = ::doorSideWords,
                    )
                    if (noteOpen) {
                        DoorNoteOverlay(
                            note = scanDoorNote(door),
                            markerInTopHalf = centre.y < boxSize.height / 2f,
                            onClose = { noteOpen = false },
                        )
                    }
                }
            }
        } else {
            // ⚠ NO aspectRatio ON THIS ONE, and that is a fix, not an omission (11 Aug 2026).
            //
            // An aspect ratio is a FIXED height derived from the width, so the box was 1.2 : 1
            // whatever was inside it — and at 200 % font this sentence needs more than that. It was
            // cut mid-word: "we will say so o…". The reader of this screen has just been told their
            // photo could not be shown, and the sentence telling them they can carry on anyway was
            // the one being truncated.
            //
            // The ratio exists to hold the PLAN's shape. With no plan there is nothing to hold, and
            // this column scrolls, so letting the box be as tall as its own words costs nothing.
            //
            // ⚠ Found by the bottom-of-screen golden added in the same commit, not by any gate: the
            // matrix goldens all draw this screen WITH a photo, so the fallback had never been
            // photographed at any size. The geometry gate could not see it either — these captures
            // emit no manifest. Only the picture showed it.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GuidanceState(
                    title = "The photo could not be shown",
                    body = "You can carry on without marking the door — we will say so on your score.",
                )
            }
        }

        // ⚠ The "YOUR FRONT DOOR — Marked on the top wall…" lines that sat here are gone from the page
        // (owner, 27 Sep 2026: *"instead filling the screen with all this info"*). Where the door is
        // now answers from the E itself — tap it — and the button below still names the choice.
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⚠ Always available, even with no door marked. Not marking it is a real answer — the score
        // says outright what it could not weigh — and a button that refuses to move is how a person
        // gets stuck on the last screen before their result.
        VastuButton(
            when {
                // Came from a finished report: this button returns there, and says so.
                returnsToReport && door != null -> "Done — back to my report"
                returnsToReport -> "Leave the door out — back to my report"
                // ⭐ In the flow this is the LAST step — North was set on the scan result just
                // before it, so nothing follows this but the report.
                door != null -> "Read my home"
                else -> "Skip the door — read my home"
            },
            onClick = onNext,
        )
        // ⚠ No second "Back" button under this one. The header already carries the ‹ chevron, and a
        // duplicate at the foot of a scrolling page bought nothing except one more thing to be half
        // cut off by the bottom of the screen at a 200 % font — which is precisely what the geometry
        // gate reported when it was there.
    }
    }
}

/**
 * The most of the screen's height the plan may take. Chosen so that on a landscape phone (480 dp tall)
 * the heading, the one-line instruction and the WHOLE plan are on the first screenful together.
 */
private const val DOOR_PLAN_SCREEN_SHARE = 0.62f
