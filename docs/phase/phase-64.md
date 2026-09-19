# Phase 64 - CML Composite StateMachine / Workflow Runtime Integration

status=planned
planned_at=2026-08-12
revised_at=2026-09-19
depends_on=[Phase 63.2](phase-63.2.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 64 Checklist](phase-64-checklist.md)

## Purpose

Extend the completed Phase 63.2 CML StateMachine path into the Composite
StateMachine/Workflow semantic, pure-derivation, and `WorkflowInstance`
contract that Phase 77 consumes for the first executable Workflow vertical
slice. Phase 64 does not itself admit generated Workflow API/SPI artifacts,
resolve Providers, execute Workflow Actions, suspend/resume Continuations, or
project work to Skill.

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

Composite business state should, where possible, be derived from constituent
StateMachine state by explicit CML rules. Actions at constituent and composite
levels use one typed composable action-program model; its planning and
interpretation are owned downstream by Phase 64.1/64.2 and Phase 77.

## Continuity with Phase 63.2

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
  -> static composite analysis
  -> SimpleModeler generation
  -> typed generated definition + action semantic metadata
  -> Phase 64 composite/workflow contract
  -> derive composite state/transition
  -> Phase 77 ABI admission / ComponentFactory bootstrap
  -> canonical ExecProgram / Provider / Continuation execution
  -> optional Operation / Job through normal CNCF boundaries
```

CML owns model semantics. CNCF owns execution semantics. CNCF must not invent a
second Workflow DSL or reconstruct model meaning through names/status fields.

## Selected Direction

- Composite StateMachine is the primary abstraction added above Phase 63.2.
- Workflow is initially treated as a specialization/profile of Composite
  StateMachine, not as a separate modeling universe.
- Workflow should reuse State, Transition, Trigger/Event, Guard/Predicate,
  Action/Effect, hierarchy/history, identity, and other admitted StateMachine
  semantics whenever they apply.
- Composite state is preferably a pure projection of the role-qualified
  constituent StateMachine configuration rather than a duplicate mutable
  business status.
- Composite state rules and the derived composite transition graph are generated
  upstream by Cozy/SimpleModeler and consumed as typed contracts by CNCF.
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
- Constituent and derived composite transitions may both declare actions. Phase
  64 fixes their semantic identity, provenance, and causal order; Phase 64.2
  compiles executable intent into the canonical `ExecProgram[UnitOfWorkOp, A]`,
  and Phase 77 interprets it. No second `ActionOp`/Free-program algebra is
  introduced here.
- An executable program is not a transaction. Phase 64.1/64.2 own
  UnitOfWork segmentation and effect classification; Phase 77 applies those
  contracts to the admitted Workflow vertical slice.
- StateMachine and Workflow use the same CML-first generated-contract
  architecture. Phase 77, not Phase 64, admits generated Workflow API/SPI
  artifacts and bootstraps them through `ComponentFactory`.
- A hand-written WorkflowDefinition or manually injected provider may support
  focused tests but is not valid Phase 77 executable end-to-end acceptance
  evidence.
- JobEngine remains the execution substrate for asynchronous work, but Phase
  64 does not choose, invoke, or schedule a Workflow Operation/Job.
- Operation invocation, Provider execution, authorization, idempotency,
  persistence, recovery, and observability remain CNCF runtime
  responsibilities. Their first Workflow execution acceptance is Phase 77.
- Runtime capabilities must follow accepted CML Composite StateMachine/Workflow
  semantics rather than independently defining a richer workflow language.

## Composite State Derivation Contract

Phase 64 freezes the generated state-configuration schema and pure
composite-state rule IR. An evaluator may consume that contract only as a pure
derivation; Provider execution, external effects, and Continuation progression
are outside this boundary.

The runtime sequence is conceptually:

```text
Committed constituent transition
  -> authoritative constituent state/configuration
  -> evaluate admitted composite rules
  -> derive CompositeState
  -> compare old/new CompositeState
  -> derive CompositeTransition occurrence if changed
```

CNCF does not choose among ambiguous rules by undocumented priority and does not
invent a default for an unmapped configuration. Such conditions are structured
admission/runtime failures unless the CML contract explicitly declares partial
mapping semantics.

Most coverage, ambiguity, reachability, and derived-graph validation belongs to
Cozy/SimpleModeler before generation. CNCF preserves runtime checks so persisted
or externally changed constituent state cannot silently violate the admitted
model.

## Action Semantic and Execution Boundary

Phase 64 defines action identity, provenance, and causal order, rather than a
second executable Action representation.

```text
CML actions
  -> generated typed action semantic metadata / compiler binding
  -> Phase 64.2 canonical ExecProgram[UnitOfWorkOp, A]
  -> UnitOfWork planner / interpreter
  -> Phase 77 admitted Provider / Continuation vertical slice
```

Phase 64 preserves these architectural properties:

- pure, inspectable semantic composition before execution;
- deterministic action ordering;
- provenance for constituent versus composite actions;
- no datastore/provider handles or arbitrary callbacks in generated metadata;
- one compiler target, `ExecProgram[UnitOfWorkOp, A]`, rather than a parallel
  StateMachine/Workflow action algebra; and
- an explicit handoff to Phase 64.1/64.2 for planning/interpreters and to
  Phase 77 for admitted Provider execution and Continuation behavior.

The default causal order is:

```text
constituent transition/action plan
  -> constituent commit
  -> CommittedTransition
  -> derive composite transition
  -> composite action semantic occurrence
  -> later canonical ExecProgram execution where the admitted definition
     declares it
```

No externally visible composite action effect may run before the constituent
commit that causes it. Phase 64 records this causal rule; Phase 77 proves it
through the concrete Provider/Continuation path. Whether purely local composite
effects can ever be folded into a larger atomic boundary is an explicit later
decision and must not weaken Phase 63 commit semantics.

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
| SWF-01 | StateMachine/Workflow semantic inventory | Existing CML StateMachine, action/effect representation, proposed Workflow, Phase 14 Workflow, and runtime concepts are classified into StateMachine, Composite StateMachine, Workflow-specific, and runtime-only concerns. | planned |
| SWF-02 | Composite StateMachine model | Constituent composition, configuration schema, derived state rules, derived transition identity, hierarchy, and projection semantics are frozen without Workflow-specific assumptions. | planned |
| SWF-03 | Composite rule admission and minimal Workflow specialization | Generated coverage/ambiguity analysis and pure rule-evaluation admission are aligned; only semantics proven mandatory beyond Composite StateMachine are added as a Workflow specialization/profile. | planned |
| SWF-04 | CML generated contract and action semantics | Cozy/SimpleModeler emits stable typed Composite StateMachine/Workflow definitions, derived-state metadata, and action semantic/compiler-binding metadata for the canonical `ExecProgram` target. | planned |
| SWF-05 | Pure composite derivation | CNCF evaluates admitted composite derivation without bypassing constituent StateMachine authority or executing Providers/effects. | planned |
| SWF-06 | WorkflowInstance persistence contract | Independent instance identity, definition/version, revision, lifecycle/progression, history, correlation, and persistence SPI are frozen for Phase 77 to bind and execute. | planned |
| SWF-07 | Contract observability and compatibility | Rule match, composite/constituent state, action provenance, Workflow specialization, source identity, compatibility adapters, and contract-failure evidence are visible and distinct. | planned |
| SWF-08 | CML-first contract acceptance | A real CML example proves derived composite state, lower/upper action semantics, generated contract metadata, Workflow specialization, and the exact Phase 63.2 `CommittedTransition` input. Provider execution, Continuation, and Skill acceptance belong to Phase 77. | planned |

## Acceptance

- One CML Composite StateMachine/Workflow source yields one deterministic typed
  generated Composite/Workflow contract.
- The contract path starts from CML and crosses Cozy normalization/static
  analysis and SimpleModeler generation. Phase 77 separately proves generated
  API/SPI admission, `ComponentFactory` bootstrap, and executable runtime use.
- A Composite StateMachine can coordinate multiple constituent StateMachines
  without erasing their individual identity or transition authority.
- Composite business state is derived from constituent configuration under the
  admitted rule model rather than independently mutated by CNCF.
- Ambiguous/unmapped runtime configurations fail according to explicit model
  policy rather than arbitrary fallback.
- A derived composite transition is correlated to the committed constituent
  transition/configuration change that caused it.
- Constituent and composite action semantics coexist with deterministic causal
  order and provenance.
- Action metadata binds to one canonical `ExecProgram[UnitOfWorkOp, A]`
  compilation target; it does not define a second Action algebra.
- Phase 64 fixes that no external composite effect may precede its causal
  commit. Phase 64.1/64.2 and Phase 77 own execution segmentation and proof.
- Workflow uses Composite StateMachine semantics wherever possible.
- Every Workflow-only field/type/behavior introduced by Phase 64 has explicit
  evidence that it is mandatory and cannot reasonably be generalized to
  Composite StateMachine.
- State/transition/guard/action concepts are not duplicated merely because the
  containing model is a Workflow.
- Workflow/composite execution never directly bypasses Phase 63 local
  StateMachine enforcement.
- The `WorkflowInstance` SPI distinguishes definition identity from runtime
  occurrence identity, revision/history, and causal correlation without using
  entity-local state as the process checkpoint.
- Operation/Job invocation, duplicate delivery handling, recovery, Provider
  execution, and external action effects are Phase 77 runtime acceptance.
- Unknown or unsupported CML semantics fail generation/admission rather than
  degrading into raw strings, callbacks, or inferred runtime behavior.

## Non-Goals

- Defining Workflow first and retrofitting StateMachine concepts afterward.
- Creating a CNCF-specific Workflow language independent of CML.
- Treating Activity, Guard, Event, Action, Timer, or another concept as
  Workflow-specific solely because workflow systems commonly expose it.
- Persisting duplicate composite business status when it is deterministically
  derivable solely for implementation convenience.
- Embedding arbitrary Scala/functions/provider handles in generated CML action
  metadata.
- Assuming UML orthogonal regions, BPMN, DAG, human-task, compensation, or rich
  connector semantics are required for the initial Composite StateMachine.
- Moving local domain invariants or transition ownership out of constituent
  StateMachines.
- Generated Workflow API/SPI admission, `ComponentFactory` discovery, Required
  SPI Provider resolution, Provider execution, durable Continuation/resume, or
  Skill/Human/UI/remote-worker projection. These are Phase 77 responsibilities.
- Executable DbC, which follows in Phase 65.

## Cozy Coordination

Cozy is expected to introduce/refine the CML Composite StateMachine model before
CNCF freezes runtime-only abstractions.

The producer-side work must determine:

- how one StateMachine composes/references constituent StateMachines;
- how composite identity and constituent role/identity are represented;
- the pure typed rule model deriving composite state from constituent
  configuration;
- rule coverage/ambiguity/reachability analysis and the derived transition
  graph;
- how constituent and composite action declarations generate typed action
  semantic/compiler-binding metadata for the single Phase 64.2 `ExecProgram`
  target;
- which existing StateMachine grammar/model elements are reused unchanged;
- which semantics belong to general Composite StateMachine;
- which semantics, if any, are truly mandatory only for Workflow;
- stable generated ids/source locations/ABI metadata; and
- how CML Workflow is represented as a specialization/profile of the composite
  model.

CNCF consumes the resulting typed contracts and must preserve that modeling
structure through runtime execution, action interpretation, and projection.

## Planning References

- [Phase 64 Checklist](phase-64-checklist.md)
- [Provisional Specification](../notes/statemachine-workflow-alignment-provisional-specification.md)
- [Phase 63](phase-63.md)
- [Phase 14](phase-14.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
- [Execution Platform Boundary](../design/execution-platform-boundary.md)
- [Composite StateMachine / Workflow Decision](../journal/2026/09/2026-09-05-composite-statemachine-workflow-direction.md)
