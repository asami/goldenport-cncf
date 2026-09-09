# Workflow Failure, Compensation, Recovery, and Semantic Projection Direction

Date: 2026-09-10
Status: design direction / Phase 64 input

## Context

Workflow is being refined as a specialization/profile of Composite StateMachine. Unlike an ordinary Entity StateMachine, whose state and persistence normally belong to the Entity, Workflow has a dedicated `WorkflowInstance`. A Workflow is owned by a Component and its persistence is managed by that Component.

The current development focus is one Subsystem. Within that Subsystem, a Workflow may compose local Workflows and Workflows exported by dependent Components as SubWorkflows. Cross-Subsystem federation is a future concept and is intentionally not required for the initial Workflow implementation.

Failure, compensation, semantic description, and downstream projections must be considered now so that the v1 contracts do not block later recovery automation or human-oriented model views.

## Workflow Semantic Metadata

Workflow is executable semantics, but it is also a named business-process concept that must remain understandable outside the runtime engine.

At minimum the generated/published Workflow contract should preserve stable semantic metadata for:

- Workflow identity and owning Component;
- purpose/goal;
- related Use Cases;
- related domain subjects such as Entities/Aggregates where admitted by the model;
- SubWorkflow relations, including references to Workflows exported by dependent Components;
- related Operations, Jobs, Events, StateMachines, and transitions where applicable; and
- source identity/provenance required for navigation and review.

Actors are not duplicated as authoritative Workflow properties. Human and system actors are derived through the explicit `Workflow -> related Use Case -> Actor` semantic path. This permits questions such as "who are the human participants related to this Workflow?" to be answered with provenance showing the Use Cases through which each Actor participates.

CNCF need not interpret all descriptive metadata as runtime behavior, but it must not discard semantic identity required by downstream consumers.

## Projection Boundary

Higher-level tools may project the same canonical Workflow semantics in more than one way.

A semantically faithful StateMachine-oriented view may expose State, Transition, Trigger/Event, Guard, Action, SubWorkflow, Operation/Job, and related runtime evidence.

A flowchart-style overview may instead optimize for human comprehension, especially for non-engineering stakeholders. Such a view may omit, aggregate, rename for presentation, or visually simplify details when the projection rules allow it. It is explicitly non-authoritative and need not be a reversible representation of every StateMachine semantic.

The governing projection rule is:

> A simplified Workflow view may be incomplete, but it must not invent unsupported semantics.

CNCF therefore does not introduce a second activity-based Workflow model for presentation. It publishes stable Workflow semantics and evidence; CBD Support or another presentation layer owns simplified flowchart projection.

## Core Failure Principle

Workflow execution failure and the obligation to restore consistency are distinct facts and therefore distinct runtime events.

```text
Workflow execution failure
  -> WorkflowFailed
  -> diagnose / observe / retry / recover
  -> consistency guaranteed?
       +-- yes -> continue or terminate according to Workflow semantics
       +-- no  -> CompensationRequired
```

`WorkflowFailed` means that Workflow execution failed.

`CompensationRequired` means something stronger: after that failure, CNCF cannot guarantee that the effects already produced by the Workflow leave the system in an acceptable consistent state. Recovery or compensation is therefore an explicit operational obligation.

A `WorkflowFailed` event does not necessarily imply `CompensationRequired`. Transient failures that can be safely retried or otherwise recovered may never create a compensation obligation.

## Failure Is Observable

Workflow failure is a first-class runtime outcome and must be observable. A failure event should carry enough stable identity and evidence to correlate the failure with the Workflow execution, including Workflow definition/instance identity, owning Component, failed state/transition, failed Operation/Job/SubWorkflow, structured cause, causal execution/event identity, and diagnostic provenance.

The event is not merely logging. It is part of the runtime contract used by observability, diagnosis, recovery policy, and operations.

## Compensation Requirement

When CNCF cannot guarantee consistency after a Workflow failure, it publishes a first-class compensation-required event.

```text
WorkflowFailed
  -> recovery analysis
  -> consistency cannot be guaranteed
  -> CompensationRequired
```

Compensation-required evidence should identify the originating failure, completed externally visible effects, pending or uncertain effects, affected Operations/Jobs/SubWorkflows, known compensation/recovery program reference when declared, and evidence needed for automatic or human recovery. The exact ABI is deferred until Workflow instance/history and action-program contracts are frozen.

## Explicit Compensation Program as the Final Line

CNCF must not depend on an implicit compensation engine being able to reverse every business effect. The fundamental programmatic fallback is an explicitly supplied compensation/recovery program.

```text
CompensationRequired
  -> automatic handling when proven safe
  -> declarative/generated compensation when available
  -> explicit Compensation Program
  -> human recovery when programmatic compensation cannot complete
```

Automatic handling is an optimization over the explicit recovery contract rather than its replacement. The Compensation Program should align with the Phase 64 typed Action Program architecture where practical and may invoke ordinary CNCF boundaries including Operations, Jobs, and Workflows/SubWorkflows. Compensating Entity changes must continue to respect normal StateMachine transition authority.

## Human Recovery Is a First-Class Operation

Some failures cannot be compensated safely or completely by software. Human intervention is therefore the final recovery mechanism and a first-class operational capability.

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

The exact event names are provisional, but the distinction between execution failure, compensation obligation, and manual recovery obligation is architectural. Human recovery may retry, resume from a safe point, invoke a recovery/compensation program, execute a domain-specific recovery Operation, resolve uncertain external effects, or explicitly resolve/terminate the obligation with audit evidence.

## SubWorkflow Implications

SubWorkflow structure should reuse the existing Sub/Composite StateMachine mechanism as far as possible, but Workflow has additional runtime semantics.

A SubWorkflow has its own `WorkflowInstance`; it is not merely nested mutable StateMachine state. The child Workflow remains owned and persisted by its owning Component, including when exported by a dependent Component.

```text
Parent WorkflowInstance
  -> Child WorkflowInstance
       -> WorkflowFailed
       -> child recovery/compensation analysis
       -> child outcome propagated to parent according to binding
```

The parent must not lose the child's Workflow-specific identity, semantic metadata, failure, recovery, and compensation evidence when the structural Composite StateMachine projection is updated.

## Relationship to Saga

For the current one-Subsystem target, CNCF does not need to introduce Saga as a separate primary abstraction. Workflow already provides most required long-running process capabilities inside a Subsystem, including dedicated instances, persistence, Component-owned composition, SubWorkflow invocation, failure handling, recovery, and eventually compensation.

A future cross-Subsystem concept may coordinate independently operated Workflow runtimes. `Federated Workflow` is a working conceptual name only; it is not part of the current implementation scope. Current contracts should avoid preventing such federation, but Phase 64 remains focused on concrete single-Subsystem Workflow semantics.

## v1 Direction

Required or strongly preferred v1 foundations are:

- durable Workflow instance identity and state;
- Workflow purpose and related-Use-Case semantic metadata with stable identities;
- preservation of the semantic path needed to derive Actors/participants from related Use Cases;
- SubWorkflow identity/composition metadata across dependent Components;
- structured Workflow failure outcome and `WorkflowFailed` publication;
- enough execution/history evidence to identify completed, pending, and uncertain work;
- explicit distinction between ordinary failure and inability to guarantee consistency;
- a `CompensationRequired` event contract or reserved runtime hook;
- a typed registration/binding point for explicit compensation/recovery programs;
- SubWorkflow failure/outcome propagation without losing child instance identity;
- observability of recovery obligations; and
- a defined path to first-class human recovery operations.

Full automatic compensation scheduling, compensation ordering, compensation of compensation, rich recovery policy, and cross-Subsystem federation may remain future work.

## Design Rule

> Every Workflow failure is observable. When CNCF cannot guarantee consistency, that uncertainty becomes an explicit compensation/recovery obligation. CNCF may automate recovery where it can prove the behavior safe, but explicit recovery programs and ultimately human recovery remain first-class final lines of defense.

Workflow semantic metadata and presentation follow a complementary rule:

> CNCF preserves authoritative Workflow semantics and stable identity. Higher-level tools may provide simplified human-oriented projections, but those projections do not become an alternative Workflow language or runtime truth.

This direction should be reflected when Phase 64 freezes Workflow-specific semantics, WorkflowInstance persistence/history, semantic metadata, SubWorkflow contracts, failure events, action-program integration, and runtime observability.