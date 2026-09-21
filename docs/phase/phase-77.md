# Phase 77 - Generated Workflow ABI, ComponentFactory Discovery, and WorkflowInstance Persistence SPI

status=closed
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-77.2
aggregate_validation_sequence=["PHASE-77","PHASE-77.1","PHASE-77.2"]
repository_full_suite=deferred-not-run
planned_at=2026-09-16
split_at=2026-09-21
closed_at=2026-09-21
depends_on=[Phase 64](phase-64.md), [Phase 64.2](phase-64.2.md), and Cozy Phase 62.3 producer handoff
successor=[Phase 77.1](phase-77.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77 Checklist](phase-77-checklist.md)

## Purpose

Phase 77 is the first child of the split StateMachine API/SPI and
Skill-driven Workflow delivery sequence. It admits Cozy's generated CML
`WORKFLOW`/StateMachine ABI without CML reparsing, discovers admitted metadata
through `ComponentFactory`, and freezes the provider-neutral independently
durable `WorkflowInstance` persistence SPI.

The resulting handoff is the only authority for Phase 77.1 to implement
ActionExecution/Provider/Continuation runtime behavior. It does not execute
Actions, expose Continuation work, or project a Skill WorkOrder.

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-01 | Freeze supported Cozy 62.1-62.3 StateMachine/Workflow ABI versions, admission diagnostics, and compatibility policy. | done |
| CWF-77-02 | Discover generated definitions and StateMachine API/SPI metadata through ComponentFactory while preserving source identity. | done |
| CWF-77-03 | Freeze the provider-neutral independently durable WorkflowInstance persistence SPI, identity/revision/history constraints, and correlation boundary. | done |

## Closure contract

Phase 77 closes only when the following are accepted:

- supported generated ABI versions, provenance, required semantics, and
  fail-closed incompatibility diagnostics are executable;
- `ComponentFactory` discovers admitted generated definitions plus Provided API
  and Required SPI metadata without handwritten canonical definitions; and
- the generic WorkflowInstance persistence SPI fixes definition/instance
  identity, revision, lifecycle/progression state, append-only history,
  correlation/causation, current suspension shape, and the boundary that keeps
  entity StateMachine storage from becoming the authoritative process store.

The closure handoff is `CWF77-FOUNDATION`: the admitted generated ABI,
ComponentFactory metadata, and WorkflowInstance SPI contract. Phase 77.1 must
consume that handoff and must stop if it is incompatible; it may not infer CML
semantics or substitute a handwritten Workflow definition.

## Split plan record — 2026-09-21

The ordinary Phase entry gate returned `SPLIT_REQUIRED` before goal creation:
960 expected minutes (1200-minute conservative upper bound) exceeds the
360-minute target and 480-minute ceiling. The applied split reasons are
`time-bound` and `reasoning-cost-isolation`; source goal status is
`none / no-goal-pre-entry`.

The serial sequence is `PHASE-77 -> PHASE-77.1 -> PHASE-77.2`. This Phase is
estimated at 300 minutes (240-390) with no comparable completed ordinary Phase
for calibration; Phase 64.2 is a forced minimum closure and is not a
calibration source. The ABI/source-identity/persistence decision is the
sequence's protected reasoning kernel, with recommended parent profile
`gpt-5.6-terra / xhigh`. Phase 77.1 and Phase 77.2 consume frozen handoffs at
the lower-cost `gpt-5.6-terra / high` profile. All children retain the standard
parent reasoning-mode policy.

The split adds two planning/checklist pairs, two independent reviews, and two
release commits. It avoids repeating protected ABI/persistence reasoning across
the runtime and consumer-handoff work. No completed Phase 77 work existed to
move.

## Non-goals

- StateMachine Action progression, Provider execution, or direct-effect-path
  removal; these are Phase 77.1.
- Durable Continuation claim/resume, Skill projection, JSON protocol codecs,
  the reference vertical slice, or the `sm-workflow` handoff; these are Phase
  77.1 or Phase 77.2 as recorded in their checklists.
- CML parsing, a second Workflow DSL, a default datastore provider, concrete
  model selection, broad API/UI/transport/orchestration expansion, or the
  generalized Composite artifact owned by Cozy Phase 66 and CNCF Phase 89.

## Validation and release

This is an aggregate-deferred member of the serial sequence. It still requires
focused validation, independent review, a closure ledger, and a phase-release
commit. The single repository-wide `sbt --batch test` runs only at the frozen
Phase 77.2 release tree.

## Closure evidence

- Phase-base recovery exception: the normal recovery helper rejected the
  already-committed Steps because their acceptance-state checklist updates were
  deferred to this release. Under the explicit user directive to bypass that
  unrecoverable helper defect, the Phase base is fixed to
  `92cdab9e98743090f53eaff0ff1e62475a92942c`, the sole parent of the first
  committed Phase Step `d3e7fca8cf12d06b117ca3a5176e01c3c8417b1c`; it is not
  the current release HEAD.
- Step 77-S1 admitted the generated Workflow ABI and ComponentFactory discovery
  contract in `d3e7fca8cf12d06b117ca3a5176e01c3c8417b1c`; focused receipt
  `P77-S1A-VAL-006` passed its nine generated-ABI specifications.
- Step 77-S1B defined the provider-neutral WorkflowInstance persistence SPI in
  `e3e592b1d14091d6d7ee52dde4e8ab7c4de50c5e`; focused receipt
  `P77-S1B-VAL-008` passed all six persistence specifications with the shared
  SBT lock released.
- `P77-PHASE-FULL-REVIEW-001` accepted the complete Phase tree with no Current
  Boundary Blocker. Its Hygiene follow-ups are recorded in
  [Phase 77 hygiene follow-up](../journal/2026/09/2026-09-21-phase-77-hygiene-follow-up.md).
- `repository_full_suite=deferred-not-run`: the Phase 77.2 release tree remains
  the sole aggregate repository-full-suite owner for the frozen
  `PHASE-77 -> PHASE-77.1 -> PHASE-77.2` sequence.

## References

- [Phase 64](phase-64.md) and [Phase 64.2](phase-64.2.md)
- [Phase 77 Checklist](phase-77-checklist.md)
- [Phase 77.1](phase-77.1.md) and [Phase 77.2](phase-77.2.md)
- [Released Workflow producer fixture handoff](../design/composite-workflow-released-producer-fixture-handoff.md)
- [Phase 64.2 forced minimum closure](../journal/2026/09/2026-09-21-phase-64.2-forced-minimum-closure.md)
