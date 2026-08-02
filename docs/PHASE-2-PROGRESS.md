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

### Two CI round-trips, and what each one was worth

⚠ **The mirror only covered the cases I thought of.** Two *pre-existing* expectations failed in CI:
`5'-0" WIDE BALCONY` cleaning to `WIDE BALCONY`, and the test proving substring matches are flagged,
which used that same caption as its example. Both were expectations encoding the old behaviour rather
than regressions — but I should have found them here, not on the runner. The mirror now **parses every
assertion out of the test file itself** (71 of them) instead of relying on a hand-picked list.

⭐ **The second round-trip was the accessibility gate earning its keep.** With `LOBBY` now flagged, a
"CHECK" pill moved above the screenshot fold for the first time — and the pill **had been failing the
contrast check since it was written**. Measured: the accent colour on a tint made from the same accent
is **3.71 : 1** where 4.5 is required at that text size. `textSecondary` on the same tint is **5.82 : 1**
(6.68 on a raised card), so the tint keeps the pill's identity and the word becomes readable.

That is the second contrast defect on this screen and both were on the load-bearing copy — the caption
we read off the user's plan, and now the flag asking them to check a specific room. **A "CHECK" nobody
can read is the same as no flag at all.**

### Looked at, per CLAUDE.md §2b

The re-recorded `scan-assisted` golden shows the rule working on a real 24-space floor plate:
`LOBBY 5100X1800` reads as **Corridor** — correctly, because that plan also prints `LIVING 3925X5000`
and `LOUNGE` — and carries a legible CHECK. Same input, opposite answer to the owner's plan, which is
the whole point of resolving the caption against its plan rather than on its own.

---

## ⭐⭐ The user can change a room's kind — v0.3.20 (2026-07-31)

Owner: *"the user must be able to change a room's name manually."* Two questions asked before
building, and both answered: he means the room's **type** from the app's fixed list (the thing that
changes the score), not a free-text nickname; and he wants it in **both** places — on the grid and on
the "we read N rooms" list after a scan.

### ⭐ The gap was bigger than "there is no rename"

The stated workaround was "delete the room and place a new one". **For eight of the nineteen room
types that workaround does not exist.** `RoomLabels` can resolve a caption to any of the nineteen,
but `GRID_ROOM_TYPES` — the "Add a room" palette — offers eleven. Entrance, Corridor, Utility,
Bathroom, Guest bedroom, Courtyard, Garage and Basement could be read off a plan and, once removed,
could never be put back. Deleting was a one-way door.

⚠ **And a correction to this document.** The v0.3.19 note says a lobby read as a corridor costs
"LIVING 1.5 against CORRIDOR's 0.8". Checked against `rooms.json` rather than repeated: **CORRIDOR
carries no rule at all.** Fifteen of the nineteen types are scored; CORRIDOR, BATHROOM, COURTYARD and
UTILITY are not. An unruled type resolves to `NOT_SCORED` with weight 0.0 and is excluded from *both*
sides of the weighted average (`RoomEvaluator`, `Scorer`). So the largest room in the owner's Gurgaon
flat was not under-weighted — **it was not counted at all**. That makes this a bigger scoring defect
than the label fix that preceded it, and it is the honest reason the control had to exist.

### What landed

| File | What it is |
|---|---|
| `app/…/newplan/RoomRetype.kt` | `retypeRoom(rooms, id, type)` — pure, geometry-preserving, same instance when nothing changes |
| `app/…/common/RoomTypePicker.kt` | the collapsed-until-asked control, shared by both screens |
| `app/…/common/UiMappers.kt` | `ALL_ROOM_TYPES` — all nineteen, palette order first |
| `shared/…/scan/ScanCorrections.kt` | `ScanOutcome.withRoomType(index, type)` — pure, rewrites the outcome itself |
| `tools/grid-prototype/sim.mjs` | `opRetype` + four before/after checks in Suite D, three fault injections |

### Decisions worth recording

- **The retype rewrites the OUTCOME on the scan screen, not a side table of overrides.** The outcome
  is both what the list draws and what is handed to the guided grid, so correcting it in place means
  there is no second copy to keep in step and `VastuNav`'s handover needed no change at all.
- **A wrapping list, never a side-scrolling strip.** The room's present kind is shown selected, and in
  a scrolling strip that chip is off-screen for anything past about the sixth kind — the control would
  open looking as though nothing were chosen, on exactly the plans where the reading was wrong.
- **Collapsed by default.** Nineteen chips is four or five wrapped lines; parking that permanently in
  the selected-room panel would push move and size off a small screen for everyone, to serve a
  correction most rooms never need.
- **Picking the kind a room already is closes the list and changes nothing** — the way out without
  committing, so opening it is never a trap.
- **The flags that were *asking* are cleared; the ones about *shape* are not.** `LOOSE_LABEL_MATCH`
  and `LOW_MODEL_CONFIDENCE` both mean "we are unsure we read this right", which the user has now
  answered, so the CHECK stops asking. `OVERLAP_TRIMMED` / `OUT_OF_BOUNDS_CLAMPED` are about the
  room's size and survive.
- **No new room types**, per the standing rule — every kind offered is one the engine already scores
  or already ignores. No rules change, no expert ruling needed.

### Proof

- **`retypeRoom` is pure and cannot move anything**, and that is the whole risk: a retype changes no
  geometry, so the danger is an implementation that "tidies up" afterwards. Suite D now fuzzes retype
  interleaved with drags, arrows, steppers and plot keys, with a **before/after** comparison — a
  property of the *change*, not of the resulting state, which is why no existing invariant could have
  caught it. Every injected mistake leaves the editor perfectly legal.
- ⭐ **Three fault injections, each a mistake someone would actually make, all proven to bite:**

  | Injection | The mistake it models | Fires |
  |---|---|---|
  | `retype-readds` | delete-and-re-add — literally the workaround this replaces | `RETYPE-MOVED` (756/1500) |
  | `retype-spreads` | keying the change on the room's *kind* instead of its id | `RETYPE-SPREAD` (76/1500) |
  | `retype-drops-door` | routing it through plot-resize-style update logic | `RETYPE-DOOR` (196/1500) |

  ⚠ **The first injection I wrote was vacuous and I nearly shipped it as proof.** It re-packed with
  `fitWithoutOverlap`, which leaves an already-legal layout exactly where it is — so the suite stayed
  green and would have "proven" an invariant that was never exercised. An injection that does not go
  red is not evidence.
- **All six suites clean** at 20 000 orders (50 000 for B and C).
- **New Kotlin tests**: `RoomRetypeTest` (13 — geometry preserved across every room × every kind,
  identity on no-ops, duplicate ids, the palette-gap pin, and the engine scoring the room as its new
  kind at weight 1.5) and `ScanCorrectionTest` (10 — flags, diagnostics, bounds, refusals).
  `GuidedGridInteractionTest` gains 4 driving the real screen end to end.
- ⚠ **Contrast measured before pushing, and it caught a defect.** The obvious styling for the
  "Change" affordance — the sage accent `primaryDark` — is **3.64 : 1** on that card against a
  required 4.5. That is the CHECK pill's defect (3.71 : 1) repeating on the same screen for the same
  reason. `textSecondary` measures **6.90 : 1**, so the "this is a control" signal moved to the label
  type style. The chips themselves measured clean (7.40 unselected, 13.63 selected).

### ⭐⭐ And a hole this closed on the way: the selected-room panel had never been rendered

`GuidedGridContent` only shows the selected-room panel once a room is selected, and **no golden could
select one**. So the remove/done buttons, the move arrows and the size steppers have shipped through
every build unseen, contrary to CLAUDE.md §2b — which is also why the corner grips appear in no
existing golden. Adding a control to that panel was the wrong moment to keep guessing, so
`startSelectedId` / `startTypeListOpen` join `startInDoorMode` as harness seams, and three new
goldens land: **`editor-selected`**, **`editor-retype`** (nineteen chips wrapped) and
**`scan-retype`** (the same list inside a card, which is the narrowest it has to fit). The panel also
joins the accessibility pass for the first time.

⚠ **A new screen name is AUTO-ADOPTED by both ratchets**, so a defect on a brand-new golden is
baselined rather than failed. The numbers these adopt have to be looked at, not trusted — the CHECK
pill was baselined unlooked-at exactly once already.

### The geometry ratchet fired, and one baseline moved: `scan-assisted` 3 → 11

Adopted **only after downloading the run's artifacts and reading the manifests**, not on the strength
of the failure text. In all eleven configurations the finding is the *same shape*: exactly one row is
partially visible, and its bottom edge equals the screen height to the pixel —

| config | screen | the one clipped node sits at |
|---|---|---|
| baseline / dark | 412 × 915 | y 893, bottom **915** |
| w320 | 320 × 711 | y 699, bottom **711** |
| font2_0 | 412 × 915 | y 859.5, bottom **915** |
| landscape | 480 × 854 | y 810, bottom **854** |

Everything past it reports `y=0, h=0` — never laid out, because it is off the capture window. That is
the documented below-the-fold capture artifact, and the screen scrolls, so it is all reachable. It
went from 3 configurations to 11 not because rows got worse but because one extra line of copy
("Tap any room to change what kind of room it is") shifts the list down far enough that *some* row now
lands on the fold in every configuration rather than in three of them.

**Looked at `scan-assisted` and `editor-selected` and `editor-retype` at baseline before adopting.**
The scan list renders correctly — room kind, the plan's own printed caption beneath it, CHECK where
we are unsure, "Change" at the right — and the lobby row shows exactly the case this all started
from: `LOBBY 5100X1800` read as **Corridor**, flagged, and now correctable.

⚠ **Correction to the line I first wrote here.** I said the accessibility pass returned 0 findings on
both new states. It did not — I read the wrong key out of the manifest JSON (`count`, not `findings`).
The pass returned **11 on the scan list and 8 on the selected-room panel**, and both are worth stating
properly because they are different kinds of thing.

**The scan list's 11 were mine, and a genuine defect.** Every room row tripped ATF's
`RedundantDescriptionCheck`, because I ended each row's description with *"Double tap to change what
kind of room it is."* TalkBack already announces the role and the gesture, so the user hears "double
tap" twice. The action belongs in the click-action **label**, which is what `onClickLabel` is for and
what ATF does not flag — so `clickableTap` gained that parameter (additive, defaulted, every existing
caller uses named arguments) and TalkBack now says *"…, button, double tap to change the room type."*

**The panel's 8 are almost entirely pre-existing, and only visible now because nothing had ever
rendered this panel.** Measured against the card (`#F2EEE4`):

| Element | Ratio | Needs |
|---|---|---|
| `2 × 2 · North-West`, and the `MOVE` / `SIZE` / `ROOM TYPE` labels (`textTertiary`) | **4.39** | 4.5 |
| the move arrows and size steppers (`primaryDark` on a 14 % `primary` tint) | **3.25** | 4.5 |
| `W 2` / `H 2` (`textSecondary`) | 6.90 | ✓ |
| the `Change room type` button | 13.63 | ✓ |

Exactly one of those eight is new — the `ROOM TYPE` label — and it is styled identically to the `MOVE`
and `SIZE` labels directly beneath it. Singling it out for a darker colour would fix one of three
identical labels and look like a mistake. **So it stays consistent and the finding is surfaced rather
than silently absorbed:** the small grey labels and the arrow glyphs in this panel have been below the
contrast bar since they were written, on every build, and the fix is a design-token change affecting
several screens — an owner call, not something to slip into a room-naming build.

⭐ **What the pre-push measurement did buy** was the "Change" affordance: it would have shipped as the
sage accent at **3.64 : 1**, and it is `textSecondary` at 6.90 instead.

⚠ **And the first rendering of the selected-room panel immediately showed a known open question in
the flesh** — the dark chip naming the selected room (`Pooja · 2×2 · North-West`) sits directly over
the Bedroom in the top row. That is the owner's own "the small grey tag covers the top row" report,
now reproduced in a golden rather than only in a screenshot he sent. **Not touched** — it is an editor
change beyond the room-type work he unfroze, and it is his call.

### Looked at before tagging (CLAUDE.md §2b)

`editor-selected` and `editor-retype` at **baseline** and at **200 % font**, and `scan-retype` at
**320 dp** — the two configurations where a wrapped row of nineteen chips would fail if it were going
to. It does not: at 200 % font the chips wrap two per line and at 320 dp two or three, every label
legible and untruncated, the room's present kind clearly marked, nothing clipped or overflowing. Also
`scan-assisted` at baseline, showing the lobby row reading **Corridor** with its CHECK and its
Change — the exact case this build exists to make correctable.

Final state: **193 pure tests green**, six fuzz suites clean, render ratchet no-regression, ATF
no-regression with the scan list back to **0** findings, and the APK signature check passed so this
build installs over the last one without erasing saved homes.

---

## ⭐⭐ Rooms are shaped to the sizes their plans print — v0.3.21 (2026-07-31)

Owner, looking at his own scan: *"can we not match the proportions of each section to match with
plan… the corridor is a horizontal rectangle but in the floor plan it's vertical — the direction will
place itself incorrectly over a room placed with incorrect orientation."* Both halves correct.

### The measurement

| room | printed | app drew | printed w/d | drawn w/d |
|---|---|---|---|---|
| LOBBY | 10'-0" × 12'-9" | 6 × 2 | 0.78 | 3.00 |
| KITCHEN | 6'-11" × 9'-7" | 4 × 1 | 0.72 | 4.00 |
| PASSAGE | 2'-3" × 9'-6" | 3 × 1 | **0.24** | 3.00 |
| BED ROOM | 10'-0" × 10'-6" | 6 × 4 | 0.95 | 1.50 |

**Four of the six dimensioned rooms had their orientation inverted.** The cause: `RoomLabels`'
cleaner deletes every measurement token, so the reader read `LOBBY 10'-0"X12'-9"` perfectly and the
size was thrown away, leaving each room's shape to come from the rectangle the reader *guessed* — the
one thing measured at 40–70 % against ~95 % for reading text.

The printed numbers are trustworthy: on that sheet they total **353 sq ft against a stated 336** of
carpet area, the 5 % being wall thickness.

### Three things this got wrong first, each caught before CI

1. ⭐ **Orientation was resolved against the reader's own rectangle** — and that quietly defeated the
   entire feature. The reader had called the passage three times wider than tall, so matching its
   sense kept it horizontal and merely restated the error at a new ratio. Trusting the **printed
   order** (first number is the width) corrects four of six; deferring to the drawing corrected
   **none**. This is now the fuzz suite's `PRINTED-ORIENTATION` invariant, and injecting the old rule
   back fires it 1 122 times.
2. **Choosing a width and then clamping the height to the grid INVERTS a tall room in a shallow
   grid** — a 2 950 × 4 200 kitchen came out 6 wide × 5 deep. Found by the new invariant on its first
   run. It now shrinks to fit *proportionally*: when area and proportion conflict, proportion wins,
   because proportion is what decides the direction.
3. **Re-shaping could bury a small room inside a grown neighbour and drop it.** A lost room changes
   the footprint the engine scores and cannot be re-added by someone who never saw it, so the read
   shape is tried before a room is given up. Exactly what happens to the owner's passage.

### ⚠ Tried, measured, and REVERTED: taking the grid from the home rather than the picture

Re-framing every box onto the rooms' bounding box reads well for a branded sheet — his devotes its
left third to a logo. It was reverted for two reasons, both found rather than reasoned:

- it makes every room's size depend on every other room's, so one stray rectangle over a title block
  shrinks the whole home; and
- ⭐ **it silently removed an existing safety check's bite.** The fuzz suite's `no-clamp` injection
  stopped failing, because after re-framing nothing can be out of bounds by construction. A change
  that disarms a proven invariant without saying so is exactly what the injections exist to catch.

It is a separate question from the one that was measured, so it is not in this build.

### Proof

- **214 pure tests, 0 failed** (up from 193): `RoomDimensionsTest` (13 — every real caption form in
  the corpus, both conventions, fractions, and the many shapes that are *not* a size: one dimension,
  a lift capacity, an index) and `ScanReshapeTest` (8 — the owner's flat end to end through the real
  mapper and the real synonym table).
- All twenty parser cases were **verified numerically in Python before any Kotlin was written**,
  20/20, per the standing rule.
- **Six fuzz suites clean at 20 000**, and **nine fault injections all bite**, including the two new
  ones (`printed-follows-model` 1 122 · `no-printed-sizes` 1 153) and — importantly — the pre-existing
  `repack` (4 213) and `no-clamp` (10 150), which confirms the anti-relocation and out-of-bounds
  guarantees still hold after the change.
- ⚠ **Expectations for real captions live in Kotlin, not in the mirror.** Pinning the owner's plan in
  `sim.mjs` failed immediately: the mirror carries a deliberately cut-down synonym table, so `W.C`,
  `LOBBY` and `PASSAGE` do not resolve there. Same lesson the recorded-reply pins already learned, and
  it was re-learned the same way.

### ⭐ And a golden that had to be added, because nothing rendered the change

**Not one existing golden moved.** The recorded scan fixtures are synthetic or route to Assisted, so
none of them prints a room dimension — the whole feature would have shipped with nothing drawing it.
`editor-scanned` renders the owner's flat through the real mapper into the real editor.

**Looked at it.** The kitchen and the living room are now vertical, as the sheet says; the toilet and
bath are small instead of full-width bars; relative sizes are right, with the living room plainly the
largest room. **Honest limits visible in the same picture:** the bedroom is trimmed where it meets the
grown living room, and the passage keeps its read shape because the living room now covers where the
reader had placed it. Positions are still the model's guess — this fixes shape, not placement, and the
user confirms every room.

---

## ⭐⭐ The grid belongs to the home, not to the sheet — v0.3.22 (2026-07-31)

Owner, scanning a second Green Court unit: *"I can see the problem of not using the whole grid is
back and I feel the proportion may be right but not dimensions — Toilet is not built correctly to
show the word Toilet."* Both halves correct, and both turned out to be one cause.

### The measurement, taken from his two screenshots alone

Working backwards through `snap` and `reshapeToPrinted` from the rectangles the app drew reproduces
the arithmetic exactly: **one cell was standing for 16.5 sq ft, so his 526 sq ft flat was drawn as
though the grid were a 41 ft × 41 ft plot.** Five of the six dimensioned rooms match that scale to
the cell; the sixth differs by exactly one trim, which is visible in the picture.

The cause is single. `ScanMapper` mapped the reader's coordinates — which are against the whole
PICTURE — straight onto the grid, and the scale came from the cells those rectangles already used.
A builder's sheet is about 40 % logo, title block and blank paper, so **the home only ever got the
share of the grid it happened to occupy on the page.** Small home ⇒ every room small ⇒ a 4'-11"
toilet rounds to one cell ⇒ "To…".

⚠ The proportions were already right. His instinct was exactly correct, and the table proves it:
printed width÷depth against drawn width÷depth agreed on every room.

### What changed

| | |
|---|---|
| **The drawing area is the HOME** | the rooms' bounding box, *clamped to the picture*, fitted uniformly so proportions survive, with the grid's shape taken from the home's proportions rather than the sheet's |
| **A room may round SMALL, never away** | both edges rounding together used to drop it |
| **The smallest room is placed first** | what a cut costs is proportional |
| **A room lost outright is rescued** | it jumps the queue and the placement is redone |

### Measured on the 30 real replies plus both of the owner's own sheets

`tools/scan-eval/exp-frame.py` — every table parsed out of `RoomLabels.kt` so the mirror cannot drift.

| | before | after |
|---|---|---|
| home fills the grid | 71 % (his sheet: 40 %) | **97 %** (his: 100 %) |
| rooms cover the grid | 49 % | 69 % |
| rooms too narrow to print a name | 37 | 30 |
| rooms cut back | 11 | 15 |
| **rooms silently lost** | **10** | **0** |

⭐ The last row was not the goal and is the most valuable thing here. Ten rooms across twelve plans
— almost all toilets, a scored input at weight 2.5 — were rounding away before they reached a
screen. Nobody had counted them.

### ⚠ Two objections that reverted this in v0.3.21, and how each is answered

- *"one stray rectangle inflates the frame and shrinks the whole home"* — measured across 20 real
  replies: removing the single most extreme room shrinks the frame by a **median 1.12×, worst
  1.29×**. Real, bounded, and not the catastrophe the note implied.
- *"it removed the fuzz suite's `no-clamp` bite"* — the frame is **clamped to the picture**, so an
  off-page box still falls outside it. The suite also gained `SANITISED-IN-PAGE`, which checks the
  property `sanitise` exists to guarantee directly rather than inferring it from a rounded
  rectangle. `--inject=no-clamp` fires it.

### ⚠ Built, measured, and NOT shipped

A four-rung "fill the grid" ladder (0.95/0.90/0.85/0.80, take the largest that costs no room).
Across the corpus and both sheets it produced **identical** orientation errors, rooms lost, fill and
coverage, and his toilet came out the same either way. Once the frame is the home the reader's own
total already fills it. Machinery that cannot be shown to change an answer does not ship; the
reasoning is recorded in `ScanMapper` so it is not re-derived.

### ⚠ Honest costs, stated not buried

- **Orientation goes 4 wrong → 5**, out of 57 dimensioned rooms. Because the biggest room is now the
  one squeezed, and the biggest room is usually the living room. Taken deliberately: a room of the
  wrong shape is visible, flagged and correctable; a room that vanishes changes the footprint the
  engine scores and cannot be re-added by someone who never saw it. That is this file's standing
  rule applied where the two harms compete.
- On the Cat-II sheet the **lobby now comes out square** rather than deeper than wide, and the
  bedroom and passage become right where they were wrong. Net 2 wrong → 1 on that plan.
- **One plan (`plan-014`) moves from Assisted to Placed**, because it was only landing in Assisted
  by losing rooms to rounding. Under the designed gate — 11 rooms, under `MAX_TRUSTED_ROOMS` — it
  belongs in Placed.
- `--inject=frame-picture`, `--inject=confidence-first` and `--inject=no-rescue` all leave the fuzz
  suite **green**, and that is said plainly rather than dressed up: losing a room is *reported*, so
  it is legal. They are corpus-measured, not fuzz-proven. `no-onecell` and `round-away` do fire.

---

## ⭐ Production-grade UI, same build — v0.3.22

Owner: *"I want you figure out the best, safe and most stable solution to keep UI/UX production
grade including this issue of names not showing up correctly."* Three defects, all previously
surfaced and parked, all closed here.

### 1. ⭐ A room too narrow for its name now turns the name on its side

`RoomTileLabel.kt`. Shrinking the text was refused — this app is read by older users looking for
directions — and blanket abbreviation was refused too, because "Corr", "Util" and "Base" are not
words anyone reads at a glance. What solves it is what architects have always done on a narrow room:
**turn the label**. Rungs, each measured rather than guessed: full name across → full name turned →
short word across → short word turned → nothing.

The choice is a pure function taking an injected measurer, so it is unit-tested at every font scale
without rendering. ⚠ The trap it exists for: at 200 % font a *line box* is taller than a one-cell
room is wide, so a turned label would hang out of its own tile — the rotated rung checks the swapped
axis too. Short forms are real words only (`WC`, `Bath`, `Bed`, `Entry`, `Court`); where no natural
one exists the full name is kept and the rung is simply skipped.

### 2. The readout no longer sits on the plan at rest

It appeared whenever a room was selected and covered the top row — the owner's screenshot, later
reproduced in our own `editor-selected` golden. It was also **redundant** there: the selected-room
panel below the grid already prints the same kind · size · direction line. It now appears only while
a finger is actually moving something, which is the one moment that panel cannot help, and it moves
to the bottom edge when the room being dragged is itself in the top row.

### 3. Contrast — the small grey text and the arrow glyphs

Measured, not eyeballed:

| | was | now |
|---|---|---|
| `textTertiary` on the card (compass letters, MOVE/SIZE/ROOM TYPE, "2 × 2 · North-West") | **4.39 : 1** | **4.64 : 1** (`#686C61`) |
| move arrows and size steppers on the 14 % primary tint | **3.25 : 1** | **6.15 : 1** (`textSecondary`) |

The token moves three values of lightness with hue and saturation untouched, so the tier still reads
as the quietest of the three (4.64 against textSecondary's 6.90). Declared in
`check-design-fidelity.mjs` with its reason — a contrast floor is not a matter of taste. The arrows
needed no token change at all: the tint already carries the "this is a control" signal, so the glyph
moved to `textSecondary`, the same call made for the Change affordance in v0.3.20.

---

## ⭐ The score is shown out of ten — v0.3.23 (2026-08-01)

Owner: *"the Vastu score is shown out of 100. Change it to out of 10 with one decimal — 47 becomes
4.7, 8 becomes 0.8, 100 becomes 10.0. Everywhere a person sees or hears it."* With a hard constraint
attached: **do not touch the scoring engine.**

### The constraint held, and it is the reason the change is small

The engine still returns a whole 0–100. `Sample01Test`, `Sample02Test`, `AuditFixesTest` and the
rotation-invariance case still assert the §15 worked example scores **exactly 31** — none of them was
opened. `scoreBandColor` still branches at 75 and 50, `verdictLine` still branches at 75 and 50, and
`ScoreBar`'s fill is still `score / 100f`. **Every home lands in exactly the band it landed in
before**; only the string above it changed. The whole change is one new pure function plus five call
sites.

### The five places a person meets the number

| where | was | now |
|---|---|---|
| the free score screen, big number | `31` `/ 100` | `3.1` `/ 10` |
| the saved-homes list — also the buyer's side-by-side compare | `68` `/100` | `6.8` `/10` |
| the live readout while dragging the North dial | `68/100` | `6.8/10` |
| the compass's spoken description | "Score 68 of 100." | "Score 6.8 out of 10." |
| the zone map's spoken description | "score 31 of 100." | "Score 3.1 out of 10." |
| the disclaimer under the score | "The **0–100 score** is…" | "The **score out of 10** is…" |

Searched rather than assumed: the paid report and the unlock/paywall screen quote **no number at
all**, so neither needed a change. `ScoreBar`, `scoreBandColor` and `verdictLine` take the score but
never print it.

### Three decisions taken without asking, each with its reason

1. ⭐ **Always one decimal.** `50` prints `5.0`, never `5`. A bare `5` beside a `4.7` reads as a
   different kind of number, and this is a column users compare two homes in.
2. **The big number now says its own scale out loud.** It carried no description, so a screen reader
   announced a bare "4.7". It is now "Score 4.7 out of 10" — the same sentence the compass and the
   zone map use, so the app says the score one way.
3. ⭐ **The decimal mark is asked of the phone, never typed in** — see below.

### ⭐ The decimal mark, and the trap inside it

All six languages VastuFirst ships in (English, Hindi, Tamil, Telugu, Marathi, Bengali) write a
**dot**. So a hard-coded `"."` would look correct forever, in every test, in every golden — and be
wrong the first time the app opened on a phone set to a comma language. `deviceDecimalMark()` asks
Android's own locale data for the resolved configuration locale (not `Locale.getDefault()`, which a
per-app language setting can make disagree), in **one place**, and hands it down through
`LocalDecimalMark`.

⚠ **Only the mark, deliberately not the whole number.** Handing "4.7" to Android's number formatter
would also **shape the digits**: verified against ICU before writing any Kotlin, `bn-IN` renders it
**৪.৭** and `mr-IN` **४.७**, because those are their languages' default numbering systems. That is
correct in a fully translated app and wrong in this one — the ₹699 price, the degrees on Mark North
and every room size are Western digits, and the entire interface is still English until the Phase 4
translations land. A lone Bengali numeral in an English screen reads as a bug. The numbering system
therefore becomes a deliberate choice made **alongside** the translations, and the note saying so
lives in the one file that would have to change.

### ⚠ A safety check this change nearly disarmed

The first version merged the big number and its `/ 10` into one accessibility node, so a screen
reader would say "Score 4.7 out of 10" as a single phrase. It was **caught in self-review before CI**
and removed:

> `L1Manifest` walks the **merged** semantics tree. Merging those two texts collapses them into one
> node — and the clipping and ellipsis checks on **the widest element on the screen** would have
> disappeared from the manifest, at exactly the moment its width changed.

Same class as v0.3.21's reverted re-framing, which silently removed the `no-clamp` injection's bite.
The description now sits on the number itself: the node survives, its measurement survives, and the
phrase is still spoken. **The saved-homes row was left alone entirely** for the opposite reason —
the whole row is clickable and therefore already one merged node, so a description added inside it
would be *appended* to the row's text rather than replacing it, and the row would read the score
twice.

### Proof

- `ScoreFormatTest` — 8 cases, JUnit (`:app`, message **first**). Includes a round-trip over **all
  101 scores**: every one reads back as itself, no two collide, every one carries exactly one
  decimal. Verified in Python first, per the standing rule, before any Kotlin was written.
- The formatter is **integer arithmetic** (`s / 10`, `s % 10`), so there is no floating-point
  division that could hand a screen "4.699999" and no rounding rule to get wrong.
- A case pins the platform's own answer for all six shipping languages (dot) **and** for German
  (comma) — the case that a hard-coded dot would fail and no other test would catch.
- Three screens re-rendered across the twelve-configuration matrix.

### Looked at before tagging (CLAUDE.md §2b)

The three changed screens, at **baseline**, **200 % font** and **320 dp** — the two configurations
where a wider number would break a row if it were going to.

- **Score** — `3.1 / 10` in the band colour, the bar still filled to 31 %, the verdict line
  unchanged. Nothing wrapped or clipped at any of the eleven configurations.
- **Saved homes** — `3.1 /10` and `6.8 /10`, red and amber, i.e. the same two bands as before.
- **Mark North** — the live readout reads `3.1/10`.

⭐ **The row got roomier, not tighter — and this was measured, not assumed.** The worry was that
"4.7" is wider than "47". It is, by one narrow glyph — but `/10` is a whole character shorter than
`/100`, and that caption is what sets the column's width. Comparing the old and new goldens side by
side at 200 % font, the home's subtitle now fits **"Building · Updated…"** where it previously cut
at **"Building · Update…"**. Neither ratchet baseline moved: **no new clipping and no new
accessibility finding on any screen.**

⚠ **Two things a screenshot cannot show, checked in the measurement manifest instead.** The
disclaimer sits below the fold on a scrolling screen, so it is absent from every golden — the
manifest confirms the new sentence lays out at its full 364 × 105 dp (351 dp tall at 200 % font)
with `ellipsized=false`. And the big number is recorded carrying **both** its text `3.1` **and** its
spoken `Score 3.1 out of 10` as one node with a real measured size — the direct evidence that the
description was added without merging the node away.

ℹ Noted while looking, **not a defect and not ours**: at 200 % font the big score grows about 9 %
while the caption beside it doubles. That is Android's non-linear font scaling — text that is
already large is deliberately scaled less — and it behaved identically when the number was `31`.

---

## ⭐ One list of room kinds, not two — v0.3.24 (2026-08-01)

Owner: *"Draw room on grid does not have same options as when you replace the room… should be
consistent, corridor should be there too."* Correct on both counts, and the second half is the
serious one.

### The measurement

| control | kinds offered |
|---|---|
| **Add a room** (editor palette) | **11** — Living, Kitchen, Master, Bedroom, Pooja, Toilet, Stairs, Study, Dining, Store, Balcony |
| **Change room type** (a placed room) | **19** — the eleven above **plus** Entrance, Corridor, Utility, Bathroom, Guest, Courtyard, Garage, Basement |

So the app answered "what kinds of room are there?" two different ways depending on which control
the user happened to be standing in front of.

### ⚠ Why this was a defect and not a tidiness item

**The plan reader can produce all nineteen kinds** off a real scanned floor plan — a lobby, a foyer,
a wash area, an attached bath and a servant's room all resolve to real types. A room read as one of
the eight the palette lacked could be **deleted and then never placed again by hand**. "Delete it
and draw a new one" was a one-way door for eight of the nineteen kinds.

The repo already knew this. It is written down in `ALL_ROOM_TYPES`' own comment and pinned by a test
called *"the palette alone cannot reach every kind"* — the gap was **documented and asserted rather
than closed**, because the change-type control (v0.3.20) made every kind *reachable* and that was
taken as sufficient. It was not: reachable-by-correcting is not the same as offered-when-adding, and
the owner found the difference in about a minute of use.

### What changed

**Not** the eight missing kinds copied across. There is now **one list** that both controls read.
Two lists is the mechanism that let this happen quietly, so the fix is to have one — the only way
to add a kind to either control is now to add it to both.

- The **eleven kinds already in the palette keep their order**, so nothing a returning user reaches
  for has moved. The other eight are appended, commonest first.
- Presentation is deliberately left alone: the palette stays a side-scrolling strip, the change-type
  control stays a wrapping list behind a button. Making the palette wrap would park four or five
  lines of chips permanently above the plan and push the move and size controls off a small screen —
  the exact reason the change-type control is collapsed in the first place.
- **No scoring change.** The engine is untouched, and every one of these kinds was already reachable
  through the change-type control.

### ⚠ Proof, and the honest limit on it

**No screenshot can show this, and that is worth stating plainly.** The palette is a side-scrolling
strip whose first eleven chips are untouched, so **every editor golden is byte-identical** and the
new chips sit beyond the visible edge. A green render gate proves nothing here.

The proof is a rendering test instead: it draws the real editor and asserts **all nineteen chips are
present on the screen**, written against the rendered palette rather than the list constant — so it
fails if the screen ever goes back to reading a shorter list of its own.

The two tests that pinned the old gap were **inverted, not deleted**: what used to assert "the
palette cannot reach every kind" now asserts that it can, and a new test pins that the familiar
eleven have not been reordered.

### Looked at before tagging (CLAUDE.md §2b) — and what "looking" had to mean here

⚠ **This is a change no screenshot can show, so the usual gate proves nothing.** The palette is a
side-scrolling strip whose first eleven chips are untouched, so **every editor golden came back
byte-identical and the render check passed without re-recording a single image** — exactly as
predicted, and exactly the kind of green that must not be reported as "verified".

The evidence used instead is the **measured geometry manifest**, which records every node the editor
actually rendered, including those scrolled past the visible edge. From this build's own run:

| | |
|---|---|
| chips present in the rendered editor | **all nineteen** — Living, Kitchen, Master, Bedroom, Pooja, Toilet, Stairs, Study, Dining, Store, Balcony, Entrance, Corridor, Utility, Bathroom, Guest, Courtyard, Garage, Basement |
| Corridor's laid-out size | **86.5 × 48 dp** — a real chip, on the 48 dp touch floor, not a zero-size ghost |
| Corridor's *visible* size | **0 × 0** — i.e. beyond the right edge of the strip, which is why no picture moved |

Both ratchets held: **no new clipping and no new accessibility finding on any screen.** That is the
expected result and the reason it is expected is worth writing down — the layout gate excludes
fully-scrolled-off items from its clipping count on purpose, so appending chips to a scroller cannot
inflate it. The chip that sits *partly* cut at the edge is unchanged, because the leading eleven
have not moved.

ℹ Left deliberately: the eight added kinds are reached by scrolling the strip further. Making the
palette wrap so all nineteen show at once is a genuine option, and it is the owner's call because it
costs vertical space above the plan — it is on the device checklist as a question rather than
decided here.

---

# ⭐⭐ v0.4.0 — the accuracy release (2026-08-01)

The four highest-ranked accuracy holes in `SCORE-ACCURACY-CAVEATS.md`, closed; the two robustness
items; the disputed rulings; and everything a paid Play Store app needs. Five gate failures on the
way, every one of them real — recorded below rather than tidied away.

## 1. ⭐⭐ An L-shaped home is finally scored as an L (caveat #1)

**The engine was never the problem.** `AnomalyDetector` has always derived a reference rectangle from
the footprint's modal edges and attributed the difference to cardinal zones, so a missing north-east
corner fires X-04 exactly as it should. **It had simply never been given one.** `buildEnginePlan`
handed it the bounding box of the placed rooms.

So an L-shaped or notched home — the commonest real Indian home shape — was scored as a filled
rectangle: silently, too generously, and with no "unusual shape" note either. Nothing in the engine
changed here. Only what it is fed.

### The model, and why it is cells

The drawing grid is whole cells, so **the home is a set of cells and its outline is the boundary of
that set.** Rectilinear by construction (real walls meet at right angles), exact in integers (no
floating-point wall that nearly closes), and it turns "is this part of my home?" into a question about
a patch rather than a trace drawn with a fingertip.

### ⭐ It is a QUESTION, not a drawing tool

This is the design decision worth recording. Tracing an outline with a fingertip is hard for the
person this app is for, and tapping cells out one at a time is worse. But the app can already **see**
every empty patch inside the home, and each patch has exactly one honest answer. So it asks — one
whole patch at a time, in full words, with the patch highlighted on the plan and two full-width
buttons. That also makes the whole feature reachable with a screen reader for free, which a grid of
40 dp tappable cells would not have been.

### ⚠ The safety property

With nothing cut out the outline is **byte-for-byte what it always was** — same four points, same
order, same winding. Proven for every bundled sample, not asserted. That is what let this ship without
opening a single engine test, and it is why no existing home moves.

| shape | what happens |
|---|---|
| a gap enclosed by rooms | refused — it would be a hole. Told to add a **Courtyard**, a real Vastu element |
| a cut that would split the home in two | refused |
| two blocks meeting only at a corner | refused |
| the cell the front door stands on | refused — the entrance would float off the wall |
| a cut a room later grows over | lapses quietly rather than punching through the room |

A saved home's shape is re-derived from the outline stored with it, so **nothing needed migrating**.

**Measured on a home with no problems of its own** (every room in a zone its own rule calls ideal or
acceptable, an unruled corridor at the centre): cutting the north-east 3×3 corner takes the defect
penalty from **0 to a real one**, fires **X-04 as MAJOR**, and leaves every room verdict and the
pre-penalty base **identical** — so the drop is attributable to the corner and nothing else.

⚠ The first fixture for that test was a home so bad it already sat at the penalty cap, scoring **0
both ways**. The cap hid the very thing under test. Recorded because it is a general trap: a
before/after test needs a fixture with room to move.

## 2. ⭐ The compass, and the double-check (caveat #3)

North is the one input nothing could verify, and the user set it from memory.

**A compass that is WRONG is worse than no compass.** Setting North by hand at least leaves the user
aware they were guessing; a confident "271°" that is ninety degrees out puts precise, authoritative,
wrong directions on every room in a ₹699 report. So the sign convention was derived **twice** — from
the North dial's own `atan2(dx, -dy)` and from the engine's rotation of the plan — and then pinned by
**actually scoring** a home with one room against the top edge and reading back where the engine put
it. Point the phone east, that room must come out East.

**No permission is requested, and that is deliberate.** Motion sensors need none. True north would
need the device's LOCATION to correct by under two degrees anywhere in India — far inside the error of
a hand holding a phone. So it uses magnetic north and says so on screen.

Bearings are smoothed as **vectors**, never as numbers: a plain average of 350° and 10° is 180°, due
south, and it only misbehaves when the user happens to be facing north.

### ⭐ The better half is the double-check

"Are you sure your North is right?" is a question nobody can answer. Where your own kitchen is, is.
So the card states what this North MEANS in the report's own words — *"your front door is on the west
side, and your kitchen is in the south-east"* — and the button that continues is the one that agrees
with it. No extra tap, and nobody walks past it without having read the claim.

## 3. The score says what it looked at, and can be told more (caveat #4)

The score has only ever come from rooms, the door and the shape, but it read as a complete verdict. It
was really a **ceiling**: a home with its water tank in the worst possible corner scored exactly the
same as one with it in the best, because nothing ever asked. Water storage, heavy trees and the road
outside all have real engine rules, and all three sat permanently in "couldn't check these yet".

Two halves: the score now **says** what it covers, and an optional step collects the four inputs.

⚠ **Optional, and reached FROM the score.** Forcing four more questions on everyone would cost every
user time to catch the minority who have something to report.

⚠ **Every answer is a DIRECTION, not a position.** Every rule that uses these inputs asks only which
of the nine zones the thing is in. Dragging a marker would demand far more precision than any rule can
use, and would be the hardest thing in the app for the person it is built for.

⚠ **The hard part, invisible on a square home.** The user names a TRUE-NORTH direction; the engine is
handed plan coordinates and rotates them itself. A home drawn at an angle has a bigger analysis
rectangle than its own footprint, so a fixture placed by naive fractions lands in the wrong zone —
silently, and only for the users whose homes are not square to the compass, which in India is most of
them. The placement mirrors the engine's own arithmetic and is checked by scoring real homes at eight
angles and reading back which defect fired.

Also closed: reopening a saved home could drop its extras entirely, because the plan was rebuilt from
an empty answer sheet the moment anything was touched.

## 4. A room on the line says so (caveat #2)

The drawing grid is coarser than the zones the engine judges against. The two grids genuinely do not
line up, so this cannot be rounded away — the honest answer is to say when a room is sitting on a
line, at the one moment it can still be moved. Silent in the ordinary case, or a warning on every room
is a warning on none.

## 5 & 6. The two ways a user could lose data

**A half-drawn home lived only in memory.** Android reclaims a backgrounded app whenever it wants the
RAM; on a cheap phone, taking a call is enough. The draft is now written to disk continuously and
brought back on the next launch — and **it says it did**. Restoring work silently would be worse than
losing it: the user would be editing a home they thought they had abandoned, with no way back.

A draft row is deleted the moment that home becomes a real saved row, which is what makes "a draft
exists" mean exactly "you never finished this one", and therefore what makes restoring it
unambiguously right rather than a guess.

**One bad row used to empty the whole list.** Two enum lookups and a JSON decode, all inside the
saved-homes flow. Any one throwing took down every home on the screen. Now a row that cannot be read
is quarantined and **counted**, and never deleted. A home vanishing without a word looks exactly like
a home the app deleted by itself.

**And the database now has an upgrade path**, which it never had. The next schema change of any kind
would have needed one, and the usual shortcut is to drop and recreate — which erases every saved home,
silently, discovered by the customer. Exercised for real: a database built at the OLD schema, with
homes in it, upgraded, every home still there.

## 7. Everything a paid Play Store app needs

⚠ **The plan said Razorpay. Google Play does not allow it here** — anything sold inside a Play Store
app that the customer then reads inside the app must be sold through Google's own billing. The
checkout is built on Play Billing; there is **nothing secret to paste**, because Play identifies the
app by its own signature.

⚠⚠ **The app must never show a screen that LOOKS like it takes payment when it does not.** That is why
"off" is a real implementation rather than a disabled button, and why both sentences come from ONE
pure function shared by the unlock screen and the score card. The negative cases are the ones pinned:
with payments off the button must not contain "pay" or a price; with the store unreachable it must NOT
quietly become free.

Also: release builds are **shrunk and obfuscated**, and CI builds that variant on every push — R8
breaks reflective code at runtime, so meeting the shrinker on the day of the store upload was not an
option. Real release signing from the environment. A privacy policy in the app, offline. And crash
reporting **without a crash-reporting service**: the app records its own crash and the next launch
offers to email it, with the user reading it first.

## 8. The disputed rulings

Nine decided, one deliberately left open, all of them one-line reversible — `docs/EXPERT-RULINGS.md`.

⭐ **Both score-moving rulings land on the value the app already runs**, and that is a finding rather
than a convenience. For M-07 (how the door's pada is decided), the two readings give the **identical**
answer on a square mandala, which is what the texts describe; they only diverge on a rectangle, where
the texts are silent. What settles it is a case the texts never had: an L-shaped home has walls that
turn back on themselves, so "how far along the wall" has no single answer there and the code already
falls back to bearing-from-centre. Making the fallback the rule means a rectangle and its L-shaped
neighbour are judged the same way.

⚠ **W-12 (where the pooja room belongs) is NOT ruled, and a test now stops it being ruled by
accident.** Applying either reading starts scoring every pooja room — changing the score of every home
already saved on a phone, and the worked example with it.

## The five gate failures, and what each was

⭐ Every one was real. None was ratcheted away.

| what failed | what it actually was |
|---|---|
| a test | the notch fixture put the cut **under a room**, where a cut lapses by design — it proved the lapse, not the trace |
| a test | the L-shape fixture was a home so bad it sat at the penalty cap, scoring 0 both ways |
| geometry gate | the compass reading measured **zero wide** at 200 % font — two unweighted children in a SpaceBetween row |
| geometry gate | "North-West" and "There isn't one" measured **0 × 0** — a nine-chip FlowRow inside a card that measures at `IntrinsicSize.Min` |
| geometry gate | the shape section pushed the selected-room panel's arrows and buttons **below the fold** |

The last one produced the better design as well: the shape question is now hidden while a room is
selected, because somebody holding a room is editing that room, not the outline.

## Looked at before tagging (CLAUDE.md §2b)

Rendered and read: the shape question and the shape cut (baseline), the extras step at **320 dp**, the
compass helper, the unlock screen with payments off, and the score with everything answered.

- The L is drawn as an actual L, with the cut corner struck through and the wall traced round it.
- The nine direction chips wrap cleanly into two columns at 320 dp with nothing clipped.
- The unlock screen says **"Unlock on this device — free"** with "No payment is taken in this version"
  under it.

**Both ratchets improved rather than held:** `editor` 17 → 14, `editor-margin` 20 → 16,
`editor-scanned` 51 → 43, `marknorth` 18 → 16, `score` 7 → 5, and the accessibility baseline for
`score` 6 → 3. Eleven new screens adopted. **255 pure tests, none failing.**

---

# ⭐⭐ v0.5.0 — the report release (2026-08-01)

The report is the thing the customer pays ₹699 for. It explained almost nothing. This release is
entirely about that, and it changes **no number anywhere**: no weight, no severity, no provenance, no
ideal/acceptable/prohibited set. The worked example still scores exactly 31 and the three tests plus
the rotation test that assert it were never opened.

## What the owner found, paying for his own report

Six findings, all verified against the data before a line was written. They are the section headings
below.

## 1. ⭐ Thirteen of fifteen problems offered the identical two remedies

**The mechanism, and why it was invisible in the code.** Every `(room type, prohibited zone)` pair
resolves to a defect definition. Six pairs had one of their own — toilet in the NE, kitchen in the
NE, staircase in the centre, master bedroom in the NE or SE, toilet in the SW. **The other fifteen
fell through to a single catch-all**, `X-GEN`, which carried two remedy ids. There were **six
remedies in the entire dataset**, four of which were attached to almost nothing.

**The fix is data, not code.** Fifteen new defect definitions, one per uncovered pair, using the
`appliesTo` mechanism that already existed. Remedies went from **6 to 28**. Every one of the 21 pairs
is now claimed by exactly one definition, and 29 of the 30 definitions have a distinct remedy set.

⚠ **Severity and provenance were held constant at `MODERATE` / `DERIV`** — the two fields the scorer
reads — precisely so this could not move the number. Some of the new pairs arguably deserve a harsher
severity; that is a scoring decision and therefore the owner's, not a side effect of a text release.

### ⭐ The guarantee is now mechanical, in the loader

Three new validations, so this class of regression fails the build rather than reaching a customer:

| the rule | what it stops |
|---|---|
| every ruled `(room, prohibited zone)` pair must be claimed by its own definition | a new room type quietly inheriting the catch-all's text |
| every defect must offer a remedy beyond `structural-correction` + `vastu-shanti` **or carry a `remedyNote` saying why it cannot** | the exact state this release fixes |
| every Tier C/D defect must carry a plain `notCheckedLabel` | the raw code reaching the reader (see §5) |

⭐ **The escape hatch is the honest one.** A defect with nothing specific to offer must say so in
words. It may not be padded with an invented remedy — which is the failure mode this whole product
exists to avoid, and the one a table-shaped data file quietly invites.

### Where no classical remedy exists, the report now says so

Eight definitions carry a `remedyNote`, rendered above the remedies. The wording is careful about a
real distinction: Vastu Shanti *is* a classical rite, but it is a **general pacification, not a cure
for this defect in particular** — so the note says that, rather than the flatly-wrong "no classical
remedy exists" while a `TEXT`-tagged remedy sits underneath it.

## 2. The reasons were one line, and the catch-all was circular

The catch-all read: *"This room sits in a zone its placement rule prohibits."* Fifty-two characters
that restate the rule's existence and tell a paying reader nothing.

Every reason is now three sentences and names, in order: **what that direction is** in the tradition
(Sanskrit name, presiding deity, element, what it governs), **why the tradition objects** to that
room being there, and **where the room belongs instead**. A test asserts every reason on the sample
home is over 150 characters and does not contain the old circular sentence.

⚠ **And the reason was not on the report screen at all.** The card went from the room's name straight
to the fix. The one-line version was on the *free* score screen. So the free screen carried more
explanation than the paid one.

⭐ **The free/paid split is now a real one:** the free screen shows the **opening sentence** of each
reason, the report shows the whole thing. That also stopped the free screen tripling in height when
the reasons got longer.

## 3. Rooms that were already right carried no reason

A room name, a direction and a tick. Each room rule now carries a `rationale` — why that kind of room
wants those directions — which reaches the reader alongside the direction's own meaning. The loader
rejects a rule without one.

## 4. ⭐⭐ Rooms rated "not ideal" appeared NOWHERE

**The worst of the six, and the only one that was arguably dishonest rather than merely thin.**
`SUBOPTIMAL` rooms are the leftover band — a zone in none of a rule's three sets. They fell between
the problems list and the already-right list, and the report filtered them out of both.

**Meanwhile the free score screen counted them.** `remainingIssueCount` added the unshown defects to
the suboptimal rooms and offered the total as "N more issues" to justify ₹699. **So the app sold
issues the report never showed.**

They now have their own section: why the direction is neither called right nor ruled out for that
room, where the tradition does put one, and a standing line that none of them is a defect but they do
count towards the score. A test pins that the number the free screen counts equals the number the
report shows.

## 5. The "couldn't check these yet" list printed raw codes

It printed `· X-09` at the customer. The engine now emits the list **twice** — the ids, unchanged,
for tests and logs (five existing assertions depend on them), and a reader's sentence plus what to do
about it. The additive shape is deliberate: rewriting `notAssessed` would have meant editing five
test files to fix a display bug.

## 6. Rich material was in the app and never shown

Every direction has a Sanskrit name, a presiding deity, an element and a domain. Every one of the 32
door positions has a name and a meaning. **None of it had ever reached a screen.**

- Direction facts are now carried on the `Analysis` and appear on every problem card, every
  already-right room and every not-ideal room.
- ⭐ **The front door has a section for the first time.** It is the highest-weighted single element in
  the reading — weight 3.0, more than any room — and the report had nothing about it at all. It now
  names the position, gives its meaning, says how the tradition reads it, and explains why the door
  counts for more than a room. A door spanning two positions says so.
- A position the sources leave unnamed (E7, S7) says exactly that rather than showing a blank.

## The paywall

Unchanged in substance — one tap, free, and honest about taking no payment — but the preview was
misleading. "N more issues" mixed unshown defects with a band the report did not contain. It is now
built from the analysis itself: real section names with this home's real counts, and a test asserts
it can never advertise a section this home does not have.

## The release signing key

The base64 decode step is built. Deliberately conditional on the secret existing, so a repo without
the key still releases on the committed test key exactly as today; it verifies the keystore opens
before anything depends on it, decodes outside the checkout, and shreds the file on the way out.

## ⚠ Two tests were INVERTED, not deleted

`kitchen in the Brahmasthan → generic defect` and `garage-in-SW → X-GEN` both asserted the very
behaviour this release removes. They now assert the opposite, with the reason written in the test.
`X-GEN` remains in the dataset — it is still the fallback for a cut or extension that is neither a
NE cut nor a SW extension.

## Layout notes

- The new room cards deliberately contain **no `FlowRow`**. `VastuCard` measures its row at
  `IntrinsicSize.Min`, and a wrapping row inside one has measured 0 × 0 on this codebase before (the
  nine direction chips on the extras step). The verdict pill sits on its own line, which also reads
  better at 200 % font.
- The report is now much longer, which is the point — it is a document, and it scrolls.

## The gate failures on the way, and what each actually was

⭐ Every one was real. None was ratcheted away without measuring it first.

### 1. ⭐⭐ The reasons were ELLIPSISED mid-sentence — the paid text, unreadable

At 200 % font and at 320 dp, the report's own reasons came back **truncated with "…"**. Not scrolled
past — cut off. On a ₹699 report, on exactly the phones this app is built for.

**The cause was the card, not the text.** `VastuCard` pinned its row to `IntrinsicSize.Min` for one
reason: so the accent stripe could be a real child `Box` that called `fillMaxHeight()`. Everything
inside the card therefore had to survive an intrinsic-height measurement first, and wrapping text
does not.

⚠ **That same constraint had already caused one shipped-class defect** — the nine direction chips on
the extras step measuring 0 × 0. It was worked around then (that screen stopped using `VastuCard`)
rather than fixed, so it came back wearing a different costume.

**The fix removes the constraint rather than dodging it.** The stripe is now *drawn* behind the card
with `drawBehind`, so the card is an ordinary `Column` that wraps its content. Visually identical —
same width, same colour, same full height — and it still starts on the correct side in RTL, which the
child `Box` got for free and a naive `drawRect` at x = 0 would not.

Measured result: **every ellipsis finding disappeared**, and three screens *improved* on the ratchet
rather than merely holding.

### 2. The unlock screen pushed its own button below the fold

Seven "what you get" lines measured taller than a 320 dp screen, so `unlock.action` — the button the
whole screen exists for — was cut by the bottom edge. Four lines now. The screen is back to its
baseline of 1.

### 3. The paywall card pushed the payment notice below the fold

Same class, worse consequence: the sentence saying whether money is taken is the one thing on that
card that must never need scrolling to. The preview is capped at three lines, and a test pins the cap
with the reason written next to it.

### 4. ⭐ A 201-character opening sentence

The free score screen shows a problem's **first sentence** and nothing else — that IS the free/paid
split, and it is what stops the free screen tripling in height now that the reasons are long. One
reason opened with a 201-character sentence, so "preview" became a wall of text that pushed
everything under it down.

Every reason now opens with a short headline saying what is wrong, with the detail following. ⭐ **The
loader refuses a dataset that breaks it**, because a screen's layout now depends on a property of the
text — and a dependency like that has to be enforced where the text lives, not hoped for.

### 5. The remedies reached the page with no provenance

Caught by re-reading rather than by a gate. `RemedyBlock` rendered `remedy.text` and dropped
`remedy.provenance`, so a rock-salt bowl invented in the 20th century and a rite prescribed in the
Mayamatam arrived looking exactly alike — in the one product whose entire differentiator is that it
does not make Vastu up. Every remedy now carries its own tag, and within a single problem they
genuinely differ.

### 6. The front door wore a "Defect" badge it had not earned

Found by looking at the rendered card, not by a gate. Only a South-West corner door raises an actual
defect, so on every other unfavourable door the reader was shown a red "✕ Defect" for something that
appeared in no problems list and had no fix attached. The door is judged on its own 32-position
scale, so its badge now speaks that scale: Favourable, Middling, Read both ways, Unfavourable.

### 7. ⚠ A golden is a viewport, not a document — and two whole sections were invisible

The report is long. On the bundled sample, **"Not ideal" and "Already right" sit far below the fold**,
so no screenshot in this harness has ever contained them — while "rooms rated not ideal appear
nowhere" is the exact defect this release exists to fix. A render gate that has never seen the thing
under repair is not a gate.

A second report golden now renders a home with **no problems at all**, which lifts both sections to
the top of the screen. Its shape is asserted rather than assumed: one defect in that fixture and both
sections drop out of frame again, unseen. Its door is favourable, so between the two goldens both
door readings are on record.

## The measured result

⭐ Every affected screen came out **better than before this release started**, not merely no worse:

| screen | before | after |
|---|---|---|
| `report` | 9 | **4** |
| `report-living` | 9 | **4** |
| `score` | 5 | **2** |
| `score-covered` | 13 | **8** |
| `unlock-paid` | 2 | **1** |
| `marknorth-compass` | 12 | **11** |

`unlock` and `unlock-unreachable` returned to 1 after the seven-line list pushed their own button off
the bottom of a 320 dp screen. The accessibility ratchet held on every screen.

## Looked at before tagging (CLAUDE.md §2b)

Downloaded and read, rather than asserted:

| screen | what it proved |
|---|---|
| `report` at 412 dp | the door section, the position number, the unnamed-position sentence, nothing clipped |
| `report` at **320 dp** | the same, wrapping cleanly on the narrowest phone we support |
| `report-clean` at 412 dp and landscape | the **favourable** door badge in green, the position named (*Mukhya — Wealth*), the "Not ideal" heading and its honest intro |
| `report-clean` at **200 % font** | every line wraps, nothing truncated — the state that was ellipsising before |
| `report-notideal` | ⭐ the not-ideal card and the already-right cards with their bodies visible, which no golden had ever contained |
| `score-covered` at 412 dp | the free preview is now one crisp headline per problem, with the source shown |
| `score` at 320 dp | the score, the zone map and the honest coverage line, uncramped |
| `unlock` at **320 dp** | ⭐ price, four things you get, the free button AND the payment notice — all on screen without scrolling, which is what the seven-line list had broken |

⚠ **One thing deliberately not chased:** the "Traditional 8-zone" toggle clips its first character at 200 %
font. It is a pre-existing behaviour of that component, already inside its ratchet, and untouched by
this release — worth fixing, but not by a release whose subject is the report's words.

⚠ **And the honest limit on all of this:** a golden is a viewport, not a document. The report is long,
so most of it is below the fold in any single image. `report-notideal` exists precisely because the
two sections this release is *about* were otherwise measured but never seen. Everything further down
— the disputes and the "couldn't check" list — is measured by the geometry gate and covered by unit
tests over the same text, but has not been photographed. Saying so is better than implying the whole
document has been looked at.

---

# ⭐⭐ v0.6.0 — the prayer-room ruling (2026-08-01)

The owner ruled on the one question deliberately left open: **the prayer room goes in the North-East,
modern practice.** Everything below follows from that, plus the thing the ruling obliged us to build —
telling anyone with a saved home that their score was recalculated, and why.

## 1. The ruling as shipped, and why this shape

| | |
|---|---|
| **Ideal** | North-East (Ishanya) |
| **Acceptable** | North, East |
| **Prohibited** | **nothing** |
| **Provenance** | `MOD` — 20th-century modern practice, tagged as such, not as a classical verse |

The owner proposed exactly this shape and asked whether we agreed. **We did, and the strongest reason
is one the brief did not list:** prohibiting anything would have to include the Brahmasthan, because
that is where every other room rule prohibits — and the classical reading *puts the shrine there*. The
app would print "defect" on the exact position it shows two lines later as the other school's advice.
The data has to say the same thing the prose says. The owner's own reasons (honest severity; no new
defect definitions and therefore no invented remedies) hold as well.

⚠ **The prayer-room rule was NOT scored at all before this**, because it carried a dispute link and
the room evaluator returns "not scored" for any rule that has one. Removing that link is what starts
it being scored.

## 2. ⭐ The score, measured rather than calculated by hand

**The worked example still scores exactly 31**, and every one of the seven assertions of that number
across five test files was left untouched. That was not the expectation going in — the brief
authorised the number to move.

What actually happens: the worked example's prayer room is in the **North-West**, so it goes from *not
scored* to *not ideal* — 45 points at weight 2.0, joining **both** sides of the weighted average. Base
**47.27 → 47.03**; the penalty is untouched at 16; `round(47.03 − 16)` is 31.

⚠ **That is a coincidence of arithmetic, not a design goal, and it does not generalise.** A saved home
with a prayer room in the North-East goes up; one no better placed than the rest of its layout goes
down. Every score in the app is now computed under a genuinely different rule. The bundled demo home
is the same layout as the worked example, so the client's sample and its screenshots are unchanged.

The rotation test still returns the same number at every angle, which is what distinguishes a scoring
change from a geometry accident.

## 3. ⭐ Both readings still reach the reader — and the card now says which one we use

Removing the room rule's dispute link also removes the path that surfaced the dispute *through the
rule*. It now arrives through the dispute's own room-type link instead, and **two tests pin that**,
because scoring one school's reading while quietly dropping "the tradition is split" would break the
product's whole promise.

⭐ **And a new line on the card: what your score uses.** Showing both sides while staying silent about
where the number stands is a half-truth. Every dispute we have *not* ruled on omits the line entirely,
so it never claims a position we do not hold.

## 4. ⚠ One test INVERTED, not deleted

`the pooja ruling is deliberately still open` existed specifically to stop the ruling being applied by
accident. It now asserts the opposite — that the ruling really is in the shipped data and has not
silently reverted — with the original reasoning kept in the file. Two further tests were added: that
both readings still surface, and that a North-West prayer room lands as "not ideal" with no defect.

## 5. ⭐⭐ Telling existing users, which is the honest half of this release

A saved home stores the rule version it was scored under — the database has carried that column since
the first build for exactly this moment. When the version moves, the app re-runs every affected home
and shows a card above the list: a heading that never overstates what happened, the reason in ordinary
words, one line per home with both numbers, and one button.

Four decisions worth recording:

- **Nothing is written back until the card is read.** The list keeps showing the number the user last
  saw. Changing a saved score and *then* mentioning it is not the same promise.
- ⭐ **A home whose number did not move is still listed, saying "unchanged".** The bundled sample is
  exactly this case. Staying quiet because the arithmetic landed where it started would be luck
  presented as integrity — their *reading* changed, and their report now contains a room it never
  mentioned before.
- **The reason lives in the rule data, not in the app**, so a rule edit and its explanation cannot
  ship apart. ⭐ **The loader now refuses a dataset that cannot say what changed in it** — a rule edit
  is precisely the moment nobody remembers to write the sentence.
- **A home this build cannot re-run is dropped from the card entirely** — old score, old version, no
  claim made. Inventing a number for a home we could not read would be worse than a silent change.
- ⚠ **Known and accepted:** until the card is acknowledged, the list shows the old number while
  opening that home shows the new one, because the report is always computed live and the list is a
  cached summary. The card sits above the list saying exactly that, so the report agreeing with the
  card rather than with the stale row is the *consistent* outcome. Writing the score first and
  explaining afterwards is the trade we refused.
- **`updatedAt` is deliberately not touched.** We moved the rules; the user did not edit their home,
  and reordering their list as though they had is a second small dishonesty on top of the first.

## 6. Two new goldens, because a golden is a viewport

Both of the things this release puts in front of a reader sit **far below the fold** on the bundled
sample — the not-ideal prayer room, and the disputes card. Neither would ever have been photographed.

- `report-prayer` — three rooms chosen so the whole report fits one screen: nothing to rank, one
  not-ideal card, two already-right cards, then the disputes. The footprint is a full 8 × 8 square on
  purpose; a narrower one trips the elongation rule, a defect appears, and everything below it drops
  out of frame again. Its shape is asserted, not assumed.
- `home-scorechange` — the "we changed a rule" card, carrying the **real** sentence from the shipped
  rule data rather than a placeholder. One home moved and one did not, so both lines are on record in
  one picture. It has the most text of anything on that screen, which is exactly the block that has
  pushed its own button off a 320 dp screen before.

## 7. ⚠ The defect the gate physically cannot see

Looking at `report-prayer` at 200 % font showed the reading toggle rendering **"Iraditional 9-zone"**.
The word "Traditional" was wider than its half of the row, so it was drawn centred and overflowing,
losing characters off **both** ends.

⭐ **Every gate was green, and correctly so.** The geometry checker compares each node's clipped and
unclipped bounds; for that label both were the segment box's own 178 dp. The box is not clipped — the
*ink* is. `ellipsized` was false too, because the text was never constrained into ellipsising, merely
drawn wider than its container. **This class of defect is invisible to the harness and is caught only
by a person opening the picture** — which is exactly why "never hand over a screen I have not looked
at" is a hard rule. It had already survived a release, recorded as a known blemish, because CI was
green every time.

The fix is a constraint, not a workaround: a fixed-width control holding text must keep every **word**
short enough to fit its narrowest configuration alone, because a long word cannot wrap — it bleeds.
The segment labels are now `8 zones` / `16 zones · soon`, the component documents the rule where the
next person will read it, and `docs/UI-POLISH.md` §6.7b records the limitation permanently.

Two more of our own words went at the same time: the settings row said "School profile · Traditional
8-zone" — jargon on a screen a customer opens — and now reads "Vastu reading · 8 zones".

## 8. Looked at before tagging (CLAUDE.md §2b)

Downloaded from the build and read, rather than asserted:

| screen | what it proved |
|---|---|
| `report-prayer` at 412 dp | the prayer room's own card — the "Not ideal" badge, the **Modern practice** provenance tag, the direction's meaning and the full reason. The first picture of a scored prayer room. |
| `report-prayer` at **200 % font** | ⚠ the toggle rendering **"Iraditional 9-zone"** — the defect no gate could see — and, after the fix, `8 zones` whole |
| `report-disputes` at 412 dp | ⭐ both readings AND "What your score uses" — the section that had never once been photographed, and the one carrying the product's promise |
| `report-disputes`, again after the rewrite | compass abbreviations gone: "Against the West or South-West wall", not "Idols on W/SW wall" |
| `home-scorechange` at 412 dp | the whole card: heading, explanation, one line per home with both numbers, and the button |
| `home-scorechange` at **200 % font** | ⚠ first: the button entirely off screen. Then: 33 dp of 50.5. Finally: all 50.5, with the explanation intact above it |
| `score` at 412 dp | the demo home still reads **3.1**, and the zone map now colours the prayer room instead of leaving it blank |
| the bundled report's own structure | the demo home now runs problems → **Not ideal (Pooja — North-West, Kitchen — West)** → already right → both prayer-room disputes → couldn't check. It reads sensibly end to end. |

⚠ **The honest limit, again:** a golden is a viewport. `report-prayer` reaches the not-ideal card and no
further, which is why the disputes section has a render of its own — and the comment on `report-prayer`
now says so, so nobody assumes otherwise a release from now.

---

# ⭐⭐ v0.6.1 — a scanned home arrives as a home (2026-08-02)

The owner scanned his own flat — Tower D1/D2, 2B+2T, 1262 sq ft — and got **ten small boxes with
empty squares between every one of them**, on a sheet where every room shares a wall with its
neighbour. The app then asked him whether his home was "cut off" in the north-west, pointing at the
slack beside his master bedroom. Neither answer to that question was right, because the question was
wrong.

## 1. The cause: every edge was rounded on its own

The mapper already knew adjacency mattered — its own comment says so, and it deliberately rounds each
EDGE rather than rounding position-plus-size. But independent rounding preserves adjacency **only
when the reader reports a shared wall at the identical number**, and it never does. The reader draws
inside the wall thickness and jitters every edge, so one room's right edge comes back at 3.48 cells
and its neighbour's left edge at 3.52 — four hundredths of a cell apart, rounding to 3 and 4, opening
a one-cell moat between two rooms that touch in the real home. Every room does it to every neighbour,
which is why a plan falls apart into islands rather than merely loosening.

⚠ **No existing fixture could reproduce it.** Every scan fixture in the suite has rooms at exactly
0.0 / 0.3 / 0.4 / 0.7 — perfectly flush — so the defect was invisible to the whole test suite by
construction. The owner's plan is now in the corpus, read the way a real reader reads it.

## 2. The fix: the plan's own wall lines

Edges within **half a cell** of each other are agreed to be the same wall before anything is rounded.
Half a cell is not tuned: closer than that, rounding is arbitrary — a hundredth either way flips the
answer — so the difference carries no information about being different walls. Beyond it, rounding is
decided and we leave it alone. Single-linkage, with a hard cap on how wide one group may grow, or a
run of near-misses chains into one enormous "wall" and crushes the rooms between them.

**Measured on the owner's sheet:**

| | before | after |
|---|---|---|
| empty squares in the bounding box | 41 of 80 | **26 of 80** |
| biggest single hole | 28 squares | **11** |
| separate holes | 3 | 4 (smaller) |
| real adjacencies restored | — | **all of them** |

Master bedroom to bedroom, master toilet under the master bedroom, toilet to living, kitchen under
living, service balcony beside kitchen — every wall that touches on the sheet touches on the grid.
What is left empty is the genuine shape of that flat, which really does have white space at the
top-right and below the two toilets.

## 3. Re-shaping to the printed size was re-opening the moats

It anchors a room's top-left corner and replaces the extent, so the room shrinks away from the
neighbour on its right and below. A re-shaped edge now **clicks onto a wall line** when it lands
within one cell of it.

⚠ **But never at the cost of the room's shape**, and this took two goes:

- The fuzz suite caught the magnet turning a 1 350 × 2 250 toilet into a wider-than-deep room in
  **363 of 20 000** random replies — the exact error the printed sizes exist to remove.
- The first guard was a yes/no ("does this still agree with the sheet?"), and **square counts as
  agreeing** — so CI then caught a kitchen printed 6'-11" × 9'-7" being stretched from
  deeper-than-wide to square. Not wrong, but no longer right.
- It is now a **score** — runs the right way / square / runs the wrong way — and the magnet may never
  make a room agree with its own plan *less* than it already did. Doing nothing always scores the
  same as itself, so there is always an answer.

## 4. ⭐ The shape question is only asked when it means something

A missing corner **contains a corner** of the bounding box, and is at least a twentieth of the home
(four squares on the grid a scan uses). Anything else is floor with no room drawn on it — a passage,
a hall, wall thickness the grid cannot resolve — which belongs to the home whether or not anyone
draws a room there. The app now says so instead of asking.

On the owner's plan that takes the questions from four to three, and the three that remain are the
real notches in that flat.

## 5. ⚠ Honest limits, stated

- **Two of the fault injections leave the fuzz suite green** (`no-walls`, `no-magnet`), and that is
  recorded rather than dressed up: random replies rarely contain adjacent rooms, so "are there gaps?"
  is not a property that suite can judge. The owner's own plan is pinned in Kotlin instead, which is
  where the defect actually was.
- **A quarter to a third of a scanned home's bounding box is still empty**, and on this plan most of
  that is correct. It costs the score nothing — the footprint is the bounding box minus what the user
  cuts, so unlabelled floor is still part of the home — but it does mean a scan still looks looser
  than a hand-drawn plan.
- **A wall thinner than one cell disappears into a whole empty cell.** That is the drawing grid's
  resolution, already recorded in `docs/SCORE-ACCURACY-CAVEATS.md` #2, and it is why the confirmation
  step exists.

---

# ⭐⭐ v0.6.2 — the plan is read from what it PRINTS (2026-08-02)

The owner scanned a second flat — a 3-bedroom apartment, fifteen named spaces — and got **thirteen
identical squares parked in a row across the top of an empty grid**, under the heading "Place your
rooms". The app then asked whether the seven empty squares beside them were "in the south" and not
part of his home. They were at the top of the grid, directly under the word NORTH.

## 1. ⭐ The first thing done was to get the REAL reply, and it was already on disk

His plan is `plan-010` of the 30-plan corpus — same fifteen captions, same sheet. So the model's
actual answer for it had been recorded three days earlier and thrown away unexamined. It was also
**re-read live, twice, on 2 August**: byte-identical to the recording both times.

⚠ **This matters because of what the previous release was validated against.** v0.6.1's fixture is
the owner's Green Court flat as *re-typed by hand* with plausible noise added. That is validation
against a guess about the reader, not against the reader, and he said so. `owner-flat.json` is now
the first recorded reply for one of his own plans, pasted in unedited.

## 2. What the reader actually got wrong: it never measured the plan

| | |
|---|---|
| room names | **15 of 15 right**, in the order a person reads the sheet |
| distinct left edges for 15 rooms | **4** |
| coordinates on a 0.05 lattice | **100 %** |
| distinct room sizes | **3** |
| rooms placed past the bottom of the page | **3** |
| `planConfidence` | 0.95, as it always is |

That is a **template**, not a reading — the same signature §3j D2 found on a numbered-legend plan,
but wearing three stock sizes instead of one, so the uniform-box detector (which fires below 0.15
area variation) sees 0.62 and waves it through. **11 of the 23 real 2D plans in the corpus come back
with more than 90 % of every coordinate on that lattice.**

⭐ **And it cannot be prompted away.** A variant that explicitly forbids round coordinates and tells
the model to read the walls was measured: identical lattice, and *more* rooms off the page (6 against
3). Dropped. This is the fourth independent confirmation of the same thing — the model reads text and
does not measure drawings.

## 3. What it does read is text, and his sheet prints a size on every room

His plan prints `3.72m X 4.50m ( 12'-2" x 14'-9" )` under all fifteen captions. **We were capturing
none of them** — the prompt asked for the label and the reader gave the name only.

Asking for the size in its own field, and changing nothing else:

| plan | rooms | with a printed size |
|---|---|---|
| **the owner's flat** | 15 | **15** |
| plan-002 (24-space floor plate) | 24 | 24 |
| plan-014 | 14 | 14 |
| plan-008 · plan-020 · plan-022 | 14 · 14 · 13 | all |
| plan-009 · plan-019 | 13 · 12 | 11 · 11 |
| **plan-006 — a sheet that prints no sizes** | 10 | **0** ✓ |

**116 of 129 rooms across nine real plans**, thirteen of the owner's fifteen matching his sheet
exactly — and on the plan that prints nothing it invents nothing. Cost went 2 239 → 2 917 tokens a
scan, about 20 paise instead of 15.

⚠ The measured accuracy figures in §3e–§3h were taken with the previous wording and no longer strictly
apply. The prompt change was re-measured across the corpus rather than assumed; the triage half is
untouched and `plan-031` still classifies as a 3D render.

## 4. Four fixes, each measured before it was written

- **A reply that overruns the page is shrunk onto it, not cut off.** The clamp was deleting one of
  his three balconies outright and flattening the utility to a third of its depth before anything
  else ran. The shrink is one uniform scale and offset, which **cancels exactly** against a
  home-framed grid — so it is a provable no-op for every reply that already fits, and pure rescue for
  the ones that do not.
- **The grid widens until the home's biggest room can be drawn the way its plan prints it.** His
  living/dining is printed 7.25 m × 4.30 m; the reader's four x positions made the flat look so
  narrow that the grid came out **four columns wide**, where a room that size cannot be wider than
  deep. It came out inverted and the overlap trimmer then ate into it. Four of eleven rooms were
  drawn against their printed shape → **two**, and **no other plan in the corpus changes**.
- **The room count no longer decides a plan whose rooms state their sizes.** Fifteen named spaces —
  three balconies, a utility, a vestibule, a passage, a dressing area — is an ordinary Indian
  apartment, and the cut of twelve (whose own comment called it *"a judgement, not a measurement"*)
  binned the entire home. Where the sizes are printed, the rectangles supply only the arrangement and
  the text supplies every measurement. Two thirds is the threshold and nothing sits near it: real
  plans come back at 0 % or 85–100 %.
- **What separates a floor plate from a home is now said outright: a lift.** Every sheet in the
  corpus that names one is a shared floor plate; no single-home plan names one, including three
  genuine 21-room villas. A duct alone is deliberately not enough — flats print those too.

## 5. The screen he was handed now tells the truth

A scan that cannot place its rooms parks them as equal single squares. That row was titled "Place
your rooms — touch a room and slide to move it", which reads as a finished plan gone wrong; and
because the home's shape is measured against the box around whatever is placed, the app offered to
cut away the leftovers of the parking row itself and called them "the south".

The editor now knows the difference. While the rooms are parked it says **"These rooms aren't placed
yet"**, explains that we read them but could not tell where they go, and **asks no shape questions at
all** — it says instead that it will ask once they are where they belong. It stops being parked the
moment the user moves anything.

## 6. ⭐ Two fault injections finally bite

`--inject=no-widen` and `--inject=no-shrink` both left the fuzz suite green, because random replies
rarely reproduce either case. His real plan, pinned in `sim.mjs` and in `OwnerFlatScanTest`, turns
both red — and closes `no-walls` too, which v0.6.1 had to record as unproven.

⚠ **`no-magnet` is still unproven and stays recorded rather than dressed up.**

## 7. ⚠ Honest limits, unchanged or new

- **The rectangles are still a template on half of real plans.** Nothing here makes the reader
  measure; it makes our code stop pretending it did. Arrangement from the rectangles, every dimension
  from the text.
- **Two of his rooms are still drawn against their printed shape**, down from four. Both are visible,
  flagged and correctable, which is what the mandatory confirmation step is for.
- **His attached master-bedroom toilet is dropped**, because the reader drew it wholly inside the
  rectangle it gave the living room and sub-areas are dropped by geometry (owner decision D1). A
  toilet is a scored room. It is shown to the user in the "we also saw…" list rather than vanishing,
  and it is pinned as a stated trade.
- **A plan that prints no sizes is exactly as good as it was before** — and that is most of what the
  room-count gate still protects.

## 8. ⭐ Looked at before tagging (CLAUDE.md §2b) — and both pictures changed the build

Downloaded from the build and opened, not asserted:

| screen | what it proved |
|---|---|
| `editor-scanned-owner` at 412 dp | ⭐ **his flat arrives as a home.** Two bedrooms and a balcony along the north, toilet and passage west, entrance centre, living room in the middle, the east balcony beside it, kitchen and utility east, master bedroom south-west, third balcony south. **All fourteen rooms in the right part of the flat.** |
| the same at **200 % font** | still legible: names whole, the tall east balcony's label turned on its side, the plan still readable |
| `editor-scanned-unplaced` at 412 dp | ⚠ **the finding that changed the release.** The heading and the missing shape question were right — and every parked tile was a **blank coloured square**. A one-cell tile cannot print a room's name, so sixteen rooms sat under "drag each one to where it really is" with no way to tell which was the kitchen. |
| the same screen, re-rendered two cells wide | every name now reads — and the gate's count **did not move at all**, which is what exposed the second defect underneath. |
| the L1 geometry gate | ⚠⚠ its **203 findings on that screen are the TOUCH FLOOR, not the labels**: one cell of a ten-wide plan is ~36 dp against a 48 dp minimum, so every tile was too small to grab reliably — the person this app is for failing to hold the thing they are told to drag. Tiles are now two cells each way. **And the gate had adopted 203 as the screen's baseline without complaint**, because the ratchet only fails a count that RISES: a defect present from a screen's first render is blessed as normal. |
| the same gate, on `scan-retype` | ⚠ failed the build for 3 newly out-of-view elements — and **the screen had got better, not worse**. Comparing the two pictures side by side: the shorter floor-plate paragraph pulled *more* room rows into view, and a partly-visible row counts as clipped where a fully scrolled-off one does not. Baseline raised to 13 deliberately, with the pictures as the evidence. |

⚠ **Two honest notes on the owner's own screen.** His living room comes out **square** rather than
wider than deep — better than the inversion it replaces, and it is the residual §7 records. And the
app asks him **one** shape question, about the top-right corner; that is a fair question with a real
answer, where the version he complained about asked an impossible one about a parking row.

## 9. Released as 0.6.2

Tagged on the commit below, not on the goldens commit CI pushes — that one carries `[skip ci]`, so a
tag on it would name a build nothing verified.

**What a user gets that they did not have yesterday:** a scanned plan whose rooms are shaped by the
sizes the plan itself prints, no room silently deleted for being drawn off the page, a fifteen-room
flat treated as the ordinary apartment it is, and — when the reading genuinely fails — a screen that
says so instead of a row of squares under the heading "Place your rooms".

---

# ⭐⭐ v0.6.3 — every recorded plan through the mapper, measured, and the toilets come back (2026-08-02)

The whole scan pipeline was audited against **every reply the real API has ever returned for a real
plan** — 41 recordings: the 30-plan corpus, the nine re-reads with printed sizes, and the owner's own
flat. A new tool (`tools/scan-eval/audit-mapper.mjs`) runs them all through the mapper in seconds and
reports, per plan, what a user would be handed. Everything below was found by that audit, fixed, and
then re-measured on the same corpus. Nothing here changes the engine or any score rule — it changes
what a scan hands the editor.

## 1. ⭐⭐ The "sub-area" rule was deleting toilets — 24 real rooms across the corpus, zero sub-areas

A rectangle drawn wholly inside another room's rectangle was treated as a dressing-area and dropped.
Measured across all 41 recordings, that rule fired **24 times — and every single one was a real,
scored room**: nine toilets, a pooja, balconies, a study, a servant room. The owner's own
master-bedroom toilet was one of them (v0.6.2 shipped it as a stated trade in the "we also saw"
list). Not one genuine dressing area ever reached the rule, because dressing areas, wardrobes and
ducts are recognised **by name** and dropped before any geometry is looked at. The reader lays out
template rectangles, so "inside another room" describes the reader's sloppiness, not the home.

The rule is gone. A named room now always survives to the grid: it keeps the cells it was read at,
and the room it collided with is trimmed around it and flagged for checking. **On his flat: all
fifteen spaces now arrive, including the master toilet, and nothing is dropped at all.**

## 2. ⭐ The overlap trimmer is now exact, and it respects the printed sizes

When two rooms fight over cells, the loser used to be cut back one whole edge at a time, each cut
locally cheapest — and the sum was often terrible: one plan's living/dining went from a 20-cell room
to a 6-cell sliver drawn the wrong way round. The trimmer now searches **every** possible surviving
rectangle and keeps the best: most cells first, then the one that still runs the way the plan prints
the room, then the least moved. Rooms drawn against their own sheet's orientation across the corpus:
**11 → 10 of 148 judged**, and — with the label fixes below — rooms lost outright on the current
prompt: **zero**.

## 3. ⭐ Eleven real caption styles that read as "we didn't recognise the name" now resolve

Every one read verbatim off a recorded reply, none invented: `BED RM.-01` (a bedroom), a bare
`KIDS`, `SERV. RM`, `Masterbed 360X370`, `TOIL` (the sheet itself truncates it), `Pojo 150X100` (a
hand-lettered pooja), `DRY BALC.`, `C Bal`, `W area` / `W/area` (wash area). And three things that
are genuinely not rooms — a lawn, a pathway, a crockery unit — now say "not a room we score" instead
of the dishonest "we didn't recognise the name". A watchman's cabin stays unrecognised **on
purpose**: it stands at the gate, outside the home, and guessing it onto the plan would move the
score.

Two plans transform: the hand-drawn sheet goes from 8 of 12 rooms placed to 11 of 12 (its master
bedroom and pooja were the ones missing), and another recovers **both bedrooms** it had been losing
to the caption style `BED RM.-01` — its layout had been two-thirds empty because of it.

## 4. ⭐ The fuzz mirror had two silent blind spots — found because the audit disagreed with Kotlin

- **No feet-and-inches size ever parsed in the mirror.** A broken string escape meant the pattern
  could not match, so every suite stayed green while the mirror and Kotlin disagreed on a real
  plan's entire outcome (Assisted there, Placed here). The plan that exposed it — a Gurgaon builder
  plan printing `11'-0" x 15'-0"` on all fourteen spaces — is now a bundled fixture pinned in BOTH
  Kotlin and the mirror, so the two can never drift apart silently again.
- **The orientation invariant never judged the current prompt's replies.** It read sizes only from
  the caption text, and the current prompt puts them in their own field — so the check that guards
  "rooms run the way the sheet says" had been judging nothing since the size field shipped.

## 5. ⭐ The wall magnet is finally proven — and a relaxation pass was measured and NOT shipped

- `no-magnet` had been recorded as unproven in v0.6.1 and v0.6.2. The feet-and-inches plan settles
  it: without the magnet its kitchen falls one cell short of the dining wall (a 9-cell kitchen and
  37 empty cells; 12 and 34 with it). The injection now goes red on a pinned real plan.
- A relaxation pass (each room re-trimmed against everyone's final rectangles, to reclaim cells a
  mid-queue trim freed) was built and measured across all 41 recordings: **it changed nothing on any
  plan** — the cells a big room loses are held by neighbours whose placement is legitimate.
  Machinery that cannot be shown to change an answer does not ship; it was removed, and the note in
  the code says why.

## 6. ⚠ Honest limits, restated

- **His living/dining is still square** (printed wider than deep). It is squeezed between the reader
  template's positions for its neighbours — including the master toilet this release brings back,
  which occupies one of the cells the living room would need. Fixing it would mean *moving* a room
  somewhere the reader did not put it, and the standing rule — never relocate a room the user has
  not seen — is worth more than the shape of one room. Visible, flagged, correctable in one drag.
- **Ten rooms across the corpus still land against their printed orientation** after overlaps
  resolve — all big rooms whose cells were genuinely taken. The pre-trim shapes are always right;
  the confirmation step exists for exactly this.
- **Two rooms are lost outright on the OLD prompt's recordings** (two rectangles read at identical
  cells, nothing to trim); on the current prompt's recordings, none.
- **A plan that prints no sizes** still rides entirely on the reader's template and the room-count
  gate. Unchanged, and still the weak case.

## 7. Looked at before tagging (CLAUDE.md §2b)

The three fixture-driven screens re-render with this release: the owner's flat (now fifteen rooms,
with the master toilet visible west of the living room), the Gurgaon seven-room flat, and the
floor-plate room list (which now offers all four of its toilets). Goldens re-recorded and read
before tagging; ratchet movements compared picture-to-picture, not assumed.
