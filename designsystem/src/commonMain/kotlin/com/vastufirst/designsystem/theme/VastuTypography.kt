// VastuTypography.kt — the locale-aware type ramp (Impl PRD §3.4; ramp from the handoff).
//
// The ramp (from handoff/VastuTheme.kt comments):
//   display  Marcellus 34/1.2 (400)   h1 28/1.25   h2 22/1.3   h3 18/1.35
//   body-lg  DM Sans 18/1.5           body 16/1.55 body-sm 14/1.5
//   label    DM Sans 14/1.2 (600)     caption DM Mono 12/1.4   mono 16/1.2 (500)
//
// Six languages, five non-Latin scripts. Marcellus/DM Sans/DM Mono cover Latin only;
// per-script Noto faces are selected by locale so a Hindi or Tamil screen keeps the same
// weight and rhythm as English. INDIC SCRIPTS GET LINE-HEIGHT 1.5–1.6, not the Latin 1.2 —
// baked in per script. Never pin a text container's height (Product PRD §10, handoff L10n).
//
// FONT SEAM (Phase 0): the exact typefaces are free OFL faces (Marcellus, DM Sans, DM Mono,
// Noto Sans Devanagari/Tamil/Telugu/Bengali). Until the .ttf files are bundled via Compose
// Resources, the families below resolve to the platform defaults — which on Android already
// fall back to Noto for Indic scripts, so the ramp renders in Hindi/Tamil WITHOUT clipping
// (the Phase 0 bar). Swapping in the bundled faces is a change to `familiesFor()` only;
// no call site changes.
package com.vastufirst.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

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

/** The three font roles. Bundled OFL faces slot in here per script (see FONT SEAM above). */
private data class ScriptFamilies(
    val display: FontFamily,   // Marcellus (serif) / Noto Serif <script>
    val sans: FontFamily,      // DM Sans / Noto Sans <script>
    val mono: FontFamily,      // DM Mono / Noto Sans Mono
)

private fun familiesFor(script: VastuScript): ScriptFamilies =
    when (script) {
        // Latin: Marcellus (serif) for display, DM Sans for text, DM Mono for numerals.
        VastuScript.LATIN -> ScriptFamilies(
            display = FontFamily.Serif,
            sans = FontFamily.SansSerif,
            mono = FontFamily.Monospace,
        )
        // Indic: system fallback resolves to the installed Noto <script> faces today;
        // the bundled Noto faces replace these once the .ttf files are added.
        VastuScript.DEVANAGARI, VastuScript.TAMIL, VastuScript.TELUGU, VastuScript.BENGALI ->
            ScriptFamilies(
                display = FontFamily.Serif,
                sans = FontFamily.SansSerif,
                mono = FontFamily.Monospace,
            )
    }

private val LatinLineHeights = mapOf(
    "display" to 1.20f, "h1" to 1.25f, "h2" to 1.30f, "h3" to 1.35f,
    "bodyLg" to 1.50f, "body" to 1.55f, "bodySm" to 1.50f,
    "label" to 1.20f, "caption" to 1.40f, "mono" to 1.20f,
)

/**
 * Indic scripts need more vertical room for stacked marks: floor every line-height at 1.5,
 * cap at 1.6 (Impl PRD §3.4). Latin values already ≥1.5 are left as-is.
 */
private fun lineHeightMultiplier(role: String, script: VastuScript): Float {
    val latin = LatinLineHeights.getValue(role)
    return if (script == VastuScript.LATIN) latin else latin.coerceIn(1.5f, 1.6f)
}

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
        display = style(34f, "display", f.display, FontWeight.Normal, script),
        h1      = style(28f, "h1", f.display, FontWeight.Normal, script),
        h2      = style(22f, "h2", f.display, FontWeight.Medium, script),
        h3      = style(18f, "h3", f.display, FontWeight.Medium, script),
        bodyLg  = style(18f, "bodyLg", f.sans, FontWeight.Normal, script),
        body    = style(16f, "body", f.sans, FontWeight.Normal, script),
        bodySm  = style(14f, "bodySm", f.sans, FontWeight.Normal, script),
        label   = style(14f, "label", f.sans, FontWeight.SemiBold, script),
        caption = style(12f, "caption", f.mono, FontWeight.Normal, script),
        mono    = style(16f, "mono", f.mono, FontWeight.Medium, script),
    )
}
