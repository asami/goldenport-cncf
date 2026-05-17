# KnowledgeSpace Operational Semantic Structure Note

Date: 2026-05-17

Source note:
`docs/notes/knowledge-node-operaitonal-semantic-structure.md`

Review note:
`docs/journal/2026/05/review-knowledgespace-operational-semantic-structure.md`

This journal entry records a Phase 25 design interpretation of
`KnowledgeNode` as an operational semantic structure and derives the intended
`KnowledgeSpace` runtime shape. It is journal evidence for KS-09 / KS-10 work,
not yet a normative runtime schema.

## Key Reading

The source note makes an important distinction:

```text
RDF graph
  -> semantic structuring
  -> KnowledgeNode graph
  -> domain / application logic
```

The central decision is that `KnowledgeNode` is not an RDF wrapper and not an
RDF ORM. RDF remains a flexible semi-structured knowledge representation.
`KnowledgeNode` is the operational semantic structure that makes that knowledge
usable by program logic.

This means `KnowledgeSpace` should not become an RDF store, triple store,
ontology reasoner, or Vector DB facade. It should be the CNCF runtime boundary
where structured operational knowledge is loaded, indexed, queried, projected,
and connected to business logic.

## KnowledgeSpace Position

The intended runtime layering is:

```text
RDF / Vector DB / Document / AI extraction / Citation graph
  -> provider or application semantic structuring
  -> KnowledgeSpace WorkingSet
  -> View / Aggregate / Entity logic / AI context / MCP service
  -> execution
```

Provider/application code owns RDF DB, Vector DB, embedding, inference,
ranking, extraction, and source-specific interpretation.

CNCF `KnowledgeSpace` owns:

- operational knowledge nodes;
- operational relationships;
- evidence and provenance;
- source and external identifier mapping;
- entity-to-knowledge binding;
- in-memory WorkingSet indexes;
- query/projection surfaces usable by application logic, admin/debug, AI
  context construction, and MCP-facing services.

`TagSpace` remains separate. Tags may be projected into `KnowledgeSpace` only
when a graph projection needs them.

## WorkingSet Structure

The KS-08 skeleton currently has nodes, relationships, evidence, provenance,
external identifiers, and basic indexes. The source note suggests the next
structural expansion:

- Node identity layer:
  - CNCF knowledge id;
  - external identifiers such as URI, CURIE, blank node id, generated id, or
    provider id;
  - explicit mapping instead of assuming `Entity.id == RDF subject URI`.
- Typing layer:
  - node kind for coarse operational category;
  - multiple semantic types from RDF/OWL/schema vocabularies;
  - shape/profile reference when operational constraints are known.
- Property layer:
  - typed attributes, not only string attributes;
  - language, datatype, source, confidence, and metadata when available;
  - redaction/confidentiality policy before projection to diagnostics or AI
    context.
- Relation layer:
  - typed edges as first-class records;
  - relation metadata such as source, confidence, language, evidence,
    provenance, and original predicate;
  - traversal indexes by source, target, kind, and semantic type.
- Metadata layer:
  - label, alternative labels, description;
  - same-as / equivalent-to / external reference links;
  - source and generation context.
- Evidence/provenance layer:
  - Entity record, DB row/version, document, chunk, RDF statement, operation
    context, extraction run, and external URI;
  - generated-by, owner/origin, confidence, lifecycle/state, and observation
    time.
- Shape layer:
  - operational expectations similar to SHACL or OWL restrictions, but used as
    CNCF runtime validation/projection guidance rather than as an ontology
    engine.

This implies that the current `Map[String, String]` attribute shape is a
minimum placeholder. KS-09/KS-10 should avoid overfitting it as the final
contract.

## Entity-To-Knowledge Binding

An important use case is linking an existing business Entity to knowledge about
that Entity.

There are two distinct cases:

- the Entity is projected as a `KnowledgeNode`;
- the Entity remains the authoritative business object and is linked to
  knowledge nodes, relationships, evidence, or external subjects that describe
  it.

The second case is the more operationally important one. Business logic should
be able to:

1. load an Entity from `EntitySpace` or EntityStore;
2. query `KnowledgeSpace` for knowledge related to that Entity;
3. apply business rules over the Entity plus the operational knowledge graph.

The binding should be explicit through one or more of:

- Entity id and entity kind/name;
- Entity version or DB row reference;
- Association-backed link;
- evidence/source reference;
- external identifier mapping;
- provider-specific subject mapping.

Do not collapse Entity identity, RDF subject identity, and KnowledgeNode
identity into one id space.

## Semantic Structuring Pipeline

The KnowledgeSpace load path should be thought of as semantic structuring, not
serialization:

```text
raw source facts
  -> source-specific interpretation
  -> normalized nodes / relationships / attributes / metadata
  -> evidence / provenance attachment
  -> shape/profile validation or classification
  -> WorkingSet replacement
```

For RDF input, structuring maps recurring semantic patterns into the operational
model:

- `rdf:type` -> node semantic types;
- `rdfs:subClassOf`, `skos:broader`, `skos:narrower` -> hierarchy or semantic
  relationship edges;
- `owl:ObjectProperty` predicates -> relationship kinds;
- `owl:DatatypeProperty` predicates -> typed attributes;
- `schema.org`, Dublin Core, and `prov:*` -> metadata, evidence, and
  provenance.

For Vector DB retrieval, structuring should not make retrieval chunks the
KnowledgeNode schema. Retrieval results should contribute evidence, confidence,
source/chunk references, and candidate node/relationship context.

For AI extraction, extracted facts should be loaded with provenance and
confidence, and should remain distinguishable from curated or RDF-backed facts.

## Fact, Assertion, and Observation Boundary

The review note points out that future semantic processing may need a more
explicit distinction between:

- node;
- relationship;
- fact;
- assertion;
- observation;
- extracted statement.

This distinction is not required in the KS-08 skeleton, but the model should
not block it. A statement such as:

```text
Alice worksFor Acme
```

may be represented as:

- a relationship between two knowledge nodes;
- an asserted fact;
- an extracted observation;
- an evidence-backed statement;
- a time-bound or source-bound semantic claim.

The immediate design implication is that `KnowledgeRelationship` must not be
treated as a lightweight pointer. In many knowledge systems, the edge carries
the operational fact. Relationship metadata therefore needs room for evidence,
provenance, confidence, extraction method, temporal validity, lifecycle state,
and contradiction tracking.

Possible future structures include `KnowledgeFact`, `KnowledgeAssertion`, or
`KnowledgeObservation`, but Phase 25 should introduce them only if KS-09/KS-10
needs a concrete distinction.

## WorkingSet Update Semantics

The current KS-08 implementation uses full WorkingSet replacement. That is a
reasonable skeleton, but it should not become the only assumed load model.

Future provider/runtime paths may require:

- full snapshot replacement;
- incremental merge;
- event-applied updates;
- streaming semantic updates;
- provenance-preserving merge behavior.

For now, replacement should remain the minimal safe contract. KS-09/KS-10
should keep query/projection APIs independent from the update strategy, so a
future incremental WorkingSet can be added without changing operational query
semantics.

## Shape and Projection Profiles

The source note introduces `KnowledgeShape` as validation/projection guidance.
The review note adds that shape may eventually split from projection profile.

The distinction is useful:

- shape describes expected operational semantic structure;
- projection profile controls what is exposed for a particular audience or
  runtime use case.

Candidate projection profiles include:

- AI context profile;
- MCP projection profile;
- admin/debug projection profile;
- public-safe projection profile;
- compact traversal projection.

This reinforces the Phase 24 observability rule that raw or sensitive payloads
must not leak through debug or AI surfaces. KnowledgeSpace projection should
eventually be profile-aware.

## Query/Projection Implications

KS-09 should expose queries over operational knowledge, not over raw triples:

- node lookup by id, external identifier, type, shape, source, or Entity
  binding;
- relationship traversal by source, target, kind, type, depth, and limit;
- attribute/metadata projection with typed values;
- evidence/provenance projection for a node, relationship, or fact;
- Entity detail enrichment from KnowledgeSpace;
- explanation projection for AI and MCP tools;
- WorkingSet status, counts, source freshness, and failed-load diagnostics.

The review note suggests that the API should evolve toward semantic traversal
rather than only repository-style lookup. Candidate traversal-oriented
operations include:

```scala
neighbors(node, relationType)
expand(node, depth)
evidence(node)
explain(relation)
```

This aligns with AI-oriented semantic exploration, where neighborhood
expansion, relation explanation, evidence navigation, and bounded context
assembly are more useful than raw triple queries.

Admin/debug projection should show the operational graph and links back to
evidence/provenance. It should not expose raw RDF or unbounded source payloads
as the primary display.

## AI Runtime Implications

KnowledgeSpace is likely to become the AI-ready semantic runtime layer inside
CNCF. It should support:

- semantic neighborhood expansion;
- provenance-aware retrieval;
- explanation projection;
- context assembly;
- bounded-context semantic extraction;
- citation and evidence navigation.

Raw RDF triples are not operationally convenient for these workflows.
KnowledgeNode graphs provide the structure needed for domain logic and AI
context construction while preserving links back to RDF, Vector DB, document,
or extraction sources.

## Operational Guardrails

The following guardrails should hold:

- KnowledgeSpace is component-owned, matching EntitySpace / AggregateSpace /
  ViewSpace management style.
- Cross-component knowledge aggregation is a query/projection layer, not a
  subsystem-global mutable KnowledgeSpace.
- KnowledgeSpace does not replace TagSpace.
- KnowledgeSpace does not own RDF DB, Vector DB, embedding, ranking, or MCP
  tool semantics.
- KnowledgeSpace should carry enough structure for domain logic to use it
  directly without parsing RDF triples.
- RDF/OWL semantics should be preserved as source/evidence/provenance and
  operational relationship/type metadata, not discarded during projection.
- Relationships are first-class operational knowledge and must retain
  evidence/provenance, confidence, and source metadata when available.
- Full snapshot replacement is the KS-08 skeleton behavior, not a permanent
  constraint on future incremental or event-applied WorkingSet updates.
- Shape and projection profile should remain distinguishable concepts as
  KnowledgeSpace surfaces mature.

## Design Direction For KS-09 / KS-10

KS-09 should define the query/projection API around this operational shape.
The first practical surface should include:

- status/counts;
- node lookup;
- relationship traversal;
- external identifier lookup;
- Entity-to-knowledge lookup;
- evidence/provenance projection;
- explanation projection.

The surface should be traversal-friendly and should avoid exposing raw RDF or
Vector DB structures as the primary API. Provider-specific source structures
remain behind SIE/application/provider boundaries.

KS-10 should then connect `textus-sie` provider behavior by producing
KnowledgeSpace snapshots from RDF DB / Vector DB / semantic retrieval outputs.
The driver should prove that RDF knowledge can be transformed into operational
KnowledgeNodes and that business logic can apply rules to those nodes and their
relationships.
