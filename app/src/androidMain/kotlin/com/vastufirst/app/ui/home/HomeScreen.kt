package com.vastufirst.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vastufirst.data.SavedPlan
import com.vastufirst.designsystem.theme.VastuTheme
import org.koin.androidx.compose.koinViewModel

/**
 * Saved-plans start screen (Product PRD §6 · design system screen 11). Block A-1 wires the
 * real data path — Koin → ViewModel → DB flow → tokens; the polished list, empty-state art,
 * and side-by-side comparison land in Block D.
 */
@Composable
fun HomeScreen(
    onAddHome: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VastuTheme.colors.paper)
            .padding(VastuTheme.spacing.s6),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "Your plans",
            style = VastuTheme.type.h1.copy(color = VastuTheme.colors.textPrimary),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        if (plans.isEmpty()) {
            EmptyPlans(modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3),
            ) {
                items(plans, key = { it.id }) { PlanRow(it) }
            }
        }

        PrimaryButton(text = "Add your home", onClick = onAddHome)
    }
}

@Composable
private fun EmptyPlans(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "No plans yet",
            style = VastuTheme.type.h3.copy(color = VastuTheme.colors.textPrimary, textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        androidx.compose.foundation.text.BasicText(
            text = "Add your first floor plan to see its Vastu score.",
            style = VastuTheme.type.body.copy(color = VastuTheme.colors.textSecondary, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun PlanRow(plan: SavedPlan) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VastuTheme.shapes.md)
            .background(VastuTheme.colors.surfaceRaised)
            .padding(VastuTheme.spacing.s4),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = plan.name,
            style = VastuTheme.type.h3.copy(color = VastuTheme.colors.textPrimary),
        )
        androidx.compose.foundation.text.BasicText(
            text = "Score ${plan.score} / 100",
            style = VastuTheme.type.bodySm.copy(color = VastuTheme.colors.textSecondary),
        )
    }
}

/** Temporary token-styled CTA for the A-1 smoke test; replaced by the real VastuButton in Block B. */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(VastuTheme.sizes.cta)
            .clip(VastuTheme.shapes.full)
            .background(VastuTheme.colors.primary)
            .clickable(onClick = onClick)
            .wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = VastuTheme.type.label.copy(color = VastuTheme.colors.onPrimary),
        )
    }
}
