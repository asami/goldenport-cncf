# Knowledge Node, Relationship, and Evidence Model

This note records the Phase 25 KS-04 model-boundary decision. It is a planning
note, not a runtime schema or storage implementation.

## Purpose

KS-04 defines the minimum knowledge-structure model vocabulary that CNCF should
own before query/projection work starts.

The model boundary is intentionally generic. It must support `textus-sie` as a
driver while keeping RDF DB, Vector DB, embedding, retrieval ranking, and
SIE-specific MCP tool behavior outside CNCF core.

## TagSpace and KnowledgeSpace Separation

`TagSpace` and `KnowledgeSpace` are separate runtime/model boundaries.

`TagSpace` owns Tag management:

- Tag master data.
- strict tree hierarchy.
- Tag path and resident `TagTree` lookup.
- `TagAttachment` classification links.
- existing Tag navigation and descendant-search semantics.

`KnowledgeSpace` owns knowledge projection and graph behavior:

- knowledge nodes;
- knowledge relationships;
- evidence and provenance links;
- external identifier mappings;
- traversal/query/explanation inputs.

KS-04 does not merge Tags into knowledge records. A Tag may be projected as a
`KnowledgeNode`, and TagAttachment may provide evidence for a knowledge
relationship, but the Tag remains TagSpace-managed.

## KnowledgeSpace Direction

`KnowledgeSpace` is expected to play a role similar to `EntitySpace`: it
provides a CNCF core boundary for knowledge projection, lookup, traversal, and
query support while keeping provider-specific storage outside the core model.

The likely runtime shape is a memory-resident WorkingSet backed by canonical
records and projection inputs. Candidate WorkingSet contents:

- node index;
- relationship index;
- external identifier mapping;
- evidence/provenance lookup;
- traversal and query projection cache.

Canonical records may later be backed by EntityStore, Association, or dedicated
knowledge records. KS-04 does not choose storage. RDF DB and Vector DB remain
SIE/provider-owned systems whose contents can be projected into
KnowledgeSpace.

## KnowledgeNode

`KnowledgeNode` is a graph-level projection boundary.

Candidate node sources:

- CNCF Entity.
- CNCF Tag.
- concept.
- knowledge source.
- source document or chunk.
- external subject such as an RDF subject URI.

`KnowledgeNode` is not a synonym for every Entity. It is the representation
used when an Entity, Tag, concept, source, or external subject participates in a
knowledge graph projection.

## Entity and Knowledge Binding

CNCF must support both of these cases:

- an Entity is projected as a `KnowledgeNode`;
- an Entity remains an ordinary business Entity and is linked to knowledge
  nodes, relationships, evidence, or external subjects that describe it.

The second case is important for application logic. Business logic often needs
to load an Entity from `EntitySpace` / EntityStore, then inspect knowledge about
that Entity from `KnowledgeSpace` without turning the Entity itself into a graph
record.

Entity-to-knowledge binding should therefore be explicit. Candidate binding
inputs are:

- CNCF Entity id and entity kind/name;
- Entity version or DB row reference;
- Association-backed relationship from an Entity to a knowledge target;
- Evidence/source reference pointing from a knowledge node or relationship back
  to the Entity;
- external identifier mapping when the same real-world subject is represented
  by both an Entity and RDF/provider identifiers.

Do not assume `Entity.id == KnowledgeNode.id`, and do not require every Entity
to have a KnowledgeNode counterpart. A KnowledgeNode can represent knowledge
about an Entity, while the Entity remains the authoritative business object.

## KnowledgeRelationship

`KnowledgeRelationship` is a typed graph edge between knowledge nodes.

Candidate relationships:

- Tag graph extension edges from KS-03, such as `additional-parent`,
  `related`, `alias`, `same-as`, `broader`, `narrower`, and `see-also`.
- Entity-to-Entity semantic relationships.
- Entity-to-external-subject mappings.
- Source-to-node evidence relationships.
- RDF-style predicate relationships projected into CNCF vocabulary.

Existing CNCF `Association` is a storage/runtime foundation for
entity-to-entity links. It is not automatically the same thing as a knowledge
relationship. A future implementation may use Association-backed storage for
some relationship kinds, but KS-04 does not decide storage.

## Evidence

`Evidence` is traceable support for a knowledge fact, node, or relationship.

Candidate evidence sources:

- Entity record.
- DB row or entity version.
- source document.
- source chunk.
- operation context.
- external URI.
- RDF statement or external graph fact, when projected by a driver.

Evidence is not a retrieval score. Retrieval scores may be evidence metadata,
but they do not replace traceability back to source material or generated
facts.

## Provenance

`Provenance` records where a node, relationship, or evidence item came from and
how it was generated.

Candidate provenance fields:

- origin.
- owner or owning component/subsystem.
- generated-by operation or process.
- lifecycle/state.
- confidence.
- generated-at or observed-at context.

Provenance is not authorization policy. Authorization may use provenance as
input later, but KS-04 keeps them separate.

## External Identifier Mapping

External identifiers connect CNCF identity to external graph or provider
identity.

Rules:

- Do not assume `Entity.id == RDF subject URI`.
- Keep CNCF Entity identity and external subject identity distinct.
- Mapping must be explicit when a CNCF Entity, Tag, or knowledge node is linked
  to an RDF subject, provider id, URI, or other external identifier.
- Entity-to-knowledge binding is also explicit; knowledge about an Entity can
  be reached through evidence/source reference, relationship projection, or
  external identifier mapping without making the Entity id the knowledge id.
- Identity mapping is a boundary that SIE can use when projecting RDF DB and
  Vector DB results into CNCF-visible knowledge structures.

## KS-04 Decisions

- Do not add concrete runtime types or storage implementation in KS-04.
- Do not change `Tag.parentId`, `TagTree`, `TagAttachment`, or existing
  Association behavior.
- Do not merge TagSpace and KnowledgeSpace.
- Keep Tags TagSpace-managed; project them into KnowledgeSpace only when a
  knowledge graph view needs them.
- Treat KnowledgeSpace as the future CNCF boundary for memory-resident
  knowledge WorkingSet, traversal, and projection.
- Support Entity-to-knowledge binding as a first-class query/projection use
  case, separate from projecting an Entity itself as a KnowledgeNode.
- Treat `KnowledgeRelationship` as the future graph layer concept that can
  receive KS-03 Tag graph edge kinds.
- Send query, traversal, search expansion, and admin/API projection behavior to
  KS-05.
- Keep SIE-specific RDF DB, Vector DB, embedding, ranking, and MCP tool
  semantics driver/application/provider-owned.
