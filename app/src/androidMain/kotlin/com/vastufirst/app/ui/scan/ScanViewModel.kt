package com.vastufirst.app.ui.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vastufirst.shared.RoomType
import com.vastufirst.shared.scan.PlanReader
import com.vastufirst.shared.scan.ScanOutcome
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
    /**
     * ⭐ The model ids a tester may pick from — the primary and the second-opinion model out of
     * `reader-config.json`, in that order (owner request, 4 Aug 2026: compare the readers from the
     * phone). Empty hides every picker and keeps the shipping behaviour exactly as before.
     */
    val modelChoices: List<String> = emptyList(),
) : ViewModel() {

    var state by mutableStateOf<ScanUiState>(if (canRead) ScanUiState.Idle else ScanUiState.NotConfigured)
        private set

    /**
     * The model the next scan must use, or null for Auto — the shipping primary-plus-second-opinion
     * path. An explicit pick reads with that one model only, deterministically.
     */
    var chosenModel by mutableStateOf<String?>(null)
        private set

    fun chooseModel(model: String?) {
        chosenModel = if (model != null && model in modelChoices) model else null
    }

    private var lastSource: Any? = null

    /**
     * ⭐ The decoded picture the reply was read FROM, kept for the on-photo review screen — which
     * shows the user their own plan with our reading tinted over it. Never persisted, never logged
     * (same privacy rule as the reader); it lives exactly as long as this one-shot ViewModel.
     */
    var lastImage: DecodedImage? = null
        private set

    /** Read the picked file. [source] is whatever the platform picker handed back (a Uri). */
    fun scan(source: Any?) {
        if (!canRead) { state = ScanUiState.NotConfigured; return }
        lastSource = source
        if (source == null) { state = ScanUiState.Idle; return }
        state = ScanUiState.Reading
        viewModelScope.launch {
            val image = decode.toJpeg(source)
            if (image == null) { state = ScanUiState.BadImage; return@launch }
            lastImage = image
            state = readToState(image)
        }
    }

    /**
     * ⭐ Same picture, a named model (owner's testing lever, 4 Aug 2026) — offered on the results
     * screen so two readers can be compared on the identical bytes. Reuses the decoded image; no
     * re-pick, no re-decode. Each call is one more paid read of the same photo, which the button's
     * own caption says.
     */
    fun rescanWith(model: String) {
        val image = lastImage ?: return
        if (!canRead || model !in modelChoices) return
        state = ScanUiState.Reading
        viewModelScope.launch { state = readToState(image, forcedModel = model) }
    }

    private suspend fun readToState(image: DecodedImage, forcedModel: String? = null): ScanUiState {
        val pick = forcedModel ?: chosenModel
        val r = if (pick != null) reader.readWith(pick, image.bytes, image.aspect)
        else reader.read(image.bytes, image.aspect)
        return when (r) {
            is ScanResult.Read -> ScanUiState.Done(r.outcome, readBy = r.readBy ?: pick)
            is ScanResult.Busy -> ScanUiState.Busy(r.retryAfterSeconds)
            ScanResult.Unavailable -> ScanUiState.Unavailable
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
        // copy() keeps readBy: a correction changes what the user says, not which model read it.
        if (corrected !== done.outcome) state = done.copy(outcome = corrected)
    }

    /**
     * ⭐ "Show me what you read" — take the refusal's own alternative reading and carry on with it.
     *
     * Costs NO second scan: the alternative was computed by the mapper from the very same reply and
     * has been sitting on the refusal ever since. It lands on the ordinary result screen, so the
     * room-by-room confirmation still stands between it and any score — the user is being shown a
     * reading to judge, not being given a result.
     */
    fun readAnyway() {
        val done = state as? ScanUiState.Done ?: return
        val refused = done.outcome as? ScanOutcome.Refused ?: return
        val alternative = refused.ifRead ?: return
        state = done.copy(outcome = alternative)
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
