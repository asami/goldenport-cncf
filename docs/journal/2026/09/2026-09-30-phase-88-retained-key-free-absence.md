# Retained key-free facts and index absence

Date: 2026-09-30
Status: bounded executable evidence; not Phase acceptance

Continued active Phase 2 / admitted CNCF Phase 88 after focused-359 authority
validation. Existing simplified parent recovery and shared SBT serialization
remain in force. Changed only DataStoreObservationSnapshotSpec; no production edit.

One new Given/When/Then scenario exercises four real managed SQLite cases:
key-free retained V1, key-free retained V2, ID-retained V1 with an incoming key
whose lookup index is absent, and that absent index created after observation.
All incoming payloads are unsupported for new encoding; existing bytes win.
All classifications have zero new durable facts.

The first three cases verify exactly four records (Workflow pair, unchanged
cursor and retained event). No index appears in readback or is created by
completion. Pair closes; every cursor 2/1 and event field remains exact.
The stale-absence case is rejected before Workflow publication: pair stays open,
cursor/event fields unchanged and the explicitly created competing index remains
exact. No reclassification, retry, sequence allocation or alias-index creation.

Attempt 360 failed the new scenario after the two key-free cases passed:
absence observation correctly rejected the unprovisioned index collection.
Corrected the fixture by creating/deleting the canonical candidate index BEFORE
capture for the two index-absence cases. This provisions its schema while leaving
the target absent. Missing collection is NOT treated as trusted row absence;
production admission was not weakened. This bounded case proves a provisioned
absent target that becomes present, not every schema-change race.

Focused-361 passed 240/240, 26 suites, zero failed/aborted/canceled/ignored/pending,
SBT/wrapper 0/0, lock=released. Both attempt summaries/logs were read; final exact
invocation/cwd/unchanged 26-suite argv and all four outcomes checked. No source
edits after final validation. No commit/publish/push, skill/agent edits, full
review, typed acceptance or Phase closure.

Invocation: SMWF-CNCF-RETAINED-ABSENCE-DIRECT-20260930-361.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-e419aae4b13237ddf552a04de27f70db3c9d4b7d4f1614c7c02fc787c7f9892f-510f301946c4bf779073ed9419a8d34e/7938-20260930T144948Z.log
Adjacent summary: 7938-20260930T144948Z.summary.json.
Validated DataStoreObservationSnapshotSpec.scala SHA-256:
482530e1000fcee56c46c48fb234cad39e4491773d5a74533e1bb0b0dbb315b1.

Next: retained invalid Workflow transition coverage, then transient-only/empty
completion without cursor work and deterministic common host/runtime routing.
Public skills/AI must not choose storage branches. Native cleanup/crash,
application/UoW dispatch, production assembly/CLI/MCP/restart/management remain
open. S12/P2-U15/P2-U16/P2-U05 stay unchecked; Phase goal remains active.
