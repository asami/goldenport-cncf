# Phase 114 - Hybrid Domain Rule and Evidence Demonstrator

Status: PLANNED
Owner: CNCF Rule / Domain Decision Architecture
Created: 2026-10-17

## Goal

Prove, with one executable vertical slice, that the Phase 32 Rule Engine
foundation can be extended conceptually into a hybrid Domain Rule architecture
that combines operational Facts, symbolic inference, semantic/RDF inference,
embedding similarity, analytics-derived evidence, and generative-AI judgment
without leaking physical stores or provider-specific engines into the Domain
Rule model.

This is a demonstrator phase. Its purpose is architectural proof and feedback
for a later public ABI, not production completeness.

## Foundation

Phase 114 builds on the closed Phase 32 Rule Engine and Inference Runtime. It
must preserve Phase 32's core invariants:

- evaluation is separated from action firing;
- Rule evaluation does not mutate persistent Domain state directly;
- provider-specific models do not leak into CNCF-owned Rule semantics;
- runtime actions continue through canonical ActionCall/Event/Job boundaries;
- deterministic built-in behavior remains the compatibility baseline.

Architectural input:

- `docs/design/rule-engine-inference-runtime.md`
- `docs/notes/hybrid-rule-evidence-architecture.md`
- `docs/journal/2026/10/2026-10-17-domain-rule-evidence-hybrid-inference.md`

## Demonstrator Architecture

```text
                         Domain Rule
                              |
                         Rule Engine
                              |
                     Evidence / Provider
                              |
       +----------------------+----------------------+
       |                      |                      |
 Fact Provider          Inference Provider      Judgment Provider
       |                      |                      |
 Entity Space           +-----+-----+              Codex
       |                |           |                |
 DataStore          Symbolic       SIE           AIJudgment
       |             Engine         |
 PostgreSQL          adapter        +-- RDF/Knowledge -> DerivedFact
                                      `-- Vector       -> Measure

 PostgreSQL / analytical projection
       |
 Analytics Provider
       |
 Measure / Estimate
```

The provisional provider names are not an ABI commitment. Implementation must
first reuse or adapt the existing Phase 32 RuleEngine/InferenceEngine SPI where
that remains semantically correct.

## Evidence Prototype

The demonstrator may use phase-local/prototype types where necessary. It must
preserve these semantic distinctions:

```text
Evidence
  |- Fact
  |    |- DefinedFact
  |    |- StateFact
  |    |- ObservedFact
  |    |- DeclaredFact
  |    `- DerivedFact
  |- Measure
  |    |- Similarity
  |    |    `- EmbeddingSimilarity
  |    |- Distance
  |    `- Score
  |- Estimate
  |    |- StatisticalEstimate
  |    |- Prediction
  |    `- Forecast
  `- Judgment
       |- AIJudgment
       `- HumanJudgment
```

Evidence kind and provenance are independent dimensions.

## Required Vertical Slice

One fixed demonstrator scenario must exercise all required evidence paths and
produce one explainable Domain Decision.

The fixture domain may be small and synthetic. It must include:

1. at least two StateFacts projected from Entity/Aggregate state through the
   Entity Space path backed by PostgreSQL or a faithful DataStore test fixture;
2. at least one deterministic DerivedFact produced by symbolic inference;
3. at least one SIE RDF/Knowledge-derived Fact;
4. at least one SIE embedding/vector Similarity Measure;
5. at least one PostgreSQL/analytics-derived Measure or Estimate;
6. at least one Codex-produced AIJudgment;
7. one Domain Rule that consumes a declared subset of those Evidence values and
   returns a typed Domain Decision;
8. an explanation/provenance projection showing which Evidence and derivations
   contributed to the decision.

The final decision must be reproducible in a deterministic fixture by replacing
external providers with recorded/deterministic test providers. Live SIE/Codex
integration proves integration separately and is not allowed to make the core
test suite nondeterministic.

## Work Packages

### HDR-01 Evidence prototype and compatibility mapping

- Define the smallest prototype Evidence algebra needed by the scenario.
- Map current Phase 32 Fact/WorkingMemory into the prototype without replacing
  or silently changing the Phase 32 ABI.
- Define provenance/input linkage sufficient to reconstruct the decision
  evidence graph.
- Record the mapping to Candidate-Admission Evidence and avoid a second
  competing generic Evidence authority.

### HDR-02 Entity Space StateFact adapter

- Add a phase-local Fact projection boundary over Entity Space/EntityRealm.
- Resolve Entity/Aggregate state through the existing DataStore path.
- Demonstrate PostgreSQL-backed state where practical.
- Prohibit SQL/table/row concepts from the Rule-facing model.

### HDR-03 Symbolic inference adapter

- First prove the path with the existing Phase 32 built-in deterministic
  inference.
- Add an external expert-system adapter only where needed for the demonstrator;
  Drools is the reference candidate, not a mandatory CNCF dependency.
- Normalize deterministic conclusions to DerivedFact with rule/input
  provenance.
- Do not allow an external rule engine to mutate CNCF Domain state directly.

### HDR-04 SIE semantic evidence adapter

- Consume SIE through a component/provider boundary.
- Map RDF/Knowledge inference results to DerivedFact.
- Map vector similarity to EmbeddingSimilarity Measure.
- Preserve SIE/provider provenance without exposing Fuseki, Chroma, SPARQL, or
  vector-store payloads in Domain Rule types.
- Reuse existing SIE query/explain/status and RDF DB / Vector DB boundaries
  where possible.

### HDR-05 Analytics evidence adapter

- Use PostgreSQL SQL/view/materialized-view style aggregation for one
  set-oriented analytical input.
- Return Measure or Estimate Evidence with model/query/version/time provenance
  appropriate to the fixture.
- Do not force large analytical sets through Entity Space.

### HDR-06 Generative judgment adapter

- Invoke Codex through the existing provider-neutral JudgmentAction /
  Continuation direction where practical; do not introduce Codex-specific Rule
  types.
- Normalize the semantic result to AIJudgment with rationale/evidence
  references.
- Do not treat model self-confidence as a calibrated Estimate.
- Provide a deterministic recorded/test provider for executable specifications.

### HDR-07 Domain Rule composition and explanation

- Compose the fixture Evidence into one Domain Rule evaluation.
- Produce a typed Domain Decision.
- Project an Evidence graph / explanation sufficient to answer why the
  decision was produced.
- Keep evaluation separate from optional Action/Event/Job firing.

### HDR-08 Integration demonstration and design feedback

- Run the deterministic executable specification.
- Run the live integration demonstration with available PostgreSQL, SIE, and
  Codex providers.
- Record provider substitutions and unavailable optional integrations without
  weakening the deterministic acceptance fixture.
- Review whether the prototype warrants new public Evidence/Provider SPI or can
  remain layered over existing Phase 32 contracts.
- Produce a follow-up ABI recommendation; do not freeze that ABI in this phase.

## Acceptance

Phase 114 closes only when:

- the required vertical slice passes deterministically;
- StateFact acquisition does not bypass Entity Space for ordinary Domain state;
- analytics is the only accepted PostgreSQL-direct set-oriented path in the
  fixture;
- SIE RDF and vector results are normalized respectively as DerivedFact and
  Measure;
- symbolic inference produces DerivedFact and cannot mutate Domain persistence;
- Codex output is represented as AIJudgment rather than Fact/Estimate;
- the Domain Rule consumes typed Evidence and returns a typed Domain Decision;
- explanation/provenance identifies contributing Evidence and producer/input
  relationships;
- external provider details do not leak into the Domain Rule public model;
- existing Phase 32 focused regression remains green;
- the implementation review records an explicit recommendation for the next
  ABI/productization phase.

## Non-Goals

- production-grade generic Evidence ABI;
- replacing or reopening Phase 32;
- complete Drools, RDFS, OWL, SPARQL, or CEP support;
- automatic backward-chaining orchestration across providers;
- general Rule authoring DSL or UI;
- production ML platform;
- autonomous trust scoring for AI judgments;
- migrating SIE from Fuseki/Chroma to PostgreSQL/pgvector;
- high-availability, performance, or large-data production validation;
- making Codex or any other concrete provider a CNCF core dependency.

## Dependencies

- Phase 32 Rule Engine and Inference Runtime: required foundation.
- Entity Runtime / Entity Space / DataStore: StateFact source.
- PostgreSQL DataStore path: preferred demo persistence and analytics source.
- SIE: semantic/RDF/vector evidence provider.
- JudgmentAction / Continuation / Candidate Admission: reuse where applicable
  for generative judgment boundaries.
- AI Audit: record integration where already available; not a closure blocker
  for a new production-quality audit feature.

## Deliverables

- executable vertical-slice demonstrator;
- focused executable specifications;
- provider adapters or deterministic test doubles required by the slice;
- Evidence/provenance/explanation projection;
- implementation journal and architecture feedback;
- recommendation for the later ABI/productization phase.

See `docs/phase/phase-114-checklist.md`.
