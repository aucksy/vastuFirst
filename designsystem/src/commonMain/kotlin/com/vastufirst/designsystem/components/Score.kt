package com.vastufirst.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vastufirst.designsystem.theme.VastuTheme

/*
 * ⭐ THE SCORE IS SHOWN OUT OF TEN, AND COMPUTED OUT OF A HUNDRED.
 *
 * The engine keeps producing a whole 0–100 (the §15 worked example must still score exactly 31,
 * and three engine tests plus the rotation-invariance test assert it). Everything in this file is
 * PRESENTATION: the same number, written the way a person reads it. Bands, the progress bar and
 * every threshold in the app stay on 0–100 so a home lands in exactly the band it did before.
 */

/**
 * The mark this reader's own language writes between the whole part and the tenth — "." in English,
 * Hindi, Tamil, Telugu, Marathi and Bengali; "," across much of Europe; "٫" in Arabic.
 *
 * NEVER hard-code it at a call site. The app reads it ONCE from the phone's own locale data
 * (`deviceDecimalMark()`, which asks Android rather than consulting a table we would have to keep)
 * and provides it here. The default is the mark every language VastuFirst ships in uses, so a
 * screen drawn outside the app — the screenshot harness — still reads correctly.
 */
val LocalDecimalMark = staticCompositionLocalOf { '.' }

/**
 * The score as a person reads it: out of ten, ALWAYS one decimal — 47 → "4.7", 8 → "0.8",
 * 50 → "5.0", 100 → "10.0". A bare "5" next to a "4.7" reads as a different kind of number, so
 * consistency wins over brevity.
 *
 * Integer arithmetic on purpose: dividing by ten in floating point can hand a screen "4.699999",
 * and there is no rounding rule to get wrong when there is no rounding.
 */
fun scoreOutOfTen(score: Int, decimalMark: Char = '.'): String {
    val s = score.coerceIn(0, 100)
    return "${s / 10}$decimalMark${s % 10}"
}

/**
 * The score as a screen reader should SAY it — "Score 4.7 out of 10". One sentence, so TalkBack
 * reads a number and its scale together instead of announcing a digit and a stray slash.
 */
fun spokenScore(score: Int, decimalMark: Char = '.'): String =
    "Score ${scoreOutOfTen(score, decimalMark)} out of 10"

/** Score → band colour (§6.4): ≥75 strong, ≥50 workable, below attention. Thresholds stay on the
 *  engine's 0–100 — only the printed number changed, so every home keeps its band. */
@Composable
fun scoreBandColor(score: Int): Color = when {
    score >= 75 -> VastuTheme.colors.scoreStrong
    score >= 50 -> VastuTheme.colors.scoreWorkable
    else -> VastuTheme.colors.scoreAttention
}

/** The big band-coloured score number with its "/ 10" and progress bar (Score screen). */
@Composable
fun ScoreDisplay(score: Int, modifier: Modifier = Modifier) {
    val band = scoreBandColor(score)
    val mark = LocalDecimalMark.current
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            // The number says its own scale out loud — "Score 4.7 out of 10" — instead of leaving a
            // screen reader to announce a bare "4.7".
            //
            // ⚠ Deliberately NOT `mergeDescendants` on the Row. The layout gate walks the MERGED
            // semantics tree, so merging these two texts into one node would delete the clipping and
            // ellipsis checks on the widest thing on the screen — at exactly the moment its width
            // changed. A description on the leaf keeps the node, and its measurement, intact.
            VText(
                text = scoreOutOfTen(score, mark),
                style = VastuTheme.type.scoreDisplay,
                color = band,
                modifier = Modifier.semantics { contentDescription = spokenScore(score, mark) },
            )
            VText(
                text = "/ 10",
                style = VastuTheme.type.caption,
                color = VastuTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = VastuTheme.spacing.s3),
            )
        }
        Box(Modifier.height(VastuTheme.spacing.s3))
        ScoreBar(score = score)
    }
}

/** The thin progress bar under the score. */
@Composable
fun ScoreBar(score: Int, modifier: Modifier = Modifier) {
    val band = scoreBandColor(score)
    val fraction = (score.coerceIn(0, 100)) / 100f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VastuTheme.sizes.progressTrack)
            .clip(VastuTheme.shapes.full)
            .background(VastuTheme.colors.borderDefault),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(VastuTheme.shapes.full)
                .background(band),
        )
    }
}
