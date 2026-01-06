# HelloWorld Bootstrap
## for Cloud Native Component Framework (CNCF)

status = draft
audience = users / platform / demos
scope = cncf-run / subsystem-bootstrap / admin / web / openapi

---

## 1. Purpose

The HelloWorld Bootstrap defines the **minimum executable experience**
of CNCF.

Its goal is to ensure that:

- CNCF can be started with zero configuration
- A Subsystem successfully boots
- Users can confirm runtime status via REST and Web
- API usage is discoverable without reading documentation

This bootstrap phase prioritizes **“it runs safely”**
over domain logic, configuration, or persistence.

---

## 2. User Experience Flow

### Step 0: Run CNCF with no arguments

```bash
cncf run
```

Behavior:
- CNCF starts with a built-in default subsystem
- No configuration file is required
- No domain logic is involved

Startup log example:

```text
[INFO] No subsystem configuration provided.
[INFO] Starting default HelloWorld subsystem.
[INFO] HTTP server started on port 8080.
```

---

### Step 1: Confirm via Web Browser

Open:

```text
http://localhost:8080/
```

The Web interface displays:
- Subsystem name, tier, kind
- Runtime status (OK)
- Uptime and basic statistics
- Links to Admin APIs and OpenAPI documentation
- Instructions for next steps

This page is intended for **human users**.

---

### Step 2: Confirm via REST (Admin API)

```bash
curl http://localhost:8080/admin/ping
```

```json
{ "status": "ok" }
```

Other available endpoints:
- `/admin/meta`
- `/admin/stats`
- `/admin/health`

These APIs are intended for **automation and tooling**.

---

### Step 3: Inspect API Usage (OpenAPI)

```text
http://localhost:8080/api
```

- Displays an OpenAPI viewer (Swagger UI or equivalent)
- Shows all available REST endpoints
- Backed by `/openapi.json`

This page serves as **the authoritative usage guide** for REST APIs.

---

## 3. Default HelloWorld Subsystem

When no configuration is provided, CNCF constructs
the following implicit SubsystemModel:

```text
SubsystemModel(
  name = "hello-world",
  tier = domain,
  kind = service,
  components = []   // AdminComponent and WebInterfaceComponent are implicit
)
```

Characteristics:
- No DomainComponent
- No CML
- No persistence
- No business logic

This subsystem exists purely to validate
startup, lifecycle, and observability.

---

## 4. Components in the Bootstrap Subsystem

### 4.1 AdminComponent (standard)

Provides machine-facing APIs:

- `/admin/ping`
- `/admin/meta`
- `/admin/stats`
- `/admin/health`

Responsibilities:
- Liveness and readiness
- Runtime metadata
- Basic statistics

AdminComponent is implicitly included
in all subsystems.

---

### 4.2 WebInterfaceComponent (standard)

Provides human-facing Web pages:

- `/` : status overview
- `/instructions` : usage guidance
- `/api` : OpenAPI viewer

Responsibilities:
- Visual confirmation that the subsystem is running
- Clear instructions for next steps
- Links to REST and OpenAPI resources

WebInterfaceComponent is also implicitly included.

---

## 5. OpenAPI as the Single Source of Truth

All externally visible APIs exposed during bootstrap
are described by OpenAPI.

Endpoints:
- `/openapi.json` : OpenAPI specification
- `/api` : OpenAPI viewer

Key rule:

> API usage is never described manually.
> It is always derived from the OpenAPI model.

This applies equally to:
- Admin APIs
- Future CRUD projections
- Future domain services

---

## 6. Running with a User-Defined Subsystem

Users may provide their own minimal subsystem definition:

```bash
cncf run hello-subsystem.conf
```

Example configuration:

```hocon
subsystem {
  name = "hello-world"
  tier = domain
  kind = service
}
```

Behavior:
- The default subsystem is replaced
- The same Admin and Web interfaces are provided
- The OpenAPI surface remains visible

This step allows users to confirm
that they can control subsystem definitions safely.

---

## 7. What Is Explicitly NOT Included

The HelloWorld Bootstrap intentionally excludes:

- Domain models
- CML parsing
- CRUD APIs
- Persistence
- Memory-First runtime behavior

These are introduced in subsequent phases
after the bootstrap is validated.

---

## 7.1 Related Design Notes

The HelloWorld Bootstrap intentionally focuses on **Server mode** execution only.

However, CNCF Subsystems are designed to support multiple execution modes
while sharing the same core logic, including:

- Server mode (REST / OpenAPI)
- Command mode (CLI, in-process execution)
- Batch mode (import/export, migration, maintenance)

These additional execution modes are discussed in the following design note:

- [Subsystem Execution Modes](subsystem-execution-modes.md)

The HelloWorld Bootstrap keeps these modes out of scope for simplicity,
but its design assumes that Subsystem logic is transport-independent
and can later be reused in Command or Batch execution contexts.

---

## 8. Transition to the Next Phase

Once the HelloWorld Bootstrap is confirmed,
the next step is:

- Adding a CML file
- Generating CRUD projections
- Bootstrapping a Domain Subsystem with behavior

The bootstrap subsystem provides
the stable foundation for all later extensions.

---

## 9. Summary

> The HelloWorld Bootstrap guarantees that CNCF
> can always start safely, visibly, and understandably.
>
> It establishes the operational baseline upon which
> all domain logic, configuration, and runtime evolution
> can be layered incrementally.
