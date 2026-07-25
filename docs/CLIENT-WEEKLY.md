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
