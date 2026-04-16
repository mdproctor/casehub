# Session Handover — CaseHub
**Date:** 2026-04-16
**Branch (casehub):** main
**Branch (casehub-engine):** multiple — see Open PRs below

---

## Where We Are

Phase 2 in progress. PR 1 of persistence decoupling is complete — 3 stacked PRs open on casehubio/engine, awaiting review. PRs 2 and 3 not yet started.

**Awaiting:** co-owner review on PRs #32, #34, #35, #38, #65, #66, #67. All previous PRs (#49, #52–54, #56, #62) merged into main today.

---

## What Was Done This Session

**Persistence decoupling PR 1** — 3 stacked PRs, 17 tests:
- Wrote implementation plan at `docs/superpowers/plans/2026-04-15-persistence-hibernate.md`
- Executed via subagent-driven development (8 tasks)
- SPI interfaces: `CaseMetaModelRepository`, `CaseInstanceRepository`, `EventLogRepository` in `engine/spi/`
- `casehub-persistence-hibernate` module: 3 JPA entity classes + 3 `@ApplicationScoped` Hibernate Reactive repositories
- 17 integration tests (4 + 4 + 9) — passing with Podman via Dev Services
- Split into 3 stacked PRs for piecemeal review; rebased onto `upstream/main`

**Key discoveries:**
- `VertxContextSupport.subscribeAndAwait()` — correct way to call reactive Panache from JUnit test threads in Quarkus 3.x (classpath already has it via `quarkus-vertx`; no extra dep)
- `TESTCONTAINERS_RYUK_DISABLED=true` written to `~/.mavenrc` (Podman doesn't support Ryuk)
- Always run `mvn install -DskipTests -q` from engine root before `mvn test -pl casehub-persistence-hibernate` when upstream modules changed (memory entry saved)

**PR #49 cleanup:** removed 3 stray files (`beans.xml`, 2 migration files) from the branch.

---

## Open PRs in casehubio/engine

| PR | What | Base |
|---|---|---|
| #32 | LoopControl SPI | main |
| #34 | ExpressionEngine SPI | main |
| #35 | Pre-validation | main |
| #38 | Renames + 181 tests | main |
| #65 | SPI interfaces (125 lines) | main |
| #66 | casehub-persistence-hibernate scaffold + entities (292 lines) | feat/persistence/spi |
| #67 | JPA repositories + 17 tests (733 lines) | feat/persistence/hibernate-entities |

---

## Immediate Next Steps

1. **Write plan for PR 2** — run `writing-plans` using `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` § "PR 2" section. Module: `casehub-persistence-memory`, in-memory implementations, unit tests, no Docker.
2. **Execute PR 2** — branch `feat/persistence/memory` from `feat/persistence/hibernate` (or rebase to main if #65–67 merged)
3. **Write plan for PR 3** — engine decoupling (strip JPA from domain objects, refactor 14 handlers)

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/specs/2026-04-15-persistence-plan-notes.md` | Full PR 2 and PR 3 pre-plan notes (detailed code for in-memory impls and handler refactoring map) |
| `docs/superpowers/plans/2026-04-15-persistence-hibernate.md` | PR 1 implementation plan (completed) |
| `docs/superpowers/specs/2026-04-15-persistence-decoupling-design.md` | Approved design spec |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #55 | Open — persistence decoupling tracking |
| mdproctor/casehub | #8 | Open — retirement tracking |
