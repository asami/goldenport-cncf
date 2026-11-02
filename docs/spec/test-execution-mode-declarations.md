# Per-test execution modes and build integration

Decision date: 2026-11-02
Status: adopted extension contract; ABI/annotation and execution implementation pending
Authorities: [validation metadata](validation-test-metadata.md),
[standard test annotations](test-selection-standard-annotations.md),
[runtime-mode behavior](runtime-mode-security-defaults.md)

## Responsibility

| Owner | Responsibility |
| --- | --- |
| Executable Specification / Test Operation | Declare required execution modes and assertions for each selected mode |
| CNCF Validation Metadata and discovery | Validate and normalize declarations, inheritance and provenance; expose mode requirements with the exact logical test reference |
| Slice/project verification plan | Select tests and acceptance conditions; supply routine/nightly default modes for tests without a specific declaration |
| build.sbt and shared SBT integration | Bind the selected test/mode to actual target configuration and execute through the framework |
| sm-workflow | Select tests/mode variants, prepare permitted prerequisites, invoke the normal execution path and collect per-variant results |

No second membership/mode registry is maintained in build.sbt or sm-workflow.
Classification remains ADMISSION plus standard framework tags/annotations;
execution mode is an independent dimension, not another purpose/tag taxonomy.

## Declaration semantics

A Test Operation may declare one or more required target operation modes from
dev, test, demo and production. Reuse CNCF OperationMode: dev normalizes to
develop; normalized metadata uses the canonical values. This describes the
application under test, not SBT's Test configuration, host execution mode or
the mode in which the tested business feature is enabled.

- A specification/class declaration provides defaults. An explicit per-test
  declaration replaces that default; absence inherits. Keep the effective set
  and declaration origin in ordinary normalized discovery metadata.
- An explicit set is nonempty; reject unknown values or an explicit empty set.
  Normalize recognized aliases and deduplicate the resulting modes. Empty must
  not silently disable a test.
- If neither level declares modes, the selected verification policy supplies
  them: ordinarily dev, and dev/production for the declared nightly suite.
  Absence does not assert that a test has been qualified in every mode.
- Once a test is selected, all its explicitly required modes are execution
  variants required for that selected acceptance. The run policy cannot silently
  narrow them to its default. A requested variant conflicting with an explicit
  declaration is reported before setup; resolve the selected contract rather
  than execute an undeclared mode or drop required coverage.
- An explicitly scoped exploratory run may execute a subset of declared variants
  and retain useful partial evidence. It does not rewrite the declaration or
  complete acceptance that requires the remaining variants.
- Adding/removing required modes changes verification coverage and is reviewed
  as a normal declaration change. A Slice can select references and requirements,
  but does not copy or override the authoritative per-test mode set.

For example: an ordinary mode-unspecified business test follows daily/nightly
defaults; a test-only integration declares test; demo assistance declares demo;
a guard scenario can declare test and production and assert enabled behavior in
test and disabled behavior in production. Test code owns those expected outcomes;
neither build.sbt nor sm-workflow invents them or demands identical results.

This is a planned metadata extension. The current V1 field list does not already
expose this declaration. Freeze the concrete typed field, annotation/CML mapping
and ABI version/required-semantics treatment in TM-01 before implementation.
Unsupported consumers must report unsupported required semantics, not ignore
the modes, reinterpret them as arbitrary business parameters or claim acceptance.
Preserve existing evidence and closed Phase 103 history.

## SBT execution connection

The shared build integration consumes the selected logical test, normalized
parameters and effective mode. build.sbt wires that integration to the project's
normal configuration entry; it does not contain a duplicate per-test mode table.
Use the existing typed SBT binding, prerequisite selection and framework selectors.
No free-form caller command, arbitrary environment map or second launcher.

Verify the mode reaches the actual application/test process, including forked
JVMs. Separate incompatible mode configurations using the supported build/process
lifecycle; do not mutate process-global configuration concurrently or leak a
previous run's mode. Ordinary native/SBT locking remains authoritative.

For suites containing differently declared tests, select the exact cases for
each mode with supported framework selectors. Batch compatible variants only
when test/mode attribution remains exact. Do not run an unrestricted suite and
label all its tests with the requested mode. If the framework cannot express
the selection, expose the capability gap before launching broader work.

Mode selection enables only declared behavior. Real external access still needs
the selected execution requirements, credentials, destinations and ordinary
authorization; Slow/DoNotDiscover and explicit live opt-ins remain in force.
Prepare resources after final test/mode selection, not merely after discovery.

## Result and acceptance contract

Identify a result by logical Test Operation, normalized parameters and effective
mode, within the existing selected repository/artifact and execution context.
Preserve actual executed mode, adapter/provider kind, outcome and duration.
Different modes are distinct variants and cannot deduplicate into one PASS.
Expected negative behavior can pass its assertions; an unexecuted variant is
not a successful negative test. Aggregate acceptance needs every required variant.

- TM-A01: class inheritance, explicit per-test replacement, absent defaults,
  dev/develop normalization, invalid/empty declarations and unsupported consumers.
- TM-A02: one discovery -> selection -> typed SBT -> real harmless test dispatch
  carries the actual requested mode, including the supported forked-process path.
- TM-A03: mixed-mode cases are selected exactly; consecutive modes do not leak,
  and distinct modes/parameters retain separate results and declared expectations.
- TM-A04: no external setup for excluded variants; missing prerequisites and
  unknown/failed outcomes stay explicit without fallback or aggregate PASS.
- TM-A05: dev-first daily, dev/production nightly defaults, explicit demo cases
  and focused test-specific cases use one shared declaration/execution route.

Use small connected fixtures first and reuse applicable prior evidence. No
universal four-mode suite replay, separate comparison ledger or live-provider
call is needed merely to verify selection and dispatch.

Delivery: [producer follow-up](../phase/test-execution-mode-plan.md).
