# Phase 88 S1: canonical durable Workflow instance provider

Date: 2026-09-29
Status: first provider slice validated; Phase 88 remains in progress
Consumer: sm-workflow Phase 2

## Implemented

Added WorkflowInstanceRecordJsonV1, WorkflowInstanceDataStorePersistence and
seven executable provider examples. The provider-neutral
WorkflowInstancePersistence SPI stays create/load/append; no private engine,
new Operation, implicit store, command dispatcher or public SQL/CLI boundary.

The strict versioned codec preserves exact admitted ABI/source provenance,
current state, append-only history and suspension references. Reopen requires a
separately admitted definition catalog. Persisted definition metadata is never
used to construct executable definitions.

The provider borrows an explicitly selected canonical datastore, collection,
validated policy configuration and execution context. It requires atomic Entity
CAS and authoritative applied readback, using public detached EntityRevisionBinding.
Workflow revision 0 maps to storage revision 1; overflow fails without a write.
Canonical camel-case properties and a private versioned opaque text envelope
preserve JSON bytes across SQL property normalization and structured-value decoding.
No private SQL or migration/resource lifecycle is implemented.

## Validation

All SBT invocations used the registered cncf_command_runner (gpt-5.6-luna /
medium), typed receipt-first standing-authorized route and shared serial wrapper.
Every attempt completed with lock=released. Existing Cozy changes were preserved;
only its project-owned cozyExportRuntimeClasspath task was required as an
SBT-only validation prerequisite after initial generator classpath failure.

- 230: stale Cozy development classpath prevented compilation.
- 231: Cozy runtime classpath refresh succeeded, no source mutation/publication.
- 232: non-public datastore helper compile error; repaired to public revision binding.
- 233: focused run 20/22; SQLite exposed property normalization/structured JSON decoding.
- 234: repaired focused provider/SPI/native CAS validation passed 22/22.
- 235: after additional malformed-storage/catalog guards, all Workflow/datastore
  regression ran: 35 suites, 244 succeeded, 0 failed, 7 canceled, 1 pending.
  The seven canceled cases require opt-in live MySQL environment; the pending
  SqliteDataStoreSpec case predates this slice. They are not executed proof.
  All seven new provider examples succeeded.

Final command:
`sbt --batch "testOnly org.goldenport.cncf.workflow.* org.goldenport.cncf.datastore.*"`.

Final runner: `/root/sbt_receipt_38115f6b31f38997a707`.
Receipt SHA-256:
`d70bbfa03cc0769c4f28684ca5f6899b49b60b1769c3ad0052199d52f881414a`.
Validated complete-tree SHA-256:
`231280bd66aba620990c5636ea27a03d8771154b705d861a5523be36a67f9d8e`.

Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-99cd1c0310730d6930b34e28a14b9e438f4a319e4c80de99292741f029560627-567e38ff7f323296109e6bdb8bc4f0d5/command-execution-sha256-d70bbfa03cc0769c4f28684ca5f6899b49b60b1769c3ad0052199d52f881414a`.

Log:
`/tmp/skill.cncf.d/cncf-sbt-623cf31b2793a75e9d5533349b2edf8c242be9cd0672112b95551c68a2081708-9a789102c27774091ba5c8a86a1893ad/1057-20260929T022749Z.log`.

Source SHA-256:

- WorkflowInstanceDataStorePersistence.scala:
  `b339803d0fe2795e277d8dfb4f95cffa88b9442e8c76ea60310fd9bb56c2316b`
- WorkflowInstanceRecordJsonV1.scala:
  `8d45d253219c0512eaa06e0319460d95a94907e0668ea9f0a883f4c790f5c7bc`
- WorkflowInstanceDataStorePersistenceSpec.scala:
  `94585c1315104c0ad2ba106ad5fef9e874dba10b427ae94672d93f6ca142dbe2`

Subsequent documentation changes are not part of that captured validation tree.
No producer source changed after regression 235. This is focused/regression
evidence, not whole-CNCF full-test or commit acceptance.

## Open boundaries at the S1 checkpoint

The subsequent [S2 bootstrap slice](2026-09-29-phase-88-instance-bootstrap.md)
closes configured binding and managed-resource lease composition. This S1 record
retains its original validation scope; joint recovery and downstream consumption
remain open.

Configured Factory/bootstrap, datastore ownership/lifecycle, joint
instance/issued-work/claim/evidence recovery, shared production service/server/CLI,
resident reference refresh and canonical Phase 87 management visibility remain
open. Resource close/reopen in a provider test is not a separate-process
launcher restart. Policy names are preserved/validated, not a lease/migration
implementation. No downstream SNAPSHOT refresh/consumption is claimed.

No sm-workflow-specific semantics, semantic-worker dispatch, cncf-* skill changes,
skill deployment, publishLocal, commit, push or remote publication occurred.

See [Phase 88](../../../phase/phase-88.md).
