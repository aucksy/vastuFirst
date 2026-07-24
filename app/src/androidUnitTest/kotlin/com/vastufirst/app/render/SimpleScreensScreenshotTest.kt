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
 * ViewModel, so they render straight from their public composable. (The ViewModel-backed screens —
 * Welcome, Home, Settings, MarkNorth, Score, Report — get the same treatment once each has a
 * stateless `…Content` seam like the editor's; that is the next batch, and the pattern is proven.)
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
        captureAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onSample = {}) }
        writeManifestAcrossMatrix("addhome") { AddHomeScreen(onDrawGrid = {}, onSample = {}) }
    }
}
