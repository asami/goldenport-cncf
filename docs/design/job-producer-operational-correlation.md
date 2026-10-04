# Native Job Producer Operational Correlation

status=design
phase=69.7
slice=JM69-09C6
implementation=selected-validation-passed
validation_status=selected-validation-passed
review_status=independent-complete-Step-review-pending
initial_authoring_status=authored_test_pending

The [producer contract](../spec/job-producer-operational-correlation-contract.md)
owns R1..R10 and PA1..PA6/PW1..PW4/PJ1..PJ4. WorkflowEngine carries the original
ActionTask.actionId through its private submit result. It records the native
Workflow update first, observes that captured update next, then builds its
original decision. JclRuntimeBridge waits exactly once, captures its original
Job/result tuple, observes the original root ActionId and optional snapshot,
then returns that same tuple and inner Consequence.

JobProducerOperationalAudit is private[cncf], with only fixed workflow and jcl
entry points. Required identities suppress malformed association frames; optional
malformed definition IDs preserve presence through a fixed omitted flag. The
observer performs no engine, Task, provider, payload or security reads. It catches
only observation NonFatal after native domain effects have normally returned.

One shared private[cncf] JobOperationalAuditTransport selects the three existing fixed Job frames.
Experience and lifecycle byte order, atoms, limits, scope and visibility remain
unchanged. The new producer frame uses explicit primitive JSON and the same
Record atoms. No public configurable emitter, new IDs, clock reads, state, schema,
metric, retry or output fallback is introduced. Transport ownership remains with
the existing runtime backend. Native Workflow history stays in its existing
owner; no persistent Workflow or fresh-process authority is invented.

The contract's seventeen-facet matrix distinguishes source-bound associations
from original global Event/all17 operational assurance. Those and original
JM69-10/whole-Step/Phase review/validation/release obligations remain pending.

Required native ID emptiness is unreachable through the final actual types:
validated labels, kind, timestamp and delimiters keep the canonical value nonempty.
The observer retains its defensive nonempty guard. Fixtures use explicit epoch
and entropy with real prefix overhead for inclusive512/adjacent513 total UTF8
bytes, ISO controls and null references; they never manufacture an empty native
value. Optional JCL snapshot String IDs can be empty or null and retain presence
with the fixed omitted flag. This revision3 fixture correction changes no
production identity, public API or security authority.

Actual selected testOnly validation passed62 tests in7 representative suites and
804 tests in97 accumulator suites, with one existing SqliteDataStoreSpec pending
example. The [canonical C6 journal](../journal/2026/10/2026-10-04-phase-69.7-native-producer-correlation.md)
retains the initial three failures and the unwaived PW3/PJ3/PJ4 two-file M2
fixture correction. These factual results change no design or identity contract
and do not close original global Event/all17, whole-Step or Phase obligations.

## Separately added C7 Event acknowledgement

initial_authoring_status=authored_test_pending
validation_status=selected-validation-passed
review_status=independent-complete-Step-review-pending

The [Event persistence design](event-persistence-operational-audit.md) and unchanged [E1..E10 companion](../spec/event-persistence-operational-audit-contract.md) separately add event.persistence.audit.v1 for the normal original EventEngine return inside contextual persistent EventBus publishing. This fourth fixed frame preserves all three existing Job frames and prior C1-C6/D1-D3 semantics/receipts/history. Initial documentation-before-code authored_test_pending remains historical. The [canonical C7 journal](../journal/2026/10/2026-10-04-phase-69.7-event-persistence-audit.md) records REP-00418 suites/142 succeeded/0 failed/0 pending and ACC-001110 suites/896 succeeded/0 failed plus1 existing SqliteDataStoreSpec pending. Both root Test/testOnly selections completed with0 aborted suites, reader/SBT/wrapper0, compilerwarnings0 and lock=released; they supply source-ack proof only.

Compile-fix CV-PH697-C7-SCALATEST-IMPORT-01/CV-PH697-C7-NULL-PATTERN-WARN-01 narrows explicit helper imports and uses case null without behavior/schema redesign. It and native fixture/docs correction both retain unwaived M2 complete-original-Step independent assurance. Native required-ID labels disallow quote/backslash and timestamp/Base62 widths vary, requiring genuine measured512/513 fixtures via a legal-label bounded64 constructor. Static body2914/standard-line4163 derivation remains analysis; passing EA4 separately proves actual successful assertions. No4096/8192 overflow execution is claimed; defensive guards remain unchanged.

Phase69.7/JM69-09 remain IN_PROGRESS; JM69-10 OPEN. Original authority/base/binding/epoch1/Step repair1/Phase repair0/history17events10budgets/no acceptance reviews, all17 integrated security/retention/hostile operational proof, CPB-PH697-NRT-DISPATCH-REFUSAL-01, CPB-PH697-NRT-CANCEL-SETTLEMENT-01, HYG-PH697-SQL-SIZE-01, HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 and historical DVL-PH697-D3-EXPECTATION-START-FIELDS-01 remain unchanged. Real JM10 two-process/independently fresh CBD Support runtime, Phase69.4-69.6 ancestry/deferred audits, independent complete-Step reviews/local acceptance commits, aggregate final full/downstream validation, sole epoch1 Phase full review and final local release remain required; no waiver, new finding classification, full repository/Step/Phase acceptance or remote action is implied. Parent owns verification and transitions.
