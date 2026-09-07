# Action Transaction and Compensation Runtime Provisional Specification

status = proposed, non-normative
date = 2026-09-05
target_phase = 64.1

## Purpose

Define CNCF execution semantics for generated typed Action Programs when the
program contains effects with different transaction and reversibility
characteristics.

## Execution Model

```text
Generated Action Program
      |
      v
Action Planner
      |
      +--> Atomic Segment
      +--> Distributed Atomic Segment
      +--> After-Commit Segment
      +--> Compensation Graph
      +--> Irreversible Boundary
      |
      v
Interpreter / Recovery Runtime
```

A Free Action Program is a logical description. It is not a transaction and
must not imply one physical commit boundary.

## Atomicity

For any segment admitted as atomic, all segment actions and the associated
StateMachine state change succeed together or none become authoritative.

Pre-commit failure means:

```text
state unchanged
local effects rolled back
no CommittedTransition
```

This applies to local UnitOfWork and to a distributed atomic segment when CNCF
has admitted a 2PC-capable execution plan.

## 2PC Admission

Distributed atomicity is a requested/required semantic guarantee, not a default
implementation assumption.

CNCF must verify that all participants can join the selected two-phase or
otherwise equivalent atomic protocol. If not, admission fails before the
segment begins.

Runtime responsibilities include coordinator identity, participant list,
prepare/commit/abort durability, timeout/in-doubt handling, crash recovery, and
structured diagnostics.

## Compensation

Compensation is modeled as an explicit action relationship generated from CML.

A forward action completion creates a durable occurrence. If later execution
requires recovery and the action is compensatable, CNCF schedules/interprets the
explicit compensation Action Program.

Compensation rules:

- only completed forward occurrences are eligible;
- every compensation occurrence has stable identity;
- retries reuse the same logical occurrence identity;
- default ordering is reverse causal order;
- partial compensation is a durable outcome;
- failed compensation enters recovery rather than pretending rollback;
- audit/history retain both forward and compensation occurrences.

## Irreversible Effects

An irreversible effect is an execution boundary beyond which technical rollback
is impossible. CNCF records this fact and exposes recovery requirements.

Irreversible effects may still be valid, but later failure can require a new
corrective action, business process, or operator intervention.

## Orthogonal Effect Semantics

Prefer separate concepts for transaction capability and reversibility:

```text
TransactionCapability
  LocalAtomic
  TwoPhaseCapable
  NonTransactional

Reversibility
  TransactionRollback
  Compensation(actionRef)
  Irreversible
```

The generated ABI may encode equivalent structures. CNCF must reject
contradictory combinations rather than normalize them heuristically.

## Composite Program Planning

Constituent and composite StateMachine actions are composed first at the model
level. Planning then determines execution boundaries.

```text
constituent Program
   *>
composite Program
      |
      v
combined logical Program
      |
      v
planner segmentation
```

Action provenance survives segmentation so diagnostics and recovery can trace
back to the exact constituent/composite transition and CML source.

## Failure Semantics

Distinguish at least:

- planning/admission failure;
- atomic action/interpreter failure before commit;
- atomic persistence/commit failure;
- 2PC prepare failure;
- 2PC in-doubt/recovery condition;
- after-commit forward action failure;
- later failure requiring compensation;
- compensation failure/retry exhaustion;
- failure after irreversible effect.

These outcomes must not collapse into one generic Workflow failure.

## Idempotency

External forward and compensation actions need stable logical identities.
Duplicate delivery, crash recovery, or retry must not create duplicate logical
business effects where the target/action contract supports deduplication.

If an action is intrinsically non-idempotent and cannot be deduplicated, the
execution plan must surface the risk explicitly and may reject automatic retry.

## Recovery State

CNCF may persist recovery metadata separate from derived Composite StateMachine
business state. Examples include:

- pending forward action;
- pending compensation;
- compensation failed;
- distributed transaction in doubt;
- irreversible boundary crossed;
- manual recovery required.

These are execution/recovery states and must not silently become CML domain
states unless the model explicitly defines them.

## Observability

Correlation should connect:

```text
CML action definition
 -> generated ActionOp
 -> program occurrence
 -> plan segment
 -> transaction/participant or after-commit execution
 -> forward effect
 -> compensation if any
 -> recovery outcome
```

Provider credentials and sensitive payloads remain excluded.

## Compatibility with StateMachine Commit Semantics

Phase 64.1 must preserve Phase 63's commit authority. A `CommittedTransition`
is published only when the transition's required atomic segment has committed.

After-commit failures do not retroactively uncommit the transition. They are
handled by retry, compensation, corrective action, or explicit recovery.

## Open Decisions

1. Existing CNCF support for XA/JTA/2PC and whether Phase 64.1 implements or
   only defines the capability contract.
2. Exact atomic-segment declaration/derivation from generated Action Programs.
3. Compensation stack/graph persistence representation.
4. Handling multiple independent external effects that can compensate in
   parallel versus reverse sequence.
5. Retry policy authority for forward versus compensation actions.
6. Interaction with future Saga Management without duplicating semantics.
7. Whether irreversible boundaries require an explicit operator acknowledgment
   policy in some runtimes.

## Governing Principles

> Atomic guarantees must never be silently downgraded.

> Compensation is an explicit new business action, not rollback.

> Irreversible effects must be represented honestly.

> CML owns logical semantics; CNCF owns transaction, interpreter, and recovery
> execution.
