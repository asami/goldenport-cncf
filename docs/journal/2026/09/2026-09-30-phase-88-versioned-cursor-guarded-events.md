# Versioned cursor and selected-event guarded commit

Date: 2026-09-30
Status: bounded managed versioned progression passed focused-327

WorkflowCursorEventProposal is a package-internal pure proposal assembler.
It takes frozen WorkflowEventSelection, a captured host EventStore declaration
and loaded EventCursorRecordV1 facts. The persistent subset alone determines
checked contiguous sequences and cursor progression. The guard uses the host's
exact service owner, collection and cursor entry, expected native revision,
schema and canonical decimal-text last_sequence. The provider, not changes,
owns revision increment. No sequence range is reserved by preparation.

multiGuardPlanC combines the Workflow transition, selected event Creates and
cursor guard into one plan. Existing live host admission, same physical managed
SQLite resource, native atomic transaction and complete verified readback remain
required. No raw provider, SQL shortcut, initializer/reset, migration, retry,
CLI activation or new dispatch authority is supplied.

Readback candidate projection first strictly decodes the verified cursor and
matches its proposed revision/sequence, then checks canonical durable events
and restores transient events in original order. This remains storage/candidate
evidence only, not application completion or permission to dispatch.

Three host examples cover:
- Exact sequence text above 2^53, mixed durable/transient candidates, immutable
  input events, cursor recovery on reopen and no stale-plan republication.
- Schema, sequence or revision mismatch and a second event Create collision:
  every Workflow/cursor/event pre-state is retained, including no first event.
- All-transient input creates no cursor guard or event Creates, even with
  exhausted counters. The assembler explicitly rejects multi-guard completion;
  a verified two-record completion route is still required and remains open.

Focused-326 compiled and ran 191 tests in 21 suites: 190 passed, one failed
because the new replay spec wrongly expected Failure instead of the existing
successful NotMatched stale-root result. Product commit/readback passed.
Only that spec expectation was corrected before focused-327.
Both attempts preserve fresh helper metadata, scoped platform review and the
machine-wide serial lock under the user's simplified parent execution route.

Focused-327 passed 191/191 tests in 21 suites, no failed/aborted/canceled/
ignored/pending cases. SBT and wrapper exit=0; lock=released.
Invocation: SMWF-CNCF-CURSOR-GUARDED-DIRECT-20260930-327.
Log: /tmp/skill.cncf.d/cncf-sbt-c767d6af52836c9b7dd0b4ffccae982ced98c30c63ca5241e5d4b5dec3826432-828611d364b6e5302a5fb37a4d29991c/7396-20260930T074249Z.log
Adjacent .summary.json was read and matched exact cwd/argv, invocation,
test counts, exits and lock evidence.
Validated SHA-256:
- WorkflowCursorEventProposal.scala: 05555be7c1a4660ef39f9e7bf514b351f7c46a6c95535177b77c22466d8e5e0b
- EventStorePersistenceHostSpec.scala: 2c19a8f6a5721c96604f81b730526b9e44c4241908475a3a14fe44a6ed448b44

Next boundaries: full typed V2 event/discriminator and delivery-index codecs;
managed progression races/revocation and duplicate reconciliation; verified
all-transient completion; application/dispatch integration.
S12/P2-U15/P2-U16/P2-U05 remain open. No full-suite, independent review,
typed Phase/PLAN acceptance, commit or publication is claimed.
