# The disputed Vastu rulings — the position VastuFirst takes, and why

**Decided 1 August 2026.** The owner asked for the most widely attested classical position on each
open question. This is that list, with the reasoning, and — for the ones that touch the number — what
the alternative would actually do, measured rather than guessed.

**Every one of these is a one-line change in the rule data.** Nothing here is compiled into the app.
A real Vastu expert can overturn any of them by editing a single line of `rules/…/ruleset/*.json`,
and the app re-scores without a rebuild. That is the whole reason they live there.

**How to overturn one:** send us the ruling in any words. We change the line, the tests re-run, and
the next build carries it. Turnaround is minutes, not a release cycle.

---

## The two that move the score

### M-05 · How big is the centre (the Brahmasthan)?

**Ruling: the central 3×3 block of the nine-square grid.** Shipped as `brahmasthanExtent:
"CENTRAL_3X3"`.

**Why.** This is the Paramasayika mandala as Mayamata and Manasara set it out: a 9×9 grid of 81
squares, with Brahma holding the middle nine. It is what essentially every practising consultant in
India teaches, and it is the reading the rest of the engine's zone map is built on — the eight
directions around it are the other 72 squares.

**Effect on the score: none.** This is the value the app already runs on, so the worked example still
scores exactly the same as it always has.

**The alternative** — a differently sized centre — is deliberately **not implemented**, and the app
refuses to start rather than quietly running a value it cannot honour. If the expert rules for a
different extent, that is engine work, not a config edit, and we would say so rather than pretend.

### M-07 · How is the front door's position decided?

**Ruling: by its direction from the centre of the home.** Shipped as `doorLocationMethod:
"BEARING_FROM_CENTRE"`.

**Why, and this is the strongest reason of the two.** The 32 entrance padas are classically laid
around the edge of a **square** mandala, and for a square the two readings — "direction from the
centre" and "how far along the wall" — give the *identical* answer. They only diverge on a rectangle,
where the texts are silent.

What decides it is a case the texts never had to consider: **an L-shaped home has walls that turn
back on themselves, and "how far along the wall" has no single answer there.** The app already has to
fall back to direction-from-the-centre in that case. Making the fallback the rule means every home is
judged the same way — rather than a rectangle being judged one way and its L-shaped neighbour
another, which is the kind of inconsistency that destroys trust in a report.

**Effect on the score: none, because this is what the app already does.** The alternative reading is
implemented and measured on every build; the figure it produces on the worked example is printed by
the engine's own test rather than written down from memory, so this document cannot drift from the
app.

---

## The eight that are surfaced, not scored

These are shown to the reader as "the schools disagree", with both readings and which tradition each
comes from. None of them silently moves the number.

*(W-12, where the prayer room belongs, was the ninth of these until the owner ruled on it on
1 August 2026. It is now scored — see the section below — and both readings are still shown.)*

| | Question | Ruling | Why |
|---|---|---|---|
| **W-01** | Toilet in the East | **A defect** | The traditional position: East is the sun's quarter. The exception belongs to the 16-zone school, which this app does not use. |
| **W-02** | Kitchen in the North | **Not ideal, shown as such** | Kubera and water against fire. The modern reading calls it merely suboptimal; both are shown. |
| **W-03** | Master bedroom in the West | **Acceptable** | The classical heavy-zone alternative to the South-West. The objection is a 16-zone refinement. |
| **W-04** | Main entrance in the South-East | **Acceptable on the right pada** | ⭐ Chosen for internal consistency: the 32-pada table *is* the classical construction, and it already separates the good padas from the bad ones within the south-east. A blanket ban would contradict the very table the app scores on. |
| **W-05** | Sleeping head direction | **Avoid head-to-North** | The one near-universal agreement across every school. Stated as tradition, with no health claim of any kind. |
| **W-06** | Where the idols face | **Idols on the West or South-West wall, worshipper facing East** | The classical arrangement. |
| **W-09** | Septic tank direction | **No ruling — the sources genuinely contradict** | North, North-West and South-East are each asserted by serious sources. Picking one would be inventing a tradition. Reported as "not checked" unless the user tells us where it is. |
| **W-10** | A road pointing at the plot (Veedhi Shoola) | **Directional** | North-East, North and East are benign; South, South-West, South-East and North-West are the harmful thrusts. The strict school calls every road-arrow harmful; that is a minority reading. |
| **W-11** | Extensions | **A North-East extension is auspicious** | The classical position. The strict school treats all extensions as negative; shown as the second reading. |

---

## ⭐ W-12 · Where the pooja room belongs — RULED BY THE OWNER, 1 August 2026

**This was the last open question, and the only one deliberately left unruled.** The owner ruled for
**the modern North-East** on 1 August 2026. It is applied, and the app scores prayer rooms.

### The ruling as shipped

| | |
|---|---|
| **Ideal** | North-East (Ishanya) |
| **Acceptable** | North, East |
| **Prohibited** | **nothing** |
| **Provenance tag** | `MOD` — 20th-century modern practice, not a classical verse |

**Why the North-East.** It is what a customer in India will have been told by a consultant, and it is
near-universal in modern practice. The classical alternative puts a room in the very square the same
tradition insists must stay open and unbuilt — which is hard to defend to a reader who knows that.

**⭐ Why nothing is prohibited, which is the part that took the thought.** Three reasons, in order of
weight:

1. **It would contradict the report's own facing page.** The classical reading places the shrine at
   the centre. If the centre were prohibited, the app would print "defect" on the exact position it
   shows two lines later as the other school's advice. We are *declining* the classical reading, not
   *condemning* it — and the data has to say the same thing the prose does.
2. **It is the honest severity.** A prayer room in the North-West is not a fault; it is simply not
   where this tradition puts one. It now reads "not ideal", which is what it is.
3. **It needs no new defect definitions.** Every prohibited (room, direction) pair must carry its own
   reason and its own remedies. Inventing those for a room whose placement two schools disagree about
   would mean inventing remedies — the exact thing this product exists not to do.

### Effect on the score, measured rather than guessed

**The worked example still scores 31.** Its prayer room is in the North-West, so it changed from
*not scored at all* to *not ideal* — worth 45 points at weight 2.0. Joining both sides of the weighted
average pulls the base from **47.27 to 47.03**, the penalty is untouched at 16, and the result rounds
to the same 31. The bundled demo home is the same layout, so its score is unchanged too.

**⚠ This is a coincidence of arithmetic, not a design goal, and it does not generalise.** A saved home
with a prayer room in the North-East goes **up**; one in a direction no better than the rest of its
layout goes **down**. Every score in the app is now computed under a genuinely different rule.

**The rotation test still gives the same number at every angle**, which is the check that the change
is a scoring change and not a geometry accident.

### What the reader still sees

⭐ **Both readings remain on the page.** Ruling on a dispute must never stop us saying the tradition is
split. The "where the schools disagree" card still carries the classical centre and the modern
North-East — and it now also carries a third line saying **which reading the number uses**. Showing
both sides while staying silent about where the score stands would be a half-truth.

### And everyone who had already saved a home was told

Saved homes store the rule version they were scored under. This ruling moves that version, so the app
re-runs every affected home and shows the old number, the new number and the reason — in the rule
data's own plain words — **before** anything is written back. A home whose number did not move is
still listed, saying so. The build now refuses a rule dataset that cannot explain what changed in it.
