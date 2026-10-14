package com.vastufirst.app.di

import android.app.Application
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ReaderChoice
import com.vastufirst.app.update.PublishedRules
import com.vastufirst.app.update.RulesStore
import com.vastufirst.app.update.RulesUpdater
import com.vastufirst.data.PlanRepository
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.scan.PlanReader
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull

/**
 * ⭐ THE APP'S WIRING RESOLVES (14 Oct 2026). Moving screens and the Control Room store into shared code
 * changed the TYPES Koin is asked for (a RulesStore, not a PublishedRulesStore). A wrong type there
 * compiles, passes every screen test, and crashes the real app on its first launch — no golden ever
 * starts Koin. This test starts the real module and asks for every piece the first screens need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppModuleTest {
    @After fun stop() = stopKoin()

    @Test
    fun every_piece_the_app_starts_with_can_be_built() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModule)
        }.koin
        assertNotNull(koin.get<RulesStore>())
        assertNotNull(koin.get<PublishedRules>())
        assertNotNull(koin.get<RulesUpdater>())
        assertNotNull(koin.get<VastuEngine>())
        assertNotNull(koin.get<PlanRepository>())
        assertNotNull(koin.get<PlanReader>())
        assertNotNull(koin.get<ImageDecoder>())
        assertNotNull(koin.get<PlanReadingConsent>())
        assertNotNull(koin.get<ReaderChoice>())
        assertNotNull(koin.get<Billing>())
    }
}
