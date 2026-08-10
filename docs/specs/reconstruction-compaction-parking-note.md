# History Reconstruction & Compaction — Parking Note

**Status:** Parked — resuming after engine code changes  
**Parked:** 2026-05-04  
**Resume trigger:** "pick up reconstruction" or similar

---

## What this is

A plan to replace `casehubio/engine` main's squash-merged history with a
curated, granular history that preserves Claude's well-written commit messages.
The other casehubio repos (ledger, work, qhorus, claudony) will be compacted
(noise reduction only, no reconstruction needed — they were committed directly
to main with granular history already).

---

## What we've done

### Infrastructure already in place

- **`main_20260502`** branch created on `casehubio/engine` — permanent snapshot
  of the squash-merged history. Never to be deleted.
- **All local engine branches pushed** to both `mdproctor/engine` (fork) and
  `casehubio/engine` (upstream). All work is safe in two places.
- **Fork workflow set up** for ledger, work, qhorus, claudony:
  - `origin` → `mdproctor/<repo>` (fork)
  - `upstream` → `casehubio/<repo>`
- **Squash/rebase merges disabled** on all 6 casehubio repos. Only merge commits
  and rebase merges allowed going forward.
- **`casehubio/engine` local main** synced with upstream (PR #228 merged and
  local + fork main brought in line).

### Documents produced

| Document | Location | Purpose |
|----------|----------|---------|
| Squash policy | `docs/superpowers/specs/commit-squash-policy.md` | Rules for KEEP/SQUASH/MERGE |
| Reconstruction plan | `docs/superpowers/specs/engine-reconstruction-plan.md` | Side-by-side table, all 86 PRs |

GitHub URLs:
- Policy: https://github.com/mdproctor/casehub/blob/main/docs/superpowers/specs/commit-squash-policy.md
- Plan: https://github.com/mdproctor/casehub/blob/main/docs/superpowers/specs/engine-reconstruction-plan.md

### Analysis findings

- **86 total PRs** on `casehubio/engine`
- **27 from treblereel** — kept as squash commits (his commit messages are brief,
  no granular history to recover, and we don't have his fork branches)
- **59 from mdproctor/casehubio** — reconstructed from source branches with
  full granular history
- **All source branches available** on `mdproctor/engine` and `casehubio/engine`

---

## Corrections needed before resuming

### 1. Remove ❌ DROP from squash policy — use filter-repo instead

**Problem discovered:** The ❌ DROP category in the squash policy is wrong in
principle. You should never drop a commit that contains file changes, even if
those files are workspace artifacts.

**Correct approach:**
1. Run `git filter-repo` first to strip workspace artifact files from the entire
   history: `HANDOFF.md`, blog entries, `CLAUDE.md` session-methodology content
2. `--prune-empty always` — commits that become empty after filtering are removed
   automatically
3. Squash policy operates only on commits with real file changes (KEEP/SQUASH/MERGE only)

**For engine specifically:** Engine has NO workspace artifacts in its history
(no HANDOFF.md, no blog entries — confirmed). So filter-repo is not needed for
engine. The DROP category can simply be removed from the engine reconstruction
plan. For other repos (ledger, work, qhorus, claudony) it will matter.

**Exception:** A commit with zero file changes (empty commit, e.g. `ci: retrigger`)
can be dropped directly — nothing to filter, nothing to preserve.

### 2. Three ❌ DROP commits in plan need reclassification

Current plan marks these as ❌ DROP (PRs #52/#53/#54 "superseded by #126"):
- `2ad38d9` — PoisonPill implementation, 542 lines of real code
- `959e3a4` — DeadLetterQueue implementation, 865 lines of real code
- `b30c1c2` — backoff strategies, 446 lines of real code

**Correct label:** These are ❌ DROP because PR #126 reconstruction covers the
same code — they'd be double-counted, not because the code is being discarded.
The plan needs a clearer label: "covered via PR #126 reconstruction — not
double-counted" rather than bare ❌ DROP.

`07a89a8` (Revert "build: wire BOM") is also ❌ DROP but should be SQUASH —
it's part of a revert chain and touches real build files.

### 3. cc-praxis git-squash skill needs updating (tell cc-praxis Claude)

Two things to pass to cc-praxis Claude:

**A. Remove/revise the "Always DROP" table in squash-policy.md:**
The workspace artifact patterns (session handovers, blog entries, CLAUDE.md
updates) should be handled by `git filter-repo --prune-empty always`, not by
the squash classification. The squash policy should only classify commits with
real file changes.

**B. Add CLAUDE.md split policy:**
- **Keep in repo CLAUDE.md:** build commands, test patterns, naming conventions,
  architecture notes — valuable to any developer using Claude Code
- **Move to workspace CLAUDE.md:** personal working style, session methodology,
  collaboration preferences — personal, no value to other contributors
The "Always DROP" patterns for `docs(claude): ...` need revision: squash if
project-convention content, only drop if purely personal methodology.

### 4. Workspaces needed for each casehubio repo

Run cc-praxis `workspace-init` for engine, ledger, work, qhorus, claudony.
Once workspaces exist, HANDOFF.md and blog entries never touch the project repo
going forward. The filter-repo step is only needed to rectify the past.

---

## The reconstruction execution plan (when resuming)

### Phase 1 — Fix the plan

1. Update `engine-reconstruction-plan.md`:
   - Remove ❌ DROP category (not needed for engine — no workspace artifacts)
   - Reclassify `07a89a8` as SQUASH (revert chain member)
   - Add clarifying note to PRs #52/#53/#54 drop entries

2. Get approval on the corrected side-by-side plan

### Phase 2 — Build main_proposal locally

```bash
cd ~/claude/casehub/engine
git checkout -b main_proposal main_20260502
```

For each PR in merge order:
- **Treblereel PRs:** `git cherry-pick <squash-sha-from-main_20260502>`
- **mdproctor PRs:** replay individual commits from source branch, applying
  squash policy (`git rebase -i` or individual cherry-picks with interactive
  fixup/squash)

### Phase 3 — Verify

```bash
# Tree at HEAD of main_proposal must match main_20260502 exactly
git diff main_20260502 main_proposal
# Expected: empty diff (same code, different history)
```

### Phase 4 — Review

```bash
git log --oneline main_proposal   # review the curated history
```
Open for user review. Iterate if anything looks wrong.

### Phase 5 — Push to mdproctor/engine

```bash
git push origin main_proposal
```

User reviews on GitHub. Adjustments made if needed.

### Phase 6 — Push to casehubio/engine (after user approval)

```bash
git push upstream main_proposal
```

Treblereel reviews. Gets his sign-off.

### Phase 7 — The swap (treblereel must agree first)

```bash
# On casehubio/engine:
# 1. Rename current main → main_20260502_squashed (extra backup)
# 2. Rename main_proposal → main
# 3. Force-push (or branch rename via GitHub API)
```

---

## Other repos — compaction only (not reconstruction)

ledger, work, qhorus, claudony were committed directly to main (no PR squashing).
They need:
1. Workspaces created (workspace-init)
2. git filter-repo to remove workspace artifact files from history
3. Squash policy applied to reduce noise (same KEEP/SQUASH/MERGE rules)
4. Same mdproctor → casehubio review flow before touching casehubio main

---

## Key decisions made

| Decision | Rationale |
|----------|-----------|
| Treblereel's PRs kept as squash | His commit messages are brief; no granular history to recover |
| ❌ DROP removed as a category | Use filter-repo + prune-empty instead — never drop commits with files |
| CLAUDE.md stays in project repo | Valuable to any developer using Claude Code; split project vs personal content |
| Squash/rebase disabled on casehubio repos | Prevents recurrence of squash-merge history loss |
| main_20260502 as permanent backup | Never deleted — always available as fallback |
| mdproctor review first, casehubio after | Two-stage review before touching upstream |
| Treblereel must sign off before main rename | Coordination required for destructive history rewrite |
