# Datastore Boundary and Shared Database Decision

date=2026-07-23
status=active-design-direction
scope=CNCF component persistence and launcher/runtime integration

## Decision

CNCF component behavior uses the datastore abstraction as its only durable
storage boundary. A component's domain and application logic reaches durable
state through a purpose-specific internal DSL or component-owned persistence
port. Framework infrastructure implements that port with `DataStoreSpace` and
the admitted component datastore.

SQLite, JDBC, SQL dialects, database connections, paths, and credentials are
backend-provisioning concerns. They are selected through launcher/runtime
configuration and must not appear in component domain/application behavior.

## Shared Database Rule

A `DataStoreSpace` may be backed by one common physical database. That does
not make the database a shared application namespace: each component receives
only its component datastore and owns only its named collections and versioned
record model. Components must not read, enumerate, write, delete, or migrate
another component's records because the backend is shared.

Collection identity and record-model evolution are component-owned. Backend
schema/driver lifecycle remains CNCF infrastructure responsibility. A
component's logical migration must use supported datastore operations and must
not issue backend DDL or raw SQL.

## Decision Criteria

Choose the datastore/internal-DSL route when any of these are true:

- persistence must survive a restart or be shared by multiple execution
  surfaces;
- a development SQLite profile must later move to a common or external
  datastore without changing component semantics;
- authorization, tenant scope, observability, deterministic fixtures, or
  lifecycle control must remain at a CNCF chokepoint;
- the component needs a stable record identity, retention history, or
  concurrent admission rule.

Use an in-memory component-local structure only for explicitly transient,
rebuildable state. Do not introduce a component-specific direct database
adapter as a shortcut around the datastore boundary.

## Consequences

- Launcher profiles can provide lightweight local SQLite without making SQLite
  a component dependency.
- A common production database can host multiple components while retaining
  ownership and isolation boundaries.
- Direct backend access is strongly discouraged because it destroys the
  practical benefit of component technology: backend substitution, independent
  assembly/deployment, isolation in common infrastructure, and one testable
  runtime boundary all become component-specific obligations.
- Direct backend access is also a material security risk: it can bypass
  framework-owned credential handling, component/tenant scope, authorization,
  collection admission, redaction, and audit policy.
- Direct backend access is an observability loss: it bypasses the datastore
  CallTree/metrics/failure chokepoint and weakens attribution from an operation
  to its storage effect, latency, retry, and failure.
- Provider-specific behavior is tested in CNCF/launcher integration; component
  tests prove semantic behavior through configured datastore fixtures.
- A missing datastore capability is a CNCF enhancement candidate, not a reason
  for a component to bypass the abstraction.

## Related Records

- `docs/notes/internal-dsl-guideline.md`
- `docs/notes/datastore-query-translation-note.md`
- `docs/phase/phase-37.md`
- CBD Support P8-42 persistence decision, maintained in the CBD Support
  repository
