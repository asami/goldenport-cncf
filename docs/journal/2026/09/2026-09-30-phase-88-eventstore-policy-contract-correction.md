# EventStore-owned selection and complete readback candidate

Date: 2026-09-30
Status: candidate source/spec changes; validation blocked before compilation

## Contract correction

EventStore owns a fixed versioned event-kind persistence policy. Matching
Persist rules retain events; Transient rules and unmatched kinds do not.
Ordinary senders specify no persistence option. Caller record hints do not
override the policy. Per-send overrides are reserved for a separate future
extension, not implemented here. Subsystem declarations continue to configure
storage destinations and exact permissions, not event selection or dispatch.

The configured in-memory EventStore now evaluates the complete bounded batch
before mutation. Only selected events enter retained records, identity and
delivery indexes and sequence allocation. Excluded events return in input order
at sequence zero. Both ordinary lanes use this store policy; Workflow preparation
still accepts only transactional records. Malformed, duplicate or pre-issued
batches reject before retaining their earlier members.

WorkflowEventSelection.prepareFromStoreC consumes the store decision and freezes
its revision. The older policy-argument helper remains pure compatibility planning,
not runtime ownership. The legacy no-argument EventStore remains unchanged and
cannot prepare the new route. Existing EventEngine emit uses configured append
without adding a sender option. This is an in-memory candidate, not managed
SQLite integration, commit evidence or Workflow dispatch authorization.

Six new EventStorePersistenceSelectionSpec examples cover ordinary emit,
durable-only storage/indexes/sequences, excluded-only batches, fixed store policy
and frozen preparation, ordinary nontransactional selection, and all-or-nothing
input validation. Existing selection and host-separation examples were corrected
to treat unmatched kinds as transient and obtain preparation from the store.

## Separate complete-readback candidate

The internal Workflow -> DataStoreSpace -> one admitted physical provider route
now has a separate private verified multi-guard capability. Old raw capability
behavior remains unchanged. Unadmitted or raw-only providers reject before commit.
No production provider implements the new capability.

A native commit acknowledgement retains the exact full ordered postimages of
root, successor, additional guards and side effects. Authoritative readback must
use one coherent native snapshot and match every target and complete record,
including unchanged fields. The live lease/authority token is rechecked before
commit, readback and verified return. Verified means storage evidence only, not
Workflow completion or delivery authorization.

Known noncommit/rollback failures may propagate as such; uncertain commit outcomes,
invalid acknowledgements, missing/divergent readback and authority loss after
commit are transaction-indeterminate. They suppress success and permit neither
automatic replay nor speculative rollback.

Seven DataStoreMultiGuardReadbackSpec examples cover complete equality, each
target's missing/divergent record, malformed acknowledgement sets, failed/thrown
readback, final-read lease loss, stale root without reads, and unsupported routes.
The synchronized same-owner memory test fixture retains prepared postimages
before commit and reads one coherent snapshot. It is not production opt-in,
cross-owner support, SQL implementation or independent-writer evidence.

## Validation ledger

Historical focused-301 passed 65/65 in eight suites with terminal exit 0/0 and
released serial lock. It predates this candidate and does not validate these
changes.

Focused attempt 302:
- ID: SMWF-P2-CNCF-P88-COMPLETE-READBACK-FOCUSED-20260930-302.
- Registered runner: /root/sbt_receipt_ce48c5be6cf63730a5a8,
  gpt-5.6-luna / medium.
- Nine suites selected; no compilation or specs ran.
- Terminal exit 1/1, serial lock released.
- Generator error: development classpath stale:
  /Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt.
  CNCF Information generation exited 2 and requested cozyExportRuntimeClasspath.
- Receipt:
  /tmp/skill.cncf.d/cncf-command-execution-c093147fe0af8a4c9afe495e4f2003ec0d82f33676afe3153a7ea458ae7335db-1960617375a2efb1283ff63d4cc9daa2/command-execution-sha256-acba8e32b99526dda9361c2e421673d1115ee8c84161888df7767eb33f961c51.
- Log:
  /tmp/skill.cncf.d/cncf-sbt-4fc75bbe6b8742551b7cd42e77ff1e014516786d1fd84553e5d09f23c328f50e-bc165f1e30569ed33843d0ea586972a8/2563-20260930T013721Z.log.

Prerequisite attempt 303:
- ID: SMWF-P2-COZY-P88-COMPLETE-READBACK-CLASSPATH-20260930-303.
- Registered runner: /root/sbt_receipt_56611006b81d2d2beaae,
  gpt-5.6-luna / medium.
- Exact target: /Users/asami/src/dev2025/cozy.
- Logical argv: ["--batch", "compile", "cozyExportRuntimeClasspath"].
- Receipt-first intake accepted; the single escalated submission was rejected
  by platform review as unrelated to the current EventStore work.
- Disposition: EXECUTION_PERMISSION_DENIED_AFTER_ATTRIBUTABLE_AUTHORITY.
- SBT did not start, no lock was acquired, and no command mutations occurred.
- Retained intake:
  /tmp/skill.cncf.d/cncf-command-execution-6a86fdc0e4e3fc44ff39d35bfa90d9ede5af2aba79c2456df3a0aba3364e2fac-997f3e875d24ef4aed0cde310df69773/command-execution-intake-sha256-52bb17c5c95003bbc5507c7c7e4a37a7579e59051bb6e86127aa4e4cefb1d3ab.json.

The command-execution skill's denial-stop rule applies. No alternate runner,
fresh envelope, parent SBT, substitute command or retry was used. This is a
platform execution-path rejection after attributable authority, not absent
human approval. Only a platform-permitted recovery for the exact prerequisite
can unblock validation; repeated human approval alone is not that recovery.
Source/spec/documentation work unaffected by execution denial was preserved.

## Open gates

This source has not compiled or passed the new examples. S12, P2-U15, P2-U16
and P2-U05 remain open. No production activation, full-suite QA, commit,
publication, cursor/index managed runtime, cross-owner atomicity or actual
post-commit mixed dispatch acceptance is claimed.
