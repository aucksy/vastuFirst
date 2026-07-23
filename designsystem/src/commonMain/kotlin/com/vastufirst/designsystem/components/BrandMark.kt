package com.vastufirst.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The VastuFirst mark (design system logo): a mandala square with its 3×3 grid, a North arrow,
 * and the gold Brahmasthan dot. Drawn from tokens so it re-colours with the theme.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = VastuTheme.sizes.logo) {
    val line = VastuTheme.colors.borderDefault
    val tint = VastuTheme.colors.primary.copy(alpha = 0.18f)
    val dial = VastuTheme.colors.primary
    val gold = VastuTheme.colors.secondary
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 40f   // design grid is 40 units
        fun p(x: Float, y: Float) = Offset(x * u, y * u)

        // Outer rounded square.
        drawRoundRect(
            color = line,
            topLeft = p(2f, 2f),
            size = Size(36f * u, 36f * u),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * u, 4f * u),
            style = Stroke(width = 1.4f * u),
        )
        // 3×3 grid lines.
        listOf(14f, 26f).forEach { gx ->
            drawLine(line, p(gx, 2f), p(gx, 38f), strokeWidth = 0.8f * u)
            drawLine(line, p(2f, gx), p(38f, gx), strokeWidth = 0.8f * u)
        }
        // Centre tile tint.
        drawRect(color = tint, topLeft = p(14f, 14f), size = Size(12f * u, 12f * u))
        // North arrow.
        val arrow = Path().apply {
            moveTo(20f * u, 5f * u); lineTo(23.4f * u, 15.6f * u)
            lineTo(20f * u, 13.4f * u); lineTo(16.6f * u, 15.6f * u); close()
        }
        drawPath(arrow, color = dial)
        // Gold Brahmasthan dot.
        drawCircle(color = gold, radius = 2.6f * u, center = p(20f, 20f))
    }
}
