# SimpleEntity Managed Attribute Catalog Consideration

date = 2026-07-26
status = reflected-in-design-and-spec
target_phase = 50
related_work = PC-01, PC-02

## Purpose

This journal records why CNCF-managed `SimpleEntity` attributes were gathered
into one design catalog and one static specification.

It is a chronological record. Normative behavior is defined by:

- `docs/design/simpleentity-storage-shape-policy.md`; and
- `docs/spec/simpleentity-storage-shape-policy.md`.

## Starting Question

The Phase 50 revision discussion first asked whether default OCC created an
unacceptable persistence cost for applications that ordinarily use
last-write-wins updates.

The discussion then separated two concerns:

```text
managed revision lifecycle
  !=
ordinary OCC enforcement
```

Most applications are expected not to select OCC. Their ordinary state changes
should therefore use a direct update without a target-record pre-read or
lock-read. Revision can still advance in that update.

## Attribute Question

Once ordinary update was expressed as:

```text
application change
+ updated_at
+ updated_by
+ revision advancement
```

the next question was which other attributes CNCF manages for a
`SimpleEntity`.

The existing storage-shape design listed identity, creation/update audit,
logical lifecycle, security identity, and revision. The runtime policy also
recognized deletion, publication, tenancy, observability, and permission
fields. Those fields were discoverable in code but not presented as one
canonical catalog.

## Resulting Classification

The catalog now distinguishes:

- identity;
- creation and update audit;
- logical, deletion, and publication lifecycle;
- tenancy scope;
- observability context;
- persistence generation;
- security identity; and
- permission policy.

This grouping avoids calling every managed field a lifecycle attribute.
`revision` is persistence-generation metadata used by OCC when enabled.
`traceId` and `correlationId` are observability metadata. Tenant and security
fields belong to scope and authorization rather than temporal lifecycle.

## Mutation Clarification

Management does not mean that every field changes on every update.

The accepted ordinary mutation rule is:

- successful `AlwaysWrite` changes `updatedAt`, `updatedBy`, available
  trace/correlation context, and `revision`;
- creation metadata remains unchanged;
- lifecycle, deletion, publication, tenancy, and security metadata change only
  through an operation that owns that concern;
- `WriteIfChanged` no-op changes no managed metadata; and
- failed, rejected, stale, or rolled-back mutation publishes no change.

Soft delete and restore remain explicit lifecycle operations. They update their
state/deletion fields together with update audit and revision.

## Persistence-Path Consequence

The attribute catalog does not justify sending every ordinary update through
the OCC transaction path.

Phase 50 PC-02 records three paths:

```text
None + AlwaysWrite
  -> direct provider update

Optimistic
  -> expected-revision compare-and-set

Conditional Transition
  -> explicit provider transaction and conditional boundary
```

The ordinary direct update must maintain its managed attributes in the same
mutation without a target-record pre-read, `SELECT FOR UPDATE`, or mandatory
authoritative readback.

## Representation Clarification

Standard `SimpleEntity` uses Embedded `revision`.

`cncf_revision` belongs only to the explicitly admitted Detached
non-`SimpleEntity` extension. It is not another `SimpleEntity` attribute and
must not coexist with Embedded revision.

## Documentation Decision

The existing storage-shape design remains the architectural source for field
classification and physical expansion.

A dedicated static specification now fixes:

- the complete managed-attribute catalog;
- create, update, no-op, delete, and restore effects;
- application-input protection;
- Embedded versus Detached revision representation; and
- the rule that managed revision does not implicitly select OCC execution.

No new runtime attribute was introduced by this documentation work. The change
makes the existing and planned contracts visible in one place and identifies
the PC-02 provider-path clauses that still require executable evidence.

## References

- `docs/design/simpleentity-storage-shape-policy.md`
- `docs/spec/simpleentity-storage-shape-policy.md`
- `docs/phase/phase-50.md`
- `docs/phase/phase-50-checklist.md`
- `docs/journal/2026/07/2026-07-24-simpleentity-revision-occ-consideration.md`
