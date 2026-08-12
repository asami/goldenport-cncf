# Phase 65 Checklist - CML Executable Design by Contract

status=planned
phase=[Phase 65 - CML Executable Design by Contract](phase-65.md)

This checklist is the authoritative Phase 65 state ledger after Phase 65
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 64 closes.

## DBC-01: Inventory and Semantic Freeze

Stage Status:
- Current status: PLANNED
- Owner: Cozy, SimpleModeler, SimpleModeling, CNCF, and CML contract maintainers
- Entry rule: Phase 64 is closed.
- Completion rule: Every current contract surface and responsibility class is
  inventoried, classified, and represented by failing-first acceptance
  identities before implementation.

- [ ] Inventory CML operation/use-case `PRECONDITION`, `POSTCONDITION`, and
  `RULE` syntax, AST, normalized model, generated metadata, Help, JSON/YAML,
  and runtime consumers.
- [ ] Inventory Aggregate command/create `VALIDATE`, `STATE`, and `INVARIANT`
  syntax, AST, generated definitions, runtime consumers, and samples.
- [ ] Inventory the verified Phase 63 StateMachine predicate, transition,
  candidate-state, UnitOfWork, committed-envelope, and failure-observability
  interfaces consumed by DbC.
- [ ] Inventory the verified Phase 64 Workflow trigger, Operation invocation,
  WorkflowInstance, Job, and observability interfaces crossed by DbC-enabled
  Operations.
- [ ] Inventory existing handwritten `require`, `assert`, validation,
  `Consequence`, and `Conclusion` enforcement at generated and CNCF boundaries.
- [ ] Classify each surface as descriptive prose, expected input/domain
  validation, DbC precondition, DbC postcondition, or Aggregate invariant.
- [ ] Freeze ownership across Cozy parsing, SimpleModeler IR/generation,
  SimpleModeling failure semantics, and CNCF execution.
- [ ] Freeze the boundary between false DbC clauses, guard non-match,
  transition rejection, and guard/evaluator failure.
- [ ] Freeze DbC checkpoints around StateMachine-bound and Workflow-invoked
  Operations without moving transition/progression ownership into DbC.
- [ ] Freeze the compatibility rule that existing prose is never executable by
  inference.
- [ ] Reconcile strategy candidates 9.4, 9.7, and 9.43 so DbC owns only its
  required observability/error/idempotency interactions and does not close
  retained platform or transport scope.
- [ ] Register exact failing-first Executable Specifications for every Phase 65
  acceptance group and cross-repository boundary.

Evidence:
- Pending.

## DBC-02: Contract IR and Evaluator Contract

Stage Status:
- Current status: PLANNED
- Owner: CML semantic model and runtime contract maintainers
- Entry rule: DBC-01 is DONE.
- Completion rule: The closed IR, subject/context model, evaluator behavior,
  versioning, limits, and failure policy are fixed by static and executable
  specifications.

- [ ] Define closed contract kinds for precondition, postcondition, invariant,
  and separately classified validation.
- [ ] Define stable clause identity, source location, subject identity,
  evaluation phase, severity, and optional safe message metadata.
- [ ] Define typed expression nodes for literals, field/path selection,
  equality/order comparison, boolean composition, and the admitted bounded
  empty/non-empty/size predicates.
- [ ] Define explicit evaluation contexts for operation input/result and
  Aggregate command/current-state/new-state/events.
- [ ] Reuse or conservatively extend Phase 63 `PredicateProgram` expression
  nodes while preserving StateMachine guard compatibility.
- [ ] Define contract subjects for Phase 63 candidate-state and Phase 64
  Workflow-invoked Operation boundaries without redefining their bindings.
- [ ] Define immutable bounded `old` state availability for postconditions
  without exposing provider handles or mutable objects.
- [ ] Define deterministic null/missing/unknown behavior and prohibit implicit
  truthiness or string coercion.
- [ ] Define evaluator depth, collection, numeric, string, and diagnostic-size
  limits.
- [ ] Define IR and expression-language version compatibility and rejection.
- [ ] Prohibit network, filesystem, environment, clock, random, reflection,
  class loading, script, database, service, and arbitrary function effects.
- [ ] Preserve the completed Phase 63 MVEL migration/admission contract and
  prohibit a separate raw-expression fallback for Phase 65 programs.
- [ ] Add property-based evaluator determinism, totality, limit, and malformed
  program specifications.

Evidence:
- Pending.

## DBC-03: CML Parsing and Normalization

Stage Status:
- Current status: PLANNED
- Owner: Cozy and CML parser/modeler maintainers
- Entry rule: DBC-02 is DONE.
- Completion rule: Executable clauses are explicit, typed, source-attributed,
  and cannot be confused with descriptive contract prose.

- [ ] Add or fix explicit executable clause grammar without changing the
  meaning of existing prose-only sections.
- [ ] Normalize executable preconditions and postconditions into the closed IR.
- [ ] Normalize Aggregate validations and invariants according to the DBC-01
  semantic classification.
- [ ] Normalize DbC clauses against the shared Phase 63 predicate IR without
  changing StateMachine guard definitions or Workflow bindings.
- [ ] Preserve explicit Phase 63/64 binding identities in clause context
  without inferring them from unrelated names.
- [ ] Preserve clause id and source position through parsing and normalization.
- [ ] Reject duplicate ids, unknown subjects, unknown paths, unsupported
  operators, type mismatches, and incompatible language versions.
- [ ] Keep legacy readable forms compatible while requiring explicit opt-in to
  executable semantics during migration.
- [ ] Add Given/When/Then plus ScalaCheck parsing, normalization, diagnostic,
  and compatibility specifications.

Evidence:
- Pending.

## DBC-04: Generation and ABI Propagation

Stage Status:
- Current status: PLANNED
- Owner: SimpleModeler and generated-component maintainers
- Entry rule: DBC-03 is DONE.
- Completion rule: Generated code and metadata carry one typed versioned
  contract program and do not require CNCF to evaluate raw source strings.

- [ ] Add the closed contract model to the generator input boundary.
- [ ] Generate stable typed contract programs for operation and Aggregate
  clauses.
- [ ] Reuse generated Phase 63 predicate programs where expression nodes are
  identical and generate only the additional DbC subject/phase envelope.
- [ ] Reference Phase 63/64 binding metadata without duplicating transition or
  Workflow definitions in the DbC ABI.
- [ ] Preserve deterministic clause ordering, ids, source references, subject,
  phase, and language version.
- [ ] Keep descriptive prose available for Help without making it executable.
- [ ] Define ABI/manifest impact and an explicit compatibility strategy for
  components generated before Phase 65.
- [ ] Reject generation when a required contract cannot be represented.
- [ ] Add deterministic source, metadata, Record/JSON, ABI, and compilation
  Executable Specifications.

Evidence:
- Pending.

## DBC-05: Structured Violation Semantics

Stage Status:
- Current status: PLANNED
- Owner: SimpleModeling error-model and CNCF observability maintainers
- Entry rule: DBC-04 is DONE.
- Completion rule: Every contract outcome has stable semantic classification
  and safe structured evidence before transport presentation.

- [ ] Reuse or extend the closed invariant-, precondition-, and
  postcondition-violation vocabulary without string-only fallbacks.
- [ ] Define semantic helpers for typed clause violations and evaluator
  admission/evaluation failures.
- [ ] Attach bounded facets for clause id, contract kind, component,
  service/operation or Aggregate, evaluation phase, and source position.
- [ ] Attach StateMachine, transition, source-state, target-state, event, guard,
  job/task, trace, and span identities when applicable.
- [ ] Preserve safe exception/cause evidence while giving interruption and
  fatal errors their existing priority.
- [ ] Prohibit raw input, state, result, event, credential, secret, and
  expression-value leakage.
- [ ] Define stable DetailCode, Record, and JSON projections.
- [ ] Add Executable Specifications for semantic helpers, serialization,
  transport conversion, observability classification, and non-leakage.

Evidence:
- Pending.

## DBC-06: CNCF Operation and Aggregate Runtime Enforcement

Stage Status:
- Current status: PLANNED
- Owner: CNCF Operation, Aggregate, UnitOfWork, StateMachine/Workflow adapter,
  and runtime maintainers
- Entry rule: DBC-05 is DONE.
- Completion rule: Contracts execute exactly once in the fixed order and a
  violation cannot cross its no-side-effect boundary.

- [ ] Evaluate operation preconditions after authentication, authorization,
  transport/schema decoding, and expected validation but before handler
  dispatch.
- [ ] Ensure synchronous and Action/Task/Job Operation execution evaluate the
  owning pre/postcondition program exactly once.
- [ ] Invoke the already verified Phase 63 transition boundary exactly once
  where an Operation is StateMachine-bound.
- [ ] Preserve Phase 63 semantics: guard false continues candidate selection,
  guard failure stops selection, and no admitted transition remains distinct
  from a DbC violation.
- [ ] Evaluate operation postconditions after handler success but before a
  successful response is committed or projected.
- [ ] Evaluate Aggregate invariants for admitted current state and candidate new
  state at the frozen public boundary.
- [ ] Evaluate command-specific contracts before persistence and event
  publication.
- [ ] Surround Phase 63 candidate-state/local-effect execution with contract
  checkpoints without duplicating transition hooks or effect execution.
- [ ] Check postconditions and candidate-state invariants after transition
  planning and before persistence or successful transition/domain event
  publication.
- [ ] Guarantee that precondition failure performs no handler effect.
- [ ] Guarantee that postcondition or invariant failure performs no persistence,
  event publication, successful response, or downstream projection effect.
- [ ] Guarantee that failed contract/guard/transition evaluation publishes no
  successful state-transition lifecycle or business event.
- [ ] Define behavior for asynchronous Action/Task/Job execution without
  duplicating or skipping evaluation.
- [ ] Prove that an Operation selected by Phase 64 Workflow follows the same
  DbC path and cannot bypass pre/postcondition or invariant enforcement.
- [ ] Define compatibility admission for components without executable
  contract programs.
- [ ] Add property-based ordering, exactly-once, no-side-effect, retry, and
  cancellation/interruption Executable Specifications, including overlapping
  transition candidates and guard failures.

Evidence:
- Pending.

## DBC-07: Projection, Observability, and Compatibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF Help, meta, HTTP, CLI, CallTree, and compatibility maintainers
- Entry rule: DBC-06 is DONE.
- Completion rule: Contract availability and violation identity are observable
  without exposing executable values or changing legacy prose semantics.

- [ ] Project contract kind, clause id, subject, phase, language version, and
  executable/descriptive status through bounded Help/meta/JSON surfaces.
- [ ] Record one correlated contract-evaluation observability node/outcome with
  component, service/operation, Aggregate, machine, transition, clause, phase,
  trace, and job/task identities where applicable.
- [ ] Select one canonical persistence/export path for synchronous and
  Action/Task/Job contract failures and define lookup by trace/correlation or
  job/task identity.
- [ ] Record transition planning/guard outcomes alongside contract outcomes
  without merging their semantic classifications.
- [ ] Keep expression text and evaluated values hidden by default from runtime
  diagnostics and public transport errors.
- [ ] Record safe CallTree/audit/metric identity and coarse outcome without
  sensitive values.
- [ ] Ensure contract and transition failure diagnostics survive UnitOfWork
  rollback and remain retrievable while domain transition events follow normal
  commit semantics.
- [ ] Preserve semantic failure through HTTP, CLI, shell, synchronous, and
  asynchronous boundaries before any generic Throwable fallback.
- [ ] Define legacy generated-component behavior and explicit compatibility
  diagnostics.
- [ ] Prove that unsupported required programs fail admission instead of
  running without enforcement.
- [ ] Add projection round-trip, redaction, compatibility, and transport
  Executable Specifications, including observability lookup after rollback.

Evidence:
- Pending.

## DBC-08: Cross-Repository Acceptance and Contract Promotion

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy, SimpleModeler, SimpleModeling, sample, documentation, and
  release maintainers
- Entry rule: DBC-07 is DONE.
- Completion rule: Representative generated behavior and every affected
  repository pass the final gate before the accepted contract becomes
  normative.

- [ ] Add one representative CML sample with executable operation
  precondition/postcondition, Aggregate invariant clauses, and an
  Operation-bound guarded StateMachine transition.
- [ ] Prove positive, malformed, unsupported, precondition-failure,
  postcondition-failure, invariant-failure, and non-leakage paths through the
  real CNCF runtime boundary.
- [ ] Prove StateMachine guard non-match, guard failure, invalid source state,
  successful transition, rejected transition, and contract-blocked transition
  paths with exact observability evidence.
- [ ] Prove an Operation selected by the generated Phase 64 Workflow follows
  the same contract enforcement and failure-observability path.
- [ ] Prove generated behavior is deterministic and does not depend on ambient
  environment or provider state.
- [ ] Run focused parser, generator, error-model, evaluator, Operation,
  Aggregate, HTTP/CLI, and sample Executable Specifications.
- [ ] Run `Test/compile` and full tests for every affected repository through
  the serialized SBT contract.
- [ ] Run required cross-repository generation, ABI, and downstream sample
  acceptance.
- [ ] Perform clean review, admitted review-fix, focused re-review, naming,
  executable-specification, and `git diff --check` gates.
- [ ] Promote verified semantics and architecture from notes to `docs/design`.
- [ ] Promote verified behavior and expression/IR contract to `docs/spec`.
- [ ] Update developer guidance, strategy, phase evidence, versions, and
  release commits only after every acceptance item passes.
- [ ] Reconcile final implementation evidence with every linked development
  candidate and update candidate status without implicit absorption.

Evidence:
- Pending.
