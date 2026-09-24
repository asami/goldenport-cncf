# Phase 77.2 - Generic Skill Workflow Projection, Typed Protocol, and sm-workflow Handoff

status=planned
execution_priority=first_sm_workflow_vertical_slice
split_full_test_policy=final-only
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
validation_ownership=aggregate-final-owner
aggregate_validation_owner=PHASE-77.2
aggregate_validation_sequence=["PHASE-77","PHASE-77.1","PHASE-77.2"]
split_from=[Phase 77](phase-77.md)
predecessor=[Phase 77.1](phase-77.1.md)
planned_at=2026-09-21
depends_on=[Phase 77.1](phase-77.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-statemachine-api-spi-runtime-and-skill-driven-workflow)
checklist=[Phase 77.2 Checklist](phase-77.2-checklist.md)

## Purpose

Phase 77.2 is the final child of the Phase 77 sequence. It consumes the
accepted `CWF77-RUNTIME` handoff to project externally claimable Continuation
work into Generic Skill WorkOrders, define the minimum typed
Start/Continuation/WorkOrder/Terminal protocol and schema-versioned fail-closed
Skill/Codex JSON encoding, prove the real Cozy fixture vertical slice, and
freeze the `sm-workflow` consumer handoff.

The first `sm-workflow` vertical slice is the priority. It uses Phase 77.1's
explicitly loose, post-commit Continuation baseline: external claim follows
successful continuation persistence, while WorkflowInstance, Continuation,
and UnitOfWork effects are not represented as one atomic commit. The durable
shared-transaction upgrade is [Phase 94](phase-94.md), outside this Phase's
entry and closure criteria.

Application-specific payloads and software-development policy remain owned by
`sm-workflow`. CNCF owns the generic Workflow envelope, identity, revision,
ContextSnapshot, Evidence, execution requirements, projection boundary, and
resume rules.

## Incoming authority handoff

Input: Phase 77.1's accepted canonical ActionExecution/ExecProgram/Provider
and durable Continuation-resume contract. Action: close Phase 77.1. Output:
the frozen Skill projection and reference-fixture execution boundary. Owner:
CNCF Phase 77.1. The handoff is invalidated by changed Action-outcome, Provider,
or resume-admission semantics; this Phase must stop instead of creating a
parallel protocol or Workflow runtime.

## Scope

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-07 | Project an externally claimable Continuation SPI request into Generic Skill WorkOrder semantics without making Skill canonical. | in progress |
| CWF-77-08 | Prove the real Cozy fixture's internal -> suspended external -> resumed -> internal path through the IoC boundary. | planned |
| CWF-77-09 | Freeze reproducible CML-first evidence and the exact `sm-workflow` consumer handoff. | planned |
| CWF-77-10 | Define the minimum typed Workflow protocol and schema-versioned fail-closed Skill/Codex JSON encoding. | in progress |

## Current implementation slice

`WorkflowProtocolV1` now defines a stable Handle, profile-owned typed Start
request, the closed Continuation forms, execution requirements, minimal
Presentation, and a claimed-Continuation-to-WorkOrder projection. The claim
token stays internal. A separate-turn `ContinuationResult` carries typed
result, ContextSnapshot, completion facts, and execution evidence; admission
checks it against the issued WorkOrder before the existing one-shot runtime
resume. `WorkflowWorkOrderJsonV1` and `WorkflowResultJsonV1` provide strict,
schema-versioned round trips for the external WorkOrder/result exchange.
Focused `WorkflowProtocolV1Spec` passed 3/3 using the serialized SBT runner
(`P772-PROTOCOL-FINAL-VAL-014`, receipt
`31efa482fbbb530227910b02f671ee3c205d8e09858548cf45d2581e38a34cc7`).

The uncommitted `WorkflowStartJsonV1` adds strict, schema-versioned wire
round trips for profile-selected `WorkflowStartRequest`. Its bound decode
rejects a Start Operation or Workflow identity/revision different from the
selected profile; unknown fields, schema, type, and payload shape fail closed.
`P772-START-JSON-VAL-015` passed 5/5 focused protocol tests with the shared
SBT lock released. This is preparatory development while Phase 77.1 remains
open, not an accepted Phase 77.2 handoff or a public Start Operation. The
profile still owns payload/authority admission and transport mapping.
Decision/Wait/Terminal wire coverage, direct Skill dispatch/result
normalization, and the real Cozy fixture vertical slice remain open.

## Closure contract

- `ContinuationRequest` projects only a durable externally claimable suspended
  Required SPI operation. Internal deterministic Actions remain in the runtime
  and never become Skill WorkOrders.
- The generic typed model includes `WorkflowStartRequest`, `WorkflowStartResult`,
  `WorkflowHandle`, and closed `Continuation = WORK_ORDER | DECISION | WAIT |
  TERMINAL`; application payloads remain consumer-owned extension points.
- WorkOrder requirements use typed capability/risk data and abstract
  `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL` reasoning levels. Host model or
  provider selection is evidence, not StateMachine control.
- Fail-closed JSON preserves identity, expected revision, ContextSnapshot,
  typed input/result, Completion/Evidence, and rejects unknown schema,
  incompatible payload, stale revision/snapshot, missing evidence, and
  duplicate/incompatible result.
- The accepted Cozy Phase 62.3 fixture proves internal Action programs,
  durable external ReviewChange suspension, after-commit claimability,
  typed fresh-UnitOfWork resume, closing Action execution, and terminal output.
- A committed UnitOfWork followed by failed or indeterminate continuation
  persistence remains an explicit incomplete outcome; the runtime must not
  hand out work from that failed result or describe it as atomic rollback.
  An indeterminate write can nevertheless leave an `Available` record in the
  store; this loose boundary requires reconciliation and does not prove
  global claim exclusion after a failure response.
- The CML-first evidence pins Cozy source/ABI/fixture, CNCF revision, protocol
  compatibility, and a stable `sm-workflow` consumer handoff without claiming
  consumer datastore, migration, retention, or public CLI completion.

## Planning and validation

Estimated duration is 330 minutes (270-420), within the six-hour target and
eight-hour ceiling. Reconciled common-contract and JSON-boundary decisions make
this bounded-settled execution with recommended parent profile
`gpt-5.6-terra / high` and standard reasoning-mode policy.

Phase 77.2 is the `aggregate-final-owner`: after every predecessor's deferred
release handoff is verified, it runs the serial sequence's one repository-wide
`sbt --batch test` on its frozen release tree, in addition to its focused
validation, review, closure ledger, and release commit.

This later aggregate test does not gate or retroactively substitute for
Phase 77.1's own focused validation, review, closure ledger, and release
commit. Phase 77.2 final acceptance consumes the closed Phase 77.1 handoff.

## Non-goals

- ABI admission, ComponentFactory discovery, and persistence SPI ownership
  from Phase 77; or Action/Provider/Continuation runtime implementation from
  Phase 77.1.
- A second Workflow DSL or runtime, direct Worker transition control, concrete
  model-selection policy, default datastore provider, broad Start/API expansion,
  rich Presentation/UI, parent/child composition, orchestration, REST/MCP/UI,
  transport, or Flutter implementation.
- `sm-workflow` application payload schemas, SQLite/datastore, migration,
  retention, lease policy, or public CLI implementation.
- The shared EventStore/DataStore/WorkflowInstance/Continuation/UnitOfWork
  transaction domain; [Phase 94](phase-94.md) owns that upgrade and must not
  block the first `sm-workflow` vertical slice.

## References

- [Phase 77](phase-77.md), [Phase 77.1](phase-77.1.md), [Phase 77.2 Checklist](phase-77.2-checklist.md), and [Phase 94](phase-94.md)
- [Phase 77 common contract decision](../journal/2026/09/2026-09-20-phase-77-common-contract-reconciliation-decision.md)
- [Phase 77 minimum Skill continuation JSON contract](../journal/2026/09/2026-09-20-phase-77-minimum-skill-continuation-json-contract.md)
- [Released Workflow producer fixture handoff](../design/composite-workflow-released-producer-fixture-handoff.md)
