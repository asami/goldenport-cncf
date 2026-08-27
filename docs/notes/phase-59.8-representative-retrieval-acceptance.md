# Phase 59.8 P598-S2 Representative Retrieval Acceptance

This is noncanonical P598-S2 executable acceptance evidence. It is not a
test receipt and does not close Phase 59.8 canonically.

## Read-only retrieval evidence

The bounded executable specifications are:

- org.simplemodeling.textus.bok.RepresentativeDocumentationProfileRetrievalSpec
  in textus-bok.
- org.simplemodeling.textus.cbdsupport.RepresentativeDocumentationProfileCarrierSpec
  in textus-cbd-support.

Both specifications use existing typed, digest-bound value contracts. The BoK
spec reads an in-memory cncf.knowledge-source.v1 manifest containing exactly
one component-knowledge consumer contract and one semantic index, and proves
public framework, mounted Directive, and descriptive public Skill Catalog
records. The equivalent CBD spec admits the canonical encoded contract only
through its exact carrier digest. The profile keeps the
simplemodeling/0.1.0 framework identity, canonical document and section
identities, publication availability, public metadata identities, resource
digests, and the authorization-denied SourceCode fact. Raw contract/source
bytes, repository or physical provenance, bundles, installation or activation,
runtime or MCP authority are outside both value-only/read-only projections.

The existing P597 BoK-to-CBD coordinate handoff remains the exact
existence/detail boundary proof in:

- org.simplemodeling.textus.bok.BokCbdComponentReferenceHandoffSpec.
- org.simplemodeling.textus.cbdsupport.ComponentReferenceHandoffSpec.

Those handoff specifications continue to prove that BoK supplies exact
existence coordinates while CBD owns catalog detail and usage selection; this
P598-S2 evidence does not replace or broaden that boundary.

## Deferred Skill Catalog boundary

Under user decision D-P598-SKILL-CATALOG-001, this slice accepts only
descriptive public Skill Catalog metadata. Actual CAR-owned
SkillBundleManifest linkage, schema/path/digest validation, CAR
packaging/projection, installation, and activation remain deferred to CNCF
Phase 66, Cozy Phase 24, and launcher work.

Phase 59.9 owns full, adversarial, and downstream validation. Phase 59.10
owns canonical documentation closure. This note makes no claim for either
phase and records no validation receipt.
