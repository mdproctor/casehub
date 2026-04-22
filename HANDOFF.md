# Session Handover — CaseHub
**Date:** 2026-04-22
**Branch (casehub):** main
**Branch (casehub-engine):** main (local, ahead of upstream by 9 casehub-resilience commits)

---

## Where We Are

casehub-resilience is implemented and open as PR #126 on casehubio/engine.

Blackboard PRs #88 and #89 merged during this session. PR #90 is CLEAN and ready to merge. PRs #91–#100 and #119/#120 need rebasing onto the new upstream/main (since #88 and #89 have merged).

**Full PR status:**

| PR | What | Status |
|---|---|---|
| #88 | Async LoopControl [1/3] | MERGED |
| #89 | Data model [2/3] | MERGED |
| #90 | Orchestration [3/3] | CLEAN — ready to merge |
| #91–#95 | QE A–E | Need rebase onto upstream/main |
| #96–#100 | QE F–J | Need rebase onto upstream/main |
| #119 | PropagationContext to api/ | Need rebase |
| #120 | Architectural exclusion fitness tests | Need rebase |
| #126 | casehub-resilience | Open — new this session |

**casehub-engine local main:** 9 commits ahead of upstream/main (all casehub-resilience). Pushed to mdproctor/engine:main. PR #126 is from mdproctor:main → casehubio/engine:main.

---

## What Was Done This Session

- casehub-resilience implemented: ConflictResolver SPI (3 strategies), CaseTimeoutEnforcer (@Scheduled wall-clock scanner), 59 tests
- Key finding: Quarkus Vert.x @ConsumeEvent on a request() address competes round-robin with the primary consumer — use lazy start-time recording instead
- Key finding: after merging a branch that modifies api/, run `mvn install -DskipTests -pl api,engine,casehub-resilience -am` before tests or you get NoSuchMethodError from stale local Maven JAR
- Merged feat/casehub-resilience-main into local main, pushed to origin, PR #126 open
- Blog entry written: `docs/_posts/2026-04-22-mdp01-resilience-conflict-timeout-vertx.md`
- Garden PR #95: 3 Quarkus entries submitted (stale-maven-jar, scheduled-clock-testability, consumeevent-request-starvation)

---

## Immediate Next Steps

1. **Merge PR #90** (CLEAN) — ping treblereel; then cascade: rebase #91–#100 onto new upstream/main
2. **Rebase #119 and #120** onto upstream/main after #90 merges
3. **Upgrade CaseTimeoutEnforcer** to use `PropagationContext.isBudgetExhausted()` after PR #119 merges (one-line change in `CaseTimeoutEnforcer.java`)
4. **Naming ADR** — Task vs WorkItem — needed before any TaskBroker implementation (issue #121 open decision #1)
5. **Claim SLA policy** (issue #122) — four approaches A–D, needs decision with treblereel

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/plans/2026-04-21-casehub-resilience.md` | Resilience plan (all 7 tasks complete) |
| `docs/_posts/2026-04-22-mdp01-resilience-conflict-timeout-vertx.md` | Blog: resilience phase update |
| `docs/adr/INDEX.md` | ADR-0001 (Goal Model), ADR-0002 (Stage Binding Gating) |

## GitHub Issues (casehubio/engine)

*Unchanged — `git show HEAD~1:HANDOFF.md`*
