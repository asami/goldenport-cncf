# Transient completion rollback, revocation and event-SQL isolation

Status: bounded executable evidence; not Phase acceptance

Continued Phase 2 / admitted CNCF Phase 88 after focused-365 initial transient
native completion. Existing simplified parent recovery and shared SBT wrapper
retained. Changed only the test provider and three Given/When/Then specs in
ManagedEventObservationIsolationSpec. Production implementation unchanged.

The test-only provider opts into EntityTransientEventDataStore and delegates
to the real borrowed managed SQLite native methods. Existing write/commit/
readback counters and actual lease revocation are reused. During completion,
the SQL statement hook records statements referencing quoted cursor/event/index
table names; observe_native invocations are counted separately. This hook proves
absence of those emitted statements, not an exhaustive JDBC metadata/I/O audit.

Eight configurations in three scenarios:
- GuardAdmitted, SuccessorPrepared, RootPrepared, BeforePublish failure: one
  write attempt, zero native commits/readbacks; pair rolls back.
- Actual staging lease revocation: pair rolls back, no commit/readback. Actual
  postcommit-readback lease revocation: one commit/readback, result explicitly
  transaction-indeterminate with no Verified, retry or compensating rollback.
- Empty and ordinary transient input with pre-existing durable facts: one
  pair-only commit/readback and exactly two verified records.

All configurations invoke durable observation zero times and emit zero
cursor/event/index SQL through the instrumented statement hook. Fresh admitted
scope compares every stored cursor/envelope/index field and expected pair state:
open after rollback, closed after successful commit or committed-unverified
revocation. No transient delivery, dispatch or application/UoW completion is
authorized by this storage proof.

Attempt 366 stopped at test compilation: a Boolean variable named empty shadowed
ScalaTest's empty matcher. Renamed it to emptyInput. Focused-367 passed 245/245
in 26 suites, zero failed/aborted/canceled/ignored/pending, SBT/wrapper 0/0 and
lock=released. Both final summaries/logs read; final invocation/cwd/unchanged
26-suite argv and all eight outcomes checked. No source edits after validation.
No commit/publish/push, skill/agent edit, full review or typed Phase acceptance.

Invocation: SMWF-CNCF-TRANSIENT-ISOLATION-DIRECT-20261001-367.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-5a7283040cf4280048ed7c09456a44c26cfe508a0eea44008de14344251a3716-16f36569354294fd457bc61ad6a0193c/16874-20260930T150933Z.log
Adjacent summary: 16874-20260930T150933Z.summary.json.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
9baee8e7550f50a9b0976ecb866786b8679423cbd3c2f0976fd5fbd7f376dc41.

Next: transient-specific authority/unconfigured service, malformed/failed/
throwing readback and independent writer competition. Then common deterministic
host/runtime routing from captured EventStore input. Skills/AI never choose
storage branches. Native cleanup/crash, application/UoW dispatch, production
assembly/CLI/MCP/restart/management remain open. S12/P2-U15/P2-U16/P2-U05
unchecked; full Phase goal stays active.
