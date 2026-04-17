# Session Handover — CaseHub
**Date:** 2026-04-18
**Branch (casehub):** main
**Branch (casehub-engine):** feat/persistence/engine-decoupling (PR3 — pushed, PR open)

---

## Where We Are

PR3 complete and pushed. PR #75 is open against casehubio/engine:main.

The engine module is now JPA-free: domain objects are plain POJOs, all
persistence routes through SPI interfaces, engine tests run without Docker.

**Awaiting upstream:** PR #72 (our memory work) and PR #73 (treblereel's
rebased version) are both open. PR #75 depends on one of them merging first.
Once merged, rebase #75 on new main before review.

**Watch:** PR #74 (treblereel — concurrent signal processing) touches
`SignalReceivedEventHandler`, which we rewrote in PR3. Check for conflicts
before either PR merges.

---

## What Was Done This Session

- Executed 27-task PR3 plan via subagent-driven development
- Added `updateStateAndAppendEvent` to `CaseInstanceRepository` SPI (atomic state + event write)
- Converted `CaseMetaModel`, `CaseInstance`, `EventLog` from `PanacheEntity` to plain POJOs
- Refactored 12 handler/service classes to inject repository SPI
- Removed JPA/Panache/PostgreSQL/Testcontainers deps from engine/pom.xml
- Solved Maven cycle: copied in-memory impls into `engine/src/test/java/` instead of module dep
- Engine tests run without Docker — 353 pass, 1 pre-existing port-conflict flake (AgentPipelineBeanTest)
- Blog: `docs/_posts/2026-04-18-mdp01-cutting-the-jpa-wire.md`
- Garden: 2 new entries (GE-20260417-96accd Maven cycle, GE-20260417-460714 mvn -q false-clean); 2 revisions (GE-20260417-a405a4 library module variant, GE-20260417-c59817 test-local index update)

---

## Open PRs in casehubio/engine

| PR | What | Base | Status |
|---|---|---|---|
| #72 | casehub-persistence-memory (our version) | main | Open — waiting |
| #73 | casehub-persistence-memory (treblereel rebased) | main | Open — likely to merge first |
| #74 | Concurrent signal processing (treblereel) | main | Open — may conflict with PR3 SignalReceivedEventHandler |
| #75 | Engine persistence decoupling (PR3) | main | Open — depends on #72 or #73 |

---

## Immediate Next Steps

1. **Watch #72/#73** — once one merges, rebase #75 on new main and push
2. **Review PR #74** — read treblereel's signal handler changes; confirm compatible with our rewrite before either merges
3. **PR4 (casehub-blackboard)** — start only after #75 is on clean merged ground

---

## Key Files

| File | What |
|------|-------|
| `docs/superpowers/plans/2026-04-16-pr3-engine-decoupling.md` | PR3 plan (completed) |
| `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` | casehub-engine conventions (updated this session) |
| `docs/_posts/2026-04-18-mdp01-cutting-the-jpa-wire.md` | Session blog entry |

## GitHub Issues

| Repo | Issue | Status |
|------|-------|--------|
| casehubio/engine | #30 (epic) | Open — Phase 2 in progress |
| casehubio/engine | #69 | Open — PR3 (engine decoupling), PR #75 filed |
| mdproctor/casehub | #8 | Open — retirement tracking |
