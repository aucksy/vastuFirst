package com.vastufirst.app.render

import android.app.Application
import com.vastufirst.app.ui.scan.ScanScreen
import com.vastufirst.app.ui.scan.ScanUiState
import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.RefusalReason
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanNotes
import com.vastufirst.shared.scan.ScanOutcome
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every state of the scan screen, rendered across the §6.4 matrix (412 dp, 360 dp, 200 % font,
 * insets, RTL). CLAUDE.md §2b: a screen that has never been rendered is not done, and this one is
 * mostly *copy* — long, careful, load-bearing copy that has to survive a 200 % font scale on a
 * 360 dp phone, which is exactly the configuration that shattered rows in v0.2.1.
 *
 * ⭐ The Placed and Assisted states are driven by the **real recorded Groq replies** through the
 * **real mapper**, not by hand-written fixtures. So these goldens show what a user will actually
 * see for those two reads, and they change if the mapper's behaviour changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class ScanScreenshotTest {

    private fun screen(state: ScanUiState): @androidx.compose.runtime.Composable () -> Unit = {
        ScanScreen(
            state = state,
            onPickImage = {}, onTakePhoto = {}, onRetry = {},
            onUseRooms = {}, onDrawInstead = {}, onBack = {},
        )
    }

    /** The clean render: measured 8/8 rooms right — the read that gets its geometry trusted. */
    private fun placed(): ScanOutcome =
        ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)

    /**
     * A REAL 24-space floor plate: names read perfectly, rectangles scattered nowhere near the rooms
     * they name (verified by drawing them back over the plan). Too many rooms to trust the geometry,
     * so the room list is kept and the layout is thrown away. This is the path most real plans take.
     */
    private fun assisted(): ScanOutcome =
        ScanMapper.map(RecordedScans.load(RecordedScans.DENSE)!!.reply)

    @Test
    fun scanIdle() {
        captureAcrossMatrix("scan-idle", screen(ScanUiState.Idle))
        writeManifestAcrossMatrix("scan-idle", screen(ScanUiState.Idle))
    }

    @Test
    fun scanReading() {
        captureAcrossMatrix("scan-reading", screen(ScanUiState.Reading))
        writeManifestAcrossMatrix("scan-reading", screen(ScanUiState.Reading))
    }

    @Test
    fun scanPlaced() {
        val s = ScanUiState.Done(placed())
        captureAcrossMatrix("scan-placed", screen(s))
        writeManifestAcrossMatrix("scan-placed", screen(s))
    }

    @Test
    fun scanAssisted() {
        val s = ScanUiState.Done(assisted())
        captureAcrossMatrix("scan-assisted", screen(s))
        writeManifestAcrossMatrix("scan-assisted", screen(s))
    }

    /** The refusal a real upload hits most: one in five of the 30 real plans was a 3D render. */
    @Test
    fun scanRefused3d() {
        val s = ScanUiState.Done(ScanOutcome.Refused(RefusalReason.NOT_2D, ScanNotes(0.0, 0.0, 0.0)))
        captureAcrossMatrix("scan-refused-3d", screen(s))
        writeManifestAcrossMatrix("scan-refused-3d", screen(s))
    }

    @Test
    fun scanRefusedNoLabels() {
        val s = ScanUiState.Done(ScanOutcome.Refused(RefusalReason.NO_LABELS, ScanNotes(0.0, 0.0, 0.0)))
        captureAcrossMatrix("scan-refused-labels", screen(s))
        writeManifestAcrossMatrix("scan-refused-labels", screen(s))
    }

    /** Rate-limited. Roughly three scans a minute across all users on the free tier, so: expected. */
    @Test
    fun scanBusy() {
        val s = ScanUiState.Busy(retryAfterSeconds = 45)
        captureAcrossMatrix("scan-busy", screen(s))
        writeManifestAcrossMatrix("scan-busy", screen(s))
    }
}
