package com.vastufirst.app.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android's plan pickers: the system document picker (PDF or picture) and the camera app, writing into
 * a file of ours. The same launchers the scan route has always used (iPhone plan, stage 4 moved the
 * route itself into shared code).
 */
@Composable
actual fun rememberPlanPickers(onPicked: (Any?) -> Unit): PlanPickers {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onPicked)
    // ⚠ SAVEABLE, and that is the whole point. The camera app is a separate app in the foreground,
    // which is exactly when Android reclaims ours — so this must survive process death or the photo
    // comes back with nowhere to be read from. Stored as a String because Uri is not Saveable.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> deliver(uri) }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved: Boolean ->
        val uri = pendingPhoto?.let(Uri::parse)
        pendingPhoto = null
        deliver(if (saved) uri else null)
    }

    return remember(documentPicker, camera, context) {
        object : PlanPickers {
            override fun pickDocument() = documentPicker.launch(arrayOf("application/pdf", "image/*"))

            // A phone with no camera app at all (an emulator, a stripped ROM) throws here; the screen
            // then says so in one line rather than the button doing nothing.
            override fun takePhoto(): Boolean = runCatching {
                val uri = newPlanPhotoUri(context)
                pendingPhoto = uri.toString()
                camera.launch(uri)
            }.onFailure { pendingPhoto = null }.isSuccess
        }
    }
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
