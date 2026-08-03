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
//   · The tint is APPROXIMATE. The reader reports each room as fractions of the BUILDING's outer
//     wall, not of the page (prompt contract), so on a sheet with wide margins the tint sits
//     offset. The subtitle says "roughly" in as many words.
//   · This screen VERIFIES; it does not fix. A wrong or missing room is corrected on the guided
//     grid, one tap away — that is what the "Fix on the grid instead" button is for, and why the
//     classic flow stays the toggle's default.
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

/** What the scan hands this screen: the picture it read, and the rooms it read off it. */
class ScanReviewData(
    val imageBytes: ByteArray?,
    val rooms: List<ScannedRoom>,
)

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
    onContinue: () -> Unit,
    onEditGrid: () -> Unit,
    onBack: () -> Unit,
) {
    val data = handover.data
    val image = remember(data) {
        data?.imageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    ScanReviewContent(
        image = image,
        rooms = data?.rooms.orEmpty(),
        onContinue = onContinue,
        onEditGrid = onEditGrid,
        onBack = onBack,
    )
}

/** The screen as a pure function of its inputs — the seam the render harness draws. */
@Composable
fun ScanReviewContent(
    image: ImageBitmap?,
    rooms: List<ScannedRoom>,
    onContinue: () -> Unit = {},
    onEditGrid: () -> Unit = {},
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
        VText(
            "Your plan stays as you scanned it. Tap a room below — the tint shows roughly where it was read.",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        if (image != null) {
            val tint = rooms.getOrNull(selected)
            val tintColor = tint?.type?.editorColor() ?: colors.primary
            // Read in composable scope: theme locals are not readable inside the draw lambda.
            val strokeDp = VastuTheme.spacing.s1
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
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
            Box(Modifier.fillMaxWidth().weight(1.1f), contentAlignment = Alignment.Center) {
                GuidanceState(
                    title = "The photo could not be shown",
                    body = "Every room we read is still listed below, and the grid can show the layout.",
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
                    subtitle = room.type.label() +
                        (room.printedSize.takeIf { it.isNotBlank() }?.let { " · $it" } ?: " · no size printed"),
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
        VastuButton("These are my rooms — set North", onClick = onContinue)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VastuButtonInline(
            "Something is wrong — fix on the grid",
            onClick = onEditGrid,
            style = VastuButtonStyle.SECONDARY,
        )
    }
}
