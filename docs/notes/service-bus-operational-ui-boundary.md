# Service Bus and Operational UI Boundary

## Service Bus placement

Service Bus is a CNCF Runtime component alongside Job Management and Workflow/StateMachine. It provides common publish/subscribe, optional authoritative journal persistence, correlation and transport abstraction.

Message semantics, journal policy and distribution scope are independent axes. Initial runtime delivery is subsystem-local/in-memory. Future SYSTEM scope may bridge through external transports such as Kafka or Kinesis without changing application publish APIs.

Event Sourcing is not a target. Entity/DB remains authoritative for current state; journaled events are authoritative records of what happened; OpenTelemetry remains observational telemetry.

## UI boundary

CNCF Dashboard/Admin exposes runtime-mechanism information needed to operate and debug CNCF itself: Service Bus health, recent events, journal status/counts, subscriptions/delivery status, and related Job/Workflow/StateMachine runtime information.

Textus Control Center consumes CNCF operational APIs/views/journal and presents higher-level system operations: cross-application/subsystem timelines, diagnosis and integrated infrastructure/AI resources. CNCF UI shows the mechanism; Control Center shows what is happening in the system.
