// VastuTypography.kt — the locale-aware type ramp (Impl PRD §3.4; ramp from the handoff).
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
// Six languages, five non-Latin scripts. Indic scripts still fall back to the platform Noto
// faces (which render Hindi/Tamil without clipping) until their Noto files are bundled in
// Phase 4 localisation; INDIC LINE-HEIGHT stays 1.5–1.6, never the Latin 1.2. Never pin a
// text container's height (Product PRD §10, handoff L10n).
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

/** The scripts VastuFirst ships (Product PRD §6.1 / §7.5). */
enum class VastuScript { LATIN, DEVANAGARI, TAMIL, TELUGU, BENGALI }

/** Map a BCP-47 language tag to its script so the right ramp/faces are chosen. */
fun scriptForLanguage(languageTag: String): VastuScript =
    when (languageTag.lowercase().substringBefore('-')) {
        "hi", "mr" -> VastuScript.DEVANAGARI   // Hindi, Marathi
        "ta" -> VastuScript.TAMIL
        "te" -> VastuScript.TELUGU
        "bn" -> VastuScript.BENGALI
        else -> VastuScript.LATIN               // English + fallback
    }

/** The three font roles. Bundled OFL faces slot in here per script (see FONTS note above). */
private data class ScriptFamilies(
    val display: FontFamily,   // Marcellus (serif) / Noto Serif <script>
    val sans: FontFamily,      // DM Sans / Noto Sans <script>
    val mono: FontFamily,      // DM Mono / Noto Sans Mono
)

@Composable
private fun familiesFor(script: VastuScript): ScriptFamilies =
    when (script) {
        // Latin: bundled Marcellus (display), DM Sans (text), DM Mono (numerals/labels).
        VastuScript.LATIN -> ScriptFamilies(
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
        // Indic: platform fallback resolves to the installed Noto <script> faces today;
        // bundled Noto faces replace these in Phase 4 localisation.
        VastuScript.DEVANAGARI, VastuScript.TAMIL, VastuScript.TELUGU, VastuScript.BENGALI ->
            ScriptFamilies(
                display = FontFamily.Serif,
                sans = FontFamily.SansSerif,
                mono = FontFamily.Monospace,
            )
    }

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

/**
 * Indic scripts need more vertical room for stacked marks: floor every line-height at 1.5,
 * cap at 1.6 (Impl PRD §3.4). Latin values already ≥1.5 are left as-is.
 */
private fun lineHeightMultiplier(role: String, script: VastuScript): Float {
    val latin = LatinLineHeights.getValue(role)
    return if (script == VastuScript.LATIN) latin else latin.coerceIn(1.5f, 1.6f)
}

/**
 * Tracking applies to LATIN ONLY. Positive letter-spacing pulls apart the conjunct clusters and
 * attached matras of Devanagari, Tamil, Telugu and Bengali, which is a legibility defect, not a
 * style — so non-Latin scripts always get 0 regardless of the ramp.
 */
private fun trackingEm(role: String, script: VastuScript): Float =
    if (script == VastuScript.LATIN) LatinTracking.getValue(role) else 0f

private fun style(
    fontSizeSp: Float,
    role: String,
    family: FontFamily,
    weight: FontWeight,
    script: VastuScript,
): TextStyle {
    val size: TextUnit = fontSizeSp.sp
    return TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = (fontSizeSp * lineHeightMultiplier(role, script)).sp,
        letterSpacing = trackingEm(role, script).em,
        // Center text within its (script-appropriate) line box; never clip ascenders/descenders.
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
}

/**
 * Build the full type ramp for a given script. Composable so a screen can react to a
 * runtime language change: `VastuTheme(typography = vastuTypography(scriptForLanguage(tag)))`.
 */
@Composable
fun vastuTypography(script: VastuScript = VastuScript.LATIN): VastuTypography {
    val f = familiesFor(script)
    return VastuTypography(
        scoreDisplay = style(56f, "score", f.display, FontWeight.Normal, script),
        display = style(34f, "display", f.display, FontWeight.Normal, script),
        h1      = style(28f, "h1", f.display, FontWeight.Normal, script),
        h2      = style(22f, "h2", f.display, FontWeight.Normal, script),  // Marcellus is single-weight
        h3      = style(18f, "h3", f.display, FontWeight.Normal, script),
        bodyLg  = style(18f, "bodyLg", f.sans, FontWeight.Normal, script),
        body    = style(16f, "body", f.sans, FontWeight.Normal, script),
        bodySm  = style(14f, "bodySm", f.sans, FontWeight.Normal, script),
        label   = style(14f, "label", f.sans, FontWeight.Medium, script),  // DM Sans ships no 600
        caption = style(12f, "caption", f.mono, FontWeight.Normal, script),
        mono    = style(16f, "mono", f.mono, FontWeight.Medium, script),
    )
}
