# Choreography and Expected Flows: Using Worker Observation to Describe and Discover Execution Patterns

**Status:** Design research — not yet implemented  
**Date:** 2026-04-09  
**Context:** CaseHub can observe Workers executing in choreography (each Worker sees the CaseFile and acts on it independently). This observability creates an opportunity: describe the expected transitions between Workers, detect when actual execution deviates, and treat deviations either as violations to flag or as discoveries to learn from.

---

## 1. The Core Idea

CaseHub's blackboard architecture is purely reactive. TaskDefinitions fire when their entry criteria are met. There is no concept of an expected execution order — the system is opportunistic by design.

But in practice, most cases follow predictable patterns. A legal document analysis case will almost always go:

```
Text Extraction → Entity Recognition → Risk Assessment → Summary Generation
```

This expected sequence is implicit knowledge — it lives in developers' heads, in documentation, or in the structure of the entry criteria themselves. It is not captured anywhere the system can reason about.

The proposal: **make expected transitions explicit** as an optional, advisory description alongside the Goal and Milestones. Then compare actual execution against the expectation. When they diverge, decide: is this a violation to flag, or a discovery to capture?

---

## 2. Three Distinct Concepts

This design introduces a third concept alongside Goals and Milestones:

| Concept | What it is | Hard or Soft? |
|---------|-----------|---------------|
| `Goal` | The overall case objective — what done looks like | **Hard** — the case exists to achieve this |
| `Milestone` | A named, observable achievement state | **Hard** — must be reached for the goal to be satisfied |
| `Trajectory` | The expected sequence of Milestone/Worker transitions | **Soft** — advisory, deviation is informative not terminal |

Goals and Milestones are constraints on the *destination*. Trajectories are hints about the *path*.

A case with a trajectory but no trajectory violation is running as expected. A case with a trajectory deviation is running in a way worth examining — but it may still achieve the Goal via an alternative path, which is valid.

---

## 3. What a Trajectory Describes

A trajectory describes the expected ordering of transitions between named states (Milestones) or Worker/TaskDefinition activations within a case.

**Example — simple expected sequence:**
```
Text Extraction → Entity Recognition → Risk Assessment → Summary
```

**Example — partial ordering (some flexibility):**
```
Text Extraction → (Entity Recognition ‖ Sentiment Analysis) → Risk Assessment → Summary
```
(Entity Recognition and Sentiment Analysis may happen in any order, but both precede Risk Assessment.)

**Example — constraint-based (DCR style):**
```
IF FraudAlert fires THEN ManualReview must eventually fire
Text Extraction must precede Risk Assessment
Summary must be last
```

The trajectory is not a rigid execution plan — it is a set of ordering constraints or an expected partial order. The blackboard architecture already handles what triggers each step. The trajectory describes what order the system expects those steps to naturally fall into.

---

## 4. Deviation: Violation vs Discovery

When actual execution departs from the expected trajectory, two fundamentally different interpretations are possible:

### 4.1 Violation Mode

The deviation is treated as a signal that something went wrong:

> "We expected `Text Extraction → Entity Recognition → Risk Assessment` but `Risk Assessment` ran before `Entity Recognition` had completed."

Violation signals are useful for:
- Audit and compliance (the case must follow a regulated sequence)
- Quality control (an LLM worker short-circuited a step it shouldn't have)
- Debugging (a dependency is broken — something that should have gated step N didn't)
- SLA enforcement (step B ran at the wrong time relative to step A)

Violation handling responses:
- **Alert**: notify a human reviewer that the trajectory was not followed
- **Halt**: suspend the case pending human intervention
- **Compensate**: trigger a compensating TaskDefinition to restore expected state
- **Log**: record the deviation for audit without interrupting execution

### 4.2 Discovery Mode

The deviation is treated as new knowledge about valid execution paths:

> "We expected `Text Extraction → Entity Recognition → Risk Assessment → Summary` but three consecutive cases went `Text Extraction → Risk Assessment → Summary` and all achieved the Goal successfully — Entity Recognition was never needed."

Discovery signals are useful for:
- **Model refinement**: the trajectory description was wrong or overly constrained
- **Efficiency learning**: a shorter path to the Goal has been observed
- **LLM path discovery**: an LLM-backed Worker found a novel approach that achieves the same outcome
- **Adaptive case management**: the case type evolves based on actual execution history

Discovery handling responses:
- **Capture**: record the new path as a candidate alternative trajectory
- **Promote**: after N successful observations, promote the alternative to an accepted trajectory variant
- **Alert**: surface to a human for review before promotion
- **Annotate**: tag the CaseFile with the deviation for later analysis

### 4.3 Hybrid (Recommended Default)

The case continues running regardless — the Goal is still the authority on when the case is done. The trajectory is advisory. A deviation:
1. Is recorded in the case's observability trace
2. Triggers a notification (alert or log, depending on configuration)
3. Does NOT halt the case unless explicitly configured to do so
4. Is available for later analysis and potential model promotion

```
Trajectory deviation detected:
  Expected: [text-extracted] → [entities] → [risk-assessed]
  Actual:   [text-extracted] → [risk-assessed]   (entities skipped)
  Goal:     STILL ACTIVE — case continues
  Signal:   DEVIATION_OBSERVED — logged for review
  Outcome:  Goal ACHIEVED via shorter path
  Learning: candidate for trajectory model update
```

---

## 5. Connection to Process Mining

This idea is directly grounded in the academic field of **process mining** (van der Aalst et al.), which has three core activities:

### 5.1 Process Discovery
Automatically constructing a process model from event logs. In CaseHub terms: observing many case executions and inferring the typical trajectories from the recorded execution traces. The `PropagationContext` and lineage tracking that CaseHub already does provides the raw event log for discovery.

### 5.2 Conformance Checking
Comparing actual execution traces against a declared process model and identifying deviations. This is the Violation Mode described above. Academic conformance checking algorithms (token-based replay, alignment-based approaches) compute precise fitness metrics: how closely does the observed trace fit the declared model?

In CaseHub terms:
- The **declared model** is the `Trajectory` description
- The **observed trace** is the sequence of Milestone/Worker activations recorded in the case's lineage
- The **fitness score** measures how closely the case followed the expected path

### 5.3 Process Enhancement
Improving or extending an existing process model based on observed execution. This is the Discovery Mode described above. Real-world paths that consistently achieve the Goal but deviate from the declared trajectory become candidates for incorporation into the model.

**Key process mining insight for CaseHub:** The execution trace is already being recorded (PropagationContext lineage). The missing pieces are: (a) a declared trajectory to compare against, and (b) a conformance-checking component that computes deviation signals.

---

## 6. Connection to Declarative Workflow Research (DCR / DECLARE)

From the goal model research: DCR Graphs define constraints on what can and must happen. An execution is *accepted* if it satisfies all constraints. Deviations that reach an accepting state are valid — they are not violations in the DCR model.

This is the theoretical grounding for Discovery Mode: if the case achieves the Goal (reaches the acceptance state) via an unexpected trajectory, the trajectory was an overly strict prior, not a mandatory constraint. The Goal acceptance predicate is the true authority.

DCR's four constraint types map naturally to Trajectory descriptions:

| DCR Relation | CaseHub Trajectory equivalent |
|---|---|
| Condition (A must precede B) | `before(milestoneA, milestoneB)` |
| Response (if A fires, B must eventually fire) | `requires(milestoneA, milestoneB)` |
| Include (A enables B) | `enables(milestoneA, milestoneB)` |
| Exclude (if A fires, B cannot fire) | `excludes(milestoneA, milestoneB)` |

A Trajectory expressed as DCR constraints is a partial specification — the case can follow many paths that satisfy the constraints. Paths that satisfy all constraints are expected. Paths that violate a constraint are deviations. But the Goal acceptance predicate remains the ultimate authority.

---

## 7. Agentic AI Context

This mechanism is particularly valuable when Workers are backed by LLMs, for two reasons:

### 7.1 LLM Paths Are Non-Deterministic

An LLM-backed Worker (including a LangChain4j agent system) does not always take the same path to a result. Given the same CaseFile state, it may:
- Skip an intermediate step it deems unnecessary
- Take a novel approach that still produces valid output
- Decompose a task differently each time

In a traditional workflow engine, non-determinism is an error. In CaseHub with trajectory descriptions, non-determinism is observable and can be handled gracefully — sometimes as violation (when the LLM short-circuited something important), sometimes as discovery (when the LLM found a better path).

### 7.2 The Trajectory Is a Prompt for the System

Trajectory descriptions can inform LLM-based PlanningStrategies. A `SupervisorPlanningStrategy` (analogous to LangChain4j's `SupervisorPlanner`) given the expected trajectory as context will tend to follow it — reducing LLM plan variability. When it deviates anyway, the deviation is recorded.

This creates a feedback loop:
- Trajectory → guides LLM planning decisions
- Actual execution → compared against trajectory
- Deviations → surfaced for review or model update
- Updated trajectory → guides future LLM planning

---

## 8. Design Implications for CaseHub

### 8.1 Trajectory as an Optional Case Descriptor

A Trajectory is not required. A case can run successfully with only a Goal and Milestones. The Trajectory is an optional layer that adds observability and conformance-checking on top of the existing blackboard execution.

```java
// Goal and Milestones only — no trajectory
Goal.of("risk-assessment-complete",
    Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
    Milestone.when(cf -> cf.contains("executive_summary")).named("summary-complete")
);

// Goal, Milestones, and Trajectory
Goal.of("risk-assessment-complete", milestones)
    .withTrajectory(
        Trajectory.expects()
            .then("text-extracted")
            .then("risk-assessed")
            .then("summary-complete")
    );
```

### 8.2 Trajectory Deviation Events

When actual execution deviates from the declared trajectory, the case produces a `TrajectoryDeviationEvent` containing:
- Which Milestone/Worker activated unexpectedly (or failed to activate when expected)
- The expected next step at the point of deviation
- The current CaseFile state at deviation time
- Whether the Goal is still reachable (is deviation recoverable?)
- Whether the deviation has been observed before (known alternative vs novel path)

### 8.3 TrajectoryEvaluator Component

A `TrajectoryEvaluator` runs alongside the `GoalEvaluator` in the CaseEngine control loop, consuming the same CaseFile state and the same Milestone activation events:

```
CaseEngine control loop:
  1. GoalEvaluator.checkSatisfaction()
  2. GoalEvaluator.checkAbandonment()
  3. TrajectoryEvaluator.checkConformance()   ← new
  4. ListenerEvaluator.evaluate()
  5. PlanningStrategy.rank()
  6. execute top PlanItem
  7. TrajectoryEvaluator.recordActivation()   ← new: record for trace
```

The `TrajectoryEvaluator` is purely observational. It produces events. It does not halt or redirect execution (unless configured to do so via a violation policy).

### 8.4 Violation Policy

Configurable per case type or per case instance:

```java
Trajectory.expects()
    .then("text-extracted")
    .then("risk-assessed")
    .withViolationPolicy(ViolationPolicy.LOG_AND_CONTINUE)  // default
    // or ViolationPolicy.ALERT
    // or ViolationPolicy.HALT
    // or ViolationPolicy.COMPENSATE(compensatingTaskDefinition)
```

### 8.5 Discovery Registry

When a deviation results in a successfully-achieved Goal, the alternative path is recorded in a `TrajectoryDiscoveryRegistry`. After a configurable threshold of observations, the alternative path is surfaced as a candidate for promotion to an accepted trajectory variant:

```
TrajectoryDiscovery #42:
  Case type: legal-analysis
  Original trajectory: text-extracted → entities → risk-assessed → summary
  Observed alternative: text-extracted → risk-assessed → summary (entities skipped)
  Observed count: 7
  Goal achieved in all cases: YES
  Status: CANDIDATE — awaiting human review
```

### 8.6 Connection to PropagationContext and Lineage

CaseHub already records execution lineage through `PropagationContext`. The Trajectory mechanism consumes this lineage as its raw input. Each Milestone activation event is an entry in the lineage trace. Conformance checking compares the ordered sequence of activations against the declared trajectory constraints.

No new storage is required for the trace — it is derived from the existing PropagationContext lineage. What is new: the comparison algorithm and the deviation event model.

---

## 9. Relationship to Goals and Milestones

| Dimension | Goal | Milestone | Trajectory |
|-----------|------|-----------|------------|
| Describes | The destination | Named stops on the journey | The expected path |
| Hard or soft? | Hard (case exists to achieve it) | Hard (must be reached) | Soft (advisory) |
| Evaluated against | CaseFile state | CaseFile state | Execution ordering |
| Deviation response | None (it's the target) | Case fails to complete | Violation or Discovery |
| Required? | Yes (to use Goal model) | Yes (at least one) | No (optional) |
| Who sets it? | Caller at case creation | Caller at case creation | Caller or case type registry |

A Trajectory deviation never prevents Goal achievement. The Goal is the authority. The Trajectory is a description of how you expected to get there.

---

## 10. Open Questions

### 10.1 Who owns the Trajectory definition?
- **Instance-level**: caller provides the trajectory at `createAndSolve()` time — each case can have a custom expected path
- **Type-level**: the trajectory is registered as part of the case type definition (like TaskDefinitions are) — all cases of that type share the same expected trajectory
- **Both**: type-level provides defaults, instance-level can override

### 10.2 Strict sequence vs partial order vs constraint set?
- **Strict**: `then(A).then(B).then(C)` — A must happen, then B, then C
- **Partial order**: `A before C`, `B before C`, `A and B can be concurrent` — more flexible
- **DCR constraints**: full expressiveness (`condition`, `response`, `include`, `exclude`)

Starting with strict sequence is simplest. Partial order and DCR constraints can be added when use cases demand them.

### 10.3 How does the Discovery Registry interact with case type evolution?
Discovered alternative trajectories are candidates for model promotion. But who promotes them? A human reviewer? An automated threshold? And when promoted, do they become alternative trajectory variants (the case type has multiple valid trajectories) or do they replace the original?

### 10.4 Intersection with Goal abandonment?
If a trajectory deviation makes the Goal unreachable (the path taken cannot produce the remaining Milestones), does the Trajectory evaluation trigger Goal abandonment? This is the intersection point between the `TrajectoryEvaluator` and the `GoalEvaluator`. The interaction needs careful design.

### 10.5 Performance overhead?
Conformance checking on every Milestone activation adds evaluation overhead. For long cases with complex trajectories and many Milestones, this could be significant. The `TrajectoryEvaluator` should be asynchronous and non-blocking relative to the control loop.

### 10.6 How does this interact with autonomous workers?
Autonomous workers (workers that self-initiate via `notifyAutonomousWork()`) may trigger Milestone activations that were not anticipated in any trajectory. Do they inherently produce deviations? Should autonomous worker activations be exempt from trajectory conformance checking?

---

## 11. Related Work and Prior Art

- **Process mining**: van der Aalst et al. — ProM framework, IEEE Task Force on Process Mining
- **DCR Graphs**: Hildebrandt, Mukkamala, Slaats — dynamic condition response graphs
- **DECLARE**: Pesic, Schonenberg, van der Aalst — constraint-based declarative workflow
- **Adaptive Case Management (ACM)**: Keith Swenson et al. — cases that deviate and learn
- **Conformance checking**: token-based replay, alignment-based approaches (van der Aalst)
- **LangChain4j GoalOrientedPlanner**: A\* search over agent dependency graph — a related mechanism for path-finding in agent systems
- **CaseHub PropagationContext**: the lineage tracking already in place provides the raw event log for process mining
