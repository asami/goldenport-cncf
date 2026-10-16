# Phase 88: Workflow Server Application Support

Status: in_progress
Planned: 2026-09-20
Depends on: Phase 77
Related: Phase 87

Implementation started: 2026-09-29
Consumer: sm-workflow Phase 2

The [managed mechanical transition candidate](../journal/2026/10/2026-10-02-managed-mechanical-workflow-transition.md)
supports the consumer's all-known advance without an issued AI result. It
uses the existing captured Workflow grant, DataStoreSpace and common provider
to commit instance history, a distinct checkpoint receipt and application
Creates together. Five new main files and one metadata-only binding are
candidate source, with three executable-spec files. Focused accumulator 797
completed compilation and passed **97/97 tests in nine suites**, exits 0/0
and lock=released, after three retained bounded corrections. Native atomic
publication, physical reopen/later-history recovery, missing/foreign grant
refusal and uncertain/tampered readback are verified. This is producer-focused
evidence, not consumer advance/default installation, independent acceptance,
full repository validation, a new pool or original Phase closure.

## Slice 88-S1: Canonical datastore instance persistence

The first slice adds a strict versioned Workflow instance-record codec and a
borrowed canonical DataStore adapter implementing the unchanged provider-neutral
create/load/append SPI. Reopen requires a separately admitted exact ABI catalog;
persisted metadata never constructs a new executable definition.
The adapter uses existing atomic Entity CAS with authoritative readback rather
than a private SQL/store/engine. Workflow revision zero maps explicitly to positive
storage revision one; storage overflow rejects an append without writing.

Executable scope: exact record/history/suspension rehydration, schema/provenance
rejection, policy drift, stale replay, SQLite close/reopen and concurrent writers.
Validation: all seven new provider examples passed; Workflow/datastore regression
235 completed 35 suites with 244 succeeded, 0 failed, 7 existing live MySQL
cases canceled and 1 existing example pending. See the
[implementation journal](../journal/2026/09/2026-09-29-phase-88-durable-instance-provider.md)
for exact receipts, source identities and evidence limits.
The private storage text envelope preserves bytes through canonical SQL naming
and structured-value decoding; no private SQL backend is introduced.
This slice does not complete Phase 88: resident references,
shared service/server/CLI composition, joint
instance/claim/evidence recovery and Phase 87 management visibility remain open.
The adapter borrows datastore/resource ownership and grants no command authority.

## Slice 88-S2: Configured Component/Factory bootstrap

WorkflowInstancePersistenceSource and WorkflowInstancePersistenceBinding now
connect the S1 provider through normal ComponentFactory bootstrap. Component
source selection precedes Factory selection, including explicit opt-out; legacy
components have no implicit persistence. Malformed declarations or missing
required durable storage fail closed before a binding is advertised.

Each create/load/append resolves a fresh canonical managed datastore view inside
the owning Subsystem lease. Bootstrap's lease-bound view is not retained;
SystemNode owns the shared physical resource. The binding is a host composition
API, not an Operation, new engine, command authority or resource owner.

Eight bootstrap executable examples cover fresh contexts, source selection,
admission failures, shared-binding concurrent CAS and shutdown/reopen with a
separately admitted catalog. Regression 238 completed 109 suites with 876
succeeded, 0 failed, 7 existing opt-in MySQL cases canceled and 3 existing
examples pending. See the
[bootstrap journal](../journal/2026/09/2026-09-29-phase-88-instance-bootstrap.md).

Next is joint instance/Continuation/issued-work/claim/evidence recovery.
Same-process owner shutdown/reopen is not separate-process launcher proof;
policy identity validation is not migration/retention execution. No downstream
artifact refresh or production shared service/server/CLI acceptance is claimed.
Phase 88 remains in progress.

## Slice 88-S3: Paired suspension and durable claims

The borrowed instance provider now supports an explicit typed same-store
suspension composition. Canonical EntityConditionalTransitionDataStore commits
one guarded instance-history update and Available Continuation together;
distinct explicit EntityStore collections and compound capability are required.
WorkflowContinuationDataStorePersistence provides durable claim/release/complete
using canonical Entity CAS, exact claim tokens and authoritative readback.
Committed but unadmittable readback is transaction-indeterminate, not safe retry.

Ten new executable examples cover record rollback, independent SQLite connection
races, exact-token/replay guards, resource reopen and malformed/uncertain storage.
Focused-242 passed 24/24 tests in three suites. After limited recovery and
successful project-owned Cozy classpath prerequisite-244, regression-245
completed 124 suites with 1024 succeeded, 0 failed, 8 existing live MySQL cases
canceled and 5 existing examples pending, SBT/wrapper exit 0 and lock=released.
This is selected Component/Workflow/datastore/UnitOfWork regression, not the
whole-CNCF full suite or downstream production acceptance.
See the [S3 journal](../journal/2026/09/2026-09-29-phase-88-paired-suspension.md).

This is a bounded record-transaction foundation, not AtomicWorkflowSuspensionEngine
or active UnitOfWork/event enlistment. S4 below adds bounded issued payload;
retained evidence, production Continuation Factory binding and separate-process
launcher recovery remain open.
No downstream artifact refresh or production runtime acceptance; Phase 88
remains in progress.

## Slice 88-S4: Atomic issued WorkOrder publication

WorkflowIssuedWorkDataStorePersistence binds an explicit Component identity,
completion Operation, application PayloadCodec, admitted definition catalog and
distinct configured EntityStore collections. It reuses the strict existing
WorkflowWorkOrderJsonV1 codec and S3 canonical compound transaction. The new
Available Continuation contains opaque immutable issued bytes, published together
with instance history. No independent side-effect Save, callback or later
putIfAbsent call can retrofit a missing issue.

Recovery checks exact typed handle/Continuation facts and matching instance
suspension history. The existing issued-work SPI provides reads and identical
retry only; neither grants claim authority. Claim/release/complete CAS preserves
the co-located issued bytes. Missing or malformed issue, lossy/throwing codec and
foreign completion binding fail closed without repair.

Focused-248 passed 34/34 tests in four suites, including all nine S4 examples.
Regression-249 completed 125 suites: 1033 succeeded, 0 failed, 8 existing live
MySQL cases canceled and 5 existing examples pending; no aborted suites,
SBT/wrapper exit 0 and lock=released. Earlier 246/247 fixture-ID initialization
failures are retained in the
[S4 journal](../journal/2026/09/2026-09-29-phase-88-issued-work-publication.md)
with exact receipts, source identities and evidence limits.

This is a borrowed producer record-transaction slice, not active UnitOfWork
event/data enlistment, retained result/admission evidence or compound completion.
Production claim admission, Factory shared-service/launcher/server/MCP binding,
separate-process recovery, downstream artifact consumption and management
visibility remain open. Phase 88 remains in progress.

## Slice 88-S5: Guarded completion and retained result

Canonical DataStoreConditionalSuccessor.Update reuses validated revision/exact-field
guards for a second existing record. EntityConditionalUpdateDataStore is opt-in;
in-memory staged snapshots and canonical SQL transactions return authoritative
readback for both records. Old Create/Bind and overlap/ownership guards remain.
This is not general UnitOfWork participation or an unguarded side-effect Save.

WorkflowCompletionDataStorePersistence validates host-owned instance history and
claim token against exact issued work and typed semantic result. One transaction
appends the unsuspended entry, completes the Continuation and retains strict
result/ExecutionEvidence bytes with the completing revision; issued bytes are
preserved. Missing/foreign/stale facts, lossy codec and oversized exact guards
reject before writing. Existing optional dispatch evidence remains compatible.
Recovery checks adjacent history even after later progression and never replays.
Applied unadmittable readback is transaction-indeterminate.

Focused-254 passed 77/77 tests in nine suites, including all 21 new S5 examples.
Regression-255 completed 127 suites with 1054 succeeded, 0 failed/aborted,
8 existing MySQL cases canceled and 5 placeholders pending. Both accepted
receipts bind the same complete tree and nine owned source hashes, with
SBT/wrapper exit 0 and lock=released. Earlier prerequisite/fixture failures and
252 tree-drift rejection are retained in the
[S5 journal](../journal/2026/09/2026-09-29-phase-88-guarded-completion.md).

This retains generic ExecutionEvidence, not separate application AdmissionEvidence
payload/scope. Active UnitOfWork event/data/closing-program enlistment, production
claim/Factory shared-service binding, registered application Operation/launcher/
server/MCP composition, separate-process restart, management visibility and
downstream artifact consumption remain open. No AI or command runs inside the
record transaction. Phase 88 remains in progress.

## Slice 88-S6: Retained application AdmissionEvidence payload/scope

CandidateAdmissionSubmissionJsonV1 supplies one explicit result PayloadCodec
for the existing typed AdmissionSubmission, composing existing evidence JsonV1.
Candidate identity/revision, typed candidate, ordered evidence payload/scope,
requirement identity and provenance remain exact. Undeclared JSON fields,
incompatible schema/type/nested identity, duplicates and lossy/throwing codecs
reject before completion. Application scope has no CNCF-interpreted meaning.

The unchanged S5 guarded transaction retains that submission with generic
ExecutionEvidence, Completed claim and instance history. There is no separate
evidence store, third write, side-effect Save, new SQL or completion service.
Read-only recovery requires admitted catalog/codecs and grants no admission,
progression, claim or command authority. Shape-valid missing/stale/wrong-target/
narrow evidence remains retained and current application EvidencePolicy determines
reuse or typed Pending gaps. Host completion records collection, not acceptance.

CandidateAdmissionSubmissionJsonV1Spec has five examples, including 50 generated
payload/scope round-trips. CandidateAdmissionCompletionPersistenceSpec has nine
examples covering memory/SQLite retention, resource reopen, policy changes,
claim/replay rejection, rollback, indeterminate readback and incompatible recovery.
Focused-257 passed 36/36 tests in five suites; selected regression-258 completed
129 suites with 1068 succeeded, 0 failed/aborted, 8 existing opt-in MySQL examples
canceled and 5 placeholders pending. Both accepted receipts bind the same complete
tree and three new source hashes; all nine S5 sources are unchanged. Exits are
0/0 with lock=released. The initial 256 fixture type-inference compile failure
and exact invocation/source evidence are retained in the
[S6 journal](../journal/2026/09/2026-09-29-phase-88-admission-evidence.md).
The paired contract is
[Candidate-Admission submission retention](../spec/candidate-admission-submission-retention.md).

This closes only bounded application evidence retention. Active UnitOfWork
event/data/closing-program shared transaction enlistment, production claim/Factory
service/Operation/launcher/server/MCP, separate-process recovery, downstream artifact
consumption and management remain open. Phase 88 remains in progress.

## Slice 88-S7: Prepared canonical Workflow record commits

WorkflowPreparedDataStoreTransition exposes the existing guarded suspension and
completion plans before publication. prepareSuspensionC/prepareCompletionC may
read/admit facts but write no records and acquire no transaction/claim/lease.
withSideEffectsC admits explicit normalized same-store canonical record effects,
preserving root/successor guards, ownership and correlation; malformed, duplicate,
overlapping, excessive or unsupported effects reject before publication.
commitC performs one conditionalTransition and the existing exact readback.
The old immediate APIs delegate to prepare then commit with no added effects.
Guards are rechecked at commit; uncertain provider/readback/projection failures
remain transaction-indeterminate, not rollback or automatic retry authority.

Twelve new prepared-transition examples, two issued-work examples and three
completion examples cover memory/SQLite nonpublication, single commit, effect
admission, stale/replay/claim movement, rollback/reopen and uncertainty, including
50 generated normalized local payload round-trips. Focused-259 passed 70/70 tests
in seven suites; selected component/workflow/datastore/unitofwork/event
regression-260 completed 141 suites with 1158 succeeded, 0 failed/aborted,
8 existing opt-in MySQL examples canceled and 9 placeholders pending.
Both accepted receipts bind the same complete tree and seven owned source
hashes, exits 0/0 and lock=released. The three S6 sources are unchanged; the
existing adapters/specs are intentionally refactored. Exact evidence is in the
[S7 journal](../journal/2026/09/2026-09-29-phase-88-prepared-record-commit.md)
and the paired [prepared-record contract](../spec/prepared-workflow-record-commit.md).

This is a bounded Work B prerequisite, not full active UnitOfWork recovery.
No EventEngine/UnitOfWork source, canonical datastore kernel, Factory or Cozy
source is changed. EventRecordStore normalization, staged active UoW data,
closing-program enlistment and AtomicWorkflowSuspensionEngine remain open.
Preparation is process-local and does not provide durable or portable authority.
Production claim/Factory/service/Operation/launcher/server/MCP, separate-process
restart, downstream artifact consumption and management remain open.
Phase 88 stays in progress; no commit/publication/deployment is selected.

## Slice 88-S8: Create-only canonical side records

EntityVersionedSideEffect.Create is implemented as a closed normalized
same-store record effect. Memory replacement-state publication and SQL native
insert reject an existing identity without overwrite and roll back the guarded
records plus earlier effects. Save/Delete retain their existing semantics.
Ordinary versioned mutation and entity/content-body normalization share the
closed algebra; SQL schema preparation is not represented as rollback-safe DDL.

Fourteen new examples cover memory/SQLite, collision after Workflow preparation,
independent SQLite writers with distinct roots, resource reopen and generated
payloads. After historical 262-264 tree-drift receipt rejections, focused-265
passed 57/57 in six suites; regression-266 completed 142 suites with 1172
successful tests, zero failed/aborted, eight existing opt-in MySQL examples
canceled and nine existing placeholders pending. Accepted receipts bind the
same complete SM/CNCF/Cozy tree and eight owned source hashes, exits 0/0 and
lock=released. This closes bounded S8 / downstream P2-U11 only.
See the [S8 journal](../journal/2026/09/2026-09-29-phase-88-create-only-side-records.md)
and the paired [record contract](../spec/entity-conflict-and-conditional-transition.md).

This is a Work B prerequisite, not an EventStore, sequence allocator or active
UoW/closing-program shared transaction. Preserve historical rejected evidence
alongside the accepted producer receipts. Cozy source, commits, publication and downstream artifacts remain
outside this bounded change. Phase 88 remains in progress.

## Slice 88-S9: bounded EventRecord encoding

EventRecordJsonV1 supplies pure canonical encoding, strict recovery and a
persistent transactional Create-only bridge. Typed ID/time/payload/attributes
and already-assigned positive sequence are exact. Unsupported values and
internal committed delivery keys reject; no allocator/dedup authority is added.
Twelve executable examples cover pure admission, generated values, memory/SQL
joint record composition, collision rollback and close/reopen.

Following accepted Cozy classpath refresh-268 and scoped repair of five
focused-269 failures, focused-270 passed 50/50 in six suites and regression-271
passed 1184 tests in 143 suites, with zero failed/aborted, eight existing MySQL
cases canceled and nine placeholders pending. Both accepted receipts bind one
complete SM/CNCF/Cozy tree and four owned S9 identities, exits 0/0, lock=released.
The repair rejects unresolved EventId options, uses opaque canonical value keys
and wraps canonical JSON in a strict non-JSON storage prefix. Existing SQL
decoding remains unchanged. Bounded P2-U12 is accepted; full P2-U05 stays open.
See the [S9 journal](../journal/2026/09/2026-09-29-phase-88-event-record-storage-codec.md).
No EventStore allocator/delivery-key durability, active UoW or closing-program
enlistment, production binding/Operations or downstream artifact refresh is
claimed. Historical rejected-267 evidence remains unaccepted.

## Slice 88-S10: bounded prepared EventRecord batch

`WorkflowPreparedDataStoreTransition.withEventRecordsC` attaches one nonempty
batch of fully issued persistent transactional EventRecords to the existing
canonical prepared record plan. The batch requires distinct EventId values and
strictly increasing, distinct positive assigned sequences. It rejects an
already-used event collection, invalid codec facts and foreign-store effects
before publication. The containing canonical guarded transaction inserts all
event Create records with the Workflow records, or rolls everything back on a
late identity collision. No independent EventStore write occurs.

Six new memory/SQLite executable examples cover no precommit publication,
two-event readback, invalid/duplicate/unordered batch rejection and late
collision rollback. The first focused attempt-272 had 43/44 raw successes but
one spec fixture compared Record instances rather than their retained bytes;
its receipt also rejected tree drift. Attempt-273 stopped before tests on a
stale Cozy development classpath. After accepted validation-only Cozy classpath
refresh-274, focused-275 passed 44/44 and regression-276 passed 1190 tests.
The batch was then tightened to validate EventRecord with the S9 codec before
deriving its Create key, so unresolved EventId options cannot generate hidden
identity during preflight. Concurrent Cozy source edits made the classpath
stale for focused-277 before tests; accepted Cozy refresh-278 restored it.
Final focused-279 passed 44/44 in three suites and regression-280 passed 1190
tests in 143 suites, zero failed/aborted, eight existing opt-in MySQL cases
canceled and nine placeholders pending. The final accepted receipts
bind the same complete SM/CNCF/Cozy tree
`88bbea9f991d101e33b853c506b6904208b4742b0cf4217854d80000ec3a78ea`,
exits 0/0 and lock=released. Bounded P2-U13 is accepted; P2-U05 remains open.
See the [S10 journal](../journal/2026/09/2026-09-30-phase-88-prepared-event-batch.md)
and [event storage contract](../spec/event-record-canonical-storage.md).

S10 does not reserve a global sequence, retain committed-delivery lookup keys,
implement EventStore append/query/replay, intercept active UnitOfWork data
effects, enlist the closing program or supply AtomicWorkflowSuspensionEngine.
No production binding, Operation, launcher, separate-process recovery or
downstream artifact refresh is claimed.

## Slice 88-S11: event readback admission

The prepared event batch now retains its exact canonical Create bytes and
checks every event by identity on the same borrowed datastore after an
acknowledged guarded commit. Missing or divergent bytes prevent a success
projection and classify the already-possibly-committed outcome as
transaction-indeterminate. Two fault-injection cases cover missing and
divergent post-commit readback. Focused-281 passed 45/45 in three suites;
regression-282 passed 1191 tests in 143 suites, zero failed/aborted, eight
existing opt-in MySQL cases canceled and nine placeholders pending. Both
accepted receipts bind the same complete tree
`99047c6527bbcfd4c8369ee2b0b33f9e46892adeb6f63a281f5b8ee9dd65cdf9`,
with exits 0/0 and lock released. S10 receipts remain historical; bounded
P2-U14 is accepted. It does not provide a global sequence allocator,
EventStore or active UnitOfWork domain, so P2-U05 remains open. See the
[S11 journal](../journal/2026/09/2026-09-30-phase-88-event-readback-candidate.md).

## Slice 88-S12: multi-guard transaction prerequisite

The pure input contract and its executable specification passed focused-286
(12/12 tests in two suites) after Cozy's PathMap-compatible classpath refresh
in prerequisite-285. Internal same-owner memory staging then passed focused-287
(16/16 across the new and existing suites); it is not exposed as a provider
capability. Cross-owner host authority, SQLite, authoritative readback and
independent-writer behavior are still open, so S12 and P2-U15 remain open.
Earlier focused-283 stopped on a corrected test-fixture
private-copy compilation error; retest-284 stopped in Cozy Information
generation on missing `TreeMap$` before tests. See the
[S12 contract journal](../journal/2026/09/2026-09-30-phase-88-s12-contract-candidate.md).
The [memory staging journal](../journal/2026/09/2026-09-30-phase-88-s12-memory-staging.md)
records the bounded internal result.
The [route-first correction](../journal/2026/09/2026-09-30-phase-88-s12-route-contract.md)
requires Workflow → `DataStoreSpace` → one physical provider and its
executable admission/atomicity/readback spec before further provider work.
The existing two-record conditional plan cannot guard a third global
EventStore sequence cursor through a Create-only side effect. The next
canonical provider capability must admit additional independently revised
guards in one physical transaction, including their provider-domain and
cross-component authority checks. Its [design boundary](../design/workflow-multi-guard-transaction.md)
and the consumer's P2-U15 are open. A sequence cursor, delivery-key index,
active UnitOfWork enlistment and P2-U05 remain later gates.

### Selected-event persistence correction (2026-09-30)

The Subsystem-owned EventStore declaration is selected as the storage/authority
source, not a persist-all directive. Workflow now has a pure versioned
`EventPersistencePolicy` / `WorkflowEventSelection` candidate for exact kind
selection, independent of destinations and owner/collection permissions.
Only selected persistent events enter cursor/event/index preparation.
Transient delivery waits for commit and authoritative readback, is discarded
on rollback, and is suppressed on an indeterminate outcome; it has no durable
replay/restart guarantee. Nontransactional emit is outside this API.

Focused-291 stopped before tests on a stale Cozy runtime classpath;
prerequisite-292 refreshed that classpath. Focused-293 then passed **45/45
tests across five suites**, including selection and Subsystem host separation,
with exits 0/0 and lock released. Host construction, no-declaration/unknown
grant denial, shared/split managed resources, escaped views, malformed
declarations and scope restoration are covered. A positive S12 provider
capability is still unsupported and fail-closed. Actual selected-only
persistence, cursor/index allocation and delivery integration remain open as
consumer P2-U16, alongside S12/P2-U15 and P2-U05. See the
[selection design](../design/workflow-event-persistence-selection.md).

Selected-only canonical Create preparation then passed focused-294 (30/30)
and final focused regression-295 (50/50 across six suites). It proposes
overflow-safe sequences only for the durable subset, retains canonical bytes,
preserves unsupported private delivery keys for codec rejection, and admits
mixed-order delivery candidates only on exact complete durable readback.
This pure producer/comparison is not allocation, commit evidence or dispatch.
Actual S12 provider, index/cursor composition and P2-U16 remain open.

The internal admitted opt-in route then passed settled-tree focused-299
(60/60 in seven suites), after Cozy prerequisite-297 and correction of three
test expression line breaks. Attempt 298 has no valid receipt and is not
acceptance evidence. Workflow retains DataStoreSpace; opaque provider dispatch
rechecks lease, issuing thread, identity and policy after collection lookup.
The positive fixture counts one NotMatched invocation without writes; neither
real memory nor SQLite opts in. Exceptions are transaction-indeterminate with
no automatic replay. Atomic commit/rollback/readback and selected delivery
remain unclosed. See the [route journal](../journal/2026/09/2026-09-30-phase-88-s12-positive-route.md).

### Real same-owner memory route evidence (2026-09-30)

Focused-301 passed 65/65 tests in eight suites after correcting the new spec's
four nonexistent DataStoreSpace.load calls (terminal failed attempt 300).
Five test-only opt-in examples exercise the existing real memory staging
through Workflow → DataStoreSpace: three guarded updates and two Creates,
stale third guard/second Create collision rollback, four pre-publication
failure points, cross-owner rejection and explicit stale-plan resubmission.
Actual record comparisons are fixture diagnostics, not admitted Workflow
readback or completion/dispatch authority. No production provider/bootstrap
was enabled; S12, cross-owner/SQLite and selected delivery remain open.
See the [memory route journal](../journal/2026/09/2026-09-30-phase-88-s12-memory-route.md).

### EventStore ownership and complete-readback candidate (2026-09-30)

The corrected contract places event-kind persistence policy in EventStore:
ordinary senders specify no persistence option and unmatched kinds are transient.
Subsystem declarations remain storage/permission configuration. Configured-memory
append selects before mutation; Workflow consumes the store decision and freezes
its revision. Six new examples cover ordinary emit and selected-only retention.

The separate private verified multi-guard route checks every full ordered
postimage against coherent native-snapshot readback, rechecking lease authority
before verified return. Seven new examples cover readback/acknowledgement faults
and unsupported routes. No production provider opts in; no Workflow completion
or dispatch authority is conferred by this storage evidence.

Both candidates have now compiled and passed focused-309: 107/107 tests in
14 suites, including seven complete readback and eight EventStore selection
examples. Cozy prerequisite-306 succeeded first. Two test-fixture errors were
corrected; production behavior was unchanged. The user-directed parent execution
route retained shared SBT serialization and platform review. Exit 0/0 and
lock=released were observed. This is focused evidence, not typed Phase/PLAN
acceptance, full regression or production runtime integration.
S12/P2-U15/P2-U16/P2-U05 remain open. See the
[resumed validation journal](../journal/2026/09/2026-09-30-phase-88-focused-validation-resumed.md).

Two subsequent ordinary-send examples cover transient/durable identity isolation
and first-record durable deduplication; the configured EventStore suite now has
eight examples, all now passing focused-309. The earlier assertion that the
platform permitted no recovery was incorrect: its original response admitted
informed approval, subsequently obtained. Prerequisite and validation are resumed.
See the [follow-up journal](../journal/2026/09/2026-09-30-phase-88-eventstore-ordinary-send-boundary.md).

### Native memory acknowledgement (2026-09-30)

Focused-310 passed 144/144 in 18 suites, including existing conditional
transition regressions. Same-owner memory captures the full ordered native
staged post-images before publication and returns them only after success;
the verified fixture consumes that acknowledgement instead of reconstructing
it with preloads. Two new immutable-result/no-publication examples bring
readback to nine cases. Exit 0/0 and lock=released were observed. No production
opt-in, SQL, cross-owner or dispatch activation is claimed. S12 remains open.
See the [native acknowledgement journal](../journal/2026/09/2026-09-30-phase-88-native-memory-multi-guard-acknowledgement.md).

### Internal SQLite multi-guard candidate (2026-09-30)

Focused-316 passed 164/164 in 20 suites after prerequisite-312 and explicit
SQLite physical/logical Record adaptation. Eleven SQLite route cases exercise
Workflow/DataStoreSpace with a test-only opt-in, three guards/two Creates,
record and schema rollback, independent writers, reopen and uncertainty.
Complete readback now has eleven cases; duplicates and extra values still reject.
Exit 0/0 and lock=released were observed. No production provider opt-in,
cross-owner/managed host activation or selected event dispatch is enabled.
Snapshot-phase writer competition was untested at 316; S12/P2-U15/P2-U16/P2-U05
remain open. See the [SQLite route journal](../journal/2026/09/2026-09-30-phase-88-sqlite-native-multi-guard-route.md).

### SQLite snapshot-phase writer evidence (2026-09-30)

Focused-317 passed 165/165 in 20 suites. The SQLite route suite now has twelve
examples: the new latch-controlled case attempts an independent cursor UPDATE
between native snapshot reads, observes exact SQLITE_BUSY, verifies the original
complete post-images, then proves a separate later write succeeds after snapshot
closure. Retained verified records keep their earlier revision. No production
source or capability activation changed. Exit 0/0 and lock=released were observed.
This closes the internal snapshot evidence gap only; managed host/cross-owner and
selected dispatch remain open. See the [snapshot journal](../journal/2026/09/2026-09-30-phase-88-sqlite-snapshot-writer-evidence.md).

### Managed host verified-route authority boundary (2026-09-30)

Focused-318 passed 169/169 tests in 20 suites. Four additional real Subsystem
examples confirm caller binding preservation, thread/lease lifetime constraints,
exact declaration checks, provider replacement denial and exception cleanup.
The managed verified capability remains unsupported; no successful cross-owner
commit or production activation is claimed. See the
[host boundary journal](../journal/2026/09/2026-09-30-phase-88-managed-host-authority-boundary.md).
S12 and selected cursor/index/event runtime/dispatch remain open.

### Explicit managed verified SQLite route (2026-09-30)

Focused-319 passed 173/173 tests in 20 suites. A separate internal Subsystem
path borrows one verified managed SQLite view, preserving host invocation
authority through native commit. Real cross-owner guarded updates and event/index
fixture Creates commit, verify and recover after reopen; stale cursor and index
collision roll back the entire composition. Ordinary views remain opt-out.
Managed concurrency/revocation and canonical selected-event runtime/dispatch
remain open. See the [managed provider journal](../journal/2026/09/2026-09-30-phase-88-managed-verified-provider.md).
S12/P2-U15/P2-U16/P2-U05 are not complete.

### Selected canonical managed storage composition (2026-09-30)

Focused-320 passed 176/176 tests in 20 suites. Store-owned selection, canonical
events and guarded cursor proposals compose through the real managed verified
route. Durable-only sequences and absent transient records survive reopen;
cursor mismatch and second event collision roll back. Verified event bytes yield
mixed-order candidates, not a dispatch permit. Versioned global cursor/index,
actual delivery and all-transient runtime completion remain open. See the
[composition journal](../journal/2026/09/2026-09-30-phase-88-selected-managed-canonical-events.md).
S12/P2-U15/P2-U16/P2-U05 are not complete.

### Versioned cursor/delivery-index design candidate (2026-09-30)

The [new contract](../spec/event-cursor-and-delivery-index.md) defines a
host-pinned versioned cursor, full typed delivery discriminator in a separate V2
event envelope, explicit-key-only index and retained-first duplicate semantics.
Codec/executable admission, host cursor identity and managed runtime remain open.
V1 and legacy memory behavior are unchanged. Documentation-only work required
no SBT; focused-320 remains implementation evidence, not codec acceptance.
See the [design journal](../journal/2026/09/2026-09-30-phase-88-event-cursor-delivery-index-contract.md).

### Pure versioned cursor codec (2026-09-30)

EventCursorRecordV1 and seven executable examples passed focused-323 (183/183
in 21 suites), after Cozy classpath prerequisite-322. Canonical decimal-text
sequence, strict logical field/type decoding, bounded immutable proposals,
zero-work and overflow rejection are covered. Initialization/reset authority,
host cursor identity, managed recovery and V2/index/runtime remain open. See the
[cursor journal](../journal/2026/09/2026-09-30-phase-88-event-cursor-codec.md).

### Host-pinned managed cursor initialization (2026-09-30)

Focused-325 passed 188/188 tests in 21 suites. Exact host cursor identity is
enforced, with explicit Create-only initialization and separate strict managed
loading. Reopen/retained progress, one concurrent winner and malformed/legacy
non-repair cases pass. Existing rows reject before Create/schema expansion.
Versioned guarded progression, V2/index codecs and actual dispatch remain open.
See the [bootstrap journal](../journal/2026/09/2026-09-30-phase-88-event-cursor-host-bootstrap.md).

### Versioned cursor / selected-event guarded progression (2026-09-30)

Focused-327 passed 191/191 in 21 suites. Internal pure host-configured assembly
connects loaded V1 cursor facts to selected canonical event Creates and exact
revision/schema/decimal-text guards in one managed verified multi-guard plan.
Three host examples cover above-2^53 sequence/reopen, strict cursor readback,
stale-root NotMatched, four rollback conflicts and zero durable cursor work.
No index/V2 codec, all-transient completion, dispatch/CLI activation or full
Phase acceptance is claimed. S12/P2-U15/P2-U16/P2-U05 remain open. See
[progression journal](../journal/2026/09/2026-09-30-phase-88-versioned-cursor-guarded-events.md).

### Pure typed delivery key / V2 / index codecs (2026-09-30)

Focused-329 passed 199/199 in 22 suites after a test-helper DSL collision in
compile-only failed-328. New bounded structural key, separate V2 envelope and
strict index codecs preserve full typed identity without printed-ID parsing.
V1 and memory behavior remain unchanged; eight examples include target/options/
field order, malformed bytes, full-key/reference integrity and SQLite recovery.
This does not activate V2/index managed Create composition, deduplication or
dispatch. S12/P2-U15/P2-U16/P2-U05 remain open. See
[codec journal](../journal/2026/09/2026-09-30-phase-88-event-v2-delivery-index-codecs.md).

### New-only managed V2 event/index/cursor composition (2026-09-30)

Focused-331 passed 204/204 in 23 suites after one missing spec import in
compile-only failed-330. Explicit V2 preparation composes selected V2 events
and keyed indexes with the host-pinned cursor and Workflow root/Continuation in
one managed native transaction. Five host examples cover commit/reopen, ordered
typed candidates, late-index/cursor rollback, reused-key admission rejection,
missing/divergent index suppression and all-transient no-work. V1 is preserved;
native Verified alone is not semantic index admission or dispatch permission.
Retained-first coherent duplicate classification, races/revocation, verified
two-record completion and configured service/dispatch remain open, as do
S12/P2-U15/P2-U16/P2-U05. See
[managed V2 journal](../journal/2026/09/2026-09-30-phase-88-managed-v2-selected-events.md).

### Pure EventId-first retained-fact classifier (2026-09-30)

Focused-335 passed 212/212 in 24 suites after enum naming repair-332 and stale
Cozy classpath-333 recovery through compile/export-334. Eight pure examples prove
retained-first/no aliases, divergent ID/key rejection, batch-local first-new
sequence consumption, inventory integrity and canonical immutability. Supplied
inventories grant no snapshot/absence authority; no managed commit adapter was
added. Existing unique-ID policy and new-only key collision behavior stay intact.
Host coherent lookup/absence/cursor provenance, guarded retained commit/readback,
all-retained validation and dispatch remain open, as do S12/P2-U15/P2-U16/P2-U05.
See [classifier journal](../journal/2026/09/2026-09-30-phase-88-event-duplicate-classifier.md).

## Goal

Provide CNCF runtime support for long-lived Workflow server applications that use the normal Entity/Aggregate/View facilities, while preserving the same Workflow semantics for one-shot launcher/CLI execution.

The first representative consumer is sm-workflow. The implementation should reuse the application-runtime patterns proven by textus-art-scene rather than introducing a Workflow-specific storage/application framework.

## Required capabilities

1. **Long-lived server runtime**
   - host Workflow application services in a continuously running CNCF/Textus process;
   - keep admitted master/reference data resident through canonical Collection/View facilities;
   - reuse normal ComponentFactory/provider/bootstrap and configuration boundaries.

2. **Workflow Aggregate/View integration**
   - expose Workflow instance/progression data through canonical Aggregate/View read models where appropriate;
   - support efficient enumeration/filtering needed by Phase 87 Workflow Management;
   - avoid a UI-only or server-only shadow registry.

3. **Resident reference/master data**
   - allow Workflow definitions, application profiles, policy/reference data, and other stable lookup data to be loaded once and reused by server requests;
   - retain datastore/version authority and explicit refresh/reload semantics;
   - do not make an in-memory cache the source of truth.

4. **Shared application service**
   - server transports and launcher/CLI invoke the same typed Workflow application service;
   - server lifetime is an optimization/operational mode, not a different Workflow model.

5. **One-shot execution**
   - the same component remains runnable through the CNCF launcher/CLI without a pre-existing server;
   - one-shot mode bootstraps the required runtime, loads required reference data, invokes the exact typed Operation, persists/returns the result, and shuts down cleanly;
   - durable Workflow instances created in one-shot mode remain observable when the configured durable provider is shared.

6. **Presentation neutrality**
   - REST/Web/MCP/CLI are adapters over the same typed service/runtime truth;
   - MCP is not required for CLI execution and CLI is not required for server execution.

## Runtime shape

    Component / Application
      |
      +-- ComponentFactory / providers
      +-- Entity / Aggregate
      +-- View / resident reference projections
      +-- Workflow application service
      |     +-- start
      |     +-- submit result / decision
      |     +-- advance
      |     +-- status/history/result
      |
      +-- Server host
      |     +-- REST/Web
      |     +-- MCP adapter
      |
      +-- One-shot launcher/CLI
            +-- same typed application service

## sm-workflow acceptance target

The support is sufficient when sm-workflow can:

- run as a long-lived server;
- keep its stable workflow/profile/reference data resident through CNCF collection/view support;
- persist and query Workflow instances through canonical runtime contracts;
- expose Skill-facing MCP without embedding Workflow semantics in the MCP adapter;
- appear in the generic Phase 87 Workflow Management Web surface;
- execute the same typed operation from launcher/CLI when no server is running.

## Non-goals

- sm-workflow application policy or GoalPhase semantics.
- A second Workflow engine for server mode.
- Mandatory Job wrapping of Workflow execution.
- Requiring a daemon for local/CI usage.
- Treating cached master/reference data as authoritative mutable state.
- Duplicating generic Entity/Aggregate/View facilities.

## Implementation note

Before coding, inventory the current textus-art-scene server bootstrap, Aggregate/View use, resident/reference-data loading, datastore lifecycle, and standalone restart patterns. Reuse the CNCF-level mechanism where it is generic; keep ArtScene-specific policy out of CNCF.

## References

- [Phase 77](phase-77.md)
- [Phase 87](phase-87.md)
- [Workflow Server Application Support Note](../notes/workflow-server-application-support.md)
- [Decision journal](../journal/2026/09/2026-09-20-workflow-server-application-and-cli-dual-mode.md)
