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
