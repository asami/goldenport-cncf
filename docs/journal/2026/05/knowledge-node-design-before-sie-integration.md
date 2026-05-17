# KnowledgeNode Design Before SIE Integration

Date: 2026-05-18

This journal entry records the design direction for `KnowledgeNode` before
Phase 25 KS-10 hardening. It is a working design note, not yet the normative
runtime schema.

## Why This Note Exists

`KnowledgeSpace` is the projection layer that turns the RDF / external
knowledge world into an object model that CNCF domain logic can operate.

The input side may be semi-structured RDF, external knowledge graphs, source
documents, chunks, embeddings, and provider-specific knowledge records. The
output side must be structured CNCF objects:

```text
semi-structured RDF / external knowledge
  -> CNCF-defined KnowledgeNode / KnowledgeRelationship model
  -> memory-resident KnowledgeWorkingSet inside KnowledgeSpace
  -> domain logic, query/projection, admin/debug, MCP publication
```

This is the core of Phase 25. CNCF is not trying to store RDF as-is or expose
RDF DB behavior as the domain programming model. CNCF needs a KnowledgeNode
object model that makes accepted knowledge structures directly operable by
business logic.

The runtime shape is:

```text
KnowledgeSpace
  owns KnowledgeWorkingSet
    owns KnowledgeFrame(s)
    holds KnowledgeNode / KnowledgeRelationship / Evidence / Provenance
    builds indexes and direct node projections
    exposes programming-oriented lookup/traversal APIs
```

`KnowledgeWorkingSet` is not only a cache. It is the memory-resident
operational view of the structured knowledge model. Domain logic should be
able to use it like the other CNCF Spaces: load the component-owned space,
lookup nodes, follow canonical node fields, traverse selected relationships,
and obtain evidence/provenance when needed.

`KnowledgeFrame` sits between `KnowledgeWorkingSet` and individual
`KnowledgeNode` / `KnowledgeRelationship` records. A frame is a purpose- or
focus-oriented unit of structured knowledge inside the WorkingSet.

Examples:

- SIE search result frame.
- Entity-centered knowledge frame.
- query/explanation frame.
- source document/chunk frame.
- Tag/classification neighborhood frame.
- manually curated frame.

External RDF DB / Vector DB providers can remain authoritative for their own
storage and retrieval behavior, but domain logic should operate against the
WorkingSet projection when it needs programmatic knowledge manipulation.

Knowledge can enter `KnowledgeSpace` through multiple routes:

- SIE semantic retrieval over RDF DB / Vector DB / hybrid providers.
- DB stored Entity projection.
- TagSpace / Association projection.
- component-local import or batch materialization.
- admin/API/manual curation.
- external knowledge graph import.
- generated or extracted knowledge from application operations.

SIE is the current development driver and a concrete example, not the only
input route.

Entity projection is especially important. A corresponding Entity may provide
knowledge that is not explicitly written in RDF. For `SimpleEntity`, CNCF
already knows structure, intended usage, lifecycle, tags, associations,
attributes, and model-level context. That information can produce richer
KnowledgeNode attributes than a raw RDF import alone.

Example SIE-driven usage:

```text
1. SIE searches RDF DB / Vector DB / hybrid retrieval providers.
2. SIE returns relevant knowledge candidates, source chunks, predicates,
   evidence, and retrieval context.
3. CNCF projects those results into KnowledgeNode / KnowledgeRelationship /
   Evidence / Provenance objects.
4. CNCF groups the projected result set into a KnowledgeFrame.
5. KnowledgeSpace stores the frame inside a KnowledgeWorkingSet.
6. Domain logic traverses the frame and WorkingSet:
   - read canonical node fields;
   - follow translations, localized versions, alignments, identity links,
     classifications, part-whole links;
   - traverse KnowledgeRelationships;
   - inspect evidence/provenance;
   - run application-specific decisions over the structured graph.
```

In this flow, SIE performs semantic retrieval, while CNCF provides the
programming model for operating on the retrieved knowledge. The search result
is not only response data; it becomes an in-memory KnowledgeSpace WorkingSet
frame that domain logic can inspect and transform.

KS-08 and KS-09 introduced the minimum `KnowledgeSpace` skeleton and projection
surface. That skeleton intentionally used loose fields such as `kind: String`,
`label: Option[String]`, and `attributes: Map[String, String]` so CNCF could
start exercising the runtime boundary.

That shape is not strong enough to connect `textus-sie` safely. If provider
integration and model hardening happen together, RDF DB / Vector DB behavior
will obscure core CNCF model decisions. KS-10 should therefore harden
`KnowledgeNode` and related structures before SIE provider/runtime wiring.

## Current Skeleton

The current model is:

```scala
final case class KnowledgeNode(
  id: KnowledgeNodeId,
  kind: String,
  label: Option[String],
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier],
  provenanceId: Option[KnowledgeProvenanceId],
  attributes: Map[String, String]
)
```

This is sufficient for a memory-resident `KnowledgeWorkingSet`, basic lookup,
external identifier matching, and admin/debug projection. It is not sufficient
as the operational semantic model.

## Node Identity

`KnowledgeNodeId` is the CNCF internal node id inside a `KnowledgeSpace`.

It must not be treated as any of the following:

- RDF subject URI.
- RDF blank node label.
- Entity id.
- Tag id.
- Vector DB id.
- provider-local document or chunk id.

Those identifiers belong in explicit external identifier or source-reference
structures. The reason is operational stability: the same real-world subject
may appear through multiple RDF sources, multiple provider ids, one or more
business Entities, and generated or extracted facts. Collapsing all of those
into `KnowledgeNodeId` would make identity unstable.

KS-10 should keep `KnowledgeNodeId` small and CNCF-local, and add helper
constructors only where the generation policy is explicitly CNCF-owned. RDF
subject mapping should remain external:

```text
KnowledgeNodeId("node-123")
ExternalKnowledgeIdentifier(system="rdf", kind="subject", value="https://...")
```

Entity binding should likewise remain explicit:

```text
ExternalKnowledgeIdentifier(system="cncf.entity", kind="<entity-name>", value="<entity-id>")
```

## Node Category

`kind: String` is the weakest part of the current model.

KS-10 should replace it with a typed provisional representation named
`KnowledgeNodeCategory`. The name is intentional: this is a coarse operational
category for WorkingSet, admin/debug, and projection behavior. It is not the
semantic type system.

Candidate core categories:

- `entity`: projected business Entity or Entity-related node.
- `tag`: TagSpace-managed Tag projected into KnowledgeSpace.
- `concept`: operational concept.
- `source`: knowledge source.
- `document`: source document.
- `chunk`: document chunk or retrieval unit.
- `external-subject`: provider/RDF subject represented operationally.
- `generated`: generated or extracted node.

This should not become an unbounded string again. A likely shape is a small
core vocabulary plus an extension/custom case with explicit namespace.
The category list should stay small and provisional. Domain decisions should
use `KnowledgeSemanticType` and canonical node fields, not category alone.

## Labels

`label: Option[String]` is only a placeholder.

If it is display-facing, it should be localizable. If it is RDF-derived, it
must also support language tags such as `rdfs:label` values. A single String
cannot represent:

- default label;
- language-specific labels;
- alternative labels;
- source-specific labels;
- generated labels with confidence/provenance.

KS-10 should introduce an I18n/localizable label shape. It can remain compact,
but it should not force all labels into one unqualified string.

Candidate shape:

```text
KnowledgeLabel(
  default: Option[String],
  localized: Map[LocaleTag, String],
  alternatives: Vector[KnowledgeLabelValue]
)
```

The exact type can follow existing CNCF/simplemodeling label conventions where
available. The important rule is that display text is not the semantic id.

## RDF Node Name

RDF node name is a core knowledge representation when the node is RDF-backed.
It should not be reduced to a generic external identifier.

`KnowledgeNodeId` remains necessary because not every knowledge node has an
RDF node, and because CNCF needs a local runtime id for WorkingSet indexing,
projection, and business logic. But when a node is produced from RDF, the RDF
node name is part of the node's canonical knowledge content.

Candidate shape:

```scala
final case class KnowledgeNode(
  id: KnowledgeNodeId,
  rdfNode: Option[RdfNodeName],
  category: KnowledgeNodeCategory,
  ...
)
```

`RdfNodeName` should be able to represent:

- IRI / URI node.
- blank node, when meaningful in the source context.
- literal node only if CNCF decides that literal-as-node is a valid projection
  shape for a specific model.
- CURIE or compact name as a projection of an IRI, not as the only stored
  identity.

The important distinction is:

```text
KnowledgeNodeId = CNCF runtime identity
RdfNodeName     = RDF knowledge identity/content
```

`ExternalKnowledgeIdentifier` remains useful for provider ids, Entity ids, Tag
ids, alternate RDF/source ids, and cross-system lookup, but RDF node name is
important enough to be a direct `KnowledgeNode` field when present.

## External Identifiers

External identifiers are the boundary between CNCF identity and non-primary
outside identity. They should cover:

- RDF subject URI.
- CURIE.
- blank node reference, when stable enough for the source context.
- provider id.
- Entity id binding.
- Tag id binding.
- Vector DB id or document/chunk id, if it identifies an external object.

The current `ExternalKnowledgeIdentifier.key` is a display/index projection,
not the semantic identity contract. The structured triple
`system/kind/value` remains canonical.

KS-10 should decide whether external identifiers need richer source context,
such as provider name, source graph, dataset, or namespace. If the same RDF URI
can appear in multiple operational source contexts with different trust or
provenance, the identifier alone is not enough.

After adding direct `rdfNode`, RDF identifiers in `externalIdentifiers` should
be used for alternate RDF names, source-specific aliases, provider ids, or
cross-reference lookup, not as the only place where the primary RDF node name
is stored.

## Similarity / Distance Information

Similarity / distance representation is knowledge information.

`embedding` is a current technical method. `vector` and `index` are
implementation details. The node-side semantic attribute should express the
purpose: this node has a representation that can be used for similarity /
distance search.

The earlier "reference only" framing is too weak. If a node, relationship, or
source chunk is represented for similarity search, the representation context
says something operationally important about how the knowledge is used for
semantic retrieval. CNCF should treat similarity representation information as
first-class knowledge metadata.

Raw vector values may still be too large or sensitive for admin/debug
projection, but the model should be able to carry representation metadata,
implementation references, and value policy:

Candidate similarity representation fields:

- representation method, such as embedding;
- model id/name/version;
- similarity metric or distance metric;
- representation context: what text/graph/source was represented and under what
  projection profile;
- vector store/provider id, when implemented by a vector store;
- collection/index name, when indexed externally;
- vector id or search index id, when applicable;
- indexed-at timestamp;
- source document/chunk reference;
- optional vector value or vector payload reference, when the runtime policy
  allows it;
- status such as indexed, stale, missing, failed.

Search-time similarity scores belong to query result/projection records, not
to the node itself. A node can have similarity representations; a query result
can have scores/distances.

## Attributes and Semantic Types

`attributes: Map[String, String]` is another skeleton placeholder.

Operational semantic nodes need typed values and semantic type information:

- RDF/OWL/schema types;
- shape/profile references;
- typed property values;
- language/datatype/source metadata;
- confidence and provenance on extracted values;
- confidentiality/redaction hints before admin, diagnostics, MCP, or AI
  context projection.

KS-10 must define the CNCF-owned knowledge structure carried by
`KnowledgeNode`. This is the core of Phase 25.

That does not mean CNCF should become a complete ontology reasoner or RDF value
system. External ontology reasoning, provider-specific RDF value modeling, and
full RDF store behavior remain outside CNCF core. But CNCF must still define
the operational knowledge structures that domain logic can use directly.

At minimum, the next model should separate:

- semantic types;
- display labels/descriptions;
- operational attributes;
- external identifiers;
- similarity/distance representations and search references;
- provenance/evidence.

## Semantic Type Model

Semantic type is one of the key parts of the KnowledgeSpace design. It should
not be collapsed into `KnowledgeNodeCategory`, `rdf:type`, or arbitrary
attributes.

The current working distinction is:

```text
KnowledgeNodeCategory = coarse CNCF operational node category
SemanticType          = semantic classification attached to the node
RDF rdf:type          = one source of semantic type information
```

`KnowledgeNodeCategory` answers "what coarse operational category is this?"
`SemanticType` answers "what semantic classes, roles, profiles, or domain
types does this node carry?"

Known semantic type sources:

- RDF `rdf:type`.
- RDFS / OWL classes.
- SKOS concept scheme membership.
- Schema.org type/class names.
- domain model type, such as Entity type or aggregate role.
- TagSpace classification projected into KnowledgeSpace.
- source document type.
- chunk/retrieval unit type.
- generated/extracted concept type.
- shape/profile type, such as SHACL shape or application projection profile.
- provider-local type from RDF DB / Vector DB / SIE import.
- similarity profile type, describing how the node is represented for
  similarity/distance search.
- operational role, such as source, evidence, assertion subject, or answer
  candidate.

Known semantic type facets:

- `system`: vocabulary or authority, such as `rdf`, `owl`, `schema.org`,
  `skos`, `cncf.entity`, `cncf.tag`, or application namespace.
- `name`: canonical type name or URI.
- `label`: optional display label, preferably localizable.
- `source`: where this type was obtained from.
- `profile`: mapping/projection profile that accepted or produced the type.
- `confidence`: confidence for extracted or inferred types.
- `provenance`: provenance/evidence reference for the type assignment.
- `status`: asserted, inferred, generated, deprecated, rejected, or candidate.

Candidate shape:

```scala
final case class KnowledgeSemanticType(
  system: String,
  name: String,
  label: Option[KnowledgeLabel],
  source: Option[KnowledgeSourceRef],
  profile: Option[String],
  confidence: Option[Double],
  provenanceId: Option[KnowledgeProvenanceId],
  status: KnowledgeSemanticTypeStatus
)
```

The exact type names can change, but the important rule is that semantic type
is structured and multi-valued. A node may be both:

```text
KnowledgeNodeCategory.entity
SemanticType(cncf.entity:Customer)
SemanticType(schema.org:Organization)
SemanticType(skos:Concept)
```

These are not redundant. They come from different semantic layers and are used
for different decisions.

## Semantic Type Categories

Known categories that KS-10 should reserve or implement:

- `rdf-class`: direct `rdf:type` / RDFS / OWL class assignment.
- `ontology-class`: ontology class after mapping or reasoning.
- `schema-type`: Schema.org or similar vocabulary type.
- `skos-concept`: SKOS concept / concept scheme membership.
- `domain-type`: application/domain model type.
- `entity-type`: CNCF Entity name/kind binding.
- `tag-type`: TagSpace classification projected into KnowledgeSpace.
- `source-type`: source/document/chunk class.
- `relationship-derived-type`: type inferred from standard relationships,
  such as part-of, broader/narrower, same-as, or membership.
- `embedding-profile`: semantic type of the embedding projection.
- `retrieval-role`: role in retrieval, such as query candidate, answer source,
  explanation source, or context chunk.
- `generated-type`: AI/extraction-produced type.
- `inferred-type`: type produced by rules, ontology reasoning, or projection.
- `candidate-type`: proposed but not accepted type.

The list should be explicit in the model or catalog. Using only string
attributes would make it impossible to reason about which type is safe to use
for domain logic.

## Language / Locale Correspondence Nodes

Language-specific correspondence nodes are canonical knowledge information, not
just display labels.

Example use cases:

```text
English concept node  -> Japanese concept node
Japanese term node    -> English term node
source chunk in ja    -> corresponding source chunk in en
```

This is different from `KnowledgeLabel`. A label is display text attached to a
node. A language correspondence is another node that represents a corresponding
knowledge object in another language, locale, source corpus, or translation
context.

KS-10 should provide a first-class way to traverse such correspondences from
the node, while preserving the canonical relationship and evidence.

Candidate node-side projection:

```scala
final case class KnowledgeNodeCorrespondences(
  translations: Vector[KnowledgeNodeCorrespondence],
  localizedVersions: Vector[KnowledgeNodeCorrespondence],
  sameConcepts: Vector[KnowledgeNodeCorrespondence],
  sameResources: Vector[KnowledgeNodeCorrespondence],
  sourceAlignments: Vector[KnowledgeNodeCorrespondence],
  aliases: Vector[KnowledgeNodeCorrespondence]
)

final case class KnowledgeNodeCorrespondence(
  nodeId: KnowledgeNodeId,
  language: Option[LanguageTag],
  locale: Option[LocaleTag],
  relationshipKind: KnowledgeRelationshipKind
)
```

Candidate source relationship kinds:

- `translation-of`
- `localized-version-of`
- `same-concept-as`
- `same-resource-as`
- `source-aligned-with`
- `alias-of`

The node-side `correspondences` field is an operational projection for
business logic and admin/debug navigation. The canonical graph fact should
still be a `KnowledgeRelationship`, such as:

```text
KnowledgeRelationship(enNode --translation-of--> jaNode)
KnowledgeRelationship(enNode --same-concept-as--> jaNode)
```

The relationship keeps RDF predicate, evidence, provenance, confidence,
direction policy, translation source, and alignment details. The node-side
correspondence structure makes common traversal direct without hiding the
predicate-derived relationship.

This design supports queries such as:

```text
load Japanese translation for this English node
find all English nodes aligned to this Japanese concept
show source evidence for this translation alignment
```

It also prevents a common modeling error: storing language correspondences only as
localized label strings. A translated label and a corresponding knowledge node
are different things.

## Type Assignment and Business Logic

Business logic should be able to select nodes by semantic type without knowing
the RDF source vocabulary in detail.

Examples:

```text
select Customer-related knowledge nodes
select nodes that are schema.org:Person or mapped to Person
select source chunks used as evidence for this Entity
select concepts in a SKOS scheme
select nodes embedded with profile "sie.semantic.chunk.v1"
```

This requires semantic type normalization. The source type may be RDF/OWL, but
the application-facing type may be a domain or operational type:

```text
rdf:type schema:Organization
  -> SemanticType(schema.org:Organization)
  -> SemanticType(cncf.entity:Company), if mapping profile says so
```

Both should be retained. The mapped type supports domain logic; the source
type preserves traceability.

## Standard Relationship-Derived Node Attributes

`KnowledgeRelationship` is the generic operational structure for RDF
predicate-derived relationships. It remains the right place for arbitrary,
typed, evidence-backed graph edges.

However, some RDF relationship patterns are effectively standard structures in
domain modeling and should also be directly available as `KnowledgeNode`
attributes or fields. Examples include:

- `is-part-of` / part-whole membership.
- broader/narrower classification.
- same-as / equivalence.
- type/class membership.
- primary source/document/chunk membership.
- canonical owner/origin relationship, when the profile treats it as node
  metadata.

This is not a replacement for `KnowledgeRelationship`. It is a denormalized
operational projection for common structures that business logic should not
have to rediscover by traversing arbitrary edges every time.

The rule should be:

```text
KnowledgeRelationship = canonical generic graph edge
KnowledgeNode fields  = direct operational projection of selected standard
                        relationship patterns
```

For example, an RDF predicate mapped to `is-part-of` may produce both:

```text
KnowledgeRelationship(child --is-part-of--> parent)
KnowledgeNode(child).partOf = parent
```

The `KnowledgeRelationship` preserves evidence, provenance, original RDF
predicate, and graph semantics. The direct node field makes the relationship
available to domain logic and admin/debug projection without requiring
relationship traversal for every common case.

KS-10 should decide which standard relationship-derived properties are allowed
as direct node fields, and should keep the list small and profile-driven.

## Canonical Node Projections

The key design question is deciding which knowledge should become canonical
node-side information.

`KnowledgeRelationship` remains the generic source of graph semantics, but not
all business logic should have to traverse arbitrary relationships for
well-known operational facts. When a relationship pattern becomes a stable
part of the operational knowledge model, it should be projected into typed
node-side fields.

The design rule is:

```text
KnowledgeRelationship = canonical generic edge and evidence/provenance carrier
KnowledgeNode field   = canonical operational projection for selected facts
```

This is stronger than "cache" or "UI convenience." A canonical node projection
is part of the KnowledgeSpace model. It must have a defined source
relationship pattern, evidence/provenance trace, and update policy.

Candidate canonical node projections:

- semantic types: RDF/OWL/schema/SKOS/domain/entity/tag/retrieval types.
- labels and names: display labels, localized labels, aliases, canonical name.
- language/locale correspondences: translations, localized versions,
  same-concept/same-resource nodes, source alignments.
- identity links: same-as, equivalent-to, canonical subject, external ids.
- hierarchy/classification: broader, narrower, primary classification path,
  additional classification edges.
- part-whole structure: part-of, has-part, member-of, container/document/chunk
  membership.
- source/evidence links: primary source, source document, source chunk,
  extracted-from, observed-in.
- entity bindings: related Entity, Entity type/name/id/version binding.
- tag bindings: TagSpace classification projected into KnowledgeSpace.
- provenance summary: origin, generated-by, owner, source graph, import run.
- similarity/distance metadata: representation profile, search entries,
  status, source text/graph projection used for similarity search.
- confidence and lifecycle: accepted/candidate/rejected, inferred/generated,
  stale/current, validation status.
- authorization/confidentiality hints: projection-safe confidentiality status,
  redaction policy, visibility class.
- temporal validity: valid-from, valid-to, observed-at, effective period.
- operational roles: source node, evidence node, answer candidate, query
  context, explanation context.

These projections should be typed structures, not free-form
`Map[String, String]` entries. The current `attributes` field can remain as an
escape hatch during early implementation, but it should not become the place
where canonical knowledge semantics disappear.

## Operational Semantic Node Shape

The candidate structure above is still too abstract if it is read as a set of
generic projection buckets. `KnowledgeNode` should be shaped as an operational
semantic object: domain logic should be able to ask the node directly for the
knowledge structures CNCF has decided are canonical.

The model should therefore expose direct, typed fields for accepted canonical
knowledge structures. The underlying relationships remain available for full
graph traversal and evidence, but the node itself should no longer look like
only `id + kind + attributes`.

## KnowledgeFrame

`KnowledgeFrame` is the middle layer between `KnowledgeWorkingSet` and
individual nodes/relationships.

It represents a coherent unit of knowledge for a focus, purpose, source, query,
or application operation. It is not a similarity cluster, not a document chunk,
and not an unordered bag. It is a frame of structured knowledge that the
WorkingSet can index and domain logic can traverse.

Candidate shape:

```scala
final case class KnowledgeFrame(
  id: KnowledgeFrameId,
  kind: KnowledgeFrameKind,
  focusNodeIds: Vector[KnowledgeNodeId],
  nodeIds: Vector[KnowledgeNodeId],
  relationshipIds: Vector[KnowledgeRelationshipId],
  evidenceIds: Vector[KnowledgeEvidenceId],
  provenanceIds: Vector[KnowledgeProvenanceId],
  origin: KnowledgeFrameOrigin,
  sourceRefs: Vector[KnowledgeSourceRef],
  purpose: Option[KnowledgeFramePurpose],
  query: Option[KnowledgeQueryRef],
  materializedAt: Option[Instant],
  confidence: Option[Double],
  attributes: KnowledgeAttributes
)
```

Candidate frame kinds:

- `retrieval-result`: SIE or other semantic search result.
- `entity-context`: knowledge around a business Entity.
- `explanation`: knowledge used to explain an answer/decision.
- `source-context`: knowledge from a source document/chunk/import.
- `classification-context`: knowledge around a Tag/classification.
- `curated`: manually curated knowledge unit.
- `generated`: generated/extracted application knowledge.

`origin` is required because a frame is not only a collection. It is an
operational unit of knowledge with a history.

Candidate origin shape:

```scala
final case class KnowledgeFrameOrigin(
  route: KnowledgeFrameInputRoute,
  provider: Option[String],
  operation: Option[String],
  component: Option[String],
  sourceGraph: Option[String],
  sourceDataset: Option[String],
  requestId: Option[String],
  jobId: Option[String],
  taskId: Option[String],
  createdBy: Option[String],
  provenanceId: Option[KnowledgeProvenanceId]
)
```

Candidate input routes:

- `sie-retrieval`
- `entity-projection`
- `tag-association-projection`
- `batch-import`
- `manual-curation`
- `external-graph-import`
- `operation-generated`
- `admin-api`

This makes frame-level provenance explicit:

```text
KnowledgeFrame
  tells where this unit of knowledge came from
  groups nodes/relationships/evidence/provenance produced by that route
  lets admin/debug/explanation show frame-level origin before node details
```

Node/relationship/evidence provenance remains more detailed. Frame origin is
the coarse-grained provenance of the frame as an operational unit.

`KnowledgeWorkingSet` owns frames and builds global indexes across the nodes
and relationships contained in those frames:

```scala
final case class KnowledgeWorkingSetSnapshot(
  frames: Vector[KnowledgeFrame],
  nodes: Vector[KnowledgeNode],
  relationships: Vector[KnowledgeRelationship],
  evidence: Vector[KnowledgeEvidence],
  provenance: Vector[KnowledgeProvenance]
)
```

This gives the runtime three levels:

```text
KnowledgeWorkingSet = memory-resident operational view
KnowledgeFrame      = purpose/focus/source/query unit of knowledge
KnowledgeNode       = domain-operable structured knowledge object
KnowledgeRelationship = graph/predicate/evidence source of relationship facts
```

## Candidate KnowledgeNode Shape

`KnowledgeNode` will carry a lot of information. It should not become one huge
case class with dozens of unrelated fields.

See also `knowledge-node-rdf-predicate-attribute-mapping.md` for the working
mapping between RDF predicate families and canonical `KnowledgeNode` value
attributes.

The preferred shape is the same style used around `SimpleEntity`: define
purpose-specific value objects and let `KnowledgeNode` delegate to them. The
node remains the domain-operable object, but detailed responsibilities are
split into coherent value structures.

Candidate top-level shape:

```scala
final case class KnowledgeNode(
  id: KnowledgeNodeId,
  category: KnowledgeNodeCategory,
  identity: KnowledgeNodeIdentity,
  presentation: KnowledgeNodePresentation,
  semantics: KnowledgeNodeSemantics,
  structure: KnowledgeNodeStructure,
  sources: KnowledgeNodeSources,
  bindings: KnowledgeNodeBindings,
  similarity: KnowledgeNodeSimilarity,
  operations: KnowledgeNodeOperations,
  attributes: KnowledgeAttributes
)
```

Candidate delegated values:

```scala
final case class KnowledgeNodeIdentity(
  rdfNode: Option[RdfNodeName],
  identityLinks: KnowledgeIdentityLinks,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier]
)

final case class KnowledgeNodePresentation(
  labels: KnowledgeLabels,
  names: KnowledgeNames,
  descriptions: KnowledgeDescriptions
)

final case class KnowledgeNodeSemantics(
  semanticTypes: Vector[KnowledgeSemanticType],
  roles: Set[KnowledgeOperationalRole],
  confidence: KnowledgeConfidenceProfile,
  confidentiality: KnowledgeConfidentialityProfile,
  temporal: KnowledgeTemporalProfile,
  lifecycle: KnowledgeNodeLifecycle
)

final case class KnowledgeNodeStructure(
  correspondences: KnowledgeNodeCorrespondences,
  classifications: KnowledgeClassifications,
  hierarchy: KnowledgeHierarchy,
  partWhole: KnowledgePartWhole
)

final case class KnowledgeNodeSources(
  sourceRefs: KnowledgeSourceRefs,
  evidenceIds: Vector[KnowledgeEvidenceId],
  provenanceIds: Vector[KnowledgeProvenanceId]
)

final case class KnowledgeNodeBindings(
  entityBindings: Vector[KnowledgeEntityBinding],
  tagBindings: Vector[KnowledgeTagBinding]
)

final case class KnowledgeNodeSimilarity(
  representations: Vector[KnowledgeSimilarityRepresentation],
  searchEntries: Vector[KnowledgeSimilaritySearchEntry],
  status: KnowledgeSimilarityStatus
)

final case class KnowledgeNodeOperations(
  materializedAt: Option[Instant],
  frameIds: Vector[KnowledgeFrameId],
  validationStatus: KnowledgeValidationStatus
)
```

Candidate lower-level direct structures:

```scala
final case class KnowledgeIdentityLinks(
  canonical: Option[KnowledgeNodeId],
  sameAs: Vector[KnowledgeNodeId],
  equivalentTo: Vector[KnowledgeNodeId],
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier]
)

final case class KnowledgeClassifications(
  primary: Option[KnowledgeNodeId],
  broader: Vector[KnowledgeNodeId],
  narrower: Vector[KnowledgeNodeId],
  additional: Vector[KnowledgeNodeId]
)

final case class KnowledgeHierarchy(
  parent: Option[KnowledgeNodeId],
  children: Vector[KnowledgeNodeId],
  ancestors: Vector[KnowledgeNodeId],
  descendants: Vector[KnowledgeNodeId]
)

final case class KnowledgePartWhole(
  partOf: Vector[KnowledgeNodeId],
  hasPart: Vector[KnowledgeNodeId],
  memberOf: Vector[KnowledgeNodeId],
  hasMember: Vector[KnowledgeNodeId],
  container: Option[KnowledgeNodeId]
)

final case class KnowledgeSourceRefs(
  primarySource: Option[KnowledgeSourceRef],
  sourceDocuments: Vector[KnowledgeSourceRef],
  sourceChunks: Vector[KnowledgeSourceRef],
  extractedFrom: Vector[KnowledgeEvidenceId],
  observedIn: Vector[KnowledgeEvidenceId]
)
```

These value object names are intentionally domain-facing. They are the API that
business logic should use. A caller should not need to search all relationships
to discover the accepted part-whole, correspondence, classification, source, or
identity information for ordinary operations.

Delegation rules:

- `KnowledgeNode` owns the stable node id and coarse category.
- `KnowledgeNodeIdentity` owns RDF node identity and external identity links.
- `KnowledgeNodePresentation` owns human-facing display information.
- `KnowledgeNodeSemantics` owns semantic types, roles, confidence,
  confidentiality, temporal profile, and lifecycle.
- `KnowledgeNodeStructure` owns graph-derived operational structure that
  domain logic traverses directly, including translation/localization/source
  alignment correspondences.
- `KnowledgeNodeSources` owns source/evidence/provenance references.
- `KnowledgeNodeBindings` owns CNCF Entity/Tag bindings.
- `KnowledgeNodeSimilarity` owns similarity/distance representations and
  search entries. Embedding/vector/index details are implementation metadata
  inside that value.
- `KnowledgeNodeOperations` owns WorkingSet/frame/runtime materialization
  metadata.

Every direct structure should still be traceable:

```text
direct node field
  -> source relationship id(s)
  -> evidence/provenance
  -> original RDF predicate/source statement, when applicable
```

If a field cannot be traced back to relationship/evidence/provenance, it
should be treated as provider-local or derived/debug information, not as a
canonical node field.

KS-10 should implement the minimal coherent CNCF knowledge structure, not just
reserve names. It may avoid implementing every field above if that makes the
slice too large, but it must decide which structures are model-owned canonical
fields now, which are explicitly reserved for near-term implementation, and
which stay as application/provider-owned extensions.

Selection criteria for making a projection canonical:

- domain logic needs to traverse it frequently;
- admin/debug/explanation needs to show it as a first-class fact;
- it has stable semantics across SIE and likely future applications;
- it can be traced back to relationships/evidence/provenance;
- hiding it in generic attributes would make query/projection behavior
  ambiguous;
- it is not merely provider-local implementation detail.

Non-canonical examples:

- provider-specific temporary extraction flags;
- raw RDF parser internals;
- Vector DB tuning parameters;
- debug-only import counters;
- source-specific strings that have no stable semantic meaning.

## Entity-To-Knowledge Binding

Business logic needs to load an Entity and then ask `KnowledgeSpace` for
knowledge about that Entity.

This should support two cases:

1. The Entity itself is projected as a `KnowledgeNode`.
2. The Entity remains an ordinary business object and is linked to knowledge
   nodes, external subjects, evidence, or relationships.

The second case must be first-class. Entity id, RDF subject, provider id, and
KnowledgeNode id must remain separate. The current helper
`ExternalKnowledgeIdentifier.entity(entityName, entityId)` is a good starting
point, but KS-10 should strengthen the binding model and tests around it.

Potential future helpers:

- lookup by Entity id and entity name;
- lookup by Entity version/source reference;
- lookup by Tag attachment or Association-backed relationship;
- explanation projection for an Entity that returns nodes, relationships,
  evidence, provenance, and vector/source references.

## Entity-Derived Knowledge

When a `KnowledgeNode` corresponds to an Entity, Entity information should be
reflected into the node even if no RDF predicate explicitly defines it.

This is not RDF fallback. It is a first-class input route. CNCF has structured
knowledge about Entities that can be projected into `KnowledgeNode`:

- Entity name, id, version, and component context.
- Entity kind and operational role.
- SimpleEntity lifecycle and state.
- descriptive/content attributes.
- declared attributes and attribute types.
- containment/ownership/association relationships.
- Tag attachments and TagSpace classification.
- Aggregate/View/Service usage context where available.
- model-level inheritance-like or specialization relationships.
- generated runtime descriptors and usage policy.
- operation context that created/updated/read the Entity.

For `SimpleEntity`, the structure and intended usage are known to CNCF, so the
projection can be richer and more reliable than a generic external graph
projection.

Entity-derived facts are especially important in CNCF.

```text
Entity
  -> projection
  -> Fact
  -> KnowledgeNode / KnowledgeRelationship / node structured attributes
```

An Entity is not itself a fact. A fact is the knowledge-side proposition
derived from Entity content, structure, state, or relationship.

Examples:

```text
Entity Customer#123 exists
Customer#123 is-a Customer
Customer#123 hasStatus Active
Customer#123 hasTag important
Customer#123 partOf Company#9
```

Compared with generic RDF-derived facts, Entity-derived facts have special
value for CNCF:

- CNCF knows the source model and runtime descriptor.
- CNCF knows Entity id, version, lifecycle, and storage context.
- CNCF can trace the fact to an Entity record, TagAttachment, Association, or
  operation context.
- CNCF can expose the fact to domain logic without forcing RDF interpretation.
- SimpleEntity structure and usage make the projection more deterministic than
  generic external graph import.

Entity-derived facts should therefore be a first-class fact category in
KnowledgeSpace, not just generic evidence or provider metadata.

Candidate mapping:

```text
SimpleEntity model metadata
  -> KnowledgeNodeSemantics.semanticTypes
  -> KnowledgeNodeStructure.classifications / hierarchy
  -> KnowledgeNodeBindings.entityBindings

Entity record/version
  -> KnowledgeNodeSources.sourceRefs
  -> KnowledgeNodeSources.evidenceIds

TagAttachment
  -> KnowledgeNodeBindings.tagBindings
  -> KnowledgeNodeStructure.classifications

Association
  -> KnowledgeRelationship
  -> KnowledgeNodeStructure / KnowledgeNodeSources where canonical
```

This keeps the separation:

```text
Entity        = business object / stored application state
KnowledgeNode = structured knowledge representation about that Entity
```

The Entity-derived route should also preserve traceability. If a node attribute
comes from an Entity model, Entity record, TagAttachment, Association, or
operation context, the source should be visible through `KnowledgeNodeSources`,
`KnowledgeFrame.origin`, evidence, or provenance.

## Relationship to Facts and Assertions

`KnowledgeNode` is not enough by itself. Many knowledge statements are edge- or
fact-centered:

```text
Alice worksFor Acme
```

This can be represented as a relationship, an assertion, an observation, or an
evidence-backed extracted statement. KS-10 does not have to introduce all of
those types, but the `KnowledgeNode` design must not block them.

The immediate implication is that `KnowledgeRelationship` should also be
hardened with typed relationship kind, evidence/provenance, source predicate,
confidence, temporal validity, and contradiction hooks where needed.

## KS-10 Design Direction

KS-10 should do the following before SIE provider integration:

- Introduce provisional typed `KnowledgeNodeCategory` as a small operational
  category, not as the semantic type system.
- Introduce typed `KnowledgeRelationshipKind`.
- Clarify `KnowledgeNodeId` as CNCF-internal identity.
- Add direct RDF node name support for RDF-backed nodes while keeping
  `KnowledgeNodeId` as CNCF-internal identity.
- Keep Entity id, Tag id, provider id, alternate RDF ids, and similarity search
  ids as explicit external identifiers or references.
- Replace `label: Option[String]` with an I18n/localizable label model.
- Add similarity/distance representation context as first-class knowledge
  information; embedding/vector/index details remain implementation metadata
  and raw vector value storage remains policy-controlled.
- Introduce structured `KnowledgeSemanticType` as a first-class multi-valued
  model, separated from `KnowledgeNodeCategory`, RDF `rdf:type`, and generic
  attributes.
- Reserve semantic type categories for RDF/OWL/schema/SKOS, domain/entity,
  TagSpace projection, source/chunk, similarity profile, retrieval role,
  generated/inferred/candidate types.
- Introduce typed attributes after semantic types are separated from display,
  operational, similarity/search, provenance, and evidence fields.
- Add direct node projections for selected standard relationship patterns such
  as part-of, broader/narrower, same-as, and type membership, while preserving
  `KnowledgeRelationship` as the canonical generic edge.
- Add language/locale correspondence projections named by predicate-like
  concepts: translations, localized versions, same-concepts, same-resources,
  source-alignments, and aliases, backed by canonical
  `KnowledgeRelationship` records.
- Define canonical node projection criteria and reserve typed node-side
  structures for identity, classification, part-whole, source/evidence,
  Entity/Tag binding, similarity/search metadata, lifecycle/confidence,
  confidentiality, temporal validity, and operational roles.
- Make `KnowledgeNode` itself an operational semantic object with direct
  delegated value objects for identity, presentation, semantics, structure,
  sources, bindings, similarity, and operations.
- Avoid a giant `KnowledgeNode` case class; follow the SimpleEntity-style
  delegation pattern with purpose-specific value objects.
- Require direct node fields to retain traceability back to relationship ids,
  evidence/provenance, and original RDF predicate/source statement where
  applicable.
- Strengthen Entity-to-knowledge binding helpers and tests.
- Keep fact/assertion/observation as an explicit future hook unless concrete
  SIE data forces the distinction in KS-10.

## Non-Goals

KS-10 should not:

- add Fuseki or Chroma dependencies to CNCF core;
- implement RDF import/export in CNCF core;
- expose raw similarity vectors through admin/debug projection by default;
- make `KnowledgeNodeId` equal RDF subject URI;
- hide RDF node name only inside generic external identifiers when a node is
  RDF-backed;
- merge `TagSpace` and `KnowledgeSpace`;
- turn retrieval chunks into the `KnowledgeNode` schema;
- implement SIE-specific MCP tool semantics in CNCF core.

## Expected Outcome

After KS-10, CNCF should have a stronger operational knowledge model that can
accept SIE-projected knowledge without redesigning identity, labels, kinds, or
similarity representations during provider integration. KS-11 can then refine
projection/admin/query surfaces against that hardened model, and KS-12 can
connect `textus-sie` providers.
