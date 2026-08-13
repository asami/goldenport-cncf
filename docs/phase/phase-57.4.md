# Phase 57.4 - Build and Publication Compatibility Retirement

status=done
split_from=[Phase 57](phase-57.md)
depends_on=[Phase 57.3](phase-57.3.md)
successor=[Phase 57.5](phase-57.5.md)
checklist=[Phase 57.4 Checklist](phase-57.4-checklist.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Goal

Remove unreleased compatibility from Cozy, sbt-cozy, CAR packaging,
publication, and repository-index production, then rebuild the active local
warehouse from fresh canonical `-SNAPSHOT` artifacts.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Closure

- Packager, publisher, lint, and repository writers emit only schema 3, ABI v2,
  index v2, and canonical coordinates.
- Unreleased legacy conversion and fallback are absent unless backed by an
  explicit published/production authority.
- When forensic retention is requested, `~/.cncf/local` is moved to a unique
  sibling outside active lookup and the active warehouse is rebuilt empty.
- Canonical dependencies and representative CARs are freshly built and locally
  published in dependency order.
- sbt-cozy remains `0.1.20-SNAPSHOT` and Cozy remains `0.3.4-SNAPSHOT`.

## Completion/Current State

- AES-06B Slices A through C are implemented, validated, and independently
  reviewed without a current blocker.
- Cozy Step commit:
  `98436875582e31b83959c4638fcd0152f63c8cc0`.
- sbt-cozy Step commit:
  `7e37adff5fdfd8d4dbca6b86ef92d90bcc5683cc`.
- The prior active warehouse is retained at
  `~/.cncf/local.phase57.4-forensic-20260813T191338Z`; the active
  `~/.cncf/local` was rebuilt empty and contains only fresh canonical
  ArtScene, ControlCenter, Scraper, and Supervisor `-SNAPSHOT` CARs.
- Catalog schema 2, descriptor schema 3, ABI manifest v2, repository index v2,
  external CAR checksums, runtime integrity entries, and exact qualified
  launcher resolution were verified.
- Final sbt-cozy invocation `76728-20260813T195405Z` passed 29 suites with
  144/144, 0 failed or aborted, and 5 explicitly canceled integration cases;
  SBT/wrapper exits were 0/0 and the shared lock was released.
- Final Cozy invocation `73349-20260813T194937Z` completed 94 suites and 1,303
  tests with 1,289 passed, 14 failed, 7 canceled, and the shared lock released.
  Scope containment proved all failures belong to unchanged article-media
  cross-suite fixture/concurrency behavior, not AES-06B CAR production; the
  complete nonzero evidence is persisted as `HYG-P57.4-003` rather than
  reported as an all-tests pass.
- The Phase-owned lint and canonical producer accumulator passed 21/21 and
  118/118 respectively; fresh warehouse integrity and exact launcher
  consumption were independently reviewed without a current blocker.
- Phase 57.4 is DONE. Phase 57.5 remains planned and unstarted.

## Non-Goals

- Runtime compatibility already closed by Phase 57.3.
- Automatic backup, migration, merge, or restoration.
- Public release or removal of `-SNAPSHOT`.
