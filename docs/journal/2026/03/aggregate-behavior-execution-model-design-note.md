# Aggregate / Behavior / Execution Model Design Note

status=draft  
created_at=2026-03-29  
tag=cncf, aggregate, behavior, execution-model  

---

# Overview

This design defines an execution model centered around Aggregates, clearly separating:

- Execution unit (ActionCall)
- Execution logic (Behavior)
- Execution context (ExecutionContext)
- Execution engine (Processor)

The goal is to construct an **Executable Domain Model**.

Key characteristics:

- Generate per-Aggregate internal DSL (protected API)
- Define Behavior as a shared abstraction
- Clearly separate Action / Call / Behavior responsibilities
- Preserve Aggregate boundaries strictly

---

# Core Concepts

## Aggregate

An Aggregate consists of:

- State (Entity / Value)
- Invariants
- Internal DSL (protected operations)

Aggregate
  = State + Invariants + Protected DSL

---

## Internal DSL

Internal DSL is defined as:

Internal DSL = protected methods of Aggregate

Example:

class SalesOrder {

  protected def findLine(lineId: LineId): SalesOrderLine

  protected def changeQuantity(line: SalesOrderLine, qty: Int): Unit

  protected def recalcTotal(): Unit

}

RULE:
All state changes must go through protected methods

---

## Behavior

Behavior
  = executable logic dependent on ExecutionContext

---

## AggregateBehavior

AggregateBehavior
  = state transition logic for an Aggregate

Characteristics:

- Uses Aggregate's protected DSL
- Executes within transaction boundary
- Subject to invariants

---

## ViewBehavior

ViewBehavior
  = read/query logic for View

Characteristics:

- Handles join / projection
- No state mutation

---

# Execution Model

## Layer Structure

Operation / Reception
        ↓
ActionCall (invocation)
        ↓
Processor (execution control)
        ↓
Behavior (logic)
        ↓
Aggregate (state)

---

## ActionCall

ActionCall
  = invocation

- Triggered from Operation / Reception
- Points to execution target (Behavior)

---

## Processor

Processor
  = orchestrator of ActionCall execution

Responsibilities:

- Load Aggregate
- Create ExecutionContext
- Execute Behavior
- Check invariants
- Commit
- Emit events

---

## ExecutionContext

ExecutionContext
  = runtime environment

Examples:

- user
- requestId
- timestamp
- transaction
- logging

---

## Behavior Execution

Behavior
  = f(Target, ExecutionContext)

Example:

trait AggregateBehavior[A] {
  def run(a: A, ctx: ExecutionContext): Result
}

---

# AggregateSpace

Aggregate execution environment is structured as:

AggregateSpace
  ├─ WorkingSet (resident cache)
  └─ WorkSpace (transactional working area)

---

## WorkingSet

- Long-lived
- Cache
- Stored per Aggregate

---

## WorkSpace

- Short-lived
- Per transaction
- UnitOfWork scope

---

# Loading Model

load(aggregateId)

→ load root + members  
→ place into AggregateSpace  

Example:

SalesOrder
+ SalesOrderLine*

---

# View Model

View
  = Aggregate + External Entities via application-level join

---

## Characteristics

- External entities are materialized
- Read-only
- Join performed in application layer

---

## Difference from Aggregate

Aggregate
  = reference external entities by ID only

View
  = join external entities as full objects

---

# Naming Strategy

## Separation Principles

Action        = general-purpose operation (existing core)  
ActionCall    = invocation  
Behavior      = execution logic  
Aggregate     = state boundary  

---

## Disallowed

AggregateAction   ❌ (conflicts with Action)  
AggregateCall     ❌ (conflicts with invocation semantics)  

---

## Adopted

AggregateBehavior  ⭕  
ViewBehavior       ⭕  

---

# Key Design Principles

## 1. Layer Separation

call ≠ behavior ≠ state

---

## 2. Aggregate Boundary Protection

- No direct external mutation  
- Only via protected DSL  

---

## 3. Execution Separation

Behavior = logic  
Processor = execution control  

---

## 4. DSL Realization

DSL = protected methods

---

## 5. Type Safety

Invalid operations are unrepresentable

---

# Role of Cozy

Cozy
  = DSL compiler that generates per-Aggregate internal DSL (protected API)

---

# Summary

Aggregate
  + protected DSL
  + Behavior
  + Processor

Behavior executes the DSL,  
ActionCall triggers the execution  

---

# Next Steps

- Define Behavior granularity  
- Invariant strategy (step vs commit)  
- Processor extensions (async / retry / job)  
- View update triggers (event-driven)  
- DSL generation granularity (high-level vs low-level)

---
