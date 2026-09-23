# Service Bus Temporal Decoupling

Persistent events provide a temporal decoupling boundary between CNCF/Textus subsystems. Producers and consumers do not need to be simultaneously available, and the journal preserves the authoritative handoff history.

## Core rule

- Commands/operations request an action.
- Events describe what happened.
- Selected events use JournalPolicy.AUTHORITATIVE and are committed before delivery.
- Transient signals/events may use JournalPolicy.NONE when persistence has little value, such as many reference/status interactions or high-frequency progress/heartbeat traffic.

Entity/DB is authoritative for current state. The Service Bus Journal is authoritative for journaled operational facts. OpenTelemetry is observational telemetry. Event Sourcing and automatic state reconstruction are not targets.

## StateMachine integration

A Service Bus event may drive a CNCF StateMachine through subscription/API integration. The bus transports and journals the event; StateMachine owns interpretation and transition semantics. Correlation and causation metadata make input event -> transition -> resulting event traceable.

## Scope and transport

Initial SUBSYSTEM delivery is in-memory. SYSTEM scope is a future transport concern, with Kafka/Kinesis-style adapters. The publish contract should remain stable across transports. Internal events need not be exported; only explicitly SYSTEM-scoped integration events cross subsystem boundaries.

## Reference scenario

sm-workflow publishes authoritative lifecycle/admission events. Control Center consumes them asynchronously for Web/mobile/watch monitoring. A human approval is sent back as an authenticated operation to sm-workflow; the accepted result is again published as an authoritative event. sm-workflow does not depend on a watch/mobile client, and clients do not depend on Codex/OpenClaw internals.
