# JobState Transition Specification (Provisional)

This document is a supporting reference for "Event-Driven Job Management (Phase 1–2)".
Canonical entry point: event-driven-job-management.md
Scope and invariants are defined there; this document specifies details for this topic.

----------------------------------------------------------------------
Objective
----------------------------------------------------------------------
This document defines a provisional JobState transition model for
event-driven job management. It establishes shared expectations that
align with JobPlan and ExpectedEvent.

This specification:
- is a contract ahead of implementation
- defines transition conditions in event terms
- avoids blocking future refinements

----------------------------------------------------------------------
Design Principles
----------------------------------------------------------------------
- JobState transitions are driven by incoming Events only.
- ActionCall and observe_* do not affect JobState.
- JobState must be derivable from JobEventLog.
- Re-delivered events must not corrupt state (idempotent).
- Observability IDs may accompany events but do not influence transitions.

----------------------------------------------------------------------
Job States (Provisional)
----------------------------------------------------------------------

- Created
- Running
- Compensating
- Done
- Failed

----------------------------------------------------------------------
State Semantics
----------------------------------------------------------------------

Created
-------
- The JobPlan has been created.
- ExpectedEvent has not been evaluated.
- Event reception is not yet active (or record-only).

Running
-------
- The Job receives and evaluates events.
- ExpectedEvent satisfaction is monitored.
- Error events may move the job to Compensating.

Compensating
------------
- An error event was received and compensation is in progress.
- Compensation actions also emit events.
- Policy decides transition to Done or Failed.

Done
----
- All ExpectedEvent entries are satisfied.
- The job is complete.
- Further events are recorded only (no transitions).

Failed
------
- The job has failed conclusively.
- Compensation is not possible or policy ends the job.
- Further events are recorded only (no transitions).

----------------------------------------------------------------------
State Transition Table (Provisional)
----------------------------------------------------------------------

Current State | Incoming Event | Condition | Next State | Notes
----------------------------------------------------------------------
Created | JobKick | — | Running | Job starts
Created | any Event | — | Created | record only
----------------------------------------------------------------------
Running | ExpectedEvent matched | not all satisfied | Running | partial satisfaction
Running | ExpectedEvent matched | all satisfied | Done | completion
Running | ErrorEvent | compensationPolicy defined | Compensating | compensation starts
Running | ErrorEvent | no compensationPolicy | Failed | immediate failure
----------------------------------------------------------------------
Compensating | CompensationEvent | policy decides done | Done | compensation success
Compensating | CompensationEvent | policy decides fail | Failed | compensation failure
Compensating | any Event | — | Compensating | no transition
----------------------------------------------------------------------
Done | any Event | — | Done | record only
Failed | any Event | — | Failed | record only

----------------------------------------------------------------------
Notes on ErrorEvent
----------------------------------------------------------------------
- ErrorEvent is independent of ExpectedEvent.
- ErrorEvent does not satisfy ExpectedEvent by default.
- ErrorEvent interpretation is delegated to JobPolicy.

----------------------------------------------------------------------
Idempotency Rules
----------------------------------------------------------------------
- Re-delivery of the same Event must not repeat transitions.
- Done and Failed are terminal states.
- ExpectedEvent satisfaction is monotonic (once satisfied, stays satisfied).

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------
- Timeouts or deadlines
- Retry count management
- Partial completion states
- Human-in-the-loop transitions

----------------------------------------------------------------------
Next Refinement Candidates
----------------------------------------------------------------------
- JobPolicy type design (compensation and failure policy)
- Event-to-Job correlation rules
- JobEventLog persistence model
- Executable specs for JobState transitions

----------------------------------------------------------------------
Document Status
----------------------------------------------------------------------
Status: Provisional
Stability: Medium
Audience: CNCF developers and architects
