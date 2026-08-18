# Phase 63 - CML StateMachine Runtime Completion

status=planned
planned_at=2026-08-12
depends_on=[Phase 62](phase-62.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 63 Checklist](phase-63-checklist.md)

## Purpose

Complete the executable path from a CML StateMachine declaration to one
canonical, deterministic, atomic CNCF transition runtime.

Phase 63 closes the remaining gaps between parsed/generated transition rules
and actual guard, action, state update, event, rollback, and observability
behavior. It establishes the transition contract that Workflow and executable
DbC can safely consume in later phases.

## Dependency

Phase 63 begins after Phase 62 closes.

Its foundations are Phase 4 StateMachine integration, the canonical
`org.goldenport.statemachine` primitives, generated CML transition rules,
CNCF transition planning/hooks, Aggregate persistence, UnitOfWork, and
CallTree observability.

## Selected Direction

- The core StateMachine remains the canonical owner of pure transition
  selection and `(priority, declarationOrder)` determinism.
- CNCF owns binding resolution, candidate-state construction, UnitOfWork
  planning, local effect execution, persistence, publication, and diagnostics.
- CML expression guards normalize to a closed, typed, versioned, pure
  `PredicateProgram`; named guards remain explicit resolver bindings.
- The canonical machine definition preserves explicit initial/final-state
  semantics, the existing one-level composite-state/named shallow-history
  contract, and the typed trigger context seen by predicates.
- A transition produces a candidate state and a local effect plan before any
  state is committed.
- State mutation, admitted local effects, persistence, and the durable
  transition outcome share one UnitOfWork boundary.
- External I/O is not a transition action. It starts only from a committed
  transition/domain event through a separately governed Operation or Job.
- A successful commit exposes one stable `CommittedTransition` envelope.
- Guard non-match, guard/evaluator failure, invalid source state, ambiguous
  transition, action failure, persistence failure, and rollback remain
  distinct structured outcomes.
- Failed transitions publish no successful transition event, but their
  bounded diagnostic outcome remains retrievable after rollback.
- Create, update/save, patch, command, and compatibility execution paths must
  not silently bypass declared StateMachine enforcement.
- End-to-end acceptance must cross CML parsing, SimpleModeler generation,
  generated provider publication, ComponentFactory automatic bootstrap, and
  the real UnitOfWork boundary. A manually injected transition provider is not
  sufficient evidence for this path.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| SMR-01 | Inventory and semantic freeze | Existing CML, generated, core, CNCF, Aggregate, UnitOfWork, and observability behavior plus bypasses are fixed by failing-first evidence. | planned |
| SMR-02 | Canonical transition and predicate contract | Core selection, typed guard IR, named binding, effect boundary, result vocabulary, and ordering are fixed without duplicate engines. | planned |
| SMR-03 | CML normalization | Machine identity/version, initial/final state, existing composite/history structure, typed trigger context, transition identity, source/target, event, priority, guard, and action declarations normalize deterministically. | planned |
| SMR-04 | Generation and ABI propagation | SimpleModeler emits stable typed machine/transition definitions, predicates, bindings, hierarchy/history, and metadata without required raw-string execution. | planned |
| SMR-05 | Atomic CNCF execution | CNCF plans and commits candidate state, admitted local effects, persistence, and outcomes exactly once through UnitOfWork. | planned |
| SMR-06 | Trigger and committed-transition contract | Operation/event triggers are explicit and a successful commit produces one stable downstream envelope. | planned |
| SMR-07 | Observability and compatibility | Structured failures, redaction, legacy admission, projections, rollback diagnostics, and bypass prevention are fixed. | planned |
| SMR-08 | Cross-repository acceptance and promotion | Generated SalesOrder-style scenarios, property specifications, full validation, and promoted contracts prove the runtime. | planned |

## Acceptance

- One CML declaration yields one canonical transition definition from Cozy and
  SimpleModeler to CNCF.
- Priority and declaration order are honored; guard false continues selection
  and guard failure stops it.
- Explicit initial/final-state semantics and the existing one-level
  composite-state/named shallow-history contract survive parsing, generation,
  ABI/projection, and execution without flattening away required meaning.
- Named guards resolve explicitly; required expression guards do not silently
  fall back to raw MVEL.
- Predicate field access is checked against one bounded typed trigger context;
  missing or incompatible trigger data is a structured failure.
- Exactly one admitted transition is planned from current state and trigger.
- Transition actions are executable only inside the admitted local UnitOfWork
  boundary; external effects begin after commit.
- Candidate state and admitted effects commit atomically or roll back together.
- Create, update/save, patch, command, and compatibility paths enforce the
  declared machine or reject an unsupported bypass explicitly.
- A successful commit produces one idempotent, correlated
  `CommittedTransition` envelope.
- A failed or rolled-back transition produces no success envelope and no
  persisted state change.
- Failure observability retains machine, transition, entity, source/target,
  event, guard/action phase, trace, and safe cause identity after rollback.
- Representative generated `SalesOrder`/`SalesStatus` behavior crosses the
  real CNCF execution boundary and is fixed by Executable Specifications.
- The representative acceptance starts from CML source and uses the generated
  provider plus ComponentFactory automatic bootstrap; it does not replace that
  path with a hand-written provider fixture.

## Non-Goals

- Workflow orchestration or WorkflowInstance progression.
- Executable preconditions, postconditions, or Aggregate invariants owned by
  Phase 65.
- Timer, schedule, parallel, human-task, compensation, or connector semantics.
- Deep history, orthogonal regions, arbitrary-depth composite states,
  choice/junction/fork/join expansion, or a general UML statechart language.
- External network, process, database, or service I/O inside a transition
  action.
- Replacing core StateMachine primitives or introducing a second transition
  selector in CNCF.
- A general-purpose expression, scripting, rules, or workflow language.

## Development Candidate Alignment

| Strategy item | Phase 63 relationship | Retained candidate scope |
| --- | --- | --- |
| 9.2 Event Mechanism Follow-ups | Defines the StateMachine-specific committed envelope only. | Generic transaction lanes, reception policies, and JCL events remain future work. |
| 9.4 Metrics and Observability | Supplies minimum correlated transition/rollback evidence. | Platform retention, exporters, dashboards, durable metrics, and operations remain future work. |
| 9.7 Error Model Follow-ups | Adds only required closed StateMachine outcomes/facets. | Broad taxonomy cleanup, compatibility policy, catalogs, CLI mapping, and trace UX remain future work. |
| 9.10 Compensation Recovery Events | Keeps external effects after commit and compensation explicit. | Compensation-of-compensation and human recovery events remain future work. |
| 9.53 ComponentFactory Purity | May consume minimal named guard/action implementation evidence. | General Factory purity and capability-evidence policy remain unassigned. |
| Aggregate method `IMPLEMENTATION` candidates | Selects the `state-machine`/`state-transition` built-in slice. | General implementation kinds, inline/external Scala, other patterns, and broad factory reuse remain provisional. |

## Planning References

- [Phase 63 Checklist](phase-63-checklist.md)
- [Provisional Specification](../notes/cml-statemachine-runtime-completion-provisional-specification.md)
- [Sequencing Record](../journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md)
- [State Machine Boundary Contract](../design/statemachine-boundary-contract.md)
- [Execution Platform Boundary](../design/execution-platform-boundary.md)
- [Phase 4](phase-4.md)
- [Aggregate Method Implementation Strategy](../notes/aggregate-method-implementation-strategy.md)
