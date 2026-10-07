# SQLite snapshot competing-writer evidence

Date: 2026-09-30
Status: snapshot-phase writer competition passed focused-317; production activation open

Only DataStoreMultiGuardSqliteRouteSpec changed. A new executable case pauses
the native snapshot after its root read and before its continuation query.
A latch releases an independent JDBC writer at that exact boundary. The writer
has zero busy timeout and attempts a real cursor UPDATE; completion is awaited
while the read transaction remains open. The test requires native SQLITE_BUSY
(code 5), not a scheduling timeout or a generic failed Consequence.

The admitted Workflow/DataStoreSpace route verifies all five original targets
at revision 2. Exactly one provider call, one native write commit and one
snapshot are observed. A separate explicit cursor UPDATE after the snapshot
returns succeeds, proving the snapshot lock was released. Storage then contains
revision 3, while the previously verified post-image retains revision 2.
This is test-only independent-writer orchestration, not a product automatic retry
or a caller-facing transaction callback. No sleeps or wall-clock guesses decide
the outcome; latch deadlines merely bound a failed test.

The SQLite route suite now has twelve examples. Final invocation
SMWF-CNCF-SQLITE-SNAPSHOT-DIRECT-20260930-317 passed 165/165 tests in 20 suites,
zero failed/aborted/canceled/ignored/pending. SBT exit=0, wrapper exit=0,
lock=released. Mandatory serial wrapper, metadata validation and scoped platform
review were preserved under the user-selected simplified parent execution route.

Log:
/tmp/skill.cncf.d/cncf-sbt-6b68e6ff6de5fb86ffd730a380aae4ff63f1f03bf37cd5cb4b5c0dc1d34664b9-fd79b2ad5568e9f6b567d7e47d7c9fcc/41326-20260930T061654Z.log

Validated spec SHA-256:
929b22ecc12eb995db40d978985325e5c4749d946de56262ff0fee3451f8bb27.

This closes only the previously missing snapshot-phase competing-writer evidence
for the internal same-owner SQLite candidate. Managed host/bootstrap activation,
production opt-in, cross-owner authority/atomicity and selected cursor/index/event
dispatch remain open. No full-suite, independent review or typed Phase/PLAN
acceptance is claimed. No commit/publication was performed.
S12/P2-U15/P2-U16/P2-U05 remain open.
