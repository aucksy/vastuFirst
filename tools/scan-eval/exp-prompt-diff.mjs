// exp-prompt-diff.mjs — what did changing the PROMPT actually change? (23 Aug 2026)
//
// WHY THIS EXISTS. reader-config.json says it in as many words: "changing the words invalidates the
// numbers. Prompt tuning is a deliberate, re-measured step, never a tweak." Every prompt generation
// so far has been re-measured by hand, in a different shape each time, and the comparison then lived
// only in whichever session ran it. This makes it one command.
//
// It reads recordings from out/live and lines up two generations of the SAME sheet by tag, e.g.
// the v5 reads against the v6 reads. Free — replays recordings, no API calls.
//
// Run:  node tools/scan-eval/exp-prompt-diff.mjs --from=v5 --to=v6
//       node tools/scan-eval/exp-prompt-diff.mjs --from=v4a --to=v6 --model=openai_gpt-5.6-luna
//
// The columns that matter:
//   triage        2D_PLAN / 3D_RENDER / NOT_A_PLAN — the refusal decision, which is the whole point
//   rooms         how many spaces came back
//   sized         how many carry a size the sheet printed (the reader's strongest output)
//   outcome       what ScanMapper does with it: placed / assisted / refused
//
// ⚠ A recording tagged "v5" is only v5 because a session named it so. Since 23 Aug 2026 every new
// recording also carries a `prompt` fingerprint of the exact words sent (scan-live.py /
// scan-candidate.py); this tool prints it so a mislabelled file cannot pass unnoticed.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

if (!process.argv.some((a) => a.startsWith('--only='))) process.argv.push('--only=X');
const { scanMap } = await import('../grid-prototype/sim.mjs');
const { fullResolve, contextOf } = await import('./audit-mapper.mjs');

const HERE = dirname(fileURLToPath(import.meta.url));
const LIVE = join(HERE, 'out', 'live');
const arg = (n, d) => (process.argv.find((a) => a.startsWith(`--${n}=`)) || `=${d}`).split('=').slice(1).join('=');

const FROM = arg('from', 'v5');
const TO = arg('to', 'v6');
const MODEL = arg('model', 'openai_gpt-5.6-luna');

const load = (p) => {
  if (!existsSync(p)) return null;
  try {
    const d = JSON.parse(readFileSync(p, 'utf8'));
    const reply = typeof d.reply === 'string' ? JSON.parse(d.reply) : d.reply;
    return reply ? { ...d, reply } : null;
  } catch { return null; }
};

// Every stem that has a recording at the TO tag — that is the set the new prompt was run over.
const stems = readdirSync(LIVE)
  .filter((n) => n.endsWith(`.${MODEL}.${TO}.json`))
  .map((n) => n.slice(0, -`.${MODEL}.${TO}.json`.length))
  .sort();

if (!stems.length) {
  console.log('No recordings tagged "%s" for model "%s" in out/live.', TO, MODEL);
  console.log('Record them first (one PAID scan each — get the owner\'s approved count, CLAUDE.md §2c):');
  console.log('  python tools/scan-eval/scan-candidate.py "<plan>" --model=openai/gpt-5.6-luna --tag=%s', TO);
  process.exit(1);
}

// The older read for a stem: the same tag if we have it, otherwise the newest thing we hold.
const olderFor = (stem) => {
  const exact = join(LIVE, `${stem}.${MODEL}.${FROM}.json`);
  if (existsSync(exact)) return { path: exact, how: FROM };
  for (const tag of ['v5', 'v4b', 'v4a']) {
    const p = join(LIVE, `${stem}.${MODEL}.${tag}.json`);
    if (existsSync(p)) return { path: p, how: tag };
  }
  const plain = join(LIVE, `${stem}.json`);
  if (existsSync(plain)) return { path: plain, how: 'plain' };
  return null;
};

const summarise = (d) => {
  if (!d) return null;
  const rooms = d.reply.rooms || [];
  const sized = rooms.filter((r) => String(r.size || '').trim()).length;
  const aspect = d.imageSize ? d.imageSize[0] / d.imageSize[1] : 1;
  const ctx = contextOf(rooms.map((r) => r.label));
  const out = scanMap(d.reply, aspect, { resolveLabel: (raw) => fullResolve(raw, ctx) });
  return {
    triage: d.reply.planType || 'UNKNOWN',
    rooms: rooms.length,
    sized,
    outcome: out.kind === 'placed' ? `placed ${out.cols}x${out.rows}` : `${out.kind} ${out.reason || ''}`.trim(),
    fingerprint: String(d.prompt || '(none)'),
  };
};

const pad = (s, n) => String(s).padEnd(n).slice(0, n);
const padL = (s, n) => String(s).padStart(n);

console.log('Prompt %s  ->  %s      model %s\n', FROM, TO, MODEL);
console.log('%s %s %s %s %s %s',
  pad('sheet', 30), pad('from', 6), pad('triage  before -> after', 26),
  padL('rooms', 11), padL('sized', 11), ' outcome after');

let flipped = 0, lostRooms = 0, gainedRooms = 0, missing = 0, mismatched = 0;
for (const stem of stems) {
  const older = olderFor(stem);
  const b = summarise(older && load(older.path));
  const a = summarise(load(join(LIVE, `${stem}.${MODEL}.${TO}.json`)));
  if (!a) { console.log('%s  (unreadable new recording)', pad(stem, 30)); missing++; continue; }
  if (!b) { console.log('%s  no earlier read — %s, %d rooms, %s', pad(stem, 30), a.triage, a.rooms, a.outcome); continue; }
  const moved = b.triage !== a.triage;
  if (moved) flipped++;
  if (a.rooms < b.rooms) lostRooms++;
  if (a.rooms > b.rooms) gainedRooms++;
  if (older.how !== FROM) mismatched++;
  console.log('%s %s %s %s %s  %s %s',
    pad(stem, 30),
    pad(older.how, 6),
    pad(`${b.triage} -> ${a.triage}`, 26),
    padL(`${b.rooms} -> ${a.rooms}`, 11),
    padL(`${b.sized} -> ${a.sized}`, 11),
    pad(a.outcome, 22),
    moved ? '<< TRIAGE MOVED' : '');
}

console.log('\n%d sheets compared · %d changed their triage answer · %d returned more rooms · %d returned fewer',
  stems.length, flipped, gainedRooms, lostRooms);
console.log('A sheet that moved 3D_RENDER -> 2D_PLAN is one we used to refuse and now read.');
console.log('A sheet that moved 2D_PLAN -> 3D_RENDER is a REGRESSION and must be looked at by eye.');
if (mismatched) {
  console.log('\n⚠ %d of these sheets have NO recording at "%s" — the "from" column names what was', mismatched, FROM);
  console.log('  actually compared against. Those rows span more than one prompt generation, so a room');
  console.log('  count moving by one or two on them is not evidence about the change being measured.');
  console.log('  THE TRIAGE COLUMN IS THE ONE TO READ: it is the decision a prompt change is aimed at,');
  console.log('  and it is stable across generations in a way room counts are not.');
}
