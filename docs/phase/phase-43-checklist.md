# Phase 43 - Bounded Resource Tree Query Checklist

This checklist is the authoritative Phase 43 state ledger.

## RQ-01: Contract Promotion

Status: ACTIVE

- [ ] Amend the resource-tree design with a distinct query contract.
- [ ] Amend the static specification with selector, limits, result, symlink,
      and diagnostics requirements.
- [ ] Keep exact leaf-name selection generic and CBD-independent.

## RQ-02: Query Model

Status: PLANNED

- [ ] Add validated selector, query-limit, and immutable result models.
- [ ] Define conservative caller/runtime limit composition.
- [ ] Preserve logical relative paths and deterministic result ordering.

## RQ-03: Providers

Status: PLANNED

- [ ] Add deterministic in-memory query behavior.
- [ ] Add bounded local traversal without following symbolic links.
- [ ] Keep strict snapshot behavior unchanged.
- [ ] Cover root symlink, nested symlink, depth, visit, match, per-entry, and
      aggregate-byte boundaries with executable specifications.

## RQ-04: DSL and Observability

Status: PLANNED

- [ ] Expose resource-tree query through ExecutionContext and protected DSL.
- [ ] Add payload-safe CallTree and `resource-tree.query` metrics.
- [ ] Preserve structured `Consequence/Conclusion` failures.

## RQ-05: CBD Support Driver

Status: PLANNED

- [ ] Query exact leaf name `project.yaml` from logical tree `working`.
- [ ] Emit one working observation per returned descriptor.
- [ ] Initialize local inputs before status and catalog readiness projection.
- [ ] Keep physical roots and ambient filesystem access out of CBD code.

## RQ-06: Verification and Closure

Status: PLANNED

- [ ] Run focused ResourceTree and DSL executable specifications.
- [ ] Run full CNCF tests.
- [ ] Run CBD Support CAR lint and full tests.
- [ ] Validate a live bounded workspace query without snapshotting the whole
      workspace.
- [ ] Update strategy/phase evidence and close Phase 43.
