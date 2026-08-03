// resemblance.mjs — score a DRAWN grid against the PAPER truth, as numbers.
//
// WHY: the owner judges every scan by eye against the sheet ("kitchen on the wrong side",
// "drawing room tiny"). Until now only totals existed (rooms placed, fill %), which average
// away exactly that harm. This scores what he actually checks, per plan:
//   rooms       how many of the sheet's rooms arrived at all (type-matched, nearest first)
//   arrangement of every matched pair clearly left/right or above/below on the sheet,
//               what share the drawing keeps in the same order
//   biggest     does the sheet's biggest printed room arrive as the biggest drawn room?
//   one-cell    how many drawn rooms are a single square (the nameless-chip problem)
//
// A truth file is written ONCE per plan by reading the printed sheet (see truth/*.json).
// The geometry comes from render-grid.mjs (--reply=... writes out/render/<id>.geom.json),
// so this scores the REAL mapper's output for any reader, current or candidate — which is
// how reader A vs reader B on the same plan becomes one comparable line each.
//
// Usage:
//   node tools/scan-eval/resemblance.mjs --truth=tools/scan-eval/truth/<plan>.json \
//        tools/scan-eval/out/render/<scan-a>.geom.json [<scan-b>.geom.json ...]
import { readFileSync } from 'node:fs';

const args = process.argv.slice(2);
const truthPath = (args.find((a) => a.startsWith('--truth=')) || '').slice(8);
const geomPaths = args.filter((a) => !a.startsWith('--'));
if (!truthPath || geomPaths.length === 0) {
  console.log('usage: node resemblance.mjs --truth=truth/<plan>.json out/render/<scan>.geom.json ...');
  process.exit(1);
}
const truth = JSON.parse(readFileSync(truthPath, 'utf8'));

// Type-compatibility groups: a maid's room may legitimately arrive as BEDROOM, a toilet as
// BATHROOM, a lobby as ENTRANCE. Matching across a group is correct, not a defect.
const GROUP = (t) =>
  ['BEDROOM', 'MASTER_BEDROOM', 'GUEST_BEDROOM'].includes(t) ? 'BED'
  : ['TOILET', 'BATHROOM'].includes(t) ? 'BATH'
  : ['CORRIDOR', 'ENTRANCE', 'STAIRCASE'].includes(t) ? 'PASS'
  : ['LIVING', 'DINING'].includes(t) ? 'MAIN'
  : t;

// Two sheet positions closer than this (as a fraction of the sheet) are "about the same
// place" — their order is not scored. Keeps wall-thickness jitter out of the number.
const SAME_PLACE = 0.08;

for (const gp of geomPaths) {
  const g = JSON.parse(readFileSync(gp, 'utf8'));
  const name = String(g.id ?? gp).padEnd(28);
  if (g.kind !== 'placed') {
    console.log(`${name} REFUSED (${g.reason ?? g.kind}) — rooms 0/${truth.rooms.length}`);
    continue;
  }
  const drawn = g.rooms.map((r) => ({
    type: r.type,
    cx: (r.rect.col + r.rect.w / 2) / g.cols,
    cy: (r.rect.row + r.rect.h / 2) / g.rows,
    cells: r.rect.w * r.rect.h,
  }));

  // Nearest-centroid match inside each type group, greedily in truth order.
  const free = new Set(drawn.keys());
  const matched = [];
  const missing = [];
  for (const t of truth.rooms) {
    let best = -1, bestD = Infinity;
    for (const i of free) {
      if (GROUP(drawn[i].type) !== GROUP(t.type)) continue;
      const d = (drawn[i].cx - t.cx) ** 2 + (drawn[i].cy - t.cy) ** 2;
      if (d < bestD) { bestD = d; best = i; }
    }
    if (best === -1) missing.push(t.name);
    else { matched.push({ t, d: drawn[best] }); free.delete(best); }
  }

  // Pairwise order agreement, each axis scored only where the sheet clearly separates the pair.
  let agree = 0, pairs = 0;
  for (let i = 0; i < matched.length; i++) {
    for (let j = i + 1; j < matched.length; j++) {
      const a = matched[i], b = matched[j];
      if (Math.abs(a.t.cx - b.t.cx) > SAME_PLACE) {
        pairs++;
        if (Math.sign(a.t.cx - b.t.cx) === Math.sign(a.d.cx - b.d.cx)) agree++;
      }
      if (Math.abs(a.t.cy - b.t.cy) > SAME_PLACE) {
        pairs++;
        if (Math.sign(a.t.cy - b.t.cy) === Math.sign(a.d.cy - b.d.cy)) agree++;
      }
    }
  }

  const biggestTruth = truth.rooms.reduce((m, r) => (r.wFt * r.hFt > (m ? m.wFt * m.hFt : -1) ? r : m), null);
  const biggestPair = matched.find((m) => m.t === biggestTruth);
  const maxCells = Math.max(...drawn.map((r) => r.cells));
  const biggestOK = biggestPair ? biggestPair.d.cells === maxCells : false;
  const chips = drawn.filter((r) => r.cells === 1).length;

  console.log(
    `${name} rooms ${matched.length}/${truth.rooms.length}` +
    (missing.length ? ` (missing: ${missing.join(', ')})` : '') +
    ` · arrangement ${pairs ? Math.round((100 * agree) / pairs) : 0}%` +
    ` · biggest-room ${biggestOK ? 'RIGHT' : 'WRONG'}` +
    ` · one-cell ${chips}/${drawn.length}`,
  );
}
