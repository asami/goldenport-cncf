CNCF Form API Specification (Draft)
===================================

status=draft
category=web / api / form

Overview
--------

This specification defines the Form API in CNCF.

The Form API provides a schema-driven interface for:

- generating input forms
- performing validation
- assisting UI development

It is designed as an optional layer on top of REST APIs,
primarily for browser-based applications and developer productivity.

Form API is NOT a replacement for REST API,
but a complementary interface.


1. Design Principles
--------------------

### 1.1 Schema-Driven

Form API is derived from:

- operation input schema
- validation rules
- metadata

### 1.2 UI-Agnostic

- does not enforce UI framework
- provides structure, not rendering

### 1.3 REST-Based Execution

- actual execution is always performed via REST API
- Form API prepares input, not execute business logic

### 1.4 Optional Layer

- SPA may use Form API or ignore it
- REST API remains the primary interface

### 1.5 Web Tier Only

Form API is provided only through Web Tier:

- not available for internal direct calls


2. API Endpoints
----------------

### 2.1 Form Definition

Retrieve form definition for an operation:

```http
GET /{component}/{service}/{operation}/form
```

---

### 2.2 Form Validation

Validate input without execution:

```http
POST /{component}/{service}/{operation}/form/validate
```

---

### 2.3 Optional Combined Mode (Future)

```http
POST /{component}/{service}/{operation}/form/submit
```

→ validate + execute (optional feature)


3. Form Definition Model
------------------------

Example:

```json
{
  "result": {
    "form": {
      "fields": [
        {
          "name": "username",
          "type": "string",
          "required": true,
          "label": "User Name",
          "constraints": {
            "minLength": 3,
            "maxLength": 50
          }
        },
        {
          "name": "email",
          "type": "string",
          "format": "email",
          "required": true,
          "label": "Email Address"
        }
      ]
    }
  }
}
```

---

### 3.1 Field Properties

Each field may include:

- name
- type
- required
- label
- defaultValue
- constraints
- format
- description

---

### 3.2 Type System

Basic types:

- string
- number
- boolean
- object
- array

Extended:

- enum
- date
- email
- custom types (from schema)

---

### 3.3 Constraints

Examples:

- min / max
- minLength / maxLength
- pattern
- enum values

---

### 3.4 Layout Hints (Optional)

```json
{
  "ui": {
    "group": "basic",
    "order": 1
  }
}
```

→ purely advisory


4. Validation Model
-------------------

### 4.1 Request

```json
{
  "input": {
    ...
  }
}
```

---

### 4.2 Response (Success)

```json
{
  "result": {
    "valid": true
  }
}
```

---

### 4.3 Response (Validation Error)

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "detail": {
      "fields": [
        {
          "name": "email",
          "code": "FORMAT",
          "message": "Invalid email format"
        }
      ]
    }
  }
}
```

---

### 4.4 Consistency with REST

Validation error structure is identical to REST API.


5. Relationship with REST API
-----------------------------

```text
Form API = input preparation layer
REST API = execution layer
```

Flow:

1. fetch form schema
2. render UI
3. validate input (optional)
4. call REST API

---

6. JavaScript Integration
--------------------------

Typical usage:

```javascript
const form = await cncf.form("userAccount.user.create");

renderForm(form);

const validation = await cncf.validate("userAccount.user.create", input);

if (validation.valid) {
  await cncf.call("userAccount.user.create", input);
}
```

---

7. Security Model
-----------------

Form API follows Web Tier rules:

- requires exposure (protected or public)
- respects authentication
- respects authorization

Form API does NOT expose internal operations.


8. CML Integration (Draft)
--------------------------

Form behavior is derived from CML:

```
operation createUser {
  input UserInput
  exposure = protected
}
```

Optional extensions:

```
operation createUser {
  input UserInput

  form {
    label = "Create User"
    field username {
      label = "User Name"
    }
  }
}
```

---

9. Use Cases
------------

### 9.1 Admin UI

- rapid CRUD interface
- minimal frontend code

### 9.2 Internal Tools

- debugging tools
- manual operation execution

### 9.3 Prototyping

- early-stage UI
- schema-driven development

---

10. Non-Goals
-------------

Form API does NOT:

- replace frontend frameworks
- define UI rendering
- manage application state

---

11. Future Extensions
---------------------

- dynamic form (conditional fields)
- internationalization (i18n)
- UI component hints (dropdown, date picker)
- schema versioning
- auto-generated admin console


12. CNCF-Specific Characteristics
---------------------------------

- derived from operation schema
- consistent with validation model
- integrated with exposure control
- integrated with authentication
- aligned with REST API


Conclusion
----------

The CNCF Form API provides a schema-driven layer
for improving frontend development experience.

It enables:

- rapid UI construction
- consistent validation
- reduced duplication

while preserving the core architecture:

REST API as the execution backbone.
