# Blackboard PR-A: Thread Safety Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three thread-safety issues in casehub-blackboard: non-volatile `PlanItem.status`, non-atomic Stage lifecycle transitions, and TOCTOU race in addPlanItem.

**Architecture:** `PlanItem.status` becomes `volatile` (single writer per lifecycle stage, visibility is the concern). `Stage.status` becomes `AtomicReference<StageStatus>` with CAS lifecycle methods (two handlers can race to write terminal state). `DefaultCasePlanModel.addPlanItemIfAbsent` merges the dedup check and insert into `ConcurrentHashMap.compute()` (eliminates the TOCTOU window between check and insert).

**Tech Stack:** Java 21, `java.util.concurrent.atomic.AtomicReference`, `ConcurrentHashMap.compute()`, JUnit 5, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`

**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`
**Expected baseline:** 68 tests passing before any changes.

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java` | `status` field → `volatile` |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java` | Add `addPlanItemIfAbsent(PlanItem)` to interface |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java` | Implement `addPlanItemIfAbsent` atomically via `compute()` |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java` | `status` → `AtomicReference<StageStatus>`; `activatedAt`/`completedAt` → `volatile`; all lifecycle methods → CAS |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java` | Replace `hasActivePlanItem` + `addPlanItem` with `addPlanItemIfAbsent` |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java` | Add Rb2: `status_field_is_volatile` |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java` | Add Rb1: concurrent insert test; Rb6: `addPlanItemIfAbsent` semantics |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java` | Add Rb5: `status_field_is_atomic_reference`; existing lifecycle tests unchanged |

---

## Task 1: Make `PlanItem.status` volatile

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java`

- [ ] **Step 1: Write failing test**

```java
// Add to PlanItemTest.java
@Test
void status_field_is_volatile() throws NoSuchFieldException {
    java.lang.reflect.Field field = PlanItem.class.getDeclaredField("status");
    assertThat(java.lang.reflect.Modifier.isVolatile(field.getModifiers()))
        .as("PlanItem.status must be volatile for cross-thread visibility")
        .isTrue();
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=PlanItemTest#status_field_is_volatile 2>&1 | grep -E "FAIL|PASS|Tests run"
```

- [ ] **Step 3: Add `volatile` to `PlanItem.status`**

In `PlanItem.java`, change line 37:
```java
// Before:
private PlanItemStatus status;

// After:
private volatile PlanItemStatus status;
```

- [ ] **Step 4: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=PlanItemTest 2>&1 | grep "Tests run"
```

Expected: all PlanItemTest tests pass (was 6, now 7).

- [ ] **Step 5: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/PlanItem.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/plan/PlanItemTest.java
git commit -m "fix(blackboard): make PlanItem.status volatile for cross-thread visibility (casehubio/engine#76)"
```

---

## Task 2: Make `Stage.status` an `AtomicReference` with CAS lifecycle

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`

- [ ] **Step 1: Write failing test**

```java
// Add to StageTest.java
@Test
void status_field_is_atomic_reference() throws NoSuchFieldException {
    java.lang.reflect.Field field = Stage.class.getDeclaredField("status");
    field.setAccessible(true);
    assertThat(field.getType())
        .as("Stage.status must be AtomicReference to prevent concurrent transition races")
        .isEqualTo(java.util.concurrent.atomic.AtomicReference.class);
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=StageTest#status_field_is_atomic_reference 2>&1 | grep -E "FAIL|PASS"
```

- [ ] **Step 3: Rewrite `Stage.java` with `AtomicReference`**

Replace the `status` field and all lifecycle methods. The full replacement for the relevant parts of `Stage.java`:

```java
// Add import at top of Stage.java:
import java.util.concurrent.atomic.AtomicReference;

// Replace field declarations (around line 44):
private final AtomicReference<StageStatus> status = new AtomicReference<>(StageStatus.PENDING);
private volatile Instant activatedAt;
private volatile Instant completedAt;

// Replace getStatus():
public StageStatus getStatus() { return status.get(); }

// Replace isTerminal():
public boolean isTerminal() {
    StageStatus s = status.get();
    return s == StageStatus.COMPLETED || s == StageStatus.TERMINATED || s == StageStatus.FAULTED;
}

// Replace isActive():
public boolean isActive() { return status.get() == StageStatus.ACTIVE; }

// Replace activate():
public void activate() {
    if (status.compareAndSet(StageStatus.PENDING, StageStatus.ACTIVE)) {
        activatedAt = Instant.now();
    }
}

// Replace complete() — accepts ACTIVE or SUSPENDED:
public void complete() {
    StageStatus current;
    do {
        current = status.get();
        if (current != StageStatus.ACTIVE && current != StageStatus.SUSPENDED) return;
    } while (!status.compareAndSet(current, StageStatus.COMPLETED));
    completedAt = Instant.now();
}

// Replace terminate() — accepts ACTIVE or SUSPENDED:
public void terminate() {
    StageStatus current;
    do {
        current = status.get();
        if (current != StageStatus.ACTIVE && current != StageStatus.SUSPENDED) return;
    } while (!status.compareAndSet(current, StageStatus.TERMINATED));
    completedAt = Instant.now();
}

// Replace suspend():
public void suspend() {
    status.compareAndSet(StageStatus.ACTIVE, StageStatus.SUSPENDED);
}

// Replace resume():
public void resume() {
    status.compareAndSet(StageStatus.SUSPENDED, StageStatus.ACTIVE);
}

// Replace fault() — no-op if already terminal:
public void fault() {
    StageStatus current;
    do {
        current = status.get();
        if (isTerminalStatus(current)) return;
    } while (!status.compareAndSet(current, StageStatus.FAULTED));
    completedAt = Instant.now();
}

// Private helper — avoids calling isTerminal() which reads status again:
private static boolean isTerminalStatus(StageStatus s) {
    return s == StageStatus.COMPLETED || s == StageStatus.TERMINATED || s == StageStatus.FAULTED;
}
```

Also remove the `private StageStatus status;` field declaration and the old `status = StageStatus.PENDING` in the constructor (the AtomicReference initializes to PENDING inline).

- [ ] **Step 4: Run all Stage tests — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=StageTest 2>&1 | grep "Tests run"
```

Expected: all 14 existing StageTest tests pass + 1 new = 15 total.

- [ ] **Step 5: Run full suite — verify no regression**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

Expected: 69 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java
git commit -m "fix(blackboard): Stage.status → AtomicReference with CAS lifecycle methods (casehubio/engine#76)

Prevents concurrent complete()/terminate() race where both threads read ACTIVE,
both pass the guard, and the last writer determines the terminal state arbitrarily.
With CAS, exactly one transition succeeds and the other is cleanly rejected."
```

---

## Task 3: Atomic `addPlanItemIfAbsent` — eliminate TOCTOU in PlanItem scheduling

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java`
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java`
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java`

- [ ] **Step 1: Write failing tests**

```java
// Add to DefaultCasePlanModelTest.java

@Test
void addPlanItemIfAbsent_returns_true_when_no_active_item() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    assertThat(plan.addPlanItemIfAbsent(item)).isTrue();
    assertThat(plan.getAgenda()).hasSize(1);
}

@Test
void addPlanItemIfAbsent_returns_false_when_pending_item_exists() {
    PlanItem first = PlanItem.create("binding-a", "worker-a", 0);
    PlanItem second = PlanItem.create("binding-a", "worker-a", 0);
    plan.addPlanItemIfAbsent(first);
    assertThat(plan.addPlanItemIfAbsent(second)).isFalse();
    assertThat(plan.getAgenda()).hasSize(1); // only one item
}

@Test
void addPlanItemIfAbsent_returns_false_when_running_item_exists() {
    PlanItem item = PlanItem.create("binding-a", "worker-a", 0);
    plan.addPlanItemIfAbsent(item);
    item.setStatus(PlanItem.PlanItemStatus.RUNNING);
    PlanItem second = PlanItem.create("binding-a", "worker-a", 0);
    assertThat(plan.addPlanItemIfAbsent(second)).isFalse();
}

@Test
void addPlanItemIfAbsent_returns_true_when_prior_item_is_completed() {
    PlanItem first = PlanItem.create("binding-a", "worker-a", 0);
    plan.addPlanItemIfAbsent(first);
    first.setStatus(PlanItem.PlanItemStatus.COMPLETED);
    PlanItem second = PlanItem.create("binding-a", "worker-a", 0);
    // Completed item is no longer "active" — new item should be added
    assertThat(plan.addPlanItemIfAbsent(second)).isTrue();
}

@Test
void concurrent_addPlanItemIfAbsent_for_same_binding_adds_exactly_one() throws Exception {
    int threads = 10;
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
    java.util.concurrent.atomic.AtomicInteger addedCount = new java.util.concurrent.atomic.AtomicInteger(0);

    for (int i = 0; i < threads; i++) {
        int idx = i;
        Thread t = new Thread(() -> {
            try {
                start.await();
                PlanItem item = PlanItem.create("binding-a", "worker-" + idx, 0);
                if (plan.addPlanItemIfAbsent(item)) addedCount.incrementAndGet();
            } catch (InterruptedException ignored) {
            } finally {
                done.countDown();
            }
        });
        t.start();
    }

    start.countDown(); // release all threads simultaneously
    done.await(5, java.util.concurrent.TimeUnit.SECONDS);

    assertThat(addedCount.get()).as("Exactly one thread should have added the PlanItem").isEqualTo(1);
    assertThat(plan.getAgenda()).hasSize(1);
}
```

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run|FAIL"
```

- [ ] **Step 3: Add `addPlanItemIfAbsent` to `CasePlanModel` interface**

```java
// In CasePlanModel.java, add after addPlanItem():

/**
 * Atomically adds the given PlanItem only if no PENDING or RUNNING item exists
 * for the same binding name. Uses {@code ConcurrentHashMap.compute()} — the
 * check and insert are a single atomic operation with no TOCTOU window.
 *
 * @return true if the item was added; false if a duplicate was detected
 */
boolean addPlanItemIfAbsent(PlanItem planItem);
```

- [ ] **Step 4: Implement `addPlanItemIfAbsent` in `DefaultCasePlanModel`**

```java
// In DefaultCasePlanModel.java, add after addPlanItem():

@Override
public boolean addPlanItemIfAbsent(PlanItem item) {
    boolean[] added = {false};
    activeByBinding.compute(item.getBindingName(), (k, existing) -> {
        if (existing != null) {
            PlanItemStatus s = existing.getStatus();
            if (s == PlanItemStatus.PENDING || s == PlanItemStatus.RUNNING) {
                return existing; // active item present — reject new item
            }
            // existing is terminal — allow replacement
        }
        agenda.add(item);
        itemsById.put(item.getPlanItemId(), item);
        added[0] = true;
        return item;
    });
    return added[0];
}
```

- [ ] **Step 5: Update `PlanningStrategyLoopControl.select()` to use `addPlanItemIfAbsent`**

In `PlanningStrategyLoopControl.java`, replace the PlanItem creation block:

```java
// Before:
eligible.forEach(binding -> {
    if (!plan.hasActivePlanItem(binding.getName())) {
        String workerName = resolveWorkerName(binding, ctx);
        plan.addPlanItem(PlanItem.create(binding.getName(), workerName, 0));
    }
});

// After:
eligible.forEach(binding -> {
    String workerName = resolveWorkerName(binding, ctx);
    plan.addPlanItemIfAbsent(PlanItem.create(binding.getName(), workerName, 0));
});
```

The `hasActivePlanItem` check is now internal to `addPlanItemIfAbsent` — no separate guard needed.

- [ ] **Step 6: Run all tests — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

Expected: 74 tests (68 + 5 new addPlanItemIfAbsent + 1 concurrent), 0 failures.

- [ ] **Step 7: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/plan/CasePlanModel.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/plan/DefaultCasePlanModel.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/control/PlanningStrategyLoopControl.java \
         casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java
git commit -m "fix(blackboard): addPlanItemIfAbsent — atomic check-and-insert eliminates TOCTOU race (casehubio/engine#76)

ConcurrentHashMap.compute() merges the active-item check and insertion into a
single atomic operation. Two concurrent select() calls for the same binding
now guarantee exactly one PlanItem is created. Replaces the separate
hasActivePlanItem() guard in PlanningStrategyLoopControl."
```

---

## Self-Review

**Spec coverage:**
- A1 (PlanItem.status volatile) ✓ Task 1
- A7 (Stage.status AtomicReference CAS) ✓ Task 2
- A2/A3 (atomic addPlanItem) ✓ Task 3
- Rb2 (PlanItem volatile test) ✓ Task 1
- Rb5 (Stage AtomicReference test) ✓ Task 2
- Rb1 (concurrent insert test) ✓ Task 3

**Type consistency:** `addPlanItemIfAbsent(PlanItem)` defined in CasePlanModel, implemented in DefaultCasePlanModel, called in PlanningStrategyLoopControl — consistent throughout.
