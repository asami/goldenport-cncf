# Phase 90 Checklist - Candidate-Admission Runtime Support

status=planned
phase=[Phase 90](phase-90.md)

## Layer boundary
- [ ] CAM-90-01: Freeze that Phase 77 owns Continuation/runtime mechanics and Phase 90 owns optional Admission semantics.
- [ ] CAM-90-02: Prove Continuation carries no required Candidate/AdmissionGap semantics and non-CAM StateMachines remain unchanged.

## Generic model
- [ ] CAM-90-03: Define typed CandidateRef/CandidateSnapshot reference, AdmissionSubmission, AdmissionRequirement, AdmissionEvaluation, AdmissionGap and AdmissionResult.
- [ ] CAM-90-04: Keep application candidate/evidence payloads typed and application-owned.
- [ ] CAM-90-05: Define target snapshot/revision and provenance correlation needed for evidence freshness/coverage without defining domain review scopes.

## Runtime integration
- [ ] CAM-90-06: Integrate admission with existing StateMachine guards/actions; do not create a second transition engine.
- [ ] CAM-90-07: Map missing semantic requirements to admitted Required SPI Actions that may suspend through Phase 77 Continuation.
- [ ] CAM-90-08: Route deterministic requirements through normal Providers and authority gaps through Decision boundaries.
- [ ] CAM-90-09: Re-evaluate admission after gap completion without requiring candidate resubmission.
- [ ] CAM-90-10: Prevent semantic workers/results from selecting transitions, admission success, or physical commitment directly.

## Evidence acceptance
- [ ] CAM-90-11: Reuse compatible fresh supplied evidence without duplicate semantic work.
- [ ] CAM-90-12: Reject or gap stale, incompatible, insufficiently covered or missing evidence fail closed.
- [ ] CAM-90-13: Preserve typed evidence payload/provenance/snapshot identity across suspend/resume.

## Reference consumer
- [ ] CAM-90-14: Exercise sm-workflow-style Step closure fixture with proactive scoped evidence, broader required evidence, Continuation-backed semantic work, re-evaluation and admission.
- [ ] CAM-90-15: Prove admission does not itself perform Git/application commitment.
- [ ] CAM-90-16: Freeze sm-workflow consumer handoff and Cozy follow-up requirements.

## Validation
- [ ] CAM-90-17: Pass positive, stale, insufficient, incompatible, replay/restart and non-CAM regression fixtures.
- [ ] CAM-90-18: Complete focused independent review and record exact Phase 77 compatibility evidence.
