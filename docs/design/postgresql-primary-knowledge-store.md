# PostgreSQL primary Knowledge Store

Document role: stable architecture decision for the CNCF / KnowledgeHub
integration line, adopted on 2026-10-14. This decision selects the practical
primary store; it does not claim implemented backend support or acceptance.
The [shared rules](../../RULE.md), [repository rules](../rules/repository-rules.md)
and [document lifecycle](../../ai/directive/core/document-lifecycle.md) govern
its interpretation. The [Phase 96 persistence specification](../spec/postgresql-phase-96-persistence.md)
defines the required native behavior and evidence.

## Scope

The decision applies to practical persistence and knowledge operations in this
CNCF / KnowledgeHub integration line. It does not migrate every CNCF consumer,
change global datastore defaults or assign application semantics to a database.
Phase 96 requires real PostgreSQL persistence before its current mutation and
reference handoffs can be accepted. MySQL may coexist; SQLite and memory retain
their regression responsibilities.

## Decision

PostgreSQL is the primary practical Knowledge Store. It is required alongside
the retained backend obligations, rather than an optional future backend.
RDF retains semantic representation, Knowledge Graph and external-knowledge
connection responsibilities. A dedicated vector or graph database requires
demonstrated insufficiency of the admitted PostgreSQL solution and a separate
architecture decision.

## Rationale

Relational data, JSON/JSONB, text search, recursive queries and SQL aggregation
provide a broad basis for editing, filtering and searching knowledge within one
store. Facet, annotation, View and meta-knowledge operations motivate that
breadth. Extension-based pgvector and combinations of relational predicates,
structured attributes, text and vector similarity motivate later embedding and
hybrid search work. These capabilities justify the selection; their presence
in PostgreSQL does not establish a CNCF API or a currently implemented product.

Japanese text search requires separate evaluation of tokenization or approaches
such as pg_bigm. Neither default full-text search nor this decision settles a
Japanese search configuration or deployment.

## Responsibilities

| Owner | Responsibility |
| --- | --- |
| PostgreSQL persistence provider | Native storage, transactions and authoritative readback under CNCF contracts. |
| CNCF Entity/Aggregate, UnitOfWork and Display boundaries | Identity, authorization, revision, domain/lifecycle admission, committed outcomes and selected reload. |
| KnowledgeHub domain | Material -> Information Candidate -> Information -> Knowledge semantics and admission. These stages are not database schemas. |
| RDF integration | Semantic representation, Knowledge Graph and external knowledge connections. |
| Later specialized-store decision | Admit dedicated vector/graph storage only when demonstrated requirements exceed the selected solution. |

Selecting a storage engine does not replace any domain or framework owner.
Generic Record and ContentBody carrier encoding remains distinct from a future
native JSONB query or storage contract.

## Phase 96 minimum and future capabilities

The minimum is [PG1–PG12](../spec/postgresql-phase-96-persistence.md#required-behavior):
native production JDBC/dialect routing through normal ComponentDataStore and
SystemNode-managed selection; canonical create/update/delete, revisions and
conditional mutation; root/body atomicity, authoritative readback, rollback and
acknowledgment safety; and registered Entity AND Aggregate mutation with actual
contextual committed reload. Positive task-owned PostgreSQL execution and
retained memory/SQLite/MySQL regressions are mandatory.

JSONB product operations, full-text search, recursive-query products, pgvector,
hybrid search and RDF products require their own later contracts and acceptance.
They are selection rationale, not additional Phase 96 features. No PostgreSQL,
JDBC, dependency or container version is selected here; the next native
implementation plan owns those exact choices.

## Consequences

The existing 17-path atomic-create implementation draft remains preserved and
unvalidated. It does not supply PostgreSQL acceptance. All four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN, with P96-08 through P96-11 and the original validation, review,
release and aggregate handoff gates retained. Documentary conformance does not
advance those gates.

MySQL schema preparation retains its existing nontransactional DDL boundary.
The [strict-create design](entity-create-atomic-provider.md#postgresql-native-create-decision)
records the separate PostgreSQL transaction/address decision. No production
data migration, RDF migration or dedicated database rollout follows from this
document.

## References

- [Phase 96](../phase/phase-96.md#postgresql-required-persistence-handoff)
- [Adoption history and provenance limits](../journal/2026/10/2026-10-14-postgresql-primary-knowledge-store.md)
- [PostgreSQL JSON types](https://www.postgresql.org/docs/18/datatype-json.html)
- [PostgreSQL full-text search](https://www.postgresql.org/docs/18/textsearch.html)
- [PostgreSQL recursive queries](https://www.postgresql.org/docs/18/queries-with.html)
- [pgvector extension](https://github.com/pgvector/pgvector)

The technical references describe capabilities, not a selected Phase 96 runtime
baseline or deployment.
