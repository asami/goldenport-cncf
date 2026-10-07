# Host-pinned cursor identity and Create-only initialization

Date: 2026-09-30
Status: bounded managed cursor initialization/recovery passed focused-325

EventStorePersistenceDeclaration captures an exact StringEntryId at host
construction (source-compatible default "global"; explicit custom identity
supported). Its policy pins the EventStore guard's owner, collection and entry.
A syntactically valid plan targeting another row rejects before provider dispatch.
Generic internal host-policy fixtures without pinned targets retain their existing
behavior; EventStore host assembly always supplies its cursor target.

Subsystem.initializeEventCursorC and loadEventCursorC are separate trusted,
package-internal managed SQLite paths. The ordinary loader never initializes a
missing row. Initialization first rejects any existing row, without entering
Create/schema preparation, then performs one Create of the versioned initial
record and strictly reads it back. No upsert/reset, migration, retry or automatic
loser recovery is supplied. A successful Create followed by failed readback is
transaction-indeterminate, not a rollback assertion.

Physical SQL identity is checked exactly before removing its reserved id field
for strict EventCursorRecordV1 logical decoding. Both methods borrow the same
SystemNode-owned resource through a live lease and require managed SQLite.
Declaration or returned facts do not grant ordinary Workflow write authority.
No public Operation/CLI bootstrap or launcher activation was added.

Five real host examples prove:
- Missing progression load rejects; explicit initialization yields revision 1
  and sequence 0; repeated initialization cannot reset retained revision 2/
  sequence 9, including after Subsystem shutdown/reconstruction.
- A custom host cursor identity rejects a plan selecting the default row and
  creates no second global row; null identity rejects.
- Concurrent initialization calls admit one Create winner with one node-owned
  resource. The loser can explicitly load retained facts; no automatic retry.
- Noncanonical retained sequence bytes reject without repair/reset.
- A retained legacy record lacking canonical columns rejects before Create,
  preserving its complete physical record without schema expansion.

Validation:
Focused-324 passed 187/187 in 21 suites before the existing-row preflight
correction. Focused-325 passed 188/188 in 21 suites after adding that correction
and legacy-schema example. Both had SBT/wrapper exit=0 and lock=released.
Final invocation: SMWF-CNCF-CURSOR-HOST-DIRECT-20260930-325.
Serial locking, fresh helper-owned permission metadata and scoped platform
review were preserved under the user's simplified parent execution route.

Final log:
/tmp/skill.cncf.d/cncf-sbt-fe0270bd5af4403542cc906a89da6ccbe6eae257c0a12920a04b012bdd5d097b-02e51f34df627c9440646aa2f58936e3/71298-20260930T072750Z.log

Validated SHA-256:
- DataStoreConditionalMultiGuardHostPolicy.scala: 69226192a6c7de68d894cbc9bb39d65430e1b73e6f677d4a63d518a69addd888
- EventStorePersistenceDeclaration.scala: bf97c95f3fd0c128afd4e3859196590b858da3e0970cbd9db9181f376d0eac50
- Subsystem.scala: 8f71fb3645a7d7845c96286ec6db9779d155dccc047cb3b9f2930f9b0aca741c
- EventStorePersistenceHostSpec.scala: 27cb3ff117a1870f1d45e6b31b2550a6ce307760cbe8dd17d8998e5e214eb619

Next: connect versioned decimal-text proposals to guarded selected-event commit
without using earlier integral cursor fixtures, then full typed V2/index codecs.
Managed revocation/competition during progression, full duplicate reconciliation,
all-transient completion and actual dispatch remain open.
S12/P2-U15/P2-U16/P2-U05 remain open; no full-suite, independent review or typed
Phase/PLAN acceptance is claimed. No commit/publication occurred.
