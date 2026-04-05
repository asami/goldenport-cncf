CNCF REST API Response Specification (Draft)
============================================

status=draft
category=web / api / response

Overview
--------

This specification defines the standard response model for CNCF REST APIs.

The goal is to provide:

- a consistent response envelope
- clear distinction between success, async acceptance, and error
- compatibility with JavaScript applications and external systems
- stable foundations for SDK, Form API, and operational tooling

CNCF APIs are operation-centric rather than resource-centric.
Therefore, responses are designed around operation execution results.


1. Design Principles
--------------------

### 1.1 Consistent Envelope

All REST responses use a predictable structure.

### 1.2 Operation-Centric Semantics

Responses represent the outcome of an operation call.

### 1.3 Explicit Async Model

Async execution is represented explicitly as Job acceptance.

### 1.4 Machine-Friendly and Human-Friendly

Responses should be easy to consume from:

- JavaScript applications
- server-side web applications
- CLI tools
- operational dashboards

### 1.5 Minimal but Extensible

The base format should stay simple,
while allowing future extensions such as tracing metadata,
pagination, and warnings.


2. Response Categories
----------------------

CNCF defines three primary response categories:

- Success
- Accepted (async/job)
- Error


3. Success Response
-------------------

A successful synchronous operation returns:

```json
{
  "result": {
    ...
  }
}
```

### 3.1 Meaning

- operation executed successfully
- business result is contained in `result`

### 3.2 Examples

#### Object result

```json
{
  "result": {
    "id": "u-123",
    "name": "Alice"
  }
}
```

#### Primitive result

```json
{
  "result": "ok"
}
```

#### Boolean result

```json
{
  "result": true
}
```

#### Empty result

```json
{
  "result": null
}
```

### 3.3 HTTP Status

- 200 OK

Optionally:

- 201 Created for creation-oriented operations, if desired by policy

The default policy should prefer:

- 200 OK for successful synchronous operation execution

This keeps the model simple and operation-centric.


4. Accepted Response (Async / Job)
----------------------------------

When an operation is accepted for asynchronous execution:

```json
{
  "job": {
    "id": "job-123",
    "status": "accepted"
  }
}
```

Alternative flattened form:

```json
{
  "jobId": "job-123",
  "status": "accepted"
}
```

### 4.1 Preferred Form

The nested form is preferred:

```json
{
  "job": {
    "id": "...",
    "status": "accepted"
  }
}
```

because it is easier to extend later.

### 4.2 Meaning

- operation request was accepted
- execution continues asynchronously
- result must be retrieved through Job API

### 4.3 HTTP Status

- 202 Accepted

### 4.4 Example

```json
{
  "job": {
    "id": "job-987",
    "status": "accepted"
  }
}
```


5. Error Response
-----------------

Any failure returns a structured error object:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable description",
    "detail": {}
  }
}
```

### 5.1 Fields

#### code

- stable machine-readable identifier

Examples:

- UNAUTHENTICATED
- UNAUTHORIZED
- NOT_EXPOSED
- VALIDATION_ERROR
- NOT_FOUND
- CONFLICT
- TIMEOUT
- INTERNAL_ERROR

#### message

- human-readable summary

#### detail

- optional structured detail
- validation issues
- field-level errors
- debug-safe metadata

### 5.2 Example: authentication error

```json
{
  "error": {
    "code": "UNAUTHENTICATED",
    "message": "Authentication required"
  }
}
```

### 5.3 Example: validation error

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Input validation failed",
    "detail": {
      "fields": [
        {
          "name": "email",
          "message": "Invalid email format"
        }
      ]
    }
  }
}
```

### 5.4 Example: internal error

```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Unexpected server error"
  }
}
```


6. HTTP Status Mapping
----------------------

Suggested standard mapping:

| CNCF Error Code     | HTTP Status |
|---------------------|-------------|
| UNAUTHENTICATED     | 401         |
| UNAUTHORIZED        | 403         |
| NOT_EXPOSED         | 403 or 404  |
| NOT_FOUND           | 404         |
| VALIDATION_ERROR    | 400         |
| CONFLICT            | 409         |
| TIMEOUT             | 504         |
| RATE_LIMITED        | 429         |
| INTERNAL_ERROR      | 500         |

### 6.1 NOT_EXPOSED Policy

Two possible policies exist:

- 403 Forbidden
- 404 Not Found

Recommended default:

- 403 Forbidden

Reason:
this expresses that the endpoint exists conceptually,
but access is not allowed from the current boundary.

### 6.2 Operation Success

Recommended default:

- 200 OK for synchronous success
- 202 Accepted for async acceptance


7. Metadata Envelope (Optional)
-------------------------------

Responses may optionally include metadata:

```json
{
  "result": {
    ...
  },
  "meta": {
    "traceId": "tr-123",
    "timestamp": "2026-04-06T10:00:00Z"
  }
}
```

### 7.1 Purpose

Metadata may include:

- traceId
- timestamp
- warnings
- pagination info
- version

### 7.2 Policy

`meta` is optional and should not be required
for basic client behavior.


8. Validation Error Detail
--------------------------

Validation errors are important for SPA and Form API.

Recommended structure:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Input validation failed",
    "detail": {
      "fields": [
        {
          "name": "username",
          "code": "REQUIRED",
          "message": "username is required"
        },
        {
          "name": "email",
          "code": "FORMAT",
          "message": "email is invalid"
        }
      ]
    }
  }
}
```

This structure supports:

- inline form validation
- UI highlighting
- SDK normalization


9. Job API Response
-------------------

Job-related APIs should also follow the same envelope model.

### 9.1 Job status response

```json
{
  "result": {
    "job": {
      "id": "job-123",
      "status": "running"
    }
  }
}
```

### 9.2 Job result response

When completed successfully:

```json
{
  "result": {
    ...
  }
}
```

When failed:

```json
{
  "error": {
    "code": "JOB_FAILED",
    "message": "Job execution failed",
    "detail": {
      "jobId": "job-123"
    }
  }
}
```

### 9.3 Recommended Job Status Values

- accepted
- queued
- running
- completed
- failed
- cancelled


10. Help / Schema / Manual Responses
------------------------------------

Meta APIs should also follow the standard envelope.

### 10.1 Help

```json
{
  "result": {
    "help": {
      ...
    }
  }
}
```

### 10.2 Schema

```json
{
  "result": {
    "schema": {
      ...
    }
  }
}
```

This keeps the model uniform across all API families.


11. Pagination (Future Extension)
---------------------------------

For list-oriented responses, pagination can be expressed via `meta`:

```json
{
  "result": {
    "items": [ ... ]
  },
  "meta": {
    "page": 1,
    "pageSize": 20,
    "total": 135
  }
}
```

This is a future extension and not required for the initial core model.


12. Trace and Observability Integration
---------------------------------------

When enabled, responses may include trace metadata:

```json
{
  "result": {
    ...
  },
  "meta": {
    "traceId": "tr-abc-123"
  }
}
```

This enables:

- UI debugging
- log correlation
- operational troubleshooting


13. JavaScript SDK Implications
-------------------------------

The SDK can normalize responses as:

- success → return `result`
- async acceptance → return `job`
- error → throw or return normalized error object

Because the response envelope is stable,
SDK implementation remains thin and predictable.


14. CNCF-Specific Characteristics
---------------------------------

This response model reflects CNCF architecture:

- operation-centric execution
- explicit async as Job
- structured validation feedback
- optional trace metadata
- unified model across runtime and meta APIs


Conclusion
----------

The CNCF REST response model provides a stable and minimal contract
for all Web-facing integration.

It supports:

- SPA applications
- external web applications
- JavaScript SDK
- Form API
- operational tooling

while preserving CNCF's core philosophy of explicit,
operation-oriented execution.
