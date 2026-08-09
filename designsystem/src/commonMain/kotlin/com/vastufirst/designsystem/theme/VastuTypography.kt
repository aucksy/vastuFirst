// VastuTypography.kt — the type ramp (Impl PRD §3.4; ramp from the handoff).
//
// The ramp (from handoff/VastuTheme.kt comments):
//   display  Marcellus 34/1.2 (400)   h1 28/1.25   h2 22/1.3    h3 18/1.35
//   body-lg  DM Sans 18/1.5           body 16/1.55 body-sm 14/1.5
//   label    DM Sans 14/1.2 (600)     caption DM Mono 12/1.4    mono 16/1.2 (500)
//
// FONTS (Block A-2): the Latin faces are now BUNDLED as OFL files in
// commonMain/composeResources/font — Marcellus (single weight), DM Sans (400/500/700),
// DM Mono (400/500). Two ramp weights are reconciled to the cuts we actually ship:
//   - Marcellus is single-weight, so h2/h3 use its natural 400 (never faux-bold a serif).
//   - DM Sans ships no 600, so `label` uses Medium (500) — the nearest real cut.
//
// ⛔ LATIN ONLY, and permanently (CLAUDE.md §2e, 9 Aug 2026). This file used to carry a whole
// script-switching machine — a VastuScript enum, a BCP-47 tag→script mapper, per-script font
// families and an Indic line-height floor — built for a six-language plan that is now cancelled.
// Nothing ever called it with anything but the default, so it was dead on arrival; it is deleted
// rather than left lying around, because a future session reads leftover machinery as an intention.
// The one rule from it that survives on its own merit: NEVER PIN A TEXT CONTAINER'S HEIGHT. That
// was written for stacked Indic marks but it is what breaks at 200 % font scale, which is a real
// setting on a real English phone.
package com.vastufirst.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vastufirst.designsystem.generated.resources.Res
import com.vastufirst.designsystem.generated.resources.dm_mono_medium
import com.vastufirst.designsystem.generated.resources.dm_mono_regular
import com.vastufirst.designsystem.generated.resources.dm_sans_bold
import com.vastufirst.designsystem.generated.resources.dm_sans_medium
import com.vastufirst.designsystem.generated.resources.dm_sans_regular
import com.vastufirst.designsystem.generated.resources.marcellus_regular
import org.jetbrains.compose.resources.Font

/** The three font roles, all bundled OFL faces (see FONTS note above). */
private data class TypeFamilies(
    val display: FontFamily,   // Marcellus (serif)
    val sans: FontFamily,      // DM Sans
    val mono: FontFamily,      // DM Mono
)

@Composable
private fun latinFamilies(): TypeFamilies = TypeFamilies(
    display = FontFamily(Font(Res.font.marcellus_regular, FontWeight.Normal)),
    sans = FontFamily(
        Font(Res.font.dm_sans_regular, FontWeight.Normal),
        Font(Res.font.dm_sans_medium, FontWeight.Medium),
        Font(Res.font.dm_sans_bold, FontWeight.Bold),
    ),
    mono = FontFamily(
        Font(Res.font.dm_mono_regular, FontWeight.Normal),
        Font(Res.font.dm_mono_medium, FontWeight.Medium),
    ),
)

private val LatinLineHeights = mapOf(
    "score" to 1.00f,
    "display" to 1.20f, "h1" to 1.25f, "h2" to 1.30f, "h3" to 1.35f,
    "bodyLg" to 1.50f, "body" to 1.55f, "bodySm" to 1.50f,
    "label" to 1.20f, "caption" to 1.40f, "mono" to 1.20f,
)

/**
 * Letter-spacing (tracking), in `em`, straight from the design ramp's `tr` column. Everything not
 * listed here is 0 — the design only tracks the four small/tight styles.
 */
private val LatinTracking = mapOf(
    "score" to 0f,
    "display" to 0f, "h1" to 0f, "h2" to 0f, "h3" to 0.005f,
    "bodyLg" to 0f, "body" to 0f, "bodySm" to 0f,
    "label" to 0.01f, "caption" to 0.02f, "mono" to 0.02f,
)

/** Line-height for a role, straight from the design ramp. */
private fun lineHeightMultiplier(role: String): Float = LatinLineHeights.getValue(role)

/** Letter-spacing for a role, straight from the design ramp's `tr` column. */
private fun trackingEm(role: String): Float = LatinTracking.getValue(role)

private fun style(
    fontSizeSp: Float,
    role: String,
    family: FontFamily,
    weight: FontWeight,
): TextStyle {
    val size: TextUnit = fontSizeSp.sp
    return TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = (fontSizeSp * lineHeightMultiplier(role)).sp,
        letterSpacing = trackingEm(role).em,
        // Center text within its line box; never clip ascenders/descenders.
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

/** Build the full type ramp. Composable because the bundled faces are Compose resources. */
@Composable
fun vastuTypography(): VastuTypography {
    val f = latinFamilies()
    return VastuTypography(
        scoreDisplay = style(56f, "score", f.display, FontWeight.Normal),
        display = style(34f, "display", f.display, FontWeight.Normal),
        h1      = style(28f, "h1", f.display, FontWeight.Normal),
        h2      = style(22f, "h2", f.display, FontWeight.Normal),  // Marcellus is single-weight
        h3      = style(18f, "h3", f.display, FontWeight.Normal),
        bodyLg  = style(18f, "bodyLg", f.sans, FontWeight.Normal),
        body    = style(16f, "body", f.sans, FontWeight.Normal),
        bodySm  = style(14f, "bodySm", f.sans, FontWeight.Normal),
        label   = style(14f, "label", f.sans, FontWeight.Medium),  // DM Sans ships no 600
        caption = style(12f, "caption", f.mono, FontWeight.Normal),
        mono    = style(16f, "mono", f.mono, FontWeight.Medium),
    )
}
