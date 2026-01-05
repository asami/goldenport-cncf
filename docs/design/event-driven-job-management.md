# Event-Driven Job Management (Phase 1–2)

----------------------------------------------------------------------
1. Purpose
----------------------------------------------------------------------
Provide an event-driven long-transaction / job management mechanism that is
usable in typical web applications without distributed complexity.

This phase emphasizes replayability, idempotency, and clear separation
between outcome facts and observability.

----------------------------------------------------------------------
2. DONE (Phase 1–2)
----------------------------------------------------------------------
- Event-driven job management framework
- JobPlan / ExpectedEvent
- JobEventJournal (in-memory)
- JobStateProjector (pure, replayable)
- EventId / EventTypeId
- Idempotent event handling
- Observability-independent job state derivation

----------------------------------------------------------------------
4. Non-Goals (Explicit)
----------------------------------------------------------------------
- Actor/cluster/sharding/high-availability runtime
- Distributed job ownership / consensus
- Service bus as the primary job progress synchronizer
- Event sourcing as the primary domain model
- Complex compensation orchestration beyond a placeholder policy hook

----------------------------------------------------------------------
5. Core Invariants (MUST NOT BREAK)
----------------------------------------------------------------------
- Job state is derived from the journal (replayable).
- Journal is append-only.
- Idempotency by EventId (duplicate EventId does not change derived state).
- Terminal states are record-only (events may be recorded but state unchanged).
- Observability identifiers do not affect JobState derivation.
- JobEngine is the semantic boundary: interprets events for job context.
- EventEngine is not job-aware; service bus concerns are out of scope here.

----------------------------------------------------------------------
6. Reference Architecture (Short)
----------------------------------------------------------------------
- Event ingestion lanes are separated conceptually:
  - internal execution events vs domain/coordinator events
- JobEngine interprets relevant events and appends JobEventLogEntry to the journal.
- Projector derives JobState.

----------------------------------------------------------------------
7. Links (Supporting Docs)
----------------------------------------------------------------------
- JobPlan / ExpectedEvent: job-plan-expected-event.md
- JobState transitions: job-state-transition.md
- JobEventLog persistence: job-event-log.md
- Canonical event shape: event-shape.md
- EventId / EventTypeId: event-id-event-type.md
- Execution / prepare semantics notes: execution-model.md

----------------------------------------------------------------------
8. FUTURE (Phase 3)
----------------------------------------------------------------------
- Actor-based JobEngine (HA / SPOF removal)
- Distributed job coordination
- Shared persistent job journal
- Promotion of coordination events onto service bus
