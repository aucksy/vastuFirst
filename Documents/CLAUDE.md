# CLAUDE.md

> **How to use this file:** Save it as `CLAUDE.md` in the root of a new project.
> Claude Code reads it automatically at the start of every session and must follow it.
> Fill in the `<...>` placeholders once at kickoff, then delete this quote block.

---

## 1. Project facts

- **Project name:** `<name>`
- **What we're building:** `<one line — e.g. Android + iOS app + marketing site>`
- **Stack (change if this project differs):**
  - App: Expo + React Native (TypeScript)
  - Backend: Supabase
  - Website: Astro or Next.js on Cloudflare Pages
  - Builds: EAS Build (free tier)
- **Commands:**
  - Install: `<e.g. npm install>`
  - Run: `<e.g. npx expo start>`
  - Test: `<e.g. npm test>`
  - Lint/format: `<e.g. npm run lint>`
- **Where things live:** `<e.g. app/ = screens, lib/ = shared code, supabase/ = db>`

---

## 2. How you (Claude) must work

**The loop for anything bigger than a one-line change:**
1. **Plan first.** Enter plan mode. Read only the files you need, then show me a numbered plan. Do **not** write code yet.
2. Wait for my "go."
3. **Do one step, then stop and show me the diff.** Do not run the whole plan unattended.
4. **Prove it works.** Run the tests and show me the real output — never just claim it passes.
5. **Commit** with a clear message. Open a PR if we're using them.
6. Move to the next step.

For trivial edits (typo, rename, log line), just do it — no plan needed.

---

## 3. Golden rules

- **Ask, don't assume.** If a missing detail would change the cost, the design, or the result, stop and ask me. See Section 4.
- **Simple over clever.** Fewest files, fewest dependencies, least new tooling. Solve the root problem, not the symptom.
- **Don't rewrite working code** unless I ask. Preserve what already works.
- **Read before you edit.** Understand the existing code and follow its conventions.
- **Use Serena instead of reading whole files.** Once Serena is set up (Section 7), navigate and edit with its symbol tools (`find_symbol`, `find_referencing_symbols`) rather than dumping whole files into context. If a `/compact` makes you forget Serena exists, start using it again — I'll remind you.
- **No secrets in the repo.** Never write API keys, tokens, or passwords into any file. Use a `.env` file (git-ignored) and ask me to fill it in.
- **Free first.** Use free tiers by default. Never sign us up for anything paid without asking. See Section 5.
- **Say what you're unsure about.** Separate facts from guesses. If confidence is low, say so.
- **Keep context clean.** If we switch to an unrelated task, remind me to `/clear`.

---

## 4. When to STOP and ask me to do something

Some things only I can do. When you hit one of these, **stop, post an `ACTION NEEDED` block (format below), and wait for me.** Do not work around it, fake it, or guess.

Stop and ask me when you need:

- **A decision** — which option, scope, name, design direction, or trade-off to pick.
- **Plan approval** — before implementing anything non-trivial.
- **An account created** — Apple Developer, Google Play, Supabase, Cloudflare, GitHub, domain registrar, email service.
- **A secret or key** — API keys, tokens, connection strings, signing credentials.
- **A purchase or a paid upgrade** — anything that costs money (see Section 5).
- **A store / legal step only I can do** — Apple D-U-N-S number, Google Play's 12-tester closed test, App Store Connect setup, privacy policy sign-off, DPDP/consent decisions.
- **A login** — anything behind a dashboard you can't reach.
- **Real-device testing** — anything that needs my phone or physical hardware.
- **A destructive action** — deleting data, dropping tables, force-pushing, resetting anything.

---

## 5. Money rules

- Default to the **free tier** of every service.
- **Before anything costs money**, stop and tell me: what it is, how much, one-time or recurring, and whether there's a free alternative. Wait for my yes.
- These costs are known and unavoidable — flag them early so I can bill the client, don't spring them at launch:
  - Apple Developer: **$99/year**
  - Google Play: **$25 one-time**
  - Domain: **~₹750–1,100/year**
- If we're about to cross a free-tier limit (e.g. Supabase pausing, EAS build minutes, bandwidth), warn me **before** we hit it, not after.

---

## 6. The `ACTION NEEDED` format

When you need me to do something, post exactly this and then stop:

```
🙋 ACTION NEEDED (you)
What: <the one thing I need to do>
Why: <why it's blocking you>
How: <exact steps or command, if you know them>
Then: <what to tell you so you can continue>
```

Post one block per blocker. If several things are blocking you, list them all at once so I can knock them out together.

---

## 7. Kickoff checklist (run this on a brand-new project)

At the very start of a new project, walk me through this — pausing with `ACTION NEEDED` wherever you need me:

1. Confirm the stack and fill in Section 1 with me.
2. Turn my brief into a short `plan.md` in the repo (goals, scope, what's out of scope).
3. Set up the repo: create the project, add `.gitignore`, add a git-ignored `.env`, first commit.
4. Ask me for any accounts/keys you'll need soon (Section 4) — as one list, early.
5. **Set up Serena** (semantic code tools — keeps context lean once there's real code). Do this unless the project is a tiny throwaway, where it adds little value:
   - Check whether `uv` (the Python package manager Serena needs) is installed. If not, post an `ACTION NEEDED` asking me to install it, or ask permission to install it for me.
   - Get the **current** official install command from `github.com/oraios/serena` — do **not** use an MCP marketplace, its commands are outdated. (As of mid-2026 it was roughly: `uv tool install -p 3.13 serena-agent`, then `serena init`, then `claude mcp add --scope user serena -- serena start-mcp-server --context claude-code --project-from-cwd` — but verify against the repo first.)
   - Show me the command, run it once I say go, and confirm the MCP is connected.
6. Set up lint/format/test so we have green checks from day one.
7. Only then start building, one feature at a time, using the loop in Section 2.

---

## 8. Notes for this project

`<Anything specific: client quirks, deadlines, must-use libraries, things that broke before.
Keep this short. If a rule must never be broken, tell me and I'll put it in a hook or CI instead.>`
