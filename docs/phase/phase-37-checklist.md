# Phase 37 - Downstream Runtime Boundary Adoption Checklist

This checklist tracks the downstream adoption of the closed Phase 36 runtime
capability surface. The summary dashboard is `phase-37.md`.

## BA-01: Contract and Version Freeze

Status: DONE

- [x] Confirm Phase 36 closure evidence and select the CNCF runtime version.
- [x] Map each CBD Support ambient dependency to an existing CNCF capability.
- [x] Record a separately scoped CNCF proposal for any genuine missing
  capability; do not create an application-specific bypass.

Evidence:

- CBD Support selected CNCF `0.5.1-SNAPSHOT` and mapped clock,
  configuration, resource-tree, WorkArea, and process execution to the closed
  Phase 36 contracts. No missing CBD-specific CNCF capability was required.

## BA-02: Clock and Configuration Migration

Status: DONE

- [x] Replace operational host-clock access with the execution-time capability.
- [x] Declare typed public configuration and opaque secret-reference keys.
- [x] Prove deterministic test configuration and safe structured failure paths.
- [x] Prove component behavior cannot override protected runtime declarations.

Evidence:

- CBD Support commit `4d1b59b` binds runtime construction to the ActionCall
  execution clock and declared configuration. Deterministic factory and
  runtime-boundary specifications cover unavailable and protected values.

## BA-03: Resource-Tree and WorkArea Migration

Status: DONE

- [x] Bind registered development/CAR trees as named admitted resource trees.
- [x] Replace direct host-path use with bounded snapshot/materialization input.
- [x] Prove traversal, symbolic-link, unknown-tree, and limit violations remain
  denied before provider launch.

Evidence:

- Runtime-boundary specifications and standalone fixtures use named admitted
  snapshots and WorkArea-relative input. Commit `2d0be7f` additionally proves
  sequential and concurrent ActionCalls cannot share admitted local inventory.

## BA-04: External Evidence Provider Migration

Status: DONE

- [x] Register the logical Process Execution capability, fixed command
  template, fixed environment policy, limits, and grant in runtime assembly.
- [x] Submit only logical request arguments, admitted WorkArea input, and
  declared output artifacts from CBD Support behavior.
- [x] Map neutral terminal process outcomes in the CBD-owned adapter without
  adding CBD/Cozy/CAR Review types to CNCF.

Evidence:

- The CBD-owned Cozy adapter consumes CNCF `ProcessExecutionResult` values and
  covers success, rejection, timeout, cancellation, launch failure,
  non-zero exit, and bounded-output failures without direct process launch.

## BA-05: Deterministic Evidence and Lint

Status: DONE

- [x] Add fake configuration, resource-tree, and Process Execution executable
  specifications with no live external tool requirement.
- [x] Run CBD Support CAR lint and record resolved/remaining boundary findings.
- [x] Run focused CNCF regression specifications for the consumed contracts.

Evidence:

- CBD Support passed 227 tests, standalone and CBD/SIE SAR integration, CAR
  ABI governance, and CAR lint. Lint retains only the attributed
  `FUTURE-CBD-ABI-RELEASE-01` first-release baseline warning.
- Phase 36 closure already provides the focused and full CNCF regression
  evidence for the consumed capability contracts; Phase 37 added no CNCF
  runtime API or behavior.

## BA-06: Closure

Status: DONE

- [x] Record selected runtime version and downstream migration evidence.
- [x] Relocate production secret providers, remote/container execution, CAR
  ABI publication, and deployment automation to explicit follow-up work.
- [x] Update strategy and close Phase 37 after scoped review and validation.

Evidence:

- CBD Support commits `4d1b59b` and `2d0be7f`, its closed Phase 6/7 records,
  and the Phase 37 closure record provide the implementation and validation
  trail. Final scoped review found no actionable Phase 37 issue.
