# Phase 73 - Knowledge Primitives Foundation

status=planned
planned_at=2026-09-15
depends_on=[Phase 61.6](phase-61.6.md)
consumer=KnowledgeHub Phase 1
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
implementation_note=[CNCF Knowledge Primitives Provisional Specification](../notes/knowledge-primitives-provisional-specification.md)
decision_journal=[CNCF Knowledge Primitives / KnowledgeHub Framework Boundary](../journal/2026/09/2026-09-15-knowledge-primitives-knowledgehub-boundary.md)

## Purpose

Extend the canonical CNCF Information foundation with the minimum domain-independent knowledge representation primitives required by KnowledgeHub Phase 1, without moving the Knowledge Processing Framework into CNCF.

The phase establishes the reusable result-side contract for:

```text
Information
 -> contextual Interpretation / SemanticMapping
 -> Knowledge
```

with Context / Provenance / Evidence traceability.

## Dependency and scheduling

The technical dependency is the Phase 61 canonical Information closure, especially Phase 61.6. Phase 73 is consumer-driven by KnowledgeHub Phase 1 and is separately schedulable; it does not silently supersede or renumber existing planned Phases 62-72.

Each implementation subphase should remain within approximately six hours of focused implementation where practical.

## Selected boundary

CNCF owns reusable knowledge representation / committed result types.

KnowledgeHub owns:

- Semantic Context selection from SIE / BoK;
- Semantic Grounding;
- Knowledge Formation / Distillation;
- Context Shift;
- Structural Analogy;
- Abductive Projection;
- Knowledge Gap exploration;
- application / domain processing policy.

CNCF must not gain a dependency on KnowledgeHub or SIE.

## Work Stack

| ID | Stage | Outcome | Status |
|---|---|---|---|
| KP-01 | Canonical inventory and boundary freeze | Existing Information, Knowledge, Context, provenance, evidence, annotation, mapping, persistence, projection, and Phase 61 migration behavior are inventoried; duplicates and ownership are fixed before coding. | planned |
| KP-02 | Knowledge result model | The minimum reusable Knowledge / semantic-role / source-reference contract is defined as an extension of canonical Information semantics. | planned |
| KP-03 | Context and provenance contract | Generic Context / Provenance / Evidence references required for knowledge traceability are reused or added without KnowledgeHub policy. | planned |
| KP-04 | Interpretation and mapping results | Reusable Interpretation / committed SemanticMapping result representation is established without grounding algorithms or ontology policy. | planned |
| KP-05 | Persistence, serialization, and projection | New or promoted primitives round-trip through canonical CNCF persistence / wire / projection paths with stable IDs and references. | planned |
| KP-06 | Traceability operations | Source Information -> Interpretation / Mapping -> Knowledge and reverse trace queries are proven through normal CNCF APIs / repositories. | planned |
| KP-07 | Executable specifications and consumer fixture | Executable specifications prove lifecycle, provenance, mapping, persistence, and a minimal KnowledgeHub-style consumer scenario without importing KnowledgeHub. | planned |
| KP-08 | Boundary audit and handoff | No duplicate Information model or KnowledgeHub/SIE dependency remains; stable contracts and deferred items are handed to KnowledgeHub Phase 1. | planned |

## KP-01 — Canonical inventory and boundary freeze

Before implementation:

- inspect canonical `Information` from Phase 61;
- inspect any existing Knowledge representation or migrated Knowledge lifecycle;
- inspect Context / ExecutionContext / provenance-like structures;
- inspect evidence / annotation / semantic-role equivalents;
- inspect persistence and entity identity conventions;
- inspect wire / projection / API representation;
- identify what can be promoted or reused instead of added.

Produce failing-first or inventory evidence for any missing contract.

## KP-02 — Knowledge result model

Define the minimum reusable Knowledge representation.

Desired properties:

- stable identity where required;
- link to originating Information / InformationFragment;
- Context reference;
- Provenance reference;
- Evidence references where applicable;
- generic semantic role where justified;
- representation that remains meaningful without KnowledgeHub.

Prefer `Knowledge + semanticRole=Hypothesis` over a separate Hypothesis entity unless executable requirements demonstrate that Hypothesis has a distinct lifecycle that requires a first-class type.

## KP-03 — Context and provenance

Provide only generic reusable context and provenance semantics.

Do not model `SemanticContext` as an SIE / BoK selection inside CNCF.

The contract must be sufficient for a KnowledgeHub consumer to state that a derived Interpretation / Mapping / Knowledge was produced from specific source Information under a specific context and provenance chain.

## KP-04 — Interpretation and mapping results

Represent committed result state, not the process that discovers it.

For example:

```text
SemanticMapping
  source reference
  target reference
  mapping type
  confidence / quality when generic
  context
  provenance
```

GroundingCandidate generation, SIE lookup, prompt / AI policy, review workflow, and mapping discovery remain KnowledgeHub responsibilities.

## KP-05 — Persistence / serialization / projection

Use existing CNCF mechanisms.

No second knowledge-specific persistence subsystem is introduced.

Round-trip acceptance should cover:

- creation;
- retrieval;
- serialization;
- stable references;
- persisted source links;
- Context / Provenance links;
- compatibility with canonical Information runtime behavior.

## KP-06 — Traceability

Prove both directions where supported:

```text
Knowledge
 -> Interpretation / SemanticMapping
 -> Information
```

and

```text
Information
 -> derived Interpretation / Mapping
 -> Knowledge
```

Traceability should use ordinary CNCF identity / repository / query mechanisms.

## KP-07 — Executable specifications

At minimum cover:

- Information remains canonical and is not duplicated;
- Knowledge can reference source Information;
- Context / Provenance survives persistence round-trip;
- committed SemanticMapping can be represented without SIE dependency;
- generic semantic role round-trips if introduced;
- source-to-knowledge traceability works;
- no KnowledgeHub class is required in CNCF tests.

A consumer fixture may emulate a KnowledgeHub call sequence, but the fixture must depend only on CNCF public contracts.

## KP-08 — Boundary audit / handoff

Before closure verify:

- no dependency on KnowledgeHub;
- no dependency on SIE / textus-bok;
- no book / museum / agriculture-specific vocabulary;
- no SemanticGrounding algorithm in CNCF;
- no duplicate Information hierarchy;
- no alternate persistence mechanism;
- KnowledgeHub has enough public contracts for its Phase 1 server-side vertical slice.

## Acceptance

- Canonical Phase 61 Information remains the single Information foundation.
- A reusable Knowledge representation exists or an existing one is promoted and documented.
- Knowledge can trace to source Information without KnowledgeHub-specific types.
- Generic Context / Provenance required by the consumer survive persistence and projection.
- Interpretation / committed SemanticMapping results can be represented without embedding grounding policy.
- Generic Evidence / semantic role support is available where inventory proves it necessary.
- A KnowledgeHub-style consumer fixture can represent Information -> Interpretation / Mapping -> Knowledge using only CNCF contracts.
- CNCF has no dependency on KnowledgeHub, SIE, or domain-specific BoKs.
- Executable specifications fix lifecycle, round-trip, and traceability behavior.

## Non-Goals

- SIE / BoK connectivity.
- SemanticContext selection.
- SemanticGrounding algorithms or AI prompts.
- GroundingCandidate ranking.
- Knowledge Formation orchestration.
- Knowledge Distillation.
- Context Shift / Reframing.
- Structural Analogy.
- Abductive Projection.
- Knowledge Commons communication processing.
- Editing Studio Book Capture behavior.
- Flutter / mobile APIs.
- Moving CaptureSession / CaptureItem into CNCF without a separate explicit decision.

## Planning References

- [CNCF Knowledge Primitives Provisional Specification](../notes/knowledge-primitives-provisional-specification.md)
- [CNCF Knowledge Primitives / KnowledgeHub Framework Boundary](../journal/2026/09/2026-09-15-knowledge-primitives-knowledgehub-boundary.md)
- [Phase 61 - Information CML Runtime Canonicalization](phase-61.md)
- [Phase 61.6 - Information Canonical Closure](phase-61.6.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
