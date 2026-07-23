package com.vastufirst.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.data.SavedPlan
import com.vastufirst.designsystem.components.BrandMark
import com.vastufirst.designsystem.components.EmptyState
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuListRow
import com.vastufirst.designsystem.components.scoreBandColor
import com.vastufirst.designsystem.foundation.clickableTap
import com.vastufirst.designsystem.theme.VastuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Saved-plans home (§6 · design system screen 11). Add a home, or reopen a saved one (which
 * re-runs the engine from the stored plan). Two plans sit side by side so a BUYING user can
 * compare — the promised comparison, honestly (Impl PRD §8.4).
 */
@Composable
fun HomeScreen(
    onAddHome: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val colors = VastuTheme.colors
    val plans by viewModel.plans.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().background(colors.paper).padding(VastuTheme.spacing.s6),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                BrandMark(size = VastuTheme.sizes.tile)
                VText("Your plans", style = VastuTheme.type.h2, color = colors.textPrimary)
            }
            Box(
                Modifier.size(VastuTheme.sizes.control).clip(CircleShape).background(colors.surface)
                    .clickableTap(role = Role.Button, onClick = onSettings)
                    .semantics(mergeDescendants = true) { contentDescription = "Settings" },
                contentAlignment = Alignment.Center,
            ) { VText("⚙", style = VastuTheme.type.h3, color = colors.textSecondary) }
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        if (plans.isEmpty()) {
            EmptyState(
                title = "No plans yet",
                body = "Add your first floor plan to see its Vastu score.",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
                items(plans, key = { it.id }) { plan -> PlanRow(plan, onOpenPlan) }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VastuButton("Add a home", onClick = onAddHome)
    }
}

@Composable
private fun PlanRow(plan: SavedPlan, onOpen: (String) -> Unit) {
    val colors = VastuTheme.colors
    VastuListRow(
        title = plan.name,
        subtitle = "${plan.intent.name.lowercase().replaceFirstChar { it.uppercase() }} · updated recently",
        modifier = Modifier.clickableTap(onClick = { onOpen(plan.id) }),
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                VText("${plan.score}", style = VastuTheme.type.h2, color = scoreBandColor(plan.score))
                VText("/100", style = VastuTheme.type.caption, color = colors.textTertiary)
            }
        },
    )
}
