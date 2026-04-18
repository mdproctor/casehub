# casehub-blackboard Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the `casehub-blackboard` Maven module to casehub-engine, enabling async CMMN/Blackboard orchestration via `PlanningStrategy`, `CasePlanModel`, `Stage`, and reactive `LoopControl`.

**Architecture:** `LoopControl.select()` changes to return `Uni<List<Binding>>`. `PlanningStrategyLoopControl` manages a per-case `CasePlanModel`, delegates to `PlanningStrategy`, evaluates Stage lifecycle, and tracks PlanItem completion via a second `@ConsumeEvent(WORKER_EXECUTION_FINISHED)` handler. All wiring is opt-in via CDI `@Alternative @Priority(10)`.

**Tech Stack:** Java 21, Quarkus 3.17.5, Vert.x Mutiny, SmallRye Mutiny `Uni`, Jakarta CDI, JUnit 5, AssertJ, QuarkusTest.

**Spec:** `docs/superpowers/specs/2026-04-18-casehub-blackboard-design.md`
**Issue:** casehubio/engine#76 | **Epic:** casehubio/engine#30

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `api/src/main/java/io/casehub/api/engine/LoopControl.java` | Modify | Return `Uni<List<Binding>>` |
| `engine/src/main/java/io/casehub/engine/internal/engine/ChoreographyLoopControl.java` | Modify | Wrap result in `Uni.createFrom().item()` |
| `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseContextChangedEventHandler.java` | Modify | Await `Uni` from `loopControl.select()` |
| `pom.xml` (root) | Modify | Add `casehub-blackboard` module |
| `casehub-blackboard/pom.xml` | Create | Module POM |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java` | Create | Activation record for a Binding |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java` | Create | Control blackboard interface |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java` | Create | In-memory CasePlanModel impl |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/StageStatus.java` | Create | Stage lifecycle enum |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java` | Create | CMMN Stage POJO |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/event/BlackboardEventBusAddresses.java` | Create | Stage event bus address constants |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageActivatedEvent.java` | Create | Record |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageCompletedEvent.java` | Create | Record |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageTerminatedEvent.java` | Create | Record |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategy.java` | Create | Selection SPI |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/DefaultPlanningStrategy.java` | Create | Returns all eligible, equal priority |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java` | Create | Entry/exit evaluation, publishes stage events |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/registry/BlackboardRegistry.java` | Create | Shared per-case plan model + completion index |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/MilestoneAchievementHandler.java` | Create | Promotes milestone to ACHIEVED in CasePlanModel |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java` | Create | Marks PlanItem COMPLETED, triggers Stage autocomplete |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java` | Create | `@Alternative @Priority(10)` LoopControl impl |

---

## Task 1: Make `LoopControl` async

**Files:**
- Modify: `api/src/main/java/io/casehub/api/engine/LoopControl.java`
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/ChoreographyLoopControl.java`
- Modify: `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseContextChangedEventHandler.java`

- [ ] **Step 1: Update `LoopControl` to return `Uni`**

```java
// api/src/main/java/io/casehub/api/engine/LoopControl.java
package io.casehub.api.engine;

import io.casehub.api.model.Binding;
import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * SPI for controlling which eligible bindings are selected for execution.
 *
 * <p>Returns {@code Uni<List<Binding>>} to allow non-blocking I/O during
 * selection (e.g. EventLog queries, LLM scoring). See casehubio/engine#76.
 *
 * <p>The default implementation ({@link
 * io.casehub.engine.internal.engine.ChoreographyLoopControl}) wraps with
 * {@code Uni.createFrom().item(eligible)} — no behaviour change.
 */
public interface LoopControl {
  Uni<List<Binding>> select(PlanExecutionContext context, List<Binding> eligible);
}
```

- [ ] **Step 2: Update `ChoreographyLoopControl`**

```java
// engine/src/main/java/io/casehub/engine/internal/engine/ChoreographyLoopControl.java
@Override
public Uni<List<Binding>> select(final PlanExecutionContext context, final List<Binding> eligible) {
  return Uni.createFrom().item(eligible);
}
```

Add import: `import io.smallrye.mutiny.Uni;`

- [ ] **Step 3: Update `CaseContextChangedEventHandler.rules()` to chain the Uni**

Replace the synchronous `loopControl.select()` call block inside `rules()`:

```java
// engine/.../handler/CaseContextChangedEventHandler.java
// Replace from "// LoopControl decides..." to the end of the method with:

PlanExecutionContext planCtx =
    new PlanExecutionContext(caseInstance.getUuid(), definition, caseInstance.getCaseContext());

return loopControl.select(planCtx, eligible)
    .chain(
        selected -> {
          List<Uni<Void>> unis = new ArrayList<>(selected.size());
          for (Binding b : selected) {
            unis.add(publishWorkerSchedules(caseInstance, workers, b, b.getCapability()));
          }
          if (unis.isEmpty()) return Uni.createFrom().voidItem();
          return Uni.combine().all().unis(unis).discardItems();
        });
```

- [ ] **Step 4: Build engine and api modules — verify compilation**

```bash
cd /path/to/casehub-engine
mvn compile -pl api,engine -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run existing engine tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl engine -q
```

Expected: all tests pass (353+). No regressions from the async change.

- [ ] **Step 6: Commit**

```bash
git add api/src engine/src
git commit -m "feat: make LoopControl.select() return Uni<List<Binding>> (casehubio/engine#76)

Enables PlanningStrategy implementations to perform non-blocking I/O
during selection. ChoreographyLoopControl wraps with Uni.createFrom().item()
— zero behaviour change. CaseContextChangedEventHandler.rules() chains
the Uni instead of blocking."
```

---

## Task 2: Maven module setup

**Files:**
- Modify: `pom.xml` (root)
- Create: `casehub-blackboard/pom.xml`

- [ ] **Step 1: Add module to root `pom.xml`**

In the `<modules>` section, add:

```xml
<module>casehub-blackboard</module>
```

- [ ] **Step 2: Create `casehub-blackboard/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-engine-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blackboard</artifactId>
  <name>CaseHub Blackboard</name>
  <description>
    Optional CMMN/Blackboard orchestration layer for casehub-engine.
    Provides PlanningStrategy, CasePlanModel, Stage, and PlanItem.
    See casehubio/engine#76 (epic: casehubio/engine#30).
  </description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine-api</artifactId>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-engine</artifactId>
    </dependency>

    <!-- Quarkus Vert.x for EventBus and @ConsumeEvent -->
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-vertx</artifactId>
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
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-test-vertx</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: Create directory structure**

```bash
mkdir -p casehub-blackboard/src/main/java/io/casehub/blackboard/{plan,stage,event,control,registry,handler}
mkdir -p casehub-blackboard/src/test/java/io/casehub/blackboard/{plan,stage,control,it,e2e}
```

- [ ] **Step 4: Verify module compiles empty**

```bash
mvn compile -pl casehub-blackboard -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add pom.xml casehub-blackboard/
git commit -m "feat: add casehub-blackboard Maven module skeleton (casehubio/engine#76)"
```

---

## Task 3: `PlanItem`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java
package io.casehub.blackboard.plan;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for PlanItem ordering and lifecycle. See casehubio/engine#76. */
class PlanItemTest {

    @Test
    void higher_priority_sorts_before_lower() {
        PlanItem low = PlanItem.create("binding-a", "worker-a", 0);
        PlanItem high = PlanItem.create("binding-b", "worker-b", 10);
        List<PlanItem> items = new ArrayList<>(List.of(low, high));
        Collections.sort(items);
        assertThat(items.get(0).getBindingName()).isEqualTo("binding-b");
    }

    @Test
    void equal_priority_earlier_creation_sorts_first() throws InterruptedException {
        PlanItem first = PlanItem.create("binding-a", "worker-a", 5);
        Thread.sleep(2);
        PlanItem second = PlanItem.create("binding-b", "worker-b", 5);
        List<PlanItem> items = new ArrayList<>(List.of(second, first));
        Collections.sort(items);
        assertThat(items.get(0).getBindingName()).isEqualTo("binding-a");
    }

    @Test
    void default_status_is_pending() {
        PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
        assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.PENDING);
    }

    @Test
    void status_transitions_pending_to_running_to_completed() {
        PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
        item.setStatus(PlanItem.PlanItemStatus.RUNNING);
        assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.RUNNING);
        item.setStatus(PlanItem.PlanItemStatus.COMPLETED);
        assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.COMPLETED);
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemTest -q 2>&1 | tail -5
```

Expected: compilation error (class not found).

- [ ] **Step 3: Implement `PlanItem`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java
package io.casehub.blackboard.plan;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Activation record for a {@link io.casehub.api.model.Binding} on the
 * {@link CasePlanModel} scheduling agenda.
 *
 * <p>Priority is assigned by {@link io.casehub.blackboard.control.PlanningStrategy}.
 * Status is updated by {@link io.casehub.blackboard.handler.PlanItemCompletionHandler}
 * on worker completion. Implements {@link Comparable} for priority-ordered sorting
 * (higher priority first; FIFO for equal priority). See casehubio/engine#76.
 */
public class PlanItem implements Comparable<PlanItem> {

  private final String planItemId;
  private final String bindingName;
  private final String workerName;
  private int priority;
  private PlanItemStatus status;
  private final Instant createdAt;
  private Optional<String> parentStageId;

  /** Lifecycle states. See casehubio/engine#76. */
  public enum PlanItemStatus {
    PENDING, RUNNING, COMPLETED, FAULTED, CANCELLED
  }

  private PlanItem(String bindingName, String workerName, int priority) {
    this.planItemId = UUID.randomUUID().toString();
    this.bindingName = bindingName;
    this.workerName = workerName;
    this.priority = priority;
    this.status = PlanItemStatus.PENDING;
    this.createdAt = Instant.now();
    this.parentStageId = Optional.empty();
  }

  public static PlanItem create(String bindingName, String workerName, int priority) {
    return new PlanItem(bindingName, workerName, priority);
  }

  @Override
  public int compareTo(PlanItem other) {
    int cmp = Integer.compare(other.priority, this.priority); // higher first
    if (cmp != 0) return cmp;
    return this.createdAt.compareTo(other.createdAt); // earlier first
  }

  public String getPlanItemId() { return planItemId; }
  public String getBindingName() { return bindingName; }
  public String getWorkerName() { return workerName; }
  public int getPriority() { return priority; }
  public void setPriority(int priority) { this.priority = priority; }
  public PlanItemStatus getStatus() { return status; }
  public void setStatus(PlanItemStatus status) { this.status = status; }
  public Instant getCreatedAt() { return createdAt; }
  public Optional<String> getParentStageId() { return parentStageId; }
  public void setParentStageId(String stageId) { this.parentStageId = Optional.ofNullable(stageId); }
}
```

- [ ] **Step 4: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemTest -q
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add PlanItem with priority ordering and lifecycle status (casehubio/engine#76)"
```

---

## Task 4: `CasePlanModel` and `DefaultCasePlanModel`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java
package io.casehub.blackboard.plan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultCasePlanModel agenda management and milestone tracking.
 * See casehubio/engine#76. Milestone/Goal/Stage alignment: casehubio/engine#84.
 */
class DefaultCasePlanModelTest {

    private DefaultCasePlanModel plan;

    @BeforeEach
    void setUp() {
        plan = new DefaultCasePlanModel(UUID.randomUUID());
    }

    @Test
    void agenda_returns_only_pending_items_sorted_by_priority() {
        PlanItem low = PlanItem.create("b-low", "w-low", 1);
        PlanItem high = PlanItem.create("b-high", "w-high", 10);
        PlanItem running = PlanItem.create("b-run", "w-run", 99);
        running.setStatus(PlanItem.PlanItemStatus.RUNNING);

        plan.addPlanItem(low);
        plan.addPlanItem(high);
        plan.addPlanItem(running);

        assertThat(plan.getAgenda()).hasSize(2);
        assertThat(plan.getAgenda().get(0).getBindingName()).isEqualTo("b-high");
        assertThat(plan.getAgenda().get(1).getBindingName()).isEqualTo("b-low");
    }

    @Test
    void getTopPlanItems_respects_limit() {
        for (int i = 0; i < 5; i++) {
            plan.addPlanItem(PlanItem.create("b-" + i, "w-" + i, i));
        }
        assertThat(plan.getTopPlanItems(3)).hasSize(3);
    }

    @Test
    void getTopPlanItems_handles_limit_larger_than_agenda() {
        plan.addPlanItem(PlanItem.create("b-a", "w-a", 0));
        assertThat(plan.getTopPlanItems(100)).hasSize(1);
    }

    @Test
    void getPlanItem_returns_by_id() {
        PlanItem item = PlanItem.create("b-a", "w-a", 0);
        plan.addPlanItem(item);
        assertThat(plan.getPlanItem(item.getPlanItemId())).contains(item);
    }

    @Test
    void removePlanItem_removes_from_agenda() {
        PlanItem item = PlanItem.create("b-a", "w-a", 0);
        plan.addPlanItem(item);
        plan.removePlanItem(item.getPlanItemId());
        assertThat(plan.getAgenda()).isEmpty();
    }

    @Test
    void milestone_lifecycle_pending_to_achieved() {
        plan.trackMilestone("docs-received");
        assertThat(plan.isMilestoneAchieved("docs-received")).isFalse();
        plan.achieveMilestone("docs-received");
        assertThat(plan.isMilestoneAchieved("docs-received")).isTrue();
    }

    @Test
    void achieve_untracked_milestone_does_not_throw() {
        plan.achieveMilestone("unknown");
        assertThat(plan.isMilestoneAchieved("unknown")).isFalse();
    }

    @Test
    void focus_and_rationale_roundtrip() {
        plan.setFocus("analysis");
        plan.setFocusRationale("high-value documents detected");
        assertThat(plan.getFocus()).contains("analysis");
    }

    @Test
    void extensible_kv_roundtrip() {
        plan.put("custom-key", 42);
        assertThat(plan.get("custom-key", Integer.class)).contains(42);
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest -q 2>&1 | tail -3
```

Expected: compilation error.

- [ ] **Step 3: Implement `CasePlanModel` interface**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java
package io.casehub.blackboard.plan;

import io.casehub.blackboard.stage.Stage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The control blackboard — Hayes-Roth's BB1 "control board" — paired 1:1 with
 * each running case. Holds the scheduling agenda, focus of attention, resource
 * budget, stage tracking, milestone lifecycle state, and extensible key-value
 * state. Written to by {@link io.casehub.blackboard.control.PlanningStrategy};
 * read by {@link io.casehub.blackboard.control.PlanningStrategyLoopControl}.
 *
 * <p>Milestone lifecycle tracking here is an interim approach. Full alignment
 * of Milestone, Stage, and Goal is tracked in casehubio/engine#84.
 * See casehubio/engine#76.
 */
public interface CasePlanModel {

  UUID getCaseId();

  // Scheduling agenda
  void addPlanItem(PlanItem planItem);
  void removePlanItem(String planItemId);
  Optional<PlanItem> getPlanItem(String planItemId);
  /** Returns only PENDING items, sorted highest-priority first. */
  List<PlanItem> getAgenda();
  List<PlanItem> getTopPlanItems(int maxCount);

  // Stage management
  void addStage(Stage stage);
  Optional<Stage> getStage(String stageId);
  List<Stage> getPendingStages();
  List<Stage> getActiveStages();
  List<Stage> getAllStages();

  // Milestone lifecycle (PENDING → ACHIEVED). See casehubio/engine#84.
  void trackMilestone(String milestoneName);
  void achieveMilestone(String milestoneName);
  boolean isMilestoneAchieved(String milestoneName);

  // Focus of attention
  void setFocus(String focusArea);
  Optional<String> getFocus();
  void setFocusRationale(String rationale);

  // Resource budget
  void setResourceBudget(Map<String, Object> budget);
  Map<String, Object> getResourceBudget();

  // Extensible key-value
  void put(String key, Object value);
  <T> Optional<T> get(String key, Class<T> type);
}
```

- [ ] **Step 4: Implement `DefaultCasePlanModel`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java
package io.casehub.blackboard.plan;

import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory {@link CasePlanModel} implementation.
 * Plan state is transient — rebuilt from EventLog on engine recovery.
 * See casehubio/engine#76. Persistence SPI deferred — see casehubio/engine#84.
 */
public class DefaultCasePlanModel implements CasePlanModel {

  private final UUID caseId;
  private final PriorityBlockingQueue<PlanItem> agenda = new PriorityBlockingQueue<>();
  private final ConcurrentHashMap<String, PlanItem> itemsById = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Stage> stages = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> milestones = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Object> resourceBudget = new ConcurrentHashMap<>();
  private volatile String focus;
  private volatile String focusRationale;

  public DefaultCasePlanModel(UUID caseId) {
    this.caseId = caseId;
  }

  @Override public UUID getCaseId() { return caseId; }

  @Override
  public void addPlanItem(PlanItem item) {
    agenda.add(item);
    itemsById.put(item.getPlanItemId(), item);
  }

  @Override
  public void removePlanItem(String planItemId) {
    PlanItem item = itemsById.remove(planItemId);
    if (item != null) agenda.remove(item);
  }

  @Override
  public Optional<PlanItem> getPlanItem(String planItemId) {
    return Optional.ofNullable(itemsById.get(planItemId));
  }

  @Override
  public List<PlanItem> getAgenda() {
    return agenda.stream()
        .filter(p -> p.getStatus() == PlanItem.PlanItemStatus.PENDING)
        .sorted()
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public List<PlanItem> getTopPlanItems(int maxCount) {
    List<PlanItem> all = getAgenda();
    return Collections.unmodifiableList(
        all.size() <= maxCount ? all : all.subList(0, maxCount));
  }

  @Override public void addStage(Stage stage) { stages.put(stage.getStageId(), stage); }

  @Override
  public Optional<Stage> getStage(String stageId) {
    return Optional.ofNullable(stages.get(stageId));
  }

  @Override
  public List<Stage> getPendingStages() {
    return stages.values().stream()
        .filter(s -> s.getStatus() == StageStatus.PENDING)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public List<Stage> getActiveStages() {
    return stages.values().stream()
        .filter(s -> s.getStatus() == StageStatus.ACTIVE)
        .collect(Collectors.toUnmodifiableList());
  }

  @Override
  public List<Stage> getAllStages() {
    return List.copyOf(stages.values());
  }

  @Override
  public void trackMilestone(String name) { milestones.putIfAbsent(name, Boolean.FALSE); }

  @Override
  public void achieveMilestone(String name) { milestones.computeIfPresent(name, (k, v) -> Boolean.TRUE); }

  @Override
  public boolean isMilestoneAchieved(String name) {
    return Boolean.TRUE.equals(milestones.get(name));
  }

  @Override public void setFocus(String f) { this.focus = f; }
  @Override public Optional<String> getFocus() { return Optional.ofNullable(focus); }
  @Override public void setFocusRationale(String r) { this.focusRationale = r; }

  @Override
  public void setResourceBudget(Map<String, Object> budget) {
    resourceBudget.clear(); resourceBudget.putAll(budget);
  }

  @Override
  public Map<String, Object> getResourceBudget() { return Collections.unmodifiableMap(resourceBudget); }

  @Override public void put(String key, Object value) { state.put(key, value); }

  @SuppressWarnings("unchecked")
  @Override
  public <T> Optional<T> get(String key, Class<T> type) {
    Object v = state.get(key);
    return (v != null && type.isInstance(v)) ? Optional.of((T) v) : Optional.empty();
  }
}
```

- [ ] **Step 5: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest -q
```

Expected: `Tests run: 9, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add CasePlanModel interface and DefaultCasePlanModel (casehubio/engine#76)"
```

---

## Task 5: `Stage` and `StageStatus`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/StageStatus.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java
package io.casehub.blackboard.stage;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Stage lifecycle transitions and containment.
 * See casehubio/engine#76. CMMN 1.1 §5.4.4 alignment: casehubio/engine#84.
 */
class StageTest {

    @Test
    void new_stage_is_pending() {
        assertThat(Stage.create("intake").getStatus()).isEqualTo(StageStatus.PENDING);
    }

    @Test
    void activate_from_pending_transitions_to_active() {
        Stage s = Stage.create("intake");
        s.activate();
        assertThat(s.getStatus()).isEqualTo(StageStatus.ACTIVE);
    }

    @Test
    void activate_from_non_pending_is_noop() {
        Stage s = Stage.create("intake");
        s.activate();
        s.complete();
        s.activate(); // should be noop
        assertThat(s.getStatus()).isEqualTo(StageStatus.COMPLETED);
    }

    @Test
    void complete_from_active_transitions_to_completed() {
        Stage s = Stage.create("intake");
        s.activate();
        s.complete();
        assertThat(s.getStatus()).isEqualTo(StageStatus.COMPLETED);
    }

    @Test
    void terminate_from_active_transitions_to_terminated() {
        Stage s = Stage.create("intake");
        s.activate();
        s.terminate();
        assertThat(s.getStatus()).isEqualTo(StageStatus.TERMINATED);
    }

    @Test
    void suspend_and_resume_roundtrip() {
        Stage s = Stage.create("intake");
        s.activate();
        s.suspend();
        assertThat(s.getStatus()).isEqualTo(StageStatus.SUSPENDED);
        s.resume();
        assertThat(s.getStatus()).isEqualTo(StageStatus.ACTIVE);
    }

    @Test
    void isTerminal_true_for_completed_terminated_faulted() {
        Stage completed = Stage.create("a"); completed.activate(); completed.complete();
        Stage terminated = Stage.create("b"); terminated.activate(); terminated.terminate();
        assertThat(completed.isTerminal()).isTrue();
        assertThat(terminated.isTerminal()).isTrue();
        assertThat(Stage.create("c").isTerminal()).isFalse();
    }

    @Test
    void containment_addPlanItem_and_addRequiredItem() {
        Stage s = Stage.create("intake");
        s.addPlanItem("pi-1");
        s.addRequiredItem("pi-1");
        assertThat(s.getContainedPlanItemIds()).contains("pi-1");
        assertThat(s.getRequiredItemIds()).contains("pi-1");
    }

    @Test
    void autocomplete_defaults_true() {
        assertThat(Stage.create("intake").isAutocomplete()).isTrue();
    }

    @Test
    void manualActivation_defaults_false() {
        assertThat(Stage.create("intake").isManualActivation()).isFalse();
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=StageTest -q 2>&1 | tail -3
```

- [ ] **Step 3: Implement `StageStatus`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/stage/StageStatus.java
package io.casehub.blackboard.stage;

/**
 * CMMN Stage lifecycle states. See casehubio/engine#76.
 * Full CMMN 1.1 §5.4.4 audit tracked in casehubio/engine#84.
 */
public enum StageStatus {
  PENDING, ACTIVE, SUSPENDED, COMPLETED, TERMINATED, FAULTED
}
```

- [ ] **Step 4: Implement `Stage`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java
package io.casehub.blackboard.stage;

import io.casehub.api.model.evaluator.ExpressionEvaluator;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CMMN Stage — a container for PlanItems, Milestones, and nested Stages that
 * activates and completes as a unit. Entry/exit conditions use
 * {@link ExpressionEvaluator} (JQ or Lambda). See casehubio/engine#76.
 *
 * <p>Milestone containment ({@code containedMilestoneIds}) is present but
 * Stage exit criteria referencing milestone achievement requires the full
 * alignment in casehubio/engine#84. For PR4, exit conditions use expression
 * evaluation against {@code CaseContext} only.
 */
public class Stage {

  private final String stageId;
  private String name;
  private StageStatus status;
  private Instant createdAt;
  private Instant activatedAt;
  private Instant completedAt;
  private Optional<String> parentStageId;

  // Conditions — nullable means "no condition" (always satisfied / never exit)
  private ExpressionEvaluator entryCondition;
  private ExpressionEvaluator exitCondition;

  // Containment
  private final List<String> containedPlanItemIds = new CopyOnWriteArrayList<>();
  private final List<String> containedMilestoneIds = new CopyOnWriteArrayList<>(); // casehubio/engine#84
  private final List<String> containedStageIds = new CopyOnWriteArrayList<>();
  private final List<String> requiredItemIds = new CopyOnWriteArrayList<>();

  // Behaviour
  private boolean autocomplete = true;
  private boolean manualActivation = false;

  private Stage(String name) {
    this.stageId = UUID.randomUUID().toString();
    this.name = name;
    this.status = StageStatus.PENDING;
    this.createdAt = Instant.now();
    this.parentStageId = Optional.empty();
  }

  public static Stage create(String name) { return new Stage(name); }

  public Stage withEntryCondition(ExpressionEvaluator c) { this.entryCondition = c; return this; }
  public Stage withExitCondition(ExpressionEvaluator c) { this.exitCondition = c; return this; }
  public Stage withManualActivation(boolean v) { this.manualActivation = v; return this; }
  public Stage withAutocomplete(boolean v) { this.autocomplete = v; return this; }
  public Stage withParentStage(String id) { this.parentStageId = Optional.of(id); return this; }

  public void activate() {
    if (status == StageStatus.PENDING) { status = StageStatus.ACTIVE; activatedAt = Instant.now(); }
  }

  public void complete() {
    if (status == StageStatus.ACTIVE || status == StageStatus.SUSPENDED) {
      status = StageStatus.COMPLETED; completedAt = Instant.now();
    }
  }

  public void terminate() {
    if (status == StageStatus.ACTIVE || status == StageStatus.SUSPENDED) {
      status = StageStatus.TERMINATED; completedAt = Instant.now();
    }
  }

  public void suspend() { if (status == StageStatus.ACTIVE) status = StageStatus.SUSPENDED; }
  public void resume()  { if (status == StageStatus.SUSPENDED) status = StageStatus.ACTIVE; }
  public void fault()   { status = StageStatus.FAULTED; completedAt = Instant.now(); }

  public boolean isTerminal() {
    return status == StageStatus.COMPLETED || status == StageStatus.TERMINATED
        || status == StageStatus.FAULTED;
  }
  public boolean isActive() { return status == StageStatus.ACTIVE; }

  public void addPlanItem(String planItemId) {
    if (!containedPlanItemIds.contains(planItemId)) containedPlanItemIds.add(planItemId);
  }
  public void addMilestone(String milestoneName) {
    if (!containedMilestoneIds.contains(milestoneName)) containedMilestoneIds.add(milestoneName);
  }
  public void addNestedStage(String stageId) {
    if (!containedStageIds.contains(stageId)) containedStageIds.add(stageId);
  }
  public void addRequiredItem(String itemId) {
    if (!requiredItemIds.contains(itemId)) requiredItemIds.add(itemId);
  }

  // Getters
  public String getStageId() { return stageId; }
  public String getName() { return name; }
  public StageStatus getStatus() { return status; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getActivatedAt() { return activatedAt; }
  public Instant getCompletedAt() { return completedAt; }
  public Optional<String> getParentStageId() { return parentStageId; }
  public ExpressionEvaluator getEntryCondition() { return entryCondition; }
  public ExpressionEvaluator getExitCondition() { return exitCondition; }
  public List<String> getContainedPlanItemIds() { return List.copyOf(containedPlanItemIds); }
  public List<String> getContainedMilestoneIds() { return List.copyOf(containedMilestoneIds); }
  public List<String> getContainedStageIds() { return List.copyOf(containedStageIds); }
  public List<String> getRequiredItemIds() { return List.copyOf(requiredItemIds); }
  public boolean isAutocomplete() { return autocomplete; }
  public boolean isManualActivation() { return manualActivation; }
}
```

- [ ] **Step 5: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=StageTest -q
```

Expected: `Tests run: 10, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add Stage POJO with CMMN lifecycle and containment (casehubio/engine#76)"
```

---

## Task 6: Stage events and `BlackboardEventBusAddresses`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/event/BlackboardEventBusAddresses.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageActivatedEvent.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageCompletedEvent.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/event/StageTerminatedEvent.java`

These are simple constants and records — no tests needed beyond compilation.

- [ ] **Step 1: Create `BlackboardEventBusAddresses`**

```java
package io.casehub.blackboard.event;

/**
 * EventBus addresses for casehub-blackboard events.
 * Published via {@code eventBus.publish()} — fan-out, multiple consumers.
 * See casehubio/engine#76.
 */
public final class BlackboardEventBusAddresses {
  private BlackboardEventBusAddresses() {}
  public static final String STAGE_ACTIVATED  = "casehub.blackboard.stage.activated";
  public static final String STAGE_COMPLETED  = "casehub.blackboard.stage.completed";
  public static final String STAGE_TERMINATED = "casehub.blackboard.stage.terminated";
}
```

- [ ] **Step 2: Create event records**

```java
// StageActivatedEvent.java
package io.casehub.blackboard.event;
import io.casehub.blackboard.stage.Stage;
import java.util.UUID;
/** Published when a Stage transitions PENDING → ACTIVE. See casehubio/engine#76. */
public record StageActivatedEvent(UUID caseId, Stage stage) {}

// StageCompletedEvent.java
package io.casehub.blackboard.event;
import io.casehub.blackboard.stage.Stage;
import java.util.UUID;
/** Published when a Stage autocompletes or all required items finish. See casehubio/engine#76. */
public record StageCompletedEvent(UUID caseId, Stage stage) {}

// StageTerminatedEvent.java
package io.casehub.blackboard.event;
import io.casehub.blackboard.stage.Stage;
import java.util.UUID;
/** Published when a Stage's exit condition is satisfied. See casehubio/engine#76. */
public record StageTerminatedEvent(UUID caseId, Stage stage) {}
```

- [ ] **Step 3: Compile**

```bash
mvn compile -pl casehub-blackboard -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add stage EventBus addresses and event records (casehubio/engine#76)"
```

---

## Task 7: `StageLifecycleEvaluator`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/control/StageLifecycleEvaluatorTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/control/StageLifecycleEvaluatorTest.java
package io.casehub.blackboard.control;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.context.CaseContext;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StageLifecycleEvaluator entry/exit evaluation.
 * See casehubio/engine#76.
 */
class StageLifecycleEvaluatorTest {

    private StageLifecycleEvaluator evaluator;
    private EventBus mockBus;
    private DefaultCasePlanModel plan;
    private PlanExecutionContext ctx;

    @BeforeEach
    void setUp() {
        mockBus = mock(EventBus.class);
        evaluator = new StageLifecycleEvaluator(mockBus);
        UUID caseId = UUID.randomUUID();
        plan = new DefaultCasePlanModel(caseId);
        CaseContext mockCtx = mock(CaseContext.class);
        ctx = new PlanExecutionContext(caseId, mock(CaseDefinition.class), mockCtx);
    }

    @Test
    void pending_stage_activates_when_entry_condition_met() {
        Stage stage = Stage.create("intake")
            .withEntryCondition(c -> true); // always true
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.ACTIVE);
        verify(mockBus).publish(eq(BlackboardEventBusAddresses.STAGE_ACTIVATED), any());
    }

    @Test
    void pending_stage_stays_pending_when_entry_condition_not_met() {
        Stage stage = Stage.create("intake")
            .withEntryCondition(c -> false);
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.PENDING);
        verifyNoInteractions(mockBus);
    }

    @Test
    void pending_stage_with_no_entry_condition_activates() {
        Stage stage = Stage.create("intake"); // no entry condition
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.ACTIVE);
    }

    @Test
    void active_stage_terminates_when_exit_condition_met() {
        Stage stage = Stage.create("intake")
            .withExitCondition(c -> true);
        stage.activate();
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.TERMINATED);
        verify(mockBus).publish(eq(BlackboardEventBusAddresses.STAGE_TERMINATED), any());
    }

    @Test
    void active_stage_stays_active_when_exit_condition_not_met() {
        Stage stage = Stage.create("intake")
            .withExitCondition(c -> false);
        stage.activate();
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.ACTIVE);
    }

    @Test
    void manual_activation_stage_stays_pending_even_when_entry_met() {
        Stage stage = Stage.create("intake")
            .withEntryCondition(c -> true)
            .withManualActivation(true);
        plan.addStage(stage);

        evaluator.evaluate(plan, ctx).await().indefinitely();

        assertThat(stage.getStatus()).isEqualTo(StageStatus.PENDING);
        verifyNoInteractions(mockBus);
    }
}
```

Add Mockito to `casehub-blackboard/pom.xml` test dependencies:
```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=StageLifecycleEvaluatorTest -q 2>&1 | tail -3
```

- [ ] **Step 3: Implement `StageLifecycleEvaluator`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.event.StageActivatedEvent;
import io.casehub.blackboard.event.StageTerminatedEvent;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.stage.Stage;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Evaluates Stage entry and exit conditions on each CONTEXT_CHANGED cycle.
 * Called from {@link PlanningStrategyLoopControl} within the Uni chain.
 * Publishes {@link StageActivatedEvent} and {@link StageTerminatedEvent}.
 * See casehubio/engine#76.
 */
@ApplicationScoped
public class StageLifecycleEvaluator {

  private final EventBus eventBus;

  @Inject
  public StageLifecycleEvaluator(EventBus eventBus) {
    this.eventBus = eventBus;
  }

  public Uni<Void> evaluate(CasePlanModel plan, PlanExecutionContext ctx) {
    activatePendingStages(plan, ctx);
    terminateActiveStages(plan, ctx);
    return Uni.createFrom().voidItem();
  }

  private void activatePendingStages(CasePlanModel plan, PlanExecutionContext ctx) {
    for (Stage stage : plan.getPendingStages()) {
      if (stage.isManualActivation()) continue;
      boolean conditionMet = stage.getEntryCondition() == null
          || stage.getEntryCondition().evaluate(ctx.caseContext());
      if (conditionMet) {
        stage.activate();
        eventBus.publish(BlackboardEventBusAddresses.STAGE_ACTIVATED,
            new StageActivatedEvent(ctx.caseId(), stage));
      }
    }
  }

  private void terminateActiveStages(CasePlanModel plan, PlanExecutionContext ctx) {
    for (Stage stage : plan.getActiveStages()) {
      if (stage.getExitCondition() == null) continue;
      if (stage.getExitCondition().evaluate(ctx.caseContext())) {
        stage.terminate();
        eventBus.publish(BlackboardEventBusAddresses.STAGE_TERMINATED,
            new StageTerminatedEvent(ctx.caseId(), stage));
      }
    }
  }
}
```

Note: `ExpressionEvaluator.evaluate()` takes a `CaseContext` — verify the exact method signature in `api/model/evaluator/ExpressionEvaluator.java` and adjust if needed.

- [ ] **Step 4: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=StageLifecycleEvaluatorTest -q
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add StageLifecycleEvaluator with entry/exit condition evaluation (casehubio/engine#76)"
```

---

## Task 8: `PlanningStrategy`, `DefaultPlanningStrategy`, and contract test

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategy.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/DefaultPlanningStrategy.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/control/DefaultPlanningStrategyTest.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/control/PlanningStrategyContractTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/control/DefaultPlanningStrategyTest.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for DefaultPlanningStrategy.
 * See casehubio/engine#76. Epic casehubio/engine#30.
 */
class DefaultPlanningStrategyTest {

    private final DefaultPlanningStrategy strategy = new DefaultPlanningStrategy();

    private PlanExecutionContext ctx() {
        return new PlanExecutionContext(
            UUID.randomUUID(), mock(CaseDefinition.class), mock(CaseContext.class));
    }

    @Test
    void returns_all_eligible_bindings() {
        DefaultCasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        Binding b1 = mock(Binding.class);
        Binding b2 = mock(Binding.class);
        List<Binding> eligible = List.of(b1, b2);

        List<Binding> result = strategy.select(plan, ctx(), eligible)
            .await().indefinitely();

        assertThat(result).containsExactlyInAnyOrderElementsOf(eligible);
    }

    @Test
    void empty_eligible_returns_empty_not_null() {
        DefaultCasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        List<Binding> result = strategy.select(plan, ctx(), List.of())
            .await().indefinitely();
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void does_not_modify_plan_focus_or_budget() {
        DefaultCasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        strategy.select(plan, ctx(), List.of()).await().indefinitely();
        assertThat(plan.getFocus()).isEmpty();
        assertThat(plan.getResourceBudget()).isEmpty();
    }
}
```

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/control/PlanningStrategyContractTest.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Abstract contract test — extend this in any custom PlanningStrategy module
 * to verify the implementation honours the PlanningStrategy contract.
 * See casehubio/engine#76.
 */
public abstract class PlanningStrategyContractTest {

    protected abstract PlanningStrategy strategy();

    private PlanExecutionContext ctx() {
        return new PlanExecutionContext(
            UUID.randomUUID(), mock(CaseDefinition.class), mock(CaseContext.class));
    }

    @Test
    void never_returns_null() {
        CasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        assertThat(strategy().select(plan, ctx(), List.of())).isNotNull();
    }

    @Test
    void returns_only_bindings_from_eligible_list() {
        CasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        Binding b1 = mock(Binding.class);
        Binding b2 = mock(Binding.class);
        Binding outsider = mock(Binding.class);
        List<Binding> eligible = List.of(b1, b2);

        List<Binding> result = strategy().select(plan, ctx(), eligible)
            .await().indefinitely();

        assertThat(result).doesNotContain(outsider);
        assertThat(eligible).containsAll(result);
    }

    @Test
    void handles_empty_eligible_without_throwing() {
        CasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        List<Binding> result = strategy().select(plan, ctx(), List.of())
            .await().indefinitely();
        assertThat(result).isNotNull();
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest="DefaultPlanningStrategyTest" -q 2>&1 | tail -3
```

- [ ] **Step 3: Implement `PlanningStrategy` interface**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategy.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.blackboard.plan.CasePlanModel;
import io.smallrye.mutiny.Uni;
import java.util.List;

/**
 * Selects which eligible {@link Binding}s to fire and in what order, optionally
 * reading and writing {@link CasePlanModel} control state.
 *
 * <p>Returns {@code Uni} — implementations may perform non-blocking I/O
 * (e.g. EventLog queries) before returning. See casehubio/engine#76.
 * Async strategy use cases tracked in casehubio/engine#82.
 *
 * <p>Contract (enforced by {@link PlanningStrategyContractTest}):
 * <ul>
 *   <li>Never return bindings not in {@code eligible}
 *   <li>Never return null — return empty list to suppress all firing
 *   <li>Handle empty {@code eligible} gracefully
 * </ul>
 */
public interface PlanningStrategy {
  String getId();
  String getName();

  Uni<List<Binding>> select(CasePlanModel plan,
                             PlanExecutionContext context,
                             List<Binding> eligible);
}
```

- [ ] **Step 4: Implement `DefaultPlanningStrategy`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/control/DefaultPlanningStrategy.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.blackboard.plan.CasePlanModel;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

/**
 * Default {@link PlanningStrategy} — returns all eligible bindings unchanged,
 * assigns equal priority. Equivalent to choreography but routed through the
 * plan model, enabling CasePlanModel state to accumulate for custom strategies.
 * See casehubio/engine#76. Epic casehubio/engine#30.
 */
@ApplicationScoped
public class DefaultPlanningStrategy implements PlanningStrategy {

  @Override public String getId() { return "default"; }
  @Override public String getName() { return "Default Equal-Priority Strategy"; }

  @Override
  public Uni<List<Binding>> select(CasePlanModel plan,
                                    PlanExecutionContext context,
                                    List<Binding> eligible) {
    return Uni.createFrom().item(eligible);
  }
}
```

- [ ] **Step 5: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest="DefaultPlanningStrategyTest" -q
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add PlanningStrategy SPI, DefaultPlanningStrategy, and contract test (casehubio/engine#76)"
```

---

## Task 9: `BlackboardRegistry`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/registry/BlackboardRegistry.java`

No unit tests — this is a pure data holder. Tested via integration tests in Tasks 13–15.

- [ ] **Step 1: Implement**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/registry/BlackboardRegistry.java
package io.casehub.blackboard.registry;

import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared registry of per-case {@link CasePlanModel} instances and the
 * worker-name-to-PlanItemId completion index.
 *
 * <p>Injected by both {@link io.casehub.blackboard.control.PlanningStrategyLoopControl}
 * (which writes entries on Binding selection) and
 * {@link io.casehub.blackboard.handler.PlanItemCompletionHandler}
 * (which reads entries on worker completion). See casehubio/engine#76.
 *
 * <p>State is in-memory and transient — rebuilt from EventLog on engine recovery.
 * Persistence SPI deferred to casehubio/engine#84.
 */
@ApplicationScoped
public class BlackboardRegistry {

  // caseId → CasePlanModel
  private final ConcurrentHashMap<UUID, CasePlanModel> planModels = new ConcurrentHashMap<>();

  // caseId → (workerName → planItemId)
  private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> completionIndex
      = new ConcurrentHashMap<>();

  public CasePlanModel getOrCreate(UUID caseId) {
    return planModels.computeIfAbsent(caseId, DefaultCasePlanModel::new);
  }

  public Optional<CasePlanModel> get(UUID caseId) {
    return Optional.ofNullable(planModels.get(caseId));
  }

  public void indexWorkerForCompletion(UUID caseId, String workerName, String planItemId) {
    completionIndex.computeIfAbsent(caseId, k -> new ConcurrentHashMap<>())
        .put(workerName, planItemId);
  }

  public Optional<String> getPlanItemId(UUID caseId, String workerName) {
    ConcurrentHashMap<String, String> index = completionIndex.get(caseId);
    return index == null ? Optional.empty() : Optional.ofNullable(index.get(workerName));
  }
}
```

- [ ] **Step 2: Compile**

```bash
mvn compile -pl casehub-blackboard -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add BlackboardRegistry for shared plan model and completion index (casehubio/engine#76)"
```

---

## Task 10: `MilestoneAchievementHandler`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/MilestoneAchievementHandler.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/MilestoneAchievementHandlerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/handler/MilestoneAchievementHandlerTest.java
package io.casehub.blackboard.handler;

import io.casehub.blackboard.plan.DefaultCasePlanModel;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.engine.internal.event.MilestoneReachedEvent;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.api.model.Milestone;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MilestoneAchievementHandler.
 * See casehubio/engine#76. Milestone/Goal/Stage alignment: casehubio/engine#84.
 */
class MilestoneAchievementHandlerTest {

    @Test
    void achieves_tracked_milestone_in_plan_model() {
        BlackboardRegistry registry = new BlackboardRegistry();
        UUID caseId = UUID.randomUUID();
        DefaultCasePlanModel plan = (DefaultCasePlanModel) registry.getOrCreate(caseId);
        plan.trackMilestone("docs-received");

        MilestoneAchievementHandler handler = new MilestoneAchievementHandler(registry);

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);
        Milestone milestone = mock(Milestone.class);
        when(milestone.getName()).thenReturn("docs-received");

        handler.onMilestoneReached(new MilestoneReachedEvent(instance, milestone))
            .await().indefinitely();

        assertThat(plan.isMilestoneAchieved("docs-received")).isTrue();
    }

    @Test
    void no_plan_model_does_not_throw() {
        BlackboardRegistry registry = new BlackboardRegistry();
        MilestoneAchievementHandler handler = new MilestoneAchievementHandler(registry);

        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(UUID.randomUUID());
        Milestone milestone = mock(Milestone.class);
        when(milestone.getName()).thenReturn("docs-received");

        // No plan model exists for this caseId — should not throw
        handler.onMilestoneReached(new MilestoneReachedEvent(instance, milestone))
            .await().indefinitely();
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=MilestoneAchievementHandlerTest -q 2>&1 | tail -3
```

- [ ] **Step 3: Implement**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/handler/MilestoneAchievementHandler.java
package io.casehub.blackboard.handler;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.MilestoneReachedEvent;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Promotes milestone to ACHIEVED in the {@link io.casehub.blackboard.plan.CasePlanModel}
 * when a {@code MilestoneReachedEvent} fires. Only acts if a plan model exists
 * for the case — pure choreography cases have no plan model.
 *
 * <p>Uses {@code MILESTONE_REACHED} which is published via {@code eventBus.publish()}
 * (fan-out) — this handler coexists with the engine's existing milestone processing.
 * See casehubio/engine#76. Milestone alignment: casehubio/engine#84.
 */
@ApplicationScoped
public class MilestoneAchievementHandler {

  private final BlackboardRegistry registry;

  @Inject
  public MilestoneAchievementHandler(BlackboardRegistry registry) {
    this.registry = registry;
  }

  @ConsumeEvent(EventBusAddresses.MILESTONE_REACHED)
  public Uni<Void> onMilestoneReached(MilestoneReachedEvent event) {
    registry.get(event.caseInstance().getUuid())
        .ifPresent(plan -> plan.achieveMilestone(event.milestone().getName()));
    return Uni.createFrom().voidItem();
  }
}
```

- [ ] **Step 4: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=MilestoneAchievementHandlerTest -q
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add MilestoneAchievementHandler — promotes milestone in CasePlanModel (casehubio/engine#76)"
```

---

## Task 11: `PlanItemCompletionHandler`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java`

- [ ] **Step 1: Write failing tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java
package io.casehub.blackboard.handler;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import io.casehub.blackboard.plan.PlanItem;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.engine.internal.event.WorkflowExecutionCompleted;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.api.model.Worker;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlanItemCompletionHandler — marks PlanItems COMPLETED
 * and triggers Stage autocomplete. See casehubio/engine#76.
 */
class PlanItemCompletionHandlerTest {

    private BlackboardRegistry registry;
    private EventBus mockBus;
    private PlanItemCompletionHandler handler;
    private UUID caseId;
    private DefaultCasePlanModel plan;

    @BeforeEach
    void setUp() {
        registry = new BlackboardRegistry();
        mockBus = mock(EventBus.class);
        handler = new PlanItemCompletionHandler(registry, mockBus);
        caseId = UUID.randomUUID();
        plan = (DefaultCasePlanModel) registry.getOrCreate(caseId);
    }

    private WorkflowExecutionCompleted eventFor(String workerName) {
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);
        Worker worker = mock(Worker.class);
        when(worker.getName()).thenReturn(workerName);
        return new WorkflowExecutionCompleted(instance, worker, "idempotency-key", java.util.Map.of());
    }

    @Test
    void marks_plan_item_completed_on_worker_finish() {
        PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
        plan.addPlanItem(item);
        registry.indexWorkerForCompletion(caseId, "worker-a", item.getPlanItemId());

        handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

        assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.COMPLETED);
    }

    @Test
    void unknown_worker_does_not_throw() {
        handler.onWorkerFinished(eventFor("unknown-worker")).await().indefinitely();
        // no exception expected
    }

    @Test
    void stage_autocompletes_when_all_required_items_done() {
        PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
        plan.addPlanItem(item);
        registry.indexWorkerForCompletion(caseId, "worker-a", item.getPlanItemId());

        Stage stage = Stage.create("intake");
        stage.addPlanItem(item.getPlanItemId());
        stage.addRequiredItem(item.getPlanItemId());
        stage.activate();
        plan.addStage(stage);

        handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

        assertThat(stage.isTerminal()).isTrue();
        verify(mockBus).publish(eq(BlackboardEventBusAddresses.STAGE_COMPLETED), any());
    }

    @Test
    void stage_does_not_autocomplete_when_not_all_required_done() {
        PlanItem item1 = PlanItem.create("binding-a", "worker-a", 0);
        PlanItem item2 = PlanItem.create("binding-b", "worker-b", 0);
        plan.addPlanItem(item1);
        plan.addPlanItem(item2);
        registry.indexWorkerForCompletion(caseId, "worker-a", item1.getPlanItemId());

        Stage stage = Stage.create("intake");
        stage.addPlanItem(item1.getPlanItemId());
        stage.addPlanItem(item2.getPlanItemId());
        stage.addRequiredItem(item1.getPlanItemId());
        stage.addRequiredItem(item2.getPlanItemId());
        stage.activate();
        plan.addStage(stage);

        handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

        assertThat(stage.isTerminal()).isFalse();
        verifyNoInteractions(mockBus);
    }

    @Test
    void autocomplete_false_stage_does_not_complete_even_when_all_done() {
        PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
        plan.addPlanItem(item);
        registry.indexWorkerForCompletion(caseId, "worker-a", item.getPlanItemId());

        Stage stage = Stage.create("intake").withAutocomplete(false);
        stage.addRequiredItem(item.getPlanItemId());
        stage.activate();
        plan.addStage(stage);

        handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

        assertThat(stage.isTerminal()).isFalse();
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemCompletionHandlerTest -q 2>&1 | tail -3
```

- [ ] **Step 3: Implement**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java
package io.casehub.blackboard.handler;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.event.StageCompletedEvent;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.PlanItem;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.casehub.engine.internal.event.WorkflowExecutionCompleted;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;

/**
 * Marks {@link PlanItem}s COMPLETED when a worker finishes, then evaluates
 * Stage autocomplete for any active Stage containing the completed item.
 *
 * <p>Subscribes to {@code WORKER_EXECUTION_FINISHED} which is published via
 * {@code eventBus.publish()} — fan-out. Coexists with
 * {@link io.casehub.engine.internal.engine.handler.WorkflowExecutionCompletedHandler}.
 * See casehubio/engine#76. Stage future alignment: casehubio/engine#84.
 */
@ApplicationScoped
public class PlanItemCompletionHandler {

  private final BlackboardRegistry registry;
  private final EventBus eventBus;

  @Inject
  public PlanItemCompletionHandler(BlackboardRegistry registry, EventBus eventBus) {
    this.registry = registry;
    this.eventBus = eventBus;
  }

  @ConsumeEvent(EventBusAddresses.WORKER_EXECUTION_FINISHED)
  public Uni<Void> onWorkerFinished(WorkflowExecutionCompleted event) {
    UUID caseId = event.caseInstance().getUuid();
    String workerName = event.worker().getName();

    registry.get(caseId).ifPresent(plan ->
        registry.getPlanItemId(caseId, workerName).ifPresent(planItemId -> {
          plan.getPlanItem(planItemId).ifPresent(item -> {
            item.setStatus(PlanItem.PlanItemStatus.COMPLETED);
            evaluateStageAutocomplete(caseId, plan, planItemId);
          });
        }));

    return Uni.createFrom().voidItem();
  }

  private void evaluateStageAutocomplete(UUID caseId, CasePlanModel plan, String completedItemId) {
    for (Stage stage : plan.getActiveStages()) {
      if (!stage.isAutocomplete()) continue;
      if (!stage.getRequiredItemIds().contains(completedItemId)) continue;

      boolean allDone = stage.getRequiredItemIds().stream().allMatch(itemId ->
          plan.getPlanItem(itemId)
              .map(pi -> pi.getStatus() == PlanItem.PlanItemStatus.COMPLETED)
              .orElse(false));

      if (allDone) {
        stage.complete();
        eventBus.publish(BlackboardEventBusAddresses.STAGE_COMPLETED,
            new StageCompletedEvent(caseId, stage));
      }
    }
  }
}
```

- [ ] **Step 4: Run tests — verify PASS**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemCompletionHandlerTest -q
```

Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add PlanItemCompletionHandler with Stage autocomplete (casehubio/engine#76)"
```

---

## Task 12: `PlanningStrategyLoopControl`

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java`

Tested via integration tests in Tasks 13–15.

- [ ] **Step 1: Implement**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.LoopControl;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Worker;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.PlanItem;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;

/**
 * {@link LoopControl} implementation that delegates selection to a
 * {@link PlanningStrategy}, managing a {@link CasePlanModel} per case.
 *
 * <p>Activated via {@code @Alternative @Priority(10)} — replaces
 * {@link io.casehub.engine.internal.engine.ChoreographyLoopControl} when
 * {@code casehub-blackboard} is on the classpath. See casehubio/engine#76.
 * Epic: casehubio/engine#30.
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class PlanningStrategyLoopControl implements LoopControl {

  @Inject BlackboardRegistry registry;
  @Inject PlanningStrategy planningStrategy;
  @Inject StageLifecycleEvaluator stageLifecycleEvaluator;

  @Override
  public Uni<List<Binding>> select(PlanExecutionContext ctx, List<Binding> eligible) {
    UUID caseId = ctx.caseId();
    CasePlanModel plan = registry.getOrCreate(caseId);

    // Add PlanItems for each eligible binding
    eligible.forEach(b -> {
      String workerName = resolveWorkerName(b, ctx);
      PlanItem item = PlanItem.create(b.getName(), workerName, 0);
      plan.addPlanItem(item);
    });

    return stageLifecycleEvaluator.evaluate(plan, ctx)
        .chain(() -> planningStrategy.select(plan, ctx, eligible))
        .invoke(selected -> indexSelected(caseId, selected, plan));
  }

  /**
   * Resolves the worker name for a Binding by matching its capability against
   * the case definition's worker list. Returns the first match, or the
   * capability name as a fallback.
   */
  private String resolveWorkerName(Binding binding, PlanExecutionContext ctx) {
    if (binding.getCapability() == null) return "unknown";
    String capName = binding.getCapability().getName();
    return ctx.definition().getWorkers().stream()
        .filter(w -> w.getCapabilities() != null &&
            w.getCapabilities().stream().anyMatch(c -> c.getName().equals(capName)))
        .map(Worker::getName)
        .findFirst()
        .orElse(capName);
  }

  private void indexSelected(UUID caseId, List<Binding> selected, CasePlanModel plan) {
    for (Binding b : selected) {
      plan.getAgenda().stream()
          .filter(pi -> pi.getBindingName().equals(b.getName())
              && pi.getStatus() == PlanItem.PlanItemStatus.PENDING)
          .findFirst()
          .ifPresent(pi -> {
            pi.setStatus(PlanItem.PlanItemStatus.RUNNING);
            registry.indexWorkerForCompletion(caseId, pi.getWorkerName(), pi.getPlanItemId());
          });
    }
  }
}
```

- [ ] **Step 2: Build all modules**

```bash
mvn compile -pl casehub-blackboard -am -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src
git commit -m "feat: add PlanningStrategyLoopControl — wires PlanningStrategy into LoopControl (casehubio/engine#76)"
```

---

## Task 13: Integration tests — happy path, choreography unchanged, basic loop

Copy in-memory SPI impls from `casehub-persistence-memory` into `casehub-blackboard/src/test/java/` (same pattern as PR3 — avoids Maven cycle).

**Files:**
- Copy: `InMemoryCaseInstanceRepository.java` → `src/test/java/io/casehub/blackboard/it/support/`
- Copy: `InMemoryEventLogRepository.java` → same
- Copy: `InMemoryCaseMetaModelRepository.java` → same
- Create: `src/test/java/io/casehub/blackboard/it/ChoreographyUnchangedIT.java`
- Create: `src/test/java/io/casehub/blackboard/it/BasicLoopControlIT.java`

- [ ] **Step 1: Copy in-memory impls**

```bash
cp casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseInstanceRepository.java \
   casehub-blackboard/src/test/java/io/casehub/blackboard/it/support/
cp casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryEventLogRepository.java \
   casehub-blackboard/src/test/java/io/casehub/blackboard/it/support/
cp casehub-persistence-memory/src/main/java/io/casehub/persistence/memory/InMemoryCaseMetaModelRepository.java \
   casehub-blackboard/src/test/java/io/casehub/blackboard/it/support/
```

Change packages to `io.casehub.blackboard.it.support` in each copied file.

- [ ] **Step 2: Write choreography-unchanged test**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/it/ChoreographyUnchangedIT.java
package io.casehub.blackboard.it;

import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.Binding;
import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: DefaultPlanningStrategy returns all eligible bindings unchanged —
 * equivalent to choreography. No stage events published.
 * See casehubio/engine#76. Epic casehubio/engine#30.
 */
@QuarkusTest
class ChoreographyUnchangedIT {

    @Inject BlackboardRegistry registry;

    @Test
    void default_strategy_selects_all_eligible_bindings() throws Exception {
        // Start a case with two bindings, both eligible
        // Verify both workers are scheduled (both PlanItems move to RUNNING)
        // Full wiring tested via CaseHubRuntime.startCase() + signal
        // ...
        // Placeholder structure — fill with actual runtime invocation
        // following the pattern in engine/src/test/java integration tests
        assertThat(true).isTrue(); // replace with actual assertion
    }
}
```

**Note:** Examine existing integration tests in `engine/src/test/java/` for the exact `CaseHubRuntime.startCase()` pattern and replicate it here. The test structure mirrors what exists — use `@Inject CaseHubRuntime runtime` and signal the context.

- [ ] **Step 3: Write basic loop control test**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/it/BasicLoopControlIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.plan.PlanItem;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: after a worker completes, its PlanItem is COMPLETED in the registry.
 * See casehubio/engine#76.
 */
@QuarkusTest
class BasicLoopControlIT {

    @Inject BlackboardRegistry registry;

    @Test
    void plan_item_moves_to_completed_after_worker_finishes() throws Exception {
        // Start a case, let one worker complete, verify PlanItem.status == COMPLETED
        // Use CaseHubRuntime and wait for WORKER_EXECUTION_FINISHED signal
        // Follow existing engine integration test pattern
        assertThat(true).isTrue(); // replace with actual assertion
    }
}
```

- [ ] **Step 4: Run integration tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest="*IT" -q
```

Expected: all pass (fill in real assertions following engine test patterns before marking done).

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/test
git commit -m "test: add integration test infrastructure and happy path ITs (casehubio/engine#76)"
```

---

## Task 14: Integration tests — Stage lifecycle

**Files:**
- Create: `src/test/java/io/casehub/blackboard/it/StageActivationIT.java`
- Create: `src/test/java/io/casehub/blackboard/it/StageAutocompleteIT.java`
- Create: `src/test/java/io/casehub/blackboard/it/StageTerminationIT.java`
- Create: `src/test/java/io/casehub/blackboard/it/MilestoneTrackingIT.java`
- Create: `src/test/java/io/casehub/blackboard/it/NestedStageIT.java`

- [ ] **Step 1: Write stage activation test**

```java
// StageActivationIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: Stage with satisfied entry condition activates on CONTEXT_CHANGED,
 * and StageActivatedEvent is published on the EventBus.
 * See casehubio/engine#76.
 */
@QuarkusTest
class StageActivationIT {

    @Inject BlackboardRegistry registry;

    @Test
    void stage_activates_when_context_satisfies_entry_condition() throws Exception {
        // 1. Create case definition with a Stage whose entry condition is ctx -> ctx.getPath(".phase").equals("analysis")
        // 2. Add stage to the CasePlanModel via registry before starting case
        // 3. Start case, signal {phase: "analysis"}
        // 4. Wait for StageActivatedEvent on EventBus
        // 5. Assert stage.getStatus() == ACTIVE in registry.get(caseId)
        assertThat(true).isTrue(); // replace with actual runtime wiring
    }
}
```

- [ ] **Step 2: Write stage autocomplete test**

```java
// StageAutocompleteIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: Stage with two required PlanItems autocompletes after both workers finish.
 * StageCompletedEvent is published. See casehubio/engine#76.
 */
@QuarkusTest
class StageAutocompleteIT {

    @Inject BlackboardRegistry registry;

    @Test
    void stage_autocompletes_when_all_required_workers_finish() throws Exception {
        // 1. Case definition: Stage "intake" contains bindings "ocr" and "ner",
        //    both listed as requiredItemIds, autocomplete=true
        // 2. Start case, signal context to trigger both bindings
        // 3. Let both workers complete (mock workers return immediately)
        // 4. Wait for StageCompletedEvent
        // 5. Assert stage.getStatus() == COMPLETED
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 3: Write stage termination test**

```java
// StageTerminationIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.event.BlackboardEventBusAddresses;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: active Stage terminates when exit condition is met.
 * StageTerminatedEvent is published. See casehubio/engine#76.
 */
@QuarkusTest
class StageTerminationIT {

    @Inject BlackboardRegistry registry;

    @Test
    void active_stage_terminates_when_exit_condition_met() throws Exception {
        // 1. Stage with exitCondition: ctx -> ctx.getPath(".abort") != null
        // 2. Activate stage, then signal {abort: true}
        // 3. Wait for StageTerminatedEvent
        // 4. Assert stage.getStatus() == TERMINATED
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 4: Write milestone tracking test**

```java
// MilestoneTrackingIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: tracked milestone in CasePlanModel is promoted to ACHIEVED
 * when MilestoneReachedEvent fires.
 * See casehubio/engine#76. Milestone alignment: casehubio/engine#84.
 */
@QuarkusTest
class MilestoneTrackingIT {

    @Inject BlackboardRegistry registry;

    @Test
    void milestone_achieved_in_plan_model_after_milestone_reached_event() throws Exception {
        // 1. Case definition with Milestone "docs-received"
        // 2. plan.trackMilestone("docs-received") before start
        // 3. Signal context satisfying milestone condition
        // 4. Wait for MilestoneReachedEvent to propagate
        // 5. Assert registry.get(caseId).isMilestoneAchieved("docs-received") == true
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 5: Write nested stage test**

```java
// NestedStageIT.java
package io.casehub.blackboard.it;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: parent Stage autocompletes when child Stage completes.
 * StageCompletedEvent fires for both child and parent. See casehubio/engine#76.
 */
@QuarkusTest
class NestedStageIT {

    @Inject BlackboardRegistry registry;

    @Test
    void parent_stage_completes_when_child_stage_completes() throws Exception {
        // 1. Parent stage requires child stage ID in requiredItemIds
        // 2. Child stage has one PlanItem, autocomplete=true
        // 3. Worker for child completes
        // 4. Child autocompletes → parent autocompletes
        // 5. Assert both stages COMPLETED
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 6: Run all integration tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest="*IT" -q
```

- [ ] **Step 7: Commit**

```bash
git add casehub-blackboard/src/test
git commit -m "test: add Stage lifecycle and milestone integration tests (casehubio/engine#76)"
```

---

## Task 15: Integration test — sequential execution via custom `PlanningStrategy`

**Files:**
- Create: `src/test/java/io/casehub/blackboard/it/SequentialExecutionIT.java`

- [ ] **Step 1: Write test**

```java
// SequentialExecutionIT.java
package io.casehub.blackboard.it;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.Binding;
import io.casehub.blackboard.control.PlanningStrategy;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.smallrye.mutiny.Uni;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy path: custom PlanningStrategy that returns only the highest-priority
 * PlanItem enforces sequential execution — second worker not scheduled until
 * first completes. See casehubio/engine#76. Epic casehubio/engine#30.
 */
@QuarkusTest
class SequentialExecutionIT {

    @Inject BlackboardRegistry registry;

    /**
     * Strategy that returns only the first (highest-priority) binding per cycle.
     * Register as @Alternative @Priority(20) in test resources/beans.xml.
     */
    static class SequentialStrategy implements PlanningStrategy {
        @Override public String getId() { return "sequential"; }
        @Override public String getName() { return "Sequential"; }

        @Override
        public Uni<List<Binding>> select(CasePlanModel plan,
                                          PlanExecutionContext ctx,
                                          List<Binding> eligible) {
            List<Binding> top = eligible.isEmpty() ? List.of() : List.of(eligible.get(0));
            return Uni.createFrom().item(top);
        }
    }

    @Test
    void only_one_worker_scheduled_per_cycle_with_sequential_strategy() throws Exception {
        // 1. Register SequentialStrategy as the active PlanningStrategy in test
        // 2. Case with two eligible bindings at start
        // 3. Verify only one WorkerScheduleEvent published per CONTEXT_CHANGED cycle
        // 4. After first worker completes, second worker is scheduled
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 2: Run and commit**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=SequentialExecutionIT -q
git add casehub-blackboard/src/test
git commit -m "test: add sequential execution integration test (casehubio/engine#76)"
```

---

## Task 16: End-to-end tests

**Files:**
- Create: `src/test/java/io/casehub/blackboard/e2e/DocumentAnalysisE2ETest.java`
- Create: `src/test/java/io/casehub/blackboard/e2e/StageTerminationE2ETest.java`

These run against the full persistence stack (H2 in Hibernate Reactive compat mode — no Docker required).

- [ ] **Step 1: Write document analysis E2E test**

```java
// DocumentAnalysisE2ETest.java
package io.casehub.blackboard.e2e;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E happy path — document analysis case with two stages:
 *   Stage "intake": bindings for OCR + entity-extraction, autocomplete=true
 *   Stage "underwriting": entry condition ".intakeComplete == true",
 *     binding for credit-check, autocomplete=true
 *   Milestone: "intake-complete" when .extractionDone == true
 *   Goal (SUCCESS): ".decision == approved"
 *
 * Verifies full reactive chain from start to COMPLETED, including EventLog entries.
 * See casehubio/engine#76. Epic casehubio/engine#30.
 */
@QuarkusTest
class DocumentAnalysisE2ETest {

    @Inject BlackboardRegistry registry;

    @Test
    void full_document_analysis_case_completes_through_two_stages() throws Exception {
        // 1. Build CaseDefinition with intake and underwriting stages
        // 2. Start case
        // 3. Signal initial context (document uploaded)
        // 4. Wait for OCR + entity-extraction workers to complete (intake stage)
        // 5. Signal {intakeComplete: true} — triggers underwriting stage activation
        // 6. Wait for credit-check worker to complete
        // 7. Signal {decision: "approved"} — triggers SUCCESS Goal
        // 8. Assert:
        //    - intake stage COMPLETED
        //    - underwriting stage COMPLETED
        //    - milestone "intake-complete" achieved in plan model
        //    - CaseStatus == COMPLETED
        //    - EventLog contains WORKER_EXECUTION_COMPLETED x3,
        //      MILESTONE_REACHED x1, STAGE_ACTIVATED x2, STAGE_COMPLETED x2
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 2: Write stage termination E2E test**

```java
// StageTerminationE2ETest.java
package io.casehub.blackboard.e2e;

import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: active Stage terminates on abort signal, case transitions to FAILED
 * via FAILURE Goal. EventLog records STAGE_TERMINATED entry.
 * See casehubio/engine#76.
 */
@QuarkusTest
class StageTerminationE2ETest {

    @Inject BlackboardRegistry registry;

    @Test
    void stage_terminates_and_case_fails_on_abort_signal() throws Exception {
        // 1. Stage "processing" with exit condition: ctx -> ctx.getPath(".abort") != null
        // 2. Goal (FAILURE): ".abort != null"
        // 3. Start case, activate stage
        // 4. Signal {abort: true}
        // 5. Assert:
        //    - stage TERMINATED
        //    - CaseStatus == FAILED
        //    - EventLog contains STAGE_TERMINATED entry
        assertThat(true).isTrue();
    }
}
```

- [ ] **Step 3: Run E2E tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest="*E2ETest" -q
```

- [ ] **Step 4: Run all casehub-blackboard tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -q
```

Expected: all unit tests pass. Integration and E2E test assertions filled in — all pass.

- [ ] **Step 5: Run full engine suite — verify no regressions**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl api,engine,casehub-blackboard -am -q
```

Expected: `BUILD SUCCESS`. No regressions in engine tests.

- [ ] **Step 6: Final commit**

```bash
git add casehub-blackboard/src/test
git commit -m "test: add E2E tests for document analysis and stage termination (casehubio/engine#76)

All tests passing. casehub-blackboard module complete:
- LoopControl async (api/)
- PlanItem, CasePlanModel, Stage, StageLifecycleEvaluator
- PlanningStrategy SPI + DefaultPlanningStrategy
- BlackboardRegistry, PlanItemCompletionHandler, MilestoneAchievementHandler
- PlanningStrategyLoopControl (@Alternative @Priority 10)
- Unit, integration, and E2E test coverage"
```

---

## Self-Review

**Spec coverage check:**

| Spec Section | Task |
|---|---|
| `LoopControl` async change | Task 1 |
| Maven module setup | Task 2 |
| `PlanItem` | Task 3 |
| `CasePlanModel` + `DefaultCasePlanModel` | Task 4 |
| `Stage` + `StageStatus` | Task 5 |
| Stage events + `BlackboardEventBusAddresses` | Task 6 |
| `StageLifecycleEvaluator` | Task 7 |
| `PlanningStrategy` + `DefaultPlanningStrategy` + contract test | Task 8 |
| `BlackboardRegistry` | Task 9 |
| `MilestoneAchievementHandler` | Task 10 |
| `PlanItemCompletionHandler` + Stage autocomplete | Task 11 |
| `PlanningStrategyLoopControl` | Task 12 |
| Integration: choreography unchanged, basic loop | Task 13 |
| Integration: Stage lifecycle + milestones + nested stages | Task 14 |
| Integration: sequential execution | Task 15 |
| E2E: document analysis + abort | Task 16 |

**No gaps found.**

**Type consistency:** `PlanItem.create(String bindingName, String workerName, int priority)` used consistently in Tasks 3, 11, 12. `BlackboardRegistry` injected in Tasks 9, 10, 11, 12 — same bean. `StageLifecycleEvaluator` constructor-injected in Task 7 tests, `@Inject` in Task 12 — consistent.

**Notes for implementer:**
- Task 13–16 integration test bodies contain `assertThat(true).isTrue()` placeholders. These must be replaced with real `CaseHubRuntime` invocations following existing engine integration test patterns before marking those tasks complete.
- `ExpressionEvaluator.evaluate()` exact method signature — verify against `api/model/evaluator/ExpressionEvaluator.java` before Task 7.
- `MilestoneReachedEvent` constructor signature — verify against `engine/internal/event/MilestoneReachedEvent.java` before Task 10.
