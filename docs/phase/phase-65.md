# Phase 65 - CML Executable Design by Contract

status=planned
planned_at=2026-08-12
depends_on=[Phase 64](phase-64.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 65 Checklist](phase-65-checklist.md)

## Purpose

Make CML contract declarations executable through one safe path from Cozy
parsing and SimpleModeler generation to CNCF Operation and Aggregate runtime
boundaries.

Phase 65 completes the gap between preserved contract metadata and automatic
contract evaluation. It keeps DbC for defect detection and keeps expected
domain validation, authorization, infrastructure failures, and ordinary user
rejection in their existing `Consequence`/`Conclusion` paths.

## Dependency

Phase 65 begins after Phase 64 closes.

Its technical foundations are Phase 7 Aggregate/View semantics, Phase 8 CML
Operation grammar, the current Cozy/SimpleModeler metadata pipeline, the
SimpleModeling structured violation vocabulary, Phase 63's canonical
StateMachine runtime and `PredicateProgram`, Phase 64's Workflow alignment,
and CNCF Operation/Aggregate execution boundaries.

## Selected Direction

- Descriptive contract prose and executable expressions are separate surfaces.
- Existing prose is never evaluated automatically.
- Executable expressions normalize to a closed, versioned, typed contract IR.
- Evaluation is deterministic, side-effect-free, bounded, and always on.
- Required unsupported or ill-typed clauses fail generation or admission.
- Operation preconditions execute after ingress/schema admission and before
  domain dispatch.
- Operation postconditions execute before a success response is committed.
- Every executable contract owned by an Operation is evaluated when that
  Operation executes, including synchronous and Action/Task/Job paths.
- Aggregate invariants execute at admitted public state boundaries before
  persistence and event publication.
- Contract clauses extend/reuse Phase 63's closed `PredicateProgram`
  foundation rather than introduce another expression evaluator.
- StateMachine and Workflow execution remain owned by Phases 63 and 64; DbC
  surrounds their Operation/Aggregate checkpoints without duplicating their
  selection or progression semantics.
- `VALIDATE` remains expected domain/input validation unless DBC-01 records a
  clause as a true DbC obligation.
- Violations use structured SimpleModeling semantics and bounded safe facets.
- Every contract evaluation leaves a correlated bounded observability outcome;
  rollback removes domain effects, not the retrievable failure diagnostic.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| DBC-01 | Inventory and semantic freeze | Existing prose, validation, precondition, postcondition, and invariant surfaces plus responsibility boundaries are classified without ambiguity. | planned |
| DBC-02 | Contract IR and evaluator contract | A typed contract program extends the Phase 63 predicate foundation with subjects, phases, versioning, limits, and compatibility policy. | planned |
| DBC-03 | CML parsing and normalization | Cozy parses executable clauses distinctly from prose and rejects unsupported or ill-typed required contracts deterministically. | planned |
| DBC-04 | Generation and ABI propagation | SimpleModeler generates typed contract programs and stable metadata/ABI without raw runtime string evaluation. | planned |
| DBC-05 | Structured violation semantics | SimpleModeling failure helpers and facets preserve contract kind, clause, subject, phase, and safe cause evidence. | planned |
| DBC-06 | CNCF runtime enforcement | Operation and Aggregate checkpoints evaluate exactly once around the established StateMachine/Workflow boundaries before success, persistence, or publication. | planned |
| DBC-07 | Projection, observability, and compatibility | CallTree/trace/metrics/audit plus Help/meta/JSON expose bounded correlated contract and transition identity while legacy descriptive metadata remains compatible and sensitive values do not leak. | planned |
| DBC-08 | Cross-repository acceptance and promotion | Generated samples, property-based specifications, full validation, and downstream evidence pass before verified contracts move to design/specification. | planned |

## Acceptance

- One CML source produces one typed executable contract representation across
  Cozy, SimpleModeler, SimpleModeling, and CNCF.
- Existing descriptive precondition/postcondition prose remains descriptive.
- Unsupported or ill-typed executable clauses fail explicitly and cannot be
  admitted as silently inactive metadata.
- Contract evaluation performs no ambient or provider effect.
- Operation and Aggregate evaluation order is deterministic and covered by
  Executable Specifications.
- A StateMachine-bound Operation preserves Phase 63 transition semantics while
  evaluating each owning contract exactly once at the defined checkpoints.
- A failed precondition prevents handler dispatch.
- A failed postcondition prevents successful response commitment.
- A failed Aggregate invariant prevents persistence and event publication.
- A guard non-match remains transition selection behavior; an evaluator fault,
  transition rejection, and a false DbC clause remain separately classified.
- Contract and transition failures remain visible in the correlated CallTree
  and remain retrievable through the canonical observability path even when the
  UnitOfWork rolls back.
- Failed contract or transition evaluation emits no successful transition or
  domain event.
- Violations retain stable semantic identity and safe facets through HTTP and
  shell/CLI presentation without leaking input, state, result, secret, or
  expression values.
- Representative generated CML proves positive and negative paths through the
  real CNCF runtime boundary.
- All affected repositories pass focused and complete validation before the
  provisional note is promoted to design/specification.

## Non-Goals

- Executing arbitrary Scala, Java, JavaScript, shell, or embedded scripts.
- Providing a general OCL implementation or a Rule Engine authoring language.
- Reading databases, services, clocks, randomness, files, environment, or
  mutable global state from a contract expression.
- Reclassifying expected business validation or user rejection as a defect.
- Replacing authentication, authorization, schema validation, OCC,
  idempotency, availability handling, or normal domain error semantics.
- Distributed cross-component transaction contracts.
- General inheritance-contract covariance/contravariance enforcement.
- Replacing the canonical core StateMachine primitives or duplicating their
  deterministic transition-selection semantics inside the DbC evaluator.

## Development Candidate Alignment

| Strategy item | Phase 65 relationship | Retained candidate scope |
| --- | --- | --- |
| 9.4 Metrics and Observability | Supplies minimum correlated contract outcomes and rollback lookup. | Platform retention, payload storage, exporters, dashboards, durable metrics, and operations remain future work. |
| 9.7 Error Model Follow-ups | Adds required contract violation/evaluator/admission semantics and projections. | Broad message-only cleanup, compatibility policy, catalogs, application/CLI codes, and trace UX remain future work. |
| 9.43 Transport Idempotency | Evaluates contracts only when an Operation actually executes. | Transport replay/store/token/fingerprint semantics remain independent; recorded response replay does not rerun DbC. |

## Planning References

- [Phase 65 Checklist](phase-65-checklist.md)
- [Provisional Specification](../notes/cml-executable-design-by-contract-provisional-specification.md)
- [Consideration Record](../journal/2026/08/2026-08-12-cml-executable-design-by-contract-consideration.md)
- [Phase Sequencing Record](../journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md)
- [Phase 63](phase-63.md)
- [Phase 64](phase-64.md)
- [Aggregate/View Semantic Boundary](../design/aggregate-view-semantic-boundary.md)
- [Error Taxonomy Catalog](../design/error-taxonomy-catalog.md)
- [Phase 7](phase-7.md)
- [Phase 8](phase-8.md)
