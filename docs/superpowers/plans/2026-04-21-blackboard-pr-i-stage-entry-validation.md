# Blackboard PR-I: Stage Entry Condition Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make null entry conditions on Stage explicit rather than silent. Add `Stage.builder(name)` that requires `.entryCondition()` before `.build()`. Rename `Stage.create(name)` to `Stage.alwaysActivate(name)` for intentional always-activating stages. Matches treblereel's practice of requiring entry conditions.

**Architecture:** `Stage.builder(name)` is a new required-entry-condition builder. `Stage.alwaysActivate(name)` replaces `Stage.create(name)` for explicit always-activating stages. `Stage.create(name)` is kept as a package-private test utility (no API guarantee). All production code uses builder or alwaysActivate. Existing tests updated.

**Tech Stack:** Java 21, JUnit 5, AssertJ.

**Working directory:** `/Users/mdproctor/dev/casehub-engine/.worktrees/feat/casehub-blackboard`
**Branch:** create `feat/bb-qa-i-stage-entry-validation` from `feat/bb-qa-h-subcase`
**Run tests:** `TESTCONTAINERS_RYUK_DISABLED=true mvn clean test -pl casehub-blackboard`

---

## File Map

| File | Change |
|------|--------|
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java` | Add `builder(name)`, `alwaysActivate(name)`; keep `create(name)` package-private |
| `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.Builder` | NEW inner class — requires `entryCondition()` before `build()` |
| `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java` | Add builder tests; update `Stage.create()` calls to use builder or alwaysActivate |
| All IT test files that use `Stage.create()` | Update to `Stage.alwaysActivate()` or builder pattern |

---

## Task 1: Add `Stage.Builder` + `alwaysActivate` + tests

**Files:**
- Modify: `casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java`
- Test: `casehub-blackboard/src/test/java/io/casehub/blackboard/stage/StageTest.java`

- [ ] **Step 1: Write failing tests first**

```java
// Add to StageTest.java

@Test
void builder_requires_entry_condition() {
    assertThatThrownBy(() ->
        Stage.builder("intake").build()) // no entryCondition set
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("entryCondition");
}

@Test
void builder_with_entry_condition_creates_stage() {
    Stage stage = Stage.builder("intake")
        .entryCondition(ctx -> true)
        .build();

    assertThat(stage.getName()).isEqualTo("intake");
    assertThat(stage.getEntryCondition()).isNotNull();
    assertThat(stage.getStatus()).isEqualTo(StageStatus.PENDING);
}

@Test
void builder_fluent_api_retains_all_options() {
    Stage stage = Stage.builder("intake")
        .entryCondition(ctx -> true)
        .exitCondition(ctx -> false)
        .withManualActivation(true)
        .withAutocomplete(false)
        .build();

    assertThat(stage.getEntryCondition()).isNotNull();
    assertThat(stage.getExitCondition()).isNotNull();
    assertThat(stage.isManualActivation()).isTrue();
    assertThat(stage.isAutocomplete()).isFalse();
}

@Test
void alwaysActivate_creates_stage_with_null_entry_condition() {
    Stage stage = Stage.alwaysActivate("intake");

    assertThat(stage.getName()).isEqualTo("intake");
    assertThat(stage.getEntryCondition()).isNull(); // explicit always-activates
    assertThat(stage.getStatus()).isEqualTo(StageStatus.PENDING);
}
```

Add import: `import static org.assertj.core.api.Assertions.assertThatThrownBy;`

- [ ] **Step 2: Run — verify FAIL**

```bash
mvn test -pl casehub-blackboard -Dtest=StageTest 2>&1 | grep -E "Tests run:|FAIL" | head -5
```

- [ ] **Step 3: Add `Stage.Builder` and `Stage.alwaysActivate` to `Stage.java`**

Read `Stage.java` first. Then add:

```java
// Add to Stage.java:

/**
 * Creates a Stage with an explicit entry condition requirement.
 * Use {@link #alwaysActivate(String)} for stages that activate on every evaluation cycle.
 */
public static Builder builder(String name) {
    return new Builder(name);
}

/**
 * Creates a Stage that activates on every evaluation cycle (null entry condition).
 * Prefer this over {@link #create(String)} — the name makes the intent explicit.
 */
public static Stage alwaysActivate(String name) {
    return new Stage(name, null);
}

/**
 * Creates a Stage with a null entry condition. Use {@link #alwaysActivate(String)}
 * in production code — this factory is retained for test setup convenience.
 * @deprecated Use {@link #builder(String)} or {@link #alwaysActivate(String)}.
 */
@Deprecated(since = "PR-I", forRemoval = false)
public static Stage create(String name) {
    return new Stage(name, null);
}

/** Builder that requires an entry condition before constructing a Stage. */
public static final class Builder {
    private final String name;
    private ExpressionEvaluator entryCondition;
    private ExpressionEvaluator exitCondition;
    private boolean manualActivation = false;
    private boolean autocomplete = true;
    private String parentStageId;

    private Builder(String name) {
        this.name = java.util.Objects.requireNonNull(name, "name must not be null");
    }

    public Builder entryCondition(ExpressionEvaluator condition) {
        this.entryCondition = condition; return this;
    }

    public Builder entryCondition(java.util.function.Predicate<io.casehub.api.context.CaseContext> predicate) {
        this.entryCondition = new io.casehub.api.model.evaluator.LambdaExpressionEvaluator(predicate);
        return this;
    }

    public Builder exitCondition(ExpressionEvaluator condition) {
        this.exitCondition = condition; return this;
    }

    public Builder exitCondition(java.util.function.Predicate<io.casehub.api.context.CaseContext> predicate) {
        this.exitCondition = new io.casehub.api.model.evaluator.LambdaExpressionEvaluator(predicate);
        return this;
    }

    public Builder withManualActivation(boolean manual) { this.manualActivation = manual; return this; }
    public Builder withAutocomplete(boolean autocomplete) { this.autocomplete = autocomplete; return this; }
    public Builder withParentStage(String parentStageId) { this.parentStageId = parentStageId; return this; }

    public Stage build() {
        if (entryCondition == null) {
            throw new IllegalStateException(
                "Stage.builder(\"" + name + "\").build() requires entryCondition — " +
                "use Stage.alwaysActivate(\"" + name + "\") if the stage should activate on every cycle");
        }
        Stage stage = new Stage(name, entryCondition);
        stage.exitCondition = this.exitCondition;
        stage.manualActivation = this.manualActivation;
        stage.autocomplete = this.autocomplete;
        if (parentStageId != null) stage.parentStageId = parentStageId;
        return stage;
    }
}
```

You need to add a constructor `Stage(String name, ExpressionEvaluator entryCondition)` or adapt the existing constructor. Read `Stage.java` carefully to see the current constructor and field structure, then integrate cleanly.

- [ ] **Step 4: Update production test files that use `Stage.create()` to use `Stage.alwaysActivate()` or builder**

Search all test files:
```bash
grep -rn "Stage.create\|Stage\.create" casehub-blackboard/src/test/
```

For each `Stage.create("name")` that is meant to "always activate": replace with `Stage.alwaysActivate("name")`.
For each `Stage.create("name").withEntryCondition(...)`: replace with `Stage.builder("name").entryCondition(...).build()`.

Also search main sources (there shouldn't be any `Stage.create` there, but verify):
```bash
grep -rn "Stage.create" casehub-blackboard/src/main/
```

- [ ] **Step 5: Run all tests**

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test -pl casehub-blackboard 2>&1 | grep "Tests run" | tail -2
```

Expected: all previous tests + 4 new builder/alwaysActivate tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add casehub-blackboard/src/main/java/io/casehub/blackboard/stage/Stage.java
git add $(git diff --name-only casehub-blackboard/src/test/)
git commit -m "feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() for explicit always-on (casehubio/engine#76)

Stage.create(name) was silently always-activating when entryCondition was null.
builder(name) requires explicit .entryCondition() before .build() — throws
IllegalStateException if omitted. alwaysActivate(name) makes intent explicit.
Matches treblereel's practice of requiring entry conditions."
```
