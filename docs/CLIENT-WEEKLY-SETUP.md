# Weekly client email — how it works

A scheduled job creates a **Gmail draft** every **Sunday at 9:00 am**, addressed to
`Simran.manocha123@gmail.com`, summarising the week and linking the latest APK. It **drafts,
never sends** — you read it before the 11 am call and hit send yourself.

For that draft to be any good, something has to write down what happened during the week in
words a client understands. That is `docs/CLIENT-WEEKLY.md`, maintained by Claude Code as you
build. The Sunday job reads it and turns it into the email.

```
  Claude Code (VS Code)          Sunday 9:00 am                you, before 11 am
  ────────────────────           ──────────────                ─────────────────
  updates docs/CLIENT-WEEKLY.md  →  reads it, writes    →      review the draft,
  as work lands                     a Gmail draft               edit, send
```

---

## Part 1 — the prompt to give Claude Code in VS Code

Paste this into Claude Code once. It sets up the file and the habit of keeping it current.

---

> **Set up and maintain a client-facing running log at `docs/CLIENT-WEEKLY.md`.**
>
> **Why this file exists.** A separate scheduled job reads it every Sunday at 9 am and turns it
> into a progress email for the client, Simran. She is not a developer. She is paying for this
> build and wants to know what she can see, what is decided, and what we need from her. This
> file is the only input that job has, so anything not written here does not reach her.
>
> **Voice.** Plain English, the way you would explain it to a smart person who does not code.
> Never mention file names, class names, Kotlin, Gradle, CI, commits or tags in the week log —
> those belong in `docs/PROGRESS.md`. Write outcomes, not mechanics.
> Bad: "Fixed `NewPlanViewModel.updateGrid()` overlap clamp."
> Good: "Shrinking the plot could quietly stack two rooms on top of each other and skew the
> score. Fixed."
>
> **Create it now** with the structure below, back-filled from the existing docs
> (`docs/PHASE-2-PROGRESS.md`, `docs/E2E-ASSESSMENT-2026-07-24.md`, the git tags) so the first
> email has real history. Then **keep it current** — update it whenever you tag a build, close
> a phase item, hit a blocker, or a decision lands. Treat it as part of "done", the same way
> `docs/PROGRESS.md` is.
>
> ````markdown
> # VastuFirst — client running log
> <!-- Maintained by Claude Code. Read by the Sunday 9am client-email job. -->
> <!-- Plain English only. No file names, no code, no CI talk. Newest week first. -->
>
> ## Current state
> - **Phase:** <e.g. 2 — Android app, guided-grid path>
> - **Latest build:** <e.g. v0.3.3>
> - **APK download:** https://github.com/aucksy/vastuFirst/releases/tag/<tag>
> - **Next milestone:** <e.g. 4 August 2026 — Android app with the full Vastu engine>
> - **Timeline:** on track | at risk | slipped — <one line of why>
> - **Last updated:** <YYYY-MM-DD>
>
> ## Ready for her to try
> <!-- What she can actually DO with the current APK. 3-6 bullets, each a real action. -->
> - <e.g. Draw your home on a grid and get a Vastu score, fully offline>
>
> ## Waiting on her
> <!-- Anything blocked on the client. Include why it matters and how long it's been open. -->
> - **<what>** — <why it matters>. Needed by <date>. Open since <date>.
>
> ## Known limits right now
> <!-- Honest caveats she should know before testing, so nothing looks like a surprise bug. -->
> - <e.g. Homes with an L-shape are measured as a full rectangle for now.>
>
> ## Week log
>
> ### Week of <D>–<D> <Month> <Year>
> **Shipped**
> - <what changed, in her terms>
>
> **Decided**
> - <decision + date, e.g. Sage & Gold chosen as the colour theme (23 Jul)>
>
> **In progress**
> - <what's underway and when it lands>
>
> **Notes**
> - <anything else worth her knowing — risks, questions, things we're watching>
> ````
>
> **Rules for keeping it accurate.**
> 1. Never invent progress. If a week was quiet, say so plainly — the email can say "quiet week,
>    here is why" and that is fine.
> 2. Update **Current state** on every tag — especially `Latest build` and `APK download`, which
>    must point at the newest published GitHub Release.
> 3. Move anything the client resolves out of **Waiting on her** and record it under **Decided**
>    with the date.
> 4. Keep **Known limits** honest and current. It is what stops a known trade-off being reported
>    back to us as a bug.
> 5. Start a new `### Week of …` block each Monday. Keep at least the last 8 weeks.
> 6. Do not delete history — the email job only reads the top block, but the rest is our record.

---

## Part 2 — what happens Sunday

The scheduled job (`weekly-client-progress-email`) will:

1. Read `docs/CLIENT-WEEKLY.md`.
2. Cross-check it against the last 7 days of git history and the newest tag, so a stale file
   gets caught rather than quietly emailed.
3. Write a designed HTML email in the app's Sage & Gold palette.
4. Create it as a **draft** in your Gmail, to Simran. Nothing sends automatically.

If the log file is missing or has not been touched in over a week, it still creates a draft —
but flags the problem at the top so you notice before sending.

## One thing to do before the first send

Simran needs to be able to open the APK link. The repo is private, so:

1. Ask her for the email on her GitHub account (she can make one free at github.com).
2. Go to **github.com/aucksy/vastuFirst → Settings → Collaborators → Add people**.
3. Invite her with **Read** access.

She will then be able to download any release APK. Note this also lets her see the source code —
normal for a client who owns the build, but worth knowing before you click invite.
