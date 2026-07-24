#!/usr/bin/env node
/**
 * check-a11y-manifest.mjs — Gate 3c (accessibility) of docs/UI-POLISH.md §6.6.
 *
 * Reads the per-screen accessibility manifests (app/build/a11y-manifest/*.json) written by the
 * AccessibilityTest run (Google's ATF via Roborazzi's checkRoboAccessibility — contrast, touch
 * targets, missing/duplicate labels, traversal order), and RATCHETS the error-level finding count
 * per screen against app/src/androidUnitTest/a11y-baseline.json. Fails only when a screen's count
 * INCREASES — so it adopts a codebase that does not yet pass, and can only improve.
 *
 * a11y is additive: the render + L1 gates already catch the worst geometry, so this never hard-gates
 * on the finding COUNT — it ratchets. But two things ARE hard failures:
 *   - a missing manifest dir / zero manifests  → the a11y pass did not run (exit 2);
 *   - any manifest with "errored": true         → checkRoboAccessibility threw something unexpected,
 *     i.e. the integration itself is broken and the counts are meaningless (exit 2). Never let a
 *     broken check read as "0 findings".
 *
 * Usage:  node scripts/check-a11y-manifest.mjs [repoRoot]
 * Exit 0 = ok (or ratchet bootstrapped/tightened) · 1 = a screen gained findings · 2 = check broken.
 */
import { readFileSync, writeFileSync, existsSync, readdirSync } from 'node:fs';
import { argv, exit } from 'node:process';

const ROOT = argv[2] ?? '.';
const MANIFEST_DIR = `${ROOT}/app/build/a11y-manifest`;
const BASELINE = `${ROOT}/app/src/androidUnitTest/a11y-baseline.json`;

if (!existsSync(MANIFEST_DIR)) {
  console.error(`a11y: MISSING manifest dir\n  ${MANIFEST_DIR}`);
  console.error('The accessibility pass did not run — refusing to report "no findings".');
  exit(2);
}
const files = readdirSync(MANIFEST_DIR).filter((f) => f.endsWith('.json'));
if (files.length === 0) {
  console.error(`a11y: ZERO manifests in ${MANIFEST_DIR} — the accessibility pass did not run.`);
  exit(2);
}

const counts = {};
const details = {};
const errored = [];
for (const f of files) {
  const m = JSON.parse(readFileSync(`${MANIFEST_DIR}/${f}`, 'utf8'));
  const screen = m.screen ?? f.replace(/\.json$/, '');
  if (m.errored) errored.push(`${screen}: ${m.detail}`);
  counts[screen] = m.count ?? 0;
  if (m.detail) details[screen] = m.detail;
}

/* ── fail-loud: the check itself broke ───────────────────────────────────────────────────── */
if (errored.length) {
  console.error(`\na11y: the accessibility check ERRORED on ${errored.length} screen(s) — counts are meaningless:`);
  errored.forEach((e) => console.error(`  ✗ ${e}`));
  console.error('\nThe checkRoboAccessibility integration is broken (a version/API mismatch, most likely).');
  console.error('Fix the integration, or remove the a11y gate until it can run — do not ship it reading "0".');
  exit(2);
}

/* ── ratchet ─────────────────────────────────────────────────────────────────────────────── */
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
console.log('a11y: ATF error-level findings per screen (ratchet):');
lines.forEach((l) => console.log(l));

if (changed && !regressed) {
  writeFileSync(BASELINE, JSON.stringify(sortKeys(baseline), null, 2) + '\n');
  console.log('a11y: a11y-baseline.json updated (CI will commit it).');
}

if (regressed) {
  console.error('\na11y: a screen gained accessibility findings. Details:');
  for (const [screen, count] of Object.entries(counts)) {
    if (count > (baseline[screen] ?? 0)) console.error(`  ✗ ${screen}: ${details[screen] ?? '(no detail)'}`);
  }
  console.error('\nFix the finding, or — if intentional — update a11y-baseline.json in the same commit.');
  exit(1);
}
console.log('\na11y: no regression. ✓');
exit(0);

function sortKeys(obj) {
  return Object.fromEntries(Object.keys(obj).sort().map((k) => [k, obj[k]]));
}
