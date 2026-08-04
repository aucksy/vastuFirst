package com.vastufirst.app.render

/**
 * The standard configuration matrix (UI-POLISH.md §6.4) — every screen is rendered at every one of
 * these. Shared by every screenshot test so "the matrix" is defined once.
 *
 * [qualifiers] are Robolectric device qualifiers, applied with a leading `+` so they merge onto the
 * default device. ⚠ Robolectric validates that tokens are in **canonical Android resource-qualifier
 * order** and throws `IllegalArgumentException: failed to parse qualifiers` otherwise. The order
 * (relevant subset) is: locale → layout-direction → smallestWidth → **width (wNNNdp)** → **height
 * (hNNNdp)** → orientation → ui-mode → **night** → **density**. So locale comes FIRST and `night`
 * comes BEFORE density — `...-night-xhdpi`, never `...-xhdpi-night`.
 *
 * [fontScale] is applied at the Compose level via LocalDensity, because font scale is NOT a resource
 * qualifier — a screen that fits at 1.0 and clips at 2.0 is a defect this harness exists to catch.
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
    // Baseline canvas: 412×915 dp at xhdpi. Locale/night are prepended/inserted in canonical order.
    //
    // ⚠⚠ EVERY config names its orientation EXPLICITLY (`-port` / `-land`) — this is load-bearing,
    // not pedantry. A leading `+` makes setQualifiers MERGE onto the current configuration, and the
    // harness applies these configs in sequence within one test method. The first -land version of
    // this file (4 Aug 2026) set orientation once, in the landscape config, and every config after
    // it silently INHERITED landscape — including the whole measurement pass, which runs after the
    // capture pass. The geometry gate then reported phantom clipping on dozens of screens that were
    // never touched. `-port` on every portrait config makes each entry self-contained, so no config
    // can be polluted by whichever ran before it.
    // Public so the scrolled bottom-half captures (LongScreenBottomScreenshotTest) render in the
    // exact same window the rest of the matrix uses — one definition of "the baseline phone".
    const val BASE = "+w412dp-h915dp-port-xhdpi"

    val configs: List<RenderConfig> = listOf(
        // 1 — the baseline / design-reference size, light, scale 1.0, English.
        RenderConfig("baseline", BASE),
        // 2 — system dark. The app forces its single light palette; this proves it does not invert.
        //     night MUST sit before density in the qualifier order.
        RenderConfig("dark", "+w412dp-h915dp-port-night-xhdpi"),
        // 3 — early clipping.
        RenderConfig("font1_3", BASE, fontScale = 1.3f),
        // 4 — fixed heights clipping, screens outgrowing the viewport.
        RenderConfig("font2_0", BASE, fontScale = 2.0f),
        // 5 — the most common Indian phone width; where rows shatter.
        RenderConfig("w360", "+w360dp-h800dp-port-xhdpi"),
        // 6 — a 360 dp phone set to "Display size = Largest" behaves like this.
        RenderConfig("w320", "+w320dp-h711dp-port-xhdpi"),
        // 7 — landscape; unreachable CTAs.
        //     ⚠ The `-land` token is NOT optional (audit D1). Without it Robolectric normalises the
        //     requested 854×480 back to a 480 dp-wide PORTRAIT window, so every "landscape" golden
        //     ever recorded before 4 Aug 2026 was actually a narrow portrait phone — the short-
        //     viewport CTA question had never been tested. Canonical position: after height,
        //     before ui-mode/night.
        RenderConfig("landscape", "+w854dp-h480dp-land-xhdpi"),
        // 8 — pseudolocale, ~2× text expansion (affects resource strings once they exist).
        //     locale comes first in the qualifier order.
        RenderConfig("pseudo_en", "+en-rXA-w412dp-h915dp-port-xhdpi"),
        // 9 — pseudolocale, RTL mirroring (mirrors layout regardless of strings).
        RenderConfig("rtl", "+ar-rXB-w412dp-h915dp-port-xhdpi"),
        // 10 — Devanagari / Tamil clipping.
        RenderConfig("hi", "+hi-rIN-w412dp-h915dp-port-xhdpi"),
        RenderConfig("ta", "+ta-rIN-w412dp-h915dp-port-xhdpi"),
    )
}
