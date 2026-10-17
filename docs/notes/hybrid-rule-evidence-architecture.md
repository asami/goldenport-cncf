# Hybrid Rule Evidence Architecture

Status: design note
Date: 2026-10-17

## Intent

Extend the conceptual scope around the Phase 32 Rule Engine without changing
its closed ABI yet. The target is one Rule Architecture that can combine
traditional expert-system / symbolic reasoning with BI, statistical/ML
estimation, Knowledge, generative-AI inference, and human judgment.

## Evidence Model

The common input concept is Evidence.

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

Fact, Measure, Estimate, and Judgment must not be collapsed into one confidence-bearing
value. They have different certainty semantics.

### Fact

DefinedFact is true within the scope of an authoritative definition such as a
contract, regulation, catalog, or configuration.

StateFact projects operational Domain State into a proposition. For example,
stored Entity data may yield `parentOf(A,B)` or `birthYear(B,2010)`. The
physical database row is not itself the Fact. The Domain Model supplies the
meaning and the Fact projection.

ObservedFact comes from an observation. DeclaredFact records an assertion by a
person or external party.

DerivedFact is produced deterministically from other Facts. Symbolic expert
system reasoning, deterministic calculation, and deterministic Knowledge
inference therefore produce Derived Facts that can be fed back into subsequent
inference.

### Measure

Measure is first-class Evidence. It represents the result of an explicit
measurement, comparison, or deterministic scoring function and may be consumed
directly by a Domain Rule or used as input to an Estimate or Judgment.

Embedding is not itself Evidence. An embedding model produces a vector
representation; a similarity or distance function over vectors produces
Evidence. Embedding-based similarity is therefore represented as an
EmbeddingSimilarity Measure. Its provenance should retain the embedding model,
model version, vector space/index, compared subjects, source versions, and
similarity/distance function.

A similarity value such as 0.87 is not a probability that two subjects have the
same meaning. This is why Similarity belongs to Measure rather than Estimate.

Similarity search may additionally return retrieved Evidence or references to
Evidence. Retrieval is a discovery mechanism; the returned similarity scores
are Measures and the retrieved domain/knowledge material retains its own
Evidence kind and provenance.

### Estimate

Estimate represents quantified uncertainty. A prediction or forecast should
retain the producing model/version, population or applicable context,
evaluation time, and statistical uncertainty/calibration information where
available.

A calibrated statistical probability and an LLM's self-reported confidence are
not the same semantic quantity.

### Judgment

Judgment represents semantic or interpretive evaluation that is not justified
as a deterministic derivation or statistically calibrated estimate.
Generative-AI output belongs here by default when it interprets documents,
context, intent, risk, suitability, or other semantic material. Human
judgments use the same top-level category but retain their distinct provenance.

## Provenance Is a Separate Axis

Evidence kind answers what epistemic kind of input this is. Provenance answers
where it came from. They are orthogonal.

Typical provenance includes Domain Entity/Aggregate, DataStore, contract,
regulation, external system, sensor, Knowledge, BI/Analytics, AI, and Human.

Evidence should retain source identity/reference, scope, validity time,
generation/evaluation time, version, and derivation/input links as applicable.

## Hybrid Inference

```text
Defined / State / Observed / Declared Facts
                 |
       +---------+----------+
       |                    |
       v                    v
Symbolic Inference     Knowledge Inference
       |                    |
       +------> Derived Facts <------+
                    |                |
                    |          BI / Statistical / ML
                    |                |
                    |             Estimates
                    |                |
                    +--------+-------+
                             |
                    Generative AI
                             |
                        AI Judgment
                             |
               +-------------+-------------+
               |                           |
          Domain Rule                Human Judgment
               |                           |
               +-------------+-------------+
                             |
                        Domain Decision
```

The diagram is not a required processing order. A Rule may consume any
admissible combination of Facts, Measures, Estimates, and Judgments. Symbolic inference
may iterate over newly Derived Facts. Generative AI may consume Facts,
Estimates, Knowledge, or other evidence and return a Judgment. Candidate
Admission may be required before a Judgment is accepted for a high-impact
decision.

## Relationship to Existing CNCF Mechanisms

- Phase 32 RuleEngine remains the deterministic rule-evaluation foundation.
- Phase 32 InferenceEngine remains the current deterministic derivation
  foundation.
- WorkingMemory is the current Fact-space mechanism.
- JudgmentAction is the natural provider-neutral execution boundary for
  semantic judgment work.
- Candidate-Admission provides the acceptance boundary for candidate evidence
  or judgment where required.
- AI Audit supplies external evidence for evaluating AI judgment quality.
- Experiment can measure provider/model/rule alternatives and downstream
  outcomes.
- Knowledge can provide source material and deterministic knowledge-derived
  Facts.
- Embedding/vector providers can provide Similarity Measures and retrieve related Evidence.
- BI/Analytics can provide Estimates and Predictions; it may also discover
  candidates for later Domain Rule evolution.


## Provider and Storage Boundary

The Rule architecture consumes semantic Evidence and must not bind Domain Rules
to a physical database or a concrete knowledge/vector engine.

The default StateFact path is:

```text
Rule Engine
    |
Fact Provider SPI
    |
Entity Space / EntityRealm
    |
DataStore
    |
PostgreSQL
```

PostgreSQL is therefore an important implementation substrate but is not part
of the Domain Rule contract. Entity/Aggregate state is interpreted by the
Domain Model and projected through a Fact Provider into StateFacts. Rule code
must not issue SQL or depend on table/row structure.

A Fact Provider SPI also leaves room for DefinedFact, ObservedFact,
DeclaredFact, and external Fact providers without coupling the Rule Engine to
their storage mechanisms.

Collection-oriented analytics is a deliberate exception to the Entity-first
path. An Analytics Provider may operate directly over PostgreSQL views,
materialized views, aggregates, or another analytical store when routing large
sets through Entity Space would be artificial or inefficient. Its output is
normalized as Measure or Estimate Evidence rather than exposed as database
rows.

Semantic evidence is supplied by SIE through an SPI/provider boundary rather
than by embedding Fuseki, Chroma, SPARQL, or vector-store details in the Rule
Engine. The current SIE RDF DB and Vector DB providers can map conceptually as:

```text
SIE RDF / Knowledge reasoning -> DerivedFact
SIE semantic/vector similarity -> EmbeddingSimilarity Measure
SIE retrieval                  -> retrieved Evidence + similarity Measures
```

The initial demo architecture can therefore use the following composition:

```text
                         Rule Engine
                              |
                     Evidence / Inference
                              |
       +----------------------+----------------------+
       |                      |                      |
 Fact Provider SPI      Inference Provider      Judgment Provider
       |                      |                      |
 Entity Space           +-----+-----+              Codex
       |                |           |                |
 DataStore           Symbolic      SIE           AIJudgment
       |              engine       |
 PostgreSQL          (e.g.         +-- RDF/Knowledge -> DerivedFact
                     Drools)       `-- Vector       -> Measure
       |
 Analytics Provider
       |
 Measure / Estimate
```

The exact SPI split remains open until it is reconciled with the existing Phase
32 RuleEngine/InferenceEngine sockets. The architectural invariant is more
important than the provisional SPI names: physical stores and provider-specific
engines remain behind component/provider boundaries, while Rule evaluation
operates on CNCF-owned Evidence semantics.

## Domain Rule Direction

Domain Rule should become a first-class Domain Model concept. It expresses
business/domain knowledge, not merely an Operation execution policy.

A future CML/Cozy integration should map Domain Rule into the CNCF Rule Model
and bind it to the owning Domain Component. This is also the Architecture
Harness interpretation: model the business rule, assign ownership to the
Domain Component, package that responsibility in the CAR boundary, and let
runtime evaluation use the CNCF Rule Engine.

## Open Design Questions

1. Whether Evidence is a new supertype around the current Fact model or an
   adjacent decision-input model.
2. How existing Candidate-Admission Evidence aligns with Rule Evidence without
   creating duplicate Evidence abstractions.
3. How DomainRule declares accepted evidence kinds, provenance constraints,
   freshness, validity, and Admission requirements.
4. Whether Measure needs a common value/unit/function contract and how Similarity provenance is represented.
5. Whether Estimate needs a general uncertainty model or starts with a small
   statistical metadata contract.
6. How JudgmentAction results are projected into AIJudgment without coupling
   Rule Engine core to an AI provider.
7. How Knowledge propositions and operational StateFacts share proposition
   identity while retaining different provenance.
8. How BI/Analytics adapters expose estimates without putting analytics
   engines inside CNCF core.
9. Which later phase should freeze the public ABI after the conceptual model is
   reconciled with Phase 32, Candidate Admission, Knowledge, and AI Audit.



## Phase 114 Demonstrator Implementation Plan

Phase 114 is the first implementation proving ground for this note. It should
prefer thin adapters and one vertical scenario over framework generalization.

### Suggested package direction

The exact names are implementation-time decisions, but the prototype should
keep CNCF-owned semantics separate from adapters:

```text
org.goldenport.cncf.rule.evidence
  Evidence / Fact / Measure / Estimate / Judgment prototype
  EvidenceProvenance
  EvidenceLink / derivation link

org.goldenport.cncf.rule.integration
  EntityFactProjection
  SemanticEvidenceAdapter
  AnalyticsEvidenceAdapter
  JudgmentEvidenceAdapter

provider/test or fixture scope
  deterministic symbolic provider
  deterministic SIE provider
  deterministic judgment provider
```

Do not add a provider-specific class such as `DroolsFact`, `FusekiFact`,
`ChromaMeasure`, or `CodexJudgment` to the CNCF public model. Provider
identity belongs in provenance/diagnostics.

### StateFact implementation

Reuse EntityRealm/Entity Space and existing DataStore resolution. A small
projection function should translate an already-resolved Entity/Aggregate view
into propositions needed by the fixture. Do not build a generic ORM-to-Fact
reflection layer in Phase 114.

For example, a fixture may project:

```text
Customer(id=C1, rank=B, birthYear=2010)
  -> StateFact(customerRank(C1,B))
  -> StateFact(birthYear(C1,2010))
```

The PostgreSQL integration test proves persistence substitution, while the
deterministic unit/executable specification may use the existing in-memory
DataStore path if that keeps the suite hermetic.

### Symbolic implementation

Use the Phase 32 built-in forward derivation first. Add Drools only after the
same fixture works through the CNCF deterministic baseline. The external-engine
adapter should translate CNCF Facts into provider input and translate only
admitted deterministic conclusions back to DerivedFact. Provider actions,
sessions, globals, or mutations must not escape the adapter.

This order proves that Hybrid Rule architecture is a CNCF semantic capability,
not a Drools wrapper.

### SIE implementation

Treat SIE as a reusable component, not a library hidden inside Rule Engine.
Reuse its existing RDF DB SPI, Vector DB SPI, and SemanticRetrieval operations.

The adapter should have two narrow mappings:

- semantic/RDF proposition -> DerivedFact;
- similarity result -> EmbeddingSimilarity Measure plus references to retrieved
  Evidence.

For Phase 114, do not redesign SIE storage. Existing Fuseki/Chroma providers are
acceptable. PostgreSQL/pgvector migration or consolidation is explicitly later
work.

### Analytics implementation

Use one small SQL aggregate or view against the PostgreSQL demo data. The
result should be deliberately chosen to demonstrate the semantic difference
between Measure and Estimate. Prefer a Measure if no statistically meaningful
uncertainty model is present. Do not label an arbitrary score or ratio as an
Estimate merely to exercise the type.

### Codex judgment implementation

The live path should formulate one bounded semantic question whose output can
be normalized to a small typed alternative set plus rationale/evidence
references. Reuse JudgmentAction/Continuation concepts rather than adding an
LLM call inside RuleEngine.

The executable specification uses a deterministic provider returning the same
typed Judgment. Live Codex invocation is an integration proof and must not be
required for ordinary test execution.

### Explanation implementation

Do not build a general graph database for explanation. Keep a lightweight
Evidence DAG using stable Evidence identities and input/producer links:

```text
StateFact ----+
              +--> DerivedFact ----+
Knowledge ----+                    |
                                   +--> DomainDecision
SimilarityMeasure -----------------|
AnalyticsMeasure ------------------|
AIJudgment ------------------------+
```

The acceptance projection only needs to show Evidence identity/kind,
producer/provenance summary, derivation inputs, Rule identity/version, and
Decision. Raw sensitive payloads remain outside diagnostics.

### Demonstrator domain

Choose a synthetic domain with no privacy or external-data dependency. The
scenario should naturally need all four evidence families rather than adding
dummy values solely to tick checklist boxes. A small eligibility/review
decision is preferred because it can end in typed alternatives such as
Accept/ManualReview/Reject while keeping Action firing out of scope.
