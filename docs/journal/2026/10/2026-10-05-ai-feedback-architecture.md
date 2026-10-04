# AI Feedback Architecture

Date: 2026-10-05
Status: architectural direction
Related: Phase 90 Candidate-Admission, Phase 102 Integrated Admission Management

## Principle

In AI-assisted systems, operational feedback is not merely observability output. It is potential evidence for the next design, knowledge, or operational change.

The common loop is:

```text
Design / Knowledge / Configuration
        |
        v
Candidate -> Admission -> Apply
        |
        v
Operation / Usage
        |
        v
Observation / Evidence
        |
        v
Interpretation
        |
        v
Feedback Candidate
        |
        +----> Admission ----> Change
```

AI is especially useful in the Interpretation and Candidate-production stages, where broad context and non-deterministic semantic reasoning are valuable. AI does not gain change authority merely because it generated the interpretation or proposal.

## Boundary

Keep the following stages distinct:

1. Observation: deterministic/runtime facts, metrics, logs, experiment outcomes, user feedback and other evidence.
2. Interpretation: semantic analysis of what the evidence may mean.
3. Proposal/Candidate: a concrete proposed design, code, knowledge, configuration or operational change.
4. Admission: typed review/decision under the applicable authority.
5. Apply: deterministic/provider-owned mutation of the authoritative system.
6. Measurement: observe the result and feed new evidence into the next loop.

Do not collapse Observation directly into autonomous mutation.

## Common applications

### Software development

Operational evidence may produce a Phase/Plan change candidate, implementation candidate or review finding. GitHub PR and sm-workflow remain normal admission/apply routes.

### Knowledge lifecycle

Knowledge usage and new source material may reveal stale, duplicate, contradictory or missing knowledge. AI may produce editorial/Semantic-GC candidates. For Git-managed BoK artifacts, GitHub Pull Requests provide the physical candidate/review mechanism while Phase 102 provides integrated admission management.

### Experiments and AI runtime

Experiment arms, AI audit evidence and outcome metrics may produce routing, prompt, model, UI or behavior-change candidates. Experiment results are evidence, not automatic authority to modify production behavior.

## Architecture implication

Observability, Service Bus, AI Audit, Experiment, Control Center, KnowledgeHub/TKL/TKW, sm-workflow and Admission Management should be designed so evidence can retain provenance and be referenced by later Candidates.

Avoid copying complete source systems into an AI-owned feedback store. Preserve references to authoritative evidence where possible.

The long-term value is a closed improvement loop in which AI can continuously discover and formulate useful changes while deterministic systems and human/application authority govern admission and application.
