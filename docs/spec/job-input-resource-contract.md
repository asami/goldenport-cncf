# Job Input Resource Contract

status=normative
phase=JM69-09
slice=JM69-09B3B3C1
implementation_acceptance=focused-VAL003-passed-independent-review-pending-commit-pending

This contract bounds engine-owned runtime input descriptors at explicit submission,
complete State bootstrap, and native runtime reconstruction. Public `JobInput`,
`JobInputPayload`, `JobSubmitOption`, State and resource snapshot constructors,
fields, defaults, copy/unapply and durable schemas remain unchanged. Admission
preserves the original input object and facts without normalization or clipping.

## R1 Inclusive runtime limits

Package-internal `JobInputResourcePolicy.validate(Option[JobInput])` is pure and
returns `Consequence[Unit]`. The hard limits are:

| Resource | Inclusive ceiling |
| --- | ---: |
| Payload descriptors per Job | 1024 |
| Actual inline or known declared bytes per payload | 8,388,608 |
| Aggregate admitted payload bytes per Job | 8,388,608 |
| Aggregate UTF-8 metadata bytes per Job | 1,048,576 |
| Encoded inline Base64 characters per payload | 11,184,812 |
| Storage UTF-8 bytes | 256 |
| Field name UTF-8 bytes | 512 |
| Filename UTF-8 bytes | 1024 |
| Content type UTF-8 bytes | 512 |
| SHA metadata UTF-8 bytes | 512 |
| Blob ID metadata UTF-8 bytes | 2048 |

Metadata is exactly storage plus present fieldName, filename, contentType, sha256
and blobId. Inline Base64, timestamps, TTL and retention fields are excluded.
Every String length is checked against its own byte ceiling before bounded
UTF-8 allocation; actual UTF-8 bytes then determine admission. Aggregate bytes
are accounted using Long remaining capacity before addition. One late overflow
refuses the whole input and never drops the valid prefix.

Storage must be non-null and nonblank after bounded trim. Bounded unknown storage
values, optional empty strings, opaque legacy SHA values such as `abc`/`sha256`,
and Blob IDs such as `blob-1` remain legal. There is no storage enum, SHA grammar,
Blob parsing/resolution, ownership inference or inline/Blob exclusivity check.

## R2 Actual inline and known external bytes

The encoded-character ceiling precedes Java Basic Base64 decoding. Its legal
padded, unpadded and empty encodings remain accepted; malformed alphabet,
whitespace and encoding refuse. Decode one bounded byte array at a time, retain
only its actual length during validation, and never compute a payload hash.
Actual decoded length must fit the per-payload ceiling. Present byteSize must be
nonnegative, within the same ceiling, and equal actual inline length. An absent
byteSize is legal and charges actual decoded bytes.

With no inline raw value, a present nonnegative fitting byteSize charges the
declared known bytes. An absent byteSize charges zero bytes plus its bounded
descriptor/count/metadata. Actual inline and known external bytes share one
aggregate ceiling. This is a descriptor admission proof, not proof of unknown
external Blob physical size. Exclusive Job-owned Blob byte authority remains
the preceding B3B2 contract. Shared/external payload lifetime and disposal remain
with their owners.

## R3 Shape, time and closed failures

The input Option container, payload vector, every payload, every optional field
container and every present reference value must be non-null. None input is
legal. Some(null), null vector/element, null retentionPolicy/createdAt/ttl,
null cleanedAt or Some(null) cleanedAt, and null payload createdAt refuse.
Present byteSize must represent a nonnegative Long.

For Ttl, createdAt.plus(ttl) must be representable. Only NonFatal from that
addition is normalized. Negative or already due TTL remains legal when the
addition fits. Keep and DeleteOnCompletion retain their non-null unused TTL
without addition, including extreme durations. There is no positive/max TTL
policy, clock read, cleanup, raw stripping or timestamp rewrite in validation.

Shape, malformed Base64, inconsistent declared inline length and required TTL
addition overflow use exactly
`Consequence.argumentInvalid("JobEngine input request refused")`. Count, text,
encoded-character, known-byte, actual-byte or aggregate oversupply uses exactly
`JobResourcePolicy.refused`, the existing serviceUnavailable
`JobEngine resource capacity unavailable`. New errors expose no supplied body,
field, Blob identity, provider or path text. Only decoder and required date
addition NonFatal failures are normalized; fatal signals/interruption propagate.

## R4 Explicit submission ordering

Existing quiescing refusal retains first precedence. Null explicit submit option
then refuses as fixed invalid input. Input admission precedes the original option
validator and its recoverWith admission-observer handler. Only the original
option refusal for an admitted input invokes observeAdmissionFailure. Invalid
input therefore precedes all task getters/callbacks, IDs and clocks, provisional
leases, native lifecycle evidence/writes, runtime records/events/scopes/queues,
timers and task execution. Valid Sync/Async Persistent/Ephemeral scheduling,
guards, observers and default submission remain unchanged.

Caller allocation, JobInput factories, HTTP body parsing and prior managed
upload happen before the engine; these are outside this zero-engine-effect
guarantee.

## R5 Complete State bootstrap

Under the existing State monitor, validateEngine runs first. Both durableJobs
and runtimeJobs then receive complete value/input admission before budget.seed,
the attached flag, new owner, scheduler or worker initialization. Each map is
scanned through its iterator up to maxretainedjobs plus one refusal sentinel,
using the existing lower-only policy without unbounded materialization.
Every status and unscheduled/terminal value is admitted, not only runnable
records. Null record/input shape refuses. Duplicate IDs across maps retain the
existing seed-union accounting. Failure throws exactly
`IllegalArgumentException("JobEngine resource bootstrap refused")`, preserving
maps and prior budget. Out-of-band unsynchronized map tampering remains outside
this boundary. No new State/Product field is introduced.

## R6 Native runtime reconstruction

All existing result/task/context/Option/candidate/snapshot/runtime identity
checks retain their order and errors. Their final success additionally validates
the returned runtime input. This precedes record construction/map/_now,
sanitization/copy, resource reservation/insertion, queue and timer registration.
Input refusal produces the existing closed startup registration Refused and
leaves native revision/timestamp and runtime resources unchanged.

The eligible source, native load and provider port have already run; their calls
remain observable and are never described as zero discovery/provider effects.
Valid reconstruction preserves native identity times and the existing sanitizer:
inlineBase64 and blobId become None while every other payload/input metadata and
timestamp is retained. Existing due-state timers and normal queue drain remain
the sole execution path.

## Executable scenarios and acceptance

[JobInputResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/JobInputResourceBoundsSpec.scala)
authors IC1 compatibility; IC2 all null/container shapes; IC3 count boundary;
IC4 actual Base64 bytes/declared consistency; IC5 aggregate/mixed bytes;
IC6 Basic grammar/encoded preflight/redaction; IC7 individual UTF-8 fields;
IC8 aggregate metadata; IC9 TTL cleanup/overflow compatibility;
IC10 real native lifecycle admission and guarded positive dispatch;
IC11 original option/observer/quiescing precedence; IC12 at least 24 fixed-seed
actual behavioral submissions.

[JobInputRecoveryResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/JobInputRecoveryResourceBoundsSpec.scala)
authors IR1 complete preseed refusal; IR2 inclusive valid/duplicate/shared State
and due dispatch; IR3 real signed source/port input refusal with unchanged native
authority; IR4 prior identity/shape refusal; IR5 exact sanitizer/timestamps/due
drain; IR6 at least 24 fixed-seed actual native reconstruction attempts.
Both Specs use adjacent Given/When/Then and direct should assertions, actual
manual engines/counters/EventStore and signed standard EntityStore. Engines
close in finally and no host sleep or network is involved.

Actual C1 `PHASE-69.7-JM69-09B3B3C1-VAL-003` discovered 47 suites and passed
410 tests, zero failures and zero compiler warnings, with one existing
SqliteDataStore pending example. Independent Step review, acceptance and commit
remain pending.
Preceding B3B3B actual `PHASE-69.7-JM69-09B3B3B-VAL-002` discovered 43 suites,
passed 380 tests with zero failures/compiler warnings and the one pre-existing
SqliteDataStoreSpec pending example. It proves only the preceding slice.
[C2A cumulative per-Job task/history/lifetime admission](job-lifetime-resource-contract.md)
is authored with validation/review/commit pending; mandatory C2B native persisted
lifetime authority, remaining operations,
real fresh-process/CBD acceptance, predecessor ancestry audit, independent Step
and full Phase reviews, aggregate final validation and local release commit
remain mandatory. No Step, Phase or aggregate criterion is closed here.
