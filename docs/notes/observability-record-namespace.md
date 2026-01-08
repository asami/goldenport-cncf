---
status = draft
scope = goldenport-core / cncf observability
phase = 2.5
---

# Observability Record Namespace Specification

## 1. Purpose

This document defines the **namespace and structural conventions**
for observability data represented using `org.goldenport.record.Record`.

The goal is to establish a **stable, meaning-preserving intermediate representation**
for observability before projection into:

- CLI output
- HTTP responses
- structured logs
- OpenTelemetry
- metrics systems
- AI / automation consumers

This specification freezes the **semantic contract** of observability records.

---

## 2. Core Principle

**If data is not a target of logic, it must not have a type.  
Such data is carried exclusively as `Record`.**

Observability data is **informational**, not behavioral.

Therefore:
- No branching
- No validation
- No interpretation
- No message formatting

Only **structured meaning transport**.

---

## 3. Representation Model

- Observability data is represented as a single `Record`
- Meaning is expressed using **namespaced keys**
- Keys use **dot-separated hierarchical namespaces**
- Values are primitives or small value objects

No DTO-style `XXXRecord` classes are defined.

---

## 4. Namespace Design Rules

### 4.1 Hierarchical Keys

- `.` indicates semantic hierarchy
- Hierarchy must be shallow and stable
- Namespaces are additive only

Example:
```
http.method
scope.environment
error.code
```

---

### 4.2 Namespace as Semantic Boundary

Each namespace represents a **semantic category**.
Namespaces must not overlap in meaning.

---

## 5. Defined Namespaces

### 5.1 scope.*

Where execution occurred.

| key | description |
|----|-------------|
| `scope.subsystem` | Logical subsystem name |
| `scope.environment` | dev / test / prod |
| `scope.ingress` | cli / server / server-emulator |
| `scope.base_uri` | REST server base URI (optional) |
| `scope.instance_id` | Process or node identifier (optional) |

Notes:
- `base_uri` is informational only
- Presence is optional
- Must not be inferred or auto-filled

---

### 5.2 http.*

Observed HTTP request facts.

| key | description |
|----|-------------|
| `http.method` | HTTP method |
| `http.path` | Request path |
| `http.original_uri` | Full URI if available |
| `http.query` | Raw query string (optional) |
| `http.content_type` | Content-Type header (optional) |
| `http.user_agent` | User-Agent header (optional) |

Notes:
- Headers are not fully copied
- Only observability-relevant fields are included

---

### 5.3 operation.*

Logical operation identity.

| key | description |
|----|-------------|
| `operation.component` | Component name |
| `operation.service` | Service name |
| `operation.name` | Operation name |
| `operation.fqn` | Fully-qualified name |
| `operation.action_id` | Execution identifier (optional) |

Notes:
- Values must be determined by routing / execution
- Partial or speculative values are prohibited

---

### 5.4 result.*

Execution result.

| key | description |
|----|-------------|
| `result.success` | Boolean success flag |

---

### 5.5 error.*

Failure semantics (core-error-semantics compliant).

| key | description |
|----|-------------|
| `error.kind` | fault / defect / anomaly |
| `error.cause` | validation / argument / state / etc |
| `error.code` | Detail error code |
| `error.strategy` | retry / input_required (optional) |
| `error.subject` | Domain or parameter identifier (optional) |

Critical rule:
- `error.code` is the **only machine-stable discriminator**
- Messages are never included

---

## 6. Record Composition

Observability records are composed by merging multiple sources:

```
Record.empty
  .merge(scopeRecord)
  .merge(httpRecord)
  .merge(operationRecord)
  .merge(resultRecord)
  .merge(errorRecord)
```

Rules:
- Merge is order-sensitive (later wins)
- Missing data remains missing
- No normalization or enrichment occurs here

---

## 7. Prohibited Content

The following must never appear in an observability record:

- Human-readable messages
- HTTP status codes
- Exit codes
- Stack traces
- Arbitrary dumps
- Nested Map structures

These belong to **projection layers**, not core observability.

---

## 8. Boundary Declaration

This specification defines the **observability semantic contract**
for goldenport-core and CNCF.

After this point:
- Core logic must not reinterpret observability meaning
- CML / CRUD / Job / Workflow layers must conform
- Only additive namespace extensions are permitted

---

END OF DOCUMENT
