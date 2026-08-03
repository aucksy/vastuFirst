package com.vastufirst.app.ui.scan

import android.content.Context

/**
 * Which surface confirms a scan: the classic guided grid, or the on-photo review (owner request,
 * 4 Aug 2026 — "the scanned image is always visible, they scroll through the list of extracted
 * rooms"). A Settings toggle, because the two are being COMPARED, not replaced: the grid remains
 * the only surface that can FIX a wrong read, so the photo flow hands over to it on demand and
 * whenever a scan arrives without trustworthy geometry.
 *
 * Same shape as [PlanReadingConsent] and for the same reason: an interface with an in-memory
 * double, so the render harness and tests never touch SharedPreferences.
 */
interface ScanReviewStyle {
    fun onPhoto(): Boolean
    fun set(onPhoto: Boolean)
}

class AndroidScanReviewStyle(context: Context) : ScanReviewStyle {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun onPhoto(): Boolean = prefs.getBoolean(KEY, false)

    override fun set(onPhoto: Boolean) {
        prefs.edit().putBoolean(KEY, onPhoto).apply()
    }

    private companion object {
        const val FILE = "vastufirst_ui"

        /** Versioned like the consent key: if what the toggle means changes, the key changes. */
        const val KEY = "scan_review_on_photo_v1"
    }
}

/** The toggle held in memory only — for the render harness and tests. */
class InMemoryScanReviewStyle(private var onPhoto: Boolean = false) : ScanReviewStyle {
    override fun onPhoto(): Boolean = onPhoto
    override fun set(onPhoto: Boolean) { this.onPhoto = onPhoto }
}
