# Java Component Binding Consideration

Date: 2026-07-31

Status: consideration decision record

## Goal

Enable ordinary CNCF Component development entirely in Java.

The development model must support three first-class adoption paths:

1. develop a new Component entirely in Java;
2. encapsulate existing Java logic as a Component by developing only a pure
   Java facade; and
3. encapsulate existing Java logic as a Component by developing only a Scala
   facade.

The third path is expected to be the simplest option for a developer who can
use Scala because the facade can consume the existing CNCF Scala DSL directly.
The Java-facade path remains essential when the Component integrator wants or
needs to stay entirely within Java.

The target baseline is:

- JDK 17 or later for Java Component development; and
- Scala 3.9 or later for CNCF and Scala Component development.

The Java binding is not a thin example wrapper around Scala classes. It must
support the Component lifecycle, generated CML contracts, Service and
Operation implementation, internal DSL use, Component-to-Component calls,
packaging, loading, diagnostics, and the eventual full Component capability
surface.

Existing Java business/domain logic is not required to adopt CNCF base classes
or be rewritten in Scala. A facade owns adaptation between its existing Java
types and lifecycle and the CML-defined Component/Service/Operation contract.

## Current Evidence

The existing runtime already supplies several foundations:

- CAR `component/main.jar` and dependency JARs can be loaded through a
  Component-local ClassLoader;
- assembly API and Component-local dependency ClassLoaders preserve the
  required contract and implementation identities;
- Component, Service, and Operation form the common runtime execution model;
- Process Execution is the canonical boundary for an approved one-shot
  external program;
- Managed Service Container Runtime owns reusable endpoint-bearing Docker
  services; and
- legacy Docker and REST Component adapter work provides earlier execution-form
  experience.

The existing Java development surface is not sufficient:

- runtime discovery currently recognizes Scala-side `Component`,
  `Component.Factory`, and `Component.BundleFactory` types;
- those APIs expose Scala and CNCF implementation types such as `Option`,
  `Vector`, `Consequence`, `ExecutionContext`, Cats, and Circe;
- the current `RestComponent` surface remains mostly adapter/scaffolding
  rather than a general external Component hosting protocol;
- the legacy one-shot Docker adapter is not the canonical future execution
  boundary; and
- the current project baseline is Scala 3.3.8 with Java compilation targets
  older than the selected JDK 17 baseline.

ClassLoader isolation is a dependency, namespace, and governance boundary. It
is not a hostile-code security sandbox. Untrusted or environment-dependent
execution requires a process, isolated JVM, container, or remote boundary.

## Selected Direction: Pure Java API

Java Component code does not directly extend or consume the Scala Component
API.

Introduce a pure Java binding API, provisionally identified as
`cncf-java-api`, with these properties:

- compiled with `--release 17`;
- no Scala binary suffix;
- no required Scala library, Cats, Circe, or Scala collection dependency in
  the Java developer API;
- uses ordinary Java classes, records, interfaces, collections, and
  `Optional`;
- remains stable independently of the Scala 3.9-or-later implementation
  version; and
- is translated to the existing CNCF runtime by a Scala-owned bridge.

The conceptual structure is:

```text
new Java Component -----------+
                              |
existing Java logic           |
  -> pure Java facade --------+-> pure Java Component API
                                  -> Scala Java-binding bridge
                                  -> CNCF runtime

existing Java logic
  -> Scala facade
  -> existing Scala Component API and internal DSL
  -> CNCF runtime
```

Java factory discovery must not depend on a Java class being a subtype of the
Scala `Component` or `Component.Factory`. A CAR records a pure Java factory
entrypoint and binding version in its admitted metadata. Naming conventions or
Java `ServiceLoader` may remain optional advanced discovery aids, but they are
not the primary ordinary development contract.

Provisional metadata concerns include:

```yaml
implementation:
  language: java
  binding: cncf.java-component.v1
  java:
    minimum: 17

execution:
  placement: in-process-jvm
```

The exact schema and vocabulary remain open.

## Existing Java Logic Facades

An existing Java library or application-logic module may remain an ordinary
Java artifact on the Component-local classpath. The facade supplies:

- CML Component, Service, Operation, and type bindings;
- factory and lifecycle integration;
- conversion between generated CML language projections and existing Java
  input/output types;
- configuration and dependency construction;
- generic Component invocation;
- transaction and UnitOfWork integration at the facade boundary;
- exception-to-Conclusion and Error propagation policy;
- CallTree and safe diagnostic attribution; and
- CAR dependency, packaging, source, and provenance metadata.

The pure Java facade path uses the pure Java Component API and procedure DSL.
It must not require a Scala source file in the Component project.

The Scala facade path wraps the same existing Java logic through the ordinary
Scala Component API and Scala internal DSL. It does not require a separate
Java binding layer for the facade itself and is expected to need less adapter
infrastructure when Scala development is acceptable.

Both facade forms expose the same CML-defined logical Component contract.
Callers cannot distinguish whether the implementation is new Java logic,
existing Java logic behind a Java facade, or existing Java logic behind a
Scala facade.

Facade encapsulation does not automatically govern effects performed directly
inside legacy Java logic. Direct filesystem, network, process, thread, native,
or other ambient access in that logic bypasses the CNCF internal DSL unless it
is separately adapted. Admission must therefore classify the existing logic:

- trusted in-process logic may run under ClassLoader isolation with declared
  dependencies and reviewed capabilities;
- adaptable effects should be redirected through facade-supplied CNCF
  capabilities; and
- untrusted, native, unrestricted, or environment-sensitive logic should use
  an isolated JVM, Process Execution, container, or remote placement as
  appropriate.

ClassLoader encapsulation alone must not be presented as a security sandbox for
legacy Java logic.

## Java Internal DSL

Java requires a Java-native projection of the CNCF internal DSL. It is not
enough to provide only Component construction and Operation handler
interfaces.

The Java internal DSL must ultimately cover the admitted Component capability
surface, including:

- Entity create/load/update/delete/search, revision, and OCC;
- Aggregate, CQRS, View, and StateMachine behavior;
- configuration;
- events, Jobs, and Tasks;
- HTTP, Resource, Blob, and Process Execution;
- managed services;
- datastore access through the admitted CNCF boundary;
- Component, Service, and Operation calls;
- SPI and other capabilities;
- authorization;
- CallTree and observability integration;
- failure, retry, and compensation behavior; and
- UnitOfWork participation.

Java and Scala DSLs are two language surfaces over one semantic kernel:

```text
Scala internal DSL --+
                     +-> UnitOfWorkOp / Capability / ActionEngine
Java internal DSL  --+
```

The Java DSL does not implement a second UnitOfWork interpreter, transaction
model, authorization path, or observability path.

## Procedure-Only Java Semantics

The Java binding provides procedure semantics only.

It does not expose:

- a Java Free Monad;
- `JavaProgram<T>` or an equivalent deferred functional program;
- Scala `ExecUowM`;
- `flatMap`-based effect composition; or
- arbitrary `CompletionStage` execution as the ordinary handler model.

A Java Operation handler is an ordinary sequential procedure:

```java
public Order execute(
    JavaProcedureContext context,
    CreateOrder input
) {
    Customer customer = context.entities(Customer.class)
        .require(input.customerId());

    Order order = context.entities(Order.class)
        .create(Order.create(customer, input.items()));

    context.events().publish(new OrderCreated(order.id()));
    return order;
}
```

The runtime begins the ActionCall and UnitOfWork before entering the handler.
Java DSL calls execute through the current CNCF interpreter, and normal return,
failure, abort, commit, cancellation, cleanup, and observability remain
runtime-owned.

Asynchronous execution is an outer CNCF Job/Task concern. A Java procedure may
be run by a Job, but the handler does not create an independent asynchronous
execution model.

## Generic Component Invocation

Scala and Java Components do not use a special cross-language invocation
mechanism.

Every Component-to-Component call uses the generic CNCF
Component/Service/Operation invocation path:

```text
caller
  -> Component / Service / Operation identity
  -> generic Operation invocation
  -> ActionEngine
  -> target Component
```

This applies to:

- Scala to Scala;
- Scala to Java;
- Java to Scala; and
- Java to Java.

The same logical invocation also supports targets placed in-process, in an
isolated JVM, in a container, or behind a remote service adapter. The caller
does not branch on target implementation language or placement.

CML remains the language-neutral contract. Java records and Scala case classes
are language projections of the same CML logical type. Component calls do not
exchange or cast implementation-language classes directly.

Generated Java and Scala clients may provide a typed development surface, but
they are facades over generic Operation invocation rather than direct object
calls. Even an in-process optimization must preserve authorization,
ExecutionContext, UnitOfWork, ActionEngine, CallTree, and structured failure
semantics.

## Execution Placement

Implementation language and execution placement are independent axes.

Provisional placement categories are:

- `in-process-jvm`;
- `isolated-jvm`;
- `container-service`; and
- `remote-service`.

In-process Java Components use the existing CAR and Component-local ClassLoader
foundation through the Java-binding bridge.

An isolated JVM, container, or remote placement requires a language-neutral
invocation protocol derived from the same CML Component/Service/Operation
contract. Java handler objects and the runtime's full internal
`ExecutionContext` are not transported directly. Only the admitted invocation,
safe context projection, result, failure, cancellation, correlation, and
artifact information cross that boundary.

The relationship with existing runtime mechanisms is:

- Process Execution remains the canonical one-shot external-program boundary;
- Managed Service Container Runtime remains the lifecycle boundary for a
  reusable endpoint-bearing service;
- the legacy one-shot Docker adapter is not the new Java binding foundation;
  and
- the Java binding must reuse or extend the common invocation boundary rather
  than add another Docker-, REST-, or Java-specific Component model.

## Java-to-Scala Failure Boundary

Normal Java return becomes `Consequence.Success`.

A Java `Exception`, including an explicitly raised Java DSL failure, is
normalized by the Scala bridge into a structured `Conclusion` and returned as
`Consequence.Failure`.

```text
Java return
  -> Consequence.Success

Java Exception
  -> Scala bridge
  -> Conclusion
  -> Consequence.Failure
```

The boundary distinguishes:

- an existing CNCF failure carried through the Java DSL;
- an explicitly declared domain, validation, authorization, conflict, timeout,
  or other Java binding failure;
- generic Component invocation failure;
- cancellation and interruption; and
- an unexpected Component implementation exception.

An existing `Conclusion` crossing Scala to Java and back must retain its
structured identity and evidence. The bridge must not parse a display message
and synthesize a weaker replacement. Cause kind, detail code, retryability,
authorization classification, provenance, safe diagnostic facets, and
CallTree correlation must remain attributable.

Generic exceptions such as `IllegalArgumentException` are not inferred to be
business validation failures merely from their Java class. Expected failures
are raised explicitly through the Java DSL; unexpected exceptions become
Component-defect Conclusions.

## Java Error Boundary

A Java `Error` is not converted into a normal `Conclusion`.

```text
Java Error
  -> rethrow across the Java-binding bridge
  -> Scala runtime exception handling
```

This includes errors such as:

- `VirtualMachineError`;
- `OutOfMemoryError`;
- `StackOverflowError`;
- `LinkageError`; and
- `AssertionError`.

The Scala runtime exception path owns safe abnormal termination observation,
UnitOfWork abort and cleanup, Task/Job failure, Component/ClassLoader health
policy, and propagation of a JVM-fatal condition. An Error must not make the
operation appear to have produced an ordinary recoverable Component result.

`LinkageError` during CAR admission or Component loading may be diagnosed as a
JDK, binding, dependency, or ABI incompatibility. That admission-time
diagnostic is distinct from converting an Error thrown during Operation
execution into a business `Conclusion`.

## Open Design Issues

The following remain undecided:

1. exact Maven coordinates and package names for the pure Java API and Scala
   bridge;
2. the minimal Java binding v1 surface and the order in which the full DSL is
   delivered;
3. Java factory and handler metadata schemas and compatibility ranges;
4. the Java procedure failure API and safe descriptor vocabulary;
5. Java representations of CML values, Entity identity, revision, Record,
   schema, and binary/resource values;
6. Java DSL signatures for UnitOfWork-sensitive Entity, Aggregate,
   StateMachine, event, Job, HTTP, process, and managed-service operations;
7. the language-neutral invocation envelope and transport used by isolated
   JVM, container, and remote placements;
8. SPI cross-language generation and invocation boundaries;
9. ClassLoader parent-first identity rules for the Java binding API and
   generated Component API contracts;
10. Java source generation, project scaffold, build, lint, test fixture, CAR
    packaging, and repository admission changes in Cozy/sbt-cozy;
11. JDK 17-or-later and Scala 3.9-or-later compatibility and test matrices;
12. admission and packaging policy for trusted in-process legacy Java logic
    versus isolated-JVM/container/remote placement; and
13. assignment to a future CNCF development phase.

## Recommended Next Consideration

Define the smallest complete Java vertical slice before assigning an
implementation phase:

```text
CML
  -> generated pure Java input/output and typed client
  -> Java Component factory
  -> Java procedure Operation handler
  -> Java internal DSL call
  -> generic call to one Scala Component
  -> Conclusion-preserving failure return
  -> CAR package
  -> Component-local ClassLoader activation on JDK 17
```

This slice must prove the selected architecture without treating direct Scala
inheritance, a compile-only Java class, or a Java-only demo path as completion
of Java Component development.

Subsequent acceptance must add the same existing Java logic twice: once
through a pure Java facade and once through a Scala facade. Both Components
must expose an equivalent CML contract and observable behavior while retaining
their language-specific development experience.
