# Phase 6 — Job Management (CQRS)

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 6.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Introduce first-class Job management on top of Phase 5 event/runtime foundations.
- Execute both Command and Event flows under Job management.
- Define Job CQRS surfaces:
  - query (read model: status, trace, timeline)
  - query (result retrieval API: return payload in the same response shape as synchronous execution)
  - control (command model: cancel, retry, suspend, resume)
- Command execution policy:
  - default is asynchronous execution
  - return `JobId` as primary execution result
  - optional synchronous execution is available by explicit option
- Define Job lifecycle transition behavior and event correlation rules.
- Align Job operations with existing ActionCall/EventBus/EventStore boundaries.
- Add executable specifications for Job lifecycle and control behavior.

## 3. Non-Goals

- No distributed scheduler implementation in this phase.
- No external workflow engine migration.
- No redefinition of EventStore/EventBus core contracts completed in Phase 5.
- No security model redesign beyond existing policy/privilege boundaries.

## 4. Current Work Stack

- A (DONE): Define Job domain model and lifecycle transition contract.
- B (DONE): Implement Job query read model and projection surface.
- C (ACTIVE): Implement Job control commands (cancel/retry/suspend/resume).
- D (SUSPENDED): Integrate Job lifecycle with event correlation and replay boundary.
- E (SUSPENDED): Add executable specifications and regression coverage.

Resume hint:
- Fix lifecycle contract first; query/control behavior depends on stable lifecycle semantics.

## 5. Development Items

- [x] JM-01: Define canonical Job model (`JobId`, state, timestamps, correlation) and lifecycle transitions.
- [x] JM-02: Implement Job query read model (status/trace/timeline) and result retrieval API (sync-equivalent response shape).
- [ ] JM-03: Implement Job control command model (`cancel`, `retry`, `suspend`, `resume`) and command execution mode (`async default`, `sync option`) with policy checks.
- [ ] JM-04: Integrate Job/event correlation and replay interaction rules.
- [ ] JM-05: Add executable specifications for lifecycle transitions, query consistency, control semantics, async/sync command behavior, and failure paths.

## 6. Inputs from Previous Phases

- Phase 5 Event foundations (EV-01 to EV-06) are treated as prerequisites.
- Existing provisional Job design documents are treated as baseline references and must be reconciled with implementation.

## 7. References

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/strategy/cncf-development-strategy.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/phase/phase-5.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-management.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/event-driven-job-management.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-plan-expected-event.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-state-transition.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/job-event-log.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/job-task-execution-persistence-design.md`
