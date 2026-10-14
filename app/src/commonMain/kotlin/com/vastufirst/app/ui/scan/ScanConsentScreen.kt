package com.vastufirst.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vastufirst.app.ui.common.screenRoot
import com.vastufirst.designsystem.components.SectionLabel
import com.vastufirst.designsystem.components.VText
import com.vastufirst.designsystem.components.VastuButton
import com.vastufirst.designsystem.components.VastuButtonStyle
import com.vastufirst.designsystem.components.VastuCard
import com.vastufirst.designsystem.components.VastuFoldSection
import com.vastufirst.designsystem.theme.VastuTheme

/**
 * The gate in front of the scan screen (§6.3, NFR §10, India's DPDP Act).
 *
 * ⭐ It sits BEFORE the scanner, not after it and not inside it, because that ordering is the only
 * version that means anything: a consent screen the user meets after their plan has been uploaded is
 * decoration. Nothing in this app can reach the network until this screen has been answered.
 *
 * The copy is deliberately concrete — what leaves the phone, who receives it, what for, and what we
 * do not do — because a page of legal prose is how consent gets clicked through unread. Every line is
 * something we can stand behind: we send the one picture the user chose, we ask it to read what is
 * printed (sometimes a second model gives a second opinion — said on the card), we store nothing,
 * and the alternative next to it is judged by the same rules without sending anything.
 *
 * ⚠ It describes OUR behaviour. It makes no promises on the reading service's behalf.
 * ⚠ The "Who reads it" fact must match `reader-config.json`. When the reader moved from Groq to
 * OpenRouter (4 Aug 2026) this card was the piece that lagged — and the consent key was bumped to
 * v2 so every earlier yes is asked again, per the key's own contract in [PlanReadingConsent].
 *
 * ⚠ 19 Sep 2026 — `readsPerScan` became 2, so the card now says the reader is asked twice. The
 * consent key was deliberately NOT bumped, and that is a judgement worth stating rather than
 * leaving implied: nothing NEW leaves the phone and no NEW party receives it. It is the same one
 * picture, to the same recipient, for the same purpose; only the number of requests changed. A
 * re-prompt would ask every existing user to agree again to a disclosure that has not materially
 * changed, which trains people to click through the card — the exact failure this screen exists to
 * avoid. If the owner reads it differently, bumping the key is a one-line change in
 * [PlanReadingConsent].
 *
 * ⭐ 30 Sep 2026 — THE FIVE FACTS ARE FOLDED (owner: *"Put its five facts inside one folded list, shut
 * by default, headed 'What happens to your plan (5)' ... Keep every fact word for word inside the
 * fold."*). The screen showed 172 words before scrolling and the facts were most of them. They sit,
 * unchanged, one tap under that heading; the headline, the button that agrees, the line about
 * Settings and the way to draw instead stay on the page. The consent key was NOT bumped, on his word:
 * nothing new is disclosed — the same facts, one tap further in. ScanConsentFoldTest holds all of it.
 */
@Composable
fun ScanConsentScreen(
    onAgree: () -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = VastuTheme.colors
    Column(
        modifier = Modifier
            .screenRoot(colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(VastuTheme.spacing.s6),
    ) {
        SectionLabel("Before we start")
        Spacer(Modifier.height(VastuTheme.spacing.s3))

        VText("Your plan leaves this phone", style = VastuTheme.type.h2, color = colors.textPrimary)
        Spacer(Modifier.height(VastuTheme.spacing.s2))
        VText(
            // Copy cut (4 Aug 2026): consent framing kept; the fact pairs below are untouched.
            "Everything else happens on your phone. Reading a plan does not — so here is " +
                "exactly what happens.",
            style = VastuTheme.type.body, color = colors.textSecondary,
        )

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        // ⭐ Shut by default; one tap opens it. The count on the heading is counted, never typed.
        VastuFoldSection(label = "What happens to your plan", count = CONSENT_FACTS.size) {
            VastuCard {
                CONSENT_FACTS.forEach { (title, body) -> Fact(title, body) }
            }
        }

        Spacer(Modifier.height(VastuTheme.spacing.s4))
        VText(
            "You can turn this off again at any time in Settings.",
            style = VastuTheme.type.bodySm, color = colors.textTertiary,
        )

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton("I agree — read my plan", onClick = onAgree)

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        Column(
            Modifier
                .clip(VastuTheme.shapes.md)
                .background(colors.surface)
                .padding(VastuTheme.spacing.s4),
        ) {
            VText("Rather not?", style = VastuTheme.type.bodyLg, color = colors.textPrimary)
            Spacer(Modifier.height(VastuTheme.spacing.s2))
            VText(
                "Draw your home on the grid instead. It takes a few minutes, nothing leaves your " +
                    "phone, and it's scored by exactly the same rules.",
                style = VastuTheme.type.bodySm, color = colors.textSecondary,
            )
            Spacer(Modifier.height(VastuTheme.spacing.s3))
            VastuButton(
                "Draw it on a grid instead",
                onClick = onDrawInstead,
                style = VastuButtonStyle.SECONDARY,
                large = false,
            )
        }

        Spacer(Modifier.height(VastuTheme.spacing.s6))
        VastuButton("Back", onClick = onBack, style = VastuButtonStyle.SECONDARY, large = false)
    }
}

/**
 * The five facts a person agrees to, each a title and its sentence — word for word what the card has
 * said since 19 Sep 2026. The "Who reads it" fact must match `reader-config.json` (see above).
 */
private val CONSENT_FACTS = listOf(
    "What we send" to "The one picture or PDF you choose. Nothing else — no name, no phone number, no location.",
    "Who reads it" to
        "A relay called OpenRouter passes it to an AI model from OpenAI, which we ask " +
        "twice and keep the fuller reading — and sometimes a second model from Google " +
        "for a second opinion. Their computers are abroad.",
    "What we ask it" to
        "Only to read what is printed on your plan — the room names and sizes, and where " +
        "each room sits. It is never asked anything about Vastu.",
    "What we keep" to "Nothing. Your plan is not stored by us, and it stays in your phone's own storage.",
    "Who works out your score" to "Your phone does, on its own, exactly as it does for a home you draw by hand.",
)

@Composable
private fun Fact(title: String, body: String) {
    VText(title, style = VastuTheme.type.bodyLg, color = VastuTheme.colors.textPrimary)
    Spacer(Modifier.height(VastuTheme.spacing.s1))
    VText(body, style = VastuTheme.type.bodySm, color = VastuTheme.colors.textSecondary)
    Spacer(Modifier.height(VastuTheme.spacing.s3))
}
