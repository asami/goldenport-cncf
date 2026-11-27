For classification and ordinary execution, see
[Scala test classification and default execution](#scala-test-classification-and-default-execution).
See [CNCF test selection and framework bindings](test-selection-standard-annotations.md)
for metadata and discovery responsibilities.

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
5. CNCF regression and explicit acceptance
----------------------------------------------------------------------

Separate CNCF provider integration cases from the ordinary SQLite,
in-memory and fake-provider cases. Place CNCF suites that require independently
running MySQL, PostgreSQL or Docker in explicitly named `@DoNotDiscover` suites
and apply the shared external-service classification. Retain provider opt-ins
and provision only after suite selection so `beforeAll` cannot start a container
during ordinary validation. Keep a scenario that compares several real
database providers together in an explicit suite. Preserve real-provider
assertions and report unexecuted coverage as NOT RUN.

Job management uses
five representative suites: InMemoryJobEngineSpec, JobPrimaryResultSpec,
JobQueryReadModelSpec, DurableJobLifecycleWriteBridgeSpec and JobControlComponentSpec.
These cover submission/completion, primary versus async results, queries, durable
lifecycle writes and the public control component.

Detailed Job suites that were previously opt-in solely for coverage and budget
reasons use `@DoNotDiscover`. Retain the five ordinary representatives. Apply Slow
to the existing 5,000-level and 2,048-level individual cases in DurableJobRecordSpec
and DurableJobTerminalProjectionSpec for their execution cost. Keep their
assertions, depths and property counts intact.

Use the standard SBT commands below through the repository's prescribed SBT
execution route:

```text
test
testOnly org.goldenport.cncf.job.DurableJobStoreSpec
testOnly org.goldenport.cncf.job.DurableJobRecordSpec
testOnly org.goldenport.cncf.job.DurableJobRecordSpec -- -n org.scalatest.tags.Slow
```

`test` also excludes the existing CNCF ManualSpec/TimingSpec tags. The current
`testOnly` configuration uses empty default test options. A named suite can run
all its cases when the invocation omits case filters. Report actual executed
counts for the selected Job coverage.

Run explicitly named affected suites for Job contract changes and the selected
detailed Job suites for Job-wide acceptance or changes spanning engine,
durability, recovery and process boundaries. Report detailed Job acceptance
as NOT RUN when it was outside the actual selection.

----------------------------------------------------------------------
6. ADMISSION labels (updated 2026-10-26)
----------------------------------------------------------------------

The existing CNCF ADMISSION route reads canonical Validation/Test Metadata at
the executable specification. Local specifications cover metadata interpretation,
discovery selection and exact operation binding.

Through the prescribed SBT execution owner, use:

```text
admissionTestSelection
testAdmission
```

The first command inspects the selected suite/operation arguments. The second
uses the same canonical discovery/composition/query and standard `testOnly` with
exact operation names. Adding a labelled specification requires no second manual
registration. Intentional DoNotDiscover helpers stay excluded. An empty selection
does not constitute a successful acceptance run.

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

## Scala Test Classification and Default Execution

CNCF defines its test policy here under [Test Policy in RULE.md](../../RULE.md#test-policy)
while the shared ai-directive classification section awaits adoption.

Ordinary `sbt test` excludes Slow tests, `@DoNotDiscover` suites and the legacy
ManualSpec and TimingSpec tags. Routine `ADMISSION` selects short local
acceptance cases and applies the ordinary exclusions. Resolve selection before
fixture setup. Explicitly named suites can run with their declared prerequisites.
Keep real external services out of ordinary setup. In-memory, embedded SQLite
and small test-owned loopback fixtures remain eligible for ordinary runs.

Use ScalaTest Slow for costly or real external-service tests and
`@DoNotDiscover` for suites that require deliberate selection. Use both when
both reasons apply. Record actual prerequisites separately and configure
process isolation in the build. The
[migration plan](../phase/test-classification-metadata-plan.md) tracks how to
record `HEAVY`, `EXTERNAL_SERVICE`, `MANUAL`, `TIMING` and `FORK` as reasons
through the existing suite and case declaration providers. The
[implemented V1 purpose vocabulary](validation-test-metadata.md#explicit-only-suites-and-duration)
continues to apply to the existing ABI.
