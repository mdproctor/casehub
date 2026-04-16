# Session Handover — CaseHub
**Date:** 2026-04-16
**Branch (casehub):** main
**Branch (casehub-engine):** multiple — see Open PRs below

---

## Where We Are

Persistence decoupling PRs 1 and 2 open for review. PR 3 (engine decoupling — strip JPA) not yet started.

**Awaiting upstream maintainer review on:** #66, #67, #70.

---

## What Was Done This Session

**PR #67 review fixes (three rounds):**

Round 1 — upstream maintainer asked to merge SQL files and remove migration code:
- Misread the request: deleted all SQL files, switched Quartz to RAM, set drop-and-create. This broke Quartz persistence and removed the explicit schema.

Round 2 — corrected:
- Restored Quartz to `jdbc-cmt` (RAM only in engine tests via `%test.quarkus.quartz.store-type=ram`)
- Merged 4 SQL files into single `V1__schema.sql` in `casehub-persistence-hibernate` (Quartz + application tables, final state, no incremental migrations)
- Moved Flyway + JDBC URL to `casehub-persistence-hibernate`; engine keeps JDBC pool deps for Quartz
- Engine tests use `%test.quarkus.hibernate-orm.schema-management.strategy=create` (no Flyway in engine)
- Updated `CLAUDE.md` in casehub-engine: single SQL schema file, no versioned migrations
- Fixed CI test: `Instant.now().truncatedTo(ChronoUnit.MICROS)` in `JpaCaseMetaModelRepository.save()`
- Reverted unnecessary `SignalTest.java` changes (dedup was already fixed by treblereel's 2e66a8d)

**Blog post published:** "The Dedup Wasn't Broken. The Test Was." — covers Flyway removal rationale, Instant precision gotcha, signal dedup test analysis.

---

## Open PRs in casehubio/engine

| PR | What | Base | Status |
|---|---|---|---|
| #65 | SPI interfaces | main | MERGED |
| #66 | casehub-persistence-hibernate scaffold + entities | feat/persistence/spi | Open |
| #67 | JPA repositories + 17 tests + schema rework | feat/persistence/hibernate-entities | Open |
| #70 | casehub-persistence-memory — 30 unit tests, no Docker | feat/persistence/hibernate | Open |

---

## Immediate Next Steps

1. **PR 3** — engine decoupling: strip JPA from `CaseInstance`, `EventLog`, `CaseMetaModel`; refactor 14 handlers to inject repositories; engine tests run without Docker. Issue: casehubio/engine#69. Plan notes: `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` § "PR 3".
2. **Rebase `feat/persistence/memory`** onto upstream/main once #65–67 merge.

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` | PR 3 pre-plan notes (handler refactoring map, domain POJO changes) |
| `docs/superpowers/plans/2026-04-16-persistence-memory.md` | PR 2 implementation plan (completed) |
| `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` | casehub-engine rules (single SQL schema file, no versioned migrations, RAM Quartz in tests) |
| `casehub-persistence-hibernate/src/main/resources/db/migration/V1__schema.sql` | Single merged schema file (Quartz + application tables) |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #68 | Closed by PR #70 |
| casehubio/engine | #69 | Open — PR 3 (engine decoupling) |
| mdproctor/casehub | #8 | Open — retirement tracking |
