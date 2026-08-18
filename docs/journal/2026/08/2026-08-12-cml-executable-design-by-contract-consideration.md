# CML Executable Design by Contract Consideration

date = 2026-08-12
status = consideration record
target_phase = 65

## Question

The investigation asked whether CML/CNCF already supports Design by Contract
and whether strategy/phase contains a development plan for executable DbC.

## Confirmed Current State

- CML operation/use-case models preserve descriptive precondition and
  postcondition metadata.
- CML Aggregate definitions preserve command/create validation expressions and
  named invariant expressions.
- Cozy and SimpleModeler propagate those surfaces into generated metadata and
  definitions.
- CNCF exposes contract-related metadata through runtime/help/projection
  surfaces.
- SimpleModeling defines structured invariant-, precondition-, and
  postcondition-violation vocabulary and semantic helpers.
- The representative Aggregate sample calls a handwritten validation function;
  the runtime does not generally interpret the declared raw expression.

## Planning Finding

Phase 7 defined Aggregate as a command-side model with invariant enforcement
and marked its semantic/generation/runtime/executable-spec work complete.
That phase fixed the responsibility and metadata boundary. It did not establish
a general typed evaluator that automatically executes arbitrary CML
precondition, postcondition, validation, or invariant declarations.

Phase 8 completed first-class Operation grammar and metadata propagation. Later
Cozy work verified that descriptive precondition/postcondition metadata survives
normalization. No existing open strategy item or phase explicitly owns CML
contract-expression execution.

The resulting gap is a new capability rather than corrective maintenance for a
closed phase. Reopening Phase 7 would mix historical completion evidence with a
larger cross-repository runtime feature.

## Options Considered

### Reopen Phase 7

Rejected.

- Phase 7 is closed and its normal work groups are frozen.
- A generic contract evaluator is broader than a correction to one Phase 7
  claim.
- Operation pre/postconditions also cross Phase 8 and later Cozy work.

### Treat handwritten validation as sufficient

Rejected as the target state.

- It preserves developer control but leaves CML declarations non-executable.
- Metadata and implementation can drift silently.
- Generated components cannot provide uniform ordering, observability, or
  no-side-effect guarantees.

### Evaluate raw strings at CNCF runtime

Rejected.

- It would duplicate CML parsing and typing in CNCF.
- Arbitrary evaluation creates security, determinism, compatibility, and
  observability problems.
- Unsupported expressions could become environment-dependent runtime failures.

### Generate a closed typed contract program

Selected for planning.

- Cozy remains the CML parser/modeler owner.
- SimpleModeler carries a versioned typed representation into generated code.
- SimpleModeling owns reusable structured violation semantics.
- CNCF evaluates the program at existing Operation/Aggregate/UnitOfWork
  boundaries.
- The evaluator can be pure, bounded, deterministic, and covered by
  property-based Executable Specifications.

## Semantic Decision

DbC remains defect detection, not a general error-handling mechanism.

- Preconditions express caller/program obligations.
- Postconditions express implementation guarantees.
- Aggregate invariants express validity of admitted public Aggregate state.
- Expected user input and business rejection remain normal validation/domain
  failures.
- Infrastructure unavailability, timeout, authorization denial, concurrency
  conflict, and ordinary not-found outcomes are not DbC violations.

Existing descriptive prose is never inferred to be executable. Aggregate
`VALIDATE` is not automatically reclassified as a DbC precondition; its exact
role is an explicit DbC semantic-freeze decision.

## Initial Phase Decision

The initial decision was to create Phase 63, after the already planned Phase
62, with the title:

`CML Executable Design by Contract`

The phase owns:

1. inventory and semantic freeze;
2. closed typed contract IR and safe evaluator contract;
3. Cozy grammar/parsing/normalization;
4. SimpleModeler generation and ABI propagation;
5. SimpleModeling structured violation semantics;
6. CNCF Operation/Aggregate runtime enforcement;
7. projection, observability, redaction, and compatibility; and
8. cross-repository acceptance and promotion to design/specification.

## Requirement Refinement

The initial Phase 63 request was refined with two mandatory outcomes:

1. every executable DbC definition declared in CML is checked when its owning
   Operation executes, and an error remains in observability; and
2. the contract execution path connects cleanly to CML/CNCF StateMachine
   transition planning and enforcement.

These are acceptance requirements, not optional follow-ups.

The existing StateMachine foundation already provides canonical core guards,
deterministic priority/declaration ordering, generated transition rules,
pre-persistence transition hooks, lifecycle events, and UnitOfWork CallTree
coverage. A staged transition-failure event alone is not sufficient evidence
for the new requirement because the owning UnitOfWork may roll back. The
foundation also currently supports raw MVEL expression guards and retains a
legacy/transition binding surface. The DbC capability must integrate with these
existing boundaries rather than build a parallel transition engine.

The selected planning direction is:

- use one closed typed expression IR/evaluator for DbC clauses and
  StateMachine expression guards;
- retain named guards through the existing resolver extension point;
- bind an Operation explicitly to a StateMachine event/transition in generated
  metadata rather than inferring by name;
- preserve core semantics where guard false is a non-match and guard failure
  stops transition selection;
- evaluate current invariant and precondition before handler/transition
  effects, then postcondition and candidate invariant before commit; and
- record contract/guard/transition outcomes with shared trace/job/task
  correlation while keeping their semantic failure categories distinct; and
- keep failure diagnostics retrievable through the canonical observability
  path after domain UnitOfWork rollback.

## Provisional Runtime Direction

The working order for an Aggregate-backed, StateMachine-bound Operation is:

1. ingress authentication/authorization and decoding;
2. expected schema/domain validation;
3. current state load;
4. current-state invariant;
5. precondition;
6. deterministic transition selection and guard evaluation;
7. handler plus admitted transition effects;
8. postcondition;
9. candidate-state invariant and target-state check;
10. persistence and successful transition/domain event publication; and
11. success response projection.

This order is provisional until DBC-01/DBC-02 freeze it against existing
UnitOfWork behavior. The essential boundary is that a failed contract cannot be
silently ignored or cross the next success/side-effect checkpoint.

A false guard is not automatically a DbC violation. A guard non-match,
transition rejection, evaluator failure, and pre/post/invariant violation must
remain distinguishable. Failed evaluation must be visible in CallTree or its
canonical successor even when UnitOfWork rollback prevents domain events and
state changes.

## Compatibility Direction

- Existing prose remains prose.
- Existing generated components without typed contract programs follow an
  explicit compatibility path.
- New required executable clauses use a versioned program.
- Unknown, unsupported, or ill-typed required programs fail generation or
  admission rather than running without enforcement.
- Migration of existing Aggregate invariant expressions must be explicit and
  covered by generated-source and runtime acceptance.

## Safety and Observability Direction

Contract evaluation must not access services, databases, files, environment,
clock, randomness, reflection, processes, or arbitrary code.

Violations should preserve stable clause and subject identity through
SimpleModeling semantic failures. Public errors, logs, CallTree, metrics, and
audit must not expose raw input, state, result, event, credential, secret, or
evaluated expression values.

## Open Questions Carried into the DbC Phase

- Exact executable CML syntax.
- Exact `VALIDATE` semantics.
- Whether current-state data is available to preconditions.
- Initial expression and built-in predicate subset.
- `old` state snapshot representation and limit.
- Migration/version boundary for existing invariant expressions.
- Public versus developer-only expression-source visibility.
- Failure taxonomy for invalid or incompatible contract programs, distinct
  from a valid clause evaluating to false.
- Explicit Operation-to-StateMachine binding syntax and generated metadata.
- Migration of current raw MVEL expression guards to the closed evaluator.
- Exact durable/non-durable policy for failed-transition observability records.

## Sequencing Supersession

Subsequent StateMachine and Workflow consideration showed that the DbC phase
depends on two contracts that should be completed independently:

1. canonical CML StateMachine guard/action/UnitOfWork/committed-transition
   execution; and
2. committed-transition-to-WorkflowInstance-to-next-Operation alignment.

The current sequence therefore moves this DbC plan from Phase 63 to Phase 65:

- Phase 63: CML StateMachine Runtime Completion;
- Phase 64: StateMachine-Workflow Alignment; and
- Phase 65: CML Executable Design by Contract.

The original Phase 63 decision above is retained as chronological evidence; it
is superseded by
`2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`. DbC reuses the
Phase 63 `PredicateProgram` and executes around the verified Phase 63/64
boundaries rather than owning transition or Workflow semantics.

The Operation-to-StateMachine binding, raw MVEL migration, transition action,
and rollback-observability questions listed above now enter Phase 63. Phase 65
consumes the verified answers and retains only its DbC-specific subject and
checkpoint questions.

The strategy's existing development candidates were reconciled separately in
`2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`. In particular,
Phase 65 adds only the DbC-specific portion of Error Model and Observability;
it does not close their broader hardening candidates or the separate transport
idempotency candidate.

## Documents Added

- `docs/strategy/cncf-development-strategy.md` item 9.56
- `docs/phase/phase-65.md`
- `docs/phase/phase-65-checklist.md`
- `docs/notes/cml-executable-design-by-contract-provisional-specification.md`
- `docs/journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`

This journal is chronological evidence only. The provisional note is
non-normative. Phase 65 and its checklist own work state. Verified behavior must
be promoted to design/specification before Phase 65 closes. The artifact
lifecycle follows `docs/rules/document-lifecycle.md`.
