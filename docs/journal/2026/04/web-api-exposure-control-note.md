CNCF API Exposure Control Specification (Draft)
==============================================

status=draft
category=web / security / api

Overview
--------

This specification defines how CNCF controls the exposure of operations
to external clients, especially through the Web Tier.

The goal is to:

- ensure secure API exposure
- prevent unintended access to internal operations
- provide fine-grained control over accessibility
- integrate with authentication and authorization mechanisms

By default, CNCF follows a deny-by-default model.


1. Design Principles
--------------------

### 1.1 Deny by Default

Operations are NOT exposed unless explicitly declared.

### 1.2 Operation-Centric Control

Exposure is defined at the operation level,
aligned with the CNCF operation model:

component.service.operation

### 1.3 Explicit Exposure

Only explicitly exposed operations are accessible from the Web Tier.

### 1.4 Separation of Internal and External APIs

- internal operations are callable only within trusted boundaries
- public operations are accessible via Web Tier


2. Exposure Levels
------------------

CNCF defines three exposure levels:

### 2.1 internal

- default level
- not accessible from Web Tier
- accessible only within trusted environment

### 2.2 protected

- accessible via Web Tier
- requires authentication
- may require authorization (role/scope)

### 2.3 public

- accessible via Web Tier
- no authentication required (optional)
- intended for open APIs


3. Exposure Scope
-----------------

Exposure can be defined at multiple levels:

### 3.1 Component Level

Applies to all services and operations within a component.

### 3.2 Service Level

Applies to all operations within a service.

### 3.3 Operation Level

Most precise control.

Example:

- component: internal
- service: protected
- operation: public

Resolution priority:

operation > service > component


4. CML Definition (Draft)
-------------------------

Exposure is defined in CML.

Example:

```
component UserAccount {
  exposure = internal

  service User {
    exposure = protected

    operation create {
      exposure = public
    }

    operation delete {
      exposure = protected
    }
  }
}
```

This results in:

- User.create → public
- User.delete → protected
- others → internal


5. Web Tier Enforcement
-----------------------

The Web Tier enforces exposure rules.

### 5.1 Request Filtering

- incoming requests are checked against exposure rules
- non-exposed operations are rejected

### 5.2 Error Response

Example:

```json
{
  "error": {
    "code": "NOT_EXPOSED",
    "message": "Operation is not accessible from Web"
  }
}
```


6. Authentication Integration
-----------------------------

Exposure works together with authentication.

| Exposure  | Authentication Required |
|----------|------------------------|
| public   | optional               |
| protected| required               |
| internal | not accessible         |

Authenticated user context is injected into ExecutionContext.


7. Authorization Integration
----------------------------

Protected operations may define access control:

- role-based (RBAC)
- scope-based (OAuth style)

Example (future):

```
operation create {
  exposure = protected
  roles = ["admin", "manager"]
}
```


8. Trusted vs Untrusted Access
------------------------------

### 8.1 Trusted Access

- server-side applications
- internal CNCF components

→ can invoke internal operations directly

### 8.2 Untrusted Access

- browser (SPA)
- external clients

→ must go through Web Tier  
→ exposure rules enforced


9. Relationship with Web Tier
-----------------------------

Exposure control is enforced ONLY at Web Tier.

Application Tier does not enforce exposure rules.

This allows:

- high performance internal calls
- strict external control


10. Developer Experience
------------------------

### 10.1 Explicit API Design

Developers must explicitly decide:

- which operations are public
- which are protected
- which remain internal

### 10.2 No Stub Required

Exposed operations are directly callable:

- REST
- JS SDK
- CLI

No additional API definition needed.


11. Future Extensions
---------------------

- dynamic exposure (environment-based)
- rate limit per exposure level
- audit logging
- API versioning
- tenant-based exposure


Conclusion
----------

API Exposure Control ensures that CNCF remains:

- secure by default
- explicit in API design
- flexible in deployment
- consistent across all access paths

It is a foundational mechanism for Web Tier and JavaScript integration.
