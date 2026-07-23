package com.vastufirst.app.di

import com.vastufirst.app.platform.createAndroidSqlDriver
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.data.PlanRepository
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

    // ViewModels.
    viewModel { HomeViewModel(repo = get()) }
    viewModel { NewPlanViewModel(engine = get(), repo = get()) }
}
