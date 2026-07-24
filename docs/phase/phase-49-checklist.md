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
- Current status: DONE
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
- [x] Advance the token exactly once for each admitted successful mutation.
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
  exactly-once advancement were completed by the EC-03A provider-owned
  single-record atomic mutation foundation.
- EC-03A completion evidence for the remaining EC-02 requirement:
  - expectation-required Entity full save, typed update, and patch-by-id
    compare the physical revision inside the provider capability;
  - successful admitted mutations advance exactly once;
  - generated 2-to-12-caller legacy-record races admit exactly one
    absence-to-one winner;
  - focused validation passed 19 tests across four suites.

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
- Current status: DONE
- Owner: CNCF Entity/Aggregate maintainers
- Update rule: Mark IN_PROGRESS only after EC-02 closes. Mark DONE only when
  every admitted path compares its expected token in the native mutation and
  stale updates cannot change storage or resident state.

- [x] Add expected-token forms for Entity save.
- [x] Add expected-token forms for typed Entity update.
- [x] Add expected-token forms for patch update by id.
- [x] Integrate the minimum Aggregate-root and framework state-transition paths
  required by the generic conflict foundation.
- [x] Ensure the datastore comparison and mutation are one atomic operation.
- [x] Return structured conflict diagnostics with expected/actual metadata
  according to the accepted redaction contract.
- [x] Ensure Working Set values cannot supply or bypass the authoritative
  token check.
- [x] Define the temporary policy for unversioned mutation paths without
  treating them as implicit force/repair.
- [x] Add concurrent stale-update executable specifications.

Evidence:
- EC-03A implementation validation:
  - `EntityVersionedMutationDataStore` is a supplementary provider capability;
  - `DataStoreSpace` rejects unsupported and cross-provider side-record plans
    before mutation;
  - the in-memory provider serializes ordinary CRUD/search with versioned
    mutation, prepares root and side-record states, and publishes all affected
    collections through one immutable top-level state swap only after the
    complete plan succeeds;
  - `ContentBodyStoragePolicy.planForVersionedSave` returns pure bounded
    side-record save/delete effects;
  - stale ContentBody overflow mutation changes neither root nor side record;
  - focused validation passed 19 tests:
    `EntityVersionedMutationDataStoreSpec`,
    `EntityVersionedMutationSpec`,
    `ContentBodyVersionedMutationSpec`, and
    `EntityConcurrencyTokenSpec`.
  - `sbt -J-Xmx4G --batch test`: 2377 tests passed across 337 suites.
- EC-03B implementation adds snapshot load plus expectation-required
  save/update/patch operations to the UnitOfWork algebra. Provider success
  installs the returned authoritative Entity and invalidates views once;
  stale conflict evicts the resident value without invalidating views.
- The normal protected Entity DSL no longer exposes upsert overwrite.
  Stable-identity ownership remains `entity_claim_or_load`; create-only
  collection writes are separate from versioned saves.
- Aggregate create is create-only, Aggregate update requires an explicit
  expectation, and Aggregate command persists with the root snapshot admitted
  during its resolve phase.
- Explicit unversioned save/update/upsert operations require both a closed
  `EntityUnversionedMutationPurpose` and System admission. No protected
  application DSL exposes these operations.
- `UnitOfWorkVersionedMutationSpec` covers authoritative snapshot
  reconciliation, stale resident eviction, unchanged authoritative storage,
  and rejection of non-System unversioned mutation.
- EC-03B implementation validation:
  - the focused UnitOfWork, authorization, Aggregate, EntityStore, child
    binding, Tag, JobControl, and Static Form matrix passed 458 tests across
    11 suites;
  - `StaticFormAppRendererSpec` passed all 319 tests, including authoritative
    hidden-version projection, rejection of a stale browser update form, and
    canonical EntityId projection through `sourceEntityId`;
  - `sbt -J-Xmx4G --batch Test/compile` passed;
  - `sbt -J-Xmx4G --batch test`: 2380 tests passed across 338 suites, with
    2 canceled, 1 ignored, and 59 pending;
  - `git diff --check` passed.
- Clean re-review found no actionable implementation, naming, or
  executable-specification finding.
- EC-03 is complete. The next implementation slice is EC-04.

EC-03B Modified Scala File Compliance Ledger:

| File | Naming review | Executable-spec review | Validation | Disposition |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/ChildEntityBindingWorkflow.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityPersistent.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/entity/runtime/Collection.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererComponentAdminPart.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/job/JobEngine.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/tag/TagModel.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala` | Whole-file naming review passed | Not a spec | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/action/ActionCallAggregateResolveSpec.scala` | Whole-file naming review passed | Given/When/Then Aggregate resolve and mutation behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/action/ActionCallEntityAccessMetricsSpec.scala` | Whole-file naming review passed | Grouped Given/When/Then Entity access and metrics behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/entity/ChildEntityBindingWorkflowSpec.scala` | Whole-file naming review passed | Given/When/Then child binding and compensation behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyTokenSpec.scala` | Whole-file naming review passed | Nine Given/When/Then behaviors with ScalaCheck properties passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityStoreQueryRouteSpec.scala` | Whole-file naming review passed | Given/When/Then EntityStore route behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala` | Whole-file naming review passed | All 319 Given/When/Then renderer behaviors passed, including stale-form rejection | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkStateMachineHookSpec.scala` | Whole-file naming review passed | Given/When/Then transition-hook behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala` | Whole-file naming review passed | Five grouped Given/When/Then authorization feature areas passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedMutationSpec.scala` | Whole-file naming review passed | Four Given/When/Then authoritative mutation behaviors passed | Focused 458-test matrix, `Test/compile`, and full 2380-test suite passed | EC-03B release commit |

EC-03A Modified Scala File Compliance Ledger:

| File | Naming review | Executable-spec review | Validation | Disposition |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/datastore/EntityVersionedMutation.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/datastore/DataStore.scala` | Whole-file naming debt fixed; clean re-review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicy.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/test/scala/org/goldenport/cncf/datastore/EntityVersionedMutationDataStoreSpec.scala` | Whole-file naming review passed | Five Given/When/Then behaviors with one ScalaCheck concurrency property | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationSpec.scala` | Whole-file naming review passed | Three Given/When/Then EntityStore behaviors | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |
| `src/test/scala/org/goldenport/cncf/entity/ContentBodyVersionedMutationSpec.scala` | Whole-file naming review passed | Two Given/When/Then ContentBody side-record behaviors | Focused 19-test matrix and full 2377-test suite passed | EC-03A release commit |

Clean re-review found no actionable implementation, naming, or executable-spec
finding in the EC-03A scope.

## EC-04: Atomic Datastore Capability

Stage Status:
- Current status: DONE
- Owner: CNCF datastore maintainers
- Update rule: Mark IN_PROGRESS only after EC-03 closes. Mark DONE only when
  the closed record-level plan/result and deterministic unsupported behavior
  are implemented without a load-then-save fallback.

- [x] Add a supplementary atomic conditional-mutation datastore capability.
- [x] Add bounded root guard, successor mutation, root change, and provider
  result models.
- [x] Require root and successor collections to belong to one component, and
  require every affected collection to resolve to one transaction domain.
- [x] Reject reserved-field mutation, duplicate expected fields, unsupported
  values, missing token, and empty effective root mutation.
- [x] Return authoritative root/successor records from the provider result.
- [x] Roll back successor and root changes together on every failure.
- [x] Reject unsupported providers deterministically.
- [x] Implement deterministic in-memory reference behavior.
- [x] Add property-based one-winner and rollback specifications.

Evidence:
- EC-04 implementation plan:
  - `docs/notes/entity-conflict-conditional-transition-implementation.md`,
    section `EC-04 Atomic Datastore Capability Plan`.
- The plan fixes:
  - one closed provider-level plan/result and exact-value algebra;
  - constructor and provider-side defensive validation;
  - same Entity component and same `DataStore` transaction-domain owner;
  - no CRUD fallback for unsupported providers;
  - one immutable staged-state publish in the in-memory reference provider;
  - deterministic rollback checkpoints; and
  - ScalaCheck-generated 2-to-12-caller one-winner evidence.
- EC-04 implementation:
  - `EntityConditionalTransition.scala` defines the closed exact-value,
    root/successor/correlation, plan/result, checkpoint, and supplementary
    provider capability models;
  - `DataStoreComponentOwner` carries explicit typed component ownership, so
    admission does not infer ownership from Entity ID or collection-name text;
  - root changes, successor records, and side-effect save records are
    defensively checked against one closed bounded provider value algebra;
  - `DataStoreSpace.conditionalTransition` rejects non-Entity,
    cross-component, cross-provider, and unsupported plans before provider
    invocation and has no CRUD fallback;
  - the in-memory provider verifies the authoritative root, prepares
    successor/root/side-record replacements in immutable maps, and performs
    one top-level state replacement only after all checkpoints succeed;
  - create collision, missing bind, changed bind revision, incompatible stored
    value, and provider/admission failures remain structured failures rather
    than `NotMatched`; and
  - ordinary CRUD remains serialized with the datastore-owned atomic boundary.
- EC-04 executable evidence:
  - `DataStoreConditionalTransitionSpec` covers the closed exact-value algebra,
    exact declared limits, malformed and open provider-record rejection,
    explicit component-owner and same-provider admission, unsupported
    providers, and successful `DataStoreSpace` routing;
  - `InMemoryConditionalTransitionSpec` covers create and bind success,
    mismatch, successor failure distinctions, all four rollback checkpoints,
    publish-before-visibility, and ready/start-barrier-backed generated
    2-to-12-caller races;
  - focused validation passed 22 tests across the two EC-04 specs plus
    `EntityVersionedMutationDataStoreSpec` and
    `ContentBodyVersionedMutationSpec`;
  - `sbt --batch Test/compile` passed; and
  - `git diff --check` passed.
- EC-04 REVIEW found component-owner inference, provider-record closure,
  concurrency-barrier, and constant-naming debts. REVIEW_FIX resolves all four
  debts.
- The first clean re-review then found that provider-side defensive validation
  did not independently require Entity collections for the root and successor,
  plus three local naming debts. REVIEW_FIX:
  - applies one shared Entity-collection admission to construction,
    `DataStoreSpace`, and provider boundaries;
  - proves through public construction paths that invalid root, successor,
    cross-owner, and null-route inputs do not invoke the provider;
  - removes the remaining local naming debts; and
  - confirms the model's private constructors and `copy` methods do not expose
    a public validation bypass.
- Post-fix evidence:
  - a clean rebuild and the focused four-suite matrix passed all 22 tests;
  - an independent `sbt --batch Test/compile` passed;
  - whole-file naming and executable-specification scans passed; and
  - `git diff --check` passed.
- The next clean re-review found:
  - full traversal of lazy or unbounded sequences before applying the
    1024-value limit;
  - missing exact/first-over executable evidence for record-field, sequence,
    and nesting limits; and
  - result checks that proved only the `Transitioned` subtype rather than the
    authoritative returned payloads.
- REVIEW_FIX:
  - materializes only `MAX_COLLECTION_VALUES + 1` values before deciding
    sequence admission;
  - exercises exact and first-over structural limits and proves lazy input
    evaluation stops at 1025 values;
  - proves create and bind results contain the records committed by the
    provider; and
  - passes the focused four-suite 22-test matrix and `Test/compile`.
- The separate clean RE_REVIEW_COMMIT review found no actionable findings.
- Release validation completed 341 suites with all 2397 executed tests
  successful, 0 failed, and 0 aborted. The suite retained 2 canceled,
  1 ignored, and 59 pending specifications.
- `git diff --check` passed after the release-status update.
- EC-04 is complete. EC-05 is the next Phase 49 implementation slice.
- EC-05 typed Entity/UnitOfWork/DSL work, EC-06 coherence/diagnostics, and
  EC-07 SQLite/MySQL provider evidence remain outside this slice.

## EC-05: EntityStore, UnitOfWork, and Protected DSL

Stage Status:
- Current status: DONE
- Owner: CNCF Entity and action-runtime maintainers
- Update rule: Mark IN_PROGRESS only after EC-04 closes. Mark DONE only when
  the typed operation traverses the canonical ActionCall/UnitOfWork/EntityStore
  path and no public CRUD or raw storage escape is introduced.

- [x] Add typed transition expectation, successor, mutation, request, and
  result models.
- [x] Add `EntityStore` and `EntityStoreSpace` conditional-transition
  operations.
- [x] Add one explicit `UnitOfWorkOp` and interpreter branch.
- [x] Add protected ActionCall/Behavior DSL helpers.
- [x] Add an explicitly bounded `ServiceInternal` variant without bypassing
  Entity or component authorization.
- [x] Normalize typed values into the closed datastore plan inside the
  framework.
- [x] Apply successor storage-shape and content-body policies.
- [x] Preserve normal transition-validation hooks.
- [x] Keep the operation absent from automatic REST/Form/CLI/MCP CRUD
  projection.
- [x] Preserve all structured `Consequence` failures unchanged.

Evidence:
- IMPLEMENT added `EntityConditionalTransitionModelSpec` and
  `UnitOfWorkConditionalTransitionSpec`.
- The initial focused run passes typed field admission, successor create,
  normal `NotMatched`, and bound-successor execution.
- REVIEW identified full-record root submission, stale-resident ordering,
  raw provider identity admission, lifecycle rejection, and overstated
  executable evidence.
- REVIEW_FIX adds provider-delta, empty/no-op/managed/deleted-patch,
  transition-hook rejection, bound-successor race, post-result
  reauthorization, and protected DSL/`ServiceInternal` executable evidence.
- The first RE_REVIEW_COMMIT found an executing-component owner spoofing gap
  and multi-contract executable-spec cases. The follow-up REVIEW_FIX resolves
  both canonical collections in the executing component `EntitySpace`, rejects
  foreign collections before UnitOfWork construction, and splits create,
  bind, and root-patch rejection contracts into separate cases.
- Final follow-up REVIEW_FIX validation passes 33 conditional-transition
  tests across five suites and 14 versioned-mutation regression tests across
  four suites. The additional evidence covers candidate-id collection
  mismatch, one-time admitted identity evaluation, and missing-id generation
  in the retained collection. `Test/compile`, `git diff --check`, untracked
  whitespace checks, and whole-file naming/specification scans also pass.
- The second RE_REVIEW_COMMIT found that create-successor collection
  admission and candidate-id targeting could disagree. The second follow-up
  REVIEW_FIX retains one admitted collection/id pair, rejects mismatched
  candidate-id collections, and prevents provider preparation from
  reevaluating the target identity.
- The final clean RE_REVIEW_COMMIT found no actionable behavioral,
  documentation, naming, or executable-specification finding.
- Release validation passed all 2415 executed tests across 344 suites, with
  0 failed and 0 aborted. The existing suite retained 2 canceled, 1 ignored,
  and 59 pending specifications.
- EC-05 is complete. EC-06 is the next Phase 49 implementation slice.

EC-05 Modified Scala File Compliance Ledger:

| File | Naming review | Executable-spec review | Validation | Disposition |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, 14-test regression matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityConditionalTransition.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, 14-test regression matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, 14-test regression matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala` | Whole-file naming review passed | Not a spec | Focused 33-test matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/test/scala/org/goldenport/cncf/action/ActionCallConditionalTransitionDslSpec.scala` | Whole-file naming review passed | Three grouped Given/When/Then behaviors prove protected user/internal construction and component-scope denial | Focused 33-test matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionModelSpec.scala` | Whole-file naming review passed | Two grouped Given/When/Then behaviors plus bounded ScalaCheck evidence prove model and successor-identity admission | Focused 33-test matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkConditionalTransitionSpec.scala` | Whole-file naming review passed | Thirteen grouped Given/When/Then behaviors prove authoritative outcomes, admission, races, and retained identity | Focused 33-test matrix, 14-test regression matrix, `Test/compile`, and full 2415-test suite passed | EC-05 release commit |

## EC-06: Coherence, Authorization, Audit, and Diagnostics

Stage Status:
- Current status: DONE
- Owner: CNCF Entity runtime, security, and observability maintainers
- Update rule: Mark IN_PROGRESS only after EC-05 closes. Mark DONE only when
  authoritative outcomes drive cache, View, security, audit, and diagnostic
  behavior with no payload leakage.

- [x] Authorize root read/update and successor create or bind/read as required.
- [x] Ensure `NotMatched(existing)` returns no unauthorized root data.
- [x] Change no EntitySpace/Working Set/View state before provider success.
- [x] Reconcile root and successor resident state after `Transitioned`.
- [x] Reconcile a stale local root after `NotMatched`.
- [x] Invalidate affected Views only for committed mutations.
- [x] Emit bounded audit evidence with accepted redaction.
- [x] Add ActionCall, UnitOfWork, EntityStoreSpace, and DataStore CallTree
  layers.
- [x] Add metrics for transition, mismatch, stale conflict, unsupported
  capability, authorization denial, provider failure, and transaction failure.
- [x] Classify failures from typed results and structured `Conclusion`, never
  display-message parsing.
- [x] Prove expected values and Entity payloads are absent from default
  observability.

Evidence:
- EC-06 implementation completed its first independent review and review-fix
  pass. It remains `IN_PROGRESS` until a clean re-review closes the stage.
- Independent review findings resolved:
  - raw provider failures are normalized at the real `DataStoreSpace`
    boundary without replacing the original `Conclusion`;
  - execution metadata retains the human-readable `Conclusion.display`, while
    CallTree and metrics use structured diagnostics;
  - audit records include the logical operation, trace/correlation/saga
    context, principal identity, and authoritative generated successor id;
  - the ActionCall CallTree is captured before runtime disposal;
  - transition observation support is package-internal;
  - touched private models and specs satisfy naming and executable-spec
    organization rules.
- The first clean re-review found three additional issues, now resolved in a
  separate review-fix:
  - Action CallTree is finalized before runtime disposal, while execution
    metadata, metrics, trace export, and execution history still run when
    CallTree finalization or runtime disposal fails;
  - authoritative successor identity extraction preserves the successor type
    parameter and no longer casts `EntityPersistent` through `Any`;
  - the ActionCall CallTree executable spec restores global execution-history
    state after verification.
- `ActionEngineObservabilitySeparationSpec` additionally proves runtime
  disposal failure is returned only after inline CallTree and execution
  history are retained, and that fatal error display text remains in execution
  metadata rather than leaking into CallTree.
- The second clean re-review found that a runtime disposal failure was retained
  but still projected as the preceding successful Action outcome. The third
  review-fix now promotes CallTree-finalization or runtime-disposal failure to
  the effective diagnostic outcome while preserving the original throwable
  propagation.
- `ActionEngineObservabilitySeparationSpec` proves a runtime disposal failure
  is recorded as execution metadata failure, execution-history
  `failure`/`Conclusion`, and a dashboard Action error.
- `ActionCallConditionalTransitionDslSpec` executes the real `ActionEngine`
  path and proves ActionCall, UnitOfWork, EntityStoreSpace, and DataStore
  CallTree layers contain no Entity payload.
- `EntityConditionalTransitionCoherenceSpec` proves:
  - authoritative root/successor resident reconciliation after `Transitioned`;
  - stale root refresh without View invalidation after authorized
    `NotMatched`;
  - no datastore, EntitySpace, successor, or View mutation before provider
    success;
  - post-result authorization denial exposes no root, evicts stale resident
    state, and leaves non-mutating View state intact;
  - UnitOfWork, EntityStoreSpace, and DataStore CallTree layers contain no
    Entity payload;
  - actual provider and transaction failures crossing `DataStoreSpace` and
    UnitOfWork receive stable structured outcome classification.
- `EntityConditionalTransitionDiagnosticsSpec` proves:
  - typed success/mismatch and structured `Conclusion` failure
    classification;
  - transition, mismatch, conflict, authorization, unsupported capability,
    provider, and transaction metric outcomes;
  - bounded audit identity/revision evidence;
  - absence of Entity payload, expected values, and display messages from
    transition audit and CallTree failure attributes.
- `UnitOfWorkConditionalTransitionSpec` proves root relation-rule admission,
  bound-successor read authorization, bound revision conflict, and
  post-result root reauthorization, including authoritative generated
  successor identity in observation context.
- Focused review-fix validation:
  - `sbt --batch "testOnly
    org.goldenport.cncf.action.ActionCallConditionalTransitionDslSpec
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionSpec
    org.goldenport.cncf.datastore.InMemoryConditionalTransitionSpec
    org.goldenport.cncf.entity.EntityConditionalTransitionCoherenceSpec
    org.goldenport.cncf.entity.EntityConditionalTransitionDiagnosticsSpec
    org.goldenport.cncf.unitofwork.UnitOfWorkConditionalTransitionSpec"`:
    42 tests passed across six suites;
  - `sbt --batch Test/compile`: passed;
  - `git diff --check`: passed.
- Second review-fix validation:
  - `sbt --batch "testOnly
    org.goldenport.cncf.action.ActionEngineObservabilitySeparationSpec
    org.goldenport.cncf.action.ActionCallConditionalTransitionDslSpec
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionSpec
    org.goldenport.cncf.datastore.InMemoryConditionalTransitionSpec
    org.goldenport.cncf.entity.EntityConditionalTransitionCoherenceSpec
    org.goldenport.cncf.entity.EntityConditionalTransitionDiagnosticsSpec
    org.goldenport.cncf.unitofwork.UnitOfWorkConditionalTransitionSpec"`:
    47 tests passed across seven suites;
  - `sbt --batch Test/compile`: passed;
  - touched-file naming and raw-assert scan: passed;
  - `git diff --check`: passed.
- Third review-fix validation:
  - the seven-suite EC-06 focused matrix passed all 47 tests;
  - `sbt --batch Test/compile`: passed;
  - touched-file naming and raw-assert scan: passed;
  - `git diff --check`: passed.
- The following clean re-review found no actionable implementation, naming,
  or executable-specification issue, but release validation exposed one stale
  cross-suite observability assertion:
  `ComponentLogicOperationDefinitionSemanticsSpec` still required
  `Conclusion.display` in CallTree output although EC-06 deliberately projects
  only structured diagnostics there.
- The fourth review-fix replaces that stale expectation with
  `diagnostic_key`, taxonomy category/symptom, and explicit display-message
  redaction assertions. It also removes the touched spec's historical
  underscore-prefixed helper-type naming debt.
- Fourth review-fix validation:
  - `ComponentLogicOperationDefinitionSemanticsSpec`: 12 tests passed;
  - the EC-06 conditional-transition matrix: 49 tests passed across eight
    suites;
  - `sbt --batch Test/compile`: passed;
  - touched-spec naming and raw-assert scan: passed;
  - `git diff --check`: passed.
- The final clean re-review found no actionable implementation,
  documentation, naming, or executable-specification finding.
- Release validation passed all 2427 executed tests across 346 suites, with
  0 failed and 0 aborted. The existing suite retained 2 canceled, 1 ignored,
  and 59 pending specifications.
- EC-06 is complete. EC-07 is the next Phase 49 implementation slice.

EC-06 Modified Scala File Compliance Ledger:

| File | Naming review | Executable-spec review | Validation | Disposition |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/action/ActionEngine.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/datastore/EntityConditionalTransition.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/http/RuntimeDashboardMetrics.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/metrics/RuntimeMetrics.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/observability/CallTreeValueSummary.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/observability/ConclusionDiagnostics.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/observability/EntityConditionalTransitionObservation.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | Whole-file naming review passed | Not a spec | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/action/ActionCallConditionalTransitionDslSpec.scala` | Whole-file naming review passed | Four grouped Given/When/Then DSL, ownership, and real CallTree behaviors | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/action/ActionEngineObservabilitySeparationSpec.scala` | Whole-file naming review passed | Five Given/When/Then authorization, success, fatal-error, disposal-failure, and legacy callback behaviors | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala` | Historical helper-type naming debt removed; whole-file naming review passed | Twelve Given/When/Then operation-definition behaviors, including structured failed-CallTree diagnostics and display redaction | Focused 12-test regression, EC-06 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionCoherenceSpec.scala` | Whole-file naming review passed | Five Given/When/Then coherence, authorization, and provider-boundary behaviors | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionDiagnosticsSpec.scala` | Whole-file naming review passed | Four Given/When/Then diagnostic and redaction behaviors | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkConditionalTransitionSpec.scala` | Whole-file naming review passed | Grouped transition matrix plus relation-aware Bind authorization | Focused 49-test matrix, `Test/compile`, and full 2427-test suite passed | EC-06 release commit |

## EC-07: Provider and Concurrency Evidence

Stage Status:
- Current status: DONE
- Owner: CNCF datastore/provider maintainers
- Update rule: Mark IN_PROGRESS only after EC-06 closes. Mark DONE only when
  in-memory, SQLite, and the selected shared profile pass the same semantic
  matrix with independent callers.

- [x] Run bounded-caller property evidence against in-memory reference
  behavior.
- [x] Implement SQLite conditional transition with one explicit native
  transaction.
- [x] Exercise SQLite with independent callers and independent connections.
- [x] Prove SQLite restart visibility.
- [x] Inject failure after guard match, during successor work, during root
  update, and during commit.
- [x] Verify every injected failure leaves no orphan successor or partial root.
- [x] Implement or activate the selected shared-datastore profile.
- [x] Exercise the shared profile with independently executing callers.
- [x] Prove exactly one winner and provider-neutral result parity.
- [x] Preserve claim-or-load behavior as a regression boundary.

Evidence:
- EC-07A implements `EntityConditionalTransitionDataStore` in `SqlDataStore`.
  Each transition uses one connection and one explicit native transaction for
  guard admission, create/bind successor work, root revision/update, side
  records, authoritative reload, and commit.
- `SqliteConditionalTransitionSpec` passes create and bind transitions,
  authoritative `NotMatched`, create collision, missing/stale bind, restart
  visibility, all four pre-commit checkpoints, and deterministic commit
  rejection.
- Its ScalaCheck evidence executes generated caller counts from two through
  twelve. Every caller constructs an independent SQLite provider and obtains
  an independent connection against one physical database; exactly one
  transition wins, all admitted losers return `NotMatched`, and only the
  winning successor and side record exist.
- The same one-winner result passes through both `SqlDataStore.sqlite` and a
  SQLite URL resolved by the generic `SqlDataStore.jdbc` factory.
- Review-fix keeps the shared SQL capability safe before EC-07B acceptance:
  MySQL schema preparation runs before the domain transaction so MySQL DDL
  cannot implicitly commit a partially applied transition, and root admission
  uses a locking `FOR UPDATE` read inside the transaction.
- The focused EC-07A review-fix matrix passed 51 tests across
  `SqliteConditionalTransitionSpec`,
  `InMemoryConditionalTransitionSpec`,
  `DataStoreConditionalTransitionSpec`, `SqliteDataStoreSpec`, and
  `ActionCallEntityAccessMetricsSpec`.
- Release validation passed all 2434 executed CNCF tests across 347 suites.
- EC-07B adds an opt-in live shared-provider specification using
  Testcontainers 2.0.5 and pinned `mysql:8.4`. Normal test runs cancel the
  five live behaviors before Docker unless `CNCF_LIVE_MYSQL_TEST=true`.
- The live MySQL matrix uses one physical database and independent
  `SqlDataStore` instances/connections. It proves provider-neutral JDBC
  create/bind/mismatch behavior, successor collision/missing/stale isolation,
  rollback after all four pre-commit checkpoints, deterministic commit
  rejection, restart visibility, and generated two-to-twelve-caller
  one-winner behavior.
- Concurrent first-use schema preparation recovers only when another caller
  installed the requested column after the failed add attempt. Other schema
  failures preserve the original structured provider failure.
- Live validation passed all five MySQL behaviors. Combined provider parity
  passed all 19 behaviors across `InMemoryConditionalTransitionSpec`,
  `SqliteConditionalTransitionSpec`, and
  `MysqlConditionalTransitionAcceptanceSpec`.
- The final clean `Test/compile` passed. A normal non-live execution canceled
  all five MySQL behaviors without contacting Docker.
- EC-07B read-only review found that successor failures asserted only the
  presence of a `Consequence.Failure`, and that a failed post-DDL metadata
  check could replace the original add-column failure.
- Review-fix now asserts the portable conflict/not-found symptom, stable
  conflict reason facets, and byte-for-byte root/successor/side preservation
  for all three successor failure modes. Add-column race recovery succeeds
  only after positively observing the requested column; every other path
  preserves the original DDL conclusion.
- Review-fix validation passed all five live MySQL behaviors and all 19
  combined in-memory/SQLite/MySQL provider behaviors. `Test/compile` passed
  before the live acceptance rerun.
- Clean read-only re-review found no remaining implementation, naming,
  executable-specification, dependency, or documentation finding.
- Release validation passed all 2434 executed CNCF tests across 348 suites,
  with the five opt-in MySQL behaviors canceled before Docker in the normal
  suite.
- EC-07 is DONE. EC-08 CBD Support acceptance is the next Phase 49 slice.

EC-07A Modified Scala File Compliance Ledger:

| File | Naming | Executable specification | Validation | Scope |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala` | Whole-file private/protected naming review passed | Covered by the SQLite provider matrix and existing datastore regressions | Focused 51-test matrix, `Test/compile`, and full 2434-test suite passed | EC-07A release commit |
| `src/test/scala/org/goldenport/cncf/datastore/SqliteConditionalTransitionSpec.scala` | Whole-file naming review passed | Seven Given/When/Then behaviors, including generated bounded concurrency and the generic JDBC factory | Focused 51-test matrix, `Test/compile`, and full 2434-test suite passed | EC-07A release commit |

EC-07B Modified Scala File Compliance Ledger:

| File | Naming | Executable specification | Validation | Scope |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala` | Whole-file private/protected naming review passed | Covered by live MySQL and combined provider acceptance, including concurrent schema preparation | Five live MySQL behaviors, combined 19-behavior provider matrix, `Test/compile`, and full 2434-test suite passed after review-fix | EC-07B release commit |
| `src/test/scala/org/goldenport/cncf/datastore/MysqlConditionalTransitionAcceptanceSpec.scala` | Whole-file naming review passed | Five Given/When/Then behaviors, including structured successor diagnostics, complete state isolation, and generated bounded independent-caller concurrency | Five live MySQL behaviors, combined 19-behavior provider matrix, normal opt-in cancellation, `Test/compile`, and full 2434-test suite passed | EC-07B release commit |

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
