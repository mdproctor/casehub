# QuarkBoard: Blackboard Architecture for Quarkus and AgenticAI

## Executive Summary

QuarkBoard is a lightweight blackboard architecture service for Quarkus-based agentic AI systems. It provides a **shared workspace** where autonomous knowledge sources collaboratively build solutions, combined with a **task coordination** layer for direct request-response work — all without requiring direct coupling between participants.

**Project Duration (MVP):** 2 weeks (+ 1 week buffer for polish)
**Target Platform:** Quarkus
**Primary Use Case:** CaseHub and general AgenticAI task coordination

### Key Architectural Principles
- **True Blackboard Semantics**: Shared workspace (Board) where multiple knowledge sources read from and contribute to an evolving solution
- **Data-Driven Activation**: Knowledge sources fire when their preconditions are met on the board, enabling opportunistic problem solving
- **Dual Execution Model**: Board model for collaborative multi-contributor problems; Task model for simple request-response work
- **Separation of Concerns**: Specialized components (BoardController, TaskBroker, registries, schedulers)
- **Observable by Default**: Built-in metrics, structured logging, and health checks
- **Secure by Default**: API key authentication for executors, RBAC for requestors
- **Thread-Safe**: Concurrent reads and writes to the shared workspace without data races
- **Pluggable Storage**: SPI-based abstraction for future Redis/PostgreSQL backends

### Document Structure

This document is organized around six conceptual pillars:

| # | Pillar | What It Covers |
|---|--------|---------------|
| §3 | **Blackboard Core** | Board (shared workspace), KnowledgeSources, ActivationEngine, storage SPI |
| §4 | **Workers** | Executors, Task model (request-response), executor lifecycle |
| §5 | **Coordination** | BoardController (control loop), context propagation, lineage & hierarchy |
| §6 | **Observability & Control** | ControlBoard, Control KS, KSARs, logging, metrics, health checks |
| §7 | **Resilience** | Error handling, timeouts, retry, dead letter, distributed consistency |
| §8 | **Agentic AI Specifics** | Dual execution model, Quarkus/CDI integration, security, end-to-end examples |

---

## 1. Problem Statement

Current agentic AI platforms lack a standalone, lightweight blackboard architecture solution that:
- Provides a **shared workspace** where multiple AI agents can collaboratively build solutions
- Enables **data-driven activation** — agents respond to state changes, not explicit assignments
- Supports both collaborative problem solving and simple task coordination
- Integrates naturally with Quarkus ecosystem
- Avoids the overhead of full agentic AI platform dependencies

**Market Research:** Initial research (Grok, Claude) found no suitable standalone Java/Quarkus solutions for building new agentic platforms with blackboard architecture.

---

## 2. Goals and Non-Goals

### Goals
1. **True Blackboard Semantics**: Shared workspace with data-driven knowledge source activation
2. **Dual Execution Model**: Board model (collaborative) + Task model (request-response)
3. **Quarkus Integration**: First-class Quarkus support with minimal dependencies
4. **Pluggable Storage**: SPI-based storage abstraction (Redis as primary target)
5. **Asynchronous Coordination**: Support decoupling between requestors and knowledge sources
6. **Incremental Evolution**: Architecture that supports future enhancements without major rewrites
7. **Terminology Alignment**: Consistency with CNCF OWL, Quarkus Flow, and classic blackboard literature where applicable

### Non-Goals (Deferred to Future Iterations)
- ❌ Complex executor selection strategies (beyond simple round-robin/random)
- ❌ Job bidding/contract net protocols (FIPA)
- ❌ Nested task hierarchies and sub-slots
- ❌ Progress notifications and partial result streaming
- ❌ Advanced merge strategies for multi-executor results
- ❌ Policy governance and execution constraints
- ❌ Sophisticated retry/timeout policies

---

## 3. Blackboard Core

The blackboard core implements the foundational pattern: a **shared workspace** where independent **knowledge sources** read and write data, activated automatically when their preconditions are met.

### 3.1 The Board (Shared Workspace)

The **Board** is the defining element of a blackboard architecture. It is a shared, key-value data space where:
- A requestor provides **initial state** (e.g., raw documents)
- Multiple knowledge sources **read** what they need and **write** their contributions
- The solution **emerges** incrementally from independent contributions
- The board is **complete** when no more knowledge sources can contribute or a terminal condition is met

```
Board: "analyze-case-42"
  ├─ raw_documents: [doc1.pdf, doc2.pdf]          ← Requestor writes initial state
  ├─ extracted_text: {doc1: "...", doc2: "..."}    ← OCR knowledge source contributes
  ├─ entities: [{name: "Acme", type: "company"}]   ← NER knowledge source contributes
  ├─ sentiment: {overall: "negative"}              ← Sentiment knowledge source contributes
  └─ case_summary: "..."                           ← Summary knowledge source reads ALL above, contributes
```

Each knowledge source works independently. No knowledge source needs to know about the others. The BoardController orchestrates activation.

#### Board Interface

```java
public interface Board {
    String getBoardId();

    // Read shared state
    <T> Optional<T> get(String key, Class<T> type);
    boolean contains(String key);
    Set<String> keys();
    Map<String, Object> snapshot();

    // Write contributions (triggers change events)
    void put(String key, Object value);
    void putIfAbsent(String key, Object value);

    // React to changes
    void onChange(String key, Consumer<BoardChangeEvent> listener);
    void onAnyChange(Consumer<BoardChangeEvent> listener);

    // Versioning (for optimistic concurrency — see §7.5)
    long getVersion();
    void putIfVersion(String key, Object value, long expectedVersion)
        throws StaleVersionException;
    long getKeyVersion(String key);

    // Context (see §5.2)
    PropagationContext getPropagationContext();

    // Lifecycle
    BoardStatus getStatus();
    Instant getCreatedAt();
    void complete();
    void fail(ErrorInfo error);
}
```

#### BoardStatus

```java
public enum BoardStatus {
    CREATED,     // Board created, not yet solving
    ACTIVE,      // Control loop running, KS being activated
    QUIESCENT,   // No more KS can fire; awaiting explicit completion or timeout
    COMPLETED,   // Successfully solved
    FAILED,      // Error during solving
    CANCELLED,   // Explicitly cancelled
    TIMEOUT      // Exceeded time limit
}
```

#### Board Lifecycle

```
                                          ┌──── KS activates ────┐
                                          ▼                      │
[CREATED] ──solve──> [ACTIVE] ──────> [ACTIVE] ──────> [QUIESCENT]
                        │                                    │
                        │              ┌─────────────────────┼────────────────┐
                        │              ▼                     ▼                ▼
                        │        [COMPLETED]            [FAILED]         [TIMEOUT]
                        │         (explicit              (KS error)
                        │          or auto)
                        ▼
                   [CANCELLED]
```

**State Transitions:**
- **CREATED → ACTIVE**: `BoardController.solve()` called; control loop begins
- **ACTIVE → ACTIVE**: Knowledge source writes to board, triggering re-evaluation of preconditions
- **ACTIVE → QUIESCENT**: No eligible knowledge sources and none in-flight; awaiting explicit completion
- **QUIESCENT → COMPLETED**: Board explicitly completed, or all known producedKeys are present
- **ACTIVE → FAILED**: Knowledge source throws unrecoverable exception after retries exhausted
- **ACTIVE/QUIESCENT → TIMEOUT**: Board exceeds configured timeout (enforced by TimeoutEnforcer — see §7.2)
- **Any non-terminal → CANCELLED**: Explicit cancellation requested (cascades to children)

**Auto-Completion (MVP)**: Board automatically completes when no more knowledge sources can fire and none are in-flight (including RETRY_PENDING KSARs). Requestor can also call `board.complete()` explicitly.

### 3.2 Knowledge Sources (Specialists)

A **KnowledgeSource** is an independent specialist that:
- Declares **preconditions**: what keys must exist on the board before it can activate
- Declares **produced keys**: what it will contribute to the board
- **Reads** from the board, performs work, and **writes** results back
- Is **activated opportunistically** by the BoardController when preconditions are satisfied

```java
public interface KnowledgeSource {
    /** Unique identifier for this knowledge source */
    String getId();

    /** Human-readable name */
    String getName();

    /**
     * Preconditions: keys that must exist on the board before this KS can activate.
     * BoardController evaluates this on every board change.
     */
    Set<String> requiredKeys();

    /**
     * Keys this KS will produce. Used to:
     * - Detect circular dependencies at registration time
     * - Track provenance of contributions
     * - Determine when no more progress is possible
     */
    Set<String> producedKeys();

    /**
     * Optional: fine-grained activation check beyond key presence.
     * Called only if requiredKeys() are all present.
     * Default returns true.
     */
    default boolean canActivate(Board board) {
        return true;
    }

    /**
     * Execute and contribute results to the board.
     * Called by BoardController when preconditions are met.
     * Must write produced keys to the board before returning.
     */
    void contribute(Board board);

    /**
     * Maximum time this KS is allowed to execute.
     * TimeoutEnforcer interrupts execution if exceeded (see §7.2).
     * Default: 5 minutes.
     */
    default Duration getExecutionTimeout() {
        return Duration.ofMinutes(5);
    }

    /**
     * Retry policy for this KS (see §7.3).
     * Default: 3 attempts with exponential backoff.
     */
    default RetryPolicy getRetryPolicy() {
        return RetryPolicy.defaults();
    }
}
```

#### KnowledgeSourceRegistry

Registration and lookup of knowledge sources.

```java
@ApplicationScoped
public class KnowledgeSourceRegistry {
    // Domain Knowledge Sources
    public void register(KnowledgeSource ks, Set<String> boardTypes)
        throws CircularDependencyException;
    public void unregister(String ksId);
    public List<KnowledgeSource> getForBoardType(String boardType);
    public Optional<KnowledgeSource> getById(String ksId);

    // Control Knowledge Sources (see §6.2)
    public void registerControl(ControlKnowledgeSource cks, Set<String> boardTypes);
    public void unregisterControl(String cksId);
    public List<ControlKnowledgeSource> getControlKsForBoardType(String boardType);
}
```

**Circular Dependency Detection**: Validated at registration time. Example: KS-A requires "x", produces "y"; KS-B requires "y", produces "x" → valid chain, not circular. Circular: mutual dependency where neither can ever fire → rejected with `CircularDependencyException`.

### 3.3 Activation Engine

Precondition evaluation and KSAR creation.

```java
@ApplicationScoped
public class ActivationEngine {

    /**
     * Evaluate which knowledge sources are newly eligible given current board state.
     * Creates KSARs for each eligible KS and adds them to the ControlBoard agenda.
     *
     * @param domainBoard   The domain board to evaluate preconditions against
     * @param controlBoard  The control board to add KSARs to
     * @param registered    All registered domain KS for this board type
     * @param triggerKey    The board key that changed (null for initial evaluation)
     * @return              Newly created KSARs
     */
    public List<KSAR> evaluateAndCreateKsars(Board domainBoard, ControlBoard controlBoard,
                                              List<KnowledgeSource> registered,
                                              String triggerKey);

    /**
     * Check if the board has reached quiescence (no more progress possible).
     * True when: agenda is empty AND no KSARs are EXECUTING.
     */
    public boolean isQuiescent(ControlBoard controlBoard);
}
```

- On each board change, evaluates which domain KS preconditions are now satisfied
- Creates KSARs (see §6.3) for newly eligible KS and adds them to the ControlBoard's agenda
- Filters out KS that have already contributed (unless re-activation is enabled)
- Skips quarantined KS (see §7.4 Poison Pill Protection)
- Detects quiescence: no KSARs on agenda and none in-flight

### 3.4 Storage SPI

Pluggable storage abstractions for both Board and Task models.

#### BoardStorageProvider

Persists board state and change history.

```java
public interface BoardStorageProvider {
    // Board CRUD
    void createBoard(String boardId, Map<String, Object> initialState);
    Optional<Map<String, Object>> retrieveBoard(String boardId);
    void updateBoardStatus(String boardId, BoardStatus newStatus);
    void deleteBoard(String boardId);

    // Key-value operations (the board's shared workspace)
    void putKey(String boardId, String key, Object value);
    Optional<Object> getKey(String boardId, String key);
    Set<String> getKeys(String boardId);
    Map<String, Object> getSnapshot(String boardId);

    // Provenance tracking
    void recordContribution(String boardId, String ksId, Set<String> keys, Instant timestamp);
    List<ContributionRecord> getContributionHistory(String boardId);

    // Consistency extensions (see §7.5)
    boolean putIfVersion(String boardId, String key, Object value, long expectedVersion);
    Optional<VersionedValue> getVersioned(String boardId, String key);
    Optional<LockHandle> tryLock(String boardId, Duration ttl);
    boolean renewLock(LockHandle handle, Duration ttl);
    void releaseLock(LockHandle handle);

    // Lifecycle
    void cleanup(Predicate<BoardMetadata> shouldDelete);
}
```

#### TaskStorageProvider

Persists task state for the request-response model.

```java
public interface TaskStorageProvider {
    // Task CRUD
    void storeTask(String taskId, TaskData data);
    Optional<TaskData> retrieveTask(String taskId);
    void updateTaskStatus(String taskId, TaskStatus newStatus);
    void delete(String taskId);

    // Result management
    void storeResult(String taskId, TaskResult result);
    Optional<TaskResult> retrieveResult(String taskId);

    // Queries
    List<TaskData> findTasksByStatus(TaskStatus status);
    List<TaskData> findTasksByExecutor(String executorId);

    // Lifecycle
    void cleanup(Predicate<TaskData> shouldDelete);
}
```

#### PropagationStorageProvider

Persists context propagation and lineage data (see §5.2).

```java
public interface PropagationStorageProvider {
    void storeContext(String spanId, PropagationContext context);
    Optional<PropagationContext> retrieveContext(String spanId);
    List<PropagationContext> findByTraceId(String traceId);
    List<PropagationContext> findByParentSpanId(String parentSpanId);
    void deleteByTraceId(String traceId);  // Cleanup entire hierarchy
}
```

#### Concurrency Model (MVP)

- Thread-safe using `ConcurrentHashMap`
- Board key writes trigger CDI change events before returning
- Atomic status transitions using synchronized blocks
- Future implementations must provide equivalent concurrency guarantees

**MVP Implementation**: `InMemoryBoardStorage`, `InMemoryTaskStorage`, `InMemoryPropagationStorage` with Quarkus CDI Event Bus
**Future**: Redis, PostgreSQL, Cassandra backends

### 3.5 Information Slots

Key-value storage concept that manifests differently in each model:

**Board Model**: The Board itself **is** the information slot — a shared workspace that knowledge sources read from and write to directly via `Board.get()` and `Board.put()`. This is the canonical blackboard pattern.

**Task Model**: Slots are an **internal implementation detail** of TaskRegistry. Accessed only through `TaskRequest.context` (input) and `TaskResult.data` (output).

**Storage Model (both models):**
- Lifecycle managed by the owning component (BoardRegistry or TaskRegistry)
- Cleanup on completion, cancellation, error, or timeout (configurable TTL)
- Thread-safe concurrent access via ConcurrentHashMap

---

## 4. Workers

Workers are the execution units — both the **executors** that perform work and the **task model** that coordinates simple request-response interactions.

### 4.1 Executor Registry

Used by both Board model (KS may delegate to executors) and Task model.

```java
@ApplicationScoped
public class ExecutorRegistry {

    /**
     * Register executor with capabilities and obtain executor ID.
     * Capabilities used for task matching (e.g., ["sentiment-analysis", "java-17"]).
     * Requires API key for authentication (configured in application.properties).
     */
    public String register(String executorName, Set<String> capabilities, String apiKey)
        throws UnauthorizedException;

    /**
     * Unregister executor and mark all assigned tasks as FAILED.
     */
    public void unregister(String executorId);

    /**
     * Heartbeat to indicate executor is still alive (30s timeout).
     * Must be called at least every 20s to avoid timeout.
     */
    public void heartbeat(String executorId);

    /**
     * Poll for next available task matching executor capabilities.
     * Returns empty if no tasks available.
     * Marks task as ASSIGNED and transitions to EXECUTING when claimed.
     */
    public Optional<Task> claimTask(String executorId);

    /**
     * Submit successful result.
     * Task marked as COMPLETED and result stored for requestor retrieval.
     */
    public void submitResult(String executorId, String taskId, TaskResult result)
        throws UnauthorizedException;

    /**
     * Report execution failure with structured error information.
     * Task marked as FAILED with error details.
     */
    public void reportFailure(String executorId, String taskId, ErrorInfo error)
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
     * Task enters PENDING state and is queued for executor selection.
     */
    public TaskHandle submitTask(TaskRequest request) throws TaskSubmissionException;

    /**
     * Submit task with explicit timeout.
     * If no executor completes within timeout, task marked as TIMEOUT.
     */
    public TaskHandle submitTask(TaskRequest request, Duration timeout) throws TaskSubmissionException;

    /**
     * Cancel a running or pending task.
     * Returns true if cancellation successful, false if already completed/failed.
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
    private Set<String> requiredCapabilities;     // Executor requirements
    private Optional<Duration> timeout;           // Time limit (default: 5 minutes)
    private Optional<String> idempotencyKey;      // For duplicate detection
    private PropagationContext propagationContext; // Context propagation (see §5.2)
    private RetryPolicy retryPolicy;              // Default: RetryPolicy.defaults()

    public static TaskRequestBuilder builder() { ... }
}

public class Task {
    private String taskId;                        // UUID generated by TaskBroker
    private String taskType;
    private Map<String, Object> context;
    private Set<String> requiredCapabilities;
    private TaskStatus status;
    private Instant submittedAt;
    private Optional<String> assignedExecutorId;
    private Optional<Instant> assignedAt;
    private PropagationContext propagationContext;
}

public class TaskResult {
    private String taskId;
    private TaskStatus status;
    private Map<String, Object> data;             // Output data (JSON-serializable)
    private Optional<ErrorInfo> error;            // Present only if FAILED
    private Instant completedAt;
    private Optional<String> executorId;          // Which executor produced result

    public static class ErrorInfo {
        private String errorCode;                 // e.g., "EXECUTOR_TIMEOUT", "VALIDATION_FAILED"
        private String message;                   // Human-readable description
        private boolean retryable;                // Should requestor retry?
        private Instant timestamp;
        private Optional<String> executorId;      // Which executor failed
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
    Optional<String> getAssignedExecutor();
}
```

#### TaskStatus

```java
public enum TaskStatus {
    PENDING,         // Submitted, awaiting executor
    ASSIGNED,        // Matched to executor
    EXECUTING,       // Executor processing
    COMPLETED,       // Successfully finished
    FAILED,          // Retries exhausted
    CANCELLED,       // Explicitly cancelled
    TIMEOUT,         // Exceeded time limit
    RETRY_PENDING    // Failed, awaiting retry after backoff
}
```

### 4.3 Task Lifecycle

```
[PENDING] ──select──> [ASSIGNED] ──execute──> [EXECUTING]
                                                    │
                          ┌─────────────────────────┼────────────────┐
                          ▼                         │                ▼
                    [COMPLETED]                     ▼           [TIMEOUT]
                                            [RETRY_PENDING]
                                              │         │
                                     retry ◀──┘         └──▶ retries exhausted
                                      ▼                           ▼
                                  [PENDING]                   [FAILED]
                                                                  │
                                                                  ▼
                                                          [DEAD LETTER]

[CANCELLED] (can occur from PENDING, ASSIGNED, EXECUTING, or RETRY_PENDING)
```

#### Task Model Components

- **TaskBroker**: Orchestration entry point — delegates to TaskRegistry for storage and TaskScheduler for routing
- **TaskRegistry**: Task storage and lifecycle management — stores metadata, manages state transitions, handles cleanup
- **TaskScheduler**: Task-to-executor matching — selects available executor, marks task as ASSIGNED

### 4.4 Executor Selection Strategy

```java
public interface ExecutorSelectionStrategy {
    Optional<Executor> selectExecutor(Task task, List<Executor> availableExecutors);
}
```

**MVP Implementation**: Simple round-robin or random selection
**Future**: Capability matching, load balancing, bidding protocols

### 4.5 Cleanup Policy

**Board Model:**
- **On COMPLETED**: Board state retained for configurable TTL (default: 1 hour) for result retrieval
- **On FAILED/TIMEOUT**: Board state retained for TTL for debugging, then deleted
- **On CANCELLED**: Board state deleted after brief grace period (5 minutes)

**Task Model:**
- **On COMPLETED**: Delete slot after result retrieved by requestor
- **On FAILED**: Delete slot after error notification
- **On CANCELLED**: Delete slot immediately
- **On TIMEOUT**: Delete slot after timeout notification

**Future**: Configurable retention policies, audit logging

### 4.6 Notification Service

Event dispatch for both models.

- Publishes board change events (key added/updated)
- Publishes task lifecycle events
- Notifies requestors of board completion or task result
- **MVP Implementation**: Quarkus CDI Event Bus

**Serialization (MVP Decision)**:
- **Jackson JSON** for all `Map<String, Object>` serialization
- Quarkus default, handles complex types well
- Context and data must contain only JSON-serializable types
- Custom objects require `@RegisterForReflection` for native builds
- Future: Consider Protocol Buffers for high-throughput scenarios (Phase 4)

---

## 5. Coordination

Coordination is the **mechanism** layer — how work flows through the system, how parent-child relationships are maintained, and how the control loop orchestrates knowledge source execution.

### 5.1 BoardController (Scheduler)

The **BoardController** is the execution engine — it runs the control loop but defers *control decisions* to the ControlBoard and its Control KS (see §6):

1. Detect domain Board change
2. Evaluate which domain KS preconditions are now satisfied → create KSARs
3. **Invoke Control KS** to reason about focus, priority, and strategy → Control KS update ControlBoard
4. Read the agenda from ControlBoard, select highest-priority KSAR(s)
5. Activate the selected domain KS
6. Domain KS reads board, contributes, writes back → triggers step 1
7. Repeat until board is complete, quiescent, or timed out

#### Requestor API

```java
@ApplicationScoped
public class BoardController {

    /**
     * Create a board with initial state and begin solving.
     * Board type determines which domain and control KS are eligible.
     * A paired ControlBoard is created automatically.
     * Returns immediately; solving is asynchronous.
     */
    public Board createAndSolve(String boardType, Map<String, Object> initialState)
        throws BoardCreationException;

    /**
     * Create a board with initial state and timeout.
     * Board marked as TIMEOUT if not completed within duration.
     */
    public Board createAndSolve(String boardType, Map<String, Object> initialState,
                                 Duration timeout) throws BoardCreationException;

    /**
     * Wait for a board to reach a terminal state (COMPLETED, FAILED, TIMEOUT).
     */
    public Board awaitCompletion(Board board, Duration timeout)
        throws InterruptedException, TimeoutException;

    /**
     * Create a child board from within a KnowledgeSource.
     * PropagationContext is automatically inherited from the parent board.
     * Child deadline is capped at parent's remaining budget.
     */
    public Board createChildBoard(Board parentBoard, String boardType,
                                   Map<String, Object> initialState);

    /**
     * Cancel a running board. All in-flight KS executions and child boards are cancelled.
     * Cancellation propagates down the entire hierarchy.
     */
    public boolean cancel(Board board);

    /**
     * Get a read-only snapshot of the domain board's current state.
     */
    public Map<String, Object> getSnapshot(Board board);

    /**
     * Access the control board for inspection (current strategy, focus, agenda).
     * Useful for debugging and monitoring control decisions.
     */
    public ControlBoard getControlBoard(Board board);

    /**
     * Query the lineage of a board — all ancestors and descendants.
     */
    public LineageTree getLineage(Board board);
}
```

### 5.2 Context Propagation

When a Board spawns child Boards, or when a KnowledgeSource submits Tasks that themselves spawn sub-Tasks, **context must propagate** through the entire hierarchy. Without this, it's impossible to:
- Trace a leaf-level result back to the originating Goal
- Understand why a particular sub-Task was created
- Aggregate results across a hierarchy of related work
- Apply consistent timeouts, security policies, or resource budgets across a chain

#### PropagationContext

Every Board and Task carries a **PropagationContext** — an immutable, hierarchical context that flows from parent to child:

```
Goal: "Analyze legal case #42"                          ← PropagationContext (root)
  └─ Board: "legal-case-analysis"                       ← inherits root context
       ├─ KS: OCR extracts text                        ← inherits board context
       │    └─ Task: "ocr-page-1"                      ← inherits KS context
       │    └─ Task: "ocr-page-2"                      ← inherits KS context
       ├─ KS: NER extracts entities                    ← inherits board context
       │    └─ Board: "entity-disambiguation"           ← child board, inherits parent board context
       │         ├─ KS: WikidataLookup                  ← inherits child board context
       │         └─ KS: ContextualDisambiguation        ← inherits child board context
       └─ KS: Summary writes case_summary              ← inherits board context
```

The PropagationContext carries:
- **Trace ID**: Single unique ID for the entire hierarchy (root to leaf), enabling distributed tracing
- **Span ID**: Unique ID for the current node in the hierarchy
- **Parent Span ID**: Link back to the parent (Board or Task that spawned this one)
- **Lineage Path**: Ordered list of ancestor IDs from root to current node
- **Inherited Attributes**: Key-value pairs that flow from parent to child (e.g., security principal, tenant ID, deadline)
- **Resource Budget**: Remaining time/compute budget, decremented as it propagates down

```java
public class PropagationContext {
    // Tracing — correlates the entire hierarchy
    private final String traceId;           // Single ID for root-to-leaf (UUID, set at root)
    private final String spanId;            // Unique ID for this node (UUID, set per Board/Task)
    private final Optional<String> parentSpanId;  // Link to parent (empty for root)
    private final List<String> lineagePath; // Ordered ancestor IDs: [root, ..., parent]

    // Inherited attributes — flow from parent to child automatically
    private final Map<String, String> inheritedAttributes;  // e.g., security principal, tenant ID

    // Resource budget — enforced down the hierarchy
    private final Optional<Instant> deadline;        // Absolute deadline (inherited, never extended)
    private final Optional<Duration> remainingBudget; // Remaining time (decremented per level)

    // Factory methods
    public static PropagationContext createRoot();
    public static PropagationContext createRoot(Map<String, String> attributes);

    /**
     * Create a child context inheriting from this parent.
     * - traceId: inherited (same across entire hierarchy)
     * - spanId: newly generated UUID
     * - parentSpanId: set to this context's spanId
     * - lineagePath: appended with this context's spanId
     * - inheritedAttributes: merged (child can add, not remove)
     * - deadline: inherited (never extended)
     * - remainingBudget: decremented by elapsed time since parent started
     */
    public PropagationContext createChild();
    public PropagationContext createChild(Map<String, String> additionalAttributes);

    // Queries
    public boolean isRoot();
    public int getDepth();                     // 0 for root, 1 for first child, etc.
    public boolean isBudgetExhausted();        // True if deadline passed or budget <= 0
    public Optional<String> getAttribute(String key);
}
```

#### Context Inheritance Rules

1. **Automatic inheritance**: Trace ID, security principal, tenant ID, deadline/timeout budget
2. **Explicit opt-in**: Domain-specific attributes set by the parent (e.g., `case_id`, `priority_override`)
3. **Never inherited**: Internal state, mutable data, implementation details
4. **Budget propagation**: Parent's remaining timeout is the *maximum* a child can use; children cannot exceed their parent's budget

#### Propagation Rules

**1. Board Creation (Root)**
When a requestor creates a top-level Board, a root PropagationContext is created automatically:
```java
Board board = boardController.createAndSolve("legal-case-analysis", initialState);
// board.getPropagationContext().isRoot() == true
// board.getPropagationContext().getTraceId() == "abc-123" (new UUID)
```

**2. KnowledgeSource → Child Board**
When a KnowledgeSource spawns a child Board during `contribute()`, the child inherits context:
```java
public class EntityDisambiguationKS implements KnowledgeSource {
    @Inject BoardController boardController;

    @Override
    public void contribute(Board parentBoard) {
        List<Entity> ambiguous = parentBoard.get("raw_entities", List.class).orElseThrow();

        // Child board inherits parent's PropagationContext automatically
        Board childBoard = boardController.createChildBoard(
            parentBoard,                    // parent — context propagated
            "entity-disambiguation",        // board type
            Map.of("ambiguous_entities", ambiguous)  // initial state
        );

        Board resolved = boardController.awaitCompletion(childBoard, Duration.ofMinutes(2));
        parentBoard.put("entities", resolved.snapshot().get("resolved_entities"));
    }
}
// childBoard.getPropagationContext().getParentSpanId() == parentBoard spanId
// childBoard.getPropagationContext().getTraceId() == parentBoard traceId (same!)
// childBoard.getPropagationContext().getDepth() == 1
```

**3. KnowledgeSource → Task**
When a KnowledgeSource submits a Task (using the Task model for simple work), context propagates:
```java
public class OcrKnowledgeSource implements KnowledgeSource {
    @Inject TaskBroker taskBroker;

    @Override
    public void contribute(Board board) {
        List<Document> docs = board.get("raw_documents", List.class).orElseThrow();
        List<String> results = new ArrayList<>();

        for (Document doc : docs) {
            // Task inherits board's PropagationContext
            TaskHandle handle = taskBroker.submitTask(
                TaskRequest.builder()
                    .taskType("ocr")
                    .context(Map.of("document", doc))
                    .propagationContext(board.getPropagationContext().createChild())
                    .build()
            );
            results.add(taskBroker.awaitResult(handle, Duration.ofMinutes(1))
                .getData().get("text").toString());
        }
        board.put("extracted_text", results);
    }
}
```

**4. Executor → Sub-Task**
When an Executor processing a Task submits sub-Tasks, context flows automatically:
```java
public class OcrExecutor {
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
// Root board with 10-minute deadline
Board root = boardController.createAndSolve("analysis", state, Duration.ofMinutes(10));
// root.getPropagationContext().getDeadline() == now + 10min

// 3 minutes later, KS spawns child board
// childContext.getRemainingBudget() == 7min (automatically decremented)
// childContext.getDeadline() == same absolute deadline as root

// Attempting to set a child deadline beyond parent's is silently capped
```

### 5.3 Lineage & Hierarchy

#### Lineage Query API

```java
@ApplicationScoped
public class LineageService {

    /**
     * Get the full lineage of a Board or Task — all ancestors from root to current.
     */
    public List<LineageNode> getLineage(String spanId);

    /**
     * Get all descendants of a Board or Task.
     */
    public List<LineageNode> getDescendants(String spanId);

    /**
     * Get the full tree for a trace — root to all leaves.
     */
    public LineageTree getFullTree(String traceId);

    /**
     * Find all Boards and Tasks sharing the same trace (part of the same hierarchy).
     */
    public List<LineageNode> findByTraceId(String traceId);
}

public class LineageNode {
    private String spanId;
    private Optional<String> parentSpanId;
    private String traceId;
    private LineageNodeType type;           // BOARD, TASK
    private String typeId;                  // boardId or taskId
    private String typeName;               // boardType or taskType
    private Instant createdAt;
    private Optional<Instant> completedAt;
    private Object status;                 // BoardStatus or TaskStatus

    public enum LineageNodeType { BOARD, TASK }
}

public class LineageTree {
    private LineageNode root;
    private List<LineageTree> children;
    public int getDepth();
    public int getTotalNodes();
    public List<LineageNode> getLeaves();
}
```

#### Hierarchical Cancellation

When a parent is cancelled or times out, all descendants are cancelled (not timed out — the parent timed out, children are collateral):
1. Board TIMEOUT → all executing KSARs → CANCELLED, all child boards → CANCELLED, all child tasks → CANCELLED
2. Board TIMEOUT → ControlBoard agenda cleared
3. PropagationContext deadline ensures children cannot outlive parents

---

## 6. Observability and Control

This pillar combines two concerns: the **Control component** from Hayes-Roth (strategic reasoning about *how* to solve) and **observability** (logging, metrics, health checks that make the system transparent).

### 6.1 Control Board (Control Blackboard)

QuarkBoard implements the canonical blackboard architecture as formalized by Erman et al. (HEARSAY-II, 1980) and Hayes-Roth (1985). Critically, this includes an explicit **Control component** where control itself is treated as a knowledge-based problem-solving activity with its own blackboard.

#### The Four Pillars of Blackboard Architecture

| Pillar | Classic Name | QuarkBoard Component | Purpose |
|--------|-------------|---------------------|---------|
| **Domain Workspace** | Blackboard | `Board` (§3.1) | Shared data space where partial solutions accumulate |
| **Specialists** | Knowledge Sources | `KnowledgeSource` (§3.2) | Independent domain contributors activated by board state |
| **Control** | Control Component | `ControlBoard` + `ControlKnowledgeSource` | Reasons about *how* to solve: focus, strategy, scheduling |
| **Scheduler** | Scheduler / Control Loop | `BoardController` (§5.1) | Executes control decisions — activates KS, manages lifecycle |

The key insight from Hayes-Roth is that **control is itself a problem-solving activity**. Rather than hardcoding a fixed control strategy (e.g., "activate all eligible KS"), the Control Blackboard allows control knowledge sources to reason dynamically about:
- **Focus of Attention**: Which part of the solution space deserves effort right now?
- **Strategy**: Should we pursue depth-first, breadth-first, or focus on a specific hypothesis?
- **Scheduling**: When multiple KS are eligible, which should run first? In parallel or sequentially?
- **Resource Allocation**: How much compute/time budget remains? Should we curtail exploration?

#### ControlBoard Interface

The **ControlBoard** is a separate blackboard dedicated to control reasoning. While the domain Board holds the evolving solution, the ControlBoard holds the evolving *strategy* for how to solve it:

```
ControlBoard (for board "analyze-case-42"):
  ├─ focus: "entity-extraction"                    ← Where to direct attention
  ├─ strategy: "depth-first"                       ← Current solving approach
  ├─ agenda: [KSAR(ner, pri=9), KSAR(sent, pri=5)] ← Prioritized pending activations
  ├─ resource_budget: {time_remaining: 8m, ...}     ← Resource constraints
  └─ rationale: "Entities needed before summary"    ← Explanation of control decisions
```

The ControlBoard is paired 1:1 with each domain Board instance and managed by the BoardController.

```java
public interface ControlBoard {
    String getControlBoardId();
    Board getDomainBoard();  // The paired domain board

    // Scheduling Agenda — prioritized KSARs
    void addKsar(KSAR ksar);
    void removeKsar(String ksarId);
    List<KSAR> getAgenda();                   // All pending KSARs, priority-ordered
    List<KSAR> getTopKsars(int maxCount);     // Highest priority KSARs to execute next

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

    // General key-value (extensible by custom Control KS)
    void put(String key, Object value);
    <T> Optional<T> get(String key, Class<T> type);
    Map<String, Object> snapshot();
}
```

### 6.2 Control Knowledge Sources

**ControlKnowledgeSources** reason about control — they do not contribute domain solutions. Instead, they read the domain Board and/or ControlBoard state and write **control decisions** (focus, priority, strategy) back to the ControlBoard. The BoardController then executes those decisions.

Examples of control knowledge sources:
- **FocusSelector**: Reads board state, determines which area of the solution space is most important
- **PriorityAssigner**: When multiple domain KS are eligible, assigns priorities based on urgency or expected value
- **StrategySelector**: Chooses between depth-first vs. breadth-first based on problem characteristics
- **ResourceMonitor**: Tracks time/compute budget and curtails exploration when resources are low

```java
public interface ControlKnowledgeSource {
    /** Unique identifier */
    String getId();

    /** Human-readable name */
    String getName();

    /**
     * When should this Control KS be invoked?
     * Called after KSARs are created but before they are executed.
     */
    ControlActivationCondition getActivationCondition();

    /**
     * Reason about control and update the ControlBoard.
     * Typical actions:
     * - Reprioritize KSARs on the agenda
     * - Set or change focus of attention
     * - Cancel low-value KSARs
     * - Adjust strategy based on board state
     * - Update resource budget
     *
     * @param controlBoard  The control blackboard (read/write)
     * @param domainBoard   The domain blackboard (read-only for control KS)
     */
    void reason(ControlBoard controlBoard, Board domainBoard);

    enum ControlActivationCondition {
        ON_NEW_KSARS,           // When new KSARs are added to agenda
        ON_DOMAIN_BOARD_CHANGE, // When domain board state changes
        ON_KS_COMPLETION,       // When a domain KS finishes
        ALWAYS                  // Every control cycle
    }
}
```

#### DefaultControlKS (MVP Built-in)

```java
/**
 * Default control knowledge source that assigns equal priority
 * to all eligible KSARs. Provides baseline behavior equivalent
 * to a system without explicit control.
 * Users can register additional ControlKS to override or augment.
 */
@ApplicationScoped
public class DefaultControlKS implements ControlKnowledgeSource {

    @Override
    public String getId() { return "default-control"; }

    @Override
    public String getName() { return "Default Equal-Priority Control"; }

    @Override
    public ControlActivationCondition getActivationCondition() {
        return ControlActivationCondition.ON_NEW_KSARS;
    }

    @Override
    public void reason(ControlBoard controlBoard, Board domainBoard) {
        // Default: all KSARs get priority 0 (equal), no focus filtering
        // This is a no-op — KSARs keep their default priority
    }
}
```

**MVP Simplification**: The ControlBoard is present but starts with this single built-in `DefaultControlKS` that assigns equal priority to all eligible KS. Users can register custom Control KS to add sophisticated control strategies without changing the core architecture.

#### Custom Control KS Example

```java
@ApplicationScoped
@BoardType("legal-case-analysis")
public class EntityFirstControlKS implements ControlKnowledgeSource {

    @Override
    public String getId() { return "entity-first-control"; }

    @Override
    public String getName() { return "Prioritize Entity Extraction"; }

    @Override
    public ControlActivationCondition getActivationCondition() {
        return ControlActivationCondition.ON_NEW_KSARS;
    }

    @Override
    public void reason(ControlBoard controlBoard, Board domainBoard) {
        // Strategy: prioritize entity extraction before summarization
        for (KSAR ksar : controlBoard.getAgenda()) {
            if (ksar.getKnowledgeSourceId().equals("ner-extractor")) {
                ksar.setPriority(10);  // High priority
            } else if (ksar.getKnowledgeSourceId().equals("summarizer")) {
                // Don't summarize until entities are available
                if (!domainBoard.contains("entities")) {
                    ksar.setPriority(-1);  // Deprioritize
                }
            }
        }
        controlBoard.setFocus("entity-extraction");
        controlBoard.setFocusRationale("Entities needed before downstream analysis");
    }
}
```

### 6.3 Scheduling Agenda (KSARs)

The **Scheduling Agenda** is a prioritized queue of **Knowledge Source Activation Records (KSARs)** maintained on the ControlBoard. Each KSAR represents a pending KS activation:
- Which KS is eligible
- Its assigned priority (from Control KS or default)
- The trigger event (which board key change made it eligible)
- Timestamp of eligibility

The BoardController consumes KSARs from the agenda in priority order, rather than activating all eligible KS indiscriminately.

```java
public class KSAR {
    private String ksarId;                    // Unique activation record ID
    private String knowledgeSourceId;         // Which KS to activate
    private int priority;                     // Higher = execute sooner (default: 0)
    private String triggerKey;                // Which board key change triggered eligibility
    private Instant createdAt;                // When the KS became eligible
    private Optional<String> focusArea;       // Focus area this KSAR belongs to
    private KSARStatus status;                // PENDING, EXECUTING, COMPLETED, etc.

    public enum KSARStatus {
        PENDING,         // On agenda, awaiting execution
        EXECUTING,       // Currently being executed
        COMPLETED,       // KS finished contributing
        CANCELLED,       // Removed from agenda by Control KS
        TIMEOUT,         // Deadline expired while pending or executing
        RETRY_PENDING,   // Failed, awaiting retry after backoff
        FAILED           // Retries exhausted; sent to dead letter
    }

    public static KSAR create(KnowledgeSource ks, String triggerKey) { ... }
}
```

#### KSAR Lifecycle

```
[PENDING] ──activate──> [EXECUTING] ──success──> [COMPLETED]
                              │
                          failure ──> [RETRY_PENDING] ──backoff──> [PENDING]
                              │               │
                              │         retries exhausted ──> [FAILED] ──> Dead Letter
                              │
                          timeout ──> [TIMEOUT]

[CANCELLED] (by Control KS or parent board cancellation)
```

### 6.4 Logging Strategy

- **Framework**: SLF4J with structured JSON output
- **Correlation**: `trace_id`, `span_id`, `parent_span_id` from PropagationContext added to MDC for all logs; plus `board_id` or `task_id` and `ks_id` during KS execution. This enables tracing across the full Goal → Board → KS → Task → sub-Task hierarchy
- **Levels**:
  - INFO: Board created/completed/failed, KS activated/contributed, task lifecycle events
  - DEBUG: Precondition evaluation results, executor selection, routing decisions, heartbeats
  - ERROR: KS failures, task failures, timeouts, executor crashes
  - WARN: Quiescence detected, missed heartbeats, KS rollbacks

### 6.5 Metrics (Micrometer)

QuarkBoard exposes the following metrics for monitoring:

**Board Model Counters**:
- `quarkboard_boards_created_total{board_type}` - Boards created
- `quarkboard_boards_completed_total{board_type}` - Boards completed/failed
- `quarkboard_ks_activations_total{ks_id, board_type}` - KS activations
- `quarkboard_ks_failures_total{ks_id, board_type}` - KS failures

**Board Model Gauges**:
- `quarkboard_boards_active` - Boards currently in ACTIVE state
- `quarkboard_ks_in_flight{board_type}` - KS currently executing
- `quarkboard_knowledge_sources_registered{board_type}` - Registered KS by board type

**Board Model Timers**:
- `quarkboard_board_solve_duration_seconds{board_type}` - Total board solving time
- `quarkboard_ks_contribute_duration_seconds{ks_id}` - Individual KS contribution time
- `quarkboard_activation_evaluation_duration` - Time to evaluate preconditions

**Control Metrics**:
- `quarkboard_ksars_created_total{board_type}` - KSARs created
- `quarkboard_ksars_cancelled_total{board_type}` - KSARs cancelled by Control KS
- `quarkboard_control_ks_invocations_total{cks_id}` - Control KS invocations
- `quarkboard_agenda_size{board_id}` - Current agenda depth (gauge)
- `quarkboard_control_cycle_duration` - Time for full control cycle (timer)

**Context Propagation Metrics**:
- `quarkboard_hierarchy_depth{trace_id}` - Maximum depth of the Board/Task hierarchy (gauge)
- `quarkboard_hierarchy_nodes_total{trace_id}` - Total Boards + Tasks in hierarchy (counter)
- `quarkboard_child_boards_spawned_total{board_type}` - Child boards spawned by KS
- `quarkboard_budget_exhaustions_total` - Times a child was capped by parent's remaining budget

**Task Model Counters**:
- `quarkboard_task_submissions_total{type, status}` - Total tasks submitted
- `quarkboard_task_completions_total{type, status}` - Tasks completed/failed
- `quarkboard_executor_registrations_total` - Executor registrations
- `quarkboard_heartbeat_failures_total` - Missed heartbeats

**Task Model Gauges**:
- `quarkboard_tasks_pending` - Tasks awaiting assignment
- `quarkboard_tasks_executing` - Tasks currently executing
- `quarkboard_executors_active{capability}` - Active executors by capability

**Task Model Timers**:
- `quarkboard_task_duration_seconds{type, status}` - Task execution time
- `quarkboard_executor_selection_duration` - Time to select executor

**Resilience Metrics** (see §7):
- `quarkboard_dead_letter_entries_total{type}` - Items entering DLQ
- `quarkboard_dead_letter_replayed_total` - Items replayed from DLQ
- `quarkboard_dead_letter_discarded_total` - Items discarded
- `quarkboard_dead_letter_pending` - Items awaiting review (gauge)
- `quarkboard_poison_pill_quarantines_total{source_id}` - Quarantine events
- `quarkboard_poison_pill_active_quarantines` - Currently quarantined sources (gauge)

### 6.6 Health Checks

Quarkus health check integration for deployment orchestration:

**Readiness** (`/q/health/ready`):
- At least one knowledge source or executor registered
- BoardController and TaskRegistry initialized and accepting requests
- Storage provider healthy

**Liveness** (`/q/health/live`):
- BoardController and TaskBroker instances responding
- No critical internal errors

---

## 7. Resilience

QuarkBoard provides robust resilience capabilities to ensure reliable operation under failure conditions.

### 7.1 Error Handling Strategy

#### KnowledgeSource Failures (Board Model)

**KS Exception During Contribution**:
- Board keys written by the failed KS during current `contribute()` call are **rolled back**
- Board remains ACTIVE; other KS may still contribute
- If no other KS can activate, board enters QUIESCENT or FAILED state
- Error logged with board_id and ks_id in MDC

**KS Timeout**:
- KS execution has a configurable timeout (default: 5 minutes)
- If exceeded, KS thread is interrupted and contribution rolled back
- BoardController continues with remaining eligible KS

**Circular Dependencies**:
- Detected at registration time by `KnowledgeSourceRegistry`
- `CircularDependencyException` thrown, KS not registered

**Control KS Failure**: Falls back to `DefaultControlKS`; board solving continues

#### Executor Failures (Task Model)

**Executor Crash (Unresponsive)**:
- Detection: Missed heartbeat after 30s timeout
- Action: Mark assigned tasks as FAILED with error code `EXECUTOR_TIMEOUT`
- Recovery: TaskScheduler may reassign to different executor (Phase 2)

**Exception During Execution**:
- Executor calls `reportFailure(taskId, exception)`
- Task marked as FAILED with structured error info
- Error details include: error code, message, retryable flag, timestamp

**Network Partition**:
- MVP: Executor marked inactive after heartbeat timeout
- Tasks assigned to inactive executor marked FAILED
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
- Executor selection uses bounded thread pool (max 10 threads in MVP)
- Task submission queued with max queue size (1000)
- Reject with `TaskSubmissionException` if queue full

### 7.2 Timeout Enforcement

Timeout and cancellation are first-class states in both the Board and Task state machines. Beyond declaring these states, QuarkBoard provides an **active enforcement mechanism** that transitions work to TIMEOUT when deadlines expire.

#### TimeoutEnforcer

A dedicated Quarkus Scheduler component that actively monitors all in-flight work:

```java
@ApplicationScoped
public class TimeoutEnforcer {

    /**
     * Scheduled task that runs every 1 second (configurable).
     * Checks all active Boards, executing KSARs, and in-flight Tasks
     * against their configured deadlines.
     */
    @Scheduled(every = "${quarkboard.timeout.check-interval:1s}")
    public void enforceTimeouts();
}
```

**What TimeoutEnforcer monitors:**

| Target | Timeout Source | Action on Expiry |
|--------|--------------|-----------------|
| Board | `Duration timeout` on `createAndSolve()`, or PropagationContext deadline | Board → TIMEOUT; all in-flight KSARs cancelled; all child boards/tasks cancelled |
| KSAR (on agenda) | Board's remaining budget | KSAR → TIMEOUT; removed from agenda |
| KSAR (executing) | Per-KS timeout (default: 5min) | KS thread interrupted; contribution rolled back; KSAR → TIMEOUT |
| Task | `Duration timeout` on `submitTask()`, or PropagationContext deadline | Task → TIMEOUT; executor notified |
| Executor heartbeat | 30s heartbeat interval | Executor marked inactive; assigned tasks → FAILED (EXECUTOR_TIMEOUT) |

#### Timeout Cascade Rules

When a parent times out, all descendants are cancelled:
1. Board TIMEOUT → all executing KSARs → CANCELLED, all child boards → CANCELLED, all child tasks → CANCELLED
2. Board TIMEOUT → ControlBoard agenda cleared
3. PropagationContext deadline ensures children cannot outlive parents

#### Quiescent Board Timeout

Boards in QUIESCENT state (no more KS can fire) are monitored by TimeoutEnforcer:
- If a board is QUIESCENT and its deadline expires → TIMEOUT
- If a board is QUIESCENT and no deadline is set → remains QUIESCENT until explicit completion or configurable quiescent timeout (default: 10 minutes)

#### Configuration

```properties
quarkboard.timeout.check-interval=1s
quarkboard.timeout.ks.default=5m
quarkboard.timeout.ks.ocr-extractor=10m
quarkboard.timeout.board.default=30m
quarkboard.timeout.board.quiescent=10m
quarkboard.timeout.task.default=5m
quarkboard.timeout.executor.heartbeat=30s
```

### 7.3 Retry Policy

Structured retry support for both domain KS contributions and task executions. Retry is automatic and configurable — requestors no longer need to manually resubmit.

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

**Board Model — KnowledgeSource Retry:**
When a domain KS fails during `contribute()`:
1. Contributed keys are rolled back (existing behavior)
2. KSAR status → RETRY_PENDING
3. BoardController consults the KS's retry policy
4. If retries remain: KSAR re-queued on ControlBoard agenda after backoff delay
5. If retries exhausted: KSAR → FAILED; KS sent to dead letter (see §7.4)
6. Board remains ACTIVE while other KS can still contribute

**Task Model — Task Retry:**
When a task executor fails:
1. Task status → RETRY_PENDING
2. TaskScheduler consults the task's retry policy
3. If retries remain: task re-queued for executor selection after backoff delay
4. If retries exhausted: task → FAILED; sent to dead letter

#### Retry Budget Interaction with Timeout

- Retry attempts consume the parent timeout budget. If a Board has 10 minutes remaining and a KS has already used 8 minutes across 2 failed attempts, the 3rd retry is only allowed if `remainingBudget > initialDelay`
- Retries never extend past the PropagationContext deadline
- If a retry is pending when the Board times out, the retry is cancelled (not executed)

#### Retry Tracking

```java
public class RetryState {
    private String targetId;              // KS ID or Task ID
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
quarkboard.retry.ks.default.max-attempts=3
quarkboard.retry.ks.default.backoff=EXPONENTIAL_WITH_JITTER
quarkboard.retry.ks.default.initial-delay=1s
quarkboard.retry.ks.default.max-backoff=30s
quarkboard.retry.ks.default.max-duration=2m
quarkboard.retry.ks.ocr-extractor.max-attempts=5

quarkboard.retry.task.default.max-attempts=3
quarkboard.retry.task.default.backoff=EXPONENTIAL_WITH_JITTER
quarkboard.retry.task.default.initial-delay=1s
quarkboard.retry.task.default.max-duration=5m
```

### 7.4 Dead Letter Queue & Poison Pill Protection

When retries are exhausted, failed items must go somewhere safe rather than being silently dropped or — worse — retried indefinitely. The dead letter mechanism ensures failed work is preserved for inspection while **preventing poison pills** from consuming resources.

#### Dead Letter Queue (DLQ)

```java
@ApplicationScoped
public class DeadLetterQueue {

    /**
     * Move a failed KS activation to the dead letter queue.
     * Called by BoardController when KSAR retries are exhausted.
     */
    public DeadLetterEntry sendToDeadLetter(KSAR ksar, Board board,
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
     * Creates a new KSAR or Task with the original context.
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
    private DeadLetterType type;          // KSAR or TASK
    private Instant arrivedAt;            // When it entered the DLQ

    // Original context — everything needed to replay
    private String originalId;            // KSAR ID or Task ID
    private String boardId;              // Originating board (for KSARs)
    private String taskType;             // Task type (for Tasks)
    private Map<String, Object> originalContext;  // Input data
    private PropagationContext propagationContext;

    // Failure details
    private ErrorInfo finalError;
    private List<RetryState.RetryAttempt> retryHistory;
    private int totalAttempts;

    // Status
    private DeadLetterStatus status;      // PENDING_REVIEW, REPLAYED, DISCARDED

    public enum DeadLetterType { KSAR, TASK }
    public enum DeadLetterStatus { PENDING_REVIEW, REPLAYED, DISCARDED }
}
```

#### Poison Pill Protection

A **poison pill** is a KS or Task that consistently fails, consuming resources on every retry attempt without ever succeeding. QuarkBoard protects against this with a **circuit breaker** pattern:

```java
@ApplicationScoped
public class PoisonPillDetector {

    /**
     * Track failure for a KnowledgeSource or Task type.
     * If failures exceed the threshold within the window, the source is quarantined.
     */
    public void recordFailure(String sourceId, String sourceType);

    /**
     * Check if a KS or Task type is quarantined (considered a poison pill).
     */
    public boolean isQuarantined(String sourceId);

    /**
     * Manually release a quarantined source (after fixing the underlying issue).
     */
    public void release(String sourceId);
}
```

**How it works:**

1. **Failure counting**: PoisonPillDetector tracks failures per KS ID and per task type within a sliding time window
2. **Threshold breach**: When failures exceed the threshold (default: 5 failures in 10 minutes), the source is **quarantined**
3. **Quarantine effect**:
   - Quarantined KS: ActivationEngine skips this KS when creating KSARs. Board solving continues without it
   - Quarantined task type: TaskBroker rejects new submissions of this type with `PoisonPillException`
4. **Recovery**: Quarantine expires after a configurable cool-down period (default: 30 minutes), or can be manually released
5. **Notification**: Quarantine events published via NotificationService and logged at ERROR level

#### Dead Letter Flow

```
KS/Task fails
    → Retry Policy: attempts remaining?
        → YES: backoff delay → re-queue (RETRY_PENDING)
        → NO: send to Dead Letter Queue
              → PoisonPillDetector.recordFailure()
                  → threshold breached?
                      → YES: quarantine source; log ERROR; notify
                      → NO: continue; other instances of this KS/task unaffected
```

#### Configuration

```properties
quarkboard.poison-pill.failure-threshold=5
quarkboard.poison-pill.failure-window=10m
quarkboard.poison-pill.quarantine-duration=30m
quarkboard.dead-letter.retention=7d
quarkboard.dead-letter.max-entries=10000
```

### 7.5 Distributed Consistency

QuarkBoard supports single-instance deployment in MVP with a clear path to distributed deployment. The consistency model is explicitly defined at each layer.

#### Consistency Model

| Layer | MVP (Single Instance) | Phase 2+ (Distributed) |
|-------|----------------------|----------------------|
| Board state | Strong (ConcurrentHashMap + synchronized) | Optimistic locking with version vectors |
| ControlBoard | Strong (same JVM) | Leader-elected single writer |
| Task state | Strong (ConcurrentHashMap) | Redis atomic operations |
| KSAR agenda | Strong (priority queue in JVM) | Redis sorted set with distributed lock |
| Dead letter | Strong (in-memory) | Redis list with at-least-once guarantee |

#### Board Versioning (MVP)

Every Board write is versioned to support optimistic concurrency and future distributed deployment:

```java
public class VersionedValue {
    private Object value;
    private long version;          // Monotonically increasing per key
    private String writtenBy;      // KS ID that wrote this value
    private Instant writtenAt;
    private String instanceId;     // QuarkBoard instance ID (for distributed mode)
}
```

#### Conflict Resolution for Board Writes

When two KnowledgeSources (potentially on different instances) write to the same board key concurrently:

```java
public interface ConflictResolver {
    /**
     * Resolve a conflict when two writers attempt to update the same key.
     *
     * @param key         The contested board key
     * @param existing    The current value (with version and writer info)
     * @param incoming    The new value being written
     * @return            The resolved value to store
     */
    VersionedValue resolve(String key, VersionedValue existing, VersionedValue incoming);
}
```

**Built-in conflict resolution strategies:**
- `LAST_WRITER_WINS` (default): Higher version wins; ties broken by timestamp
- `FIRST_WRITER_WINS`: Reject the incoming write; KS must re-read and re-contribute
- `MERGE`: Invoke a custom merge function (registered per key pattern)
- `FAIL`: Throw `ConflictException`; KS contribution rolled back

#### Idempotency Guarantees

```java
@ApplicationScoped
public class IdempotencyService {

    /**
     * Check if an operation has already been performed.
     * Uses idempotencyKey from TaskRequest or generated from KSAR context.
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
- ExecutorRegistry.submitResult(): Duplicate result submissions are idempotent (last-write-wins with version check)
- KnowledgeSource.contribute(): Board.putIfVersion() prevents duplicate writes from re-activated KS
- Dead letter replay: Replay generates new idempotency key to avoid re-dedup

#### Distributed Deployment Model (Phase 2+)

When multiple QuarkBoard instances share state via Redis:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  QuarkBoard     │    │  QuarkBoard     │    │  QuarkBoard     │
│  Instance A     │    │  Instance B     │    │  Instance C     │
│                 │    │                 │    │                 │
│  BoardController│    │  BoardController│    │  BoardController│
│  (follower)     │    │  (LEADER)       │    │  (follower)     │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         └──────────────────────┼──────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │     Redis Cluster     │
                    │  ┌─────────────────┐  │
                    │  │ Board State     │  │ ← Optimistic locking via WATCH/MULTI
                    │  │ ControlBoard    │  │ ← Leader-only writes
                    │  │ KSAR Agenda     │  │ ← Sorted set with BRPOPLPUSH
                    │  │ Task Queue      │  │ ← Atomic RPOPLPUSH
                    │  │ Dead Letter     │  │ ← Append-only list
                    │  │ Locks           │  │ ← Redisson distributed locks
                    │  └─────────────────┘  │
                    └───────────────────────┘
```

**Leader Election for ControlBoard:**
- Only one instance runs the BoardController control loop per board
- Leader elected via Redis distributed lock (Redisson)
- Followers monitor leader health; automatic failover on leader crash
- Domain KS can execute on any instance; results written to shared Redis board

**Optimistic Locking for Board Writes:**
```java
// Distributed board write with optimistic concurrency
public void put(String key, Object value) {
    // 1. Read current version from Redis
    // 2. Execute WATCH on key
    // 3. MULTI: write new value + increment version
    // 4. EXEC: if version changed since WATCH, StaleVersionException → retry
}
```

#### Configuration

```properties
quarkboard.consistency.conflict-resolution=LAST_WRITER_WINS
quarkboard.consistency.conflict-resolution.entities=MERGE
quarkboard.consistency.idempotency-ttl=24h
quarkboard.consistency.leader-lock-ttl=30s
quarkboard.consistency.leader-renewal-interval=10s
```

---

## 8. Agentic AI Specifics

This section covers what makes QuarkBoard specifically suited for agentic AI systems — the dual execution model, Quarkus-native integration, and how AI agents interact with the platform.

### 8.1 Dual Execution Model

QuarkBoard provides two complementary execution models:

| | Board Model (Blackboard) | Task Model (Request-Response) |
|---|---|---|
| **Use Case** | Collaborative, multi-contributor problems | Simple, single-executor work |
| **Data Flow** | Many knowledge sources read/write shared board | One requestor → one executor → one result |
| **Control** | Data-driven (preconditions on board state) | Explicit (requestor submits, executor claims) |
| **Solution** | Emerges from accumulated contributions | Returned as single result |
| **Example** | "Analyze this legal case" | "Classify sentiment of this text" |

Both models share the same executor infrastructure, storage SPI, and observability.

### 8.2 Goals vs Tasks

- **Goal**: High-level objective or desired outcome (e.g., "analyze customer sentiment")
- **Task**: Concrete, executable work unit derived from a goal (e.g., "classify sentiment of document X")

**Design Decision**: MVP supports Tasks and Boards. Goals-to-Tasks decomposition will be added in future iterations when patterns emerge from usage.

### 8.3 Quarkus Integration

#### CDI-Based KnowledgeSource Declaration

Knowledge sources are declared as CDI beans with `@BoardType` annotations for automatic registration:

```java
@ApplicationScoped
@BoardType("legal-case-analysis")
public class NerKnowledgeSource implements KnowledgeSource {

    @Override
    public String getId() { return "ner-extractor"; }

    @Override
    public String getName() { return "Named Entity Recognition"; }

    @Override
    public Set<String> requiredKeys() {
        return Set.of("extracted_text");   // Needs OCR output
    }

    @Override
    public Set<String> producedKeys() {
        return Set.of("entities");          // Produces entity list
    }

    @Override
    public void contribute(Board board) {
        String text = board.get("extracted_text", String.class).orElseThrow();
        List<Entity> entities = nerService.extract(text);
        board.put("entities", entities);
    }
}
```

#### Configuration via application.properties

All QuarkBoard configuration is managed through standard Quarkus `application.properties`. See individual sections for specific properties:
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

**Executor Security**:
- API key authentication for executor registration
- Configuration: `quarkboard.executor.api-keys=executor1:secret1,executor2:secret2`
- Each executor operation validates API key matches executor ID
- Prevents unauthorized result submission or task claiming

**Task Data Security (MVP Scope)**:
- No encryption at rest (in-memory storage)
- No encryption in transit (assumes internal network or TLS termination at load balancer)
- Task context validation: Reject oversized payloads (>1MB default)

**Future Security Enhancements (Phase 2+)**:
- At-rest encryption for Redis storage
- TLS for executor-to-broker communication
- Task context encryption for sensitive data
- Fine-grained permissions (e.g., user can only cancel own tasks)
- Audit logging for compliance
- Schema validation for task context (JSON Schema)
- Rate limiting on task submission

### 8.4 End-to-End Examples

#### Example: Legal Case Analysis (Board Model)

```java
// Requestor creates a board with initial documents
Board board = boardController.createAndSolve(
    "legal-case-analysis",
    Map.of("raw_documents", List.of(doc1, doc2))
);

// Board solving happens automatically:
// 1. BoardController detects "raw_documents" key
// 2. OCR KnowledgeSource activates (requires: raw_documents, produces: extracted_text)
// 3. Board now has extracted_text → NER and Sentiment KS both activate in parallel
// 4. Board now has entities + sentiment → Summary KS activates
// 5. Board complete with case_summary

// Requestor waits for result
Board result = boardController.awaitCompletion(board, Duration.ofMinutes(10));
Map<String, Object> solution = result.snapshot();
String summary = (String) solution.get("case_summary");
```

---

## 9. Implementation Plan (2-Week Sprint)

### Week 1: Core Framework — Board, Control & Task Models
**Day 1-2: Project Setup & Core Interfaces**
- Quarkus project initialization (Maven, dependencies)
- Define Board model interfaces: `Board`, `BoardController`, `KnowledgeSource`, `BoardStatus`
- Define Control interfaces: `ControlBoard`, `ControlKnowledgeSource`, `KSAR`
- Define Task model interfaces: `TaskBroker`, `TaskHandle`, `TaskStatus`
- Define cross-cutting: `PropagationContext`, `LineageService`, `LineageNode`, `LineageTree`
- Define resilience: `RetryPolicy`, `DeadLetterQueue`, `DeadLetterEntry`, `PoisonPillDetector`, `TimeoutEnforcer`
- Define consistency: `VersionedValue`, `ConflictResolver`, `IdempotencyService`
- Define shared interfaces: `ExecutorRegistry`, `NotificationService`
- Define data models: `TaskRequest`, `Task`, `TaskResult`, `ErrorInfo`, `BoardChangeEvent`
- Basic unit tests for models, KSARs, PropagationContext, and RetryPolicy

**Day 3-4: Storage Layer, Board & ControlBoard Implementation**
- Implement `InMemoryBoardStorage` with `ConcurrentHashMap` and change listeners
- Implement `InMemoryTaskStorage` with `ConcurrentHashMap`
- Implement `Board` with key-value operations, change events, thread safety, and versioning
- Implement `ControlBoard` with agenda management, focus, strategy, and resource tracking
- Implement `KSAR` with priority ordering and status lifecycle
- Implement `BoardRegistry` and `TaskRegistry` with lifecycle management
- Implement `InMemoryPropagationStorage` for context and lineage persistence
- TTL-based cleanup scheduler (Quarkus Scheduler) — cleans up by traceId (entire hierarchy)
- Unit tests for Board, ControlBoard, KSAR, BoardRegistry, TaskRegistry, PropagationStorage
- Concurrency tests for Board (multiple writers)

**Day 5: KnowledgeSource & Executor Management**
- Implement `ExecutorRegistry` with heartbeat tracking
- Implement `KnowledgeSourceRegistry` for both domain KS and control KS
- Circular dependency detection for domain KS
- CDI-based `@BoardType` annotation scanning for auto-registration (domain + control KS)
- Implement `DefaultControlKS` (equal-priority baseline)
- Executor timeout detection (30s)
- Unit tests for all registries

### Week 2: Control Loop, Coordination & Testing
**Day 6-7: BoardController Control Loop & TaskBroker**
- Implement `ActivationEngine` — precondition evaluation, KSAR creation, quiescence detection
- Implement `BoardController` control loop:
  - Board change → ActivationEngine creates KSARs on ControlBoard
  - Invoke Control KS to prioritize/filter KSARs
  - Execute highest-priority KSAR(s) from agenda
  - Domain KS contributes to Board → repeat
- Implement `createChildBoard()` with automatic PropagationContext inheritance
- Implement `LineageService` for hierarchy queries (getLineage, getDescendants, getFullTree)
- Implement `TaskScheduler` with round-robin selection
- Wire `TaskBroker` orchestration (delegates to TaskRegistry + TaskScheduler)
- Implement `NotificationService` with Quarkus Event Bus (board changes + task lifecycle)
- Implement hierarchical cancellation (cancel propagates to all child boards/tasks)
- Budget enforcement: child deadline capped at parent's remaining budget
- Unit tests for ActivationEngine, BoardController (with and without custom Control KS)
- Unit tests for context propagation (root → child board → child task, budget inheritance)
- Integration tests: end-to-end Board solving with KS spawning child boards and tasks
- Integration tests: end-to-end Task submission and result retrieval

**Day 8: Resilience, Observability & Error Handling**
- Implement `TimeoutEnforcer` (Quarkus Scheduler, checks every 1s)
- Implement `RetryPolicy` with backoff strategies (fixed, exponential, exponential+jitter)
- Implement KSAR and Task retry flow (RETRY_PENDING state, backoff delay, re-queue)
- Implement `DeadLetterQueue` with DLQ entries, replay, and discard
- Implement `PoisonPillDetector` with failure counting, quarantine, and cool-down
- Implement `IdempotencyService` for duplicate detection
- Add Micrometer metrics for Board, Control, Task, DLQ, and poison pill models
- Structured logging with MDC (trace_id/span_id/parent_span_id + board_id/task_id/ks_id)
- Quarkus health checks (readiness, liveness)
- Error handling: KS failures with retry, executor crashes, board timeouts
- KS failure rollback (contributed keys not written on exception)
- Control KS failure handling (fall back to DefaultControlKS)
- Unit tests for: timeout enforcement, retry with backoff, dead letter flow, poison pill quarantine

**Day 9: Quarkus Integration & Security**
- CDI integration and dependency injection for all components
- Configuration via `application.properties`
- Security: API key validation for executors
- Basic authentication/authorization with `@RolesAllowed`
- Integration tests with Quarkus Test framework

**Day 10: Documentation & Examples**
- API documentation (Javadoc)
- Example: Legal case analysis board (multi-KS with custom Control KS for priority)
- Example: Simple sentiment analysis task (request-response)
- README with quickstart guide for Board model (with/without custom control) and Task model
- End-to-end acceptance tests for both models

### Testing Strategy
- **Unit Tests**: JUnit 5, Mockito for component isolation
- **Integration Tests**: Quarkus Test with `@QuarkusTest`
- **Concurrency Tests**: Multiple KS contributing to same board; multiple task submissions
- **Board-specific Tests**: Precondition evaluation, quiescence detection, auto-completion
- **Control-specific Tests**: KSAR priority ordering, Control KS agenda manipulation, focus changes
- **Propagation Tests**: Context inheritance across Board → child Board → Task hierarchy, budget enforcement, hierarchical cancellation, lineage queries
- **Resilience Tests**: Timeout enforcement (board, KS, task), retry with backoff, dead letter flow, poison pill detection and quarantine, idempotency, board versioning and conflict resolution
- **Coverage Target**: 80% line coverage minimum

### Out of Scope for MVP
- Redis storage provider (Phase 2, Week 3+)
- Progress notifications (Phase 2, Week 4+)
- KS re-activation and retry on failure (Phase 2)
- Nested tasks/sub-slots (Phase 3)
- Advanced control strategies (meta-reasoning, learning from past boards) (Phase 3)
- Merge strategies (Phase 3)
- Distributed deployment (Phase 4)

### Phase 1.5 Buffer (Week 3)
Reserved for:
- Bug fixes from testing
- Performance optimization for Board control loop
- Documentation improvements
- Integration with CaseHub (if ready)

---

## 10. Future Enhancements

### Phase 2 (Weeks 3-4) — Persistence & Reliability
- Redis storage implementation for both Board and Task models
- KS re-activation and retry on failure
- Basic progress notifications
- Configurable retry policies
- Board state snapshots and restoration on crash recovery
- Timeout handling improvements

### Phase 3 (Weeks 5-8) — Advanced Blackboard & Control Features
- **Board hierarchies**: Nested boards for sub-problem decomposition
- **Conditional re-activation**: KS can re-fire when keys they've already read are updated
- **Meta-reasoning Control KS**: Control KS that learn from past board solutions to improve strategy
- **Adaptive resource management**: Control KS that adjust strategy based on resource consumption
- **Control plan persistence**: Save and replay successful control strategies
- Goal → Task decomposition (goals create boards automatically)
- Partial result streaming
- Advanced executor selection (capability matching, load balancing)
- Distributed tracing with OpenTelemetry

### Phase 4 (Long-term) — Distributed & Enterprise
- FIPA contract net protocols
- Policy governance framework
- Merge strategy SPI for conflicting KS contributions
- Multi-instance BoardController (distributed control loop with distributed ControlBoard)
- Distributed deployment support
- Board templates (predefined board types with standard KS + Control KS pipelines)
- Control KS marketplace (pluggable control strategies)

---

## 11. Design Decisions (Answered Questions)

### 1. Goals vs Tasks
**Decision**: ✅ **Task-only for MVP**
- Rationale: Goal decomposition patterns will emerge from usage. Premature abstraction risk.
- Future: Phase 3 will add goal-to-task decomposition based on real-world patterns.

### 2. Notification Mechanism
**Decision**: ✅ **Quarkus CDI Event Bus**
- Rationale: Native Quarkus integration, simple for MVP, no external dependencies.
- Implementation: `NotificationService` publishes `TaskEvent` via `@Observes`.
- Future: Phase 2 may add reactive messaging (Kafka/AMQP) for distributed scenarios.

### 3. Executor Registration
**Decision**: ✅ **Programmatic registration with API key authentication**
- Rationale: MVP needs flexibility; executors call `ExecutorRegistry.register()` at startup.
- Configuration: API keys in `application.properties` for executor validation.
- Future: Phase 2 adds service discovery (Consul, Kubernetes) for dynamic environments.

### 4. Serialization
**Decision**: ✅ **Jackson JSON**
- Rationale: Quarkus default, excellent `Map<String, Object>` support, human-readable.
- Constraint: Task context and result data must be JSON-serializable types.
- Native builds: Custom types need `@RegisterForReflection`.
- Future: Consider Protocol Buffers for high-throughput scenarios (Phase 4).

### 5. Thread Safety
**Decision**: ✅ **ConcurrentHashMap + synchronized blocks**
- TaskRegistry: `ConcurrentHashMap<String, TaskData>` for storage.
- Executor selection: Synchronized block in `TaskScheduler.selectExecutor()`.
- TaskHandle: Thread-safe via immutable task ID and `CompletableFuture`.
- Rationale: Simple, performant for MVP single-instance deployment.
- Future: Phase 4 adds distributed locking (Redis, Hazelcast) for multi-instance.

### 6. Monitoring
**Decision**: ✅ **Micrometer metrics + Quarkus health checks**
- Metrics exposed:
  - Counters: `task_submissions_total`, `task_completions_total`, `task_failures_total`
  - Gauges: `tasks_pending`, `tasks_executing`, `executors_active`
  - Timers: `task_duration_seconds`, `executor_selection_duration`
- Health checks: Readiness (≥1 executor), Liveness (broker responsive)
- Logging: Structured JSON with task_id in MDC for correlation.
- Future: Distributed tracing with OpenTelemetry (Phase 3).

---

## 12. Success Criteria

### MVP (2 weeks) - Core Blackboard + Task Functionality

**Blackboard Core (§3):**
- ✅ **Board Creation**: Requestor can create a board with initial state and paired ControlBoard
- ✅ **Knowledge Source Registration**: Domain KS and Control KS register via CDI with `@BoardType`
- ✅ **Data-Driven Activation**: ActivationEngine evaluates preconditions, creates KSARs
- ✅ **Shared Workspace**: Multiple KS can read from and write to the same board concurrently
- ✅ **Board Lifecycle**: Full CREATED → ACTIVE → QUIESCENT → COMPLETED/FAILED/TIMEOUT lifecycle

**Workers (§4):**
- ✅ **Task Submission**: Requestor can submit task and receive TaskHandle immediately
- ✅ **Task Routing**: TaskScheduler routes tasks to executors using round-robin
- ✅ **Result Retrieval**: Requestor receives notification when result available
- ✅ **Executor Lifecycle**: Executor registration with capabilities, heartbeat, claiming
- ✅ **Cleanup**: TTL-based cleanup for boards, task slots, and dead letter entries

**Coordination (§5):**
- ✅ **Control Loop**: BoardController runs Erman/Hayes-Roth cycle (detect → create KSARs → invoke Control KS → execute top KSARs → repeat)
- ✅ **Auto-Completion**: Board automatically completes when agenda empty and no KSARs in-flight
- ✅ **PropagationContext**: Every Board and Task carries trace ID, span ID, parent span ID, lineage path
- ✅ **Automatic Inheritance**: Child boards/tasks inherit parent's PropagationContext (trace ID preserved)
- ✅ **Child Board Spawning**: KnowledgeSource can spawn child boards via `createChildBoard()` with full context
- ✅ **Budget Enforcement**: Child deadline capped at parent's remaining budget; never exceeds parent
- ✅ **Hierarchical Cancellation**: Cancelling a parent propagates to all child boards and tasks
- ✅ **Lineage Service**: Query full hierarchy tree by trace ID, ancestors, or descendants

**Observability & Control (§6):**
- ✅ **Control Blackboard**: ControlBoard holds scheduling agenda (KSARs), focus, strategy
- ✅ **Control Knowledge Sources**: Control KS reason about priority, focus, strategy on ControlBoard
- ✅ **Scheduling Agenda**: KSARs prioritized by Control KS, executed in priority order by BoardController
- ✅ **Default Control**: Built-in `DefaultControlKS` provides equal-priority baseline
- ✅ **Observability**: Micrometer metrics (incl. DLQ, poison pill, retry), health checks, structured logging with hierarchical MDC

**Resilience (§7):**
- ✅ **Error Handling**: KS exceptions prevent partial writes; Control KS failures fall back to DefaultControlKS
- ✅ **Timeout Enforcement**: TimeoutEnforcer actively monitors all boards, KSARs, and tasks
- ✅ **KSAR Timeout State**: KSARs include TIMEOUT and RETRY_PENDING states in lifecycle
- ✅ **Retry Policy**: Configurable per-KS and per-Task retry with backoff (fixed, exponential, exponential+jitter)
- ✅ **Dead Letter Queue**: Failed items preserved with full context for inspection, replay, or discard
- ✅ **Poison Pill Protection**: PoisonPillDetector quarantines consistently-failing KS/task types
- ✅ **Board Versioning**: Every Board write versioned; `putIfVersion()` for optimistic concurrency
- ✅ **Idempotency**: IdempotencyService prevents duplicate task execution and KS contribution
- ✅ **Conflict Resolution**: Configurable strategies (LAST_WRITER_WINS, FIRST_WRITER_WINS, MERGE, FAIL)

**Agentic AI Specifics (§8):**
- ✅ **Quarkus Integration**: CDI dependency injection, `@BoardType` annotation, configuration
- ✅ **Security**: API key authentication for executors, RBAC for requestors
- ✅ **Concurrency**: Thread-safe operation with versioned board writes and atomic state transitions
- ✅ **Testing**: 80%+ coverage with unit, integration, concurrency, propagation, and resilience tests
- ✅ **Documentation**: README, API docs, legal case analysis with child boards example, sentiment example

### Phase 2 (Weeks 3-4) - Persistence & Distributed
- ✅ Redis storage provider for Board, Task, DLQ, and PropagationContext
- ✅ Redis-backed distributed locking for leader election (ControlBoard)
- ✅ Optimistic locking via Redis WATCH/MULTI for board writes
- ✅ Board crash recovery from persistent state
- ✅ Progress notifications for long-running KS and tasks
- ✅ Distributed IdempotencyService with Redis TTL
- ✅ Multi-instance deployment support

### Phase 3 (Weeks 5-8) - Advanced Blackboard & Control Features
- ✅ Board hierarchies (nested sub-boards)
- ✅ Conditional KS re-activation on key updates
- ✅ Goal-to-board decomposition framework
- ✅ Advanced control strategies (meta-reasoning, learning from past board solutions)
- ✅ Control KS that adapt strategy mid-solve based on resource consumption
- ✅ Advanced executor selection (capability matching, load balancing)
- ✅ Distributed tracing with OpenTelemetry

---

## 13. References

- **Erman, Hayes-Roth et al. (1980)**: "The Hearsay-II Speech-Understanding System: Integrating Knowledge to Resolve Uncertainty" — foundational blackboard architecture
- **Hayes-Roth, B. (1985)**: "A Blackboard Architecture for Control" — formalizes the Control Blackboard and Control Knowledge Sources as a separate reasoning layer
- **Nii, H.P. (1986)**: "Blackboard Systems" — comprehensive survey and taxonomy of blackboard architectures
- **POSA Vol 1 (1996)**: Buschmann et al., "Pattern-Oriented Software Architecture" — Blackboard pattern description
- **CNCF OWL**: [Link to OWL specification if available]
- **Quarkus Flow**: [Link to Quarkus Flow documentation]
- **FIPA Contract Net Protocol**: [Link for future reference]

---

## Appendix A: Architecture Overview

```
            ┌─── Board Model (Collaborative) ──────────────────────────────────────┐
            │                                                                      │
            │                      ┌──────────────────┐                            │
            │                      │  ControlBoard    │ ◀──── ControlKnowledge-    │
            │                      │  (Control        │        Source (reasons     │
            │                      │   Blackboard)    │        about strategy,     │
            │                      │  ┌─ agenda ──┐   │        focus, priority)    │
┌────────┐  │                      │  │ KSAR(pri=9)│  │                            │
│Request-│──┼──▶┌──────────────┐   │  │ KSAR(pri=5)│  │                            │
│  or    │  │   │BoardController│──▶│  └───────────┘  │   ┌──────────────────┐     │
└────────┘  │   │ (Scheduler)  │   │  ┌─ focus ───┐   │   │ KnowledgeSource  │     │
            │   │              │   │  │"entities" │   │   │  (Domain         │     │
            │   └──────┬───────┘   │  └───────────┘   │   │   Specialist)    │     │
            │          │           └──────────────────┘   │                  │     │
            │          │  activate                        │  contribute()    │     │
            │          │  top KSAR                        │    ┌─────────┐   │     │
            │          ▼                                  │    │ read    │   │     │
            │   ┌──────────────┐  onChange                │    │ board   │   │     │
            │   │    Board     │──────────────────────────┼───▶│ write   │   │     │
            │   │   (Domain    │                          │    │ back    │   │     │
            │   │  Workspace)  │◀─────────────────────────┼────┘         │   │     │
            │   └──────────────┘                          └──────────────────┘     │
            └──────────────────────────────────────────────────────────────────────┘

            ┌─── Task Model (Request-Response) ──┐
            │                                     │
┌────────┐  │  ┌──────────────┐    ┌───────────┐  │
│Request-│──┼─▶│  TaskBroker  │◀───│ Executor  │  │
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
- ✅ "Requestor" (not "Requester") - used consistently
- ✅ "Executor" (not "Executive") - used consistently
- ✅ "TaskBroker" - primary service name for task model
- ✅ "Board" (not "Blackboard") - QuarkBoard's domain workspace (avoids confusion with the architecture name)
- ✅ "ControlBoard" - Separate control blackboard per Hayes-Roth 1985
- ✅ "KnowledgeSource" (not "Agent" or "Worker") - aligns with classic blackboard literature (Erman et al.)
- ✅ "ControlKnowledgeSource" - KS that reasons about control, not domain (Hayes-Roth 1985)
- ✅ "BoardController" - Scheduler/execution engine that runs the control loop
- ✅ "KSAR" (Knowledge Source Activation Record) - standard term from HEARSAY-II
- ✅ "Information Slots" - legacy term retained for task model internal storage

---

## Appendix C: Terminology Alignment

| Concept | QuarkBoard | Classic Blackboard (Erman/Hayes-Roth) | CNCF OWL | Quarkus Flow |
|---------|------------|---------------------------------------|----------|--------------|
| Domain Workspace | Board | Blackboard | — | — |
| Control Workspace | ControlBoard | Control Blackboard (Hayes-Roth) | — | — |
| Domain Specialist | KnowledgeSource | Knowledge Source | Worker | Processor |
| Control Specialist | ControlKnowledgeSource | Control Knowledge Source | — | — |
| Activation Record | KSAR | KSAR (KS Activation Record) | — | — |
| Scheduler | BoardController | Scheduler / Control Loop | — | — |
| Context | PropagationContext | — (not in classic model) | — | — |
| Hierarchy Trace | traceId / LineageTree | — | — | — |
| Work Unit | Task | — | Job | Flow Step |
| Work ID | TaskHandle | — | JobHandle | Flow ID |
| Work State | TaskStatus / BoardStatus | — | JobStatus | Flow Status |
| Executor | Executor | — | Worker | Processor |

---

## Appendix D: Design Refinement Changelog

**Version 7.0 (2026-03-26)** - Document Restructuring

### Structural Reorganization
- **Restructured entire document** around six conceptual pillars: Blackboard Core, Workers, Coordination, Observability & Control, Resilience, Agentic AI Specifics
- **Added document structure table** in Executive Summary for navigation
- **Consolidated Board Model and Task Model** content into appropriate pillars rather than mixing by API/architecture layer
- **Moved Control component** (ControlBoard, ControlKS, KSARs) into Observability & Control pillar alongside logging, metrics, and health checks
- **Moved Security** from standalone section into Agentic AI Specifics (§8.3)
- **Architecture overview diagram** moved to Appendix A
- **All technical content preserved** — no interfaces, APIs, or specifications were removed

---

**Version 6.0 (2026-03-26)** - Resilience: Timeout, Retry, Dead Letter, Distributed Consistency

### Timeout Enforcement (§7.2)
- **Added TimeoutEnforcer**: Quarkus Scheduler component that actively monitors all in-flight work (boards, KSARs, tasks) against configured deadlines
- **Added KSAR TIMEOUT state**: KSARs now include TIMEOUT, RETRY_PENDING, and FAILED states
- **Per-KS timeout configuration**: KnowledgeSource.getExecutionTimeout() with application.properties overrides
- **Quiescent board timeout**: Configurable timeout for boards in QUIESCENT state (default: 10min)

### Retry Policy (§7.3)
- **Added RetryPolicy**: Configurable per-KS and per-Task retry with BackoffStrategy (FIXED, EXPONENTIAL, EXPONENTIAL_WITH_JITTER)
- **Added RETRY_PENDING states**: KSAR and Task state machines extended with RETRY_PENDING for automatic retry
- **Added RetryState**: Tracks attempt history, backoff applied, and failure details
- **Budget-aware retry**: Retries consume parent timeout budget; never extend past PropagationContext deadline
- **KnowledgeSource.getRetryPolicy()**: Per-KS retry configuration with sensible defaults (3 attempts, exponential+jitter)

### Dead Letter Queue & Poison Pill Protection (§7.4)
- **Added DeadLetterQueue**: Failed items preserved with full context (original input, retry history, error details) for inspection, replay, or discard
- **Added DeadLetterEntry**: Comprehensive record of failed work with PENDING_REVIEW, REPLAYED, DISCARDED statuses
- **Added PoisonPillDetector**: Circuit breaker that quarantines consistently-failing KS/task types after threshold breach (default: 5 failures in 10min)
- **Quarantine mechanism**: Quarantined KS skipped by ActivationEngine; quarantined task types rejected by TaskBroker
- **Manual recovery**: Quarantine can be manually released; dead letter entries can be replayed after fixing underlying issue

### Distributed Consistency (§7.5)
- **Added Board versioning**: Every Board write versioned with VersionedValue (value, version, writtenBy, writtenAt, instanceId)
- **Added `putIfVersion()`**: Optimistic concurrency control via conditional writes with StaleVersionException
- **Added ConflictResolver**: Pluggable conflict resolution strategies (LAST_WRITER_WINS, FIRST_WRITER_WINS, MERGE, FAIL)
- **Added IdempotencyService**: Duplicate detection and cached result retrieval for tasks and KS contributions
- **Added BoardStorageProvider consistency extensions**: tryLock(), renewLock(), releaseLock() for distributed leader election
- **Defined distributed deployment model**: Leader-elected ControlBoard writer, optimistic locking for Board writes, Redis sorted sets for KSAR agenda

---

**Version 5.0 (2026-03-26)** - Context Propagation & Hierarchical Lineage

### Context Propagation
- **Added PropagationContext**: Immutable, hierarchical context object carried by every Board and Task — includes trace ID, span ID, parent span ID, lineage path, inherited attributes, and resource budget
- **Added `createChild()` factory**: Creates child contexts that inherit trace ID, append lineage, decrement budget, and merge attributes
- **Added `createChildBoard()`**: BoardController method for KnowledgeSources to spawn child boards with automatic context inheritance
- **Added budget enforcement**: Child deadline automatically capped at parent's remaining budget; never exceeds parent
- **Added hierarchical cancellation**: Cancelling a parent Board/Task cascades cancellation to all descendants

### Lineage & Tracing
- **Added LineageService**: Query API for hierarchy traversal — getLineage(), getDescendants(), getFullTree(), findByTraceId()
- **Added LineageNode / LineageTree**: Data structures representing nodes and trees in the Board/Task hierarchy
- **Added PropagationStorageProvider**: SPI for persisting context and lineage data
- **Updated MDC logging**: trace_id, span_id, parent_span_id added to all log entries for cross-hierarchy correlation
- **Added propagation metrics**: hierarchy depth, child boards spawned, budget exhaustions

### API Updates
- **Board interface**: Added `getPropagationContext()` method
- **TaskRequest**: Added `propagationContext` field for explicit context passing
- **Task**: Added `propagationContext` field
- **BoardController**: Added `createChildBoard()` and `getLineage()` methods
- **Updated examples**: KS spawning child boards and tasks with context propagation

---

**Version 4.0 (2026-03-26)** - Erman/Hayes-Roth Control Component

### Control Architecture (Hayes-Roth 1985)
- **Added ControlBoard**: Separate control blackboard paired 1:1 with each domain Board — holds scheduling agenda (KSARs), focus of attention, strategy, resource tracking
- **Added ControlKnowledgeSource interface**: KS that reason about *how* to solve (priority, focus, strategy), not about the domain. Activation conditions: ON_NEW_KSARS, ON_DOMAIN_BOARD_CHANGE, ON_KS_COMPLETION, ALWAYS
- **Added KSAR (Knowledge Source Activation Record)**: Prioritized activation records on the scheduling agenda, consumed by BoardController in priority order
- **Added DefaultControlKS**: Built-in baseline that assigns equal priority — provides backward compatibility while enabling custom control strategies
- **Refactored BoardController**: Now acts as scheduler/execution engine that defers control *decisions* to Control KS on the ControlBoard, per Erman et al.
- **Refactored ActivationEngine**: Now creates KSARs on the ControlBoard rather than directly returning eligible KS
- **Updated KnowledgeSourceRegistry**: Handles both domain KS and control KS registration
- **Added control-specific metrics**: KSAR counts, agenda depth, control cycle duration, Control KS invocations
- **Updated terminology**: KSAR, ControlBoard, ControlKnowledgeSource aligned with HEARSAY-II literature
- **Updated references**: Added Erman et al. 1980, Hayes-Roth 1985, Nii 1986, POSA Vol 1

---

**Version 3.0 (2026-03-26)** - True Blackboard Architecture

### Blackboard Architecture Completeness
- **Added Board (shared workspace)**: Shared data space where multiple knowledge sources collaboratively build solutions
- **Added KnowledgeSource interface**: Independent specialists with declarative preconditions and produced keys
- **Added BoardController**: Blackboard control loop
- **Added ActivationEngine**: Precondition evaluation and quiescence detection
- **Added KnowledgeSourceRegistry**: KS registration with circular dependency validation
- **Established dual execution model**: Board model (collaborative) alongside Task model (request-response)
- **Added CDI-based `@BoardType` annotation** for declarative KS registration
- **BoardStorageProvider SPI**: Separate storage abstraction for board state and contribution history
- **Board lifecycle**: CREATED → ACTIVE → QUIESCENT → COMPLETED/FAILED/TIMEOUT

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
1. ✅ Quarkus CDI Event Bus for notifications
2. ✅ Programmatic executor registration with API keys
3. ✅ Jackson JSON for serialization
4. ✅ ConcurrentHashMap + synchronized blocks for thread safety
5. ✅ Micrometer metrics + Quarkus health checks
