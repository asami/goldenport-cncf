# Aggregate/View Generation Migration Note (AV-02)

status=closed
phase=7
item=AV-02
date=2026-03-21
owner=cncf-runtime

## Purpose

Document the migration policy from manual aggregate/view implementation
to generated source-of-truth based on CML Entity definitions.

## Old Path (manual)

Previous path allowed manual aggregate/view definitions in application code,
with optional ad-hoc runtime registration.

Typical characteristics:
- manual `AggregateCollection` / `ViewCollection` wiring
- metadata not always synchronized with generated models
- higher drift risk between model intent and runtime behavior

## New Path (generated source-of-truth)

Canonical path is now:

`Cozy (CML) -> SimpleModeler (generated DomainComponent metadata) -> CNCF bootstrap`

Canonical metadata fields consumed by CNCF:
- `aggregateDefinitions`
- `viewDefinitions`

Runtime behavior:
- Component bootstrap consumes generated metadata deterministically
- Aggregate/View collections are registered without manual patching

## Compatibility Policy

- Generated metadata path is canonical.
- Manual definitions are treated as legacy compatibility path only.
- When both generated metadata and manual definitions exist, generated metadata
  is the authoritative source for AV-02 scope.

## Legacy Removal Policy

Phase policy:
- AV-02: keep compatibility while migration is in progress.
- AV-03+ candidate: remove or hard-deprecate manual-only paths after
  cross-repo stabilization.

No immediate hard removal is applied in AV-02.

## Rollback Policy

If generated metadata path is unavailable or blocked:
1. temporarily continue with legacy/manual path for operational continuity.
2. keep runtime behavior deterministic (no mixed ambiguous registration).
3. restore generated path as soon as upstream (Cozy/SimpleModeler) is fixed.

Rollback is temporary and does not change canonical source-of-truth direction.

## Verification Evidence

Cross-repository connection slice:
- Cozy -> metadata generation
- SimpleModeler -> metadata propagation to generated DomainComponent
- CNCF -> metadata consumption/bootstrap registration

Reference checks:
- CNCF spec: `ComponentFactoryAggregateViewBootstrapSpec`
- Cozy model generation verification
- SimpleModeler generation/publish verification

