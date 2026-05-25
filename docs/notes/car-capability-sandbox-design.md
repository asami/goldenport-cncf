# CAR Capability Sandbox Design

Status: note
Date: 2026-05-25

## Purpose

This note designs the CNCF-side CAR capability sandbox model based on
`docs/journal/2026/05/car-capability-sandbox.md`.

The goal is not to build a perfect JVM security sandbox. Modern JVM execution
cannot rely on `SecurityManager` as a long-term boundary, and JVM-internal
sandboxing remains difficult because of reflection, `MethodHandle`,
`invokedynamic`, JNI, `Unsafe`, classloading, and serialization gadgets.

The goal is therefore:

- architectural enforcement;
- capability-oriented execution;
- AI-generated code control;
- observability integration;
- operational governance;
- accident prevention;
- deterministic deployment rejection for unsafe CARs.

Hard security isolation remains a process/container concern.

## Design Principle

The core rule is:

```text
CAR code must not access the external world directly.
External access is only allowed through CNCF-managed runtime capabilities.
```

This means a declared CAR capability does **not** allow direct use of
`java.io`, `java.nio.file`, `java.net`, process execution, unmanaged threads,
or native access.

Instead:

```text
CAR declares required capability
  -> deployment policy grants or denies it
  -> CNCF injects a managed capability facade into the OperationCall/runtime DSL
  -> CAR code uses that managed facade
  -> CNCF observes, authorizes, audits, retries, and constrains the call
```

## Relationship To Existing Capability Vocabulary

CNCF already uses subject/resource capabilities for authorization.
CAR execution capabilities are related but distinct.

```text
subject capability
  = what the caller is allowed to do

resource/action capability
  = what a resource/action requires

CAR execution capability
  = what external runtime facility a component is allowed to request/use
```

All three may participate in one operation.

Example:

```text
subject has:           information:publish
operation requires:    information:publish
CAR requests/granted:  rdf.publish:default
runtime use:           KnowledgePublicationCapability.publish(...)
```

The caller must be authorized to invoke the operation, and the CAR must be
granted the runtime capability needed to reach the backend.

## Execution Boundary

The normal execution path is:

```text
CAR code
  -> internal DSL / OperationCall
  -> CNCF runtime capability facade
  -> managed provider/backend
  -> external world
```

The stable execution chokepoint remains the CNCF operation/runtime boundary.
Componentlet/domain code should remain pure or explicitly effect-controlled.
External effects are admitted through runtime capabilities, not arbitrary JVM
calls.

## Capability Declaration

CARs declare requested execution capabilities in CAR metadata.

Candidate descriptor shape:

```yaml
execution:
  sandbox: soft
  capabilities:
    - id: file.read
      resource: /data/config/*
      reason: load component configuration
    - id: http.outbound
      resource: https://api.example.com/*
      reason: call upstream API
    - id: event.publish
      resource: event.*
      reason: publish domain events
```

The declaration is a request, not a grant.

Deployment/subsystem policy decides whether the requested capabilities are
allowed:

```text
requested capabilities
  -> runtime/deployment policy
  -> granted capability set
  -> runtime capability scope
```

If a required capability is not granted, the component must fail deployment or
operation admission deterministically, depending on when the missing grant is
known.

## Capability Families

Initial capability families should be small and explicit.

| Family | Example | Runtime facade |
| --- | --- | --- |
| `clock` | `clock.read` | `ClockCapability` |
| `file` | `file.read:/data/*` | `FileCapability` |
| `http` | `http.outbound:https://api.example.com/*` | `HttpCapability` |
| `event` | `event.publish:event.*` | `EventCapability` |
| `queue` | `queue.publish:name` | `QueueCapability` |
| `datastore` | `datastore.read:name` | `DataStoreCapability` |
| `knowledge` | `rdf.publish:default`, `embedding.write:default` | `KnowledgeCapability` |
| `blob` | `blob.read`, `blob.write` | `BlobCapability` |
| `process` | `process.exec:*` | hard sandbox only |
| `native` | `native.load:*` | hard sandbox only |

High-risk families such as `process` and `native` should normally require a
hard sandbox profile.

## Runtime Capability Scope

At runtime, CNCF builds a capability scope for an operation:

```text
CarCapabilityScope
  componentId
  carId
  operationSelector
  requestedCapabilities
  grantedCapabilities
  deniedCapabilities
  runtimeFacades
```

The scope is bound to the execution boundary. Code should not be able to obtain
a capability facade unless it is present in the scope.

Candidate API shape:

```scala
trait CarExecutionCapabilities {
  def clock: ClockCapability
  def file: FileCapability
  def http: HttpCapability
  def events: EventCapability
  def knowledge: KnowledgeCapability
}
```

The public API should avoid exposing raw clients such as `java.net.http.Client`
or filesystem `Path` handles that allow bypassing policy.

## Verification Pipeline

CNCF should verify CARs in stages.

```text
CAR load request
  -> manifest parse
  -> dependency/classloader setup
  -> bytecode reference scan
  -> capability request validation
  -> deployment policy grant
  -> runtime capability scope construction
```

### Manifest Parse

Validate:

- declared sandbox profile;
- capability id syntax;
- resource pattern syntax;
- reason/metadata presence when policy requires it;
- unsupported or unknown capability families.

### ClassLoader Isolation

Each CAR should still use an isolated ClassLoader for:

- namespace isolation;
- dependency isolation;
- ABI separation;
- governance.

ClassLoader isolation is not a complete security boundary.

### Bytecode Reference Scan

Bytecode scanning should classify references, not rely on one flat deny list.

Candidate classes:

| Class | Meaning | Default |
| --- | --- | --- |
| hard-forbidden | should never appear in soft sandbox | reject |
| direct-external-access | external IO/process/thread API | reject; use runtime capability |
| suspicious | reflection/classloading/dynamic features | reject or require stricter profile |
| value-only | harmless value APIs such as URI/value parsing | allow by allowlist |
| runtime-facade | CNCF managed capability APIs | allow if capability is granted |

Examples:

| API family | Category | Note |
| --- | --- | --- |
| `java.lang.Runtime.exec` | hard-forbidden | hard sandbox only |
| `java.lang.ProcessBuilder` | hard-forbidden | hard sandbox only |
| `sun.misc.Unsafe`, `jdk.internal.*` | hard-forbidden | reject |
| JNI/native library loading | hard-forbidden | hard sandbox only |
| `java.io.FileInputStream` | direct-external-access | use `FileCapability` |
| `java.nio.file.Files` read/write | direct-external-access | use `FileCapability` |
| `java.net.Socket` | direct-external-access | use `HttpCapability` or provider |
| `java.net.http.HttpClient` | direct-external-access | use `HttpCapability` |
| `Thread`, executor creation | suspicious/direct-external-access | use CNCF job/async runtime |
| reflection / `MethodHandles` | suspicious | deny in soft sandbox unless justified |
| `java.net.URI` | value-only | likely allow |
| `java.nio.charset.StandardCharsets` | value-only | allow |

Important rule:

```text
Declaring file.read does not permit direct java.io access.
It permits use of CNCF FileCapability.
```

### Capability Request Validation

The scan result and manifest are compared.

Reject if:

- code references direct external APIs;
- code references runtime facade APIs for unrequested or denied capabilities;
- manifest requests unknown capability families;
- manifest requests hard-sandbox-only capabilities under soft sandbox;
- deployment policy denies a required capability.

Warn or reject by policy if:

- capability resource patterns are too broad;
- no reason is supplied;
- capability is requested but never used;
- bytecode references suspicious dynamic features.

## Soft Sandbox

Soft sandbox is the normal in-JVM mode.

Implemented by:

- ClassLoader isolation;
- bytecode reference scanning;
- capability declaration/grant;
- runtime capability facade injection;
- operation/context chokepoint;
- observability and audit.

Purpose:

- architectural enforcement;
- accidental misuse prevention;
- governance;
- AI-generated code control.

Soft sandbox is not a hostile-code security boundary.

## Hard Sandbox

Hard sandbox is process/container isolation.

Use for:

- untrusted CARs;
- native execution;
- unrestricted IO;
- external tools;
- GPU/inference runtimes;
- process execution;
- high-risk vendor code.

Candidate shape:

```text
CAR
  -> Docker/isolated process component
  -> CNCF bridge protocol
  -> runtime capabilities exposed through bridge
```

Hard sandbox should still use CNCF capability declarations. The difference is
that process/container isolation becomes the security boundary.

## Relation To Docker Component

CAR capability sandbox is not separate from the existing Docker Component
direction. It is the capability/governance layer that should apply to both
ordinary in-JVM CAR execution and Docker-backed CAR execution.

The relationship is:

```text
Soft sandbox
  = in-JVM CAR execution with ClassLoader isolation, bytecode scan, and CNCF
    runtime capability facades

Hard sandbox / Docker Component
  = process/container-isolated execution using the same capability declaration
    and governance model, with Docker/process isolation as the security boundary
```

Docker Component therefore remains the execution form for workloads that need
hard isolation, native tools, process execution, GPU/inference runtimes, or
untrusted code. CAR capability declarations still matter in that mode because
they describe which CNCF-managed external effects the component is allowed to
request through the bridge.

This means the first implementation should avoid creating a second,
Docker-specific capability model. The same CAR capability manifest, deployment
grant policy, observability vocabulary, and runtime capability semantics should
be usable by both soft and hard sandbox profiles.

## Observability

All runtime capability use should emit structured observability.

Candidate events:

```text
car.capability.requested
car.capability.granted
car.capability.denied
car.capability.used
car.capability.violation
car.bytecode.scan
car.sandbox.profile.selected
```

Useful fields:

- component id;
- CAR id/version;
- operation selector;
- capability id;
- resource pattern;
- resolved target;
- sandbox profile;
- outcome;
- diagnostic;
- latency;
- retry count;
- subject/security context summary when operation-bound.

Failures should use normal `Consequence.Failure(Conclusion)` structure and
common diagnostics. Do not create a parallel sandbox-specific error hierarchy.

## AI-Generated Code Policy

The model is especially important for AI-generated code.

AI-generated code should be constrained by:

- generated manifest capabilities;
- bytecode scan;
- runtime capability facade availability;
- operation authorization;
- observability;
- deployment review.

If generated code attempts direct IO, process execution, sockets, unmanaged
threads, or reflection-heavy bypasses, the CAR should fail verification before
deployment.

## Deployment Policy

Deployment policy should decide whether requested capabilities are granted.

Candidate policy inputs:

- runtime mode: develop/test/production;
- component trust level;
- CAR source/repository;
- signer or checksum;
- requested capability family;
- resource pattern breadth;
- subsystem descriptor overrides;
- operator approval;
- hard sandbox availability.

Example:

```yaml
carCapabilityPolicy:
  defaultSandbox: soft
  grants:
    example.component:
      - id: http.outbound
        resource: https://api.example.com/*
  requireHardSandbox:
    - process.exec
    - native.load
```

## Relation To Authorization

CAR capability sandbox does not replace operation/resource authorization.

Both checks apply:

```text
operation authorization
  -> may this subject invoke the operation?

resource authorization
  -> may this subject access this target resource?

CAR capability grant
  -> may this component use this external runtime facility?
```

Example:

```text
Subject may publish information.
Component may use rdf.publish:default.
Only then may the operation publish to the RDF backend.
```

## Design Boundaries

Do not:

- treat ClassLoader isolation as a full security sandbox;
- use `SecurityManager` as the primary design;
- allow direct external APIs just because a capability is declared;
- leak provider clients into component code;
- let CAR code bypass OperationCall/internal DSL chokepoints;
- collapse CAR execution capabilities into subject capabilities;
- make hard sandbox an implementation detail of soft sandbox.

Do:

- keep runtime capabilities explicit and reviewable;
- scan bytecode before loading/execution;
- inject managed facades only through CNCF runtime context;
- observe every capability use;
- use Docker/process isolation for hostile or native workloads;
- keep failures structured through `Consequence` / `Conclusion`;
- make AI-generated side effects capability-governed.

## First Implementation Slice

Recommended first slice:

1. Define CAR capability manifest model.
2. Add capability id/resource pattern parser.
3. Add soft sandbox profile metadata.
4. Add bytecode scanner skeleton with allow/deny classification.
5. Reject obvious hard-forbidden APIs.
6. Add runtime capability scope model.
7. Add one managed facade, probably `ClockCapability` or `HttpCapability`
   using a test backend.
8. Emit observability records for capability grant/use/denial.
9. Add executable specs for direct API rejection and managed facade success.

Non-goals for the first slice:

- complete bytecode verifier;
- hostile-code security sandbox;
- Docker execution bridge;
- all capability families;
- production policy UI;
- dependency mediation redesign;
- source-code static analysis.

## Open Decisions

- Exact CAR metadata file and schema location.
- Whether capability declarations live in component descriptor, CAR manifest,
  or both.
- Capability id grammar and resource pattern grammar.
- Which value-only JDK APIs are allowlisted.
- How to model capability optionality: required vs optional.
- Whether deployment policy rejects unused requested capabilities.
- How hard sandbox CARs communicate with CNCF runtime capability facades.
- How capability grants are surfaced in manual/admin projections.
