# Retained completion verification uncertainty

Date: 2026-09-30
Status: bounded managed-provider verification evidence; not Phase acceptance

Previous goal turn made progress with focused-354 retained rollback/revocation/
writer isolation (236/236). Continued active sm-workflow Phase 2 / admitted CNCF
Phase 88 under the existing simplified parent recovery. Production code unchanged.

Added two Given/When/Then scenarios to ManagedEventObservationIsolationSpec.
The fixture performs an actual native commit on the borrowed managed SQLite
provider. Test-only transformations then inject faults at its trusted return
boundaries; no production callback or public substitute authority was added.

Nine malformed committed acknowledgements: missing, reordered, duplicate target,
unexpected target, invalid Workflow post-image, changed cursor, changed retained
fact, null row and null vector. Each returns transaction-indeterminate, not
Verified or NotMatched; exactly one write/commit, zero postcommit readbacks.

Twelve postcommit readback faults: failure, exception, missing, reordered,
duplicate target, unexpected target, invalid Workflow post-image, changed cursor,
changed retained fact, extra Workflow field, null row and null vector. Each
returns transaction-indeterminate, with exactly one write/commit/readback.
The extra Workflow field satisfies basic root update conditions but differs from
the transaction-captured acknowledgement, so exact complete comparison rejects it.

All 21 configurations use fresh admitted-scope inspection afterward: Workflow
pair is committed at revision 2/status closed, cursor stays 2/2 and every retained
envelope/index field (including raw physical id) remains exact. Alias/fresh event
and index entries remain absent. No automatic retry, repair, compensating rollback,
dispatch or application/UoW completion is performed or authorized.

Scope: these are provider-return-boundary fault injection tests after real native
SQLite commit/readback. They do NOT prove native JDBC I/O failure, transaction
cleanup failure, crash recovery or every possible uncertainty condition.

Focused-355 passed 238/238 in 26 suites, zero failed/aborted/canceled/ignored/
pending, SBT/wrapper 0/0, lock=released. Exact summary/log invocation, cwd, unchanged
26-suite argv, and per-configuration outcomes were read. No source edits after
final validation. Shared serial wrapper, scoped permissions and normal caches
retained. No commit/publish/push, skill/agent edit, full Phase review, typed
acceptance or goal closure.

Invocation: SMWF-CNCF-RETAINED-READBACK-DIRECT-20260930-355.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-b247e63a94e71820da59c3fa0e0dc61039a72a578cb3fce8b07ec0fe39d68f9d-8d79f4931339a2bd90ab5caed01cc248/60038-20260930T122333Z.log
Summary: same directory, 60038-20260930T122333Z.summary.json.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
3ec2224cf966cb26389d95458c676933c7b185a28605fb2333a4952b62304f36.

Next: retained-specific authority and absence/key-free cases, then transient-only/
empty completion without cursor work and deterministic common host/runtime
routing. Skills/AI must not choose storage branches. Native cleanup/crash evidence,
application/UoW dispatch, production assembly/CLI/MCP/restart/management remain
open. S12/P2-U15/P2-U16/P2-U05 stay unchecked; Phase goal stays active.
