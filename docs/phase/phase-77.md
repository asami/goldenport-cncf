# Phase 77 - First-Class CML WORKFLOW ABI Admission and Progression Contract

status=planned
planned_at=2026-09-16
depends_on=[Phase 64](phase-64.md), [Phase 64.2](phase-64.2.md), and Cozy Phase 62 producer handoff
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#964-first-class-cml-workflow-admission-and-progression)
checklist=[Phase 77 Checklist](phase-77-checklist.md)

## Purpose

Admit Cozy's first-class generated CML `WORKFLOW` definition through CNCF
without reparsing CML or creating a second Workflow language. Provide the
reusable generated-definition and progression boundary consumed by component
runtimes, including Textus `sm-workflow`.

The canonical path is:

```text
CML WORKFLOW
  -> Cozy generated WorkflowDefinition ABI
  -> CNCF ABI admission + ComponentFactory bootstrap
  -> admitted progression evaluator
  -> component-owned durable runtime
```

Phase 77 consumes Phase 64's Composite StateMachine and independent
WorkflowInstance lifecycle/recovery architecture, plus Phase 64.2's typed
`ExecProgram[UnitOfWorkOp, A]` path. It closes the first-class generated-ABI
binding to those contracts without reopening their general lifecycle semantics,
Action algebra, transaction planner, or recovery responsibilities.

## Ownership Boundary

- Cozy Phase 62 owns CML syntax, normalization, validation, source identity,
  generated WorkflowDefinition ABI, and the producer fixture.
- CNCF owns generated-ABI version admission, ComponentFactory discovery, the
  ABI binding to Phase 64's independent `WorkflowInstance` persistence
  SPI/consistency contract, a reusable deterministic progression evaluator,
  and normal typed Operation / Action integration.
- An entity-local StateMachine owns only the lifecycle data persisted with that
  entity. Its record is never the authoritative WorkflowInstance store.
- A consuming component binds the WorkflowInstance SPI to its datastore,
  retention, lease policy, public operations, and client/skill behavior.
  `sm-workflow` therefore binds it to component-local SQLite and owns
  `advance`, WorkflowRun / WorkOrder lifecycle, Continuation payloads, and
  Codex-cost observability.

## Work Stack

| ID | Outcome | Status |
| --- | --- | --- |
| CWF-77-01 | Freeze supported `cozy.cml.workflow.*` ABI versions, typed definition admission, failure diagnostics, and compatibility policy. | planned |
| CWF-77-02 | Discover generated WorkflowDefinition metadata through ComponentFactory while preserving source and Composite StateMachine identity. | planned |
| CWF-77-03 | Bind the generated Workflow ABI to Phase 64's separate WorkflowInstance persistence SPI, identity/revision/history contract, and committed-transition correlation rule that does not reuse entity persistence as the process store. | planned |
| CWF-77-04 | Implement a deterministic evaluator that reports eligible automatic progression or one explicit semantic boundary without inferring either from names or Action effects. | planned |
| CWF-77-05 | Bind declared typed Operations/actions to the existing `ExecProgram[UnitOfWorkOp, A]` path and reject unsupported automatic execution. | planned |
| CWF-77-06 | Prove the CML-first producer-to-CNCF path with Cozy's real fixture and freeze the `sm-workflow` consumer handoff. | planned |

## WorkflowInstance Persistence Boundary

`WorkflowInstance` is a separately durable process record. It has its own
stable instance identity, definition identity/version, revision, lifecycle
state, current progression/boundary, append-only history, and causal
correlation to the entity transition, Operation, or Job that admitted it.

Entity StateMachine persistence remains authoritative only for entity state.
Workflow progression never writes an entity's StateMachine status as its own
checkpoint, and entity persistence never becomes a substitute for a
WorkflowInstance store. A physical database may host both only through
separate logical store/schema ownership and explicitly configured transaction
integration; a shared table or implicit field-level ownership is prohibited.

When a committed entity transition creates or resumes a WorkflowInstance, CNCF
uses the committed-transition occurrence as the durable correlation/idempotency
key. Cross-store delivery is idempotent and recoverable; CNCF does not claim an
implicit distributed transaction. The workflow store records its own accepted
creation/progression result, so replay cannot duplicate a WorkflowInstance or
silently re-execute an external boundary.

## Required Runtime Contract

The admitted generated contract distinguishes automatic progression from a
semantic boundary. A semantic boundary must carry an explicit typed category
such as Work Order, Decision, or Wait; CNCF never derives that category from a
transition name, documentation string, Action effect label, or status value.

The evaluator is deterministic and bounded. It may select an automatic
transition only when the generated contract declares it eligible and all
required inputs are supplied by the caller's persisted projection. It returns
one explicit boundary or a terminal outcome, and it reports ambiguous,
cyclic, unsupported, or version-incompatible models as structured failures.

Phase 77 supplies no default persistence provider or background progression
loop. A consumer binds the independent WorkflowInstance store, decides when to
persist and invoke the evaluator, and must not cross a returned semantic
boundary automatically.

## Completion Conditions

- CNCF admits a real Cozy-generated WorkflowDefinition by supported ABI
  version and fails closed for unavailable or incompatible required semantics.
- ComponentFactory discovers only generated definitions; a handwritten
  canonical Workflow definition is not accepted as end-to-end evidence.
- Automatic progression and typed semantic boundaries remain visible and
  source-correlated through the admitted CNCF API.
- The evaluator neither executes an external Action nor crosses Work Order,
  Decision, or Wait boundaries automatically.
- Declared Operations/actions reuse the existing typed `ExecProgram` /
  `UnitOfWorkOp` contract; no Workflow-specific low-level Action algebra is
  added.
- Cross-repository evidence records exact Cozy source, generated ABI, and CNCF
  revisions. The Textus consumer handoff makes no claim that `sm-workflow`
  persistence or public skills are already complete.

## Non-Goals

- Parsing CML, defining CML syntax, or independently reconstructing Workflow
  semantics in CNCF.
- A second Workflow DSL, BPMN/DAG language, arbitrary scripts, or opaque
  callbacks.
- A default SQLite/datastore provider, consumer-owned migration/retention,
  WorkOrder leasing, public CLI/server contracts, skill distribution, or Codex
  cost policy.
- Replacing Phase 64 Composite StateMachine execution, Phase 64.1 recovery,
  Phase 64.2 `UnitOfWorkOp`, or a component's durable runtime ownership.

## References

- [Phase 64](phase-64.md)
- [Phase 64.2](phase-64.2.md)
- [Phase 77 Checklist](phase-77-checklist.md)
- `asami/cozy/docs/phase/phase-62.md`
