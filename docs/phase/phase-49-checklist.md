# Phase 49 Checklist - Entity Conflict and Conditional Transition

status=open
phase=[Phase 49 - Entity Conflict and Conditional Transition](phase-49.md)

This checklist is the authoritative Phase 49 state ledger. Only one stage may
be `IN_PROGRESS` at a time.

## EC-01: Normative Contract

Stage Status:
- Current status: DONE
- Owner: CNCF Entity/UnitOfWork/datastore maintainers
- Update rule: Mark DONE only when design and static specification resolve
  every EC-01 decision and contain stable references to executable evidence
  planned by later stages.

- [x] Define the framework-owned concurrency token and its equality,
  advancement, visibility, and persistence semantics.
- [x] Select the reserved managed storage field and define behavior for records
  created before that field exists.
- [x] Define expected-token behavior for ordinary Entity/Aggregate mutation.
- [x] Define stale ordinary mutation as a structured conflict failure.
- [x] Define a closed exact-match expectation model for conditional transition.
- [x] Define successor `Create` and `Bind` semantics.
- [x] Define `Transitioned` and `NotMatched(existing)` as normal typed results.
- [x] Define not-found, authorization, malformed policy, unsupported
  capability, provider, transaction, and conversion failures.
- [x] Define one-provider/one-transaction-domain atomicity and rollback
  requirements.
- [x] Define provider capability declaration and deterministic rejection.
- [x] Define authorization order and information-disclosure boundaries.
- [x] Define lifecycle hook, audit, CallTree, metrics, EntitySpace, Working Set,
  and View behavior.
- [x] Define the Phase 49 contract as completion of the 9.12 version-conflict
  baseline and 9.39 conditional-transition capability.
- [x] Keep force/repair commands, merge workflows, and conflict-resolution UI
  outside 9.12 baseline completion and tracked by strategy item 9.40.
- [x] Select the SQLite and shared-datastore acceptance profiles.
- [x] Promote the contract to:
  - `docs/design/entity-conflict-and-conditional-transition.md`;
  - `docs/spec/entity-conflict-and-conditional-transition.md`.

Evidence:
- Normative contract:
  - `docs/design/entity-conflict-and-conditional-transition.md`;
  - `docs/spec/entity-conflict-and-conditional-transition.md`.
- Independent review completed.
- Review findings for authorization, atomicity, evidence wording, rollback
  coverage, status, and design rationale were resolved.
- Clean re-review completed with no actionable findings.
- Document validation:
  - 25 contiguous normative rules;
  - 18 contiguous executable examples;
  - `git diff --check`.

## EC-02: Concurrency Model and Storage Shape

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF Entity model maintainers
- Update rule: Mark IN_PROGRESS only after EC-01 closes. Mark DONE only when
  typed values, managed storage metadata, and migration behavior have
  executable model/storage evidence.

- [x] Add the typed concurrency-token model without exposing datastore/vendor
  details.
- [x] Add managed revision metadata to the canonical Entity storage shape.
- [x] Prevent application patches and records from writing managed revision
  metadata directly.
- [x] Define and implement the canonical initial token.
- [x] Define and implement loading/migration behavior for records without a
  token.
- [ ] Advance the token exactly once for each admitted successful mutation.
- [x] Preserve existing Entity id, lifecycle, audit, and content-body storage
  behavior.
- [x] Add property-based token and storage-shape specifications.

Evidence:
- EC-02A implemented, cleanly re-reviewed, and release-validated:
  - `build.sbt` keeps production Java sources on the existing JVM 8
    classfile baseline and admits the existing Java test probe at its minimum
    Java 14 language baseline;
  - `src/main/java/org/goldenport/cncf/entity/EntityConcurrencyToken.java`;
  - `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala`;
  - `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyTokenSpec.scala`.
- EC-02A focused validation:
  - `sbt --batch "testOnly org.goldenport.cncf.entity.EntityConcurrencyTokenSpec"`:
    5 tests passed.
  - `sbt -J-Xmx4G --batch Test/compile`: passed.
  - clean compilation produced classfile major version 52 for both the
    production Scala metadata codec and Java token boundary, and major version
    58 for the existing Java test probe.
  - `sbt -J-Xmx4G --batch test`: 2363 tests succeeded, 0 failed, 2 canceled,
    1 ignored, and 59 pending across 334 completed suites.
  - `git diff --check`: passed.
- EC-02A review:
  - whole-file naming and executable-specification review completed;
  - public JVM API and classfile compatibility reviewed;
  - clean re-review completed with no actionable findings.
- EC-02 remained open after EC-02A because canonical create/load integration,
  managed-field composition, mutation advancement, and regression evidence
  belonged to later slices.
- EC-02B implementation release-validated after initial review findings were
  fixed and the fresh re-review found no remaining actionable issue:
  - canonical create/upsert-create/save-create/import initialize token `1`;
  - existing save/update/upsert/soft-delete/update-by-id/import preserve the
    authoritative token and discard caller revision aliases;
  - `SimpleEntityStorageShapePolicy` owns the logical/physical revision mapping
    and managed-field classification;
  - plain load/search/identity decoding validates and removes concurrency
    metadata before invoking domain codecs;
  - UnitOfWork create/upsert strips storage metadata before admitting a
    persisted Entity to EntitySpace;
  - `EntityStore.loadSnapshot` and `EntityStoreSpace.loadSnapshot` return the
    typed Entity together with its admitted token;
  - legacy records, including records carrying only the reserved logical alias,
    load with virtual token `0` without storage backfill;
  - `EntityConcurrencyTokenSpec` now contains nine executable behaviors,
    including generated canonical create/load evidence, UnitOfWork/EntitySpace
    isolation, and mutation/import protection;
  - `UnitOfWorkTargetAuthorizationSpec` is organized into five navigable
    authorization feature areas.
- EC-02B focused validation:
  - `sbt -J-Xmx4G --batch "testOnly
    org.goldenport.cncf.entity.EntityConcurrencyTokenSpec
    org.goldenport.cncf.entity.EntityStoreQueryRouteSpec
    org.goldenport.cncf.entity.EntityStoreImportSeedSpec
    org.goldenport.cncf.unitofwork.UnitOfWorkTargetAuthorizationSpec"`:
    73 tests passed across four suites;
  - `sbt -J-Xmx4G --batch Test/compile`: passed;
  - clean `Test/compile`: passed across 507 main and 351 test sources;
  - `sbt -J-Xmx4G --batch test`: 2367 tests passed across 334 suites;
  - `git diff --check`: passed.
- EC-02B does not compare or advance a caller token. Atomic comparison and
  exactly-once advancement remain EC-03.

Modified Scala File Compliance Ledger:

| File | Naming review | Executable-spec review | Validation | Disposition |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | Whole file passed | Not a spec | Focused 5-test spec, `Test/compile`, and full 2363-test suite passed | EC-02A release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyTokenSpec.scala` | Whole file passed | Whole file passed; Given/When/Then behavior and two ScalaCheck properties | Focused 5-test spec, `Test/compile`, and full 2363-test suite passed | EC-02A release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | Whole file implementation check passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | Whole file naming cleanup and implementation check passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | Whole file naming cleanup and implementation check passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/main/scala/org/goldenport/cncf/entity/SimpleEntityStorageShapePolicy.scala` | Whole file naming cleanup passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/main/scala/org/goldenport/cncf/projection/MetaProjectionSupport.scala` | Whole file naming check passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | Whole file implementation check passed | Not a spec | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyTokenSpec.scala` | Whole file implementation check passed | Nine Given/When/Then behaviors and three ScalaCheck properties cover generated token/storage behavior | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala` | Whole file naming cleanup passed | All 37 behaviors have aligned Given/When/Then boundaries under five `which` feature groups | Focused 73-test matrix, clean `Test/compile`, and full 2367-test suite passed | EC-02B release commit |

## EC-03: Version-aware Mutation

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity/Aggregate maintainers
- Update rule: Mark IN_PROGRESS only after EC-02 closes. Mark DONE only when
  every admitted path compares its expected token in the native mutation and
  stale updates cannot change storage or resident state.

- [ ] Add expected-token forms for Entity save.
- [ ] Add expected-token forms for typed Entity update.
- [ ] Add expected-token forms for patch update by id.
- [ ] Integrate the minimum Aggregate-root and framework state-transition paths
  required by the generic conflict foundation.
- [ ] Ensure the datastore comparison and mutation are one atomic operation.
- [ ] Return structured conflict diagnostics with expected/actual metadata
  according to the accepted redaction contract.
- [ ] Ensure Working Set values cannot supply or bypass the authoritative
  token check.
- [ ] Define the temporary policy for unversioned mutation paths without
  treating them as implicit force/repair.
- [ ] Add concurrent stale-update executable specifications.

Evidence:
- Pending.

## EC-04: Atomic Datastore Capability

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore maintainers
- Update rule: Mark IN_PROGRESS only after EC-03 closes. Mark DONE only when
  the closed record-level plan/result and deterministic unsupported behavior
  are implemented without a load-then-save fallback.

- [ ] Add a supplementary atomic conditional-mutation datastore capability.
- [ ] Add bounded root guard, successor mutation, root change, and provider
  result models.
- [ ] Require all collections to belong to one component and resolve to one
  transaction domain.
- [ ] Reject reserved-field mutation, duplicate expected fields, unsupported
  values, missing token, and empty effective root mutation.
- [ ] Return authoritative root/successor records from the provider result.
- [ ] Roll back successor and root changes together on every failure.
- [ ] Reject unsupported providers deterministically.
- [ ] Implement deterministic in-memory reference behavior.
- [ ] Add property-based one-winner and rollback specifications.

Evidence:
- Pending.

## EC-05: EntityStore, UnitOfWork, and Protected DSL

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity and action-runtime maintainers
- Update rule: Mark IN_PROGRESS only after EC-04 closes. Mark DONE only when
  the typed operation traverses the canonical ActionCall/UnitOfWork/EntityStore
  path and no public CRUD or raw storage escape is introduced.

- [ ] Add typed transition expectation, successor, mutation, request, and
  result models.
- [ ] Add `EntityStore` and `EntityStoreSpace` conditional-transition
  operations.
- [ ] Add one explicit `UnitOfWorkOp` and interpreter branch.
- [ ] Add protected ActionCall/Behavior DSL helpers.
- [ ] Add an explicitly bounded `ServiceInternal` variant without bypassing
  Entity or component authorization.
- [ ] Normalize typed values into the closed datastore plan inside the
  framework.
- [ ] Apply successor storage-shape and content-body policies.
- [ ] Preserve normal transition-validation hooks.
- [ ] Keep the operation absent from automatic REST/Form/CLI/MCP CRUD
  projection.
- [ ] Preserve all structured `Consequence` failures unchanged.

Evidence:
- Pending.

## EC-06: Coherence, Authorization, Audit, and Diagnostics

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity runtime, security, and observability maintainers
- Update rule: Mark IN_PROGRESS only after EC-05 closes. Mark DONE only when
  authoritative outcomes drive cache, View, security, audit, and diagnostic
  behavior with no payload leakage.

- [ ] Authorize root read/update and successor create or bind/read as required.
- [ ] Ensure `NotMatched(existing)` returns no unauthorized root data.
- [ ] Change no EntitySpace/Working Set/View state before provider success.
- [ ] Reconcile root and successor resident state after `Transitioned`.
- [ ] Reconcile a stale local root after `NotMatched`.
- [ ] Invalidate affected Views only for committed mutations.
- [ ] Emit bounded audit evidence with accepted redaction.
- [ ] Add ActionCall, UnitOfWork, EntityStoreSpace, and DataStore CallTree
  layers.
- [ ] Add metrics for transition, mismatch, stale conflict, unsupported
  capability, authorization denial, provider failure, and transaction failure.
- [ ] Classify failures from typed results and structured `Conclusion`, never
  display-message parsing.
- [ ] Prove expected values and Entity payloads are absent from default
  observability.

Evidence:
- Pending.

## EC-07: Provider and Concurrency Evidence

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore/provider maintainers
- Update rule: Mark IN_PROGRESS only after EC-06 closes. Mark DONE only when
  in-memory, SQLite, and the selected shared profile pass the same semantic
  matrix with independent callers.

- [ ] Run bounded-caller property evidence against in-memory reference
  behavior.
- [ ] Implement SQLite conditional transition with one explicit native
  transaction.
- [ ] Exercise SQLite with independent callers and independent connections.
- [ ] Prove SQLite restart visibility.
- [ ] Inject failure after guard match, during successor work, during root
  update, and during commit.
- [ ] Verify every injected failure leaves no orphan successor or partial root.
- [ ] Implement or activate the selected shared-datastore profile.
- [ ] Exercise the shared profile with independently executing callers.
- [ ] Prove exactly one winner and provider-neutral result parity.
- [ ] Preserve claim-or-load behavior as a regression boundary.

Evidence:
- Pending.

## EC-08: CBD Support Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF and CBD Support maintainers
- Update rule: Mark IN_PROGRESS only after EC-07 closes. Mark DONE only when
  CBD Support uses the generic protected capability and its concurrent
  successor workflow passes without a persistence escape hatch.

- [ ] Replace CBD Support application-side terminal successor ownership logic
  with the protected conditional-transition DSL.
- [ ] Retain the complete terminal predecessor snapshot.
- [ ] Prove simultaneous eligible requests install exactly one successor.
- [ ] Prove losing requests observe the authoritative successor ownership.
- [ ] Prove expensive successor work starts once.
- [ ] Verify no CBD Support SQL, JDBC, raw DataStore, provider transaction, or
  process-local lock is introduced.
- [ ] Record downstream executable-specification and commit evidence.

Evidence:
- Pending.

## EC-09: Verification and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF release maintainers
- Update rule: Mark IN_PROGRESS only after EC-08 closes. Mark DONE only after
  all validation, review, documentation, downstream, and closure evidence is
  recorded.

- [ ] Run focused Entity, UnitOfWork, datastore, provider, security,
  observability, cache, and View specifications.
- [ ] Run `sbt --batch Test/compile`.
- [ ] Run the full CNCF test suite.
- [ ] Run the relevant downstream CBD Support suite.
- [ ] Run `git diff --check`.
- [ ] Run read-only CNCF review.
- [ ] Fix every actionable finding.
- [ ] Run clean re-review.
- [ ] Update design/spec with verified implementation details.
- [ ] Add one strategy section 8 completed-history item,
  `Entity Conflict and Conditional Transition`.
- [ ] Record both the 9.12 baseline and 9.39 as completed.
- [ ] Remove 9.12 and 9.39 from active/future strategy section 9.
- [ ] Preserve 9.40 as the future conflict-resolution/repair item.
- [ ] Preserve closed historical phase documents while updating current
  strategy status.
- [ ] Close Phase 49 only after exact commit and test evidence is recorded.

Evidence:
- Pending.
