# Blackboard PR-H: SubCase Data Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port treblereel's SubCase data model — a stage item representing a child case to be launched as part of a stage's work. Provides feature parity. Full engine integration (actually launching child cases) stays in the future epic.

**Architecture:** `SubCase` is a POJO identifying a child case definition (namespace, name, version). `SubCaseCompletionStrategy` maps child `CaseStatus` to stage item completion. `DefaultSubCaseCompletionStrategy` provides the standard mapping. `CasePlanModel` gains `addSubCase` / `getSubCases` methods.

**Tech Stack:** Java 21, JUnit 5, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`
**Branch:** create `feat/bb-qa-h-subcase` from `feat/bb-qa-g-strict-lifecycle`
**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java` | NEW — SubCase POJO |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCaseCompletionStrategy.java` | NEW — completion strategy interface |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/DefaultSubCaseCompletionStrategy.java` | NEW — standard CaseStatus → stage item mapping |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java` | Add `addSubCase`, `getSubCases` |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java` | Implement SubCase management |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java` | NEW — SubCase + strategy tests |

---

## Task 1: SubCase, SubCaseCompletionStrategy, DefaultSubCaseCompletionStrategy

- [ ] **Step 1: Write failing tests first**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java
package io.casehub.blackboard.stage;

import io.casehub.api.model.CaseStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SubCase and DefaultSubCaseCompletionStrategy.
 * See casehubio/engine#76.
 */
class SubCaseTest {

    @Test
    void subCase_namespace_name_version_are_required() {
        assertThatThrownBy(() -> SubCase.builder().name("n").version("v").build())
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SubCase.builder().namespace("ns").version("v").build())
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SubCase.builder().namespace("ns").name("n").build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void subCase_fields_are_retained() {
        SubCase sc = SubCase.builder()
            .namespace("io.casehub")
            .name("loan-application")
            .version("1.0.0")
            .build();

        assertThat(sc.namespace()).isEqualTo("io.casehub");
        assertThat(sc.name()).isEqualTo("loan-application");
        assertThat(sc.version()).isEqualTo("1.0.0");
    }

    @Test
    void default_completion_strategy_maps_completed_to_completed() {
        DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();
        assertThat(strategy.mapToStageItemStatus(CaseStatus.COMPLETED))
            .isEqualTo(SubCaseCompletionStrategy.ItemStatus.COMPLETED);
    }

    @Test
    void default_completion_strategy_maps_faulted_to_faulted() {
        DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();
        assertThat(strategy.mapToStageItemStatus(CaseStatus.FAULTED))
            .isEqualTo(SubCaseCompletionStrategy.ItemStatus.FAULTED);
    }

    @Test
    void default_completion_strategy_maps_cancelled_to_terminated() {
        DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();
        assertThat(strategy.mapToStageItemStatus(CaseStatus.CANCELLED))
            .isEqualTo(SubCaseCompletionStrategy.ItemStatus.TERMINATED);
    }

    @Test
    void default_completion_strategy_maps_waiting_to_terminated() {
        DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();
        assertThat(strategy.mapToStageItemStatus(CaseStatus.WAITING))
            .isEqualTo(SubCaseCompletionStrategy.ItemStatus.TERMINATED);
    }

    @Test
    void default_completion_strategy_maps_suspended_to_terminated() {
        DefaultSubCaseCompletionStrategy strategy = new DefaultSubCaseCompletionStrategy();
        assertThat(strategy.mapToStageItemStatus(CaseStatus.SUSPENDED))
            .isEqualTo(SubCaseCompletionStrategy.ItemStatus.TERMINATED);
    }

    @Test
    void default_strategy_is_used_when_not_specified() {
        SubCase sc = SubCase.builder()
            .namespace("ns").name("n").version("v").build();
        assertThat(sc.completionStrategy())
            .isInstanceOf(DefaultSubCaseCompletionStrategy.class);
    }

    @Test
    void custom_completion_strategy_is_retained() {
        SubCaseCompletionStrategy custom = status -> SubCaseCompletionStrategy.ItemStatus.COMPLETED;
        SubCase sc = SubCase.builder()
            .namespace("ns").name("n").version("v")
            .completionStrategy(custom)
            .build();
        assertThat(sc.completionStrategy()).isSameAs(custom);
    }
}
```

- [ ] **Step 2: Run — verify FAIL**

- [ ] **Step 3: Implement `SubCaseCompletionStrategy`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCaseCompletionStrategy.java
package io.casehub.blackboard.stage;

import io.casehub.api.model.CaseStatus;

/**
 * Maps a child {@link CaseStatus} to a stage item completion state.
 * Used by the blackboard control layer when a sub-case reaches a terminal state.
 * Full engine integration (launching and monitoring sub-cases) is in the future epic.
 * See casehubio/engine#76.
 */
public interface SubCaseCompletionStrategy {

    /** The stage-item-level status resulting from a sub-case terminal state. */
    enum ItemStatus { COMPLETED, FAULTED, TERMINATED }

    ItemStatus mapToStageItemStatus(CaseStatus childCaseStatus);
}
```

- [ ] **Step 4: Implement `DefaultSubCaseCompletionStrategy`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/stage/DefaultSubCaseCompletionStrategy.java
package io.casehub.blackboard.stage;

import io.casehub.api.model.CaseStatus;

/**
 * Standard mapping from child {@link CaseStatus} to stage item status.
 * COMPLETED → COMPLETED; FAULTED → FAULTED; all others → TERMINATED.
 * See casehubio/engine#76.
 */
public class DefaultSubCaseCompletionStrategy implements SubCaseCompletionStrategy {

    @Override
    public ItemStatus mapToStageItemStatus(CaseStatus childCaseStatus) {
        return switch (childCaseStatus) {
            case COMPLETED -> ItemStatus.COMPLETED;
            case FAULTED -> ItemStatus.FAULTED;
            default -> ItemStatus.TERMINATED;
        };
    }
}
```

- [ ] **Step 5: Implement `SubCase`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java
package io.casehub.blackboard.stage;

import java.util.Objects;

/**
 * Identifies a child case definition to be launched as part of a stage's work.
 * Provides feature parity with treblereel's SubCase. Full engine integration
 * (launching and monitoring child cases) is tracked in the future epic.
 * See casehubio/engine#76.
 */
public class SubCase {

    private final String namespace;
    private final String name;
    private final String version;
    private final SubCaseCompletionStrategy completionStrategy;

    private SubCase(Builder builder) {
        this.namespace = Objects.requireNonNull(builder.namespace, "namespace must not be null");
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.version = Objects.requireNonNull(builder.version, "version must not be null");
        this.completionStrategy = builder.completionStrategy != null
            ? builder.completionStrategy
            : new DefaultSubCaseCompletionStrategy();
    }

    public String namespace() { return namespace; }
    public String name() { return name; }
    public String version() { return version; }
    public SubCaseCompletionStrategy completionStrategy() { return completionStrategy; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String namespace;
        private String name;
        private String version;
        private SubCaseCompletionStrategy completionStrategy;

        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder completionStrategy(SubCaseCompletionStrategy s) {
            this.completionStrategy = s; return this;
        }
        public SubCase build() { return new SubCase(this); }
    }
}
```

- [ ] **Step 6: Run tests — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=SubCaseTest 2>&1 | grep "Tests run"
```

Expected: 8 tests, 0 failures.

- [ ] **Step 7: Add SubCase management to `CasePlanModel` and `DefaultCasePlanModel`**

Add to `CasePlanModel.java` interface:
```java
// SubCase management — sub-cases to be launched as part of stage work.
// Full engine integration in future epic. See casehubio/engine#76.
void addSubCase(SubCase subCase);
List<SubCase> getSubCases();
```

Add to `DefaultCasePlanModel.java`:
```java
private final java.util.concurrent.CopyOnWriteArrayList<SubCase> subCases =
    new java.util.concurrent.CopyOnWriteArrayList<>();

@Override
public void addSubCase(SubCase subCase) {
    subCases.add(Objects.requireNonNull(subCase));
}

@Override
public List<SubCase> getSubCases() {
    return List.copyOf(subCases);
}
```

Add import: `import io.casehub.blackboard.stage.SubCase;`

- [ ] **Step 8: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -2
```

Expected: all previous tests still passing + 8 new SubCase tests.

- [ ] **Step 9: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCase.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/stage/SubCaseCompletionStrategy.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/stage/DefaultSubCaseCompletionStrategy.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/stage/SubCaseTest.java
git commit -m "feat(blackboard): SubCase data model — parity with prior implementation (casehubio/engine#76)

Ports SubCase, SubCaseCompletionStrategy, DefaultSubCaseCompletionStrategy
from the prior implementation. CasePlanModel gains addSubCase/getSubCases.
Full engine integration (launching child cases) tracked in future epic."
```
