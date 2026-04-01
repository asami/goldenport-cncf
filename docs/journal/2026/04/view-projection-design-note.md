# CML View / Projection Model — Design Note

- date: 2026-04-01
- status: draft

---

## Purpose

This note defines the design of the view / projection model in CML.

The goal is to:

- provide a consistent projection mechanism for Entity / Value
- enable type-safe DTO generation
- eliminate RecordResponse-like untyped structures
- align with the context namespace model (view / query / update)

---

## Core Concept

A view is a projection context over types.

view.<projection>.<Type>

Examples:

view.summary.Item  
view.detail.Item  

---

## System-Defined Projections

The following projections are predefined:

- all (or whole)
- summary
- detail

---

## all (default projection)

all represents the full structure.

all is treated specially:

view.<Type>

Examples:

view.Item

---

### Important Rule

- view.all.<Type> is **not generated**
- view.<Type> is the canonical representation of all

---

## Projection Declaration

Projection is defined at the property level.

# ENTITY

## Item

### PROPERTIES

| name        | type     | projection        |
|-------------|----------|-------------------|
| id          | entityid | summary detail     |
| name        | name     | summary detail     |
| description | text     | detail             |
| price       | number   | summary detail     |

---

## Omission Rule (Important)

projection = all is implicit.

Therefore:

- specifying "all" is optional
- omission means "included in all"

---

## Default Projection Assignment

When projection is not specified, the following rules apply:

---

### Case 1: SimpleEntity / SimpleObject

For base types:

- predefined projections are applied

SimpleEntity / SimpleObject
  → all, summary, detail are predefined

Each property is assumed to belong to:

- all
- summary
- detail

(unless explicitly overridden)

---

### Case 2: Derived Entity / Value

For types extending SimpleEntity / SimpleObject:

- all properties automatically receive:

all, summary, detail

unless explicitly specified otherwise.

---

## Effective Rule

If projection is not specified:

projection = all summary detail

---

## Projection Interpretation

Example:

| name        | projection      |
|-------------|----------------|
| id          | summary detail |
| description | detail         |

Then:

summary:
- id

detail:
- id
- description

all:
- all properties

---

## Projection Context Model

Projection defines a context.

view.summary
  = summary context

Inside this context:

- all referenced types default to summary

---

## Default Propagation Rule

If no projection is specified on a reference:

inherit the current projection

Example:

Order.summary
  item: Item

↓

Order.summary
  item: Item.summary

---

## Explicit Projection Override

A property may override projection:

| name | type        |
|------|-------------|
| item | Item.detail |

---

## Resolution Rules

For each property:

1. if explicit projection is specified → use it
2. otherwise → inherit current projection

---

## Nested Projection

Projection propagates recursively:

Order.summary
  item → Item.summary
    category → Category.summary

---

## Scala Code Generation

### Namespace

view is the root namespace.

---

### Generated Structure

object view {

  // all (default)
  case class Item(
    id: EntityId,
    name: Name,
    description: String,
    price: BigDecimal
  )

  object summary {
    case class Item(...)
  }

  object detail {
    case class Item(...)
  }
}

---

### Generation Rules

#### all

view.<Type>

- generated as root-level case class
- corresponds to full structure
- replaces view.all.<Type>

---

#### other projections

view.<projection>.<Type>

Examples:

view.summary.Item  
view.detail.Item  

---

## Operation Integration

CML:

OUTPUT = Item.summary

Scala:

view.summary.Item

---

## Type Safety

Each projection produces a distinct type.

This ensures:

- compile-time safety
- explicit structure
- safe component wiring

---

## RecordResponse Elimination

Instead of:

RecordResponse(Map[String, Any])

Use:

view.Item  
view.summary.Item  
view.detail.Item  

---

## Design Rationale

### Why special handling for all?

- avoids redundant namespace (view.all)
- keeps usage concise
- aligns with default semantics

---

### Why context-based model?

- aligns with graph traversal
- simplifies nested projection
- matches mental model

---

### Why property-level projection?

- simple
- local
- declarative

---

## Constraints

- projection must exist
- invalid projection references are errors
- circular projection should be detected or avoided

---

## Future Work

- custom projections (compact, admin)
- projection inheritance
- dynamic projection (field selection)
- GraphQL-style selection

---

## Summary

- view is a projection context
- projection is defined at property level
- "all" is implicit and omitted
- default projection is all summary detail
- view.<Type> represents "all"
- view.<projection>.<Type> represents projected types
- projection propagates by default
- override is explicit
- RecordResponse is eliminated
