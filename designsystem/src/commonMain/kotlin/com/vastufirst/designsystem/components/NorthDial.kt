package com.vastufirst.designsystem.components

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val dim = maxWidth
        ZoneMap(
            model = model,
            modifier = Modifier.matchParentSize(),
            showLabels = true,
            contentDescription = contentDescription,
        )

        // Input layer — drag or tap sets North to the bearing of the touch from the centre.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> emit(change.position, size); change.consume() }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> emit(pos, size) }
                }
        )

        // Knob — positioned on the ring at the current North bearing.
        val ringFrac = 66f / 160f
        val nrad = model.northDegrees * (PI.toFloat() / 180f)
        val knobX = dim * (0.5f + ringFrac * sin(nrad))
        val knobY = dim * (0.5f - ringFrac * cos(nrad))
        val hit = VastuTheme.sizes.knobHit
        val arrowColor = VastuTheme.colors.primary          // captured for the DrawScope below
        Box(
            modifier = Modifier
                .offset(x = knobX - hit / 2f, y = knobY - hit / 2f)
                .size(hit),
            contentAlignment = Alignment.Center,
        ) {
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
