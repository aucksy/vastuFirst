package com.vastufirst.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The root modifier for every screen (UI-POLISH.md §3.A — window insets).
 *
 * `targetSdk = 35` means Android 15 draws this app edge-to-edge with **no opt-out**. Without
 * handling it, the status bar overlaps the top of every screen and the gesture-navigation pill
 * covers the primary button at the bottom — on the screens that do not scroll, that button becomes
 * partly unreachable. That is exactly what shipped in v0.2.1.
 *
 * The split is deliberate:
 *   - [background] fills the **whole window**, behind the system bars, so the paper colour runs to
 *     the physical edges and the bars read as part of the app rather than as black straps.
 *   - `safeDrawingPadding()` then insets the **content** — status bar, navigation bar and any
 *     display cutout — so nothing the user must read or tap sits underneath them.
 *
 * Apply it before any `verticalScroll`, so the scroll viewport itself is inset and content stops
 * cleanly at the bars instead of sliding under them:
 *
 * ```
 * Column(Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(s6))
 * ```
 *
 * `scripts/check-screen-roots.sh` fails the build if a screen root does not go through this.
 */
fun Modifier.screenRoot(background: Color): Modifier =
    fillMaxSize()
        .background(background)
        .safeDrawingPadding()
