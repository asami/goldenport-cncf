# Phase 43 - Bounded Resource Tree Query Checklist

This checklist is the authoritative Phase 43 state ledger.

## RQ-01: Contract Promotion

Status: DONE

- [x] Amend the resource-tree design with a distinct query contract.
- [x] Amend the static specification with selector, limits, result, symlink,
      and diagnostics requirements.
- [x] Keep exact leaf-name selection generic and CBD-independent.
- [x] Fix the boundary between generic `Tree[A]`, complete ResourceTree
      snapshots, and sparse ResourceTree query results.

## RQ-02: Query Model

Status: DONE

- [x] Add validated selector, query-limit, and immutable result models.
- [x] Define conservative caller/runtime limit composition.
- [x] Preserve logical relative paths and deterministic result ordering.

## RQ-03: Providers

Status: DONE

- [x] Add deterministic in-memory query behavior.
- [x] Add bounded local traversal without following symbolic links.
- [x] Keep strict snapshot behavior unchanged.
- [x] Cover root symlink, nested symlink, depth, visit, match, per-entry, and
      aggregate-byte boundaries with executable specifications.

## RQ-04: DSL and Observability

Status: DONE

- [x] Expose resource-tree query through ExecutionContext and protected DSL.
- [x] Add payload-safe CallTree and `resource-tree.query` metrics.
- [x] Preserve structured `Consequence/Conclusion` failures.

## RQ-05: CBD Support Driver

Status: DONE

- [x] Query exact leaf name `project.yaml` from logical tree `working`.
- [x] Emit one working observation per returned descriptor.
- [x] Initialize local inputs before status and catalog readiness projection.
- [x] Keep physical roots and ambient filesystem access out of CBD code.

## RQ-06: Verification and Closure

Status: DONE

- [x] Run focused ResourceTree and DSL executable specifications.
- [x] Run full CNCF tests.
- [x] Run CBD Support CAR lint and full tests.
- [x] Validate a live bounded workspace query without snapshotting the whole
      workspace.
- [x] Update strategy/phase evidence and close Phase 43.

Closure evidence, Jul. 20, 2026:

- focused ResourceTree, DSL, observability, and RuntimeConfig specs: 50 passed;
- full CNCF suite: 2,062 passed, 0 failed;
- full CBD Support suite: 231 passed, 0 failed;
- normal CBD Support CAR lint: no FAIL findings, with publish-readiness WARNs
  for the absent prior ABI baseline and development sbt-cozy SNAPSHOT;
- live `/Users/asami/src/dev2026` query: 48,176 directories visited, 18
  descriptors projected, and `working=ready` through MCP `status` and
  `listCatalogs`.
