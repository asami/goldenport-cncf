# KnowledgeNode RDF Predicate to Attribute Mapping

Date: 2026-05-18

This journal entry records the working mapping between RDF predicates,
Entity-derived knowledge, and the CNCF `KnowledgeNode` attribute/value-object
structure before Phase 25 KS-10 implementation. It is a design input, not yet
the normative runtime contract.

## Purpose

`KnowledgeSpace` projects semi-structured RDF / external knowledge and
CNCF-owned Entity information into a domain-operable CNCF object model.
`KnowledgeNode` should expose selected canonical knowledge structures as typed
attributes, while
`KnowledgeRelationship` preserves graph edges, RDF predicates, evidence, and
provenance.

The key rule is:

```text
RDF predicate / RDF statement
  -> mapping profile
  -> KnowledgeRelationship source edge
  -> KnowledgeNode delegated value attribute, when the fact is canonical
```

`KnowledgeNode` should not store arbitrary RDF predicates as flat attributes.
Only stable operational facts become typed node attributes. The original RDF
predicate remains traceable through `KnowledgeRelationship`, evidence, and
provenance.

RDF is not the only source. If a node corresponds to a CNCF Entity, especially
a `SimpleEntity`, CNCF can derive knowledge from the Entity model, stored
record, tags, associations, lifecycle, and operation context even when no RDF
predicate exists.

## Node Value Objects

The target `KnowledgeNode` shape is delegation-oriented:

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

The sections below map RDF predicate families to those delegated values.

## Identity

Target value object: `KnowledgeNodeIdentity`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `rdfNode` | RDF subject IRI / blank node | n/a | Direct RDF node name, not a generic external id. |
| `identityLinks.sameAs` | `owl:sameAs`, `schema:sameAs`, `skos:exactMatch` | `same-as` | Strong identity/equivalence candidate. |
| `identityLinks.equivalentTo` | `skos:closeMatch`, `skos:relatedMatch`, domain equivalence predicates | `equivalent-to` or `related` | Weaker than `same-as`; profile decides strength. |
| `identityLinks.canonical` | `schema:mainEntityOfPage`, canonical subject mapping, provider canonical id | `canonical-of` | Often source/profile-defined rather than pure RDF. |
| `externalIdentifiers` | CURIE, provider id, source id, Entity id, Tag id | external id binding | Kept structured by `system/kind/value`. |

Identity mapping must preserve the difference between:

```text
KnowledgeNodeId = CNCF runtime identity
RdfNodeName     = RDF knowledge identity/content
external id     = source/provider/application identifier
```

## Presentation

Target value object: `KnowledgeNodePresentation`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `labels.default` | `rdfs:label`, `skos:prefLabel`, `schema:name`, `foaf:name`, `dc:title`, `dcterms:title` | label/value | Language tag must be preserved. |
| `labels.localized` | language-tagged `rdfs:label`, `skos:prefLabel`, `schema:name` | localized-label | Keyed by language/locale. |
| `labels.alternatives` | `skos:altLabel`, `schema:alternateName` | alias-label | Display alias, not necessarily an alias node. |
| `names.canonical` | canonical label/profile-selected name | canonical-name | Mapping profile selects the canonical display name. |
| `descriptions` | `rdfs:comment`, `schema:description`, `dc:description`, `dcterms:description` | description | Long text may need summary/redaction policy. |

Presentation attributes are display-facing. They are distinct from language
correspondence nodes. A Japanese label and a Japanese correspondence node are not the
same thing.

## Semantics

Target value object: `KnowledgeNodeSemantics`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `semanticTypes` | `rdf:type` | `type-of` / `type-membership` | Primary source for RDF class membership. |
| `semanticTypes` | RDFS/OWL class hierarchy after mapping | `type-of` / `inferred-type` | Inferred type must be marked as inferred. |
| `semanticTypes` | SKOS concept scheme membership | `in-scheme` / `concept-type` | Preserve scheme/source. |
| `roles` | `prov:hadRole`, domain role predicates | `role-of` | Operational role, not display label. |
| `confidence` | extraction score, `schema:confidence`-like provider metadata, ML score | confidence metadata | Usually provider/profile metadata rather than standard RDF. |
| `temporal` | `schema:startDate`, `schema:endDate`, `time:hasBeginning`, `time:hasEnd`, `prov:generatedAtTime` | temporal validity | Distinguish valid time from observation/import time. |
| `lifecycle` | `schema:dateCreated`, `schema:dateModified`, provider state, curation status | lifecycle metadata | CNCF lifecycle state is not automatically RDF lifecycle. |
| `confidentiality` | data classification predicates, application policy metadata | confidentiality classification | Must be projection-safe before admin/MCP output. |

`KnowledgeNodeCategory` is not semantic type. It is only a coarse operational
category such as entity, concept, source, document, chunk, or generated.

## Structure

Target value object: `KnowledgeNodeStructure`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `correspondences.translations` | `schema:workTranslation`, `schema:translationOfWork`, domain translation predicates | `translation-of` | Node-to-node translation correspondence. |
| `correspondences.localizedVersions` | `dcterms:hasVersion`, `dcterms:isVersionOf`, version/localization predicates | `localized-version-of` | Localized representation of the same concept/resource. |
| `correspondences.sameConcepts` | `skos:exactMatch`, `skos:closeMatch`, domain concept equivalence predicates | `same-concept-as` | Language/source-specific node for the same concept. |
| `correspondences.sameResources` | `owl:sameAs`, `schema:sameAs`, domain resource equivalence predicates | `same-resource-as` | Node for the same resource/subject. |
| `correspondences.sourceAlignments` | source alignment metadata, text fragment alignment, translation memory alignment | `source-aligned-with` | Aligned source document/chunk. |
| `correspondences.aliases` | alias predicates, `schema:alternateName`, profile alias mapping | `alias-of` | Alias node, distinct from label-only alias. |
| `classifications.primary` | profile-selected `skos:broader`, `rdf:type`, Tag projection | `classified-by` | Primary classification is profile-selected. |
| `classifications.broader` | `skos:broader`, `schema:isPartOf` when classification profile says so | `broader` | Classification hierarchy. |
| `classifications.narrower` | `skos:narrower` | `narrower` | Classification hierarchy. |
| `hierarchy.parent` | `dcterms:isPartOf`, `schema:isPartOf`, `schema:parentItem`, domain parent predicate | `parent-of` / `part-of` | Tree/path projection only when profile approves. |
| `hierarchy.children` | inverse of parent predicates, `schema:hasPart` | `has-child` / `has-part` | Built from relationships/indexes. |
| `partWhole.partOf` | `dcterms:isPartOf`, `schema:isPartOf`, `schema:partOf`, domain part predicates | `part-of` | Part-whole semantics, not always hierarchy. |
| `partWhole.hasPart` | `dcterms:hasPart`, `schema:hasPart` | `has-part` | Inverse may be generated by mapping profile. |
| `partWhole.memberOf` | `schema:memberOf`, `org:memberOf`, domain membership predicates | `member-of` | Membership is not always containment. |
| `partWhole.hasMember` | inverse member predicates | `has-member` | Often index-derived. |

Structure attributes are canonical operational projections. The source
relationships remain in the WorkingSet and retain predicate/evidence details.

## Sources

Target value object: `KnowledgeNodeSources`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `sourceRefs.primarySource` | `dcterms:source`, `prov:wasDerivedFrom`, `schema:citation` | `derived-from` / `source-of` | Coarse source of the node. |
| `sourceRefs.sourceDocuments` | `schema:mainEntityOfPage`, `foaf:page`, `prov:wasQuotedFrom`, source document mapping | `document-source` | Document-level source. |
| `sourceRefs.sourceChunks` | SIE chunk mapping, text fragment selector, annotation target | `chunk-source` | Often provider metadata, not standard RDF. |
| `sourceRefs.extractedFrom` | extraction evidence, `prov:wasGeneratedBy`, `oa:hasSource` | `extracted-from` | Evidence id should point to extraction details. |
| `sourceRefs.observedIn` | observation event/source, crawl/import run | `observed-in` | Observation provenance, not necessarily truth. |
| `evidenceIds` | RDF statement, named graph, source chunk, operation context | evidence reference | Source of support for the node/attribute. |
| `provenanceIds` | `prov:*`, import run, generator, curation operation | provenance reference | Origin/generation context. |

Source attributes should support explanation and audit. Large source bodies
must remain references/summaries, not inline payloads.

## Bindings

Target value object: `KnowledgeNodeBindings`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `entityBindings` | `cncf:entity`, application entity mapping, external identifier `cncf.entity` | `entity-binding` | Connects business Entity and knowledge node. |
| `tagBindings` | `cncf:tag`, TagSpace projection, Association-backed tag attachment | `tag-binding` | Tag management remains in TagSpace. |
| `entityBindings` | RDF subject mapped to Entity id by profile | `same-resource-as` / `entity-binding` | Entity id and RDF node remain separate. |
| `tagBindings` | SKOS concept or Tag projection | `classified-by` / `tag-binding` | Profile decides whether SKOS concept is also a Tag binding. |

Bindings are CNCF operational links. They should not force RDF subject names to
equal Entity ids or Tag ids.

## Entity-Derived Mapping

Target value objects:

- `KnowledgeNodeIdentity`
- `KnowledgeNodeSemantics`
- `KnowledgeNodeStructure`
- `KnowledgeNodeSources`
- `KnowledgeNodeBindings`
- `KnowledgeNodeOperations`

| Node attribute | Entity source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `identity.externalIdentifiers` | Entity id, entity name, component name | `entity-binding` | Entity id remains distinct from `KnowledgeNodeId` and RDF node. |
| `bindings.entityBindings` | Entity id/name/version | `entity-binding` | Primary route from business object to knowledge node. |
| `semantics.semanticTypes` | Entity type/name/kind, SimpleEntity model metadata | `type-membership` | CNCF can derive semantic type without RDF. |
| `semantics.lifecycle` | Entity lifecycle/state/runtime descriptor | lifecycle metadata | More structured than generic RDF import. |
| `structure.classifications` | Entity kind, Tag attachments, model-level category | `classified-by` | TagSpace remains source of tag management. |
| `bindings.tagBindings` | TagAttachment / TagSpace classification | `tag-binding` | Tag-derived knowledge reflected into node. |
| `structure.hierarchy` | model-level parent/specialization/ownership where defined | `parent-of` / `specializes` | Captures inheritance-like or specialization structure when CNCF model defines it. |
| `structure.partWhole` | Association / containment / ownership | `part-of` / `has-part` / `member-of` | Association may create canonical relationships and node projections. |
| `sources.sourceRefs` | Entity record/version, repository/backend, operation context | `source-of` | Entity record is evidence/source for the knowledge node. |
| `sources.evidenceIds` | Entity record, TagAttachment, Association, operation log | evidence reference | Keeps derivation traceable. |
| `operations` | create/update/read operation context, WorkingSet materialization | operation metadata | Runtime context for how the node was built. |

For `SimpleEntity`, CNCF knows structure and usage. This means Entity-derived
projection can be richer than RDF-derived projection:

```text
SimpleEntity model
  -> semantic type
  -> lifecycle/status
  -> attribute/value structure
  -> tag-derived classification
  -> association-derived relationships
  -> source/evidence/provenance
```

Entity-derived facts should be treated as a particularly important
KnowledgeSpace fact category:

```text
Entity source data
  -> Entity-derived Fact
  -> KnowledgeNode attribute and/or KnowledgeRelationship
```

Examples:

```text
Entity type       -> semantic type fact
Entity attribute  -> value fact
TagAttachment     -> classification fact
Association       -> relationship fact
Lifecycle state   -> operational state fact
```

These facts are not weaker than RDF facts. In CNCF they may be stronger,
because CNCF knows the source model, lifecycle, version, storage context, and
operation context.

This is one reason `KnowledgeNode` should be a CNCF object model, not a thin
RDF node wrapper.

## Similarity / Distance

Target value object: `KnowledgeNodeSimilarity`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `representations` | SIE/vector provider metadata, embedding profile mapping | `similarity-representation` | Representation for similarity/distance search. |
| `representations.method` | embedding model or other representation method | similarity metadata | `embedding` is one method, not the attribute name. |
| `representations.context` | source text/graph/chunk projection used for representation | `represented-from` | Important for explanation and reproducibility. |
| `representations.metric` | cosine, dot-product, Euclidean, or provider metric | similarity metric | Defines distance/similarity interpretation. |
| `searchEntries` | vector store id, collection/index id, vector/search id | similarity search entry | Where the representation is searchable. Raw vector may be externalized or hidden. |
| `status` | indexed/stale/missing/failed | similarity status | Operational state. |

Similarity information may be created by an embedding/vector implementation,
but it is not a standard RDF predicate mapping. It is canonical KnowledgeNode
metadata because semantic retrieval depends on similarity/distance behavior.

## Operations

Target value object: `KnowledgeNodeOperations`

| Node attribute | RDF predicate / source candidate | Relationship kind | Notes |
| --- | --- | --- | --- |
| `materializedAt` | WorkingSet materialization time | n/a | Runtime projection timestamp. |
| `frameIds` | `KnowledgeFrame` membership | frame membership | Built by WorkingSet, not usually RDF. |
| `validationStatus` | shape validation, import validation, curation state | validation metadata | May use SHACL/provider validation input. |
| `attributes` | profile-approved extension values | extension | Non-canonical values only. |

Operational attributes are CNCF runtime/projection metadata. They are separate
from RDF semantics and should not become a dumping ground for canonical facts.

## Mapping Policy

The mapping must be profile-driven.

Core CNCF can define a small default mapping for widely accepted predicates:

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

Application/SIE/provider profiles can add:

- domain-specific predicates;
- ontology-specific property mappings;
- language correspondence predicates;
- Entity/Tag binding predicates;
- similarity/embedding metadata mappings;
- validation/curation metadata mappings.

When a mapping creates a node attribute, it should also create or retain the
source relationship/evidence/provenance path:

```text
KnowledgeNode.structure.partWhole.partOf = parent
  <- KnowledgeRelationship(child --part-of--> parent)
  <- rdfPredicate dcterms:isPartOf
  <- evidence/provenance/source graph
```

## Non-Goals

This mapping note does not make CNCF:

- an RDF triple store;
- a SPARQL query engine;
- a full ontology reasoner;
- a universal RDF-to-object mapper;
- a replacement for application-specific semantic mapping profiles.

The goal is narrower: define how accepted RDF/external knowledge facts become
canonical `KnowledgeNode` value attributes that domain logic can operate.
