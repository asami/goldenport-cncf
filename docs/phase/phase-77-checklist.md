# Phase 77 Checklist - First-Class CML WORKFLOW ABI Admission and Progression Contract

status=planned
phase=[Phase 77](phase-77.md)

## CWF-77-01: Generated ABI Admission

Stage Status:
- Current status: OPEN
- Owner: CNCF generated-contract/runtime owner
- Update rule: Close only after supported Cozy Workflow ABI versions,
  compatibility policy, and structured fail-closed diagnostics are frozen and
  executable.

- [ ] Inventory the exact Cozy Phase 62 generated Workflow ABI, fixture, and
      source-identity contract.
- [ ] Define supported ABI version admission and incompatible/unknown required
      semantics failure behavior.
- [ ] Preserve Workflow, Composite StateMachine, transition/action provenance,
      and typed Operation identity without CML parsing or name inference.

## CWF-77-02: ComponentFactory Discovery

Stage Status:
- Current status: OPEN
- Owner: CNCF ComponentFactory owner
- Update rule: Close only after ComponentFactory discovers admitted generated
  Workflow definitions with explicit version/source diagnostics.

- [ ] Bind generated WorkflowDefinition bootstrap metadata to ComponentFactory.
- [ ] Reject absent, duplicate, or incompatible generated definitions rather
      than accepting handwritten canonical runtime definitions.
- [ ] Preserve normal component capability/admission boundaries.

## CWF-77-03: Independent WorkflowInstance Persistence

Stage Status:
- Current status: OPEN
- Owner: CNCF Workflow persistence/runtime owner
- Update rule: Close only after the generated Workflow ABI is bound to Phase
  64's WorkflowInstance store contract, which separates process persistence
  from entity StateMachine persistence and defines durable, idempotent
  committed-transition correlation.

- [ ] Consume and version-bind Phase 64's WorkflowInstance persistence SPI
      with independent instance and definition identity/version, revision,
      lifecycle state, boundary, append-only history, and correlation/causation
      references.
- [ ] Prohibit entity fields, entity StateMachine records, and shared-table
      ownership from becoming the authoritative WorkflowInstance store.
- [ ] Define explicitly configured same-store transaction behavior and
      idempotent/recoverable cross-store delivery from `CommittedTransition`
      for the generated Workflow ABI.
- [ ] Require a consumer binding such as Textus `sm-workflow` to supply its
      datastore, migration, retention, and lease policy without changing the
      generic persistence contract.

## CWF-77-04: Deterministic Progression Evaluation

Stage Status:
- Current status: OPEN
- Owner: CNCF Workflow runtime owner
- Update rule: Close only after the evaluator returns one deterministic
  automatic result, explicit semantic boundary, terminal result, or structured
  failure for every admitted definition/input.

- [ ] Consume generated automatic versus semantic-boundary metadata directly.
- [ ] Return typed Work Order, Decision, and Wait boundary categories without
      crossing them automatically.
- [ ] Detect ambiguity, cycle/bound overflow, unavailable persisted input, and
      unsupported automatic behavior as structured failures.
- [ ] Keep persistence, retry scheduling, and client-turn policy outside the
      evaluator.

## CWF-77-05: Typed Action / Operation Integration

Stage Status:
- Current status: OPEN
- Owner: CNCF UnitOfWork / Workflow runtime owners
- Update rule: Close only after admitted declared Operations/actions use the
  existing typed execution path and invalid automatic execution is rejected.

- [ ] Reuse `ExecProgram[UnitOfWorkOp, A]` and the Phase 64.2 planner contract.
- [ ] Preserve identity, idempotency, authorization, provenance, and normal
      UnitOfWork boundaries for declared Operations/actions.
- [ ] Prohibit a Workflow-specific Action algebra, opaque callback, or raw
      command execution surface.

## CWF-77-06: CML-First Acceptance and Textus Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 77 coordinating with Cozy Phase 62 and Textus `sm-workflow`
- Update rule: Close only after a real Cozy source/generated fixture reaches
  the admitted CNCF path with reproducible evidence and an exact consumer
  handoff. Textus runtime acceptance remains external.

- [ ] Exercise a Cozy-generated Workflow fixture through ComponentFactory and
      the deterministic progression evaluator.
- [ ] Prove that the WorkflowInstance record remains independently persisted
      from the entity StateMachine record across create, replay, restart, and
      recovery cases.
- [ ] Prove automatic progression is evaluated without CML reparsing and that
      semantic boundaries are returned rather than crossed.
- [ ] Record exact Cozy source, generated ABI, CNCF revision, and `sm-workflow`
      consumer contract.
- [ ] Complete focused validation, independent review, final validation, and
      release closure without claiming Textus SQLite/skill completion.
