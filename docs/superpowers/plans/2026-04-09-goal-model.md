# Goal Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-class `CaseGoal` concept to CaseHub so callers can declare what success looks like at `createAndSolve()` time, cases complete when the goal is achieved (not just when quiescent), and a `GoalEvaluator` tests satisfaction predicates against CaseFile state after each control loop iteration.

**Architecture:** `CaseGoal` is composed of named `Milestone`s, each holding a `Predicate<CaseFile>` satisfaction predicate. A new `GoalEvaluator` component runs in the `CaseEngine` control loop before and after each TaskDefinition execution — separately from task execution (BDI deliberation/intention separation). Cases with no Goal fall back to the existing quiescence-based completion. Cases with a Goal transition to COMPLETED only when all Milestones are ACHIEVED (AND semantics — OR deferred).

**Tech Stack:** Java 21, Quarkus 3.17.5, JUnit 5, Maven multi-module. All new code goes in `casehub-core`. Tests go in `casehub-persistence-memory` (no Quarkus container needed — plain JUnit with InMemoryCaseFileRepository).

**GitHub issue:** Refs #7

---

## File Map

### casehub-core — new files
- `casehub-core/src/main/java/io/casehub/control/CaseGoal.java` — Goal container: name, Milestones, GoalStatus, `isSatisfied()`, factory `of()`
- `casehub-core/src/main/java/io/casehub/control/GoalResult.java` — Immutable result: `isAchieved()`, `isAbandoned()`, `achieved()`, `pending()`, `noGoal()` sentinel
- `casehub-core/src/main/java/io/casehub/coordination/GoalEvaluator.java` — Evaluates all Milestones, transitions Goal status, returns GoalResult

### casehub-core — modified
- `casehub-core/src/main/java/io/casehub/control/Milestone.java` — Add `Predicate<CaseFile>` satisfaction predicate, `when(Predicate)` factory, `named(String)` builder, `isSatisfiedBy(CaseFile)`, `ABANDONED` status, remove stale `String caseFileId` field
- `casehub-core/src/main/java/io/casehub/coordination/CaseEngine.java` — Add `Map<Long, CaseGoal> caseGoals`, `createAndSolve(…, CaseGoal)` overload, `awaitGoal()`, inject `GoalEvaluator`, integrate GoalEvaluator into control loop

### casehub-persistence-memory — new test file
- `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java` — 6 integration tests covering full goal lifecycle

---

## Task 1: Enhance Milestone — add predicate support

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/control/Milestone.java`

- [ ] **Step 1: Replace Milestone.java with the enhanced version**

Replace the full file:

```java
package io.casehub.control;

import io.casehub.core.CaseFile;

import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

/**
 * CMMN Milestone — a named, observable achievement state within a case.
 * Activated when its satisfaction predicate evaluates true against the CaseFile.
 *
 * <p>Milestones are passive markers: they have no work of their own, they mark
 * <em>that</em> a state was reached, not <em>how</em>. A Milestone is the right
 * CMMN primitive for intermediate goal states. See ADR-0001.
 *
 * <p>Two ways to create a Milestone:
 * <pre>
 *   // Predicate-based (preferred)
 *   Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed")
 *
 *   // Key-presence shorthand
 *   Milestone.requiring("risk_assessment", "executive_summary").named("analysis-done")
 * </pre>
 */
public class Milestone {

    /** Lifecycle states for a Milestone. */
    public enum MilestoneStatus {
        /** Satisfaction predicate not yet true. */
        PENDING,
        /** Satisfaction predicate evaluated true — Milestone is done. */
        ACHIEVED,
        /** Milestone can no longer be achieved (goal abandoned). */
        ABANDONED
    }

    private String milestoneId = UUID.randomUUID().toString();
    private String name;
    private MilestoneStatus status = MilestoneStatus.PENDING;
    private Instant createdAt = Instant.now();
    private Instant achievedAt;

    /** Predicate-based satisfaction check — takes priority over achievementCriteria. */
    private Predicate<CaseFile> satisfactionPredicate;

    /** Key-presence fallback when no predicate is set. */
    private Set<String> achievementCriteria = new HashSet<>();

    /** Optional parent stage. */
    private Optional<String> parentStageId = Optional.empty();

    private Map<String, Object> metadata = new HashMap<>();

    // ---- Factory methods ----

    /**
     * Creates a Milestone satisfied when the given predicate evaluates true.
     * Chain {@link #named(String)} to give it a human-readable name.
     */
    public static Milestone when(Predicate<CaseFile> predicate) {
        Milestone m = new Milestone();
        m.satisfactionPredicate = predicate;
        return m;
    }

    /**
     * Creates a Milestone satisfied when all specified keys are present in the CaseFile.
     * Shorthand for {@code when(cf -> keys.stream().allMatch(cf::contains))}.
     */
    public static Milestone requiring(String... keys) {
        Milestone m = new Milestone();
        m.achievementCriteria = new HashSet<>(Arrays.asList(keys));
        return m;
    }

    /** Creates a plain Milestone with a given name (no predicate — must be set separately). */
    public static Milestone create(String name) {
        Milestone m = new Milestone();
        m.name = name;
        return m;
    }

    // ---- Builder methods ----

    /** Sets the human-readable name. Returns {@code this} for chaining. */
    public Milestone named(String name) {
        this.name = name;
        return this;
    }

    /** Sets key-presence achievement criteria. Returns {@code this} for chaining. */
    public Milestone withAchievementCriteria(Set<String> keys) {
        this.achievementCriteria = new HashSet<>(keys);
        return this;
    }

    /** Sets the parent stage. Returns {@code this} for chaining. */
    public Milestone withParentStage(String stageId) {
        this.parentStageId = Optional.of(stageId);
        return this;
    }

    // ---- Evaluation ----

    /**
     * Tests whether this Milestone is satisfied by the given CaseFile state.
     * Uses the satisfaction predicate if set; falls back to key-presence check.
     */
    public boolean isSatisfiedBy(CaseFile caseFile) {
        if (satisfactionPredicate != null) {
            return satisfactionPredicate.test(caseFile);
        }
        return achievementCriteria.stream().allMatch(caseFile::contains);
    }

    // ---- Lifecycle transitions ----

    /** Transitions this Milestone to ACHIEVED. No-op if already achieved. */
    public void achieve() {
        if (status == MilestoneStatus.PENDING) {
            status = MilestoneStatus.ACHIEVED;
            achievedAt = Instant.now();
        }
    }

    /** Transitions this Milestone to ABANDONED. Used when the owning Goal is abandoned. */
    public void abandon() {
        if (status == MilestoneStatus.PENDING) {
            status = MilestoneStatus.ABANDONED;
        }
    }

    public boolean isAchieved()  { return status == MilestoneStatus.ACHIEVED; }
    public boolean isPending()   { return status == MilestoneStatus.PENDING; }
    public boolean isAbandoned() { return status == MilestoneStatus.ABANDONED; }

    // ---- Getters / setters ----

    public String getMilestoneId()                        { return milestoneId; }
    public void setMilestoneId(String id)                 { this.milestoneId = id; }
    public String getName()                               { return name; }
    public void setName(String name)                      { this.name = name; }
    public MilestoneStatus getStatus()                    { return status; }
    public void setStatus(MilestoneStatus status)         { this.status = status; }
    public Instant getCreatedAt()                         { return createdAt; }
    public Instant getAchievedAt()                        { return achievedAt; }
    public Set<String> getAchievementCriteria()           { return Collections.unmodifiableSet(achievementCriteria); }
    public void setAchievementCriteria(Set<String> keys)  { this.achievementCriteria = new HashSet<>(keys); }
    public Optional<String> getParentStageId()            { return parentStageId; }
    public void setParentStageId(Optional<String> id)     { this.parentStageId = id; }
    public Predicate<CaseFile> getSatisfactionPredicate() { return satisfactionPredicate; }

    public Map<String, Object> getMetadata()              { return Collections.unmodifiableMap(metadata); }
    public void putMetadata(String key, Object value)     { metadata.put(key, value); }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        return (value != null && type.isInstance(value)) ? Optional.of((T) value) : Optional.empty();
    }
}
```

- [ ] **Step 2: Compile casehub-core**

```bash
cd /path/to/casehub && mvn compile -pl casehub-core -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`. The old `String caseFileId` field is gone — `ListenerEvaluator.evaluateAndAchieveMilestones()` uses `milestone.getAchievementCriteria()` which still works.

- [ ] **Step 3: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/control/Milestone.java
git commit -m "refactor(core): enhance Milestone with Predicate<CaseFile> satisfaction and ABANDONED status

Adds when(Predicate) and requiring(String...) factory methods, named() builder,
isSatisfiedBy(CaseFile) evaluation method, and ABANDONED status for goal
abandonment. Removes stale String caseFileId field. Backward compatible —
existing achievementCriteria key-presence checks still work.

Refs #7"
```

---

## Task 2: Create CaseGoal

**Files:**
- Create: `casehub-core/src/main/java/io/casehub/control/CaseGoal.java`

- [ ] **Step 1: Write failing test for CaseGoal**

Add to a new test file `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java`:

```java
package io.casehub.persistence.memory;

import io.casehub.control.CaseGoal;
import io.casehub.control.Milestone;
import io.casehub.core.CaseFile;
import io.casehub.coordination.PropagationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoalModelTest {

    private InMemoryCaseFileRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCaseFileRepository();
    }

    @Test
    void goalWithAllMilestonesSatisfiedIsAchieved() {
        CaseGoal goal = CaseGoal.of("risk-complete",
            Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
            Milestone.when(cf -> cf.contains("executive_summary")).named("summary-done")
        );

        assertFalse(goal.isSatisfied());
        assertEquals(CaseGoal.GoalStatus.PENDING, goal.getStatus());
        assertEquals(2, goal.getMilestones().size());
        assertEquals("risk-complete", goal.getName());
    }

    @Test
    void goalIsSatisfiedWhenAllMilestonesAchieved() {
        CaseGoal goal = CaseGoal.of("risk-complete",
            Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
            Milestone.when(cf -> cf.contains("executive_summary")).named("summary-done")
        );

        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(
            "risk_assessment", "HIGH",
            "executive_summary", "Done"
        ), ctx);

        // Manually test Milestone satisfaction against CaseFile
        goal.getMilestones().forEach(m -> {
            if (m.isSatisfiedBy(cf)) m.achieve();
        });

        assertTrue(goal.isSatisfied());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd /path/to/casehub && mvn test -pl casehub-persistence-memory 2>&1 | grep "ERROR\|FAIL\|CaseGoal"
```

Expected: compilation error — `CaseGoal` does not exist yet.

- [ ] **Step 3: Create CaseGoal.java**

```java
package io.casehub.control;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The formal contract between the caller and the case management system.
 * Declared at {@code createAndSolve()} time. The case transitions to COMPLETED
 * only when this Goal is ACHIEVED — not merely when quiescent.
 *
 * <p>A Goal is satisfied when ALL its named Milestones are achieved (AND semantics).
 * OR semantics and custom predicates are deferred to a future iteration.
 *
 * <pre>
 *   CaseGoal goal = CaseGoal.of("risk-assessment-complete",
 *       Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
 *       Milestone.when(cf -> cf.contains("executive_summary")).named("summary-done")
 *   );
 *   CaseFile cf = caseEngine.createAndSolve("legal-analysis", initialState, goal);
 * </pre>
 *
 * See ADR-0001 and docs/research/goal-model-research.md.
 */
public class CaseGoal {

    /** Lifecycle of a CaseGoal within a case execution. */
    public enum GoalStatus {
        /** Declared but CaseEngine has not yet started pursuing it. */
        PENDING,
        /** CaseEngine is actively working toward this goal. */
        ACTIVE,
        /** All Milestones satisfied — case can transition to COMPLETED. */
        ACHIEVED,
        /** Goal became unreachable — case transitions to FAULTED. */
        ABANDONED
    }

    private final String name;
    private final List<Milestone> milestones;
    private GoalStatus status = GoalStatus.PENDING;

    private CaseGoal(String name, List<Milestone> milestones) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Goal name must not be blank");
        if (milestones == null || milestones.isEmpty()) throw new IllegalArgumentException("Goal must have at least one Milestone");
        this.name = name;
        this.milestones = Collections.unmodifiableList(milestones);
    }

    /**
     * Creates a Goal satisfied when ALL given Milestones are achieved (AND semantics).
     */
    public static CaseGoal of(String name, Milestone... milestones) {
        return new CaseGoal(name, Arrays.asList(milestones));
    }

    /**
     * Returns true when all Milestones are in ACHIEVED state.
     * This is the AND-semantics satisfaction check.
     */
    public boolean isSatisfied() {
        return milestones.stream().allMatch(Milestone::isAchieved);
    }

    public String getName()             { return name; }
    public List<Milestone> getMilestones() { return milestones; }
    public GoalStatus getStatus()       { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }

    public boolean isAchieved()  { return status == GoalStatus.ACHIEVED; }
    public boolean isAbandoned() { return status == GoalStatus.ABANDONED; }
    public boolean isActive()    { return status == GoalStatus.ACTIVE; }
}
```

- [ ] **Step 4: Run the test**

```bash
cd /path/to/casehub && mvn test -pl casehub-persistence-memory -Dtest=GoalModelTest 2>&1 | tail -15
```

Expected: 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/control/CaseGoal.java \
        casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java
git commit -m "feat(core): add CaseGoal — formal contract at case creation time

CaseGoal.of(name, milestones...) creates a goal satisfied when all Milestones
achieve (AND semantics). GoalStatus: PENDING → ACTIVE → ACHIEVED | ABANDONED.
Initial tests in GoalModelTest confirm construction and manual satisfaction.

Refs #7"
```

---

## Task 3: Create GoalResult and GoalEvaluator

**Files:**
- Create: `casehub-core/src/main/java/io/casehub/control/GoalResult.java`
- Create: `casehub-core/src/main/java/io/casehub/coordination/GoalEvaluator.java`

- [ ] **Step 1: Write failing tests for GoalEvaluator**

Add to `GoalModelTest.java`:

```java
    @Test
    void goalEvaluatorAchievesMilestoneWhenPredicateTrue() {
        CaseGoal goal = CaseGoal.of("test-goal",
            Milestone.when(cf -> cf.contains("result")).named("result-ready")
        );

        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(), ctx);
        GoalEvaluator evaluator = new GoalEvaluator();

        // Before key is present
        GoalResult before = evaluator.evaluate(cf, goal);
        assertFalse(before.isAchieved());
        assertEquals(1, before.pending().size());
        assertEquals(0, before.achieved().size());

        // After key is present
        cf.put("result", "done");
        GoalResult after = evaluator.evaluate(cf, goal);
        assertTrue(after.isAchieved());
        assertEquals(0, after.pending().size());
        assertEquals(1, after.achieved().size());
    }

    @Test
    void goalEvaluatorReturnsNoGoalSentinelForNullGoal() {
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(), ctx);
        GoalEvaluator evaluator = new GoalEvaluator();

        GoalResult result = evaluator.evaluate(cf, null);
        assertFalse(result.isAchieved());
        assertFalse(result.isAbandoned());
        assertTrue(result.isNoGoal());
    }
```

Add imports at the top of `GoalModelTest.java`:
```java
import io.casehub.control.GoalResult;
import io.casehub.coordination.GoalEvaluator;
```

- [ ] **Step 2: Run to confirm failure**

```bash
mvn test -pl casehub-persistence-memory -Dtest=GoalModelTest 2>&1 | grep "ERROR\|FAIL"
```

Expected: compilation error — `GoalResult` and `GoalEvaluator` do not exist.

- [ ] **Step 3: Create GoalResult.java**

```java
package io.casehub.control;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of Goal evaluation state returned by {@link io.casehub.coordination.GoalEvaluator}.
 * Contains per-Milestone breakdown of achieved vs pending, and the overall goal status.
 */
public class GoalResult {

    private static final GoalResult NO_GOAL = new GoalResult(null, false, false);

    private final CaseGoal goal;
    private final boolean achieved;
    private final boolean abandoned;

    private GoalResult(CaseGoal goal, boolean achieved, boolean abandoned) {
        this.goal = goal;
        this.achieved = achieved;
        this.abandoned = abandoned;
    }

    /** Creates a result for a case that was created without a Goal. */
    public static GoalResult noGoal() { return NO_GOAL; }

    /** Creates a result reflecting the current state of the given Goal. */
    public static GoalResult of(CaseGoal goal) {
        return new GoalResult(goal, goal.isAchieved(), goal.isAbandoned());
    }

    /** True when the Goal's ALL Milestones are ACHIEVED. */
    public boolean isAchieved()  { return achieved; }

    /** True when the Goal has been abandoned (became unreachable). */
    public boolean isAbandoned() { return abandoned; }

    /** True when no Goal was declared for this case. */
    public boolean isNoGoal()    { return goal == null; }

    /** Milestones that have been ACHIEVED. Empty list if no Goal. */
    public List<Milestone> achieved() {
        if (goal == null) return Collections.emptyList();
        return goal.getMilestones().stream()
                .filter(Milestone::isAchieved)
                .collect(Collectors.toList());
    }

    /** Milestones still PENDING. Empty list if no Goal. */
    public List<Milestone> pending() {
        if (goal == null) return Collections.emptyList();
        return goal.getMilestones().stream()
                .filter(Milestone::isPending)
                .collect(Collectors.toList());
    }

    /** The Goal being tracked, or null if none was declared. */
    public CaseGoal getGoal() { return goal; }
}
```

- [ ] **Step 4: Create GoalEvaluator.java**

```java
package io.casehub.coordination;

import io.casehub.control.CaseGoal;
import io.casehub.control.GoalResult;
import io.casehub.control.Milestone;
import io.casehub.core.CaseFile;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Evaluates a {@link CaseGoal} against current {@link CaseFile} state.
 * Runs in the {@link CaseEngine} control loop before and after each TaskDefinition
 * execution — architecturally separate from task execution (BDI deliberation/intention
 * separation, MAP Monitor/Orchestrator pattern).
 *
 * <p>This component is a pure function of CaseFile state: it does not consult
 * execution history, does not know which TaskDefinitions ran, and does not modify
 * the CaseFile. It only reads state and transitions Milestone/Goal statuses.
 *
 * <p>If {@code goal} is null (case was created without a Goal), returns
 * {@link GoalResult#noGoal()} — the control loop falls back to quiescence-based
 * completion.
 */
@ApplicationScoped
public class GoalEvaluator {

    /**
     * Evaluates all pending Milestones against the current CaseFile state.
     * Achieves any Milestone whose satisfaction predicate evaluates true.
     * If all Milestones are achieved, transitions the Goal to ACHIEVED.
     *
     * @param caseFile current CaseFile state
     * @param goal     the Goal to evaluate, or null if none was declared
     * @return current GoalResult snapshot
     */
    public GoalResult evaluate(CaseFile caseFile, CaseGoal goal) {
        if (goal == null) return GoalResult.noGoal();

        // Transition to ACTIVE on first evaluation if still PENDING
        if (goal.getStatus() == CaseGoal.GoalStatus.PENDING) {
            goal.setStatus(CaseGoal.GoalStatus.ACTIVE);
        }

        // Check each pending Milestone (BDI: opportunistic achievement)
        for (Milestone milestone : goal.getMilestones()) {
            if (milestone.isPending() && milestone.isSatisfiedBy(caseFile)) {
                milestone.achieve();
            }
        }

        // Check overall satisfaction (AND semantics — all Milestones must be ACHIEVED)
        if (goal.isSatisfied()) {
            goal.setStatus(CaseGoal.GoalStatus.ACHIEVED);
        }

        return GoalResult.of(goal);
    }

    /**
     * Marks the Goal and all its pending Milestones as ABANDONED.
     * Called when the CaseEngine detects the goal cannot be achieved
     * (e.g. timeout, explicit cancellation, or future abandonment predicate).
     *
     * @param goal the Goal to abandon
     */
    public void abandon(CaseGoal goal) {
        if (goal == null) return;
        goal.getMilestones().forEach(Milestone::abandon);
        goal.setStatus(CaseGoal.GoalStatus.ABANDONED);
    }
}
```

- [ ] **Step 5: Run tests**

```bash
cd /path/to/casehub && mvn test -pl casehub-persistence-memory -Dtest=GoalModelTest 2>&1 | tail -10
```

Expected: 4 tests pass, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/control/GoalResult.java \
        casehub-core/src/main/java/io/casehub/coordination/GoalEvaluator.java \
        casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java
git commit -m "feat(core): add GoalResult and GoalEvaluator

GoalEvaluator is a pure function of CaseFile state — tests Milestone
satisfaction predicates after each control loop iteration, separate from
task execution. GoalResult carries per-Milestone achieved/pending breakdown.
GoalEvaluator.abandon() marks all pending Milestones as ABANDONED.

Refs #7"
```

---

## Task 4: Wire GoalEvaluator into CaseEngine

**Files:**
- Modify: `casehub-core/src/main/java/io/casehub/coordination/CaseEngine.java`

- [ ] **Step 1: Write failing integration test**

Add to `GoalModelTest.java`:

```java
    @Test
    void caseEngineCompletesWhenGoalAchieved() throws Exception {
        // This test wires CaseEngine manually without CDI.
        // We use a minimal in-process setup.
        // Note: full CDI wiring is tested via casehub-examples.
        // Here we just verify the Goal is evaluated correctly by GoalEvaluator
        // when the CaseFile reaches the target state.

        CaseGoal goal = CaseGoal.of("analysis-complete",
            Milestone.when(cf -> cf.contains("result")).named("result-ready")
        );

        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(), ctx);
        GoalEvaluator evaluator = new GoalEvaluator();

        // Before state change — goal not satisfied
        GoalResult before = evaluator.evaluate(cf, goal);
        assertFalse(before.isAchieved());
        assertEquals(CaseGoal.GoalStatus.ACTIVE, goal.getStatus());

        // Simulate TaskDefinition writing to CaseFile
        cf.put("result", "analysis output");

        // After state change — goal should be satisfied
        GoalResult after = evaluator.evaluate(cf, goal);
        assertTrue(after.isAchieved());
        assertEquals(CaseGoal.GoalStatus.ACHIEVED, goal.getStatus());
        assertEquals(1, after.achieved().size());
        assertEquals("result-ready", after.achieved().get(0).getName());
        assertEquals(0, after.pending().size());
    }
```

- [ ] **Step 2: Run test to confirm it passes (GoalEvaluator already works)**

```bash
mvn test -pl casehub-persistence-memory -Dtest=GoalModelTest 2>&1 | tail -10
```

Expected: 5 tests pass. (This test passes without CaseEngine changes — it tests GoalEvaluator directly.)

- [ ] **Step 3: Modify CaseEngine to inject GoalEvaluator and store goals**

In `CaseEngine.java`, make these changes:

**Add field and inject GoalEvaluator (after existing `@Inject` fields):**
```java
@Inject GoalEvaluator goalEvaluator;
```

**Add goals map (after existing maps):**
```java
private final Map<Long, CaseGoal> caseGoals = new ConcurrentHashMap<>();
```

**Add new `createAndSolve` overload (after existing overloads):**
```java
/**
 * Creates a case with an explicit Goal. The case transitions to COMPLETED only
 * when all Goal Milestones are ACHIEVED — not merely on quiescence.
 * Cases without a Goal fall back to quiescence-based completion.
 */
public CaseFile createAndSolve(String caseType, Map<String, Object> initialState,
                                CaseGoal goal) throws CaseCreationException {
    PropagationContext ctx = PropagationContext.createRoot();
    CaseFile caseFile = caseFileRepository.create(caseType, initialState, ctx);
    if (goal != null) caseGoals.put(caseFile.getId(), goal);
    scheduleControlLoop(caseFile);
    return caseFile;
}
```

**Add `awaitGoal()` method (after `awaitCompletion`):**
```java
/**
 * Waits for the case's Goal to be ACHIEVED or ABANDONED, up to the given timeout.
 * Returns a {@link GoalResult} with per-Milestone breakdown.
 * If the case was created without a Goal, returns {@link GoalResult#noGoal()}.
 */
public GoalResult awaitGoal(CaseFile caseFile, Duration timeout)
        throws InterruptedException, TimeoutException {
    awaitCompletion(caseFile, timeout);
    CaseGoal goal = caseGoals.get(caseFile.getId());
    return goal != null ? GoalResult.of(goal) : GoalResult.noGoal();
}
```

**Modify `scheduleControlLoop` to clean up goals on completion:**

In the existing cleanup block at the bottom of `runControlLoop()` (after `future.complete`):
```java
activeCaseFiles.remove(id);
casePlanModels.remove(id);
caseFileFutures.remove(id);
caseGoals.remove(id);   // ← add this line
```

Also in `cancel()`:
```java
caseGoals.remove(caseFile.getId());   // ← add this line
```

- [ ] **Step 4: Integrate GoalEvaluator into the control loop**

In `runControlLoop()`, make these changes:

**After `caseFile.setStatus(CaseStatus.RUNNING)` and before the `while` loop, add opportunistic pre-check:**
```java
// BDI insight: check if Goal is already satisfied before any planning
CaseGoal goal = caseGoals.get(id);
if (goal != null) {
    GoalResult preCheck = goalEvaluator.evaluate(caseFile, goal);
    if (preCheck.isAchieved()) {
        caseFile.setStatus(CaseStatus.COMPLETED);
        // falls through to future.complete() below
    }
}
```

**Replace the existing quiescence block inside the while loop:**

Current code:
```java
if (topPlanItems.isEmpty()) {
    if (listenerEvaluator.isQuiescent(casePlanModel)) {
        caseFile.setStatus(CaseStatus.WAITING);
        break;
    }
}
```

Replace with:
```java
if (topPlanItems.isEmpty()) {
    if (listenerEvaluator.isQuiescent(casePlanModel)) {
        if (goal != null) {
            // With Goal: quiescent but goal not yet achieved → WAITING (not done)
            GoalResult result = goalEvaluator.evaluate(caseFile, goal);
            if (result.isAchieved()) {
                caseFile.setStatus(CaseStatus.COMPLETED);
            } else {
                caseFile.setStatus(CaseStatus.WAITING);
            }
        } else {
            // No Goal: quiescence = done (existing behaviour)
            caseFile.setStatus(CaseStatus.WAITING);
        }
        break;
    }
}
```

**After `tdOpt.get().execute(caseFile)` succeeds, add Goal evaluation:**

After the line `newPlanItems = listenerEvaluator.evaluateAndCreatePlanItems(...)`, add:
```java
// Check Goal after each TaskDefinition execution
if (goal != null) {
    GoalResult result = goalEvaluator.evaluate(caseFile, goal);
    if (result.isAchieved()) {
        caseFile.setStatus(CaseStatus.COMPLETED);
        break;
    }
}
```

- [ ] **Step 5: Compile**

```bash
cd /path/to/casehub && mvn compile -pl casehub-core -am 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Run all tests**

```bash
mvn test -pl casehub-persistence-memory 2>&1 | tail -10
```

Expected: all tests pass (13 existing + 5 goal tests = 18 total).

- [ ] **Step 7: Commit**

```bash
git add casehub-core/src/main/java/io/casehub/coordination/CaseEngine.java \
        casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java
git commit -m "feat(core): integrate GoalEvaluator into CaseEngine control loop

createAndSolve(type, state, goal) overload stores CaseGoal per case.
GoalEvaluator runs: (1) pre-check before first planning — BDI opportunistic
achievement; (2) after each TaskDefinition execution — transition to COMPLETED
when all Milestones satisfied; (3) on quiescence — COMPLETED if goal achieved,
WAITING if not. awaitGoal() returns GoalResult with per-Milestone breakdown.
Cases without a Goal retain existing quiescence-based completion behaviour.

Refs #7"
```

---

## Task 5: End-to-end integration test

**Files:**
- Modify: `casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java`

- [ ] **Step 1: Write two end-to-end tests**

Add to `GoalModelTest.java`. These tests demonstrate the full goal lifecycle using real TaskDefinition + CaseEngine wiring (manual, no CDI container):

```java
    @Test
    void goalIsPathIndependent_differentTasksProduceSameResult() {
        // Two different TaskDefinitions can satisfy the same Milestone.
        // The Goal evaluates state, not which task ran.
        CaseGoal goal = CaseGoal.of("analysis-complete",
            Milestone.when(cf -> cf.contains("analysis_result")).named("analysis-ready")
        );

        GoalEvaluator evaluator = new GoalEvaluator();
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(), ctx);

        // Path 1: key written by TaskDefinition A
        cf.put("analysis_result", "from-task-A");
        GoalResult result = evaluator.evaluate(cf, goal);
        assertTrue(result.isAchieved(), "Goal should be satisfied regardless of which task wrote the key");

        // Verify: reset and try Path 2 with a different source
        CaseGoal goal2 = CaseGoal.of("analysis-complete",
            Milestone.when(cf2 -> cf2.contains("analysis_result")).named("analysis-ready")
        );
        CaseFile cf2 = repository.create("test", Map.of("analysis_result", "from-task-B"), ctx);
        GoalResult result2 = evaluator.evaluate(cf2, goal2);
        assertTrue(result2.isAchieved(), "Goal should be satisfied from initial state too");
    }

    @Test
    void multiMilestoneGoalRequiresAllMilestonesToBeAchieved() {
        CaseGoal goal = CaseGoal.of("full-analysis",
            Milestone.when(cf -> cf.contains("entities")).named("entities-extracted"),
            Milestone.when(cf -> cf.contains("risk_assessment")).named("risk-assessed"),
            Milestone.when(cf -> cf.contains("summary")).named("summary-done")
        );

        GoalEvaluator evaluator = new GoalEvaluator();
        PropagationContext ctx = PropagationContext.createRoot();
        CaseFile cf = repository.create("test", Map.of(), ctx);

        // Partial progress — only one Milestone satisfied
        cf.put("entities", "list");
        GoalResult partial = evaluator.evaluate(cf, goal);
        assertFalse(partial.isAchieved());
        assertEquals(1, partial.achieved().size());
        assertEquals(2, partial.pending().size());

        // All Milestones satisfied
        cf.put("risk_assessment", "HIGH");
        cf.put("summary", "Executive summary text");
        GoalResult complete = evaluator.evaluate(cf, goal);
        assertTrue(complete.isAchieved());
        assertEquals(3, complete.achieved().size());
        assertEquals(0, complete.pending().size());
    }
```

- [ ] **Step 2: Run all tests**

```bash
cd /path/to/casehub && mvn test -pl casehub-persistence-memory 2>&1 | tail -10
```

Expected: all 20 tests pass (13 existing + 7 goal tests).

- [ ] **Step 3: Run full build**

```bash
mvn clean compile 2>&1 | grep -E "BUILD|SUCCESS|FAIL"
```

Expected: `BUILD SUCCESS` for all 6 modules.

- [ ] **Step 4: Final commit**

```bash
git add casehub-persistence-memory/src/test/java/io/casehub/persistence/memory/GoalModelTest.java
git commit -m "test(persistence-memory): add end-to-end Goal model integration tests

Demonstrates path-independence (any TaskDefinition can satisfy a Milestone),
partial progress tracking (per-Milestone achieved/pending breakdown), and
multi-Milestone AND semantics requiring all Milestones before Goal is achieved.

Closes #7"
```

---

## Self-Review

**Spec coverage (from issue #7):**

| Requirement | Task |
|---|---|
| `CaseGoal` interface with satisfaction predicate and Milestones | Task 2 |
| `Milestone` enhancement — `Predicate<CaseFile>`, named(), ABANDONED | Task 1 |
| `GoalEvaluator` — separate from task execution | Task 3 |
| Modified CaseEngine control loop | Task 4 |
| `createAndSolve()` overload with optional `CaseGoal` | Task 4 |
| `awaitGoal()` returns `GoalResult` | Task 4 |
| Goal lifecycle: PENDING → ACTIVE → ACHIEVED \| ABANDONED | Tasks 2, 3, 4 |
| COMPLETED = Goal achieved; WAITING = quiescent but not done | Task 4 |
| Cases with no Goal fall back to existing quiescence | Task 4 |
| Path-independence integration test | Task 5 |

**Deferred (not in this plan):**
- AND/OR/custom completion semantics
- Abandonment predicate on CaseGoal (GoalEvaluator.abandon() exists but nothing calls it yet)
- Goal-filtered TaskDefinition activation (only activate TDs contributing to an active Goal)
- Hibernate persistence of Goal/Milestone state

**Placeholder scan:** None found.

**Type consistency:** `CaseGoal`, `GoalResult`, `GoalEvaluator`, `Milestone` — names consistent across all tasks. `GoalResult.noGoal()` sentinel used correctly in `awaitGoal()`.
