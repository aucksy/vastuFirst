# VastuFirst — Phase 2 progress (the guided-grid Android app)

**Milestone:** 4 August 2026 client delivery. **Status: the full guided-grid path is built and
green on CI.** Entry point for a fresh session is `Documents/VastuFirst-Android-Implementation-PRD.md`
(the build plan) + this file (what's done).

## What ships in Phase 2

A real person can, fully offline, on their own phone: pick their intent → place their rooms and
front door on a grid (or load a sample) → mark North on the signature dial with a live score →
see a free score with the top-3 defects → unlock the full, intent-branched report with provenance
tags and disputes → save it and reopen it later.

### Screens (all from the Sage & Gold design system, token-only)
| # | Screen | Notes |
|---|--------|-------|
| 1 | Welcome | Language (English live; 5 scripts "soon" — l10n is Phase 4), intent picker, Continue gated on intent |
| 2 | Add home | Guided grid + samples wired; Upload = "soon" (AI reading is Phase 4) |
| 3 | Guided grid editor | Tap-to-place rooms, resize (steppers), remove; a second mode places the **front door** on an outer wall |
| 5 | Mark North | The `NorthDial` (drag/tap) + slider + N/E/S/W chips + degree stepper; **live score debounced ≤50 ms, off the main thread**; clean centre; **no best-angle affordance** (§0.7) |
| 6 | Score (free) | Big band-coloured number, zone map, top-3 defects, honest count of the rest, "score is our own construction" note |
| 8 | Unlock | Paywall UX at ₹699; **unlocks locally** (payments are Phase 5) and says so honestly |
| 7 | Full report | **Branches on intent**: BUILDING/BUYING lead with layout changes, LIVING leads with remedies; provenance tag on every rule; "already right", "where schools disagree", "not assessed", disclaimer |
| 11 | Saved plans (Home) | Reopen re-runs the engine from the stored plan; two plans side-by-side = the BUYING comparison |
| 12 | Settings | Preferences shown (language/school fixed this phase); honest data controls incl. delete-all |
| 13 | Legal / Honesty & sources | Visible disclaimer + the provenance vocabulary |

## Architecture (how it's wired)
- **Engine untouched** (consumed, not changed). The only additive change to a pure module was
  `@Serializable` on the `Plan` input DTOs so a plan can be persisted — no engine logic touched.
- **`NewPlanViewModel`** (nav-graph-scoped) is the draft home shared across the flow. It converts
  the guided grid → the engine `Plan` (grid rows flipped to engine north-up space) and runs the
  engine off the main thread with a ≤50 ms debounce. The engine is TOTAL, so there is never an
  error state.
- **Persistence:** SQLDelight in `:data` (Android-free — the Android `SqlDriver` is the only
  platform seam, in `:app`). Each row stores the plan JSON + `ruleSetVersion` (so a later ruleset
  change is detectable, never a silent re-score).
- **Components:** an owned, token-only Compose kit in `:designsystem` (no Material theming, so the
  iOS re-skin stays mechanical). DS-local `VastuVerdict`/`VastuProvenance` keep it free of `:shared`.
- **Fonts:** Marcellus / DM Sans / DM Mono bundled as OFL Compose resources (Indic Noto = Phase 4).
- CI guards (module boundaries + token discipline) pass on every push.

## Deliberate deviations from the mock (resolved, not accidental)
- **Front-door step added** to the grid editor — the mock omits it, but the door is the highest-
  weighted element the engine scores.
- **Resize by steppers**, not drag — more reliable one-handed; same outcome.
- **Local unlock** instead of real payment (Razorpay is Phase 5); the paywall UX is intact.
- **Compass-sensor helper omitted** on Mark North (it is explicitly secondary/optional, §6.3);
  dial + slider + degree + chips cover the need. Candidate for a later pass.
- **BUYING comparison** = the saved-plans side-by-side view (no separate compare screen).

## Owner decisions still open (needed before 4 Aug ship, built on placeholders)
1. **Report price** — `₹699` placeholder throughout.
2. **The 8 expert rulings** (§13) — engine runs on the current safe defaults until rulings land.

## Known minors to revisit in review
- Language picker is display-only for the 5 non-English scripts (l10n is Phase 4).
- 16-zone school toggle on the report is display-only (Phase 4).
- Review-gate self-audit done; a physical-device pass at 360 dp + TalkBack is still owner testing.

## Next
Client testing (Phase 3) + the owner decisions above. iOS/payments/AI/languages are Phases 4–5.

**Phase 3 audit round 1 done (v0.2.1)** — see `docs/PHASE-3-AUDIT.md`. Fixed the no-scary-error
gap (INSUFFICIENT no longer shows red 0/100), the save-timing score bug, blank reopen zone map,
ruleSetVersion re-score, intent-blind verdict, unlock "Pay" wording, and the a11y/review-gate items
(48dp targets, icon-button labels, slider semantics, button clip-safety, delete-all confirm).
Deferred + surfaced to owner: L-shaped/notched homes are scored as a filled rectangle (needs an
outline-capture step) — candidate #1 for the refinement plan.

---

## v0.2.3 — the floor-plan editor becomes direct manipulation (Build A)

**What changed for the user.** Before: tapping the plan dropped a 2×2 room the instant your finger
touched it, and the only way to change it was four `W −/+ H −/+` buttons — a room in the wrong place
had to be deleted and drawn again. Now: touch a room and slide it, pull a corner to resize it, and
when you add a room a dashed outline follows your finger and only lands **when you lift**. A chip
above the plan names what you are about to get ("Kitchen · 2×2 · North-East") while you are still
holding it. Rooms cannot be dropped on top of each other — the outline turns red, the phone buzzes,
and nothing is committed.

Built to `docs/EDITOR-REWORK-PLAN.md` §5 Build A (items 1–5, plus item 7 pulled forward).

**Under it:**
- All the arithmetic that decides where a room lands is a pure function in `shared/…/editor/` with
  21 unit tests that run on every push. That is deliberate: with no screenshot harness in this
  project yet, a gesture written inside a Composable is a gesture nothing can prove.
- One gesture arbiter (`awaitEachGesture` + manual hit-testing) on the grid, never per-child, so the
  corner grips can hang outside a room and still be grabbable. Priority: selected room's grips
  (circular hit test) → room bodies topmost-first → empty space.
- The drag consumes at touch-slop, which is what stops the page scroll from stealing a vertical
  drag inside the plan. A drag starting on **empty** grid space deliberately does not consume, so
  the page still scrolls from there.
- Drag state is republished only when the **snapped cell** changes, so a drag recomposes a handful
  of times rather than once per pointer event.

**Three older defects closed on the way:** the `pointerInput` was keyed on the room list (so the
first edit a drag made cancelled the very gesture making it — UI audit item 25); the room palette's
scroll position reset to the far left on every placement (item 16); the size steppers were one Row
of six controls, which overflows a 320 dp screen.

**Engine untouched** — `sample-01` still scores exactly 31.

### ⚠ What was and was not verified for v0.2.3

- **Verified:** green CI on `aucksy/vastuFirst` — module boundaries, token discipline, window-inset
  screen-roots, design fidelity against the frozen contract, and every pure-module test including
  the engine's `sample-01 = 31` and rotation invariance. The editor's own maths is covered by the 21
  new tests. The agreed interaction was re-read line by line against
  `tools/grid-prototype/index.html`, which remains the behavioural spec.
- **NOT verified:** nobody and nothing has *rendered* this screen. The Roborazzi harness of
  UI-POLISH §6 is Build B item 10 and does not exist yet, so there are no screenshots to look at and
  the pre-APK ritual of UI-POLISH §7 steps 2–4 **could not be performed**. Everything visual here —
  chip position, grip size against a real fingertip, the lift shadow, behaviour at 360 dp and at
  200 % font scale — is reasoned, not seen. Runtime gesture behaviour (does the drag genuinely beat
  the page scroll on a real phone?) is likewise unproven off-device.
- Two logic defects were caught in self-review after the code was written and before it shipped: an
  early `return` before the first pointer-down, which would have spun `awaitEachGesture` into an ANR;
  and reading `positionChange()` after consuming the change, which always reports zero movement and
  would have frozen the placement ghost.

---

## Render harness — batch 2: Welcome, Home, Settings drawn (v0.2.5, 2026-07-24)

Three more screens the build had never rendered can now be seen. Each got the editor's proven
stateless seam: a thin `Screen(vm)` wrapper that is the only thing touching the ViewModel, calling a
pure `…Content(state, callbacks)` the screenshot harness drives from a fixture.

- **`WelcomeScreen` → `WelcomeContent(intent, onIntentChange, onContinue)`** — `testTag("welcome.continue")`.
- **`HomeScreen` → `HomeContent(plans, onAddHome, onOpenPlan, onSettings)`** — `testTag("home.add")`;
  the wrapper still collects `viewModel.plans`. Rendered in two states: with two saved homes, and
  **empty** (`home-empty`) — the state a first-time user lands on and the one most likely to collapse.
- **`SettingsScreen` → `SettingsContent(onLegal, onBack, onDeleteAll)`** — `testTag("settings.back")`;
  the delete-confirm dialog state stays inside `Content`, the ViewModel's `deleteAll` is now a callback.

New harness files: `render/RenderFixtures.kt` (a minimal `SavedPlan` list — the list view only reads
id/name/intent/score, so the `Plan` inside is deliberately trivial) and
`render/ViewModelScreensScreenshotTest.kt` (four screens × the full §6.4 matrix, screenshot +
manifest each). CI records the goldens and the ratchet adopts each screen into `render-baseline.json`.

**Behaviour unchanged** — this is a pure extract-a-seam refactor; every `vm.x` became a parameter,
no layout or logic moved. Still cannot see: window insets, IME, gesture conflicts, rotation, real
TalkBack (UI-POLISH §6.7).

---

## Render harness — batch 3: Mark North, Score, Report drawn (v0.2.6, 2026-07-24)

The three score-driven screens — the ones that show the actual Vastu result — can now be seen. All
eleven screens are now rendered + measured in CI.

- **`MarkNorthScreen` → `MarkNorthContent(rooms, north, analysis, onNorthChange, onRead, onBack)`**.
- **`ScoreScreen` → `ScoreContent(rooms, north, intent, analysis, onUnlock, onFix)`** — the state
  `when` (loading / "insufficient plan" guidance / full result) moved into `Content`, so all three
  states render. Rendered as `score`, `score-insufficient` and `score-loading`.
- **`ReportScreen` → `ReportContent(analysis, intent)`** — rendered on both branches (`report` =
  BUILDING/layout-led, `report-living` = remedy-led).

**The fixture is real, not faked.** These screens need a scored `Analysis`. Rather than hand-build
one, the grid→Plan conversion was lifted out of `NewPlanViewModel.buildPlan()` into a pure
`buildEnginePlan(...)` (`ui/newplan/PlanConversion.kt`) that the ViewModel now delegates to — one
source of truth for the row-flip and door geometry. `RenderFixtures` runs the bundled sample home
through it and through the **real `VastuEngine`**, so the zone-map colours, the ranked defects, the
remedies and the "already right" rows are the engine's genuine output. The `INSUFFICIENT` guidance
state is `.copy()`d from that real analysis (quality + note changed), so every other field stays valid.

**Behaviour unchanged** — the ViewModel produces byte-identical plans (same maths, now in a shared
function). Still cannot see: window insets, IME, gesture conflicts, rotation, real TalkBack.

---

## Two harness findings fixed (v0.2.7, 2026-07-24)

The render harness surfaced these two on the earlier batches; both are now fixed.

- **The compass can never mirror.** The guided-grid plan — the NORTH/EAST/SOUTH/WEST labels and the
  grid itself — is now wrapped in `CompositionLocalProvider(LocalLayoutDirection provides
  LayoutDirection.Ltr)`. North/East/South/West are cardinal directions, not translatable text: under
  an RTL locale (a future Urdu build) the `SpaceBetween` label row would swap WEST↔EAST and the grid
  would flip, silently reversing every Vastu direction the engine scored. The harness caught this on
  the `ar-XB` pseudolocale pass. The lock is a no-op in every LTR locale, so only the RTL render
  changes. (The zone-map/dial compass in Mark North & Score is Canvas-drawn with absolute geometry
  and does not mirror, so it needs no lock.)
- **The Unlock screen scrolls.** `UnlockScreen` had no scroll container, so at font scale 2.0 the
  "What you get" list pushed the disclaimer off the bottom and it clipped. Added
  `verticalScroll(rememberScrollState())` at the screen root (UI-POLISH §3.B).

Both are verified by the render harness re-rendering the affected configs (`editor`/`editor-empty`
at `rtl`, `unlock` at `font2_0`); the goldens in this release show the corrected layout.

---

## Accessibility check now runs in CI (v0.2.8, 2026-07-24)

Google's Accessibility Test Framework — contrast, touch-target size, missing/duplicate labels,
traversal order — now runs **headless inside the same JVM render** via Roborazzi's
`checkRoboAccessibility` (`AccessibilityTest` → `A11yHarness`). No emulator; it turns a whole class
of accessibility defects from "needs a device" into a CI check.

- **Ratcheted like the geometry gate**, never a hard cliff: `scripts/check-a11y-manifest.mjs` records
  the error-level finding count per screen in `a11y-baseline.json`, and fails only when a screen's
  count *increases*. a11y is additive — the render + L1 gates already catch the worst geometry.
- **Fail-loud if the check itself breaks.** This is the most version-sensitive piece of the harness
  on the pinned Roborazzi 1.60.0. If `checkRoboAccessibility` throws anything other than the expected
  findings exception, the manifest records `errored: true` and the gate exits 2 — a broken check must
  never read as "0 findings".
- **Robust to the toolchain.** Runs at the baseline config only (ATF's added value over L1 — contrast,
  label quality, traversal — is config-independent; touch targets are already measured per-config by
  L1). Catches `Throwable` and reads the finding count via reflection `getResults()`, so nothing here
  depends on the ATF/espresso exception types being on the test compile classpath — only Roborazzi's
  own `checkRoboAccessibility` is imported. The caught exception is swallowed: the test never fails
  the build; the ratchet script decides.

The 11 rendered screens now pass through four gates on every build: L0 tokens/fidelity, L1 measured
geometry, the inset grep gate, and now L2 accessibility.

---

## Owner device-feedback pass (v0.2.9, 2026-07-24)

Four fixes from the first real-device review (a fifth — rectangular plots — is a research
recommendation in docs/RECT-PLOT-RESEARCH.md, awaiting sign-off before any code):

- **First run no longer opens on an empty "Your plans".** A one-frame `Routes.LAUNCH` decider (the
  new start destination) reads the DB once behind a themed splash: a returning user lands on their
  saved plans, a first-timer goes straight into the flow. `popUpTo(LAUNCH, inclusive)` so Back from
  the first real screen exits.
- **Haptics, app-wide.** New `VastuHaptics` vocabulary (`LocalVastuHaptics`, no-op default; Android
  impl routes through `View.performHapticFeedback` so it honours the system setting). A light tap on
  every discrete control (wired once in `clickableTap`, so all buttons/chips/rows/icons get it), and
  a fine per-step tick on the North dial (per degree) and the slider. The editor keeps its existing
  on-touch-down haptics. *(Design note: the Sage & Gold spec suggests a soft detent every 15° on the
  dial; per the owner's request this ships as a tick every degree — trivially switchable if it reads
  as too busy on hardware.)*
- **The North knob has its direction arrow** (design: the sage triangle above the "N" circle), drawn
  rotated to the bearing so it always points radially outward — at North=0 that is straight up,
  exactly the prototype. Reads as a compass needle.
- **The selected-room corner grips are refined** — smaller solid dots with a thin cream halo instead
  of the large hollow rings that floated over the corner (owner: "not looking good"). *(The render
  harness draws the editor with nothing selected, so this one is reasoned + needs the on-device look;
  the dial arrow IS in the goldens.)*

---

## Editor Build C — rectangular / true-to-life plots (v0.3.0, 2026-07-24)

Owner item #2, approved (square cells, choose size). Users can now set the plot's true proportions
so a rectangular home is drawn true-to-life — no forced square, no confusing empty strip.

**No engine change, no scoring change.** The engine already scores the *rooms' bounding box* divided
proportionally (`PadaGrid`: `padaW = width/N`, `padaH = height/N`), so the grid size never entered
the score — only the drawing canvas did (docs/RECT-PLOT-RESEARCH.md). This is purely an editor change.

- **Shared math generalised** (`GridEditing.kt`): `moveBy`/`resizeBy`/`zoneOfRect` take `cols` +
  `rows = cols`. The `rows = cols` default means all 21 existing tests and every square call site
  compile untouched; only the editor passes a rectangular pair.
- **ViewModel**: `gridCols`/`gridRows` (default 8×8), `updateGrid(cols, rows)` clamps to
  `MIN_GRID`..`MAX_GRID` (4..10) and shrinks/moves rooms to fit (never drops them); a door on a wall
  that no longer exists is cleared; `load()` re-derives the plot shape from a reopened home's rooms.
- **Editor** (`GuidedGridScreen`): grid draws at `aspectRatio(cols/rows)` with square cells and
  per-axis third-bands; hit-test, placement, move/resize, tiles, door and grips all thread cols/rows.
  A **Plot size** control (two `− n wide +` / `− n deep +` stepper rows) sits in the resting toolbar.
- **Zone map** (`buildZoneMapModel`, Score + Mark North): normalises rooms by cols/rows so they align
  to the plot's thirds at any proportion (default 8×8 unchanged).
- **Render**: new `editor-wide` golden (8×5) proves the rectangular grid draws correctly; the 8×8
  `editor`/`editor-empty` goldens are unchanged (`aspectRatio(8/8) == 1`).

The default is still square, so anyone who doesn't set a size sees exactly the previous editor.

---

## End-to-end assessment + Wave 1 fixes (v0.3.1, 2026-07-24)

Full v0.3.0 assessment (every screen rendered across the config matrix + 3 parallel code audits —
UI catalogue, navigation/state, engine). Findings + tiers recorded in **docs/E2E-ASSESSMENT-2026-07-24.md**.
The engine passed all three hard gates (31 / rotation-invariant / crash-safe); the real defects were
in the *flow around the edges* and *touch polish*. **Wave 1** (owner-approved) fixes the correctness /
dead-end bugs + the cheap high-impact polish; Wave 2 = the rest; Group D = owner decisions (no code).

**Group A — correctness & dead-ends:**
- **A1 · plot-resize no longer stacks rooms (score-corrupting).** Shrinking the plot clamped each room
  independently and could push two onto the same cells → the engine double-counted the buried room.
  New pure `fitWithoutOverlap(rects, cols, rows)` in `shared/editor/GridEditing.kt` re-packs after any
  resize (keeps size/order, relocates only what would collide, never drops a room); `updateGrid` uses
  it. 4 new `:shared:test` cases incl. the audit's exact 10→8-wide repro.
- **A2 · first-run dead-end removed.** A first-timer had no in-app path back to "Your plans" (only
  Back-to-exit). Score and Report now carry a "See all my plans" / "Done" button → `nav.goHome()`
  (`popUpTo(NEWPLAN_GRAPH, inclusive)` so Back can't re-enter the flow).
- **A3 · edits no longer vanish silently.** An already-saved home (planId != null) now **autosaves**
  every debounced edit, so reopen → Fix → edit → Back keeps the changes and the list stays in sync.
  A brand-new draft is still first saved at Mark North (no junk rows mid-draw).
- **A4 · no more forever-spinner.** If the OS reclaims an in-progress draft (process death on a cheap
  phone), Score/Report showed "Reading your home…" forever. Now the empty-draft case degrades to a
  guidance card with a "Go to my plans" exit.

**Group B — polish (UI-POLISH hard-rule):**
- **B5 · every control now has a pressed state.** `clickableTap` dims content on press by default
  (`pressEffect`, ~0.55 alpha); the buttons opt out (they own a fill swap). Fixes ~10 dead-to-touch
  control types (chips, rows, gear, steppers, cards, icon buttons).
- **B6 · double-tap can't double-navigate.** Added `launchSingleTop` to every navigation (`nav.go`).
- **B8 · Settings scrolls** (was unreachable bottom rows at large font / small screens).
- **B9 · Score provenance badge no longer breaks mid-word** at font 2.0 — the two-pill row is now
  `FlowRow` (matches the Report fix).
- **B10 · the "16-zone school" tab is honest** — shown disabled + "· soon", no longer a live-looking
  no-op (`VastuSegmented(disabledIndices)`).
- **B11 · after unlocking, Score drops the ₹699 paywall** and offers "See the full report"; tapping
  it routes straight to the report, not the paywall again.

Also opportunistically: Score's zone-map model is now `remember`ed (§H, was rebuilt every recomposition).

**Deferred to Wave 2:** B7 (Mark-North compass labels collide at font 2.0), B12 (all homes named
"My home"), C13 (TalkBack can't set North), C14 (Mark-North drag perf), C15 (minor a11y). **Group D**
(L-shape footprint, score-is-a-ceiling labelling, the 8 rulings + ₹699) awaits owner decisions.

**Adversarial review before tagging** (standing rule): no blockers/majors. Two cheap review fixes
applied — F1: the terminal `repo.save` (both autosave and the Mark-North `save()`) is now
`NonCancellable`, so the one-tap "See all my plans" (which pops the flow's ViewModel scope) can't
drop an in-flight save; F2: `updateGrid` only recomputes/re-saves when a room actually moved or the
door cleared, so a pure canvas *grow* no longer bumps the plan's `updatedAt`. F3 (autosave writes the
constant "My home" name) is inert until naming ships — folded into Wave 2's B12.

---

## Wave 2 · B7 — Mark-North label collisions at large text (v0.3.2, 2026-07-25)

The one clear visual defect left from the v0.3.0 assessment. On "Which way is North?" at the
phone's 2× accessibility text size, two things broke — both now fixed, verified on the render
harness (font2_0 golden), **no engine change and no effect on the score**:

- **Room-name labels overran their tiles** ("PoojaBedroomToilet" mashed together). The compass is a
  fixed-size Canvas, so the text is now clamped to its tile: one line, cut with "…" if too long
  (`ZoneMap.drawCentered` measures with a width constraint + ellipsis), and it degrades by the
  vertical room the current text size actually leaves — both name and verdict when they fit,
  name-only when one line fits, nothing when even one line won't (the tile colour still carries the
  verdict). No change at normal text size beyond long names now ellipsising like the editor already
  does; the two-line placement is now symmetric and provably non-overlapping at any scale.
- **The colour key chopped "Defect" mid-word** ("Defec/t"). `Legend` Row → FlowRow (the same fix
  B9 applied to Score/Report), so the four keys wrap whole items instead of breaking a word.

**Deliberate L1 ratchet bump — `marknorth` 17 → 18.** The taller (correctly wrapping) legend pushes
the "Live score / 31·100" readout row 8 dp lower in the *top-of-scroll* screenshot at font2_0, so
it reads as 8 dp more clipped (37.5 → 29.5 dp visible) and "31/100" crossed the clip threshold. This
is a scroll-fold capture artifact on a `verticalScroll` screen — the row is fully reachable in the
app — and is the same class already accepted in the baseline (the Back / Read-my-home buttons at the
bottom of this screen are flagged across 6 configs). Confirmed by diffing the old vs new render
manifests (the only delta), then updating render-baseline.json per the gate's own "if intentional"
instruction — not a silent ratchet.

Adversarial review before tagging: no blockers.

---

## Wave 2 · C14 + C13 — Mark-North drag perf + TalkBack can set North (v0.3.3, 2026-07-25)

Two Mark-North-only fixes, one release. No engine change, no effect on the score, **no golden pixel
change** (CI committed no new goldens; L1 `marknorth` stays 18, a11y `marknorth` stays 7 — both
ratchets "no regression").

- **C14 — the compass is smooth while dragging North.** Turning the dial recomputed the *entire*
  plan picture — every room, colour and label — on every degree, even though only the needle moved;
  on cheap phones that read as stutter. Two changes:
  - `buildZoneMapModel` (`UiMappers.kt`) now **memoizes** its heavy room+wedge build in a `remember`
    keyed on `(gridRooms, analysis, cols, rows, theme)` — deliberately **not** `north`. So a drag
    (which changes only `north`) reuses the cached lists and rebuilds only the tiny `northDegrees`;
    a real edit (new room-list instance / new analysis) invalidates and rebuilds. The verdict colours
    are `@Composable` (they read the theme), so they're resolved just *outside* the memo and captured
    — the theme key re-runs the block with fresh values on a theme switch. (First CI attempt caught
    that I'd wrongly moved a `@Composable` call inside the memo — fixed before tagging.)
  - `ZoneMap.kt` gives the `TextMeasurer` a real cache (`cacheSize = 64`, was the default 8). The
    canvas lays out 20+ distinct strings per frame; the tiny default cache missed every frame and
    re-laid-out all text on each degree. 64 holds a full plan's labels → cache hits.
- **C13 — a TalkBack user can now *set* North with the dial or the slider.** Both exposed a value
  (`progressBarRangeInfo`) but no *set-value* action, so the screen-reader "swipe to adjust" gesture
  did nothing — a blind user was forced onto the ▲▼ / N-E-S-W fallback. Added a `setProgress` action
  to `VastuSlider` and to `NorthDial` (the dial's label + range + action now live on the interactive
  overlay, and the inner `ZoneMap` label is blanked, so TalkBack sees exactly **one** labelled,
  adjustable node — no duplicate-label ATF flag). Google's ATF a11y gate does **not** check for a
  set-value action, so a new headless test (`MarkNorthA11yTest`) invokes `setProgress` on each control
  and asserts North actually moves — this is the "prove it", ran green in CI.

**Adversarial review before tagging:** no blockers. Stale-cache risk (the C14 memo) checked: keys
cover every input that changes room geometry/colour; `GridRoom` is a data class and `vm.rooms` is
reassigned (never mutated) on every edit, so an edit produces a new value-unequal list → invalidates,
while a drag keeps the same instance → cache holds. `VastuTheme.colors` is a `staticCompositionLocalOf`
singleton → a stable, correct memo key.

**Deferred to later in Wave 2:** C15 (minor a11y), B12 (all homes named "My home" + review-finding
F3). **Group D** (L-shape footprint, score-is-a-ceiling labelling, the 8 rulings + ₹699) still awaits
owner decisions.

---

## Wave 2 · B12 — distinct home names + rename + real "updated" time (v0.3.4, 2026-07-25)

Every saved home was "My home" with a fixed "updated recently", which defeated the side-by-side
compare. Now each home is its own thing:

- **Auto-named "Home N".** A new home takes the next free number (highest existing + 1, so deleting
  one never causes a duplicate — pure `nextHomeNumber`, unit-tested incl. the delete case). The
  ViewModel holds the name; `save()` assigns it once at first save, `load()` restores it, and both
  persistence paths (save + autosave) write the REAL name — which also closes review-finding **F3**
  (autosave was clobbering the name with a constant).
- **Rename on the list.** A labelled pencil per row opens a rename box (`RenameDialogContent`, reusing
  the owned `VastuTextField` + the Settings `Dialog` pattern); Save is disabled while blank. New
  `HomeViewModel.rename` → `PlanRepository.rename` → a `setName` query that deliberately does NOT bump
  `updatedAt` (a rename must not reorder the updatedAt-DESC list above a just-scored home).
- **Real "updated" time.** Plain-English `relativeUpdated` (pure, unit-tested): "Updated today /
  yesterday / N days ago / on 3 Jul", replacing the hardcoded string. The home fixture uses a fixed
  `now` so the golden is deterministic.

**Proof / render:** the rename box is a new `home-rename` render+a11y screen (actually looked at,
baseline + font2.0). ⚠**First CI caught a real latent a11y gap** in `VastuTextField` (never rendered
before B12): its editable/touch node was ~25 dp tall (below the 48 dp floor) and unlabeled — 22 L1
findings. Fixed at the component (fills its 48 dp box + carries its label as the accessible name),
dropping home-rename to **0** L1 / 2 ATF (adopted). Also **home L1 baseline 1→3** (documented): the
new pencil narrows each row, so long names ellipsise at font 2.0 / 320 dp — benign (full name shows
at normal size + in the rename box; auto-names "Home N" are short).

**Adversarial review before tagging:** no blockers. Name-assignment race is idempotent (both paths
assign the same "Home N" since the draft isn't in the DB yet); rename of an open home is safe (the
nav-scoped draft VM isn't active on the Home list, and `load()` re-reads the stored name).

**Wave 2 remaining:** C15 (minor a11y). **Group D** (L-shape footprint, ₹699, the 8 rulings, free-score
label) still awaits owner decisions.

---

## Wave 2 · C15 — minor a11y + robustness (v0.3.5, 2026-07-25) — WAVE 2 COMPLETE

Four small fixes, no visual change (goldens byte-identical, both ratchets "no regression"):

- **Door no longer jumps on reopen.** A door tapped past the room extent was snapped to the footprint
  (the bbox the engine scores) at save, then reconstructed there on reload → it appeared to move.
  `placeDoor` now clamps the door cell onto the footprint at placement, matching `doorGeometry`'s
  clamp, so displayed == stored == reloaded. (Within-footprint doors were always stable.)
- **Editor survives rotation.** `selectedId` / `doorMode` / `armedType` are `rememberSaveable` now, so
  a config change (or a brief process reclaim) doesn't drop the selection / door step / armed room.
- **Clickable rows carry a Role.** Saved-home rows and the Welcome intent cards announce as buttons to
  a screen reader (`clickableTap(role = Role.Button)`).
- **"Coming soon" languages say so.** Each soon-pill carries a "<language> — coming soon"
  contentDescription, instead of a screen reader reading only the language name.

**Adversarial review:** no blockers. Door clamp range is always valid (`fMaxC-1 ≥ fMinC`) and identical
to the engine clamp; `RoomType` is Serializable so the enum saver is safe; the rest is semantics-only.

**Wave 2 is now complete** (B7 · C14 · C13 · B12 · C15). Remaining before the 4 Aug delivery is all
**Group D owner decisions** (L-shape footprint, ₹699, the 8 expert rulings, free-score label) + the two
device-glance checks — no more Wave 2 code.

---

## Guided-grid UAT — exhaustive QA pass (v0.3.6, 2026-07-25)

An adversarial, not-happy-path QA of **everything a user can do on the guided grid**, because the
owner was seeing too many issues in the plan builder. Full catalogue + verdicts + owner proposals:
`docs/UAT-GRID-PLAN-BUILDING.md`.

**Automated the testable cases (all green in CI):**
- `shared/GridEditingRectTest` — move/resize/repack/zones on **non-square** plots and at MIN/MAX
  bounds (the rectangular-plot risk area the old square-only suite missed).
- `app/…/newplan/PlanConversionRoundTripTest` — the grid⇄engine flip proven both ways: rooms/door
  round-trip byte-for-byte (incl. rows 8–9 where engine-Y goes negative), door-side classification on
  1-cell-thin footprints, door-past-rooms snap, empty-polygon skip, and the headline **score
  translation-invariance** proof (same home scores the same wherever it sits on the canvas).
- `app/…/newplan/GridResizeTest` — the plot-resize decision + reopen grid derivation + door re-clamp.
- `app/…/grid/GuidedGridInteractionTest` — the WCAG single-pointer BUTTON paths headless (move arrows,
  size steppers, overlap-refusal, remove, plot bounds, door-mode entry).

**Testable seam (no behaviour change):** moved `gridRoomsFromPlan`/`gridDoorFromPlan` out of the
ViewModel into `PlanConversion.kt` (public, next to the forward flip); extracted
`resolveGridResize`/`gridSizeForRooms`/`clampDoorToRooms` into `GridResize.kt`. `updateGrid`/`load`/
`updateRooms` now bind to them.

**Triage verdicts (7 suspects + F4):**
- ✅ NOT bugs (proven): S1 translation-invariance, S5 stepper/drag asymmetry, S6 thin-footprint door side.
- ✅ FIXED this build: **S4** (door "Set the front door" button was a dead end on the empty grid →
  hidden until a room exists) and **F4** (door could be displayed in one place but scored/reloaded in
  another after a room was removed/moved → now re-clamped to the footprint on every room edit).
- ⚠ Documented, benign, left as-is: **S7** (plot-shrink clamps a lone oversized room to fit — no
  overlap, score intact).
- ⚠ CONFIRMED, owner decision (proposals in the UAT doc, NOT blockers for 4 Aug): **S2** (reopen loses
  empty plot margin drawn beyond the rooms — needs a saved-data change) and **S3** (a brand-new,
  not-yet-scored draft can be lost on a low-RAM process kill — needs SavedStateHandle).

**Render/§7:** the S4 change re-recorded the empty-state goldens (`editor-empty` + `editor-wide`); I
looked at baseline + font2.0 — grid visible, band lines, steppers readable, door button correctly gone,
no clip/overlap. L1 + a11y ratchets adopted the drop. Adversarial review before tagging: no blockers.

**Device-glance checklist still open** (raw gestures/haptics/true process-death — can't be faked
headlessly): touch-and-slide move, corner-grip drag resize, drag-into-another refusal, haptics,
rotation persistence, process-death of a saved vs unsaved home, a real TalkBack pass. Listed in the UAT doc.

---

## On-device gesture + layout fixes — v0.3.7 then v0.3.8 (2026-07-25)

Owner tried the guided grid on a real phone and reported ~9 issues the headless UAT missed —
because they live in the finger/gesture/render code the pure tests and the unselected render
harness never cross. Root-caused each in the real code (not more tests first).

**v0.3.7 (commit c85ad59 / tag v0.3.7):**
- ⭐**Gesture arbiter re-keyed on (cols, rows), not Unit.** It was frozen to the plot size from first
  composition, so after ANY plot resize every finger calc used the OLD grid → rooms placed off-grid,
  taps missing the room (selecting only from its edge), a room unable to move past the former bottom
  until the screen was re-entered, order-of-operations mattering. ONE root cause behind owner #6.2 / #8
  / #9 and much of #7. `size` was already live; only the captured cols/rows were stale.
- **Corner grips:** removed the ~24 dp inward clamp (dots now sit ON the corners of wall-hugging rooms
  — owner #4) and capped each grab-zone at half a cell; draw + hit share the clamp. placeDoor reads the
  live room list.
- **Layout:** chip is a top-of-plan overlay (no idle blank band, no move-lurch — owner #1); plot-size on
  one wrapping FlowRow (#2); "Set the front door" is the primary highlighted button, secondary once set
  (#3); selected-room tools reordered Remove/Done → Move → Size-on-one-line (#5).
- Verified by looking at the re-recorded editor/editor-empty/editor-wide goldens (baseline + font2.0):
  no blank strip, plot-size one line (wraps cleanly at font2.0), rectangular plot clean.

**⭐NEW verification method (owner #11) — `tools/grid-prototype/sim.mjs`:** a headless behavioural mirror
that ports the EXACT post-fix logic (pure maths + the finger/coordinate pipeline: handleCentre, hitTest,
placementAt, the drag arbiter) WITH rectangular grids + plot resize (the dimension the old fixed-8×8
prototype lacked), then fuzzes random ORDERS of operations, asserting invariants after every step —
including **draw==hit at every room centre** and **a selected room's centre always moves** (the two
checks that pinpoint #6.2/#7). Deterministic seeds → every failure reproduces.

**v0.3.8 — two residual bugs the harness found (that v0.3.7 still had):**
1. **1×1 room centre resized instead of moving** (owner #7 on tiny rooms): the edge-clamp pulls a 1×1's
   single grip far enough inward to swallow its own centre. Fix = a grip only wins when the finger is
   CLOSER to that corner than to the room centre → the middle always moves, at any size/density.
2. **Door left off the footprint after a plot resize** (order-dependent, #9): `updateGrid`'s repack can
   shrink/shift the footprint past the door, but only `updateRooms` re-clamped the door — the F4 fix
   missed the plot-resize path. Fix = `resolveGridResize` now re-clamps the door onto the repacked
   footprint (new `GridResizeTest` case pins it). Fuzz now clean over 120k random operation-orders.

**Still on-device only (can't be faked headlessly):** raw drag feel/haptics/lag, true process-death,
a real TalkBack pass. The grip-alignment + move-vs-resize + reordered-tools fixes aren't in the goldens
(harness renders the editor unselected) — they're the owner's phone-test proofs.

---

## Autonomous harness deepening + door-marker fix — v0.3.9 (2026-07-25)

Owner away; brief was to keep hunting gesture/geometry bugs with the fuzz harness, build an interactive
owner-facing harness, LOOK at it, fix what it surfaces, and hand back a final on-device checklist.

**Fuzz harness (`tools/grid-prototype/sim.mjs`) extended to close the coverage gaps** — still a byte-for-byte
mirror of the Kotlin, now three suites:
- **Suite A (editor gesture-order fuzz)** — drags are now genuinely **multi-step** (a path of 1–5
  waypoints, not a single delta), so they exercise `snapWithHysteresis`'s path-dependent state and the
  frozen-start-rect + blocked-carry machine that a single jump can never reach. Added a per-state
  **reopen round-trip** invariant: every fuzzed editor state is pushed through `buildEnginePlan →
  gridRoomsFromPlan/gridDoorFromPlan` and must come back byte-for-byte (rooms + door).
- **Suite B (resizeBy invariants)** — the corner **opposite** the dragged handle stays pinned, the rect
  never inverts below 1×1, never leaves the grid — for any delta, on rectangular grids.
- **Suite C (round-trip / translation-invariance / thin-footprint door)** — over independent random
  footprints: rooms + door survive save→reload byte-for-byte, the score is **translation-invariant**
  (normalized engine geometry is identical after any shift — the portable half of "same home scores the
  same"), and the door **side** is stable on 1-cell-thin footprints and recovers to exactly what
  `clampDoorToRooms` stores (incl. deliberately off-footprint doors).
- **All three bite** — proven by injecting deliberate faults (asymmetric row-flip, unpinned resize,
  swapped E/W door test) and watching each go red — then **all pass on the real logic at 100 000
  iterations each**. The expanded coverage found no residual gesture/geometry bug: v0.3.8's logic is
  robust across these classes.

**Interactive owner-facing harness (`tools/grid-prototype/harness.html`)** — supersedes the stale
`index.html` (a fixed 8×8 with no resize; now a redirect stub). It drives the **exact** post-fix logic
(the same ported maths + finger pipeline as the sim) with a **real pointer**, WITH rectangular plots and
the plot-size steppers, in the app's Sage & Gold theme — so the owner can feel it, and I can render it
headlessly and LOOK. It carries a **live self-check** that runs the same invariants on the on-screen
plan after every change (goes red if anything ever breaks). Rendered and reviewed at six states
(default, rectangular 10×5, a selected room with grips, a 1×1 room, a 1-cell-thin footprint, the
side-by-side notes view).

**⭐ Real bug found by LOOKING at the harness — the front-door marker drew on the PLOT edge, not the
house.** The engine **scores** the door on the rooms' footprint, `placeDoor` **clamps** it to the
footprint, and on **reopen** the plot collapses to the footprint — but the live `DoorMarker` drew the
perpendicular wall on the plot boundary. So whenever the plot was drawn larger than the house (the
default plan already does this), the door floated in the empty margin **above/beside** the rooms instead
of sitting on the house's outer wall — displayed ≠ scored ≠ reloaded, the same class C15/F4 exist to
close. **Fix:** extracted a pure `doorMarkerCell(door, rooms, cols, rows)` (PlanConversion.kt) that pins
the marker to the footprint edge; `DoorMarker` now calls it; the harness mirrors it. Regression tests in
`PlanConversionRoundTripTest` (marker lands on the footprint edge on every side incl. thin footprints,
and agrees with where the built+reopened plan scores the door). **No golden change** — the screenshot
fixture's sample home fills the 8×8 grid, so its door was already on the footprint edge; the fix only
moves the marker on plots larger than the house (none of the goldens).

**Adversarial review before tagging:** no blockers. `doorMarkerCell` is total (has `?:` fallbacks, never
called with an empty room list since the door is cleared then); the door's parallel coordinate is always
footprint-clamped upstream, so the marker sits fully on the footprint perimeter; the offset it feeds is
always a valid in-grid cell. The change is strictly "draw where it is scored/reloaded".

**Left parked at v0.3.9 (owner decisions, not touched):** the door-**side** selection still picks the nearest
**plot** wall on tap (not the nearest footprint wall), so a tap in a large empty margin can choose a side
that doesn't match the wall the user visually aimed at. It is self-consistent (drawn == scored on the
chosen side) and only misfires when the plot is drawn much larger than the house — the same S2 empty-margin
area the owner has parked. Noted as a candidate, not changed. Group D and S2/S3 remain owner calls.

---

## Autonomous pass 2 — button-path fuzz + two silent-failure fixes — v0.3.10 (2026-07-26)

No owner bug reports pending, so this continued the autonomous hunt. The four suites now run at
100 000 iterations each and are all clean; the two fixes below were found by **looking**, not by the
fuzz — the same way v0.3.9's door-marker bug was.

### New coverage — `sim.mjs` Suite D (the WCAG button paths)

The fuzz mirrored the **finger** pipeline but never the **button** pipeline, and that arithmetic is
hand-written inside the Composable rather than in the tested `shared` module:

- `SelectedRoomTools.onResize` clamps with `(w+dw).coerceIn(1, cols-col)` — a *different code path*
  from `resizeBy`, which is the only resize the gesture fuzz ever reaches.
- `RoomTile`'s semantics `onClick` is a *different selection path* from `hitTest` — no pointer maths
  at all. It is the only way a TalkBack user selects a room.
- The plot-size keys go straight to `onGridChange`.

Suite D fuzzes the move arrows, size steppers, plot keys and tile-select **interleaved with real
drags** (a user mixes both constantly; a screen-reader user uses nothing else). Three invariants were
added that *every* suite now carries:

1. **Door marker** — `doorMarkerCell` must land on the footprint wall matching the door's side, and be
   a real in-grid cell. The v0.3.9 fix had **no automated pin at all**; this is it.
2. **Plot range** — the plot never leaves `MIN_GRID..MAX_GRID`.
3. **Reopen plot** — the grid `load()` re-derives (`gridSizeForRooms`) must *contain* the reopened
   rooms. It clamps to `MAX_GRID`, so a stored room reaching past that would reopen hanging outside
   the plot, putting the finger and the drawing in different coordinate spaces — the exact v0.3.7
   class of bug, arriving through the database instead of a stepper. Unreachable today (both bundled
   samples fit 8×8 and rooms can only be drawn inside a ≤10 grid); pinned so it stays that way.

**All five checks proven to bite by fault injection** — reverting `doorMarkerCell` to the plot edge
reproduces the v0.3.9 bug and fires in ~23 % of sequences; a stepper clamped to the plot width, a
missing overlap refusal, a `cols`-for-`rows` slip in the move arrow, and an origin-only
`gridSizeForRooms` all go red. Then **all four suites pass at 100 000 iterations each**. Suite D found
no residual geometry bug: the button paths were already correct.

### Fix 1 — the plot-size keys failed silently

A plot key can decline for two reasons the user cannot see: the **rooms don't fit** at that size
(`resolveGridResize` refuses rather than overlap them, because an overlap makes the engine score the
buried room twice) or the plot is at its **4/10 limit**. In both cases the key did *nothing at all* —
the same light tap as a key that worked. Every other refusal on this screen already says "no" (an
overlapping move or resize fires `haptics.reject()`), so these were the one control here that failed
silently, and a key that cannot act reads as a broken button.

`GridResizeResult` gains **`honoured`** (false when the request was clamped to a bound and nothing
moved); `updateGrid` returns it, `null` still means "rooms won't fit", and the keys turn either into
the same "no" buzz the arrows give. The decision stays in the pure, tested layer.

### Fix 2 — the door announced "N" to a screen reader

`"Front door on the ${door.side.name} wall"` spoke a bare enum letter — the one place a raw enum
reached a user, in an app whose entire vocabulary is directions and whose audience skews older and
less phone-literate. `Zone.short()` already spells zones out ("North-East"); new `DoorSide.spoken()`
does the same, so it now reads **"Front door on the north wall"**.

### Fix 3 — the v0.3.9 door fix finally has a rendered proof

New golden **`editor-margin`**: a house drawn *smaller* than the plot with its door on the house's
north wall. No golden rendered this state before — the sample home fills the 8×8 grid, so its door was
already on the footprint edge and the screenshots could not tell the fixed and broken versions apart.
The v0.3.9 fix therefore shipped with no rendered proof in the app itself, which CLAUDE.md §2b does
not allow. A regression now puts the "D" back in the empty margin above the rooms, where it is caught
by looking.

### Tests

`GridResizeTest` pins the `honoured` contract at both bounds and on every honoured path. Two new
`GuidedGridInteractionTest` cases prove a plot key **at the limit** and an **infeasible shrink** both
report a refusal and leave the rooms untouched (the infeasible case needs two 4-wide full-depth rooms
filling the plot — with anything less, `fitWithoutOverlap` simply relocates a room and the resize
legitimately succeeds).

### Also looked at, and deliberately NOT changed

- **The zone chip can name a different zone from the report.** Already a considered decision —
  `SCORE-ACCURACY-CAVEATS.md` §2b — tied to the parked outline-capture item. Left alone.
- **"Tap the outer wall" with no outer wall drawn** (new finding **S8** in the UAT doc). The door step
  had never been rendered; doing so shows the house's outline is invisible, so the wall the
  instruction names has to be guessed whenever the plot is bigger than the house. It is the visual
  half of the parked door-side nuance and S2, and drawing a house outline is a design call on a screen
  the owner is reviewing. Documented with options, not changed.
- **S7's "score intact" note corrected** in the UAT doc: a plot shrink that clamps an oversized room
  smaller *does* move the score. Nothing is corrupted, but the wording was wrong.
- **A second finger resting on the plan mid-drag** is not consumed by the arbiter, so the page could
  scroll under an in-flight drag. Compose's own `detectDragGestures` behaves the same way and it can't
  be reproduced headlessly — added to the owner's device checklist rather than blind-fixed.

---

## ⭐ Manual-test list — `docs/DEVICE-TEST-CHECKLIST.md` (2026-07-26)

Every test that needs a real finger, a real phone or a real pair of eyes now lives in **one running
list**: `docs/DEVICE-TEST-CHECKLIST.md`. It replaces the copies that had accumulated in the UAT doc
and in these progress notes (both now point at it).

**Appending to it is part of "done".** Any fix whose proof is a haptic, a raw gesture, true
process-death, a screen-reader phrase, or "look at it" gets a row there, tagged with the build it
arrived in — so the owner can keep building a test pass at their own pace instead of the work
blocking on a device round-trip. Reported results move to its "Settled" section with a date, so a
verdict is never lost and nothing is tested twice.

It also deliberately probes the three known gaps (S2 plot margin, S3 draft lost on process kill,
S8 undrawn house outline in the door step) — the point being to learn whether they matter enough to
fix before 4 August, rather than guessing on the owner's behalf.

---

## The front-door step — S8 fixed both halves — v0.3.11 (2026-07-26)

Owner picked this over the alternatives (L-shapes, the two robustness items, more hunting) because the
front door is the **highest-weighted single input the engine scores**, and the tap that sets it could
land on a wall the user never aimed at.

### 1 · The wall a tap means is measured from the HOUSE, not the plot

The old code compared the tap against the **plot's** four edges. With a plot drawn bigger than the
house, a tap directly above the rooms could resolve to *West* — the plot's west edge happened to be
nearer than its north edge — so the door was set on a wall the user never touched, and the score moved
accordingly.

The decision moved out of the Composable (where nothing could test it) into pure
**`doorForTap(xCells, yCells, rooms)`** in `PlanConversion.kt`. Three properties:

- **No plot parameter at all.** The fix stated structurally rather than by comment: the canvas cannot
  influence which wall a tap means, because the function can't see it.
- **Signed distances**, so a tap out in the empty margin is negative for the wall it lies beyond and
  that wall wins — exactly what "I tapped above the house" should mean.
- **Fractional cells**, not whole ones. A 1-cell-deep house has its north and south walls half a cell
  apart; rounded to whole cells both distances are 0, the tie always resolved north, and **a south door
  was unreachable on a thin house**. Comparing the continuous tap against the wall lines fixes it.

`placeDoor` is now a three-line binding, and `cellIndex` is gone from the door path.

### 2 · The house is outlined during the door step

The step says "tap the wall of your home" — and that wall (the rooms' footprint) was never drawn, so
with a plot bigger than the house the user had to imagine it. The Canvas now strokes the footprint in
`primaryDark` at focus width, with the rooms' corner radius, **only in door mode**: the rooms' own
borders carry the boundary while placing, and a second always-on frame would compete with them. Copy
updated to name it — *"Your home is outlined below. Tap the wall where your main entrance is."*

⚠ The outline is the footprint **bounding box**, so a home with a gap between rooms reads larger than
the rooms do. That is honest — it is precisely the rectangle the engine scores — and it has a useful
side effect: the Group D "L-shapes are scored as a filled rectangle" caveat is now **visible on
screen** instead of living only in the report text. Flagged for the owner's eye (checklist B6).

### Proof

- **5 new `PlanConversionRoundTripTest` cases**: a tap beyond each wall picks that wall; the plot plays
  no part; taps inside pick the nearest wall; a thin house accepts a south door; and every tap in a
  441-point sweep across the plot (all four margins included) is *already* footprint-clamped and
  survives the reopen flip byte-for-byte.
- **New fuzz invariant** (`sim.mjs` Suite C): sweeps taps over the whole plot for every random
  footprint and asserts a tap strictly beyond one wall chooses that wall, and that every tap is
  footprint-clamped. **Both halves proven to bite** — re-measuring to the plot edges fails ~89 % of
  footprints (first failure reads *"beyond S but chose W"*, literally the bug), and rounding the tap to
  whole cells fails ~66 %. All four suites then clean at 100 000 iterations each.
- **New golden `editor-door`** — the door step had never been rendered in the app. It needed a small
  seam: `startInDoorMode` on `GuidedGridContent` (a golden can't press a button to get into the mode,
  and it's the entry point a future "move the front door" shortcut would use anyway).
- Rendered and **looked at** in the interactive harness first: outline hugs the rooms, copy names it.

### Mirrors

`doorForTap` and the outline are in `sim.mjs` and `harness.html` too, so the three stay in lock-step.
One harness-hygiene fix while there: the tap sweep now reports only the first offending tap per
footprint — a real regression trips hundreds and buried the useful line in a wall of text.

### ⚠ Release gotcha — never tag the CI goldens commit (it carries the CI-skip directive)

**v0.3.11 was tagged and no release was ever built.** The tag landed on `2ec014f`, which is CI's own
auto-commit *"ci: record screen render goldens **[skip ci]**"*. GitHub honours `[skip ci]` for **tag
pushes too**, so `release.yml` was silently skipped — no run, no release, no error anywhere.

It bites specifically when a build adds or changes a golden: CI records the PNGs, commits them with
`[skip ci]` (correct — it stops CI re-triggering itself forever), you pull, and `HEAD` is now that
commit. Tagging `HEAD` at that moment tags an un-runnable commit. Earlier tags escaped it only because
`HEAD` happened to be a normal commit.

**Rule: before tagging, check `git log -1 --format=%s` for `[skip ci]`.** If it's there, put a real
commit on top and tag that. Re-dispatching `release.yml` by hand is the other option, but it needs an
Actions API write.

v0.3.11 is left as a dangling, never-built tag (deleting a pushed tag is a rewrite for no benefit);
**v0.3.12 is the same content, shipped from a normal commit.**

### ⚠ And a process fix on my side: no unbounded poll loops

The wait-for-release loop was `while true … sleep 30` with no iteration cap, so when the run it was
waiting for never appeared it spun for over an hour instead of failing loudly. **Every poll loop gets a
bounded iteration count and prints a TIMED-OUT line**, so "the thing never happened" is reported rather
than waited on forever. (Related: the earlier CI poll used `grep -m1 '"status"'` on raw Actions JSON,
which matches a *nested* status field and reported "completed" while the run was still going — parse
the JSON and filter by `head_sha` + workflow `name` instead.)

**Postscript — I then reproduced the bug while documenting it.** The commit that wrote up the rule
quoted the directive verbatim in its own subject line, so GitHub skipped that commit's CI *and* the
v0.3.13-predecessor tag pointing at it. Two tags (v0.3.11, v0.3.12) are dangling and never built.
**The directive is matched anywhere in the commit message, including a quotation of it.** Never write
it in a commit message — refer to it as "the CI-skip directive". Pre-tag guard, now mandatory:

```
git log -1 --format=%B | grep -Ei '\[(skip ci|ci skip|no ci|skip actions|actions skip)\]' && echo REFUSE
```

---

## Scan your plan — the pure layer (2026-07-29)

Build-order steps 1 and 2 of `docs/SCAN-PLAN-READING-PLAN.md`. **No key, no network, no Android** —
all of it runs in `:shared:test` and in the fuzz harness, which is the point: this is where
essentially all of scan's correctness risk lives, and it is provable at zero cost before an account
exists.

### What landed

| File | What it is |
|---|---|
| `shared/…/scan/ScanTypes.kt` | `ScanDraft`/`ScanBox` (the wire format, field-identical to the recorded replies) · `ScanOutcome` = **Placed · Assisted · Refused** · `PlanReader` |
| `shared/…/scan/RoomLabels.kt` | printed captions → the existing 19 `RoomType` values. **No new types.** |
| `shared/…/scan/ScanMapper.kt` | the whole normalised-boxes → cell-rects pipeline, pure |
| `shared/…/scan/FakePlanReader.kt` | replays the three **real** Groq replies from `tools/scan-eval/out/` |
| `shared/src/main/resources/scan/*.json` | those replies, copied verbatim (token usage and all) |
| `tools/grid-prototype/sim.mjs` | **Suite E** — random model output in, editor-legal rooms out |

### The two decisions that needed real numbers

**1. The coverage threshold is `0.577`, and it came from the corpus.**
The 23 labelled 2D plans in `tools/scan-eval/out/real-plans.json` read
**min 0.204 · p25 0.388 · median 0.440 · p75 0.577 · max 0.760**. 0.577 is that upper quartile: the
top ~26 % of real reads get their geometry trusted, the rest hand their rooms over unplaced. That is
the measured product conclusion (§3h) expressed as a constant — **Assisted is the primary mode, not
the fallback.** A test pins the number so the ~0.85 figure from the early drafts (calibrated on a
synthetic fixture, would have rejected all 30 real plans) cannot come back by accident.

It also classifies both cases that *have* ground truth correctly, and the threshold was derived from a
different corpus before those were looked at, so this is a check rather than a fit:

| Recorded reply | Coverage | Measured accuracy | Outcome |
|---|---|---|---|
| clean render | 1.000 | 8/8 rooms, IoU 0.79 | **Placed** |
| downscaled + JPEG | 1.000 | 6/8, IoU 0.73 | **Placed** |
| simulated phone photo | 0.569 | 2/8, IoU 0.37 | **Assisted** |

⚠ The photo lands 0.008 below the gate. Narrow, and stated rather than hidden — coverage separates
good reads from bad ones *within* a corpus, and the synthetic and real corpora sit on different
scales. Experiment E3 (judging the 24 real 2D plans by eye) is what would tighten it.

**2. ⭐ §7's worry is answered: `MAX_GRID = 10` is enough, so the editor needs no change.**
The plan doc flagged that a scanned 3BHK might not fit in 10×10 — which would have meant touching the
editor the owner put on hold. Measured instead of assumed: **a scan draws on 10×10, not the editor's
hand-drawing default of 8.** At 8×8 the recorded `plan-01` read loses its toilet *and* its bath — both
0.1 of the plan deep, so 0.8 of a cell, and both round to nothing. Two rooms weighing 2.5 + 0.8
vanishing from a paid score. At 10×10 all eight survive, and so do all twelve rooms of a dense 4BHK
fixture. `ScanGridConstantsTest` in `:app` fails the build if the mirrored bounds ever drift.

### Things that are deliberately not what they look like

- **`fitWithoutOverlap` is NOT reused.** It *relocates* a room to the nearest free slot — right when a
  finger is on it, wrong for a scan, because it would silently move a kitchen the user has never seen
  and the kitchen's zone is a scored input. Scan **trims** the lower-confidence room along whichever
  single edge costs it least, and flags it. Suite E's `TRIMMED-MOVED` invariant exists purely to stop
  a future edit from "simplifying" this back.
- **Per-room confidence is used, plan confidence is not.** Deciding which of two overlapping rooms
  gives way is a *relative* comparison inside one reply. Gating quality on a self-report is what S2
  forbids, and `planConfidence` was 0.95 on a 100 % read, a 25 % read and on fabricated geometry.
- **Sub-areas are dropped by geometry, not by name** (owner decision D1) — but every drop is recorded
  with its reason and shown to the user. ⚠ Accepted cost: an *attached* toilet drawn slightly inside
  its bedroom will be dropped as a sub-area. It appears in the "we also saw…" list rather than
  vanishing, and the user confirms every room before anything is scored.
- **A missing `planType` means "not stated", never "reject".** The §3e fixtures predate the triage
  prompt; only a *positive* 3D/not-a-plan classification refuses.

### Proof

- **55 unit tests** across `RoomLabelsTest` (real captions off the corpus), `ScanMapperTest`
  (adversarial: NaN, inverted rects, off-page boxes, 40 rooms, everything stacked, unknown captions,
  duplicate labels, one room filling the plan) and `RecordedScanTest` (the three real replies).
- **Suite E, clean at 100 000 random replies** — mix 38 105 placed · 43 438 assisted · 18 457 refused.
  Asserts: no overlap · nothing off-grid · nothing under a cell · a trimmed room never moves from
  where it was read · reopening keeps every room inside the plot · every tap anywhere lands a door on
  the house's own wall (the v0.3.11 S8 guarantee, now reached through a plan nobody drew) · mapping is
  deterministic.
- ⭐ **Every invariant proven to bite, 7 of 7**, via `--inject=`:

  | Injection | Fires |
  |---|---|
  | `repack` (use the editor's relocating packer) | `TRIMMED-MOVED` |
  | `no-trim` | `OVERLAP` |
  | `keep-degenerate` | `SUB-CELL` |
  | `no-clamp` | `OFF-GRID` + `REOPEN-OUTSIDE` |
  | `grid-unclamped` | `PLOT-RANGE` |
  | `no-uniform-gate` | the fabricated-geometry pinned case |
  | `no-coverage-gate` | **the real phone-photo reply passes as Placed** |

  The last one is the strongest evidence the gate is load-bearing: remove it and a read that got 2 of
  8 rooms right ships as a trusted layout.

### ⭐ And a gap closed on the way: the fuzz harness now runs in CI

Suites A–D have each caught a real bug (the stale plot-size coordinate bug, the 1×1 grip swallowing a
room's centre, the door left off the footprint after a plot resize) — and **none of them gated
anything**. They ran when I remembered to run them. `ci.yml` now runs the whole harness on every push;
it costs ~20 s.

### Verified numerically before pushing, not in CI

Every one of the 25 mapper cases was run through the JS mirror first and its outcome, coverage, area
variance and resulting cell rectangles checked by hand. That caught three wrong expectations (a
sub-area drop I had written as a degenerate drop, an "everything overlaps" case that actually trips
the fabrication detector, and the 8×8 rounding above) **before** a CI round-trip rather than after
three of them.

### ⚠ Two gotchas that cost a CI round-trip

**1. ⭐ Kotlin block comments NEST — unlike Java's.** A KDoc line reading
``Mirrors the on-disk shape of `tools/scan-eval/out/*.json` `` contains a slash-star inside the glob.
That opens a *second* comment; the closing delimiter shuts only the inner one, and **the entire rest
of the file is swallowed as comment**. The compiler reports `Syntax error: Unclosed comment` at the
last line of the file, which points nowhere near the cause. Never write a `*`-glob in a Kotlin
comment — name the directory instead. Cheap local guard before pushing:

```
grep -rn '[^ /]/\*' <changed .kt files>
```

**2. A JUnit4 test method must return void.** `fun x() = runBlocking { … }` returns whatever the block's
last expression evaluates to — and `assertIs<T>(…)` returns the cast value — so the test is silently
rejected at runtime with "Method should be void". Use `runBlocking<Unit> { … }` when a suspending test
ends on an assertion that returns something.

---

## Scan your plan — the screen (2026-07-29, v0.3.14)

Build-order step 3. "Upload a plan" is live on Add home and leads all three method cards, because it
is the shortcut — with a subtitle that promises only the measured strength: *"we read the room names,
you place them"*.

### Six states, and the guided grid offered on every one

| State | What it says | Why |
|---|---|---|
| Idle | the ask + "what works best" | steers to a **PDF**: skew is what ruins a read, and a PDF has none |
| Reading | progress | |
| Done · Placed | "We read N rooms" | coverage cleared the gate; geometry is trusted |
| Done · Assisted | "We found N rooms" | **the expected outcome**; claims the list, claims nothing about placement |
| Done · Refused | one message per reason | each names the single thing the user can change |
| Busy / Unavailable / BadImage | a wait, not an error | |

The grid is on every state because it is both §6.2b's "fall back without an error state" and the
offline alternative DPDP consent has to have.

### Decisions worth recording

- **`PlanReader` now returns `ScanResult`, not `ScanOutcome`.** "What the plan says" and "whether we
  could ask at all" are different things, and only one is worth retrying. Rate limiting is therefore a
  state in the domain rather than an exception caught in a Composable.
- **Unplaced rooms arrive as a strip of single cells.** Uniform, obviously provisional, visibly not a
  floor plan, and non-overlapping by construction. It buys the user the room list and the types — the
  two slowest steps — while placement stays with the person who knows the answer. ⚠ **This is the main
  open product question**, and it is checklist row G4 for the owner to judge on a real phone: is a row
  of squares to drag genuinely better than an empty grid?
- **`PickVisualMedia` asks for no runtime permission**, which matters for a feature whose whole problem
  is asking someone to trust us with their home's layout. PDFs come through `OpenDocument`, because the
  photo picker will not offer them and a PDF is the input we most want.
- **1400 px / JPEG 88** are exactly what `tools/scan-eval/batch-real.py` fed the model when the 30 real
  plans were measured. Changing either invalidates the accuracy figures the feature is designed around,
  so they are named constants with that written next to them.
- **The reader in the graph is still `FakePlanReader`.** The whole flow is tappable and reviewable on a
  device before a paid call is made — and before §6.2's key-location question has to be answered.

### ⚠ A bug found in my own wiring during the pre-tag review

`updateGrid` re-packs whatever is already placed and **refuses** a size the existing rooms cannot fit.
So on the path *grid → draw some rooms → back → Upload*, the resize could silently decline while the
scanned rooms — sized for the grid we asked for — went in anyway and landed **outside the plot**. That
is the v0.3.7 coordinate-space bug arriving by a new road. Fixed by clearing the rooms first: an empty
plot always resizes, and clearing is right on its own terms since a scan replaces the home.

### Proof

- **160 tests, 0 failed** across the pure modules — the CI log now prints the per-class breakdown, so
  the scan figure (8 + 13 + 32 in `:shared`, plus 2 in `:app`) is checkable rather than claimed.
- **7 new render goldens × 11 configurations.** The Placed and Assisted ones are driven by the **real
  recorded replies through the real mapper**, so they show what a user actually sees and they move if
  the mapper's behaviour moves.
- **Looked at** (CLAUDE.md §2b): `scan-idle`, `scan-placed` and `scan-assisted` at baseline, and
  `scan-busy` at 200 % font. Copy holds, buttons wrap instead of shattering, nothing clipped.
- **The editor is untouched.**

### ⚠ Not yet done, and load-bearing

**DPDP consent (§6.3) is not built.** It is harmless today *because the reader is fake and nothing
leaves the phone* — but it must land **before** `GroqPlanReader` is wired, not after. That ordering is
the next step's first requirement, not a nice-to-have: a floor plan is personal data under the DPDP
Act, and scan is the first feature that would send one off the device.

---

## ⭐⭐ E3 — the coverage gate was wrong, and only pictures showed it (2026-07-30)

The owner pushed back: *"you're telling me we can only extract the number of rooms but can't place
them correctly?"* That was worth checking, because **nobody had ever looked at a single real
placement.** `batch-real.py` recorded room counts, names and a coverage number, and threw the
rectangles away. Every claim about placement rested on one synthetic fixture.

`tools/scan-eval/exp-place.py` re-runs the same plans with the same prompt, **keeps** the rectangles,
and draws them back over the original image. Overlays in `out/overlay/`. Judged by eye:

| plan | rooms | coverage | placement, by eye | old gate did |
|---|---|---|---|---|
| plan-008 | 11 | 0.390 | **all 11 boxes on the right room** | ❌ threw it away |
| plan-006 | 10 | 0.421 | good — 7 of 10 | ❌ threw it away |
| plan-018 | 15 | 0.204 | bad — boxes over a 3D render and a legend | ✅ |
| plan-003 | 17 | 0.574 | bad — a "balcony" landed on the title block | ✅ |
| plan-005 | 21 | **0.760** | mixed — dining, kitchen, puja all wrong | ❌ **trusted it** |
| plan-002 | 24 | 0.421 | bad — scattered | ✅ |

⭐ **Coverage is not mis-calibrated, it is non-monotonic with quality.** Two plans with *identical*
coverage placed well and badly. The corpus's *highest* coverage placed worse than its
lowest-but-one. A simple house with a garden and a porch legitimately has a lot of area that is not a
labelled room, so a good read of a real home scores LOW. The gate was wrong in **both** directions.

**Room count separates cleanly where nothing else does** — good 10–11, bad 15–24, no overlap. Run
against the eye verdicts, every other cheap signal overlapped: coverage (good 0.39–0.42 vs bad
0.20–0.57), area-CV (0.63–0.84 vs 0.44–0.71), overlap ratio, and grid-snappiness — which turns out to
measure *how tidy the plan's proportions are*, not whether the read was invented (the two CORRECT
synthetic reads snap at 1.00; the wrong one snaps at 0.00).

Room count is also the real distinction the eye is picking up: a **single-family house plan** names
10–12 spaces; an **apartment floor plate** names 17–25, thick with ducts, shafts, lobbies and lifts.
And it is what the published benchmarks predict — these models degrade as the number of separate
items to count grows.

### The change

`PLACED_COVERAGE = 0.577` → **`MAX_TRUSTED_ROOMS = 12`**. Coverage is still measured and carried in
`ScanNotes`, with "this decides nothing" written on it, because it is worth being able to answer "why
did it do that?". `AssistReason.LOW_COVERAGE` → `TOO_MANY_ROOMS`, with copy that explains it in the
user's terms ("that's usually a whole floor of flats rather than one home").

⭐ Roughly **40 % of the real corpus now gets its rooms placed, up from 26 %** — and, more to the
point, the *right* 40 %.

### ⚠ Three limits, stated rather than buried

1. **6 plans judged, 2 of them good.** 13 and 14 rooms were never observed, so the cut between 11 and
   15 is a judgement. It is still infinitely better than the previous basis, which was zero.
2. **A badly skewed photo of a simple plan is now trusted** — 8 rooms, under the gate, and only 2 of
   8 placed right. **Nothing available catches it**: its coverage (0.569) sits *above* both real plans
   that placed perfectly, so no coverage threshold could separate them. Pinned as a test so the
   trade-off stays deliberate. The mandatory confirmation step is what catches it — which is exactly
   what §6.2b requires that step to be for.
3. **Only 10 of 30 plans were re-captured.** The free tier's limiter reported a **582-second** reset,
   so the sweep could not finish in one sitting. That is more evidence for §3f's conclusion: the free
   tier cannot serve production — it cannot even complete a 30-plan evaluation.

### New fixture

`real-dense.json` — a **real** reply from a mirrored two-flat floor plate (24 spaces, names perfect,
rectangles scattered). It is what the Assisted render golden and the Assisted tests are driven from,
so the documented failure mode is exercised by the reply that actually exhibits it. Only the model's
reply is committed; the plan images stay out of the repo.

⚠ **Where the real-reply expectations live matters.** `sim.mjs` carries a deliberately cut-down
synonym table, and now that the gate is a room *count*, a smaller table changes the answer. So the
recorded-reply pins moved to Kotlin's `RecordedScanTest`, where the real table runs; the mirror keeps
what it can check honestly — geometry.

---

## ⭐⭐ The reader is real — v0.3.16 (2026-07-30)

The owner tried the shipped build with several different plans and got the same room list every time.
He was right, and the reason is not subtle: **the app was never looking at his images.**

`AppModule` bound `FakePlanReader`, which replays four recorded replies in order and ignores the
image bytes entirely. Three of those four are the *same* synthetic test plan (`plan-01` clean, JPEG'd
and photographed), so uploads one, two and three produce an identical eight-room list — and because
the reader is a Koin `single`, closing the app resets the cursor and the first fixture comes back
again. Every visible part of the screen worked: the picker opened, the PDF rendered, a progress
spinner ran, rooms landed on the grid. The one thing that never happened was the read.

⚠ **The caveat existed in `docs/DEVICE-TEST-CHECKLIST.md` and nowhere the owner would meet it.** A
build that cannot do the thing must not be indistinguishable from a build that can. That is the
lesson, and it is now enforced in three places rather than written down in one:

| Enforcement | What it does |
|---|---|
| `ScanUiState.NotConfigured` | a build with no key **says so on the screen** and offers no picker |
| `FakePlanReader` unbound from the app | recorded replies drive tests and goldens; the app reads or admits it cannot |
| `scan-not-configured` render golden | the state is rendered and looked at, like every other screen |

### What landed

| File | What it is |
|---|---|
| `shared/…/resources/scan/reader-config.json` + `plan-read-prompt.txt` | model id, endpoint, reasoning effort, timeouts, User-Agent — **data, not constants** — and the prompt as prose |
| `shared/…/scan/ScanReaderConfig.kt` | fail-loud loader, same contract as the ruleset loader |
| `shared/…/scan/GroqWire.kt` | **pure**: request body, envelope unwrapping, HTTP status → user-visible state, rate-limit duration parsing |
| `shared/…/scan/GroqPlanReader.kt` | the transport. One POST, never throws, never logs, dispatches its own IO |
| `app/…/ui/scan/PlanReadingConsent.kt` + `ScanConsentScreen.kt` | the DPDP gate, and it is a **route**, not a dialog |
| `app/…/AndroidManifest.xml` | `INTERNET` — see below |
| `scripts/check-manifest.sh` | the guard that stops the below happening twice |

### ⭐ Caught by looking, not by the build: the app had no INTERNET permission

Scan was written, reviewed, unit-tested and one commit from being tagged while
`AndroidManifest.xml` declared **no permissions at all** — correct for an offline app, fatal for this
feature. Nothing in the toolchain would have said so: it compiles, every test passes, the render
goldens are byte-identical, and the failure on a real phone is a polite *"we couldn't read your plan
just now"* on every attempt by every user forever, which reads as a flaky server rather than as a
missing line of XML. It would have cost another release and another evening of the owner's time.

`scripts/check-manifest.sh` now fails the build if it disappears, **and** fails if a permission this
product has no business holding appears (location, camera, contacts, storage, phone state) — a merged
manifest can gain those without anyone deciding to. Proven to bite by deleting the line.

### Verified against the live API before a line of Kotlin was written

`qwen/qwen3.6-27b`, `reasoning_effort: none`, `User-Agent: curl/8.4.0`, two real plans on
2026-07-30: **HTTP 200**, 2 143 prompt + 751 completion = 2 894 tokens. The recipe measured on 29 July
still holds, and the Kotlin is written against an observed response rather than a remembered one.

⚠ **It also re-measured the limit that matters.** After **two** scans the live headers reported
`x-ratelimit-remaining-tokens: 825` against a 2 894-token cost — so on the free tier the **third scan
within a minute is refused**, across all users, not the third hundred. `x-ratelimit-reset-tokens` came
back as `53.812s` and `x-ratelimit-reset-requests` as `2m52.8s`, which is why the wait is parsed from
a duration string rather than read as a plain number of seconds.

### The prompt is unchanged, deliberately

Numbered-legend resolution (owner decision D2) is **not** in the shipped prompt. Every accuracy figure
this feature is designed around — 24 of 30 real plans classified 2D, names read excellently, placement
trusted only under 12 rooms — was measured with exactly these words. Changing the prompt and the
transport in the same build would leave no way to tell which one moved the result. Prompt work is
build-order step 6, measured against the corpus, on its own.

### Proof

- **178 tests, 0 failed** across the pure modules (up from 161), of which **17 are new**: 12 in
  `GroqWireTest`, 5 in `ScanReaderConfigTest`. Plus 4 gate tests in `:app`. The request shape field by
  field (including that the prompt's quotes,
  braces and newlines survive JSON encoding — the failure mode that looks exactly like a bad model),
  a **real recorded reply travelling the whole way from HTTP body to `ScanOutcome`** and matching the
  mapper called directly, malformed replies degrading to Unavailable rather than to a refusal, every
  rate-limit duration shape, and every other status landing on Unavailable.
- ⭐ **Safety rule S1 is now mechanical.** A test asserts the prompt contains none of *vastu, zone,
  sector, auspicious, brahmasthan, direction, mandala*. It was a paragraph in a design document; it is
  now something that fails a build. The measurement behind it: asked for a room's sector the model
  returned byte-identical answers for a clean render and a badly skewed photo, with its errors moving
  toward textbook positions — a reader that nudges homes toward canonical placement inflates every
  score, and the score is the product.
- **A refusal is never used to describe our own failure.** A reply we cannot parse is Unavailable
  ("try again"), not Refused ("your plan is the problem") — the second would be a lie.
- **Gate tests in `:app`**: a keyless build reports NotConfigured from the first frame and refuses to
  decode or send anything (both test doubles throw if touched), consent starts switched off, and
  switching it off again works.
- **The duration arithmetic was checked numerically in node first** (16 cases), before pushing, per
  the standing rule — no CI round-trip spent on `2m52.8s`.
- **Two new render goldens** across the 11-configuration matrix: `scan-consent` and
  `scan-not-configured`. The consent screen is also in the ATF accessibility pass, because it carries
  more prose than any other screen and prose is where the contrast trap already bit once.

### The ratchet earned its keep, and one baseline moved

The L1 geometry gate failed the first push with **settings 1 → 3**, and it was right twice over:

1. Spelling the upload exception out in full took the closing privacy line to three lines, which
   **clipped it at 320 dp** and pushed the last row of the screen further past the fold. Fixed by
   cutting it back to one sentence — the detail belongs on the consent screen, where the decision is
   actually being made. That took it to 2.
2. The remaining two findings are the documented **below-the-fold capture artifact**: Settings now has
   one more row, so at 200 % font and at 320 dp the bottom of a scrolling screen sits outside the
   capture window.

⭐ **Adopted 1 → 2 only after looking at both configurations** (`settings/font2_0`, `settings/w320`
from the run's artifact): the new row renders correctly at both, everything is legible, and the only
"clipped" content is below the fold and reachable by scrolling. Same call, same reason, as
`marknorth 17 → 18`.

Also looked at, per CLAUDE.md §2b: `scan-consent/baseline` (card, hierarchy and the "rather not?"
alternative all hold) and `scan-not-configured/baseline` (**0 L1 findings**, fits above the fold, and
carries no picker — which is the whole point of it).

The accessibility pass adopted **`scan-consent: 0` ATF findings** and settings held at 1, so the
longest piece of prose in the app arrived with no contrast or labelling problem — which is not luck,
it is `textSecondary` rather than `textTertiary` on every body line, the lesson from the last one.

### What was verified in the shipped APK itself, and the one thing that was not

The release asset was downloaded (authenticated range request — the repo is private, so an
unauthenticated `HEAD` returns 404 even for a file that exists) and opened:

| Checked inside `vastufirst-v0.3.16.apk` | Result |
|---|---|
| The reading key reached the build | **yes** — present in the dex, 56 characters, value never printed |
| `scan/reader-config.json` shipped | yes — model `qwen/qwen3.6-27b`, reasoning `none` |
| `scan/plan-read-prompt.txt` shipped | yes — 1 139 characters, `OUTER WALL` intact |
| **Safety rule S1 in the file that actually ships** | **holds** — no Vastu vocabulary in the packaged prompt |
| `android.permission.INTERNET` in the packaged manifest | yes |

⚠ **Two methodological traps in that check, both of which produced a wrong answer first.** An APK is a
zip, so grepping the raw bytes for a shipped string finds nothing — the entries have to be
decompressed. And `AndroidManifest.xml` inside an APK is *binary* AXML with a **UTF-16LE** string pool,
so an ASCII search for `android.permission.INTERNET` says "absent" about a manifest that plainly
contains it. Both nearly became a false alarm reported as a fact.

⚠ **Noticed on the way:** the merged manifest also carries `android.permission.DUMP`, which is not in
our source manifest — it arrives from a dependency, is signature-level so it can never be granted to
this app, and is harmless in a debug test build. `check-manifest.sh` reads the source manifest, not the
merged one. **Worth re-checking against the merged manifest of the minified release variant before
store submission**, which is when it would matter.

⭐ **And the honest gap: the on-device HTTP call itself is still unproven.** The recipe is proven
against the live API from this machine, the key is proven to be in the APK, the config and prompt are
proven to ship, and the permission is proven present — but nothing here exercises
`HttpURLConnection` on Android. That is a device check (checklist G9/G10), not a claim to make from a
green build.

### Decisions worth recording

- **Consent is a navigation destination, not a dialog and not a flag check.** The only route to the
  scanner runs through it, so the ordering is structural instead of remembered. It is withdrawable
  from Settings, which is what makes it consent, and the stored key is versioned (`..._v1`) so that if
  what we send or who receives it ever changes, everyone is asked again.
- **The consent copy describes our own behaviour only.** What we send, who reads it, what it is asked,
  that we keep nothing, and that the phone still does the scoring. It makes no promise on the reading
  service's behalf, because we could not keep one.
- **The key is a build input, never a repository file** — a GitHub Actions secret in CI, a git-ignored
  `.env` locally, escaped before it is pasted into generated Kotlin. §6.2 option A, acceptable only
  because a free-tier key has no money attached; a server proxy is still required before store
  submission, and `PlanReader` is why that stays a one-file change.
- **`withContext(Dispatchers.IO)` lives inside the reader**, not in the ViewModel. A blocking suspend
  function that trusts every future caller to remember the dispatcher is one refactor away from an
  ANR. That is the only reason `:shared` gained coroutines-core — still zero Android.

---

## ⭐⭐ Every release destroyed the owner's saved homes — v0.3.17 (2026-07-30)

Owner: *"I am having to uninstall previous version everytime I install new — this means any self build
plans are lost."*

**Diagnosed, not guessed.** Both published APKs were pulled and their signing certificates compared:

| Release | Signing certificate SHA-256 |
|---|---|
| v0.3.15 | `1f3b25d8…` |
| v0.3.16 | `4dbe5b6c…` |

Different keys. **Every cloud build was signed by a different, randomly generated key**, because
`assembleDebug` with no signing config falls back to `~/.android/debug.keystore` — and a fresh GitHub
Actions runner has no such file, so AGP silently creates one, uses it, and throws it away with the
runner. Android refuses to install an APK over an app signed by a different key, so the only way in
was to uninstall, and uninstalling erases the app's database. **Nine releases, each one quietly
deleting every home he had drawn.**

⚠ **This is the third defect in this project of exactly the same shape**: green build, passing tests,
identical screenshots, and destructive on a real phone (the others: no INTERNET permission, and the
stand-in reader that looked real). None of them are code defects in the ordinary sense — they all live
in the space between "the build succeeded" and "the thing works for a person".

### The fix

- `signing/vastufirst-debug.keystore` — a stable, committed **test** key (PKCS12, 30-year validity),
  wired as the `debug` signing config. Every build from now on carries the same identity, so it
  installs as an update and the database survives.
- ⚠ **`.gitignore` had `*.keystore`, so the new key was silently excluded** — the build would have gone
  straight back to a random key with nothing to show why. Caught by testing `git add -n` rather than
  trusting the `git add` that appeared to work. One deliberate negation, with the reasoning next to it.
- `scripts/check-apk-signature.py` — parses the APK Signing Block (no v1 JAR signature exists to read
  once minSdk ≥ 24) and fails the build if the certificate is not the expected one, in **both** CI and
  release. Pure standard library, so it needs nothing installed. **Proven to bite against the real
  v0.3.16 APK**, which it correctly rejects, and proven to pass against a matching fingerprint.

### Why committing a key does not violate "no secrets in the repo"

It is a self-signed test certificate with the conventional debug password. It holds no account, no
service, no store identity, and its only power is to sign builds that Android will treat as updates to
other builds signed with it — which is the entire point. Same trust level as the debug keystore
Android Studio writes into every developer's home directory.

⚠ **The Play Store key is a different key and must never be committed.** It is created at store setup,
held by the owner, and supplied to the build as a secret. Switching to it costs one final reinstall on
the two sideloaded test phones and nothing at all for real users, who will install from the Store
rather than update a sideloaded APK.

### The cost that cannot be avoided

v0.3.16's random key is gone with the runner that made it, so **this one last install needs an
uninstall**. From v0.3.17 onward it updates in place. Said plainly in the release notes, the client
note and the device checklist, because "you will lose your data one more time" is not something to
leave for someone to discover.

---

## The build now checks that its reading key works — v0.3.18 (2026-07-30)

The owner rotated the key in the repository secret, which means the APK published minutes earlier
carried the previous one. That exposed a gap: **nothing about a green build said whether the key baked
into it is accepted.** A rotated, revoked or mistyped key compiles, tests, renders and ships
identically to a working one, and then tells every user *"we couldn't read your plan just now"* on
every attempt, forever. The fourth defect in this project with that shape.

`scripts/check-plan-reader-key.py` runs in CI and release. It lists the account's models — no tokens,
because it never reads a plan — and asserts two things:

| Condition | Verdict | Why that way |
|---|---|---|
| No key at all | **pass** | a keyless build is supported; the app says it cannot read |
| Key rejected (401/403) | **fail** | this APK would fail every scan |
| **Pinned model not served** | **fail** | how a retirement arrives; both Llama 4 vision models died mid-2026 |
| Network error or 5xx | **pass**, with a warning | Groq's uptime is not our build gate |

⭐ **Proven to bite on both real failure paths**, not reasoned about: a fabricated key returns HTTP 401
and fails, and pinning the retired `llama-4-scout` fails with the served list printed. The listing also
re-confirms the standing constraint — of the 15 models this account can reach, `qwen/qwen3.6-27b` is
still the only one that takes an image.

⭐ **And CI going green is now the proof that the owner's new secret is valid**, without the key ever
being seen, printed or logged. That is the useful property: the question "did the key get set
correctly?" is answered by the build instead of by the client's first scan.

---

## ⭐⭐ The first real user scan — three label defects (2026-07-30)

The owner scanned a Gurgaon 2BHK (Green Court, Sector 90 — a builder unit plan) and sent the result
alongside the source. Comparing them caption by caption found three defects, two of which move a paid
score. **All three are in label reading, none in the model**: the model read every caption on that
sheet correctly.

### 1. ⭐ A full stop deleted a toilet

The plan prints its second toilet as `W.C 4'-11"X6'-4½"`. The cleaner replaced the abbreviation stop
with a **space**, so the caption became `W C` — and `WC` is in the table while `W C` is not. It
resolved to "unrecognised" and the room was dropped. **The flat has two toilets and the app placed
one.** A toilet is weighted 2.5 and its zone is among the most consequential in Vastu, so this
silently changed the number the customer pays for.

Deleting the stop instead of spacing it is strictly better: `W.C` → `WC`, while `ATT. TOILET` still
becomes `ATT TOILET` because the space after the stop is its own character.

⚠ **Honest measurement of the blast radius**: across the 30-plan corpus this recovers exactly **one**
caption, because only `plan-016` prints a dotted `W.C`. So it is not a widespread reading failure — it
is a rare one that silently deletes a scored room when it happens, and it has now happened on two real
plans out of thirty-one.

### 2. ⭐⭐ "Lobby" — and the fix that measurement rejected

The owner's complaint: `LOBBY 10'-3½"X14'-10½"` became a **Corridor**. On his plan that is plainly
wrong — it is the largest room in the flat, drawn with sofas and a dining table, on a sheet whose own
legend describes the unit as *"2 Bedroom + Drawing cum Dining Room"*. LIVING is weighted 1.5 against
CORRIDOR's 0.8.

**The obvious fix was to map LOBBY to LIVING. Measuring it against the corpus showed that would be
wrong more often than the bug was.** Six of the 30 plans print a lobby, and **four of those six also
print a separate living room**:

| Plan | Its lobby | Its living room |
|---|---|---|
| plan-002 | `LOBBY 5100X1800` — a 5.1 m × 1.8 m passage | `LOUNGE`, `LIVING 3925X5000` |
| plan-003 | `LOBBY 5600X7700` | `LIVING ROOM 3500X6700` |
| plan-004 | `LIFT LOBBY` (already dropped) | `LIVING ROOM` |
| plan-020 | `LOBBY/DINING` | `DRAWING ROOM` |
| plan-022 | `ENTRANCE LOBBY` | `LIVING RM.` |
| plan-026 | `LOBBY/DINING 17'1"X9'10"` | none |
| **Green Court** | `LOBBY 10'-3½"X14'-10½"` | **none** |

⭐ **The signal is not the caption, it is the rest of the plan.** A lobby is the living room when the
plan names no other one, and circulation when it does. That reads six of the seven correctly; the
seventh (plan-003, which has both a living room and an unusually large lobby) is ambiguous to a human
too. So `RoomLabels.resolve` now takes a `LabelContext` built once per reply, and **either way the
room arrives flagged "CHECK"** — the word is genuinely ambiguous and the user has the last word.

`LIFT LOBBY` still drops as a service core, and `ENTRANCE LOBBY` / `ENT. LOBBY` now match exactly as
an entrance before the ambiguous rule is consulted.

### 3. A clear caption was asking to be checked

`BALCONY 5'-0" WIDE` cleaned to `BALCONY WIDE`, matched nothing exactly, resolved through the
substring path and arrived with a "CHECK" against it — asking the user to verify something obvious,
which spends the one thing that flag is for. `WIDE` is now a descriptor word and a token that is only
a measurement (`1500`, `1500MM`, `12FT`) is dropped, so `BALCONY 1500MM WIDE` also reads cleanly.
Across the corpus, 6 % of captions now carry a check flag.

⚠ `AREA` is deliberately **not** a descriptor — `WASH AREA` and `DINING AREA` are real room names.

### What was NOT changed, and why

- **The room positions.** The balcony should span the north edge and was placed top-right; the rooms
  leave large gaps. That is the measured limit of the model's spatial reading (§3h), it is why the user
  confirms every room, and no label fix touches it.
- **The selected-room chip covers the top row of the grid.** Visible in the owner's screenshot — the
  "Corridor · 3×4 · South-East" pill sits over the Balcony. It is real, it is in the guided-grid editor,
  and the editor is on hold by his instruction. **Surfaced, not touched.**
- **Neither bedroom is a master bedroom.** The plan labels both `BED ROOM`; the engine weights
  MASTER_BEDROOM 3.0 against BEDROOM 1.5, so which one is the master materially moves the score, and
  nothing asks. Guessing would be a Vastu judgement the model must never make (S1). **Owner decision.**

### Proof

- Every caption on the owner's actual plan is now a test, so that scan cannot silently read differently
  again. Plus the corpus's real lobby captions, both `LOBBY/DINING` forms, `ENT. LOBBY`, and the
  reverse case where a lobby beside a living room must stay a corridor.
- The whole resolver was mirrored in Python **by parsing the tables out of the Kotlin itself**, so the
  mirror cannot drift from the source, and all 22 expectations were checked before pushing — including
  the ones I might have broken (`MASTER TOILET`, `PUJA SPACE`, `SER ROOM`, `DRESSING`, `PASSAGE`).
