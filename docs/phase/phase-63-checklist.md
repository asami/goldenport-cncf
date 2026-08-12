# Phase 63 Checklist - CML StateMachine Runtime Completion

status=planned
phase=[Phase 63 - CML StateMachine Runtime Completion](phase-63.md)

This checklist is the authoritative Phase 63 state ledger after Phase 63
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 62 closes.

## SMR-01: Inventory and Semantic Freeze

Stage Status:
- Current status: PLANNED
- Owner: Cozy, SimpleModeler, SimpleModeling, CNCF, StateMachine, Aggregate,
  UnitOfWork, and observability maintainers
- Entry rule: Phase 62 is closed.
- Completion rule: Current semantics, gaps, bypasses, and exact failing-first
  acceptance identities are frozen before implementation.

- [ ] Inventory CML StateMachine grammar/AST for state, event, transition,
  source/target, priority, guard, action/effect, entry, and exit declarations.
- [ ] Inventory SimpleModeler transition generation, generated action bodies,
  named/expression guards, priority/declaration order, metadata, and ABI.
- [ ] Inventory core `StateMachine`, `TransitionDecider`, `Guard`, and `Effect`
  semantics and the CNCF `TransitionSelector`/planner/provider/hook path.
- [ ] Identify duplicate selection semantics and freeze the canonical reuse or
  adapter boundary.
- [ ] Inventory raw MVEL execution, missing named-guard resolution, defaulted
  priority, no-op generated actions, and transition identity loss.
- [ ] Inventory create, update/save, patch, command, direct/unversioned,
  migration, retry, and replay paths for enforcement bypass.
- [ ] Inventory the provisional Aggregate `IMPLEMENTATION` paths
  `pattern:state-machine` and `pattern:state-transition`, then freeze which
  minimal shared implementation interface Phase 63 actually selects.
- [ ] Inventory staged/published transition events and CallTree behavior on
  commit, action failure, persistence failure, cancellation, and rollback.
- [ ] Freeze the boundary between local transition effects and post-commit
  external work.
- [ ] Reconcile strategy candidates 9.2, 9.4, 9.7, 9.10, and 9.53 so Phase 63
  consumes only its explicit minimum and does not close retained candidate
  scope.
- [ ] Keep other Aggregate implementation kinds, patterns, inline Scala, and
  broad factory/Operation reuse outside Phase 63 unless separately admitted.
- [ ] Register failing-first Executable Specifications for every Phase 63
  acceptance group and cross-repository surface.

Evidence:
- Pending.

## SMR-02: Canonical Transition and Predicate Contract

Stage Status:
- Current status: PLANNED
- Owner: core StateMachine, CML semantic-model, and CNCF adapter maintainers
- Entry rule: SMR-01 is DONE.
- Completion rule: Pure selection, guard, effect, result, ordering, and failure
  semantics are fixed without parallel transition engines.

- [ ] Fix core StateMachine as the canonical pure transition-selection owner.
- [ ] Fix deterministic ordering by `(priority asc, declarationOrder asc)`.
- [ ] Preserve guard false as candidate non-match and guard failure as terminal
  selection failure.
- [ ] Define a closed, typed, versioned, side-effect-free `PredicateProgram`
  with bounded depth, size, path, numeric, string, and collection behavior.
- [ ] Define named guard identity and explicit `GuardBindingResolver` behavior.
- [ ] Define stable machine, transition, state, event, guard, and action ids.
- [ ] Define `TransitionPlan`, candidate state, admitted local effect plan, and
  success/failure result vocabulary.
- [ ] Prohibit ambient service, provider, datastore, filesystem, process,
  network, environment, clock, randomness, reflection, and script effects.
- [ ] Add property-based determinism, malformed predicate, limit, candidate
  overlap, and failure-priority specifications.

Evidence:
- Pending.

## SMR-03: CML Parsing and Normalization

Stage Status:
- Current status: PLANNED
- Owner: Cozy and CML parser/modeler maintainers
- Entry rule: SMR-02 is DONE.
- Completion rule: CML declarations normalize to the canonical closed model
  with stable identity and deterministic diagnostics.

- [ ] Normalize machine/state/event/transition identity and source position.
- [ ] Normalize explicit priority and declaration order without constant
  defaulting that discards source intent.
- [ ] Normalize expression guards to `PredicateProgram`.
- [ ] Normalize named guards/actions as explicit binding references.
- [ ] Normalize entry, exit, and transition action phases.
- [ ] Reject duplicate ids, missing states/events, invalid targets, invalid
  priorities, unsupported predicates, and ambiguous bindings.
- [ ] Define explicit compatibility admission for legacy CML and generated
  raw-expression rules.
- [ ] Add Given/When/Then and ScalaCheck parser/normalization/diagnostic specs.

Evidence:
- Pending.

## SMR-04: SimpleModeler Generation and ABI

Stage Status:
- Current status: PLANNED
- Owner: SimpleModeler and generated-component maintainers
- Entry rule: SMR-03 is DONE.
- Completion rule: Generated code carries executable typed transition
  definitions and stable metadata without required no-op/raw-string behavior.

- [ ] Generate canonical transition definitions with stable ids and ordering.
- [ ] Generate typed predicate programs and named guard/action references.
- [ ] Generate executable local transition actions or explicit resolver
  bindings instead of placeholder no-op behavior.
- [ ] Preserve source/target/event/priority/declaration order and source
  location through metadata, Help, Record, and JSON.
- [ ] Define generated ABI/version admission for old and new components.
- [ ] Reject generation when a required transition cannot be represented.
- [ ] Add deterministic generated-source, metadata, ABI, compilation, Record,
  and JSON Executable Specifications.

Evidence:
- Pending.

## SMR-05: Atomic CNCF Transition Execution

Stage Status:
- Current status: PLANNED
- Owner: CNCF StateMachine, Aggregate, persistence, and UnitOfWork maintainers
- Entry rule: SMR-04 is DONE.
- Completion rule: One CNCF path plans and commits an admitted transition
  exactly once with atomic local state/effect behavior.

- [ ] Adapt CNCF planning to canonical core transition selection rather than
  reimplementing it.
- [ ] Load current entity state and validate source-state applicability.
- [ ] Evaluate guards and construct exactly one candidate transition plan.
- [ ] Construct candidate entity state without mutating persisted state.
- [ ] Execute exit, transition, and entry local effects in the frozen order.
- [ ] Place candidate state, admitted local effects, persistence, and outcome
  staging in one UnitOfWork.
- [ ] Ensure action, persistence, cancellation, and interruption failures roll
  back state and successful-event staging.
- [ ] Cover create, update/save, patch, command, direct/unversioned, retry, and
  compatibility routes; reject unsafe bypass explicitly.
- [ ] Add property-based exactly-once, atomicity, ordering, retry, replay,
  cancellation, and rollback specifications.

Evidence:
- Pending.

## SMR-06: Trigger Binding and Committed Transition

Stage Status:
- Current status: PLANNED
- Owner: CNCF Operation, event, StateMachine, and UnitOfWork maintainers
- Entry rule: SMR-05 is DONE.
- Completion rule: Trigger bindings are explicit and only a committed
  transition produces the canonical downstream envelope.

- [ ] Define explicit Operation/event-to-machine trigger binding.
- [ ] Reject name-based or status-field inference where no binding exists.
- [ ] Define `CommittedTransition` fields for component, entity type/id,
  machine, transition, source/target, trigger/event, operation, commit,
  correlation, trace/span, and occurrence identity.
- [ ] Publish/stage the envelope only after the owning UnitOfWork commits.
- [ ] Give the envelope a stable idempotency/replay identity.
- [ ] Guarantee failed, rejected, non-matching, or rolled-back transitions emit
  no success envelope.
- [ ] Preserve the envelope through Event and Job handoff without changing its
  semantic identity.
- [ ] Add commit-order, duplicate, replay, retry, and rollback specs.

Evidence:
- Pending.

## SMR-07: Failure Observability and Compatibility

Stage Status:
- Current status: PLANNED
- Owner: SimpleModeling error, CNCF observability, HTTP, shell/CLI, and
  compatibility maintainers
- Entry rule: SMR-06 is DONE.
- Completion rule: Every outcome is structurally observable and legacy
  behavior is explicit without leaking state or expression values.

- [ ] Define closed structured outcomes for invalid source, no match,
  ambiguity, guard admission/evaluation, action, target, persistence, and
  rollback failure.
- [ ] Attach bounded safe machine, transition, state, event, entity,
  operation, phase, trace/span, job/task, exception/cause, and commit facets.
- [ ] Preserve interruption/fatal priority at Throwable boundaries.
- [ ] Define a failure-observability path that survives domain rollback.
- [ ] Keep raw state, input, event payload, credentials, secrets, expression
  source, and evaluated values out of public diagnostics.
- [ ] Define DetailCode, Record, JSON, Help/meta, CallTree, metric, and audit
  projections.
- [ ] Define legacy raw-expression/generated-component admission and migration.
- [ ] Add non-leakage, transport-boundary, rollback-visibility, and
  compatibility Executable Specifications.

Evidence:
- Pending.

## SMR-08: Cross-Repository Acceptance and Promotion

Stage Status:
- Current status: PLANNED
- Owner: Cozy, SimpleModeler, SimpleModeling, CNCF, sample, and documentation
  maintainers
- Entry rule: SMR-07 is DONE.
- Completion rule: The real generated/runtime path passes complete validation
  and verified contracts are promoted from notes.

- [ ] Add a generated `SalesOrder` entity with `SalesStatus` StateMachine and
  representative positive/negative transitions.
- [ ] Prove priority, declaration-order, named/expression guards, actions,
  create/update/patch/command routes, atomic rollback, and success envelopes.
- [ ] Prove guard non-match, guard failure, action failure, persistence failure,
  interruption, retry/replay, and diagnostic non-leakage.
- [ ] Run affected focused suites and full suites serially under project rules.
- [ ] Validate downstream generated sample/CAR behavior where admitted.
- [ ] Re-review naming, ABI, lifecycle, observability, and compatibility debt.
- [ ] Reconcile final implementation evidence with every linked development
  candidate and update candidate status without implicit absorption.
- [ ] Promote verified architecture to `docs/design` and behavior to
  `docs/spec`; keep unsupported claims provisional.
- [ ] Record exact repository, test-count, artifact, and revision evidence.

Evidence:
- Pending.
