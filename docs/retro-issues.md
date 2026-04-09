# Retrospective Issue Mapping

**Repository:** mdproctor/casehub  
**Analysed:** 2026-04-09  
**Commit range:** a91fa875 → 45209c0d (2026-03-27 → 2026-03-28, 10 commits)  
**Phase boundaries detected:** None (all commits within 2 days, no ADRs, tags, or blog entries)  
**Epics created:** 0 (no warranted groupings — all standalone issues)

---

## Standalone Issues

### #TBD — Initialize repository

**Type:** chore  
**Date:** 2026-03-27  
**Commits:**
- `8788fcf5` — "commit 2" — `.gitignore`, `.claude/settings.local.json`

**What:** Set up project skeleton — `.gitignore` for Maven/IDE artifacts and Claude Code local settings.

---

### #TBD — Implement core framework and multi-module Maven structure

**Type:** feat  
**Date:** 2026-03-27  
**Commits:**
- `e4814476` — "Refactoring to a multi-module maven project." — initial full framework (casehub/ module)
- `63927e99` — "Refactoring to a multi-module maven project." — rename to casehub-core/, add casehub-examples/

**What:** Full initial implementation of the CaseHub framework using Blackboard Architecture with CMMN terminology, structured as a multi-module Maven project (casehub-core + casehub-examples).

Key components delivered:
- `casehub-core`: CaseFile, TaskDefinition, TaskDefinitionRegistry, ListenerEvaluator, CaseStatus, CaseEngine, CasePlanModel, PlanItem, PlanningStrategy, PropagationContext, LineageService, Task, Worker, TaskBroker, TaskScheduler, WorkerRegistry, TaskOrigin (autonomous workers), full resilience layer (RetryPolicy, TimeoutEnforcer, DeadLetterQueue, IdempotencyService, PoisonPillDetector), storage SPI interfaces, error types
- `casehub-examples`: SimpleDocumentAnalysis, DocumentAnalysisApp, LlmReasoningWorker, LlmAnalysisTaskDefinition, DocumentAnalysisWithLlmApp, AutonomousMonitoringWorker

**Primary paths changed:** `casehub-core/src/`, `casehub-examples/src/`, `pom.xml`, `casehub-core/pom.xml`, `casehub-examples/pom.xml`

---

### #TBD — Add CMMN Stages and Milestones

**Type:** feat  
**Date:** 2026-03-28  
**Commits:**
- `0a664efc` — "Stages and Milestones added." — Stage.java, Milestone.java, CasePlanModel updates, storage SPI updates

**What:** Extended the control layer with CMMN Stage and Milestone concepts. Stage groups related PlanItems into a scoped execution boundary; Milestone marks a named point of achievement within a case. Also updated CasePlanModel and StorageProvider SPI to accommodate these additions.

**Primary paths changed:** `casehub-core/src/main/java/io/casehub/control/Stage.java`, `casehub-core/src/main/java/io/casehub/control/Milestone.java`, `casehub-core/src/main/java/io/casehub/control/CasePlanModel.java`, `casehub-core/src/main/java/io/casehub/core/spi/CaseFileStorageProvider.java`

---

### #TBD — Add in-memory storage providers and Flow Worker module

**Type:** feat  
**Date:** 2026-03-28  
**Commits:**
- `259e55f7` — "Add CMMN Stages/Milestones, in-memory storage providers, and Flow worker module."

**What:** Three related additions in one commit:
1. **In-memory storage providers** — concrete SPI implementations (`InMemoryCaseFileStorage`, `InMemoryPropagationStorage`, `InMemoryTaskStorage`) enabling the framework to run without an external database
2. **Stage-based examples** — `StageBasedDocumentProcessingExample` and `StageBasedWorkerIntegrationExample` demonstrating Stage/Milestone usage
3. **casehub-flow-worker module** — optional module integrating CaseHub Workers with Quarkus Flow / Kogito Serverless Workflow, including `FlowWorker`, `FlowWorkflowDefinition`, `FlowExecutionContext`, `FlowWorkflowRegistry`, examples, and a CNCF Serverless Workflow definition (`.sw.json`)

**Primary paths changed:** `casehub-core/src/main/java/io/casehub/core/spi/InMemory*.java`, `casehub-examples/src/main/java/io/casehub/examples/StageBased*.java`, `casehub-flow-worker/`

---

### #TBD — Reorganize documentation

**Type:** docs  
**Date:** 2026-03-28  
**Commits:**
- `a09bcdb1` — "Move design documents to design/ folder and update all references"
- `3c4aad65` — "Move CLAUDE.md back to root folder"
- `0a6ce63f` — "Reorganize documentation: rename design/ to docs/ and organize summary files"
- `45209c0d` — "Standardize design document naming to DESIGN.md"

**What:** Iterative reorganization of the documentation layout — moved design documents into a `docs/` directory, organized summary files under `docs/summaries/`, moved `CLAUDE.md` back to the project root, and standardized the main design document filename to `DESIGN.md`.

**Primary paths changed:** `docs/DESIGN.md`, `docs/summaries/`, `CLAUDE.md`, `README.md`, `CONTRIBUTING.md`

---

## Excluded Commits

| Hash | Message | Reason |
|------|---------|--------|
| `a91fa875` | "commit 1" | Empty commit — no files changed |

---

## Grouping Rationale

- `e4814476` and `63927e99` share the identical message and represent a continuous multi-part operation (initial code dump → module rename). Grouped as one issue.
- `a09bcdb1`, `3c4aad65`, `0a6ce63f`, `45209c0d` are four successive doc-only moves/renames completing a single reorganization effort. Grouped as one issue.
- `0a664efc` and `259e55f7` both reference Stages/Milestones but contribute distinct deliverables that could be independently reviewed; however `0a664efc` is purely the control model changes while `259e55f7` also adds storage impls and an entirely new module. Kept as separate issues for clarity.
- No epics warranted: all commits within a 2-day window, no phase boundaries detected.
