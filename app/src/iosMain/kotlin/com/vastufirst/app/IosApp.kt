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
import com.vastufirst.app.ui.scan.DecodedImage
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
import com.vastufirst.shared.scan.PlanImage
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.ScanReaderConfigLoader
import com.vastufirst.shared.scan.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * ⭐ THE iPHONE APP'S FRONT DOOR (iPhone plan, stage 3). The Swift app (iosApp/) shows this one view
 * controller, and everything inside it is the shared app — the same screens, navigation and engine
 * Android runs. What differs is only the wiring below: where homes are stored, how settings are kept,
 * and the pieces not built for the iPhone yet, each of which SAYS so rather than pretending.
 */
@Suppress("FunctionName", "unused") // called from Swift: MainViewControllerKt.MainViewController()
fun MainViewController(): UIViewController {
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
    startKoin { modules(iosModule) }
}

/**
 * The iPhone's half of the wiring — Android's is di/AppModule.kt. The rules are the ones built into
 * the app (taking a newer published set from the Control Room arrives in stage 4).
 */
val iosModule = module {
    single { VastuEngine() }
    single { NativeSqliteDriver(VastuDatabase.Schema, "vastufirst.db") }
    single { VastuDatabaseFactory.create(get<NativeSqliteDriver>()) }
    single { PlanRepository(db = get(), io = Dispatchers.IO, photos = IosPlanPhotoStore) }
    single { ScanReaderConfigLoader.load() }
    // Reading a plan on the iPhone arrives in stage 4 (its pickers and the relay call). Until then the
    // scan screen says so (ScanRoute.ios.kt) and this reader answers "unavailable" if ever asked.
    single<PlanReader> { NotYetOnIphoneReader }
    single<ImageDecoder> { NotYetOnIphoneDecoder }
    single<PlanReadingConsent> { UserDefaultsPlanReadingConsent }
    single<ReaderChoice> { UserDefaultsReaderChoice }
    single { ScanPictureSlot() }
    // Apple's checkout is stage 6. With payments off the screen says no payment is taken, as on Android.
    single<Billing> { NoBilling() }

    viewModel { HomeViewModel(repo = get(), engine = get()) }
    viewModel { NewPlanViewModel(engine = get(), repo = get(), handle = get()) }
    viewModel {
        val recipe = get<com.vastufirst.shared.scan.PlanReadRecipe>()
        ScanViewModel(
            reader = get(),
            decode = get(),
            canRead = false,
            modelChoices = listOfNotNull(recipe.config.model.ifBlank { null }, recipe.config.escalationModel),
            readerChoice = get(),
        )
    }
}

private object NotYetOnIphoneReader : PlanReader {
    override suspend fun read(image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult =
        ScanResult.Unavailable
}

private object NotYetOnIphoneDecoder : ImageDecoder {
    override suspend fun toJpeg(source: Any): DecodedImage? = null
}
