# Phase 57.3 - Runtime Compatibility Retirement

status=done
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

## Completion/Current State

- AES-06R-A through AES-06R-E are completed and accepted.
- Framework Step commit:
  `115d5ed19b07d3f24fa1cf7139fae70d552cf060`.
- Corrective framework Step commit:
  `6f7cd0bbe79276f6203e3a1d92a4cd9a27a4dbe3`.
- Final-suite fixture Step commit:
  `8c886a5a0bd6c3735c830adad44a2dbe987cfb4e`.
- Launcher Step commit:
  `49eff9adb43e7af7f5164cc9f4c88d3ee33ae003`.
- Corrective focused validation `36237-20260813T034150Z` passed 107/107, and
  the final independent focused re-review was clean.
- Final framework invocation `69427-20260813T043639Z` passed 440 suites with
  3,224/3,224; final launcher invocation `70587-20260813T043931Z` completed
  `CncfLauncherSpec` with no failure or abort. Both completed with SBT/wrapper
  exits 0/0 and the shared lock released.
- Phase 57.3 is DONE. Phase 57.4 remains planned and unstarted.

## Non-Goals

- Cozy/sbt-cozy packager, publisher, or repository-writing migration; Phase
  57.4.
- Deleting or importing forensic backups.
- Changing development `-SNAPSHOT` versions.
