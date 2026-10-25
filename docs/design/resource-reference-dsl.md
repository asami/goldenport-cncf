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

## Runtime-limited Ordinary Acquisition

The ordinary capability retains its component API and existing dispatcher:

```text
Component read_resource / read_resource_text(logical reference)
  -> ExecutionContext.resources
  -> observed ResourceAccess.read / readText
  -> existing URL / Textus / external URN route
  -> provider.read(reference, runtime ResourceReadLimits)
```

Runtime configuration owns one `ResourceReadLimits` policy, defaulting to
1,048,576 bytes (1 MiB) with an admitted inclusive range of
1..268,435,456 bytes (256 MiB). Only
`textus.resource.read.max-bytes`, its `textus.runtime` spelling and the matching
`cncf` aliases select it; invalid numeric settings fail construction. Settings,
storage assembly and the normal global-runtime context pass the actual policy
into `ResourceAccess.standard`. Existing factory arities explicitly delegate to
finite defaults; explicit runtime overloads carry the configured policy.
Components own only logical references and charset choices. There is no
component safety variant or per-call maximum argument.

A larger selected limit permits more input; it is not a peak-memory bound.
Immutable byte, text and JSON values may require more memory than the input size.

Provider acquisition takes `ResourceReadLimits` in the existing URL, Textus and
external URN SPIs, including `readStaticWeb`. Every selected route receives the
same runtime-owned policy. Configured providers are trusted extensions and must
honor it while acquiring content; the policy cannot contain malicious plugin
code. Existing NID/no-argument-constructor configuration and reserved Textus
routing remain intact. Removed single-argument provider acquisition has no
compatibility fallback. Rebuilt extension artifacts are required; there is no
binary compatibility or deployed-producer qualification claim.

The shared package-private stream helper uses finite
`readNBytes(maximumBytes + 1)` acquisition and closes its stream on success,
overflow and exceptions. Only complete content within the cap becomes immutable
`ResourceContent`. Byte-count equality succeeds and overflow returns no partial
value. The bound catches metadata understatement and source growth without
relying on a file-size precheck. In-memory providers count actual UTF-8 byte
widths before allocating a new encoded copy, retaining complete preowned values
only when admitted.

Both standard local-file routes require normalized and real-root containment,
reject every symbolic link in configured-root and target ancestor chains,
require ordinary regular files with `NOFOLLOW_LINKS`, and open with `READ` plus
`NOFOLLOW_LINKS`. This is cooperative path admission without writable locks,
identity hashes, retries or an adversarial race-proof snapshot promise.

Ordinary HTTPS passes the runtime cap as `http.max-response-bytes` to the
existing transport and bounds response-bag acquisition with the shared helper.
Exact-host/user-info denial and no redirects remain. Static Web additionally
retains public-target admission, public-network-only transport, fixed timeouts
and required Content-Type; its effective cap is
`min(runtime maximumBytes, 1048576)`. No HTTP-driver redesign or additional
provider discovery is required.

Strict text interpretation remains with `ResourceContent.textC`. `readText`
reuses exactly one ordinary observed `read`: malformed UTF-8 may be admitted as
complete bytes while strict decoding fails. The observation adapter emits one
`resource` / `read` chokepoint and one `Resolve` phase, forwarding the real
provider consequence once. Safe attributes include only scheme, provider
family/logical identity and configured state. Generic failure displays exclude
cap settings, host roots, full URL/NSS and content; custom providers own their
failure privacy.

Executable contracts are the retained `ResourceAccessBoundedReadSpec` identity,
new `ResourceReadLimitsSpec`, and ordinary config/provider/DSL/tool specs,
linked in the [paired specification](../spec/resource-reference-dsl.md).
Historical bounded-API results remain historical; they do not qualify the
changed ordinary API. Test profiles explicitly select finite limits and remain
separate from production assembly.

This is partial producer integration for sm-workflow Phase 6 / P6-SBX03, using
the existing [File Workspace handoff](../../../sm-workflow/docs/journal/2026/10/2026-10-15-phase-6-file-workspace-dsl-handoff.md)
and [security review profile](../../../sm-workflow/docs/phase/phase-6-security-review-profile.md).
It neither activates nor completes CNCF Phase 33/107/110 and makes no deployed
artifact or whole workflow acceptance claim. File Workspace operations, the
primary five-purpose migration, eight remaining main/launcher retirements and
installed command acceptance remain unresolved. Reading grants no file-write,
Git, SBT, remote or process authority. Existing effect composition, the tree
contract and current-UoW Git/SBT execution remain separate; no new resource UoW
algebra is introduced.

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
