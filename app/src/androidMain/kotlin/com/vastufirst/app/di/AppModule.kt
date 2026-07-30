package com.vastufirst.app.di

import com.vastufirst.app.BuildConfig
import com.vastufirst.app.platform.createAndroidSqlDriver
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.scan.AndroidImageDecoder
import com.vastufirst.app.ui.scan.AndroidPlanReadingConsent
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.data.PlanRepository
import com.vastufirst.shared.scan.GroqPlanReader
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.data.VastuDatabaseFactory
import com.vastufirst.engine.VastuEngine
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * The app's dependency graph (Impl PRD §3.2 — DI in Koin so wiring is iOS-shareable).
 *
 * The engine loads its rule dataset ONCE at startup (fail-loud) and is a singleton; the DB
 * driver + repository are singletons; ViewModels are per-screen. Nothing here is Android-only
 * except the driver factory, which is the deliberate platform seam.
 */
val appModule = module {
    // Engine — loads + validates the versioned ruleset once (Product PRD §5.1).
    single { VastuEngine() }

    // Persistence — one driver, one database, one repository.
    single { createAndroidSqlDriver(androidContext()) }
    single { VastuDatabaseFactory.create(get()) }
    single { PlanRepository(db = get(), io = Dispatchers.IO) }

    // Scan — the REAL reader. One HTTP POST to the model named in `scan/reader-config.json`.
    //
    // ⚠ The stand-in reader that replayed recorded replies is deliberately NOT bound here any more.
    // It shipped in v0.3.14/15 and, because three of its four recorded readings were the same test
    // plan, every upload produced the same room list — which looks precisely like a broken feature.
    // Recorded replies still drive the tests and the render goldens, where they belong; the app now
    // either reads the user's plan or says it cannot.
    single<PlanReader> { GroqPlanReader(apiKey = BuildConfig.GROQ_API_KEY) }
    single<ImageDecoder> { AndroidImageDecoder(androidContext()) }
    single<PlanReadingConsent> { AndroidPlanReadingConsent(androidContext()) }

    // ViewModels.
    viewModel { HomeViewModel(repo = get()) }
    viewModel { NewPlanViewModel(engine = get(), repo = get()) }
    viewModel {
        ScanViewModel(
            reader = get(),
            decode = get(),
            // A build made without the key cannot read anything, and the screen says so rather than
            // offering a picker that leads nowhere.
            canRead = BuildConfig.GROQ_API_KEY.isNotBlank(),
        )
    }
}
