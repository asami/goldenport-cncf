# Optional reserved direct FileStore upload

Decision: 2026-10-25. Status: provider foundation authoring; validation and live
acceptance pending. This capability is additive to [direct upload](direct-file-upload-v1.md).

`ReservedDirectUploadFileStore extends DirectUploadFileStore` exposes:

```scala
prepareReservedDirectUpload(parent: FileStoreRef, name: String,
  mediaType: MimeType, byteLength: Long): ExecUowM[DirectFileUploadReservation]
inspectReservedDirectUpload(reservation: DirectFileUploadReservation): ExecUowM[DirectFileUploadStatus]
```

The final top-level `DirectFileUploadReservation(upload, fileReference,
storageRoot)` retains a sensitive `DirectFileUpload`, the server-generated new
file reference and an optional observed provider storage root. Its display is
exactly `DirectFileUploadReservation(<redacted>)`. Restored values are untrusted;
neither a reservation nor a storage root establishes caller, folder or domain
authorization. The owning server retains the binding privately. No server token
or general Google credential is returned to a transfer client.

## Google preparation

Before credentials or HTTP, validate the selected-store safe parent ID, name,
non-folder MIME and known nonnegative length under the existing provider rules.
Acquire native managed authorization once for this complete sequence:

1. GET the exact parent's metadata using stat's endpoint, fields and parser.
   Require the exact reference and Collection kind; retain its `storageRoot`.
2. GET `https://www.googleapis.com/drive/v3/files/generateIds?count=1&space=drive&type=files`.
   A 200 JSON object must contain exactly one safe opaque string in `ids`,
   distinct from the parent and observed root. If supplied, `space` is exactly
   `drive` and `kind` is exactly `drive#generatedIds`. Null, coercion, invalid,
   duplicate and extra IDs fail with the fixed metadata diagnostic. No session
   POST follows an invalid parent or ID response.
3. POST the existing native files.create resumable endpoint with
   `supportsAllDrives=true`, MIME/length headers, and metadata
   `{id:generatedId,name,mimeType,parents:[parent]}`. Admit Location with the
   existing strict HTTPS session rules. Return the retained generated identity,
   session and observed root.

Metadata requests use the existing 65536-byte response ceiling, managed server
authorization and native HTTP admission. Fixed, refreshed and service-account
credentials retain their current lifecycle. Preparation sends no original bytes,
downloads no content, accepts no caller-provided file ID, and performs no update,
retry or rollback. The existing unreserved route remains POST-only and omits `id`.

The primary [files.generateIds reference](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/generateIds)
defines this endpoint; the [Drive upload guide](https://developers.google.com/workspace/drive/api/guides/manage-uploads)
describes pre-generated IDs for binary creation. This supplies no TTL or
revocation promise.

## Inspection and independent completion

Before credentials or HTTP, require nonnull reservation/upload, valid existing
protocol/name/non-folder MIME/size/session URI, and safe references in the same
selected store. The reserved file differs from parent and optional root. A
nonnull Option represents the optional root; present null/invalid references fail.

Query only the retained existing session through the native empty binary PUT
with `Content-Range: bytes */<length>`. Pending returns the provider-confirmed
offset without metadata GET or new POST. A completed session must report the
exact reserved file reference. Then independently GET that file using managed
server authorization. Require exact reference, name, parent, File kind, MIME,
length and equality of observed `storageRoot`, including absence. Completed
returns this independent metadata, including optional typed SHA-256; session
metadata alone cannot establish completion.

On session 404, GET the known reserved file. Matching metadata reconciles to
Completed; an actual file 404 retains Expired. Other provider, authorization,
admission and transport errors retain the existing fixed failure taxonomy.
A file 404 after a completed session is failure, never completion or another
create. Foreign/malformed/trashed metadata cannot reconcile successfully.

My Drive's absent root and a Shared Drive's observed root remain distinct. The
[File resource](https://developers.google.com/workspace/drive/api/reference/rest/v3/files)
defines output-only `driveId` and binary `sha256Checksum` when available.
Missing checksum remains `None`. This capability performs no expected SHA
comparison and does not mark TKL verified or registered. Those gates, persistence,
queue policy and client UI belong to their owning future changes. No management
hash, directory authority, deletion, update, retry or original download is added.

## Executable specifications

- [GoogleDriveReservedUploadSpec](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveReservedUploadSpec.scala)
- [Existing direct route](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveDirectUploadSpec.scala)
- [Checksum observations](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveContentDigestSpec.scala)
- [Service-account lifecycle](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveServiceAccountSpec.scala)
