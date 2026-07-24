package com.vastufirst.app.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.vastufirst.designsystem.theme.VastuTheme
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.math.roundToInt

/**
 * The hand-written L1 measurement manifest (UI-POLISH.md §6.1). Our pinned Roborazzi (1.60.0)
 * predates `dumpUiTree` (1.69.0), so we walk the Compose semantics tree ourselves and emit JSON
 * per node — testTag, bounds in dp, role, text, clickable — plus the two things a screenshot
 * cannot show and §6.5 insists on:
 *
 *   - **clipping**: `node.size` is the UNCLIPPED layout size; `boundsInRoot` is CLIPPED to
 *     ancestors. If they differ, something is cut off.
 *   - **ellipsis**: only visible through `GetTextLayoutResult` → `isLineEllipsized`. The semantics
 *     `Text` carries the input string, so "Mas…" still reports the full text.
 *
 * `scripts/check-render-manifest.mjs` then asserts on this file: zero-size nodes, clipped text,
 * ellipsis on only-copy text, tap targets < 48 dp, unreachable CTAs, missing content descriptions.
 *
 * Written per config to `build/render-manifest/<screen>__<config>.json` — transient (regenerated
 * every run, read by the gate in the same CI job, uploaded as an artifact), never committed.
 */
@OptIn(ExperimentalTestApi::class)
fun writeManifestAcrossMatrix(screen: String, content: @Composable () -> Unit) {
    RenderMatrix.configs.forEach { cfg ->
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        runComposeUiTest {
            setContent {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, cfg.fontScale),
                ) {
                    VastuTheme { content() }
                }
            }
            val root = onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val density = root.layoutInfo.density.density
            val nodes = ArrayList<String>()
            walk(root, density, nodes)

            val json = buildString {
                append("{\n")
                append("  \"screen\": ${jsonStr(screen)},\n")
                append("  \"config\": ${jsonStr(cfg.name)},\n")
                append("  \"fontScale\": ${cfg.fontScale},\n")
                append("  \"rootWidthDp\": ${dp(root.size.width.toFloat(), density)},\n")
                append("  \"rootHeightDp\": ${dp(root.size.height.toFloat(), density)},\n")
                append("  \"nodes\": [\n")
                append(nodes.joinToString(",\n"))
                append("\n  ]\n}\n")
            }
            File("build/render-manifest/${screen}__${cfg.name}.json").apply {
                parentFile?.mkdirs()
                writeText(json)
            }
        }
    }
}

/** Depth-first walk. Emits a row only for nodes that carry meaning (tag / text / description /
 *  click / role) so the manifest stays about content, not layout scaffolding. */
private fun walk(node: SemanticsNode, density: Float, out: MutableList<String>) {
    val c = node.config
    val testTag = c.getOrNull(SemanticsProperties.TestTag)
    val role = c.getOrNull(SemanticsProperties.Role)?.toString()
    val text = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
    val desc = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
    val clickable = c.getOrNull(SemanticsActions.OnClick) != null

    if (testTag != null || text != null || desc != null || clickable || role != null) {
        val clipped = node.boundsInRoot                 // px, clipped to ancestors
        val unclippedW = node.size.width.toFloat()      // px, layout size (unclipped)
        val unclippedH = node.size.height.toFloat()

        var ellipsized = false
        c.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            val results = ArrayList<TextLayoutResult>()
            action(results)
            ellipsized = results.firstOrNull()?.let { r ->
                (0 until r.lineCount).any { r.isLineEllipsized(it) }
            } ?: false
        }

        out.add(
            "    {" +
                "\"testTag\": ${jsonStr(testTag)}, " +
                "\"role\": ${jsonStr(role)}, " +
                "\"text\": ${jsonStr(text)}, " +
                "\"contentDescription\": ${jsonStr(desc)}, " +
                "\"clickable\": $clickable, " +
                "\"xDp\": ${dp(clipped.left, density)}, " +
                "\"yDp\": ${dp(clipped.top, density)}, " +
                "\"wDp\": ${dp(clipped.width, density)}, " +
                "\"hDp\": ${dp(clipped.height, density)}, " +
                "\"unclippedWDp\": ${dp(unclippedW, density)}, " +
                "\"unclippedHDp\": ${dp(unclippedH, density)}, " +
                "\"ellipsized\": $ellipsized" +
                "}",
        )
    }
    node.children.forEach { walk(it, density, out) }
}

private fun dp(px: Float, density: Float): String {
    val v = ((px / density) * 10f).roundToInt() / 10.0
    return v.toString()
}

private fun jsonStr(s: String?): String =
    if (s == null) "null"
    else "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ") + "\""
