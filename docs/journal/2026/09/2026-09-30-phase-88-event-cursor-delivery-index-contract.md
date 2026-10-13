# Versioned EventStore cursor and delivery-index design

Date: 2026-09-30
Status: design recorded; executable admission pending

Inspected the producer definitions CommittedTransition/CommittedDeliveryLookupKey,
complete CmlTransitionBinding and EventStore.InMemory.append. The typed lookup
key contains invocationKey, EntityId, complete binding and operationId; reducing
it to an invocation string or displayed transition attributes changes equality.

The new contract defines:
- A single host-pinned, Create-initialized cursor with guarded revision and
  canonical decimal-text last_sequence, checked overflow and no implicit reset.
- A separate V2 event envelope retaining the full explicit delivery discriminator.
  EventRecordJsonV1 remains unchanged and rejects unsupported keys.
- Create-only indexes only for persistent facts with explicit delivery keys,
  comparing full canonical keys after hashed lookup and validating exact event
  references, sequence and complete stored-body digest.
- EventId-before-key duplicate classification, retained-first facts and no new
  sequence or alias for retained duplicates. Conflicting retained indexes reject
  as new-route structural inconsistency, without changing legacy memory behavior.
- Cursor-guarded coherent classification/publication, complete readback and
  separate application admission before actual dispatch.

The typed EntityId constructor/constituent ABI is not guessed; closure of its
exact representation is a codec implementation gate. The same is true for
complete ordered trigger-context fields and binding options. No constructor,
authority or executable declaration can be recovered by parsing display text.

The host currently declares collections but does not yet pin a cursor entry ID;
implementation must add that assembly-owned identity without accepting it from
a caller plan. The selected managed composition's integral last_sequence field
is a test fixture, not this versioned decimal-text schema. Update future fixtures
explicitly; no runtime migration/reset is authorized by this design.

No code, codec, runtime dispatch or Operation/CLI was added this turn. No SBT
execution was needed for documentation-only work. Existing focused-320 remains
the latest implementation evidence (176/176 in 20 suites), not evidence for
these proposed cursor/index/V2 codecs.

Next is the pure cursor model/codec and executable malformed/overflow/reset
specs, followed by full typed discriminator/V2/index codecs and managed
composition. Full managed duplicate reconciliation and runtime dispatch must
remain distinct from current low-level Create-only rollback semantics.
S12/P2-U15/P2-U16/P2-U05 remain open. No commit/publication performed.
