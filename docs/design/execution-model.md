Execution Model
===============

This document defines the authoritative execution semantics
of the Cloud-Native Component Framework (CNCF).

This document is NORMATIVE.
All implementations MUST conform to the semantics defined here.


----------------------------------------------------------------------
Overview
----------------------------------------------------------------------

CNCF execution is Action-centered and scenario-driven.

Primary flow:

args / http
  -> prepare
  -> ActionCall
  -> execute

Scenario and program execution are driven by run.


----------------------------------------------------------------------
Core Concepts
----------------------------------------------------------------------

ExecutionContext
----------------

ExecutionContext represents execution facts.

It includes, but is not limited to:
- security context (principal, capabilities, security level)
- runtime facilities
- configuration snapshot
- observability context
- UnitOfWork binding (runtime ownership)

ExecutionContext:
- MUST be constructed outside Engine
- MUST NOT be created or mutated by Engine
- MUST be immutable during execution
- MUST be bound to ActionCall

ExecutionContext MUST NOT be passed implicitly.


ActionCall
----------

ActionCall represents an executable plan with bound context.

Characteristics:
- ExecutionContext is bound at creation time
- execute takes no parameters and no implicits
- accessed resources are declared via:

    accesses: Seq[ResourceAccess]

ActionCall declares intent, not effect.

ActionCall is a complete executable unit.


Engine
------

Engine is the execution boundary.

Engine responsibilities:
- invoke authorization (decision only)
- manage runtime lifecycle (commit / abort / dispose)
- emit observability events
- coordinate execution phases

Engine MUST NOT:
- construct ExecutionContext
- mutate ExecutionContext
- make authorization decisions
- perform authorization enforcement
- use implicit ExecutionContext


Scenario
--------

Scenario is a run target.

Scenario:
- may cross multiple layers
- MUST avoid asserting internal implementation details
- SHOULD focus on observable outcomes


----------------------------------------------------------------------
Execution Phases
----------------------------------------------------------------------

Execution of an ActionCall is strictly divided into phases.


Phase 1: Authorization (Pre-Execution)
--------------------------------------

- Authorization is evaluated
- Policy evaluation is defined in SystemContext and executed using the
  Action ExecutionContext as input
- No observability events are emitted
- If authorization fails:
  - the action is considered not started
  - observe_enter MUST NOT be called
  - observe_leave MUST NOT be called

Authorization failure is not an action failure.
observe_enter / observe_leave MUST NOT be emitted on authorization failure.

Design Notes (Security)
-----------------------
- Security decisions can occur before execution and during execution.
- Pre-execution authorization failure is treated as "not started," so observe_enter/leave are not emitted.
- Access denials or constraints during execution are handled as SecurityEvent and remain distinct from ActionEvent / DomainEvent.

Authorization Failure Handling
------------------------------

Authorization is evaluated *before* an ActionCall is constructed or executed.

If authorization fails:

- The ActionCall is **not created and not invoked**
- Action execution does not occur
- An ActionEvent with result = `AuthorizationFailed` is created
- The ActionEngine directly invokes `UnitOfWork.commit(events)`
- The event is persisted and published through the same 2-phase commit
  path as successful actions (UnitOfWork → EventEngine → DataStore)

This design ensures that authorization failures are:

- Fully observable and auditable
- Persisted using the same transactional guarantees as normal actions
- Clearly separated from action execution concerns

ActionCall remains the execution unit for *authorized* actions only.
Authorization failures are treated as first-class events, not exceptions.


Phase 2: Action Start
---------------------

- observe_enter(action) is emitted
- This marks the true start of the action
- Tracing, auditing, and metrics MAY begin here


Phase 3: Execution and Completion
---------------------------------

Success:
- the action executes
- runtime.commit() is called
- observe_leave(action, Success(result)) is emitted

Failure:
- runtime.abort() is called
- observe_leave(action, Failure(conclusion)) is emitted
- the failure represents action failure

An action that starts MUST always produce exactly one
observe_leave event.


----------------------------------------------------------------------
Observability Semantics
----------------------------------------------------------------------

Observability MUST reflect truth.

Situation                | observe_enter | observe_leave
-------------------------|---------------|----------------
Authorization failure    | NO            | NO
Action success           | YES           | YES (Success)
Action failure           | YES           | YES (Failure)

Observability MUST NOT lie about whether an action started.

Design Notes (Observability / Audit)
------------------------------------
- Observability handles behavioral/diagnostic information, while SystemEvent / ActionEvent / DomainEvent represent facts (primary information).
- Audit is a view over primary facts (Events) and has a different purpose and lifecycle from Observability.
- The contract that observe_enter/leave are not emitted on authorization failure exists to guarantee that the action did not start.
- Execution observation hooks (observe_enter/leave) capture runtime behavior, while ActionEvent records the factual outcome, including non-execution.


----------------------------------------------------------------------
Runtime Pipeline (Post-Protocol Execution Path)
----------------------------------------------------------------------

This section records the CNCF runtime delivery pipeline from protocol
inputs through ActionCall execution. It mirrors the current implementation
so readers understand what happens in practice today.

Entry points
------------
- CLI modes: `CncfRuntime.executeCommand`, `executeClient`, `executeServer`, and `executeServerEmulator`
- Script execution: `ScriptRuntime`

Each entry point builds a `Subsystem` from resolved configuration and
walks into the shared execution path.

Ingress → Subsystem
--------------------
- `CncfRuntime` resolves configuration via `ConfigurationResolver` and
  passes the resulting `ResolvedConfiguration` into `DefaultSubsystemFactory`,
  returning a configured `Subsystem`.
- HTTP adapters (`HttpExecutionEngine`, `Http4sHttpServer`) and CLI/Script
  handlers feed requests to `Subsystem.execute` or `executeHttp`.
- The subsystem owns the resolved configuration snapshot and resolves
  routes before delegating to its components.

OperationRequest construction
-----------------------------
- `ComponentLogic.makeOperationRequest` converts DSL/protocol requests into
  `OperationRequest` (`Action`) instances.
- Protocol obligations end at `OperationRequest` creation; subsequent logic
  operates on semantic builders and does not consult raw DSL fragments.

Promotion to ActionCall
-----------------------
- `ComponentLogic.createActionCall` layers subsystem/component HTTP drivers,
  runtime/application contexts, and observability into a fresh
  `ExecutionContext`.
- `ActionEngine.createActionCall` wraps the `Action`, `ExecutionContext`, and
  optional correlation id inside `ActionCall.Core` and lets domain-specific
  `Action` implementations produce the concrete `ActionCall`.
- ActionCall creation is the last touchpoint before runtime execution semantics
  begin.

Execution
---------
- `ActionEngine.execute` is the single execution surface.
- Authorization hooks run first; on success `observe_enter` is emitted and
  execution proceeds.
- `ExecutionContext.runtime` handles commit/abort/dispose around
  `ActionCall.execute`.
- `UnitOfWork` coordinates data stores and event engines during commit/abort.
- Execution never depends on protocol artifacts beyond the bound `ActionCall`.

Transport completion
--------------------
- CLI entry points translate `Consequence[OperationResponse]` into responses
  and exit codes.
- HTTP execution returns `HttpResponse`, which `Http4sHttpServer` adapts for
  transport.
- Script execution reuses the same ActionCall flow and returns control to
  the calling harness (`ScriptRuntime`).

References
----------
- ExecutionContext lifecycle: `execution-context.md`
- ActionCall boundary: `domain-component.md`
- Component responsibilities: `component-model.md`
- Application/UnitOfWork lifecycle: `component-and-application-responsibilities.md`



----------------------------------------------------------------------
CncfRuntime CLI Normalization (Protocol + Configuration)
----------------------------------------------------------------------

Phase 2.8 Status: **Normalized / Fixed** (no new semantics introduced).  
This section captures how `CncfRuntime` resolves protocol and configuration
inputs before handing control to the ActionCall execution model.

CLI Responsibility Boundary
--------------------------
- `CncfRuntime` resolves the protocol surface (command/client/server/server-emulator),
  `ResolvedConfiguration`, and the normalization chain (argv → `Request` → `OperationRequest`).
- `CncfRuntime` does **not** interpret semantics, create ActionCalls, or manage
  ExecutionContext/UnitOfWork.

Canonical CLI Pipeline
----------------------
- argv flows into CLI adapters.
- Protocol-defined operations are parsed into `Request` objects.
- Configuration is resolved via `ConfigurationResolver` before execution.
- The ProtocolEngine normalizes inputs (argv → `Request` → `OperationRequest`).
- Runtime mode selection derives from the operation name.
- Control enters the mode-specific path (command / client / server / server-emulator).

Runtime Mode Dispatch Rule
--------------------------
- Runtime mode is derived from `OperationRequest.operation`.
- Mode selection is a CNCF runtime concern; the Protocol stays execution-agnostic.

Adapter Boundary
----------------
- CNCF may adapt `OperationRequest`s for subsystem/component routing without
  introducing new semantics.
- This adapter work is CNCF responsibility.

Script Execution Note
---------------------
- `ScriptRuntime` bypasses CLI protocol normalization.
- It still resolves configuration first, builds the `Subsystem`, and then
  produces `OperationRequest`/`Action` instances directly for the runtime pipeline.

Phase 2.8 Status Note
---------------------
- Marked as normalized/fixed for Phase 2.8; no new semantics are introduced here.

References
----------
- `configuration-model.md#configuration-propagation-model`
- `execution-context.md`
- `domain-component.md`

----------------------------------------------------------------------
Error Handling
----------------------------------------------------------------------

All execution paths use org.simplemodeling.Consequence.

- scala.util.Try MUST NOT be used
- exceptions are converted to Conclusion
- failures are represented as Consequence.Failure
- success is represented as Consequence.Success

This model ensures:
- semantic failures
- composability
- auditability


----------------------------------------------------------------------
Execution Naming and Boundaries
----------------------------------------------------------------------

prepare
- constructs Action or Scenario meaning structure
- MUST NOT execute
- MAY be used for validation or preview in Executable Specs

execute
- runs a prepared ActionCall
- MUST be limited to ActionCall execution

run
- drives Scenario, program, or workflow
- run MAY trigger multiple prepare and execute steps

prepare is intentionally constrained today.
It may be surfaced in the public API in the future,
but current implementations MUST keep prepare internal.


----------------------------------------------------------------------
Relation to Core Execution Model
----------------------------------------------------------------------

CNCF depends on goldenport core execution principles.

Differences:
- CNCF is Action-centered and scenario-driven
- CNCF binds UnitOfWork into ExecutionContext for runtime ownership
- CNCF emphasizes run as the Scenario driver

Core principles remain authoritative for primitives and invariants.
CNCF adds composition and execution structure on top.


----------------------------------------------------------------------
Design Invariants
----------------------------------------------------------------------

The following invariants MUST always hold:

- Authorization failure != Action failure
- ExecutionContext is bound to ActionCall
- Engine is a pure execution boundary
- Observability must never lie about whether an action started
- EventEngine.prepare fixes pending events for the transaction; commit operates only on the prepared events

These invariants are foundational to CNCF.

----------------------------------------------------------------------
Phase 2.8 Note: RuntimeScopeContext-Based Logging (Design Only)
----------------------------------------------------------------------

This note records a minimal design decision for Phase 2.8.
It does NOT introduce new execution semantics.

Design Intent
-------------
- Logging configuration is initialized once at runtime startup.
- Logging settings are sourced from resolved Configuration.
- Logging behavior propagates through the ScopeContext hierarchy.

Context Structure
-----------------
- A runtime-level scope (RuntimeScopeContext) acts as the root.
- Subsystem and Component scopes are children in the ScopeContext tree.
- No additional fields or behaviors are introduced in Phase 2.8.

Initialization Order
--------------------
1. Resolve Configuration.
2. Initialize runtime logging backend from Configuration.
3. Create the runtime root ScopeContext.
4. Create Subsystem and Component scopes as children.
5. Observability and logging consume scope information only.

Phase 2.8 Constraints
---------------------
- No dynamic reconfiguration.
- No multi-subsystem runtime assumptions.
- No scope-specific backend switching.
- This note exists to preserve a future extension point.
