package com.vastufirst.shared.scan

import kotlin.io.encoding.Base64

/**
 * ⭐⭐ "TRY A SAMPLE PLAN" IS A SCANNED PLAN (owner, 15 Oct 2026: option B).
 *
 * The sample used to be a home drawn on the grid, so the first thing a new user tried showed drawn room
 * boxes on the North dial and the report — the picture a scanned home never shows. It is now the
 * repository's synthetic sample sheet, `plan-01` (safe to publish: it is nobody's home), with the reply
 * the reader gave for it ([RecordedScans.CLEAN]), so the sample walks the main journey on a real plan
 * photograph. No read is made: the picture and the reply are both bundled, so it costs nothing and
 * works offline and in a build with no reader at all.
 *
 * The picture is stored as base64 text because the shared code bundles text resources onto the iPhone
 * (BundleTextTask) — one copy, read the same way on both phones.
 */
object SampleScan {
    /** The sheet's pixel size — what [BUILDING] and the door point below are measured on. */
    const val WIDTH = 1000
    const val HEIGHT = 1400

    /**
     * Where the building sits on the sheet: x 50–950, y 120–1320. The recorded reply predates the
     * building box (prompt v1), so it is supplied here exactly as prompt v4+ replies supply it — the
     * same box the screen goldens use (PlanSheet).
     */
    val BUILDING = ScanBox(x = 50.0 / WIDTH, y = 120.0 / HEIGHT, w = 900.0 / WIDTH, h = 1200.0 / HEIGHT)

    /**
     * ⭐ The front door, on the page: the gap in the bottom wall above "MAIN ENTRANCE" — measured off
     * the sheet's pixels (wall gap x 349–440 on row 1320). The reader does not read that caption as a
     * room, so a scan of this sheet would ask for the door; the sample sets it where the sheet draws
     * it, so trying the sample stays two taps.
     */
    const val DOOR_PAGE_X = 0.395f
    const val DOOR_PAGE_Y = 0.943f

    private const val PICTURE = "/scan/sample-plan-01.png.b64.txt"

    /** The sample sheet's picture (PNG bytes), or null if it is missing from the build. */
    fun pictureBytes(): ByteArray? {
        val text = bundledText(PICTURE) ?: return null
        return runCatching { Base64.Default.decode(text.filterNot { it.isWhitespace() }) }.getOrNull()
    }

    /** The sample sheet as the reader read it, placed by the real mapper — or null if it no longer places. */
    fun outcome(): ScanOutcome.Placed? {
        val reply = RecordedScans.load(RecordedScans.CLEAN)?.reply ?: return null
        return ScanMapper.map(reply.copy(building = BUILDING), imageAspect = WIDTH.toDouble() / HEIGHT)
            as? ScanOutcome.Placed
    }
}
