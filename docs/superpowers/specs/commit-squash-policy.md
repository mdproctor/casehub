# Commit Squash Policy — casehubio Ecosystem

**Purpose:** Applied during history reconstruction and enforced pre-push by `java-git-commit`.
Retains the granular, AI-generated commit messages that carry genuine information while
eliminating noise that obscures the history.

**Context:** This policy was derived from analysing the `casehubio/engine` commit history.
Claude generates high-quality, specific commit messages. The noise patterns are mechanical
artifacts of the development process, not meaningful history.

---

## Keep as standalone commits

These commit types carry information a future developer needs when reading `git log`:

| Pattern | Reason to keep |
|---------|---------------|
| `feat(scope): ...` | Introduces a new capability — the what and why matter |
| `fix(scope): ...` with ≥ 10 lines changed | Real bug fix with context |
| `test(scope): <scenario name> ...` | Describes specific test scenario — documents intended behaviour |
| `refactor(scope): ...` with ≥ 20 lines changed | Structural change worth understanding |
| `adr: ...` | Architecture decision record — always standalone |
| `docs: DESIGN.md ...` with substantive content | Design decisions, not fixups |
| Any commit referencing an issue number (`Closes #N`, `Refs #N`) | Traceability |

---

## Squash into the preceding meaningful commit

These are artifacts of the development process, not history:

| Pattern | Action |
|---------|--------|
| `Revert "..."` followed within 3 commits by a replacement | Squash all three (revert + retry + fix) into the final working commit |
| `chore: remove dead ...` / `chore: apply spotless` / `chore: fix formatting` | Squash into preceding commit |
| `docs(scope): align Javadoc ...` / `docs(scope): fix wording` / `docs(scope): add missing ...` | Squash into the feature commit it follows |
| `fix(test): ...` where the same test class was fixed in the previous commit | Squash — it's the same test being hardened |
| `build: wire ...` with a `Revert "build: wire ..."` following it | Both are noise — squash into the eventual working state |
| Any commit with `< 5 lines changed` and no issue reference | Squash into preceding commit |
| Multiple commits with near-identical messages on the same class/file | Keep the last (most complete), squash the earlier ones |

---

## Special cases

**Revert chains:** `Revert X` + `X (attempt 2)` + `fix` → collapse to one commit with the
final message, noting the approach that worked. Do not preserve the failed attempts.

**Test hardening runs:** When 3+ commits touch the same test class (`fix(test): ...`
repeatedly), keep only the last one. The earlier attempts add no information once the
final version is known.

**CI/build fixups:** `ci: retrigger`, `build: bump`, `fix(ci): correct URL` — squash into
the feature or fix they were unblocking, or discard if purely mechanical.

---

## How this is applied

### During history reconstruction

1. For each PR branch, list all commits
2. Classify each commit as KEEP or SQUASH per the rules above
3. Present the proposed groupings for author review
4. Execute `git rebase -i` with the approved plan

### Pre-push enforcement (java-git-commit)

Before `git push` or PR creation:
1. List all commits ahead of main
2. Flag any SQUASH candidates
3. Show proposed grouping
4. Require author approval before pushing

---

## Examples from casehubio/engine

### Keep all — each commit tells a distinct story
```
feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider — Closes #152
test(blackboard): R1 — two sequential stages activate in order
test(blackboard): R3 — exit condition satisfied by worker output end-to-end
fix(blackboard): nested stage activation gated on parent ACTIVE state
feat(blackboard): CaseEvictionHandler — evict plan models on terminal case state
```

### Squash — revert chain collapses to one
```
SQUASH ← build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger
SQUASH ← Revert "build: wire casehub-parent BOM and GitHub Packages for quarkus-ledger"
KEEP   → build: remove embedded ledger/work builds; use GitHub Packages; add distributionManagement
```

### Squash — small docs follow-on
```
KEEP   → feat(engine): add CaseMetaModelRepository, CaseInstanceRepository, EventLogRepository SPI interfaces
SQUASH ← docs(engine): align findByKey Javadoc null-return wording
```

### Squash — cleanup after feature
```
KEEP   → feat(engine): wire WorkerStatusListener, WorkerContextProvider, CaseChannelProvider
SQUASH ← chore: remove dead workerContextProvider.buildContext() call
SQUASH ← test(engine): remove WorkerContextProvider wiring tests
```

---

## Repos this policy applies to

- `casehubio/engine` — origin of this policy (history reconstruction 2026-05-02)
- `casehubio/ledger` — apply after engine is verified
- `casehubio/work` — apply after engine is verified
- `casehubio/qhorus` — apply after engine is verified
- `casehubio/claudony` — apply after engine is verified
