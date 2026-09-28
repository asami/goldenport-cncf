# Phase 95: UI-Facing View Model and Aggregate Contract

status=planned
planned_at=2026-09-27
execution_priority=nonblocking_for_flutter_application
checklist=[Phase 95 Checklist](phase-95-checklist.md)

## Purpose

Provide a reusable, application-authorized contract that lets a UI client read
declared CNCF Views and submit declared Aggregate commands. The contract must
preserve `ViewSpace` as the read-side runtime and `AggregateSpace` as the write
boundary. A UI-facing View Model is a typed projection and interaction
descriptor over those existing spaces, not a second canonical store or a
Flutter Widget/state object.

This Phase is independent of the Editing Studio Flutter application's current
fake and provisional CRUD adapters. Its phase number does not make Phases
89–94 or the Flutter application prerequisites of one another.

## Existing baseline

- `Component.viewSpace` and `Component.aggregateSpace` are distinct,
  component-local runtime spaces.
- `ViewSpace` supports registered default and named Views, find/query/count,
  cache policy, and invalidation.
- Protected `ActionCall` methods already read Views and dispatch Aggregate
  commands; committed mutations invalidate component-local Views.
- The existing `admin/view/read` Operation is an `admin.system` surface. It is
  not an application-facing API and must not be exposed to ordinary UI clients.
- CNCF already serves versioned REST Operations. OpenAPI currently includes
  View metadata but does not by itself establish a complete typed UI contract.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| UIV-95-01 | Freeze a versioned UI-facing View Model descriptor for declared collection/detail Views, query parameters, resource identity, revision, actions, and result/error states. | planned |
| UIV-95-02 | Define an application-facing Aggregate command interface with declared command identity, typed input, authorization, expected revision, and structured outcome. | planned |
| UIV-95-03 | Bind the descriptors to existing `ViewSpace` reads and protected Aggregate dispatch without exposing arbitrary Views, raw Aggregate mutation, or the Admin Operation. | planned |
| UIV-95-04 | Publish the same contract through REST v1 and machine-readable client metadata; prove stable serialization, paging/filtering, conflict, and failure semantics. | planned |
| UIV-95-05 | Validate one non-admin reference consumer and record the generated-client/Flutter adapter handoff without making it a Flutter development prerequisite. | planned |

## Contract boundaries

- Only explicitly declared application Views and commands are remotely
  addressable. Authorization is checked on every read and command using the
  caller's execution context; a client-supplied View name is not a grant.
- View queries return typed, reconstructable projections. Pagination,
  filtering, sorting, identity, revision, and unavailable/forbidden/not-found
  states have a versioned representation.
- Updates are commands against the Aggregate boundary. A command response
  distinguishes accepted, conflict, validation failure, and indeterminate
  outcomes; it must not imply that an asynchronous projection is already
  visible. The read-after-write/refresh rule is explicit.
- Existing component-local View invalidation is reused. Cross-process cache
  coherence or a subscription stream must not be inferred from it.
- The REST adapter is a transport projection of the same contract, not a
  separate business API. gRPC is deferred and must not be required for Phase
  95 or the Flutter application's initial delivery.

## Acceptance

Executable specifications must prove one declared collection/detail View and
one revision-checked Aggregate command through an ordinary authorized REST
client. They must cover authorized read, pagination, successful command and
subsequent refresh, stale revision, validation failure, unauthorized View or
command, and refusal of undeclared names. The proof must not rely on
`admin.system`, a generic mutable CRUD endpoint, or a Candidate-specific
schema in CNCF.

## Non-goals

- Replacing `ViewSpace`, `AggregateSpace`, `ViewCollection`, or their existing
  synchronization and cache policies.
- Implementing Editing Studio or Knowledge Workbench Candidate lifecycle.
- Defining Flutter Widgets, navigation, or app-session View state in CNCF.
- Requiring a CNCF client generator before the Flutter app can use fake,
  local, or provisional CRUD adapters.
- gRPC, push subscriptions, or cross-process View invalidation in this Phase.

## References

- [Phase 95 Checklist](phase-95-checklist.md)
- [Phase 7 Aggregate/View baseline](phase-7.md)
- [Phase 42 Static Web contract](phase-42.md)
- [Phase 60.1 Admin View Model](phase-60.1.md)
- [Entity runtime architecture](../notes/entity-runtime-architecture.md)
