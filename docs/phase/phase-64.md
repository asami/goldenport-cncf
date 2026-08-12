# Phase 64 - StateMachine-Workflow Alignment

status=planned
planned_at=2026-08-12
depends_on=[Phase 63](phase-63.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 64 Checklist](phase-64-checklist.md)

## Purpose

Connect committed entity StateMachine transitions to CNCF's lightweight
WorkflowEngine while preserving the two layers' different responsibilities.

The reference model is a `SalesOrder` entity whose `SalesStatus` StateMachine
governs local domain validity and whose external `SalesOrderWorkflow`
orchestrates the next Operation or Job after a committed transition.

## Dependency

Phase 64 begins after Phase 63 closes.

It consumes Phase 63's canonical transition execution and
`CommittedTransition` envelope, and extends Phase 14's existing lightweight
event-triggered, entity-status-based Workflow baseline.

## Selected Direction

- StateMachine owns local entity transition semantics and never becomes a
  workflow engine.
- Workflow owns cross-operation progression and never writes entity status
  directly.
- JobEngine remains the execution substrate for asynchronous work.
- A Workflow advances only from a committed transition or another explicitly
  admitted trigger; an attempted or rolled-back transition cannot advance it.
- CML carries an explicit typed binding from entity/state-machine transition
  context to Workflow definition/entry, not an inferred name match.
- `SalesStatus` and `WorkflowInstance.status` are separate state spaces with
  separate persistence, history, and recovery contracts.
- Existing `entityKind=workflow` classifies a stateful business Entity such as
  `SalesOrder`; it does not mean that Entity is a WorkflowEngine
  `WorkflowInstance`.
- Workflow invokes the next Operation through the generic CNCF invocation and
  authorization boundary; that Operation may request the next entity
  transition.
- Trigger handling is idempotent and replay-safe through stable transition and
  WorkflowInstance identities.
- The built-in Workflow remains the Pareto 80/20 orchestrator. Rich branching,
  timers, parallelism, compensation, human tasks, and connector-heavy flows
  remain external-engine territory.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SWF-01 | Inventory and responsibility freeze | Phase 14 Workflow behavior and Phase 63 transition contracts are mapped, with direct-status and raw-event ambiguities captured by failing-first evidence. | planned |
| SWF-02 | Committed-transition trigger contract | A typed, correlated, idempotent trigger and its commit/replay semantics are fixed. | planned |
| SWF-03 | CML Workflow binding | Entity, StateMachine, transition, Workflow definition, registration, condition, and next Operation normalize explicitly. | planned |
| SWF-04 | Generation and ABI propagation | SimpleModeler generates stable typed Workflow definitions/bindings and compatible metadata. | planned |
| SWF-05 | Workflow runtime alignment | WorkflowEngine consumes committed transitions, selects the next Operation, and delegates execution without direct entity mutation. | planned |
| SWF-06 | WorkflowInstance persistence and recovery | Instance state, history, job links, idempotency, retry, replay, and concurrency are made authoritative and recoverable. | planned |
| SWF-07 | Observability and compatibility | Domain/workflow state, transition/instance identity, legacy triggers, security, redaction, and external-engine boundaries are explicit. | planned |
| SWF-08 | SalesOrder acceptance and promotion | A generated SalesOrder/SalesStatus/SalesOrderWorkflow slice proves commit-to-workflow-to-next-operation behavior and promotes verified contracts. | planned |

## Acceptance

- A committed `SalesOrder`/`SalesStatus` transition can start or advance the
  explicitly bound `SalesOrderWorkflow`.
- A failed, rejected, non-matching, or rolled-back transition cannot advance a
  WorkflowInstance.
- Workflow selects a next Operation and delegates through CNCF/JobEngine; it
  never directly mutates `SalesOrder.status`.
- The invoked Operation follows normal authorization, idempotency,
  UnitOfWork, StateMachine, error, and observability boundaries.
- Domain state and WorkflowInstance state remain distinct in storage,
  projection, history, diagnostics, and recovery.
- `SalesOrder` may retain `entityKind=workflow` while its external
  `SalesOrderWorkflow` has a separate WorkflowInstance identity and state.
- Duplicate delivery and replay do not create duplicate progression or Jobs.
- Transition, WorkflowInstance, Operation, Job, trace/span, and failure
  identities remain correlated without exposing entity/event payloads.
- Legacy raw-event/status-field triggers are either mapped explicitly or
  rejected; they are not silently treated as committed transitions.
- The built-in path remains sequential/lightweight and has an explicit
  handoff boundary to specialist workflow engines.

## Non-Goals

- Moving local domain invariants or transition ownership into Workflow.
- Direct Workflow mutation of entity or Aggregate state.
- Merging `SalesStatus` and `WorkflowInstance.status`.
- A general BPMN, DAG, branch/loop/parallel, timer-rich, human-task,
  compensation, or connector platform.
- Replacing JobEngine, Event, generic Operation invocation, or external
  specialist workflow engines.
- Executable DbC, which follows in Phase 65.
- Active-state Working Set residency/eviction policy for either SalesOrder or
  WorkflowInstance.
- Generic Event/JCL expansion, Job Management expansion, distributed runtime,
  or Saga Management retained by their existing development candidates.

## Development Candidate Alignment

| Strategy item | Phase 64 relationship | Retained candidate scope |
| --- | --- | --- |
| 9.2 Event Mechanism Follow-ups | Consumes committed transitions with bounded delivery/idempotency. | Generic event lanes, reception policy, continuation, and JCL event semantics remain future work. |
| 9.9 ServiceCall Fallback | Workflow may call an Operation that uses explicit fallback policy. | ServiceCall fallback selection and result semantics remain independent. |
| 9.10 Compensation Recovery Events | Allows only explicit compensating Operations/transitions. | Compensation engine and human recovery signals remain future work. |
| 9.11 Workflow Active-State Working Set | Separates a stateful `entityKind=workflow` business Entity from WorkflowEngine WorkflowInstance and defines both lifecycles. | Per-shape memory residency/eviction policy remains future work after Phase 64. |
| 9.13 Distributed Component Runtime | Fixes local identities and replay boundaries only. | Cluster ownership, fencing, delivery, and coherence remain future work. |
| 9.14 Job Management Follow-ups | Reuses existing Job submission/retry/linkage. | JCL flow/events, durable task history, CompositeQuery v2, and general Job UX remain future work. |
| 9.15 Saga Management | Provides a local boundary a Saga may later consume. | Distributed coordination, remote retry/compensation, and Saga persistence remain future work. |
| 9.43 Transport Idempotency | Uses internal transition/Workflow occurrence identity. | REST/Web Form request keys, tokens, stores, and response replay remain separate. |

## Planning References

- [Phase 64 Checklist](phase-64-checklist.md)
- [Provisional Specification](../notes/statemachine-workflow-alignment-provisional-specification.md)
- [Sequencing Record](../journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md)
- [Phase 63](phase-63.md)
- [Phase 14](phase-14.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
- [Execution Platform Boundary](../design/execution-platform-boundary.md)
- [Entity Kind and Working Set Policy](../notes/entity-kind-and-working-set-policy.md)
- [Descriptor Entity Classification Examples](../spec/component-descriptor-entity-classification-examples.md)
