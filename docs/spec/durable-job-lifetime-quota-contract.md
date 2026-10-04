# Durable Job Lifetime Quota Contract

status=normative-partial-implementation
phase=69.7
slice=JM69-09B3B3C2B1-and-C2B2A-and-C2B2B1-and-C2B2B2
implementation_acceptance=B1-incremental-proof-validated-and-assured-Step-acceptance-commit-pending
root_admission=VAL-005-validated-independent-conformance-PASS
engine_integration=C2B2B1-focused-validated-C2B2B2-authored-fresh-validation-independent-review-pending

This contract fixes the private native provider authority for exact paid
lifetime credits, unused normal tickets, once-only compensation claims and
prepaid cancellation intent. C2B1 has current validated and independently assured
incremental proof; Step/Phase acceptance and commits remain pending. C2B2A has
VAL-005 focused validation and independent root conformance PASS. C2B2B1 now
implements same-State native live-runtime enforcement with focused validation
complete; independent Slice conformance, Step acceptance and commit remain pending.
C2B2B2 authors exact fresh-State runtime/terminal recovery in place of signed
footprint floors. Its fresh validation and independent review remain pending.
There is no production bridge-binding caller yet.
Fresh SQLite owners in one process establish provider persistence behavior;
they do not close JM69-10 real two-process or actual CBD Support acceptance.

The signed V1/V2 Job record remains the authenticated lifecycle and audit truth.
The lifetime ledger is a separate closed operational Entity. It stores no task
body, callback, provider, access value, signed record or runtime execution state.
The existing Job store, public APIs/Products, provider API, build, dependencies
and signed schemas remain unchanged.

## R1 Frozen policy and prepaid encoding

`DurableJobLifetimeQuotaPolicy` contains the existing lower-only
`JobLifetimePolicy` and independent inclusive positive lower-only ceilings of
4096 ledger entries and 67108864 held bytes. Lifetime defaults are 8192 charged
task slots, 524288 history credits and 16384 admissions per Job. Every persisted
policy field must equal the current configuration across reopen, including a
decrease. A new runtime cannot reset or reinterpret surviving authority.

Fixed bounds are 512 UTF-8 bytes per nonblank opaque Job/task ID, 67108864 wire
bytes, JSON depth16 and eight total native mutation attempts. IDs retain their
exact spelling and are not parsed as historical canonical IDs.

Each entry holds **4096 + 4096 × paid.chargedtasks** bytes, with checked Long
arithmetic. A normal pays two charged slots: one for its normal ID and one for
the possible repeated original ID in a compensation claim. Worst JSON escaping
of a 512-byte ID takes at most3072 bytes plus delimiters, which fits its4096-byte
slot. The4096-byte entry overhead covers the worst Job ID, fixed digest/token
fields, root state, intent, bounded counter digits and punctuation. Capacity is
reserved before IDs and claims exist. Current short JSON size cannot stand in
for those future holds.

Both prospective entry count and held bytes must fit before native mutation.
The actual generated wire must also fit configured and fixed wire ceilings.
Already-paid association, compensation and Cancel use no additional hold and
cannot fail encoding capacity on a healthy valid ledger. Pending, Published and
ghost charges never refund, evict, truncate or disappear during cleanup.

## R2 Closed operational version one

The root has exactly `version`, `maximumChargedTasks`,
`maximumHistoryCredits`, `maximumAdmissions`, `maximumLedgerJobs`,
`maximumLedgerBytes` and `entries`; version is1. Entries are sorted and unique by
opaque `jobId`. Each has exactly `jobId`, `ownerSha256`, `rootSha256`,
`rootToken`, `rootState`, `chargedTasks`, `historyCredits`, `admissions`,
`cancelCredits`, `remainingNormals`, `normalIds`, `compensationClaims` and
`cancelClaimed`.

Digests are64 lowercase hexadecimal characters and tokens32. Root state is
exactly `Pending` or `Published`. Counter values are nonnegative integral Longs
within the exact policy, with cancelCredits≤admissions and
2×(normalIds.size+remainingNormals)≤chargedTasks. Each ID vector is sorted and
unique; claims are a subset of normalIds. ID counts are bounded before traversal.
Pending entries have no normal IDs, claims or cancellation intent. Owner SHA
reuses `DurableJobOwnedBlobPayloads.ownerFingerprint`; root SHA hashes the exact
admitted canonical signed Job JSON.

Encoding is deterministic compact explicit-field-order JSON. Strict Jawn
refuses duplicate keys. Wire byte/depth admission precedes parsing; unknown or
missing fields, versions, primitive/null records, wrong numbers, malformed IDs,
ordering/uniqueness/subset/counter violations and invalid policy refuse. Null or
malformed constructed values refuse with the same closed invalid result.
Encoding and aggregate usage admit the same complete record. Reads do not
rewrite surviving wire text or discard facts.

## R3 Entity, provider and bootstrap authority

The singleton collection is
`EntityCollectionId(cncf,builtin,durableJobLifetimeQuota)`. Its fixed ID uses
`EntityId.bridgeFromParts` with epoch and entropy `lifetime_quota_v1`. Native
Entity persistence uses StoreOnly, Detached revision and Optimistic policy with
the existing `DurableJobStoreEntityRealmRef` pattern. Serialization requires the
expected singleton identity and nonempty bounded canonicalJson; detached carrier
and creation acknowledgement admission require a positive provider revision.

The shared SQL reader preserves `canonicalJson` as the exact stored String for
this exact `cncf/builtin/durableJobLifetimeQuota` collection before the closed
lifetime decoder validates it. Whitespace, field ordering, duplicate keys and
malformed text remain unchanged; reading never normalizes or rewrites them.
Other fields, Instance aliases and other EntityStore namespaces retain ordinary
structured JSON decoding. The real SQLite canonical-text specification covers
create/load, projected search, save/update, authoritative CAS and fresh reopen.

Each admitted operation checks compatible Job and lifetime descriptors and
resolves both actual routes through entityStoreSpace and dataStoreSpace. The
selected provider objects must be identical within that runtime and support
SearchableDataStore, native OptimisticCompareAndSet and AuthoritativeRecordResult.
Unsupported, nonsearchable or split routing refuses before quota writes. Reopen
may construct entirely new provider, EntityStore and context objects. There is
no global JVM budget, process map, SQL fallback, overwrite/save mutation or
provider API extension.

Only reserveRoot may bootstrap. With no lifetime singleton, one native Job query
uses Query.Empty, ascending `id`, Limit(1), offset0. The page must have at most
one row, no cursor and Exact or correctly Limited(1) range, and must be empty.
Any actual row, malformed page or source failure refuses. No count, EntityStore
search, unbounded scan, historical floor import or legacy-ledger reconstruction
is allowed. Bootstrap creates the complete first Pending entry in one native
create attempt. Only explicit duplicate singleton creation may reload/retry.
Snapshot and other operations never bootstrap.

## R4 Root reservation and publication

reserveRoot admits policy, task count0..1024, bounded nonnegative note count,
ID, complete canonical signed V1/V2 codec/access validation and initial semantic
revision1, Submitted lifecycle and Active retention before native effects.
The original codec continues to own V1/V2 compatibility; invalid combinations
are refused. The exact `JobLifetimeBudget.work(n,m)` costs are2n tasks,
32+64n+m history, one admission and one prepaid Cancel, with n remaining normals.

The ordinary authenticated Job store must load no existing signed Job, and no
quota entry may already use the ID, even for an identical candidate. One random
private token is generated after pure admission and retained across stale
retries. Acknowledged Pending persists owner/root digests, paid counters and
empty ID/claim/intent fields before the caller writes its Job. reserveRoot never
creates/checkpoints that signed Job. Only an exact acknowledged native mutation
issues the lease binding ID/token/root SHA/owner SHA.

publishRoot loads actual authenticated signed proof. Pending promotes only when
the actual canonical root SHA, semantic revision1 and immutable owner match.
recoverRoot reads the persisted binding and requires that same proof without a
lost JVM token. Missing, mismatching, corrupt or unauthorized proof cannot
publish or refund. A matching issued lease on Published returns current exact
counters unchanged after later legitimate signed checkpoints.

A source failure or acknowledgement loss retains Pending charge. An ambiguous
quota result never licenses native Job writing or execution. There is no
release/abort API. Root candidate discard now follows the existing configured
engine refusal path in authored C2B2A and cannot refund separately persisted
native evidence. Root A's VAL-005 and independent conformance PASS are retained.

### R4a Configured root acknowledgement pipeline

After the original local input/lifetime preflight, provisional physical lease,
ID and Submitted record construction, the bridge first validates Submitted
status and the complete effective native policy. It uses actual record.tasks
and record.debug.executionNotes counts, JobLifetimeBootstrap and a pure
JobLifetimeBudget root ticket before its one existing Admission evidence call.
The engine explicitly supplies the State lower-only lifetime policy; copying
that field retains configured global limits and exact persisted policy matching.

The Store's package-only lifetimeQuota factory uses the same constructor-owned
EntityStore and performs no writes, discovery or bootstrap. The closed helper
then sequences acknowledged native Pending reservation, ordinary signed initial
Store create, and acknowledged native Published confirmation with Consequence
flatMap. Only after all three acknowledge may the bridge retain the actual
signed snapshot and DurableJobLifetimeBinding(quota,access), followed by existing
local lifetime/physical publication, scope, record, event, timer/queue and body.
The binding retains ownership, never paid counters or cached admission authority.
Its growth is bounded by native maxledgerjobs without eviction/deletion.

Lost reserve acknowledgement leaves Pending without a signed write; lost signed
create acknowledgement leaves actual signed revision1 and Pending; lost publish
acknowledgement leaves actual signed revision1 and Published. Every uncertain
call remains refused, releases its physical provisional lease and leaves the
acknowledged bridge entry and executable candidate absent. Existing reserve unavailable,
Store closed failure and publish confirmation-unavailable labels are preserved.
There is no helper-level translation, same-call readback inference, refund or
compensating delete. Explicit fresh recoverRoot may promote matching initial
signed proof once as library confirmation, with no engine/task effect.

No source invocation, live task, JobRecord, resolver or callback enters the helper.
All six boundary/request shapes and later checkpoint bodies remain unchanged.
Ordinary direct Store creates do not automatically charge lifetime authority;
signed V1/V2 reads/checkpoints retain their original contracts. Root admission
retains n unused normals; authored B1 dispatch associates actual normal IDs
before metadata and body, reducing remaining without repayment.

## R5 Published capacity and once-only settlement

snapshot requires an actual authenticated signed Job and matching immutable
owner, and returns the exact existing Seed plus cancelclaimed. No footprint
reseed, componentwise lowering, signed timestamp/revision/checkpoint change or
task retention is permitted. resourceSnapshot contains only numeric entry/hold,
summed paid counters and configured limits, counting Pending and ghosts.

| Operation | Paid delta | Native entry change |
| --- | --- | --- |
| reserveWork(n) | 2n / 32+64n / 1 / 1 | remaining+=n; refuses Cancel intent |
| reserveRetry(n) | 2n / 32+64n / 1 / 1 | remaining+=n; clears intent only on success |
| reserveControl | 0 / 32 / 1 / 0 | refuses Cancel intent |
| reserveAnnotation(m) | 0 / 32+m / 1 / 0 | preserves Cancel intent |
| associate(id) | already paid | decrements remaining once; sorted exact ID |
| claimCompensation(original) | already paid | adds one associated original claim once |
| cancel | already paid | decrements positive Cancel credit and sets intent |

Each expansion checks prospective per-Job counters, remaining normals and global
held capacity before one observed-revision CAS. Refusal preserves actual bytes
and revision. These methods grant capacity; caller lifecycle, source, replay and
transition decisions remain mandatory and no Job callback is executed here.

Duplicate association returns true without write/payment, even after Cancel.
Fresh association requires an unused normal and no Cancel intent. Unknown or
already claimed compensation returns false without write; owed claims remain
available after Cancel/capacity exhaustion. Cancel returns true only for a newly
persisted intent. Existing intent returns false without write even at zero
credits. No task/history/admission/hold is added by Cancel. Only accepted paid
Retry clears intent and adds its next allowance.

Lost association/claim/Cancel acknowledgements return unavailable while retaining
actual committed evidence. Fresh compensation never reissues an uncertain
original claim. A sidecar intent is not signed lifecycle cancellation or runtime
completion: live native gates and authored fresh-State recovery inspect intent
before new work and finish original safety cancellation/replay without reviving
work or spending again. Only an actual admitted signed terminal checkpoint
establishes durable lifecycle cancellation.

## R6 OCC and closed failures

There are at most eight total attempts per operation, counting bootstrap create.
Updates use updateDetached with the observed positive revision and
ObservedRequired Optimistic policy. Success requires exact acknowledged singleton
identity/canonical bytes, a strictly greater positive detached revision and a
complete record with exact persisted policy. Wrong or uncertain results refuse.
Only explicit stale-entity-revision or expected duplicate bootstrap create may
retry. Generic failure, throws and lost acknowledgement never retry or infer
success from readback. Duplicate/no-op selectors and reads never rewrite.

Fixed invalid/unavailable/confirmation labels are respectively
`Durable job lifetime quota invalid`, `Durable job lifetime quota unavailable`
and `Durable job lifetime quota confirmation unavailable`. Capacity uses the
existing JobResourcePolicy.refused taxonomy/status/message. IDs, tokens, subjects,
tenants, paths, raw JSON and source/provider/decoder exceptions never enter
outward text. Ordinary authenticated signed reads retain original auth/results
when quota authority is unavailable or corrupt.

## Executable scenarios and mandatory integration

[DurableJobLifetimeQuotaSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobLifetimeQuotaSpec.scala)
authors LQ1..LQ16 with real temporary SQLite files under target, independent
provider/EntityStore/context objects, exact native bytes/metadata, bounded joined
actors and finally cleanup. Faults commit actual writes before losing responses.
ScalaCheck seed6970933611 requires at least24 successful finite operation
sequences with actual actions and adjacent GWT/direct should assertions.

| Scenario | Contract |
| --- | --- |
| LQ1–LQ3 | Inclusive root bounds, pure refusal, Pending/exact signed publication and lost-token recovery |
| LQ4–LQ7 | Exact fresh-owner policy/counters, paid deltas, normal/claim uniqueness and prepaid safety |
| LQ8–LQ10 | Closed-provider reopen, conservative ghosts/missing/corrupt authority and prepaid/wire/count bounds |
| LQ11–LQ14 | Real CAS/bootstrap races, bounded stale retry, acknowledgement losses and exact provider proof/routes |
| LQ15–LQ16 | Closed diagnostics, unchanged signed read/auth facts and24 seeded native sequences |

B1 VAL-003 completed all51 suites:449 tests ran,433 passed and16 new lifetime
examples failed, with zero compiler warnings and one existing pending example.
Its immutable receipt SHA256 is
`a81f035f08f5186e7757895a9cc29b1aab70c9715c1a72a0139c13caf4f3bb0e`.
Parent diagnosis confirmed that the shared SQL reader omitted the lifetime
collection from exact native text preservation. After the bounded SQL repair,
B1 VAL-004 completed all51 suites:449 tests ran,447 passed and2 failed, with
zero compiler warnings and one existing pending example. Its immutable receipt
SHA256 is
`04622788d07fca6465b23b4361e306065f88f1b98b766de2945cae92830b906b`.
All eight SQL canonical-text scenarios and15 of16 lifetime scenarios passed.
The remaining LQ14 fault fixture targeted an unused mutation method; LC5 selected
a compensated original by unstable task-page position. The bounded correction
now injects zero acknowledgement in the actual versioned mutation path, proves
one injected response, and selects the unique recorded compensation linkage.
Current B1 `PHASE-69.7-JM69-09B3B3C2B1-VAL-005` passed449 tests in51 suites,
with zero failures, zero compiler warnings and one existing pending example.
Its immutable receipt SHA256 is
`1f5b2332120960147b6b8b91145e6f02f644ac120133beccee5a519ab20aa646`.
The sealed independent combined M2 result is PASS, SHA256
`414d9ab586ead4ffe5c7ed9a8bef628ed53b8d3f78d844678764a36676f25392`.
These are accepted incremental B1 proof; B1 is not Step/Phase accepted or
committed. Existing unit1/2/3 ledgers and exhausted B1 pre-review repair budget
remain unchanged. The actual predecessor
C2A VAL-003 discovered50 suites and passed433 tests, with zero failures, zero
compiler warnings and one existing SqliteDataStore pending example; immutable
receipt SHA256 is
`b6dc2aea3c4ef0ccfea78d10ef1b79ec4874c5047583aa41710ae66425401f37`.
Of the prior50 specifications,48 remain byte-identical. The SQL canonical-text
matrix extends the lifetime collection and exact-identity negative cases; only
LC5 in the existing concurrency specification receives the original-ID fixture
correction. The signed Job store/runtime remain unchanged by B1.

Root C2B2A passed VAL-005 focused validation and independent conformance review. The new
[JobDurableLifetimeAdmissionSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeAdmissionSpec.scala)
authors eight NRA examples: Sync/Async acknowledged order, inclusive pure bounds,
native global capacity, real lost reserve/create/publish acknowledgements,
closed authority refusals and24 fixed-seed finite native sequences using
Seed(6970933621L). Actual root A `PHASE-69.7-JM69-09B3B3C2B2A-VAL-001` completed53 suites:
463 tests ran,458 succeeded and5 failed, with zero compiler warnings and one
existing pending example. Receipt SHA256 is `95ec13eb66279d2a89a04dc9d9325af75e8cd7fc3294a96e5700f53f2dd10966`.
The five failures are NRA2 (missing fixture Detached JobEntity binding), NRA7
(nonterminal V1 fixture), NRA8 (generated failure context hidden), and the two
existing durable cancellation scenarios (pending removal assertions). The frozen
test-only repair installs the declared Detached fixture binding, creates a legal
terminal V1 record, adds ScalaCheck Test.Result clues, and asserts synchronous
cancel pending removal with once-only terminal/event gates and finally cleanup.
VAL-002 stopped before tests because Cozy's development classpath was stale;
the authorized build-only classpath refresh succeeded. VAL-003 and diagnostic
VAL-004 each completed53 suites:463 tests ran,462 passed and only NRA8 failed,
with zero compiler warnings and one existing pending example. Diagnostic
evidence exposed a sequential fixture declaring every task as a root while
the engine correctly gave subsequent tasks a parent. The test-only repair
declares the first task root and subsequent tasks child, retains original
counters, seed6970933621, normal shrinking and at least24 successful runs,
and verifies live and signed parent-chain observations.

Actual `PHASE-69.7-JM69-09B3B3C2B2A-VAL-005` completed53 suites:463 tests ran
and all463 succeeded, with zero failures, zero compiler warnings and one
existing pending example. Receipt SHA256 is
`fa5db495b95e3f0c78a3aff7c75299f75d466b1e7c2344bd00832a6933b1eace`.
Original LC5 and LQ14 remain passed. All eleven prior validated implementation
accumulators are preserved; independent root conformance review is PASS.
The two lifecycle-event cancellation scenarios are explicitly admitted and
the prior B1 validation vector remains intact. Root A is focused-validated;
C2B2, Step and Phase remain incomplete. No Step/full-Phase acceptance or
full-Phase review PASS is claimed. Root conformance PASS is bounded incremental proof.
See the
[Oct2 journal](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-root-admission.md).

Authored C2B2B1 connects same-State child/control/Retry/annotation admission,
normal association, compensation claims and exact native bootstrap. Authored
C2B2B2 fresh-State runtime/terminal recovery retains legacy/missing-authority
fixtures. Both preserve original source and
port precedence, root-candidate discard, prepaid closure and signed lifecycle
truth. Historical floors cannot silently import missing restart authority.
All eleven prior accumulators, remaining JM69-09C/D, every JM69-10 real two-process
and actual CBD Support fresh-runtime fixture, predecessor69.4–69.6 ancestry and
deferred obligations, independent Step review/local commits, aggregate final
full validation, one mandatory Phase full review and local release commit remain
required. JM69-09/Phase69.7 remain IN_PROGRESS and JM69-10 OPEN; no checklist
criterion is closed. HYG-PH697-SQL-SIZE-01 remains separate maintenance.

## R7 Authored native live-runtime boundary

C2B2B1 installs a bounded private State-body ownership binding only after actual
reserve/create/publish acknowledgements and an issued quota/access handle. All
same-State owners use its admitted root context, owning Bridge, cancellation
scope and shared closed refusal facts. The binding is never paid-counter proof.
Existing constructors, Products, public APIs and wire schemas are unchanged.

Pure whole-batch counting and individual ticket fit precede a provisional
physical lease and the appropriate native Work/Retry/control/annotation payment.
Only acknowledged exact Seeds replace the diagnostic mirror. Failed or ambiguous
operations retain paid ghosts and release physical leases without inferred
success, reissue or refund. Fresh numeric diagnostics may refuse with None.
Same-State attachment validates both complete maps before IO, then authenticates
one fresh native Seed per unique bound job. Live normal IDs/claims are subsets
of native proof; unused normals and paid ghosts may be supersets. Native Seeds
are copied exactly, including zero Cancel credits, without floors or maxima.

Association acknowledgement and a fresh intent gate precede normal metadata,
checkpoints and body. Uncertain association or claim closes normal admission
and settlement across owners. Each uncertain compensation selector is remembered
under the existing State monitor, bounded by the retained normal footprint, and
suppressed before a second native submission. Other owed selectors, annotations
and prepaid Cancel remain eligible. A batch folds all claims before any getter,
history, ID, clock or callback; known false is an effect-free ordinary skip.

Cancel acknowledgement, including known false, permits original safety once.
A lost acknowledgement applies no local cancellation in that call; a later fresh
gate observes intent, cancels the shared scope and removes that owner's pending
work without spending again. Acknowledged Retry alone resets intent and installs
a new shared scope; old callbacks retain their cancelled scope. Automatic due
callbacks transfer their original pending lease without a second charge. Delayed
Retry computes time only after payment; overflow/scheduling failure retains the
payment and closes with the original failure and actual attempt count.

Only the existing terminal Bridge boundary projects compensation. Terminal
preprocessing retains every typed aggregate field for V2 validation. A targetless
normal original may declare a nonblank action without a descriptor; its checked
success/failure aggregate requires one exact actual companion. Missing requires
a completed committed Succeeded original, no companion, a nonblank failure and
both model/job recoveryRequired. Its action may be absent. Every actual companion
and complete historical explicit descriptor retains strict source equality; V1
remains strict. No invented action, self-target, dropped field or schema change.

[JobDurableLifetimeRuntimeSpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRuntimeSpec.scala)
authors NRT-A..K with real SQLite, actual Detached metadata, acknowledgement-based
semantic revisions, committed/precommit faults, bounded joined actors/manual
timers and Seed6970933631 with at least24 successful generated runs.
[DurableJobProjectionSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobProjectionSpec.scala)
authors NRT-P1..P3; NRA1/NRA8 now assert actual associated IDs and remaining0
without changing paid vectors or Seed6970933621. These additions passed current runtime VAL-003 focused validation.
Independent Slice conformance, Step acceptance and commit remain pending; no Step or Phase checklist closure follows this edit.

Authored B2 fresh-State runtime/terminal registration retains original
source/load/classifier/port/input precedence and authenticates Published
native authority instead of importing historical floors. Missing/corrupt/legacy
authority and persisted Cancel remain required scenarios. JM69-09C/D, JM69-10,
all predecessor/full-validation/review/release obligations remain incomplete.

Current runtime evidence `PHASE-69.7-JM69-09B3B3C2B2B1-VAL-003` records
54 completed / 0 aborted suites, 477 run / 477 succeeded / 0 failed tests,
zero compiler warnings and one existing SqliteDataStore pending example.
Independent incremental Slice conformance, independent Step acceptance and
commits remain pending. Fresh B2 validation/review, C/D, JM69-10 actual
two-process/CBD Support and original full Phase obligations remain open.
See the [runtime validation chronology](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-runtime.md).

## R8 Authored exact native recovery authority

`recoverySnapshot` is read-only. It checks the explicit complete policy, access
and caller recovery context using existing compatible provider routing, selects
only Published authority, and loads the signed Job once through the identical
supplied store. The entire signed record and positive provider revision must
equal the coordinator-admitted snapshot. Missing, Pending, corrupt, V1,
foreign-owner, mismatched policy or stale signed/provider proof refuses.
Generic live `snapshot` retains its original behavior. Neither operation
bootstraps, rewrites surviving authority or refunds a ghost.

Pure recovery footprint is an observed obligation lower bound, never a Seed.
Before native IO, checked twice the intrinsically executed normal count and the
actual descriptor/history/diagnostic/retry bounds must fit policy. Unassociated
Pending declarations without task events or compensation obligations do not
count as executions or require normal association. After authentication,
native-associated descriptors also count as executed; the exact paid Seed must
cover the observed footprint, executed normal IDs, original companion IDs and
once-only claims. Task-event IDs belong to the closed descriptor set. Historic
strings are never parsed as live TaskIds. An uncancelled live batch must fit
actual remaining normals; confirmed cancellation instead admits only safety
settlement without dispatch capacity.

Engine registration makes zero native mutation/payment and calls no reserveRoot,
recoverRoot, publishRoot, Work or Retry operation. Under the original State
monitor it completes pure/local collision checks and exact authentication before
safe runtime construction, physical provisional reservation and atomic bridge
adoption. Only successful adoption publishes the shared owner, exact Seed and
record/due state. Runtime owner uses Some(exact-store bridge); closed terminal
registration needs only retained capacity and owner with bridge=None. Numeric
diagnostics authenticate that original quota/access/context. Failed staging
publishes no record/Seed/timer and releases only physical provisional resources.
Later ambiguity retains genuine ownership/payment/refusal evidence.

Confirmed persisted Cancel skips metadata, IDs, timer and pending work, applies
original shared safety once, and requires the narrow authenticated recovered
Cancelled terminal checkpoint before canonical settlement. Explicit Retry alone
adds its original paid batch and resets intent/scope. Recovery RetryRun/Control
keep their existing taskbridgewrites=false contract.

[JobDurableLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRecoverySpec.scala)
authors NR1..NR24 against closed/reopened real SQLite owners, actual OCC/lost
acknowledgements and one atomic adoption race. Seed6970933641 requests at least24
finite shrinking sequences with independent 2*n / 32+64*n / 1 paid arithmetic,
exact remaining normals, IDs, claims and Cancel decrements. Fresh validation,
independent incremental/whole-Step reviews, acceptance and commits remain pending.
See the [B2 authoring journal](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-recovery.md).
