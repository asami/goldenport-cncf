# Service Bus and Operational UI Boundary

## Service Bus placement

The existing CNCF Event Runtime (`EventBus` / `EventEngine`) is the baseline. It is CNCF runtime infrastructure used by higher-level facilities such as Job Management and Workflow/StateMachine, not a separately packaged built-in Component. It provides common publish/subscribe, optional authoritative journal persistence, correlation and transport abstraction.

Message semantics, journal policy and distribution scope are independent axes. Initial runtime delivery is subsystem-local/in-memory. Future SYSTEM scope may bridge through external transports such as Kafka or Kinesis without changing application publish APIs.

Event Sourcing is not a target. Entity/DB remains authoritative for current state; journaled events are authoritative records of what happened; OpenTelemetry remains observational telemetry.

## UI boundary

CNCF Dashboard/Admin exposes runtime-mechanism information needed to operate and debug CNCF itself: Service Bus health, recent events, journal status/counts, subscriptions/delivery status, and related Job/Workflow/StateMachine runtime information.

Textus Control Center consumes CNCF operational APIs/views/journal and presents higher-level system operations: cross-application/subsystem timelines, diagnosis and integrated infrastructure/AI resources. CNCF UI shows the mechanism; Control Center shows what is happening in the system.

## Existing implementation baseline

CNCF already implements `org.goldenport.cncf.event.EventBus` and related Event Runtime facilities. The current baseline includes publish/register-subscribe, deterministic synchronous dispatch, filtering by event name/kind/selector, authorization-aware dispatch, Action dispatch integration, and optional persistence through `EventPublishOption(persistent)` and `EventEngine.emit`. Persistent publication already performs persistence before subscriber dispatch.

The design in this document is therefore an **extension of the existing EventBus**, not the introduction of a parallel bus implementation. The term "Service Bus" may describe the architectural role, but the CNCF runtime API/model should evolve from the existing EventBus unless a later implementation need justifies a separate abstraction.

Planned evolution includes replacing/generalizing the boolean persistence option with JournalPolicy semantics, Journal SPI providers (SQLite/PostgreSQL), correlation/causation, journal query/timeline, SUBSYSTEM/SYSTEM scope, external Transport SPI (future Kafka/Kinesis), and Dashboard/Control Center operational integration.
