// ScanTypes.kt — the data contract for "scan your plan" (Product PRD §6.2b).
//
// WHY THESE TYPES LOOK LIKE THIS — docs/SCAN-PLAN-READING-PLAN.md §3i, measured not assumed:
//
//   the model reads TEXT · OUR code does GEOMETRY and everything Vastu · the USER confirms
//
// Three safety rules are baked into the shape of this file, each bought with a measurement:
//
//   S1  The model is never asked a Vastu-shaped question and no Vastu vocabulary reaches it.
//       Asked for a room's *sector* it returned byte-identical answers for a clean render and a
//       badly skewed photo (it wasn't looking), and its errors moved toward doctrine
//       (MASTER BEDROOM→SW, KITCHEN→E — the textbook positions, not the drawing's). A reader that
//       nudges homes toward canonical placement inflates every score. So there is no Zone, no
//       direction and no verdict anywhere in [ScanDraft]: only labels and rectangles.
//   S2  [ScanDraft.planConfidence] is RECORDED AND NEVER GATED ON. It was 0.95 on a 100 % read,
//       on a 25 % read, and on fifteen entirely fabricated rectangles. The gates are objective
//       (coverage, area variance) and live in ScanMapper.
//   S3  There is no door in this file. Door detection benchmarks at 39 % and the front door is the
//       highest-weighted single input the engine scores; the user taps it via `doorForTap`.
//
// Zero Android, zero network — `scripts/check-boundaries.sh` enforces the first and the
// [PlanReader] seam keeps the second on the far side of an interface.
package com.vastufirst.shared.scan

import com.vastufirst.shared.RoomType
import com.vastufirst.shared.editor.CellRect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * L0 triage: what kind of image this actually is. **One upload in five is not a usable plan** —
 * of 30 real plans the owner curated, 24 were 2D plans, 5 were 3D marketing renders and 1 was not a
 * plan at all (§3h). A 3D render does not fail loudly; it yields plausible-looking rectangles that
 * are simply wrong, which is the worst failure mode for a paid score. So it is refused, not read.
 */
@Serializable
enum class PlanImageType {
    @SerialName("2D_PLAN") TWO_D_PLAN,
    @SerialName("3D_RENDER") THREE_D_RENDER,
    @SerialName("NOT_A_PLAN") NOT_A_PLAN,

    /** The reply predates the triage prompt (the §3e fixtures). Treated as "not stated", never as 3D. */
    @SerialName("UNKNOWN") UNKNOWN,
}

/**
 * One rectangle as the model returned it: **normalised** `0..1` against the building's outer wall,
 * origin top-left, y growing downward (image convention, matching the grid editor's rows).
 *
 * Normalised because it means the model never has to know the app's grid — and it makes everything
 * downstream a pure function of numbers in a unit square.
 *
 * [confidence] is the model's per-room self-report. It is used ONLY to decide which of two
 * overlapping rooms gives way (a relative comparison inside one reply), never as a quality gate — see
 * S2 above.
 */
@Serializable
data class ScanBox(
    val label: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
    val confidence: Double = 0.0,
    /**
     * ⭐⭐ THE SIZE THE PLAN PRINTS BESIDE THIS ROOM, copied verbatim — `3.72m X 4.50m ( 12'-2" x
     * 14'-9" )`. Empty when the drawing prints none, which is a real and common case.
     *
     * ⚠ It sits LAST despite belonging beside [label], and that is deliberate: every recorded
     * fixture and pinned test in this repo builds a `ScanBox` positionally, and inserting a String
     * in the middle would have silently rewritten what a dozen of them mean. A trailing parameter
     * with a default cannot.
     *
     * ⚠ This field exists because of the owner's own flat. The reader returned fifteen rectangles
     * with only three distinct sizes between them, every coordinate on a tidy 0.05 lattice, and
     * three rooms sitting off the bottom of the page — a plausible-looking template, not a
     * measurement. Half the real corpus comes back the same way. **The rectangles are an
     * arrangement, never a measurement, and no wording makes them one**: a prompt that explicitly
     * forbids round coordinates was tried and changed nothing except pushing more boxes off the page.
     *
     * What DOES work is asking for the printed text, because reading text is the one thing this
     * model does at ~95 %. His sheet prints a size under all fifteen captions and we were capturing
     * **none of them**; asked for it directly the reader returned **all fifteen**, thirteen matching
     * the sheet exactly. Across nine real plans: 116 of 129 rooms — and on the one plan that prints
     * no sizes at all it correctly returned none rather than inventing them.
     *
     * Kept SEPARATE from [label] rather than appended to it: the confirmation screen shows the label
     * to the user ("we read 'MASTER BEDROOM' as a Master bedroom"), and a caption with two
     * measurement systems bolted on is not something to put in front of someone. Older recorded
     * replies carry the dimensions inside the label instead, and [RoomDimensions.of] reads both.
     */
    @SerialName("size") val printedSize: String = "",
)

/**
 * A whole model reply, normalised. Field names match the recorded JSON in `tools/scan-eval/out/`
 * verbatim so the fixtures are the actual measured replies rather than a re-typing of them.
 *
 * Every field has a default: a reply that omits one is a real, observed case (the §3e fixtures
 * predate the triage prompt), and a missing field must degrade rather than throw.
 */
@Serializable
data class ScanDraft(
    val planType: PlanImageType = PlanImageType.UNKNOWN,
    val hasRoomLabels: Boolean = true,
    val northMarked: Boolean = false,
    val rooms: List<ScanBox> = emptyList(),
    /** ⚠ RECORDED FOR DIAGNOSTICS ONLY. Never gate on this — S2. */
    val planConfidence: Double = 0.0,
    val unreadable: Boolean = false,
)

/** Why a space the model reported did not become a room. Always surfaced, never silent. */
@Serializable
enum class DropReason {
    /** A rectangle wholly inside another room — a dressing area, a walk-in closet. §3j D1. */
    SUB_AREA,

    /** Named on the drop list: dressing areas, ducts, shafts, lifts. Not habitable, not scored. */
    NOT_HABITABLE,

    /** No synonym matched. Not guessed — a wrong room type moves a paid score. */
    UNKNOWN_LABEL,

    /** Rounded to zero cells on the grid. */
    DEGENERATE,

    /** NaN / inverted / entirely outside the unit square. */
    INVALID_GEOMETRY,

    /** Overlapped a higher-confidence room and no trim left it at least one cell. */
    OVERLAP_UNRESOLVABLE,
}

/** A space the model read that did not survive to the grid, with the reason, for the UI to show. */
data class DroppedSpace(val label: String, val reason: DropReason)

/** Per-room caveats the user should see on the confirmation screen. */
enum class RoomFlag {
    /** Overlapped a more-confident room and was cut back. The user should check its size. */
    OVERLAP_TRIMMED,

    /** Stuck out of the unit square and was clamped back in. */
    OUT_OF_BOUNDS_CLAMPED,

    /** The label matched a synonym only loosely (a substring, not the whole name). */
    LOOSE_LABEL_MATCH,

    /** The model's own per-room confidence was low. Advisory only — never a gate (S2). */
    LOW_MODEL_CONFIDENCE,
}

/**
 * One room that survived to the user. [rect] is `null` in [ScanOutcome.Assisted] — the room was
 * identified but not placed, which is the **primary real-world path** (§3h): across 30 real plans the
 * model named rooms excellently and positioned them badly, three independent ways.
 *
 * [label] is kept verbatim as printed on the plan so the confirmation screen can say
 * *"we read 'ATT. TOILET 1350X2250' as a Toilet"* — the user checks our reading, not just our answer.
 */
data class ScannedRoom(
    val type: RoomType,
    val label: String,
    val rect: CellRect?,
    val flags: Set<RoomFlag> = emptySet(),
    /**
     * ⭐ The size the plan printed for this room, verbatim — carried all the way to the confirmation
     * screen so the user can check it against their own paper.
     *
     * ⚠ This field exists to undo a regression introduced by the field that produced it. The size
     * used to arrive inside [label] (`ATT. TOILET 1350X2250`), so the screen showed it for free.
     * Moving it into its own field on the wire made the labels clean — and made the numbers we now
     * shape every room from **invisible to the person confirming them**. Since those numbers decide
     * which way round a room is drawn, and therefore which Vastu direction it lands in, they are
     * exactly what §6.2b means by checking our reading rather than just our answer.
     */
    val printedSize: String = "",
)

/** Why the geometry was thrown away and the rooms handed over unplaced. */
enum class AssistReason {
    /**
     * ⭐ Too many rooms for the model to keep track of, on a plan that prints no sizes for us to
     * measure from. Measured by drawing its rectangles back over real plans and looking: 10–11 rooms
     * placed well, 15–24 placed badly.
     *
     * ⚠ No longer the first thing checked — see [ScanMapper.SIZED_SHARE_TO_TRUST]. Counting rooms
     * threw out the owner's own fifteen-space flat, which is an ordinary Indian apartment.
     */
    TOO_MANY_ROOMS,

    /**
     * ⭐ The sheet names a lift, so it shows a whole floor of a building rather than one home. The
     * rooms are still worth handing over; where they sit on the sheet is not.
     */
    FLOOR_PLATE,

    /** Every rectangle came back the same size — invented, not measured (§3j D2). */
    UNIFORM_BOXES,

    /** Too few rooms survived snapping to be worth placing for the user. */
    TOO_FEW_PLACED,
}

/** Why nothing could be read at all. Each maps to a message that tells the user what to fix. */
enum class RefusalReason {
    /** A 3D marketing render. 5 of the 30 real plans (§3h). */
    NOT_2D,

    /** An elevation, a brochure page, a photo of something else. */
    NOT_A_PLAN,

    /** No readable room names. The owner made a labelled plan a precondition (§3g). */
    NO_LABELS,

    /** A sheet with several homes on it (UNIT-1 … UNIT-4). Seen in the corpus. */
    MULTI_UNIT,

    /** It is a labelled 2D plan, but nothing came back that maps to a room. */
    NO_ROOMS,
}

/**
 * The objective measurements behind the decision — kept on every outcome so a support question
 * ("why did it do that?") is answerable from the data rather than from a guess.
 */
data class ScanNotes(
    /**
     * Fraction of the unit square covered by the union of the model's rectangles.
     *
     * ⚠ **Diagnostic only — this decides nothing.** It was the Placed/Assisted gate until the
     * rectangles were drawn back over real plans and looked at, which showed it is non-monotonic
     * with placement quality. Kept because it is useful when answering "why did it do that?".
     * See [ScanMapper.MAX_TRUSTED_ROOMS].
     */
    val coverage: Double,
    /** stdev(area) ÷ mean(area) over those rectangles. Near zero means the geometry was invented. */
    val areaVariation: Double,
    /** The model's own self-report. Diagnostics only — S2. */
    val modelConfidence: Double,
    val dropped: List<DroppedSpace> = emptyList(),
)

/**
 * What a scan produced. Three outcomes, and **every branch ends somewhere useful** — nothing
 * dead-ends and nothing lies. The worst case is the guided grid that already exists and has been
 * fuzzed across 400 000 sequences.
 */
sealed interface ScanOutcome {
    val notes: ScanNotes

    /**
     * Rooms identified **and** positioned. The user confirms and corrects them on the guided grid,
     * exactly as §6.2b requires — there is no automatic path to a score.
     */
    data class Placed(
        val cols: Int,
        val rows: Int,
        val rooms: List<ScannedRoom>,
        override val notes: ScanNotes,
    ) : ScanOutcome

    /**
     * Rooms identified, geometry discarded. **This is the expected outcome for most real plans**, and
     * it is a real saving rather than a failure: it removes the two slowest parts of the current flow
     * — working out the room list, and hunting each type out of the palette — and keeps the part the
     * model cannot do with the person who knows the answer.
     */
    data class Assisted(
        val rooms: List<ScannedRoom>,
        val reason: AssistReason,
        override val notes: ScanNotes,
    ) : ScanOutcome

    /** The image cannot be used. [reason] tells the user precisely what to change. */
    data class Refused(
        val reason: RefusalReason,
        override val notes: ScanNotes,
    ) : ScanOutcome
}

/**
 * What a read attempt produced. Separates *what the plan says* ([ScanOutcome]) from *whether we
 * could ask at all* — because those are different things to the user and only one of them is worth
 * trying again.
 */
sealed interface ScanResult {
    /** We got an answer. It may still be a refusal — that is a property of the plan, not of the call. */
    data class Read(val outcome: ScanOutcome) : ScanResult

    /**
     * ⭐ HTTP 429 — and a **first-class state, not an error**. The free tier allows 8 000 tokens a
     * minute, so at ~2 630 tokens a scan that is roughly three scans a minute **across all users**:
     * two people scanning at once will throttle each other. The screen says a calm "we're reading a
     * lot of plans right now, try again in a minute" and offers the guided grid alongside, so nobody
     * is ever stuck. [retryAfterSeconds] comes from the real `x-ratelimit-reset-tokens` header when
     * present, so the wait can state a number instead of guessing.
     */
    data class Busy(val retryAfterSeconds: Int?) : ScanResult

    /** No network, the service is down, or the pinned model was retired. Falls back to the grid. */
    data object Unavailable : ScanResult
}

/**
 * The one seam between "read the picture" and everything else.
 *
 * Implementations: `FakePlanReader` (recorded replies — needs no key, no network, and is what CI
 * runs) and, later, `GroqPlanReader` (one HTTP POST, model id from config). Everything to the right
 * of this interface is pure Kotlin with no Android and no network, which is where essentially all
 * the correctness risk lives — so it is built and proven green in CI before any account exists.
 */
interface PlanReader {
    /** [image] is the encoded bytes of a downscaled JPEG. Never throws — failures are [ScanResult]s. */
    suspend fun read(image: ByteArray, imageAspect: Double?): ScanResult
}
