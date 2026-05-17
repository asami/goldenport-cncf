# Review Notes for KnowledgeSpace Operational Semantic Structure

Date: 2026-05-17

Target document:
`docs/journal/2026/05/knowledge-space-operational-semantic-structure-note.md`

This document records review observations and follow-up design considerations
for the KnowledgeSpace operational semantic structure direction defined in the
Phase 25 KS-09 / KS-10 journal note.

The reviewed note is already directionally strong.
In particular, it successfully preserves the core architectural distinction:

```text
KnowledgeSpace != RDF store
KnowledgeSpace != triple API facade
KnowledgeSpace != ontology engine
KnowledgeSpace == operational semantic runtime
```

This distinction is critical.

The review below focuses mainly on areas that may require future expansion or
careful stabilization as implementation proceeds.

# Overall Assessment

The note correctly positions `KnowledgeNode` and `KnowledgeSpace` as
operational semantic structures rather than RDF wrappers.

The following design decisions are especially important and should remain
stable:

- semantic structuring rather than RDF deserialization;
- operational graph projection rather than triple exposure;
- explicit separation between provider semantic infrastructure and CNCF runtime
  operational structures;
- provenance/evidence as first-class operational concerns;
- separation of Entity identity, RDF subject identity, and KnowledgeNode
  identity.

The current direction is architecturally coherent and avoids several common
failure modes seen in graph abstraction layers.

# Important Strengths

## KnowledgeSpace is not an RDF facade

The strongest architectural decision is that `KnowledgeSpace` does not become:

- an RDF store abstraction;
- a triple query API;
- an ontology runtime;
- a Vector DB facade.

Instead,
it acts as the operational semantic runtime boundary.

This preserves a clean layering:

```text
semantic source systems
    ↓
semantic structuring
    ↓
operational semantic runtime
    ↓
domain logic / AI context / execution
```

This separation is likely essential for long-term maintainability.

## Semantic structuring is correctly centralized

The note correctly frames the loading process as:

```text
raw source facts
  -> semantic interpretation
  -> operational graph normalization
```

rather than:

```text
RDF parser
  -> runtime object graph
```

This distinction is important because RDF ecosystems contain recurring semantic
patterns but not stable operational structure.

The operational model should remain interpretation-driven rather than
serialization-driven.

## Evidence and provenance are first-class

The note correctly treats evidence and provenance as operational structures
rather than optional metadata.

This becomes increasingly important for:

- AI extraction;
- retrieval-augmented generation;
- semantic search;
- externally sourced knowledge;
- contradiction handling;
- explainability.

The distinction between:

```text
fact
truth
evidence
observation
```

must remain explicit.

## Identity separation is correct

The note correctly avoids collapsing:

- Entity identity;
- RDF subject identity;
- KnowledgeNode identity.

This separation is operationally important once multiple semantic providers,
RDF sources, AI extraction pipelines, or external ontologies become involved.

A unified identity space would likely become unstable over time.

# Areas Requiring Future Expansion

## Distinguishing node, relationship, and fact

The current design centers primarily on `KnowledgeNode`.

However,
future semantic processing may require explicit distinction between:

- node;
- relationship;
- fact;
- assertion;
- observation;
- extracted statement.

For example:

```text
Alice worksFor Acme
```

may simultaneously represent:

- a relationship;
- an asserted fact;
- an extracted observation;
- an evidence-backed statement.

This suggests future possible structures such as:

```scala
KnowledgeFact
KnowledgeAssertion
KnowledgeObservation
```

This is not required immediately,
but the model should avoid preventing such future expansion.

## Relationship metadata is likely central

The current design already includes relation metadata,
but future evolution may make relationship metadata one of the most important
runtime structures.

In operational semantic systems:

```text
the edge is often the knowledge
```

Important future metadata may include:

- provenance;
- confidence;
- extraction source;
- extraction method;
- contradiction tracking;
- temporal validity;
- lifecycle state.

The runtime should avoid treating relationships as lightweight helper
structures.

## WorkingSet replacement semantics may become complex

The current semantic structuring pipeline ends with:

```text
-> WorkingSet replacement
```

Operationally,
this area may become significantly more complicated once incremental loading
appears.

Potential future modes include:

- snapshot replacement;
- incremental merge;
- event-applied update;
- streaming semantic updates;
- provenance-preserving merges.

The current note does not need to define this yet,
but the runtime should avoid prematurely assuming full snapshot replacement as
the only model.

## Shape layer may evolve into operational profiles

The note currently positions the shape layer primarily as:

```text
validation/projection guidance
```

This is likely correct initially.

However,
future usage may evolve toward operational projection profiles such as:

- AI context profile;
- MCP projection profile;
- admin/debug projection profile;
- public-safe projection profile;
- compact traversal projection.

This may eventually split into:

```text
shape
+
projection profile
```

as separate concepts.

## Query APIs may become traversal-oriented

The current query direction is already good,
but operational usage suggests that `KnowledgeSpace` may evolve closer to a
semantic traversal runtime than a repository abstraction.

Future APIs may center around:

```scala
neighbors(node, relationType)
expand(node, depth)
evidence(node)
explain(relation)
```

rather than only lookup-oriented repository APIs.

This may become especially important for AI-oriented semantic exploration.

# AI Runtime Implications

One of the most important implications is that `KnowledgeSpace` may become the
primary AI-ready semantic runtime layer inside CNCF.

In practice,
AI systems often require:

- semantic neighborhood expansion;
- provenance-aware retrieval;
- explanation projection;
- context assembly;
- bounded-context semantic extraction.

Raw RDF triples are usually not operationally convenient for these workflows.

KnowledgeNode graphs provide a more natural operational representation.

This direction aligns well with future AI-oriented runtime architecture.

# Suggested Stability Rules

The following architectural rules appear worth preserving explicitly:

- KnowledgeSpace remains component-owned;
- KnowledgeSpace does not replace TagSpace;
- KnowledgeSpace does not own RDF DB or Vector DB responsibilities;
- semantic structuring remains provider/application responsibility;
- operational semantic structures remain distinct from raw source structures;
- provenance/evidence is never discarded during projection;
- domain logic must be able to use KnowledgeSpace directly without parsing RDF
  triples.

# Final Assessment

The current direction is strong.

Most importantly,
the design successfully avoids collapsing into:

- RDF abstraction APIs;
- graph database facades;
- ontology runtime leakage;
- triple-centric programming models.

Instead,
it preserves the intended operational semantic layering:

```text
semantic sources
    ↓
semantic structuring
    ↓
operational knowledge graph
    ↓
domain logic / AI context / execution
```

This appears to be a sound foundation for KS-09 and KS-10 evolution.
