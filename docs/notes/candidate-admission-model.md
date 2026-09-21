# Candidate-Admission Model

- Date: 2026-09-21
- Status: Design principle
- Scope: CNCF StateMachine / Workflow / Continuation

## Definition

Candidate-Admission Model (CAM) is the basic collaboration model between a semantic actor such as AI, Skill or Human and a deterministic StateMachine/Workflow runtime.

> AI/Skill constructs a candidate. StateMachine admits it. Runtime commits it.

The semantic actor is allowed substantial freedom in how it reaches a candidate state. It does not receive authority to choose or commit the next StateMachine state.

~~~text
Semantic Actor
  -> semantic work
  -> Candidate
  -> Submission
StateMachine / Workflow
  -> Admission Evaluation
     -> sufficient: admit
     -> insufficient: Admission Gap
Admission Gap
  -> Continuation / deterministic operation / Decision
  -> revised Candidate or new Evidence
Admission
  -> Transition
Runtime
  -> durable commitment
~~~

## Evidence-driven admission

Workflow requirements should preferentially describe evidence required for admission rather than forcing every possible work procedure.

A semantic actor may perform useful work proactively. If equivalent fresh evidence already satisfies the admission requirement, the Workflow should reuse it. If evidence is missing, stale, insufficiently scoped, or blocked by authority, the gap is materialized as a Continuation, deterministic operation, or Decision.

This makes Continuation an expression of an Admission Gap rather than merely a hard-coded next procedural step.

## JudgmentAction

JudgmentAction is the Action-level form of CAM.

The worker produces a typed JudgmentResult containing an admitted decision, rationale and evidence. The worker does not select the next state. StateMachine admission and guards interpret the result and retain transition authority.

## Workflow closure

At larger granularity, a consumer may submit a closure candidate with available evidence. The Workflow evaluates closure policy, requests missing semantic evidence through Continuations, performs deterministic validation, and commits only an admitted candidate.

CNCF remains domain-neutral: Candidate/Admission concepts must not introduce software-development Phase/Checklist vocabulary into the generic runtime.

## Design consequences

- semantic freedom and transition authority are separate;
- typed Result/Evidence is the handoff boundary;
- proactive semantic work is allowed and reusable when fresh;
- admission is fail-closed on missing/incompatible/stale evidence;
- Continuations should identify the unmet semantic requirement;
- deterministic operations may create/verify evidence without AI invocation;
- runtime commitment follows admission and must not be performed by an external semantic worker;
- provider/model choice is not transition semantics.

CAM is expected to be validated first through sm-workflow and then generalized through CNCF/Cozy contracts without making sm-workflow-specific concepts generic.
