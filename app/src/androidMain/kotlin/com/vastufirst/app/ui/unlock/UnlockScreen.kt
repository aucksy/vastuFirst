package com.vastufirst.app.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.billing.FALLBACK_PRICE
import com.vastufirst.app.billing.PurchaseResult
import com.vastufirst.app.billing.billingActionLabel
import com.vastufirst.app.billing.billingNotice
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.theme.VastuTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Unlock (§6.5 · design system screen 8) — no dark patterns; the price is shown before you pay.
 *
 * ⚠⚠ THE RULE THIS SCREEN EXISTS UNDER: **it must never look like it takes payment when it does
 * not.** Payments are built in full and shipped switched off, so every word here — the button, the
 * notice under it — comes from `billingNotice()`/`billingActionLabel()`, which read the live billing
 * mode. With payments off the button says "Unlock on this device — free" and the notice says no
 * payment is taken. There is no state in which this screen implies a charge that will not happen.
 */
@Composable
fun UnlockScreen(onUnlocked: () -> Unit, billing: Billing = koinInject()) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(billing.state) }
    var problem by remember { mutableStateOf<String?>(null) }

    UnlockContent(
        state = state,
        problem = problem,
        onAction = {
            scope.launch {
                problem = null
                state = state.copy(busy = true)
                val result = billing.purchase()
                state = billing.state.copy(busy = false)
                when (result) {
                    is PurchaseResult.Purchased, is PurchaseResult.AlreadyOwned -> onUnlocked()
                    // Backing out is not an error and must not be reported as one.
                    is PurchaseResult.Cancelled -> Unit
                    is PurchaseResult.Failed -> problem = result.message
                }
            }
        },
        onRestore = {
            scope.launch {
                problem = null
                val result = billing.restore()
                state = billing.state
                when (result) {
                    is PurchaseResult.AlreadyOwned -> onUnlocked()
                    is PurchaseResult.Failed -> problem = result.message
                    else -> problem = "We couldn't find a purchase on this Google account."
                }
            }
        },
    )
}

/** Unlock as a pure function of its state, so every billing mode can be rendered and looked at. */
@Composable
fun UnlockContent(
    state: BillingState,
    problem: String? = null,
    onAction: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    Column(
        // verticalScroll so the notice at the bottom is reachable at font scale 2.0 — without it
        // the "What you get" list pushes it off the bottom and it clips (UI-POLISH §3.B, measured by
        // the render harness).
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        VText("Unlock the full report", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            // ⭐ The STORE'S OWN price string when there is one. It carries the customer's currency
            // and any local tax, and it is the number Google will actually charge — a hard-coded
            // figure would be a promise the app is not the one keeping.
            VText(state.price ?: FALLBACK_PRICE, style = VastuTheme.type.scoreDisplay, color = colors.textPrimary)
            VText(
                "one-time · this home, forever",
                style = VastuTheme.type.body, color = colors.textTertiary,
                modifier = Modifier.padding(bottom = VastuTheme.spacing.s3),
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        SectionLabel("What you get")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            listOf(
                "Every issue ranked, not just the top three",
                "Layout change and remedy for each",
                "Source & provenance on every rule",
                "Where the schools disagree, shown honestly",
            ).forEach { Feature(it) }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s8))
        VastuButton(
            billingActionLabel(state),
            onClick = onAction,
            enabled = !state.busy,
            modifier = Modifier.testTag("unlock.action"),
        )
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        VText(billingNotice(state), style = VastuTheme.type.caption, color = colors.textTertiary)

        if (problem != null) {
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VText(problem, style = VastuTheme.type.bodySm, color = colors.error)
        }

        // Restoring is only meaningful once there is a store to restore FROM. Offering it when
        // payments are off would be a button that can never do anything.
        if (state.mode != BillingMode.DISABLED) {
            Spacer(Modifier.height(VastuTheme.spacing.s4))
            VastuButton(
                "I already paid — restore it",
                onClick = onRestore,
                style = VastuButtonStyle.SECONDARY,
                large = false,
                enabled = !state.busy,
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@Composable
private fun Feature(text: String) {
    val colors = VastuTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s2)) {
        VText("✓", style = VastuTheme.type.body, color = colors.verdictIdeal)
        VText(text, style = VastuTheme.type.body, color = colors.textPrimary)
    }
}
