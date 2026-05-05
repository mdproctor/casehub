# Session Handover — CaseHub
**Date:** 2026-05-05
**Session:** MessageType SPI and ProvisionContext — two PRs opened against casehubio/engine

---

## Where We Are

**What landed this session (engine repo):**
- PR #232 — `feat/engine-221-message-type` — `MessageType` (from `casehub-qhorus-api`) added to `CaseChannelProvider.postToChannel()` and reactive variant; 3-arg default delegates with `null`; `WorkerScheduleEventHandler.dispatchCommand()` passes `MessageType.COMMAND` explicitly
- PR #233 — `feat/engine-229-provision-context-trigger` — `triggerChannelId` + `triggerCorrelationId` (nullable String) added to `ProvisionContext`; engine call site passes `null`; Javadoc references engine#231

**New issues opened:**
- engine#230 — normative layer audit (extract `MessageType` to `casehubio/protocol` when third consumer appears)
- engine#231 — thread Qhorus trigger context through CaseFile-update API into `ProvisionContext` (follow-on; until done, call site passes null)

**Design decision:** engine takes narrow dependency on `casehub-qhorus-api` for `MessageType` (pragmatic, tracked for future extraction). Both PRs include DESIGN.md + CLAUDE.md doc updates on the feature branches.

---

## Immediate Next Steps

1. **Review and merge PR #232 and #233** — both passed full TDD + two-stage review; ready
2. **Claudony follow-on** — after PRs merge, claudony picks up `casehub-qhorus-api` bump and implements `ClaudonyCaseChannelProvider.postToChannel(channel, from, content, type)` forwarding type to Qhorus; claudony#94 uses the `ProvisionContext` trigger fields once engine#231 lands

---

## Reconstruction Status (parked)

*Unchanged — `git show HEAD~1:HANDOFF.md`*

---

## Repo Build Status

| Repo | Status |
|------|--------|
| casehubio/engine main | ✅ green; PRs #232 + #233 open, not yet merged |
| mdproctor/engine main | ✅ clean (revert of misplaced doc commit applied) |

## Key References

| What | Where |
|---|---|
| Reconstruction parking note | `docs/superpowers/specs/reconstruction-compaction-parking-note.md` |
| engine PR #232 (MessageType) | `feat/engine-221-message-type` |
| engine PR #233 (ProvisionContext) | `feat/engine-229-provision-context-trigger` |
| Normative layer future work | engine#230 |
| Trigger context threading | engine#231 |
