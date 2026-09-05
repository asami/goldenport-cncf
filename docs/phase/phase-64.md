# Phase 64 - CML Workflow Runtime Integration

status=planned
planned_at=2026-08-12
revised_at=2026-09-05
depends_on=[Phase 63](phase-63.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 64 Checklist](phase-64-checklist.md)

## Purpose

Complete the executable path from a CML Workflow declaration to CNCF Workflow
runtime execution, preserving direct continuity with Phase 63's CML
StateMachine runtime path.

The canonical end-to-end shape is:

```text
CML StateMachine
  -> parse / normalize
  -> SimpleModeler generation
  -> typed generated definition
  -> ComponentFactory bootstrap
  -> CNCF StateMachine Runtime
  -> CommittedTransition

CML Workflow
  -> parse / normalize
  -> SimpleModeler generation
  -> typed generated definition
  -> ComponentFactory bootstrap
  -> CNCF Workflow Runtime
  -> WorkflowInstance
  -> Operation / Job
  -> StateMachine
  -> CommittedTransition
```

CML owns the Workflow model and its declared semantics. CNCF does not introduce
an independent Workflow language or infer workflow structure from runtime
names. CNCF owns execution semantics: WorkflowInstance lifecycle, trigger
admission, durable progression, invocation, Job linkage, persistence,
recovery, authorization integration, and observability.

The reference model is a `SalesOrder` entity whose `SalesStatus` StateMachine
governs local domain validity and whose CML-declared `SalesOrderWorkflow`
orchestrates the next Operation or Job after a committed transition.

## Dependency

Phase 64 begins after Phase 63 closes.

It consumes Phase 63's canonical CML-to-runtime path and `CommittedTransition`
envelope. It also reconciles and supersedes the parts of Phase 14's lightweight
event-triggered, entity-status-based Workflow baseline that conflict with the
CML-authoritative model.

## Selected Direction

- CML is the canonical declaration surface for built-in Workflow semantics,
  just as it is for StateMachine semantics.
- StateMachine and Workflow use the same architectural pipeline: CML ->
  normalization -> generated typed definition -> ComponentFactory bootstrap ->
  CNCF runtime.
- A hand-written WorkflowDefinition or manually injected provider may be useful
  for focused unit tests, but it is not sufficient end-to-end acceptance
  evidence.
- CNCF must not define a second Workflow DSL or reconstruct CML Workflow
  semantics through name/status matching.
- StateMachine owns local Entity/Aggregate transition semantics and never
  becomes a Workflow engine.
- Workflow owns cross-Operation/process progression and never writes domain
  entity status directly.
- JobEngine remains the execution substrate for asynchronous work.
- A Workflow advances only from a committed transition or another explicitly
  CML-admitted trigger; an attempted or rolled-back transition cannot advance
  it.
- CML carries explicit typed references among Entity, StateMachine,
  transition/trigger, Workflow, step, and Operation identities.
- `SalesStatus` and `WorkflowInstance.status` are separate state spaces with
  separate persistence, history, and recovery contracts.
- Workflow invokes the next Operation through the generic CNCF invocation and
  authorization boundary; that Operation may request the next Entity
  transition through Phase 63.
- Trigger handling is idempotent and duplicate-delivery-safe through stable
  transition, Workflow definition, WorkflowInstance, and step-occurrence
  identities. This does not imply Temporal-style deterministic code replay.
- CNCF implements the executable subset declared by the accepted CML Workflow
  contract. CNCF must not independently grow workflow-language features ahead
  of CML semantics.
- Specialist workflow engines remain an explicit integration boundary for
  orchestration semantics outside the accepted CML/CNCF built-in contract.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SWF-01 | CML/runtime inventory and responsibility freeze | Existing CML Workflow semantics, Phase 14 runtime behavior, Phase 63 pipeline, and ownership boundaries are frozen by failing-first evidence. | planned |
| SWF-02 | Canonical CML Workflow runtime model | CML Workflow identity, triggers, steps, conditions, Operation references, terminal outcomes, and StateMachine bindings normalize into one canonical typed model. | planned |
| SWF-03 | Committed-transition and trigger contract | Phase 63 `CommittedTransition` and other explicitly admitted CML triggers map to Workflow entries with stable correlation/idempotency semantics. | planned |
| SWF-04 | Generation, ABI, and bootstrap propagation | SimpleModeler emits stable typed Workflow definitions/metadata and ComponentFactory automatically registers them without required hand-written runtime definitions. | planned |
| SWF-05 | CNCF Workflow execution | Workflow runtime consumes generated definitions, advances WorkflowInstance, and delegates Operations/Jobs without direct domain mutation. | planned |
| SWF-06 | WorkflowInstance persistence and recovery | Instance state, history, step occurrence, Job links, duplicate delivery, retry, recovery, and concurrency are authoritative and durable. | planned |
| SWF-07 | Observability and compatibility | CML source identity, generated/runtime identity, domain/workflow state, security, diagnostics, and explicit Phase 14 compatibility are visible without semantic inference. | planned |
| SWF-08 | CML-first cross-repository acceptance and promotion | A generated SalesOrder/SalesStatus/SalesOrderWorkflow scenario proves the complete CML -> Cozy/SimpleModeler -> CNCF path and promotes verified contracts. | planned |

## Acceptance

- One CML Workflow declaration yields one canonical generated Workflow
  definition consumed by CNCF runtime.
- The representative acceptance begins with CML source and crosses parser,
  normalization, SimpleModeler generation, generated provider/metadata,
  ComponentFactory automatic bootstrap, and the real CNCF Workflow runtime.
- A hand-written WorkflowDefinition or manually injected registration cannot
  substitute for that acceptance path.
- A committed `SalesOrder`/`SalesStatus` transition can start or advance the
  explicitly bound CML `SalesOrderWorkflow`.
- A failed, rejected, non-matching, or rolled-back transition cannot advance a
  WorkflowInstance.
- Workflow selects the next CML-declared Operation and delegates through
  CNCF/JobEngine; it never directly mutates `SalesOrder.status`.
- The invoked Operation follows normal authorization, idempotency, UnitOfWork,
  StateMachine, error, and observability boundaries.
- Any domain transition caused by a Workflow-selected Operation returns through
  Phase 63 and may produce the next `CommittedTransition`.
- Domain state and WorkflowInstance state remain distinct in storage,
  projection, history, diagnostics, and recovery.
- Duplicate trigger delivery and recovery do not create duplicate progression
  or duplicate logical Operation/Job submission.
- Transition definition identity and transition occurrence identity remain
  distinct and correlated with CML Workflow/step/Operation identities.
- Unknown or unsupported CML Workflow semantics fail generation/admission; they
  do not degrade to raw strings, name matching, or silently different runtime
  behavior.
- Legacy Phase 14 raw-event/status-field triggers are mapped through an explicit
  compatibility adapter or rejected.

## Non-Goals

- Defining a CNCF-specific Workflow language independent of CML.
- Reconstructing Workflow semantics from coincidental Entity, state, event,
  Workflow, step, or Operation names.
- Moving local domain invariants or transition ownership into Workflow.
- Direct Workflow mutation of Entity or Aggregate state.
- Merging domain StateMachine state and WorkflowInstance state.
- Implementing orchestration constructs that are not part of the accepted CML
  Workflow contract merely because the CNCF runtime could support them.
- Replacing JobEngine, Event, generic Operation invocation, or external
  specialist workflow engines.
- Executable DbC, which follows in Phase 65.
- Generic Event/JCL expansion, distributed runtime, or Saga Management retained
  by their existing development candidates.

## StateMachine Continuity Rule

Phase 64 deliberately mirrors Phase 63.

```text
CML model declaration
       |
       v
canonical normalized model
       |
       v
generated typed definition / ABI
       |
       v
ComponentFactory automatic bootstrap
       |
       v
CNCF execution runtime
       |
       v
observable durable outcome
```

For StateMachine the durable domain outcome is a committed Entity transition and
`CommittedTransition`. For Workflow it is authoritative WorkflowInstance
progression plus correlated Operation/Job linkage.

Neither runtime owns the source modeling language. Neither end-to-end acceptance
may bypass CML generation.

## Cozy Coordination

Phase 64 is a cross-repository contract with Cozy/SimpleModeler.

Cozy must provide or preserve:

- CML Workflow parsing and semantic validation;
- stable Workflow, trigger, step, condition, and referenced model-element ids;
- explicit StateMachine/transition-to-Workflow binding;
- explicit Operation references;
- deterministic normalization;
- generated typed Workflow definitions and metadata;
- source-location diagnostics;
- ABI/version information needed by CNCF admission; and
- cross-repository acceptance fixtures starting from CML source.

CNCF consumes these generated contracts. It does not compensate for missing CML
semantics by inventing runtime-only Workflow definitions.

## Development Candidate Alignment

Existing related development candidates remain independently owned. Phase 64
consumes only the portions required to execute the accepted CML Workflow model.
In particular, generic event expansion, rich compensation, distributed
coordination, general Job UX, transport idempotency, and specialist workflow
features are not implicitly absorbed.

## Planning References

- [Phase 64 Checklist](phase-64-checklist.md)
- [Provisional Specification](../notes/statemachine-workflow-alignment-provisional-specification.md)
- [Sequencing Record](../journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md)
- [Phase 63](phase-63.md)
- [Phase 14](phase-14.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
- [Execution Platform Boundary](../design/execution-platform-boundary.md)
