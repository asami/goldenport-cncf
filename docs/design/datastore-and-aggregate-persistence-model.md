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
