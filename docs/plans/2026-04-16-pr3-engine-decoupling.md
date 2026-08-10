# PR3: Engine Persistence Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strip all JPA/Panache from engine domain objects and handlers; route all persistence through repository SPI so engine tests run without Docker.

**Architecture:** Three domain objects (CaseMetaModel, CaseInstance, EventLog) become plain POJOs. Twelve handlers/services inject repository SPI interfaces instead of calling Panache directly. Engine pom drops all JPA deps; casehub-persistence-memory is added as test scope, activated via `quarkus.arc.selected-alternatives`.

**Tech Stack:** Java 21, Quarkus 3.17.5, Mutiny, Quartz, Maven 3.9+. Repo: `/Users/mdproctor/dev/casehub-engine`. Maven: `/opt/homebrew/bin/mvn`.

**Issue:** `casehubio/engine#69` (epic: `casehubio/engine#30`). Every commit ends with `Refs #69`.

**Branch:** `feat/persistence/engine-decoupling` off `feat/persistence/memory`.

---

## Files Modified

| File | Change |
|------|--------|
| `engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java` | Add `updateStateAndAppendEvent` |
| `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java` | Implement `updateStateAndAppendEvent`, add setter |
| `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java` | Implement `updateStateAndAppendEvent` |
| `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java` | Add unit test for `updateStateAndAppendEvent` |
| `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java` | Add integration test for `updateStateAndAppendEvent` |
| `engine/src/main/java/io/casehub/engine/internal/model/CaseMetaModel.java` | Remove PanacheEntity, make POJO |
| `engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java` | Remove PanacheEntity, make POJO |
| `engine/src/main/java/io/casehub/engine/internal/history/EventLog.java` | Remove PanacheEntity + static methods, make POJO |
| `engine/src/main/java/io/casehub/engine/internal/engine/CaseDefinitionRegistry.java` | Inject CaseMetaModelRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java` | Inject CaseInstanceRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStartedEventHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStatusChangedHandler.java` | Inject CaseInstanceRepository + EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerRetriesExhaustedEventHandler.java` | Inject CaseInstanceRepository + EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/GoalReachedEventHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/MilestoneReachedEventHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/SignalReceivedEventHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionManager.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionJobListener.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionTask.java` | Inject EventLogRepository |
| `engine/src/main/java/io/casehub/engine/internal/engine/recovery/WorkerExecutionRecoveryService.java` | Inject CaseInstanceRepository + EventLogRepository |
| `engine/pom.xml` | Remove JPA deps, add memory test dep |
| `engine/src/test/resources/application.properties` | RAM Quartz, selected-alternatives |
| `engine/src/test/java/io/casehub/engine/EngineDecouplingIT.java` | New happy-path E2E test |

---

## Task 1: Create branch and build baseline

**Files:** none

- [ ] **Step 1: Create branch**

```bash
cd /Users/mdproctor/dev/casehub-engine
git checkout feat/persistence/memory
git checkout -b feat/persistence/engine-decoupling
```

- [ ] **Step 2: Install all modules (required before any test run)**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn install -DskipTests -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Confirm engine tests currently require Docker**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -q 2>&1 | tail -20
```

Expected: tests run (with Docker) or skip — note the count. This is the baseline.

---

## Task 2: Extend CaseInstanceRepository SPI

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java`

- [ ] **Step 1: Add `updateStateAndAppendEvent` to the SPI**

Replace the entire file with:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.spi;

import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.model.CaseInstance;
import io.smallrye.mutiny.Uni;
import java.util.UUID;

/**
 * Storage provider for {@link CaseInstance} lifecycle. Implementations must handle their own
 * session/transaction management; callers do not wrap calls in Panache.withTransaction().
 */
public interface CaseInstanceRepository {

  /** Persist a new CaseInstance. Sets {@code instance.id} on completion. */
  Uni<CaseInstance> save(CaseInstance instance);

  /** Update mutable fields (state, parentPlanItemId) of an existing CaseInstance. */
  Uni<CaseInstance> update(CaseInstance instance);

  /** Look up a CaseInstance by its business UUID. Returns null if not found. */
  Uni<CaseInstance> findByUuid(UUID uuid);

  /**
   * Atomically update the instance state and append an event log entry. JPA implementations wrap
   * both writes in a single transaction. In-memory implementations perform both synchronously.
   * Sets {@code eventLog.id} and {@code eventLog.seq} on completion.
   */
  Uni<Void> updateStateAndAppendEvent(CaseInstance instance, EventLog eventLog);
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java
git commit -m "feat(spi): add updateStateAndAppendEvent to CaseInstanceRepository

Refs #69"
```

---

## Task 3: Implement updateStateAndAppendEvent in InMemoryCaseInstanceRepository (TDD)

**Files:**
- Modify: `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java`
- Modify: `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Add this test to `InMemoryCaseInstanceRepositoryTest`:

```java
@Test
void updateStateAndAppendEvent_updatesInstanceAndAppendsEvent() {
    InMemoryEventLogRepository eventLogRepo = new InMemoryEventLogRepository();
    InMemoryCaseInstanceRepository repo = new InMemoryCaseInstanceRepository();
    repo.setEventLogRepository(eventLogRepo);

    CaseMetaModel model = new CaseMetaModel();
    model.setNamespace("ns"); model.setName("n"); model.setVersion("1");

    CaseInstance instance = new CaseInstance();
    instance.setUuid(UUID.randomUUID());
    instance.setCaseMetaModel(model);
    instance.setState(CaseStatus.RUNNING);
    repo.save(instance).subscribe().asCompletionStage().toCompletableFuture().join();

    instance.setState(CaseStatus.COMPLETED);
    EventLog eventLog = new EventLog();
    eventLog.setCaseId(instance.getUuid());
    eventLog.setEventType(CaseHubEventType.CASE_COMPLETED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());

    repo.updateStateAndAppendEvent(instance, eventLog)
        .subscribe().asCompletionStage().toCompletableFuture().join();

    CaseInstance updated = repo.findByUuid(instance.getUuid())
        .subscribe().asCompletionStage().toCompletableFuture().join();
    assertThat(updated.getState()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(eventLog.id).isNotNull();
    assertThat(eventLog.getSeq()).isNotNull();
    EventLog found = eventLogRepo.findById(eventLog.id)
        .subscribe().asCompletionStage().toCompletableFuture().join();
    assertThat(found).isNotNull();
    assertThat(found.getEventType()).isEqualTo(CaseHubEventType.CASE_COMPLETED);
}
```

Ensure imports include:
```java
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.api.model.CaseStatus;
import java.time.Instant;
```

- [ ] **Step 2: Run — confirm it fails**

```bash
cd /Users/mdproctor/dev/casehub-engine/casehub-persistence-memory
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test \
  -Dtest=InMemoryCaseInstanceRepositoryTest#updateStateAndAppendEvent_updatesInstanceAndAppendsEvent -q 2>&1 | tail -15
```

Expected: compilation error (`setEventLogRepository` and `updateStateAndAppendEvent` not found).

- [ ] **Step 3: Implement in InMemoryCaseInstanceRepository**

Replace the file with:

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.persistence.memory;

import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.engine.spi.EventLogRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory {@link CaseInstanceRepository} for use in engine unit tests. Activated via
 * {@code quarkus.arc.selected-alternatives} — never active in production.
 */
@Alternative
@ApplicationScoped
public class InMemoryCaseInstanceRepository implements CaseInstanceRepository {

  private final AtomicLong idSeq = new AtomicLong(0);
  private final ConcurrentHashMap<UUID, CaseInstance> store = new ConcurrentHashMap<>();

  @Inject EventLogRepository eventLogRepository;

  /** Package-private setter for unit tests that cannot use CDI injection. */
  void setEventLogRepository(EventLogRepository eventLogRepository) {
    this.eventLogRepository = eventLogRepository;
  }

  @Override
  public Uni<CaseInstance> save(CaseInstance instance) {
    instance.id = idSeq.incrementAndGet();
    store.put(instance.getUuid(), instance);
    return Uni.createFrom().item(instance);
  }

  @Override
  public Uni<CaseInstance> update(CaseInstance instance) {
    if (!store.containsKey(instance.getUuid())) {
      return Uni.createFrom().failure(
          new IllegalStateException("CaseInstance not found: " + instance.getUuid()));
    }
    store.put(instance.getUuid(), instance);
    return Uni.createFrom().item(instance);
  }

  @Override
  public Uni<CaseInstance> findByUuid(UUID uuid) {
    return Uni.createFrom().item(store.get(uuid));
  }

  @Override
  public Uni<Void> updateStateAndAppendEvent(CaseInstance instance, EventLog eventLog) {
    store.put(instance.getUuid(), instance);
    return eventLogRepository.append(eventLog);
  }
}
```

- [ ] **Step 4: Run test — confirm it passes**

```bash
cd /Users/mdproctor/dev/casehub-engine/casehub-persistence-memory
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test \
  -Dtest=InMemoryCaseInstanceRepositoryTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add casehub-persistence-memory/
git commit -m "feat(persistence-memory): implement updateStateAndAppendEvent with unit test

Refs #69"
```

---

## Task 4: Implement updateStateAndAppendEvent in JpaCaseInstanceRepository (TDD)

**Files:**
- Modify: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java`
- Modify: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java`

- [ ] **Step 1: Write the failing integration test**

Add to `JpaCaseInstanceRepositoryTest`:

```java
@Test
void updateStateAndAppendEvent_atomicallyUpdatesAndPersistsEvent() {
    CaseMetaModel model = createAndSaveMetaModel("atomic-ns", "atomic", "1.0");

    CaseInstance instance = new CaseInstance();
    instance.setUuid(UUID.randomUUID());
    instance.setCaseMetaModel(model);
    instance.setState(CaseStatus.RUNNING);
    caseInstanceRepository.save(instance)
        .subscribe().asCompletionStage().toCompletableFuture().join();

    instance.setState(CaseStatus.FAULTED);
    EventLog eventLog = new EventLog();
    eventLog.setCaseId(instance.getUuid());
    eventLog.setEventType(CaseHubEventType.CASE_FAULTED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MICROS));

    caseInstanceRepository.updateStateAndAppendEvent(instance, eventLog)
        .subscribe().asCompletionStage().toCompletableFuture().join();

    CaseInstance updated = caseInstanceRepository.findByUuid(instance.getUuid())
        .subscribe().asCompletionStage().toCompletableFuture().join();
    assertThat(updated.getState()).isEqualTo(CaseStatus.FAULTED);
    assertThat(eventLog.id).isNotNull();
    assertThat(eventLog.getSeq()).isNotNull();

    EventLog found = eventLogRepository.findById(eventLog.id)
        .subscribe().asCompletionStage().toCompletableFuture().join();
    assertThat(found).isNotNull();
    assertThat(found.getEventType()).isEqualTo(CaseHubEventType.CASE_FAULTED);
}
```

The test class needs `@Inject EventLogRepository eventLogRepository;` and a `createAndSaveMetaModel` helper (check if it already exists; if not, add it following the pattern of other helpers in the same test file).

- [ ] **Step 2: Run — confirm it fails**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test \
  -pl casehub-persistence-hibernate \
  -Dtest=JpaCaseInstanceRepositoryTest#updateStateAndAppendEvent_atomicallyUpdatesAndPersistsEvent \
  -q 2>&1 | tail -15
```

Expected: compilation error (method not found on interface).

- [ ] **Step 3: Implement in JpaCaseInstanceRepository**

Add this method to `JpaCaseInstanceRepository`:

```java
@Override
public Uni<Void> updateStateAndAppendEvent(CaseInstance instance, EventLog eventLog) {
    EventLogEntity logEntity = eventLogMapper.toEntity(eventLog);
    return Panache.withTransaction(() ->
        CaseInstanceEntity.<CaseInstanceEntity>findById(instance.id)
            .chain(entity -> {
                entity.state = instance.getState();
                entity.parentPlanItemId = instance.getParentPlanItemId();
                return Panache.getSession().chain(s -> s.merge(entity));
            })
            .chain(merged -> logEntity.persist())
    )
    .invoke(() -> {
        eventLog.id = logEntity.id;
        eventLog.setSeq(logEntity.seq);
    })
    .replaceWithVoid();
}
```

Note: `eventLogMapper` is the existing mapper already used in the class. If the class uses inline `toEntity` logic instead of a mapper field, follow the same pattern as the existing `append` method in `JpaEventLogRepository`.

- [ ] **Step 4: Run test — confirm it passes**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test \
  -pl casehub-persistence-hibernate \
  -Dtest=JpaCaseInstanceRepositoryTest -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add casehub-persistence-hibernate/
git commit -m "feat(persistence-hibernate): implement updateStateAndAppendEvent with integration test

Refs #69"
```

---

## Task 5: Convert CaseMetaModel to POJO

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/model/CaseMetaModel.java`

- [ ] **Step 1: Replace with POJO version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Objects;

/** Plain domain object describing a registered case type. Persistence is handled by the SPI. */
public class CaseMetaModel {

  /** Populated by the repository after save. Null until first persisted. */
  public Long id;

  private String name;
  private String namespace;
  private String version;
  private String title;
  private String dsl;
  private JsonNode definition;
  private Instant createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getNamespace() { return namespace; }
  public void setNamespace(String namespace) { this.namespace = namespace; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getVersion() { return version; }
  public void setVersion(String version) { this.version = version; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getDsl() { return dsl; }
  public void setDsl(String dsl) { this.dsl = dsl; }

  public JsonNode getDefinition() { return definition; }
  public void setDefinition(JsonNode definition) { this.definition = definition; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CaseMetaModel that = (CaseMetaModel) o;
    return Objects.equals(namespace, that.namespace)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() { return Objects.hash(namespace, name, version); }

  @Override
  public String toString() {
    return "CaseMetaModel{id=" + id + ", namespace='" + namespace + "', name='" + name
        + "', version='" + version + "', title='" + title + "', dsl='" + dsl
        + "', createdAt=" + createdAt + '}';
  }
}
```

- [ ] **Step 2: Compile to catch any usages that broke**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
/opt/homebrew/bin/mvn compile -q 2>&1 | grep -E "ERROR|error:" | head -20
```

Expected: errors in files that still reference `PanacheEntity` methods on `CaseMetaModel` — those will be fixed in later tasks. No errors in files that only use getters/setters.

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/model/CaseMetaModel.java
git commit -m "refactor(engine): convert CaseMetaModel to plain POJO — remove JPA annotations

Refs #69"
```

---

## Task 6: Convert CaseInstance to POJO

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java`

- [ ] **Step 1: Replace with POJO version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.model;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseStatus;
import java.util.UUID;

/** Plain domain object representing one running case. Persistence is handled by the SPI. */
public class CaseInstance {

  /** Populated by the repository after save. Null until first persisted. */
  public Long id;

  private CaseMetaModel caseMetaModel;
  private UUID uuid;
  private long version = 0L;
  private CaseContext caseContext;
  private UUID parentPlanItemId;
  private CaseStatus state;

  public CaseMetaModel getCaseMetaModel() { return caseMetaModel; }
  public void setCaseMetaModel(CaseMetaModel caseMetaModel) { this.caseMetaModel = caseMetaModel; }

  public UUID getUuid() { return uuid; }
  public void setUuid(UUID uuid) { this.uuid = uuid; }

  public long getVersion() { return version; }
  public void setVersion(long version) { this.version = version; }

  public CaseContext getCaseContext() { return caseContext; }
  public void setCaseContext(CaseContext caseContext) { this.caseContext = caseContext; }

  public UUID getParentPlanItemId() { return parentPlanItemId; }
  public void setParentPlanItemId(UUID parentPlanItemId) { this.parentPlanItemId = parentPlanItemId; }

  public CaseStatus getState() { return state; }
  public void setState(CaseStatus state) { this.state = state; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java
git commit -m "refactor(engine): convert CaseInstance to plain POJO — remove JPA annotations

Refs #69"
```

---

## Task 7: Convert EventLog to POJO

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/history/EventLog.java`

- [ ] **Step 1: Replace with POJO version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.history;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Plain domain object for an immutable audit event. {@code id} and {@code seq} are populated by
 * the repository after append. Static Panache query methods have been removed; use
 * {@link io.casehub.engine.spi.EventLogRepository} instead.
 *
 * <p>{@code workerId} is intentionally denormalised so events can be filtered by worker without
 * parsing the JSON metadata payload.
 */
public class EventLog {

  /** Populated by the repository after append. Null until first persisted. */
  public Long id;

  private UUID caseId;
  private Long seq;
  private CaseHubEventType eventType;
  private EventStreamType streamType;
  private String workerId;
  private Instant timestamp;
  private JsonNode payload;
  private JsonNode metadata;

  public UUID getCaseId() { return caseId; }
  public void setCaseId(UUID caseId) { this.caseId = caseId; }

  public Long getSeq() { return seq; }
  public void setSeq(Long seq) { this.seq = seq; }

  public CaseHubEventType getEventType() { return eventType; }
  public void setEventType(CaseHubEventType eventType) { this.eventType = eventType; }

  public EventStreamType getStreamType() { return streamType; }
  public void setStreamType(EventStreamType streamType) { this.streamType = streamType; }

  public String getWorkerId() { return workerId; }
  public void setWorkerId(String workerId) { this.workerId = workerId; }

  public Instant getTimestamp() { return timestamp; }
  public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

  public JsonNode getPayload() { return payload; }
  public void setPayload(JsonNode payload) { this.payload = payload; }

  public JsonNode getMetadata() { return metadata; }
  public void setMetadata(JsonNode metadata) { this.metadata = metadata; }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/history/EventLog.java
git commit -m "refactor(engine): convert EventLog to plain POJO — remove JPA and static query methods

Refs #69"
```

---

## Task 8: Refactor CaseDefinitionRegistry

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/CaseDefinitionRegistry.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.Milestone;
import io.casehub.api.model.PredicateBasedCompletion;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.internal.util.ReactiveUtils;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Registry for case definitions. Persists each definition's metadata via
 * {@link CaseMetaModelRepository} on startup so the engine can reference it by id.
 */
@ApplicationScoped
public class CaseDefinitionRegistry {

  private final Map<CaseMetaModel, CaseDefinition> registry = new ConcurrentHashMap<>();

  private static final Logger LOG = Logger.getLogger(CaseDefinitionRegistry.class);

  @Inject Instance<CaseHub> caseHubInstance;

  @Inject CaseMetaModelRepository caseMetaModelRepository;

  @Inject Vertx vertx;

  @Inject ExpressionEngineRegistry expressionEngineRegistry;

  void onStart(@Observes @Priority(10) StartupEvent ev) {
    ReactiveUtils.runOnSafeVertxContext(vertx, this::registerKnownDefinitions)
        .await()
        .atMost(Duration.ofSeconds(30));
  }

  Uni<Void> registerKnownDefinitions() {
    return Multi.createFrom()
        .iterable(caseHubInstance)
        .onItem()
        .transformToUniAndConcatenate(hub -> registerCaseDefinition(hub.getDefinition()))
        .collect()
        .last()
        .replaceWithVoid();
  }

  public Uni<CaseMetaModel> registerCaseDefinition(CaseDefinition model) {
    try {
      validateExpressions(model);
    } catch (IllegalArgumentException e) {
      LOG.errorf("Case definition '%s' rejected: %s", model.getName(), e.getMessage());
      return Uni.createFrom().failure(e);
    }

    LOG.info("Registering case: " + model.getName() + " version: " + model.getVersion()
        + " namespace: " + model.getNamespace());

    CaseMetaModel definition = new CaseMetaModel();
    definition.setName(model.getName());
    definition.setNamespace(model.getNamespace());
    definition.setVersion(model.getVersion());

    for (CaseMetaModel registered : registry.keySet()) {
      if (registered.equals(definition)) {
        return Uni.createFrom().item(registered);
      }
    }

    return caseMetaModelRepository.findByKey(model.getNamespace(), model.getName(), model.getVersion())
        .onItem()
        .transformToUni(existing -> {
          if (existing != null) {
            registry.put(existing, model);
            return Uni.createFrom().item(existing);
          }
          definition.setDsl(model.getDsl());
          definition.setCreatedAt(Instant.now());
          return caseMetaModelRepository.save(definition)
              .invoke(saved -> registry.put(saved, model));
        });
  }

  public CaseDefinition getCaseDefinition(CaseMetaModel definition) {
    return registry.get(definition);
  }

  public CaseMetaModel getCaseMetaModel(CaseDefinition caseDefinition) {
    for (Map.Entry<CaseMetaModel, CaseDefinition> entry : registry.entrySet()) {
      if (entry.getValue().equals(caseDefinition)) {
        return entry.getKey();
      }
    }
    throw new RuntimeException("CaseMetaModel not found for caseDefinition: "
        + caseDefinition.getNamespace() + "." + caseDefinition.getName()
        + ":" + caseDefinition.getVersion());
  }

  private void validateExpressions(CaseDefinition definition) {
    if (definition.getBindings() != null) {
      for (Binding rule : definition.getBindings()) {
        if (rule.getOn() instanceof ContextChangeTrigger cct) {
          expressionEngineRegistry.validate(cct.getFilter());
        }
        expressionEngineRegistry.validate(rule.getWhen());
      }
    }
    if (definition.getMilestones() != null) {
      for (Milestone milestone : definition.getMilestones()) {
        expressionEngineRegistry.validate(milestone.getCondition());
      }
    }
    if (definition.getGoals() != null) {
      for (Goal goal : definition.getGoals()) {
        expressionEngineRegistry.validate(goal.getCondition());
      }
    }
    if (definition.getCompletion() instanceof PredicateBasedCompletion pbc) {
      expressionEngineRegistry.validate(pbc.getDoneWhen());
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/CaseDefinitionRegistry.java
git commit -m "refactor(engine): replace Panache/SessionFactory in CaseDefinitionRegistry with CaseMetaModelRepository SPI

Refs #69"
```

---

## Task 9: Refactor CaseHubReactor

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java`

- [ ] **Step 1: Replace persistence calls**

Find and replace in `CaseHubReactor.java`:

Remove injection:
```java
@Inject Mutiny.SessionFactory sessionFactory;
```

Add injection:
```java
@Inject io.casehub.engine.spi.CaseInstanceRepository caseInstanceRepository;
```

Remove import:
```java
import org.hibernate.reactive.mutiny.Mutiny;
```

Replace `getCaseInstance` method body — change:
```java
return sessionFactory.withTransaction(session -> instance.persist());
```
to:
```java
return caseInstanceRepository.save(instance);
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
/opt/homebrew/bin/mvn compile -q 2>&1 | grep -E "^.*error:" | head -20
```

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/CaseHubReactor.java
git commit -m "refactor(engine): replace SessionFactory in CaseHubReactor with CaseInstanceRepository SPI

Refs #69"
```

---

## Task 10: Refactor CaseStartedEventHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStartedEventHandler.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.internal.event.CaseContextChangedEvent;
import io.casehub.engine.internal.event.CaseStartedEvent;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/** Records a {@code CASE_STARTED} event and notifies listeners that the context has changed. */
@ApplicationScoped
public class CaseStartedEventHandler {

  private static final Logger LOG = Logger.getLogger(CaseStartedEventHandler.class);

  @Inject EventBus eventBus;

  @Inject EventLogRepository eventLogRepository;

  @ConsumeEvent(value = EventBusAddresses.CASE_STARTED)
  public Uni<Void> onCaseStarted(CaseStartedEvent event) {
    CaseInstance instance = event.instance();
    JsonNode contextSnapshot = instance.getCaseContext().asJsonNode();

    EventLog eventLog = new EventLog();
    eventLog.setCaseId(instance.getUuid());
    eventLog.setEventType(CaseHubEventType.CASE_STARTED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setPayload(contextSnapshot);

    return eventLogRepository.append(eventLog)
        .invoke(() -> eventBus.publish(
            EventBusAddresses.CONTEXT_CHANGED,
            new CaseContextChangedEvent(instance, contextSnapshot)));
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStartedEventHandler.java
git commit -m "refactor(engine): replace Panache in CaseStartedEventHandler with EventLogRepository SPI

Refs #69"
```

---

## Task 11: Refactor CaseStatusChangedHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStatusChangedHandler.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.event.CaseStatusChanged;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Persists a case status change event and atomically updates the instance state. Publishes a
 * downstream event (CASE_COMPLETED or CASE_FAULTED) after the write commits.
 */
@ApplicationScoped
public class CaseStatusChangedHandler {

  private static final Logger LOG = Logger.getLogger(CaseStatusChangedHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject EventBus eventBus;

  @Inject CaseInstanceRepository caseInstanceRepository;

  @ConsumeEvent(value = EventBusAddresses.CASE_STATUS_CHANGED)
  public Uni<Void> onCaseStatusChangedHandler(CaseStatusChanged event) {
    CaseInstance caseInstance = event.instance();
    CaseStatus newState = CaseStatus.valueOf(event.newStatus());
    String oldStatus = event.oldStatus();

    LOG.infof("Case status changed: caseId=%s, %s -> %s",
        caseInstance.getUuid(), oldStatus, event.newStatus());

    caseInstance.setState(newState);

    EventLog eventLog = new EventLog();
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setEventType(resolveState(newState));
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setMetadata(OBJECT_MAPPER.createObjectNode()
        .put("oldStatus", oldStatus)
        .put("newStatus", event.newStatus()));

    return caseInstanceRepository.updateStateAndAppendEvent(caseInstance, eventLog)
        .invoke(() -> {
          String eventBusAddress = resolveStateAsString(newState);
          if (eventBusAddress != null) {
            eventBus.publish(eventBusAddress, caseInstance);
          }
        });
  }

  private CaseHubEventType resolveState(CaseStatus state) {
    return switch (state) {
      case COMPLETED -> CaseHubEventType.CASE_COMPLETED;
      case FAULTED -> CaseHubEventType.CASE_FAULTED;
      default -> CaseHubEventType.CASE_STATUS_CHANGED;
    };
  }

  private String resolveStateAsString(CaseStatus state) {
    return switch (state) {
      case COMPLETED -> EventBusAddresses.CASE_COMPLETED;
      case FAULTED -> EventBusAddresses.CASE_FAULTED;
      default -> null;
    };
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseStatusChangedHandler.java
git commit -m "refactor(engine): replace Panache in CaseStatusChangedHandler with CaseInstanceRepository.updateStateAndAppendEvent

Refs #69"
```

---

## Task 12: Refactor WorkerRetriesExhaustedEventHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerRetriesExhaustedEventHandler.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.event.CaseStatusChanged;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkerRetriesExhaustedEvent;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Handles worker retry exhaustion by marking the case as FAULTED. Atomically updates the instance
 * state and appends the event log entry.
 */
@ApplicationScoped
public class WorkerRetriesExhaustedEventHandler {

  private static final Logger LOG = Logger.getLogger(WorkerRetriesExhaustedEventHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject CaseInstanceCache caseInstanceCache;

  @Inject EventBus eventBus;

  @Inject CaseInstanceRepository caseInstanceRepository;

  @ConsumeEvent(value = EventBusAddresses.WORKER_RETRIES_EXHAUSTED)
  public Uni<Void> onWorkerRetriesExhaustedEvent(WorkerRetriesExhaustedEvent event) {
    CaseInstance caseInstance = caseInstanceCache.get(event.caseId());
    String oldStatus = caseInstance.getState().name();
    caseInstance.setState(CaseStatus.FAULTED);

    EventLog eventLog = new EventLog();
    eventLog.setEventType(CaseHubEventType.CASE_FAULTED);
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setWorkerId(event.workerId());
    eventLog.setMetadata(OBJECT_MAPPER.createObjectNode()
        .put("workerId", event.workerId())
        .put("inputDataHash", event.idempotency()));

    return caseInstanceRepository.updateStateAndAppendEvent(caseInstance, eventLog)
        .invoke(() -> {
          LOG.warnf("Worker retries exhausted for caseId=%s, workerId=%s",
              event.caseId(), event.workerId());
          eventBus.publish(EventBusAddresses.CASE_STATUS_CHANGED,
              new CaseStatusChanged(caseInstance, oldStatus, CaseStatus.FAULTED.name()));
        });
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerRetriesExhaustedEventHandler.java
git commit -m "refactor(engine): replace Panache in WorkerRetriesExhaustedEventHandler with CaseInstanceRepository SPI

Refs #69"
```

---

## Task 13: Refactor WorkflowExecutionCompletedHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.model.Worker;
import io.casehub.api.spi.ContextDiffStrategy;
import io.casehub.engine.internal.event.CaseContextChangedEvent;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkflowExecutionCompleted;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Applies worker output to the case context, persists the completion event, and notifies listeners
 * that the context has changed.
 */
@ApplicationScoped
public class WorkflowExecutionCompletedHandler {

  @Inject EventBus eventBus;
  @Inject ContextDiffStrategy contextDiffStrategy;
  @Inject EventLogRepository eventLogRepository;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Logger LOG = Logger.getLogger(WorkflowExecutionCompletedHandler.class);

  @ConsumeEvent(value = EventBusAddresses.WORKER_EXECUTION_FINISHED)
  public Uni<Void> onWorkflowExecutionCompletedHandler(WorkflowExecutionCompleted event) {
    final CaseInstance caseInstance = event.caseInstance();
    final Worker worker = event.worker();
    final Map<String, Object> rawOutput = event.output() == null ? Map.of() : event.output();
    final Instant now = Instant.now();

    JsonNode contextBefore = caseInstance.getCaseContext().snapshot().asJsonNode();
    caseInstance.getCaseContext().setAll(rawOutput);
    JsonNode contextAfter = caseInstance.getCaseContext().asJsonNode();
    JsonNode diff = contextDiffStrategy.compute(contextBefore, contextAfter);

    EventLog eventLog = buildEventLog(caseInstance, worker, rawOutput, event.idempotency(), now, diff);

    return eventLogRepository.append(eventLog)
        .invoke(() -> eventBus.publish(
            EventBusAddresses.CONTEXT_CHANGED,
            new CaseContextChangedEvent(caseInstance, contextAfter)))
        .replaceWithVoid()
        .onFailure()
        .invoke(t -> LOG.error(
            "Failed to handle WorkflowExecutionCompleted for caseId: " + caseInstance.getUuid(), t));
  }

  private EventLog buildEventLog(CaseInstance caseInstance, Worker worker,
      Map<String, Object> output, String idempotency, Instant timestamp, JsonNode contextDiff) {
    EventLog eventLog = new EventLog();
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setWorkerId(worker.getName());
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(timestamp);
    eventLog.setEventType(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    eventLog.setPayload(OBJECT_MAPPER.valueToTree(output == null ? Map.of() : output));
    eventLog.setMetadata(buildMetadata(idempotency, contextDiff));
    return eventLog;
  }

  private JsonNode buildMetadata(String idempotency, JsonNode contextDiff) {
    ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
    metadata.put("inputDataHash", idempotency);
    if (contextDiff != null) {
      metadata.set("contextChanges", contextDiff);
    }
    return metadata;
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkflowExecutionCompletedHandler.java
git commit -m "refactor(engine): replace Panache in WorkflowExecutionCompletedHandler with EventLogRepository SPI

Refs #69"
```

---

## Task 14: Refactor GoalReachedEventHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/GoalReachedEventHandler.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import static io.casehub.engine.internal.history.CaseHubEventType.GOAL_REACHED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.CaseCompletion;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.CaseStatus;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalBasedCompletion;
import io.casehub.api.model.GoalExpression;
import io.casehub.engine.internal.engine.CaseDefinitionRegistry;
import io.casehub.engine.internal.event.CaseStatusChanged;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.GoalReachedEvent;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/** Records a GOAL_REACHED event and evaluates whether the case has reached a terminal state. */
@ApplicationScoped
public class GoalReachedEventHandler {

  private static final Logger LOG = Logger.getLogger(GoalReachedEventHandler.class);

  @Inject CaseDefinitionRegistry caseDefinitionRegistry;

  @Inject EventBus eventBus;

  @Inject EventLogRepository eventLogRepository;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @ConsumeEvent(value = EventBusAddresses.GOAL_REACHED)
  public Uni<Void> onGoalReachedEventHandler(GoalReachedEvent event) {
    CaseInstance caseInstance = event.caseInstance();
    CaseDefinition definition = caseDefinitionRegistry.getCaseDefinition(caseInstance.getCaseMetaModel());
    Goal goal = event.goal();

    EventLog eventLog = new EventLog();
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setEventType(GOAL_REACHED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setMetadata(OBJECT_MAPPER.createObjectNode()
        .put("name", goal.getName())
        .put("description", goal.getDescription())
        .put("kind", goal.getKind().value())
        .put("isTerminal", goal.getTerminal()));

    return eventLogRepository.append(eventLog)
        .chain(() -> evaluateCompletion(caseInstance, definition.getCompletion()));
  }

  private Uni<Void> evaluateCompletion(CaseInstance caseInstance, CaseCompletion completion) {
    if (completion == null || !(completion instanceof GoalBasedCompletion goalBasedCompletion)) {
      return Uni.createFrom().voidItem();
    }

    return eventLogRepository.findByCaseAndTypes(caseInstance.getUuid(), Set.of(GOAL_REACHED))
        .chain(eventLogs -> {
          Set<String> reachedGoals = eventLogs.stream()
              .map(el -> el.getMetadata().get("name").asText())
              .collect(Collectors.toSet());

          LOG.infof("Evaluating completion for caseId=%s, reachedGoals=%s",
              caseInstance.getUuid(), reachedGoals);

          String oldStatus = caseInstance.getState().name();

          if (goalBasedCompletion.getFailure() != null
              && isGoalExpressionSatisfied(goalBasedCompletion.getFailure(), reachedGoals)) {
            LOG.infof("Case FAILED: caseId=%s", caseInstance.getUuid());
            eventBus.publish(EventBusAddresses.CASE_STATUS_CHANGED,
                new CaseStatusChanged(caseInstance, oldStatus, CaseStatus.FAULTED.name()));
            return Uni.createFrom().voidItem();
          }

          if (goalBasedCompletion.getSuccess() != null
              && isGoalExpressionSatisfied(goalBasedCompletion.getSuccess(), reachedGoals)) {
            LOG.infof("Case COMPLETED: caseId=%s", caseInstance.getUuid());
            eventBus.publish(EventBusAddresses.CASE_STATUS_CHANGED,
                new CaseStatusChanged(caseInstance, oldStatus, CaseStatus.COMPLETED.name()));
            return Uni.createFrom().voidItem();
          }

          return Uni.createFrom().voidItem();
        });
  }

  private boolean isGoalExpressionSatisfied(GoalExpression expression, Set<String> reachedGoals) {
    if (expression == null || expression.getGoals() == null || expression.getGoals().isEmpty()) {
      return false;
    }
    Set<String> expressionGoalNames = expression.getGoals().stream()
        .map(Goal::getName).collect(Collectors.toSet());
    if (expression instanceof io.casehub.api.model.AllOfGoalExpression) {
      return reachedGoals.containsAll(expressionGoalNames);
    }
    if (expression instanceof io.casehub.api.model.AnyOfGoalExpression) {
      return expressionGoalNames.stream().anyMatch(reachedGoals::contains);
    }
    return false;
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/GoalReachedEventHandler.java
git commit -m "refactor(engine): replace Panache in GoalReachedEventHandler with EventLogRepository SPI

Refs #69"
```

---

## Task 15: Refactor MilestoneReachedEventHandler and SignalReceivedEventHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/MilestoneReachedEventHandler.java`
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/SignalReceivedEventHandler.java`

- [ ] **Step 1: Replace MilestoneReachedEventHandler**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import static io.casehub.engine.internal.history.CaseHubEventType.MILESTONE_REACHED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.model.Milestone;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.MilestoneReachedEvent;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;

/** Records a MILESTONE_REACHED event. */
@ApplicationScoped
public class MilestoneReachedEventHandler {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject EventLogRepository eventLogRepository;

  @ConsumeEvent(value = EventBusAddresses.MILESTONE_REACHED)
  public Uni<Void> onMilestoneReachedEventHandler(MilestoneReachedEvent event) {
    CaseInstance caseInstance = event.caseInstance();
    Milestone milestone = event.milestone();

    EventLog eventLog = new EventLog();
    eventLog.setCaseId(caseInstance.getUuid());
    eventLog.setEventType(MILESTONE_REACHED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setMetadata(OBJECT_MAPPER.createObjectNode()
        .put("name", milestone.getName())
        .put("description", milestone.getDescription()));

    return eventLogRepository.append(eventLog);
  }
}
```

- [ ] **Step 2: Replace SignalReceivedEventHandler**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.handler;

import static io.casehub.engine.internal.event.EventBusAddresses.CONTEXT_CHANGED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.engine.recovery.WorkerExecutionRecoveryService;
import io.casehub.engine.internal.event.CaseContextChangedEvent;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.SignalReceivedEvent;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Applies an external signal to the case context, persists the event, and notifies listeners that
 * the context has changed.
 */
@ApplicationScoped
public class SignalReceivedEventHandler {

  private static final Logger LOG = Logger.getLogger(SignalReceivedEventHandler.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Inject EventBus eventBus;

  @Inject CaseInstanceCache caseInstanceCache;

  @Inject WorkerExecutionRecoveryService recoveryService;

  @Inject EventLogRepository eventLogRepository;

  @ConsumeEvent(value = EventBusAddresses.SIGNAL_RECEIVED)
  public Uni<Void> onSignalReceived(SignalReceivedEvent event) {
    CaseInstance cached = caseInstanceCache.get(event.caseId());
    if (cached != null) {
      return applySignal(cached, event);
    }
    LOG.warnf("CaseInstance not found in cache for caseId=%s, trying recovery", event.caseId());
    return recoveryService.loadOrRestoreCaseInstance(event.caseId())
        .chain(instance -> applySignal(instance, event));
  }

  private Uni<Void> applySignal(CaseInstance instance, SignalReceivedEvent event) {
    Optional<JsonNode> maybeDiff = instance.getCaseContext().applyAndDiff(event.path(), event.value());

    if (maybeDiff.isEmpty()) {
      LOG.debugf("Signal path='%s' produced no state change for caseId=%s — skipping",
          event.path(), event.caseId());
      return Uni.createFrom().voidItem();
    }

    JsonNode diff = maybeDiff.get();
    JsonNode contextSnapshot = instance.getCaseContext().asJsonNode();
    EventLog eventLog = buildSignalEventLog(instance, diff);

    return eventLogRepository.append(eventLog)
        .invoke(() -> eventBus.publish(CONTEXT_CHANGED, new CaseContextChangedEvent(instance, contextSnapshot)))
        .replaceWithVoid()
        .onFailure()
        .invoke(t -> LOG.errorf(t, "Failed to process signal path='%s' for caseId=%s",
            event.path(), event.caseId()));
  }

  private EventLog buildSignalEventLog(CaseInstance instance, JsonNode diff) {
    EventLog eventLog = new EventLog();
    eventLog.setCaseId(instance.getUuid());
    eventLog.setEventType(CaseHubEventType.SIGNAL_RECEIVED);
    eventLog.setStreamType(EventStreamType.CASE);
    eventLog.setTimestamp(Instant.now());
    eventLog.setPayload(OBJECT_MAPPER.createObjectNode().set("patch", diff.deepCopy()));
    return eventLog;
  }
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/MilestoneReachedEventHandler.java
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/SignalReceivedEventHandler.java
git commit -m "refactor(engine): replace Panache in MilestoneReachedEventHandler and SignalReceivedEventHandler with EventLogRepository SPI

Refs #69"
```

---

## Task 16: Refactor WorkerScheduleEventHandler

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java`

- [ ] **Step 1: Replace persistence calls**

In `WorkerScheduleEventHandler.java`, make these targeted changes:

**Add injection** (add after existing `@Inject` fields):
```java
@Inject io.casehub.engine.spi.EventLogRepository eventLogRepository;
```

**Remove import:**
```java
import io.quarkus.hibernate.reactive.panache.Panache;
```

**Replace `onWorkerScheduleEventHandler` — the persistence section** (the `return Panache.withTransaction(...)` block):

Change:
```java
return Panache.withTransaction(
        () ->
            EventLog.findSchedulingEvents(instance.getUuid(), worker.getName())
                .map(existing -> decideAction(existing, inputDataHash))
                .chain(action -> executeAction(action, eventLog, instance, worker, capability)))
    .chain(eventLogId -> submitIfNeeded(eventLogId, instance, worker, capability, inputData))
```

To:
```java
return eventLogRepository.findSchedulingEvents(instance.getUuid(), worker.getName())
    .map(existing -> decideAction(existing, inputDataHash))
    .chain(action -> executeAction(action, eventLog, instance, worker, capability))
    .chain(eventLogId -> submitIfNeeded(eventLogId, instance, worker, capability, inputData))
```

**Replace `executeAction` method — the `CREATE_NEW` case**:

Change:
```java
case CREATE_NEW -> eventLog.persistScheduledEvent();
```

To:
```java
case CREATE_NEW -> eventLogRepository.appendAndReturnId(eventLog);
```

**Replace `decideAction` — direct field access on `eventLog.id`** — this still works because `EventLog.id` is now `public Long id`. No change needed there.

- [ ] **Step 2: Compile check**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
/opt/homebrew/bin/mvn compile -q 2>&1 | grep "error:" | head -20
```

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/handler/WorkerScheduleEventHandler.java
git commit -m "refactor(engine): replace Panache in WorkerScheduleEventHandler with EventLogRepository SPI

Refs #69"
```

---

## Task 17: Refactor WorkerExecutionManager

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionManager.java`

- [ ] **Step 1: Apply targeted changes**

**Add injection:**
```java
@Inject io.casehub.engine.spi.EventLogRepository eventLogRepository;
```

**Remove import:**
```java
import io.quarkus.hibernate.reactive.panache.Panache;
```

**Replace `submit` method body** — change:
```java
return Panache.withTransaction(
        () ->
            EventLog.<EventLog>findById(eventLogId)
                .onItem()
                .ifNull()
                .failWith(() -> new NotFoundException("EventLog not found: id=" + eventLogId))
                .replaceWithVoid())
    .chain(() -> scheduleQuartzJob(eventLogId, instance, worker, idempotency, group));
```
To:
```java
return eventLogRepository.findById(eventLogId)
    .onItem()
    .ifNull()
    .failWith(() -> new NotFoundException("EventLog not found: id=" + eventLogId))
    .replaceWithVoid()
    .chain(() -> scheduleQuartzJob(eventLogId, instance, worker, idempotency, group));
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionManager.java
git commit -m "refactor(engine): replace Panache in WorkerExecutionManager with EventLogRepository SPI

Refs #69"
```

---

## Task 18: Refactor WorkerExecutionJobListener

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionJobListener.java`

- [ ] **Step 1: Apply targeted changes**

**Add injection:**
```java
@Inject io.casehub.engine.spi.EventLogRepository eventLogRepository;
```

**Remove imports:**
```java
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
```

**Replace `persistEventLog` method:**
```java
private Uni<Void> persistEventLog(String jobName, EventLog eventLog) {
  return runOnSafeVertxContext(() -> eventLogRepository.append(eventLog))
      .onFailure()
      .invoke(ex -> LOG.errorf(ex, "Failed to persist event for job: %s", jobName));
}
```

**Replace `countFailedAttempts` method:**
```java
private Uni<Long> countFailedAttempts(UUID caseId, String workerId, String idempotency) {
  return runOnSafeVertxContext(
      () -> eventLogRepository.findByCaseAndWorkerAndType(
              caseId, workerId, CaseHubEventType.WORKER_EXECUTION_FAILED)
          .map(eventLogs -> eventLogs.stream()
              .filter(eventLog -> {
                JsonNode metadata = eventLog.getMetadata();
                JsonNode idempotencyNode = metadata == null ? null : metadata.get("inputDataHash");
                return idempotencyNode != null && idempotency.equals(idempotencyNode.asText());
              })
              .count()));
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
/opt/homebrew/bin/mvn compile -q 2>&1 | grep "error:" | head -20
```

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionJobListener.java
git commit -m "refactor(engine): replace Panache in WorkerExecutionJobListener with EventLogRepository SPI

Refs #69"
```

---

## Task 19: Refactor WorkerExecutionTask

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionTask.java`

- [ ] **Step 1: Apply targeted changes**

**Add injection:**
```java
@Inject io.casehub.engine.spi.EventLogRepository eventLogRepository;
```

**Remove imports:**
```java
import io.quarkus.hibernate.reactive.panache.Panache;
```

**Replace `findEventLog` method:**
```java
private Uni<EventLog> findEventLog(String eventLogId) {
  return ReactiveUtils.runOnSafeVertxContext(
      vertx, () -> eventLogRepository.findById(Long.parseLong(eventLogId)));
}
```

Note: `EventLog.findById(eventLogId)` previously accepted a `String` (Panache auto-coerces). After decoupling, `EventLogRepository.findById(Long id)` takes a `Long`, so `Long.parseLong` is required.

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/worker/WorkerExecutionTask.java
git commit -m "refactor(engine): replace Panache in WorkerExecutionTask with EventLogRepository SPI

Refs #69"
```

---

## Task 20: Refactor WorkerExecutionRecoveryService

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/recovery/WorkerExecutionRecoveryService.java`

- [ ] **Step 1: Replace with repository-backed version**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine.internal.engine.recovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.api.context.CaseContext;
import io.casehub.engine.internal.context.CaseContextImpl;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.util.ReactiveUtils;
import io.casehub.engine.internal.worker.WorkerExecutionManager;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.engine.spi.EventLogRepository;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Restores in-flight workers and case state after a restart. Uses the repository SPI — no direct
 * Hibernate session access.
 */
@ApplicationScoped
public class WorkerExecutionRecoveryService {

  private static final Logger LOG = Logger.getLogger(WorkerExecutionRecoveryService.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final EnumSet<CaseHubEventType> RELEVANT_RECOVERY_EVENTS =
      EnumSet.of(
          CaseHubEventType.WORKER_SCHEDULED,
          CaseHubEventType.WORKER_EXECUTION_STARTED,
          CaseHubEventType.WORKER_EXECUTION_COMPLETED,
          CaseHubEventType.WORKER_EXECUTION_FAILED);

  @Inject CaseInstanceRepository caseInstanceRepository;

  @Inject EventLogRepository eventLogRepository;

  @Inject Vertx vertx;

  @Inject CaseInstanceCache caseInstanceCache;

  @Inject WorkerExecutionManager workflowExecutionManager;

  public Uni<CaseInstance> loadOrRestoreCaseInstance(UUID caseId) {
    CaseInstance cached = caseInstanceCache.get(caseId);
    if (cached != null) {
      return Uni.createFrom().item(cached);
    }

    return runOnSafeContext(() -> caseInstanceRepository.findByUuid(caseId))
        .onItem()
        .ifNull()
        .failWith(() -> new IllegalStateException("CaseInstance not found for caseId=" + caseId))
        .chain(instance ->
            rebuildStateContext(caseId).map(stateContext -> {
              instance.setCaseContext(stateContext);
              caseInstanceCache.put(instance);
              return instance;
            }));
  }

  public Uni<Void> recoverPendingScheduledWorkers() {
    return runOnSafeContext(() -> eventLogRepository.findByTypes(RELEVANT_RECOVERY_EVENTS))
        .chain(this::reschedulePendingEvents);
  }

  private Uni<Void> reschedulePendingEvents(List<EventLog> eventLogs) {
    Set<String> alreadyProgressed = new HashSet<>();
    for (EventLog eventLog : eventLogs) {
      if (eventLog.getEventType() != CaseHubEventType.WORKER_SCHEDULED) {
        String key = executionKey(eventLog);
        if (key != null) {
          alreadyProgressed.add(key);
        }
      }
    }

    List<Uni<Void>> recoveries = eventLogs.stream()
        .filter(eventLog -> {
          if (eventLog.getEventType() != CaseHubEventType.WORKER_SCHEDULED) {
            return false;
          }
          String key = executionKey(eventLog);
          return key != null && !alreadyProgressed.contains(key);
        })
        .map(workflowExecutionManager::schedulePersistedEvent)
        .toList();

    if (recoveries.isEmpty()) {
      return Uni.createFrom().voidItem();
    }

    return Uni.combine().all().unis(recoveries).discardItems();
  }

  @SuppressWarnings("unchecked")
  private Uni<CaseContext> rebuildStateContext(UUID caseId) {
    return runOnSafeContext(() ->
        eventLogRepository.findByCaseAndTypes(caseId,
            EnumSet.of(
                CaseHubEventType.CASE_STARTED,
                CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                CaseHubEventType.SIGNAL_RECEIVED)))
        .map(eventLogs -> {
          CaseContext caseContext = new CaseContextImpl();
          EventLog caseStartedEvent = eventLogs.stream()
              .filter(e -> e.getEventType() == CaseHubEventType.CASE_STARTED)
              .findFirst().orElse(null);

          if (caseStartedEvent != null) {
            caseContext = new CaseContextImpl(payloadAsMap(caseStartedEvent.getPayload()));
          }

          for (EventLog eventLog : eventLogs) {
            if (eventLog.getEventType() == CaseHubEventType.CASE_STARTED) {
              continue;
            }
            if (eventLog.getEventType() == CaseHubEventType.SIGNAL_RECEIVED) {
              JsonNode patch = payloadAsPatch(eventLog.getPayload());
              if (patch != null) {
                caseContext.applyDiff(patch);
              }
            } else if (eventLog.getEventType() == CaseHubEventType.WORKER_EXECUTION_COMPLETED) {
              caseContext.setAll(payloadAsMap(eventLog.getPayload()));
            } else {
              LOG.warnf("Unexpected event type in rebuildStateContext: %s", eventLog.getEventType());
            }
          }
          return caseContext;
        });
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> payloadAsMap(JsonNode payload) {
    return OBJECT_MAPPER.convertValue(
        payload == null ? OBJECT_MAPPER.createObjectNode() : payload, Map.class);
  }

  private JsonNode payloadAsPatch(JsonNode payload) {
    if (payload == null || payload.isNull()) return null;
    JsonNode patch = payload.get("patch");
    return patch != null && patch.isArray() ? patch : null;
  }

  private String executionKey(EventLog eventLog) {
    JsonNode metadata = eventLog.getMetadata();
    if (metadata == null || eventLog.getCaseId() == null || eventLog.getWorkerId() == null) {
      return null;
    }
    JsonNode inputDataHash = metadata.get("inputDataHash");
    if (inputDataHash == null || inputDataHash.isNull()) return null;
    return eventLog.getCaseId() + "|" + eventLog.getWorkerId() + "|" + inputDataHash.asText();
  }

  private <T> Uni<T> runOnSafeContext(java.util.function.Supplier<Uni<? extends T>> supplier) {
    return ReactiveUtils.runOnSafeVertxContext(vertx, supplier);
  }
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/internal/engine/recovery/WorkerExecutionRecoveryService.java
git commit -m "refactor(engine): replace SessionFactory/Panache in WorkerExecutionRecoveryService with repository SPIs

Refs #69"
```

---

## Task 21: Update engine pom.xml

**Files:**
- Modify: `engine/pom.xml`

- [ ] **Step 1: Remove JPA dependencies**

Remove these four blocks from `engine/pom.xml`:
```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-hibernate-reactive-panache</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-reactive-pg-client</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-flyway</artifactId>
</dependency>
```

Remove this test dependency block:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Add memory module as test dependency**

Add after the existing quarkus-junit5 test dependency:
```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-persistence-memory</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/pom.xml
git commit -m "chore(engine): remove JPA/Panache/PostgreSQL/Flyway deps — add persistence-memory as test dep

Refs #69"
```

---

## Task 22: Rewrite engine test application.properties

**Files:**
- Modify: `engine/src/test/resources/application.properties`

- [ ] **Step 1: Replace contents entirely**

```properties
# Engine tests use in-memory persistence — no Docker, no PostgreSQL required.

# Quartz: use in-memory store (no database connection needed)
quarkus.quartz.store-type=ram

# Activate in-memory repository implementations for all three SPIs
quarkus.arc.selected-alternatives=\
  io.casehub.persistence.memory.InMemoryCaseMetaModelRepository,\
  io.casehub.persistence.memory.InMemoryCaseInstanceRepository,\
  io.casehub.persistence.memory.InMemoryEventLogRepository

# CDI must discover @Alternative beans from the memory module jar
quarkus.index-dependency.memory.group-id=io.casehub
quarkus.index-dependency.memory.artifact-id=casehub-persistence-memory
```

- [ ] **Step 2: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/test/resources/application.properties
git commit -m "chore(engine): replace test application.properties — RAM Quartz, in-memory persistence alternatives

Refs #69"
```

---

## Task 23: Build verification

- [ ] **Step 1: Rebuild all modules**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn install -DskipTests -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Compile engine specifically to catch remaining errors**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
/opt/homebrew/bin/mvn compile -q 2>&1 | grep "error:" | head -30
```

Expected: no errors. If any remain, they will be in files that still import Panache/Hibernate — fix them following the patterns above.

---

## Task 24: Add happy-path E2E test

**Files:**
- Create: `engine/src/test/java/io/casehub/engine/EngineDecouplingIT.java`

- [ ] **Step 1: Write the test**

```java
/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.casehub.engine.spi.EventLogRepository;
import io.casehub.persistence.memory.InMemoryCaseInstanceRepository;
import io.casehub.persistence.memory.InMemoryCaseMetaModelRepository;
import io.casehub.persistence.memory.InMemoryEventLogRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the engine uses in-memory repository implementations — no Docker, no PostgreSQL.
 * This test confirms the persistence decoupling is complete and functional end-to-end.
 */
@QuarkusTest
class EngineDecouplingIT {

  @Inject CaseMetaModelRepository caseMetaModelRepository;

  @Inject CaseInstanceRepository caseInstanceRepository;

  @Inject EventLogRepository eventLogRepository;

  @Test
  void repositoriesAreInMemoryImplementations() {
    assertThat(caseMetaModelRepository).isInstanceOf(InMemoryCaseMetaModelRepository.class);
    assertThat(caseInstanceRepository).isInstanceOf(InMemoryCaseInstanceRepository.class);
    assertThat(eventLogRepository).isInstanceOf(InMemoryEventLogRepository.class);
  }

  @Test
  void eventLogRepository_appendAndFind_happyPath() {
    EventLog eventLog = new EventLog();
    eventLog.setCaseId(java.util.UUID.randomUUID());
    eventLog.setEventType(CaseHubEventType.CASE_STARTED);
    eventLog.setStreamType(io.casehub.engine.internal.history.EventStreamType.CASE);
    eventLog.setTimestamp(java.time.Instant.now());

    eventLogRepository.append(eventLog).subscribe().asCompletionStage().toCompletableFuture().join();

    assertThat(eventLog.id).isNotNull().isPositive();
    assertThat(eventLog.getSeq()).isNotNull().isPositive();

    EventLog found = eventLogRepository.findById(eventLog.id)
        .subscribe().asCompletionStage().toCompletableFuture().join();
    assertThat(found).isNotNull();
    assertThat(found.getEventType()).isEqualTo(CaseHubEventType.CASE_STARTED);
  }

  @Test
  void eventLogRepository_findByTypes_returnsMatchingEvents() {
    java.util.UUID caseId = java.util.UUID.randomUUID();

    EventLog started = new EventLog();
    started.setCaseId(caseId);
    started.setEventType(CaseHubEventType.CASE_STARTED);
    started.setStreamType(io.casehub.engine.internal.history.EventStreamType.CASE);
    started.setTimestamp(java.time.Instant.now());
    eventLogRepository.append(started).subscribe().asCompletionStage().toCompletableFuture().join();

    EventLog completed = new EventLog();
    completed.setCaseId(caseId);
    completed.setEventType(CaseHubEventType.CASE_COMPLETED);
    completed.setStreamType(io.casehub.engine.internal.history.EventStreamType.CASE);
    completed.setTimestamp(java.time.Instant.now());
    eventLogRepository.append(completed).subscribe().asCompletionStage().toCompletableFuture().join();

    List<EventLog> found = eventLogRepository
        .findByTypes(List.of(CaseHubEventType.CASE_STARTED, CaseHubEventType.CASE_COMPLETED))
        .subscribe().asCompletionStage().toCompletableFuture().join();

    assertThat(found).hasSizeGreaterThanOrEqualTo(2);
    assertThat(found).anyMatch(e -> e.getEventType() == CaseHubEventType.CASE_STARTED);
    assertThat(found).anyMatch(e -> e.getEventType() == CaseHubEventType.CASE_COMPLETED);
  }
}
```

- [ ] **Step 2: Run just this test (no Docker)**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test \
  -pl engine \
  -Dtest=EngineDecouplingIT -q 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, 3 tests pass, no Docker started.

- [ ] **Step 3: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/test/java/io/casehub/engine/EngineDecouplingIT.java
git commit -m "test(engine): add EngineDecouplingIT — verifies in-memory repositories active, no Docker

Refs #69"
```

---

## Task 25: Run full engine test suite without Docker

- [ ] **Step 1: Rebuild memory module (in case of any changes)**

```bash
cd /Users/mdproctor/dev/casehub-engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn install -DskipTests -q -pl casehub-persistence-memory
```

- [ ] **Step 2: Run ALL engine tests**

```bash
cd /Users/mdproctor/dev/casehub-engine/engine
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -q 2>&1 | tail -20
```

Expected: `BUILD SUCCESS`, all tests pass, no Docker/TestContainers started (no `Starting testcontainers` lines in output).

- [ ] **Step 3: If tests fail, diagnose**

Common failure modes:
- `CDI bean not found for EventLogRepository` — check `quarkus.index-dependency.memory.*` is in test properties
- `@Alternative not selected` — check `quarkus.arc.selected-alternatives` lists all three fully-qualified class names
- `NullPointerException in eventLogRepository.append` — check `InMemoryCaseInstanceRepository.eventLogRepository` is injected (CDI test context)
- `Long.parseLong` error — check `WorkerExecutionTask.findEventLog` uses `Long.parseLong(eventLogId)` not the raw string

Fix any failures, then re-run until clean.

- [ ] **Step 4: Commit any fixes**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add -p   # stage only fix files
git commit -m "fix(engine): resolve test failures after persistence decoupling

Refs #69"
```

---

## Task 26: Documentation pass

**Files to update:**
- `engine/src/main/java/io/casehub/engine/spi/CaseMetaModelRepository.java` — add method Javadocs
- `engine/src/main/java/io/casehub/engine/spi/EventLogRepository.java` — add method Javadocs
- `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` — update persistence notes
- `/Users/mdproctor/claude/casehub/docs/DESIGN.md` — update engine persistence section

- [ ] **Step 1: Add Javadocs to CaseMetaModelRepository**

```java
/**
 * Storage provider for {@link io.casehub.engine.internal.model.CaseMetaModel} definitions.
 * Implementations handle their own session/transaction management.
 */
public interface CaseMetaModelRepository {

  /**
   * Find a registered case type by its natural key. Returns {@code null} if not found.
   *
   * @param namespace the case namespace (may be null for unnamespaced definitions)
   * @param name the case name
   * @param version the semantic version string
   */
  Uni<CaseMetaModel> findByKey(String namespace, String name, String version);

  /**
   * Persist a new case meta model. Sets {@code metaModel.id} and {@code metaModel.createdAt} on
   * completion if not already set.
   */
  Uni<CaseMetaModel> save(CaseMetaModel metaModel);
}
```

- [ ] **Step 2: Add Javadocs to EventLogRepository**

Open `EventLogRepository.java` and add method-level Javadocs:

```java
/**
 * Storage provider for immutable {@link EventLog} entries. All writes are append-only.
 * Implementations handle their own session/transaction management.
 */
public interface EventLogRepository {

  /** Append an event. Sets {@code eventLog.id} and {@code eventLog.seq} on completion. */
  Uni<Void> append(EventLog eventLog);

  /** Append an event and return its generated id. Sets {@code eventLog.id} and {@code eventLog.seq}. */
  Uni<Long> appendAndReturnId(EventLog eventLog);

  /** Find an event by its generated id. Returns {@code null} if not found. */
  Uni<EventLog> findById(Long id);

  /**
   * Find all scheduling-lifecycle events (WORKER_SCHEDULED, WORKER_EXECUTION_STARTED,
   * WORKER_EXECUTION_COMPLETED) for the given case and worker, ordered by seq ascending.
   */
  Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId);

  /**
   * Find all events matching the given types across all cases, ordered by seq ascending.
   * Used by recovery to replay in-flight workers.
   */
  Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types);

  /**
   * Find events for a specific case matching the given types, ordered by seq ascending.
   */
  Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types);

  /**
   * Find events for a specific case, worker, and event type (all criteria must match).
   */
  Uni<List<EventLog>> findByCaseAndWorkerAndType(UUID caseId, String workerId, CaseHubEventType type);
}
```

- [ ] **Step 3: Update CLAUDE.md in casehub-engine**

Open `/Users/mdproctor/dev/casehub-engine/CLAUDE.md` and add or update the persistence section:

```markdown
## Persistence Architecture

The engine module has **no JPA dependency**. Persistence is routed through three SPI interfaces
in `engine/src/main/java/io/casehub/engine/spi/`:

- `CaseMetaModelRepository` — find/save CaseMetaModel definitions
- `CaseInstanceRepository` — save/update/find CaseInstance + atomic updateStateAndAppendEvent
- `EventLogRepository` — append-only event log with query methods

**Production implementation:** `casehub-persistence-hibernate` (JPA/Panache, PostgreSQL)
**Test implementation:** `casehub-persistence-memory` (in-memory, no Docker)

Engine tests activate the memory implementation via:
```properties
quarkus.arc.selected-alternatives=io.casehub.persistence.memory.InMemory*
quarkus.index-dependency.memory.group-id=io.casehub
quarkus.index-dependency.memory.artifact-id=casehub-persistence-memory
```

Domain objects (`CaseMetaModel`, `CaseInstance`, `EventLog`) are plain POJOs. The `id` field
is public and set by the repository after save.
```

- [ ] **Step 4: Update docs/DESIGN.md in casehub repo**

Open `/Users/mdproctor/claude/casehub/docs/DESIGN.md` and find the engine persistence section. Update it to reflect that:
- Domain objects are plain POJOs (no JPA)
- Three SPI interfaces route persistence
- Engine tests run without Docker via in-memory implementations
- `casehub-persistence-hibernate` holds all JPA entity classes and Panache repositories

- [ ] **Step 5: Commit documentation**

```bash
cd /Users/mdproctor/dev/casehub-engine
git add engine/src/main/java/io/casehub/engine/spi/
git add CLAUDE.md
git commit -m "docs(engine): add Javadocs to SPI interfaces, update CLAUDE.md persistence section

Refs #69"
```

---

## Task 27: Create PR

- [ ] **Step 1: Push branch**

```bash
cd /Users/mdproctor/dev/casehub-engine
git push -u origin feat/persistence/engine-decoupling
```

- [ ] **Step 2: Create PR against feat/persistence/memory**

```bash
gh pr create \
  --repo casehubio/engine \
  --base feat/persistence/memory \
  --title "feat: engine persistence decoupling — strip JPA, use repository SPI" \
  --body "$(cat <<'EOF'
## Summary

- Converts `CaseMetaModel`, `CaseInstance`, and `EventLog` from JPA entities (`PanacheEntity`) to plain POJOs
- Removes all `Panache.*`, `SessionFactory`, and static Panache query methods from 12 engine classes
- Adds `updateStateAndAppendEvent` to `CaseInstanceRepository` SPI with implementations in both persistence modules
- Engine tests now run without Docker using `casehub-persistence-memory` via `quarkus.arc.selected-alternatives`
- Removes `quarkus-hibernate-reactive-panache`, `quarkus-reactive-pg-client`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, and `testcontainers-postgresql` from `engine/pom.xml`

## Test plan

- [ ] Unit test: `InMemoryCaseInstanceRepositoryTest#updateStateAndAppendEvent_updatesInstanceAndAppendsEvent` passes without Quarkus
- [ ] Integration test: `JpaCaseInstanceRepositoryTest#updateStateAndAppendEvent_atomicallyUpdatesAndPersistsEvent` passes (requires Docker)
- [ ] E2E: `EngineDecouplingIT` passes — confirms in-memory impls active, no Docker
- [ ] Full engine test suite passes without Docker: `TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl engine`

Closes #69 | Epic #30

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Strip JPA from CaseInstance, EventLog, CaseMetaModel → Tasks 5–7
- ✅ Refactor all 14 handlers/services → Tasks 8–20
- ✅ Remove JPA deps from engine pom → Task 21
- ✅ Engine tests run without Docker → Tasks 22–25
- ✅ `updateStateAndAppendEvent` SPI + impls → Tasks 2–4
- ✅ TDD: unit test (Task 3), integration test (Task 4), E2E test (Task 24), happy path (Task 24)
- ✅ All commits reference issue #69
- ✅ Javadocs updated → Task 26
- ✅ DESIGN.md and CLAUDE.md updated → Task 26

**Gotchas documented in plan:**
- `Long.parseLong(eventLogId)` required in WorkerExecutionTask (Panache auto-coerced String; repository takes Long)
- `runOnSafeVertxContext` required in WorkerExecutionJobListener (Quartz thread → Vert.x reactive context)
- `quarkus.index-dependency.memory.*` required for CDI to discover @Alternative beans from the jar
- `eventLogRepository` in `InMemoryCaseInstanceRepository` must be wired via setter in unit tests (no CDI)
- `CaseInstance.parentPlanItemId` is `UUID`, not `Long` (pre-plan notes had it wrong)
- Context snapshot in `WorkflowExecutionCompletedHandler` is taken outside the transaction (was inside `Panache.withTransaction` — moved to before `eventLogRepository.append`)
