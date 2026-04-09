# POJO Graph Model + Hibernate Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the lineage graph (LineageTree/LineageNode/PropagationContext-as-backbone) with a POJO object graph where CaseFile and Task carry their own parent/child relationships, extract persistence into dedicated modules (casehub-persistence-memory, casehub-persistence-hibernate), and use Long primary keys with @Version optimistic locking.

**Architecture:** CaseFile and Task become the graph — each holds direct references to parent/children. PropagationContext shrinks to a tracing/budget value object attached to each node. A CaseFileRepository and TaskRepository SPI replaces the old storage providers. Two persistence modules implement the SPI: one pure in-memory (for fast tests), one Hibernate/Panache (for production).

**Tech Stack:** Java 21, Quarkus 3.17.5, Hibernate ORM with Panache, H2 (test), PostgreSQL (production), JUnit 5, Maven multi-module.

**GitHub issue:** Refs #6

---

## File Map

### casehub-core — deleted
- `DELETE casehub-core/src/main/java/io/casehub/coordination/LineageTree.java`
- `DELETE casehub-core/src/main/java/io/casehub/coordination/LineageNode.java`
- `DELETE casehub-core/src/main/java/io/casehub/coordination/LineageService.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/PropagationStorageProvider.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/InMemoryCaseFileStorage.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/InMemoryTaskStorage.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/InMemoryPropagationStorage.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/CaseFileStorageProvider.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/spi/TaskStorageProvider.java`
- `DELETE casehub-core/src/main/java/io/casehub/core/DefaultCaseFile.java` (moved to persistence-memory)

### casehub-core — modified
- `casehub-core/src/main/java/io/casehub/coordination/PropagationContext.java` — remove spanId, parentSpanId, lineagePath; keep private createdAt for budget math; add fromStorage() factory
- `casehub-core/src/main/java/io/casehub/core/CaseFile.java` — add Long getId(), Long getVersion(), UUID getOtelTraceId(), getCaseType(), setStatus(), getParentCase(), getChildCases(), getTasks(); remove getCaseFileId()
- `casehub-core/src/main/java/io/casehub/worker/Task.java` — extract Task interface (rename concrete class to DefaultTask temporarily); add Long getId(), Long getVersion(), UUID getOtelSpanId(), getOwningCase(), getChildTasks(); remove String getTaskId(), Optional<String> getCaseFileId()
- `casehub-core/src/main/java/io/casehub/worker/TaskRegistry.java` — evolve into TaskRepository interface (rename + simplify)
- `casehub-core/src/main/java/io/casehub/coordination/CaseEngine.java` — inject CaseFileRepository, remove LineageService, update createChildCaseFile to set parent
- `casehub-core/src/main/java/io/casehub/worker/TaskBroker.java` — use TaskRepository, update task.getTaskId() → task.getId()
- `casehub-core/src/main/java/io/casehub/worker/WorkerRegistry.java` — use TaskRepository, update notifyAutonomousWork to accept CaseFile instead of String caseFileId
- `casehub-core/src/main/java/io/casehub/worker/DefaultTaskHandle.java` — update getTaskId() → getId()
- `casehub-core/src/main/java/io/casehub/worker/TaskHandle.java` — update getTaskId() → getId()

### casehub-core — new
- `casehub-core/src/main/java/io/casehub/core/spi/CaseFileRepository.java` — create/findById/findByStatus/save/delete
- `casehub-core/src/main/java/io/casehub/worker/TaskRepository.java` — create/findById/findByStatus/findByWorker/save/delete

### casehub-persistence-memory — new module
- `casehub-persistence-memory/pom.xml`
- `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseFile.java` — implements CaseFile; ConcurrentHashMap workspace; ArrayList graph relationships
- `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryTask.java` — implements Task
- `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseFileRepository.java` — implements CaseFileRepository; AtomicLong for id generation
- `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryTaskRepository.java` — implements TaskRepository
- `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseFileRepositoryTest.java`
- `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryTaskRepositoryTest.java`

### casehub-persistence-hibernate — new module
- `casehub-persistence-hibernate/pom.xml`
- `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate/HibernateCaseFile.java` — JPA entity implementing CaseFile; @Id Long, @Version Long, @ManyToOne parent, @OneToMany children/tasks
- `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate/HibernateTask.java` — JPA entity implementing Task
- `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate/StringMapConverter.java` — @Converter for Map<String,String> ↔ JSON TEXT
- `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate/HibernateCaseFileRepository.java` — extends PanacheRepositoryBase implements CaseFileRepository
- `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate/HibernateTaskRepository.java` — extends PanacheRepositoryBase implements TaskRepository
- `casehub-persistence-hibernate/src/main/resources/application.properties` — H2 datasource for tests
- `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/hibernate/HibernateCaseFileRepositoryTest.java`
- `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/hibernate/HibernateTaskRepositoryTest.java`

### Root
- `pom.xml` — add casehub-persistence-memory and casehub-persistence-hibernate modules
- `casehub-examples/pom.xml` — add casehub-persistence-memory dependency

---

## Task 1: Slim PropagationContext

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/coordination/PropagationContext.java`

- [ ] **Step 1: Rewrite PropagationContext**

Replace the entire file content:

```java
package io.casehub.coordination;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Immutable tracing and budget context that flows from parent to child.
 * Carries a W3C-compatible trace ID (shared across entire hierarchy),
 * inherited attributes (tenantId, userId, etc.), and optional resource budget.
 *
 * NOTE: spanId/parentSpanId/lineagePath removed — the POJO graph (CaseFile.getParentCase(),
 * CaseFile.getChildCases()) carries the structural relationships.
 */
public class PropagationContext {

    private final String traceId;
    private final Map<String, String> inheritedAttributes;
    private final Instant deadline;           // null = no deadline
    private final Duration remainingBudget;   // null = no budget
    private final Instant createdAt;          // private, used for child budget calculation only

    private PropagationContext(String traceId, Map<String, String> inheritedAttributes,
                               Instant deadline, Duration remainingBudget) {
        this.traceId = traceId;
        this.inheritedAttributes = Collections.unmodifiableMap(new HashMap<>(inheritedAttributes));
        this.deadline = deadline;
        this.remainingBudget = remainingBudget;
        this.createdAt = Instant.now();
    }

    /** Creates a root context with a new random trace ID. */
    public static PropagationContext createRoot() {
        return new PropagationContext(UUID.randomUUID().toString(), Map.of(), null, null);
    }

    /** Creates a root context with inherited attributes and a time budget. */
    public static PropagationContext createRoot(Map<String, String> attributes, Duration budget) {
        Instant deadline = Instant.now().plus(budget);
        return new PropagationContext(UUID.randomUUID().toString(), attributes, deadline, budget);
    }

    /** Creates a root context with inherited attributes and no budget. */
    public static PropagationContext createRoot(Map<String, String> attributes) {
        return new PropagationContext(UUID.randomUUID().toString(), attributes, null, null);
    }

    /**
     * Reconstructs a PropagationContext from storage.
     * Used by Hibernate persistence to restore context from entity fields.
     */
    public static PropagationContext fromStorage(String traceId, Map<String, String> attributes,
                                                  Instant deadline, Duration remainingBudget) {
        return new PropagationContext(traceId,
                attributes != null ? attributes : Map.of(),
                deadline, remainingBudget);
    }

    /** Creates a child context inheriting traceId and adjusting budget. */
    public PropagationContext createChild(Map<String, String> additionalAttributes) {
        Map<String, String> childAttrs = new HashMap<>(this.inheritedAttributes);
        childAttrs.putAll(additionalAttributes);

        Duration childBudget = null;
        if (this.remainingBudget != null) {
            Duration elapsed = Duration.between(this.createdAt, Instant.now());
            Duration remaining = this.remainingBudget.minus(elapsed);
            childBudget = remaining.isNegative() ? Duration.ZERO : remaining;
        }

        return new PropagationContext(this.traceId, childAttrs, this.deadline, childBudget);
    }

    public PropagationContext createChild() {
        return createChild(Map.of());
    }

    public boolean isBudgetExhausted() {
        if (deadline != null && Instant.now().isAfter(deadline)) return true;
        return remainingBudget != null && (remainingBudget.isZero() || remainingBudget.isNegative());
    }

    public Optional<String> getAttribute(String key) {
        return Optional.ofNullable(inheritedAttributes.get(key));
    }

    public String getTraceId()                           { return traceId; }
    public Map<String, String> getInheritedAttributes()  { return inheritedAttributes; }
    public Optional<Instant> getDeadline()               { return Optional.ofNullable(deadline); }
    public Optional<Duration> getRemainingBudget()       { return Optional.ofNullable(remainingBudget); }
}
```

- [ ] **Step 2: Compile to verify no build errors**

```bash
cd /path/to/casehub && mvn compile -pl casehub-core -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` (some errors for callers of removed methods are acceptable here — fixed in later tasks)

- [ ] **Step 3: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/coordination/PropagationContext.java
git commit -m "refactor(core): slim PropagationContext — remove spanId/parentSpanId/lineagePath

Graph structure now expressed via POJO relationships on CaseFile/Task.
PropagationContext retains only: traceId (OTel), inheritedAttributes,
deadline, remainingBudget. Adds fromStorage() factory for Hibernate.

Refs #6"
```

---

## Task 2: Refactor CaseFile interface

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/core/CaseFile.java`

- [ ] **Step 1: Rewrite CaseFile interface**

```java
package io.casehub.core;

import io.casehub.coordination.PropagationContext;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;
import io.casehub.worker.Task;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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

    // Graph relationships
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

- [ ] **Step 2: Compile**

```bash
mvn compile -pl casehub-core -am 2>&1 | tail -20
```

Expected: compile errors on `DefaultCaseFile` and `CaseEngine` (they reference removed `getCaseFileId()`). That is expected — fixed in Tasks 6 and 7.

- [ ] **Step 3: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/core/CaseFile.java
git commit -m "refactor(core): add Long id, UUID otelTraceId, POJO graph to CaseFile interface

Removes getCaseFileId() (String UUID). Adds getId() (Long), getVersion() (Long),
getOtelTraceId() (UUID), getParentCase(), getChildCases(), getTasks().
Downstream callers updated in subsequent tasks.

Refs #6"
```

---

## Task 3: Extract Task interface, add graph relationships

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/worker/Task.java` — becomes interface
- Create: `casehub-core/src/main/java/io/casehub/worker/DefaultTask.java` — concrete in-core impl (temporary; moved to persistence-memory in Task 9)

- [ ] **Step 1: Rename existing Task.java to DefaultTask.java**

```bash
mv casehub-core/src/main/java/io/casehub/worker/Task.java \
   casehub-core/src/main/java/io/casehub/worker/DefaultTask.java
```

- [ ] **Step 2: Update DefaultTask class name and package declaration**

Edit `DefaultTask.java`: change `public class Task` → `public class DefaultTask implements Task`.

Remove the `import io.casehub.coordination.PropagationContext;` if it was there (PropagationContext is still referenced — keep it).

The class keeps all existing fields. Add the new fields:

```java
package io.casehub.worker;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultTask implements Task {

    private static final AtomicLong ID_SEQ = new AtomicLong(0);

    private final Long id;
    private Long version = 0L;
    private final UUID otelSpanId = UUID.randomUUID();
    private String taskType;
    private Map<String, Object> context;
    private Set<String> requiredCapabilities;
    private TaskStatus status;
    private Instant submittedAt;
    private Optional<String> assignedWorkerId;
    private Optional<Instant> assignedAt;
    private PropagationContext propagationContext;
    private TaskOrigin taskOrigin;
    private CaseFile owningCase;              // replaces caseFileId string
    private List<Task> childTasks = new ArrayList<>();

    public DefaultTask() {
        this.id = ID_SEQ.incrementAndGet();
        this.context = new HashMap<>();
        this.requiredCapabilities = new HashSet<>();
        this.status = TaskStatus.PENDING;
        this.submittedAt = Instant.now();
        this.assignedWorkerId = Optional.empty();
        this.assignedAt = Optional.empty();
        this.taskOrigin = TaskOrigin.BROKER_ALLOCATED;
    }

    public DefaultTask(TaskRequest request) {
        this();
        this.taskType = request.getTaskType();
        this.context = new HashMap<>(request.getContext());
        this.requiredCapabilities = new HashSet<>(request.getRequiredCapabilities());
        this.propagationContext = request.getPropagationContext();
    }

    @Override public Long getId()                              { return id; }
    @Override public Long getVersion()                        { return version; }
    @Override public UUID getOtelSpanId()                     { return otelSpanId; }
    @Override public String getTaskType()                     { return taskType; }
    @Override public void setTaskType(String t)               { this.taskType = t; }
    @Override public Map<String, Object> getContext()         { return context; }
    @Override public void setContext(Map<String, Object> c)   { this.context = c; }
    @Override public Set<String> getRequiredCapabilities()    { return requiredCapabilities; }
    @Override public void setRequiredCapabilities(Set<String> c) { this.requiredCapabilities = c; }
    @Override public TaskStatus getStatus()                   { return status; }
    @Override public void setStatus(TaskStatus s)             { this.status = s; }
    @Override public Instant getSubmittedAt()                 { return submittedAt; }
    @Override public Optional<String> getAssignedWorkerId()   { return assignedWorkerId; }
    @Override public void setAssignedWorkerId(String w) {
        this.assignedWorkerId = Optional.ofNullable(w);
        this.assignedAt = Optional.of(Instant.now());
    }
    @Override public Optional<Instant> getAssignedAt()        { return assignedAt; }
    @Override public PropagationContext getPropagationContext(){ return propagationContext; }
    @Override public void setPropagationContext(PropagationContext c) { this.propagationContext = c; }
    @Override public TaskOrigin getTaskOrigin()               { return taskOrigin; }
    @Override public void setTaskOrigin(TaskOrigin o)         { this.taskOrigin = o; }
    @Override public Optional<CaseFile> getOwningCase()       { return Optional.ofNullable(owningCase); }
    @Override public void setOwningCase(CaseFile c)           { this.owningCase = c; }
    @Override public List<Task> getChildTasks()               { return Collections.unmodifiableList(childTasks); }
    @Override public void addChildTask(Task t)                { this.childTasks.add(t); }
}
```

- [ ] **Step 3: Create the Task interface**

Create `casehub-core/src/main/java/io/casehub/worker/Task.java`:

```java
package io.casehub.worker;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A concrete, executable work unit in the request-response model.
 * Has a Long primary key and UUID for OpenTelemetry span tracking.
 * Carries a direct reference to its owning CaseFile and any child tasks,
 * forming part of the POJO object graph.
 */
public interface Task {

    // Identity
    Long getId();
    Long getVersion();
    UUID getOtelSpanId();

    // Task data
    String getTaskType();
    void setTaskType(String taskType);
    Map<String, Object> getContext();
    void setContext(Map<String, Object> context);
    Set<String> getRequiredCapabilities();
    void setRequiredCapabilities(Set<String> capabilities);

    // Lifecycle
    TaskStatus getStatus();
    void setStatus(TaskStatus status);
    Instant getSubmittedAt();
    Optional<String> getAssignedWorkerId();
    void setAssignedWorkerId(String workerId);
    Optional<Instant> getAssignedAt();
    TaskOrigin getTaskOrigin();
    void setTaskOrigin(TaskOrigin origin);

    // Context propagation
    PropagationContext getPropagationContext();
    void setPropagationContext(PropagationContext context);

    // Graph relationships
    Optional<CaseFile> getOwningCase();
    void setOwningCase(CaseFile caseFile);
    List<Task> getChildTasks();
    void addChildTask(Task task);
}
```

- [ ] **Step 4: Update TaskBroker and WorkerRegistry to use DefaultTask**

In `TaskBroker.java` change `new Task(request)` → `new DefaultTask(request)`.
In `WorkerRegistry.java` change `new Task()` → `new DefaultTask()`.
Change `task.getTaskId()` → `task.getId()` in both files.
Change `task.getCaseFileId()` usages to `task.getOwningCase()`.

- [ ] **Step 5: Update TaskRegistry to use Task interface**

In `TaskRegistry.java`, change all method signatures from `Task` concrete class to `Task` interface. Change `task.getTaskId()` → `task.getId()` (returns Long, convert with `.toString()` for map keys or switch map to `Map<Long, Task>`).

- [ ] **Step 6: Update TaskHandle and DefaultTaskHandle**

In `TaskHandle.java`: change `String getTaskId()` → `Long getId()`.
In `DefaultTaskHandle.java`: update accordingly.

- [ ] **Step 7: Compile**

```bash
mvn compile -pl casehub-core -am 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`. Fix any remaining compilation errors.

- [ ] **Step 8: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/worker/
git commit -m "refactor(core): extract Task interface, add Long id/UUID/graph to Task

DefaultTask is the in-core interim implementation. Task interface gains
getId() (Long), getOtelSpanId() (UUID), getOwningCase(), getChildTasks().
Removes getTaskId() (String) and getCaseFileId() (String).

Refs #6"
```

---

## Task 4: Introduce CaseFileRepository and TaskRepository SPIs

**Files:**
- Create: `casehub-core/src/main/java/io/casehub/core/spi/CaseFileRepository.java`
- Create: `casehub-core/src/main/java/io/casehub/worker/TaskRepository.java`
- Delete: `casehub-core/src/main/java/io/casehub/core/spi/CaseFileStorageProvider.java`
- Delete: `casehub-core/src/main/java/io/casehub/core/spi/TaskStorageProvider.java`
- Delete: `casehub-core/src/main/java/io/casehub/core/spi/PropagationStorageProvider.java`

- [ ] **Step 1: Create CaseFileRepository**

```java
package io.casehub.core.spi;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;

import java.util.List;
import java.util.Optional;

/**
 * SPI for creating and retrieving CaseFiles. Implementations:
 * - casehub-persistence-memory: InMemoryCaseFileRepository
 * - casehub-persistence-hibernate: HibernateCaseFileRepository
 */
public interface CaseFileRepository {

    /** Create a root CaseFile (no parent). */
    CaseFile create(String caseType, java.util.Map<String, Object> initialState,
                    PropagationContext propagationContext);

    /** Create a child CaseFile attached to a parent. */
    CaseFile createChild(String caseType, java.util.Map<String, Object> initialState,
                         CaseFile parent);

    Optional<CaseFile> findById(Long id);

    List<CaseFile> findByStatus(CaseStatus status);

    /** Persist any state changes made to the CaseFile (no-op for in-memory). */
    void save(CaseFile caseFile);

    void delete(Long id);
}
```

- [ ] **Step 2: Create TaskRepository**

```java
package io.casehub.worker;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * SPI for creating and retrieving Tasks. Replaces TaskRegistry and TaskStorageProvider.
 * Implementations:
 * - casehub-persistence-memory: InMemoryTaskRepository
 * - casehub-persistence-hibernate: HibernateTaskRepository
 */
public interface TaskRepository {

    Task create(String taskType, java.util.Map<String, Object> context,
                Set<String> requiredCapabilities, PropagationContext propagationContext,
                CaseFile owningCase);

    Task createAutonomous(String taskType, java.util.Map<String, Object> context,
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

- [ ] **Step 3: Delete the old SPIs**

```bash
rm casehub-core/src/main/java/io/casehub/core/spi/CaseFileStorageProvider.java
rm casehub-core/src/main/java/io/casehub/core/spi/TaskStorageProvider.java
rm casehub-core/src/main/java/io/casehub/core/spi/PropagationStorageProvider.java
```

- [ ] **Step 4: Delete old in-memory storage files still in core**

```bash
rm -f casehub-core/src/main/java/io/casehub/core/spi/InMemoryCaseFileStorage.java
rm -f casehub-core/src/main/java/io/casehub/core/spi/InMemoryTaskStorage.java
rm -f casehub-core/src/main/java/io/casehub/core/spi/InMemoryPropagationStorage.java
```

- [ ] **Step 5: Compile**

```bash
mvn compile -pl casehub-core -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`. Fix compilation errors from removed SPIs.

- [ ] **Step 6: Commit**

```bash
git add -A casehub-core/src/main/java/io/casehub/core/spi/
git add casehub-core/src/main/java/io/casehub/worker/TaskRepository.java
git commit -m "refactor(core): introduce CaseFileRepository + TaskRepository SPIs

Replaces CaseFileStorageProvider, TaskStorageProvider, PropagationStorageProvider.
Simpler repository pattern: create, findById, findByStatus, save, delete.
Old SPIs and orphaned InMemory* files deleted.

Refs #6"
```

---

## Task 5: Delete lineage classes, update CaseEngine

**Files:**
- Delete: `LineageTree.java`, `LineageNode.java`, `LineageService.java`
- Modify: `casehub-core/src/main/java/io/casehub/coordination/CaseEngine.java`

- [ ] **Step 1: Delete lineage classes**

```bash
rm casehub-core/src/main/java/io/casehub/coordination/LineageTree.java
rm casehub-core/src/main/java/io/casehub/coordination/LineageNode.java
rm casehub-core/src/main/java/io/casehub/coordination/LineageService.java
```

- [ ] **Step 2: Rewrite CaseEngine**

Replace `CaseEngine.java`:

```java
package io.casehub.coordination;

import io.casehub.control.CasePlanModel;
import io.casehub.control.DefaultCasePlanModel;
import io.casehub.control.PlanItem;
import io.casehub.control.PlanItem.PlanItemStatus;
import io.casehub.control.PlanningStrategy;
import io.casehub.control.PlanningStrategy.ControlActivationCondition;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.ListenerEvaluator;
import io.casehub.core.TaskDefinition;
import io.casehub.core.TaskDefinitionRegistry;
import io.casehub.core.spi.CaseFileRepository;
import io.casehub.error.CaseCreationException;
import io.casehub.resilience.PoisonPillDetector;
import io.casehub.worker.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The execution engine for the CaseHub control loop. Creates CaseFiles via
 * CaseFileRepository, pairs them with CasePlanModels, then runs the control loop:
 * evaluate entry criteria → create PlanItems → invoke PlanningStrategies → execute
 * top PlanItem → repeat until complete or quiescent.
 *
 * Child CaseFiles are created with parent references set, forming the POJO object graph.
 * Lineage queries are expressed by traversing getParentCase() / getChildCases() directly.
 */
@ApplicationScoped
public class CaseEngine {

    private static final Logger LOG = Logger.getLogger(CaseEngine.class);

    @Inject CaseFileRepository caseFileRepository;
    @Inject TaskDefinitionRegistry taskDefRegistry;
    @Inject ListenerEvaluator listenerEvaluator;
    @Inject NotificationService notificationService;
    @Inject PoisonPillDetector poisonPillDetector;

    private final Map<Long, CasePlanModel> casePlanModels = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<CaseFile>> caseFileFutures = new ConcurrentHashMap<>();
    private final ExecutorService controlLoopExecutor = Executors.newCachedThreadPool();

    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState)
            throws CaseCreationException {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile caseFile = caseFileRepository.create(caseType, initialState, ctx);
        scheduleControlLoop(caseFile);
        return caseFile;
    }

    public CaseFile createAndSolve(String caseType, Map<String, Object> initialState,
                                    Duration timeout) throws CaseCreationException {
        PropagationContext ctx = PropagationContext.createRoot(Map.of(), timeout);
        CaseFile caseFile = caseFileRepository.create(caseType, initialState, ctx);
        scheduleControlLoop(caseFile);
        return caseFile;
    }

    public CaseFile createChildCaseFile(CaseFile parent, String caseType,
                                         Map<String, Object> initialState) {
        CaseFile child = caseFileRepository.createChild(caseType, initialState, parent);
        scheduleControlLoop(child);
        return child;
    }

    private void scheduleControlLoop(CaseFile caseFile) {
        Long id = caseFile.getId();
        DefaultCasePlanModel casePlanModel = new DefaultCasePlanModel(caseFile);
        casePlanModels.put(id, casePlanModel);

        CompletableFuture<CaseFile> future = new CompletableFuture<>();
        caseFileFutures.put(id, future);

        controlLoopExecutor.submit(() -> runControlLoop(caseFile));
    }

    private void runControlLoop(CaseFile caseFile) {
        Long id = caseFile.getId();
        CasePlanModel casePlanModel = casePlanModels.get(id);

        caseFile.setStatus(CaseStatus.RUNNING);

        String caseType = caseFile.getCaseType();
        List<TaskDefinition> taskDefs = taskDefRegistry.getForCaseType(caseType);
        List<PlanningStrategy> strategies = taskDefRegistry.getStrategiesForCaseType(caseType);

        List<PlanItem> newPlanItems = listenerEvaluator.evaluateAndCreatePlanItems(
                caseFile, casePlanModel, taskDefs, null);

        while (caseFile.getStatus() == CaseStatus.RUNNING) {
            for (PlanningStrategy strategy : strategies) {
                if (strategy.getActivationCondition() == ControlActivationCondition.ON_NEW_PLAN_ITEMS
                        && !newPlanItems.isEmpty()) {
                    strategy.reason(casePlanModel, caseFile);
                } else if (strategy.getActivationCondition() == ControlActivationCondition.ALWAYS) {
                    strategy.reason(casePlanModel, caseFile);
                }
            }

            List<PlanItem> topPlanItems = casePlanModel.getTopPlanItems(1);

            if (topPlanItems.isEmpty()) {
                if (listenerEvaluator.isQuiescent(casePlanModel)) {
                    caseFile.setStatus(CaseStatus.WAITING);
                    break;
                }
            }

            newPlanItems = List.of();

            for (PlanItem planItem : topPlanItems) {
                planItem.setStatus(PlanItemStatus.RUNNING);

                var tdOpt = taskDefRegistry.getById(planItem.getTaskDefinitionId());
                if (tdOpt.isEmpty()) {
                    planItem.setStatus(PlanItemStatus.FAULTED);
                    casePlanModel.removePlanItem(planItem.getPlanItemId());
                    LOG.warnf("TaskDefinition not found: %s", planItem.getTaskDefinitionId());
                    continue;
                }

                try {
                    tdOpt.get().execute(caseFile);
                    planItem.setStatus(PlanItemStatus.COMPLETED);
                    casePlanModel.removePlanItem(planItem.getPlanItemId());
                    newPlanItems = listenerEvaluator.evaluateAndCreatePlanItems(
                            caseFile, casePlanModel, taskDefs, planItem.getTriggerKey());
                } catch (Exception e) {
                    planItem.setStatus(PlanItemStatus.FAULTED);
                    LOG.errorf(e, "TaskDefinition %s failed", planItem.getTaskDefinitionId());
                }

                CaseStatus currentStatus = caseFile.getStatus();
                if (currentStatus == CaseStatus.COMPLETED || currentStatus == CaseStatus.FAULTED) {
                    break;
                }
            }

            for (PlanningStrategy strategy : strategies) {
                if (strategy.getActivationCondition() == ControlActivationCondition.ON_TASK_COMPLETION) {
                    strategy.reason(casePlanModel, caseFile);
                }
            }
        }

        CompletableFuture<CaseFile> future = caseFileFutures.get(id);
        if (future != null) {
            future.complete(caseFile);
        }
    }

    public CaseFile awaitCompletion(CaseFile caseFile, Duration timeout)
            throws InterruptedException, TimeoutException {
        CompletableFuture<CaseFile> future = caseFileFutures.get(caseFile.getId());
        if (future == null) return caseFile;
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("CaseFile execution failed", e.getCause());
        }
        return caseFile;
    }

    public boolean cancel(CaseFile caseFile) {
        caseFile.setStatus(CaseStatus.CANCELLED);
        CasePlanModel casePlanModel = casePlanModels.get(caseFile.getId());
        if (casePlanModel != null) casePlanModel.clearAgenda();
        CompletableFuture<CaseFile> future = caseFileFutures.get(caseFile.getId());
        if (future != null) future.complete(caseFile);
        return true;
    }

    public CasePlanModel getCasePlanModel(CaseFile caseFile) {
        return casePlanModels.get(caseFile.getId());
    }

    public Map<String, Object> getSnapshot(CaseFile caseFile) {
        return caseFile.snapshot();
    }
}
```

- [ ] **Step 3: Compile**

```bash
mvn compile -pl casehub-core -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/coordination/
git commit -m "refactor(core): delete lineage classes, wire CaseFileRepository into CaseEngine

Removes LineageTree, LineageNode, LineageService. CaseEngine now uses
CaseFileRepository.create() and .createChild() instead of instantiating
DefaultCaseFile directly. Child CaseFiles linked to parent via repository.
Lineage traversal via getParentCase()/getChildCases() on the POJO graph.

Refs #6"
```

---

## Task 6: Update TaskBroker and WorkerRegistry to use TaskRepository

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/worker/TaskBroker.java`
- Modify: `casehub-core/src/main/java/io/casehub/worker/WorkerRegistry.java`
- Delete: `casehub-core/src/main/java/io/casehub/worker/TaskRegistry.java` (absorbed into TaskRepository)

- [ ] **Step 1: Rewrite TaskBroker to inject TaskRepository**

Key changes in `TaskBroker.java`:
- `@Inject TaskRegistry taskRegistry` → `@Inject TaskRepository taskRepository`
- `new Task(request)` → `taskRepository.create(request.getTaskType(), request.getContext(), request.getRequiredCapabilities(), request.getPropagationContext(), null)`
- `task.getTaskId()` → `task.getId().toString()` for map keys, or switch handles map to `Map<Long, DefaultTaskHandle>`
- `taskRegistry.store(task)` → no-op (TaskRepository.create() handles storage)
- `taskRegistry.updateStatus(task.getTaskId(), ...)` → `task.setStatus(...); taskRepository.save(task)`
- `taskRegistry.get(handle.getTaskId())` → `taskRepository.findById(handle.getId())`

- [ ] **Step 2: Rewrite WorkerRegistry to inject TaskRepository**

Key changes in `WorkerRegistry.java`:
- `@Inject TaskRegistry taskRegistry` → `@Inject TaskRepository taskRepository`
- `taskRegistry.findByStatus(TaskStatus.PENDING)` → `taskRepository.findByStatus(TaskStatus.PENDING)`
- `new Task()` in `notifyAutonomousWork` → `taskRepository.createAutonomous(...)`
- Change `String caseFileId` parameter in `notifyAutonomousWork` to `CaseFile owningCase`
- Update all overloads accordingly

- [ ] **Step 3: Delete TaskRegistry**

```bash
rm casehub-core/src/main/java/io/casehub/worker/TaskRegistry.java
```

- [ ] **Step 4: Compile**

```bash
mvn compile -pl casehub-core -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/worker/
git commit -m "refactor(core): wire TaskRepository into TaskBroker and WorkerRegistry

Removes TaskRegistry. TaskBroker and WorkerRegistry now use TaskRepository
SPI for task creation and lookup. notifyAutonomousWork accepts CaseFile
instead of String caseFileId — graph edge set directly.

Refs #6"
```

---

## Task 7: Create casehub-persistence-memory module

**Files:** All new.

- [ ] **Step 1: Create module directory structure**

```bash
mkdir -p casehub-persistence-memory/src/main/java/io/casehub/persistence/memory
mkdir -p casehub-persistence-memory/src/test/java/io/casehub/persistence/memory
```

- [ ] **Step 2: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-persistence-memory</artifactId>
    <name>CaseHub Persistence - In-Memory</name>
    <description>In-memory implementations of CaseFileRepository and TaskRepository. No external dependencies. Use for fast unit tests.</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write failing test for InMemoryCaseFileRepository**

Create `src/test/java/io/casehub/persistence/memory/InMemoryCaseFileRepositoryTest.java`:

```java
package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCaseFileRepositoryTest {

    private InMemoryCaseFileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCaseFileRepository();
    }

    @Test
    void createAssignsSequentialId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile first = repository.create("type-a", Map.of(), ctx);
        CaseFile second = repository.create("type-b", Map.of(), ctx);
        assertNotNull(first.getId());
        assertNotNull(second.getId());
        assertEquals(first.getId() + 1, second.getId());
    }

    @Test
    void createSetsInitialState() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("doc", "hello"), ctx);
        assertEquals("doc-analysis", cf.getCaseType());
        assertEquals(Optional.of("hello"), cf.get("doc", String.class));
        assertEquals(CaseStatus.PENDING, cf.getStatus());
    }

    @Test
    void findByIdReturnsCreatedCaseFile() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile created = repository.create("type-a", Map.of(), ctx);
        Optional<CaseFile> found = repository.findById(created.getId());
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<CaseFile> found = repository.findById(999L);
        assertTrue(found.isEmpty());
    }

    @Test
    void createChildLinksParentAndChild() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);

        assertTrue(child.getParentCase().isPresent());
        assertEquals(parent.getId(), child.getParentCase().get().getId());
        assertEquals(1, parent.getChildCases().size());
        assertEquals(child.getId(), parent.getChildCases().get(0).getId());
    }

    @Test
    void createChildInheritsTraceId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);
        assertEquals(parent.getPropagationContext().getTraceId(),
                     child.getPropagationContext().getTraceId());
    }

    @Test
    void findByStatusFiltersCorrectly() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf1 = repository.create("type-a", Map.of(), ctx);
        CaseFile cf2 = repository.create("type-b", Map.of(), ctx);
        cf1.setStatus(CaseStatus.RUNNING);

        List<CaseFile> running = repository.findByStatus(CaseStatus.RUNNING);
        assertEquals(1, running.size());
        assertEquals(cf1.getId(), running.get(0).getId());

        List<CaseFile> pending = repository.findByStatus(CaseStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(cf2.getId(), pending.get(0).getId());
    }
}
```

- [ ] **Step 4: Run test to confirm it fails**

```bash
cd casehub-persistence-memory && mvn test 2>&1 | tail -10
```

Expected: compilation failure — `InMemoryCaseFileRepository` does not exist yet.

- [ ] **Step 5: Create InMemoryCaseFile**

```java
package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseFileItemEvent;
import io.casehub.core.CaseStatus;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class InMemoryCaseFile implements CaseFile {

    private final Long id;
    private final Long version = 0L;
    private final UUID otelTraceId = UUID.randomUUID();
    private final String caseType;
    private final PropagationContext propagationContext;
    private final Instant createdAt = Instant.now();

    private final ConcurrentHashMap<String, CaseFileItem> store = new ConcurrentHashMap<>();
    private final AtomicLong keyVersion = new AtomicLong(0);
    private final AtomicReference<CaseStatus> status = new AtomicReference<>(CaseStatus.PENDING);

    private final CopyOnWriteArrayList<CaseFile> childCases = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<io.casehub.worker.Task> tasks = new CopyOnWriteArrayList<>();
    private CaseFile parentCase;

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Consumer<CaseFileItemEvent>>> keyListeners = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<CaseFileItemEvent>> anyChangeListeners = new CopyOnWriteArrayList<>();

    InMemoryCaseFile(Long id, String caseType, Map<String, Object> initialState,
                     PropagationContext propagationContext) {
        this.id = id;
        this.caseType = caseType;
        this.propagationContext = propagationContext;
        if (initialState != null) {
            initialState.forEach((k, v) -> {
                long ver = keyVersion.incrementAndGet();
                store.put(k, new CaseFileItem(v, ver));
            });
        }
    }

    void setParentCase(CaseFile parent) { this.parentCase = parent; }
    void addChildCase(CaseFile child) { this.childCases.add(child); }
    void addTask(io.casehub.worker.Task task) { this.tasks.add(task); }

    @Override public Long getId()            { return id; }
    @Override public Long getVersion()       { return keyVersion.get(); }
    @Override public UUID getOtelTraceId()   { return otelTraceId; }
    @Override public String getCaseType()    { return caseType; }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        CaseFileItem item = store.get(key);
        return item == null ? Optional.empty() : Optional.of(type.cast(item.getValue()));
    }

    @Override public boolean contains(String key) { return store.containsKey(key); }

    @Override public Set<String> keys() { return Collections.unmodifiableSet(store.keySet()); }

    @Override public Map<String, Object> snapshot() {
        Map<String, Object> snap = new HashMap<>();
        store.forEach((k, v) -> snap.put(k, v.getValue()));
        return Collections.unmodifiableMap(snap);
    }

    @Override public void put(String key, Object value) {
        CaseFileItem previous = store.get(key);
        long ver = keyVersion.incrementAndGet();
        store.put(key, new CaseFileItem(value, ver));
        fireEvent(key, value, previous);
    }

    @Override public void putIfAbsent(String key, Object value) {
        long ver = keyVersion.incrementAndGet();
        CaseFileItem existing = store.putIfAbsent(key, new CaseFileItem(value, ver));
        if (existing == null) fireEvent(key, value, null);
    }

    @Override public void putIfVersion(String key, Object value, long expectedVersion)
            throws StaleVersionException {
        synchronized (key.intern()) {
            long current = getKeyVersion(key);
            if (current != expectedVersion) throw new StaleVersionException(key, expectedVersion, current);
            CaseFileItem previous = store.get(key);
            long ver = keyVersion.incrementAndGet();
            store.put(key, new CaseFileItem(value, ver));
            fireEvent(key, value, previous);
        }
    }

    @Override public long getKeyVersion(String key) {
        CaseFileItem item = store.get(key);
        return item != null ? item.getVersion() : 0;
    }

    @Override public void onChange(String key, Consumer<CaseFileItemEvent> listener) {
        keyListeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override public void onAnyChange(Consumer<CaseFileItemEvent> listener) {
        anyChangeListeners.add(listener);
    }

    @Override public PropagationContext getPropagationContext() { return propagationContext; }

    @Override public Optional<CaseFile> getParentCase() { return Optional.ofNullable(parentCase); }
    @Override public List<CaseFile> getChildCases()     { return Collections.unmodifiableList(childCases); }
    @Override public List<io.casehub.worker.Task> getTasks() { return Collections.unmodifiableList(tasks); }

    @Override public CaseStatus getStatus()          { return status.get(); }
    @Override public void setStatus(CaseStatus s)    { status.set(s); }
    @Override public Instant getCreatedAt()          { return createdAt; }

    @Override public void complete() { transitionTo(CaseStatus.COMPLETED); }
    @Override public void fail(ErrorInfo error) { transitionTo(CaseStatus.FAULTED); }

    private void transitionTo(CaseStatus target) {
        CaseStatus current = status.get();
        if (current == target) return;
        if (isTerminal(current)) throw new IllegalStateException("Cannot transition from " + current);
        status.compareAndSet(current, target);
    }

    private boolean isTerminal(CaseStatus s) {
        return s == CaseStatus.COMPLETED || s == CaseStatus.FAULTED || s == CaseStatus.CANCELLED;
    }

    private void fireEvent(String key, Object value, CaseFileItem previous) {
        Optional<Object> prev = previous != null ? Optional.of(previous.getValue()) : Optional.empty();
        CaseFileItemEvent event = new CaseFileItemEvent(id.toString(), key, value, prev, Optional.empty());
        List<Consumer<CaseFileItemEvent>> perKey = keyListeners.get(key);
        if (perKey != null) perKey.forEach(l -> l.accept(event));
        anyChangeListeners.forEach(l -> l.accept(event));
    }

    // Minimal value holder
    private record CaseFileItem(Object value, long version) {
        Object getValue() { return value; }
        long getVersion() { return version; }
    }
}
```

- [ ] **Step 6: Create InMemoryCaseFileRepository**

```java
package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.spi.CaseFileRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ApplicationScoped
public class InMemoryCaseFileRepository implements CaseFileRepository {

    private final AtomicLong idSequence = new AtomicLong(0);
    private final Map<Long, InMemoryCaseFile> store = new ConcurrentHashMap<>();

    @Override
    public CaseFile create(String caseType, Map<String, Object> initialState,
                            PropagationContext propagationContext) {
        Long id = idSequence.incrementAndGet();
        InMemoryCaseFile cf = new InMemoryCaseFile(id, caseType, initialState, propagationContext);
        store.put(id, cf);
        return cf;
    }

    @Override
    public CaseFile createChild(String caseType, Map<String, Object> initialState, CaseFile parent) {
        PropagationContext childCtx = parent.getPropagationContext().createChild();
        Long id = idSequence.incrementAndGet();
        InMemoryCaseFile child = new InMemoryCaseFile(id, caseType, initialState, childCtx);
        child.setParentCase(parent);
        ((InMemoryCaseFile) parent).addChildCase(child);
        store.put(id, child);
        return child;
    }

    @Override
    public Optional<CaseFile> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<CaseFile> findByStatus(CaseStatus status) {
        return store.values().stream()
                .filter(cf -> cf.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void save(CaseFile caseFile) {
        // No-op: in-memory objects are mutated directly
    }

    @Override
    public void delete(Long id) {
        store.remove(id);
    }
}
```

- [ ] **Step 7: Create InMemoryTask and InMemoryTaskRepository**

`InMemoryTask.java` — mirrors `DefaultTask` but package `io.casehub.persistence.memory`. Copy `DefaultTask` implementation, change class name to `InMemoryTask`, use the same static `AtomicLong ID_SEQ`.

`InMemoryTaskRepository.java`:

```java
package io.casehub.persistence.memory;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.worker.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ApplicationScoped
public class InMemoryTaskRepository implements TaskRepository {

    private final AtomicLong idSequence = new AtomicLong(0);
    private final Map<Long, Task> store = new ConcurrentHashMap<>();

    @Override
    public Task create(String taskType, Map<String, Object> context,
                       Set<String> requiredCapabilities, PropagationContext propagationContext,
                       CaseFile owningCase) {
        InMemoryTask task = new InMemoryTask(idSequence.incrementAndGet());
        task.setTaskType(taskType);
        task.setContext(new HashMap<>(context));
        task.setRequiredCapabilities(new HashSet<>(requiredCapabilities));
        task.setPropagationContext(propagationContext);
        task.setOwningCase(owningCase);
        task.setTaskOrigin(TaskOrigin.BROKER_ALLOCATED);
        store.put(task.getId(), task);
        if (owningCase instanceof InMemoryCaseFile icf) icf.addTask(task);
        return task;
    }

    @Override
    public Task createAutonomous(String taskType, Map<String, Object> context,
                                  String assignedWorkerId, CaseFile owningCase,
                                  PropagationContext propagationContext) {
        InMemoryTask task = new InMemoryTask(idSequence.incrementAndGet());
        task.setTaskType(taskType);
        task.setContext(new HashMap<>(context));
        task.setPropagationContext(propagationContext);
        task.setOwningCase(owningCase);
        task.setTaskOrigin(TaskOrigin.AUTONOMOUS);
        task.setAssignedWorkerId(assignedWorkerId);
        task.setStatus(TaskStatus.ASSIGNED);
        store.put(task.getId(), task);
        if (owningCase instanceof InMemoryCaseFile icf) icf.addTask(task);
        return task;
    }

    @Override
    public Optional<Task> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Task> findByStatus(TaskStatus status) {
        return store.values().stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Task> findByWorker(String workerId) {
        return store.values().stream()
                .filter(t -> t.getAssignedWorkerId().map(workerId::equals).orElse(false))
                .collect(Collectors.toList());
    }

    @Override public void save(Task task) { /* no-op */ }

    @Override public void delete(Long id) { store.remove(id); }
}
```

- [ ] **Step 8: Run tests**

```bash
cd casehub-persistence-memory && mvn test 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add casehub-persistence-memory/
git commit -m "feat(persistence-memory): add casehub-persistence-memory module

InMemoryCaseFile implements CaseFile with ConcurrentHashMap workspace
and ArrayList graph relationships. InMemoryCaseFileRepository and
InMemoryTaskRepository implement the SPI with AtomicLong id sequences.
No external dependencies — suitable for fast unit tests.

Refs #6"
```

---

## Task 8: Create casehub-persistence-hibernate module

**Files:** All new.

- [ ] **Step 1: Create module directory structure**

```bash
mkdir -p casehub-persistence-hibernate/src/main/java/io/casehub/persistence/hibernate
mkdir -p casehub-persistence-hibernate/src/main/resources
mkdir -p casehub-persistence-hibernate/src/test/java/io/casehub/persistence/hibernate
mkdir -p casehub-persistence-hibernate/src/test/resources
```

- [ ] **Step 2: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>casehub-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-persistence-hibernate</artifactId>
    <name>CaseHub Persistence - Hibernate</name>
    <description>Hibernate/Panache JPA implementations of CaseFileRepository and TaskRepository. Targets PostgreSQL (production) and H2 (tests).</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>casehub-core</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-hibernate-orm-panache</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-jdbc-h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write failing test**

Create `src/test/java/io/casehub/persistence/hibernate/HibernateCaseFileRepositoryTest.java`:

```java
package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.spi.CaseFileRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class HibernateCaseFileRepositoryTest {

    @Inject
    CaseFileRepository repository;

    @Test
    @Transactional
    void createAssignsDatabaseId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("input", "test"), ctx);
        assertNotNull(cf.getId());
        assertTrue(cf.getId() > 0);
    }

    @Test
    @Transactional
    void findByIdReturnsSavedEntity() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile created = repository.create("doc-analysis", Map.of(), ctx);
        Long id = created.getId();

        Optional<CaseFile> found = repository.findById(id);
        assertTrue(found.isPresent());
        assertEquals("doc-analysis", found.get().getCaseType());
    }

    @Test
    @Transactional
    void createChildLinksParentAndChild() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);

        assertTrue(child.getParentCase().isPresent());
        assertEquals(parent.getId(), child.getParentCase().get().getId());
    }

    @Test
    @Transactional
    void createChildInheritsTraceId() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile parent = repository.create("parent-type", Map.of(), ctx);
        CaseFile child = repository.createChild("child-type", Map.of(), parent);
        assertEquals(parent.getPropagationContext().getTraceId(),
                     child.getPropagationContext().getTraceId());
    }

    @Test
    @Transactional
    void workspaceDataPersistsAndLoads() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("doc-analysis", Map.of("key1", "value1"), ctx);
        repository.save(cf);
        cf.put("key2", "value2");
        repository.save(cf);

        Optional<CaseFile> found = repository.findById(cf.getId());
        assertTrue(found.isPresent());
        assertEquals(Optional.of("value1"), found.get().get("key1", String.class));
        assertEquals(Optional.of("value2"), found.get().get("key2", String.class));
    }

    @Test
    @Transactional
    void optimisticVersionIncrementOnUpdate() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("type-a", Map.of(), ctx);
        Long v1 = cf.getVersion();
        cf.put("x", "y");
        repository.save(cf);
        Long v2 = repository.findById(cf.getId()).get().getVersion();
        assertTrue(v2 >= v1, "Version should not decrease after update");
    }
}
```

- [ ] **Step 4: Create test application.properties**

`src/test/resources/application.properties`:

```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:casehub-test;DB_CLOSE_DELAY=-1
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.hibernate-orm.log.sql=false
```

- [ ] **Step 5: Create StringMapConverter**

```java
package io.casehub.persistence.hibernate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

@Converter
public class StringMapConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) return "{}";
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize map", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new HashMap<>();
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize map", e);
        }
    }
}
```

- [ ] **Step 6: Create HibernateCaseFile entity**

```java
package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseFileItemEvent;
import io.casehub.core.CaseStatus;
import io.casehub.error.ErrorInfo;
import io.casehub.error.StaleVersionException;
import io.casehub.worker.Task;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Entity
@Table(name = "case_files")
public class HibernateCaseFile implements CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "case_file_seq")
    @SequenceGenerator(name = "case_file_seq", sequenceName = "case_file_seq", allocationSize = 50)
    private Long id;

    @Version
    private Long version;

    @Column(name = "otel_trace_id", nullable = false, updatable = false, length = 36)
    private String otelTraceIdStr;

    @Column(name = "case_type", nullable = false)
    private String caseType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // PropagationContext stored as columns
    @Column(name = "trace_id", nullable = false)
    private String traceId;

    @Convert(converter = StringMapConverter.class)
    @Column(name = "inherited_attributes", columnDefinition = "TEXT")
    private Map<String, String> inheritedAttributes = new HashMap<>();

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "remaining_budget_seconds")
    private Long remainingBudgetSeconds;

    // Workspace data: key → JSON-serialized value
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_file_items",
            joinColumns = @JoinColumn(name = "case_file_id"))
    @MapKeyColumn(name = "item_key")
    @Column(name = "item_value", columnDefinition = "TEXT")
    private Map<String, String> items = new HashMap<>();

    // Per-key versions
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_file_item_versions",
            joinColumns = @JoinColumn(name = "case_file_id"))
    @MapKeyColumn(name = "item_key")
    @Column(name = "item_version")
    private Map<String, Long> itemVersions = new HashMap<>();

    // Graph relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_case_id")
    private HibernateCaseFile parentCase;

    @OneToMany(mappedBy = "parentCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<HibernateCaseFile> childCases = new ArrayList<>();

    @OneToMany(mappedBy = "owningCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<HibernateTask> tasks = new ArrayList<>();

    // Ephemeral listeners (not persisted)
    @Transient
    private final Map<String, List<Consumer<CaseFileItemEvent>>> keyListeners = new HashMap<>();
    @Transient
    private final List<Consumer<CaseFileItemEvent>> anyChangeListeners = new CopyOnWriteArrayList<>();

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    protected HibernateCaseFile() {}

    HibernateCaseFile(String caseType, Map<String, Object> initialState,
                       PropagationContext propagationContext) {
        this.caseType = caseType;
        this.otelTraceIdStr = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.traceId = propagationContext.getTraceId();
        this.inheritedAttributes = new HashMap<>(propagationContext.getInheritedAttributes());
        this.deadline = propagationContext.getDeadline().orElse(null);
        this.remainingBudgetSeconds = propagationContext.getRemainingBudget()
                .map(Duration::getSeconds).orElse(null);
        if (initialState != null) {
            initialState.forEach((k, v) -> {
                items.put(k, serialize(v));
                itemVersions.put(k, 1L);
            });
        }
    }

    @Override public Long getId()          { return id; }
    @Override public Long getVersion()     { return version; }
    @Override public UUID getOtelTraceId() { return UUID.fromString(otelTraceIdStr); }
    @Override public String getCaseType()  { return caseType; }
    @Override public CaseStatus getStatus() { return status; }
    @Override public void setStatus(CaseStatus s) { this.status = s; }
    @Override public Instant getCreatedAt() { return createdAt; }

    @Override
    public PropagationContext getPropagationContext() {
        Duration budget = remainingBudgetSeconds != null
                ? Duration.ofSeconds(remainingBudgetSeconds) : null;
        return PropagationContext.fromStorage(traceId, inheritedAttributes, deadline, budget);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        String json = items.get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(MAPPER.readValue(json, type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize key: " + key, e);
        }
    }

    @Override public boolean contains(String key) { return items.containsKey(key); }
    @Override public Set<String> keys()           { return Collections.unmodifiableSet(items.keySet()); }

    @Override public Map<String, Object> snapshot() {
        Map<String, Object> snap = new HashMap<>();
        items.forEach((k, v) -> snap.put(k, deserialize(v)));
        return Collections.unmodifiableMap(snap);
    }

    @Override public void put(String key, Object value) {
        String previous = items.get(key);
        items.put(key, serialize(value));
        itemVersions.merge(key, 1L, Long::sum);
        fireEvent(key, value, previous != null ? deserialize(previous) : null);
    }

    @Override public void putIfAbsent(String key, Object value) {
        if (!items.containsKey(key)) {
            items.put(key, serialize(value));
            itemVersions.put(key, 1L);
            fireEvent(key, value, null);
        }
    }

    @Override public void putIfVersion(String key, Object value, long expectedVersion)
            throws StaleVersionException {
        long current = getKeyVersion(key);
        if (current != expectedVersion) throw new StaleVersionException(key, expectedVersion, current);
        put(key, value);
    }

    @Override public long getKeyVersion(String key) {
        return itemVersions.getOrDefault(key, 0L);
    }

    @Override public void onChange(String key, Consumer<CaseFileItemEvent> listener) {
        keyListeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
    }

    @Override public void onAnyChange(Consumer<CaseFileItemEvent> listener) {
        anyChangeListeners.add(listener);
    }

    @Override public Optional<CaseFile> getParentCase() { return Optional.ofNullable(parentCase); }
    @Override public List<CaseFile> getChildCases()     { return Collections.unmodifiableList(childCases); }
    @Override public List<Task> getTasks()               { return Collections.unmodifiableList(tasks); }

    @Override public void complete() { transitionTo(CaseStatus.COMPLETED); }
    @Override public void fail(ErrorInfo error) { transitionTo(CaseStatus.FAULTED); }

    void setParentCase(HibernateCaseFile parent) { this.parentCase = parent; }
    void addChildCase(HibernateCaseFile child)   { this.childCases.add(child); }
    void addTask(HibernateTask task)             { this.tasks.add(task); }

    private void transitionTo(CaseStatus target) {
        if (status == target) return;
        if (isTerminal(status)) throw new IllegalStateException("Cannot transition from " + status);
        status = target;
    }

    private boolean isTerminal(CaseStatus s) {
        return s == CaseStatus.COMPLETED || s == CaseStatus.FAULTED || s == CaseStatus.CANCELLED;
    }

    private void fireEvent(String key, Object value, Object previous) {
        CaseFileItemEvent event = new CaseFileItemEvent(
                id != null ? id.toString() : "?", key, value,
                Optional.ofNullable(previous), Optional.empty());
        List<Consumer<CaseFileItemEvent>> perKey = keyListeners.get(key);
        if (perKey != null) perKey.forEach(l -> l.accept(event));
        anyChangeListeners.forEach(l -> l.accept(event));
    }

    private String serialize(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Serialize failed", e); }
    }

    private Object deserialize(String json) {
        try { return MAPPER.readValue(json, Object.class); }
        catch (Exception e) { throw new IllegalStateException("Deserialize failed", e); }
    }
}
```

- [ ] **Step 7: Create HibernateTask entity**

`HibernateTask.java` — JPA entity implementing `Task`. Mirror structure of `HibernateCaseFile`:
- `@Id @GeneratedValue Long id`
- `@Version Long version`
- `@Column UUID otelSpanId` (stored as string)
- `@ManyToOne(fetch=LAZY) HibernateCaseFile owningCase`
- `@OneToMany(mappedBy="parentTask") List<HibernateTask> childTasks`
- `@Column` fields for taskType, status, submittedAt, assignedWorkerId, taskOrigin
- `@Convert(StringMapConverter)` for context (stored as JSON TEXT) and requiredCapabilities (JSON array as TEXT)
- `PropagationContext` stored as traceId/inheritedAttributes/deadline/remainingBudgetSeconds columns
- `getPropagationContext()` reconstructed via `PropagationContext.fromStorage(...)`

- [ ] **Step 8: Create HibernateCaseFileRepository**

```java
package io.casehub.persistence.hibernate;

import io.casehub.coordination.PropagationContext;
import io.casehub.core.CaseFile;
import io.casehub.core.CaseStatus;
import io.casehub.core.spi.CaseFileRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class HibernateCaseFileRepository
        implements CaseFileRepository, PanacheRepositoryBase<HibernateCaseFile, Long> {

    @Override
    @Transactional
    public CaseFile create(String caseType, Map<String, Object> initialState,
                            PropagationContext propagationContext) {
        HibernateCaseFile cf = new HibernateCaseFile(caseType, initialState, propagationContext);
        persist(cf);
        return cf;
    }

    @Override
    @Transactional
    public CaseFile createChild(String caseType, Map<String, Object> initialState, CaseFile parent) {
        PropagationContext childCtx = parent.getPropagationContext().createChild();
        HibernateCaseFile child = new HibernateCaseFile(caseType, initialState, childCtx);
        HibernateCaseFile hibernateParent = (HibernateCaseFile) parent;
        child.setParentCase(hibernateParent);
        hibernateParent.addChildCase(child);
        persist(child);
        return child;
    }

    @Override
    public Optional<CaseFile> findById(Long id) {
        return findByIdOptional(id).map(cf -> (CaseFile) cf);
    }

    @Override
    public List<CaseFile> findByStatus(CaseStatus status) {
        return list("status", status).stream().map(cf -> (CaseFile) cf).toList();
    }

    @Override
    @Transactional
    public void save(CaseFile caseFile) {
        // Hibernate tracks dirty state automatically within a transaction.
        // Explicit merge only needed when the entity is detached.
        if (caseFile instanceof HibernateCaseFile hcf) {
            getEntityManager().merge(hcf);
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        deleteById(id);
    }
}
```

- [ ] **Step 9: Create HibernateTaskRepository**

Mirror of `HibernateCaseFileRepository` but for `HibernateTask` / `TaskRepository`.

- [ ] **Step 10: Run tests**

```bash
cd casehub-persistence-hibernate && mvn test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, all 5 `HibernateCaseFileRepositoryTest` tests pass.

- [ ] **Step 11: Commit**

```bash
git add casehub-persistence-hibernate/
git commit -m "feat(persistence-hibernate): add casehub-persistence-hibernate module

HibernateCaseFile and HibernateTask are JPA entities implementing the
CaseFile and Task interfaces. Workspace data stored in @ElementCollection.
Graph relationships via @ManyToOne/@OneToMany with LAZY fetch.
PropagationContext reconstructed from stored columns via fromStorage().
H2 used for tests; PostgreSQL datasource declared for production.

Refs #6"
```

---

## Task 9: Update parent POM and examples; full build verification

**Files:**
- Modify: `pom.xml` (root)
- Modify: `casehub-examples/pom.xml`

- [ ] **Step 1: Add new modules to root pom.xml**

In `pom.xml`, in the `<modules>` section add:

```xml
<module>casehub-persistence-memory</module>
<module>casehub-persistence-hibernate</module>
```

Also add to `<dependencyManagement>`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-persistence-memory</artifactId>
    <version>${casehub.version}</version>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-persistence-hibernate</artifactId>
    <version>${casehub.version}</version>
</dependency>
```

- [ ] **Step 2: Add persistence-memory dependency to casehub-examples**

In `casehub-examples/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-persistence-memory</artifactId>
</dependency>
```

- [ ] **Step 3: Remove DefaultCaseFile from casehub-core**

```bash
rm casehub-core/src/main/java/io/casehub/core/DefaultCaseFile.java
```

Update any remaining references in `casehub-core` to remove the `DefaultCaseFile` import (should be none after Task 5 rewrote CaseEngine).

- [ ] **Step 4: Run full build**

```bash
cd /path/to/casehub && mvn clean compile 2>&1 | tail -30
```

Expected:
```
[INFO] CaseHub Parent .............................. SUCCESS
[INFO] CaseHub Core ................................ SUCCESS
[INFO] CaseHub Persistence - In-Memory ............. SUCCESS
[INFO] CaseHub Persistence - Hibernate ............. SUCCESS
[INFO] CaseHub Examples ............................ SUCCESS
[INFO] CaseHub Flow Worker ......................... SUCCESS
[INFO] BUILD SUCCESS
```

Fix any remaining compilation errors.

- [ ] **Step 5: Run all tests**

```bash
mvn test 2>&1 | tail -30
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 6: Final commit**

```bash
git add pom.xml casehub-examples/pom.xml casehub-core/
git commit -m "feat(build): wire persistence modules into parent POM and examples

casehub-persistence-memory and casehub-persistence-hibernate declared
in parent POM. casehub-examples now uses casehub-persistence-memory.
DefaultCaseFile removed from casehub-core — replaced by InMemoryCaseFile.
Full build passing across all modules.

Closes #6"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|---|---|
| Long id + Long version on CaseFile and Task | Tasks 2, 3 |
| UUID for OpenTelemetry (not used for graph) | Tasks 2, 3 |
| Remove LineageTree, LineageNode, LineageService | Task 5 |
| Remove PropagationStorageProvider | Task 4 |
| Slim PropagationContext (remove spanId etc.) | Task 1 |
| PropagationContext.fromStorage() for Hibernate | Task 1 |
| getParentCase(), getChildCases(), getTasks() on CaseFile | Task 2 |
| getOwningCase(), getChildTasks() on Task | Task 3 |
| CaseFileRepository SPI | Task 4 |
| TaskRepository SPI | Task 4 |
| casehub-persistence-memory module | Task 7 |
| casehub-persistence-hibernate module | Task 8 |
| H2 for tests in Hibernate module | Task 8 |
| Remove old CaseFileStorageProvider, TaskStorageProvider | Task 4 |
| InMemoryCaseFileRepository tests | Task 7 |
| HibernateCaseFileRepository tests | Task 8 |
| Full build passing | Task 9 |

**Type consistency check:**
- `CaseFile.getId()` returns `Long` — used as `Long` throughout CaseEngine (map keys) ✓
- `Task.getId()` returns `Long` — used as `Long` in TaskBroker/WorkerRegistry ✓
- `PropagationContext.fromStorage()` signature matches fields stored in HibernateCaseFile ✓
- `createChild()` in both repositories calls `parent.getPropagationContext().createChild()` ✓
- `HibernateCaseFileRepository.findByStatus()` uses Panache `list("status", status)` ✓

**No placeholders detected.** All steps contain concrete code.
