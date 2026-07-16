# Resource Reference DSL Foundation

Status: normative foundation contract

## Scope

This specification fixes the Phase 33 RR-01 value model and read-only
ExecutionContext boundary. It defines reference parsing, resource content,
text decoding, and failure ownership. It does not define URL policy, provider
selection, filesystem access, network access, URN namespace resolution, or
resource mutation.

## Reference Model

`ResourceReference` is an immutable logical reference. It is either:

- `Url`, an absolute non-URN URI; or
- `Urn`, a `urn:<nid>:<nss>` reference.

Parsing accepts only a non-empty, syntactically valid absolute URI. A URI with
the `urn` scheme must also have a non-empty NID and NSS. The NID is normalized
to lower case; the NSS remains opaque. A malformed, relative, or incomplete
reference returns a structured `Consequence` argument-format failure.

The reference is an identity and routing input only. It does not disclose a
provider implementation, local path, credential, cache location, or transport
handle. `urn:textus` semantics are deliberately not defined by this document;
they are introduced by RR-03.

## Read-only Resource Boundary

`ResourceAccess` is the sole CNCF resource-read boundary exposed through
`ExecutionContext.resources`.

- `read(reference)` returns immutable `ResourceContent`.
- `readText(reference, charset)` obtains content through `read` and decodes it
  with either the supplied charset or declared content charset, falling back to
  UTF-8.
- Resource content has its logical reference, immutable bytes, optional media
  type, and optional declared charset.

The model has no write, delete, list, scan, cache, credential, provider,
filesystem, network, or classpath operation. Components must use this boundary
for managed-resource reads rather than calling Java filesystem or network APIs.

## Text And Failure Semantics

Text decoding is strict: malformed or unmappable bytes are a structured
resource-invalid failure, not replacement-character output. The requested or
declared charset is part of decoding behavior; it is not inferred from a file
name or provider implementation.

`ResourceAccess` preserves provider-owned structured `Consequence` failures.
The default unconfigured access value returns a service-unavailable failure.
Future provider slices own missing-resource and policy-denial decisions:

- a configured provider reports absence as `resourceNotFound`;
- a policy layer reports denied access as a structured policy failure; and
- an unavailable resource service reports `serviceUnavailable`.

No raw resource content, provider settings, credentials, or physical location
may be inserted into generic failure messages by this boundary.

## ExecutionContext Injection

`ExecutionContext.CncfCore` owns the injected `ResourceAccess` value. Context
rebinding and scope changes preserve that value. Test/demonstration contexts use
the unconfigured default unless a caller explicitly injects another access
implementation. Production provider configuration is deferred to RR-02 through
RR-04.
