# Blackboard PR-B: Lifecycle Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix four lifecycle correctness issues: milestone achievement silently dropped if not pre-tracked; completed PlanItems not cleaned from activeByBinding; autocomplete hangs when required item is unregistered; plan models accumulate in memory forever.

**Architecture:** `achieveMilestone` changes from `computeIfPresent` (no-op if untracked) to `put` (always records). `PlanItemCompletionHandler` calls `removePlanItem` after marking COMPLETED. `evaluateStageAutocomplete` guards against unregistered required items. A new `CaseEvictionHandler` listens to `CASE_STATUS_CHANGED` and calls `registry.evict()` on terminal case states.

**Tech Stack:** Java 21, Quarkus `@ConsumeEvent`, Vert.x Mutiny, JUnit 5, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`

**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

**Depends on:** PR-A merged (run on the PR-A branch or after PR-A commits).

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java` | `achieveMilestone` → `milestones.put()` |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java` | Call `removePlanItem` after COMPLETED; guard unregistered required items |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/CaseEvictionHandler.java` | NEW — evicts plan model on case terminal state |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java` | Add Rb4 (untracked milestone), N5 (no required items no autocomplete) |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java` | Add Rb3 (unregistered required item), Rb6 (activeByBinding cleaned after COMPLETED) |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/CaseEvictionHandlerTest.java` | NEW — unit test for eviction on terminal status |

---

## Task 1: Fix `achieveMilestone` to always record achievement

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java`

- [ ] **Step 1: Write failing test (Rb4)**

```java
// Add to DefaultCasePlanModelTest.java
@Test
void achieveMilestone_before_trackMilestone_still_records_achievement() {
    // achieveMilestone on an untracked name should still persist the achievement
    // (the event may arrive before the application code calls trackMilestone)
    plan.achieveMilestone("docs-received");
    assertThat(plan.isMilestoneAchieved("docs-received"))
        .as("achieveMilestone must record achievement regardless of prior trackMilestone call")
        .isTrue();
}

@Test
void trackMilestone_after_achieve_returns_already_achieved() {
    plan.achieveMilestone("docs-received");
    plan.trackMilestone("docs-received"); // late track — should see it's already achieved
    assertThat(plan.isMilestoneAchieved("docs-received")).isTrue();
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest 2>&1 | grep -E "FAIL|Tests run"
```

- [ ] **Step 3: Fix `achieveMilestone` in `DefaultCasePlanModel`**

```java
// Replace lines 143-145 in DefaultCasePlanModel.java:

// Before:
@Override
public void achieveMilestone(String name) {
    milestones.computeIfPresent(name, (k, v) -> Boolean.TRUE);
}

// After:
@Override
public void achieveMilestone(String name) {
    milestones.put(name, Boolean.TRUE);
}
```

`trackMilestone` uses `putIfAbsent` so it won't overwrite an already-achieved milestone.

- [ ] **Step 4: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest 2>&1 | grep "Tests run"
```

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java
git commit -m "fix(blackboard): achieveMilestone uses put() — achievement recorded regardless of trackMilestone order (casehubio/engine#76)"
```

---

## Task 2: `PlanItemCompletionHandler` — cleanup and guard

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java`

- [ ] **Step 1: Write failing tests (Rb3, Rb6)**

```java
// Add to PlanItemCompletionHandlerTest.java

@Test
void completed_plan_item_is_removed_from_active_tracking() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    plan.addPlanItemIfAbsent(item);
    registry.indexWorkerForCompletion(caseId, "worker-a", item.getPlanItemId());

    handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

    // After COMPLETED, hasActivePlanItem must return false so the binding can re-fire
    assertThat(plan.hasActivePlanItem("binding-a"))
        .as("completed PlanItem must be removed from active tracking")
        .isFalse();
}

@Test
void autocomplete_with_unregistered_required_item_does_not_complete_stage() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    plan.addPlanItemIfAbsent(item);
    registry.indexWorkerForCompletion(caseId, "worker-a", item.getPlanItemId());

    Stage stage = Stage.create("intake");
    stage.addRequiredItem("non-existent-plan-item-id"); // not in plan
    stage.activate();
    plan.addStage(stage);

    handler.onWorkerFinished(eventFor("worker-a")).await().indefinitely();

    // Stage should remain ACTIVE — unregistered required item blocks autocomplete
    assertThat(stage.isTerminal())
        .as("stage must not autocomplete when required item is not registered in plan")
        .isFalse();
    verifyNoInteractions(mockBus);
}
```

Note: `addPlanItemIfAbsent` is used in the test setup — update the test class accordingly. Also verify the `setUp` in this test class calls `addPlanItemIfAbsent` instead of `addPlanItem` where the dedup semantics matter.

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemCompletionHandlerTest 2>&1 | grep -E "FAIL|Tests run"
```

- [ ] **Step 3: Fix `PlanItemCompletionHandler.onWorkerFinished`**

Replace the inner lambda in `onWorkerFinished`:

```java
// In PlanItemCompletionHandler.java, replace the plan.getPlanItem block:

plan.getPlanItem(planItemId).ifPresent(item -> {
    item.setStatus(PlanItem.PlanItemStatus.COMPLETED);
    plan.removePlanItem(planItemId); // clean activeByBinding so binding can re-fire
    evaluateStageAutocomplete(caseId, plan, planItemId);
});
```

- [ ] **Step 4: Fix `evaluateStageAutocomplete` to guard unregistered required items**

Replace the `allDone` check in `evaluateStageAutocomplete`:

```java
private void evaluateStageAutocomplete(UUID caseId, CasePlanModel plan, String completedItemId) {
    for (Stage stage : plan.getActiveStages()) {
        if (!stage.isAutocomplete()) continue;
        if (!stage.getRequiredItemIds().contains(completedItemId)) continue;

        boolean allDone = stage.getRequiredItemIds().stream().allMatch(itemId -> {
            // Guard: item must be registered in the plan; if not, it cannot be complete
            return plan.getPlanItem(itemId)
                .map(pi -> pi.getStatus() == PlanItem.PlanItemStatus.COMPLETED)
                .orElse(false); // unregistered item treated as not done — blocks autocomplete
        });

        if (allDone) {
            stage.complete();
            eventBus.publish(BlackboardEventBusAddresses.STAGE_COMPLETED,
                new StageCompletedEvent(caseId, stage));
        }
    }
}
```

The `orElse(false)` was already present — add the comment so the intent is clear.

- [ ] **Step 5: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=PlanItemCompletionHandlerTest 2>&1 | grep "Tests run"
```

Expected: all existing tests + 2 new = 7 tests.

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java
git commit -m "fix(blackboard): removePlanItem after COMPLETED; guard unregistered required items in autocomplete (casehubio/engine#76)"
```

---

## Task 3: `CaseEvictionHandler` — evict plan models on case completion

**Files:**
- Create: `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/CaseEvictionHandler.java`
- Create: `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/CaseEvictionHandlerTest.java`

- [ ] **Step 1: Write failing test**

```java
// casehub-blackboard/src/test/java/io/casehub/blackboard/handler/CaseEvictionHandlerTest.java
package io.casehub.blackboard.handler;

import io.casehub.api.model.CaseStatus;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.engine.internal.event.CaseStatusChanged;
import io.casehub.engine.internal.model.CaseInstance;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CaseEvictionHandler.
 * Verifies plan models are evicted from BlackboardRegistry on terminal case states.
 * See casehubio/engine#76.
 */
class CaseEvictionHandlerTest {

    @Test
    void evicts_plan_model_on_completed_status() {
        BlackboardRegistry registry = new BlackboardRegistry();
        UUID caseId = UUID.randomUUID();
        registry.getOrCreate(caseId); // create a plan model

        CaseEvictionHandler handler = new CaseEvictionHandler(registry);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);

        handler.onCaseStatusChanged(
            new CaseStatusChanged(instance, "RUNNING", CaseStatus.COMPLETED.name()))
            .await().indefinitely();

        assertThat(registry.get(caseId))
            .as("plan model must be evicted when case reaches COMPLETED")
            .isEmpty();
    }

    @Test
    void evicts_plan_model_on_faulted_status() {
        BlackboardRegistry registry = new BlackboardRegistry();
        UUID caseId = UUID.randomUUID();
        registry.getOrCreate(caseId);

        CaseEvictionHandler handler = new CaseEvictionHandler(registry);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);

        handler.onCaseStatusChanged(
            new CaseStatusChanged(instance, "RUNNING", CaseStatus.FAULTED.name()))
            .await().indefinitely();

        assertThat(registry.get(caseId)).isEmpty();
    }

    @Test
    void does_not_evict_on_non_terminal_status() {
        BlackboardRegistry registry = new BlackboardRegistry();
        UUID caseId = UUID.randomUUID();
        registry.getOrCreate(caseId);

        CaseEvictionHandler handler = new CaseEvictionHandler(registry);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(caseId);

        handler.onCaseStatusChanged(
            new CaseStatusChanged(instance, "PENDING", CaseStatus.RUNNING.name()))
            .await().indefinitely();

        assertThat(registry.get(caseId))
            .as("plan model must NOT be evicted for non-terminal status changes")
            .isPresent();
    }

    @Test
    void no_plan_model_does_not_throw() {
        BlackboardRegistry registry = new BlackboardRegistry();
        CaseEvictionHandler handler = new CaseEvictionHandler(registry);
        CaseInstance instance = mock(CaseInstance.class);
        when(instance.getUuid()).thenReturn(UUID.randomUUID());

        // No plan model exists — should not throw
        handler.onCaseStatusChanged(
            new CaseStatusChanged(instance, "RUNNING", CaseStatus.COMPLETED.name()))
            .await().indefinitely();
    }
}
```

- [ ] **Step 2: Run — verify FAIL (class not found)**

```bash
mvn test -pl casehub-blackboard -Dtest=CaseEvictionHandlerTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -5
```

- [ ] **Step 3: Implement `CaseEvictionHandler`**

```java
// casehub-blackboard/src/main/java/io/casehub/blackboard/handler/CaseEvictionHandler.java
package io.casehub.blackboard.handler;

import io.casehub.api.model.CaseStatus;
import io.casehub.blackboard.registry.BlackboardRegistry;
import io.casehub.engine.internal.event.CaseStatusChanged;
import io.casehub.engine.internal.event.EventBusAddresses;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;

/**
 * Evicts {@link io.casehub.blackboard.plan.CasePlanModel} from
 * {@link BlackboardRegistry} when a case reaches a terminal state.
 *
 * <p>Prevents unbounded memory growth in long-running applications by releasing
 * plan model state once a case is no longer active. See casehubio/engine#76.
 */
@ApplicationScoped
public class CaseEvictionHandler {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
        CaseStatus.COMPLETED.name(),
        CaseStatus.FAULTED.name(),
        CaseStatus.CANCELLED.name()
    );

    private final BlackboardRegistry registry;

    @Inject
    public CaseEvictionHandler(BlackboardRegistry registry) {
        this.registry = registry;
    }

    @ConsumeEvent(EventBusAddresses.CASE_STATUS_CHANGED)
    public Uni<Void> onCaseStatusChanged(CaseStatusChanged event) {
        if (TERMINAL_STATUSES.contains(event.newStatus())) {
            registry.evict(event.instance().getUuid());
        }
        return Uni.createFrom().voidItem();
    }
}
```

- [ ] **Step 4: Add `mockito-core` import check — it's already in pom.xml**

```bash
grep "mockito-core" casehub-blackboard/pom.xml
```

Expected: present. If not, add it (it was added in the original pom).

- [ ] **Step 5: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=CaseEvictionHandlerTest 2>&1 | grep "Tests run"
```

Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

Expected: all tests passing (74 from PR-A + milestone fix + completion handler fixes + 4 new eviction tests).

- [ ] **Step 7: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/handler/CaseEvictionHandler.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/handler/CaseEvictionHandlerTest.java
git commit -m "feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state (casehubio/engine#76)

Listens to CASE_STATUS_CHANGED; evicts BlackboardRegistry entries for COMPLETED,
FAULTED, and CANCELLED cases. Prevents unbounded plan model accumulation in
long-running applications."
```
