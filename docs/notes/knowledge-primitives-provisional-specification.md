# CNCF Knowledge Primitives — Provisional Specification

status=provisional
planned_at=2026-09-15
consumer=KnowledgeHub Phase 1
phase=[Phase 79 - Knowledge Primitives Foundation](../phase/phase-79.md)

## Purpose

Define the domain-independent knowledge representation primitives that belong in CNCF while keeping knowledge-processing policy and orchestration in KnowledgeHub.

The primary architectural rule is:

> CNCF owns reusable information / knowledge state and result vocabulary. KnowledgeHub owns knowledge-processing behavior such as semantic grounding, formation, distillation, context shift, analogy, and abduction.

CNCF already owns canonical `Information`; the new knowledge primitives extend that foundation instead of introducing a parallel KnowledgeHub-only information model.

## Boundary

```text
Reference Applications
  Editing Studio / Commons / Atelier / ...
              |
              v
KnowledgeHub Knowledge Processing Framework
  Semantic Context / Grounding / Formation
  Distillation / Context Shift / Abduction
              |
              v
CNCF Knowledge Primitives
  Information / Knowledge / Context
  Provenance / Evidence / Interpretation / Mapping
```

## Candidate primitives

Phase 79 should inventory existing CNCF types before adding anything. Candidate reusable concepts are:

- `Information` — existing canonical CNCF foundation; do not duplicate.
- `InformationFragment` — source-bounded information subset if not already represented canonically.
- `Context` / interpretation context representation — reusable conditions under which meaning or a claim is established.
- `Provenance` — source / actor / operation / time trace for derived information and knowledge.
- `Evidence` — reusable support / contradiction reference without KnowledgeHub-specific evaluation policy.
- `Annotation` — attached descriptive contribution where a generic primitive is justified.
- `Interpretation` — result of interpreting information in a context.
- `SemanticMapping` — committed mapping result between source and target semantic objects.
- `Knowledge` — reusable knowledge representation linked to source information, context, evidence, and provenance.
- `SemanticRole` or equivalent — generic claim role such as Fact, Observation, Interpretation, Hypothesis, Prediction, Recommendation, Opinion, Question when the existing Information/Knowledge model supports this cleanly.

`Hypothesis` should preferably be represented through generic Knowledge + semantic role unless implementation evidence requires a distinct runtime type.

## Result versus process rule

A key boundary is to distinguish a durable result from the process that creates it.

CNCF candidates:

```text
Information
Interpretation
SemanticMapping
Knowledge
Evidence
Provenance
```

KnowledgeHub-owned processes:

```text
SemanticContext selection
SemanticGrounding
GroundingCandidate generation
KnowledgeFormation
KnowledgeDistillation
ContextShift / Reframing
StructuralAnalogy discovery
AbductiveProjection
KnowledgeGap exploration
```

For example, CNCF may own a committed `SemanticMapping` result, while KnowledgeHub owns the grounding operation that proposes, reviews, and commits that mapping.

## Traceability contract

The primitives must support bidirectional traceability without depending on KnowledgeHub classes:

```text
Knowledge
 -> Interpretation / SemanticMapping
 -> Information / InformationFragment
 -> Source identity / locator
```

Where CaptureSession / CaptureItem are owned is intentionally not frozen by this note. Phase 79 should only pull them into CNCF if inventory confirms that they are domain-independent runtime primitives rather than KnowledgeHub acquisition-framework concepts.

## Context rule

CNCF may own a generic `Context` representation. It must not own the KnowledgeHub-specific meaning of `SemanticContext` as an SIE / BoK knowledge set selected to interpret information.

```text
CNCF Context
  actor / time / place / purpose / conditions / references

KnowledgeHub SemanticContext
  SIE-managed Knowledge / BoK / ConceptScheme / interpretation framework
```

## Provenance rule

Provenance is part of the model, not an optional audit afterthought. Derived Information, Interpretation, SemanticMapping, and Knowledge must be able to identify their origin sufficiently for later KnowledgeHub traceability and human review.

The first implementation should reuse existing CNCF journal / execution / entity identity mechanisms rather than create a second history subsystem.

## Compatibility with Information

Phase 61 established canonical Information runtime behavior. Phase 79 must begin with an inventory of that model and extend it compatibly.

Do not:

- create a second Information hierarchy;
- fork persisted Information semantics for KnowledgeHub;
- make KnowledgeHub-specific packages canonical owners of reusable primitives;
- add a second persistence or identity mechanism just for Knowledge.

## Minimal Phase 1 target

KnowledgeHub Phase 1 needs enough CNCF support for this server-side path:

```text
Information
 -> Interpretation / SemanticMapping result
 -> Knowledge candidate / Knowledge
```

with Context / Provenance traceability.

The first slice does not require structural analogy, abduction, Commons communication semantics, or SIE integration inside CNCF.

## API / runtime expectations

The implementation should provide stable model / wire / persistence behavior consistent with existing CNCF conventions.

At minimum:

- canonical IDs / references;
- serialization / projection required by Component APIs;
- persistence where the corresponding CNCF model is persistent;
- trace references to source Information;
- executable specifications for lifecycle and round-trip behavior;
- no dependency from CNCF to KnowledgeHub, SIE, or application-specific BoKs.

## Non-goals

- Semantic grounding algorithms.
- SIE / BoK integration.
- Knowledge Formation workflow.
- Knowledge Distillation.
- Context Shift.
- Structural Analogy.
- Abductive Projection.
- Knowledge Commons communication extraction.
- Editing Studio book-specific concepts.
- Agriculture- or museum-specific vocabulary.

## Open questions for inventory

- Which Context / Provenance / Evidence types already exist and are canonical?
- Does `Information` already provide a fragment / locator model sufficient for KnowledgeHub?
- Is existing Knowledge support present in Phase 61 migration work and should it be promoted rather than recreated?
- Should Annotation / Interpretation be generic CNCF entities, values attached to Information, or semantic-role Knowledge instances?
- What is the minimum generic mapping-result type that does not import ontology policy into CNCF?
- Does generic CaptureSession belong in CNCF acquisition primitives or KnowledgeHub Knowledge Processing Framework?

## Acceptance direction

Phase 79 is complete when a KnowledgeHub consumer can use CNCF types to represent and persist the generic result side of:

```text
source Information
 -> contextual Interpretation / Mapping
 -> Knowledge
```

with provenance and traceability, while all KnowledgeHub-specific processing remains outside CNCF.
