# Phase 102 Checklist: Integrated Admission Management

status=planned
phase=[Phase 102](phase-102.md)

## Contract
- [ ] IAM-102-01: Freeze Phase 90 Candidate-Admission evaluation vs Phase 102 Admission Management projection.
- [ ] IAM-102-02: Define minimal provider-neutral AdmissionItem identity, candidate/provider/evidence/authority references, status, revision and available-action contracts.
- [ ] IAM-102-03: Define stale-state, idempotency, authorization and structured failure behavior.

## Provider SPI
- [ ] IAM-102-04: Define provider query/read/action SPI without GitHub, Slack, Dot or OpenClaw vocabulary in CNCF core.
- [ ] IAM-102-05: Implement deterministic/in-memory provider fixtures and prove list/read/action/status transitions.
- [ ] IAM-102-06: Prove external authority can be referenced without copying full diff/review/conversation content.

## Consumers and adapters
- [ ] IAM-102-07: Map sm-workflow Phase-90-backed admission projection without changing Workflow authority.
- [ ] IAM-102-08: Freeze GitHub Pull Request adapter contract and external-owner handoff with revision/stale-decision requirements.
- [ ] IAM-102-09: Freeze Textus Control Center unified Admission Inbox consumption contract.
- [ ] IAM-102-10: Define Slack/mobile/watch decision projection requirements without making interaction text authoritative.

## Lifecycle and closure
- [ ] IAM-102-11: Define lifecycle events/observability projection and provider reconciliation behavior.
- [ ] IAM-102-12: Add executable specifications proving provider interchange, stale action rejection, external-reference preservation and Phase 90 compatibility.
- [ ] IAM-102-13: Complete focused/full review and consumer handoff.

- [ ] IAM-102-14: Define Human-in-the-Loop as a standard Admission authority/routing case rather than a product/UI-specific Workflow primitive.
- [ ] IAM-102-15: Prove an Admission Gap requiring human judgment can suspend/resume through Phase 77 Continuation without exposing internal Workflow state/transition semantics to the external participant host.
- [ ] IAM-102-16: Prove the same abstract Workflow/Operation contract remains valid when an internal admission route changes between automatic, AI-assisted and human-required policies.
- [ ] IAM-102-17: Prove Slack/Web/mobile/Watch bindings are replaceable participant/presentation adapters and do not become Admission or Workflow authority.
