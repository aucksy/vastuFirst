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

These are the things only you can decide. Everything else is done.

1. **A Google Play developer account.** It costs **$25 (about ₹2,100), once**, at
   `play.google.com/console`, on the Google account you want to own this app forever. Nothing can be
   published until it exists and your ID is verified, and verification can take a few days — so this
   is the one worth starting today. Tell us the email address on it and we'll take it from there.
2. **A decision about how the ₹699 is collected.** Google does not allow other payment services for
   something sold and read inside a Play Store app, so the checkout is built on Google's own — which
   means Google keeps about ₹105 of each ₹699. The alternative is selling the report on a web page
   instead, where you keep all of it but the customer has to pay on a website first. We have built
   the first; say the word if you'd rather have the second.
3. **The email address customers should write to.** It appears publicly on your store page and in the
   app's privacy policy. We are using `simpleapps108@gmail.com` until you say otherwise.
4. **Where the privacy policy should live on the web.** Google needs it at a public address. We can
   put it up for free in about ten minutes — just say yes.
5. **One ruling we have deliberately left to you: where the pooja room belongs.** The classical texts
   put it in the centre of the home; modern practice puts it in the north-east, near-universally.
   Turning either one on changes the score of every home already saved on a phone, so we have not
   done it. Our recommendation is the modern north-east. It is a one-line change.
6. **Two quick "please glance at it on your phone" checks:** does the compass helper point the right
   way in your actual flat, and does the app come back with your half-drawn home intact after you
   leave it in the background for an hour?

**The other seven disputed Vastu questions are now decided** — the most widely attested classical
position on each, written up with the reasoning. Any of them can be overturned in one line if your
expert disagrees.

---

## Week of 28 July – 3 August 2026

### Saturday 1 August, evening — the accuracy release

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.4.0/vastufirst-v0.4.0.apk

This is the biggest single change to how accurate the app is, plus everything a paid app on the Play
Store needs before it can be sold.

**Homes that aren't a plain rectangle are finally scored properly.** This was the worst problem in
the product. If your home has a missing corner — an L-shape, a notch, the commonest real shape in
India — the app used to quietly treat it as a full rectangle and give it a better score than it
deserved, with no warning at all. It now **asks**: leave part of the grid empty and a card appears
saying "Is this part of your home?", with the corner highlighted, and two big buttons. Answer no and
the missing-corner checks run for the first time. Answer yes, or say nothing, and your score is
exactly what it was before — nobody's existing home moves.

**Your phone's compass can now set North**, without asking you for any permission at all. And better
than that: before it scores, the app now tells you **what your North actually means** — "your front
door is on the west side, and your kitchen is in the south-east" — and the button that carries on is
the one that agrees with it. "Are you sure?" is a question nobody can answer. "Is your kitchen in the
south-east?" is one anybody can, standing in their own kitchen.

**A half-drawn home now survives your phone closing the app.** It comes back, and it says it came
back, with a way to start over. Before, ten minutes of drawing could vanish with no explanation.

**One damaged saved home can no longer wipe out your whole list.** It used to be able to. Now the
rest load and the screen says one couldn't be opened — and nothing is ever deleted.

**The score now says what it looked at.** It has only ever come from your rooms, your front door and
your home's shape, but it read like a complete verdict. There is now an optional step — the water
tank, an underground tank, a big tree, a road pointing at the house — and answering any of them lets
more of the checks run.

**A room sitting exactly on the line between two directions now says so**, while you can still move
it.

**And everything a paid app needs:** the ₹699 checkout is built in full and switched off, saying in
plain words that no payment is taken; there is a real privacy policy in the app; the app now records
its own crashes and offers to email them to us — nothing is sent unless you press send; and the
version we would upload to the store is now built and checked on every single change rather than
being met for the first time on the day it matters.

---


### Saturday 1 August, later — you can now add every kind of room, not just eleven of them

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.3.24/vastufirst-v0.3.24.apk

You noticed that the list of room types when you *add* a room isn't the same as the list when you
*change* a room you've already placed, and that Corridor was missing from the first one. Both true.

Adding offered **eleven** kinds. Changing offered **nineteen**. The eight you could never add were
Entrance, Corridor, Utility, Bathroom, Guest bedroom, Courtyard, Garage and Basement.

**That mattered more than it looks.** When the app reads a scanned floor plan it can produce all
nineteen kinds. So if it read one of your rooms as a Corridor and you deleted it, there was no way
to put a corridor back — you'd have had to start that room again as something else and correct it
afterwards. A one-way door, for eight kinds out of nineteen.

Fixed properly rather than patched: there is now **one** list that both places read, so they can't
drift apart again in future. The eleven you're used to stay in the same order, so nothing you
already reach for has moved — the extra eight come after them, so you scroll a bit further along the
row to reach Corridor.

Nothing about scoring changed.

**One honest note:** the extra kinds sit further along that left-to-right strip, so you do have to
scroll to find them. If you'd rather the strip showed everything at once without scrolling, say so —
it's a small change, but it makes the row of buttons taller and pushes the plan itself down the
screen, which is why it isn't done by default.

### Saturday 1 August — the score now reads out of 10, not out of 100

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.3.23/vastufirst-v0.3.23.apk

A home that used to show **47** now shows **4.7**. A home that showed 8 now shows 0.8, and a perfect
home shows 10.0. It always carries one number after the dot, so a home that scores exactly five shows
"5.0" and never a bare "5" — beside a 4.7, a lone 5 looks like a different kind of number, and this is
a column people compare two homes in.

**Nothing about the scoring changed.** The same home gets the same result, in the same colour, with
the same wording under it — "Strong", "Workable" and so on all fall in exactly the same places. Only
the way the number is written on screen is different.

It changed in every place a person meets it: the big number on the free result, the list of your saved
homes (which is also the side-by-side comparison someone uses when they are choosing between two
places), the live number that moves as you drag the compass, and — for a blind user — the two places
the phone reads the score out loud, which used to say "score 47 of 100" and now say "score 4.7 out
of 10". The small print under the score changed too, because it used to describe the scale by name.

**One judgement call worth knowing about.** A number like 4.7 *looks* more precise than 47 did, and
this score is a summary of a report, not a measurement of anything. So the small print under it now
says so in four extra words — "a summary, not a measurement". If you would rather go further, the
cheapest next step is to show the number next to its word ("4.7 · Workable") so the word carries the
meaning. Your call, not urgent.

**And one thing you will never see, which is the point of mentioning it.** India's languages write
decimals with a dot, and so does English — so writing the dot in by hand would have looked right in
every test we have. It would also have been wrong the moment someone opened the app on a phone set to
a European language, where 4.7 is written "4,7". The app now asks the phone what its language uses
instead of assuming. Nothing to check; it just will not embarrass us later.

### Friday 31 July, later — rooms now match the shape printed on your plan

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.3.21/vastufirst-v0.3.21.apk

You spotted that the passage on your Gurgaon plan came out as a wide flat box when the plan clearly
draws it as a tall narrow strip — and asked whether that throws the score off. It does, and you were
right to push on it.

Checked properly against your sheet: **four of the six rooms that print their size came out the wrong
way round.** The lobby, the kitchen, the bedroom and the passage were all drawn wider than deep when
the plan says the opposite. That matters because a room's Vastu direction is decided by where it sits
in the home, so a room of the wrong shape sits in the wrong direction.

The cause was daft, and now fixed: your plan prints the sizes right next to the room names, we read
them perfectly, and then threw them away — leaving each room's shape to come from the AI's guess,
which is the one thing it is genuinely bad at. Those printed numbers are reliable: on your sheet they
add up to 353 sq ft against the 336 the sheet states, and the difference is just the wall thickness.

Now the kitchen and the living room come out tall, as your plan draws them, and the toilet and bath
come out small instead of full-width bars.

⚠ **Two honest limits.** Only about a quarter of plans print sizes on every room; yours does, many
don't, and where they're missing nothing changes. And this fixes each room's **shape**, not **where**
it sits — the AI's sense of position is still a guess, so on your plan the passage still ends up in
the wrong spot and is marked for you to check. You still confirm every room, as before.


### Friday 31 July — you can now correct a room the app got wrong

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.3.20/vastufirst-v0.3.20.apk
This one installs straight over the previous version — your saved homes are kept.

When you scan a plan, the app reads the room names off it. It is very good at that, but not perfect —
and until now, if it read a room wrongly there was no way to tell it so. Your Gurgaon flat is the
example: the biggest room in it, printed "LOBBY", was read as a corridor.

**Now you can just change it.** Tap any room and pick what it really is, from the app's list of room
types. It works in two places:

- **On the list right after a scan** — each room is now tappable, with a "Change" next to it. This is
  where you will notice a mistake, so it is now also where you can fix it.
- **On the plan itself** — tap a room, and there is a "Change room type" button in its panel. This one
  also helps homes you drew by hand, not just scanned ones.

The score and the report update straight away, exactly as they do when you move a room.

⚠ **Two things worth knowing, because they matter more than they sound.**

**First, this was a bigger problem than it looked.** The advice up to now was "delete the room and
place a new one". For eight of the nineteen kinds of room, that was not actually possible — the list
you add rooms from is shorter than the list the app can read from a plan. So a room read as a
corridor, an entrance, a utility or a bathroom could be deleted and never put back. That is now fixed:
every kind of room the app knows about can be chosen.

**Second, a correction to something I told you earlier.** I said a lobby read as a corridor was scored
too low. Checking it properly rather than repeating it: a corridor is **not scored at all**. So the
largest room in your flat was not counted low — it was not counted. Changing it to a living room does
not nudge the score, it genuinely fixes it.

Nothing else about the plan builder changed.


### Thursday 30 July, later — new versions no longer wipe your saved homes

A real fault, found because the owner had to uninstall the app to install each new version: **every
build we published was stamped with a different signature**, and Android will not install an update
whose signature has changed. The only way in was to uninstall — which deletes everything the app has
saved. Every home you had drawn was being destroyed by every update.

Fixed. Every build from now on carries the same signature, installs straight over the previous one, and
keeps your saved homes. There is also now an automatic check that refuses to publish a build that would
break this again.

⚠ **One last time only:** to move to this version you do have to uninstall first, because the previous
version's signature can't be recovered. After that, updates just install.

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

### 31 July — the plan now fills the drawing area, and small rooms keep their names

Two things came out of testing a second real flat plan.

**The home was being drawn too small, in a corner.** Builder plans put a logo and a title block down
one side of the sheet, and the app was treating the whole sheet as the drawing area — so a home that
took up part of the page also took up only part of the grid. On the flat we tested, a 526 sq ft home
was being drawn as though the plot were forty feet square. The app now measures the drawing area from
the home itself. Across thirty real plans the home went from filling about seven-tenths of the area to
filling nearly all of it.

**Something we found while fixing it, which matters more.** Rooms that came out very small were being
dropped altogether before they ever reached the screen — ten of them across the test plans, nearly all
toilets, which are one of the more important rooms for a Vastu score. That can no longer happen: a
room can be drawn small, but it cannot disappear.

**Small rooms now keep their names.** A narrow toilet used to show "To…". The name is now turned on
its side and runs down the room, the way a printed architectural plan labels a narrow space.

**Two smaller polish items**, both reported earlier: the little label showing a room's name and
direction no longer sits on top of the plan when you select a room (it only appears while you are
actually moving one), and the faintest grey lettering and the arrow buttons have been darkened enough
to meet the accessibility standard for readable text.

Room *positions* are still the AI's best guess and are confirmed by the user, unchanged.
