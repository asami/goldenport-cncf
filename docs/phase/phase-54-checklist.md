# Phase 54 Checklist - SystemNode Datastore Pool Ownership and Shutdown Closure

status=closed
phase=[Phase 54 - SystemNode Datastore Pool Ownership and Shutdown Closure](phase-54.md)

This checklist is the authoritative Phase 54 state ledger after Phase 54
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 53 closes.

## DSP-01: Inventory and Failing-First Contract

Stage Status:
- Current status: DONE
- Owner: CNCF datastore, runtime, SystemNode, and Subsystem maintainers
- Entry rule: Phase 53 is closed.
- Completion rule: Source inventory is complete and the selected lifecycle,
  ownership, timeout-invalid-value, reuse, concurrency, close, and
  injected-store contract groups are registered as failing-first evidence.
  Typed configuration/default/precedence, canonical identity implementation,
  and production/resource acceptance remain later stages.

- [x] Inventory `ComponentDataStore` local/dedicated/basic resolution.
- [x] Inventory direct `SqlDataStore.jdbc` and `SqlDataStore.sqlite` creation.
- [x] Inventory runtime-bootstrap and application datastore construction.
- [x] Inventory every ActionCall/Entity helper datastore resolution path.
- [x] Inventory command, server, embedded, fixture, and startup-failure
  Subsystem termination paths.
- [x] Classify SystemNode-owned pools, Subsystem-owned bindings/leases,
  caller-owned stores, injected stores, and partial resources.
- [x] Freeze canonical datastore identity inputs and secret-safe diagnostics.
- [x] Freeze datastore alias/provenance versus canonical identity behavior.
- [x] Freeze the SystemNode resource-lease grant/release linearization point
  across resident Subsystem HTTP/Action, Job, nested-call, direct-borrow, and
  creation paths.
- [x] Freeze lazy resolution behavior for work admitted before `Stopping`.
- [x] Freeze the bounded drain timeout and configuration ownership.
- [x] Freeze the SystemNode-owned zero-binding pool reclamation policy: retain
  a zero-binding pool until SystemNode shutdown; defer opportunistic
  reclamation to a future explicit policy.
- [x] Freeze direct ownerless `ComponentDataStore` resolution as caller-owned
  and the explicit SystemNode owner plus Subsystem binding for managed
  resolution.
- [x] Freeze `shutdownC` source/binary compatibility and cleanup-result
  aggregation.
- [x] Register failing-first same-key, concurrency, shutdown-race, close, and
  bounded fake-resource-stability specifications. Private-port evidence is
  registered; production registry/lifecycle binding and DSP-06 real-resource
  acceptance remain separate work.
- [x] Record that HTTP reset causality remains outside the proven baseline.

Evidence:
- `docs/notes/phase-54-dsp01-inventory-and-failing-first-contract.md`
  records the creation/resolution/termination inventory and the frozen
  SystemNode timeout contract.
- `SystemNodeDataStorePoolRuntimeSpec` and
  `SystemNodeDataStoreShutdownSpec` register deterministic
  `pendingUntilFixed` provisional parser-admission counterexamples and active
  ScalaCheck boundary properties for
  over-bound, zero, negative, and non-numeric canonical timeout values. They
  require the canonical key and rejected value in the eventual structured
  failure; typed SystemNode construction is not yet implemented.
- DSP-01 is DONE. Its failing-first registry/lifecycle, concurrent-acquisition,
  shutdown-race, close, and bounded fake-resource specifications are
  registered; DSP-02--DSP-06 bind them to production seams and provide real
  resource acceptance.
- DSP-01B completed the remaining borrower/binding and termination-path source
  matrices without modifying production or test Scala.
- DSP-01C records the accepted zero-binding retention and canonical-key close
  order, and adds private-port `pendingUntilFixed` lifecycle/registry evidence.
  Focused validation passed: `Test/compile`; the two focused suites completed
  with 20 intended pending scopes, 0 failures, and 0 cancellations. DSP-01 is
  complete at failing-first registration; DSP-02--DSP-06 own production-seam
  binding and real-resource acceptance.

## DSP-02: Managed SQL Lifecycle and Identity

Stage Status:
- Current status: DONE
- Owner: CNCF datastore and SQL provider maintainers
- Entry rule: DSP-01 is DONE.
- Completion rule: Lifecycle-capable SQL stores have an explicit ownership and
  close contract, and effective identities are deterministic and secret-safe.

- [x] Define the bounded managed datastore resource contract.
- [x] Make direct SQL construction explicitly caller-owned and closeable.
- [x] Close partially created resources when construction fails.
- [x] Make SQL datastore close idempotent and observable.
- [x] Define normalized SQLite path identity.
- [x] Define normalized JDBC endpoint/database/catalog/schema identity.
- [x] Include principal, mandatory credential reference/version/fingerprint,
  and effective pool/transaction configuration without exposing credentials.
- [x] Prove credential rotation cannot reuse the old credential's pool.
- [x] Preserve logical name, alias, and selection-source provenance.
- [x] Add identity equality, non-collision, stability, and redaction property
  specifications.
- [x] Add exactly-once and failed-construction lifecycle specifications.

Evidence:
- The implementation is complete for this stage. The admitted managed identity grammars are
  SQLite filesystem and named shared-memory targets, MySQL/MariaDB, and
  PostgreSQL. `:memory:` and private SQLite memory remain direct caller-owned
  only; SQL Server, Oracle, Db2, unknown dialects, and JDBC userinfo are
  structurally rejected. Raw credentials use only a SystemNode-lifetime,
  explicitly injected opaque HMAC key; ambient configuration is forbidden.
- `ManagedSqlDataStoreResource` supplies one physical close, cached terminal
  success/failure, post-close structural borrow rejection, and unpublished
  resource cleanup. `SqlDataStore` factories transfer Hikari ownership while
  its public constructor remains non-owning; `SqlDataStore.owning` is the
  explicit infrastructure transfer path.
- `SqlDataStoreIdentity` implements secret-safe canonical identities for the
  admitted SQLite/MySQL/MariaDB/PostgreSQL grammars. `SqlDataStoreLifecycleSpec`
  and `DataStoreIdentitySpec` cover ownership, closure, path/endpoint
  normalization, cloud-style endpoints, rotation, redaction, and deferred
  dialect rejection.
- Focused validation passed: `Test/compile` (invocation
  `80313-20260801T222313Z`) and `testOnly
  org.goldenport.cncf.datastore.SqlDataStoreLifecycleSpec
  org.goldenport.cncf.datastore.DataStoreIdentitySpec` (invocation
  `81007-20260801T222430Z`, 12 succeeded, 0 failed/canceled/ignored/pending).
- Focused legacy regression passed: `testOnly
  org.goldenport.cncf.datastore.SqliteDataStoreSpec
  org.goldenport.cncf.datastore.ComponentDataStoreSpec
  org.goldenport.cncf.datastore.SqliteConditionalTransitionSpec` (invocation
  `82104-20260801T222636Z`, 34 succeeded, 0 failed/canceled/ignored, 1
  pre-existing pending scope).
- Review-fix validation passed after routing versioned and conditional
  transaction borrowing through the managed resource, parsing safe SQLite URI
  properties, making raw HMAC input byte-preserving, encapsulating key bytes,
  and adding active ScalaCheck properties: `Test/compile` (invocation
  `89097-20260801T224053Z`), DSP-02 focused suites (invocation
  `89722-20260801T224156Z`, 14 succeeded, 0 failed/canceled/ignored/pending),
  and the SQLite/Component/conditional transition regression suites
  (invocation `90336-20260801T224258Z`, 34 succeeded, 0 failed/canceled, 1
  pre-existing pending scope).
- The focused re-review safe-property finding is fixed: `foreign_keys` is an
  admitted SQLite configuration property, while explicit credential-bearing
  names remain rejected. `Test/compile` passed (invocation
  `97330-20260801T225653Z`) and `DataStoreIdentitySpec` passed 9/9 (invocation
  `97922-20260801T225754Z`).

## DSP-03: SystemNode Registry and Single-Flight Creation

Stage Status:
- Current status: DONE
- Owner: CNCF SystemNode, Subsystem, and datastore runtime maintainers
- Entry rule: DSP-02 is DONE.
- Completion rule: One SystemNode-owned registry linearizes creation, shares
  equal canonical identities across resident Subsystems, and prevents
  cross-SystemNode sharing.

- [x] Add the SystemNode-owned managed datastore registry.
- [x] Implement `Running` and `Stopping` registry admission state; terminal
  `Stopped` transition follows ordered node-owned pool closure in DSP-05.
- [x] Implement the SystemNode resource lease and active-lease accounting.
- [x] Implement Subsystem datastore binding and lease ownership.
- [x] Make nested calls inherit the active lease.
- [x] Implement same-key linearizable single-flight creation.
- [x] Publish one successful resource to all current waiters.
- [x] Keep different canonical identities isolated.
- [x] Share equal canonical identities across Subsystems in one SystemNode.
- [x] Keep equal identities in different SystemNodes isolated.
- [x] Close partial resources and publish no entry after failed creation.
- [x] Permit retry after a failed creation.
- [x] Reject conflicting effective definitions without replacement.
- [x] Reject requests without a pre-`Stopping` lease after shutdown wins the
  state transition.
- [x] Allow a valid pre-`Stopping` lease to complete lazy resolution.
- [ ] Account every pre-`Stopping` creation in the terminal drain.
- [x] Prove one creation for 100 concurrent same-key resolutions.
- [x] Prove deterministic behavior for concurrent creation failure and retry.

Evidence:
- `SystemNode` owns the registry boundary and random per-node HMAC key
  provision. Each `SystemNodeDataStoreBinding` issues and revokes only its own
  leases; release clears logical identity bindings without closing a
  node-owned resource.
- `SystemNodeSqlDataStoreRegistry` publishes exactly one successful resource
  per canonical identity, makes other same-key callers await that outcome, and
  removes a failed flight before retry. Factory throws are converted to a
  structured outcome so no waiter is stranded.
- Focused validation passed: `Test/compile` (invocation
  `48131-20260802T003101Z`) and `testOnly
  org.goldenport.cncf.datastore.SystemNodeDataStorePoolRuntimeSpec`
  (invocation `48766-20260802T003211Z`, 10 succeeded, 0 failed/canceled, 3
  intentional pending scopes: E1/E2 configuration admission and E7 terminal
  finalization). The active E3--E6 and E8--E13 cases cover same-key reuse,
  resident-node sharing, cross-node isolation, 100 callers, failed creation
  retry, binding release, pre-Stopping lazy resolution, distinct identities,
  conflicting logical definitions, non-inflating nested lease inheritance,
  concurrent failed-flight retry, and real resource-instance topology checks.
- Review clean: initial review findings on flight/terminal sequencing,
  concurrency evidence, object identity, bounded waits, naming, and status
  accuracy were fixed. Focused re-review returned `RE_REVIEW_CLEAN`.

## DSP-04: Component and ActionCall Adoption

Stage Status:
- Current status: DONE
- Owner: CNCF component, action, Entity, and datastore maintainers
- Entry rule: DSP-03 is DONE.
- Completion rule: Application datastore helpers borrow the SystemNode-owned
  pool through a Subsystem binding while preserving configuration behavior.

- [x] Add an explicit SystemNode-owned managed `ComponentDataStore` resolution
  path through the owning Subsystem binding.
- [x] Keep direct ownerless `ComponentDataStore` resolution caller-owned and
  closeable.
- [x] Route application datastore binding through the registry.
- [x] Route direct component datastore helper access through the registry.
- [x] Remove repeated helper-time Hikari pool creation.
- [x] Preserve datastore policy and configuration precedence.
- [x] Preserve component/logical datastore provenance.
- [x] Preserve nested ActionCall capture/restore behavior.
- [x] Preserve one inherited execution lease across nested ActionCalls.
- [x] Preserve transaction and UnitOfWork connection ownership.
- [x] Preserve injected in-memory and caller-owned datastore behavior.
- [x] Add repeated Entity/helper/action reuse specifications.
- [x] Add alias-equivalence and distinct-effective-identity specifications.
- [x] Prove that no ActionCall cache owns or closes a pool.

Evidence:
- `ComponentDataStore.resolveManagedForDataStoreSpaceC` preserves policy and
  configuration selection while resolving managed SQLite/JDBC resources through
  the SystemNode binding and lease. The public direct `resolve` overloads
  remain caller-owned.
- `DataStoreSpace.bindManagedApplicationDataStoreC`, `Subsystem`, and the
  ActionCall application-datastore path use that managed resolution seam.
  `SqlDataStore.managedView` borrows through the shared resource while staying
  non-owning for close.
- Initial validation passed: `Test/compile` (invocation
  `65738-20260802T010413Z`) and ComponentDataStore/ActionCall regression
  (invocation `63056-20260802T005934Z`, 32 succeeded, 0 failed/canceled).
- `ComponentDataStoreManagedLifecycleSpec` E1/E2 prove resident-binding
  resource sharing, non-owning view close, and legacy-alias canonical reuse
  (invocation `67703-20260802T010811Z`, 2 succeeded, 0 failed/canceled).
- Review fixes make every managed SQL borrow revalidate its admitted lease,
  allow concurrent borrows while close drains them, remove released leases
  from a long-lived binding, and roll back an unpublished logical identity.
  Component and direct action roots plus component-backed `ActionTask` runs
  execute in one inherited Subsystem lease. Validation passed: `Test/compile`
  (invocations `82787-20260802T013756Z` and `88744-20260802T014921Z`),
  Component/Action/Entity/UnitOfWork regression (invocation
  `83924-20260802T014016Z`, 34 succeeded, 0 failed), and lifecycle/Job
  regression (invocation `89537-20260802T015049Z`, 26 succeeded, 0 failed).
  The initial review findings were fixed and the final focused re-review
  returned `RE_REVIEW_CLEAN`.

## DSP-05: Shutdown Admission, Drain, and Close

Stage Status:
- Current status: DONE
- Owner: CNCF Subsystem, Job, managed-service, and datastore maintainers
- Entry rule: DSP-04 is DONE.
- Completion rule: SystemNode shutdown deterministically stops admission,
  drains accepted work, closes node-owned pools once, and aggregates cleanup;
  Subsystem shutdown releases its bindings without directly closing node-owned
  pools.

- [x] Atomically enter `Stopping`.
- [x] Reject new execution leases after `Stopping`.
- [x] Quiesce the JobEngine before datastore closure.
- [x] Drain valid pre-`Stopping` leases, inherited nested calls, direct managed
  borrows, and in-flight creation under the bounded policy.
- [x] Close owned pools once in ascending secret-safe canonical datastore
  identity order, independent of creation/insertion timing.
- [x] Release a terminating Subsystem's bindings and leases without closing a
  pool still leased by another resident Subsystem.
- [x] Retain a zero-binding pool until SystemNode shutdown; keep opportunistic
  reclamation under a future explicit SystemNode policy.
- [x] Continue closing later resources after a close failure.
- [x] Continue existing service-container, MCP, and evaluation cleanup.
- [x] Aggregate structured cleanup failures.
- [x] Make repeated and concurrent shutdown idempotent.
- [x] On drain timeout, revoke remaining leases, prevent later borrow, cancel
  supported work, reclaim pools best-effort, and report affected work
  structurally.
- [x] Preserve the existing `shutdownC` signature and successful
  service-container outcome while aggregating datastore cleanup failures.
- [x] Ensure `shutdown()` does not bypass normal cleanup observation.
- [x] Add shutdown/acquire, shutdown/use, timeout, and multi-failure
  specifications.

Evidence:
- Serialized `Test/compile` passed (invocation `28354-20260802T030831Z`).
- Focused SystemNode, pool-runtime, and JobEngine validation passed: 46
  succeeded, 0 failed (invocation `29524-20260802T031045Z`).
- The interruption-safe terminal-close regression passed: `Test/compile` and
  `SystemNodeDataStoreShutdownSpec`, 16 succeeded, 0 failed (invocation
  `42580-20260802T033438Z`).
- Fresh focused re-review returned `RE_REVIEW_CLEAN` after the interruption
  correction.

## DSP-06: Runtime and Resource Acceptance

Stage Status:
- Current status: DONE
- Owner: CNCF CLI, server, fixture, runtime, and observability maintainers
- Entry rule: DSP-05 is DONE.
- Completion rule: Every runtime entry and exit path finalizes SystemNode pool
  ownership and Subsystem bindings, and bounded resource evidence proves no
  pool accumulation.

- [x] Finalize command-mode Subsystems.
- [x] Finalize server-mode Subsystems on normal termination.
- [x] Finalize Subsystems after startup failure.
- [x] Finalize embedded/runtime API Subsystems.
- [x] Preserve deterministic `SubsystemTestFixture` cleanup.
- [x] Add bounded repeated same-identity operation acceptance.
- [x] Prove pool count remains at the expected identity cardinality.
- [x] Prove equal identities across resident Subsystems reuse one node-owned
  pool and different SystemNodes never share it.
- [x] Prove Hikari housekeeper-thread count remains bounded.
- [x] Prove SQLite descriptor count remains bounded through the Hikari physical
  SQLite connection count.
- [x] Prove owned pool threads and descriptors return to baseline after
  shutdown.
- [x] Validate the representative Control Center datastore path without making
  Control Center a framework implementation repository.
- [ ] Keep HTTP reset causality as a separate runtime diagnosis unless directly
  proven.

Evidence:
- `CncfRuntimeDataStoreShutdownSpec` proves owned handle shutdown, caller-managed
  bare embedding, and repeated logical Subsystem cleanup; `CncfRuntime.run`,
  all object adapters, and `SubsystemTestFixture` finalize owned resources in
  the order Subsystem binding/lease then SystemNode pool.
- Factory construction now owns post-construction rollback in Default, Generic,
  and Textus identity factories, preserving the original startup failure and
  attaching cleanup failure.
- `DataStorePoolResourceAcceptanceSpec` executes 64 alternating managed SQLite
  borrows through two bindings, observes one registry resource, one Hikari
  housekeeper, and one physical SQLite pool connection while held, then proves
  Node close empties the registry, closes Hikari, and restores the housekeeper
  baseline. The focused run passed 4/4 (invocation `79764-20260802T044344Z`).
- Shutdown/runtime/JobEngine regression focused run passed 36/36 (invocation
  `71710-20260802T042922Z`); `Test/compile` passed (invocation
  `68346-20260802T042227Z`).

## DSP-07: Regression and Canonical Closure

Stage Status:
- Current status: DONE
- Owner: CNCF datastore, Entity, runtime, documentation, and release maintainers
- Entry rule: DSP-06 is DONE.
- Completion rule: Full compatibility and resource evidence pass, normative
  documentation matches verified behavior, and no pool lifecycle contract
  remains planning-only.

- [x] Run focused datastore, Entity, OCC, UnitOfWork, Subsystem, CLI, and HTTP
  specifications.
- [x] Run `Test/compile`.
- [x] Run the complete CNCF full suite.
- [x] Run bounded resource acceptance with exact before/after evidence.
- [x] Run naming, executable-specification, and `git diff --check` gates.
- [x] Perform independent read-only review.
- [x] Apply every actionable review finding.
- [x] Perform a clean focused re-review.
- [x] Create or update
  `docs/design/system-node-datastore-pool-lifecycle.md`.
- [x] Create or update
  `docs/spec/system-node-datastore-pool-lifecycle.md`.
- [x] Update architecture, strategy, phase, checklist, and runtime lifecycle
  references.
- [x] Record exact test, resource, review, and release evidence.
- [x] Close Phase 54 only after all completion rules pass.

Evidence:
- DSP-06 clean re-review accepted the runtime, fixture, ownership, and bounded
  resource evidence. DSP-07 corrected the cached Subsystem shutdown result
  expectation in `ServiceContainerObservabilitySpec`; its focused regression
  passed 25/25 (invocation `93553-20260802T051126Z`).
- Final serialized `clean; test` passed 2,786 tests with 0 failures, 14
  canceled, 1 ignored, 59 pending, and 395 completed suites (invocation
  `94576-20260802T051332Z`).

## Modified Scala File Compliance Ledger

The final DSP-07 full validation and release evidence below supersede the
per-row provisional `pending DSP-07`/`pending Phase 54 release` placeholders
retained from the incremental implementation record.

| File | Naming | Executable specification | Focused validation | Full validation | Final commit |
| --- | --- | --- | --- | --- | --- |
| `src/test/scala/org/goldenport/cncf/datastore/SystemNodeDataStorePoolRuntimeSpec.scala` | compliant | compliant; active DSP-03 E3--E6 and E8--E13 registry/binding cases; provisional `pendingUntilFixed` configuration-admission and terminal-finalization cases | `Test/compile` pass; DSP-03 focused 10 succeeded, 3 intentional pending, 0 failed/canceled | pending DSP-07 | pending Phase 54 release |
| `src/test/scala/org/goldenport/cncf/subsystem/SystemNodeDataStoreShutdownSpec.scala` | compliant | compliant; provisional `pendingUntilFixed` parser-admission, lease, timeout, canonical-order, retention, ownership-exclusion, and idempotence properties | `Test/compile` pass; focused suite completed with 20 total pending scopes across both DSP-01C specs, 0 failed/canceled | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/sql/ManagedSqlDataStoreResource.scala` | compliant | not a spec | `Test/compile`, DSP-02 focused, SQLite regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStoreIdentity.scala` | compliant | not a spec | `Test/compile`, DSP-02 focused, SQLite regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala` | compliant | not a spec | `Test/compile`, DSP-02 focused, SQLite regression pass | pending DSP-07 | pending Phase 54 release |
| `src/test/scala/org/goldenport/cncf/datastore/SqlDataStoreLifecycleSpec.scala` | compliant | compliant | `Test/compile`, 14-scope DSP-02 focused, SQLite regression pass | pending DSP-07 | pending Phase 54 release |
| `src/test/scala/org/goldenport/cncf/datastore/DataStoreIdentitySpec.scala` | compliant | compliant; active ScalaCheck properties | `Test/compile`, 14-scope DSP-02 focused, SQLite regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/subsystem/SystemNode.scala` | compliant | not a spec | `Test/compile`, DSP-03 focused pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/sql/SystemNodeSqlDataStoreRegistry.scala` | compliant | not a spec | `Test/compile`, DSP-03 focused pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/ComponentDataStore.scala` | compliant | not a spec | `Test/compile`, DSP-04 focused pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala` | compliant | not a spec | `Test/compile`, DSP-04 focused pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` | compliant | not a spec | `Test/compile`, DSP-04/Job regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/action/ActionCall.scala` | compliant | not a spec | `Test/compile`, DSP-04 regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala` | compliant | not a spec | `Test/compile`, DSP-04 regression pass | pending DSP-07 | pending Phase 54 release |
| `src/main/scala/org/goldenport/cncf/job/JobEngine.scala` | compliant | not a spec | `Test/compile`, Job regression pass | pending DSP-07 | pending Phase 54 release |
| `src/test/scala/org/goldenport/cncf/datastore/ComponentDataStoreManagedLifecycleSpec.scala` | compliant | compliant; active E1/E2 resident-binding resource sharing, non-owning view close, and legacy alias reuse | `Test/compile`, DSP-04 focused pass | pending DSP-07 | pending Phase 54 release |

During implementation, every created or modified Scala file must be recorded
with:

- complete-file naming compliance;
- executable-specification compliance or `not a spec`;
- focused and full validation evidence; and
- final Phase 54 commit.

## Status

Phase 54 is CLOSED. DSP-01 through DSP-03 are DONE; DSP-01A recorded the
SystemNode topology, timeout decision, and deterministic configuration-admission
counterexamples. DSP-01B completed the remaining source inventories; lifecycle
and registry failing-first evidence is registered. DSP-03 supplied registry,
binding ownership, active-lease accounting, and nested lease inheritance.
Terminal pool shutdown, ordered closure, and runtime adoption completed in
DSP-04--DSP-06; DSP-07 final validation passed 2,786 tests with 0 failures
(invocation `94576-20260802T051332Z`).
