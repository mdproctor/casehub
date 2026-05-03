# Engine Reconstruction Plan

**Purpose:** Curated commit history for `casehubio/engine` — restore mdproctor's squash commits to granular form, apply squash policy, leave treblereel squash commits unchanged.

**Baseline:** `upstream/main_20260502` — snapshot of casehubio/engine main at reconstruction time.

**Policy reference:** `commit-squash-policy.md`

---

## Summary

| Metric | Count |
|--------|-------|
| Total PRs | 86 |
| Treblereel PRs (kept as squash) | 27 |
| mdproctor/casehubio PRs (reconstructed) | 59 |
| Original commits on mdproctor branches (raw) | ~180 |
| Proposed commits after curation | ~105 |

> **Note on stacked PRs:** Several mdproctor PR series (e.g. #140–#144, #91–#100) used a stacked branch strategy where each branch contained all commits from the previous. The commits shown per PR are the unique commits added in that PR only (derived from GitHub PR commit lists), not the full branch log.

> **Note on PRs #52, #53, #54:** These were opened on casehubio/engine branches and merged into casehubio/engine main. Their content was subsequently consolidated into PR #126 (from mdproctor/engine:main). Both the original casehubio merges and the mdproctor consolidation are documented. The reconstruction uses the PR #126 consolidated commit as the canonical entry.

> **Note on PR #60 and #61:** Both were merged into casehubio/engine main but their content does not appear in `main_20260502` as distinct commits — PR #60's rename work was superseded by PR #62; PR #61's ContextDiffStrategy content was folded into PR #62 or the upstream rebase. Both are noted as superseded.

---

## PR #1 — initial schema/models (2026-04-08) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `761b8ca` initial schema/models | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #24 — initial README (2026-04-09) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `ac51bd6` initial README | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #26 — Add GitHub Action to run Maven tests for pull requests, fixes #25 (2026-04-10) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `9b31438` Add GitHub Action to run Maven tests for pull requests, fixes #25 | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #28 — Improve build configuration and code quality enforcement, fixes #27 (2026-04-13) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `2162300` Improve build configuration and code quality enforcement, fixes #27 | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #32 — feat(engine): add LoopControl SPI for pluggable dispatch rule selection (2026-04-14) [MDPROCTOR]

**Branch:** `feat/loop-control-spi`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f488145` feat(engine): add LoopControl SPI for pluggable dispatch rule selection | ✅ KEEP | `feat(engine): add LoopControl SPI for pluggable dispatch rule selection` |

---

## PR #36 — Duplicate signal can trigger the same worker twice despite deduplication, fixes #29 (2026-04-14) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `2e66a8d` Duplicate signal can trigger the same worker twice despite deduplication, fixes #29 | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #38 — refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition (2026-04-14) [MDPROCTOR]

**Branch:** `feat/rename-binding-casedefinition` (first merge — rename work only)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `2ca7bfb` refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition | ✅ KEEP | `refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition` (unified with 441213d + 5ac72ea + 5f356b0) |
| `441213d` chore: remove .claude/ from tracking, add to .gitignore | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, no issue ref)* |
| `5f356b0` docs(api): add Javadoc clarifying Goal vs Milestone distinction and unified Milestone design | 🔽 SQUASH ↑ | *(absorbed — Javadoc follows rename commit)* |
| `5ac72ea` refactor(schema): rename CaseHubDefinition.yaml → CaseDefinition.yaml, DispatchRule → Binding | 🔀 MERGE with 2ca7bfb | *(unified — same rename scope)* |
| `161bdfd` refactor(api): rename getRules()/rules() → getBindings()/bindings() on CaseDefinition | ✅ KEEP | `refactor(api): rename getRules()/rules() → getBindings()/bindings() on CaseDefinition` |
| `7a75233` test: comprehensive coverage pass — model builders, StateContext, ValidationResult, GoalExpression | ✅ KEEP | `test: comprehensive coverage pass — model builders, StateContext, ValidationResult, GoalExpression` |

> **Result:** 3 commits.

---

## PR #40 — refactor(api): rename follow-up (2026-04-14) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `65e5f6a` #38 follow-up commit | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #41 — Lambda expression evaluator (2026-04-14) [TREBLEREEL]

> PR #41 was merged as a regular merge (not squash). Treated as single treblereel unit.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `95db452` feat(api): unseal ExpressionEvaluator, add LambdaExpressionEvaluator | TREBLEREEL (unchanged) | *(kept as-is)* |
| `3734f95` Rebase branch, resolve conflicts, and restore build | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #42 — 35 refactor changes (2026-04-14) [TREBLEREEL]

> PR #42 squash commit is the same SHA as the PR #41 rebase commit — same branch context.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `3734f95` Rebase branch, resolve conflicts, and restore build | TREBLEREEL (unchanged) | *(kept as-is — same SHA as PR #41 merge commit)* |

---

## PR #49 — CaseStatus alignment, Milestone/Goal fixes, and casehub-blackboard orchestration layer (2026-04-15) [MDPROCTOR]

**Branch:** `feat/rename-binding-casedefinition` (second merge — blackboard scaffold added after PR #38 landed)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `51e60d9` feat(api): promote CaseStatus to public API, align with CNCF Serverless Workflow | ✅ KEEP | `feat(api): promote CaseStatus to public API, align with CNCF Serverless Workflow — Closes #46 Refs #14` (unified with 1913332 + e81161c + fc53944) |
| `1913332` fix(engine): use merge() not persist() in WorkerRetriesExhaustedEventHandler | 🔀 MERGE with 51e60d9 | *(unified — same PR, small fix)* |
| `e81161c` test(engine): add CaseStatus lifecycle and FAULTED state coverage | 🔽 SQUASH ↑ | *(absorbed — follows the feat)* |
| `fc53944` fix(api): requireNonNull on String condition overloads in Milestone and Goal builders | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, defensive fix)* |
| `086a901` refactor(api): rename StateContext → CaseContext throughout | ✅ KEEP | `refactor(api): rename StateContext → CaseContext throughout` |
| `7b0745e` feat(blackboard): scaffold casehub-blackboard module | ✅ KEEP | `feat(blackboard): scaffold casehub-blackboard module` (unified with 68739ad) |
| `68739ad` feat(api): add PlanElement marker interface; Worker implements it | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, API marker only)* |
| `c30e27e` feat(api): add lambda Predicate overload to Milestone and Goal condition builders | ✅ KEEP | `feat(api): add lambda Predicate overload to Milestone and Goal condition builders` |
| `f8d2623` feat(blackboard): add PlanItemStatus lifecycle enum | ✅ KEEP | `feat(blackboard): add PlanItemStatus lifecycle enum` |
| `42fd911` feat(blackboard): add PlanItem<T> generic lifecycle container | ✅ KEEP | `feat(blackboard): add PlanItem<T> generic lifecycle container` |
| `3624609` feat(blackboard): add Stage type with three-overload condition builder | ✅ KEEP | `feat(blackboard): add Stage type with three-overload condition builder` |
| `aec682c` feat(blackboard): add SubCase + SubCaseCompletionStrategy | ✅ KEEP | `feat(blackboard): add SubCase + SubCaseCompletionStrategy` |
| `6f3d197` feat(blackboard): add CasePlanModel and CasePlanModelRegistry | ✅ KEEP | `feat(blackboard): add CasePlanModel and CasePlanModelRegistry` |
| `e2d284a` feat(blackboard): add PlanningStrategy SPI + DefaultPlanningStrategy | ✅ KEEP | `feat(blackboard): add PlanningStrategy SPI + DefaultPlanningStrategy` (unified with ae785f0) |
| `ae785f0` feat(api): add PlanExecutionContext; enrich LoopControl.select() signature | 🔀 MERGE with e2d284a | *(unified — wires PlanExecutionContext into PlanningStrategy)* |
| `f22b805` feat(blackboard): add PlanningStrategyLoopControl — Stage-aware LoopControl bridge | ✅ KEEP | `feat(blackboard): add PlanningStrategyLoopControl — Stage-aware LoopControl bridge` (unified with 79843d2) |
| `79843d2` feat(engine): add parentPlanItemId to CaseInstance for sub-case wiring | 🔀 MERGE with f22b805 | *(unified — SubCase field wiring)* |
| `d6ad453` test(blackboard): add nested stage integration tests | ✅ KEEP | `test(blackboard): add nested stage integration tests` (unified with eba658e + 0ba9410) |
| `eba658e` fix(engine): correct logger class in CaseContextChangedEventHandler after rebase | 🔽 SQUASH ↑ | *(absorbed — < 5 lines)* |
| `0ba9410` chore: remove beans.xml and migration files from PR #49 | 🔽 SQUASH ↑ | *(absorbed — cleanup artifact)* |

> **Result:** ~12 commits. Large but unavoidable — this PR encompassed CaseStatus migration + entire blackboard scaffold.

---

## PR #52 — feat(resilience): backoff strategies, WorkerExecutionGuard SPI, module scaffold (2026-04-15) [MDPROCTOR — casehubio/engine branch]

> **Note:** PRs #52, #53, #54 were opened directly on casehubio/engine and merged in rapid succession. Content was consolidated into PR #126 which is canonical in `main_20260502`. These PRs are documented for completeness but their individual squash commits are NOT in `main_20260502`.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `b30c1c2` feat(resilience): add backoff strategies, WorkerExecutionGuard SPI, module scaffold | ❌ DROP (superseded by PR #126) | *(content folded into PR #126 consolidated resilience commit)* |

---

## PR #53 — feat(resilience): Dead Letter Queue — store, query, replay, discard (2026-04-15) [MDPROCTOR — casehubio/engine branch]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `959e3a4` feat(resilience): add Dead Letter Queue — store, query, replay, discard | ❌ DROP (superseded by PR #126) | *(content folded into PR #126 consolidated resilience commit)* |

---

## PR #54 — feat(resilience): PoisonPill detection — sliding-window quarantine, scheduler skip (2026-04-15) [MDPROCTOR — casehubio/engine branch]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `2ad38d9` feat(resilience): add PoisonPill detection — sliding-window quarantine, scheduler skip | ❌ DROP (superseded by PR #126) | *(content folded into PR #126 consolidated resilience commit)* |

---

## PR #57 — wrong class declared in logger in CaseStateContextChangedEventHandler (2026-04-15) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `78b1378` wrong class decalred in the logger in CaseStateContextChangedEventHandler (#57) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #58 — fix(engine): improve signal deduplication and worker recovery (2026-04-15) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `66330ef` - introduce CaseContext.applyAndDiff() to compute diffs as part of the write operation (#58) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #59 — bump gh_actions to the latest (2026-04-15) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `1cca5e3` bump gh_actions to the latest (#59) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #60 — Rename binding casedefinition rebased main (2026-04-15) [TREBLEREEL]

> **Note:** Content superseded by PR #62. No distinct commit in `main_20260502`.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| *(superseded)* Rename binding casedefinition rebased main | ❌ DROP (superseded by PR #62) | *(content absorbed into PR #62)* |

---

## PR #61 — feat(engine): enrich WORKER_EXECUTION_COMPLETED with ContextDiffStrategy (2026-04-15) [TREBLEREEL]

> **Note:** ContextDiffStrategy commits exist on upstream branches but do not appear as distinct commits in `main_20260502` — absorbed into subsequent treblereel rebases.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| *(absorbed)* feat(engine): enrich WORKER_EXECUTION_COMPLETED with ContextDiffStrategy | ❌ DROP (absorbed into PR #62 rebase) | *(content folded into PR #62)* |

---

## PR #62 — Rename binding casedefinitio rebased (2026-04-15) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `5b264c1` Rename binding casedefinitio rebased (#62) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #65 — feat(engine): add repository SPI interfaces for persistence decoupling (2026-04-16) [MDPROCTOR]

**Branch:** `feat/persistence/spi`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `1f5ace9` feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces | ✅ KEEP | `feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces` |
| `791ffbe` docs(engine): align findByKey Javadoc null-return wording | 🔽 SQUASH ↑ | *(absorbed — Javadoc fixup follows feature; squash-policy canonical example)* |

> **Result:** 1 commit.

---

## PR #66 — feat(persistence-hibernate): scaffold module + JPA entity classes (2026-04-16) [MDPROCTOR]

**Branch:** `feat/persistence/hibernate-entities`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c7ce1de` chore(build): scaffold casehub-persistence-hibernate module | 🔀 MERGE with 0990374 | *(unified — scaffold alone is noise)* |
| `0990374` feat(persistence-hibernate): add JPA entity classes | ✅ KEEP | `feat(persistence-hibernate): scaffold module + JPA entity classes` (unified with c7ce1de + 58d0f02) |
| `58d0f02` fix(persistence-hibernate): add missing length=50 on CaseInstanceEntity.state column | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, same entity)* |

> **Result:** 1 commit.

---

## PR #71 — feat(persistence-hibernate): JPA repository implementations + 17 integration tests (2026-04-16) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `76eed17` feat(persistence-hibernate): JPA repository implementations + 17 integration tests (#71) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #72 — feat(persistence-memory): in-memory SPI implementations — no Docker, no PostgreSQL (2026-04-19) [MDPROCTOR]

**Branch:** `feat/persistence/memory-clean`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `3fd7d1d` chore(build): scaffold casehub-persistence-memory module | 🔀 MERGE with 5b6c1c1 | *(unified — scaffold alone is noise)* |
| `5b6c1c1` feat(persistence-memory): add InMemoryEventLogRepository with 12 unit tests | ✅ KEEP | `feat(persistence-memory): scaffold module + InMemoryEventLogRepository with 12 unit tests` (unified with 3fd7d1d) |
| `9b374a2` feat(persistence-memory): add InMemoryCaseMetaModelRepository with 7 unit tests | ✅ KEEP | `feat(persistence-memory): add InMemoryCaseMetaModelRepository with 7 unit tests` (unified with e7bc4d2) |
| `e7bc4d2` fix(persistence-memory): guard id assignment in InMemoryCaseMetaModelRepository | 🔀 MERGE with 9b374a2 | *(unified — same class hardened in same sitting)* |
| `25fa1fb` feat(persistence-memory): add InMemoryCaseInstanceRepository with 9 unit tests | ✅ KEEP | `feat(persistence-memory): add InMemoryCaseInstanceRepository with 9 unit tests` (unified with e26cd20) |
| `e26cd20` fix(persistence-memory): throw IllegalStateException in update() for unknown UUID | 🔀 MERGE with 25fa1fb | *(unified — same class hardened)* |
| `441e8c4` fix(tests): correct test isolation for signal dedup and primitive signal tests | ✅ KEEP | `fix(tests): correct test isolation for signal dedup and primitive signal tests` |

> **Result:** 4 commits.

---

## PR #74 — Serialize concurrent signal processing with Vert.x local lock (2026-04-20) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `7c2372a` Description: (#74) | TREBLEREEL (unchanged) | *(kept as-is — low-quality message; content: serialize concurrent signal processing with Vert.x local lock)* |

---

## PR #85 — Feat/persistence/engine decoupling (2026-04-20) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `49dc32f` Feat/persistence/engine decoupling (#85) | TREBLEREEL (unchanged) | *(kept as-is — title-case artifact from branch name)* |

---

## PR #86 — test(engine): add Maven profiles for dual-persistence testing (hibernate/memory) (2026-04-21) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `e880f73` test(engine): add Maven profiles for dual-persistence testing (hibernate/memory) (#86) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #88 — feat(api): make LoopControl.select() return Uni<List<Binding>> [1/3] (2026-04-21) [MDPROCTOR]

**Branch:** `feat/bb-1-async-loop-control`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `8dd0399` feat(api): make LoopControl.select() return Uni<List<Binding>> (casehubio/engine#76) | ✅ KEEP | `feat(api): make LoopControl.select() return Uni<List<Binding>> — Refs #76` (unified with ae923d4 + dfc5185) |
| `ae923d4` fix: update PlanningStrategyLoopControl to return Uni<List<Binding>> | 🔽 SQUASH ↑ | *(absorbed — same-class fixup of the feat)* |
| `dfc5185` docs(claude): document persistence-memory profile and full-reactor compile check | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, no issue ref, < 5 significant lines)* |

> **Result:** 1 commit.

---

## PR #89 — feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records [2/3] (2026-04-21) [MDPROCTOR]

**Branch:** `feat/bb-2-data-model`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `75b33ba` feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records — Refs #76` |
| `3336f28` ci: trigger CI after rebase onto updated PR #88 branch | 🔽 SQUASH ↑ | *(absorbed — CI/build fixup, pure noise)* |

> **Result:** 1 commit.

---

## PR #90 — feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests [3/3] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-3-orchestration`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `40ca1fb` feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests — Refs #76` |
| `9546a4c` docs(claude): add casehub-blackboard module conventions and test patterns | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, no issue ref)* |

> **Result:** 1 commit.

---

## PR #124 — add '/rebase' github action trigger (2026-04-21) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c4a2600` add '/rebase' github action trigger (#124) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #125 — fix(ci): handle fork PRs in rebase workflow (2026-04-21) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f5b63e8` fix(ci): handle fork PRs in rebase workflow (#125) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #91 — fix(blackboard): thread safety — volatile, AtomicReference CAS, atomic addPlanItemIfAbsent [QE-A/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-a-thread-safety`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `d56b6fb` fix(blackboard): make PlanItem.status volatile for cross-thread visibility (casehubio/engine#76) | 🔀 MERGE all three | `fix(blackboard): thread safety — volatile PlanItem.status, AtomicReference Stage.status CAS, atomic addPlanItemIfAbsent — Refs #76` |
| `9e18bef` fix(blackboard): Stage.status → AtomicReference with CAS lifecycle methods (casehubio/engine#76) | 🔀 MERGE into d56b6fb | *(unified — same scope, same sitting)* |
| `3c406d7` fix(blackboard): addPlanItemIfAbsent — atomic check-and-insert via ConcurrentHashMap.compute() (casehubio/engine#76) | 🔀 MERGE into d56b6fb | *(unified — same scope, same sitting)* |

> **Result:** 1 commit. Three thread-safety fixes form one coherent story.

---

## PR #92 — fix(blackboard): lifecycle correctness — achieveMilestone, autocomplete guard, CaseEvictionHandler [QE-B/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-b-lifecycle`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `a5223a9` fix(blackboard): achieveMilestone uses put() — records regardless of trackMilestone order (casehubio/engine#76) | ✅ KEEP | `fix(blackboard): achieveMilestone uses put() — records regardless of trackMilestone order — Refs #76` (unified with 6bd812a) |
| `6bd812a` fix(blackboard): document lazy activeByBinding cleanup; add autocomplete guard tests (casehubio/engine#76) | 🔀 MERGE with a5223a9 | *(unified — same lifecycle concern)* |
| `a68a182` feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state — Refs #76` |

> **Result:** 2 commits.

---

## PR #93 — fix(blackboard): nested stage activation gated on parent ACTIVE state [QE-C/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-c-nested-stages`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c50c13f` fix(blackboard): nested stage activation gated on parent ACTIVE state (casehubio/engine#76) | ✅ KEEP | `fix(blackboard): nested stage activation gated on parent ACTIVE state — Refs #76` (unified with f43f250) |
| `f43f250` test(blackboard): C2 nested stage integration test — child activates after parent (casehubio/engine#76) | 🔀 MERGE with c50c13f | *(unified — fix + test are part of one story)* |

> **Result:** 1 commit.

---

## PR #127 — feature(ci): added '/retest' command (2026-04-22) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `fd2c3a2` feature(ci): added '/retest' command (#127) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #94 — test(blackboard): integration regression coverage — R1, R3, R4, R5 [QE-D/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-d-integration-regression`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `19e36f6` test(blackboard): R1 — two sequential stages activate in order (casehubio/engine#76) | ✅ KEEP | `test(blackboard): R1 — two sequential stages activate in order — Refs #76` |
| `6c7097e` test(blackboard): R3 — exit condition satisfied by worker output end-to-end (casehubio/engine#76) | ✅ KEEP | `test(blackboard): R3 — exit condition satisfied by worker output end-to-end — Refs #76` |
| `757549f` test(blackboard): R4 — two workers with different capabilities both complete (casehubio/engine#76) | ✅ KEEP | `test(blackboard): R4 — two workers with different capabilities both complete — Refs #76` |
| `78c03a8` test(blackboard): R5 — lambda entry condition activates stage end-to-end (casehubio/engine#76) | ✅ KEEP | `test(blackboard): R5 — lambda entry condition activates stage end-to-end — Refs #76` |

> **Result:** 4 commits. Each test scenario describes a distinct behaviour — the policy keeps `test(scope): <scenario>` commits.

---

## PR #95 — fix+test(blackboard): edge cases — dedup contract, getTopPlanItems(0), fault() guards [QE-E/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-e-edge-cases`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `327f61f` test(blackboard): N2 getTopPlanItems(0) returns empty; N3 type mismatch returns empty (casehubio/engine#76) | ✅ KEEP | `test(blackboard): N2/N3 — getTopPlanItems(0) and type mismatch return empty — Refs #76` |
| `bb99aaa` fix(blackboard): DefaultPlanningStrategy deduplicates eligible list; contract test verifies (casehubio/engine#76) | ✅ KEEP | `fix(blackboard): DefaultPlanningStrategy deduplicates eligible list — Refs #76` |
| `f998021` test(blackboard): N7 fault() from terminal states is no-op — 3 confirmatory tests (casehubio/engine#76) | ✅ KEEP | `test(blackboard): N7 — fault() from terminal states is no-op — Refs #76` |

> **Result:** 3 commits.

---

## PR #96 — feat(blackboard): BlackboardPlanConfigurer SPI [QE-F/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-f-plan-configurer`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `26cfd77` feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan config with per-instance semantics (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan config with per-instance semantics — Refs #76` (unified with e5a80ad) |
| `e5a80ad` test(blackboard): integration test for BlackboardPlanConfigurer — stages declared, called once per instance (casehubio/engine#76) | 🔀 MERGE with 26cfd77 | *(unified — feature + its integration test = one logical unit)* |

> **Result:** 1 commit.

---

## PR #97 — feat(blackboard): PlanItem strict lifecycle [QE-G/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-g-strict-lifecycle`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `aeda201` feat(blackboard): PlanItem strict lifecycle — markRunning/markCompleted/markFaulted/markCancelled replace setStatus() (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): PlanItem strict lifecycle — markRunning/markCompleted/markFaulted/markCancelled replace setStatus() — Refs #76` |

> **Result:** 1 commit.

---

## PR #98 — feat(blackboard): SubCase data model — parity with prior implementation [QE-H/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-h-subcase`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f49ad06` feat(blackboard): SubCase data model — parity with prior implementation (casehubio/engine#76) | ✅ KEEP | `feat(blackboard): SubCase data model — parity with prior implementation — Refs #76` |

> **Result:** 1 commit.

---

## PR #99 — feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() [QE-I/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-i-stage-entry-validation` (first use; branch later reused for PR #129)

> The PR #99 stage-entry-validation commit was merged as squash `d498915`. The branch was subsequently reused for PR #129.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `d498915` feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() makes intent explicit | ✅ KEEP | `feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() makes intent explicit — Refs #76` |

> **Result:** 1 commit.

---

## PR #100 — feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002) [QE-J] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-j-binding-gating`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `df452c7` test(blackboard): bump MixedWorkersBlackboardTest timeout 15s → 30s — flaky on Java 17 CI | 🔽 SQUASH ↑ | *(absorbed into 67e45a7 — < 5 lines, CI fixup)* |
| `67e45a7` feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002, casehubio/engine#76) | ✅ KEEP | `feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002) — Refs #76` |

> **Result:** 1 commit.

---

## PR #119 — feat(api): PropagationContext — tracing, budget, and inherited attributes value object (2026-04-22) [MDPROCTOR]

**Branch:** `feat/propagation-context`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f091c12` feat(api): port PropagationContext — tracing, budget, inherited attributes value object (casehubio/engine#117) | ✅ KEEP | `feat(api): PropagationContext — tracing, budget, and inherited attributes value object — Refs #117` |

> **Result:** 1 commit.

---

## PR #120 — test(engine): architectural fitness — deprecated casehub-core types must not be ported (2026-04-22) [MDPROCTOR]

**Branch:** `feat/architectural-exclusions`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `d37053f` test(engine): architectural fitness — deprecated casehub-core types must not be ported (casehubio/engine#118) | ✅ KEEP | `test(engine): architectural fitness — deprecated casehub-core types must not be ported — Refs #118` |

> **Result:** 1 commit.

---

## PR #126 — feat(resilience): casehub-resilience module — ConflictResolver, CaseTimeoutEnforcer, tests (2026-04-22) [MDPROCTOR]

**Branch:** `main` on mdproctor/engine — consolidated resilience work (supercedes PRs #52, #53, #54)

> Granular commits are from the resilience branch chain; PR #126 squash in main is the canonical entry.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `b30c1c2` feat(resilience): add backoff strategies, WorkerExecutionGuard SPI, module scaffold | ✅ KEEP | `feat(resilience): backoff strategies, WorkerExecutionGuard SPI, module scaffold — Refs #51` |
| `959e3a4` feat(resilience): add Dead Letter Queue — store, query, replay, discard | ✅ KEEP | `feat(resilience): Dead Letter Queue — store, query, replay, discard — Refs #51` |
| `2ad38d9` feat(resilience): add PoisonPill detection — sliding-window quarantine, scheduler skip | ✅ KEEP | `feat(resilience): PoisonPill detection — sliding-window quarantine, scheduler skip — Refs #51` |

> **Result:** 3 commits. Each resilience capability is independently valuable.

---

## PR #129 — feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-i-stage-entry-validation` (reused)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `159f027` feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer | ✅ KEEP | `feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer` |

> **Result:** 1 commit.

---

## PR #138 — feat(engine): add ScheduleTrigger support for time-based worker execution (2026-04-22) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `e74f879` feat(engine): add ScheduleTrigger support for time-based worker execution (#138) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #140 — feat(engine): WorkBroker dependencies — CDI wiring + pinned CI checkout (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr2-workbroker-deps`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f3d7e5a` test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest | 🔽 SQUASH ↑ | *(absorbed into 26f9072 — same test hardening pass)* |
| `dd8b353` fix(test): remove cache.clear() from loop in twoSequentialCases — caused Quartz re-runs | 🔽 SQUASH ↑ | *(absorbed into 26f9072 — same test class hardened)* |
| `cb010b7` fix(test): restructure twoSequentialCases to use single pre-loop snapshot | 🔽 SQUASH ↑ | *(absorbed into 26f9072 — same test class hardened)* |
| `26f9072` test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation | ✅ KEEP | `test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation` |
| `2242c16` build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger | 🔽 SQUASH ↑ | *(absorbed into fccb647 — revert chain collapsed)* |
| `07a89a8` Revert "build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger" | ❌ DROP (revert chain) | *(dropped — revert+retry collapsed into final state)* |
| `fccb647` build: remove embedded ledger/work builds; use GitHub Packages; add distributionManagement | ✅ KEEP | `build: use GitHub Packages for quarkus-ledger/work; distributionManagement` (revert chain collapsed) |
| `948e9ca` fix(persistence): drop-and-create schema in tests — no installed base | 🔀 MERGE with 747d922 | *(unified — same fix scope)* |
| `4b02392` fix(resilience): disable Hibernate ORM in resilience tests | 🔀 MERGE with 747d922 | *(unified — same fix scope)* |
| `747d922` fix(ci,blackboard): JDK 21 + disable Hibernate ORM in blackboard tests | ✅ KEEP | `fix(ci,blackboard): JDK 21 + disable Hibernate ORM in blackboard+resilience tests` (unified with 4b02392 + 948e9ca) |
| `5afadc3` fix(ci): dual JDK — JDK 21 for quarkus-work install, JDK 17 for casehub | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup chain)* |
| `ed536fd` fix(ci): install quarkus-work parent POM alongside api and core | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `544f4fd` fix(ci): upgrade to JDK 21 — quarkus-work requires Java 21 compiler | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `5ab2546` fix(ci): remove SHA pin from quarkus-work/ledger checkout — use defaults | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `fb7ad91` fix(ci): update quarkus-work pinned SHA to 098acfe | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `103f9fc` fix(ci): update quarkus-workitems repo reference to quarkus-work (renamed) | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `1c2346d` fix(ci): pin quarkus-workitems checkout to 9707bc5 | 🔽 SQUASH ↑ | *(absorbed into 3f02b18 — CI fixup)* |
| `f321b4e` feat(engine): CDI producers for WorkBroker, LeastLoadedStrategy, NoOpWorkerProvisioner | 🔀 MERGE with 08121ea | *(unified — deps + wiring go together)* |
| `08121ea` feat(engine): add quarkus-work-api and quarkus-work-core dependencies | ✅ KEEP | `feat(engine): add quarkus-work-api/core dependencies + CDI producers for WorkBroker` (unified with f321b4e) |
| `3f02b18` feat(ci): install quarkus-work-core alongside quarkus-work-api | ✅ KEEP | `feat(ci): install quarkus-work — JDK 21, dual-JDK CI, GitHub Packages setup` (all CI fixup chain absorbed) |

> **Result:** 5 commits. CI setup unified; CDI wiring unified; CI test-env fixes unified; build unified (revert chain collapsed); test hardening unified.

---

## PR #141 — feat(engine): choreography — WorkBroker-based worker selection (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr3-choreography`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c2c645c` feat(engine): CasehubWorkloadProvider — Quartz job count per worker | ✅ KEEP | `feat(engine): CasehubWorkloadProvider — Quartz job count per worker` |
| `724f7c3` fix(engine): guard against missing workerId in Quartz JobDataMap | 🔽 SQUASH ↑ | *(absorbed into c7016fa — same-class guard)* |
| `c7016fa` feat(engine): choreography worker selection via WorkBroker — replace random strategy | ✅ KEEP | `feat(engine): choreography worker selection via WorkBroker — replace random strategy` (unified with 7340976) |
| `7340976` fix(engine): null-guard assigneeId() and document SelectionContext null contract | 🔽 SQUASH ↑ | *(absorbed — same-class null-contract fix)* |

> **Result:** 2 commits.

---

## PR #142 — feat(api): orchestration model types — WorkRequest, WorkResult, WAITING state (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr4-orchestration-model`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `55fa326` feat(api): WorkRequest, WorkResult, WorkStatus — orchestration model types | ✅ KEEP | `feat(api): WorkRequest, WorkResult, WorkStatus — orchestration model types` |
| `ccdb637` feat(engine-model): add WORK_SUBMITTED and WORK_COMPLETED event types | ✅ KEEP | `feat(engine-model): add WORK_SUBMITTED and WORK_COMPLETED event types` |
| `1ebce19` feat(engine): add waitingForWorkId to CaseInstance and persistence layer | ✅ KEEP | `feat(engine): add waitingForWorkId to CaseInstance and persistence layer` |

> **Result:** 3 commits. Each adds a distinct layer (API model, event types, persistence field).

---

## PR #143 — feat(engine): PendingWorkRegistry + WorkOrchestrator — durable orchestration (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr5-pending-registry`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `3d7ec41` feat(engine): PendingWorkRegistry — in-memory future correlation for WorkOrchestrator | ✅ KEEP | `feat(engine): PendingWorkRegistry — in-memory future correlation for WorkOrchestrator` (unified with 21d11e5) |
| `21d11e5` fix(engine): eliminate race condition in PendingWorkRegistry.register() | 🔽 SQUASH ↑ | *(absorbed — same class hardened)* |
| `664411f` feat(engine): WorkOrchestrator — durable orchestration entry point | ✅ KEEP | `feat(engine): WorkOrchestrator — durable orchestration entry point` (unified with da6d462 + cad781c + 47e15b7) |
| `da6d462` fix(engine): inject LeastLoadedStrategy concrete type in WorkOrchestrator | 🔽 SQUASH ↑ | *(absorbed — same file fixup)* |
| `cad781c` fix(engine): add missing LeastLoadedStrategy import in WorkOrchestrator | 🔽 SQUASH ↑ | *(absorbed — < 5 lines)* |
| `47e15b7` fix(test): update WorkOrchestratorTest to use LeastLoadedStrategy concrete type | 🔽 SQUASH ↑ | *(absorbed — same test class, same sitting)* |
| `6eccac2` feat(engine): WorkflowExecutionCompletedHandler resumes WAITING cases | ✅ KEEP | `feat(engine): WorkflowExecutionCompletedHandler resumes WAITING cases` |

> **Result:** 3 commits.

---

## PR #144 — feat(engine): WAITING state wiring, startup recovery, E2E tests, DESIGN.md (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr6-waiting-state`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `56c6052` feat(engine): WorkBroker orchestration wiring — WAITING/RUNNING + scheduling | ✅ KEEP | `feat(engine): WorkBroker orchestration wiring — WAITING/RUNNING + scheduling` (unified with 67f2ab0 + 29df93c + acd8b13) |
| `67f2ab0` fix(engine): use LeastLoadedStrategy concrete type in tests; allow out-of-order WAITING | 🔽 SQUASH ↑ | *(absorbed — same-class fixup)* |
| `fad9db8` test(engine): integration and E2E tests for WorkBroker orchestration | ✅ KEEP | `test(engine): integration and E2E tests for WorkBroker orchestration` |
| `2baf08d` feat(engine): PendingWorkRegistry startup recovery from EventLog | ✅ KEEP | `feat(engine): PendingWorkRegistry startup recovery from EventLog` (unified with c48b749) |
| `acd8b13` docs: sync DESIGN.md with WorkBroker hybrid execution model | 🔽 SQUASH ↑ | *(absorbed — docs follow feature)* |
| `29df93c` fix(engine): implement findSubmittedWorkWithoutCompletion in blackboard SPI | 🔽 SQUASH ↑ | *(absorbed — same wiring concern)* |
| `c48b749` fix(resilience): disable Hibernate ORM in resilience tests | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, CI fixup)* |

> **Result:** 3 commits.

---

## PR #151 — feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle (2026-04-23) [MDPROCTOR]

**Branch:** `feat/casehub-ledger-integration`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `a2b7343` feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle | ✅ KEEP | `feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle` (unified with e4ffdc4 + 1f66dd2) |
| `e4ffdc4` fix(casehub-ledger): remove double Merkle update — delegate digest and entry creation | 🔽 SQUASH ↑ | *(absorbed — same module, same sitting)* |
| `1f66dd2` docs: sync DESIGN.md with casehub-ledger integration | 🔽 SQUASH ↑ | *(absorbed — docs follow feature)* |
| `b39d27b` feat(api): model types for worker provisioner SPIs | ✅ KEEP | `feat(api): model types + WorkerProvisioner, WorkerStatusListener, CaseChannelProvider, WorkerContextProvider SPIs` (unified with c5a6e63 + fff74b4 + f61ac20 + 53371c5 + 8c9f676 + 4004f80 + 230f8d4 + 9774a45) |
| `8c9f676` fix(api): code quality corrections for model types | 🔽 SQUASH ↑ | *(absorbed — same-sitting cleanup)* |
| `c5a6e63` feat(api): WorkerProvisioner SPI + NoOp default | 🔀 MERGE with b39d27b | *(unified — all four SPIs are one capability)* |
| `fff74b4` feat(api): WorkerStatusListener SPI + NoOp default | 🔀 MERGE with b39d27b | *(unified)* |
| `f61ac20` feat(api): CaseChannelProvider SPI + NoOp default | 🔀 MERGE with b39d27b | *(unified)* |
| `53371c5` feat(api): WorkerContextProvider SPI + Empty default | 🔀 MERGE with b39d27b | *(unified)* |
| `5f84d30` adr: 0004 — ClaimSlaPolicy as pluggable CDI strategy, Continuation design | ✅ KEEP | `adr: 0004 — ClaimSlaPolicy as pluggable CDI strategy, Continuation design` |
| `dfc206c` test+docs: engine-level unit tests for default SPI impls, document null-worker design | ✅ KEEP | `test: engine-level unit tests for default SPI impls + reactive SPI mirrors` (unified with 4004f80 + 47e6e70) |
| `4004f80` feat(api): reactive SPI mirrors + NoOp/Empty defaults | 🔀 MERGE with dfc206c | *(unified — same SPI capability, reactive mirror)* |
| `47e6e70` test: expand reactive SPI test coverage | 🔀 MERGE with dfc206c | *(unified — test commits for same feature)* |
| `230f8d4` docs: Worker Provisioner SPIs — DESIGN.md, CLAUDE.md, ADR 0004 | 🔽 SQUASH ↑ | *(absorbed into SPI feat — docs follow feature)* |
| `9774a45` fix: final review corrections for Worker Provisioner SPIs | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, review fixup)* |
| `a07b552` test(resilience): extend DLQ E2E test — replay on faulted case is rejected | ✅ KEEP | `test(resilience): DLQ E2E — replay on faulted case is rejected` |

> **Result:** 4 commits. Ledger integration; unified SPI feat; ADR; test+reactive coverage.

---

## PR #154 — fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc (2026-04-23) [MDPROCTOR]

**Branch:** `fix/workcdi-remove-redundant-producers`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c6546d9` fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc | ✅ KEEP | `fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc` |

> **Result:** 1 commit.

---

## PR #155 — fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround (2026-04-24) [MDPROCTOR]

**Branch:** `fix/restore-flyway-validate-in-tests`

> Base commits fccb647 through f3d7e5a were already accounted for in PR #140. Unique additions start from 4535be5.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `fccb647` build: remove embedded ledger/work builds; use GitHub Packages; add distributionManagement | *(already in PR #140)* | *(not re-counted)* |
| `07a89a8` Revert "build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger" | *(already in PR #140)* | *(not re-counted)* |
| `2242c16` build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger | *(already in PR #140)* | *(not re-counted)* |
| `26f9072` test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation | *(already in PR #140)* | *(not re-counted)* |
| `cb010b7` fix(test): restructure twoSequentialCases to use single pre-loop snapshot | *(already in PR #140)* | *(not re-counted)* |
| `dd8b353` fix(test): remove cache.clear() from loop in twoSequentialCases | *(already in PR #140)* | *(not re-counted)* |
| `f3d7e5a` test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest | *(already in PR #140)* | *(not re-counted)* |
| `4535be5` build: add GitHub Packages repository and casehub-parent BOM import | ✅ KEEP | `build: add GitHub Packages repository + casehub-parent BOM; pin quarkus-ledger; align Quarkus version` (unified with 72833a1 + 66bd725 + d1274a6 + bb6f7d3 + a9596e3) |
| `72833a1` fix(build): move casehub-parent BOM import last — preserves casehub-engine Quarkus version | 🔀 MERGE with 4535be5 | *(unified — same build configuration pass)* |
| `66bd725` fix(build): pin quarkus-ledger to 0.2-SNAPSHOT; align all projects on Quarkus 3.32.2 | 🔀 MERGE with 4535be5 | *(unified — same build pass)* |
| `d1274a6` fix(ci): verify on PRs, deploy only on push to main | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, CI policy)* |
| `bb6f7d3` fix(engine): update quarkus-work-api/core to 0.2-SNAPSHOT | 🔽 SQUASH ↑ | *(absorbed — < 5 lines)* |
| `a9596e3` build: manage quarkus-work/ledger versions via parent POM properties | 🔀 MERGE with 4535be5 | *(unified — same build configuration)* |
| `1a89a8f` fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest | ✅ KEEP | `fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest` |
| `05217bd` fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround | ✅ KEEP | `fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround` (unified with 2d35ae4) |
| `2d35ae4` fix(blackboard,resilience): remove quarkus.hibernate-orm.enabled=false workaround | 🔀 MERGE with 05217bd | *(unified — same Flyway/Hibernate revert story)* |

> **Result:** 3 commits. Build alignment; test fix; Flyway/Hibernate restore.

---

## PR #156 — test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest (2026-04-24) [MDPROCTOR]

**Branch:** `fix/harden-flaky-tests`

> Branch contains all commits from PR #155 plus `1a89a8f`. That commit is already captured in PR #155.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `1a89a8f` fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest | *(already in PR #155)* | *(not re-counted — appears once, in PR #155 block)* |

---

## PR #157 — fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages (2026-04-24) [MDPROCTOR]

**Branch:** `fix/deploy-unique-version`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `7c6c877` fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages | ✅ KEEP | `fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages` |

> **Result:** 1 commit.

---

## PR #158 — feat(milestone): add complete milestone SLA tracking implementation (2026-04-24) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `2c02436` feat(milestone): add complete milestone SLA tracking implementation (#158) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #160 — ci: retrigger deploy with uniqueVersion=false fix (2026-04-26) [MDPROCTOR]

**Branch:** `main` on mdproctor/engine (CI retrigger only — no functional change)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `e6a590f` ci: retrigger deploy with uniqueVersion=false fix (#160) | 🔽 SQUASH into PR #157 | *(absorbed into fix/deploy-unique-version — CI mechanical artifact)* |

---

## PR #159 — Scheduler refactoring (2026-04-26) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `28888af` Scheduler refactoring (#159) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #163 — ci: enable deploy on main branch pushes (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-skip` on casehubio/engine

> commit 8f1b555 appears here and is also the content of PR #164 squash.

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `5f62430` ci: enable deploy on main branch pushes | ✅ KEEP | `ci: enable deploy on main branch pushes` (unified with 8f1b555) |
| `8f1b555` fix(ci): correct GitHub Packages deploy URL | 🔽 SQUASH ↑ | *(absorbed — immediate follow-on fix, < 5 lines)* |

> **Result:** 1 commit.

---

## PR #164 — fix(ci): correct GitHub Packages deploy URL (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-url` on casehubio/engine

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `24b6244` fix(ci): correct GitHub Packages deploy URL | 🔽 SQUASH into PR #163 | *(absorbed — immediate CI fixup of PR #163)* |

---

## PR #165 — fix(deploy): deploy root parent POM to GitHub Packages (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-parent-pom` on casehubio/engine

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `4b274a6` fix(deploy): deploy root parent POM to GitHub Packages | ✅ KEEP | `fix(deploy): deploy root parent POM to GitHub Packages` (unified with 0317dde) |
| `0317dde` docs(claude): correct publishing convention — root parent POM must be deployed | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, follows fix)* |

> **Result:** 1 commit.

---

## PR #175 — ci: verify-only on forks, deploy only on upstream main (2026-04-27) [MDPROCTOR]

**Branch:** `ci/fork-deploy-verify-only`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `73d1f26` ci: only deploy on upstream, verify-only on forks | ✅ KEEP | `ci: verify-only on forks, deploy only on upstream main` |

> **Result:** 1 commit.

---

## PR #176 — feat: wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider (2026-04-27) [MDPROCTOR]

**Branch:** `feat/spi-wiring-152`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `5eeb080` feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider into lifecycle — Closes #152 | ✅ KEEP | `feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider — Closes #152` (unified with e2b1ce4 + 86c73a3) |
| `e2b1ce4` chore: remove dead workerContextProvider.buildContext() call | 🔽 SQUASH ↑ | *(absorbed — dead code removal follows feature)* |
| `86c73a3` test(engine): remove WorkerContextProvider wiring tests | 🔽 SQUASH ↑ | *(absorbed — test cleanup follows feature)* |

> **Result:** 1 commit. Matches squash-policy example verbatim.

---

## PR #177 — feat: cancelCase, suspendCase, resumeCase on CaseHub public API (2026-04-27) [MDPROCTOR]

**Branch:** `feat/cancel-suspend-resume-14`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `07846b8` feat(engine): cancelCase, suspendCase, resumeCase on CaseHub public API — Closes #14 | ✅ KEEP | `feat(engine): cancelCase, suspendCase, resumeCase on CaseHub public API — Closes #14` (unified with fcc15a6) |
| `fcc15a6` fix: restore CaseContextChangedEvent import dropped in conflict resolution | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, pure merge artifact)* |

> **Result:** 1 commit.

---

## PR #178 — feat: fire CaseLifecycleEvent for worker execution start and completion (2026-04-27) [MDPROCTOR]

**Branch:** `feat/worker-lifecycle-events-169`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `49aa679` feat(engine): fire CaseLifecycleEvent for worker execution start and completion | ✅ KEEP | `feat(engine): fire CaseLifecycleEvent for worker execution start and completion — Refs #169` |

> **Result:** 1 commit.

---

## PR #179 — fix: null guard CaseContextChangedEventHandler when CaseMetaModel is null (2026-04-27) [MDPROCTOR]

**Branch:** `fix/npe-case-meta-model-172`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `1259329` fix(engine): guard against null CaseMetaModel in CaseContextChangedEventHandler | ✅ KEEP | `fix(engine): guard against null CaseMetaModel in CaseContextChangedEventHandler — Refs #172` |

> **Result:** 1 commit.

---

## PR #180 — feat: add casehub-testing module — in-memory CaseEngine for @QuarkusTest (2026-04-27) [MDPROCTOR]

**Branch:** `feat/casehub-testing-170`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `4985616` feat: add casehub-testing module — in-memory CaseEngine for consumer @QuarkusTest | ✅ KEEP | `feat: add casehub-testing module — in-memory CaseEngine for consumer @QuarkusTest — Refs #170` |

> **Result:** 1 commit.

---

## PR #181 — feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions (2026-04-27) [MDPROCTOR]

**Branch:** `feat/casehub-work-adapter-171`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `5669dc0` feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions | ✅ KEEP | `feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions — Refs #171` |

> **Result:** 1 commit.

---

## PR #182 — docs: CLAUDE.md — work-adapter setup, SPI test pattern, platform coherence (2026-04-27) [MDPROCTOR]

**Branch:** `docs/claude-md-174`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `253f77c` docs(claude): document recording SPI test pattern for wiring verification | 🔀 MERGE all three | `docs: CLAUDE.md — SPI test pattern, work-adapter setup, platform coherence — Refs #174` |
| `6f038c3` docs(claude): document casehub-work-adapter test setup; remove stale blackboard PR list | 🔀 MERGE into 253f77c | *(unified — same CLAUDE.md update pass)* |
| `2876acd` docs(claude): add platform coherence protocol and cross-repo deep-dives | 🔀 MERGE into 253f77c | *(unified — same CLAUDE.md update pass)* |

> **Result:** 1 commit. Three CLAUDE.md updates in the same sitting — single doc pass.

---

## PR #183 — docs: ADR-0006 — worker registration as normative act (2026-04-27) [MDPROCTOR]

**Branch:** `docs/adr-006-173`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `6cddc62` docs(adr): worker registration as normative act (ADR-0006) | ✅ KEEP | `adr: ADR-0006 — worker registration as normative act — Refs #173` |

> **Result:** 1 commit. ADR commits are always standalone per policy.

---

## PR #184 — fix: pass caseId in onWorkerStarted sessionMeta (2026-04-27) [MDPROCTOR]

**Branch:** `fix/session-meta-case-id`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `1f5d652` fix(engine): pass caseId in onWorkerStarted sessionMeta | ✅ KEEP | `fix(engine): pass caseId in onWorkerStarted sessionMeta` |
| `dfe42f1` docs(spec): WorkerProvisioner wiring design — WorkerRegistry, sealed WorkerExecution, normative ledger | 🔽 SQUASH ↑ | *(absorbed — internal spec/planning doc, not history)* |
| `574c5e3` docs(plan): WorkerProvisioner wiring implementation plan — 15 tasks, full TDD pyramid | 🔽 SQUASH ↑ | *(absorbed — internal planning doc, not history)* |
| `170d18a` fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake | ✅ KEEP | `fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake — Refs #188` |

> **Result:** 2 commits. Internal spec/plan docs squash out. Fix and test-fix kept separately.

---

## PR #190 — fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake (2026-04-28) [MDPROCTOR]

**Branch:** `fix/blackboard-mixed-workers-188`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `6dac3f1` fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake | ✅ KEEP | `fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake — Refs #188` (unified with 46dec86) |
| `46dec86` docs(claude): blackboard event-driven test pattern + PR workflow convention | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, follows fix)* |

> **Result:** 1 commit.

---

## PR #196 — Scheduler refactoring part two (2026-04-28) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `172411a` Scheduler refactoring part two (#196) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #192 — feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path (2026-04-29) [MDPROCTOR]

**Branch:** `feat/worker-provisioner-context-wiring-191`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f5a96e6` feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path | ✅ KEEP | `feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path — Refs #191` (unified with 9e824ce + b1f8b23) |
| `9e824ce` fix: align PropagationContext.traceId with active OTel span | 🔀 MERGE with f5a96e6 | *(unified — same wiring concern)* |
| `b1f8b23` docs(design): resolve merge conflicts and document lifecycle flows | 🔽 SQUASH ↑ | *(absorbed — merge artifact, not history)* |
| `608ab72` fix: add MODE=PostgreSQL to H2 test URL in casehub-work-adapter | ✅ KEEP | `fix: H2 test URL + CDI/ledger configuration for blackboard, resilience, work-adapter modules` (unified with 106793f + ae97e2a + 4660961 + bef642c) |
| `106793f` test(engine): fix CDI failure when quarkus-ledger is on engine classpath | 🔀 MERGE with 608ab72 | *(unified — same H2/CDI test env pass)* |
| `ae97e2a` fix(blackboard): configure H2 + NoOpLedgerEntryRepository for quarkus-blackboard tests | 🔀 MERGE with 608ab72 | *(unified — same test env pass)* |
| `4660961` fix(resilience): configure H2 + NoOpLedgerEntryRepository for quarkus-resilience tests | 🔀 MERGE with 608ab72 | *(unified — same test env pass)* |
| `bef642c` fix(work-adapter): add NoOpLedgerEntryRepository for quarkus-ledger CDI | 🔀 MERGE with 608ab72 | *(unified — same test env pass)* |

> **Result:** 2 commits. Wiring feat + test environment fixes.

---

## PR #197 — feat: idempotency window — configurable dedup TTL for WorkerScheduleEventHandler (2026-04-29) [MDPROCTOR]

**Branch:** `feat/idempotency-window-193`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `f55aa47` feat(engine-model): add Instant after cutoff overload to findSchedulingEvents | ✅ KEEP | `feat(engine-model+persistence): add Instant cutoff overload to findSchedulingEvents — Refs #193` (unified with db991ae + 7173a1f + ae9311f + 4f286e9 + bad672f) |
| `db991ae` feat(persistence-memory): implement findSchedulingEvents with Instant cutoff | 🔀 MERGE with f55aa47 | *(unified — same SPI impl pair)* |
| `7173a1f` feat(persistence-hibernate): implement findSchedulingEvents with Instant cutoff | 🔀 MERGE with f55aa47 | *(unified — same SPI impl pair)* |
| `ae9311f` fix(casehub-blackboard): update test-local InMemoryEventLogRepository | 🔽 SQUASH ↑ | *(absorbed — test fixup of SPI change)* |
| `4f286e9` test(persistence-hibernate): rename withNullCutoff → withNullAfter for clarity | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, rename)* |
| `bad672f` style(tests): use ObjectMapper import instead of FQN in repository tests | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, style-only)* |
| `df330cc` feat(engine): wire casehub.idempotency.window config into WorkerScheduleEventHandler | ✅ KEEP | `feat(engine): wire casehub.idempotency.window config into WorkerScheduleEventHandler — Refs #193` (unified with e628421) |
| `e628421` docs: document casehub.idempotency.window in DESIGN.md, mark migration gap #193 resolved | 🔽 SQUASH ↑ | *(absorbed — docs follow feature)* |
| `8180144` feat(blackboard): add waitForCompletion, inputMapping, outputMapping to Binding | ✅ KEEP | `feat(blackboard): add waitForCompletion, inputMapping, outputMapping to Binding` |

> **Result:** 3 commits.

---

## PR #198 — feat: DLQ replay — explicit API and optional auto-replay scheduler (2026-04-29) [MDPROCTOR]

**Branch:** `feat/dlq-replay-194`

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `c7a2853` feat(resilience): add replayAttempts and lastReplayAttemptAt to DeadLetterEntry | 🔀 MERGE all three | `feat(resilience): DLQ replay — replayAttempts tracking, DeadLetterReplayService, DeadLetterAutoReplayJob — Refs #194` (unified with c7a2853 + 2e30905 + fb892a1 + 6a08ae4 + e428959 + b623068) |
| `2e30905` feat(resilience): implement DeadLetterReplayService — explicit DLQ replay | 🔀 MERGE into c7a2853 | *(unified — same DLQ replay capability)* |
| `fb892a1` feat(resilience): implement DeadLetterAutoReplayJob — optional @Scheduled auto-replay | 🔀 MERGE into c7a2853 | *(unified — same DLQ replay capability)* |
| `a07b552` test(resilience): extend DLQ E2E test — replay on faulted case is rejected | ✅ KEEP | `test(resilience): DLQ E2E — replay on faulted case is rejected — Refs #194` |
| `6a08ae4` fix(resilience): O(1) DLQ findById + arrivedAt-based delay in isEligible | 🔽 SQUASH ↑ | *(absorbed into DLQ feat — same-module fix, same sitting)* |
| `e428959` docs: DLQ replay documented in DESIGN.md | 🔽 SQUASH ↑ | *(absorbed — docs follow feature)* |
| `b623068` fix: update CaseDefinitionRegistry import after PR #196 scheduler refactoring | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, import fix)* |

> **Result:** 2 commits. DLQ replay unified; test scenario standalone.

---

## PR #199 — feat: SubCaseBinding — Binding variant that spawns a child CaseInstance (2026-04-29) [MDPROCTOR]

**Branch:** `feat/subcase-binding-195` (first merge)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `badabd2` feat(api): move SubCase to api, add subCase field to Binding (mutually exclusive with capability) | ✅ KEEP | `feat(api): move SubCase to api, add subCase field to Binding — Refs #195` |
| `b49d88f` feat(engine): add SUBCASE_STARTED/COMPLETED event types and SubCaseScheduleEvent | ✅ KEEP | `feat(engine): add SUBCASE_STARTED/COMPLETED event types and SubCaseScheduleEvent — Refs #195` |
| `d3c29d0` refactor(engine): extract CaseResumptionService from WorkflowExecutionCompletedHandler | ✅ KEEP | `refactor(engine): extract CaseResumptionService from WorkflowExecutionCompletedHandler — Refs #195` (unified with 4bb4924 + 4574655) |
| `4bb4924` feat(engine): detect SubCase bindings in CaseContextChangedEventHandler | 🔀 MERGE with d3c29d0 | *(unified — same engine wiring pass)* |
| `4574655` test(engine): update Binding builder test — IllegalStateException replaces NPE | 🔽 SQUASH ↑ | *(absorbed — same-class test update)* |
| `e788950` feat(blackboard): implement SubCaseExecutionHandler — spawns child CaseInstance on SubCaseScheduleEvent | ✅ KEEP | `feat(blackboard): implement SubCaseExecutionHandler + SubCaseCompletionListener — Refs #195` (unified with 3925d60 + 3d0c914) |
| `3925d60` feat(blackboard): implement SubCaseCompletionListener — routes child terminal state to parent | 🔀 MERGE with e788950 | *(unified — part two of handler pair)* |
| `3d0c914` fix(blackboard): SubCaseExecutionHandler — add blocking=true to prevent event-loop deadlock | 🔽 SQUASH ↑ | *(absorbed — same handler hardened)* |
| `c8d31e4` test(blackboard): SubCaseIntegrationTest — parent transitions to WAITING on SubCase spawn | ✅ KEEP | `test(blackboard): SubCaseIntegrationTest — parent transitions to WAITING on SubCase spawn — Refs #195` (unified with 2716f18 + 30c8aa2 + 9efc291 + 0b862ae) |
| `2716f18` docs: SubCaseBinding documented in DESIGN.md | 🔽 SQUASH ↑ | *(absorbed — docs follow test)* |
| `30c8aa2` fix(blackboard): fix double-write and outputMapping against wrong context in SubCase handlers | 🔽 SQUASH ↑ | *(absorbed — same handlers, same sitting)* |
| `9efc291` fix(blackboard): critical fixes from code quality review | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, review fixup)* |
| `0f6b320` ci: trigger CI for PR #199 | ❌ DROP (CI mechanical artifact) | *(dropped)* |
| `0b862ae` fix: update imports after upstream/main rebase (PR #196 renamed engine-model) | 🔽 SQUASH ↑ | *(absorbed — < 5 lines, import-only)* |

> **Result:** 5 commits.

---

## PR #214 — fex(sql): Disable datasource Dev Services reuse in tests (2026-04-29) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `07b2993` fex(sql): Disable datasource Dev Services reuse in tests so Testcontainers Postgres instances are cleaned up after Maven runs. (#214) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #216 — refactor: update imports for quarkus-ledger-api and quarkus-qhorus-api module splits (2026-04-29) [MDPROCTOR]

**Branch:** `feat/subcase-binding-195` (second merge — imports refactor + ADR-0007)

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `fb3e0aa` docs(claude): update engine-model → casehub-engine-common, add quarkus version | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, squash)* |
| `ed3472a` docs(adr): ADR-0007 — four execution models as first-class citizens | ✅ KEEP | `adr: ADR-0007 — four execution models as first-class citizens` (all ADR-0007 iterations unified into final state) |
| `16decd1` docs(adr): ADR-0007 — add lineage constraint across all four execution models | 🔽 SQUASH ↑ | *(absorbed — same ADR, iterative refinement)* |
| `d724649` docs(adr): ADR-0007 — add context propagation design principle | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `c46d880` docs(adr): ADR-0007 — add langchain4j-agentic integration constraint | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `f8125d0` docs(adr): ADR-0007 — expand langchain4j-agentic section with technical details | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `9218953` docs(adr): ADR-0007 — correct context model: three distinct contexts | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `54f5f3b` docs(adr): ADR-0007 — context model is worker-level, with optional case-level | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `83a58d9` docs(adr): ADR-0007 — link to implementation epic #201 | 🔽 SQUASH ↑ | *(absorbed — same ADR)* |
| `3b4c313` refactor: update imports for quarkus-ledger-api module split | ✅ KEEP | `refactor: update imports for quarkus-ledger-api and quarkus-qhorus-api module splits` (unified with f90bfc9) |
| `f90bfc9` fix(deps): explicitly declare quarkus-ledger-api dependency in engine modules | 🔀 MERGE with 3b4c313 | *(unified — same module-split pass)* |

> **Result:** 2 commits. Import refactor; ADR-0007 (final state only — iterative ADR commits collapse into one).

---

## PR #215 — feat(worker): add configurable execution timeout with per-worker override (2026-04-29) [TREBLEREEL]

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `ffa3c8c` feat(worker): add configurable execution timeout with per-worker override (#215) | TREBLEREEL (unchanged) | *(kept as-is)* |

---

## PR #223 — fix(core): post-rename Jandex and CDI discovery fixes (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/post-rename-jandex` on casehubio/engine

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `34658d3` fix(core): post modules renaming fix | 🔀 MERGE all four | `fix(core): post-rename Jandex, CDI discovery, and test config fixes` |
| `48c4d97` fix(tests): restore CDI bean discovery broken by scheduler-quartz extraction | 🔀 MERGE into 34658d3 | *(unified — same root cause: Jandex/CDI after module rename)* |
| `05d326c` fix(tests): add persistence-memory index and selected-alternatives to engine test config | 🔀 MERGE into 34658d3 | *(unified — same root cause)* |
| `d76b4f1` fix(tests): add beans.xml to persistence-hibernate and fix engine test index config | 🔀 MERGE into 34658d3 | *(unified — same root cause)* |

> **Result:** 1 commit.

---

## PR #218 — feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `feat/worker-schedule-command-186` on casehubio/engine

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `eb67bb3` feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling (#186) | ✅ KEEP | `feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling — Refs #186` (unified with e4b2065 + 5136a62 + a2a52a0) |
| `e4b2065` chore: consistency pass — stale names, artifact leak, and terminology cleanup | 🔽 SQUASH ↑ | *(absorbed — chore cleanup follows feature)* |
| `5136a62` chore: replace stale quarkus-ledger name with casehub-ledger in DESIGN.md | 🔽 SQUASH ↑ | *(absorbed — chore cleanup)* |
| `a2a52a0` chore: apply Spotless comment reflow in WorkItemLifecycleAdapter | 🔽 SQUASH ↑ | *(absorbed — formatting fixup)* |

> **Result:** 1 commit.

---

## PR #224 — feat: WorkerContext.channels() populated and WorkerExecutionContext thread-local (Closes #220) (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `feat/case-channel-provider-post-220` on casehubio/engine

| Original commit | Action | Curated result |
|----------------|--------|----------------|
| `73fda63` feat(api): expose WorkerContext.channels() for worker-to-channel posting | 🔀 MERGE both | `feat: WorkerContext.channels() exposed via API and WorkerExecutionContext thread-local — Closes #220` |
| `682cf37` feat: expose WorkerContext.channels() via WorkerExecutionContext thread-local (Closes #220) | 🔀 MERGE into 73fda63 | *(unified — API exposure + thread-local wiring are part 1 and 2)* |
| `93c9009` docs(claude): document IntelliJ MCP tool preference convention | 🔽 SQUASH ↑ | *(absorbed — CLAUDE.md maintenance, squash)* |
| `9c59902` fix(test): implement new LedgerEntryRepository methods in NoOpLedgerEntryRepository | ✅ KEEP | `fix(test): implement new LedgerEntryRepository methods in NoOpLedgerEntryRepository` |

> **Result:** 2 commits.

---

## Reconstruction Notes

### Commits not appearing in main_20260502

| PR | Reason |
|----|--------|
| #52, #53, #54 | Content consolidated into PR #126 from mdproctor/engine:main |
| #60 | Rename work superseded by PR #62 |
| #61 | ContextDiffStrategy content absorbed into treblereel's PR #62 rebase |

### Stacked branch note

PRs #88→#90 and #91→#100 were developed as stacked branches. The commits shown per PR are unique commits added in that PR only. During reconstruction, branches are replayed in merge order; each subsequent branch extends the previous.

### Low-quality squash messages (treblereel)

| SHA | Original message | Suggested display note |
|-----|-----------------|----------------------|
| `7c2372a` | Description: (#74) | Content: serialize concurrent signal processing with Vert.x local lock |
| `49dc32f` | Feat/persistence/engine decoupling (#85) | Title-case artifact from branch name |
