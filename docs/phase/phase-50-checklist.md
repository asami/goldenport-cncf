# Phase 50 Checklist - SimpleEntity Revision and OCC Simplification

status=active
phase=[Phase 50 - SimpleEntity Revision and OCC Simplification](phase-50.md)

This checklist is the authoritative Phase 50 state ledger after Phase 50
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 49 closes.

## SE-01: Contract Decisions and Executable Acceptance

Stage Status:
- Current status: DONE
- Owner: SimpleEntity, CNCF Entity, and datastore maintainers
- Entry rule: Phase 49 is closed.
- Completion rule: Every unresolved contract choice is explicit and represented
  by an exact Executable Specification expectation before implementation.

- [x] Confirm `EntityRevision` ownership in `simplemodeling-model`.
- [x] Inventory existing `simplemodeling-lib` generic datatype/schema/decoder
  support and identify whether any independently reusable primitive is
  actually missing.
- [x] Fix the canonical initial revision as `1`.
- [x] Fix successful mutation and no-op mutation advancement semantics using
  `AlwaysWrite` and `WriteIfChanged`.
- [x] Fix authoritative `WriteIfChanged` ordering so concurrent identical
  managed mutations produce at most one write and later no-op successes.
- [x] Fix the concurrency/precondition combination matrix and reject
  `None + ObservedRequired`.
- [x] Fix write/precondition declaration surfaces and precedence from route,
  operation, adapter profile, and framework fallback.
- [x] Fix revision exhaustion as a structured no-mutation failure at
  `Long.MaxValue`.
- [x] Fix Entity-level and collection-level concurrency-policy declaration and
  precedence: explicit collection override, then Entity declaration, then the
  `Optimistic` default.
- [x] Fix the deterministic ordinary concurrency-policy default as
  `Optimistic`, with CNCF-managed revision propagation.
- [x] Fix missing-revision schema/data behavior as deterministic admission
  failure until explicit migration or recreation.
- [x] Fix revision visibility for read/search/View/Aggregate projections as
  read-only managed metadata on mutation-capable standard surfaces.
- [x] Fix REST/Form/Web/generated-client revision transport as framework
  metadata rather than a business operation parameter.
- [x] Fix the representation matrix: `SimpleEntity` uses embedded revision;
  only an explicitly admitted non-`SimpleEntity` model may use detached
  revision.
- [x] Fix where Entity/collection revision representation is declared and how
  conflicting declarations fail: generated Entity metadata and
  `EntityRuntimeDescriptor.revisionRepresentation` bind at assembly, and
  conflicts fail without precedence fallback.
- [x] Fix the detached carrier API as `EntityRevisionCarrier[A]` and its
  physical managed field as `cncf_revision`.
- [x] Inventory Phase 49 implementation assets and classify each as common
  kernel reuse, detached-extension refactoring, or provisional token removal.
- [x] Prohibit dual representation, mirroring, per-request selection, and
  implicit embedded/detached fallback.
- [x] Add failing-first Executable Specification identities for all Phase 50
  acceptance groups.

Evidence:
- Human decision `P50-SE01-SEMANTICS-01` was resolved on 2026-07-25 with the
  framework-managed revision, `AlwaysWrite` core default, `WriteIfChanged`
  route policy, and `ObservedRequired` strict transport direction.
- The accepted decision is recorded in:
  - `docs/journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md`;
  - `docs/notes/simpleentity-revision-occ-simplification-proposal.md`; and
  - `docs/phase/phase-50.md`.
- The generic-core inventory found no Phase 50-specific gap:
  `ValueReader[Long]`, positive-integer datatype/schema support, and structured
  `Consequence`/`Conclusion` facilities are sufficient. SE-02 must not modify
  `simplemodeling-lib` unless a failing Executable Specification proves an
  independently reusable missing primitive.
- The Phase 49 asset disposition table in the implementation proposal fixes
  common-kernel reuse, detached-extension refactoring, and provisional-token
  removal explicitly.
- SE-01 fixes transport ownership and separation from business parameters.
  Exact hidden-field and HTTP validator encoding belongs to the executable
  adapter contract in SE-07.
- Review findings `P50-SE01-R1-01` through `P50-SE01-R1-06` were resolved by
  defining separate `None`/`Optimistic` provider paths, authoritative
  concurrent no-op detection, the complete policy matrix and declaration
  precedence, revision exhaustion, and repository-qualified Executable
  Specification identities.
- Independent review identified findings `P50-SE01-R1-01` through
  `P50-SE01-R1-06`; all were fixed and the clean re-review found no remaining
  actionable finding.
- Documentation-only validation passed with `git diff --check`. The commit
  containing this checklist update is the SE-01 release evidence.

### Executable Specification Identities

Paths are repository-relative to the repository named in the second column.

| ID | Repository | Executable Specification path | Required behavior |
| --- | --- | --- | --- |
| ER-01 | `simplemodeling-model` | `src/test/scala/org/simplemodeling/model/datatype/EntityRevisionSpec.scala` | Accept revisions from `1` through `Long.MaxValue`, fix initial revision `1`, reject invalid values structurally, and fail advancement at the upper bound without overflow. |
| ER-02 | `simplemodeling-model` | `src/test/scala/org/simplemodeling/model/SimpleEntityRevisionSpec.scala` | Expose exactly one framework-managed embedded revision while preserving existing lifecycle attributes. |
| ER-03 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityRevisionRepresentationSpec.scala` | Admit embedded `SimpleEntity` and explicit detached non-`SimpleEntity` representations, and reject every conflicting, dual, mirrored, implicit, or per-request selection. |
| ER-04 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyPolicySpec.scala` | Resolve collection override, Entity declaration, and `Optimistic` default deterministically; keep `None` explicit. |
| ER-05 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityWritePolicySpec.scala` | Prove `AlwaysWrite` advancement, authoritative `WriteIfChanged` equality, concurrent identical no-op convergence, unchanged no-op metadata, and stale observed-precondition ordering. |
| ER-06 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityRevisionPreconditionSpec.scala` | Prove the valid policy matrix, reject `None + ObservedRequired`, resolve route/operation/adapter defaults, keep revision out of business parameters, and preserve one mutation attempt's base revision. |
| ER-07 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityManagedMutationSpec.scala` | Initialize, compare, advance, return, reject managed input, reject revision exhaustion without mutation, and preserve revision across failure, rollback, soft delete, and restore. |
| ER-08 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityRevisionMigrationSpec.scala` | Reject missing schema/record revision deterministically until explicit migration or recreation. |
| ER-09 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/projection/EntityRevisionProjectionSpec.scala` | Expose read-only revision on admitted Entity, search, View, and Aggregate surfaces without making it writable. |
| ER-10 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/http/StaticFormEntityRevisionSpec.scala` | Use `WriteIfChanged + ObservedRequired`, retain observed revision as hidden framework metadata, and preserve strict stale-edit conflict without domain parameters. |
| ER-11 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/http/RestEntityRevisionSpec.scala` | Apply `WriteIfChanged + Managed` to idempotent routes, select `ObservedRequired` for strict validators, and leave general REST request replay and Web Form submission-token semantics to strategy item 9.43. |
| ER-12 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/datastore/EntityRevisionProviderParitySpec.scala` | Prove equivalent atomic, concurrent no-op, rollback, restart, exhaustion, and admission results for in-memory, SQLite, and the selected shared provider. |
| ER-13 | `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionRevisionSpec.scala` | Require authoritative expected revision under every ordinary policy and retain exactly-one-winner behavior for both revision representations. |

## SE-02: SimpleEntity Revision Model

Stage Status:
- Current status: DONE
- Owner: `simplemodeling-model` maintainers, with `simplemodeling-lib`
  maintainers only for proven generic gaps
- Entry rule: SE-01 is DONE.
- Completion rule: The model surface contains one validated managed revision
  attribute and existing lifecycle timestamp semantics remain unchanged.

- [x] Add `EntityRevision` under the appropriate
  `org.simplemodeling.model` datatype boundary.
- [x] Add `revision` as the only new standard `SimpleEntity` attribute.
- [x] Preserve `createdAt` and `updatedAt` without adding an OCC timestamp.
- [x] Define read-only/framework-managed metadata classification.
- [x] Reuse existing `simplemodeling-lib` generic datatype, schema,
  `ValueReader`, `Consequence`, and record facilities.
- [x] Add a `simplemodeling-lib` primitive only when it is independently
  reusable and cannot be expressed correctly with existing core APIs.
- [x] Keep `simplemodeling-lib` independent of `SimpleEntity`, CNCF, and OCC.
- [x] Add property-based datatype and model-shape specifications.
- [x] Publish the required `simplemodeling-lib` snapshot first when changed.
  No core change was required, so publication was not applicable.
- [x] Publish the `simplemodeling-model` snapshot for downstream integration.
- [x] Consume the updated model from generated Entities and CNCF.

Evidence:
- `simplemodeling-model/src/main/scala/org/simplemodeling/model/datatype/EntityRevision.scala`
- `simplemodeling-model/src/main/scala/org/simplemodeling/model/SimpleEntity.scala`
- `simplemodeling-model/src/test/scala/org/simplemodeling/model/datatype/EntityRevisionSpec.scala`
- `simplemodeling-model/src/test/scala/org/simplemodeling/model/SimpleEntityRevisionSpec.scala`
- Focused model specifications: 10 passed.
- Full `simplemodeling-model` suite: 56 passed, with 27 pre-existing pending
  specifications.
- `simplemodeling-model` commit: `fc6d618` (`Add SimpleEntity revision model`).
- Local development artifact:
  `org.simplemodeling:simplemodeling-model_3:0.2.0-SNAPSHOT`.
- `simple-modeler` centralizes generated `SimpleEntity` output normalization,
  projects one system/read-only `EntityRevision` after `id`, and excludes the
  managed field from Create, Update, and Query inputs only when the generated
  input implements the corresponding `SimpleEntity` input contract. An
  ordinary non-`SimpleEntity` business attribute named `revision` remains
  present on Create, Update, and Query inputs.
- `SimpleEntityRevisionGenerationSpec`: 2 passed.
- `EntityUpdateOperationContractProjectionSpec`: 1 passed.
- Full `simple-modeler` suite: 40 passed.
- Local generator artifact:
  `org.simplemodeling:simplemodeler_2.12:1.1.24-SNAPSHOT`.
- Cozy defaults generated model dependencies to
  `org.simplemodeling:simplemodeling-model_3:0.2.0-SNAPSHOT`.
- Cozy aggregate Create, Save, and Update operations consume generated
  application input models instead of output Aggregate models. Their request
  schemas therefore omit managed `revision`; aggregate Update resolves the
  required Entity id from the request before loading and mutating the
  Aggregate.
- `ModelerSimpleEntityRevisionGenerationSpec`: 2 passed and verifies generated
  operation metadata and decoders do not accept managed `revision`.
- `ModelerEntityVersionedMutationGenerationSpec`: 1 passed and preserves the
  provisional Phase 49 `cncfRevision` path for later kernel migration.
- `ModelerScalaGenerationSpec`: 27 passed and verifies the generated Aggregate
  Update action uses the request-resolved Entity id.
- Full Cozy suite: 662 passed, 2 canceled.
- Local Cozy artifact: `org.simplemodeling:cozy_2.12:0.3.0-SNAPSHOT`.
- `GeneratedInformationRevisionSpec`: 1 passed from a cold CNCF build and
  verifies embedded revision on seven generated Information output variants
  with no revision member on Create, Update, or Query input variants.
- Full CNCF suite: 2437 passed, 7 canceled, 1 ignored, and 59 pending
  against `simplemodeling-model_3:0.2.0-SNAPSHOT`.

### SE-02B Modified Scala File Compliance Ledger

All paths are repository-relative. Dependency repository rows identify their
validated commits; the CNCF row is part of this SE-02B release commit.

| Repository | Scala file | Naming | Spec style | Validation | Commit |
| --- | --- | --- | --- | --- | --- |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/Scala3ClassGeneratorBase.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueAggregateScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueCreateScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueOperationScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueProjectionScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueQueryScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueReadScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/EntityValueUpdateScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/SimpleEntityScalaModelSupport.scala` | whole-file scan passed | not a spec | focused and full generator suites passed | `57fcbc8` |
| `simple-modeler` | `src/test/scala/org/simplemodeling/SimpleModeler/transformers/scala/SimpleEntityRevisionGenerationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 2 focused specifications passed | `57fcbc8` |
| `cozy` | `src/main/scala/cozy/modeler/Modeler.scala` | whole-file scan passed | not a spec | focused Aggregate generation and full Cozy suites passed | `59afa95` |
| `cozy` | `src/test/scala/cozy/modeler/ModelerScalaGenerationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 27 focused specifications and full Cozy suite passed | `59afa95` |
| `cozy` | `src/test/scala/cozy/modeler/ModelerSimpleEntityRevisionGenerationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 2 focused specifications and full Cozy suite passed | `59afa95` |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/information/GeneratedInformationRevisionSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 1 cold-build focused specification and full CNCF suite passed | this SE-02B release commit |

Review-fix evidence:
- Generated operation request schemas no longer require embedded managed
  `revision`.
- Revision filtering is bound to generated `SimpleEntityCreate`,
  `SimpleEntityUpdate`, and `SimpleEntityQuery` inheritance rather than the
  attribute name alone.
- The non-`SimpleEntity` regression specification proves an application-owned
  `revision` field is preserved.
- Whole-file naming cleanup covers all modified generator files, including
  method-local helpers, local helper parameters, and private internal model
  fields.

## SE-03: Common Revision Kernel and Binding

Stage Status:
- Current status: DONE
- Owner: CNCF EntityStore, UnitOfWork, and datastore maintainers
- Entry rule: SE-02 is DONE.
- Completion rule: Embedded and detached paths use one `EntityRevision`,
  one atomic provider kernel, and one deterministic representation binding.

- [x] Replace `EntityConcurrencyToken` and `EntityMutationExpectation` with
  common `EntityRevision` throughout EntityStore, UnitOfWork, protected DSL,
  Conditional Transition, diagnostics, and callers.
- [x] Retain and generalize the Phase 49 revision-field-independent atomic
  compare-and-advance provider operation.
- [x] Define Entity/collection revision representation metadata.
- [x] Resolve exactly one optional revision binding before Entity collection
  registration. An undeclared non-`SimpleEntity` remains outside revision
  management.
- [x] Reject `SimpleEntity + Detached`, non-`SimpleEntity + implicit
  Detached`, dual fields, and conflicting declarations.
- [x] Keep authorization, UnitOfWork, transaction, audit, diagnostics, and
  observability common across representations.
- [x] Add representation-selection and common-kernel Executable
  Specifications.

Evidence:
- `SE-03A Revision Representation Contract Propagation` is implemented.
  Independent review findings were fixed, and the clean re-review found no
  remaining actionable finding.
- SimpleModeler now carries `revisionModelKind` and
  `revisionRepresentation` through
  `MComponent.EntityRuntimeDescriptor`, the Scala component model, and
  generated CNCF `EntityRuntimeDescriptor` construction.
- Cozy emits explicit `SimpleEntity` or `NonSimpleEntity` model-kind evidence
  for generated Entities and declares `Embedded` only when the source Entity
  inherits `SimpleEntity`; CNCF does not infer this from schema fields, class
  names, representation absence, or persisted data.
- CNCF defines `EntityRevisionRepresentation` and the immutable
  `EntityRevisionBinding` resolver. Generated model-kind evidence is required
  before binding, so a missing model declaration cannot admit Detached.
  The contract validator rejects conflicts, implicit detached fallback, missing
  persisted revision, wrong physical fields, and mirrored/dual fields when
  invoked. SE-03B now enforces the declaration contract during registration.
- `EntityRuntimeDescriptor` and component descriptor decoding expose the
  collection declaration without inventing a default or accepting an unknown
  value.
- Focused SimpleModeler Executable Specifications passed: 4 tests in 2 suites.
- Focused Cozy SimpleEntity generation Executable Specifications passed:
  2 tests in 1 suite.
- Focused CNCF representation and component-descriptor Executable
  Specifications passed: 21 tests in 2 suites.
- Full SimpleModeler suite passed.
- Full Cozy suite passed: 662 tests, 2 canceled.
- Full CNCF suite passed with the development Cozy launcher selected
  explicitly: 2442 tests, 7 canceled, 1 ignored, and 59 pending.
- `SE-03B Registration-time Revision Representation Binding` is implemented
  and passed independent review, review-fix, clean re-review, and full release
  validation. Its first release-validation run found four
  stale reflective fixtures in three existing ComponentFactory
  specifications; review-fix migrated them to `bootstrapC`.
- `ComponentFactory.bootstrapC` preflights every Entity declaration before
  registering any `EntityCollection`. A failure in a later declaration leaves
  the component EntitySpace empty, does not mutate component descriptor
  metadata, and does not set `collectionsBootstrapped`.
- Runtime assembly descriptors retain component ownership through binding;
  same-named Entities in different components cannot consume or conflict with
  each other's revision declarations. Bundle descriptors owned through
  `componentlets` use the same ownership semantics and remain visible to the
  generated componentlet.
- Generated `SimpleEntity + Embedded` installs
  `EntityDescriptor.revisionBinding = Some(Embedded)`.
- A proven non-`SimpleEntity` installs `Some(Detached)` only through an
  explicit detached declaration. A non-`SimpleEntity` without a revision
  declaration installs `None`.
- Missing model-kind evidence, conflicting model or collection declarations,
  incomplete `SimpleEntity` model metadata, and `SimpleEntity + Detached` fail
  with structured configuration failure. A complete duplicate declaration
  cannot mask missing generated representation metadata.
- Focused SE-03B and representation specifications passed: 15 tests in 2
  suites.
- ComponentFactory generated-schema and runtime-plan regression
  specifications passed together with SE-03B: 25 tests in 4 suites.
- Review-fix validation passed all affected bootstrap, SE-03B,
  generated-schema, and runtime-plan specifications: 29 tests in 7 suites,
  followed by successful `Test/compile`.
- Clean re-review reran the same 7 focused suites and passed all 29 tests.
- Isolated staged-slice full release validation passed 2450 tests in 350
  suites, with 7 canceled, 1 ignored, and 59 pending.
- SE-03D replaces the remaining Phase 49 upper concurrency API with common
  `EntityRevision` directly. No compatibility alias, adapter, or duplicate
  expectation wrapper remains.
- `SE-03C Provider Revision Kernel Typing` is implemented and passed
  independent read-only review and clean re-review.
  `EntityVersionedMutationPlan`, `DataStoreConditionalRoot`, and
  `DataStoreConditionalSuccessor.Bind` carry `EntityRevision`; in-memory and
  SQL providers persist only `EntityRevision.value`.
- The common provider decoder rejects physically absent revision with a
  structured admission failure. The former `DataStoreRevisionState` and
  virtual-zero progression path are removed.
- Existing authorization, UnitOfWork, transaction, rollback, diagnostics,
  exactly-one-winner, and no-orphan execution routes remain in use; this slice
  adds no compatibility adapter and does not start concurrency-policy or
  detached-carrier work.
- Focused SE-03C provider, EntityStore, and UnitOfWork specifications passed:
  57 tests in 9 suites, with 5 opt-in MySQL tests canceled.
- Clean re-review expanded the focused boundary and passed 59 tests in 10
  suites, with the same 5 opt-in MySQL tests canceled.
- Isolated staged-slice full release validation passed 2450 tests in 350
  suites, with 7 canceled, 1 ignored, and 59 pending.
- `COZY_RUNTIME_DEV_DIR=/Users/asami/src/dev2025/cozy sbt --batch Test/compile`
  passed. The development runtime is required because the published Cozy
  0.2.25 generator predates the already-completed SimpleEntity revision
  generation contract.
- `SE-03D Upper Concurrency API Replacement` is implemented and passed
  independent read-only review, review-fix, and clean re-review.
  `EntitySnapshot` and
  `EntityRecordSnapshot` now carry `revision: EntityRevision`, while
  EntityStore, UnitOfWork operations, ActionCall helpers, admin/job callers,
  Conditional Transition, and observability consume the revision directly.
- The obsolete Java `EntityConcurrencyToken` and Scala
  `EntityMutationExpectation` types are deleted. Source and test searches find
  no remaining reference to either compatibility vocabulary.
- `EntityRevisionKernelSpec` replaces the former token-named specification and
  verifies positive construction, exact advancement, upper-bound structured
  failure, physical storage admission, metadata isolation, and persistence
  behavior against the model-owned datatype.
- Focused SE-03D EntityStore, ActionCall, UnitOfWork, Conditional Transition,
  authorization, and diagnostics specifications passed: 144 tests in 14
  suites.
- The affected Static Form stale-version specification passed separately:
  1 test in 1 suite.
- `COZY_RUNTIME_DEV_DIR=/Users/asami/src/dev2025/cozy sbt --batch Test/compile`
  passed after the upper API replacement.
- Independent read-only review found eight private-helper parameter naming
  violations across five modified source files, an overstated compliance
  ledger, and two SE-06 checklist items already completed by SE-03D.
- Review-fix changed only private-helper names, retained the canonical
  public/protected `expectedRevision` API, and passed all 144 tests in the 14
  focused suites followed by `Test/compile`.
- Clean re-review found no remaining actionable finding. It reran all 14
  focused suites and passed 144 tests, then reran the affected Static Form
  stale-update specification and passed 1 test.
- Full release validation passed 2452 tests in 351 suites, with 7 canceled,
  1 ignored, and 59 pending. This SE-03D release checkpoint completes SE-03.

### SE-03D Modified Scala File Compliance Ledger

All paths are repository-relative; every listed file was checked as a whole
file during clean re-review.

| Scala file | Naming | Spec style | Validation | Review |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | private-parameter finding fixed; whole-file scan passed | not a spec | focused suites and `Test/compile` passed | review-fix and clean re-review passed |
| `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala` | private-parameter finding fixed; whole-file scan passed | not a spec | focused suites and `Test/compile` passed | review-fix and clean re-review passed |
| `src/main/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobControlComponent.scala` | private-parameter finding fixed; whole-file scan passed | not a spec | focused suites and `Test/compile` passed | review-fix and clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityConditionalTransition.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | private-parameter finding fixed; whole-file scan passed | not a spec | focused suites and `Test/compile` passed | review-fix and clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/runtime/Collection.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/job/JobEngine.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/observability/EntityConditionalTransitionObservation.scala` | private-parameter finding fixed; whole-file scan passed | not a spec | focused suites and `Test/compile` passed | review-fix and clean re-review passed |
| `src/main/scala/org/goldenport/cncf/tag/TagModel.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala` | whole-file scan passed | not a spec | focused suites and `Test/compile` passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/action/ActionCallAggregateResolveSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/action/ActionCallConditionalTransitionDslSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/action/ActionCallEntityAccessMetricsSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/ContentBodyVersionedMutationSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionCoherenceSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionDiagnosticsSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityConditionalTransitionModelSpec.scala` | whole-file scan passed | Given/When/Then grouping, property checks, and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityRevisionKernelSpec.scala` | whole-file scan passed | Given/When/Then grouping, property checks, and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityStoreQueryRouteSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/http/StaticFormAppRendererSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused stale-version specification and `Test/compile` passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkConditionalTransitionSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkStateMachineHookSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedMutationSpec.scala` | whole-file scan passed | Given/When/Then grouping and matcher vocabulary passed | focused suite passed | clean re-review passed |



This implementation evidence passed read-only review, review-fix, clean
re-review, and isolated staged-slice full release validation.

| Repository | Scala file | Naming | Spec style | Validation | Review |
| --- | --- | --- | --- | --- | --- |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala` | whole-file scan passed; existing local naming debt removed | not a spec | focused, regression, and full suites passed | clean re-review passed |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/entity/EntityRevisionRepresentation.scala` | whole-file scan passed | not a spec | focused and full suites passed | clean re-review passed |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/entity/runtime/EntityDescriptor.scala` | whole-file scan passed | not a spec | focused, regression, and full suites passed | clean re-review passed |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/component/ComponentFactoryRevisionBindingSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 9 focused specifications and full suite passed | clean re-review passed |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityRevisionRepresentationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 6 focused specifications and full suite passed | clean re-review passed |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/component/ComponentFactoryStateMachineBootstrapSpec.scala` | whole-file scan passed; fixture type naming corrected | Given/When/Then structure and matcher vocabulary passed | affected, regression, and full suites passed | clean re-review passed |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/component/ComponentFactoryWorkingSetPolicySpec.scala` | whole-file scan passed; private parameter naming corrected | Given/When/Then structure and matcher vocabulary passed | affected, regression, and full suites passed | clean re-review passed |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/component/ComponentFactoryLegacyPlanConsistencySpec.scala` | whole-file scan passed; fixture type naming corrected | Given/When/Then structure and matcher vocabulary passed | affected, regression, and full suites passed | clean re-review passed |

### SE-03A Modified Scala File Compliance Ledger

All paths are repository-relative. Every modified Scala source was checked as
a whole file rather than only on changed lines.

| Repository | Scala file | Naming | Spec style | Validation | Commit |
| --- | --- | --- | --- | --- | --- |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/ComponentPart.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/generator/scala/model/ScalaModel.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `simple-modeler` | `src/main/scala/org/simplemodeling/SimpleModeler/transformers/scala/ComponentScalaModelTransformer.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `simple-modeler` | `src/main/scala/org/simplemodeling/model/MComponent.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `simple-modeler` | `src/test/scala/org/simplemodeling/SimpleModeler/generator/scala/ComponentRevisionRepresentationGenerationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 4 focused tests and full suite passed | this SE-03A release commit |
| `cozy` | `src/main/scala/cozy/modeler/Modeler.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `cozy` | `src/test/scala/cozy/modeler/ModelerSimpleEntityRevisionGenerationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 2 focused tests and full suite passed | this SE-03A release commit |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/entity/EntityRevisionRepresentation.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `cloud-native-component-framework` | `src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimeDescriptor.scala` | whole-file scan passed | not a spec | focused and full suites passed | this SE-03A release commit |
| `cloud-native-component-framework` | `src/test/scala/org/goldenport/cncf/entity/EntityRevisionRepresentationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 5 focused tests and full suite passed | this SE-03A release commit |

### SE-03C Modified Scala File Compliance Ledger

This implementation evidence passed independent read-only review and clean
re-review. All paths are repository-relative.

| Scala file | Naming | Spec style | Validation | Review |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/datastore/DataStore.scala` | whole-file scan passed | not a spec | focused suite and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/datastore/EntityConditionalTransition.scala` | whole-file scan passed | not a spec | focused suite and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/datastore/EntityVersionedMutation.scala` | whole-file scan passed | not a spec | focused suite and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala` | whole-file scan passed; protected hook debt removed | not a spec | focused suite and `Test/compile` passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityConcurrency.scala` | whole-file scan passed | not a spec | focused suite and `Test/compile` passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/DataStoreConditionalTransitionSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/EntityVersionedMutationDataStoreSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/InMemoryConditionalTransitionSpec.scala` | whole-file scan passed | Given/When/Then structure, property checks, and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/SqliteConditionalTransitionSpec.scala` | whole-file scan passed; protected override debt removed | Given/When/Then structure, property checks, and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/MysqlConditionalTransitionAcceptanceSpec.scala` | whole-file scan passed; protected override debt removed | Given/When/Then structure, property checks, and matcher vocabulary passed | compiled; 5 live tests canceled by opt-in gate | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyTokenSpec.scala` | whole-file scan passed | Given/When/Then structure, property checks, and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedMutationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | focused suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/testutil/EntityRevisionFixture.scala` | whole-file scan passed | not a spec; shared admitted-record fixture | 104 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/action/ActionCallEntityAccessMetricsSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 20 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/component/ComponentFactoryAggregateViewBootstrapSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 5 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/component/ComponentFactoryDefaultAggregateCollectionSpec.scala` | whole-file naming debt removed | Given/When/Then structure and matcher vocabulary added | 4 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityStoreQueryRouteSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 25 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkSearchAuthorizationSpec.scala` | whole-file private-parameter debt removed | Given/When/Then structure and matcher vocabulary passed | 14 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkStateMachineHookSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 2 regression tests and `Test/compile` passed | review-fix verification passed |
| `src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkTargetAuthorizationSpec.scala` | whole-file scan passed | Given/When/Then structure and matcher vocabulary passed | 34 regression tests and `Test/compile` passed | review-fix verification passed |

## SE-04: Embedded SimpleEntity Lifecycle and OCC

Stage Status:
- Current status: DONE
- Current step: release validation and clean re-review complete
- Owner: CNCF SimpleEntity, EntityStore, UnitOfWork, and datastore maintainers
- Entry rule: SE-03 is DONE.
- Completion rule: Every admitted `SimpleEntity` persistence path manages
  embedded revision automatically, while policy controls only expected
  revision enforcement.

- [x] Initialize embedded revision on `SimpleEntity` creation.
- [x] Decode and return authoritative embedded revision on load.
- [x] Advance revision exactly once with successful update, soft delete, and
  restore.
- [x] Leave no revision advancement after rejection, conflict, rollback, or
  provider failure.
- [x] Reject create and mutation input that writes managed `revision`.
- [x] Implement declarative `None` and `Optimistic` policy semantics.
- [x] Apply selected Entity/collection precedence deterministically.
- [x] Require `expectedRevision` for every optimistic ordinary mutation.
- [x] Reject request-level optimistic-policy bypass.
- [x] Compare and advance revision in one provider-native atomic mutation.
- [x] Return a structured conflict for missing/stale optimistic revision.
- [x] Ensure policy `None` performs no expected-revision comparison.
- [x] Ensure policy `None` still advances managed revision.
- [x] Prevent resident EntitySpace/Working Set values from bypassing the
  datastore comparison.
- [x] Keep lifecycle, audit, content-body, and storage-shape behavior coherent.
- [x] Add persistence round-trip, rollback, restart, managed-field, and
  property-based simultaneous-update specifications.
- [x] Pass independent read-only review, clean re-review, and release
  validation.

Evidence:
- `EntityManagedMutationSpec` proves embedded create/load/update/delete/restore
  revision lifecycle, managed-field rejection, `None`/`Optimistic`, Working Set
  eviction, request-policy bypass rejection, fresh-runtime round trip, and
  provider-failure plus UnitOfWork rollback behavior.
- `EntityVersionedMutationDataStoreSpec` proves provider-native atomic
  compare/advance, root plus side-state publication, failure-before-publication
  rollback, policy behavior, and property-based simultaneous optimistic
  contenders.
- `EntityConcurrencyPolicySpec`, `EntityWritePolicySpec`,
  `EntityRevisionPreconditionSpec`, and `EntityRevisionMigrationSpec` prove
  declarative precedence, write/no-op behavior, revision preconditions, and
  persisted-revision admission.
- Independent review found three defects: the alternate detached storage alias
  could bypass Embedded managed-field admission, System-admitted unversioned
  save/update/upsert/update-by-id paths could bypass managed revision
  advancement, and `ObservedRequired` stale diagnostics could report an
  unrelated expected revision. The review-fix closes all three with direct
  Executable Specifications.
- The first release-validation full run exposed 80 failures in
  `StaticFormAppRendererSpec`: its common System-admitted fixture used
  `EntityStoreSaveUnversioned` to create missing records, while the first
  managed-save implementation had accidentally made save update-only.
  `StandardEntityStore.save` now preserves create-or-replace semantics while
  initializing revision one on create and advancing the authoritative revision
  on replace.
- The review-fix 10-suite SE-04 run passed 53 tests. Content-body,
  authorization, and component bootstrap regression suites passed 41 tests.
  The managed-mutation plus complete Static Form renderer run passed 329 tests.
  `Test/compile`, naming/spec scans, and `git diff --check` passed.
- Clean independent re-review found no actionable finding. Full release
  validation passed 2478 tests in 356 suites, with 7 canceled, 1 ignored, and
  59 pending.
- Whole-file implementation naming scans found no private/protected or
  changed-line parameter/local violations, and modified executable
  specifications contain no bare `assert`.

### SE-04 Modified Scala File Compliance Ledger

Clean re-review passed for every entry. Paths are repository-relative.

| Scala file | Naming | Spec style | Validation | Review |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/component/ComponentDescriptor.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/datastore/DataStore.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/datastore/EntityVersionedMutation.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityMutationPolicy.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityRevisionRepresentation.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityStore.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimeDescriptor.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/entity/runtime/EntityRuntimePlan.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala` | implementation scan passed | not a spec | focused suites, `Test/compile`, and full suite passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/datastore/EntityVersionedMutationDataStoreSpec.scala` | implementation scan passed | Given/When/Then, property checks, and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityConcurrencyPolicySpec.scala` | implementation scan passed | Given/When/Then and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityManagedMutationSpec.scala` | implementation scan passed | Given/When/Then and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityRevisionKernelSpec.scala` | implementation scan passed | Given/When/Then, property checks, and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityRevisionMigrationSpec.scala` | implementation scan passed | Given/When/Then, property checks, and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityRevisionPreconditionSpec.scala` | implementation scan passed | Given/When/Then and matcher vocabulary passed | focused and full suites passed | clean re-review passed |
| `src/test/scala/org/goldenport/cncf/entity/EntityWritePolicySpec.scala` | implementation scan passed | Given/When/Then, property checks, and matcher vocabulary passed | focused and full suites passed | clean re-review passed |

## SE-05: Detached Non-SimpleEntity Extension

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity persistence and extension maintainers
- Entry rule: SE-04 is DONE.
- Completion rule: An explicitly admitted non-`SimpleEntity` model can use the
  common revision kernel through a detached carrier without becoming a second
  standard Entity representation.

- [ ] Define the detached carrier around `EntityRevision`, not
  `EntityConcurrencyToken`.
- [ ] Admit detached revision only for Entity models that do not extend
  `SimpleEntity`.
- [ ] Require explicit Entity/collection registration; do not infer detached
  mode from a missing revision field or codec failure.
- [ ] Keep managed revision outside the domain codec while preserving it in
  the authoritative persistence contract.
- [ ] Apply the same `None` and `Optimistic` policy semantics where detached
  revision is admitted.
- [ ] Reject application writes to detached managed revision.
- [ ] Reject embedded plus detached values, dual writes, mirroring, and
  representation fallback.
- [ ] Add detached load/mutation, stale conflict, rollback, and projection
  specifications.

Evidence:
- Pending.

## SE-06: Conditional Transition Integration

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity, UnitOfWork, and Conditional Transition maintainers
- Entry rule: SE-05 is DONE.
- Completion rule: Conditional Transition uses the common `EntityRevision`
  kernel and preserves Phase 49 atomic outcomes for both admitted
  representations.

- [x] Replace transition token parameters with expected revision. Completed as
  SE-03D preparatory work; representation-specific result integration remains
  in this stage.
- [ ] Require expected revision under both ordinary concurrency policies.
- [ ] Return embedded root/successor `SimpleEntity` values containing
  authoritative revisions.
- [ ] Return detached revision carriers only when the admitted Entity model is
  non-`SimpleEntity`.
- [x] Remove `EntityConcurrencyToken` and token expectation dependencies from
  the canonical upper path. Completed by SE-03D; SE-06 must still verify the
  embedded and detached representation paths.
- [ ] Preserve authorization, lifecycle, transaction, audit, observability,
  Working Set, View, and rollback behavior.
- [ ] Add property-based simultaneous-update specifications.
- [ ] Re-run exactly-one-winner and no-orphan successor evidence.

Evidence:
- SE-03D removed the token/expectation types and changed Conditional Transition
  definitions, expectations, provider plans, diagnostics, and callers to
  `EntityRevision`.
- Remaining SE-06 evidence is pending for embedded/detached result integration,
  policy behavior, simultaneous updates, exactly-one-winner, and no-orphan
  verification.

## SE-07: Projection and Transport

Stage Status:
- Current status: PLANNED
- Owner: CNCF projection, Web, Form, and REST maintainers
- Entry rule: SE-06 is DONE.
- Completion rule: Mutation-capable surfaces can round-trip expected revision
  without exposing revision as writable business data or making detached
  representation part of the standard `SimpleEntity` contract.

- [ ] Project embedded revision on admitted `SimpleEntity` read/detail
  surfaces.
- [ ] Preserve bounded list/search projection according to the SE-01 decision.
- [ ] Carry expected revision through generated update operations.
- [ ] Carry expected revision through Form and Web update submissions.
- [ ] Carry expected revision through REST request/response contracts.
- [ ] Derive transport validators from revision only where explicitly
  specified.
- [ ] Reject patch paths that target the managed revision.
- [ ] Expose detached revision only through explicitly revision-aware
  non-`SimpleEntity` extension surfaces.
- [ ] Do not emit token aliases or detached carrier roots for `SimpleEntity`.
- [ ] Add JSON/YAML/XML/Form projection parity evidence where applicable.

Evidence:
- Pending.

## SE-08: Provider, Migration, Downstream, and Regression Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore-provider, generator, and downstream maintainers
- Entry rule: SE-07 is DONE.
- Completion rule: Providers, migration rules, generators, and downstream
  consumers prove the standard embedded path and the explicit detached
  extension without compatibility behavior.

- [ ] Verify deterministic in-memory reference behavior.
- [ ] Verify SQLite atomic comparison, rollback, restart, and concurrency.
- [ ] Verify one shared-provider profile with independent callers.
- [ ] Run the same atomic semantic matrix for embedded and detached revision.
- [ ] Verify deterministic unsupported-provider admission.
- [ ] Verify records without revision follow the explicit SE-01 rule.
- [ ] Verify timestamps and resident values are never fallback tokens.
- [ ] Verify schema mismatch does not silently degrade to last-write-wins.
- [ ] Migrate generated and downstream `SimpleEntity` users to embedded
  revision.
- [ ] Use detached revision downstream only for a proven non-`SimpleEntity`
  model.
- [ ] Prove Conditional Transition remains exactly-one-winner downstream.
- [ ] Prove ordinary non-OCC Entity applications retain declared semantics.
- [ ] Prove optimistic Entity applications reject stale writes.
- [ ] Run model-library, CNCF, and relevant downstream focused suites.
- [ ] Run CNCF and relevant downstream full suites.
- [ ] Confirm no token compatibility adapter, implicit detached admission, or
  duplicate revision field remains.

Evidence:
- Pending.

## SE-09: Confirmed Design and Specification Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF Entity architecture maintainers
- Entry rule: SE-08 is DONE and implementation/provider behavior is stable.
- Completion rule: Canonical design/specification describes exactly the
  verified implementation and references exact Executable Specification
  evidence.

- [ ] Update `docs/design/entity-conflict-and-conditional-transition.md`.
- [ ] Update `docs/spec/entity-conflict-and-conditional-transition.md`.
- [ ] Update `docs/design/simpleentity-storage-shape-policy.md`.
- [ ] Update other affected canonical Entity persistence/API documents.
- [ ] Remove current separate-token requirements.
- [ ] Define embedded revision as the standard `SimpleEntity` contract.
- [ ] Define detached revision as an explicit non-`SimpleEntity` extension,
  not a compatibility or fallback path.
- [ ] Record final revision lifecycle and managed-field rules.
- [ ] Record final concurrency-policy declaration, precedence, and default.
- [ ] Record final Conditional Transition revision contract.
- [ ] Record final projection and expected-revision transport contract.
- [ ] Link normative examples to exact Executable Specifications.
- [ ] Verify no current design/spec document contradicts the implemented OCC
  contract.

Evidence:
- Pending.

## SE-10: Verification and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF phase maintainers
- Entry rule: SE-09 is DONE.
- Completion rule: Full validation and clean review are complete and strategy,
  phase, design, spec, implementation, and evidence agree.

- [ ] Run `sbt --batch Test/compile`.
- [ ] Run the full CNCF test suite.
- [ ] Run required `simplemodeling-lib`, `simplemodeling-model`, and downstream
  suites.
- [ ] Run `git diff --check`.
- [ ] Complete read-only review.
- [ ] Fix every actionable finding.
- [ ] Complete clean re-review.
- [ ] Update strategy completed history and remove the active 9.41 item.
- [ ] Close Phase 50 dashboard and checklist with exact validation evidence.
- [ ] Confirm force/merge/repair/UX scope remains visible in strategy item
  9.40.

Evidence:
- Pending.
