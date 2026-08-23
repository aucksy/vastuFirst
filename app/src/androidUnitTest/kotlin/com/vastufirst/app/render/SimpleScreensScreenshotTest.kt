package com.vastufirst.app.render

import android.app.Application
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.legal.PrivacyScreen
import com.vastufirst.app.billing.BillingMode
import com.vastufirst.app.billing.BillingState
import com.vastufirst.app.ui.unlock.UnlockContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The stateless, callback-only screens — rendered + measured across the §6.4 matrix. These take no
 * ViewModel, so they render straight from their public composable. (Welcome, Home and Settings are
 * driven through their new stateless `…Content` seams in ViewModelScreensScreenshotTest; MarkNorth,
 * Score and Report — which need an engine-computed Analysis fixture — follow in the next batch.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class SimpleScreensScreenshotTest {

    @Test
    fun legal() {
        captureAcrossMatrix("legal") { LegalScreen(onBack = {}) }
        writeManifestAcrossMatrix("legal") { LegalScreen(onBack = {}) }
    }

    @Test
    fun unlock() {
        // ⭐ PAYMENTS OFF — what ships. The button and the notice both have to say so in plain
        // words; a screen that LOOKS like it charges and does not is the one thing this feature must
        // never do, so the state that ships gets the golden.
        captureAcrossMatrix("unlock") { UnlockContent(state = BillingState()) }
        writeManifestAcrossMatrix("unlock") { UnlockContent(state = BillingState()) }
    }

    /** ⭐ Payments ON, store answered — the screen the owner will see the day it goes live. */
    @Test
    fun unlock_paid() {
        val paid = BillingState(mode = BillingMode.READY, price = "₹699.00")
        captureAcrossMatrix("unlock-paid") { UnlockContent(state = paid) }
        writeManifestAcrossMatrix("unlock-paid") { UnlockContent(state = paid) }
    }

    /** Payments ON but Google Play unreachable — must never quietly fall back to giving it away. */
    @Test
    fun unlock_unavailable() {
        val down = BillingState(mode = BillingMode.UNAVAILABLE)
        captureAcrossMatrix("unlock-unreachable") { UnlockContent(state = down) }
        writeManifestAcrossMatrix("unlock-unreachable") { UnlockContent(state = down) }
    }

    /** The privacy policy, in the app — required by Play and by the DPDP Act, and never rendered. */
    @Test
    fun privacy() {
        captureAcrossMatrix("privacy") { PrivacyScreen(onBack = {}) }
        writeManifestAcrossMatrix("privacy") { PrivacyScreen(onBack = {}) }
    }

    @Test
    fun addHome() {
        captureAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) }
        writeManifestAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) }
    }

    /**
     * ⭐⭐ THE HOUSE-OR-FLAT QUESTION WITH "A FLAT" CHOSEN (23 August 2026).
     *
     * ⚠ A SECOND GOLDEN, NOT A REPLACEMENT, and that is the whole point. `addhome` above photographs
     * the default state — which is "A house", because that is what every home in the product
     * silently was until this screen asked. The chosen state is a different picture: a filled card,
     * a heavier border and a coloured label, and none of it appears in the default one.
     *
     * ⚠ It matters most at the configurations nobody looks at. Two cards side by side each hold a
     * heading and a subtitle, and at 200 % font scale on a 320 dp screen that is where a two-word
     * label runs out of room. The matrix renders exactly those, which is why this is a golden and
     * not a unit test.
     */
    @Test
    fun addHomeFlatChosen() {
        val flat: @androidx.compose.runtime.Composable () -> Unit = {
            AddHomeScreen(
                onDrawGrid = {}, onScan = {}, onSample = {},
                propertyType = com.vastufirst.shared.PropertyType.FLAT,
            )
        }
        captureAcrossMatrix("addhome-flat", flat)
        writeManifestAcrossMatrix("addhome-flat", flat)
    }
}
