# Session Handover — CaseHub
**Date:** 2026-04-27
**Branch (casehub-engine):** main (casehubio — `a2449ea`)

---

## Where We Are

Ecosystem fully green — all 7 repos, both Pages sites, first-ever full-stack build pass.

**What landed this session:**
- Migration plan cleaned: removed stale ListenerEvaluator and Vert.x items (both already resolved)
- Issue audit: 14 done issues closed across both epics (#2, #3, #11, #30, #45, #76, #131 + children, #145 + children, #152)
- SPIs wired into engine lifecycle (WorkerStatusListener, WorkerContextProvider, CaseChannelProvider) — `a58f042`
- cancelCase / suspendCase / resumeCase added to CaseHub public API — `90e1ae2`
- CLAUDE.md updated: recording SPI test pattern documented
- Garden entry: `@Alternative @Priority(1)` recording SPI technique — `GE-20260427-62d3ab`

---

## Immediate Next Steps

1. **Rename** `casehubio/engine` → `casehubio/casehub-engine`; update `pom.xml` `distributionManagement` URL
2. **`casehub-quarkus/`** — not started, biggest remaining work
3. **WorkerProvisioner wiring** — deferred from #152; needs design: dynamic provisioning changes the model (workers pre-defined in YAML vs provisioned at runtime)
4. **#22 (SLA)** — case-level and goal-level SLA not implemented; milestone SLA exists

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
