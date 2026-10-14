package com.vastufirst.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.NoBilling
import com.vastufirst.app.navigation.VastuNavHost
import com.vastufirst.app.platform.IosPlanPhotoStore
import com.vastufirst.app.platform.IosVastuHaptics
import com.vastufirst.app.platform.UserDefaultsPlanReadingConsent
import com.vastufirst.app.platform.UserDefaultsReaderChoice
import com.vastufirst.app.platform.iosDecimalMark
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.scan.IosImageDecoder
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ReaderChoice
import com.vastufirst.app.ui.scan.ScanPictureSlot
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.data.PlanRepository
import com.vastufirst.data.VastuDatabaseFactory
import com.vastufirst.data.db.VastuDatabase
import com.vastufirst.designsystem.components.LocalDecimalMark
import com.vastufirst.designsystem.foundation.LocalVastuHaptics
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.scan.GroqPlanReader
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.ScanReaderConfigLoader
import com.vastufirst.app.update.IosRulesStore
import com.vastufirst.app.update.PublishedRules
import com.vastufirst.app.update.RulesStore
import com.vastufirst.app.update.RulesUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import kotlinx.coroutines.IO
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * ⭐ THE iPHONE APP'S FRONT DOOR (iPhone plan, stage 3). The Swift app (iosApp/) shows this one view
 * controller, and everything inside it is the shared app — the same screens, navigation and engine
 * Android runs. What differs is only the wiring below: where homes are stored, how settings are kept,
 * and the pieces not built for the iPhone yet (Apple's checkout), each of which
 * SAYS so or keeps the safe default rather than pretending.
 */
@Suppress("FunctionName", "unused") // called from Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
    com.vastufirst.app.platform.IosCrashLog.install(appVersion)
    startKoinOnce()
    return ComposeUIViewController {
        CompositionLocalProvider(
            LocalVastuHaptics provides IosVastuHaptics,
            LocalDecimalMark provides iosDecimalMark(),
        ) {
            VastuTheme {
                VastuNavHost(onFirstScreenDecided = {})
            }
        }
    }
}

private var koinStarted = false

private fun startKoinOnce() {
    if (koinStarted) return
    koinStarted = true
    val koin = startKoin { modules(iosModule) }.koin
    // Ask the Control Room for newer rules, off the main thread and never waited for — exactly as
    // Android does at start-up. What it produces is a file; the next launch reads it.
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching { koin.get<RulesUpdater>().refresh() }
    }
}

/** The version Apple shows for this build (CFBundleShortVersionString), as the Control Room compares it. */
private val appVersion: String
    get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.0.0"

/**
 * The iPhone's half of the wiring — Android's is di/AppModule.kt. The rules are the newest set the
 * Control Room published and this phone verified, or the ones built into the app.
 */
val iosModule = module {
    single<RulesStore> { IosRulesStore }
    single { PublishedRules.atStartup(store = get(), appVersion = appVersion) }
    single { VastuEngine(get<PublishedRules>().ruleSet) }
    single { RulesUpdater(store = get(), appVersion = appVersion) }
    single { NativeSqliteDriver(VastuDatabase.Schema, "vastufirst.db") }
    single { VastuDatabaseFactory.create(get<NativeSqliteDriver>()) }
    single { PlanRepository(db = get(), io = Dispatchers.IO, photos = IosPlanPhotoStore) }
    single { ScanReaderConfigLoader.load() }
    // ⭐ The iPhone reads plans ONLY through our relay (server/reader): it never carries the AI key,
    // whatever reader-config says for Android. Same reader code as Android (GroqPlanReader, shared).
    single<PlanReader> {
        val recipe = get<com.vastufirst.shared.scan.PlanReadRecipe>()
        GroqPlanReader(apiKey = "", recipe = recipe.copy(config = recipe.config.copy(useRelay = true)))
    }
    single<ImageDecoder> { IosImageDecoder }
    single<PlanReadingConsent> { UserDefaultsPlanReadingConsent }
    single<ReaderChoice> { UserDefaultsReaderChoice }
    single { ScanPictureSlot() }
    // Apple's checkout is stage 6. With payments off the screen says no payment is taken, as on Android.
    single<Billing> { NoBilling(publishedPrice = get<PublishedRules>().plans?.unlockPrice) }

    viewModel { HomeViewModel(repo = get(), engine = get()) }
    viewModel { NewPlanViewModel(engine = get(), repo = get(), handle = get()) }
    viewModel {
        val recipe = get<com.vastufirst.shared.scan.PlanReadRecipe>()
        ScanViewModel(
            reader = get(),
            decode = get(),
            canRead = true,
            modelChoices = listOfNotNull(recipe.config.model.ifBlank { null }, recipe.config.escalationModel),
            readerChoice = get(),
        )
    }
}

