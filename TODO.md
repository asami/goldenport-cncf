# TODO — Cloud Native Component Framework

This document tracks planned work items for the Cloud Native Component Framework (CNCF).
Items are grouped roughly from **core runtime** to **integration and documentation**.

The framework’s primary goal is to provide a stable, operable runtime for
Cozy-generated components based on Event-Centered Architecture.

---

## 1. Core Runtime Foundations

### 1.1 Component / Componentlet Runtime

- [ ] Define minimal `Component` lifecycle interface
- [ ] Define minimal `Componentlet` execution interface
- [ ] Clarify ownership of configuration loading
- [ ] Define replacement / hot-swap strategy for generated Componentlets
- [ ] Establish conventions for generated vs handwritten code boundaries

---

### 1.2 OperationCall

- [x] Define minimal `OperationCall` trait
- [x] Support synchronous execution
- [ ] Support asynchronous execution
- [x] Bind `ExecutionContext` explicitly to OperationCall
- [x] Integrate `Consequence / Conclusion` error model
- [ ] Define extension points for cross-cutting concerns
  - logging
  - tracing
  - metrics
  - retry / timeout
- [x] Separate authorization (pre-execution) from operation observability
- [x] Remove implicit ExecutionContext from execution path
- [ ] Introduce OperationCallFeaturePart for helper-based DSL
  - define OperationCallFeaturePart base trait
  - define feature-specific mixins (Time / Security / Random / Config / Store)
  - restrict OperationCall implementations to protected final helpers
  - adopt <prefix>_<functionality> naming convention
  - ensure helpers delegate only to ExecutionContext

---

### 1.3 ExecutionContext

- [x] Define `ExecutionContext` data model
- [ ] Include locale / time / clock abstraction
- [x] Include user / session identifiers
- [ ] Include rule sets (Home / Project / Session)
- [ ] Integrate OpenTelemetry trace/span identifiers
- [ ] Define propagation rules across async boundaries
- [x] Bind ExecutionContext to OperationCall at creation time
- [ ] Clarify boundary between ExecutionContext and RuntimeContext

### 1.4 Engine Execution Boundary

- [x] Define Engine as pure execution boundary
- [x] Separate authorization failure from operation failure semantics
- [x] Ensure observability reflects true operation lifecycle
- [ ] Add authorization enforcement (deny => failure)
- [ ] Integrate authorization decision into audit / trace

---

## 2. Event-Centered Architecture

### 2.1 Command / Event Model

- [ ] Define base `Command` abstraction (tight semantics)
- [ ] Define base `Event` abstraction (loose semantics)
- [ ] Clarify semantic vs transport separation
- [ ] Support Pub/Sub transport for both Command and Event
- [ ] Define naming and versioning conventions

---

### 2.2 Job Management

- [ ] Define `Job` domain model
- [ ] Define Job lifecycle states
  - pending
  - running
  - completed
  - failed
- [ ] Define retry / compensation hooks
- [ ] Define cancellation and suspension semantics
- [ ] Define relationship between Job and OTel trace/span

> Note: Job management is not yet fully implemented in code.
> This section defines the target design and execution model.

---

### 2.3 Failure Handling and Recovery

- [ ] Record all Command / Event execution results as Job outcomes
- [ ] Ensure failures are never silently dropped
- [ ] Define application-driven recovery patterns
- [ ] Define operator-driven manual recovery flows
- [ ] Document operational recovery guidelines

---

## 3. Job Visualization and Control API

- [ ] Define Job query API
- [ ] Support filtering by status / time / component
- [ ] Support retry / re-execution operations
- [ ] Support cancel / suspend / resume operations
- [ ] Define audit log and history retention strategy
- [ ] Consider minimal UI / CLI tooling

---

## 4. Synchronous Operations

- [ ] Support synchronous operations without Job management (default)
- [ ] Support opt-in Job-managed synchronous execution
- [ ] Define defaults for mission-critical update operations
- [ ] Clarify performance vs observability trade-offs

---

## 5. Cozy Integration

### 5.1 Modeling

- [ ] Introduce `Command` and `Event` as first-class Cozy model elements
- [ ] Align Cozy stereotypes with runtime semantics
- [ ] Define mapping rules from Cozy models to runtime artifacts

---

### 5.2 Code Generation

- [ ] Generate Command / Event classes
- [ ] Generate Component / Componentlet scaffolding
- [ ] Generate OperationCall bindings
- [ ] Ensure generated code is replaceable and regenerable
- [ ] Document safe customization points

---

## 6. Cloud-Native Integration

- [ ] Container-friendly runtime packaging
- [ ] Support local / demo / production execution modes
- [ ] Integrate with external message brokers
- [ ] Integrate with OpenTelemetry collectors
- [ ] Define deployment reference architectures

---

## 7. AI and Tooling Integration

- [ ] Expose runtime metadata for AI agents
- [ ] Enable introspection of available Commands / Events
- [ ] Support MCP or CLI-based control flows
- [ ] Align with Semantic Integration Engine (SIE)
- [ ] Define knowledge feedback loop from execution results

---

## 8. Documentation

- [ ] Split README into focused architecture documents
- [ ] Create `docs/architecture.md`
- [ ] Document Event-Centered Architecture in detail
- [ ] Provide minimal runnable examples
- [ ] Provide Cozy model examples
- [ ] Provide operational and recovery guides

---

## 9. Governance and Project Hygiene

- [ ] Add LICENSE files (Apache 2.0 / CC-BY-SA 4.0)
- [ ] Add CONTRIBUTING.md
- [ ] Add CLA.md
- [ ] Define versioning and release policy
- [ ] Establish backward compatibility guidelines

---

## 10. Refactoring and Cleanup

- [ ] Review existing implementations of Command / Event / OperationCall
- [ ] Consolidate or remove experimental variants (e.g. UnitOfWork2)
- [ ] Mark exploratory code explicitly as experimental where appropriate
- [ ] Remove editor backup files (`*~`) from the repository
- [ ] Define a deprecation policy for exploratory APIs

---

## Notes

This TODO list intentionally focuses on **runtime correctness,
operability, and conceptual clarity** rather than feature count.

The framework should remain small, composable, and model-driven.