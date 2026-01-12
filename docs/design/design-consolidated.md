Cloud Native Component Framework (CNCF) Design Notes
===================================================

This document consolidates CNCF design intent and decisions scattered across
docs, TODOs, and current code. It is a reconstruction of intent, not a redesign.

Sources consulted (non-exhaustive):
- docs/execution-model.md
- docs/execution-context.md
- docs/component-model.md
- docs/design/domain-component.md
- docs/glossary.md
- docs/job-management.md
- docs/adync-model.md
- README.md
- TODO.md
- src/main/scala/org/goldenport/cncf/service/Engine.scala
- src/main/scala/org/goldenport/cncf/service/OperationCall.scala
- src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala


Overview
--------
CNCF provides a stable runtime foundation for executing Cozy-generated
componentlets in cloud-native, event-centered systems. It standardizes:
- The execution boundary (OperationCall)
- The runtime context model (ExecutionContext)
- The execution coordinator (Engine)
- Separation between domain logic and infrastructure

DomainComponent architecture is defined in:
- docs/design/domain-component.md (DomainComponent / Cozy integration contract)

Compared to goldenport core, CNCF focuses on execution orchestration,
context binding, and operational semantics (authorization, observability,
lifecycle), while goldenport core supplies foundational primitives
(e.g., Consequence/Conclusion) and low-level utilities. CNCF is not an
application framework; application logic stays in componentlets and
application code.

Assumption: goldenport core provides the error model (Consequence/Conclusion)
and basic utilities, while CNCF defines runtime structure and boundaries.


Architecture
------------
The component model enforces strict separation:
- Component: runtime container, configuration binding, execution orchestration
- Componentlet: pure domain logic (deterministic, infra-independent)
- OperationCall: execution boundary between domain and runtime
- Engine: execution coordinator and policy application

Conceptual "Interaction Contract" layer:
- Component Operation Language (sync operations) and Component Reception
  Language (async events) are admission surfaces that are transformed into
  OperationCalls before execution. This layer is documented as tentative but
  informs the stable boundary (OperationCall).


Core Abstractions
-----------------
ExecutionContext
- Represents explicit execution-time facts bound to an OperationCall.
- Contents include security context, time/clock, locale, config snapshot,
  observability/tracing, and runtime lifecycle access.
- Created by infrastructure or test harnesses; domain code must not construct.
- Immutable during execution; must not be implicit in domain signatures.
- Coordinates UnitOfWork lifecycle and interpreters.

OperationCall
- Represents an executable plan with bound ExecutionContext.
- apply() takes no parameters and no implicits.
- Declares accessed resources (for authorization intent).
- Normalizes errors into Consequence/Conclusion.
- Only permitted access point from domain logic into runtime capabilities.

Engine
- Pure execution boundary: accepts OperationCalls and executes them.
- Orchestrates authorization decision, lifecycle (commit/abort/dispose),
  and observability (enter/leave).
- Must not construct or mutate ExecutionContext.
- Must not rely on implicit ExecutionContext.
- Variation and Extension Points
  - `docs/design/variation-and-extension-points.md` — canonical distinction between value variation and behavior extension in CNCF.


Execution Model
---------------
Execution is explicitly phased (docs/execution-model.md):
1) Authorization (pre-execution)
   - Decision evaluated before any observability.
   - Authorization failure is "not started" (no enter/leave events).
2) Operation Start
   - observe_enter marks true start.
3) Execution and Completion
   - Success: commit, observe_leave(Success).
   - Failure: abort, observe_leave(Failure(Conclusion)).
   - Exactly one observe_leave for any started operation.

- Component execution and persistence models:
  - Component Internal Execution Model
    `docs/design/component-internal-execution-model.md`
  - DataStore and Aggregate Persistence Model
    `docs/design/datastore-and-aggregate-persistence-model.md`
- Component and application responsibilities
  `docs/design/component-and-application-responsibilities.md`
- Memory-First Domain Architecture
  - Defines a domain runtime architecture where entities are
    operated in memory and persistence serves durability.
  - Establishes the execution model for domain subsystems,
    including entity classification (resource / task) and
    event handling.
  - See: memory-first-domain-architecture.md
- Configuration Model
  - Defines how system and subsystem configurations are normalized
    into a semantic configuration model and compiled into
    platform-specific DSLs (e.g. CDK).
  - Canonical reference for configuration, validation, and
    platform compilation architecture.
  - See: configuration-model.md
- Event-Driven Job Management (Phase 1–2) — canonical overview
  - `docs/design/event-driven-job-management.md`
- Client execution / demo specs
  - `docs/design/client-component-action-api.md` — Stage 4 finalized behavior (execution path, driver resolution chain, output contract, config keys).
- Component repository
  - `docs/design/component-repository.md` — canonical specification for repository types, CLI syntax, and discovery unification.
- Component instantiation
  - `docs/design/component-factory.md` — Factory/Provider/Group contract for turning discovered classes into Component instances.
  - JobPlan and ExpectedEvent (supporting)
    `docs/design/job-plan-expected-event.md`
  - JobState transition (supporting)
    `docs/design/job-state-transition.md`
  - JobEventLog persistence (supporting)
    `docs/design/job-event-log.md`
  - Canonical event shape (supporting)
    `docs/design/event-shape.md`
  - EventId and EventType (supporting)
    `docs/design/event-id-event-type.md`
- See the Observability / Audit design notes in `docs/design/execution-model.md`.
  (Events are primary facts, Observability is diagnostic, Audit is a view over Events)
- AI-Assisted Development Rules
  - workflow rules for AI-assisted development
Error handling:
- All paths use Consequence; exceptions are mapped to Conclusion.
- Authorization failure is not an operation failure.

Security notes:
- Security design notes (pre-execution vs in-action decisions, SecurityEvent) are summarized in `docs/design/execution-model.md`.
- Authorization failure handling is specified in `docs/design/execution-model.md`.

Current Engine.scala:
- execute() follows this shape but does not yet enforce authorization
  decisions (TODO indicates enforcement is planned).
- run() is a simplified path without auth and lifecycle handling.


Context Model
-------------
ExecutionContext is a passive carrier of execution facts:
- Security: principal, capabilities, security level.
- Time/clock: timestamp and time utilities (pending in TODO).
- Locale and environment.
- Observability: tracing identifiers and correlation.
- Resolved configuration snapshot.
- Runtime lifecycle integration (commit/abort/dispose).

Creation and propagation:
- Constructed by infrastructure (CLI/REST/MCP adapters, runtime adapters,
  test harnesses).
- Bound to OperationCall at creation time.
- Not implicit in domain APIs; domain code must not directly access it.

Testability and determinism:
- ExecutionContext can be constructed by test harnesses with deterministic
  clock, config, and runtime interpreters.
- Domain logic remains deterministic given the same OperationCall inputs.

Assumption: time_now, store_fetch, random_seed helper accessors are intended
to be provided by ExecutionContext (via OperationCall helpers) but are not
present in the current codebase.


OperationCall Design
--------------------
Binding and execution:
- OperationCall binds ExecutionContext explicitly at creation time.
- ExecutionContext is accessible only through OperationCall, not implicitly.
- OperationCall is a fully-bound executable unit; apply() has no parameters.

Protected helper pattern (planned):
- TODO calls for OperationCallFeaturePart and feature-specific mixins
  (Time / Security / Random / Config / Store).
- Helpers should be protected final and delegate only to ExecutionContext.
- Naming convention <prefix>_<functionality> to signal protected helpers.

Dependency injection and testing:
- Rather than direct DI, dependencies are accessed through ExecutionContext
  and helper methods on OperationCall.
- Tests can supply a custom ExecutionContext (e.g., deterministic clock,
  in-memory store, fixed random seed) to ensure reproducibility.

Assumption: OperationCallFeaturePart will be used to centralize access to
context utilities and provide test seams without exposing ExecutionContext
directly to domain code.


Boundaries
----------
goldenport core
- Provides foundational types and error model (Consequence/Conclusion).
- Defines low-level utilities and shared primitives.

CNCF
- Defines component model, execution boundary (OperationCall), Engine
  semantics, ExecutionContext model, and config resolution.
- Owns execution lifecycle, authorization decision integration,
  and observability semantics.

Application code
- Implements domain logic in componentlets and application-specific components.
- Constructs OperationCalls from commands/events, but does not own execution.
- Must not construct ExecutionContext.

Protocol / Ingress / Egress
- Adapters (CLI/REST/MCP, message brokers) construct ExecutionContext,
  resolve configuration, and admit requests/events into Components.
- Translate external protocol data into OperationCalls.


Historical Decisions (Explicit or Implied)
------------------------------------------
- OperationCall binds ExecutionContext at creation time.
- Engine executes OperationCall; context is passive, execution is active.
- ExecutionContext must not be implicit in domain signatures.
- Authorization decision is pre-execution; failure is not "operation failure".
- Observability must reflect truth (no enter/leave on auth failure).
- Errors are normalized into Consequence/Conclusion; exceptions are mapped.
- OperationCall is the stable execution boundary, even with tentative
  "Interaction Contract" concepts.

Observed inconsistency:
- docs/glossary.md says ExecutionContext is implicitly propagated.
  Normative execution-model and execution-context docs state it must not be
  implicit. The consolidated intent follows the normative documents.


Execution Flow (Typical)
------------------------
1) Ingress adapter receives command/event.
2) Component constructs an OperationCall and binds ExecutionContext.
3) Engine executes OperationCall:
   - authorizes (decision only for now)
   - observe_enter
   - execute logic
   - commit/abort
   - observe_leave
   - dispose
4) Job management (for async or configured cases) records outcome.

Domain logic (componentlets) does not observe context binding, authorization,
or lifecycle steps directly.


Design Principles
-----------------
- Separation of domain logic from runtime infrastructure.
- Explicit context binding; no implicit execution context.
- Stable execution boundary (OperationCall) for policy enforcement.
- Observability must reflect actual execution start and completion.
- Deterministic domain logic when given the same bound context.


Open Questions and Future Work (from TODO and gaps)
---------------------------------------------------
- OperationCallFeaturePart and helper-based DSL for context utilities.
- Time/clock, locale, ruleset integration into ExecutionContext.
- Authorization enforcement and audit integration.
- Context propagation across async boundaries.
- Boundary clarity between ExecutionContext and RuntimeContext.
- Generate OperationCall bindings from Cozy models.
- Align with Semantic Integration Engine usage patterns.

Not found in repo:
- Concrete time_now, store_fetch, random_seed helpers. These are inferred
  design intents that should be verified or located in external notes.
