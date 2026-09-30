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
     * The real configured readers. Every "done" fixture below is still stamped with the one that
     * read it, because that is what the app really carries on [ScanUiState.Done].
     *
     * ⛔ NOTHING ON THESE GOLDENS MAY PRINT ONE (owner, 11 Aug 2026). The "Which AI read it ·
     * testing" section came off the customer's path; a model name reappearing in any of these
     * pictures means it has crept back. Choosing a reader lives in Settings, and `settings` in
     * [SimpleScreensScreenshotTest] is the golden that photographs it.
     */
    private val models: List<String> =
        com.vastufirst.shared.scan.ScanReaderConfigLoader.load().config
            .let { listOfNotNull(it.model.ifBlank { null }, it.escalationModel) }

    private fun screen(
        state: ScanUiState,
        openRow: Int = -1,
        noCamera: Boolean = false,
        readingElapsedMillis: Long = 0L,
        north: com.vastufirst.app.ui.scan.NorthOnResult = com.vastufirst.app.ui.scan.NorthOnResult(),
    ): @androidx.compose.runtime.Composable () -> Unit = {
        ScanScreen(
            state = state,
            onPickImage = {}, onTakePhoto = {}, onRetry = {},
            onUseRooms = {}, onCorrectRoom = { _, _ -> }, onDrawInstead = {}, onBack = {},
            startOpenRow = openRow,
            cameraUnavailable = noCamera,
            readingElapsedMillis = readingElapsedMillis,
            north = north,
        )
    }

    /**
     * ⭐⭐ NORTH ON THE RESULT ITSELF (30 Sep 2026) — the home a placed read has just become, worked out
     * exactly the way the app works it out: the scan's rooms through the real grid conversion, the
     * real front-door read and the real engine, with North at 0. The "Is this right?" card under the
     * dial quotes this analysis, so a faked one would photograph claims no reader could get.
     */
    private fun northFor(
        outcome: ScanOutcome.Placed,
        photo: androidx.compose.ui.graphics.ImageBitmap,
    ): com.vastufirst.app.ui.scan.NorthOnResult {
        val grid = com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows)
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(grid)
        val plan = com.vastufirst.app.ui.newplan.buildEnginePlan(
            rooms = grid,
            door = door,
            intent = com.vastufirst.shared.Intent.BUYING,
            propertyType = com.vastufirst.shared.PropertyType.FLAT,
            north = 0,
            planId = "golden-scan-result",
        )
        return com.vastufirst.app.ui.scan.NorthOnResult(
            rooms = grid,
            north = 0,
            analysis = plan?.let { com.vastufirst.engine.VastuEngine().analyze(it) },
            cols = outcome.cols,
            rows = outcome.rows,
            planImage = photo,
            doorKnown = door != null,
        )
    }

    /** The clean render: measured 8/8 rooms right — the read that gets its geometry trusted. */
    private fun placed(): ScanOutcome =
        ScanMapper.map(RecordedScans.load(RecordedScans.CLEAN)!!.reply)

    /**
     * A REAL 21-space apartment whose sheet prints **no room size anywhere**. With no printed
     * measurement to anchor anything, that many rooms is the reader's template rather than the home,
     * so the room list is kept and the layout is thrown away. This is the path most real plans take.
     *
     * ⚠ Was `real-dense` until 16 Aug 2026. Removing the lift rule made `real-dense` PLACE, so this
     * golden was photographing the Placed screen under the name "assisted" — green, and wrong.
     */
    private fun assisted(): ScanOutcome =
        ScanMapper.map(RecordedScans.load(RecordedScans.UNSIZED)!!.reply)

    @Test
    fun scanIdle() {
        captureAcrossMatrix("scan-idle", screen(ScanUiState.Idle))
        writeManifestAcrossMatrix("scan-idle", screen(ScanUiState.Idle))
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

    /**
     * ⭐ The same screen with the E's note open (owner, 27 Sep 2026: *"on first tap and pop up can
     * tell them what it is"*). A golden cannot tap, so without this the note on this screen would be
     * a thing no picture has ever shown — at 200 % font on a 320 dp phone least of all.
     */
    @Test
    fun scanDoorNoteOpen() {
        val outcome = ownersPlan()
        val door = com.vastufirst.app.ui.newplan.frontDoorFromEntrance(
            com.vastufirst.app.ui.scan.toGridRooms(outcome.rooms, outcome.cols, outcome.rows),
        )
        // The guard: a fixture that stopped yielding a door would photograph no E and no note.
        kotlin.test.assertNotNull(door, "this fixture must yield a door, or the golden proves nothing")
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.app.ui.scan.ScanDoorContent(
                image = planPhoto(),
                rooms = outcome.rooms,
                door = door,
                startDoorNoteOpen = true,
            )
        }
        captureAcrossMatrix("scan-door-note", content)
        writeManifestAcrossMatrix("scan-door-note", content)
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

    /**
     * ⭐⭐ THE WAIT WHEN IT STOPS BEING SHORT (11 Aug 2026) — the two states a reader actually gets
     * stuck in, and the ones no picture contained.
     *
     * A read is usually a few seconds, but the reader is allowed 20 s to connect plus 120 s to
     * answer, and nothing on this screen moves — the loading surface is centred text with no
     * indicator. So the words are the ONLY thing that can tell somebody the app has not frozen, and
     * words are exactly what a golden can check.
     *
     * ⚠ Driven by a plain number, not by a clock. The elapsed time is a parameter with a default of
     * zero and the clock lives in ScanRoute, which the harness never renders — a composition that
     * keeps ticking never goes idle, and the harness waits for idle before it photographs.
     */
    @Test
    fun scanReadingStillGoing() {
        val s = screen(ScanUiState.Reading, readingElapsedMillis = 10_000L)
        captureAcrossMatrix("scan-reading-still", s)
        writeManifestAcrossMatrix("scan-reading-still", s)
    }

    @Test
    fun scanReadingSecondLook() {
        val s = screen(ScanUiState.Reading, readingElapsedMillis = 40_000L)
        captureAcrossMatrix("scan-reading-second-look", s)
        writeManifestAcrossMatrix("scan-reading-second-look", s)
    }

    /**
     * ⭐ The failure every slow or broken read lands on. Rendered because its words changed on
     * 11 Aug 2026: it used to tell a reader on perfect Wi-Fi to go and check their Wi-Fi, whatever
     * had actually gone wrong.
     */
    @Test
    fun scanUnavailable() {
        captureAcrossMatrix("scan-unavailable", screen(ScanUiState.Unavailable))
        writeManifestAcrossMatrix("scan-unavailable", screen(ScanUiState.Unavailable))
    }

    /**
     * ⭐⭐ A PLACED READ: "We read N rooms", the rooms folded shut under it, and North asked on the
     * reader's own plan (30 Sep 2026). The dial draws `plan-01`, the repository's one real plan
     * picture — a SAMPLE sheet, not anybody's home — so the picture shows a plan inside the compass,
     * not a blank stand-in.
     */
    @Test
    fun scanPlaced() {
        val outcome = PlanSheet.outcome()
        val s = ScanUiState.Done(outcome, readBy = models.firstOrNull())
        val content = screen(s, north = northFor(outcome, PlanSheet.bitmap().asImageBitmap()))
        captureAcrossMatrix("scan-placed", content)
        writeManifestAcrossMatrix("scan-placed", content)
    }

    /**
     * ⭐ The same screen with its folded room list OPEN — the one place a room's kind is corrected. Every
     * fold and every i on the screen is opened by the photography seam, because a golden cannot tap.
     *
     * It is the owner's own sheet (`plan-020`), which carries the two things this list must show that no
     * other fixture does: rooms the reader was unsure of (the CHECK pill, and "4 to check" on the
     * heading), and a balcony his sheet dimensions in three pieces, printed as "one space: A + B + C" —
     * the job "Check what we read" used to do alone.
     */
    @Test
    fun scanPlacedRoomsOpen() {
        val outcome = ownersPlan()
        val s = ScanUiState.Done(outcome, readBy = models.firstOrNull())
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            com.vastufirst.designsystem.components.VastuRevealAll {
                screen(s, north = northFor(outcome, planPhoto()))()
            }
        }
        captureAcrossMatrix("scan-placed-open", content)
        writeManifestAcrossMatrix("scan-placed-open", content)
    }

    /**
     * ⭐⭐ THE OWNER'S OWN FLAT — fifteen rooms, every one with its printed size, and a tall sheet in the
     * dial. With the list folded, what the picture has to show is the heading's count and the compass
     * on a portrait page, with the button still reachable at 200 % font on a 320 dp phone.
     */
    @Test
    fun scanPlacedOwnerFlat() {
        val outcome = ScanMapper.map(
            RecordedScans.load(RecordedScans.OWNER_FLAT)!!.reply,
            imageAspect = 646.0 / 1400.0,
        ) as ScanOutcome.Placed
        val tall = android.graphics.Bitmap.createBitmap(646, 1400, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.rgb(0xEF, 0xE9, 0xDA)) }
            .asImageBitmap()
        val s = ScanUiState.Done(outcome, readBy = models.firstOrNull())
        val content = screen(s, north = northFor(outcome, tall))
        captureAcrossMatrix("scan-placed-sizes", content)
        writeManifestAcrossMatrix("scan-placed-sizes", content)
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
