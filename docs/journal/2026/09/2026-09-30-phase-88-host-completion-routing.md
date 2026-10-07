# Deterministic host completion routing

Status: bounded executable evidence; not Phase acceptance

Resumed after the user's Cozy repair. Existing simplified parent-direct recovery
is retained; shared serial SBT wrapper, ordinary caches and fresh invocation
identities are unchanged. No Cozy semantic edits, skill/agent changes, commits,
publish, push or destructive cleanup.

DataStoreSpace now provides private[cncf] conditionalHostEventsVerified for an
effect-free authorized Workflow root/successor pair and ordinary EventRecords.
The captured EventStore selects persistence once in this implementation. No
caller-supplied route, policy or selection is accepted. Empty/transient-only
input reaches the existing pair-only native route without durable observation;
durable input obtains one managed snapshot/preparation and frozen duplicate
classification. New durable count selects the prepared route; zero new durable
count selects validation-only retained completion. There is no catch-and-fallback,
retry or caller/skill-side dispatcher. The transient helper reuses the same
captured selection rather than preparing it again. Selection count is not a
separately instrumented assertion in these specs.

Three new Given/When/Then scenarios cover ten managed configurations:

- Five successful inputs: empty, transient-only, retained plus transient,
  new durable, and retained/transient/new mixed. Test-only provider counters
  prove exactly one selected native write route, one commit and one coherent
  readback. Verified record counts are 2/2/7/5/9. Durable observation occurs
  once only for durable inputs; transient event-domain SQL-hook count is zero.
  Original input sequence values stay unissued. Fresh scope checks pair closure,
  unchanged retained storage or the new event/index and cursor sequence 3.
- Missing captured service, missing read admission, unsupported new payload and
  native precommit rollback reject without another route, commit or readback.
  The first two have zero observations/writes; unsupported payload observes once
  but writes zero; rollback enters prepared write once. Fresh scope proves open
  pair and exact unchanged cursor/event/index fields.
- A separate immediate zero-timeout SQLite writer cannot modify the Workflow
  pair during transient staging (SQLITE_BUSY). A same-value probe after completion
  succeeds, establishing native lock release without retrying the Workflow.
  Fresh scope verifies the closed pair and unchanged durable fields.

Attempt 370 compiled production code but failed test compilation on an erroneous
EventRecord.source reference. Corrected to EventRecord.sequence. Focused-371
passed 249/249 in 26 suites; focused-372 includes the additional rejection cases
and passed 250/250 in 26 suites, with zero failed/aborted/canceled/ignored/pending,
SBT/wrapper exits 0/0 and lock=released. Complete summaries and relevant log
scenarios were read. No production/test source edit after final validation.

Final invocation: SMWF-CNCF-HOST-ROUTING-DIRECT-20261001-372.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-245bb6621f6152d473d05faf38e446f38fa6400decf990ec7586a576863b9829-6c4a978007ef4f2045d32845f36d7083/25588-20260930T152956Z.log
Adjacent summary: 25588-20260930T152956Z.summary.json.
Validated DataStoreSpace.scala SHA-256:
3acd53a9f128f3bbfcdbc72000d04ac64897a9eb1f65fe806752af23910b706e.
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
48f8211cff38342f68d1f5a0830c9ecb48a4593e810043ec7a24ad39ab280cc3.

This is an internal storage entry, not a public Operation or production runtime
assembly. Verified grants no UoW completion or transient delivery permission.
Native cleanup/I/O/crash cases and exhaustive shape/authority coverage remain
separate. Next is application/runtime integration with one canonical entry,
active UoW enlistment and original-order delivery only after accepted completion;
CLI/MCP/restart/management/packaging acceptance remains open.
S12/P2-U15/P2-U16/P2-U05 remain unchecked; goal/Phase remain incomplete.
