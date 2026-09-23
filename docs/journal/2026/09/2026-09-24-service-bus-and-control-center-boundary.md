# Service Bus and Control Center Boundary

Date: 2026-09-24

Decision: evolve the already implemented CNCF Event Runtime (`EventBus` / `EventEngine`). EventBus is lower-level runtime infrastructure used by Job Management and Workflow/StateMachine rather than a new separately packaged Service Bus component. CNCF Dashboard/Admin will expose basic Service Bus and journal operational information for runtime administration and debugging.

Textus Control Center is the higher-level operational surface. It uses CNCF views/APIs and authoritative journal records to build meaningful timelines and cross-subsystem/application views, and combines them with non-CNCF resources such as databases, OpenTelemetry, OpenClaw and AI runtimes.

The boundary is: CNCF UI explains/operates the runtime mechanism; Control Center explains/operates the Textus system.

## Existing implementation baseline

CNCF already implements `org.goldenport.cncf.event.EventBus` and related Event Runtime facilities. The current baseline includes publish/register-subscribe, deterministic synchronous dispatch, filtering by event name/kind/selector, authorization-aware dispatch, Action dispatch integration, and optional persistence through `EventPublishOption(persistent)` and `EventEngine.emit`. Persistent publication already performs persistence before subscriber dispatch.

The design in this document is therefore an **extension of the existing EventBus**, not the introduction of a parallel bus implementation. The term "Service Bus" may describe the architectural role, but the CNCF runtime API/model should evolve from the existing EventBus unless a later implementation need justifies a separate abstraction.

Planned evolution includes replacing/generalizing the boolean persistence option with JournalPolicy semantics, Journal SPI providers (SQLite/PostgreSQL), correlation/causation, journal query/timeline, SUBSYSTEM/SYSTEM scope, external Transport SPI (future Kafka/Kinesis), and Dashboard/Control Center operational integration.
