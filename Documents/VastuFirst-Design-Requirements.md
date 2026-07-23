# VastuFirst — Design Requirements
**For Claude Design · v1.1 · 19 July 2026**
**Target: Android first. iOS follows. Design for both from the start.**

> **STATUS — RESOLVED (23 Jul 2026): the chosen theme is Sage & Gold** — sage-green primary `#7A9E7E` + gold `#C9A227`, on a warm paper ground. Stage 1 is complete. This document is the original brief, kept for the *why*. Where it discusses the four exploration directions or the earlier "paper-and-warm-accent" incumbent, that is **historical — none of those is the active theme.** The authoritative colour / spacing / type values live in the handoff (`VastuTheme.kt` / `tokens.json`), which supersedes this file where they overlap.

---

## 0. The ask, in one paragraph

Design the complete visual system and screen mockups for **VastuFirst**, an Android app (iOS to follow) that reads a home's floor plan and tells the user what to change to align it with Vastu Shastra. The output must be precise enough that an **AI coding tool** implements it without asking follow-up questions — named tokens, exact values, every interaction state, and a checklist it can grade itself against. Stage 2 is a **code-first, enforceable contract**, not a set of screens (§4.0). This document is self-contained; you do not need any other file.

**Work in two stages. Do not skip to stage 2.**

> ### STAGE 1 — Present 4 theme options first. Stop. Wait for a choice.
> Before building anything, present **four distinct colour themes**, each shown applied to the same two screens so they can be compared honestly. The client picks one. Only then build the full system.
> Detailed brief in §3.

> ### STAGE 2 — Build the full design system and all screen mockups
> Using the chosen theme. Detailed brief in §4 onward.

**Note on deliverable count:** the signed client plan commits to showing the end client *three* design concepts to choose from. So: you produce **four** themes → the product owner narrows to **three** → the end client picks **one**. Design all four to be genuinely shippable; none is a strawman.

---

## 1. What the product is

**VastuFirst analyses a home's floor plan against Vastu Shastra and tells the user what to change — while changing it is still free.**

The wedge is **pre-construction**. You can move a kitchen on a drawing for nothing. You cannot move a wall in a finished flat. Every competitor sells remedies to people who already live somewhere; VastuFirst targets the moment before concrete is poured.

**Core loop:** add your floor plan → mark which way North is → get a score, a defect report, and specific layout changes plus remedies for what cannot move.

**Market:** India. Users are ordinary people planning or buying a home — not architects, not scholars. Many are middle-aged, many are reading an architect's PDF on a mid-range Android phone, many will use the app in Hindi or a regional language rather than English.

**Competitors** (Grihafy, Apzok, AppliedVastu) are web-first, cluttered, and gate everything behind a phone number. Our differentiators are directly visual: a real native app, instant results, no sign-up wall, transparent sourcing, and honesty where tradition genuinely disagrees with itself.

### 1.1 The emotional job of the design

This is the part most Vastu apps get wrong, and it should drive the aesthetic.

Vastu carries real anxiety. People worry their home will bring misfortune. Rival apps **lean into that fear** — red warnings, doom language, upsells to expensive remedies. That is both distasteful and, in India, legally risky.

**VastuFirst's tone is calm, warm, and constructive.** A low score is an *opportunity*, not a verdict — especially for someone who has not built yet, where every problem is still free to fix. The design must feel like a knowledgeable, unhurried friend who respects the tradition without exploiting belief in it.

Concretely, this means:
- **Bright and cheerful, not dark and mystical.** No black backgrounds, no occult styling, no glowing runes. This is a warm, daylit, paper-and-ink feel.
- A score of 26/100 must not feel like a death sentence. Pair every low score with what can be done about it.
- Never use fear, urgency, countdowns, or loss-framing to drive a purchase.
- Respectful of Hindu tradition without kitsch. Restraint over ornament.

---

## 2. Non-negotiable design constraints

Read these before you design anything. They are product and legal requirements, not preferences.

**2.1 Bright, warm, daylit.** Not dark-mode-first, not mystical. See §1.1.

**2.2 Six languages, five of them non-Latin.** English, हिन्दी (Devanagari), தமிழ் (Tamil), తెలుగు (Telugu), मराठी (Devanagari), বাংলা (Bengali). **This is a hard typographic constraint and it currently breaks — see §6. Solve it in stage 1, not as an afterthought.**

**2.3 Android now, iOS later, one shared codebase.** The app is built in Compose Multiplatform, so the *same* UI code renders on both platforms. Therefore: **design an owned design language, not a Material 3 skin.** Material-specific components (FABs, Material switches, Material top-app-bar behaviour) will look wrong on iOS. See §7.

**2.4 The compass centre stays clean.** The single most-complained-about flaw in the leading rival is that it puts its own logo over the exact centre point of the compass — the point users need to read. Our centre is never obscured by branding, watermark, or decoration. Ever.

**2.5 Provenance tags are a first-class UI element, not a footnote.** Every Vastu rule shown to a user carries a visible tag saying where it comes from. This is the product's core differentiator. It needs real design attention. See §5.3.

**2.6 No fear-based or outcome-promising visual language.** No "your family is at risk" framing. No guarantees of wealth, health, marriage or fortune — in copy, iconography, or illustration. This is a legal requirement under India's Consumer Protection Act and ASCI advertising rules.

**2.7 A visible legal disclaimer.** On the report screen, legible, not buried in settings. Wording: *"Vastu is a traditional practice. This is guidance for your own decisions — not a guaranteed outcome."* Design it so it reads as honest rather than as fine print.

**2.8 No sign-up wall before value.** No account, no phone number, no OTP before the free score. The design must never imply one is coming.

**2.9 Colour can never be the only signal.** See §8 — this app is unusually colour-dependent and that is an accessibility risk.

---

## 3. STAGE 1 — The four theme options

**Deliver this first, then stop.**

### 3.1 What a "theme" means here

Not four accent colours. Each theme is a complete, coherent palette that must survive an unusually heavy colour load. This app needs, simultaneously:

| Set | Count | Purpose |
|---|---|---|
| Brand | 2–3 | Primary, secondary, and a wordmark treatment |
| Surface & ink | 5–6 | Background, card, borders, three text weights |
| **Zone colours** | **9** | N, NE, E, SE, S, SW, W, NW, and the sacred centre — all shown together on one map, all must be distinguishable |
| **Verdict states** | **5** | Ideal, Acceptable, Suboptimal, Defect, Not-assessed |
| **Score bands** | **3** | Strong / workable / needs work |
| Provenance tags | 4 | Classical text, Traditional practice, Modern practice, Schools disagree |

**That is roughly 28 colours that must coexist on a single screen without becoming a fruit salad.** This is the real design problem, and it is where most themes will fall apart. A theme that looks beautiful as a swatch row and illegible on the zone map has failed.

The zone colours are the hardest: nine hues, adjacent on a circular map, each needing to be identifiable at a glance, while remaining harmonious and calm. Consider whether they should be a modulated single-family scheme rather than nine independent hues.

### 3.2 How to present each theme

For each of the four, provide:

1. **A name and a one-line rationale** — what feeling it targets and who it appeals to
2. **The full palette** with hex values and semantic role names (not "orange" but `brand/primary`, `zone/northeast`, `verdict/defect`)
3. **The nine zone colours shown on an actual zone map**, not as a swatch strip
4. **The same two screens rendered in that theme** — the **Score screen** and one **Report card** with a defect and a provenance tag. Same content in all four, so the only variable is the theme.
5. **A contrast audit** — every text/background pair with its WCAG ratio (see §8)

### 3.3 Directions to explore

Four genuinely different directions, not four tints of the same idea. Suggested starting points, but you may substitute better ones:

- **A · Paper and warm accent.** The incumbent direction (see §9): warm paper ground, a warm accent pair, teal support, serif display. Feels like a well-made book. Safe, proven, already tested with the client. *(Historical — the chosen theme is Sage & Gold; see the status note at the top.)*
- **B · Temple stone.** Cooler and more architectural — sandstone, weathered copper, deep indigo. Leans on the *architecture* half of Vastu rather than the ritual half. Would appeal to the architect the report gets forwarded to.
- **C · Daylight and ink.** Near-white, high-clarity, one strong accent, very restrained colour elsewhere. Lets the nine zone colours carry all the chroma without competition. The most modern and the most legible.
- **D · Earth and marigold.** Warmer and more distinctly Indian — terracotta, turmeric, leaf green, with the zone colours drawn from natural pigment. Highest cultural resonance, highest risk of kitsch.

**Constraint on all four:** must satisfy §1.1 and §2.1 — bright, warm, calm, non-mystical. None may be dark-first.

### 3.4 What to say about each

For each theme, state plainly: **who it wins, what it risks, and where it is weakest.** The product owner is choosing on merit and needs the downsides, not a sales pitch for each.
---

## 4. STAGE 2 — The design system as an enforceable contract

Build only after a theme is chosen.

### 4.0 Who reads this, and why it changes what you produce

**The implementer is an AI coding tool** (Claude Code), building in Kotlin Multiplatform + Compose Multiplatform for Android and iOS. That has one consequence that should shape every decision in this stage:

> **An AI implementer drifts from anything vague and holds precisely to anything named.**

Give it "a warm, generous amount of padding" and you get four different values across four screens. Give it `space-4` and you get 16dp, every time, on every screen, in both platforms.

So the output of stage 2 is not a set of pictures with notes. **It is a contract** — a system where the question *"does the build match the design?"* is answered by **comparison, not opinion**.

Three rules follow:

1. **Prefer exact tokens over descriptions.** Never "a soft grey border"; always `color-border-default`.
2. **Prefer stated rules over examples.** Never "buttons look like this"; always "all primary buttons are 48dp tall, `radius-full`, `color-primary`, `text-on-primary`, `space-4` horizontal padding."
3. **Make every visual decision checkable.** If a reviewer cannot mechanically verify it, it is not specified yet.

Produce §4.1 → §4.2 → §4.3 → §4.4 in that order. Each depends on the one before it.

---

### 4.1 Tokens — the single source of truth

Every value in the system is a **named token**. No loose descriptions, no raw values anywhere downstream.

**Deliver the tokens twice:**

- **(a) A human-readable reference table** — for the product owner and for review. This is documentation.
- **(b) A ready-to-paste Compose theme** — this is **the real deliverable**. Claude Code should be able to drop it into the project and start building against it with no translation step.

#### 4.1.1 What must be tokenised

| Group | Requirement |
|---|---|
| **Colour** | Every colour, named, with exact hex. `color-primary`, `color-surface`, `color-defect`, `color-ideal`, and so on. **No colour may ever appear in app code as a raw hex — only as a token.** Includes all 9 zone colours, all 5 verdict states, 3 score bands, 4 provenance tags. |
| **Spacing** | A fixed scale. `space-1`=4, `space-2`=8, `space-3`=12, `space-4`=16, `space-6`=24, `space-8`=32, `space-10`=40, `space-12`=48, `space-16`=64. **All spacing in the app comes from this scale.** No arbitrary values. |
| **Typography** | A named ramp: `display`, `h1`, `h2`, `h3`, `body-lg`, `body`, `body-sm`, `label`, `caption`, `mono`. Each with font family, size, weight, line-height, letter-spacing. Per script where they diverge (§6). |
| **Radii** | `radius-sm`, `radius-md`, `radius-lg`, `radius-full`. Named, exact. |
| **Elevation / shadow** | `elevation-flat`, `elevation-raised`, `elevation-overlay`, `elevation-modal`. Each given **both** a shadow spec (Android) and a border/separator spec (iOS) — see §7.2. |
| **Border** | `border-hairline`, `border-default`, `border-strong`, `border-focus`. Width plus colour token. |
| **Motion** | `duration-instant/short/medium/long` and `easing-standard/decelerate/accelerate`. |

Naming is lowercase, hyphenated, and stable. Once published, a token is not renamed — it is deprecated and replaced.

#### 4.1.2 The Compose theme — structural requirement

Material 3's `ColorScheme` has fixed slots (primary, secondary, surface…) and **cannot hold this app's ~28 semantic colours** — there is no Material slot for "north-east zone" or "provenance: schools disagree". Combined with §7.1 (own the design language, do not skin Material), the theme must be **an owned theme object exposed through CompositionLocals**, not a `MaterialTheme` colour override.

Deliver this shape, fully populated:

```kotlin
// ---- Colour ---------------------------------------------------------------
@Immutable
data class VastuColors(
    // brand + surface
    val primary: Color, val onPrimary: Color, val secondary: Color,
    val background: Color, val surface: Color, val surfaceRaised: Color,
    val borderDefault: Color, val borderStrong: Color, val borderFocus: Color,
    val textPrimary: Color, val textSecondary: Color, val textTertiary: Color,
    // the nine zones + their low-opacity map fills
    val zoneN: Color, val zoneNE: Color, val zoneE: Color, val zoneSE: Color,
    val zoneS: Color, val zoneSW: Color, val zoneW: Color, val zoneNW: Color,
    val zoneCentre: Color,
    val zoneFillN: Color, /* …nine fills… */
    // five verdict states + fills
    val verdictIdeal: Color, val verdictAcceptable: Color,
    val verdictSuboptimal: Color, val verdictDefect: Color,
    val verdictNotAssessed: Color,
    // three score bands
    val scoreStrong: Color, val scoreWorkable: Color, val scoreAttention: Color,
    // four provenance tags
    val provenanceText: Color, val provenanceDeriv: Color,
    val provenanceMod: Color, val provenanceDisp: Color,
    // feedback
    val success: Color, val warning: Color, val error: Color, val info: Color,
)

// ---- Spacing --------------------------------------------------------------
@Immutable
data class VastuSpacing(
    val s1: Dp = 4.dp,  val s2: Dp = 8.dp,  val s3: Dp = 12.dp,
    val s4: Dp = 16.dp, val s6: Dp = 24.dp, val s8: Dp = 32.dp,
    val s10: Dp = 40.dp, val s12: Dp = 48.dp, val s16: Dp = 64.dp,
)

// ---- Type, shape, elevation ----------------------------------------------
@Immutable data class VastuTypography(
    val display: TextStyle, val h1: TextStyle, val h2: TextStyle, val h3: TextStyle,
    val bodyLg: TextStyle, val body: TextStyle, val bodySm: TextStyle,
    val label: TextStyle, val caption: TextStyle, val mono: TextStyle,
)
@Immutable data class VastuShapes(
    val sm: CornerBasedShape, val md: CornerBasedShape,
    val lg: CornerBasedShape, val full: CornerBasedShape,
)
@Immutable data class VastuElevation(
    val flat: Dp, val raised: Dp, val overlay: Dp, val modal: Dp,
)

// ---- Access ---------------------------------------------------------------
val LocalVastuColors     = staticCompositionLocalOf<VastuColors> { error("no theme") }
val LocalVastuSpacing    = staticCompositionLocalOf { VastuSpacing() }
val LocalVastuTypography = staticCompositionLocalOf<VastuTypography> { error("no theme") }
val LocalVastuShapes     = staticCompositionLocalOf<VastuShapes> { error("no theme") }
val LocalVastuElevation  = staticCompositionLocalOf<VastuElevation> { error("no theme") }

@Composable
fun VastuTheme(content: @Composable () -> Unit) { /* provides all locals */ }

object VastuTheme {
    val colors: VastuColors      @Composable get() = LocalVastuColors.current
    val spacing: VastuSpacing    @Composable get() = LocalVastuSpacing.current
    val type: VastuTypography    @Composable get() = LocalVastuTypography.current
    val shapes: VastuShapes      @Composable get() = LocalVastuShapes.current
    val elevation: VastuElevation @Composable get() = LocalVastuElevation.current
}
```

**Deliver it populated with the chosen theme's real values** — every `Color(0xFF……)` filled in, every `TextStyle` complete. Not a skeleton. It must compile and render as-is.

Also supply `tokens.json` mirroring the same names, for any tooling that needs it. **Where the two disagree, the Compose theme wins** — it is the source of truth.

**Platform note:** this file lives in `commonMain` so Android and iOS share one theme. Anything that must differ by platform goes through `expect`/`actual`, not a second theme.

---

### 4.2 Components — every state pinned to tokens

For every component, specify **every applicable state**: default, pressed, disabled, focused, error, loading, empty, selected. A state that is not specified is a state the implementer will invent.

For each state give the **exact tokens** — background, text, border, radius, height, padding, elevation. Not adjectives.

**State every fixed rule as a rule**, in this voice:

> All primary buttons are **48dp** tall, `radius-full`, background `color-primary`, label `text-on-primary` in `label` style, horizontal padding `space-4`, full width unless inside a row.
> Pressed: background `color-primary` at 88% brightness, no size change.
> Disabled: background `color-primary` at 38% opacity, label `text-on-primary` at 38%, no elevation, not focusable.

Use this table shape for every component:

| State | Background | Text | Border | Radius | Height | Padding | Elevation |
|---|---|---|---|---|---|---|---|
| Default | `color-primary` | `text-on-primary` | none | `radius-full` | 48dp | `space-4` | `elevation-raised` |
| Pressed | … | … | … | … | … | … | … |
| Disabled | … | … | … | … | … | … | … |

**Components requiring this treatment:**
Buttons (primary, secondary, ghost, destructive × 3 sizes) · text field · numeric/degree input · chip and toggle · selection card · content card and its defect/warning/positive variants · **provenance tag (4 variants)** · **verdict pill (5 variants)** · score display · slider · list row · section header · divider · bottom sheet · dialog · toast/snackbar · segmented control · progress and skeleton · empty state · error state · paywall card.

**A component the implementer cannot get wrong is one whose every state is pinned to a token.** That is the bar.

---

### 4.3 The design-review checklist — a pass/fail gate

Produce a checklist Claude Code runs against **every screen it builds, before declaring that screen done**. Write it as a gate, not as advice: each line is answerable yes or no, and any **no** means the screen is not finished.

Include at minimum the following, and add any theme-specific checks the chosen system needs:

```
SCREEN REVIEW GATE — all must pass

TOKENS
[ ] Every colour resolves to a theme token. Zero hardcoded hex literals in this file.
[ ] Every spacing value comes from VastuTheme.spacing. No raw .dp on padding, margin or gaps.
[ ] Every text style comes from VastuTheme.type. No ad-hoc fontSize or fontWeight.
[ ] Every corner radius comes from VastuTheme.shapes.
[ ] Every border width and colour comes from a border token.

COMPONENTS
[ ] Every interactive component renders its correct pressed state.
[ ] Every interactive component renders its correct disabled state.
[ ] Loading, empty and error states exist where the screen can reach them.
[ ] No component has been restyled inline; variants come from the component API.

LAYOUT
[ ] Screen matches the reference mockup for this screen at 412x915dp.
[ ] Screen has been checked at 360dp width with no clipping or overlap.

ACCESSIBILITY  (hard constraints, section 8)
[ ] Every touch target is at least 48x48dp.
[ ] Body text is at least 16sp; no text below 12sp anywhere.
[ ] Every text/background pair meets its contrast minimum (4.5:1 body, 3:1 large).
[ ] No information is conveyed by colour alone — label, icon or pattern also present.
[ ] Every interactive element has a content description.
[ ] Screen is usable at 200% font scale.

LOCALISATION
[ ] Screen renders without clipping in Hindi and Tamil, not only English.
[ ] No fixed-height container holds translatable text.

PRODUCT RULES
[ ] The compass centre is unobscured by any branding or decoration.
[ ] No affordance suggests the user can adjust North to improve their score.
[ ] Where a Vastu rule is shown, its provenance tag is shown with it.
```

Two of these can be checked mechanically and should be called out as such in the deliverable: **zero hardcoded hex** and **zero raw `.dp` in spacing positions** are both greppable, and are the two rules that decay fastest without enforcement.

---

### 4.4 Iconography

A single coherent set. Line-based, one stroke weight, 24dp grid. Must include a Vastu-specific set that does not currently exist anywhere: room types (kitchen, toilet, pooja, staircase, bedroom, living, study, store, garage, balcony), compass and North marker, zone glyph, defect, remedy, layout-change, provenance marks, PDF export, share.

**No religious iconography used decoratively.** Symbols with genuine meaning (swastika, Om, yantra forms) are not ornament. If a yantra appears it is because a rule references it, drawn accurately, in context.

---

## 5. Screens to design

Full mockups at **412 × 915 dp** (a common mid-range Android size — not a flagship, deliberately). Also provide a 360 dp-wide variant for the small-device check.

### 5.1 The screen list

| # | Screen | Notes |
|---|---|---|
| 1 | Welcome / onboarding | Language picker (6), intent picker (3), "No sign-up · No phone number" reassurance |
| 2 | Add home — method choice | Three paths: guided grid, photo/PDF upload, sample plan |
| 3 | **Guided grid editor** | User places and labels room blocks on a grid. Hard: must work one-handed on a phone |
| 4 | Plan upload + AI confirmation | AI drafts rooms; **user must confirm or correct each one**. Design the confirm/correct interaction and the "unsure about this room" state |
| 5 | **Mark North** | The signature screen. See §5.2 |
| 6 | **Score — free tier** | Big number, zone map, top 3 defects, honest count of the rest |
| 7 | **Full report — paid** | Ranked issues, layout change vs remedy, provenance, disputes. See §5.3–5.4 |
| 8 | Unlock / payment | Honest, no dark patterns, price visible before commitment |
| 9 | AI assistant chat | Answers cite their source and provenance |
| 10 | Remedy store + product detail | Clean, ad-light, matched to the user's actual defects |
| 11 | Saved plans list | Empty state matters |
| 12 | Settings | Language, school profile, data & privacy, delete my data |
| 13 | Legal / disclaimer | §2.7 |

### 5.2 Screen 5 — Mark North (the signature interaction)

The user rotates a compass dial to tell the app which way North faces on their plan. **Everything downstream keys off this single input**, and it is the moment the product feels alive: as the dial turns, every room re-zones and the score moves live.

Design requirements:
- A large, draggable dial with an unambiguous North marker. Comfortable one-handed thumb reach.
- **The centre of the dial stays visually clean** — no logo, no watermark, no decoration. §2.4.
- Three input routes, all equally valid: drag the dial, drag a slider, type exact degrees.
- Quick-set chips for N / E / S / W.
- The score updates continuously while dragging. Design the live-update treatment so it feels responsive but not frantic — a number flickering wildly during a drag feels broken.
- A secondary, clearly-optional route: read North from the phone's compass, with calibration state and an accuracy indicator. **Secondary by design** — most users are reading a PDF at home, and you cannot stand inside a house that has not been built yet.
- Motion tokens for the dial: define the drag response, the settle, and the score transition.

**There must be no "find the best angle" affordance.** North is a physical fact about the building, not a setting to optimise. If a design suggests the user can tune it for a better score, that design is wrong.

### 5.3 Provenance tags — the differentiator

Every Vastu rule shown carries a visible, tappable tag saying where it comes from. Four variants:

| Tag | Label shown | Meaning |
|---|---|---|
| `TEXT` | **From classical text** | Traceable to a named source, with citation |
| `DERIV` | **Traditional practice** | Reasoned from the mandala, taught widely, not a specific verse |
| `MOD` | **Modern practice** | 20th-century, no classical basis |
| `DISP` | **Schools disagree** | Two readings shown, neither chosen |

Design challenge: these must be **informative without being alarming**. A `MOD` tag on a remedy is not a warning that it does not work — it is honesty about its age. If `MOD` reads as a red flag, users will distrust the whole remedy section and the design has failed.

They appear frequently — on nearly every report item — so they must be quiet enough to live at that density, yet noticeable enough to be discovered and tapped.

### 5.4 Screen 7 — the report

Two visually distinct kinds of advice, because they are genuinely different acts:

- **Change the layout** — free right now, on paper. The primary value for someone still building. Should feel actionable and optimistic.
- **Remedies** — for what cannot move. Secondary, and never presented as equivalent to fixing the actual problem.

Also on this screen:
- Issues ranked by severity, with a clear visual hierarchy between MAJOR / MODERATE / MINOR
- An **"Already right — leave alone"** section. This matters: it proves the app is not merely fault-finding, and it is a moment of relief in a screen full of problems.
- A **"Where the schools disagree"** section showing both readings side by side with no winner declared. Design this as two balanced columns — not one primary and one caveat.
- A **"Not assessed"** section for rules that could not be evaluated because the user did not supply the input. Must read as neutral, never as a pass or a failure.
- Export to PDF and share. **This is the moment the product becomes genuinely useful**, because the report goes to the architect. Give it real prominence.

### 5.5 The report branches on intent

The user states up front whether they are **building**, **buying**, or **already living there**. This changes what the report can honestly offer:

| Intent | Report emphasis |
|---|---|
| Building | Layout changes lead. Everything is still free to move. Most optimistic tone. |
| Buying | Layout changes plus the option to walk away or negotiate. |
| **Already living there** | **Remedies lead.** Walls cannot move. Showing "move your kitchen to the South-East" as the headline fix is a product failure — design the demoted, "if you ever renovate" treatment for layout changes. |

Provide the report screen in at least two of these three states so the difference is visible.
---

## 6. Typography — and a problem that must be solved in stage 1

### 6.1 The problem, stated plainly

The existing prototype loads three typefaces: **Marcellus** (display serif), **DM Sans** (body), **DM Mono** (labels and numerals). Its language picker offers हिन्दी, தமிழ், తెలుగు, मराठी and বাংলা.

**None of those three typefaces supports Devanagari, Tamil, Telugu or Bengali.** As it stands, five of the six launch languages fall back to whatever the device happens to provide — inconsistent metrics, broken vertical rhythm, and in the worst case missing glyphs. Five-sixths of the intended market would see a different, unstyled app.

This is a design-system problem, not a localisation afterthought. **Solve it as part of stage 1**, because it constrains which display faces are even viable.

### 6.2 What is required

- A type system that renders **all six languages** with consistent weight, colour and rhythm
- Per-script families where a single family cannot cover everything, with **matched optical sizes** so a Hindi screen and an English screen feel like the same product
- **Indic scripts need more vertical space than Latin** — typically 1.4–1.6 line-height against 1.2 for Latin display text, because of ascenders, matras and conjuncts. Every component that contains text must be specified to accommodate this without clipping or re-layout. Fixed-height buttons and cards designed against English will break in Devanagari and Tamil.
- **Test string requirement:** show the type scale rendered in all six scripts, and show the Score screen and one Report card in **Hindi and Tamil** as well as English. A system that has only been seen in English has not been tested.
- Numerals: decide and document whether to use Latin or Devanagari numerals per locale. The score is a number and appears everywhere.

### 6.3 Scale

Define: display, h1, h2, h3, body-lg, body, body-sm, label, caption, mono/numeric. Each with family, size, weight, line-height, letter-spacing — per script where they diverge.

Note the app is used by a wide age range, much of it middle-aged and older, often outdoors or on a bright screen. **Bias larger.** The prototype's 13.5px body and 11.5px small text are too small for the actual audience; treat those as a floor to move up from, not a target.

Text must remain usable at 200% system font scaling. Specify what reflows.

---

## 7. Cross-platform — Android now, iOS later

The app is built in **Compose Multiplatform**: the same UI code renders on both platforms. Design decisions made now for Android are inherited by iOS. Plan for it from the first token.

### 7.1 Design an owned language, not a Material skin

Do not build on Material 3 defaults. A Material-flavoured app looks correct on Android and subtly wrong on iOS — the shadows, the component shapes, the motion curves and the navigation idioms all read as foreign. VastuFirst should have its **own** visual identity that looks intentional on both.

### 7.2 Specify platform divergence explicitly

Some things must differ. For each, state the Android treatment and the iOS treatment now, so the iOS build is a re-skin rather than a redesign:

| Concern | What to specify |
|---|---|
| Navigation | Android back gesture and system back button; iOS swipe-back and nav-bar back |
| Safe areas | Android status/nav insets; iOS notch, Dynamic Island, home indicator |
| Elevation | Android uses shadow; iOS convention favours borders and separators — provide **both** treatments per surface token |
| Typography | Android default vs iOS default metrics; state whether the custom face is bundled on both (it should be) |
| Controls | Switch, slider, picker, date field — give an owned design so neither platform's native control is needed |
| Haptics | Define where feedback fires — especially on the North dial |
| Share / export | Android share sheet vs iOS share sheet |
| Density | Android dp vs iOS pt, and how the spacing scale maps |

### 7.3 Deliverable

Screens in Android form now. Plus **a written iOS adaptation note per screen** — what changes, what stays. Not full iOS mockups yet; enough that the later iOS pass is mechanical.

---

## 8. Accessibility and platform fit — stated as hard constraints

These are **constraints, not suggestions**. The §4.3 review gate checks against them, so they must be stated as numbers the implementer can test.

| Constraint | Value | Applies to |
|---|---|---|
| Minimum touch target | **48 × 48 dp** | every interactive element, both platforms |
| Minimum text size | **12 sp** absolute floor; **16 sp** for body copy | all text |
| Contrast — body text | **4.5:1** | every text token on every surface token it may sit on |
| Contrast — large text (≥18sp or ≥14sp bold) and meaningful UI boundaries | **3:1** | headings, icons carrying meaning, borders that convey state |
| Text scaling | usable to **200%** | every screen; specify reflow for dense ones |
| Colour independence | **no information by colour alone** | zones, verdicts, score bands, provenance |

**8.1 Colour is never the only signal.** Roughly 1 in 12 men has some form of colour vision deficiency, and this is a mass-market Indian consumer app carrying nine zone colours, five verdict states and three score bands — often on one screen. Every zone and every verdict must also be distinguishable by **label, icon, pattern or position**. Deliver a **colourblind simulation** of the zone map and the report screen under deuteranopia and protanopia. If zones become ambiguous, the palette changes — the palette loses that argument, not the accessibility rule.

**8.2 Contrast — deliver the audit, do not assert it.** Provide the full pair-by-pair table (§3.2.5). The warm, low-contrast paper palettes this brief encourages are exactly where contrast quietly fails. Any pair below its minimum is a defect in the theme, not a note for later.

**8.3 Touch targets.** 48 × 48 dp minimum, including when the visible element is smaller. The North dial must be comfortably draggable with a thumb, including for users with limited fine motor control.

**8.4 Screen readers.** Provide content-description guidance for the zone map, the score and the dial. *"Floor plan with Vastu zones, North at 45 degrees, score 31 of 100"* is the standard — the visual map must have a meaningful non-visual equivalent, not a label saying "image".

**8.5 Platform differences the implementer must honour.** State each as a rule, not an observation — see §7.2 for the full list. At minimum: elevation renders as shadow on Android and as border/separator on iOS; back navigation differs; safe-area insets differ; the type ramp must specify whether the bundled face is used on both platforms (it should be).

## 9. The existing visual language

A working HTML prototype exists and the client approved its earlier look. The chosen theme — **Sage & Gold** — kept that paper-and-ink calm and the serif/sans/mono split, and replaced the brand accents (sage-green primary + gold). The resolved brand / foundation tokens are below; the handoff (`VastuTheme.kt` / `tokens.json`) is authoritative for the full set, including the nine zone colours.

```
Surface     paper #F8F6F0   surface #F2EEE4   raised #FFFFFF
Ink         text #232A22    text-soft #4B5347    text-faint #6B7064
Border      default #DDDED3   strong #B6BBA8   focus #4C7355
Brand       primary/sage #7A9E7E   primary-dark #5F8465   secondary/gold #C9A227
Support     ideal #3E9256   acceptable #8FBE95   suboptimal #D68C18   defect #C43F35

Zones       N  #3E8E7E    NE #2E9CA6    E  #F0A93B    SE #E2582F
            S  #B9453E    SW #8A6A45    W  #4A6FA5    NW #7BA88F
            centre #B99BC9

Radius      14px cards · 100px pills
Type        Marcellus (display serif) · DM Sans (body) · DM Mono (labels, numerals)
Buttons     pill, min-height 46px
Eyebrow     10px mono, uppercase, 0.14em tracking
```

**What works and should probably survive:** the warm paper ground, the paper-and-ink calm, the serif/sans/mono three-voice split, pill buttons, the restrained use of the primary accent.

**What needs to change regardless of theme:** the type sizes are too small for the audience (§6.3); the typefaces do not support five of six languages (§6.1); provenance tags do not exist yet and must be designed (§5.3); the "not assessed" state does not exist yet (§5.4).

**Second palette, for reference:** the client-facing PDFs use a related but distinct set — teal `#137A70`, orange `#E0872E`, green `#4E9350`, gold `#C1922E`, red `#BC4B45`, cream `#FBF7EF`. The app and the documents should feel like one brand. Note in your recommendation whether the chosen theme requires updating the document palette to match.

---

## 10. Deliverables checklist

### Stage 1
- [ ] Four named themes, each with rationale, full palette with semantic token names, and hex values
- [ ] Nine zone colours shown on an actual zone map, per theme
- [ ] Score screen + one report card rendered in each theme, same content throughout
- [ ] Contrast audit table per theme
- [ ] Colourblind simulation of the zone map per theme
- [ ] Typography proposal that covers all six scripts (§6)
- [ ] Honest statement of what each theme wins, risks, and is weakest at

### Stage 2 (after selection)
- [ ] **Populated Compose theme** — `VastuColors`/`VastuSpacing`/`VastuTypography`/`VastuShapes`/`VastuElevation` with every real value filled in, compiling as-is, in `commonMain` (§4.1.2). **This is the primary deliverable.**
- [ ] Human-readable token reference table (§4.1.1)
- [ ] `tokens.json` mirroring the same names (§4.1.2)
- [ ] Component library with **every state pinned to tokens**, in the table shape given (§4.2)
- [ ] **Screen review gate checklist** — the pass/fail list Claude Code runs before any screen is done (§4.3)
- [ ] Icon set including the Vastu-specific icons (§4.4)
- [ ] All 13 screens at 412 × 915 dp, plus 360 dp variants of the dense ones
- [ ] Report screen in at least two intent states (§5.5)
- [ ] Score screen and one report card in Hindi and Tamil (§6.2)
- [ ] Redlines: spacing, sizing, alignment on every screen
- [ ] Motion specification: durations, easing, and the North-dial interaction
- [ ] iOS adaptation note per screen (§7.3)
- [ ] Accessibility annotations: content descriptions, focus order, touch targets
- [ ] Contrast audit table and colourblind simulation (§8)

---

## 11. Explicit non-goals

- **No dark mode in this pass.** It may come later; do not let it constrain the light palette now.
- **No marketing site or app-store screenshots.** App UI only.
- **No illustration system** unless a screen genuinely needs one. Prefer restraint.
- **No animation beyond the specified interactions.** The North dial and score transition are the two that matter.
- **No onboarding carousel.** The user reaches value in one screen. Do not add tutorial screens between them and the score.

---

## 12. How this gets used

The chosen theme and the stage 2 system are handed to an engineer building in Kotlin and Compose Multiplatform, who will implement them literally. Anything ambiguous becomes a guess, and guesses become inconsistencies across 13 screens and 6 languages.

So: **name every token, specify every state, give every measurement.** A beautiful mockup with no redlines is not a deliverable here. The test is whether an engineer who has never spoken to you can build the screen and get it right.
