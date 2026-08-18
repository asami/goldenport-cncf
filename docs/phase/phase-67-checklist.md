# Phase 67 Checklist - CNCF Testability and Explicit Test Invocation

status=planned
phase=[Phase 67 - CNCF Testability and Explicit Test Invocation](phase-67.md)

This checklist is the authoritative Phase 67 state ledger after Phase 67
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 57.5 closes.

## TST-01: Inventory and Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF CLI, configuration, execution-profile, Component-runtime, SPI, observability, and test-kit maintainers
- Update rule: Update only after every inventory boundary and failing-first acceptance identity is recorded.

- [ ] Inventory `cncf test`, ordinary command invocation, argument
  normalization, runtime configuration bootstrap, and current descriptor-path
  options/compatibility aliases.
- [ ] Inventory `RuntimeTestDescriptor`, configuration source precedence,
  provenance, duplicate detection, diagnostics, Help, and CLI rendering.
- [ ] Inventory test-home, temporary-home, runtime/component datastore,
  work-area, cleanup, and JVM `user.home` isolation behavior.
- [ ] Inventory controlled/standard profiles, time, ID, random, scheduler,
  ordering, locale, timezone, charset, and environment-assumption controls.
- [ ] Inventory Component stub/double implementations, factory lifecycle,
  static/global reset hazards, in-memory providers, and the boundary between
  framework-owned and application-owned doubles.
- [ ] Inventory SPI socket/provider binding, descriptor test overrides,
  provider selection, deterministic recording, replacement, reset, and
  cross-test isolation behavior.
- [ ] Inventory Component CAR/development-directory/assembly/repository source
  resolution and live process/network/filesystem/provider fixture seams.
- [ ] Inventory test CallTree, diagnostics, metrics, retained execution,
  configuration provenance, result evidence, redaction, and cleanup signals.
- [ ] Classify every candidate as parameterizable common control,
  descriptor-only structured input, framework test-double/test-kit contract,
  downstream fixture concern, or explicit non-goal.
- [ ] Register exact failing-first framework, CLI, CAR, development-directory,
  Component stub, SPI/provider, observability, and downstream acceptance
  identities.

Evidence:
- Pending.

## TST-02: Parameterized Invocation and Test-Double Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF CLI, RuntimeConfig, Component/SPI, and testability contract maintainers
- Update rule: Update only after the public vocabulary and all normalization, selection, lifecycle, and rejection rules are fixed.

- [ ] Define the exact public `cncf test` grammar for ordinary explicit test
  execution while preserving ordinary production command grammar.
- [ ] Define stable typed parameter families for test home, logical
  runtime/component datastores, execution profile/key, deterministic controls,
  supported source selection, and admitted common double selection.
- [ ] Define defaults, required combinations, repeatability, duplicate rules,
  path/value limits, and parameter-to-configuration/descriptor projection.
- [ ] Define precedence among parameters, descriptor files, configuration and
  assembly sources, compatibility aliases, and ordinary runtime configuration.
- [ ] Define descriptor-only structured assembly/SPI/provider input and reject
  ambiguous parameter/descriptor mixtures or attempts to flatten nested
  topology into flags.
- [ ] Define Component/SPI double identity, declaration, construction,
  selection, deterministic recording, reset, cleanup, concurrency isolation,
  production exclusion, and safe failure semantics.
- [ ] Define bounded test observability facets for activation, effective
  controls, selected sources/doubles/providers, lifecycle, outcome, and
  cleanup; preserve existing redaction policy.
- [ ] Add grammar, normalization, precedence, conflict, double/SPI lifecycle,
  observability, compatibility, and no-ambient-activation specifications.

Evidence:
- Pending.

## TST-03: CLI Bootstrap and Test-Double Implementation

Stage Status:
- Current status: PLANNED
- Owner: CNCF CLI, runtime bootstrap, Component runtime, and SPI maintainers
- Update rule: Update only after public parsing, canonical projection, and double/provider behavior satisfy TST-02 acceptance.

- [ ] Implement admitted `cncf test` parameters and compatibility aliases
  without changing ordinary command semantics.
- [ ] Construct the canonical in-memory test configuration/descriptor view from
  admitted parameters without requiring a caller-created temporary YAML file.
- [ ] Route normalized inputs through existing configuration snapshots,
  execution-profile, Component admission, authorization, and failure handling.
- [ ] Implement/reconcile the framework-owned Component/SPI double registry or
  accepted equivalent without direct socket mutation or global mutable leakage.
- [ ] Preserve descriptor-file loading for structured overlays and reject
  unsupported flattening.
- [ ] Render Help, invalid combinations, double/provider selection, and
  structured failures with stable safe diagnostics.
- [ ] Add CLI/bootstrap, Component/SPI double, and command-level specifications.

Evidence:
- Pending.

## TST-04: Isolation and Deterministic Controls

Stage Status:
- Current status: PLANNED
- Owner: CNCF execution-profile, datastore, test-home, Component/SPI, and test-kit maintainers
- Update rule: Update only after every admitted control and double lifecycle is isolated and reproducible through parameter and descriptor paths.

- [ ] Align test-home and logical datastore parameter projection with existing
  descriptor behavior and test-owned cleanup.
- [ ] Align profile/time/ID/random/scheduler/ordering/assumption parameter
  projection with existing deterministic execution semantics.
- [ ] Prove equivalent parameterized and descriptor-backed controls produce the
  same effective profile and non-secret evidence.
- [ ] Prove Component and SPI doubles cannot leak selection, recordings,
  providers, mutable state, threads, or resources across independently created
  test runtimes.
- [ ] Prove no ambient user home, production datastore, host environment,
  hidden seed, fixture, remote access, or credential changes a selected test.
- [ ] Preserve cancellation, interruption, cleanup, and fatal-error boundaries.
- [ ] Add property, replay, isolation, concurrency, cleanup, and redaction
  executable specifications.

Evidence:
- Pending.

## TST-05: Composition, Fixture, and Observability Boundary

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component runtime, repository, resolver, SPI, provider, and observability maintainers
- Update rule: Update only after common parameterized cases and descriptor-only structured cases are separated by executable acceptance.

- [ ] Define safe parameterized Component source/CAR/development-directory
  controls and preserve descriptor ownership for nested assembly/SPI/provider
  topology.
- [ ] Prove test mode has no implicit remote resolution, CAR fallback, source
  build, publication, Component activation, provider selection, or credential
  inheritance.
- [ ] Define bounded explicit seams for local provider, process, network,
  filesystem, and datastore fixtures; live services remain opt-in integration
  evidence.
- [ ] Project deterministic, bounded, redacted CallTree, diagnostics, metrics,
  provenance, and cleanup evidence for parameterized execution and
  Component/SPI double selection.
- [ ] Prove test observability never retains secrets, full payloads, unbounded
  host paths, or stale execution/double state across isolated runtimes.
- [ ] Add resolver, composition, fixture, observability, and hostile-input
  specifications plus representative CAR/development-directory acceptance.

Evidence:
- Pending.

## TST-06: Guidance, Downstream Acceptance, and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF release, CLI, observability, documentation, and downstream integration maintainers
- Update rule: Update only after public evidence and consumer acceptance are verified against the accepted contract.

- [ ] Update test policy, execution design, developer guide, CLI Help, and
  test-kit guidance without making notes an implementation authority.
- [ ] Add end-to-end framework acceptance for parameterized and descriptor
  paths, compatibility, production exclusion, Component/SPI doubles, and
  observability/redaction.
- [ ] Add representative downstream public-command acceptance without a
  caller-created descriptor file and preserve descriptor-backed compatibility.
- [ ] Run focused CLI/bootstrap/profile/resolver/double/observability suites,
  required CAR/development acceptance, `Test/compile`, and the full CNCF suite.
- [ ] Complete clean review, all admitted review-fix/re-review work, naming and
  executable-specification checks, and `git diff --check`.
- [ ] Promote accepted behavior to design/specification and record exact
  commits, artifacts, validation evidence, and closure without reopening
  earlier phases.

Evidence:
- Pending.
