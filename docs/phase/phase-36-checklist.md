# Phase 36 - Component Runtime Boundary Capabilities Checklist

This checklist is the Phase 36 state ledger. The summary dashboard is
`phase-36.md`.

## RB-01: Contract Audit and Scope Freeze

Status: IN_PROGRESS

- [ ] Inventory reusable Phase 31 execution-time contracts.
- [ ] Inventory Phase 33 resource-read contracts and identify tree gaps.
- [ ] Inventory Phase 34/35 Process Execution input, WorkArea, and lifecycle
  contracts, including concurrent fixed-input work.
- [ ] Freeze typed configuration, secret-reference, resource-tree, and process
  materialization responsibilities without application-specific types.
- [ ] Promote settled decisions to normative design/spec documents.

## RB-02: Component Execution-Time Contract

Status: OPEN

- [ ] Specify the existing ExecutionContext clock as the component runtime
  time authority.
- [ ] Distinguish stable business time from monotonic observability duration.
- [ ] Prove controlled-profile time through a component-facing internal DSL.
- [ ] Prove the normal runtime path does not fall back to a host clock.

## RB-03: Declared Typed Component Configuration

Status: OPEN

- [ ] Define typed key, decoder, optional/required result, and access contracts.
- [ ] Define component/subsystem/runtime precedence and safe provenance.
- [ ] Reject undeclared, missing required, malformed, and policy-denied values
  with structured `Conclusion` failures.
- [ ] Prevent action/request properties from overriding protected runtime
  declarations.
- [ ] Provide deterministic test-descriptor coverage.

## RB-04: Opaque Secret References

Status: OPEN

- [ ] Define an opaque secret-reference value with no public value accessor.
- [ ] Classify public configuration, confidential configuration, and secret
  references.
- [ ] Keep secret resolution inside authorized runtime providers/drivers.
- [ ] Prove diagnostics and failures do not expose secret references or values.
- [ ] Relocate concrete Vault/cloud secret providers outside Phase 36.

## RB-05: Admitted Read-only Resource Trees

Status: OPEN

- [ ] Define logical tree identity/reference, limits, entries, and immutable
  snapshot contracts.
- [ ] Bind named physical roots only at runtime configuration/provider
  boundaries.
- [ ] Enforce deterministic ordering, depth, count, per-file, total-byte,
  traversal, and symlink policies.
- [ ] Provide an in-memory provider for executable specifications.
- [ ] Add payload-safe CallTree and metrics diagnostics.

## RB-06: Process WorkArea Tree Materialization

Status: OPEN

- [ ] Define logical admitted tree input separately from fixed bounded input
  files.
- [ ] Require Process program admission to allow each tree identity.
- [ ] Allow request limits to narrow, never broaden, runtime tree limits.
- [ ] Materialize the tree inside the runtime-owned WorkArea boundary.
- [ ] Reject arbitrary component/request paths before filesystem access.
- [ ] Preserve Phase 35 UnitOfWork cleanup on success, failure, timeout, and
  cancellation.

## RB-07: Provider-neutral External-tool Pattern

Status: OPEN

- [ ] Verify fixed command templates, admitted arguments/environment, bounded
  inputs, and declared outputs through a fake Process driver.
- [ ] Keep timeout, cancellation, launch, output-limit, and non-zero-exit
  outcomes distinct until provider adaptation.
- [ ] Prove provider result conversion without a live Cozy installation.
- [ ] Keep CNCF contracts free of CBD Support and CAR Review types.

## RB-08: Documentation and Developer Guidance

Status: OPEN

- [ ] Update execution-context and configuration design documents.
- [ ] Update resource-reference and Process Execution specifications.
- [ ] Update component developer guidance and test policy.
- [ ] Record safe configuration, secret, tree, and external-tool examples.
- [ ] Prepare the separately scoped CBD Support migration handoff.

## RB-09: Verification and Closure

Status: OPEN

- [ ] Run focused execution-context, configuration, resource, and Process
  Execution executable specifications.
- [ ] Run `sbt --batch Test/compile` and the full CNCF test suite.
- [ ] Run scoped review and resolve actionable findings.
- [ ] Update strategy/phase closure evidence.
- [ ] Relocate production secret providers, remote/container execution, CBD
  migration, and CAR ABI publication to explicit downstream work.
