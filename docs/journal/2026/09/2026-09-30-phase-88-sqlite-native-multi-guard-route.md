# SQLite native multi-guard route candidate

Date: 2026-09-30
Status: internal same-owner SQLite candidate passed focused-316; production activation open

## Boundary and implementation

SqlDataStore adds package-limited SQLite-only same-owner native acknowledgement
and coherent snapshot helpers. It does not implement the multi-guard opt-in
traits. A test-only subclass exercises WorkflowPreparedMultiGuardTransition ->
DataStoreSpace -> one SqlDataStore under a live issuing-thread lease. No Workflow
or skill calls a raw SQL transaction, and no caller receives a transaction callback.

All independent guards, successor and Create effects execute in one native
write transaction. Exact post-images are captured before commit. Schema expansion
also occurs inside that transaction: failures roll back both columns and records,
and NotMatched performs no schema expansion. The existing commit/rollback hooks
remain responsible for uncertainty; uncertain commit/readback is never retried.

Readback uses one connection and explicit native read transaction for all targets,
rechecking the invocation before each read; rollback ends this read-only snapshot
without invoking the write-commit hook. SQLite physical id is checked against the
target before removal from the logical record. Business id writes are reserved and
reject before staging. All other fields/values are retained and sorted by field
name. Pure post-image admission ignores field order only, rejects duplicates
without map collapse, and still rejects extra/missing/changed Create facts.
Full ordered target and complete acknowledgement-versus-readback comparison stays
exact; this is not relaxed transaction acceptance.

## Executable evidence

The SQLite route suite has eleven examples: three guards/two Creates with one
write commit and reopen; stale third guard/second Create rollback; all four
pre-publication checkpoints; stale root; schema expansion rollback/no expansion
on stale root; two independent writers with one Verified winner and one
NotMatched loser; missing actual post-commit event; uncertain acknowledgement
after real commit; unadmitted host route; cross-owner rejection even with host
labels; reserved physical-id rejection. Complete readback has eleven examples
after field-order/duplicate cases were added.

Attempt 311 stopped before compilation on a stale Cozy classpath. Prerequisite
312 compiled Cozy and exported classpath successfully. Attempt 313 ran 160 tests
with three failures exposing physical-id/column-order differences. After explicit
logical adaptation, 314 passed 163/163. Schema rollback refinement was then made;
315 compiled production code but the added spec incorrectly used private copy.
The spec was repaired to use the validation factory.

Final SMWF-CNCF-SQLITE-MULTI-DIRECT-20260930-316 passed 164/164 tests in 20 suites,
zero failed/aborted/canceled/ignored/pending. SBT exit=0, wrapper exit=0,
lock=released. Parent execution retained the mandatory shared wrapper, fresh
metadata and scoped platform review under the user's simplified execution route.

Log:
/tmp/skill.cncf.d/cncf-sbt-1e5b30a6d675442e0f5d5545ea01bda31bf4df4a634109dca86cf48c7c04c654-e6cec653452c11f334f0f22906699c4a/33531-20260930T060450Z.log

Validated SHA-256:
- SqlDataStore.scala: 58742868b66e4b07f603b8c7a2e1937040c49bd4f27c31711aff0b4d862ea139
- DataStoreMultiGuardReadback.scala: 957b36ab470ac6b4a1ccac054eec31456d59bfc801f164400c31f54631ab902e
- DataStoreMultiGuardSqliteRouteSpec.scala: 2c0f1b5d5ef0f3a39b33eea0f3cef9347d1cea577df4d907b12a486a4e7b1ba2
- DataStoreMultiGuardReadbackSpec.scala: a3604b69533aa3b82eae366615c7cd60f5e4a7ff973fc8d5537cdb068b37f30d

## Remaining gates

No production opt-in, managed host/bootstrap activation, cross-owner transaction
or selected cursor/index/event dispatch is enabled. Writer competition during
the readback snapshot itself still needs a dedicated executable case; the current
independent-writer case races write proposals. MySQL/non-SQLite multi-guard is
unsupported. Full-suite validation, independent review and typed Phase/PLAN
acceptance are not claimed. No commit/publication was performed.
S12/P2-U15/P2-U16/P2-U05 remain open.
