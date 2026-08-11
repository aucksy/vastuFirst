package com.vastufirst.app.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

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
) {
    val context = LocalContext.current
    // Which way in the user last chose, so "try a different picture" reopens the same one.
    var lastWasCamera by rememberSaveable { mutableStateOf(false) }
    // ⚠ SAVEABLE, and that is the whole point. The camera app is a separate app in the foreground,
    // which is exactly when Android reclaims ours — so this must survive process death or the photo
    // comes back with nowhere to be read from. Stored as a String because Uri is not Saveable.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    // Set when the phone has no camera app at all (an emulator, a stripped ROM). The screen then
    // says so in one line rather than the button doing nothing — the defect this release fixes.
    var cameraUnavailable by rememberSaveable { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> vm.scan(uri) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        // `saved` is false when the user backed out of the camera without keeping the shot. That is
        // a decision, not a failure: return to the ask, and never read a zero-byte file.
        val uri = pendingPhoto?.let(Uri::parse)
        pendingPhoto = null
        vm.scan(if (saved) uri else null)
    }

    val pickDocument = remember(documentPicker) {
        {
            lastWasCamera = false
            // PDF first in the list, because it is the input that reads best — and `image/*` is why
            // this one button still covers "a picture I already have" now that the second button is
            // the camera. There is no third button: two clear choices beat three on this screen.
            documentPicker.launch(arrayOf("application/pdf", "image/*"))
        }
    }
    val takePhoto = remember(camera, context) {
        {
            lastWasCamera = true
            // Total on purpose: a phone with no camera app throws on launch, and a scan screen that
            // crashes is worse than one that says "use a picture instead".
            val started = runCatching {
                val uri = newPlanPhotoUri(context)
                pendingPhoto = uri.toString()
                camera.launch(uri)
            }.isSuccess
            if (!started) { pendingPhoto = null; cameraUnavailable = true }
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
        // in Settings, where a named pick also survives a retry — see the note above `RoomsBody`.
        // The 2D gate's escape hatch — no scan, no network, just the reading we already had.
        onReadAnyway = vm::readAnyway,
    )
}

/**
 * A fresh, empty file for the camera app to write one plan photo into, as a URI it is allowed to
 * open. Named by the clock so a second shot never lands on top of the first while the first is still
 * being read; the folder lives in the cache, so the phone may clear it whenever it likes and the
 * picture never enters the user's gallery.
 */
private fun newPlanPhotoUri(context: android.content.Context): Uri {
    val dir = planPhotoDir(context).apply { mkdirs() }
    // ⭐ EVERY EARLIER SHOT GOES FIRST, so at most one plan photograph is ever on the phone.
    // Without this the folder only grew: three plans photographed meant three pictures of somebody's
    // home sitting in the cache with nothing in the app able to remove them. The one that remains is
    // the one being read, and "Delete all my data" now takes that too — see [clearPlanPhotos].
    clearPlanPhotos(context)
    val file = File(dir, "plan-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Where the camera door writes a plan photograph. Cache, so the OS may clear it whenever it likes. */
internal fun planPhotoDir(context: android.content.Context): File =
    File(context.cacheDir, "plan-photos")

/**
 * ⭐ Remove every plan photograph this app's camera has written.
 *
 * ⚠ Called by "Delete all my data", which promises "this permanently removes every saved home from
 * this device". It emptied the two databases and left the photographs behind — so somebody handing
 * their phone on, having been told everything was gone, still had a picture of their own floor plan
 * on it. The databases were never the only copy.
 *
 * Total on purpose: a file that cannot be deleted must not take the rest of the wipe down with it.
 */
internal fun clearPlanPhotos(context: android.content.Context) {
    runCatching { planPhotoDir(context).listFiles()?.forEach { it.delete() } }
}
