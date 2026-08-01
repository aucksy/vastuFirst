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

**Latest build to test:** v0.5.0 —
[download the APK](https://github.com/aucksy/vastuFirst/releases/download/v0.5.0/vastufirst-v0.5.0.apk)

Everything else about the app is checked automatically on every build — the maths, the layout at
small screens and large fonts, the score, and screenshots of every screen. What's left below is the
handful of things a machine genuinely cannot judge: how a drag *feels*, whether a buzz actually
happens, what a screen reader says out loud, and whether Android killing the app loses your work.

**You don't need to do these in one sitting, or in order.** Tick what you get to. Anything you don't
reach stays on the list for next time.

### ⚠ Installing this version — one last uninstall

Up to v0.3.16 every build we published carried a different signature, which is why Android made you
uninstall each time, and why your saved homes kept disappearing. **From v0.3.17 onward builds install
straight over each other and your homes are kept.** Moving *to* v0.3.17 still needs one uninstall,
because the old signature can't be recovered — that is the last one.

- [ ] **Install v0.3.17 over v0.3.18 when it arrives, without uninstalling.** Draw or scan a home, save
      it, then install the next build I send. Your homes must still be there. If Android says "App not
      installed" or you have to uninstall again, tell me — the check that is supposed to prevent it
      failed.

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
- [ ] **E5 · The score should be read out as "score 4.7 out of 10"** — the number *and* the scale, in
      one phrase. Three places say it: the compass screen, the big number on your result, and the
      picture of your home with its zones. None of them should still say "out of 100", and none
      should read out a bare number with no scale. *(v0.3.23)*

## F · Quick looks (no interaction needed)

- [ ] **F1 · The corner dots.** Small solid dot with a pale ring. Do they read as "grab me"? The
      earlier large hollow rings looked clunky. *(v0.2.9)*
- [ ] **F2 · The compass dial's arrow** points outward from the middle, up when North is at the top.
      *(v0.2.9)*
- [ ] **F3 · Fresh install → your list of homes** should be a friendly empty state, not a blank
      screen. *(v0.2.9)*
- [ ] **F5 · Scroll the "Add a room" row all the way to the right.** Every kind should be there —
      Entrance, Corridor, Utility, Bathroom, Guest, Courtyard, Garage, Basement after the familiar
      eleven — and it should match the list you get when you change a room you've already placed.
      Tell me if scrolling that far to reach Corridor is annoying enough to change. *(v0.3.24)*
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

## I · Room shapes matching the plan  *(new in this build)*

Where your plan prints a room's size beside its name, the app now shapes the room to that size
instead of to the AI's guess.

- [ ] **I1 — ⭐ Re-scan your Gurgaon plan.** The lobby and the kitchen should now come out **taller
      than wide**, matching how the plan draws them. Before this build they were flat wide bars.
- [ ] **I2 — Relative sizes.** The biggest room on the plan should now be the biggest on the grid, and
      a small toilet should look small. Previously a toilet could be drawn as wide as a kitchen.
- [ ] **I3 — A plan with no sizes printed on it.** Should look exactly as it did before — this only
      does anything where the plan actually prints the numbers.
- [ ] **I4 — Rooms marked "CHECK".** Where a room's true shape won't fit where the AI placed it, the
      app keeps the room and marks it rather than dropping it. Does that read as helpful or confusing?
- [ ] **I5 — Millimetre plans.** Many plans print sizes like "2950X4200" rather than feet and inches.
      If you have one, check those rooms are shaped sensibly too.

## H · Changing what kind of room something is  *(new in this build)*

The app reads room names off your plan well, but not perfectly — and until this build there was no
way to tell it that it got one wrong. Now you can. Two places, and both are worth trying.

- [ ] **H1 — ⭐ On the list right after a scan.** Scan a plan, then tap any room in the list. It should
      open a set of room types underneath it; pick a different one and the row should update. Is
      tapping the row obvious enough, or did you have to hunt for it? The word "Change" sits on the
      right of each row.
- [ ] **H2 — ⭐ On the plan itself.** Tap a room on the grid, then use "Change room type" in the panel
      that appears. The room's colour and name on the plan should change, and nothing should move.
- [ ] **H3 — Nothing else may move.** This is the one to be fussy about. After changing a room's kind,
      the room must stay exactly where it was and the same size, every other room must be untouched,
      and the little **D** for your front door must stay put. If anything shifts, tell me — that would
      be a real bug and the automated checks say it cannot happen.
- [ ] **H4 — The score should change.** Change a room to something quite different (a corridor to a
      living room, a bedroom to a master bedroom) and carry on to the score. The number should move.
      If it does not, something is not reaching the score and I need to know.
- [ ] **H5 — Backing out.** Open the list of room types and pick the one it already is. It should just
      close and change nothing — that is the way out if you opened it by accident.
- [ ] **H6 — ⭐ The rooms that were previously impossible.** Try setting a room to **Entrance**,
      **Corridor**, **Utility**, **Bathroom**, **Guest**, **Courtyard**, **Garage** or **Basement**.
      These eight were never in the "add a room" list, so before this build a room read as one of them
      could be deleted but never put back. They should all be there now.
- [ ] **H7 — Is the list too long?** There are nineteen kinds, and they wrap over several lines. On
      your phone, is that fine, or does it feel like too much? I can group or shorten it.
- [ ] **H8 — With big text.** If you use a large font size on your phone, check the list of types
      still fits and nothing is cut off.
- [ ] **H9 — Rotating mid-change.** Open the list of room types, then rotate the phone. It should still
      be open, on the same room.

## G · Scan your plan

> ⚠ **Everything below changed in v0.3.16.** Up to and including v0.3.15 the app did
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
      the plan actually prints. We rename some on purpose: SIT-OUT becomes "Balcony", LOUNGE becomes
      "Living", STUDY-ROOM becomes "Study", a servant's room becomes a bedroom. Dressing areas, ducts
      and lifts are dropped on purpose and listed under "we also saw". Are any of those wrong for how
      your customers talk?
- [ ] **G15 — "Lobby", both ways round.** This one now depends on the rest of the plan. On a plan that
      names no living room, a lobby is read as the **living room** (your Gurgaon plan). On a plan that
      already has a living room, a lobby stays a **corridor**, because there it really is a passage.
      Either way it is marked "CHECK" for you. Try one of each if you have them, and tell me if the
      rule ever picks wrong.

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

## J · The plan filling the drawing area, and room names  *(new in this build)*

Two things you reported, and one you asked me to make production-grade.

- [ ] **J1 — ⭐ Re-scan the Green Court 2BHK.** Your home should now fill the drawing area instead of
      sitting in one corner at about half size. The empty margin on the left should be gone.
- [ ] **J2 — ⭐ Small rooms keep their names.** The narrow toilet should now show the word "Toilet"
      running down the room, the way a printed plan labels a narrow space. Does that read naturally
      to you, or does it look odd on a phone?
- [ ] **J3 — Nothing lost.** Count the rooms on your plan against the rooms on the grid. Small rooms
      used to disappear silently when they were drawn too small; that should now be impossible.
- [ ] **J4 — The tag no longer covers the top row.** Tap a room: the little dark label with the room
      name and direction should NOT appear over the plan any more. It only shows while your finger is
      actually moving a room, and it jumps to the bottom if you are dragging something in the top row.
      Is the information still there when you want it?
- [ ] **J5 — Faint text.** The small grey lettering (NORTH/WEST/SOUTH/EAST, MOVE, SIZE) and the arrow
      buttons should be a little easier to read. Check in daylight and at your usual brightness.
- [ ] **J6 — Very large text.** If you use a big font size, check room names still fit or drop out
      cleanly rather than being cut off mid-word.
- [ ] **J7 — A plan with no printed sizes.** Should behave as before; this build only changes how big
      the home is drawn, not how the rooms are read.

---

# ⭐⭐ v0.5.0 — READ THE REPORT. That is the whole test.

This build changed one thing: what the ₹699 report actually says. No screenshot check can tell us
whether it reads well to a person, so this section is a reading job rather than a tapping job.

**How to get there in one minute:** open the app → **Try a sample home** → the score screen → **See
the full report**. The unlock is one tap and free in this build, so nothing is behind a wall.

- [ ] **R1 — ⭐⭐ Read the whole report top to bottom, once, as a customer.** Not looking for bugs —
      just: *would you pay ₹699 for this?* Tell me the first place your attention drops, and the first
      sentence you would have to read twice. Those two answers are worth more than everything below.
- [ ] **R2 — ⭐⭐ Are the reasons believable to somebody who knows Vastu?** Every problem now explains
      which direction it is about, which deity and element the tradition gives that direction, and why
      a room of that kind does not belong there. **If any one of them reads as wrong, or as made up,
      tell me the exact sentence** — that is the one thing in this build that would damage trust, and
      every line is one edit away from being changed.
- [ ] **R3 — ⭐ The "not ideal" section — is it clear these are not faults?** This is the section that
      did not exist at all before. It says plainly that nothing in it is a defect, but they do count
      towards the score. Does that land, or does it read as a second list of problems?
- [ ] **R4 — Does the front-door section make sense?** It names one of 32 traditional door positions
      and what the tradition attaches to it. Is that interesting, or is it a paragraph you skipped?
- [ ] **R5 — Is the report too long to read on a phone?** It is now much longer than it was, on
      purpose. Scroll it on your own phone: does it feel like a document worth paying for, or like a
      wall of text? If it needs breaking up, now is the time to say so.
- [ ] **R6 — At the size you actually read at.** If you use large text on your phone, or if your
      client will, check nothing is cut off and no line runs off the side. Machine checks cover this,
      but they cannot tell whether it is comfortable to read in daylight.
- [ ] **R7 — Does the free screen promise what the report delivers?** On the score screen, the unlock
      card lists what you get, with real numbers off that home. Open the report and check each line is
      really there. If it promises something the report does not have, that is a serious one.

---

# ⭐ v0.4.1 — the checks that actually matter before you hand this over

Everything below is something only a real phone can settle. The three marked ⭐⭐ are the ones I would
not hand over without; the rest are worth ten minutes if you have them.

## K · The three that matter most

- [ ] **K1 — ⭐⭐ Does the compass point the right way in your actual flat?** Open a home, go to
      "Which way is North?", tap **Use my phone's compass**. Hold the phone flat and turn until its
      top edge points the same way as the top of your drawing. Press **Set North from this**. Now look
      at the card above the button: it will say something like "your front door is on the west side,
      and your kitchen is in the south-east". **Is that actually true of your flat?** If it is, the
      compass is right. If it is ninety degrees out, tell me immediately — that is the one thing in
      this build that would put confident wrong directions on every room.
      *(Stand away from a fridge, a steel almirah or a laptop; they bend any phone compass.)*
- [ ] **K2 — ⭐⭐ Does a half-drawn home survive?** Start a new home, place three or four rooms, then
      press Home and leave the phone alone for an hour — or open a dozen other apps to push it out of
      memory. Come back. You should land on your rooms, with a card at the top saying "We kept the
      home you were drawing" and a button to start again. **If the grid is empty, that is the bug this
      build exists to fix and I need to know.**
- [ ] **K3 — ⭐⭐ Does this build install straight over the last one?** Install v0.4.1 on top of
      v0.3.24 **without uninstalling**. Every home you had saved must still be there, with the same
      scores. If Android refuses to install, or your homes are gone, stop and tell me.

## L · The L-shaped home — the big accuracy change

- [ ] **L1 — Leave a corner empty.** Draw a home but leave one corner of the grid with no room on it.
      A card should appear under the plan: "Is this part of your home?", with that corner marked on
      the plan. Is it obvious which part it is pointing at?
- [ ] **L2 — Say no.** Press "No — my home is cut off there". The corner should be crossed out and the
      home's real outline drawn as an L, and the card should say which direction it is cut off in.
      Then look at the score: it should have gone **down**, and the report should now mention a
      missing corner. *(That drop is the point — the old score was too generous.)*
- [ ] **L3 — Say yes.** On another home, press "Yes — it's part of my home". Nothing should change at
      all, and it should stop asking.
- [ ] **L4 — Change your mind.** After cutting a corner, press "Start the shape again". The home should
      go back to a full rectangle and ask again.
- [ ] **L5 — A gap in the middle.** Leave an empty square surrounded by rooms on all four sides. It
      should NOT offer to cut it out; it should say it's inside your home and suggest adding a
      Courtyard. Does that read sensibly?
- [ ] **L6 — Does it get in the way?** Draw a normal, gap-free home. You should see only a quiet grey
      line about treating it as a rectangle — no card, no interruption. Tell me if it nags.

## M · The rest

- [ ] **M1 — The double-check on North.** Even without using the compass, the card above "Read my home"
      should describe where your door and rooms ended up. Is it worded in a way you'd trust?
- [ ] **M2 — A few more things.** From the score screen, press "Answer a few more and check more".
      Answer the water-tank question with a direction and go back. The score should change and the
      "What this covers" line should now count what you told it. Are the four questions clear?
- [ ] **M3 — "There isn't one".** Answer one of them with "There isn't one". That should count as
      answered, not skipped.
- [ ] **M4 — A room on the line.** Select a room sitting near a boundary between two directions. Its
      line under the name should read something like "2 × 2 · North-West, close to North". Does that
      help, or is it noise?
- [ ] **M5 — The unlock screen.** Open it. It must say plainly that **no payment is taken** and the
      button must say "free". ⚠ **If anything on that screen looks like it would charge you, stop and
      tell me** — that is the one thing this build must never do.
- [ ] **M6 — Privacy.** Settings → Privacy. Read it as a customer would. Is anything on it something
      you would rather not promise?
- [ ] **M7 — Delete everything still works.** Settings → Delete all my data. Confirm your homes go.
      *(Do this last, obviously.)*
- [ ] **M8 — Big text.** Turn your phone's font size right up and walk the whole flow. Nothing should
      be cut off mid-word or unreachable.
- [ ] **M9 — In daylight.** Take the phone outside and read the score screen and the shape card at
      your usual brightness.

## N · Only if you have the patience

- [ ] **N1 — Crash reporting.** There is no way to force a crash on purpose, so this is only checkable
      if one happens. If the app ever closes by itself, open it again, go to Settings, and there
      should be a "Something went wrong" section offering to email me what happened. Please do send it.
