# StateMachine-Workflow Alignment Provisional Specification

status = proposed, non-normative
date = 2026-08-12
target_phase = 64

## Status and Authority

This note records the provisional Phase 64 contract. It does not override the
closed Phase 14 Workflow baseline, Phase 63's future verified StateMachine
contract, source code, generated ABI, or Executable Specifications. Accepted
behavior must move to `docs/design` and `docs/spec` after verification.

## Reference Scenario

The design is anchored in this model:

```text
SalesOrder entity
  owns SalesStatus state
  governed by SalesOrder StateMachine

SalesOrderWorkflow
  observes committed SalesStatus transitions
  chooses the next SalesOrder Operation
  delegates execution through CNCF/JobEngine
```

For example, a committed transition from `Draft` to `Submitted` may advance a
WorkflowInstance and select `authorizePayment`. The Workflow never sets
`SalesOrder.status = Paid`. The `capturePayment` or equivalent next Operation
requests the transition, and Phase 63 enforces it.

## Two-Layer Responsibility

| Concern | StateMachine | Workflow |
| --- | --- | --- |
| Scope | One entity/Aggregate's local lifecycle | Cross-Operation/process progression |
| State | `SalesStatus` or equivalent domain state | Independent `WorkflowInstance.status` |
| Decision | Is this local transition admitted? | What Operation should run next? |
| Mutation | Candidate domain state inside UnitOfWork | WorkflowInstance/history only |
| Trigger output/input | Produces `CommittedTransition` | Consumes committed triggers |
| Execution | Local admitted effects | Generic Operation invocation / JobEngine |
| Failure | Transition/guard/action/persistence outcome | Step/submission/retry/terminal outcome |

Neither layer is a substitute for the other. A StateMachine alone can realize
a small local lifecycle. The external Workflow becomes valuable when progress
crosses Operations, components, Jobs, retry/recovery, or process-level history.

## Entity Kind Terminology

Existing CNCF descriptor/specification vocabulary classifies `SalesOrder` as
`entityKind=workflow` because it is a stateful business Entity with explicit
transitions. That classification does not make `SalesOrder` an instance of
WorkflowEngine and does not merge its state with `WorkflowInstance`.

In this specification:

- `SalesOrder` remains the domain Entity and may retain
  `entityKind=workflow`;
- `SalesStatus` remains its StateMachine-governed domain state;
- `SalesOrderWorkflow` is a Workflow definition; and
- `WorkflowInstance` is a separate orchestration record with its own status.

SWF-01/SWF-06 must freeze how WorkflowInstance itself is persisted and
classified. Its Working Set policy cannot be inferred from the word
`workflow`, from SalesOrder's classification, or from SalesStatus.

## Trigger Contract

The preferred trigger is Phase 63's typed `CommittedTransition` envelope. It
must be available only after commit and must carry stable transition occurrence
identity for deduplication and replay.

Phase 14 raw event plus status-field matching becomes an explicit legacy
compatibility surface. Phase 64 must either map such a trigger through a named
adapter with stated weaker guarantees or reject it for definitions requiring
committed-transition semantics. It must not silently infer commit success from
the current status field.

JCL synthetic start remains a separate explicit Workflow entry trigger; it is
not presented as a domain transition.

## Provisional Workflow Model

```text
WorkflowDefinition(
  id,
  version,
  registrations,
  steps
)

CommittedTransitionRegistration(
  id,
  entityType,
  machineId,
  transitionId | source/target selector,
  predicate?,
  entryStep
)

WorkflowStep(
  id,
  condition?,
  nextOperation | terminalOutcome
)
```

Conditions reuse Phase 63's closed `PredicateProgram`. They operate on bounded
trigger/workflow context and cannot read providers or arbitrary entity state.

Candidate CML shape, subject to SWF-01/SWF-03:

```text
## WORKFLOW SalesOrderWorkflow

### ON COMMITTED TRANSITION submitOrder
ENTITY SalesOrder
MACHINE OrderStatus
TRANSITION DraftToSubmitted
NEXT OPERATION authorizePayment
```

Bindings are explicit. Matching by coincidental Workflow, state, event, or
Operation names is not permitted.

## WorkflowInstance Contract

WorkflowInstance is not the SalesOrder and is not a Job.

```text
WorkflowInstance(
  id,
  definitionId,
  definitionVersion,
  businessKey,
  status,
  currentStep,
  consumedTriggerIds,
  selectedOperationIds,
  jobIds,
  history,
  correlationId,
  subjectScope,
  version
)
```

The instance has its own concurrency, persistence, retention, retry, replay,
and terminal-state behavior. Domain status and WorkflowInstance status must be
projected separately so an operator can distinguish, for example,
`SalesStatus=Submitted` from `WorkflowStatus=WaitingForPaymentJob`.

## Provisional Runtime Order

1. Phase 63 commits a SalesOrder transition.
2. The commit exposes one `CommittedTransition` occurrence.
3. Event/Workflow delivery supplies it at least once.
4. WorkflowEngine admits version, tenant/subject scope, and registration.
5. It deduplicates by transition occurrence plus registration/instance key.
6. It loads or creates the independent WorkflowInstance.
7. It evaluates the bounded trigger/step condition.
8. It records one deterministic next-Operation or terminal decision.
9. It invokes/submits the Operation through generic CNCF/JobEngine paths.
10. It records Operation/Job linkage and Workflow history.
11. Any resulting entity transition returns through Phase 63 and may produce
    the next committed trigger.

An implementation must freeze crash windows between steps 8-10 so retry cannot
duplicate an Operation or Job.

## Invocation and Security

Workflow is an orchestrator, not a privileged direct method call.

- The next Operation follows normal component/service/operation resolution.
- Authorization and capability checks remain mandatory.
- Subject/tenant propagation and service authority must be explicit.
- Idempotency and correlation survive synchronous and Job execution.
- Workflow cannot obtain a provider handle that lets it mutate domain state.
- Any state change occurs through the invoked Operation and Phase 63
  StateMachine/UnitOfWork boundary.

## Failure and Recovery

Keep these outcomes distinct:

- trigger incompatibility/admission failure;
- duplicate already consumed;
- no registration or condition non-match;
- ambiguous next-step decision;
- WorkflowInstance concurrency/persistence failure;
- Operation resolution/authorization failure;
- Operation/Job submission failure;
- downstream Operation failure;
- retry exhaustion and poison/dead-letter; and
- operator recovery/replay outcome.

A failed Workflow step does not undo the already committed SalesOrder
transition. Compensation, if needed, is a separate explicitly modeled
Operation/transition or an external specialist workflow concern.

## Idempotency and Replay

- A committed transition occurrence may be delivered more than once.
- The pair of trigger occurrence and Workflow registration/instance scope must
  identify one progression decision.
- Retrying after partial failure must reuse the same Operation/Job idempotency
  identity.
- Replay must be explicit, observable, version-aware, and unable to re-run an
  already completed step unintentionally.
- Concurrent triggers for one instance require optimistic concurrency or an
  equivalent serialization rule.

## Observability and Projection

One trace/correlation path should connect:

```text
CommittedTransition
  -> Workflow registration and instance
  -> step/decision
  -> Operation invocation
  -> Job (when asynchronous)
  -> next committed transition or terminal failure
```

Safe identity includes machine, transition occurrence, entity type/id,
Workflow definition/instance/step, Operation, Job, event, trace/span,
subject/tenant scope, retry, and failure category. Entity/event/Operation
payloads, credentials, secrets, and predicate values remain excluded.

Admin/Help/Record/JSON surfaces must show domain and WorkflowInstance state as
separate concepts and preserve links between them.

## Built-In and External Workflow Boundary

The built-in Workflow remains deliberately small:

- event/committed-transition triggered;
- entity-aware;
- sequential next-Operation decisions;
- WorkflowInstance/history/Job linkage;
- retry/dead-letter/recovery; and
- inspection/observability.

Branch/loop/parallel graphs, rich timers, human tasks, long compensation
protocols, connector catalogs, visual BPMN, and cross-organization processes
belong to a specialist engine. Integration should use committed events and
normal CNCF Operation ingress rather than shared database writes.

## Development Candidate Alignment

Phase 64 narrows its use of related future candidates as follows:

- Strategy 9.2 retains generic event lanes/reception/JCL event behavior; Phase
  64 consumes the typed committed-transition path only.
- Strategy 9.9 retains explicit ServiceCall fallback policy. Workflow retry or
  next-step choice is not an implicit fallback.
- Strategy 9.10 retains compensation recovery events and human recovery.
  Phase 64 supports only explicit compensating Operations/transitions.
- Strategy 9.11 applies to stateful `entityKind=workflow` business Entities and
  may later apply separately to a persisted WorkflowInstance representation.
  Phase 64 defines both lifecycles, but active memory residency and eviction
  remain outside this phase.
- Strategy 9.13 and 9.15 retain clustered ownership, fencing, remote delivery,
  distributed retry/compensation, and Saga persistence. Phase 64 is local.
- Strategy 9.14 retains JCL flow/events, JobDefinition rollout, durable task
  history, CompositeQuery v2, and general Job UX. Phase 64 reuses only the
  existing Job execution/linkage contract.
- Strategy 9.43 retains REST/Web Form request idempotency. Workflow uses
  transition/step occurrence identity, not a transport idempotency key.

## Compatibility

- Preserve Phase 14 definitions through explicit versioned adapters where
  their semantics are known.
- Do not claim raw event/status-field matching has commit-coupled guarantees.
- Preserve JCL's submission-only role and explicit synthetic Workflow start.
- Preserve WorkflowInstance and Job as separate authorities.
- Unknown required binding versions fail admission; they do not run silently.

## Executable Specification Matrix

Phase 64 evidence should cover:

- commit-before-trigger and no trigger on rollback;
- exact CML/generated binding and unknown-reference rejection;
- deterministic registration/step decision;
- duplicate delivery, replay, concurrency, and crash-window recovery;
- independent domain and WorkflowInstance state/history;
- generic Operation authorization and idempotent Job submission;
- no direct entity mutation by Workflow;
- downstream Operation returning through Phase 63;
- structured failure/retry/dead-letter and non-leakage projections;
- explicit Phase 14/JCL compatibility; and
- a generated SalesOrder/SalesStatus/SalesOrderWorkflow end-to-end slice.

## Open Decisions

1. Exact CML Workflow declaration and binding syntax.
2. Business-key and WorkflowInstance identity derivation.
3. Exact transactional/outbox delivery between transition commit and Workflow.
4. Workflow decision/submission/history crash-window mechanism.
5. Subject/service-authority propagation for automatic next Operations.
6. Retention and replay policy for transition triggers and Workflow history.
7. Compatibility lifetime for raw status-field triggers.
8. External-engine adapter envelope and acknowledgment semantics.

## Related Documents

- `docs/phase/phase-64.md`
- `docs/phase/phase-64-checklist.md`
- `docs/phase/phase-63.md`
- `docs/phase/phase-14.md`
- `docs/notes/cml-statemachine-runtime-completion-provisional-specification.md`
- `docs/notes/entity-kind-and-working-set-policy.md`
- `docs/spec/component-descriptor-entity-classification-examples.md`
- `docs/design/statemachine-boundary-contract.md`
- `docs/design/execution-platform-boundary.md`
- `docs/journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`
