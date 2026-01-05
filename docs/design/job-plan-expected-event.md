# JobPlan and ExpectedEvent (Provisional Type Spec)

This document is a supporting reference for "Event-Driven Job Management (Phase 1–2)".
Canonical entry point: event-driven-job-management.md
Scope and invariants are defined there; this document specifies details for this topic.

----------------------------------------------------------------------
Objective
----------------------------------------------------------------------
Event-driven job management requires a type-safe way to express
what a Job is waiting for. This document defines the provisional
type design for JobPlan and ExpectedEvent.

This design:
- is implementation-agnostic
- treats DomainEvent and TaskCompletedEvent uniformly
- allows future extensions without breaking the core model

----------------------------------------------------------------------
Design Principles
----------------------------------------------------------------------
- Job does not interpret events; it only matches expectations.
- JobPlan declares an expected set, not an order.
- ExpectedEvent is a predicate over events, not an event instance.
- JobPlan is immutable.
- Event arrival order is not assumed.
- JobId and TaskId are semantic control identifiers and must be typed.

----------------------------------------------------------------------
Core Types (Conceptual)
----------------------------------------------------------------------

JobPlan
-------
A JobPlan defines the set of events whose arrival determines
the completion of a Job.

Conceptual fields:

JobPlan:
  jobId: JobId
  expected: Set[ExpectedEvent]
  policy: JobPolicy (optional)

Notes:
- expected is a set (no ordering).
- If expected is empty, the job may be Done immediately (policy-dependent).

ExpectedEvent
-------------
An ExpectedEvent describes what kind of event the Job is waiting for.

It is a predicate over events, not an event instance.

Conceptual fields:

ExpectedEvent:
  kind: ExpectedEventKind
  matcher: EventMatcher
  multiplicity: Multiplicity

----------------------------------------------------------------------
ExpectedEventKind
----------------------------------------------------------------------
Represents a semantic category for readability and logging.

Examples:
- TaskCompletion
- DomainFact
- ExternalSignal

This field must not affect matching logic.

----------------------------------------------------------------------
EventMatcher
----------------------------------------------------------------------
Defines how an incoming event matches this expectation.

Conceptual structure:

EventMatcher:
  eventType: EventTypeId
  correlation: CorrelationPredicate

Examples:
- eventType = OrderShipped
- correlation = orderId == X

Matching rule:
- Incoming event matches ExpectedEvent when:
  - eventType matches
  - correlation predicate evaluates to true

----------------------------------------------------------------------
CorrelationPredicate
----------------------------------------------------------------------
A declarative condition over event attributes.

Conceptual examples:
- entityId == JobContext.entityId
- businessKey in JobContext.keys
- (entityId == X) AND (region == "JP")

Implementation note:
- Predicate is evaluated, not executed as code.
- A DSL or data structure is preferred over lambdas.

----------------------------------------------------------------------
Multiplicity
----------------------------------------------------------------------
Defines how many matching events are required.

Provisional options:

Multiplicity:
  - ExactlyOnce
  - AtLeastOnce
  - Times(n)

Default:
- ExactlyOnce

----------------------------------------------------------------------
Satisfaction Semantics
----------------------------------------------------------------------

For each ExpectedEvent:
- Maintain a satisfaction state:
  - Pending
  - Satisfied(count)

Rules:
- An ExpectedEvent is satisfied when:
  - count meets multiplicity requirement
- Once satisfied, further matching events are recorded
  but do not affect completion.

Job completion condition:
All ExpectedEvent in JobPlan.expected are satisfied.

----------------------------------------------------------------------
Error Events
----------------------------------------------------------------------
Error or failure events are not ExpectedEvent by default.

Handling:
- Error events may trigger compensation or mark the Job as Failed.
- Error events do not satisfy ExpectedEvent unless explicitly modeled.

This avoids accidental completion on failure.

----------------------------------------------------------------------
Extensibility Hooks (Future)
----------------------------------------------------------------------
The following are intentionally excluded from the initial design:

- Alternative expectations (A OR B)
- Conditional expectations (if X then wait for Y)
- Timeouts or deadlines
- Versioned JobPlan

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------
- Encoding execution order
- Embedding domain logic
- Referencing ActionCall or UnitOfWork
- Strong typing per DomainEvent subtype

----------------------------------------------------------------------
Definition of Done
----------------------------------------------------------------------
- JobPlan clearly states which events are awaited.
- ExpectedEvent matching is declarative and side-effect free.
- DomainEvent and TaskCompletedEvent are treated uniformly.
- The design supports idempotency and re-delivery.
- Observability IDs may accompany events but must not affect Job behavior.

----------------------------------------------------------------------
Next Steps
----------------------------------------------------------------------
- JobState transition table
- Event-to-Job correlation strategy
- Compensation policy model
