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
