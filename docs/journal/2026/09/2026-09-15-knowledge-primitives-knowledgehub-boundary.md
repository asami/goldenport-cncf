# CNCF Knowledge Primitives / KnowledgeHub Framework Boundary

status=current-design-decision
recorded_at=2026-09-15
phase=[Phase 73 - Knowledge Primitives Foundation](../../phase/phase-73.md)
implementation_note=[CNCF Knowledge Primitives Provisional Specification](../../notes/knowledge-primitives-provisional-specification.md)

## Context

KnowledgeHub Phase 1 requires a server-side model for Information -> Interpretation / Semantic Mapping -> Knowledge.

Two ownership options were considered:

1. place the knowledge model in the KnowledgeHub component;
2. extend CNCF, where canonical `Information` already exists, with reusable knowledge primitives.

The selected direction is to split representation from processing.

## Decision

**CNCF owns domain-independent knowledge representation primitives. KnowledgeHub owns the Knowledge Processing Framework.**

```text
CNCF
  reusable state / result model

KnowledgeHub
  reusable knowledge-processing behavior

Reference Applications
  application / domain usage
```

This preserves CNCF independence while preventing KnowledgeHub from becoming the canonical owner of generic Information / Knowledge concepts.

## CNCF responsibility

CNCF should provide reusable concepts when inventory confirms that they are not already represented canonically:

- Information and Information references / fragments
- Context
- Provenance
- Evidence
- Interpretation result
- Semantic Mapping result
- Knowledge
- generic semantic role / claim role where justified

The exact type structure is not frozen by this journal. Phase 73 begins with inventory and promotes / reuses existing types before adding new ones.

## KnowledgeHub responsibility

KnowledgeHub remains the owner of the **Knowledge Processing Framework**, including:

- SIE-managed Knowledge as Semantic Context
- Semantic Grounding
- Knowledge Formation
- Knowledge Distillation
- Knowledge Federation
- Context Shift / Reframing
- Structural Analogy discovery
- Abductive Projection
- Knowledge Gap exploration
- Knowledge Creation History orchestration
- Domain Profile composition

A process may create a result represented by CNCF. For example:

```text
KnowledgeHub SemanticGrounding
       |
       v
proposal / human review
       |
       v
CNCF SemanticMapping result
```

## Why this boundary

### Information continuity

CNCF already owns canonical Information. Knowledge should extend that runtime model rather than create a KnowledgeHub-only parallel model.

### Reuse outside KnowledgeHub

Observation, Evidence, Hypothesis-role Knowledge, Context, and Provenance are useful to agriculture, CBD support, and future Components even when KnowledgeHub is absent.

### Framework identity

KnowledgeHub should remain recognizable as a Knowledge Processing / Creation Framework. Moving processing such as grounding, distillation, context shift, or abduction into CNCF would weaken this boundary and make CNCF knowledge-domain-specific.

### Dependency direction

The desired dependency is:

```text
Reference Application
        |
        v
KnowledgeHub
        |
        v
CNCF
```

CNCF must not depend on KnowledgeHub, SIE, BoK implementations, or application-specific knowledge domains.

## Result / process distinction

Use this as the default placement test:

> If the concept represents reusable application state or a committed result that remains meaningful without KnowledgeHub, it is a CNCF candidate. If it represents how KnowledgeHub interprets, forms, transforms, discovers, or creates knowledge, it belongs in KnowledgeHub.

Examples:

| Concept | Owner direction |
|---|---|
| Information | CNCF |
| Knowledge | CNCF |
| generic Context | CNCF |
| Provenance | CNCF |
| Evidence | CNCF |
| committed SemanticMapping | CNCF candidate |
| SemanticContext backed by SIE Knowledge | KnowledgeHub |
| SemanticGrounding | KnowledgeHub |
| KnowledgeFormation | KnowledgeHub |
| ContextShift | KnowledgeHub |
| StructuralAnalogy | KnowledgeHub |
| AbductiveProjection | KnowledgeHub |

## Phase 1 impact

KnowledgeHub Phase 1 server-side vertical slice should use the following division:

```text
Flutter / Editing Studio
        |
        v
KnowledgeHub Component
  capture / formation orchestration
        |
        v
CNCF Information
        |
        v
KnowledgeHub Semantic Grounding
        |
        +---- SIE Semantic Context
        |
        v
CNCF Interpretation / SemanticMapping / Knowledge
```

The exact ownership of CaptureSession / CaptureItem remains open. Their use across books, assets, and agriculture suggests generality, but acquisition orchestration is currently a KnowledgeHub concern. Do not move them into CNCF without an explicit inventory decision.

## Relationship to Phase 61

Phase 61 canonicalized Information runtime behavior and included Information / Knowledge lifecycle migration work. Phase 73 must inspect and reuse that result before introducing new types.

The new phase is therefore an extension of canonical Information semantics, not a restart of the Information model.

## Development sequencing

Phase 73 is planned as a separately schedulable consumer-driven phase for KnowledgeHub Phase 1. Existing planned Phase 62-72 work is not silently renumbered or superseded.

The implementation should be sliced so each subphase can remain within approximately six hours of focused implementation where practical, consistent with current CNCF phase planning practice.

## Follow-up

After CNCF primitives are fixed, KnowledgeHub Phase 1 should define its own notes / journal / implementation phase for:

- SIE Semantic Context;
- Semantic Grounding;
- Knowledge Formation orchestration;
- Flutter / server contract;
- Editing Studio minimum view.
