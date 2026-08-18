# CML Executable Design by Contract Provisional Specification

status = proposed, non-normative
date = 2026-08-12
target_phase = 65

## Status and Authority

This note is a provisional specification for Phase 65 planning. It is not a
runtime contract and does not override existing rules, design, specification,
generated APIs, or Executable Specifications.

After implementation and cross-repository verification, accepted architecture
must move to `docs/design` and accepted behavior must move to `docs/spec`.

## Problem

CML currently carries several contract-like surfaces:

- operation and use-case `PRECONDITION` and `POSTCONDITION` prose;
- operation `RULE` prose;
- Aggregate create/command `VALIDATE` expressions; and
- Aggregate `INVARIANT` expressions.

The parser/modeler and generator preserve these declarations, CNCF can expose
parts of them as metadata, and SimpleModeling has structured violation
vocabulary. CNCF does not yet have one general boundary that executes typed CML
contract programs automatically.

Phase 65 must close that gap without treating every validation or business
rejection as a program defect and without executing raw source strings. Every
executable clause owned by an Operation must be evaluated when that Operation
executes, and a failure must remain in correlated observability even when the
owning UnitOfWork rolls back.

## Prerequisite Contracts

Phase 65 begins only after two separate runtime contracts are verified:

- Phase 63 owns canonical CML StateMachine parsing, generation, guard/action
  execution, candidate-state UnitOfWork semantics, `PredicateProgram`, and the
  committed-transition/failure-observability contract; and
- Phase 64 owns the connection from committed transitions to an independent
  WorkflowInstance and the next generic Operation/Job invocation.

DbC reuses those contracts. It does not reopen transition selection, implement
Workflow progression, or introduce another expression evaluator. A Workflow-
selected Operation must enter the same DbC boundary as any other Operation.

## Semantic Separation

The initial contract taxonomy is:

| Surface | Responsibility | Failure meaning | Initial execution policy |
| --- | --- | --- | --- |
| Descriptive pre/postcondition prose | Documentation | No direct runtime failure | Never inferred as executable |
| Expected input/domain validation | User/domain admissibility | Expected structured rejection | Existing validation/`Consequence` path |
| DbC precondition | Caller/program defect | Precondition violation | Before domain handler dispatch |
| DbC postcondition | Implementation defect | Postcondition violation | After handler success, before success commitment |
| Aggregate invariant | Invalid public Aggregate state | Invariant violation | At admitted current/candidate state boundaries |

`VALIDATE` is not automatically a DbC precondition. DBC-01 must classify its
current semantics and retain expected domain validation where correct.

## Source Compatibility

- Existing prose-only `PRECONDITION` and `POSTCONDITION` sections remain
  descriptive.
- The implementation must introduce or confirm an explicit syntax marker for
  executable clauses.
- No migration may guess executability from punctuation, wording, expression-
  like text, or a method name.
- Existing generated components without a contract program remain readable and
  executable under an explicit legacy compatibility policy.
- A component that declares a required executable program but uses an unknown
  language/IR version fails generation or admission. It never runs with the
  clause silently disabled.

Candidate source shape, subject to DBC-01/DBC-03 freeze:

```text
### PRECONDITION
The caller supplies a positive quantity.

#### CONTRACT
##### quantityPositive
- EXPRESSION :: input.quantity > 0
```

The final grammar may use a different heading or field, but prose and executable
form must remain structurally distinguishable.

## Closed Contract Model

Conceptual representation:

```scala
ContractProgram(
  languageVersion,
  clauses = Vector(
    ContractClause(
      id,
      kind,
      subject,
      phase,
      expression,
      source
    )
  )
)
```

The public representation must use closed types for:

- language/IR version;
- contract kind;
- subject kind;
- evaluation phase;
- expression node kind; and
- evaluation outcome.

Raw expression text may remain source/diagnostic metadata, but CNCF runtime must
evaluate the typed IR rather than reparsing or compiling the raw string.

## Initial Expression Subset

The first vertical slice should admit only operations needed by representative
existing CML contracts:

- boolean, integer/decimal, string, and explicit null/missing literals;
- typed input/current-state/old-state/new-state/result/event path selection;
- equality and ordered comparison;
- boolean `and`, `or`, and `not`;
- bounded `isEmpty`, `nonEmpty`, and `size` predicates; and
- explicitly typed numeric operations only if required by the selected sample.

The evaluator must not provide:

- arbitrary method or constructor calls;
- reflection, class loading, or dynamic dispatch;
- Scala/Java/JavaScript/shell/script evaluation;
- network, service, database, filesystem, environment, clock, random, process,
  Docker, or global mutable state access; or
- unbounded recursion, traversal, allocation, or diagnostic rendering.

Unsupported nodes and type errors are explicit failures. They are not `false`
and they do not disable the clause.

The contract program should reuse or conservatively extend the closed Phase 63
expression nodes used by StateMachine guards. Named guards remain a Phase 63
runtime binding and are not compiled into DbC clauses by inference.

## Evaluation Contexts

### Operation precondition

Available:

- normalized typed input;
- stable Operation/component/service identity; and
- explicitly admitted immutable execution facts, if any are fixed by DBC-02.

Unavailable:

- provider handles;
- arbitrary runtime context;
- mutable state not declared by the contract; and
- external reads.

### Operation postcondition

Available:

- normalized input;
- successful typed result;
- a bounded immutable `old` snapshot when the Operation owns an admitted state
  transition; and
- the candidate new state/events when the Operation is Aggregate-backed.

Evaluation occurs before the success response is committed or projected.

### Aggregate invariant

Available:

- admitted current state before a public command;
- candidate new state after handler success; and
- emitted events only where the frozen invariant model explicitly allows them.

The runtime should check current-state validity before command execution and
candidate-state validity before persistence/event publication. DBC-01 must
confirm whether creation has only a candidate-state check or a distinct empty
state contract.

### StateMachine transition context

Available:

- current state and its explicit state field;
- normalized Operation input or transition event;
- proposed/candidate state where the transition phase admits it; and
- stable machine, event, transition, source-state, and target-state identity.

The verified Phase 63 StateMachine contract owns transition semantics:

- priority then declaration order is deterministic;
- guard `false` is a non-match and continues candidate selection; and
- guard evaluation failure stops selection and propagates failure.

The DbC evaluator must not duplicate transition selection. It consumes the
selected transition/candidate-state context and reuses the shared predicate
foundation for contract clauses.

The Operation-to-StateMachine binding is a Phase 63 CML/generated contract.
Phase 65 preserves that identity and must not guess or redefine it.

## Proposed Evaluation Order

For an Aggregate-backed, StateMachine-bound Operation:

1. authenticate and authorize ingress;
2. decode and perform schema/expected domain validation;
3. load admitted current state;
4. check the current Aggregate invariant;
5. check the Operation/command precondition;
6. select one StateMachine transition from source state, event/input, guards,
   priority, and declaration order;
7. execute the domain handler and admitted transition effects exactly once;
8. check the postcondition against input, result, transition, and bounded
   before/after data;
9. check the candidate Aggregate invariant and declared target state;
10. persist state and publish successful transition/domain events through the
    normal UnitOfWork boundary; and
11. project the successful response.

The final placement must respect existing UnitOfWork atomicity. A violation at
steps 4, 5, 6, 8, or 9 must not produce handler, persistence, event, success-
response, or projection effects beyond the effects explicitly admitted before
that checkpoint. A failed or non-matching transition does not emit a successful
transition event.

For a non-Aggregate Operation, omit state/invariant steps while preserving
precondition-before-dispatch and postcondition-before-success commitment.

## StateMachine Connection

Phase 63 is required to establish canonical core `StateMachine`, `State`,
`Transition`, `Guard`, and `Effect` use, generated typed transition rules,
deterministic selection, candidate-state UnitOfWork behavior, and committed
transition observability. Phase 65 inserts contract checkpoints around those
verified boundaries; it does not replace them.

The intended connection is:

```text
CML Operation contract + Phase 63 StateMachine binding
  -> generated typed ContractProgram + verified TransitionRule
  -> Operation precondition
  -> core transition selection / guard evaluation
  -> handler and transition plan
  -> postcondition + candidate invariant
  -> UnitOfWork commit
  -> successful transition/domain event
```

Raw MVEL expression-guard migration is owned and completed by Phase 63. Phase
65 must preserve that admission decision and must not create a DbC-specific
raw-expression fallback.

## Workflow Connection

Phase 64 Workflow may select an Operation after a committed StateMachine
transition. That origin does not change DbC semantics: the Operation's
precondition, postcondition, and applicable Aggregate invariants execute once
at the same checkpoints. DbC failure is recorded against the shared trace,
WorkflowInstance, step, Operation, Job/Task, and transition context, but it
does not let Workflow bypass authorization or directly mutate entity state.

## Development Candidate Alignment

- Strategy 9.4 retains platform-wide diagnostic retention, cleanup,
  authorization, exporters, dashboards, durable metrics, and operational
  policy. Phase 65 provides only the correlated contract outcome and rollback
  lookup required for its acceptance.
- Strategy 9.7 retains general message-only cleanup, taxonomy compatibility,
  catalog generation, application/CLI code policy, and trace UX. Phase 65 owns
  only contract violation and contract-program admission/evaluation semantics.
- Strategy 9.43 retains REST/Web Form request idempotency stores, keys, tokens,
  fingerprints, response replay, and retention. DbC runs once when an Operation
  actually executes; serving a recorded transport result does not rerun it.

## Failure Semantics

Use existing structured semantic categories where sufficient:

- invariant violation;
- precondition violation; and
- postcondition violation.

The implementation may need separate structured categories for an invalid,
unsupported, incompatible, or failed-to-evaluate contract program. Such
admission/evaluator failures must not be mislabeled as a clause evaluating to
false.

Safe facets should include:

- clause id;
- contract kind;
- component;
- service/operation or Aggregate;
- evaluation phase;
- language/IR version;
- source position when safe; and
- bounded safe cause/exception identity.

For StateMachine-bound execution, also include safe identities for machine,
event, transition, source state, target state, guard, trace/span, and job/task.

Raw input, state, result, events, credentials, secrets, tokens, and evaluated
expression values must not enter HTTP/CLI errors, logs, CallTree, metrics,
audit, or generic exception messages by default.

Interruption and fatal-error priority remain governed by existing
SimpleModeling/CNCF failure rules.

## Runtime and Generation Ownership

| Concern | Owner |
| --- | --- |
| CML executable grammar, parsing, source positions, normalization | Cozy |
| Closed generator-facing contract model and generated representation | SimpleModeler |
| Violation taxonomy, semantic helpers, safe facets and serialization | SimpleModeling |
| Phase 63 predicate/transition and Phase 64 Workflow invocation contracts | Existing verified CNCF boundaries |
| Admission, evaluation ordering, UnitOfWork and Operation/Aggregate enforcement | CNCF DbC adapter |
| Representative behavior and downstream acceptance | CNCF samples and selected downstream component |

CNCF must not create a parallel CML parser. Cozy must not own CNCF runtime
dispatch or UnitOfWork policy. SimpleModeling must remain independent of CNCF.

## Observability and Projection

Each evaluated clause must create or annotate one bounded correlated
observability outcome before the result crosses the Operation boundary.

At minimum, CallTree or its canonical successor must preserve:

- `calltree_kind = contract-evaluation`;
- component and service/operation identity;
- contract kind and clause id;
- evaluation phase and coarse outcome;
- Aggregate and StateMachine/transition identity where applicable;
- trace/span/correlation and Job/Task identity where applicable; and
- structured `ConclusionDiagnostics` identity for failure.

Guard non-match, transition rejection, evaluator failure, and DbC violation
must be distinguishable. A UnitOfWork rollback must not erase the diagnostic
record. The failure must remain retrievable through the canonical observability
store/export path by trace/correlation or Job/Task identity, while rollback
prevents successful transition/business event publication.

The structured `Conclusion` and its safe contract facets remain available even
when an external trace exporter is not configured. The selected CNCF
observability profile must not silently discard a contract failure that it
claims to retain.

Help/meta/JSON may expose:

- clause id;
- kind;
- subject;
- executable versus descriptive status;
- language version; and
- coarse availability/validation state.

Expression source and runtime values should be hidden from public runtime
diagnostics by default. A developer-only source projection, if admitted, must
have an explicit confidentiality and authorization contract.

CallTree, audit, and metrics may record clause identity, evaluation phase,
duration, and coarse outcome. They must not record evaluated values.

## Executable Specification Matrix

The Phase 65 acceptance suite should cover:

- prose remains descriptive and is not executed;
- explicit clauses parse and generate deterministically;
- malformed, unknown, unsupported, ill-typed, duplicate-id, and incompatible-
  version programs fail explicitly;
- evaluator results are deterministic for the same program/context;
- depth, collection, string, numeric, and diagnostic limits are enforced;
- precondition false prevents handler dispatch;
- postcondition false prevents success commitment;
- current/candidate invariant false prevents the next side-effect boundary;
- asynchronous Action/Task/Job paths evaluate once;
- StateMachine guard selection and DbC evaluation share one typed expression
  foundation without sharing failure meaning;
- Phase 63 selects at most one transition and Phase 65 does not duplicate or
  change that decision;
- guard false, guard failure, invalid source state, and contract violation have
  distinct structured outcomes;
- failed contract/transition evaluation remains in correlated observability
  after UnitOfWork rollback and emits no success event;
- cancellation/interruption retains priority and performs cleanup;
- legacy components follow the explicit compatibility policy;
- an Operation selected by Phase 64 Workflow follows the same exactly-once DbC
  enforcement and observability path;
- Record/JSON/DetailCode projections are stable; and
- public diagnostics never leak protected values.

Use Given/When/Then structure, `should` matchers, and ScalaCheck properties in
the owning repositories.

## Open Decisions

DBC-01 and DBC-02 must resolve:

1. the exact executable CML syntax;
2. whether existing Aggregate `INVARIANT.EXPRESSION` becomes executable through
   opt-in migration or through a generation-version boundary;
3. the exact semantic role of Aggregate `VALIDATE`;
4. whether preconditions can observe admitted current Aggregate state;
5. the minimal numeric and collection expression subset;
6. the exact `old` snapshot shape and size limit;
7. the compatibility policy for previously generated components;
8. the public/developer projection policy for expression source; and
9. the exact structured categories for contract admission/evaluator faults;
10. the exact contract context exposed from the verified Phase 63 transition
    plan and Phase 64 Workflow invocation; and
11. whether the mandatory durable observability record is also accompanied by
    a separately committed failure domain event under an explicit policy.

## Deferred Scope

- General OCL compatibility.
- User-defined functions and arbitrary code execution.
- Rule Engine descriptor-language integration.
- Symbolic proof, static theorem proving, and solver-backed verification.
- Cross-service or distributed transaction contracts.
- General class-hierarchy contract inheritance rules.
- Contract authoring UI.

## Related Documents

- `docs/phase/phase-65.md`
- `docs/phase/phase-65-checklist.md`
- `docs/phase/phase-63.md`
- `docs/phase/phase-64.md`
- `docs/notes/cml-statemachine-runtime-completion-provisional-specification.md`
- `docs/notes/statemachine-workflow-alignment-provisional-specification.md`
- `docs/journal/2026/08/2026-08-12-cml-executable-design-by-contract-consideration.md`
- `docs/journal/2026/08/2026-08-12-statemachine-workflow-dbc-phase-sequencing.md`
- `docs/design/aggregate-view-semantic-boundary.md`
- `docs/design/error-taxonomy-catalog.md`
- `docs/phase/phase-7.md`
- `docs/phase/phase-8.md`
