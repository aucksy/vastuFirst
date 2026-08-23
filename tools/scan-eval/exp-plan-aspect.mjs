// exp-plan-aspect.mjs — the DRAWING's proportions, or the SHEET's? (23 Aug 2026)
//
// WHY THIS EXISTS. Room boxes come back as fractions of the BUILDING'S OUTER WALL — that is the
// prompt's own contract and has been since v4 added the `building` box. But `ScanMapper` was
// turning those fractions into pixels using the ratio of the WHOLE SHEET. That is only the same
// number when the drawing fills the page, and a real sheet never does: there is a title block, a
// legend, an area table, margins, sometimes a showcase render taking half the width.
//
// The error is exactly `building.w / building.h`, and it lands on the two things that decide where
// a room ends up — the grid's shape (`gridFor`) and the wall-line snap (`snapAll`). A room's
// position IS its Vastu direction, so a sheet with wide margins was being scored as a
// different-shaped home than the one on the paper.
//
// IT IS ARITHMETIC, NOT A JUDGEMENT. Three sheets in the corpus state their own overall size, so
// the right answer is printed on them and can simply be read off:
//
//   plan-034   prints 25'0" x 40'0"  = 0.63    sheet ratio said 1.00     building ratio says 0.63
//   plan-017   prints 45'   x 36'    = 1.25    sheet ratio said 0.75     building ratio says 1.22
//   plan-018   the 2D drawing measures ~1.17   sheet ratio said 2.72     building ratio says 1.12
//
// plan-018 is the sheet it was found on — a 4 BHK drawn beside a big showcase render — and it is
// the worst case: the flat was squashed into a 10 x 4 letterbox with a room falling off the bottom.
// Through the building box it is 10 x 8 and all fifteen rooms land where the paper draws them.
//
// Costs nothing: replays recordings, no API calls. Reads out/live, which are the reads from the
// reader we actually ship.
//
// Run:  node tools/scan-eval/exp-plan-aspect.mjs
//       node tools/scan-eval/exp-plan-aspect.mjs --only=X      (the flag sim.mjs wants; added for you)
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

// ⚠ sim.mjs runs its fuzz suites on import unless --only is in argv. See render-grid.mjs.
if (!process.argv.some((a) => a.startsWith('--only='))) process.argv.push('--only=X');
const { scanMap } = await import('../grid-prototype/sim.mjs');
// The app's own synonym table, so "rooms placed" here means what it means on the phone.
const { fullResolve, contextOf } = await import('./audit-mapper.mjs');

const HERE = dirname(fileURLToPath(import.meta.url));
const LIVE = join(HERE, 'out', 'live');

const pad = (s, n) => String(s).padEnd(n).slice(0, n);
const padL = (s, n) => String(s).padStart(n);

// One recording per PLAN — the newest reply we hold for it — so a plan with nine model comparisons
// does not outvote one with a single read.
const byPlan = new Map();
for (const name of readdirSync(LIVE).filter((n) => n.endsWith('.json'))) {
  const stem = name.replace(/\.json$/, '');
  const plan = stem.split('.')[0];
  let d;
  try { d = JSON.parse(readFileSync(join(LIVE, name), 'utf8')); } catch { continue; }
  const reply = typeof d.reply === 'string' ? JSON.parse(d.reply) : d.reply;
  if (!reply || !Array.isArray(reply.rooms) || !reply.rooms.length || !d.imageSize) continue;
  if (!reply.building) continue;                       // pre-v4 reads are untouched by this change
  // Prefer the plain "<plan>.json" (the owner's own recording) over a model-comparison variant.
  if (byPlan.has(plan) && stem !== plan) continue;
  byPlan.set(plan, { stem, reply, imageSize: d.imageSize });
}

const strip = (reply) => ({ ...reply, building: null });

console.log('%s %s %s %s %s %s',
  pad('plan', 14), padL('sheet', 6), padL('drawing', 8),
  padL('grid now', 9), padL('grid fixed', 11), ' rooms placed  now -> fixed');

let moved = 0, betterPlaced = 0, worsePlaced = 0;
for (const [plan, rec] of [...byPlan].sort((a, b) => a[0].localeCompare(b[0]))) {
  const sheet = rec.imageSize[0] / rec.imageSize[1];
  const b = rec.reply.building;
  const drawing = sheet * (b.w / b.h);
  // Force the 2D gate open on both sides: this experiment is about SHAPE, not about triage.
  const asTwoD = { ...rec.reply, planType: '2D_PLAN' };
  const ctx = contextOf(asTwoD.rooms.map((r) => r.label));
  const opts = { resolveLabel: (raw) => fullResolve(raw, ctx) };
  const before = scanMap(strip(asTwoD), sheet, opts);
  const after = scanMap(asTwoD, sheet, opts);
  const grid = (o) => (o.kind === 'placed' ? `${o.cols}x${o.rows}` : o.kind);
  const placed = (o) => (o.kind === 'placed' ? o.rooms.length : 0);
  const changed = grid(before) !== grid(after);
  if (changed) moved++;
  if (placed(after) > placed(before)) betterPlaced++;
  if (placed(after) < placed(before)) worsePlaced++;
  console.log('%s %s %s %s %s   %s -> %s %s',
    pad(plan, 14), padL(sheet.toFixed(2), 6), padL(drawing.toFixed(2), 8),
    padL(grid(before), 9), padL(grid(after), 11),
    padL(placed(before), 3), padL(placed(after), 3),
    changed ? ' <<' : '');
}

console.log('\n%d plans carry a building box · %d get a different grid · %d place more rooms · %d place fewer',
  byPlan.size, moved, betterPlaced, worsePlaced);
console.log('Recordings without a building box (every pre-v4 read, and every bundled test fixture)');
console.log('are untouched: the conversion degrades to the sheet ratio it used before.');
