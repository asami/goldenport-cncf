# Phase 64.1 - Action Transaction, 2PC, and Compensation Runtime

status=planned
planned_at=2026-09-05
parent=[Phase 64 - CML Composite StateMachine / Workflow Runtime Integration](phase-64.md)
producer_phase=`asami/cozy/docs/phase/phase-33.1.md`

## Purpose

Define CNCF runtime execution for typed StateMachine/Composite StateMachine
Action Programs when effects span local resources, optional 2PC-capable
resources, and non-transactional external systems.

The governing model is:

```text
Generated typed Action Program
        |
        v
Action Planner
        |
        +--> local atomic segment
        +--> distributed atomic / 2PC segment
        +--> after-commit compensatable segment
        +--> irreversible segment
        |
        v
Interpreter / recovery runtime
```

CNCF owns execution mechanics. CML/Cozy owns logical action meaning,
transaction/reversibility requirements, and explicit compensation relations.

## Core Invariants

- A StateMachine transition whose required actions all participate in one
  admitted atomic boundary either commits completely or aborts completely.
- Any pre-commit failure in that atomic boundary produces no committed state
  transition and no `CommittedTransition`.
- 2PC is optional runtime capability; CNCF must not pretend it is available.
- If generated semantics require distributed atomicity and required resources
  cannot participate, admission fails rather than silently degrading.
- Compensation is a new explicit action occurrence, not rollback of history.
- An irreversible effect remains historically visible and cannot be represented
  as rollbackable.
- Constituent/composite action composition happens before execution planning;
  composition does not imply one physical transaction.

## Execution Classification

The planner should treat transaction capability and reversibility as orthogonal
inputs where the generated contract supports that distinction.

Conceptually:

```text
transaction capability:
  local-atomic
  two-phase-capable
  non-transactional

reversibility:
  rollback-by-transaction
  compensate(actionRef)
  irreversible
```

Runtime provider capability may further constrain which plan is admissible.

## Atomic Local Execution

Local StateMachine effects run inside the admitted UnitOfWork:

```text
transition candidate
  -> compose local action program
  -> interpret local actions
  -> persist entity/aggregate state
  -> commit
```

Interpreter or persistence failure before commit aborts the entire unit.

## Distributed Atomic / 2PC Execution

When generated semantics require one distributed atomic segment and all
participants are capable, CNCF may execute using 2PC or an equivalent admitted
atomic protocol.

The runtime must freeze:

- participant admission/capability discovery;
- prepare/commit/abort ordering;
- crash/recovery behavior around prepare and commit;
- coordinator identity and durable transaction outcome;
- timeout/in-doubt handling;
- diagnostics without leaking credentials/payloads.

A runtime without the required capability rejects the plan before externally
visible execution.

## Compensatable Execution

A completed non-transactional forward action may register an explicit generated
compensation relation.

```text
forward occurrence
  -> durable completion record
  -> later failure
  -> compensation occurrence
```

Rules:

- compensate only a forward occurrence known to have completed;
- compensation is idempotent/deduplicated through stable occurrence identity;
- default ordering for multiple completed effects is reverse causal order unless
  the generated contract explicitly states another safe order;
- compensation failure is durable and recoverable;
- retry exhaustion produces a visible recovery state, not silent success;
- forward and compensation events both remain in history/audit;
- compensation cannot be claimed as exact rollback unless its model semantics
  say so.

## Irreversible Effects

For irreversible effects, later failure cannot restore the pre-effect world.
CNCF must expose the resulting recovery boundary explicitly.

Possible outcomes include retry of later work, explicit corrective action,
operator/manual recovery, or higher-level business compensation. The runtime
must not invent a technical inverse.

## Planner Output

A provisional runtime representation may include:

```text
ActionExecutionPlan
  atomicSegments
  distributedAtomicSegments
  afterCommitSegments
  compensationGraph
  irreversibleBoundaries
  idempotencyKeys
  provenance
```

The exact implementation type is runtime-internal, but plan diagnostics must
remain traceable to generated CML action identities/source locations.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ATX-01 | Runtime inventory | UnitOfWork, transaction-manager/2PC capability, Job/Event after-commit behavior, retry/idempotency, and existing compensation candidates are inventoried. | planned |
| ATX-02 | Effect admission model | Generated transaction/reversibility semantics are mapped to runtime capability and structured admission outcomes. | planned |
| ATX-03 | Atomic planner/interpreter | Local atomic segmentation and full-transition abort semantics are proven. | planned |
| ATX-04 | 2PC planner/interpreter | Optional distributed atomic execution, prepare/commit recovery, and in-doubt diagnostics are proven or explicitly unsupported. | planned |
| ATX-05 | Compensation runtime | Forward occurrence tracking, reverse-order compensation, retry, idempotency, and durable recovery state are implemented. | planned |
| ATX-06 | Irreversible boundary | Runtime projection/recovery behavior for non-compensatable effects is explicit. | planned |
| ATX-07 | Composite integration | Constituent and composite Action Programs compose before planning while preserving provenance and causal order. | planned |
| ATX-08 | Cross-repository acceptance | CML-generated examples prove atomic abort, required-2PC admission, compensation success/failure, and irreversible-effect behavior. | planned |

## Acceptance

- A local StateMachine transition plus local actions either commits fully or
  aborts fully.
- No `CommittedTransition` is emitted for a pre-commit action/interpreter error.
- Required distributed atomicity cannot silently downgrade when 2PC capability
  is absent.
- When 2PC is admitted, coordinator/participant outcomes are recoverable across
  crash windows.
- A completed external forward effect is compensated only through its explicit
  typed compensation action.
- Compensation occurrence has stable identity and supports duplicate-safe retry.
- Failed compensation leaves a durable visible recovery state.
- Irreversible effects are visible as irreversible boundaries and are never
  described as rolled back.
- Constituent and composite action provenance remains observable after program
  composition and planning.
- Runtime plans are traceable to CML/generated action definitions but do not
  expose provider-specific details back into CML semantics.

## Non-Goals

- Requiring 2PC for all external interactions.
- Embedding provider/JTA/XA configuration into generated model semantics.
- Automatically generating business compensation.
- Treating compensation as history erasure.
- A generic Saga DSL independent of Composite StateMachine/Workflow.

## References

- `docs/phase/phase-64.md`
- `docs/notes/action-transaction-compensation-runtime-provisional-specification.md`
- `docs/notes/statemachine-workflow-alignment-provisional-specification.md`
- `asami/cozy/docs/phase/phase-33.1.md`
