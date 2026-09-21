# Phase 90: Candidate-Admission Runtime Support

status=planned
planned_at=2026-09-21
depends_on=[Phase 77](phase-77.md)
consumer=sm-workflow Phase 1
design=[Candidate-Admission Model](../notes/candidate-admission-model.md)
checklist=[Phase 90 Checklist](phase-90-checklist.md)

## Purpose

Implement the generic Candidate-Admission Model (CAM) layer on top of the Phase 77 StateMachine/Workflow Continuation runtime.

Phase 77 remains the lower execution layer: ActionExecution, Completed/Suspended/Failed, durable Continuation, typed resume, Provider resolution, Workflow identity/revision and deterministic progression.

Phase 90 adds a distinct upper semantic/runtime layer for candidate submission and admission. Admission Gap is not a Continuation subtype and Continuation does not acquire Admission semantics.

~~~text
Application
  -> Candidate / Submission
  -> Admission Evaluation
       -> admitted
       -> Admission Gap
            -> required semantic Action
                 -> Phase 77 Suspended(Continuation)
            -> deterministic requirement
                 -> Phase 77/internal Provider
            -> authority requirement
                 -> Decision boundary
  -> admitted transition / application commitment action
~~~

## Layer boundary

Phase 77 owns State/Action/Transition execution, ActionExecution, durable Continuation/resume, ContextSnapshot, identity/revision/idempotency, generic typed Result/Evidence transport and Provider/runtime mechanics. It does not know Candidate, AdmissionRequirement or AdmissionGap.

Phase 90 owns generic CandidateRef/CandidateSnapshot reference, AdmissionSubmission, AdmissionRequirement, AdmissionEvidence binding, AdmissionEvaluation, AdmissionGap, AdmissionResult/admitted status, and evidence coverage/freshness hooks without application-specific scope vocabulary.

Application-specific review scope, Phase/Checklist, software-development closure rules and physical Git commit remain outside CNCF.

## Core rules

1. A semantic actor may construct a candidate proactively.
2. Submission is not transition authority.
3. Admission evaluates the candidate and available evidence fail closed.
4. Existing compatible fresh evidence can satisfy a requirement without forcing duplicate semantic work.
5. Missing semantic requirements select an admitted semantic Action; external execution may then suspend through Phase 77 Continuation.
6. Missing deterministic requirements execute through normal typed Providers.
7. Authority ambiguity becomes a Decision boundary rather than heuristic admission.
8. Semantic Result/Evidence cannot directly choose the next StateMachine state or physical commitment.
9. Admission and physical/application commitment remain distinct.
10. Applications own typed candidate/evidence payload semantics.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| CAM-90-01 | Freeze Phase 77/90 layer boundary and generic CAM terminology. | planned |
| CAM-90-02 | Define provider-neutral Candidate/Submission/Requirement/Evaluation/Gap/Result Value Objects. | planned |
| CAM-90-03 | Define typed Evidence binding, target snapshot/revision correlation and freshness/coverage extension hooks. | planned |
| CAM-90-04 | Integrate Admission Evaluation with StateMachine guards/actions without adding a parallel transition engine. | planned |
| CAM-90-05 | Map semantic Admission Gaps to normal Required SPI Actions that may suspend through Phase 77 Continuation. | planned |
| CAM-90-06 | Prove reuse of supplied fresh evidence and fail-closed stale/incompatible evidence behavior. | planned |
| CAM-90-07 | Prove deterministic and authority gaps use normal Provider/Decision boundaries rather than semantic AI by default. | planned |
| CAM-90-08 | Freeze the sm-workflow consumer handoff and record Cozy producer/ABI follow-up requirements. | planned |

## Reference acceptance

The reference fixture must show a candidate submitted with insufficient scoped evidence; Admission Evaluation identifies a broader semantic evidence requirement; the selected semantic Action suspends through the unchanged Phase 77 Continuation runtime; typed result/evidence resumes that Action; Admission Evaluation runs again without a second candidate submission; adequate fresh evidence is reused; stale evidence is rejected or produces the required gap; admission alone does not execute an application-specific physical commit; and the StateMachine retains transition authority throughout.

## Non-goals

- Replacing or reopening Phase 77 Continuation semantics.
- Making every StateMachine use CAM.
- Adding Admission fields to every Action/Continuation.
- Software-development Phase/Checklist/review-scope vocabulary.
- Git commit, publication, organizational approval or other domain commitment implementation.
- A second Workflow/StateMachine transition algebra.
- Provider/model-specific AI semantics.

## sm-workflow handoff

sm-workflow Phase 1 is the first application proving case. Its RequestStepClose specializes Candidate/Submission and its closure policy specializes AdmissionRequirement/Evaluation. Review scope/freshness semantics remain sm-workflow-owned typed payloads. Missing semantic evidence is converted by the Admission layer into the application-declared semantic Action, which may suspend through Phase 77.

sm-workflow must consume Phase 90 rather than implement a parallel generic Candidate/Admission runtime.

## References

- [Candidate-Admission Model](../notes/candidate-admission-model.md)
- [Phase 77](phase-77.md)
- [Phase 89](phase-89.md)
