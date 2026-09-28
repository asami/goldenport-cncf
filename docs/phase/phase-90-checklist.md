# Phase 90 Checklist - Candidate-Admission Runtime Support

status=closed
phase=[Phase 90](phase-90.md)

## Layer boundary
- [x] CAM-90-01: Freeze that Phase 77 owns Continuation/runtime mechanics and Phase 90 owns optional Admission semantics.
- [x] CAM-90-02: Prove Continuation carries no required Candidate/AdmissionGap semantics and non-CAM StateMachines remain unchanged.
  - The Step fixture uses the unchanged Continuation shape; focused `ContinuationRuntimeSpec`, `TransitionSelectorSpec`, and `StateMachineRequiredOperationActionSpec` remain green.

## Generic model
- [x] CAM-90-03: Define typed CandidateRef/CandidateSnapshot reference, AdmissionSubmission, AdmissionRequirement, AdmissionEvaluation, AdmissionGap and AdmissionResult.
- [x] CAM-90-04: Keep application candidate/evidence payloads typed and application-owned.
- [x] CAM-90-05: Define target snapshot/revision and provenance correlation needed for evidence freshness/coverage without defining domain review scopes.

## Runtime integration
- [x] CAM-90-06: Integrate admission with existing StateMachine guards/actions; do not create a second transition engine.
- [x] CAM-90-07: Map missing semantic requirements to admitted Required SPI Actions that may suspend through Phase 77 Continuation.
  - A synthetic Step closure model admitted through the real Cozy Candidate-Admission ABI receiver routes from an Active instance through the existing UnitOfWork-bound Program Provider into Phase 77 suspension/restart.
- [x] CAM-90-08: Route deterministic requirements through normal Providers and authority gaps through Decision boundaries.
  - Focused fixture executes a deterministic Gap through the existing UnitOfWork/explicit Provider binding, rejects an unbound Provider, and keeps the authority Gap as a Workflow Decision that cannot be submitted as a WorkOrder result. Application issuance/durability is not attributed to CAM.
- [x] CAM-90-09: Re-evaluate admission after gap completion without requiring candidate resubmission.
- [x] CAM-90-10: Prevent semantic workers/results from selecting transitions, admission success, or physical commitment directly.
  - `CandidateAdmissionGuard` re-evaluates application requirements and typed evidence; the event cannot supply an admitted result or transition target. `CandidateAdmissionGapRouting` only admits declared Required SPI targets; `TransitionSelector` retains transition selection. The Step fixture proves an external result and evidence write do not trigger the commit transition by themselves.

## Evidence acceptance
- [x] CAM-90-11: Reuse compatible fresh supplied evidence without duplicate semantic work.
- [x] CAM-90-12: Reject or gap stale, incompatible, insufficiently covered or missing evidence fail closed.
- [x] CAM-90-13: Preserve typed evidence payload/provenance/snapshot identity across suspend/resume.
  - A separate, codec-bound local evidence store reopens after the Continuation fixture resumes; the original Submission is re-evaluated from recovered evidence. This is a loose persistence boundary, not a shared transaction.

## Reference consumer
- [x] CAM-90-14: Exercise sm-workflow-style Step closure fixture with proactive scoped evidence, broader required evidence, Continuation-backed semantic work, re-evaluation and admission.
  - This is a CNCF reference fixture with Step naming derived from an admitted Cozy ABI shape, not a real sm-workflow CML generation receipt.
- [x] CAM-90-15: Prove admission does not itself perform Git/application commitment.
  - Model evaluation, guard, routing and evidence persistence have no Git/application commit operation. The Step fixture only observes the existing selector's `application-commit` target; it never executes that target.
- [x] CAM-90-16: Freeze sm-workflow consumer handoff and Cozy follow-up requirements.
  - The handoff in `phase-90.md` fixes the admitted Cozy ABI receiver, sm-workflow-owned scope/policy/Decision/commit duties, synthetic-fixture limit, concrete-Cozy-gap reporting rule, and the separate Phase 94 transaction boundary.

## Validation
- [x] CAM-90-17: Pass positive, stale, insufficient, incompatible, replay/restart and non-CAM regression fixtures.
  - Final full-suite receipt `P90-FULL-TEST-01A0D871` (2026-09-25): 515 suites completed, 3,840 tests succeeded, 0 failed, 0 aborted. The suite includes the CAM model, guard, routing, local evidence persistence, restart/one-shot resume, and unchanged non-CAM regression specs.
- [x] CAM-90-18: Complete focused independent review and record exact Phase 77 compatibility evidence.
  - Separate read-only closure review of the Phase 90 source/spec/documentation delta found no Current Boundary Blocker. `CandidateAdmissionContinuationSpec` exercises the unchanged `Continuation`, `PersistentContinuationRuntime`, and UnitOfWork post-commit suspension/restart/resume path; the full suite also passes the existing `ContinuationRuntimeSpec`, `TransitionSelectorSpec`, and `StateMachineRequiredOperationActionSpec`.
