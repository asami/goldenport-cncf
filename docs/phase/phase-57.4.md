# Phase 57.4 - Build and Publication Compatibility Retirement

status=planned
split_from=[Phase 57](phase-57.md)
depends_on=[Phase 57.3](phase-57.3.md)
successor=[Phase 57.5](phase-57.5.md)
checklist=[Phase 57.4 Checklist](phase-57.4-checklist.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Goal

Remove unreleased compatibility from Cozy, sbt-cozy, CAR packaging,
publication, and repository-index production, then rebuild the active local
warehouse from fresh canonical `-SNAPSHOT` artifacts.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Closure

- Packager, publisher, lint, and repository writers emit only schema 3, ABI v2,
  index v2, and canonical coordinates.
- Unreleased legacy conversion and fallback are absent unless backed by an
  explicit published/production authority.
- When forensic retention is requested, `~/.cncf/local` is moved to a unique
  sibling outside active lookup and the active warehouse is rebuilt empty.
- Canonical dependencies and representative CARs are freshly built and locally
  published in dependency order.
- sbt-cozy remains `0.1.20-SNAPSHOT` and Cozy remains `0.3.4-SNAPSHOT`.

## Non-Goals

- Runtime compatibility already closed by Phase 57.3.
- Automatic backup, migration, merge, or restoration.
- Public release or removal of `-SNAPSHOT`.
