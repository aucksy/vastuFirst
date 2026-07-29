package com.vastufirst.app.di

import com.vastufirst.app.platform.createAndroidSqlDriver
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.scan.AndroidImageDecoder
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.data.PlanRepository
import com.vastufirst.shared.scan.FakePlanReader
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

    // Scan — the reader is behind an interface so the transport swaps without touching a line of the
    // pure layer. ⚠ Today it is the FAKE reader, which replays the three replies the real Groq API
    // returned on 2026-07-29: the whole flow is tappable and reviewable before a paid call is made,
    // and before the key question (§6.2 — key in the APK vs a proxy) has to be answered. Swapping in
    // GroqPlanReader is a one-line change here.
    single<PlanReader> { FakePlanReader() }
    single<ImageDecoder> { AndroidImageDecoder(androidContext()) }

    // ViewModels.
    viewModel { HomeViewModel(repo = get()) }
    viewModel { NewPlanViewModel(engine = get(), repo = get()) }
    viewModel { ScanViewModel(reader = get(), decode = get()) }
}
