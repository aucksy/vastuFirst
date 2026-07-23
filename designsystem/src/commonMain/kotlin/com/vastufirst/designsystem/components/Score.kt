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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.vastufirst.designsystem.theme.VastuTheme

/** Score → band colour (§6.4): ≥75 strong, ≥50 workable, below attention. */
@Composable
fun scoreBandColor(score: Int): Color = when {
    score >= 75 -> VastuTheme.colors.scoreStrong
    score >= 50 -> VastuTheme.colors.scoreWorkable
    else -> VastuTheme.colors.scoreAttention
}

/** The big band-coloured score number with its "/ 100" and progress bar (Score screen). */
@Composable
fun ScoreDisplay(score: Int, modifier: Modifier = Modifier) {
    val band = scoreBandColor(score)
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
            VText(text = "$score", style = VastuTheme.type.scoreDisplay, color = band)
            VText(
                text = "/ 100",
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
