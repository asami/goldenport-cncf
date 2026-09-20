# Phase 64 Checklist - Minimal Composite StateMachine / Workflow Foundation

status=completed
outcome=success
release_disposition=forced
assurance=exceptions-recorded
closure_exception=fresh-full-suite-not-refreshed
phase=[Phase 64](phase-64.md)

Phase 64 closes only the minimum Composite StateMachine / Workflow foundation required by Phase 77 and sm-workflow Phase 1. It consumes the completed Phase 63.1 local atomic-execution contract and starts only after Phase 63.2 closes.

SWF-01 through SWF-06 are accepted. SWF-07 reuses the already closed
[Cozy Phase 62.3 producer handoff](https://github.com/asami/cozy/blob/main/docs/phase/phase-62.3.md).
No new Cozy Phase is an entry condition, and accepted Steps do not require a
replacement Phase 64 goal.

The acceptance path consumes, but never reinterprets or executes ahead of, the Phase 63.2 post-commit handoff:

```text
CML -> normalize -> generate -> Composite/Workflow semantic contract
Phase 63.2 CommittedTransition -> composite derivation / WorkflowInstance contract
Phase 77 -> ABI admission -> ComponentFactory -> Provider/Continuation runtime
```

## SWF-01 Semantic inventory
- [x] Classify required concepts as StateMachine reuse, Composite-general, minimal Workflow-only, or runtime-policy.
- [x] Reject speculative advanced Workflow features from this Phase.

Stage Status:

- Current status: ACCEPTED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: completed Phase 63.1 local atomic-execution and Phase 63.2 committed-transition inputs are available.
- Completion rule: the exclusive four-way inventory is accepted with no advanced Workflow expansion.
- Update rule: preserve this accepted result until the ordinary Step 64-S1 commit.

Evidence: [Composite StateMachine / Workflow Semantic Inventory](../design/composite-workflow-semantic-inventory.md) records the sealed Step review disposition digest `566d9146076175e6fefb92809b6ef9548462d7d9d57da99f51c0074ebb61f34a`. Subsequent SWF-02–08 entries provide the Phase completion ledger.

## SWF-02 Composite identity and derivation
- [x] Freeze constituent role/identity and configuration schema.
- [x] Freeze deterministic composite-state derivation.
- [x] Freeze derived composite-transition identity and causal correlation.
- [x] Consume the Phase 63.2 `CommittedTransition` only as the post-commit constituent-transition input.

Stage Status:

- Current status: ACCEPTED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: entered only after completed Phase 63.2 post-commit input is available.
- Completion rule: role/identity, configuration, deterministic derivation, and post-commit causal correlation are accepted.
- Update rule: preserve this accepted result until the ordinary Step 64-S2 commit.

Evidence: [Composite StateMachine Identity and Derivation](../design/composite-state-machine-identity-and-derivation.md) records the sealed Step re-review disposition digest `f034e3593ad328b6be971a6494a71e4db354c929143748f0cc192ec308abac09`. Subsequent SWF-03–08 entries provide the Phase completion ledger.

## SWF-03 Admission
- [x] Consume generated CML contracts without reparsing CML.
- [x] Fail closed for ambiguous/unsupported composite configuration.
- [x] Preserve source/model identity.
- [x] Preserve the typed generated semantic contract without generated API/SPI admission or `ComponentFactory` bootstrap; those are Phase 77 concerns.

Stage Status:

- Current status: ACCEPTED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: enter only through the sealed producer-generated typed contract.
- Completion rule: consume CML without reparsing, fail-closed admission, preserve source/model identity, and preserve the typed semantic contract without generated API/SPI admission or `ComponentFactory` bootstrap.
- Update rule: preserve this accepted result until the ordinary Step 64-S3 commit.

Evidence: [Producer-Generated Rule Admission Contract](../design/composite-state-machine-generated-rule-admission.md) records the sealed focused re-review disposition digest `86bc82a1b27dc83ce2407b1b5ffb2f3c8184ac2eaa55a5d6aa833370d43c6fb4`. Subsequent SWF-04–08 entries provide the Phase completion ledger.

## SWF-04 Action composition
- [x] Preserve constituent/composite action ordering and provenance.
- [x] Use the shared typed executable-program boundary.
- [x] Consume the completed Phase 63.1 local atomic-execution contract without reopening or duplicating it; delegate incremental planner/interpreter/testability to 64.2.

Stage Status:

- Current status: ACCEPTED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: enter after the sealed typed action contract and the Phase 63.1/63.2 causal boundary are available.
- Completion rule: deterministic constituent/composite action ordering and provenance use the shared typed `ExecProgram` boundary, consume the Phase 63.1 contract, and leave planner/interpreter work to 64.2.
- Update rule: preserve this accepted result until the ordinary Step 64-S4 commit.

Evidence: [Composite StateMachine Action Composition](../design/composite-state-machine-action-composition.md) records the sealed Step review disposition digest `485d5190a7259944c024f28954376920c9feb8a068704ace60f2972815666cee`. Subsequent SWF-05–08 entries provide the Phase completion ledger.

## SWF-05 Minimal Workflow specialization
- [x] Freeze semantic WorkflowDefinitionIdentity / WorkflowInstanceIdentity.
- [x] Freeze lifecycle/current progression and correlation/causation semantics needed by Phase 77.
- [x] Leave independently durable revision/history/store/suspension semantics to Phase 77.
- [x] Add no Workflow-only concept that can be represented as Composite StateMachine semantics.

Stage Status:

- Current status: ACCEPTED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: enter after the sealed Workflow-profile identity and the Phase 63.2/SWF-02 causal boundary are available.
- Completion rule: pinned definition/instance identity, three-state lifecycle/current progression plus correlation/causation, durable revision/history/store/suspension remaining Phase 77, and no Workflow-only concept representable as Composite StateMachine semantics.
- Update rule: preserve this accepted result until the ordinary Step 64-S5 commit.

Evidence: [Minimal Workflow specialization](../design/minimal-workflow-specialization.md) records the sealed Step review disposition digest `2165bdb3f9c71ad3147e623e70570657be36e6022aa87868788713e728e52e57`. Subsequent SWF-06–08 entries provide the Phase completion ledger.

## SWF-06 Generated semantic-contract handoff
- [x] Freeze a CML-first generated Composite StateMachine/Workflow semantic-contract handoff without CML reparsing.
- [x] Leave generated API/SPI admission and `ComponentFactory` bootstrap to Phase 77.
- [x] Do not accept handwritten definitions as canonical end-to-end evidence.
- [x] Distinguish the released Workflow subset needed by Phase 77 from the
      complete generalized Composite artifact.
- [x] Assign future generalized artifact production to Cozy Phase 66 and its
      CNCF admission/runtime projection to Phase 87, without blocking the
      `sm-workflow` critical path.

Stage Status:

- Current status: ACCEPTED DESIGN; GENERALIZED ARTIFACT DEFERRED.
- Owner: CNCF semantic-boundary owner under Phase 64 closure authority.
- Entry rule: enter after the sealed CML-first typed handoff is available.
- Completion rule: preserve the CML-first design, accept the released Workflow
  subset required by Phase 77, leave its API/SPI admission and
  `ComponentFactory` bootstrap to Phase 77, and route the complete generalized
  artifact through Cozy Phase 66 and CNCF Phase 87 without handwritten
  substitution.
- Update rule: preserve this accepted result until the ordinary Step 64-S6 commit.

Evidence: [Composite Workflow Generated Semantic Handoff](../design/composite-workflow-generated-semantic-handoff.md) records the sealed Step review disposition digest `b177d55228453e4c84e3a702511036edd37ae337afa6d41d4cf2a30c5375cc5a`. SWF-07 consumes the closed Cozy Phase 62.3 fixture and SWF-08 records the bounded Phase 77 handoff.

## SWF-07 Real fixture
- [x] Pin the released Cozy Phase 62.3 CML fixture, generated Workflow ABI,
      schema/version, and producer validation evidence.
- [x] Prove the minimum typed action/progression boundary required for Phase 77
      using that fixture, without a new Cozy producer implementation.
- [x] Prove that an entity-triggered entry accepts only the Phase 63.2
      `CommittedTransition`; do not force that trigger onto the explicitly
      started Skill-driven Phase 62.3 fixture.
- [x] Record that the pinned Workflow ABI omits the full generalized Composite
      configuration, derivation, causal/action provenance, version pins, and
      producer diagnostics required by the broad SWF-06 design.
- [x] Confirm that those omissions do not change the Phase 77 / `sm-workflow`
      minimum and defer them to Cozy Phase 66 and CNCF Phase 87.

Stage Status:

- Current status: ACCEPTED FOR PHASE 77 / `sm-workflow` MINIMUM.
- Owner: CNCF Phase 64 for the bounded fixture composition and runtime proof;
  Cozy Phase 62.3 remains the immutable producer evidence owner.
- Entry rule: consume the accepted Cozy Phase 62.3 revision, generated ABI
  schema/version, source/fixture identity, and validation evidence already
  recorded by that closure.
- Resume rule: continue directly at SWF-07; do not reopen SWF-01 through SWF-06.
- Scope rule: add only the smallest CNCF-side fixture adapter or executable
  specification needed to connect the released producer fixture to the
  Phase 63.2/64 boundary. Do not request generalized Cozy metadata or static
  analysis unless a concrete ABI incompatibility is demonstrated.
- Rejection rule: handwritten replacement definitions, CML reparsing, and name
  inference do not satisfy the dependency.

## SWF-08 Handoff and deferral
- [x] Freeze exact Phase 77 consumer handoff.
- [x] Record the exact accepted Cozy Phase 62.3 producer handoff consumed by
      SWF-07.
- [x] Record that generated API/SPI admission, ActionExecution, Provider runtime, Continuation/resume, the minimum typed Workflow protocol and schema-versioned fail-closed Skill/Codex JSON encoding, and the minimum Skill projection required by the sm-workflow vertical slice are Phase 77 concerns.
- [x] Record broad Start/API expansion, rich Presentation/UI, broad reasoning vocabulary, parent/child Workflow composition, and orchestration/REST/MCP surfaces as later-phase concerns.
- [x] Record Retry/Timeout and other runtime-control features as later phases.
- [x] Record 2PC/compensation/recovery as Phase 85.
- [x] Record Workflow-to-Workflow orchestration and advanced integration as later work.
- [x] If Phase 63.2 closure reveals hierarchy/history runtime gaps, require the dedicated follow-up before relying on those semantics.
- [x] Record Cozy Phase 66 as the future generalized producer and CNCF Phase 87
      as its admission/runtime-projection consumer; neither blocks Phase 77 or
      `sm-workflow` Phase 1.

Evidence: [Released producer fixture handoff](../design/composite-workflow-released-producer-fixture-handoff.md) pins the accepted producer identity, ABI/schema, fixture and validation evidence; binds it to the existing Phase 63.2 `CommittedTransition` boundary without miscasting the fixture's explicit Skill-driven start; and records the Phase 77 consumer handoff and later-phase deferrals. This completes Phase 64's released-Workflow-subset closure; the generalized artifact remains owned by future Cozy Phase 66 and CNCF Phase 87 work.

## Closure

Phase 64 is complete when the generated Composite StateMachine / minimal Workflow semantics and real fixture are sufficient for Phase 64.2's incremental execution/test foundation and the Phase 77 handoff, without reopening Phase 63.1 or implementing advanced operational Workflow facilities.

Closure result: success. The user explicitly selected forced release on
2026-09-21 because the workflow could not provide a fresh final full-suite
receipt without restarting heavyweight validation. The exception waives only
that fresh receipt: accepted Step evidence, `P64-FULL-REVIEW-001`, the pinned
Cozy Phase 62.3 fixture evidence, and the final documentation integrity checks
remain preserved. No new Scala or test bytes are included in this closure.
