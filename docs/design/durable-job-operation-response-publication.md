# Durable Job OperationResponse Terminal Publication

status=normative-partial-design
phase=69.7
step=JM69-10
slice=JM69-10B3

The [publication contract](../spec/durable-job-operation-response-publication-contract.md)
owns opted-in framework terminal publication. The existing
[payload adapter](durable-job-operation-response-payload.md) owns bytes and
native producer/reader authority; the [codec](durable-job-operation-response-codec.md)
owns the bounded closed wire.

The independent evidence source supplies a closed intent with its original
authorization/access, task, input, retry, diagnostic, calltree, definition,
retention and revision facts. `Supplied` retains the original checkpoint path.
`OwnedOperationResponse` permits only the bridge's actual successful terminal
response to enter the native payload producer. No live response enters the
source, and no signed non-result fact is inferred from execution context.

The terminal ordering is fixed:

1. Original live/request/current-snapshot guards and the one keyed source fetch.
2. Original binding access and signed authorization equality; Running/Pending
   snapshot and Succeeded/Absent seed admission.
3. Original native or recovered projection and signed canonical roundtrip.
4. One actual B2 publishObserved under the existing lifecycle context/lease.
5. Result-only substitution of the acknowledged owned reference, V2 signing and
   canonical roundtrip.
6. Original optimistic store checkpoint and acknowledgement entry replacement.

The original recovered projector and recovered cancellation confirmation remain
shared responsibilities. Source fetch count, semantic revision and timeline
event count remain those of the original terminal boundary.

Publication and checkpoint have separate acknowledgement ownership. Failed
publication uses the existing producer rollback. Successful Published payload
followed by failed/lost checkpoint acknowledgement retains actual quota,
metadata and physical evidence; its durable terminal can already exist.
The bridge neither rolls back nor refunds nor retries the producer. Original
terminal-checkpoint refusal and suppressed job success event are compatible
with an already live Succeeded result. No atomic combined transaction is promised.

The configured runtime owner creates one payload service from its original
owned payload composition before constructing the original bridge. All native
operations retain actual SystemNode binding lease ownership and shutdown gates.
The owner does not acquire a replacement engine, store, scheduler, timer or pool.

The publication boundary retains raw/getResult/query/management behavior.
Fresh terminal projection registers signed completion. The separate
[current-caller query contract](../spec/durable-job-operation-response-query-contract.md)
and [design](durable-job-operation-response-query.md) require original overview
authorization, then independently resolved current raw tenant/subject/scopes
inside the same ready owner's actual lease. Its one original B2 reader owns
current signed/Active/Published and final native checks. Only an exact detached
queryVisible model result escapes; projection/raw/management remain body-free
and no caller grant or response is cached. Admitted reads may finish through
logical close while SystemNode retains physical finalization. Startup credentials
and signed authorization are not ordinary caller authority. Query evidence
belongs to the [query journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-query.md).
Actual CBD completion requires its real completed provider pipeline and fresh
runtime; the six-field Review fixture and same-process reopen do not establish it.

Canonical integration points are the [keyed evidence contract](../spec/durable-job-keyed-evidence-contract.md),
[runtime owner contract](../spec/durable-job-runtime-owner-contract.md) and
[Job operations contract](../spec/job-operations-contract.md).
Decision rationale, rejected alternatives, authoring history and execution
receipts belong only to the [journal](../journal/2026/10/2026-10-04-phase-69.7-job-result-publication.md).
