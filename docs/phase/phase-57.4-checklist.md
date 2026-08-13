# Phase 57.4 Checklist - Build and Publication Compatibility Retirement

status=done
phase=[Phase 57.4 - Build and Publication Compatibility Retirement](phase-57.4.md)
predecessor=[Phase 57.3](phase-57.3.md)
successor=[Phase 57.5](phase-57.5.md)

## AES-06B: Build/Publication Compatibility Retirement

Stage Status:
- Current status: DONE
- Current step: Phase 57.4 closure complete
- Owner: CNCF Phase 57.4
- Entry rule: Phase 57.3 is DONE.
- Completion rule: Canonical build/publication evidence and a fresh active
  local warehouse replace unreleased legacy production paths.
- Update rule: Record only accepted implementation, review, validation,
  publication, and commit evidence; final Phase closure requires the separate
  Phase Closure checklist below.
- Closure basis: The seven AES-06B items and both Phase Closure items are the
  authoritative basis for Phase completion.

- [x] Inventory Cozy, sbt-cozy, packager, publisher, lint, and index writers.
- [x] Remove unauthorized legacy conversion, v1 output, alias, and fallback.
- [x] Preserve the full local warehouse as a timestamped forensic sibling when
  requested; do not read it from active runtime lookup.
- [x] Create a clean active warehouse.
- [x] Fresh-build and publish canonical `-SNAPSHOT` libraries/plugins/CARs in
  dependency order.
- [x] Verify representative schema 3, ABI v2, index v2 metadata and runtime
  consumption.
- [x] Review once and commit the accepted migration.
  - Slice A validation: scripted invocation `18751-20260813T113658Z` passed
    1/1; focused invocation `19265-20260813T113747Z` passed 51/51.
  - Slice B validation: lint invocation `40884-20260813T123415Z` passed 21/21;
    accumulator `41348-20260813T123526Z` passed 118/118.
  - Slice C independent review accepted the fresh warehouse, four canonical
    CARs, integrity evidence, and launcher list/show consumption without a
    current blocker.
  - Cozy Step commit:
    `98436875582e31b83959c4638fcd0152f63c8cc0`.
  - sbt-cozy Step commit:
    `7e37adff5fdfd8d4dbca6b86ef92d90bcc5683cc`.

## Phase Closure

Stage Status:
- Current status: DONE
- Owner: Phase 57.4 closure
- Completion rule: AES-06B is complete, its accepted hygiene ledger is
  persisted, and every required Phase repository completes its final
  serialized validation with any demonstrably unrelated failure explicitly
  contained by the goal-phase release policy.
- Checklist closure basis: Both items below must be checked before Phase 57.4
  is closed.

- [x] Run the final Phase 57.4 full-validation gate.
  - Cozy invocation `73349-20260813T194937Z` completed 94 suites and 1,303
    tests with 1,289 passed, 14 failed, 7 canceled, and SBT/wrapper exits 1/1;
    the serialized lock was released. All failures were separated from
    AES-06B as unchanged article-media fixture/concurrency behavior and are
    persisted without an all-tests-pass claim in `HYG-P57.4-003`.
  - sbt-cozy invocation `76728-20260813T195405Z` completed 29 suites with
    144/144, 0 failed or aborted, and 5 explicitly canceled external
    integration cases; SBT/wrapper exits were 0/0 and the lock was released.
  - The framework release delta is documentation-only; no framework SBT was
    run for this Phase release commit.
- [x] Close Phase 57.4 without starting Phase 57.5.
