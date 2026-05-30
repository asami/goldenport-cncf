# Phase 27 KE-12: Person / Organization Editor Screens

Date: 2026-05-28

## Summary

KE-12 makes `person` and `organization` first-class app-facing Information
domains in `textus-knowledge-editor`. KE-11 made those domains available as
book-adjacent authority candidates; KE-12 adds the direct editor workflows
needed to create, search, edit, validate, confirm, publish, and materialize
Person and Organization Information outside the book detail page.

## Decisions

- Person and Organization use the CNCF Information editor profiles introduced
  in KE-11.
- The editor keeps Person and Organization as ordinary Information domains:
  `person` and `organization`.
- External identifiers such as Wikidata, DBpedia, VIAF, ISNI, ORCID, ROR, LCCN,
  NDL, and publisher ids remain identity bindings or lookup keys, not CNCF ids.
- Bulk import is included and uses the existing Record import provider path.
  Line-based import maps each line to `name`.
- Bulk import is Job-backed through `execution :: async-job`, matching the
  current Information import policy.
- Book author/editor/publisher candidates link toward Person/Organization
  editor workflows, but candidate selection does not automatically create
  confirmed authority knowledge.

## Implemented Behavior

- `PersonEditor` and `OrganizationEditor` services are available in TKE CML.
- Both domains have dashboard, list, seed/create, import, detail, edit,
  resolver, lifecycle, publish, materialize, and tag-sync operations.
- TKE Static Form pages expose Person and Organization dashboards, lists,
  detail pages, and update screens.
- The overall dashboard includes Person and Organization counts and recent
  Information sections.
- Resolver candidates can be created from local authority fields or DBpedia
  lookup summaries without storing raw provider payloads.

## Deferred

- Authority merge/split workflow remains KE-15.
- Relationship role/order/qualifier editing remains KE-14.
- Work / Edition / Series / Volume book structure expansion starts in KE-13.
