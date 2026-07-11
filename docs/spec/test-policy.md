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

Unit tests in CNCF serve as structural reservations.

Characteristics:
  - ScalaTest AnyWordSpec is used
  - Tests are intentionally marked as pending
  - No concrete behavior is asserted
  - Tests compile and pass by design

This prevents premature specification locking
during architectural exploration.

Classic test-first TDD is intentionally avoided
in early CNCF development.

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

Test-owned datastore replacement should use logical CNCF datastore keys:

  runtime.datastore.type/path
  components.<component>.datastore.<name>.type/path

The public test descriptor contract is `type: local` plus `path`, not
SQLite-specific implementation keys.

----------------------------------------------------------------------
5. Use of TDD
----------------------------------------------------------------------

TDD MAY be applied selectively in later stages,
once core structure stabilizes.

Typical use cases include:
  - boundary condition refinement
  - failure handling
  - regression prevention

----------------------------------------------------------------------
6. Summary
----------------------------------------------------------------------

- Unit tests reserve structure (pending-first)
- Integration and scenario tests validate execution
- Architectural freedom is prioritized early
- Behavior is fixed only after stabilization

======================================================================
