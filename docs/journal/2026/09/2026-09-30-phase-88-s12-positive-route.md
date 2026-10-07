# Phase 88 S12: internal admitted provider route


WorkflowPreparedMultiGuardTransition retains a DataStoreSpace, not a raw
provider. DataStoreSpace revalidates the plan, checks host policy and live
issuing-thread lease before lookup, and requires every collection to resolve
to the same admitted provider object. It alone constructs an opaque invocation;
the internal EntityConditionalMultiGuardDataStore seam rechecks provider
identity, policy and lease after lookup, immediately before provider dispatch.
A throwing route is classified transaction-indeterminate, with no automatic
replay or successful Workflow completion projection.

DataStoreMultiGuardRouteSpec supplies an opt-in counting fixture returning
NotMatched without writes. Its ten examples cover one admitted invocation,
unadmitted/null input, owner/collection/guard mismatch before lookup, split or
rebound providers, unsupported providers, released/escaped leases, release
during lookup, cross-thread use and provider exception without replay. No
production memory or SQLite provider implements the seam. This is internal
route admission evidence only, not cross-owner commit, rollback, readback,
selected-event dispatch or public provider capability acceptance.

Attempt 296 stopped before compilation on stale Cozy classpath; prerequisite
297 completed compile/classpath export. Attempt 298 compiled production code
but failed three test line-break expressions; its receipt was invalidated by
repository-tree drift and is not acceptance evidence. After correcting the
expressions, settled-tree attempt 299 passed 60/60 tests in seven suites,
with accepted receipt, exits 0/0 and lock released. S12, P2-U15, P2-U16 and
P2-U05 remain open. Full-suite validation was not performed.

## Retained validation evidence

Invocation: `SMWF-P2-CNCF-P88-MULTIGUARD-ROUTE-SETTLED-20260930-299`.
Seven suites: DataStoreMultiGuardRouteSpec, DataStoreConditionalMultiGuardPlanSpec,
DataStoreConditionalUpdateSpec, EventStorePersistenceHostSpec,
WorkflowSelectedEventRecordsSpec, WorkflowEventSelectionSpec and
EventRecordJsonV1Spec. 60 succeeded, zero failed/aborted.

Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-ba0c7bae654b2f3e55cc001a3b4d81703389c6af9fef2c0e8022ba8544f4745e-023f71c3d4c660a960cf0efaeac32a8d/command-execution-sha256-60738843e9dd4c2f0ba331ff12031e25c383bbd964006f7dd8c4a57ea4d7c0f2`.
Log:
`/tmp/skill.cncf.d/cncf-sbt-214df86937807edb629ba258c991e5a1ed9f8fee6d4822d8488e993bee483aa9-9d828d238f408ac0bebd296e56d995ce/81769-20260930T004854Z.log`.
Validated tree SHA-256: `2210f5d170a93f08bd3e12cc713fb9710e049d6fc4855d6b5eccf34f01d1c08f`.
Agent Use: registered-custom-role cncf_command_runner, gpt-5.6-luna / medium,
`/root/sbt_receipt_adfb5d78e33457ce5b0f`; actual identity matched.
Subsequent changes are documentation only. No commit, publication or runtime
activation. Existing selected-kind policy and durable-only proposal behavior
are unchanged; transient delivery remains blocked until actual verified commit
and readback, with no transient replay/recovery.
