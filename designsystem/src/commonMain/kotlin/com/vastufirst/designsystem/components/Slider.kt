package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * Owned horizontal slider — no Material control (Impl PRD §7, so iOS is a mechanical re-skin).
 * Drag or tap sets [value] within [valueRange]. Used for the North bearing on Mark-North.
 */
@Composable
fun VastuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)

    fun setFromX(x: Float, widthPx: Int) {
        val f = (x / widthPx.toFloat()).coerceIn(0f, 1f)
        onValueChange(start + f * (end - start))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(VastuTheme.sizes.knobHit)) {
        val dim = maxWidth
        val thumb = VastuTheme.sizes.sliderThumb

        // Track (full) + active portion, vertically centred.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(VastuTheme.sizes.sliderTrack)
                .clip(VastuTheme.shapes.full)
                .background(VastuTheme.colors.borderDefault),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(VastuTheme.sizes.sliderTrack)
                .clip(VastuTheme.shapes.full)
                .background(VastuTheme.colors.primary),
        )

        // Thumb.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (dim - thumb) * fraction)
                .size(thumb)
                .clip(CircleShape)
                .background(VastuTheme.colors.paper)
                .border(VastuTheme.borders.focus, VastuTheme.colors.primary, CircleShape),
        )

        // Input layer over the whole strip.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> setFromX(o.x, size.width) },
                    ) { change, _ -> setFromX(change.position.x, size.width); change.consume() }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o -> setFromX(o.x, size.width) }
                },
        )
    }
}
