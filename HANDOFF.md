# Session Handover — CaseHub
**Date:** 2026-04-23
**Branch (casehub):** main
**Branch (casehub-engine):** main (local, ahead of upstream by 22 WorkBroker commits — not yet PR'd)

---

## Where We Are

WorkBroker integration complete and merged to local main (epic #131, casehubio/engine).
473 tests passing. Branch `feat/bb-qa-i-stage-entry-validation` deleted.

**What landed:**
- Choreography: `WorkBroker.apply()` + `LeastLoadedStrategy` replaces schedule-all in `publishWorkerSchedules()`
- Orchestration: `WorkOrchestrator` (durable replacement for casehub-core `TaskBroker`) — `submit()`/`submitAndWait()` return `CompletionStage<WorkResult>`
- WAITING state: `waitingForWorkId` on `CaseInstance` + `CaseInstanceEntity`, V1.4.0 Flyway migration
- `PendingWorkRegistry` with thread-safe synchronization + EventLog startup recovery
- `WorkflowExecutionCompletedHandler` resumes WAITING cases on matching completion
- ADR-0003: Work/WorkBroker/WorkItem/Task naming hierarchy — `TaskBroker` name retired
- `docs/DESIGN.md` synced, issue #121 closed with comment

**casehub-engine main is 22 commits ahead of upstream/main** — needs a PR to treblereel.

---

## Immediate Next Steps

1. **Push and open PR** for WorkBroker integration against casehubio/engine — push `main` to origin fork, then `gh pr create --repo casehubio/engine`
2. **Close issue #121** — `gh issue close 121 --repo casehubio/engine` (naming decision resolved by ADR-0003)
3. **Claim SLA policy (issue #122)** — four approaches A–D, needs decision with treblereel
4. **Task vs WorkItem naming (#121)** — done; alignment steps for WorkBroker integration tracked by issues #132–#137

---

## Key Files (casehub-engine)

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## GitHub Issues (casehubio/engine)

- **#131** — Epic: WorkBroker integration (22 commits on local main, not PR'd yet)
- **#132–#137** — Child issues (CI, pom, choreography, orchestration, WAITING, docs) — complete
- **#121** — Naming decision — closed by ADR-0003 comment; `gh issue close` pending
- **#128** — PropagationContext/CaseTimeoutEnforcer — closed (PR #129 merged upstream)
- **Open:** #122 (Claim SLA policy — next decision needed with treblereel)
