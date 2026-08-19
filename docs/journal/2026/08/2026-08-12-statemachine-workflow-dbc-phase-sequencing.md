# StateMachine, Workflow, and DbC Phase Sequencing

date = 2026-08-12
status = design consideration record
phases = 63, 64, 65

## Starting Point

The discussion began with a plan to make CML Design by Contract executable:
CML-defined obligations should run when their owning Operation executes, and a
failure should remain in CNCF observability. The initial planning choice was to
assign this work to Phase 63.

The requirement then expanded to connect DbC with StateMachine transitions.
Before fixing that runtime order, the existing StateMachine and Workflow
support was investigated.

## Investigation Finding

The repository already has meaningful foundations rather than an empty design:

- canonical core StateMachine primitives and deterministic transition order;
- CML/AST/model/generator transition surfaces;
- CNCF transition planning/provider hooks, pre-persistence validation,
  UnitOfWork, lifecycle observation, and CallTree integration;
- a closed Phase 14 lightweight WorkflowEngine with definition/registration,
  independent WorkflowInstance/history, next-action decision, Job delegation,
  retry/dead-letter, inspection, and JCL entrypoints; and
- a documented boundary that keeps Workflow outside StateMachine ownership.

The StateMachine path is nevertheless not a sufficiently stable prerequisite
for executable DbC or Workflow binding. The gaps identified during the review
include generated no-op actions, incomplete named-guard resolution, priority
loss/defaulting, raw MVEL expression guards, possible duplication between core
and CNCF selection, inconsistent enforcement across entity execution paths,
and uncertainty about transition diagnostics surviving rollback.

The Workflow baseline also matches raw event plus entity status rather than a
strong typed post-commit transition contract. It does not yet express the
intended CML binding between an entity StateMachine and a Workflow definition.

## Reference Domain Model

The design was clarified using this example:

```text
SalesOrder entity
  has SalesStatus
  SalesStatus is governed by the SalesOrder StateMachine

SalesOrderWorkflow
  observes committed SalesStatus transitions
  orchestrates the next SalesOrder Operation or Job
```

The StateMachine is sufficient for local entity-lifecycle rules. An outer
`SalesOrderWorkflow` remains useful for a full process that spans Operations,
Jobs, retries, recovery, other components, or operator-visible history.

## Responsibility Decision

The agreed boundary is:

```text
StateMachine
  owns local transition validity and candidate state
  -> commits CommittedTransition

Workflow
  consumes committed transition
  owns cross-operation progression and WorkflowInstance
  -> selects generic Operation / submits Job

Operation
  owns its executable DbC checkpoints
  -> may request the next StateMachine transition
```

Two state spaces remain deliberately separate:

- `SalesStatus` is domain state persisted with `SalesOrder`; and
- `WorkflowInstance.status` is orchestration state persisted with Workflow
  history and Job links.

The existing descriptor term `entityKind=workflow` is a third, orthogonal
classification axis. It can continue to describe `SalesOrder` as a stateful
business Entity; it does not mean `SalesOrder` is the WorkflowInstance. The
representation and residency policy of WorkflowInstance require their own
explicit decision.

Workflow must not directly write SalesStatus. It invokes a normal Operation,
which crosses authorization, idempotency, UnitOfWork, StateMachine, DbC,
failure, and observability boundaries.

## Effect and Commit Decision

StateMachine actions are restricted to deterministic local changes explicitly
admitted to the same UnitOfWork. External HTTP, service, process, message, or
other ambient effects do not run before the transition commits.

Only a successful commit produces the canonical `CommittedTransition`
envelope. Workflow and external effect handlers consume that envelope.
Attempted, rejected, failed, canceled, or rolled-back transitions cannot
advance Workflow or publish a success transition.

Failure diagnostics must remain correlated and retrievable after rollback even
though domain state and staged success events are removed.

## Phase Sequencing Decision

The initial Phase 63 DbC plan is superseded by this prerequisite order:

1. **Phase 63 — CML StateMachine Runtime Completion**
   establishes the canonical typed guard/action, candidate-state UnitOfWork,
   committed-transition, compatibility, and failure-observability contract.
2. **Phase 64 — StateMachine-Workflow Alignment**
   connects committed transitions to independent WorkflowInstances and next
   generic Operations/Jobs, using `SalesOrder`/`SalesStatus`/
   `SalesOrderWorkflow` as the reference slice.
3. **Phase 65 — CML Executable Design by Contract**
   reuses Phase 63's closed predicate foundation and enforces Operation/
   Aggregate contracts around the established StateMachine and Workflow
   boundaries.

This means the DbC work formerly drafted as Phase 63 moves to Phase 65. Its
semantic goal remains unchanged; only its prerequisites and ownership boundary
are clarified.

## Why This Order

Implementing DbC first would force it to make decisions about transition
selection, candidate state, action order, Workflow invocation, and rollback
observability that belong to other layers. That would risk a parallel
StateMachine executor or contract-specific Workflow behavior.

Completing StateMachine first gives both later phases a stable
`PredicateProgram`, transition result vocabulary, UnitOfWork checkpoint, and
post-commit envelope. Aligning Workflow second then fixes how automatically
selected Operations enter the runtime. DbC can finally enforce every owning
Operation consistently, regardless of whether the caller was HTTP, shell,
another component, a Job, or Workflow.

## Alternatives Rejected

### Put StateMachine, Workflow, and DbC in one Phase

Rejected because the work crosses three separately testable ownership
boundaries and would make completion evidence difficult to interpret.

### Let Workflow directly update entity status

Rejected because it bypasses StateMachine guards/actions, Aggregate
invariants, authorization, UnitOfWork, and domain observability.

### Use StateMachine as the complete Workflow engine

Rejected because local domain lifecycle and cross-operation orchestration have
different state, persistence, retry, recovery, and operational visibility.

### Let DbC own the shared expression and transition engine

Rejected because StateMachine guards need the pure predicate foundation before
DbC and transition semantics must remain canonical outside contract checking.

### Execute external effects inside transition actions

Rejected because they cannot generally roll back with entity persistence and
would make retry/exactly-once claims unsound. External work follows commit.

## Product Boundary

The built-in Workflow remains the Pareto 80/20 solution: committed-event
triggering, sequential next-Operation decisions, WorkflowInstance/history,
Job linkage, retry/dead-letter, and inspection.

Rich BPMN/DAG behavior, branch/loop/parallel execution, timer-heavy flows,
human tasks, compensation protocols, connector catalogs, and
cross-organization processes remain a specialist-engine integration concern.

## Development Candidate Reconciliation

The new phases were checked against the existing strategy candidate ledger.
The decision is to consume existing boundaries without treating a reference as
candidate completion.

| Candidate | Phase relationship | Candidate remains open for |
| --- | --- | --- |
| 9.2 Event Mechanism | Phase 63 produces and Phase 64 consumes committed transitions. | Generic outcome lanes, reception policy, continuation, and JCL events. |
| 9.4 Metrics/Observability | Phases 63-65 require bounded correlated evidence. | Platform retention, authorization, exporters, dashboards, durable metrics, and operations. |
| 9.7 Error Model | Phases 63/65 add StateMachine and DbC semantics. | General taxonomy cleanup/compatibility, catalogs, application/CLI codes, and trace UX. |
| 9.9 ServiceCall Fallback | Phase 64 may invoke an Operation using explicit fallback. | Fallback selection, execution, and result policy. |
| 9.10 Compensation Recovery | Phases 63/64 keep compensation explicit and post-commit. | Compensation-of-compensation and human recovery events. |
| 9.11 Workflow Working Set | Phase 63 establishes business-Entity transitions and Phase 64 separately establishes WorkflowInstance lifecycle. | Per-shape active residency/eviction without conflating `entityKind=workflow` and WorkflowInstance. |
| 9.13 Distributed Runtime | Phase 64 fixes local identity/replay boundaries. | Cluster ownership, fencing, delivery, and coherence. |
| 9.14 Job Follow-ups | Phase 64 reuses current JobEngine and Job linkage. | JCL flow/events, rollout, durable task history, CompositeQuery v2, and Job UX. |
| 9.15 Saga Management | Phase 64 provides a local boundary for later reuse. | Distributed coordination, remote retry/compensation, and Saga persistence. |
| 9.43 Transport Idempotency | Phases 63/64 use internal occurrence identity; Phase 65 runs on actual execution. | REST/Web Form keys, tokens, stores, fingerprints, and response replay. |
| 9.53 ComponentFactory Purity | Phase 63 may consume minimal guard/action binding evidence. | General Factory purity and capability-implementation evidence. |
| Aggregate method `IMPLEMENTATION` candidates | Phase 63 selects the StateMachine built-in pattern slice. | Other implementation kinds/patterns, inline/external Scala, and broad factory/Operation reuse. |

The phase close checklists must revisit these rows. A candidate moves to
completed history only if a Phase explicitly owns and verifies its full scope;
otherwise its retained scope stays in section 9.

Candidate Triage: COMPLETED
Canonical ID: DEV-008
Disposition: STRATEGY_ITEM
Strategy Record: docs/strategy/cncf-development-strategy.md#9-development-item-status
Target Phase: -
Triaged On: 2026-08-19

## Open Decisions Carried Forward

- Exact CML typed predicate, local action, and Workflow binding syntax.
- Exact core/CNCF selector adapter and removal/migration of duplicate logic.
- Legacy raw MVEL, no-op generated actions, and unversioned/direct path policy.
- `CommittedTransition` outbox/persistence and retention mechanism.
- Durable observability path for rolled-back transition/contract failures.
- Workflow business key, subject/service authority, duplicate/replay, and
  crash-window semantics.
- Exact boundary between built-in sequential Workflow and external engines.
- Exact DbC executable syntax, `VALIDATE` classification, subject contexts, and
  clause-program versioning.

## Documents Updated

- `docs/strategy/cncf-development-strategy.md` items 9.54-9.56
- `docs/phase/phase-63.md` and `phase-63-checklist.md`
- `docs/phase/phase-64.md` and `phase-64-checklist.md`
- `docs/phase/phase-65.md` and `phase-65-checklist.md`
- `docs/notes/cml-statemachine-runtime-completion-provisional-specification.md`
- `docs/notes/statemachine-workflow-alignment-provisional-specification.md`
- `docs/notes/cml-executable-design-by-contract-provisional-specification.md`
- `docs/notes/entity-kind-and-working-set-policy.md`
- `docs/notes/aggregate-method-implementation-strategy.md`
- `docs/journal/2026/08/2026-08-12-cml-executable-design-by-contract-consideration.md`

This journal preserves design history only. Phase/checklist documents own work
state, notes remain non-normative, and only verified promoted design/spec plus
Executable Specifications can define accepted runtime behavior. The artifact
lifecycle follows `docs/rules/document-lifecycle.md`.
