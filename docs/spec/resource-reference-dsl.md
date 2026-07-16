# Resource Reference DSL Foundation

Status: normative foundation contract

## Scope

This specification fixes the Phase 33 RR-01 value model and read-only
ExecutionContext boundary, RR-02 URL provider and policy behavior, and RR-03
standard Textus URN resolution. It defines reference parsing, resource
content, text decoding, URL policy, Textus namespace resolution, and failure
ownership. It does not define generic external URN resolution or resource
mutation.

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
handle.

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
Configured URL providers own missing-resource and policy-denial decisions:

- a configured provider reports absence as `resourceNotFound`;
- a policy layer reports denied access as a structured policy failure; and
- an unavailable resource service reports `serviceUnavailable`.

No raw resource content, provider settings, credentials, or physical location
may be inserted into generic failure messages by this boundary.

## URL Provider And Policy Resolution

URL resolution is an execution-configuration concern. `ResourceAccess.url`
dispatches only to registered `UrlResourceProvider` values; an unregistered
scheme is rejected. The standard RR-02 binding installs providers only when
their corresponding policy is non-empty:

- `file:` uses `FileUrlResourceProvider` and is limited to configured,
  read-only roots; the resolved real path must remain under a configured real
  root, so a symlink cannot escape the policy boundary.
- `https:` uses `HttpsUrlResourceProvider` and the existing CNCF `HttpDriver`;
  it is limited to configured exact host names, rejects user-info URLs, and
  disables automatic redirects before transport execution.

Runtime configuration accepts comma-separated values through the usual aliases:

- `textus.resource.url.file.roots`
- `textus.resource.url.https.hosts`
- `textus.runtime.resource.url.*`
- `cncf.resource.url.*` and `cncf.runtime.resource.url.*`

There is no ambient URL fallback. Empty policy values install no provider and
therefore reject every URL. File absence is `resourceNotFound`; an unconfigured
scheme, host, root, or provider is a structured `resourceUnsupported` policy
failure. Provider failures must not report configured roots, host lists, or
resource contents.

## ExecutionContext Injection

`ExecutionContext.CncfCore` owns the injected `ResourceAccess` value. A context
created from a `GlobalRuntimeContext` binds RR-02 URL policy and RR-03 Textus
URN providers from that runtime configuration. Context rebinding and scope
changes preserve the injected value. Test/demonstration contexts use the
unconfigured default unless a caller explicitly injects another access
implementation.

## Standard Textus URN Resolution

The standard resource URN grammar is:

```text
urn:textus:<namespace>:<resource-id>
```

`namespace` is case-insensitive and canonicalized to lower case for provider
selection. It uses lower-case letters, digits, and hyphens, begins with a
letter, and is at most 32 characters. `resource-id` is a logical relative
identifier: it begins with an alphanumeric character and may use
alphanumerics, `.`, `_`, `-`, and `/`. Empty segments and `.` or `..` segments
are forbidden. Thus a logical identifier may organize content as
`documents/paper-1.json`, but it cannot select a physical path outside its
configured namespace root.

`TextusUrnResourceProvider` is the dedicated standard-provider SPI. Its input
is a parsed logical Textus URN, never a file path, remote endpoint, credential,
or provider implementation handle. The standard runtime installation maps
namespace bindings to read-only file-root providers through a comma-separated
configuration value:

```text
textus.resource.urn.textus.file-roots=book=/srv/textus/books,paper=/srv/textus/papers
```

The usual aliases are accepted: `textus.runtime.resource.urn.textus.file-roots`,
`cncf.resource.urn.textus.file-roots`, and
`cncf.runtime.resource.urn.textus.file-roots`. Each binding is exactly one
`<namespace>=<absolute filesystem path>` pair. Duplicate namespaces, invalid
namespaces, and relative roots fail during runtime configuration. A configured
file provider verifies the resolved real path remains below its configured real
root before reading.

An unknown namespace, invalid logical resource identifier, absent resource, or
policy denial remains a structured `Consequence` failure. Generic URN providers
cannot select or shadow `textus`; that extension point is RR-04. This resource
URN layer is separate from the existing Blob/entity semantic URN facilities:
RR-03 neither changes their identity model nor uses them as resource-content
providers.
