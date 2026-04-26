# Session Handover — CaseHub
**Date:** 2026-04-26
**Branch (casehub-engine):** main (casehubio — `604ef8b`)

---

## Where We Are

Ecosystem build is green. All major CI issues resolved.

**What landed since last handover:**
- PRs #140–#144 (WorkBroker), #151 (ledger), #154–#157 merged to casehubio/engine
- casehub-parent dashboards fixed — were silently skipping casehub-engine (checked `publish.yml`; engine uses `maven.yml`); now queries latest push run regardless of workflow name; `continue-on-error` + `exit 1` fixes for full-stack build false greens
- `quarkus-qhorus` and `claudony` fixed — both needed `<repositories>` pointing to GitHub Packages in their poms; claudony CI had `server-id: github-casehubio` mismatched to `<id>github</id>`
- `ChoreographySelectionTest` hardened — binding `.trigger == "go"` was permanently true, re-fired after worker wrote output; fixed with `and .result == null`. Per-run-ID tracking replaces shared static counters.
- `WorkerRetryExtendedTest` hardened — event log assertions folded into `await()` blocks
- Ecosystem CLAUDE.mds updated across all 6 repos; `casehub-parent/CLAUDE.md` created
- `mdproctor` added to casehubio `developers` team with write access to all 7 repos — can now push directly without fork PRs

**Still open:**
- `casehub-engine` deploy — `mvn verify` works; `mvn deploy` fails on first-ever SNAPSHOT publish to GitHub Packages. Maven tries to GET snapshot metadata before uploading; GitHub Packages returns a non-404 error for non-existent artifacts. CI is on `verify` for now. Fix: initialize the namespace, or configure wagon plugin retry.
- PR #159 (`Scheduler refactoring`) open on casehubio/engine — check CI, merge when ready.

---

## Immediate Next Steps

1. Unblock `mvn deploy` — either initialize the GitHub Packages namespace for `io.casehub:api` manually, or investigate `maven-wagon-http` config to handle missing metadata gracefully
2. Check/merge PR #159
3. Run the casehub-parent dashboards and confirm they show accurate green across all repos

---

## Key Files

- `casehub-parent/.github/workflows/dashboard.yml` — queries `?branch=main&event=push`, not workflow name
- `casehub-engine/engine/src/test/java/.../ChoreographySelectionTest.java` — per-run-ID tracking + `and .result == null` binding
- `casehub-engine/pom.xml` — `maven.deploy.skip=true` default; `version.io.quarkiverse.*` properties

## Repo Build Status (end of session)

| Repo | Status |
|------|--------|
| casehubio/engine | ✅ |
| casehubio/quarkus-work | ✅ |
| casehubio/quarkus-ledger | ✅ |
| casehubio/quarkus-qhorus | ✅ |
| casehubio/claudony | ✅ |
| casehubio/casehub-parent | ✅ |
