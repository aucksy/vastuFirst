package com.vastufirst.app.render

import android.app.Application
import com.vastufirst.app.ui.addhome.AddHomeScreen
import com.vastufirst.app.ui.legal.LegalScreen
import com.vastufirst.app.ui.unlock.UnlockScreen
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
        captureAcrossMatrix("unlock") { UnlockScreen(onUnlocked = {}) }
        writeManifestAcrossMatrix("unlock") { UnlockScreen(onUnlocked = {}) }
    }

    @Test
    fun addHome() {
        captureAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) }
        writeManifestAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onScan = {}, onSample = {}) }
    }
}
