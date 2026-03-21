CML Operation Input as Command/Query Value (Handoff)
==================================================

status=proposed
phase=7
date=2026-03-21

---

# 1. Overview

This document defines the typing model for Operation input in CML.

Operation input is defined as:

```
Command Value
Query Value
```

These are specialized Value types used as canonical input for Operation.

---

# 2. Core Concept

```
Operation(input)
```

where:

```
input ∈ { Command, Query }
```

---

# 3. Value Classification

CML Value types are extended as:

```
Value
  ├─ Command
  └─ Query
```

---

# 4. DSL Design

## 4.1 Command Value

# Command

## CreateOrder

### Attribute

orderId :: OrderId
amount  :: Money

---

## 4.2 Query Value

# Query

## GetOrder

### Attribute

orderId :: OrderId

---

# 5. Operation Definition

## 5.1 Canonical Form

# Operation

## createOrder

### Type

Command

### Input

CreateOrder

---

# Operation

## getOrder

### Type

Query

### Input

GetOrder

---

# 6. Convenience Form (b)

## Parameter style

# Operation

## createOrder

### Type

Command

### Parameter

orderId :: OrderId
amount  :: Money

---

## Normalization

```
Parameter → Command Value生成
→ canonical form に変換
```

---

# 7. AST Model

```scala
case class OperationDef(
  name: String,
  opType: OperationType,
  input: TypeRef
)

enum OperationType {
  case Command
  case Query
  case Event
  case System
}
```

---

## Value Types

```scala
sealed trait ValueDef

case class CommandDef(
  name: String,
  attributes: Vector[AttributeDef]
) extends ValueDef

case class QueryDef(
  name: String,
  attributes: Vector[AttributeDef]
) extends ValueDef
```

---

# 8. Generator Model

## Output structure

```
command/
  CreateOrder.scala

query/
  GetOrder.scala

operation/
  CreateOrderOp.scala
  GetOrderOp.scala
```

---

## Generated Command

```scala
case class CreateOrder(
  orderId: OrderId,
  amount: Money
)
```

---

## Generated Operation

```scala
def createOrder(cmd: CreateOrder): Consequence[Result]
```

---

# 9. Runtime Mapping

```
Operation
  ↓
Command / Query Value
  ↓
ActionCall
  ↓
Task
  ↓
Job
```

---

# 10. Semantics

## Command

- executed via Job (async default)
- modifies Aggregate
- emits Event

---

## Query

- synchronous
- no persistence (Ephemeral Job)
- reads View

---

# 11. Validation Rules

- Operation.Type must match Value type
  - Command → Command Value
  - Query   → Query Value

- Parameter form must normalize into Value

---

# 12. Design Rationale

## Why Command/Query as Value

```
- aligns with CQRS
- makes input explicit and typed
- simplifies generator
- avoids ambiguity of generic Value
```

---

## Why not generic Value

```
generic Value → meaning ambiguity
Command/Query → explicit semantics
```

---

# 13. Key Definition

```
Operation input is always a Command or Query Value
```

---

End.
