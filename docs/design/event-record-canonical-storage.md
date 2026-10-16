# EventRecord canonical storage boundary

Phase 88 S9 first normalizes immutable event facts into the existing record
algebra. EventRecordJsonV1 is event-owned and contains no Workflow dependency,
SQL, resource ownership, callback or second datastore. Its closed tagged
payload preserves admitted runtime types instead of relying on lossy generic
Any-to-JSON stringification.

The complete EventRecord is stored as one canonical string; recovery decodes
and validates that representation before a runtime may expose any fact.
EventId typed components are reconstructed exactly, not interpreted. Only fully
issued timestamp/entropy options are admitted: UniversalId fills missing options
with generated identity parts, so reconstructing missing options cannot preserve
operational identity. Reject instead of resolving or reissuing them.
Assigned
sequence is retained as data, not authority to allocate/reuse a global slot.

The Create field wraps canonical JSON in an exact non-JSON storage prefix,
following the existing provider-owned text storage pattern without importing
Workflow. SQL's generic JSON-to-Record decoding is unchanged. Recovery removes
only the admitted prefix then strictly decodes the canonical body; raw JSON
is not a compatibility route. Use EventId.value as the opaque canonical key.

Create-only composition is intentionally narrower than EventStore append:
duplicate identity fails and rolls back the canonical plan. Future EventStore
append must reconcile existing retained identities, ordered sequence allocation
and explicit delivery-key deduplication under a proven transaction guard.
Unsupported delivery keys fail closed now; silent omission is forbidden.

This dependency boundary does not change EventEngine.noop, UnitOfWork,
TransactionContext, or existing EventStore behavior. Those still need one shared
prepare/commit/abort domain and pre-execution admission of every participant.
See ../spec/event-record-canonical-storage.md for the normative wire contract.
