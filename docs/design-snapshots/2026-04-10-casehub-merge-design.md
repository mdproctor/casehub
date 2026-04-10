# CaseHub — Design Snapshot
**Date:** 2026-04-10
**Topic:** Post-merge-design — two implementations analysed, unified design decided
**Supersedes:** [2026-04-09-casehub-architecture](2026-04-09-casehub-architecture.md)
**Superseded by:** *(leave blank)*

---

## Where We Are

CaseHub is a Quarkus Blackboard/CMMN framework for agentic AI coordination. Two independent implementations now exist: **casehub** (5-module, Blackboard/CMMN, synchronous control loop, rich resilience) and **casehub-engine** (4-module, reactive/async, Vert.x EventBus, Goal model, YAML schema). A comprehensive merge design has been agreed: casehub is the base, casehub-engine contributes interface design, reactive infrastructure, Goal model, EventLog, and YAML schema. The unified design spec is written at `docs/superpowers/specs/2026-04-09-casehub-unified-design.md`.

## How We Got Here

| Decision | Chosen | Why | Alternatives Rejected |
|---|---|---|---|
| Merge direction | casehub as base | casehub's Blackboard loop, CMMN stages, resilience are architectural — can't retrofit onto casehub-engine | casehub-engine as base (would require rebuilding everything casehub has) |
| Migration approach | Evolve casehub in-place (Option B) | Existing test coverage + module structure; each phase shippable | Big bang rewrite; new unified repo |
| Execution model | Async event cycle — always non-blocking | Architecturally necessary for hybrid model; `notifyAutonomousWork()` workaround disappears | Keep synchronous loop (fights the hybrid orchestration+choreography model) |
| TaskDefinition vs Worker | Option C — `TaskDefinition` sugar over Worker+Binding | Ergonomic Java DSL + clean internal model | Keep TaskDefinition only; pure Worker+Binding |
| Schema vs code | Both first-class (Quarkus Flow pattern) | Validated by `DiscoveredWorkflowBuildItem.fromSpec/fromSource` | Schema-first only; code-first only |
| Expression language | Pluggable — JQ + Lambda both valid | YAML needs JQ; Java devs want lambdas | JQ only (sealed, casehub-engine's current state) |
| Context model | Pluggable `CaseFile` impls | `JsonCaseFile`, `JavaBeanCaseFile<T>`, `MapCaseFile` | Rename to StateContext (loses CMMN correctness) |
| Quarkus Flow depth | Option A — one backend among several | Option C (all-Flow) is a usage pattern of A, not a constraint | Mandating Quarkus Flow for all workers |
| Naming | `bindings` | Evolved naturally; Quarkus Flow has no equivalent to align with | `rules`, `dispatch-rules` |
| Worker role | Pure executor + indirect influencer via CaseFile | Different from CMMN where worker decides directly; PlanningStrategy retains authority | CMMN-style direct worker authority |

## Where We're Going

Nine implementation phases, ordered by dependency and naming-safety:

1. Unseal `ExpressionEvaluator`, add `LambdaExpressionEvaluator` — no naming decisions needed
2. Goal model — `Goal`, `GoalExpression`, `GoalKind`, `CaseCompletion` (supersedes issue #7 plan)
3. `EventLog` + Quartz for durable execution
4. Async event cycle — replace synchronous control loop with Vert.x EventBus
5. Pluggable `CaseFile` implementations
6. `Binding` + `Trigger` model (deferred — involves renaming)
7. YAML schema adoption (deferred — involves naming)
8. Sub-cases — wire the existing POJO graph (`getParentCase/getChildCases`) into the engine
9. `casehub-quarkus` extension — Quarkus DX layer, build-time discovery

**Open questions:**
- `Milestone` name clash — casehub's CMMN `Milestone` (PENDING/ACHIEVED lifecycle) vs casehub-engine's `Milestone` (JQ predicate, observability). Proposed rename: `ProgressMarker`. Needs co-worker agreement.
- `CaseState` alignment — casehub (PENDING/RUNNING/WAITING/SUSPENDED/COMPLETED/FAULTED/CANCELLED) vs casehub-engine (ACTIVE/COMPLETED/FAILED/SUSPENDED/TERMINATED/WAITING). Which wins?
- `ContextChangeTrigger` vs `StateChangeTrigger` — naming TBD.
- `CaseDefinitionRegistry` shim — how do YAML-defined cases and Java-defined cases coexist cleanly in the same registry?
- Long-lived worker lifecycle — `CASE`/`STAGE`/`BINDING` scope enum needs design.
- Nested stage lifecycle — evaluation methods exist in `ListenerEvaluator` but not fully wired into the engine.

## Linked ADRs

| ADR | Decision |
|---|---|
| [ADR-0001 — Goal model design](../adr/0001-goal-model-design.md) | Goal as predicate over CaseFile; BDI framing; Milestone for intermediate states |

## Context Links

- Unified design spec: `docs/superpowers/specs/2026-04-09-casehub-unified-design.md`
- Scratch working notes: `docs/superpowers/specs/scratch-merge-design.md`
- Blog series: `docs/blog/` (4 entries, mdp01–mdp04)
- Open issue: mdproctor/casehub#7 (Goal model — plan needs updating to align with casehub-engine model)
