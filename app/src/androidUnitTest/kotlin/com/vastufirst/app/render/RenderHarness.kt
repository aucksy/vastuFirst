package com.vastufirst.app.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.vastufirst.designsystem.theme.VastuTheme
import org.robolectric.RuntimeEnvironment

/**
 * Render [content] once per configuration in [RenderMatrix] and write a golden PNG for each
 * (UI-POLISH.md §6). This is the whole point of the harness: the build finally *draws* the screen.
 *
 * Goldens land under `src/androidUnitTest/roborazzi/<screen>/` (see the `roborazzi { outputDir }`
 * block in build.gradle.kts) so CI can commit them back — build/ is git-ignored and could not keep
 * them (the Now-in-Android bootstrap, §6.3).
 *
 * `content` is wrapped in [VastuTheme] here, exactly as `MainActivity` wraps the whole app, so a
 * screen renders through the same theme the phone would use. Font scale is applied via LocalDensity
 * because it is not a resource qualifier (§6.4).
 *
 * NOTE this uses Roborazzi's self-contained composable capture (no Activity, no `createComposeRule`):
 * it is the mechanism Now-in-Android uses for multi-device screenshots and needs no emulator. The L1
 * *measurement* manifest (semantics geometry) is produced separately, because that needs the
 * semantics tree — see the manifest emitter.
 */
fun captureAcrossMatrix(screen: String, content: @Composable () -> Unit) {
    RenderMatrix.configs.forEach { cfg ->
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        captureRoboImage("$screen/${screen}__${cfg.name}.png") {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, cfg.fontScale),
            ) {
                VastuTheme { content() }
            }
        }
    }
}
