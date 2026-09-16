# Phase 73 - Component Knowledge Contribution Hub

status=planned
planned_at=2026-09-09
checklist=[Phase 73 Checklist](phase-73-checklist.md)

## Goal

Define and implement a domain-neutral CNCF exchange through which a component
can contribute an attributable knowledge snapshot and one or more consumers
can discover and read it without depending directly on the provider.

## Contract Direction

- A stable `knowledgeDomainId` identifies the contributed knowledge domain.
- `providerComponent` identifies the current supplying component independently
  of the domain identity.
- A contribution is an immutable, complete snapshot with explicit generation,
  schema/version, provenance, and availability state.
- CNCF owns registration, discovery, selection, and delivery mechanics only.
  It does not interpret domain records or infer domain semantics.
- Consumers use the CNCF contract and do not bind to provider implementations.
- The initial contract is pull-after-assembly. Events, incremental updates,
  watching, and distributed propagation are deferred.

## Scope

- Inventory existing component registry, runtime binding, resource, and
  KnowledgeSource mechanisms before admitting a new abstraction.
- Define the smallest versioned provider, contribution descriptor, immutable
  snapshot, discovery, and consumer-read contracts.
- Preserve deterministic provider/domain selection and attributable failures.
- Integrate the contract with normal post-assembly component availability.
- Verify two interchangeable opaque fixture providers, one active at a time,
  and two independent consumers.
- Produce a frozen supplier handoff for Textus Knowledge Editor Phase 2 and
  Textus BoK Phase 7.5.

## Non-Goals

- Book, textual-work, terminology, ontology, or other domain semantics.
- A central CNCF knowledge store, editor, reasoner, or search engine.
- Provider-specific configuration or direct provider-to-consumer wiring.
- Mutable/incremental publication, events, watchers, hot reload, or remote
  federation.
- Textus BoK MCP publication or Textus Knowledge Editor user experience.

## Work Groups

### P73-A: Inventory and Contract Freeze

Classify reusable CNCF facilities, freeze the domain/provider identity split,
snapshot lifecycle, discovery/read contract, failure model, and failing-first
acceptance fixtures.

### P73-B: Hub Implementation

Implement only the missing domain-neutral registration, selection, and
delivery mechanics and preserve opaque domain payloads end to end.

### P73-C: Supplier and Consumer Acceptance

Verify assembly ordering, complete-snapshot replacement, attribution,
isolation, absence/failure behavior, multiple consumers, documentation,
review, validation, and release closure.

## Dependencies

- Reuse the accepted CNCF component assembly and Phase 70 post-assembly
  activation contracts; do not reopen them.
- This Phase may proceed independently of the active Phase 69 sequence and the
  planned Phases 71 and 72.

## Completion Conditions

Phase 73 closes when components can publish opaque, attributable, immutable
knowledge snapshots under stable domain identities, independent consumers can
discover and read them through CNCF, and accepted handoffs are available to
TKE Phase 2 and Textus BoK Phase 7.5.
