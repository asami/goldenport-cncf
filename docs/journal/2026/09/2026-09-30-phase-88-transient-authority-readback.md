# Transient authority and verification uncertainty

Status: bounded executable evidence; not Phase acceptance

Previous goal turn progressed with focused-367 transient rollback/revocation.
Continued active Phase 2 / admitted CNCF Phase 88 under existing simplified
parent execution recovery, shared serial SBT wrapper and ordinary user caches.
Changed only test provider/specs, not production implementation.

Added two Given/When/Then scenarios with 16 actual managed configurations.

Six authority misuses: absent captured EventStore, captured legacy/unconfigured
EventStore, wrong thread (joined and terminal), actual revoked lease, explicitly
released lease, and escaped original view after scope cleanup. Each rejects
before provider write/commit/readback, with zero durable observation and zero
SQL-hook event-domain statements. Fresh scope retains the open pair and every
cursor/event/index field.

Transient completion accepts ordinary input, not a retained preparation handoff.
Therefore a newly admitted legitimate host may perform its own authorized
selection/completion; the spec does not invent a foreign-preparation restriction
for this input route. Only an escaped ORIGINAL host loses its invocation rights.

Ten test-only trusted-provider return faults after actual managed pair commit:
acknowledgement missing/reordered/null/wrong revision, and readback failure/
exception/missing/reordered/null/extra Workflow field. Each is explicitly
transaction-indeterminate, not Verified/NotMatched. One write/commit only;
malformed ack reads back zero times, readback faults read back once. No retry,
compensating rollback, repair or dispatch. Fresh scope proves closed committed
pair and exact unchanged durable facts. Event SQL hook and durable observation
counts remain zero.

These are faults injected at provider-return boundaries. They do not prove
native JDBC failure, cleanup failure, every malformed shape or crash recovery.
Storage proof still grants neither transient delivery nor application/UoW
completion. SQL hook evidence is not an exhaustive metadata/I/O audit.

Attempt 368 stopped at test compilation: legacy EventStore.inMemory is
parameterless, not an empty-parentheses call. Corrected fixture call. Final
focused-369 passed 247/247 in 26 suites, zero failed/aborted/canceled/ignored/
pending, SBT/wrapper 0/0, lock=released. Both final logs/summaries read; final
exact invocation/cwd/unchanged 26-suite argv and all 16 configurations checked.
No source edits after validation. No commit/publish/push, skill/agent edits,
full Phase review, typed acceptance or goal closure.

Invocation: SMWF-CNCF-TRANSIENT-AUTHORITY-READBACK-DIRECT-20261001-369.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-d54b7fa7f6189ef95c5eb6789be8454766e549788b2df7d168baa3c928819110-9ffa1227c4f98b7d5637387769ea90b3/19584-20260930T151511Z.log
Adjacent summary: 19584-20260930T151511Z.summary.json.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
0bb2e5be98dfa37c045385eb20b2b42c9ad3c981e05a7714b943d8ff90f2f703.

Next: transient independent writer competition and common deterministic
host/runtime routing from captured EventStore ordinary input. Branch choice must
not leak to skills/AI. Additional transition/provider-domain/shape/native
cleanup/crash coverage, application/UoW dispatch, production assembly/CLI/MCP/
restart/management remain open. S12/P2-U15/P2-U16/P2-U05 unchecked; goal active.
