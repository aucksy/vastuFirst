# VastuFirst

An offline-first Android app that scores a home's layout against Vastu rules and produces a
structured, honest report. Kotlin Multiplatform + Compose Multiplatform (Android now; iOS and
a marketing website later).

> Builds are **cloud-only** (GitHub Actions). This project is not built on a local machine —
> see `CLAUDE.md` §2.

## Module graph

```
engine/        Pure Kotlin/JVM — zone maths, 81-pada grid, 32-pada door, scoring, defects.
rules/         Pure Kotlin/JVM — versioned rule JSON dataset + loader + validation.
shared/        Pure Kotlin/JVM — enums, DTOs, result types.
data/          Pure Kotlin/JVM — repositories, persistence (SQLDelight), Supabase (later).
designsystem/  Compose Multiplatform — VastuTheme (Saffron & Ivory), tokens, components.
app/           Android application — screens, navigation, platform integrations.
```

`engine`, `rules`, `shared`, `data` resolve **zero Android dependencies** so the future iOS
port is a build-file change, not a rewrite. CI enforces this (`scripts/check-boundaries.sh`).

## Guardrails (enforced in CI)

- **Module boundary** — no `android.*` / `androidx.*` in the four pure modules.
- **Design tokens** — no raw hex / `.dp` / `.sp` outside the `designsystem` theme package;
  everything reads from `VastuTheme.colors / .spacing / .shapes / .type`.

## Build phases

Foundations → the Vastu engine (headless, tested to score the `sample-01` fixture **31**) →
Android guided-grid app (**client milestone: 4 Aug 2026**) → testing + website → reports/AI/
languages → iOS + payments → launch. Full plan in
`Documents/VastuFirst-Android-Implementation-PRD.md`.

## Status

**Phase 0 — Foundations.** Six-module scaffold, the Saffron & Ivory theme, locale-aware
typography ramp, and CI guardrails. Screens and the scoring engine come next.
