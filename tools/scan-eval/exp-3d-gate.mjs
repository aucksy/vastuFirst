// exp-3d-gate.mjs — when the reader calls a sheet "3D", can we tell a PLAN from a PICTURE?
//
// WHY THIS EXISTS. `ScanMapper.ifRead` offers a "read it anyway" retry behind a NOT_2D refusal.
// Until 15 Aug 2026 that retry threw its own placement away and handed over bare room names — and
// unplaced rooms route to the guided grid, which the owner reported as the worst journey in the
// app ("a scan sent me to the grid and it made me crazy mad", 15 Aug 2026).
//
// The discard was not arbitrary. A genuinely tilted photo returns rectangles that describe where
// the CAMERA stood, not where the walls are, and a confident wrong layout is the precise harm the
// 2D gate exists to stop. So the question was never "should we trust it" but "can we TELL".
//
// TWO CANDIDATE TESTS, both measured here:
//
//   share of rooms PLACED   — REJECTED. The corpus's genuinely tilted street aerial (plan-030,
//                             roof off, cars and neighbours in frame) places 11 of the 14 rooms it
//                             reads: 79 %. The mislabelled flat sheets place 100 % and 93 %. They
//                             do not separate, so this rule would wave a 3D render through.
//
//   share of rooms with a   — ADOPTED. A marketing render is drawn to look nice, never to be built
//   PRINTED SIZE              from, so it prints no dimensions. A plan sheet always does. The two
//                             classes sit at 0 % and 100 %. Shipped as `sheetPrintsItsOwnSizes`.
//
// Costs nothing: replays recordings, no API calls. Re-run after any prompt change — the reads this
// reads from out/live are re-recordable, and a prompt that stops returning printed sizes would move
// a plan from one column to the other silently.
//
// Run:  node tools/scan-eval/exp-3d-gate.mjs
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));

// MIRRORS ScanMapper.MIN_SIZED_SHARE / MIN_PRINTED_TO_FIT. If those move, move these.
const MIN_SIZED_SHARE = 0.7;
const MIN_PRINTED_TO_FIT = 3;

// The sheets that matter: everything the reader has ever called 3D, plus a few known-good 2D
// sheets as controls. Names are the stems written by scan-live.py into out/live.
const SHEETS = [
  ['plan-030', 'tilted street aerial — roof off, cars, neighbours (a real render)'],
  ['plan-031', 'flat sheet the reader mislabels 3D'],
  ['plan-018', 'flat sheet the reader mislabels 3D'],
  ['plan-007', 'straight-overhead furnished render (control)'],
  ['plan-012', 'numbered rooms with a legend (control)'],
  ['aipl-zen-residences-gurgaon_2bhk-1262sqft (1)', "the owner's Gurgaon sheet (control)"],
];

const pad = (s, n) => String(s).padEnd(n).slice(0, n);
const padL = (s, n) => String(s).padStart(n);

console.log('%s %s %s %s %s  %s',
  pad('sheet', 14), pad('reader says', 12), padL('rooms', 5), padL('sized', 5), padL('share', 6), 'layout kept?');

let missing = 0;
for (const [stem, note] of SHEETS) {
  const p = join(HERE, 'out/live', `${stem}.json`);
  if (!existsSync(p)) { console.log('%s (no recording — run scan-live.py on it)', pad(stem, 14)); missing++; continue; }
  const d = JSON.parse(readFileSync(p, 'utf8'));
  const reply = typeof d.reply === 'string' ? JSON.parse(d.reply) : d.reply;
  const rooms = reply.rooms || [];
  const sized = rooms.filter((r) => (r.size || '').trim()).length;
  const share = rooms.length ? sized / rooms.length : 0;
  const keeps = rooms.length >= MIN_PRINTED_TO_FIT && sized >= MIN_PRINTED_TO_FIT && share >= MIN_SIZED_SHARE;
  const is3d = (reply.planType || '') === '3D_RENDER';
  console.log('%s %s %s %s %s  %s   %s',
    pad(stem, 14), pad(reply.planType || '?', 12), padL(rooms.length, 5), padL(sized, 5),
    padL((share * 100).toFixed(0) + '%', 6),
    keeps ? 'KEEPS LAYOUT ' : 'hands back   ',
    is3d ? note : `(2D anyway) ${note}`);
}
if (missing) console.log('\n%d sheet(s) have no recording — the check is incomplete.', missing);
console.log('\nThe rule: a sheet keeps its layout through the 3D gate only when at least %d rooms,',
  MIN_PRINTED_TO_FIT);
console.log('and at least %d%% of them, carry a size the sheet itself printed.', MIN_SIZED_SHARE * 100);
