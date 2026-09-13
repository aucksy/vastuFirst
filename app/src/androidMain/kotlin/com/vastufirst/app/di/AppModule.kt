package com.vastufirst.app.di

import com.vastufirst.app.BuildConfig
import com.vastufirst.app.CurrentActivity
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.NoBilling
import com.vastufirst.app.billing.PlayBilling
import com.vastufirst.app.platform.AndroidPlanPhotoStore
import com.vastufirst.app.platform.createAndroidSqlDriver
import com.vastufirst.app.ui.home.HomeViewModel
import com.vastufirst.app.ui.newplan.NewPlanViewModel
import com.vastufirst.app.ui.scan.AndroidImageDecoder
import com.vastufirst.app.ui.scan.AndroidPlanReadingConsent
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.AndroidReaderChoice
import com.vastufirst.app.ui.scan.PlanReadingConsent
import com.vastufirst.app.ui.scan.ReaderChoice
import com.vastufirst.app.ui.scan.ScanReviewHandover
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.data.PlanRepository
import com.vastufirst.shared.scan.GroqPlanReader
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.data.VastuDatabaseFactory
import com.vastufirst.app.update.PublishedRules
import com.vastufirst.app.update.PublishedRulesStore
import com.vastufirst.app.update.RulesUpdater
import com.vastufirst.engine.VastuEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    // ⭐ THE RULES AND PRICES THIS LAUNCH IS USING.
    //
    // Read ONCE, here, at start-up, and held for the life of the process. A set published by the
    // Control Room is used only if it is on disk, its signature still verifies against the key
    // compiled into this build, and the app's own loader still accepts it. Anything else at all —
    // no file, a file somebody edited, a rule set this build is too old for — and the rules built
    // into the app are used, exactly as before any of this existed.
    //
    // ⚠ Nothing downloaded takes effect in the session that downloads it. Swapping the rules under
    // a report somebody is reading would move their score while they looked at it, with nothing on
    // the screen to explain it. The download lands; the next launch uses it.
    single { PublishedRulesStore(androidContext()) }
    single { PublishedRules.atStartup(store = get(), appVersion = BuildConfig.VERSION_NAME) }

    // Engine — loads + validates the versioned ruleset once (Product PRD §5.1), from whichever
    // rule set the line above settled on.
    single { VastuEngine(get<PublishedRules>().ruleSet) }

    // Asking whether there is anything newer. Started below, off the main thread, and its answer
    // is a file for the next launch — never anything this session can be waiting on.
    single { RulesUpdater(store = get(), appVersion = BuildConfig.VERSION_NAME) }

    // Persistence — one driver, one database, one repository.
    single { createAndroidSqlDriver(androidContext()) }
    single { VastuDatabaseFactory.create(get()) }
    // ⭐ The repository owns the plan photographs as well as the rows, so a home and its picture can
    // never be deleted separately — see PlanPhotoStore for why that guarantee lives down there.
    single { PlanRepository(db = get(), io = Dispatchers.IO, photos = AndroidPlanPhotoStore(androidContext())) }

    // Scan — the REAL reader. One HTTP POST to the model named in `scan/reader-config.json`.
    //
    // ⚠ The stand-in reader that replayed recorded replies is deliberately NOT bound here any more.
    // It shipped in v0.3.14/15 and, because three of its four recorded readings were the same test
    // plan, every upload produced the same room list — which looks precisely like a broken feature.
    // Recorded replies still drive the tests and the render goldens, where they belong; the app now
    // either reads the user's plan or says it cannot.
    // The recipe is loaded ONCE and shared: the reader speaks it, and the scan screen's testing
    // picker lists its two model ids — so a config edit can never leave the picker naming models
    // the reader no longer calls.
    single { com.vastufirst.shared.scan.ScanReaderConfigLoader.load() }
    single<PlanReader> { GroqPlanReader(apiKey = BuildConfig.PLAN_READER_KEY, recipe = get()) }
    single<ImageDecoder> { AndroidImageDecoder(androidContext()) }
    single<PlanReadingConsent> { AndroidPlanReadingConsent(androidContext()) }
    single<ReaderChoice> { AndroidReaderChoice(androidContext()) }
    // The on-photo scan review (owner request, 4 Aug 2026): the Settings toggle that picks the
    // confirmation surface, and the one-field handover slot the scan writes before navigating.
    single { ScanReviewHandover() }

    // ⭐ The ₹699 checkout. Built in full, SWITCHED OFF by a build flag — and the "off" path is a
    // real, honest implementation rather than a disabled button: NoBilling unlocks locally and the
    // screen's own words (billingNotice) say plainly that no payment is taken. There is no key to
    // paste to turn it on; Play Billing identifies the app by its signature (docs/PLAY-STORE-SETUP.md).
    // ⭐ The published price reaches the screen HERE and nowhere else, so there is one place where
    // "what the app says it costs" is decided. With nothing published it is null, and the screen
    // falls back to the price built into the app — which is never deleted.
    single<Billing> {
        val publishedPrice = get<PublishedRules>().plans?.unlockPrice
        if (BuildConfig.PAYMENTS_ENABLED) {
            PlayBilling(androidContext(), activityProvider = CurrentActivity::get, publishedPrice = publishedPrice)
                .also { billing -> CoroutineScope(Dispatchers.Main).launch { billing.start() } }
        } else {
            NoBilling(publishedPrice = publishedPrice)
        }
    }

    // ViewModels.
    viewModel { HomeViewModel(repo = get(), engine = get()) }
    // The SavedStateHandle comes from the nav-graph entry that owns this ViewModel, so the two ids
    // it carries (draft / saved home) survive the OS killing the process mid-flow (audit B1).
    viewModel { NewPlanViewModel(engine = get(), repo = get(), handle = get()) }
    viewModel {
        val recipe = get<com.vastufirst.shared.scan.PlanReadRecipe>()
        ScanViewModel(
            reader = get(),
            decode = get(),
            // A build made without the key cannot read anything, and the screen says so rather than
            // offering a picker that leads nowhere.
            canRead = BuildConfig.PLAN_READER_KEY.isNotBlank(),
            // The testing picker's choices: the primary model first, then the second-opinion model.
            modelChoices = listOfNotNull(recipe.config.model.ifBlank { null }, recipe.config.escalationModel),
            // Read from Settings, not picked on the scan screen (owner, 10 Aug 2026).
            readerChoice = get(),
        )
    }
}
