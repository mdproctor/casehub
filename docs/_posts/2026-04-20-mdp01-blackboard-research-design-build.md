---
layout: post
title: "Blackboard: Research, Analysis, and Implementation"
date: 2026-04-20
type: phase-update
entry_type: note
subtype: diary
projects: [casehub]
tags: [blackboard, architecture, reactive, quarkus]
---

PR3 landed upstream as #85 on Thursday. By Friday the upstream had also merged
#72 (in-memory SPI), #74 (treblereel's concurrent signal handling), and a few
others. The engine is in reasonable shape. It was time to start the blackboard.

I wanted to do this properly. Before writing a line of code, I asked Claude to
pull recent academic papers on blackboard architectures — specifically LLM-based
systems. The 2024 papers were more useful than I expected: one showed blackboard
multi-agent systems outperforming static frameworks on five of six benchmarks,
explicitly attributing gains to dynamic agent selection and shared memory. Another
introduced memory stratification — working memory, episodic, semantic — as a
concrete improvement over flat key-value blackboards.

The research shaped the design in one critical way. The classical blackboard
control shell is synchronous. It takes eligible bindings, returns a list. That
means a `PlanningStrategy` can only reason from what's already in memory — it
can't query the EventLog, can't call an LLM scorer, can't reach any I/O without
blocking the Vert.x event loop. We changed `LoopControl.select()` to return
`Uni<List<Binding>>`. One interface change, four files, zero behaviour change for
`ChoreographyLoopControl`. That became the article published alongside:
[Four Things a Synchronous Blackboard Strategy Can't Do](https://mdproctor.github.io/casehub).

The spec and plan came next — 16 tasks, TDD throughout. We started executing via
subagent-driven development.

Task 2 — Maven module setup — surfaced something worth noting: a
`casehub-blackboard` module already existed in the upstream. A prior
implementation was already in place: generic `PlanItem<T>`, stages containing
workers directly, `CasePlanModelRegistry`, `SubCase`.

## Why a rewrite rather than an adaptation

The immediate incompatibility was the `LoopControl` interface change. The
existing `PlanningStrategyLoopControl` implemented the old synchronous signature:

```java
@Override
public List<Binding> select(PlanExecutionContext context, List<Binding> eligible)
```

Our Task 1 change required `Uni<List<Binding>>`. The existing implementation
did not compile against the new interface.

Beyond the compilation break, analysis of the existing implementation identified
six architectural differences that would have required substantial rework in any
case:

**1. Plan model keyed by case type, not case instance.** `CasePlanModelRegistry`
associated plan models with `CaseDefinition` objects. For concurrent cases of
the same type, all running instances would share one plan model — their agendas,
stages, and focus of attention mixed together. The new `BlackboardRegistry` keys
by case UUID; each running instance has an independent `CasePlanModel`.

**2. `PlanItem<T>` — a generic type holding either `Stage` or `Worker`.** This
conflates two distinct concerns: an activation record (a unit of work to schedule)
and a lifecycle container (a stage that gates activation). Making them the same
generic type prevents independent tracking of plan item completion within stages.

**3. Stages contained `Worker` objects directly.** In the existing implementation,
workers were assigned to stages at definition time. In the CMMN model and in the
design we were targeting, stages gate *which Bindings can fire* — they evaluate
entry and exit conditions against the case state, not against worker capability
names. Stage-to-worker coupling at definition time prevents the engine's dynamic
capability matching from working correctly within a staged case.

**4. `PlanningStrategy` did not have access to the plan model.** The interface
took `(CaseContext, List<PlanItem<?>>)`. Strategies were pure selectors with no
way to write control state — no focus of attention, no resource budget, no
extensible key-value store. The separation of domain state (`CaseContext`) from
control state (`CasePlanModel`) that the Hayes-Roth BB1 architecture describes
was not present.

**5. No completion tracking.** There was no equivalent of `PlanItemCompletionHandler`.
Nothing informed the plan model when a worker finished executing. Stage autocomplete
— completing a stage when all its required plan items are done — had no
implementation path.

**6. No stage lifecycle events.** Stage transitions were internal state mutations.
No `StageActivatedEvent`, `StageCompletedEvent`, or `StageTerminatedEvent` were
published on the event bus. Stage lifecycle was opaque to any observability,
lineage, or dashboard component.

Given the incompatible interface and these structural differences, a rewrite from
the new design specification was the more straightforward path than an adaptation.
The result: `plan/`, `stage/`, `event/`, `control/`, `registry/`, `handler/`
packages built against the async `LoopControl` contract, with the six points
above addressed throughout.

## On the synchronous nature of the original LoopControl

It is worth being precise about what "synchronous" meant here, because the
existing handler was already operating in a reactive context.

`CaseContextChangedEventHandler` is a `@ConsumeEvent` handler that returns
`Uni<Void>` — it participates in Vert.x's reactive model. However, within that
handler, the call to `loopControl.select()` was a blocking synchronous call
returning `List<Binding>`:

```java
// Before: synchronous call lodged inside an async chain
List<Binding> selected = loopControl.select(planCtx, eligible);
// ...then compose the Uni from results
return Uni.combine().all().unis(unis).discardItems();
```

The outer context was reactive; the selection point within it was not. Any code
inside `select()` that attempted I/O — querying the EventLog, calling an external
scoring service — would block the event loop thread directly. On Vert.x, blocking
the event loop thread is a correctness violation, not merely a performance concern.

After the change, `select()` returns a `Uni` and participates in the chain:

```java
// After: select() is a first-class participant in the reactive pipeline
return loopControl.select(planCtx, eligible)
    .chain(selected -> { ... });
```

The practical difference: a `PlanningStrategy` can now query the EventLog,
call an LLM scorer, or reach any non-blocking I/O before resolving its selection.
The async context and the selection point are now consistent throughout.

## Code review and findings

The code review returned 18 findings. Two were critical:

First: `PlanItem.priority` was mutable. A strategy calling `setPriority()` after
insertion into the `PriorityBlockingQueue` would silently corrupt the heap —
wrong ordering, no exception, no warning. `priority` was made `final`.

Second: `hasActivePlanItem()` was an O(n) scan of all `ConcurrentHashMap` values
with a TOCTOU gap between the check and the subsequent insert. We replaced it
with an `activeByBinding` index — a single map lookup, effectively atomic.

The six remaining Important findings were all fixed. Three Minor ones tightened
test coverage and the `PlanItemCompletionHandler` control flow.

## Integration and PRs

The upstream had diverged enough that a rebase of the branch produced conflicts
on commit 1 of 48. A fresh branch from `upstream/main` with the net diff applied
via `git diff upstream/main -- <files>` resolved this cleanly. One commit, 68
tests green.

The PR was split into three for review — #88 (async LoopControl, 4 files), #89
(data model, 36 tests), #90 (orchestration + integration, full 68 tests). All
open against `casehubio/engine:main`, merge in order.

The research-identified improvements — meta-control, private agent scratchpad,
memory stratification, hierarchical panels — are all tracked in issues #77–#84.
That's the next evolutionary layer. For now the foundation is in.
