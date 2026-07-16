======================================================================
Test Specification — Cloud Native Component Framework (CNCF)
======================================================================

Status: Normative
Scope: cloud-native-component-framework

----------------------------------------------------------------------
1. Purpose
----------------------------------------------------------------------

This document defines the test policy for the
Cloud Native Component Framework (CNCF).

CNCF is an asynchronous, event-driven framework whose architecture
is subject to exploration and evolution.

Tests are therefore used to reserve structure and intent,
not to prematurely fix behavior.

----------------------------------------------------------------------
2. Relationship to SimpleModeling Library
----------------------------------------------------------------------

CNCF inherits the test philosophy defined by simplemodeling-lib,
but applies it differently due to architectural uncertainty
and non-deterministic execution.

----------------------------------------------------------------------
3. Unit Test Policy (src/test)
----------------------------------------------------------------------

Tests under `src/test` are Executable Specifications by default.

Characteristics:
  - substantial behavior specifications use ScalaTest `AnyWordSpec`
  - behavior is expressed with Given / When / Then boundaries
  - Property-Based Testing is used for value spaces and invariants
  - expectations reserve observable contracts rather than implementation
    details
  - pending tests are allowed only for explicitly unfinished exploratory work

Stable behavior must be asserted directly. Tests must not remain pending merely
to avoid fixing an implemented contract.

----------------------------------------------------------------------
4. Integration and Scenario Tests
----------------------------------------------------------------------

Execution-level tests are separated from unit tests.

Locations:

  src/it/integration
    - technical integration tests
    - wiring and interaction checks

  src/it/scenario
    - use case scenarios
    - end-to-end execution paths
    - BDD style may be used (optional)

BDD is treated as a description technique,
not as an architectural or directory layer.

Modern CNCF executable specifications are also used for stabilized runtime
behavior. Once a behavior is intentionally fixed, tests should assert it
directly using Given / When / Then structure rather than leaving it as a
pending reservation.

Component integration tests that need runtime wiring or resource replacement
must use explicit CNCF test surfaces:

  test.yaml / test.json
    - test-only startup descriptor
    - loaded only when explicitly specified
    - may override runtime config, assembly wiring, SPI provider selection,
      runtime datastore, and component datastores

  cncf test
    - test launcher wrapper
    - normalizes into ordinary server/client/command/script execution
    - provides explicit test home support without changing JVM user.home

Tests MUST NOT rely on JVM `-Duser.home` mutation to isolate CNCF state.
Use `cncf test --home ...` or `cncf test --temporary-home` when a test needs
an isolated CNCF home. Most component integration tests should instead use
runtime overlay mode: keep normal runtime/repository resolution and replace
only test-owned resources through `test.yaml`.

Resource-reference tests that do not need assembly/configuration coverage
should use `ResourceAccessTestProfile` through
`ExecutionContext.withResourceAccessTestProfile`. The profile provides
deterministic in-memory URL, Textus URN, and external URN providers; tests must
not substitute host files or direct network clients for this surface.

Test-owned datastore replacement should use logical CNCF datastore keys:

  runtime.datastore.type/path
  components.<component>.datastore.<name>.type/path

The public test descriptor contract is `type: local` plus `path`, not
SQLite-specific implementation keys.

Controlled execution tests may declare an explicit execution profile:

```yaml
kind: test-descriptor

execution:
  profile: controlled
  key: executable-spec-run
  time:
    mode: manual
    start-at: 2026-07-28T09:00:00Z
  random:
    mode: seeded
    seed: executable-spec-seed
  ids:
    mode: deterministic
  scheduler:
    mode: manual
  ordering:
    mode: deterministic
```

The block normalizes to the canonical execution configuration defined in
`docs/design/execution-determinism.md`. `controlled` is valid only through an
explicit test descriptor, `cncf test`, or an in-process executable-spec
builder. CNCF must reject it during ordinary production startup.

The controlled test API may advance time and run eligible CNCF work until idle.
Component code must not receive the advance control. Executable specifications
for retry, delay, timeout, and async Event behavior should use controlled time
instead of host sleeps when the behavior is owned by CNCF.

Replay specifications must create two independent runtime graphs from the same
controlled profile and invocation sequence. They should compare both business
data and CNCF-owned semantic evidence such as generated IDs, domain/Event
timestamps, retry due time, Task/Event ordering, and the sanitized profile
fingerprint. Raw trace, span, and correlation identifiers are diagnostic
metadata and should be removed from the semantic comparison. Reusing one
mutable runtime is not sufficient replay evidence.

----------------------------------------------------------------------
5. Use of TDD
----------------------------------------------------------------------

TDD MAY be applied selectively in later stages,
once the behavior boundary is sufficiently understood.

Typical use cases include:
  - boundary condition refinement
  - failure handling
  - regression prevention
  - property-based validation of stable invariants

Repository work follows rules, executable specification, design, then code.
Exploratory pending specifications must be replaced by executable behavior
specifications when the contract is promoted to design/spec.

----------------------------------------------------------------------
6. Summary
----------------------------------------------------------------------

- Tests are executable behavior specifications by default
- Integration and scenario tests validate execution
- Property-Based Testing covers value spaces and invariants
- Explicit controlled profiles isolate observable time, random, ID, and
  CNCF-owned async ordering

======================================================================
