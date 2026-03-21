CNCF Aggregate / View Design and Generation (Handoff)
====================================================

status=av-01-frozen
phase=7
date=2026-03-21

---

# 1. Overview

This document defines the design and generation model for:

- Aggregate (command-side model)
- View (query-side model)

The goal is to:

- establish clear CQRS separation
- generate Aggregate and View from a single CML Entity definition
- align runtime execution with Job/Event architecture

AV-01 freeze note:
- Semantic boundary and CML/AST contract are frozen in CNCF documentation.
- Parser/modeler source-of-truth changes are escalated to Cozy.

---

# 2. Core Concept

```
Entity (CML)
  ↓
Aggregate (write model)
View (read model)
```

Key principle:

```
Single source of truth (Entity)
→ multiple runtime projections (Aggregate / View)
```

---

# 3. Design Principles

1. Aggregate and View are separate models
2. Both are derived from a single Entity definition
3. Aggregate handles commands and invariants
4. View handles queries and projections
5. Event is the bridge between Aggregate and View

---

# 4. CML DSL Design

Aggregate and View are defined inside Entity.

## Example

# Entity

## Order

### Attribute

id     :: OrderId
amount :: Money
status :: OrderStatus

---

### Aggregate

#### Command

createOrder
payOrder
cancelOrder

#### State

status

#### Invariant

amount > 0

---

### View

#### Attribute

id     :: OrderId
amount :: Money
status :: OrderStatus

#### Query

getOrder
listOrders

---

# 5. Semantic Model

## 5.1 Entity

```
Domain concept definition
```

---

## 5.2 Aggregate

```
Command-side model
- state transition
- invariant enforcement
- event emission
```

---

## 5.3 View

```
Query-side model
- projection result
- read-optimized structure
```

---

# 6. Code Generation Model

## 6.1 Output Structure

```
entity/
  Order.scala

aggregate/
  OrderAggregate.scala

view/
  OrderView.scala
```

---

## 6.2 Aggregate (generated)

```scala
case class OrderAggregate(
  id: OrderId,
  amount: Money,
  status: OrderStatus
) {
  def handle(cmd: Command): Consequence[(OrderAggregate, Vector[Event])]
}
```

---

## 6.3 View (generated)

```scala
case class OrderView(
  id: OrderId,
  amount: Money,
  status: OrderStatus
)
```

---

# 7. Runtime Model

## 7.1 Command Flow

```
Command
  → Aggregate
    → new state
    → Event(s)
```

---

## 7.2 Projection Flow

```
Event
  → Projection
    → View update
```

---

## 7.3 Execution Context

All execution runs under Job/Task model:

```
ActionCall
  → Task
    → Job
```

Aggregate execution and projection must follow this rule.

---

# 8. Aggregate Contract

Aggregate MUST:

- accept Command
- return new state
- emit Event(s)
- enforce invariants

## Interface

```scala
trait Aggregate[A] {
  def handle(cmd: Command): Consequence[(A, Vector[Event])]
}
```

---

# 9. View Contract

View MUST:

- be derived from Event
- be rebuildable
- support query operations

## Interface

```scala
trait View[V] {
  def apply(event: Event): V
}
```

---

# 10. Synchronization Model

```
Aggregate
  → Event
    → Projection
      → View
```

Rules:

- eventual consistency
- deterministic projection
- idempotent application

---

# 11. AST Extension

## EntityDef

```scala
case class EntityDef(
  name: String,
  attributes: Vector[AttributeDef],
  aggregate: Option[AggregateDef],
  view: Option[ViewDef]
)
```

---

## AggregateDef

```scala
case class AggregateDef(
  commands: Vector[String],
  state: Vector[String],
  invariants: Vector[String]
)
```

---

## ViewDef

```scala
case class ViewDef(
  attributes: Vector[AttributeDef],
  queries: Vector[String]
)
```

---

# 12. Generator Rules

```
if Aggregate defined → generate aggregate/*.scala
if View defined      → generate view/*.scala
```

Naming:

```
Order → OrderAggregate / OrderView
```

---

# 13. Integration with Existing Code

Current state:

- aggregate/*.scala exists
- view/*.scala exists
- not yet generated from CML

Required change:

```
manual implementation → CML-based generation
```

---

# 14. Phase 7 Alignment

## AV-01

Aggregate boundary finalization  
→ AggregateDef

## AV-02

View contract finalization  
→ ViewDef

## AV-03

Synchronization rules  
→ Event + Projection

## AV-04

Meta alignment  
→ reflect Aggregate/View structure

## AV-05

Executable specifications  
→ deterministic CQRS behavior

---

# 15. Constraints

- Aggregate must remain pure and deterministic
- View must be reconstructable from events
- Event must not depend on View
- Projection must be idempotent

---

# 16. Future Extensions

- snapshot support for Aggregate
- projection parallelization
- distributed read model
- materialized view storage
- schema versioning

---

# 17. Key Definition

```
Aggregate = authoritative write model
View      = derived read model
Entity    = common definition source
```

---

End.
