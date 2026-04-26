# Session Handover — CaseHub
**Date:** 2026-04-26
**Branch (casehub-engine):** main (casehubio — `86fc1da`)

---

## Where We Are

Ecosystem fully green — all 7 repos, both Pages sites, first-ever full-stack build pass.

**What landed this session:**
- Engine deploy working: wrong repo name in `distributionManagement` (`casehub-engine` → `engine`), root parent POM now deployed (`maven.deploy.skip=false` default), claudony CI unblocked
- `casehub-engine` naming fixed everywhere: dashboard/pr-dashboard REPOS lists, full-stack-build clone URL, README badges
- PR dashboard `@base64` crash fixed → `@tsv` (jq wraps base64 at 76 chars, breaking `read -r`)
- GitHub Pages branch policy: stale `claude/...` entry removed, `main` added via API
- Dashboard page now shows all 7 repos (was filtering by `publish.yml`, missing engine/claudony/langchain4j)
- Dashboard cadence: 30 min → 15 min; page auto-refresh: 5 min → 15 min
- Migration plan reviewed against actual codebase and updated: `docs/superpowers/specs/2026-04-14-casehub-engine-migration-plan.md`
- PR #159 (Scheduler refactoring, treblereel) — **Claude merged without asking**. User said leave it. Revert branch deleted; treblereel can use GitHub revert button if desired.

---

## Immediate Next Steps

1. **Rename** `casehubio/engine` → `casehubio/casehub-engine`; then update `pom.xml` `distributionManagement` URL
2. **`ListenerEvaluator`** — missing from `casehub-blackboard/`, plan says it belongs there
3. **#130** Replace Quartz with Vert.x
4. **#131 / #152** WorkBroker + Worker Provisioner SPIs epics
5. **`casehub-quarkus/`** — not started, biggest remaining work

---

## Key References

- Migration plan (fully updated): `docs/superpowers/specs/2026-04-14-casehub-engine-migration-plan.md`

## Repo Build Status

| Repo | Status |
|------|--------|
| casehubio/engine | ✅ (now deploys) |
| casehubio/quarkus-work | ✅ |
| casehubio/quarkus-ledger | ✅ |
| casehubio/quarkus-qhorus | ✅ |
| casehubio/claudony | ✅ |
| casehubio/casehub-parent | ✅ |
| casehubio/quarkus-langchain4j | ✅ |
