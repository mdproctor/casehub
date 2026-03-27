# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
mvn compile                          # Compile all 53 source files
mvn clean compile                    # Clean build
mvn test -Dtest=TestClassName        # Run a single test class
mvn clean test                       # Run all tests
mvn clean quarkus:dev                # Quarkus dev mode with hot reload
```

Build runs from the `casehub/` module directory. Java 21, Quarkus 3.17.5, Maven.

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

### Package Structure (`casehub/src/main/java/io/casehub/`)

| Package | Responsibility |
|---------|---------------|
| `core/` | CaseFile, TaskDefinition, TaskDefinitionRegistry, ListenerEvaluator, CaseStatus |
| `control/` | CasePlanModel, PlanItem, PlanningStrategy — control reasoning layer |
| `coordination/` | CaseEngine (orchestrator), PropagationContext, LineageService |
| `worker/` | Task, TaskBroker, Worker, TaskRegistry, TaskScheduler |
| `resilience/` | RetryPolicy, TimeoutEnforcer, PoisonPillDetector, DeadLetterQueue, IdempotencyService |
| `error/` | Exception types and ErrorInfo |
| `core/spi/` | Storage provider interfaces (CaseFileStorageProvider, TaskStorageProvider, PropagationStorageProvider) |
| `annotation/` | CaseType CDI qualifier |

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

`CaseHub_Design_Document.md` contains the comprehensive architecture specification. Keep it consistent with code changes. `Blackboard_Design_Document.md` is the original document, used prior to the changes to introduce CMMN terminology
