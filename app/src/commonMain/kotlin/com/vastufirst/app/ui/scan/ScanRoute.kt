package com.vastufirst.app.ui.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Which door "Add a home" opened this screen through — the picker or the camera. */
enum class ScanStart { PICK, CAMERA }

/**
 * The phone's own ways in for a plan picture. Each phone answers it (androidMain / iosMain); the scan
 * route below is the same on both.
 */
interface PlanPickers {
    /** Open the picker for a PDF or a picture. */
    fun pickDocument()

    /** Open the camera. False when this phone has no camera to open — the screen then says so. */
    fun takePhoto(): Boolean
}

/** The pickers, delivering what was picked to [onPicked] — or null when the person backed out. */
@Composable
expect fun rememberPlanPickers(onPicked: (Any?) -> Unit): PlanPickers

/**
 * The scan screen wired to the platform, by two doors and no more.
 *
 * ⭐ **Neither of them asks for a permission, and that is the point.** This feature's whole problem
 * is asking somebody to trust us with a picture of their home; a permission dialog on top of that is
 * a cost we have never had to pay, and `scripts/check-manifest.sh` fails the build if one appears.
 *
 * - **"Choose a PDF or picture" → `OpenDocument`.** The system hands back one file the user chose.
 *   PDF is first in the filter because it is the input that reads best (skew is what ruins a read,
 *   §3e, and a PDF has none); the filter's second entry is every image type, which is why this one
 *   door still covers a picture they already have.
 *   ⚠ Do not write that second filter string inside a KDoc. Kotlin block comments NEST, so the
 *   slash-star in it opens a comment the closing marker below then closes — leaving this whole
 *   comment unterminated and the file failing to compile with "Unclosed comment".
 * - **⭐⭐ "Take a photo of it now" → `TakePicture`** (v0.6.6). This used to be `PickVisualMedia` —
 *   the gallery, i.e. the same door as the button above it wearing a different label — so somebody
 *   holding a printed plan had no way to photograph it from inside the app, and the button looked
 *   broken because it was. The capture goes to the phone's OWN camera app, which is why no CAMERA
 *   permission is needed: Android demands that grant only from an app that declares it. The camera
 *   writes into one cache folder granted for the length of one shot, and the picture never enters
 *   the user's gallery.
 *
 * There is deliberately no third door. Two clear choices beat three on a screen whose reader may be
 * older, less phone-literate, and standing in daylight.
 */
@Composable
fun ScanRoute(
    vm: ScanViewModel,
    onUseRooms: (com.vastufirst.shared.scan.ScanOutcome) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
    startWith: ScanStart? = null,
    north: NorthOnResult = NorthOnResult(),
) {
    // Which way in the user last chose, so "try a different picture" reopens the same one.
    var lastWasCamera by rememberSaveable { mutableStateOf(false) }
    // Set when the phone has no camera app at all (an emulator, a stripped ROM). The screen then
    // says so in one line rather than the button doing nothing — the defect this release fixes.
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    // ⭐ SAVEABLE, like the flags above: true once this screen has opened the picker by itself, so a
    // screen rebuilt after the picker (or after Android reclaimed the app while it was open) never
    // opens it a second time.
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    // True until the first pick of an automatic opening comes back. A reader who cancels THAT pick
    // meant "not now" and goes back to Add a home, instead of landing on an upload screen they never
    // asked for.
    var awaitingFirstPick by rememberSaveable { mutableStateOf(false) }

    fun picked(source: Any?) {
        val cancelledTheFirst = awaitingFirstPick && source == null
        awaitingFirstPick = false
        if (cancelledTheFirst) onBack() else vm.scan(source)
    }

    // The phone's own picker and camera (Android: its document picker and camera app; iPhone: Photos,
    // Files and its camera). Each hands back what it picked, or null when the person backed out.
    val pickers = rememberPlanPickers(onPicked = ::picked)
    val pickDocument = remember(pickers) {
        {
            lastWasCamera = false
            pickers.pickDocument()
        }
    }
    val takePhoto = remember(pickers) {
        {
            lastWasCamera = true
            if (!pickers.takePhoto()) { cameraUnavailable = true; awaitingFirstPick = false }
        }
    }

    LaunchedEffect(Unit) {
        if (startWith == null || autoOpened || vm.state != ScanUiState.Idle) return@LaunchedEffect
        autoOpened = true
        awaitingFirstPick = true
        when (startWith) {
            ScanStart.PICK -> pickDocument()
            ScanStart.CAMERA -> takePhoto()
        }
    }

    // ⭐ HOW LONG THIS READ HAS BEEN GOING, so the waiting screen can stop promising "a few
    // seconds" once it plainly isn't. See ReadingBody for what the reader is spared.
    //
    // ⚠⚠ IT LIVES HERE, NOT IN THE SCREEN, and it STOPS. Two rules this project has already paid
    // for: a composition that never settles never goes idle, and the screenshot harness waits for
    // idle before it photographs — one infinite animation hung a cloud build for forty minutes with
    // no error at all. This route is never rendered by the harness (it needs a ViewModel and the
    // platform pickers), and even so the loop below ends after the last step rather than ticking on
    // for the two minutes a read is allowed to take.
    var readingElapsed by remember { mutableStateOf(0L) }
    val isReading = vm.state is ScanUiState.Reading
    LaunchedEffect(isReading) {
        readingElapsed = 0L
        if (!isReading) return@LaunchedEffect
        delay(STILL_READING_AFTER_MILLIS)
        readingElapsed = STILL_READING_AFTER_MILLIS
        delay(SECOND_LOOK_AFTER_MILLIS - STILL_READING_AFTER_MILLIS)
        readingElapsed = SECOND_LOOK_AFTER_MILLIS
    }

    ScanScreen(
        state = vm.state,
        onPickImage = pickDocument,
        onTakePhoto = takePhoto,
        cameraUnavailable = cameraUnavailable,
        readingElapsedMillis = readingElapsed,
        // "Try again" means retry the same file when the reader was busy or offline (that is the
        // fix), and choose a new file when the plan itself was the problem.
        onRetry = {
            when (vm.state) {
                is ScanUiState.Busy, ScanUiState.Unavailable -> vm.retrySameImage()
                // ⚠ A retry after a camera shot reopens the CAMERA, not the gallery. Sending someone
                // who photographed their plan to the photo picker is the same wrong turn this
                // release removes from the button above.
                else -> { vm.reset(); if (lastWasCamera) takePhoto() else pickDocument() }
            }
        },
        onUseRooms = onUseRooms,
        // The correction rewrites the outcome held in the ViewModel, so what `onUseRooms` hands to
        // the guided grid is already the corrected reading — nothing downstream needs to know.
        onCorrectRoom = vm::correctRoom,
        onDrawInstead = onDrawInstead,
        onBack = onBack,
        // ⛔ NOTHING ON THIS SCREEN NAMES A MODEL any more (owner, 11 Aug 2026). The reader is picked
        // in Settings, where a named pick also survives a retry.
        // The 2D gate's escape hatch — no scan, no network, just the reading we already had.
        onReadAnyway = vm::readAnyway,
        north = north,
    )
}
