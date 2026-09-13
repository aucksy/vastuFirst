// Prove the JavaScript build of the engine gives the SAME answer as the phone.
//
//   node tools/verify-enginejs.mjs [bundleDir]
//
// ⭐ Why this exists. `enginejs` compiles the real scoring source to JavaScript so the admin panel
// can show what a rule change does before anyone publishes it. Compiling is not proof: a bundle
// that builds cleanly and quietly rounds differently would hand an expert a number no phone would
// ever produce, and nothing else in the build would notice. So this loads the built bundle, feeds
// it the LIVE rule set and the anchor home, and fails unless it answers exactly what the JVM tests
// pin — score 31, penalty 16, defects X-01 and X-03, door E6.
//
// It also checks the loader's refusals survived the crossing, because the panel's whole promise is
// that it rejects a bad rule set using the app's own checks rather than a re-typed copy of them.

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { createRequire } from 'node:module';

const BUNDLE = process.argv[2] || 'enginejs/build/engine-bundle';
const require = createRequire(import.meta.url);

let failures = 0;
const check = (name, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  console.log(`${ok ? '  ok  ' : ' FAIL '} ${name}: ${JSON.stringify(got)}${ok ? '' : `  (wanted ${JSON.stringify(want)})`}`);
  if (!ok) failures++;
};
const checkThat = (name, ok, detail) => {
  console.log(`${ok ? '  ok  ' : ' FAIL '} ${name}${ok ? '' : `  — ${detail}`}`);
  if (!ok) failures++;
};

// ---- load the bundle ------------------------------------------------------------------------

const engineDir = join(BUNDLE, 'engine');
if (!existsSync(engineDir)) {
  console.error(`No engine folder at ${engineDir}. Did :enginejs:engineBundle run?`);
  process.exit(2);
}
const jsFiles = readdirSync(engineDir).filter(f => f.endsWith('.js'));
console.log('bundle contains:', jsFiles.join(', ') || '(nothing)');
if (jsFiles.length === 0) {
  console.error('The engine bundle has no JavaScript in it.');
  process.exit(2);
}

// Prefer the file named after the module; otherwise the largest, which is the bundle.
const pick = jsFiles.includes('vastuengine.js')
  ? 'vastuengine.js'
  : jsFiles.sort((a, b) => readFileSync(join(engineDir, b)).length - readFileSync(join(engineDir, a)).length)[0];
console.log('loading:', pick);

const loaded = require(join(process.cwd(), engineDir, pick));

// Kotlin's UMD output nests exports under the package path. Find the object however it is shaped.
function findApi(root) {
  const seen = new Set();
  const queue = [root];
  while (queue.length) {
    const node = queue.shift();
    if (!node || typeof node !== 'object' || seen.has(node)) continue;
    seen.add(node);
    if (typeof node.check === 'function' && typeof node.score === 'function' && typeof node.scoreAll === 'function') {
      return node;
    }
    for (const key of Object.keys(node)) {
      try { queue.push(node[key]); } catch { /* getters that throw are not the API */ }
    }
  }
  return null;
}

const api = findApi(loaded) || findApi(globalThis.vastuengine);
if (!api) {
  console.error('Could not find VastuEngineJs in the bundle. Top-level keys:', Object.keys(loaded || {}));
  process.exit(2);
}
console.log('found the engine API\n');

// ---- the live rule set and the anchor home ---------------------------------------------------

const rulesetDir = join(BUNDLE, 'ruleset');
const ruleset = {};
for (const name of ['meta', 'config', 'zones', 'rooms', 'doorPadas', 'defects', 'disputes', 'remedies']) {
  ruleset[name] = JSON.parse(readFileSync(join(rulesetDir, `${name}.json`), 'utf8'));
}
const rulesetJson = JSON.stringify(ruleset);
const sample01 = readFileSync(join(BUNDLE, 'homes', 'sample-01.json'), 'utf8');

// ---- 1. the live rule set must pass its own checks -------------------------------------------

console.log('--- the shipped rule set ---');
check('check() accepts the live rule set', api.check(rulesetJson), '');
const info = JSON.parse(api.describe(rulesetJson));
checkThat('describe() reports it as readable', info.ok === true, info.error);
check('door positions', info.doorPadas, 32);
check('room rules', info.roomRules, 15);
check('zones', info.zones, 9);
checkThat('it carries a version stamp', /^\d{4}\.\d{2}\.\d{2}-\d+$/.test(info.version), info.version);
checkThat('it carries a change note', (info.changeNote || '').length >= 120, 'the note is missing or too short');

// ---- 2. the anchor home must score what the JVM tests pin ------------------------------------

console.log('\n--- the anchor home, sample-01 ---');
const res = JSON.parse(api.score(rulesetJson, sample01));
checkThat('it scored at all', res.ok === true, res.error);
if (res.ok) {
  const a = res.analysis;
  check('score', a.score, 31);
  check('penalty', a.defectPenalty, 16);
  check('base, to two places', Math.round(a.base * 100) / 100, 47.02);
  check('findings', a.defects.map(d => d.id).sort(), ['X-01', 'X-03']);
  check('both findings are major', a.defects.map(d => d.severity), ['MAJOR', 'MAJOR']);
  check('door position', a.door && a.door.padaId, 'E6');
  check('door reading', a.door && a.door.verdict, 'INAUSPICIOUS');
  check('door bearing, to one place', a.door && Math.round(a.door.bearing * 10) / 10, 145.7);
  check('rooms scored', a.rooms.length, 7);
  check('quality', a.quality, 'OK');
  check('no cuts', a.cuts.length, 0);
  check('no extensions', a.extensions.length, 0);
  check('shape is regular', a.shapeIrregular, false);
  checkThat('the prayer-room disagreement is surfaced', a.disputes.some(d => d.id === 'W-12'), 'W-12 missing');
  check('it reports the rule version it used', a.ruleSetVersion, info.version);
}

// ---- 3. turning the dial must move the score, exactly as the JVM test says --------------------

console.log('\n--- a rule change moves the score ---');
const harsher = JSON.parse(JSON.stringify(ruleset));
harsher.config.penalties.MAJOR = 20;
const harsherRes = JSON.parse(api.score(JSON.stringify(harsher), sample01));
checkThat('the harsher rule set still scores', harsherRes.ok === true, harsherRes.error);
if (harsherRes.ok) {
  // RuleDataTest on the JVM pins exactly this: raise a major problem from 8 to 20 points and
  // sample-01 falls from 31 to 17, with the total capped at 30.
  check('score with a major problem worth 20', harsherRes.analysis.score, 17);
  check('penalty hits its ceiling', harsherRes.analysis.defectPenalty, 30);
}

// ---- 4. the app's own refusals must have survived the crossing --------------------------------

console.log('\n--- the rule set checks still refuse bad data ---');
const dropRemedies = JSON.parse(JSON.stringify(ruleset));
dropRemedies.defects[0].remedyIds = [];
checkThat('a finding with no remedy is refused', api.check(JSON.stringify(dropRemedies)) !== '', 'it was accepted');

const shortNote = JSON.parse(JSON.stringify(ruleset));
shortNote.meta.changeNote = 'Changed some things.';
checkThat('a change note that explains nothing is refused', api.check(JSON.stringify(shortNote)) !== '', 'it was accepted');

const wrongCentre = JSON.parse(JSON.stringify(ruleset));
wrongCentre.config.brahmasthanExtent = 'CENTRAL_4X4';
checkThat('a centre size the engine cannot honour is refused', api.check(JSON.stringify(wrongCentre)) !== '', 'it was accepted');

const missing = JSON.parse(JSON.stringify(ruleset));
delete missing.zones;
checkThat('a missing file is refused', api.check(JSON.stringify(missing)) !== '', 'it was accepted');

const typo = JSON.parse(JSON.stringify(ruleset));
typo.config.penaltyCapp = 30;
checkThat('a mistyped setting is refused', api.check(JSON.stringify(typo)) !== '', 'it was accepted');

// A refusal must also READ like something a person can act on.
const why = api.check(JSON.stringify(dropRemedies));
checkThat('the refusal says what is wrong in words', why.length > 30 && /remed/i.test(why), why);

// ---- 5. scoring a whole corpus in one call ----------------------------------------------------

console.log('\n--- scoring every preview home at once ---');
const homeFiles = readdirSync(join(BUNDLE, 'homes')).filter(f => f.endsWith('.json')).sort();
const homes = homeFiles.map(f => JSON.parse(readFileSync(join(BUNDLE, 'homes', f), 'utf8')));
const all = JSON.parse(api.scoreAll(rulesetJson, JSON.stringify(homes)));
check('one answer per home', all.length, homes.length);
checkThat('every home scored', all.every(r => r.ok), JSON.stringify(all.filter(r => !r.ok)));
checkThat('every score is a real number 0-100',
  all.every(r => Number.isInteger(r.analysis.score) && r.analysis.score >= 0 && r.analysis.score <= 100),
  'a score was out of range');

// A broken home must not stop the rest.
const withJunk = JSON.parse(api.scoreAll(rulesetJson, JSON.stringify([...homes, { id: 'nonsense' }])));
check('a broken home does not stop the others', withJunk.filter(r => r.ok).length, homes.length);

// ---- done -------------------------------------------------------------------------------------

console.log('');
if (failures > 0) {
  console.error(`${failures} check(s) failed. The browser engine does NOT match the phone.`);
  process.exit(1);
}
console.log('All checks passed. The browser engine answers exactly what the phone answers.');
