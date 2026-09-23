# Persistent Event as Temporal Decoupling Boundary

Date: 2026-09-24

Persistent Service Bus events are adopted as an explicit temporal decoupling mechanism. A journaled event is both a delivered message and an authoritative record of an operational fact. Consumers may be offline when it is produced and catch up later without turning the journal into an Event Sourcing store.

The first concrete scenario is sm-workflow on Codex/OpenClaw -> Service Bus -> Textus Control Center -> smartphone/watch. Development progress and approval requests are journaled; Control Center presents them remotely. Human approval returns through an authenticated sm-workflow operation/admission boundary, after which the outcome is published as another authoritative event.

This scenario also validates StateMachine event drive, correlation/causation, and future subsystem-to-subsystem SYSTEM transport while keeping Phase 93 initially subsystem-local/in-memory.

## Existing EventBus baseline

This capability extends the existing CNCF `EventBus` / `EventEngine` implementation. Existing `EventPublishOption(persistent)` and persist-before-dispatch behavior are the migration baseline; new JournalPolicy/provider/transport concepts should be introduced compatibly rather than by creating a second independent bus.
