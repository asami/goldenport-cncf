# CML → CRUD Domain Subsystem Bootstrap (Minimal Pipeline)

Goal:
- From a single CML (Cozy Modeling Language) file
- Bootstrap a CRUD-scoped Domain Subsystem
- With no handwritten code, and get it running immediately

Assumptions:
- Memory-First is NOT applied initially
  (only boundaries that allow later replacement are prepared)
- The focus is on bootstrapping a Domain Tier Subsystem as fast as possible
  (UI / Application tiers may be added later)

Prerequisite

This document assumes that the HelloWorld Bootstrap
has already been completed.

Specifically, it relies on the following baseline being in place:

- CNCF can start with `cncf run`
- A default Subsystem boots successfully
- AdminComponent is available
- WebInterfaceComponent is available
- OpenAPI is exposed at `/openapi.json` and viewable via `/api`

The steps described here focus exclusively on:
- Adding domain behavior
- Introducing CML-based model input
- Bootstrapping CRUD projections

They do not re-establish
startup, lifecycle, admin, or web infrastructure.

See: docs/notes/helloworld-bootstrap.md

============================================================
0. Definition of Done (MVP Criteria)
============================================================

The MVP is considered complete when:

1) A Domain Subsystem can be started from a single CML file
2) The subsystem successfully boots and logs startup completion
3) A minimal CRUD API is available:
   - create / get / update / delete / list
4) Persistence is in-memory only at first
   (file or durable persistence comes later)

Example command:
- cncf run --cml ./model/order.cml

Expected output:
- "[INFO] OrderDomainSubsystem started"

Example APIs:
- POST /orders
- GET  /orders/{id}
- ...

============================================================
1. Minimal Architecture Overview
============================================================

CML (input)
  ↓ parse
DomainModel (AST)
  ↓ compile
SubsystemModel (tier=domain, kind=service, components/capabilities)
  ↓ build
CrudDomainRuntime (default runtime)
  ↓ run
HTTP Adapter (optional) / CLI Adapter (minimal)
  ↓ persist
Repository (InMemory first)

Key principles:
- No branching logic based on raw configuration values
- Always normalize inputs into a model before building runtime objects

============================================================
2. Minimal AST Model Definition
============================================================

Minimum information extracted from CML:

DomainModel
- namespace (optional)
- entities: List[EntityModel]

EntityModel (minimum required for CRUD):
- name: String
- id: IdSpec (type + generation policy)
- attributes: List[AttributeSpec]
- indexes: List[IndexSpec] (optional in MVP)
- constraints: List[ConstraintSpec] (only "required" is needed initially)

AttributeSpec:
- name: String
- datatype: String (string / int / decimal / bool / datetime / ...)
- required: Boolean
- default: Option[String]
- isIdentifierRef: Boolean (for external references, later use)

State machines and resource/task classification are NOT required at this stage.

============================================================
3. SubsystemModel Generation Rules (Minimal)
============================================================

A Domain Subsystem is mechanically generated from the CML DomainModel.

Default SubsystemModel:
- tier = domain
- kind = service
- components = one implicit CRUD component per entity
- capabilities:
  - datastore (required)
    - satisfied by a built-in InMemory implementation in the MVP
  - event_bus (optional)
    - satisfied by a Noop implementation in the MVP

Notes:
- A handwritten Subsystem DSL is NOT required initially
- SubsystemModel must be kept independent so it can later be merged
  with manually written subsystem definitions

============================================================
4. CRUD Domain Runtime (Minimal Design)
============================================================

CrudDomainRuntime generates CRUD handlers from a set of EntityModels.

Required interfaces (minimal):

DomainRuntime:
- start(): Unit
- stop(): Unit
- handle(request: DomainRequest): DomainResponse

DomainRequest (shared by HTTP / CLI adapters):
- entity: String
- op: create | get | update | delete | list
- id: Option[String]
- payload: Map[String, Any]   // JSON-like structure

DomainResponse:
- status: ok | not_found | validation_error | conflict | error
- payload: Option[Json]
- errors: List[String]

Repository abstraction (per entity):
- create(entityName, record): id
- get(entityName, id): record?
- update(entityName, id, record): record?
- delete(entityName, id): Boolean
- list(entityName, query): List[record]

MVP persistence:
- InMemoryRepository:
  Map[EntityName, Map[Id, Record]]

============================================================
5. Minimal API: HTTP Adapter or CLI Adapter
============================================================

For fastest feedback, a CLI adapter is acceptable.
For better usability, an HTTP adapter is preferred.

Example HTTP mapping:
- POST   /{entity}            → create
- GET    /{entity}/{id}       → get
- PUT    /{entity}/{id}       → update
- DELETE /{entity}/{id}       → delete
- GET    /{entity}            → list

Pluralization is ignored in the MVP:
- entity names are used as-is in paths
- normalization can be added later

============================================================
6. Minimal CML Example (CRUD-Oriented)
============================================================

Example: order.cml (pseudo syntax; adapt to actual Cozy/CML grammar)

domain OrderDomain {
  entity Order {
    id: uuid
    attributes:
      customer_id: string required
      total_amount: decimal required
      status: string default("created")
      created_at: datetime
      updated_at: datetime
  }
}

MVP notes:
- Datatypes may be parsed as strings initially
- required / default semantics must be honored

============================================================
7. Implementation Steps (Shortest Path)
============================================================

Step 1: Minimal CML Parser
- Extract entity names, attributes, id spec, required/default
- Full grammar support is NOT required

Step 2: Build DomainModel AST
- Store parsed results into DomainModel / EntityModel

Step 3: Implement CrudDomainRuntime
- Dispatch CRUD operations per entity
- Apply minimal validation (required/default)

Step 4: Implement InMemoryRepository
- Maintain a Map per entity

Step 5: Provide startup entrypoint
- cncf run --cml ...
- Log startup
- Provide health endpoint (GET /health)

Done:
- "A Domain Subsystem that boots and supports CRUD from CML only"

============================================================
8. Boundaries Reserved for Memory-First Replacement
============================================================

The following boundaries MUST be fixed in the MVP
to enable later Memory-First enhancement:

- DomainRuntime entry points are fixed (start / handle)
- Persistence is accessed only via Repository interfaces
- CRUD logic is delegated to an EntityRuntime abstraction

Replacement points in the next phase:
- InMemoryRepository → DurableRepository (snapshot / events)
- CrudDomainRuntime → MemoryFirstCrudDomainRuntime
  - resource: load all and keep in memory
  - task: load only active instances, evict on completion
  - event handling: taskId lookup → apply

============================================================
9. Minimal Extension Roadmap
============================================================

Recommended post-MVP steps:

A) File-based persistence (JSON snapshots)
- Load on startup
- Flush on update
- Enable restart persistence quickly

B) Integrate tier / kind into the model
- tier=domain may be implicit but should exist in the model

C) Introduce resource / task classification
- Add entity.kind to CML
- Define "active" conditions (state machine or status set)

D) Event bus integration
- Publish Created / Updated / Deleted domain events

============================================================
10. First Decisions to Fix Before Coding
============================================================

Before implementation, only two things must be fixed:

1) The CRUD-minimal subset of CML grammar
   (Entity / Attributes / Id / required / default)
2) The exact shape of DomainRequest
   (op / entity / id / payload)

Once these are fixed, the path to the MVP is straightforward.

---
Recommended next action:
- Align the CRUD-minimal CML grammar with the actual Cozy/CML syntax,
  and prepare 2–3 test CML files for validation.
