<!--
MAINTAINER NOTE — not for the client. This file is the single source for Simran's Sunday
progress email, so it must stay in plain English: outcomes, not mechanics. No file/class names,
no code, no build/release jargon (those go in docs/PROGRESS.md). Update it as part of "done":
whenever a build is released, a phase item closes, a decision lands, or we hit a blocker.
Newest week at the top. Keep the "What we need from you" AND "What's coming" sections current —
between them they are the most useful part for the client. "What's coming" is high-level pointers
only, in rough order, and carries NO dates: this file has never promised a date and must not start.
Move an item out of it and into a week the moment it ships.

STANDING RULES FOR THE WEEKLY EMAIL (set 3 August 2026, apply every week from now on):
- Recipients: Simran.manocha123@gmail.com AND vastufirst13@gmail.com — both on the To line as
  normal recipients, every time. Not CC.
- Send from the alias connect@aakashpahuja.in — NOT the default Gmail account. The Gmail
  connector cannot set the sender, so this is a manual step: switch the "From" dropdown in the
  Gmail compose window before sending. Always say so when handing over a draft.
- Do NOT state a day or time for the client call. The meeting schedule is not settled.
  Sign off with "Hopefully talk to you soon, when the stars align." — never "Talk at 11 on Sunday".
- Do not lead on or count down to a delivery date unless asked.
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
3. **Where the privacy policy should live on the web.** Google needs it at a public address. We can
   put it up for free in about ten minutes — just say yes.
4. **Two quick "please glance at it on your phone" checks:** does the compass helper point the right
   way in your actual flat, and does the app come back with your half-drawn home intact after you
   leave it in the background for an hour?

**Settled since last week:** the address customers write to is `contact@vastufirst.com`, and it is
already in the app and the privacy policy.

**✅ Where the prayer room belongs is now decided — the north-east.** This was the last open Vastu
question, and it was yours to make. Prayer rooms used to be left out of the score altogether, because
the old texts place the shrine at the centre of the home while almost every consultant working today
places it in the north-east. We now follow the modern reading: **north-east is ideal, north and east
are fine, and no position counts as a fault — including the centre.** A prayer room in, say, the
north-west now reads as "not ideal", never as a defect, which is the honest way to describe a choice
between two living traditions rather than a mistake. The report still shows you both traditions side
by side, and now also says which one your score uses. **Every disputed Vastu question is now settled.**

**And anyone who already saved a home is told their score was recalculated**, with the old number,
the new number and the reason in ordinary words, before anything changes on their screen. A score
that moves on its own with no explanation is the difference between an honest product and an unnerving
one, and it is not something we were willing to ship.

---

---

## 🔭 What's coming

The plan for this app has six stages. **Stages 0, 1 and 2 are finished** — the scoring engine, drawing
a home, the compass, the free score and the paid report. That was the 4 August delivery.

**We are in stage 3 now: real-world testing.** Its finish line is written down rather than felt — no
serious bugs left open, and every main journey working on three real phones. We are fixing from your
feedback continuously; the three-phone pass still has to happen.

**Stage 4 is the largest piece still ahead.** In rough order:

- **Reading a plan without ever falling back to a grid.** When the app can read your room names but
  not work out where they sit, it still hands you a grid of squares. You will place a room by tapping
  where it is on your own plan instead. About one plan in six needs this today.
- **The Vastu assistant** — ask a question, get an answer drawn only from the rule set, with the
  source named, and an honest "the texts do not say" when they do not.
- **Flats treated properly** as their own case, with a report honest about what a flat owner cannot
  move.
- **The finer 16-direction reading**, alongside today's 8. Today the app reads 8 and says so plainly
  in Settings; where the 16-zone school reads a room differently, the report already shows both under
  "where the schools disagree".
- **A polish round** — the score reveal, the empty screens, the finish of the thing.

**Stage 5 — money and iPhone.** The ₹699 payment is built and switched off, waiting on your decision
about how it is collected. A shop for remedies is planned. **iPhone has not started**: the app was
built from day one so that this is a re-targeting job rather than writing it twice, but it is real
work and it is a whole stage.

**Stage 6 — launch.** The disclaimer, the privacy policy and the sources page are in the app.
**Terms of use and a refund policy still have to be written** — those are needed before the store will
take it. The store listing, the data-safety declaration and the content rating are all waiting on the
developer account.

**No dates on any of this.** Several of them move only when a decision above lands.

## Week of 10–16 August 2026

### Tuesday 11 August, late — three things from the original list, now in the app

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.13.0/vastufirst-v0.13.0.apk
**This is the one to install.**

**Every room on "Check what we read" now tells you how it reads and which way it faces.** That
screen shows your own plan with the rooms we found listed underneath, so you can hold it up against
your paper. Until now each row gave you the room's name and the size printed on your sheet and
nothing else — you had to reach the report before you learned that the kitchen we found sits in the
south-east and reads as already right. Each row now carries both: the same one word the report uses
— *already right*, *not ideal*, *needs fixing* or *not rated* — and the direction spelled out. The
size printed on your plan is still there; nothing was traded away for the new labels.

**To make that possible, the app asks which way North is one step earlier.** A room has no
direction until North is marked, so for as long as North came *after* the checking screen there was
nothing true to put on those rows. Uploading a plan now goes: read the plan → mark North on your own
picture → check what we read → mark the front door, but only if your plan did not already name it →
your report. Same number of steps, better order, and the last one still opens the report directly.

**The AI model names are off the customer's path.** The screen you land on after a plan is read
carried a section headed "Which AI read it · testing", a line naming the model that read your plan,
and a button per model offering to send it again. That was a testing tool of ours sitting in the
middle of a paying customer's journey. It is gone. Choosing which reader to use is still available;
it lives in Settings, where it also survives trying the same plan again, which the old buttons did
not.

**The report is shorter again — our own wording is down from 451 words to 373.** Nothing was
removed: every finding, every reason, every remedy and every Vastu term is exactly as it was,
including the paragraph explaining that your front door is read on the tradition's 32 named
positions and is not counted twice. Only the joining words went.

**And one thing we found while checking the above.** If your plan's picture ever fails to open, the
front-door screen shows a card saying you can carry on without marking it. At the largest text
setting that card was cutting its own sentence off mid-word. It now shows the whole thing.

### Tuesday 11 August, evening — we read the whole app end to end, and fixed what we found

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.12.0/vastufirst-v0.12.0.apk
**This is the one to install.**

We went through every journey in the app — first launch, uploading a plan, drawing one by hand, your
saved homes, the report, settings — and looked at every screen the build draws, at five phone sizes
and text settings. This is what that turned up. Nothing new was added; all of it is the app telling
the truth about itself.

**A saved home could show a different home's floor plan.** This is the one that mattered most. Once
you had uploaded any plan, the app kept that picture for the rest of the session and drew it under
*every* home you opened afterwards — a home you had drawn by hand, a home you reopened from your
list — under the heading "Your home, as we read it". Your score and your rooms were always your
own; only the picture was somebody else's. Tapping "change where the front door is" then opened that
wrong picture to mark a door on. A saved home never has a photograph stored, so there was no case
where showing one was right. It is gone.

**The offer to unlock the report was overstating what you get.** The bar at the bottom of the free
report said "6 more findings, with the reason and remedies for each". It was adding up two different
things: problems, and rooms. Most of the rooms it counted are rooms the same report calls *already
right* — nothing is wrong with them, so there is no remedy to give — and a room that does have a
problem was being counted twice. The two are now counted separately and the sentence says only what
paying actually reveals. The ₹699 page had the same trouble: two of its four selling points were
things the free report already hands over in full, including your front door. Both now describe
something you genuinely cannot see without paying.

**The report gave two different counts of your own rooms.** The bar near the top said "How your 10
rooms read" while the list further down said "Your rooms (11)". The first number was three figures
added together, and one of the three was counting *problems* beside two that count rooms — so one
room with two problems pushed it up, and rooms the tradition does not rate were missing from it
altogether. All three now count rooms, and the heading no longer claims a total.

**A room that is only middling wore the same red warning as a real fault.** The summary counts your
rooms as "already right", "not ideal" and "need fixing" — and then the list underneath used none of
those words. Every room from the last two groups was marked "Review" in the same alarm red. On a
good home the page could say "reads well throughout, nothing the tradition counts as a defect", show
zero under "need fixing", and still put a red mark on a kitchen right below. The list now uses the
same four words as the summary, and middling rooms are amber. Nothing has been softened: a real
fault is still red and still says so.

**The report pointed you at a screen the app no longer has.** Three of the things it says it could
not check told you to answer some extra questions "on the score screen" — a screen removed last
week. They now name the button that is actually on your report. A fourth, about a mirror facing a
bed or a beam over one, promised to check it if you answered more questions, and there has never
been a question for it. It says plainly that we do not check that one, and why.

**And on a plan that prints ENTRANCE or FOYER**, the room list used to say your front door was "not
rated — the tradition does not place this kind of room", on the same page that reads your front door
in full and calls it the single most important thing in the whole reading. That row now says where
its reading is.

**A button named a screen it never opened.** From a finished report, "change where the front door
is" led to a screen whose green button said "Next — mark North" — and pressing it took you back to
your report instead. Both versions of that screen now say where they go.

**Anyone who had not finished a home could not reach the privacy policy at all.** On a fresh install
the app opens on the first question, and the only way into Settings — and through it the policy, the
sources page and "delete all my data" — was a gear on the saved-homes screen, which you only see
after finishing a whole home. There is now a Privacy link on the first screen, which is where the
person who wants to read it before trusting us with their floor plan actually is.

**Turn the phone sideways while drawing and every control vanished.** The plan filled the width and
kept going — about one and two-thirds of a screen tall — so you saw the top of your home and nothing
else: no plot-size keys, no room list, no button to go on. The plan is now sized to leave room for
the controls underneath.

**Three smaller ones.** On a phone set to a right-to-left language the score read backwards — "10 /
3.1" instead of "3.1 / 10". Settings said the app reads "8 zones"; it reads nine, the eight
directions and the centre, and the centre is on your report by name. And the price screen led with
₹699 in the largest type on it while nothing is actually charged in this version — the caption
beside it now says so, in the same place your eye lands first.

**What is still open, and honestly.** Paying is still switched off and waiting on your decision about
how the ₹699 is collected. The report still cannot be sent to anyone — no share, no PDF — and that
is the next thing worth building. And when the app cannot work out where your rooms sit, it still
hands you a grid of squares to drag; placing them by tapping your own plan instead is still ahead.

### Tuesday 11 August — your report now uses the names printed on your own plan

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.11.0/vastufirst-v0.11.0.apk
**This is the one to install.**

**Your report calls each room what your plan calls it.** If your drawing says MASTER BEDROOM 1 or
ATTACHED TOILET 1, that is what the room list says — not our own generic "Master" or "Bedroom 2".
Until now the checking screen showed your words and the report quietly swapped in ours, so the same
room had two different names two screens apart. Your words now carry all the way through, and they
survive saving a home and opening it again later. A home you draw by hand still gets our names,
because there is no printed plan to read them off.

**The app skips the "mark your front door" question about twice as often.** It has always been able
to read your door off the plan when the plan names its own entrance — but we had never counted how
often that actually happened, so we counted it, across every plan we already hold. The answer was 7
plans in 24. Nearly every plan that still asked did so for one reason: it prints PORCH or VERANDAH
rather than the word "entrance". Those are the covered way in, so the app now reads them too, and
the number is 13 in 24. It still says on screen which words on your plan it read the door from, and
you can still move it. Where it is not sure, it still asks — a front door in the wrong place is the
most expensive mistake this app can make, so a question is cheaper than a guess.

**The picture of your plan is about a third bigger**, on both the checking screen and the report. It
had ended up filling roughly two-thirds of the width, which made a floor plan hard to read on a
phone. The cost is about one room row before you scroll.

**The report reads shorter again.** Another one word in eight of our own wording is gone. Nothing
about your home has been removed: every finding, every reason, every remedy and every Sanskrit name
is exactly as it was — only our joining words went.

### Monday 10 August, evening — the app now asks you for two things, and then just tells you

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.10.0/vastufirst-v0.10.0.apk
**This is the one to install.**

**The whole path through the app is shorter.** Photograph your plan, say which way North is, and
you are looking at your report. The free summary screen that used to sit in between — a number, a
picture and the three worst problems, followed by an offer to read the report — is gone. It was a
summary of a document you were about to be handed, shown on the way to being handed it.

**Your rooms are now one list, worst first.** Each room shows its direction and a single word —
**Review** or **Aligned** — and opening one tells you why, where the tradition says it, and what to
do about it. The report used to split your home across three sections you had to choose between,
which meant "where is my kitchen?" had three possible answers depending on how the kitchen had
scored. Nothing has been removed: every problem the report raised before it still raises, with the
same reason and the same suggestions.

**Tap a room on your plan and it lights up in the list. Tap it in the list and it lights up on the
plan.** Every room we read is outlined on your own photograph, so you can see what is tappable
rather than hunting for it. On the checking screen the plan now stays put while only the list
scrolls, and more of your rooms fit on screen at once.

**Marking your front door and marking North both show you what to do.** A ring travels around your
home's outline until you tap a wall; a ring pulses on the compass dial until you turn it. Both stop
as soon as you have done it.

**After you tap "read my home" there is a short pause with a progress bar, and then your score
counts up to its number.** Worth knowing: the app works your score out in a fraction of a second.
That pause is deliberate, to let the moment land — it is not the app struggling.

**Two things have been taken away on purpose.** The score no longer moves while you turn the
compass dial, because a number that changes as you turn invites you to hunt for the best-scoring
North rather than the true one. And "use my phone's compass" is gone; North is set by hand. That one
has a real cost and you should know it: if North is set wrongly, every direction in the report is
wrong while still looking right. The screen still asks you to confirm what North means for your own
kitchen before it scores anything.

**Small things:** the report opens with your own photograph rather than our drawing of your home,
with buttons under it to change North or the front door. The report reads shorter again — our own
wording, never the tradition's. And the choice of which AI reads a plan has moved into Settings,
out of the way.

### Monday 10 August, later — the boxes on "check what we read" now match the rooms

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.9.2/vastufirst-v0.9.2.apk
**This is the one to install.**

**When you tap a room on the "check what we read" screen, the shaded box was bigger than the room
it was pointing at.** Not by a lot, and not on every room — but reliably on the small ones. A toilet
would be shaded about a tenth larger than it really is, while a big living room was fine, and it
made the whole screen look approximate at exactly the moment it is asking you to confirm we read
your plan correctly.

**The cause was a habit of the plan-reading service, not of the app.** It draws a box around each
room with a small margin left over — roughly five inches of extra wall, added the same whether the
wall is four feet long or eighteen. On a small room that is a tenth of it. On a large one it
disappears.

**Your plan already prints the answer, so we now use it.** Where the drawing says "KITCHEN 8'1" X
9'5"", we shade that size, in that position. Measured across every test plan we hold, the rooms on
a page went from disagreeing with each other about their own plan's scale by as much as double, to
agreeing exactly.

**Rooms are also drawn the right way round now.** A bedroom that is taller than it is wide was
sometimes shaded wider than tall, which is the sort of thing you notice immediately and cannot
un-see. It now follows the printed measurements, the same way the layout behind your score already
does — so the picture and the score agree.

**Your score is untouched by this.** It is the same number, worked out the same way, from the same
layout. This changes only what you are shown while checking our reading.

**Where a plan prints no sizes**, nothing changes — those boxes stay as they were, and the screen
still says "roughly", because that is what they are.

### Monday 10 August — the penalty on a room that only clips a wrong area was double what it should have been

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.9.1/vastufirst-v0.9.1.apk
**This is the one to install.**

**Last week's fix was right but only half applied.** A room that overlaps a place it should not be
now keeps its marks in proportion — a room a fifth over the line keeps four fifths of them, which is
fair. But the separate penalty added to the home's total did not follow the same rule. It reached its
full weight as soon as a room was merely *half* over. So that same room kept four fifths of its marks
and was charged two fifths of the penalty: one corner counted against the home twice, at two
different rates.

**The penalty is now charged at exactly the same proportion as the marks.** A room a tenth over the
line costs a tenth of the penalty. Only a room genuinely built in the wrong place costs all of it,
exactly as before.

**Across the real home plans we test against, the typical score went from about 4 out of 10 to
about 5, and every single one of the eight improved. None went down.** The best-laid-out home still
scores 10 out of 10, and the worked example we check every build against is unchanged.

**Nothing was hidden to lift those numbers, and that was checked rather than assumed.** Before
changing anything we measured eight different ways of making scores fairer against every test home.
Most of them worked by simply not mentioning some problems any more — one of them would have stopped
telling you about eighteen of the thirty-seven problems those homes have. **The one we chose removes
nothing.** Every problem the report raised before, it still raises, with the same reason and the
same suggestions.

**One thing worth knowing while we were in there.** We checked whether the front door was dragging
scores down, since most door positions are considered unfavourable. It is not — only one of the eight
test homes has its door in an unfavourable position at all, and making that position cost less does
not move the typical score at all. So we have left it alone rather than change something that reads
as generous but fixes nothing.

**If you saved a home in an earlier version, its score will have changed.** The app now tells you so
and explains why in plain words, rather than simply showing a different number the next time you open
it.

## Week of 3–9 August 2026

Thirteen new versions this week. The short version: **the app stopped redrawing your home and started
working on your own plan instead**, a better plan reader went in, the whole app was read through end
to end and fixed in three rounds, the question of languages is settled — the app is English — **the
report itself was rebuilt so it can actually be read**, with the entrance, kitchen and toilets now
free, and **the scoring was found to be harsher than it should have been, and corrected**.

### Sunday 9 August, last of the day — the scores were harsher than they should have been

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.9.0/vastufirst-v0.9.0.apk
**This is the one to install.**

**A room that only clipped the corner of a place it should not be was being treated as though the
whole room were sitting there.** The app divides a home into nine parts, and some rooms are not
meant to occupy certain ones. If a bedroom overlapped a forbidden part by a corner — five square
feet on a thousand-square-foot flat was enough — that bedroom lost every mark it had, exactly as if
it had been built entirely in the wrong place. Worse, the same corner was counted against the home a
second time in the overall total. One small overlap, charged twice.

**On real flats this was the difference between a fair reading and a discouraging one.** In an
ordinary home something almost always sits over the middle of the plan, and the middle is one of the
places most rooms are not meant to be. So home after home came out at one or two out of ten, no
matter how sensibly it was laid out.

**A room is now marked down by how much of it is actually in the wrong place.** Mostly where it
should be, with a corner over the line, and it keeps most of its marks. Genuinely built in the wrong
place, and it loses them all, exactly as before.

**Nothing has been hidden to make the numbers look better.** Every problem the report listed before,
it still lists, in the same words, with the same reason and the same suggestions. That was the line
we would not cross: the score is our own invention and can be corrected, but what the tradition says
about your home is not ours to soften. Only the number moved.

**Across the real home plans we test against, the typical score went from about 2 out of 10 to about
4**, and the worst of them from 0.5 to 3.1. Homes that were genuinely well laid out were always
scoring well and still do.

**Separately, a house could be called "long and narrow" when it was not.** A home whose proportions
sit exactly on the line between ordinary and elongated could be judged either way, depending on
nothing more than where on the page it had been drawn. It is now judged on its shape alone, and a
home exactly on that line counts as ordinary.

### Sunday 9 August, earlier — the front door is filed where it belongs

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.8.1/vastufirst-v0.8.1.apk
**This is the one to install** — the rebuilt report from earlier today, with three things put right.

**Your front door was always listed under "already right", whatever it actually said.** On a home
whose doorway the tradition counts unfavourable, tapping the section that promises good news showed
that door, marked unfavourable, as its very first item. The reading was correct; the heading was
wrong — and a heading that argues with what sits under it makes every other heading on the page
harder to trust. The door now goes wherever it reads: with the things that are right when it is
favourable, with the things to fix when it is not, and with the questions the schools argue about
when the sources genuinely differ.

**The three counts at the top now line up** as one neat row on a narrow phone instead of one box
sitting a line short of its neighbours.

**And the bottom of the free report is now checked before every release, not just the top.** That is
the half where the rooms you have not paid to read are listed, so it is the half worth watching.

### Sunday 9 August, earlier still — the report is rebuilt, and three rooms are now free to read

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.8.0/vastufirst-v0.8.0.apk

**The report now opens by telling you how the home did.** Until this build it opened straight into
the front door and never gave an overall verdict at all — you had to read to the end to find out
where you stood. The first thing on the screen is now the score, one plain sentence, and a single
band across the whole home showing how many rooms are already right, how many are not ideal, and how
many need work.

**What is right is shown before what is wrong.** "Already right" used to sit underneath every
problem, so someone with a perfectly decent home reached the end feeling told off. The balance is now
the second thing they see.

**One long scroll became three short ones** — what to fix first, what is already right, and what is
worth knowing — with the count on each. And every finding is now a single line you tap to open: the
room, its direction and how it read are always on show, and the whole reason, the Sanskrit name, the
deity, where the rule comes from and every remedy sit one tap underneath. **Nothing was taken out.**
Open everything and the report is exactly as long as it was; it simply no longer arrives all at once.

**Your entrance, kitchen and toilets are now free to read in full.** They are the three the tradition
weighs heaviest and the three most people already have an opinion about before they open the app.
Every other room still shows its name and how it read — only the reasoning behind it is part of the
₹699. The score and the number of problems are never hidden behind the price; a free result that
hides how many faults a home has, to sell the number, is not something we will build.

**And one real fault, found by looking at the screens rather than by any test.** If you were buying a
home, or already living in one, the report's top recommendation was to *move or resize the room on
the drawing* — advice that only makes sense while a plan is still on paper, and the opposite of what
this app promises those two readers. It had been there since the report was first written. A buyer
and a resident now see only remedies they can actually carry out.

### Sunday 9 August — the app is English, and that is now a decision rather than a wait

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.7.10/vastufirst-v0.7.10.apk

**VastuFirst is an English app, and it is staying that way.** Until this build the first screen
carried five greyed-out language buttons — Hindi, Tamil, Telugu, Marathi, Bengali — under the line
"English for now, more languages soon". They are gone, and so is the plan behind them. The screen now
ends on Continue, and the three "what brings you here?" cards sit higher up the page than before.

**The honest reason.** Most of the words in this product are not buttons and headings. They are the
Vastu writing: why a room in that direction is a problem, what the tradition attaches to it, what you
can do instead. Translating that properly means finding a person who reads Vastu, once for every
language — and running rule text through a machine translator would produce something confident and
wrong, five times over. That is not a shortcut worth taking, and a promise sitting on the first
screen with nothing behind it is not worth keeping. So the promise comes off.

**One thing that has not changed, and should not:** the app still writes numbers the way your own
phone writes them, so a phone set to a language that uses a comma still shows 4,7 rather than 4.7.
That is your phone's own setting rather than a translation of the app, and it stays.

**And one small thing found by looking at the screen rather than by any test.** On a phone set to the
largest text size, the green tick that confirms which option you have picked was being drawn bigger
than the circle around it, so it came out as a bare diagonal slash. The circle now grows with the
text instead of cropping the tick.

### Thursday 6 August — the scan flow stops redrawing your home

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.7.9/vastufirst-v0.7.9.apk

**Your own plan is now the picture the app works on, from the first screen to the last.** Until this
build, scanning a plan ended by handing you our redrawn version of it — a grid of squares — to mark
your front door and North on. Every complaint ever made about scanning has been about that redrawing
and never about the photo, so the redrawing is out of scanning altogether. You check the rooms on your
plan, tap the wall your front door is on **on your plan**, and turn the compass around **your plan**.
The drawing grid is still there for anyone who wants to draw a home from scratch; it is simply no
longer part of reading one.

**The plan picture is now as large as the screen allows.** It was sitting at about half the width it
could have used, which made checking it against the paper harder than it needed to be.

**A long balcony is read as one balcony.** A plan that dimensions one continuous balcony in three
pieces — because three rooms open onto it — was being read as three separate balconies. It counted as
three in the score, too. It is now one, and the app still prints all three of the sizes your sheet
gives so you can check them against the paper.

**And where a plan prints its own entrance, the app no longer asks you where the front door is.** If
the sheet says ENTRY or FOYER, it reads it and tells you which wall it decided on, with a way to move
it. It only asks when the plan does not say. The front door matters more to the score than anything
else in the reading, so it says what it worked out rather than deciding quietly.

**Small thing, useful thing:** the version number inside the app now matches the version you
installed. It had said 0.7.0 for the last eight releases, so there was no way to tell one build from
another on your phone.

### Wednesday 5 August — plans that were being refused now read, and the report got shorter

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.7.8/vastufirst-v0.7.8.apk

**The app was turning away real floor plans, and that is now fixed.** It refuses 3D marketing pictures
on purpose, because they produce confident, wrong answers. But the test it used was looking at
*styling* — furniture, colour, shadows — so a perfectly flat, perfectly measurable plan that happened
to be furnished and coloured got rejected as a 3D render. It now asks the only question that actually
matters: **was this picture taken from straight above, or at an angle?** A tilted photograph cannot be
measured, whatever it shows. A furnished one seen from overhead can.

**And when it does refuse, there is now a way forward.** "Show me what you read" gives you the room
names it found, without costing a second scan. What it deliberately will **not** do is draw you a
layout: when a picture is genuinely taken at an angle, every shape in it depends on where the camera
stood, so a drawn plan would look confident and be wrong. Names are the part it reads reliably;
placing them stays with you.

**A home the app could not read is now named, and you can remove it.** These used to sit in your list
as unidentifiable rows with no way to clear them. Each one now shows which home it is and when it was
made, with its own Remove — and the confirmation tells you the real cost of removing it: a future
update might have been able to rescue it, and removing it ends that chance. Healthy homes still have
no delete; that is a separate decision.

**Dates on your saved homes stopped getting cut off** at the largest text sizes.

**The report reads shorter without losing anything.** Every zone's identity was being explained twice
on the same card. Those repeats are gone — the report is about a sixth shorter — and nothing that
makes it worth ₹699 was touched: every deity, element and Sanskrit name stays, and so does every
sentence where the report admits a limit, such as the road rule it shows you but does not count in
your score.

### Tuesday 4 August — a better plan reader, and the whole app read end to end

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.7.4/vastufirst-v0.7.4.apk

**The app reads plans with a different, better engine now.** Several were tested head to head on the
same real sheets. The one now in use reads plans better than the old one on every sheet tested, and
costs about **9 paise a scan instead of 21**. A second, stronger reader is called only when the first
one says it cannot use the picture — never routinely — so the cost stays where it is.

**Two things on your own plans were being read correctly and then thrown away.** The maid's room on
your sheet was transcribed perfectly every time and discarded, because the app only knew the singular
spelling of the word. And where a sheet prints a half-inch as a fraction, the readers type it out in
three characters and the app could not parse it — which made it give up on a fully dimensioned
eighteen-room plan as "too many rooms". Both fixed: **your tower plan now draws all sixteen of its
rooms.**

**You can now check a scan against your own photo rather than our grid** — the first version of what
Thursday's build finished. The scanned picture stays on screen with the rooms listed beside it, and
tapping a room shades roughly where on the picture it was read.

**Then the whole app was read end to end, as a stranger would, and fixed in three rounds.** The worst
of what that found:

- **The app could lose your place if your phone killed it mid-flow** and leave you on "Reading your
  home…" forever. It now picks its own work back up.
- **The privacy card was naming the wrong company.** The plan reader changed, and the card that asks
  your permission still named the old one. It now says exactly who sees the photo and what they are
  asked — and because the wording changed, everyone who agreed to the old wording is asked again.
- **"Delete all my data" was leaving every half-finished home on the phone.** It now means all.
- **Opening a saved home after a rules change quietly rewrote its score** without the card that is
  supposed to explain the change first. That silent rewrite is gone.
- **Room labels could go blank** on small tiles, and the ₹699 row broke into one character per line at
  large text sizes, with the buy button pushed below the fold.
- **Every screen before the report now reads in about half the words** it used to.

---

## Week of 28 July – 3 August 2026

### Monday 3 August — a half-drawn home waits for you, and a saved home can be corrected

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.6.6/vastufirst-v0.6.6.apk

**If you stop halfway through drawing a home, it now waits until you ask for it back.** Before, an
unfinished home was pushed at you: whatever you had half-drawn came back the next time you opened
either the drawing screen or the upload screen, and the only way to a blank grid was to notice a card
and press "start this home again". Worse, starting a second home wrote over the first one's only copy.
Now every unfinished home gets its own row in a "Still to finish" group above your saved homes,
showing how many rooms are on it and when you last touched it — and no score, because it has not been
through the scoring yet. Tapping that row is the only way back into it, and the ✕ asks you to confirm
before it throws anything away.

**"Take a photo" now opens the camera.** It was opening the photo gallery — the same picker as the
button above it, just under a different name — so anyone holding a printed plan had no way to
photograph it from inside the app. It opens the camera now. **The app still asks for no camera
permission**, because it hands the job to your phone's own camera app rather than taking the picture
itself. That was deliberate: the app's promise of a single permission is worth protecting.

**A home you have already saved can have its rooms and its North corrected.** Renaming was the only
thing a saved home offered, so a wrongly marked North — which the entire score is built on — meant
deleting the home and drawing it again from scratch. Two buttons now sit directly under the coloured
plan picture, where you would notice something is wrong: change the rooms or the front door, and
change which way North is.

**Buying a home is now offered before building one, and the report respects the difference.** Most
people opening this app are looking at a home somebody else has already built. Until now, a buyer got
exactly the same report as a builder: every problem headed "✦ Change the layout — free now", and an
opening line promising nothing had been built yet. You cannot move a wall in a flat you are only
considering buying. For buyers, and for anyone already living in the home, the report now drops the
layout advice altogether and gives remedies instead.

### Scanning a floor plan — working, but still being fixed

**This is the part of the app still under active work, and it is fair to call it in progress.** Since
Sunday it has been through four rounds of fixes, each one driven by a real plan sheet rather than a
guess, and each result checked by looking at the drawn plan next to the paper it came from.

**What is better now:**

- **The app reads the sizes printed on the plan.** Most builder sheets print something like
  `3.72m X 4.50m` under every room name, and we were reading the name and ignoring the size — so
  rooms came out as identical stock boxes. Across nine real plans, 116 of 129 rooms now come back at
  the size their sheet prints. On a sheet that prints no sizes, it invents none.
- **Rooms stopped disappearing.** A rule meant to ignore dressing areas was quietly deleting real
  rooms — nine toilets, a prayer room, balconies, a study and a servant's room across the plans we
  test against, including the toilet off your own master bedroom. Every named room now survives to
  the grid.
- **Eleven ways real sheets write room names** — `BED RM.-01`, a bare `KIDS`, `SERV. RM`, `TOIL`, a
  hand-lettered `Pojo` — used to come back as "we didn't recognise this name". They are understood
  now. One hand-drawn sheet went from 8 of its 12 rooms placed to 11 of 12.
- **The home fills the grid instead of floating inside it.** A rounding margin was pulling whole
  rooms off the outer wall they sit against. Your own flat now reaches its own west wall, and its
  living/dining is finally drawn wider than deep, the way your sheet prints it.
- **A balcony no longer towers over the bedrooms.** When a strip like a balcony prints only one
  dimension (`BALCONY 6'-0" WIDE`), that number is how deep the strip is — we were reading it as no
  size at all, so the balcony kept its oversized sketch while every properly sized room shrank around
  it. On your 336 sq ft plan it was taking up a quarter of the page.

**What is still wrong, in the order you put it:**

1. **A small home still gets a large, mostly empty grid.** A 336 sq ft one-bedroom is drawn on a grid
   sized for a villa, so the cells are huge and you have to scroll past empty space. Giving small
   homes a smaller grid is a real change to the drawing screen and will come with its own build.
2. **A sheet with a builder's logo and branding on it still confuses the reader** and pushes rooms to
   the left of where your sheet puts them. A clean copy of the same sheet reads close to the paper.
   The open question is whether a badly confused reading should hand you the "place the rooms
   yourself" screen instead of drawing something confidently wrong.

⚠ **Next time you scan a plan, please put the drawn result next to the paper and tell me what is
off.** The scans I can run here are only ever as good as the sheets I have.

### Sunday 2 August, later — reading a plan properly

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.6.1/vastufirst-v0.6.1.apk

**You sent a screenshot of your own flat scanned in, and you were right: it was not good enough.**
Ten small boxes with empty squares between every one of them, on a plan where every room shares a wall
with its neighbour — and then the app asked whether your home was "cut off" in the north-west, pointing
at the space beside your master bedroom. Neither answer to that question was right, because the
question was wrong.

**What was actually happening.** The app rounds each room onto a grid of squares. It was rounding every
room separately, which only keeps two rooms touching if the plan reader reports their shared wall at
exactly the same number. It never does — it draws just inside the wall, and wobbles a little on every
edge — so one room's right-hand wall came back a hair to the left of its neighbour's left-hand wall,
they rounded to different squares, and a gap opened between two rooms that touch in your real flat.
Every room did that to every neighbour, which is why the whole plan fell apart instead of just
loosening slightly.

**The fix.** Two walls within half a square of each other are now treated as the same wall before
anything is rounded. Rebuilding your sheet the way the reader actually sees it: **41 of 80 squares were
empty, in one hole big enough to swallow three rooms — now 26 in four small ones**, and every real
join is back: master bedroom to bedroom, master toilet under the master bedroom, toilet to living,
kitchen under living. What is still empty is the true shape of your flat, which really does have blank
space at the top-right and below the two toilets.

**And the "is this part of your home?" question now only appears for a genuine missing corner** — it
has to sit in a corner of the plan and be big enough to be a piece of building. Loose space between
rooms is simply counted as part of your home, and the app says so instead of asking.

⚠ **Please re-scan your plan on the next build and tell me whether the rooms now sit against each
other.** I have matched it against a faithful rebuild of your sheet, but only your phone and your real
plan can confirm it.

### Sunday 2 August — the prayer-room decision, applied

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.6.0/vastufirst-v0.6.0.apk

**You decided where the prayer room belongs, and the app now follows it.** North-east is the right
place, north and east are fine, and no position is treated as a fault — including the centre, which is
where the old texts put it. That last part was deliberate: marking the centre as a fault would mean the
report calling one school's advice a defect on the same page where it prints that advice. We decline
that reading; we do not condemn it.

**What it did to the numbers.** Prayer rooms were not scored at all before, so every home with one is
now read differently. On the sample home you have been shown, the prayer room is in the north-west, and
the score comes out at exactly the same number it had before — the new room pulls the average down by a
quarter of a point and that rounds away. **That is a coincidence of the arithmetic, not a design goal.**
A home with its prayer room in the north-east will score higher than it used to; a badly placed one will
score lower.

**Anyone who already saved a home is told.** The first thing they see is a card explaining what changed
in ordinary words, with their old score and their new one side by side, and one button. Nothing is
rewritten until they have read it. Homes whose number did not move are still listed, saying so — going
quiet because the maths happened to land in the same place would not be honest.

**Two things fixed by looking at the screens rather than by any automated check.** The "where the schools
disagree" section — the part that admits the tradition contradicts itself — had never once appeared in a
screenshot, because it sits at the bottom of a long report. When we finally photographed it, it read like
our own private notes: *"Idols on W/SW wall"*, *"NE/N/E benign, S/SW/SE/NW harmful"*. Every line is
rewritten in plain words with every direction spelled out. And the small "8 zones / 16 zones" switch at
the top of the report was losing its first letter at large text sizes — a customer with big text saw a
word with a chunk missing.


### Saturday 1 August, late — the report release

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.5.0/vastufirst-v0.5.0.apk

The report is the thing a customer pays ₹699 for, and until today it explained almost nothing. This
release is entirely about fixing that. **Open the report on the sample home and read it end to end —
it is a different document.**

**Every problem now has a reason you could repeat to somebody else.** Before, a problem was a room
name, a direction and a single line — and the catch-all line said "this room sits in a zone its
placement rule prohibits", which tells a paying reader nothing at all. Now each one says what that
direction *is* in the tradition — its Sanskrit name, its presiding deity, its element, what the
tradition attaches to it — why the tradition objects to that room being there, and where the room
belongs instead.

**Every problem now has remedies for that problem in that direction.** Before, thirteen of the
fifteen problems offered the same two lines: move it, or do a Vastu Shanti puja. There were only six
remedies in the whole app. There are now twenty-eight, and each problem draws the ones that actually
apply to it — where to stand the cooking platform, which way to sleep, what to keep out of a
north-east store, and so on.

**And where the tradition genuinely has no remedy, the report says so** instead of inventing one.
That sentence appears above the remedies on the problems it applies to: the texts say where a toilet
belongs, and record no cure for putting it somewhere else. Not making Vastu up is the whole point of
this product, so a remedy invented to fill a table would have been the worst thing we could ship.

**Every remedy now says where it comes from** — from a classical text, from traditional practice, or
modern 20th-century practice — right on the line. Before, a rock-salt bowl someone invented in the
1900s and a rite prescribed in the Mayamatam arrived on the page looking exactly alike.

**Rooms rated "not ideal" now appear in the report.** ⭐ This was the most serious of the lot. They
fell between the problems list and the already-right list and appeared *nowhere* — while the free
score screen counted them in "N more issues" to justify the ₹699. We were selling issues the report
never showed. They have their own section now, each with why it is not ideal and where the tradition
would put that room, and the section says plainly that none of them is a defect.

**Rooms that are already right now say why.** Being told why something is right is worth as much as
being told why something is wrong, and that section used to be a room name, a direction and a tick.

**Your front door has a section at last.** It is the single most important thing in the whole
reading, and the app has always known which of the 32 named door positions it stands on — the names
and their meanings have been sitting in the app since the first build and were never shown to
anybody. The report now names the position, says what the tradition attaches to it, and explains why
the door counts for more than any room.

**The "couldn't check these yet" list stopped printing our internal codes at the customer.** It used
to say things like "· X-09". It now says "Where your water tank and borewell sit", and tells the
reader how to get it checked.

**The free score screen now previews the paid report honestly** — it lists the real sections, with
this home's own numbers, rather than one vague count. And in this build the unlock is one tap and
free, so you can reach all of it without hunting.

**Nothing about the score changed.** No weight, no rule, no direction moved. The sample home scores
exactly what it scored yesterday. This release only changes what the report *says*.

**One thing set up for later:** when you have the Play Store account and send us the signing key,
the release now picks it up by itself. Nothing to remember on the day.

**And two things caught by looking at the screens rather than by any test.** The reasons were being
cut off mid-sentence with "…" on a small phone at large text — the paid writing, unreadable, on
exactly the phones this app is for. That is fixed at the root, and every screen we touched came out
measurably tidier than it was before this release started. The front door was also wearing a red
"Defect" badge it had not earned; it now says plainly whether the tradition counts that doorway
favourable or not.

---

### Saturday 1 August, evening — the accuracy release

**Try it:** https://github.com/aucksy/vastuFirst/releases/download/v0.4.1/vastufirst-v0.4.1.apk

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

### 2 August — the app now reads the room sizes printed on the plan

We tested a third real flat and the result was bad enough to be useful: the app showed thirteen
identical little squares in a row at the top of an empty grid, and then asked whether the space next
to them was part of the home. Nothing about that screen was right.

**What we found when we looked at what the AI actually said.** It named all fifteen rooms correctly,
in the right order, and put each one on the correct side of the home. What it did *not* do was
measure anything: every room came back as one of only three stock sizes, at tidy round positions,
with three rooms placed off the bottom of the page. We checked, and it does this on about half of the
real plans we hold. We also tried telling it not to — that changed nothing.

**The fix is to take the measurements from the plan's own printing.** Nearly every Indian plan prints
each room's size next to its name — *"3.72m x 4.50m"* — and reading printed text is the one thing
these AI models are genuinely reliable at. We simply were not asking for it. Now we do:

- on the flat we tested, **all fifteen rooms** came back with their printed size, thirteen of them
  matching the sheet exactly;
- across nine real plans, **116 of 129 rooms**;
- on a plan that prints no sizes at all, it correctly returned none rather than making them up.

**Three other things this uncovered, all now fixed.**

- Rooms the AI placed past the bottom edge were being **deleted**. On this flat that quietly lost one
  of its three balconies and squashed the utility to a fifth of its depth. The whole reading is now
  scaled to fit instead, so no room is ever thrown away for being drawn off the page.
- The drawing area was coming out **too narrow** for this home, so its long living-and-dining room —
  clearly wider than it is deep on the plan — was drawn the wrong way round. Rooms drawn the wrong
  shape sit in the wrong Vastu direction, so this matters to the score. Four rooms were affected;
  now two, and both are visible for the customer to correct.
- We had a rule that refused to place any plan with more than twelve rooms, on the grounds that it
  was probably a whole floor of flats. **This flat has fifteen — three balconies, a utility, a
  vestibule, a passage and a dressing area — which is a perfectly ordinary Indian apartment.** The
  app now tells a building apart from a home by whether the sheet has a *lift* on it, which is what
  actually distinguishes them.

**And when the app genuinely cannot place the rooms, it now says so.** Instead of a row of squares
under the heading "Place your rooms", it says these rooms are not placed yet, explains why, and asks
you to drag them where they belong — and it no longer asks questions about the shape of a home that
has not been arranged yet.

Room positions remain the AI's best guess and are always confirmed by the customer, unchanged.

**Two more things we only found by looking at the screens.** When the app cannot place the rooms, it
lays them out as a block of equal tiles for you to drag onto the plan. Those tiles were too small in
both directions. Too narrow to print the room's name — so you'd be looking at sixteen blank coloured
squares while being told to drag each one where it belongs, with no way to tell which was the
kitchen. And too small to reliably tap: each was about three-quarters the minimum size a finger
needs, which is exactly the wrong failure for an older customer. They are bigger both ways now, and
every name reads.

The automated checks had already counted the tapping problem and simply accepted it as this screen's
normal, because they only complain when a number gets worse — and it was there from the very first
render. Worth knowing: those checks tell us a screen is not broken, never that it is usable.
