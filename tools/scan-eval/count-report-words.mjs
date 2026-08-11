// count-report-words.mjs — how many words the report screen says IN OUR OWN VOICE.
//
// WHY (owner, 11 Aug 2026): *"Report copy shorter and simpler… show me the before/after word
// counts."* A word count is only honest if it counts the same thing each time, so this counts one
// thing precisely: the prose the report screen writes itself.
//
// ⭐ WHAT IT COUNTS — every string literal of three words or more in ReportScreen.kt, which is our
// scaffolding: headings, the sentence under a heading, button labels, the honesty notes.
//
// ⛔ WHAT IT DOES NOT COUNT, on purpose:
//   · ReportText.kt — the Vastu reasoning. That is the product (CLAUDE.md §2e) and cutting it to
//     make a number go down would be cutting the only thing worth ₹699.
//   · anything the engine supplies: rule rationales, defect explanations, remedies, zone meanings.
//   · screen-reader descriptions, test tags, and single- or two-word labels.
//
// Run:  node tools/scan-eval/count-report-words.mjs
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const FILE = join(HERE, '../../app/src/androidMain/kotlin/com/vastufirst/app/ui/report/ReportScreen.kt');
const src = readFileSync(FILE, 'utf8');

// ⚠ Comments are stripped by SCANNING, not by a regex, and the difference is the whole count. A
// line comment in this file often quotes the owner mid-sentence and closes the quote on the next
// line; a per-line regex leaves those fragments behind and they arrive looking exactly like screen
// copy. The first run counted eleven such fragments as report prose.
function stripComments(text) {
  let out = '';
  let i = 0;
  let inString = false;
  let block = 0;
  while (i < text.length) {
    const two = text.slice(i, i + 2);
    if (!inString && block === 0 && two === '/*') { block = 1; i += 2; continue; }
    if (!inString && block > 0) {
      if (two === '/*') { block++; i += 2; continue; }        // Kotlin block comments NEST
      if (two === '*/') { block--; i += 2; continue; }
      i++; continue;
    }
    if (!inString && two === '//') { while (i < text.length && text[i] !== '\n') i++; continue; }
    if (text[i] === '"' && text[i - 1] !== '\\') inString = !inString;
    out += text[i];
    i++;
  }
  return out;
}
const code = stripComments(src);

const strings = [...code.matchAll(/"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1]);
const prose = strings.filter((s) => {
  if (s.includes('$')) { /* interpolated prose still counts */ }
  if (/^report\./.test(s)) return false;              // test tags
  const words = s.trim().split(/\s+/).filter(Boolean);
  return words.length >= 3;
});

// Screen-reader text is addressed by `contentDescription = "…"` — excluded by name.
const a11y = new Set(
  [...code.matchAll(/contentDescription\s*=\s*"((?:[^"\\]|\\.)*)"/g)].map((m) => m[1]),
);
const counted = prose.filter((s) => !a11y.has(s));

const wordsOf = (s) => s.replace(/\$\{[^}]*\}/g, 'X').replace(/\$[A-Za-z.]+/g, 'X')
  .trim().split(/\s+/).filter(Boolean).length;

let total = 0;
counted
  .map((s) => [wordsOf(s), s])
  .sort((a, b) => b[0] - a[0])
  .forEach(([n, s]) => { total += n; console.log(String(n).padStart(3), s); });

console.log(`\n${counted.length} sentences · ${total} words of our own prose on the report screen`);
