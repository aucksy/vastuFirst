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
 */
@Composable
fun ScanRoute(
    vm: ScanViewModel,
    onUseRooms: (com.vastufirst.shared.scan.ScanOutcome) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
) {
    // Which picker the user last chose, so "try a different picture" reopens the same one.
    var lastWasPhoto by rememberSaveable { mutableStateOf(false) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> vm.scan(uri) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> vm.scan(uri) }

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

    ScanScreen(
        state = vm.state,
        onPickImage = pickDocument,
        onTakePhoto = pickPhoto,
        // "Try again" means retry the same file when the reader was busy or offline (that is the
        // fix), and choose a new file when the plan itself was the problem.
        onRetry = {
            when (vm.state) {
                is ScanUiState.Busy, ScanUiState.Unavailable -> vm.retrySameImage()
                else -> { vm.reset(); if (lastWasPhoto) pickPhoto() else pickDocument() }
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
