package com.vastufirst.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.AnalysisNote
import com.vastufirst.shared.NoteLevel

/**
 * A quiet, non-alarming strip of the engine's plain-language notes (unusual shape, off-compass
 * tilt, long-and-narrow, …). These messages are already user-safe by construction — the engine
 * never emits jargon or "error/irregular" here — so a screen shows them verbatim. The tint only
 * differentiates gentle info from a caution; the message text always carries the meaning, so this
 * never conveys information by colour alone. Empty list → nothing rendered.
 */
@Composable
fun NotesStrip(notes: List<AnalysisNote>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        notes.forEach { note ->
            val accent = if (note.level == NoteLevel.WARNING) VastuTheme.colors.warning else VastuTheme.colors.info
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(VastuTheme.shapes.sm)
                    .background(accent.copy(alpha = 0.12f))
                    .padding(VastuTheme.spacing.s3),
            ) {
                VText(note.message, style = VastuTheme.type.bodySm, color = VastuTheme.colors.textPrimary)
            }
        }
    }
}
