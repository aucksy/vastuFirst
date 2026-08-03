package com.vastufirst.app.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * The scan screen wired to the platform's file pickers.
 *
 * ⭐ **`PickVisualMedia` needs no runtime permission** — the Photo Picker is a system UI that hands
 * back one item the user chose, so the app never asks for gallery access at all. It works back to
 * API 26 through the AndroidX backport, and it is strictly better than a permission prompt for a
 * feature whose whole problem is asking someone to trust us with their home's layout.
 *
 * PDFs come through `OpenDocument` instead, because the Photo Picker only offers images and video —
 * and a PDF is the input we most want, since skew is what ruins a read and a PDF has none.
 *
 * ⭐⭐ **"Take a photo of it now" opens the CAMERA** (v0.6.6). It used to open the gallery: the same
 * picker as the button above it, wearing a different label — so someone holding a printed plan had
 * no way to photograph it from inside the app, and the button looked broken because it was. The
 * capture goes to the phone's own camera app, which needs no permission from us (see the manifest),
 * writes into one cache folder we grant it for the length of one shot, and never touches the user's
 * gallery.
 */
@Composable
fun ScanRoute(
    vm: ScanViewModel,
    onUseRooms: (com.vastufirst.shared.scan.ScanOutcome) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // Which picker the user last chose, so "try a different picture" reopens the same one.
    var lastWasPhoto by rememberSaveable { mutableStateOf(false) }
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

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
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
            lastWasPhoto = false
            // PDF first in the list, because it is the input that reads best.
            documentPicker.launch(arrayOf("application/pdf", "image/*"))
        }
    }
    val pickPhoto = remember(photoPicker) {
        {
            lastWasPhoto = true
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
    val takePhoto = remember(camera, context) {
        {
            lastWasPhoto = true
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

    ScanScreen(
        state = vm.state,
        onPickImage = pickDocument,
        onTakePhoto = takePhoto,
        cameraUnavailable = cameraUnavailable,
        // "Try again" means retry the same file when the reader was busy or offline (that is the
        // fix), and choose a new file when the plan itself was the problem.
        onRetry = {
            when (vm.state) {
                is ScanUiState.Busy, ScanUiState.Unavailable -> vm.retrySameImage()
                // ⚠ A retry after a camera shot reopens the CAMERA, not the gallery. Sending someone
                // who photographed their plan to the photo picker is the same wrong turn this
                // release removes from the button above.
                else -> { vm.reset(); if (lastWasPhoto) takePhoto() else pickDocument() }
            }
        },
        onUseRooms = onUseRooms,
        // The correction rewrites the outcome held in the ViewModel, so what `onUseRooms` hands to
        // the guided grid is already the corrected reading — nothing downstream needs to know.
        onCorrectRoom = vm::correctRoom,
        onDrawInstead = onDrawInstead,
        onBack = onBack,
    )
}

/**
 * A fresh, empty file for the camera app to write one plan photo into, as a URI it is allowed to
 * open. Named by the clock so a second shot never lands on top of the first while the first is still
 * being read; the folder lives in the cache, so the phone may clear it whenever it likes and the
 * picture never enters the user's gallery.
 */
private fun newPlanPhotoUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "plan-photos").apply { mkdirs() }
    val file = File(dir, "plan-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
