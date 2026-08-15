# Vastu Knowledge Base — Draft 3.0

The expert-review document, rebuilt on 15 August 2026 by checking every rule in
`Documents/VastuFirst-KnowledgeBase-v2-for-Expert-Review.pdf` against the rule data the app
actually ships, and by sweeping every user-visible string out of the source.

Draft 2.0 described the rules we *intended* to build. This draft describes the rules the engine
*applies*. They were not the same — the clearest gap being that Draft 2.0 showed the reviewer
fourteen problem types where the app applies thirty.

## The files

| File | What it is |
| --- | --- |
| `body.html` | The hand-written analysis: what changed, the corrected rule tables, the open questions. Carries the whole stylesheet. Two placeholder markers are filled by the generator. |
| `copy.json` | **Every user-visible string in the app** — 865 of them across 63 screens, each with the scenario that puts it on screen and a note where we have a concern. Swept from source, not typed by hand. |
| `rewrites.json` | Proposed wording for the lines that promise something the app cannot deliver, contradict another screen, or use a word a home buyer would not. |
| `build_kb.py` | Assembles the three into `kb-v3.html`. No dependencies — plain Python 3. |
| `kb-v3.html` | The generated document. This is the file that gets published. |

## Rebuilding it

```
python docs/kb-v3/build_kb.py
```

It reads and writes alongside itself, so run it from anywhere. It prints the screen, string and
flag counts, which are also printed in the document's own header — if those three numbers move,
the header moves with them automatically.

## ⚠ Keeping it honest

**`copy.json` goes stale the moment a screen's wording changes.** It is a snapshot, not a live
read. Before sending this document to anyone, re-sweep the copy rather than trusting the file —
a review document that quotes wording the app no longer uses is worse than no document, because
the reviewer's corrections then apply to text nobody will ever see.

**The rule tables were checked against ruleset version `2026.08.15-1`.** The version stamp is in
`rules/src/main/resources/ruleset/meta.json`. If it has moved, the tables need re-checking against
the rule files before this goes out.

## What this audit changed in the product

- **Fixed:** the engine scored a north-east extension as a fault — the most auspicious shape the
  tradition recognises, and one the app's own defect text calls "welcomed" in the same report. Only
  the south-west extension had a rule of its own; every other one fell to the catch-all. The
  welcomed zones now live in `config.benignExtensionZones` rather than in Kotlin.
- **Open, needs a Vastu ruling:** a room a plan labels `BATH` carries no rule at all, while the same
  room labelled `TOILET` produces the gravest finding in the app.
- **Open, needs a ruling:** the entrance arc the app condemns holds Yama and Gandharva but not
  Pitra, which is the pada Draft 2.0 named as near-universally condemned.
