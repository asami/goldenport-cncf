# Composite StateMachine First Workflow Direction

Date: 2026-09-05
Status: design decision / Phase 64 refinement

## Background

The Workflow discussion started from the need to connect CML Workflow to CNCF
with the same source-to-runtime continuity already planned for StateMachine.
The first refinement was to make Workflow CML-first rather than CNCF-DSL-first.

A second, more important refinement followed: much of what had been described
as Workflow semantics already exists naturally in StateMachine semantics.
Activities/actions, triggers/events, guards/predicates, state transitions, and
related execution concepts should not be duplicated merely because the model is
called a Workflow.

The discussion then considered Workflow as a model that coordinates multiple
StateMachines. That raised the possibility that Workflow is best understood as
a specialization of Composite StateMachine rather than as an unrelated model.

## Decision

Proceed with Composite StateMachine as the primary abstraction.

```text
StateMachine
  +-- local/simple StateMachine
  +-- Composite StateMachine
        +-- Workflow
```

Workflow should reuse the Composite StateMachine structure as far as possible.
Only semantics that prove mandatory for Workflow and cannot be represented
cleanly as general Composite StateMachine behavior should be added as
Workflow-specific semantics.

This is intentionally a conservative modeling decision. The exact mandatory
Workflow-only set is not frozen yet.

## Why this direction

The approach provides several benefits:

- continuity with the existing CML StateMachine model;
- less duplicate grammar and runtime machinery;
- one conceptual family for simple, composite, and process-oriented machines;
- better visualization at multiple levels of detail;
- clearer reuse of guards, triggers, actions/effects, hierarchy, and history;
- a stronger CML -> SimpleModeler -> CNCF runtime pipeline;
- the ability to discover Workflow-specific requirements from concrete use
  rather than assuming BPM/workflow-system feature sets in advance.

## Multiple constituent StateMachines

A Composite StateMachine may coordinate multiple constituent StateMachines.
A constituent should normally retain independent identity and domain authority.
Composition must not imply that a Workflow can directly rewrite constituent
state.

Conceptually:

```text
OrderFulfillment
  <<Composite StateMachine / Workflow>>

  order    -> OrderStateMachine
  payment  -> PaymentStateMachine
  shipment -> ShipmentStateMachine
```

From the outside, `OrderFulfillment` can be treated as one higher-level machine.
From the inside, its constituent StateMachines remain separately identifiable
and governed.

This supports both views:

```text
macroscopic:  one composite StateMachine
microscopic:  multiple constituent StateMachines
```

## Workflow-specific semantics remain an open discovery

Candidate requirements discussed included:

- process-instance identity;
- correlation across multiple subjects;
- durable progression/waiting;
- pending work;
- process-oriented completion/cancellation;
- process history.

The decision is not to label these Workflow-specific yet. Each must first be
examined as a possible general Composite StateMachine concept or as CNCF runtime
policy/infrastructure.

## Runtime continuity

The runtime must preserve the CML model rather than invent another abstraction:

```text
CML Composite StateMachine / Workflow
 -> normalize
 -> generate typed definition
 -> ComponentFactory bootstrap
 -> CNCF composite runtime
 -> constituent CommittedTransition / Operation / Job
```

Constituent domain changes still pass through Phase 63 StateMachine execution.
The composite/workflow layer reacts to committed facts and invokes normal CNCF
Operations/Jobs for further work.

## Documentation changes

This decision refines:

- `docs/phase/phase-64.md`
- `docs/notes/statemachine-workflow-alignment-provisional-specification.md`

Cozy has a corresponding CML-side phase, note, and journal to define Composite
StateMachine syntax/model/generation before CNCF freezes implementation details.
