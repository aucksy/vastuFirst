package com.vastufirst.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.vastufirst.designsystem.foundation.semanticsLabel
import com.vastufirst.designsystem.theme.VastuTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One room drawn on the plan: normalised rect in 0..100 plan space, coloured by its verdict. */
data class ZoneMapRoom(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val label: String,
    val zoneText: String,           // e.g. "NE ✕" / "SW ✓"
    val fill: Color,
    val stroke: Color,
    val zoneTextColor: Color,
)

/** One directional wedge of the compass ring, in fixed order N, NE, E, SE, S, SW, W, NW. */
data class ZoneWedge(val label: String, val color: Color)

/** Everything the [ZoneMap] needs to draw — computed by the app from an engine Analysis. */
data class ZoneMapModel(
    val rooms: List<ZoneMapRoom>,
    val wedges: List<ZoneWedge>,     // 8, in N..NW order
    val northDegrees: Float,
    val centreColor: Color,
)

/**
 * The signature plan view (§6.3/§6.4 · VastuCompass.dc.html): a directional wedge ring that
 * rotates with North, the open Brahmasthan centre, the plan outline, and each room coloured by
 * its verdict. The exact centre stays clean — no logo, no watermark (the rivals' worst flaw).
 *
 * Purely a renderer of a pre-computed [ZoneMapModel]; it depends on no engine types, so the
 * module stays `shared`-free.
 */
@Composable
fun ZoneMap(
    model: ZoneMapModel,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    contentDescription: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val zoneStyle = VastuTheme.type.caption
    val ink = VastuTheme.colors.textSecondary
    val line = VastuTheme.colors.borderDefault
    val faint = VastuTheme.colors.borderStrong
    val dial = VastuTheme.colors.primary
    val paper = VastuTheme.colors.paper
    val roomLabelInk = VastuTheme.colors.textPrimary
    val desc = contentDescription

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(if (desc != null) Modifier.semanticsLabel(desc) else Modifier)
    ) {
        val s = min(size.width, size.height) / 160f
        fun off(ux: Float, uy: Float) = Offset((ux + 30f) * s, (uy + 30f) * s)
        val centre = off(50f, 50f)
        val ringR = 66f * s
        val north = model.northDegrees
        fun rad(bearingDeg: Float) = (bearingDeg) * (kotlin.math.PI.toFloat() / 180f)

        // Outer ring.
        drawCircle(color = line, radius = ringR, center = centre, style = Stroke(width = 0.6f * s))

        // 8 directional wedges, rotated by North (drawArc: 0° = +x, clockwise; bearing→canvas = −90).
        model.wedges.forEachIndexed { i, w ->
            val startBearing = i * 45f - 22.5f + north
            drawArc(
                color = w.color.copy(alpha = 0.13f),
                startAngle = startBearing - 90f,
                sweepAngle = 45f,
                useCenter = true,
                topLeft = Offset(centre.x - ringR, centre.y - ringR),
                size = Size(ringR * 2, ringR * 2),
            )
            if (showLabels) {
                val m = rad(i * 45f + north)
                val lp = Offset(centre.x + 73f * s * sin(m), centre.y - 73f * s * cos(m))
                drawCentered(measurer, w.label, lp, zoneStyle.copy(color = ink))
            }
        }

        // Brahmasthan centre — kept open, drawn faint, never covered.
        drawCircle(color = model.centreColor.copy(alpha = 0.13f), radius = 11f * s, center = centre)

        // Plan outline (the analysis square).
        drawRect(
            color = faint,
            topLeft = off(4f, 4f),
            size = Size(92f * s, 92f * s),
            style = Stroke(width = 0.6f * s),
        )

        // Rooms, coloured by verdict.
        model.rooms.forEach { r ->
            val tl = off(4f + r.x / 100f * 92f, 4f + r.y / 100f * 92f)
            val sz = Size(r.w / 100f * 92f * s, r.h / 100f * 92f * s)
            drawRect(color = r.fill, topLeft = tl, size = sz)
            drawRect(color = r.stroke, topLeft = tl, size = sz, style = Stroke(width = 0.7f * s))
            if (showLabels) {
                val cx = tl.x + sz.width / 2f
                val cy = tl.y + sz.height / 2f
                drawCentered(measurer, r.label, Offset(cx, cy - 3f * s), zoneStyle.copy(color = roomLabelInk))
                drawCentered(measurer, r.zoneText, Offset(cx, cy + 5f * s), zoneStyle.copy(color = r.zoneTextColor))
            }
        }

        // North line + marker.
        val nEnd = Offset(centre.x + 56f * s * sin(rad(north)), centre.y - 56f * s * cos(rad(north)))
        drawLine(
            color = dial,
            start = centre,
            end = nEnd,
            strokeWidth = 0.7f * s,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * s, 2f * s)),
        )
        val nMark = Offset(centre.x + ringR * sin(rad(north)), centre.y - ringR * cos(rad(north)))
        drawCircle(color = paper, radius = 7f * s, center = nMark)
        drawCircle(color = dial, radius = 7f * s, center = nMark, style = Stroke(width = 1f * s))
        drawCentered(measurer, "N", nMark, zoneStyle.copy(color = dial))
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val res = measurer.measure(text, style)
    drawText(res, topLeft = Offset(at.x - res.size.width / 2f, at.y - res.size.height / 2f))
}
