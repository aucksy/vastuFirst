#!/usr/bin/env node
// THE FILL FACTOR (29 Sep 2026, direction trial) — free, no scan.
//
//     node tools/scan-eval/exp-fill-factor.mjs
//
// A POINT reply (prompt v7-point) gives each room one point and its printed size, but no box. To
// rebuild a box from "point + printed size" we need the plan's SCALE — pixels per millimetre — and
// the only length on the page left to take it from is the BUILDING box. So:
//
//     scale² × (sum of the printed room areas) = fill × (the building box's area)
//
// where "fill" is the share of the building box that the rooms cover. This measures fill on TODAY's
// recordings (gpt-5.6-luna, prompt v6), which DO carry boxes, and checks how well each way of taking
// the scale from the building box predicts the scale each plan's own boxes give (the same scale the
// "printed size around each box's middle" baseline used: sqrt(sized box area / printed area)).
//
//   A  sized rooms only:    scale² = F_sized × building / printed
//   B  every room:          scale² = (F_all × building − unsized boxes) / printed
//
// Each plan's scale is predicted with the MEDIAN fill of the OTHER plans (leave one out), so a
// plan never helps predict itself.
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.argv.push('--only=X'); // load the mapper mirror without running its self-test suites
const { parsePrinted, printedTextOf } = await import('../grid-prototype/sim.mjs');

const HERE = dirname(fileURLToPath(import.meta.url));
const LIVE = join(HERE, 'out', 'live');
const SUFFIX = '.openai_gpt-5.6-luna.v6.json';

const median = (xs) => { const s = [...xs].sort((a, b) => a - b); return s.length ? s[Math.floor(s.length / 2)] : NaN; };
const pct = (x) => (100 * x).toFixed(1) + '%';

// The same sanity the mapper applies to a building box (ScanMapper.saneBuilding): a real box, mostly
// on the page, no sliver.
function sane(b) {
  if (!b) return null;
  const { x, y, w, h } = b;
  if (![x, y, w, h].every(Number.isFinite)) return null;
  const ok = w > 0.05 && h > 0.05 && w <= 1.0 && h <= 1.0 && x >= -0.02 && y >= -0.02 && x + w <= 1.05 && y + h <= 1.05;
  return ok ? b : null;
}

const plans = [];
for (const f of readdirSync(LIVE).filter((n) => n.endsWith(SUFFIX)).sort()) {
  const rec = JSON.parse(readFileSync(join(LIVE, f), 'utf8'));
  const rep = rec.reply || {};
  const b = sane(rep.building);
  if (!b || !Array.isArray(rep.rooms) || rep.planType !== '2D_PLAN') continue;
  const [W, H] = rec.imageSize;
  const bPx = b.w * W * b.h * H;
  let sizedPx = 0, printedMm2 = 0, unsizedPx = 0, nSized = 0, nUnsized = 0;
  for (const r of rep.rooms) {
    const px = (r.w || 0) * b.w * W * (r.h || 0) * b.h * H;
    // The mirror of RoomDimensions.of: the reader's size field first, the caption second; {w, h} in mm.
    const p = parsePrinted(printedTextOf(r));
    if (p && p.w > 0 && p.h > 0) { sizedPx += px; printedMm2 += p.w * p.h; nSized++; }
    else { unsizedPx += px; nUnsized++; }
  }
  if (nSized < 2 || printedMm2 <= 0 || sizedPx <= 0) continue;
  plans.push({
    id: f.slice(0, -SUFFIX.length), bPx, sizedPx, printedMm2, unsizedPx, nSized, nUnsized,
    fSized: sizedPx / bPx, fAll: (sizedPx + unsizedPx) / bPx,
    sBox: Math.sqrt(sizedPx / printedMm2),
  });
}

console.log(`today's recordings with a sane building box and >= 2 sized rooms: ${plans.length}`);
console.log(`fill, sized rooms only : median ${pct(median(plans.map((p) => p.fSized)))}  ` +
  `range ${pct(Math.min(...plans.map((p) => p.fSized)))} - ${pct(Math.max(...plans.map((p) => p.fSized)))}`);
console.log(`fill, every room       : median ${pct(median(plans.map((p) => p.fAll)))}  ` +
  `range ${pct(Math.min(...plans.map((p) => p.fAll)))} - ${pct(Math.max(...plans.map((p) => p.fAll)))}`);

// Leave-one-out: predict each plan's scale from the other plans' median fill.
const errs = { A: [], B: [] };
const rows = [];
for (const p of plans) {
  const others = plans.filter((q) => q !== p);
  const fS = median(others.map((q) => q.fSized));
  const fA = median(others.map((q) => q.fAll));
  const sA = Math.sqrt(fS * p.bPx / p.printedMm2);
  const bArea = fA * p.bPx - p.unsizedPx;
  const sB = bArea > 0 ? Math.sqrt(bArea / p.printedMm2) : NaN;
  const eA = sA / p.sBox - 1, eB = sB / p.sBox - 1;
  errs.A.push(Math.abs(eA)); if (Number.isFinite(eB)) errs.B.push(Math.abs(eB));
  rows.push(`${p.id.slice(0, 30).padEnd(31)} sized ${String(p.nSized).padStart(2)} unsized ${String(p.nUnsized).padStart(2)}  ` +
    `fill sized ${pct(p.fSized).padStart(6)} all ${pct(p.fAll).padStart(6)}   scale error  A ${(100 * eA).toFixed(0).padStart(4)}%  B ${Number.isFinite(eB) ? (100 * eB).toFixed(0).padStart(4) + '%' : '   -'}`);
}
console.log('');
rows.forEach((r) => console.log(r));
console.log('');
for (const k of ['A', 'B']) {
  const e = errs[k];
  console.log(`way ${k}: scale off by a median ${pct(median(e))}, worst ${pct(Math.max(...e))}, ` +
    `within 10% on ${e.filter((x) => x <= 0.10).length} of ${e.length}, within 20% on ${e.filter((x) => x <= 0.20).length}`);
}
console.log(`\nuse: F_sized = ${median(plans.map((p) => p.fSized)).toFixed(3)}, F_all = ${median(plans.map((p) => p.fAll)).toFixed(3)} (medians over all ${plans.length})`);
