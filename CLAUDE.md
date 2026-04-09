# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Type

type: java

## Project Structure

CaseHub is a multi-module Maven project:

```
casehub/
├── pom.xml                    # Parent POM
├── casehub-core/             # Core framework implementation
│   └── src/main/java/io/casehub/
│       ├── core/             # CaseFile, TaskDefinition, etc.
│       ├── control/          # CasePlanModel, PlanningStrategy
│       ├── coordination/     # CaseEngine, PropagationContext
│       ├── worker/           # Task, Worker, TaskBroker
│       ├── resilience/       # Retry, timeout, dead-letter
│       └── error/            # Exception types
└── casehub-examples/         # Examples demonstrating architecture
    └── src/main/java/io/casehub/examples/
        ├── SimpleDocumentAnalysis.java
        ├── DocumentAnalysisApp.java
        └── workers/
            ├── LlmReasoningWorker.java
            ├── LlmAnalysisTaskDefinition.java
            ├── DocumentAnalysisWithLlmApp.java
            └── AutonomousMonitoringWorker.java
```

## Build Commands

```bash
# From root directory - builds all modules
mvn compile                          # Compile all modules
mvn clean compile                    # Clean build all modules
mvn test                             # Run all tests
mvn clean test                       # Clean and test all modules

# Build specific module
cd casehub-core && mvn compile       # Build core only
cd casehub-examples && mvn compile   # Build examples only

# Run examples (Quarkus dev mode)
cd casehub-examples && mvn quarkus:dev
```

**Requirements:** Java 21, Quarkus 3.17.5, Maven 3.9+

## Architecture

CaseHub implements the **Blackboard Architecture** (Hayes-Roth 1985) for Quarkus-based agentic AI, using **CMMN** (Case Management Model and Notation) terminology throughout. It has two execution models:

### 1. Collaborative Problem-Solving (CaseFile Model)

A **CaseFile** is a shared key-value workspace. **TaskDefinitions** are specialists that declare entry criteria (keys they need) and produced keys (what they contribute). The **CaseEngine** runs a control loop:

```
CaseEngine.createAndSolve(caseType, initialState)
  → ListenerEvaluator checks TaskDefinition entry criteria against CaseFile state
  → Creates PlanItems for eligible TaskDefinitions
  → PlanningStrategies reason about priority/focus
  → Execute top-priority PlanItem (TaskDefinition writes to CaseFile)
  → Re-evaluate → loop until quiescent or complete
```

### 2. Request-Response (Task Model)

**TaskBroker** accepts TaskRequests, **TaskScheduler** selects a **Worker** by capability match, worker executes and returns TaskResult.

**Autonomous Workers:** CaseHub also supports decentralized workers that work on their own agency. Autonomous workers monitor external systems, decide when work is needed, and notify the system via `WorkerRegistry.notifyAutonomousWork()`. These tasks have `TaskOrigin.AUTONOMOUS` (vs `BROKER_ALLOCATED`) and fully integrate with PropagationContext for lineage tracking. See `casehub/src/main/java/io/casehub/examples/workers/AutonomousMonitoringWorker.java`.

### Package Structure

**casehub-core module** (`casehub-core/src/main/java/io/casehub/`):

| Package | Responsibility |
|---------|---------------|
| `core/` | CaseFile, TaskDefinition, TaskDefinitionRegistry, ListenerEvaluator, CaseStatus |
| `control/` | CasePlanModel, PlanItem, PlanningStrategy — control reasoning layer |
| `coordination/` | CaseEngine (orchestrator), PropagationContext, LineageService |
| `worker/` | Task, TaskBroker, Worker, TaskRegistry, TaskScheduler, TaskOrigin |
| `resilience/` | RetryPolicy, TimeoutEnforcer, PoisonPillDetector, DeadLetterQueue, IdempotencyService |
| `error/` | Exception types and ErrorInfo |
| `core/spi/` | Storage provider interfaces (CaseFileStorageProvider, TaskStorageProvider, PropagationStorageProvider) |
| `annotation/` | CaseType CDI qualifier |

**casehub-examples module** (`casehub-examples/src/main/java/io/casehub/examples/`):

| Package | Contents |
|---------|----------|
| `examples/` | SimpleDocumentAnalysis.java (conceptual), DocumentAnalysisApp.java (real implementation) |
| `examples/workers/` | LlmReasoningWorker, LlmAnalysisTaskDefinition, DocumentAnalysisWithLlmApp, AutonomousMonitoringWorker |

**casehub-flow-worker module** (`casehub-flow-worker/src/main/java/io/casehub/flow/`) - **Optional**:

| Package | Contents |
|---------|----------|
| `flow/` | FlowWorker, FlowWorkflowDefinition, FlowExecutionContext, FlowWorkflowRegistry |
| `flow/examples/` | DocumentProcessingWorkflow, FlowWorkerDemo |

**Note:** The flow-worker module has isolated Quarkus Flow dependencies. Include only if using Quarkus Flow workflows.

### Key Flow

**CaseEngine** is the central orchestrator. It creates a CaseFile + CasePlanModel, then loops: **ListenerEvaluator** evaluates which TaskDefinitions can fire → creates **PlanItems** → **PlanningStrategies** reason about control → top PlanItem executes → TaskDefinition writes to CaseFile → re-evaluate. Loop exits on quiescence (WAITING), completion, fault, or cancellation.

### Lifecycle States

CaseStatus and TaskStatus both use CNCF Serverless Workflow (OWL) phases: `PENDING`, `RUNNING`, `WAITING`, `SUSPENDED`, `CANCELLED`, `FAULTED`, `COMPLETED`.

## Terminology Conventions

This codebase uses CMMN terminology mapped from the original Blackboard Architecture:

| CMMN Term (used in code) | Blackboard Origin |
|--------------------------|------------------|
| CaseFile | Board (shared workspace) |
| TaskDefinition | KnowledgeSource |
| CasePlanModel | ControlBoard |
| PlanItem | KSAR (KS Activation Record) |
| PlanningStrategy | ControlKnowledgeSource |
| ListenerEvaluator | ActivationEngine |
| CaseEngine | BoardController |
| Worker | Executor |

Do not introduce old Blackboard terminology into the codebase. Always use the CMMN terms.

## Configuration

Config properties use the `casehub.` prefix (e.g., `casehub.timeout.check-interval`, `casehub.retry.ks.default.max-attempts`). See `casehub/src/main/resources/application.properties` for all available properties.

## Design Document

`docs/DESIGN.md` contains the comprehensive architecture specification. Keep it consistent with code changes. `QuarkBoard_Design_Document.md` is the original document, used prior to the changes to introduce CMMN terminology

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** mdproctor/casehub
**Changelog:** GitHub Releases (run `gh release create --generate-notes` at milestones)

**Automatic behaviours (Claude follows these at all times in this project):**
- **Before implementation begins** — when the user says "implement", "start coding",
  "execute the plan", "let's build", or similar: check if an active issue or epic
  exists. If not, run issue-workflow Phase 1 to create one **before writing any code**.
- **Before writing any code** — check if an issue exists for what's about to be
  implemented. If not, draft one and assess epic placement (issue-workflow Phase 2)
  before starting. Also check if the work spans multiple concerns.
- **Before any commit** — run issue-workflow Phase 3 (via git-commit) to confirm
  issue linkage and check for split candidates. This is a fallback — the issue
  should already exist from before implementation began.
- **All commits should reference an issue** — `Refs #N` (ongoing) or `Closes #N` (done).
  If the user explicitly says to skip ("commit as is", "no issue"), ask once to confirm
  before proceeding — it must be a deliberate choice, not a default.
