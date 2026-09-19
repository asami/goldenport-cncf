# Phase 63.2 Checklist - StateMachine Committed Transition, Observability, and Acceptance

status=completed
phase=[Phase 63.2 - StateMachine Committed Transition, Observability, and Acceptance](phase-63.2.md)

This ledger owns only `SMR-06` through `SMR-08`. It consumes the closed
Phase 63.1 handoff and is the aggregate full-validation owner for the complete
Phase 63 split sequence.

## SMR-06: Trigger Binding and Committed Transition

Stage Status:
- Current status: DONE
- Owner: CNCF Operation, Event, StateMachine, and UnitOfWork maintainers
- Entry rule: Phase 63.1 is closed.
- Completion rule: Trigger bindings are explicit and only a committed
  transition produces the canonical downstream envelope.

- [x] Define explicit Operation/event-to-machine binding and one versioned
  typed trigger-context schema.
- [x] Keep that binding local to StateMachine entry. Do not add Workflow
  action/Operation/Job selection, Required SPI Provider resolution, or any
  external execution route; those are Phase 64 / 64.2 and Phase 77 work.
- [x] Reject name/status-field inference where no binding exists.
- [x] Define `CommittedTransition` component, entity, machine, transition,
  source/target, trigger, operation, commit, correlation, trace, and
  occurrence identities.
- [x] Stage/publish the envelope only after UnitOfWork commit with stable
  idempotency/replay identity.
- [x] Prove rejected, non-matching, failed, canceled, and rolled-back
  transitions emit no successful envelope.
- [x] Add commit-order, duplicate, replay, retry, and rollback specifications.

Evidence:
- Step commits `43a9c98b`, `d2707fb2`, `d8f8429c`, and `1d6f75dd` establish
  the typed binding, committed envelope, replay identity, and generated
  runtime integration.
- `EventEngineLaneSemanticsSpec`, `EventStoreBaselineSpec`,
  `PlannedTransitionValidationHookSpec`, `UnitOfWorkStateMachineHookSpec`,
  `UnitOfWorkPostCommitConsequenceSpec`, and
  `ComponentFactoryStateMachineBootstrapSpec` are the executable evidence.

## SMR-07: Failure Observability and Compatibility

Stage Status:
- Current status: DONE
- Owner: SimpleModeling error, CNCF observability, HTTP, shell/CLI, and
  compatibility maintainers
- Entry rule: SMR-06 is DONE.
- Completion rule: Every outcome is structurally observable and legacy
  behavior is explicit without leaking state or expression values.

- [x] Define closed outcomes and safe facets for source, no-match, ambiguity,
  guard, Phase-63.1-owned local-action, target, persistence, and rollback
  failures. Do not add Workflow Action/Provider/Continuation outcomes here.
- [x] Preserve interruption/fatal priority and one failure-observability path
  after domain rollback.
- [x] Keep raw state, payload, credentials, secrets, expression source, and
  evaluated values out of public diagnostics.
- [x] Define DetailCode, Record, JSON, Help/meta, CallTree, metric, and audit
  projections with legacy generated/raw-expression admission.
- [x] Add non-leakage, transport-boundary, rollback-visibility, and
  compatibility Executable Specifications.

Evidence:
- Step commits `d3d9fe66`, `2e5fb6ff`, and `1d6f75dd` establish the closed
  failure taxonomy and post-rollback safe lifecycle records.
- `TransitionLifecycleEvent`, `PlannedTransitionValidationHookSpec`,
  `UnitOfWorkStateMachineHookSpec`, and `UnitOfWorkPostCommitConsequenceSpec`
  provide the executable failure, rollback, interruption, and no-success-event
  coverage.

## SMR-08: Cross-Repository Acceptance and Promotion

Stage Status:
- Current status: DONE (forced release)
- Owner: Cozy, SimpleModeler, SimpleModeling, CNCF, sample, and documentation
  maintainers
- Entry rule: SMR-07 is DONE.
- Completion rule: The real generated/runtime path has focused acceptance.
  The aggregate full-suite, fresh full-review, and further documentation
  promotion requirements are explicitly deferred in the force-release audit.

- [x] Add generated `SalesOrder`/`SalesStatus` positive and negative
  StateMachine scenarios, including initial/final and one-level
  composite/named shallow-history behavior.
- [x] Prove the focused terminal-save route, persisted FINAL state,
  post-commit success envelope, replay identity, and invalid saved-reversal
  rejection without an additional envelope.
- [x] Exclude Composite/Workflow state derivation, WorkflowInstance persistence,
  `ExecProgram` interpretation, Provider resolution, Continuation/resume, and
  Skill projection from this acceptance. Verify only that their future input,
  `CommittedTransition`, is published after commit with the required identity
  and correlation evidence.
- [x] Prove the exact CML -> Cozy -> SimpleModeler -> generated provider ->
  ComponentFactory -> UnitOfWork -> persisted state -> `CommittedTransition`
  path for the focused `SalesOrder` terminal-save scenario, without manual
  provider substitution.
- [x] Run the affected focused suites. The aggregate repository full suite is
  deferred by direct user-authorized forced release.
- [x] Preserve the existing promoted boundaries and defer additional
  documentation promotion and Development Candidate reconciliation under the
  same forced-release exception record.

Evidence:
- The Cozy `sales-order-committed-transition-runtime` fixture supplies INIT,
  typed FINAL, named shallow history, PowerType values, generated provider,
  persistence, replay, and negative mutation coverage.
- CNCF focused receipt `P632-E3-CNCF-SAVE-FOCUSED-020` passed the
  `UnitOfWorkStateMachineHookSpec` and
  `CollectionStateMachinePlannerProviderSpec` on the saved-record propagation
  repair tree. Its verified receipt SHA-256 is
  `74f66f3fff8bf1af569e30450ac36251ddedaf3b2ff93cc434f292faafa001b1`.
- The required current CNCF SNAPSHOT refresh
  `P632-E3-CNCF-REFRESH-021` passed before downstream validation; verified
  receipt SHA-256 is
  `6c6c6670b88f52991c8bb9cc373557dbed6fe674e3e2c38ca47616a8c3df6042`.
- The exact generated Cozy runtime `P632-E3-COZY-SCRIPTED-024` passed after
  proving the explicit terminal `saveSalesOrder` route and rejection of an
  invalid saved reversal without an extra committed-transition envelope. Its
  verified receipt SHA-256 is
  `c91d046c4f5292ff0fa3e6891e86ddc72ac78ac8185bee51afc363a73983095d`.
- A focused re-review accepted the final repair delta. The aggregate final
  suite, a replacement full review, and further documentation promotion are
  deferred under force-release exceptions
  `FORCE-AGGREGATE-ENTRY-COMPATIBILITY`,
  `FORCE-AGGREGATE-FULL-SUITE-DEFERRED`,
  `FORCE-DOCUMENT-PROMOTION-DEFERRED`, and
  `FORCE-PHASE-FULL-REVIEW-DEFERRED`.
