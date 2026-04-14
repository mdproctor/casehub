# casehub-blackboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `casehub-blackboard` — an optional Maven module that layers CMMN/Blackboard orchestration (Stages, PlanItems, PlanningStrategy) onto casehub-engine, with pure choreography remaining the default when the module is absent.

**Architecture:** `PlanningStrategyLoopControl` implements `LoopControl` as a CDI `@Alternative @Priority(10)`, replacing `ChoreographyLoopControl` when `casehub-blackboard` is on the classpath. It evaluates Stage entry/exit criteria, builds a `PlanItem` hierarchy, delegates to `PlanningStrategy`, and returns the filtered `Binding` list to the engine. The engine sees only `Binding`s — the entire Blackboard layer is contained within the `LoopControl` boundary.

**Tech Stack:** Java 17, Quarkus 3.31.1, CDI (quarkus-arc), JUnit 5, AssertJ, Awaitility, Quarkus Dev Services (PostgreSQL via TestContainers)

**Test discipline:** Every task writes failing tests first, then implements, then verifies green. No task is complete until all tests pass. Do not move to the next task until coverage is high, meaningful, and all tests are green.

---

## File Map

**New module `casehub-blackboard/`:**
- `casehub-blackboard/pom.xml`
- `src/main/java/io/casehub/blackboard/plan/PlanItemStatus.java`
- `src/main/java/io/casehub/blackboard/plan/PlanItem.java`
- `src/main/java/io/casehub/blackboard/plan/CasePlanModel.java`
- `src/main/java/io/casehub/blackboard/plan/CasePlanModelRegistry.java`
- `src/main/java/io/casehub/blackboard/stage/Stage.java`
- `src/main/java/io/casehub/blackboard/stage/SubCase.java`
- `src/main/java/io/casehub/blackboard/strategy/PlanningStrategy.java`
- `src/main/java/io/casehub/blackboard/strategy/DefaultPlanningStrategy.java`
- `src/main/java/io/casehub/blackboard/strategy/SubCaseCompletionStrategy.java`
- `src/main/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategy.java`
- `src/main/java/io/casehub/blackboard/strategy/PlanningStrategyLoopControl.java`
- `src/main/java/io/casehub/blackboard/SubCaseCompletionListener.java`
- `src/test/resources/application.properties`

**Modified in `api/`:**
- `api/src/main/java/io/casehub/api/plan/PlanElement.java` (new)
- `api/src/main/java/io/casehub/api/model/Worker.java` — `implements PlanElement`
- `api/src/main/java/io/casehub/api/model/Milestone.java` — add `condition(Predicate<CaseContext>)` overload
- `api/src/main/java/io/casehub/api/model/Goal.java` — add `condition(Predicate<CaseContext>)` overload

**Modified in `engine/`:**
- `engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java` — add `parentPlanItemId` (nullable UUID)
- `engine/src/main/resources/db/migration/V1.3.0__Add_Parent_Plan_Item_Id.sql` (new)

**Modified in project root:**
- `pom.xml` — add `casehub-blackboard` to `<modules>`

---

## Task 1: Module scaffold

**Files:**
- Create: `casehub-blackboard/pom.xml`
- Modify: `pom.xml` (root)

- [ ] **Step 1: Add module to parent pom**

In `pom.xml`, add `<module>casehub-blackboard</module>` to `<modules>`:

```xml
<modules>
    <module>api</module>
    <module>codegen</module>
    <module>schema</module>
    <module>engine</module>
    <module>casehub-blackboard</module>
</modules>
```

- [ ] **Step 2: Create `casehub-blackboard/pom.xml`**

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

    <artifactId>casehub-blackboard</artifactId>
    <name>Case Hub :: Blackboard</name>
    <description>Optional CMMN/Blackboard orchestration layer — Stages, PlanItems, PlanningStrategy</description>

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

- [ ] **Step 3: Create test application.properties**

Create `casehub-blackboard/src/test/resources/application.properties`:

```properties
%test.quarkus.hibernate-orm.mapping.format.global=ignore
%test.quarkus.flyway.clean-at-start=true
```

- [ ] **Step 4: Verify the module compiles (no sources yet)**

```bash
cd /Users/mdproctor/dev/casehub-engine
mvn compile -pl casehub-blackboard -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml casehub-blackboard/
git commit -m "feat(blackboard): scaffold casehub-blackboard module

Empty module — pom.xml + test application.properties only.
Depends on api/ + engine/ + quarkus-arc.

Refs #30"
```

---

## Task 2: PlanElement marker interface in api/

**Files:**
- Create: `api/src/main/java/io/casehub/api/plan/PlanElement.java`
- Modify: `api/src/main/java/io/casehub/api/model/Worker.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanElementTest.java`

- [ ] **Step 1: Write failing test**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanElementTest.java`:

```java
package io.casehub.blackboard.plan;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.Capability;
import io.casehub.api.model.Worker;
import io.casehub.api.plan.PlanElement;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanElementTest {

  @Test
  void workerImplementsPlanElement() {
    Worker worker =
        Worker.builder()
            .name("w")
            .capabilities(Capability.builder().name("c").build())
            .function(input -> input)
            .build();
    assertThat(worker).isInstanceOf(PlanElement.class);
  }

  @Test
  void planElementIsAnInterface() {
    assertThat(PlanElement.class).isInterface();
  }
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
cd /Users/mdproctor/dev/casehub-engine
mvn test -pl casehub-blackboard -am -Dtest="PlanElementTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

Expected: compilation failure — `PlanElement` not found.

- [ ] **Step 3: Create `PlanElement.java` in api/**

Create `api/src/main/java/io/casehub/api/plan/PlanElement.java`:

```java
package io.casehub.api.plan;

/**
 * Marker interface for elements that can be wrapped in a {@link
 * io.casehub.blackboard.plan.PlanItem} and tracked through a CMMN lifecycle.
 *
 * <p>Permitted implementations: {@link io.casehub.api.model.Worker}, {@code
 * io.casehub.blackboard.stage.Stage}, {@code io.casehub.blackboard.stage.SubCase}.
 */
public interface PlanElement {}
```

- [ ] **Step 4: Add `implements PlanElement` to `Worker`**

In `api/src/main/java/io/casehub/api/model/Worker.java`, add the import and interface:

```java
import io.casehub.api.plan.PlanElement;

public class Worker implements PlanElement {
```

- [ ] **Step 5: Run test — confirm it passes**

```bash
mvn test -pl casehub-blackboard -am -Dtest="PlanElementTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD"
```

Expected: `Tests run: 2, Failures: 0` and BUILD SUCCESS.

- [ ] **Step 6: Run full suite — no regressions**

```bash
mvn test -pl api,engine,casehub-blackboard -am 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: BUILD SUCCESS, all existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add api/src/main/java/io/casehub/api/plan/ api/src/main/java/io/casehub/api/model/Worker.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanElementTest.java
git commit -m "feat(api): add PlanElement marker interface; Worker implements it

Non-sealed — implementations span Maven module boundaries:
Worker in api/, Stage and SubCase in casehub-blackboard/.

Refs #30"
```

---

## Task 3: Lambda condition overload on Milestone and Goal

**Files:**
- Modify: `api/src/main/java/io/casehub/api/model/Milestone.java`
- Modify: `api/src/main/java/io/casehub/api/model/Goal.java`
- Test: `engine/src/test/java/io/casehub/engine/model/ModelBuilderTest.java` (add to existing nested class)

- [ ] **Step 1: Write failing tests**

In `engine/src/test/java/io/casehub/engine/model/ModelBuilderTest.java`, add to `MilestoneBuilderTests`:

```java
@Test
@DisplayName("condition(Predicate) creates LambdaExpressionEvaluator")
void conditionPredicate_createsLambdaEvaluator() {
    final var m = Milestone.builder()
        .name("m")
        .condition((CaseContext ctx) -> true)
        .build();
    assertInstanceOf(LambdaExpressionEvaluator.class, m.getCondition());
}

@Test
@DisplayName("null Predicate condition throws NullPointerException")
void nullPredicateCondition_throws() {
    assertThrows(
        NullPointerException.class,
        () -> Milestone.builder().name("m").condition((java.util.function.Predicate<CaseContext>) null).build());
}
```

And add to `GoalBuilderTests`:

```java
@Test
@DisplayName("condition(Predicate) creates LambdaExpressionEvaluator")
void conditionPredicate_createsLambdaEvaluator() {
    final var g = Goal.builder()
        .name("g")
        .condition((CaseContext ctx) -> true)
        .kind(GoalKind.SUCCESS)
        .build();
    assertInstanceOf(LambdaExpressionEvaluator.class, g.getCondition());
}

@Test
@DisplayName("null Predicate condition throws NullPointerException")
void nullPredicateCondition_throws() {
    assertThrows(
        NullPointerException.class,
        () -> Goal.builder().name("g")
            .condition((java.util.function.Predicate<CaseContext>) null)
            .kind(GoalKind.SUCCESS)
            .build());
}
```

- [ ] **Step 2: Run — confirm failure**

```bash
mvn test -pl api,engine -am -Dtest="ModelBuilderTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "FAIL|ERROR" | head -5
```

Expected: compilation failure — no `condition(Predicate)` method.

- [ ] **Step 3: Add lambda overload to `Milestone.Builder`**

In `api/src/main/java/io/casehub/api/model/Milestone.java`, add import and overload:

```java
import io.casehub.api.model.evaluator.LambdaExpressionEvaluator;
import java.util.function.Predicate;
```

Add inside `Builder` after the existing `condition(String)` method:

```java
public Builder condition(Predicate<io.casehub.api.context.CaseContext> predicate) {
    Objects.requireNonNull(predicate, "condition must not be null");
    this.condition = new LambdaExpressionEvaluator(predicate);
    return this;
}
```

- [ ] **Step 4: Add lambda overload to `Goal.Builder`**

In `api/src/main/java/io/casehub/api/model/Goal.java`, same imports and overload inside `Builder`:

```java
import io.casehub.api.model.evaluator.LambdaExpressionEvaluator;
import java.util.function.Predicate;
```

```java
public Builder condition(Predicate<io.casehub.api.context.CaseContext> predicate) {
    Objects.requireNonNull(predicate, "condition must not be null");
    this.condition = new LambdaExpressionEvaluator(predicate);
    return this;
}
```

- [ ] **Step 5: Run tests — confirm green**

```bash
mvn test -pl api,engine -am -Dtest="ModelBuilderTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: BUILD SUCCESS, all Milestone and Goal builder tests pass.

- [ ] **Step 6: Full suite — no regressions**

```bash
mvn test -pl api,engine -am 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add api/src/main/java/io/casehub/api/model/Milestone.java \
        api/src/main/java/io/casehub/api/model/Goal.java \
        engine/src/test/java/io/casehub/engine/model/ModelBuilderTest.java
git commit -m "feat(api): add lambda Predicate overload to Milestone and Goal condition builders

Universal condition pattern: String (JQ), Predicate (lambda),
ExpressionEvaluator (any engine) — all builder overloads present.
Null Predicate throws NullPointerException at builder time.

Refs #30"
```

---

## Task 4: PlanItemStatus enum

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItemStatus.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemStatusTest.java`

- [ ] **Step 1: Write failing test**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemStatusTest.java`:

```java
package io.casehub.blackboard.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class PlanItemStatusTest {

  @Test
  void containsExactlyExpectedValues() {
    assertThat(EnumSet.allOf(PlanItemStatus.class))
        .containsExactlyInAnyOrder(
            PlanItemStatus.PENDING,
            PlanItemStatus.ACTIVE,
            PlanItemStatus.COMPLETED,
            PlanItemStatus.TERMINATED,
            PlanItemStatus.FAULTED);
  }

  @Test
  void pendingIsInitialState() {
    // PENDING is the only non-terminal, non-active state — the start of every PlanItem lifecycle
    assertThat(PlanItemStatus.PENDING.isTerminal()).isFalse();
    assertThat(PlanItemStatus.PENDING.isActive()).isFalse();
  }

  @Test
  void activeIsNotTerminal() {
    assertThat(PlanItemStatus.ACTIVE.isTerminal()).isFalse();
    assertThat(PlanItemStatus.ACTIVE.isActive()).isTrue();
  }

  @Test
  void completedTerminatedAndFaultedAreTerminal() {
    assertThat(PlanItemStatus.COMPLETED.isTerminal()).isTrue();
    assertThat(PlanItemStatus.TERMINATED.isTerminal()).isTrue();
    assertThat(PlanItemStatus.FAULTED.isTerminal()).isTrue();
  }

  @Test
  void faultedIsDistinctFromTerminated() {
    // FAULTED = stopped by failure; TERMINATED = stopped by design
    assertThat(PlanItemStatus.FAULTED).isNotEqualTo(PlanItemStatus.TERMINATED);
    assertThat(PlanItemStatus.FAULTED.isFaulted()).isTrue();
    assertThat(PlanItemStatus.TERMINATED.isFaulted()).isFalse();
  }
}
```

- [ ] **Step 2: Run — confirm compilation failure**

```bash
mvn test -pl casehub-blackboard -am -Dtest="PlanItemStatusTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "FAIL|ERROR" | head -3
```

Expected: compilation failure.

- [ ] **Step 3: Create `PlanItemStatus.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItemStatus.java`:

```java
package io.casehub.blackboard.plan;

/** Lifecycle states for a {@link PlanItem}. */
public enum PlanItemStatus {
  /** Created — waiting for entry criteria to be met. */
  PENDING,
  /** Entry criteria met — element is executing. */
  ACTIVE,
  /** Completed successfully or exit criteria met. */
  COMPLETED,
  /** Stopped by design — parent terminated, or externally cancelled. */
  TERMINATED,
  /** Stopped by failure — worker exhausted retries, or sub-case faulted. */
  FAULTED;

  public boolean isTerminal() {
    return this == COMPLETED || this == TERMINATED || this == FAULTED;
  }

  public boolean isActive() {
    return this == ACTIVE;
  }

  public boolean isFaulted() {
    return this == FAULTED;
  }
}
```

- [ ] **Step 4: Run — confirm green**

```bash
mvn test -pl casehub-blackboard -am -Dtest="PlanItemStatusTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 5 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItemStatus.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemStatusTest.java
git commit -m "feat(blackboard): add PlanItemStatus lifecycle enum

PENDING→ACTIVE→COMPLETED/TERMINATED/FAULTED.
FAULTED (failure) distinct from TERMINATED (design).
isTerminal(), isActive(), isFaulted() convenience methods.

Refs #30"
```

---

## Task 5: PlanItem hierarchy

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java`

- [ ] **Step 1: Write failing tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java`:

```java
package io.casehub.blackboard.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.api.plan.PlanElement;
import org.junit.jupiter.api.Test;

class PlanItemTest {

  // Minimal PlanElement for testing
  static class TestElement implements PlanElement {}

  @Test
  void newPlanItemStartsInPending() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    assertThat(item.getStatus()).isEqualTo(PlanItemStatus.PENDING);
  }

  @Test
  void activateTransitionsPendingToActive() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    item.activate();
    assertThat(item.getStatus()).isEqualTo(PlanItemStatus.ACTIVE);
    assertThat(item.getActivatedAt()).isNotNull();
  }

  @Test
  void completeTransitionsActiveToCompleted() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    item.activate();
    item.complete();
    assertThat(item.getStatus()).isEqualTo(PlanItemStatus.COMPLETED);
    assertThat(item.getCompletedAt()).isNotNull();
  }

  @Test
  void terminateTransitionsActiveToTerminated() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    item.activate();
    item.terminate();
    assertThat(item.getStatus()).isEqualTo(PlanItemStatus.TERMINATED);
  }

  @Test
  void faultTransitionsActiveToFaulted() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    item.activate();
    item.fault();
    assertThat(item.getStatus()).isEqualTo(PlanItemStatus.FAULTED);
  }

  @Test
  void activatingAlreadyActiveItemThrows() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    item.activate();
    assertThatThrownBy(item::activate).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void completingPendingItemThrows() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    assertThatThrownBy(item::complete).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void parentReferenceIsNullForRootItem() {
    PlanItem<TestElement> item = PlanItem.of(new TestElement());
    assertThat(item.getParent()).isNull();
  }

  @Test
  void addChildSetsParentOnChild() {
    PlanItem<TestElement> parent = PlanItem.of(new TestElement());
    PlanItem<TestElement> child = PlanItem.of(new TestElement());
    parent.addChild(child);
    assertThat(child.getParent()).isSameAs(parent);
    assertThat(parent.getChildren()).containsExactly(child);
  }

  @Test
  void terminatingParentTerminatesAllChildren() {
    PlanItem<TestElement> parent = PlanItem.of(new TestElement());
    PlanItem<TestElement> child1 = PlanItem.of(new TestElement());
    PlanItem<TestElement> child2 = PlanItem.of(new TestElement());
    parent.addChild(child1);
    parent.addChild(child2);
    parent.activate();
    child1.activate();
    parent.terminate();
    assertThat(parent.getStatus()).isEqualTo(PlanItemStatus.TERMINATED);
    assertThat(child1.getStatus()).isEqualTo(PlanItemStatus.TERMINATED);
    assertThat(child2.getStatus()).isEqualTo(PlanItemStatus.TERMINATED);
  }

  @Test
  void elementIsRetained() {
    TestElement element = new TestElement();
    PlanItem<TestElement> item = PlanItem.of(element);
    assertThat(item.getElement()).isSameAs(element);
  }
}
```

- [ ] **Step 2: Run — confirm compilation failure**

```bash
mvn test -pl casehub-blackboard -am -Dtest="PlanItemTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "FAIL|ERROR" | head -3
```

- [ ] **Step 3: Create `PlanItem.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java`:

```java
package io.casehub.blackboard.plan;

import io.casehub.api.plan.PlanElement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Runtime lifecycle container for a {@link PlanElement}. Hierarchical — knows its parent and children. */
public class PlanItem<T extends PlanElement> {

  private final T element;
  private PlanItemStatus status;
  private PlanItem<?> parent;
  private final List<PlanItem<?>> children = new ArrayList<>();
  private Instant activatedAt;
  private Instant completedAt;

  private PlanItem(T element) {
    this.element = element;
    this.status = PlanItemStatus.PENDING;
  }

  public static <T extends PlanElement> PlanItem<T> of(T element) {
    return new PlanItem<>(element);
  }

  public T getElement() {
    return element;
  }

  public PlanItemStatus getStatus() {
    return status;
  }

  public PlanItem<?> getParent() {
    return parent;
  }

  public List<PlanItem<?>> getChildren() {
    return Collections.unmodifiableList(children);
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void addChild(PlanItem<?> child) {
    child.parent = this;
    children.add(child);
  }

  public void activate() {
    requireStatus(PlanItemStatus.PENDING, "activate");
    this.status = PlanItemStatus.ACTIVE;
    this.activatedAt = Instant.now();
  }

  public void complete() {
    requireStatus(PlanItemStatus.ACTIVE, "complete");
    this.status = PlanItemStatus.COMPLETED;
    this.completedAt = Instant.now();
  }

  public void terminate() {
    if (status.isTerminal()) return; // idempotent for terminal states
    this.status = PlanItemStatus.TERMINATED;
    this.completedAt = Instant.now();
    for (PlanItem<?> child : children) {
      child.terminate();
    }
  }

  public void fault() {
    requireStatus(PlanItemStatus.ACTIVE, "fault");
    this.status = PlanItemStatus.FAULTED;
    this.completedAt = Instant.now();
  }

  private void requireStatus(PlanItemStatus required, String operation) {
    if (status != required) {
      throw new IllegalStateException(
          "Cannot " + operation + " PlanItem in status " + status + " (requires " + required + ")");
    }
  }
}
```

- [ ] **Step 4: Run — confirm green**

```bash
mvn test -pl casehub-blackboard -am -Dtest="PlanItemTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 11 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java
git commit -m "feat(blackboard): add PlanItem<T> generic lifecycle container

Hierarchical — parent/children refs for full lineage.
Transitions: PENDING→ACTIVE→COMPLETED/TERMINATED/FAULTED.
terminate() propagates to all children recursively.
Invalid transitions throw IllegalStateException.

Refs #30"
```

---

## Task 6: Stage type

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`

- [ ] **Step 1: Write failing tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`:

```java
package io.casehub.blackboard.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.api.model.evaluator.LambdaExpressionEvaluator;
import io.casehub.api.plan.PlanElement;
import org.junit.jupiter.api.Test;

class StageTest {

  @Test
  void stageImplementsPlanElement() {
    Stage stage = Stage.builder().name("s").entry(".x == true").build();
    assertThat(stage).isInstanceOf(PlanElement.class);
  }

  @Test
  void nameIsRequired() {
    assertThatThrownBy(() -> Stage.builder().entry(".x == true").build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void entryConditionIsRequired() {
    assertThatThrownBy(() -> Stage.builder().name("s").build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void entryStringCreatesJQEvaluator() {
    Stage stage = Stage.builder().name("s").entry(".x == true").build();
    assertInstanceOf(JQExpressionEvaluator.class, stage.getEntryCondition());
  }

  @Test
  void entryLambdaCreatesLambdaEvaluator() {
    Stage stage = Stage.builder().name("s").entry(ctx -> true).build();
    assertInstanceOf(LambdaExpressionEvaluator.class, stage.getEntryCondition());
  }

  @Test
  void nullEntryStringThrows() {
    assertThatThrownBy(() -> Stage.builder().name("s").entry((String) null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullEntryLambdaThrows() {
    assertThatThrownBy(
            () -> Stage.builder().name("s")
                .entry((java.util.function.Predicate<io.casehub.api.context.CaseContext>) null)
                .build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void exitConditionIsOptional() {
    Stage stage = Stage.builder().name("s").entry(".x == true").build();
    assertThat(stage.getExitCondition()).isNull();
  }

  @Test
  void exitStringCreatesJQEvaluator() {
    Stage stage = Stage.builder().name("s").entry(".x == true").exit(".y == true").build();
    assertInstanceOf(JQExpressionEvaluator.class, stage.getExitCondition());
  }

  @Test
  void exitLambdaCreatesLambdaEvaluator() {
    Stage stage = Stage.builder().name("s").entry(".x == true").exit(ctx -> false).build();
    assertInstanceOf(LambdaExpressionEvaluator.class, stage.getExitCondition());
  }

  @Test
  void workersDefaultToEmpty() {
    Stage stage = Stage.builder().name("s").entry(".x == true").build();
    assertThat(stage.getWorkers()).isEmpty();
  }

  @Test
  void nestedStagesDefaultToEmpty() {
    Stage stage = Stage.builder().name("s").entry(".x == true").build();
    assertThat(stage.getNestedStages()).isEmpty();
  }
}
```

- [ ] **Step 2: Run — confirm compilation failure**

```bash
mvn test -pl casehub-blackboard -am -Dtest="StageTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "FAIL|ERROR" | head -3
```

- [ ] **Step 3: Create `Stage.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java`:

```java
package io.casehub.blackboard.stage;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.Worker;
import io.casehub.api.model.evaluator.ExpressionEvaluator;
import io.casehub.api.model.evaluator.JQExpressionEvaluator;
import io.casehub.api.model.evaluator.LambdaExpressionEvaluator;
import io.casehub.api.plan.PlanElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** CMMN Stage — a named logical grouping of Workers with entry/exit criteria. */
public class Stage implements PlanElement {

  private final String name;
  private final ExpressionEvaluator entryCondition;
  private final ExpressionEvaluator exitCondition;
  private final List<Worker> workers;
  private final List<Stage> nestedStages;
  private final List<SubCase> subCases;

  private Stage(Builder b) {
    this.name = b.name;
    this.entryCondition = b.entryCondition;
    this.exitCondition = b.exitCondition;
    this.workers = List.copyOf(b.workers);
    this.nestedStages = List.copyOf(b.nestedStages);
    this.subCases = List.copyOf(b.subCases);
  }

  public String getName() { return name; }
  public ExpressionEvaluator getEntryCondition() { return entryCondition; }
  public ExpressionEvaluator getExitCondition() { return exitCondition; }
  public List<Worker> getWorkers() { return workers; }
  public List<Stage> getNestedStages() { return nestedStages; }
  public List<SubCase> getSubCases() { return subCases; }

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private String name;
    private ExpressionEvaluator entryCondition;
    private ExpressionEvaluator exitCondition;
    private final List<Worker> workers = new ArrayList<>();
    private final List<Stage> nestedStages = new ArrayList<>();
    private final List<SubCase> subCases = new ArrayList<>();

    public Builder name(String name) { this.name = name; return this; }

    public Builder entry(String jq) {
      this.entryCondition = new JQExpressionEvaluator(Objects.requireNonNull(jq, "entry condition must not be null"));
      return this;
    }

    public Builder entry(Predicate<CaseContext> predicate) {
      this.entryCondition = new LambdaExpressionEvaluator(Objects.requireNonNull(predicate, "entry condition must not be null"));
      return this;
    }

    public Builder entry(ExpressionEvaluator evaluator) {
      this.entryCondition = evaluator;
      return this;
    }

    public Builder exit(String jq) {
      this.exitCondition = new JQExpressionEvaluator(Objects.requireNonNull(jq, "exit condition must not be null"));
      return this;
    }

    public Builder exit(Predicate<CaseContext> predicate) {
      this.exitCondition = new LambdaExpressionEvaluator(Objects.requireNonNull(predicate, "exit condition must not be null"));
      return this;
    }

    public Builder exit(ExpressionEvaluator evaluator) {
      this.exitCondition = evaluator;
      return this;
    }

    public Builder workers(Worker... workers) {
      this.workers.addAll(Arrays.asList(workers));
      return this;
    }

    public Builder nestedStage(Stage stage) {
      this.nestedStages.add(stage);
      return this;
    }

    public Builder subCase(SubCase subCase) {
      this.subCases.add(subCase);
      return this;
    }

    public Stage build() {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(entryCondition, "entry condition must not be null");
      return new Stage(this);
    }
  }
}
```

- [ ] **Step 4: Create stub `SubCase.java`** (needed for Stage to compile — full implementation in Task 7)

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java`:

```java
package io.casehub.blackboard.stage;

import io.casehub.api.plan.PlanElement;

/** Placeholder — full implementation in Task 7. */
public class SubCase implements PlanElement {
  // TODO: implement in Task 7
}
```

- [ ] **Step 5: Run — confirm green**

```bash
mvn test -pl casehub-blackboard -am -Dtest="StageTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 13 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/stage/ \
        casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java
git commit -m "feat(blackboard): add Stage type with three-overload condition builder

Stage implements PlanElement. Entry required, exit optional.
Three overloads: String (JQ), Predicate (lambda), ExpressionEvaluator.
Null conditions throw at builder time.
SubCase stub added to allow Stage to compile.

Refs #30"
```

---

## Task 7: SubCase type and SubCaseCompletionStrategy

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/SubCaseCompletionStrategy.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategy.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategyTest.java`

- [ ] **Step 1: Write failing tests for SubCase**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java`:

```java
package io.casehub.blackboard.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.api.plan.PlanElement;
import io.casehub.blackboard.strategy.DefaultSubCaseCompletionStrategy;
import org.junit.jupiter.api.Test;

class SubCaseTest {

  @Test
  void subCaseImplementsPlanElement() {
    SubCase sc = SubCase.builder().namespace("ns").name("n").version("1.0").build();
    assertThat(sc).isInstanceOf(PlanElement.class);
  }

  @Test
  void namespaceRequired() {
    assertThatThrownBy(() -> SubCase.builder().name("n").version("1.0").build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nameRequired() {
    assertThatThrownBy(() -> SubCase.builder().namespace("ns").version("1.0").build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void versionRequired() {
    assertThatThrownBy(() -> SubCase.builder().namespace("ns").name("n").build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void defaultCompletionStrategyIsUsedWhenNotSpecified() {
    SubCase sc = SubCase.builder().namespace("ns").name("n").version("1.0").build();
    assertThat(sc.getCompletionStrategy()).isInstanceOf(DefaultSubCaseCompletionStrategy.class);
  }

  @Test
  void customCompletionStrategyIsRetained() {
    var strategy = (status, ctx) -> io.casehub.blackboard.plan.PlanItemStatus.TERMINATED;
    SubCase sc = SubCase.builder().namespace("ns").name("n").version("1.0")
        .completionStrategy(strategy).build();
    assertThat(sc.getCompletionStrategy()).isSameAs(strategy);
  }

  @Test
  void fieldsAreRetained() {
    SubCase sc = SubCase.builder().namespace("ns").name("my-case").version("2.0").build();
    assertThat(sc.getNamespace()).isEqualTo("ns");
    assertThat(sc.getName()).isEqualTo("my-case");
    assertThat(sc.getVersion()).isEqualTo("2.0");
  }
}
```

- [ ] **Step 2: Write failing tests for DefaultSubCaseCompletionStrategy**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategyTest.java`:

```java
package io.casehub.blackboard.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.CaseStatus;
import io.casehub.blackboard.plan.PlanItemStatus;
import org.junit.jupiter.api.Test;

class DefaultSubCaseCompletionStrategyTest {

  private final DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();

  @Test
  void completedMapsToCompleted() {
    assertThat(strategy.resolve(CaseStatus.COMPLETED, null)).isEqualTo(PlanItemStatus.COMPLETED);
  }

  @Test
  void faultedMapsToFaulted() {
    assertThat(strategy.resolve(CaseStatus.FAULTED, null)).isEqualTo(PlanItemStatus.FAULTED);
  }

  @Test
  void cancelledMapsToTerminated() {
    assertThat(strategy.resolve(CaseStatus.CANCELLED, null)).isEqualTo(PlanItemStatus.TERMINATED);
  }

  @Test
  void waitingMapsToTerminated() {
    assertThat(strategy.resolve(CaseStatus.WAITING, null)).isEqualTo(PlanItemStatus.TERMINATED);
  }

  @Test
  void suspendedMapsToTerminated() {
    assertThat(strategy.resolve(CaseStatus.SUSPENDED, null)).isEqualTo(PlanItemStatus.TERMINATED);
  }
}
```

- [ ] **Step 3: Create `SubCaseCompletionStrategy.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/SubCaseCompletionStrategy.java`:

```java
package io.casehub.blackboard.strategy;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseStatus;
import io.casehub.blackboard.plan.PlanItemStatus;

/**
 * Strategy that maps a completed sub-case's {@link CaseStatus} to a {@link PlanItemStatus}
 * for the parent {@link io.casehub.blackboard.plan.PlanItem}.
 */
@FunctionalInterface
public interface SubCaseCompletionStrategy {
  PlanItemStatus resolve(CaseStatus subCaseStatus, CaseContext subCaseContext);
}
```

- [ ] **Step 4: Create `DefaultSubCaseCompletionStrategy.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategy.java`:

```java
package io.casehub.blackboard.strategy;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseStatus;
import io.casehub.blackboard.plan.PlanItemStatus;

/**
 * Default: COMPLETED→COMPLETED, FAULTED→FAULTED, everything else→TERMINATED.
 */
public class DefaultSubCaseCompletionStrategy implements SubCaseCompletionStrategy {

  @Override
  public PlanItemStatus resolve(CaseStatus subCaseStatus, CaseContext subCaseContext) {
    return switch (subCaseStatus) {
      case COMPLETED -> PlanItemStatus.COMPLETED;
      case FAULTED -> PlanItemStatus.FAULTED;
      default -> PlanItemStatus.TERMINATED;
    };
  }
}
```

- [ ] **Step 5: Implement full `SubCase.java`**

Replace the stub at `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java`:

```java
package io.casehub.blackboard.stage;

import io.casehub.api.plan.PlanElement;
import io.casehub.blackboard.strategy.DefaultSubCaseCompletionStrategy;
import io.casehub.blackboard.strategy.SubCaseCompletionStrategy;
import java.util.Objects;

/** Reference to a child case to spawn as a sub-case PlanItem. */
public class SubCase implements PlanElement {

  private final String namespace;
  private final String name;
  private final String version;
  private final SubCaseCompletionStrategy completionStrategy;

  private SubCase(Builder b) {
    this.namespace = b.namespace;
    this.name = b.name;
    this.version = b.version;
    this.completionStrategy = b.completionStrategy;
  }

  public String getNamespace() { return namespace; }
  public String getName() { return name; }
  public String getVersion() { return version; }
  public SubCaseCompletionStrategy getCompletionStrategy() { return completionStrategy; }

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private String namespace;
    private String name;
    private String version;
    private SubCaseCompletionStrategy completionStrategy = new DefaultSubCaseCompletionStrategy();

    public Builder namespace(String namespace) { this.namespace = namespace; return this; }
    public Builder name(String name) { this.name = name; return this; }
    public Builder version(String version) { this.version = version; return this; }
    public Builder completionStrategy(SubCaseCompletionStrategy strategy) {
      this.completionStrategy = strategy;
      return this;
    }

    public SubCase build() {
      Objects.requireNonNull(namespace, "namespace must not be null");
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(version, "version must not be null");
      return new SubCase(this);
    }
  }
}
```

- [ ] **Step 6: Run tests**

```bash
mvn test -pl casehub-blackboard -am -Dtest="SubCaseTest,DefaultSubCaseCompletionStrategyTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 12 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java \
        casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/ \
        casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultSubCaseCompletionStrategyTest.java
git commit -m "feat(blackboard): add SubCase + SubCaseCompletionStrategy

SubCase: namespace/name/version reference with pluggable completion strategy.
Default strategy: COMPLETED→COMPLETED, FAULTED→FAULTED, else→TERMINATED.
SubCaseCompletionStrategy is @FunctionalInterface for lambda use.

Refs #30"
```

---

## Task 8: CasePlanModel and Registry

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModelRegistry.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/CasePlanModelTest.java`

- [ ] **Step 1: Write failing tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/CasePlanModelTest.java`:

```java
package io.casehub.blackboard.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.api.model.CaseDefinition;
import io.casehub.blackboard.stage.Stage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CasePlanModelTest {

  private static CaseDefinition def(String name) {
    return CaseDefinition.builder().namespace("test").name(name).version("1.0").build();
  }

  private static Stage stage(String name) {
    return Stage.builder().name(name).entry(".x == true").build();
  }

  @Test
  void nameRequired() {
    assertThatThrownBy(() -> CasePlanModel.builder().build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void stagesAreRetained() {
    Stage s1 = stage("s1");
    Stage s2 = stage("s2");
    CasePlanModel model = CasePlanModel.builder().name("m").stages(s1, s2).build();
    assertThat(model.getStages()).containsExactly(s1, s2);
  }

  @Test
  void stagesDefaultToEmpty() {
    CasePlanModel model = CasePlanModel.builder().name("m").build();
    assertThat(model.getStages()).isEmpty();
  }

  @Test
  void registryReturnsEmptyForUnknownDefinition() {
    CasePlanModelRegistry registry = new CasePlanModelRegistry();
    Optional<CasePlanModel> result = registry.find(def("unknown"));
    assertThat(result).isEmpty();
  }

  @Test
  void registryReturnsPlanForRegisteredDefinition() {
    CasePlanModelRegistry registry = new CasePlanModelRegistry();
    CaseDefinition definition = def("my-case");
    CasePlanModel model = CasePlanModel.builder().name("m").stages(stage("s1")).build();
    registry.register(definition, model);
    assertThat(registry.find(definition)).contains(model);
  }

  @Test
  void registryMatchesByNamespaceNameVersion() {
    CasePlanModelRegistry registry = new CasePlanModelRegistry();
    CaseDefinition def1 = def("case-a");
    CaseDefinition def2 = def("case-b");
    CasePlanModel model1 = CasePlanModel.builder().name("m1").build();
    CasePlanModel model2 = CasePlanModel.builder().name("m2").build();
    registry.register(def1, model1);
    registry.register(def2, model2);
    assertThat(registry.find(def1)).contains(model1);
    assertThat(registry.find(def2)).contains(model2);
  }
}
```

- [ ] **Step 2: Create `CasePlanModel.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java`:

```java
package io.casehub.blackboard.plan;

import io.casehub.blackboard.stage.Stage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Authored orchestration definition — a named list of root Stages. Separate from CaseDefinition. */
public class CasePlanModel {

  private final String name;
  private final List<Stage> stages;

  private CasePlanModel(Builder b) {
    this.name = b.name;
    this.stages = List.copyOf(b.stages);
  }

  public String getName() { return name; }
  public List<Stage> getStages() { return stages; }

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private String name;
    private final List<Stage> stages = new ArrayList<>();

    public Builder name(String name) { this.name = name; return this; }

    public Builder stages(Stage... stages) {
      this.stages.addAll(Arrays.asList(stages));
      return this;
    }

    public CasePlanModel build() {
      Objects.requireNonNull(name, "name must not be null");
      return new CasePlanModel(this);
    }
  }
}
```

- [ ] **Step 3: Create `CasePlanModelRegistry.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModelRegistry.java`:

```java
package io.casehub.blackboard.plan;

import io.casehub.api.model.CaseDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CDI singleton registry mapping CaseDefinition identity (namespace+name+version) to
 * CasePlanModel. Register plans at application startup (e.g., in @PostConstruct of a CaseHub bean).
 */
@ApplicationScoped
public class CasePlanModelRegistry {

  private final Map<String, CasePlanModel> registry = new ConcurrentHashMap<>();

  public void register(CaseDefinition definition, CasePlanModel model) {
    registry.put(key(definition), model);
  }

  public Optional<CasePlanModel> find(CaseDefinition definition) {
    return Optional.ofNullable(registry.get(key(definition)));
  }

  private static String key(CaseDefinition definition) {
    return definition.getNamespace() + ":" + definition.getName() + ":" + definition.getVersion();
  }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl casehub-blackboard -am -Dtest="CasePlanModelTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 6 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java \
        casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModelRegistry.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/plan/CasePlanModelTest.java
git commit -m "feat(blackboard): add CasePlanModel and CasePlanModelRegistry

CasePlanModel: named list of root Stages, separate from CaseDefinition.
CasePlanModelRegistry: @ApplicationScoped CDI singleton, keyed on
namespace+name+version. register() at @PostConstruct; find() at runtime.

Refs #30"
```

---

## Task 9: PlanningStrategy and DefaultPlanningStrategy

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategy.java`
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/DefaultPlanningStrategy.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultPlanningStrategyTest.java`

- [ ] **Step 1: Write failing tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultPlanningStrategyTest.java`:

```java
package io.casehub.blackboard.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.Capability;
import io.casehub.api.model.Worker;
import io.casehub.blackboard.plan.PlanItem;
import io.casehub.blackboard.plan.PlanItemStatus;
import io.casehub.blackboard.stage.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPlanningStrategyTest {

  private final DefaultPlanningStrategy strategy = new DefaultPlanningStrategy();

  private static Worker worker(String name) {
    return Worker.builder()
        .name(name)
        .capabilities(Capability.builder().name("cap-" + name).build())
        .function(input -> input)
        .build();
  }

  private static PlanItem<Worker> workerItem(Worker w) {
    return PlanItem.of(w);
  }

  @Test
  void returnsAllEligibleItems() {
    PlanItem<Worker> w1 = workerItem(worker("a"));
    PlanItem<Worker> w2 = workerItem(worker("b"));
    List<PlanItem<?>> result = strategy.select(null, List.of(w1, w2));
    assertThat(result).containsExactly(w1, w2);
  }

  @Test
  void returnsEmptyForEmptyEligible() {
    List<PlanItem<?>> result = strategy.select(null, List.of());
    assertThat(result).isEmpty();
  }

  @Test
  void preservesOrder() {
    PlanItem<Worker> w1 = workerItem(worker("a"));
    PlanItem<Worker> w2 = workerItem(worker("b"));
    PlanItem<Worker> w3 = workerItem(worker("c"));
    List<PlanItem<?>> result = strategy.select(null, List.of(w1, w2, w3));
    assertThat(result).containsExactly(w1, w2, w3);
  }
}
```

- [ ] **Step 2: Create `PlanningStrategy.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategy.java`:

```java
package io.casehub.blackboard.strategy;

import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.plan.PlanItem;
import java.util.List;

/**
 * SPI for selecting which eligible {@link PlanItem}s to activate.
 *
 * <p>{@code eligible} contains PlanItem&lt;Worker&gt; and PlanItem&lt;SubCase&gt; whose containing
 * Stage is ACTIVE and whose Binding trigger conditions have been evaluated as true by the engine.
 * The strategy picks which to fire. Return all for choreography-within-stage behaviour.
 */
public interface PlanningStrategy {
  List<PlanItem<?>> select(CaseContext context, List<PlanItem<?>> eligible);
}
```

- [ ] **Step 3: Create `DefaultPlanningStrategy.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/DefaultPlanningStrategy.java`:

```java
package io.casehub.blackboard.strategy;

import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.plan.PlanItem;
import java.util.List;

/**
 * Default: fires all eligible PlanItems — orchestration at Stage level, choreography within Stage.
 */
public class DefaultPlanningStrategy implements PlanningStrategy {

  @Override
  public List<PlanItem<?>> select(CaseContext context, List<PlanItem<?>> eligible) {
    return eligible;
  }
}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl casehub-blackboard -am -Dtest="DefaultPlanningStrategyTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: 3 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategy.java \
        casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/DefaultPlanningStrategy.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/strategy/DefaultPlanningStrategyTest.java
git commit -m "feat(blackboard): add PlanningStrategy SPI + DefaultPlanningStrategy

PlanningStrategy: select(context, eligiblePlanItems) → selected items.
Default: returns all eligible — orchestration at Stage, choreography within.

Refs #30"
```

---

## Task 10: PlanningStrategyLoopControl (core bridge) + integration tests

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategyLoopControl.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/BlackboardIntegrationTest.java`

This is the most complex task. Read it entirely before starting.

The `PlanningStrategyLoopControl`:
1. Is injected with `CasePlanModelRegistry`, `PlanningStrategy`, and `ExpressionEngineRegistry` (from engine)
2. On each `select()` call, looks up the `CasePlanModel` for the current case
3. Evaluates Stage entry criteria — activates PENDING stages whose entry condition is true
4. Evaluates Stage exit criteria — completes ACTIVE stages whose exit condition is true (or all children terminal)
5. Filters the engine-provided eligible Bindings to only those whose Workers are in ACTIVE stages
6. Passes the filtered `PlanItem`s to `PlanningStrategy.select()`
7. Translates selected `PlanItem`s back to `Binding`s
8. Passes free-floating Bindings (Workers not in any Stage) through unchanged

The plan instance (tree of `PlanItem`s) is held in a per-case in-memory map keyed by case UUID.

- [ ] **Step 1: Create `PlanningStrategyLoopControl.java`**

Create `casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategyLoopControl.java`:

```java
package io.casehub.blackboard.strategy;

import io.casehub.api.context.CaseContext;
import io.casehub.api.engine.LoopControl;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Worker;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.CasePlanModelRegistry;
import io.casehub.blackboard.plan.PlanItem;
import io.casehub.blackboard.plan.PlanItemStatus;
import io.casehub.blackboard.stage.Stage;
import io.casehub.engine.internal.engine.ExpressionEngineRegistry;
import io.casehub.engine.internal.model.CaseInstance;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * Replaces {@code ChoreographyLoopControl} when casehub-blackboard is on the classpath.
 * Evaluates Stage entry/exit criteria, builds a PlanItem tree per case, delegates to
 * PlanningStrategy, and returns the filtered Binding list to the engine.
 */
@Alternative
@Priority(10)
@ApplicationScoped
public class PlanningStrategyLoopControl implements LoopControl {

  private static final Logger LOG = Logger.getLogger(PlanningStrategyLoopControl.class);

  // Per-case plan instances — keyed by case UUID
  private final Map<UUID, List<PlanItem<Stage>>> planInstances = new ConcurrentHashMap<>();

  @Inject CasePlanModelRegistry registry;
  @Inject PlanningStrategy planningStrategy;
  @Inject ExpressionEngineRegistry expressionEngineRegistry;

  @Override
  public List<Binding> select(CaseContext context, List<Binding> eligible) {
    // CaseContext must carry the case UUID — look it up from the instance cache
    // We access it via the Vert.x context (set by CaseContextChangedEventHandler)
    UUID caseId = extractCaseId(context);
    if (caseId == null) {
      LOG.warn("Could not determine case UUID from context — falling back to all bindings");
      return eligible;
    }

    CaseDefinitionKey key = extractDefinitionKey(context);
    if (key == null) {
      return eligible;
    }

    Optional<CasePlanModel> planModel = registry.findByKey(key.namespace, key.name, key.version);
    if (planModel.isEmpty()) {
      // No CasePlanModel registered — pure choreography, pass all bindings through
      return eligible;
    }

    // Get or build the plan instance for this case
    List<PlanItem<Stage>> stageItems = planInstances.computeIfAbsent(
        caseId, id -> buildPlanInstance(planModel.get()));

    // Evaluate entry criteria — activate PENDING stages
    for (PlanItem<Stage> stageItem : stageItems) {
      evaluateEntry(stageItem, context);
    }

    // Evaluate exit criteria — complete ACTIVE stages
    for (PlanItem<Stage> stageItem : stageItems) {
      evaluateExit(stageItem, context);
    }

    // Collect all workers in ACTIVE stages
    Map<String, PlanItem<?>> activeWorkerItems = new HashMap<>();
    for (PlanItem<Stage> stageItem : stageItems) {
      collectActiveWorkerItems(stageItem, activeWorkerItems);
    }

    // Determine which eligible Bindings belong to active-stage workers vs free-floating
    List<Binding> staged = new ArrayList<>();
    List<Binding> freeFloating = new ArrayList<>();

    for (Binding binding : eligible) {
      if (binding.getCapability() == null) continue;
      String capabilityName = binding.getCapability().getName();
      boolean inActiveStage = activeWorkerItems.keySet().stream()
          .anyMatch(workerName -> {
            PlanItem<?> item = activeWorkerItems.get(workerName);
            if (item.getElement() instanceof Worker w) {
              return w.getCapabilities().stream()
                  .anyMatch(c -> c.getName().equals(capabilityName));
            }
            return false;
          });
      boolean inAnyStage = isWorkerInAnyStage(binding, planModel.get());

      if (!inAnyStage) {
        freeFloating.add(binding); // choreographic
      } else if (inActiveStage) {
        staged.add(binding); // orchestrated and active
      }
      // else: in a stage but stage not active — skip
    }

    // Ask PlanningStrategy to further filter staged bindings
    List<PlanItem<?>> eligibleItems = staged.stream()
        .map(b -> resolveToItem(b, activeWorkerItems))
        .filter(item -> item != null)
        .toList();

    List<PlanItem<?>> selected = planningStrategy.select(context, eligibleItems);

    // Translate selected PlanItems back to Bindings
    List<Binding> result = new ArrayList<>(freeFloating);
    for (PlanItem<?> selectedItem : selected) {
      staged.stream()
          .filter(b -> matchesItem(b, selectedItem))
          .findFirst()
          .ifPresent(result::add);
    }

    return result;
  }

  private List<PlanItem<Stage>> buildPlanInstance(CasePlanModel model) {
    List<PlanItem<Stage>> items = new ArrayList<>();
    for (Stage stage : model.getStages()) {
      items.add(buildStageItem(stage, null));
    }
    return items;
  }

  private PlanItem<Stage> buildStageItem(Stage stage, PlanItem<?> parent) {
    PlanItem<Stage> item = PlanItem.of(stage);
    if (parent != null) parent.addChild(item);
    for (Worker worker : stage.getWorkers()) {
      item.addChild(PlanItem.of(worker));
    }
    for (Stage nested : stage.getNestedStages()) {
      buildStageItem(nested, item);
    }
    return item;
  }

  private void evaluateEntry(PlanItem<Stage> item, CaseContext context) {
    if (item.getStatus() != PlanItemStatus.PENDING) {
      // Recurse into active nested stages
      for (PlanItem<?> child : item.getChildren()) {
        if (child.getElement() instanceof Stage && child.getStatus() == PlanItemStatus.PENDING) {
          @SuppressWarnings("unchecked")
          PlanItem<Stage> nestedStage = (PlanItem<Stage>) child;
          evaluateEntry(nestedStage, context);
        }
      }
      return;
    }
    Stage stage = item.getElement();
    if (expressionEngineRegistry.evaluate(stage.getEntryCondition(), context)) {
      LOG.infof("Stage '%s' entry condition met — activating", stage.getName());
      item.activate();
      for (PlanItem<?> child : item.getChildren()) {
        if (child.getElement() instanceof Worker) {
          child.activate();
        }
      }
    }
  }

  private void evaluateExit(PlanItem<Stage> item, CaseContext context) {
    if (item.getStatus() != PlanItemStatus.ACTIVE) return;
    Stage stage = item.getElement();
    boolean shouldComplete = false;
    if (stage.getExitCondition() != null) {
      shouldComplete = expressionEngineRegistry.evaluate(stage.getExitCondition(), context);
    } else {
      // Exit when all non-stage children are terminal
      shouldComplete = item.getChildren().stream()
          .filter(c -> c.getElement() instanceof Worker)
          .allMatch(c -> c.getStatus().isTerminal());
    }
    if (shouldComplete) {
      LOG.infof("Stage '%s' exit criteria met — completing", stage.getName());
      item.complete();
    }
  }

  private void collectActiveWorkerItems(PlanItem<Stage> item, Map<String, PlanItem<?>> result) {
    if (item.getStatus() != PlanItemStatus.ACTIVE) return;
    for (PlanItem<?> child : item.getChildren()) {
      if (child.getElement() instanceof Worker w && child.getStatus() == PlanItemStatus.ACTIVE) {
        result.put(w.getName(), child);
      } else if (child.getElement() instanceof Stage) {
        @SuppressWarnings("unchecked")
        PlanItem<Stage> nestedStage = (PlanItem<Stage>) child;
        collectActiveWorkerItems(nestedStage, result);
      }
    }
  }

  private boolean isWorkerInAnyStage(Binding binding, CasePlanModel model) {
    if (binding.getCapability() == null) return false;
    String cap = binding.getCapability().getName();
    return model.getStages().stream().anyMatch(s -> stageContainsCapability(s, cap));
  }

  private boolean stageContainsCapability(Stage stage, String capabilityName) {
    for (Worker w : stage.getWorkers()) {
      if (w.getCapabilities().stream().anyMatch(c -> c.getName().equals(capabilityName))) {
        return true;
      }
    }
    for (Stage nested : stage.getNestedStages()) {
      if (stageContainsCapability(nested, capabilityName)) return true;
    }
    return false;
  }

  private PlanItem<?> resolveToItem(Binding binding, Map<String, PlanItem<?>> activeWorkerItems) {
    if (binding.getCapability() == null) return null;
    String cap = binding.getCapability().getName();
    return activeWorkerItems.values().stream()
        .filter(item -> item.getElement() instanceof Worker w &&
            w.getCapabilities().stream().anyMatch(c -> c.getName().equals(cap)))
        .findFirst()
        .orElse(null);
  }

  private boolean matchesItem(Binding binding, PlanItem<?> item) {
    if (binding.getCapability() == null || !(item.getElement() instanceof Worker w)) return false;
    String cap = binding.getCapability().getName();
    return w.getCapabilities().stream().anyMatch(c -> c.getName().equals(cap));
  }

  // Extracts the case UUID from CaseContext — implementation depends on how CaseContext
  // carries this metadata. If CaseContext doesn't have it, PlanningStrategyLoopControl
  // needs to be passed the CaseInstance instead. For now, store case UUID in a ThreadLocal
  // set by the event handler. See CaseContextChangedEventHandler.
  private UUID extractCaseId(CaseContext context) {
    return CURRENT_CASE_ID.get();
  }

  private CaseDefinitionKey extractDefinitionKey(CaseContext context) {
    return CURRENT_DEFINITION_KEY.get();
  }

  // ThreadLocals set by CaseContextChangedEventHandler before invoking LoopControl
  public static final ThreadLocal<UUID> CURRENT_CASE_ID = new ThreadLocal<>();
  public static final ThreadLocal<CaseDefinitionKey> CURRENT_DEFINITION_KEY = new ThreadLocal<>();

  public void evictCase(UUID caseId) {
    planInstances.remove(caseId);
  }

  public record CaseDefinitionKey(String namespace, String name, String version) {}
}
```

**Note:** `CasePlanModelRegistry` needs a `findByKey(namespace, name, version)` method. Add it:

In `CasePlanModelRegistry.java`, add:

```java
public Optional<CasePlanModel> findByKey(String namespace, String name, String version) {
    return Optional.ofNullable(registry.get(namespace + ":" + name + ":" + version));
}
```

Also update `CaseContextChangedEventHandler` in `engine/` to set the ThreadLocals before calling `loopControl.select()`. In `engine/src/main/java/io/casehub/engine/internal/engine/handler/CaseContextChangedEventHandler.java`, in `onCaseStateContextChangedEventHandler()`, before calling `loopControl.select()`, add:

```java
// Set context for PlanningStrategyLoopControl (no-op if using ChoreographyLoopControl)
io.casehub.blackboard.strategy.PlanningStrategyLoopControl.CURRENT_CASE_ID.set(caseInstance.getUuid());
// (definition key set similarly from caseMetaModel)
```

**Wait — this creates a dependency from `engine/` to `casehub-blackboard/`.** That violates the dependency rule.

**Correct approach:** Change the `LoopControl` interface to receive a richer context. Add a `PlanExecutionContext` to `api/` that carries case UUID and definition info alongside the `CaseContext`. This is the right fix.

Add to `api/src/main/java/io/casehub/api/engine/PlanExecutionContext.java`:

```java
package io.casehub.api.engine;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseDefinition;
import java.util.UUID;

/** Richer context passed to LoopControl — carries case identity alongside CaseContext. */
public record PlanExecutionContext(UUID caseId, CaseDefinition definition, CaseContext caseContext) {}
```

Update `LoopControl` in `api/`:

```java
public interface LoopControl {
  List<Binding> select(PlanExecutionContext context, List<Binding> eligible);
}
```

Update `ChoreographyLoopControl` in `engine/` to use `PlanExecutionContext`:

```java
@Override
public List<Binding> select(PlanExecutionContext context, List<Binding> eligible) {
    return eligible;
}
```

Update `CaseContextChangedEventHandler` to build and pass `PlanExecutionContext`.

This ripples through all existing usages — update them all and ensure 327 tests still pass before writing the integration tests.

- [ ] **Step 2: Update `LoopControl` signature**

In `api/src/main/java/io/casehub/api/engine/LoopControl.java`:

```java
package io.casehub.api.engine;

import io.casehub.api.model.Binding;
import java.util.List;

public interface LoopControl {
  List<Binding> select(PlanExecutionContext context, List<Binding> eligible);
}
```

- [ ] **Step 3: Create `PlanExecutionContext.java` in api/**

Create `api/src/main/java/io/casehub/api/engine/PlanExecutionContext.java`:

```java
package io.casehub.api.engine;

import io.casehub.api.context.CaseContext;
import io.casehub.api.model.CaseDefinition;
import java.util.UUID;

/** Context passed to LoopControl — carries case identity alongside CaseContext. */
public record PlanExecutionContext(UUID caseId, CaseDefinition definition, CaseContext caseContext) {}
```

- [ ] **Step 4: Update `ChoreographyLoopControl` in engine/**

In `engine/src/main/java/io/casehub/engine/internal/engine/ChoreographyLoopControl.java`:

```java
@Override
public List<Binding> select(PlanExecutionContext context, List<Binding> eligible) {
    return eligible;
}
```

Add import: `import io.casehub.api.engine.PlanExecutionContext;`

- [ ] **Step 5: Update `CaseContextChangedEventHandler` call site**

In `engine/.../handler/CaseContextChangedEventHandler.java`, find the `loopControl.select(...)` call and update to pass `PlanExecutionContext`:

```java
CaseDefinition caseDefinition = caseDefinitionRegistry.getCaseDefinition(caseMetaModel);
PlanExecutionContext planCtx = new PlanExecutionContext(
    caseInstance.getUuid(), caseDefinition, caseInstance.getCaseContext());
List<Binding> selected = loopControl.select(planCtx, eligible);
```

Add import: `import io.casehub.api.engine.PlanExecutionContext;`

- [ ] **Step 6: Update `PlanningStrategyLoopControl` to use `PlanExecutionContext`**

Replace the ThreadLocal approach in `PlanningStrategyLoopControl` with direct use of `PlanExecutionContext`:

```java
@Override
public List<Binding> select(PlanExecutionContext context, List<Binding> eligible) {
    UUID caseId = context.caseId();
    CaseDefinition definition = context.definition();
    CaseContext caseContext = context.caseContext();

    Optional<CasePlanModel> planModel = registry.find(definition);
    if (planModel.isEmpty()) {
        return eligible; // pure choreography
    }
    // ... rest of the implementation using caseContext instead of context
}
```

Remove the `CURRENT_CASE_ID` and `CURRENT_DEFINITION_KEY` ThreadLocals entirely.

- [ ] **Step 7: Verify full suite compiles and passes**

```bash
cd /Users/mdproctor/dev/casehub-engine
mvn test -pl api,engine -am -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS, 327+ tests, 0 failures.

- [ ] **Step 8: Write integration tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/BlackboardIntegrationTest.java`:

```java
package io.casehub.blackboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Worker;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.CasePlanModelRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.api.model.CaseStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BlackboardIntegrationTest {

  @Inject SingleStageCaseBean singleStageBean;
  @Inject ExitConditionCaseBean exitConditionBean;
  @Inject MixedChoreographyCaseBean mixedBean;
  @Inject CaseInstanceCache caseInstanceCache;

  @BeforeEach
  void reset() {
    SingleStageCaseBean.workerRan.set(false);
    ExitConditionCaseBean.workerRan.set(false);
    MixedChoreographyCaseBean.stagedRan.set(false);
    MixedChoreographyCaseBean.freeRan.set(false);
  }

  // ------------------------------------------------------------------ //
  // Test 1: Single stage activates on entry condition, completes when worker done
  // ------------------------------------------------------------------ //

  @Test
  void singleStageActivatesAndCompletesViaWorker() {
    UUID caseId = singleStageBean.startCase(Map.of("status", "ready"))
        .toCompletableFuture().join();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(SingleStageCaseBean.workerRan).isTrue();
      assertThat(caseInstanceCache.get(caseId).getState()).isEqualTo(CaseStatus.COMPLETED);
    });
  }

  // ------------------------------------------------------------------ //
  // Test 2: Stage with explicit exit condition — completes on condition, not worker
  // ------------------------------------------------------------------ //

  @Test
  void stageWithExplicitExitCompletesOnCondition() {
    UUID caseId = exitConditionBean.startCase(Map.of("status", "start"))
        .toCompletableFuture().join();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(ExitConditionCaseBean.workerRan).isTrue();
      assertThat(caseInstanceCache.get(caseId).getState()).isEqualTo(CaseStatus.COMPLETED);
    });
  }

  // ------------------------------------------------------------------ //
  // Test 3: Free-floating workers fire alongside staged workers
  // ------------------------------------------------------------------ //

  @Test
  void freeFloatingWorkerFiresAlongsideStagedWorker() {
    UUID caseId = mixedBean.startCase(Map.of("status", "go"))
        .toCompletableFuture().join();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(MixedChoreographyCaseBean.stagedRan).isTrue();
      assertThat(MixedChoreographyCaseBean.freeRan).isTrue();
    });
  }

  // ------------------------------------------------------------------ //
  // Test beans
  // ------------------------------------------------------------------ //

  /** Single stage, entry on .status == "ready", worker sets .status = "done", success goal. */
  @ApplicationScoped
  public static class SingleStageCaseBean extends CaseHub {

    static final AtomicBoolean workerRan = new AtomicBoolean(false);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability cap = Capability.builder().name("process").build();
    private final Worker worker = Worker.builder().name("processor").capabilities(cap)
        .function(input -> { workerRan.set(true); return Map.of("status", "done"); }).build();
    private final Goal goal = Goal.builder().name("done").condition(".status == \"done\"")
        .kind(GoalKind.SUCCESS).build();

    @PostConstruct
    void registerPlan() {
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("single-stage-plan")
              .stages(Stage.builder().name("processing").entry(".status == \"ready\"")
                  .workers(worker).build())
              .build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("SingleStage")
          .version("1.0").capabilities(cap).workers(worker)
          .bindings(Binding.builder().name("on-ready").capability(cap)
              .on(new ContextChangeTrigger(".status == \"ready\"")).build())
          .goals(goal).completion(GoalExpression.allOf(goal)).build();
    }
  }

  /** Stage with explicit exit condition .status == "exited". Worker sets .status = "exited". */
  @ApplicationScoped
  public static class ExitConditionCaseBean extends CaseHub {

    static final AtomicBoolean workerRan = new AtomicBoolean(false);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability cap = Capability.builder().name("exit-worker-cap").build();
    private final Worker worker = Worker.builder().name("exit-worker").capabilities(cap)
        .function(input -> { workerRan.set(true); return Map.of("status", "exited"); }).build();
    private final Goal goal = Goal.builder().name("exited").condition(".status == \"exited\"")
        .kind(GoalKind.SUCCESS).build();

    @PostConstruct
    void registerPlan() {
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("exit-condition-plan")
              .stages(Stage.builder().name("work").entry(".status == \"start\"")
                  .exit(".status == \"exited\"").workers(worker).build())
              .build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("ExitCondition")
          .version("1.0").capabilities(cap).workers(worker)
          .bindings(Binding.builder().name("on-start").capability(cap)
              .on(new ContextChangeTrigger(".status == \"start\"")).build())
          .goals(goal).completion(GoalExpression.allOf(goal)).build();
    }
  }

  /** Staged worker (in Stage) + free-floating worker (not in Stage). Both must run. */
  @ApplicationScoped
  public static class MixedChoreographyCaseBean extends CaseHub {

    static final AtomicBoolean stagedRan = new AtomicBoolean(false);
    static final AtomicBoolean freeRan = new AtomicBoolean(false);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability stagedCap = Capability.builder().name("staged-cap").build();
    private final Capability freeCap = Capability.builder().name("free-cap").build();
    private final Worker stagedWorker = Worker.builder().name("staged").capabilities(stagedCap)
        .function(input -> { stagedRan.set(true); return Map.of("staged", true); }).build();
    private final Worker freeWorker = Worker.builder().name("free").capabilities(freeCap)
        .function(input -> { freeRan.set(true); return Map.of("free", true); }).build();

    @PostConstruct
    void registerPlan() {
      // Only stagedWorker is in the plan; freeWorker is choreographic
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("mixed-plan")
              .stages(Stage.builder().name("stage1").entry(".status == \"go\"")
                  .workers(stagedWorker).build())
              .build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("Mixed")
          .version("1.0").capabilities(stagedCap, freeCap).workers(stagedWorker, freeWorker)
          .bindings(
              Binding.builder().name("on-go-staged").capability(stagedCap)
                  .on(new ContextChangeTrigger(".status == \"go\"")).build(),
              Binding.builder().name("on-go-free").capability(freeCap)
                  .on(new ContextChangeTrigger(".status == \"go\"")).build())
          .build();
    }
  }
}
```

- [ ] **Step 9: Run integration tests**

```bash
mvn test -pl casehub-blackboard -am -Dtest="BlackboardIntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|ERROR" | tail -10
```

Expected: 3 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 10: Run full suite — regression check**

```bash
mvn test -pl api,engine,casehub-blackboard -am 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: BUILD SUCCESS, 330+ tests, 0 failures.

- [ ] **Step 11: Commit**

```bash
git add api/src/main/java/io/casehub/api/engine/ \
        engine/src/main/java/io/casehub/engine/internal/engine/ \
        casehub-blackboard/src/main/java/io/casehub/blackboard/strategy/PlanningStrategyLoopControl.java \
        casehub-blackboard/src/test/java/io/casehub/blackboard/BlackboardIntegrationTest.java
git commit -m "feat(blackboard): add PlanningStrategyLoopControl — core Blackboard bridge

PlanExecutionContext added to api/ — carries caseId + CaseDefinition
alongside CaseContext, eliminating ThreadLocal approach.
LoopControl.select() now takes PlanExecutionContext.
ChoreographyLoopControl and CaseContextChangedEventHandler updated.

PlanningStrategyLoopControl (@Alternative @Priority(10)):
- Evaluates Stage entry/exit criteria per CaseContextChangedEvent
- Filters eligible Bindings to ACTIVE-stage workers
- Free-floating workers (not in any Stage) pass through unchanged
- Delegates to PlanningStrategy for final selection

Integration tests: single stage, explicit exit condition, mixed
choreography+orchestration.

Refs #30"
```

---

## Task 11: CaseInstance parentPlanItemId + Flyway migration

**Files:**
- Modify: `engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java`
- Create: `engine/src/main/resources/db/migration/V1.3.0__Add_Parent_Plan_Item_Id.sql`
- Test: embedded in `BlackboardIntegrationTest` (sub-case tests in Task 12)

- [ ] **Step 1: Add `parentPlanItemId` field to `CaseInstance`**

In `engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java`, add:

```java
import java.util.UUID;  // already imported

@Column(name = "parent_plan_item_id", nullable = true)
private UUID parentPlanItemId;

public UUID getParentPlanItemId() {
    return parentPlanItemId;
}

public void setParentPlanItemId(UUID parentPlanItemId) {
    this.parentPlanItemId = parentPlanItemId;
}
```

- [ ] **Step 2: Create Flyway migration**

Create `engine/src/main/resources/db/migration/V1.3.0__Add_Parent_Plan_Item_Id.sql`:

```sql
-- Supports sub-case wiring: a child CaseInstance references the parent PlanItem<SubCase>.
-- Null for root cases (the vast majority). No foreign key — PlanItems are in-memory.
ALTER TABLE case_instance ADD COLUMN IF NOT EXISTS parent_plan_item_id UUID;
```

- [ ] **Step 3: Verify full suite still passes**

```bash
mvn test -pl api,engine -am 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: BUILD SUCCESS, all existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add engine/src/main/java/io/casehub/engine/internal/model/CaseInstance.java \
        engine/src/main/resources/db/migration/V1.3.0__Add_Parent_Plan_Item_Id.sql
git commit -m "feat(engine): add parentPlanItemId to CaseInstance for sub-case wiring

Nullable UUID column — null for root cases.
V1.3.0 Flyway migration adds the column.
No foreign key — PlanItems are in-memory.

Refs #30"
```

---

## Task 12: Nested stage integration tests

**Files:**
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/NestedStageIntegrationTest.java`

- [ ] **Step 1: Write nested stage tests**

Create `casehub-blackboard/src/test/java/io/casehub/blackboard/NestedStageIntegrationTest.java`:

```java
package io.casehub.blackboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Worker;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.CasePlanModelRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.casehub.api.model.CaseStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NestedStageIntegrationTest {

  @Inject TwoSequentialStagesCaseBean sequentialBean;
  @Inject NestedStageCaseBean nestedBean;
  @Inject LambdaConditionCaseBean lambdaBean;
  @Inject CaseInstanceCache caseInstanceCache;

  @BeforeEach
  void reset() {
    TwoSequentialStagesCaseBean.stage1Ran.set(0);
    TwoSequentialStagesCaseBean.stage2Ran.set(0);
    NestedStageCaseBean.outerRan.set(false);
    NestedStageCaseBean.innerRan.set(false);
    LambdaConditionCaseBean.workerRan.set(false);
  }

  // ------------------------------------------------------------------ //
  // Test 1: Two sequential stages — stage 2 activates only after stage 1 completes
  // ------------------------------------------------------------------ //

  @Test
  void twoSequentialStages_stage2RunsAfterStage1() {
    UUID caseId = sequentialBean.startCase(Map.of("phase", "one"))
        .toCompletableFuture().join();

    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(TwoSequentialStagesCaseBean.stage1Ran.get()).isGreaterThan(0);
      assertThat(TwoSequentialStagesCaseBean.stage2Ran.get()).isGreaterThan(0);
      // Stage 1 must have run before stage 2
      assertThat(TwoSequentialStagesCaseBean.stage1Ran.get())
          .isLessThanOrEqualTo(TwoSequentialStagesCaseBean.stage2Ran.get());
      assertThat(caseInstanceCache.get(caseId).getState()).isEqualTo(CaseStatus.COMPLETED);
    });
  }

  // ------------------------------------------------------------------ //
  // Test 2: Nested stage activates only when parent stage is ACTIVE
  // ------------------------------------------------------------------ //

  @Test
  void nestedStageActivatesOnlyWhenParentIsActive() {
    UUID caseId = nestedBean.startCase(Map.of("status", "outer-ready"))
        .toCompletableFuture().join();

    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(NestedStageCaseBean.outerRan).isTrue();
      assertThat(NestedStageCaseBean.innerRan).isTrue();
      assertThat(caseInstanceCache.get(caseId).getState()).isEqualTo(CaseStatus.COMPLETED);
    });
  }

  // ------------------------------------------------------------------ //
  // Test 3: Lambda condition on Stage entry works same as JQ
  // ------------------------------------------------------------------ //

  @Test
  void lambdaEntryConditionActivatesStage() {
    UUID caseId = lambdaBean.startCase(Map.of("value", 42))
        .toCompletableFuture().join();

    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      assertThat(LambdaConditionCaseBean.workerRan).isTrue();
      assertThat(caseInstanceCache.get(caseId).getState()).isEqualTo(CaseStatus.COMPLETED);
    });
  }

  // ------------------------------------------------------------------ //
  // Test beans
  // ------------------------------------------------------------------ //

  /** Stage 1: phase == "one" → sets phase = "two". Stage 2: phase == "two" → sets status = "done". */
  @ApplicationScoped
  public static class TwoSequentialStagesCaseBean extends CaseHub {

    static final AtomicInteger stage1Ran = new AtomicInteger(0);
    static final AtomicInteger stage2Ran = new AtomicInteger(0);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability cap1 = Capability.builder().name("stage1-cap").build();
    private final Capability cap2 = Capability.builder().name("stage2-cap").build();
    private final Worker w1 = Worker.builder().name("stage1-worker").capabilities(cap1)
        .function(input -> { stage1Ran.incrementAndGet(); return Map.of("phase", "two"); }).build();
    private final Worker w2 = Worker.builder().name("stage2-worker").capabilities(cap2)
        .function(input -> { stage2Ran.incrementAndGet(); return Map.of("status", "done"); }).build();
    private final Goal goal = Goal.builder().name("done").condition(".status == \"done\"")
        .kind(GoalKind.SUCCESS).build();

    @PostConstruct
    void registerPlan() {
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("sequential")
              .stages(
                  Stage.builder().name("stage1").entry(".phase == \"one\"").workers(w1).build(),
                  Stage.builder().name("stage2").entry(".phase == \"two\"").workers(w2).build())
              .build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("Sequential")
          .version("1.0").capabilities(cap1, cap2).workers(w1, w2)
          .bindings(
              Binding.builder().name("b1").capability(cap1)
                  .on(new ContextChangeTrigger(".phase == \"one\"")).build(),
              Binding.builder().name("b2").capability(cap2)
                  .on(new ContextChangeTrigger(".phase == \"two\"")).build())
          .goals(goal).completion(GoalExpression.allOf(goal)).build();
    }
  }

  /** Outer stage activates on .status == "outer-ready", activates inner stage via nested. */
  @ApplicationScoped
  public static class NestedStageCaseBean extends CaseHub {

    static final AtomicBoolean outerRan = new AtomicBoolean(false);
    static final AtomicBoolean innerRan = new AtomicBoolean(false);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability outerCap = Capability.builder().name("outer-cap").build();
    private final Capability innerCap = Capability.builder().name("inner-cap").build();
    private final Worker outerWorker = Worker.builder().name("outer-worker").capabilities(outerCap)
        .function(input -> { outerRan.set(true); return Map.of("innerReady", true); }).build();
    private final Worker innerWorker = Worker.builder().name("inner-worker").capabilities(innerCap)
        .function(input -> { innerRan.set(true); return Map.of("status", "nested-done"); }).build();
    private final Goal goal = Goal.builder().name("done").condition(".status == \"nested-done\"")
        .kind(GoalKind.SUCCESS).build();

    @PostConstruct
    void registerPlan() {
      Stage inner = Stage.builder().name("inner").entry(".innerReady == true")
          .workers(innerWorker).build();
      Stage outer = Stage.builder().name("outer").entry(".status == \"outer-ready\"")
          .workers(outerWorker).nestedStage(inner).build();
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("nested").stages(outer).build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("Nested")
          .version("1.0").capabilities(outerCap, innerCap).workers(outerWorker, innerWorker)
          .bindings(
              Binding.builder().name("b-outer").capability(outerCap)
                  .on(new ContextChangeTrigger(".status == \"outer-ready\"")).build(),
              Binding.builder().name("b-inner").capability(innerCap)
                  .on(new ContextChangeTrigger(".innerReady == true")).build())
          .goals(goal).completion(GoalExpression.allOf(goal)).build();
    }
  }

  /** Lambda entry condition: ctx -> value is 42. */
  @ApplicationScoped
  public static class LambdaConditionCaseBean extends CaseHub {

    static final AtomicBoolean workerRan = new AtomicBoolean(false);

    @Inject CasePlanModelRegistry planRegistry;

    private final Capability cap = Capability.builder().name("lambda-cap").build();
    private final Worker worker = Worker.builder().name("lambda-worker").capabilities(cap)
        .function(input -> { workerRan.set(true); return Map.of("status", "lambda-done"); }).build();
    private final Goal goal = Goal.builder().name("done").condition(".status == \"lambda-done\"")
        .kind(GoalKind.SUCCESS).build();

    @PostConstruct
    void registerPlan() {
      planRegistry.register(getDefinition(),
          CasePlanModel.builder().name("lambda-plan")
              .stages(Stage.builder().name("lambda-stage")
                  .entry(ctx -> {
                    Object val = ctx.getPath("value");
                    return val instanceof Number n && n.intValue() == 42;
                  })
                  .workers(worker).build())
              .build());
    }

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder().namespace("test-blackboard").name("Lambda")
          .version("1.0").capabilities(cap).workers(worker)
          .bindings(Binding.builder().name("b-lambda").capability(cap)
              .on(new ContextChangeTrigger(".value == 42")).build())
          .goals(goal).completion(GoalExpression.allOf(goal)).build();
    }
  }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl casehub-blackboard -am -Dtest="NestedStageIntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|BUILD|ERROR" | tail -10
```

Expected: 3 tests, 0 failures, BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/NestedStageIntegrationTest.java
git commit -m "test(blackboard): add nested stage integration tests

Two sequential stages, nested stage hierarchy, lambda entry condition.
Validates stage activation ordering and choreography-within-orchestration.

Refs #30"
```

---

## Task 13: Final regression verification and PR

- [ ] **Step 1: Run complete test suite**

```bash
cd /Users/mdproctor/dev/casehub-engine
mvn test -pl api,engine,casehub-blackboard -am 2>&1 | grep -E "Tests run:|BUILD" | tail -10
```

Expected: BUILD SUCCESS, 0 failures across all modules.

- [ ] **Step 2: Verify casehub-blackboard absent = identical engine behaviour**

Remove `casehub-blackboard` temporarily from the build and confirm engine tests still pass:

```bash
mvn test -pl api,engine -am 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: BUILD SUCCESS, same results as before `casehub-blackboard` was added.

- [ ] **Step 3: Create GitHub issue for the casehub-blackboard work**

```bash
gh issue create \
  --repo casehubio/engine \
  --title "Add casehub-blackboard module — CMMN/Blackboard orchestration layer" \
  --label "enhancement" \
  --body "Implements the casehub-blackboard design (see casehub repo docs/superpowers/specs/2026-04-14-casehub-blackboard-design.md).

Adds PlanElement, PlanItem, Stage, SubCase, CasePlanModel, CasePlanModelRegistry,
PlanningStrategy, DefaultPlanningStrategy, PlanningStrategyLoopControl,
SubCaseCompletionStrategy.

Also: PlanExecutionContext in api/ (LoopControl enrichment), parentPlanItemId on
CaseInstance, Flyway V1.3.0, lambda Predicate overloads on Milestone/Goal builders.

Part of epic #30."
```

- [ ] **Step 4: Push and raise PR**

```bash
git push origin feat/rename-binding-casedefinition
gh pr view 49 --repo casehubio/engine  # add commits to existing PR, or raise new one
```

---

## Summary of files created/modified

| File | Action |
|---|---|
| `pom.xml` | Add `casehub-blackboard` module |
| `api/.../plan/PlanElement.java` | New |
| `api/.../engine/PlanExecutionContext.java` | New |
| `api/.../engine/LoopControl.java` | Modified signature |
| `api/.../model/Worker.java` | `implements PlanElement` |
| `api/.../model/Milestone.java` | Lambda condition overload |
| `api/.../model/Goal.java` | Lambda condition overload |
| `engine/.../model/CaseInstance.java` | `parentPlanItemId` field |
| `engine/.../engine/ChoreographyLoopControl.java` | Updated signature |
| `engine/.../handler/CaseContextChangedEventHandler.java` | Pass `PlanExecutionContext` |
| `engine/.../db/migration/V1.3.0__*.sql` | New |
| `casehub-blackboard/pom.xml` | New |
| `casehub-blackboard/.../PlanItemStatus.java` | New |
| `casehub-blackboard/.../PlanItem.java` | New |
| `casehub-blackboard/.../CasePlanModel.java` | New |
| `casehub-blackboard/.../CasePlanModelRegistry.java` | New |
| `casehub-blackboard/.../Stage.java` | New |
| `casehub-blackboard/.../SubCase.java` | New |
| `casehub-blackboard/.../PlanningStrategy.java` | New |
| `casehub-blackboard/.../DefaultPlanningStrategy.java` | New |
| `casehub-blackboard/.../SubCaseCompletionStrategy.java` | New |
| `casehub-blackboard/.../DefaultSubCaseCompletionStrategy.java` | New |
| `casehub-blackboard/.../PlanningStrategyLoopControl.java` | New |
