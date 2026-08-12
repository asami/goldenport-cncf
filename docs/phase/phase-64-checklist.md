# Phase 64 Checklist - StateMachine-Workflow Alignment

status=planned
phase=[Phase 64 - StateMachine-Workflow Alignment](phase-64.md)

This checklist is the authoritative Phase 64 state ledger after Phase 64
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 63 closes.

## SWF-01: Inventory and Responsibility Freeze

Stage Status:
- Current status: PLANNED
- Owner: CML, SimpleModeler, CNCF StateMachine, Workflow, Event, Job,
  persistence, and observability maintainers
- Entry rule: Phase 63 is closed.
- Completion rule: Current Workflow and committed-transition behavior,
  ownership, ambiguities, and failing-first identities are frozen.

- [ ] Inventory Phase 14 WorkflowDefinition, registration, trigger,
  WorkflowInstance, history, next-action, Job linkage, retry, and projection.
- [ ] Inventory raw event plus entity-status matching, status-field rules,
  synthetic JCL start, and any direct state lookup or mutation.
- [ ] Inventory Phase 63 `CommittedTransition` production, identity,
  idempotency, replay, rollback, and observability.
- [ ] Freeze StateMachine as local domain-transition owner, Workflow as
  cross-operation progression owner, and JobEngine as execution owner.
- [ ] Freeze domain status and WorkflowInstance status as separate state
  spaces and persistence authorities.
- [ ] Freeze `entityKind=workflow` as a business-Entity classification distinct
  from WorkflowEngine WorkflowInstance, including the representation chosen
  for WorkflowInstance persistence.
- [ ] Freeze the built-in Pareto 80/20 boundary and external-engine handoff.
- [ ] Inventory authorization, subject propagation, correlation, retry,
  duplicate delivery, concurrent transition, and poison/dead-letter behavior.
- [ ] Reconcile strategy candidates 9.2, 9.9, 9.10, 9.11, 9.13, 9.14, 9.15,
  and 9.43 so local Workflow work does not absorb retained platform,
  distributed, Job, compensation, or transport scope.
- [ ] Register failing-first Executable Specifications for every Phase 64
  acceptance group and cross-repository surface.

Evidence:
- Pending.

## SWF-02: Committed-Transition Trigger Contract

Stage Status:
- Current status: PLANNED
- Owner: StateMachine, Event, Workflow, UnitOfWork, and persistence maintainers
- Entry rule: SWF-01 is DONE.
- Completion rule: Workflow can consume one stable typed trigger only after
  domain commit, with deterministic duplicate/replay behavior.

- [ ] Adopt the Phase 63 `CommittedTransition` envelope without redefining its
  semantic identity.
- [ ] Define Workflow trigger identity from transition occurrence, workflow
  definition/registration, entity, and tenant/security scope.
- [ ] Define commit-before-delivery and no-delivery-on-rollback behavior.
- [ ] Define at-least-once delivery, deduplication, replay, and retention
  semantics.
- [ ] Define missing/out-of-order/duplicate transition handling.
- [ ] Define structured trigger admission, incompatibility, and failure
  outcomes.
- [ ] Add commit-order, duplicate, replay, race, and rollback property specs.

Evidence:
- Pending.

## SWF-03: CML Workflow Semantics and Binding

Stage Status:
- Current status: PLANNED
- Owner: Cozy and CML semantic-model maintainers
- Entry rule: SWF-02 is DONE.
- Completion rule: CML explicitly binds committed transition context to a
  Workflow definition and next Operation without name/status inference.

- [ ] Define explicit entity/StateMachine/transition-to-Workflow registration
  syntax and stable ids.
- [ ] Define trigger predicates using the Phase 63 closed `PredicateProgram`
  where conditions are needed.
- [ ] Define the permitted sequential next-Operation and terminal outcomes.
- [ ] Define WorkflowInstance key, correlation, subject/tenant, and version.
- [ ] Reject unknown machine/state/transition/Workflow/Operation references.
- [ ] Reject direct status mutation, embedded scripts, and unsupported rich
  orchestration constructs.
- [ ] Preserve Phase 14 JCL synthetic-start semantics through an explicit
  separate entry trigger.
- [ ] Add Given/When/Then and ScalaCheck parsing, normalization, diagnostic,
  and compatibility specifications.

Evidence:
- Pending.

## SWF-04: SimpleModeler Generation and ABI

Stage Status:
- Current status: PLANNED
- Owner: SimpleModeler and generated-component maintainers
- Entry rule: SWF-03 is DONE.
- Completion rule: Generated code and metadata carry typed Workflow bindings,
  trigger predicates, next Operations, and compatibility identity.

- [ ] Generate Workflow definitions, registrations, trigger bindings, and
  sequential next-Operation decisions.
- [ ] Preserve entity, machine, transition, Workflow, registration,
  WorkflowInstance-key, Operation, and source-location identity.
- [ ] Generate typed predicates rather than raw runtime strings.
- [ ] Project stable Help/meta/Record/JSON descriptions without payload values.
- [ ] Define ABI/version admission for Phase 14 and Phase 64 definitions.
- [ ] Reject generation when a required binding cannot be represented.
- [ ] Add deterministic source, metadata, ABI, compilation, Record, and JSON
  Executable Specifications.

Evidence:
- Pending.

## SWF-05: Workflow Runtime Alignment

Stage Status:
- Current status: PLANNED
- Owner: CNCF WorkflowEngine, Operation invocation, Event, and Job maintainers
- Entry rule: SWF-04 is DONE.
- Completion rule: Workflow advances from committed transitions and delegates
  the next Operation without owning domain state mutation.

- [ ] Admit and match a typed committed-transition trigger.
- [ ] Load or create the independent WorkflowInstance.
- [ ] Evaluate registration/step conditions deterministically.
- [ ] Select at most one permitted next Operation or terminal outcome.
- [ ] Delegate the Operation through generic CNCF invocation and normal
  authorization/idempotency/correlation boundaries.
- [ ] Delegate asynchronous work to JobEngine and record resulting Job ids.
- [ ] Prohibit direct Workflow writes to entity status or Aggregate state.
- [ ] Ensure a next Operation's transition returns through the normal Phase 63
  path rather than a Workflow shortcut.
- [ ] Add exactly-once-decision, no-direct-mutation, authorization, failure,
  cancellation, and retry specifications.

Evidence:
- Pending.

## SWF-06: WorkflowInstance Persistence and Recovery

Stage Status:
- Current status: PLANNED
- Owner: Workflow, persistence, Job, retry/dead-letter, and operations
  maintainers
- Entry rule: SWF-05 is DONE.
- Completion rule: Workflow progression and Job linkage are authoritative,
  idempotent, inspectable, and recoverable independently of domain state.

- [ ] Define WorkflowInstance states, step history, consumed trigger ids,
  selected Operations, resulting Job ids, and terminal outcomes.
- [ ] Define WorkflowInstance persistence classification without inheriting
  `SalesOrder` entity kind or Working Set residency by name.
- [ ] Define optimistic concurrency or equivalent progression serialization.
- [ ] Define idempotent retry after failure between decision, submission, and
  history persistence.
- [ ] Define replay/rebuild behavior from retained transition triggers and
  Workflow history.
- [ ] Preserve Phase 14 retry/dead-letter authority and operator recovery.
- [ ] Define retention, version migration, and orphan Job/instance handling.
- [ ] Add crash-window, duplicate, race, retry exhaustion, replay, and
  recovery Executable Specifications.

Evidence:
- Pending.

## SWF-07: Observability, Security, and Compatibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF observability, security, Workflow, HTTP/admin, and compatibility
  maintainers
- Entry rule: SWF-06 is DONE.
- Completion rule: Domain and Workflow progression are separately visible,
  securely correlated, and compatible without silent semantic inference.

- [ ] Correlate transition, Workflow definition/registration/instance, step,
  Operation, Job, event, trace/span, subject, and tenant identities.
- [ ] Project domain status and WorkflowInstance status as distinct fields and
  histories.
- [ ] Preserve subject/capability/authorization context for delegated
  Operations without privilege amplification.
- [ ] Define bounded failure, retry, poison/dead-letter, and recovery
  observability.
- [ ] Keep entity/event/Operation payload, credentials, secrets, and predicate
  values out of public diagnostics.
- [ ] Define explicit mapping or rejection for Phase 14 raw-event/status-field
  triggers and JCL synthetic starts.
- [ ] Define external specialist-engine handoff using committed events and
  normal Operation ingress, not shared mutable state.
- [ ] Add Record/JSON, admin/help, audit, non-leakage, compatibility, and
  external-boundary Executable Specifications.

Evidence:
- Pending.

## SWF-08: SalesOrder Acceptance and Promotion

Stage Status:
- Current status: PLANNED
- Owner: Cozy, SimpleModeler, CNCF, sample, and documentation maintainers
- Entry rule: SWF-07 is DONE.
- Completion rule: A real generated scenario proves the complete boundary and
  verified contracts are promoted from notes.

- [ ] Define `SalesOrder`, `SalesStatus`, and `SalesOrderWorkflow` in CML.
- [ ] Prove a committed SalesStatus transition starts/advances the explicitly
  bound WorkflowInstance.
- [ ] Prove Workflow selects and delegates the next Operation without direct
  status mutation.
- [ ] Prove the next Operation returns to Phase 63 StateMachine enforcement.
- [ ] Prove rollback/no-match/failure does not advance Workflow.
- [ ] Prove duplicate delivery, retry, replay, concurrency, Job linkage,
  authorization, observability, and non-leakage behavior.
- [ ] Run affected focused suites and full suites serially under project rules.
- [ ] Validate downstream generated sample/CAR behavior where admitted.
- [ ] Reconcile final implementation evidence with every linked development
  candidate and update candidate status without implicit absorption.
- [ ] Promote verified architecture to `docs/design` and behavior to
  `docs/spec`; keep specialist features explicitly deferred.
- [ ] Record exact repository, test-count, artifact, and revision evidence.

Evidence:
- Pending.
