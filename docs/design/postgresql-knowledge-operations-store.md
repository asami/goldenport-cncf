# PostgreSQL as a General-Purpose Knowledge Operations Store

Status: design decision record (retrospective; scope-limited)
Recorded: 2026-10-14
Scope: CNCF DataStore-backed knowledge-oriented applications, including KnowledgeHub. This is not a mandate to replace all CNCF storage providers.

## Context

Knowledge-oriented applications need to combine structured entity attributes, evolving semi-structured material, text, relationships, facets and semantic retrieval. Splitting every access pattern into a dedicated relational, document, search, graph and vector database increases operational and synchronization costs before actual workload evidence warrants it.

## Decision and rationale

Prefer PostgreSQL as the initial general-purpose operational store for server-side knowledge workloads, accessed through CNCF's DataStore abstraction rather than through application-specific JDBC or SQL assumptions.

- Relational tables and transactions support Information, candidates, editorial workflow and metadata.
- JSONB supports evolving Material attributes, annotations and facets alongside typed relational fields.
- Full-text indexing/search supports editorial and discovery queries; Japanese tokenization requires explicit evaluation (e.g. suitable tokenizer or pg_bigm), not an assumption that default PostgreSQL FTS is sufficient.
- Recursive CTEs support bounded hierarchical and relationship traversals.
- pgvector is an optional extension for embedding similarity. Relational predicates, JSONB filters, full-text relevance and vector similarity can be composed into hybrid retrieval.
- A single operational store simplifies consistency, deployment and maintenance at the initial scale.

This is a *pragmatic operational knowledge store* choice, not a claim that SQL is a complete semantic knowledge representation.

## Boundaries

- CNCF remains provider-neutral: SQLite is appropriate for local/zero-setup use; PostgreSQL is preferred for applicable server-side knowledge workloads. Other SQL providers remain valid.
- The conceptual progression is Material -> Information Candidate -> Information -> Knowledge. CNCF Information is the operational entity representation; Knowledge may be projected into RDF for semantic representation and interoperability.
- RDF/SPARQL stores (e.g. Fuseki) remain candidates where ontology reasoning, SPARQL or external knowledge graph integration is actually required.
- Dedicated graph stores (e.g. Neo4j) and vector stores (e.g. Qdrant) are deferred until measured graph traversal, retrieval or scaling requirements justify their extra operational cost.
- PostgreSQL does not replace a Knowledge Lake's source material repository or large binary object storage.

## Alternatives and trade-offs

- Multiple specialized stores from day one: stronger specialist features, but additional deployment, synchronization and failure modes; defer.
- RDF triple store as the sole operational database: valuable for semantics and SPARQL, but not presumed best for ordinary editorial transactions and mixed structured queries.
- SQLite everywhere: attractive for local use, but not the default server-side choice for concurrent multi-user knowledge workloads.
- PostgreSQL alone for all knowledge: rejected; RDF semantics and specialized workloads must remain possible.

Costs accepted include PostgreSQL operations, extension lifecycle, index/query tuning, and Japanese-language search evaluation. Hybrid retrieval relevance and performance must be validated against real data; no universal superiority is assumed.

## Implementation guidance

Expose storage through CNCF DataStore contracts; keep SQL dialect/extension details in provider or explicitly scoped query adapters. Define indexes from observed access patterns rather than speculative preemptive indexing. Measure full-text quality, hybrid ranking, vector size and graph traversal before introducing a specialized service.

## Provenance

Retrospective reconstruction of the PostgreSQL selection discussion (spring–September 2026), recorded October 2026. Existing CNCF documentation discusses PostgreSQL for Service Bus journaling and DataStore lifecycle; those are separate decisions and must not be mistaken for the knowledge-store rationale.
