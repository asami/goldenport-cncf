# Composite StateMachine / Workflow Runtime Provisional Specification

status = proposed, non-normative
date = 2026-09-05
target_phase = 64

## Status and Authority

This note records the provisional Phase 64 contract after the decision to treat
Workflow as a specialization of Composite StateMachine and to maximize reuse of
StateMachine semantics.

It does not override verified StateMachine behavior, generated ABI, source code,
or Executable Specifications. Accepted behavior moves to `docs/design` and
`docs/spec` after cross-repository verification.

## Core Model

The working classification is:

```text
StateMachine
  +-- local/simple StateMachine
  +-- Composite StateMachine
        +-- Workflow
             + workflow-specific mandatory semantics only
```

The model intentionally avoids assuming that common workflow-system concepts
are Workflow-specific. State, transition, trigger/event, guard/predicate,
action/effect, timer, hierarchy/history, and similar concepts must first be
considered StateMachine or Composite StateMachine capabilities.

## Composite StateMachine

A Composite StateMachine coordinates one or more constituent StateMachines and
presents a higher-level machine boundary.

Conceptually:

```text
CompositeStateMachineDefinition(
  id,
  version,
  constituents,
  states/configuration,
  transitions,
  triggers,
  predicates,
  actions/effects
)

ConstituentStateMachineBinding(
  role,
  machineRef,
  subjectBinding?,
  sourceLocation
)
```

Exact CML syntax is owned by Cozy and remains subject to producer-side design.
The runtime contract should not freeze syntax before the CML model is stable.

Constituent StateMachines retain their own domain identity, state, guard, and
transition authority. A composite transition may coordinate or react to their
committed transitions but must not bypass the local transition boundary.

## Workflow Specialization

Workflow is a Composite StateMachine specialization/profile.

The initial design rule is not to predeclare a large Workflow-specific model.
Instead, each proposed Workflow concept must be classified as one of:

1. existing StateMachine semantics;
2. general Composite StateMachine semantics;
3. mandatory Workflow specialization;
4. runtime infrastructure/policy.

Only class (3) becomes Workflow-specific CML semantics.

Candidate concerns that require investigation include:

- independent process-instance identity;
- correlation across multiple subjects;
- durable progression/wait semantics;
- pending-work semantics;
- process completion/cancellation semantics;
- process-oriented history.

None is automatically accepted as Workflow-specific until Composite
StateMachine generalization has been evaluated.

## CML-First Continuity

StateMachine and Workflow share one source-to-runtime architecture:

```text
CML
 -> parse / normalize
 -> SimpleModeler generation
 -> typed generated definition
 -> ComponentFactory automatic bootstrap
 -> CNCF runtime
```

For Composite StateMachine/Workflow the generated contract must preserve:

- composite definition identity/version;
- constituent machine identity and role;
- referenced Entity/Aggregate/subject identity where applicable;
- higher-level state/configuration identity;
- transition/trigger identity;
- explicit relation to constituent committed transitions;
- predicates/actions reused from the StateMachine contract where applicable;
- Operation references where the model declares execution;
- source-location diagnostics; and
- ABI/version admission metadata.

No coincidental name matching is permitted.

## Runtime Boundary

CNCF owns execution concerns including:

- instance lifecycle and persistence;
- constituent transition observation/admission;
- composite progression;
- concurrency/idempotency;
- durable recovery;
- Operation invocation;
- JobEngine linkage;
- authorization/context propagation;
- correlation and observability.

These runtime concerns do not automatically imply new CML syntax. A concept
belongs in CML only when it is part of the model's meaning rather than runtime
implementation policy.

## StateMachine Authority

A committed constituent transition is the safe coordination fact.

```text
Constituent StateMachine
  -> local transition selection
  -> UnitOfWork commit
  -> CommittedTransition
  -> Composite StateMachine / Workflow progression
```

A composite/workflow execution that needs another domain change invokes the
normal Operation/StateMachine path:

```text
Composite / Workflow
  -> Operation / Job
  -> constituent StateMachine
  -> CommittedTransition
  -> composite progression
```

The composite layer never writes constituent domain status directly.

## Instance Semantics

The runtime may need a durable composite/workflow instance separate from each
constituent domain Entity. The exact distinction between a general
`CompositeStateMachineInstance` and a Workflow-specific `WorkflowInstance` is
an explicit Phase 64 design question.

The preferred direction is to generalize as much instance machinery as
possible:

```text
CompositeStateMachineInstance
  definitionId/version
  instanceId
  constituent bindings
  current composite configuration
  consumed trigger/transition occurrences
  progression/history
  correlation
  version
```

Workflow-specific instance fields should be added only when justified by
mandatory Workflow semantics.

## Idempotency and Recovery

- committed transition occurrences may be delivered more than once;
- one logical composite progression must have a stable occurrence identity;
- retry after partial failure must reuse stable logical Operation/Job identity;
- crash windows between progression decision, submission, and persistence must
  be closed or explicitly recoverable;
- duplicate-delivery safety does not imply deterministic code-history replay;
- concurrent progression requires optimistic concurrency or another explicit
  serialization mechanism.

## Observability

One correlation path should be able to expose:

```text
CML composite/workflow definition
  -> constituent machine / CommittedTransition
  -> composite instance/configuration
  -> composite transition/progression
  -> Operation / Job
  -> next constituent transition
```

Definition identities and occurrence identities remain distinct.

## Compatibility

Phase 14 Workflow behavior is a compatibility input, not the semantic authority
for the new model.

Legacy raw event/status-field matching must be explicitly mapped or rejected.
It must not silently define Composite StateMachine/Workflow semantics.

## Executable Specification Matrix

Evidence should cover:

- CML-first generation/bootstrap;
- deterministic constituent binding;
- multiple constituent StateMachines;
- committed-transition-only progression where required;
- no bypass of constituent transition authority;
- reuse of StateMachine predicates/actions without duplicate semantics;
- classification of every Workflow-specific addition;
- composite/workflow instance persistence and recovery;
- duplicate delivery and crash-window handling;
- Operation/Job delegation through ordinary authorization boundaries;
- observability linking CML source, composite definition, constituent machines,
  runtime occurrence, Operation, and Job; and
- representative end-to-end CML acceptance.

## Open Decisions

1. Exact CML syntax for Composite StateMachine composition.
2. Whether constituent machines are owned, referenced, or role-bound in each
   CML context; the default should avoid accidental ownership semantics.
3. Representation of composite state/configuration versus constituent states.
4. Which instance semantics are general Composite StateMachine semantics.
5. Which, if any, semantics are mandatory only for Workflow.
6. How Operation invocation is represented without duplicating StateMachine
   action/effect semantics.
7. Durable waiting/timer/correlation semantics and whether each belongs to
   Composite StateMachine, Workflow specialization, or runtime policy.
8. Phase 14 compatibility lifetime.

## Governing Principle

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.

## Related Documents

- `docs/phase/phase-64.md`
- `docs/phase/phase-64-checklist.md`
- `docs/phase/phase-63.md`
- `docs/phase/phase-14.md`
- `docs/journal/2026/09/2026-09-05-composite-statemachine-workflow-direction.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/design/execution-platform-boundary.md`
