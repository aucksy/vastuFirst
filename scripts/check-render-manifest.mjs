#!/usr/bin/env node
/**
 * check-render-manifest.mjs — Gate 3 (L1 geometry) of docs/UI-POLISH.md.
 *
 * Reads the hand-written L1 measurement manifests (app/build/render-manifest/*.json), produced by
 * the semantics walk in L1Manifest.kt, and asserts on the rendered geometry — the engine-independent
 * layer that a pixel diff cannot judge. This is the gate that would have caught the zero-height grid.
 *
 * Two classes of check:
 *   - HARD (always fail): a tagged / interactive / text node with zero width or height. This is the
 *     v0.2.1 class of catastrophe and is never ratcheted away.
 *   - RATCHETED (fail only if the per-screen count INCREASES): clipped content, ellipsis, tap
 *     targets under 48 dp, interactive nodes with no accessible label. Recorded in
 *     app/src/androidUnitTest/render-baseline.json so the gate can adopt a codebase that does not
 *     yet pass, and can only improve from there (UI-POLISH §6.3).
 *
 * Fail-loud (§6.3): a missing manifest dir, zero manifests, an empty node list, or a collapsed root
 * is exit 2 — the harness did not actually run, and that must never read as "no findings".
 *
 * Usage:  node scripts/check-render-manifest.mjs [repoRoot]
 * Exit 0 = ok (or ratchet bootstrapped) · 1 = a HARD defect or a ratchet regression · 2 = harness broken.
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { argv, exit } from 'node:process';

const ROOT = argv[2] ?? '.';
const MANIFEST_DIR = `${ROOT}/app/build/render-manifest`;
const BASELINE = `${ROOT}/app/src/androidUnitTest/render-baseline.json`;

// Tolerances (dp).
const MIN_TOUCH = 48;   // Material touch-target floor.
const CLIP_TOL = 1.0;   // unclipped-minus-clipped beyond this = genuinely cut off (not rounding).

/* ── fail-loud: did the harness actually run? ─────────────────────────────────────────────── */
if (!existsSync(MANIFEST_DIR)) {
  console.error(`render-manifest: MISSING manifest dir\n  ${MANIFEST_DIR}`);
  console.error('The render harness did not run — refusing to report "no findings".');
  exit(2);
}
const files = readdirSync(MANIFEST_DIR).filter((f) => f.endsWith('.json'));
if (files.length === 0) {
  console.error(`render-manifest: ZERO manifests in ${MANIFEST_DIR} — the harness did not render.`);
  exit(2);
}

const hard = [];                 // HARD defects, always fail
const softByScreen = {};         // screen -> [messages]  (ratcheted)
const addSoft = (screen, msg) => (softByScreen[screen] ??= []).push(msg);

for (const f of files) {
  const m = JSON.parse(readFileSync(`${MANIFEST_DIR}/${f}`, 'utf8'));
  const screen = m.screen ?? f;

  if (!Array.isArray(m.nodes) || m.nodes.length === 0) {
    console.error(`render-manifest: ${f} has ZERO nodes — the render produced an empty tree.`);
    exit(2);
  }
  if (!(m.rootWidthDp > 0) || !(m.rootHeightDp > 0)) {
    console.error(`render-manifest: ${f} root collapsed to ${m.rootWidthDp}×${m.rootHeightDp} dp.`);
    exit(2);
  }
  softByScreen[screen] ??= [];

  for (const n of m.nodes) {
    const label = n.testTag || trim(n.text) || trim(n.contentDescription) || n.role || '(node)';
    const meaningful = n.testTag || n.clickable || n.text;
    const fullyClipped = n.wDp <= 0 || n.hDp <= 0;   // scrolled off-screen — clipped bounds vanish

    // ⭐⭐ CUT BY THE FOLD, NOT BY A CONTAINER — excluded, and this is a correctness fix, not a
    // loosening. A capture is a VIEWPORT: on any screen taller than the window, whichever node
    // happens to straddle the bottom edge reports clipped bounds shorter than its layout size. That
    // is not a defect, it is the edge of the photograph.
    //
    // ⚠ Measured before this went in (9 Aug 2026, the report rebuild): on three consecutive runs
    // EVERY new finding on the report was one of these, and the count moved 7 → 8 when a hint line
    // was shortened by a few dp — nothing about the layout's quality changed, only which node landed
    // on the fold. A number that moves like that is measuring the fold, not the screen, and it had
    // been quietly inflating the baselines of every long screen (report-clean fell 6 → 2 and
    // report-prayer 7 → 2 the moment this was applied).
    //
    // The test is narrow on purpose: the node's VISIBLE bottom has to reach the bottom of the root.
    // A container clipping its own text cuts it well inside the window, so that still reports.
    const cutByFold = !fullyClipped &&
      n.yDp + n.hDp >= m.rootHeightDp - CLIP_TOL &&
      n.unclippedHDp > n.hDp;

    // HARD: the LAYOUT itself has no size (unclipped). This is the zero-height-grid bug. A node
    // that is merely fully clipped (scrolled out of view) has a real unclipped size and is fine.
    if (meaningful && (n.unclippedWDp <= 0 || n.unclippedHDp <= 0)) {
      hard.push(`[${screen} · ${m.config}] "${label}" has ZERO layout size (${n.unclippedWDp}×${n.unclippedHDp} dp).`);
    }
    // PARTIAL clip: visible but cut off. Fully-clipped (off-screen in a scroller) is excluded —
    // that is normal, and the partially-visible edge item is the one that actually looks broken.
    // Width clipping is ALWAYS reported — the fold cuts height, never width, so a narrow node is a
    // real container problem even on a node that also happens to sit on the bottom edge.
    const clippedW = n.unclippedWDp - n.wDp > CLIP_TOL;
    const clippedH = n.unclippedHDp - n.hDp > CLIP_TOL;
    if (!fullyClipped && (clippedW || (clippedH && !cutByFold))) {
      addSoft(screen, `[${m.config}] "${label}" is clipped: shows ${n.wDp}×${n.hDp}, needs ${n.unclippedWDp}×${n.unclippedHDp} dp.`);
    }
    if (n.ellipsized) {
      addSoft(screen, `[${m.config}] "${label}" text is ellipsized.`);
    }
    // Touch target judged on the UNCLIPPED size, so a chip scrolled to the edge isn't miscounted.
    if (n.clickable && !fullyClipped && (n.unclippedWDp < MIN_TOUCH || n.unclippedHDp < MIN_TOUCH)) {
      addSoft(screen, `[${m.config}] clickable "${label}" is ${n.unclippedWDp}×${n.unclippedHDp} dp (< ${MIN_TOUCH} dp touch floor).`);
    }
    if (n.clickable && !n.contentDescription && !n.text) {
      addSoft(screen, `[${m.config}] clickable "${label}" has no contentDescription or text.`);
    }
  }
}

/* ── HARD defects fail immediately, never ratcheted ──────────────────────────────────────── */
if (hard.length) {
  console.error(`\nrender-manifest: ${hard.length} HARD defect(s) — a rendered element has no size:`);
  hard.forEach((h) => console.error(`  ✗ ${h}`));
  console.error('\nThis is the zero-height-grid class of bug. Fix the layout; do not ratchet it away.');
  exit(1);
}

/* ── RATCHET the soft findings ───────────────────────────────────────────────────────────── */
// The count can only go DOWN. A screen not yet in the baseline is ADOPTED at its current count
// (so new screens land without a red build); a screen whose count DROPPED tightens the baseline;
// a screen whose count ROSE fails. Adoption + tightening are persisted, and CI commits the file.
const counts = Object.fromEntries(Object.entries(softByScreen).map(([s, l]) => [s, l.length]));
const baseline = existsSync(BASELINE) ? JSON.parse(readFileSync(BASELINE, 'utf8')) : {};

let regressed = false;
let changed = false;
const lines = [];
for (const [screen, count] of Object.entries(counts)) {
  if (!(screen in baseline)) {
    baseline[screen] = count;
    changed = true;
    lines.push(`  + ${screen}: ${count} (adopted)`);
  } else if (count > baseline[screen]) {
    regressed = true;
    lines.push(`  ✗ ${screen}: ${count} (baseline ${baseline[screen]})`);
  } else if (count < baseline[screen]) {
    lines.push(`  ↓ ${screen}: ${count} (was ${baseline[screen]}) — baseline tightened`);
    baseline[screen] = count;
    changed = true;
  } else {
    lines.push(`  · ${screen}: ${count}`);
  }
}
console.log('render-manifest: L1 findings per screen (ratchet):');
lines.forEach((l) => console.log(l));

if (changed && !regressed) {
  writeFileSync(BASELINE, JSON.stringify(sortKeys(baseline), null, 2) + '\n');
  console.log('render-manifest: render-baseline.json updated (CI will commit it).');
}

if (regressed) {
  console.error('\nrender-manifest: a screen gained new L1 findings. Details:');
  for (const [screen, list] of Object.entries(softByScreen)) {
    if ((counts[screen] ?? 0) > (baseline[screen] ?? 0)) {
      list.forEach((msg) => console.error(`  ✗ ${msg}`));
    }
  }
  console.error('\nFix the new finding, or — if intentional — update render-baseline.json in the same commit.');
  exit(1);
}
console.log('\nrender-manifest: no regression. ✓');
exit(0);

function sortKeys(obj) {
  return Object.fromEntries(Object.keys(obj).sort().map((k) => [k, obj[k]]));
}

function trim(s) {
  if (!s) return null;
  return s.length > 40 ? s.slice(0, 39) + '…' : s;
}
