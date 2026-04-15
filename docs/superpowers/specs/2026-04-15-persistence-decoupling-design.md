# Persistence Decoupling — Repository SPI + casehub-persistence-memory + casehub-persistence-hibernate
**Date:** 2026-04-15
**Status:** Approved — ready for implementation

---

## Problem

The engine is tightly coupled to Hibernate Reactive and PostgreSQL. `CaseInstance`, `EventLog`,
and `CaseMetaModel` all extend `PanacheEntity`, and every event handler calls
`Panache.withTransaction()` directly. There is no storage abstraction.

Consequences:
- Every `@QuarkusTest` requires a real PostgreSQL container via TestContainers — slow, Docker-dependent
- There is no lightweight deployment option without a database
- Adding a second storage backend (e.g. Redis, in-memory) requires modifying the engine core

---

## Goal

Make persistence fully pluggable via three repository SPI interfaces. Provide two implementations:

| Module | Storage | Use case |
|---|---|---|
| `casehub-persistence-memory` | `ConcurrentHashMap` + `AtomicLong` | Fast tests, no Docker |
| `casehub-persistence-hibernate` | Hibernate Reactive + PostgreSQL | Production |

The engine core has **no** Hibernate, Panache, or PostgreSQL dependency after this change.

---

## Architecture

```
api/                               ← no deps (unchanged)
engine/                            ← depends on api + Quartz; NO Hibernate/Panache
  spi/
    CaseMetaModelRepository        ← NEW: interface
    CaseInstanceRepository         ← NEW: interface
    EventLogRepository             ← NEW: interface
  internal/model/CaseInstance      ← POJO (remove PanacheEntity + JPA annotations)
  internal/model/CaseMetaModel     ← POJO (remove PanacheEntity + JPA annotations)
  internal/history/EventLog        ← POJO (remove PanacheEntity + JPA annotations)
  internal/engine/handlers/*       ← refactored to inject repositories

casehub-persistence-memory/        ← NEW module; depends on engine; no DB deps
  InMemoryCaseMetaModelRepository  ← @Alternative @ApplicationScoped
  InMemoryCaseInstanceRepository   ← @Alternative @ApplicationScoped
  InMemoryEventLogRepository       ← @Alternative @ApplicationScoped

casehub-persistence-hibernate/     ← NEW module; depends on engine; Hibernate Reactive + Flyway
  HibernateCaseMetaModelRepository ← @ApplicationScoped (default)
  HibernateCaseInstanceRepository  ← @ApplicationScoped (default)
  HibernateEventLogRepository      ← @ApplicationScoped (default)
  META-INF/orm.xml                 ← external JPA mapping (no annotations on domain objects)
  db/migration/                    ← Flyway migrations (moved from engine)

casehub-blackboard/                ← unchanged
casehub-resilience/                ← unchanged
```

---

## Repository SPI Interfaces

All interfaces return `Uni<T>` for consistency with the engine's reactive model.
Placed in `engine/src/main/java/io/casehub/engine/spi/`.

### CaseMetaModelRepository

```java
package io.casehub.engine.spi;

public interface CaseMetaModelRepository {

    /** Find by the unique (namespace, name, version) key. Returns empty if not registered. */
    Uni<CaseMetaModel> findByKey(String namespace, String name, String version);

    /** Persist a new case meta model. Returns the saved instance with id populated. */
    Uni<CaseMetaModel> save(CaseMetaModel metaModel);
}
```

### CaseInstanceRepository

```java
package io.casehub.engine.spi;

public interface CaseInstanceRepository {

    /** Persist a new case instance. Returns the saved instance with id populated. */
    Uni<CaseInstance> save(CaseInstance instance);

    /** Merge state changes back to storage (status transitions). */
    Uni<CaseInstance> update(CaseInstance instance);

    /**
     * Load a case instance by UUID with its CaseMetaModel eagerly joined.
     * Returns null if not found.
     */
    Uni<CaseInstance> findByUuid(UUID uuid);
}
```

### EventLogRepository

```java
package io.casehub.engine.spi;

public interface EventLogRepository {

    /** Append an event to the log. */
    Uni<Void> append(EventLog eventLog);

    /**
     * Append an event and flush, returning the generated id.
     * Used by WorkerScheduleEventHandler to get the id before scheduling a Quartz job.
     */
    Uni<Long> appendAndReturnId(EventLog eventLog);

    /** Load an event by id. Returns null if not found. */
    Uni<EventLog> findById(Long id);

    /**
     * Find WORKER_SCHEDULED, WORKER_EXECUTION_STARTED, and WORKER_EXECUTION_COMPLETED events
     * for a specific case+worker. Used by WorkerScheduleEventHandler for idempotency checking.
     */
    Uni<List<EventLog>> findSchedulingEvents(UUID caseId, String workerId);

    /**
     * Find all events matching any of the given types, ordered by seq ascending.
     * Used by WorkerExecutionRecoveryService to find pending scheduled workers on startup.
     */
    Uni<List<EventLog>> findByTypes(Collection<CaseHubEventType> types);

    /**
     * Find events for a specific case matching any of the given types, ordered by seq ascending.
     * Used by WorkerExecutionRecoveryService to rebuild CaseContext state.
     */
    Uni<List<EventLog>> findByCaseAndTypes(UUID caseId, Collection<CaseHubEventType> types);

    /**
     * Find events for a specific case+worker of a specific type.
     * Used by WorkerExecutionJobListener to count failed attempts for retry logic.
     */
    Uni<List<EventLog>> findByCaseAndWorkerAndType(
            UUID caseId, String workerId, CaseHubEventType type);
}
```

---

## Domain Object Changes

### CaseInstance

- Remove `extends PanacheEntity`
- Add `public Long id` field (was inherited)
- Remove: `@Entity`, `@Table`, `@Column`, `@OneToMany`, `@ManyToOne`, `@JoinColumn`, all JPA imports
- Keep: `uuid`, `state`, `caseMetaModel`, `parentPlanItemId`, `caseContext` (transient), all business logic
- The `caseMetaModel` reference stays — it's a domain relationship, not a JPA one

### EventLog

- Remove `extends PanacheEntity`
- Add `public Long id` field
- Remove: `@Entity`, `@Table`, `@Column`, `@Enumerated`, `@Generated`, `@JdbcTypeCode`, all JPA imports
- Remove: `persistScheduledEvent()` — moves to `EventLogRepository.appendAndReturnId()`
- Remove: `findSchedulingEvents()` static method — moves to `EventLogRepository`
- Keep: `seq`, `caseId`, `eventType`, `streamType`, `workerId`, `timestamp`, `payload`, `metadata` fields
- `seq` becomes a regular `Long` field — set by the repository implementation

### CaseMetaModel

- Remove `extends PanacheEntity`
- Add `public Long id` field
- Remove: `@Entity`, `@Table`, `@Column`, `@OneToMany`, `@PrePersist`, `@UniqueConstraint`, all JPA imports
- Keep: `name`, `namespace`, `version`, `title`, `dsl`, `definition`, `createdAt`, all getters/setters
- `createdAt` is set by the repository on save, not by a JPA lifecycle callback
- Remove `List<CaseInstance> caseInstance` back-reference — this was a JPA bidirectional mapping concern only; the domain model doesn't need it

---

## Engine Handler Refactoring

Each handler that currently calls Panache injects the relevant repository instead.
`Panache.withTransaction()` calls are removed — transaction management moves into the
Hibernate repository implementations.

| Handler / Service | Current Panache call | Becomes |
|---|---|---|
| `CaseHubReactor` | `instance.persist()` | `caseInstanceRepository.save(instance)` |
| `CaseStatusChangedHandler` | `session.merge(caseInstance)` + `eventLog.persist()` | `caseInstanceRepository.update(...)` + `eventLogRepository.append(...)` |
| `WorkerRetriesExhaustedEventHandler` | `session.merge(caseInstance)` + `eventLog.persist()` | `caseInstanceRepository.update(...)` + `eventLogRepository.append(...)` |
| `CaseStartedEventHandler` | `eventLog.persist()` | `eventLogRepository.append(...)` |
| `WorkflowExecutionCompletedHandler` | `eventLog.persist()` | `eventLogRepository.append(...)` |
| `GoalReachedEventHandler` | `eventLog.persist()` + `EventLog.find(...)` | `eventLogRepository.append(...)` + `eventLogRepository.findByCaseAndTypes(...)` |
| `MilestoneReachedEventHandler` | `eventLog.persist()` | `eventLogRepository.append(...)` |
| `SignalReceivedEventHandler` | `eventLog.persist()` | `eventLogRepository.append(...)` |
| `WorkerScheduleEventHandler` | `EventLog.findSchedulingEvents(...)` + `eventLog.persistScheduledEvent()` | `eventLogRepository.findSchedulingEvents(...)` + `eventLogRepository.appendAndReturnId(...)` |
| `WorkerExecutionManager` | `EventLog.findById(eventLogId)` | `eventLogRepository.findById(eventLogId)` |
| `WorkerExecutionJobListener` | `PanacheEntityBase.persist(eventLog)` + `EventLog.find(...)` | `eventLogRepository.append(...)` + `eventLogRepository.findByCaseAndWorkerAndType(...)` |
| `WorkerExecutionTask` | `EventLog.findById(eventLogId)` | `eventLogRepository.findById(eventLogId)` |
| `WorkerExecutionRecoveryService` | `sessionFactory.withSession(...)` queries for CaseInstance + EventLog | `caseInstanceRepository.findByUuid(...)` + `eventLogRepository.findByTypes(...)` + `eventLogRepository.findByCaseAndTypes(...)` |
| `CaseDefinitionRegistry` | `CaseMetaModel.find(...).firstResult()` + `definition.persistAndFlush()` | `caseMetaModelRepository.findByKey(...)` + `caseMetaModelRepository.save(...)` |

---

## casehub-persistence-memory

### Structure

```
casehub-persistence-memory/
  pom.xml                                          ← depends on engine + quarkus-arc
  src/main/java/io/casehub/persistence/memory/
    InMemoryCaseMetaModelRepository.java           ← @Alternative @ApplicationScoped
    InMemoryCaseInstanceRepository.java            ← @Alternative @ApplicationScoped
    InMemoryEventLogRepository.java                ← @Alternative @ApplicationScoped
```

No database, no Hibernate, no Flyway, no Quartz JDBC store.

### Activation

Projects using in-memory persistence add to `application.properties`:

```properties
quarkus.arc.selected-alternatives=\
  io.casehub.persistence.memory.InMemoryCaseMetaModelRepository,\
  io.casehub.persistence.memory.InMemoryCaseInstanceRepository,\
  io.casehub.persistence.memory.InMemoryEventLogRepository
quarkus.quartz.store-type=ram
```

And omit `casehub-persistence-hibernate` from their dependencies (and therefore have no
datasource, no Flyway, no PostgreSQL).

### In-Memory Design

- IDs generated by `AtomicLong` counters (one per repository)
- `EventLog.seq` set from a separate `AtomicLong` — monotonically increasing, same semantics as PostgreSQL `GENERATED ALWAYS AS IDENTITY`
- `CaseMetaModel.createdAt` set on save to `Instant.now()`
- All operations return `Uni.createFrom().item(result)` — synchronous, no I/O
- Thread-safe: `ConcurrentHashMap` for stores, `CopyOnWriteArrayList` where ordered sequences needed
- No transaction management needed

---

## casehub-persistence-hibernate

### Structure

```
casehub-persistence-hibernate/
  pom.xml                                          ← depends on engine + Hibernate Reactive + Flyway
  src/main/java/io/casehub/persistence/hibernate/
    HibernateCaseMetaModelRepository.java          ← @ApplicationScoped (default)
    HibernateCaseInstanceRepository.java           ← @ApplicationScoped (default)
    HibernateEventLogRepository.java               ← @ApplicationScoped (default)
  src/main/resources/
    META-INF/orm.xml                               ← external JPA mapping for engine domain objects
    db/migration/                                  ← Flyway SQL migrations (moved from engine)
```

### orm.xml approach

`casehub-persistence-hibernate` provides `META-INF/orm.xml` that maps the engine's plain Java
classes to database tables. No JPA annotations on domain objects. Example structure:

```xml
<entity-mappings>
  <entity class="io.casehub.engine.internal.model.CaseInstance">
    <table name="case_instance"/>
    <attributes>
      <id name="id"><generated-value strategy="IDENTITY"/></id>
      <basic name="uuid"><column name="uuid" nullable="false" unique="true" updatable="false"/></basic>
      <basic name="state"><column name="state" length="50"/>
        <enumerated>STRING</enumerated>
      </basic>
      <basic name="parentPlanItemId"><column name="parent_plan_item_id" nullable="true"/></basic>
      <many-to-one name="caseMetaModel" fetch="LAZY">
        <join-column name="case_meta_model_id"/>
      </many-to-one>
    </attributes>
  </entity>

  <entity class="io.casehub.engine.internal.history.EventLog">
    <table name="event_log"/>
    <attributes>
      <id name="id"><generated-value strategy="IDENTITY"/></id>
      <!-- seq: GENERATED ALWAYS AS IDENTITY in DDL, insertable=false -->
      <basic name="seq"><column name="seq" insertable="false" updatable="false"/></basic>
      <basic name="caseId"><column name="case_id" nullable="false" updatable="false"/></basic>
      <basic name="eventType"><column name="event_type" length="255"/>
        <enumerated>STRING</enumerated>
      </basic>
      <basic name="streamType"><column name="stream_type" length="255"/>
        <enumerated>STRING</enumerated>
      </basic>
      <basic name="workerId"><column name="worker_id" length="255" nullable="true"/></basic>
      <basic name="timestamp"><column name="timestamp" nullable="false"/></basic>
      <!-- payload and metadata: JSONB — handled via column definition in Flyway DDL -->
      <basic name="payload"><column name="payload" column-definition="jsonb"/></basic>
      <basic name="metadata"><column name="metadata" column-definition="jsonb"/></basic>
    </attributes>
  </entity>

  <entity class="io.casehub.engine.internal.model.CaseMetaModel">
    <table name="case_meta_model">
      <unique-constraint><column-name>namespace</column-name><column-name>name</column-name><column-name>version</column-name></unique-constraint>
    </table>
    <attributes>
      <id name="id"><generated-value strategy="IDENTITY"/></id>
      <basic name="name"><column name="name" nullable="false" length="255"/></basic>
      <basic name="namespace"><column name="namespace" length="255"/></basic>
      <basic name="version"><column name="version" nullable="false" length="50"/></basic>
      <basic name="title"><column name="title" length="500"/></basic>
      <basic name="dsl"><column name="dsl" length="50"/></basic>
      <basic name="definition"><column name="definition" column-definition="jsonb"/></basic>
      <basic name="createdAt"><column name="created_at" nullable="false" updatable="false"/></basic>
    </attributes>
  </entity>
</entity-mappings>
```

### Hibernate implementations

Use `Mutiny.SessionFactory` injected directly — Panache active record pattern is not available
since the domain objects are no longer `PanacheEntity`. Each repository wraps operations in
`sessionFactory.withTransaction()` or `sessionFactory.withSession()` as appropriate.

---

## Quartz Store

Quartz is in the `engine` module. The store type is configurable:

- `casehub-persistence-hibernate` sets default: `quarkus.quartz.store-type=jdbc` (persisted)
- `casehub-persistence-memory` users set: `quarkus.quartz.store-type=ram` (in-memory)

Engine tests that use `casehub-persistence-memory` include `quarkus.quartz.store-type=ram`
in their `application.properties`.

---

## Flyway

Flyway migrations currently live in `engine/src/main/resources/db/migration/`. They move to
`casehub-persistence-hibernate/src/main/resources/db/migration/`. The engine module has no
migration files after this change.

---

## Testing

### Engine module tests

After this change, `engine` tests use `casehub-persistence-memory` as a test-scoped dependency.
The test `application.properties` selects the in-memory alternatives and sets
`quarkus.quartz.store-type=ram`. No TestContainers, no Docker.

### casehub-persistence-hibernate module tests

Integration tests in `casehub-persistence-hibernate` exercise the Hibernate implementations
against a real PostgreSQL container (TestContainers). These are the only tests that need Docker.

### casehub-persistence-memory module tests

Pure unit tests verifying the in-memory behaviour: correct ID generation, ordering guarantees
on EventLog (seq monotonically increasing), query filtering. No Quarkus required for these.

---

## Implementation Scope

**Files modified in `engine`:**
- `CaseInstance.java` — remove PanacheEntity + JPA annotations, add `Long id`
- `EventLog.java` — remove PanacheEntity + JPA annotations, add `Long id`, remove static query methods
- `CaseMetaModel.java` — remove PanacheEntity + JPA annotations, add `Long id`
- All 8+ handler/service classes — replace Panache calls with repository injection
- `engine/pom.xml` — remove Hibernate Reactive, PostgreSQL, Flyway dependencies
- `engine/src/test` — switch to `casehub-persistence-memory` for tests

**New files in `engine`:**
- `spi/CaseMetaModelRepository.java`
- `spi/CaseInstanceRepository.java`
- `spi/EventLogRepository.java`

**New module: `casehub-persistence-memory`**
- `pom.xml`
- `InMemoryCaseMetaModelRepository.java`
- `InMemoryCaseInstanceRepository.java`
- `InMemoryEventLogRepository.java`
- `src/test/resources/application.properties`
- Unit tests for each implementation

**New module: `casehub-persistence-hibernate`**
- `pom.xml`
- `HibernateCaseMetaModelRepository.java`
- `HibernateCaseInstanceRepository.java`
- `HibernateEventLogRepository.java`
- `META-INF/orm.xml`
- `db/migration/` (Flyway migrations moved from engine)
- Integration tests against PostgreSQL

**Parent `pom.xml`:** add two new modules.

---

## Out of Scope

- `CaseInstanceCache` — already in-memory, no change
- `CaseDefinitionRegistry` in-memory cache — already exists, no change
- Quartz job data — Quartz persists its own job data separately from our domain objects
- `WorkerExecutionKeys` utility — no change
- Any changes to `api`, `casehub-blackboard`, or `casehub-resilience`
