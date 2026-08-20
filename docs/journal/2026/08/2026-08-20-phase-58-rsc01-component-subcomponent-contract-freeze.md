# Phase 58 RSC-01 Component/Subcomponent Contract Freeze

status=recorded, pending parent review
date=2026-08-20
phase=[Phase 58](../../../phase/phase-58.md)
step=RSC01-A
slice=RSC01-A1-owner-inventory-and-reconciliation
repository=/Users/asami/src/dev2025/cloud-native-component-framework

## Purpose and Boundary

RSC01-A1 records the current Component/Subcomponent owner inventory,
reconciles the two existing non-normative proposals, and freezes the
architecture invariants and successor ownership needed by Phase 58.1 through
Phase 58.8. It is a class-D documentation slice.

No product code, executable specification, schema, API, type, wire format,
configuration, build file, packaging behavior, runtime behavior, or repository
behavior changed in RSC01-A1. Exact failing-first acceptance identities remain
owned by RSC01-B. The two implementation notes remain provisional until Phase
58.9 canonical promotion.

## Design History

The resource proposal began with one logical Component release containing
physical Documentation and SourceCode resource artifacts. The broader
architecture proposal later modeled every declared Subcomponent as an
independent Component with its own CAR and parent membership. These propositions
were compatible on payload non-executability, physical provenance, and the
separation of publication from activation, but conflicted on whether a
declared child created a Component identity/runtime participant.

RSC-01 resolves that conflict explicitly: a declared Subcomponent is an
independent Component with canonical identity and its own CAR. Documentation
and SourceCode remain initial role vocabulary for the child's information
payload; the payload is not itself a Component, is non-authoritative, and is
non-executable. The parent registry records child identity and membership but
does not duplicate mutable identity or grant activation, operation, MCP,
disclosure, or deployment authority.

The remaining resource proposal inputs are retained as successor concerns:
physical provenance/security, operation mode, lifecycle/concurrency, cache and
offline source evidence, and Help/Admin sanitized projections. Their concrete
schema/API/type choices were not selected by this slice.

## Read-only Evidence Snapshot

Evidence was read at these exact repository HEADs:

| Repository | HEAD | Evidence paths |
| --- | --- | --- |
| cloud-native-component-framework | `308fdd6d830c9aa1fdc8e1dcb6421e1f6ec0d57e` | `docs/spec/component-identity.md`; `docs/spec/canonical-car-repository-resolution.md`; `docs/spec/component-repository-index.md`; descriptor/extractor, repository/bootstrap, subsystem, Help/Admin, and WebResourceRoot owners listed in the main architecture note |
| cozy | `20c7761f2505a0fa8a6b484accdeba91f946fe50` | `docs/design/car-project-metadata-ownership.md`; `docs/design/component-repository-publication.md`; Cozy archive, manifest, index, publisher, and CML source resolver owners |
| sbt-cozy | `095c9c606acefa6f542b146797d5912a066dd124` | CAR classpath/dependency resolver owners; development-runtime evidence and namespace-qualified repository scripted fixtures |
| cncf-samples | `c601252b23b2a788f9869463c833de52ddfcd771` | CAR-directory, one-component SAR, multi-component, mixed standalone/bundled, implicit Subsystem, and expanded `sar.d` samples |
| textus-sample-apps | `3202c0204e57bb73b4aabfb7f0540185630b7f6b` | cwitter subsystem descriptor, Web descriptor, assembly descriptor, and component web-resource inputs |

Cozy had unrelated pre-existing dirty working-tree paths. They were excluded;
only Cozy's tracked HEAD was treated as evidence. No read-only evidence
repository was edited.

The concrete current-owner and successor mapping is maintained in
[`component-subcomponent-architecture-implementation.md`](../../../notes/component-subcomponent-architecture-implementation.md#current-behavior-and-ownership-inventory).

## Frozen Invariants

RSC-01 freezes these successor constraints:

1. Every declared Subcomponent is an independent CNCF Component with
   canonical identity and its own CAR.
2. Documentation and SourceCode are initial role vocabulary for the
   information payload. The payload is non-authoritative and non-executable
   and is not itself a Component.
3. The parent composition registry references child canonical identity and
   membership; it does not duplicate mutable identity or grant activation,
   operation, MCP, disclosure, or deployment authority.
4. Role is separate from implementation technology.
5. An external-platform child still has a CNCF CAR identity/metadata surface.
   Deployment of its platform artifact is explicit; CNCF never silently falls
   back to automatic platform deployment.
6. Parent relationship is separate from general Subsystem membership.
7. Logical release identity is separate from physical artifact identity,
   digest, path, and provenance.
8. Publication completeness is separate from runtime activation.
9. Discovery/availability is separate from activation.
10. Resolved provenance retains logical identity plus physical source evidence.
11. Authorization, integrity, and availability are orthogonal dimensions;
    Phase 58 must not collapse them into one enum/state prematurely.
12. Concrete registry schema, APIs, type names, wire formats, lifecycle
    machinery, and resolver implementation are owned by Phase 58.1 through
    Phase 58.8, but cannot reinterpret these invariants.

## Step and Phase Boundaries

- RSC01-A1 owns documentation-only inventory and reconciliation.
- RSC01-B owns the exact failing-first acceptance identities and matrix. It is
  not deferred to successor implementation phases.
- Phase 58.1 through Phase 58.8 own protected implementation and integration in
  the dependency order recorded by the Phase 58 split. They may choose
  implementation vocabulary inside the frozen invariants.
- Phase 58.9 owns canonical design/specification promotion, historicalization
  of these notes, dependent Phase 59/60 reconciliation, and Phase closure.
- This slice does not authorize implementation in CNCF, Cozy, sbt-cozy,
  samples, or applications, and does not authorize tests, SBT, publication,
  deployment, staging, or commit work.

## Separate Ledgers

### Phase Hygiene

| ID | Status | Scope |
| --- | --- | --- |
| HYG-P58-001 | OPEN / nonblocking | Normalize `SubComponent`/`Subcomponent` and `Source`/`SourceCode` terminology later. No broad terminology cleanup is part of RSC01-A1. |

New normative RSC-01 prose uses `Subcomponent` and the role `SourceCode`,
except when quoting an existing title or identifier.

### Development Candidates

None beyond the already authorized Phase 58.1 through Phase 58.9 series. Any
new out-of-series work must be recorded separately before authorization; it
must not be inferred from this inventory.

### Modified Scala File Compliance

Not applicable. No Scala file changed, and no source, executable
specification, configuration, or build file changed.

## Acceptance Ownership and Unresolved Items

RSC01-B still owns exact failing-first acceptance identities. Successor phases
own concrete registry schema, archive layout, resolver API, mode/lifecycle
machinery, security representation, consumer API, and cross-repository fixture
vocabulary within the frozen constraints. Those are unresolved implementation
items, not contradictions in the RSC-01 handoff.

The main architecture note is the detailed handoff; this journal is the
design-history and ledger record. Parent review, validation, and transition
decisions remain outside this implementation slice.
