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

    // HARD: the LAYOUT itself has no size (unclipped). This is the zero-height-grid bug. A node
    // that is merely fully clipped (scrolled out of view) has a real unclipped size and is fine.
    if (meaningful && (n.unclippedWDp <= 0 || n.unclippedHDp <= 0)) {
      hard.push(`[${screen} · ${m.config}] "${label}" has ZERO layout size (${n.unclippedWDp}×${n.unclippedHDp} dp).`);
    }
    // PARTIAL clip: visible but cut off. Fully-clipped (off-screen in a scroller) is excluded —
    // that is normal, and the partially-visible edge item is the one that actually looks broken.
    if (!fullyClipped && (n.unclippedWDp - n.wDp > CLIP_TOL || n.unclippedHDp - n.hDp > CLIP_TOL)) {
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
const counts = Object.fromEntries(Object.entries(softByScreen).map(([s, l]) => [s, l.length]));

if (!existsSync(BASELINE)) {
  writeFileSync(BASELINE, JSON.stringify(counts, null, 2) + '\n');
  console.log('render-manifest: bootstrapped render-baseline.json (ratchet starts here):');
  for (const [s, c] of Object.entries(counts)) console.log(`  ${s}: ${c} finding(s)`);
  console.log('These are recorded, not fixed — the count can only decrease from now on.');
  exit(0);
}

const baseline = JSON.parse(readFileSync(BASELINE, 'utf8'));
let regressed = false;
const lines = [];
for (const [screen, count] of Object.entries(counts)) {
  const base = baseline[screen] ?? 0;
  const mark = count > base ? '✗' : count < base ? '↓' : '·';
  if (count > base) regressed = true;
  lines.push(`  ${mark} ${screen}: ${count} (baseline ${base})`);
}
console.log('render-manifest: L1 findings per screen (ratchet):');
lines.forEach((l) => console.log(l));

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

function trim(s) {
  if (!s) return null;
  return s.length > 40 ? s.slice(0, 39) + '…' : s;
}
