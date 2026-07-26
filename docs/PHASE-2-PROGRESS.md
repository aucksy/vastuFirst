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
