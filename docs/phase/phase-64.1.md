# Phase 64.1 - Action Transaction, 2PC, Compensation, and Recovery Runtime

status=planned
planned_at=2026-09-05
revised_at=2026-09-05
parent=[Phase 64 - CML Composite StateMachine / Workflow Runtime Integration](phase-64.md)
producer_phase=`asami/cozy/docs/phase/phase-33.1.md`

## Purpose

Define CNCF runtime execution for StateMachine/Composite StateMachine programs
compiled onto the existing `UnitOfWorkOp` / `ExecProgram` execution algebra when
execution spans local resources, optional 2PC-capable resources, and
non-transactional external systems.

The governing recovery contract is deliberately simple:

```text
inside CNCF UnitOfWork
  failure -> complete rollback

outside the committed UnitOfWork
  failure requiring business reversal
    -> application-defined compensation handler
       success -> compensated
       failure -> durable RecoveryRequired event containing original UnitOfWorkId
```

CNCF guarantees atomic UnitOfWork behavior and durable escalation. It does not
invent the business recovery procedure.

## Core Invariants

- A CNCF UnitOfWork is atomic: every admitted local effect and the StateMachine
  mutation commit together or the complete UnitOfWork rolls back.
- A failed UnitOfWork leaves no partial local commit and emits no successful
  `CommittedTransition` for that attempted transition.
- Every committed UnitOfWork has a stable `UnitOfWorkId` usable for diagnostics,
  transition history, external-action correlation, compensation, and recovery.
- 2PC is an optional way to enlarge the atomic boundary. If required distributed
  atomicity cannot be provided, admission fails; CNCF never silently downgrades
  to best effort.
- Effects outside the committed atomic boundary are not described as rollback.
- Business compensation is explicit application logic registered as a
  compensation handler/program; CNCF does not synthesize compensation.
- A compensation handler executes through normal CNCF execution boundaries and
  may itself fail.
- Compensation-handler failure produces a durable `RecoveryRequired` event with
  the original `UnitOfWorkId` and causal execution references.
- After durable `RecoveryRequired` publication/persistence, deciding and
  performing the business/manual recovery procedure is outside CNCF's semantic
  responsibility, although recovery Operations may execute through CNCF.
- Committed StateMachine history is never erased. Business reversal is a new
  explicit StateMachine transition when the domain requires one.

## Existing Execution Algebra

Phase 64.1 extends the existing CNCF execution model rather than introducing a
StateMachine-specific algebra:

```text
CML logical action
  -> Action binding/compiler
  -> ExecProgram[A] = Program[UnitOfWorkOp, A]
  -> UnitOfWork planner/interpreter
```

`UnitOfWorkOp` remains the canonical executable-intent algebra. New operations
are added only when they represent reusable CNCF executable intent not
expressible by existing operations plus metadata.

## UnitOfWork Atomic Execution

The default StateMachine execution boundary is:

```text
select transition
  -> build candidate state
  -> compile/compose admitted local ExecProgram
  -> interpret inside UnitOfWork
  -> persist state and local effects
  -> commit
```

Any error before commit means:

```text
complete rollback
no partial local effect
no committed transition occurrence
```

The atomicity guarantee is stronger than compensation and should be used
whenever the required effects can participate in the UnitOfWork.

## Optional Distributed Atomic / 2PC Execution

When all required participants support an admitted distributed atomic protocol,
CNCF may enlarge the UnitOfWork boundary using 2PC or an equivalent protocol.

The runtime must freeze participant admission, prepare/commit/abort ordering,
coordinator identity, crash recovery, timeout/in-doubt handling, and durable
outcome. If the requested atomic semantics cannot be met, execution is rejected
before external effects begin.

2PC capability is runtime infrastructure, not a CML provider API.

## External Effects and Compensation Handlers

Once a UnitOfWork has committed, externally visible effects cannot generally be
rolled back by the local transaction manager.

When application semantics provide a business reversal, the application may
register an explicit compensation handler for the logical action/effect.

Conceptually:

```text
external forward effect
  -> completed occurrence
  -> later failure requiring compensation
  -> compensation handler
       -> ExecProgram[UnitOfWorkOp, CompensationResult]
```

Rules:

- a handler is application-defined logic, not an automatically generated inverse;
- the handler is associated with a stable logical action/effect identity;
- it receives sufficient occurrence/correlation context to perform a deliberate
  recovery action;
- it executes through normal authorization, UnitOfWork, observability, and
  idempotency boundaries;
- its execution is recorded as a new occurrence;
- if domain state must be reversed, the handler invokes a normal explicit
  StateMachine transition rather than deleting or rewriting history;
- automatic compensation chains are not a v1 requirement.

## Compensation Failure and Durable RecoveryRequired

A failed compensation handler crosses the CNCF automatic-recovery boundary.
CNCF must durably record/publish a `RecoveryRequired` event.

Minimum event semantics:

```text
RecoveryRequired
  eventId
  unitOfWorkId              // original committed UoW that anchors the incident
  correlationId
  causationId
  componentRef
  actionOccurrenceRef
  compensationOccurrenceRef
  transitionOccurrenceRef?
  failureSummary
  occurredAt
```

The exact payload type may evolve, but `unitOfWorkId` is mandatory. Sensitive
payloads, credentials, and secrets must not be copied into the event.

The event is a durable execution fact, not a transient notification. Existing
CNCF event persistence/delivery guarantees must be used or strengthened so a
recovery-required condition cannot disappear because a consumer is offline.

After the event is durably established, consumers may project it into an admin
queue/dashboard, alerting system, or dedicated recovery component. CNCF does not
choose the business repair procedure.

## Manual / Administrative Recovery Boundary

A recovery consumer or administrator may later invoke an explicit recovery
Operation. That Operation should normally re-enter CNCF through the ordinary
path:

```text
administrator / recovery component
  -> authorized recovery Operation
  -> ExecProgram[UnitOfWorkOp, A]
  -> UnitOfWork / StateMachine / external action
```

This preserves authorization, audit, transition history, and observability even
when the recovery decision itself is manual.

Phase 64.1 does not require CNCF to own a RecoveryTask domain model or business
workflow. The durable `RecoveryRequired` event is the framework handoff point.

## Irreversible Effects

An external effect with no registered compensation handler is treated as
irreversible from CNCF's automatic recovery perspective. If later failure leaves
business inconsistency requiring human action, the runtime may durably escalate
that condition through the same recovery-event mechanism where the execution
contract identifies it.

The framework never invents a technical inverse or reports an irreversible
external effect as rolled back.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ATX-01 | Runtime inventory | Existing UnitOfWork/`UnitOfWorkOp`, transaction capability, Event durability, after-commit effects, idempotency, and compensation hooks are inventoried. | planned |
| ATX-02 | Atomicity contract | Complete UnitOfWork rollback, stable UnitOfWorkId, and no-partial-commit semantics are frozen and tested. | planned |
| ATX-03 | Optional 2PC boundary | Distributed atomic admission and explicit unsupported behavior are frozen. | planned |
| ATX-04 | Compensation-handler SPI | Application-defined handler registration, occurrence context, normal CNCF execution, authorization, and idempotency are defined. | planned |
| ATX-05 | RecoveryRequired event | Durable event schema, mandatory original UnitOfWorkId, causation/correlation, persistence, and non-leakage are implemented. | planned |
| ATX-06 | Recovery boundary | CNCF responsibility ends at durable escalation; explicit admin/recovery Operations can safely re-enter normal execution. | planned |
| ATX-07 | Composite integration | Constituent/composite programs preserve UnitOfWorkId, transition/action occurrence causation, and recovery provenance. | planned |
| ATX-08 | Acceptance | Local rollback, optional 2PC, compensation-handler success/failure, durable RecoveryRequired, and admin-operation re-entry are proven. | planned |

## Acceptance

- Any failed local UnitOfWork is completely rolled back.
- No successful transition occurrence is published for a rolled-back attempt.
- Every committed StateMachine UnitOfWork has a stable UnitOfWorkId.
- Required 2PC semantics cannot silently downgrade.
- External business reversal uses an explicit application-defined compensation
  handler rather than framework-generated compensation.
- Compensation-handler failure durably emits/persists `RecoveryRequired` with
  the original UnitOfWorkId.
- RecoveryRequired can be consumed after restart/offline periods without losing
  the recovery obligation.
- CNCF does not claim that the failed business operation was restored merely
  because recovery was requested.
- Manual recovery can use an authorized CNCF Operation while retaining audit and
  StateMachine history.

## Non-Goals

- Automatically synthesizing business compensation.
- Automatically constructing multi-step Saga compensation chains in v1.
- Owning the business/manual recovery procedure after `RecoveryRequired` is
  durably established.
- Rewriting committed transition history.
- Requiring 2PC for all external interactions.
- Embedding provider/JTA/XA configuration into CML.

## References

- `docs/phase/phase-64.md`
- `docs/phase/phase-64.2.md`
- `docs/design/free-unitofwork-execution-model.md`
- `docs/notes/action-transaction-compensation-runtime-provisional-specification.md`
- `asami/cozy/docs/phase/phase-33.1.md`
