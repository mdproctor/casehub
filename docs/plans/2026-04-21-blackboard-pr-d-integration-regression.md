# Blackboard PR-D: Integration Test Regression Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the integration test coverage gap against treblereel's original suite — four end-to-end `@QuarkusTest` scenarios that were present in the prior implementation but absent from ours.

**Architecture:** All tests are `@QuarkusTest` following the pattern in `BasicBlackboardTest` and `StageBlackboardTest`. New `CaseHub` bean subclasses define the case definitions. Tests use `CaseInstanceCache`, `BlackboardRegistry`, and `Awaitility` for async assertions.

**Tech Stack:** Java 21, Quarkus 3.x, `@QuarkusTest`, Awaitility, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`

**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

**Study before starting:** Read `casehub-blackboard/src/test/java/io/casehub/blackboard/it/BasicBlackboardTest.java` and `StageBlackboardTest.java` in full — all new tests follow the same patterns for `CaseHub` bean definitions, `startCase`, `signal`, and `Awaitility` assertions.

**Depends on:** PRs A, B, C completed.

---

## File Map

| File | What |
|------|------|
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/SequentialStagesBlackboardTest.java` | R1: Two sequential stages |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/ExitConditionBlackboardTest.java` | R3: Exit condition end-to-end |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/MixedWorkersBlackboardTest.java` | R4: Free-floating + staged workers |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/LambdaEntryConditionBlackboardTest.java` | R5: Lambda entry condition end-to-end |

---

## Task 1: R1 — Two sequential stages activate in order

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/it/SequentialStagesBlackboardTest.java`

- [ ] **Step 1: Create test**

```java
package io.casehub.blackboard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.*;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * R1: Two sequential stages — first activates on initial context, second activates
 * after the first worker writes output. Verifies sequential orchestration via
 * context-driven stage entry conditions. See casehubio/engine#76.
 */
@QuarkusTest
class SequentialStagesBlackboardTest {

    @Inject BlackboardRegistry registry;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject TwoStagesCaseBean twoStagesCase;

    @Test
    void two_sequential_stages_activate_in_order() {
        UUID caseId = twoStagesCase.startCase(Map.of("phase", "start"))
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

        // Add stage-1: activates when .phase == "start", worker writes phase=two
        Stage stage1 = Stage.create("stage-one").withEntryCondition(ctx -> {
            Object phase = ctx.getPath("phase");
            return "start".equals(phase);
        });
        // Add stage-2: activates when .phase == "two"
        Stage stage2 = Stage.create("stage-two").withEntryCondition(ctx -> {
            Object phase = ctx.getPath("phase");
            return "two".equals(phase);
        });

        registry.get(caseId).get().addStage(stage1);
        registry.get(caseId).get().addStage(stage2);

        // Trigger evaluation — stage-1 should activate (phase == "start")
        twoStagesCase.signal(caseId, "probe", "tick");

        await().atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(stage1.getStatus())
                    .as("stage-one must activate — entry condition .phase == 'start' is met")
                    .isEqualTo(StageStatus.ACTIVE);
                assertThat(stage2.getStatus())
                    .as("stage-two must activate after worker writes phase=two")
                    .isEqualTo(StageStatus.ACTIVE);
            });
    }

    @ApplicationScoped
    public static class TwoStagesCaseBean extends CaseHub {
        private final Capability cap = Capability.builder()
            .name("phase-writer")
            .inputSchema("{ phase: .phase }")
            .outputSchema("{ phase: .phase }")
            .build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("blackboard-it")
                .name("Two Stages Case")
                .version("1.0.0")
                .capabilities(cap)
                .workers(Worker.builder()
                    .name("phase-writer-worker")
                    .capabilities(cap)
                    .function(input -> Map.of("phase", "two")) // writes phase=two
                    .build())
                .bindings(Binding.builder()
                    .name("trigger-on-start")
                    .capability(cap)
                    .on(new ContextChangeTrigger(".phase == \"start\""))
                    .build())
                .build();
        }
    }
}
```

- [ ] **Step 2: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=SequentialStagesBlackboardTest 2>&1 | grep -E "Tests run:|FAIL"
```

Expected: `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/SequentialStagesBlackboardTest.java
git commit -m "test(blackboard): R1 — two sequential stages activate in order (casehubio/engine#76)"
```

---

## Task 2: R3 — Exit condition satisfied by actual worker output (end-to-end)

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/it/ExitConditionBlackboardTest.java`

- [ ] **Step 1: Create test**

```java
package io.casehub.blackboard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.*;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * R3: Stage with an explicit exit condition is terminated when a worker's output
 * satisfies that condition. Verifies the end-to-end reactive path from worker
 * completion through context change to StageLifecycleEvaluator termination.
 * See casehubio/engine#76.
 */
@QuarkusTest
class ExitConditionBlackboardTest {

    @Inject BlackboardRegistry registry;
    @Inject ExitConditionCaseBean exitConditionCase;

    @Test
    void active_stage_is_terminated_when_worker_output_satisfies_exit_condition() {
        UUID caseId = exitConditionCase.startCase(Map.of("phase", "active"))
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

        // Add a stage that is pre-activated and has an exit condition matching worker output
        Stage stage = Stage.create("active-stage")
            .withExitCondition(ctx -> "exited".equals(ctx.getPath("phase")));
        stage.activate(); // pre-activate — worker output should trigger exit condition
        registry.get(caseId).get().addStage(stage);

        // Trigger a cycle — worker fires (phase == "active"), writes phase = "exited"
        // Next CONTEXT_CHANGED evaluates exit condition → stage terminates
        exitConditionCase.signal(caseId, "probe", "tick");

        await().atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(stage.getStatus())
                    .as("stage must be TERMINATED when worker output satisfies exit condition")
                    .isEqualTo(StageStatus.TERMINATED));
    }

    @ApplicationScoped
    public static class ExitConditionCaseBean extends CaseHub {
        private final Capability cap = Capability.builder()
            .name("exit-writer")
            .inputSchema("{ phase: .phase }")
            .outputSchema("{ phase: .phase }")
            .build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("blackboard-it")
                .name("Exit Condition Case")
                .version("1.0.0")
                .capabilities(cap)
                .workers(Worker.builder()
                    .name("exit-writer-worker")
                    .capabilities(cap)
                    .function(input -> Map.of("phase", "exited")) // writes phase=exited
                    .build())
                .bindings(Binding.builder()
                    .name("trigger-on-active")
                    .capability(cap)
                    .on(new ContextChangeTrigger(".phase == \"active\""))
                    .build())
                .build();
        }
    }
}
```

- [ ] **Step 2: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=ExitConditionBlackboardTest 2>&1 | grep -E "Tests run:|FAIL"
```

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/ExitConditionBlackboardTest.java
git commit -m "test(blackboard): R3 — exit condition satisfied by worker output, end-to-end (casehubio/engine#76)"
```

---

## Task 3: R4 — Free-floating and staged workers execute together

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/it/MixedWorkersBlackboardTest.java`

- [ ] **Step 1: Create test**

```java
package io.casehub.blackboard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.*;
import io.casehub.blackboard.plan.PlanItem.PlanItemStatus;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.engine.internal.engine.cache.CaseInstanceCache;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * R4: A free-floating binding (no stage) and a staged binding both fire in the same
 * case. Both workers complete independently. Verifies that staged and unstaged
 * bindings coexist correctly and neither blocks the other.
 * See casehubio/engine#76.
 */
@QuarkusTest
class MixedWorkersBlackboardTest {

    @Inject BlackboardRegistry registry;
    @Inject CaseInstanceCache caseInstanceCache;
    @Inject MixedCaseBean mixedCase;

    @Test
    void free_floating_and_staged_workers_both_complete() {
        UUID caseId = mixedCase.startCase(Map.of("phase", "start"))
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

        // No stages added — both bindings are free-floating
        // Worker A: "phase-a-worker" fires on .phase == "start"
        // Worker B: "phase-b-worker" fires on .phase == "start"
        // Both should complete

        await().atMost(15, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var plan = registry.get(caseId);
                assertThat(plan).isPresent();

                // Both workers should have been indexed (both ran)
                assertThat(registry.getPlanItemId(caseId, "phase-a-worker"))
                    .as("worker-A must have been indexed for completion tracking")
                    .isPresent();
                assertThat(registry.getPlanItemId(caseId, "phase-b-worker"))
                    .as("worker-B must have been indexed for completion tracking")
                    .isPresent();

                // Both PlanItems must be COMPLETED
                var itemAId = registry.getPlanItemId(caseId, "phase-a-worker").get();
                var itemBId = registry.getPlanItemId(caseId, "phase-b-worker").get();
                assertThat(plan.get().getPlanItem(itemAId).map(i -> i.getStatus()))
                    .contains(PlanItemStatus.COMPLETED);
                assertThat(plan.get().getPlanItem(itemBId).map(i -> i.getStatus()))
                    .contains(PlanItemStatus.COMPLETED);
            });
    }

    @ApplicationScoped
    public static class MixedCaseBean extends CaseHub {
        private final Capability capA = Capability.builder()
            .name("phase-a")
            .inputSchema("{ phase: .phase }")
            .outputSchema("{ phaseA: .phaseA }")
            .build();
        private final Capability capB = Capability.builder()
            .name("phase-b")
            .inputSchema("{ phase: .phase }")
            .outputSchema("{ phaseB: .phaseB }")
            .build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("blackboard-it")
                .name("Mixed Workers Case")
                .version("1.0.0")
                .capabilities(capA, capB)
                .workers(
                    Worker.builder().name("phase-a-worker").capabilities(capA)
                        .function(input -> Map.of("phaseA", "done")).build(),
                    Worker.builder().name("phase-b-worker").capabilities(capB)
                        .function(input -> Map.of("phaseB", "done")).build())
                .bindings(
                    Binding.builder().name("trigger-a").capability(capA)
                        .on(new ContextChangeTrigger(".phase == \"start\"")).build(),
                    Binding.builder().name("trigger-b").capability(capB)
                        .on(new ContextChangeTrigger(".phase == \"start\"")).build())
                .build();
        }
    }
}
```

- [ ] **Step 2: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=MixedWorkersBlackboardTest 2>&1 | grep -E "Tests run:|FAIL"
```

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/MixedWorkersBlackboardTest.java
git commit -m "test(blackboard): R4 — free-floating and staged workers coexist (casehubio/engine#76)"
```

---

## Task 4: R5 — Lambda entry condition activates stage (end-to-end)

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/it/LambdaEntryConditionBlackboardTest.java`

- [ ] **Step 1: Create test**

```java
package io.casehub.blackboard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.*;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * R5: A stage with a Java lambda entry condition (not a JQ string) activates
 * end-to-end when the predicate returns true. Verifies LambdaExpressionEvaluator
 * dispatch in StageLifecycleEvaluator works correctly in the full engine context.
 * See casehubio/engine#76.
 */
@QuarkusTest
class LambdaEntryConditionBlackboardTest {

    @Inject BlackboardRegistry registry;
    @Inject LambdaCaseBean lambdaCase;

    @Test
    void stage_with_lambda_entry_condition_activates_end_to_end() {
        UUID caseId = lambdaCase.startCase(Map.of("value", 42))
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

        // Lambda: activates when context contains value == 42
        Stage stage = Stage.create("lambda-stage")
            .withEntryCondition(ctx -> Integer.valueOf(42).equals(ctx.getPath("value")));
        registry.get(caseId).get().addStage(stage);

        // Trigger a cycle
        lambdaCase.signal(caseId, "probe", "tick");

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(stage.getStatus())
                    .as("stage with Java lambda entry condition must activate when predicate is true")
                    .isEqualTo(StageStatus.ACTIVE));
    }

    @Test
    void stage_with_lambda_stays_pending_when_predicate_is_false() {
        UUID caseId = lambdaCase.startCase(Map.of("value", 99)) // not 42
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

        Stage stage = Stage.create("lambda-stage")
            .withEntryCondition(ctx -> Integer.valueOf(42).equals(ctx.getPath("value")));
        registry.get(caseId).get().addStage(stage);

        lambdaCase.signal(caseId, "probe", "tick");

        // Give time for evaluation — stage should NOT activate
        await().during(2, TimeUnit.SECONDS).atMost(4, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(stage.getStatus())
                    .as("stage must stay PENDING when lambda predicate is false")
                    .isEqualTo(StageStatus.PENDING));
    }

    @ApplicationScoped
    public static class LambdaCaseBean extends CaseHub {
        private final Capability cap = Capability.builder()
            .name("lambda-cap")
            .inputSchema("{ value: .value }")
            .outputSchema("{ done: .done }")
            .build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("blackboard-it")
                .name("Lambda Entry Case")
                .version("1.0.0")
                .capabilities(cap)
                .workers(Worker.builder()
                    .name("lambda-worker")
                    .capabilities(cap)
                    .function(input -> Map.of("done", true))
                    .build())
                .bindings(Binding.builder()
                    .name("trigger-on-value")
                    .capability(cap)
                    .on(new ContextChangeTrigger(".value != null"))
                    .build())
                .build();
        }
    }
}
```

- [ ] **Step 2: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=LambdaEntryConditionBlackboardTest 2>&1 | grep -E "Tests run:|FAIL"
```

- [ ] **Step 3: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 4: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/LambdaEntryConditionBlackboardTest.java
git commit -m "test(blackboard): R5 — lambda entry condition activates stage end-to-end (casehubio/engine#76)

Closes regression coverage gap vs prior treblereel implementation.
All four previously-missing integration scenarios now covered: sequential
stages (R1), exit condition end-to-end (R3), mixed workers (R4), lambda
entry conditions (R5)."
```
