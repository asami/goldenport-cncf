# Phase 57 - Runtime and Control Center Development Stabilization

status=in-progress
planned_at=2026-08-08
split_approved_at=2026-08-09
depends_on=[Phase 56](phase-56.md)
successor=[Phase 57.1](phase-57.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 57 Checklist](phase-57-checklist.md)
origin=[Phase 56 CID-05 Runtime Identity Migration Plan](../notes/phase-56-cid05-cncf-runtime-identity-migration-plan.md)
planning_journal=[Phase 57 Action Execution Semantics and Forward Phase Renumbering](../journal/2026/08/2026-08-08-phase-57-action-execution-semantics-and-renumbering.md)

## Purpose

Close the reviewed development-runtime recovery accumulated while proving the
Phase 56 canonical Component contract in an actual Textus Control Center
startup. The result is a coherent, commit-ready baseline for the later Action,
compatibility-retirement, and test-hygiene child Phases.

## Approved Split

The 2026-08-09 Phase planning gate estimated the former seven-stage Phase at
20–31 hours. The user approved a six-way split with `$cncf-split-phase 57`.
Phase 57 retains the already performed runtime-stabilization history. Unfinished
work is owned exactly once by [Phase 57.1](phase-57.1.md) through
[Phase 57.5](phase-57.5.md). Phase 58 and later retain their existing numbers.

Pre-split gate evidence: `SPLIT_REQUIRED`, target upper bound six hours,
recommended minimum parent effort `xhigh`, critical path spanning the public
Action contract, canonical CAR/runtime admission, Cozy/sbt-cozy publication,
warehouse rebuild, and test-suite cleanup.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 1–2h
- recommended_minimum_effort: xhigh
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 57

## Scope

- canonical packaged and development CAR admission;
- canonical local CAR repository lookup and runtime dependency discovery;
- launcher project/development runtime selection and default repository search;
- Textus Control Center standalone assembly, Web alias, candidate catalog, and
  launcher-evidence integration; and
- exact focused validation, CAR lint, runtime readiness, and review records for
  the accumulated tree.

The canonical Phase 56 contracts stay fixed: descriptor schema 3, ABI manifest
v2, repository index v2, qualified Component identity, and development
`-SNAPSHOT` coordinates.

## Preserved Completed History

- framework admission/repository/subsystem focused suites passed, including
  45/45 `GenericSubsystemDescriptorSpec` and the canonical CAR regressions;
- cncf-launcher `CncfLauncherSpec` passed 117/117;
- Control Center catalog/evidence specifications passed and CAR lint returned
  exit 0 with no FAIL;
- Control Center reached HTTP readiness; ArtScene appeared as a non-running
  candidate and historical-stopped launcher evidence was projected; and
- independent review and focused re-review converged with no findings.

These results are pre-split completed history. Their exact unchanged-tree
evidence may be reused for the Phase 57 Step commit.

## Closure

- [ ] Freeze the exact reviewed path set and exclude unrelated prepare-hygiene
  journals.
- [ ] Reconcile current diff identities against the accepted review baseline.
- [ ] Commit the runtime-stabilization accumulator without rerunning unchanged
  focused evidence.
- [ ] Record the commit and mark Phase 57 DONE before Phase 57.1 starts.

## Non-Goals

- Action execution changes; owned by Phase 57.1 and Phase 57.2.
- General legacy compatibility retirement; owned by Phase 57.3 and Phase 57.4.
- Phase/CID Spec cleanup or broad validation; owned by Phase 57.5.
- Removing `-SNAPSHOT` from sbt-cozy `0.1.20-SNAPSHOT`, Cozy
  `0.3.4-SNAPSHOT`, or any other development coordinate.
