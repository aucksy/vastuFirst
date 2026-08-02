// audit-mapper.mjs — every RECORDED real reply through the mapper mirror, measured.
//
// WHY: the fuzz suite proves the mapper never emits an illegal plan, and the pinned cases prove a
// handful of known answers — but neither says how good the corpus AS A WHOLE comes out. This runs
// all recorded replies (30 old-prompt + 9 size-field + the owner's flat) and reports, per plan:
//
//   · outcome (placed / assisted / refused) and why
//   · rooms read → typed → placed, and every drop with its reason
//   · unknown captions verbatim (the synonym-table gap list)
//   · printed-size orientation: rooms whose FINAL drawn shape disagrees with the sheet
//   · looseness: empty cells inside the placed bounding box (the "islands" measure)
//   · any invariant violation (should never happen — the fuzz suite owns that)
//
// Run:  node tools/scan-eval/audit-mapper.mjs --only=X
//       (--only=X stops sim.mjs's own suites running on import)
//
// ⚠ The full RoomLabels table is ported here because the mirror's cut-down one changes Placed vs
// Assisted answers on real replies (the sized-share and room-count gates count TYPED rooms). This
// port mirrors shared/.../scan/RoomLabels.kt; RoomLabelsTest is the authority when they disagree.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { scanMap, scanInvariants, parsePrinted, printedTextOf } from '../grid-prototype/sim.mjs';

const HERE = dirname(fileURLToPath(import.meta.url));

// ---- full RoomLabels port (see RoomLabels.kt) --------------------------------------------------
const EXACT = {};
const put = (type, ...names) => names.forEach((n) => { EXACT[n] = type; });
put('ENTRANCE', 'ENTRANCE', 'ENTRY', 'FOYER', 'VESTIBULE', 'PORCH ENTRY', 'MAIN ENTRANCE',
  'ENTRANCE LOBBY', 'ENTRY LOBBY', 'ENT LOBBY');
put('KITCHEN', 'KITCHEN', 'KIT', 'MODULAR KITCHEN', 'OPEN KITCHEN', 'KITCHEN AREA');
put('MASTER_BEDROOM', 'MASTER BEDROOM', 'MASTER BED ROOM', 'MASTER BED', 'MBR', 'M BEDROOM', 'MASTER', 'MASTERBED');
put('BEDROOM', 'BEDROOM', 'BED ROOM', 'BED', 'SER ROOM', 'SERVANT ROOM', 'SERVANT', 'MAID ROOM',
  'KIDS ROOM', 'KIDS BEDROOM', 'CHILDREN ROOM', 'CHILD BEDROOM', 'BED RM', 'KIDS', 'SERV RM', 'SERV ROOM');
put('GUEST_BEDROOM', 'GUEST BEDROOM', 'GUEST ROOM', 'GUEST BED ROOM');
put('POOJA', 'POOJA', 'PUJA', 'POOJA ROOM', 'PUJA ROOM', 'PUJA SPACE', 'POOJA SPACE', 'PRAYER ROOM', 'MANDIR', 'TEMPLE', 'POJO');
put('TOILET', 'TOILET', 'WC', 'WATER CLOSET', 'POWDER ROOM', 'ATT TOILET', 'ATTACHED TOILET', 'COMMON TOILET', 'WASHROOM', 'WASH ROOM', 'TOIL');
put('BATHROOM', 'BATH', 'BATHROOM', 'BATH ROOM', 'BATH TOILET');
put('LIVING', 'LIVING', 'LIVING ROOM', 'HALL', 'DRAWING', 'DRAWING ROOM', 'LOUNGE', 'FAMILY ROOM',
  'LIVING DINING', 'LIVING AND DINING', 'LIVING CUM DINING', 'DRAWING DINING', 'LOBBY');
put('DINING', 'DINING', 'DINING ROOM', 'DINING SPACE', 'DINING AREA');
put('STAIRCASE', 'STAIR', 'STAIRS', 'STAIRCASE', 'STAIR CASE', 'STEPS');
put('STUDY', 'STUDY', 'STUDY ROOM', 'OFFICE', 'HOME OFFICE', 'AV ROOM', 'LIBRARY');
put('STORE', 'STORE', 'STORAGE', 'STORE ROOM', 'PANTRY');
put('GARAGE', 'GARAGE', 'CAR PARK', 'CAR PARKING', 'PARKING', 'CAR PORCH');
put('BALCONY', 'BALCONY', 'BALC', 'TERRACE', 'OPEN TERRACE', 'SIT OUT', 'SITOUT', 'PORCH',
  'VERANDAH', 'VERANDA', 'DECK', 'UTILITY BALCONY', 'DRY BALC', 'DRY BALCONY', 'C BAL');
put('BASEMENT', 'BASEMENT', 'CELLAR');
put('COURTYARD', 'COURTYARD', 'COURT YARD', 'ATRIUM', 'OPEN TO SKY');
put('UTILITY', 'UTILITY', 'WASH AREA', 'WASH', 'LAUNDRY', 'SERVICE AREA', 'UTILITY AREA', 'W AREA');
put('CORRIDOR', 'CORRIDOR', 'PASSAGE', 'HALLWAY', 'GALLERY', 'CIRCULATION');

const DROP_EXACT = new Set([
  'DRESS', 'DRESSING', 'DRESS AREA', 'DRESSING AREA', 'DRESSING ROOM',
  'WALK IN CLOSET', 'WALK IN WARDROBE', 'WALK IN ROBE', 'WARDROBE', 'CLOSET',
  'DUCT', 'ELECTRICAL DUCT', 'PLUMBING DUCT', 'SHAFT', 'LIFT', 'LIFT LOBBY',
  'ELEVATOR', 'AC LEDGE', 'LEDGE', 'OTS', 'VOID', 'OPEN TO BELOW',
  'LAWN', 'PATHWAY', 'CROCKERY UNIT',
]);
const DROP_CONTAINS = ['DRESSING', 'DRESS AREA', 'WARDROBE', 'CLOSET', 'DUCT', 'SHAFT', 'LIFT', 'ELEVATOR', 'LEDGE'];
const AMBIGUOUS = new Set(['LOBBY']);
const LOOSE_EXCLUDED = new Set(['MASTER', 'BED', 'KIT', 'MBR', 'WC', 'BALC', 'WASH', 'HALL']);
const CONTAINS = Object.entries(EXACT)
  .filter(([k]) => !LOOSE_EXCLUDED.has(k))
  .sort((a, b) => b[0].length - a[0].length || a[0].localeCompare(b[0]));

function clean(raw) {
  let s = String(raw).toUpperCase();
  s = s.replace(/\([^)]*\)/g, ' ');
  s = s.replace(/['"‘’“”′″]/g, ' ');
  s = s.replace(/\./g, '');
  s = s.replace(/(?<=[0-9])[ ]*X[ ]*(?=[0-9])/g, ' ');
  s = s.replace(/[^0-9A-Z]/g, ' ');
  return s.split(' ')
    .filter((t) => t && !/^[0-9]+(MM|CM|M|FT|SQFT|SQM)?$/.test(t) && t !== 'WIDE')
    .join(' ');
}
function livingByName(c) {
  if (EXACT[c]) return EXACT[c] === 'LIVING';
  if (DROP_EXACT.has(c) || DROP_CONTAINS.some((d) => c.includes(d))) return false;
  const hit = CONTAINS.find(([k]) => c.includes(k));
  return hit ? hit[1] === 'LIVING' : false;
}
function contextOf(labels) {
  return { hasLiving: labels.some((raw) => { const c = clean(raw); return !AMBIGUOUS.has(c) && livingByName(c); }) };
}
function fullResolve(raw, ctx) {
  const c = clean(raw);
  if (!c) return { kind: 'unknown' };
  if (AMBIGUOUS.has(c)) return { kind: 'room', type: ctx.hasLiving ? 'CORRIDOR' : 'LIVING', loose: true };
  if (EXACT[c]) return { kind: 'room', type: EXACT[c], loose: false };
  if (DROP_EXACT.has(c)) return { kind: 'drop' };
  if (DROP_CONTAINS.some((d) => c.includes(d))) return { kind: 'drop' };
  const hit = CONTAINS.find(([k]) => c.includes(k));
  if (hit) return { kind: 'room', type: AMBIGUOUS.has(hit[0]) ? (ctx.hasLiving ? 'CORRIDOR' : 'LIVING') : hit[1], loose: true };
  return { kind: 'unknown' };
}

// ---- corpus ------------------------------------------------------------------------------------
function load(rel) { return JSON.parse(readFileSync(join(HERE, rel), 'utf8')); }
const plans = [];
for (const p of load('out/real-plans-rects.json')) {
  plans.push({ id: p.file, aspect: p.size[0] / p.size[1], reply: p.reply, prompt: 'v1' });
}
for (const p of load('out/exp-size-field.json')) {
  plans.push({ id: `${p.file} [sized]`, aspect: p.size[0] / p.size[1], reply: p.V3, prompt: 'v3' });
}
{
  const o = load('../../shared/src/main/resources/scan/owner-flat.json');
  plans.push({ id: 'owner-flat [sized]', aspect: o.imageSize[0] / o.imageSize[1], reply: o.reply, prompt: 'v3' });
}

// ---- measures ----------------------------------------------------------------------------------
const cellsOf = (rects) => {
  const s = new Set();
  for (const r of rects) for (let c = r.col; c < r.col + r.w; c++) for (let w = r.row; w < r.row + r.h; w++) s.add(`${c},${w}`);
  return s;
};
const boundsOf = (rects) => {
  const minC = Math.min(...rects.map((r) => r.col)), maxC = Math.max(...rects.map((r) => r.col + r.w));
  const minR = Math.min(...rects.map((r) => r.row)), maxR = Math.max(...rects.map((r) => r.row + r.h));
  return (maxC - minC) * (maxR - minR);
};

// `--inject=` runs the whole corpus under a fault injection, so "does this machinery change any
// real answer?" is one diff away instead of an argument.
const INJECT = (process.argv.find((a) => a.startsWith('--inject=')) || '').split('=')[1] || null;
if (INJECT) console.log(`⚠ FAULT INJECTED: ${INJECT}`);

let totals = { placed: 0, assisted: 0, refused: 0, orientBad: 0, orientJudged: 0, unknowns: new Map(), drops: new Map() };
for (const plan of plans) {
  const ctx = contextOf((plan.reply.rooms || []).map((b) => b.label));
  const out = scanMap(plan.reply, plan.aspect, { resolveLabel: (raw) => fullResolve(raw, ctx), inject: INJECT });
  const inv = scanInvariants(out);
  totals[out.kind]++;
  const lines = [];
  const drops = out.notes.dropped || [];
  for (const d of drops) {
    totals.drops.set(d.reason, (totals.drops.get(d.reason) || 0) + 1);
    if (d.reason === 'UNKNOWN_LABEL') totals.unknowns.set(d.label, (totals.unknowns.get(d.label) || 0) + 1);
  }
  let detail = '';
  if (out.kind === 'placed') {
    const rects = out.rooms.map((r) => r.rect);
    const empty = boundsOf(rects) - cellsOf(rects).size;
    // FINAL orientation vs the printed sheet — what the user actually sees, trim included.
    // `r.printed` is attached by scanMap per room, so duplicate captions (three rooms all labelled
    // BALCONY with different sizes) are judged each against their own size, not the first one's.
    let bad = 0, judged = 0;
    for (const r of out.rooms) {
      const p = r.printed;
      if (!p) continue;
      const ps = Math.sign(p.w - p.h), ds = Math.sign(r.rect.w - r.rect.h);
      if (ps !== 0) { judged++; if (ds !== 0 && ds !== ps) bad++; }
    }
    totals.orientBad += bad; totals.orientJudged += judged;
    detail = `${out.cols}x${out.rows} · ${out.rooms.length}/${(plan.reply.rooms || []).length} rooms · empty ${empty}/${boundsOf(rects)}`
      + (judged ? ` · orient ${judged - bad}/${judged}` : '');
  } else if (out.kind === 'assisted') {
    detail = `${out.reason} · ${out.rooms.length} identified`;
  } else {
    detail = out.reason;
  }
  const dropStr = drops.length ? ` · drops: ${drops.map((d) => `${d.label}(${d.reason.slice(0, 4)})`).join(' ')}` : '';
  console.log(`${plan.id.padEnd(28)} ${out.kind.toUpperCase().padEnd(9)} ${detail}${dropStr}`);
  if (inv.length) console.log(`   ⚠ INVARIANT: ${inv.join(' | ')}`);
  for (const l of lines) console.log(l);
}

console.log('\n---- totals ----');
console.log(`outcomes: ${totals.placed} placed · ${totals.assisted} assisted · ${totals.refused} refused of ${plans.length}`);
console.log(`final orientation agrees with the printed sheet: ${totals.orientJudged - totals.orientBad}/${totals.orientJudged}`);
console.log('drops by reason:', Object.fromEntries(totals.drops));
console.log('unknown captions:', [...totals.unknowns.keys()].sort().join(' | ') || '(none)');
