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

## ⚠ The one ruling we have deliberately NOT applied

### W-12 · Where the pooja room belongs

**The classical position is the centre** (the family deity holds the Brahmasthan, per Manasara).
**Modern practice is near-universally the North-East.** Both are seriously held, and they contradict
each other about the most important square in the home.

**What we have done: nothing, on purpose.** The pooja room is currently *not scored at all* and both
readings are shown to the reader.

**Why not just rule it.** Applying either reading would immediately start scoring every pooja room —
which changes the score of **every home already saved on a phone**, including the worked example the
whole engine is checked against. That is not a change to make without the owner saying so.

**Our recommendation if you want it ruled: the modern North-East.** It is what a customer in India
will have been told by a consultant, and the classical reading puts a room in the very square the same
tradition says must stay open and unbuilt — which is hard to defend to a reader who knows that.

**It is one line.** Say the word and the next build carries it. We would also, in that build, re-score
every saved home and tell the user their score changed and why, rather than letting it move silently.
