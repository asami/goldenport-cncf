# Phase 57.3 - Runtime Compatibility Retirement

status=in-progress
split_from=[Phase 57](phase-57.md)
depends_on=[Phase 57.2](phase-57.2.md)
successor=[Phase 57.4](phase-57.4.md)
checklist=[Phase 57.3 Checklist](phase-57.3-checklist.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Goal

Remove unreleased runtime, launcher, repository, descriptor, ABI, assembly,
alias, and fallback compatibility. Active development accepts only the
canonical Phase 56 runtime contracts and rejects legacy state early with an
actionable warehouse-rebuild diagnostic.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Canonical Contract

- Component descriptor schema 3;
- CAR ABI manifest v2;
- Component repository index v2;
- assembly identity as `namespace` / `id` / `version`; and
- qualified Component identity with no unqualified or legacy-name fallback.

## Closure

- Runtime-side legacy readers, aliases, automatic migration, and fallback are
  removed unless an explicit published/production authority requires them.
- Legacy input fails closed and reports the resolved warehouse plus exact
  stop, backup, empty-active-warehouse, republish, retry, and verify steps.
- Runtime performs no backup, merge, migration, or rebuild mutation.
- Focused framework/launcher/runtime acceptance and review pass.

## Non-Goals

- Cozy/sbt-cozy packager, publisher, or repository-writing migration; Phase
  57.4.
- Deleting or importing forensic backups.
- Changing development `-SNAPSHOT` versions.
