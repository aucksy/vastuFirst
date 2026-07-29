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

## 3b. Cost — there is no training cost (verified 2026-07-29)

**Nothing is trained or fine-tuned.** We send an image plus instructions and read the answer. That is
*inference*, billed per use. The only one-off spend is my own iteration while tuning the prompt.

Prices per 1M tokens, at ~₹88/USD:

| Model | Input | Output | Vision | On Groq free tier? |
|---|---|---|---|---|
| `qwen/qwen3.6-27b` | $0.285 | $0.90–1.99 * | yes | **yes** |
| `google/gemini-3-flash-preview` | $0.50 | $3.00 | yes | no |
| `google/gemini-3.6-flash` | $1.50 | $7.50 | yes | no |

\* sources disagree on the output rate; the higher figure is used in every estimate below.

**Per scan** — image ~2–3k tokens + ~800 tokens of instructions ≈ 3.5k in; JSON for ~10 rooms ≈ 700 out:

| | Cost | ₹ |
|---|---|---|
| qwen3.6-27b, reasoning **off** | $0.0024 | **₹0.21** |
| qwen3.6-27b, reasoning **on** | $0.0070 | ₹0.62 |
| Gemini 3 Flash | $0.0038 | ₹0.33 |

⭐ **The biggest cost lever is a config flag.** Qwen3.6 is a *reasoning* model and reasoning tokens bill
as output. Plan reading is extraction, not deliberation — **run it at minimal/no reasoning**. That is
the difference between 21 and 62 paise a scan.

At volume (qwen, reasoning off): 1 000 scans ≈ ₹210 · 10 000 ≈ ₹2 100 · 100 000 ≈ ₹21 000.
Against a ₹699 report, one scan costs **~0.03 %** of one sale.

**Prompt-tuning budget:** ~25 real plans × 100–300 iterations ≈ 2.5–7.5k calls ≈ **₹1 000–3 000
one-off**, and much of it runs on Groq's free tier.

**Cheaper source for the same model?** OpenRouter is already listed as the cheapest provider for
qwen3.6-27b ($0.285 in). DeepInfra and Alibaba's own DashScope serve Qwen models and can undercut on
paper, but at our volumes the gap is tens of rupees per thousand scans — not worth a second
integration. OpenRouter's real value here is one key across many models plus automatic failover, which
is exactly the two-key arrangement the owner asked for. Revisit only if volume passes ~100k scans.

## 3c. ⚠ Accuracy — the finding that shapes the whole feature

**AECV-bench** (2026, 120 floor plans, 10 state-of-the-art vision models):

| Element | Best model | Accuracy |
|---|---|---|
| Bedrooms | GPT-5.2 / Claude Opus 4.5 | 91 % |
| Toilets | Gemini 3 Pro / GLM-4.6V | 82 % |
| **Doors** | Gemini 3 Pro | **39 %** |
| Windows | Gemini 3 Pro | 34 % |
| **Mean** | Gemini 3 Pro | **51 %** |

Document-QA split: OCR / text extraction **~95 %**, comparative reasoning ~80 %, spatial reasoning
~70 %, instance counting 40–55 %. The authors' conclusion: *"Current AI works well as a document
assistant but lacks robust drawing literacy."*

⭐ **Two decisions fall straight out of this.**

1. **Never ask the model for the front door.** Door detection tops out at 39 % — and the front door is
   the **highest-weighted single input the engine scores**. Letting a coin-flip set it would silently
   corrupt the number the customer pays for. The user already places the door by tapping a wall, and
   that path was hardened in v0.3.11 (`doorForTap`, measured from the house). **Scan drafts rooms
   only; the door stays manual.** This converts the benchmark's worst result into a non-issue.
2. **Lean on the labels, not the geometry.** Indian architectural plans are captioned — *"BEDROOM
   12'-0" × 10'-0"", "KITCHEN", "POOJA"*. That makes this an OCR-plus-layout task (95 % / 70 %) rather
   than shape inference from bare walls (40–55 %). The prompt must be built around reading captions
   and their positions, and a plan with no captions should be treated as low-confidence and routed to
   the guided grid.

**Honest expectation to set with the client:** scan saves typing and gets most *labelled* rooms about
right. It is a drafting aid with mandatory human confirmation — which is precisely what §6.2b already
specifies. It must never be sold as "the app reads your plan for you".

## 3d. Model choice is not final — measure it

`qwen3.6-27b` was selected under one constraint: *it is the only vision model Groq hosts*. With
OpenRouter in the mix that constraint is gone, and Qwen3.6 is billed as an **agentic coding** model
with vision attached — not a document-vision specialist. It did not appear in AECV-bench at all.

Because `PlanReader` makes the model a config string, the right move is a **measured bake-off**: the
same 20–25 real plans through 3 candidates (qwen3.6-27b · Gemini 3 Flash · one premium control such as
Gemini 3 Pro), scored on rooms-found and label accuracy against a hand-made answer key. Cost ≈ ₹200.
**Pick on measured accuracy, not on marketing or on who is free.** Free is worthless if the draft is
wrong often enough that correcting it is slower than drawing from scratch — that is the real bar this
feature has to clear.

## 3e. ⭐ Measured on the real API — 2026-07-29

The Groq key arrived, so this stopped being a paper exercise. Harness: `tools/scan-eval/`
(`run-eval.py` scores a model against a fixture with exact ground truth; `make-photo.py` degrades a
clean render into a realistic desk photo; `rectify.py` tests a two-pass corner-finding approach).
Fixture `plan-01` is a synthetic Indian 2BHK, 8 labelled rooms tiling a 30′×40′ footprint —
**synthetic on purpose, so answers can be scored rather than admired**.

Model: `qwen/qwen3.6-27b` — confirmed by API listing to be the **only** image-capable model this
Groq account can reach (15 models visible; one accepts `image`).

| Input | rooms IoU≥0.5 | mean IoU | coverage | self-reported confidence |
|---|---|---|---|---|
| Clean digital render | **8/8 (100 %)** | 0.79 | 100 % | 0.95 |
| Downscaled + JPEG q72, no skew | 6/8 (75 %) | 0.73 | — | 0.95 |
| Simulated phone photo | **2/8 (25 %)** | 0.37 | **57 %** | 0.95 |

**Cost measured, not estimated:** 2 061 prompt + ~570 completion tokens = **₹0.15 per scan**, below
the ₹0.21 projected in §3b. The estimate holds.

### Four findings, each of which changes a design decision

1. ⭐ **Labels survive photography; geometry does not.** Even on the bad photo the model found all 8
   rooms and named **every one correctly**. What collapsed was *where* they are (IoU 0.79 → 0.37).
   This is the published benchmark reproduced exactly: OCR ~95 %, spatial reasoning ~70 %.
   **Use the model for identification, not for measurement.**
2. ⭐ **The model's confidence is worthless as a gate.** `planConfidence` was **0.95 on both** the
   100 % read and the 25 % read. Gating on it would have shipped bad plans as confident ones.
   **Coverage** discriminates cleanly (100 % vs 57 %) and needs no ground truth — it runs on the
   user's phone, on their plan.
3. ⭐ **Skew is the killer, not compression.** Isolating the variables: JPEG + downscale alone costs
   little (0.79 → 0.73); adding perspective and tilt is what destroys it (→ 0.37). So a **PDF or
   screenshot is the good path** and a **photograph is the hard one**.
4. ⭐ **The model cannot rectify its own input.** Asked for the outer wall's four corners so we could
   de-skew mathematically, it returned a perfect axis-aligned square — `(0.11,0.11)–(0.65,0.65)` —
   for a visibly skewed quadrilateral. A guess, not a measurement, and the rectified image was
   garbage (verified by looking at it). **Corner-finding is the same weak spatial skill.** De-skew
   must be deterministic code, never a second model call.

### The design these findings force

**Two outcomes, chosen by the coverage gate — and both are honest:**

- **Placed** (coverage ≥ ~85 %, typical of a PDF/screenshot): rooms arrive positioned on the grid.
  The user confirms and corrects, per §6.2b.
- **Assisted** (coverage below the gate, typical of a photo): we say *"we found these 8 rooms but
  couldn't place them reliably — here they are, drop them in"*, and pre-load the palette with the
  right rooms. **This is still a real saving** — the model got every room name right even on the bad
  photo — and it never pretends to a precision it does not have.

This satisfies §6.2b's "fall back without an error state" with something better than a bare fallback:
the degraded path still carries the model's genuine strength.

**Steer users to the good input.** The upload copy should ask for *"the PDF your architect sent"*
first and offer the camera second, because the format difference is worth more than any model choice.

**Deferred, not chosen:** classical CV page-detection (find the plan quadrilateral by edge/contour,
rectify, then extract) would likely move photos into the Placed path. It is the natural upgrade, but
OpenCV is a heavy dependency against a 30 MB APK budget (NFR §10) and it is not needed to ship a
useful v1. Revisit once real user plans show how many arrive as photos.

## 3f. ⭐ Tested against REAL plans — and the corpus question is not closed

**The owner challenged the synthetic-only evidence, and was right to.** Everything in §3e rests on
one fixture I drew myself, built to tile its footprint exactly with clean labels and no clutter.
That flatters the model, and — more seriously — **it mis-calibrates the coverage gate**: real plans
have wall thickness, corridors, stairwells and shafts, so their rooms will *never* sum to 100 % of
the footprint. **The ~85 % threshold in §4.2 is calibrated on a fiction and must be re-derived from
real plans before it is trusted.** Treat it as a placeholder.

### ROBIN — 510 real Indian plans, downloaded and tested

[github.com/gesstalt/ROBIN](https://github.com/gesstalt/ROBIN), GPL-3.0, 510 JPGs across 3/4/5-room
categories. Held in `tools/scan-eval/corpus/`, which is **git-ignored** — we evaluate against it, we
do not redistribute it.

⚠ **ROBIN plans carry no room labels at all.** Rooms are identified purely by furniture symbols — a
bed means bedroom, a WC means toilet. That is the exact opposite of the case §3e's prompt targets, so
ROBIN is an **adversarial** corpus, not a representative one. Its plans are also frequently
**L-shaped**, which is the parked Group D footprint issue appearing in real data.

**Result — the refuse path works.** On the unlabelled plans tested the model returned
`unreadable: true`, **zero rooms, confidence 0.00**. It did not invent rooms. Given §3e finding 2
(confidence 0.95 on a 25 % read) the honest expectation was that it would hallucinate confidently;
it did not. ⚠ Only 2 plans completed before the rate limit bit — **this is an encouraging signal, not
a proven property**, and it needs re-running across a proper sample.

### ⚠ Groq's free tier cannot serve production

Measured from the live rate-limit headers:

| Limit | Value | Consequence |
|---|---|---|
| Requests / day | 1 000 | fine |
| **Tokens / minute** | **8 000** | **binding** |

A scan costs ~2 630 tokens, so the free tier allows **~3 scans per minute in total, across all
users**. Two people scanning at once would throttle each other. It is adequate for development and
for the client's own testing; **it cannot back a launched product.** The OpenRouter key therefore
moves from "nice fallback" to **required before launch**, and the failover must handle HTTP 429
specifically (not just outages) by falling through to the paid provider.

### What is still unvalidated

Neither corpus matches what users will actually upload — an architect's PDF or a builder's brochure,
which has room names **and** furniture **and** dimension lines **and** clutter. ROBIN has geometry
without labels; the synthetic fixture has labels without clutter. **The representative corpus is
still missing**, and until it exists these numbers should not be quoted to the client as what the
feature will do.

**Best available corpus: the client's own plans.** Simran is a Vastu consultant — her customers send
her exactly the plans this feature will receive. Ten to twenty real ones, with permission and any
personal details removed, would be worth more than any public dataset, cost nothing, and carry no
licence question.

⚠ **Licence note:** **CubiCasa5K** (5 000 richly annotated plans, the obvious first hit) is
**CC BY-NC — non-commercial**. VastuFirst is a paid product. Do not use it, for evaluation or
anything else.

## 3g. ⭐ OWNER DECISION (2026-07-29) — what the user must upload

> *"It will be a requirement for the user to upload a plan that has the rooms labelled and
> optionally north marked; if not, they mark north in the next step."*

This settles the central design question and **ratifies the prompt strategy in §3c**: the model's
strongest skill is reading printed text (~95 %) and its weakest is inferring geometry (40–55 %).
Requiring labels means the feature leans on the strength by contract, not by hope.

Consequences:

1. **A labelled plan is a precondition, not a nice-to-have.** An unlabelled plan is a *rejected
   input* with a clear message — not a failed read. ROBIN (§3f) is therefore out of scope for the
   main path and only ever exercised the refusal path, which is exactly what it was useful for.
2. **North is optional in the image.** If a north mark is present we can offer it; if not, the user
   sets it on the existing Mark-North screen. **No change needed** — that screen already exists and
   is the app's signature interaction. Scan simply doesn't have to solve north.
3. The extraction prompt now also reports `hasRoomLabels` and `northMarked`, so the app can say
   precisely *which* precondition failed instead of a generic "couldn't read it".

### ⚠ And a third precondition that only real data revealed: the plan must be 2D

The very first real image inspected (`plan-031.jpg`) was a **3D isometric marketing render** — a
perspective view with furniture, shadows and drop shadows, builder branding, and the room labels
floating *outside* the building on leader lines. There is no top-down rectangle to extract: the
geometry is projected, so every room's apparent shape is a function of the camera, not the floor.

This matters because **a large share of what an image search returns for "floor plan" is builder
marketing, not a plan** — and a user searching their builder's website will hit exactly the same
mix. A 3D render would not fail loudly; it would yield plausible-looking rectangles that are simply
wrong, which is the worst failure mode for a paid score.

**So the input gate is three questions, asked before any extraction is trusted:**

| Precondition | Failure message |
|---|---|
| Is it a **2D** plan (not a 3D render / elevation / photo)? | "This looks like a 3D picture of the home. Please upload the flat, top-down plan." |
| Are the rooms **labelled**? | "We can't see room names on this plan. Upload one with the rooms named, or draw it instead." |
| Do the rooms **tile the footprint** (coverage gate, §3e)? | "We couldn't read this cleanly — let's place the rooms together." |

Only the third is fuzzy; the first two are crisp and cheap, and they catch the failures a user can
actually fix. This is `ScanOutcome` gaining explicit refusal reasons rather than one `Unreadable`.

## 3h. ⭐⭐ Measured on 30 REAL plans — the gate would have rejected all of them

Owner downloaded and curated 30 real Indian plans. Full run: `tools/scan-eval/batch-real.py`,
results in `out/real-plans.json`.

### What the images actually were

| Classification | Count |
|---|---|
| `2D_PLAN` | 24 (80 %) |
| `3D_RENDER` | 5 (17 %) |
| `NOT_A_PLAN` | 1 (3 %) |

**One upload in five is not a usable plan.** The classifier caught every render I verified by eye,
including `plan-031` (§3g). The 2D/3D gate is not theoretical — it fires on real user input.

### ⭐ The finding that matters most

| Corpus | Footprint coverage |
|---|---|
| Synthetic fixture (§3e) | **100 %** |
| 30 real plans | **min 20 % · median 44 % · max 76 %** |

**The ~85 % coverage gate proposed in §3e would have rejected 30 out of 30 real plans.** Every
single one. The feature would have shipped refusing all genuine input while passing only the test
plan I drew myself.

This is exactly the failure the owner's challenge (§3f) predicted, and it is worth stating plainly:
**a synthetic fixture cannot calibrate a threshold.** The gate stays — coverage still separates a
good read from a bad one *within* a corpus — but its threshold must be re-derived from real plans,
and the absolute number is far lower than intuition suggests because real homes are mostly walls,
corridors, ducts and shafts.

### Labels are excellent. Geometry is not. Again.

Real reads, verbatim: `BEDROOM 6750X4350` · `ATT. TOILET 1350X2250` ·
`LIFT 1850X1850 (8 PERSON)` · `5'-0" WIDE BALCONY` · `SER ROOM` · `WALK IN CLOSET` · `PUJA`.
That is precise reading of Indian plan conventions, including abbreviations.

Meanwhile `plan-014` returned a **flawless** room list — BEDROOM-1, WALK-IN WARDROBE, TOILET-1,
PUJA SPACE, LIVING & DINING, FOYER, VERANDAH — at **28 % coverage**. Names right, rectangles wrong.

⭐ **This is now measured three independent ways** (clean vs photo §3e, unlabelled corpus §3f, 30 real
plans here): **the model reliably identifies WHICH rooms a home has, and does not reliably know
WHERE they are.** That is not a prompt problem to be tuned away; it is what the published benchmarks
say too (OCR ~95 %, spatial 40–70 %).

### The product conclusion this forces

**"Assisted" is not the fallback — it is the primary mode.** The honest feature is:

> *We read your plan and found these 11 rooms — Master Bedroom, Kitchen, Puja, 3 Toilets, Balcony…
> Now place them on the grid.*

That removes the two slowest parts of the current flow — deciding the room list and picking each
type out of a palette — and keeps the part the model cannot do, placement, with the person who knows
the answer. It is a real saving, it is honest, and it degrades to the existing guided grid with no
new failure mode. Promising "the AI draws your plan" would ship a confidently wrong ₹699 score.

### Two more things real data exposed

**1. ⭐ 27 % of labels carry the room's printed DIMENSIONS** (`BED ROOM 12'1"X11'0"`,
`KITCHEN 2950X4200`), and in **7 of 23 plans most labels do**. Those dimensions are *text*, which is
the model's 95 %-accurate skill — a far more trustworthy geometric signal than its own rectangles.
Where present, they could size rooms properly on the grid. **Promising, not proven** — a bonus when
available, never the foundation. Worth its own experiment.

**2. ⚠ Only 74 % of labels map to the app's 19 `RoomType` values.** The commonest gaps:

| Unmapped space | Occurrences |
|---|---|
| DRESS / DRESSING / DRESS AREA | 14 |
| PORCH · SIT OUT | 4 |
| VESTIBULE | 2 |
| DUCT · ELECTRICAL DUCT | 3 |
| LIFT · LIFT LOBBY | 3 |
| SER ROOM (servant) · AV ROOM | 2 |

A **dressing area** appears in real Indian plans constantly and the app has no type for it. The
mapper needs a synonym table *and* a documented policy for unmappable spaces — merge into the parent
room, map to the nearest type, or drop with a note. Silently dropping them changes the footprint the
engine scores, so this is a scoring decision, not a cosmetic one. **Needs an owner call.**

**3. ⚠ Numbered-legend plans.** `plan-018` returned labels `1, 2, 3 … 15` — the rooms are numbered on
the drawing with a key printed alongside. A real and common style; currently it passes the
"has labels" check while carrying no usable names. Either resolve the legend or reject explicitly.

## 3i. ⭐⭐⭐ THE ARCHITECTURE — what everything measured adds up to

**One sentence: the model reads *text*; our own code does *geometry* and *everything Vastu*; the
user confirms.** Every measurement in §3e–§3h points the same way, and the design is now evidence-led
rather than assumed.

### The three hard safety rules — each bought with a measurement

| # | Rule | Why — measured |
|---|---|---|
| **S1** | **The model never answers a Vastu-shaped question, and no Vastu vocabulary appears in the prompt.** | E1: asked for a room's *sector*, it returned byte-identical answers for a clean render and a badly skewed photo — it wasn't reading the image — and its errors moved toward doctrine (MASTER BEDROOM→SW, KITCHEN→E, the textbook positions, not the drawing's W and NE). A reader that nudges homes toward canonical placement makes every home score better than it is. |
| **S2** | **Never gate on the model's self-reported confidence.** | §3e: `planConfidence` 0.95 on both a 100 % read and a 25 % read. |
| **S3** | **Never let the model place the front door.** | Benchmarks: door detection 39 %, and the door is the highest-weighted single input the engine scores. The user taps it, as today. |

⭐ **S1 is the one specific to this product.** Every other app using a vision model wants the model to
be knowledgeable. We need it to be *ignorant* — a neutral document reader. The instant it starts
"helping" with Vastu, the score becomes fiction.

### The five layers

```
 L0  TRIAGE      is this a usable plan at all?         model classifies · our code decides
 L1  IDENTITY    which rooms does this home have?      model reads TEXT        ← reliable core
 L2  ARRANGEMENT roughly where are they?               model gives rectangles · quality-gated
 L3  ZONES       which Vastu zone is each room in?     OUR CODE ONLY. never the model.
 L4  CONFIRM     user adjusts, sets north + door       the existing grid editor
```

**L1 is the product.** It works on every 2D labelled plan and is the model's 95 %-accurate skill. If
L2 fails entirely, L1 alone still deletes the two slowest steps of the current flow — working out the
room list and hunting each type out of a palette.

**L2 is a bonus, never a promise.** Signals in priority order — the higher one available, the better
the draft:

1. **Printed dimensions in the labels** (`BED ROOM 12'1"X11'0"`) — *text*, so 95 %-grade. Present in
   27 % of labels and in most labels on 7 of 23 plans. Gives true room *sizes*.
2. **The model's rectangles** — 40–70 %-grade. Good enough for rough arrangement, gated on coverage.
3. **Nothing** — rooms are handed to the user unplaced, in a sensible default order.

**L3 is deterministic, always.** E1 proved our own 3×3 partition of the model's coordinates (100 %)
beats asking the model to partition (50 %). Zone assignment already exists in the engine and is
tested; scan feeds it coordinates and never opinions.

### What this covers — the plan-type matrix

| Plan type | Seen in the 30 | Outcome | What the user gets |
|---|---|---|---|
| 2D, labelled, **dimensioned** | 7 / 23 | L1 + L2 (sizes) | Rooms placed and correctly sized |
| 2D, labelled | 16 / 23 | L1 + L2 (rough) | Rooms placed, needs adjusting |
| 2D photo, skewed | — | L1 only | Room list pre-loaded, user places |
| Numbered legend (`1…15`) | 1 | L1 if the key is readable, else refuse | Rooms, or a clear ask |
| Multi-unit sheet | seen | Ask user to crop to one home | Clear instruction |
| **3D render** | 5 / 30 | **Refuse** | "Upload the flat, top-down plan" |
| Unlabelled | 1 / 30 | **Refuse** (owner's rule §3g) | "Upload a labelled plan, or draw it" |

**Every branch ends somewhere useful.** Nothing dead-ends, nothing lies, and the worst case is the
guided grid that already exists and is fuzz-hardened.

### Open experiments, in value order

| | Experiment | Settles | Cost |
|---|---|---|---|
| **E2** | Dimension-driven sizing — parse `12'1"X11'0"` from labels, size rooms from text instead of rectangles | Whether L2 tier 1 is real | ~₹5 |
| **E3** | Coverage-threshold calibration across the 24 real 2D plans, judged by eye | The one number still untrusted | ~₹5 + an hour of looking |
| **E4** | Does a neutral prompt (rooms renamed "Room A/B") change the sector answers? | Confirms S1 beyond a strong signal | ~₹2 |
| **E5** | Hindi/Devanagari labels | 6-language launch (§7.5) — completely untested | ~₹5 |
| **E6** | Multi-unit sheet detection | A real case seen in the corpus | ~₹5 |

### Owner decisions still needed

1. **Unmappable rooms** (§3h) — DRESS/DRESSING ×14, PORCH, VESTIBULE, DUCT, LIFT, SER ROOM. Add
   types / fold into parent / map to nearest? **This moves scores**, so it is not cosmetic.
2. **Numbered-legend plans** — resolve the key, or refuse and ask for a named plan?
3. **Where the API key lives** (§6.2) — unchanged, and now urgent given the free tier cannot serve
   production (§3f).

### Effort

| Layer | Days |
|---|---|
| L0 triage + refusal messages | 1 |
| L1 identity, synonym table, tests | 2 |
| L2 arrangement, coverage gate, fuzz suite | 2 |
| Android glue — picker, PdfRenderer, downscale, HTTP, Groq→OpenRouter failover | 2 |
| Consent + legal copy | 0.5 |
| Screens, render goldens, adversarial review, tag | 1.5 |
| **Total** | **~9 days** |

Fits Stage 3's 10–28 Aug window with room to spare. **L0–L2 need no key and no network** — they are
built against recorded replies and proven in CI at zero cost.

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
5. ⭐ **Coverage gate — NOT the model's confidence.** See §3e: the model reported
   `planConfidence: 0.95` on both a 100 %-correct read and a 25 %-correct one, so its self-report
   is worthless as a gate. Use the **objective** signal instead: do the returned rooms *tile* the
   footprint? Coverage of the unit square was **100 % on a good read and 57 % on a bad one**, with
   no ground truth needed. Gate on measured coverage, overlap area and out-of-bounds count.

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
