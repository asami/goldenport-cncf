# RDF 1.5+hop Neighborhood

status=note
created_at=2026-06-22
updated_at=2026-06-23
scope=shared-concept
applies_to=CNCF, SIE/TKE, Cozy BoK

## Purpose

`1.5+hop` is the shared graph-explanation concept for focused RDF / knowledge
node inspection. CNCF should treat it as a knowledge projection and explanation
concept, not as a UI-only Cozy behavior.

The rule is:

- show the focused node;
- show direct 1-hop relationships;
- add only the extra triples required to explain the meaning of the focused
  node and its directly related nodes;
- choose those extra triples from node schema metadata and concrete Information
  schemas when available;
- keep fallback descriptive predicates small and deterministic.

The `+` is schema-guided expansion. It is not unconstrained 2-hop traversal.

## Concept Layers

The shared model has three layers:

1. `informationView`: the canonical metadata root for interpreting an RDF /
   knowledge node as Information.
2. `informationView.concept`: the graph explanation concept, normally
   `1.5+hop`.
3. `informationView.informationSchemas`: concrete Information schemas selected
   per node type, category, provider, or explicit node metadata.

This means `Information` is not one global schema. It is the interpretation
framework. A glossary term, article, video artifact, source document, RDF
resource, or TKE Information projection may each use a different concrete
Information schema under the same `1.5+hop` concept.

## CNCF Boundary

CNCF owns generic knowledge projection concepts:

- `KnowledgeNode`;
- `KnowledgeRelationship`;
- `Evidence`;
- provenance;
- external identifier mapping;
- query/traversal/explanation inputs;
- the default predicate role profile `cncf-rdf-1.5-hop-v1`.

CNCF does not own TKE editing state, SIE storage providers, or Cozy UI behavior.
Those projects project their local semantics into CNCF-compatible explanation
metadata.

`1.5+hop` should be expressed as an explanation profile over CNCF knowledge
concepts. A SIE/TKE provider may project Information-derived metadata into
CNCF `KnowledgeSpace`, but CNCF core should remain storage-neutral and
provider-neutral.

## Information View Metadata Contract

Graph metadata may expose the shared concept as:

```json
{
  "informationView": {
    "name": "cncf-rdf-1.5-hop-information-view-v1",
    "label": "CNCF RDF 1.5+hop Information View",
    "concept": "1.5+hop",
    "attributes": [
      "information.schema",
      "information.type",
      "information.category",
      "information.identity",
      "information.description",
      "information.links",
      "information.hierarchy",
      "information.provenance",
      "schema.required",
      "schema.directional",
      "schema.expansion"
    ],
    "informationSchemas": [
      {
        "name": "rdf-resource-information-v1",
        "label": "RDF Resource Information",
        "match": {"nodeTypes": ["uri", "literal"]},
        "requiredPredicates": ["rdf:type"],
        "descriptivePredicates": ["rdfs:label", "schema:name"]
      }
    ],
    "predicateProfile": {
      "name": "cncf-rdf-1.5-hop-v1",
      "roles": {
        "identity": ["rdf:type", "owl:sameAs", "skos:exactMatch"],
        "descriptive": ["rdfs:label", "skos:definition"],
        "link": ["rdfs:seeAlso", "schema:about"],
        "hierarchy": ["skos:broader", "skos:narrower"],
        "provenance": ["dcterms:source", "prov:wasDerivedFrom"]
      }
    }
  }
}
```

Node metadata may select or refine a concrete Information schema:

```json
{
  "id": "https://example.com/resource",
  "node_type": "uri",
  "category": "concept",
  "informationSchema": "rdf-resource-information-v1",
  "schema": {
    "requiredPredicates": ["rdf:type", "rdfs:label"],
    "descriptivePredicates": ["skos:definition"],
    "outgoingRequiredPredicates": ["schema:about"],
    "incomingRequiredPredicates": ["schema:mentions"]
  }
}
```

The field `informationView` names the view/framework. The field
`informationView.concept` names the `1.5+hop` expansion semantics, and
`informationView.informationSchemas` contains concrete schemas. Avoid modeling
the concept as a single schema object, because that incorrectly implies there
is only one Information schema.

## Common Predicate Profile

CNCF owns the generic shared predicate profile `cncf-rdf-1.5-hop-v1`.
It is intentionally small and role-based. Applications, SIE providers, and
publication tools may override or extend it, but they should not replace the
concept with a large fixed ontology in CNCF core.

The default roles are:

- `identity`: `rdf:type`, `owl:sameAs`, `schema:sameAs`, `skos:exactMatch`,
  `skos:closeMatch`.
- `descriptive`: `rdfs:label`, `rdfs:comment`, `skos:prefLabel`,
  `skos:altLabel`, `skos:definition`, `schema:name`, `schema:title`,
  `schema:description`, `schema:summary`.
- `link`: `rdfs:seeAlso`, `schema:about`, `schema:url`, `schema:memberOf`.
- `hierarchy`: `skos:broader`, `skos:narrower`, `dcterms:isPartOf`,
  `dcterms:hasPart`, `schema:isPartOf`, `schema:hasPart`.
- `provenance`: `dcterms:source`, `prov:wasDerivedFrom`,
  `prov:generatedAtTime`, `rdfs:isDefinedBy`.

TKE/SIE may add Information-specific predicates such as
`textus:primaryRdfAnchor` through its provider profile.

## Expansion Semantics

Given a focused node `F`:

1. Include `F`.
2. Include all relationships where `F` is source or target.
3. Include the opposite endpoint nodes of those relationships.
4. For each visible node, select its concrete Information schema.
5. Read node-level schema predicates and selected Information-schema-required
   predicates.
6. If both are absent, use the active predicate profile as fallback.
7. Include only relationships whose predicates match required or descriptive
   predicate sets.
8. Include newly reached nodes from those required relationships.
9. Repeat bounded required expansion with a small implementation limit.
10. Mark graph roles as `focus`, `near`, or `schema`.

The output must be deterministic and bounded. It should support both UI graph
views and API/explanation projections.

## Relationship To SIE, TKE, And Cozy

- TKE owns curated Information entity editing semantics.
- SIE owns RDF/vector provider integration and explanation APIs.
- CNCF owns storage-neutral KnowledgeSpace projection boundaries and default
  predicate roles.
- Cozy BoK and SmartDox consume published metadata for user-facing graph
  navigation.

The same node may therefore be viewed in three layers:

- TKE/SIE: curated Information and RDF anchor/editing state;
- CNCF: generic knowledge node, relationship, evidence, and provenance
  projection;
- Cozy BoK: dashboard and RDF graph viewer rendering.

## Follow-Up

- Define how `informationView` appears in CNCF API responses for
  explain/traverse operations.
- Decide which concrete Information schemas are project-owned and which are
  shared defaults.
- Keep external RDF graph import separate from local curated knowledge
  projection.
