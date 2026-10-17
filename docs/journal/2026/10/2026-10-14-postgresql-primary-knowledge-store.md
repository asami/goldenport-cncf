# PostgreSQL primary Knowledge Store clarification

Document role: chronological clarification/adoption history. This journal
defines no behavior independently. The
[architecture decision](../../../design/postgresql-primary-knowledge-store.md)
owns stable selection and responsibilities; the
[Phase 96 persistence specification](../../../spec/postgresql-phase-96-persistence.md)
owns intended native behavior and future acceptance.

## Supplied background

The current human clarification places direct PostgreSQL adoption discussion
around September 22 and earlier embedding discussion around April. Those
approximate timings and rationale are supplied by the current message; older
conversations were not independently retrieved. They are not independently
verified historical decision dates.

The supplied history describes RDF/Fuseki, graph traversal/Neo4j and dedicated
vector storage such as Qdrant as considered alternatives, not deployed systems.
The earlier embedding direction considered PostgreSQL plus pgvector before a
dedicated vector database. The broader September rationale combined relational
conditions, JSONB, text search, recursive queries, SQL aggregation and later
vector/hybrid search in a practical common Knowledge Store. Japanese
tokenization or pg_bigm remains future evaluation; default full-text search
alone does not settle that requirement.

## Current clarification and documentary adoption

The current direction requires PostgreSQL as the primary practical store for
this CNCF / KnowledgeHub integration line. MySQL may coexist; SQLite and memory
work is retained. RDF retains semantics, Knowledge Graph and external-knowledge
connection roles. Dedicated vector/graph stores remain a later separate
decision after demonstrated insufficiency. Material -> Information Candidate
-> Information -> Knowledge retains semantic/admission ownership rather than
becoming a database-schema prescription.

The decision and PG1–PG12 contract are recorded in their design/spec layers;
the [Phase handoff](../../../phase/phase-96.md#postgresql-required-persistence-handoff)
and [P96-07 checklist](../../../phase/phase-96-checklist.md#required-native-postgresql-evidence-within-p96-07)
project the required native persistence evidence onto the existing work.
Broader knowledge capabilities explain the selection and are not automatic
Phase 96 feature additions, RDF migration or a production database rollout.

## Runtime gap and retained work

PostgreSQL JDBC identity grammar is present, but current dialect selection,
normal ComponentDataStore selection and native mutation acceptance do not yet
provide the required PostgreSQL backend. Identity support is not persistence
acceptance. The already-authored 17-path atomic-create product draft is
preserved and unvalidated; its earlier memory/SQLite/MySQL intent and historical
41-suite or proposed 56-suite subjects do not establish PostgreSQL evidence.

Native implementation requires the next parent plan to freeze exact source,
executable specifications, dependencies/baselines, private runtime scope and
positive native acceptance. This documentary authoring runs no new validation
and claims no passing runtime result, commit, Step acceptance or Phase closure.
All original 54 criteria and four P96-07 OPEN states remain unchanged;
P96-08 through P96-11, independent Step/full-Phase review, final validation and
release, and separate Cozy/server/app aggregate gates remain pending.
