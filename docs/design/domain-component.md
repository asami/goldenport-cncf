# DomainComponent Architecture Contract

This document defines the canonical architecture contract for DomainComponent
as the runtime foundation for Cozy-generated domain code in CNCF.

This document is NORMATIVE.
All implementations and generators MUST conform to the contract defined here.

----------------------------------------------------------------------
Purpose
----------------------------------------------------------------------

DomainComponent defines the boundary between pure domain logic and CNCF runtime
responsibilities. It is a long-lived architectural contract for Cozy-generated
code and hand-written domain extensions.

----------------------------------------------------------------------
Core Principles
----------------------------------------------------------------------

- Component is the sole owner of quality attributes:
  observability, reliability, security, consistency, and execution control.

- DomainComponent contains pure domain logic only.
  It MUST NOT implement quality attributes or execution concerns.

- Domain logic is expressed using:
  - Consequence for semantic validity and domain correctness.
  - UnitOfWork for declaring state changes and domain events.

- ActionCall provides a protected Scala DSL that domain logic uses to access
  execution services. Domain logic MUST NOT access ExecutionContext directly.

----------------------------------------------------------------------
Execution Boundary
----------------------------------------------------------------------

- ActionCall is the execution boundary between CNCF and domain logic.
- ActionCall is created and executed by Component-owned runtime flows.
- DomainComponent receives ActionCall and performs domain logic only.

----------------------------------------------------------------------
Domain Modeling Target
----------------------------------------------------------------------

- DomainEntity CRUD is the first concrete prototype and the primary
  Cozy auto-generation target.
- CRUD operations follow the Consequence + UnitOfWork model.

----------------------------------------------------------------------
Non-Goals
----------------------------------------------------------------------

- DomainComponent does not own transport (HTTP/CLI/MCP).
- DomainComponent does not own scheduling or Job lifecycle.
- DomainComponent does not manage observability or security policies.
