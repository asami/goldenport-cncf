# Durable Job OperationResponse Payload Contract

status=normative-partial
initial_authoring_implementation=authored_test_pending
implementation=focused_validated_independent_review_pending
phase=69.7
step=JM69-10
slice=JM69-10B2
decision=PH697-JM10B2-AUTHORIZED-PAYLOAD-01@1
recorded_at=2026-10-04

This contract governs one package-private owned-result payload adapter. The
[B1 codec contract](durable-job-operation-response-codec-contract.md) owns the
unchanged typed canonical wire. Signed records, ordinary access, exclusive Blob
ownership, Published quota and cleanup retain their existing contracts. The
[design](../design/durable-job-operation-response-payload.md) provides
normative architectural boundary.

## Canonical terminal lifecycle integration

The [terminal publication contract](durable-job-operation-response-publication-contract.md)
owns framework use of this adapter at the original terminal checkpoint. Its
[design](../design/durable-job-operation-response-publication.md) fixes the
independent preflight, actual producer and original signed checkpoint order;
its [journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md)
owns execution evidence. Explicit private OwnedOperationResponse intent on
independent Succeeded(Absent) seed enables one genuine successful live response
publication; default Supplied retains intentional absence and the original path.
Only the acknowledged reference replaces projected result, preserving every
other independent field. Successful publication followed by checkpoint refusal
or lost acknowledgement retains actual Published payload audit evidence without
caller rollback/refund/retry. This adapter's original producer refusal and
rollback remain authoritative.

Raw/getResult/query/management retain their existing compatibility. The separate
[query contract](durable-job-operation-response-query-contract.md) and
[design](../design/durable-job-operation-response-query.md) select only exact
registered owned/header terminal results after original overview authorization.
The same ready owner resolves actual current raw tenant/subject/scopes once in
its native lease and consumes this original bounded reader with that caller's
context. Only a detached queryVisible model copy carries the native response;
no projection mutation or response cache is introduced. Already admitted reads
may finish through logical close with these final native checks. Startup
credentials and signed authorization never substitute for caller grants.
Actual completed CBD fresh-runtime acceptance remains separate required work;
the [query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md)
owns execution and original process/Step/Phase accounting.

## R1 Private publication

`DurableJobOperationResponsePayloads(payloads: DurableJobOwnedBlobPayloads)` is
`private[job] final`. Its companion content type is
`application/vnd.cncf.job-operation-response+json`.
`publishObserved(snapshot, access, response)(using ExecutionContext)` returns
`Consequence[DurableExternalReference]`. It requires a non-null record with
Running lifecycle and Pending result, applies the pure bounded B1 encode, then
publishes its canonical UTF-8 bytes through the existing `putObserved`, filename
`operation-response.json`. Existing current Active access admission precedes
payload opening, allocation and provider dispatch in `putObserved`; pure codec
evaluation can occur before that authorization. Unsupported or null values,
invalid shape, encode or publication failure refuse. Success returns only the
actual acknowledged reference. It does not attach, sign, checkpoint or change
lifecycle. Existing failed-publication rollback remains authoritative; a later
caller checkpoint failure does not authorize rollback.

## R2 Signed observed-current ordinary read admission

The private owned-payload primitive
`readObserved(snapshot, access, reference)(using ExecutionContext)` returns
`Consequence[DurableJobOwnedBlobDetachedPayload]`. It reuses canonical signed
startup admission with the original tenant, subject and required scopes, then
requires an equal complete current optimistic snapshot and Active retention
before any Blob metadata or payload lookup. Null, foreign, corrupt, missing,
stale and inactive authority refuses. There is no administrative, tenant-wide
or delegated cross-subject bypass.

## R3 Complete native ownership plan

Validate the unchanged configured policy. The non-null reference must have
storageKind `job-owned-blob` and be a complete value equal to an actual input,
result or calltree reference in the admitted signed record. The complete
reference vector, including definition source, must fit the inclusive 1,024
limit. Reuse the complete metadata target plan: protected/shared/definition
overlap and inconsistent duplicates refuse. Select only the one verified target
corresponding to that reference; never parse an arbitrary external storage
locator or construct a physical reference from caller text.

## R4 Existing Published quota before physical lookup

Inspect all original verified facts through `inspectTargets`, requiring exact
immutable owner, content, size, digest and binding and existing Published
entries. Missing, Pending, Released, mismatched or invalid policy authority
refuses. Reads never prepare targets, bootstrap, reserve, confirm, release,
search or census. The selected size must lie in 0..policy.maxpayloadbytes before
the one configured physical get.

## R5 Exact physical reply before stream opening

Call configured `BlobStore.get(selected.storageRef)` exactly once. Its result
and payload must be non-null and its id, complete storage ref, content header,
byte size and case-insensitive digest must equal verified facts before opening.
Do not consult returned URLs, accessUrl/status methods, provider printing or
another store. A null stream refuses after its single open attempt.

## R6 Bounded bytes, EOF and integrity

Open one stream once. Check interruption at entry and before every read. Read
at most verified size + 1 bytes with a buffer no larger than 8,192 bytes and
checked Long accumulation. A bulk count must be -1, 0 or positive no greater
than the request; zero progress uses exactly one single-byte fallback, admitting
only -1 or 0..255. Incrementally apply existing SHA-256 integrity and accumulate
only at most the verified size. Exact EOF, exact size and case-insensitive
actual digest are all necessary. Short EOF, a trailing sentinel, invalid counts
and tampering refuse. No truncation, reopen or readAllBytes is permitted. The
existing inclusive 8,388,608-byte ceiling remains unchanged.

## R7 Closure and final optimistic admission

Every opened non-null stream closes in finally on success, mismatch, ordinary
exception and interruption. Close failure refuses. InterruptedException restores
the thread flag; fatal errors are not caught. After successful physical read
and finally-close, repeat original current Active access admission and inspect
all original quota facts. Detected revision, deletion or quota-release races
refuse without read-side writes or rollback. This is an optimistic observed
read, with no guarantee against effects unobserved after the final admission.

## R8 Typed terminal consumer

The adapter `readObserved(snapshot, access)(using ExecutionContext)` returns
`Consequence[OperationResponse]`. Its pure shape gate requires a non-null
Succeeded record and `Succeeded(External(ref))`, with non-null owned ref and
the exact companion content header. Pending, Failed, Cancelled, Absent, Inline,
external/shared and other legacy interpretations refuse. Delegate to R2..R7,
then a fresh UTF-8 decoder configured to REPORT malformed/unmappable input,
then actual B1 decode. Do not replace Unicode, remove BOMs, repair wire/version,
invoke fallback serializers or reconstruct summaries/placeholders/writer values.

## R9 Redacted failures and compatibility

All owned primitive failures are exactly
`Consequence.stateInvalid("Durable Job-owned payload refused")`; all service
failures are exactly
`Consequence.stateInvalid("durable job operation response payload refused")`.
Collapse codec, Unicode, ownership, provider and NonFatal failures; restore
interruption. Failure facets expose no private exception, reference, key, path
or body. Detached bytes are private data, never control/management receipts.
No result escapes before closure and final checks. Existing Absent, raw result,
getResult, management, terminal projection, signed format, public API, quota and
cleanup behavior remains unchanged. The separate current-caller query contract
owns eligible detached queryVisible exposure; this private adapter adds no public
consumer, cached response or alternate authority.

## Executable specification gate

[DurableJobOperationResponsePayloadsSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponsePayloadsSpec.scala)
binds `spec:durable-job-operation-response-payload phase:69.7 slice:JM69-10B2`
with adjacent Given/When/Then and should expectations in all twelve leaves and
generated cases. Fixtures use actual managed file SQLite/LocalBlob namespaces.

| Scenario | Required behavior | Evidence |
| --- | --- | --- |
| RP1 | Independent six-field Review wire/domain, publication does not checkpoint, actual attached result restores exact ownership/bytes/types | authored_test_pending |
| RP2 | Closed writer and fresh native managed owners recover Published exact result with no read-side effects | authored_test_pending |
| RP3 | Foreign/null/missing-scope/corrupt/stale/inactive reads refuse before any Blob metadata/get/open/read | authored_test_pending |
| RP4 | Complete membership/kind/owner/store/binding/duplicate/protected admission and inclusive 1024/1025 plans | authored_test_pending |
| RP5 | Only original exact Published native authority permits get; missing/Pending/Released/mismatched/policy facts refuse without writes/census | authored_test_pending |
| RP6 | Null/wrong native replies refuse before open, null stream after open; uppercase digest accepted and every stream closes | authored_test_pending |
| RP7 | Ordinary/zero-fallback EOF success; short/trailing/tampered/invalid count refusal within size+1 with single closure | authored_test_pending |
| RP8 | Matching owned malformed UTF-8 and malformed/noncanonical/version wires refuse; Unicode response succeeds | authored_test_pending |
| RP9 | Deterministic revision/deletion/release races, provider/close errors and interruption refuse after closure without private diagnostics | authored_test_pending |
| RP10 | Unsupported/null/invalid-shape publisher requests allocate nothing; original put refusal and rollback retained | authored_test_pending |
| RP11 | Independent exact 8388608-byte Scalar wire roundtrips, one-over producer allocates nothing, lying/trailing replies refuse boundedly | authored_test_pending |
| RP12 | Ordinary shrinking generated native Records and V1/V2 selection, seed69710022L/min24, exact independent domain/bytes and no-write/closure invariants | authored_test_pending |

Selected parent validation is six representative suites then the retained
113-suite accumulator. Execution and independent whole-Step assurance are
pending. The same-process fresh-owner/domain fixtures are neither real
separate-JVM proof nor actual completed CBD pipeline/runtime acceptance.
Later B3 parent PLAN must connect real terminal publication and the authorized
consumer, preserving raw and management compatibility. Every original JM69-10
process/downstream/compatibility, Phase69.4..69.6 handoff and Phase closure gate
in the [cross-process contract](job-cross-process-acceptance-contract.md) remains
mandatory. The rejected component shadow-store spike remains rejected.

## JM69-10B2 current selected execution — 2026-10-04

status=focused_validated_independent_review_pending
subject_revision=3
review_status=complete-original-Step-independent-assurance-pending
acceptance_status=not-accepted

Earlier authored/pending sections remain the original authoring history. Current
`PH697-JM10B2-REP-005` passed six completed suites/60 succeeded,
zero failed/pending/aborted; `PH697-JM10B2-ACC-005` passed the unchanged original
ordered113-suite accumulator/924 succeeded, zero failed/aborted, plus the
one existing SqliteDataStoreSpec pending example. Both had zero compiler
warnings, typed reader/SBT/wrapper exit0, consumed permissions and lock=released.
RP1..RP12 passed; RP12 ordinary shrinking seed69710022L/min24 retained its actual
checked.passed and checked.succeeded>=24 assertions, valid V2 Running/Pending
producer, and both selected V1/V2 completed consumers. Parent complete sixteen-path
mechanical/GWT/compliance admission passed; current Step union22 and twelve
unrelated dirty paths were preserved.

Failure history remains: REP001 stopped before compile/tests on stale Cozy
classpath; the existing authorized `cozyExportRuntimeClasspath` CP001 succeeded
with lock released (three noncompiler missing-credential notices). Its initial
request-retention stop had no submission and used one same-child V4 recovery.
REP002 had thirteen test compile errors from inherited ScalaTest withFixture
resolution; qualifying twelve existing native-helper calls corrected it. REP003
was a parent typed repair-identity preparation rejection, with no child or SBT
submission. REP004 compiled with no warnings and ran six suites:59 succeeded,
one RP12 failure from invalid V1/Pending fixture setup. Two local RP12 lines now
use the valid V2 producer and explicitly retain V1/V2 completed-consumer coverage.
No assertion was weakened, case removed, shrink disabled or snapshot printed.
These are selected test results, not independent acceptance.

JM69-09 remains DONE; JM69-10 remains OPEN and Phase69.7 IN_PROGRESS. Real terminal
runtime publication/authorized consumer integration, the complete process
matrix and independently fresh actual Textus CBD Support completed full result,
predecessor ancestry/deferred audits, complete original Step independent review
and local acceptance commit, aggregate full CNCF/downstream validation, sole
epoch1 comprehensive Phase review and final local release remain mandatory.
The representative native fixture is same-process and does not establish actual
CBD or separate-JVM acceptance. No B2-only review, acceptance, commit, push,
publication, deployment or successor work is recorded.
