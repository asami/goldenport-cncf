# Native prepared event publication and complete retained readback

Date: 2026-09-30
Status: bounded managed SQLite storage route; not application/Phase acceptance

Previous turn progressed with focused-346 preparation independence/lifecycle
specs. Continued the same active sm-workflow Phase 2 and admitted CNCF Phase 88.
The named cncf-goal-phase contract is retained with the user-authorized simplified
parent execution override; no runner/history loops, new Phase or new goal.

DataStoreSpace.conditionalPreparedEventsVerified consumes an effect-free Workflow
transition plus the original opaque preparation. Exact host-view identity is
required, not just a matching service/provider/revision. Its cursor update and
new V2 event/index Creates are generated from immutable classifier templates and
actual sequence proposal. Caller-created event effects are not accepted.
No new storage authority derives from returned Records or source EventRecords.
The capability is separately opt-in; legacy/memory providers default to false.

Managed SQLite preserves the preparation on the native write invocation. On the
same write connection, before root/successor staging or DDL, it reads EVERY
frozen target, checking table existence, exact physical ID and duplicate fields,
then compares complete cursor/event/index/present/absent/closure logical facts.
Any mismatch fails without reclassification/retry/repair. Provider/host/lease
checks continue through staging and commit. This is not preflight validation in
a separate transaction. Existing legacy multi-guard entry remains compatible.

Native transaction post-images and separate coherent postcommit readback include
retained event/index facts as well as Workflow pair, cursor and new Creates.
The final verified seam validates plan post-images plus exact retained bytes/
fields, complete order/coverage and native-ack versus actual-readback equality.
This Verified remains storage evidence only, never dispatch/UoW permission.

A new Given/When/Then scenario exercises six actual SQLite configurations:
unchanged, stale cursor, missing retained event, missing retained index, newly
present formerly absent event and newly present formerly absent index. The
unchanged case verifies seven records (Workflow pair, cursor, new event/index,
retained event/index), cursor sequence 2 and closed Workflow root. Explicit
resubmission fails stale without a second advance. All changed cases reject and
preserve open root/continuation, exact externally changed cursor and every
otherwise absent new side record. No product-command external effects run.

Focused-347: 228/228 tests, 26 suites, zero failed/aborted/canceled/ignored/pending;
SBT/wrapper 0/0, lock=released. Persistent summary and scenario log read; exact
invocation, cwd and unchanged 26-suite argv match. No source edits after final
validation. Shared serial wrapper, scoped first-attempt platform escalation and
ordinary caches retained. No commit/publish/skill/agent edit.
Invocation: SMWF-CNCF-PREPARED-PUBLICATION-DIRECT-20260930-347.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-0607a49b639d8e90c947b69842765cbf85b97e990f46bfaf26e60a5effd58558-3198df2024d9befdab97aad6593d9cc4/43977-20260930T114052Z.log

Validated SHA-256:
- ManagedEventPreparation.scala: d706a05c3eb8c9fb1c7a1cfb11469af5ae8f369838a2b55fc701b43873469083
- DataStoreSpace.scala: b1d1564b468ffbfd7e974e8c6d8f286fd3c2f47047104eefd2f330ee595d3b64
- EntityVerifiedMultiGuardDataStore.scala: b91e53f7b53299b209c09a26b55b4b520f4d5ed7f94772fa1fc961a3c540cac3
- SqlDataStore.scala: f1d253e4301469a694282495aab4dd834c7e52632c8d3b5ae7d63f856b0cbe7f
- ManagedEventDuplicateClassifier.scala: 82d11f838afd643db026f0ffa0b6096d87aa512e65626299846cb38721243c2f
- DataStoreObservationSnapshotSpec.scala: 739ead7ca64e86666385608ae0ad8a7d7dfa842b19ce3adeaa484bad74be548c

Next: prepared-route rollback/checkpoint and competing-writer evidence, explicit
cross-host/revoked write rejection, then all-retained validation-only completion
without cursor update and transient-only completion without cursor work. These
are not waived by this first mixed/new-durable route; no dummy cursor advance or
legacy fallback is permitted. Production application assembly, frozen Workflow
completion binding, actual mixed delivery/no-dispatch-on-uncertainty, launcher/
MCP/restart/management and Phase closure remain unfinished. S12/P2-U15/P2-U16/
P2-U05 remain open. No full review, typed acceptance or release claimed.
