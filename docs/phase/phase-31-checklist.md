# Phase 31 - Deterministic Execution Capabilities Checklist

This checklist tracks the implementation and evidence for Phase 31. The phase
dashboard remains in `phase-31.md`.

## Checklist Rules

- Mark an item DONE only after implementation, executable specifications,
  focused validation, and review pass.
- Promote settled contracts to design/spec; keep alternatives and rationale in
  notes or journal records.
- Do not weaken scheduler, ActionCall/UoW, security, or provider boundaries to
  obtain deterministic tests.
- Do not classify a profile as replayable merely because it has a random seed.
- Preserve unrelated dirty work and validate each related repository as an
  independent commit unit.

## ED-01: Freeze the Execution-Profile Contract

Status: DONE

### Objective

Fix the terminology, ownership, configuration, compatibility, and
replayability rules before implementing new runtime types.

### Tasks

- [x] Record the investigation handoff and non-normative design exploration.
- [x] Add strategy item `9.28 Execution Determinism`.
- [x] Open Phase 31 and its detailed checklist.
- [x] Decide final core and CNCF type names.
- [x] Define the core/CNCF/runtime ownership matrix normatively.
- [x] Define `standard`, `seeded`, and `controlled` compatibility rules.
- [x] Define profile identity, invocation identity, and deterministic ordinal
      semantics.
- [x] Define canonical configuration keys and structured test-descriptor
      normalization.
- [x] Define replayability states and sanitization rules.
- [x] Promote settled decisions to execution-context, execution-clock, Job,
      timer-boundary, and test-policy documents.

### Acceptance Criteria

- No generic execution assumption is redefined in CNCF when core owns it.
- Mutable execution facilities have one runtime lifecycle owner.
- `controlled` cannot activate through ordinary production startup.
- Existing offset-clock configuration has an explicit compatibility rule.
- The remaining slices can implement against one unambiguous contract.

## ED-02: Seeded Named Random and Dedicated Entropy

Status: DONE

### Tasks

- [x] Extend core `RandomContext` with seeded advancing behavior.
- [x] Add stable purpose-based stream derivation.
- [x] Define and implement a dedicated entropy abstraction.
- [x] Keep domain, ID, retry, and security purpose families separate.
- [x] Preserve production-safe security entropy defaults.
- [x] Verify sequence reproducibility, stream independence, and numeric bounds.
- [x] Publish the validated core snapshot locally for CNCF consumption.

### Acceptance Criteria

- Same seed, invocation key, and purpose produce the same sequence.
- Calls in one purpose stream do not change another purpose stream.
- No seed or entropy material appears in diagnostics.

### Evidence

- Core `RandomContext` now provides stateful seeded named streams and retains
  production and fixed compatibility implementations.
- Core `EntropyContext` provides independent secure and explicitly
  deterministic purpose streams; `ExecutionContext.Core` carries random and
  entropy as separate capabilities.
- `RandomContextSpec`, `EntropyContextSpec`, and `ExecutionContextSpec` verify
  reproducibility, stream isolation, bounds, capability separation, and
  redacted representations with property-based executable specifications.
- The core `0.4.1-SNAPSHOT` passed focused specs and `Test/compile`, was
  published locally, and CNCF `Test/compile` passed against the new API.
- CNCF composition of profile seed, invocation identity, and purpose remains
  ED-03; core accepts the resulting seed without owning CNCF invocation
  semantics.

## ED-03: CNCF Profile Resolution and ActionCall Binding

Status: DONE

### Tasks

- [x] Add profile config and validated resolver models.
- [x] Add `test.yaml` / `test.json` structured `execution` shorthand.
- [x] Build core assumptions and CNCF controls from one resolver result.
- [x] Derive an ActionCall invocation identity at the execution boundary.
- [x] Rebind clock, random, IDs, scheduling, and profile metadata coherently.
- [x] Reject incompatible profiles with structured `Conclusion`.
- [x] Add context/config executable specifications.

### Acceptance Criteria

- One runtime profile produces one coherent ActionCall context.
- Runtime rebinding cannot mix facilities from different profiles.
- Test profiles are explicit and are not auto-discovered.

### Evidence

- `ExecutionProfileResolver` resolves `standard`, `seeded`, and test-only
  `controlled` as validated compatibility rows. Invalid dimensions and unsafe
  activation return the existing structured configuration `Conclusion`.
- `RuntimeTestDescriptor` normalizes a structured `execution` block into the
  canonical runtime keys. Ordinary bootstrap does not discover `test.yaml`;
  controlled activation requires an explicit descriptor, `cncf test`, or the
  in-process executable-spec resolver.
- `GlobalRuntimeContext` owns one `ExecutionProfileRuntime`. ActionCall
  admission assigns a monotonic invocation identity before call creation and
  derives clock, random, entropy, ID, and execution-control capabilities from
  one binding.
- Runtime rebinding replaces all profile-dependent capabilities together.
  Internal UnitOfWork rebinding preserves the already selected invocation
  binding instead of returning to a runtime-base profile.
- Scheduler and ordering modes are carried coherently as resolved controls in
  ED-03. Manual scheduling behavior and test-control operations remain ED-05.
- `ExecutionProfileSpec`, `ExecutionProfileBootstrapSpec`,
  `ExecutionContextSpec`, and the related runtime/config/command specs pass.
  The focused validation completed with 81 successful examples and four
  pre-existing pending examples.

## ED-04: Internal DSL and Capability-Based IDs

Status: DONE

### Tasks

- [x] Add purpose-based random internal DSL helpers.
- [x] Add ID creation helpers through `IdGenerationContext`.
- [x] Construct ID generation from selected clock and dedicated ID entropy.
- [x] Derive deterministic per-invocation/per-collection sequences.
- [x] Migrate observable builtin UUID and entity-ID creation paths.
- [x] Preserve opaque `CanonicalId` and `EntityId` contracts.
- [x] Add CallTree metadata without exposing seeds or token values.

### Acceptance Criteria

- ID generation does not consume domain random streams.
- Controlled IDs reproduce without colliding across invocation ordinals.
- Component behavior does not access profile internals directly.

### Evidence

- `BehaviorFeaturePart` provides protected purpose-based random helpers and
  runtime-namespace, collection-namespace, and opaque ID helpers. Component
  behavior does not receive seed, entropy, or profile-config access.
- `IdGenerationContext` now owns the selected clock, dedicated entropy, and
  collection/purpose-local sequences. Controlled profile bindings include the
  invocation ordinal in the derived ID seed, so repeated explicit invocation
  keys do not collide while independent runtimes replay the same sequence.
- Runtime-namespace Entity IDs remain available for ordinary EntityStore
  creation. Collection-namespace Entity IDs explicitly preserve the previous
  self-describing Blob, Association, and child-Entity binding contract.
- Blob registration and attachment, Association binding, child Entity binding,
  Aggregate edit contexts, and the message-delivery stub no longer create
  component-visible IDs through ambient UUID/EntityId defaults.
- `IdGenerationContextSpec`, `ExecutionCapabilityDslSpec`, and
  `ExecutionProfileSpec` verify property-based sequence replay, purpose and
  collection isolation, invocation-ordinal uniqueness, domain-random
  independence, EntityId round-trip behavior, and CallTree redaction.
- Focused Blob, Association, child Entity, Aggregate edit, and message-delivery
  regression suites pass. The focused execution-profile, context, clock, ID,
  and internal-DSL suites also pass with no failures; two pre-existing
  `ExecutionContextSpec` placeholders remain pending.

## ED-05: Unified Time and Job/Event Scheduling

Status: DONE

### Tasks

- [x] Adapt `JobTimeSource` to the runtime-selected execution time.
- [x] Add a manual clock and operational scheduler pair.
- [x] Add test-harness `advanceBy` and `runUntilIdle` controls.
- [x] Drive delayed Job start, retry, due time, and observable await timeout
      from the selected policy.
- [x] Preserve monotonic performance measurement independently from wall time.
- [x] Route async Event reception through the same Job scheduler.
- [x] Preserve immediate same-transaction synchronous Event handling.
- [x] Replace real sleeping in controlled Job/Event executable specs.

Implementation evidence:

- `ExecutionProfileRuntime` now owns the selected operational scheduler and
  exposes in-process test control only for a manual profile.
- `Subsystem` constructs its `InMemoryJobEngine` with the runtime-selected
  clock and scheduler when a global runtime scope is present; scope-less test
  fixtures retain the established realtime default.
- `ExecutionProfileJobSchedulingSpec` proves deterministic equal-due ordering,
  delayed start, delayed retry, and logical-time await timeout without host
  sleeping. Await completion and timeout use Job state notifications plus the
  selected operational timer, and completed waits cancel their timer
  registrations.
- Job slow-call capture uses monotonic elapsed time rather than logical wall
  time, so advancing a manual clock does not create false performance data.
- `EventReceptionSpec` proves that async same-Job continuation Tasks remain
  queued until the controlled runtime scheduler drains them, while local
  same-transaction Event handlers execute immediately without entering that
  queue.

### Acceptance Criteria

- Advancing manual time makes due work eligible without host sleeping.
- Equal due-time ordering is deterministic.
- Phase 31 does not introduce general scheduling semantics.

## ED-06: Resolved Environment Assumptions

Status: DONE

### Tasks

- [x] Resolve locale, timezone, charset, line separator, math, and i18n at
      bootstrap.
- [x] Define an allowlisted immutable environment snapshot.
- [x] Carry generic assumptions through core `ExecutionContext.Core`.
- [x] Keep secrets and undeclared environment values out of diagnostics.
- [x] Add propagation and immutability executable specifications.

### Evidence

- `ExecutionProfileResolver` validates and resolves locale, timezone, charset,
  line separator, math context, i18n policies, and allowlisted environment
  values before `GlobalRuntimeContext` construction.
- `ResolvedEnvironmentAssumptions` applies the resolved values to core
  `ExecutionContext.Core`, `VirtualMachineContext`, and `I18nContext`; runtime
  and invocation rebinding replace the complete assumption set coherently.
- `ExecutionProfileSpec` uses property-based checks for supported assumption
  combinations and executable scenarios for allowlist enforcement, ambient
  snapshot selection, value redaction, test-descriptor normalization, and
  ActionCall/UnitOfWork propagation.

### Acceptance Criteria

- Component-visible assumptions are stable for one ActionCall.
- Ambient environment access is restricted to bootstrap/provider boundaries.

## ED-07: Deterministic Ordering, Migration, and Enforcement

Status: IN PROGRESS

### Tasks

- [x] Fix ordering guarantees for Job queues, due timers, retries, and async
      Event continuations owned by CNCF.
- [x] Define strict concurrent named-stream behavior.
- [x] Classify direct time, UUID, random, sleep, environment, property,
      filesystem, and executor access by semantic boundary.
- [ ] Migrate component-visible and runtime-semantic occurrences.
- [x] Preserve host bootstrap and monotonic diagnostic uses where appropriate.
- [x] Extend CAR lint/review/developer guidance for ambient-state access.

### Current Evidence

- Controlled scheduling orders due timers by due instant and registration
  sequence, ready Jobs by priority and enqueue sequence, delayed retry by
  inherited priority and retry enqueue sequence, same-Job continuation Tasks by
  inherited priority and enqueue sequence, and Event subscriptions by priority
  and registration sequence.
- Existing Job/Event executable specifications cover equal-due Jobs, FIFO and
  priority queues, delayed retry, same-Job async continuation through the
  controlled scheduler, immediate same-transaction Event handling, and Event
  subscription ordering. The focused ED-07 run completed 81 tests without
  failure.
- The strict named-stream rule permits shared-stream calls only through
  CNCF-managed serialized execution. Arbitrary component-created thread or
  executor ordering is outside the replay contract.
- The source audit is recorded in
  `docs/notes/execution-determinism-capability-design.md`; it separates
  component/runtime semantics from provider, monotonic diagnostics,
  bootstrap/repository, transport, and compatibility-test concerns.
- Tag update and move timestamps now use the current operational execution
  clock and have property-based executable specification coverage.
- Canonical Event reception now derives semantic timestamps and generated saga
  boundaries from the bound execution profile. Transition lifecycle events use
  one execution-clock instant and profile-derived Event IDs. Property-based
  replay coverage fixes both contracts, and direct `ReceptionDomainEvent`
  construction now requires an explicit timestamp.
- EventStore record materialization now uses an explicit clock/ID capability.
  UnitOfWork fixes transactional Event records before prepare/commit, authorized
  EventBus publication uses the caller profile, and generic fallback Events no
  longer use ambient `Instant.now` or the constant `EventId.generate` value.
- Workflow instance creation now uses the invocation-bound ID generation
  namespace, entropy, and clock. Instance creation/update/history timestamps
  have no ambient default, and property-based executable specifications verify
  replay-stable IDs/timestamps plus collision-free per-invocation sequencing.
- Job input payload/input creation and JobDefinition create/update now require
  explicit semantic timestamps. Component command execution and Job Control
  operations supply the bound execution clock, Job input cleanup receives an
  explicit operational instant, and property-based executable specifications
  verify replay-stable lifecycle timestamps without ambient clock access.
- JobEngine Job submission and base, same-Job, and compensation Task boundaries
  now derive Job/Task IDs from the caller execution namespace, clock, and
  purpose-local entropy sequence. Property-based executable specifications
  verify equivalent-capability replay and collision-free per-invocation IDs.
- Component, Event reception, Workflow, and JCL execution boundaries now derive
  primary and compensation Action IDs from the same caller-bound capabilities.
  The replay specification covers Job, Task, and Action identity sequences.
- InformationSpace registration, mutation, validation, confirmation,
  publication, conflict handling, and Knowledge materialization now require the
  caller execution context and derive semantic timestamps from its clock.
  Information and field-event model constructors no longer supply ambient
  timestamp defaults, and property-based coverage verifies the lifecycle and
  materialized KnowledgeFrame timestamps.
- Knowledge working-set load and replacement now require the caller execution
  context. Successful and failed reload status derives `startedAt` and
  `completedAt` from one execution-clock instant, failed replacement preserves
  the previous indexed snapshot, and property-based coverage verifies replay
  across generated fixed instants.
- Entity working-set initialization now receives the clock selected by the
  active execution profile. Loading, ready, and failed status transitions use
  explicit instants from that clock, while context-free fixture transitions
  omit timestamps instead of reading ambient wall time. Property-based coverage
  verifies successful and failed lifecycle replay across generated instants,
  and startup preload coverage verifies the injected clock boundary.
- CAR lint and component developer guidance now report direct ambient clock,
  UUID/random, sleep, environment/property, host filesystem, thread, and
  executor access in component sources.
- Entity working-set policy admission/residency defaults and other remaining
  runtime model semantic defaults still require migration before ED-07 can be
  marked DONE.

### Acceptance Criteria

- CNCF-owned async ordering is reproducible under `controlled` mode.
- Migration does not blindly route host/bootstrap concerns through ActionCall.

## ED-08: Introspection, Replay Verification, and Closure

Status: PLANNED

### Tasks

- [ ] Expose sanitized profile mode, fingerprint, controlled dimensions, and
      replayability reasons.
- [ ] Add replayability states: replayable, partially-controlled,
      uncontrolled, and invalid.
- [ ] Verify profile diagnostics never contain seeds, credentials, or secret
      environment values.
- [ ] Run the end-to-end two-runtime replay scenario.
- [ ] Update component developer and test guidance.
- [ ] Run full related core and CNCF validation.
- [ ] Resolve final review findings and update strategy/history.
- [ ] Close Phase 31 without overstating external provider or distributed
      replay coverage.

### Closure Evidence Required

- Core seeded random/entropy executable specifications.
- CNCF profile/context/ID executable specifications.
- Job/Event manual-time and ordering executable specifications.
- Environment snapshot and redaction executable specifications.
- End-to-end replay scenario output comparison.
- Passing full tests in every dirty related core repository and CNCF.
- Final `git diff --check` and review with no blocking findings.
