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

Status: PLANNED

### Tasks

- [ ] Add purpose-based random internal DSL helpers.
- [ ] Add ID creation helpers through `IdGenerationContext`.
- [ ] Construct ID generation from selected clock and dedicated ID entropy.
- [ ] Derive deterministic per-invocation/per-collection sequences.
- [ ] Migrate observable builtin UUID and entity-ID creation paths.
- [ ] Preserve opaque `CanonicalId` and `EntityId` contracts.
- [ ] Add CallTree metadata without exposing seeds or token values.

### Acceptance Criteria

- ID generation does not consume domain random streams.
- Controlled IDs reproduce without colliding across invocation ordinals.
- Component behavior does not access profile internals directly.

## ED-05: Unified Time and Job/Event Scheduling

Status: PLANNED

### Tasks

- [ ] Adapt `JobTimeSource` to the runtime-selected execution time.
- [ ] Add a manual clock and operational scheduler pair.
- [ ] Add test-harness `advanceBy` and `runUntilIdle` controls.
- [ ] Drive delayed Job start, retry, due time, and observable await timeout
      from the selected policy.
- [ ] Preserve monotonic performance measurement independently from wall time.
- [ ] Route async Event reception through the same Job scheduler.
- [ ] Preserve immediate same-transaction synchronous Event handling.
- [ ] Replace real sleeping in controlled Job/Event executable specs.

### Acceptance Criteria

- Advancing manual time makes due work eligible without host sleeping.
- Equal due-time ordering is deterministic.
- Phase 31 does not introduce general scheduling semantics.

## ED-06: Resolved Environment Assumptions

Status: PLANNED

### Tasks

- [ ] Resolve locale, timezone, charset, line separator, math, and i18n at
      bootstrap.
- [ ] Define an allowlisted immutable environment snapshot.
- [ ] Carry generic assumptions through core `ExecutionContext.Core`.
- [ ] Keep secrets and undeclared environment values out of diagnostics.
- [ ] Add propagation and immutability executable specifications.

### Acceptance Criteria

- Component-visible assumptions are stable for one ActionCall.
- Ambient environment access is restricted to bootstrap/provider boundaries.

## ED-07: Deterministic Ordering, Migration, and Enforcement

Status: PLANNED

### Tasks

- [ ] Fix ordering guarantees for Job queues, due timers, retries, and async
      Event continuations owned by CNCF.
- [ ] Define strict concurrent named-stream behavior.
- [ ] Classify direct time, UUID, random, sleep, environment, property,
      filesystem, and executor access by semantic boundary.
- [ ] Migrate component-visible and runtime-semantic occurrences.
- [ ] Preserve host bootstrap and monotonic diagnostic uses where appropriate.
- [ ] Extend CAR lint/review/developer guidance for ambient-state access.

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
