CML Operation Argument Model (Handoff)
=====================================

status=proposed
phase=7
date=2026-03-21

---

# 1. Overview

This document defines the argument model for CML Operation.

Two forms are supported:

(a) canonical form (single input object)
(b) convenience form (multiple parameters)

The system normalizes both into a single execution model.

---

# 2. Core Concept

```
(a) = canonical operation form
(b) = convenience (syntactic sugar)
```

Execution is always based on (a).

---

# 3. Canonical Form (a)

## Definition

```
Operation takes exactly one argument
→ a structured input object
```

---

## DSL

# Operation

## createOrder

### Type

Command

### Input

CreateOrderInput

---

# Entity

## CreateOrderInput

### Attribute

orderId :: OrderId
amount  :: Money

---

## Meaning

```
createOrder(input: CreateOrderInput)
```

---

# 4. Convenience Form (b)

## Definition

```
Operation defines multiple parameters
→ internally converted to (a)
```

---

## DSL

# Operation

## createOrder

### Type

Command

### Parameter

orderId :: OrderId
amount  :: Money

---

## Meaning

```
createOrder(orderId: OrderId, amount: Money)
```

Internally:

```
CreateOrderInput(orderId, amount)
→ canonical operation
```

---

# 5. Dual Definition (a + b)

Both forms can coexist.

## DSL

# Operation

## createOrder

### Type

Command

### Input

CreateOrderInput

### Parameter

orderId :: OrderId
amount  :: Money

---

## Semantics

```
(b) → converted to (a)
(a) → actual execution path
```

---

# 6. Simplified Mode (b only)

If only (b) is defined:

```
Parameter only
→ Input is auto-generated
```

---

## Example

# Operation

## createOrder

### Type

Command

### Parameter

orderId :: OrderId
amount  :: Money

---

## Generated

```
CreateOrderInput(
  orderId :: OrderId
  amount  :: Money
)
```

---

# 7. Normalization Rule

All operations are normalized into canonical form.

```
Operation
  ↓ normalize
Operation(input: InputType)
```

---

# 8. AST Model

```scala
case class OperationDef(
  name: String,
  opType: OperationType,
  input: Option[TypeRef],
  parameters: Vector[AttributeDef]
)
```

---

## Normalized AST

```scala
case class NormalizedOperation(
  name: String,
  opType: OperationType,
  inputType: TypeRef
)
```

---

# 9. Generator Rules

## Input generation

```
if Input defined:
  use Input

if Parameter defined only:
  generate Input class

if both defined:
  Parameter must match Input fields
```

---

## Output

```
operation/
  CreateOrder.scala
  CreateOrderInput.scala
```

---

# 10. Runtime Mapping

```
(b) call
  ↓
construct Input
  ↓
(a) execution
  ↓
ActionCall
  ↓
Task
  ↓
Job
```

---

# 11. Validation Rules

- Input and Parameter must be consistent if both defined
- Parameter names must map to Input fields
- canonical form must always exist after normalization

---

# 12. Design Rationale

## Why (a) is primary

- aligns with functional programming
- simplifies serialization
- consistent with ActionCall model
- easier versioning

---

## Why (b) is supported

- developer ergonomics
- simple DSL usage
- API friendliness

---

# 13. Key Definition

```
Execution always uses canonical form (a)
Convenience form (b) is syntactic sugar only
```

---

End.
