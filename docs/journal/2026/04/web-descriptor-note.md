CNCF Web Descriptor Specification (Draft)
=========================================

status=draft
category=web / descriptor

Overview
--------

This specification defines the Web Descriptor used to configure
Web Tier behavior in CNCF.

The descriptor is separate from CML and defines:

- API exposure
- authentication and authorization
- Form API behavior
- Management Console behavior
- traffic control
- application hosting

This enables flexible and environment-specific configuration.


1. Design Principles
--------------------

- separation from domain model (CML)
- environment-specific configuration
- declarative structure
- override-friendly


2. Structure
------------

Example:

```yaml
web:
  expose:
    userAccount.user.create: protected
    userAccount.user.delete: internal

  auth:
    mode: session

  authorization:
    userAccount.user.create:
      roles: ["admin"]

  form:
    userAccount.user.create:
      enabled: true

  admin:
    entity.user:
      totalCount: optional

  rateLimit:
    userAccount.user.create:
      rps: 10

  apps:
    - name: admin
      path: /app/admin
```

---

3. Exposure
------------

Defines visibility of operations:

- public
- protected
- internal


4. Authentication
-----------------

Defines authentication mode:

- session
- token
- hybrid


5. Authorization
----------------

Defines access rules:

- roles
- scopes


6. Form API
-----------

Controls Form API availability:

- enable/disable per operation


7. Traffic Control
------------------

Defines:

- rate limiting
- throttling


8. Application Hosting
----------------------

Defines SPA hosting:

- app name
- path
- asset location


9. Override Model
-----------------

Descriptors can be layered:

- base
- environment (dev/prod)
- local override


Conclusion
----------

The Web Descriptor separates operational concerns from domain modeling,
enabling flexible, secure, and environment-aware configuration.
