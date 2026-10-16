# Aggregate lifecycle capture ownership

Document role: settled internal PHASE-96 / P96-07 / P96-07C4A design.
The [paired specification](../spec/aggregate-lifecycle-mutation-capture.md) owns
behavior. This design supplies no executed validation or acceptance claim.

## Existing canonical producers remain authoritative

ActionCallRepositoryPart owns the two additive internal application edges.
Actual Aggregate registration, declared aliases and the canonical unique root
resolver establish ownership before lifecycle execution. The registered root
descriptor supplies Optimistic policy and managed Embedded/Detached binding.
Creation preserves its input type C and EntityPersistentCreate[C]; deletion
preserves its resolved domain type A and pure validator. Neither edge exposes
an erased writer, capture constructor or new provider/algebra protocol.

AggregateAuthorization owns normal declared policy. The complete request is
evaluated on the actual active UoW before application callbacks and retained
for canonical persistence authorization. Type create uses its fixed command;
instance delete uses lifecycle access without inventing a command. The default
loader, current factory, access metadata, relations and operation model remain
their established owners. No System/ServiceInternal permission is synthesized.

The existing typed create and observed-delete outcome operations already own
canonical ACK retention before optional coherence. Creation delegates unchanged
default options, strict ID policy, defaults, INITIAL revision and root/body
atomicity. Deletion loads the registered authoritative revision before builder
or validator, retains exact root correspondence and legal successor admission,
and delegates atomic recheck plus physical/logical lifecycle semantics. It does
not save Aggregate.toRecord or rebuild a deletion result.

## One current UoW and bounded callback admission

Each helper selects the existing lazy UoW once. Existing preserving Aggregate
outer/phase wrappers and active evaluation perform authorization and one
canonical outcome operation without entering run/commit/abort/dispose. The
caller retains events and enclosing transaction finalization. Exact interpreted
outcomes and raw native Record references remain unchanged through optional
coherence Failure; no postwrite read supplies replacement raw evidence.

A new private lifecycle callback guard has its own fixed diagnostic and wraps
only new application/identity/collection/root correspondence callbacks. Genuine
structured Conclusions survive; malformed or NonFatal callbacks are redacted.
The existing preserving runner owns signal propagation and balanced cleanup.
Legacy Aggregate/update and interpreter/provider callback policy remain intact.
Known rollback and unknown ACK have different durable states and unchanged
classifications. Neither permits a fabricated capture or automatic replay.

## Fixture composition and independent state

The object-owned fixture reuses the real UoW capture environment, observing
store/Space and independently specified expected create/delete transformations.
Its named Component installs a canonical collection with the same selected
Realm/memory and an Optimistic copy of the existing plan, current operation
declarations, root and alias Aggregate builders, and the actual semantic View.
One new runtime owns a consistently bound context/UoW/interpreter against those
same real resources and the original deterministic ID context. Copying the
old context while retaining its other runtime/UoW would break this ownership.

The registered persistent delegates actual post-ACK optional decoding to the
existing fault seam. Its ordinary authoritative reads decode registered
identities directly; they do not consume the optional-coherence decoder fault.
Root-render/identity fault activation occurs after actual contextual Aggregate
resolution, keeping normal authorization loading observable. All state
expectations precede action and compare whole ordered logical Records plus
complete physical tables, columns/types, ordered rows and NULL cells. No
unexpected field is suppressed and no result is manufactured by an observer.

Separate create/delete laws use bounded representation-specific roots and
owned pools. Actual cleanup of only the known owned IDs precedes legal reuse.
The native positive gate remains mandatory; existing memory/SQLite and retained
MySQL suites preserve their separate provider obligations. Parent-selected
representative15 and expanded125 execution, independent assurance and formal
acceptance remain outside this implementation role.

## Compatibility and retained work

This change adds no public schema, transport, default, provider, storage grammar
or cross-repository implementation. All four P07 criteria remain OPEN. Ordinary
Display create/delete/List, strict ingress, committed contextual reload, PG-E12,
C4B/C4C, P08..11, original Step commits, protected Step acceptance, the sole
epoch-1 full Phase review and validated release/ledger/reciprocal aggregate
remain required. The existing trait's size, legacy naming, nested
AggregateRootState and imports retain their parent's separate Hygiene
disposition; this boundary authorizes no extraction or unrelated cleanup.
