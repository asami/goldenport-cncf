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

Component runtime-boundary tests use explicit deterministic fixtures:

- declared configuration through test descriptor component/subsystem/runtime
  configuration or `ComponentConfigurationSources` in a focused specification;
- secret tests assert opaque `SecretReference` behavior for both declared
  operation-time configuration and component initialization parameters and
  must not use a real credential value or provider;
- tree tests install `ResourceTreeAccess.inMemory(...)` through
  `ExecutionContext.withResourceTreeAccess` and assert limits/traversal policy;
- Process Execution tests use `ProcessExecutionTestProfile` and admitted
  logical snapshots/WorkArea-relative targets rather than a host process or
  developer directory.

Tests must prove that request/action properties do not override declared
component configuration and that an unadmitted tree or path never reaches a
Process Execution driver. A live external tool is integration evidence only;
it is not required for the framework executable specification.

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
5. Normal FULL and opt-in HEAVY (updated 2026-10-19)
----------------------------------------------------------------------

Normal repository `test` is FULL for its declared selection, not exhaustive Job
or external-service acceptance. FULL must not depend on an independently running
server or start/stop Docker containers, including MySQL and PostgreSQL. An
environment variable enabling live tests must not change this FULL boundary.
Keep in-memory providers, embedded SQLite and small test-owned loopback HTTP
servers running in the same JVM in FULL; loopback fixtures own their ports and
cleanup and must not assume a separately running server. Pure protocol/dialect
tests using fake providers remain FULL even when they mention an external DB.
This execution policy does not exclude HEAVY sources from Test compilation or
claim that normal build dependency resolution is offline.

External-service and Docker acceptance uses the existing ScalaTest Slow tag,
in addition to any existing explicit live-environment opt-in. Whole external
suites must be excluded before lifecycle setup, not canceled after contacting
the service. In mixed suites, tag only the live cases and acquire their external
resources inside those cases: a suite-wide beforeAll must not start Docker for
the remaining SQLite/in-memory cases. Tests whose one scenario compares several
real database providers remain HEAVY as a whole; standalone SQLite coverage stays
FULL. Preserve real-provider assertions and report unexecuted coverage as NOT RUN.

Job management uses
five representative suites: InMemoryJobEngineSpec, JobPrimaryResultSpec,
JobQueryReadModelSpec, DurableJobLifecycleWriteBridgeSpec and JobControlComponentSpec.
These cover submission/completion, primary versus async results, queries, durable
lifecycle writes and the public control component.

HEAVY uses ScalaTest's built-in `org.scalatest.tags.Slow` tag. Detailed Job suites
carry `@org.scalatest.tags.Slow`; mixed suites use
`taggedAs org.scalatest.tagobjects.Slow` on individual expensive cases. In
DurableJobRecordSpec and DurableJobTerminalProjectionSpec only the 5,000-level
and 2,048-level cases are HEAVY; their ordinary cases remain in FULL alongside
the five representatives. Other detailed Job suites, including integrations
outside the job package, are opt-in. This is a coverage/budget distinction, not
a claim that every individual case is slow. Keep assertions, depths and property
counts intact. New tests declare their category at the test definition; there is
no classification registry, package-name filter or custom FULL/HEAVY selection
command. The additive ADMISSION entry below uses canonical metadata.

Use the standard SBT commands below through the repository's prescribed SBT
execution route:

```text
test
testOnly * -- -n org.scalatest.tags.Slow
testOnly org.goldenport.cncf.job.DurableJobRecordSpec
testOnly org.goldenport.cncf.job.DurableJobRecordSpec -- -n org.scalatest.tags.Slow
```

`test` excludes Slow as well as the existing ManualSpec/TimingSpec tags.
`testOnly` retains its existing empty default testOptions: selecting a suite
without tag arguments runs all its cases, including HEAVY. `-n` selects tagged
cases; `-l` excludes them. A tag selection may match zero tests, so report actual
executed counts rather than treating a zero-test success as HEAVY coverage.

Run affected suites for Job contract changes and the explicit HEAVY selection
for Job-wide acceptance or changes spanning engine/durability/recovery/process
boundaries. HEAVY is never an automatic follow-up after FULL or every repair.
Unrelated routine FULL checks need not run it. Report omitted HEAVY as NOT RUN,
never passed or complete Job coverage. Explicit affected heavy acceptance
obligations remain applicable. This build policy is not a new metadata ABI or
an implementation of CNCF Phase 103 discovery.

----------------------------------------------------------------------
6. ADMISSION labels (updated 2026-10-26)
----------------------------------------------------------------------

ADMISSION uses canonical Validation/Test Metadata at the executable specification,
not a class-name registry or an inference from measured duration. Initial local
specifications cover metadata interpretation, discovery selection and exact
operation binding. Their declaration includes ADMISSION, FOCUSED and FULL.
This additive classification does not change existing FULL/HEAVY tags, exclusions,
fixtures, assertions or default `test`/explicit `testOnly` behavior.

Through the prescribed SBT execution owner, use:

```text
admissionTestSelection
testAdmission
```

The first command inspects the selected suite/operation arguments. The second
uses the same canonical discovery/composition/query and standard `testOnly` with
exact operation names. Adding a labelled specification requires no second manual
registration. Intentional DoNotDiscover helpers stay excluded. An empty selection
does not constitute a successful acceptance run. These commands do not migrate
legacy FULL/HEAVY selection to a new mechanism.

Existing runnable metadata fixtures with valid ADMISSION declarations also follow
their declared membership. Deliberately malformed discovery fixtures can produce
PARTIAL discovery; the adapter reports that limitation and canonical selection
excludes invalid/ambiguous rows. Success establishes the selected executed
operations only, not complete repository inventory or absence of other coverage.

The operating target is **one minute or less for the sum of all selected test
bodies**, excluding compilation, dependency resolution, generation, discovery,
startup, fixture setup/teardown and reporting/retention. Parallelism does not turn
this into wall-clock time or a separate budget per suite. It is a guideline,
not a fixed timeout, cancellation rule, assertion or automatic label change.
Successful results remain successful when the target is exceeded; review the
selection and cost without discarding coverage. Existing execution deadlines
remain independent and must not become 60 seconds because of this target.

Keep net body time distinct from suite/run duration in evidence. If the available
report cannot separate setup/teardown, report net time as unavailable or explicitly
use a conservative upper bound. Do not populate per-operation expectedDuration
with the aggregate budget or fabricate measurements. ADMISSION is a bounded
acceptance selection; affected FOCUSED and required FULL/HEAVY checks retain their
existing roles. This repository policy is not a global CNCF metadata ABI limit.

----------------------------------------------------------------------
7. Use of TDD
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
8. Summary
----------------------------------------------------------------------

- Tests are executable behavior specifications by default
- Integration and scenario tests validate execution
- Property-Based Testing covers value spaces and invariants
- Explicit controlled profiles isolate observable time, random, ID, and
  CNCF-owned async ordering

======================================================================
