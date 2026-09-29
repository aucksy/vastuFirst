package com.vastufirst.app.render

import com.vastufirst.shared.scan.RecordedScans
import com.vastufirst.shared.scan.ScanBox
import com.vastufirst.shared.scan.ScanMapper
import com.vastufirst.shared.scan.ScanOutcome
import java.io.File

/**
 * ⭐ THE ONE REAL PLAN PICTURE THE REPOSITORY CARRIES, for the goldens that must show a label sitting
 * ON the room it names (the direction trial, 29 Sep 2026).
 *
 * Every other photograph in the harness is a flat beige stand-in: honest for geometry, useless for the
 * question "is the direction written on the kitchen?". `tools/scan-eval/fixtures/plan-01.png` is a
 * synthetic sheet, safe to publish, whose recorded reply ([RecordedScans.CLEAN]) is bundled with the app.
 *
 * ⚠ Decoded on the JVM (ImageIO) and handed to Android as pixels, never through BitmapFactory: no other
 * test in this suite decodes a file under Robolectric, and a golden that depends on an untried decoder
 * is a red build waiting to happen for a reason that has nothing to do with the screen.
 *
 * ⚠ The reply predates the building box (prompt v1) and measures its rooms in fractions of the
 * BUILDING. The sheet draws that building at x 50–950 and y 120–1320 of its 1000 × 1400 page, so the
 * box is supplied here exactly as prompt v4+ replies supply it themselves. Without it the rooms would
 * be composed onto the whole page, margins included, and every label would sit off its room.
 */
internal object PlanSheet {
    private const val PATH = "../tools/scan-eval/fixtures/plan-01.png"
    private const val W = 1000
    private const val H = 1400
    private val building = ScanBox(x = 50.0 / W, y = 120.0 / H, w = 900.0 / W, h = 1200.0 / H)

    fun bitmap(): android.graphics.Bitmap {
        val file = File(PATH)
        check(file.exists()) { "The plan picture these goldens draw is missing: ${file.absolutePath}" }
        val img = javax.imageio.ImageIO.read(file) ?: error("plan-01.png did not decode: ${file.absolutePath}")
        check(img.width == W && img.height == H) { "plan-01.png is ${img.width}×${img.height}, not $W×$H — the building box below is wrong for it" }
        val px = IntArray(img.width * img.height)
        img.getRGB(0, 0, img.width, img.height, px, 0, img.width)
        return android.graphics.Bitmap.createBitmap(px, img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
    }

    fun outcome(): ScanOutcome.Placed =
        ScanMapper.map(
            RecordedScans.load(RecordedScans.CLEAN)!!.reply.copy(building = building),
            imageAspect = W.toDouble() / H,
        ) as ScanOutcome.Placed
}
