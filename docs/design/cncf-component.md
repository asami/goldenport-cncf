# CNCF Component Model & Behavior Model

## 1. Purpose and Positioning

This document defines the **Component Model** and **Behavior Model** of CNCF (Cloud-Native Component Framework).

Its purpose is to establish a **stable target specification** for implementation, code generation, and design decisions, so that development can proceed against a fixed conceptual baseline.

The model is informed by UML, DDD, CQRS, Event-driven architecture, and the Actor model, but is **reconstructed with implementation feasibility as the top priority**.

For the DomainComponent contract and Cozy integration boundary, see:
- `docs/design/domain-component.md`

---

## 2. CNCF Component Overview

### 2.1 Definition of a Component

A **Component** is an independent semantic and execution unit that  
interacts with the outside world through Messages and  
executes Actions according to its responsibility.

A Component consists of the following elements:

Component  
  ├─ Receptor  
  ├─ Service  
  ├─ ComponentActionEntry  
  └─ Engine (ActionExecutor)  
        └─ ActionLogic (abstract)

---

## 3. Component Model (Structure)

### 3.1 Receptor (Collection of Receptions)

**Responsibilities**

- Receive Messages  
- Translate Messages into Actions  
- Apply subscription rules, authorization, and pre-processing  

**Characteristics**

- Does not call Operations directly  
- Does not call the Engine directly  
- Invokes only the ComponentActionEntry  

Flow:

Message → Receptor → Action

The Receptor is an executable reinterpretation of UML *Reception*.

---

### 3.2 Service (Collection of Operations)

**Responsibilities**

- Provide internal operation entry points for the Component  
- Translate Operations into Actions  
- Expose use-case-level APIs internally  

**Characteristics**

- Not an external API  
- Contains no business logic  
- Responsible only for Action creation  

Flow:

Operation → Service → Action

The Service corresponds to the UML *Operation Compartment* elevated into a concrete abstraction.

---

### 3.3 ComponentActionEntry (Single Action Entry Point)

**Responsibilities**

- Act as the single entry point for Actions in the Component  
- Enforce Component-wide policies:  
  - Authorization  
  - Rate limiting  
  - Tracing and correlation  
- Delegate execution to the Engine  

**Design Rule**

Service and Receptor **MUST** submit Actions via ComponentActionEntry.

---

### 3.4 Engine (ActionExecutor)

**Responsibilities**

- Control Action execution  
- Manage Jobs  
- Enforce CQRS  
- Manage transactions  
- Publish Results and Events  

**Characteristics**

- Accepts only Actions  
- Knows nothing about Message, Receptor, or Service  
- Treats synchronous vs asynchronous execution as a strategy  

Flow:

Action → Engine → Result / Event

---

### 3.5 ActionLogic (Abstract Class)

**Responsibilities**

- Implement the actual business behavior for Actions  
- Enforce domain rules  
- Emit domain and system events  

**Key Design Principle**

ActionLogic is defined as an abstract class,  
and each Component must provide its concrete implementation.

Example:

abstract class ComponentActionLogic {  
  def issueSalesOrder(cmd: IssueSalesOrder): Result  
}

**What ActionLogic must NOT do**

- Manage Job lifecycle  
- Control asynchronous execution  
- Handle Messages or Receptors  

**Design Notes (Security)**

- Security decisions can occur before execution and during execution.
- SecurityEvent is used for security-relevant outcomes and is not mixed with ActionEvent / DomainEvent.
- Component owns security/observability concerns; ActionLogic remains domain-only.

---

## 4. Behavior Model

### 4.1 Basic Execution Flow

Message  
  ↓  
Receptor  
  ↓  
Action  
  ↓  
ComponentActionEntry  
  ↓  
Engine  
  ↓  
ActionLogic  
  ↓  
Result / Event

### 4.5 OperationCall-Centered Execution

- Actions are realized as OperationCalls at the execution boundary.
- OperationDefinition.createOperationRequest(Request) is the sole conversion
  point from Request to OperationRequest.
- ComponentActionEntry and Engine MUST invoke OperationDefinition before
  constructing an OperationCall.
- ExecutionContext and identifiers are passed through unchanged
  (CorrelationId via ObservabilityContext).

### Identifier Policy (UniversalId Baseline)
- CNCF relies on core UniversalId as the common identifier format across the execution and observability layers.
- Observability uses CorrelationId (which extends UniversalId) to correlate requests, operations, messages, events, and jobs across a single causal flow.
- CanonicalId is reserved for semantic/domain identity outside CNCF and MUST NOT be required by CNCF APIs.

---

### 4.2 Command, Query, and CQRS

**Command**

- An Action that changes state  
- Always executed as a Job  
- Synchronous or asynchronous execution is determined by Job policy  

**Query**

- An Action that reads state  
- Does not create a Job in principle  
- Returns an immediate Result  

CQRS is realized as an internal structure of the Engine.

---

### 4.3 Job Model

- A Job represents the execution instance of a Command Action  
- Jobs have multiple persistence levels:  
  - EphemeralJob (non-persistent)  
  - TransientJob (short-lived)  
  - PersistentJob (durable)  

**Important Principle**

All Commands are Job-based,  
but not all Jobs are persisted at the same level.

---

### 4.4 Event and EventMessage

- ActionLogic emits DomainEvents and SystemEvents  
- CNCF wraps Events into EventMessages for delivery  
- Components receive only subscribed EventMessages via Receptors  

Flow:

ActionLogic → Event → EventMessage → Receptor

---

## 5. Mapping to UML

UML Concept → CNCF Concept

- Component → Component  
- Operation → Service  
- Reception → Receptor  
- Behavior → Engine + ActionLogic  
- Operation Compartment → Service  
- Reception (feature) → Receptor  

CNCF elevates UML’s static model into an executable architecture.

---

## 6. Design Principles (Implementation Guidelines)

1. Messages are received only by Receptors  
2. Services and Receptors only create Actions  
3. Actions must pass through ComponentActionEntry  
4. The Engine is the sole executor of Actions  
5. Business logic resides exclusively in ActionLogic  
6. Commands are executed as Jobs  
7. Job persistence level is determined by policy  
8. CNCF execution MUST be OperationCall-centered; no parallel execution
   pipeline is permitted.

---

## 7. Status of This Document

- This document serves as the **target specification** for implementation  
- Implementations must conform to this model  
- Any exception requires updating this document first  

---

## Closing Note

By fixing this model as a reference point:

- Implementation proceeds without ambiguity  
- Architectural discussions remain focused  
- AI agents (ChatGPT / MCP) can reason about the system reliably  
- Future refactoring becomes tractable  

This document defines the **conceptual backbone of CNCF**.
