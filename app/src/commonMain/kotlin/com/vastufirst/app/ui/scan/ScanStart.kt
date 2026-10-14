package com.vastufirst.app.ui.scan

import androidx.compose.runtime.Composable

/** Which door "Add a home" opened this screen through — the picker or the camera. */
enum class ScanStart { PICK, CAMERA }

/**
 * The scan screen with the phone's own camera and picker attached. Android: its camera and file
 * hand-offs (androidMain ScanRoute.kt). iPhone: its own pickers (iosMain).
 */
@Composable
expect fun ScanRoute(
    vm: ScanViewModel,
    onUseRooms: (com.vastufirst.shared.scan.ScanOutcome) -> Unit,
    onDrawInstead: () -> Unit,
    onBack: () -> Unit,
    /**
     * ⭐⭐ OPEN THE PICKER STRAIGHT AWAY (30 Sep 2026). "Upload a plan" on Add a home used to open an
     * upload screen that asked the same question again — PDF or photo — with a card of tips and a
     * second offer to draw on the grid. Now the card's own tap opens the phone's picker (or camera)
     * the moment this screen appears, and the reader's next sight is "Reading your plan…". The upload
     * screen stays as the fallback for a cancelled retry and a phone with no camera app.
     *
     * Null opens on the upload screen as before — nothing in the app does that now, but it keeps this
     * route honest if a new door into it forgets to say which picker it means.
     */
    startWith: ScanStart? = null,
    /** North, set on the result itself for a placed read — see [NorthOnResult]. */
    north: NorthOnResult = NorthOnResult(),
)
