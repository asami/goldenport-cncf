# Selected canonical events on the managed storage route

Date: 2026-09-30
Status: bounded canonical composition passed focused-320; delivery/index runtime open

WorkflowSelectedEventRecords.deliveryCandidatesFromVerifiedStorageC now obtains
durable EventRecord bytes directly from the complete verified storage vector.
It matches exact collection and entry identity, decodes the canonical storage
wrapper and compares against the frozen selected proposal before interleaving
transient candidates. Null, missing and divergent verified event evidence reject.
This package-internal projection is not a dispatch permit, index admission,
cursor admission or Workflow/application completion.

Three new real Subsystem examples compose store-owned exact-kind selection,
canonical EventRecord Creates and a host-admitted cross-owner cursor guard
through the explicit managed SQLite verified route. The cursor's expected
last_sequence and revision are both guarded. Two durable events receive proposed
sequences 11 and 12 and move the fixture cursor from 10 to 12 atomically with
Workflow root/continuation updates. Sender persistence flags do not control
selection. Two transient/unmatched events remain sequence zero and have no
stored records, including after Subsystem reconstruction. Canonical bytes
retain a Long payload beyond exact floating-point integer range.

Actual verified bytes yield candidates in original mixed order. Cursor sequence
mismatch (even with the expected revision) or collision on the second canonical
event rolls back every update and earlier Create. A different proposal with
changed payload or absent identity cannot use another result's verified storage.

These examples use a test cursor composition, not a newly accepted versioned
global cursor schema or allocator service. They perform no dispatch. The codec
still rejects internal delivery lookup discriminators without admitted durable
encoding. No index encoding, all-transient runtime commit path, EventStore
replay service or generic callback was added. Recovery here checks canonical
DataStore records; it is not acceptance of EventStore.query/replay integration.

Invocation SMWF-CNCF-SELECTED-MANAGED-DIRECT-20260930-320 passed 176/176 tests
in 20 suites. Zero failures/aborted/canceled/ignored/pending; SBT exit=0,
wrapper exit=0, lock=released. Persistent summary confirms compile coverage.
Serial execution, fresh permission metadata and scoped platform review were
preserved under the user's simplified parent execution route.

Log:
/tmp/skill.cncf.d/cncf-sbt-3e0b4ea05d5e81523647d3a20a2b5085aeeadbc20b34396131421509b96f676b-fae2c4bf7b7b653ac4baa461911c4590/57017-20260930T065813Z.log

Validated SHA-256:
- WorkflowSelectedEventRecords.scala: fa6a58912bbda8ad9e7f7ab53606500e15927aa51a0bb958667e490538ebaf7a
- EventStorePersistenceHostSpec.scala: b7667cffd422a07308531b5e3cb589220f2569701c8b3f48bc52aed1e6f85659

Next: define/admit the versioned cursor and delivery-index representations,
including currently unsupported delivery-key facts, before implementing actual
mixed-order dispatch. Managed competition/revocation, dispatch suppression after
uncertain outcomes and all-transient completion remain open.
S12/P2-U15/P2-U16/P2-U05 are not complete. No full-suite, independent review,
typed Phase/PLAN acceptance, commit or publication is claimed.
