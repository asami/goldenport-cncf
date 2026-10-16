# Continuation, Admission and Human-in-the-Loop Modeling

Date: 2026-10-05
Status: architectural conclusion
Related: Phase 77 Continuation, Phase 90 Candidate-Admission, Phase 102 Integrated Admission Management

## Conclusion

The combination of Continuation and Candidate-Admission makes Human/AI participation an internal, refinable implementation concern of an abstract Workflow rather than an outer orchestration concern.

Without Continuation, a caller/orchestrator tends to own knowledge such as "this component is waiting for human approval", how to resume it, and what to do after approval. That leaks component Workflow semantics across the abstraction boundary and weakens component-level modeling.

With Continuation, the Workflow declares that an external typed result is required, suspends, and later resumes. The host binds the external participation through IoC. The host need not know the internal state/transition sequence.

Admission supplies the semantic layer: a Candidate needs a decision under an Admission requirement/policy. If that decision requires a human, the required semantic Action can use Continuation mechanics. Human-in-the-Loop is therefore not a separate outer loop.

## Modeling consequence

An abstract model element can collaborate with a Workflow/Operation without knowing whether its internal realization currently contains deterministic processing, AI reasoning, human review, or a combination.

This permits refinement from abstract model through implementation while preserving the external contract. Human/AI participation can be introduced after operational feedback reveals a need, or reduced after sufficient evidence supports automation.

The important separation is:

- Workflow/Operation: abstract semantic contract;
- Admission: acceptance meaning and authority/policy;
- Continuation: external-participation suspension/resume and IoC mechanics;
- provider/presentation adapter: Slack/Web/mobile/Watch/AI realization.

This separation is a core CNCF modeling property, not merely a UI approval feature.
