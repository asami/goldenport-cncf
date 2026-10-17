# Durable Job OperationResponse Current-Caller Query

status=normative-partial-design
phase=69.7
step=JM69-10
slice=JM69-10B4

The [query contract](../spec/durable-job-operation-response-query-contract.md)
owns visible result behavior. The unchanged JobEngine queryVisible signature
remains the public entry. The original query returns a model or absence;
ordinary overview policy authorizes that original model before any private
result selection. Live/result-bearing and all noneligible legacy selections
retain their original values. Only this Job ID's registered closed Succeeded
projection with the exact owned operation-response header enters hydration.

The trusted original per-Subsystem runtime provider optionally composes one
package-private DurableJobCallerAccessResolver after the existing migration
input. Null container/element is rejected at startup; None retains compatible
startup and cannot authorize an eligible result. The resolver receives only
the actual current ExecutionContext. Production defines no mapping from
attributes, aliases, startup access, signed facts, capabilities or global state.
Its raw tenant/subject/scopes are shape-checked without altering their values,
then the existing durable signed authorization performs exact matching.

The query selects the existing owner through brief engine monitor reads. The
owner briefly checks its Ready state, engine readiness and exact owner identity,
then acquires its original real binding lease. All monitor reads finish before
the resolver or provider executes. Within that admitted lease the owner resolves
once and invokes its one original result payload reader explicitly using the
actual caller context, overriding its startup given only for this read.
No second owner-ready check is introduced inside admitted work. Logical close
stops new admission; subsystem drain waits for the existing read, and the original
SystemNode owns physical finalization. No arbitrary callback or native stream
holds an owner or engine monitor.

The [original B2 payload reader](durable-job-operation-response-payload.md)
owns current signed access, Active retention, native membership/metadata,
Published quota, one bounded integrity/EOF read, stream close, final optimistic
revision/retention/quota admission, strict UTF-8 and B1 decode. Observed concurrent
faults refuse; no guarantee is added against effects after the final observation.
The [B3 producer](durable-job-operation-response-publication.md) remains the sole
framework terminal publisher. Consumer code adds no producer, store or cache.

Only a detached model copy carrying the exact decoded OperationResponse escapes.
The body-free [terminal projection](../spec/durable-job-terminal-projection-contract.md),
raw getter/query, executable maps, management result, payment, source, quota and
payload remain unchanged. Every later caller resolves and reads independently.
Returned overview refusal stays original. Thrown NonFatal policy callbacks and
all private hydration failures become the fixed redacted query refusal;
interruption restores the flag and fatal errors propagate.

The [runtime owner contract](../spec/durable-job-runtime-owner-contract.md),
[Job operations contract](../spec/job-operations-contract.md) and
[cross-process contract](../spec/job-cross-process-acceptance-contract.md)
are canonical integration points. Genuine Task-produced native result fixtures
and separate writer/reader JVMs assert domain locally before closed observational
receipts. They do not complete the actual Textus CBD Support provider pipeline
or establish its independently fresh-runtime completed Review acceptance.
Decision rationale, rejected alternatives and execution/accounting belong to
the [query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md).
