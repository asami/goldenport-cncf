# Resource Reference DSL

## Purpose

Phase 33 establishes resource reading as an ExecutionContext capability rather
than an ambient host capability. A component asks CNCF to read a logical
reference; it does not choose Java NIO, HTTP clients, classpath loaders, or a
provider-specific SDK.

## Ownership

- goldenport core remains independent of CNCF resource runtime semantics.
- CNCF owns `ResourceReference`, `ResourceAccess`, `ResourceContent`, the
  ExecutionContext injection point, and component-facing read helpers.
- RR-02 through RR-04 add provider SPI/configuration and policy behind this
  boundary.
- Components own logical references only. They do not own physical locations,
  credentials, or provider selection.

## Boundary

```text
Component behavior
  -> ExecutionContext.resources
  -> ResourceAccess
  -> provider/policy implementation
```

An unconfigured resource access value remains deliberately unavailable. This
makes a missing runtime binding deterministic and prevents an accidental
fallback to ambient host files or network access.

`ResourceReference` distinguishes an absolute non-URN URI from a generic URN.
It does not assign a provider or policy to either form. In particular,
`urn:textus` receives its namespace grammar and resolver only in RR-03, while
non-Textus NIDs receive extension dispatch only in RR-04.

Components that need artifacts below one logical source root use
`ResourceReference.resolveC`. The operation owns safe child-path validation and
defines child semantics only for URL and standard Textus URN references. It
does not infer path structure from an external URN NSS, because that namespace
belongs to the configured provider.

## Component Use

Component behavior uses the internal DSL helpers backed by
`ExecutionContext.resources`. The helpers retain structured `Consequence`
failures and do not log or trace content values. A component can request bytes
or strict text decoding; it cannot enumerate a location or mutate a resource.

## Standard Textus Provider

RR-03 introduces the standard `urn:textus:<namespace>:<resource-id>` provider
layer. Its `TextusUrnResourceProvider` SPI receives a parsed logical reference;
the runtime owns namespace selection and the configured read-only backing
provider. The initial binding is a file-root provider configured by logical
namespace. Components therefore retain a stable `urn:textus:` reference when a
deployment changes its root directories or later replaces the backing provider.

The provider layer is deliberately independent of legacy Blob/entity URN
resolution. Those URNs model entity identity and Blob semantics; they are not a
resource-content provider contract and must not become an implicit route into
this DSL.

## External URN Provider Boundary

RR-04 provides an explicit extension route for non-Textus NIDs. The generic
`UrnResourceProvider` is selected only from runtime-configured NID bindings;
there is no ambient discovery and no generic fallback for an unconfigured NID.
The configured binding and the provider-reported NID must agree, which prevents
one installed provider from claiming another provider's route.

`textus` is structurally reserved. The resource dispatcher selects the
dedicated Textus provider before generic external dispatch, and the external
configuration rejects `textus` bindings. This preserves the standard Textus
resource grammar as a CNCF-owned contract while allowing explicitly installed
future NIDs to evolve behind the same `ExecutionContext.resources` DSL.

## Admitted Resource Trees

`ResourceReference` and `ResourceAccess` remain single-resource read
contracts. A component that needs a bounded directory-like input uses the
separate `ResourceTreeReference` and `ResourceTreeAccess` capability through
protected `read_resource_tree`. The result is an immutable,
deterministically-ordered `ResourceTreeSnapshot` with logical entries and
admitted limits; it does not expose a physical root or general filesystem API.

The component may request tighter limits but cannot broaden the admitted
snapshot. A snapshot can become Process Execution input only through
`ProcessExecutionResourceTreeInput.createC(snapshot, target, limits)`, where
`target` is a validated WorkArea-relative path. Process Execution admission
then applies program and grant limits before the UnitOfWork interpreter
materializes the tree in its owned WorkArea. No component/request host path can
be converted into a tool input.

Resource-tree access is distinct from `ResourceAccess`: it is not a listing
extension, does not provide mutable filesystem access, and does not change URI
provider semantics.

## Test and Observability Boundary

`ResourceAccessTestProfile` is a deterministic test-owned composition surface.
It supplies in-memory URL, Textus URN, and external URN providers only when an
executable specification or component integration test explicitly installs the
profile on its `ExecutionContext`. It is not configuration syntax and cannot
silently alter a production assembly.

The `ExecutionContext.resources` boundary emits the ordinary resource DSL
chokepoint around each read. Its safe provider metadata is limited to scheme,
provider family, provider logical identity, and configured state. Physical
roots, remote endpoints, complete logical references, provider settings, and
content are intentionally outside both observability attributes and ordinary
framework-generated failure displays. A custom provider owns the safety of any
failure message it returns.

## Deferred Design

This design intentionally excludes caching, credentials, refresh, and SIE
migration. Those remain Phase 33 RR-06 work and later maintenance.
