CNCF Authentication and Authorization Specification (Draft)
==========================================================

status=draft
category=web / security / auth

Overview
--------

This specification defines authentication and authorization
for CNCF Web integration.

The goal is to:

- secure access to exposed operations
- unify identity across tiers
- support both browser-based and server-based access
- integrate with API exposure control

Authentication is handled at the Web Tier,
while authorization is applied based on operation definitions.


1. Design Principles
--------------------

### 1.1 Web Tier as Security Boundary

Authentication and authorization are enforced at the Web Tier.

Application Tier assumes trusted input.

### 1.2 Identity Propagation

Authenticated identity is propagated to:

- ExecutionContext
- operations
- domain logic (if needed)

### 1.3 Operation-Centric Authorization

Authorization is defined per:

component.service.operation

### 1.4 Integration with Exposure Control

- public → no authentication required (optional)
- protected → authentication required
- internal → not accessible from Web


2. Authentication Model (Refactored)
------------------------------------

CNCF distinguishes between two types of authentication:

- End-User Authentication (human identity)
- Service Authentication (machine identity)

Both may coexist in a single request.

User identity is optional,
but service identity is required for external access.

---

### 2.1 End-User Authentication

Purpose:

- identify human users
- support browser-based and interactive systems

Mechanisms:

- session-based (cookie)
- token-based (JWT)

Characteristics:

- produces UserContext
- used for authorization decisions
- typically originates from browser or UI layer

---

### 2.2 Service Authentication

Purpose:

- establish trust between systems
- secure service-to-service communication

Mechanisms:

- bearer token (JWT or opaque token)
- API key (future)
- mTLS (future)

Characteristics:

- produces ServiceContext
- required for external system access
- independent from user identity

---

### 2.3 Internal Trusted Calls

- no authentication required
- used within trusted boundary

Examples:

- Application Tier → Domain Tier
- server-side Web app → Application Tier

---

3. Identity Context
-------------------

CNCF maintains two types of identity context:

### 3.1 UserContext

Represents human user identity.

```
UserContext {
  userId: String
  roles: List[String]
  scopes: List[String]
  attributes: Map[String, Any]
}
```

---

### 3.2 ServiceContext

Represents calling system identity.

```
ServiceContext {
  serviceId: String
  permissions: List[String]
}
```

---

### 3.3 ExecutionContext Integration

ExecutionContext may contain:

```
ExecutionContext {
  userContext?: UserContext
  serviceContext: ServiceContext
}
```

- userContext is optional
- serviceContext is required for external access

---

4. Authorization Model
----------------------

Authorization is applied to protected operations.


### 4.1 Role-Based Access Control (RBAC)

Operations may define required roles:

```
operation createUser {
  exposure = protected
  roles = ["admin"]
}
```

---

### 4.2 Scope-Based Access Control

Operations may define required scopes:

```
operation createOrder {
  exposure = protected
  scopes = ["order:write"]
}
```

---

### 4.3 Evaluation

Authorization is evaluated as:

- role match OR scope match (configurable)

Failure results in:

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Access denied"
  }
}
```


5. Authentication Flow (Refactored)
-----------------------------------

### 5.1 Browser Flow (End-User)

1. User logs in
2. Web Tier establishes session or token
3. UserContext is created
4. ServiceContext is implicitly assigned (Web Tier identity)
5. ExecutionContext is constructed

---

### 5.2 Token Flow (Service)

1. Client obtains token
2. Client sends token in Authorization header
3. Web Tier validates token
4. ServiceContext is created
5. UserContext may or may not be present
6. ExecutionContext is constructed

---

6. Integration with Web Tier
----------------------------

Web Tier is responsible for:

- authentication validation
- token/session parsing
- user context creation
- authorization enforcement

Application Tier receives:

- already authenticated requests


7. Multi-Tier Identity Propagation
----------------------------------

Both identity contexts are propagated across tiers:

Web Tier → Application Tier → Domain Tier

```
ExecutionContext {
  userContext?: ...
  serviceContext: ...
}
```

This enables:

- audit logging (user + service)
- business rule enforcement
- traceability across systems


8. Error Handling
-----------------

Standard error responses:

### 8.1 Authentication Failure

```json
{
  "error": {
    "code": "UNAUTHENTICATED",
    "message": "Authentication required"
  }
}
```

---

### 8.2 Authorization Failure

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Access denied"
  }
}
```


9. Security Considerations
--------------------------

- HTTPS required
- secure cookie settings
- token expiration and rotation
- protection against CSRF (for session)
- CORS configuration


10. Future Extensions
---------------------

- OAuth2 / OpenID Connect integration
- multi-tenant identity
- attribute-based access control (ABAC)
- dynamic policy evaluation
- external identity providers


Conclusion
----------

The CNCF authentication and authorization model:

- centralizes security at Web Tier
- integrates with operation-based API
- supports both browser and API clients
- enables fine-grained control via CML

This provides a secure and flexible foundation
for modern web and API applications.
