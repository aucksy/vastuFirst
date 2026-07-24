# End-to-end assessment — v0.3.0 (2026-07-24)

**How this was produced:** every screen was rendered in CI and looked at across the §6.4 config
matrix (baseline, dark, font 1.3/2.0, 320/360 dp, landscape, pseudolocales, Hindi/Tamil), and three
independent read-only code audits were run in parallel: (A) the UI-POLISH §3 defect catalogue across
every screen + shared component, (B) navigation / state / flow logic, (C) the scoring engine +
grid→engine conversion + rules. This document is the consolidated, de-duplicated result.

**Headline:** the scoring maths is correct, rotation-invariant and crash-safe (all three hard gates
pass — see §D). The screens are on-design and clean at normal size. The real defects are in the
*flow around the edges* (Back, reopen, resize, process death) and *touch polish* (no pressed state).
Two flow defects can make a customer see a wrong score or get stuck; those gate a paid report.

Severity: **BLOCKER** = wrong paid output or hard stuck · **MAJOR** = our own hard-rule broken /
data loss path · **MINOR** = noticeable but recoverable.

---

## Group A — correctness & dead-ends (fix before selling) — WAVE 1

| # | Sev | What | Where | Failure for the user |
|---|-----|------|-------|----------------------|
| A1 | BLOCKER | **Plot-resize stacks rooms → score double-counts** | `NewPlanViewModel.updateGrid()` L112–132 (esp. 118–123): each room `coerceIn`'d independently, no overlap pass | Shrinking the plot pushes two rooms into the same cells; one hides behind the other; engine scores both in that zone → paid score silently wrong. Single tap on the "−" plot-size key triggers it. Regression introduced by v0.3.0. |
| A2 | MAJOR | **First-run dead-end: no path back to Home** | `VastuNav.kt` — `Routes.HOME` only ever the LAUNCH target + its own composable; nothing in the flow navigates to it. Report has no Done. | A first-timer finishes the flow and can never reach "Your plans" — only System-Back until the app exits. Must force-quit to see the home they saved / add a second to compare. |
| A3 | MAJOR | **Edits vanish silently** (reopen→Fix→edit→Back) + **draft lost on process death** | Save only at Mark North `onRead` (`VastuNav.kt:118`); `NewPlanViewModel` all plain `mutableStateOf`, no `SavedStateHandle`/autosave | Edit a saved plan then Back = changes lost, list still shows old score, no warning. Phone kills app mid-flow (cheap phones) = draft gone. |
| A4 | MAJOR | **Loading trap / soft-lock** | `ScoreScreen.kt:94–99`, `ReportScreen.kt:71–75` — `LoadingState` while `analysis == null`, no spinner/timeout/exit | With A3, an emptied draft → `buildPlan()` null → Score stuck on "Reading your home…" forever. UI-POLISH §F forbids a loading state with no way out. |

## Group B — polish a client will notice (UI-POLISH hard-rule) — WAVE 1 = 5,6,8,9,10,11

| # | Sev | What | Where |
|---|-----|------|-------|
| B5 | MAJOR | **No pressed state on ~10 control types** (chips, tabs, rows, gear, steppers, method/intent cards) — only the CTA reacts | `Interaction.kt:28–34` `clickableTap` hardcodes `indication = null`; only `VastuButton` owns a pressed state |
| B6 | MAJOR | **Double-tap = duplicate navigation** (no `launchSingleTop`, no debounce) | every `nav.navigate(...)` in `VastuNav.kt`; `clickableTap` = plain `clickable` |
| B7 | MAJOR | **North compass labels collide at font 2.0** (room names overrun tiles; legend breaks "Defec/t") — **WAVE 2** | `MarkNorthScreen.kt` Legend L146–155 (plain `Row`, no `FlowRow`); `ZoneMap` labels don't clip to tile |
| B8 | MAJOR | **Settings can't scroll** — bottom rows unreachable at large font / small screen | `SettingsScreen.kt:61–62` — `Column` with no `verticalScroll` |
| B9 | MINOR | **Score provenance badge breaks mid-word** at font 2.0 | `ScoreScreen.kt:155–158` plain `Row` of two pills (Report already fixed this with `FlowRow`) |
| B10 | MINOR | **"16-zone school" tab is a live-looking no-op** | `ReportScreen.kt:100` `onSelect = {}`, `selectedIndex = 0` hardcoded |
| B11 | MINOR | **Score still shows ₹699 Unlock card after paying** | `ScoreScreen` never reads `vm.unlocked`; `UnlockCard` always shown |
| B12 | MINOR | **Every home named "My home" + fixed "updated recently"** — defeats compare — **WAVE 2** | `NewPlanViewModel.defaultName()` L207; `HomeScreen.PlanRow` subtitle L106 |

## Group C — accessibility & smoothness (some need a device) — WAVE 2

| # | Sev | What | Where |
|---|-----|------|-------|
| C13 | MAJOR | **TalkBack can't set North** — dial/slider expose `progressBarRangeInfo` with no `setProgress` action (▲▼ + N/E/S/W chips are the fallback) | `Slider.kt:101–105`, `NorthDial.kt:79–88` |
| C14 | MAJOR | **Compass stutters while dragging** — `buildZoneMapModel` not `remember`ed (rebuilt every degree); `TextMeasurer` cache thrash | `MarkNorthScreen.kt:80`, `ZoneMap.kt:60,145–147` |
| C15 | MINOR | language pills no feedback / no "soon" affordance; clickable rows lack `Role`; editor loses selection on rotation; door on a far wall can jump on reload | `WelcomeScreen.kt`, `HomeScreen.kt:107`, `GuidedGridScreen.kt:269–274`, `PlanConversion.doorGeometry` |

## Group D — engine / product-scope — OWNER DECISIONS, not bugs

**All three hard gates PASS (verified):** sample-01 scores exactly 31 (`Sample01Test.kt:19-21`);
rotation-invariant across 10 angles + a non-square L-shape (`RotationInvarianceTest.kt`); never
crashes / never says "error" — total try/catch degrades to `INSUFFICIENT`, `PlanSanitizer` cleans
NaN/degenerate input, `StressCorpusTest` proves empty/collinear/bowtie/725°-north all return a valid
score. Rectangular plots divide proportionally (`PadaGrid.kt:18-19`), grid count never leaks into the
score. The eight §13 rulings are config/data (one architectural exception, M-07 hybrid door, disclosed).

| # | What | Recommendation |
|---|------|----------------|
| D1 | **L-shaped / notched homes scored as a filled rectangle** — `buildEnginePlan()` synthesises the footprint as the bounding box (`PlanConversion.kt:48-56`), so a missing NE corner is invisible and the score is too generous. Biggest *accuracy* risk for a paid report. | Owner decision: build simplified outline capture, OR label the report "based on a rectangular footprint." Full outline capture likely too big before 4 Aug. |
| D2 | **Headline score is a ceiling** — fixtures/site (septic tank, borewell, road) never collected | Label the free number "based on rooms, door & shape." |
| D3 | North user-set & unverifiable (no compass cross-check); door capture coarse; 8×8 editor vs 9×9 padas | Documented in SCORE-ACCURACY-CAVEATS.md; no cheap fix; disclose. |
| D4 | **Scary-zero gap**: an all-unruled-rooms + no-door plan → base 0 at `quality = OK` (SUSPECTED) | Cheap guard: treat `weightSum == 0` as INSUFFICIENT/guidance. Candidate for Wave 1/2. |
| D5 | Elongation penalty (X-15) suppressed when shape is irregular | Minor, "generous" direction; low priority. |
| D6 | Eight expert rulings + final ₹699 price still needed from owner | Owner action (Impl-PRD §13). Rulings plug into JSON quickly. |

---

## Plan

- **Wave 1 (v0.3.1):** A1–A4 + B5, B6, B8, B9, B10, B11 (+ consider D4 guard). One at a time, CI +
  screenshot ritual + adversarial review before tagging.
- **Wave 2 (v0.3.2):** B7, B12, C13–C15.
- **Group D:** owner decisions; no code until chosen.

## Change log
| Date | Change |
|---|---|
| 2026-07-24 | Created from the v0.3.0 end-to-end assessment (3 parallel code audits + full render-matrix review). |
</content>
</invoke>
