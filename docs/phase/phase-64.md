# Phase 64 - CML Composite StateMachine / Workflow Runtime Integration

status=planned
planned_at=2026-08-12
revised_at=2026-09-05
depends_on=[Phase 63](phase-63.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 64 Checklist](phase-64-checklist.md)

## Purpose

Extend the Phase 63 CML StateMachine path into Composite StateMachine and
Workflow execution while preserving one continuous CML -> generated model ->
CNCF runtime architecture.

The selected modeling direction is:

```text
StateMachine
  |
  +-- simple / local StateMachine
  |
  +-- Composite StateMachine
        |
        +-- Workflow
             + workflow-specific mandatory semantics only
```

Workflow is therefore not designed as an unrelated orchestration language.
Its first design obligation is to reuse Composite StateMachine structure as far
as possible. A semantic or runtime concept is Workflow-specific only when it
cannot be represented cleanly as a general Composite StateMachine capability.

## Continuity with Phase 63

The canonical paths are:

```text
CML StateMachine
  -> parse / normalize
  -> SimpleModeler generation
  -> typed generated definition
  -> ComponentFactory bootstrap
  -> CNCF StateMachine Runtime
  -> CommittedTransition

CML Composite StateMachine / Workflow
  -> parse / normalize
  -> SimpleModeler generation
  -> typed generated definition
  -> ComponentFactory bootstrap
  -> CNCF composite/workflow runtime
  -> durable composite progression
  -> Operation / Job where declared
  -> constituent StateMachine transition
  -> CommittedTransition
```

CML owns model semantics. CNCF owns execution semantics. CNCF must not invent a
second Workflow DSL or reconstruct model meaning through names/status fields.

## Selected Direction

- Composite StateMachine is the primary abstraction added above Phase 63.
- Workflow is initially treated as a specialization/profile of Composite
  StateMachine, not as a separate modeling universe.
- Workflow should reuse State, Transition, Trigger/Event, Guard/Predicate,
  Action/Effect, hierarchy/history, identity, and other admitted StateMachine
  semantics whenever they apply.
- Phase 64 must inventory every proposed Workflow concept and classify it as:
  1. existing StateMachine semantics;
  2. general Composite StateMachine semantics;
  3. truly mandatory Workflow-specific semantics; or
  4. runtime policy/infrastructure rather than model semantics.
- Workflow-specific semantics are added only after this classification proves
  they cannot live cleanly in Composite StateMachine.
- A Composite StateMachine may coordinate multiple constituent StateMachines
  while presenting one higher-level machine boundary.
- Constituent machines retain their own local domain authority; composition
  does not authorize direct mutation that bypasses their transition contract.
- StateMachine and Workflow use the same CML-first generation/bootstrap
  architecture.
- A hand-written WorkflowDefinition or manually injected provider may support
  focused tests but is not valid end-to-end acceptance evidence.
- JobEngine remains the execution substrate for asynchronous work.
- Operation invocation, authorization, idempotency, persistence, recovery, and
  observability remain CNCF runtime responsibilities.
- Runtime capabilities must follow accepted CML Composite StateMachine/Workflow
  semantics rather than independently defining a richer workflow language.

## Primary Design Question

Phase 64 does not assume in advance which semantics are uniquely required by
Workflow.

Candidate concerns such as process-instance identity, correlation across
multiple subjects, durable waiting/progression, pending work, completion, and
history must first be tested against the more general Composite StateMachine
model.

The governing rule is:

> Maximize reuse of Composite StateMachine semantics; introduce
> Workflow-specific semantics only when they are required for Workflow to exist
> and cannot be expressed cleanly as general Composite StateMachine behavior.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SWF-01 | StateMachine/Workflow semantic inventory | Existing CML StateMachine, proposed Workflow, Phase 14 Workflow, and runtime concepts are classified into StateMachine, Composite StateMachine, Workflow-specific, and runtime-only concerns. | planned |
| SWF-02 | Composite StateMachine model | Constituent-machine composition, higher-level state/configuration, transition/trigger coordination, identity, hierarchy, and projection semantics are frozen without Workflow-specific assumptions. | planned |
| SWF-03 | Minimal Workflow specialization | Only semantics proven mandatory beyond Composite StateMachine are added as Workflow specialization/profile. | planned |
| SWF-04 | CML normalization and generated ABI | Cozy/SimpleModeler emits stable typed Composite StateMachine and Workflow definitions with explicit constituent-machine/model references. | planned |
| SWF-05 | CNCF composite execution | CNCF executes admitted generated composite definitions while preserving constituent StateMachine authority and Phase 63 transition rules. | planned |
| SWF-06 | Durable instance/progression | Runtime state, correlation, persistence, recovery, idempotency, Operation/Job linkage, and crash-window rules are made authoritative where required. | planned |
| SWF-07 | Observability and compatibility | Composite configuration, constituent state, Workflow specialization, source identity, compatibility adapters, and failure evidence are visible and distinct. | planned |
| SWF-08 | CML-first cross-repository acceptance | A real CML example proves composite machine structure, Workflow specialization, generation, bootstrap, CNCF execution, and return through Phase 63. | planned |

## Acceptance

- One CML Composite StateMachine/Workflow source yields one deterministic typed
  generated runtime definition.
- The end-to-end path starts from CML and crosses Cozy normalization,
  SimpleModeler generation, ComponentFactory bootstrap, and CNCF runtime.
- A Composite StateMachine can coordinate multiple constituent StateMachines
  without erasing their individual identity or transition authority.
- Higher-level composite progression and constituent machine state remain
  traceably related but are not conflated.
- Workflow uses Composite StateMachine semantics wherever possible.
- Every Workflow-only field/type/behavior introduced by Phase 64 has explicit
  evidence that it is mandatory and cannot reasonably be generalized to
  Composite StateMachine.
- State/transition/guard/action concepts are not duplicated merely because the
  containing model is a Workflow.
- Workflow/composite execution never directly bypasses Phase 63 local
  StateMachine enforcement.
- Operations and Jobs are invoked through normal CNCF boundaries.
- Duplicate trigger delivery and recovery do not duplicate logical progression.
- Unknown or unsupported CML semantics fail generation/admission rather than
  degrading into raw strings or inferred runtime behavior.

## Non-Goals

- Defining Workflow first and retrofitting StateMachine concepts afterward.
- Creating a CNCF-specific Workflow language independent of CML.
- Treating Activity, Guard, Event, Action, Timer, or another concept as
  Workflow-specific solely because workflow systems commonly expose it.
- Assuming UML orthogonal regions, BPMN, DAG, human-task, compensation, or rich
  connector semantics are required for the initial Composite StateMachine.
- Moving local domain invariants or transition ownership out of constituent
  StateMachines.
- Executable DbC, which follows in Phase 65.

## Cozy Coordination

Cozy is expected to introduce/refine the CML Composite StateMachine model before
CNCF freezes runtime-only abstractions.

The producer-side work must determine:

- how one StateMachine composes/references constituent StateMachines;
- how composite identity and constituent role/identity are represented;
- how higher-level states/transitions relate to constituent-machine
  transitions;
- which existing StateMachine grammar/model elements are reused unchanged;
- which semantics belong to general Composite StateMachine;
- which semantics, if any, are truly mandatory only for Workflow;
- stable generated ids/source locations/ABI metadata; and
- how CML Workflow is represented as a specialization/profile of the composite
  model.

CNCF consumes the resulting typed contracts and must preserve that modeling
structure through runtime execution and projection.

## Planning References

- [Phase 64 Checklist](phase-64-checklist.md)
- [Provisional Specification](../notes/statemachine-workflow-alignment-provisional-specification.md)
- [Phase 63](phase-63.md)
- [Phase 14](phase-14.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
- [Execution Platform Boundary](../design/execution-platform-boundary.md)
- [Composite StateMachine / Workflow Decision](../journal/2026/09/2026-09-05-composite-statemachine-workflow-direction.md)
