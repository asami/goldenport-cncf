# RDF Integration Preliminary Design Handling Note

Status: planning note
Date: 2026-04-11

## Scope

This note records how to handle:

- `docs/journal/2026/04/external-preliminary-rdf-knowledge-graph-entity-design-note.md`

That file is an external/AI-generated preliminary design for:

### 8.6 RDF Integration

- RDF-based data representation
- External knowledge graph integration

## Handling Policy

The external preliminary design is useful as a design seed, but it was produced
without direct access to the current CNCF source tree.

Therefore:

- do not treat it as approved design
- do not treat it as current implementation documentation
- verify each assumption against the current entity runtime and CML model
- keep it in journal as preliminary input

## Current Interpretation

The likely minimal direction is:

- keep Entity as the fact-world source of truth
- use an RDF subject URI as an optional knowledge-graph anchor
- keep inferred or context-dependent knowledge outside Entity
- store references to heavy artifacts instead of payloads
- defer RDF triple storage, external KG synchronization, and vector index integration

This is not yet the Phase 8.6 spec.

## Known Risk Areas

The external preliminary design may conflict with current CNCF implementation in
the following areas:

- entity id versus RDF subject identity mapping
- CML syntax for knowledge-related fields
- entity lifecycle/security/metadata flattening decisions
- aggregate/entity persistence boundaries
- output contract and record projection rules
- configuration and variation point conventions
- admin/observability surface conventions

In particular, avoid assuming `Entity.id == RDF.subject` until identity mapping
is explicitly designed. A safer interim rule is:

- Entity has or can expose a reference to an RDF subject URI.

## Development Direction

Before Phase 8.6, only implement a coherent minimal slice if it is clearly
compatible with the current codebase.

Candidate minimal slice:

- model an optional RDF subject URI reference
- expose it as a normal flat field in generated record output
- avoid storing RDF triples, inferred types, embeddings, or context graph output
  directly inside Entity

Everything beyond that should be held for Phase 8.6.

## Decision

Keep the external preliminary design in journal.

Use this handling note as the gate before converting any part of it into
`docs/design`, `docs/spec`, or implementation.
