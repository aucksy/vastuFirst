#!/usr/bin/env node
/**
 * check-design-fidelity.mjs — Gate 1 of docs/UI-POLISH.md.
 *
 * "Matches the design" is not an opinion here — it is a file. This script compares the
 * IMPLEMENTATION (the designsystem theme) against the DESIGN CONTRACT and fails the build on drift:
 *
 *   design contract                                    implementation
 *   ─────────────────────────────────────────────      ────────────────────────────────
 *   handoff/tokens.json .......... colours, spacing,    theme/VastuTheme.kt
 *                                  radii, elevation,
 *                                  border widths
 *   `const ramp`   in the .dc.html  type ramp incl.     theme/VastuTypography.kt
 *                                  letter-spacing
 *   `const auditRows` in the .dc.html  contrast floors  (computed from VastuColors)
 *
 * Usage:  node scripts/check-design-fidelity.mjs [repoRoot]
 * Exit 0 = conformant · 1 = drift · 2 = a contract input is missing (harness broken).
 *
 * A DELIBERATE deviation is legal, but it must be declared in ACCEPTED below with a reason, so it
 * shows up in the diff. Silent drift is what this script exists to prevent.
 */
import { readFileSync, existsSync } from 'node:fs';
import { argv, exit } from 'node:process';

/* ── declared, reviewed deviations from the contract ─────────────────────────────────────── */
const ACCEPTED = {
  'type.weight.label':
    'Design ramp asks for 600; the bundled DM Sans ships no 600 cut, so label uses Medium (500). ' +
    'Never faux-bold a real face.',
  'radius.radius-full':
    'Design records 999dp; implemented as RoundedCornerShape(percent = 50) — identical for a pill.',
};

/* ── the three accent colours the design itself audits BELOW their 3:1 floor ──────────────
 * These are not palette bugs — the design marks them "accent". They carry a USAGE CONSTRAINT
 * instead: none of them may ever be the only thing conveying a meaning (UI-POLISH.md §2.3).  */
const KNOWN_ACCENTS = new Set(['color-secondary', 'score-workable', 'zone-e']);

const ROOT = argv[2] ?? process.cwd();
const BUNDLE = `${ROOT}/Design System/Final Designs/vastufirst-mobile-apps-implementation/project`;
const IN = {
  tokens: `${BUNDLE}/handoff/tokens.json`,
  ds: `${BUNDLE}/VastuFirst - Sage & Gold Design System.dc.html`,
  theme: `${ROOT}/designsystem/src/commonMain/kotlin/com/vastufirst/designsystem/theme/VastuTheme.kt`,
  typo: `${ROOT}/designsystem/src/commonMain/kotlin/com/vastufirst/designsystem/theme/VastuTypography.kt`,
};

for (const [k, f] of Object.entries(IN)) {
  if (!existsSync(f)) {
    console.error(`design-fidelity: MISSING CONTRACT INPUT (${k})\n  ${f}`);
    console.error('The harness is broken — refusing to report "no drift".');
    exit(2);
  }
}

const tokens = JSON.parse(readFileSync(IN.tokens, 'utf8'));
const dsHtml = readFileSync(IN.ds, 'utf8');
const themeSrc = readFileSync(IN.theme, 'utf8');
const typoSrc = readFileSync(IN.typo, 'utf8');

const drift = [];
const accepted = [];
const constraints = [];
const fail = (key, msg) => (ACCEPTED[key] ? accepted.push(`${key} — ${ACCEPTED[key]}`) : drift.push(msg));

/* ── helpers ─────────────────────────────────────────────────────────────────────────────── */
const camel = (k) => k.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
// tokens.json spells compass zones zone-ne / zone-sw; Kotlin uses zoneNE / zoneSW
const FIELD = {
  zoneNe: 'zoneNE', zoneSe: 'zoneSE', zoneSw: 'zoneSW', zoneNw: 'zoneNW',
};
const field = (t) => { const c = camel(t); return FIELD[c] ?? c; };

function colorsFromTheme() {
  const block = themeSrc.match(/val SageGold = VastuColors\(([\s\S]*?)\n\)/);
  if (!block) { drift.push('colour: cannot find `val SageGold = VastuColors(...)` in VastuTheme.kt'); return {}; }
  const out = {};
  for (const m of block[1].matchAll(/(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)/g)) out[m[1]] = '#' + m[2].slice(2).toUpperCase();
  return out;
}
function dpDefaults(cls) {
  const block = themeSrc.match(new RegExp(`data class ${cls}\\(([\\s\\S]*?)\\n\\)`));
  if (!block) { drift.push(`${cls}: data class not found in VastuTheme.kt`); return {}; }
  const out = {};
  for (const m of block[1].matchAll(/val\s+(\w+)\s*:\s*Dp\s*=\s*([\d.]+)\.dp/g)) out[m[1]] = parseFloat(m[2]);
  return out;
}
function shapesFromTheme() {
  const block = themeSrc.match(/data class VastuShapes\(([\s\S]*?)\n\)/);
  if (!block) { drift.push('radius: data class VastuShapes not found'); return {}; }
  const out = {};
  for (const m of block[1].matchAll(/val\s+(\w+)\s*:\s*CornerBasedShape\s*=\s*RoundedCornerShape\(\s*([^)]*?)\s*\)/g)) {
    const dp = m[2].match(/([\d.]+)\.dp/);
    out[m[1]] = dp ? parseFloat(dp[1]) : (/percent\s*=\s*50/.test(m[2]) ? 'full' : m[2]);
  }
  return out;
}
const lum = (hex) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };

/* ── 1 · colours ─────────────────────────────────────────────────────────────────────────── */
const impl = colorsFromTheme();
for (const [tok, hex] of Object.entries(tokens.color)) {
  const key = field(tok), want = hex.toUpperCase();
  if (!(key in impl)) { fail(`color.${tok}`, `colour ${tok}: no VastuColors field \`${key}\``); continue; }
  if (impl[key] !== want) fail(`color.${tok}`, `colour ${tok}: design ${want}, code ${impl[key]}`);
}

/* ── 2 · spacing ─────────────────────────────────────────────────────────────────────────── */
const spacing = dpDefaults('VastuSpacing');
for (const [tok, v] of Object.entries(tokens.space)) {
  const key = tok.replace('space-', 's');
  if (!(key in spacing)) { fail(`space.${tok}`, `spacing ${tok}=${v}dp: no VastuSpacing field \`${key}\``); continue; }
  if (spacing[key] !== v) fail(`space.${tok}`, `spacing ${tok}: design ${v}dp, code ${spacing[key]}dp`);
}

/* ── 3 · radii ───────────────────────────────────────────────────────────────────────────── */
const shapes = shapesFromTheme();
for (const [tok, v] of Object.entries(tokens.radius)) {
  const key = tok.replace('radius-', ''), got = shapes[key];
  if (got === undefined) { fail(`radius.${tok}`, `radius ${tok}: no VastuShapes field \`${key}\``); continue; }
  if (v > 100) { if (got !== 'full') fail(`radius.${tok}`, `radius ${tok}: design is a full pill, code ${got}`); }
  else if (got !== v) fail(`radius.${tok}`, `radius ${tok}: design ${v}dp, code ${got}dp`);
}

/* ── 4 · elevation ───────────────────────────────────────────────────────────────────────── */
const elev = dpDefaults('VastuElevation');
for (const [tok, v] of Object.entries(tokens.elevation)) {
  const key = tok.replace('elevation-', '');
  if (!(key in elev)) { fail(`elev.${tok}`, `elevation ${tok}: no VastuElevation field \`${key}\``); continue; }
  if (elev[key] !== v) fail(`elev.${tok}`, `elevation ${tok}: design ${v}dp, code ${elev[key]}dp`);
}

/* ── 5 · border widths ───────────────────────────────────────────────────────────────────── */
const borders = dpDefaults('VastuBorders');
const BORDER_FIELD = { hairline: 'hairline', default: 'regular', strong: 'strong', focus: 'focus' };
for (const [tok, v] of Object.entries(tokens.border)) {
  const key = BORDER_FIELD[tok.replace('border-', '')];
  if (!key || !(key in borders)) { fail(`border.${tok}`, `border ${tok}: no VastuBorders field`); continue; }
  if (borders[key] !== v) fail(`border.${tok}`, `border ${tok}: design ${v}dp, code ${borders[key]}dp`);
}

/* ── 6 · type ramp (size · line-height · weight · letter-spacing) ────────────────────────── */
const rampBlock = dsHtml.match(/const ramp=\[([\s\S]*?)\]\.map/);
if (!rampBlock) drift.push('type: cannot find `const ramp=[...]` in the design system file');
else {
  const design = {};
  for (const m of rampBlock[1].matchAll(/\{name:'([^']+)',fam:'([^']+)',size:(\d+),weight:(\d+),lh:'([^']+)',tr:'([^']+)'/g))
    design[camel(m[1])] = { fam: m[2], size: +m[3], weight: +m[4], lh: parseFloat(m[5]), tr: m[6] };

  const built = {};
  for (const m of typoSrc.matchAll(/(\w+)\s*=\s*style\(\s*([\d.]+)f\s*,\s*"(\w+)"\s*,\s*f\.(\w+)\s*,\s*FontWeight\.(\w+)/g))
    built[m[1]] = { size: parseFloat(m[2]), role: m[3], family: m[4], weight: m[5] };

  const mapOfBlock = (name) => {
    const b = typoSrc.match(new RegExp(`private val ${name} = mapOf\\(([\\s\\S]*?)\\)\\n`));
    const out = {};
    if (b) for (const m of b[1].matchAll(/"(\w+)"\s*to\s*([\d.]+)f/g)) out[m[1]] = parseFloat(m[2]);
    return out;
  };
  const lh = mapOfBlock('LatinLineHeights');
  const tracking = mapOfBlock('LatinTracking');
  const appliesTracking = /letterSpacing\s*=\s*trackingEm\(/.test(typoSrc);
  const W = { Normal: 400, Medium: 500, SemiBold: 600, Bold: 700 };

  for (const [name, d] of Object.entries(design)) {
    const b = built[name];
    if (!b) { fail(`type.${name}`, `type: design style "${name}" is not built in vastuTypography()`); continue; }
    if (b.size !== d.size) fail(`type.size.${name}`, `type ${name}: design ${d.size}sp, code ${b.size}sp`);
    const got = lh[b.role];
    if (got === undefined) fail(`type.lh.${name}`, `type ${name}: no line-height for role "${b.role}"`);
    else if (Math.abs(got - d.lh) > 0.001) fail(`type.lh.${name}`, `type ${name}: design line-height ${d.lh}, code ${got}`);
    if (W[b.weight] !== d.weight) fail(`type.weight.${name}`, `type ${name}: design weight ${d.weight}, code ${W[b.weight]}`);

    // letter-spacing: the design's `tr` column is in em ("0", ".005em", ".02em")
    const wantTr = d.tr === '0' ? 0 : parseFloat(d.tr);
    if (!appliesTracking) {
      fail(`type.tracking.${name}`, `type ${name}: vastuTypography() never applies letterSpacing`);
    } else {
      const gotTr = tracking[b.role];
      if (gotTr === undefined) fail(`type.tracking.${name}`, `type ${name}: no LatinTracking entry for role "${b.role}"`);
      else if (Math.abs(gotTr - wantTr) > 1e-6)
        fail(`type.tracking.${name}`, `type ${name}: design letter-spacing ${wantTr}em, code ${gotTr}em`);
    }
  }
}

/* ── 7 · contrast, against the design's OWN accessibility audit ──────────────────────────── */
const auditBlock = dsHtml.match(/const auditRows=\[([\s\S]*?)\]\.map/);
if (!auditBlock) drift.push('contrast: cannot find `const auditRows` — floors not verified');
else {
  for (const m of auditBlock[1].matchAll(/\['([^']+)','([^']+)',[^,]+,[^,]+,([\d.]+),'([^']*)'\]/g)) {
    const [, fgTok, bgTok, floorS, use] = m;
    const fg = impl[field(fgTok.replace(/^color-/, ''))];
    const bg = impl[field(bgTok.replace(/^color-/, ''))];
    if (!fg || !bg) continue;
    const r = contrast(fg, bg), floor = parseFloat(floorS);
    if (r >= floor) continue;
    if (KNOWN_ACCENTS.has(fgTok)) {
      constraints.push(`${fgTok} on ${bgTok} = ${r.toFixed(2)}:1 (floor ${floor}) — design-declared ACCENT: ` +
        `never let it carry meaning alone; pair with a word, glyph or position. [${use}]`);
    } else {
      drift.push(`contrast: ${fgTok} on ${bgTok} = ${r.toFixed(2)}:1, below its ${floor}:1 floor (${use})`);
    }
  }
}

/* ── report ──────────────────────────────────────────────────────────────────────────────── */
console.log('── design fidelity ────────────────────────────────────────────');
console.log(`contract: ${Object.keys(tokens.color).length} colours · ${Object.keys(tokens.space).length} spacings · ` +
  `${Object.keys(tokens.radius).length} radii · ${Object.keys(tokens.elevation).length} elevations · ` +
  `${Object.keys(tokens.border).length} borders + the type ramp + the contrast audit`);

if (accepted.length) {
  console.log('\nDECLARED DEVIATIONS (reviewed, allowed):');
  accepted.forEach((a) => console.log('  · ' + a));
}
if (constraints.length) {
  console.log('\nUSAGE CONSTRAINTS (accent colours below their floor by design):');
  constraints.forEach((c) => console.log('  ! ' + c));
}
if (drift.length) {
  console.log(`\n❌ ${drift.length} DRIFT(S) FROM THE DESIGN CONTRACT:`);
  drift.forEach((d) => console.log('  ✗ ' + d));
  console.log('\nFix the code, or — if the deviation is deliberate — add it to ACCEPTED with a reason.');
  exit(1);
}
console.log('\n✅ implementation matches the design contract');
