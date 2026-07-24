package com.vastufirst.app.render

/**
 * The standard configuration matrix (UI-POLISH.md §6.4) — every screen is rendered at every one of
 * these. Shared by every screenshot test so "the matrix" is defined once.
 *
 * [qualifiers] are Robolectric device qualifiers, applied with a leading `+` so they merge onto the
 * default device (overriding only width/height/density/locale/night). [fontScale] is applied at the
 * Compose level via LocalDensity, because font scale is NOT a resource qualifier — a screen that
 * fits at 1.0 and clips at 2.0 is one of the defects this harness exists to catch.
 *
 * Density is pinned to xhdpi (2×) for every config so a committed golden's pixel size is a clean
 * function of its dp size, and so anti-aliasing is identical run to run on the pinned CI image.
 */
data class RenderConfig(
    val name: String,
    val qualifiers: String,
    val fontScale: Float = 1f,
)

object RenderMatrix {
    private const val BASE = "+w412dp-h915dp-xhdpi"

    val configs: List<RenderConfig> = listOf(
        // 1 — the baseline / design-reference size, light, scale 1.0, English.
        RenderConfig("baseline", BASE),
        // 2 — system dark. The app forces its single light palette; this proves it does not invert.
        RenderConfig("dark", "$BASE-night"),
        // 3 — early clipping.
        RenderConfig("font1_3", BASE, fontScale = 1.3f),
        // 4 — fixed heights clipping, screens outgrowing the viewport.
        RenderConfig("font2_0", BASE, fontScale = 2.0f),
        // 5 — the most common Indian phone width; where rows shatter.
        RenderConfig("w360", "+w360dp-h800dp-xhdpi"),
        // 6 — a 360 dp phone set to "Display size = Largest" behaves like this.
        RenderConfig("w320", "+w320dp-h711dp-xhdpi"),
        // 7 — landscape; unreachable CTAs.
        RenderConfig("landscape", "+w854dp-h480dp-xhdpi"),
        // 8 — pseudolocale, ~2× text expansion (affects resource strings once they exist).
        RenderConfig("pseudo_en", "$BASE-en-rXA"),
        // 9 — pseudolocale, RTL mirroring (mirrors layout regardless of strings).
        RenderConfig("rtl", "$BASE-ar-rXB"),
        // 10 — Devanagari / Tamil clipping.
        RenderConfig("hi", "$BASE-hi-rIN"),
        RenderConfig("ta", "$BASE-ta-rIN"),
    )
}
