# Goal Model Research: Mapping Goals Across AI, Planning, and Workflow Systems to CaseHub

**Status:** Design research — not yet implemented  
**Date:** 2026-04-09  
**Context:** CaseHub currently has no explicit Goal concept. Cases complete on quiescence (no eligible TaskDefinitions remain). This document captures research across planning, multi-agent, workflow, and LLM-agent systems to inform what a CaseHub Goal model should look like.

---

## 1. Why Goals Are Missing and Why They Matter

CaseHub's design document (§8.2, Design Decision #1) deliberately deferred Goals to "Phase 3", citing premature abstraction risk. That deferral has a real cost:

- `CaseEngine.createAndSolve()` has no way for the caller to express *what they want back*
- Case completion (WAITING state) and case success are conflated — quiescence ≠ goal achieved
- `awaitCompletion()` waits for quiet, not for a stated objective to be satisfied
- There is no way for the system to detect that a goal has already been satisfied before planning
- There is no way to detect that a goal has become unreachable (impossible)
- Workers and TaskDefinitions fire opportunistically with no filter for relevance to intent

The blackboard architecture is powerful for collaborative problem-solving, but without a goal the board has no definition of done beyond "nothing more can be written."

---

## 2. Research Coverage

Eight areas were researched:

| Area | Key Source |
|------|-----------|
| Goal-Oriented Action Planning (GOAP) | Jeff Orkin, F.E.A.R. (2005); GDC Vault |
| BDI Architecture | Bratman (1987); Rao & Georgeff (1991, 1995) |
| HTN Planning | Wikipedia HTN; Game AI Pro Ch12; Stanford CS227 |
| Declarative Workflows (DCR/DECLARE) | Hildebrandt et al.; arXiv 1110.4161 |
| CMMN Case Goals | OMG CMMN 1.0 Spec; Flowable; Cafienne |
| Requirements Engineering (KAOS, i\*) | van Lamsweerde (KAOS); Yu (i\*); iStar 2.0 |
| LangChain4j agenticai | GitHub langchain4j/langchain4j; docs.langchain4j.dev |
| Modern LLM Agents | Weng 2023; SELFGOAL; MAP architecture |

---

## 3. Per-System Analysis

### 3.1 Goal-Oriented Action Planning (GOAP)

**Core model:** Agents hold goals (desired world states) and a library of actions (preconditions + effects). A real-time A\* planner finds the cheapest action sequence transforming current world state into the goal state. Plans are computed at runtime, not authored.

**Goal definition:** A set of key-value conditions that must simultaneously hold on world state. Example: `{hasAmmo: true, enemyDown: true}`. Purely declarative — says nothing about how.

**Task/Action definition:** Atomic step with preconditions (what must be true before execution) and effects (what changes in world state after execution). Actions are self-contained and mutually ignorant.

**Goal-Task relationship:** Backward-chaining A\* search. Planner asks "which action has this goal condition as an effect?" then recursively adopts preconditions as sub-goals until current state is reached.

**Success criteria:** All goal key-value conditions match current world state. Pure state-test — no process history consulted.

**Key insight for CaseHub:** Goals should be **predicates over CaseFile state**, not references to specific TaskDefinitions. A goal is satisfied when the CaseFile contains specific entries meeting defined conditions — tested against observable state, never procedurally tracked. The CaseFile IS the world state.

**What doesn't map:** GOAP assumes a closed, fully observable world state. CaseFile data is typed and structured; GOAP's flat key-value world state is simpler. GOAP's A\* replanning is continuous and cheap; CaseHub cases are longer-lived and more expensive to re-plan.

---

### 3.2 BDI Architecture (Belief-Desire-Intention)

**Core model:** An agent has Beliefs (world model), Desires (motivational states — things it wants, possibly conflicting), and Intentions (committed subsets of desires, backed by plans currently executing). Bratman's theory of practical reasoning formalised by Rao & Georgeff.

**Goal definition:** A desire that has been adopted for active pursuit. Two types relevant to CaseHub:
- *Achievement goals*: bring about a state that doesn't yet hold (declarative — "world state X must become true")
- *Maintain goals*: keep a state holding ("world state X must remain true")

**Critical BDI property:** A declarative achievement goal is **dropped when the agent comes to believe it is already satisfied** — opportunistic achievement without executing any plan.

**Task/Action definition:** Plans (recipes) are pre-authored sequences triggered by events. Tasks are steps within plans. An *intention* is an adopted plan being executed.

**Goal-Task relationship:** When an event triggers a desire, deliberation selects applicable plans from the plan library. The chosen plan becomes an intention — a committed task sequence. BDI agents balance deliberation (goal selection) and execution (intention pursuit).

**Success criteria:** A declarative achievement goal succeeds when the agent *believes* the target state holds. An agent with "single-minded" commitment drops an intention only if: (a) goal achieved, (b) goal impossible, or (c) goal no longer desired.

**Key insights for CaseHub:**
1. **Desire/Intention split**: A Goal is a desire (something wanted). A PlanItem executing a TaskDefinition is the intention (something committed to). These must be architecturally separate.
2. **Opportunistic achievement**: The CaseEngine must check goal satisfaction against the CaseFile *before* planning. If a goal is already satisfied, skip plan generation entirely.
3. **Abandonment is mandatory**: Goals that become impossible must be explicitly dropped. Without abandonment, impossible goals accumulate and cases never complete.

**What doesn't map:** BDI's plan library (pre-authored recipes) doesn't fit CaseHub's data-driven, dynamic TaskDefinition activation. CaseHub uses the blackboard pattern; BDI uses event-triggered plan selection. The activation mechanism is different, but the goal semantics translate well.

---

### 3.3 HTN Planning (Hierarchical Task Networks)

**Core model:** Decomposes high-level compound tasks into networks of primitive tasks through domain-authored decomposition *methods*. Unlike GOAP, HTN doesn't search for goal states — it decomposes tasks. Input is not "reach state X" but "accomplish task T."

**Goal definition:** HTN has no explicit goal states in the pure form. The "goal" is expressed as a top-level compound task. Extensions (SHOP2) add goal conditions. The important HTN concept for CaseHub: **multiple decomposition methods** — alternative paths to achieve the same compound task, selected based on current preconditions.

**Task/Action definition:** Two types:
- *Compound tasks*: abstract, must be decomposed via methods. No direct effects.
- *Primitive tasks*: atomic, with STRIPS-style preconditions and effects. Directly executable.

**Key insight for CaseHub:** HTN's method mechanism maps to CaseHub's `PlanningStrategy`. A Goal should be accompanied by **decomposition hints** — which TaskDefinitions are relevant to this goal. But hints are advisory: the system can discover other paths. HTN also demonstrates that *partial decomposition is useful* — goals need not prescribe all tasks upfront; subtasks reveal themselves as preconditions become satisfied.

**What doesn't map:** HTN's success criterion is task completion, not state satisfaction. CaseHub's blackboard model is more naturally goal-state-based (like GOAP) than task-tree-based (like HTN).

---

### 3.4 Declarative Workflows (DCR Graphs / DECLARE)

**Core model:** Constraint-based workflow languages that invert the procedural model. Instead of specifying an execution path, they specify *constraints* on what can and must happen. A run is *accepted* when it satisfies all constraint obligations — regardless of the specific path taken.

**Goal definition:** The **acceptance condition** of the constraint automaton. In DCR: a run is accepted if there are no pending *response* obligations. There is no explicit "goal" element — the goal is the global state of no outstanding obligations. Constraint-satisfaction view: goal = all constraints met.

**Goal-Task relationship:** Activities executing create and clear obligation constraints. A single activity may clear one obligation and create another. Goals are *constructed bottom-up* by constraint semantics, not decomposed top-down.

**Success criteria:** The workflow instance is in an accepting state — tested against a marking `M = (Executed, Pending, Included)`, not a task checklist.

**Key insight for CaseHub — the strongest from all research:** Goal completion must be tested by evaluating a **satisfaction predicate against case state**, not by checking whether specific tasks ran. The same goal can be satisfied by different execution paths. If two different sets of TaskDefinitions can both produce the CaseFile state that satisfies the goal predicate, both are valid. This is the strongest argument against procedural goal tracking.

**What doesn't map:** DCR/DECLARE constraints are globally specified across all cases of a type. CaseHub goals are per-case. DCR's constraint graph is fixed at model design time; CaseHub's goals should be specifiable at case creation time (runtime). But the acceptance condition formulation translates directly.

---

### 3.5 CMMN Case Goals

**Core model:** The OMG standard for case management notation. A Case has a CaseFile, CasePlanModel, CaseRoles, and optionally CaseGoals. The CasePlanModel contains Stages, Tasks, EventListeners, and Milestones connected by Sentries.

**Goal-like constructs in CMMN:**

1. **Milestones**: Named achievable states within a case. Activated when their entry sentry fires. Have no work of their own — mark *that* a state was reached, not *how*. Can serve as entry criteria for other PlanItems.

2. **CasePlanModel.completionExpression**: An optional condition that, when true, transitions the case to COMPLETED. This is the formal goal predicate for the whole case.

3. **Exit sentries on Stages**: When a stage exit sentry fires, the stage terminates even if tasks within it are still running — analogous to goal abandonment.

**Key insight for CaseHub:** The **Milestone concept is the right primitive for named goal states**. A Milestone is a reified, named achievement state with a sentry (satisfaction predicate), no work of its own, and the ability to gate subsequent stages and tasks. The CasePlanModel's `completionExpression` — tested against CaseFile state — is the formal Goal predicate. Together: Goal = `completionExpression` evaluated over Milestone states.

**Direct CMMN mapping for CaseHub:**

| CaseHub Concept | CMMN Equivalent |
|----------------|-----------------|
| `Goal` | `CasePlanModel.completionExpression` |
| `Milestone` | `Milestone` element with entry sentry |
| Goal satisfaction predicate | Sentry condition expression |
| Goal achieved | Case transitions to COMPLETED |
| Milestone achieved | Milestone transitions to COMPLETED |
| Unreachable goal | Exit sentry on containing Stage (termination) |

**What doesn't map:** CMMN Milestones are statically defined in the case type model. CaseHub's design requires Goals and Milestones to be specifiable at case creation time by the caller. This is adaptive case management (ACM) semantics — not fully captured in standard CMMN.

---

### 3.6 Requirements Engineering (KAOS and i\*)

**Core model:** Goal-oriented requirements frameworks that distinguish *why* a system does something (goals) from *how* it does it (tasks). Model organizational and system requirements as hierarchies of goals refined into tasks assigned to agents/actors.

**i\* means-ends links:** A task is a *means* to achieve a goal. Task-decomposition links: a task *decomposes into* sub-goals, sub-tasks, resources, and softgoals. This creates a bipartite goal/task graph — goals lead to tasks (via means-ends), tasks lead back to goals (via decomposition).

**KAOS obstacle analysis:** An obstacle is a condition inconsistent with achieving a goal. If an obstacle holds unconditionally, the goal must be abandoned or reformulated. This is the formal basis for goal abandonment conditions.

**Key insights for CaseHub:**
1. **Goals and tasks form a bipartite graph**, not a strict hierarchy. A TaskDefinition writing to CaseFile may satisfy one goal and simultaneously activate the entry condition for another. The relationship is a DAG.
2. **Abandonment conditions** from KAOS map to: "if this CaseFile condition holds, the goal is no longer achievable — abandon it."
3. **Softgoals** (satisficed, not satisfied) hint at the AND/OR question: some goals have fuzzy completion criteria that are "good enough", not binary.

**What doesn't map:** KAOS and i\* are requirements-time frameworks — they model what the system *should* do, not runtime execution. CaseHub needs runtime goal evaluation, not design-time goal modelling.

---

### 3.7 LangChain4j Agenticai

**Core model:** The `langchain4j-agentic` module (experimental) provides: `AgenticScope` (shared key-value workspace), `@Agent`-annotated methods (units of work with named inputs and a single outputKey), and `Planner` interface (strategy for deciding what to execute next). Built-in planners: Sequential, Parallel, Loop, Conditional, Supervisor (LLM-driven), GoalOriented (GOAP A\*), P2P (reactive blackboard).

**Goal definition:** An `outputKey` string — the desired key to populate in `AgenticScope`. This is the thinnest possible goal model: a goal is just a named slot to fill. No predicate, no priority, no abandonment condition.

**Plan definition:** The sequence of `Action` objects returned by a `Planner`. Actions are: call these agents, or done. Plans are emergent from planner strategy + scope state.

**Key structural parallels with CaseHub:**

| LangChain4j | CaseHub |
|-------------|---------|
| `AgenticScope` | `CaseFile` — both are shared key-value blackboards |
| `@Agent` method (inputs → outputKey) | `TaskDefinition` (entryCriteria → producedKeys) |
| `Planner` interface | `PlanningStrategy` |
| `P2PPlanner` | current `ListenerEvaluator` reactive activation |
| `GoalOrientedPlanner` | not yet in CaseHub — GOAP A\* over dependency graph |
| `SupervisorPlanner` | not in CaseHub — LLM-driven orchestration strategy |
| — | CMMN lifecycle, resilience, persistence (CaseHub only) |

**Finding: Neither system has a mature Goal model yet.** LangChain4j's "goal" is an `outputKey` string. CaseHub's "goal" is implicit quiescence. This is an opportunity: CaseHub can define the reference Goal model that LangChain4j can integrate with.

**Bidirectional integration:**

*Direction 1 — LangChain4j as a CaseHub Worker:*
A LangChain4j agent system (with its AgenticScope, planners, and LLM-backed agents) is wrapped as a CaseHub `Worker`. CaseHub owns: case lifecycle, CMMN states, resilience, and when to invoke the LLM system. LangChain4j owns: which LLMs, which tools, parallel vs sequential agent composition.

```
CaseEngine orchestrates:
  Goal: "Analyse legal risk"
    → TaskDefinition: OcrTaskDefinition
    → TaskDefinition: LlmRiskAnalysisTaskDefinition
         ↓ delegates to LangChain4j Worker
         LangChain4j AgenticScope (backed by CaseFile slice)
           @Agent: extractRiskFactors
           @Agent: generateRecommendations
         ← writes result back to CaseFile
```

*Direction 2 — CaseHub Goal as a LangChain4j Plan Goal:*
A LangChain4j SupervisorPlanner's plan includes a CaseHub case as one of its `@Agent` steps. The CaseHub case is opaque to LangChain4j — just an agent that takes inputs and produces an outputKey. Internally it runs the full CMMN case lifecycle with multiple goals and Milestones.

```
LangChain4j SupervisorPlanner:
  Goal (outputKey): "investment_report"
    → @Agent: FetchMarketDataAgent
    → @Agent: CaseHubRiskAnalysisAgent  ← wraps entire CaseHub case
    → @Agent: FormatReportAgent
```

**Why there is no terminology collision:** LangChain4j uses "Goal" as a string key name. CaseHub uses "Milestone" as a named achievement state and "Goal" as the case completion condition. The LangChain4j `AgenticScope` (their blackboard) maps to `CaseFile` (CaseHub's blackboard) — but with different terminology, no confusion.

**What doesn't map:** LangChain4j is optimised for composing LLM-backed agents and their data flow, particularly streaming and A2A connectivity. CaseHub is optimised for case management lifecycle, multi-strategy control, and resilience. These are genuinely complementary concerns, not competing implementations.

---

### 3.8 Modern LLM Agent Frameworks

**Core model:** ReAct (Thought → Action → Observation loop), Plan-and-Execute (plan first, then execute), Hierarchical (planner LLM + executor LLM). Goals are natural-language statements decomposed into sub-goals and tasks by the LLM.

**Key finding:** Goals in LLM agent frameworks are informal (natural language), dynamically decomposed, and evaluated by the LLM itself ("does the current state satisfy the goal?"). Recent work (MiRA, milestoning approaches) is moving toward formal milestone conditions with dense reward signals rather than binary goal completion — converging toward what CaseHub should build.

**MAP architecture Monitor/Orchestrator pattern:** A Monitor continuously checks whether a subgoal is achieved. An Orchestrator determines when the top-level goal is satisfied. This separation of execution (agents) from evaluation (monitor/orchestrator) is the key architectural pattern CaseHub should adopt.

**Key insight for CaseHub:** A `GoalEvaluator` component — architecturally separate from task execution — should run in the CaseEngine control loop after each TaskDefinition execution, testing goal predicates against CaseFile state.

---

## 4. Cross-Cutting Comparison Table

| Property | GOAP | BDI | HTN | DCR | CMMN | LangChain4j |
|---|---|---|---|---|---|---|
| Goal = desired state? | Yes | Yes (achievement) | No (task) | No (constraint) | Partial | No (outputKey) |
| Goal tested against? | World state | Belief base | Task completion | Acceptance marking | CaseFile + expression | Scope key presence |
| Decomposition by? | Runtime A\* planner | Plan library match | Authored methods | None (bottom-up) | Sentry evaluation | LLM or planner |
| Multiple goal paths? | Yes | Yes (plan variants) | Yes (methods) | Yes (constraint satisfaction) | Discretionary tasks | Yes (planner) |
| Goal abandonment? | Implicit (re-plan) | Explicit (commitment) | Implicit (backtrack) | Exclusion relation | Exit sentry | Not supported |
| Task = contribution to goal? | Yes (effects) | Yes (plan achieves) | Yes (primitive) | Yes (clears pending) | Yes (PlanItem advances Milestone) | Yes (produces outputKey) |
| Goal evaluation separate from execution? | Yes (planner) | Yes (deliberation) | Partial | Yes (marking) | Yes (Sentry) | No (mixed) |

---

## 5. Synthesis: Six Key Ideas for CaseHub

### 5.1 Goals Are Predicates Over CaseFile State, Not Task Checklists

From GOAP, DCR, BDI, CMMN — all four systems converge on this.

A `CaseGoal` is an acceptance predicate — a condition expression evaluated against CaseFile content. The goal is achieved when the predicate evaluates true, regardless of how the CaseFile reached that state. Never store a list of "required tasks" on a goal.

**Concrete form:** `Predicate<CaseFile>` — tested after every CaseEngine iteration.

### 5.2 Desires and Intentions Must Be Architecturally Separate

From BDI.

A Goal is a desire (something wanted). A PlanItem executing a TaskDefinition is an intention (something committed to). The CaseEngine must check goal satisfaction against the CaseFile *before* planning. If a goal is already satisfied, no plan is needed — opportunistic achievement without unnecessary work.

**Concrete form:** `GoalEvaluator.checkSatisfaction()` runs before `ListenerEvaluator.evaluate()` in every control loop iteration.

### 5.3 Goals Form a Bipartite Graph with Tasks

From i\*, HTN, KAOS.

Goals lead to TaskDefinitions (a TaskDefinition is a means to achieve a goal). TaskDefinitions lead back to goals (executing a TaskDefinition writes CaseFile state that may satisfy one goal and activate the entry condition for another). This is a DAG, not a strict hierarchy.

**Concrete form:** A `Milestone` achieved by one TaskDefinition's output becomes the entry condition for subsequent TaskDefinitions — the graph is implicit in the CaseFile key dependencies.

### 5.4 Goal Evaluation Must Be Architecturally Separate from Task Execution

From BDI deliberation/intention separation, MAP Monitor/Orchestrator, CMMN Sentry.

The executor produces CaseFile changes. A `GoalEvaluator` tests those changes against goal predicates. These must not be the same component. Any CaseFile change — from any TaskDefinition, Worker, autonomous worker, or external event — can trigger goal satisfaction without any task needing to know which goals it serves.

**Concrete form:** `GoalEvaluator` is a pure function: `evaluate(CaseFile, List<CaseGoal>) → List<GoalTransition>`. No task history consulted.

### 5.5 Only Activate TaskDefinitions That Contribute to an Unsatisfied Goal

From GOAL language action selection, GOAP, HTN.

Without goals: CaseHub's `ListenerEvaluator` activates all eligible TaskDefinitions (pure blackboard opportunism). With goals: activation should be filtered — only TaskDefinitions relevant to at least one ACTIVE, unsatisfied goal become PlanItems.

**Concrete form:** `TaskDefinition` gains an optional `@ContributesTo(goalType)` annotation. When goals are active, the ListenerEvaluator checks relevance before creating PlanItems. Without goals: fallback to existing pure-blackboard behaviour.

### 5.6 Goals Need Abandonment Conditions, Not Just Achievement Conditions

From BDI commitment strategies, KAOS obstacle analysis.

Every Goal must have:
- An **achievement predicate**: when the goal is satisfied
- An **abandonment predicate**: when the goal is no longer achievable (KAOS obstacle) or no longer desired

Without abandonment, impossible goals accumulate and cases never complete. The abandonment predicate is the obstacle condition from KAOS: "if this holds in the CaseFile, the goal cannot be achieved — mark it ABANDONED and transition dependent work accordingly."

**Concrete form:** `GoalEvaluator.checkAbandonment()` runs in parallel with satisfaction checking.

---

## 6. CaseHub Design Implications

### 6.1 Goal Is the Contract When Creating a Case

The most important finding for CaseHub's API. `createAndSolve()` currently has no way for the caller to express what they want. With Goals, the call becomes a formal contract:

```java
// Without Goal — quiescence and success are conflated
CaseFile cf = caseEngine.createAndSolve("legal-analysis", Map.of("docs", docs));

// With Goal — the caller declares what they want
CaseFile cf = caseEngine.createAndSolve(
    "legal-analysis",
    Map.of("docs", docs),
    Goal.of("risk-assessment-complete",
        Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
        Milestone.when(cf -> cf.contains("executive_summary")).named("summary-complete")
    )
);
```

### 6.2 Goal, Milestone, and CaseFile Are Three Distinct Concepts

| Concept | What it is | CMMN equivalent |
|---------|-----------|----------------|
| `Goal` | The overall case objective — the contract between caller and system | `CasePlanModel.completionExpression` |
| `Milestone` | A named, intermediate achievement state with a satisfaction predicate | `Milestone` with entry sentry |
| `CaseFile` | The shared workspace where state evolves | `CaseFile` |

A Goal is satisfied when its Milestones are reached (AND semantics by default). A Milestone is satisfied when its predicate evaluates true against the CaseFile. The CaseFile is the ground truth — the only thing predicates are evaluated against.

### 6.3 Goal Lifecycle

```
PENDING   — declared but not yet being pursued
ACTIVE    — CaseEngine is working toward this goal
ACHIEVED  — all Milestones satisfied; case can complete
ABANDONED — achievement predicate became false / abandonment predicate became true
```

### 6.4 Modified CaseEngine Control Loop (Conceptual)

```
loop:
  1. GoalEvaluator.checkSatisfaction()    // BDI: opportunistic achievement check
  2. GoalEvaluator.checkAbandonment()     // KAOS: impossible goal detection
  3. GoalEvaluator.activateNewGoals()     // entry predicates now satisfied
  4. ListenerEvaluator.evaluate()         // GOAL language: goal-filtered activation
  5. PlanningStrategy.rank()              // BDI deliberation: goal-priority weighted ranking
  6. execute top PlanItem
  7. GoalEvaluator.checkSatisfaction()    // re-check after execution
  until: all goals ACHIEVED | ABANDONED | quiescent
```

### 6.5 awaitCompletion Gets Meaning

```java
// Today — waits for quiescence, no indication of success
caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(5));

// With Goals — waits for goal resolution with result
GoalResult result = caseEngine.awaitGoal(caseFile, Duration.ofMinutes(5));
result.isAchieved();    // all milestones satisfied
result.isAbandoned();   // goal became unreachable
result.achieved();      // which milestones completed
result.pending();       // which milestones never fired
```

---

## 7. Where Things Don't Map Cleanly

### 7.1 AND vs OR Goal Completion Semantics

Most research assumes AND semantics (all conditions must hold). DCR uses OR (any acceptance state). i\* softgoals use "satisficing" (good enough). **Decision deferred** — start with AND-only (`Goal.of()` requires all Milestones) and introduce `Goal.any()` when real use cases demand it.

### 7.2 Maintain Goals vs Achievement Goals

BDI distinguishes achievement goals (reach this state) from maintain goals (keep this state). The Milestone model handles achievement naturally. Maintenance goals — where the system must detect regression and re-activate pursuit — are not covered by the current Milestone design. Deferred.

### 7.3 Goal Priority and Conflict Resolution

Multiple goals may be active simultaneously with competing resource demands. BDI's deliberation function handles this via intention selection. GOAP handles it via action cost. CaseHub's `PlanningStrategy` layer is the natural place for goal-weighted PlanItem ranking, but the exact mechanism is not designed. Deferred.

### 7.4 Sub-Goal Creation During Execution

HTN and BDI both support goals creating sub-goals during execution. A TaskDefinition writing to the CaseFile might logically create new Goal obligations. CaseHub's data-driven activation partially addresses this (new keys activate new TaskDefinitions), but there is no formal sub-goal creation mechanism. Deferred.

### 7.5 LangChain4j Goal Alignment

LangChain4j's `outputKey` is not semantically equivalent to a CaseHub `Goal`. The integration point is: the CaseHub Goal's terminal Milestone satisfaction predicate tests for a key in the CaseFile — which is what a LangChain4j Agent writes. The systems compose without needing semantic alignment at the goal level.

---

## 8. Open Questions

1. **AND/OR/custom completion semantics** — `Goal.of()` (AND) vs `Goal.any()` (OR) vs `Goal.completedWhen()` (custom predicate). Which are needed? Let real use cases answer.

2. **What triggers Milestone abandonment?** Does a Milestone become abandoned when its entry predicate can never become true? How does the system detect impossibility?

3. **Does a Goal carry an SLA/deadline?** A Goal with `Duration timeout` would abandon if not achieved within the deadline. Natural fit for the existing TimeoutEnforcer.

4. **How does a Goal surface to LangChain4j?** The terminal Milestone's keys are the agent's output contract. How is this expressed in the `AgentInstance.arguments()` model?

5. **Should Goals be registered at the case type level or only at creation time?** Type-level Goals (like `@CaseType` on TaskDefinitions) vs instance-level Goals (passed in at `createAndSolve`). CMMN uses type-level. ACM favours instance-level.

6. **How does `GoalEvaluator` interact with `PoisonPillDetector`?** A TaskDefinition repeatedly firing without advancing any goal could be a poison pill. The goal layer provides the right signal.
