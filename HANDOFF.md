# Session Handover — CaseHub
**Date:** 2026-04-29
**Session:** Migration Gaps and SubCaseBinding

---

## Where We Are

**What landed this session:**
- **PR #197** (idempotency window) — merged to `casehubio/engine` main ✅
- **PR #198** (DLQ replay) — merged to `casehubio/engine` main ✅
- **PR #192** (WorkerContextProvider/WorkerProvisioner wiring) — CI green, waiting for trebelreel to merge
- **PR #199** (SubCaseBinding) — CI green, waiting for trebelreel to merge

**Three migration gaps closed** (casehubio/engine#193, #194, #195):
- `casehub.idempotency.window` config + `findSchedulingEvents(after)` cutoff
- `DeadLetterReplayService` (explicit) + `DeadLetterAutoReplayJob` (Quartz, disabled by default)
- SubCaseBinding: `subCase` field on `Binding`, `SubCaseExecutionHandler`, `SubCaseCompletionListener`, `CaseResumptionService` extracted

**quarkus-ledger cascade fixed:** PR #196 (trebelreel) added `quarkus-ledger` transitively; JPA entities broke 4 test modules. Fixed with `NoOpLedgerEntryRepository` + H2 config in `casehub-blackboard`, `casehub-resilience`, `casehub-work-adapter`. Issues filed: quarkus-ledger#73 (domain/JPA split), quarkus-qhorus#128 (same).

**Platform rule added:** `casehub-parent/docs/PLATFORM.md` — persistence module split rule (JPA entities must be in separate module from domain SPIs).

---

## Immediate Next Steps

1. **Wait for trebelreel to merge #192 and #199** — both CI green, no action needed
2. **`casehub-quarkus/` extension** — biggest remaining migration gap; needs design before code. See migration plan for scope.
3. **Issue #186** (WorkerScheduleEvent → Qhorus COMMAND) — design work needed

---

## Repo Build Status

| Repo | Status |
|------|--------|
| casehubio/engine main | ✅ (#197, #198 merged) |
| mdproctor/engine feat/worker-provisioner-context-wiring-191 | ✅ CI green → PR #192 |
| mdproctor/engine feat/subcase-binding-195 | ✅ CI green → PR #199 |
| casehubio/quarkus-ledger | ✅ pushed (DOUBLE PRECISION fix + trust score fixes) |

## Key References

| What | Path |
|---|---|
| Migration plan | `docs/superpowers/specs/2026-04-14-casehub-engine-migration-plan.md` (casehub repo) |
| SubCase design spec | `docs/superpowers/specs/2026-04-28-migration-gaps-design.md` |
| NoOpLedgerEntryRepository pattern | `engine/src/test/java/io/casehub/engine/NoOpLedgerEntryRepository.java` |
| Platform persistence rule | `casehub-parent/docs/PLATFORM.md` |
