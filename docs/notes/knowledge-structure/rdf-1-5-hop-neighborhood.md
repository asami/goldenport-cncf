# RDF 1.5+hop Neighborhood

status=note
created_at=2026-06-22
scope=shared-concept
applies_to=CNCF, SIE/TKE, Cozy BoK

## Purpose

`1.5+hop` is the shared graph-explanation rule for focused RDF / knowledge
node inspection. CNCF should treat it as a knowledge projection and explanation
concept, not as a UI-only Cozy behavior.

The rule is:

- show the focused node;
- show direct 1-hop relationships;
- add only the extra triples required to explain the meaning of the focused
  node and its directly related nodes;
- choose those extra triples from node schema metadata when available;
- keep fallback descriptive predicates small and deterministic.

The `+` is schema-driven expansion. It is not unconstrained 2-hop traversal.

## TKE Information Schema Basis

The initial schema model for `1.5+hop` comes from the TKE Information entity
schema. TKE Information holds curated semantic editing state: RDF anchors,
Information Links, identifiers, classifications, source evidence, review state,
RDF types, and RDF notes.

CNCF must not depend on TKE concrete classes. Instead, TKE/SIE can project that
Information schema into generic RDF / knowledge node explanation metadata:

- required predicates: facts needed to identify a node;
- descriptive predicates: facts needed to explain the node to a human;
- outgoing required predicates: owned or linked facts that complete the node's
  meaning;
- incoming required predicates: reverse facts that explain why another node
  refers to this node;
- evidence/provenance predicates: facts needed to audit source and generation.

This makes the Information entity schema a semantic source for RDF node schema,
while CNCF keeps the boundary generic.

## CNCF Boundary

CNCF owns generic knowledge projection concepts:

- `KnowledgeNode`;
- `KnowledgeRelationship`;
- `Evidence`;
- provenance;
- external identifier mapping;
- query/traversal/explanation inputs.

`1.5+hop` should be expressed as an explanation profile over these concepts.
A SIE/TKE provider may project Information-schema-derived metadata into CNCF
KnowledgeSpace, but CNCF core should not contain TKE-specific editing behavior,
resolver behavior, or provider classes.

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

Graph metadata can expose this profile as:

```json
{
  "predicateProfile": {
    "name": "cncf-rdf-1.5-hop-v1",
    "roles": {
      "identity": ["rdf:type", "owl:sameAs"],
      "descriptive": ["rdfs:label", "schema:name"],
      "link": ["rdfs:seeAlso"],
      "hierarchy": ["skos:broader", "skos:narrower"],
      "provenance": ["dcterms:source", "prov:wasDerivedFrom"]
    }
  }
}
```

## Expansion Semantics

Given a focused node `F`:

1. Include `F`.
2. Include all relationships where `F` is source or target.
3. Include the opposite endpoint nodes of those relationships.
4. For each visible node, inspect its schema-required predicates.
5. Include only relationships whose predicates match required or descriptive
   predicate sets.
6. Include newly reached nodes from those schema-required relationships.
7. Repeat bounded schema-required expansion with a small implementation limit.
8. Mark graph roles as `focus`, `near`, or `schema`.

The output must be deterministic and bounded. It should support both UI graph
views and API/explanation projections.

## Relationship To SIE And Cozy

SIE/TKE provides the Information-centered schema and materialization rules.
CNCF provides the generic KnowledgeSpace and operation boundaries. Cozy BoK and
SmartDox use the resulting metadata for user-facing graph navigation.

The same node may therefore be viewed in three layers:

- TKE/SIE: curated Information and RDF anchor/editing state;
- CNCF: generic knowledge node, relationship, evidence, and provenance
  projection;
- Cozy BoK: dashboard and RDF graph viewer rendering.

## Follow-Up

- Define the canonical metadata field names for Information-schema-derived RDF
  node explanation profiles.
- Decide which predicates are global defaults and which are type-specific.
- Confirm how `1.5+hop` appears in CNCF API responses for explain/traverse
  operations.
- Keep external RDF graph import separate from local curated knowledge
  projection.
