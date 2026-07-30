# Phase 54 Checklist - Subsystem Datastore Pool Ownership and Shutdown Closure

status=planned
phase=[Phase 54 - Subsystem Datastore Pool Ownership and Shutdown Closure](phase-54.md)

This checklist is the authoritative Phase 54 state ledger after Phase 54
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 53 closes.

## DSP-01: Inventory and Failing-First Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore, runtime, and Subsystem maintainers
- Entry rule: Phase 53 is closed.
- Completion rule: Every managed SQL creation/resolution path, lifecycle
  owner, identity input, timeout decision, and injected-store boundary is
  represented by failing executable evidence.

- [ ] Inventory `ComponentDataStore` local/dedicated/basic resolution.
- [ ] Inventory direct `SqlDataStore.jdbc` and `SqlDataStore.sqlite` creation.
- [ ] Inventory runtime-bootstrap and application datastore construction.
- [ ] Inventory every ActionCall/Entity helper datastore resolution path.
- [ ] Inventory command, server, embedded, fixture, and startup-failure
  Subsystem termination paths.
- [ ] Classify Subsystem-owned, caller-owned, injected, and partial resources.
- [ ] Freeze canonical datastore identity inputs and secret-safe diagnostics.
- [ ] Freeze datastore alias/provenance versus canonical identity behavior.
- [ ] Freeze the Subsystem execution-lease grant/release linearization point
  for HTTP/Action, Job, nested-call, direct-borrow, and creation paths.
- [ ] Freeze lazy resolution behavior for work admitted before `Stopping`.
- [ ] Freeze the bounded drain timeout and configuration ownership.
- [ ] Freeze direct ownerless `ComponentDataStore` resolution as caller-owned
  and the explicit owner input for managed resolution.
- [ ] Freeze `shutdownC` source/binary compatibility and cleanup-result
  aggregation.
- [ ] Register failing-first same-key, concurrency, shutdown-race, close, and
  resource-stability specifications.
- [ ] Record that HTTP reset causality remains outside the proven baseline.

Evidence:
- Pending.

## DSP-02: Managed SQL Lifecycle and Identity

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore and SQL provider maintainers
- Entry rule: DSP-01 is DONE.
- Completion rule: Lifecycle-capable SQL stores have an explicit ownership and
  close contract, and effective identities are deterministic and secret-safe.

- [ ] Define the bounded managed datastore resource contract.
- [ ] Make direct SQL construction explicitly caller-owned and closeable.
- [ ] Close partially created resources when construction fails.
- [ ] Make SQL datastore close idempotent and observable.
- [ ] Define normalized SQLite path identity.
- [ ] Define normalized JDBC endpoint/database/catalog/schema identity.
- [ ] Include principal, mandatory credential reference/version/fingerprint,
  and effective pool/transaction configuration without exposing credentials.
- [ ] Prove credential rotation cannot reuse the old credential's pool.
- [ ] Preserve logical name, alias, and selection-source provenance.
- [ ] Add identity equality, non-collision, stability, and redaction property
  specifications.
- [ ] Add exactly-once and failed-construction lifecycle specifications.

Evidence:
- Pending.

## DSP-03: Subsystem Registry and Single-Flight Creation

Stage Status:
- Current status: PLANNED
- Owner: CNCF Subsystem and datastore runtime maintainers
- Entry rule: DSP-02 is DONE.
- Completion rule: One Subsystem-owned registry linearizes creation and
  enforces the cardinality contract without cross-Subsystem sharing.

- [ ] Add the Subsystem-owned managed datastore registry.
- [ ] Implement `Running`, `Stopping`, and `Stopped` registry state.
- [ ] Implement the Subsystem execution lease and active-lease accounting.
- [ ] Make nested calls inherit the active lease.
- [ ] Implement same-key linearizable single-flight creation.
- [ ] Publish one successful resource to all current waiters.
- [ ] Keep different canonical identities isolated.
- [ ] Keep equal identities in different Subsystems isolated.
- [ ] Close partial resources and publish no entry after failed creation.
- [ ] Permit retry after a failed creation.
- [ ] Reject conflicting effective definitions without replacement.
- [ ] Reject requests without a pre-`Stopping` lease after shutdown wins the
  state transition.
- [ ] Allow a valid pre-`Stopping` lease to complete accounted lazy resolution
  and include its creation in the drain.
- [ ] Prove one creation for 100 concurrent same-key resolutions.
- [ ] Prove deterministic behavior for concurrent creation failure and retry.

Evidence:
- Pending.

## DSP-04: Component and ActionCall Adoption

Stage Status:
- Current status: PLANNED
- Owner: CNCF component, action, Entity, and datastore maintainers
- Entry rule: DSP-03 is DONE.
- Completion rule: Application datastore helpers borrow the Subsystem-owned
  resource while preserving configuration and binding behavior.

- [ ] Add an explicit Subsystem-owned managed `ComponentDataStore` resolution
  path.
- [ ] Keep direct ownerless `ComponentDataStore` resolution caller-owned and
  closeable.
- [ ] Route application datastore binding through the registry.
- [ ] Route direct component datastore helper access through the registry.
- [ ] Remove repeated helper-time Hikari pool creation.
- [ ] Preserve datastore policy and configuration precedence.
- [ ] Preserve component/logical datastore provenance.
- [ ] Preserve nested ActionCall capture/restore behavior.
- [ ] Preserve one inherited execution lease across nested ActionCalls.
- [ ] Preserve transaction and UnitOfWork connection ownership.
- [ ] Preserve injected in-memory and caller-owned datastore behavior.
- [ ] Add repeated Entity/helper/action reuse specifications.
- [ ] Add alias-equivalence and distinct-effective-identity specifications.
- [ ] Prove that no ActionCall cache owns or closes a pool.

Evidence:
- Pending.

## DSP-05: Shutdown Admission, Drain, and Close

Stage Status:
- Current status: PLANNED
- Owner: CNCF Subsystem, Job, managed-service, and datastore maintainers
- Entry rule: DSP-04 is DONE.
- Completion rule: Subsystem shutdown deterministically stops admission,
  drains accepted work, closes owned pools once, and aggregates all cleanup.

- [ ] Atomically enter `Stopping`.
- [ ] Reject new execution leases after `Stopping`.
- [ ] Quiesce the JobEngine before datastore closure.
- [ ] Drain valid pre-`Stopping` leases, inherited nested calls, direct managed
  borrows, and in-flight creation under the bounded policy.
- [ ] Close owned pools once in deterministic registry order.
- [ ] Continue closing later resources after a close failure.
- [ ] Continue existing service-container, MCP, and evaluation cleanup.
- [ ] Aggregate structured cleanup failures.
- [ ] Make repeated and concurrent shutdown idempotent.
- [ ] On drain timeout, revoke remaining leases, prevent later borrow, cancel
  supported work, reclaim pools best-effort, and report affected work
  structurally.
- [ ] Preserve the existing `shutdownC` signature and successful
  service-container outcome while aggregating datastore cleanup failures.
- [ ] Ensure `shutdown()` does not bypass normal cleanup observation.
- [ ] Add shutdown/acquire, shutdown/use, timeout, and multi-failure
  specifications.

Evidence:
- Pending.

## DSP-06: Runtime and Resource Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF CLI, server, fixture, runtime, and observability maintainers
- Entry rule: DSP-05 is DONE.
- Completion rule: Every runtime entry and exit path finalizes Subsystem
  ownership, and bounded resource evidence proves no pool accumulation.

- [ ] Finalize command-mode Subsystems.
- [ ] Finalize server-mode Subsystems on normal termination.
- [ ] Finalize Subsystems after startup failure.
- [ ] Finalize embedded/runtime API Subsystems.
- [ ] Preserve deterministic `SubsystemTestFixture` cleanup.
- [ ] Add bounded repeated same-identity operation acceptance.
- [ ] Prove pool count remains at the expected identity cardinality.
- [ ] Prove Hikari housekeeper-thread count remains bounded.
- [ ] Prove SQLite descriptor count remains bounded.
- [ ] Prove owned pool threads and descriptors return to baseline after
  shutdown.
- [ ] Validate the representative Control Center datastore path without making
  Control Center a framework implementation repository.
- [ ] Keep HTTP reset causality as a separate runtime diagnosis unless directly
  proven.

Evidence:
- Pending.

## DSP-07: Regression and Canonical Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF datastore, Entity, runtime, documentation, and release maintainers
- Entry rule: DSP-06 is DONE.
- Completion rule: Full compatibility and resource evidence pass, normative
  documentation matches verified behavior, and no pool lifecycle contract
  remains planning-only.

- [ ] Run focused datastore, Entity, OCC, UnitOfWork, Subsystem, CLI, and HTTP
  specifications.
- [ ] Run `Test/compile`.
- [ ] Run the complete CNCF full suite.
- [ ] Run bounded resource acceptance with exact before/after evidence.
- [ ] Run naming, executable-specification, and `git diff --check` gates.
- [ ] Perform independent read-only review.
- [ ] Apply every actionable review finding.
- [ ] Perform a clean focused re-review.
- [ ] Create or update
  `docs/design/subsystem-datastore-pool-lifecycle.md`.
- [ ] Create or update
  `docs/spec/subsystem-datastore-pool-lifecycle.md`.
- [ ] Update architecture, strategy, phase, checklist, and runtime lifecycle
  references.
- [ ] Record exact test, resource, review, and release evidence.
- [ ] Close Phase 54 only after all completion rules pass.

Evidence:
- Pending.

## Modified Scala File Compliance Ledger

No Scala file is modified by the Phase 54 planning-only insertion.

During implementation, every created or modified Scala file must be recorded
with:

- complete-file naming compliance;
- executable-specification compliance or `not a spec`;
- focused and full validation evidence; and
- final Phase 54 commit.

## Status

Phase 54 is PLANNED. DSP-01 has not started.
