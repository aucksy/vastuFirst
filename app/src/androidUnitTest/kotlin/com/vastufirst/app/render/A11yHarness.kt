package com.vastufirst.app.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityCheckOptions
import com.github.takahirom.roborazzi.RoborazziATFAccessibilityChecker
import com.github.takahirom.roborazzi.checkRoboAccessibility
import com.vastufirst.designsystem.theme.VastuTheme
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The accessibility pass (UI-POLISH.md §6.6) — Google's Accessibility Test Framework run INSIDE the
 * same headless Robolectric render via Roborazzi's `checkRoboAccessibility`. It turns contrast,
 * touch-target size, missing/duplicate labels and traversal order from "needs a device" into a JVM
 * check — no emulator. Findings are ratcheted like L1 (scripts/check-a11y-manifest.mjs), never a hard
 * gate: a11y is additive, and the render + L1 gates already catch the highest-severity geometry.
 *
 * ⚠ This is the most version-sensitive piece of the harness (skill note). Robustness choices for the
 * pinned Roborazzi 1.60.0:
 *  - Run at the BASELINE config only. ATF's added value over the L1 gate is contrast + label quality
 *    + traversal, which are config-independent; touch targets (which DO vary by width) are already
 *    measured per-config by L1. One config keeps the ATF pass fast and its bitmap work bounded.
 *  - `CheckLevel.Error` → `checkRoboAccessibility` THROWS an AccessibilityViewCheckException listing
 *    the error-level findings. We catch **Throwable** (not the typed exception) and read the count via
 *    reflection `getResults()`, so nothing here depends on the ATF/espresso types being on the test
 *    COMPILE classpath — only Roborazzi's own `checkRoboAccessibility` is imported. The caught
 *    exception is swallowed: this test never fails the build; the ratchet script decides.
 *  - ATF is skipped by the library below API 34; we render at SDK 35, so it runs.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
fun writeA11yManifest(screen: String, content: @Composable () -> Unit) {
    val cfg = RenderMatrix.configs.first()          // baseline (412×915, light, scale 1.0, en)
    RuntimeEnvironment.setQualifiers(cfg.qualifiers)

    var count = 0
    var detail = ""
    var errored = false
    runComposeUiTest {
        setContent { VastuTheme { content() } }
        try {
            onRoot().checkRoboAccessibility(
                roborazziATFAccessibilityCheckOptions = RoborazziATFAccessibilityCheckOptions(
                    failureLevel = RoborazziATFAccessibilityChecker.CheckLevel.Error,
                ),
            )
            // No exception → no error-level findings.
        } catch (e: Throwable) {
            val results = resultsOf(e)
            if (results != null) {
                count = results.size
                detail = results.joinToString(" | ") { it.toString().replace('\n', ' ').take(160) }
            } else {
                // Not the shape we expected — record it honestly rather than counting a phantom 0.
                errored = true
                detail = "checkRoboAccessibility threw ${e.javaClass.simpleName}: ${e.message?.take(300)}"
                count = 1
            }
        }
    }

    val json = buildString {
        append("{\n")
        append("  \"screen\": ${jsonStr(screen)},\n")
        append("  \"config\": ${jsonStr(cfg.name)},\n")
        append("  \"errored\": $errored,\n")
        append("  \"count\": $count,\n")
        append("  \"detail\": ${jsonStr(detail)}\n")
        append("}\n")
    }
    File("build/a11y-manifest/${screen}.json").apply {
        parentFile?.mkdirs()
        writeText(json)
    }
}

/**
 * Pull the ATF result list off the thrown exception via reflection, so this file never needs the ATF
 * exception type on its compile classpath. `AccessibilityViewCheckException.getResults()` returns the
 * list of failures; anything else (a different throwable) yields null → recorded as "errored".
 */
private fun resultsOf(e: Throwable): List<*>? =
    try {
        val m = e.javaClass.getMethod("getResults")
        m.invoke(e) as? List<*>
    } catch (_: Throwable) {
        null
    }

private fun jsonStr(s: String?): String =
    if (s == null) "null"
    else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
