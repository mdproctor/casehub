# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Type

type: java

## Project Structure

CaseHub is a multi-module Maven project:

```
casehub/
├── pom.xml                          # Parent POM
├── casehub-core/                    # Interfaces + framework logic only (no persistence)
├── casehub-persistence-memory/      # In-memory SPI implementations (fast unit tests)
├── casehub-persistence-hibernate/   # Hibernate/Panache JPA implementations (production)
├── casehub-examples/                # Examples (depends on casehub-persistence-memory)
└── casehub-flow-worker/             # Optional Quarkus Flow integration
```

## Website

The project site is published at `https://mdproctor.github.io/casehub` via GitHub Pages.

Jekyll source lives in `docs/`. Build locally: `cd docs && bundle exec jekyll serve` (requires Ruby 3+).

**Blog posts are authored in `docs/_posts/`** (not `docs/blog/`). Filename format: `YYYY-MM-DD-slug.md`. Every post needs frontmatter:

```yaml
---
layout: post
title: "Post Title"
date: YYYY-MM-DD
tags: [tag1, tag2]
excerpt: "One sentence summary shown in listings and cards."
---
```

Do not add files to `docs/` without also adding them to the `exclude:` list in `docs/_config.yml` if they should not be published.

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

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.

**Positive framing:** Avoid negative framings in all writing. Reword to be constructive and forward-looking (e.g. "how we improved from here" not "what was wrong"). Applies to blog entries, design docs, commit messages, and any other writing — unless explicitly told otherwise.

## Design Document

`docs/DESIGN.md` contains the comprehensive architecture specification. Keep it consistent with code changes. `docs/adr/` contains Architecture Decision Records — append-only, numbered sequentially (`0001-title.md`). `docs/research/` contains design research documents. `QuarkBoard_Design_Document.md` is the legacy original.

## Ecosystem Context

CaseHub is the orchestration/choreography engine in a three-project Quarkus Native AI Agent Ecosystem. Load the ecosystem design only when working on SPI interfaces, the `casehub-mcp` module, Goal/Transition data model, or CaseEngine extension points:

@/Users/mdproctor/claude/claudony/docs/superpowers/specs/2026-04-13-quarkus-ai-ecosystem-design.md

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
