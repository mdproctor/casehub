# Blackboard PR-F: BlackboardPlanConfigurer SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `BlackboardPlanConfigurer` SPI so application code can declare plan model configuration (stages, milestones) for a case definition before any binding fires.

**Architecture:** `BlackboardPlanConfigurer` is a CDI-injectable interface with `configure(CasePlanModel, PlanExecutionContext)` and optional `supports(CaseDefinition)`. `PlanningStrategyLoopControl` calls all matching configurers the first time a plan model is created for a case. This is per-instance correct (each new case gets its own plan model configured once) while giving per-type configuration semantics.

**Tech Stack:** Java 21, Jakarta CDI `Instance<>`, JUnit 5, AssertJ, Mockito, `@QuarkusTest`.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`
**Branch:** create `feat/bb-qa-f-plan-configurer` from `feat/bb-qa-e-edge-cases`
**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/BlackboardPlanConfigurer.java` | NEW — SPI interface |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java` | Inject `Instance<BlackboardPlanConfigurer>`, call on first plan creation |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/control/BlackboardPlanConfigurerTest.java` | NEW — unit tests |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/PlanConfigurerBlackboardTest.java` | NEW — `@QuarkusTest` integration |

---

## Task 1: `BlackboardPlanConfigurer` interface + unit tests

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/BlackboardPlanConfigurer.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/control/BlackboardPlanConfigurerTest.java`

- [ ] **Step 1: Create the interface**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/control/BlackboardPlanConfigurer.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.CaseDefinition;
import io.casehub.blackboard.plan.CasePlanModel;

/**
 * SPI for configuring a new {@link CasePlanModel} when a case instance starts.
 *
 * <p>Implement as a CDI {@code @ApplicationScoped} bean. Called exactly once per
 * case instance, the first time {@link PlanningStrategyLoopControl} creates a
 * plan model for that case. Replaces the per-type registry pattern — plan models
 * remain per-instance (keyed by UUID) while configuration is declared per-type.
 *
 * <p>Example — declare stages for a case definition:
 * <pre>{@code
 * @ApplicationScoped
 * public class LoanCasePlanConfigurer implements BlackboardPlanConfigurer {
 *     @Override
 *     public boolean supports(CaseDefinition definition) {
 *         return "loan-application".equals(definition.name());
 *     }
 *     @Override
 *     public void configure(CasePlanModel plan, PlanExecutionContext context) {
 *         plan.addStage(Stage.builder("intake").entryCondition(...).build());
 *         plan.addStage(Stage.builder("underwriting").entryCondition(...).build());
 *         plan.trackMilestone("documents-received");
 *     }
 * }
 * }</pre>
 *
 * See casehubio/engine#76.
 */
public interface BlackboardPlanConfigurer {

    /**
     * Returns true if this configurer should be applied to cases of the given
     * definition. Default implementation returns true (applies to all cases).
     */
    default boolean supports(CaseDefinition definition) {
        return true;
    }

    /**
     * Configure the plan model for a newly started case. Called once per case
     * instance before any binding is evaluated.
     */
    void configure(CasePlanModel plan, PlanExecutionContext context);
}
```

- [ ] **Step 2: Write unit tests**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/control/BlackboardPlanConfigurerTest.java
package io.casehub.blackboard.control;

import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.context.CaseContext;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.plan.DefaultCasePlanModel;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BlackboardPlanConfigurer contract.
 * See casehubio/engine#76.
 */
class BlackboardPlanConfigurerTest {

    @Test
    void default_supports_returns_true_for_any_definition() {
        BlackboardPlanConfigurer configurer = (plan, ctx) -> {}; // lambda impl

        assertThat(configurer.supports(mock(CaseDefinition.class))).isTrue();
    }

    @Test
    void configure_is_called_with_plan_and_context() {
        CasePlanModel[] captured = {null};
        BlackboardPlanConfigurer configurer = (plan, ctx) -> captured[0] = plan;

        CasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
        PlanExecutionContext ctx = new PlanExecutionContext(
            UUID.randomUUID(), mock(CaseDefinition.class), mock(CaseContext.class));

        configurer.configure(plan, ctx);

        assertThat(captured[0]).isSameAs(plan);
    }

    @Test
    void supports_can_filter_by_definition_name() {
        BlackboardPlanConfigurer configurer = new BlackboardPlanConfigurer() {
            @Override
            public boolean supports(CaseDefinition def) {
                return "loan-application".equals(def.name());
            }
            @Override
            public void configure(CasePlanModel plan, PlanExecutionContext ctx) {}
        };

        CaseDefinition loanDef = mock(CaseDefinition.class);
        when(loanDef.name()).thenReturn("loan-application");
        CaseDefinition otherDef = mock(CaseDefinition.class);
        when(otherDef.name()).thenReturn("other");

        assertThat(configurer.supports(loanDef)).isTrue();
        assertThat(configurer.supports(otherDef)).isFalse();
    }
}
```

- [ ] **Step 3: Compile and run tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=BlackboardPlanConfigurerTest 2>&1 | grep "Tests run"
```

Expected: 3 tests passing.

- [ ] **Step 4: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/control/BlackboardPlanConfigurer.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/control/BlackboardPlanConfigurerTest.java
git commit -m "feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan configuration per-instance semantics (casehubio/engine#76)"
```

---

## Task 2: Wire `BlackboardPlanConfigurer` into `PlanningStrategyLoopControl`

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java`

- [ ] **Step 1: Read current `PlanningStrategyLoopControl.java` in full, then add configurer injection**

Add field injection (constructor injection, consistent with rest of class):

```java
@Inject Instance<BlackboardPlanConfigurer> configurers;
```

In the constructor, add `Instance<BlackboardPlanConfigurer> configurers` parameter and assign to field.

- [ ] **Step 2: Track which cases have been configured**

Add a set to track which caseIds have already had their plan model configured:

```java
private final Set<UUID> configured = ConcurrentHashMap.newKeySet();
```

- [ ] **Step 3: Call configurers on first plan creation**

In `select()`, after `registry.getOrCreate(caseId)`, call configurers if this is the first time:

```java
UUID caseId = ctx.caseId();
CasePlanModel plan = registry.getOrCreate(caseId);

// Configure the plan model exactly once per case instance
if (configured.add(caseId)) {
    configurers.stream()
        .filter(c -> c.supports(ctx.definition()))
        .forEach(c -> c.configure(plan, ctx));
}
```

`configured.add(caseId)` returns `true` only the first time — atomic due to `ConcurrentHashMap.newKeySet()`.

- [ ] **Step 4: Evict from `configured` set when case is evicted**

In `BlackboardRegistry.evict(UUID)`, we also need to remove from `configured`. But `configured` is on `PlanningStrategyLoopControl`, not the registry. Options:

Add a `void evictConfigured(UUID caseId)` to `BlackboardRegistry` that holds a secondary set, or simply remove from `configured` in `CaseEvictionHandler`. The cleanest: add the configured set to `BlackboardRegistry` (it already holds all per-case state).

Add to `BlackboardRegistry`:
```java
private final Set<UUID> configured = ConcurrentHashMap.newKeySet();

public boolean markConfigured(UUID caseId) {
    return configured.add(caseId); // returns true if first time
}

// Update evict() to also clear:
public void evict(UUID caseId) {
    planModels.remove(caseId);
    completionIndex.remove(caseId);
    configured.remove(caseId);
}
```

Then in `PlanningStrategyLoopControl.select()`:
```java
if (registry.markConfigured(caseId)) {
    configurers.stream()
        .filter(c -> c.supports(ctx.definition()))
        .forEach(c -> c.configure(plan, ctx));
}
```

Remove the local `configured` set from `PlanningStrategyLoopControl`.

- [ ] **Step 5: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -2
```

Expected: all previous tests still passing (99+).

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/registry/BlackboardRegistry.java
git commit -m "feat(blackboard): wire BlackboardPlanConfigurer — called once on first plan model creation (casehubio/engine#76)"
```

---

## Task 3: Integration test for `BlackboardPlanConfigurer`

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/it/PlanConfigurerBlackboardTest.java`

- [ ] **Step 1: Write integration test**

```java
package io.casehub.blackboard.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.PlanExecutionContext;
import io.casehub.api.model.*;
import io.casehub.blackboard.control.BlackboardPlanConfigurer;
import io.casehub.blackboard.plan.CasePlanModel;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.blackboard.stage.Stage;
import io.casehub.blackboard.stage.StageStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Integration test for BlackboardPlanConfigurer — verifies stages declared
 * in a configurer are present and evaluated before bindings fire.
 * See casehubio/engine#76.
 */
@QuarkusTest
class PlanConfigurerBlackboardTest {

    @Inject BlackboardRegistry registry;
    @Inject ConfiguredCaseBean configuredCase;

    @Test
    void stages_declared_in_configurer_are_active_when_case_starts() {
        UUID caseId = configuredCase.startCase(Map.of("phase", "ready"))
            .toCompletableFuture().join();

        // The configurer adds a stage with no entry condition — it activates immediately
        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var plan = registry.get(caseId);
                assertThat(plan).isPresent();
                assertThat(plan.get().getAllStages())
                    .as("configurer-declared stage must exist in plan model")
                    .hasSize(1);
                assertThat(plan.get().getAllStages().get(0).getStatus())
                    .as("configurer-declared stage with no entry condition must activate")
                    .isEqualTo(StageStatus.ACTIVE);
            });
    }

    @Test
    void configurer_is_called_only_once_per_case_instance() {
        CallCountingConfigurer.callCount = 0;

        UUID caseId1 = configuredCase.startCase(Map.of("phase", "ready"))
            .toCompletableFuture().join();
        UUID caseId2 = configuredCase.startCase(Map.of("phase", "ready"))
            .toCompletableFuture().join();

        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId1)).isPresent());
        await().atMost(10, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThat(registry.get(caseId2)).isPresent());

        // Trigger additional cycles to verify configurer is not called again
        configuredCase.signal(caseId1, "probe", "tick");
        configuredCase.signal(caseId2, "probe", "tick");

        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS)
            .untilAsserted(() ->
                assertThat(CallCountingConfigurer.callCount)
                    .as("configurer must be called exactly once per case instance (2 cases = 2 calls)")
                    .isEqualTo(2));
    }

    /** Test configurer — adds one always-active stage and counts calls. */
    @ApplicationScoped
    public static class CallCountingConfigurer implements BlackboardPlanConfigurer {
        static volatile int callCount = 0;

        @Override
        public void configure(CasePlanModel plan, PlanExecutionContext ctx) {
            callCount++;
            plan.addStage(Stage.alwaysActivate("configured-stage"));
        }
    }

    @ApplicationScoped
    public static class ConfiguredCaseBean extends CaseHub {
        private final Capability cap = Capability.builder()
            .name("probe-cap").inputSchema("{}").outputSchema("{}").build();

        @Override
        public CaseDefinition getDefinition() {
            return CaseDefinition.builder()
                .namespace("blackboard-it").name("Configured Case").version("1.0.0")
                .capabilities(cap)
                .workers(Worker.builder().name("probe-worker").capabilities(cap)
                    .function(input -> Map.of()).build())
                .bindings(Binding.builder().name("trigger-on-probe").capability(cap)
                    .on(new ContextChangeTrigger(".probe != null")).build())
                .build();
        }
    }
}
```

**Note:** `Stage.alwaysActivate("name")` is the new explicit always-activates factory from PR-I. If PR-I hasn't landed yet on this branch, use `Stage.create("configured-stage")` (null entry = always activates) and add a TODO comment.

- [ ] **Step 2: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -2
```

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/PlanConfigurerBlackboardTest.java
git commit -m "test(blackboard): integration test for BlackboardPlanConfigurer — stages declared, called once per instance (casehubio/engine#76)"
```
