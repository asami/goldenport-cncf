# Optional direct FileStore upload

Decision: 2026-10-25. Status: implementation contract; live provider acceptance pending.

`DirectUploadFileStore` is an additive capability beside FileStore. A consumer
tests for this capability and its supported protocol before choosing direct
transfer. A store without it continues to support server-mediated `create`.
Permission refusal, network failure and an unknown write result do not establish
capability absence and never authorize a different-route resend.

`prepareDirectUpload(parent, name, mediaType, byteLength)` initiates an upload
without reading or sending original bytes. It returns a `DirectFileUpload`
binding the store, parent, label, MIME, length, protocol and provider upload URI.
The URI is sensitive per-upload authority: persist it only in the owning
server's private delivery state, disclose only to the authorized transfer client,
and exclude it from displays, status and ordinary logs. No server token, private
key or general Google credential is returned. The owning Operation establishes
caller authorization before either capability is invoked.

`inspectDirectUpload(upload)` validates the retained binding before credentials
or network access and queries the existing provider session. It returns Pending
with the provider-confirmed next byte offset, Completed with metadata checked
against that binding, or Expired on an actual provider 404. It never starts a
new session, resends bytes, deletes a file or invents a local expiry time.

GoogleDriveFileStore advertises `googleDriveResumable`. Preparation uses its
existing native POST, managed authorization and strict Location admission.
Inspection uses an authenticated native empty PUT to that exact session with
`Content-Range: bytes */<length>`. A 200/201 must confirm file name, MIME, parent
and exact length. A 308 without Range means offset zero; a single canonical
`bytes=0-N` Range means N+1, bounded by the declared length. If all bytes are
retained but completion is not yet acknowledged, an empty status PUT is required;
the same bytes are not sent again. Malformed progress
is a structured fixed failure. 401/403 and other failures preserve the existing
taxonomy without leaking provider bodies or session URIs.

Preparation and inspection remain native UnitOfWork programs. Existing FileStore
and its combined create behavior remain compatible. Controlled native-driver
specifications prove request construction and binding checks; live Drive testing
must separately prove that the selected resumable session accepts the delegated
client PUT without a general server bearer credential.
