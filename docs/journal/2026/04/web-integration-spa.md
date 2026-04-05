CNCF Web Platform Specification for SPA (Draft)
===============================================

status=draft
category=web / spa / platform

Overview
--------

This specification defines the CNCF Web platform targeting
Single Page Applications (SPA).

The goal is to provide:

- a secure hosting environment for SPA
- a unified REST API for application control
- seamless integration with application and domain tiers
- elimination of API stub layers

Frontend frameworks are not restricted.


1. Architecture Overview
------------------------

The system consists of three tiers:

[SPA (Browser)]
        ↓
[Web Tier (CNCF)]
        ↓
[Application Tier]
        ↓
[Domain Tier]

Key principle:

SPA directly invokes CNCF operations via REST API.


2. SPA Hosting
---------------

### 2.1 Static Hosting

The Web tier provides static hosting for SPA assets:

- HTML
- JavaScript
- CSS
- images

### 2.2 URL Structure

- /app/{appName}/...
- /app/{appName}/index.html

### 2.3 Routing Support

- SPA fallback to index.html
- client-side routing supported

### 2.4 Multiple Applications

Multiple SPA applications can coexist:

- /app/admin/
- /app/customer/


3. REST API Model
------------------

### 3.1 Operation-Based API

All APIs follow:

POST /{component}/{service}/{operation}

Example:

POST /user-account/user/create

### 3.2 Request Format

```json
{
  "input": {
    ...
  }
}
```

or simplified:

```json
{
  ...
}
```

### 3.3 Response Format

Success:

```json
{
  "result": {
    ...
  }
}
```

Error:

```json
{
  "error": {
    "code": "...",
    "message": "...",
    "detail": {}
  }
}
```

### 3.4 Async Execution

```json
{
  "jobId": "...",
  "status": "accepted"
}
```

### 3.5 Job API

GET /job/{jobId}
GET /job/{jobId}/result


4. API Exposure Control
------------------------

Not all operations are exposed to SPA.

### 4.1 Exposure Policy

Each operation must be explicitly marked:

- public (accessible from Web)
- internal (not exposed)

### 4.2 Default Behavior

- operations are NOT exposed by default

### 4.3 Scope

Exposure can be defined at:

- component level
- service level
- operation level


5. Authentication and Authorization
-----------------------------------

### 5.1 Authentication

Supported methods:

- session-based (browser)
- bearer token (API)

### 5.2 User Context

Authenticated user context is automatically injected into:

- ExecutionContext
- Operation parameters (if needed)

### 5.3 Authorization

Access control based on:

- role
- scope
- component/service/operation


6. Security Boundary
--------------------

The Web tier enforces:

- API exposure filtering
- authentication
- input validation (schema)

SPA cannot bypass this boundary.


7. Form API (Optional Layer)
-----------------------------

Form API provides:

- schema-based form definition
- validation rules
- UI hints

Use cases:

- rapid prototyping
- admin UI
- internal tools

Form API is optional for SPA applications.


8. JavaScript Integration
--------------------------

SPA interacts with CNCF via REST.

### 8.1 Direct Fetch

```javascript
await fetch("/user-account/user/create", {
  method: "POST",
  body: JSON.stringify(input)
});
```

### 8.2 SDK (Optional)

```javascript
await cncf.call("userAccount.user.create", input);
```

### 8.3 Selector Model

component.service.operation

Unified across:

- CLI
- REST
- JS


9. Observability Integration
----------------------------

SPA-triggered operations are observable via:

- TraceTree
- logs
- metrics

Correlation:

- request → operation → job → event


10. Performance Considerations
------------------------------

- low-latency REST calls
- async offloading via Job
- batching (future)
- caching (future)


11. Deployment Model
---------------------

### 11.1 Unified Deployment

SPA and CNCF runtime deployed together

### 11.2 Distributed Deployment

- Web tier
- Application tier (possibly FaaS)
- Domain tier

CNCF abstracts communication.


12. CNCF-Specific Characteristics
---------------------------------

- operation-centric API
- no API stub layer
- unified selector model
- built-in async (Job)
- schema-driven API
- strong security boundary


Conclusion
----------

This specification defines CNCF as a Web-native application platform
optimized for SPA-based architectures.

It enables:

- direct frontend-to-operation integration
- elimination of redundant layers
- secure and consistent API exposure
- seamless multi-tier application development
