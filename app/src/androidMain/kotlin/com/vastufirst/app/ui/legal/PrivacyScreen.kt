package com.vastufirst.app.ui.legal

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.designsystem.components.IconTapButton
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The privacy policy, IN THE APP.
 *
 * Google Play requires a policy at a public URL for any app that handles personal data, and India's
 * DPDP Act requires a notice the person can actually read. A link is the minimum; a link is also the
 * thing that is offline, out of date, or a 404 exactly when somebody goes looking. So the policy
 * lives in the app, works with no connection, and is the same text that gets published at the URL —
 * docs/PRIVACY-POLICY.md is generated from these words, not written separately.
 *
 * ⚠ It is short because the truth is short. VastuFirst has no account, no analytics, no advertising
 * and no third-party SDK that phones home. There is exactly one thing that leaves the device and the
 * user is asked before it does. A long policy here would mean either padding or something to hide.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier.screenRoot(colors.paper).verticalScroll(rememberScrollState()).padding(VastuTheme.spacing.s6),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VastuTheme.spacing.s3)) {
            IconTapButton("‹", contentDescription = "Back", onClick = onBack)
            VText("Privacy", style = VastuTheme.type.h2, color = colors.textPrimary)
        }
        Spacer(Modifier.height(VastuTheme.spacing.s4))

        VastuCard(accent = colors.verdictIdeal) {
            VText(
                "Your homes stay on your phone — except a plan picture you ask us to read.",
                style = VastuTheme.type.bodyLg, color = colors.textPrimary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                "Everything you draw, every score and every report is worked out on this device and " +
                    "saved only on this device. There is no account, no sign-up and no phone number.",
                style = VastuTheme.type.body, color = colors.textSecondary,
            )
        }

        Section("What we collect", PRIVACY_COLLECT)
        Section("The one thing that leaves your phone", PRIVACY_UPLOAD)
        Section("When the app crashes", PRIVACY_CRASH)
        Section("If you buy the full report", PRIVACY_PAYMENT)
        Section("Deleting everything", PRIVACY_DELETE)
        Section("Children", PRIVACY_CHILDREN)
        Section("Contact", PRIVACY_CONTACT)

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VText(
            "Last updated $PRIVACY_UPDATED.",
            style = VastuTheme.type.caption, color = colors.textTertiary,
        )
        Spacer(Modifier.height(VastuTheme.spacing.s4))
    }
}

@Composable
private fun Section(title: String, body: String) {
    Spacer(Modifier.height(VastuTheme.spacing.s6))
    SectionLabel(title)
    Spacer(Modifier.height(VastuTheme.spacing.s2))
    VText(body, style = VastuTheme.type.body, color = VastuTheme.colors.textSecondary, modifier = Modifier.fillMaxWidth())
}

// The policy text, kept as constants so docs/PRIVACY-POLICY.md and this screen can never say
// different things — the published page is generated from exactly these words.

const val PRIVACY_UPDATED = "15 August 2026"

const val PRIVACY_COLLECT =
    "Nothing about you. We do not ask for your name, email, phone number or location, and the app " +
        "has no account to sign in to. The homes you draw, their scores and their reports are stored " +
        "only in the app's own private storage on your phone, where no other app can read them."

const val PRIVACY_UPLOAD =
    "If you choose to have a floor plan read for you, that one picture is sent to an AI service to " +
        "be turned into rooms, and the rooms come back. The app asks you before it does this, every " +
        "first time, and you can turn it off again in Settings. The picture is not stored by us and " +
        "is not linked to you — there is nothing to link it to. Drawing your home by hand needs no " +
        "internet at all and is scored by exactly the same rules."

const val PRIVACY_CRASH =
    "If the app closes unexpectedly, it writes what went wrong into its own private storage and " +
        "offers to email it to us the next time you open it. Nothing is sent unless you press send, " +
        "and you can read the whole message first. It contains only technical detail, your phone " +
        "model and the app version — never your homes, your scores or anything about you."

const val PRIVACY_PAYMENT =
    "Payment is handled entirely by Google Play. We never see or store your card, and the app does " +
        "not receive your payment details at any point. Google tells us only that this device has " +
        "bought the report."

const val PRIVACY_DELETE =
    "Settings → Delete all my data removes every saved home from this device immediately and " +
        "permanently. Uninstalling the app removes everything too. Because nothing is stored on our " +
        "side, there is nothing left for us to delete and nothing you need to ask us for."

const val PRIVACY_CHILDREN =
    "VastuFirst is not aimed at children and collects nothing from anyone, of any age."

const val PRIVACY_CONTACT =
    "Questions about privacy, or anything else: contact@vastufirst.com."
