# CaseHub — Design Snapshot
**Date:** 2026-04-09
**Topic:** Full architecture state after POJO graph refactor and Goal model research
**Supersedes:** *(none)*
**Superseded by:** *(leave blank — filled in if this snapshot is later superseded)*

---

## Where We Are

CaseHub is a Quarkus-based Blackboard Architecture framework for agentic AI using CMMN terminology, structured as a 5-module Maven project. The core framework (`casehub-core`) is now a pure-interfaces module with zero persistence dependencies; two persistence modules provide in-memory and Hibernate/JPA implementations of `CaseFileRepository` and `TaskRepository`. The POJO graph refactor replaced the lineage class hierarchy (`LineageTree`/`LineageNode`/`LineageService`) with direct parent/child references on `CaseFile` and `Task`, using `Long` primary keys and `UUID` for OpenTelemetry tracing. A Goal model has been designed (ADR-0001) but not yet implemented — the research phase is complete and an implementation plan exists.

## How We Got Here

Key decisions made to reach this point, in rough chronological order.

| Decision | Chosen | Why | Alternatives Rejected |
|---|---|---|---|
| Blackboard Architecture + CMMN terminology | Shared CaseFile workspace, TaskDefinitions, CaseEngine control loop | Data-driven activation without coupling between agents | Traditional workflow engines (rigid sequencing) |
| Multi-module Maven structure | casehub-core + casehub-persistence-* + casehub-examples + casehub-flow-worker | Isolates persistence dependencies; core stays lean | Single-module (examples and impls mixed with core) |
| POJO graph model | CaseFile/Task hold direct parent/child references; Long ids | Fluent traversal, type safety, lays groundwork for OOPath queries | Lineage-as-backbone (LineageTree reconstructed from PropagationContext storage) |
| Long id + UUID otelTraceId | Long for DB keys/optimistic locking; UUID for OTel only | Integer ids are efficient for B-tree indexes and @Version; UUIDs only needed for distributed tracing | UUID as primary key (poor DB performance), String caseFileId (no optimistic locking) |
| PropagationContext slimmed | Keep traceId, inheritedAttributes, deadline, remainingBudget; remove spanId/parentSpanId/lineagePath | Graph structure now in POJO; PropagationContext is just a tracing/budget value object | Keeping PropagationContext as graph backbone |
| Module-per-persistence | casehub-persistence-memory + casehub-persistence-hibernate | Consistent with Quarkus Flow pattern; each backend is truly optional | Single persistence module (mixing concerns) |
| Goal model design — [ADR-0001](../adr/0001-goal-model-design.md) | CaseGoal with named Milestones (`Predicate<CaseFile>` satisfaction), GoalEvaluator separate from task execution | Path-independent satisfaction; BDI opportunistic achievement; CMMN-aligned | Goal as final Milestone only (loses explicit caller contract); implicit via case type registry (inflexible) |

## Where We're Going

One more research topic (not yet disclosed) needs to be completed before implementation begins. After that, the Goal model implementation follows the existing plan.

**Next steps:**
1. Complete the user's pending research topic (next session)
2. Implement Goal model per `docs/superpowers/plans/2026-04-09-goal-model.md` (issue #7)
3. Design and implement `casehub-langchain4j` integration module (depends on Goal model)
4. Trajectory/Expected Flows design and implementation (see `docs/research/choreography-expected-flows.md`)
5. REST API layer (`casehub-rest-api` module)

**Open questions:**
- What is the user's additional research topic? (not yet disclosed — next session)
- Goal AND/OR semantics: `Goal.all()` vs `Goal.any()` vs `Goal.completedWhen()` — deferred to real use cases
- Goal SLA/deadline: should `CaseGoal` carry a `Duration` timeout?
- Milestone abandonment: how does the system detect that a Milestone is no longer reachable?
- LangChain4j integration shape: `CaseFileAgenticScope` adapter, `LangChain4jWorker`, `GoalOrientedPlanningStrategy` — depends on Goal model being stable first
- Trajectory model: hard constraint vs soft hint? Instance-level vs type-level declaration?

## Linked ADRs

| ADR | Decision |
|---|---|
| [ADR-0001 — Goal Model Design](../adr/0001-goal-model-design.md) | CaseGoal + named Milestones with `Predicate<CaseFile>` satisfaction; GoalEvaluator separate from task execution; AND semantics only for now |

## Context Links

- Research: [`docs/research/goal-model-research.md`](../research/goal-model-research.md) — GOAP, BDI, HTN, DCR, CMMN, LangChain4j synthesis
- Research: [`docs/research/choreography-expected-flows.md`](../research/choreography-expected-flows.md) — Trajectory/violation/discovery model
- Implementation plan: [`docs/superpowers/plans/2026-04-09-goal-model.md`](../superpowers/plans/2026-04-09-goal-model.md)
- GitHub issues: #6 (POJO graph refactor — closed), #7 (Goal model — open)
