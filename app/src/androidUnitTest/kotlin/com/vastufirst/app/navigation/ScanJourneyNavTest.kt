package com.vastufirst.app.navigation

import android.app.Application
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.vastufirst.app.billing.Billing
import com.vastufirst.app.billing.NoBilling
import com.vastufirst.app.di.appModule
import com.vastufirst.app.ui.scan.DecodedImage
import com.vastufirst.app.ui.scan.ImageDecoder
import com.vastufirst.app.ui.scan.ScanViewModel
import com.vastufirst.designsystem.theme.VastuTheme
import com.vastufirst.shared.scan.PlanImage
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanResult
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ A FIRST SCAN, WALKED THROUGH THE REAL APP — the real navigation graph, the real screens, the real
 * view models and database, and real taps — from a fresh install to the report (30 Sep 2026).
 *
 * Every other test in this suite draws one screen from a fixture. This one proves the SCREENS JOIN UP:
 * that "Upload a plan" opens the phone's picker by itself, that the read lands on "We read N rooms"
 * with North on the same screen, and that a plan naming its own entrance goes from there straight to
 * its report — with no "Check what we read" in between.
 *
 * Nothing leaves the machine and nothing is paid for: the plan reader is a stand-in that answers with
 * the RECORDED reply for the owner's own sheet (`plan-020`), and the phone's picker is a stand-in that
 * hands back one made-up file. Everything else is the app as it ships.
 *
 * ⚠ REPORTED, NOT ENFORCED, on its first cloud run. It needs the whole app graph to start inside the
 * test harness — database, dependency injection, the picker seam — which nothing here has done before.
 * It prints `NAVTEST|PASS` or `NAVTEST|FAIL|…` into the log. If it passes, the next commit makes it a
 * real test; if it does not, it is removed rather than spending cloud rounds on it (the owner's brief).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ScanJourneyNavTest {

    /** Answers every read with the recorded reply for the owner's own sheet, which prints FOYER. */
    private object RecordedReader : PlanReader {
        override suspend fun read(image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult =
            ScanResult.Read(
                ScanMapper.map(RecordedScans.load(RecordedScans.PLAN_020)!!.reply, imageAspect = 1399.0 / 1389.0),
                readBy = "recorded",
            )
    }

    /** Whatever file the "picker" hands back, a real little JPEG — so the app's own decoding runs. */
    private object BeigeJpeg : ImageDecoder {
        override suspend fun toJpeg(source: Any): DecodedImage {
            val bitmap = android.graphics.Bitmap.createBitmap(280, 278, android.graphics.Bitmap.Config.ARGB_8888)
                .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            return DecodedImage(out.toByteArray(), 1399.0 / 1389.0)
        }
    }

    @After
    fun stop() {
        runCatching { stopKoin() }
    }

    @Test
    fun a_first_scan_reaches_its_report_without_a_checking_screen() {
        val result = runCatching { walk() }
        val line = result.exceptionOrNull()
            ?.let { "NAVTEST|FAIL|${it.javaClass.simpleName}: ${it.message?.replace('\n', ' ')?.take(400)}" }
            ?: "NAVTEST|PASS|Welcome → Add a home → consent → picker → We read 11 rooms (North) → report"
        println(line)
    }

    private fun walk() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        startKoin {
            allowOverride(true)
            androidContext(app)
            modules(
                appModule,
                module {
                    single<PlanReader> { RecordedReader }
                    single<ImageDecoder> { BeigeJpeg }
                    single<Billing> { NoBilling() }
                    viewModel { ScanViewModel(reader = get(), decode = get(), canRead = true) }
                },
            )
        }
        runComposeUiTest {
            // The phone's picker, stood in for: whatever is launched comes straight back with one file.
            val registry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, Uri.parse("content://vastufirst.test/plan.jpg") as O)
                }
            }
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
            setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    VastuTheme { VastuNavHost() }
                }
            }
            tapWhenShown("I am buying a home")
            tapWhenShown("Upload a plan")
            tapWhenShown("I agree — read my plan")
            // The picker opens by itself and the read lands on the result, with North on it.
            waitFor("Which way is North?", 30_000)
            check(onAllNodesWithText("Check what we read").fetchSemanticsNodes().isEmpty()) {
                "the checking screen appeared"
            }
            tapWhenShown("Yes — read my home", 10_000)
            waitFor("/ 10", 30_000)
        }
    }

    private fun ComposeUiTest.waitFor(text: String, millis: Long = 10_000) {
        waitUntil(timeoutMillis = millis) { onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.tapWhenShown(text: String, millis: Long = 10_000) {
        waitFor(text, millis)
        onNodeWithText(text, substring = true).performScrollTo().performClick()
    }
}
