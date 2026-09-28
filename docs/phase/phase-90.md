# Phase 90: Candidate-Admission Runtime Support

status=closed
planned_at=2026-09-21
started_at=2026-09-25
closed_at=2026-09-25
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

The stable acceptance IDs are defined only in the [Phase 90 Checklist](phase-90-checklist.md).

| Outcome | Status |
| --- | --- |
| Freeze Phase 77/90 layer boundary and generic CAM terminology. | implemented |
| Define provider-neutral Candidate/Submission/Requirement/Evaluation/Gap/Result Value Objects. | implemented |
| Define typed Evidence binding, target snapshot/revision correlation and freshness/coverage extension hooks. | implemented |
| Integrate Admission Evaluation with StateMachine guards/actions without adding a parallel transition engine. | focused proof passed |
| Map semantic Admission Gaps to normal Required SPI Actions that may suspend through Phase 77 Continuation. | integrated reference fixture passed |
| Prove reuse of supplied fresh evidence and fail-closed stale/incompatible evidence behavior. | focused proof passed |
| Prove deterministic and authority gaps use normal Provider/Decision boundaries rather than semantic AI by default. | focused proof passed; application issuance remains consumer-owned |
| Freeze the sm-workflow consumer handoff and record Cozy producer/ABI follow-up requirements. | documented below |

## Current implementation slice

The optional CAM Value Objects and evidence evaluator now validate candidate
revision, typed evidence/scope, provenance, freshness and coverage. A separate
immutable per-evidence local JSON store uses application-supplied codecs and
preserves the submission association, target revision, typed payload/scope and
provenance across adapter recreation. It does not share a transaction with
Continuation or turn an evidence write into admission. An ordinary
StateMachine guard evaluates admission and leaves transition selection to the
existing canonical selector. A declared Gap binding maps semantic and
deterministic requirements to normal Required SPI operations and authority
requirements to an application-owned Decision boundary; it does not select
which gap to execute. A focused fixture takes the semantic operation through
the ordinary Action, persists its suspension through Phase 77, recreates the
runtime, resumes once, stores the later typed evidence, reopens the store, and
re-evaluates the original submission with recovered evidence. Neither
evaluation nor guard executes application commitment.

The execution-facing `resolveForInstanceC` now validates an Active,
unsuspended WorkflowInstance and uses its version-bound admitted definition.
The declaration check requires each semantic/deterministic operation's
capability, Action identity, service/operation and input/result type to match
its Required SPI. The shape-only and separately supplied definition resolvers
are package-private and are not execution admission APIs. A real Cozy
Candidate-Admission ABI fixture passes this check; undeclared Action,
result-type drift, unstarted and suspended instances fail closed.
Authority Decision remains application-owned and is not claimed to be admitted
by Required SPI metadata.

A synthetic Step-closure reference fixture derives an admitted Cozy
Candidate-Admission ABI shape with Step names, binds it to an Active
WorkflowInstance, and routes a broader-review Gap through its declared
Required SPI. The ordinary UnitOfWork interpreter invokes an explicitly bound
Program Provider, receives `Suspended`, and commits that same UnitOfWork before
Phase 77 publishes the Continuation. After runtime recreation and one-shot
resume, the later evidence is loaded from the separate CAM store and the
original Submission is admitted without resubmission. This does not assert
that Cozy already generated a real sm-workflow CML model.

A separate focused fixture dispatches a deterministic Gap through the existing
UnitOfWork interpreter and explicitly bound deterministic Provider; an unbound
Required SPI fails. It projects an authority Gap through the normal Workflow
Decision wire boundary, and that Decision cannot be admitted as a WorkOrder
result. These fixtures do not add a CAM-owned Provider dispatcher or Decision
issuer.

Focused direct Scala compilation of the new files and 52 ScalaTest cases passed
on 2026-09-25, including unchanged `TransitionSelectorSpec`,
`StateMachineRequiredOperationActionSpec`, `WorkflowProtocolV1Spec`, and
`ContinuationRuntimeSpec` from the existing compiled classpath. The final
source/spec tree passed `sbt --batch test` under receipt
`P90-FULL-TEST-01A0D871`: 515 suites completed, 3,840 tests succeeded, 0
failed and 0 aborted; the SBT lock was released. The only later edits are
this documentation-only closure record and checklist/index status projection.
A separate read-only closure review found no Current Boundary Blocker.
Application-owned Decision issuance/durability and an actual sm-workflow
consumer remain outside this Phase.

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

The consumer handoff is the existing admitted Cozy Candidate-Admission producer
sidecar and Workflow ABI, received by `CandidateAdmissionProducerAbi` and
`CandidateWorkflowAbi` and bound to an active instance through
`WorkflowInstancePersistence.bindCandidateDefinitionC`. For each submitted Step
candidate, sm-workflow owns the concrete candidate/evidence codecs, Step review
scope and freshness policy, required evidence definitions, and the declared
Requirement-to-Required-SPI bindings. It calls `evaluateC` or the ordinary
`CandidateAdmissionGuard` and uses `resolveForInstanceC` only against the
instance's admitted definition. It owns issuing and persisting authority
Decisions, mapping successful semantic results into evidence, and invoking any
physical Git/application commitment only after its StateMachine selects an
admitted transition. CAM evaluation, routing, and evidence storage never commit.

The current Step fixture is synthetic naming over a real admitted Cozy ABI
shape; it is not a generated sm-workflow CML artifact. sm-workflow Phase 1 must
replace it with its own generated model and executable consumer proof. If that
model exposes a concrete missing Cozy producer expression or ABI field, record
the exact gap for Cozy rather than adding a CNCF-local substitute. The separate
evidence store and Phase 77 post-commit Continuation are intentionally loose;
shared EventStore/WorkflowInstance/Continuation/UnitOfWork transaction work
remains in [Phase 94](phase-94.md), not in this handoff.

## References

- [Candidate-Admission Model](../notes/candidate-admission-model.md)
- [Phase 77](phase-77.md)
- [Phase 89](phase-89.md)
