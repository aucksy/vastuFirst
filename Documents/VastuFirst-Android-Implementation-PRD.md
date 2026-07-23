# VastuFirst — Android Implementation PRD
**For Claude Code · v1.0 · 23 July 2026 · Android build, iOS + website later**

> **What this document is.** The build plan for the VastuFirst Android app: project scaffold, the order things get built in, and the definition of "done" for each phase. It is the connective tissue between three inputs you already have. It does **not** repeat them — it tells you how to assemble them into a shipping app.

---

## 0. Read these three inputs first, in this order

You are not starting from a blank page. Three companion artifacts already exist and each owns a different half of the answer. Read all three before writing code.

| # | Input | Location | What it is the source of truth for |
|---|---|---|---|
| 1 | **Product PRD** | `D:\Apps\VastuFirst\Documents\VastuFirst-PRD.md` | **What** the app does: the Vastu engine algorithm, rule data, data model, screens, phases, the non-negotiables. The engine spec lives here — do not reinvent it. |
| 2 | **Design handoff bundle** | `D:\Apps\VastuFirst\Design System\Final Designs\VastuFirst Mobile Apps Implementation-handoff.zip` | **How** it looks: the chosen **Sage & Gold** theme, all screens, every component state, the review gate, iOS adaptation rules. |
| 3 | **Design Requirements** | `D:\Apps\VastuFirst\Documents\VastuFirst-Design-Requirements.md` | **Why** the design is the way it is — the constraints the handoff satisfies. Reference only; the handoff supersedes it where they overlap. |

**This document (the Implementation PRD) is the fourth. It sequences the work and defines done.**

### 0.1 Extract the design bundle first

The handoff is a zip. Extract it into the repo (git-ignored) and note these paths — you will use them constantly:

```
<bundle>/vastufirst-mobile-apps-implementation/
  README.md                                          ← how the bundle is meant to be consumed
  project/
    VastuFirst - Sage & Gold Design System.dc.html       ← THE primary visual reference. Read in full.
    handoff/VastuTheme.kt                            ← the populated Compose theme (see 0.2 — NOT drop-in)
    handoff/tokens.json                              ← the same tokens as data, for tooling
    VastuCompass.dc.html                             ← the North-dial interaction reference
    screenshots/                                     ← rendered references for each screen
    exports/png/                                     ← the five shortlisted themes (context only)
```

Per the bundle's own README: **recreate the designs pixel-perfectly in native Compose. Do not copy the prototype's HTML/JS structure** — match the visual output. The `.dc.html` files are prototypes, not architecture.

### 0.2 The handoff theme is a design export, not compilable code — treat it as such

`handoff/VastuTheme.kt` is the **authoritative source of every colour, spacing, radius, elevation and type value**. Its structure (owned `VastuColors`/`VastuSpacing`/`VastuShapes`/`VastuElevation`/`VastuTypography` exposed through `staticCompositionLocalOf`) is the structure to implement. **But it will not compile as delivered.** Before it works you must, in Phase 0:

1. **Add imports** — it has none (`androidx.compose.ui.graphics.Color`, `androidx.compose.ui.unit.dp/sp`, `androidx.compose.ui.text.TextStyle`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.runtime.*`, etc.).
2. **Implement `vastuTypography()`** — it is referenced as the default typography but only *described in comments*, not defined. You must write it as the locale-aware font builder (Typography section of the Product PRD §6, and §3.4 below).
3. **Place it in `commonMain`** so Android and iOS share one theme (iOS is later, but the file location is decided now).
4. Copy the values **exactly** — every `Color(0xFF……)` as given. Do not "improve" the palette; it passed a contrast and colourblind audit in the handoff.

Once these four are done, this file is the theme and nothing downstream may hardcode a value it contains.

---

## 1. What you are building — and what you are deliberately not

**Now:** the Android app, offline-first, up to and including the paid report. In Kotlin Multiplatform + Compose Multiplatform, Android target only.

**Later, by other passes (not now, but do not make them expensive):**

| Deferred | When | The seam you must protect now |
|---|---|---|
| **iOS app** | After Android is tested and stable | The `engine`, `rules`, `shared` and `designsystem` modules stay **pure `commonMain`** with zero Android imports. iOS becomes a re-target, not a rewrite. The handoff already specifies every iOS divergence (§7) — honour those rules as you build Android. |
| **Website** | Parallel track, separate | Nothing in the app should assume a web surface. Keep the engine reusable (it is pure Kotlin — a future web build can call it via Kotlin/JS or a thin server). |

**The single most expensive mistake available to you** is putting an Android dependency into a module that iOS will later share. The Product PRD §3.1 makes this a hard rule; this document operationalises it in §2–3.

---

## 2. Stack and versions

Pin these. Do not float to "latest" without a reason.

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.0+ | K2 compiler |
| UI | Compose Multiplatform 1.6+ (Material3 dependency present but **not themed via it** — see §3.3) | |
| Build | Gradle 8.7+, AGP 8.5+, version catalog (`libs.versions.toml`) | |
| Min / Target SDK | 26 / 35 | min 26 per Product PRD §3.2 |
| DI | Koin (KMP-friendly) or Hilt (Android-only — but then DI cannot live in shared code) → **use Koin** so wiring is shareable with iOS | |
| Async | Coroutines + Flow | |
| Local DB | Room (KMP) or SQLDelight → **SQLDelight**, it is KMP-native and iOS-ready | |
| Serialization | kotlinx-serialization | rule JSON + persistence |
| Backend | Supabase (Postgres, Storage, anonymous auth) | network only where §3.5 of Product PRD allows |
| AI | Groq | assistant + vision plan reading (Phase 4) |
| Payments | Razorpay Android SDK | Phase 5; **Android-only dependency — lives in `app`, never in shared** |
| Images | Coil 3 (KMP) | |
| Testing | kotlin.test + Turbine (Flow) + Compose UI test + Paparazzi/Roborazzi (screenshot) | |

**Rule:** any dependency that is Android-only (Razorpay, Coil-Android, anything `androidx.*` outside Compose Multiplatform's common surface) may appear **only** in the `app` module. If you find yourself wanting one in `engine`, `rules`, `shared` or `designsystem`, stop — that is the §1 mistake.

---

## 3. Project scaffold

### 3.1 Module graph — build this first, verify the boundaries hold

```
vastufirst/
├── engine/          kotlin("multiplatform") · commonMain only · NO Android plugin
│                    Zone maths, 81-pada grid, 32-pada door, rule evaluation, scoring, defects.
│                    Depends on: shared, rules. Nothing else.
├── rules/           kotlin("multiplatform") · commonMain · versioned rule JSON + loader + validation
├── shared/          kotlin("multiplatform") · commonMain · DTOs, result types, enums (Zone, Verdict, …)
├── designsystem/    Compose Multiplatform · commonMain · VastuTheme, tokens, components, icons
│                    Depends on: nothing app-specific. Pure UI kit.
├── data/            kotlin("multiplatform") · repositories, SQLDelight, Supabase client, persistence
│                    Depends on: shared, rules.
└── app/             com.android.application + Compose · Android entry point, navigation, screens,
                     platform integrations (Razorpay, share sheet, compass sensor).
                     Depends on: all of the above.
```

**Enforced boundary (Product PRD §3.1):** `engine`, `rules`, `shared`, `designsystem` must resolve **zero Android dependencies**. Add a CI check in Phase 0 that fails the build if any of these four modules pulls an `com.android.*` artifact. This is the guardrail that keeps iOS cheap.

### 3.2 Package layout inside `app`

```
com.vastufirst.app/
  navigation/        NavHost, routes, arg types
  ui/
    welcome/  addhome/  marknorth/  score/  report/  unlock/
    assistant/  store/  saved/  settings/  legal/
    common/          screen-level composables that are app-specific, not design-kit
  platform/          compass sensor, Razorpay launcher, share intent, file/PDF export
  di/                Koin modules
```

Screens are folders. Each screen folder owns its composable, its ViewModel, and its UI state. ViewModels depend on repositories (`data`) and the engine (`engine`) — never the reverse.

### 3.3 Wiring the design system — the token discipline

The `designsystem` module holds the theme from §0.2. Two absolute rules, both mechanically checkable and both in the handoff's review gate:

- **No raw hex anywhere except inside `VastuColors`.** Every colour is `VastuTheme.colors.<name>`. A `Color(0xFF……)` literal in any screen file is a defect.
- **No raw `.dp` in a spacing position.** Every padding, gap, margin and size comes from `VastuTheme.spacing.*` (or a component's own spec). A bare `16.dp` on a `padding()` is a defect.

Material3 is on the classpath because Compose pulls it, but **you do not theme through `MaterialTheme`** — its `ColorScheme` cannot hold this app's ~28 semantic colours (the handoff theme comment says exactly this). Wrap the app in `VastuTheme { }`, read everything from it, and only borrow Material3 for un-themed primitives (ripple, text-field internals) where an owned control does not yet exist.

### 3.4 Typography — the one non-trivial piece of Phase 0

The type ramp (from the handoff `VastuTheme.kt` comments) is:

```
display  Marcellus 34/1.2 (400)      h1 28/1.25    h2 22/1.3    h3 18/1.35
body-lg  DM Sans 18/1.5              body 16/1.55  body-sm 14/1.5
label    DM Sans 14/1.2 (600)        caption DM Mono 12/1.4    mono 16/1.2 (500)
```

**The hard part is six languages, five non-Latin.** Marcellus / DM Sans / DM Mono cover Latin only. `vastuTypography()` must select **bundled Noto faces per locale** (Noto Sans Devanagari for Hindi/Marathi, Noto Sans Tamil, Noto Sans Telugu, Noto Sans Bengali) so a Hindi screen keeps the same weight and rhythm as English. **Indic scripts get line-height 1.5–1.6, not the Latin 1.2** — bake this into the ramp per script, and never pin a text container's height (Product PRD §6, handoff Localisation note). Bundle all faces; disable the system font-scaling override but still honour up to 200% through the ramp (handoff iOS "Type" rule).

### 3.5 Definition of done — Phase 0

- [ ] Six modules exist with the dependency graph in §3.1; app runs an empty `VastuTheme { }` screen.
- [ ] CI fails if `engine`/`rules`/`shared`/`designsystem` resolve any `com.android.*` artifact.
- [ ] `VastuTheme.kt` compiles in `commonMain` with imports added and values copied exactly from the handoff.
- [ ] `vastuTypography()` renders the ramp correctly in English, Hindi and Tamil with no clipping.
- [ ] A greppable check exists for raw hex and raw `.dp`, wired into the review gate (§6).
---

## 4. The build phases

Phases match the Product PRD §9 numbering so the two documents never disagree. Each phase below adds the **implementation** detail: file-level deliverables, wiring, and a verification gate you must watch pass before moving on.

**Golden rule between phases:** the engine is built and fully tested *headless* before any screen consumes it. UI bugs are cheap; a wrong score is the one thing this product cannot ship.

---

### Phase 0 — Foundations
**Goal:** the skeleton compiles, the boundaries are enforced, the theme is real.
Covered in §3. Done = §3.5 checklist.

---

### Phase 1 — The Vastu engine (headless)
**Goal:** a pure-Kotlin engine that scores a plan correctly, with no UI.

**Implement, from Product PRD §4 and §8:**
- `engine`: 81-pada grid with north rotation (§4.2), three `ZoneAssignmentStrategy` values, configurable Brahmasthan extent, 32-pada door resolution with both location methods (§4.3), room evaluation → 5 verdicts (§4.4), scoring with door contribution + defect penalty (§4.5), cut/extension detection (§4.2.7), dispute surfacing.
- `rules`: load the versioned JSON dataset (§5.1), validate on load (32 padas present, every RoomType ruled or explicitly unruled, every disputeId resolves, every defect has ≥1 remedy). Fail loud at startup, never mis-score silently.
- `shared`: all enums and result types from Product PRD §5.

**Verification gate — do not exit Phase 1 until every one passes:**
- [ ] **The worked example (Product PRD §15) reproduces exactly: `sample-01` scores `31`.** This is the anchor test. If it is not 31, the engine is wrong, not the fixture.
- [ ] Toilet-in-NE fixture raises `X-01` at MAJOR; stairs-in-Brahmasthan raises `X-03`.
- [ ] **Rotation invariance:** rotating plan + door + North by the same angle yields the identical score. (This is the correct form of the facing-neutrality rule — Product PRD §0.4, §15.)
- [ ] Static check: `Analysis` exposes no `facingDirection` field and no rule keys off building orientation.
- [ ] Disputed rules (POOJA/W-12) return `NOT_SCORED` and contribute nothing to the score.
- [ ] Swapping a value in the rule JSON changes output with **no recompile**.
- [ ] Boundary fixtures pass: room straddling a pada line, L-shaped footprint, room on the Brahmasthan edge.

**Why this phase is headless and first:** if an expert ruling later changes a rule (Product PRD §13), it is a JSON edit against a tested engine, not a UI rebuild. This is the whole reason the architecture is shaped this way.

---

### Phase 2 — Android app, guided-grid path → **the 4 August milestone**
**Goal:** a real person scores their real home on their own phone, fully offline.

**Build these screens** (visual source: the Sage & Gold design system; interaction source: `VastuCompass.dc.html` for the dial):

| Screen | Source of truth | Notes |
|---|---|---|
| Welcome | design system | Language picker (6), intent picker (3), "No sign-up · No phone number" |
| Add home — method choice | design system | This phase wires **guided grid + sample plans only**. Upload/AI is Phase 4. |
| Guided grid editor | design system | The hard one. One-handed room placement + type labelling. |
| Mark North | `VastuCompass.dc.html` | The signature dial. Live score. Clean centre. **No "best angle" affordance.** |
| Score — free | design system | Big number, zone map, top-3 defects, honest count of the rest. |
| Full report — paid | design system | Ranked issues, layout-change vs remedy, **provenance tags**, disputes, "already right", "not assessed". |

**Wiring:**
- `data`: SQLDelight schema for plans/analyses/reports; save and reopen a plan; store `ruleSetVersion` on every analysis (Product PRD §5).
- Score recompute on North drag stays 60fps by debouncing to ≤50ms and computing off the main thread (Product PRD §4.5.3) — **not** by requiring a 16ms engine.
- English only this phase. Disclaimer visible on the report screen.

**Explicitly NOT in Phase 2:** AI plan reading, AI assistant, payments, remedy store, other languages, iOS.

**Verification gate:**
- [ ] APK installs on a physical mid-range device; a plan added via guided grid scores with **no network**.
- [ ] Every screen passes the review gate (§6) — including in a 360dp-width check.
- [ ] The report screen renders the intent branch (Product PRD §2, §5.5): a `LIVING` user does **not** see "move your kitchen" as the headline fix.
- [ ] Mark-North has no affordance that adjusts North to raise the score.

---

### Phase 3 — Client testing + website (parallel)
**Goal:** the Android build survives two weeks of real use; the website is built alongside (separate track, not this app).

- Fix from real feedback. **Exit criterion is written, not felt:** no open P1/P2 bugs; all core flows pass on three physical devices.
- Website is out of scope for this codebase — do not add a web surface to the app to accommodate it.

---

### Phase 4 — Reports, remedies, AI, languages
**Goal:** the full product surface, still Android.

- **AI-assisted plan reading** (Product PRD §6.2b): vision model drafts rooms; **user confirms every room before any score**. Low confidence → fall back to guided grid, no error state. There is no fully-automatic path to a score.
- **AI assistant** (§7.1): retrieval over the rule dataset only, cites rule id + provenance, says so when the KB is silent, never mixes traditions, never predicts outcomes.
- **Remedies** (§7.2): ship gadget remedies but tag every one `MOD`, rank layout-change and Vastu Shanti above them.
- **Six languages** (§7.5): rule explanations translated by a Vastu-literate human, wired as translatable data, not hardcoded strings. Machine-translated rule text is a defect.
- **Flats** (§7.4): the second analysis path; report copy honest about what a flat owner cannot move.
- **16-zone / 45-devata profiles:** build the abstraction; shipping is **gated on the M-11 expert ruling** (§13). Do not enable them by default before the ruling lands.

**Verification gate:** AI never writes a score without user confirmation; every AI answer carries a citation; `MOD` remedies never rank above layout change; a Hindi and a Tamil report render without clipping.

---

### Phase 5 — iOS, payments, store *(iOS is the deferred pass — see §7)*
**Goal (Android side of it):** payments and the remedy store on Android.

- Razorpay integration **in `app` only** (Android dependency).
- Remedy store matched to the user's actual defects; clean, ad-light.
- iOS begins here as a re-target of the shared modules — its detail is §7, not this phase's Android work.

---

### Phase 6 — Launch
- Legal: visible disclaimer (already on report), privacy policy, terms, refund policy, DPDP consent flow.
- Play Store listing, data-safety declaration, content rating — honest.
- Submission argues **genuine utility** (the engine + structured report), not fortune-telling, for guideline compliance (Product PRD §11).
---

## 5. Coding standards that keep the build verifiable

These are the rules that let "does the build match the spec?" be answered by comparison, not opinion.

1. **Tokens only.** No raw hex outside `VastuColors`; no raw `.dp` in spacing positions. Both are greppable (§6) and both decay fastest without enforcement.
2. **Engine purity.** `engine`/`rules`/`shared`/`designsystem` never import `com.android.*`. CI enforces it.
3. **Rules are data.** Every Vastu value lives in the rule JSON, versioned. A disputed or open-question rule (Product PRD §13) is a config flag, never a Kotlin constant. Eight rulings are still pending — hardcoding any of them is a defect.
4. **The engine is tested before it is shown.** No screen consumes a score the engine tests do not cover.
5. **State, not screenshot.** Every interactive component implements every applicable state (default, pressed, disabled, focused, error, loading, empty, selected — whichever apply). The handoff carries the button state table as the exemplar; the pattern repeats for every component.
6. **Preserve the iOS seam.** Before adding any dependency, ask which module it lands in. Android-only → `app`. Never shared.

---

## 6. Per-screen definition of done — the review gate

This is the handoff's **Screen review gate**, reproduced as the gate every screen must pass before it is called done. Run it against each screen. Any **no** means not finished.

```
SCREEN REVIEW GATE

TOKENS
[ ] Every colour resolves to a VastuTheme token. Zero hardcoded hex in this file.   (greppable)
[ ] Every spacing value comes from VastuTheme.spacing. No raw .dp on padding/gap.    (greppable)
[ ] Every text style comes from VastuTheme.type. No ad-hoc fontSize/fontWeight.
[ ] Every radius from VastuTheme.shapes; every border from a border token.

COMPONENTS
[ ] Every interactive component renders its correct pressed state.
[ ] Every interactive component renders its correct disabled state.
[ ] Loading / empty / error states exist where the screen can reach them.
[ ] Variants come from the component API, not inline restyling.

LAYOUT
[ ] Matches the reference mockup at 412×915 dp.
[ ] Checked at 360 dp width — no clipping or overlap.

ACCESSIBILITY (hard constraints — handoff §Accessibility)
[ ] Every touch target ≥ 48×48 dp.
[ ] Body text ≥ 16 sp; nothing below 12 sp.
[ ] Every text/background pair meets contrast (4.5:1 body, 3:1 large).
[ ] No information by colour alone — label, icon or position also present.
[ ] Every interactive element has a content description.
[ ] Usable at 200% font scale.

LOCALISATION
[ ] Renders without clipping in Hindi and Tamil, not only English.
[ ] No fixed-height container holds translatable text.

PRODUCT RULES
[ ] Compass centre unobscured by any branding or decoration.
[ ] No affordance suggests North can be adjusted to improve the score.
[ ] Where a Vastu rule is shown, its provenance tag is shown with it.
```

The two greppable lines should be a scripted check in CI, not a manual read — they are the ones that rot silently.

---

## 7. iOS — deferred, but the rules are already written

Do not build iOS now. But every divergence is already specified in the handoff, so honour these while building Android and the later iOS pass is a mechanical re-skin, not a redesign.

| Concern | Android (now) | iOS (later) |
|---|---|---|
| Navigation | System back button + predictive back gesture | Swipe-from-edge back + nav-bar chevron |
| Safe areas | Status + nav-bar insets via `WindowInsets` | Notch / Dynamic Island + home indicator; content never under either |
| Elevation | Shadow per elevation token | Border + separator per token, no shadow — **both already defined in the theme** |
| Controls | Owned switch, slider, dial, degree field — no Material control | Identical owned controls; no `UISwitch`/`UISlider`. Re-skin is mechanical |
| Haptics | Tick on quick-set chips; soft detent every 15° on the dial; success on unlock | Same map via `UIImpactFeedbackGenerator` |
| Type | Bundled Marcellus + DM + Noto; disable system scaling override | Same bundled faces; respect Dynamic Type to 200% via the ramp |
| Share / export | Android share sheet for the PDF report | iOS share sheet (`UIActivityViewController`), same PDF payload |

The practical consequence for **now**: build owned controls (switch, slider, the North dial, the degree field) rather than reaching for Material equivalents, and define elevation as *both* a shadow and a border in the theme. Do both while building Android — retrofitting them at the iOS stage is the expensive path.

**Website** is a separate track and not part of this codebase. The engine being pure Kotlin means a future web build can reuse it; nothing you do in `app` should assume a web surface exists.

---

## 8. Decisions the owner must make (not yours to invent)

1. **Report price.** The design mockups show **₹699** one-time (with ₹149/₹249/₹399 shown as anchoring). This is still the owner's call, and per Product PRD §17 it must be modelled against the per-report AI cost before it is fixed — image-based plan reading costs materially more per call than text, and a report can be sold at a loss unnoticed. Use ₹699 as the placeholder in the paywall; do not treat it as final.
2. **The eight expert rulings** (Product PRD §13). Needed **before Phase 4 hardens the reports** — the disputed rules currently render as "schools disagree", which is correct, but a ruling that resolves one changes shipped output.
3. **E7 / S7 pada names** are absent from the knowledge base and left null. A reviewer supplies them or confirms the gap.
4. **`BUYING` intent's comparison view** is promised in the Product PRD but no screen exists for it. Either it is specified and built, or the promise is softened to "score each option separately."

---

## 9. First session — where to start

1. Read the three inputs in §0. Extract the bundle (§0.1).
2. Stand up the six-module scaffold (§3.1) and the CI boundary check.
3. Land `VastuTheme.kt` in `commonMain` with imports and `vastuTypography()` (§0.2, §3.4).
4. Build the engine headless and make the §15 worked example score **31** (Phase 1).
5. Only then build the first screen, and run it through the review gate (§6).

Do not start on screens before the engine reproduces 31. The engine is the product; the screens are how people reach it.
