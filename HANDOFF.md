# Session Handover — CaseHub
**Date:** 2026-04-20
**Branch (casehub):** main
**Branch (casehub-engine):** 3 PRs open from fork branches

---

## Where We Are

All upstream PRs (#72, #74, #85/PR3) have merged into `casehubio/engine:main`.

`casehub-blackboard` module redesigned from scratch — wiped treblereel's existing
implementation, rebuilt with async-reactive design. 68 tests passing.

**3 PRs open against `casehubio/engine:main` — merge in order:**

| PR | Branch | What | Tests |
|---|---|---|---|
| #88 | `feat/bb-1-async-loop-control` | `LoopControl.select()` → `Uni<List<Binding>>` (4 files) | 386 engine |
| #89 | `feat/bb-2-data-model` | PlanItem, CasePlanModel, Stage, event records (pure Java) | 36 unit |
| #90 | `feat/bb-3-orchestration` | PlanningStrategy, handlers, PlanningStrategyLoopControl, @QuarkusTest | 68 total |

**Awaiting upstream review.** All branches built cleanly from `upstream/main`.

---

## What Was Done This Session

- Researched LLM-based blackboard papers (arXiv 2507.01701, 2510.01285); informed async design
- Brainstormed and designed async reactive blackboard architecture (async LoopControl key insight)
- Published article: `docs/_posts/2026-04-18-mdp02-reactive-blackboard-control-shell.md`
- Spec: `docs/superpowers/specs/2026-04-18-casehub-blackboard-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-casehub-blackboard.md`
- Executed 16-task subagent-driven build; discovered treblereel's existing impl mid-task
- Decision: clean design forward → wiped old impl (2519 lines), rebuilt from scratch
- Code review (18 findings): fixed critical PBQ mutation bug (priority now final), TOCTOU race
  (activeByBinding O(1) index), plus 15 important/minor fixes; 68 tests passing
- Stacked PRs #88/#89/#90 created and pushed to fork, opened against upstream
- casehub-engine CLAUDE.md updated with blackboard module test conventions
- 7 garden entries submitted (PRs #82, #83 to Hortora/garden)
- Blog: `docs/_posts/2026-04-20-mdp01-blackboard-research-design-build.md`

---

## Open PRs in casehubio/engine

| PR | What | Status |
|---|---|---|
| #88 | Async LoopControl (prerequisite for #89, #90) | Open — awaiting review |
| #89 | Data model | Open — merge #88 first |
| #90 | Orchestration + integration tests | Open — merge #89 first |

---

## Immediate Next Steps

1. **Watch #88/#89/#90** — ping upstream once ready for review
2. **Next module: casehub-resilience** (#51) — DLQ, PoisonPill, RetryPolicy, TimeoutEnforcer;
   align with #22 (treblereel's ExecutionPolicy) before starting
3. **Milestone/Goal/Stage alignment** (#84) — worth design discussion before casehub-quarkus

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/specs/2026-04-18-casehub-blackboard-design.md` | Full design spec |
| `docs/superpowers/plans/2026-04-18-casehub-blackboard.md` | 16-task plan (executed) |
| `docs/_posts/2026-04-18-mdp02-reactive-blackboard-control-shell.md` | Technical article |
| `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` | Updated with blackboard test conventions |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #76 | Open — PRs #88/#89/#90 filed |
| casehubio/engine | #77 | Open — future evolution epic (#78–#83 child issues) |
| casehubio/engine | #84 | Open — Milestone/Goal/Stage alignment |
| mdproctor/casehub | #8 | Open — retirement tracking |
