# Event observation writer competition and lease revocation

Date: 2026-09-30
Status: focused-341 passed; no production source changes this turn

Added ManagedEventObservationIsolationSpec using a test-only instrumented
SqlDataStore borrowed from the actual host-managed physical resource under the
issuing SystemNode lease. Host policy derives from the captured declaration and
Workflow grant. The native route is the production observeDeclaredSnapshotC
implementation and final EntityObservationDataStore admission seam; no
production transaction callback, new runtime access or unmanaged snapshot.

The retained fixture contains two keyed events and their indexes, cursor 2/2.
Incoming key alias and key-free EventId match require reference-event expansion
and an intrinsic-index probe not present in the initial manifest. Exactly three
event probes and two index probes execute in one native call.

Competition example synchronizes a separate JDBC writer immediately before the
referenced event probe, after initial cursor/event/key reads. It receives exact
SQLITE_BUSY error code 5, not a scheduling timeout. Snapshot closure/classification
retains cursor sequence 2 and both existing facts, consuming zero new slots.
Read route invokes no write-commit hook. An explicit later write succeeds with
one affected cursor row after read cleanup, proving lock release; the retained
snapshot still exposes sequence 2 while actual storage has sequence 3.
This is bounded SQLite locking evidence, not WAL/non-SQLite behavior or native
write-time validation of a stale observation.

Revocation example uses the actual SystemNodeResourceLease.revoke operation
through a test-only subsystem-package helper. Four points: entry, referenced
event SQL preparation, intrinsic-index SQL preparation, and after native read
cleanup before final snapshot admission. Each rejects with no snapshot/result,
no write-commit hook and no retry: zero provider calls at entry, one otherwise.
Revocation during a probe may allow that in-flight read to finish, but no
authoritative snapshot is returned. A newly admitted host subsequently reads
unchanged cursor 2; it does not reuse the revoked invocation.

Focused-340 failed Test compilation only: the SQL-package fixture attempted to
access the event-package-private delivery key. No tests ran. Kept production
visibility unchanged; added event-package ObservationEventTestSupport solely for
fixture key construction/copy/clear. ObservationLeaseTestSupport is test-only.
Focused-341 then passed 223/223 tests in 26 suites, zero failures, aborts,
canceled, ignored or pending cases. Both wrapper attempts terminated with
lock=released; summaries/logs were read. Failure-340 exits 1/1; success-341 0/0.
Final invocation/cwd/exact argv match; no source edits after final validation.
Final invocation: SMWF-CNCF-OBSERVATION-ISOLATION-DIRECT-20260930-341.
Final log: /tmp/skill.cncf.d/cncf-sbt-23b5831ce44f53126ac41eceeb35bd642a30f3d76a35fa5998ad440637c07bbd-ec2b00dc93d0a8689974f300b2386ce3/97482-20260930T093621Z.log

Validated SHA-256:
- ManagedEventObservationIsolationSpec.scala: 9049bf71f9693041952a45b5cb7bac093d8fadc7a219331f7b21cfc14d1af73c
- ObservationEventTestSupport.scala: 35ac67f7ed560bae1be9c5e251663a8449075c68cf7c9d1646dc6930e68b459b
- ObservationLeaseTestSupport.scala: 1f4f333c7f4218d004b62edc790a4ba8575e4d46b998b45e4f79ed2d2b718cbc

Next: trusted EventStore selection provenance and immutable publication handoff,
then native write-time validation of exact cursor/present/absent/closure facts.
No classification-to-commit adapter exists. All-retained validation-only
completion, all-transient verified completion, retained postcommit readback and
application/dispatch gates remain open. No automatic retry/reclassification,
repair, cursor reset, publication, dispatch or permission widening was added.

S12/P2-U15/P2-U16/P2-U05 remain open. No full suite, independent review, typed
Phase acceptance, commit, publish, public Operation/CLI activation or skill/agent
edit. Serial-execution skill kept shared lock, first-attempt platform escalation
and terminal evidence using the authorized simplified parent execution route.
