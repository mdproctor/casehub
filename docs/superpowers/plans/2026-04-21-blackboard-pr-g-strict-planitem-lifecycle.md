# Blackboard PR-G: Strict PlanItem Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `PlanItem.setStatus()` (raw setter) with validated transition methods that throw `IllegalStateException` on invalid transitions, matching treblereel's stricter lifecycle enforcement.

**Architecture:** Add `markRunning()`, `markCompleted()`, `markFaulted()`, `markCancelled()` methods with state guards. Remove the public `setStatus()` setter. Update all callers (`PlanningStrategyLoopControl`, `PlanItemCompletionHandler`). Update all tests that used `setStatus()` directly.

**Tech Stack:** Java 21, JUnit 5, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`
**Branch:** create `feat/bb-qa-g-strict-lifecycle` from `feat/bb-qa-f-plan-configurer`
**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java` | Remove `setStatus()`, add transition methods with guards |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java` | Use `markRunning()` instead of `setStatus(RUNNING)` |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java` | Use `markCompleted()` instead of `setStatus(COMPLETED)` |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java` | Update tests to use transition methods; add throws tests |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java` | Update `setStatus` calls to transition methods |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java` | Update `setStatus` calls |

---

## Task 1: Replace `setStatus` with validated transition methods

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java`

- [ ] **Step 1: Write new failing tests first (TDD)**

Read `PlanItemTest.java` first. Then add these tests:

```java
@Test
void markRunning_from_pending_succeeds() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    item.markRunning();
    assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.RUNNING);
}

@Test
void markRunning_from_running_throws() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    item.markRunning();
    assertThatThrownBy(item::markRunning)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("RUNNING");
}

@Test
void markCompleted_from_running_succeeds() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    item.markRunning();
    item.markCompleted();
    assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.COMPLETED);
}

@Test
void markCompleted_from_pending_throws() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    assertThatThrownBy(item::markCompleted)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PENDING");
}

@Test
void markFaulted_from_running_succeeds() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    item.markRunning();
    item.markFaulted();
    assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.FAULTED);
}

@Test
void markCancelled_from_pending_succeeds() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    item.markCancelled();
    assertThat(item.getStatus()).isEqualTo(PlanItem.PlanItemStatus.CANCELLED);
}
```

Add import: `import static org.assertj.core.api.Assertions.assertThatThrownBy;`

- [ ] **Step 2: Run — verify FAIL (methods don't exist)**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemTest 2>&1 | grep -E "Tests run:|FAIL|ERROR" | head -5
```

- [ ] **Step 3: Add transition methods to `PlanItem.java`, remove public `setStatus`**

Read `PlanItem.java` first to see the current `setStatus` and `status` field.

Replace the public `setStatus(PlanItemStatus)` with:

```java
/**
 * Transitions status from PENDING to RUNNING.
 * @throws IllegalStateException if current status is not PENDING
 */
public void markRunning() {
    if (status != PlanItemStatus.PENDING) {
        throw new IllegalStateException(
            "Cannot transition to RUNNING from " + status + " (planItemId=" + planItemId + ")");
    }
    status = PlanItemStatus.RUNNING;
}

/**
 * Transitions status from RUNNING to COMPLETED.
 * @throws IllegalStateException if current status is not RUNNING
 */
public void markCompleted() {
    if (status != PlanItemStatus.RUNNING) {
        throw new IllegalStateException(
            "Cannot transition to COMPLETED from " + status + " (planItemId=" + planItemId + ")");
    }
    status = PlanItemStatus.COMPLETED;
}

/**
 * Transitions to FAULTED from PENDING or RUNNING.
 * @throws IllegalStateException if current status is already terminal
 */
public void markFaulted() {
    if (status == PlanItemStatus.COMPLETED || status == PlanItemStatus.FAULTED
            || status == PlanItemStatus.CANCELLED) {
        throw new IllegalStateException(
            "Cannot fault a terminal PlanItem (status=" + status + ", planItemId=" + planItemId + ")");
    }
    status = PlanItemStatus.FAULTED;
}

/**
 * Cancels this PlanItem from PENDING or RUNNING.
 * @throws IllegalStateException if current status is already terminal
 */
public void markCancelled() {
    if (status == PlanItemStatus.COMPLETED || status == PlanItemStatus.FAULTED
            || status == PlanItemStatus.CANCELLED) {
        throw new IllegalStateException(
            "Cannot cancel a terminal PlanItem (status=" + status + ", planItemId=" + planItemId + ")");
    }
    status = PlanItemStatus.CANCELLED;
}
```

Keep `status` as `volatile` (already done in PR-A). Remove the public `void setStatus(PlanItemStatus status)` method entirely.

- [ ] **Step 4: Update callers**

**`PlanningStrategyLoopControl.java`** — find `pi.setStatus(PlanItem.PlanItemStatus.RUNNING)` and change to `pi.markRunning()`.

**`PlanItemCompletionHandler.java`** — find `item.setStatus(PlanItem.PlanItemStatus.COMPLETED)` and change to `item.markCompleted()`.

- [ ] **Step 5: Update tests that used `setStatus()` directly**

Search for `setStatus` in test files:
```bash
grep -rn "setStatus" casehub-blackboard/src/test/
```

For each occurrence:
- `item.setStatus(RUNNING)` → `item.markRunning()`
- `item.setStatus(COMPLETED)` → `item.markRunning(); item.markCompleted()`
- `item.setStatus(FAULTED)` → `item.markRunning(); item.markFaulted()`
- `item.setStatus(CANCELLED)` → `item.markCancelled()`

The existing status-transition tests in `PlanItemTest` (`status_transitions_pending_to_running_to_completed`) need updating too. Also update the `hasActivePlanItem` tests in `DefaultCasePlanModelTest` and `PlanItemCompletionHandlerTest`.

Also update the reflection test `status_field_is_volatile` — the field is still `volatile`, just no longer has a public setter. The test is still valid.

- [ ] **Step 6: Run all tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -2
```

Expected: all passing (previous count + 6 new transition tests).

- [ ] **Step 7: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/handler/PlanItemCompletionHandler.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/handler/PlanItemCompletionHandlerTest.java
git commit -m "feat(blackboard): PlanItem strict lifecycle — validated transition methods replace setStatus() (casehubio/engine#76)

markRunning(), markCompleted(), markFaulted(), markCancelled() throw
IllegalStateException on invalid transitions. Removes raw public setStatus()
setter. Matches treblereel's strict lifecycle enforcement while retaining
volatile for cross-thread visibility (PR-A)."
```
