# FileStore v1

Status: normative SPI contract; production provider integration remains pending.

## Identity and ownership

`org.goldenport.cncf.filestore.FileStore` is a lower, provider-neutral capability.
`FileStoreRef(store, key)` names a configured store and a provider-opaque physical
key. It is neither authority, a domain Entity/Material identity, nor a mandatory
URI. Slash and Unicode keys are valid, including S3 bucket/prefix/key mappings
selected by store configuration. A reference or stronger provider credential
never grants access: the owning Operation must establish entity/source
authorization before provider use. HTTP host admission is an additional runtime
boundary, not a replacement for that authorization.

## Values and operations

`FileStoreKind` is `File` or `Collection`. A collection is a grouping target;
an S3 prefix need not be a physical folder. Metadata contains `reference`,
display `name`, `mediaType`, optional `byteLength`, `parents`, `kind`, and optional
`version`. Unknown length and version remain `None`, never fabricated zero or
version. Version is an observation, not a guarantee of version-pinned reads.
`FileStoreContent` pairs a media type with a materialized `Bag`.

The capability exposes `name` and exactly four operations returning `ExecUowM`:

- `stat(reference)` returns metadata without reading content.
- `read(reference)` returns content without a hidden stat.
- `createCollection(parent, name)` creates a fresh grouping reference.
- `create(parent, name, mediaType, content)` creates a fresh object reference.

Callers may validate trusted expected parents, MIME and length after stat and
before read. Names are display labels, not identity, uniqueness, paths or
grants. Duplicate labels are permitted and creation never replaces an existing
object. Implementations construct native `UnitOfWorkOp` programs, without a
generic Eval or interpreter escape. Network programs pass normal host admission
before lazy driver acquisition; binary PUT is an External occurrence, including
equal repeated operations. HTTP operation displays redact paths, headers and
bodies, including sensitive resumable-session URIs.

## Content lifetime and write outcomes

A Bag is materialized under the library contract. Its caller owns backing
storage; the provider borrows it only for one interpreted write. The driver
closes each opened input/output stream exactly once on success or failure,
including output acquisition failure, and disconnects the connection. Returned
Bags remain readable after UoW completion; they are never live HTTP streams.
Native HTTP-backed original reads select `Property("http.response-body-mode",
"binary", None)` so text-classified media retains its original octets without
charset decoding or silent malformed-byte replacement. The driver owns byte
materialization and lifetime; FileStore returns the materialized Bag unchanged.
The Property name is case-insensitive. Absent mode means `auto`, preserving the
existing MIME/charset text-versus-binary behavior; a present selector must have
exactly one String value, exactly `auto` or `binary`. Empty, whitespace,
case-changed, unknown, non-string, null and duplicate declarations are invalid,
including equal duplicate values. There is no trimming, coercion or first-wins.
Validation precedes HTTP request and request-body stream access. Ordinary mode
validation throws `IOException("Invalid HTTP response body mode")`; the bounded
path retains its sanitized `IOException("Bounded HTTP exchange failed")`.
Binary mode retains actual Content-Type/charset, status and headers and uses
the same finite response buffers and limits. This representation choice grants
no host or source permission and does not change the product-wide default.
Do not eagerly copy or hash unbounded content into a temporary protocol encoding.
Providers must reject URL-backed input Bags to avoid a second unadmitted network
read, and should require known nonnegative size when their wire protocol needs
it. The generic HTTP driver may accept any caller Bag backend; the SPI cannot
invent universal runtime checks on behalf of providers.

Binary PUT streams known nonnegative lengths with fixed-length mode and unknown
lengths with an 8192-byte chunked mode; absent content uses zero-length mode.
It preserves connect/read limits, response ceiling, headers and redirect policy.
An explicitly requested total-timeout policy is rejected before connection/body
acquisition because bounded binary PUT is unsupported. Existing POST/PUT
behavior is retained. Old injected drivers remain source-compatible through a
concrete unsupported `putBag` default.

Every write error after submission may leave an unconfirmed remote write.
There is no automatic retry, replay, delete rollback, or assurance that nothing
was written. Consumers must keep incomplete package attempts out of accepted
Material state.

## Compatibility and non-goals

The existing managed BlobStore and BlobModel retain synchronous Consequence /
BinaryBag, Entity, digest and accessURL semantics. They are not migrated to Free
interpretation. This capability adds no list/delete/rename/move/copy/accessURL,
automatic digest, domain registry, or third provider registry. An optional thin
read-only FileSystemView may present this capability later; it is not implemented.
No actual S3 adapter is supplied or implied by S3-shaped references.

The Drive provider, OAuth configuration, real Studio source authorization and
Material materialization remain pending, as do cloud evidence, connected Step
independent review and final full validation. Fixtures are not production
adapters or cloud evidence.

## Executable specifications

- [FileStoreSpec](../../src/test/scala/org/goldenport/cncf/filestore/FileStoreSpec.scala)
- [HttpPutBagSpec](../../src/test/scala/org/goldenport/cncf/http/HttpPutBagSpec.scala)
- [HttpResponseBodyModeSpec](../../src/test/scala/org/goldenport/cncf/http/HttpResponseBodyModeSpec.scala)
- [HTTP admission](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpAdmissionSpec.scala)
- [Program planning](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkProgramPlanningSpec.scala)
