# SWF-05: Minimal Workflow Specialization Contract

## Purpose

This contract defines the smallest Workflow-profile specialization over the
composite StateMachine model. It is a semantic documentation boundary only. It
does not define runtime execution, provider behavior, persistence, protocols,
retries, compensation, or recovery.

## Typed identities

`WorkflowDefinitionIdentity` is the typed generated Workflow-profile identity
and the exact identity of its definition version. It binds that generated
Workflow profile and exact Workflow definition version to
`CompositeStateMachineDefinitionIdentity` and that Composite StateMachine
definition's exact version. This typed identity and its source/model provenance
arrive from the generated CML contract and are consumed without CML reparsing.

`WorkflowInstanceIdentity` is an opaque, non-durable logical identity
explicitly bound to one `WorkflowDefinitionIdentity`. It is never inferred from
names, lifecycle status, storage keys, or provider handles.

## Lifecycle and progression

`WorkflowLifecycle` is exactly `not-started`, `active`, and `completed`; there
are no other Workflow lifecycle semantics. `CurrentProgression` is the relation to the current derived
composite occurrence/configuration, or is absent before start. It advances only
after the Phase 63.2 post-commit `CommittedTransition` permits SWF-02 to derive
a transition.

This relation does not reexecute or replay an occurrence, fold a transition
into a false atomic operation, trigger from a raw event, or introduce a
duplicate mutable business status.

Correlation is explicitly supplied, not inferred. The complete causal chain is
retained through the Workflow definition and instance, the derived composite
occurrence, and its committed predecessor.

## Specialization boundary

A Workflow-only concept is admitted only when it cannot be represented as
Composite StateMachine semantics. State derivation, authority, admission,
action ordering and provenance, and `ExecProgram[UnitOfWorkOp, A]` remain the
responsibility of SWF-02 through SWF-04.

Phase 64.2 owns planner, interpreter, and executable acceptance. Phase 77 owns
durable store, revision, history, suspension, leases, continuation, context,
replay protection, API/SPI, `ComponentFactory`, Provider, and protocol
semantics. Phase 85 owns 2PC, compensation, and recovery.

## Examples

### Normal progression

For an explicitly identified active Workflow instance, Phase 63.2 commits a
predecessor transition. SWF-02 derives the next composite occurrence and
`CurrentProgression` advances to that derived occurrence, retaining the
definition/instance, occurrence, and committed-predecessor causal chain.

### No derived transition

If SWF-02 derives no transition after a Phase 63.2 `CommittedTransition`,
`CurrentProgression` does not advance. No action is reexecuted, replay is not
created, and no raw event or mutable business status substitutes for the absent
derivation.
