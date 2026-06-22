# KS-10 Knowledge Operational Model Hardening

Date: 2026-05-18

This note is the KS-10 implementation design input for Phase 25. It summarizes
the working journal discussion into the model shape CNCF should harden before
connecting concrete `textus-sie` RDF DB / Vector DB provider behavior.

Related journal inputs:

- `docs/journal/2026/05/knowledge-node-design-before-sie-integration.md`
- `docs/journal/2026/05/knowledge-relationship-design-before-sie-integration.md`
- `docs/journal/2026/05/knowledge-node-rdf-predicate-attribute-mapping.md`

## Purpose

`KnowledgeSpace` projects semi-structured RDF / external knowledge and
CNCF-owned Entity information into a domain-operable CNCF object model.

The core flow is:

```text
RDF / external knowledge / Entity-derived knowledge
  -> KnowledgeFrame
  -> KnowledgeNode / KnowledgeRelationship / Evidence / Provenance
  -> memory-resident KnowledgeWorkingSet
  -> domain logic, query/projection, admin/debug, MCP publication
```

CNCF should not expose raw RDF DB behavior as the domain programming model.
Domain logic should operate against `KnowledgeNode`,
`KnowledgeRelationship`, and `KnowledgeWorkingSet`.

## Runtime Layers

KS-10 should make the runtime layers explicit:

```text
KnowledgeSpace
  owns KnowledgeWorkingSet
    owns KnowledgeFrame(s)
      groups KnowledgeNode / KnowledgeRelationship / Evidence / Provenance
    builds indexes
    builds canonical node projections
    exposes programming-oriented lookup/traversal APIs
```

### KnowledgeWorkingSet

`KnowledgeWorkingSet` is not just a cache. It is the memory-resident
operational view of structured knowledge.

It stores:

- `KnowledgeFrame` list.
- `KnowledgeNode` list.
- `KnowledgeRelationship` list.
- `KnowledgeEvidence` list.
- `KnowledgeProvenance` list.

It builds indexes:

- node by id.
- relationship by id.
- relationships by source node.
- relationships by target node.
- frame by id.
- frames by focus node.
- external identifier to node ids.
- Entity binding to node ids.
- Tag binding to node ids.

It also builds accepted canonical node projections from relationships and
facts. `KnowledgeNode` itself should not keep relationship ids as its primary
relationship access model.

### KnowledgeFrame

`KnowledgeFrame` sits between the WorkingSet and individual nodes or
relationships. It is a purpose/focus/source/query unit of structured
knowledge.

Candidate shape:

```scala
final case class KnowledgeFrame(
  id: KnowledgeFrameId,
  kind: KnowledgeFrameKind,
  focusNodeIds: Vector[KnowledgeNodeId],
  nodeIds: Vector[KnowledgeNodeId],
  relationshipIds: Vector[KnowledgeRelationshipId],
  factIds: Vector[KnowledgeFactId],
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

- `retrieval-result`
- `entity-context`
- `explanation`
- `source-context`
- `classification-context`
- `curated`
- `generated`

`KnowledgeFrameOrigin` should record the coarse-grained source of the frame:

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

Frame origin explains where the whole knowledge unit came from. Node,
relationship, evidence, and provenance records explain the detailed source of
individual facts.

## Fact Model Direction

`Entity` itself is not a fact.

Fact means a knowledge-side proposition derived from RDF, an Entity, a
relationship, an observation, or generated/extracted knowledge.

```text
Entity source data
  -> Entity-derived Fact
  -> KnowledgeNode attribute and/or KnowledgeRelationship
```

Entity-derived facts are especially important in CNCF. CNCF knows the Entity
model, runtime descriptor, version, lifecycle, storage context, TagAttachment,
Association, and operation context.

Examples:

```text
Entity type       -> semantic type fact
Entity attribute  -> value fact
TagAttachment     -> classification fact
Association       -> relationship fact
Lifecycle state   -> operational state fact
```

Fact/assertion/observation can remain a near-term hook if KS-10 does not need
full concrete types, but the hardened model must not block them:

```text
Observation = what was observed
Assertion   = what a source claims
Fact        = normalized knowledge proposition
Relationship = graph projection of a fact/proposition
```

## KnowledgeNode

`KnowledgeNode` should be a domain-operable object, not a thin RDF node
wrapper and not a giant flat case class.

It should follow a SimpleEntity-style delegation pattern:

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

### Identity

`KnowledgeNodeId` is CNCF internal runtime identity. It must not equal:

- RDF subject URI.
- RDF blank node label.
- Entity id.
- Tag id.
- Vector DB id.
- provider-local document/chunk id.

Candidate value object:

```scala
final case class KnowledgeNodeIdentity(
  rdfNode: Option[RdfNodeName],
  identityLinks: KnowledgeIdentityLinks,
  externalIdentifiers: Vector[ExternalKnowledgeIdentifier]
)
```

`RdfNodeName` should be direct information when a node is RDF-backed. It should
not be hidden only in generic external identifiers.

### Category

`KnowledgeNodeCategory` is provisional. It is a coarse operational category
for WorkingSet, admin/debug, and projection behavior. It is not the semantic
type system.

Candidate categories:

- `entity`
- `tag`
- `concept`
- `source`
- `document`
- `chunk`
- `external-subject`
- `generated`

Domain logic should primarily use semantic types and canonical node fields,
not category alone.

### Presentation

Display-facing information should be localizable and source-aware:

```scala
final case class KnowledgeNodePresentation(
  labels: KnowledgeLabels,
  names: KnowledgeNames,
  descriptions: KnowledgeDescriptions
)
```

This replaces `label: Option[String]`.

### Semantics

Semantic type is a key part of Phase 25. It must not be collapsed into
category, RDF `rdf:type`, or free-form attributes.

```scala
final case class KnowledgeNodeSemantics(
  semanticTypes: Vector[KnowledgeSemanticType],
  roles: Set[KnowledgeOperationalRole],
  confidence: KnowledgeConfidenceProfile,
  confidentiality: KnowledgeConfidentialityProfile,
  temporal: KnowledgeTemporalProfile,
  lifecycle: KnowledgeNodeLifecycle
)
```

Known semantic type categories to reserve or implement:

- `rdf-class`
- `ontology-class`
- `schema-type`
- `skos-concept`
- `domain-type`
- `entity-type`
- `tag-type`
- `source-type`
- `relationship-derived-type`
- `similarity-profile`
- `retrieval-role`
- `generated-type`
- `inferred-type`
- `candidate-type`

Known semantic type facets:

- system / authority.
- canonical name or URI.
- label.
- source.
- mapping/profile.
- confidence.
- provenance.
- status: asserted, inferred, generated, deprecated, rejected, or candidate.

### Structure

`KnowledgeNodeStructure` is where accepted relationship-derived structures
become directly traversable node attributes.

```scala
final case class KnowledgeNodeStructure(
  correspondences: KnowledgeNodeCorrespondences,
  classifications: KnowledgeClassifications,
  hierarchy: KnowledgeHierarchy,
  partWhole: KnowledgePartWhole
)
```

Language/locale correspondence should use predicate-like names, not vague
`counterparts`:

```scala
final case class KnowledgeNodeCorrespondences(
  translations: Vector[KnowledgeNodeCorrespondence],
  localizedVersions: Vector[KnowledgeNodeCorrespondence],
  sameConcepts: Vector[KnowledgeNodeCorrespondence],
  sameResources: Vector[KnowledgeNodeCorrespondence],
  sourceAlignments: Vector[KnowledgeNodeCorrespondence],
  aliases: Vector[KnowledgeNodeCorrespondence]
)
```

Canonical structure projections include:

- translations and localized versions.
- same-concept and same-resource links.
- source alignments.
- classification broader/narrower.
- hierarchy parent/children/ancestors/descendants.
- part-of, has-part, member-of, has-member.

The underlying `KnowledgeRelationship` remains the source of graph/evidence
truth. Node fields are the programming-oriented projection.

### Sources

```scala
final case class KnowledgeNodeSources(
  sourceRefs: KnowledgeSourceRefs,
  evidenceIds: Vector[KnowledgeEvidenceId],
  provenanceIds: Vector[KnowledgeProvenanceId]
)
```

These fields should trace node attributes back to:

- RDF statement or named graph.
- Entity record/version.
- TagAttachment.
- Association.
- operation context.
- source document/chunk.
- extraction/import run.

### Bindings

```scala
final case class KnowledgeNodeBindings(
  entityBindings: Vector[KnowledgeEntityBinding],
  tagBindings: Vector[KnowledgeTagBinding]
)
```

Entity binding and Tag binding are first-class CNCF links. Entity ids and Tag
ids remain distinct from `KnowledgeNodeId` and RDF node names.

### Similarity / Distance

The node-side semantic name should be `similarity`, not `embedding`,
`vector`, or `index`.

`embedding` is one representation method. `vector` and `index` are
implementation details.

```scala
final case class KnowledgeNodeSimilarity(
  representations: Vector[KnowledgeSimilarityRepresentation],
  searchEntries: Vector[KnowledgeSimilaritySearchEntry],
  status: KnowledgeSimilarityStatus
)
```

The value object should carry:

- representation method, such as embedding.
- model id/name/version.
- similarity or distance metric.
- representation context/profile.
- source text/graph/chunk projection used for representation.
- search provider/store.
- collection/index and representation id, when implemented by vector search.
- value/payload reference policy.
- indexed/stale/missing/failed status.

Similarity score or distance belongs to query result/projection records, not to
the node itself.

### Operations

```scala
final case class KnowledgeNodeOperations(
  materializedAt: Option[Instant],
  frameIds: Vector[KnowledgeFrameId],
  validationStatus: KnowledgeValidationStatus
)
```

This value object holds WorkingSet/frame/runtime materialization metadata.

## KnowledgeRelationship

`KnowledgeRelationship` is the canonical generic graph edge and the source of
predicate/evidence/provenance detail.

Candidate direction:

```scala
final case class KnowledgeRelationship(
  id: KnowledgeRelationshipId,
  kind: KnowledgeRelationshipKind,
  sourceNodeId: KnowledgeNodeId,
  targetNodeId: KnowledgeNodeId,
  rdfPredicate: Option[RdfPredicateName],
  semanticTypes: Vector[KnowledgeRelationshipSemanticType],
  evidenceIds: Vector[KnowledgeEvidenceId],
  provenanceId: Option[KnowledgeProvenanceId],
  qualifiers: KnowledgeRelationshipQualifiers,
  similarity: Option[KnowledgeRelationshipSimilarity],
  attributes: KnowledgeAttributes
)
```

`KnowledgeRelationshipId` is CNCF internal relationship identity. It is not:

- RDF predicate URI.
- RDF statement id.
- RDF reification id.
- source row id.
- provider edge id.

`rdfPredicate` preserves source semantics when RDF-backed. `kind` is the CNCF
operational relationship interpretation.

Relationship semantic type categories to reserve or implement:

- `rdf-object-property`
- `rdf-datatype-property`
- `rdf-annotation-property`
- `identity-equivalence`
- `type-membership`
- `taxonomy`
- `part-whole`
- `dependency`
- `causal`
- `provenance`
- `evidence`
- `document-structure`
- `entity-binding`
- `tag-binding`
- `language-correspondence`
- `association-projection`
- `similarity-context`
- `retrieval-context`
- `generated-relation`
- `inferred-relation`
- `candidate-relation`

Relationship direction policy must distinguish:

- source RDF direction.
- operational relationship direction.
- inverse predicate mapping.
- symmetric relationship policy.

Literal object handling must be profile-driven:

```text
RDF object IRI/blank node -> usually KnowledgeNode + KnowledgeRelationship
RDF object literal        -> typed value, unless profile promotes it to a node
```

## Predicate / Attribute Mapping

Mapping is profile-driven.

Core CNCF can provide small default mappings for common predicates. The same
set is the generic `cncf-rdf-1.5-hop-v1` predicate profile used by focused
RDF/knowledge node explanation. It is role-based and provider-overridable.
Common predicates include:

- `rdf:type`
- `rdfs:label`
- `rdfs:comment`
- `owl:sameAs`
- `skos:prefLabel`
- `skos:altLabel`
- `skos:broader`
- `skos:narrower`
- `skos:exactMatch`
- `dcterms:source`
- `dcterms:isPartOf`
- `dcterms:hasPart`
- `prov:wasDerivedFrom`
- `prov:generatedAtTime`
- `schema:name`
- `schema:description`
- `schema:sameAs`
- `schema:isPartOf`
- `schema:hasPart`
- `schema:memberOf`

But CNCF should not hardcode a large fixed ontology. Predicate interpretation
must be overridable by application/SIE/provider profiles.

When a mapping creates a node attribute, it must keep the source path:

```text
KnowledgeNode.structure.partWhole.partOf = parent
  <- KnowledgeRelationship(child --part-of--> parent)
  <- rdfPredicate dcterms:isPartOf
  <- evidence/provenance/source graph
```

## Entity-Derived Projection

Entity-derived facts are a first-class and particularly important input route.

For `SimpleEntity`, CNCF knows structure and usage, so projection can be richer
than RDF-derived projection:

```text
SimpleEntity model
  -> semantic type
  -> lifecycle/status
  -> attribute/value structure
  -> tag-derived classification
  -> association-derived relationships
  -> source/evidence/provenance
```

Candidate mapping:

- Entity id/name/version -> `bindings.entityBindings`.
- Entity type/name/kind -> `semantics.semanticTypes`.
- Entity lifecycle/state -> `semantics.lifecycle`.
- TagAttachment -> `bindings.tagBindings` and `structure.classifications`.
- Association -> `KnowledgeRelationship` and canonical structure projection.
- Entity record/version/operation context -> `sources`.

## KS-10 Implementation Tasks

KS-10 should implement the minimal coherent CNCF knowledge structure, not just
reserve names.

Recommended task list:

- Add or rename model types:
  - `KnowledgeFrameId`, `KnowledgeFrame`, `KnowledgeFrameOrigin`,
    `KnowledgeFrameKind`, `KnowledgeFrameInputRoute`.
  - `KnowledgeNodeCategory`.
  - `KnowledgeNodeIdentity`, `KnowledgeNodePresentation`,
    `KnowledgeNodeSemantics`, `KnowledgeNodeStructure`,
    `KnowledgeNodeSources`, `KnowledgeNodeBindings`,
    `KnowledgeNodeSimilarity`, `KnowledgeNodeOperations`.
  - `KnowledgeRelationshipKind`,
    `KnowledgeRelationshipSemanticType`.
- Keep `KnowledgeNodeId` and `KnowledgeRelationshipId` CNCF-internal.
- Add direct RDF node name and RDF predicate name support.
- Replace `label: Option[String]` with localizable presentation values.
- Replace generic node `kind` string with provisional `KnowledgeNodeCategory`.
- Keep relationship `kind` typed as `KnowledgeRelationshipKind`.
- Add Entity-derived fact projection helpers for Entity/Tag/Association inputs.
- Add WorkingSet frame storage and indexes.
- Build node canonical projections from relationships/facts during WorkingSet
  materialization.
- Keep raw RDF DB, Vector DB, Fuseki, Chroma, and SIE provider wiring out of
  KS-10 implementation.

## Test Direction

Focused KS-10 tests should cover:

- `KnowledgeNodeId` remains distinct from RDF node, Entity id, Tag id, and
  provider ids.
- `KnowledgeNodeCategory` is typed and not used as semantic type.
- `KnowledgeSemanticType` supports multiple source systems and statuses.
- Localized labels preserve language tags.
- `KnowledgeFrame` groups nodes/relationships/evidence/provenance and records
  frame origin.
- WorkingSet builds indexes over frames, nodes, and relationships.
- WorkingSet builds canonical node projections from relationships.
- Node does not need to store relationship ids to expose canonical attributes.
- Entity-derived facts populate node bindings, semantic types,
  classifications, sources, and relationships.
- Similarity metadata uses meaning-level names and keeps embedding/vector/index
  as implementation metadata.

## Non-Goals

KS-10 should not:

- add Fuseki or Chroma dependencies to CNCF core.
- implement RDF import/export in CNCF core.
- implement SIE-specific MCP tool semantics.
- expose raw similarity vectors through admin/debug projection by default.
- turn CNCF into an RDF triple store, SPARQL engine, or full ontology reasoner.
- merge TagSpace and KnowledgeSpace.
- make Entity id, Tag id, RDF subject URI, or provider id equal
  `KnowledgeNodeId`.
