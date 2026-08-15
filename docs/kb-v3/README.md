# Vastu Knowledge Base — the expert-review document

The document sent to practising Vastu consultants for review. It is written as a **fresh statement
of the rule set as it stands**, not as a record of what changed — a reviewer should read the rules
as the app applies them today, with no commentary from us about how they got there.

The published PDF is `Documents/VastuFirst-KnowledgeBase-for-Expert-Review.pdf` (70 pages).

## The files

| File | What it is |
| --- | --- |
| `body.html` | The hand-written rule sections and every "Please rule" box, plus the whole stylesheet including the print rules. One placeholder marker is filled by the generator. |
| `copy.json` | **Every user-visible string in the app** — 865 across 63 screens, each with the scenario that puts it on screen. Swept from source, not typed by hand. |
| `build_kb.py` | Assembles the two into `kb-v3.html`. Plain Python 3, no dependencies. |
| `kb-v3.html` | The generated document. |

## Rebuilding it, and making the PDF

```
python docs/kb-v3/build_kb.py
```

Then print it. Chrome's headless printer honours the `@page` and `@media print` rules in
`body.html`, which is what produces the page breaks before each section:

```
chrome --headless=new --disable-gpu --no-pdf-header-footer --print-to-pdf=out.pdf file:///ABSOLUTE/PATH/kb-v3.html
```

## ⚠ Two things that make this document lie if you are not careful

**`copy.json` is a snapshot, not a live read.** It goes stale the moment any screen's wording
changes. Re-sweep before sending this to anyone. A review document that quotes wording the app no
longer uses is worse than no document, because the reviewer's corrections then apply to text nobody
will ever see. Two lines had already drifted between the sweep and the first PDF.

**The rule tables are hand-written from the rule data.** They were last checked against ruleset
version `2026.08.15-1` (the stamp lives in `rules/src/main/resources/ruleset/meta.json`). If that
has moved, re-check the tables against `rooms.json`, `defects.json`, `disputes.json`, `zones.json`,
`doorPadas.json` and `remedies.json` before the document goes out.

## What the reviewer is actually being asked

Eleven boxed questions, plus a comment column on every table. The ones that change real scores:

- **Where the count of the 32 door positions begins.** We anchor the north side's eight from
  348.75°, a third of a side clockwise of the wall. A door in the middle of a north wall therefore
  resolves to an unfavourable position rather than a favourable one, and the door is the
  heaviest-weighted element in the whole reading.
- **Whether a room printed `BATH` should be judged like one printed `TOILET`.** Today it carries no
  rule at all, while `TOILET` in the north-east is the gravest fault the app reports.
- **Which door positions the condemned south-west corner fault should cover.** Our arc holds Yama
  and Gandharva; Pitra sits outside it.
- **Whether the open centre should apply to every room.** Today six rooms are faulted for standing
  on it and the rest are not.
