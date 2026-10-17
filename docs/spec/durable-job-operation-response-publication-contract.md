# Durable Job OperationResponse Terminal Publication Contract

status=normative-partial
spec=durable-job-operation-response-publication
phase=69.7
step=JM69-10
slice=JM69-10B3
decision=PH697-JM10B3-TERMINAL-PUBLICATION-01@1

This contract owns the framework terminal lifecycle integration of the
[owned payload adapter](durable-job-operation-response-payload-contract.md)
and unchanged [bounded codec](durable-job-operation-response-codec-contract.md).
Its canonical integration points are the [Job operations contract](job-operations-contract.md),
[keyed evidence contract](durable-job-keyed-evidence-contract.md) and
[runtime owner contract](durable-job-runtime-owner-contract.md).
The [design](../design/durable-job-operation-response-publication.md) fixes the
ordering and responsibility boundary. The [execution journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md)
and [Phase ledger](../phase/phase-69.7.md) own selected execution and acceptance evidence.

## R1 Closed intent and legacy behavior

The private enum `DurableJobResultPublication` has exactly `Supplied` and
`OwnedOperationResponse`. `DurableJobLifecycleWriteEvidence` appends
`resultPublication` defaulting to `Supplied`. It carries no live response or
provider. The bridge constructor appends optional `resultPayloads = None`;
old two/three positional calls retain their meaning. Supplied evidence follows
the original projection/checkpoint path, including intentional `Succeeded(Absent)`.
The original six requests, keyed job/revision addressing and immutable task
models remain unchanged. Exactly one source fetch occurs per attempted boundary.
Null intent and owned intent at any nonterminal boundary refuse before its
native admission/checkpoint.

## R2 Successful terminal eligibility

Owned publication requires a genuine live Succeeded candidate with
`Some(JobResult.Success(non-null response))`, a configured non-null payload
capability, and an acknowledged Running/Pending snapshot. The independent
terminal seed must be `Succeeded(Absent)`. Entry/current snapshot consistency,
original native binding access equality and original signed authorization
equality are required. Failed, Cancelled, Pending, null, non-Absent and repeated
already-terminal candidates refuse without publication. Repeated terminal
requests stop at the original snapshot guard before another source fetch.

## R3 Independent signed preflight

Source authorization, access, tasks, inputs, retry, diagnostics, calltree,
definition, retention and semantic revision remain independently supplied.
They must never be reconstructed from live context, Task or raw result.
Reuse the original native projection or recovered checkpoint projection,
including recovered-cancel confirmation. Canonical signed roundtrip/decode
under supplied access precedes any owned Blob publication. Invalid semantic,
replay, retry or access evidence therefore cannot allocate a payload.

## R4 One actual producer

After successful preflight, invoke the existing `publishObserved` once using
the original Running/Pending snapshot, supplied access, actual live success
response and existing lifecycle ExecutionContext. The adapter owns B1 encoding,
native access, policy/quota admission, Blob metadata, physical acknowledgement
and its own failed-publication rollback. The bridge must not create a synthetic
reference, duplicate encoding, placeholder, fallback or source-provided raw body.

## R5 Original signed checkpoint

Replace only the projected body result with
`Succeeded(External(actual acknowledged reference))`. Retain every other field
exactly, create V2 and canonically roundtrip/decode under the same access, then
call the original optimistic store checkpoint and entry replacement.
No second evidence fetch, extra semantic revision or extra timeline event is
permitted. The acknowledged checkpoint owns durable terminal success.

## R6 Facade compatibility

Live raw `getResult` and query results retain their original response values.
Fresh terminal projection registers the genuine signed terminal fact while raw
`getResult` and query result remain None and management remains
`UnavailableAfterRestart`. B3 adds no public result read or queryVisible behavior.
The separate [current-caller query contract](durable-job-operation-response-query-contract.md)
owns eligible queryVisible consumption after ordinary overview authorization and
independently composed current raw tenant/subject/scopes. Its same-owner leased
B2 read returns only a detached model copy; raw query/getResult and original
management remain None/UnavailableAfterRestart. Startup credentials and signed
authorization never supply caller grants. The [query design](../design/durable-job-operation-response-query.md)
fixes admitted-close/final native checks and no caching; its
[journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md) owns execution.
Publication retains the original producer/record/payment/quota responsibility.

## R7 Failure and acknowledgement ambiguity

Original native OCC, missing/invalid/unsupported evidence, bounded codec and
producer failures, failed/lost provider acknowledgements, close/lease refusal
and interruption remain authoritative. Owned shape failures use the existing
redacted payload refusal; original engine failure facts remain unchanged.
The engine can already hold a live Succeeded value before checkpoint refusal.
Failure records `TerminalOutcomeCheckpointRefused` and emits no job success event.

A successful Published payload followed by checkpoint refusal or lost
acknowledgement remains physical/metadata/quota audit evidence. The durable
terminal might already have persisted. The bridge must not delete, release,
refund, retry or claim that durable persistence was absent. Producer rollback
is solely the producer's original failed-publication responsibility. Publication
and checkpoint do not form a strengthened transaction.

## R8 Actual native owner and remaining consumer boundary

The configured owner constructs one original `_payloads`, one `_result_payloads`
from those same payloads, and the original bridge with that capability.
Original engine, stores, lease, timer, scheduler, recovery, startup, health and
shutdown ownership remain unchanged. Reads in executable fixtures use the
actual owner lease. Separate configured owners in the same process establish
independent composition; they do not establish separate-JVM or actual CBD
acceptance. The actual CBD gateway consumes queryVisible.model.result and its
current ReviewPipelineTask is a pending-provider stub. An independent six-field
Review fixture cannot establish that actual completed downstream behavior.

## R9 Executable specification cases

[DurableJobOperationResponsePublicationSpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponsePublicationSpec.scala)
binds TP1..TP13 to adjacent Given/When/Then, should expectations and afterWord
metadata `spec:durable-job-operation-response-publication`, `phase:69.7`,
`slice:JM69-10B3`. Fixtures use actual managed SQLite/local Blob and SystemNode
ownership under unique target/configured-durable-runtime/work children, with
finally cleanup and independently supplied recipe/domain oracles.

| Case | Rules | Required behavior |
| --- | --- | --- |
| TP1 | R1,R2,R3,R4,R5 | Actual task-produced six-field Review, six contiguous signed revisions, exact acknowledged ref/domain/types/independent wire and retained non-result evidence |
| TP2 | R1,R2,R4,R5 | Scalar publication once; repeated actual terminal guard before another fetch/put, no refund |
| TP3 | R1,R6 | Original default Supplied absence, zero puts, original live result identity/query value |
| TP4 | R1,R2,R7 | Original direct native bridge without capability refuses genuine terminal publication and retains Running/Pending paid prefix |
| TP5 | R1,R2,R7 | Null intent at all six boundaries and owned intent at five nonterminal boundaries; exact observed prefix before source fault |
| TP6 | R2,R3,R7 | Pending/unrelated External, wrong tenant/subject/scopes, authorization and retry evidence refuse before allocation |
| TP7 | R2,R4,R7 | Actual unsupported Opaque or null success refuses with zero allocation and no success event; live settlement retained |
| TP8 | R4,R5,R7 | Corrupt returned size/digest preserves original producer-owned single put/delete and Released quota marker |
| TP9 | R3,R5,R7 | Original terminal-only lost update acknowledgement retains actual committed signed terminal and Published bytes despite checkpoint failure |
| TP10 | R2,R4,R7 | Configured payload capacity and original closed owner lease deny new publication without another put |
| TP11 | R4,R5,R6,R8 | Fully closed writer and fresh configured owner retain genuine signed terminal/payment/Published bytes; raw/management restart compatibility |
| TP12 | R1,R2,R3,R4,R5,R6,R8,R9 | Normal shrinking generated nonempty ASCII1..12 Scalar/Record native flow, seed69710033L/min24, checked.passed and succeeded>=24 |
| TP13 | R2,R7 | Actual nonretryable failed task and real control Cancel from running body refuse owned success publication, retain native task/control prefix |

The [journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md)
records the selected six/114-suite execution boundary. Independent complete
original JM69-10 Step acceptance remains required alongside the entire
[cross-process/downstream contract](job-cross-process-acceptance-contract.md)
and [Phase checklist](../phase/phase-69.7-checklist.md).
