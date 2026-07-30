<!--
MAINTAINER NOTE — not for the client's email; this one IS for the owner to use on their phone.

⭐ THIS IS THE SINGLE RUNNING LIST of everything that needs a real finger, a real phone, or a real
pair of eyes. It is the canonical list — docs/UAT-GRID-PLAN-BUILDING.md and PHASE-2-PROGRESS.md now
point HERE instead of keeping their own copies, so there is never a second, staler list.

APPENDING IS PART OF "DONE". Every time a build ships something that automated tests cannot prove —
a haptic, a raw gesture, true process-death, a screen-reader phrase, anything whose only proof is
looking at it — add a row here, tagged with the build it arrived in. Never quietly drop a row: once
the owner reports a result, move it to "Settled" at the bottom with their verdict and the date.

Keep every line in plain English: what to DO, what you should SEE. No file names, no class names.
-->

# VastuFirst — manual test list (things only a phone can prove)

**Latest build to test:** v0.3.13 —
[download the APK](https://github.com/aucksy/vastuFirst/releases/download/v0.3.13/vastufirst-v0.3.13.apk)

Everything else about the app is checked automatically on every build — the maths, the layout at
small screens and large fonts, the score, and screenshots of every screen. What's left below is the
handful of things a machine genuinely cannot judge: how a drag *feels*, whether a buzz actually
happens, what a screen reader says out loud, and whether Android killing the app loses your work.

**You don't need to do these in one sitting, or in order.** Tick what you get to. Anything you don't
reach stays on the list for next time.

### How to report a problem so it's quick to fix

Three things, and a screenshot if it's visual:

1. **Which screen** you were on ("the floor-plan builder", "the compass screen").
2. **What you did** — as literally as you can ("held the bottom-right dot of the kitchen and dragged
   it left past the living room").
3. **What happened** vs what you expected.

The most useful sentence you can send is the one that starts *"I tapped X and expected Y, but Z."*

---

## A · Moving and resizing rooms by finger

The whole point of the rework — and the part no automated test can reach, because the tests press
buttons and never actually slide a finger.

- [ ] **A1 · Touch a room and keep sliding.** It should select the moment you touch it and start
      moving in that *same* motion — no extra tap first. *(v0.2.3)*
- [ ] **A2 · Pull each corner dot to resize.** The opposite corner should stay exactly where it is,
      like stretching a picture frame. *(v0.2.3)*
- [ ] **A3 · Pull one corner past the far corner.** The room should stop at one cell and refuse to
      turn inside-out. It must never vanish or flip. *(v0.2.3)*
- [ ] **A4 · Drag a room on top of another.** It should freeze at the last clear spot, the outline
      should turn red, and lifting should leave it in the clear spot — never stacked. *(v0.2.3)*
- [ ] **A5 · Touch the middle of a small (1×1) room and drag.** It must **move**, not resize. This
      was a real bug: the corner dot used to swallow a tiny room's own middle. *(v0.3.8)*
- [ ] **A6 · Rooms pushed right up against a wall.** Their corner dots should sit *on* the corners,
      not floated inwards, and should still be grabbable. *(v0.3.7)*
- [ ] **A7 · Change the plot size, then immediately drag a room.** It should land exactly where your
      finger is. Rooms used to go off-grid after a resize because the app was still using the old
      grid. *(v0.3.7)*
- [ ] **A8 · Rest a second finger on the plan while dragging a room.** Watch whether the page
      scrolls underneath the drag. Standard Android drag behaviour does this too, so if it isn't
      noticeable in practice we'll leave it — I just want to know. *(v0.3.10, curiosity)*

## B · Plot size and the front door

- [ ] **B1 · Press "−" on plot size until it stops.** At the smallest size, one more press should
      give a short **"no" buzz** — the same one you feel dragging a room onto another. It used to do
      nothing at all, which looked like a broken button. *(v0.3.10)*
- [ ] **B2 · Fill the plot with rooms, then press "−".** Same "no" buzz — the app refuses rather
      than squashing your rooms on top of each other. *(v0.3.10)*
- [ ] **B3 · Draw the plot bigger than your rooms, then set the front door.** The little **D** should
      sit on the wall of your *house*, not out in the empty space around it. *(v0.3.9)*
- [ ] **B4 · Move a room so the house gets smaller, with the door already set.** The D should stay
      stuck to the house's edge, and still be there when you reopen the home. *(v0.3.6)*
- [ ] **B5 · Tap each of the four walls in the door step, on a plot bigger than your house.** The
      wall it picks should be the one you aimed at, every time — including when you tap out in the
      empty space *beyond* a wall. This used to measure to the edges of the drawing area, so a tap
      just above your house could come back as a *West* door. **This is the important one to try.**
      *(v0.3.11 — was gap S8)*
- [ ] **B6 · Your house should be outlined during the door step.** A sage line hugging your rooms, so
      "tap the wall" points at something you can see. It's the box the app actually scores, so on a
      home with a gap between rooms it will look larger than the rooms themselves — that's honest, not
      a glitch, but tell me if it reads oddly. *(v0.3.11)*
- [ ] **B7 · A one-room-deep home.** Draw a single wide room, one cell tall, then tap just above it
      and just below it. You should get a north door and a south door respectively — a thin house used
      to only ever accept a north door. *(v0.3.11)*

## C · How it feels (haptics)

Turn phone vibration **on** first — the app deliberately stays silent if you've turned it off.

- [ ] **C1 · Dragging a room:** a soft tick each time the shape jumps a cell. *(v0.2.3)*
- [ ] **C2 · A refused move or resize:** a firmer "no" buzz. *(v0.2.3)*
- [ ] **C3 · A room or door landing:** a confirming tap. *(v0.2.3)*
- [ ] **C4 · Every button, chip and row:** a light tap. *(v0.2.9)*
- [ ] **C5 · The compass dial — a decision for you.** It currently ticks for **every degree** you
      turn. The design suggested a firmer click every **15°** instead. Which do you prefer? Either is
      a one-line change. *(v0.2.9, needs your preference)*
- [ ] **C6 · Is any of it too much?** If the buzzing is annoying anywhere, say where.

## D · Surviving interruptions

- [ ] **D1 · Rotate the phone mid-edit.** Your selected room, the room you were placing, and the door
      step should all still be there. *(v0.3.5)*
- [ ] **D2 · A half-drawn NEW home, then force-stop the app** (Settings → Apps → VastuFirst → Force
      stop) and reopen. **Expected today: the rooms are gone.** That's the known gap "S3" — I want to
      know how bad it feels before deciding whether to fix it. *(known gap)*
- [ ] **D3 · A home you've already scored, force-stopped and reopened.** This one **should** come
      back exactly as you left it. *(v0.3.1)*
- [ ] **D4 · Reopen a saved home where you'd left empty space around the rooms.** The plot comes back
      trimmed to the rooms; your rooms and score are unchanged. That's the known gap "S2" — tell me
      whether the trimming bothers you. *(known gap)*

## E · Screen reader (TalkBack)

Settings → Accessibility → TalkBack. Worth one pass — it's a paid product and this is the part
sighted testing never catches.

- [ ] **E1 · Swipe through the floor-plan builder.** Every control should be announced and usable.
      *(v0.2.3)*
- [ ] **E2 · The front door should say "front door on the *north* wall"** — the word, not the letter
      "N". *(v0.3.10)*
- [ ] **E3 · Place and move a room using only the arrow and size buttons**, no dragging. This is the
      path someone who can't see the screen relies on. *(v0.2.3)*
- [ ] **E4 · Set North with the dial or slider via TalkBack.** It should actually change, not just
      be readable. *(v0.3.3)*

## F · Quick looks (no interaction needed)

- [ ] **F1 · The corner dots.** Small solid dot with a pale ring. Do they read as "grab me"? The
      earlier large hollow rings looked clunky. *(v0.2.9)*
- [ ] **F2 · The compass dial's arrow** points outward from the middle, up when North is at the top.
      *(v0.2.9)*
- [ ] **F3 · Fresh install → your list of homes** should be a friendly empty state, not a blank
      screen. *(v0.2.9)*
- [ ] **F4 · Anything that just looks wrong.** A screenshot is enough — I'd rather chase a vague
      "this looks off" than have you talk yourself out of reporting it.

---

## Known gaps this list is deliberately probing

These are already understood; the tests above exist to tell me whether they matter enough to fix
before 4 August. Full write-ups (with options and costs) are in `docs/UAT-GRID-PLAN-BUILDING.md`.

| | What it is | Test |
|---|---|---|
| **S2** | Reopening a home trims the plot to the rooms; the empty margin you drew isn't remembered. Score unaffected. | D4 |
| **S3** | A brand-new, never-scored home lives only in memory and can be lost if Android reclaims the app. | D2 |
| ~~**S8**~~ | ~~The door step says "tap the outer wall", but your house's outline is never drawn.~~ **FIXED in v0.3.11** — the house is outlined, and the wall a tap means is now measured from your house, not the drawing area. Please confirm with B5–B7. | B5–B7 |

## Settled

Nothing yet — this section fills up as you report results, so we never re-test something twice or
lose a verdict.

## G · Scan your plan

> ⚠ **Everything below changed in the build after v0.3.15.** Up to and including v0.3.15 the app did
> **not** read your picture at all — it replayed four readings recorded on 29 July, and three of those
> four were the same test plan, which is why every upload looked identical. **From v0.3.16 it really
> reads the plan you choose.** Any row you already ticked against v0.3.14 or v0.3.15 was testing the
> wording, not the reading, and G3/G4/G8 in particular are worth doing again.

Tap **Add your home → Upload a plan**. The first time, you'll get a screen explaining that the plan
leaves the phone, and you have to agree before anything is sent.

⚠ **Two plans a minute is the limit** on the free reading allowance. A third one straight away will
politely ask you to wait about a minute, and that is expected, not a fault. Spacing them out avoids
it entirely.

- [ ] **G1 — Picking a file.** "Choose a PDF or picture" opens the file browser; "Take a photo
      instead" opens the photo picker. Neither should ask permission for your gallery.
- [ ] **G2 — A real PDF opens.** Pick an actual PDF of a floor plan. It should get as far as
      "Reading your plan…" rather than "We couldn't open that file". The PDF page is rendered on your
      phone before anything is sent.
- [ ] **G3 — "We read N rooms" (rooms placed).** On a straightforward single-home plan, tap through
      to the grid: the rooms should be sitting there, roughly in the shape of a home.
- [ ] **G4 — ⭐ "We found N rooms" (rooms NOT placed).** This is the one to judge hardest, because it
      is what busy plans will do. The rooms arrive as a row of small single squares in the corner, and
      you drag each into place. **Is that genuinely easier than starting from an empty grid, or would
      you rather it just showed you the list and let you draw?** Your call — it is the main open
      question in this feature.
- [ ] **G5 — The refusal wording.** Now reachable for real: upload a 3D marketing picture of a house
      (the kind on a builder's website). Does "That looks like a 3D picture — please upload the flat,
      top-down plan" say enough for someone to know what to do?
- [ ] **G6 — Is the promise honest?** The whole screen deliberately promises only that we read the
      room *names*, never that we know where the rooms are. Does that read as useful, or as
      underselling? We can say more only if we can back it up.
- [ ] **G7 — Leaving mid-scan.** Start a scan, switch to another app, come back. It's expected to
      return to the beginning rather than resume; tell me if that's annoying in practice.

- [ ] **G8 — ⭐ Room-name translations.** Whichever plan you scan, check the names it read against what
      the plan actually prints. We rename some on purpose: LOBBY becomes "Corridor", SIT-OUT becomes
      "Balcony", LOUNGE becomes "Living", STUDY-ROOM becomes "Study", a servant's room becomes a
      bedroom. Dressing areas, ducts and lifts are dropped on purpose and listed under "we also saw".
      Are any of those wrong for how your customers talk?

### Rows that only exist now the reading is real (v0.3.16)

- [ ] **G9 — ⭐⭐ Different plans give different answers.** The whole point of this build. Scan two
      genuinely different plans, a minute apart. The room list must match each plan — a 3-bedroom
      should not come back looking like a 2-bedroom. **If two different plans ever give the identical
      list again, stop and tell me: that is the old fault, not a new one.**
- [ ] **G10 — Your own plan, honestly judged.** Scan a plan you know well. Two questions, and both
      answers are useful: did it get the room *names* right, and if it placed them on the grid, were
      they roughly in the right places? Names being right while positions are wrong is the expected
      result on busy plans — I want to know how often it happens on *your* plans.
- [ ] **G11 — The privacy screen.** It appears before the first scan only. Read it as a customer
      would: does it say enough, plainly enough? Is anything on it something you would rather not
      promise?
- [ ] **G12 — Turning it off.** Settings now has "Reading uploaded plans online". Tap it to switch it
      off, then go back to Add home → Upload a plan: it must ask you to agree again before it will
      scan. (This is a legal requirement, so it matters that it actually works.)
- [ ] **G13 — Scanning three in a row.** Do three scans quickly on purpose. The third should say
      something calm about waiting a minute, with the option to draw instead — never an error, never a
      dead end.
- [ ] **G14 — A photo taken at an angle.** Photograph a printed plan deliberately tilted. It should
      still read the room names; it will probably hand them to you unplaced. Confirm it doesn't
      pretend to know where they go.
