package com.vastufirst.app.ui.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
                // ⚠ THE BUSY FLAG IS CLEARED IN A `finally`, NOT AFTER THE CALL. Rotating the phone
                // or the OS reclaiming the app while Google's payment sheet is open CANCELS this
                // coroutine, so the line that cleared it never ran — and the buyer came back to a
                // screen whose pay button and "I already paid" button were both greyed out, for the
                // rest of the session, with no way to finish and no way to restore. Nothing can
                // strand them now, whatever kills the wait. (Payments are off today; this must not
                // be discovered on the day they are switched on.)
                try {
                    val result = billing.purchase()
                    when (result) {
                        is PurchaseResult.Purchased, is PurchaseResult.AlreadyOwned -> onUnlocked()
                        // Backing out is not an error and must not be reported as one.
                        is PurchaseResult.Cancelled -> Unit
                        is PurchaseResult.Failed -> problem = result.message
                    }
                } finally {
                    state = billing.state.copy(busy = false)
                }
            }
        },
        onRestore = {
            scope.launch {
                problem = null
                // ⚠ Restore takes the same busy latch the pay button has, so a double-tap cannot
                // fire two restores — the one control on this screen that had no guard at all.
                if (state.busy) return@launch
                state = state.copy(busy = true)
                try {
                    val result = billing.restore()
                    when (result) {
                        is PurchaseResult.AlreadyOwned -> onUnlocked()
                        is PurchaseResult.Failed -> problem = result.message
                        else -> problem = "We couldn't find a purchase on this Google account."
                    }
                } finally {
                    state = billing.state.copy(busy = false)
                }
            }
        },
    )
}

/** Unlock as a pure function of its state, so every billing mode can be rendered and looked at. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockContent(
    state: BillingState,
    problem: String? = null,
    onAction: () -> Unit = {},
    onRestore: () -> Unit = {},
) {
    val colors = VastuTheme.colors
    Column(
        // verticalScroll so everything below the fold is reachable at font scale 2.0
        // (UI-POLISH §3.B, measured by the render harness).
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        VText("Unlock the full report", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s4))
        // ⭐ FlowRow, not Row (audit C2). In a Row the huge price figure took nearly the whole
        // width and the caption wrapped ONE CHARACTER PER LINE beside it — "hom / e, / fore / ver"
        // next to ₹699.00 at the exact moment of purchase. In a FlowRow the caption sits beside
        // the price where it fits, and drops below it as a whole readable phrase where it doesn't.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            // ⭐ The STORE'S OWN price string when there is one. It carries the customer's currency
            // and any local tax, and it is the number Google will actually charge — a hard-coded
            // figure would be a promise the app is not the one keeping.
            VText(state.price ?: FALLBACK_PRICE, style = VastuTheme.type.scoreDisplay, color = colors.textPrimary)
            VText(
                // ⭐ WITH PAYMENTS OFF, THE BIGGEST THING ON THE MONEY SCREEN IS A NUMBER NOBODY IS
                // CHARGED. The small grey notice under the button has always said so, but a reader
                // takes the page in the other way round — the price is in the largest type on it,
                // and "one-time · this home, forever" beside it reads as a live offer. The caption
                // now carries the same truth as the notice, at the one spot the eye lands first.
                if (state.mode == BillingMode.DISABLED) "the planned price — not charged in this version"
                else "one-time · this home, forever",
                style = VastuTheme.type.body, color = colors.textTertiary,
                modifier = Modifier.align(Alignment.Bottom).padding(bottom = VastuTheme.spacing.s3),
            )
        }
        Spacer(Modifier.height(VastuTheme.spacing.s6))

        // ⭐ The button and its honesty notice come BEFORE the feature list (audit C3). At 200 %
        // font the list alone outgrew the screen, so the one control the screen exists for — and
        // the "no subscription / nothing has been charged" line under it — had to be hunted for
        // below the fold. Price, button, and what it costs (or doesn't) now always share the
        // first screenful; the sell follows for whoever wants it.
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

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        SectionLabel("What you get")
        Spacer(Modifier.height(VastuTheme.spacing.s3))
        Column(verticalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            // ⚠ FOUR LINES, AND THE COUNT IS THE POINT. Seven measured taller than the screen at
            // 320 dp (and four still are at 200 % font, which is why the button now sits above the
            // list rather than under it). Each line is still a real section of the report ("Layout
            // change and remedy for each" used to be true and still hid the problem: the remedies
            // were the same two lines on almost every problem).
            // ⭐⭐ TWO OF THESE FOUR WERE SELLING SOMETHING THE FREE REPORT ALREADY GIVES AWAY, which
            // is the one thing a payment screen must never do (Product PRD §6.4 — "no bait").
            //
            // · "Your front door by name" — the front door is FREE, deliberately and permanently
            //   (FreeTier.DOOR_IS_FREE). Before paying a reader already sees its wall, its position
            //   out of the 32, its Sanskrit name and quarter and the whole explanation. It was the
            //   fourth reason given to spend ₹699 and it was already on the screen behind this one.
            // · "The rooms rated not ideal, which the free score only counts" — the free report does
            //   not merely count them. It names every one, with its direction and its verdict. What
            //   is actually behind the wall is the REASON, which is a different and honest promise.
            //
            // Each line below is now something a reader genuinely cannot see until they pay.
            listOf(
                "The whole reason behind every problem — not only the entrance, kitchen and toilets, which are free",
                "Remedies for that problem in that direction — and where none exists, we say so",
                "Why each of your other rooms reads the way it does, not just its verdict",
                "The classical source behind every rule we apply to your home",
            ).forEach { Feature(it) }
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
