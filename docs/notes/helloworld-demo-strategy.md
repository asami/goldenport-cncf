# HelloWorld Demo Strategy

## 1. Goal and Scope
This strategy defines the minimal, experience-first path to complete the
HelloWorld demo before entering CRUD / CML development.

The objective is to stabilize the demo experience and execution boundary,
so that later CRUD/CML work can build on a proven, user-visible flow.

Scope is intentionally demo-first:
- Establish a clean startup and execution path.
- Validate the user experience across server, client, and command modes.

CRUD / CML is explicitly out of scope for this phase.

## 1.1 Terminology

This document uses the term **Stage** to describe user-visible demo milestones.

The term **Step** is reserved for concrete implementation tasks and
is intentionally not used at the structural level in this document.

## 2. Target Demo Experience
The demo experience should provide:
- No-setting HelloWorld server startup
- OpenAPI-based API specification exposure
- Client mode usage
- Command mode usage
- First custom HelloWorld Component implementation

## 3. Architectural Principles
- Subsystem is the stable execution boundary.
- Component / Service / Operation model is reused across modes.
- No special REST adapter is introduced.
- Projection-based outputs (OpenAPI, CLI help) are the primary interface.
- A single implementation supports multiple projections.

## 4. Demo Stages

The demo is completed through a sequence of **Stages**.
Each stage represents a stable, user-visible capability.
Implementation steps may be broken down separately.


### Stage 1: No-Setting HelloWorld Server
- Default Subsystem instantiation
- AdminComponent availability
- HTTP server delegation to `Subsystem.executeHttp`

#### Observability (Stage 1 Completion Criteria)

This stage validates the basic observability wiring between the execution model
and external visibility. The goal is not advanced monitoring, but to ensure that
execution events can be observed during development and debugging.

Completion criteria:

- The observation DSL is defined:
  - observe_fatal
  - observe_error
  - observe_warning
  - observe_info
  - observe_debug
  - observe_trace

- Action execution automatically resolves its observation scope
  (component / service / operation) without explicit specification
  by the action implementation.

- The following observation events are emitted:
  - observe_info on Subsystem startup
  - observe_error on Action execution failure
  - observe_fatal on unrecoverable execution failures

- Observation events are emitted via a temporary SLF4J-based sink:
  - No SLF4J StaticLoggerBinder warnings are emitted at startup
  - Observation output is visible in standard logs

- Observation does not affect execution control:
  - observe_* does not throw, retry, or alter execution flow
  - Execution control remains the responsibility of ActionEngine

### Stage 2: Executable HelloWorld API
- `ping` operation returning 200 OK
- Health / info as future extensions

### Stage 3: OpenAPI Projection
- Read-only OpenAPI generation from Subsystem model
- No manual spec writing
- Projection, not adapter

### Stage 4: Client and Command Modes
- `cncf` client execution
- Remote Subsystem invocation
- Same Component/Operation model
- Local Subsystem execution as a command
- Batch / admin use cases
- Shared implementation with server mode

### Stage 5: First Custom HelloWorld Component Extension
- Add a simple operation (e.g. `hello(name)`)
- Verify propagation to server / OpenAPI / client / command

### Stage 6: Demo Consolidation

This stage focuses on polishing the demo experience:
- Documentation and demo script preparation
- Error handling and messaging improvements
- Ensuring consistency across server, OpenAPI, client, and command modes

No new architectural features are introduced at this stage.

## 5. Stage Execution Order
1. Stage 1: No-Setting HelloWorld Server
2. Stage 2: Executable HelloWorld API
3. Stage 3: OpenAPI Projection
4. Stage 4: Client and Command Modes
5. Stage 5: First Custom HelloWorld Component Extension
6. Stage 6: Demo Consolidation

The HelloWorld demo is considered complete only after all six stages are finished.

## 6. Non-Goals
- No CML parsing
- No CRUD generation
- No persistence or memory-first logic
- No authentication or authorization
- No UI beyond projections

## 7. Transition to CRUD / CML
The following artifacts are expected to carry forward:
- Subsystem execution boundary
- Component / Service / Operation model
- Projection-based outputs

This strategy validates the demo experience first and then transitions into
CRUD / CML with a stable, reusable execution model.
