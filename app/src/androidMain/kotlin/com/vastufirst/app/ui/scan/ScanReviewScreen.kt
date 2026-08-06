// ScanReviewScreen.kt — the ON-PHOTO review: the user's own plan stays on screen, and our reading
// of it is a LIST they check against it, room by room.
//
// ⭐ WHY THIS EXISTS (owner request, 4 Aug 2026). Every complaint ever filed against a scan —
// "balcony towering over the rooms", "everything clumped left", "a huge mostly-empty grid" — was
// about OUR redrawing, never about the photo. This screen never redraws the home: the photo is the
// picture of record, the extracted rooms are a list beside it, and tapping a room tints roughly
// where on the picture it was read, so "did it find my kitchen?" is answerable at a glance.
//
// ⚠ HONESTY LIMITS, stated on screen rather than hidden:
//   · The tint is APPROXIMATE. Room boxes are fractions of the BUILDING's outer wall (prompt
//     contract). Since prompt v4 (4 Aug 2026) the reply also carries the building's own box on the
//     page, and ScanMapper.pageSource composes the two — measured on 35 approved scans, this took
//     Green Court's tint centres from 0.18–0.28 of the sheet off to 0.007–0.033. Older replies
//     carry no building box and draw as before. Either way the subtitle keeps saying "roughly".
//   · This screen VERIFIES; it does not redraw. A room read as the wrong KIND is re-typed on the
//     results screen before this one. What it deliberately no longer offers is the guided grid:
//     since 6 Aug 2026 the scan flow never opens the editor at all (owner: "I intend to remove the
//     floor plan builder / modifier from the Scan flow completely"), so the door and North are now
//     marked on this same photograph and the redrawing is confined to the draw-it-yourself path.
package com.vastufirst.app.ui.scan

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vastufirst.app.ui.common.label
import com.vastufirst.app.ui.common.editorColor
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonInline
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuListRow
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.ScannedRoom

/**
 * ⭐ How much of the flexible height the PICTURE gets, against 1 for the room list (owner,
 * 6 Aug 2026: "enlarge the floor plan — it is too small"). It was 1.1, which on a square builder's
 * sheet left the plan height-limited at roughly 60 % of the screen's width — a postage stamp beside
 * a list of rooms whose names he already knows. At 2 the picture becomes WIDTH-limited instead,
 * i.e. as large as the screen can draw it, and the list keeps a third of the space and scrolls.
 * The list is the cheaper thing to scroll: it is text, and every row is one line.
 */
private const val PLAN_WEIGHT = 2f

/** What the scan hands this screen: the picture it read, and the rooms it read off it. */
class ScanReviewData(
    val imageBytes: ByteArray?,
    val rooms: List<ScannedRoom>,
) {
    /**
     * The photo, decoded once per handover. Shared with the door screen so both draw the identical
     * bitmap — a second decode would be a second object, and the door marker is positioned against
     * the picture's own pixels.
     */
    fun decodeImage(): ImageBitmap? = imageBytes?.let { bytes ->
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
    }
}

/**
 * The handover slot between the scan screen and this one — a one-field singleton rather than a
 * navigation argument, because the payload is an image plus a room list and the nav graph passes
 * strings. Written right before navigating here; never persisted.
 */
class ScanReviewHandover {
    var data: ScanReviewData? = null
}

@Composable
fun ScanReviewScreen(
    handover: ScanReviewHandover,
    door: GridDoor?,
    onContinue: () -> Unit,
    onChangeDoor: () -> Unit,
    onBack: () -> Unit,
) {
    val data = handover.data
    val image = remember(data) { data?.decodeImage() }
    ScanReviewContent(
        image = image,
        rooms = data?.rooms.orEmpty(),
        door = door,
        onContinue = onContinue,
        onChangeDoor = onChangeDoor,
        onBack = onBack,
    )
}

/**
 * What the plan printed for this room, for the line under its name.
 *
 * ⭐ A room fused from a run of sections has no single printed size — it has all of them (the owner's
 * balcony runs the width of his flat and his sheet dimensions it in three pieces). All of them are
 * printed here, because checking our reading against the paper is this screen's entire job, and a
 * fused row that showed "no size printed" would have quietly deleted three real measurements.
 */
private fun sizeNote(room: ScannedRoom): String = when {
    room.readInParts.size > 1 -> " · one space: " + room.readInParts.joinToString(" + ")
    room.printedSize.isNotBlank() -> " · " + room.printedSize
    else -> " · no size printed"
}

/** The screen as a pure function of its inputs — the seam the render harness draws. */
@Composable
fun ScanReviewContent(
    image: ImageBitmap?,
    rooms: List<ScannedRoom>,
    /**
     * ⭐ The front door, when the plan told us where it is — read from its own printed ENTRY/FOYER by
     * `frontDoorFromEntrance`, never guessed. Non-null means this screen STATES it rather than
     * sending the user off to mark it; null means the next step is the asking.
     */
    door: GridDoor? = null,
    onContinue: () -> Unit = {},
    onChangeDoor: () -> Unit = {},
    onBack: () -> Unit = {},
    /** For the harness: pre-select a room so the golden shows the tint. -1 = nothing selected. */
    startSelected: Int = -1,
) {
    val colors = VastuTheme.colors
    var selected by remember { mutableStateOf(startSelected) }

    Column(Modifier.screenRoot(colors.paper).padding(VastuTheme.spacing.s6)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
        ) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Check what we read", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        // ⭐ Two lines, not three (owner, 6 Aug 2026: "enlarge the floor plan — it is too small").
        // Every line of prose above the picture is a line taken off the picture, and this screen's
        // whole job is looking at the plan. The honesty words stay: "as you scanned it" is the
        // promise that we never redrew it, "roughly" is the tint's stated limit.
        VText(
            "Your plan, as you scanned it. Tap a room to see roughly where we read it.",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        if (image != null) {
            val tint = rooms.getOrNull(selected)
            val tintColor = tint?.type?.editorColor() ?: colors.primary
            // Read in composable scope: theme locals are not readable inside the draw lambda.
            val strokeDp = VastuTheme.spacing.s1
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(PLAN_WEIGHT)
                    .semantics {
                        contentDescription =
                            tint?.let { "Your plan, showing roughly where ${it.type.label()} was read" }
                                ?: "Your scanned plan"
                    },
            ) {
                // Fit the photo into this box, centred — then everything else draws in ITS frame.
                val scale = minOf(size.width / image.width, size.height / image.height)
                val drawn = Size(image.width * scale, image.height * scale)
                val origin = Offset((size.width - drawn.width) / 2f, (size.height - drawn.height) / 2f)
                drawImage(
                    image = image,
                    dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                    dstSize = IntSize(drawn.width.toInt(), drawn.height.toInt()),
                )
                val box = tint?.source
                if (box != null) {
                    val topLeft = Offset(
                        origin.x + (box.x.toFloat() * drawn.width),
                        origin.y + (box.y.toFloat() * drawn.height),
                    )
                    val area = Size(box.w.toFloat() * drawn.width, box.h.toFloat() * drawn.height)
                    drawRect(color = tintColor.copy(alpha = 0.28f), topLeft = topLeft, size = area)
                    drawRect(
                        color = tintColor,
                        topLeft = topLeft,
                        size = area,
                        style = Stroke(width = strokeDp.toPx() / 2f),
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().weight(PLAN_WEIGHT), contentAlignment = Alignment.Center) {
                GuidanceState(
                    title = "The photo could not be shown",
                    body = "Every room we read is still listed below, and your score still works.",
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        SectionLabel("${rooms.size} rooms read from your plan")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(rooms) { index, room ->
                val checkFlagged = room.flags.isNotEmpty()
                val shown = selected == index
                VastuListRow(
                    title = room.label.ifBlank { room.type.label() },
                    subtitle = room.type.label() + sizeNote(room),
                    modifier = Modifier.clickableTap(onClickLabel = "show this room on the plan") {
                        selected = if (shown) -1 else index
                    },
                    trailing = {
                        VText(
                            text = if (shown) "Shown" else if (checkFlagged) "Check" else "›",
                            style = VastuTheme.type.caption,
                            color = if (shown) colors.primary
                            else if (checkFlagged) colors.warning
                            else colors.textTertiary,
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⭐⭐ WHEN THE PLAN NAMED ITS OWN ENTRANCE, WE DO NOT ASK (owner, 6 Aug 2026: "cant we do it
        // ourselves when Entry is clearly marked? we ask only if its not"). But not asking is not the
        // same as not saying: the front door is the heaviest input the engine weighs, so what we read
        // is stated here in one line, on the screen whose whole job is checking our reading, with the
        // way to change it beside it. Silence would have been the app deciding and saying nothing.
        if (door != null) {
            VText(
                "Front door: we read it from your plan's own entrance, on ${doorSideWords(door.side)}.",
                style = VastuTheme.type.bodySm,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
        }
        // The button never promises a screen other than the one it opens (audit B2).
        VastuButton(
            if (door != null) "These are my rooms — which way is North?"
            else "These are my rooms — set the front door",
            onClick = onContinue,
        )
        if (door != null) {
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VastuButtonInline(
                "Put the front door somewhere else",
                onClick = onChangeDoor,
                style = VastuButtonStyle.SECONDARY,
            )
        }
    }
}
