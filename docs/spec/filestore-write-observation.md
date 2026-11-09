# FileStore collection write observation

Status: normative static contract for the optional capability

`ObservedCollectionFileStore` adds reservation and observation for collection
creation without changing the base `FileStore` interface or its existing
creation behavior. A binding identifies the owning application's operation,
authorizing caller, parent and display name. The application authenticates and
authorizes that caller, validates the binding against its own durable state, and
retains the reservation before submission. Neither the supplied identifiers,
the opaque provider reference, nor matching a value confers authority. Returned
observations are meaningful only when obtained from the native execution and
retained in trusted owner storage. Deserialized data is untrusted; equality is
not authentication. The consumer must compare the entire reservation, including
caller, operation, parent, name, provider reference and root, against its own
checkpoint and expected revision. This API does not maintain a receipt registry.

A native managed POST admitted unsuccessfully is `NotDispatched` for that POST;
the HTTP driver has not been acquired. After driver invocation an exception is
`PossiblyDispatched`. A matching successful provider response is `Confirmed`;
other responses or inconsistent metadata are `Unknown`. The provider does not
infer dispatch or absence from an HTTP status or exception text. Errors before
the observed POST, and interruption or process failure before its result is
durably recorded, yield no observation: the owner must retain UNKNOWN. The
owner's durable checkpoint and its transaction boundaries remain its own
responsibility. None of these outcomes triggers retry, rollback, deletion or a
change in host admission, credentials, authorization or stream ownership.

Google Drive reserves an opaque generated ID before the create and inspects that
exact ID with normal authorized GET. A matching metadata response can confirm
it; 404, access denial, inconsistent metadata or a missing observation cannot
prove that a write did not occur. Names may duplicate; independent child pages
and name searches are not operation attribution or absence evidence. A
reservation needs durable retention before submission and remains subject to
provider expiry, availability, visibility and authorization. Confirmation is
an observation at the time of the response, not a guarantee against subsequent
changes. Existing reserved direct upload is a separate file/session capability.

S3 object keys can identify objects, but common S3 behavior does not itself
provide an operation receipt, portable collection creation, unique display
names, conditional create semantics across all providers, or proof that an
uncertain request was never applied. A future S3 implementation must specify
its own supported reservation, conditional-write, authorization, retention,
versioning and inspection semantics; this capability does not implement S3.
Old operations without a retained owner-native binding and observation remain
UNKNOWN. New code cannot produce retrospective evidence for them.

Behavioral coverage: [FileStoreWriteObservationSpec](../../src/test/scala/org/goldenport/cncf/filestore/FileStoreWriteObservationSpec.scala).
