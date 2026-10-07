# All-retained validation-only completion

Date: 2026-09-30
Status: initial managed native implementation; bounded focused evidence

Continued the active sm-workflow Phase 2 / admitted CNCF Phase 88. Previous turn
progressed with focused-350 prepared-route rollback/isolation. Retained the
cncf-goal-phase contract and existing simplified parent recovery override.

Added a separate EntityRetainedEventDataStore opt-in and opaque original-host
RetainedEventInvocation. It admits only an effect-free authorized Workflow pair
and a captured preparation with zero genuinely new durable facts. Host/view,
provider, thread and lease identity are rechecked; lookup admission covers all
observation collections, including absent targets. No legacy empty-guard plan,
dummy cursor update, sequence allocation, event/index Create or fallback.

Managed SQLite validates EVERY frozen observation on the same native write
connection before staging, updates only the Workflow pair, captures transaction
post-images including the unchanged cursor and retained event/index, and performs
one coherent complete postcommit readback before returning storage Verified.
The separate seam rejects uncertainty without retry and grants no UoW/dispatch
permission. Legacy multi-guard's nonempty guarded-update contract is unchanged.

One new Given/When/Then scenario has five actual managed configurations:
normal all-retained alias with unsupported incoming payload, exhausted cursor
revision/sequence at Long.MaxValue, stale cursor, missing retained index, and
new-durable input mistakenly supplied to this route. Normal/exhausted cases
update the Workflow pair, verify five records and preserve every cursor field;
explicit resubmission is NotMatched without cursor advance. Stale/new cases
reject with open Workflow pair and unchanged externally modified cursor.
No alias/new event is created. This is storage-only evidence.

Focused-351 passed 233/233 in 26 suites. Then expanded entry provider comparison
to include absent observation collections as well as present readback targets;
focused-352 passed the same 233/233, zero failed/aborted/canceled/ignored/pending,
SBT/wrapper 0/0 and lock=released. Both summaries/logs read; final invocation,
cwd and unchanged 26-suite argv match. No source edits after final validation.
Shared serial wrapper, scoped platform permissions and normal caches retained.
No commit/publish/skill/agent edit/full review/typed acceptance/goal closure.
Invocation: SMWF-CNCF-RETAINED-COMPLETION-DIRECT-20260930-352.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-010a04a6cf7fd27ee25a1a14ad07204bbb0ddc8d4bbd3232fefb0c3a500cbe6b-90501aa0c6636b7087d94e2e71697f57/53958-20260930T120745Z.log

Validated SHA-256:
- EntityRetainedEventDataStore.scala: 5f36a22c40fe40fda9705b6268f2cc2623f2b10541891b36e582f97ee2f5ae6b
- DataStoreMultiGuardReadback.scala: 13b9dccce2ad35e3966a070edbbe710be2aebfb0a0fde67abf0e342a1bc2659a
- DataStoreConditionalMultiGuardHostPolicy.scala: 27a37e30626acd78f9ad006615172fe84711f7447afba676bfec97dfac195976
- DataStoreSpace.scala: 18199613efe63b64876e0e914811c741a1a585564913af80a546f780e0b3fc85
- SqlDataStore.scala: 77272bed1f7455c85cf10ed6066a10c09808f30be0e109a7b740c392ba2872fe
- DataStoreObservationSnapshotSpec.scala: ea6fe7988f9cc1e63a16c6cbd28164937153cdc0ba006496bbba5dcf647ee997

Next: retained-route-specific rollback/revocation/competition/readback uncertainty
and authority specs, including absent-only index lookup and key-free retained
facts. Mixed-route evidence cannot substitute. Transient-only/empty completion
with no cursor work is still unimplemented. A common host/runtime operation must
choose these routes deterministically from its captured EventStore selection;
public skills/AI must not choose the storage branch. Actual application/UoW
completion and dispatch, production assembly/CLI/MCP/restart/management remain
open. S12/P2-U15/P2-U16/P2-U05 remain open; Phase goal stays active.
