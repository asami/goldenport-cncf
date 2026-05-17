# Tag Graph Polyhierarchy Model

This note records the Phase 25 KS-03 DAG/polyhierarchy Tag graph model
decision. It is a planning note, not a runtime schema.

## Purpose

KS-03 defines how CNCF can move beyond strict tree Tags without breaking the
Phase 20 Tag baseline.

The core decision is that `Tag.parentId` and `TagTree` remain the canonical
strict hierarchy owned by `TagSpace`. DAG/polyhierarchy behavior is modeled as
a separate `KnowledgeSpace` graph projection/mapping layer.

## Phase 20 Baseline

Existing Tag behavior:

- `Tag.parentId` is a single parent pointer.
- `TagTree` is the resident strict-tree lookup structure.
- Tag move rejects cycles.
- Descendant expansion is based on the strict tree.
- `TagAttachment` is Association-backed and links source Entities to Tags.
- `tag_search_entities` expands descendants by default and then searches
  `TagAttachment` sources.

KS-03 does not change this behavior.

## TagSpace and KnowledgeSpace Separation

`TagSpace` remains the management boundary for Tags:

- Tag master data.
- strict tree parent/path management.
- `TagTree` resident lookup.
- Tag navigation and existing descendant expansion.
- `TagAttachment` based classification/search behavior.

`KnowledgeSpace` is the management boundary for knowledge graph projection:

- knowledge nodes projected from Tags, Entities, source material, and external
  subjects;
- typed graph relationships;
- evidence and provenance links;
- optional traversal/search/explanation inputs.

KS-03 does not merge these spaces. Tags may be projected into KnowledgeSpace as
knowledge nodes, and TagAttachment may contribute evidence or relationships,
but Tag lifecycle and canonical hierarchy stay TagSpace-owned.

## Graph Extension Position

DAG/polyhierarchy is not the canonical Tag hierarchy.

Canonical hierarchy:

- single primary parent;
- one canonical path;
- strict tree navigation;
- existing descendant search behavior.

KnowledgeSpace graph projection:

- additional classification paths;
- cross-cutting links;
- aliases and equivalence;
- broader/narrower semantic relationships;
- optional search/navigation projection inputs.

This keeps existing Tag admin, Tag paths, resident tree cache, and
TagAttachment search stable while enabling richer knowledge graph behavior in
later slices.

## Edge Kind Candidates

Candidate KnowledgeSpace edge kinds related to Tags:

- `additional-parent`: secondary parent-like classification.
- `related`: loose semantic relationship.
- `alias`: alternate label/path relationship.
- `same-as`: identity/equivalence relationship to another Tag or external
  subject.
- `broader`: semantic broader-than relationship.
- `narrower`: semantic narrower-than relationship.
- `see-also`: navigation or documentation hint.

These edge kinds are not TagSpace tree mutations. KS-03 does not finalize
storage shape. Concrete relationship model and evidence belong to KS-04.

## Cycle Policy

- Strict Tag tree cycles remain forbidden.
- KnowledgeSpace graph projection cycle policy is edge-kind specific.
- `additional-parent`, `broader`, and `narrower` should normally be acyclic in
  their directed interpretation.
- `related`, `alias`, `same-as`, and `see-also` may be symmetric or cyclic
  depending on their future semantics.
- Cycle validation belongs to the KnowledgeSpace relationship layer, not to
  `TagTree`.

## Projection Policy

Strict tree projection:

- Uses primary parent/path.
- Powers existing Tag navigation and descendant expansion.
- Remains the default behavior for `tag_search_entities`.

KnowledgeSpace graph projection:

- May include additional graph edges for optional expansion.
- May support alternate navigation views.
- May provide RDF/external knowledge graph projection.
- Must be opt-in until KS-05 defines query/projection behavior.

## Relationship to Later Slices

- KS-04 defines concrete knowledge node, relationship, evidence, and
  provenance model shape.
- KS-05 defines query/projection surfaces and whether graph edges affect
  search expansion.
- KS-06 validates the model through `textus-sie`.

## KS-03 Decisions

- Do not multi-parent `Tag` by changing `Tag.parentId`.
- Do not replace `TagTree`.
- Do not change existing `TagAttachment` or `tag_search_entities` default
  behavior.
- Do not push DAG/polyhierarchy into TagSpace.
- Treat DAG/polyhierarchy as KnowledgeSpace graph projection/mapping.
- Allow Tags and TagAttachments to be projected into KnowledgeSpace, while
  keeping Tag management TagSpace-owned.
- Keep RDF store and SIE provider behavior outside CNCF core.
