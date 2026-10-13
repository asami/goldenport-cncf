# Generic review/admission semantics for staged development quality checks

Date: 2026-10-13
Status: architecture direction
Related: Workflow, Candidate-Admission Model, Evidence, AdmissionRequirement, sm-workflow, Cozy, cbd-support

## Context

A development workflow may evaluate quality at several lifecycle boundaries. sm-workflow currently needs Step Close, Phase Review and Phase Final, with CAR lint and cbd-support semantic Review using different scope/purpose at each boundary.

## Shared review matrix

| Concern | Step Close | Phase Review | Phase Final |
|---|---|---|---|
| CAR lint: CNCF/Textus rules | changed scope, primary | whole Phase, primary | unresolved/blocking confirmation |
| CAR lint: model/ABI consistency | changed scope, primary | integrated state, primary | freshness/blocker confirmation |
| CAR lint: prohibited/risky patterns | primary | primary | unresolved confirmation |
| Review: Step requirements | primary | supporting evidence | normally no new search |
| Review: Usage Contract | affected Slice | whole Phase, primary | blocking completion |
| Review: implementation quality | changed scope, primary | whole-system perspective | repair delta when needed |
| Review: excessive checking/assurance | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: unnecessary complexity/abstraction | changed scope, primary | cross-Step/system-wide, primary | repair delta when needed |
| Review: duplicate implementation/state | relevant scope | primary | only unresolved/delta |
| Review: component integration | relevant connected scope | primary | blocking completion |
| Review: architecture fitness | relevant scope | primary | only affected delta |
| Review: cross-Step assumption conflicts | normally out of scope | primary | unresolved confirmation |
| Acceptance Boundary compliance | primary | primary | primary |
| Finding resolution | after repair | primary | primary |
| Evidence coverage/freshness | primary | primary | primary |
| New issue discovery | primary within Step | primary across Phase | normally not exploratory |
| Release blockers absent | not a Step concern | identify/classify | primary |

Interpretation:

- **Step Close** asks: is the delivered Step/Slice locally sound and admissible?
- **Phase Review** asks: is the integrated result sound as a whole? This is the broadest semantic quality review.
- **Phase Final** asks: is closure/release justified by fresh, sufficient evidence? It should not restart an unconstrained exploratory review.

CAR lint and cbd-support Review are therefore not three unrelated implementations and should not blindly rerun an identical full scan. The lifecycle context selects scope, checks, evidence reuse, and blocking policy. Phase Final preferentially reuses admitted evidence and examines repair deltas/unresolved blockers.

## CNCF responsibility

CNCF should support this use case through generic Candidate-Admission and Evidence semantics, not by adding `STEP`, `PHASE` or `FINAL` as CNCF-specific lifecycle vocabulary.

The generic contract needs to express or preserve, as appropriate:

- evaluation/review purpose and requested scope;
- Candidate/artifact revision or snapshot association;
- coverage;
- freshness;
- typed findings/results and severity/blocking disposition;
- requirement satisfaction/gaps;
- evidence reuse after subsequent workflow progression;
- invalidation/affected scope after repair;
- Admission evaluation and Continuation/Decision when evidence or authority is missing.

An application such as sm-workflow can map its lifecycle contexts to different AdmissionRequirements: narrow/change-oriented, comprehensive/integration-oriented, and closure/readiness-oriented.

CNCF must not prescribe cbd-support's concrete quality rules or Cozy's CML semantics. It provides the substrate through which those results can become admissible typed evidence.

## Cross-project contract

- CNCF: generic review/validation Requirement, Evidence and Admission semantics.
- Cozy: model-derived knowledge/scope.
- cbd-support: concrete CAR lint and semantic quality evaluation.
- sm-workflow: lifecycle timing and progression.

The key architectural requirement is evidence reuse: a final closure check should be able to prove that a comprehensive review remains fresh and complete, plus inspect affected repair deltas, rather than requiring a duplicate semantic review simply because the workflow reached another named state.

## Development-plan adoption — 2026-10-13

The owner adopted sm-workflow Phase 6 as the concrete consumer/driver. Required
producer/provider capabilities are to be implemented and accepted by their owners:
CNCF Phase 106, Cozy Phase 81 and textus-cbd-support Phase 15. The connected
sequence is minimum shared contract -> real Step admission/commit -> integrated
Phase Review -> affected repair/re-review -> Final evidence reuse and closure.
Each project owns its implementation/checklist; do not wait for unrelated roadmap
items or import an entire provider backlog into Phase 6. Phase 9 extends this
working route with convergence/provider switching rather than delivering it first.

This project's [Phase 106 plan](../../../phase/phase-106.md)
records the bounded delivery. The shared driver/acceptance scenarios are in
[sm-workflow Phase 6](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.md).
Freshness means applicability to the actual current subject and requirements;
no TTL, hash/copy-based unchanged-content proof or automatic full review at every
boundary is introduced. Plans are PLANNED, not implementation or acceptance
claims. Existing active work and its Goal/base/epoch/history/counters are retained.
