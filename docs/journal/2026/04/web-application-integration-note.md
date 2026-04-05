CNCF Web Layer and Application Integration (Journal)
====================================================

status=journal
published_at=2026-04-06
category=web / architecture / cncf

Overview
--------

This entry summarizes the current design direction of CNCF regarding
web capabilities and application integration.

The goal is to extend CNCF from an execution platform into a
full-stack integration platform that seamlessly connects:

- frontend applications
- application logic
- domain logic

while maintaining strong architectural consistency and minimizing
redundant development effort.


1. Position of the Web Layer
----------------------------

The Web layer in CNCF is not treated as a traditional presentation layer.

Instead, it is defined as an integration and boundary layer that:

- exposes CNCF operations as APIs
- hosts web applications
- enforces security and validation
- bridges frontend and backend without intermediate layers

This shifts the role of the web layer from "UI rendering"
to "system integration surface".


2. Elimination of API Stub Layer
--------------------------------

A key design decision is the elimination of the traditional API stub layer.

Conventional architecture:

Frontend → API Stub → Application → Domain

CNCF architecture:

Frontend → CNCF Operation API → Application / Domain

This removes:

- client stub generation
- duplicated DTO definitions
- manual endpoint mapping

and establishes operations as the single source of truth for APIs.


3. Operation-Centric API Model
------------------------------

All APIs are defined based on the CNCF operation model:

component.service.operation

Example:

POST /user-account/user/create

This model differs from conventional REST:

- resource-oriented → replaced by operation-oriented
- CRUD → replaced by explicit use-case execution

This aligns naturally with CQRS and CNCF's execution model.


4. Dual API Model: REST and Form
--------------------------------

CNCF provides two complementary API styles:

### 4.1 REST API

- Primary interface for JS and external systems
- JSON-based invocation
- Direct mapping to operations

### 4.2 Form API

- UI-oriented interface
- Generated from schema
- Provides validation and input structure

The relationship is:

Form API → built on top of REST API  
REST API → execution layer

This enables both:

- rapid UI development
- full flexibility for custom frontend logic


5. JavaScript Integration
-------------------------

JavaScript applications directly invoke CNCF operations via REST.

Example:

fetch("/user-account/user/create", { ... })

or via a thin SDK:

cncf.call("userAccount.user.create", { ... })

Key principles:

- no generated client required
- selector-based invocation
- unified interface across CLI, REST, and JS

This significantly reduces frontend-backend integration cost.


6. Multi-Tier Architecture
--------------------------

CNCF organizes applications into three logical tiers:

### 6.1 Web Tier

- hosts web applications
- exposes REST and Form APIs
- handles authentication and validation

### 6.2 Application Tier

- implements use cases
- orchestrates domain logic
- may run as component or cloud function

### 6.3 Domain Tier

- contains domain model
- entities, aggregates, rules

These tiers are connected seamlessly through CNCF,
without manual wiring or API duplication.


7. CML-Driven Bootstrapping
---------------------------

CML acts as the single source of truth.

From CML, CNCF can derive:

- domain structures
- application operations
- web API exposure
- schema definitions

This enables automatic configuration of:

- Web tier
- Application tier
- Domain tier

and ensures consistency across all layers.


8. Web Application Container
----------------------------

CNCF provides a Web Application Container with the following role:

- hosting frontend applications (SPA, static assets)
- providing secure access to CNCF APIs
- enabling unified deployment with components

Important design choice:

- frontend technology is not restricted
- CNCF does not act as a frontend framework

This preserves flexibility while providing integration capabilities.


9. Security Model
-----------------

The Web layer acts as a security boundary.

Key responsibilities:

- authentication (token/session)
- API exposure control
- input validation (schema-driven)

Only explicitly exposed operations are accessible from the Web.

10. Web Tier as Controlled Gateway (Optional)
---------------------------------------------

The Web Tier is positioned as an optional but powerful control layer.

It is NOT required for all communication, but is introduced when
advanced requirements are needed.

### 10.1 Role

The Web Tier acts as:

- a controlled API gateway
- a security boundary
- a reliability and traffic control layer

### 10.2 When Web Tier is Required

Web Tier MUST be used for:

- browser-originated access (SPA / direct JS)
- public API exposure
- Form API usage

Web Tier SHOULD be used for:

- authentication and authorization enforcement
- rate limiting and traffic shaping
- retry, timeout, and circuit breaking
- observability integration (trace/log correlation)

### 10.3 When Web Tier Can Be Skipped

Web Tier can be skipped for trusted server-side communication:

- external web application servers (Spring, Play, Express)
- internal services within the same trust boundary

In this case, the Application Tier is invoked directly.

### 10.4 Responsibilities

Web Tier provides:

- API exposure control
- authentication and authorization
- rate limiting and traffic shaping
- retry and fault tolerance
- request validation (schema-driven)
- observability integration (trace/log)
- Form API

### 10.5 Non-Responsibilities

Web Tier does NOT provide:

- web application routing
- HTML rendering
- MVC or templating framework features

### 10.6 Architectural Position

The system can be viewed as:

- Application Tier = execution and business logic
- Web Tier = controlled external boundary

This separation ensures flexibility while maintaining strong governance.


11. Operational Web Capabilities
-------------------------------

In addition to application integration,
CNCF provides operational web features:

### 11.1 Dashboard

- metrics visualization (latency, throughput, errors)
- component and operation overview
- job and queue status

### 11.2 Management Console

- operation execution UI
- job management (retry, cancel)
- configuration management

### 11.3 Manual

- help (meta.help)
- describe (meta.describe)
- schema and OpenAPI
- FAQ and troubleshooting

These features provide a unified operational interface.


12. Observability and Debugging
-------------------------------

CNCF emphasizes trace-based observability.

Key capabilities:

- TraceTree (execution flow visualization)
- ActionCall hierarchy
- event propagation
- job lifecycle tracking

This supports:

- performance tuning
- root cause analysis
- reproducibility

Integration examples:

Metrics → Trace → Bottleneck identification  
Error → Trace → Failure point detection  
Queue → System state → Throughput analysis


13. Performance Tuning
----------------------

CNCF provides built-in support for performance analysis:

- slow operation detection
- latency breakdown per step
- load monitoring (requests, concurrency, queue)

Future direction includes:

- optimization suggestions
- automated bottleneck detection


14. Key Architectural Characteristics
-------------------------------------

The CNCF web architecture is characterized by:

- operation-centric API model
- elimination of API stub layer
- unified REST and Form APIs
- CML-driven multi-tier configuration
- direct frontend-to-operation integration
- strong observability through trace
- secure and controlled API exposure


Conclusion
----------

The introduction of the Web layer transforms CNCF into a unified platform:

- execution platform (core runtime)
- integration platform (API and web layer)
- operational platform (dashboard and console)
- application platform (web application container)

This architecture reduces development cost,
improves consistency, and enables seamless integration
across all layers of modern applications.
