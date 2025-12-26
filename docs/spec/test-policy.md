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
