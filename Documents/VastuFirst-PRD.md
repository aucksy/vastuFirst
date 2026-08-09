# VastuFirst — Product Requirements Document
**Version 1.1 · 19 July 2026 · Android build**
*v1.1 corrects nine defects found in independent review of v1.0 — see Appendix B notes.*

> **How to use this document.** This is the single source of truth for building VastuFirst. It is written to be consumed with no other context. Everything needed to build the Android app — product rules, engine algorithms, rule data, screen specs, data model, phases — is contained here. Where an external document is referenced it is for *additional* depth, never for information required to build.

---

## 0. Read this first — the non-negotiables

These are the rules that must survive every implementation decision. If a later section appears to contradict one of these, this section wins.

**0.1 Vastu has no scientific validation. The app never claims otherwise.**
Present every rule as *traditional prescription*. Never as fact, prediction, or guaranteed outcome. No health, wealth, marriage, fertility, longevity or fortune claims anywhere — not in the UI, not in report copy, not in remedy descriptions, not in AI assistant answers. This is a legal requirement under India's Consumer Protection Act and ASCI advertising rules, and it is the product's honesty position. A visible disclaimer appears on the report screen — not buried in settings.

**0.2 Room-placement rules are NOT in the classical texts.**
Kitchen-in-South-East, master-bedroom-in-South-West, pooja-in-North-East are *not* verses in Mayamata, Manasara or Brihat Samhita. What those texts fix is the mandala and its deity-directions. The room checklist is a reasoned 20th-century extrapolation from that. The app must never present these as ancient scripture. Every rule carries a provenance tag (§8.1) and the UI surfaces it.

**0.3 Pooja is classically CENTRAL, not North-East.**
Manasara reserves the Brahmasthan (centre) for the family deity. North-East is near-universal *modern* practice. This is genuinely disputed. The app must not silently pick a side.

**0.4 Facing direction carries ZERO score.**
South-facing and west-facing homes are not inherently inauspicious. That is folk prejudice, not tradition. The **entrance pada** decides — a south-facing home with its door on pada S3 (Vitatha) scores better than an east-facing home with its door on E6 (Satya). The engine must never apply a penalty or bonus based on which way the building faces.

**0.5 Zones are computed on the 81-pada square grid, not 45° pie sectors.**
The tradition uses a square pada grid. Angular sectors diverge badly near the corners of a plan and are wrong. (The HTML prototype uses 45° sectors — do not port that.)

**0.6 AI never decides anything silently.**
AI-assisted floor-plan reading *drafts* a layout; the user confirms every room before any score is produced. A silently mis-read room corrupts the entire analysis. Product framing: *"AI-assisted plan reading — you check, we don't guess."* There is no fully-automatic path to a score.

**0.7 There is no "optimise my score" affordance.**
North is a physical fact about the building, not a variable to tune. The app must never offer to find the orientation that maximises the score. (The prototype has a "Find the best angle" button. It must not exist in the product.)

**0.8 Rules are data, not code.**
Eight rule questions are currently with expert reviewers and unresolved (§16). The engine must be a generic evaluator over a versioned rule dataset so that every expert ruling is a **data edit, not a code change**. Hard-coding any disputed rule is a defect.

---

## 1. What the product is

VastuFirst analyses a home's floor plan against Vastu Shastra and tells the user what to change — while changing it is still free.

**The wedge is pre-construction.** You can move a kitchen on a drawing for nothing. You cannot move a wall in a finished flat. Every competitor sells remedies to people who already live somewhere. VastuFirst targets the moment before concrete is poured.

**Core loop:** add your floor plan → mark which way North is → get a score, a defect report, and specific layout changes plus remedies for what cannot move.

**Competitors:** Grihafy, Apzok and AppliedVastu already do floor-plan Vastu. We are not first. Our edges are: a real native mobile app (rivals are web-first), instant in-app results (no waiting on a human report or WhatsApp follow-up), transparent provenance, honesty where schools genuinely disagree, no hidden paywall or forced phone capture, a clean centre point on the compass (rivals put their logo over the exact point users need), and pre-construction focus.

---

## 2. Who uses it — and the branch that matters most

On first run the user answers one question. **This is the single most consequential input in the product** and it changes what the report can offer.

| Intent | Meaning | What the report can offer |
|---|---|---|
| `BUILDING` | Plan not final yet | **Layout changes** — the full product. Everything is still free to move. |
| `BUYING` | Choosing between options | **Comparison + layout changes** — can still negotiate or walk away. |
| `LIVING` | Already lives there | **Remedies only.** Walls cannot move. Report must not lead with impossible advice. |

**Requirement:** report generation branches on intent. For `LIVING`, layout-change suggestions are demoted below remedies and framed as "if you ever renovate" — never as the primary call to action. Showing a `LIVING` user "move your kitchen to the South-East" as the headline fix is a product failure.

**Onboarding constraints:** no sign-up, no phone number, no OTP, no email gate before the free score. Rival apps gate this and it is their most-complained-about behaviour. Anonymous auth only.

---

## 3. Architecture

### 3.1 Module structure — mandatory

```
vastufirst/
├── engine/          Pure Kotlin. NO Android plugin, NO Android dependencies.
│                    Zone maths, pada logic, rule evaluation, scoring, defects.
├── rules/           Versioned rule dataset (JSON) + loader. Pure Kotlin.
├── app/             Android application. Compose UI, platform integrations.
└── shared/          DTOs, result types shared between engine and app. Pure Kotlin.
```

**Why `engine` must have no Android plugin:** iOS follows this build. If the engine module has zero Android dependencies, the compiler physically prevents `Context`, `Bitmap`, `Resources` or any framework type leaking in. Converting it to a Kotlin Multiplatform `commonMain` source set later is then a build-file change, not a rewrite. Applying the Android plugin to this module — even "temporarily" — is a defect.

Concretely: `engine/build.gradle.kts` applies `kotlin("jvm")` only. No `com.android.library`. Use `kotlin.time`, `kotlinx-serialization`, and plain Kotlin types throughout. No `java.time` in engine public API (use epoch millis or `kotlinx-datetime`) so the KMP move stays clean.

### 3.2 Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose (Material 3) | Compose Multiplatform later for iOS |
| Min SDK | 26 (Android 8.0) | Covers >95% of Indian devices in use |
| Target SDK | Latest stable at build time | Google mandates annual bumps to stay listed |
| Backend | Supabase | Postgres, Storage, anonymous auth |
| AI inference | Groq | Assistant + vision plan reading |
| Payments | Razorpay | Android SDK |
| Local DB | Room | Plans, analyses, cached reports |
| Async | Coroutines + Flow | |
| DI | Hilt | |
| Serialization | kotlinx-serialization | |

### 3.3 Offline-first — hard requirement

**The engine runs entirely on-device. Computing a score must never require a network call.** Reasons: it is instant, it is free (no per-analysis inference cost), and it works in a half-built house with no signal.

Network is required only for: AI-assisted plan reading, the AI assistant, remedy store, payments, and sync. The core value — plan → North → score → defect report → layout changes — works fully offline.

---
## 4. The Vastu engine — algorithm specification

This is the heart of the product. Specify it precisely; everything else is UI around it.

### 4.0 Coordinate system — read before any geometry

All engine geometry uses a single convention. Getting this wrong mirrors every zone assignment.

```kotlin
/** Plan-space point. X increases EAST (right). Y increases NORTH (up).
 *  Units are arbitrary and self-consistent within one plan (the engine is
 *  scale-free). Android UI works in y-down screen space and MUST convert
 *  at the boundary — the engine never sees screen coordinates. */
data class Point(val x: Double, val y: Double)
```

- Angles are **degrees clockwise from North**, 0..359. North = 0, East = 90, South = 180, West = 270.
- `northOffsetDegrees` is the bearing of true North measured clockwise from the plan's "up" direction.
- To bring a plan into true-North alignment, rotate every point by `-northOffsetDegrees` **clockwise**, i.e. counter-clockwise by that amount in standard math convention:
  ```
  x' = x·cos(θ) − y·sin(θ)
  y' = x·sin(θ) + y·cos(θ)      where θ = +northOffsetDegrees in radians
  ```
- **Rotation origin** is the *area centroid of the footprint polygon* (not the bounding-box centre, not the vertex mean). Specified because they differ on the L-shaped footprints §4.1 requires.

### 4.1 Inputs

```kotlin
data class Plan(
    val id: String,
    val propertyType: PropertyType,
    val intent: Intent,
    val levels: List<Level>,            // ground floor is index 0
    val site: Site?,                    // null when the user only has a unit plan
    val northOffsetDegrees: Int         // 0..359
)

data class Level(
    val index: Int,
    val outline: List<Point>,           // footprint polygon, any orientation
    val rooms: List<Room>,
    val doors: List<Door>,
    val fixtures: List<Fixture>         // see 4.1.1 — enables the non-room defects
)

data class Room(
    val id: String,
    val type: RoomType,
    val polygon: List<Point>            // must support L-shapes and non-convex
)

data class Door(
    val id: String,
    val centre: Point,
    val wallStart: Point,
    val wallEnd: Point,
    val isMainEntrance: Boolean         // exactly one per Level 0 must be true
)

/** Idealised reference rectangle for cut/extension detection. See 4.2.7. */
data class Site(
    val plotOutline: List<Point>,
    val roads: List<Road> = emptyList(),
    val trees: List<Tree> = emptyList()
)
```

#### 4.1.1 Fixtures — required for 5 of the 13 defects

Several shipped defects cannot be computed from room polygons alone. They need explicit fixtures:

```kotlin
enum class FixtureType {
    OVERHEAD_TANK, UNDERGROUND_WATER, BOREWELL, SEPTIC_TANK,
    STAIRCASE_RUN, BED, KITCHEN_PLATFORM, HEAVY_TREE, CEILING_BEAM, MIRROR
}
data class Fixture(
    val id: String,
    val type: FixtureType,
    val position: Point,
    val facingDegrees: Int? = null,     // BED head direction, platform orientation
    val underRoomId: String? = null     // for "toilet/store beneath a staircase"
)
```

**Capture rule:** fixtures are **optional user input**. If a fixture is absent, the corresponding defect is **not evaluated and not counted as passing** — the report says "not assessed" rather than "clear." Silently treating missing input as absence of a defect is a correctness bug and would inflate every score.

### 4.2 Zone computation — the 81-pada grid

```
1. Rotate all geometry by northOffsetDegrees about the footprint area
   centroid (see 4.0). The grid is ALWAYS cardinally aligned and NEVER
   tilted — a building sitting diagonally on its plot sits diagonally
   inside a square, north-aligned grid. This is the classical position
   (Samarangana Sutradhara): the mandala is never rotated.

2. Compute the smallest cardinally-oriented bounding rectangle of the
   rotated outline. Call this the ANALYSIS RECTANGLE.

3. Divide it into 9 x 9 = 81 equal padas. Index [row][col], row 0 = North,
   col 0 = West.

4. Map padas to zones:
      rows 0-2, cols 0-2  -> NW      rows 0-2, cols 3-5  -> N
      rows 0-2, cols 6-8  -> NE      rows 3-5, cols 0-2  -> W
      rows 3-5, cols 3-5  -> BRAHMASTHAN (9 padas)
      rows 3-5, cols 6-8  -> E        rows 6-8, cols 0-2  -> SW
      rows 6-8, cols 3-5  -> S        rows 6-8, cols 6-8  -> SE

5. For each room, compute overlap AREA with every pada.

6. Assign zone per the room rule's configured strategy (4.2.6).

7. Detect cuts and extensions against the REFERENCE RECTANGLE (4.2.7).
```

#### 4.2.6 Zone assignment — strategy, ties and thresholds

```kotlin
enum class ZoneAssignmentStrategy { CENTROID, LARGEST_OVERLAP, ANY_ENCROACHMENT }
```

| Strategy | Rule |
|---|---|
| `CENTROID` | The room's area centroid decides |
| `LARGEST_OVERLAP` | Zone holding the most room area wins |
| `ANY_ENCROACHMENT` | Any overlap above the threshold counts |

**Per-rule configuration.** `RoomRule` carries `positiveStrategy` and `defectStrategy` fields (§5). Defaults, pending the M-10 ruling: `defectStrategy = ANY_ENCROACHMENT`, `positiveStrategy = LARGEST_OVERLAP`. This mirrors observed practitioner behaviour.

**Encroachment threshold — mandatory, not optional.** `ANY_ENCROACHMENT` triggers only when overlap area ≥ **2% of the room's own area** AND ≥ **0.5% of the analysis rectangle area**. Without this, a shared boundary line (measure-zero overlap) or a 0.1% clip triggers a MAJOR defect. Both thresholds are config values.

**Tie-breaking for `LARGEST_OVERLAP`** — deterministic, in order: (1) larger area wins; (2) if within 1% of each other, the zone containing the room's centroid wins; (3) if still tied, the more sensitive zone wins, by fixed precedence `BRAHMASTHAN > NE > SW > SE > N > E > S > W > NW`. Ties must never depend on iteration order.

#### 4.2.7 Cuts and extensions — the reference rectangle

A cut or extension is only meaningful against an **idealised** rectangle, not the analysis rectangle (nothing can protrude beyond its own bounding box — that construction is circular).

```
REFERENCE RECTANGLE = the largest cardinally-oriented rectangle that can be
inscribed in the footprint outline, expanded on each side to the modal edge
position of that side. In practice, for the common case of a rectangle with
one corner missing or one wing added:
  - take the four extreme edges of the outline
  - for each side, if >= 60% of the outline's edge length on that side lies
    at one offset, that offset is the reference edge
  - regions inside the reference rectangle but outside the footprint = CUT
  - regions outside the reference rectangle but inside the footprint = EXTENSION
```

Where no modal edge exists (a genuinely irregular polygon), the engine reports `shapeIrregular = true` and **skips cut/extension analysis entirely** rather than guessing. This is surfaced honestly in the report.

**Thresholds (L-08 — consultant heuristics, no textual basis, all config):** an anomaly counts when its area ≥ **10%** of the reference rectangle area; between **5% and 10%** it is reported at `Severity.MINOR`; below 5% it is ignored. The knowledge base's "exceeds half the plot side" formulation is ambiguous about which dimension and is deliberately not implemented.

**Multi-zone anomalies:** a cut or extension spanning several zones produces one `ZoneAnomaly` **per zone touched**, each carrying its own area share. The report groups them.

### 4.3 Door computation — the 32-pada method

The entrance is the highest-weighted element in the product. It is computed angularly, not on the grid. This hybrid is open question M-07.

**4.3.1 Convention — fixed here, since the sources disagree (P-07).**
Padas are numbered **1–32 clockwise starting at true North**, walking N → E → S → W. Pada *k* spans bearing `[(k−1)×11.25°, k×11.25°)` measured clockwise from North. Side-relative ids (N1..N8, E1..E8, S1..S8, W1..W8) map as: N1..N8 = 1..8 is **wrong** — the north wall straddles North, so:

```
Bearing 348.75°..360° and 0°..78.75°  -> N1..N8   (N1 begins at 348.75°)
Bearing  78.75°..168.75°              -> E1..E8
Bearing 168.75°..258.75°              -> S1..S8
Bearing 258.75°..348.75°              -> W1..W8
```
So **N1 sits at the north-west end of the north wall** and numbering runs clockwise. This convention must be written into a code comment and shown in the report's methodology note, because reviewers from different schools count differently.

**4.3.2 Locating the door.**
```kotlin
enum class DoorLocationMethod { BEARING_FROM_CENTRE, PROPORTION_ALONG_WALL }
```
- `BEARING_FROM_CENTRE` (**default**): bearing = `atan2(dx, dy)` in degrees clockwise from North, from the analysis-rectangle centre to the door centre; normalise to 0..359; resolve to pada by the table above.
- `PROPORTION_ALONG_WALL`: determine which side the door's wall belongs to; divide that wall into 8 equal linear segments; the segment index gives the pada.

These give different answers on a non-square plan. **The earlier "divide the side into 8, each spanning 11.25°" formulation was wrong** — equal linear division does not produce equal angles from the centre (on a square plan the outermost eighth subtends ~8.1° and the innermost ~14.0°). The two methods are genuinely different algorithms and are implemented separately.

**4.3.3 Corner and edge cases.**
- A door whose centre falls within 1.0° of a pada boundary is reported as **spanning both**, and takes the *less* auspicious of the two.
- **Corner doors:** the SW corner (P-06, the one near-universal prohibition) is defined as bearing `213.75°..236.25°` — i.e. padas S6, S7, S8, W1, W2. A door anywhere in that arc raises X-06 at MAJOR regardless of which wall owns it.
- **Re-entrant walls** (L-shaped footprints where the door sits on a wall touching no side of the analysis rectangle): fall back to `BEARING_FROM_CENTRE`, which is always defined.
- **Multiple doors:** only the door with `isMainEntrance = true` is scored. Other doors are recorded but produce no verdict. If no door is flagged, the engine returns `doorResult = null` and the report says the entrance was not assessed.

**Absolute rule:** the engine never scores the building's facing direction. Only the main door's pada. See §0.4.

### 4.4 Room evaluation

For each room, look up its rule (§8.2) and produce a verdict:

| Verdict | Condition |
|---|---|
| `IDEAL` | Zone ∈ rule.ideal |
| `ACCEPTABLE` | Zone ∈ rule.acceptable |
| `DEFECT` | Zone ∈ rule.prohibited |
| `SUBOPTIMAL` | Zone in none of the three, **and** the rule is not `DISPUTED` |
| `NOT_SCORED` | The rule is `DISPUTED` (§4.4.1), or the room type has no rule |

**4.4.1 Disputed rules must not be scored.**
A rule whose `disputeId` is non-null (currently POOJA / W-12) is **excluded from the score entirely** — it contributes nothing to numerator or denominator. It appears in the report showing both readings, marked "schools disagree," and is never coloured as a defect.

> This corrects a real bug in the previous draft: POOJA had empty ideal/acceptable/prohibited sets, which resolved to `SUBOPTIMAL` = 45 points. Every plan containing a pooja room was silently penalised, contradicting §0.3 and §8.5 W-12. `BASEMENT` had the same defect and is now `NOT_SCORED` pending a ruling.

**4.4.2 Room types with no rule.** `BATHROOM`, `COURTYARD`, `UTILITY`, `CORRIDOR` have no placement rule. They evaluate to `NOT_SCORED`, contribute nothing to the score, and are listed in the report under "not assessed." **`BATHROOM` is deliberately distinct from `TOILET`** — R-06 covers the WC. The combined bath-plus-WC common in Indian homes should be entered as `TOILET`; the UI must say so at the point of selection.

**4.4.3 Prototype correction.** The prototype awards 50/100 to any unlisted zone, so silence reads as mediocre and inflates scores. Here `SUBOPTIMAL` is explicitly derived, and the report states the tradition is silent on that placement rather than implying a middling judgement.

### 4.5 Scoring — a product invention, and labelled as such

The 0–100 score has **no basis in any classical text**. No text ranks rooms numerically. The weights are ours, and the knowledge base excludes them from expert review by design.

```
base   = Σ(points_i × weight_i) / Σ(weight_i)     over all SCORED elements
score  = clamp(round(base − defectPenalty), 0, 100)

points:  IDEAL 100 · ACCEPTABLE 70 · SUBOPTIMAL 45 · DEFECT 10
         NOT_SCORED elements are excluded from BOTH sums.
```

**4.5.1 The door contributes to the base sum.** `PadaVerdict` maps into the same points scale, at the ENTRANCE weight of 3.0 — the highest of any element:

| PadaVerdict | Points |
|---|---|
| `AUSPICIOUS` | 100 |
| `MODERATE` | 60 |
| `MIXED` | 50 |
| `INAUSPICIOUS` | 15 |

`MIXED` is a real fourth value (W5 Varuna is described as "Mixed" in the sources) and belongs in the enum.

**4.5.2 Defect penalties.** Structural defects (cuts, extensions, fixture placement, Brahmasthan violations) are not room verdicts and would otherwise contribute nothing:

```
defectPenalty = Σ over raised structural defects:
      MAJOR 8 · MODERATE 4 · MINOR 1        (capped at 30 total)
```
Without this, a plan with a missing North-East corner scores identically to an intact one. Penalty values are config.

**4.5.3 Requirements.**
- Score computes in **under 100 ms** on a mid-range device (cold path).
- While the North dial is being dragged, the UI must stay at 60 fps. This is achieved by **debouncing recomputation to at most every 50 ms and computing off the main thread**, not by requiring a 16 ms engine. The previous draft demanded both 100 ms and 60 fps, which are inconsistent; this is the reconciliation.
- An information affordance explains, in plain language, that the number is the app's own construction and not traditional.
- The score is never described as an accuracy, probability, or prediction.

### 4.6 Defect detection

A false positive damages trust more than a missed defect. Prefer fewer, accurate defects.

**Computability tiers — every defect declares what input it needs:**

| Tier | Needs | Defects |
|---|---|---|
| A | Room polygons only | X-01, X-02, X-03, X-06, X-07, X-08, X-10 |
| B | Footprint + reference rectangle | X-04, X-05 |
| C | Optional `Fixture` input | X-09, X-12, X-13 |
| D | Optional `Site` input | X-11 |

**Tier C and D defects are evaluated only when their input is present.** When absent, the report shows "not assessed" — never "clear." A user who did not enter a septic tank has not thereby passed the septic tank rule.

**Defect id coverage.** Every `(RoomType, prohibited Zone)` pair must resolve to a defect id, severity and remedy set. Where §8.4 names no specific id (e.g. kitchen in SW, which R-02 prohibits but which has no X-entry), the engine raises a **generic** `X-GEN` defect at `MODERATE`, using the room rule's own text. Silent `DEFECT` verdicts with no defect record are a bug.


### 4.7 School profiles

The app supports multiple systems. **These are different geometries, not settings on one geometry** — the 16-zone model is angular at 22.5° and is not a refinement of the 81-pada grid. Offering both requires running two independent evaluations, not reinterpreting one.

| Profile | Geometry | Status |
|---|---|---|
| `TRADITIONAL_8` | 81-pada square grid, 8 zones + centre | Default |
| `SIXTEEN_ZONE` | 16 angular zones at 22.5° | Independent evaluator |
| `FORTY_FIVE_DEVATA` | 45 devata padas | Independent evaluator |

Where profiles conflict, the app **shows both readings side by side and does not pick a winner**. See §8.5.

> **Flag for the product owner:** knowledge base entry M-11 concludes the 16-zone system is a genuinely incompatible geometry and asks reviewers whether presenting both in one app is honest or merely confusing. That question is unresolved. Build the profile abstraction now; treat shipping `SIXTEEN_ZONE` and `FORTY_FIVE_DEVATA` as gated on that ruling.


---
## 5. Data model

```kotlin
enum class Zone { N, NE, E, SE, S, SW, W, NW, BRAHMASTHAN }
enum class Provenance { TEXT, DERIV, MOD, DISP }
enum class Intent { BUILDING, BUYING, LIVING }
enum class PropertyType { INDEPENDENT_HOUSE, FLAT }
enum class Verdict { IDEAL, ACCEPTABLE, SUBOPTIMAL, DEFECT, NOT_SCORED }
enum class PadaVerdict { AUSPICIOUS, MODERATE, MIXED, INAUSPICIOUS }
enum class Severity { MAJOR, MODERATE, MINOR }
enum class FixKind { MOVE_IT, REMEDY_IT, RITUAL }
enum class SchoolProfile { TRADITIONAL_8, SIXTEEN_ZONE, FORTY_FIVE_DEVATA }
enum class AnomalyKind { CUT, EXTENSION }

enum class RoomType {
    ENTRANCE, KITCHEN, MASTER_BEDROOM, BEDROOM, POOJA, TOILET, BATHROOM,
    LIVING, DINING, STAIRCASE, STUDY, STORE, GUEST_BEDROOM, GARAGE,
    BALCONY, BASEMENT, COURTYARD, UTILITY, CORRIDOR
}

data class RoomRule(
    val roomType: RoomType,
    val weight: Double,
    val ideal: Set<Zone>,
    val acceptable: Set<Zone>,
    val prohibited: Set<Zone>,
    val provenance: Provenance,
    val sourceId: String,                       // "R-02" -> knowledge base
    val citation: String? = null,               // only when provenance == TEXT
    val note: String? = null,
    val disputeId: String? = null,              // non-null => NOT_SCORED (4.4.1)
    val positiveStrategy: ZoneAssignmentStrategy = LARGEST_OVERLAP,
    val defectStrategy: ZoneAssignmentStrategy = ANY_ENCROACHMENT
)

data class DoorPada(
    val id: String,                             // "N3"
    val ordinal: Int,                           // 1..32 clockwise from North
    val name: String?,                          // null where sources disagree
    val side: Zone,
    val startBearing: Double,                   // degrees clockwise from North
    val verdict: PadaVerdict,
    val domain: String?,                        // null where sources give none
    val provenance: Provenance = Provenance.DERIV
)

data class Dispute(
    val id: String,                             // "W-12"
    val title: String,
    val readingA: DisputeReading,
    val readingB: DisputeReading,
    val appliesTo: RoomType? = null,
    val appliesToZone: Zone? = null
)
data class DisputeReading(val label: String, val text: String, val school: String?)

data class Remedy(
    val id: String,
    val kind: FixKind,
    val text: String,
    val provenance: Provenance,                 // MOD for gadgets, TEXT for shanti
    val rank: Int,                              // lower = shown first (7.2)
    val productSku: String? = null              // links to remedy store
)

data class Defect(
    val id: String,                             // "X-01" or "X-GEN"
    val severity: Severity,
    val zone: Zone,
    val roomId: String?,
    val fixtureId: String?,
    val ruleSourceId: String,
    val provenance: Provenance,
    val explanation: String,
    val layoutFix: String?,                     // null when nothing can move
    val remedies: List<Remedy>
)

data class RoomResult(
    val roomId: String, val type: RoomType, val zone: Zone,
    val verdict: Verdict, val points: Int, val weight: Double,
    val rule: RoomRule, val padaOverlap: Map<Pair<Int,Int>, Double>
)
data class DoorResult(
    val doorId: String, val pada: DoorPada, val bearing: Double,
    val verdict: PadaVerdict, val points: Int, val spansTwoPadas: Boolean
)
data class ZoneAnomaly(
    val kind: AnomalyKind, val zone: Zone,
    val areaShare: Double, val severity: Severity
)

data class Analysis(
    val planId: String,
    val intent: Intent,                         // report branches on this (§2)
    val propertyType: PropertyType,
    val northOffsetDegrees: Int,
    val schoolProfile: SchoolProfile,
    val score: Int,
    val defectPenalty: Int,
    val roomResults: List<RoomResult>,
    val doorResult: DoorResult?,                // null when no main door flagged
    val defects: List<Defect>,                  // sorted severity, then weight
    val cuts: List<ZoneAnomaly>,
    val extensions: List<ZoneAnomaly>,
    val shapeIrregular: Boolean,                // cut/extension skipped (4.2.7)
    val notAssessed: List<String>,              // rules skipped for missing input
    val disputes: List<Dispute>,                // relevant to THIS plan only
    val ruleSetVersion: String                  // e.g. "2026.07.19-1"
)
```

**`ruleSetVersion` is mandatory on every stored analysis.** When expert rulings change the dataset, past reports must remain explicable. Never silently re-score a saved analysis under new rules — offer the user a re-run.

### 5.1 Rule dataset JSON schema

§0.8 makes "rules are data, not code" a non-negotiable, so the dataset needs a real schema. Files live in `rules/src/main/resources/ruleset/`:

```
ruleset/
  meta.json          { "version": "2026.07.19-1", "kbDraft": "2.0" }
  zones.json         9 entries: zone, deity, element, domain, sourceId
  rooms.json         RoomRule[]
  doorPadas.json     DoorPada[] — exactly 32
  defects.json       DefectDefinition[]
  disputes.json      Dispute[]
  remedies.json      Remedy[]
  config.json        strategies, thresholds, penalty weights, score points
```

`config.json` holds every tunable named in §4 — encroachment thresholds, tie-break precedence, anomaly thresholds, defect penalties, score points, Brahmasthan extent, door location method. **No value listed in §4 may be a Kotlin constant.**

Loader requirements: validate on load (32 padas present and contiguous, every `RoomType` either has a rule or is explicitly listed as unruled, every `disputeId` resolves, every defect has ≥1 remedy); fail loudly at startup rather than silently mis-scoring.

---
## 6. Screens

Flow: **Welcome → Add home → Mark North → Score (free) → Full report (paid)**

### 6.1 Welcome
- Brand mark, one-line promise: *"Fix your home's Vastu on paper — before you build."*
- Explicit reassurance: **No sign-up · No phone number**
- ~~Language picker: English, हिन्दी, தமிழ், తెలుగు, मराठी, বাংলা~~ — **CANCELLED 9 Aug 2026. There is no language control on this screen and no note about language. See §7.5.**
- Intent picker (§2) — three options, required before Continue
- Continue is disabled until intent is chosen

### 6.2 Add home
Three paths:

**(a) Guided grid — the primary and always-available path.** User places room blocks on a grid and labels each by type. Must be usable one-handed on a phone. This path requires no AI, no network, and never fails.

**(b) AI-assisted plan reading.** User photographs or uploads a plan (JPEG/PNG/PDF). A vision model drafts room rectangles and types. **The user is then shown every detected room and must confirm or correct each one before any score is computed.** Rooms the model is unsure about are flagged for attention, not silently accepted. If the model fails or returns low confidence, fall back to (a) without an error state — "we couldn't read this cleanly, let's place the rooms together."

**(c) Sample plans.** Two bundled examples for evaluation without uploading anything.

Also captured here: `PropertyType` (independent house / flat), because flats change what the report can offer (§7.4).

### 6.3 Mark North
The signature interaction. Everything downstream keys off it.

- Draggable compass dial on the plan, plus a slider, plus numeric degree entry
- Quick-set chips: N / E / S / W
- Live score updates continuously while dragging
- Optional: read from device magnetometer, with magnetic declination correction, a calibration prompt, and a visible accuracy indicator. **Secondary by design** — most users are reading an architect's PDF at home, where a compass cannot help, and you cannot stand inside a house that has not been built. Phones without a magnetometer get a sun-and-shadow fallback.
- The centre point of the dial stays visually clean — no logo, no watermark over it. This is the single most-complained-about flaw in the leading rival.

**Must NOT include any "find the best angle" affordance.** See §0.7.

### 6.4 Score — free
- Large 0–100 number, colour-coded (≥75 green, ≥50 amber, below red)
- Colour-coded zone map of the plan
- Named list of the top 3 defects with plain-language explanation
- A count of remaining issues, with a clear prompt to unlock
- Tappable note explaining the score is the app's own construction, not traditional

**The free tier must feel honest and complete in itself.** No hidden wall, no bait. The count of remaining issues is shown truthfully.

### 6.5 Full report — paid
- Every issue ranked by severity, then weight
- Each item shows: what it is, which zone, the deity/element of that zone, **the provenance tag**, the plain-language reason, the **layout change**, and **remedies** for what cannot move
- Layout changes vs remedies visually distinguished — they are different kinds of advice
- "Already right — leave alone" section (positive reinforcement, and it proves the engine isn't just fault-finding)
- **"Where the schools disagree"** section, populated from disputes relevant to this specific plan — both readings shown, no winner declared
- School profile toggle
- Export to PDF and share (WhatsApp / email) — this is the moment the product becomes useful, because it goes to the architect
- Visible disclaimer: *"Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome."*

### 6.6 Provenance display — required throughout
Every rule shown to a user carries a visible, tappable tag:

| Tag | Label shown to user |
|---|---|
| `TEXT` | **From classical text** — with the citation |
| `DERIV` | **Traditional practice** — reasoned from the mandala, taught widely, not a specific verse |
| `MOD` | **Modern practice** — 20th-century, no classical basis |
| `DISP` | **Schools disagree** — both readings shown |

This is the product's core differentiator. It is not optional polish. The prototype surfaces none of it.

---

## 7. Feature specifications

### 7.1 AI Vastu assistant
- Answers questions grounded **only** in the project knowledge base — retrieval over the rule dataset, not open-ended generation
- Cites the rule id and provenance for every answer
- When the knowledge base is silent, it says so explicitly rather than inventing
- **Never** mixes in Feng Shui or other traditions
- **Never** makes outcome predictions or health/wealth claims
- Runs on Groq; requires network; degrades gracefully offline

### 7.2 Remedies — with honest labelling
The classical response to a defect was **ritual pacification (Vastu Shanti) and structural correction** — not an object placed in a corner. There appears to be no classical concept of a non-demolition gadget remedy at all.

Pyramids, rock salt bowls, copper strips, crystals, wind chimes and directional colours have **no classical basis**; they are 20th-century commercial practice. Money plant and lucky bamboo are modern imports, largely from Feng Shui. Tulsi is genuinely traditional.

**Requirement:** ship gadget remedies (users expect them, rivals sell them) but tag every one `MOD`, and **rank layout change and Vastu Shanti above them** in every report. Never imply efficacy. Never imply antiquity.

### 7.3 Remedy store
Products matched to the user's actual defects, not a generic catalogue. Razorpay checkout. Clean and ad-light — not a marketplace dump.

### 7.4 Flats and apartments
Two levels of analysis: the building read as one plot, and the individual flat read as its own unit with its own mandala. The consultant consensus is that **the flat's own door — from the common lobby — matters more than the building gate** for the people living in that flat, so the 32-pada analysis applies to the flat door.

**The honest constraint:** a flat owner cannot move the building gate, the lift, the shafts or the shared walls. For flats the app can largely only *report*, not recommend layout change. Report copy must reflect this rather than suggesting impossible fixes.

> **Open question A-03, unresolved:** whether Vastu offers a flat buyer anything meaningful beyond "choose a different flat." Build the flat path; treat its report copy as provisional until that ruling lands.

### 7.5 Localisation — ⛔ CANCELLED (owner decision, 9 Aug 2026)

**VastuFirst ships in English only, permanently.** Not "English for now", not "more languages later" — there is no phase that adds one, and nothing in the product may imply otherwise: no language picker, no "soon" pill, no note on any screen, no roadmap line.

~~Six languages: English, Hindi, Tamil, Telugu, Marathi, Bengali — in native scripts. These are not UI strings alone; rule explanations, defect descriptions and remedy text are the bulk of the content and must be translated by someone Vastu-literate.~~

**Why it was cancelled, kept because it is the honest reason:** the bulk of this product's words are rule explanations, and translating those needs a Vastu-literate human translator per language, not a machine — a real cost and a real hiring problem for a product still finding its first customers. Machine-translated rule text would have been worse than English.

**What this does NOT cancel — do not "tidy" these away:**
- **Number formatting follows the phone, not the app.** The decimal mark is read from the device's own locale data, so a phone set to a comma language writes 6,8. That is the device's setting and has nothing to do with translating VastuFirst.
- **Never pin a text container's height.** That rule was written for Indic line-heights but it earns its place on font scale 2.0 alone.
- **Sanskrit and Vastu vocabulary stay.** Deity names, element names, zone names and provenance are the product, not localisation.

---
## 8. Rule data

This section is the seed dataset. Ship it as versioned JSON loaded by the `rules` module. Every entry carries a `sourceId` tracing to the knowledge base.

### 8.1 Directional framework — the classical core (all `TEXT`)

This is the genuinely classical layer. Everything downstream is reasoned from it, which is why it must be right.

| Zone | Sanskrit | Deity | Element | Domain | Source |
|---|---|---|---|---|---|
| N | Uttara | Kubera | — (water-linked) | Wealth, career | D-01 |
| NE | Ishanya | Ishana / Shiva | Water (Jal) | Clarity, health. **The Purusha's head. Most sensitive and most auspicious zone.** | D-02 |
| E | Purva | Indra | — (sun-linked) | Vitality, social standing | D-03 |
| SE | Agneya | Agni | Fire | Energy, transformation, finances | D-04 |
| S | Dakshina | Yama | — (earth-linked) | Rest, fame, discipline | D-05 |
| SW | Nairritya | Nirriti / Nairrta | Earth (Prithvi) | Stability, bonds. **The Purusha's feet. The heaviest zone.** | D-06 |
| W | Paschima | Varuna | — (water-linked) | Gains, savings, learning | D-07 |
| NW | Vayavya | Vayu | Air | Movement, communication, transience | D-08 |
| Centre | Brahmasthan | Brahma | Space (Akasha) | **Must stay open.** Manasara: unfit for building, reserved for the family deity. | D-09 |

**D-10 — weight and height principle (`DERIV`, one of the most consistent rules across every school):** North-East kept lightest, lowest, most open; South-West heaviest and highest. Ground slopes toward the NE; water exits NE / N / E. Has a defensible physical rationale — drainage, plus morning sun on the lower side.

**Mandala facts (`TEXT`):** The Vastu Purusha lies face-down, head to the North-East, feet to the South-West, knees and elbows at South-East and North-West (Brihat Samhita 53.2-3). The 81-pada 9×9 Paramasayika grid is the residential model (Brihat Samhita 53.42). The 64-pada Manduka is for temples and does not apply. 45 devatas: Brahma holds the central 9 padas, 32 outer padadevatas hold one pada each, 12 inner devas hold 2–9 each (1 + 12 + 32 = 45).

### 8.2 Room placement rules

**Every rule in this table is `DERIV`** — not because they are doubted, but because they could not be found stated as verses in Mayamata, Manasara or Brihat Samhita. They are reasoned from the mandala's deity-directions and taught by virtually every practitioner. Do not present them as scripture.

| Room | Weight | Ideal | Acceptable | Prohibited | Source | Note |
|---|---|---|---|---|---|---|
| ENTRANCE | 3.0 | *governed by 32-pada, not zone* | — | — | R-01 | **Facing direction carries no score.** See §4.3 |
| KITCHEN | 3.0 | SE | NW | NE, SW, Brahmasthan | R-02 | North contested — see W-02 |
| MASTER_BEDROOM | 3.0 | SW | S, W | NE, SE, Brahmasthan | R-03 | SE bedroom is the one all sources warn against |
| BEDROOM | 1.5 | W, NW | N, E, S | SE, Brahmasthan | R-04 | |
| POOJA | 2.0 | **DISPUTED** | — | — | R-05 | Classical = Brahmasthan (centre). Modern = NE. See §8.5 |
| TOILET | 2.5 | NW, W | S | NE, SW, Brahmasthan | R-06 | East contested — W-01. **No classical verse exists; texts predate indoor sanitation** |
| LIVING | 1.5 | N, E, NE | W, NW | SW, Brahmasthan | R-07 | |
| STAIRCASE | 2.0 | S, SW, W | NW | NE, Brahmasthan | R-08 | |
| STUDY | 1.0 | W, NW | N, E, NE | S, SW | R-09 | User faces E or N when working |
| DINING | 1.0 | W | N, E | — | R-10 | |
| STORE | 0.8 | SW, W, S | — | NE | R-11 | |
| GUEST_BEDROOM | 0.8 | NW | W | — | R-12 | |
| GARAGE | 0.8 | NW, SE | — | NE, SW | R-13 | |
| BALCONY | 0.8 | N, E, NE | — | SW | R-14 | |
| BASEMENT | 0.8 | — | — | — | R-15 | `MOD` — traditionally discouraged entirely; MahaVastu permits in S and W plots |

**Building elements (all `DERIV` unless marked):** underground water/well/borewell → NE, N or E; avoid SW, SE, centre (B-01). Overhead tank → SW or W; avoid NE, SE, centre (B-02). Septic tank → **genuine conflict, see W-09** (B-04). Staircase ascends clockwise, ideally E→W or N→S (B-06); odd number of steps (B-07). Kitchen platform: cook faces East (B-10). Bed: head to South (best) or East — **see W-05** (B-11). **Brahmasthan carries no weight: no staircase, no toilet, no pillar, no heavy fixture (B-12 — `TEXT`, Manasara).** More openings on N and E than S and W (B-13).

### 8.3 The 32 door padas

Each pada spans 11.25°, eight per side. **Roughly nine or ten of the thirty-two are auspicious — which is why direction alone was never enough.** Framework traced to Samarangana Sutradhara's *dvaravinyasa*. All `DERIV`.

**NORTH (N1–N8)** — Auspicious: **N3 Mukhya**, **N4 Bhallat**, **N5 Soma** (wealth, ancestral gain, prosperity). Moderate: N8 Diti. Inauspicious: N1 Roga, N2 Naga, N6 Sarpa/Bhujag, N7 Aditi.

**EAST (E1–E8)** — Auspicious: **E3 Jayanta**, **E4 Indra/Mahendra** (success, authority, official favour). Inauspicious: E1 Shikhi (fire, accident), E2 Parjanya, E5 Surya/Bhrisha, E6 Satya (loss, theft), E7, E8 Antariksha.

**SOUTH (S1–S8)** — Auspicious: **S3 Vitatha**, **S4 Gruhakshata** (fame, prosperity). Moderate: S2 Pusha. Inauspicious: S1 Anila, S5 Yama (death, disease), S6 Gandharva, S7, S8 Mriga.

**WEST (W1–W8)** — Auspicious: **W3 Sugriva**, **W4 Pushpadanta** (wealth, growth, Kubera). Mixed: W5 Varuna. Inauspicious: W1 Pitra (poverty), W2 Dauwarik/Dwarika, W6 Asura, W7 Shosha, W8 Papayakshma.

**P-06 — the one near-universal prohibition:** the South-West corner entrance (Pitra / Nairrti zone) is condemned across every school examined.

**P-07 — naming and counting vary between sources.** Both pada names and where the count begins differ across translations and schools. Fix one convention — **compass degrees from true North** — and document it in the code. This is where two expert reviewers are most likely to disagree with each other.

### 8.4 Defects the engine flags

| ID | Defect | Severity | Provenance |
|---|---|---|---|
| X-01 | Toilet in the North-East | MAJOR | DERIV — held most serious across every school examined |
| X-02 | Kitchen in the North-East | MAJOR | DERIV — fire in the water quarter |
| X-03 | Staircase or heavy weight in the Brahmasthan | MAJOR | **TEXT** |
| X-04 | Cut or missing North-East corner | MAJOR | DERIV |
| X-05 | South-West extension | MAJOR | DERIV |
| X-06 | Main entrance on an inauspicious pada (esp. SW corner / Pitra) | MAJOR | DERIV |
| X-07 | Master bedroom in the North-East or South-East | MODERATE | DERIV |
| X-08 | Toilet in the South-West | MODERATE | DERIV |
| X-09 | Overhead tank in NE / underground water in SW | MODERATE | DERIV |
| X-10 | Pooja sharing a wall with a toilet; toilet or store beneath a staircase | MODERATE | DERIV |
| X-11 | Veedhi Shoola from S, SW, SE or NW | MODERATE | **DISP** — see W-10 |
| X-12 | Heavy trees in the NE or centre | MINOR | DERIV |
| X-13 | Mirror facing bed / beam over bed or desk | MINOR | **MOD** — no classical basis found for either |

**X-14 — explicitly removed:** "south-facing house" is **no longer a defect** and must not be flagged. See §0.4.

**Plot-level rules:** square or rectangular plots auspicious, aspect ratio within 1:2 (L-01). NE extension is the most auspicious of all; N and E extensions generally beneficial (L-05). SW extension is a serious defect; SE extension = excess fire; NW extension = restlessness (L-06). A cut in the NE is a major defect (L-07). Extension/cut thresholds (L-08) are **consultant heuristics with no textual basis** — treat as configurable, currently: counts as a true extension or cut if it exceeds half the plot side; under ~5–10% of area treat as minor.

### 8.5 Disputes — show both, never resolve

The app shows both readings and lets the user pick a school profile. All `DISP`.

| ID | Dispute | Reading A | Reading B |
|---|---|---|---|
| W-01 | Toilet in the East | A defect — East is the sun's quarter | Acceptable in specific east-of-SE / ESE sub-zones |
| W-02 | Kitchen in the North | Not ideal — Kubera and water against fire | Merely suboptimal, not a hard defect |
| W-03 | Master bedroom in the West | Acceptable heavy-zone alternative to SW | 16-zone reads WSW as education zone; advises against |
| W-04 | Main entrance in the South-East | Acceptable on the right pada | A defect — Manasara says never face SE |
| W-05 | Sleeping head direction | Head-to-South best, East good, West neutral | **Head-to-North avoided — the one near-universal cross-school agreement.** The magnetic-repulsion rationale usually given is folk science, not established; a 2015 PSQI study of 153 students concluded the opposite. **State as tradition; make no health claim.** |
| W-06 | Idol facing | Idols on W/SW wall, worshipper faces East | Idols on E wall facing West |
| W-09 | Septic tank direction | N vs NW vs SE — sources contradict outright | **One of the sharpest conflicts found. Do not hard-code.** |
| W-10 | Veedhi Shoola (road thrust) | Directional — NE/N/E benign, S/SW/SE/NW harmful | Universal — any road-arrow wounds the plot |
| W-11 | Extensions | NE extension auspicious | All extensions negative, including NE |
| W-12 | **Pooja location** | **Classical — Brahmasthan (centre), per Manasara** | **Modern near-universal practice — NE** |

**W-12 requires special handling.** The prototype hard-codes NE as ideal *and marks the centre as a defect* — penalising the classical position. Both readings must be presented; neither may be scored as a defect until the expert ruling lands.

### 8.6 Remedy provenance

| Remedy | Provenance | Note |
|---|---|---|
| Vastu Shanti / Vastu Puja / Homa | **TEXT** | The genuine traditional remedy, prescribed in Mayamatam |
| Structural correction | **TEXT** | The other genuine remedy — **for a pre-construction audience this IS the product** |
| Vastu pyramids | MOD | 20th-century "pyramid power" (Bovis, 1930s) fused onto Vastu. Claims of ancient origin appear to be marketing |
| Rock / sea salt bowls | MOD | No textual source for the energy-absorption rationale |
| Copper / brass / lead strips | MOD | Modern |
| Crystals, wind chimes, mirrors | MOD | Largely modern, visible Feng Shui cross-influence |
| Yantras | MOD | A genuine older tantric lineage exists, but their use as a Vastu-defect gadget is modern-commercial |
| Directional colours | MOD | Modern systematisation mapped from each direction's element |
| Tulsi plant | DERIV | **Genuinely traditional** (unlike money plant and lucky bamboo, which are modern imports) |

---
## 9. Build phases

Phases are sliced by **dependency**, not by category — each phase's output feeds the next. Dates align to the committed client schedule.

---

### Phase 0 — Foundations
**Days 1–3 · no client-facing deliverable**

- Repo, Gradle setup, module structure per §3.1
- `engine` module created with `kotlin("jvm")` only — **verify the Android plugin is absent**
- `rules` module with JSON schema and loader
- Compose + Material 3 scaffold, navigation graph
- Supabase project, anonymous auth wired
- CI that runs engine unit tests

**Done when:** `./gradlew :engine:test` runs green, and `:engine` has zero Android dependencies in its resolved classpath.

---

### Phase 1 — The Vastu engine
**Days 3–8 · headless, fully tested, no UI**

This is the product. Build it standalone and prove it before any screen exists.

- 81-pada grid computation with north rotation (§4.2)
- All three `ZoneAssignmentStrategy` implementations, config-selectable
- Brahmasthan extent, config-selectable, default central 3×3
- 32-pada door resolution, both location methods (§4.3)
- Room evaluation producing four verdicts (§4.4)
- Scoring (§4.5)
- Defect detection over the §8.4 table
- Cut and extension detection (§4.2 step 6)
- Dispute surfacing — return conflicts relevant to the given plan
- Rule dataset loaded from JSON, versioned

**Test requirements — this phase is not done without them:**
- Known-fixture tests: a plan with a toilet in the NE must produce X-01 at MAJOR
- Rotation invariance: rotating both plan and North by the same amount must produce an identical score
- **Facing-direction neutrality: the same plan and same door pada must score identically regardless of which way the building faces.** This test directly guards §0.4
- Boundary cases: rooms straddling pada lines, L-shaped footprints, rooms exactly on the Brahmasthan edge
- Strategy-swap tests: the same plan under `CENTROID` vs `LARGEST_OVERLAP` produces the documented difference

**Done when:** the engine scores both bundled sample plans correctly, every test passes, and swapping a rule in the JSON changes the output with no recompile.

---

### Phase 2 — Android app, guided-grid path
**Days 8–14 · MILESTONE: delivered 4 August 2026**

The first client delivery. Everything needed for a real person to score a real home.

- Welcome: intent picker (§6.1) — the language picker is cancelled, see §7.5
- Add home: **guided grid path and bundled samples only** — AI reading comes in Phase 4
- Property type capture (house / flat)
- Mark North: dial, slider, numeric entry, quick chips, live score (§6.3)
- Score screen, free tier (§6.4)
- Full report screen with provenance tags and disputes (§6.5, §6.6)
- Local persistence (Room) — save and reopen plans
- English only — and, since 9 Aug 2026, English only permanently (§7.5)
- Visible legal disclaimer

**Explicitly NOT in this phase:** AI plan reading, AI assistant, payments, remedy store, iOS. *(Other languages are not deferred — they are cancelled, §7.5.)*

**Done when:** an APK is installed on the client's own phone, and she can add her own home via the guided grid, mark North, and get a score and a full report with no network connection.

---

### Phase 3 — Client testing + website
**4–18 August 2026 · two parallel tracks**

- Client tests the Android build for two weeks
- Bug fixes and UX corrections from her feedback
- Brand website built in parallel (separate track, does not block the app)

**Exit criterion — must be written down, not judged by feel:** no open P1 or P2 bugs; all five core flows pass on three physical devices. "Perfect" is not an exit criterion.

---

### Phase 4 — Reports, remedies, AI
**19–28 August 2026**

- AI-assisted plan reading with mandatory user confirmation (§6.2b)
- AI Vastu assistant, retrieval-grounded with citations (§7.1)
- Remedy content with `MOD` labelling and correct ranking (§7.2)
- ~~Six languages (§7.5)~~ — **CANCELLED 9 Aug 2026. English only, permanently.**
- Flat and apartment analysis (§7.4)
- `SIXTEEN_ZONE` and `FORTY_FIVE_DEVATA` profiles — **gated on the M-11 expert ruling**
- PDF export and share

---

### Phase 5 — iOS, payments, store
**31 August – 8 September 2026**

- Convert `engine`, `rules`, `shared` to Kotlin Multiplatform `commonMain` — build-file change, no rewrite, because §3.1 was followed
- Compose Multiplatform iOS UI
- Codemagic pipeline, TestFlight
- Razorpay integration
- Remedy store

**Note:** requires an Apple Developer account in the client's name and a physical iPhone for QA. The simulator will not catch what ships.

---

### Phase 6 — Launch
**14–20 September 2026**

- Legal: disclaimer, privacy policy, terms, refund policy, DPDP consent flow
- Play Store and App Store listings, data safety declarations, content ratings
- Both apps submitted, website live

**Apple review takes 1–3 days and is outside our control. Submit early.**

---

## 10. Non-functional requirements

| Area | Requirement |
|---|---|
| Performance | Score computes < 100 ms; North dragging holds 60 fps |
| Offline | Full core loop works with no network (§3.3) |
| Accessibility | TalkBack labels on the North dial and score; minimum 44 dp touch targets; text scales to 200% |
| Privacy | Floor plans and location are personal data under India's DPDP Act — explicit consent, clear retention, user-initiated deletion |
| Storage | Plans stored locally by default; cloud sync opt-in |
| Size | Target APK under 30 MB |
| Devices | Test on three physical Android devices before any delivery |

---

## 11. Legal and compliance

- **Visible** entertainment/informational disclaimer — not buried in settings
- No health, wealth, marriage, fertility or fortune guarantees anywhere
- Privacy policy and DPDP consent flow
- Terms and conditions
- Clear refund policy — hidden paywalls and refund trouble are the top complaints against every competitor researched
- Both stores scrutinise thin fortune-telling apps. **Apple rejects them under guideline 4.3.** The floor-plan engine and structured reports are what make this a genuine utility rather than a horoscope — write the review submission to say exactly that
- A live privacy policy URL is required *before* either store will accept a submission — this depends on the domain and website being up

---

## 12. Do NOT port these from the HTML prototype

The prototype at `vastufirst-prototype.html` is a useful reference for **flow, copy tone and interaction design**. Its rules engine contradicts the knowledge base in nine places. Read it for the former; ignore it for the latter.

| # | Prototype behaviour | Why it is wrong | Correct behaviour |
|---|---|---|---|
| 1 | **"Find the best angle" button** (line 569) brute-forces the North bearing that maximises the score | North is a physical fact, not a variable. This lets a user manufacture a 90 | **Delete entirely.** §0.7 |
| 2 | Zones via 45° pie sectors (`Math.round(b/45)%8`) | Tradition uses a square pada grid; the two diverge badly at corners | 81-pada grid, §4.2 |
| 3 | Entrance scored by direction, S and SW marked bad | Contradicts R-01, P-05, L-14 — facing carries no score | 32-pada door analysis, §4.3 |
| 4 | Brahmasthan = circle radius 11 on 100×100 (~3.8% area) | Under-flags centre defects ~3× | Central 3×3 of 9×9, ~11%, §4.2 |
| 5 | Pooja hard-codes NE ideal **and marks centre as a defect** | Penalises the classical position; W-12 is disputed | Show both readings, §8.5 |
| 6 | Remedy text calls DERIV rules "classical"; recommends pyramids and rock salt unlabelled | Y-03, Y-04 say no classical basis | Provenance tags, §8.6 |
| 7 | No provenance labels anywhere in the UI | This is the entire differentiator | §6.6 |
| 8 | Unlisted zones default to 50/100 | Silence reads as mediocre; inflates scores | Explicit `SUBOPTIMAL`, §4.4 |
| 9 | Disputes array has 4 hardcoded cases | The knowledge base has twelve | Data-driven from §8.5 |

---

## 13. Open expert questions — build as configuration

These are with expert reviewers and **unresolved as of 19 July 2026**. Each is currently a guess sitting inside a product about to be sold. Build every one as a config flag so a ruling is a data edit, not a rebuild.

| ID | Question | Current default |
|---|---|---|
| M-05 | Brahmasthan extent: central 9 of 81, central 4 of 64, or one-ninth by area? | Central 3×3 of 9×9 |
| M-07 | Is the hybrid (square grid for rooms, angular padas for the door) defensible, or does it mix incompatible systems? | Hybrid implemented |
| M-10 | Rooms spanning padas: centroid, largest overlap, or any encroachment? | Any-encroachment for defects, largest-overlap for credit |
| M-11 | Is offering 8-zone and 16-zone in one app honest, or confusing? | Profile abstraction built; shipping gated |
| P-08 | Door located by bearing from centre, or proportion along wall? | Bearing from centre |
| R-05 / W-12 | Pooja: classical centre or modern NE? | Both shown, neither penalised |
| B-04 / W-09 | Septic tank: N, NW or SE? | Not hard-coded; both shown |
| A-03 | Does Vastu offer a flat buyer anything beyond "choose a different flat"? | Flat path built; copy provisional |

**If the M-07 ruling requires a full 81-pada rebuild of the door method, that is an engine change — which is exactly why Phase 1 is headless and fully tested. Get these rulings back before Phase 4 hardens the reports.**

---

## 14. Glossary

**Vastu Purusha Mandala** — the cosmological grid underlying all Vastu; a figure lying face-down, head NE, feet SW.
**Pada** — one cell of the mandala grid. 81 for residential (9×9).
**Brahmasthan** — the sacred centre, held by Brahma. Must remain open and unweighted.
**Dosha** — a defect; a placement working against the tradition's principles.
**Vastu Shanti** — ritual pacification, the genuine classical remedy for a defect.
**Dvaravinyasa** — the door-placement framework; 32 padas of 11.25° each.
**Veedhi Shoola** — "road spear"; a road pointing directly at a plot.
**Gaumukhi / Shermukhi** — cow-faced (narrow front, wide rear; suits homes) / lion-faced (wide front, narrow rear; suits commercial).
**Nalukettu / Nadumuttam** — Kerala courtyard house / its open central courtyard — the Brahmasthan as built reality.
**Ayadi Shadvarga** — the Kerala six-formula proportional system.
---

## 15. Appendix A — worked example (Phase 1 acceptance fixture)

Phase 1's exit criterion needs a number to check against. This is that number.

**Plan `sample-01` — "Builder's draft, 2BHK".** Square footprint, corners (0,0) to (100,100) in plan units, `northOffsetDegrees = 0`, `TRADITIONAL_8`, `INDEPENDENT_HOUSE`, `BUILDING`.

Analysis rectangle = (0,0)–(100,100). Pada size 11.111. Zone bands: cols/rows 0–33.33 / 33.33–66.67 / 66.67–100.

| Room | Rect (x,y,w,h) | Centroid | Zone | Rule | Verdict | Pts | Wt |
|---|---|---|---|---|---|---|---|
| Pooja | 6,74,28,20 | (20,84) | NW | R-05 disputed | `NOT_SCORED` | — | — |
| Bedroom 2 | 38,70,26,24 | (51,82) | N | R-04 acceptable | `ACCEPTABLE` | 70 | 1.5 |
| Toilet | 68,74,26,20 | (81,84) | **NE** | R-06 prohibited | `DEFECT` | 10 | 2.5 |
| Kitchen | 6,40,28,26 | (20,53) | W | R-02 unlisted | `SUBOPTIMAL` | 45 | 3.0 |
| Stairs | 42,41,17,18 | (50.5,50) | **BRAHMASTHAN** | R-08 prohibited | `DEFECT` | 10 | 2.0 |
| Living | 66,38,28,30 | (80,53) | E | R-07 ideal | `IDEAL` | 100 | 1.5 |
| Master | 6,6,34,28 | (23,20) | SW | R-03 ideal | `IDEAL` | 100 | 3.0 |

**Main door** at centre (80,6). Analysis-rectangle centre is (50,50), so
`bearing = atan2(80−50, 6−50) = 145.7°` clockwise from North. That falls on the
East side (78.75°–168.75°), segment index 6 → **pada E6 "Satya"**, `INAUSPICIOUS`
→ 15 points at weight 3.0.

```
Σ(points × weight) = 105 + 25 + 135 + 20 + 150 + 300 + 45 = 780.0
Σ(weight)          = 1.5 + 2.5 + 3.0 + 2.0 + 1.5 + 3.0 + 3.0 = 16.5
base               = 780.0 / 16.5 = 47.27

Structural defects: X-01 (toilet NE, MAJOR)        -> 8
                    X-03 (stairs Brahmasthan, MAJOR) -> 8
defectPenalty      = 16

score = clamp(round(47.27 − 16), 0, 100) = 31
```

**Expected `Analysis` for `sample-01`:** `score == 31`; `defects` contains X-01 and
X-03, both `MAJOR`; `doorResult.pada.id == "E6"` with verdict `INAUSPICIOUS`;
`disputes` contains W-12; `notAssessed` lists the Tier C and D rules (no fixtures
supplied); `shapeIrregular == false`; `cuts` and `extensions` empty.

**Rotation invariance:** setting `northOffsetDegrees = 90` and rotating every room
polygon and the door by +90° about (50,50) must yield **exactly `score == 31`**
again. This is the correct formulation of the test the previous draft got wrong.

> **Correction.** The previous draft required "the same plan and same door pada must score identically regardless of which way the building faces." That test is unconstructible — rotating a building genuinely does move rooms between zones, so the score *must* change. What §0.4 actually forbids is a *facing-direction term in the scoring formula*. The correct test is: **assert that no code path reads building orientation as a scoring input** — verified by the rotation-invariance test above plus a static check that `Analysis` exposes no `facingDirection` field and no rule keys off one.

---

## 16. Appendix B — rule IDs referenced elsewhere in this document

Cited in §12 and §0, defined here for self-containment.

| ID | Content |
|---|---|
| P-05 | Every side has good padas and bad ones. A south-facing house with its door on S3 Vitatha is better than an east-facing house with its door on E6 Satya. **This is why the engine no longer scores facing direction at all.** `DERIV` |
| L-14 | South-facing and west-facing plots are **not** inherently inauspicious. Multiple senior consultants call this a myth outright; Kerala practice is lenient on south; major south-facing temples exist (Kedarnath, Mahakaleshwar). The entrance pada is the determinant. `DISP` |
| W-07 | **Cash locker.** North (Kubera) versus SW opening toward North. Possibly reconcilable as "in the SW, opening N." `DISP` — commercial only, out of scope for v1 |
| W-08 | **Brahmasthan extent.** See M-05. `DISP` |
| Y-01 | Vastu Shanti / Vastu Puja / Homa — the genuine traditional remedy, prescribed in Mayamatam. `TEXT` |
| Y-02 | Structural correction — the other genuine remedy. **For a pre-construction audience this is the whole product.** `TEXT` |
| Y-03 | Vastu pyramids — no classical basis. Traced to 20th-century "pyramid power" (Bovis, 1930s) fused onto Vastu. `MOD` |
| Y-04 | Rock/sea salt bowls — no classical basis; the energy-absorption rationale has no textual source. `MOD` |
| Y-05 | Copper/brass/lead strips and wires — no classical basis. `MOD` |
| Y-06 | Crystals, wind chimes, mirrors — largely modern, visible Feng Shui cross-influence. `MOD` |
| Y-07 | Yantras — a genuine older tantric lineage exists, but their use as a Vastu-defect gadget is modern-commercial. `MOD` |
| Y-08 | Directional colours — modern systematisation mapped from each direction's element. `MOD` |
| Y-09 | Plants — Tulsi genuinely traditional; money plant and lucky bamboo are modern imports. `MOD`/`DERIV` |
| Y-10 | Keep gadget remedies but label them modern with efficacy not established, and rank layout change and Vastu Shanti above them. `MOD` |

**Note on §0.5 wording:** "zones are computed on the 81-pada grid, not 45° sectors" governs **room** zoning. The door is angular by design (§4.3, the M-07 hybrid), and `SIXTEEN_ZONE` is a separate angular geometry run as an independent evaluator (§4.7). These are not exceptions to §0.5; they are different computations.

**Note on `CENTRAL_FOUR_OF_64`:** §8.1 records that the 64-pada Manduka is for temples. The option exists only because M-05 lists it as one of the three readings a reviewer might endorse. It is not the default and should not be offered in the UI unless a reviewer rules for it.

---

## 17. Appendix C — commercial model

| Item | Value |
|---|---|
| Free tier | Score, colour zone map, top 3 named defects, honest count of remaining issues |
| Paid tier | Full ranked report, all defects, layout changes, remedies, PDF export, school profiles |
| Pricing model | **One-time unlock per plan.** Price shown before payment. No subscription at launch. |
| Price point | **TO BE SET BY THE PRODUCT OWNER before Phase 5.** Must be modelled against per-report AI cost (§17.1) |
| Entitlement | Stored server-side against the anonymous Supabase user id, restorable on reinstall |
| Restore | "Restore my purchases" required on both stores |
| Refunds | Clear, published refund policy. Refund trouble is the top complaint against every competitor researched |

**17.1 Cost warning for pricing.** Store fees and hosting are flat; AI is charged per use. Floor-plan reading costs materially more per call than the text assistant because images consume far more tokens. **Model the per-report AI cost before setting the unlock price** — it is possible to sell reports at a loss and not notice.

---

## 18. Known open items requiring a human decision

Not defects in this document — decisions the product owner owns.

1. **Unlock price** (§17). Blocks Phase 5.
2. **The eight expert rulings** (§13). **Needed before Phase 2 ships, not Phase 4** — Phase 2 delivers the full report screen with disputes and provenance on 4 August. A ruling arriving after that date means reworking shipped output.
3. **E7 and S7 pada names** are absent from the source knowledge base and left `null`. A reviewer must supply them or confirm the gap.
4. **`BUYING` intent promises a comparison view** (§2) that no screen in §6 and no phase in §9 builds. Either specify the comparison screen or soften the promise to "score each option separately."
5. ~~**Vastu-literate translators** for five languages (§7.5).~~ **CLOSED 9 Aug 2026 — no translators are needed. English only, permanently.**
6. **Disclaimer wording** (§11): use *"informational and traditional guidance"* — **not** "entertainment." An entertainment disclaimer directly undercuts the App Store guideline 4.3 submission argument that this is a genuine utility.
