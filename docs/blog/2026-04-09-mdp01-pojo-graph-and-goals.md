# CaseHub — From Prototype to a Real Project

**Date:** 2026-04-09
**Type:** day-zero

---

## What I was trying to achieve: getting CaseHub onto a proper foundation

CaseHub had been sitting in a local repository for a few weeks. Good
foundation — Blackboard Architecture, CMMN terminology, TaskDefinitions
that fire when their entry criteria are met. But no GitHub, no issue tracking,
no test coverage, and a design that had drifted from its original intent in a
few important ways. Today was about fixing that.

## What we believed going in: the architecture was sound, the details weren't

The core architecture held up. A CaseFile as a shared workspace, TaskDefinitions
as specialists, a CaseEngine running the control loop — these were right. What
wasn't right were three things I'd accepted as reasonable trade-offs: the graph
model, the identity scheme, and the absence of goals.

## The graph model was wrong, and fixing it was the right call

The original design tracked lineage through `PropagationContext` — a separate
object carrying `spanId`, `parentSpanId`, and `lineagePath`. To traverse the
execution hierarchy you had to go through `LineageService`, which rebuilt a
`LineageTree` from stored contexts. Filtering meant accessing `LineageNode`
wrappers and looking up properties separately.

That's the wrong way round. Think of it like XPath versus OOPath: every node
in the graph had to look up content to filter on its properties, instead of
the objects themselves being the graph. We replaced the entire model.

`CaseFile` now carries `getParentCase()`, `getChildCases()`, and `getTasks()`
directly. `Task` carries `getOwningCase()` and `getChildTasks()`. `LineageTree`,
`LineageNode`, and `LineageService` are gone. `PropagationContext` still exists
but as a slim value object — just `traceId` for OTel correlation, `inheritedAttributes`,
and the budget fields. No graph backbone.

String IDs became `Long` primary keys — right for Hibernate `@Version` optimistic
locking. The `UUID` stayed but only for OpenTelemetry correlation, not for graph edges.

We extracted persistence into dedicated modules: `casehub-persistence-memory` for
tests (zero external dependencies), `casehub-persistence-hibernate` for production
(Panache entities, H2 for tests, PostgreSQL target). `casehub-core` is now purely
interfaces — one persistence module per backend, consistent with how Quarkus Flow
organises its extensions.

## Goals were missing, and I'd known it for a while

The design document had a section called "Goals vs Tasks" with a decision: task-only
for MVP, revisit in Phase 3. Phase 3 never arrived.

The consequence: `createAndSolve()` has no way to express what the caller actually
wants. Cases complete on quiescence — when nothing more can fire. That conflates
"nothing left to run" with "we achieved what we set out to do." They're not the
same thing.

We spent time doing real research — GOAP, BDI, HTN, DCR graphs, CMMN, KAOS, the
LangChain4j agenticai module. The insight that keeps coming up across all these
systems: a goal is a predicate over state, not a checklist of tasks that ran.

The BDI framing fits CaseHub best. A desire (the Goal) and an intention (the
PlanItems executing TaskDefinitions) should be architecturally separate. If a
goal is already satisfied before any work starts, don't plan anything. If it
becomes impossible, drop it — don't let the case run indefinitely.

The design: a `CaseGoal` declared at `createAndSolve()` time, composed of
named `Milestone`s, each a `Predicate<CaseFile>`. A `GoalEvaluator` runs in
the control loop separately from task execution, testing satisfaction after each
iteration. Cases without a Goal fall back to existing quiescence behaviour.

LangChain4j uses "Goal" to mean something narrower — an `outputKey` string.
Using `Milestone` for intermediate states keeps the terminology clean and
CMMN-aligned. The decision is in ADR-0001.

The plan is written. There's one more research topic before implementation starts.
