package com.vastufirst.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    /** The plan letter-code ("K", "BR") drawn when [label] is too wide — never an ellipsis. */
    val microLabel: String = "",
    /** The verdict mark alone ("✓"/"✕"/"△") — the last ink to survive on a shrinking tile. */
    val verdictGlyph: String = "",
)

/** One directional wedge of the compass ring, in fixed order N, NE, E, SE, S, SW, W, NW. */
data class ZoneWedge(val label: String, val color: Color)

/** Everything the [ZoneMap] needs to draw — computed by the app from an engine Analysis. */
data class ZoneMapModel(
    val rooms: List<ZoneMapRoom>,
    val wedges: List<ZoneWedge>,     // 8, in N..NW order
    val northDegrees: Float,
    val centreColor: Color,
    /**
     * ⭐ The user's OWN scanned plan, drawn in the plan square in place of [rooms] (owner,
     * 6 Aug 2026: *"marking the Door and north should happen only on this actual floor plan"*).
     * Null everywhere else, which is every hand-drawn home and every reopened one — those keep the
     * redrawn rectangles they have always shown.
     *
     * It needs no counter-rotation and gets none: North turns the ring, the wedges and the N marker,
     * while the plan underneath has always stayed still. A photograph behaves exactly as the
     * rectangles do.
     *
     * ⚠ Hold it in a `remember`. This is a data class and ImageBitmap compares by identity, so a
     * bitmap decoded afresh each recomposition makes the whole model unequal every frame and defeats
     * the measure cache the dial's drag smoothness depends on.
     */
    val planImage: ImageBitmap? = null,
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
    // A generous measure cache (C14): this Canvas lays out 20+ distinct strings per frame — 8 wedge
    // labels, each room's name + verdict, plus "N"/"Wg". The default cache holds only 8, so every
    // frame every string missed the cache and was re-laid-out; while dragging North that re-layout
    // ran on every degree and showed up as stutter. 64 comfortably holds all labels of a full plan,
    // turning per-frame re-layout into cache hits. Purely a perf change — nothing drawn differs.
    val measurer = rememberTextMeasurer(cacheSize = 64)
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

        // ⭐ The scanned plan, where there is one — drawn into the same 92-unit square the rooms use,
        // aspect-fit and centred. It goes in HERE, before the wedges, and that position is the whole
        // trick: every layer that follows (the wedge tints, the Brahmasthan disc, the plan outline,
        // the North line and marker) then paints over the photograph in the order it always has, so
        // the dial reads identically whether the plan underneath is drawn or photographed.
        model.planImage?.let { img ->
            if (img.width > 0 && img.height > 0) {
                val tl = off(4f, 4f)
                val box = 92f * s
                val k = min(box / img.width, box / img.height)
                val dw = img.width * k
                val dh = img.height * k
                drawImage(
                    image = img,
                    dstOffset = IntOffset((tl.x + (box - dw) / 2f).toInt(), (tl.y + (box - dh) / 2f).toInt()),
                    dstSize = IntSize(dw.toInt(), dh.toInt()),
                )
            }
        }

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

        // Rooms, coloured by verdict — stood down when the user's own plan is showing, because
        // drawing our rectangles on top of their photograph would claim their rooms are where we put
        // them. The wedge ring above still says which direction each part of the picture is in.
        val drawnRooms = if (model.planImage != null) emptyList() else model.rooms
        drawnRooms.forEach { r ->
            val tl = off(4f + r.x / 100f * 92f, 4f + r.y / 100f * 92f)
            val sz = Size(r.w / 100f * 92f * s, r.h / 100f * 92f * s)
            drawRect(color = r.fill, topLeft = tl, size = sz)
            drawRect(color = r.stroke, topLeft = tl, size = sz, style = Stroke(width = 0.7f * s))
            if (showLabels) {
                val cx = tl.x + sz.width / 2f
                val cy = tl.y + sz.height / 2f
                // Labels must never overrun their tile (B7), and they degrade on a MEASURED ladder,
                // never an ellipsis (audit C8): a name too wide for its tile falls back to its plan
                // letter-code ("K", "BR" — the editor tiles' own rung), because "Poo…" says less than
                // "PJ". And the VERDICT mark dies last, not first: when only one line fits, the old
                // code kept the name and dropped the tick/cross, leaving colour as the only verdict
                // signal — which §2 rule 3 forbids. The direction+mark line degrades to the bare mark
                // before it disappears.
                val maxW = sz.width - 2f * s
                fun fitted(vararg candidates: String): String? =
                    candidates.firstOrNull { it.isNotEmpty() && measurer.measure(it, zoneStyle).size.width.toFloat() <= maxW }
                val name = fitted(r.label, r.microLabel)
                val verdict = fitted(r.zoneText, r.verdictGlyph)
                val lineH = measurer.measure("Wg", zoneStyle).size.height.toFloat()
                when {
                    maxW <= 0f || sz.height < lineH -> Unit
                    sz.height >= lineH * 2.2f && name != null && verdict != null -> {
                        val dy = lineH * 0.6f
                        drawCentered(measurer, name, Offset(cx, cy - dy), zoneStyle.copy(color = roomLabelInk), maxW)
                        drawCentered(measurer, verdict, Offset(cx, cy + dy), zoneStyle.copy(color = r.zoneTextColor), maxW)
                    }
                    // One line's worth of room (or only one of the two fits at all): on a scored map
                    // the verdict wins the line; an unscored map has no verdict and the name takes it.
                    verdict != null -> drawCentered(measurer, verdict, Offset(cx, cy), zoneStyle.copy(color = r.zoneTextColor), maxW)
                    name != null -> drawCentered(measurer, name, Offset(cx, cy), zoneStyle.copy(color = roomLabelInk), maxW)
                    else -> Unit
                }
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
        // ⭐ The circle is sized to the GLYPH, not a constant (audit C7). The "N" is sp-typed and
        // doubles at 200 % font while `7f * s` does not move, so the letter poked through its own
        // ring and read as a no-entry sign. Measuring the letter and keeping the ring just outside
        // it means the pair scale together at every font size.
        val nGlyph = measurer.measure("N", zoneStyle)
        val nRadius = maxOf(
            7f * s,
            maxOf(nGlyph.size.width, nGlyph.size.height) / 2f + 2f * s,
        )
        drawCircle(color = paper, radius = nRadius, center = nMark)
        drawCircle(color = dial, radius = nRadius, center = nMark, style = Stroke(width = 1f * s))
        drawCentered(measurer, "N", nMark, zoneStyle.copy(color = dial))
    }
}

private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    style: TextStyle,
    maxWidthPx: Float? = null,
) {
    val res = if (maxWidthPx != null) {
        measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidthPx.toInt().coerceAtLeast(0)),
        )
    } else {
        measurer.measure(text, style)
    }
    drawText(res, topLeft = Offset(at.x - res.size.width / 2f, at.y - res.size.height / 2f))
}
