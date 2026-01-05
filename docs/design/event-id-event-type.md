# EventId and EventType (Provisional Core Contract)

This document is a supporting reference for "Event-Driven Job Management (Phase 1–2)".
Canonical entry point: event-driven-job-management.md
Scope and invariants are defined there; this document specifies details for this topic.

----------------------------------------------------------------------
Objective
----------------------------------------------------------------------
This document defines dedicated types for EventId and EventType
based on the canonical event shape.

Goals:
- ensure event instance identity and idempotency via EventId
- stabilize semantic categories via EventType
- avoid exposing UniversalId as a public field
- align with Job, Task, and Observability design

----------------------------------------------------------------------
Design Principles
----------------------------------------------------------------------
- EventId and EventType have distinct responsibilities.
- EventId identifies a single event instance.
- EventType represents a semantic category.
- UniversalId must not be used directly in public fields.
- EventType must not be a raw String.

----------------------------------------------------------------------
EventId Design
----------------------------------------------------------------------

Semantics
---------
EventId:
- uniquely identifies an event instance
- is the basis for idempotency and deduplication
- is not a key for JobState transitions

Type Definition (Conceptual)
----------------------------
EventId:
- dedicated type (value class or final case class)
- internally backed by UniversalId

UniversalId Mapping
-------------------
EventId:
  major = <application or domain>   // e.g. "ec"
  minor = <subdomain or feature>    // e.g. "shipment"
  kind  = "event"

Notes:
- major/minor should follow JobId/TaskId domain context
- kind is fixed to "event"

Generation Rules
----------------
- generated when an event is created
- preserved across retries and re-delivery
- shared by ActionEvent, DomainEvent, and TaskCompletedEvent

----------------------------------------------------------------------
EventType Design
----------------------------------------------------------------------

Semantics
---------
EventType:
- semantic category for an event
- primary key for ExpectedEvent matching
- used in logging, audit, and analytics

Hard Rule
---------
- EventType is not an ID
- EventType is defined, not generated
- EventType is reused

Type Definition (Conceptual)
----------------------------
EventType:
- sealed trait EventType
- concrete objects or case objects

Naming Rules
------------
- business/fact-based names
- use past tense for facts
  - OrderCreated
  - ShipmentCompleted
  - AuthorizationFailed

Prohibited:
- technical names (ActionExecuted, HandlerInvoked)
- imperative names (CreateOrder)

Namespace Strategy
------------------
EventType can be organized by namespace.

Examples:
  ec.shipment.ShipmentRequested
  ec.shipment.ShipmentCompleted
  ec.order.OrderCancelled

Implementation may use:
- Scala packages
- object grouping

----------------------------------------------------------------------
Relationship Summary
----------------------------------------------------------------------

Event:
  eventId   : EventId      // instance identity
  eventType : EventType    // semantic category

- eventId != eventType
- many events may share the same eventType
- eventId is never regenerated
- eventType is a defined asset

----------------------------------------------------------------------
Hard Rules
----------------------------------------------------------------------
- JobState and ExpectedEvent use eventType for matching.
- eventId is used only for idempotency and audit.
- eventType must not be a UUID or UniversalId.
- eventType must not be a raw String.

----------------------------------------------------------------------
Backward Compatibility
----------------------------------------------------------------------
- if existing code uses String eventType, allow a transitional adapter
  and migrate to canonical EventType
- EventId is required (not optional)

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------
- schema registry
- dynamic event type generation
- versioned event types
- forced enum types beyond sealed trait

----------------------------------------------------------------------
Next Steps
----------------------------------------------------------------------
- reflect EventId/EventType in JobEventLog schema
- executable specs:
  - reprocessing the same EventId does not break JobState
  - mismatched eventType does not satisfy ExpectedEvent
- initial EventType catalog

----------------------------------------------------------------------
Document Status
----------------------------------------------------------------------
Status: Provisional
Stability: High (core identity contract)
Audience: CNCF developers and architects
