# Prepared publication authority boundary

Date: 2026-09-30
Status: bounded executable rejection evidence; Phase/application gates open

Previous turn implemented and validated the prepared publication route at
focused-347. This turn extends only DataStoreObservationSnapshotSpec; production
sources remain unchanged. Retain the same active sm-workflow Phase 2 and admitted
CNCF Phase 88 scope and the user-authorized simplified parent execution override.
No new goal/Phase, runner/history loop, skill/agent edit or permission relaxation.

One Given/When/Then scenario covers seven separate real managed SQLite fixtures:
foreign host view, wrong thread, actual revoked lease, conflicted Continuation,
reserved physical id-column changes, caller-injected event Create, and escaped
preparation resubmitted through a new view. The foreign view deliberately has
the SAME EventStore, provider, grant/policy and actual live lease; it still cannot
replace the issuing view. The revoked case uses actual SystemNode revocation.
The injected-effect and reserved-column cases keep production admission/private
constructors intact. Every attempt rejects. Fresh post-scope inspection confirms
open Workflow root, unchanged cursor sequence 0, absent new event/index and
absent injected record. Continuation remains open except for the exact externally
changed status/revision in the conflict fixture, which is preserved unchanged.
Re-observation during inspection only derives the expected index identity; it
performs no publication/retry of the rejected attempt.

Focused-348 failed Test compilation because a fixture tried the private root
copy method; no tests ran. Corrected fixture construction using the existing
DataStoreConditionalRoot.create path, without making copy public or changing
production visibility. Both attempts terminal with lock=released; failure
summary/log read before retry. Focused-349 passed 229/229 tests in 26 suites,
zero failed/aborted/canceled/ignored/pending; SBT/wrapper 0/0. Persistent summary
and scenario log match exact invocation/cwd/unchanged 26-suite argv. No source
edits after final validation. Shared serial wrapper and scoped platform access,
normal caches retained. No publish, commit, typed acceptance or full review.

Invocation: SMWF-CNCF-PREPARED-AUTHORITY-DIRECT-20260930-349.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-35350cdf09ee96ea7bacfc7234357c5bd9ae00f0f3276acf073c958c5bf15dd8-2d660a476543da34fd8c6ef64ed765cd/46853-20260930T114809Z.log
Validated DataStoreObservationSnapshotSpec.scala SHA-256:
dd46eb11ca03f9cf2da617c58890d39fd30eb302a9b09745b9805cbbe835372d.
Production sources unchanged from focused-347.

Next: prepared-route checkpoint rollback after successor/root/cursor/event/index
staging, revocation inside write/readback, independent writer competition and
postcommit uncertainty evidence. Existing legacy multi-guard rollback specs do
not prove the preparation/retained-readback path. Then all-retained validation-
only completion with no cursor advance and transient-only completion with no
cursor work, followed by actual application/UoW dispatch and production runtime
assembly/CLI/MCP/restart/management. No dispatch or external command proof from
these storage-only specs. S12/P2-U15/P2-U16/P2-U05 remain open; goal stays active.
