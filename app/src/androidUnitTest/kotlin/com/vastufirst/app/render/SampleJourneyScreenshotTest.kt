package com.vastufirst.app.render

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import com.vastufirst.app.ui.newplan.buildEnginePlan
import com.vastufirst.app.ui.report.ReportContent
import com.vastufirst.app.ui.scan.NorthOnResult
import com.vastufirst.app.ui.scan.ScanScreen
import com.vastufirst.app.ui.scan.ScanUiState
import com.vastufirst.app.ui.scan.doorForPhotoTap
import com.vastufirst.app.ui.scan.doorMarkerOnPage
import com.vastufirst.app.ui.scan.planRoomsOf
import com.vastufirst.app.ui.scan.toGridRooms
import com.vastufirst.engine.VastuEngine
import com.vastufirst.shared.Intent
import com.vastufirst.shared.PropertyType
import com.vastufirst.shared.scan.SampleScan
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ⭐⭐ "TRY A SAMPLE PLAN" — the two screens it now shows (owner, 15 Oct 2026: option B).
 *
 *  - `sample-result` — "We read 8 rooms" with North asked on the sample sheet's own photograph, the front
 *    door already known (the sheet draws it), so the button reads "Read my home".
 *  - `sample-report` — the top of the sample's report: its score, and its picture of record is the
 *    photograph, never drawn room boxes.
 *
 * Built exactly as the app builds it: [SampleScan] for the reading, the door from the sheet's own door
 * gap, North 0, a person building a home.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class SampleJourneyScreenshotTest {

    private val outcome = SampleScan.outcome()!!
    private val grid = toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
    private val door = doorForPhotoTap(SampleScan.DOOR_PAGE_X, SampleScan.DOOR_PAGE_Y, outcome.rooms)
    private val analysis = VastuEngine().analyze(
        buildEnginePlan(
            rooms = grid,
            door = door,
            intent = Intent.BUILDING,
            propertyType = PropertyType.INDEPENDENT_HOUSE,
            north = 0,
            planId = "golden-sample",
        )!!,
    )

    @Test
    fun sample_result() {
        val content: @Composable () -> Unit = {
            ScanScreen(
                state = ScanUiState.Done(outcome),
                onPickImage = {}, onTakePhoto = {}, onRetry = {},
                onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
                north = NorthOnResult(
                    rooms = grid,
                    north = 0,
                    analysis = analysis,
                    cols = outcome.cols,
                    rows = outcome.rows,
                    planImage = PlanSheet.bitmap().asImageBitmap(),
                    doorKnown = door != null,
                ),
            )
        }
        captureAcrossMatrix("sample-result", content)
        writeManifestAcrossMatrix("sample-result", content)
    }

    @Test
    fun sample_report() {
        val content: @Composable () -> Unit = {
            ReportContent(
                analysis = analysis,
                intent = Intent.BUILDING,
                rooms = grid,
                north = 0,
                cols = outcome.cols,
                rows = outcome.rows,
                planImage = PlanSheet.bitmap().asImageBitmap(),
                planRooms = planRoomsOf(outcome.rooms),
                doorAtPage = door?.let { doorMarkerOnPage(it, outcome.rooms) },
            )
        }
        captureAcrossMatrix("sample-report", content)
        writeManifestAcrossMatrix("sample-report", content)
    }
}
