package com.vastufirst.app.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.vastufirst.designsystem.theme.VastuTheme
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * ⭐⭐ WHAT EACH JOURNEY COSTS A FIRST-TIME READER — screens, taps, words, choices (owner, 30 Sep 2026:
 * *"decreasing the number of screens and text to read.. its just too much"*).
 *
 * Every screen already renders headlessly from fixtures; this puts them in the ORDER a person meets
 * them and counts, on the baseline phone (412 × 915):
 *
 *  · **words** — the words on the FIRST screenful, the way a reader meets them. A line counts when at
 *    least half of it is on screen. Text folded shut or behind an **i** is not in the semantics tree
 *    until it is opened, so it is not counted — which is exactly the point of folding it.
 *  · **choices** — every tappable control on the whole screen that is switched on (a greyed-out button
 *    is not a choice yet). An **i** is counted apart, as a **note**: it explains, it does not decide.
 *  · **taps** — the fewest taps that move a reader on toward the report, written beside each screen
 *    so the number can be argued with. Setting North counts as one; picking the file in the phone's
 *    own picker counts as one; dragging a room onto the grid counts as one per room.
 *
 * The numbers are printed into the test's output as `JOURNEY|…` lines, which CI lifts into its log
 * (the "What each journey costs" step), and each journey is photographed as one strip of pictures
 * under `app/build/journeys/`, which CI uploads with the screenshots.
 *
 * ⚠ The pictures are written with the RECORD task forced on, whatever mode the run is in. They are
 * not goldens and must never be compared: a strip is for eyes, and a verify run with no golden to
 * compare against would otherwise either skip them or fail on them.
 */
internal class JourneyStep(
    /** The screen's name as a reader would say it. */
    val screen: String,
    /** The fewest taps from here toward the report. 0 on the report itself and on a wait. */
    val taps: Int,
    /** What those taps are, in words, so the count can be checked by a person. */
    val how: String,
    val content: @Composable () -> Unit,
)

internal class Journey(val name: String, val steps: List<JourneyStep>)

/** One screen's count. */
internal data class ScreenCount(val words: Int, val choices: Int, val notes: Int)

@OptIn(ExperimentalTestApi::class)
internal fun countScreen(content: @Composable () -> Unit): ScreenCount {
    RuntimeEnvironment.setQualifiers(RenderMatrix.BASE)
    var result: ScreenCount? = null
    runComposeUiTest {
        setContent { VastuTheme { content() } }
        val root = onRoot(useUnmergedTree = false).fetchSemanticsNode()
        result = countTree(root)
    }
    return checkNotNull(result) { "The screen did not render, so nothing could be counted." }
}

private fun countTree(root: SemanticsNode): ScreenCount {
    val windowHeight = root.size.height.toFloat()
    var words = 0
    var choices = 0
    var notes = 0
    fun walk(n: SemanticsNode) {
        val c = n.config
        c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }?.let { text ->
            val shown = n.boundsInRoot
            val whole = n.size.height.toFloat()
            val onFirstScreen = shown.width > 0f && shown.height > 0f && shown.top < windowHeight &&
                shown.height >= 0.5f * whole
            if (onFirstScreen) words += wordsIn(text)
        }
        val click = c.getOrNull(SemanticsActions.OnClick)
        if (click != null && !c.contains(SemanticsProperties.Disabled)) {
            val label = click.label.orEmpty()
            if (label.startsWith("explain") || label.startsWith("hide")) notes++ else choices++
        }
        n.children.forEach { walk(it) }
    }
    walk(root)
    return ScreenCount(words, choices, notes)
}

/** A word is anything with a letter or a digit in it; a lone "·", "‹" or "✓" is not. */
internal fun wordsIn(text: String): Int =
    text.split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

/** Where a journey's pictures are written — absolute, for the reason [goldenPath] gives. */
private fun journeyDir(journey: String): File =
    File("build/journeys/${slug(journey)}").absoluteFile

internal fun slug(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

@OptIn(ExperimentalRoborazziApi::class)
private fun photograph(file: File, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(RenderMatrix.BASE)
    file.parentFile?.mkdirs()
    captureRoboImage(
        filePath = file.absolutePath,
        roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record),
    ) { VastuTheme { content() } }
}

/**
 * The strip: every screen of one journey side by side, at half size, in the order a reader meets
 * them. Plain JVM imaging — no Android decoder — for the reason [PlanSheet] gives.
 */
private fun stitch(pictures: List<File>, out: File) {
    System.setProperty("java.awt.headless", "true")
    val images = pictures.mapNotNull { f -> if (f.exists()) javax.imageio.ImageIO.read(f) else null }
    if (images.isEmpty()) return
    val gap = 16
    val scaled = images.map { it.width / 2 to it.height / 2 }
    val width = scaled.sumOf { it.first } + gap * (images.size - 1)
    val height = scaled.maxOf { it.second }
    val strip = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val g = strip.createGraphics()
    g.setRenderingHint(
        java.awt.RenderingHints.KEY_INTERPOLATION,
        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
    )
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, width, height)
    var x = 0
    images.forEachIndexed { i, img ->
        val (w, h) = scaled[i]
        g.drawImage(img, x, 0, w, h, null)
        x += w + gap
    }
    g.dispose()
    javax.imageio.ImageIO.write(strip, "png", out)
}

/**
 * Count and photograph every journey, print one line per screen and one total per journey.
 *
 * A screen that appears in several journeys is counted and photographed ONCE, by its name — the
 * report measured in the scan journey is the same report the side journeys start from.
 */
internal fun measureJourneys(label: String, journeys: List<Journey>) {
    val counted = HashMap<String, ScreenCount>()
    val pictured = HashMap<String, File>()
    val lines = ArrayList<String>()
    val totals = ArrayList<String>()
    journeys.forEach { journey ->
        var words = 0
        var choices = 0
        var notes = 0
        var taps = 0
        val strip = ArrayList<File>()
        journey.steps.forEachIndexed { i, step ->
            val count = counted.getOrPut(step.screen) { countScreen(step.content) }
            val picture = pictured.getOrPut(step.screen) {
                val f = File(journeyDir("screens"), "${slug(step.screen)}.png")
                runCatching { photograph(f, step.content) }
                    .onFailure { println("JOURNEY-WARN|could not photograph ${step.screen}: ${it.message}") }
                f
            }
            strip += picture
            words += count.words
            choices += count.choices
            notes += count.notes
            taps += step.taps
            lines += "JOURNEY|$label|${journey.name}|${i + 1}|${step.screen}|" +
                "words ${count.words}|choices ${count.choices}|notes ${count.notes}|" +
                "taps ${step.taps}${if (step.how.isNotBlank()) " (${step.how})" else ""}"
        }
        totals += "JOURNEY|$label|${journey.name}|TOTAL|screens ${journey.steps.size}|taps $taps|" +
            "words $words|choices $choices|notes $notes"
        runCatching {
            val out = File(journeyDir(""), "$label-${slug(journey.name)}.png")
            out.parentFile?.mkdirs()
            stitch(strip, out)
        }.onFailure { println("JOURNEY-WARN|could not stitch ${journey.name}: ${it.message}") }
    }
    lines.forEach(::println)
    totals.forEach(::println)
}
