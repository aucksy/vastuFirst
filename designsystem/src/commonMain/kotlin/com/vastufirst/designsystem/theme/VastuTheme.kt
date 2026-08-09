// VastuTheme.kt — commonMain · Sage & Gold · the single source of truth.
// No colour, size, or radius appears anywhere downstream as a raw value.
// Material3 ColorScheme cannot hold ~38 semantic colours, so this is an OWNED theme
// exposed through CompositionLocals — not a MaterialTheme override.
//
// Values copied EXACTLY from the design handoff (handoff/VastuTheme.kt + tokens.json).
// Do not "improve" the palette; it passed a contrast and colourblind audit in the handoff.
package com.vastufirst.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class VastuColors(
    val paper: Color, val surface: Color, val surfaceRaised: Color,
    val primary: Color, val primaryDark: Color, val onPrimary: Color,
    val secondary: Color, val secondaryText: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    val borderDefault: Color, val borderStrong: Color, val borderFocus: Color,
    val zoneN: Color, val zoneNE: Color, val zoneE: Color, val zoneSE: Color,
    val zoneS: Color, val zoneSW: Color, val zoneW: Color, val zoneNW: Color, val zoneCentre: Color,
    val verdictIdeal: Color, val verdictAcceptable: Color, val verdictSuboptimal: Color,
    val verdictDefect: Color, val verdictNotAssessed: Color,
    val scoreStrong: Color, val scoreWorkable: Color, val scoreAttention: Color,
    val provenanceText: Color, val provenanceDeriv: Color, val provenanceMod: Color, val provenanceDisp: Color,
    val success: Color, val warning: Color, val error: Color, val info: Color,
)

val SageGold = VastuColors(
    paper = Color(0xFFF8F6F0), surface = Color(0xFFF2EEE4), surfaceRaised = Color(0xFFFFFFFF),
    primary = Color(0xFF7A9E7E), primaryDark = Color(0xFF5F8465), onPrimary = Color(0xFF232A22),
    secondary = Color(0xFFC9A227), secondaryText = Color(0xFF6F5410),
    // ⭐ textTertiary is 0xFF686C61, three values darker than the design's 0xFF6B7064. MEASURED, not
    // preferred: the design value contrasts 4.39 : 1 against the card surface where WCAG AA requires
    // 4.5, so every small grey label in the app — the compass letters, MOVE / SIZE / ROOM TYPE, the
    // selected room's "2 × 2 · North-West" — has been below the legibility bar on every build. Hue and
    // saturation are untouched and only the lightness moves, so the tier still reads as the quietest
    // of the three (4.64 : 1 against textSecondary's 6.90). Declared in check-design-fidelity.mjs.
    textPrimary = Color(0xFF232A22), textSecondary = Color(0xFF4B5347), textTertiary = Color(0xFF686C61),
    borderDefault = Color(0xFFDDDED3), borderStrong = Color(0xFFB6BBA8), borderFocus = Color(0xFF4C7355),
    zoneN = Color(0xFF2E8B8B), zoneNE = Color(0xFF2F6FBF), zoneE = Color(0xFFE0A21E), zoneSE = Color(0xFFE0662F),
    zoneS = Color(0xFFC83B32), zoneSW = Color(0xFF8A6A45), zoneW = Color(0xFF6A5FB0), zoneNW = Color(0xFF4E9A55), zoneCentre = Color(0xFF9A57B0),
    verdictIdeal = Color(0xFF3E9256), verdictAcceptable = Color(0xFF8FBE95), verdictSuboptimal = Color(0xFFD68C18),
    verdictDefect = Color(0xFFC43F35), verdictNotAssessed = Color(0xFF948C84),
    scoreStrong = Color(0xFF3E9256), scoreWorkable = Color(0xFFD68C18), scoreAttention = Color(0xFFC43F35),
    provenanceText = Color(0xFF3F7D5E), provenanceDeriv = Color(0xFF9A6B33), provenanceMod = Color(0xFF5B7089), provenanceDisp = Color(0xFF7A5AA6),
    success = Color(0xFF3E9256), warning = Color(0xFFD68C18), error = Color(0xFFC43F35), info = Color(0xFF2F6FBF),
)

@Immutable
data class VastuSpacing(
    val s1: Dp = 4.dp, val s2: Dp = 8.dp, val s3: Dp = 12.dp, val s4: Dp = 16.dp,
    val s6: Dp = 24.dp, val s8: Dp = 32.dp, val s10: Dp = 40.dp, val s12: Dp = 48.dp, val s16: Dp = 64.dp,
)

@Immutable
data class VastuShapes(
    val sm: CornerBasedShape = RoundedCornerShape(8.dp),
    val md: CornerBasedShape = RoundedCornerShape(14.dp),
    val lg: CornerBasedShape = RoundedCornerShape(22.dp),
    val full: CornerBasedShape = RoundedCornerShape(percent = 50),
)

@Immutable
data class VastuElevation(
    val flat: Dp = 0.dp, val raised: Dp = 2.dp, val overlay: Dp = 8.dp, val modal: Dp = 20.dp,
)

/** Border widths (design system §04). Every stroke in the app reads one of these. */
@Immutable
data class VastuBorders(
    val hairline: Dp = 1.dp,     // fine dividers / svg-like rules (tokens.json: border-hairline = 1)
    val regular: Dp = 1.dp,      // default card & control border
    val strong: Dp = 1.5.dp,     // selected / emphasised outline
    val focus: Dp = 2.dp,        // focused field / active selection
)

/**
 * Fixed element sizes that are NOT on the 4dp spacing scale but are still tokens, so no screen
 * or component ever hardcodes a raw `.dp` (review gate). Proportional dimensions (dial diameter,
 * plan grid) use fillMaxWidth/aspectRatio instead and need no token here.
 */
@Immutable
data class VastuSizes(
    val minTouch: Dp = 48.dp,        // a11y floor — every interactive target (dial included)
    val control: Dp = 48.dp,         // standard button / field / chip height
    val cta: Dp = 52.dp,             // primary full-width call-to-action height
    val iconXs: Dp = 16.dp,
    val iconSm: Dp = 20.dp,
    val icon: Dp = 24.dp,
    val iconLg: Dp = 30.dp,
    val tileSm: Dp = 38.dp,          // list-row leading icon tile
    val tile: Dp = 46.dp,            // method-choice leading icon tile
    val knobHit: Dp = 54.dp,         // North-dial knob touch area
    val knob: Dp = 40.dp,            // North-dial knob visual circle
    val sliderThumb: Dp = 26.dp,
    val sliderTrack: Dp = 6.dp,
    val handleGrip: Dp = 12.dp,      // floor-plan editor: the DRAWN corner grip (solid dot)
    val handleTouch: Dp = 48.dp,     // …and the invisible target around it (a cell is only 34–45dp)
    val progressTrack: Dp = 8.dp,    // score bar
    val balanceTrack: Dp = 14.dp,    // report balance meter — thicker than the score bar on purpose:
                                     // it carries three colours side by side, not one fill
    val planThumb: Dp = 60.dp,       // saved-plan thumbnail
    val dot: Dp = 6.dp,              // provenance / status dot
    val logo: Dp = 54.dp,            // welcome brand mark
)

@Immutable
data class VastuTypography(
    val scoreDisplay: TextStyle,   // the oversized Marcellus score number (Score screen)
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val bodyLg: TextStyle, val body: TextStyle, val bodySm: TextStyle,
    val label: TextStyle, val caption: TextStyle, val mono: TextStyle,
)

val LocalVastuColors     = staticCompositionLocalOf<VastuColors> { error("VastuTheme missing") }
val LocalVastuSpacing    = staticCompositionLocalOf { VastuSpacing() }
val LocalVastuShapes     = staticCompositionLocalOf { VastuShapes() }
val LocalVastuElevation  = staticCompositionLocalOf { VastuElevation() }
val LocalVastuBorders    = staticCompositionLocalOf { VastuBorders() }
val LocalVastuSizes      = staticCompositionLocalOf { VastuSizes() }
val LocalVastuTypography = staticCompositionLocalOf<VastuTypography> { error("VastuTheme missing") }

@Composable
fun VastuTheme(
    typography: VastuTypography = vastuTypography(),   // locale-aware — see VastuTypography.kt
    content: @Composable () -> Unit,
) = CompositionLocalProvider(
    LocalVastuColors provides SageGold,
    LocalVastuSpacing provides VastuSpacing(),
    LocalVastuShapes provides VastuShapes(),
    LocalVastuElevation provides VastuElevation(),
    LocalVastuBorders provides VastuBorders(),
    LocalVastuSizes provides VastuSizes(),
    LocalVastuTypography provides typography,
    content = content,
)

object VastuTheme {
    val colors  @Composable get() = LocalVastuColors.current
    val spacing @Composable get() = LocalVastuSpacing.current
    val shapes  @Composable get() = LocalVastuShapes.current
    val elevation @Composable get() = LocalVastuElevation.current
    val borders @Composable get() = LocalVastuBorders.current
    val sizes   @Composable get() = LocalVastuSizes.current
    val type    @Composable get() = LocalVastuTypography.current
}
