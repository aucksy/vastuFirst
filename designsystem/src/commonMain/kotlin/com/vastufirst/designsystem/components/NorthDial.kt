package com.vastufirst.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntSize
import com.vastufirst.designsystem.foundation.LocalVastuHaptics
import com.vastufirst.designsystem.theme.VastuTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The signature Mark-North control (§6.3 · VastuCompass.dc.html). Drag anywhere on the dial (or
 * tap) to point North; the knob follows and North is emitted as a 0..359 bearing. The centre
 * stays clean; there is deliberately NO "best angle" affordance (§0.7) — this control only sets
 * a physical fact, it never optimises the score.
 */
@Composable
fun NorthDial(
    model: ZoneMapModel,
    onNorthChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    /**
     * Breathe a ring around the knob until the reader first moves the dial. Off by default so the
     * render harness photographs the dial at rest; the screen turns it on until North is touched.
     */
    hintPulse: Boolean = false,
) {
    val haptics = LocalVastuHaptics.current
    // Tracks the last whole degree emitted, so the tick fires once per degree crossed while dragging
    // (the design's "soft detent on the dial"), not once per raw pointer event.
    var lastDeg by remember { mutableStateOf(model.northDegrees.roundToInt()) }

    fun emit(pos: Offset, sizePx: IntSize) {
        val cx = sizePx.width / 2f
        val cy = sizePx.height / 2f
        val dx = pos.x - cx
        val dy = pos.y - cy
        var deg = (atan2(dx, -dy) * 180f / PI.toFloat())
        deg = ((deg % 360f) + 360f) % 360f
        val d = deg.roundToInt() % 360
        if (d != lastDeg) {
            lastDeg = d
            haptics.tick()
        }
        onNorthChange(d)
    }

    // ⭐ The control takes the PLAN's shape when there is a photographed one — see [dialAspectFor].
    // Square for every hand-drawn home, exactly as it always was.
    val aspect = model.planImage?.let { dialAspectFor(it.width, it.height) } ?: 1f
    BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(aspect)) {
        val dim = maxWidth
        val tall = maxHeight
        // The label + adjustable semantics live on the interactive overlay below, not here — one
        // labelled node, no duplicate for TalkBack / the ATF a11y gate.
        ZoneMap(
            model = model,
            modifier = Modifier.matchParentSize(),
            showLabels = true,
            contentDescription = null,
            aspect = aspect,
        )

        // Input layer — drag or tap sets North to the bearing of the touch from the centre. It also
        // carries the accessibility contract: the label, the current bearing as a range, and a
        // setProgress action so TalkBack's adjust gesture can set North (C13 — the dial was
        // label-only before, so a blind user could hear it but not turn it).
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(model.northDegrees, 0f..359f)
                    setProgress { target ->
                        onNorthChange(((target.roundToInt() % 360) + 360) % 360)
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> emit(change.position, size); change.consume() }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> emit(pos, size) }
                }
        )

        // Knob — positioned on the ring at the current North bearing.
        //
        // ⚠ The RING's radius comes from the narrower side (the canvas does the same arithmetic),
        // but the knob's vertical origin is the box's own centre. Written as `dim` for both, the
        // knob rode a circle centred half a width down a box that is taller than that — so on a
        // tall plan it sat well above the ring it is supposed to be gripping.
        val ringFrac = 66f / 160f
        val ringR = minOf(dim, tall) * ringFrac
        val nrad = model.northDegrees * (PI.toFloat() / 180f)
        val knobX = dim / 2f + ringR * sin(nrad)
        val knobY = tall / 2f - ringR * cos(nrad)
        val hit = VastuTheme.sizes.knobHit
        val arrowColor = VastuTheme.colors.primary          // captured for the DrawScope below
        Box(
            modifier = Modifier
                .offset(x = knobX - hit / 2f, y = knobY - hit / 2f)
                .size(hit),
            contentAlignment = Alignment.Center,
        ) {
            // ⭐ THE NUDGE (owner, 10 Aug 2026: "Mark the North page also need to have some intuitive
            // animation which indicates what they need to do"). A ring breathes outward from the
            // knob until the reader first moves the dial, which is the only instruction this screen
            // really needs: THIS is the thing you drag.
            //
            // ⚠ It stops for good on the first change, and it is drawn BEHIND the knob so it can
            // never obscure the one control it is pointing at. A hint that keeps pulsing after you
            // have understood it is a distraction, not a hint.
            //
            // ⚠ A still photograph cannot show this moving. What a golden CAN check is that it never
            // covers the knob and never changes the dial's size — which is why it draws inside the
            // knob's own hit box rather than as an overlay.
            if (hintPulse) {
                val transition = rememberInfiniteTransition(label = "knob-hint")
                val pulse by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
                    label = "knob-hint-pulse",
                )
                Canvas(Modifier.matchParentSize()) {
                    val maxR = size.minDimension / 2f
                    drawCircle(
                        color = arrowColor.copy(alpha = (1f - pulse) * 0.35f),
                        radius = maxR * (0.55f + 0.45f * pulse),
                    )
                }
            }
            // The direction arrow (design: the sage triangle above the N circle). Drawn first, so the
            // circle sits over its base, and rotated by the North bearing so it always points
            // radially OUTWARD from the dial centre — at North=0 that is straight up, exactly the
            // prototype. It reads as a compass needle: this is the way North is pointing.
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .rotate(model.northDegrees.toFloat()),
            ) {
                val w = size.width
                val cx = w / 2f
                val apexY = size.height * 0.015f
                val baseY = size.height * 0.20f
                val halfW = w * 0.095f
                val tri = Path().apply {
                    moveTo(cx, apexY)
                    lineTo(cx + halfW, baseY)
                    lineTo(cx - halfW, baseY)
                    close()
                }
                drawPath(tri, color = arrowColor)
            }
            Box(
                modifier = Modifier
                    .size(VastuTheme.sizes.knob)
                    .clip(CircleShape)
                    .background(VastuTheme.colors.paper)
                    .border(VastuTheme.borders.focus, VastuTheme.colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VText(text = "N", style = VastuTheme.type.mono, color = VastuTheme.colors.primary)
            }
        }
    }
}
