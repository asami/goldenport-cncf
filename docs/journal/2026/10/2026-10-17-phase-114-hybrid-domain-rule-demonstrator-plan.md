# Phase 114 Hybrid Domain Rule Demonstrator Planning

Date: 2026-10-17
Status: planned

## Decision

Create Phase 114, Hybrid Domain Rule and Evidence Demonstrator.

Phase 32 remains closed and authoritative for the current deterministic
RuleEngine/InferenceEngine foundation. Phase 114 is a consumer/extension proof,
not a reopening of Phase 32 and not yet a public Evidence ABI phase.

Phase 113 is intentionally avoided because it is reserved by the current
Control Center / Docker-supervisor planning stream even though a phase-113 file
was not visible in the repository at planning time.

## Why a Demonstrator Phase

The conceptual model now connects several independently existing CNCF/Textus
mechanisms:

- Phase 32 RuleEngine / InferenceEngine / WorkingMemory;
- Entity Space / EntityRealm / DataStore;
- PostgreSQL;
- SIE RDF DB / Vector DB / SemanticRetrieval;
- JudgmentAction / Continuation;
- Candidate Admission;
- AI Audit and Experiment;
- Codex as a concrete generative judgment provider.

Freezing a generic ABI before exercising these boundaries would risk creating
duplicate Evidence abstractions or provider-shaped public types. Phase 114
therefore proves one vertical slice first and emits an ABI recommendation.

## Implementation Strategy

### 1. Keep the deterministic center

Start with Phase 32 built-in inference and a deterministic recorded provider for
every external boundary. The core executable specification must run without
Fuseki, Chroma, Codex, network access, or an external expert-system process.

### 2. Project Domain state through Entity Space

Ordinary StateFacts are obtained from Entity/Aggregate semantics through
Entity Space and DataStore. PostgreSQL is an implementation behind that path,
not a Rule dependency. Do not create a Rule-to-SQL shortcut.

### 3. Keep analytics set-oriented

For aggregate analysis only, an Analytics adapter may query PostgreSQL directly
and return Measure/Estimate Evidence. This is a deliberate semantic exception,
not a second StateFact path.

### 4. Reuse SIE as a component

Do not add Jena/Fuseki/Chroma dependencies to CNCF Rule core. SIE already owns
RDF DB and Vector DB provider boundaries. Phase 114 consumes SIE and maps:

- RDF/Knowledge inference -> DerivedFact;
- vector similarity -> EmbeddingSimilarity Measure;
- retrieval -> Evidence references plus Measures.

### 5. Prove symbolic inference before adding Drools

The existing Phase 32 deterministic inference must prove the symbolic
DerivedFact path first. Drools is a useful demo/provider candidate for
traditional expert-system integration, but it is optional until the baseline
slice works. If added, it remains an adapter dependency and cannot mutate CNCF
Domain state.

### 6. Treat Codex as Judgment, not Fact

Codex receives a bounded semantic question and returns a typed alternative,
rationale, and evidence references that normalize to AIJudgment. It does not
write WorkingMemory facts directly merely because the model stated a
conclusion. Admission may later promote/use a judgment according to policy.

### 7. Explain by Evidence DAG

Each produced Evidence item receives a stable identity and provenance/input
links sufficient to reconstruct a small DAG. The final Domain Decision
references the Evidence that caused the Rule to match. Phase 114 does not need
a separate graph database for this.

## Acceptance Scenario Shape

Use a synthetic eligibility/review domain. The final fixture should contain at
least:

- two StateFacts from Entity Space;
- one deterministic symbolic DerivedFact;
- one SIE knowledge/RDF DerivedFact;
- one SIE EmbeddingSimilarity Measure;
- one PostgreSQL analytics Measure or meaningful Estimate;
- one Codex AIJudgment;
- one Domain Rule;
- one typed decision such as Accept / ManualReview / Reject;
- one explanation projection tracing the decision back to its Evidence.

The scenario should be coherent: every Evidence item used by the Rule must
serve a domain reason, not exist solely to demonstrate a provider.

## Expected Learning

Phase 114 should answer:

1. Can the existing Phase 32 Fact/WorkingMemory model host or bridge to the
   wider Evidence model?
2. Is a FactProvider SPI actually needed, or can an existing CNCF boundary be
   adapted without another public SPI?
3. Should SIE integration appear as InferenceEngine, EvidenceProvider, or an
   application/component operation adapter?
4. Does Measure need a separate public contract?
5. How should Estimate uncertainty metadata be represented?
6. Can JudgmentAction output map cleanly to Rule Evidence without duplicating
   Candidate-Admission Evidence?
7. What minimum provenance/explanation contract is useful across all providers?
8. Which abstractions deserve promotion to a successor production ABI phase?

## References

- `docs/phase/phase-114.md`
- `docs/phase/phase-114-checklist.md`
- `docs/design/rule-engine-inference-runtime.md`
- `docs/notes/hybrid-rule-evidence-architecture.md`
- `docs/journal/2026/10/2026-10-17-domain-rule-evidence-hybrid-inference.md`
