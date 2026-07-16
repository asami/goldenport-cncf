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
  -> provider/policy implementation (future RR-02 through RR-04)
```

The first slice deliberately installs an unavailable default. This makes a
missing runtime binding deterministic and prevents an accidental fallback to
ambient host files or network access.

`ResourceReference` distinguishes an absolute non-URN URI from a generic URN.
It does not assign a provider or policy to either form. In particular,
`urn:textus` receives its namespace grammar and resolver only in RR-03, while
non-Textus NIDs receive extension dispatch only in RR-04.

## Component Use

Component behavior uses the internal DSL helpers backed by
`ExecutionContext.resources`. The helpers retain structured `Consequence`
failures and do not log or trace content values. A component can request bytes
or strict text decoding; it cannot enumerate a location or mutate a resource.

## Deferred Design

This slice intentionally excludes provider SPI contracts, URL host/root policy,
filesystem and HTTPS implementations, Textus URN namespace configuration,
generic URN extension dispatch, resolution metrics, caching, credentials,
refresh, and SIE migration. Those are Phase 33 RR-02 through RR-06 work.
