# Optional direct FileStore preparation and status

The App-led Phase 6.3 task explicitly added this limited CNCF capability.
Work is isolated from the server producer at baseline 44229fc. Existing
FileStore/create consumers and managed credential ownership remain unchanged.

DirectUploadFileStore advertises its protocol and supplies preparation without
original bytes plus status inspection of a retained provider session. Google
uses its native managed authorization/UnitOfWork programs and strict session
admission. Completed metadata must match the retained parent/name/MIME/length;
Pending exposes the actual offset, including a full prefix needing another
empty status probe, and Expired reflects an actual provider 404. No automatic
new session, upload replay, rollback deletion or invented expiry is added.

DirectFileUpload has a redacted display. The authorized consumer treats its
session URI as sensitive per-upload authority and keeps it out of ordinary
status/logs. No general server bearer/key is exposed. A provider without the
optional capability can still use server-mediated create; permission/network
failure is not absence of that capability.

41 controlled native-provider/credentials specifications pass. The unchanged
SNAPSHOT is refreshed only into normal Local Ivy for admitted TKL validation,
with separate typed publication receipts and released serial locks. No remote
release, actual Google session delegation, deployment or Phase acceptance is
claimed. See [direct upload contract](../../../spec/direct-file-upload-v1.md).
