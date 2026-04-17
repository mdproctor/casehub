# Session Handover — CaseHub
**Date:** 2026-04-17
**Branch (casehub):** main
**Branch (casehub-engine):** feat/persistence/engine-decoupling (PR3 work branch)

---

## Where We Are

PR3 branch created and building clean. Ready to execute the implementation plan next session.

Treblereel merged our PR #67 content as PR #71 directly to upstream main — the branch topology changed mid-session. PR #70 was closed, replaced with PR #72 (clean branch).

**Awaiting upstream review on:** PR #72 (persistence-memory, open, Java 17 CI green, ubuntu-latest has one flaky test — leave for coworker to handle).

---

## What Was Done This Session

**PR3 plan written:**
- 27-task implementation plan at `docs/superpowers/plans/2026-04-16-pr3-engine-decoupling.md`
- Covers: SPI extension, domain POJO conversion (3 classes), 11 handler refactors, pom changes, E2E validation, docs

**CI triage on PRs #67 and #70:**
- Fixed `JpaCaseMetaModelRepository.save()` — missing `truncatedTo(ChronoUnit.MICROS)` (same Instant precision bug as CaseInstance)
- Fixed `BlackboardIntegrationTest` — `casehub-blackboard` runs before `casehub-persistence-hibernate` in Maven reactor; schema tables missing without Flyway. Fix: `%test.quarkus.hibernate-orm.schema-management.strategy=create` + `%test.quarkus.quartz.store-type=ram` in blackboard test properties
- Multiple rounds of test isolation fixes for `SignalPersistenceAndDedupTest` and `SignalTest` — settled on `findWorkerEvents(caseId, ...)` (DB query by case UUID, immune to global counter pollution)

**Branch cleanup after treblereel merge:**
- PR #72: `mdproctor:feat/persistence/memory-clean` → `casehubio/engine:main`
- PR3 branch: `feat/persistence/engine-decoupling` off `feat/persistence/memory-clean`

---

## Open PRs in casehubio/engine

| PR | What | Base | Status |
|---|---|---|---|
| #65 | SPI interfaces | main | MERGED |
| #66 | casehub-persistence-hibernate scaffold + entities | feat/persistence/spi | MERGED |
| #71 | JPA repositories + schema (treblereel's rework of #67) | main | MERGED |
| #72 | casehub-persistence-memory — 30 unit tests, no Docker | main | Open — Java 17 ✅, ubuntu flaky |

---

## Immediate Next Steps

1. **PR3** — execute plan via subagent-driven development. Resume with: "resume handover, start PR3 subagent-driven". Branch `feat/persistence/engine-decoupling` is clean and building. Issue: `casehubio/engine#69` (epic: `casehubio/engine#30`). Plan: `docs/superpowers/plans/2026-04-16-pr3-engine-decoupling.md`.
2. **Leave PR #72 CI alone** — coworker is active in the repo; don't push more fixes to casehubio/engine until PR #72 is merged or treblereel comments.

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/plans/2026-04-16-pr3-engine-decoupling.md` | PR3 full implementation plan (27 tasks) |
| `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` | casehub-engine conventions |
| `/Users/mdproctor/dev/casehub-engine/engine/src/main/java/io/casehub/engine/spi/` | SPI interfaces (CaseInstanceRepository needs `updateStateAndAppendEvent` added in Task 2) |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #69 | Open — PR3 (engine decoupling) |
| mdproctor/casehub | #8 | Open — retirement tracking |
