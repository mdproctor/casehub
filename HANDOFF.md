# Session Handover — CaseHub
**Date:** 2026-05-05
**Session:** Migration Audit and Tracking

---

## Where We Are

**PRs #232, #233, #234 still open — not merged this session.**

*PR state and branch details unchanged — `git show HEAD~1:HANDOFF.md`*

**What changed this session:**
- Migration audit completed — 5 untracked items found and filed as engine issues:
  - #235 `casehub-quarkus/` extension (epic — largest remaining chunk)
  - #236 `casehub-examples/` (blocks poc archival)
  - #237 Long-lived workers with lifecycle scopes (CASE/STAGE/BINDING)
  - #238 `JavaBeanCaseFile<T>`
  - #239 `MapCaseFile` compat shim
- `migration` label created in casehubio/engine, casehubio/work, casehubio/ledger
- 64 issues labelled (59 engine, 4 work, 1 ledger) — open and closed history
- GitHub Projects V2 board created: `https://github.com/orgs/casehubio/projects/2`

---

## Immediate Next Steps

1. **Merge PRs #232, #233, #234** — unchanged, still the first thing
2. **engine#231** — thread Qhorus trigger context into `ProvisionContext` (null call site)
3. **Claudony follow-on** — bump `casehub-qhorus-api`, implement `postToChannel(channel, from, content, type)`; claudony#94 waits on engine#231
4. **casehub-quarkus/** (engine#235) — biggest remaining migration item; plan before coding

---

## Reconstruction Status (parked)

*Unchanged — `git show HEAD~2:HANDOFF.md`*

---

## Repo Build Status

*Unchanged — `git show HEAD~1:HANDOFF.md`*

---

## Key References

| What | Where |
|---|---|
| Migration project board | `https://github.com/orgs/casehubio/projects/2` |
| casehub-quarkus/ issue | engine#235 |
| casehub-examples/ issue | engine#236 |
| Trigger context threading | engine#231 |
| Reconstruction parking note | `docs/superpowers/specs/reconstruction-compaction-parking-note.md` |
| Squash policy | `docs/superpowers/specs/commit-squash-policy.md` |
