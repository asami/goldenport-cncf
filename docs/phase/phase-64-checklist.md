# Phase 64 Checklist - CML Workflow Runtime Integration

status=planned
phase=[Phase 64 - CML Workflow Runtime Integration](phase-64.md)

This checklist is the authoritative Phase 64 state ledger after Phase 64
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 63 closes.

The acceptance path must preserve continuity with Phase 63:

```text
CML -> normalize -> generate -> ComponentFactory -> CNCF runtime
```

A hand-written WorkflowDefinition or manually injected runtime registration is
not sufficient end-to-end evidence.

## SWF-01: CML/Runtime Inventory and Responsibility Freeze

- [ ] Inventory the accepted CML Workflow grammar/model and its existing Cozy
  parser/modeler behavior.
- [ ] Inventory Phase 14 WorkflowDefinition, registration, trigger,
  WorkflowInstance, history, next-action, Job linkage, retry, and projection.
- [ ] Inventory Phase 63 CML-to-runtime pipeline and `CommittedTransition`.
- [ ] Freeze CML as owner of Workflow declaration semantics and CNCF as owner of
  Workflow execution semantics.
- [ ] Freeze StateMachine as local domain-transition owner, Workflow as
  cross-Operation progression owner, and JobEngine as async execution owner.
- [ ] Identify every runtime-only Workflow semantic that is not represented by
  CML and classify it as compatibility, implementation detail, candidate CML
  addition, or removal.
- [ ] Register failing-first cross-repository specifications.

## SWF-02: Canonical CML Workflow Runtime Model

- [ ] Freeze stable Workflow definition identity and version.
- [ ] Freeze trigger/entry, step, condition, Operation reference, and terminal
  outcome identities represented by CML.
- [ ] Freeze explicit StateMachine/transition-to-Workflow binding.
- [ ] Reuse the closed typed predicate model where CML Workflow conditions need
  it; do not introduce a second expression language.
- [ ] Reject unknown model references and unsupported constructs during
  normalization/generation.
- [ ] Ensure no runtime behavior depends on coincidental name matching.
- [ ] Preserve source-location information for diagnostics.

## SWF-03: Committed-Transition and Trigger Contract

- [ ] Adopt Phase 63 `CommittedTransition` without redefining its semantic
  identity.
- [ ] Keep transition definition identity distinct from transition occurrence
  identity.
- [ ] Map the CML-declared binding to one typed Workflow entry.
- [ ] Define stable Workflow trigger/step-occurrence correlation and duplicate
  delivery semantics.
- [ ] Define commit-before-delivery and no-delivery-on-rollback behavior.
- [ ] Define other Workflow trigger kinds only when explicitly admitted by the
  accepted CML Workflow model.
- [ ] Use precise terms for duplicate delivery/recovery; do not imply
  Temporal-style deterministic code replay unless such a contract is actually
  introduced.

## SWF-04: SimpleModeler Generation, ABI, and Bootstrap

- [ ] Generate typed Workflow definitions directly from CML.
- [ ] Generate explicit trigger, StateMachine/transition, step, condition,
  Operation, terminal-outcome, and source identities required by runtime.
- [ ] Preserve deterministic ordering and stable ABI/version metadata.
- [ ] Generate typed predicates/bindings rather than required raw runtime
  strings.
- [ ] Make generated Workflow definitions available through the normal
  generated component/provider surface.
- [ ] Make ComponentFactory automatically discover/register generated Workflow
  definitions.
- [ ] Reject unknown required ABI versions or unrepresentable CML semantics.
- [ ] Add deterministic source, metadata, ABI, compilation, bootstrap, Record,
  and JSON specifications.

## SWF-05: CNCF Workflow Execution

- [ ] Consume only admitted generated Workflow definitions for the canonical
  path.
- [ ] Load or create the independent WorkflowInstance.
- [ ] Evaluate the CML-declared trigger/step condition deterministically.
- [ ] Select the CML-declared next Operation or terminal outcome.
- [ ] Delegate through generic CNCF invocation and normal authorization,
  idempotency, and correlation boundaries.
- [ ] Delegate asynchronous execution to JobEngine and record Job linkage.
- [ ] Prohibit direct Workflow writes to Entity/Aggregate domain state.
- [ ] Ensure any next domain transition returns through Phase 63 StateMachine
  enforcement.
- [ ] Do not add runtime-only Workflow language features ahead of accepted CML
  semantics.

## SWF-06: WorkflowInstance Persistence and Recovery

- [ ] Define WorkflowInstance state independently of domain StateMachine state.
- [ ] Persist definition/version, current step/progression, consumed trigger
  occurrences, step occurrences, selected Operations, Job ids, history,
  correlation, and terminal outcome as required.
- [ ] Define progression serialization/concurrency.
- [ ] Freeze the crash window between progression decision, Operation/Job
  submission, and history persistence.
- [ ] Give each logical step execution a stable occurrence/idempotency identity
  so recovery cannot duplicate logical Operation/Job submission.
- [ ] Define retry, duplicate delivery, resume/recovery, retention, and version
  migration semantics.
- [ ] Keep WorkflowInstance, domain Entity, and Job as separate authorities.

## SWF-07: Observability, Security, and Compatibility

- [ ] Correlate CML source/model identity, generated Workflow definition,
  trigger occurrence, WorkflowInstance, step occurrence, Operation, Job,
  StateMachine transition, trace/span, subject, and tenant.
- [ ] Project domain state and WorkflowInstance state separately.
- [ ] Preserve normal authorization for delegated Operations without privilege
  amplification.
- [ ] Keep payloads, credentials, secrets, and predicate values out of public
  diagnostics.
- [ ] Map Phase 14 raw-event/status-field behavior through explicit adapters or
  reject it; never silently reinterpret it as canonical CML Workflow behavior.
- [ ] Keep specialist workflow-engine integration explicit.

## SWF-08: CML-First Cross-Repository Acceptance and Promotion

- [ ] Define `SalesOrder`, `SalesStatus`, and `SalesOrderWorkflow` in real CML.
- [ ] Start acceptance from the CML source, not a hand-written runtime fixture.
- [ ] Parse and normalize the Workflow through Cozy.
- [ ] Generate the typed Workflow definition/metadata through SimpleModeler.
- [ ] Bootstrap it automatically through ComponentFactory.
- [ ] Prove a committed SalesStatus transition starts/advances the explicitly
  bound CML WorkflowInstance.
- [ ] Prove Workflow delegates the CML-declared next Operation without direct
  domain mutation.
- [ ] Prove the next Operation returns to Phase 63 StateMachine enforcement.
- [ ] Prove rollback/no-match/failure does not advance Workflow.
- [ ] Prove duplicate delivery, crash recovery, concurrency, Job linkage,
  authorization, observability, and non-leakage behavior.
- [ ] Validate the generated CAR/sample path where applicable.
- [ ] Promote verified contracts from notes into design/spec documentation.
- [ ] Record exact Cozy, SimpleModeler, and CNCF revisions used by acceptance.
