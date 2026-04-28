# Session Handover — CaseHub
**Date:** 2026-04-28
**Branch (casehub-engine):** `fix/blackboard-mixed-workers-188` (open PR #190 against `casehubio/engine`)

---

## Where We Are

**What landed this session:**
- All 10 PRs (#175–#184) merged to `casehubio/engine` main by trebelreel — SPI wiring, lifecycle events, casehub-testing, casehub-work-adapter, NPE fix, ADR-0006, CLAUDE.md, sessionMeta fix, CI fork gate
- **WorkerProvisioner wiring designed** — spec at `docs/superpowers/specs/2026-04-27-worker-provisioner-wiring-design.md`, plan at `docs/superpowers/plans/2026-04-27-worker-provisioner-wiring.md` (15 tasks, full TDD pyramid)
- **ADR-0006** — worker registration as normative act; discovery lineage maps to `causedByEntryId` chains; cross-referenced in Qhorus normative-ledger-design.md and message-type-redesign-design.md
- **Fork discipline established** — all work via `mdproctor/engine` fork, one branch per concern, every commit linked to an issue
- **casehub-connectors snapshotRepository fix** — missing `<snapshotRepository>` caused SNAPSHOT deploys to inherit parent registry URL (403); fixed and pushed direct to main
- **PR #190 open** — `fix/blackboard-mixed-workers-188`: event-driven `MixedWorkersBlackboardTest` using `PlanItemCompletedEvent` carrying exact `planItemId`; CI running
- **casehub-engine CLAUDE.md** — added blackboard event-driven test pattern + PR workflow section (on PR #190 branch)
- Issue #187 created — future `WorkerCandidateSource` SPI chain inside `WorkerRegistry` (no plans to schedule)

**pom.xml rename already done** — `distributionManagement` already shows `casehubio/casehub-engine`; GitHub repo rename is a separate admin task.

---

## Immediate Next Steps

1. **Merge PR #190** (`fix/blackboard-mixed-workers-188`) — CI should be green; check and merge
2. **Fork setup for remaining repos** — claudony, quarkus-qhorus, quarkus-ledger, quarkus-work, casehub-parent still push direct to org; all clean (no unmerged local commits)
3. **WorkerProvisioner wiring implementation** — spec + plan ready; use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; start with Task 1 (sealed `WorkerExecution` hierarchy)
4. **`casehub-quarkus/`** — biggest remaining migration work, not started
5. **#22 (SLA)** — case-level and goal-level SLA not implemented

---

## Key References

| What | Path |
|---|---|
| WorkerProvisioner wiring spec | `docs/superpowers/specs/2026-04-27-worker-provisioner-wiring-design.md` |
| WorkerProvisioner wiring plan | `docs/superpowers/plans/2026-04-27-worker-provisioner-wiring.md` |
| ADR-0006 | `casehub-engine/adr/0006-worker-registration-as-normative-act.md` |
| Migration plan | `docs/superpowers/specs/2026-04-14-casehub-engine-migration-plan.md` |
| Backup branch (fork reset) | `mdproctor/engine:backup/fork-main-2026-04-27` |

## Repo Build Status

| Repo | Status |
|------|--------|
| casehubio/engine | ✅ (all PRs merged) |
| casehubio/quarkus-qhorus | ✅ |
| casehubio/quarkus-ledger | ✅ |
| casehubio/quarkus-work | ✅ |
| casehubio/casehub-connectors | ✅ (snapshotRepository fix landed) |
| casehubio/casehub-parent | ✅ |
| casehubio/claudony | ✅ |
