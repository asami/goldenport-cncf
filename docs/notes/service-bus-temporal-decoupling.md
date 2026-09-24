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

## Existing implementation baseline

CNCF already implements `org.goldenport.cncf.event.EventBus` and related Event Runtime facilities. The current baseline includes publish/register-subscribe, deterministic synchronous dispatch, filtering by event name/kind/selector, authorization-aware dispatch, Action dispatch integration, and optional persistence through `EventPublishOption(persistent)` and `EventEngine.emit`. Persistent publication already performs persistence before subscriber dispatch.

The design in this document is therefore an **extension of the existing EventBus**, not the introduction of a parallel bus implementation. The term "Service Bus" may describe the architectural role, but the CNCF runtime API/model should evolve from the existing EventBus unless a later implementation need justifies a separate abstraction.

Planned evolution includes replacing/generalizing the boolean persistence option with JournalPolicy semantics, Journal SPI providers (SQLite/PostgreSQL), correlation/causation, journal query/timeline, SUBSYSTEM/SYSTEM scope, external Transport SPI (future Kafka/Kinesis), and Dashboard/Control Center operational integration.
