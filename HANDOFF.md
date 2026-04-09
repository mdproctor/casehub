# Session Handover — CaseHub
**Date:** 2026-04-09
**Branch:** main (all committed and pushed)

---

## Where We Are

CaseHub is a 5-module Quarkus Blackboard Architecture framework. The POJO graph
refactor (issue #6, closed) replaced the lineage model with direct parent/child
references on `CaseFile` and `Task`. Two new persistence modules exist:
`casehub-persistence-memory` (13 tests) and `casehub-persistence-hibernate`
(6 @QuarkusTest tests with H2). `casehub-core` is now pure interfaces.

The Goal model research phase is complete. ADR-0001 accepted. Implementation plan
written. **One more research topic from the user is pending before implementation.**

---

## Immediate Next Step

**Ask the user for their additional research topic.** They said at session end:
*"I have one more research topic for you, before we progress."* — topic not yet
disclosed. Get it, do the research, then implement issue #7 per the plan.

---

## Key Files (read if task requires it)

| File | What it is |
|------|-----------|
| `docs/adr/0001-goal-model-design.md` | Accepted ADR — Goal model design decision |
| `docs/research/goal-model-research.md` | Full research: GOAP, BDI, HTN, DCR, CMMN, LangChain4j |
| `docs/research/choreography-expected-flows.md` | Trajectory/violation/discovery model (future feature) |
| `docs/superpowers/plans/2026-04-09-goal-model.md` | 5-task TDD implementation plan for issue #7 |
| `docs/design-snapshots/2026-04-09-casehub-architecture.md` | Full architecture state snapshot |
| `docs/DESIGN.md` | Living architecture document (synced this session) |

---

## Open GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #7 | Add Goal model: CaseGoal, Milestone enhancement, GoalEvaluator | Open — plan ready, awaiting research |

Issues #1–#6 are closed.

---

## Goal Model Design (summary for context)

- `CaseGoal.of(name, Milestone...)` — declared at `createAndSolve()` time
- `Milestone.when(Predicate<CaseFile>).named(String)` — satisfaction predicate over CaseFile state
- `GoalEvaluator` — runs in CaseEngine control loop, separate from task execution
- Cases without a Goal → existing quiescence behaviour (no regression)
- AND semantics only for now; OR/custom deferred
- Terminology: `Milestone` (not "Goal") for intermediate states — avoids clash with LangChain4j's `outputKey`-based "Goal"

---

## LangChain4j Agenticai (research complete, no implementation yet)

`AgenticScope` ≈ `CaseFile`, `@Agent` method ≈ `TaskDefinition`, `Planner` ≈ `PlanningStrategy`.
Neither LangChain4j nor CaseHub has a real Goal model yet — CaseHub can define the reference.
Integration plan: `casehub-langchain4j` module (depends on Goal model being stable first).

---

## Deferred

- Trajectory/expected flows (choreography observation model) — design in `docs/research/choreography-expected-flows.md`
- LangChain4j integration module
- REST API layer
- AND/OR Goal completion semantics
