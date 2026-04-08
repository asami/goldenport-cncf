# Event Mechanism Extension Work Items

## Context

CNCF already has the foundations of an event mechanism.

Existing pieces include:

- `EventBus` for publish/subscribe dispatch
- `EventReception` for reception rules and action dispatch
- event continuation modes such as `SameJob` and `NewJob`
- builtin event and job-control components for inspection

The next step is not to invent a separate mechanism, but to extend the
existing event system so that components inside a subsystem can react to events
published by other components as a normal runtime pattern.

## Goal

Establish event-driven component collaboration as a standard CNCF runtime
capability inside a subsystem.

This means:

- a component can publish an event
- another component in the same subsystem can subscribe and react
- reaction can run in the same job or a new job
- callers and tests can wait for event-visible completion without relying on
  ad hoc polling logic

## Scope

Primary scope for this work:

- intra-subsystem event collaboration
- action dispatch through event subscriptions
- job integration for event-triggered execution
- internal waiting support based on event completion

Explicitly out of immediate scope:

- inter-subsystem transport over external service bus
- external application delivery protocol
- public application-facing async abstraction finalization

## Work Items

### 1. Subsystem-level Event Wiring

Define the standard subsystem-owned event facilities.

Tasks:

- make subsystem-level `EventBus` ownership explicit
- make subsystem-level `EventReception` ownership explicit
- ensure component bootstrap can register subscriptions into subsystem scope
- define lifecycle and visibility of subsystem event facilities

Expected outcome:

- components in the same subsystem can publish and receive events through a
  shared subsystem event context

### 2. Component Subscription Bootstrap

Make subscription registration part of normal component startup.

Tasks:

- define how component descriptors / generated definitions contribute
  subscriptions
- define handwritten registration path for custom factories
- ensure duplicate registration and ordering behavior are deterministic
- clarify authorization boundary for subscription registration

Expected outcome:

- event-capable components do not require ad hoc manual wiring for normal
  subsystem collaboration

### 3. Event-to-Action Dispatch Contract

Refine the contract by which a received event triggers an action call.

Tasks:

- confirm canonical target resolution rules
- confirm event payload to action input mapping rules
- document selector behavior and failure handling
- clarify same-component versus cross-component dispatch semantics

Expected outcome:

- event-triggered action execution becomes predictable and testable

### 4. Event Continuation and Job Integration

Promote existing continuation behavior into a clearer execution model.

Tasks:

- formalize `SameJob` versus `NewJob`
- define job metadata inherited from event context
- define correlation / causation / parent-job propagation
- define failure, retry, and dead-letter expectations for event-triggered jobs

Expected outcome:

- event reception can start follow-up work under job management in a consistent
  way

### 5. Internal Await Based on Event Completion

Replace ad hoc demo/test waiting with event-aware waiting support.

Tasks:

- keep current polling helper internal only
- add event-aware internal await utilities where practical
- define minimal contract for waiting on event visibility or event-derived
  completion
- apply it first to tests, executable specs, and demos

Expected outcome:

- local verification paths can wait for event-driven completion without fixed
  sleeps

### 6. Observability and Inspection

Expose enough visibility to debug event-driven collaboration.

Tasks:

- ensure emitted events can be inspected per job / per correlation
- confirm builtin event component coverage for event-driven paths
- add traces for subscription matching and dispatch outcome
- define minimum operator-facing diagnostics for event dispatch failure

Expected outcome:

- event-driven execution is observable enough for development and operations

### 7. Test Strategy

Add focused regression coverage for the event extension path.

Tasks:

- subsystem-level component-to-component event collaboration test
- same-job continuation test
- new-job continuation test
- action-call wait/visibility test without fixed sleep
- authorization and failure-path tests for event dispatch

Expected outcome:

- event collaboration becomes a protected framework capability rather than an
  experimental behavior

## Working Policy

Until the full event-driven collaboration model is established:

- polling helpers remain internal utilities only
- application-facing async semantics are not yet considered stable
- subsystem-internal event collaboration is the first-class target
- message/signal style integration outside that scope remains separate work

## Immediate Next Slice

The most practical next slice is:

1. subsystem-level shared event wiring
2. component subscription bootstrap
3. a minimal integration test where one component publishes an event and
   another component reacts through `EventReception`
