# CaseHub: Case Management Architecture for Quarkus and AgenticAI

## Executive Summary

CaseHub is a lightweight case management architecture service for Quarkus-based agentic AI systems. It provides a **shared workspace** where autonomous task definitions collaboratively build solutions, combined with a **task coordination** layer for direct request-response work — all without requiring direct coupling between participants.

**Project Duration (MVP):** 2 weeks (+ 1 week buffer for polish)
**Target Platform:** Quarkus
**Primary Use Case:** CaseHub and general AgenticAI task coordination

### Key Architectural Principles
- **True Case Management Semantics**: Shared workspace (CaseFile) where multiple task definitions read from and contribute to an evolving solution
- **Data-Driven Activation**: Task definitions fire when their preconditions are met on the case file, enabling opportunistic problem solving
- **Dual Execution Model**: CaseFile model for collaborative multi-contributor problems; Task model for simple request-response work
- **Modular Architecture**: Multi-module Maven structure with isolated dependencies; core framework has zero workflow engine dependencies
- **Separation of Concerns**: Specialized components (CaseEngine, TaskBroker, registries, schedulers)
- **Observable by Default**: Built-in metrics, structured logging, and health checks
- **Secure by Default**: API key authentication for case workers, RBAC for requestors
- **Thread-Safe**: Concurrent reads and writes to the shared workspace without data races
- **Pluggable Storage**: SPI-based abstraction for future Redis/PostgreSQL backends
- **Workflow Integration**: Optional modules for Quarkus Flow, Temporal, Camunda, and other workflow engines

### Document Structure

This document is organized around six conceptual pillars:

| # | Pillar | What It Covers |
|---|--------|---------------|
| §0.5 | **Project Structure** | Multi-module Maven architecture, module dependencies, workflow integration |
| §3 | **Case Management Core** | CaseFile (shared workspace), TaskDefinitions, ListenerEvaluator, storage SPI |
| §4 | **Workers** | Workers, Task model (request-response), case worker lifecycle |
| §5 | **Coordination** | CaseEngine (control loop), context propagation, lineage & hierarchy |
| §6 | **Observability & Control** | CasePlanModel, PlanningStrategy, PlanItems, logging, metrics, health checks |
| §7 | **Resilience** | Error handling, timeouts, retry, dead letter, distributed consistency |
| §8 | **Agentic AI Specifics** | Dual execution model, Quarkus/CDI integration, security, workflow examples |

---

## 0.5 Project Structure

CaseHub is organized as a multi-module Maven project to support modularity and dependency isolation:

```
casehub/
├── pom.xml                         # Parent POM (Quarkus 3.17.5)
├── casehub-core/                  # Core framework — interfaces + logic, zero persistence
│   └── src/main/java/io/casehub/
│       ├── core/                  # CaseFile, TaskDefinition, ListenerEvaluator
│       ├── core/spi/              # CaseFileRepository SPI
│       ├── control/               # CasePlanModel, PlanItem, PlanningStrategy
│       ├── coordination/          # CaseEngine, PropagationContext
│       ├── worker/                # Task, Worker, TaskBroker, TaskScheduler, TaskRepository SPI
│       ├── resilience/            # Retry, timeout, dead-letter, idempotency
│       ├── error/                 # Exception types and ErrorInfo
│       └── annotation/            # CaseType CDI qualifier
├── casehub-persistence-memory/    # In-memory SPI implementations (zero external deps)
│   └── src/main/java/io/casehub/persistence/memory/
│       ├── InMemoryCaseFile.java
│       ├── InMemoryTask.java
│       ├── InMemoryCaseFileRepository.java
│       └── InMemoryTaskRepository.java
├── casehub-persistence-hibernate/  # JPA/Panache SPI implementations
│   └── src/main/java/io/casehub/persistence/hibernate/
│       ├── HibernateCaseFile.java
│       ├── HibernateTask.java
│       ├── HibernateCaseFileRepository.java
│       └── HibernateTaskRepository.java
├── casehub-examples/              # Examples (depends on casehub-persistence-memory)
│   └── src/main/java/io/casehub/examples/
│       ├── SimpleDocumentAnalysis.java
│       ├── DocumentAnalysisApp.java
│       └── workers/
│           ├── LlmReasoningWorker.java
│           ├── LlmAnalysisTaskDefinition.java
│           ├── DocumentAnalysisWithLlmApp.java
│           └── AutonomousMonitoringWorker.java
└── casehub-flow-worker/           # Optional: Quarkus Flow workflow integration
    └── src/main/java/io/casehub/flow/
        ├── FlowWorker.java                    # Worker for executing workflows
        ├── FlowWorkflowDefinition.java        # Workflow interface
        ├── FlowExecutionContext.java          # Execution context
        ├── FlowWorkflowRegistry.java          # Workflow registry
        └── examples/
            ├── QuarkusFlowDocumentWorkflow.java   # Programmatic workflow
            ├── DocumentFunctions.java             # Workflow step functions
            └── FlowWorkerQuarkusDemo.java         # Quarkus runtime demo
```

### Module Dependencies

```
casehub-core (interfaces + logic; zero persistence deps)
    ↑
    ├── casehub-persistence-memory (implements CaseFileRepository + TaskRepository, no external deps)
    ├── casehub-persistence-hibernate (implements same SPIs via JPA/Panache; H2 for tests, PostgreSQL for prod)
    ├── casehub-examples (depends on casehub-persistence-memory)
    └── casehub-flow-worker (depends on core + quarkus-flow 0.7.1)
```

**Dependency Isolation Strategy:**
- **casehub-core**: All framework interfaces and logic; zero persistence or workflow engine dependencies
- **casehub-persistence-memory**: Lightweight in-memory implementations; used for fast unit tests and local development
- **casehub-persistence-hibernate**: Production-grade JPA/Panache implementations; workspace stored as JSON TEXT blob
- **casehub-examples**: Demonstrates core functionality using the in-memory persistence module
- **casehub-flow-worker**: Optional module with isolated Quarkus Flow dependencies
- Future modules (e.g., `casehub-temporal-worker`, `casehub-camunda-worker`) can be added without affecting core

**Benefits:**
- Users can use CaseHub without workflow engines (just `casehub-core` + a persistence module)
- Multiple workflow engines supported via separate modules
- Core framework remains lightweight and dependency-free
- Each persistence and workflow module maintains version compatibility independently

---

## 1. Problem Statement

Current agentic AI platforms lack a standalone, lightweight case management architecture solution that:
- Provides a **shared workspace** where multiple AI agents can collaboratively build solutions
- Enables **data-driven activation** — agents respond to state changes, not explicit assignments
- Supports both collaborative problem solving and simple task coordination
- Integrates naturally with Quarkus ecosystem
- Avoids the overhead of full agentic AI platform dependencies

**Market Research:** Initial research (Grok, Claude) found no suitable standalone Java/Quarkus solutions for building new agentic platforms with case management architecture.

---

## 2. Goals and Non-Goals

### Goals
1. **True Case Management Semantics**: Shared workspace with data-driven task definition activation
2. **Dual Execution Model**: CaseFile model (collaborative) + Task model (request-response)
3. **Quarkus Integration**: First-class Quarkus support with minimal dependencies
4. **Pluggable Storage**: SPI-based storage abstraction (Redis as primary target)
5. **Asynchronous Coordination**: Support decoupling between requestors and task definitions
6. **Incremental Evolution**: Architecture that supports future enhancements without major rewrites, including modular support for multiple workflow engines
7. **Terminology Alignment**: Consistency with CMMN, CNCF OWL, Quarkus Flow, and classic blackboard literature where applicable

### Non-Goals (Deferred to Future Iterations)
- Complex case worker selection strategies (beyond simple round-robin/random)
- Job bidding/contract net protocols (FIPA)
- Nested task hierarchies and sub-slots
- Progress notifications and partial result streaming
- Advanced merge strategies for multi-case-worker results
- Policy governance and execution constraints
- Sophisticated retry/timeout policies

---

## 3. Case Management Core

The case management core implements the foundational pattern: a **shared workspace** where independent **task definitions** read and write data, activated automatically when their preconditions are met.

### 3.1 The CaseFile (Shared Workspace)

The **CaseFile** is the defining element of the case management architecture. It is a shared, key-value data space where:
- A requestor provides **initial state** (e.g., raw documents)
- Multiple task definitions **read** what they need and **write** their contributions
- The solution **emerges** incrementally from independent contributions
- The case file is **complete** when no more task definitions can contribute or a terminal condition is met

```
CaseFile: "analyze-case-42"
  ├─ raw_documents: [doc1.pdf, doc2.pdf]          ← Requestor writes initial state
  ├─ extracted_text: {doc1: "...", doc2: "..."}    ← OCR task definition contributes
  ├─ entities: [{name: "Acme", type: "company"}]   ← NER task definition contributes
  ├─ sentiment: {overall: "negative"}              ← Sentiment task definition contributes
  └─ case_summary: "..."                           ← Summary task definition reads ALL above, contributes
```

Each task definition works independently. No task definition needs to know about the others. The CaseEngine orchestrates activation.

#### CaseFile Interface

```java
/**
 * The shared workspace at the heart of the Blackboard Architecture.
 * Each CaseFile has a Long primary key (for database storage and optimistic locking),
 * a UUID for OpenTelemetry trace correlation, and direct references to its parent
 * and children — forming the POJO object graph.
 */
public interface CaseFile {

    // Identity
    Long getId();
    Long getVersion();
    UUID getOtelTraceId();
    String getCaseType();

    // Read shared workspace state
    <T> Optional<T> get(String key, Class<T> type);
    boolean contains(String key);
    Set<String> keys();
    Map<String, Object> snapshot();

    // Write contributions (triggers change events)
    void put(String key, Object value);
    void putIfAbsent(String key, Object value);

    // Optimistic concurrency (fine-grained, per-key)
    void putIfVersion(String key, Object value, long expectedVersion) throws StaleVersionException;
    long getKeyVersion(String key);

    // Change listeners (in-memory only; not persisted)
    void onChange(String key, Consumer<CaseFileItemEvent> listener);
    void onAnyChange(Consumer<CaseFileItemEvent> listener);

    // Context propagation (tracing + budget)
    PropagationContext getPropagationContext();

    // Graph relationships — navigate parent/child hierarchy directly on the POJO
    Optional<CaseFile> getParentCase();
    List<CaseFile> getChildCases();
    List<Task> getTasks();

    // Lifecycle
    CaseStatus getStatus();
    void setStatus(CaseStatus status);
    Instant getCreatedAt();
    void complete();
    void fail(ErrorInfo error);
}
```

**Identity design note:** `getId()` returns a `Long` primary key used for database storage and keying internal maps. `getOtelTraceId()` returns a UUID shared across the entire parent/child hierarchy, enabling W3C-compatible distributed tracing without a separate `spanId` field — structural relationships are carried by the POJO graph (`getParentCase()` / `getChildCases()`) rather than embedded in the context.

#### CaseStatus

```java
public enum CaseStatus {
    PENDING,     // CaseFile created, not yet solving
    RUNNING,     // Control loop running, task definitions being activated
    WAITING,     // No more task definitions can fire; awaiting explicit completion or timeout
    SUSPENDED,   // Temporarily paused
    COMPLETED,   // Successfully solved
    FAULTED,     // Error during solving (distinguished via ErrorInfo)
    CANCELLED    // Explicitly cancelled
}
```

#### CaseFile Lifecycle

```
                                          ┌──── task def activates ──┐
                                          ▼                          │
[PENDING] ──solve──> [RUNNING] ──────> [RUNNING] ──────> [WAITING]
                        │                                    │
                        │              ┌─────────────────────┼────────────────┐
                        │              ▼                     ▼                ▼
                        │        [COMPLETED]            [FAULTED]        [FAULTED]
                        │         (explicit              (task def        (timeout,
                        │          or auto)               error)       via ErrorInfo)
                        ▼
                   [CANCELLED]
```

**State Transitions:**
- **PENDING → RUNNING**: `CaseEngine.solve()` called; control loop begins
- **RUNNING → RUNNING**: Task definition writes to case file, triggering re-evaluation of preconditions
- **RUNNING → WAITING**: No eligible task definitions and none in-flight; awaiting explicit completion
- **WAITING → COMPLETED**: CaseFile explicitly completed, or all known producedKeys are present
- **RUNNING → FAULTED**: Task definition throws unrecoverable exception after retries exhausted
- **RUNNING/WAITING → FAULTED**: CaseFile exceeds configured timeout (enforced by TimeoutEnforcer — see §7.2); distinguished via ErrorInfo
- **Any non-terminal → CANCELLED**: Explicit cancellation requested (cascades to children)

**Auto-Completion (MVP)**: CaseFile automatically completes when no more task definitions can fire and none are in-flight (including WAITING PlanItems). Requestor can also call `caseFile.complete()` explicitly.

### 3.2 Task Definitions (Specialists)

A **TaskDefinition** is an independent specialist that:
- Declares **entry criteria**: what keys must exist on the case file before it can activate
- Declares **produced keys**: what it will contribute to the case file
- **Reads** from the case file, performs work, and **writes** results back
- Is **activated opportunistically** by the CaseEngine when entry criteria are satisfied

```java
public interface TaskDefinition {
    /** Unique identifier for this task definition */
    String getId();

    /** Human-readable name */
    String getName();

    /**
     * Entry criteria: keys that must exist on the case file before this task definition can activate.
     * CaseEngine evaluates this on every case file change.
     */
    Set<String> entryCriteria();

    /**
     * Keys this task definition will produce. Used to:
     * - Detect circular dependencies at registration time
     * - Track provenance of contributions
     * - Determine when no more progress is possible
     */
    Set<String> producedKeys();

    /**
     * Optional: fine-grained activation check beyond key presence.
     * Called only if entryCriteria() are all present.
     * Default returns true.
     */
    default boolean canActivate(CaseFile caseFile) {
        return true;
    }

    /**
     * Execute and contribute results to the case file.
     * Called by CaseEngine when entry criteria are met.
     * Must write produced keys to the case file before returning.
     */
    void execute(CaseFile caseFile);

    /**
     * Maximum time this task definition is allowed to execute.
     * TimeoutEnforcer interrupts execution if exceeded (see §7.2).
     * Default: 5 minutes.
     */
    default Duration getExecutionTimeout() {
        return Duration.ofMinutes(5);
    }

    /**
     * Retry policy for this task definition (see §7.3).
     * Default: 3 attempts with exponential backoff.
     */
    default RetryPolicy getRetryPolicy() {
        return RetryPolicy.defaults();
    }
}
```

#### TaskDefinitionRegistry

Registration and lookup of task definitions.

```java
@ApplicationScoped
public class TaskDefinitionRegistry {
    // Domain Task Definitions
    public void register(TaskDefinition td, Set<String> caseTypes)
        throws CircularDependencyException;
    public void unregister(String tdId);
    public List<TaskDefinition> getForCaseType(String caseType);
    public Optional<TaskDefinition> getById(String tdId);

    // Planning Strategies (see §6.2)
    public void registerStrategy(PlanningStrategy ps, Set<String> caseTypes);
    public void unregisterStrategy(String psId);
    public List<PlanningStrategy> getStrategiesForCaseType(String caseType);
}
```

**Circular Dependency Detection**: Validated at registration time. Example: TD-A requires "x", produces "y"; TD-B requires "y", produces "x" → valid chain, not circular. Circular: mutual dependency where neither can ever fire → rejected with `CircularDependencyException`.

### 3.3 Listener Evaluator

Entry criteria evaluation and PlanItem creation.

```java
@ApplicationScoped
public class ListenerEvaluator {

    /**
     * Evaluate which task definitions are newly eligible given current case file state.
     * Creates PlanItems for each eligible task definition and adds them to the CasePlanModel agenda.
     *
     * @param domainCaseFile   The domain case file to evaluate entry criteria against
     * @param casePlanModel    The case plan model to add PlanItems to
     * @param registered       All registered domain task definitions for this case type
     * @param triggerKey       The case file key that changed (null for initial evaluation)
     * @return                 Newly created PlanItems
     */
    public List<PlanItem> evaluateAndCreatePlanItems(CaseFile domainCaseFile, CasePlanModel casePlanModel,
                                              List<TaskDefinition> registered,
                                              String triggerKey);

    /**
     * Check if the case file has reached quiescence (no more progress possible).
     * True when: agenda is empty AND no PlanItems are RUNNING.
     */
    public boolean isQuiescent(CasePlanModel casePlanModel);
}
```

- On each case file change, evaluates which domain task definition entry criteria are now satisfied
- Creates PlanItems (see §6.3) for newly eligible task definitions and adds them to the CasePlanModel's agenda
- Filters out task definitions that have already contributed (unless re-activation is enabled)
- Skips quarantined task definitions (see §7.4 Poison Pill Protection)
- Detects quiescence: no PlanItems on agenda and none in-flight

### 3.4 Storage SPI

Pluggable repository abstractions for both CaseFile and Task models. The SPIs are defined in `casehub-core`; implementations live in separate persistence modules.

#### CaseFileRepository

Lifecycle management for CaseFile instances, in `casehub-core/src/main/java/io/casehub/core/spi/`.

```java
/**
 * SPI for creating and retrieving CaseFiles.
 *
 * Implementations:
 * - casehub-persistence-memory: InMemoryCaseFileRepository (no external deps, fast tests)
 * - casehub-persistence-hibernate: HibernateCaseFileRepository (JPA/Panache, production)
 */
public interface CaseFileRepository {

    /** Create a root CaseFile (no parent). */
    CaseFile create(String caseType, Map<String, Object> initialState,
                    PropagationContext propagationContext);

    /** Create a child CaseFile attached to a parent. */
    CaseFile createChild(String caseType, Map<String, Object> initialState, CaseFile parent);

    Optional<CaseFile> findById(Long id);

    List<CaseFile> findByStatus(CaseStatus status);

    /** Persist any state changes to the CaseFile (no-op for in-memory). */
    void save(CaseFile caseFile);

    void delete(Long id);
}
```

#### TaskRepository

Lifecycle management for Task instances, in `casehub-core/src/main/java/io/casehub/worker/`.

```java
/**
 * SPI for creating and retrieving Tasks.
 *
 * Implementations:
 * - casehub-persistence-memory: InMemoryTaskRepository
 * - casehub-persistence-hibernate: HibernateTaskRepository
 */
public interface TaskRepository {

    Task create(String taskType, Map<String, Object> context,
                Set<String> requiredCapabilities, PropagationContext propagationContext,
                CaseFile owningCase);

    Task createAutonomous(String taskType, Map<String, Object> context,
                          String assignedWorkerId, CaseFile owningCase,
                          PropagationContext propagationContext);

    Optional<Task> findById(Long id);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByWorker(String workerId);

    /** Persist state changes (no-op for in-memory). */
    void save(Task task);

    void delete(Long id);
}
```

#### Concurrency Model

- Thread-safe using `ConcurrentHashMap` (in-memory implementation)
- CaseFile key writes trigger CDI change events before returning
- Atomic status transitions using synchronized blocks
- Future implementations must provide equivalent concurrency guarantees

**`casehub-persistence-memory`**: `InMemoryCaseFile`, `InMemoryTask`, `InMemoryCaseFileRepository`, `InMemoryTaskRepository`. Zero external dependencies; used for fast unit tests and local development.

**`casehub-persistence-hibernate`**: `HibernateCaseFile`, `HibernateTask`, `HibernateCaseFileRepository`, `HibernateTaskRepository`. JPA/Panache entities; workspace stored as JSON TEXT blob; H2 for tests, PostgreSQL for production.

**Future**: Redis, Cassandra backends via additional persistence modules.

### 3.5 Information Slots

Key-value storage concept that manifests differently in each model:

**CaseFile Model**: The CaseFile itself **is** the information slot — a shared workspace that task definitions read from and write to directly via `CaseFile.get()` and `CaseFile.put()`. This is the canonical case management pattern.

**Task Model**: Slots are an **internal implementation detail** of TaskRegistry. Accessed only through `TaskRequest.context` (input) and `TaskResult.data` (output).

**Storage Model (both models):**
- Lifecycle managed by the owning component (CaseFileRegistry or TaskRegistry)
- Cleanup on completion, cancellation, error, or timeout (configurable TTL)
- Thread-safe concurrent access via ConcurrentHashMap

---

## 4. Workers

Workers are the execution units — both the **case workers** that perform work and the **task model** that coordinates simple request-response interactions.

### 4.1 Worker Registry

Used by both CaseFile model (task definitions may delegate to case workers) and Task model.

```java
@ApplicationScoped
public class WorkerRegistry {

    /**
     * Register case worker with capabilities and obtain case worker ID.
     * Capabilities used for task matching (e.g., ["sentiment-analysis", "java-17"]).
     * Requires API key for authentication (configured in application.properties).
     */
    public String register(String caseWorkerName, Set<String> capabilities, String apiKey)
        throws UnauthorizedException;

    /**
     * Unregister case worker and mark all assigned tasks as FAULTED.
     */
    public void unregister(String workerId);

    /**
     * Heartbeat to indicate case worker is still alive (30s timeout).
     * Must be called at least every 20s to avoid timeout.
     */
    public void heartbeat(String workerId);

    /**
     * Poll for next available task matching case worker capabilities.
     * Returns empty if no tasks available.
     * Marks task as ASSIGNED and transitions to RUNNING when claimed.
     */
    public Optional<Task> claimTask(String workerId);

    /**
     * Submit successful result.
     * Task marked as COMPLETED and result stored for requestor retrieval.
     */
    public void submitResult(String workerId, String taskId, TaskResult result)
        throws UnauthorizedException;

    /**
     * Report execution failure with structured error information.
     * Task marked as FAULTED with error details.
     */
    public void reportFailure(String workerId, String taskId, ErrorInfo error)
        throws UnauthorizedException;
}
```

### 4.2 Task Model (Request-Response)

For simpler request-response work that doesn't require collaborative problem solving.

#### TaskBroker

Orchestration and requestor-facing API for simple request-response tasks.

```java
@ApplicationScoped
public class TaskBroker {

    /**
     * Submit task and get handle immediately (non-blocking).
     * Task enters PENDING state and is queued for case worker selection.
     */
    public TaskHandle submitTask(TaskRequest request) throws TaskSubmissionException;

    /**
     * Submit task with explicit timeout.
     * If no case worker completes within timeout, task marked as FAULTED with timeout ErrorInfo.
     */
    public TaskHandle submitTask(TaskRequest request, Duration timeout) throws TaskSubmissionException;

    /**
     * Cancel a running or pending task.
     * Returns true if cancellation successful, false if already completed/faulted.
     */
    public boolean cancelTask(TaskHandle handle);

    /**
     * Block until result available (uses TaskHandle.awaitResult internally).
     * Throws TimeoutException if task exceeds configured timeout.
     */
    public TaskResult awaitResult(TaskHandle handle, Duration timeout)
        throws InterruptedException, TimeoutException;

    /**
     * Query task status without blocking.
     */
    public TaskStatus getStatus(TaskHandle handle);
}
```

#### Task Data Structures

```java
public class TaskRequest {
    private String taskType;                      // e.g., "sentiment-analysis"
    private Map<String, Object> context;          // Input data (JSON-serializable)
    private Set<String> requiredCapabilities;     // Worker requirements
    private Optional<Duration> timeout;           // Time limit (default: 5 minutes)
    private Optional<String> idempotencyKey;      // For duplicate detection
    private PropagationContext propagationContext; // Context propagation (see §5.2)
    private RetryPolicy retryPolicy;              // Default: RetryPolicy.defaults()

    public static TaskRequestBuilder builder() { ... }
}

/**
 * A concrete, executable work unit in the request-response model.
 * Task is an interface; DefaultTask is the in-core interim implementation.
 * Persistence modules provide HibernateTask (JPA entity) and InMemoryTask.
 */
public interface Task {

    // Identity
    Long getId();
    Long getVersion();
    UUID getOtelSpanId();

    // Task data
    String getTaskType();
    Map<String, Object> getContext();
    Set<String> getRequiredCapabilities();

    // Lifecycle
    TaskStatus getStatus();
    void setStatus(TaskStatus status);
    Instant getSubmittedAt();
    Optional<String> getAssignedWorkerId();
    void setAssignedWorkerId(String workerId);
    Optional<Instant> getAssignedAt();
    TaskOrigin getTaskOrigin();

    // Context propagation
    PropagationContext getPropagationContext();

    // Graph relationships
    Optional<CaseFile> getOwningCase();
    List<Task> getChildTasks();
}

public class TaskResult {
    private String taskId;
    private TaskStatus status;
    private Map<String, Object> data;             // Output data (JSON-serializable)
    private Optional<ErrorInfo> error;            // Present only if FAULTED
    private Instant completedAt;
    private Optional<String> workerId;        // Which case worker produced result

    public static class ErrorInfo {
        private String errorCode;                 // e.g., "CASEWORKER_TIMEOUT", "VALIDATION_FAILED"
        private String message;                   // Human-readable description
        private boolean retryable;                // Should requestor retry?
        private Instant timestamp;
        private Optional<String> workerId;    // Which case worker failed
        private Optional<String> stackTrace;      // For debugging (optional)
    }
}
```

#### TaskHandle

Opaque identifier for submitted tasks, returned immediately to requestor.

```java
public interface TaskHandle {
    // Identification
    String getTaskId();

    // Status checking (non-blocking)
    TaskStatus getStatus();

    // Result retrieval
    CompletableFuture<TaskResult> getResultAsync();  // Cached, idempotent
    TaskResult awaitResult(Duration timeout) throws TimeoutException, InterruptedException;

    // Cancellation
    boolean cancel();
    boolean isCancelled();

    // Metadata
    Instant getSubmittedAt();
    Optional<String> getAssignedWorker();
}
```

#### TaskStatus

```java
public enum TaskStatus {
    PENDING,         // Submitted, awaiting case worker
    ASSIGNED,        // Matched to case worker
    RUNNING,         // Worker processing
    WAITING,         // Failed, awaiting retry after backoff
    SUSPENDED,       // Temporarily paused
    COMPLETED,       // Successfully finished
    FAULTED,         // Retries exhausted (distinguished via ErrorInfo for timeout vs error)
    CANCELLED        // Explicitly cancelled
}
```

### 4.3 Autonomous Workers

In addition to the traditional **broker-allocated** pattern (TaskBroker creates task → TaskScheduler assigns worker), CaseHub supports **autonomous/decentralized** workers that work on their own agency.

#### Autonomous Worker Pattern

Autonomous workers:
- Monitor external systems (APIs, message queues, databases, file systems)
- Decide independently when work is needed based on observed conditions
- Notify the system when starting work via `WorkerRegistry.notifyAutonomousWork()`
- Perform work and submit results
- Fully integrate with PropagationContext for lineage tracking
- Can spawn sub-workers that become children in the execution hierarchy

**Use Cases:**
- Event-driven workflows: Monitor message queue, trigger case analysis when events arrive
- Scheduled analysis: Periodic scans that spawn case-specific work
- Threshold monitoring: Watch metrics/KPIs, trigger alerts or analysis when thresholds exceeded
- Multi-agent collaboration: Agents observe shared CaseFile, contribute autonomously when they can add value

#### TaskOrigin Enum

```java
public enum TaskOrigin {
    BROKER_ALLOCATED,  // Traditional: TaskBroker creates, scheduler assigns
    AUTONOMOUS         // Worker-initiated: Worker self-selects and notifies
}
```

#### Autonomous Task Creation API

```java
@ApplicationScoped
public class WorkerRegistry {
    /**
     * Register autonomous task initiated by a decentralized worker.
     * Creates Task with AUTONOMOUS origin and links to PropagationContext.
     * Task immediately ASSIGNED (worker already owns it).
     *
     * @param workerId Worker's unique identifier
     * @param taskType Type of task being performed
     * @param context Task context/parameters
     * @param caseFileId Optional - associate work with a case
     * @param parentContext Optional - parent PropagationContext for lineage
     * @return Created Task with AUTONOMOUS origin
     */
    public Task notifyAutonomousWork(
        String workerId,
        String taskType,
        Map<String, Object> context,
        String caseFileId,
        PropagationContext parentContext
    ) throws UnauthorizedException;
}
```

#### Extended Task Fields

The `Task` interface carries `TaskOrigin` and a graph reference to its owning `CaseFile`:

```java
public interface Task {
    // ... other fields ...
    TaskOrigin getTaskOrigin();             // BROKER_ALLOCATED or AUTONOMOUS
    Optional<CaseFile> getOwningCase();     // Case associated with this task (if any)
    List<Task> getChildTasks();             // Sub-tasks spawned during execution
}
```

#### Autonomous Worker Lifecycle

```
1. Worker monitors external system (API, queue, etc.)
2. Worker detects condition requiring work
3. Worker calls WorkerRegistry.notifyAutonomousWork()
   → System creates Task with AUTONOMOUS origin
   → Task immediately in ASSIGNED state
   → PropagationContext created/propagated for lineage
   → If caseFileId provided, task associated with case
4. Worker performs work
5. Worker submits result via WorkerRegistry.submitResult()
6. Optional: Worker spawns sub-workers by passing parent PropagationContext
```

**Key Difference from Broker-Allocated:**
- No TaskBroker or TaskScheduler involvement
- Worker decides WHAT to work on (not system allocation)
- Task creation is worker-initiated (not requestor-initiated)
- Still fully tracked and observable via TaskRegistry
- Full PropagationContext lineage support
- Can be part of hierarchical execution (parent/child tasks)

**Example:**

```java
// Autonomous monitoring worker
public class FraudMonitoringWorker implements Runnable {
    public void run() {
        while (running) {
            // 1. Monitor external system
            List<Transaction> suspicious = pollApi("https://api.example.com/transactions");

            for (Transaction txn : suspicious) {
                if (txn.fraudScore > THRESHOLD) {
                    // 2. Autonomously decide work is needed
                    // 3. Notify system
                    Task task = workerRegistry.notifyAutonomousWork(
                        workerId,
                        "fraud-analysis",
                        Map.of("transaction", txn),
                        "fraud-case-" + txn.id
                    );

                    // 4. Perform work
                    Map<String, Object> analysis = analyzeFraud(txn);

                    // 5. Submit result (task.getId() is Long; TaskResult takes String)
                    String taskIdStr = task.getId().toString();
                    TaskResult result = TaskResult.success(taskIdStr, analysis);
                    workerRegistry.submitResult(workerId, taskIdStr, result);
                }
            }
        }
    }
}
```

**See:** `casehub/src/main/java/io/casehub/examples/workers/AutonomousMonitoringWorker.java` for complete implementation.

### 4.4 Task Lifecycle

```
[PENDING] ──select──> [ASSIGNED] ──execute──> [RUNNING]
                                                    │
                          ┌─────────────────────────┼────────────────┐
                          ▼                         │                ▼
                    [COMPLETED]                     ▼           [FAULTED]
                                              [WAITING]       (timeout, via
                                              │         │      ErrorInfo)
                                     retry ◀──┘         └──▶ retries exhausted
                                      ▼                           ▼
                                  [PENDING]                   [FAULTED]
                                                                  │
                                                                  ▼
                                                          [DEAD LETTER]

[CANCELLED] (can occur from PENDING, ASSIGNED, RUNNING, or WAITING)
```

#### Task Model Components

- **TaskBroker**: Orchestration entry point — delegates to TaskRegistry for storage and TaskScheduler for routing
- **TaskRegistry**: Task storage and lifecycle management — stores metadata, manages state transitions, handles cleanup
- **TaskScheduler**: Task-to-case-worker matching — selects available case worker, marks task as ASSIGNED

### 4.5 Worker Selection Strategy

```java
public interface WorkerSelectionStrategy {
    Optional<Worker> selectWorker(Task task, List<Worker> availableWorkers);
}
```

**MVP Implementation**: Simple round-robin or random selection
**Future**: Capability matching, load balancing, bidding protocols

### 4.6 Cleanup Policy

**CaseFile Model:**
- **On COMPLETED**: CaseFile state retained for configurable TTL (default: 1 hour) for result retrieval
- **On FAULTED**: CaseFile state retained for TTL for debugging, then deleted
- **On CANCELLED**: CaseFile state deleted after brief grace period (5 minutes)

**Task Model:**
- **On COMPLETED**: Delete slot after result retrieved by requestor
- **On FAULTED**: Delete slot after error notification
- **On CANCELLED**: Delete slot immediately

**Future**: Configurable retention policies, audit logging

### 4.6 Notification Service

Event dispatch for both models.

- Publishes case file item events (key added/updated)
- Publishes task lifecycle events
- Notifies requestors of case file completion or task result
- **MVP Implementation**: Quarkus CDI Event Bus

**Serialization (MVP Decision)**:
- **Jackson JSON** for all `Map<String, Object>` serialization
- Quarkus default, handles complex types well
- Context and data must contain only JSON-serializable types
- Custom objects require `@RegisterForReflection` for native builds
- Future: Consider Protocol Buffers for high-throughput scenarios (Phase 4)

---

## 5. Coordination

Coordination is the **mechanism** layer — how work flows through the system, how parent-child relationships are maintained, and how the control loop orchestrates task definition execution.

### 5.1 CaseEngine (Scheduler)

The **CaseEngine** is the execution engine — it runs the control loop but defers *control decisions* to the CasePlanModel and its Planning Strategies (see §6):

1. Detect domain CaseFile change
2. Evaluate which domain task definition entry criteria are now satisfied → create PlanItems
3. **Invoke Planning Strategies** to reason about focus, priority, and strategy → Planning Strategies update CasePlanModel
4. Read the agenda from CasePlanModel, select highest-priority PlanItem(s)
5. Activate the selected domain task definition
6. Domain task definition reads case file, contributes, writes back → triggers step 1
7. Repeat until case file is complete, waiting, or timed out

#### Requestor API

```java
@ApplicationScoped
public class CaseEngine {

    /**
     * Create a case file with initial state and begin solving.
     * Injects CaseFileRepository to create the CaseFile (not DefaultCaseFile directly).
     * Case type determines which domain and planning strategies are eligible.
     * A paired CasePlanModel is created automatically.
     * Returns immediately; solving runs asynchronously on an internal executor.
     * Internal maps are keyed by Long (the CaseFile's database primary key).
     */
    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState)
        throws CaseCreationException;

    /**
     * Create a case file with initial state and timeout.
     * CaseFile marked as FAULTED (with timeout ErrorInfo) if not completed within duration.
     */
    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState,
                                 Duration timeout) throws CaseCreationException;

    /**
     * Wait for a case file to reach a terminal state (COMPLETED, FAULTED).
     */
    public CaseFile awaitCompletion(CaseFile caseFile, Duration timeout)
        throws InterruptedException, TimeoutException;

    /**
     * Create a child case file from within a TaskDefinition.
     * Delegates to CaseFileRepository.createChild(), which sets the parent reference.
     * PropagationContext is automatically inherited from the parent case file.
     * Child deadline is capped at parent's remaining budget.
     */
    public CaseFile createChildCaseFile(CaseFile parentCaseFile, String caseType,
                                   Map<String, Object> initialState);

    /**
     * Cancel a running case file. All in-flight task definition executions and child case files are cancelled.
     * Cancellation propagates down the entire hierarchy.
     */
    public boolean cancel(CaseFile caseFile);

    /**
     * Get a read-only snapshot of the domain case file's current state.
     */
    public Map<String, Object> getSnapshot(CaseFile caseFile);

    /**
     * Access the case plan model for inspection (current strategy, focus, agenda).
     * Useful for debugging and monitoring control decisions.
     */
    public CasePlanModel getCasePlanModel(CaseFile caseFile);

    /** @PreDestroy shuts down the internal executor cleanly. */
    public void shutdown();
}
```

**Implementation notes:** The internal `activeCaseFiles`, `casePlanModels`, and `caseFileFutures` maps are all keyed by `Long` (the CaseFile's primary key, not a String UUID). After a case completes or is cancelled the maps are cleaned up to prevent memory accumulation in long-running deployments. Lineage traversal is done directly on the POJO graph (`caseFile.getParentCase()`, `caseFile.getChildCases()`) — there is no separate `LineageService` or `getLineage()` API on CaseEngine.

### 5.2 Context Propagation

When a CaseFile spawns child CaseFiles, or when a TaskDefinition submits Tasks that themselves spawn sub-Tasks, **context must propagate** through the entire hierarchy. Without this, it's impossible to:
- Trace a leaf-level result back to the originating Goal
- Understand why a particular sub-Task was created
- Aggregate results across a hierarchy of related work
- Apply consistent timeouts, security policies, or resource budgets across a chain

#### PropagationContext

Every CaseFile and Task carries a **PropagationContext** — an immutable, hierarchical context that flows from parent to child:

```
Goal: "Analyze legal case #42"                          ← PropagationContext (root)
  └─ CaseFile: "legal-case-analysis"                    ← inherits root context
       ├─ TD: OCR extracts text                         ← inherits case file context
       │    └─ Task: "ocr-page-1"                       ← inherits TD context
       │    └─ Task: "ocr-page-2"                       ← inherits TD context
       ├─ TD: NER extracts entities                     ← inherits case file context
       │    └─ CaseFile: "entity-disambiguation"        ← child case file, inherits parent context
       │         ├─ TD: WikidataLookup                  ← inherits child case file context
       │         └─ TD: ContextualDisambiguation        ← inherits child case file context
       └─ TD: Summary writes case_summary              ← inherits case file context
```

The PropagationContext carries:
- **Trace ID**: W3C-compatible trace ID shared across the entire hierarchy (root to leaf), enabling distributed tracing
- **Inherited Attributes**: Key-value pairs that flow from parent to child (e.g., security principal, tenant ID)
- **Resource Budget**: Remaining time/compute budget, decremented as it propagates down

**Removed (POJO graph refactor):** `spanId`, `parentSpanId`, `lineagePath`, `isRoot()`, `getDepth()`, `getCreatedAt()`. Structural relationships are carried by the POJO graph (`CaseFile.getParentCase()` / `getChildCases()`, `Task.getOwningCase()` / `getChildTasks()`). The OTel trace ID for a CaseFile lives on `CaseFile.getOtelTraceId()` (UUID), and per-Task on `Task.getOtelSpanId()`.

```java
/**
 * Immutable tracing and budget context that flows from parent to child.
 * Carries a W3C-compatible trace ID (shared across entire hierarchy),
 * inherited attributes (tenantId, userId, etc.), and optional resource budget.
 */
public class PropagationContext {

    // Tracing — single ID shared root-to-leaf
    private final String traceId;

    // Inherited attributes — flow from parent to child automatically
    private final Map<String, String> inheritedAttributes;  // e.g., tenantId, userId

    // Resource budget — enforced down the hierarchy
    private final Instant deadline;        // null = no deadline (absolute, inherited)
    private final Duration remainingBudget; // null = no budget (decremented per level)

    // Factory methods
    public static PropagationContext createRoot();
    public static PropagationContext createRoot(Map<String, String> attributes);
    public static PropagationContext createRoot(Map<String, String> attributes, Duration budget);

    /**
     * Reconstructs a PropagationContext from storage.
     * Used by Hibernate persistence to restore context from entity fields.
     */
    public static PropagationContext fromStorage(String traceId, Map<String, String> attributes,
                                                  Instant deadline, Duration remainingBudget);

    /**
     * Create a child context inheriting from this parent.
     * - traceId: inherited (same across entire hierarchy)
     * - inheritedAttributes: merged (child can add, not remove)
     * - deadline: inherited (never extended)
     * - remainingBudget: decremented by elapsed time since parent started
     */
    public PropagationContext createChild();
    public PropagationContext createChild(Map<String, String> additionalAttributes);

    // Queries
    public boolean isBudgetExhausted();        // True if deadline passed or budget <= 0
    public Optional<String> getAttribute(String key);
    public String getTraceId();
    public Map<String, String> getInheritedAttributes();
    public Optional<Instant> getDeadline();
    public Optional<Duration> getRemainingBudget();
}
```

#### Context Inheritance Rules

1. **Automatic inheritance**: Trace ID, security principal, tenant ID, deadline/timeout budget
2. **Explicit opt-in**: Domain-specific attributes set by the parent (e.g., `case_id`, `priority_override`)
3. **Never inherited**: Internal state, mutable data, implementation details
4. **Budget propagation**: Parent's remaining timeout is the *maximum* a child can use; children cannot exceed their parent's budget

#### Propagation Rules

**1. CaseFile Creation (Root)**
When a requestor creates a top-level CaseFile, a root PropagationContext is created automatically:
```java
CaseFile caseFile = caseEngine.createAndSolve("legal-case-analysis", initialState);
// caseFile.getParentCase().isEmpty() == true  (root — no parent in POJO graph)
// caseFile.getPropagationContext().getTraceId() == "abc-123" (new UUID)
// caseFile.getOtelTraceId() == UUID shared with all descendants
```

**2. TaskDefinition → Child CaseFile**
When a TaskDefinition spawns a child CaseFile during `execute()`, the child inherits context:
```java
public class EntityDisambiguationTD implements TaskDefinition {
    @Inject CaseEngine caseEngine;

    @Override
    public void execute(CaseFile parentCaseFile) {
        List<Entity> ambiguous = parentCaseFile.get("raw_entities", List.class).orElseThrow();

        // Child case file inherits parent's PropagationContext automatically
        CaseFile childCaseFile = caseEngine.createChildCaseFile(
            parentCaseFile,                    // parent — context propagated
            "entity-disambiguation",           // case type
            Map.of("ambiguous_entities", ambiguous)  // initial state
        );

        CaseFile resolved = caseEngine.awaitCompletion(childCaseFile, Duration.ofMinutes(2));
        parentCaseFile.put("entities", resolved.snapshot().get("resolved_entities"));
    }
}
// childCaseFile.getParentCase().get() == parentCaseFile  (POJO graph link)
// childCaseFile.getPropagationContext().getTraceId() == parentCaseFile traceId (same!)
// parentCaseFile.getChildCases().contains(childCaseFile) == true
```

**3. TaskDefinition → Task**
When a TaskDefinition submits a Task (using the Task model for simple work), context propagates:
```java
public class OcrTaskDefinition implements TaskDefinition {
    @Inject TaskBroker taskBroker;

    @Override
    public void execute(CaseFile caseFile) {
        List<Document> docs = caseFile.get("raw_documents", List.class).orElseThrow();
        List<String> results = new ArrayList<>();

        for (Document doc : docs) {
            // Task inherits case file's PropagationContext
            TaskHandle handle = taskBroker.submitTask(
                TaskRequest.builder()
                    .taskType("ocr")
                    .context(Map.of("document", doc))
                    .propagationContext(caseFile.getPropagationContext().createChild())
                    .build()
            );
            results.add(taskBroker.awaitResult(handle, Duration.ofMinutes(1))
                .getData().get("text").toString());
        }
        caseFile.put("extracted_text", results);
    }
}
```

**4. Worker → Sub-Task**
When a Worker processing a Task submits sub-Tasks, context flows automatically:
```java
public class OcrWorker {
    @Inject TaskBroker taskBroker;

    public TaskResult execute(Task parentTask) {
        // Sub-task inherits parent's context
        TaskHandle subTask = taskBroker.submitTask(
            TaskRequest.builder()
                .taskType("image-preprocessing")
                .context(Map.of("page", parentTask.getContext().get("page")))
                .propagationContext(parentTask.getPropagationContext().createChild())
                .build()
        );
        // ...
    }
}
```

**5. Budget Enforcement**
Child contexts cannot exceed their parent's remaining budget:
```java
// Root case file with 10-minute deadline
CaseFile root = caseEngine.createAndSolve("analysis", state, Duration.ofMinutes(10));
// root.getPropagationContext().getDeadline() == now + 10min

// 3 minutes later, task definition spawns child case file
// childContext.getRemainingBudget() == 7min (automatically decremented)
// childContext.getDeadline() == same absolute deadline as root

// Attempting to set a child deadline beyond parent's is silently capped
```

### 5.3 Lineage & Hierarchy

#### POJO Graph Traversal

The `LineageService`, `LineageNode`, and `LineageTree` classes have been removed. Lineage is now traversed directly on the POJO object graph:

```java
// Navigate up — find the root of a hierarchy
CaseFile current = childCaseFile;
while (current.getParentCase().isPresent()) {
    current = current.getParentCase().get();
}
CaseFile root = current;

// Navigate down — get all child cases
List<CaseFile> children = parentCaseFile.getChildCases();

// Tasks associated with a case
List<Task> tasks = caseFile.getTasks();

// Sub-tasks spawned during task execution
List<Task> subTasks = task.getChildTasks();

// The case that owns a task
Optional<CaseFile> owningCase = task.getOwningCase();

// Shared trace ID across entire hierarchy (for OTel correlation)
UUID traceId = caseFile.getOtelTraceId();  // same UUID for all cases in hierarchy
UUID spanId  = task.getOtelSpanId();       // unique per task
```

This approach eliminates a separate storage layer for lineage data; the graph structure is maintained by the persistence module (`InMemoryCaseFileRepository` wires parent/child references in memory; `HibernateCaseFileRepository` uses JPA `@OneToMany`/`@ManyToOne` associations).

#### Hierarchical Cancellation

When a parent is cancelled or faults due to timeout, all descendants are cancelled (not faulted — the parent faulted, children are collateral):
1. CaseFile FAULTED (timeout) → all executing PlanItems → CANCELLED, all child case files → CANCELLED, all child tasks → CANCELLED
2. CaseFile FAULTED (timeout) → CasePlanModel agenda cleared
3. PropagationContext deadline ensures children cannot outlive parents

---

## 6. Observability and Control

This pillar combines two concerns: the **Control component** from Hayes-Roth (strategic reasoning about *how* to solve) and **observability** (logging, metrics, health checks that make the system transparent).

### 6.1 CasePlanModel (Control Workspace)

CaseHub implements the canonical blackboard architecture as formalized by Erman et al. (HEARSAY-II, 1980) and Hayes-Roth (1985), aligned with CMMN (Case Management Model and Notation) terminology. Critically, this includes an explicit **Control component** where control itself is treated as a knowledge-based problem-solving activity with its own workspace.

#### The Four Pillars of Case Management Architecture

| Pillar | Classic Name | CaseHub Component | Purpose |
|--------|-------------|---------------------|---------|
| **Domain Workspace** | Blackboard | `CaseFile` (§3.1) | Shared data space where partial solutions accumulate |
| **Specialists** | Knowledge Sources | `TaskDefinition` (§3.2) | Independent domain contributors activated by case file state |
| **Control** | Control Component | `CasePlanModel` + `PlanningStrategy` | Reasons about *how* to solve: focus, strategy, scheduling |
| **Scheduler** | Scheduler / Control Loop | `CaseEngine` (§5.1) | Executes control decisions — activates task definitions, manages lifecycle |

The key insight from Hayes-Roth is that **control is itself a problem-solving activity**. Rather than hardcoding a fixed control strategy (e.g., "activate all eligible task definitions"), the CasePlanModel allows planning strategies to reason dynamically about:
- **Focus of Attention**: Which part of the solution space deserves effort right now?
- **Strategy**: Should we pursue depth-first, breadth-first, or focus on a specific hypothesis?
- **Scheduling**: When multiple task definitions are eligible, which should run first? In parallel or sequentially?
- **Resource Allocation**: How much compute/time budget remains? Should we curtail exploration?

#### CasePlanModel Interface

The **CasePlanModel** is a separate workspace dedicated to control reasoning. While the domain CaseFile holds the evolving solution, the CasePlanModel holds the evolving *strategy* for how to solve it:

```
CasePlanModel (for case file "analyze-case-42"):
  ├─ focus: "entity-extraction"                         ← Where to direct attention
  ├─ strategy: "depth-first"                            ← Current solving approach
  ├─ agenda: [PlanItem(ner, pri=9), PlanItem(sent, pri=5)] ← Prioritized pending activations
  ├─ resource_budget: {time_remaining: 8m, ...}          ← Resource constraints
  └─ rationale: "Entities needed before summary"         ← Explanation of control decisions
```

The CasePlanModel is paired 1:1 with each domain CaseFile instance and managed by the CaseEngine.

```java
public interface CasePlanModel {
    String getCasePlanModelId();
    CaseFile getDomainCaseFile();  // The paired domain case file

    // Scheduling Agenda — prioritized PlanItems
    void addPlanItem(PlanItem planItem);
    void removePlanItem(String planItemId);
    List<PlanItem> getAgenda();                      // All pending PlanItems, priority-ordered
    List<PlanItem> getTopPlanItems(int maxCount);    // Highest priority PlanItems to execute next

    // Focus of Attention
    void setFocus(String focusArea);           // e.g., "entity-extraction", "summarization"
    Optional<String> getFocus();
    void setFocusRationale(String rationale);  // Why this focus was chosen

    // Strategy
    void setStrategy(String strategy);         // e.g., "depth-first", "breadth-first", "best-first"
    Optional<String> getStrategy();

    // Resource Tracking
    void setResourceBudget(Map<String, Object> budget);
    Map<String, Object> getResourceBudget();

    // General key-value (extensible by custom Planning Strategies)
    void put(String key, Object value);
    <T> Optional<T> get(String key, Class<T> type);
    Map<String, Object> snapshot();
}
```

### 6.2 Planning Strategies

**PlanningStrategies** reason about control — they do not contribute domain solutions. Instead, they read the domain CaseFile and/or CasePlanModel state and write **control decisions** (focus, priority, strategy) back to the CasePlanModel. The CaseEngine then executes those decisions.

Examples of planning strategies:
- **FocusSelector**: Reads case file state, determines which area of the solution space is most important
- **PriorityAssigner**: When multiple domain task definitions are eligible, assigns priorities based on urgency or expected value
- **StrategySelector**: Chooses between depth-first vs. breadth-first based on problem characteristics
- **ResourceMonitor**: Tracks time/compute budget and curtails exploration when resources are low

```java
public interface PlanningStrategy {
    /** Unique identifier */
    String getId();

    /** Human-readable name */
    String getName();

    /**
     * When should this Planning Strategy be invoked?
     * Called after PlanItems are created but before they are executed.
     */
    ControlActivationCondition getActivationCondition();

    /**
     * Reason about control and update the CasePlanModel.
     * Typical actions:
     * - Reprioritize PlanItems on the agenda
     * - Set or change focus of attention
     * - Cancel low-value PlanItems
     * - Adjust strategy based on case file state
     * - Update resource budget
     *
     * @param casePlanModel  The case plan model (read/write)
     * @param domainCaseFile The domain case file (read-only for planning strategies)
     */
    void reason(CasePlanModel casePlanModel, CaseFile domainCaseFile);

    enum ControlActivationCondition {
        ON_NEW_PLAN_ITEMS,      // When new PlanItems are added to agenda
        ON_CASE_FILE_CHANGE,    // When domain case file state changes
        ON_TASK_COMPLETION,     // When a domain task definition finishes
        ALWAYS                  // Every control cycle
    }
}
```

#### DefaultPlanningStrategy (MVP Built-in)

```java
/**
 * Default planning strategy that assigns equal priority
 * to all eligible PlanItems. Provides baseline behavior equivalent
 * to a system without explicit control.
 * Users can register additional PlanningStrategies to override or augment.
 */
@ApplicationScoped
public class DefaultPlanningStrategy implements PlanningStrategy {

    @Override
    public String getId() { return "default-control"; }

    @Override
    public String getName() { return "Default Equal-Priority Control"; }

    @Override
    public ControlActivationCondition getActivationCondition() {
        return ControlActivationCondition.ON_NEW_PLAN_ITEMS;
    }

    @Override
    public void reason(CasePlanModel casePlanModel, CaseFile domainCaseFile) {
        // Default: all PlanItems get priority 0 (equal), no focus filtering
        // This is a no-op — PlanItems keep their default priority
    }
}
```

**MVP Simplification**: The CasePlanModel is present but starts with this single built-in `DefaultPlanningStrategy` that assigns equal priority to all eligible task definitions. Users can register custom Planning Strategies to add sophisticated control strategies without changing the core architecture.

#### Custom Planning Strategy Example

```java
@ApplicationScoped
@CaseType("legal-case-analysis")
public class EntityFirstPlanningStrategy implements PlanningStrategy {

    @Override
    public String getId() { return "entity-first-control"; }

    @Override
    public String getName() { return "Prioritize Entity Extraction"; }

    @Override
    public ControlActivationCondition getActivationCondition() {
        return ControlActivationCondition.ON_NEW_PLAN_ITEMS;
    }

    @Override
    public void reason(CasePlanModel casePlanModel, CaseFile domainCaseFile) {
        // Strategy: prioritize entity extraction before summarization
        for (PlanItem planItem : casePlanModel.getAgenda()) {
            if (planItem.getTaskDefinitionId().equals("ner-extractor")) {
                planItem.setPriority(10);  // High priority
            } else if (planItem.getTaskDefinitionId().equals("summarizer")) {
                // Don't summarize until entities are available
                if (!domainCaseFile.contains("entities")) {
                    planItem.setPriority(-1);  // Deprioritize
                }
            }
        }
        casePlanModel.setFocus("entity-extraction");
        casePlanModel.setFocusRationale("Entities needed before downstream analysis");
    }
}
```

### 6.3 Scheduling Agenda (PlanItems)

The **Scheduling Agenda** is a prioritized queue of **PlanItems** maintained on the CasePlanModel. Each PlanItem represents a pending task definition activation:
- Which task definition is eligible
- Its assigned priority (from Planning Strategy or default)
- The trigger event (which case file key change made it eligible)
- Timestamp of eligibility

The CaseEngine consumes PlanItems from the agenda in priority order, rather than activating all eligible task definitions indiscriminately.

```java
public class PlanItem {
    private String planItemId;                // Unique activation record ID
    private String taskDefinitionId;          // Which task definition to activate
    private int priority;                     // Higher = execute sooner (default: 0)
    private String triggerKey;                // Which case file key change triggered eligibility
    private Instant createdAt;                // When the task definition became eligible
    private Optional<String> focusArea;       // Focus area this PlanItem belongs to
    private PlanItemStatus status;            // PENDING, RUNNING, COMPLETED, etc.

    public enum PlanItemStatus {
        PENDING,         // On agenda, awaiting execution
        RUNNING,         // Currently being executed
        WAITING,         // Failed, awaiting retry after backoff
        SUSPENDED,       // Temporarily paused
        COMPLETED,       // Task definition finished contributing
        FAULTED,         // Retries exhausted; sent to dead letter
        CANCELLED        // Removed from agenda by Planning Strategy
    }

    public static PlanItem create(TaskDefinition td, String triggerKey) { ... }
}
```

#### PlanItem Lifecycle

```
[PENDING] ──activate──> [RUNNING] ──success──> [COMPLETED]
                              │
                          failure ──> [WAITING] ──backoff──> [PENDING]
                              │               │
                              │         retries exhausted ──> [FAULTED] ──> Dead Letter
                              │
                          timeout ──> [FAULTED] (via ErrorInfo)

[CANCELLED] (by Planning Strategy or parent case file cancellation)
```

### 6.4 CMMN Stages and Milestones

CaseHub implements CMMN (Case Management Model and Notation) **Stages** and **Milestones** to provide hierarchical workflow organization and progress tracking. These concepts complement the dynamic task activation model with structured lifecycle management.

#### Stages

A **Stage** is a container for TaskDefinitions and other Stages that can be activated and completed as a unit. Stages provide:

- **Entry Criteria**: Conditions (CaseFile keys) that must be satisfied for stage activation
- **Exit Criteria**: Conditions that trigger stage termination (abnormal completion)
- **Autocomplete**: Stages can complete automatically when all contained work finishes
- **Hierarchical Containment**: Stages can nest other stages and plan items
- **Manual Activation**: Stages can require explicit activation even when criteria are met

**Stage Lifecycle States** (CMMN-aligned):

```
[PENDING] ──entry criteria met──> [ACTIVE] ──all work complete──> [COMPLETED]
                                      │
                                      ├──exit criteria met──> [TERMINATED]
                                      │
                                      ├──suspend──> [SUSPENDED] ──resume──> [ACTIVE]
                                      │
                                      └──error──> [FAULTED]
```

**Stage Interface**:

```java
public class Stage {
    private String stageId;
    private String name;
    private String caseFileId;
    private StageStatus status;                   // PENDING, ACTIVE, COMPLETED, etc.

    // Criteria
    private Set<String> entryCriteria;            // CaseFile keys required for activation
    private Set<String> exitCriteria;             // CaseFile keys that trigger termination

    // Containment
    private Optional<String> parentStageId;       // Parent stage (if nested)
    private List<String> containedPlanItemIds;    // PlanItems in this stage
    private List<String> containedStageIds;       // Nested stages
    private List<String> requiredItems;           // Items that must complete for autocomplete

    // Behavior
    private boolean manualActivation;             // Requires explicit activation
    private boolean autocomplete;                 // Complete when all required items finish

    public enum StageStatus {
        PENDING,      // Entry criteria not yet satisfied
        ACTIVE,       // Stage running, contained items can execute
        SUSPENDED,    // Temporarily paused
        COMPLETED,    // Normal completion (autocomplete or manual)
        TERMINATED,   // Abnormal completion (exit criteria triggered)
        FAULTED       // Error occurred
    }

    // Lifecycle methods
    public void activate();
    public void complete();
    public void terminate();
    public void suspend();
    public void resume();
    public void fault();
    public boolean isTerminal();
    public boolean isActive();
}
```

**Stage Evaluation**:

The `ListenerEvaluator` evaluates stages alongside task definitions:

```java
public class ListenerEvaluator {
    // Stage activation
    public List<Stage> evaluateAndActivateStages(CaseFile caseFile, CasePlanModel casePlanModel);

    // Stage completion (exit criteria + autocomplete logic)
    public List<Stage> evaluateAndCompleteStages(CaseFile caseFile, CasePlanModel casePlanModel);

    // Create plan items within active stages
    public List<PlanItem> evaluateAndCreatePlanItemsInStage(
        CaseFile caseFile, CasePlanModel casePlanModel,
        List<TaskDefinition> registered, String stageId, String triggerKey);
}
```

**Autocomplete Logic**:

Stages with `autocomplete=true` complete automatically in two scenarios:

1. **No required items**: Stage completes immediately upon activation (empty stage)
2. **All required items complete**: Stage completes when all contained plan items and nested stages reach terminal states

#### Milestones

A **Milestone** represents a significant achievement in the case workflow. Milestones are simpler than stages:

- **Achievement Criteria**: CaseFile keys that must be present for milestone to be achieved
- **Once Achieved, Always Achieved**: Milestones are immutable markers of progress
- **Progress Tracking**: Used to track workflow progression and trigger notifications

**Milestone Interface**:

```java
public class Milestone {
    private String milestoneId;
    private String name;
    private String caseFileId;
    private MilestoneStatus status;               // PENDING or ACHIEVED
    private Set<String> achievementCriteria;      // CaseFile keys required
    private Instant achievedAt;                   // When milestone was achieved

    public enum MilestoneStatus {
        PENDING,      // Achievement criteria not yet met
        ACHIEVED      // Milestone achieved (terminal state)
    }

    public void achieve();
    public boolean isAchieved();
}
```

**Milestone Evaluation**:

```java
// ListenerEvaluator
public List<Milestone> evaluateAndAchieveMilestones(CaseFile caseFile, CasePlanModel casePlanModel);
```

#### Integration with CasePlanModel

The CasePlanModel interface extends to support stages and milestones:

```java
public interface CasePlanModel {
    // Stages
    void addStage(Stage stage);
    void removeStage(String stageId);
    Optional<Stage> getStage(String stageId);
    List<Stage> getAllStages();
    List<Stage> getActiveStages();
    List<Stage> getRootStages();  // Stages with no parent

    // Milestones
    void addMilestone(Milestone milestone);
    void removeMilestone(String milestoneId);
    Optional<Milestone> getMilestone(String milestoneId);
    List<Milestone> getAllMilestones();
    List<Milestone> getPendingMilestones();
    List<Milestone> getAchievedMilestones();
}
```

#### Persistence

Stages and milestones are stored in-memory on the `CasePlanModel` (which is created per CaseFile at solve time by the CaseEngine). The `CaseFileRepository` SPI does not currently expose stage/milestone persistence methods; stages and milestones live in memory alongside their controlling CasePlanModel and are reconstructed when a case is reloaded.

The in-memory implementation uses `ConcurrentHashMap` to store stages and milestones indexed by stage/milestone ID within the `DefaultCasePlanModel`.

#### Example: Multi-Stage Document Processing

```java
// Create stages
Stage preparationStage = Stage.create("Data Preparation")
    .withEntryCriteria(Set.of("raw_document"))
    .withAutocomplete(true);
casePlanModel.addStage(preparationStage);

Stage analysisStage = Stage.create("Analysis")
    .withEntryCriteria(Set.of("extracted_text"))
    .withAutocomplete(true);
casePlanModel.addStage(analysisStage);

Stage outputStage = Stage.create("Output")
    .withEntryCriteria(Set.of("entities", "sentiment"))
    .withExitCriteria(Set.of("summary"));  // Terminates when summary produced
casePlanModel.addStage(outputStage);

// Create milestones
Milestone dataReady = Milestone.create("Data Ready")
    .withAchievementCriteria(Set.of("extracted_text"));
casePlanModel.addMilestone(dataReady);

Milestone analysisComplete = Milestone.create("Analysis Complete")
    .withAchievementCriteria(Set.of("entities", "sentiment"));
casePlanModel.addMilestone(analysisComplete);

// Workflow execution
while (!evaluator.isQuiescent(casePlanModel)) {
    // Activate stages when entry criteria are met
    evaluator.evaluateAndActivateStages(caseFile, casePlanModel);

    // Create and execute plan items
    evaluator.evaluateAndCreatePlanItems(caseFile, casePlanModel, taskDefs, null);

    // Check milestone achievements
    evaluator.evaluateAndAchieveMilestones(caseFile, casePlanModel);

    // Complete stages (exit criteria or autocomplete)
    evaluator.evaluateAndCompleteStages(caseFile, casePlanModel);
}
```

**Key Benefits**:

- **Hierarchical Organization**: Group related work into logical stages
- **Progress Visibility**: Milestones provide clear workflow state tracking
- **Conditional Execution**: Stages only activate when prerequisites are met
- **Flexible Completion**: Autocomplete or explicit exit criteria
- **CMMN Compliance**: Standard semantics for case management workflows

See `casehub-examples/src/main/java/io/casehub/examples/StageBasedDocumentProcessingExample.java` and `StageBasedWorkerIntegrationExample.java` for complete demonstrations.

---

### 6.5 Logging Strategy

- **Framework**: SLF4J with structured JSON output
- **Correlation**: `trace_id` from PropagationContext plus `case_file_id` (Long) and `otel_trace_id` (UUID) for CaseFiles, or `task_id` (Long) and `otel_span_id` (UUID) for Tasks, added to MDC for all logs; plus `td_id` during task definition execution. This enables tracing across the full CaseFile → TaskDefinition → Task → sub-Task hierarchy
- **Levels**:
  - INFO: CaseFile created/completed/faulted, task definition activated/contributed, task lifecycle events
  - DEBUG: Entry criteria evaluation results, case worker selection, routing decisions, heartbeats
  - ERROR: Task definition failures, task failures, timeouts, case worker crashes
  - WARN: Quiescence detected, missed heartbeats, task definition rollbacks

### 6.6 Metrics (Micrometer)

CaseHub exposes the following metrics for monitoring:

**CaseFile Model Counters**:
- `casehub_case_files_created_total{case_type}` - CaseFiles created
- `casehub_case_files_completed_total{case_type}` - CaseFiles completed/faulted
- `casehub_td_activations_total{td_id, case_type}` - Task definition activations
- `casehub_td_failures_total{td_id, case_type}` - Task definition failures

**CaseFile Model Gauges**:
- `casehub_case_files_running` - CaseFiles currently in RUNNING state
- `casehub_td_in_flight{case_type}` - Task definitions currently executing
- `casehub_task_definitions_registered{case_type}` - Registered task definitions by case type

**CaseFile Model Timers**:
- `casehub_case_file_solve_duration_seconds{case_type}` - Total case file solving time
- `casehub_td_execute_duration_seconds{td_id}` - Individual task definition execution time
- `casehub_activation_evaluation_duration` - Time to evaluate entry criteria

**Control Metrics**:
- `casehub_plan_items_created_total{case_type}` - PlanItems created
- `casehub_plan_items_cancelled_total{case_type}` - PlanItems cancelled by Planning Strategy
- `casehub_planning_strategy_invocations_total{ps_id}` - Planning Strategy invocations
- `casehub_agenda_size{case_file_id}` - Current agenda depth (gauge)
- `casehub_control_cycle_duration` - Time for full control cycle (timer)

**Context Propagation Metrics**:
- `casehub_hierarchy_depth{trace_id}` - Maximum depth of the CaseFile/Task hierarchy (gauge)
- `casehub_hierarchy_nodes_total{trace_id}` - Total CaseFiles + Tasks in hierarchy (counter)
- `casehub_child_case_files_spawned_total{case_type}` - Child case files spawned by task definitions
- `casehub_budget_exhaustions_total` - Times a child was capped by parent's remaining budget

**Task Model Counters**:
- `casehub_task_submissions_total{type, status}` - Total tasks submitted
- `casehub_task_completions_total{type, status}` - Tasks completed/faulted
- `casehub_caseworker_registrations_total` - Worker registrations
- `casehub_heartbeat_failures_total` - Missed heartbeats

**Task Model Gauges**:
- `casehub_tasks_pending` - Tasks awaiting assignment
- `casehub_tasks_running` - Tasks currently executing
- `casehub_caseworkers_active{capability}` - Active case workers by capability

**Task Model Timers**:
- `casehub_task_duration_seconds{type, status}` - Task execution time
- `casehub_caseworker_selection_duration` - Time to select case worker

**Resilience Metrics** (see §7):
- `casehub_dead_letter_entries_total{type}` - Items entering DLQ
- `casehub_dead_letter_replayed_total` - Items replayed from DLQ
- `casehub_dead_letter_discarded_total` - Items discarded
- `casehub_dead_letter_pending` - Items awaiting review (gauge)
- `casehub_poison_pill_quarantines_total{source_id}` - Quarantine events
- `casehub_poison_pill_active_quarantines` - Currently quarantined sources (gauge)

### 6.7 Health Checks

Quarkus health check integration for deployment orchestration:

**Readiness** (`/q/health/ready`):
- At least one task definition or case worker registered
- CaseEngine and TaskRegistry initialized and accepting requests
- Storage provider healthy

**Liveness** (`/q/health/live`):
- CaseEngine and TaskBroker instances responding
- No critical internal errors

---

## 7. Resilience

CaseHub provides robust resilience capabilities to ensure reliable operation under failure conditions.

### 7.1 Error Handling Strategy

#### TaskDefinition Failures (CaseFile Model)

**Task Definition Exception During Execution**:
- CaseFile keys written by the failed task definition during current `execute()` call are **rolled back**
- CaseFile remains RUNNING; other task definitions may still contribute
- If no other task definitions can activate, case file enters WAITING or FAULTED state
- Error logged with case_file_id and td_id in MDC

**Task Definition Timeout**:
- Task definition execution has a configurable timeout (default: 5 minutes)
- If exceeded, task definition thread is interrupted and contribution rolled back
- CaseEngine continues with remaining eligible task definitions

**Circular Dependencies**:
- Detected at registration time by `TaskDefinitionRegistry`
- `CircularDependencyException` thrown, task definition not registered

**Planning Strategy Failure**: Falls back to `DefaultPlanningStrategy`; case file solving continues

#### Worker Failures (Task Model)

**Worker Crash (Unresponsive)**:
- Detection: Missed heartbeat after 30s timeout
- Action: Mark assigned tasks as FAULTED with error code `CASEWORKER_TIMEOUT`
- Recovery: TaskScheduler may reassign to different case worker (Phase 2)

**Exception During Execution**:
- Worker calls `reportFailure(taskId, exception)`
- Task marked as FAULTED with structured error info
- Error details include: error code, message, retryable flag, timestamp

**Network Partition**:
- MVP: Worker marked inactive after heartbeat timeout
- Tasks assigned to inactive case worker marked FAULTED
- Phase 2: Automatic reassignment with idempotency checks

#### Requestor Failures

**Abandoned Tasks** (requestor never retrieves result):
- Results retained for configurable TTL (default: 1 hour)
- After TTL expiry, TaskRegistry cleanup process deletes task
- Logged at WARN level for monitoring

**Duplicate Submission**:
- If taskId matches existing task, return existing TaskHandle
- Prevents duplicate work for retries

#### System Failures

**TaskBroker Crash** (JVM restart):
- MVP: In-memory tasks lost (acceptable for MVP scope)
- Phase 2: Redis persistence ensures recovery

**Storage Provider Failure**:
- TaskBroker enters degraded mode
- Rejects new task submissions with 503 Service Unavailable
- Health check reports NOT_READY
- Existing in-flight tasks attempt completion

**Thread Pool Exhaustion**:
- Worker selection uses bounded thread pool (max 10 threads in MVP)
- Task submission queued with max queue size (1000)
- Reject with `TaskSubmissionException` if queue full

### 7.2 Timeout Enforcement

Timeout and cancellation are first-class states in both the CaseFile and Task state machines. Beyond declaring these states, CaseHub provides an **active enforcement mechanism** that transitions work to FAULTED (with timeout ErrorInfo) when deadlines expire.

#### TimeoutEnforcer

A dedicated Quarkus Scheduler component that actively monitors all in-flight work:

```java
@ApplicationScoped
public class TimeoutEnforcer {

    /**
     * Scheduled task that runs every 1 second (configurable).
     * Checks all active CaseFiles, executing PlanItems, and in-flight Tasks
     * against their configured deadlines.
     */
    @Scheduled(every = "${casehub.timeout.check-interval:1s}")
    public void enforceTimeouts();
}
```

**What TimeoutEnforcer monitors:**

| Target | Timeout Source | Action on Expiry |
|--------|--------------|-----------------|
| CaseFile | `Duration timeout` on `createAndSolve()`, or PropagationContext deadline | CaseFile → FAULTED (timeout ErrorInfo); all in-flight PlanItems cancelled; all child case files/tasks cancelled |
| PlanItem (on agenda) | CaseFile's remaining budget | PlanItem → FAULTED (timeout ErrorInfo); removed from agenda |
| PlanItem (executing) | Per-task-definition timeout (default: 5min) | Task definition thread interrupted; contribution rolled back; PlanItem → FAULTED (timeout ErrorInfo) |
| Task | `Duration timeout` on `submitTask()`, or PropagationContext deadline | Task → FAULTED (timeout ErrorInfo); case worker notified |
| Worker heartbeat | 30s heartbeat interval | Worker marked inactive; assigned tasks → FAULTED (CASEWORKER_TIMEOUT) |

#### Timeout Cascade Rules

When a parent times out, all descendants are cancelled:
1. CaseFile FAULTED (timeout) → all executing PlanItems → CANCELLED, all child case files → CANCELLED, all child tasks → CANCELLED
2. CaseFile FAULTED (timeout) → CasePlanModel agenda cleared
3. PropagationContext deadline ensures children cannot outlive parents

#### Waiting CaseFile Timeout

CaseFiles in WAITING state (no more task definitions can fire) are monitored by TimeoutEnforcer:
- If a case file is WAITING and its deadline expires → FAULTED (timeout ErrorInfo)
- If a case file is WAITING and no deadline is set → remains WAITING until explicit completion or configurable waiting timeout (default: 10 minutes)

#### Configuration

```properties
casehub.timeout.check-interval=1s
casehub.timeout.td.default=5m
casehub.timeout.td.ocr-extractor=10m
casehub.timeout.casefile.default=30m
casehub.timeout.casefile.waiting=10m
casehub.timeout.task.default=5m
casehub.timeout.caseworker.heartbeat=30s
```

### 7.3 Retry Policy

Structured retry support for both domain task definition executions and task executions. Retry is automatic and configurable — requestors no longer need to manually resubmit.

#### RetryPolicy

```java
public class RetryPolicy {
    private int maxAttempts;                      // Max retries (default: 3, 0 = no retry)
    private BackoffStrategy backoffStrategy;      // How to space retries
    private Duration maxRetryDuration;            // Total time budget for all retries
    private Set<String> retryableErrorCodes;      // Which errors to retry (empty = all retryable)
    private Set<String> nonRetryableErrorCodes;   // Which errors to never retry

    public enum BackoffStrategy {
        FIXED,                  // Same delay every time (default: 1s)
        EXPONENTIAL,            // 1s, 2s, 4s, 8s, ... (capped at maxBackoff)
        EXPONENTIAL_WITH_JITTER // Exponential + random jitter to prevent thundering herd
    }

    private Duration initialDelay;    // First retry delay (default: 1s)
    private Duration maxBackoff;      // Cap for exponential backoff (default: 30s)
    private double jitterFactor;      // 0.0-1.0, randomization factor (default: 0.1)

    // Convenience factories
    public static RetryPolicy none();
    public static RetryPolicy defaults();         // 3 attempts, exponential with jitter, 2min max
    public static RetryPolicy of(int maxAttempts, BackoffStrategy strategy);

    public static RetryPolicyBuilder builder() { ... }
}
```

#### Where Retry Applies

**CaseFile Model — TaskDefinition Retry:**
When a domain task definition fails during `execute()`:
1. Contributed keys are rolled back (existing behavior)
2. PlanItem status → WAITING
3. CaseEngine consults the task definition's retry policy
4. If retries remain: PlanItem re-queued on CasePlanModel agenda after backoff delay
5. If retries exhausted: PlanItem → FAULTED; task definition sent to dead letter (see §7.4)
6. CaseFile remains RUNNING while other task definitions can still contribute

**Task Model — Task Retry:**
When a task case worker fails:
1. Task status → WAITING
2. TaskScheduler consults the task's retry policy
3. If retries remain: task re-queued for case worker selection after backoff delay
4. If retries exhausted: task → FAULTED; sent to dead letter

#### Retry Budget Interaction with Timeout

- Retry attempts consume the parent timeout budget. If a CaseFile has 10 minutes remaining and a task definition has already used 8 minutes across 2 failed attempts, the 3rd retry is only allowed if `remainingBudget > initialDelay`
- Retries never extend past the PropagationContext deadline
- If a retry is pending when the CaseFile times out, the retry is cancelled (not executed)

#### Retry Tracking

```java
public class RetryState {
    private String targetId;              // Task Definition ID or Task ID
    private int attemptNumber;            // Current attempt (1-based)
    private int maxAttempts;
    private List<RetryAttempt> history;   // Record of all attempts

    public static class RetryAttempt {
        private int attemptNumber;
        private Instant startedAt;
        private Instant failedAt;
        private ErrorInfo error;
        private Duration backoffApplied;
    }
}
```

#### Configuration

```properties
casehub.retry.td.default.max-attempts=3
casehub.retry.td.default.backoff=EXPONENTIAL_WITH_JITTER
casehub.retry.td.default.initial-delay=1s
casehub.retry.td.default.max-backoff=30s
casehub.retry.td.default.max-duration=2m
casehub.retry.td.ocr-extractor.max-attempts=5

casehub.retry.task.default.max-attempts=3
casehub.retry.task.default.backoff=EXPONENTIAL_WITH_JITTER
casehub.retry.task.default.initial-delay=1s
casehub.retry.task.default.max-duration=5m
```

### 7.4 Dead Letter Queue & Poison Pill Protection

When retries are exhausted, failed items must go somewhere safe rather than being silently dropped or — worse — retried indefinitely. The dead letter mechanism ensures failed work is preserved for inspection while **preventing poison pills** from consuming resources.

#### Dead Letter Queue (DLQ)

```java
@ApplicationScoped
public class DeadLetterQueue {

    /**
     * Move a failed task definition activation to the dead letter queue.
     * Called by CaseEngine when PlanItem retries are exhausted.
     */
    public DeadLetterEntry sendToDeadLetter(PlanItem planItem, CaseFile caseFile,
                                             List<RetryState.RetryAttempt> retryHistory,
                                             ErrorInfo finalError);

    /**
     * Move a failed Task to the dead letter queue.
     * Called by TaskScheduler when Task retries are exhausted.
     */
    public DeadLetterEntry sendToDeadLetter(Task task,
                                             List<RetryState.RetryAttempt> retryHistory,
                                             ErrorInfo finalError);

    /**
     * Query dead letter entries — for monitoring dashboards and manual review.
     */
    public List<DeadLetterEntry> list(DeadLetterQuery query);

    /**
     * Manually replay a dead letter entry (after fixing the underlying issue).
     * Creates a new PlanItem or Task with the original context.
     */
    public void replay(String deadLetterId);

    /**
     * Permanently discard a dead letter entry (acknowledged as unrecoverable).
     */
    public void discard(String deadLetterId);

    /**
     * Purge expired dead letter entries (TTL-based cleanup).
     */
    public void purge(Duration olderThan);
}
```

#### DeadLetterEntry

```java
public class DeadLetterEntry {
    private String deadLetterId;          // Unique ID
    private DeadLetterType type;          // PLAN_ITEM or TASK
    private Instant arrivedAt;            // When it entered the DLQ

    // Original context — everything needed to replay
    private String originalId;            // PlanItem ID or Task ID
    private String caseFileId;            // Originating case file (for PlanItems)
    private String taskType;             // Task type (for Tasks)
    private Map<String, Object> originalContext;  // Input data
    private PropagationContext propagationContext;

    // Failure details
    private ErrorInfo finalError;
    private List<RetryState.RetryAttempt> retryHistory;
    private int totalAttempts;

    // Status
    private DeadLetterStatus status;      // PENDING_REVIEW, REPLAYED, DISCARDED

    public enum DeadLetterType { PLAN_ITEM, TASK }
    public enum DeadLetterStatus { PENDING_REVIEW, REPLAYED, DISCARDED }
}
```

#### Poison Pill Protection

A **poison pill** is a task definition or Task that consistently fails, consuming resources on every retry attempt without ever succeeding. CaseHub protects against this with a **circuit breaker** pattern:

```java
@ApplicationScoped
public class PoisonPillDetector {

    /**
     * Track failure for a TaskDefinition or Task type.
     * If failures exceed the threshold within the window, the source is quarantined.
     */
    public void recordFailure(String sourceId, String sourceType);

    /**
     * Check if a task definition or Task type is quarantined (considered a poison pill).
     */
    public boolean isQuarantined(String sourceId);

    /**
     * Manually release a quarantined source (after fixing the underlying issue).
     */
    public void release(String sourceId);
}
```

**How it works:**

1. **Failure counting**: PoisonPillDetector tracks failures per task definition ID and per task type within a sliding time window
2. **Threshold breach**: When failures exceed the threshold (default: 5 failures in 10 minutes), the source is **quarantined**
3. **Quarantine effect**:
   - Quarantined task definition: ListenerEvaluator skips this task definition when creating PlanItems. CaseFile solving continues without it
   - Quarantined task type: TaskBroker rejects new submissions of this type with `PoisonPillException`
4. **Recovery**: Quarantine expires after a configurable cool-down period (default: 30 minutes), or can be manually released
5. **Notification**: Quarantine events published via NotificationService and logged at ERROR level

#### Dead Letter Flow

```
TaskDefinition/Task fails
    → Retry Policy: attempts remaining?
        → YES: backoff delay → re-queue (WAITING)
        → NO: send to Dead Letter Queue
              → PoisonPillDetector.recordFailure()
                  → threshold breached?
                      → YES: quarantine source; log ERROR; notify
                      → NO: continue; other instances of this task definition/task unaffected
```

#### Configuration

```properties
casehub.poison-pill.failure-threshold=5
casehub.poison-pill.failure-window=10m
casehub.poison-pill.quarantine-duration=30m
casehub.dead-letter.retention=7d
casehub.dead-letter.max-entries=10000
```

### 7.5 Distributed Consistency

CaseHub supports single-instance deployment in MVP with a clear path to distributed deployment. The consistency model is explicitly defined at each layer.

#### Consistency Model

| Layer | MVP (Single Instance) | Phase 2+ (Distributed) |
|-------|----------------------|----------------------|
| CaseFile state | Strong (ConcurrentHashMap + synchronized) | Optimistic locking with version vectors |
| CasePlanModel | Strong (same JVM) | Leader-elected single writer |
| Task state | Strong (ConcurrentHashMap) | Redis atomic operations |
| PlanItem agenda | Strong (priority queue in JVM) | Redis sorted set with distributed lock |
| Dead letter | Strong (in-memory) | Redis list with at-least-once guarantee |

#### CaseFile Versioning (MVP)

Every CaseFile write is versioned to support optimistic concurrency and future distributed deployment:

```java
public class CaseFileItem {
    private Object value;
    private long version;          // Monotonically increasing per key
    private String writtenBy;      // Task definition ID that wrote this value
    private Instant writtenAt;
    private String instanceId;     // CaseHub instance ID (for distributed mode)
}
```

#### Conflict Resolution for CaseFile Writes

When two TaskDefinitions (potentially on different instances) write to the same case file key concurrently:

```java
public interface ConflictResolver {
    /**
     * Resolve a conflict when two writers attempt to update the same key.
     *
     * @param key         The contested case file key
     * @param existing    The current value (with version and writer info)
     * @param incoming    The new value being written
     * @return            The resolved value to store
     */
    CaseFileItem resolve(String key, CaseFileItem existing, CaseFileItem incoming);
}
```

**Built-in conflict resolution strategies:**
- `LAST_WRITER_WINS` (default): Higher version wins; ties broken by timestamp
- `FIRST_WRITER_WINS`: Reject the incoming write; task definition must re-read and re-contribute
- `MERGE`: Invoke a custom merge function (registered per key pattern)
- `FAIL`: Throw `ConflictException`; task definition contribution rolled back

#### Idempotency Guarantees

```java
@ApplicationScoped
public class IdempotencyService {

    /**
     * Check if an operation has already been performed.
     * Uses idempotencyKey from TaskRequest or generated from PlanItem context.
     */
    public boolean isAlreadyProcessed(String idempotencyKey);

    /**
     * Record that an operation has been performed.
     * TTL-based expiry (default: 24 hours).
     */
    public void markProcessed(String idempotencyKey, Object result);

    /**
     * Get the cached result of a previously processed operation.
     */
    public Optional<Object> getCachedResult(String idempotencyKey);
}
```

**Idempotency enforcement points:**
- TaskBroker: On duplicate `idempotencyKey`, return cached TaskHandle (not re-execute)
- WorkerRegistry.submitResult(): Duplicate result submissions are idempotent (last-write-wins with version check)
- TaskDefinition.execute(): CaseFile.putIfVersion() prevents duplicate writes from re-activated task definitions
- Dead letter replay: Replay generates new idempotency key to avoid re-dedup

#### Distributed Deployment Model (Phase 2+)

When multiple CaseHub instances share state via Redis:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  CaseHub     │    │  CaseHub     │    │  CaseHub     │
│  Instance A     │    │  Instance B     │    │  Instance C     │
│                 │    │                 │    │                 │
│  CaseEngine     │    │  CaseEngine     │    │  CaseEngine     │
│  (follower)     │    │  (LEADER)       │    │  (follower)     │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         └──────────────────────┼──────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │     Redis Cluster     │
                    │  ┌─────────────────┐  │
                    │  │ CaseFile State  │  │ ← Optimistic locking via WATCH/MULTI
                    │  │ CasePlanModel   │  │ ← Leader-only writes
                    │  │ PlanItem Agenda │  │ ← Sorted set with BRPOPLPUSH
                    │  │ Task Queue      │  │ ← Atomic RPOPLPUSH
                    │  │ Dead Letter     │  │ ← Append-only list
                    │  │ Locks           │  │ ← Redisson distributed locks
                    │  └─────────────────┘  │
                    └───────────────────────┘
```

**Leader Election for CasePlanModel:**
- Only one instance runs the CaseEngine control loop per case file
- Leader elected via Redis distributed lock (Redisson)
- Followers monitor leader health; automatic failover on leader crash
- Domain task definitions can execute on any instance; results written to shared Redis case file

**Optimistic Locking for CaseFile Writes:**
```java
// Distributed case file write with optimistic concurrency
public void put(String key, Object value) {
    // 1. Read current version from Redis
    // 2. Execute WATCH on key
    // 3. MULTI: write new value + increment version
    // 4. EXEC: if version changed since WATCH, StaleVersionException → retry
}
```

#### Configuration

```properties
casehub.consistency.conflict-resolution=LAST_WRITER_WINS
casehub.consistency.conflict-resolution.entities=MERGE
casehub.consistency.idempotency-ttl=24h
casehub.consistency.leader-lock-ttl=30s
casehub.consistency.leader-renewal-interval=10s
```

---

## 8. Agentic AI Specifics

This section covers what makes CaseHub specifically suited for agentic AI systems — the dual execution model, Quarkus-native integration, and how AI agents interact with the platform.

### 8.1 Dual Execution Model

CaseHub provides two complementary execution models:

| | CaseFile Model (Case Management) | Task Model (Request-Response) |
|---|---|---|
| **Use Case** | Collaborative, multi-contributor problems | Simple, single-case-worker work |
| **Data Flow** | Many task definitions read/write shared case file | One requestor → one case worker → one result |
| **Control** | Data-driven (entry criteria on case file state) | Explicit (requestor submits, case worker claims) |
| **Solution** | Emerges from accumulated contributions | Returned as single result |
| **Example** | "Analyze this legal case" | "Classify sentiment of this text" |

Both models share the same case worker infrastructure, storage SPI, and observability.

### 8.2 Goals vs Tasks

- **Goal**: High-level objective or desired outcome (e.g., "analyze customer sentiment")
- **Task**: Concrete, executable work unit derived from a goal (e.g., "classify sentiment of document X")

**Current state**: CaseFiles complete by quiescence (no more task definitions can fire and none are in-flight) or by explicit `caseFile.complete()`. This is backward compatible and remains the default.

#### Planned: Goal Model (Issue #7)

A formal `CaseGoal` model is planned to provide a richer, explicit completion contract at `createAndSolve()` time:

- **`CaseGoal`**: Carries a *satisfaction predicate* (conditions under which the case is successfully complete) and an *abandonment predicate* (conditions under which the case should be terminated as unsolvable). Named `Milestone` objects within the goal track sub-achievements.
- **`GoalEvaluator`**: A new CaseEngine collaborator that evaluates the `CaseGoal` after each control cycle, transitioning the CaseFile to COMPLETED (satisfaction) or FAULTED (abandonment) as appropriate.
- **Backward compatibility**: Cases created with no `CaseGoal` fall back to existing quiescence-based completion unchanged.
- **Research basis**: Design research is captured in `docs/research/goal-model-research.md`.

This will make the formal contract between a requestor and the system explicit, rather than relying on the implicit "no more task definitions can fire" heuristic.

### 8.3 Quarkus Integration

#### CDI-Based TaskDefinition Declaration

Task definitions are declared as CDI beans with `@CaseType` annotations for automatic registration:

```java
@ApplicationScoped
@CaseType("legal-case-analysis")
public class NerTaskDefinition implements TaskDefinition {

    @Override
    public String getId() { return "ner-extractor"; }

    @Override
    public String getName() { return "Named Entity Recognition"; }

    @Override
    public Set<String> entryCriteria() {
        return Set.of("extracted_text");   // Needs OCR output
    }

    @Override
    public Set<String> producedKeys() {
        return Set.of("entities");          // Produces entity list
    }

    @Override
    public void execute(CaseFile caseFile) {
        String text = caseFile.get("extracted_text", String.class).orElseThrow();
        List<Entity> entities = nerService.extract(text);
        caseFile.put("entities", entities);
    }
}
```

#### Configuration via application.properties

All CaseHub configuration is managed through standard Quarkus `application.properties`. See individual sections for specific properties:
- Timeout configuration (§7.2)
- Retry policy configuration (§7.3)
- Poison pill configuration (§7.4)
- Consistency configuration (§7.5)

#### Security

**Requestor Security**:
- Role-based access control via `@RolesAllowed({"task-submitter", "admin"})`
- Integrates with Quarkus Security (OIDC, LDAP, or basic auth)
- Prevents unauthorized task submission

```java
@RolesAllowed({"task-submitter", "admin"})
public TaskHandle submitTask(TaskRequest request) { ... }
```

**Worker Security**:
- API key authentication for case worker registration
- Configuration: `casehub.caseworker.api-keys=worker1:secret1,worker2:secret2`
- Each case worker operation validates API key matches case worker ID
- Prevents unauthorized result submission or task claiming

**Task Data Security (MVP Scope)**:
- No encryption at rest (in-memory storage)
- No encryption in transit (assumes internal network or TLS termination at load balancer)
- Task context validation: Reject oversized payloads (>1MB default)

**Future Security Enhancements (Phase 2+)**:
- At-rest encryption for Redis storage
- TLS for case-worker-to-broker communication
- Task context encryption for sensitive data
- Fine-grained permissions (e.g., user can only cancel own tasks)
- Audit logging for compliance
- Schema validation for task context (JSON Schema)
- Rate limiting on task submission

### 8.4 End-to-End Examples

#### Example: Legal Case Analysis (CaseFile Model)

```java
// Requestor creates a case file with initial documents
CaseFile caseFile = caseEngine.createAndSolve(
    "legal-case-analysis",
    Map.of("raw_documents", List.of(doc1, doc2))
);

// CaseFile solving happens automatically:
// 1. CaseEngine detects "raw_documents" key
// 2. OCR TaskDefinition activates (requires: raw_documents, produces: extracted_text)
// 3. CaseFile now has extracted_text → NER and Sentiment task definitions both activate in parallel
// 4. CaseFile now has entities + sentiment → Summary task definition activates
// 5. CaseFile complete with case_summary

// Requestor waits for result
CaseFile result = caseEngine.awaitCompletion(caseFile, Duration.ofMinutes(10));
Map<String, Object> solution = result.snapshot();
String summary = (String) solution.get("case_summary");
```

#### Example: Document Processing with Quarkus Flow (casehub-flow-worker)

The optional `casehub-flow-worker` module demonstrates workflow engine integration:

```java
// 1. Define workflow using Quarkus Flow programmatic API
@ApplicationScoped
public class QuarkusFlowDocumentWorkflow extends Flow implements FlowWorkflowDefinition {

    @Inject
    DocumentFunctions documentFunctions;

    @Override
    public Workflow descriptor() {
        return FuncWorkflowBuilder.workflow("document-processing")
            .tasks(
                function("extractText", documentFunctions::extractText, Map.class),
                function("recognizeEntities",
                    ctx -> documentFunctions.recognizeEntities((Map) ctx),
                    Map.class),
                function("analyzeSentiment",
                    ctx -> documentFunctions.analyzeSentiment((Map) ctx),
                    Map.class),
                function("generateSummary",
                    ctx -> documentFunctions.generateSummary((Map) ctx),
                    Map.class)
            )
            .build();
    }

    @Override
    public Map<String, Object> execute(FlowExecutionContext context) throws Exception {
        // Extract inputs from CaseHub task context
        String documentUrl = context.getInput("documentUrl", String.class).orElseThrow();

        // Execute Quarkus Flow workflow
        var result = this.startInstance(
            Map.of("documentUrl", documentUrl, "traceId", context.getTraceId())
        ).await().indefinitely();

        return extractWorkflowData(result);
    }
}

// 2. Submit task to FlowWorker
TaskRequest request = TaskRequest.builder()
    .taskType("document-processing")
    .context(Map.of("documentUrl", "https://example.com/doc.pdf"))
    .requiredCapabilities(Set.of("flow"))
    .build();

TaskResult result = taskBroker.submitTask(request).awaitResult(Duration.ofMinutes(2));
Map<String, Object> output = result.getData();
// {extractedText: "...", entities: [...], sentiment: {...}, summary: "..."}
```

**Key Features:**
- **Workflow Definition**: Quarkus Flow's programmatic DSL for sequential steps
- **CDI Integration**: Workflows and functions are `@ApplicationScoped` beans
- **PropagationContext**: Full trace ID and lineage tracking through workflow steps
- **Data Flow**: Functions pass accumulated context between steps
- **Execution**: Workflow runs via `this.startInstance()` and returns combined results

**Module Benefits:**
- Isolated dependency: Quarkus Flow only required if using this module
- Multiple engines: Future modules can support Temporal, Camunda, etc.
- Standard integration: FlowWorker follows the same Worker pattern as other workers

See `casehub-flow-worker/README.md` and `QUARKUS_FLOW_INTEGRATION.md` for complete setup instructions.

---

## 9. Implementation Plan (2-Week Sprint)

### Week 1: Core Framework — CaseFile, Control & Task Models
**Day 1-2: Project Setup & Core Interfaces**
- Quarkus project initialization (Maven, dependencies)
- Define CaseFile model interfaces: `CaseFile`, `CaseEngine`, `TaskDefinition`, `CaseStatus`
- Define Control interfaces: `CasePlanModel`, `PlanningStrategy`, `PlanItem`
- Define Task model interfaces: `TaskBroker`, `TaskHandle`, `TaskStatus`
- Define cross-cutting: `PropagationContext` (traceId, inheritedAttributes, deadline, budget)
- Define resilience: `RetryPolicy`, `DeadLetterQueue`, `DeadLetterEntry`, `PoisonPillDetector`, `TimeoutEnforcer`
- Define consistency: `CaseFileItem`, `ConflictResolver`, `IdempotencyService`
- Define shared interfaces: `WorkerRegistry`, `NotificationService`
- Define data models: `TaskRequest`, `Task`, `TaskResult`, `ErrorInfo`, `CaseFileItemEvent`
- Basic unit tests for models, PlanItems, PropagationContext, and RetryPolicy

**Day 3-4: Storage Layer, CaseFile & CasePlanModel Implementation**
- Implement `InMemoryCaseFileRepository` + `InMemoryTaskRepository` in `casehub-persistence-memory`
- Implement `HibernateCaseFileRepository` + `HibernateTaskRepository` in `casehub-persistence-hibernate` (JPA/Panache; workspace as JSON TEXT)
- Implement `InMemoryCaseFile` and `InMemoryTask` (POJO graph with parent/child references)
- Implement `CasePlanModel` with agenda management, focus, strategy, and resource tracking
- Implement `PlanItem` with priority ordering and status lifecycle
- TTL-based cleanup scheduler (Quarkus Scheduler) — cleans up by CaseFile ID
- Unit tests for CaseFile, CasePlanModel, PlanItem, CaseFileRepository, TaskRepository
- Concurrency tests for CaseFile (multiple writers)

**Day 5: TaskDefinition & Worker Management**
- Implement `WorkerRegistry` with heartbeat tracking
- Implement `TaskDefinitionRegistry` for both domain task definitions and planning strategies
- Circular dependency detection for domain task definitions
- CDI-based `@CaseType` annotation scanning for auto-registration (domain task definitions + planning strategies)
- Implement `DefaultPlanningStrategy` (equal-priority baseline)
- Worker timeout detection (30s)
- Unit tests for all registries

### Week 2: Control Loop, Coordination & Testing
**Day 6-7: CaseEngine Control Loop & TaskBroker**
- Implement `ListenerEvaluator` — entry criteria evaluation, PlanItem creation, quiescence detection
- Implement `CaseEngine` control loop:
  - CaseFile change → ListenerEvaluator creates PlanItems on CasePlanModel
  - Invoke Planning Strategies to prioritize/filter PlanItems
  - Execute highest-priority PlanItem(s) from agenda
  - Domain task definition contributes to CaseFile → repeat
- Implement `createChildCaseFile()` with automatic PropagationContext inheritance (delegates to `CaseFileRepository.createChild()`)
- Hierarchy queries done via POJO graph (`getParentCase()`, `getChildCases()`, `getOwningCase()`, `getChildTasks()`) — no separate LineageService
- Implement `TaskScheduler` with round-robin selection
- Wire `TaskBroker` orchestration (delegates to TaskRegistry + TaskScheduler)
- Implement `NotificationService` with Quarkus Event Bus (case file changes + task lifecycle)
- Implement hierarchical cancellation (cancel propagates to all child case files/tasks)
- Budget enforcement: child deadline capped at parent's remaining budget
- Unit tests for ListenerEvaluator, CaseEngine (with and without custom Planning Strategies)
- Unit tests for context propagation (root → child case file → child task, budget inheritance, POJO graph)
- Integration tests: end-to-end CaseFile solving with task definitions spawning child case files and tasks
- Integration tests: end-to-end Task submission and result retrieval

**Day 8: Resilience, Observability & Error Handling**
- Implement `TimeoutEnforcer` (Quarkus Scheduler, checks every 1s)
- Implement `RetryPolicy` with backoff strategies (fixed, exponential, exponential+jitter)
- Implement PlanItem and Task retry flow (WAITING state, backoff delay, re-queue)
- Implement `DeadLetterQueue` with DLQ entries, replay, and discard
- Implement `PoisonPillDetector` with failure counting, quarantine, and cool-down
- Implement `IdempotencyService` for duplicate detection
- Add Micrometer metrics for CaseFile, Control, Task, DLQ, and poison pill models
- Structured logging with MDC (trace_id/span_id/parent_span_id + case_file_id/task_id/td_id)
- Quarkus health checks (readiness, liveness)
- Error handling: task definition failures with retry, case worker crashes, case file timeouts
- Task definition failure rollback (contributed keys not written on exception)
- Planning Strategy failure handling (fall back to DefaultPlanningStrategy)
- Unit tests for: timeout enforcement, retry with backoff, dead letter flow, poison pill quarantine

**Day 9: Quarkus Integration & Security**
- CDI integration and dependency injection for all components
- Configuration via `application.properties`
- Security: API key validation for case workers
- Basic authentication/authorization with `@RolesAllowed`
- Integration tests with Quarkus Test framework

**Day 10: Documentation & Examples**
- API documentation (Javadoc)
- Example: Legal case analysis case file (multi-task-definition with custom Planning Strategy for priority)
- Example: Simple sentiment analysis task (request-response)
- README with quickstart guide for CaseFile model (with/without custom control) and Task model
- End-to-end acceptance tests for both models

### Testing Strategy
- **Unit Tests**: JUnit 5, Mockito for component isolation
- **Integration Tests**: Quarkus Test with `@QuarkusTest`
- **Concurrency Tests**: Multiple task definitions contributing to same case file; multiple task submissions
- **CaseFile-specific Tests**: Entry criteria evaluation, quiescence detection, auto-completion
- **Control-specific Tests**: PlanItem priority ordering, Planning Strategy agenda manipulation, focus changes
- **Propagation Tests**: Context inheritance across CaseFile → child CaseFile → Task hierarchy, budget enforcement, hierarchical cancellation, lineage queries
- **Resilience Tests**: Timeout enforcement (case file, task definition, task), retry with backoff, dead letter flow, poison pill detection and quarantine, idempotency, case file versioning and conflict resolution
- **Coverage Target**: 80% line coverage minimum

### Out of Scope for MVP
- Redis storage provider (Phase 2, Week 3+)
- Progress notifications (Phase 2, Week 4+)
- Task definition re-activation and retry on failure (Phase 2)
- Nested tasks/sub-slots (Phase 3)
- Advanced control strategies (meta-reasoning, learning from past case files) (Phase 3)
- Merge strategies (Phase 3)
- Distributed deployment (Phase 4)

### Phase 1.5 Buffer (Week 3)
Reserved for:
- Bug fixes from testing
- Performance optimization for CaseFile control loop
- Documentation improvements
- Integration with CaseHub (if ready)

---

## 10. Future Enhancements

### Phase 2 (Weeks 3-4) — Persistence & Reliability
- Redis persistence module for CaseFile, Task, and DLQ (additional module alongside Hibernate)
- Task definition re-activation and retry on failure
- Basic progress notifications
- Configurable retry policies
- CaseFile state snapshots and restoration on crash recovery
- Timeout handling improvements
- Goal model: `CaseGoal`, `GoalEvaluator` — explicit satisfaction/abandonment contracts at `createAndSolve()` time (issue #7)

### Phase 3 (Weeks 5-8) — Advanced Case Management & Control Features
- **CaseFile hierarchies**: Nested case files for sub-problem decomposition
- **Conditional re-activation**: Task definitions can re-fire when keys they've already read are updated
- **Meta-reasoning Planning Strategies**: Planning Strategies that learn from past case file solutions to improve strategy
- **Adaptive resource management**: Planning Strategies that adjust strategy based on resource consumption
- **Control plan persistence**: Save and replay successful control strategies
- Goal → Task decomposition (goals create case files automatically; builds on Phase 2 Goal model)
- Partial result streaming
- Advanced case worker selection (capability matching, load balancing)
- Distributed tracing with OpenTelemetry

### Phase 4 (Long-term) — Distributed & Enterprise
- FIPA contract net protocols
- Policy governance framework
- Merge strategy SPI for conflicting task definition contributions
- Multi-instance CaseEngine (distributed control loop with distributed CasePlanModel)
- Distributed deployment support
- CaseFile templates (predefined case types with standard task definition + Planning Strategy pipelines)
- Planning Strategy marketplace (pluggable control strategies)

---

## 11. Design Decisions (Answered Questions)

### 1. Goals vs Tasks
**Decision**: Task-only for MVP; Goal model planned for Phase 2 (issue #7)
- Rationale: Goal decomposition patterns will emerge from usage. Premature abstraction risk.
- Planned: `CaseGoal` with satisfaction/abandonment predicates and `GoalEvaluator`; backward compatible (no-goal falls back to quiescence completion).
- Future: Phase 3 will add goal-to-task decomposition (goals that automatically create case files).

### 2. Notification Mechanism
**Decision**: Quarkus CDI Event Bus
- Rationale: Native Quarkus integration, simple for MVP, no external dependencies.
- Implementation: `NotificationService` publishes `TaskEvent` via `@Observes`.
- Future: Phase 2 may add reactive messaging (Kafka/AMQP) for distributed scenarios.

### 3. Worker Registration
**Decision**: Programmatic registration with API key authentication
- Rationale: MVP needs flexibility; case workers call `WorkerRegistry.register()` at startup.
- Configuration: API keys in `application.properties` for case worker validation.
- Future: Phase 2 adds service discovery (Consul, Kubernetes) for dynamic environments.

### 4. Serialization
**Decision**: Jackson JSON
- Rationale: Quarkus default, excellent `Map<String, Object>` support, human-readable.
- Constraint: Task context and result data must be JSON-serializable types.
- Native builds: Custom types need `@RegisterForReflection`.
- Future: Consider Protocol Buffers for high-throughput scenarios (Phase 4).

### 5. Thread Safety
**Decision**: ConcurrentHashMap + synchronized blocks
- TaskRegistry: `ConcurrentHashMap<String, TaskData>` for storage.
- Worker selection: Synchronized block in `TaskScheduler.selectWorker()`.
- TaskHandle: Thread-safe via immutable task ID and `CompletableFuture`.
- Rationale: Simple, performant for MVP single-instance deployment.
- Future: Phase 4 adds distributed locking (Redis, Hazelcast) for multi-instance.

### 6. Monitoring
**Decision**: Micrometer metrics + Quarkus health checks
- Metrics exposed:
  - Counters: `task_submissions_total`, `task_completions_total`, `task_failures_total`
  - Gauges: `tasks_pending`, `tasks_running`, `caseworkers_active`
  - Timers: `task_duration_seconds`, `caseworker_selection_duration`
- Health checks: Readiness (>=1 case worker), Liveness (broker responsive)
- Logging: Structured JSON with task_id in MDC for correlation.
- Future: Distributed tracing with OpenTelemetry (Phase 3).

---

## 12. Success Criteria

### MVP (2 weeks) - Core Case Management + Task Functionality

**Case Management Core (§3):**
- **CaseFile Creation**: Requestor can create a case file with initial state and paired CasePlanModel
- **TaskDefinition Registration**: Domain task definitions and Planning Strategies register via CDI with `@CaseType`
- **Data-Driven Activation**: ListenerEvaluator evaluates entry criteria, creates PlanItems
- **Shared Workspace**: Multiple task definitions can read from and write to the same case file concurrently
- **CaseFile Lifecycle**: Full PENDING → RUNNING → WAITING → COMPLETED/FAULTED lifecycle

**Workers (§4):**
- **Task Submission**: Requestor can submit task and receive TaskHandle immediately
- **Task Routing**: TaskScheduler routes tasks to case workers using round-robin
- **Result Retrieval**: Requestor receives notification when result available
- **Worker Lifecycle**: Worker registration with capabilities, heartbeat, claiming
- **Cleanup**: TTL-based cleanup for case files, task slots, and dead letter entries

**Coordination (§5):**
- **Control Loop**: CaseEngine runs Erman/Hayes-Roth cycle (detect → create PlanItems → invoke Planning Strategies → execute top PlanItems → repeat)
- **Auto-Completion**: CaseFile automatically completes when agenda empty and no PlanItems in-flight
- **PropagationContext**: Every CaseFile and Task carries a shared trace ID and optional resource budget; structural hierarchy carried by POJO graph
- **CaseFile identity**: `getId()` (Long primary key), `getOtelTraceId()` (UUID, shared with descendants), `getCaseType()`
- **Task identity**: `getId()` (Long primary key), `getOtelSpanId()` (UUID unique per task)
- **Automatic Inheritance**: Child case files/tasks inherit parent's PropagationContext (trace ID preserved)
- **Child CaseFile Spawning**: TaskDefinition can spawn child case files via `createChildCaseFile()` with full context
- **Budget Enforcement**: Child deadline capped at parent's remaining budget; never exceeds parent
- **Hierarchical Cancellation**: Cancelling a parent propagates to all child case files and tasks
- **POJO Graph Navigation**: Hierarchy traversal via `getParentCase()`, `getChildCases()`, `getOwningCase()`, `getChildTasks()` — no separate LineageService

**Observability & Control (§6):**
- **Case Plan Model**: CasePlanModel holds scheduling agenda (PlanItems), focus, strategy
- **Planning Strategies**: Planning Strategies reason about priority, focus, strategy on CasePlanModel
- **Scheduling Agenda**: PlanItems prioritized by Planning Strategies, executed in priority order by CaseEngine
- **Default Control**: Built-in `DefaultPlanningStrategy` provides equal-priority baseline
- **Observability**: Micrometer metrics (incl. DLQ, poison pill, retry), health checks, structured logging with hierarchical MDC

**Resilience (§7):**
- **Error Handling**: Task definition exceptions prevent partial writes; Planning Strategy failures fall back to DefaultPlanningStrategy
- **Timeout Enforcement**: TimeoutEnforcer actively monitors all case files, PlanItems, and tasks
- **PlanItem Lifecycle States**: PlanItems include FAULTED and WAITING states in lifecycle
- **Retry Policy**: Configurable per-task-definition and per-Task retry with backoff (fixed, exponential, exponential+jitter)
- **Dead Letter Queue**: Failed items preserved with full context for inspection, replay, or discard
- **Poison Pill Protection**: PoisonPillDetector quarantines consistently-failing task definition/task types
- **CaseFile Versioning**: Every CaseFile write versioned; `putIfVersion()` for optimistic concurrency
- **Idempotency**: IdempotencyService prevents duplicate task execution and task definition contribution
- **Conflict Resolution**: Configurable strategies (LAST_WRITER_WINS, FIRST_WRITER_WINS, MERGE, FAIL)

**Agentic AI Specifics (§8):**
- **Quarkus Integration**: CDI dependency injection, `@CaseType` annotation, configuration
- **Security**: API key authentication for case workers, RBAC for requestors
- **Concurrency**: Thread-safe operation with versioned case file writes and atomic state transitions
- **Testing**: 80%+ coverage with unit, integration, concurrency, propagation, and resilience tests
- **Documentation**: README, API docs, legal case analysis with child case files example, sentiment example

### Phase 2 (Weeks 3-4) - Persistence & Distributed
- Redis storage provider for CaseFile, Task, and DLQ (additional persistence module)
- Redis-backed distributed locking for leader election (CasePlanModel)
- Optimistic locking via Redis WATCH/MULTI for case file writes
- CaseFile crash recovery from persistent state
- Progress notifications for long-running task definitions and tasks
- Distributed IdempotencyService with Redis TTL
- Multi-instance deployment support
- Goal model (CaseGoal, GoalEvaluator) — see §8.2 Planned: Goal Model

### Phase 3 (Weeks 5-8) - Advanced Case Management & Control Features
- CaseFile hierarchies (nested sub-case-files)
- Conditional task definition re-activation on key updates
- Goal-to-case-file decomposition framework
- Advanced control strategies (meta-reasoning, learning from past case file solutions)
- Planning Strategies that adapt strategy mid-solve based on resource consumption
- Advanced case worker selection (capability matching, load balancing)
- Distributed tracing with OpenTelemetry

---

## 13. References

- **Erman, Hayes-Roth et al. (1980)**: "The Hearsay-II Speech-Understanding System: Integrating Knowledge to Resolve Uncertainty" — foundational blackboard architecture
- **Hayes-Roth, B. (1985)**: "A Blackboard Architecture for Control" — formalizes the Control Blackboard and Control Knowledge Sources as a separate reasoning layer
- **Nii, H.P. (1986)**: "Blackboard Systems" — comprehensive survey and taxonomy of blackboard architectures
- **POSA Vol 1 (1996)**: Buschmann et al., "Pattern-Oriented Software Architecture" — Blackboard pattern description
- **CMMN (Case Management Model and Notation)**: OMG standard for case management — provides terminology alignment for CaseFile, CasePlanModel, PlanItem, and related concepts
- **CNCF OWL**: [Link to OWL specification if available]
- **Quarkus Flow**: [Link to Quarkus Flow documentation]
- **FIPA Contract Net Protocol**: [Link for future reference]

---

## Appendix A: Architecture Overview

```
            ┌─── CaseFile Model (Collaborative) ─────────────────────────────────┐
            │                                                                     │
            │                      ┌──────────────────┐                           │
            │                      │  CasePlanModel   │ ◀──── PlanningStrategy   │
            │                      │  (Control        │        (reasons about    │
            │                      │   Workspace)     │        strategy, focus,  │
            │                      │  ┌─ agenda ──┐   │        priority)         │
┌────────┐  │                      │  │PlanItem(9) │  │                           │
│Request-│──┼──▶┌──────────────┐   │  │PlanItem(5) │  │                           │
│  or    │  │   │  CaseEngine  │──▶│  └───────────┘  │   ┌──────────────────┐    │
└────────┘  │   │ (Scheduler)  │   │  ┌─ focus ───┐   │   │ TaskDefinition   │    │
            │   │              │   │  │"entities" │   │   │  (Domain         │    │
            │   └──────┬───────┘   │  └───────────┘   │   │   Specialist)    │    │
            │          │           └──────────────────┘   │                  │    │
            │          │  activate                        │  execute()       │    │
            │          │  top PlanItem                    │    ┌─────────┐   │    │
            │          ▼                                  │    │ read    │   │    │
            │   ┌──────────────┐  onChange                │    │ case    │   │    │
            │   │  CaseFile    │──────────────────────────┼───▶│ file    │   │    │
            │   │  (Domain     │                          │    │ write   │   │    │
            │   │  Workspace)  │◀─────────────────────────┼────┘ back    │   │    │
            │   └──────────────┘                          └──────────────────┘    │
            └─────────────────────────────────────────────────────────────────────┘

            ┌─── Task Model (Request-Response) ──┐
            │                                     │
┌────────┐  │  ┌──────────────┐    ┌───────────┐  │
│Request-│──┼─▶│  TaskBroker  │◀───│Worker │  │
│  or    │  │  └──────┬───────┘    └───────────┘  │
└────────┘  │         ▼                           │
            │  ┌──────────────┐                   │
            │  │ Info Slots   │                   │
            │  └──────────────┘                   │
            └─────────────────────────────────────┘
```

---

## Appendix B: Terminology

**Fixed Inconsistencies:**
- "Requestor" (not "Requester") - used consistently
- "Worker" (not "Executive") - used consistently
- "TaskBroker" - primary service name for task model
- "CaseFile" (not "Blackboard") - CaseHub's domain workspace (aligns with CMMN terminology)
- "CasePlanModel" - Separate control workspace per Hayes-Roth 1985, aligned with CMMN
- "TaskDefinition" (not "Agent" or "Worker") - aligns with CMMN task concepts and classic blackboard literature (Erman et al.)
- "PlanningStrategy" - Reasons about control, not domain (Hayes-Roth 1985), aligned with CMMN planning
- "CaseEngine" - Scheduler/execution engine that runs the control loop
- "PlanItem" - Standard CMMN term for activation records (replaces KSAR from HEARSAY-II)
- "Information Slots" - legacy term retained for task model internal storage

---

## Appendix C: Terminology Alignment

| Concept | CaseHub | Classic Blackboard (Erman/Hayes-Roth) | CMMN | CNCF OWL | Quarkus Flow |
|---------|------------|---------------------------------------|------|----------|--------------|
| Domain Workspace | CaseFile | Blackboard | Case File | — | — |
| Control Workspace | CasePlanModel | Control Blackboard (Hayes-Roth) | Case Plan Model | — | — |
| Domain Specialist | TaskDefinition | Knowledge Source | Task (definition) | Worker | Processor |
| Control Specialist | PlanningStrategy | Control Knowledge Source | — | — | — |
| Activation Record | PlanItem | KSAR (KS Activation Record) | Plan Item | — | — |
| Scheduler | CaseEngine | Scheduler / Control Loop | Case Engine | — | — |
| Context | PropagationContext | — (not in classic model) | — | — | — |
| Hierarchy Trace | traceId / POJO graph | — | — | — | — |
| Work Unit | Task | — | Task | Job | Flow Step |
| Work ID | TaskHandle | — | — | JobHandle | Flow ID |
| Work State | TaskStatus / CaseStatus | — | — | JobStatus | Flow Status |
| Worker | Worker | — | Case Worker | Worker | Processor |
| Versioned Entry | CaseFileItem | — | Case File Item | — | — |
| Contribution | CaseFileContribution | — | — | — | — |

---

## Appendix D: Design Refinement Changelog

**Version 10.0 (2026-04-09)** - POJO Graph Refactor & Persistence Modules (Issue #6)

### CaseFile Interface
- **`getCaseFileId()` (String) → `getId()` (Long)**: Primary key is now a database-friendly Long
- **Added `getOtelTraceId()` (UUID)**: OTel trace ID shared across parent/child hierarchy
- **Added `getCaseType()`**: Case type now part of the CaseFile interface
- **Added `setStatus()`**: Mutable status for engine-driven transitions
- **Added graph relationships**: `getParentCase()`, `getChildCases()`, `getTasks()` — hierarchy is a direct POJO graph

### Task Interface
- **Task promoted to interface** (was a concrete class): `DefaultTask` is the in-core interim implementation
- **`getTaskId()` (String) → `getId()` (Long)**: Primary key is now Long; `getOtelSpanId()` (UUID) added for OTel span tracking
- **Added graph relationships**: `getOwningCase()`, `getChildTasks()` — tasks link back to owning CaseFile and child tasks

### Lineage Classes Removed
- **Deleted `LineageService`**: Lineage traversal now done via `caseFile.getParentCase()` / `getChildCases()` on the POJO graph
- **Deleted `LineageNode`** and **`LineageTree`**: No longer needed; graph structure is on the objects themselves
- **Removed `CaseEngine.getLineage()`**: Use `caseFile.getParentCase()` / `getChildCases()` directly

### PropagationContext Slimmed Down
- **Removed**: `spanId`, `parentSpanId`, `lineagePath`, `isRoot()`, `getDepth()`, `getCreatedAt()` (public)
- **Keeps**: `traceId` (W3C OTel trace ID), `inheritedAttributes`, `deadline`, `remainingBudget`
- **Added `fromStorage()` factory**: For Hibernate reconstruction of persisted PropagationContext fields

### Storage SPIs Replaced
- **Deleted `CaseFileStorageProvider`**, **`TaskStorageProvider`**, **`PropagationStorageProvider`**
- **Added `CaseFileRepository`** (`casehub-core/src/main/java/io/casehub/core/spi/`): create, createChild, findById, findByStatus, save, delete
- **Added `TaskRepository`** (`casehub-core/src/main/java/io/casehub/worker/`): create, createAutonomous, findById, findByStatus, findByWorker, save, delete

### CaseEngine
- **Injects `CaseFileRepository`** instead of instantiating `DefaultCaseFile` directly
- **Internal maps keyed by `Long`** (not String UUID)
- **`createChildCaseFile` delegates to `CaseFileRepository.createChild()`** which wires parent reference
- **Added `@PreDestroy`** for executor shutdown
- **Map cleanup** after case completes or is cancelled (prevents memory accumulation)

### New Maven Modules
- **`casehub-persistence-memory`**: In-memory implementations — `InMemoryCaseFile`, `InMemoryTask`, `InMemoryCaseFileRepository`, `InMemoryTaskRepository`. Zero external dependencies; used for fast tests and local dev.
- **`casehub-persistence-hibernate`**: JPA/Panache implementations — `HibernateCaseFile`, `HibernateTask`, `HibernateCaseFileRepository`, `HibernateTaskRepository`. Workspace stored as JSON TEXT blob; H2 for tests, PostgreSQL for production.
- **`casehub-examples`** now depends on `casehub-persistence-memory`

### Planned
- **Goal model (Issue #7)**: `CaseGoal` with satisfaction/abandonment predicates, `GoalEvaluator`, named `Milestone`s. Research in `docs/research/goal-model-research.md`. Cases with no Goal fall back to quiescence-based completion (backward compatible).

---

**Version 9.0 (2026-03-28)** - Multi-Module Architecture & Workflow Integration

### Project Structure
- **Refactored to multi-module Maven project**: Separated into `casehub-core`, `casehub-examples`, and optional workflow modules
- **Added §0.5 Project Structure**: New section documenting module organization, dependencies, and isolation strategy
- **Module dependency isolation**: Core framework has zero workflow engine dependencies; workflow integrations are opt-in via separate modules

### Workflow Integration Module (casehub-flow-worker)
- **Added casehub-flow-worker module**: Optional module for Quarkus Flow workflow integration (quarkus-flow 0.7.1)
- **Added FlowWorker**: Worker implementation that executes Quarkus Flow workflows from CaseHub tasks
- **Added FlowWorkflowDefinition**: Interface for defining executable workflows that integrate with CaseHub
- **Added FlowExecutionContext**: Context object providing task inputs, metadata, and PropagationContext to workflows
- **Added FlowWorkflowRegistry**: CDI-based registry for workflow definitions
- **Added QuarkusFlowDocumentWorkflow**: Example workflow using Quarkus Flow programmatic API
- **Added DocumentFunctions**: Example workflow step functions as `@ApplicationScoped` beans
- **Added FlowWorkerQuarkusDemo**: Quarkus runtime demo validating end-to-end workflow execution
- **Validated integration**: All 4 workflow steps (extractText, recognizeEntities, analyzeSentiment, generateSummary) execute successfully with data flow between steps
- **Version alignment**: Quarkus 3.32.2 aligned with quarkus-flow 0.7.1

### Documentation Updates
- **Updated §8.4 End-to-End Examples**: Added complete example of Quarkus Flow integration showing workflow definition, execution, and task submission
- **Added workflow module benefits**: Documented isolated dependencies, multi-engine support, and standard Worker pattern integration

### Storage Provider Implementations
- **Added InMemoryCaseFileStorage**: In-memory implementation of CaseFileStorageProvider SPI
- **Added InMemoryTaskStorage**: In-memory implementation of TaskStorageProvider SPI
- **Added InMemoryPropagationStorage**: In-memory implementation of PropagationStorageProvider SPI

### Build System
- **Parent POM structure**: Centralized dependency and plugin management in root `pom.xml`
- **Module-specific dependencies**: Each module declares only its required dependencies
- **Quarkus version**: Standardized on Quarkus 3.32.2 across all modules

---

**Version 8.0 (2026-03-26)** - CMMN Terminology Alignment

### Terminology Renames
- **Renamed all classes and interfaces** to align with CMMN (Case Management Model and Notation) and OWL terminology
- **Board → CaseFile**: Domain workspace renamed to align with CMMN Case File concept
- **BoardStatus → CaseStatus**: Lifecycle enum renamed; states updated to OWL-aligned values (PENDING, RUNNING, WAITING, SUSPENDED, COMPLETED, FAULTED, CANCELLED)
- **BoardChangeEvent → CaseFileItemEvent**: Change event renamed to align with CMMN Case File Item events
- **BoardType → CaseType**: Annotation renamed
- **KnowledgeSource → TaskDefinition**: Domain specialists renamed to align with CMMN task concepts
- **KnowledgeSourceRegistry → TaskDefinitionRegistry**: Registry renamed
- **ActivationEngine → ListenerEvaluator**: Entry criteria evaluation renamed
- **BoardController → CaseEngine**: Scheduler renamed to align with CMMN Case Engine
- **ControlBoard → CasePlanModel**: Control workspace renamed to align with CMMN Case Plan Model
- **ControlKnowledgeSource → PlanningStrategy**: Control specialists renamed
- **DefaultControlKS → DefaultPlanningStrategy**: Default control renamed
- **DefaultControlBoard → DefaultCasePlanModel**: Default control workspace renamed
- **KSAR → PlanItem**: Activation records renamed to align with CMMN Plan Item
- **Executor → Worker**: Workers renamed to align with CMMN Case Worker
- **ExecutorRegistry → WorkerRegistry**: Worker registry renamed
- **ExecutorSelectionStrategy → WorkerSelectionStrategy**: Selection strategy renamed
- **BoardStorageProvider → CaseFileStorageProvider**: Storage SPI renamed
- **BoardCreationException → CaseCreationException**: Exception renamed
- **ContributionRecord → CaseFileContribution**: Contribution tracking renamed
- **VersionedValue → CaseFileItem**: Versioned entries renamed to align with CMMN Case File Item
- **contribute() → execute()**: Primary method renamed
- **requiredKeys() → entryCriteria()**: Precondition method renamed to align with CMMN entry criteria
- **evaluateAndCreateKsars() → evaluateAndCreatePlanItems()**: Evaluation method renamed
- **Lifecycle enum values**: CREATED→PENDING, ACTIVE→RUNNING, QUIESCENT→WAITING, EXECUTING→RUNNING, FAILED→FAULTED, TIMEOUT absorbed into FAULTED (via ErrorInfo), RETRY_PENDING→WAITING

### References
- Added CMMN reference to §13
- Updated Appendix C terminology alignment table with CMMN column
- Blackboard (CaseHub) references updated in prose

---

**Version 7.0 (2026-03-26)** - Document Restructuring

### Structural Reorganization
- **Restructured entire document** around six conceptual pillars: Case Management Core, Workers, Coordination, Observability & Control, Resilience, Agentic AI Specifics
- **Added document structure table** in Executive Summary for navigation
- **Consolidated CaseFile Model and Task Model** content into appropriate pillars rather than mixing by API/architecture layer
- **Moved Control component** (CasePlanModel, PlanningStrategy, PlanItems) into Observability & Control pillar alongside logging, metrics, and health checks
- **Moved Security** from standalone section into Agentic AI Specifics (§8.3)
- **Architecture overview diagram** moved to Appendix A
- **All technical content preserved** — no interfaces, APIs, or specifications were removed

---

**Version 6.0 (2026-03-26)** - Resilience: Timeout, Retry, Dead Letter, Distributed Consistency

### Timeout Enforcement (§7.2)
- **Added TimeoutEnforcer**: Quarkus Scheduler component that actively monitors all in-flight work (case files, PlanItems, tasks) against configured deadlines
- **Added PlanItem FAULTED state**: PlanItems now include FAULTED and WAITING states
- **Per-task-definition timeout configuration**: TaskDefinition.getExecutionTimeout() with application.properties overrides
- **Waiting case file timeout**: Configurable timeout for case files in WAITING state (default: 10min)

### Retry Policy (§7.3)
- **Added RetryPolicy**: Configurable per-task-definition and per-Task retry with BackoffStrategy (FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER)
- **Added WAITING states**: PlanItem and Task state machines extended with WAITING for automatic retry
- **Added RetryState**: Tracks attempt history, backoff applied, and failure details
- **Budget-aware retry**: Retries consume parent timeout budget; never extend past PropagationContext deadline
- **TaskDefinition.getRetryPolicy()**: Per-task-definition retry configuration with sensible defaults (3 attempts, exponential+jitter)

### Dead Letter Queue & Poison Pill Protection (§7.4)
- **Added DeadLetterQueue**: Failed items preserved with full context (original input, retry history, error details) for inspection, replay, or discard
- **Added DeadLetterEntry**: Comprehensive record of failed work with PENDING_REVIEW, REPLAYED, DISCARDED statuses
- **Added PoisonPillDetector**: Circuit breaker that quarantines consistently-failing task definition/task types after threshold breach (default: 5 failures in 10min)
- **Quarantine mechanism**: Quarantined task definitions skipped by ListenerEvaluator; quarantined task types rejected by TaskBroker
- **Manual recovery**: Quarantine can be manually released; dead letter entries can be replayed after fixing underlying issue

### Distributed Consistency (§7.5)
- **Added CaseFile versioning**: Every CaseFile write versioned with CaseFileItem (value, version, writtenBy, writtenAt, instanceId)
- **Added `putIfVersion()`**: Optimistic concurrency control via conditional writes with StaleVersionException
- **Added ConflictResolver**: Pluggable conflict resolution strategies (LAST_WRITER_WINS, FIRST_WRITER_WINS, MERGE, FAIL)
- **Added IdempotencyService**: Duplicate detection and cached result retrieval for tasks and task definition contributions
- **Added CaseFileStorageProvider consistency extensions**: tryLock(), renewLock(), releaseLock() for distributed leader election
- **Defined distributed deployment model**: Leader-elected CasePlanModel writer, optimistic locking for CaseFile writes, Redis sorted sets for PlanItem agenda

---

**Version 5.0 (2026-03-26)** - Context Propagation & Hierarchical Lineage

### Context Propagation
- **Added PropagationContext**: Immutable, hierarchical context object carried by every CaseFile and Task — includes trace ID, span ID, parent span ID, lineage path, inherited attributes, and resource budget
- **Added `createChild()` factory**: Creates child contexts that inherit trace ID, append lineage, decrement budget, and merge attributes
- **Added `createChildCaseFile()`**: CaseEngine method for TaskDefinitions to spawn child case files with automatic context inheritance
- **Added budget enforcement**: Child deadline automatically capped at parent's remaining budget; never exceeds parent
- **Added hierarchical cancellation**: Cancelling a parent CaseFile/Task cascades cancellation to all descendants

### Lineage & Tracing
- **Added LineageService**: Query API for hierarchy traversal — getLineage(), getDescendants(), getFullTree(), findByTraceId()
- **Added LineageNode / LineageTree**: Data structures representing nodes and trees in the CaseFile/Task hierarchy
- **Added PropagationStorageProvider**: SPI for persisting context and lineage data
- **Updated MDC logging**: trace_id, span_id, parent_span_id added to all log entries for cross-hierarchy correlation
- **Added propagation metrics**: hierarchy depth, child case files spawned, budget exhaustions

### API Updates
- **CaseFile interface**: Added `getPropagationContext()` method
- **TaskRequest**: Added `propagationContext` field for explicit context passing
- **Task**: Added `propagationContext` field
- **CaseEngine**: Added `createChildCaseFile()` and `getLineage()` methods
- **Updated examples**: Task definitions spawning child case files and tasks with context propagation

---

**Version 4.0 (2026-03-26)** - Erman/Hayes-Roth Control Component

### Control Architecture (Hayes-Roth 1985)
- **Added CasePlanModel**: Separate control workspace paired 1:1 with each domain CaseFile — holds scheduling agenda (PlanItems), focus of attention, strategy, resource tracking
- **Added PlanningStrategy interface**: Reasons about *how* to solve (priority, focus, strategy), not about the domain. Activation conditions: ON_NEW_PLAN_ITEMS, ON_CASE_FILE_CHANGE, ON_TASK_COMPLETION, ALWAYS
- **Added PlanItem**: Prioritized activation records on the scheduling agenda, consumed by CaseEngine in priority order
- **Added DefaultPlanningStrategy**: Built-in baseline that assigns equal priority — provides backward compatibility while enabling custom control strategies
- **Refactored CaseEngine**: Now acts as scheduler/execution engine that defers control *decisions* to Planning Strategies on the CasePlanModel, per Erman et al.
- **Refactored ListenerEvaluator**: Now creates PlanItems on the CasePlanModel rather than directly returning eligible task definitions
- **Updated TaskDefinitionRegistry**: Handles both domain task definitions and planning strategy registration
- **Added control-specific metrics**: PlanItem counts, agenda depth, control cycle duration, Planning Strategy invocations
- **Updated terminology**: PlanItem, CasePlanModel, PlanningStrategy aligned with CMMN and HEARSAY-II literature
- **Updated references**: Added Erman et al. 1980, Hayes-Roth 1985, Nii 1986, POSA Vol 1

---

**Version 3.0 (2026-03-26)** - True Case Management Architecture

### Case Management Architecture Completeness
- **Added CaseFile (shared workspace)**: Shared data space where multiple task definitions collaboratively build solutions
- **Added TaskDefinition interface**: Independent specialists with declarative entry criteria and produced keys
- **Added CaseEngine**: Case management control loop
- **Added ListenerEvaluator**: Entry criteria evaluation and quiescence detection
- **Added TaskDefinitionRegistry**: Task definition registration with circular dependency validation
- **Established dual execution model**: CaseFile model (collaborative) alongside Task model (request-response)
- **Added CDI-based `@CaseType` annotation** for declarative task definition registration
- **CaseFileStorageProvider SPI**: Separate storage abstraction for case file state and contribution history
- **CaseFile lifecycle**: PENDING → RUNNING → WAITING → COMPLETED/FAULTED

---

**Version 2.0 (2026-03-26)** - Architecture Refinement

### Architecture Changes
- Split TaskBroker responsibilities into separate components
- Enhanced TaskHandle with sync/async result access, cancellation, and metadata

### New Sections Added
- Observability — logging, metrics, health checks
- Error Handling Strategy — structured error handling
- Security Considerations — authentication, authorization

### Design Decisions (Answered Open Questions)
1. Quarkus CDI Event Bus for notifications
2. Programmatic case worker registration with API keys
3. Jackson JSON for serialization
4. ConcurrentHashMap + synchronized blocks for thread safety
5. Micrometer metrics + Quarkus health checks
