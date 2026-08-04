# UI-POLISH.md — the hard rule for every VastuFirst build

**Status: HARD RULE. Binding on every build, every phase, every screen, forever.**
Referenced from `CLAUDE.md §2b`. If this document and any other instruction disagree about UI, this
document wins — except that `CLAUDE.md`'s cloud-build-only rule always wins over everything.

---

## 0. Why this exists (plain English)

The owner installed v0.2.1 and said: *"I see many UI issues in this version but I don't have time to
tell you everything."*

That sentence is the whole problem. The owner is not the QA department. Up to v0.2.1 the only thing
standing between a broken screen and the client's phone was **someone noticing**. Nobody was looking,
because nothing in the build could look — CI compiled the app, ran the maths tests, and shipped the
APK **without ever rendering a single screen**.

So the fix is not "be more careful." The fix is: **the build must be able to see.**

This document is the standard, and it is enforced by scripts that fail the build, not by good
intentions.

**The one-line rule:**
> Never hand over an APK for a screen I have not seen rendered, measured against the design, and
> checked at 360 dp and 200 % font scale.

---

## 1. The three failures this prevents

Every UI defect found in the v0.2.1 audit falls into one of three buckets. Each bucket gets its own
mechanical gate. **A rule with no gate is a wish, not a rule.**

| # | Failure | Example from v0.2.1 | Gate that catches it |
|---|---|---|---|
| **F1** | **The code drifted from the design** | `border-hairline` is 1 dp in the design, `0.6.dp` in the code. The design's letter-spacing was never implemented. | `scripts/check-design-fidelity.mjs` — §4 |
| **F2** | **The code is right but the layout breaks on a real phone** | The guided grid measures to **zero height**, so no room can be placed at all. Status bar overlaps every screen. Report rows shatter at 360 dp. | Rendered screenshots + layout assertions — §5, §6 |
| **F3** | **Nobody looked** | All of the above shipped as "CI green". | The pre-APK ritual — §7 |

---

## 2. The design contract — what "adheres to the design 100 %" actually means

"Matches the design" is not an opinion. For this project it is a **file**:

| Contract | File | Owns |
|---|---|---|
| Tokens | `Design System/…/project/handoff/tokens.json` | 39 colours, 9 spacings, 4 radii, 4 elevations, 4 border widths |
| Type ramp | `const ramp` inside `VastuFirst - Sage & Gold Design System.dc.html` | family, size, weight, line-height **and letter-spacing** per style |
| Contrast floors | `const auditRows` in the same file | the exact pair-by-pair contrast the design signed off |
| Screen reference | 14 phone frames at 412 × 915 in the same file | what each screen is supposed to look like |
| Review gate | `const gate` in the same file | the six-category checklist, reproduced in §8 |

**⭐ The bundle is git-ignored, so the contract is frozen into the repo.**
`scripts/extract-design-contract.mjs` reads the bundle and writes **`design/design-contract.json`**
(~5 KB: tokens + ramp + contrast audit), which *is* committed. CI reads that file — otherwise the
gate could not run at all in the cloud, which is the only place we build.

- Re-run the extractor whenever the design bundle changes, and commit the result. The diff on that
  file is a readable record of every design change.
- When the bundle *is* present (on the authoring machine), the checker re-derives the contract and
  **fails if the committed copy has gone stale** — so the frozen copy cannot silently drift from
  the design it claims to represent.

**Rules:**

1. **The theme is generated from the contract, never typed from memory.** If a value in
   `VastuTheme.kt` disagrees with `tokens.json`, the theme is wrong — not the design.
2. **A deliberate deviation must be declared**, in a comment naming the reason, and added to the
   allow-list in `check-design-fidelity.mjs`. Two exist today and are legitimate:
   - `label` is weight 500 not 600 — the bundled DM Sans has no 600 cut. Never faux-bold.
   - `radius-full` is `RoundedCornerShape(percent = 50)` not `999.dp` — equivalent for a pill.
3. **Three accent colours sit below 3:1 on paper and the design knows it** — `secondary` (gold, 2.24:1),
   `score-workable` (amber, 2.55:1), `zone-e` (2.08:1). The design marks them "accent". Therefore:
   **these three may never be the only thing carrying a meaning.** A word, glyph or position must
   always accompany them. This is not a bug to fix in the palette; it is a constraint on how the
   palette is used.

---

## 3. The defect catalogue — check every one of these on every screen

Ordered by how badly it hurts. Each has the mechanical detection in brackets.

### A. Window insets — the #1 "app looks broken" defect on modern Android
`targetSdk = 35` means Android 15 **enforces edge-to-edge with no opt-out**. Content goes under the
status bar and under the gesture-nav pill unless you handle it.

- [ ] `MainActivity.onCreate` calls `enableEdgeToEdge()`.
- [ ] Every screen root applies `Modifier.safeDrawingPadding()` (or `systemBarsPadding()` where the
      background is meant to bleed but content is not).
- [ ] Status-bar icon colour is set explicitly (`isAppearanceLightStatusBars`) — the app has a light
      cream background, so icons must be dark **even when the phone is in dark mode**.
- [ ] Any bottom CTA is above the nav bar, not under it.
*(grep gate: every screen-root file must match `safeDrawingPadding|systemBarsPadding|Scaffold`)*

### B. Scroll containers — content the user cannot reach
- [ ] Every screen whose content can exceed the viewport has `verticalScroll(rememberScrollState())`
      or is a `LazyColumn`. **Test at font scale 2.0, not 1.0** — a screen that fits at 1.0 and has no
      scroll container is a screen that traps the user at 1.5.
- [ ] No `LazyColumn` nested inside a `verticalScroll`.
- [ ] Scroll state is hoisted above any `when`/`if` that can tear it down. *(v0.2.1 bug: the room
      palette's horizontal scroll reset to the far left every time a room was placed.)*
*(gate: layout assertion — bottom-most CTA must be reachable at 360 dp × font scale 2.0)*

### C. Measurement — does the thing actually have a size?
- [ ] **`Modifier.offset` does NOT contribute to the parent's measured size.** A `Box`/`BoxWithConstraints`
      whose only children are offset-positioned measures to the height of its tallest child — and to
      **zero** when it has no children. *(This is the v0.2.1 guided-grid bug: an empty grid measured
      0 px tall, so it was invisible and untappable, and the primary flow did not work.)*
      Any container drawn with `drawBehind` **must** be given an explicit size — `aspectRatio(1f)`,
      `height(...)` or `fillMaxHeight()`.
- [ ] `.clip()` on a container clips children — check nothing is silently erased.
- [ ] Custom drawing honours the actual size; no coordinate baked to one device's proportions.

### D. Rows that shatter — the most common "looks unprofessional"
- [ ] In any `Row`, **at most one child may be unweighted-and-unbounded**. Two flanking pills either
      side of a `weight(1f)` text will squeeze the text to nothing and it will wrap one character per
      line. *(v0.2.1: the "Already right" rows and the defect cards.)*
- [ ] Chips/buttons that can overflow use `FlowRow`, not `Row`.
- [ ] Any text that can be long has `maxLines` **and** `overflow = TextOverflow.Ellipsis`.
- [ ] Compute the worst case: longest label × largest font scale × 360 dp width.

### E. Text and font scale
- [ ] No fixed `height()`/`size()` on a container holding text — use `heightIn(min = …)`.
      A 20 dp box around a "✓" clips at font scale 1.3.
- [ ] Body ≥ 16 sp, nothing below 12 sp.
- [ ] Renders in Hindi and Tamil without clipping; Indic line-height stays 1.5–1.6.
- [ ] No fixed-height container holds translatable text.

### F. Interaction and state
- [ ] **Every** interactive element has a visible pressed state. *(v0.2.1: `clickableTap` hardcodes
      `indication = null` app-wide, so ten controls were completely dead to the touch. If the design
      system suppresses the Material ripple, it owes every control an owned pressed state.)*
- [ ] Every interactive element has a disabled state where it can be disabled — and the disabled
      state must change more than the title colour.
- [ ] Loading, empty and error states exist wherever reachable. A loading state that cannot resolve
      is a trap — always give it a timeout or an exit.
- [ ] **No dead controls.** A control that does nothing when tapped is worse than an absent one. If
      it is not wired this phase, it must say "soon" on the control itself.
- [ ] Navigation is debounced — a double-tap must not push two destinations.
- [ ] Every screen has a visible way out.

### G. Accessibility
- [ ] Touch targets ≥ 48 × 48 dp.
- [ ] Every icon-only button has a `contentDescription` and a `Role`.
- [ ] Custom controls (dial, slider) expose semantics **actions**, not just labels — a
      `progressBarRangeInfo` with no `setProgress` action is a read-only announcement, which means a
      screen-reader user cannot set North at all.
- [ ] No information by colour alone — always a word, glyph or position too. See §2 rule 3.

### H. Performance you can see
- [ ] Models fed to a `Canvas` are `remember`ed — rebuilding a model every recomposition makes
      skipping impossible and makes a drag stutter.
- [ ] No `TextMeasurer.measure()` inside a draw scope for many strings; raise the cache size or
      pre-measure.

### I. The app shell — checked once, but fatal if missed
- [ ] A real launcher icon exists (`android:icon` + adaptive icon). *(v0.2.1 shipped with the default
      Android robot: there is no `res/` directory in the app module at all.)*
- [ ] `strings.xml` exists; `android:label` is not a hardcoded literal in a six-language app.
- [ ] A themed splash background matching `paper`, so there is no white flash on launch.

---

## 4. Gate 1 — design fidelity (`scripts/check-design-fidelity.mjs`)

Runs in CI. **Fails the build on drift.** Compares the implementation against the design contract:

1. Every colour in `tokens.json` exists in `VastuColors` with the exact hex.
2. `VastuSpacing` / `VastuShapes` / `VastuElevation` / `VastuBorders` match the token scales.
3. The type ramp matches size, line-height, weight and letter-spacing.
4. Every contrast pair in the design's own audit still meets its floor.

Declared deviations live in an allow-list at the top of the script with a stated reason. Adding to
that list is a deliberate act, visible in the diff — which is the point.

## 5. Gate 2 — the token guard, widened (`scripts/check-tokens.sh`)

The existing guard greps for `Color(0x`, `N.dp`, `N.sp` outside the theme package. It passes today
and it **still missed the 1-pixel grid line**, because a raw `Float` inside a `DrawScope` is invisible
to it. It must additionally catch:

- raw numeric literals used as sizes/strokes/offsets inside `Canvas` / `drawBehind` / `drawWithCache`
- `Color.Black` / `Color.White` / named `Color.*` constants
- `RoundedCornerShape(` outside the theme package
- raw `alpha = 0.NNf` literals (these need alpha tokens)
- **and the shell bug**: `for f in $files` word-splits on spaces; use `find -print0` / `while read -r`.

**Rule for `DrawScope`:** a stroke width is `VastuTheme.borders.regular.toPx()`, never `1f`. Inside a
draw scope `1f` is **one physical pixel** — a third of a dp on a 3× phone, i.e. invisible.

## 6. Gate 3 — the build must render every screen

This is the gate that would have caught the zero-height grid, the inset overlap and the shattered
rows. CI renders every screen headlessly (no emulator) and both **asserts** on the result and
**publishes the images**.

### 6.0 The layer model — and the one rule that keeps it honest

> **Never gate on a pixel comparison between the design and the build.**

The design reference is rendered by Chrome; the build is rendered by Android's Skia text pipeline.
Two different rasterisers, two different font-hinting policies, two different shadow algorithms. A
pixel diff between them typically shows 5–25 % of pixels differing while being *visually identical*,
and no threshold separates "wrong" from "different renderer". Pixel diffing is a **regression** tool
(same engine, two points in time), never a **conformance** tool (two engines, one spec).

So the gate is layered, and only the engine-independent layers can fail the build:

| Layer | Compares | Engine-independent | Gate |
|---|---|---|---|
| **L0 · tokens** | `tokens.json` + ramp vs the theme | yes | **HARD FAIL** — `check-design-fidelity.mjs` |
| **L1 · measurements** | element geometry from the design DOM vs from the Compose semantics tree | yes | **HARD FAIL** on structure; warn on small deltas |
| **L2 · images** | design PNG vs build PNG | **no** | **REPORT ONLY — never gates** |
| **L3 · harness** | side-by-side / overlay / difference for a human | n/a | never gates |

**L1 is the important one**, because it is what catches a whole button being 6 dp out of place —
something a pixel number cannot distinguish from an anti-aliasing halo. The matching problem is
solved by annotation, not computer vision: a `data-fid="cta.primary"` on the design element and a
matching `Modifier.testTag("cta.primary")` in Compose reduces element-matching to a dictionary
lookup. **Annotate as you build; retrofitting is the expensive path.**

### 6.1 Tooling decision (locked)

**Roborazzi on Robolectric.** It runs as a **JVM unit test on a plain ubuntu runner with no
emulator**, which is the only thing compatible with our cloud-only rule. The other two candidates
are not inconvenient, they are **blocked**:

| Candidate | Verdict for *this* project |
|---|---|
| Google's `com.android.compose.screenshot` | **Blocked twice.** Its own docs say *"Kotlin Multiplatform: Not supported; Android projects only"* — and `:designsystem` is a Compose Multiplatform module. It is also version-locked to the AGP dev cycle, so on AGP 8.7.3 we'd be stuck on `0.0.1-alpha06` (Aug 2024). |
| Paparazzi | **Blocked.** Stable 1.3.5 renders **API 34 only** and we are on compileSdk/targetSdk **35**. The 2.0 line that can is alpha and needs Java 21. |
| **Roborazzi** | **Fits.** First-class Compose Multiplatform support with a working KMP sample; used by Google's own Now in Android. |

**Pinned versions — do not float these:**

- **Roborazzi `1.60.0`.** Two independent ceilings, and 1.60.0 is the only version under **both**:
  1. **Gradle** — 1.62.0+ are published by Gradle 9.x; we are on the **Gradle 8.9** wrapper AGP 8.7.3
     requires.
  2. **Kotlin metadata** — `1.61.0` was compiled with **Kotlin 2.3.0**, whose metadata (binary
     version 2.3.0) our **Kotlin 2.0.21** compiler cannot read: it fails
     `compileDebugUnitTestKotlin` with *"incompatible version of Kotlin … 2.3.0, expected 2.0.0"*.
     **`1.60.0` is the last release built with Kotlin 2.0.21** (verified against Roborazzi's own
     `gradle/libs.versions.toml` per tag). *(The original audit pinned 1.61.0 for the Gradle ceiling
     alone and missed this one — corrected here after CI proved it, 2026-07-24.)*
- **Robolectric `4.14.1`** — the first release that can render **SDK 35**.
- `testOptions.unitTests.isIncludeAndroidResources = true` is required, and
  `src/test/resources/robolectric.properties` must say `sdk = 35`.

⚠ **Consequence of that pin, stated honestly:** Roborazzi's `dumpUiTree` (the feature that emits the
L1 measurement manifest for free) arrived in **1.69.0** and is therefore **not available to us**. So
the build-side manifest is produced instead by a small hand-written test that walks the Compose
semantics tree and writes the same JSON — `boundsInRoot`, `testTag`, role, text — roughly 40 lines.
Revisit if we ever move off Gradle 8.9. Do not bump Gradle before the 4 August delivery to chase it.

Design-side references come from headless Chrome rendering the 14 phone frames already present in
the Sage & Gold design file — this is proven working and needs no npm dependency.

### 6.3 Bootstrapping goldens when we cannot build locally

We have no reference images and no way to make them on this machine. The answer is Google's own
pattern from Now in Android: **let CI be the only machine that ever renders.**

1. CI runs `verifyRoborazziDebug` with `continue-on-error: true`.
2. If it fails (first run: there are no goldens), CI runs `recordRoborazziDebug` and commits the
   PNGs back to the branch.
3. Every run after, verify passes silently. When we deliberately change a screen, verify fails, CI
   re-records, and **the image diff in that commit *is* the visual change** — which I review.

This also closes the biggest source of false failures for free: font anti-aliasing differs between
machines, but since every golden and every check comes from the identical `ubuntu-latest` image,
that drift cannot occur. Pin the runner image and JDK anyway, and keep a small non-zero threshold.

Treat any Robolectric / compileSdk / Compose bump as a deliberate re-baseline in its own commit —
never let it ride along with a feature change.

Design-side references come from headless Chrome rendering the 14 phone frames already present in
the Sage & Gold design file — this is proven working and needs no npm dependency.

### 6.2 L1 tolerances

| Property | Tolerance | Why |
|---|---|---|
| Alignment of shared edges | **±2 dp → fail** | the most visible defect class, and fully engine-independent |
| Gap between siblings | **±3 dp → fail** | gaps are pure spacing-token consumption; a wrong token shows as a clean 4/8/12 dp delta |
| Size of a **fixed** box (button, icon, tile) | **±2 dp → fail** | a declared constant on both sides |
| Position | ±4 dp → fail | ~half a spacing step; rounding never trips it |
| Size of a **text** box | ±6 dp → **warn only** | text height is a font-metric *result*, not a design decision |
| Line height | warn only | same reason |
| Element missing from the build | **fail** | non-negotiable |
| Element extra in the build | warn | mockups are rarely exhaustive |
| Elevation / shadow | **report only** | CSS `box-shadow` and `Modifier.shadow()` are different algorithms and will never match — verify the elevation *token* at L0 instead |

Compare **relations (gaps, alignments), not absolute Y**. Absolute positions accumulate every
upstream rounding difference, so one 1 dp discrepancy in a header would "fail" all forty elements
below it.

### 6.3 Adoption without a red build forever

Gate on a **ratchet**, not a cliff: commit the current per-screen finding count to
`fidelity-baseline.json` and fail only when a count *increases*. That lets the gate go in
immediately on a codebase that does not yet pass, and makes fidelity improve monotonically.

**And fail loudly when the harness itself breaks** — zero screens rendered, an empty manifest or a
0-byte screenshot must fail, not silently report "no findings". A gate that passes when it did not
run is worse than no gate.

### 6.4 The standard configuration matrix — every screen, every build

| # | Config | Catches |
|---|---|---|
| 1 | 412 × 915 dp, light, scale 1.0, `en` | the baseline / design reference size |
| 2 | as 1, **dark** | dark-mode breakage |
| 3 | as 1, **font scale 1.3** | early clipping |
| 4 | as 1, **font scale 2.0** | fixed heights clipping, screens outgrowing the viewport |
| 5 | **360 dp width** | the most common Indian phone; where rows shatter |
| 6 | **320 dp width** | a 360 dp phone set to "Display size = Largest" behaves like this |
| 7 | landscape (height 480 dp) | unreachable CTAs |
| 8 | pseudolocale **`en-XA`** | ~2× text expansion — finds overflow without needing translations |
| 9 | pseudolocale **`ar-XB`** | RTL mirroring |
| 10 | `hi-rIN` + `ta-rIN` | Devanagari / Tamil clipping |

Pseudolocales are free translation-stress testing: enable with
`buildTypes.debug.pseudoLocalesEnabled = true`. `en-XA` roughly doubles every string, which is the
cheapest possible way to find every row that will shatter.

Two configs whose quiet behaviour is DELIBERATE (documented per the 4 Aug 2026 audit, D3/D4 — do
not re-flag them):

- **dark is a proof of non-inversion, not a dark mode.** The app ships one light palette; the dark
  config exists to prove system dark mode does not invert it. Byte-identical files to the light
  goldens are the PASS state.
- **pseudo_en / hi / ta are armed but inert for text** until strings move to resources (Phase 4):
  every user-facing string is a Kotlin literal today, and a locale can only translate resource
  strings. `rtl` still earns its place now — it mirrors layout regardless of strings (and the grid
  and compass correctly do NOT mirror).
- **landscape renders real landscape only since 4 Aug 2026.** The config's qualifier string lacked
  the `-land` token, so Robolectric normalised it to a 480 dp-wide portrait — every earlier
  "landscape" golden was actually a narrow portrait phone (audit D1).
- **Long documents also get BOTTOM-HALF goldens (audit D2, added 4 Aug 2026 late).** A golden is a
  viewport, not a document: every matrix capture starts at the top, so the report's disputes payoff,
  the score's ranked problems and the unlock feature list had never been photographed at any config.
  `LongScreenBottomScreenshotTest` renders report / report-living / score / unlock-paid in the
  baseline window, scrolls to the screen's true last element, and keeps that picture as
  `<screen>__bottom.png` and `<screen>__bottom_font2_0.png`. The scroll anchor is the screen's
  bottom-most string — if a screen's ending changes, the test fails loudly instead of quietly
  photographing the wrong place. These are eye-pass goldens only; they emit no L1 manifest.

### 6.5 Assertions on the rendered tree — what a picture cannot show

Two facts that make naive assertions useless, and the correct form of each:

- **`assertIsDisplayed()` passes on a partially clipped node.** It only requires that *some* of the
  bounds survive clipping. To detect clipping, compare
  `getUnclippedBoundsInRoot()` against `getBoundsInRoot()` — if they differ, something is cut off.
- **`assertTextEquals()` cannot detect visual truncation.** The semantics tree carries the *input*
  string, not the laid-out one — a screen showing "Mas…" still reports the full text. Truncation is
  only visible through `SemanticsActions.GetTextLayoutResult` →
  `TextLayoutResult.isLineEllipsized(line)`.

The assertions to run on every screen, at every config above:

- no node has zero width or height *(this alone would have caught the zero-height grid)*
- unclipped bounds == clipped bounds for every text node *(catches the shattered report rows)*
- no text node is ellipsized where the content is the only copy of that information
- the bottom-most CTA is inside the viewport, or its screen scrolls to reach it
- every clickable node is ≥ 48 × 48 dp
- every clickable node has a content description

### 6.6 Accessibility checks run inside the same pass — free

Google's Accessibility Test Framework can run **inside** the JVM screenshot run via Roborazzi's
`roborazzi-accessibility-check` module. That turns contrast, touch-target size, missing labels,
duplicate labels and traversal order from "needs a device" into "fails CI" — no emulator. Turn it on.

### 6.7 ⚠ What this gate CANNOT catch — be honest about it

**Window insets are invisible to JVM screenshot tests.** Robolectric and LayoutLib report
effectively zero insets, so a screen with the status bar overlapping its title renders *perfectly*
in CI. The single worst defect in v0.2.1 is the one the screenshot gate would have missed.

So insets get their own, different gate:

- **a grep gate** — every screen-root file must apply a `safeDrawing`/`systemBars` inset modifier,
  and `MainActivity` must call `enableEdgeToEdge()`; CI fails if a screen root has none;
- **and an on-device check** before any client hand-over.

Also invisible to screenshots, and therefore requiring code review or a device: the IME covering a
field, gesture and nested-scroll conflicts, flicker and layout shift as data lands, state loss on
rotation or process death, double-tap duplicate navigation, and real TalkBack behaviour.

### ⚠ 6.7b The gate measures BOXES, not GLYPHS — text can overflow and nothing fires

**Found on 1 August 2026, by looking at a picture.** The report's reading toggle rendered
"Iraditional 9-zone" at 200 % font: the word "Traditional" was wider than its half of the row, so it
was drawn centred and overflowing, losing characters off **both** ends.

**The geometry gate reported nothing at all.** It reads each node's clipped and unclipped bounds — and
for that label both were the segment box's own 178 dp. The box is not clipped; the *ink* is. `ellipsized`
was false too, because the text was never constrained into ellipsising, just drawn wider than its
container. Every number the gate has is correct and the screen is still wrong.

Two consequences, both binding:

- **A fixed-width control containing text must keep every WORD short** — nothing that cannot fit the
  control's narrowest configuration on its own line. A long word cannot wrap; it bleeds.
- **This class is caught only by a human looking at the rendered image**, which is precisely why
  §2b's "never hand over a screen I have not looked at" is a hard rule and not a nicety. It survived
  a whole release, noted as a known blemish, because every gate was green.

**The screenshots are uploaded as a CI artifact on every run.** Producing them is not optional and
neither is looking at them.

## 6b. Gate 3b — lint, because Android Lint is blind to Compose

Nearly every classic UI-quality lint check — `ContentDescription`, `EllipsizeMaxLines`, `SmallSp`,
`HardcodedText`, `RtlHardcoded`, `RequiredSize` — is a **View/XML detector**. It fires on
`res/layout/*.xml` and does **nothing** on a file full of `Text(...)`. Assuming "lint passes" means
"the UI is fine" is a mistake this project has already paid for.

So lint has to be assembled deliberately:

1. **Turn on Android Lint at all** (CI runs none today). It would have caught the missing launcher
   icon immediately. Set `warningsAsErrors` + `abortOnError`.
2. **Promote the AndroidX Compose checks to errors** — especially
   `UnusedMaterial3ScaffoldPaddingParameter` (content ignoring `Scaffold` padding),
   `UnrememberedMutableState`, `MutableCollectionMutableState`, `UnusedBoxWithConstraintsScope`,
   `ReturnFromAwaitPointerEventScope` and `MultipleAwaitPointerEventScopes` (both cause *dropped
   touch input*), and `MissingTranslation`.
3. **Add `com.slack.lint.compose:compose-lint-checks`** — Gradle-native, 21 checks, catches
   `ComposeModifierMissing` / `ComposeModifierReused` / `ComposeMultipleContentEmitters`.
4. **Write the project-specific greps** that nothing off-the-shelf covers:
   `maxLines` without `overflow` · fixed `height()` around text · `indication = null` ·
   `Color.*` constants · raw `.dp` in a `DrawScope` · `fontSize` in `dp` ·
   `navigate(` in an unguarded `onClick` · non-`AutoMirrored` directional icons.

## 7. Gate 4 — the pre-APK ritual (this is the one that was missing)

**Before I give the owner any APK link, I must do all five, in order, and say so:**

1. **Green CI** — including the three gates above.
2. **Download the rendered screenshots** from the CI artifact and **actually look at every screen.**
3. **Compare against the design side by side** — `tools/ui-review/index.html` puts the design
   reference and the current build next to each other, with an overlay slider. Measure differences;
   do not eyeball a verdict.
4. **Walk the §8 review gate** for every screen I touched.
5. **Report honestly** — say which screens I verified and which I did not. "CI is green" is **not**
   the same as "the screen is right", and I must never present it as if it were.

If I cannot see a screen, I say so rather than implying it was checked.

---

## 8. The screen review gate (from the design system — unchanged, now enforced)

```
TOKENS
[ ] Every colour resolves to a VastuTheme token. Zero hardcoded hex in this file.
[ ] Every spacing value comes from VastuTheme.spacing. No raw .dp on padding/gap.
[ ] Every text style comes from VastuTheme.type. No ad-hoc fontSize/fontWeight.
[ ] Every radius from VastuTheme.shapes; every border width and colour from a border token.

COMPONENTS
[ ] Every interactive component renders its correct pressed state.
[ ] Every interactive component renders its correct disabled state.
[ ] Loading / empty / error states exist where the screen can reach them.
[ ] Variants come from the component API, not inline restyling.

LAYOUT
[ ] Matches the reference mockup at 412×915 dp.
[ ] Checked at 360 dp width — no clipping or overlap.

ACCESSIBILITY
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

---

## 9. Rules for writing new UI (prevention, not detection)

1. **Build from the design file, not from memory.** Open the 412 × 915 reference for that screen
   first. `tools/ui-review/design/` holds a rendered PNG of each.
2. **Give every drawn container an explicit size.** Never rely on children to size a canvas.
3. **One unbounded child per Row.** Everything else gets `weight`, `maxLines` and ellipsis.
4. **`heightIn(min=)`, never `height()`,** around anything containing text.
5. **Every new interactive component ships pressed + disabled on day one**, or it is not done.
6. **Insets are applied at the screen root**, once, by the same helper everywhere.
7. **If it is not wired, it says "soon" on the control** — never a silent no-op.
8. **New raw literal? Add a token instead.** If a value deserves to exist, it deserves a name.

---

## 10. Change log

| Date | Change |
|---|---|
| 2026-07-24 | Created after the owner reported "many UI issues" in v0.2.1. Written against the verified audit in `docs/UI-AUDIT-2026-07-24.md`. |
