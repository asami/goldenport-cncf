# Managed selected V2 events and delivery indexes

Date: 2026-09-30
Status: bounded new-only managed V2/index composition passed focused-331

Explicit trusted WorkflowCursorEventProposal.prepareV2C connects frozen
EventStore policy selection to the managed cursor route. Existing prepareC is
V1-only, with its keyed-fact rejection unchanged; no content sniffing, mixed
format fallback, automatic migration or configured service activation is added.

EventRecordJsonV2.prepareCreateC preserves the hidden typed discriminator when
proposing a sequence, then returns canonical recovered event facts plus Create.
No source mutation, reserved range, datastore or retry is introduced.
WorkflowSelectedEventRecords.prepareV2C proposes sequence only for persistent
facts, builds all V2 event Creates and index Creates only for keyed persistent
facts. Its encoder/decoder choice is fixed at construction by the trusted factory.
The V1 factory and defaults are unchanged.

Cursor proposal assembles Workflow root/Continuation, one exact host-pinned
versioned cursor guard, event Creates and index Creates into one plan. The
existing live DataStoreSpace -> same physical managed SQLite provider/native
transaction -> complete postcommit snapshot route remains mandatory. Events
precede indexes in staging, so a late index collision must roll back all earlier
event/index/cursor/Workflow writes. Existing combined side-effect bound (64) and
target distinctness remain unchanged and fail closed; no automatic splitting.

Verified candidate projection strictly admits the cursor, then every planned
index's complete logical fields, exact entry/full key, reference, sequence and
digest, and its exact selected V2 envelope. Canonical durable events are compared
to frozen proposal bytes and mixed transient candidates retain original order.
The public pure admitDeliveryCandidatesC comparison is still not commit/index
authority; the trusted cursor composition uses the complete verified route.
Storage Verified does not grant application completion or dispatch permission.

Five actual host examples:
- Custom cursor identity; three selected events (two keyed, one key-free), two
  transient events (one keyed), two indexes, eight complete verified targets.
  Original order/keys/large typed payloads are retained, no transient event row,
  immutable input sequences and stale-root NotMatched. Cursor, V2 bytes and
  index/reference integrity survive Subsystem close/reopen. V1 remains rejecting.
- Late second-index collision or stale cursor leaves all guarded/event/index
  pre-state unchanged; no first event or first index leaks.
- Reused batch-local key with different EventIds produces duplicate index targets
  and rejects plan admission without deduplication, writes or cursor movement.
- Submitted low-level plans with missing indexes or modified digest, noncanonical
  sequence, different reference or key can have valid native storage evidence;
  semantic candidate projection rejects all five, without postcommit repair.
- All-transient keyed input produces no event/index Creates or cursor guard,
  even with exhausted counters; multi-guard completion still rejects.

Focused-330 compiled production sources but failed test compile at the new
spec's missing EventStorePersistenceConstruction import (line 63); no tests ran.
Only that import changed for focused-331.
Focused-331 passed 204/204 tests in 23 suites; no failed/aborted/canceled/
ignored/pending cases. SBT and wrapper exit=0; lock=released.
Invocation: SMWF-CNCF-V2-MANAGED-DIRECT-20260930-331.
Log: /tmp/skill.cncf.d/cncf-sbt-2c9e904ebeafcde5ca2545697c19b66d068f7a55a4646ad6d67aa367a9d232d9-b5b83fd1a1517ef09876711145157544/63815-20260930T082133Z.log
Adjacent .summary.json was read and matched invocation, exact cwd/argv,
counts, exits and lock evidence. No code changed after that successful run.
Validated SHA-256:
- EventRecordJsonV2.scala: 3742b8eae868836f9425e19ab155b783ece908651851d4eec794b150d9d45727
- WorkflowSelectedEventRecords.scala: 9e889013be8a0fc56fd06f4516e6892458116675d3a061f4a7a6cc0a29206b4d
- WorkflowCursorEventProposal.scala: 5357c74446e16a650b72b837434c75b17954fecee81bc6234730c1ec8203fbbc
- ManagedSelectedEventV2Spec.scala: 82599ab8ccec11f9de0e8cb2ff2076a8d5237f56952b3d5c68cbb333bc459aa0
Scoped platform review, fresh helper metadata, serial SBT lock and log/summary
are preserved under the user's simplified parent execution route.
No full-suite, independent review, typed Phase/PLAN acceptance, commit or
publication is claimed. S12/P2-U15/P2-U16/P2-U05 remain open.

Next: coherent EventId-first/full-key duplicate classification under the cursor
guard and retained-first semantics, managed races/revocation, verified
two-record all-transient completion and configured EventStore/application/dispatch.
