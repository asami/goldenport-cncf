Aggregate Relation / Boundary / Join Model
==========================================

# Purpose

This document defines the working specification for aggregate assembly
using three independent axes:

- relation
- boundary
- join strategy

The purpose is to avoid overloading a single concept such as
`member` or `association`.

---

# Axes

## Relation

Relation expresses structural meaning.

- `composition`
- `aggregation`
- `association`

### composition

- strongest whole-part relationship
- lifecycle usually follows the root
- often embedded or tightly owned

### aggregation

- whole-part relationship weaker than composition
- structurally relevant to assembled aggregate state
- may live outside the root transaction boundary

### association

- plain reference relationship
- not a whole-part relationship

## Boundary

Boundary expresses execution meaning.

- `internal`
- `external`

### internal

- belongs to aggregate transaction / invariant boundary
- may participate in aggregate-internal visibility rules

### external

- outside aggregate transaction / invariant boundary
- may still be assembled into aggregate output

## Join Strategy

Join strategy expresses how related data is found.

- `direct`
- `reverse`
- `through`

### direct

The base object holds the target identifier.

Example:
- `Order.userId -> User.id`

### reverse

The related entity holds the base identifier.

Example:
- `ShipmentOrder.orderId -> Order.id`

### through

A third entity or association table connects the two sides.

Example:
- `OrderTag(orderId, tagId)` between `Order` and `Tag`

---

# Example Mapping

The current reference example is:

- `Order`
- `OrderLine`
- `ShipmentOrder`
- `User`

Recommended interpretation:

- `OrderLine`
  - `composition + internal`
- `ShipmentOrder`
  - `aggregation + external + reverse`
- `User`
  - `association + external + direct`

This is why `ShipmentOrder` must not be treated as equivalent to `User`.

---

# Runtime Interpretation

Aggregate relation metadata describes how an aggregate is assembled and how its
members participate in the boundary. It does not define create or command
behavior.

Create and command behavior is defined separately:

- `AggregateCreateDefinition` describes Aggregate Root creation.
- `AggregateCommandDefinition` describes business operations on an existing
  Aggregate Root.

Application logic should use the Aggregate Root `create` function and domain
methods. Relation/boundary/join metadata remains the assembly contract used to
resolve members and read aggregate state.

## composition + internal

- aggregate-internal constituent
- part of aggregate state
- strongest candidate for embedded or owned data

## aggregation + external

- external related structure
- assembled into aggregate payload by default when declared
- stronger than plain association
- outside root transaction boundary

Update-side meaning:

- readable from behavior as a first-class related structure
- may be referenced by invariant and guard
- may be a target of follow-up update
- may be a target of event / compensation flow
- may participate in cascade delete policy

Transaction meaning:

- within the same `Subsystem` / `Component`
  - may participate in the same transaction
- across subsystem boundary
  - handled by saga-like coordination

## association + external

- external reference context
- loaded for reference or supplementary context
- weaker than aggregation

Update-side meaning is intentionally weaker than `aggregation + external`.

Typical use:

- reference lookup
- supplementary behavior context
- lightweight projection context

---

# Runtime Responsibilities

## relation

`relation` determines structural intent.

This affects:

- interpretation of assembled state
- future behavior semantics
- future projection semantics

## boundary

`boundary` determines execution responsibility.

This affects:

- transaction participation
- invariant participation
- visibility policy

## join strategy

`join strategy` determines lookup path.

This affects:

- how related ids are resolved
- whether lookup starts from root, target, or link entity

---

# Metadata Direction

The current metadata should move toward the following shape:

- `kind`
- `boundary`
- `join`
- `joinFieldName`
- optional target-side or through-side metadata

Minimum first-class `join` values:

- `direct`
- `reverse`
- `through`

---

# Current Codebase State

Current first-line implementation already supports:

- `kind`
- `boundary`
- direct-like root-side association lookup
- reverse-like target-side lookup for other external members

The `join strategy` axis is not yet explicit metadata.

---

# Open Issues

- formalize `join strategy` in metadata
- define `through` contract
- decide visibility policy differences between:
  - `aggregation + external`
  - `association + external`
- decide whether behavior DSL should distinguish:
  - external aggregation load
  - external association load

---

# Status

This note is a working specification for the next implementation step.

It is not yet a frozen contract.
