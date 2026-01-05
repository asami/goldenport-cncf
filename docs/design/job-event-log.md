# JobEventLog Persistence Model (Provisional Core Contract)

This document is a supporting reference for "Event-Driven Job Management (Phase 1–2)".
Canonical entry point: event-driven-job-management.md
Scope and invariants are defined there; this document specifies details for this topic.

----------------------------------------------------------------------
Objective
----------------------------------------------------------------------
This document defines a minimal persistence model for JobEventLog in
event-driven job management.

Goals:
- derive JobState from events
- tolerate idempotency, re-delivery, and out-of-order arrival
- retain observability identifiers without using them for control

----------------------------------------------------------------------
Design Principles
----------------------------------------------------------------------
- JobEventLog is append-only.
- JobEventLog records facts, not state.
- JobState is re-derivable from JobEventLog.
- Observability identifiers are stored but not used for JobState decisions.
- JobEventLog may record non-job events.

----------------------------------------------------------------------
Logical Model (Canonical)
----------------------------------------------------------------------

JobEventLogEntry (conceptual):
  // --- identity ---
  logId: JobEventLogId

  // --- event identity ---
  eventId: EventId
  eventType: EventType
  occurredAt: Instant

  // --- job / task correlation ---
  jobId: Option[JobId]
  taskId: Option[TaskId]

  // --- observability ---
  executionId: Option[ExecutionContextId]
  traceId: Option[TraceId]
  spanId: Option[SpanId]

  // --- payload ---
  attributes: Map[String, Any]

  // --- metadata ---
  receivedAt: Instant

Notes:
- eventId is the idempotency key.
- receivedAt is used for lag and receipt analysis.
- Entries with jobId/taskId == None are still stored.

----------------------------------------------------------------------
Primary Constraints
----------------------------------------------------------------------

Uniqueness
----------
- eventId is unique.
- Re-insert of the same eventId must be ignored or treated as an idempotent upsert.

Ordering
--------
- JobEventLog does not guarantee ordering.
- occurredAt/receivedAt are reference timestamps only.

----------------------------------------------------------------------
Usage Semantics
----------------------------------------------------------------------

JobState Derivation
-------------------
- JobState is derived from JobEventLogEntry sets.
- Done/Failed conditions follow the JobState specification.
- JobEventLog itself does not hold state.

Handling Non-Job Events
-----------------------
- Events without jobId may be stored.
- Non-job events are ignored for JobState, but available for audit/analysis.

----------------------------------------------------------------------
Idempotency Rules
----------------------------------------------------------------------
- eventId is used to detect duplicates.
- Duplicate eventId must not change JobState.
- Log must not contain multiple entries with the same eventId.

----------------------------------------------------------------------
Observability Rules
----------------------------------------------------------------------
- executionId / traceId / spanId are stored for diagnostics.
- JobState must not reference observability identifiers.
- Missing observability identifiers are not errors.

----------------------------------------------------------------------
Storage Mapping (Example)
----------------------------------------------------------------------

Relational example:

job_event_log
-------------
log_id          PK
event_id        UNIQUE
event_type
occurred_at
job_id          NULLABLE
task_id         NULLABLE
execution_id    NULLABLE
trace_id        NULLABLE
span_id         NULLABLE
attributes      JSON / TEXT
received_at

Index suggestions:
- (job_id)
- (event_type)
- (occurred_at)

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------
- Strong transactional guarantees
- Event ordering enforcement
- Event replay optimization
- CQRS read model design

----------------------------------------------------------------------
Next Steps
----------------------------------------------------------------------
- Executable specs:
  - duplicate EventId does not change JobState
  - late Event after Done is recorded only
- JobState projector
- Retention and archival policy

----------------------------------------------------------------------
Document Status
----------------------------------------------------------------------
Status: Provisional
Stability: High (core persistence contract)
Audience: CNCF developers and architects
