# Session Handover — CaseHub
**Date:** 2026-04-16
**Branch (casehub):** main
**Branch (casehub-engine):** multiple — see Open PRs below

---

## Where We Are

Persistence decoupling PRs 1 and 2 complete and open for review. PR 3 (engine decoupling — strip JPA) not yet started.

**Awaiting upstream maintainer review on:** #65, #66, #67, #70.

---

## What Was Done This Session

**Persistence decoupling PR 2** (`casehub-persistence-memory`, PR #70):
- Wrote plan at `docs/superpowers/plans/2026-04-16-persistence-memory.md`
- Executed via subagent-driven development (5 tasks, 2-stage review each)
- 30 unit tests across 3 repositories — no Docker, no PostgreSQL, no Quarkus runtime
- Key quality fixes from review: id-idempotency guard in save(), update() throws for unknown UUID
- PR #70 open, targeting `feat/persistence/hibernate`

**PR #67 review fixes:**
- Removed all Flyway migration tooling — 4 SQL files deleted, `quarkus-flyway`, `quarkus-jdbc-postgresql`, `quarkus-agroal` removed from engine pom
- Switched Quartz from `jdbc-cmt` to `ram` store (no DB tables needed)
- Schema strategy: `drop-and-create` (Hibernate manages DDL from entity definitions)
- Created `CLAUDE.md` in casehub-engine documenting the no-migration rule
- Fixed CI failure: `JpaCaseMetaModelRepositoryTest.save_thenFindByKey_roundTrip` — `Instant.now()` truncated to microseconds (PostgreSQL precision)
- Fixed unstable test: `SignalTest.workerRunsOnceOnDuplicateSignal` — added per-orderId run counter; dedup test now uses unique orderId per run, immune to async contamination from other tests

**Issue housekeeping:**
- Closed #55 (ContextDiffStrategy — delivered by upstream via PR #61)
- Created #68 (persistence-memory module) — now Closed by PR #70
- Created #69 (engine decoupling, PR 3) — still open

**Blog and language:**
- Published blog post for April 16 session: `docs/_posts/2026-04-16-mdp01-persistence-decoupling-pr1.md`
- Fixed "co-owner" → "upstream maintainer" language throughout

---

## Open PRs in casehubio/engine

| PR | What | Base | Status |
|---|---|---|---|
| #65 | SPI interfaces (125 lines) | main | MERGED |
| #66 | casehub-persistence-hibernate scaffold + entities | feat/persistence/spi | Open |
| #67 | JPA repositories + 17 tests + Flyway removal + test fixes | feat/persistence/hibernate-entities | Open |
| #70 | casehub-persistence-memory — 30 unit tests, no Docker | feat/persistence/hibernate | Open |

---

## Immediate Next Steps

1. **PR 3** — engine decoupling: strip JPA from `CaseInstance`, `EventLog`, `CaseMetaModel`; refactor 14 handlers to inject repositories; engine tests run without Docker. Issue: casehubio/engine#69. Plan notes: `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` § "PR 3" section.

2. **Rebase `feat/persistence/memory`** onto upstream/main once #65–67 are merged (stacked PR chain).

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` | PR 3 pre-plan notes (handler refactoring map, domain POJO changes, engine pom changes) |
| `docs/superpowers/plans/2026-04-16-persistence-memory.md` | PR 2 implementation plan (completed) |
| `docs/superpowers/plans/2026-04-15-persistence-hibernate.md` | PR 1 implementation plan (completed) |
| `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` | casehub-engine project rules (no migration tooling, RAM Quartz) |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #68 | Closed by PR #70 |
| casehubio/engine | #69 | Open — PR 3 (engine decoupling) |
| mdproctor/casehub | #8 | Open — retirement tracking |

## Active Worktrees

| Worktree | Branch | PR |
|----------|--------|-----|
| `/Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory` | `feat/persistence/memory` | #70 (open) |
