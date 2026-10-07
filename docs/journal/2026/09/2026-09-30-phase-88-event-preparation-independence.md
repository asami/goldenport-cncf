# Preparation independence and direct lifecycle evidence

Date: 2026-09-30
Status: focused executable evidence; publication remains unimplemented

Previous turn implemented immutable preparation and passed focused-345. This
turn extends DataStoreObservationSnapshotSpec with two behavior scenarios using
Given/When/Then adjacent to setup/action/expectations; no production source edits.
Retained CNCF Phase 88 scope and the simplified parent recovery override for the
active sm-workflow Phase 2 goal, without delegation/history approval loops.

The first scenario uses a captured policy-bearing EventStore, actual managed
SQLite, a keyed retained V2 event/index, a different EventId alias with the same
key and unsupported incoming payload, one new keyed event and one transient.
Host observation freezes exact retained bytes/index text, cursor 2/1 and ordered
absences; the unsupported retained payload is not encoded. Clearing alias and
new source keys and a decoded new-event projection's key leaves the immutable
classification/template and sequence proposal unchanged. Every populated Record
projection is a fresh object. Retained event/index fields and absence inventory
stay exact. Explicit later fixture writes change the actual cursor to 3/9 and
delete the retained index, but the preparation still describes its original
snapshot. These fixture writes are NOT preparation publication. This directly
exhibits stale evidence that the forthcoming write gate must reject, not repair,
reclassify, retry or treat as current storage. After scope release, direct
preparation recordsC and classificationC both reject.

The second scenario directly accesses the preparation from another thread:
both APIs reject, while the issuing thread still succeeds. It then uses the
actual inherited SystemNodeResourceLease and test-only revoke helper; both
issuing-thread APIs reject immediately after revocation. No synthetic lease,
production privilege widening or dispatch hook was introduced.

Focused-346: 227/227, 26 suites, zero failures/aborts/canceled/ignored/pending;
SBT/wrapper 0/0, lock=released. Summary and relevant persistent log scenarios
were read and match exact invocation/cwd/26-suite argv. No source edits after
validation. Shared serial wrapper, first-attempt scoped platform escalation and
normal caches retained. No publish, commit, skill/agent edit or goal closure.
Invocation: SMWF-CNCF-PREPARATION-INDEPENDENCE-DIRECT-20260930-346.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-31da603aab3626999ab03a1bc3598caedd481437150e74bd7b41f1aa84c11964-db6ca8d6f3c3936674cd571e4dd2f9d1/40578-20260930T113119Z.log
Validated DataStoreObservationSnapshotSpec.scala SHA-256:
31303a0d304bc61a4becc4b0f665cfb16acae9f466b194ce64fb92694624b146.
Production preparation/invocation sources unchanged from focused-345.

Next implementation basis: SqlDataStore._conditional_multi_guard_acknowledged
already owns one native write transaction but currently validates only the
transition root and additional guarded updates, not frozen event observations.
EntityVerifiedMultiGuardDataStore readback covers only plan targets, not retained
closure. DataStoreConditionalMultiGuardPlan requires 1..8 additional UPDATES;
therefore it cannot represent all-retained validation-only cursor work. Preserve
this legacy contract; add an explicit internal host-bound publication route,
not a dummy cursor increment or caller callback. Revalidate preparation/provider/
host scope and exact cursor, event/index presence/absence/closure before any
staging in the same write transaction. Retain complete post-images and readback
including retained facts. Empty/transient-only completion needs no cursor route.
S12/P2-U15/P2-U16/P2-U05 remain open. No native publication, UnitOfWork dispatch,
production application/launcher/MCP/restart acceptance or full review claimed.
