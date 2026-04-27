# Session Handover — CaseHub
**Date:** 2026-04-27
**Branch (casehub-engine):** main (casehubio — `2758a4c`)

---

## Where We Are

**What landed this session:**
- `CaseLifecycleEvent` fired for `WORKER_EXECUTION_STARTED` / `WORKER_EXECUTION_COMPLETED` — fixes Claudony lineage always returning empty (`59cee54`)
- `casehub-testing` module — `@Alternative @Priority(1)` in-memory repos + `WorkResultSubmitter`; Jandex index missing so external consumers still need `casehub-persistence-memory` + `quarkus.arc.selected-alternatives` (`c0edb1c`)
- `casehub-work-adapter` — CDI observer bridges `WorkItemLifecycleEvent` → `PlanItem` transitions via `BlackboardRegistry`; `callerRef` format `case:{caseId}/pi:{planItemId}`; choreography path only (`f440c80`)
- NPE fix: `CaseContextChangedEventHandler` null-guards `getCaseMetaModel()` before `ConcurrentHashMap.get()` — filed casehubio/claudony#82 (`469200c`)
- 4 garden entries: `workItem()` not public (use `source()`), `EXPIRED.isTerminal()` false, Jandex test jar, `JpaWorkloadProvider` ambiguity

**Claudony #81 is now unblocked** — both upstreams (#79 + #80) are on main, published to GitHub Packages via CI.

---

## Immediate Next Steps

1. **Rename** `casehubio/engine` → `casehubio/casehub-engine`; update `pom.xml` `distributionManagement` URL
2. **`casehub-quarkus/`** — not started, biggest remaining work
3. **WorkerProvisioner wiring** — deferred; needs design (dynamic provisioning vs YAML-defined workers)
4. **#22 (SLA)** — case-level and goal-level SLA not implemented; milestone SLA exists

---

## Key References

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## Repo Build Status

*Unchanged — `git show HEAD~1:HANDOFF.md`*
