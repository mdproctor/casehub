# casehub-resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the optional `casehub-resilience` Maven module providing ConflictResolver, PoisonPillDetector, DeadLetterQueue, and CaseTimeoutEnforcer — the four resilience components from casehub-core adapted to casehub-engine's async reactive model.

**Architecture:** All components are opt-in via CDI `@Alternative @Priority`. Without the module on the classpath, the engine uses `AllowAllWorkerExecutionGuard` (no quarantine) and has no DLQ. Adding the module activates `PoisonPillWorkerGuard` and `WorkerRetriesExhaustedHandler` automatically. `ConflictResolver` is configurable per-Binding; `CaseTimeoutEnforcer` runs on a Quartz-backed schedule.

**Tech Stack:** Java 21, Quarkus 3.x, Vert.x Mutiny, `@ConsumeEvent`, `@Scheduled`, Jakarta CDI `@Alternative`, JUnit 5, AssertJ, Mockito.

**Working directory:** `/Users/mdproctor/dev/casehub-engine`
**Branch:** create `feat/casehub-resilience` from `upstream/main`
**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience`

**Issues:** casehubio/engine#51 (epic casehubio/engine#30), casehubio/engine#45 (ConflictResolver keep)

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `casehub-resilience/pom.xml` | Create | Module POM |
| `api/src/main/java/io/casehub/api/model/Binding.java` | Modify | Add optional `conflictResolver` field |
| `engine/src/main/java/io/casehub/engine/internal/engine/cache/CaseInstanceCache.java` | Modify | Add `getAll()` for timeout scanner |
| `casehub-resilience/src/main/java/io/casehub/resilience/conflict/ConflictResolver.java` | Create | SPI interface + Strategy enum |
| `casehub-resilience/src/main/java/io/casehub/resilience/conflict/LastWriterWinsConflictResolver.java` | Create | Default implementation |
| `casehub-resilience/src/main/java/io/casehub/resilience/poison/PoisonPillWorkerGuard.java` | Create | `@Alternative` `WorkerExecutionGuard` + quarantine logic |
| `casehub-resilience/src/main/java/io/casehub/resilience/dlq/DeadLetterEntry.java` | Create | Immutable record of a failed worker execution |
| `casehub-resilience/src/main/java/io/casehub/resilience/dlq/DeadLetterStore.java` | Create | In-memory queryable store |
| `casehub-resilience/src/main/java/io/casehub/resilience/dlq/WorkerRetriesExhaustedHandler.java` | Create | `@ConsumeEvent(WORKER_RETRIES_EXHAUSTED)` → DLQ |
| `casehub-resilience/src/main/java/io/casehub/resilience/timeout/CaseTimeoutEnforcer.java` | Create | `@Scheduled` — faults cases with exhausted budget |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` | Modify | Apply ConflictResolver when key already has a value |

---

## Task 1: Branch + Maven module setup

- [ ] **Step 1: Create branch**

```bash
cd /Users/mdproctor/dev/casehub-engine
git fetch upstream
git checkout -b feat/casehub-resilience upstream/main
```

- [ ] **Step 2: Add module to root `pom.xml`**

In the `<modules>` section (after `casehub-blackboard`), add:
```xml
<module>casehub-resilience</module>
```

- [ ] **Step 3: Create `casehub-resilience/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>
  <artifactId>casehub-resilience</artifactId>
  <name>Case Hub :: Resilience</name>
  <description>
    Optional resilience layer: ConflictResolver, PoisonPillDetector,
    DeadLetterQueue, CaseTimeoutEnforcer.
    See casehubio/engine#51 (epic casehubio/engine#30).
  </description>
  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>engine</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-vertx</artifactId>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-scheduler</artifactId>
    </dependency>
    <!-- Test -->
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-junit5</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.awaitility</groupId>
      <artifactId>awaitility</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>io.quarkus.platform</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <extensions>true</extensions>
        <executions>
          <execution>
            <goals>
              <goal>generate-code</goal>
              <goal>generate-code-tests</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration><parameters>true</parameters></configuration>
      </plugin>
      <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <argLine>--add-opens java.base/java.lang=ALL-UNNAMED</argLine>
          <systemPropertyVariables>
            <java.util.logging.manager>org.jboss.logmanager.LogManager</java.util.logging.manager>
            <maven.home>${maven.home}</maven.home>
          </systemPropertyVariables>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Create directory structure**

```bash
mkdir -p casehub-resilience/src/main/java/io/casehub/resilience/{conflict,poison,dlq,timeout}
mkdir -p casehub-resilience/src/test/java/io/casehub/resilience/{conflict,poison,dlq,timeout,it}
mkdir -p casehub-resilience/src/test/java/io/casehub/persistence/memory
mkdir -p casehub-resilience/src/test/resources
```

- [ ] **Step 5: Create `src/test/resources/application.properties`**

```properties
# casehub-resilience tests — in-memory persistence, no Docker
quarkus.quartz.store-type=ram
quarkus.http.test-port=0
quarkus.arc.selected-alternatives=\
  io.casehub.persistence.memory.InMemoryCaseMetaModelRepository,\
  io.casehub.persistence.memory.InMemoryCaseInstanceRepository,\
  io.casehub.persistence.memory.InMemoryEventLogRepository
```

- [ ] **Step 6: Copy in-memory SPI implementations (PR3 pattern)**

```bash
cp engine/src/test/java/io/casehub/persistence/memory/InMemory*.java \
   casehub-resilience/src/test/java/io/casehub/persistence/memory/
```

- [ ] **Step 7: Compile empty module**

```bash
mvn compile -pl casehub-resilience -am -q && echo "BUILD SUCCESS"
```

- [ ] **Step 8: Commit**

```bash
git add pom.xml casehub-resilience/
git commit -m "feat(resilience): add casehub-resilience Maven module skeleton (casehubio/engine#51)"
```

---

## Task 2: ConflictResolver — api/ change + interface

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/Binding.java`
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/conflict/ConflictResolver.java`
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/conflict/LastWriterWinsConflictResolver.java`
- Test: `casehub-resilience/src/test/java/io/casehub/resilience/conflict/ConflictResolverTest.java`

- [ ] **Step 1: Read `api/src/main/java/io/casehub/api/model/Binding.java` in full before editing**

- [ ] **Step 2: Write failing test first**

```java
// casehub-resilience/src/test/java/io/casehub/resilience/conflict/ConflictResolverTest.java
package io.casehub.resilience.conflict;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for ConflictResolver implementations. See casehubio/engine#45, #51. */
class ConflictResolverTest {

    @Test
    void last_writer_wins_returns_incoming() {
        ConflictResolver resolver = new LastWriterWinsConflictResolver();
        assertThat(resolver.resolve("key", "existing", "incoming")).isEqualTo("incoming");
    }

    @Test
    void first_writer_wins_returns_existing() {
        ConflictResolver resolver = ConflictResolver.firstWriterWins();
        assertThat(resolver.resolve("key", "existing", "incoming")).isEqualTo("existing");
    }

    @Test
    void fail_strategy_throws() {
        ConflictResolver resolver = ConflictResolver.fail();
        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> resolver.resolve("key", "existing", "incoming"))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("key");
    }

    @Test
    void null_existing_always_returns_incoming() {
        ConflictResolver resolver = new LastWriterWinsConflictResolver();
        assertThat(resolver.resolve("key", null, "incoming")).isEqualTo("incoming");
    }
}
```

- [ ] **Step 3: Run — verify FAIL**

```bash
mvn test -pl casehub-resilience -Dtest=ConflictResolverTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -5
```

- [ ] **Step 4: Create `ConflictResolver.java`**

```java
package io.casehub.resilience.conflict;

/**
 * Strategy for resolving concurrent writes to the same CaseContext key by
 * different workers. Applied in WorkflowExecutionCompletedHandler when a key
 * already has a value from a prior worker in the same event cycle.
 *
 * <p>Configured per-Binding via {@code Binding.conflictResolver()}.
 * Default (when not set): {@link LastWriterWinsConflictResolver}.
 * See casehubio/engine#45, casehubio/engine#51.
 */
public interface ConflictResolver {

    /**
     * Resolve a conflict between an existing value and an incoming value for the same key.
     *
     * @param key      the CaseContext key in conflict
     * @param existing the value already written by a prior worker (may be null if first writer)
     * @param incoming the value this worker is trying to write
     * @return the winning value to store in CaseContext
     */
    Object resolve(String key, Object existing, Object incoming);

    /** Returns the incoming value, discarding the existing. This is the default. */
    static ConflictResolver lastWriterWins() {
        return new LastWriterWinsConflictResolver();
    }

    /** Returns the existing value, discarding the incoming. */
    static ConflictResolver firstWriterWins() {
        return (key, existing, incoming) -> existing != null ? existing : incoming;
    }

    /**
     * Throws {@link ConflictException} — forces the case designer to handle conflicts explicitly
     * by ensuring no two bindings write to the same key.
     */
    static ConflictResolver fail() {
        return (key, existing, incoming) -> {
            if (existing != null) {
                throw new ConflictException(
                    "Conflicting writes to key '" + key + "': existing=" + existing
                    + ", incoming=" + incoming + ". Use a different ConflictResolver or ensure"
                    + " only one binding writes to this key.");
            }
            return incoming;
        };
    }
}
```

- [ ] **Step 5: Create `LastWriterWinsConflictResolver.java`**

```java
package io.casehub.resilience.conflict;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default {@link ConflictResolver} — incoming value always wins.
 * The last worker to complete its execution determines the final value.
 * See casehubio/engine#45, casehubio/engine#51.
 */
@ApplicationScoped
public class LastWriterWinsConflictResolver implements ConflictResolver {
    @Override
    public Object resolve(String key, Object existing, Object incoming) {
        return incoming;
    }
}
```

- [ ] **Step 6: Create `ConflictException.java`**

```java
package io.casehub.resilience.conflict;

/** Thrown when ConflictResolver.fail() detects a key written by multiple workers. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

- [ ] **Step 7: Add `conflictResolver` to `Binding` in `api/`**

Read `api/src/main/java/io/casehub/api/model/Binding.java`. Add:

```java
// In Binding fields (nullable — null means LastWriterWins default):
private String conflictResolverStrategy; // "LAST_WRITER_WINS" | "FIRST_WRITER_WINS" | "FAIL"

// In Builder:
public Builder conflictResolverStrategy(String strategy) {
    this.conflictResolverStrategy = strategy;
    return this;
}

// Getter:
public String getConflictResolverStrategy() { return conflictResolverStrategy; }
```

**Note:** Store as a String strategy name rather than the `ConflictResolver` interface instance — `Binding` is in `api/` which must not depend on `casehub-resilience`. `WorkflowExecutionCompletedHandler` resolves the strategy to a `ConflictResolver` instance at runtime.

- [ ] **Step 8: Run all tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience -Dtest=ConflictResolverTest 2>&1 | grep "Tests run"
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 9: Commit**

```bash
git add casehub-resilience/src/main/java/io/casehub/resilience/conflict/ \
         casehub-resilience/src/test/java/io/casehub/resilience/conflict/ \
         api/src/main/java/io/casehub/api/model/Binding.java
git commit -m "feat(resilience): ConflictResolver SPI — LAST_WRITER_WINS, FIRST_WRITER_WINS, FAIL strategies (casehubio/engine#45, #51)"
```

---

## Task 3: Apply ConflictResolver in WorkflowExecutionCompletedHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java`
- Test: existing engine tests must still pass

- [ ] **Step 1: Read `WorkflowExecutionCompletedHandler.java` in full**

Find the line where `caseInstance.getCaseContext().setAll(rawOutput)` is called.

- [ ] **Step 2: Add CDI injection of `ConflictResolver` (defaulting to LastWriterWins)**

```java
// Add field to WorkflowExecutionCompletedHandler:
@Inject
@io.casehub.engine.internal.engine.handler.ConflictResolverProvider
ConflictResolver conflictResolver;
```

Actually, simpler: inject `Instance<ConflictResolver>` and use the first available, or a direct `@Inject` with a default. Since `LastWriterWinsConflictResolver` is `@ApplicationScoped` in `casehub-resilience`, it's only available when that module is on the classpath. Without it, use the lambda directly.

**Simplest approach:** Replace `setAll(rawOutput)` with a per-key loop that checks for existing values and invokes the resolver:

```java
// Replace: caseInstance.getCaseContext().setAll(rawOutput);
// With:
final Map<String, Object> output = rawOutput;
for (Map.Entry<String, Object> entry : output.entrySet()) {
    String key = entry.getKey();
    Object incoming = entry.getValue();
    Object existing = caseInstance.getCaseContext().getPath(key);
    if (existing != null && binding != null && binding.getConflictResolverStrategy() != null) {
        incoming = resolveConflict(binding.getConflictResolverStrategy(), key, existing, incoming);
    }
    caseInstance.getCaseContext().set(key, incoming);
}
```

Add private helper:
```java
private Object resolveConflict(String strategy, String key, Object existing, Object incoming) {
    return switch (strategy) {
        case "FIRST_WRITER_WINS" -> existing;
        case "FAIL" -> throw new IllegalStateException(
            "Conflicting writes to key '" + key + "' — binding uses FAIL strategy");
        default -> incoming; // LAST_WRITER_WINS
    };
}
```

**Note:** Read the actual handler to understand how `binding` is available in context. It may need to be threaded through `WorkflowExecutionCompleted` event. If not available, add `Binding binding` to `WorkflowExecutionCompleted` record or use only the strategy string from the event context.

- [ ] **Step 3: Run engine tests to verify no regression**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl engine -Ppersistence-memory \
  -Dexcludes="**/SignalTest.java" 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 4: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java
git commit -m "feat(engine): apply ConflictResolver strategy when writing worker output to CaseContext (casehubio/engine#45, #51)"
```

---

## Task 4: PoisonPillDetector — quarantine workers that fail repeatedly

**Files:**
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/poison/PoisonPillWorkerGuard.java`
- Test: `casehub-resilience/src/test/java/io/casehub/resilience/poison/PoisonPillWorkerGuardTest.java`

The `WorkerExecutionGuard` SPI is at:
`api/src/main/java/io/casehub/api/spi/WorkerExecutionGuard.java`
Interface: `boolean isBlocked(String workerId, UUID caseId)`

`WorkerRetriesExhaustedEvent` record fields — check by reading:
`engine/src/main/java/io/casehub/engine/internal/event/WorkerRetriesExhaustedEvent.java`

- [ ] **Step 1: Read `WorkerRetriesExhaustedEvent.java` to get exact field names**

- [ ] **Step 2: Write failing tests**

```java
// casehub-resilience/src/test/java/io/casehub/resilience/poison/PoisonPillWorkerGuardTest.java
package io.casehub.resilience.poison;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PoisonPillWorkerGuard sliding-window quarantine logic.
 * See casehubio/engine#51.
 */
class PoisonPillWorkerGuardTest {

    private PoisonPillWorkerGuard guard;

    @BeforeEach
    void setUp() {
        guard = new PoisonPillWorkerGuard();
        guard.setFailureThreshold(3);
        guard.setFailureWindowSeconds(60);
        guard.setQuarantineDurationSeconds(300);
    }

    @Test
    void worker_not_blocked_by_default() {
        assertThat(guard.isBlocked("worker-a", UUID.randomUUID())).isFalse();
    }

    @Test
    void worker_blocked_after_threshold_failures() {
        String workerId = "worker-a";
        guard.recordFailure(workerId);
        guard.recordFailure(workerId);
        assertThat(guard.isBlocked(workerId, UUID.randomUUID())).isFalse();
        guard.recordFailure(workerId); // 3rd failure hits threshold
        assertThat(guard.isBlocked(workerId, UUID.randomUUID())).isTrue();
    }

    @Test
    void release_removes_quarantine() {
        String workerId = "worker-a";
        guard.recordFailure(workerId);
        guard.recordFailure(workerId);
        guard.recordFailure(workerId);
        assertThat(guard.isBlocked(workerId, UUID.randomUUID())).isTrue();
        guard.release(workerId);
        assertThat(guard.isBlocked(workerId, UUID.randomUUID())).isFalse();
    }

    @Test
    void different_workers_are_tracked_independently() {
        guard.recordFailure("worker-a");
        guard.recordFailure("worker-a");
        guard.recordFailure("worker-a");
        assertThat(guard.isBlocked("worker-a", UUID.randomUUID())).isTrue();
        assertThat(guard.isBlocked("worker-b", UUID.randomUUID())).isFalse();
    }

    @Test
    void isQuarantined_returns_worker_ids() {
        guard.recordFailure("worker-a");
        guard.recordFailure("worker-a");
        guard.recordFailure("worker-a");
        assertThat(guard.quarantinedWorkers()).contains("worker-a");
    }
}
```

- [ ] **Step 3: Run — verify FAIL**

- [ ] **Step 4: Implement `PoisonPillWorkerGuard.java`**

```java
package io.casehub.resilience.poison;

import io.casehub.api.spi.WorkerExecutionGuard;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkerRetriesExhaustedEvent;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jboss.logging.Logger;

/**
 * Circuit breaker that quarantines consistently-failing workers to prevent resource waste.
 * Tracks failure frequency per workerId within a sliding time window; quarantines the worker
 * when the threshold is exceeded. Quarantined workers are rejected by
 * {@link WorkerExecutionGuard#isBlocked} before scheduling.
 *
 * <p>Activated via {@code @Alternative @Priority(10)} — replaces
 * {@link io.casehub.engine.internal.worker.AllowAllWorkerExecutionGuard}
 * when {@code casehub-resilience} is on the classpath.
 * See casehubio/engine#51.
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class PoisonPillWorkerGuard implements WorkerExecutionGuard {

    private static final Logger LOG = Logger.getLogger(PoisonPillWorkerGuard.class);

    private final Map<String, Queue<Instant>> failureWindows = new ConcurrentHashMap<>();
    private final Map<String, Instant> quarantinedUntil = new ConcurrentHashMap<>();

    private int failureThreshold = 5;
    private long failureWindowSeconds = 600;     // 10 minutes
    private long quarantineDurationSeconds = 1800; // 30 minutes

    @Override
    public boolean isBlocked(String workerId, UUID caseId) {
        Instant until = quarantinedUntil.get(workerId);
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            quarantinedUntil.remove(workerId);
            return false;
        }
        return true;
    }

    @ConsumeEvent(EventBusAddresses.WORKER_RETRIES_EXHAUSTED)
    public void onRetriesExhausted(WorkerRetriesExhaustedEvent event) {
        // Adjust field access to match the actual WorkerRetriesExhaustedEvent record fields
        recordFailure(event.workerId());
    }

    public void recordFailure(String workerId) {
        Queue<Instant> failures = failureWindows.computeIfAbsent(
            workerId, k -> new ConcurrentLinkedQueue<>());
        Instant now = Instant.now();
        failures.add(now);
        failures.removeIf(t -> t.isBefore(now.minus(Duration.ofSeconds(failureWindowSeconds))));
        if (failures.size() >= failureThreshold) {
            quarantinedUntil.put(workerId, now.plus(Duration.ofSeconds(quarantineDurationSeconds)));
            LOG.warnf("Worker '%s' quarantined after %d failures in %ds window. " +
                "Quarantine expires in %ds. See casehubio/engine#51.",
                workerId, failureThreshold, failureWindowSeconds, quarantineDurationSeconds);
        }
    }

    public void release(String workerId) {
        quarantinedUntil.remove(workerId);
        failureWindows.remove(workerId);
    }

    public Set<String> quarantinedWorkers() {
        Instant now = Instant.now();
        quarantinedUntil.entrySet().removeIf(e -> now.isAfter(e.getValue()));
        return quarantinedUntil.keySet();
    }

    // Configuration setters (for property injection or test setup)
    public void setFailureThreshold(int v) { this.failureThreshold = v; }
    public void setFailureWindowSeconds(long v) { this.failureWindowSeconds = v; }
    public void setQuarantineDurationSeconds(long v) { this.quarantineDurationSeconds = v; }
}
```

**Important:** Read `WorkerRetriesExhaustedEvent.java` and adjust `event.workerId()` to match the actual accessor. If the record uses `workerExecutionId()` or another name, correct it.

- [ ] **Step 5: Run tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience -Dtest=PoisonPillWorkerGuardTest 2>&1 | grep "Tests run"
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add casehub-resilience/src/main/java/io/casehub/resilience/poison/ \
         casehub-resilience/src/test/java/io/casehub/resilience/poison/
git commit -m "feat(resilience): PoisonPillWorkerGuard — quarantine workers exceeding failure threshold (casehubio/engine#51)"
```

---

## Task 5: DeadLetterQueue — capture exhausted worker executions

**Files:**
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/dlq/DeadLetterEntry.java`
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/dlq/DeadLetterStore.java`
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/dlq/WorkerRetriesExhaustedHandler.java`
- Test: `casehub-resilience/src/test/java/io/casehub/resilience/dlq/DeadLetterStoreTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-resilience/src/test/java/io/casehub/resilience/dlq/DeadLetterStoreTest.java
package io.casehub.resilience.dlq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for DeadLetterStore. See casehubio/engine#51. */
class DeadLetterStoreTest {

    private DeadLetterStore store;

    @BeforeEach
    void setUp() {
        store = new DeadLetterStore();
    }

    @Test
    void empty_store_returns_empty_list() {
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void entry_is_stored_and_retrievable_by_id() {
        DeadLetterEntry entry = new DeadLetterEntry(
            UUID.randomUUID().toString(), UUID.randomUUID(), "worker-a", "cap-x",
            "Simulated failure", 3, Instant.now());
        store.add(entry);
        assertThat(store.findAll()).hasSize(1);
        assertThat(store.findById(entry.id())).contains(entry);
    }

    @Test
    void discard_removes_entry() {
        DeadLetterEntry entry = new DeadLetterEntry(
            UUID.randomUUID().toString(), UUID.randomUUID(), "worker-a", "cap-x",
            "Simulated failure", 3, Instant.now());
        store.add(entry);
        store.discard(entry.id());
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void findByCaseId_filters_correctly() {
        UUID caseId = UUID.randomUUID();
        DeadLetterEntry match = new DeadLetterEntry(
            UUID.randomUUID().toString(), caseId, "worker-a", "cap-x", "err", 3, Instant.now());
        DeadLetterEntry other = new DeadLetterEntry(
            UUID.randomUUID().toString(), UUID.randomUUID(), "worker-b", "cap-y", "err", 3, Instant.now());
        store.add(match);
        store.add(other);
        assertThat(store.findByCaseId(caseId)).containsExactly(match);
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

- [ ] **Step 3: Create `DeadLetterEntry.java`**

```java
package io.casehub.resilience.dlq;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record of a worker execution that exhausted all retries.
 * Stored in {@link DeadLetterStore} for inspection and manual replay.
 * See casehubio/engine#51.
 */
public record DeadLetterEntry(
    String id,            // unique DLQ entry ID
    UUID caseId,          // the case this worker was running for
    String workerId,      // the worker's name
    String capability,    // the capability that was being exercised
    String errorMessage,  // the final error message
    int totalAttempts,    // how many times the worker was retried
    Instant failedAt      // when retries were exhausted
) {}
```

- [ ] **Step 4: Create `DeadLetterStore.java`**

```java
package io.casehub.resilience.dlq;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * In-memory store of {@link DeadLetterEntry} records. Production deployments
 * should replace this with a persistent implementation via CDI alternative.
 * See casehubio/engine#51.
 */
@ApplicationScoped
public class DeadLetterStore {

    private static final Logger LOG = Logger.getLogger(DeadLetterStore.class);
    private final ConcurrentHashMap<String, DeadLetterEntry> entries = new ConcurrentHashMap<>();

    public void add(DeadLetterEntry entry) {
        entries.put(entry.id(), entry);
        LOG.warnf("Dead letter: caseId=%s worker=%s attempts=%d error=%s. " +
            "Inspect via DeadLetterStore.findAll(). casehubio/engine#51.",
            entry.caseId(), entry.workerId(), entry.totalAttempts(), entry.errorMessage());
    }

    public List<DeadLetterEntry> findAll() {
        return List.copyOf(entries.values());
    }

    public Optional<DeadLetterEntry> findById(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public List<DeadLetterEntry> findByCaseId(java.util.UUID caseId) {
        return entries.values().stream()
            .filter(e -> e.caseId().equals(caseId))
            .toList();
    }

    public void discard(String id) {
        entries.remove(id);
    }

    public int size() {
        return entries.size();
    }
}
```

- [ ] **Step 5: Create `WorkerRetriesExhaustedHandler.java`**

Read `engine/src/main/java/io/casehub/engine/internal/event/WorkerRetriesExhaustedEvent.java` to get exact field names, then:

```java
package io.casehub.resilience.dlq;

import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkerRetriesExhaustedEvent;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;

/**
 * Routes exhausted worker executions to the {@link DeadLetterStore}.
 * Consumes {@code WORKER_RETRIES_EXHAUSTED} via fan-out — coexists with
 * the engine's existing fault handler. See casehubio/engine#51.
 */
@ApplicationScoped
public class WorkerRetriesExhaustedHandler {

    @Inject DeadLetterStore store;

    @ConsumeEvent(EventBusAddresses.WORKER_RETRIES_EXHAUSTED)
    public Uni<Void> onRetriesExhausted(WorkerRetriesExhaustedEvent event) {
        // Adjust field accessors to match the actual record definition
        DeadLetterEntry entry = new DeadLetterEntry(
            UUID.randomUUID().toString(),
            event.caseId(),
            event.workerId(),
            event.idempotency(), // or capability — check actual field
            "Worker retries exhausted",
            0,                   // totalAttempts not in this event; enrich if needed
            Instant.now());
        store.add(entry);
        return Uni.createFrom().voidItem();
    }
}
```

- [ ] **Step 6: Run tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience -Dtest=DeadLetterStoreTest 2>&1 | grep "Tests run"
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 7: Commit**

```bash
git add casehub-resilience/src/main/java/io/casehub/resilience/dlq/ \
         casehub-resilience/src/test/java/io/casehub/resilience/dlq/
git commit -m "feat(resilience): DeadLetterStore + WorkerRetriesExhaustedHandler — capture exhausted workers (casehubio/engine#51)"
```

---

## Task 6: CaseTimeoutEnforcer — fault cases with exhausted budget

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/cache/CaseInstanceCache.java` — add `getAll()`
- Create: `casehub-resilience/src/main/java/io/casehub/resilience/timeout/CaseTimeoutEnforcer.java`
- Test: `casehub-resilience/src/test/java/io/casehub/resilience/timeout/CaseTimeoutEnforcerTest.java`

**Prerequisites:** `PropagationContext` must be in `api/` (PR #119). Verify:
```bash
ls api/src/main/java/io/casehub/api/context/PropagationContext.java
```
If not present, this task must wait until PR #119 is merged. Add a note in the commit.

- [ ] **Step 1: Add `getAll()` to `CaseInstanceCache`**

Read `engine/src/main/java/io/casehub/engine/internal/engine/cache/CaseInstanceCache.java`, then add:

```java
/** Returns a snapshot of all currently cached CaseInstances for timeout scanning. */
public java.util.Collection<CaseInstance> getAll() {
    return java.util.Collections.unmodifiableCollection(cache.values());
}
```

- [ ] **Step 2: Write failing test for CaseTimeoutEnforcer**

```java
// casehub-resilience/src/test/java/io/casehub/resilience/timeout/CaseTimeoutEnforcerTest.java
package io.casehub.resilience.timeout;

import io.casehub.api.context.PropagationContext;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.model.CaseInstance;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CaseTimeoutEnforcer scheduled scanning.
 * See casehubio/engine#51.
 */
class CaseTimeoutEnforcerTest {

    @Test
    void running_case_with_exhausted_budget_is_faulted() {
        CaseInstanceCache cache = new CaseInstanceCache();
        EventBus mockBus = mock(EventBus.class);

        // Create a case with an already-exhausted budget
        CaseInstance instance = new CaseInstance();
        instance.setUuid(java.util.UUID.randomUUID());
        instance.setState(CaseStatus.RUNNING);
        // PropagationContext with 1ms budget — already exhausted
        instance.setCaseContext(
            io.casehub.engine.internal.context.CaseContextImpl.empty()
                .withPropagationContext(
                    PropagationContext.createRoot(java.util.Map.of(), Duration.ofMillis(1))));
        try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        cache.put(instance);

        CaseTimeoutEnforcer enforcer = new CaseTimeoutEnforcer(cache, mockBus);
        enforcer.scanForTimeouts();

        assertThat(instance.getState()).isEqualTo(CaseStatus.FAULTED);
        verify(mockBus).publish(eq(io.casehub.engine.internal.event.EventBusAddresses.CASE_FAULTED), any());
    }

    @Test
    void running_case_without_budget_is_not_affected() {
        CaseInstanceCache cache = new CaseInstanceCache();
        EventBus mockBus = mock(EventBus.class);

        CaseInstance instance = new CaseInstance();
        instance.setUuid(java.util.UUID.randomUUID());
        instance.setState(CaseStatus.RUNNING);
        // No propagation context = no budget constraint
        cache.put(instance);

        CaseTimeoutEnforcer enforcer = new CaseTimeoutEnforcer(cache, mockBus);
        enforcer.scanForTimeouts();

        assertThat(instance.getState()).isEqualTo(CaseStatus.RUNNING);
        verifyNoInteractions(mockBus);
    }

    @Test
    void completed_case_is_not_affected() {
        CaseInstanceCache cache = new CaseInstanceCache();
        EventBus mockBus = mock(EventBus.class);

        CaseInstance instance = new CaseInstance();
        instance.setUuid(java.util.UUID.randomUUID());
        instance.setState(CaseStatus.COMPLETED);
        cache.put(instance);

        CaseTimeoutEnforcer enforcer = new CaseTimeoutEnforcer(cache, mockBus);
        enforcer.scanForTimeouts();

        assertThat(instance.getState()).isEqualTo(CaseStatus.COMPLETED);
        verifyNoInteractions(mockBus);
    }
}
```

**Note:** Adjust `CaseContextImpl.empty().withPropagationContext(...)` to match the actual API. If `CaseContext` doesn't support propagation context yet, simplify the test to mock `CaseContext.getPropagationContext()`.

- [ ] **Step 3: Run — verify FAIL**

- [ ] **Step 4: Implement `CaseTimeoutEnforcer.java`**

```java
package io.casehub.resilience.timeout;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.model.CaseInstance;
import io.quarkus.scheduler.Scheduled;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Scheduled scanner that faults cases whose {@link io.casehub.api.context.PropagationContext}
 * budget has been exhausted. Runs every second by default; configurable via
 * {@code casehub.resilience.timeout.check-interval}.
 *
 * <p>Only affects RUNNING cases. Publishes {@code CASE_FAULTED} to the EventBus so
 * downstream handlers (EventLog, dashboard) can react. See casehubio/engine#51.
 */
@ApplicationScoped
public class CaseTimeoutEnforcer {

    private static final Logger LOG = Logger.getLogger(CaseTimeoutEnforcer.class);

    private final CaseInstanceCache cache;
    private final EventBus eventBus;

    @Inject
    public CaseTimeoutEnforcer(CaseInstanceCache cache, EventBus eventBus) {
        this.cache = cache;
        this.eventBus = eventBus;
    }

    @Scheduled(every = "${casehub.resilience.timeout.check-interval:1s}")
    public void scanForTimeouts() {
        for (CaseInstance instance : cache.getAll()) {
            if (instance.getState() != CaseStatus.RUNNING) continue;
            var ctx = instance.getCaseContext();
            if (ctx == null) continue;
            var propagation = ctx.getPropagationContext();
            if (propagation == null) continue;
            if (propagation.isBudgetExhausted()) {
                instance.setState(CaseStatus.FAULTED);
                eventBus.publish(EventBusAddresses.CASE_FAULTED, instance);
                LOG.warnf("Case %s faulted: budget exhausted. casehubio/engine#51.",
                    instance.getUuid());
            }
        }
    }
}
```

**Note:** Adjust `ctx.getPropagationContext()` to match the actual `CaseContext` API. If `CaseContext` doesn't expose `PropagationContext` yet, this task depends on PR #119.

- [ ] **Step 5: Run tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience -Dtest=CaseTimeoutEnforcerTest 2>&1 | grep "Tests run"
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Run all resilience tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-resilience 2>&1 | grep "Tests run" | tail -2
```

- [ ] **Step 7: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/engine/cache/CaseInstanceCache.java \
         casehub-resilience/src/main/java/io/casehub/resilience/timeout/ \
         casehub-resilience/src/test/java/io/casehub/resilience/timeout/
git commit -m "feat(resilience): CaseTimeoutEnforcer — scheduled scan faults cases with exhausted PropagationContext budget (casehubio/engine#51)"
```

---

## Task 7: Integration tests

**File:** `casehub-resilience/src/test/java/io/casehub/resilience/it/ResilienceIntegrationTest.java`

Copy in-memory SPI impls before running if not already done (Task 1 Step 6).

- [ ] **Step 1: Create integration test**

```java
// casehub-resilience/src/test/java/io/casehub/resilience/it/ResilienceIntegrationTest.java
package io.casehub.resilience.it;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.*;
import io.casehub.resilience.dlq.DeadLetterStore;
import io.casehub.resilience.poison.PoisonPillWorkerGuard;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration tests for casehub-resilience components.
 * Verifies PoisonPillWorkerGuard quarantine and DLQ population.
 * See casehubio/engine#51.
 */
@QuarkusTest
class ResilienceIntegrationTest {

    @Inject DeadLetterStore deadLetterStore;
    @Inject PoisonPillWorkerGuard poisonPillGuard;
    @Inject AlwaysFailingCaseBean alwaysFailingCase;

    /**
     * Happy path: case with a worker that always fails.
     * Expects: worker appears in DLQ after retries exhausted.
     */
    @Test
    void always_failing_worker_appears_in_dead_letter_queue() {
        UUID caseId = alwaysFailingCase.startCase(Map.of("trigger", true))
            .toCompletableFuture().join();

        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(deadLetterStore.findByCaseId(caseId))
                    .as("dead letter store must contain entry for the failed case")
                    .isNotEmpty());
    }

    /**
     * Happy path: after threshold failures, worker is quarantined.
     * PoisonPillWorkerGuard.isBlocked returns true.
     */
    @Test
    void worker_is_quarantined_after_repeated_failures() {
        poisonPillGuard.setFailureThreshold(2);

        poisonPillGuard.recordFailure("always-failing-worker");
        poisonPillGuard.recordFailure("always-failing-worker");

        assertThat(poisonPillGuard.isBlocked("always-failing-worker", UUID.randomUUID()))
            .as("worker must be quarantined after 2 failures with threshold=2")
            .isTrue();

        // Cleanup
        poisonPillGuard.release("always-failing-worker");
        poisonPillGuard.setFailureThreshold(5);
    }

    @io.casehub.api.engine.CaseHub.CaseHubDefinition
    @jakarta.enterprise.context.ApplicationScoped
    public static class AlwaysFailingCaseBean extends CaseHub {
        private final Capability cap = Capability.builder()
            .name("fail-cap").inputSchema("{ trigger: .trigger }").outputSchema("{}").build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("resilience-it").name("Always Failing").version("1.0.0")
                .capabilities(cap)
                .workers(Worker.builder()
                    .name("always-failing-worker").capabilities(cap)
                    .function(input -> { throw new RuntimeException("Simulated failure"); })
                    .executionPolicy(new ExecutionPolicy(1000, new RetryPolicy(2, 100)))
                    .build())
                .bindings(Binding.builder()
                    .name("trigger").capability(cap)
                    .on(new ContextChangeTrigger(".trigger == true"))
                    .build())
                .build();
        }
    }
}
```

**Note:** Adjust annotations and class structure to match the actual `CaseHub` extension pattern established in the engine tests (read `engine/src/test/java/io/casehub/engine/CaseLifecycleStateTest.java` for the correct bean pattern before implementing).

- [ ] **Step 2: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-resilience 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 3: Run full reactor compile to verify no downstream breakage**

```bash
mvn compile -q && echo "REACTOR OK"
```

- [ ] **Step 4: Final commit**

```bash
git add casehub-resilience/src/test/java/io/casehub/resilience/it/
git commit -m "test(resilience): integration tests — DLQ population and quarantine happy path (casehubio/engine#51)"
```

---

## Self-Review

**Spec coverage:**
- ConflictResolver interface + strategies ✓ Task 2
- Binding.conflictResolverStrategy field ✓ Task 2
- ConflictResolver applied in WorkflowExecutionCompletedHandler ✓ Task 3
- PoisonPillDetector (as PoisonPillWorkerGuard) ✓ Task 4
- DeadLetterEntry + DeadLetterStore ✓ Task 5
- WorkerRetriesExhaustedHandler ✓ Task 5
- CaseTimeoutEnforcer ✓ Task 6
- getAll() on CaseInstanceCache ✓ Task 6
- Integration tests ✓ Task 7

**Type consistency:** `DeadLetterEntry` record defined in Task 5, used in Task 5 test and Task 7 IT. `PoisonPillWorkerGuard.recordFailure(String)` defined and used consistently. `ConflictResolver.resolve(String, Object, Object)` consistent throughout.

**Known prerequisite:** Task 6 (`CaseTimeoutEnforcer`) depends on PR #119 (`PropagationContext` in `api/`) being merged before `ctx.getPropagationContext()` is available. If not yet merged, implement without that call and add a TODO comment referencing PR #119.
