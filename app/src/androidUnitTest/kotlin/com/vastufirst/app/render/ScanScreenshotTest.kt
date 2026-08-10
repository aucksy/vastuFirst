package com.vastufirst.app.render

import android.app.Application
import androidx.compose.ui.graphics.asImageBitmap
import com.vastufirst.app.ui.scan.ScanConsentScreen
import com.vastufirst.app.ui.scan.ScanScreen
import com.vastufirst.app.ui.scan.ScanUiState
import com.vastufirst.shared.scan.PlanImageType
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

    /**
     * ⭐ The reader-comparison levers (owner request, 4 Aug 2026) render in every golden, from the
     * REAL config — so a model swap in `reader-config.json` changes these pictures, which is the
     * point: the picker can never photograph models the reader no longer calls.
     */
    private val models: List<String> =
        com.vastufirst.shared.scan.ScanReaderConfigLoader.load().config
            .let { listOfNotNull(it.model.ifBlank { null }, it.escalationModel) }

    private fun screen(
        state: ScanUiState,
        openRow: Int = -1,
        noCamera: Boolean = false,
    ): @androidx.compose.runtime.Composable () -> Unit = {
        ScanScreen(
            state = state,
            onPickImage = {}, onTakePhoto = {}, onRetry = {},
            onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
            startOpenRow = openRow,
            cameraUnavailable = noCamera,
            modelChoices = models,
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

    /**
     * ⭐ The ON-PHOTO review (owner request, 4 Aug 2026): the scanned picture with one room's
     * reading tinted over it, and the room list beneath. Driven by the real recorded clean read
     * through the real mapper, so the list is what a user would actually be checking; the "photo"
     * is a flat stand-in bitmap (the harness has no real photograph, and the overlay geometry —
     * the thing this golden guards — is the same over any pixels).
     */
    @Test
    fun scanReview() {
        val placedOutcome = placed() as com.vastufirst.shared.scan.ScanOutcome.Placed
        val photo = android.graphics.Bitmap.createBitmap(1400, 990, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanReviewContent(
                image = photo.asImageBitmap(),
                rooms = placedOutcome.rooms,
                startSelected = 2,
            )
        }
        captureAcrossMatrix("scan-review", content)
        writeManifestAcrossMatrix("scan-review", content)
    }

    /**
     * ⭐⭐ The owner's OWN plan, on the two screens his 6 Aug 2026 notes are about — driven by the
     * recorded reply for it (`plan-020`), so these goldens are literally the pictures he was
     * looking at when he wrote them.
     *
     * What they must show:
     *   · ONE balcony row for the strip his sheet captions three times, printing all three sizes.
     *   · The front door STATED rather than asked for, because his plan prints FOYER — and the
     *     line that says so has to survive 200 % font on a 360 dp phone like everything else.
     */
    private fun ownersPlan(): ScanOutcome.Placed =
        ScanMapper.map(
            RecordedScans.load(RecordedScans.PLAN_020)!!.reply,
            imageAspect = 1399.0 / 1389.0,
        ) as ScanOutcome.Placed

    private fun planPhoto() =
        android.graphics.Bitmap.createBitmap(1399, 1389, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()

    /**
     * ⭐⭐ A TINTED BOX ON A PLAN THAT ACTUALLY PRINTS ITS SIZES — the picture that was missing.
     *
     * [scanReview] is the only golden that shows a tint, and it is driven by `plan-01`, which is the
     * one fixture family in the whole corpus printing **no room sizes at all**. So when the boxes
     * were corrected to draw at the printed size (10 Aug 2026, `ScannedRoom.printedBox`), the change
     * altered nothing this gate photographs: every golden verified clean and the fix was invisible.
     * That is the same blind spot as a golden that never scrolls and a gate that measures boxes
     * rather than glyphs — the number was green and the picture was never taken.
     *
     * So this photographs the owner's own flat, every one of whose fifteen rooms prints a size, with
     * a room selected that the correction actually moved. It fails loudly if no such room exists,
     * because a golden that silently falls back to the reader's rectangle would quietly become
     * another picture of nothing.
     */
    @Test
    fun scanReviewPrintedSizes() {
        val flat = ScanMapper.map(RecordedScans.load(RecordedScans.OWNER_FLAT)!!.reply)
            as ScanOutcome.Placed
        val corrected = flat.rooms.indexOfFirst { it.printedBox != null }
        check(corrected >= 0) {
            "No room on the owner's own flat draws at its printed size, so this golden would " +
                "photograph the reader's rectangle and prove nothing. Either the correction " +
                "regressed or this fixture stopped printing sizes."
        }
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanReviewContent(
                image = planPhoto(),
                rooms = flat.rooms,
                startSelected = corrected,
            )
        }
        captureAcrossMatrix("scan-review-printed", content)
        writeManifestAcrossMatrix("scan-review-printed", content)
    }

    @Test
    fun scanReviewWithDoorReadFromThePlan() {
        val outcome = ownersPlan()
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
            com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
        )
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanReviewContent(
                image = planPhoto(),
                rooms = outcome.rooms,
                door = door,
                startSelected = -1,
            )
        }
        captureAcrossMatrix("scan-review-door", content)
        writeManifestAcrossMatrix("scan-review-door", content)
    }

    /**
     * ⭐ Marking the front door ON THE PHOTO — the screen that replaced the hop into the grid editor.
     * Rendered with the door already placed, because the marker over the plan and the outline it
     * sits on are the two things a screenshot can check and a tap test cannot.
     */
    @Test
    fun scanDoor() {
        val outcome = ownersPlan()
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
            com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
        )
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanDoorContent(
                image = planPhoto(),
                rooms = outcome.rooms,
                door = door,
            )
        }
        captureAcrossMatrix("scan-door", content)
        writeManifestAcrossMatrix("scan-door", content)
    }

    /** The same screen before anything is marked — the state a plan with no printed entrance opens in. */
    @Test
    fun scanDoorUnmarked() {
        val outcome = ownersPlan()
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanDoorContent(
                image = planPhoto(),
                rooms = outcome.rooms,
                door = null,
            )
        }
        captureAcrossMatrix("scan-door-unmarked", content)
        writeManifestAcrossMatrix("scan-door-unmarked", content)
    }

    /**
     * ⭐ A phone with no camera app at all, after the camera button has been pressed (v0.6.6).
     *
     * ⚠ The button used to open the GALLERY — the same picker as the button above it — so someone
     * holding a printed plan had no way to photograph it and the button looked broken. It now opens
     * the camera, which means it can also fail on a phone that has none, and that failure has to be
     * a sentence rather than a tap that does nothing. No screenshot can reach this state by tapping.
     */
    @Test
    fun scanIdleWithoutCamera() {
        captureAcrossMatrix("scan-idle-no-camera", screen(ScanUiState.Idle, noCamera = true))
        writeManifestAcrossMatrix("scan-idle-no-camera", screen(ScanUiState.Idle, noCamera = true))
    }

    @Test
    fun scanReading() {
        captureAcrossMatrix("scan-reading", screen(ScanUiState.Reading))
        writeManifestAcrossMatrix("scan-reading", screen(ScanUiState.Reading))
    }

    @Test
    fun scanPlaced() {
        val s = ScanUiState.Done(placed(), readBy = models.firstOrNull())
        captureAcrossMatrix("scan-placed", screen(s))
        writeManifestAcrossMatrix("scan-placed", screen(s))
    }

    /**
     * ⭐⭐ THE OWNER'S OWN FLAT — and the only screen that shows the SIZE the plan printed.
     *
     * ⚠ Every other fixture predates that field and carries its dimensions inside the caption, so
     * nothing here would have photographed the new line at all. It is the one line on this screen
     * whose numbers now decide each room's shape — and therefore its Vastu direction — so a person
     * confirming their plan is checking exactly this against their own paper.
     *
     * What the picture has to show: fifteen rooms, each with its name AND its printed size, on one
     * caption line, with the button still reachable at 200 % font on a 320 dp phone. His sheet prints
     * every size twice (metric then imperial); if the repeat is back, this row is several lines tall
     * and the button is gone.
     */
    @Test
    fun scanPlacedOwnerFlat() {
        val s = ScanUiState.Done(
            ScanMapper.map(
                RecordedScans.load(RecordedScans.OWNER_FLAT)!!.reply,
                imageAspect = 646.0 / 1400.0,
            ),
            readBy = models.firstOrNull(),
        )
        captureAcrossMatrix("scan-placed-sizes", screen(s))
        writeManifestAcrossMatrix("scan-placed-sizes", screen(s))
    }

    @Test
    fun scanAssisted() {
        val s = ScanUiState.Done(assisted(), readBy = models.firstOrNull())
        captureAcrossMatrix("scan-assisted", screen(s))
        writeManifestAcrossMatrix("scan-assisted", screen(s))
    }

    /**
     * ⭐ A room's type list open on the confirmation screen — the correction §6.2b always required
     * and the screen never offered.
     *
     * Rendered because it is the narrowest place the nineteen wrapped chips have to fit (inside a
     * card, inside the screen's padding) and because this screen has already produced two contrast
     * defects, both on load-bearing copy, both invisible until something drew them.
     */
    @Test
    fun scanRetype() {
        val s = ScanUiState.Done(assisted(), readBy = models.firstOrNull())
        captureAcrossMatrix("scan-retype", screen(s, openRow = 0))
        writeManifestAcrossMatrix("scan-retype", screen(s, openRow = 0))
    }

    /** The refusal a real upload hits most: one in five of the 30 real plans was a 3D render. */
    @Test
    fun scanRefused3d() {
        val s = ScanUiState.Done(ScanOutcome.Refused(RefusalReason.NOT_2D, ScanNotes(0.0, 0.0, 0.0)), readBy = models.firstOrNull())
        captureAcrossMatrix("scan-refused-3d", screen(s))
        writeManifestAcrossMatrix("scan-refused-3d", screen(s))
    }

    /**
     * ⭐ The SAME refusal when the reply that earned it came back full of rooms — the case that hard-
     * stopped the owner on his own labelled, dimensioned, top-down plan (both models called it 3D).
     * It is a different screen: a different headline, different words, and a third button that
     * leads on. Rendered because the tallest of the three refusals is the one nobody had drawn.
     */
    @Test
    fun scanRefused3dWithRead() {
        val s = ScanUiState.Done(
            ScanOutcome.Refused(RefusalReason.NOT_2D, ScanNotes(0.0, 0.0, 0.0), ifRead = assisted()),
            readBy = models.firstOrNull(),
        )
        captureAcrossMatrix("scan-refused-3d-readable", screen(s))
        writeManifestAcrossMatrix("scan-refused-3d-readable", screen(s))
    }

    /**
     * ⭐ …and the screen the button above LANDS on, which is where the honesty has to hold. Built
     * the real way — a reply that would otherwise be Placed, carrying a 3D verdict — so the golden
     * proves the drawn layout really is withheld and the new "we kept the names, we threw the
     * shapes away" line really is what a reader sees.
     */
    @Test
    fun scanAngledView() {
        val reply = RecordedScans.load(RecordedScans.CLEAN)!!.reply
        val refused = ScanMapper.map(reply.copy(planType = PlanImageType.THREE_D_RENDER))
        val s = ScanUiState.Done(
            (refused as ScanOutcome.Refused).ifRead!!,
            readBy = models.firstOrNull(),
        )
        captureAcrossMatrix("scan-angled-view", screen(s))
        writeManifestAcrossMatrix("scan-angled-view", screen(s))
    }

    @Test
    fun scanRefusedNoLabels() {
        val s = ScanUiState.Done(ScanOutcome.Refused(RefusalReason.NO_LABELS, ScanNotes(0.0, 0.0, 0.0)), readBy = models.firstOrNull())
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

    /**
     * ⭐ A build made without the plan-reading key. Rendered because it is the screen that stops the
     * v0.3.14/15 failure repeating: a build that cannot read plans looked exactly like one that
     * could, so the owner spent an evening uploading different pictures at a stand-in reader that was
     * replaying the same recorded plan. There is no picker in this state, on purpose.
     */
    @Test
    fun scanNotConfigured() {
        captureAcrossMatrix("scan-not-configured", screen(ScanUiState.NotConfigured))
        writeManifestAcrossMatrix("scan-not-configured", screen(ScanUiState.NotConfigured))
    }

    /**
     * The privacy gate (§6.3). It is the first screen in this app that asks the user to let something
     * leave the phone, so its copy is the most load-bearing on the whole scan path — and it has to
     * hold at 200 % font on a 360 dp screen without a single line being clipped, because a consent
     * notice you cannot finish reading is not a consent notice.
     */
    @Test
    fun scanConsent() {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            ScanConsentScreen(onAgree = {}, onDrawInstead = {}, onBack = {})
        }
        captureAcrossMatrix("scan-consent", content)
        writeManifestAcrossMatrix("scan-consent", content)
    }
}
