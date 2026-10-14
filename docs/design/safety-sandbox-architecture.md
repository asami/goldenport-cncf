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

## ExecutionContext and hierarchical sandbox resolution

`ExecutionContext` is the runtime unit that carries the resolved **Effective Sandbox** for an Operation. Sandbox definitions may be contributed by three scopes:

- **Server Sandbox Definition** — the maximum authority of the hosting server/runtime deployment;
- **Client Sandbox Definition** — the authority of the invoking client/command/request/project session;
- **Operation Sandbox Definition** — the minimum authority required by the concrete Operation/worker/step.

Before Operation execution CNCF deterministically resolves them by narrowing/intersection:

```text
Server Sandbox Definition
        +
Client Sandbox Definition
        +
Operation Sandbox Definition
        -> SandboxResolver
        -> SandboxContext (Effective Sandbox)
        -> ExecutionContext
        -> Operation
```

A child definition cannot widen authority granted by its parent. In set terms the effective authority is bounded by `Operation ∩ Client ∩ Server`. Deny/restriction semantics must fail closed where definitions conflict or cannot be resolved safely.

`SandboxDefinition` is declarative input. `SandboxResolver` is deterministic framework logic. `SandboxContext` is the resolved runtime authority exposed through `ExecutionContext`. Runtime enforcement points such as UnitOfWork, ResourceTreeAccess, ProcessExec and managed providers consume the same effective context; UnitOfWork is an enforcement point, not the owner of the sandbox boundary.

This model supports both CLI and resident-server execution. A CLI may supply a local/default Server definition plus Command-as-Client definition; a resident server supplies its deployment definition and derives a Client definition from each admitted request/session. Operation definitions remain unchanged across that transition.

Authorization remains orthogonal: `SecuritySubject` answers who may perform an application action, while `SandboxContext` answers which External Effects this execution may perform. Both belong to the ExecutionContext and both may deny one Operation.

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

## Development driver: sm-workflow

sm-workflow is a primary practical consumer of this architecture after its basic workflow functions are operational. Its AI-driven Git/SBT/source-editing work provides a demanding Safety Sandbox use case without changing the hostile-code threat model.

For sm-workflow the generic scopes map naturally to `Server -> Client/Command -> Operation/Worker`. Initial command operation uses Command as the Client scope; later resident-server operation adds the real Server deployment boundary without changing Operation sandbox semantics.

The sm-workflow adoption is also a development driver for CNCF Runtime and CBD Support: real sandbox violations, missing managed-effect routes and lint/admission classification gaps discovered while sandboxing sm-workflow should feed bounded CNCF Runtime and CBD Support development rather than be patched with sm-workflow-specific bypasses.

## AI Safety Harness

Safety Sandbox is the runtime counterpart of Textus AI governance:

```text
AI directive -> CAR lint -> CAR Loading Admission -> Runtime Sandbox
```

AI is not asked to make the final safety decision. Deterministic CNCF mechanisms reject direct escape routes and bound admitted effects.

## Security benefit

Safety Sandbox reduces attack surface by removing ambient routes such as arbitrary shell execution, host filesystem access, raw sockets/HTTP clients and direct persistence handles from ordinary component code. This can prevent an application-level vulnerability from becoming unrestricted external-resource authority.

It does not claim that a deliberately hostile CAR cannot bypass an in-JVM boundary.

## Deferred option: Java Agent reinforcement

Java Agent / bytecode instrumentation is a deferred reinforcement option, not part of the initial Safety/Soft Sandbox completion criteria. First complete and exercise the architecture based on CAR Lint, CAR Loading Admission, ExecutionContext/SandboxContext resolution and managed Runtime Capability enforcement.

After that baseline is operational, re-evaluate the JVM ecosystem and practical experience. A Java Agent may be useful as an additional safety layer that intercepts selected direct JDK filesystem/network/process or similar calls and consults the effective SandboxContext. The intended evaluation is accident prevention and defense-in-depth for ordinary code, not recreation of SecurityManager or proof of hostile-code containment.

At evaluation time, review contemporary OpenJDK/JVM capabilities and production experience from projects using agent/instrumentation-based restrictions. Adopt only if the benefit is clear and the mechanism remains bounded, maintainable and compatible with the existing Loading Admission and Runtime Sandbox model. Docker/process isolation remains the hostile-code security boundary regardless of this option.

## Hard isolation

Docker/process isolation is the security boundary for untrusted code, native execution, unrestricted tools and other workloads that require hostile-code containment. The same capability-oriented vocabulary should be reused across in-JVM and isolated execution where practical.

## Design constraints

- Safety is the primary design criterion.
- Ordinary CAR development should remain convenient.
- Do not recreate SecurityManager or SELinux inside CNCF.
- Do not add integrity/hash/rollback machinery unrelated to the stated effect boundary.
- Prefer deterministic rejection and bounded logical capabilities over speculative defensive complexity.
