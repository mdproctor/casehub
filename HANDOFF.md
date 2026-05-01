# Session Handover — CaseHub
**Date:** 2026-05-01
**Session:** Ecosystem consistency pass, Jandex fix, CI unblocked

---

## Where We Are

**What landed this session:**
- **#218** (Qhorus COMMAND on worker schedule) — merged to `casehubio/engine` main ✅
- **#223** (Jandex/CDI/discriminator fixes, conflict-resolved from Dmitrii's #222) — merged ✅
- **#222** — closed, superseded by #223
- **#219** (consistency pass: stale names, artifact leak, gitignore) — merged ✅
- Jandex added to `api`/`testing` modules in `ledger`, `work`, `qhorus`, `claudony` — pushed to main directly
- 49 stale `io.casehub.*` SNAPSHOT packages deleted from GitHub Packages registry

**casehubio/engine main is clean and green.**

**Two design issues opened:**
- `casehubio/qhorus#131` — generalised Channel abstraction (gateway, backends, human participation, digest problem)
- `casehubio/engine#220` — `CaseChannelProvider` needs `post()` on the SPI + `WorkerContext.channels()` access

---

## Immediate Next Steps

1. **engine#220** — add `post(CaseChannel, sender, payload)` to `CaseChannelProvider` SPI, no-op default, expose via `WorkerContext`. Do this before Qhorus design (#131) — engine need defines Qhorus scope.
2. **engine#220 → qhorus#131** — once SPI is defined, design the Qhorus Channel layer (gateway, backends, WhatsApp, history store).
3. **Jandex version pin** — engine root pom has `3.1.2` pinned in pluginManagement; `ledger`, `work`, `qhorus`, `claudony` root poms still lack it.

---

## Repo Build Status

| Repo | Status |
|------|--------|
| casehubio/engine main | ✅ green |
| casehubio/ledger main | ✅ Jandex added to `api` |
| casehubio/work main | ✅ Jandex added to `casehub-work-api`, `testing` |
| casehubio/qhorus main | ✅ Jandex added to `api`, `testing` |
| casehubio/claudony main | ✅ Jandex added to `claudony-core` |

## Key References

| What | Where |
|---|---|
| Channel SPI design | `casehubio/engine#220` |
| Qhorus Channel abstraction | `casehubio/qhorus#131` |
| Clear SNAPSHOT Packages workflow | `casehubio/parent/.github/workflows/clear-snapshot-packages.yml` |
| Check GH_PAT Scopes workflow | `casehubio/parent/.github/workflows/check-pat-scopes.yml` |
