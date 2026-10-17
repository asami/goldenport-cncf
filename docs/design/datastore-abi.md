# Textus DataStore ABI

status=initial
authority=canonical
updated_at=2026-10-14
related=[Availability Architecture](availability-architecture.md)

## Purpose

DataStore ABI is the durable compatibility contract between a released CAR and
the authoritative persistent data it reads and mutates.

It is an ABI in the architectural sense: a CAR may be replaced independently
only while the persistent representation and DataStore mapping remain compatible
with the CARs that the deployment policy promises can still run.

DataStore ABI is not merely the physical SQL schema. It includes the persistent
logical types, required fields/collections, storage mappings, encoding/codec
expectations, identity/key semantics and mutation assumptions needed for a CAR
to operate correctly on existing authoritative data.

## Backward Compatibility Rule

Normal Textus updates preserve the DataStore ABI required by the retained
previous CAR during its Fallback Retention Window.

A new CAR may add compatible persistent information or introduce new mappings
that old code can ignore. It must not, as part of a Normal Update, remove,
reinterpret or destructively rewrite persistent information that the retained
CAR requires for correct operation.

The compatibility promise is behavioral, not only structural. A column/table
remaining present is insufficient if its meaning, encoding, identity semantics
or required invariant has changed incompatibly.

## Compatibility Examples

Typically compatible Normal Update changes include:

- adding optional/defaultable persistent properties;
- adding new collections/entities not required by the previous CAR;
- adding indexes or physical optimizations that preserve logical behavior;
- adding a new representation while retaining the old representation required
  by the fallback CAR;
- extending enum/value domains where the old CAR can safely ignore or reject
  values according to its declared contract.

Potentially breaking changes include removal/rename without a compatibility
mapping, incompatible type/codec changes, changed key/identity semantics,
destructive normalization/rewrite, or introducing data values that make the
fallback CAR behave incorrectly.

Exact rules are refined by DataStore type/mapping implementation and executable
compatibility evidence.

## Normal Update

A Normal Update is eligible for the ordinary immutable Deployment Transaction
only when the required DataStore ABI is backward compatible with the retained
fallback CAR.

Normal Update may therefore use the simple path:

```text
admit compatible CAR
 -> warm/probe
 -> switch
 -> fallback retention
 -> commit
 -> retire old CAR
```

No generic schema rollback machinery is required for this path.

## Maintenance Update

A change that cannot preserve the required DataStore ABI is a Maintenance
Update. It is intentionally outside daily automatic-compatible operation.

Maintenance Update requires an explicit migration decision and may require
human approval, a maintenance window, backup/recovery preparation, dedicated
migration validation and downtime. Its recovery contract is migration-specific
rather than pretending that ordinary switch-back remains available.

After a Maintenance Update establishes a new DataStore ABI baseline, subsequent
compatible CAR releases may again use Normal Update.

## Architectural Consequence

Textus prefers keeping ordinary persistent evolution backward compatible over
making every possible breaking migration transparently zero-downtime.

This constraint deliberately trades occasional explicit maintenance for a much
simpler, more reliable daily availability/release mechanism.
