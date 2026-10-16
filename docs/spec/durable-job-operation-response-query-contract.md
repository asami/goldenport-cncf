# Durable Job OperationResponse Query Contract

status=normative-partial
implementation=focused_validated_independent_review_pending
review_status=complete-original-Step-independent-assurance-pending
phase=69.7
step=JM69-10
slice=JM69-10B4
decision=PH697-JM10B4-CALLER-RESULT-01@1

This contract governs the unchanged `JobEngine.queryVisible` consumer of an
original registered closed terminal projection. The [query design](../design/durable-job-operation-response-query.md)
fixes composition and lease ownership. The [journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md)
owns authoring, execution and acceptance accounting. The [terminal projection](durable-job-terminal-projection-contract.md),
[publication](durable-job-operation-response-publication-contract.md),
[payload](durable-job-operation-response-payload-contract.md) and
[runtime owner](durable-job-runtime-owner-contract.md) retain their separate responsibilities.

## R1 Ordinary overview first and exact eligibility

A missing original query returns `Success(None)` without policy, resolver or
storage access. For a present model, call the supplied original
`policy.authorizeRead(model)` first and preserve its returned refusal. Live or
result-bearing models return their original model and values. Hydration is
eligible only for this exact Job ID's registered closed terminal projection,
Succeeded lifecycle and `Succeeded(External(reference))` with a non-null
`job-owned-blob` reference and exactly the companion operation-response content
header. Absent, Inline, shared/other external, failed, cancelled and nonterminal
selections retain their original behavior. There is no arbitrary external read.

## R2 Trusted current-caller resolver

The original trusted per-Subsystem provider may append
`callerAccessResolver: Option[DurableJobCallerAccessResolver] = None` after the
existing migration flag in private runtime inputs. The package-private abstract
resolver has exactly `resolve(context: ExecutionContext): Consequence[DurableJobRecordAccess]`.
It receives only the actual current calling context. It receives no Job ID,
stored body, startup context or candidate access. Production adds no default
attribute mapping, tenant inference, alias, normalized scope or implicit resolver.
Null Option and Some(null) refuse before Ready; explicit None preserves startup
compatibility and refuses an eligible result query. Startup credentials, signed
authorization, capabilities and global state never supply a durable caller grant.

## R3 Original ready owner and real native lease

Eligible reading requires the same original engine's ready owner. Brief internal
owner Ready, engine ready and same-owner identity checks end before callbacks or
provider work. Acquire the original real binding lease once. Reject null or
interrupted caller, resolve the caller once inside that lease and admit only a
non-null access with non-null, nonblank raw tenant/subject and non-null scopes
whose individual raw tokens are non-null and nonblank. Validation must not trim
or normalize the actual grant values. Empty scopes are valid when signed scope
requirements are empty. Use the actual caller context explicitly with the one
original `_result_payloads.readObserved(snapshot, access)(using caller)`.
The existing signed tenant/subject/scope equality has no administrative bypass.

## R4 One original owned reader

The original B2 reader owns canonical signed admission, equal complete current
semantic/provider revision, Active retention, actual signed membership, complete
metadata targets, exact Published quota, one native get, reply verification,
size+1 bounded streaming, integrity, EOF and finally-close. Its final native
revision/retention/quota checks, strict UTF-8 and B1 decode remain mandatory.
There is no alternative reader, store, backend, summary or writer-value fallback.

## R5 Detached-only result escape

Only a successful exact native `OperationResponse` escapes through
`model.copy(result = Some(response))`. The registered projection, raw query,
raw getResult and management snapshot remain unchanged. A recovered raw result
is None; authorized original management result is UnavailableAfterRestart.
Each later caller authenticates and reads independently. No response/model cache,
executable map entry, source/port call, payment/refund, quota or payload mutation
is introduced by querying.

## R6 Admitted close and optimistic observation

Gate selection admits new reads before logical owner close. Already admitted
lease work may finish through owner close with the original final native checks.
Do not add a second ready gate inside its resolver/read or hold engine/owner
monitors while arbitrary code blocks. Subsystem shutdown drains the real active
lease. The original SystemNode owns physical SQL finalization after that drain;
logical owner close does not own physical resources. New reads after gate close
refuse. Native row deletion/revision/retention and quota-release races observed
by final admission refuse after closing the stream. No transaction is promised
against changes after the final observation.

## R7 Fixed refusal and interruption

The new hydration failure is exactly
`Consequence.stateInvalid("durable job result query refused")`. Missing or
malformed resolver, null/failed resolution, private access/provider/codec errors
and thrown NonFatal failures collapse to it. Thrown NonFatal overview callbacks
are redacted; returned ordinary overview failures stay original.
InterruptedException restores the thread flag; fatal errors propagate. No Job ID,
reference, path, body, exception text or silent absence fallback is exposed.

## R8 Compatibility and independent evidence

The public API, signed format, migration, existing producer/cleanup, default
Supplied, original CP1..CP4 and raw/management restart behavior remain compatible.
Only the new trusted fixture maps raw current principal ID, tenantId and
space-delimited raw scopes. Its actual SubjectKind.User principal is independent
of the signed record; its independently supplied recipe changes only submitter
kind to User. Existing lowercase-user recipes and production policy stay intact.
Positive terminal results must originate in actual Task/B3 publication with six
signed lifecycle boundaries. Synthetic legacy terminal seeds serve negative
selection only and cannot establish positive result acceptance.

## R9 True process and domain evidence

Each native process case has a unique target/job-result-query/work/case-* root
with native/control/logs children. The launcher uses absolute java.home/bin/java
and ProcessBuilder argv, waits at most 60 seconds per owned child and cleans up
only its own process. Actual writer exit and logical/physical close precede fresh
reader launch. Readers consume only an opaque Job ID and independently selected
bounded scalar input or independent Review constants, never writer receipts,
rows, response objects or provider/control state. Each child locally checks
exact domain, semantic revision6, actual owned reference and paid native seed.
Closed <=16KiB receipts contain only actual identities, namespace, revisions,
reference/status/payment, effects, domain-check and closure observations. Value
encoding transports observations and does not certify unchanged contents.

The scalar property uses ASCII alpha1..8, ordinary shrinking, Seed(69710034L),
minSuccessfulTests24 and workers1, with a genuine sequential pair per generated
case. The independent Review fixture and native query process acceptance do not
establish the actual Textus CBD Support completed provider pipeline or its fresh
runtime acceptance. That original downstream responsibility remains mandatory.

## Executable specification mapping

[DurableJobOperationResponseQuerySpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala)
binds visible E1_Q1..E13_Q13 groups through afterWord example:E1..E13,
rules, phase:69.7 and slice:JM69-10B4 with adjacent Given/When/Then and should
expectations. The [native fixture](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQueryFixtures.scala)
and [probe](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQueryProbe.scala)
use original managed native composition and independent domain oracles.

| Group | Rules | Required executable observation |
| --- | --- | --- |
| E1_Q1 | R1,R5,R8 | Actual live identity; missing None; no resolver/Blob/missing policy |
| E2_Q2 | R1,R2,R3,R4,R5,R8 | Default owner overview and raw current grants; exact six fields; one closed read; unchanged native/raw/management; no cache |
| E3_Q3 | R1,R2,R7 | Original foreign overview refusal before resolver/Blob; returned failure identity |
| E4_Q4 | R2,R3,R4,R7 | job_view overview with foreign tenant/subject/missing/raw-uppercase scopes; strict early refusal and later independent valid success |
| E5_Q5 | R2,R3,R7 | Null input containers refuse startup; None compatibility; malformed/null/failed/thrown access; restored interrupt; fatal propagation |
| E6_Q6 | R3,R4,R7 | Actual corrupt/revised/absent/inactive signed row or Released quota; pre-get refusal and no query writes |
| E7_Q7 | R3,R4,R6,R7 | Actual metadata/reply/byte faults; no premature opening; bounded size+1; one opened stream closed |
| E8_Q8 | R3,R4,R6,R7 | Explicit native revision/deletion/quota-release after opening; final refusal with separately counted fault writes |
| E9_Q9 | R3,R6,R7,R8 | Real resolver lease blocks subsystem drain; new refusal; admitted response completes; node owns physical close; bounded actor cleanup |
| E10_Q10 | R1,R5,R8 | Default Supplied Absent and actual live failed/cancelled/nonterminal; legacy negative selections; no owned dispatch |
| E11_Q11 | R1,R2,R3,R4,R5,R6,R8,R9 | Real sequential Review JVMs; distinct identities; child-local exact domain and closed resources; reader no work/writes |
| E12_Q12 | R2,R3,R7,R9 | Valid fresh startup with overview-granted foreign current query; fixed durable refusal and no Blob |
| E13_Q13 | R1,R2,R3,R4,R5,R6,R8,R9 | Ordinary shrinking seed69710034/min24/workers1 scalar property; genuine pair per input |
