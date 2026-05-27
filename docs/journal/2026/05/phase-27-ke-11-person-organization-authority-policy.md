# Phase 27 KE-11: Person / Organization Authority Policy

Date: 2026-05-27

## Summary

KE-11 adds `person` and `organization` as first-class Information domains for
book-adjacent authority work. Book contributors and publishers are no longer
treated only as untyped strings: author, editor, translator, publisher, imprint,
and institution names can become reviewable authority candidates, then selected
candidates can materialize as surrounding Knowledge nodes in the book
1.5hop+ frame.

## Policy

- `person` and `organization` are independent Information domains.
- KE-11 exposes them through book-driven authority candidates and
  materialization, not through full standalone Person/Organization editor
  screens.
- Person requires `name`; Organization requires `name`.
- External identifiers remain identity bindings and are never CNCF ids.
- Supported supplied anchors include Wikidata, DBpedia, VIAF, ISNI, ORCID,
  LCNAF, NDL authority ids, ROR, and publisher-local ids.
- Live authority lookup in KE-11 is limited to local fields and DBpedia.
- Raw DBpedia/provider payloads are not stored in InformationSpace or projected
  into editor responses.

## Book Relationships

Selected book candidates materialize into simple surrounding nodes and
relationships:

| Source field | Default node kind | Relationship |
| --- | --- | --- |
| authors | person | authored-by |
| editors | person | edited-by |
| publisher | organization | published-by |

If a candidate explicitly carries `organization` kind, author/editor fields may
also materialize Organization nodes. Relationship qualifiers such as contributor
role, order, translation language, page range, and edition/volume context are
deferred to KE-13.

## Completion

- CNCF provides `person` and `organization` Information editor profiles.
- TKE local resolver creates reviewable contributor and publisher candidates
  from supplied book fields.
- DBpedia resolver preserves candidate field path and kind when lookup is used.
- Selected candidates appear in Knowledge materialization as support nodes and
  relationships in the book KnowledgeFrame.
