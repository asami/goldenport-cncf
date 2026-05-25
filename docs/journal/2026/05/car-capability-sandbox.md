# CAR Capability Sandbox and Execution Boundary

status=draft
updated_at=2026-05-25

# Summary

This note describes the execution boundary model for CAR (Component Archive Runtime) in CNCF.

The goal is not to build a perfect JVM security sandbox.
Instead, the primary goal is:

- architectural enforcement
- capability-oriented execution
- AI-generated code control
- observability integration
- operational governance
- accident prevention

CNCF therefore adopts a layered execution boundary model based on:

- internal DSL execution
- capability declaration
- ClassLoader isolation
- bytecode verification
- Docker-based hard isolation

# Background

Traditional JVM sandbox mechanisms relied on `SecurityManager`.

However, modern Java no longer considers `SecurityManager` to be a practical long-term sandbox mechanism.

In addition, JVM-internal sandboxing is fundamentally difficult because of:

- reflection
- MethodHandles
- invokedynamic
- JNI
- Unsafe
- serialization gadgets

Therefore, CNCF does not treat ClassLoader isolation as a complete security boundary.

Instead, it is treated as:

- structural enforcement
- execution governance
- accidental misuse prevention

# Design Principle

The fundamental principle is:

```text
CAR must not access the external world directly.
External access is only allowed through CNCF-managed capabilities.
```

This creates a capability-oriented execution architecture.

# CAR Execution Model

```text
CAR
  ↓
Internal DSL
  ↓
CNCF Runtime
  ↓
Capability
  ↓
External World
```

In the normal case, a CAR executes only CNCF internal DSL operations.

This means:

- no direct file access
- no direct socket access
- no process execution
- no arbitrary thread management

The CAR becomes a managed execution unit.

# Capability Declaration

If external access is required, the CAR must explicitly declare capabilities in its manifest.

Example:

```yaml
capabilities:
  - file.read:/data/config/*
  - http.outbound:https://api.example.com/*
  - queue.publish:event.*
```

Capabilities are therefore:

- explicit
- reviewable
- auditable
- deploy-time verifiable

# Runtime Verification

CNCF performs multiple verification stages.

## ClassLoader Isolation

Each CAR is loaded using an isolated ClassLoader.

Purpose:

- namespace isolation
- dependency isolation
- execution boundary separation

This is not treated as a full security sandbox.

## Bytecode Verification

Before loading, CNCF scans bytecode references.

Examples of forbidden APIs:

```text
java.io.*
java.nio.file.*
java.net.*
java.lang.ProcessBuilder
java.lang.Runtime.exec
sun.misc.Unsafe
jdk.internal.*
```

If forbidden APIs are detected without corresponding declared capabilities:

```text
deployment rejected
```

# Capability-Oriented Runtime

External access is performed only through CNCF runtime capabilities.

Example:

```java
public interface CarExecutionContext {
    FileCapability file();
    HttpCapability http();
    ClockCapability clock();
    EventCapability events();
}
```

The runtime therefore becomes the execution authority.

# Observability Integration

Because all external access passes through runtime capabilities:

- audit logging
- tracing
- metrics
- retry
- authorization
- transaction control
- asynchronous execution

can be centrally managed.

This integrates naturally with CNCF-native observability.

Example runtime events:

```text
capability:file:read
capability:http:get
capability:event:publish
```

# AI-Oriented Architecture

This model is particularly important for AI-generated code.

AI-generated code often attempts:

- direct file access
- arbitrary HTTP access
- process execution
- unmanaged side effects

The CNCF capability model constrains execution structure itself.

This means:

```text
Code generation
+
Capability declaration
+
Runtime governance
```

can be handled together.

# Hard Sandbox Model

For untrusted or highly dangerous workloads, CNCF uses process-level isolation.

Instead of executing directly inside the JVM:

```text
CAR
  ↓
Docker Component
  ↓
Isolated Container
```

Examples:

- GPU workloads
- native execution
- unrestricted IO
- external tools
- AI inference runtimes

# Layered Sandbox Strategy

## Soft Sandbox

Implemented by:

- ClassLoader isolation
- bytecode verification
- capability restriction

Purpose:

- architectural enforcement
- accidental misuse prevention
- governance
- AI execution control

## Hard Sandbox

Implemented by:

- Docker
- isolated JVM
- container execution

Purpose:

- security boundary
- hostile workload isolation
- native process containment

# Architectural Positioning

This mechanism should not be viewed merely as a "Java sandbox".

It is better understood as:

```text
Capability-Oriented Execution Architecture
```

or:

```text
Managed Execution Boundary
```

The primary goal is architectural governance of execution itself.
