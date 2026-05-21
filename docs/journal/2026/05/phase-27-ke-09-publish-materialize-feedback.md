# Phase 27 KE-09 Publish / Materialize Feedback

## Summary

KE-09 completes the editor-side feedback loop for the book, paper, and web
resource workflows in `textus-knowledge-editor`.

The editor keeps `InformationSpace` as the curated editing source of truth.
Publish and materialize operations now go through a `KnowledgeEngineProvider`
boundary before updating publication state or component-local `KnowledgeSpace`.
The current provider is local and deterministic, using the existing CNCF
InformationSpace-to-KnowledgeSpace materialization path. It is intentionally
replaceable by later SIE-backed provider wiring.

## Runtime Contract

- `publishBook`, `publishPaper`, and `publishWebResource` call
  `KnowledgeEngineProvider.publishInformation`.
- Publication status is recorded in `InformationSpace` only after provider
  success.
- Provider publish failures return structured operation failures and leave the
  item non-published.
- `materializeBook`, `materializePaper`, and `materializeWebResource` call
  `KnowledgeEngineProvider.materializeInformation`.
- Materialization stores the returned snapshot in component-local
  `KnowledgeSpace`.
- Provider materialization failures leave `KnowledgeSpace` unchanged.

## Editor Feedback

Editor responses now expose the operational state needed by a user or Web page:

- field validation issues;
- selected and unselected resolution candidates;
- candidate evidence and confidence;
- confirmation and publication status;
- publication target, message, provider status, and KnowledgeFrame id;
- KnowledgeSpace state and counts;
- compact KnowledgeFrame summaries;
- compact KnowledgeNode summaries;
- compact KnowledgeRelationship summaries;
- compact KnowledgeFact summaries;
- compact evidence and provenance summaries.

The summaries are bounded and deterministic. They are intended for authoring
feedback, not raw graph export.

## Raw Payload Policy

The editor projection must not expose raw RDF triples, raw vector payloads,
raw provider JSON, raw DBpedia payloads, or raw HTML bodies.

Provider and source details are represented as source references, evidence,
provenance, identifiers, confidence, and compact summaries. This keeps
ordinary editing focused on curated meaning rather than provider internals.

## Deferred Work

KE-10 owns usability smoke and Phase 27 closure. It does not add new provider
features.

Future provider work can replace the local knowledge-engine provider with
SIE-backed RDF/vector publication and richer external source behavior while
preserving the editor response contract established here.
