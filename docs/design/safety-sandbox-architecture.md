# Safety Sandbox Architecture

Status: normative design
Date: 2026-10-15
Related: [Capability-Oriented Execution Architecture](capability-oriented-execution-architecture.md)

## Purpose

CNCF Safety Sandbox prevents a CAR from causing External Effects outside its intended execution boundary because of program bugs, input mistakes, configuration mistakes, or unexpected AI-generated implementation.

Its primary goal is **safety**, not hostile-code containment.

Security is an important secondary benefit: constraining external-effect routes reduces the ability of a malicious application user to turn a vulnerable component into a filesystem, network, datastore, process or native-operation proxy.

## Threat and assurance model

Safety Sandbox protects primarily against:

- implementation defects;
- incorrect or unexpectedly broad input;
- configuration mistakes;
- AI-generated direct I/O or improvised operational mechanisms;
- vulnerable Operations being used as stepping stones to unintended external effects.

Safety Sandbox does **not** promise containment of a malicious CAR deliberately attempting to defeat the sandbox through reflection, bytecode tricks, JNI, Unsafe or equivalent techniques. Code that is itself untrusted requires Docker/process isolation.

In short:

```text
trusted-but-possibly-buggy CAR + possibly-hostile input -> Safety Sandbox
possibly-hostile CAR                               -> Docker/process isolation
```

## Three enforcement stages

### 1. CAR Lint Check

Development-time deterministic checks identify direct external-resource/effect access and other Safety Sandbox violations early enough to fix them.

Lint is diagnostic and workflow-oriented. It is not the runtime safety boundary.

### 2. CAR Loading Admission

Before an ordinary in-JVM CAR is admitted, CNCF verifies that the packaged artifact conforms to the Safety Sandbox profile.

Admission includes bounded artifact/bytecode reference classification for direct filesystem, network, process, native, raw datastore/JDBC and other defined escape routes. ClassLoader isolation provides dependency/ABI/safety isolation but is not treated as hostile-code security containment.

A declared capability does not authorize direct JVM access. It authorizes use of the corresponding CNCF-managed execution route.

### 3. Runtime Sandbox

Admitted code reaches External Effects through the Capability-Oriented Execution Architecture.

Runtime enforcement checks the logical resource/capability scope at the managed effect boundary. Prefer logical CNCF resources (ResourceTree, admitted process/program, logical datastore/provider) over a large SELinux-like matrix of physical paths and OS permissions.

## Common policy vocabulary

Lint, Loading Admission and Runtime Sandbox must share CNCF-owned External Effect / escape-hatch classifications where their evidence overlaps. They have different enforcement roles and must not maintain contradictory private rule taxonomies.

Examples include:

- filesystem/resource-tree;
- datastore/persistence;
- network/provider;
- process;
- native;
- environment/configuration;
- clock;
- event/queue/blob/device as applicable.

## Runtime examples

A CAR using ResourceTreeAccess can be admitted while Runtime Sandbox limits it to the bound logical tree. A CAR containing direct `java.nio.file.Files` access is a loading-admission violation even if it requests a filesystem capability.

A CAR may request a registered ProcessExec capability. This does not grant arbitrary shell execution: the trusted runtime owns the program definition, arguments/environment policy, WorkArea and limits.

## AI Safety Harness

Safety Sandbox is the runtime counterpart of Textus AI governance:

```text
AI directive -> CAR lint -> CAR Loading Admission -> Runtime Sandbox
```

AI is not asked to make the final safety decision. Deterministic CNCF mechanisms reject direct escape routes and bound admitted effects.

## Security benefit

Safety Sandbox reduces attack surface by removing ambient routes such as arbitrary shell execution, host filesystem access, raw sockets/HTTP clients and direct persistence handles from ordinary component code. This can prevent an application-level vulnerability from becoming unrestricted external-resource authority.

It does not claim that a deliberately hostile CAR cannot bypass an in-JVM boundary.

## Hard isolation

Docker/process isolation is the security boundary for untrusted code, native execution, unrestricted tools and other workloads that require hostile-code containment. The same capability-oriented vocabulary should be reused across in-JVM and isolated execution where practical.

## Design constraints

- Safety is the primary design criterion.
- Ordinary CAR development should remain convenient.
- Do not recreate SecurityManager or SELinux inside CNCF.
- Do not add integrity/hash/rollback machinery unrelated to the stated effect boundary.
- Prefer deterministic rejection and bounded logical capabilities over speculative defensive complexity.
