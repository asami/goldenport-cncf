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
  configurationSchema,
  compositeStateRules,
  derivedTransitions,
  actions
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

## Derived Composite State

The preferred runtime/model contract is that the constituent state
configuration is authoritative and the higher-level composite state is derived
from explicit pure rules.

```text
constituent state configuration
        |
        v
CompositeStateRule evaluation
        |
        v
CompositeState
```

For example:

```text
order    = Accepted
payment  = Authorized
shipment = Waiting
        |
        v
ReadyToShip
```

CNCF must not maintain an independently mutable copy of the same business
progress state when it is derivable from the constituent configuration.

The generated rule IR must be deterministic and inspectable. The normal
admission expectation is:

```text
0 matching rules  -> structured unmapped-configuration outcome
1 matching rule   -> derived composite state
2+ matching rules -> structured ambiguity outcome
```

Any intentionally partial mapping must be explicit in the generated contract.

## Derived Composite Transition

A constituent transition first commits under the ordinary StateMachine and
UnitOfWork rules. Only then can the Composite StateMachine evaluate the new
configuration.

```text
constituent transition
  -> constituent commit
  -> CommittedTransition
  -> recompute configuration
  -> derive old/new composite state
  -> derive composite transition when the composite state changed
```

The composite transition graph should normally be generated/analyzed from the
constituent transition graphs plus the CML composite-state rules rather than
invented independently inside CNCF.

CNCF consumes the generated graph/rule metadata and verifies runtime occurrences
against the admitted definition.

## Static Analysis Contract

Most completeness and ambiguity checks belong to Cozy/SimpleModeler before
runtime. CNCF should receive the verified typed result and enough metadata for
runtime admission/diagnostics.

Useful producer-side findings include:

- uncovered reachable configurations;
- overlapping rules;
- impossible/redundant rules;
- unreachable/dead composite states;
- unexpected derived transitions; and
- excessive configuration complexity.

CNCF should still fail structurally if a runtime configuration violates the
admitted contract. It must not choose an arbitrary rule or priority fallback
unless such behavior is explicitly part of the generated model.

## Actions at Constituent and Composite Levels

Actions may exist at both StateMachine levels and coexist.

```text
constituent transition
  action A
      |
      v
constituent commit
      |
      v
derived composite transition
  action B
```

Action A and Action B have distinct model provenance and semantics. CNCF should
not flatten them into one opaque callback or discard their ordering identity.

The default causal order is:

1. select constituent transition;
2. construct constituent local action program;
3. commit admitted constituent local effects;
4. publish/observe the committed constituent transition;
5. derive the new composite state/transition;
6. construct composite action program; and
7. interpret the admitted composite program under CNCF execution policy.

The exact boundary may be refined where purely local composite effects can be
planned atomically, but no external effect may be executed before the commit
whose fact triggers it.

## Typed Action Algebra / Free Program

The canonical generated action contract should be a typed logical program, not
an arbitrary Scala function or provider callback.

Conceptually:

```text
CML action declaration
   -> generated ActionOp algebra
   -> Free Action Program
   -> CNCF planner/interpreter
   -> runtime effects
```

The implementation may use `Free[F, A]` or an equivalent free-program
representation. The contract depends on these properties:

- pure composability before execution;
- deterministic ordering;
- inspectability;
- no embedded datastore/provider handles;
- multiple interpreters for production/test/simulation/review;
- typed logical effect identity where possible; and
- explicit failure when a required action has no admitted interpreter/binding.

A practical algebra may be factored into reusable effect families such as:

```text
EntityAction
EventAction
OperationAction
JobAction
RuntimeAction
```

The exact sum/coproduct encoding remains an implementation decision.

## Action Composition

Constituent and composite programs should compose through one mechanism.
Conceptually:

```text
constituentProgram *> compositeProgram
```

The composed logical plan must preserve provenance so observability and review
can still identify which operations originated from the constituent transition
and which from the derived composite transition.

Where multiple composite levels exist, composition follows the causal chain
from the innermost committed transition toward enclosing derived transitions.
Cycle detection and bounded progression are required before any implementation
admits recursive composite triggering.

## Effect Planning and Interpreter Boundary

A Free program is not itself a transaction. CNCF owns effect planning and
interpretation.

The interpreter/planner must classify admitted operations into execution
boundaries such as:

```text
Local / UnitOfWork effects
After-commit effects
```

Typical examples:

- Entity/Aggregate mutation may be a local effect when it participates in the
  admitted UnitOfWork contract.
- Event publication, generic Operation invocation, Job submission, external
  service/process calls, and other externally observable work are after-commit
  unless an existing CNCF contract explicitly states otherwise.

CML should not carry datastore transaction mechanics. The generated action
algebra carries logical intent; CNCF maps that intent to runtime policy.

## Action Analysis

Because action programs are typed and inspectable, CNCF and upstream review
surfaces may detect or reject issues such as:

- duplicate logical external operations produced at multiple abstraction
  levels;
- conflicting local mutations;
- unsupported effect ordering;
- external effects attempted in a local-only phase;
- missing action interpreter/provider binding; and
- retry/idempotency hazards when sufficient metadata exists.

These checks complement, rather than replace, Composite StateMachine rule
analysis.

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
 -> static composite analysis
 -> SimpleModeler generation
 -> typed generated definition + Action Program
 -> ComponentFactory automatic bootstrap
 -> CNCF runtime + Action Interpreter
```

For Composite StateMachine/Workflow the generated contract must preserve:

- composite definition identity/version;
- constituent machine identity and role;
- referenced Entity/Aggregate/subject identity where applicable;
- configuration schema;
- pure typed composite-state derivation rules;
- derived composite state/transition identity;
- explicit relation to constituent committed transitions;
- action program and action provenance;
- Workflow specialization marker/metadata when applicable;
- Operation references where represented by the action algebra;
- source-location diagnostics; and
- ABI/version admission metadata.

No coincidental name matching or opaque runtime callback substitution is
permitted for required semantics.

## Runtime Boundary

CNCF owns execution concerns including:

- instance lifecycle and persistence where needed;
- constituent transition observation/admission;
- composite configuration evaluation;
- derived composite progression;
- action planning and interpretation;
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
  -> Composite StateMachine / Workflow derivation
```

A composite/workflow execution that needs another domain change invokes the
normal Operation/StateMachine path:

```text
Composite / Workflow Action Program
  -> CNCF Interpreter
  -> Operation / Job
  -> constituent StateMachine
  -> CommittedTransition
  -> composite derivation
```

The composite layer never writes constituent domain status directly outside the
ordinary admitted action/StateMachine boundaries.

## Instance Semantics

A durable composite/workflow instance should be introduced only for information
that cannot be derived from constituent states/configuration and durable runtime
history.

The exact distinction between a general `CompositeStateMachineInstance` and a
Workflow-specific `WorkflowInstance` remains a Phase 64 question.

Do not persist a duplicate composite business status merely for convenience if
it is deterministically derivable. Persisting configuration correlation,
consumed transition occurrences, pending after-commit action work, history, and
recovery metadata may still be required.

## Idempotency and Recovery

- committed transition occurrences may be delivered more than once;
- one logical composite progression must have a stable occurrence identity;
- one logical interpreted action program must retain stable correlation;
- retry after partial failure must reuse stable logical Operation/Job identity;
- crash windows between derivation, action planning, submission, and persistence
  must be closed or explicitly recoverable;
- duplicate-delivery safety does not imply deterministic code-history replay;
- concurrent progression requires optimistic concurrency or another explicit
  serialization mechanism.

## Observability

One correlation path should expose:

```text
CML composite/workflow definition
  -> constituent machine / CommittedTransition
  -> configuration + matched composite rule
  -> derived composite transition
  -> generated Action Program
  -> interpreted local/after-commit actions
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
- rule coverage and ambiguity admission;
- derived composite state/transition behavior;
- committed-transition-only derivation where required;
- coexistence and deterministic ordering of constituent/composite actions;
- generated typed Free/action programs;
- production and test interpreter behavior;
- UnitOfWork versus after-commit effect classification;
- no bypass of constituent transition authority;
- classification of every Workflow-specific addition;
- duplicate delivery and crash-window handling;
- Operation/Job delegation through ordinary authorization boundaries;
- observability linking CML source, rules, action provenance, runtime occurrence,
  Operation, and Job; and
- representative end-to-end CML acceptance.

## Open Decisions

1. Exact CML syntax for Composite StateMachine composition.
2. Exact CML rule syntax for derived composite states.
3. Total versus explicit partial mapping policy.
4. Configuration enumeration limits and symbolic-analysis fallback.
5. Representation/identity of a derived composite transition.
6. Exact generated ActionOp algebra and Free-program ABI.
7. Interpreter binding/discovery through ComponentFactory.
8. Whether any local composite actions can join the same root UnitOfWork rather
   than executing after constituent commit.
9. Recursion/nesting limits and causal ordering for nested composites.
10. Which instance semantics are general Composite StateMachine semantics.
11. Which, if any, semantics are mandatory only for Workflow.
12. Durable waiting/timer/correlation semantics and whether each belongs to
    Composite StateMachine, Workflow specialization, or runtime policy.
13. Phase 14 compatibility lifetime.

## Governing Principles

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.

> Constituent states are the business-state authority; derive the composite
> state from explicit rules whenever possible.

> Model actions as typed composable programs and execute effects only through
> CNCF interpreters.

## Related Documents

- `docs/phase/phase-64.md`
- `docs/phase/phase-64-checklist.md`
- `docs/phase/phase-63.md`
- `docs/phase/phase-14.md`
- `docs/journal/2026/09/2026-09-05-composite-statemachine-workflow-direction.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/design/execution-platform-boundary.md`
