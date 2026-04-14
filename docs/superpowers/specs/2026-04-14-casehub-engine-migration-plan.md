# CaseHub — Migration Plan (casehub-engine as home)
**Date:** 2026-04-14
**Status:** Planning — awaiting contributor access to casehub-engine repo
**Supersedes:** The phased plan in `2026-04-09-casehub-unified-design.md` (architecture decisions still valid; execution strategy changed)

---

## Design Principle

**Nothing is locked in. Everything is up for re-evaluation as we progress.**

Each PR is an opportunity to improve the overall design, not just implement what the plan says.
If a better approach becomes apparent — simpler structure, dropped abstraction, merged concept —
take it. Don't carry forward decisions that no longer make sense just because they're in this doc.

---

## Direction Change

The original unified design used casehub as the base and ported casehub-engine's reactive
infrastructure in. This plan reverses that: **casehub-engine is the project home**, casehub's
CMMN/Blackboard/Resilience/Lineage features are ported in as Maven modules on top of the
reactive core.

**Why:** casehub-engine already has the working reactive event cycle (Vert.x + Quartz),
EventLog, Signal mechanism, recovery service, and JQ. Transplanting that into casehub
would be the same big-bang Phase 5 refactor the original plan was trying to avoid.
Building on top of a working reactive foundation is lower risk and lower effort.

**Repository:** All PRs go to casehub-engine. casehub is archived once migration is complete.

---

## Target Module Structure

```
casehub-engine/                        ← project root (existing repo)
├── api/                               ← pure Java, zero framework deps
│   └── io.casehub.api.*               ← CaseFile, Binding, Trigger, Goal, ProgressMarker,
│                                         LoopControl SPI, ExpressionEvaluator, Capability,
│                                         CaseCompletion, CaseFileItem, ErrorInfo, CaseStatus
├── engine/                            ← reactive core, minimal deps — runs anywhere
│   └── io.casehub.engine.internal.*   ← CaseHubReactor, event handlers, JQ, Quartz,
│                                         EventLog, Signal, Recovery, ChoreographyLoopControl
├── casehub-blackboard/                ← NEW — CMMN/Blackboard layer (optional)
│   └── io.casehub.blackboard.*        ← PlanningStrategy, PlanningStrategyLoopControl,
│                                         ListenerEvaluator, CasePlanModel, PlanItem,
│                                         Stage, Milestone (CMMN), DefaultPlanningStrategy
├── casehub-resilience/                ← NEW — resilience layer (optional)
│   └── io.casehub.resilience.*        ← RetryPolicy (merged), PoisonPillDetector,
│                                         DeadLetterQueue, IdempotencyService (TTL),
│                                         TimeoutEnforcer, ConflictResolver (if kept)
│   (PropagationContext lives in api/ as a value object — no separate lineage module)
├── casehub-quarkus/                   ← NEW — Quarkus extension
│   └── io.casehub.quarkus.*           ← CDI integration, @CaseType qualifier, TaskDefinition
│                                         (syntactic sugar), build-time discovery (Jandex +
│                                         YAML classpath scan), Dev Services, health checks,
│                                         live reload for YAML definitions
├── schema/                            ← existing — YAML/JSON schema + codegen
├── casehub-persistence-memory/        ← NEW — in-memory SPI impls (from casehub)
├── casehub-persistence-hibernate/     ← NEW — Hibernate Reactive (from casehub)
├── casehub-examples/                  ← NEW — examples (from casehub)
└── codegen/                           ← existing
```

**Dependency rules (enforced by Maven):**
- `api/` → no dependencies on other modules
- `engine/` → depends on `api/` only
- `casehub-blackboard/` → depends on `engine/` + `api/`
- `casehub-resilience/` → depends on `engine/` + `api/`
- `casehub-lineage/` → depends on `engine/` + `api/`
- `casehub-quarkus/` → depends on all above
- persistence modules → depend on `api/` only

---

## New Additions (not in original spec)

These were added during the 2026-04-14 session and need to be written into the main
design doc before implementation:

| Addition | What it is | Where |
|---|---|---|
| `LoopControl` SPI | `select(context, eligibleBindings)` — the pluggability hook | `api/` |
| `ChoreographyLoopControl` | Default impl — returns all eligible bindings (pure choreography) | `engine/` |
| `PlanningStrategyLoopControl` | Delegates to `PlanningStrategy` | `casehub-blackboard/` |

Without `casehub-blackboard`, users get pure choreography. Adding it enables Blackboard
orchestration. This is the co-worker's "pluggable planners and loop controls" insight.

---

## Gap Analysis

### Confirmed in plan ✓

- `LoopControl` SPI + `ChoreographyLoopControl`
- `LambdaExpressionEvaluator`, unseal `ExpressionEvaluator`
- Renames: `DispatchRule`→`Binding`, `CaseHubDefinition`→`CaseDefinition` ✓ (PR #38)
- `Milestone` rename **dropped** — see unified Milestone design below
- `PlanningStrategy`, `ListenerEvaluator`, `CasePlanModel`, `PlanItem`, `Stage` → `casehub-blackboard`
- `DLQ`, `PoisonPillDetector`, `TimeoutEnforcer` → `casehub-resilience`
- `PropagationContext` → `api/` (value object: traceId + attributes + deadline/budget; lineage is expressed through the domain model itself — parent/child refs on `CaseInstance`)
- `JsonCaseFile` (rename of `StateContextImpl`), `casehub-quarkus`
- `Goal`, `GoalExpression`, `GoalKind`, `CaseCompletion`, `EventLog`, Quartz — already in casehub-engine
- Nested stage lifecycle → `casehub-blackboard` (wiring into async event cycle required)

### In spec, no PR planned yet (gaps to fill after low-hanging fruit)

| Item | Spec says | Notes |
|---|---|---|
| `TaskDefinition` | Syntactic sugar compiling to `Worker` + `Binding` | Likely `casehub-quarkus` |
| `TaskBroker` / `TaskScheduler` / `WorkerRegistry` / `WorkerSelectionStrategy` | Keep — request-response model | Full subsystem, needs a module home. Doesn't fit cleanly into any current module. Possibly `casehub-broker/`? |
| `FlowWorker` | Keep, evolve — replace CDI polling with `WorkerScheduleEvent` | Replace polling with event-driven. Module TBD |
| `evalObjectTemplate()` | Move onto `CaseFile` interface | Small but needs explicit PR |
| `JavaBeanCaseFile<T>` | New impl — typed POJO backed `CaseFile` | `engine/` or `api/` |
| `MapCaseFile` | Migration path from current casehub | `engine/` |
| `SubCaseBinding` | New — `Binding` variant that spawns child `CaseInstance` | Significant new work. Depends on sub-case engine wiring |
| Long-lived workers | New concept — `CASE`/`STAGE`/`BINDING` lifecycle scopes | Depends on nested stage events being wired |
| `IdempotencyService` (TTL) | Keep alongside Quartz SHA256 — different layer | Task submission dedup vs Quartz job dedup |
| Dead letter replay | Marked TODO | Needs design before implementation |
| `CaseFileItem` | **Re-evaluated — Option B.** Unique value (`writtenBy`, `writtenAt`) delivered by enriching `WORKER_EXECUTION_COMPLETED` EventLog entries with a key-level diff (before/after). Gives key-level provenance through existing EventLog without adding a dual-map to `StateContextImpl`. `putIfVersion` is covered by `compareAndSet`. Full `CaseFileItem` class: skip. | — |
| `StaleVersionException` | **Drop** — not needed without `putIfVersion` | — |

### Open decisions — must be resolved before affected PRs land

| Decision | Options | Blocks |
|---|---|---|
| **CaseState/CaseStatus alignment** | casehub: `PENDING/RUNNING/WAITING/SUSPENDED/COMPLETED/FAULTED/CANCELLED` vs casehub-engine: `ACTIVE/COMPLETED/FAILED/SUSPENDED/TERMINATED/WAITING`. Specific conflicts: PENDING vs ACTIVE, RUNNING vs ACTIVE, FAULTED vs FAILED, CANCELLED vs TERMINATED | Every PR touching lifecycle |
| **`ContextChangeTrigger` naming** | Keep or rename to `StateChangeTrigger`? | Terminology rename PR |
| **`ProgressMarker` naming** | Confirm with co-worker. Alternatives: `ObservabilityMarker`, `Signal`, `Indicator` | PR 3 (rename) |
| **`ConflictResolver`** | Keep (which module?) or drop? Interface exists, not integrated. Does async model need it? | `casehub-resilience` scope |
| **`ExecutionPolicy` vs `TimeoutEnforcer`** | casehub-engine has `ExecutionPolicy(timeoutMs, RetryPolicy)`. casehub has `TimeoutEnforcer` as a separate scheduled enforcer. Merge or coexist? | `casehub-resilience` scope |
| **`CaseDefinitionRegistry` shim** | How do YAML-defined cases (no lambdas) and Java-defined cases (with lambdas) coexist cleanly? | `casehub-quarkus` scope |
| **`TaskBroker` module home** | New module `casehub-broker/`? Into `casehub-quarkus`? | Planning the broker subsystem |

### Casehub-only items — decision needed (keep/remove/supersede)

| Item | Likely fate | Action |
|---|---|---|
| `notifyAutonomousWork()` | **Remove** — Signal mechanism is the replacement. `AutonomousMonitoringWorker` example needs rewriting | Explicit removal PR once Signal is stable |
| `NotificationService` | **Remove** — superseded by Vert.x EventBus | Remove alongside migration |
| `CaseFileItemEvent` | **Remove** — superseded by `CaseStateContextChangedEvent` | Remove |
| `TaskLifecycleEvent` | **Remove** — superseded by EventLog entries | Remove |
| `CaseFileContribution` | **Absorb** — EventLog covers what this tracked. Confirm and remove | Confirm, then remove |
| `WorkerSelectionStrategy` | **Keep** — part of TaskBroker subsystem | Follows TaskBroker module |
| `annotation/CaseType` | **Keep** — CDI qualifier, goes to `casehub-quarkus` | Minor, confirm home |

---

## Planned PRs

### Phase 0 — Setup (before any code)

- [ ] Agree module structure and naming with co-worker (written issue/epic in casehub-engine)
- [ ] Resolve `ProgressMarker` naming with co-worker
- [ ] Resolve `ContextChangeTrigger` naming
- [ ] Resolve `CaseState`/`CaseStatus` alignment

### Phase 1 — Low-hanging fruit (in casehub-engine, unblocked by open decisions)

| PR | Scope | Unblocked? |
|---|---|---|
| Add `LoopControl` SPI + `ChoreographyLoopControl` default | `api/` + `engine/` | ✓ |
| Unseal `ExpressionEvaluator`, add `LambdaExpressionEvaluator` | `api/` | ✓ |
| Rename `DispatchRule`→`Binding`, `CaseHubDefinition`→`CaseDefinition` | `api/` + `engine/` | ✓ PR #38 — schema module deferred |
| ~~Rename `Milestone`→`ProgressMarker`~~ | **Decision: keep as `Milestone`, unified concept** — see below | — |
| Move `evalObjectTemplate()` onto `CaseFile`/`StateContext` interface | `api/` | ✓ |
| ~~Add `CaseFileItem` to `api/`~~ | Re-evaluated — enrich EventLog instead (see gaps table) | — |

### Unified Milestone Design Decision

`Milestone` is kept as a single unified concept — no rename, no split into two classes.

**Milestone vs Goal — the distinction:**
- **Milestones** are neutral waypoints you _pass through_ on the way to a goal. No success/failure
  polarity. A milestone marks where the case is, not what it achieved.
- **Goals** are terminal outcomes with `GoalKind` (SUCCESS/FAILURE). They drive case completion.
  You _achieve_ goals.

**Unified Milestone — two depths of use, one class:**

_Lightweight (current, no extra config):_
When the condition is met, `MilestoneReachedEvent` fires and is recorded in EventLog as
`MILESTONE_REACHED`. Sufficient for observability, dashboards, audit.

_Lifecycle-tracked (Phase 2, with `CasePlanModel`):_
When `CasePlanModel` is present, it listens to `MilestoneReachedEvent` and promotes the milestone
to PENDING → ACHIEVED lifecycle tracking. The achieved state can be referenced in stage exit
criteria and completion logic. No changes to the `Milestone` class — lifecycle is a
`CasePlanModel` concern.

The previous ProgressMarker rename proposal is dropped. `Milestone` is the right name for
both uses: you pass milestones whether or not their achievement is formally tracked.

### Phase 2 — Module additions (replan after Phase 1 and open decisions resolved)

Port casehub features as new modules. Sequence TBD pending open decisions.

Candidate modules (in rough dependency order):
1. `casehub-blackboard/` — PlanningStrategy, Stage, PlanningStrategyLoopControl
2. `casehub-resilience/` — DLQ, PoisonPill, RetryPolicy (merged), TimeoutEnforcer
3. ~~`casehub-lineage/`~~ — PropagationContext goes to `api/`; structural lineage is domain model
4. `casehub-persistence-memory/` — in-memory SPI impls
5. `casehub-persistence-hibernate/` — Hibernate Reactive
6. `casehub-quarkus/` — extension, TaskDefinition sugar, build-time discovery
7. `casehub-schema/` — extend casehub-engine schema, Java DSL

### Deferred — no urgency

- **Repository rename** (`casehub-engine` → `casehub` on GitHub) — GitHub auto-redirects the old URL, so timing doesn't matter. Do this at the end once everything else is settled.

### Phase 3 — Unplanned items (needs design before PR)

- `TaskBroker` / `TaskScheduler` subsystem — module home TBD
- `FlowWorker` — evolve to event-driven
- `SubCaseBinding` + sub-case engine wiring
- Long-lived workers + lifecycle scopes
- Dead letter replay
- `JavaBeanCaseFile<T>`, `MapCaseFile`
- `IdempotencyService` (TTL layer)
