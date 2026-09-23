# Phase 93: Service Bus Runtime and Admin Visibility

## Goal

Introduce Service Bus as a CNCF Runtime component and expose its essential operational information through CNCF Dashboard/Admin.

## Scope

- Common publish/subscribe API for Event and Signal.
- Independent JournalPolicy (NONE/AUTHORITATIVE).
- Independent distribution scope (SUBSYSTEM/SYSTEM).
- Initial SUBSYSTEM in-memory transport.
- Authoritative persistent journal for selected messages.
- Correlation/causation metadata and query/timeline support.
- StateMachine event subscription/drive integration.
- Job/Workflow lifecycle event publication integration.
- Transport SPI prepared for future Kafka/Kinesis SYSTEM transport.
- CNCF Dashboard/Admin basic Service Bus health, recent events, journal and subscription/delivery information.
- Operational API/View suitable for higher-level consumers such as Textus Control Center.

## Non-goals

- Event Sourcing/state reconstruction.
- Automatic recovery from the journal.
- Kafka/Kinesis implementation in the initial implementation.
- Full system-level operational analysis UI; that belongs to Textus Control Center.

## Reference executable scenario

Validate temporal decoupling with sm-workflow development monitoring: journal significant lifecycle/approval events, allow Control Center to consume/catch up asynchronously, and drive StateMachine subscribers where configured. Human approval remains an authenticated operation/admission request; its accepted outcome is published as an authoritative event.
