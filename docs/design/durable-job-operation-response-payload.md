# Durable Job OperationResponse Payload

status=design-partial-implementation
initial_authoring_implementation=authored_test_pending
implementation=focused_validated_independent_review_pending
phase=69.7
slice=JM69-10B2
decision=PH697-JM10B2-AUTHORIZED-PAYLOAD-01@1
recorded_at=2026-10-04

The [normative contract](../spec/durable-job-operation-response-payload-contract.md)
owns private publication and reading behavior. This parent-decided boundary
reuses the existing signed observed-current access, exclusive Blob metadata,
Published quota, cleanup and [B1 wire](durable-job-operation-response-codec.md).
It adds no public facade, record schema, provider, ownership map or sidecar.

## Canonical terminal publication responsibility

The [terminal publication contract](../spec/durable-job-operation-response-publication-contract.md)
and [design](durable-job-operation-response-publication.md) define the original
bridge's explicit owned-success integration. Independently supplied facts remain
closed; default Supplied retains absence. The bridge admits the successful
candidate and signed projected seed before this adapter publishes the actual
response. It substitutes only the actual acknowledged reference and uses its
original optimistic checkpoint. The configured owner reuses one payload service
from its original leased native composition.

A successful Published payload survives subsequent checkpoint refusal or lost
acknowledgement as physical/metadata/quota evidence. The bridge performs no
rollback/refund/retry; failed publication still belongs to the original producer.
The separate [current-caller query contract](../spec/durable-job-operation-response-query-contract.md)
and [design](durable-job-operation-response-query.md) consume this one original
reader after ordinary overview and independently resolved current raw durable
authorization inside the same ready owner's real lease. Only exact registered
Succeeded owned/header results are eligible. The detached queryVisible model
copy does not change projection/raw/getResult/management, execute a Task or cache
a response. Admitted reads can finish through logical close with the original
final native checks; SystemNode owns physical close. Startup authority never
becomes ordinary caller read authority. Actual completed CBD fresh-runtime
behavior remains a separate required downstream responsibility. The
[query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md)
owns query authoring and execution evidence.
The [publication journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md)
owns rationale, authoring and selected execution evidence.

The owned adapter resolves the complete admitted signed reference plan before
selecting one native physical body. A separate bounded one-pass reader retains
the bytes it verifies, so the typed adapter can consume actual native storage
without inspect-then-reopen or an unbounded second read. Closure belongs to that
single reader. Final current-record and original quota checks detect observed
races after closing; this design intentionally makes no transactional promise
against unobserved concurrent effects after its last admission.

Publication returns the existing native acknowledged reference. Real terminal
checkpoint ownership remains with the later caller; pure codec evaluation
precedes native producer authorization, and a later caller checkpoint failure
does not grant new rollback authority. The typed reader consumes detached
verified bytes with strict UTF-8 and the unchanged closed B1 consumer.

RP1..RP12 use actual managed SQLite/LocalBlob storage with measured adapters;
fresh owners are separately constructed and closed within the same process.
Independent wires/domain oracles and deterministic stream fault callbacks
establish the selected private boundary once the parent executes validation.
They supply no actual completed CBD or separate-JVM acceptance. Real runtime
terminal publication and current-caller consumption have their separate canonical
contracts. Independently fresh actual CBD full completed result and the complete
original process matrix remain required downstream work. Existing raw/getResult/
management compatibility and the rejection of component-owned shadow storage are
retained; the selected query consumer alone owns detached visible result escape.

See the [authoring journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-payload.md)
for scope and pending parent-selected six/113-suite execution.

## JM69-10B2 current selected execution — 2026-10-04

status=focused_validated_independent_review_pending
subject_revision=3
review_status=complete-original-Step-independent-assurance-pending
acceptance_status=not-accepted

Earlier authored/pending sections remain the original authoring history. Current
`PH697-JM10B2-REP-005` passed six completed suites/60 succeeded,
zero failed/pending/aborted; `PH697-JM10B2-ACC-005` passed the unchanged original
ordered113-suite accumulator/924 succeeded, zero failed/aborted, plus the
one existing SqliteDataStoreSpec pending example. Both had zero compiler
warnings, typed reader/SBT/wrapper exit0, consumed permissions and lock=released.
RP1..RP12 passed; RP12 ordinary shrinking seed69710022L/min24 retained its actual
checked.passed and checked.succeeded>=24 assertions, valid V2 Running/Pending
producer, and both selected V1/V2 completed consumers. Parent complete sixteen-path
mechanical/GWT/compliance admission passed; current Step union22 and twelve
unrelated dirty paths were preserved.

Failure history remains: REP001 stopped before compile/tests on stale Cozy
classpath; the existing authorized `cozyExportRuntimeClasspath` CP001 succeeded
with lock released (three noncompiler missing-credential notices). Its initial
request-retention stop had no submission and used one same-child V4 recovery.
REP002 had thirteen test compile errors from inherited ScalaTest withFixture
resolution; qualifying twelve existing native-helper calls corrected it. REP003
was a parent typed repair-identity preparation rejection, with no child or SBT
submission. REP004 compiled with no warnings and ran six suites:59 succeeded,
one RP12 failure from invalid V1/Pending fixture setup. Two local RP12 lines now
use the valid V2 producer and explicitly retain V1/V2 completed-consumer coverage.
No assertion was weakened, case removed, shrink disabled or snapshot printed.
These are selected test results, not independent acceptance.

JM69-09 remains DONE; JM69-10 remains OPEN and Phase69.7 IN_PROGRESS. Real terminal
runtime publication/authorized consumer integration, the complete process
matrix and independently fresh actual Textus CBD Support completed full result,
predecessor ancestry/deferred audits, complete original Step independent review
and local acceptance commit, aggregate full CNCF/downstream validation, sole
epoch1 comprehensive Phase review and final local release remain mandatory.
The representative native fixture is same-process and does not establish actual
CBD or separate-JVM acceptance. No B2-only review, acceptance, commit, push,
publication, deployment or successor work is recorded.
