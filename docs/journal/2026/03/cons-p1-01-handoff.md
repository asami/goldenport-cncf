CONS-P1-01: admin.system.status Introduction (Handoff)
====================================================

status=proposed
priority=P1
feasibility=high
difficulty=low
date=2026-03-21

---

# 1. Overview

This document defines the introduction of `status` operation
into the CNCF system service layer.

The goal is to establish a clear separation between:

- ping (reachability)
- health (operability)
- status (introspection)

and to standardize the contract for operational visibility.

---

# 2. Decision

## 2.1 Add status operation

Add `status` operation to:

- Component system service
- AdminComponent (direct or delegated)

```
system.ping
system.health
system.status   ← NEW
```

---

## 2.2 Keep health for compatibility

- `health` remains supported
- existing behavior is preserved
- no breaking change

---

# 3. Semantics

## 3.1 ping

```
Reachability only
```

---

## 3.2 health

```
Operational readiness
- dependency checks
- OK / NG
```

---

## 3.3 status

```
Detailed internal state
- runtime information
- job/task metrics
- system internals
```

---

# 4. Status Contract (Minimum)

To prevent future drift, `status` MUST define a minimum schema.

## 4.1 Required fields

```
status            :: "UP" | "DOWN" | "DEGRADED"
timestamp         :: Instant
uptime            :: Duration
```

---

## 4.2 Job-related (if JobEngine present)

```
jobsRunning       :: Int
jobsQueued        :: Int
jobsCompleted     :: Int
jobsFailed        :: Int
```

---

## 4.3 Optional (implementation-dependent)

```
queueDepth        :: Int
lastError         :: String
traceEnabled      :: Boolean
```

---

# 5. Structure Example

```
{
  "status": "UP",
  "timestamp": "2026-03-21T10:00:00Z",
  "uptime": "PT2H34M",
  "jobsRunning": 3,
  "jobsQueued": 5,
  "jobsCompleted": 120,
  "jobsFailed": 2
}
```

---

# 6. Component Placement

## 6.1 Component system service

```
component.system.status
```

Responsibilities:

- runtime state
- execution subsystem state
- JobEngine metrics

---

## 6.2 AdminComponent

Two options:

### Option A (recommended)

```
admin.system.status → delegate to component.system.status
```

### Option B

```
admin.system.status → aggregate across components
```

---

# 7. Implementation Plan

## Step 1

Add operation definition:

```
system.status
```

to Component system service.

---

## Step 2

Implement handler:

- collect runtime info
- collect JobEngine stats
- build status response

---

## Step 3

Expose via:

- CLI
- HTTP
- Client

---

## Step 4 (optional)

AdminComponent aggregation

---

# 8. Constraints

- status MUST be fast enough for operational use
- MUST NOT perform heavy queries
- MUST NOT block on long-running operations

---

# 9. Risks

## 9.1 Semantic overlap with health

Mitigation:

```
health = boolean readiness
status = structured state
```

---

## 9.2 Contract drift

Mitigation:

- define minimum required fields
- allow extension only via optional fields

---

# 10. Future Extensions

- OpenTelemetry integration
- TraceTree exposure
- Job detail drill-down
- multi-component aggregation

---

# 11. Summary

- Introduce `system.status` as first-class operation
- Keep `health` for compatibility
- Define minimal contract to avoid drift
- Integrate with Job/Task execution model

---

End.
