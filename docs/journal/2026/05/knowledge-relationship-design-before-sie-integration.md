# KnowledgeRelationship Design Before SIE Integration

Date: 2026-05-18

This journal entry records the design direction for `KnowledgeRelationship`
before Phase 25 KS-10 hardening. It is a working design note, not yet the
normative runtime schema.

## Why This Note Exists

`KnowledgeNode` and `KnowledgeRelationship` are the two axes of the CNCF
operational knowledge model.

`KnowledgeRelationship` is part of the projection from the RDF / external
knowledge world into a domain-operable object model. The goal is not to make
domain logic manipulate raw RDF triples. The goal is to structure
semi-structured RDF predicate information into CNCF relationship objects that
domain logic can use.

Those relationship objects live in the component-owned `KnowledgeSpace`
WorkingSet. The WorkingSet should make relationship lookup, traversal, direct
node projection, and evidence/provenance access available as ordinary
programming operations, not as raw RDF queries.

Relationships may also be grouped by `KnowledgeFrame`. A frame represents a
purpose/focus/source/query unit of knowledge inside the WorkingSet. For
example, one SIE retrieval result, one Entity context, or one explanation can
contain a bounded set of nodes, relationships, evidence, and provenance.

The frame should also carry origin information. Relationship-level provenance
explains why an individual edge exists; frame-level origin explains where the
whole knowledge unit came from, such as SIE retrieval, Entity projection,
batch import, manual curation, or operation-generated knowledge.

SIE is one concrete input route. The same relationship model must also support
relationships projected from DB Entities, TagSpace / Association structures,
batch imports, manual/admin curation, external graph imports, and generated
application knowledge.

In the SIE integration example, retrieval results are converted into a
temporary or retained `KnowledgeFrame` inside a `KnowledgeWorkingSet`. Domain
logic then runs traversal over `KnowledgeNode` and `KnowledgeRelationship`
objects instead of consuming SIE-specific retrieval rows directly.

For RDF-backed knowledge, the rough mapping is:

```text
RDF node      -> KnowledgeNode
RDF predicate -> KnowledgeRelationship
```

This is not a direct RDF object model. RDF remains the source graph
representation. `KnowledgeRelationship` is the operational semantic structure
that lets domain logic work with RDF predicate-derived relationships without
requiring every business rule to understand RDF predicate URIs directly.

## Current Skeleton

The current KS-08 model is:

```scala
final case class KnowledgeRelationship(
  id: KnowledgeRelationshipId,
  kind: String,
  sourceNodeId: KnowledgeNodeId,
  targetNodeId: KnowledgeNodeId,
  evidenceIds: Vector[KnowledgeEvidenceId],
  provenanceId: Option[KnowledgeProvenanceId],
  attributes: Map[String, String]
)
```

This is enough for basic in-memory graph traversal. It is not enough for SIE
provider integration because it lacks typed relationship kind, original RDF
predicate information, structured qualifiers, similarity/distance context,
temporal validity, and richer evidence/provenance semantics.

## Predicate as the Source of Relationship Semantics

In RDF, the predicate is the relationship:

```text
subject --predicate--> object
```

The predicate often carries the most important semantics of the statement. In
CNCF, that predicate should be transformed into a `KnowledgeRelationship` that
domain logic can use.

See also `knowledge-node-rdf-predicate-attribute-mapping.md` for how selected
predicate families become canonical `KnowledgeNode` value attributes while the
source relationship remains traceable.

The design direction is:

```text
RDF predicate
  -> semantic structuring / predicate mapping
  -> KnowledgeRelationshipKind + KnowledgeRelationship
  -> domain logic
```

`KnowledgeRelationship` should therefore preserve both:

- the original RDF predicate name, when the relationship is RDF-backed;
- the operational relationship kind used by CNCF/domain logic.

Candidate shape:

```scala
final case class KnowledgeRelationship(
  id: KnowledgeRelationshipId,
  kind: KnowledgeRelationshipKind,
  sourceNodeId: KnowledgeNodeId,
  targetNodeId: KnowledgeNodeId,
  rdfPredicate: Option[RdfPredicateName],
  ...
)
```

`rdfPredicate` is not just debug data. It is part of the source semantic
trace. `kind` is the CNCF operational interpretation.

## Relationship Identity

`KnowledgeRelationshipId` is the CNCF internal relationship id inside a
`KnowledgeSpace`.

It must not be assumed to equal:

- RDF predicate URI;
- RDF statement id;
- RDF reification id;
- source row id;
- provider edge id.

RDF statements may not have stable ids. Multiple RDF triples may map to one
operational relationship, and one RDF triple may produce more than one
operational projection. The internal relationship id should be stable inside
the WorkingSet, but it is not the RDF identity.

External statement ids, provider edge ids, and RDF reification ids should be
kept as external identifiers or evidence/source references when needed.

## Relationship Kind

`kind: String` is a placeholder.

KS-10 should introduce `KnowledgeRelationshipKind` or an equivalent typed
extensible model. Candidate core kinds include:

- `is-a`
- `type-of`
- `part-of`
- `has-part`
- `broader`
- `narrower`
- `same-as`
- `related`
- `depends-on`
- `causes`
- `located-in`
- `owned-by`
- `derived-from`
- `mentions`
- `supports`
- `contradicts`

The core vocabulary should remain small. Provider/application mappings should
be able to introduce namespaced custom relationship kinds without falling back
to arbitrary unqualified strings.

## Relationship Semantic Types

Relationship kind and relationship semantic type should also be separated.

The current working distinction is:

```text
KnowledgeRelationshipKind = CNCF operational relationship category
RdfPredicateName          = source predicate identity/content
RelationshipSemanticType  = semantic classification of the relationship
```

Examples:

```text
rdfPredicate = dcterms:isPartOf
kind         = part-of
semanticType = document-structure / containment / provenance-relevant

rdfPredicate = skos:broader
kind         = broader
semanticType = taxonomy / concept-hierarchy

rdfPredicate = owl:sameAs
kind         = same-as
semanticType = identity-equivalence
```

Known relationship semantic type sources:

- RDF predicate URI.
- RDFS/OWL property class.
- OWL object property / datatype property distinction.
- SKOS semantic relation.
- domain relationship definition.
- Association role/kind, when projected from CNCF Association.
- Tag graph edge kind.
- mapping/profile classification.
- extracted assertion type.
- generated or inferred relation type.

Known relationship semantic type facets:

- `system`: vocabulary or authority, such as `rdf`, `owl`, `skos`,
  `schema.org`, `cncf.association`, `cncf.tag`, or application namespace.
- `name`: canonical type name or URI.
- `source`: predicate/profile/provider that supplied the type.
- `profile`: mapping profile that accepted the relationship type.
- `propertyKind`: object property, datatype property, annotation property,
  domain relation, operational relation, or generated relation.
- `directionPolicy`: directed, inverse-normalized, symmetric, or bidirectional.
- `cardinalityPolicy`: one-to-one, one-to-many, many-to-many, optional,
  required, or unknown.
- `confidence`: confidence for extracted or inferred relationship types.
- `provenance`: provenance/evidence reference for the type assignment.
- `status`: asserted, inferred, generated, deprecated, rejected, or candidate.

Candidate shape:

```scala
final case class KnowledgeRelationshipSemanticType(
  system: String,
  name: String,
  propertyKind: Option[KnowledgePropertyKind],
  directionPolicy: Option[KnowledgeRelationshipDirectionPolicy],
  profile: Option[String],
  confidence: Option[Double],
  provenanceId: Option[KnowledgeProvenanceId],
  status: KnowledgeSemanticTypeStatus
)
```

The exact type names can change. The essential point is that semantic type is
multi-valued and structured. It should be possible to say that a relationship
is operationally `part-of`, sourced from `dcterms:isPartOf`, semantically a
document containment relation, and accepted by a specific SIE mapping profile.

## Relationship Semantic Type Categories

Known categories that KS-10 should reserve or implement:

- `rdf-object-property`: relationship to another RDF node.
- `rdf-datatype-property`: relationship to a literal/value.
- `rdf-annotation-property`: metadata/comment/label-like predicate.
- `identity-equivalence`: same-as/equivalence mapping.
- `type-membership`: rdf:type, class membership, domain type membership.
- `taxonomy`: broader/narrower/classification relationship.
- `part-whole`: part-of/has-part/member-of containment.
- `dependency`: depends-on, requires, derived-from.
- `causal`: causes, contributes-to, prevents.
- `provenance`: source/origin/generated-by relation.
- `evidence`: supports, contradicts, observes, asserts.
- `document-structure`: document/chunk/section containment.
- `entity-binding`: relationship between business Entity and knowledge node.
- `tag-binding`: relationship between TagSpace classification and knowledge
  node.
- `language-correspondence`: translation, localization, same-concept,
  same-resource, source-alignment, or alias relationship between nodes in
  different languages/locales.
- `association-projection`: CNCF Association projected into KnowledgeSpace.
- `similarity-context`: relation used to define or connect similarity/distance
  representation context.
- `retrieval-context`: relation used by query/explanation projection.
- `generated-relation`: AI/extraction-produced relation.
- `inferred-relation`: rule/ontology/profile-inferred relation.
- `candidate-relation`: proposed but not accepted relation.

These categories are not all relationship kinds. Some are type facets attached
to a relationship whose operational kind may be more specific.

## Language / Locale Correspondence Relationships

Language correspondence information should be represented as canonical
relationships, then projected onto `KnowledgeNode` for direct traversal.

Examples:

```text
enConcept --translation-of--> jaConcept
jaTerm --localized-version-of--> enTerm
jaChunk --source-alignment--> enChunk
```

Candidate relationship kinds:

- `translation-of`
- `localized-version-of`
- `same-concept-as`
- `same-resource-as`
- `source-aligned-with`
- `alias-of`

Candidate relationship semantic type category:

```text
language-correspondence
```

Important qualifiers:

- source language;
- target language;
- source locale;
- target locale;
- translation direction;
- alignment method;
- confidence;
- source document/chunk evidence;
- human-curated vs machine-generated status.

This relationship should be the source of truth. The corresponding
`KnowledgeNode.structure.correspondences` field is a derived operational
projection for business logic and admin/debug navigation.

This is intentionally stronger than localized labels. A localized label says
"how to display this node in a language." A correspondence relationship says
"which other node corresponds to this node in another language or locale."

## Predicate Mapping

The mapping from RDF predicate to `KnowledgeRelationshipKind` is a major
design boundary.

Possible mapping sources:

- SIE provider mapping.
- component-local mapping.
- ontology/profile/shape mapping.
- CNCF default vocabulary mapping for common predicates.
- application-specific mapping supplied by the domain model.

Examples:

```text
skos:broader      -> broader
skos:narrower     -> narrower
owl:sameAs        -> same-as
dcterms:isPartOf  -> part-of
schema:memberOf   -> part-of or member-of, depending on profile
```

CNCF should not hardcode a large ontology mapping in core. It can provide the
model and extension points. The actual mapping should be profile/provider
driven, with a small set of CNCF defaults only where the semantics are stable.

## Relationship as Canonical Generic Edge

`KnowledgeRelationship` is the canonical generic graph edge in KnowledgeSpace.

It should be able to carry:

- source node id;
- target node id;
- operational relationship kind;
- original RDF predicate;
- evidence ids;
- provenance id;
- confidence;
- temporal validity;
- qualifiers;
- source statement reference;
- similarity/distance representation metadata, when the relationship itself is
  represented for similarity search;
- contradiction/lifecycle status hooks.

This matters because many knowledge facts are relationship-centered. In an
operational knowledge graph, the edge is often the knowledge.

The minimum canonical information should be:

```text
source node
target node or literal/value projection
operational relationship kind
original RDF predicate, when RDF-backed
mapping/profile that produced the relationship kind
evidence and provenance
qualifiers such as time, role, confidence, source graph, and assertion status
```

This is the information that business logic should be able to consume without
parsing RDF directly.

## Direction and Inverse Relationships

RDF predicates are directed. Domain logic often wants a canonical direction
that is not identical to every source predicate.

Examples:

```text
child dcterms:isPartOf parent  -> child --part-of--> parent
parent schema:hasPart child    -> child --part-of--> parent
```

Both predicates can produce the same operational relationship kind and
direction if the mapping profile defines them as inverses. The original RDF
predicate must still be retained so that evidence/provenance can explain why
the relationship exists.

Some relationship kinds are symmetric:

```text
same-as
related
equivalent-to
```

KS-10 should therefore distinguish:

- source RDF direction;
- operational relationship direction;
- inverse predicate mapping;
- symmetric relationship policy.

The WorkingSet indexes can continue to index by source and target, but the
model should know whether reversing an edge preserves meaning.

## Literal Objects and Value Relationships

Not every RDF object should become a `KnowledgeNode`.

RDF triples whose object is a literal can map to:

- a typed node attribute;
- a value-bearing `KnowledgeRelationship`;
- a literal/value node, only when a profile intentionally models literals as
  nodes;
- evidence/provenance metadata rather than knowledge graph structure.

Example:

```text
product schema:sku "ABC-123"
```

For many applications this is better represented as a typed property on the
product node than as a separate literal node. In contrast, relationships to
documents, concepts, entities, tags, or external subjects should usually be
node-to-node relationships.

The rule should be profile-driven:

```text
RDF object IRI/blank node -> usually KnowledgeNode + KnowledgeRelationship
RDF object literal        -> typed value, unless profile promotes it to a node
```

## Standard Relationship-Derived Node Fields

Some relationship patterns are common enough that domain logic should be able
to access them directly from `KnowledgeNode` without traversing arbitrary
edges every time.

Examples:

- `is-part-of`
- broader/narrower classification;
- same-as/equivalence;
- type/class membership;
- source/document/chunk membership.

This does not make `KnowledgeRelationship` secondary. It means selected
relationships can be projected into direct `KnowledgeNode` fields as a
denormalized operational convenience.

Rule:

```text
KnowledgeRelationship = canonical generic edge
KnowledgeNode field   = profile-approved direct projection of common edge
```

For example:

```text
RDF:
  child dcterms:isPartOf parent

KnowledgeSpace:
  KnowledgeRelationship(child --part-of--> parent, rdfPredicate=dcterms:isPartOf)
  KnowledgeNode(child).partOf = parent
```

The relationship preserves the graph fact, predicate, evidence, provenance, and
qualifiers. The node field makes common semantics fast and ergonomic for
business logic.

## Relationship-to-Node Projection Policy

The hard part is not whether projection is possible. The hard part is deciding
which projected facts become canonical node information.

`KnowledgeRelationship` should remain the complete graph/evidence form.
`KnowledgeNode` should expose selected relationship-derived facts as typed
canonical projections when those facts are stable and operationally important.

Projection categories that are likely canonical:

- identity/equivalence relationships;
- language/locale correspondence relationships;
- semantic type and class membership relationships;
- hierarchy/classification relationships;
- part-whole and containment relationships;
- source/evidence/provenance relationships;
- Entity and Tag binding relationships;
- document/chunk/source structure relationships;
- similarity/distance context relationships;
- temporal/effective relationships;
- confidence/assertion/lifecycle relationships;
- authorization/confidentiality classification relationships;
- explanation/retrieval context relationships.

Relationship categories that should usually remain relationship-only:

- ad hoc provider-specific debug edges;
- low-confidence extraction candidates that are not accepted;
- transient import pipeline edges;
- raw parser/reification internals;
- relationships whose semantics are not stable outside one provider.

Canonical node projection requires a defined mapping rule:

```text
relationship kind + semantic type + qualifiers
  -> node projection field
  -> evidence/provenance trace back to relationship ids
```

This means node-side attributes are not arbitrary denormalization. They are a
contracted projection surface for business logic.

## Qualifiers and N-ary Semantics

Some relationships are not simple binary edges.

Examples:

```text
Alice worksFor Acme since 2020
Alice hasRole VP at Acme
Product partOf Assembly with quantity 4
```

These need qualifiers. KS-10 should avoid a relationship model that can only
hold source and target.

Candidate qualifier categories:

- temporal validity: validFrom, validTo, observedAt;
- role or function;
- quantity or measure;
- source graph or dataset;
- confidence;
- extraction method;
- language/datatype;
- domain-specific attributes.

Not every qualifier needs a first-class typed field in KS-10, but the model
should have a structured qualifier/value space rather than only
`Map[String, String]`.

## Relationship Similarity / Distance

Similarity / distance representation is not only node-level.

Some relationship facts may be represented as text or graph context for
similarity search:

```text
Alice works for Acme as VP.
```

The similarity representation context for this sentence is knowledge
information. It may be attached to a relationship, assertion, evidence item, or
query result. KS-10 should allow relationship-level similarity metadata or
references, even if raw vectors are not exposed by default.

Candidate fields mirror the node side:

- representation method, such as embedding;
- similarity/distance metric;
- representation context/profile;
- search provider/store;
- collection/index, when implemented by a vector index;
- representation id or payload reference;
- generated-at;
- source/evidence reference;
- status.

Similarity score or distance remains query-result data, not relationship state.

## Evidence and Provenance

Relationship evidence is essential.

For RDF-backed data, evidence may point to:

- RDF statement/triple;
- named graph;
- source document/chunk;
- source Entity record;
- provider import run;
- AI extraction output;
- operation context.

Provenance should explain where the relationship came from and how it was
generated. Evidence supports the truth or source of the relationship;
provenance records origin/generation context. They overlap operationally but
should not be collapsed into one free-form attribute.

## Fact, Assertion, and Observation Boundary

`KnowledgeRelationship` is close to `KnowledgeFact`, but they are not always
the same.

Useful distinction:

```text
Observation: what was observed
Assertion:   what a source claims
Fact:        normalized knowledge proposition
Relationship: graph projection of a fact/proposition
```

Example:

```text
Observation:
  crawler observed "Alice works at Acme" on page X

Assertion:
  page X asserts Alice worksFor Acme

Fact:
  Alice worksFor Acme

Relationship:
  Alice --works-for--> Acme
```

KS-10 does not have to add all of these runtime types. But
`KnowledgeRelationship` should not be designed so narrowly that a later
`KnowledgeFact`, `KnowledgeAssertion`, or `KnowledgeObservation` cannot be
introduced.

## KS-10 Design Direction

KS-10 should do the following for relationships:

- Introduce typed `KnowledgeRelationshipKind`.
- Add direct `rdfPredicate: Option[RdfPredicateName]` or equivalent source
  predicate field.
- Introduce structured relationship semantic types, separated from
  `KnowledgeRelationshipKind` and `RdfPredicateName`.
- Reserve semantic type categories for RDF property kinds, identity,
  type-membership, taxonomy, part-whole, dependency, causal, provenance,
  evidence, document structure, Entity/Tag binding, Association projection,
  similarity/retrieval context, generated/inferred/candidate relations.
- Keep `KnowledgeRelationshipId` as CNCF internal relationship identity.
- Add or reserve structured qualifiers.
- Add confidence and temporal validity hooks where needed.
- Add relationship-level similarity/distance representation metadata/reference
  support.
- Preserve evidence/provenance as first-class relationship context.
- Define a small profile-driven list of relationship-derived direct
  `KnowledgeNode` fields.
- Keep predicate mapping provider/profile driven, with only small CNCF default
  mappings.
- Preserve future room for fact/assertion/observation separation.

## Non-Goals

KS-10 should not:

- turn CNCF into an RDF triple store;
- force domain logic to consume raw RDF predicate URIs;
- hardcode a large ontology mapping in CNCF core;
- make relationship id equal RDF predicate URI or RDF statement identity;
- collapse evidence, provenance, assertion, and observation into one
  unstructured attribute map;
- expose raw similarity vectors in admin/debug projection by default.

## Expected Outcome

After KS-10, `KnowledgeRelationship` should be strong enough to represent
predicate-derived operational semantics before `textus-sie` providers are
connected. KS-11 can then refine projection/admin/query surfaces, and KS-12 can
validate SIE RDF DB / Vector DB behavior against the hardened model.
