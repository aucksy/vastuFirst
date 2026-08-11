// audit-entry.mjs — HOW OFTEN does the app read the front door off the plan instead of asking?
//
// WHY THIS EXISTS (owner, 11 Aug 2026): *"I saw it ask anyway. NOBODY HAS MEASURED HOW OFTEN IT
// ACTUALLY FIRES."* The feature (`frontDoorFromEntrance`, PlanConversion.kt) has been in since
// 6 August and its refusal conditions were reasoned about, never counted. This counts them, on the
// recorded replies already in the repo — no scan is sent anywhere, so it costs nothing.
//
// It reports, per plan and in total:
//   · did the scan PLACE its rooms at all (only a placed scan can be asked this question)
//   · how many rooms the plan named as an entrance — 0, 1 or more
//   · when exactly one: how far it sits from the nearest outer wall, in cells
//   · whether a corner tie killed it
//   · and therefore whether the door is READ or ASKED
//
// Run:  node tools/scan-eval/audit-entry.mjs
//       node tools/scan-eval/audit-entry.mjs --reach=2       (what a looser wall test would buy)
//       node tools/scan-eval/audit-entry.mjs --tiebreak      (settle a corner instead of refusing)
//       node tools/scan-eval/audit-entry.mjs --hints          (the shipped way-in captions as well)
//       node tools/scan-eval/audit-entry.mjs --hints=PORCH,LOBBY   (measure a different list)
//
// MEASURED 11 Aug 2026, and this is the whole reason the feature changed:
//   without captions   7 of 24 placed plans read their own door (29 %)
//   with them         13 of 24 (54 %), no plan changing the wall it already had
//   refusals for geometry — wall reach, corner tie — ZERO, before and after
import { scanMap } from '../grid-prototype/sim.mjs';
import { plans, fullResolve, contextOf } from './audit-mapper.mjs';

const arg = (name, dflt) => {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.split('=')[1] : dflt;
};
const REACH = Number(arg('reach', '1'));
const TIEBREAK = process.argv.includes('--tiebreak');
const HINTS = process.argv.some((a) => a === '--hints' || a.startsWith('--hints='));

/**
 * Captions that name a WAY IN without being typed as an entrance — the second lever, and the one
 * the first run of this audit pointed at. A porch is what you walk under to reach the door; a lobby
 * on an outer wall is the flat's own entry lobby. Neither changes the room's SCORED type: this list
 * is read only to decide which wall the door is on, and the geometry test still has to agree.
 */
// ⭐ The SHIPPED list, mirroring RoomLabels.WAY_IN_WORDS. LOBBY is deliberately not in it — with it
// the corpus reaches 15 of 24 and then reads two different walls for the two recorded scans of one
// Green Court flat. Pass --hints=... to measure a different list before changing the Kotlin.
const WAY_IN_HINTS = (arg('hints', 'ENTRANCE,ENTRY,FOYER,VESTIBULE,PORCH,VERANDAH,VERANDA')).split(',');
const isWayIn = (label) => {
  const c = String(label).toUpperCase();
  // CAR PORCH / CAR PARKING is where the car lives, not where the person walks in.
  if (/CAR\s*(PORCH|PARK)/.test(c)) return false;
  return WAY_IN_HINTS.some((h) => c.includes(h));
};

/**
 * The Kotlin `frontDoorFromEntrance`, mirrored. Returns the reason it refused, or the wall it read.
 * Kept structurally identical to the Kotlin so a change there can be measured here first.
 */
function readDoor(rooms, { reach = 1, tiebreak = false, hints = false } = {}) {
  if (!rooms.length) return { fired: false, why: 'no rooms' };
  let entrances = rooms.filter((r) => r.type === 'ENTRANCE');
  let via = 'entrance';
  if (entrances.length === 0 && hints) {
    entrances = rooms.filter((r) => isWayIn(r.label));
    via = 'hint';
  }
  if (entrances.length === 0) return { fired: false, why: 'plan names no entrance', entrances: 0 };
  if (entrances.length > 1) return { fired: false, why: `plan names ${entrances.length} entrances`, entrances: entrances.length };
  const e = entrances[0].rect;
  const minC = Math.min(...rooms.map((r) => r.rect.col));
  const maxC = Math.max(...rooms.map((r) => r.rect.col + r.rect.w));
  const minR = Math.min(...rooms.map((r) => r.rect.row));
  const maxR = Math.max(...rooms.map((r) => r.rect.row + r.rect.h));
  const walls = [
    ['N', e.row - minR, e.w],
    ['S', maxR - (e.row + e.h), e.w],
    ['W', e.col - minC, e.h],
    ['E', maxC - (e.col + e.w), e.h],
  ];
  const nearest = Math.min(...walls.map((w) => w[1]));
  if (nearest > reach) return { fired: false, why: `entrance is ${nearest} cells off every wall`, entrances: 1, gap: nearest };
  const onWall = walls.filter((w) => w[1] === nearest);
  const longest = Math.max(...onWall.map((w) => w[2]));
  const best = onWall.filter((w) => w[2] === longest);
  if (best.length > 1 && !tiebreak) {
    return { fired: false, why: `corner: ${best.map((b) => b[0]).join('/')} equally long`, entrances: 1, gap: nearest, tie: best.map((b) => b[0]) };
  }
  return { fired: true, wall: best[0][0], via, entrances: 1, gap: nearest, tie: best.length > 1 ? best.map((b) => b[0]) : null, label: entrances[0].label };
}

const rows = [];
const disagreements = [];
let placedN = 0;
const tally = { read: 0, noEntrance: 0, manyEntrances: 0, tooFar: 0, corner: 0, notPlaced: 0 };
const gaps = new Map();

for (const plan of plans) {
  const ctx = contextOf((plan.reply.rooms || []).map((b) => b.label));
  const out = scanMap(plan.reply, plan.aspect, { resolveLabel: (raw) => fullResolve(raw, ctx) });
  if (out.kind !== 'placed') {
    tally.notPlaced++;
    rows.push(`${plan.id.padEnd(30)} ${out.kind.toUpperCase().padEnd(9)} —  (never asked: no placed geometry)`);
    continue;
  }
  placedN++;
  const r = readDoor(out.rooms, { reach: REACH, tiebreak: TIEBREAK, hints: HINTS });
  // Does the looser reading ever DISAGREE with the strict one where both answer? That is the only
  // way a hint can do harm — and it is a number, not an opinion.
  const strict = readDoor(out.rooms, { reach: 1, tiebreak: false, hints: false });
  if (strict.fired && r.fired && strict.wall !== r.wall) disagreements.push(`${plan.id}: strict ${strict.wall} vs loose ${r.wall}`);
  if (r.fired) tally.read++;
  else if (r.entrances === 0) tally.noEntrance++;
  else if (r.entrances > 1) tally.manyEntrances++;
  else if (r.tie) tally.corner++;
  else tally.tooFar++;
  if (r.entrances === 1 && r.gap !== undefined) gaps.set(r.gap, (gaps.get(r.gap) || 0) + 1);
  const verdict = r.fired ? `READ  ${r.wall} wall${r.via === 'hint' ? ` (from "${r.label}")` : ''}` : `ASK   (${r.why})`;
  const captions = (plan.reply.rooms || [])
    .map((b) => b.label)
    .filter((l) => /ENTR|ENTRY|FOYER|VESTIB|LOBBY|PORCH/i.test(l));
  rows.push(`${plan.id.padEnd(30)} PLACED    ${verdict.padEnd(44)} ${captions.length ? 'captions: ' + captions.join(' | ') : ''}`);
}

console.log(`reach=${REACH} cells · corner tie-break ${TIEBREAK ? 'ON' : 'OFF'} · way-in captions ${HINTS ? 'ON' : 'OFF'}\n`);
rows.forEach((l) => console.log(l));
console.log('\n---- totals ----');
console.log(`plans in corpus: ${plans.length} · placed (can be asked): ${placedN} · not placed: ${tally.notPlaced}`);
console.log(`FRONT DOOR READ OFF THE PLAN: ${tally.read}/${placedN} placed plans` +
  ` (${Math.round((100 * tally.read) / Math.max(1, placedN))}% of placed, ` +
  `${Math.round((100 * tally.read) / plans.length)}% of the whole corpus)`);
console.log(`still asked — plan names no entrance: ${tally.noEntrance}` +
  ` · names several: ${tally.manyEntrances}` +
  ` · entrance too far from any wall: ${tally.tooFar}` +
  ` · corner with no longer side: ${tally.corner}`);
console.log(`walls where the loose reading disagrees with the strict one: ${disagreements.length ? disagreements.join(' · ') : 'none'}`);
console.log('distance from the nearest outer wall, single-entrance plans (cells → plans):',
  Object.fromEntries([...gaps.entries()].sort((a, b) => a[0] - b[0])));

// ---- the same sheet, read twice ---------------------------------------------------------------
// ⭐ The only free check on whether a wall reading is TRUE rather than merely confident. Ten sheets
// in this corpus were read twice (the v1 and v3 prompts; Green Court 336 clean and branded). The
// door is read off the READER'S rectangles, so if two reads of one sheet disagree about the wall,
// at least one of them is wrong — and we would be stating it to the user either way.
const bySheet = new Map();
for (const plan of plans) {
  const key = plan.id.replace(/ \[(sized|live)\]$/, '').replace(/-(clean|branded)$/, '');
  const ctx = contextOf((plan.reply.rooms || []).map((b) => b.label));
  const out = scanMap(plan.reply, plan.aspect, { resolveLabel: (raw) => fullResolve(raw, ctx) });
  if (out.kind !== 'placed') continue;
  const strict = readDoor(out.rooms, { reach: 1, tiebreak: false, hints: false });
  const loose = readDoor(out.rooms, { reach: REACH, tiebreak: TIEBREAK, hints: true });
  if (!bySheet.has(key)) bySheet.set(key, []);
  bySheet.get(key).push({ id: plan.id, strict, loose });
}
let sAgree = 0, sSplit = 0, lAgree = 0, lSplit = 0;
const splits = [];
for (const [key, reads] of bySheet) {
  if (reads.length < 2) continue;
  for (const mode of ['strict', 'loose']) {
    const walls = new Set(reads.filter((r) => r[mode].fired).map((r) => r[mode].wall));
    if (reads.filter((r) => r[mode].fired).length < 2) continue;
    if (walls.size === 1) { mode === 'strict' ? sAgree++ : lAgree++; }
    else {
      mode === 'strict' ? sSplit++ : lSplit++;
      splits.push(`${key} [${mode}]: ${reads.map((r) => `${r[mode].fired ? r[mode].wall : '—'}`).join(' vs ')}`);
    }
  }
}
console.log(`\n---- the same sheet, read twice ----`);
console.log(`strict rule: ${sAgree} sheets agree, ${sSplit} disagree`);
console.log(`with way-in captions: ${lAgree} sheets agree, ${lSplit} disagree`);
splits.forEach((s) => console.log(`  ⚠ ${s}`));
