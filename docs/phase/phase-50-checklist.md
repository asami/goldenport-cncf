# Phase 50 Checklist - SimpleEntity Revision and OCC Simplification

status=planned
phase=[Phase 50 - SimpleEntity Revision and OCC Simplification](phase-50.md)

This checklist is the authoritative Phase 50 state ledger after Phase 50
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 49 closes.

## SE-01: Contract Decisions and Executable Acceptance

Stage Status:
- Current status: PLANNED
- Owner: SimpleEntity, CNCF Entity, and datastore maintainers
- Entry rule: Phase 49 is closed.
- Completion rule: Every unresolved contract choice is explicit and represented
  by an exact Executable Specification expectation before implementation.

- [ ] Confirm `EntityRevision` ownership in `simplemodeling-model`.
- [ ] Inventory existing `simplemodeling-lib` generic datatype/schema/decoder
  support and identify whether any independently reusable primitive is
  actually missing.
- [ ] Fix the canonical initial revision.
- [ ] Fix successful mutation and no-op mutation advancement semantics.
- [ ] Fix Entity-level and collection-level policy declaration and precedence.
- [ ] Fix the deterministic ordinary concurrency-policy default.
- [ ] Fix missing-revision schema/data migration or admission behavior.
- [ ] Fix revision visibility for read/search/View/Aggregate projections.
- [ ] Fix REST/Form/Web/generated-client expected-revision transport.
- [ ] Fix the representation matrix: `SimpleEntity` uses embedded revision;
  only an explicitly admitted non-`SimpleEntity` model may use detached
  revision.
- [ ] Fix where Entity/collection revision representation is declared and how
  conflicting declarations fail.
- [ ] Fix the detached carrier API and its physical managed-field contract.
- [ ] Inventory Phase 49 implementation assets and classify each as common
  kernel reuse, detached-extension refactoring, or provisional token removal.
- [ ] Prohibit dual representation, mirroring, per-request selection, and
  implicit embedded/detached fallback.
- [ ] Add failing-first Executable Specification identities for all Phase 50
  acceptance groups.

Evidence:
- Pending.

## SE-02: SimpleEntity Revision Model

Stage Status:
- Current status: PLANNED
- Owner: `simplemodeling-model` maintainers, with `simplemodeling-lib`
  maintainers only for proven generic gaps
- Entry rule: SE-01 is DONE.
- Completion rule: The model surface contains one validated managed revision
  attribute and existing lifecycle timestamp semantics remain unchanged.

- [ ] Add `EntityRevision` under the appropriate
  `org.simplemodeling.model` datatype boundary.
- [ ] Add `revision` as the only new standard `SimpleEntity` attribute.
- [ ] Preserve `createdAt` and `updatedAt` without adding an OCC timestamp.
- [ ] Define read-only/framework-managed metadata classification.
- [ ] Reuse existing `simplemodeling-lib` generic datatype, schema,
  `ValueReader`, `Consequence`, and record facilities.
- [ ] Add a `simplemodeling-lib` primitive only when it is independently
  reusable and cannot be expressed correctly with existing core APIs.
- [ ] Keep `simplemodeling-lib` independent of `SimpleEntity`, CNCF, and OCC.
- [ ] Add property-based datatype and model-shape specifications.
- [ ] Publish the required `simplemodeling-lib` snapshot first when changed.
- [ ] Publish the `simplemodeling-model` snapshot and consume it from CNCF.

Evidence:
- Pending.

## SE-03: Common Revision Kernel and Binding

Stage Status:
- Current status: PLANNED
- Owner: CNCF EntityStore, UnitOfWork, and datastore maintainers
- Entry rule: SE-02 is DONE.
- Completion rule: Embedded and detached paths use one `EntityRevision`,
  one atomic provider kernel, and one deterministic representation binding.

- [ ] Replace `EntityConcurrencyToken` with common `EntityRevision`.
- [ ] Retain and generalize the Phase 49 revision-field-independent atomic
  compare-and-advance provider operation.
- [ ] Define Entity/collection revision representation metadata.
- [ ] Resolve exactly one representation before persistence execution.
- [ ] Reject `SimpleEntity + Detached`, non-`SimpleEntity + implicit
  Detached`, dual fields, and conflicting declarations.
- [ ] Keep authorization, UnitOfWork, transaction, audit, diagnostics, and
  observability common across representations.
- [ ] Add representation-selection and common-kernel Executable
  Specifications.

Evidence:
- Pending.

## SE-04: Embedded SimpleEntity Lifecycle and OCC

Stage Status:
- Current status: PLANNED
- Owner: CNCF SimpleEntity, EntityStore, UnitOfWork, and datastore maintainers
- Entry rule: SE-03 is DONE.
- Completion rule: Every admitted `SimpleEntity` persistence path manages
  embedded revision automatically, while policy controls only expected
  revision enforcement.

- [ ] Initialize embedded revision on `SimpleEntity` creation.
- [ ] Decode and return authoritative embedded revision on load.
- [ ] Advance revision exactly once with successful update, soft delete, and
  restore.
- [ ] Leave no revision advancement after rejection, conflict, rollback, or
  provider failure.
- [ ] Reject create and mutation input that writes managed `revision`.
- [ ] Implement declarative `None` and `Optimistic` policy semantics.
- [ ] Apply selected Entity/collection precedence deterministically.
- [ ] Require `expectedRevision` for every optimistic ordinary mutation.
- [ ] Reject request-level optimistic-policy bypass.
- [ ] Compare and advance revision in one provider-native atomic mutation.
- [ ] Return a structured conflict for missing/stale optimistic revision.
- [ ] Ensure policy `None` performs no expected-revision comparison.
- [ ] Ensure policy `None` still advances managed revision.
- [ ] Prevent resident EntitySpace/Working Set values from bypassing the
  datastore comparison.
- [ ] Keep lifecycle, audit, content-body, and storage-shape behavior coherent.
- [ ] Add persistence round-trip, rollback, restart, managed-field, and
  property-based simultaneous-update specifications.

Evidence:
- Pending.

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

- [ ] Replace transition token parameters with expected revision.
- [ ] Require expected revision under both ordinary concurrency policies.
- [ ] Return embedded root/successor `SimpleEntity` values containing
  authoritative revisions.
- [ ] Return detached revision carriers only when the admitted Entity model is
  non-`SimpleEntity`.
- [ ] Remove `EntityConcurrencyToken` and token expectation dependencies from
  the canonical and extension paths.
- [ ] Preserve authorization, lifecycle, transaction, audit, observability,
  Working Set, View, and rollback behavior.
- [ ] Add property-based simultaneous-update specifications.
- [ ] Re-run exactly-one-winner and no-orphan successor evidence.

Evidence:
- Pending.

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
