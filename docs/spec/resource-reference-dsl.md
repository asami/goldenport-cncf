# Resource Reference DSL Foundation

Status: normative foundation contract

## Scope

This specification fixes the Phase 33 RR-01 value model and read-only
ExecutionContext boundary, RR-02 URL provider and policy behavior, and RR-03
standard Textus URN resolution. It defines reference parsing, resource
content, text decoding, URL policy, Textus namespace resolution, and failure
ownership. It does not define resource mutation.

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

`ResourceReference.resolveC(base, relativepath)` derives a safe child
reference for a resource collection. `relativepath` must be non-empty,
relative, and free of empty, `.`, `..`, and backslash path segments. URL bases
use URI child resolution; standard `urn:textus` bases append the child to the
logical `resource-id`. Generic external URN NSS values remain opaque, so child
resolution for a non-Textus URN is rejected rather than guessed.

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

## Ordinary Single-resource Read Limits

Ordinary `read(reference)` and `readText(reference, charset)` acquire complete
content under one runtime-owned `ResourceReadLimits(maximumBytes)` policy.
Components retain their logical references and existing method signatures;
they neither choose a safety variant nor pass a per-call maximum:

```scala
val content = read_resource(reference)
val text = read_resource_text(reference)
```

The default maximum is **1,048,576 bytes (1 MiB)**. The admitted range is
**1 through 268,435,456 bytes (256 MiB)**, inclusive. There is no unlimited or
zero sentinel. Invalid direct construction fails before provider use;
`ResourceReadLimits.createC` provides structured construction validation.
Runtime configuration accepts an exact positive integer in that range through:

- `textus.resource.read.max-bytes`;
- `textus.runtime.resource.read.max-bytes`;
- `cncf.resource.read.max-bytes`; and
- `cncf.runtime.resource.read.max-bytes`.

A larger selected limit permits more input; it is not a peak-memory bound.
Immutable byte, text and JSON values may require more memory than the input size.

Malformed, zero, negative, excessive and overflowing values refuse runtime
construction. `RuntimeConfig.Settings`, storage assembly and the normal
`GlobalRuntimeContext` -> `ExecutionContext` binding retain the actual policy.
Runtime `ResourceAccess` factories pass that same selected value to the
existing URL, dedicated Textus URN and external NID routes. Existing factory
arities select the finite default; explicit runtime assembly overloads accept
the runtime policy. Test profiles may explicitly select `readLimits`; they do
not override production settings.

The runtime-only provider acquisition SPI is:

```scala
// UrlResourceProvider
def read(reference: ResourceReference.Url, limits: ResourceReadLimits): Consequence[ResourceContent]
// TextusUrnResourceProvider
def read(reference: TextusUrnReference, limits: ResourceReadLimits): Consequence[ResourceContent]
// UrnResourceProvider
def read(reference: ResourceReference.Urn, limits: ResourceReadLimits): Consequence[ResourceContent]
// StaticWebUrlResourceProvider
def readStaticWeb(reference: ResourceReference.Url, limits: ResourceReadLimits): Consequence[ResourceContent]
```

Configured providers are trusted framework extension code responsible for
honoring the supplied cap during acquisition. This contract does not contain
malicious plugin code. Extensions must rebuild for the changed SPI; there is
no old single-argument provider fallback or binary compatibility claim.
Reflection NID bindings, public no-argument provider constructors and reserved
Textus routing remain unchanged. Explicit test-only `ResourceAccess` injection
remains a test capability, never a production fallback.

The limit counts encoded bytes, including a final newline. Equality succeeds;
overflow returns only a structured failure, never truncated or partial content.
The package-private acquisition helper reads at most `maximumBytes + 1` bytes
from an owned stream and closes it on success, overflow and I/O exception.
It detects excess independently of metadata or source growth. Acquiring a whole
resource and checking size afterward is forbidden. In-memory providers count
UTF-8 bytes before allocating an encoded copy that could exceed the cap.

Standard Textus and file URL providers require normalized and real-root
containment. They reject symbolic links throughout configured-root and target
ancestor chains, including final links and in-root links, require ordinary
regular files with `NOFOLLOW_LINKS`, and open only with `READ` plus
`NOFOLLOW_LINKS`. This is cooperative path admission; it does not promise an
adversarial race-proof snapshot, a writable lock or mutation authority.

Ordinary HTTPS passes `http.max-response-bytes` equal to the runtime cap into
the existing `HttpDriver` before transport acquisition, keeps exact-host and
user-info denial and disables redirects. Response-bag consumption uses the
same finite stream helper at the provider boundary. Static Web keeps
`WebTargetAdmission`, public-network-only access, fixed timeouts and required
Content-Type. Its effective maximum is the smaller of the runtime maximum and
its existing 1,048,576-byte ceiling. No new host, protocol or provider-discovery
route is introduced.

`readText` calls ordinary `read` once, then uses unchanged strict
`ResourceContent.textC` decoding. Malformed UTF-8 can succeed as complete bytes
and fail as text without replacement characters. One observed ordinary call
emits one `resource` / `read` chokepoint and one `Resolve` phase; text does not
add another read. Attributes contain only safe scheme, provider family,
logical provider identity and configured state. Generic failures and diagnostic
displays omit cap settings, host roots, full URL/NSS and private content.
Custom providers own the privacy of their returned failures.

This is partial producer integration for sm-workflow Phase 6 / P6-SBX03 under
the existing [File Workspace handoff](../../../sm-workflow/docs/journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md)
and [security review profile](../../../sm-workflow/docs/phase/phase-6-security-review-profile.md).
It establishes no CNCF Phase 33/107/110 completion or deployed-artifact claim.
File Workspace writes, the primary five-purpose migration, eight remaining
main and launcher retirements, and installed command acceptance remain
unresolved. Read access grants no file writes, Git, SBT, remote or process
authority. The existing resource effect composition and separate tree contract
remain unchanged; this adds no resource UoW algebra.

Executable contracts:
[`ResourceReadLimitsSpec`](../../src/test/scala/org/goldenport/cncf/resource/ResourceReadLimitsSpec.scala),
[`ResourceAccessBoundedReadSpec`](../../src/test/scala/org/goldenport/cncf/resource/ResourceAccessBoundedReadSpec.scala)
(the historical file/class identity is retained), and the config, URL, Textus,
external URN, component DSL and builtin-tool specifications. Historical bounded
API qualification does not qualify this changed ordinary API.

| Contract | Given | When | Then |
| --- | --- | --- | --- |
| BR1 | Canonical namespace files and generated Unicode content | Ordinary component reads use runtime assembly | Exact bytes, identity, strict text and final newline survive with one safe observation |
| BR2 | Files at and above runtime-selected caps and generated lengths | Ordinary reads acquire complete content | Equality succeeds; excess has no partial value |
| BR3 | Malformed UTF-8 within the cap | Ordinary acquisition precedes strict decoding | Complete bytes survive and text fails structurally |
| BR4 | Final, ancestor and configured-root links | Ordinary reads attempt linked paths | All links are denied without mutation |
| BR5 | Unknown namespace, traversal, missing and nonregular inputs | Standard ordinary dispatch consumes each request | Structured failures omit roots, full references and contents |
| BR6 | Three explicit in-memory routes and Unicode values at and above one runtime cap | Ordinary composite reads dispatch by existing URL, Textus and external routes | Every route returns complete equality content or structured overflow |
| BR7 | A cap-aware provider returning success or failure | Observed ordinary DSL forwards one request | The same runtime cap and consequence cross exactly one read/Resolve path |

## Separate Resource Tree Contract

`ResourceReference` is a single-resource identity and does not model a
directory or collection. `ResourceTreeReference` and `ResourceTreeAccess` are
the separate CNCF capability for named, read-only tree snapshots. A snapshot is
runtime-admitted, immutable, deterministically ordered, and bounded by depth,
entry count, per-file byte size, and aggregate byte size.

The public snapshot contains logical relative entries only. It MUST NOT expose
a physical root, `Path`, symbolic-link target, or host traversal capability.
Unknown trees, unsafe entries, symbolic links in the local provider, and limit
violations fail before a component can use the tree.

For an admitted external-tool invocation, a component may construct
`ProcessExecutionResourceTreeInput` only through
`createC(snapshot, workAreaRelativeTarget, requestedLimits)`. The snapshot
must already be admitted; requested limits may only tighten it. Program and
grant policy revalidate that input before the UnitOfWork-owned Process Execution
WorkArea materializes it. This does not add a general directory read/list API.

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
  root, and every symlink in the admitted root/target ancestor chains is rejected.
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
file provider verifies normalized and real-root containment and rejects every
symbolic link in the configured-root and target ancestor chains before finite acquisition.

An unknown namespace, invalid logical resource identifier, absent resource, or
policy denial remains a structured `Consequence` failure. Generic URN providers
cannot select or shadow `textus`. This resource URN layer is separate from the
existing Blob/entity semantic URN facilities: RR-03 neither changes their
identity model nor uses them as resource-content providers.

## Generic External URN Resolution

`UrnResourceProvider` is the extension SPI for explicitly configured,
non-Textus `urn:<nid>:<nss>` references. Its input is the parsed generic URN
reference, and it returns `ResourceContent` or a normal structured
`Consequence` failure. It does not expose a provider-selection API to component
code.

External bindings are explicit execution configuration, using comma-separated
`<nid>=<provider-class>` entries:

```text
textus.resource.urn.providers=example=org.example.catalog.ExampleUrnResourceProvider
```

The usual aliases are `textus.runtime.resource.urn.providers`,
`cncf.resource.urn.providers`, and `cncf.runtime.resource.urn.providers`. A
provider class must implement `UrnResourceProvider`, have a public no-argument
constructor, and report the same normalized NID as its configured binding.
Malformed NIDs, duplicate NIDs, provider-class construction failures, and NID
mismatches fail runtime configuration deterministically.

`textus` is reserved: neither a generic configuration binding nor a generic
provider can receive a `urn:textus:` request. Runtime dispatch always resolves
Textus URNs through `TextusUrnResourceProvider` first. Empty external-provider
configuration is the default, so every non-Textus NID remains rejected unless
an operator explicitly installs its provider.

## Deterministic Test Profiles and Resolution Diagnostics

`ResourceAccessTestProfile` is the explicit executable-specification and
component-integration-test surface for resource reads. It composes
`InMemoryUrlResourceProvider`, `InMemoryTextusUrnResourceProvider`, and
`InMemoryUrnResourceProvider` without opening host files or network
connections. Tests install it only through
`ExecutionContext.withResourceAccessTestProfile`; it is not a production
runtime assembly mechanism.

Every read obtained through `ExecutionContext.resources` passes the resource
DSL chokepoint. The resulting diagnostic identifies only the resource scheme
and selected provider family/identity. It MUST NOT include the full URL, URN
NSS, configured root, provider settings, or resource content. A normal
framework-generated resource failure likewise remains a structured
`Consequence` and does not render the requested logical reference. A custom
provider remains responsible for the safety of its own failure messages.

## Actual-context text preflight and checked CLI output

`ExecutionContext.preflightResourceTextC(text)` is a pure check of the admitted
runtime's resource policy. `resourceTextPreflightScopeC` captures that same
immutable policy once for several exact serializer surfaces. Neither grants
File/storage authority, performs I/O, accepts a caller maximum, nor falls back
to the process's current global/default context. Missing admitted policy is a
typed unavailable failure.

`ResourceTextPreflight.Accepted(byteSize, maximumBytes)` reports an exact UTF-8
size. `SizeExceeded(observedBytesAtLeast, maximumBytes)` reports the bounded
prefix at the first overflow, not an exact rejected payload size. Null returns
`InvalidText`; malformed surrogate text within the cap is invalid. File text
writes use this same predicate for replacement and expected text: replacement
byte counting comes first, overflow wins over later invalid Unicode, and only
accepted text is encoded. Existing test-resource replacement semantics and the
ordinary resource limits above remain unchanged. Preflight success does not
replace File path, owner, expected-value, current-state or write admission.

The canonical Subsystem Boolean `textus.output.resource-preflight` has no new
aliases. Absent/false retains legacy formatting. When enabled on the normal
formatted CLI API, its value is decoded with the catalog Boolean codec from
the actual admitted `ExecutionContext.resolvedParameters`, retaining normal
provenance. JSON/data/no-inline-debug must be explicitly selected by request
or actual resolved parameters; unsupported selection, malformed Boolean or
missing policy fails before the component is invoked. No global/default
format/shape resolver is used by this checked route.

`OperationResponseFormatter.selectDataJsonC` creates the owner-only typed
selection. `prepareDataJsonC` uses the captured actual runtime Record context,
checked `RecordEncoder.jsonC`, and the exact final body plus platform print
separator. It returns that same `Response.Json` body for the existing printer,
without another formatting pass. Raw unformatted operation APIs remain raw;
they are not a checked transport. HTTP, envelope, inline debugging and scalar
responses are outside this finite checked output contract.

Known selection failure is pre-effect. Later encoder, metadata or output-limit
failure may follow a real acknowledged native effect: it does not roll back,
relabel or replay that effect. The API returns a bounded typed failure/nonzero
transport result. A fixed small error Record is checked once through the same
formatter; if it cannot fit, no valid-JSON-success claim is made. Oversized
response bodies, captures, findings and bindings are not copied into errors.

Native SQL Records belong to managed datastore admission, not File acquisition.
An actual terminal SQL row may exceed the selected File cap. Failed or
uncertain terminal persistence leaves its prior intent/UNKNOWN intact; output
inspection, collection or a missing result does not authorize another effect.

`ExecutionContext.checkedDataJsonTextC(response)` exposes a pure exact transport
preview to an application already admitted through the normal formatted-call
route. It returns the actual final JSON body plus the captured platform print
separator. The caller supplies only its real `OperationResponse`, never a mode,
request, formatting context, metadata, policy limit or replacement scope.
Raw/nonformatted, disabled and unadmitted contexts return typed unavailable.

At normal checked admission CNCF attaches an immutable private serializer scope
and passes that returned context into the component. The scope retains only the
actual request/mode, runtime Record context, typed selection, resource policy
and separator; it owns no reusable ExecutionContext, storage lease or process.
Preview reads actual current runtime execution/response metadata internally.
Final formatting uses that same serializer scope with actual completed metadata.
Later metadata changes may therefore reject a previously valid preview; a preview
does not certify future completion or permit replay after a post-effect failure.
