# Session Handover — CaseHub
**Date:** 2026-04-10
**Branch:** main (all committed and pushed)

---

## Where We Are

A full brainstorming and design session produced the unified merge design for two CaseHub implementations. The co-worker's **casehub-engine** (`/Users/mdproctor/dev/casehub-engine`) was systematically analysed alongside casehub. All major architectural decisions are made and documented.

**Merge direction:** casehub as base. casehub-engine contributes reactive infrastructure, Goal model, EventLog, Quartz, YAML schema, Binding+Trigger model.

**Execution model:** Async event cycle (always non-blocking). `notifyAutonomousWork()` workaround disappears — autonomous workers just write to CaseFile.

---

## Immediate Next Steps

1. **Create GitHub epic for the merge work** — CLAUDE.md requires an issue before implementation
2. **Invoke `writing-plans`** on `docs/superpowers/specs/2026-04-09-casehub-unified-design.md` to produce a phased implementation plan
3. **Start Phase 1** — unseal `ExpressionEvaluator`, add `LambdaExpressionEvaluator` (no naming decisions, safe to start now)
4. **Update issue #7** plan to align with casehub-engine's Goal model (`GoalExpression`, `GoalKind`, `CaseCompletion`)

---

## Key Files (read if task requires it)

| File | What it is |
|------|-----------|
| `docs/superpowers/specs/2026-04-09-casehub-unified-design.md` | **Primary** — full merge design: decisions, naming table, module structure, 9-phase plan |
| `docs/design-snapshots/2026-04-10-casehub-merge-design.md` | Design snapshot — current state, open questions |
| `docs/superpowers/specs/scratch-merge-design.md` | Working notes — deeper rationale behind decisions |
| `docs/blog/` | 4-entry blog series (mdp01–mdp04) — narrative of CaseHub's development |

---

## Open Naming Questions (deferred — not blocking Phase 1–4)

- `Milestone` clash — casehub's CMMN marker vs casehub-engine's JQ predicate. Proposed: `ProgressMarker`. Needs co-worker agreement.
- `CaseState` alignment — casehub vs casehub-engine have different enum values
- `ContextChangeTrigger` vs `StateChangeTrigger`

---

## GitHub Issues

| Issue | Status |
|-------|--------|
| #7 — Goal model | Open — plan needs updating to align with casehub-engine's model before implementation |
| #1–#6 | Closed |

casehub-engine is at `/Users/mdproctor/dev/casehub-engine` — open in IntelliJ (use `project_path` explicitly with MCP tools).
