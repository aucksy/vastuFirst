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
// ENTRY, FOYER, ENTRANCE — `frontDoorFromEntrance` has already read the door off it and the review
// screen STATES the answer instead of asking the question. This screen is what happens when the plan
// says nothing, plus the way back in from "change it" when it does.
package com.vastufirst.app.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.app.ui.newplan.GridDoor
import com.vastufirst.designsystem.components.GuidanceState
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.ScannedRoom

/**
 * ⭐ The picture is sized by its OWN shape and the page scrolls, exactly as on the review screen —
 * see the note there. Sharing the height instead leaves the plan sized by what is left over, which
 * on a square builder's sheet is a slot with dead margin above and below it, and which collapses at
 * a 200 % font precisely where somebody needs to aim at a wall.
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

@Composable
fun ScanDoorScreen(
    handover: ScanReviewHandover,
    door: GridDoor?,
    onDoor: (GridDoor) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val data = handover.data
    val image = remember(data) { data?.decodeImage() }
    ScanDoorContent(
        image = image,
        rooms = data?.rooms.orEmpty(),
        door = door,
        onDoor = onDoor,
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
) {
    val colors = VastuTheme.colors
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val strokeDp = VastuTheme.spacing.s1
    val aspect = doorPlanAspect(image)

    Column(
        Modifier
            .screenRoot(colors.paper)
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
        VText(
            "Tap the wall you walk in through. Of everything we read, this changes your score the most.",
            style = VastuTheme.type.bodySm,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        if (image != null) {
            val marker = door?.let { doorMarkerOnPage(it, rooms) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .onSizeChanged { boxSize = it }
                    .pointerInput(rooms, image, boxSize) {
                        detectTapGestures { at ->
                            val fit = fitPhoto(
                                image.width, image.height,
                                boxSize.width.toFloat(), boxSize.height.toFloat(),
                            ) ?: return@detectTapGestures
                            // The tap, as a fraction of the PICTURE — the units every box in a
                            // reply is written in. Outside the picture entirely is not a wall.
                            val pageX = (at.x - fit.originX) / fit.width
                            val pageY = (at.y - fit.originY) / fit.height
                            if (pageX < 0f || pageX > 1f || pageY < 0f || pageY > 1f) {
                                return@detectTapGestures
                            }
                            doorForPhotoTap(pageX, pageY, rooms)?.let(onDoor)
                        }
                    }
                    .semantics {
                        contentDescription = door
                            ?.let { "Your plan. Your front door is marked on ${doorSideWords(it.side)}." }
                            ?: "Your plan. Tap the wall your front door is on."
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val fit = fitPhoto(image.width, image.height, size.width, size.height)
                        ?: return@Canvas
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(fit.originX.toInt(), fit.originY.toInt()),
                        dstSize = IntSize(fit.width.toInt(), fit.height.toInt()),
                    )
                    // ⭐ The home's own outline, drawn faintly over the sheet. Without it "tap the
                    // wall" is an instruction with no visible target on a builder's sheet that is
                    // mostly title block and margin — and the outline is exactly the rectangle the
                    // tap is measured against, so what is shown is what is being asked for.
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
                    if (marker != null) {
                        val at = Offset(
                            fit.originX + marker.first * fit.width,
                            fit.originY + marker.second * fit.height,
                        )
                        // A filled disc with a ring around it: big enough to find on a busy sheet,
                        // open enough not to hide the wall it is sitting on.
                        drawCircle(color = colors.primary, radius = strokeDp.toPx() * 1.5f, center = at)
                        drawCircle(
                            color = colors.paper,
                            radius = strokeDp.toPx() * 2.5f,
                            center = at,
                            style = Stroke(width = strokeDp.toPx() / 2f),
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(aspect), contentAlignment = Alignment.Center) {
                GuidanceState(
                    title = "The photo could not be shown",
                    body = "You can carry on without marking the door — we will say so on your score.",
                )
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        SectionLabel("Your front door")
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            door?.let { "Marked on ${doorSideWords(it.side)}. Tap somewhere else to move it." }
                ?: "Not marked yet. Tap your plan where you come in.",
            style = VastuTheme.type.body,
            color = if (door != null) colors.textPrimary else colors.textSecondary,
        )

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⚠ Always available, even with no door marked. Not marking it is a real answer — the score
        // says outright what it could not weigh — and a button that refuses to move is how a person
        // gets stuck on the last screen before their result.
        VastuButton(
            if (door != null) "Next — which way is North?" else "Skip — I'll leave the door out",
            onClick = onNext,
        )
        // ⚠ No second "Back" button under this one. The header already carries the ‹ chevron, and a
        // duplicate at the foot of a scrolling page bought nothing except one more thing to be half
        // cut off by the bottom of the screen at a 200 % font — which is precisely what the geometry
        // gate reported when it was there.
    }
}
