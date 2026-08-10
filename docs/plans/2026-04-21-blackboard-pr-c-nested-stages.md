# Blackboard PR-C: Nested Stage Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix functional gap where nested stages activate immediately regardless of whether their parent stage is ACTIVE. A child stage should only be evaluated for activation once its parent stage is ACTIVE.

**Architecture:** `StageLifecycleEvaluator.activatePendingStages` adds a parent-active guard: for any stage with a `parentStageId`, activation is skipped unless the parent stage is ACTIVE in the `CasePlanModel`. This requires one new method on `CasePlanModel` — `getStage(String stageId)` already exists; use it directly.

**Tech Stack:** Java 21, JUnit 5, AssertJ, Mockito, `@QuarkusTest`.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`

**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

**Depends on:** PR-A and PR-B completed (branch from PR-B commit).

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java` | Add parent-active guard in `activatePendingStages` |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/control/StageLifecycleEvaluatorTest.java` | Add 3 tests: nested stage stays pending when parent pending, nested activates when parent active, root stage unaffected |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/it/StageBlackboardTest.java` | Add nested stage integration test |

---

## Task 1: Parent-active guard in `StageLifecycleEvaluator` + unit tests

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/control/StageLifecycleEvaluatorTest.java`

- [ ] **Step 1: Write failing tests**

```java
// Add to StageLifecycleEvaluatorTest.java

@Test
void nested_stage_stays_pending_when_parent_is_pending() {
    Stage parent = Stage.create("parent"); // no entry condition — would normally activate
    Stage child = Stage.create("child").withParentStage(parent.getStageId());

    plan.addStage(parent);
    plan.addStage(child);

    // Don't activate parent — it stays PENDING
    // Evaluate — child should NOT activate because parent is still PENDING
    evaluator.evaluate(plan, ctx).await().indefinitely();
    evaluator.evaluate(plan, ctx).await().indefinitely(); // second cycle: parent activates, child should now evaluate

    // After first cycle parent activates (no entry condition), but child should only
    // activate on the NEXT cycle after parent is confirmed ACTIVE
    // After second cycle: parent is ACTIVE, child should now activate
    assertThat(parent.getStatus()).isEqualTo(StageStatus.ACTIVE);
    assertThat(child.getStatus()).isEqualTo(StageStatus.ACTIVE);
}

@Test
void nested_stage_stays_pending_while_parent_is_pending_single_cycle() {
    Stage parent = Stage.create("parent").withEntryCondition(c -> false); // never activates
    Stage child = Stage.create("child").withParentStage(parent.getStageId());

    plan.addStage(parent);
    plan.addStage(child);

    evaluator.evaluate(plan, ctx).await().indefinitely();

    assertThat(parent.getStatus()).isEqualTo(StageStatus.PENDING);
    assertThat(child.getStatus())
        .as("child stage must stay PENDING when parent is not ACTIVE")
        .isEqualTo(StageStatus.PENDING);
}

@Test
void root_stage_without_parent_activates_normally() {
    Stage root = Stage.create("root"); // no parentStageId
    plan.addStage(root);

    evaluator.evaluate(plan, ctx).await().indefinitely();

    assertThat(root.getStatus())
        .as("root stage (no parent) must not be affected by the parent-active guard")
        .isEqualTo(StageStatus.ACTIVE);
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=StageLifecycleEvaluatorTest 2>&1 | grep -E "FAIL|Tests run"
```

The `nested_stage_stays_pending_while_parent_is_pending_single_cycle` test should fail (child currently activates even with pending parent).

- [ ] **Step 3: Add parent-active guard in `StageLifecycleEvaluator.activatePendingStages`**

Replace `activatePendingStages` in `StageLifecycleEvaluator.java`:

```java
private void activatePendingStages(CasePlanModel plan, PlanExecutionContext ctx) {
    for (Stage stage : plan.getPendingStages()) {
        if (stage.isManualActivation()) continue;

        // Nested stage: parent must be ACTIVE before child can be evaluated
        if (stage.getParentStageId().isPresent()) {
            String parentId = stage.getParentStageId().get();
            boolean parentActive = plan.getStage(parentId)
                .map(Stage::isActive)
                .orElse(false);
            if (!parentActive) continue;
        }

        boolean conditionMet = stage.getEntryCondition() == null
            || stage.getEntryCondition().evaluate(ctx.caseContext());
        if (conditionMet) {
            stage.activate();
            eventBus.publish(BlackboardEventBusAddresses.STAGE_ACTIVATED,
                new StageActivatedEvent(ctx.caseId(), stage));
        }
    }
}
```

- [ ] **Step 4: Run unit tests — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=StageLifecycleEvaluatorTest 2>&1 | grep "Tests run"
```

Expected: all 6 existing + 3 new = 9 tests passing.

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/control/StageLifecycleEvaluator.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/control/StageLifecycleEvaluatorTest.java
git commit -m "fix(blackboard): nested stage activation gated on parent ACTIVE state (casehubio/engine#76)

Previously all PENDING stages were evaluated for activation on every cycle
regardless of parent stage state. Now a nested stage (parentStageId present)
is only evaluated once its parent is ACTIVE."
```

---

## Task 2: Nested stage integration test

**Files:**
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/it/StageBlackboardTest.java`

- [ ] **Step 1: Add nested stage integration test**

Add the following to `StageBlackboardTest.java` (after existing tests):

```java
/**
 * Verifies that a nested stage only activates after its parent stage is ACTIVE.
 *
 * <p>Setup: a case with two signals — first triggers the parent stage, second
 * should only activate the child stage once the parent is confirmed ACTIVE.
 * The parent stage has no required items (activates immediately on entry condition).
 */
@Test
void nested_stage_activates_only_after_parent_stage_is_active() {
    // Start a case — plan model is created on first select()
    UUID caseId = signalCase.startCase(Map.of("ready", true)).toCompletableFuture().join();

    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(registry.get(caseId)).isPresent());

    // Add parent stage (no entry condition — activates immediately)
    Stage parent = Stage.create("parent-stage");
    UUID parentId = UUID.fromString(parent.getStageId());

    // Add child stage — parentStageId set, so it only activates after parent is ACTIVE
    Stage child = Stage.create("child-stage")
        .withParentStage(parent.getStageId())
        .withEntryCondition(ctx -> true); // always true once parent is active

    registry.get(caseId).get().addStage(parent);
    registry.get(caseId).get().addStage(child);

    // Trigger a select() cycle by signalling a probe value
    signalCase.signal(caseId, "probe", "tick-1");

    // After first cycle: parent activates (no entry condition)
    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() ->
            assertThat(parent.getStatus())
                .as("parent stage must activate on first evaluation cycle")
                .isEqualTo(StageStatus.ACTIVE));

    // Child must NOT be active yet — it only evaluates once parent is already confirmed ACTIVE
    // (The parent and child are evaluated in the same cycle, but parent activation
    //  happens first; child is evaluated in the NEXT iteration of getPendingStages)
    // Trigger another cycle
    signalCase.signal(caseId, "probe", "tick-2");

    await().atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() ->
            assertThat(child.getStatus())
                .as("child stage must activate after parent is ACTIVE")
                .isEqualTo(StageStatus.ACTIVE));
}
```

**Note:** This test documents the two-cycle behaviour: parent activates on cycle N, child activates on cycle N+1. This is correct because `getPendingStages()` returns a snapshot at the start of `activatePendingStages` — newly-activated parents are not visible to later iterations within the same cycle.

- [ ] **Step 2: Run integration test**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=StageBlackboardTest 2>&1 | grep "Tests run"
```

Expected: existing 6 + 1 new = 7 tests passing.

- [ ] **Step 3: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 4: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/it/StageBlackboardTest.java
git commit -m "test(blackboard): nested stage integration test — child activates after parent (casehubio/engine#76)"
```
