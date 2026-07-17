# Phase 36 - Component Runtime Boundary Capabilities Checklist

This checklist is the Phase 36 state ledger. The summary dashboard is
`phase-36.md`.

## RB-01: Contract Audit and Scope Freeze

Status: DONE

- [x] Inventory reusable Phase 31 execution-time contracts.
- [x] Inventory Phase 33 resource-read contracts and identify tree gaps.
- [x] Inventory Phase 34/35 Process Execution input, WorkArea, and lifecycle
  contracts, including concurrent fixed-input work.
- [x] Freeze typed configuration, secret-reference, resource-tree, and process
  materialization responsibilities without application-specific types.
- [x] Promote settled decisions to normative design/spec documents.

## RB-02: Component Execution-Time Contract

Status: DONE

- [x] Specify the existing ExecutionContext clock as the component runtime
  time authority.
- [x] Distinguish stable business time from monotonic observability duration.
- [x] Prove controlled-profile time through a component-facing internal DSL.
- [x] Prove the normal runtime path does not fall back to a host clock.

## RB-03: Declared Typed Component Configuration

Status: DONE

- [x] Define typed key, decoder, optional/required result, and access contracts.
- [x] Define component/subsystem/runtime precedence and safe provenance.
- [x] Reject undeclared, missing required, malformed, and policy-denied values
  with structured `Conclusion` failures.
- [x] Prevent action/request properties from overriding protected runtime
  declarations.
- [x] Provide deterministic test-descriptor coverage.

## RB-04: Opaque Secret References

Status: DONE

- [x] Define an opaque secret-reference value with no public value accessor.
- [x] Classify public configuration, confidential configuration, and secret
  references.
- [x] Keep secret resolution inside authorized runtime providers/drivers.
- [x] Prove diagnostics and failures do not expose secret references or values.
- [x] Relocate concrete Vault/cloud secret providers outside Phase 36.

## RB-05: Admitted Read-only Resource Trees

Status: DONE

- [x] Define logical tree identity/reference, limits, entries, and immutable
  snapshot contracts.
- [x] Bind named physical roots only at runtime configuration/provider
  boundaries.
- [x] Enforce deterministic ordering, depth, count, per-file, total-byte,
  traversal, and symlink policies.
- [x] Provide an in-memory provider for executable specifications.
- [x] Add payload-safe CallTree and metrics diagnostics.

## RB-06: Process WorkArea Tree Materialization

Status: DONE

- [x] Define logical admitted tree input separately from fixed bounded input
  files.
- [x] Require Process program admission to allow each tree identity.
- [x] Allow request limits to narrow, never broaden, runtime tree limits.
- [x] Materialize the tree inside the runtime-owned WorkArea boundary.
- [x] Reject arbitrary component/request paths before filesystem access.
- [x] Preserve Phase 35 UnitOfWork cleanup on success, failure, timeout, and
  cancellation.

## RB-07: Provider-neutral External-tool Pattern

Status: DONE

- [x] Verify fixed command templates, admitted arguments/environment, bounded
  inputs, and declared outputs through a fake Process driver.
- [x] Keep timeout, cancellation, launch, output-limit, and non-zero-exit
  outcomes distinct until provider adaptation.
- [x] Prove provider result conversion without a live Cozy installation.
- [x] Keep CNCF contracts free of CBD Support and CAR Review types.

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
