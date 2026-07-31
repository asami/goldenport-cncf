# Phase 53 CS-02 Schema Decision

Date: 2026-07-31

Decision ID: P53-CS02-SCHEMA-001

## Decision

The Phase 53 owner selected **Option A**: CS-02 will formalize the minimum
versioned ComponentStyle catalog and generated component-descriptor contract
now, rather than leaving its wire/API decisions for a separate approval step.

The formalization is limited to the CS-02 boundary already established by the
Phase 53 checklist and the consolidated planning journal:

- CML `COMPONENT` selects the built-in
  `full-fledged-with-standalone` ComponentStyle.
- the CNCF-built-in catalog is the sole Phase 53 provider;
- the generated descriptor records style identity, provider/schema identity,
  declared and effective ComponentCapabilities, required
  SubsystemCapabilities, and mode-free parameters only; and
- duplicate, unknown, cyclic, version-incompatible, and catalog-disagreeing
  identities fail deterministically.  Legacy style-less generated descriptors
  are rejected by this new versioned contract.

The CS-02 plan must make the exact `apiVersion`, descriptor `schemaVersion`,
catalog resource location, canonical serialization order, typed Scala
observation API, and empty initial mode-free parameter schema explicit in the
authoritative CS-02 contract.  It must not introduce Component mode,
FixedUserProfile, locale, or datastore-policy fields.

## Consequence

This decision supersedes the CS-01-only deferral of CS-02 wire/API choices.
It is authorization to freeze the minimal contract within CS-02; it does not
authorize external Metadata Factory contributions or work owned by CS-03
through CS-06.
