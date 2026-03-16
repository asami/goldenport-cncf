Repository / Browser design notes
================================

status=draft
date=2026-03-15


# Background

In the current CNCF design, domain persistence and read access need to be clearly separated.

The initial discussion started from the traditional DDD view of Repository:

    Repository = collection of aggregates

However, this model assumes an in-memory collection illusion and typically leads to a minimal interface such as:

    findById
    save

In practice, this approach is insufficient for the following reasons:

    - performance considerations
    - partial updates
    - concurrency control
    - explicit update intent
    - functional execution model

Therefore, the Repository concept needs to be reinterpreted in the context of CNCF.


# Repository: revised definition

Repository is defined as:

    an abstraction for persistence operations of domain models.

In this definition, Repository is responsible for expressing domain-oriented persistence operations while hiding the underlying storage implementation.

Typical operations include:

    findById
    query
    execute(updateProgram)

Repository does not expose storage details and does not necessarily provide a simple save(aggregate) API.


# Why save(aggregate) is insufficient

The traditional save pattern assumes state replacement:

    save(aggregate)

This approach has several problems:

    - update intent is lost
    - unnecessary full-record writes
    - inefficient persistence operations
    - increased optimistic locking conflicts

In the CNCF model, updates are treated as executable programs rather than state replacement.


# Functional update model

Updates are represented as functional programs executed within a UnitOfWork.

Execution flow:

    Operation
        ↓
    Functional Program
        ↓
    UnitOfWork
        ↓
    Repository.execute(program)
        ↓
    EntityStore

In this model:

    update = program execution

rather than

    update = state replacement


# Repository responsibilities

Repository therefore provides:

    find
    query
    execute(program)

and represents the persistence boundary for domain models.

Repository belongs to the domain/application layer.


# EntityStore responsibilities

EntityStore represents the infrastructure-level persistence mechanism.

Typical operations:

    load
    save
    update
    delete
    search

EntityStore may provide partial updates and storage optimizations.

Repository internally delegates to EntityStore.


# Browser: read-side abstraction

For read models (DTO / View objects), a separate abstraction is required.

The following constraints influenced the naming choice:

    - "Service" conflicts with Component Service
    - "Query" conflicts with Query parameter objects
    - "Projection / Projector" conflicts with projection concepts
    - "Repository" should remain domain persistence abstraction

Therefore the read abstraction is named:

    Browser

Browser executes read queries and returns View objects.


# Browser responsibilities

Browser is responsible for retrieving read models.

Typical operations:

    search(query)
    list(...)
    find(...)

Browser returns View objects (DTOs), not aggregates.


# View objects

View represents the result of read operations.

Example:

    UserView
    OrderSummaryView
    ProductListItem

Views are optimized for presentation or read access and do not represent domain aggregates.


# Resulting architecture

The resulting structure becomes:

    Repository
        → Aggregate

    Browser
        → View

Update path:

    Operation
        → Functional Program
        → UnitOfWork
        → Repository.execute

Read path:

    Operation
        → Browser
        → View


# Layer separation

Domain/Application layer:

    Repository
    Browser

Infrastructure layer:

    EntityStore

Presentation / read layer:

    View


# Summary

Repository:
    abstraction for persistence operations of domain models

Browser:
    abstraction for retrieving read models (View)

View:
    read-side DTO optimized for presentation or query results

EntityStore:
    infrastructure persistence implementation
