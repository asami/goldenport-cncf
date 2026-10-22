# Google Drive FileStore v1

Status: normative adapter contract. Actual account connection, cloud acceptance,
Studio application image/audio acceptance, connected Step review and full
validation remain open. The framework specifications use synthetic credentials
and a counted driver under the actual native interpreter.

## Capability and authority

`org.goldenport.cncf.filestore.GoogleDriveFileStore` implements the four-operation
[FileStore v1](file-store-v1.md) capability. It constructs native
`UnitOfWorkOp.HttpGet`, `HttpPost` and `HttpPutBag` programs in `ExecUowM`.
It does not create another interpreter or perform direct network access.

`GoogleDriveFileStore.createC(name: String, credentials: GoogleDriveCredentials)`
returns `Consequence[GoogleDriveFileStore]`. Construction is private. A logical
name matches `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` and credentials are required.
Invalid configuration is `argumentInvalid("Invalid Google Drive file store
configuration")`. The store display contains its logical name only.

Every reference must have exactly this configured store name and a Drive ID
matching `[A-Za-z0-9_-]{1,256}`. Invalid references fail before credentials or HTTP
with `argumentInvalid("Invalid Google Drive file reference")`. This rule belongs
to this provider; provider-neutral references continue to support other opaque
key spaces, including S3 slash/Unicode keys.

A reference, store or token never grants owning entity/source access. The
owning Operation must authorize its trusted original/material reference before
using stronger outbound credentials. Native runtime origin/method admission is
an additional independent boundary. Action properties and caller URLs cannot
override credentials, endpoints or host grants.

## Outbound credential lifecycle

`GoogleDriveCredentials` is a final, privately constructed non-product with no
public token getter, copy or credential fields. Its only factories are:

```scala
accessTokenC(accessToken: String): Consequence[GoogleDriveCredentials]
refreshTokenC(clientId: String, clientSecret: Option[String],
  refreshToken: String): Consequence[GoogleDriveCredentials]
```

Factories are pure and read no environment, file, CLI or network source.
Fixed tokens are nonempty ASCII RFC6750 b64tokens of at most 16384 characters,
without whitespace, control or comma. Refresh/client values are nonempty
printable ASCII without whitespace/control, at most 16384 characters; a supplied
optional secret must also be valid. Invalid input has the fixed
`argumentInvalid("Invalid Google Drive credential configuration")` failure.
The credential display is exactly `GoogleDriveCredentials(<redacted>)`.

The package-only authorization program supplies a Bearer header. Fixed mode
performs no token HTTP exchange; its host owns token expiry and replacement.
Refresh mode performs exactly one native POST per logical store operation to
`https://oauth2.googleapis.com/token`. Each UTF8 form value is percent-encoded
in an `application/x-www-form-urlencoded` body containing `client_id`, optional
`client_secret`, `refresh_token` and `grant_type=refresh_token`. Credentials never
enter a URL query or request log. OAuth POST requires a separate runtime grant.
The ordinary `http.max-response-bytes` Property requests a 16384-byte ceiling;
the runtime may tighten it.

Only a 200 `application/json` object with a valid `access_token`, case-insensitive
Bearer `token_type`, and numeric integral positive `expires_in` is accepted.
Missing, null, malformed or coerced fields are rejected. Status mapping is:

| Refresh response | Structured failure and fixed message |
| --- | --- |
| 400, 401, 403 | `securityPermissionDenied`: Google Drive credential refresh was denied |
| 429 or 5xx | `serviceUnavailable`: Google Drive credential refresh is unavailable |
| Other status or malformed success | `resourceInvalid`: Google Drive credential response is invalid |

Each logical operation gets a fresh access token once. A file create shares
that one token across initiation and completion. There is no credential cache,
persistence, revocation, browser consent, callback, scope expansion, retry or
expired-token replay. A response `refresh_token` never replaces configured
refresh material. These are outbound credentials, not an ingress
`AuthenticationProvider`.

## Metadata and content reads

The metadata field selection is exactly
`id,name,mimeType,size,parents,trashed,version`. `stat` performs one GET:

```text
https://www.googleapis.com/drive/v3/files/{id}?supportsAllDrives=true&fields=id,name,mimeType,size,parents,trashed,version
```

It sends Bearer authorization and `Accept: application/json`, requesting a
65536-byte ceiling via the normal Property. Only a 200 JSON object is accepted.
Its `id` must exactly match the requested ID; `name` is a nonempty string with
no ISO control characters; `mimeType` is a valid MIME token pair; and `trashed`
must be the Boolean false. `application/vnd.google-apps.folder` means Collection;
all other valid MIME types mean File.

Missing parents become an empty Vector. Present parents must be an array whose
every member is a valid Drive ID string, without coercion. Each parent becomes a
reference in the same logical store. Missing size/version become `None`.
Present size is a canonical nonnegative decimal string fitting Long, including
zero. Present version is a canonical nonnegative decimal string preserved
verbatim as an observation, without an invented value or version-pinned read
promise. JSON numbers, nulls, leading zeroes, signs, spaces and fractions do not
meet these string contracts. Malformed metadata has the fixed
`resourceInvalid("Google Drive file metadata is invalid")` failure.

`read` performs one GET, with no hidden stat:

```text
https://www.googleapis.com/drive/v3/files/{id}?alt=media&supportsAllDrives=true
```

It sends Bearer authorization and `Accept: application/octet-stream`, selecting
`Property("http.response-body-mode", "binary", None)` on this media GET only.
Accept does not force the peer's Content-Type. Native binary representation
preserves exact returned octets even for text/plain, SVG or JSON and retains
actual MIME/charset, headers and status. It performs no text decoding/reencoding
or silent malformed-byte replacement. The exact selector validation and unchanged
`auto` default are specified in [FileStore v1](file-store-v1.md). The ordinary
and total-timeout-bounded driver paths both materialize the original bytes under
their existing ceilings, redirect controls and lifecycle. The host
response byte ceiling applies. Only 200 produces
`FileStoreContent(response.mime, response.bag)` with the identical materialized
Bag. There is no eager decoding, hash, copy or live HTTP connection retained.
Callers can stat first, compare their trusted expected parent/MIME/length, and
reject a mismatch before asking for the media read.
Metadata stat, OAuth refresh, collection creation, resumable initiation and
completion retain ordinary JSON TextBag materialization; none selects binary
response mode. Metadata retains its 65536-byte request ceiling, refresh its
16384-byte ceiling, and the runtime may tighten either. This Property grants no
HTTP host or owning entity/source authority and adds no hidden stat or retry.

## Create-only writes and resource ownership

Creation labels are nonempty, at most 1024 UTF16 code units, with no ISO control
characters. Slash and Unicode remain display-label characters; there is no path
normalization, label lookup or label uniqueness assumption.

`createCollection` performs one metadata POST to:

```text
https://www.googleapis.com/drive/v3/files?supportsAllDrives=true&fields=id,name,mimeType,size,parents,trashed,version
```

The JSON body is exactly `{name, mimeType, parents:[parent.key]}`, with folder
MIME. It has no requested object ID or update operation. Headers include Bearer,
`Content-Type: application/json; charset=UTF-8` and `Accept: application/json`;
the response ceiling is 65536. A 200/201 full metadata response must confirm a
valid provider-issued ID, requested name/MIME/kind, the exact parent among its
parents and `trashed:false`.

`create` requires a valid non-folder MIME token, a Bag with non-URL backend and
`metadata.size=Some(nonnegative Long)`, before credentials or HTTP. Invalid
creation input has the fixed `argumentInvalid("Invalid Google Drive file
creation input")` failure. A URL-backed Bag is never opened. The caller owns
materialized backing storage, and the native driver borrows its input stream
only during the admitted write and closes it on success/failure.

The first request is one native metadata POST to:

```text
https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true&fields=id,name,mimeType,size,parents,trashed,version
```

It uses the same three-field metadata body and JSON/Accept/Bearer headers plus
`X-Upload-Content-Type` and `X-Upload-Content-Length`. Only 200 with an admitted
Location permits completion. Location must be an absolute hierarchical HTTPS
URI of at most 16384 characters, exact host `www.googleapis.com`, absent
user-info/fragment, no backslash/control, absent or default 443 port and exact
raw path `/upload/drive/v3/files`. Its decoded query contains exactly one
nonempty `upload_id` and exactly one `uploadType=resumable`; ordinary other
query parameters may remain. Credentials are never added. A different origin,
same-origin arbitrary path or redirect is not accepted. Invalid/missing Location
has `resourceInvalid("Google Drive upload session is invalid; creation is
unconfirmed")`, without echoing that Location.

The second request is one native `HttpPutBag` to the admitted Location, with the
same Bearer token, exact same Bag, requested MIME Content-Type and JSON Accept.
Its completion ceiling is 65536. The driver chooses streaming length from the
Bag; the adapter does not copy content, make a text/array body or manually set
Content-Length. A 200/201 full metadata response must confirm the requested
name/MIME/File kind/parent and byte length exactly equal to the input length.
Zero bytes and arbitrary binary octets are supported. The two single-shot
resumable requests avoid the multipart 5 MiB constraint for large audio; this
contract supplies no multi-chunk resume/retry implementation.

Duplicate labels yield new provider references and never overwrite earlier
bytes. There is no update, deletion, permission change, replacement, label
lookup, retry, status probe, resume registry or destructive rollback. 308 is
not successful completion.

## Failure and disclosure contract

| Drive response | Reads | Creates at every stage |
| --- | --- | --- |
| 401, 403 | `securityPermissionDenied`: Google Drive file access was denied | Same |
| 404 | `resourceNotFound`: Google Drive file was not found | Same |
| 429, 5xx | `serviceUnavailable`: Google Drive file service is unavailable | `serviceUnavailable`: Google Drive file creation is unconfirmed; do not retry automatically |
| Other unsuccessful status | `resourceInvalid`: Google Drive file response is invalid | `resourceInvalid`: Google Drive file creation is unconfirmed; do not retry automatically |
| Malformed successful write metadata | Not applicable | Same unconfirmed `resourceInvalid` |

Provider bodies, raw IDs and URLs are never included in fixed diagnostics.
Native interpreter admission/transport Conclusions propagate unchanged rather
than being collapsed into provider mappings. Existing native operation displays,
enabled calltree summaries, `ConclusionDisclosure.http` standard/full and
`ConclusionDisclosure.log` must exclude credentials, provider response bodies
and sensitive upload-session URIs while retaining native taxonomy/status.

ANY write failure after submission can be unconfirmed, including a native
failure whose taxonomy does not mention creation. Consumers must retain that
uncertainty and keep incomplete attempts out of accepted Material state. They
must not automatically submit a new object, replay, probe status, or delete as
rollback on this library's behalf.

The existing production HTTP driver represents statuses with
`HttpStatus.fromInt(code).getOrElse(HttpStatus.InternalServerError)`. Unsupported
codes such as the currently unrepresented 429 therefore arrive as 500; specs
use that actual API and disclose this limitation instead of inventing an HTTP
status value. The public API represents 308 as a redirection with its original
code; it is an invalid, unconfirmed create completion. Both outcomes remain
unaccepted and unreplayed.

S3 adapter, VFS, actual server credential provisioning and actual cloud account
changes are not supplied by this adapter. Managed BlobStore semantics remain
unchanged.

## Executable specifications

- [GoogleDriveCredentialsSpec](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveCredentialsSpec.scala)
- [GoogleDriveFileStoreSpec](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveFileStoreSpec.scala)
- [FileStoreSpec](../../src/test/scala/org/goldenport/cncf/filestore/FileStoreSpec.scala)
- [Native binary PUT](../../src/test/scala/org/goldenport/cncf/http/HttpPutBagSpec.scala)
- [Native response bytes](../../src/test/scala/org/goldenport/cncf/http/HttpResponseBodyModeSpec.scala)
- [Native HTTP admission](../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkHttpAdmissionSpec.scala)
