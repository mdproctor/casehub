# Session Handover — CaseHub
**Date:** 2026-04-15
**Branch (casehub):** main
**Branch (casehub-engine):** multiple — see Open PRs below

---

## Where We Are

Phase 2 in progress. `casehub-resilience` (PRs #52–54) and EventLog enrichment (PR #56) shipped. Persistence decoupling designed but not yet implemented — implementation plan not yet written.

**Awaiting:** co-owner review on PRs #32, #34, #35, #38, #49. All new PRs stack on top of #49.

---

## What Was Done This Session

**casehub-resilience** — 3 stacked PRs (#52, #53, #54), 38 tests:
- Backoff strategies: `BackoffStrategy` enum added to `RetryPolicy`; `BackoffDelayCalculator` in resilience module; `WorkerExecutionJobListener` now computes backoff-aware delay
- Dead Letter Queue: `DeadLetterQueue` (in-memory), `DeadLetterEventHandler` (@ConsumeEvent on WORKER_RETRIES_EXHAUSTED), unit + integration + E2E tests
- PoisonPill: `PoisonPillDetector` (sliding window), `WorkerExecutionGuard` SPI, `PoisonPillWorkerExecutionGuard` (@Alternative)
- CDI gotcha: `@Alternative @Priority(n)` globally activates in CDI 4.0 — causes AmbiguousResolutionException; removed @Priority from alternatives

**EventLog enrichment** — PR #56, 354 total engine tests:
- `ContextDiffStrategy` SPI + `TopLevelContextDiffStrategy` (default) + `JsonPatchContextDiffStrategy` (@Alternative) + `NoOpContextDiffStrategy` (@Alternative)
- `WorkflowExecutionCompletedHandler` now snapshots CaseContext before/after worker output; stores diff as `contextChanges` in metadata

**Persistence decoupling** — design only, no code:
- Key input: Francisco Javier Tirado Sarti (quarkus-flow co-creator) — orm.xml approach wrong because detached entity problem persists regardless; separate JPA entity classes required
- Design: 3 repository SPI interfaces in engine (`CaseInstanceRepository`, `EventLogRepository`, `CaseMetaModelRepository`); domain POJOs in engine (no JPA); `casehub-persistence-memory` (in-memory, no Docker); `casehub-persistence-hibernate` (JPA entities with JPA annotations, separate from domain objects, Flyway migrations)

---

## Open PRs in casehubio/engine (all waiting review)

| PR | What | Base |
|---|---|---|
| #32 | LoopControl SPI | main |
| #34 | ExpressionEngine SPI | main |
| #35 | Pre-validation | main |
| #38 | Renames + 181 tests | main |
| #49 | CaseStatus alignment + bug fix + casehub-blackboard | main |
| #52 | casehub-resilience: backoff + SPI scaffold | feat/rename-binding-casedefinition |
| #53 | casehub-resilience: DLQ | feat/casehub-resilience/backoff |
| #54 | casehub-resilience: PoisonPill | feat/casehub-resilience/dlq |
| #56 | EventLog enrichment (ContextDiffStrategy) | feat/rename-binding-casedefinition |

---

## Immediate Next Steps

1. **Write implementation plan** for persistence decoupling (`writing-plans` on spec at `docs/superpowers/specs/2026-04-15-persistence-decoupling-design.md`)
2. **Execute plan** — issue-workflow Phase 1 first, then TDD implementation of:
   - Repository SPI interfaces in engine
   - Strip JPA from `CaseInstance`, `EventLog`, `CaseMetaModel`
   - Refactor handlers to use repositories
   - `casehub-persistence-memory` module
   - `casehub-persistence-hibernate` module
3. **Wait for co-owner feedback** on open PRs before further engine work beyond persistence

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/specs/2026-04-15-persistence-decoupling-design.md` | Persistence decoupling design spec (approved) |
| `docs/superpowers/specs/2026-04-15-eventlog-enrichment-design.md` | EventLog enrichment design spec |
| `docs/superpowers/plans/2026-04-15-eventlog-enrichment.md` | EventLog enrichment implementation plan (completed) |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #51 | Open — casehub-resilience + EventLog enrichment tracking |
| casehubio/engine | #55 | Open — persistence decoupling tracking |
| mdproctor/casehub | #8 | Open — retirement tracking |
