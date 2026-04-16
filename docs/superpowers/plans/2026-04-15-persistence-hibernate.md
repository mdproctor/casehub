# casehub-persistence-hibernate Implementation Plan (PR 1 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three repository SPI interfaces to the engine and create the `casehub-persistence-hibernate` module — JPA entities + Hibernate Reactive implementations — without touching any existing engine code.

**Architecture:** Three clean SPI interfaces (`CaseMetaModelRepository`, `CaseInstanceRepository`, `EventLogRepository`) live in `engine/spi/`. The new `casehub-persistence-hibernate` module owns separate JPA entity classes (`*Entity`) and `@ApplicationScoped` repository implementations backed by Panache. Domain objects (`CaseInstance`, `CaseMetaModel`, `EventLog`) are unchanged in this PR — they still extend `PanacheEntity`. Decoupling those is PR 3.

**Tech Stack:** Java 21, Quarkus 3.17.5, Hibernate Reactive + Panache, Flyway, PostgreSQL 16 (via Dev Services / TestContainers), Mutiny `Uni<T>`, AssertJ, `@QuarkusTest`.

**Engine repo:** `/Users/mdproctor/dev/casehub-engine/` · **Maven:** `/opt/homebrew/bin/mvn` · **Base branch:** `feat/rename-binding-casedefinition`

---

## Key Design Decisions

**Separate entity classes (not orm.xml):** `CaseMetaModelEntity`, `CaseInstanceEntity`, `EventLogEntity` live in `casehub-persistence-hibernate` and own all JPA annotations. This avoids the `LazyInitializationException` risk that arises when domain objects participate in a Hibernate session lifecycle (per Francisco Javier Tirado Sarti's advice).

**No engine indexing in hibernate module tests:** The engine's `CaseMetaModel`, `CaseInstance`, `EventLog` are `@Entity` classes that map to the same tables as the new `*Entity` classes. Indexing the engine in `casehub-persistence-hibernate` tests would cause Hibernate to discover duplicate entity mappings and fail. Solution: do NOT add `quarkus.index-dependency.engine` to the hibernate module's `application.properties`.

**Migrations not moved in PR 1:** Flyway SQL files stay in `engine/src/main/resources/db/migration/` for PRs 1 and 2. They arrive on the `casehub-persistence-hibernate` test classpath transitively (via the engine JAR) — Flyway scans `classpath:db/migration` across all JARs. Migrations move to this module in PR 3 when Flyway is stripped from engine.

**`parentPlanItemId` is UUID:** The actual DB column (`V1.3.0__Add_Parent_Plan_Item_Id.sql`) and `CaseInstance.java` use `UUID`, not `Long`.

**FK column is `case_definition_id`:** The `case_instance.case_definition_id` FK references `case_meta_model(id)` — verified in both the migration SQL and `CaseInstance.java`.

**`session.getReference()` for FK in save():** When saving a `CaseInstance`, `caseMetaModel.id` is already known. Use `session.getReference(CaseMetaModelEntity.class, id)` to get a proxy rather than setting a detached object, which would throw `TransientPropertyValueException`.

---

## File Map

| Action | File |
|--------|------|
| Create | `engine/src/main/java/io/casehub/engine/spi/CaseMetaModelRepository.java` |
| Create | `engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java` |
| Create | `engine/src/main/java/io/casehub/engine/spi/EventLogRepository.java` |
| Modify | `pom.xml` (parent — add `<module>casehub-persistence-hibernate</module>`) |
| Create | `casehub-persistence-hibernate/pom.xml` |
| Create | `casehub-persistence-hibernate/src/main/resources/application.properties` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseMetaModelEntity.java` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseInstanceEntity.java` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/EventLogEntity.java` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepository.java` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java` |
| Create | `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaEventLogRepository.java` |
| Create | `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepositoryTest.java` |
| Create | `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java` |
| Create | `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaEventLogRepositoryTest.java` |
| Create | `casehub-persistence-hibernate/src/test/resources/application.properties` |

---

## Task 1: Create the Feature Branch

**Files:** none

- [ ] **Step 1: Create the branch**

```bash
cd /Users/mdproctor/dev/casehub-engine
git checkout feat/rename-binding-casedefinition
git checkout -b feat/persistence/hibernate
```

Expected: `Switched to a new branch 'feat/persistence/hibernate'`

---

## Task 2: Add SPI Interfaces to Engine

**Files:**
- Create: `engine/src/main/java/io/casehub/engine/spi/CaseMetaModelRepository.java`
- Create: `engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java`
- Create: `engine/src/main/java/io/casehub/engine/spi/EventLogRepository.java`

These are pure Java interfaces — no Panache, no JPA annotations, no CDI. They declare the storage contract the engine needs; implementations live in other modules.

- [ ] **Step 1: Write the failing compile check**

```bash
cd /Users/mdproctor/dev/casehub-engine
mkdir -p engine/src/main/java/io/casehub/engine/spi
```

- [ ] **Step 2: Create `CaseMetaModelRepository.java`**

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

import io.casehub.engine.internal.model.CaseMetaModel;
import io.smallrye.mutiny.Uni;

public interface CaseMetaModelRepository {

  /** Find by the unique (namespace, name, version) key. Returns null if not registered. */
  Uni<CaseMetaModel> findByKey(String namespace, String name, String version);

  /** Persist a new case meta model. Returns the saved instance with id populated. */
  Uni<CaseMetaModel> save(CaseMetaModel metaModel);
}
```

Save to: `engine/src/main/java/io/casehub/engine/spi/CaseMetaModelRepository.java`

- [ ] **Step 3: Create `CaseInstanceRepository.java`**

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

import io.casehub.engine.internal.model.CaseInstance;
import io.smallrye.mutiny.Uni;
import java.util.UUID;

public interface CaseInstanceRepository {

  /** Persist a new case instance. Returns the saved instance with id populated. */
  Uni<CaseInstance> save(CaseInstance instance);

  /** Merge state changes back to storage (status transitions). */
  Uni<CaseInstance> update(CaseInstance instance);

  /**
   * Load a case instance by UUID with its CaseMetaModel eagerly joined. Returns null if not found.
   */
  Uni<CaseInstance> findByUuid(UUID uuid);
}
```

Save to: `engine/src/main/java/io/casehub/engine/spi/CaseInstanceRepository.java`

- [ ] **Step 4: Create `EventLogRepository.java`**

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

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.smallrye.mutiny.Uni;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface EventLogRepository {

  /** Append an event to the log. Populates eventLog.id and eventLog.seq after append. */
  Uni<Void> append(EventLog eventLog);

  /**
   * Append an event and flush, returning the generated id. Used by WorkerScheduleEventHandler to
   * get the id before scheduling a Quartz job.
   */
  Uni<Long> appendAndReturnId(EventLog eventLog);

  /** Load an event by id. Returns null if not found. */
  Uni<EventLog> findById(Long id);

  /**
   * Find WORKER_SCHEDULED, WORKER_EXECUTION_STARTED, and WORKER_EXECUTION_COMPLETED events for a
   * specific case+worker. Used by WorkerScheduleEventHandler for idempotency checking.
   */
  Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId);

  /**
   * Find all events matching any of the given types, ordered by seq ascending. Used by
   * WorkerExecutionRecoveryService to find pending scheduled workers on startup.
   */
  Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types);

  /**
   * Find events for a specific case matching any of the given types, ordered by seq ascending.
   * Used by WorkerExecutionRecoveryService to rebuild CaseContext state.
   */
  Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types);

  /**
   * Find events for a specific case+worker of a specific type. Used by
   * WorkerExecutionJobListener to count failed attempts for retry logic.
   */
  Uni<List<EventLog>> findByCaseAndWorkerAndType(
      UUID caseId, String workerId, CaseHubEventType type);
}
```

Save to: `engine/src/main/java/io/casehub/engine/spi/EventLogRepository.java`

- [ ] **Step 5: Compile engine to verify interfaces are valid**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn compile -pl engine -am -q
```

Expected: `BUILD SUCCESS`. If there are import errors, fix the package paths — `CaseMetaModel` is in `io.casehub.engine.internal.model`, `EventLog` is in `io.casehub.engine.internal.history`, `CaseHubEventType` is in `io.casehub.engine.internal.history`.

- [ ] **Step 6: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/spi/
git commit -m "feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces

Refs #55"
```

---

## Task 3: Scaffold the casehub-persistence-hibernate Module

**Files:**
- Create: `casehub-persistence-hibernate/pom.xml`
- Modify: `pom.xml` (parent — add module entry)
- Create: `casehub-persistence-hibernate/src/main/resources/application.properties`
- Create: `casehub-persistence-hibernate/src/test/resources/application.properties`

- [ ] **Step 1: Create the module directory structure**

```bash
cd /Users/mdproctor/dev/casehub-engine
mkdir -p casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa
mkdir -p casehub-persistence-hibernate/src/main/resources
mkdir -p casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa
mkdir -p casehub-persistence-hibernate/src/test/resources
```

- [ ] **Step 2: Create `casehub-persistence-hibernate/pom.xml`**

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

    <artifactId>casehub-persistence-hibernate</artifactId>
    <name>Case Hub :: Persistence :: Hibernate</name>
    <description>JPA entity classes and Hibernate Reactive repository implementations for the engine SPI</description>

    <dependencies>
        <dependency>
            <groupId>io.casehub</groupId>
            <artifactId>engine</artifactId>
            <version>${project.version}</version>
        </dependency>
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
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-arc</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
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
                <groupId>io.quarkus.platform</groupId>
                <artifactId>quarkus-maven-plugin</artifactId>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                            <goal>generate-code</goal>
                            <goal>generate-code-tests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>
                </configuration>
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

- [ ] **Step 3: Add the module to the parent `pom.xml`**

In `/Users/mdproctor/dev/casehub-engine/pom.xml`, find the `<modules>` block and add `casehub-persistence-hibernate` after `casehub-blackboard`:

```xml
        <module>casehub-blackboard</module>
        <module>casehub-persistence-hibernate</module>
```

- [ ] **Step 4: Create `casehub-persistence-hibernate/src/main/resources/application.properties`**

```properties
# Schema validation — Flyway manages DDL, Hibernate validates on startup
quarkus.hibernate-orm.schema-management.strategy=validate

# Flyway: migrate at start; SQL files arrive from engine JAR on classpath
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-version=0
quarkus.flyway.baseline-on-migrate=true
```

- [ ] **Step 5: Create `casehub-persistence-hibernate/src/test/resources/application.properties`**

```properties
# Test: clean DB before each test class startup so migrations always run on a fresh schema
%test.quarkus.flyway.clean-at-start=true

# Suppress Hibernate mapping format warnings in tests
%test.quarkus.hibernate-orm.mapping.format.global=ignore

# Index the api module (needed for CaseStatus enum used in entity mappings).
# Do NOT index the engine module: its @Entity classes (CaseMetaModel, CaseInstance, EventLog)
# map to the same tables as our *Entity classes. Indexing both would cause Hibernate to
# discover duplicate entity mappings and fail to start.
# Flyway still finds migration SQL files from the engine JAR via classpath:db/migration
# regardless of this indexing setting.
quarkus.index-dependency.api.group-id=io.casehub
quarkus.index-dependency.api.artifact-id=api
```

- [ ] **Step 6: Compile the module scaffold**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn compile -pl casehub-persistence-hibernate -am -q
```

Expected: `BUILD SUCCESS`. The module is empty (no Java sources yet) but should compile.

- [ ] **Step 7: Commit**

```bash
git add casehub-persistence-hibernate/ pom.xml
git commit -m "chore(build): scaffold casehub-persistence-hibernate module

Empty module with pom.xml and application.properties.
Refs #55"
```

---

## Task 4: Create the JPA Entity Classes

**Files:**
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseMetaModelEntity.java`
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseInstanceEntity.java`
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/EventLogEntity.java`

These classes are the Hibernate-side of the persistence layer. They mirror the DB schema exactly and are separate from the domain POJOs in engine. The repositories convert between entity ↔ domain POJO.

- [ ] **Step 1: Create `CaseMetaModelEntity.java`**

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
package io.casehub.persistence.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "case_meta_model",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"namespace", "name", "version"})
    })
public class CaseMetaModelEntity extends PanacheEntity {

  @Column(nullable = false, length = 255)
  public String name;

  @Column(length = 255)
  public String namespace;

  @Column(nullable = false, length = 50)
  public String version;

  @Column(length = 500)
  public String title;

  @Column(length = 50)
  public String dsl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  public JsonNode definition;

  @Column(name = "created_at", nullable = false, updatable = false)
  public Instant createdAt;
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseMetaModelEntity.java`

- [ ] **Step 2: Create `CaseInstanceEntity.java`**

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
package io.casehub.persistence.jpa;

import io.casehub.api.model.CaseStatus;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "case_instance")
public class CaseInstanceEntity extends PanacheEntity {

  @Column(name = "uuid", nullable = false, unique = true, updatable = false)
  public UUID uuid;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  public CaseStatus state;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "case_definition_id", nullable = false)
  public CaseMetaModelEntity caseMetaModel;

  @Column(name = "parent_plan_item_id", nullable = true)
  public UUID parentPlanItemId;
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/CaseInstanceEntity.java`

- [ ] **Step 3: Create `EventLogEntity.java`**

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
package io.casehub.persistence.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventStreamType;
import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "event_log")
public class EventLogEntity extends PanacheEntity {

  @Column(
      name = "seq",
      nullable = false,
      updatable = false,
      insertable = false,
      columnDefinition = "BIGINT GENERATED ALWAYS AS IDENTITY")
  @Generated(event = EventType.INSERT)
  public Long seq;

  @Column(name = "case_id", nullable = false, updatable = false)
  public UUID caseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 255)
  public CaseHubEventType eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "stream_type", nullable = false, length = 255)
  public EventStreamType streamType;

  @Column(name = "worker_id", nullable = true, length = 255)
  public String workerId;

  @Column(name = "timestamp", nullable = false)
  public Instant timestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  public JsonNode payload;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  public JsonNode metadata;
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/EventLogEntity.java`

- [ ] **Step 4: Compile to verify entity classes**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn compile -pl casehub-persistence-hibernate -am -q
```

Expected: `BUILD SUCCESS`. Fix any import errors — `CaseHubEventType` and `EventStreamType` are in `io.casehub.engine.internal.history`, `CaseStatus` is in `io.casehub.api.model`.

- [ ] **Step 5: Commit**

```bash
git add casehub-persistence-hibernate/src/main/java/
git commit -m "feat(persistence-hibernate): add JPA entity classes

CaseMetaModelEntity, CaseInstanceEntity, EventLogEntity mirror the DB schema
with JPA annotations. Separate from engine domain POJOs per quarkus-flow convention.
Refs #55"
```

---

## Task 5: TDD — JpaCaseMetaModelRepository

**Files:**
- Create: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepositoryTest.java`
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepository.java`

- [ ] **Step 1: Write the failing tests**

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
package io.casehub.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaCaseMetaModelRepositoryTest {

  @Inject CaseMetaModelRepository repository;

  @Test
  void save_populatesIdAndCreatedAt() {
    CaseMetaModel meta = metaModel("save-populates", "ns", "1.0");

    CaseMetaModel saved = repository.save(meta).await().indefinitely();

    assertThat(saved.getId()).isNotNull().isPositive();
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  void findByKey_returnsNullForUnknown() {
    CaseMetaModel result =
        repository.findByKey("no-such-ns", "no-such-name", "9.9").await().indefinitely();

    assertThat(result).isNull();
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
    assertThat(found.getId()).isNotNull();
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

  private CaseMetaModel metaModel(String name, String namespace, String version) {
    CaseMetaModel m = new CaseMetaModel();
    m.setName(name);
    m.setNamespace(namespace);
    m.setVersion(version);
    return m;
  }
}
```

Save to: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepositoryTest.java`

- [ ] **Step 2: Run the tests to verify they fail (no implementation yet)**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaCaseMetaModelRepositoryTest -q 2>&1 | tail -20
```

Expected: compilation fails with "cannot find symbol: class JpaCaseMetaModelRepository" or CDI resolution fails at test start. This confirms the test is wired correctly.

- [ ] **Step 3: Implement `JpaCaseMetaModelRepository.java`**

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
package io.casehub.persistence.jpa;

import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;

@ApplicationScoped
public class JpaCaseMetaModelRepository implements CaseMetaModelRepository {

  @Override
  public Uni<CaseMetaModel> findByKey(String namespace, String name, String version) {
    return Panache.withSession(
            () ->
                CaseMetaModelEntity.<CaseMetaModelEntity>find(
                        "namespace = ?1 and name = ?2 and version = ?3", namespace, name, version)
                    .firstResult())
        .map(entity -> entity == null ? null : fromEntity(entity));
  }

  @Override
  public Uni<CaseMetaModel> save(CaseMetaModel metaModel) {
    CaseMetaModelEntity entity = toEntity(metaModel);
    entity.createdAt = Instant.now();
    return Panache.withTransaction(() -> entity.persist())
        .map(
            v -> {
              metaModel.setId(entity.id);
              metaModel.setCreatedAt(entity.createdAt);
              return metaModel;
            });
  }

  private CaseMetaModel fromEntity(CaseMetaModelEntity entity) {
    CaseMetaModel m = new CaseMetaModel();
    m.setId(entity.id);
    m.setName(entity.name);
    m.setNamespace(entity.namespace);
    m.setVersion(entity.version);
    m.setTitle(entity.title);
    m.setDsl(entity.dsl);
    m.setDefinition(entity.definition);
    m.setCreatedAt(entity.createdAt);
    return m;
  }

  private CaseMetaModelEntity toEntity(CaseMetaModel m) {
    CaseMetaModelEntity entity = new CaseMetaModelEntity();
    entity.name = m.getName();
    entity.namespace = m.getNamespace();
    entity.version = m.getVersion();
    entity.title = m.getTitle();
    entity.dsl = m.getDsl();
    entity.definition = m.getDefinition();
    return entity;
  }
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepository.java`

**Note:** `CaseMetaModel.setId()` — verify this method exists. In `CaseMetaModel.java`, `id` is inherited from `PanacheEntity` as `public Long id`. If there's no `setId()` method, set it directly: `metaModel.id = entity.id`. Check `CaseMetaModel.java` — it has `public void setId(Long id) { this.id = id; }` (line 76), so `setId()` is available.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaCaseMetaModelRepositoryTest 2>&1 | tail -30
```

Expected: All 4 tests pass. Quarkus Dev Services starts a PostgreSQL container automatically. If you see `UnsatisfiedResolutionException` for `CaseMetaModelRepository`, confirm there's no competing bean — `JpaCaseMetaModelRepository` is the only implementation on the classpath.

If tests fail with `Table "case_meta_model" not found`, Flyway didn't run — verify `quarkus.flyway.migrate-at-start=true` is in `application.properties` and that the engine JAR is on the classpath with migration files in `db/migration/`.

- [ ] **Step 5: Commit**

```bash
git add casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepository.java \
        casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseMetaModelRepositoryTest.java
git commit -m "feat(persistence-hibernate): add JpaCaseMetaModelRepository with tests

4 tests: save, findByKey null, findByKey found, round-trip.
Refs #55"
```

---

## Task 6: TDD — JpaCaseInstanceRepository

**Files:**
- Create: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java`
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java`

- [ ] **Step 1: Write the failing tests**

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
package io.casehub.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseStatus;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.engine.spi.CaseMetaModelRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaCaseInstanceRepositoryTest {

  @Inject CaseInstanceRepository instanceRepository;
  @Inject CaseMetaModelRepository metaModelRepository;

  // Each test method gets a freshly saved meta model with a unique key
  private CaseMetaModel savedMeta;

  @BeforeEach
  void setUp() {
    // Use a UUID suffix so tests don't clash even if DB is not cleaned between methods
    String unique = UUID.randomUUID().toString().substring(0, 8);
    CaseMetaModel meta = new CaseMetaModel();
    meta.setName("instance-test-" + unique);
    meta.setNamespace("test-ns");
    meta.setVersion("1.0");
    savedMeta = metaModelRepository.save(meta).await().indefinitely();
  }

  @Test
  void save_populatesId() {
    CaseInstance instance = newInstance(savedMeta, CaseStatus.RUNNING);

    CaseInstance saved = instanceRepository.save(instance).await().indefinitely();

    assertThat(saved.id).isNotNull().isPositive();
  }

  @Test
  void findByUuid_returnsNullForUnknown() {
    CaseInstance result =
        instanceRepository.findByUuid(UUID.randomUUID()).await().indefinitely();

    assertThat(result).isNull();
  }

  @Test
  void findByUuid_returnsSavedInstanceWithMetaModel() {
    UUID uuid = UUID.randomUUID();
    CaseInstance instance = newInstance(savedMeta, CaseStatus.RUNNING);
    instance.setUuid(uuid);
    instanceRepository.save(instance).await().indefinitely();

    CaseInstance found = instanceRepository.findByUuid(uuid).await().indefinitely();

    assertThat(found).isNotNull();
    assertThat(found.getUuid()).isEqualTo(uuid);
    assertThat(found.getState()).isEqualTo(CaseStatus.RUNNING);
    assertThat(found.getCaseMetaModel()).isNotNull();
    assertThat(found.getCaseMetaModel().getId()).isEqualTo(savedMeta.getId());
  }

  @Test
  void update_changesState() {
    CaseInstance instance = newInstance(savedMeta, CaseStatus.RUNNING);
    instanceRepository.save(instance).await().indefinitely();

    instance.setState(CaseStatus.COMPLETED);
    instanceRepository.update(instance).await().indefinitely();

    CaseInstance reloaded = instanceRepository.findByUuid(instance.getUuid()).await().indefinitely();
    assertThat(reloaded.getState()).isEqualTo(CaseStatus.COMPLETED);
  }

  private CaseInstance newInstance(CaseMetaModel meta, CaseStatus status) {
    CaseInstance instance = new CaseInstance();
    instance.setUuid(UUID.randomUUID());
    instance.setState(status);
    instance.setCaseMetaModel(meta);
    return instance;
  }
}
```

Save to: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java`

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaCaseInstanceRepositoryTest -q 2>&1 | tail -15
```

Expected: fails to compile or CDI resolution error — no `CaseInstanceRepository` implementation yet.

- [ ] **Step 3: Implement `JpaCaseInstanceRepository.java`**

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
package io.casehub.persistence.jpa;

import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class JpaCaseInstanceRepository implements CaseInstanceRepository {

  @Override
  public Uni<CaseInstance> save(CaseInstance instance) {
    return Panache.withTransaction(
        () ->
            Panache.getSession()
                .chain(
                    session -> {
                      CaseInstanceEntity entity = new CaseInstanceEntity();
                      entity.uuid = instance.getUuid();
                      entity.state = instance.getState();
                      entity.parentPlanItemId = instance.getParentPlanItemId();
                      if (instance.getCaseMetaModel() != null) {
                        entity.caseMetaModel =
                            session.getReference(
                                CaseMetaModelEntity.class, instance.getCaseMetaModel().getId());
                      }
                      return entity
                          .persist()
                          .map(
                              v -> {
                                instance.id = entity.id;
                                return instance;
                              });
                    }));
  }

  @Override
  public Uni<CaseInstance> update(CaseInstance instance) {
    return Panache.withTransaction(
        () ->
            CaseInstanceEntity.<CaseInstanceEntity>findById(instance.id)
                .invoke(
                    entity -> {
                      entity.state = instance.getState();
                      entity.parentPlanItemId = instance.getParentPlanItemId();
                    })
                .replaceWith(instance));
  }

  @Override
  public Uni<CaseInstance> findByUuid(UUID uuid) {
    return Panache.withSession(
            () ->
                CaseInstanceEntity.<CaseInstanceEntity>find(
                        "from CaseInstanceEntity ci join fetch ci.caseMetaModel where ci.uuid = ?1",
                        uuid)
                    .firstResult())
        .map(entity -> entity == null ? null : fromEntity(entity));
  }

  private CaseInstance fromEntity(CaseInstanceEntity entity) {
    CaseInstance instance = new CaseInstance();
    instance.id = entity.id;
    instance.setUuid(entity.uuid);
    instance.setState(entity.state);
    instance.setParentPlanItemId(entity.parentPlanItemId);
    if (entity.caseMetaModel != null) {
      instance.setCaseMetaModel(fromMetaEntity(entity.caseMetaModel));
    }
    return instance;
  }

  private CaseMetaModel fromMetaEntity(CaseMetaModelEntity entity) {
    CaseMetaModel m = new CaseMetaModel();
    m.setId(entity.id);
    m.setName(entity.name);
    m.setNamespace(entity.namespace);
    m.setVersion(entity.version);
    m.setTitle(entity.title);
    m.setDsl(entity.dsl);
    m.setDefinition(entity.definition);
    m.setCreatedAt(entity.createdAt);
    return m;
  }
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java`

**Note on `instance.id`:** `CaseInstance` extends `PanacheEntity` which exposes `public Long id` but has no `getId()`/`setId()` methods. The implementation above accesses `instance.id` directly (confirmed by reading `CaseInstance.java`). `CaseMetaModel` is different — it explicitly declares `getId()`/`setId()`, so `meta.getId()` is valid.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaCaseInstanceRepositoryTest 2>&1 | tail -30
```

Expected: All 4 tests pass.

If `update_changesState` fails with "Could not find entity of type CaseInstanceEntity with id null", it means `instance.getId()` returns null after save — check that `save()` correctly sets the id back on the domain object.

If `findByUuid_returnsSavedInstanceWithMetaModel` fails with a lazy loading exception on `caseMetaModel`, the join fetch HQL is not working — verify the HQL string starts with `from CaseInstanceEntity ci join fetch ci.caseMetaModel`.

- [ ] **Step 5: Commit**

```bash
git add casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaCaseInstanceRepository.java \
        casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaCaseInstanceRepositoryTest.java
git commit -m "feat(persistence-hibernate): add JpaCaseInstanceRepository with tests

4 tests: save, findByUuid null, findByUuid with meta model, update state.
Refs #55"
```

---

## Task 7: TDD — JpaEventLogRepository

**Files:**
- Create: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaEventLogRepositoryTest.java`
- Create: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaEventLogRepository.java`

- [ ] **Step 1: Write the failing tests**

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
package io.casehub.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class JpaEventLogRepositoryTest {

  @Inject EventLogRepository repository;

  @Test
  void append_populatesIdAndSeq() {
    EventLog log = workerScheduled(UUID.randomUUID(), "worker-1");

    repository.append(log).await().indefinitely();

    assertThat(log.id).isNotNull().isPositive();
    assertThat(log.getSeq()).isNotNull().isPositive();
  }

  @Test
  void append_seqIsMonotonicallyIncreasing() {
    UUID caseId = UUID.randomUUID();
    EventLog first = workerScheduled(caseId, "worker-seq-1");
    EventLog second = workerScheduled(caseId, "worker-seq-2");

    repository.append(first).await().indefinitely();
    repository.append(second).await().indefinitely();

    assertThat(second.getSeq()).isGreaterThan(first.getSeq());
  }

  @Test
  void appendAndReturnId_returnsId() {
    EventLog log = workerScheduled(UUID.randomUUID(), "worker-return-id");

    Long returnedId = repository.appendAndReturnId(log).await().indefinitely();

    assertThat(returnedId).isNotNull().isPositive();
    assertThat(returnedId).isEqualTo(log.id);
  }

  @Test
  void findById_returnsNullForUnknown() {
    EventLog result = repository.findById(Long.MAX_VALUE).await().indefinitely();

    assertThat(result).isNull();
  }

  @Test
  void findById_returnsAppendedEvent() {
    UUID caseId = UUID.randomUUID();
    EventLog log = workerScheduled(caseId, "worker-find-by-id");
    repository.append(log).await().indefinitely();

    EventLog found = repository.findById(log.id).await().indefinitely();

    assertThat(found).isNotNull();
    assertThat(found.getCaseId()).isEqualTo(caseId);
    assertThat(found.getEventType()).isEqualTo(CaseHubEventType.WORKER_SCHEDULED);
    assertThat(found.getWorkerId()).isEqualTo("worker-find-by-id");
  }

  @Test
  void findSchedulingEvents_filtersCorrectly() {
    UUID caseId = UUID.randomUUID();
    String workerId = "worker-scheduling-" + UUID.randomUUID();

    EventLog scheduled = event(caseId, workerId, CaseHubEventType.WORKER_SCHEDULED);
    EventLog started = event(caseId, workerId, CaseHubEventType.WORKER_EXECUTION_STARTED);
    EventLog otherWorker = event(caseId, "other-worker", CaseHubEventType.WORKER_SCHEDULED);
    EventLog otherCase =
        event(UUID.randomUUID(), workerId, CaseHubEventType.WORKER_SCHEDULED);

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
  void findByTypes_returnsOrderedBySeq() {
    UUID caseId = UUID.randomUUID();
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    EventLog e1 = event(caseId, "w-" + suffix, CaseHubEventType.CASE_STARTED);
    EventLog e2 = event(caseId, "w-" + suffix, CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    EventLog noise = event(caseId, "w-" + suffix, CaseHubEventType.WORKER_SCHEDULED);

    repository.append(e1).await().indefinitely();
    repository.append(e2).await().indefinitely();
    repository.append(noise).await().indefinitely();

    List<EventLog> result =
        repository
            .findByTypes(
                List.of(CaseHubEventType.CASE_STARTED, CaseHubEventType.WORKER_EXECUTION_COMPLETED))
            .await()
            .indefinitely();

    // Result may include events from other tests — just verify our events are present and ordered
    List<Long> seqs = result.stream().map(EventLog::getSeq).toList();
    assertThat(seqs).isSorted();
    assertThat(result.stream().map(EventLog::getEventType).toList())
        .doesNotContain(CaseHubEventType.WORKER_SCHEDULED);
  }

  @Test
  void findByCaseAndTypes_filtersByCaseId() {
    UUID targetCase = UUID.randomUUID();
    UUID otherCase = UUID.randomUUID();
    String suffix = UUID.randomUUID().toString().substring(0, 8);

    EventLog target = event(targetCase, "w-" + suffix, CaseHubEventType.CASE_STARTED);
    EventLog other = event(otherCase, "w-" + suffix, CaseHubEventType.CASE_STARTED);

    repository.append(target).await().indefinitely();
    repository.append(other).await().indefinitely();

    List<EventLog> result =
        repository
            .findByCaseAndTypes(targetCase, List.of(CaseHubEventType.CASE_STARTED))
            .await()
            .indefinitely();

    assertThat(result).isNotEmpty();
    assertThat(result).allMatch(e -> targetCase.equals(e.getCaseId()));
    assertThat(result.stream().map(EventLog::getSeq).toList()).isSorted();
  }

  @Test
  void findByCaseAndWorkerAndType_filtersCorrectly() {
    UUID caseId = UUID.randomUUID();
    String workerId = "worker-type-filter-" + UUID.randomUUID();

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

  private EventLog workerScheduled(UUID caseId, String workerId) {
    return event(caseId, workerId, CaseHubEventType.WORKER_SCHEDULED);
  }

  private EventLog event(UUID caseId, String workerId, CaseHubEventType eventType) {
    EventLog log = new EventLog();
    log.setCaseId(caseId);
    log.setWorkerId(workerId);
    log.setEventType(eventType);
    log.setStreamType(EventStreamType.WORKER);
    log.setTimestamp(Instant.now());
    return log;
  }
}
```

Save to: `casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaEventLogRepositoryTest.java`

**Note on `EventStreamType`:** verify the correct enum values. The `EventLog` entity uses `EventStreamType` — check `io.casehub.engine.internal.history.EventStreamType` for available constants. Use any valid value (e.g., `EventStreamType.WORKER` or `EventStreamType.CASE`).

**Note on `EventLog.id`:** `EventLog` extends `PanacheEntity` so `id` is `public Long id`. Access it directly (`log.id`) — there's no getter for the inherited Panache id field unless explicitly added.

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaEventLogRepositoryTest -q 2>&1 | tail -15
```

Expected: fails — no `EventLogRepository` implementation.

- [ ] **Step 3: Implement `JpaEventLogRepository.java`**

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
package io.casehub.persistence.jpa;

import io.casehub.engine.internal.history.CaseHubEventType;
import io.casehub.engine.internal.history.EventLog;
import io.casehub.engine.internal.history.EventStreamType;
import io.casehub.engine.spi.EventLogRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class JpaEventLogRepository implements EventLogRepository {

  @Override
  public Uni<Void> append(EventLog eventLog) {
    EventLogEntity entity = toEntity(eventLog);
    return Panache.withTransaction(() -> entity.persist())
        .invoke(
            () -> {
              eventLog.id = entity.id;
              eventLog.setSeq(entity.seq);
            })
        .replaceWithVoid();
  }

  @Override
  public Uni<Long> appendAndReturnId(EventLog eventLog) {
    EventLogEntity entity = toEntity(eventLog);
    return Panache.withTransaction(() -> entity.persistAndFlush())
        .map(
            v -> {
              eventLog.id = entity.id;
              eventLog.setSeq(entity.seq);
              return entity.id;
            });
  }

  @Override
  public Uni<EventLog> findById(Long id) {
    return Panache.withSession(() -> EventLogEntity.<EventLogEntity>findById(id))
        .map(entity -> entity == null ? null : fromEntity(entity));
  }

  @Override
  public Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId) {
    return Panache.withSession(
            () ->
                EventLogEntity.<EventLogEntity>find(
                        "caseId = ?1 and workerId = ?2 and eventType in (?3, ?4, ?5)",
                        caseId,
                        workerId,
                        CaseHubEventType.WORKER_SCHEDULED,
                        CaseHubEventType.WORKER_EXECUTION_STARTED,
                        CaseHubEventType.WORKER_EXECUTION_COMPLETED)
                    .list())
        .map(list -> list.stream().map(this::fromEntity).toList());
  }

  @Override
  public Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types) {
    return Panache.withSession(
            () ->
                EventLogEntity.<EventLogEntity>find("eventType in ?1 order by seq asc", types)
                    .list())
        .map(list -> list.stream().map(this::fromEntity).toList());
  }

  @Override
  public Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types) {
    return Panache.withSession(
            () ->
                EventLogEntity.<EventLogEntity>find(
                        "caseId = ?1 and eventType in ?2 order by seq asc", caseId, types)
                    .list())
        .map(list -> list.stream().map(this::fromEntity).toList());
  }

  @Override
  public Uni<List<EventLog>> findByCaseAndWorkerAndType(
      UUID caseId, String workerId, CaseHubEventType type) {
    return Panache.withSession(
            () ->
                EventLogEntity.<EventLogEntity>find(
                        "caseId = ?1 and workerId = ?2 and eventType = ?3",
                        caseId,
                        workerId,
                        type)
                    .list())
        .map(list -> list.stream().map(this::fromEntity).toList());
  }

  private EventLog fromEntity(EventLogEntity entity) {
    EventLog log = new EventLog();
    log.id = entity.id;
    log.setSeq(entity.seq);
    log.setCaseId(entity.caseId);
    log.setEventType(entity.eventType);
    log.setStreamType(entity.streamType);
    log.setWorkerId(entity.workerId);
    log.setTimestamp(entity.timestamp);
    log.setPayload(entity.payload);
    log.setMetadata(entity.metadata);
    return log;
  }

  private EventLogEntity toEntity(EventLog log) {
    EventLogEntity entity = new EventLogEntity();
    entity.caseId = log.getCaseId();
    entity.eventType = log.getEventType();
    entity.streamType = log.getStreamType();
    entity.workerId = log.getWorkerId();
    entity.timestamp = log.getTimestamp();
    entity.payload = log.getPayload();
    entity.metadata = log.getMetadata();
    return entity;
  }
}
```

Save to: `casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaEventLogRepository.java`

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am \
  -Dtest=JpaEventLogRepositoryTest 2>&1 | tail -30
```

Expected: All 9 tests pass.

If `findByTypes_returnsOrderedBySeq` fails: the test uses a global query (all events of given types). Since the DB isn't cleaned between test methods within a class, events from other tests may appear. The test only checks that the returned seq list is sorted and that `WORKER_SCHEDULED` is absent — adjust if the test's noise-filtering logic needs to be tightened.

If `append_populatesIdAndSeq` fails with `seq` being null after persist: confirm `EventLogEntity.seq` has `@Generated(event = EventType.INSERT)`. Hibernate Reactive should refresh the `seq` value after INSERT because of the `@Generated` annotation. If it's still null, add an explicit `persistAndFlush()` call in `append()` to force a flush + refresh.

- [ ] **Step 5: Commit**

```bash
git add casehub-persistence-hibernate/src/main/java/io/casehub/persistence/jpa/JpaEventLogRepository.java \
        casehub-persistence-hibernate/src/test/java/io/casehub/persistence/jpa/JpaEventLogRepositoryTest.java
git commit -m "feat(persistence-hibernate): add JpaEventLogRepository with tests

9 tests covering append, appendAndReturnId, findById, findSchedulingEvents,
findByTypes, findByCaseAndTypes, findByCaseAndWorkerAndType.
Refs #55"
```

---

## Task 8: Full Test Suite Pass and Engine Verification

**Files:** none (verification only)

- [ ] **Step 1: Run the full casehub-persistence-hibernate test suite**

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl casehub-persistence-hibernate -am 2>&1 | tail -20
```

Expected: `BUILD SUCCESS` — all tests in `JpaCaseMetaModelRepositoryTest`, `JpaCaseInstanceRepositoryTest`, and `JpaEventLogRepositoryTest` pass.

- [ ] **Step 2: Verify engine tests are unaffected**

Engine was not modified in this PR, but confirm nothing broke in the build:

```bash
cd /Users/mdproctor/dev/casehub-engine
/opt/homebrew/bin/mvn test -pl engine -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`. Engine tests still use the old Panache entity classes directly — this is expected. The SPI interfaces were added but not wired into anything yet.

- [ ] **Step 3: Push the branch and open the PR**

```bash
cd /Users/mdproctor/dev/casehub-engine
git push -u origin feat/persistence/hibernate
gh pr create \
  --base feat/rename-binding-casedefinition \
  --title "feat: add repository SPI interfaces + casehub-persistence-hibernate module" \
  --body "$(cat <<'EOF'
## Summary

- Adds three repository SPI interfaces to `engine/spi/`: `CaseMetaModelRepository`, `CaseInstanceRepository`, `EventLogRepository`
- Creates `casehub-persistence-hibernate` module with JPA entity classes (`CaseMetaModelEntity`, `CaseInstanceEntity`, `EventLogEntity`) and `@ApplicationScoped` repository implementations
- 17 integration tests run against a real PostgreSQL container (Quarkus Dev Services)
- No existing engine code changed — this PR adds only new files

## Design notes

- Entity classes are separate from domain POJOs (follows quarkus-flow convention; avoids `LazyInitializationException` from session lifecycle coupling)
- Engine module is NOT indexed in hibernate module tests to prevent duplicate entity mapping errors
- Flyway migrations remain in engine for now; they are picked up from the engine JAR via `classpath:db/migration`; moved to this module in PR 3

## Test plan

- [ ] `mvn test -pl casehub-persistence-hibernate -am` — all 17 tests pass
- [ ] `mvn test -pl engine -am` — no regressions in engine tests

Refs #55

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
EOF
)"
```

---

## Troubleshooting Reference

| Symptom | Cause | Fix |
|---------|-------|-----|
| `UnsatisfiedResolutionException: CaseMetaModelRepository` | Repository class not indexed | Check the module's own sources are compiled — the class should be auto-indexed |
| Duplicate entity mapping error on startup | Engine's `@Entity` classes discovered | Ensure `quarkus.index-dependency.engine` is NOT in test `application.properties` |
| `seq` is null after `append()` | `@Generated` not refreshing | Use `persistAndFlush()` + verify `@Generated(event = EventType.INSERT)` annotation is present |
| `TransientPropertyValueException` on `CaseInstance.save()` | Detached `CaseMetaModelEntity` | Use `session.getReference(CaseMetaModelEntity.class, id)` — already in the implementation |
| Schema validation fails | Flyway didn't run | Verify `quarkus.flyway.migrate-at-start=true` in `application.properties` (not test-scoped) |
| Engine JAR migration files not found | Engine not on classpath | The engine is a compile dep of casehub-persistence-hibernate — Flyway finds migrations from it automatically |
| `Table "case_meta_model" already exists` | Flyway saw checksum change | Add `quarkus.flyway.repair-at-start=true` for a one-time fix; then remove |
