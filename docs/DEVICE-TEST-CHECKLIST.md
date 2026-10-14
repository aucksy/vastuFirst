<!--
MAINTAINER NOTE — not for the client's weekly email. This IS the list the client runs on real phones.

⭐ THIS IS THE SINGLE RUNNING LIST of everything that needs a real finger, a real phone, or a real
pair of eyes. It is the canonical list — docs/UAT-GRID-PLAN-BUILDING.md and PHASE-2-PROGRESS.md
point HERE instead of keeping their own copies, so there is never a second, staler list.

REBUILT FOR v0.37.0 (14 Oct 2026). Every row was checked against the app's own code: button labels
are quoted exactly as the screen prints them. Rows for screens that no longer exist ("Check what we
read", the "Directions / Boxes" trial, the live phone compass, the grid a placed scan used to land
on, the boxes drawn over a photographed plan) were deleted, not parked.

APPENDING IS PART OF "DONE". Every time a build ships something that automated tests cannot prove —
a haptic, a raw gesture, true process death, a screen-reader phrase, anything whose only proof is
looking at it — add a row here, tagged with the build it arrived in. When a screen is deleted,
delete its rows in the same commit. Once the owner reports a result, move the row to "Settled" at
the bottom with the verdict and the date.

Keep every line in plain English: what to DO, what you should SEE. No file names, no class names.
-->

# VastuFirst — the three-phone pass

**Build to test:** v0.37.0 (14 October 2026) —
[download the APK](https://github.com/aucksy/vastuFirst/releases/download/v0.37.0/vastufirst-v0.37.0.apk)

This is the last check before the Play Store. Everything else is checked automatically on every
build — the maths, the score, and a picture of every screen at small sizes and large text. What is
left below is what a machine cannot judge: how a drag feels, whether a buzz happens, what a screen
reader says out loud, what an old or a brand-new phone does to the app, and whether your work
survives Android closing it.

### The three phones

| | Which phone | Why |
|---|---|---|
| **Small** | A small or cheap Android phone on **Android 8, 9 or 10** | Least memory, so Android closes the app most often; oldest Android the app supports |
| **Mid** | A mid-range phone on **Android 12, 13 or 14** | What most customers will hold |
| **16** | A phone on **Android 16**, if you can borrow one | The Play Store now requires the app to be built for Android 16, which changes how the screen edges and the Back gesture behave |

Do every row on all three phones unless it says otherwise. Tick it when it passes on all three. If
it fails on one, leave it unticked and say which phone.

### Before you start

- Turn phone **vibration on** (the app stays silent if it is off).
- Have **one PDF of a floor plan** and **one printed plan** you can photograph. A plan that prints the
  word ENTRANCE (or MAIN DOOR) and one that does not are both useful.
- Reading a plan needs the internet. Drawing a home by hand does not.

### How to report a problem

Email **contact@vastufirst.com** with:

1. The **row number** (for example "B4") and **which phone** (small, mid or 16).
2. A **screenshot** of the screen.
3. One sentence: *"I tapped X and expected Y, but Z."*

A vague "this looks off" with a screenshot is welcome — please don't talk yourself out of sending it.

---

## A · Installing and opening

- [ ] **A1 · ⭐ Install over the last build without uninstalling.** With v0.36.0 on the phone and at
      least one home saved, install v0.37.0 straight on top. Android must not ask you to uninstall,
      and every saved home must still be there with the same score. If it refuses, or your homes are
      gone, stop and tell me. *(v0.37.0)*
- [ ] **A2 · Open the app from cold.** Swipe it away from your recent apps, then open it. You should
      see the VastuFirst mark on cream for a moment, then a real screen — your list of homes, or
      "What brings you here?". Not a blank cream page, and not the mark drawn twice. *(v0.4)*
- [ ] **A3 · A fresh install starts with a question, not an empty screen.** On a phone that has never
      had the app, it should open on "What brings you here?" with three choices: "I am buying a
      home", "I am building a home", "I already live here". Tapping one moves you on at once — there
      is no separate Continue button. *(v0.35.0)*
- [ ] **A4 · The icon on your home screen.** The whole square mark sits inside the tile — all four
      corners — with a band of cream around it. If your phone lets you change icon shape, try two
      shapes. *(one phone is enough)*

## B · Scanning a plan — the main journey

Start from your list of homes: **"Add a home"**.

- [ ] **B1 · ⭐ The first screen.** "Add your home" asks "Is it a house or a flat?" and "How would you
      like to add it?", with four cards: "Upload a plan", "Photograph your plan", "Draw it on a
      grid", "Try a sample plan". *(v0.35.0)*
- [ ] **B2 · ⭐ The privacy card, first scan only.** Tap "Upload a plan". The very first time, a screen
      headed "Your plan leaves this phone" appears. The five facts are folded under one line,
      **"What happens to your plan (5)"** — tap it and all five open (what we send, who reads it,
      what we ask it, what we keep, who works out your score). The "I agree — read my plan" button
      should be visible without scrolling, and "Draw it on a grid instead" should be there too. Read
      it as a customer would: is anything on it something you would rather not promise? *(v0.36.0)*
- [ ] **B3 · ⭐ The card opens the picker straight away.** After agreeing (and every time after that),
      "Upload a plan" must open your phone's own file picker immediately — no extra "PDF or photo?"
      screen in between. Pick your PDF. *(v0.35.0)*
- [ ] **B4 · Backing out of the picker.** Open the picker and press Back without choosing anything.
      You should land calmly back on "Add your home". *(v0.35.0)*
- [ ] **B5 · ⭐ "Photograph your plan" opens the camera.** It must open the camera, not your gallery,
      and must **not** ask you for camera permission. Photograph a printed plan, keep the shot, and it
      should be read like a PDF. *(v0.35.0)*
- [ ] **B6 · The wait.** While reading, the screen says "Reading your plan…" and "This usually takes a
      few seconds." On a slow read it should change to "Still reading — a detailed plan can take a
      couple of minutes." and then "Taking a second look at your plan." It must never look frozen.
- [ ] **B7 · ⭐ "We read N rooms" — with North on the same screen.** For a clear plan the result is
      headed "We read 11 rooms" (your number). Below it, the rooms are folded under **"Rooms we read
      (11)"**, and if the reader was unsure of some, that line also says "3 to check". Further down on
      the **same screen**: "Which way is North?" with the dial over your own photograph. There should
      be no separate North screen after it. *(v0.35.0)*
- [ ] **B8 · Correcting a room's kind.** Open "Rooms we read" and tap any room. A list of room types
      opens under it; pick a different one and the row should update. Rooms marked "CHECK" are the
      ones the reader was unsure of. Picking the type it already is should just close the list. *(v0.35.0)*
- [ ] **B9 · ⭐ A plan that prints its entrance goes straight to the report.** With such a plan, the
      button under North reads "Read my home" (or "Yes — read my home" when the "Is this right?" card
      is showing). Pressing it should go to "Reading your home…" and then your report — **no
      front-door screen.** *(v0.35.0)*
- [ ] **B10 · ⭐ A plan with no printed entrance asks for the door first.** With such a plan the button
      reads "Next — your front door" (or "Yes — next, your front door"). It should open "Where is your
      front door?", and only after that the report. *(v0.35.0)*
- [ ] **B11 · Count the rooms.** For a plan you know well: did the reader get the room **names** right?
      Is any room missing? A missing room is worse than a wrongly named one — tell me which.
- [ ] **B12 · A busy plan that can't be placed.** If a plan comes back headed **"We found N rooms"**
      (not "We read"), the screen must say it read the names but not where they sit. "Place them on
      the grid" should open the grid headed "These rooms aren't placed yet", with every room named
      and big enough to grab first time. You only get here with busy or angled pictures.
- [ ] **B13 · A 3D picture is refused politely.** Upload a 3D marketing picture of a home (the kind on
      a builder's website). You should get "This looks like a tilted 3D view" with a way to try a
      different picture or draw instead — never an error or a dead end.
- [ ] **B14 · When the reader is busy.** If you ever see "We're reading a lot of plans right now", it
      must say we haven't looked at your plan yet and offer "Try again" and "Draw it on a grid
      instead". Calm, not an error. *(only if it happens)*
- [ ] **B15 · Try a sample plan.** "Try a sample plan" should go straight to "Which way is North?" and
      then the report in two taps. *(one phone is enough)*

## C · Which way is North

- [ ] **C1 · ⭐ The "Is this right?" card.** Turn the dial on any home. A card headed **"Is this
      right?"** lists sentences like "your front door is on the west side" and "your kitchen is in the
      south-east". Turn the dial and the sentences should change. **Stand in your own flat: with North
      set the way your plan's arrow shows, are the sentences true?** If they are ninety degrees out,
      tell me straight away. *(v0.4)*
- [ ] **C2 · Three ways to set it.** The dial, the slider and the N, E, S, W buttons should all move
      the same North, and "North is at" should show the number.
- [ ] **C3 · The dial on a photo.** On a scanned plan the dial sits over your own photograph and takes
      the plan's shape. Turning it should feel smooth on the small phone too.

## D · The front door

- [ ] **D1 · ⭐ Drag the E round the outline.** On "Where is your front door?", put your finger on the
      **E** and drag. It should move with your finger the whole time — no tap first — slide along the
      wall, and carry on round the corner onto the next wall. Where you let go is where the door is
      set. *(v0.31.0)*
- [ ] **D2 · Tap the E.** A small note headed "Your front door" opens, saying which wall it is on.
      Tap it again (or elsewhere) to close it. *(v0.31.0)*
- [ ] **D3 · Tap a wall instead.** Tapping another wall should move the door there. *(v0.31.0)*
- [ ] **D4 · No door at all.** Without placing a door, the button reads "Skip the door — read my
      home". Press it: the report should say the door was not marked rather than inventing one.
- [ ] **D5 · Changing the door later.** From a scanned home's report, tap "Change front door". You
      should be back on your photograph with the E. Move it, press "Done — back to my report", and
      the score should follow — and you should land on the same report, not a second copy of it.

## E · Your report

Quickest way in: **Add a home → Try a sample plan → Read my home**. For E2 and E3 use a scanned plan.

- [ ] **E1 · ⭐ Read it once, top to bottom, as a customer.** Not hunting bugs — *would you pay for
      this?* Tell me the first place your attention drops, and the first sentence you had to read
      twice.
- [ ] **E2 · ⭐ Tap a room to see its direction on the plan.** Under "Your home, as we read it" the
      photograph opens with nothing written on it. Tap a room in the list below, or on the photo
      itself: a short direction — **NE**, **SW**, or **C** for the centre — appears in the middle of
      that room, and the full name ("North-East", or "North-West · crosses the centre") is in that
      room's card below. Tap another room and the label moves to it. *(v0.34.0)*
- [ ] **E3 · Your front door on the photo.** The E should be drawn on the report's photograph too.
      Tapping it explains it; it does **not** drag here — "Change front door" is how to move it.
- [ ] **E4 · Change North from the report.** Tap "Change North", turn the dial, press "Back to my
      report" (or "Yes — back to my report"). The score should change and you should land on the same
      report.
- [ ] **E5 · Are the reasons believable to someone who knows Vastu?** Each problem names its
      direction, the deity and element the tradition gives it, and why that room does not belong
      there. If any sentence reads as wrong or made up, send me that exact sentence.
- [ ] **E6 · A prayer room is "Not ideal", never a defect.** The sample home's prayer room is in the
      north-west. It should carry a "Not ideal" label in "Your rooms" and must not be named under
      "Start here" as the thing to fix.
- [ ] **E7 · Both sides of a dispute.** Open **"Where the schools disagree (N)"** at the end of the
      report. Each entry should show both readings and a line "What your score uses".
- [ ] **E8 · The four extra questions.** On the report there is a card offering "Tell us about these
      4" (water tank, sump, a big tree, the road). Answer the water tank with a direction, answer one
      with "There isn't one", then press "Done — back to my report". The score should move, and
      "What this covers" should count what you told it.
- [ ] **E9 · Buying vs building.** Pick "I am buying a home" (or "I already live here") and read the
      full report: there must be **no** suggestion to move walls, redraw or renovate — remedies only.
      Only "I am building a home" should show "Change the layout — before it's built".
- [ ] **E10 · A flat, not a house.** Add a home as "A flat". Where something belongs to the whole
      building (its main door, its tank), the report should say "This belongs to the whole building,
      not to your flat" and offer a softening, not a fix.
- [ ] **E11 · ⭐ The unlock screen says plainly that no payment is taken.** Tap "Unlock the full
      report". Beside the price it must say "the planned price — not charged in this version", the
      button must read "Unlock on this device — free", and the line under it must say "No payment is
      taken in this version". Press it: the full report opens. **If anything on that screen looks
      like it would charge you, stop and tell me.**
- [ ] **E12 · The free part is still useful.** Before unlocking, the report should show your score,
      the front door, the kitchen and the toilets in full, with the rest marked "Reasoning and
      remedies — in the full report". Does the free part feel like a fair taste or a tease?

## F · Drawing a home by hand

**Add a home → Draw it on a grid.**

- [ ] **F1 · Touch a room and keep sliding.** It should pick up the moment you touch it and move in
      the same motion — no tap first.
- [ ] **F2 · Pull each corner dot to resize.** The opposite corner stays put. Pulling past the far
      corner stops at one square — the room never flips inside-out or vanishes.
- [ ] **F3 · Drag a room onto another.** It should stop at the last clear spot, "Rooms can’t overlap"
      should show, and letting go leaves it in the clear spot — never stacked.
- [ ] **F4 · A tiny room moves, not resizes.** Touch the middle of a 1×1 room and drag. It must move.
- [ ] **F5 · Add a room — all nineteen kinds.** Scroll the "Add a room" strip to the end: Entrance,
      Corridor, Utility, Bathroom, Guest, Courtyard, Garage and Basement should all be there after the
      familiar ones. The strip fades at the right edge while there is more.
- [ ] **F6 · Change a room's kind.** Tap a placed room, then "Change room type" in the panel below.
      The colour and name should change and **nothing may move** — not that room, not any other room,
      not the front door.
- [ ] **F7 · Plot size.** Under "Plot size", press "−" until it stops: one more press should give a
      short "no" buzz. Same buzz if your rooms fill the plot and you press "−".
- [ ] **F8 · ⭐ The front-door step.** With rooms placed, the big button reads "Next — set the front
      door". It opens "Mark your front door", with your home outlined. Tap a wall: the line should
      change to "Your front door is on the north wall. Drag the E to any wall to move it." (your
      wall), and the button should read "Next — mark North". Without a door it reads "Skip the door —
      mark North". *(v0.26.0, E since v0.31.0)*
- [ ] **F9 · The wall you aimed at.** On a plot bigger than your house, tap just outside each of the
      four walls. The door should land on the wall you meant, every time. Try a one-room-deep house
      too: above it gives a north door, below it a south door.
- [ ] **F10 · Drag the E on the grid.** Once placed, drag the E round the outline. It should follow
      your finger onto any wall. *(v0.31.0)*
- [ ] **F11 · Leave a corner empty.** Leave one corner of the plot with no room. A card asks "Is this
      part of your home?" with that corner marked. "No — my home is cut off there" should cross it
      out and the score should go **down**; "Yes — it's part of my home" changes nothing and stops
      asking; "Start the shape again" puts it back.
- [ ] **F12 · A gap in the middle.** An empty square surrounded by rooms should not be offered for
      cutting — the card should say "There's an empty space inside your home" and suggest a Courtyard.
- [ ] **F13 · A room near a line.** Select a room near a boundary. The line under its name should read
      like "2 × 2 · North-West, close to North". Helpful, or noise?

## G · How it feels (buzzes)

- [ ] **G1 · Dragging a room:** a soft tick each time it jumps a square.
- [ ] **G2 · A refused move, resize or plot press:** a firmer "no" buzz. Also when a room touches a
      wall and you press the arrow or size key pointing into that wall.
- [ ] **G3 · A room or door landing:** a confirming tap.
- [ ] **G4 · Every button, chip and row:** a light tap.
- [ ] **G5 · The North dial — a decision for you.** It ticks for **every degree** you turn. The
      alternative is a firmer click every 15°. Which do you prefer? *(needs your preference)*
- [ ] **G6 · Too much anywhere?** If the buzzing is annoying anywhere, say where.

## H · Surviving interruptions

- [ ] **H1 · ⭐⭐ A half-drawn home survives an hour.** Start a new home on the grid, place three or four
      rooms, press Home, and leave the phone for an hour — or open a dozen other apps to push it out.
      Come back to VastuFirst. Either you are exactly where you were, or you land on your rooms with a
      card **"Carrying on with your unfinished home"** and a "Start this home again" button. **An
      empty grid is the bug.** The small phone is the one most likely to show it.
- [ ] **H2 · Force-stopping keeps it too.** Place a few rooms, then Settings → Apps → VastuFirst →
      Force stop, and reopen. Your list of homes should show a **"Still to finish"** group with a row
      like "3 rooms so far · today". "Carry on" should bring the rooms back.
- [ ] **H3 · Starting fresh is really fresh.** With a home still to finish, tap "Add a home" → "Draw it
      on a grid". You must get an **empty** grid, not the half-finished one.
- [ ] **H4 · Throwing one away asks first.** The ✕ on a "Still to finish" row must ask "Throw away
      this unfinished home?" and say how many rooms you will lose. "Keep it" leaves it alone.
- [ ] **H5 · The camera on the small phone.** On the small phone, "Photograph your plan", take the
      shot and keep it. Android often closes the app while the camera is open. You should still go on
      to "Reading your plan…" — not back to the start with the photo lost.
- [ ] **H6 · Rotate mid-edit.** Turn the phone sideways while placing a room, with the room-type list
      open, and on the door step. Everything should still be there.
- [ ] **H7 · Leaving mid-scan.** While "Reading your plan…" is showing, switch to another app for a
      minute and come back. Tell me where you land and whether it annoyed you.

## I · The Android 16 phone

These rows need the **16** phone. If you can't borrow one, skip this section and say so.

- [ ] **I1 · ⭐ Nothing under the status bar or the gesture bar.** Walk through the whole journey (list
      of homes, Add your home, the privacy card, the scan result, the door screen, the report,
      Settings). Every heading and every ‹ back arrow must sit **below** the clock and battery, and
      every bottom button must sit **above** the gesture bar or navigation buttons — nothing cut off or
      hidden underneath. Repeat with the phone sideways: nothing hidden under the camera hole. *(v0.37.0)*
- [ ] **I2 · Readable clock and battery.** On the cream screens the clock and battery icons should be
      dark, and readable. Try with the phone in dark mode too. *(v0.37.0)*
- [ ] **I3 · ⭐ The Back gesture shows where it goes.** Swipe in slowly from the left edge on any
      screen and hold: you should see a preview of where Back will take you. Let go and it goes back
      one step; change your mind and slide back, and nothing happens. On your list of homes, Back
      should leave the app to the phone's home screen. *(v0.37.0)*
- [ ] **I4 · ⭐ Back cancels a North change.** On a report, tap "Change North", turn the dial a long
      way, then use the **Back gesture** instead of the button. The report's North and score must be
      exactly as they were before. *(v0.37.0)*
- [ ] **I5 · The keyboard doesn't cover the box.** On your list of homes tap ✎ on a home ("Rename this
      home"). The "Home name" box, "Cancel" and "Save" must all stay visible above the keyboard.
      *(v0.37.0)*
- [ ] **I6 · Themed icon.** Turn on themed icons in your home-screen settings. The mark should take the
      wallpaper's colour and still be whole. *(Android 13 or newer)*

## J · Privacy and your data

- [ ] **J1 · ⭐ Backups are off: a reinstall brings nothing back.** Save two homes. Uninstall the app,
      then install it again. **No homes may come back**, and your first scan must ask you to agree
      again ("Your plan leaves this phone"). Do this properly once the app is on Google Play (a
      private test track counts): install from the Play Store, save homes, uninstall, reinstall from
      the Play Store. Until then, the APK version of this test is still worth doing. *(v0.37.0)*
- [ ] **J2 · Backups are off: a new phone gets no homes.** *(Only if you are setting up a new phone
      anyway.)* When you copy your old phone onto a new one, VastuFirst may come across, but it must
      open with **no saved homes**. *(v0.37.0)*
- [ ] **J3 · Turning plan reading off.** Settings → "Reading uploaded plans online" (it shows
      "Allowed"). Tap it so it reads "Off", then Add a home → Upload a plan: it must ask you to agree
      again before it will read anything.
- [ ] **J4 · The privacy page.** Settings → Privacy. Read it as a customer would. It promises your
      homes are saved only on this phone — is there anything on it you would rather not promise?
- [ ] **J5 · Delete everything.** Settings → "Delete all my data". It asks "Delete all your data?";
      "Keep my data" changes nothing, "Delete everything" removes every home. *(Do this last.)*
- [ ] **J6 · Crash reporting.** Only checkable if it happens: if the app ever closes by itself, reopen
      it and go to Settings. A "Something went wrong" section should offer "Send what went wrong",
      which opens an email to us that you can read before sending. Please do send it.

## K · Screen reader (TalkBack)

Settings → Accessibility → TalkBack. One pass on one phone is enough.

- [ ] **K1 · The score says its scale.** On the report, the big number should be read as "Score 4.7
      out of 10" — number and scale in one phrase.
- [ ] **K2 · The front door says the word.** On the grid it should be read as "Front door on the
      north wall" — the word, not a letter.
- [ ] **K3 · A room's direction on the photo.** On a scanned report, tap a room: the plan should be
      read as "Your plan. Kitchen is in the South-East." (your room and direction).
- [ ] **K4 · Place and move a room with buttons only.** On the grid, use "Move up", "Move left" and
      the size buttons — no dragging. This is the path for someone who cannot see the screen.
- [ ] **K5 · Set North by TalkBack.** The dial and the slider should actually change North, not just
      be read out.
- [ ] **K6 · Swipe through every screen of one scan.** Every control should be announced and usable,
      including the folded "What happens to your plan (5)" and "Rooms we read" lines.

## L · Big text and daylight

- [ ] **L1 · ⭐ Largest text.** Turn the phone's font size and display size right up and walk one scan
      and one hand-drawn home through to the report. Nothing cut off mid-word, no button you cannot
      reach.
- [ ] **L2 · Daylight.** Take the phone outside and read the report and the "Is this right?" card at
      your usual brightness. Is the small grey writing readable?

## M · Anything else

- [ ] **M1 · Anything that just looks wrong.** A screenshot is enough.

---

## Known, not chased in this build

- **Reopening a home you drew by hand trims the empty plot around your rooms.** The margin you drew is
  not remembered; your rooms and score are unchanged. Tell me if it bothers you.
- **On a scanned report, the direction label sits over the room's printed name** while that room is
  picked. Where it should sit is still being decided.

## Settled

Nothing yet — this section fills up as you report results, so we never re-test something twice or
lose a verdict.
