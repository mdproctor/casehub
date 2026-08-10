# Blackboard PR-E: Data Model Edge Cases and Contract Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four targeted tests covering edge cases in `DefaultCasePlanModel`, the `PlanningStrategyContractTest`, and `Stage.fault()` — each small enough to write, run, and commit in a single task.

**Architecture:** Pure unit tests only — no CDI, no Quarkus. Each test is self-contained and tests one specific behaviour.

**Tech Stack:** Java 21, JUnit 5, AssertJ. No mocking framework needed.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`

**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

**Depends on:** PRs A, B, C, D completed.

---

## File Map

| File | Tests added |
|------|-------------|
| `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java` | N2 (`getTopPlanItems(0)`), N3 (type mismatch `get()`) |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/control/PlanningStrategyContractTest.java` | N6 (duplicate binding in eligible) |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java` | N7 (`fault()` from FAULTED is no-op) |

---

## Task 1: N2 — `getTopPlanItems(0)` and N3 — type mismatch `get()`

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java`

- [ ] **Step 1: Add tests**

```java
// Add to DefaultCasePlanModelTest.java

@Test
void getTopPlanItems_with_zero_limit_returns_empty() {
    plan.addPlanItem(PlanItem.create("b-a", "w-a", 5));
    plan.addPlanItem(PlanItem.create("b-b", "w-b", 3));

    assertThat(plan.getTopPlanItems(0))
        .as("getTopPlanItems(0) must return empty list, not NPE or all items")
        .isEmpty();
}

@Test
void get_returns_empty_when_stored_type_does_not_match_requested_type() {
    plan.put("count", 42); // stored as Integer

    assertThat(plan.get("count", String.class))
        .as("get() must return empty Optional when stored value type does not match, not ClassCastException")
        .isEmpty();

    // Original value still accessible with correct type
    assertThat(plan.get("count", Integer.class)).contains(42);
}
```

- [ ] **Step 2: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=DefaultCasePlanModelTest 2>&1 | grep "Tests run"
```

- [ ] **Step 3: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/plan/DefaultCasePlanModelTest.java
git commit -m "test(blackboard): N2 getTopPlanItems(0) returns empty; N3 type mismatch returns empty (casehubio/engine#76)"
```

---

## Task 2: N6 — Contract test rejects duplicate bindings in eligible list

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/control/PlanningStrategyContractTest.java`

- [ ] **Step 1: Add test**

```java
// Add to PlanningStrategyContractTest.java

@Test
void result_does_not_contain_duplicates_when_eligible_has_duplicates() {
    CasePlanModel plan = new DefaultCasePlanModel(UUID.randomUUID());
    Binding b1 = mock(Binding.class);
    // Pass the same binding reference twice — strategy must not return duplicates
    List<Binding> eligibleWithDuplicate = List.of(b1, b1);

    List<Binding> result = strategy().select(plan, ctx(), eligibleWithDuplicate)
        .await().indefinitely();

    assertThat(result)
        .as("strategy must not return duplicate bindings even if eligible list contains duplicates")
        .doesNotHaveDuplicates();
}
```

- [ ] **Step 2: Run — verify current behaviour**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=DefaultPlanningStrategyContractTest 2>&1 | grep -E "Tests run:|FAIL"
```

**Note:** `DefaultPlanningStrategy` returns `eligible` unchanged (`Uni.createFrom().item(eligible)`). If `eligible` contains duplicates, the result does too — this test will FAIL for `DefaultPlanningStrategy`. Fix it in the next step.

- [ ] **Step 3: Fix `DefaultPlanningStrategy` to deduplicate**

In `DefaultPlanningStrategy.java`, change `select()`:

```java
@Override
public Uni<List<Binding>> select(CasePlanModel plan,
                                  PlanExecutionContext context,
                                  List<Binding> eligible) {
    // Deduplicate by identity — preserve order, remove duplicate references
    List<Binding> deduped = eligible.stream().distinct().collect(java.util.stream.Collectors.toList());
    return Uni.createFrom().item(deduped);
}
```

`stream().distinct()` uses `equals()` / identity — `Binding` mocks without `equals` override use reference identity, which is correct here.

- [ ] **Step 4: Run — verify PASS**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest="DefaultPlanningStrategyContractTest,DefaultPlanningStrategyTest" 2>&1 | grep "Tests run"
```

- [ ] **Step 5: Run full suite**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/control/PlanningStrategyContractTest.java \
         casehub-blackboard/src/main/java/io/casehub/blackboard/control/DefaultPlanningStrategy.java
git commit -m "fix(blackboard): DefaultPlanningStrategy deduplicates eligible list; contract test verifies no-duplicates (casehubio/engine#76)"
```

---

## Task 3: N7 — `Stage.fault()` from FAULTED is a no-op

**File:** `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`

- [ ] **Step 1: Add tests**

```java
// Add to StageTest.java

@Test
void fault_from_pending_transitions_to_faulted() {
    Stage s = Stage.create("x");
    s.fault();
    assertThat(s.getStatus()).isEqualTo(StageStatus.FAULTED);
    assertThat(s.isTerminal()).isTrue();
}

@Test
void fault_from_faulted_is_noop() {
    Stage s = Stage.create("x");
    s.fault();
    s.fault(); // second call must be a no-op
    assertThat(s.getStatus())
        .as("fault() on an already-FAULTED stage must remain FAULTED (no state corruption)")
        .isEqualTo(StageStatus.FAULTED);
}

@Test
void fault_from_completed_is_noop() {
    Stage s = Stage.create("x");
    s.activate();
    s.complete();
    s.fault(); // must not overwrite COMPLETED with FAULTED
    assertThat(s.getStatus())
        .as("fault() must not overwrite a terminal COMPLETED state")
        .isEqualTo(StageStatus.COMPLETED);
}
```

- [ ] **Step 2: Run — verify PASS**

The existing `fault()` guard `if (!isTerminal())` already handles these. These tests are confirmatory — they should pass immediately.

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard -Dtest=StageTest 2>&1 | grep "Tests run"
```

Expected: all existing tests + 3 new = passing.

- [ ] **Step 3: Final full suite run**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -3
```

- [ ] **Step 4: Commit**

```bash
git add casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java
git commit -m "test(blackboard): N7 Stage.fault() from terminal states is no-op; N6 contract no-duplicates (casehubio/engine#76)"
```
