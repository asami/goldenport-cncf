# Phase 37 - Downstream Runtime Boundary Adoption Checklist

This checklist tracks the downstream adoption of the closed Phase 36 runtime
capability surface. The summary dashboard is `phase-37.md`.

## BA-01: Contract and Version Freeze

Status: OPEN

- [ ] Confirm Phase 36 closure evidence and select the CNCF runtime version.
- [ ] Map each CBD Support ambient dependency to an existing CNCF capability.
- [ ] Record a separately scoped CNCF proposal for any genuine missing
  capability; do not create an application-specific bypass.

## BA-02: Clock and Configuration Migration

Status: OPEN

- [ ] Replace operational host-clock access with the execution-time capability.
- [ ] Declare typed public configuration and opaque secret-reference keys.
- [ ] Prove deterministic test configuration and safe structured failure paths.
- [ ] Prove component behavior cannot override protected runtime declarations.

## BA-03: Resource-Tree and WorkArea Migration

Status: OPEN

- [ ] Bind registered development/CAR trees as named admitted resource trees.
- [ ] Replace direct host-path use with bounded snapshot/materialization input.
- [ ] Prove traversal, symbolic-link, unknown-tree, and limit violations remain
  denied before provider launch.

## BA-04: External Evidence Provider Migration

Status: OPEN

- [ ] Register the logical Process Execution capability, fixed command
  template, fixed environment policy, limits, and grant in runtime assembly.
- [ ] Submit only logical request arguments, admitted WorkArea input, and
  declared output artifacts from CBD Support behavior.
- [ ] Map neutral terminal process outcomes in the CBD-owned adapter without
  adding CBD/Cozy/CAR Review types to CNCF.

## BA-05: Deterministic Evidence and Lint

Status: OPEN

- [ ] Add fake configuration, resource-tree, and Process Execution executable
  specifications with no live external tool requirement.
- [ ] Run CBD Support CAR lint and record resolved/remaining boundary findings.
- [ ] Run focused CNCF regression specifications for the consumed contracts.

## BA-06: Closure

Status: OPEN

- [ ] Record selected runtime version and downstream migration evidence.
- [ ] Relocate production secret providers, remote/container execution, CAR
  ABI publication, and deployment automation to explicit follow-up work.
- [ ] Update strategy and close Phase 37 after scoped review and validation.
