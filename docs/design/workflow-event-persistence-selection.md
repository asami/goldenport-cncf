# Workflow selected-event persistence

Date: 2026-09-30
Status: EventStore-owned policy correction passed focused-309; runtime adapter/provider not accepted

The EventStore persistence declaration is a host storage/permission contract,
not a persist-all policy. EventStore owns a fixed versioned
`EventPersistencePolicy`, deterministically matching explicit exact
`EventTypeId` rules against `EventRecord.kind`. Persist/Transient decisions do
not use caller flags, payload heuristics, AI actions or wildcard defaults.
Unmatched kinds are transient; malformed kinds or batches reject before writes.
Senders send ordinary events with no persistence option. Sender overrides are
future extensions, unused here. Runtime policy belongs to the configured store,
not an AI result or skill override. Pure policy data grants no storage rights.

`WorkflowEventSelection.prepareFromStoreC` asks the store for selection, records
the policy revision and partitions
an unissued transactional batch, retaining order and normalizing its flags.
Only persistent events acquire durable cursor sequences, canonical event
Creates and delivery-key index Creates. Transient events retain sequence zero
and are neither stored nor replayed. Empty/all-transient selection creates no
event cursor work; the Workflow root/Continuation commit is still required.

Runtime delivery of both subsets waits for commit and authoritative readback
verification. Durable delivery uses stored readback, transient delivery uses
in-memory records, drained in original input order. A definite rollback drops
both; an indeterminate outcome suppresses both and permits no automatic replay
or transaction retry. Transient delivery is best-effort and can be lost after
commit before dispatch or on restart. Post-commit dispatch failure cannot undo
commit. Workflow preparation rejects the nontransactional lane; ordinary
configured EventStore append applies its policy to either lane. Workflow
rollback cannot retract an already emitted nontransactional event.

The policy and selection APIs are pure data processing, not persistence,
dispatch authorization or commit evidence. Their enum-based delivery projection
is only a planning function; a runtime must supply the real verified outcome.
The compatibility `prepareC(policy, records)` helper is pure planning, not runtime
policy ownership. The candidate `EventStore.inMemory(policy)` filters ordinary
append, including EventEngine emit without sender persistence options. Only
durable events enter storage, identity/delivery indexes and sequence allocation;
transient records return at sequence zero in input order. This is memory retention,
not managed SQLite persistence or dispatch acceptance. The legacy no-argument
store remains unchanged and cannot prepare the new route. The prepared two-record
adapter is not integrated. Append success alone grants no dispatch authority.

Eight new EventStorePersistenceSelectionSpec examples cover ordinary emit, selected
retention/indexes/sequences, no-op excluded batches, store-owned prepare-time
revision, both lanes, malformed-batch rejection before mutation, transient/durable
identity isolation and first-record durable deduplication. All eight passed
focused-309 (107/107 in 14 suites), alongside seven complete multi-guard readback
examples, after prerequisite-306 and test-fixture corrections. Exit 0/0 and
lock=released were observed. This is not managed runtime or typed Phase/PLAN
acceptance. See the
[resumed validation journal](../journal/2026/09/2026-09-30-phase-88-focused-validation-resumed.md).

The [ordinary-send follow-up](../journal/2026/09/2026-09-30-phase-88-eventstore-ordinary-send-boundary.md)
records the earlier unexecuted state and recovery interpretation. The original
platform response did permit informed approval, subsequently obtained. The user
then selected simplified parent execution retaining platform review and shared
SBT serialization; prerequisite and focused validation succeeded.

Before runtime acceptance, route specs must prove selected-only event/index
Creates, durable-only cursor allocation, no persist-all under a declared
Subsystem, dispatch only after readback, original-order mixed delivery, no
delivery on rollback/uncertainty, and no transient replay on recovery. The
existing S12 default-deny host-admission and same-provider atomicity gates
remain required and unclosed.

Historical focused-293 passed all 45 tests in five suites, including the selection
and Subsystem host specs, after Cozy classpath prerequisite-292. The installed
declaration/transient-only selection example creates neither a managed
resource nor a SQLite file. Legacy/unknown grants, split resources, escaped
views, ambiguous declarations and scope restoration are also covered. The
unsupported positive provider path remains fail-closed. This evidence does
not establish actual selected persistence, dispatch or full regression.

## Selected-only canonical Create preparation (2026-09-30)

`WorkflowSelectedEventRecords.prepareC` connects the persistent subset to
canonical EventRecord Creates, proposing overflow-safe contiguous sequences
only for those events. Empty/transient-only batches create no event records
or cursor movement. This is pure preparation, not global sequence allocation:
the host must atomically guard the exact cursor revision via DataStoreSpace
before the proposed range becomes authoritative. No cursor/index write occurs.

The codec sequence-copy helper retains private delivery keys, so unsupported
discriminators still reject rather than silently disappearing. Preparation
retains canonical durable bytes. `admitDeliveryCandidatesC` rejects missing,
divergent, reordered or unexpected durable readback; an exact match reconstructs
durable candidates and interleaves transient candidates in original order.
This pure comparison is not commit evidence or dispatch authorization. The host
must independently verify commit and root/Continuation/cursor/index readback;
a comparison failure after commit is transaction-indeterminate, not rollback
or permission to retry. All-transient delivery also requires verified commit.

Focused-294 passed 30/30; final focused regression-295 passed 50/50 in six
suites after adding delivery-key preservation through selection and sequence
copying. Both exits were 0/0 and the serial lock was released. Actual S12
provider, cursor/index allocation, selected-event commit/dispatch and P2-U16
remain open. This is not full-suite or production runtime acceptance.

## Selected canonical managed composition (2026-09-30)

Focused-320 passed 176/176 tests in 20 suites. The internal verified-storage
projection decodes exact selected canonical event bytes, compares the frozen
proposal and reconstructs mixed-order candidates. Real host compositions prove
selected-only Creates, durable-only sequence proposals guarded by revision and
last sequence, rollback on cursor mismatch/second event collision, and absent
transient storage after reopen. This does not authorize dispatch or accept a
versioned global cursor/index representation, all-transient runtime path or
EventStore query/replay service. The unsupported delivery-key codec gate remains.

## Cursor/index schema design continuation (2026-09-30)

The [versioned cursor/index contract](../spec/event-cursor-and-delivery-index.md) is a design candidate, not
executable acceptance. It clarifies that delivery-discriminator indexes are
only for persistent events with explicit typed keys; key-free events use their
EventId collection identity. Decimal-text cursor sequences differ from prior
integral test fixtures. Full delivery facts require a separate V2 envelope;
V1 keeps rejecting unsupported keys. Pure codec/spec and host identity work
must precede managed duplicate reconciliation or actual runtime dispatch.
