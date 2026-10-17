# DataStore and Aggregate Persistence Model (Draft)

## Purpose
This document defines the persistence model for aggregates inside a CNCF Component.
It clarifies the role of DataStore, the scope of its DSL, and the handling of
aggregate persistence, events, and transactions.

This document complements the Component Internal Execution Model and defines
persistence and aggregate design decisions derived from it.

## Scope / Non-Goals
- Scope:
  - Component-internal persistence
  - Aggregate storage strategy
  - DataStore abstraction and DSL usage
- Non-Goals:
  - Distributed transactions
  - Cross-component joins
  - Query optimization strategies for analytics
  - Event sourcing as a primary persistence model

## Core Principles

### Aggregate-Centric Persistence
- Compositional relationships (e.g., SalesOrder–SalesOrderItem) are modeled as aggregates.
- An aggregate is the unit of consistency and persistence.
- Aggregates are persisted as a single physical record by default.

Example:
- SalesOrder and its SalesOrderItems are stored together.
- Partial persistence of aggregate internals is not supported.

### UnitOfWork Alignment
- UnitOfWork commit boundaries align with aggregate commit boundaries.
- An aggregate is either fully committed or fully rolled back.
- Aggregate persistence participates in the UnitOfWork transaction.

## DataStore Abstraction

### Role of DataStore
- DataStore owns physical persistence concerns:
  - Tables
  - Schemas
  - SQL dialects
  - Outbox / CDC mechanisms
- UnitOfWork and EventEngine delegate persistence to DataStore.
- DataStore participates in an existing transaction via TransactionContext.

### Application Types and Physical Values

The application declares the logical type; the OR mapper owns the mapping
between that type and the physical DataStore representation. For a database
whose selected representation is text, DataStore writes and reads String.
It MUST NOT infer an application type from JSON-looking text.

The OR mapper performs these conversions:

| Application type | Write conversion before DataStore | Read conversion after DataStore |
| --- | --- | --- |
| String | Preserve String | Preserve String |
| Class X | X instance -> JSON value -> serialized JSON String | String -> JSON value -> X instance |
| Semi-structured data | Record or admitted JSON value -> serialized JSON String | String -> JSON value -> Record |
| JSON | JSON value -> serialized JSON String | String -> JSON value |
| Array | Declared array -> JSON array -> serialized JSON String | String -> JSON array -> declared array values |

A structured JSON value and serialized JSON text are different representations.
The declared application type determines where decoding stops. JSON fields
remain JSON; semi-structured fields become Record; X fields use a codec that
constructs and validates X. String fields preserve their original text even
when it is valid JSON. Accepted JSON shapes for Record and array element types
are part of the declared mapping, not inferred from content.

Mapping selection, JSON parsing/serialization, and application-value
construction belong to the OR-mapping boundary. The physical provider owns DB
access and physical type adaptation. A native JSON-capable backend may use a
different physical mapping without changing the declared application meaning.
Direct DataStore callers receive physical values and may explicitly perform
application-owned conversions, including `toRecord`/`fromRecord`. Callers
requiring automatic reconstruction use the admitted OR-mapping boundary.
Missing mappings and failures at
JSON parsing or application decoding MUST remain explicit structured outcomes.

The canonical datatype names are `XSemiStructuredData` and `XJson`.
`XSemiStructuredData` uses Record internally and supports JSON, YAML and HOCON
as external representations through explicitly selected format codecs.
`XJson` uses `io.circe.Json` internally and JSON as its external representation.
External interchange format and physical DB storage format are independent.
Normalization, accepted shapes and format-specific failures must be specified;
format guessing and silent loss of unsupported values are not permitted. Model
declarations and generated schema metadata MUST preserve those identities into
the existing EntityStore/EntityPersistent OR-mapping boundary.
`DataType.Named("record")` is a temporary workaround, not that canonical
contract; the final route MUST NOT depend on it, an equivalent `Named("json")`
substitute or ad hoc string-name dispatch. Phase 100 owns the transition of
producers, generated artifacts and consumers to the formal datatype route.
Supported behavior currently carried by `DataType.Named("record")` must be
absorbed into the `XSemiStructuredData` declaration, normalization, generation
and OR-mapping stages before the workaround is removed. Mere marker deletion
or renaming does not satisfy the transition contract. The accepted route must
have no active dependency on that legacy form, including compatibility readers.
This does not prohibit unrelated legitimate uses of `DataType.Named`.

Automatic encoding/decoding MUST select codecs using declared logical types
and their storage mappings: `XJson` restores JSON; `XSemiStructuredData`
restores Record. Runtime Record/Json values, `RecordPresentable`, or the
availability of `toRecord`/`fromRecord` MUST NOT substitute for that declaration.
An explicitly selected codec may use those methods internally. The outer
Record used as a DataStore row carrier does not imply a semi-structured type
declaration for any field.

### EntityStore OR-Mapping Route

The target automatic persistence route is EntityStore -> OR mapper ->
DataStore on write, and DataStore -> OR mapper -> EntityStore on read.
EntityStore supplies declarations and owns entity lifecycle, identity and
concurrency policy. The OR mapper consumes those declarations, selects storage
codecs and performs application/physical conversion. DataStore owns physical
access. This is a responsibility boundary built on EntityPersistent, not a
replacement ORM alongside it.

The contract applies to creation, saves, partial updates, searches/projections,
conditional/versioned mutations and authoritative mutation readback. Existing
store/admission hooks require an explicit place in the conversion stages to
avoid duplicate encoding/decoding. Integrating this route must preserve
transaction participation, provider identity, revision checks and atomic side
effects. Phase 100 is planned; these requirements do not assert implementation
completion.

### Transaction Participation
- UnitOfWork owns transaction boundaries (begin/commit/abort).
- DataStore participates in the UnitOfWork transaction.
- DataStore MUST NOT own or control transaction boundaries.

## DataStore DSL

### DSL Scope
The DataStore DSL provides typical access patterns:

- Single-table CRUD
- Primary key lookup
- Foreign key lookup
- Bulk fetch (IN queries)
- Simple filtering, ordering, and pagination

The DSL intentionally excludes:
- Inner joins
- Outer joins
- Subqueries
- Window functions

### Rationale
- Joins blur aggregate boundaries.
- Aggregate composition is handled at the domain/application layer.
- Multiple single-table queries under the same UnitOfWork are preferred over joins.

## Raw SQL Escape Hatch

- DataStore MAY provide a controlled raw SQL execution API.
- Raw SQL execution MUST:
  - Run under the provided TransactionContext
  - Be encapsulated inside DataStore implementations
- Raw SQL MUST NOT leak into:
  - Domain logic
  - UnitOfWork
  - EventEngine

Typical use cases:
- Performance tuning
- Database-specific optimizations
- Maintenance or migration tasks

## Event Persistence

- Events emitted during Action execution are collected as candidates by UnitOfWork.
- During UnitOfWork commit:
  - DataStore persists aggregate state.
  - DataStore persists events in the same transaction when using the same physical datastore.
- After transaction commit:
  - Persisted events are handed to EventEngine for publication.

Event persistence is not event sourcing.
Events represent facts derived from aggregate state changes.

## Observability
- Logs, traces, and metrics are diagnostic.
- Observability data MUST NOT be transactional.
- All observability outputs MUST be correlated to the UnitOfWork identifier.

## Design Implications
- Aggregate persistence does not require joins.
- Query complexity is controlled by DSL boundaries.
- Component-internal consistency is maximized.
- Cross-component integration relies on events and projections, not joins.

## References
- Component Internal Execution Model
- Execution Model
- Execution Context
- CanonicalId Policy
