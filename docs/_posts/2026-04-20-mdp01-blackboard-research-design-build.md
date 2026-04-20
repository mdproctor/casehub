---
layout: post
title: "Blackboard: Research, Design, Build, Wipe, Rebuild"
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

Task 2 — Maven module setup — came back unexpected: the `casehub-blackboard`
module already existed. Treblereel had implemented it. Generic `PlanItem<T>`,
stages containing workers directly, `CasePlanModelRegistry`, `SubCase`. Different
architecture from what we designed.

I looked at it for about two minutes. My response: *"I don't care about old code
or compatibility. I want the best clean design going forward."*

We wiped it. All of it — 11 main source files, 13 test files, 2519 lines deleted
in a single commit. Then built from scratch: `plan/`, `stage/`, `event/`,
`control/`, `registry/`, `handler/`. The new design has final priority on
`PlanItem` (no post-insertion PBQ mutation), an `activeByBinding` index for
O(1) atomic deduplication, `ConcurrentHashMap.newKeySet()` for Stage containment.
68 tests. All green.

The code review came back with 18 findings. Two were critical enough to have
caused production bugs:

First: `PlanItem.priority` was mutable. `setPriority()` was public. A strategy
calling it after insertion would silently corrupt the `PriorityBlockingQueue`
heap — wrong ordering, no exception, no warning. Claude caught it. We made
`priority` final.

Second: `hasActivePlanItem()` was scanning all `ConcurrentHashMap` values in a
stream — O(n) with a TOCTOU gap between the check and the insert. We replaced
it with an `activeByBinding` index: a single map lookup, effectively atomic.

The six remaining Important findings were all fixed. Three more Minor ones
tightened test coverage and the `PlanItemCompletionHandler` control flow.

Then the merge attempt. The upstream had diverged enough that `git rebase upstream/main`
produced conflicts on commit 1 of 48. I aborted it, created a fresh branch from
upstream, extracted the net diff with `git diff upstream/main -- <files>`,
applied it cleanly. One commit. 68 tests still green.

One problem: the reviewer said the PR was too large. Three PRs instead — #88
(async LoopControl, 4 files), #89 (data model, 36 tests), #90 (orchestration +
integration, full 68 tests). All open against `casehubio/engine:main`, merge in
order.

The research-identified improvements — meta-control, private agent scratchpad,
memory stratification, hierarchical panels — are all tracked in issues #77–#84.
That's the next evolutionary layer. For now the foundation is in.
