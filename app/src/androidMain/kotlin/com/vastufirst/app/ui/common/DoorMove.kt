// DoorMove.kt — moving the front door by hand, the same way on every screen that lets you.
//
// ⭐⭐ WHY THIS FILE EXISTS (owner, 27 Sep 2026): *"fix the entry point circular button... its not
// moving real-time when holding and dragging it.. currently it follows after you have dragged it..
// and I am also not able to move it to other walls.. its stuck on same wall where you put it.. it
// should just seamlessly move around realtime when tapping and dragging it. and it should not be
// locked behind a button... user can just tap and drag it... on first tap and pop up can tell them
// what it is instead filling the screen with all this info"*.
//
// What was true before this file, on the three screens that set the front door:
//
//   · Check what we read — the E moved only once it had been TAPPED first, and while it moved it
//     jumped from grid square to grid square: it was redrawn only when the SCORED door changed,
//     never under the finger.
//   · Where is your front door? — tap only. A drag moved nothing at all.
//   · The hand-drawn editor — tap only, and only inside a door step reached through a button.
//     Holding and dragging moved nothing until the finger lifted; then the E jumped.
//
// Now all three share one rule, and it is decided here:
//
//   · a finger that lands on the E owns it at once — no tap first, no button;
//   · while it moves, the E is drawn at the point on the home's outline NEAREST THE FINGER, every
//     frame, so it glides along a wall and round a corner onto the next one;
//   · the scored door follows it, and on lifting the E settles onto the exact spot that is scored —
//     displayed == scored == reloaded, the rule the door has always kept;
//   · a tap on the E opens a small note saying what it is. The sentences that used to be printed
//     round the plan moved INTO that note; nothing was deleted.
package com.vastufirst.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.vastufirst.app.ui.newplan.DoorSide
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import kotlin.math.roundToInt

/** The test tag on the front door's own node, wherever it is drawn. One name, every screen. */
const val DOOR_MARK_TAG = "door.mark"

/** The test tag on the note a tap on the front door opens. */
const val DOOR_NOTE_TAG = "door.note"

/** A point on the home's outline, and which wall it is on. The units are the caller's own. */
data class OutlinePoint(val side: DoorSide, val x: Float, val y: Float)

/** How far off a corner a point on the outline is kept, as a share of that wall's length. */
private const val CORNER_KEEP_OFF = 1e-4f

/**
 * ⭐⭐ THE POINT ON THE HOME'S OUTLINE NEAREST TO A FINGER — the whole of "the E follows you round
 * the house".
 *
 * [left]/[top]/[right]/[bottom] are the home's outline (the footprint the engine scores the door
 * against), in whatever units the caller works in: grid cells in the editor, fractions of the
 * photograph on the scan screens. The answer is ON that outline, always.
 *
 * ⚠ THE WALL IS CHOSEN BY THE SAME RULE AS [com.vastufirst.app.ui.newplan.doorForTap] — signed
 * distance, so inside the home the nearest wall wins and outside it the wall the finger is furthest
 * beyond wins, with ties going north, south, west, east in that order. That is not a coincidence to
 * keep: the E is DRAWN from this function and the door is SCORED from that one, and if the two could
 * ever name different walls the picture would lie about the heaviest input in the score.
 *
 * Pure, so every corner and every side is pinned by an ordinary test (DoorMoveTest).
 */
fun nearestOnOutline(x: Float, y: Float, left: Float, top: Float, right: Float, bottom: Float): OutlinePoint {
    // Distance to each wall line. Negative = the finger is beyond that wall.
    val dN = y - top
    val dS = bottom - y
    val dW = x - left
    val dE = right - x
    // Slid onto the wall, never past its ends — and kept a hair's breadth off the very CORNER.
    //
    // ⚠ The corner point belongs to two walls at once, so handing it to doorForTap would let the tie
    // order (north first) decide the wall rather than the finger: drag round the top-right corner
    // towards the east and the scored door would read NORTH while the E sat on the east wall. A
    // ten-thousandth of the wall's own length is invisible and settles it. Guarded so a degenerate
    // outline cannot throw.
    val keepX = (right - left) * CORNER_KEEP_OFF
    val keepY = (bottom - top) * CORNER_KEEP_OFF
    val alongX = if (left < right) x.coerceIn(left + keepX, right - keepX) else left
    val alongY = if (top < bottom) y.coerceIn(top + keepY, bottom - keepY) else top
    return when (minOf(dN, dS, dW, dE)) {
        dN -> OutlinePoint(DoorSide.N, alongX, top)
        dS -> OutlinePoint(DoorSide.S, alongX, bottom)
        dW -> OutlinePoint(DoorSide.W, left, alongY)
        else -> OutlinePoint(DoorSide.E, right, alongY)
    }
}

/**
 * Where the EDITOR draws the E while it is being dragged, in fractional cells.
 *
 * The editor rests the E inside the wall's own row or column of cells (see `doorMarkerCell`), not
 * astride the line the photograph screens straddle. So the drag keeps that
 * same convention — the middle of the wall's own cells, slid continuously along it — and the E does
 * not jump half a cell inwards the moment the finger lifts.
 */
fun editorDoorCentre(p: OutlinePoint, minC: Int, minR: Int, maxC: Int, maxR: Int): Pair<Float, Float> {
    val alongX = p.x.coerceIn(minC + 0.5f, (maxC - 0.5f).coerceAtLeast(minC + 0.5f))
    val alongY = p.y.coerceIn(minR + 0.5f, (maxR - 0.5f).coerceAtLeast(minR + 0.5f))
    return when (p.side) {
        DoorSide.N -> alongX to (minR + 0.5f)
        DoorSide.S -> alongX to (maxR - 0.5f)
        DoorSide.W -> (minC + 0.5f) to alongY
        DoorSide.E -> (maxC - 0.5f) to alongY
    }
}

/**
 * What the note on the front door says: a short name, then the sentence that used to be printed on
 * the page. Kept as data so every screen's wording is pinned by a plain test, not only a picture.
 */
data class DoorNoteText(val title: String, val body: String)

/**
 * ⭐ THE NOTE A TAP ON THE E OPENS — the owner's "pop up can tell them what it is".
 *
 * ⚠ DRAWN IN THE SCREEN'S OWN LAYOUT, NOT IN A SEPARATE WINDOW. A platform popup is a second
 * window, and the screenshot harness photographs one: every golden of this state would have come out
 * without the note in it, and a note no picture has ever shown is a note nobody has checked at a
 * 200 % font on a 320 dp phone.
 *
 * ⚠ ALLOWED TO BE TALLER THAN THE PLAN IT SITS ON. At a 200 % font on a short plan the words need
 * more height than the picture has, and a note cut off mid-sentence is worse than one that hangs
 * over the list beneath it for as long as it is open. A tap on it — or anywhere else — puts it away.
 */
@Composable
fun DoorNoteCard(note: DoorNoteText, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = VastuTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(unbounded = true)
            .clip(VastuTheme.shapes.md)
            .background(colors.surfaceRaised)
            .border(VastuTheme.borders.strong, colors.primary, VastuTheme.shapes.md)
            .testTag(DOOR_NOTE_TAG)
            .clickableTap(role = Role.Button, onClickLabel = "close this note", onClick = onClose)
            .padding(VastuTheme.spacing.s3),
    ) {
        VText(note.title, style = VastuTheme.type.label, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s1))
        VText(note.body, style = VastuTheme.type.bodySm, color = colors.textSecondary)
    }
}

/**
 * The note, placed over the plan on the side AWAY from the E, so it never covers the thing it is
 * explaining. [markerInTopHalf] says where the E is; the note takes the other half.
 */
@Composable
fun BoxScope.DoorNoteOverlay(note: DoorNoteText, markerInTopHalf: Boolean, onClose: () -> Unit) {
    DoorNoteCard(
        note = note,
        onClose = onClose,
        modifier = Modifier
            .align(if (markerInTopHalf) Alignment.BottomCenter else Alignment.TopCenter)
            .padding(VastuTheme.spacing.s2),
    )
}

/**
 * ⭐ THE E AS SOMETHING A SCREEN READER CAN FIND — invisible, touch-sized, centred on the drawn mark.
 *
 * A picture is one node to a screen reader, and dragging is a gesture it cannot perform. So the E
 * gets its own node: it names the door, a double tap opens the same note a finger's tap opens, and
 * [moveTo] offers one action per wall — "move it to the left wall of your plan" — which is the
 * single-tap way to do what the drag does (WCAG 2.2, dragging movements).
 *
 * ⚠ SEMANTICS ONLY, NO POINTER INPUT. It sits over the picture, and the picture's own gesture reader
 * must still receive every touch — including the one that lands on the E and drags it.
 *
 * [centrePx] is the E's centre in the parent's pixels, and follows the E while it is dragged.
 */
@Composable
fun DoorMarkTarget(
    centrePx: Offset,
    description: String,
    onOpen: () -> Unit,
    moveTo: ((DoorSide) -> Unit)?,
    wallWords: (DoorSide) -> String,
) {
    val half = with(LocalDensity.current) { VastuTheme.sizes.minTouch.toPx() } / 2f
    Box(
        Modifier
            .offset { IntOffset((centrePx.x - half).roundToInt(), (centrePx.y - half).roundToInt()) }
            .size(VastuTheme.sizes.minTouch)
            .testTag(DOOR_MARK_TAG)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick(label = "say what this is") { onOpen(); true }
                if (moveTo != null) {
                    customActions = DoorSide.entries.map { side ->
                        CustomAccessibilityAction("Move the front door to ${wallWords(side)}") {
                            moveTo(side); true
                        }
                    }
                }
            },
    )
}
