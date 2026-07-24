# Rectangular / true-to-life plots — research + recommendation

**Status: recommendation, awaiting owner sign-off. No code yet.**
Owner feedback (2026-07-24): the grid is a fixed square, but most homes/plots are rectangular. Drawing
true-to-life leaves empty space on one side and gives a *different* score than filling the whole
square — confusing. Owner asked to let users set true dimensions "without impacting score accuracy."

---

## The short version (plain English)

- **The scoring engine is already built the right way for rectangular homes.** It does *not* assume a
  square. It takes the actual outline the user drew, wraps the tightest rectangle around it, and
  divides *that* rectangle into 9 zones (three slices each way) — so the zones stretch to fit a
  rectangular home, which is exactly what traditional Vastu does. A room's zone is decided by *where
  it sits inside the home's own rectangle*, not by the grid on screen.
- **So the score problem is not in the engine — it's the square drawing pad.** Because the drawing
  grid is a fixed 8×8 square, the user is forced to either (a) stretch rooms to fill the square
  (wrong proportions) or (b) draw true-to-life and leave a strip empty. Those are two *different
  drawings*, with rooms in different relative positions, so they legitimately score differently. That
  is the confusion the owner hit — and it is real, not cosmetic.
- **The fix: let the drawing pad match the plot's true shape.** If the user tells us the plot is, say,
  wider than it is deep, we show a grid of that shape. Then they draw once, true-to-life, with no
  empty strip and no distortion — and because the engine already divides the *drawn* rectangle
  proportionally, the score is both correct and stable. Zones come out rectangular, which is the
  traditionally-correct result for a rectangular plot.

**Bottom line: this is very doable, needs no change to the scoring engine, and *improves* accuracy
(the drawing finally matches reality). It's an editor/UX change.**

---

## What the research settled (sources at the bottom)

1. **Is the Vastu mandala a fixed square, or stretched to the plot?** The mandala is the *ideal*
   square to aspire to, but for a real (usually rectangular) building it is **stretched onto the
   footprint**: "When a rectangular site is used, the nine spaces created by dividing the mandala will
   be rectangular in shape rather than perfect squares." This is mainstream and classically grounded
   (*pada vinyasa* divides a site of any shape into modules).
2. **Rectangular plot → 3×3 rectangular zones, cardinal-aligned, centre = Brahmasthan.** Divide length
   and breadth into thirds over the plot's bounding rectangle; the centre ninth is the Brahmasthan.
   **This is exactly what `engine/PadaGrid.kt` already does** (`padaW = rect.width / gridSize`,
   `padaH = rect.height / gridSize`, zones = three bands each way, centre 3×3 = Brahmasthan).
3. **Elongated plots.** Ideal ratio is 1:1 to 1:2 (breadth:length); beyond ~1:2 is discouraged, longer
   axis ideally north–south. Our engine already carries an elongation flag (X-15) and degrades to a
   caution rather than erroring — consistent with the research.
4. **How other apps take input.** The grid-overlay apps ask for **dimensions + compass orientation**
   and overlay a 9-zone (or 9×9 / 16-zone) grid. A second family (MahaVastu-style) uses 22.5° radial
   pie-slices from the centre instead — a genuinely *different* method that puts zone boundaries in
   different places. We use the grid method (method A), which is the mainstream classical one; we
   should stay consistent with it and say so.
5. **The real pitfall.** Treating a plot as a fixed *canvas* square (rather than dividing the *drawn
   footprint*) is a genuine correctness bug: the third-lines land at canvas positions that don't match
   the plot's real thirds, so rooms silently cross zone lines and the score shifts. Our engine avoids
   this (it divides the footprint, not the canvas) — but the *drawing tool* presenting a fixed square
   is what pushes users into inconsistent drawings.

---

## Recommendation

1. **Add a plot-shape/size step** before or in the grid editor: capture the plot's width × length
   (in feet/metres, or as a simple "columns × rows" of cells). Default stays square (8×8) for anyone
   who doesn't care.
2. **Render the grid at the true aspect ratio.** Two viable styles — an owner decision:
   - **(A) Same square cells, different counts** (e.g. 8 wide × 6 deep). Simplest; cells stay uniform;
     "1 cell = 1 fixed unit". Easiest to reason about and to keep the drag/resize maths unchanged.
   - **(B) True dimensions, rectangular cells** (grid stretched to the entered W×L). Most faithful to
     the real plot's feel; cells become slightly rectangular. A bit more UI work.
   *(My lean: **A** — it gets 95% of the benefit, keeps the editor maths and the touch targets clean,
   and the engine scores it identically. B can follow if you want the plot to look dimensionally exact.)*
3. **No engine change needed.** `PadaGrid` already divides the drawn footprint proportionally. We keep
   feeding it the footprint (bounding box of the drawn rooms), exactly as today.
4. **Keep the elongation caution** for aspect ratio > ~2:1 (already in the engine as X-15): show a
   gentle "long/narrow plot — Vastu-suboptimal" note, still score, never error.
5. **Say which method we use.** Because the grid method and the MahaVastu radial method legitimately
   disagree at zone edges, note (in the report's honesty section) that a room right on a boundary may
   read differently from a radial-method consultant — we use the classical proportional-grid method.

### What I'd build, if approved (editor "Build C")
- A plot-size control on Add-home / the grid step (width × depth in cells, with a foot/metre helper).
- `GRID` becomes `cols × rows` instead of a single `8` (touch-tested at the new proportions via the
  render harness).
- The zone-map overlay (Score/Mark-North) already stretches to the footprint, so it follows for free.
- Tests: the existing engine rotation-invariance tests still hold; add editor tests for non-square
  grids; render the new proportions across the §6.4 matrix.

---

## Sources
- plusvalueindia — [rectangular site → rectangular nine spaces](https://plusvalueindia.com/blogs/vastu/introduction-to-vastu-purusha-mandala-the-foundation-of-vastu-shastra-for-modern-homes)
- metaarch — [3×3 grid, thirds, centre = Brahmasthan](https://www.metaarch.com/post/improve-space-planning-with-vastu-shastra)
- AppliedVastu — [pada-vinyasa / mandala](https://www.appliedvastu.com/vastu-purusha-mandala)
- anantvastu — [16-direction radial (22.5°) method; bounding-box centre](https://www.anantvastu.com/blog/vastu-directions/)
- shilaavinyaas — [grid vs angular gridding methodology](https://shilaavinyaas.com/p/correct-vastu-energy-zone-gridding-methodology)
- NoBroker — [rectangular plot 1:2 ratio rule](https://www.nobroker.in/blog/rectangular-plot-vastu)
- Grokipedia — [Brahmasthan = central ninth, kept open](https://grokipedia.com/page/brahmasthan)
- Apps: [VastuAgent.ai](https://vastuagent.ai/floor-plan-generator) · [SquareYards calculator](https://www.squareyards.com/vastu-calculator) · [Vastu Compass / AppliedVastu](https://play.google.com/store/apps/details?id=com.appliedvastu.compass)
