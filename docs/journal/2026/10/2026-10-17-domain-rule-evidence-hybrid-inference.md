# Domain Rule, Evidence, and Hybrid Inference Discussion

Date: 2026-10-17
Status: recorded / architectural direction

## Context

The discussion started from making Domain Rule a first-class concept in the
Domain Model and checking whether CNCF already had a Rule Engine. Phase 32 was
confirmed as the existing Rule Engine and Inference Runtime foundation, so the
direction is to connect Domain Rule to that foundation rather than introduce a
second engine.

The discussion then considered BI and Knowledge integration. A Rule decision
may depend not only on operational Domain State but also on analytics,
knowledge-derived facts, statistical predictions, generative-AI inference, and
human judgment.

## Key Decision

Use Evidence as the umbrella concept for inputs used to support a Domain
Decision, while preserving different certainty semantics.

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

Evidence type and provenance are separate axes.

Measure is a first-class Evidence kind. Embedding itself is a representation
mechanism rather than Evidence: an embedding model produces vectors, and a
comparison over those vectors produces an EmbeddingSimilarity Measure.
Similarity scores are not probabilities and therefore are not Estimates.
Measures may be consumed directly by Domain Rules and may also serve as input
Evidence for Estimates or Judgments.

## Symbolic and Generative Reasoning

Traditional expert-system reasoning is retained as an important part of the
architecture. Its deterministic conclusions are Derived Facts and may re-enter
WorkingMemory / Fact space for further forward or backward reasoning.

Generative AI has different semantics. Its internal reasoning is opaque and
its output confidence is not automatically a calibrated probability. Semantic
conclusions produced by generative AI therefore enter the decision architecture
as AIJudgment by default rather than being silently promoted to Fact.

The architecture deliberately combines both:

- symbolic reasoning for deterministic, explainable derivation;
- embedding/vector similarity for deterministic relational Measures and retrieval;
- BI/statistical/ML processing for quantified uncertainty and predictions;
- Knowledge as source material and deterministic inference input;
- generative AI for semantic interpretation and judgment;
- Human judgment where semantic or governance requirements demand it;
- Domain Rule for combining admissible Evidence into a Domain Decision.

## Fact Clarification

Operational data stored in a database is a major Fact source. For example,
Entity/DataStore state may project propositions such as `parentOf(A,B)` and
`birthYear(B,2010)`. The database representation itself is not the Fact;
Domain semantics project the stored state into a StateFact.

Defined facts such as a tax rate, contractual payment term, or catalog price
are distinct from observed and derived facts. Declared facts cover assertions
made by a user or external party.

## Estimate Clarification

Estimate is separate from Judgment because it has an explicit uncertainty
model. Statistical estimates, predictions, and forecasts should retain their
model/version and uncertainty/calibration semantics. This permits a Domain
Rule to distinguish a calibrated probability from an AI semantic judgment.

## Integration Direction

The intended long-term flow is not a single pipeline but a cooperative
decision space.

Symbolic inference can derive Facts, embedding/vector processing can produce Measures and retrieve related Evidence, BI/ML can produce Estimates, generative AI
can produce Judgments, and Human judgment can provide or admit semantic
decisions. Domain Rules declare which Evidence kinds are acceptable. Candidate
Admission may gate evidence or judgments before high-impact decisions.

This direction connects existing CNCF work:

- Phase 32 RuleEngine / InferenceEngine / WorkingMemory;
- JudgmentAction;
- Candidate-Admission Model;
- Knowledge;
- AI Audit;
- Experiment / multi-arm evaluation;
- future BI/Analytics boundary;
- Component Knowledge and Domain Rule publication.


## Provider and Persistence Boundary Decision

The Rule Engine should not access PostgreSQL directly for ordinary Domain
Facts. StateFact acquisition uses a semantic boundary:

```text
Rule Engine
  -> Fact Provider SPI
  -> Entity Space / EntityRealm
  -> DataStore
  -> PostgreSQL (or another store)
```

This keeps the Rule architecture independent from physical persistence and
makes the Domain Model responsible for interpreting stored Entity/Aggregate
state as propositions.

Analytics is a deliberate exception: an Analytics Provider may query
PostgreSQL set-oriented structures directly and return Measure/Estimate
Evidence, because forcing large aggregate workloads through Entity Space would
be unnecessary.

SIE is used as a component/provider for semantic Evidence rather than being
folded into Rule Engine core. Its RDF/Knowledge capability supplies
DerivedFacts, while vector/semantic retrieval supplies EmbeddingSimilarity
Measures and retrieved Evidence. The Rule architecture therefore depends on an
SPI/CNCF Evidence contract, not on Fuseki, Chroma, SPARQL, or a concrete vector
database.

For a demo, the intended composition is PostgreSQL behind Entity/DataStore,
SIE for RDF and vector semantics, a traditional symbolic engine such as Drools
behind an inference provider, and Codex behind the judgment boundary. The
precise SPI taxonomy remains to be reconciled with the existing Phase 32
RuleEngine/InferenceEngine ABI.

## Documentation Action

The architecture is recorded in:

- `docs/design/rule-engine-inference-runtime.md`
- `docs/notes/hybrid-rule-evidence-architecture.md`
- this journal entry

The Phase 32 normative spec and closed phase are intentionally not changed yet.
A later phase should reconcile the new Evidence model with the existing public
Fact and Candidate-Admission Evidence contracts before implementation.
