# Canonical Event Shape (Provisional Contract)

This document is a supporting reference for "Event-Driven Job Management (Phase 1–2)".
Canonical entry point: event-driven-job-management.md
Scope and invariants are defined there; this document specifies details for this topic.

----------------------------------------------------------------------
Objective
----------------------------------------------------------------------
This document defines the canonical event shape (minimum common fields)
used across Job, Task, and Observability concerns.

It establishes a shared contract that:
- aligns JobState, JobPlan, and ExpectedEvent
- preserves observability identifiers
- provides a stable, extensible event surface

----------------------------------------------------------------------
Design Principles
----------------------------------------------------------------------
- Events represent facts.
- Events carry identifiers but do not interpret them.
- Job control and observability must not be conflated.
- The event shape must be extensible and backward compatible.

----------------------------------------------------------------------
Canonical Event Shape (Minimum)
----------------------------------------------------------------------

Event (canonical):
  eventId: EventId
  eventType: EventType
  occurredAt: Instant

  // --- Job / Task control ---
  jobId: Option[JobId]
  taskId: Option[TaskId]

  // --- Observability ---
  executionId: Option[ExecutionContextId]
  traceId: Option[TraceId]
  spanId: Option[SpanId]

  // --- Domain payload ---
  attributes: Map[String, Any]

Notes:
- Implementations may use a trait with default values.
- Option means "not related" and is not an error.

----------------------------------------------------------------------
Field Semantics
----------------------------------------------------------------------

eventId
-------
- Unique identifier for the event.
- Used for idempotency, deduplication, and audit.
- Not used directly for JobState transitions.

eventType
---------
- Semantic event category.
- Primary key for ExpectedEvent.matcher.

occurredAt
----------
- Time when the event occurred.
- Not used for ordering guarantees.

----------------------------------------------------------------------
Job and Task Fields
----------------------------------------------------------------------

jobId
-----
- Primary correlation key for Jobs.
- If present, it is the strongest matching hint.
- Used by JobState transitions.

taskId
------
- Logical task identifier within a Job.
- Used by TaskCompletedEvent and DomainEvent.
- Matched by JobPlan.expected.

----------------------------------------------------------------------
Observability Fields
----------------------------------------------------------------------

executionId
-----------
- Identifier for an execution attempt.
- Corresponds to observe_enter / observe_leave.
- Must not affect JobState or ExpectedEvent matching.

traceId / spanId
----------------
- Distributed tracing identifiers.
- Represent call relationships and spans.
- Ignored by Job logic.

----------------------------------------------------------------------
Hard Rules
----------------------------------------------------------------------
- JobState / JobPlan / ExpectedEvent must not reference
  executionId / traceId / spanId.
- Missing observability IDs are not errors.
- Any Event that matches the canonical shape may participate in Job logic.

----------------------------------------------------------------------
Event Subtypes (Examples)
----------------------------------------------------------------------

ActionEvent
-----------
- Fact derived from action outcome.
- result is recorded as an attribute.
- jobId / taskId may be present.

TaskCompletedEvent
------------------
- Explicit task completion.
- taskId is required.
- jobId is required in principle.

DomainEvent
-----------
- Business fact.
- jobId / taskId are optional.
- Meaning is derived from JobPlan.

----------------------------------------------------------------------
Backward Compatibility
----------------------------------------------------------------------
- Existing event implementations may default fields to None.
- Adding new fields must not be breaking.
- Job logic must tolerate missing optional fields.

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------
- Ordering guarantees
- Schema registry or serialization formats
- Strongly typed DomainEvent hierarchies
- Payload normalization

----------------------------------------------------------------------
Next Steps
----------------------------------------------------------------------
- Concrete EventId and EventType definitions
- JobEventLog persistence model
- Executable specs:
  - JobState ignores observability IDs
  - Events without jobId/taskId do not affect Jobs

----------------------------------------------------------------------
Document Status
----------------------------------------------------------------------
Status: Provisional
Stability: High (core contract)
Audience: CNCF developers and architects
