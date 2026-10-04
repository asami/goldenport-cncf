# Phase 93: Service Bus Runtime and Admin Visibility

## Goal

Evolve the existing CNCF EventBus/EventEngine runtime with authoritative journal, correlation, transport scope and operational visibility, while preserving the current publish/subscribe model.

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

## Journal providers

- Define a Journal SPI independent of transport.
- Implement SQLite as a first-class zero-setup/local authoritative journal provider.
- Implement PostgreSQL as the server/ops authoritative journal provider.
- Use explicit provider configuration; do not silently switch providers based on discovery.
- MacBook Air profile uses a local SQLite file because PostgreSQL is not used there.
- Mac mini profile uses PostgreSQL `ops` for Service Bus operational journal persistence; `dev` remains separate.
- Preserve commit-before-delivery semantics for JournalPolicy.AUTHORITATIVE on both providers.

## Existing implementation baseline

CNCF already implements `org.goldenport.cncf.event.EventBus` and related Event Runtime facilities. The current baseline includes publish/register-subscribe, deterministic synchronous dispatch, filtering by event name/kind/selector, authorization-aware dispatch, Action dispatch integration, and optional persistence through `EventPublishOption(persistent)` and `EventEngine.emit`. Persistent publication already performs persistence before subscriber dispatch.

The design in this document is therefore an **extension of the existing EventBus**, not the introduction of a parallel bus implementation. The term "Service Bus" may describe the architectural role, but the CNCF runtime API/model should evolve from the existing EventBus unless a later implementation need justifies a separate abstraction.

Planned evolution includes replacing/generalizing the boolean persistence option with JournalPolicy semantics, Journal SPI providers (SQLite/PostgreSQL), correlation/causation, journal query/timeline, SUBSYSTEM/SYSTEM scope, external Transport SPI (future Kafka/Kinesis), and Dashboard/Control Center operational integration.

## SQLite physical boundary

- Default to one SQLite journal database per Subsystem, rather than one host-wide SQLite database.
- Support hosts such as MacBook Air running around ten concurrent Subsystems without funneling all writers into one SQLite writer lock.
- Use WAL/busy-timeout defaults appropriate for local concurrent access.
- PostgreSQL may be physically shared across Subsystems but must preserve subsystemId/logical journal partitioning.
- Keep Journal query/API semantics independent of the physical provider layout.

## Textus EAI Event integration requirements — 2026-10-03

Add EAI as an Event/correlation consumer alongside the `sm-workflow` development
monitoring scenario. See the
[Textus EAI requirement intake](../journal/2026/10/2026-10-03-textus-eai-workflow-requirements.md).
This is a future Service Bus adoption case; EAI's first fixed harness does not
require Phase 93, a network transport or authoritative journal deployment.

### EAI-93: Event and Workflow correlation

Stage Status:
- Current status: OPEN
- Owner: CNCF EventBus/EventEngine owner; EAI Event admission and association owner
- Update rule: Close this consumer boundary with declared routing and authoritative correlation evidence, without changing EAI admission or Workflow progression ownership.

- [ ] Preserve source/event, delivery attempt, pinned Binding, start invocation, Job/Workflow and Continuation correlation as distinct facts; expose authoritative lifecycle facts through the normal journal/query boundary where journaling is selected.
- [ ] Keep EAI schema/source/Binding admission and sequential duplicate/conflict decisions application-owned. A journal delivery or replay does not by itself authorize another Workflow start.
- [ ] Route typed completion through the declared authenticated/scoped Operation and native admission; a bus event or worker prose must not bypass result/evidence checks or directly select the next Workflow state.
- [ ] Distinguish transport delivery, journal persistence, managed Job completion and Workflow business outcome. Preserve commit-before-delivery for authoritative journaling and report incomplete/unknown outcomes explicitly.
- [ ] Verify scope, redaction and read-only correlation lookup; retain the existing exclusion of automatic recovery and distributed exactly-once claims.
