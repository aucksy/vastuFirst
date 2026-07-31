package com.vastufirst.app.ui.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.ScanResult
import com.vastufirst.shared.scan.withRoomType
import kotlinx.coroutines.launch

/**
 * Drives one scan. Holds the screen's state and nothing else — the reading itself is behind
 * [PlanReader], and every decision about what the reply *means* is in the pure `ScanMapper`.
 *
 * Not part of the guided-grid graph's shared ViewModel: a scan is a one-shot action with its own
 * lifetime, and keeping it separate means the editor's ViewModel is untouched by this feature.
 */
class ScanViewModel(
    private val reader: PlanReader,
    private val decode: ImageDecoder,
    /**
     * False when the build carries no plan-reading key. The screen then says so plainly instead of
     * offering a picker that cannot work — see [ScanUiState.NotConfigured] for why this is a state
     * and not a silent fallback to recorded replies.
     */
    private val canRead: Boolean = true,
) : ViewModel() {

    var state by mutableStateOf<ScanUiState>(if (canRead) ScanUiState.Idle else ScanUiState.NotConfigured)
        private set

    private var lastSource: Any? = null

    /** Read the picked file. [source] is whatever the platform picker handed back (a Uri). */
    fun scan(source: Any?) {
        if (!canRead) { state = ScanUiState.NotConfigured; return }
        lastSource = source
        if (source == null) { state = ScanUiState.Idle; return }
        state = ScanUiState.Reading
        viewModelScope.launch {
            val image = decode.toJpeg(source)
            if (image == null) { state = ScanUiState.BadImage; return@launch }
            state = when (val r = reader.read(image.bytes, image.aspect)) {
                is ScanResult.Read -> ScanUiState.Done(r.outcome)
                is ScanResult.Busy -> ScanUiState.Busy(r.retryAfterSeconds)
                ScanResult.Unavailable -> ScanUiState.Unavailable
            }
        }
    }

    /**
     * The user overrules what we read room [index] as — §6.2b's "confirm **or correct** each one".
     *
     * ⭐ It rewrites the OUTCOME rather than keeping a separate table of overrides beside it. The
     * outcome is what the confirmation screen draws and what is handed to the guided grid, so
     * correcting it in place means there is no second copy to keep in step and the handover needs no
     * knowledge of corrections at all. The rewrite itself is the pure [withRoomType], which returns
     * the same instance when nothing changed — hence the identity check, so re-picking the kind a
     * room already is does not count as an edit.
     */
    fun correctRoom(index: Int, type: RoomType) {
        val done = state as? ScanUiState.Done ?: return
        val corrected = done.outcome.withRoomType(index, type)
        if (corrected !== done.outcome) state = ScanUiState.Done(corrected)
    }

    /** Back to the ask, so the user can choose a different file. */
    fun reset() { state = if (canRead) ScanUiState.Idle else ScanUiState.NotConfigured }

    /** Same file, another go — for the rate-limited and offline states, where retrying is the fix. */
    fun retrySameImage() { scan(lastSource) }
}

/** A decoded, downscaled, JPEG-encoded image plus its width ÷ height. */
data class DecodedImage(val bytes: ByteArray, val aspect: Double?) {
    // ByteArray gives reference equality in a data class, which is a well-known trap; these two are
    // spelled out so an accidental == comparison compares content.
    override fun equals(other: Any?): Boolean =
        this === other || (other is DecodedImage && bytes.contentEquals(other.bytes) && aspect == other.aspect)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + (aspect?.hashCode() ?: 0)
}

/**
 * Turns whatever the picker returned into bytes we can send.
 *
 * ⚠ This is the seam for step 4 of the build plan: `PdfRenderer` (in the platform since API 21, no
 * dependency) for PDFs, `BitmapFactory` for images, then **downscale to ~1400 px and JPEG-encode
 * before upload** — which is a COST control, not just a bandwidth one, because a scan is billed per
 * token and an image is tokenised into the same stream.
 */
interface ImageDecoder {
    suspend fun toJpeg(source: Any): DecodedImage?
}
