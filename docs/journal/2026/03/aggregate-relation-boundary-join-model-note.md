# Aggregate Relation / Boundary / Join Model Note

status=draft  
created_at=2026-03-31  
tag=cncf, aggregate, relation, boundary, join  

---

# Purpose

This note records how the current aggregate extension discussion evolved.

The immediate trigger was a need to model an assembled aggregate such as:

- `Order`
- embedded `OrderLine`
- external `ShipmentOrder`
- external `User`

The original `AggregateMemberDefinition` shape was not enough to explain the difference between:

- aggregate-internal composition
- external related structure
- plain association reference

---

# What Became Clear

The existing single-axis interpretation was too weak.

The following concerns were mixed together:

- structural relation
- transaction / invariant boundary
- join direction

That produced ambiguity, especially for `ShipmentOrder`.

`ShipmentOrder` is:

- stronger than a plain `User` association
- not part of the same DDD aggregate transaction boundary as `Order`

This meant that a single category such as `association` or `member` was insufficient.

---

# Current Working Model

The current discussion converged on three axes.

## Relation

- `composition`
- `aggregation`
- `association`

## Boundary

- `internal`
- `external`

## Join Strategy

- `direct`
- `reverse`
- `through`

This allows examples such as:

- `OrderLine`
  - `composition + internal`
- `ShipmentOrder`
  - `aggregation + external`
- `User`
  - `association + external`

Join direction is treated separately:

- `User`
  - typically `direct`
- `ShipmentOrder`
  - typically `reverse`

---

# Runtime Meaning Agreed In Discussion

The strongest new agreement concerns `aggregation + external`.

It is not only a read-side join category.

It is an external related structure that matters for update semantics.

Agreed meaning:

- readable from behavior as a formal related structure
- usable from invariant / guard
- eligible for follow-up update
- eligible for event / compensation flow
- eligible for cascade delete policy

Transaction interpretation:

- same `Subsystem` / `Component`
  - same transaction is allowed
- across subsystem boundary
  - saga-like coordination is required

This makes it clearly different from `association + external`,
which remains primarily a reference context.

---

# Why This Split Was Chosen

The main reason is responsibility separation.

- `relation`
  - structural meaning
- `boundary`
  - execution meaning
- `join`
  - assembly method

This keeps the model extensible.

For example, `aggregation + external` may later receive its own runtime meaning beyond simple attach behavior.

---

# Current Implementation Step

The current codebase already carries part of this model.

- `kind`
  - `composition | aggregation | association`
- `boundary`
  - `internal | external`

The current runtime also distinguishes at least:

- `association + external`
  - root-side id lookup
- other external members
  - reverse lookup via target-side join field

This is enough for the current `06.b` sample first line.

---

# Remaining Work

The following is still open:

- define runtime semantics for `aggregation + external`
- define a first-class `join strategy` contract
- decide how `through` should be represented in metadata
- decide how visibility and behavior DSL should treat each combination

The formal specification belongs in `docs/notes`.
