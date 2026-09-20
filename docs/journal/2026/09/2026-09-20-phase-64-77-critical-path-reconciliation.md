# Phase 64 / 77 sm-workflow Critical-Path Reconciliation

Date: 2026-09-20
Status: reconciliation record
Source: [Phase 64 / 77 Review Handoff](2026-09-20-phase-64-77-sm-workflow-critical-path-review-handoff.md)

## Recorded outcome

The current Phase/checklist ledgers reconcile the handoff without expanding the
`sm-workflow` Phase 1 critical path.

```text
Phase 63.1 / 63.2 complete
  -> Phase 64 Composite/Workflow semantics
  -> Phase 64.2 ExecProgram planning/interpreters/acceptance
  -> Phase 77 API/SPI runtime + minimum typed protocol
  -> sm-workflow Phase 1 executable specifications
```

Phase 64.1 is retained as superseded planning history. Its local atomic
UnitOfWork scope is already completed by Phase 63.1 and is consumed directly
by Phase 64.2; neither Phase is reopened.

Phase 77 retains the smallest typed Workflow protocol needed by the consumer:

- `WorkflowStartRequest` / `WorkflowStartResult` and `WorkflowHandle`;
- closed `Continuation = WORK_ORDER | DECISION | WAIT | TERMINAL`;
- typed WorkOrder/WorkResult, Completion/Evidence, and application-owned
  payloads;
- `ExecutionRequirement` with small typed capability/risk requirements and
  `ROUTINE`, `STANDARD`, `DEEP`, and `CRITICAL`;
- typed `ExecutionEvidence` for Skill/Host-dispatched WorkResult completion;
- common `Presentation` with title/current situation and optional
  summary/next action/reason/progress for console visibility only; and
- schema-versioned, fail-closed Skill/Codex JSON encoding.

Concrete worker-profile mapping remains Host/Skill policy and evidence, not
Workflow progression semantics. Broad Start/API expansion, rich UI,
additional reasoning vocabulary, parent/child composition, orchestration, and
REST/MCP/UI transport remain later work.

The exact common-contract decision, including the `ExecutionEvidence` and
`WorkflowInteraction` boundaries, is recorded in
[Phase 77 Common Contract Reconciliation Decision](2026-09-20-phase-77-common-contract-reconciliation-decision.md).

The authoritative current work scope is recorded in
[Phase 64](../../../phase/phase-64.md), [Phase 64.2](../../../phase/phase-64.2.md),
[Phase 77](../../../phase/phase-77.md), and their checklists.
