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

## Deferred Design

This design intentionally excludes generic URN extension dispatch, resolution
metrics, caching, credentials, refresh, and SIE migration. Those remain Phase
33 RR-04 through RR-06 work.
