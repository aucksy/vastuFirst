# Scan your plan — AI-assisted floor-plan reading (§6.2b)

**Status:** plan, awaiting "go". Written 2026-07-29.
**Why now:** owner pulled this forward — *"hold the Draw-your-plan feature and start working on
scan-your-plan. Client needs that first."*

> ⚠ **Scope note.** This is contractually **Stage 3** work ("Reports, remedies, AI & languages",
> 10–28 Aug, Rs 30,000). The **4 Aug / Stage 2** milestone is "Android app + Vastu engine", which is
> built and shipped as v0.3.13. Pulling scan forward does not by itself endanger the Stage 2 payment,
> but the next six days are no longer being spent on Stage 2 polish. Recorded here so it is a decision
> rather than a surprise.

---

## 1. What the spec actually demands

Product PRD **§6.2(b)**, quoted in full because every design choice below follows from it:

> User photographs or uploads a plan (JPEG/PNG/PDF). A vision model drafts room rectangles and types.
> **The user is then shown every detected room and must confirm or correct each one before any score
> is computed.** Rooms the model is unsure about are flagged for attention, not silently accepted. If
> the model fails or returns low confidence, fall back to (a) without an error state — "we couldn't
> read this cleanly, let's place the rooms together."

Implementation PRD §232 adds: **"There is no fully-automatic path to a score."**

Three consequences that shape everything:

1. **Scan is a *drafting* tool, not a scoring path.** Its output must land in something the user edits.
2. **That something already exists** — the guided grid editor, hardened over v0.3.6–v0.3.13 and fuzzed
   across 400 000 sequences. **Scan's output type is the editor's input type.**
3. **Failure is a fallback, never an error state.** Low confidence routes to the editor with the rooms
   the user would have placed anyway.

⭐ **Therefore the guided grid is not on hold in any meaningful sense — it is the confirmation surface
this feature delivers into.** No editor changes are planned (see §7 for the one thing that might force
one).

## 2. The seam that makes this buildable without a key

```
  image ──► PlanReader ──► ScanDraft ──► ScanMapper ──► List<GridRoom> ──► existing editor
            (interface)    (normalised)   (pure)         + door + cols/rows    (unchanged)
             │
             ├── GroqPlanReader   — real, needs key + network      ← blocked on owner
             └── FakePlanReader   — recorded JSON fixtures         ← unblocked, ships in tests
```

`PlanReader` is a one-method interface:

```kotlin
interface PlanReader { suspend fun read(image: ImageBytes): ScanOutcome }
```

Everything to the right of it is **pure Kotlin in `:shared`** — no Android, no network, no key — and is
where essentially all the correctness risk lives. It can be built, tested and proven green in CI
**today**, before any account exists. The Groq call is then a thin, swappable transport.

This is the same pattern that worked for the editor: put the arithmetic in a pure module with tests CI
actually runs, because logic written inside a Composable (or inside a network callback) is logic
nothing can prove.

## 3. Where the AI stands — verified 2026-07-29, not from memory

| Fact | Value |
|---|---|
| Groq vision model | **`qwen/qwen3.6-27b`** — the only vision model Groq currently documents |
| Max image (URL) | 20 MB |
| Max images / request | 5 |
| JSON mode with images | **Supported** |
| `meta-llama/llama-4-scout-17b-16e-instruct` | ⚠ **shut down 17 July 2026** |
| `meta-llama/llama-4-maverick-17b-128e-instruct` | ⚠ shut down 9 March 2026 |

⚠ **Both Llama 4 vision models are dead** — Scout twelve days ago. Any code written from prior
knowledge would have failed on its first call. Pin the model id in config, never in Kotlin, and treat
"the model was retired" as a first-class failure mode that falls back to the guided grid.

Cost is per-token with images tokenised into the same stream, so a scan's price scales with image
resolution — downscaling before upload is a **cost** control, not just a bandwidth one.

## 4. The pure layer, in detail

### 4.1 `ScanDraft` — what the model is asked for

Normalised coordinates (`0.0..1.0`) against the plan image, so the model never needs to know the app's
grid: a room is `x, y, w, h, label, confidence`. Plus a whole-draft `confidence` and an optional door
edge. Normalised because it makes the model's job easier *and* makes the mapper's job pure.

### 4.2 `ScanMapper` — normalised boxes → `List<GridRoom>`

This is the hard part and it is entirely testable. Ordered concerns:

1. **Pick the grid.** `cols`/`rows` from the image's aspect ratio, clamped to `MIN_GRID=4..MAX_GRID=10`.
2. **Snap** each box to whole cells; **drop** anything that rounds to zero cells.
3. **Resolve overlaps.** ⭐ **Do *not* reuse `fitWithoutOverlap` here.** That function *relocates* a
   room to the nearest free space, which is exactly right when a user is dragging (they are watching)
   and exactly wrong for a scan (they are not). A relocated room silently misreports where the kitchen
   is — and the kitchen's zone is a scored input. Scan must instead **trim the lower-confidence room**
   at the contested cells and **flag both**, or refuse the draft. Never move a room the user hasn't
   seen yet.
4. **Map labels → `RoomType`** across the 19 enum values, with Indian-English synonyms the model will
   emit: *pooja/puja/prayer*, *drawing room → LIVING*, *WC/wash → TOILET*, *servant → BEDROOM*,
   *utility/wash area → UTILITY*, *lobby/passage → CORRIDOR*. Unknown label ⇒ flagged, not guessed.
5. **Confidence gate.** Below threshold ⇒ `ScanOutcome.Unreadable` ⇒ guided grid, no error state.

### 4.3 Proof obligations

Matching the standard the editor is already held to:

- Unit tests in `:shared` for every rule above, including adversarial model output: boxes outside
  `0..1`, inverted rects, `NaN`, 40 rooms, zero rooms, every-room-overlapping, unknown labels,
  duplicate labels, a single room filling the plan.
- **A fuzz suite** (`sim.mjs` Suite E) over random model outputs asserting the editor's invariants hold
  on everything the mapper emits: no overlap, all in-grid, door on the footprint, `1 ≤ w,h`. The
  mapper's output must be indistinguishable from a hand-built plan — because downstream it *is* one.
- Every invariant **proven to bite by fault injection** before being trusted.

## 5. The Android layer (needs deps this app has never had)

The app currently has **no HTTP client, no image loading and no camera** — it is fully offline. Scan
adds the first of each. Kept minimal:

- **Pick an image:** `PickVisualMedia` (Photo Picker) — **no runtime permission**, works API 26+ via
  the AndroidX backport. Preferred over a gallery permission.
- **Camera:** `TakePicture` intent first. CameraX only if the owner wants an in-app viewfinder — it is
  a heavy dependency against a 30 MB APK budget (NFR §10) and currently we sit at 11.5 MB.
- **PDF:** `android.graphics.pdf.PdfRenderer` is **in the platform since API 21** — no dependency.
- **Downscale + JPEG encode before upload** — bounded cost, bounded latency, and it keeps us far under
  the 20 MB ceiling.
- **HTTP:** Ktor client (already a Kotlin-ecosystem fit and the KMP-friendly choice for the iOS
  re-target) or bare `HttpURLConnection` for one endpoint. Leaning Ktor **only** if iOS reuse is
  wanted; otherwise one hand-rolled POST is smaller and adds no version-matrix risk.

## 6. Blockers that need the owner

### 6.1 A Groq account and API key — hard blocker for the *real* call only

Free tier exists. Needed only for step 5 of the build; steps 1–4 proceed without it.

### 6.2 ⭐ Where the key lives — a real decision, not a detail

**An API key shipped inside an APK is extractable.** Anyone who downloads the app can pull it out and
spend the client's money. Two options:

| | How | Good | Bad |
|---|---|---|---|
| **A · key in the app** | Key in a git-ignored `.env`, baked at build time | Ships today; no backend | Extractable. Acceptable for a *private* client-test build with a spend cap; **not** for Play Store |
| **B · proxy** | Supabase Edge Function holds the key; app calls the proxy | Key never leaves the server; add rate-limiting and abuse control | Needs the Supabase account (already planned for Phase 4+); ~half a day |

**Recommendation:** build behind `PlanReader` so the transport swaps without touching a line of the
pure layer. Ship **A** for the client's testing with a hard spend cap set in the Groq console; move to
**B** before store submission (20 Sept). The seam makes that a one-file change.

### 6.3 Privacy consent — DPDP, and it is a requirement not a nicety

NFR §10 already states: *"Floor plans and location are personal data under India's DPDP Act — explicit
consent, clear retention, user-initiated deletion."* Scan is the first feature that **sends a user's
home layout off the device**. It needs an explicit, plain-English consent before the first upload, plus
a line in the legal screen saying what is sent, to whom, and that it is not retained by us. The guided
grid path stays fully offline and must be visibly offered as the alternative.

## 7. The one thing that may force an editor change

`MAX_GRID = 10`. A scanned 3BHK can carry 9–11 distinct rooms; at 10×10 cells with a 1-cell minimum
the mapper can run out of resolution and start merging or dropping small rooms (bathrooms, pooja,
utility). If tests show that biting, the fix is raising `MAX_GRID` for scanned plans — which touches
the editor the owner asked to hold. **Flagged, not silently changed.** Measured in step 2 before any
decision.

## 8. Build order

| # | Step | Blocked? |
|---|---|---|
| 1 | `ScanDraft`/`ScanOutcome` types + `PlanReader` seam + `FakePlanReader` with recorded fixtures | No |
| 2 | `ScanMapper` + full `:shared` test suite + fuzz Suite E, all proven to bite | No |
| 3 | Scan UI: pick/capture → progress → "we read N rooms, check them" → editor with flagged rooms | No (fake reader) |
| 4 | Consent screen + legal copy | Needs §6.3 wording sign-off |
| 5 | `GroqPlanReader` — real call, model id in config, retry/timeout/refusal → fallback | **Needs key** |
| 6 | Prompt engineering against real plans, measured on a fixture set | **Needs key** |
| 7 | Render goldens for every new screen, adversarial review, tag, device checklist rows | No |

Steps 1–3 are the bulk of the risk and are **unblocked today**.
