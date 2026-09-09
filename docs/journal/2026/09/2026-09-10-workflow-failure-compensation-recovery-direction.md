# Workflow Failure, Compensation, and Recovery Direction

Date: 2026-09-10
Status: design direction / Phase 64 input

## Context

Workflow is being refined as a specialization/profile of Composite StateMachine.
Unlike an ordinary Entity StateMachine, whose state and persistence normally belong
to the Entity, Workflow has a dedicated `WorkflowInstance`. A Workflow is owned by
a Component and its persistence is managed by that Component.

The current development focus is one Subsystem. Within that Subsystem, a Workflow
may compose local Workflows and Workflows exported by dependent Components as
SubWorkflows. Cross-Subsystem federation is a future concept and is intentionally
not required for the initial Workflow implementation.

Failure and compensation must nevertheless be considered now so that the v1
contracts do not block later recovery automation.

## Core Principle

Workflow execution failure and the obligation to restore consistency are distinct
facts and therefore distinct runtime events.

```text
Workflow execution failure
  -> WorkflowFailed
  -> diagnose / observe / retry / recover
  -> consistency guaranteed?
       +-- yes -> continue or terminate according to Workflow semantics
       +-- no  -> CompensationRequired
```

`WorkflowFailed` means that Workflow execution failed.

`CompensationRequired` means something stronger: after that failure, CNCF cannot
guarantee that the effects already produced by the Workflow leave the system in
an acceptable consistent state. Recovery or compensation is therefore an explicit
operational obligation.

A `WorkflowFailed` event does not necessarily imply `CompensationRequired`.
Transient failures that can be safely retried or otherwise recovered may never
create a compensation obligation.

## Failure Is Observable

Workflow failure is a first-class runtime outcome and must be observable.

A failure event should carry enough stable identity and evidence to correlate the
failure with the Workflow execution, including as applicable:

- Workflow definition identity;
- Workflow instance identity;
- owning Component;
- current/failed Workflow state or transition;
- failed Operation, Job, or SubWorkflow reference;
- structured cause/failure information;
- causal execution/event identity; and
- timestamps and source/provenance information required for diagnosis.

The event is not merely logging. It is part of the runtime contract used by
observability, diagnosis, recovery policy, and operations.

## Compensation Requirement

When CNCF cannot guarantee consistency after a Workflow failure, it publishes a
first-class compensation-required event.

Conceptually:

```text
WorkflowFailed
  -> recovery analysis
  -> consistency cannot be guaranteed
  -> CompensationRequired
```

The compensation-required evidence should make it possible to determine what has
already happened and what remains unresolved. Candidate information includes:

- originating Workflow failure identity;
- Workflow instance identity;
- completed externally visible effects;
- pending or uncertain effects;
- affected Operations, Jobs, and SubWorkflows;
- known compensation/recovery program reference, when declared; and
- evidence needed for automatic or human recovery.

The exact ABI is intentionally deferred until the Workflow instance/history and
action-program contracts are frozen.

## Explicit Compensation Program as the Final Line

CNCF must not depend on an implicit compensation engine being able to reverse every
business effect.

The fundamental fallback is an explicitly supplied compensation/recovery program.
Business logic that cannot be derived safely by the runtime can always be expressed
by application-defined logic.

Conceptually:

```text
CompensationRequired
  -> automatic handling when proven safe
  -> declarative/generated compensation when available
  -> explicit Compensation Program
  -> human recovery when programmatic compensation cannot complete
```

The design should progressively increase the range that CNCF can handle
automatically, but automatic handling must remain an optimization over the explicit
recovery contract rather than its replacement.

The Compensation Program should align with the Phase 64 typed Action Program
architecture where practical. Compensation must not introduce an unrelated opaque
callback mechanism if the same semantics can be represented by the typed logical
program/interpreter model.

A compensation program may ultimately invoke ordinary CNCF execution boundaries,
including Operations, Jobs, and Workflows/SubWorkflows. Compensating business
changes that affect Entity state must continue to respect the normal StateMachine
transition boundary.

## Human Recovery Is a First-Class Operation

Some failures cannot be compensated safely or completely by software. This is not
an exceptional hole outside the architecture.

Human intervention is the final recovery mechanism and must be treated as a
first-class operational capability.

If compensation cannot be determined, cannot be executed, or itself fails, CNCF
must leave durable evidence and publish an event that can drive an operational
recovery process rather than silently abandoning the Workflow.

Conceptually:

```text
CompensationRequired
  -> compensation/recovery attempt
       +-- recovered -> resolved
       +-- cannot compensate / compensation failed
             -> ManualRecoveryRequired
             -> operational tooling / notification
             -> human Recovery Operation
             -> resolved or explicitly terminated
```

The exact event names are provisional, but the distinction between execution
failure, compensation obligation, and manual recovery obligation is architectural.

Human recovery operations may include, according to admitted policy:

- retrying a failed execution;
- resuming a Workflow from a known safe point;
- invoking an explicit recovery or compensation program;
- executing a domain-specific recovery Operation;
- resolving uncertain external effects; and
- explicitly marking the recovery obligation resolved or terminated with audit
  evidence.

## SubWorkflow Implications

SubWorkflow structure should reuse the existing Sub/Composite StateMachine
mechanism as far as possible, but Workflow has additional runtime semantics.

A SubWorkflow has its own `WorkflowInstance`; it is not merely nested mutable
StateMachine state. The child Workflow remains owned and persisted by its owning
Component, including when the Workflow is exported by a dependent Component.

Therefore failure propagation must preserve both identities:

```text
Parent WorkflowInstance
  -> Child WorkflowInstance
       -> WorkflowFailed
       -> child recovery/compensation analysis
       -> child outcome propagated to parent according to binding
```

The parent must not lose the child's Workflow-specific failure, recovery, and
compensation evidence when the structural Composite StateMachine projection is
updated.

## Relationship to Saga

For the current one-Subsystem target, CNCF does not need to introduce Saga as a
separate primary abstraction.

Workflow already provides most of the required long-running process capabilities
inside a Subsystem, including dedicated instances, persistence, Component-owned
composition, SubWorkflow invocation, failure handling, recovery, and eventually
compensation.

A future cross-Subsystem concept may coordinate independently operated Workflow
runtimes. `Federated Workflow` is a working conceptual name only; it is not part of
the current implementation scope. The current Workflow contracts should avoid
preventing such federation, but Phase 64 should remain focused on concrete
single-Subsystem Workflow semantics.

## v1 Direction

The initial Workflow implementation should prioritize the contracts that later
recovery automation depends on.

Required or strongly preferred v1 foundations are:

- durable Workflow instance identity and state;
- structured Workflow failure outcome;
- `WorkflowFailed` publication;
- enough execution/history evidence to identify completed, pending, and uncertain
  work;
- explicit distinction between ordinary failure and inability to guarantee
  consistency;
- a `CompensationRequired` event contract or reserved runtime hook;
- a typed registration/binding point for explicit compensation/recovery programs;
- SubWorkflow failure/outcome propagation without losing child instance identity;
- observability of recovery obligations; and
- a defined path to first-class human recovery operations.

Full automatic compensation scheduling, compensation ordering, compensation of
compensation, and rich recovery policy may remain future work.

## Design Rule

The governing rule is:

> Every Workflow failure is observable. When CNCF cannot guarantee consistency,
> that uncertainty becomes an explicit compensation/recovery obligation. CNCF may
> automate recovery where it can prove the behavior safe, but explicit recovery
> programs and ultimately human recovery remain first-class final lines of defense.

This direction should be reflected when Phase 64 freezes Workflow-specific
semantics, WorkflowInstance persistence/history, failure events, action-program
integration, and runtime observability.