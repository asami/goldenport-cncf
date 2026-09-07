# SimpleModeling Model Integration Boundary in CNCF

Status: clarification note
Date: 2026-09-05

## Purpose

This note records CNCF's role in the SimpleModeling cross-model integration architecture.

The semantic integration specification itself belongs to SimpleModeling.org. CNCF provides reusable implementation/runtime primitives where they are genuinely generic; it does not become the authority for SimpleModeling Term semantics or cross-model meaning.

## Existing CNCF principle

CNCF's component model keeps domain logic and domain meaning separate from runtime infrastructure and execution orchestration.

That principle applies directly to SimpleModeling model integration.

CNCF may transport references, evidence, resources, and operations, but it should not interpret what a SimpleModeling Term means or decide whether a CML model element is semantically equivalent to an RDF concept.

## What belongs above CNCF

The following semantics belong to SimpleModeling.org or its domain modules:

- canonical Term identity and definition;
- Object Model / Knowledge Model / Literate Model integration meaning;
- Term-to-ModelElement relation semantics;
- BoK-local RDF concept meaning;
- external ontology mapping semantics;
- Glossary viewpoint semantics;
- BoK and design review rules.

Textus BoK and Textus CBD Support may implement and refine these meanings under the upstream SimpleModeling specification.

## What may belong in CNCF

Generic implementation primitives may live in CNCF when proven reusable across components, for example:

- resource references;
- artifact/model references that remain domain-neutral;
- evidence/provenance transport;
- operation dispatch;
- authorization;
- persistence and lifecycle support;
- generic external-resource references;
- cross-component handoff/correlation;
- Web/MCP/runtime exposure mechanisms;
- change-workflow primitives that are not specific to CML, BoK, or GitHub.

The criterion is whether the primitive remains useful without importing SimpleModeling-specific vocabulary.

## Reference semantics

A generic reference contract should identify a target without embedding domain interpretation.

For example, CNCF may eventually provide a generic structured reference usable for a model element or semantic resource, while Textus BoK interprets that reference as the target of a Term binding.

Conceptually:

```text
Textus BoK / CBD Support
    domain-specific meaning
            |
            v
Generic CNCF reference/evidence primitive
            |
            v
runtime/resource infrastructure
```

CNCF should not define fields such as `termId` as framework-level semantics unless a future use case proves that the concept is broader than SimpleModeling BoK terminology.

## RDF and semantic infrastructure

CNCF may host or support components such as SIE through normal component/runtime mechanisms, but generic semantic federation remains separate from BoK meaning.

A semantic candidate, embedding match, RDF resource, or provider result must not become a factual cross-model binding merely because it passes through CNCF infrastructure.

## Relationship to Cozy

Cozy may publish CML model metadata containing `termId`, model-element metadata, `glossaryPath`, and candidate RDF references.

CNCF treats these as component/domain metadata. It does not assign their semantic meaning.

## Relationship to Textus BoK

Textus BoK owns the BoK-side typed interpretation and may use CNCF references/resources/evidence to realize:

- Term-centered navigation;
- model-element bindings;
- BoK-local RDF nodes;
- external semantic mappings;
- review evidence.

The fact that CNCF carries these objects does not transfer ownership of their semantics to CNCF.

## Relationship to Textus Change Management

`textus-change-management` is currently the proving ground for generic improvement workflows.

If repeated implementation demonstrates reusable primitives such as durable artifact references, approval gates, resumable lifecycle execution, or external-action correlation, those may later be extracted into CNCF.

The semantic content of a candidate CML patch, Knowledge Diff, Design Diff, or Term change remains outside CNCF.

## Guiding rule

> CNCF enables integration and interoperability, but domain modules and SimpleModeling.org remain the semantic authorities.

This note should be revisited only when concrete implementations reveal a generic primitive that belongs in the framework.
