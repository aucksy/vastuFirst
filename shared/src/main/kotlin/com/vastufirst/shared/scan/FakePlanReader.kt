// FakePlanReader.kt — a [PlanReader] that replays REAL recorded model replies.
//
// WHY: everything to the right of [PlanReader] is where the correctness risk lives, and none of it
// needs a key, a network or an account. These fixtures are the actual JSON the Groq API returned on
// 2026-07-29, copied verbatim out of `tools/scan-eval/out/` (token usage and all) rather than
// hand-written to look plausible — so CI exercises the mapper against what the model really says,
// at zero cost, on every push.
//
// It is also what the scan screens run against until the real reader lands, so the whole flow can be
// rendered and reviewed before a single paid call is made.
package com.vastufirst.shared.scan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One recorded run of the eval harness. Mirrors the on-disk shape of the JSON files in
 * `tools/scan-eval/out/`; `usage` and the other harness bookkeeping are ignored by
 * [RecordedScans.JSON].
 *
 * ⚠ Do not write a `out/<star>.json` glob in a KDoc here. **Kotlin block comments NEST** (unlike
 * Java's), so the slash-star inside the glob opens a second comment, the closing delimiter shuts
 * only that one, and everything to the end of the file is silently swallowed. The compiler reports
 * it as "Unclosed comment" at the last line, which points nowhere near the cause.
 */
@Serializable
data class RecordedReply(
    val fixture: String = "",
    val model: String = "",
    val reply: ScanDraft = ScanDraft(),
)

/** The recorded replies bundled with the app, by fixture id. */
object RecordedScans {

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * A clean digital render of a labelled 2D plan. **Measured: 8/8 rooms found, every name correct,
     * mean IoU 0.79, coverage 1.00.** The good path — a PDF or a screenshot.
     */
    const val CLEAN = "plan-01"

    /** The same plan downscaled and JPEG-compressed. Compression alone barely hurts (IoU 0.79 → 0.73). */
    const val COMPRESSED = "plan-01-jpeg"

    /**
     * The same plan as a simulated phone photo — perspective and tilt. **Measured: all 8 rooms still
     * named correctly but only 2/8 placed, mean IoU 0.37, coverage 0.57.** Skew is the killer, not
     * compression. This is the fixture that proves the coverage gate does its job: it lands in
     * Assisted, so the room list survives and the wrong geometry is thrown away.
     */
    const val PHOTO = "plan-01-photo"

    /**
     * ⭐ A **real** reply from a sheet with 24 named spaces, every one of them SIZED —
     * `BEDROOM 6750X4350`, `ATT. TOILET 1350X2250`, `LIFT 1850X1850 (8 PERSON)`.
     *
     * ⚠ Two claims lived on this fixture for a year and BOTH were false. They are corrected here
     * rather than quietly deleted, because each one cost a real read:
     *
     *   · "a mirrored two-flat floor plate" — it is not. `Documents/Sample Floor plans/plan-002.webp`
     *     has ONE kitchen, ONE living room, ONE dining room, one utility and one entrance lobby with
     *     four bedrooms around them. It is a single 4-bedroom flat taking a whole floor, and the
     *     lift, staircase and lobby down its middle are the tower's core, not part of the home.
     *   · "its rectangles are scattered nowhere near the rooms they name" — that was measured on the
     *     RAW reader output, before the building-box composition, the home framing, the wall-line
     *     snap and the printed-size re-shape existed. Through today's mapper all nineteen land in
     *     the right quarter of the home.
     *
     * So it **PLACES**, and has since the lift rule went (16 Aug 2026). It is no longer the Assisted
     * fixture — see [UNSIZED] for that. What it proves now is that a flat in a tower is one home:
     * the lift caption is dropped as not habitable, so no lift is ever scored, and the core comes
     * out as an empty middle, which is the honest drawing of a space we did not read.
     *
     * Only the model's reply is stored here; the plan image itself stays out of the repo.
     */
    const val DENSE = "real-dense"

    /**
     * ⭐ **The Assisted path's evidence** — a real reply (`plan-004.jpg`) from a large apartment
     * whose sheet prints **no room size anywhere**, naming twenty-one habitable spaces.
     *
     * That pair is exactly what [ScanMapper.SIZED_SHARE_TO_TRUST] refuses to trust. With no printed
     * measurement to anchor anything, a twenty-one room layout is the reader's template rather than
     * the home, so the names are kept and the geometry is thrown away.
     *
     * ⚠ It exists because [DENSE] stopped being Assisted. When the lift rule went, `real-dense`
     * placed — and with it EVERY bundled fixture placed, so nothing drove the Assisted screen at
     * all. The golden that names itself "assisted" was quietly rendering a Placed screen. A fixture
     * that no longer produces the state it is named for is worse than a missing one, because the
     * suite stays green while the state goes uncovered.
     */
    const val UNSIZED = "real-unsized"

    /**
     * ⭐⭐ **THE OWNER'S OWN FLAT, and the first recorded reply for one of his plans.**
     *
     * ⚠ Every scan fixture before this one was a plan somebody rebuilt by hand with plausible noise
     * added, which is exactly the gap the owner called out: a fix validated against a re-typing of a
     * plan has been validated against a guess about the reader, not against the reader. This is the
     * live API's answer to his sheet, captured 2 August 2026 and pasted in unedited.
     *
     * It is the case the whole release is built on, and it says three things at once:
     *
     *  - **the names are perfect** — all fifteen, in the order a person reads the sheet;
     *  - **every room carries the size the plan PRINTS**, thirteen of them matching it exactly — and
     *    under the previous prompt we captured **none of them**;
     *  - **the rectangles are a template, not a measurement** — four distinct x positions for fifteen
     *    rooms, every coordinate on a 0.05 lattice, `planConfidence` 0.95 as ever. Half the 30-plan
     *    corpus comes back the same way, and no wording fixes it: a prompt explicitly forbidding
     *    round coordinates was tried and only pushed more rooms off the page.
     *
     * Fifteen rooms is an ordinary Indian apartment — three balconies, a utility, a vestibule, a
     * passage, a dressing area — and the old room-count gate threw the whole thing away for being
     * one room over a limit its own comment called "a judgement, not a measurement".
     */
    const val OWNER_FLAT = "owner-flat"

    /**
     * ⭐ A real Gurgaon builder plan whose every size is printed in **feet and inches** —
     * `11'-0" x 15'-0"` — the one measurement convention no recorded fixture covered end-to-end.
     * That gap was found the hard way: the fuzz mirror's feet-inches pattern had a broken escape,
     * so no feet-inch size ever parsed THERE while Kotlin parsed them all, and the two disagreed on
     * this plan's entire outcome without any suite noticing. It also carries four rooms captioned
     * just `BALCONY` at four different printed sizes — the duplicate-caption case — and a
     * `LOBBY/DINING`. Thirteen of its fourteen spaces place; the DRESS drops by name.
     */
    const val PLAN_020 = "plan-020"

    /**
     * ⭐ A real 2BHK (Green Court, Sector 90 Gurgaon — the 526 sq ft unit), scanned live on
     * 2 August 2026 after the owner reported its branded twin drawing badly on his phone. Two
     * things no other fixture carries: every size prints in raw MILLIMETRES (`3285X3350`), and the
     * balcony prints only ONE dimension (`1525 WIDE`) — a caption style seen on both copies of
     * this sheet — so it must ride the reader's rectangle (a full-width strip, which the reader
     * gets right on the clean copy) rather than a parsed pair.
     */
    const val GREENCOURT = "greencourt-526"

    /**
     * ⭐⭐ The owner's 336 sq ft 1BHK (Green Court Category-II), scanned live 2 August 2026 from a
     * CLEAN copy of the sheet — the plan whose drawn output he rejected outright that evening
     * ("balcony towering over the rooms, everything clumped left, a huge mostly-empty grid").
     * Carries `BALCONY 1825 WIDE`, the single-dimension strip caption that caused the towering,
     * and it is the fixture behind all three v0.6.5 drawing rules: the strip depth, the
     * dead-edge collapse, and the outer-wall anchor.
     */
    const val GREENCOURT_336_CLEAN = "greencourt-336-clean"

    /**
     * ⭐ The SAME 336 sq ft sheet read from the BRANDED builder page (logo and title take a third
     * of it). The reader's arrangement scrambles — the bedroom comes back full-width when the
     * sheet has it on the right — and stays scrambled: measured, no signal in a single reply
     * separates this copy from the clean one, so it is kept as the proof of that limit rather
     * than gated. The drawing rules stay honest on it; the arrangement stays the reader's error.
     */
    const val GREENCOURT_336_BRANDED = "greencourt-336-branded"

    /**
     * ⭐⭐ **THE SHEET WITH TWO PICTURES ON IT** — a builder's marketing page carrying a large tilted
     * showcase render on the left, a clean straight-overhead plan of the flat in the middle, and a
     * numbered legend down the right naming all fifteen rooms with their printed sizes. There is a
     * north arrow. It is one of the most readable sheets in the whole corpus.
     *
     * It was **refused** until 23 August 2026. Prompt v5 asked the camera question about "the
     * image", so the reader judged the biggest thing on the page and answered `3D_RENDER` — while
     * the very same reply had already boxed the flat plan correctly and returned every room with its
     * size. **The evidence that the sheet was measurable was inside the answer that refused it.**
     *
     * This fixture is the reply recorded under prompt v6, and it guards two things at once:
     *
     *  - the triage answer, which must stay `2D_PLAN` — the refusal is the thing that was wrong;
     *  - the home's SHAPE, which comes from the building box and not the page. The page is 2.43
     *    times wider than it is tall; the flat is very nearly square. Read through the page it maps
     *    to a 10 × 4 letterbox with a room falling off the end. Through the building box it is
     *    10 × 9 and all fifteen rooms land where the paper draws them.
     *
     * ⚠ DELIBERATELY NOT IN [ids]. That list is what [FakePlanReader] cycles through, in order, and
     * what every screen golden is rendered from — adding to it would silently re-shuffle which
     * fixture each screenshot shows. It is also the only bundled reply not from `qwen/qwen3.6-27b`,
     * which the "every recorded reply is bundled" test pins. Loaded by name where it is needed.
     */
    const val COMPOSITE_SHEET = "plan-018-composite"

    val ids: List<String> = listOf(
        CLEAN, COMPRESSED, PHOTO, DENSE, OWNER_FLAT, PLAN_020, GREENCOURT,
        GREENCOURT_336_CLEAN, GREENCOURT_336_BRANDED, UNSIZED,
    )

    /** Load a bundled reply, or null if the id is unknown. */
    fun load(id: String): RecordedReply? {
        val stream = RecordedScans::class.java.getResourceAsStream("/scan/$id.json") ?: return null
        val text = stream.use { it.readBytes().decodeToString() }
        return runCatching { JSON.decodeFromString<RecordedReply>(text) }.getOrNull()
    }

    /** Parse a reply body the model returned, tolerating missing and unexpected fields. */
    fun parseDraft(body: String): ScanDraft? =
        runCatching { JSON.decodeFromString<ScanDraft>(body) }.getOrNull()
}

/**
 * Replays [RecordedScans] through the real [ScanMapper], in order, cycling.
 *
 * The image bytes are ignored — that is the point: this reader proves the pure layer without a key.
 * [imageAspect] is honoured, because it is genuinely supplied by the platform.
 */
class FakePlanReader(
    private val fixtures: List<String> = RecordedScans.ids,
) : PlanReader {

    private var next = 0

    override suspend fun read(image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult {
        val id = fixtures[next % fixtures.size]
        next++
        val draft = RecordedScans.load(id)?.reply
            ?: return ScanResult.Read(ScanOutcome.Refused(RefusalReason.NO_ROOMS, ScanNotes(0.0, 0.0, 0.0)))
        // A recorded reply is replayed against whatever picture the caller decoded — usually none.
        return ScanResult.Read(ScanMapper.map(WallSnap.refine(draft, picture), imageAspect))
    }
}

/** A [PlanReader] that always returns [result] — for driving one screen state in a render golden. */
class FixedPlanReader(private val result: ScanResult) : PlanReader {
    override suspend fun read(image: ByteArray, imageAspect: Double?, picture: PlanImage?): ScanResult = result
}
