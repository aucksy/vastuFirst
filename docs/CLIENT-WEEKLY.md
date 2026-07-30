<!--
MAINTAINER NOTE — not for the client. This file is the single source for Simran's Sunday
progress email, so it must stay in plain English: outcomes, not mechanics. No file/class names,
no code, no build/release jargon (those go in docs/PROGRESS.md). Update it as part of "done":
whenever a build is released, a phase item closes, a decision lands, or we hit a blocker.
Newest week at the top. Keep the "What we need from you" section current — it's the most useful
part for the client.
-->

# VastuFirst — Weekly progress for Simran

Your Vastu home-scoring app: draw or pick a home, mark which way it faces, and get a Vastu score and a report. It works fully offline, and the score is worked out on the phone itself.

**Target delivery: Tuesday, 4 August 2026.**

You can install and try the latest version on an Android phone at any time — just ask for the current link, and the newest build is always ready.

---

## 🙋 What we need from you

These are the things only you can decide, and they need to be settled before the 4 August delivery. Nothing here is urgent-this-minute, but they're the last open questions.

1. **How should oddly-shaped (L-shaped or notched) homes be handled?** Right now the app treats every home as a full rectangle. For a home with a missing corner, that makes the score a little too generous. Two options: (a) we add a simple step where you trace the real outline, or (b) we clearly label the report "based on a rectangular shape" so the reader knows. Option (b) is quick and safe for 4 August; option (a) is more work.
2. **Confirm the report price.** We currently show **₹699** for the paid report. Please confirm or change it.
3. **The eight expert Vastu rulings.** There are eight points where Vastu experts disagree. We need your ruling on each so the score reflects your chosen tradition. Once you decide, they're quick for us to apply.
4. **Should the free score carry a short honesty label?** e.g. "based on your rooms, door and shape." It sets fair expectations before someone pays for the full report.
5. **Two quick "please glance at it on your phone" checks:** (a) do the little corner handles for resizing rooms look right to you, and (b) when you drag the North dial, it currently gives a small buzz for every degree — do you like that, or would you prefer a firmer click every 15 degrees? (Either is a one-line change.)

---

## Week of 28 July – 3 August 2026

### Thursday 30 July — the app now actually reads your plan

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.3.16/vastufirst-v0.3.16.apk

Up to this point, "upload a plan" was a working screen in front of a switched-off reader: it replayed
a few readings we had recorded during testing, so whichever plan you picked, you saw the same rooms.
That is now connected properly. Upload a plan and it reads **your** plan.

Three things worth knowing:

- **It asks first.** Before the very first plan is read, a short screen explains that the picture
  leaves the phone, who reads it, that it is only asked to read the room names, and that we keep no
  copy. Everything else in the app — drawing, the score, the report, your saved homes — still happens
  entirely on the phone. You can switch plan reading off again at any time in Settings.
- **Two plans a minute.** The free reading allowance is small. A third plan straight away is asked to
  wait about a minute; drawing by hand always works instantly. This is fine for your testing and will
  need a paid allowance before real customers use it.
- **A plan costs about 15 paise to read**, unchanged from last week's estimate.

**Still true, and worth repeating:** it reads room *names* very well and often does not know *where*
the rooms are. On a straightforward house plan it places them for you to check; on a busy apartment
sheet it hands you the list and you place them. It will never guess your front door — you tap that
yourself, because that single choice moves the score more than anything else.

Work started on **"scan your plan"** — upload a photo or PDF of your floor plan and have the app read
the rooms for you, instead of drawing them all by hand. This week built and tested the part that
turns what the AI sees into rooms on your grid. It is not yet something you can tap in the app; that
comes next.

### What we learned by testing it on 30 real plans

We tested the AI against 30 genuine Indian floor plans rather than trusting the sales pitch, and the
result shapes the whole feature:

- **The AI is very good at reading room *names*.** It correctly read things like "BEDROOM 6750X4350",
  "ATT. TOILET", "SER ROOM" and "PUJA" — including the abbreviations Indian plans use.
- **Knowing *where* the rooms are depends on the plan.** On a straightforward house plan it is good.
  On a busy apartment floor plate it is not. (See the correction further down — I got this wrong at
  first and had to go back and check it properly.)

**So the app does the honest thing:** it reads your plan and tells you which rooms it found. Where it
can also tell where they go, it places them and you check them. Where it can't, it hands you the room
list and you place them. Either way it removes the two slowest parts of the job — working out the room
list, and hunting each room type out of a list — and it never pretends to a precision it doesn't
have.

**Three things it will politely refuse, with a clear reason:**

- a **3D picture** of the home instead of a flat top-down plan — one upload in five turned out to be
  this, and it would have produced a confident but wrong answer
- a plan with **no room names** on it
- a sheet with **several different homes** on it — we'll ask you to crop to one

**The front door stays yours to place.** We tested this specifically: AI door-detection is right well
under half the time, and the front door is the single biggest influence on the Vastu score. Letting the
computer guess it would quietly corrupt the number people pay for. You'll keep tapping the wall
yourself, exactly as you do today.

### What we did about cost

A scan costs about **15 paise**. Against a ₹699 report that is negligible. One caution for later: the
free tier we're testing on only allows about **three scans a minute across all users**, which is fine
for your testing but must be upgraded before public launch.

### ⭐ You can try it on your phone now

"Upload a plan" is live on the **Add your home** screen — it's the first option, above drawing by
hand. Pick any picture or PDF and you'll see one of three plan readings we recorded from the real
service, so the whole journey works end to end.

**One thing to be clear about:** it isn't reading *your* picture yet. Connecting it to the real
service needs two things settled first — a privacy consent step (a floor plan counts as personal
information under India's data protection law, and this is the first feature that would send one off
your phone), and a decision about where the service key lives. Both are next.

**The question I'd most like your opinion on.** When we can't tell where
the rooms are — which will be most plans — we drop them onto the grid as a row of small squares for
you to drag into place. Is that genuinely easier than starting from an empty grid, or would you
rather just see the list of rooms and draw it yourself? Your call.

### ⭐ Correction, after you pushed back — it places rooms better than I said

You asked whether we really can't place the rooms. Checking that properly found I had been judging it
the wrong way, so here is the corrected picture.

I had the app decide "can we place these rooms?" using how much of the home's floor area the rooms
added up to. That turns out to be the wrong question. I drew the AI's answers back over 6 of your real
plans and looked at them:

- On a **straightforward house plan**, it is genuinely good. On one of your plans it put **all eleven
  rooms in exactly the right place**. On another, seven of ten.
- On a **busy apartment floor plate** — 17 to 25 rooms, full of ducts, shafts, lifts and lobbies — it
  falls apart, and no amount of tuning fixes that.

The measure I had been using couldn't tell those two apart. Two of your plans scored *identically* on
it: one placed perfectly, the other was a mess. So the app now decides on **how many rooms the plan
has** instead — few enough to be one home, and we place them; too many and it's a whole floor of
flats, so we hand you the room list instead.

**The result: roughly 4 in 10 of your plans now get their rooms placed for you, up from about 1 in 4 —
and, more importantly, it's the right ones.** It was previously throwing away that flawless eleven-room
read.

⚠ Two honest caveats. This rests on 6 plans looked at by eye, not 24 — your free AI allowance ran out
partway through (it told us to wait ten minutes), which is more evidence that we'll need the paid
plan before launch. And a badly angled *photo* of a simple plan will still be trusted when it
shouldn't be; nothing we can measure catches that one, which is exactly why you always confirm the
rooms yourself before anything is scored.

**One more thing worth knowing.** Nearly every room name on your plans has its real size printed next
to it — "BEDROOM 3300X4200". The AI reads that text perfectly. Using those printed sizes instead of the
AI's guesswork is the single biggest improvement still available, and it's about a day's work.

### Built and proven this week
- The whole "AI reading → rooms on your grid" conversion, with **55 automated tests** plus a stress
  test that throws **100,000 randomly broken AI replies** at it and checks nothing illegal ever reaches
  your screen.
- We deliberately broke each safety check one at a time to confirm it actually catches what it claims
  to — seven of seven did.
- **Your existing drawing tool is untouched.** It's the screen the scan hands its rooms to, and it stays
  the fully offline option for anyone who doesn't want to upload anything.

⚠ **Note on timing:** scanning was originally scheduled for the 10–28 August stage, and was pulled
forward at your request. The 4 August delivery (the app plus the Vastu engine) is already built and
shipped.

---

## Week of 21–27 July 2026

This was the first full build week, and a lot became real and testable. The app now runs end to end: you can add a home, draw its rooms, say which way North is, see a free score, and open a paid-style report — and your homes are saved so you can reopen and compare them.

### What you can now see and try
- **The whole journey works, start to finish.** Add a home → draw the rooms on a simple grid → mark which way it faces → see the Vastu score → open the report → and it's saved to your list of homes.
- **Drawing your floor plan is finger-friendly.** Place rooms on a grid, then drag them around and resize them by their corners, one-handed.
- **You can set your home's real proportions** — for example a plot that's wider than it is deep — so the drawing matches real life instead of always being a square.
- **The compass screen is the centrepiece and now feels smooth.** Spin the dial to set North, and the score updates live as you turn it.
- **Every home has its own name and shows when you last touched it.** New homes are named "Home 1", "Home 2"… and you can rename any of them to something meaningful like "Dwarka flat" — so comparing two homes actually makes sense. Each home also shows when you last changed it ("Updated today", "Updated 2 days ago").
- **The app is polished and honest.** It fits every screen size properly, has its own icon, gives gentle taps of feedback as you use it, and never shows a scary error — if a plan is too sparse to read, it gently asks for a bit more instead.

### Problems we found and fixed this week
- **A scoring bug that could quietly skew the result.** When you shrank the plot, two rooms could end up stacked on the same spot, and the score counted the hidden one twice. Fixed — rooms now always keep their own space.
- **A dead end for first-time users.** After finishing, a first-timer had no way back to their saved homes without closing the app. Fixed — there's now a clear way back.
- **Edits that could silently vanish.** Reopening a saved home, changing it, and going back could lose the change. Fixed — your edits are now kept automatically.
- **An endless "reading your home…" screen** in a rare case. Fixed — it now offers a way out instead of spinning forever.
- **The compass labels collided at large text sizes**, and a colour key word was cut in half. Fixed — everything stays readable however large you set your phone's text.
- **A door placed past the edge of your rooms could shift position when you reopened the home.** Fixed — it now sits firmly on the edge of the house and stays there.

### We stress-tested the plan builder, on purpose

You mentioned seeing too many little issues when building a home on the grid, so we did a thorough, deliberately-harsh test of **everything a person can do there** — placing, moving, resizing, removing rooms, changing the plot size, and setting the door — trying the awkward combinations, not just the easy path. Most of it held up well, and we now have automatic checks that will catch it if any of these ever break again. Two real issues turned up, and both are fixed in this build:
- **A dead-end button on the empty grid.** Before you'd placed any room, the app still offered "Set the front door" — but tapping the wall did nothing, because there's no house yet. It now appears only once you've placed a room.
- **The door could drift after you deleted or moved a room.** If removing a room made the house smaller, the door stayed where it looked, but was actually counted somewhere else and jumped when you reopened. Now the door always stays glued to the edge of your actual house.

We also confirmed some good news the hard way: **your score never changes just because of where on the grid you draw the home** — only the rooms, the door and the shape matter, exactly as intended.

### One more fix, from a practice version of the plan builder

To keep testing the plan builder without needing a phone every time, we built a version of it that runs on a computer screen and behaves exactly like the phone — same drawing, dragging, resizing and door behaviour. Trying it and looking closely turned up one more thing worth fixing: **the front-door marker was sitting on the edge of the drawing area instead of on the wall of your actual house.** If you drew the plot a bit bigger than your rooms, the little door dot floated off in the empty space above or beside the house — even though it was always counted correctly on the house itself. Now the door dot sits right on the wall of your house, matching where it's counted and where it comes back when you reopen the home. It's in the latest build.

### The front door now goes on the wall you actually tapped

This was the most worthwhile fix of the week, because the front door counts for more in the Vastu score than anything else in the app.

**What was wrong.** When you tapped a wall to place your front door, the app worked out which wall you meant by measuring to the edges of the *drawing area* — not to your actual house. So if you'd drawn the plot bigger than your rooms (which the app does by default), tapping just above your house could give you a **west** door, because the drawing area's left edge happened to be closer than its top edge. The door then counted on that wall. You'd have had no way of knowing.

**What's fixed.**
- **The wall is now worked out from your house**, never the drawing area. Tap above your house, you get a north door. Tap out in the empty space beyond a wall, you get that wall. The part of the app that makes this decision no longer even knows how big the plot is, so this can't come back.
- **Your house is now outlined during the door step**, and the instruction says so: *"Your home is outlined below. Tap the wall where your main entrance is."* Before, you saw a few separate rooms floating on a grid and had to guess where "the outer wall" was.
- **A one-room-deep home can now take a south door.** Previously a house only one cell tall always ended up with a north door, because the two walls were too close together for the app to tell them apart.

**One thing worth knowing:** the outline is drawn as a rectangle around all your rooms, because that rectangle is exactly what the app scores. If your rooms have a gap between them, the outline will look bigger than the rooms do. That's not a glitch — it's showing you honestly how the app is reading your home, which is the same "we treat every home as a rectangle" point that's still awaiting your decision at the top of this page.

### Two buttons that were quietly not working, and a screen-reader slip

We kept testing the plan builder on the computer version and fixed three more things:

- **The plot-size buttons sometimes did nothing, with no explanation.** If your rooms already filled the plot, or you'd reached the smallest or largest plot the app allows, pressing "−" or "+" simply had no effect — no message, no buzz, nothing. It looked like a broken button. Now the phone gives a short "no" buzz, the same one you already get when you try to drag a room on top of another, so you can tell the difference between "that didn't work" and "the app is stuck".
- **The front door didn't say which wall it was on properly.** For someone using Android's screen reader, the door was announced as "front door on the N wall" — just the letter. Everywhere else in the app we say "North-East", "South" and so on, so this now reads "front door on the **north** wall".
- **We added a picture test for the door fix from last time.** The door-on-the-house fix had no automatic picture check behind it, because the sample home we photograph fills the whole grid — so a photo couldn't tell the fixed and broken versions apart. There's now a second picture, of a house drawn smaller than the plot, so if that fix ever breaks we'll see it immediately instead of you finding it on your phone.

We also ran the harshest automated testing yet — every way of building a home, done four hundred thousand times in random orders, including the button-only route a blind user relies on. Nothing else came up, which is genuine good news about the parts we've already fixed.

**One thing we noticed here — and have since fixed:** on the "mark your front door" step we said "tap the outer wall", but the outline of your actual house was never drawn, so if you'd drawn the plot bigger than your rooms it was a guess which line "the outer wall" was. Following that thread is what turned up the bigger front-door problem described above — the wall you tapped and the wall the app counted could genuinely differ. Both halves are fixed and in the latest build: your house is outlined on that step, and the wall is worked out from your house rather than the drawing area.

**Two small optional improvements** we can make if you'd like (neither is a problem for delivery, so they're your call, not a to-do):
- When you reopen a home, if you'd left empty space around the rooms, the plot comes back trimmed to the rooms. Your rooms and score are unchanged — only the blank border shrinks. We can make it remember the exact plot size if you prefer.
- A brand-new home you haven't scored yet lives only in memory until the first score; on a cheaper phone, if Android shuts the app to save memory, that half-drawn home could be lost. A home you've already scored is always safe. We can make the half-drawn one survive too.

### Access for people with disabilities
- A blind person using Android's screen reader can now set North using the dial or slider — before, they could hear it but not change it. This matters for a paid product that should work for everyone.
- More screen-reader polish: the home list, the "what brings you here?" cards, and the not-yet-available languages now describe themselves properly out loud, and the floor-plan editor remembers what you were doing if the screen rotates.

### What we decided this week
- **The look:** a calm sage-green-and-gold theme, applied throughout.
- **Floor plans:** you choose your plot's size and proportions, with neat square cells (rather than stretched ones).

### What's next
- The build work planned for this stage is done. What remains before the 4 August delivery is the set of decisions above — they're the last things standing between here and delivery, and they need you rather than more building.

---

*Kept up to date as we build. Detailed technical notes live separately; this page is the plain-English view.*
