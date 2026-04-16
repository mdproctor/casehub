# casehub-persistence-memory Implementation Plan (PR 2 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `casehub-persistence-memory` module with in-memory SPI implementations for all three repository interfaces, enabling engine tests to run without Docker or PostgreSQL.

**Architecture:** Three `@Alternative @ApplicationScoped` beans implement the same SPI interfaces as the JPA module. Pure Java — ConcurrentHashMap storage, AtomicLong sequences, Uni.createFrom().item() wrappers. Tests instantiate directly via `new` — no Quarkus, no TestContainers. `InMemoryCaseInstanceRepository` has no external deps (the SPI has only `save`/`update`/`findByUuid` — no atomic event append required). Fresh repository instances per test via `@BeforeEach`.

**Tech Stack:** Java 21, Quarkus 3.17.5 (CDI annotations only, no runtime), Mutiny `Uni<T>`, JUnit 5, AssertJ

**Engine repo:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory/` · **Maven:** `/opt/homebrew/bin/mvn` · **Base branch:** `feat/persistence/hibernate` · **Issue:** casehubio/engine#68 · **Epic:** casehubio/engine#30

---

## Key Design Facts

**Domain object id access (critical — get these wrong and tests will fail silently):**
- `CaseMetaModel.id` — use `meta.getId()` / `meta.setId(Long)` (explicit getter/setter declared)
- `CaseInstance.id` — use `instance.id` directly (public field from PanacheEntity, no getter)
- `EventLog.id` — use `log.id` directly (public field from PanacheEntity, no getter)
- `EventLog.seq` — use `log.getSeq()` / `log.setSeq(Long)` (explicit getter/setter declared)

**CaseInstanceRepository SPI (as-built, PR 1):**
```java
Uni<CaseInstance> save(CaseInstance instance);     // set instance.id, return instance
Uni<CaseInstance> update(CaseInstance instance);   // update store, return instance
Uni<CaseInstance> findByUuid(UUID uuid);           // return null if not found
```
Note: `updateStateAndAppendEvent` was NOT added to the SPI — the plan notes were aspirational. Do not add it.

**EventLogRepository SPI:**
```java
Uni<Void> append(EventLog eventLog);                                                       // sets log.id and log.seq
Uni<Long> appendAndReturnId(EventLog eventLog);                                            // sets log.id and log.seq, returns id
Uni<EventLog> findById(Long id);                                                           // null if not found
Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId);                    // SCHEDULED + STARTED + COMPLETED
Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types);                      // ordered by seq asc
Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types);  // ordered by seq asc
Uni<List<EventLog>> findByCaseAndWorkerAndType(UUID caseId, String workerId, CaseHubEventType type);
```

**Package:** `io.casehub.persistence.memory`

---

## File Map

| Action | File |
|--------|------|
| Modify | `pom.xml` (parent — add `<module>casehub-persistence-memory</module>`) |
| Create | `casehub-persistence-memory/pom.xml` |
| Create | `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryEventLogRepository.java` |
| Create | `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepository.java` |
| Create | `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java` |
| Create | `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryEventLogRepositoryTest.java` |
| Create | `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepositoryTest.java` |
| Create | `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java` |

---

## Task 1: Scaffold the casehub-persistence-memory Module

**Files:**
- Modify: `pom.xml`
- Create: `casehub-persistence-memory/pom.xml`
- Create directory structure

- [ ] **Step 1: Create directories**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
mkdir -p casehub-persistence-memory/src/main/java/io/casehub/persistence/memory
mkdir -p casehub-persistence-memory/src/test/java/io/casehub/persistence/memory
```

- [ ] **Step 2: Create `casehub-persistence-memory/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>io.casehub</groupId>
        <artifactId>parent</artifactId>
        <version>0.2-SNAPSHOT</version>
    </parent>

    <artifactId>casehub-persistence-memory</artifactId>
    <name>Case Hub :: Persistence :: Memory</name>
    <description>In-memory SPI implementations for fast unit testing without Docker or PostgreSQL</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>engine</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
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
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
            </plugin>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
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

- [ ] **Step 3: Add the module to parent `pom.xml`**

In `pom.xml` (at the project root), find the `<modules>` block. Add `casehub-persistence-memory` after `casehub-persistence-hibernate`:

```xml
        <module>casehub-persistence-hibernate</module>
        <module>casehub-persistence-memory</module>
```

- [ ] **Step 4: Compile the empty module to verify scaffold**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn compile -pl casehub-persistence-memory -am -q
```

Expected: `BUILD SUCCESS`. No sources yet — just verifies pom.xml is valid and parent resolves.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
git add casehub-persistence-memory/pom.xml pom.xml
git commit -m "chore(build): scaffold casehub-persistence-memory module

Empty module, pom.xml only. Dependencies: engine (SPI interfaces), quarkus-arc (CDI annotations).
No Hibernate, no JPA, no TestContainers.
Refs casehubio/engine#68"
```

---

## Task 2: TDD — InMemoryEventLogRepository

Start here because it has no dependencies on the other repositories.

**Files:**
- Create: `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryEventLogRepositoryTest.java`
- Create: `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryEventLogRepository.java`

- [ ] **Step 1: Write the failing tests**

Save to `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryEventLogRepositoryTest.java`:

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

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryEventLogRepositoryTest {

  InMemoryEventLogRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryEventLogRepository();
  }

  // --- Happy path ---

  @Test
  void append_populatesIdAndSeq() {
    EventLog log = event(UUID.randomUUID(), "worker-1", CaseHubEventType.WORKER_SCHEDULED);

    repository.append(log).await().indefinitely();

    assertThat(log.id).isNotNull().isPositive();
    assertThat(log.getSeq()).isNotNull().isPositive();
  }

  @Test
  void append_seqIsMonotonicallyIncreasing() {
    UUID caseId = UUID.randomUUID();
    EventLog first = event(caseId, "w-1", CaseHubEventType.WORKER_SCHEDULED);
    EventLog second = event(caseId, "w-2", CaseHubEventType.WORKER_SCHEDULED);

    repository.append(first).await().indefinitely();
    repository.append(second).await().indefinitely();

    assertThat(second.getSeq()).isGreaterThan(first.getSeq());
  }

  @Test
  void appendAndReturnId_returnsIdAndPopulatesLog() {
    EventLog log = event(UUID.randomUUID(), "worker-ret", CaseHubEventType.WORKER_SCHEDULED);

    Long returned = repository.appendAndReturnId(log).await().indefinitely();

    assertThat(returned).isNotNull().isPositive();
    assertThat(returned).isEqualTo(log.id);
    assertThat(log.getSeq()).isNotNull().isPositive();
  }

  @Test
  void findById_returnsAppendedEvent() {
    UUID caseId = UUID.randomUUID();
    EventLog log = event(caseId, "worker-find", CaseHubEventType.WORKER_SCHEDULED);
    repository.append(log).await().indefinitely();

    EventLog found = repository.findById(log.id).await().indefinitely();

    assertThat(found).isNotNull();
    assertThat(found.getCaseId()).isEqualTo(caseId);
    assertThat(found.getEventType()).isEqualTo(CaseHubEventType.WORKER_SCHEDULED);
    assertThat(found.getWorkerId()).isEqualTo("worker-find");
  }

  @Test
  void findSchedulingEvents_returnsScheduledStartedAndCompleted() {
    UUID caseId = UUID.randomUUID();
    String workerId = "worker-sched-" + UUID.randomUUID();

    EventLog scheduled = event(caseId, workerId, CaseHubEventType.WORKER_SCHEDULED);
    EventLog started = event(caseId, workerId, CaseHubEventType.WORKER_EXECUTION_STARTED);
    EventLog otherWorker = event(caseId, "other", CaseHubEventType.WORKER_SCHEDULED);
    EventLog otherCase = event(UUID.randomUUID(), workerId, CaseHubEventType.WORKER_SCHEDULED);

    repository.append(scheduled).await().indefinitely();
    repository.append(started).await().indefinitely();
    repository.append(otherWorker).await().indefinitely();
    repository.append(otherCase).await().indefinitely();

    List<EventLog> result =
        repository.findSchedulingEvents(caseId, workerId).await().indefinitely();

    assertThat(result).hasSize(2);
    assertThat(result)
        .extracting(EventLog::getEventType)
        .containsExactlyInAnyOrder(
            CaseHubEventType.WORKER_SCHEDULED, CaseHubEventType.WORKER_EXECUTION_STARTED);
  }

  @Test
  void findByTypes_returnsMatchingEventsOrderedBySeq() {
    UUID caseId = UUID.randomUUID();
    EventLog e1 = event(caseId, "w", CaseHubEventType.CASE_STARTED);
    EventLog e2 = event(caseId, "w", CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    EventLog noise = event(caseId, "w", CaseHubEventType.WORKER_SCHEDULED);

    repository.append(e1).await().indefinitely();
    repository.append(e2).await().indefinitely();
    repository.append(noise).await().indefinitely();

    List<EventLog> result =
        repository
            .findByTypes(List.of(CaseHubEventType.CASE_STARTED, CaseHubEventType.WORKER_EXECUTION_COMPLETED))
            .await()
            .indefinitely();

    assertThat(result).hasSize(2);
    assertThat(result.stream().map(EventLog::getSeq).toList()).isSorted();
    assertThat(result.stream().map(EventLog::getEventType).toList())
        .doesNotContain(CaseHubEventType.WORKER_SCHEDULED);
  }

  @Test
  void findByCaseAndTypes_filtersByCaseId() {
    UUID target = UUID.randomUUID();
    UUID other = UUID.randomUUID();

    repository.append(event(target, "w", CaseHubEventType.CASE_STARTED)).await().indefinitely();
    repository.append(event(other, "w", CaseHubEventType.CASE_STARTED)).await().indefinitely();

    List<EventLog> result =
        repository
            .findByCaseAndTypes(target, List.of(CaseHubEventType.CASE_STARTED))
            .await()
            .indefinitely();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getCaseId()).isEqualTo(target);
    assertThat(result.stream().map(EventLog::getSeq).toList()).isSorted();
  }

  @Test
  void findByCaseAndWorkerAndType_filtersAllThreeDimensions() {
    UUID caseId = UUID.randomUUID();
    String workerId = "worker-filter-" + UUID.randomUUID();

    EventLog match = event(caseId, workerId, CaseHubEventType.WORKER_EXECUTION_FAILED);
    EventLog wrongWorker = event(caseId, "other", CaseHubEventType.WORKER_EXECUTION_FAILED);
    EventLog wrongType = event(caseId, workerId, CaseHubEventType.WORKER_SCHEDULED);

    repository.append(match).await().indefinitely();
    repository.append(wrongWorker).await().indefinitely();
    repository.append(wrongType).await().indefinitely();

    List<EventLog> result =
        repository
            .findByCaseAndWorkerAndType(caseId, workerId, CaseHubEventType.WORKER_EXECUTION_FAILED)
            .await()
            .indefinitely();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getWorkerId()).isEqualTo(workerId);
    assertThat(result.get(0).getEventType()).isEqualTo(CaseHubEventType.WORKER_EXECUTION_FAILED);
  }

  // --- Edge cases ---

  @Test
  void findById_returnsNullForUnknownId() {
    EventLog result = repository.findById(Long.MAX_VALUE).await().indefinitely();
    assertThat(result).isNull();
  }

  @Test
  void findSchedulingEvents_returnsEmptyWhenNoneMatch() {
    List<EventLog> result =
        repository.findSchedulingEvents(UUID.randomUUID(), "ghost").await().indefinitely();
    assertThat(result).isEmpty();
  }

  @Test
  void findByTypes_returnsEmptyWhenNoneMatch() {
    List<EventLog> result =
        repository.findByTypes(List.of(CaseHubEventType.CASE_CANCELLED)).await().indefinitely();
    assertThat(result).isEmpty();
  }

  @Test
  void idsAreUnique() {
    EventLog a = event(UUID.randomUUID(), "w", CaseHubEventType.WORKER_SCHEDULED);
    EventLog b = event(UUID.randomUUID(), "w", CaseHubEventType.WORKER_SCHEDULED);

    repository.append(a).await().indefinitely();
    repository.append(b).await().indefinitely();

    assertThat(a.id).isNotEqualTo(b.id);
    assertThat(a.getSeq()).isNotEqualTo(b.getSeq());
  }

  // --- Helper ---

  private EventLog event(UUID caseId, String workerId, CaseHubEventType type) {
    EventLog log = new EventLog();
    log.setCaseId(caseId);
    log.setWorkerId(workerId);
    log.setEventType(type);
    log.setStreamType(EventStreamType.WORKER);
    log.setTimestamp(Instant.now());
    return log;
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail (class not found)**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryEventLogRepositoryTest 2>&1 | grep -E "ERROR|FAIL|cannot find" | head -10
```

Expected: compile error — `InMemoryEventLogRepository` does not exist yet.

- [ ] **Step 3: Implement `InMemoryEventLogRepository`**

Save to `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryEventLogRepository.java`:

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

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.spi.EventLogRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Alternative
@ApplicationScoped
public class InMemoryEventLogRepository implements EventLogRepository {

  private final AtomicLong idSeq = new AtomicLong(0);
  private final AtomicLong seqCounter = new AtomicLong(0);
  private final ConcurrentHashMap<Long, EventLog> store = new ConcurrentHashMap<>();

  @Override
  public Uni<Void> append(EventLog eventLog) {
    eventLog.id = idSeq.incrementAndGet();
    eventLog.setSeq(seqCounter.incrementAndGet());
    store.put(eventLog.id, eventLog);
    return Uni.createFrom().voidItem();
  }

  @Override
  public Uni<Long> appendAndReturnId(EventLog eventLog) {
    eventLog.id = idSeq.incrementAndGet();
    eventLog.setSeq(seqCounter.incrementAndGet());
    store.put(eventLog.id, eventLog);
    return Uni.createFrom().item(eventLog.id);
  }

  @Override
  public Uni<EventLog> findById(Long id) {
    return Uni.createFrom().item(store.get(id));
  }

  @Override
  public Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId) {
    List<EventLog> result = store.values().stream()
        .filter(e -> caseId.equals(e.getCaseId()) && workerId.equals(e.getWorkerId()))
        .filter(e -> e.getEventType() == CaseHubEventType.WORKER_SCHEDULED
            || e.getEventType() == CaseHubEventType.WORKER_EXECUTION_STARTED
            || e.getEventType() == CaseHubEventType.WORKER_EXECUTION_COMPLETED)
        .toList();
    return Uni.createFrom().item(result);
  }

  @Override
  public Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types) {
    List<EventLog> result = store.values().stream()
        .filter(e -> types.contains(e.getEventType()))
        .sorted(Comparator.comparingLong(EventLog::getSeq))
        .toList();
    return Uni.createFrom().item(result);
  }

  @Override
  public Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types) {
    List<EventLog> result = store.values().stream()
        .filter(e -> caseId.equals(e.getCaseId()) && types.contains(e.getEventType()))
        .sorted(Comparator.comparingLong(EventLog::getSeq))
        .toList();
    return Uni.createFrom().item(result);
  }

  @Override
  public Uni<List<EventLog>> findByCaseAndWorkerAndType(
      UUID caseId, String workerId, CaseHubEventType type) {
    List<EventLog> result = store.values().stream()
        .filter(e -> caseId.equals(e.getCaseId())
            && workerId.equals(e.getWorkerId())
            && type == e.getEventType())
        .toList();
    return Uni.createFrom().item(result);
  }
}
```

- [ ] **Step 4: Run tests and verify they all pass**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryEventLogRepositoryTest 2>&1 | tail -15
```

Expected: `BUILD SUCCESS`, 12 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
git add \
  casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryEventLogRepository.java \
  casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryEventLogRepositoryTest.java
git commit -m "feat(persistence-memory): add InMemoryEventLogRepository with 12 unit tests

Happy path: append, appendAndReturnId, findById, findSchedulingEvents, findByTypes,
findByCaseAndTypes, findByCaseAndWorkerAndType, seq ordering.
Edge cases: null for unknown id, empty lists when no match, unique ids.
No Docker, no Quarkus — plain JUnit 5.
Refs casehubio/engine#68"
```

---

## Task 3: TDD — InMemoryCaseMetaModelRepository

**Files:**
- Create: `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepositoryTest.java`
- Create: `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepository.java`

- [ ] **Step 1: Write the failing tests**

Save to `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepositoryTest.java`:

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

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.internal.model.CaseMetaModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryCaseMetaModelRepositoryTest {

  InMemoryCaseMetaModelRepository repository;

  @BeforeEach
  void setUp() {
    repository = new InMemoryCaseMetaModelRepository();
  }

  // --- Happy path ---

  @Test
  void save_populatesIdAndCreatedAt() {
    CaseMetaModel meta = metaModel("save-populates", "ns", "1.0");

    CaseMetaModel saved = repository.save(meta).await().indefinitely();

    assertThat(saved.getId()).isNotNull().isPositive();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void save_returnsSameInstance() {
    CaseMetaModel meta = metaModel("same-instance", "ns", "1.0");

    CaseMetaModel saved = repository.save(meta).await().indefinitely();

    assertThat(saved).isSameAs(meta);
  }

  @Test
  void findByKey_returnsRegisteredMetaModel() {
    CaseMetaModel meta = metaModel("find-by-key", "repo-ns", "2.0");
    repository.save(meta).await().indefinitely();

    CaseMetaModel found =
        repository.findByKey("repo-ns", "find-by-key", "2.0").await().indefinitely();

    assertThat(found).isNotNull();
    assertThat(found.getName()).isEqualTo("find-by-key");
    assertThat(found.getNamespace()).isEqualTo("repo-ns");
    assertThat(found.getVersion()).isEqualTo("2.0");
    assertThat(found.getId()).isEqualTo(meta.getId());
  }

  @Test
  void save_thenFindByKey_roundTrip() {
    CaseMetaModel meta = metaModel("round-trip", "rt-ns", "3.0");
    meta.setTitle("Round Trip Title");
    meta.setDsl("yaml");

    CaseMetaModel saved = repository.save(meta).await().indefinitely();
    CaseMetaModel found =
        repository.findByKey("rt-ns", "round-trip", "3.0").await().indefinitely();

    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getTitle()).isEqualTo("Round Trip Title");
    assertThat(found.getDsl()).isEqualTo("yaml");
    assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
  }

  @Test
  void idsAreUniqueAcrossSaves() {
    CaseMetaModel a = metaModel("a", "ns", "1.0");
    CaseMetaModel b = metaModel("b", "ns", "1.0");

    repository.save(a).await().indefinitely();
    repository.save(b).await().indefinitely();

    assertThat(a.getId()).isNotEqualTo(b.getId());
  }

  // --- Edge cases ---

  @Test
  void findByKey_returnsNullForUnknown() {
    CaseMetaModel result =
        repository.findByKey("no-ns", "no-name", "9.9").await().indefinitely();
    assertThat(result).isNull();
  }

  @Test
  void findByKey_isKeyExact_namespaceVersionMustMatch() {
    CaseMetaModel meta = metaModel("exact", "exact-ns", "1.0");
    repository.save(meta).await().indefinitely();

    assertThat(repository.findByKey("wrong-ns", "exact", "1.0").await().indefinitely()).isNull();
    assertThat(repository.findByKey("exact-ns", "exact", "2.0").await().indefinitely()).isNull();
  }

  // --- Helper ---

  private CaseMetaModel metaModel(String name, String namespace, String version) {
    CaseMetaModel m = new CaseMetaModel();
    m.setName(name);
    m.setNamespace(namespace);
    m.setVersion(version);
    return m;
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryCaseMetaModelRepositoryTest 2>&1 | grep -E "ERROR|cannot find" | head -5
```

Expected: compile error — `InMemoryCaseMetaModelRepository` does not exist.

- [ ] **Step 3: Implement `InMemoryCaseMetaModelRepository`**

Save to `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepository.java`:

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

import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Alternative
@ApplicationScoped
public class InMemoryCaseMetaModelRepository implements CaseMetaModelRepository {

  private final AtomicLong idSeq = new AtomicLong(0);
  private final ConcurrentHashMap<String, CaseMetaModel> store = new ConcurrentHashMap<>();

  @Override
  public Uni<CaseMetaModel> findByKey(String namespace, String name, String version) {
    return Uni.createFrom().item(store.get(key(namespace, name, version)));
  }

  @Override
  public Uni<CaseMetaModel> save(CaseMetaModel metaModel) {
    metaModel.setId(idSeq.incrementAndGet());
    if (metaModel.getCreatedAt() == null) {
      metaModel.setCreatedAt(Instant.now());
    }
    store.put(key(metaModel.getNamespace(), metaModel.getName(), metaModel.getVersion()), metaModel);
    return Uni.createFrom().item(metaModel);
  }

  private String key(String namespace, String name, String version) {
    return namespace + ":" + name + ":" + version;
  }
}
```

- [ ] **Step 4: Run tests and verify they all pass**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryCaseMetaModelRepositoryTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
git add \
  casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepository.java \
  casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepositoryTest.java
git commit -m "feat(persistence-memory): add InMemoryCaseMetaModelRepository with 7 unit tests

Happy path: save populates id and createdAt, returns same instance, findByKey, round-trip.
Edge cases: null for unknown key, exact key matching (wrong namespace/version returns null),
unique ids across saves.
Refs casehubio/engine#68"
```

---

## Task 4: TDD — InMemoryCaseInstanceRepository

**Files:**
- Create: `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java`
- Create: `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java`

- [ ] **Step 1: Write the failing tests**

Save to `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java`:

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

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.model.CaseMetaModel;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryCaseInstanceRepositoryTest {

  InMemoryCaseInstanceRepository repository;
  CaseMetaModel meta;

  @BeforeEach
  void setUp() {
    repository = new InMemoryCaseInstanceRepository();
    meta = new CaseMetaModel();
    meta.setName("test-case");
    meta.setNamespace("test-ns");
    meta.setVersion("1.0");
    meta.setId(1L);
  }

  // --- Happy path ---

  @Test
  void save_populatesId() {
    CaseInstance instance = newInstance(CaseStatus.RUNNING);

    CaseInstance saved = repository.save(instance).await().indefinitely();

    assertThat(saved.id).isNotNull().isPositive();
  }

  @Test
  void save_returnsSameInstance() {
    CaseInstance instance = newInstance(CaseStatus.RUNNING);

    CaseInstance saved = repository.save(instance).await().indefinitely();

    assertThat(saved).isSameAs(instance);
  }

  @Test
  void findByUuid_returnsSavedInstance() {
    UUID uuid = UUID.randomUUID();
    CaseInstance instance = newInstance(CaseStatus.RUNNING);
    instance.setUuid(uuid);
    repository.save(instance).await().indefinitely();

    CaseInstance found = repository.findByUuid(uuid).await().indefinitely();

    assertThat(found).isNotNull();
    assertThat(found.getUuid()).isEqualTo(uuid);
    assertThat(found.getState()).isEqualTo(CaseStatus.RUNNING);
    assertThat(found.getCaseMetaModel()).isNotNull();
    assertThat(found.getCaseMetaModel().getId()).isEqualTo(meta.getId());
  }

  @Test
  void update_changesState() {
    CaseInstance instance = newInstance(CaseStatus.RUNNING);
    repository.save(instance).await().indefinitely();

    instance.setState(CaseStatus.COMPLETED);
    repository.update(instance).await().indefinitely();

    CaseInstance reloaded = repository.findByUuid(instance.getUuid()).await().indefinitely();
    assertThat(reloaded.getState()).isEqualTo(CaseStatus.COMPLETED);
  }

  @Test
  void update_returnsSameInstance() {
    CaseInstance instance = newInstance(CaseStatus.RUNNING);
    repository.save(instance).await().indefinitely();

    CaseInstance result = repository.update(instance).await().indefinitely();

    assertThat(result).isSameAs(instance);
  }

  @Test
  void idsAreUniqueAcrossSaves() {
    CaseInstance a = newInstance(CaseStatus.RUNNING);
    CaseInstance b = newInstance(CaseStatus.RUNNING);

    repository.save(a).await().indefinitely();
    repository.save(b).await().indefinitely();

    assertThat(a.id).isNotEqualTo(b.id);
  }

  // --- Edge cases ---

  @Test
  void findByUuid_returnsNullForUnknown() {
    CaseInstance result = repository.findByUuid(UUID.randomUUID()).await().indefinitely();
    assertThat(result).isNull();
  }

  @Test
  void update_afterMultipleStateChanges_reflectsLatest() {
    CaseInstance instance = newInstance(CaseStatus.RUNNING);
    repository.save(instance).await().indefinitely();

    instance.setState(CaseStatus.WAITING);
    repository.update(instance).await().indefinitely();

    instance.setState(CaseStatus.COMPLETED);
    repository.update(instance).await().indefinitely();

    CaseInstance reloaded = repository.findByUuid(instance.getUuid()).await().indefinitely();
    assertThat(reloaded.getState()).isEqualTo(CaseStatus.COMPLETED);
  }

  // --- Helper ---

  private CaseInstance newInstance(CaseStatus status) {
    CaseInstance instance = new CaseInstance();
    instance.setUuid(UUID.randomUUID());
    instance.setState(status);
    instance.setCaseMetaModel(meta);
    return instance;
  }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryCaseInstanceRepositoryTest 2>&1 | grep -E "ERROR|cannot find" | head -5
```

Expected: compile error — `InMemoryCaseInstanceRepository` does not exist.

- [ ] **Step 3: Implement `InMemoryCaseInstanceRepository`**

Save to `casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java`:

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

import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Alternative
@ApplicationScoped
public class InMemoryCaseInstanceRepository implements CaseInstanceRepository {

  private final AtomicLong idSeq = new AtomicLong(0);
  private final ConcurrentHashMap<UUID, CaseInstance> store = new ConcurrentHashMap<>();

  @Override
  public Uni<CaseInstance> save(CaseInstance instance) {
    instance.id = idSeq.incrementAndGet();
    store.put(instance.getUuid(), instance);
    return Uni.createFrom().item(instance);
  }

  @Override
  public Uni<CaseInstance> update(CaseInstance instance) {
    store.put(instance.getUuid(), instance);
    return Uni.createFrom().item(instance);
  }

  @Override
  public Uni<CaseInstance> findByUuid(UUID uuid) {
    return Uni.createFrom().item(store.get(uuid));
  }
}
```

- [ ] **Step 4: Run tests and verify they all pass**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am \
  -Dtest=InMemoryCaseInstanceRepositoryTest 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`, 8 tests passing.

- [ ] **Step 5: Commit**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
git add \
  casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java \
  casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepositoryTest.java
git commit -m "feat(persistence-memory): add InMemoryCaseInstanceRepository with 8 unit tests

Happy path: save populates id, returns same instance, findByUuid, update changes state,
update returns same instance, unique ids.
Edge cases: null for unknown uuid, multiple state transitions reflect latest.
Refs casehubio/engine#68"
```

---

## Task 5: Full Suite Pass and PR

**Files:** none (verification + PR only)

- [ ] **Step 1: Run all tests in casehub-persistence-memory**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
/opt/homebrew/bin/mvn test -pl casehub-persistence-memory -am 2>&1 | tail -15
```

Expected: `BUILD SUCCESS` — all 27 tests pass (12 EventLog + 7 CaseMetaModel + 8 CaseInstance).

- [ ] **Step 2: Verify engine tests are unaffected**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
TESTCONTAINERS_RYUK_DISABLED=true /opt/homebrew/bin/mvn test -pl engine -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`. Engine is unchanged in this PR — just confirming nothing regressed.

- [ ] **Step 3: Push branch and open PR**

```bash
cd /Users/mdproctor/dev/casehub-engine/.worktrees/feat-persistence-memory
git push -u origin feat/persistence/memory
gh pr create \
  --repo casehubio/engine \
  --base feat/persistence/hibernate \
  --title "feat(persistence-memory): add in-memory SPI implementations — 27 unit tests, no Docker" \
  --body "$(cat <<'EOF'
## Summary

- Creates `casehub-persistence-memory` module with three `@Alternative @ApplicationScoped` in-memory repository implementations
- `InMemoryEventLogRepository` — ConcurrentHashMap + AtomicLong id/seq sequences, 12 tests
- `InMemoryCaseMetaModelRepository` — ConcurrentHashMap keyed by namespace:name:version, 7 tests
- `InMemoryCaseInstanceRepository` — ConcurrentHashMap keyed by UUID, 8 tests
- 27 unit tests total — no Docker, no PostgreSQL, no Quarkus runtime

## Design

- `@Alternative` (not `@Priority`) — activated via `quarkus.arc.selected-alternatives` in engine test application.properties (PR 3)
- Fresh instance per test via direct instantiation — no CDI context, no test cleanup needed
- Module depends only on `engine` (SPI interfaces) and `quarkus-arc` (CDI annotations)

## Test plan

- [ ] `mvn test -pl casehub-persistence-memory -am` — 27 tests pass, no containers
- [ ] `mvn test -pl engine -am` — no regressions in engine tests

Closes casehubio/engine#68

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
EOF
)"
```

---

## Troubleshooting Reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| `cannot find symbol: class InMemoryEventLogRepository` | Class not yet written | Write the implementation first |
| `NullPointerException` on `log.id` in tests | `id` not set by `append()` | Confirm `eventLog.id = idSeq.incrementAndGet()` is in `append()` |
| `findByKey` returns wrong result | Key collision | Check `key()` uses all three components: namespace + name + version |
| `findByUuid` returns stale state after `update` | `update()` not replacing entry | Confirm `store.put(instance.getUuid(), instance)` in `update()` |
| `seq` null after `append()` | `setSeq()` not called | Confirm `eventLog.setSeq(seqCounter.incrementAndGet())` in `append()` |
| `CaseMetaModel.id` set via wrong method | Using `meta.id` directly instead of `setId()` | `CaseMetaModel` requires `meta.setId(Long)` — it has an explicit setter |
| `CaseInstance.id` set via wrong method | Using `instance.setId()` — doesn't exist | `CaseInstance.id` is a direct public field — use `instance.id = ...` |
