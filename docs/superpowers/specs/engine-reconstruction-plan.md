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

> **Note on stacked PRs:** Several mdproctor PR series (e.g. #140–#144, #91–#100) used a stacked branch strategy where each branch contained all commits from the previous. In these cases, the "before" commits shown per PR are the unique commits added in that PR only (derived from GitHub PR commit lists), not the full branch log.

> **Note on PRs #52, #53, #54:** These were opened on casehubio/engine branches and merged into casehubio/engine main. Their content was subsequently consolidated into PR #126 (from mdproctor/engine:main). Both the original casehubio merges and the mdproctor consolidation are documented. The reconstruction uses the PR #126 consolidated commit as the canonical entry.

> **Note on PR #60 and #61:** Both were merged into casehubio/engine main but their content does not appear in `main_20260502` as distinct commits — PR #60's rename work was superseded by PR #62; PR #61's ContextDiffStrategy content was folded into PR #62 or the upstream rebase. Both are noted as superseded.

---

## PR #1 — initial schema/models (2026-04-08) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 761b8ca | initial schema/models |

---

## PR #24 — initial README (2026-04-09) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| ac51bd6 | initial README |

---

## PR #26 — Add GitHub Action to run Maven tests for pull requests, fixes #25 (2026-04-10) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 9b31438 | Add GitHub Action to run Maven tests for pull requests, fixes #25 |

---

## PR #28 — Improve build configuration and code quality enforcement, fixes #27 (2026-04-13) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 2162300 | Improve build configuration and code quality enforcement, fixes #27 |

---

## PR #32 — feat(engine): add LoopControl SPI for pluggable dispatch rule selection (2026-04-14) [MDPROCTOR]

**Branch:** `feat/loop-control-spi` (origin and upstream)

### Before (original commits)
| SHA | Message |
|-----|---------|
| f488145 | feat(engine): add LoopControl SPI for pluggable dispatch rule selection |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | f488145 | feat(engine): add LoopControl SPI for pluggable dispatch rule selection |

> Single meaningful commit — nothing to squash.

---

## PR #36 — Duplicate signal can trigger the same worker twice despite deduplication, fixes #29 (2026-04-14) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 2e66a8d | Duplicate signal can trigger the same worker twice despite deduplication, fixes #29 |

---

## PR #38 — refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition (2026-04-14) [MDPROCTOR]

**Branch:** `feat/rename-binding-casedefinition` (first merge of this branch — the rename work only)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 2ca7bfb | refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition |
| 441213d | chore: remove .claude/ from tracking, add to .gitignore |
| 5f356b0 | docs(api): add Javadoc clarifying Goal vs Milestone distinction and unified Milestone design |
| 5ac72ea | refactor(schema): rename CaseHubDefinition.yaml → CaseDefinition.yaml, DispatchRule → Binding |
| 161bdfd | refactor(api): rename getRules()/rules() → getBindings()/bindings() on CaseDefinition |
| 7a75233 | test: comprehensive coverage pass — model builders, StateContext, ValidationResult, GoalExpression |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 2ca7bfb | refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition |
| SQUASH into above | 441213d | chore: remove .claude/ from tracking, add to .gitignore — < 5 lines, no issue ref |
| MERGE with above | 5ac72ea | refactor(schema): rename CaseHubDefinition.yaml → CaseDefinition.yaml, DispatchRule → Binding — same rename scope as 2ca7bfb |
| SQUASH into above | 5f356b0 | docs(api): add Javadoc clarifying Goal vs Milestone distinction — follows rename commit |
| KEEP | 161bdfd | refactor(api): rename getRules()/rules() → getBindings()/bindings() on CaseDefinition |
| KEEP | 7a75233 | test: comprehensive coverage pass — model builders, StateContext, ValidationResult, GoalExpression |

> **Result:** 3 commits. 2ca7bfb+441213d+5ac72ea+5f356b0 → unified rename commit. 161bdfd (API method rename). 7a75233 (test coverage).

---

## PR #40 — refactor(api): rename DispatchRule → Binding, CaseHubDefinition → CaseDefinition (follow-up) (2026-04-14) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 65e5f6a | #38 follow-up commit |

---

## PR #41 — Lambda expression evaluator (2026-04-14) [TREBLEREEL]

> PR #41 was merged as a regular merge (not squash). Two commits went in.

### Squash commit (unchanged — treating as single unit)
| SHA | Message |
|-----|---------|
| 95db452 | feat(api): unseal ExpressionEvaluator, add LambdaExpressionEvaluator |
| 3734f95 | Rebase branch, resolve conflicts, and restore build |

---

## PR #42 — 35 refactor changes (2026-04-14) [TREBLEREEL]

> The PR #42 (35_Refactor_changes) squash commit in main_20260502 corresponds to build-rebase work.

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 3734f95 | Rebase branch, resolve conflicts, and restore build |

> **Note:** This commit and the PR #41 merge-commit `3734f95` appear to be the same — PR #42 was a rebase+conflict-resolve on the same branch context. Treated as a single treblereel-owned commit.

---

## PR #49 — CaseStatus alignment, Milestone/Goal fixes, and casehub-blackboard orchestration layer (2026-04-15) [MDPROCTOR]

**Branch:** `feat/rename-binding-casedefinition` (second merge — blackboard scaffold added after PR #38 landed)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 51e60d9 | feat(api): promote CaseStatus to public API, align with CNCF Serverless Workflow |
| 1913332 | fix(engine): use merge() not persist() in WorkerRetriesExhaustedEventHandler |
| e81161c | test(engine): add CaseStatus lifecycle and FAULTED state coverage |
| fc53944 | fix(api): requireNonNull on String condition overloads in Milestone and Goal builders |
| 086a901 | refactor(api): rename StateContext → CaseContext throughout |
| 7b0745e | feat(blackboard): scaffold casehub-blackboard module |
| 68739ad | feat(api): add PlanElement marker interface; Worker implements it |
| c30e27e | feat(api): add lambda Predicate overload to Milestone and Goal condition builders |
| f8d2623 | feat(blackboard): add PlanItemStatus lifecycle enum |
| 42fd911 | feat(blackboard): add PlanItem<T> generic lifecycle container |
| 3624609 | feat(blackboard): add Stage type with three-overload condition builder |
| aec682c | feat(blackboard): add SubCase + SubCaseCompletionStrategy |
| 6f3d197 | feat(blackboard): add CasePlanModel and CasePlanModelRegistry |
| e2d284a | feat(blackboard): add PlanningStrategy SPI + DefaultPlanningStrategy |
| ae785f0 | feat(api): add PlanExecutionContext; enrich LoopControl.select() signature |
| f22b805 | feat(blackboard): add PlanningStrategyLoopControl — Stage-aware LoopControl bridge |
| 79843d2 | feat(engine): add parentPlanItemId to CaseInstance for sub-case wiring |
| d6ad453 | test(blackboard): add nested stage integration tests |
| eba658e | fix(engine): correct logger class in CaseContextChangedEventHandler after rebase |
| 0ba9410 | chore: remove beans.xml and migration files from PR #49 |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 51e60d9 | feat(api): promote CaseStatus to public API, align with CNCF Serverless Workflow — Closes #46 Refs #14 |
| MERGE with above | 1913332 | fix(engine): use merge() not persist() in WorkerRetriesExhaustedEventHandler — same PR, small fix |
| SQUASH into above | e81161c | test(engine): add CaseStatus lifecycle and FAULTED state coverage — follows the feat |
| SQUASH into above | fc53944 | fix(api): requireNonNull on String condition overloads — < 5 lines, defensive fix |
| KEEP | 086a901 | refactor(api): rename StateContext → CaseContext throughout |
| KEEP | 7b0745e | feat(blackboard): scaffold casehub-blackboard module |
| SQUASH into above | 68739ad | feat(api): add PlanElement marker interface; Worker implements it — < 5 lines, API marker only |
| KEEP | c30e27e | feat(api): add lambda Predicate overload to Milestone and Goal condition builders |
| KEEP | f8d2623 | feat(blackboard): add PlanItemStatus lifecycle enum |
| KEEP | 42fd911 | feat(blackboard): add PlanItem<T> generic lifecycle container |
| KEEP | 3624609 | feat(blackboard): add Stage type with three-overload condition builder |
| KEEP | aec682c | feat(blackboard): add SubCase + SubCaseCompletionStrategy |
| KEEP | 6f3d197 | feat(blackboard): add CasePlanModel and CasePlanModelRegistry |
| KEEP | e2d284a | feat(blackboard): add PlanningStrategy SPI + DefaultPlanningStrategy |
| MERGE with above | ae785f0 | feat(api): add PlanExecutionContext; enrich LoopControl.select() signature — wires PlanExecutionContext into PlanningStrategy |
| KEEP | f22b805 | feat(blackboard): add PlanningStrategyLoopControl — Stage-aware LoopControl bridge |
| MERGE with above | 79843d2 | feat(engine): add parentPlanItemId to CaseInstance for sub-case wiring — SubCase field wiring |
| KEEP | d6ad453 | test(blackboard): add nested stage integration tests |
| SQUASH into above | eba658e | fix(engine): correct logger class in CaseContextChangedEventHandler after rebase — < 5 lines |
| SQUASH into above | 0ba9410 | chore: remove beans.xml and migration files from PR #49 — cleanup artifact |

> **Result:** ~12 commits. Large but unavoidable — this PR encompassed CaseStatus migration + entire blackboard scaffold.

---

## PR #52 — feat(resilience): backoff strategies, WorkerExecutionGuard SPI, module scaffold (2026-04-15) [MDPROCTOR — casehubio/engine branch]

> **Note:** PRs #52, #53, #54 were opened directly on casehubio/engine and merged into casehubio/engine main in rapid succession (within 21 minutes). Their content was subsequently consolidated into PR #126 which became the canonical resilience commit in `main_20260502`. These three PRs are documented for completeness but their individual squash commits are NOT present in `main_20260502` — only PR #126's commit is.

### Before (original commits — branch-unique only)
| SHA | Message |
|-----|---------|
| b30c1c2 | feat(resilience): add backoff strategies, WorkerExecutionGuard SPI, module scaffold |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SUPERSEDED by PR #126 | b30c1c2 | Content folded into PR #126 consolidated resilience commit |

---

## PR #53 — feat(resilience): Dead Letter Queue — store, query, replay, discard (2026-04-15) [MDPROCTOR — casehubio/engine branch]

### Before (original commits — branch-unique only)
| SHA | Message |
|-----|---------|
| 959e3a4 | feat(resilience): add Dead Letter Queue — store, query, replay, discard |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SUPERSEDED by PR #126 | 959e3a4 | Content folded into PR #126 consolidated resilience commit |

---

## PR #54 — feat(resilience): PoisonPill detection — sliding-window quarantine, scheduler skip (2026-04-15) [MDPROCTOR — casehubio/engine branch]

### Before (original commits — branch-unique only)
| SHA | Message |
|-----|---------|
| 2ad38d9 | feat(resilience): add PoisonPill detection — sliding-window quarantine, scheduler skip |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SUPERSEDED by PR #126 | 2ad38d9 | Content folded into PR #126 consolidated resilience commit |

---

## PR #57 — wrong class declared in logger in CaseStateContextChangedEventHandler (2026-04-15) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 78b1378 | wrong class decalred in the logger in CaseStateContextChangedEventHandler (#57) |

---

## PR #58 — fix(engine): improve signal deduplication and worker recovery (2026-04-15) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 66330ef | - introduce CaseContext.applyAndDiff() to compute diffs as part of the write operation (#58) |

---

## PR #59 — bump gh_actions to the latest (2026-04-15) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 1cca5e3 | bump gh_actions to the latest (#59) |

---

## PR #60 — Rename binding casedefinition rebased main (2026-04-15) [TREBLEREEL]

> **Note:** PR #60 was merged at 2026-04-15T22:15:05Z but its content does not appear as a distinct commit in `main_20260502`. The rename work was superseded by PR #62 (another rebase of the same rename). Treated as superseded.

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| (superseded) | Rename binding casedefinition rebased main — content absorbed into PR #62 |

---

## PR #61 — feat(engine): enrich WORKER_EXECUTION_COMPLETED with ContextDiffStrategy (2026-04-15) [TREBLEREEL]

> **Note:** PR #61 was merged at 2026-04-15T22:54:08Z. The ContextDiffStrategy commits (ContextDiffStrategy SPI, TopLevelContextDiffStrategy, JsonPatchContextDiffStrategy, enrichment) exist on upstream branches but do not appear as distinct commits in `main_20260502` — they were absorbed into subsequent rebases by treblereel.

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| (absorbed) | feat(engine): enrich WORKER_EXECUTION_COMPLETED with ContextDiffStrategy — content folded into PR #62 rebase |

---

## PR #62 — Rename binding casedefinitio rebased (2026-04-15) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 5b264c1 | Rename binding casedefinitio rebased (#62) |

---

## PR #65 — feat(engine): add repository SPI interfaces for persistence decoupling (2026-04-16) [MDPROCTOR]

**Branch:** `feat/persistence/spi` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 1f5ace9 | feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces |
| 791ffbe | docs(engine): align findByKey Javadoc null-return wording |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 1f5ace9 | feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces |
| SQUASH into above | 791ffbe | docs(engine): align findByKey Javadoc null-return wording — Javadoc fixup follows feature (squash policy example) |

> **Result:** 1 commit. The Javadoc alignment is the canonical squash-policy example.

---

## PR #66 — feat(persistence-hibernate): scaffold module + JPA entity classes (2026-04-16) [MDPROCTOR]

**Branch:** `feat/persistence/hibernate-entities` (origin)

### Before (original commits — unique to this branch beyond PR #65)
| SHA | Message |
|-----|---------|
| c7ce1de | chore(build): scaffold casehub-persistence-hibernate module |
| 0990374 | feat(persistence-hibernate): add JPA entity classes |
| 58d0f02 | fix(persistence-hibernate): add missing length=50 on CaseInstanceEntity.state column |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE with next | c7ce1de | chore(build): scaffold casehub-persistence-hibernate module — scaffold alone is noise |
| KEEP | 0990374 | feat(persistence-hibernate): scaffold module + JPA entity classes — unified: includes scaffold |
| SQUASH into above | 58d0f02 | fix(persistence-hibernate): add missing length=50 on CaseInstanceEntity.state column — < 5 lines, same entity |

> **Result:** 1 commit. Scaffold + entities + length fix unified into single feat commit.

---

## PR #71 — feat(persistence-hibernate): JPA repository implementations + 17 integration tests (2026-04-16) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 76eed17 | feat(persistence-hibernate): JPA repository implementations + 17 integration tests (#71) |

---

## PR #72 — feat(persistence-memory): in-memory SPI implementations — no Docker, no PostgreSQL (2026-04-19) [MDPROCTOR]

**Branch:** `feat/persistence/memory-clean` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 3fd7d1d | chore(build): scaffold casehub-persistence-memory module |
| 5b6c1c1 | feat(persistence-memory): add InMemoryEventLogRepository with 12 unit tests |
| 9b374a2 | feat(persistence-memory): add InMemoryCaseMetaModelRepository with 7 unit tests |
| e7bc4d2 | fix(persistence-memory): guard id assignment in InMemoryCaseMetaModelRepository |
| 25fa1fb | feat(persistence-memory): add InMemoryCaseInstanceRepository with 9 unit tests |
| e26cd20 | fix(persistence-memory): throw IllegalStateException in update() for unknown UUID |
| 441e8c4 | fix(tests): correct test isolation for signal dedup and primitive signal tests |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SQUASH into next | 3fd7d1d | chore(build): scaffold casehub-persistence-memory module — scaffold alone is noise |
| KEEP | 5b6c1c1 | feat(persistence-memory): scaffold module + InMemoryEventLogRepository with 12 unit tests |
| KEEP | 9b374a2 | feat(persistence-memory): add InMemoryCaseMetaModelRepository with 7 unit tests |
| MERGE with above | e7bc4d2 | fix(persistence-memory): guard id assignment in InMemoryCaseMetaModelRepository — same class hardened in same sitting |
| KEEP | 25fa1fb | feat(persistence-memory): add InMemoryCaseInstanceRepository with 9 unit tests |
| MERGE with above | e26cd20 | fix(persistence-memory): throw IllegalStateException in update() for unknown UUID — same class hardened |
| KEEP | 441e8c4 | fix(tests): correct test isolation for signal dedup and primitive signal tests |

> **Result:** 4 commits. Each repository gets one entry; test isolation fix standalone.

---

## PR #74 — Serialize concurrent signal processing with Vert.x local lock (2026-04-20) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 7c2372a | Description: (#74) |

> **Note:** Squash message is low-quality ("Description:"). Content: serialize concurrent signal processing with Vert.x local lock. The author should consider improving the squash message during reconstruction.

---

## PR #85 — Feat/persistence/engine decoupling (2026-04-20) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 49dc32f | Feat/persistence/engine decoupling (#85) |

---

## PR #86 — test(engine): add Maven profiles for dual-persistence testing (hibernate/memory) (2026-04-21) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| e880f73 | test(engine): add Maven profiles for dual-persistence testing (hibernate/memory) (#86) |

---

## PR #88 — feat(api): make LoopControl.select() return Uni<List<Binding>> [1/3] (2026-04-21) [MDPROCTOR]

**Branch:** `feat/bb-1-async-loop-control` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 8dd0399 | feat(api): make LoopControl.select() return Uni<List<Binding>> (casehubio/engine#76) |
| ae923d4 | fix: update PlanningStrategyLoopControl to return Uni<List<Binding>> |
| dfc5185 | docs(claude): document persistence-memory profile and full-reactor compile check |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 8dd0399 | feat(api): make LoopControl.select() return Uni<List<Binding>> — Refs #76 |
| SQUASH into above | ae923d4 | fix: update PlanningStrategyLoopControl to return Uni<List<Binding>> — same-class fixup of the feat |
| SQUASH | dfc5185 | docs(claude): document persistence-memory profile — CLAUDE.md maintenance, squash (no issue ref, < 5 significant lines) |

> **Result:** 1 commit.

---

## PR #89 — feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records [2/3] (2026-04-21) [MDPROCTOR]

**Branch:** `feat/bb-2-data-model` (origin)

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| 75b33ba | feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records (casehubio/engine#76) |
| 3336f28 | ci: trigger CI after rebase onto updated PR #88 branch |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 75b33ba | feat(blackboard): data model — PlanItem, CasePlanModel, Stage, event records — Refs #76 |
| SQUASH | 3336f28 | ci: trigger CI after rebase — CI/build fixup, pure noise |

> **Result:** 1 commit.

---

## PR #90 — feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests [3/3] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-3-orchestration` (origin)

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| 40ca1fb | feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests (casehubio/engine#76) |
| 9546a4c | docs(claude): add casehub-blackboard module conventions and test patterns |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 40ca1fb | feat(blackboard): orchestration layer — PlanningStrategy, handlers, integration tests — Refs #76 |
| SQUASH | 9546a4c | docs(claude): casehub-blackboard conventions — CLAUDE.md maintenance, no issue ref |

> **Result:** 1 commit.

---

## PR #124 — add '/rebase' github action trigger (2026-04-21) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| c4a2600 | add '/rebase' github action trigger (#124) |

---

## PR #125 — fix(ci): handle fork PRs in rebase workflow (2026-04-21) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| f5b63e8 | fix(ci): handle fork PRs in rebase workflow (#125) |

---

## PR #91 — fix(blackboard): thread safety — volatile, AtomicReference CAS, atomic addPlanItemIfAbsent [QE-A/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-a-thread-safety` (origin)

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| d56b6fb | fix(blackboard): make PlanItem.status volatile for cross-thread visibility (casehubio/engine#76) |
| 9e18bef | fix(blackboard): Stage.status → AtomicReference with CAS lifecycle methods (casehubio/engine#76) |
| 3c406d7 | fix(blackboard): addPlanItemIfAbsent — atomic check-and-insert via ConcurrentHashMap.compute() (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE all three | d56b6fb | fix(blackboard): thread safety — volatile PlanItem.status, AtomicReference Stage.status CAS, atomic addPlanItemIfAbsent — Refs #76 |
| MERGE into above | 9e18bef | (same scope, same sitting, three fixes form one thread-safety story) |
| MERGE into above | 3c406d7 | (same scope, same sitting) |

> **Result:** 1 commit. Three thread-safety fixes form one coherent story — the policy merge case.

---

## PR #92 — fix(blackboard): lifecycle correctness — achieveMilestone, autocomplete guard, CaseEvictionHandler [QE-B/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-b-lifecycle` (origin)

### Before (original commits — unique to this PR beyond #91)
| SHA | Message |
|-----|---------|
| a5223a9 | fix(blackboard): achieveMilestone uses put() — records regardless of trackMilestone order (casehubio/engine#76) |
| 6bd812a | fix(blackboard): document lazy activeByBinding cleanup; add autocomplete guard tests (casehubio/engine#76) |
| a68a182 | feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | a5223a9 | fix(blackboard): achieveMilestone uses put() — records regardless of trackMilestone order — Refs #76 |
| MERGE with above | 6bd812a | fix(blackboard): document lazy activeByBinding cleanup; add autocomplete guard tests — same lifecycle concern |
| KEEP | a68a182 | feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state — Refs #76 |

> **Result:** 2 commits.

---

## PR #93 — fix(blackboard): nested stage activation gated on parent ACTIVE state [QE-C/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-c-nested-stages` (origin)

### Before (original commits — unique to this PR beyond #92)
| SHA | Message |
|-----|---------|
| c50c13f | fix(blackboard): nested stage activation gated on parent ACTIVE state (casehubio/engine#76) |
| f43f250 | test(blackboard): C2 nested stage integration test — child activates after parent (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | c50c13f | fix(blackboard): nested stage activation gated on parent ACTIVE state — Refs #76 |
| MERGE with above | f43f250 | test(blackboard): C2 nested stage integration test — child activates after parent — two commits for same fix+test; unified per policy |

> **Result:** 1 commit. Fix and test are part two of one story.

---

## PR #127 — feature(ci): added '/retest' command (2026-04-22) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| fd2c3a2 | feature(ci): added '/retest' command (#127) |

---

## PR #94 — test(blackboard): integration regression coverage — R1, R3, R4, R5 [QE-D/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-d-integration-regression` (origin)

### Before (original commits — unique to this PR beyond #93)
| SHA | Message |
|-----|---------|
| 19e36f6 | test(blackboard): R1 — two sequential stages activate in order (casehubio/engine#76) |
| 6c7097e | test(blackboard): R3 — exit condition satisfied by worker output end-to-end (casehubio/engine#76) |
| 757549f | test(blackboard): R4 — two workers with different capabilities both complete (casehubio/engine#76) |
| 78c03a8 | test(blackboard): R5 — lambda entry condition activates stage end-to-end (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 19e36f6 | test(blackboard): R1 — two sequential stages activate in order — Refs #76 |
| KEEP | 6c7097e | test(blackboard): R3 — exit condition satisfied by worker output end-to-end — Refs #76 |
| KEEP | 757549f | test(blackboard): R4 — two workers with different capabilities both complete — Refs #76 |
| KEEP | 78c03a8 | test(blackboard): R5 — lambda entry condition activates stage end-to-end — Refs #76 |

> **Result:** 4 commits. Each test scenario (R1, R3, R4, R5) describes a distinct behaviour — the policy keeps `test(scope): <scenario>` commits.

---

## PR #95 — fix+test(blackboard): edge cases — dedup contract, getTopPlanItems(0), fault() guards [QE-E/5] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-e-edge-cases` (origin)

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| 327f61f | test(blackboard): N2 getTopPlanItems(0) returns empty; N3 type mismatch returns empty (casehubio/engine#76) |
| bb99aaa | fix(blackboard): DefaultPlanningStrategy deduplicates eligible list; contract test verifies (casehubio/engine#76) |
| f998021 | test(blackboard): N7 fault() from terminal states is no-op — 3 confirmatory tests (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 327f61f | test(blackboard): N2/N3 — getTopPlanItems(0) and type mismatch return empty — Refs #76 |
| KEEP | bb99aaa | fix(blackboard): DefaultPlanningStrategy deduplicates eligible list — Refs #76 |
| KEEP | f998021 | test(blackboard): N7 — fault() from terminal states is no-op — Refs #76 |

> **Result:** 3 commits. Each addresses a distinct edge case contract.

---

## PR #96 — feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan config with per-instance semantics [QE-F/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-f-plan-configurer` (origin)

### Before (original commits — unique to this PR beyond #95)
| SHA | Message |
|-----|---------|
| 26cfd77 | feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan config with per-instance semantics (casehubio/engine#76) |
| e5a80ad | test(blackboard): integration test for BlackboardPlanConfigurer — stages declared, called once per instance (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 26cfd77 | feat(blackboard): BlackboardPlanConfigurer SPI — per-type plan config with per-instance semantics — Refs #76 |
| MERGE with above | e5a80ad | test(blackboard): integration test for BlackboardPlanConfigurer — "add field" + "wire into integration test" are part one/two of same capability |

> **Result:** 1 commit. Feature + its integration test = one logical unit.

---

## PR #97 — feat(blackboard): PlanItem strict lifecycle — markRunning/markCompleted/markFaulted/markCancelled replace setStatus() [QE-G/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-g-strict-lifecycle` (origin)

### Before (original commits — unique to this PR beyond #96)
| SHA | Message |
|-----|---------|
| aeda201 | feat(blackboard): PlanItem strict lifecycle — markRunning/markCompleted/markFaulted/markCancelled replace setStatus() (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | aeda201 | feat(blackboard): PlanItem strict lifecycle — markRunning/markCompleted/markFaulted/markCancelled replace setStatus() — Refs #76 |

> **Result:** 1 commit.

---

## PR #98 — feat(blackboard): SubCase data model — parity with prior implementation [QE-H/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-h-subcase` (origin)

### Before (original commits — unique to this PR beyond #97)
| SHA | Message |
|-----|---------|
| f49ad06 | feat(blackboard): SubCase data model — parity with prior implementation (casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | f49ad06 | feat(blackboard): SubCase data model — parity with prior implementation — Refs #76 |

> **Result:** 1 commit.

---

## PR #99 — feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() makes intent explicit [QE-I/4] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-i-stage-entry-validation` (first use of this branch)

> **Note:** The current branch tip shows only `159f027` (resilience wiring from PR #129). The PR #99 stage-entry-validation commit was merged as squash `d498915` and the branch was subsequently reused for PR #129.

### Before (original commits)
| SHA | Message |
|-----|---------|
| d498915 | feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() makes intent explicit — squash commit in main |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | d498915 | feat(blackboard): Stage.builder() requires entryCondition; Stage.alwaysActivate() makes intent explicit — Refs #76 |

> **Result:** 1 commit.

---

## PR #100 — feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002) [QE-J] (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-j-binding-gating` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| df452c7 | test(blackboard): bump MixedWorkersBlackboardTest timeout 15s → 30s — flaky on Java 17 CI |
| 67e45a7 | feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002, casehubio/engine#76) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SQUASH into next | df452c7 | test(blackboard): bump MixedWorkersBlackboardTest timeout — < 5 lines, CI fixup |
| KEEP | 67e45a7 | feat(blackboard): Stage binding declarations gate loop control selection (ADR-0002) — Refs #76 |

> **Result:** 1 commit.

---

## PR #119 — feat(api): PropagationContext — tracing, budget, and inherited attributes value object (2026-04-22) [MDPROCTOR]

**Branch:** `feat/propagation-context` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| f091c12 | feat(api): port PropagationContext — tracing, budget, inherited attributes value object (casehubio/engine#117) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | f091c12 | feat(api): PropagationContext — tracing, budget, and inherited attributes value object — Refs #117 |

> **Result:** 1 commit.

---

## PR #120 — test(engine): architectural fitness — deprecated casehub-core types must not be ported (2026-04-22) [MDPROCTOR]

**Branch:** `feat/architectural-exclusions` (origin)

### Before (original commits)
| SHA | Message |
|-----|---------|
| d37053f | test(engine): architectural fitness — deprecated casehub-core types must not be ported (casehubio/engine#118) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | d37053f | test(engine): architectural fitness — deprecated casehub-core types must not be ported — Refs #118 |

> **Result:** 1 commit.

---

## PR #126 — feat(resilience): casehub-resilience module — ConflictResolver, CaseTimeoutEnforcer, tests (2026-04-22) [MDPROCTOR]

**Branch:** `main` on mdproctor/engine — consolidated resilience work (supercedes PRs #52, #53, #54)

### Before (original commits — this is the consolidated form; granular commits are on feat/casehub-resilience/* branches)

The PR #126 squash commit in main_20260502 is the entry point. The granular commits existed on the resilience branches and in PRs #52/#53/#54 but those were superseded. For reconstruction, restore granular commits from the resilience branch chain:

| SHA | Message |
|-----|---------|
| b30c1c2 | feat(resilience): add backoff strategies, WorkerExecutionGuard SPI, module scaffold |
| 959e3a4 | feat(resilience): add Dead Letter Queue — store, query, replay, discard |
| 2ad38d9 | feat(resilience): add PoisonPill detection — sliding-window quarantine, scheduler skip |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | b30c1c2 | feat(resilience): backoff strategies, WorkerExecutionGuard SPI, module scaffold — Refs #51 |
| KEEP | 959e3a4 | feat(resilience): Dead Letter Queue — store, query, replay, discard — Refs #51 |
| KEEP | 2ad38d9 | feat(resilience): PoisonPill detection — sliding-window quarantine, scheduler skip — Refs #51 |

> **Result:** 3 commits. Each resilience capability is independently valuable.

---

## PR #129 — feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer (2026-04-22) [MDPROCTOR]

**Branch:** `feat/bb-qa-i-stage-entry-validation` (reused for this PR)

### Before (original commits)
| SHA | Message |
|-----|---------|
| 159f027 | feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 159f027 | feat(resilience): wire PropagationContext budget into CaseTimeoutEnforcer |

> **Result:** 1 commit.

---

## PR #138 — feat(engine): add ScheduleTrigger support for time-based worker execution (2026-04-22) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| e74f879 | feat(engine): add ScheduleTrigger support for time-based worker execution (#138) |

---

## PR #140 — feat(engine): WorkBroker dependencies — CDI wiring + pinned CI checkout (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr2-workbroker-deps`

### Before (original commits — unique to PR #140, from GitHub PR commit list)
| SHA | Message |
|-----|---------|
| f3d7e5a | test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest |
| dd8b353 | fix(test): remove cache.clear() from loop in twoSequentialCases — caused Quartz re-runs |
| cb010b7 | fix(test): restructure twoSequentialCases to use single pre-loop snapshot |
| 26f9072 | test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation |
| 2242c16 | build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger |
| 07a89a8 | Revert "build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger" |
| fccb647 | build: remove embedded ledger/work builds; use GitHub Packages; add distributionManagement |
| 948e9ca | fix(persistence): drop-and-create schema in tests — no installed base |
| 4b02392 | fix(resilience): disable Hibernate ORM in resilience tests |
| 747d922 | fix(ci,blackboard): JDK 21 + disable Hibernate ORM in blackboard tests |
| 5afadc3 | fix(ci): dual JDK — JDK 21 for quarkus-work install, JDK 17 for casehub |
| ed536fd | fix(ci): install quarkus-work parent POM alongside api and core |
| 544f4fd | fix(ci): upgrade to JDK 21 — quarkus-work requires Java 21 compiler |
| 5ab2546 | fix(ci): remove SHA pin from quarkus-work/ledger checkout — use defaults |
| fb7ad91 | fix(ci): update quarkus-work pinned SHA to 098acfe |
| 103f9fc | fix(ci): update quarkus-workitems repo reference to quarkus-work (renamed) |
| 1c2346d | fix(ci): pin quarkus-workitems checkout to 9707bc5 |
| f321b4e | feat(engine): CDI producers for WorkBroker, LeastLoadedStrategy, NoOpWorkerProvisioner |
| 08121ea | feat(engine): add quarkus-work-api and quarkus-work-core dependencies |
| 3f02b18 | feat(ci): install quarkus-work-core alongside quarkus-work-api |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 3f02b18 | feat(ci): install quarkus-work — JDK 21, dual-JDK CI, GitHub Packages setup |
| SQUASH into above (all fix(ci)) | 1c2346d | fix(ci): pin quarkus-workitems checkout to 9707bc5 — CI fixup chain squashed into feat |
| SQUASH into above | 103f9fc | fix(ci): update quarkus-workitems repo to quarkus-work — CI fixup |
| SQUASH into above | fb7ad91 | fix(ci): update quarkus-work pinned SHA — CI fixup |
| SQUASH into above | 5ab2546 | fix(ci): remove SHA pin — CI fixup |
| SQUASH into above | 544f4fd | fix(ci): upgrade to JDK 21 — CI fixup |
| SQUASH into above | ed536fd | fix(ci): install parent POM — CI fixup |
| SQUASH into above | 5afadc3 | fix(ci): dual JDK — CI fixup |
| KEEP | 08121ea | feat(engine): add quarkus-work-api/core dependencies + CDI producers for WorkBroker |
| MERGE with above | f321b4e | (same concern — deps + wiring go together) |
| KEEP | 747d922 | fix(ci,blackboard): JDK 21 + disable Hibernate ORM in blackboard+resilience tests |
| MERGE with above | 4b02392 | (same fix scope) |
| MERGE with above | 948e9ca | (same fix scope — persistence drop-and-create) |
| KEEP | 2242c16 | build: use GitHub Packages for quarkus-ledger/work; distributionManagement |
| SQUASH revert chain into above | 07a89a8 | Revert "build: wire..." — revert+replace collapse to final state |
| KEEP | 26f9072 | test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation |
| SQUASH into above | cb010b7 | fix(test): restructure twoSequentialCases — same test class hardened |
| SQUASH into above | dd8b353 | fix(test): remove cache.clear() from loop — same test class hardened |
| SQUASH into above | f3d7e5a | test(engine): harden WorkerRetryExtendedTest — same test hardening pass |

> **Result:** 5 commits. CI setup unified; CDI wiring unified; CI test-env fixes unified; build unified (revert chain collapsed); test hardening unified.

---

## PR #141 — feat(engine): choreography — WorkBroker-based worker selection (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr3-choreography`

### Before (original commits — unique to PR #141 beyond PR #140)
| SHA | Message |
|-----|---------|
| c2c645c | feat(engine): CasehubWorkloadProvider — Quartz job count per worker |
| 724f7c3 | fix(engine): guard against missing workerId in Quartz JobDataMap |
| c7016fa | feat(engine): choreography worker selection via WorkBroker — replace random strategy |
| 7340976 | fix(engine): null-guard assigneeId() and document SelectionContext null contract |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | c2c645c | feat(engine): CasehubWorkloadProvider — Quartz job count per worker |
| KEEP | c7016fa | feat(engine): choreography worker selection via WorkBroker — replace random strategy |
| SQUASH into above | 724f7c3 | fix(engine): guard against missing workerId in Quartz JobDataMap — same-class guard |
| SQUASH into above | 7340976 | fix(engine): null-guard assigneeId() — same-class null-contract fix |

> **Result:** 2 commits.

---

## PR #142 — feat(api): orchestration model types — WorkRequest, WorkResult, WAITING state (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr4-orchestration-model`

### Before (original commits — unique to PR #142 beyond PR #141)
| SHA | Message |
|-----|---------|
| 55fa326 | feat(api): WorkRequest, WorkResult, WorkStatus — orchestration model types |
| ccdb637 | feat(engine-model): add WORK_SUBMITTED and WORK_COMPLETED event types |
| 1ebce19 | feat(engine): add waitingForWorkId to CaseInstance and persistence layer |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 55fa326 | feat(api): WorkRequest, WorkResult, WorkStatus — orchestration model types |
| KEEP | ccdb637 | feat(engine-model): add WORK_SUBMITTED and WORK_COMPLETED event types |
| KEEP | 1ebce19 | feat(engine): add waitingForWorkId to CaseInstance and persistence layer |

> **Result:** 3 commits. Each adds a distinct layer (API model, event types, persistence field).

---

## PR #143 — feat(engine): PendingWorkRegistry + WorkOrchestrator — durable orchestration (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr5-pending-registry`

### Before (original commits — unique to PR #143 beyond PR #142)
| SHA | Message |
|-----|---------|
| 3d7ec41 | feat(engine): PendingWorkRegistry — in-memory future correlation for WorkOrchestrator |
| 21d11e5 | fix(engine): eliminate race condition in PendingWorkRegistry.register() |
| 664411f | feat(engine): WorkOrchestrator — durable orchestration entry point |
| 6eccac2 | feat(engine): WorkflowExecutionCompletedHandler resumes WAITING cases |
| da6d462 | fix(engine): inject LeastLoadedStrategy concrete type in WorkOrchestrator |
| cad781c | fix(engine): add missing LeastLoadedStrategy import in WorkOrchestrator |
| 47e15b7 | fix(test): update WorkOrchestratorTest to use LeastLoadedStrategy concrete type |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 3d7ec41 | feat(engine): PendingWorkRegistry — in-memory future correlation for WorkOrchestrator |
| SQUASH into above | 21d11e5 | fix(engine): eliminate race condition in PendingWorkRegistry.register() — same class hardened |
| KEEP | 664411f | feat(engine): WorkOrchestrator — durable orchestration entry point |
| SQUASH into above | da6d462 | fix(engine): inject LeastLoadedStrategy concrete type in WorkOrchestrator — same file fixup |
| SQUASH into above | cad781c | fix(engine): add missing LeastLoadedStrategy import — < 5 lines |
| SQUASH into above | 47e15b7 | fix(test): update WorkOrchestratorTest — same test class, same sitting |
| KEEP | 6eccac2 | feat(engine): WorkflowExecutionCompletedHandler resumes WAITING cases |

> **Result:** 3 commits.

---

## PR #144 — feat(engine): WAITING state wiring, startup recovery, E2E tests, DESIGN.md (2026-04-23) [MDPROCTOR]

**Branch:** `feat/pr6-waiting-state`

### Before (original commits — unique to PR #144 beyond PR #143)
| SHA | Message |
|-----|---------|
| 56c6052 | feat(engine): WorkBroker orchestration wiring — WAITING/RUNNING + scheduling |
| 67f2ab0 | fix(engine): use LeastLoadedStrategy concrete type in tests; allow out-of-order WAITING |
| fad9db8 | test(engine): integration and E2E tests for WorkBroker orchestration |
| 2baf08d | feat(engine): PendingWorkRegistry startup recovery from EventLog |
| acd8b13 | docs: sync DESIGN.md with WorkBroker hybrid execution model |
| 29df93c | fix(engine): implement findSubmittedWorkWithoutCompletion in blackboard SPI |
| c48b749 | fix(resilience): disable Hibernate ORM in resilience tests |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 56c6052 | feat(engine): WorkBroker orchestration wiring — WAITING/RUNNING + scheduling |
| SQUASH into above | 67f2ab0 | fix(engine): use LeastLoadedStrategy concrete type in tests — same-class fixup |
| SQUASH into above | 29df93c | fix(engine): implement findSubmittedWorkWithoutCompletion — same wiring concern |
| KEEP | fad9db8 | test(engine): integration and E2E tests for WorkBroker orchestration |
| KEEP | 2baf08d | feat(engine): PendingWorkRegistry startup recovery from EventLog |
| SQUASH into above | c48b749 | fix(resilience): disable Hibernate ORM in resilience tests — < 5 lines, CI fixup |
| SQUASH into feat | acd8b13 | docs: sync DESIGN.md with WorkBroker hybrid execution model — docs follow feature |

> **Result:** 3 commits.

---

## PR #151 — feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle (2026-04-23) [MDPROCTOR]

**Branch:** `feat/casehub-ledger-integration`

### Before (original commits — unique to PR #151 beyond PR #144)
| SHA | Message |
|-----|---------|
| a2b7343 | feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle |
| e4ffdc4 | fix(casehub-ledger): remove double Merkle update — delegate digest and entry creation |
| 1f66dd2 | docs: sync DESIGN.md with casehub-ledger integration |
| b39d27b | feat(api): model types for worker provisioner SPIs |
| 8c9f676 | fix(api): code quality corrections for model types |
| c5a6e63 | feat(api): WorkerProvisioner SPI + NoOp default |
| fff74b4 | feat(api): WorkerStatusListener SPI + NoOp default |
| f61ac20 | feat(api): CaseChannelProvider SPI + NoOp default |
| 53371c5 | feat(api): WorkerContextProvider SPI + Empty default |
| 5f84d30 | adr: 0004 — ClaimSlaPolicy as pluggable CDI strategy, Continuation design |
| dfc206c | test+docs: engine-level unit tests for default SPI impls, document null-worker design |
| 4004f80 | feat(api): reactive SPI mirrors + NoOp/Empty defaults |
| 47e6e70 | test: expand reactive SPI test coverage |
| 230f8d4 | docs: Worker Provisioner SPIs — DESIGN.md, CLAUDE.md, ADR 0004 |
| 9774a45 | fix: final review corrections for Worker Provisioner SPIs |
| a07b552 | test(resilience): extend DLQ E2E test — replay on faulted case is rejected |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | a2b7343 | feat(casehub-ledger): quarkus-ledger integration — immutable audit ledger for case lifecycle |
| SQUASH into above | e4ffdc4 | fix(casehub-ledger): remove double Merkle update — same module, same sitting |
| SQUASH into above | 1f66dd2 | docs: sync DESIGN.md with casehub-ledger — docs follow feature |
| KEEP | b39d27b | feat(api): model types + WorkerProvisioner, WorkerStatusListener, CaseChannelProvider, WorkerContextProvider SPIs |
| MERGE with above | c5a6e63 | (all four SPIs are one capability — model types + four SPIs unified) |
| MERGE with above | fff74b4 | (same) |
| MERGE with above | f61ac20 | (same) |
| MERGE with above | 53371c5 | (same) |
| SQUASH into above | 8c9f676 | fix(api): code quality corrections for model types — same-sitting cleanup |
| KEEP | 5f84d30 | adr: 0004 — ClaimSlaPolicy as pluggable CDI strategy, Continuation design |
| KEEP | dfc206c | test: engine-level unit tests for default SPI impls, document null-worker design |
| MERGE with above | 4004f80 | feat(api): reactive SPI mirrors + NoOp/Empty defaults — same SPI capability, reactive mirror |
| MERGE with above | 47e6e70 | test: expand reactive SPI test coverage — two test commits for same feature |
| SQUASH into SPI feat | 230f8d4 | docs: Worker Provisioner SPIs — DESIGN.md, CLAUDE.md, ADR 0004 — docs follow feature |
| SQUASH into SPI feat | 9774a45 | fix: final review corrections — < 5 lines |

> **Result:** 4 commits. Ledger integration; unified SPI feat; ADR; test+reactive coverage.

---

## PR #154 — fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc (2026-04-23) [MDPROCTOR]

**Branch:** `fix/workcdi-remove-redundant-producers`

### Before (original commits)
| SHA | Message |
|-----|---------|
| c6546d9 | fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | c6546d9 | fix(engine): remove redundant @Produces from WorkCdi; add explanatory Javadoc |

> **Result:** 1 commit.

---

## PR #155 — fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround (2026-04-24) [MDPROCTOR]

**Branch:** `fix/restore-flyway-validate-in-tests`

### Before (original commits — unique to this branch beyond earlier base)
| SHA | Message |
|-----|---------|
| fccb647 | build: remove embedded ledger/work builds; use GitHub Packages; add distributionManagement |
| 07a89a8 | Revert "build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger" |
| 2242c16 | build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger |
| 26f9072 | test(engine): rewrite ChoreographySelectionTest with per-run-ID isolation |
| cb010b7 | fix(test): restructure twoSequentialCases to use single pre-loop snapshot |
| dd8b353 | fix(test): remove cache.clear() from loop in twoSequentialCases |
| f3d7e5a | test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest |
| 4535be5 | build: add GitHub Packages repository and casehub-parent BOM import |
| 72833a1 | fix(build): move casehub-parent BOM import last — preserves casehub-engine Quarkus version |
| 66bd725 | fix(build): pin quarkus-ledger to 0.2-SNAPSHOT; align all projects on Quarkus 3.32.2 |
| d1274a6 | fix(ci): verify on PRs, deploy only on push to main |
| bb6f7d3 | fix(engine): update quarkus-work-api/core to 0.2-SNAPSHOT |
| a9596e3 | build: manage quarkus-work/ledger versions via parent POM properties |
| 1a89a8f | fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest |
| 05217bd | fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround |
| 2d35ae4 | fix(blackboard,resilience): remove quarkus.hibernate-orm.enabled=false workaround |

> **Note:** The base commits (fccb647 through f3d7e5a) were already accounted for in PR #140. The unique additions for PR #155 are from 4535be5 onward.

### After (proposed — unique commits only)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 4535be5 | build: add GitHub Packages repository and casehub-parent BOM import |
| MERGE with above | 72833a1 | fix(build): move casehub-parent BOM import last — same build configuration pass |
| MERGE with above | 66bd725 | fix(build): pin quarkus-ledger; align Quarkus version — same build pass |
| SQUASH into above | d1274a6 | fix(ci): verify on PRs — < 5 lines, CI policy |
| SQUASH into above | bb6f7d3 | fix(engine): update quarkus-work-api/core version — < 5 lines |
| SQUASH into above | a9596e3 | build: manage versions via parent POM — same build configuration |
| KEEP | 1a89a8f | fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest |
| KEEP | 05217bd | fix(persistence): restore Flyway+validate in tests — drop-and-create was a workaround |
| MERGE with above | 2d35ae4 | fix(blackboard,resilience): remove quarkus.hibernate-orm.enabled=false workaround — same Flyway/Hibernate revert story |

> **Result:** 3 commits. Build alignment; test fix; Flyway/Hibernate restore.

---

## PR #156 — test(engine): harden ChoreographySelectionTest and WorkerRetryExtendedTest (2026-04-24) [MDPROCTOR]

**Branch:** `fix/harden-flaky-tests`

> **Note:** Branch contains all commits from PR #155 plus one new commit (1a89a8f). That commit is already captured in PR #155 above. PR #156 squash in main is `6fd9e48`.

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| 1a89a8f | fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP (already counted in #155) | 1a89a8f | fix(test): prevent binding re-fire after worker output in ChoreographySelectionTest |

> **Note for executor:** This commit appears in both PR #155 and #156 branches. During reconstruction it should appear once — as part of the PR #155 commit block. PR #156 squash can be omitted since the content is already in #155's reconstruction.

---

## PR #157 — fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages (2026-04-24) [MDPROCTOR]

**Branch:** `fix/deploy-unique-version`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 7c6c877 | fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 7c6c877 | fix(deploy): disable timestamped SNAPSHOT versions for GitHub Packages |

> **Result:** 1 commit.

---

## PR #158 — feat(milestone): add complete milestone SLA tracking implementation (2026-04-24) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 2c02436 | feat(milestone): add complete milestone SLA tracking implementation (#158) |

---

## PR #160 — ci: retrigger deploy with uniqueVersion=false fix (2026-04-26) [MDPROCTOR]

**Branch:** `main` on mdproctor/engine (small CI retrigger)

> **Note:** This is a CI retrigger only — the squash commit `e6a590f` in main_20260502 represents no functional change. Per policy, CI/build retriggers squash into the feature they were unblocking.

### Before (original commits)
| SHA | Message |
|-----|---------|
| e6a590f | ci: retrigger deploy with uniqueVersion=false fix (#160) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SQUASH into PR #157 | e6a590f | ci: retrigger deploy — CI mechanical artifact, squash into fix/deploy-unique-version |

---

## PR #159 — Scheduler refactoring (2026-04-26) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 28888af | Scheduler refactoring (#159) |

---

## PR #163 — ci: enable deploy on main branch pushes (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-skip` on casehubio/engine

### Before (original commits)
| SHA | Message |
|-----|---------|
| 5f62430 | ci: enable deploy on main branch pushes |
| 8f1b555 | fix(ci): correct GitHub Packages deploy URL |

> **Note:** commit 8f1b555 appears here but is also the content of PR #164 squash.

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 5f62430 | ci: enable deploy on main branch pushes |
| SQUASH into above | 8f1b555 | fix(ci): correct GitHub Packages deploy URL — immediate follow-on fix, < 5 lines |

> **Result:** 1 commit. Deploy enable + immediate URL fix unified.

---

## PR #164 — fix(ci): correct GitHub Packages deploy URL (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-url` on casehubio/engine

### Before (original commits)
| SHA | Message |
|-----|---------|
| 24b6244 | fix(ci): correct GitHub Packages deploy URL |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| SQUASH into PR #163 | 24b6244 | fix(ci): correct deploy URL — immediate CI fixup of PR #163, squash per revert-chain policy |

---

## PR #165 — fix(deploy): deploy root parent POM to GitHub Packages (2026-04-26) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/deploy-parent-pom` on casehubio/engine

### Before (original commits)
| SHA | Message |
|-----|---------|
| 4b274a6 | fix(deploy): deploy root parent POM to GitHub Packages |
| 0317dde | docs(claude): correct publishing convention — root parent POM must be deployed |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 4b274a6 | fix(deploy): deploy root parent POM to GitHub Packages |
| SQUASH into above | 0317dde | docs(claude): correct publishing convention — CLAUDE.md maintenance, follows fix |

> **Result:** 1 commit.

---

## PR #175 — ci: verify-only on forks, deploy only on upstream main (2026-04-27) [MDPROCTOR]

**Branch:** `ci/fork-deploy-verify-only`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 73d1f26 | ci: only deploy on upstream, verify-only on forks |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 73d1f26 | ci: verify-only on forks, deploy only on upstream main |

> **Result:** 1 commit.

---

## PR #176 — feat: wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider (2026-04-27) [MDPROCTOR]

**Branch:** `feat/spi-wiring-152`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 5eeb080 | feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider into lifecycle — Closes #152 |
| e2b1ce4 | chore: remove dead workerContextProvider.buildContext() call |
| 86c73a3 | test(engine): remove WorkerContextProvider wiring tests |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 5eeb080 | feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider — Closes #152 |
| SQUASH into above | e2b1ce4 | chore: remove dead workerContextProvider.buildContext() call — squash policy: dead code removal follows feature |
| SQUASH into above | 86c73a3 | test(engine): remove WorkerContextProvider wiring tests — squash policy: test cleanup follows feature |

> **Result:** 1 commit. Matches the squash-policy example verbatim.

---

## PR #177 — feat: cancelCase, suspendCase, resumeCase on CaseHub public API (2026-04-27) [MDPROCTOR]

**Branch:** `feat/cancel-suspend-resume-14`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 07846b8 | feat(engine): cancelCase, suspendCase, resumeCase on CaseHub public API — Closes #14 |
| fcc15a6 | fix: restore CaseContextChangedEvent import dropped in conflict resolution |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 07846b8 | feat(engine): cancelCase, suspendCase, resumeCase on CaseHub public API — Closes #14 |
| SQUASH into above | fcc15a6 | fix: restore import dropped in conflict resolution — < 5 lines, pure artifact |

> **Result:** 1 commit.

---

## PR #178 — feat: fire CaseLifecycleEvent for worker execution start and completion (2026-04-27) [MDPROCTOR]

**Branch:** `feat/worker-lifecycle-events-169`

### Before (original commits — unique beyond PR #176)
| SHA | Message |
|-----|---------|
| 49aa679 | feat(engine): fire CaseLifecycleEvent for worker execution start and completion |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 49aa679 | feat(engine): fire CaseLifecycleEvent for worker execution start and completion — Refs #169 |

> **Result:** 1 commit.

---

## PR #179 — fix: null guard CaseContextChangedEventHandler when CaseMetaModel is null (2026-04-27) [MDPROCTOR]

**Branch:** `fix/npe-case-meta-model-172`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 1259329 | fix(engine): guard against null CaseMetaModel in CaseContextChangedEventHandler |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 1259329 | fix(engine): guard against null CaseMetaModel in CaseContextChangedEventHandler — Refs #172 |

> **Result:** 1 commit.

---

## PR #180 — feat: add casehub-testing module — in-memory CaseEngine for @QuarkusTest (2026-04-27) [MDPROCTOR]

**Branch:** `feat/casehub-testing-170`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 4985616 | feat: add casehub-testing module — in-memory CaseEngine for consumer @QuarkusTest |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 4985616 | feat: add casehub-testing module — in-memory CaseEngine for consumer @QuarkusTest — Refs #170 |

> **Result:** 1 commit.

---

## PR #181 — feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions (2026-04-27) [MDPROCTOR]

**Branch:** `feat/casehub-work-adapter-171`

### Before (original commits — unique beyond PR #180)
| SHA | Message |
|-----|---------|
| 5669dc0 | feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 5669dc0 | feat: add casehub-work-adapter — bridge WorkItemLifecycleEvent to PlanItem transitions — Refs #171 |

> **Result:** 1 commit.

---

## PR #182 — docs: CLAUDE.md — work-adapter setup, SPI test pattern, platform coherence (2026-04-27) [MDPROCTOR]

**Branch:** `docs/claude-md-174`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 253f77c | docs(claude): document recording SPI test pattern for wiring verification |
| 6f038c3 | docs(claude): document casehub-work-adapter test setup; remove stale blackboard PR list |
| 2876acd | docs(claude): add platform coherence protocol and cross-repo deep-dives |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE all three | 253f77c | docs: CLAUDE.md — SPI test pattern, work-adapter setup, platform coherence — Refs #174 |
| MERGE into above | 6f038c3 | (same CLAUDE.md update pass) |
| MERGE into above | 2876acd | (same CLAUDE.md update pass) |

> **Result:** 1 commit. Three CLAUDE.md updates in the same sitting — near-identical scope, single doc pass.

---

## PR #183 — docs: ADR-0006 — worker registration as normative act (2026-04-27) [MDPROCTOR]

**Branch:** `docs/adr-006-173`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 6cddc62 | docs(adr): worker registration as normative act (ADR-0006) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 6cddc62 | adr: ADR-0006 — worker registration as normative act — Refs #173 |

> **Result:** 1 commit. ADR commits are always standalone per policy.

---

## PR #184 — fix: pass caseId in onWorkerStarted sessionMeta (2026-04-27) [MDPROCTOR]

**Branch:** `fix/session-meta-case-id`

### Before (original commits — unique beyond earlier PRs in this branch)
| SHA | Message |
|-----|---------|
| 1f5d652 | fix(engine): pass caseId in onWorkerStarted sessionMeta |
| dfe42f1 | docs(spec): WorkerProvisioner wiring design — WorkerRegistry, sealed WorkerExecution, normative ledger |
| 574c5e3 | docs(plan): WorkerProvisioner wiring implementation plan — 15 tasks, full TDD pyramid |
| 170d18a | fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 1f5d652 | fix(engine): pass caseId in onWorkerStarted sessionMeta |
| SQUASH | dfe42f1 | docs(spec): WorkerProvisioner wiring design — internal spec/planning doc, not history |
| SQUASH | 574c5e3 | docs(plan): WorkerProvisioner wiring implementation plan — internal planning doc, not history |
| KEEP | 170d18a | fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake — Refs #188 |

> **Result:** 2 commits. Internal spec/plan docs squash out. Fix and test-fix kept separately.

---

## PR #190 — fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake (2026-04-28) [MDPROCTOR]

**Branch:** `fix/blackboard-mixed-workers-188`

### Before (original commits)
| SHA | Message |
|-----|---------|
| 6dac3f1 | fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake |
| 46dec86 | docs(claude): blackboard event-driven test pattern + PR workflow convention |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 6dac3f1 | fix(blackboard): event-driven MixedWorkersBlackboardTest — eliminates timing flake — Refs #188 |
| SQUASH into above | 46dec86 | docs(claude): blackboard test pattern — CLAUDE.md maintenance, follows fix |

> **Result:** 1 commit.

---

## PR #196 — Scheduler refactoring part two (2026-04-28) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 172411a | Scheduler refactoring part two (#196) |

---

## PR #192 — feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path (2026-04-29) [MDPROCTOR]

**Branch:** `feat/worker-provisioner-context-wiring-191`

### Before (original commits — unique to this PR)
| SHA | Message |
|-----|---------|
| f5a96e6 | feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path |
| 9e824ce | fix: align PropagationContext.traceId with active OTel span |
| b1f8b23 | docs(design): resolve merge conflicts and document lifecycle flows |
| 608ab72 | fix: add MODE=PostgreSQL to H2 test URL in casehub-work-adapter |
| 106793f | test(engine): fix CDI failure when quarkus-ledger is on engine classpath |
| ae97e2a | fix(blackboard): configure H2 + NoOpLedgerEntryRepository for quarkus-blackboard tests |
| 4660961 | fix(resilience): configure H2 + NoOpLedgerEntryRepository for quarkus-resilience tests |
| bef642c | fix(work-adapter): add NoOpLedgerEntryRepository for quarkus-ledger CDI |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | f5a96e6 | feat(engine): wire WorkerContextProvider and WorkerProvisioner into execution path — Refs #191 |
| MERGE with above | 9e824ce | fix: align PropagationContext.traceId with active OTel span — same wiring concern |
| SQUASH into above | b1f8b23 | docs(design): resolve merge conflicts — merge artifact, not history |
| KEEP | 608ab72 | fix: add MODE=PostgreSQL to H2 test URL in casehub-work-adapter |
| MERGE with above | 106793f | test(engine): fix CDI failure when quarkus-ledger is on engine classpath — same H2/CDI test env pass |
| MERGE with above | ae97e2a | fix(blackboard): configure H2 + NoOpLedgerEntryRepository — same test env pass |
| MERGE with above | 4660961 | fix(resilience): configure H2 + NoOpLedgerEntryRepository — same test env pass |
| MERGE with above | bef642c | fix(work-adapter): add NoOpLedgerEntryRepository — same test env pass |

> **Result:** 2 commits. Wiring feat + test environment fixes.

---

## PR #197 — feat: idempotency window — configurable dedup TTL for WorkerScheduleEventHandler (2026-04-29) [MDPROCTOR]

**Branch:** `feat/idempotency-window-193`

### Before (original commits)
| SHA | Message |
|-----|---------|
| f55aa47 | feat(engine-model): add Instant after cutoff overload to findSchedulingEvents |
| db991ae | feat(persistence-memory): implement findSchedulingEvents with Instant cutoff |
| 7173a1f | feat(persistence-hibernate): implement findSchedulingEvents with Instant cutoff |
| ae9311f | fix(casehub-blackboard): update test-local InMemoryEventLogRepository |
| 4f286e9 | test(persistence-hibernate): rename withNullCutoff → withNullAfter for clarity |
| bad672f | style(tests): use ObjectMapper import instead of FQN in repository tests |
| df330cc | feat(engine): wire casehub.idempotency.window config into WorkerScheduleEventHandler |
| e628421 | docs: document casehub.idempotency.window in DESIGN.md, mark migration gap #193 resolved |
| 8180144 | feat(blackboard): add waitForCompletion, inputMapping, outputMapping to Binding |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | f55aa47 | feat(engine-model): add Instant cutoff overload to findSchedulingEvents — Refs #193 |
| MERGE with above | db991ae | feat(persistence-memory): implement findSchedulingEvents with Instant cutoff — same SPI impl pair |
| MERGE with above | 7173a1f | feat(persistence-hibernate): implement findSchedulingEvents with Instant cutoff — same SPI impl pair |
| SQUASH into above | ae9311f | fix(casehub-blackboard): update test-local InMemoryEventLogRepository — test fixup of SPI change |
| SQUASH into above | 4f286e9 | test: rename withNullCutoff → withNullAfter — < 5 lines, rename |
| SQUASH into above | bad672f | style(tests): use ObjectMapper import — < 5 lines, style-only |
| KEEP | df330cc | feat(engine): wire casehub.idempotency.window config into WorkerScheduleEventHandler — Refs #193 |
| SQUASH into above | e628421 | docs: document casehub.idempotency.window in DESIGN.md — docs follow feature |
| KEEP | 8180144 | feat(blackboard): add waitForCompletion, inputMapping, outputMapping to Binding |

> **Result:** 3 commits.

---

## PR #198 — feat: DLQ replay — explicit API and optional auto-replay scheduler (2026-04-29) [MDPROCTOR]

**Branch:** `feat/dlq-replay-194`

### Before (original commits)
| SHA | Message |
|-----|---------|
| c7a2853 | feat(resilience): add replayAttempts and lastReplayAttemptAt to DeadLetterEntry |
| 2e30905 | feat(resilience): implement DeadLetterReplayService — explicit DLQ replay |
| fb892a1 | feat(resilience): implement DeadLetterAutoReplayJob — optional @Scheduled auto-replay |
| a07b552 | test(resilience): extend DLQ E2E test — replay on faulted case is rejected |
| 6a08ae4 | fix(resilience): O(1) DLQ findById + arrivedAt-based delay in isEligible |
| e428959 | docs: DLQ replay documented in DESIGN.md |
| b623068 | fix: update CaseDefinitionRegistry import after PR #196 scheduler refactoring |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE both | c7a2853 | feat(resilience): DLQ replay — replayAttempts tracking, DeadLetterReplayService, DeadLetterAutoReplayJob — Refs #194 |
| MERGE into above | 2e30905 | (same DLQ replay capability) |
| MERGE into above | fb892a1 | (same DLQ replay capability) |
| KEEP | a07b552 | test(resilience): DLQ E2E — replay on faulted case is rejected — Refs #194 |
| SQUASH into first | 6a08ae4 | fix(resilience): O(1) DLQ findById + arrivedAt-based delay — same-module fix, same sitting |
| SQUASH into first | e428959 | docs: DLQ replay in DESIGN.md — docs follow feature |
| SQUASH into first | b623068 | fix: update CaseDefinitionRegistry import after PR #196 — < 5 lines, import fix |

> **Result:** 2 commits. DLQ replay unified; test scenario standalone.

---

## PR #199 — feat: SubCaseBinding — Binding variant that spawns a child CaseInstance (2026-04-29) [MDPROCTOR]

**Branch:** `feat/subcase-binding-195` (first merge)

### Before (original commits)
| SHA | Message |
|-----|---------|
| badabd2 | feat(api): move SubCase to api, add subCase field to Binding (mutually exclusive with capability) |
| b49d88f | feat(engine): add SUBCASE_STARTED/COMPLETED event types and SubCaseScheduleEvent |
| d3c29d0 | refactor(engine): extract CaseResumptionService from WorkflowExecutionCompletedHandler |
| 4bb4924 | feat(engine): detect SubCase bindings in CaseContextChangedEventHandler |
| 4574655 | test(engine): update Binding builder test — IllegalStateException replaces NPE |
| e788950 | feat(blackboard): implement SubCaseExecutionHandler — spawns child CaseInstance on SubCaseScheduleEvent |
| 3925d60 | feat(blackboard): implement SubCaseCompletionListener — routes child terminal state to parent |
| 3d0c914 | fix(blackboard): SubCaseExecutionHandler — add blocking=true to prevent event-loop deadlock |
| c8d31e4 | test(blackboard): SubCaseIntegrationTest — parent transitions to WAITING on SubCase spawn |
| 2716f18 | docs: SubCaseBinding documented in DESIGN.md |
| 30c8aa2 | fix(blackboard): fix double-write and outputMapping against wrong context in SubCase handlers |
| 9efc291 | fix(blackboard): critical fixes from code quality review |
| 0f6b320 | ci: trigger CI for PR #199 |
| 0b862ae | fix: update imports after upstream/main rebase (PR #196 renamed engine-model) |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | badabd2 | feat(api): move SubCase to api, add subCase field to Binding — Refs #195 |
| KEEP | b49d88f | feat(engine): add SUBCASE_STARTED/COMPLETED event types and SubCaseScheduleEvent — Refs #195 |
| KEEP | d3c29d0 | refactor(engine): extract CaseResumptionService from WorkflowExecutionCompletedHandler — Refs #195 |
| MERGE with above | 4bb4924 | feat(engine): detect SubCase bindings in CaseContextChangedEventHandler — same engine wiring pass |
| SQUASH into above | 4574655 | test(engine): update Binding builder test — IllegalStateException replaces NPE — same-class test update |
| KEEP | e788950 | feat(blackboard): implement SubCaseExecutionHandler — spawns child CaseInstance on SubCaseScheduleEvent — Refs #195 |
| MERGE with above | 3925d60 | feat(blackboard): implement SubCaseCompletionListener — routes child terminal state to parent — part two of handler pair |
| SQUASH into above | 3d0c914 | fix(blackboard): SubCaseExecutionHandler — add blocking=true to prevent event-loop deadlock — same handler hardened |
| KEEP | c8d31e4 | test(blackboard): SubCaseIntegrationTest — parent transitions to WAITING on SubCase spawn — Refs #195 |
| SQUASH into above | 2716f18 | docs: SubCaseBinding documented in DESIGN.md — docs follow test |
| SQUASH into above | 30c8aa2 | fix(blackboard): fix double-write and outputMapping — same handlers, same sitting |
| SQUASH into above | 9efc291 | fix(blackboard): critical fixes from code quality review — < 5 lines, review fixup |
| SQUASH | 0f6b320 | ci: trigger CI for PR #199 — CI mechanical artifact |
| SQUASH into closest | 0b862ae | fix: update imports after PR #196 rebase — < 5 lines, import-only |

> **Result:** 5 commits.

---

## PR #214 — fex(sql): Disable datasource Dev Services reuse in tests (2026-04-29) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| 07b2993 | fex(sql): Disable datasource Dev Services reuse in tests so Testcontainers Postgres instances are cleaned up after Maven runs. (#214) |

---

## PR #216 — refactor: update imports for quarkus-ledger-api and quarkus-qhorus-api module splits (2026-04-29) [MDPROCTOR]

**Branch:** `feat/subcase-binding-195` (second merge — imports refactor + ADR-0007)

### Before (original commits — unique to this PR beyond PR #199)
| SHA | Message |
|-----|---------|
| fb3e0aa | docs(claude): update engine-model → casehub-engine-common, add quarkus version |
| ed3472a | docs(adr): ADR-0007 — four execution models as first-class citizens |
| 16decd1 | docs(adr): ADR-0007 — add lineage constraint across all four execution models |
| d724649 | docs(adr): ADR-0007 — add context propagation design principle |
| c46d880 | docs(adr): ADR-0007 — add langchain4j-agentic integration constraint |
| f8125d0 | docs(adr): ADR-0007 — expand langchain4j-agentic section with technical details |
| 9218953 | docs(adr): ADR-0007 — correct context model: three distinct contexts |
| 54f5f3b | docs(adr): ADR-0007 — context model is worker-level, with optional case-level |
| 83a58d9 | docs(adr): ADR-0007 — link to implementation epic #201 |
| 3b4c313 | refactor: update imports for quarkus-ledger-api module split |
| f90bfc9 | fix(deps): explicitly declare quarkus-ledger-api dependency in engine modules |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | 3b4c313 | refactor: update imports for quarkus-ledger-api and quarkus-qhorus-api module splits |
| MERGE with above | f90bfc9 | fix(deps): explicitly declare quarkus-ledger-api dependency — same module-split pass |
| KEEP | ed3472a | adr: ADR-0007 — four execution models as first-class citizens |
| SQUASH all ADR-0007 iterations into above | 16decd1 | (same ADR, iterative refinement — multiple passes squash into final ADR state) |
| SQUASH into above | d724649 | (same) |
| SQUASH into above | c46d880 | (same) |
| SQUASH into above | f8125d0 | (same) |
| SQUASH into above | 9218953 | (same) |
| SQUASH into above | 54f5f3b | (same) |
| SQUASH into above | 83a58d9 | (same) |
| SQUASH into above | fb3e0aa | docs(claude): update engine-model name — CLAUDE.md maintenance, squash |

> **Result:** 2 commits. Import refactor; ADR-0007 (final state only — iterative ADR commits collapse into one).

---

## PR #215 — feat(worker): add configurable execution timeout with per-worker override (2026-04-29) [TREBLEREEL]

### Squash commit (unchanged)
| SHA | Message |
|-----|---------|
| ffa3c8c | feat(worker): add configurable execution timeout with per-worker override (#215) |

---

## PR #223 — fix(core): post-rename Jandex and CDI discovery fixes (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `fix/post-rename-jandex` on casehubio/engine

### Before (original commits)
| SHA | Message |
|-----|---------|
| 34658d3 | fix(core): post modules renaming fix |
| 48c4d97 | fix(tests): restore CDI bean discovery broken by scheduler-quartz extraction |
| 05d326c | fix(tests): add persistence-memory index and selected-alternatives to engine test config |
| d76b4f1 | fix(tests): add beans.xml to persistence-hibernate and fix engine test index config |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE all | 34658d3 | fix(core): post-rename Jandex, CDI discovery, and test config fixes — all four commits fix the same post-rename breakage |
| MERGE into above | 48c4d97 | (same) |
| MERGE into above | 05d326c | (same) |
| MERGE into above | d76b4f1 | (same) |

> **Result:** 1 commit. All four fix the same root cause (Jandex/CDI after module rename).

---

## PR #218 — feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `feat/worker-schedule-command-186` on casehubio/engine

### Before (original commits)
| SHA | Message |
|-----|---------|
| eb67bb3 | feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling (#186) |
| e4b2065 | chore: consistency pass — stale names, artifact leak, and terminology cleanup |
| 5136a62 | chore: replace stale quarkus-ledger name with casehub-ledger in DESIGN.md |
| a2a52a0 | chore: apply Spotless comment reflow in WorkItemLifecycleAdapter |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| KEEP | eb67bb3 | feat(worker): dispatch Qhorus COMMAND on worker channel after scheduling — Refs #186 |
| SQUASH into above | e4b2065 | chore: consistency pass — stale names — squash policy: chore cleanup follows feature |
| SQUASH into above | 5136a62 | chore: replace stale quarkus-ledger name in DESIGN.md — squash policy: chore cleanup |
| SQUASH into above | a2a52a0 | chore: apply Spotless comment reflow — squash policy: formatting fixup |

> **Result:** 1 commit.

---

## PR #224 — feat: WorkerContext.channels() populated and WorkerExecutionContext thread-local (Closes #220) (2026-05-01) [MDPROCTOR — casehubio/engine branch]

**Branch:** `feat/case-channel-provider-post-220` on casehubio/engine

### Before (original commits — unique beyond PR #218)
| SHA | Message |
|-----|---------|
| 73fda63 | feat(api): expose WorkerContext.channels() for worker-to-channel posting |
| 682cf37 | feat: expose WorkerContext.channels() via WorkerExecutionContext thread-local (Closes #220) |
| 93c9009 | docs(claude): document IntelliJ MCP tool preference convention |
| 9c59902 | fix(test): implement new LedgerEntryRepository methods in NoOpLedgerEntryRepository |

### After (proposed)
| Action | SHA | Proposed message |
|--------|-----|-----------------|
| MERGE both feat | 73fda63 | feat: WorkerContext.channels() exposed via API and WorkerExecutionContext thread-local — Closes #220 |
| MERGE into above | 682cf37 | (same feature — API exposure + thread-local wiring are part 1 and 2) |
| SQUASH | 93c9009 | docs(claude): IntelliJ MCP tool preference — CLAUDE.md maintenance, squash |
| KEEP | 9c59902 | fix(test): implement new LedgerEntryRepository methods in NoOpLedgerEntryRepository |

> **Result:** 2 commits.

---

## Reconstruction Notes

### Commits not appearing in main_20260502

The following merged PRs have no distinct commit in `main_20260502` because their content was superseded or absorbed:

| PR | Reason |
|----|--------|
| #52, #53, #54 | Content consolidated into PR #126 from mdproctor/engine:main |
| #60 | Rename work superseded by PR #62 |
| #61 | ContextDiffStrategy content absorbed into treblereel's PR #62 rebase |

### Stacked branch note

PRs #88→#90 and #91→#100 were developed as stacked branches. The "before" commits shown per PR are unique commits added in that PR only. During reconstruction, branches are replayed in merge order; each subsequent branch extends the previous.

### Low-quality squash messages (treblereel)

Two treblereel squash commits have poor messages and may warrant a note when presenting the reconstructed history:

| SHA | Original message | Suggested display note |
|-----|-----------------|----------------------|
| 7c2372a | Description: (#74) | Content: serialize concurrent signal processing with Vert.x local lock |
| 49dc32f | Feat/persistence/engine decoupling (#85) | Title-case artifact from branch name |
