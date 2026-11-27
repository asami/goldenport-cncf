# CNCF test selection and framework bindings

Date: 2026-10-31
Status: CNCF supplement to the test policy. The bounded discovery and
selection connection dates to 2026-11-06. Catalogue migration and qualification
across consumers remain pending.

Follow the [CNCF test policy](test-policy.md#scala-test-classification-and-default-execution)
for classification, default execution and this repository's test scenarios.
The [CNCF implementation plan](../phase/test-classification-metadata-plan.md)
tracks exclusion-reason declarations and migration of the existing tags.

## Metadata and framework selection

CNCF owns validation metadata, discovery, exact operation resolution and
framework bindings. Preserve the source's classifications and framework facts
alongside feature, expected duration, execution requirements, logical identity,
provenance and result attribution. Framework adapters expose the tags and
annotations they actually observe. The canonical metadata ABI and its
implemented queries are described in [Validation/Test Metadata V1](validation-test-metadata.md).

Resolve the selected operations before resource preparation or suite lifecycle
setup. CNCF consumers use the same framework discovery and exact resolution
without maintaining a second suite registry. Execution requirements carry
actual provider and environment prerequisites through the existing capability
checks. Labels and framework facts describe a test. Execution authority comes
from those checks and the selected operation.

## Low-frequency maintenance

For CNCF recovery and acceptance scenarios, retain the exact prerequisites,
invocation, expected result, cleanup and versioned execution evidence.
Qualification covers the selected scenario and its actual prerequisites.

## Migration and evidence

Coordinate any metadata or selection implementation change with its CML
mapping, generated inventory and actual consumers. Historical receipts retain
their original vocabulary, selectors and results. Apply the common authoring
rule when the source is classified. Label-based invocation and any related ABI
changes are separate work.

Acceptance: bounded normal/ADMISSION/Slow/explicit-suite/named-focused selection
checks, class and per-test tags, both annotations together, inherited metadata,
preparation after final selection, a harmless real dispatch and accurate results.
Use the existing execution owner. Do not run external services or expensive
self-update catalogues just to prove exclusion. Previously passed 22/13 and
7/77/16 tests retain their original scopes, not proof of this replacement contract.

## Independent per-test execution modes — 2026-11-02

[Mode declarations](test-execution-mode-declarations.md) are required execution
variants, not custom classifications or replacements for Slow/DoNotDiscover.
Source metadata owns class/per-test requirements; build.sbt wires shared
execution and sm-workflow selects/collects variants. Excluded or explicit-only
tests are not re-enrolled merely because a requested mode matches.
Coordinate [TM-01..04](../phase/test-execution-mode-plan.md) with the
[classification migration](../phase/test-classification-metadata-plan.md)
and qualify the applicable build and discovery paths before claiming delivery.

## Bounded discovery connection — 2026-11-06

`ValidationDiscoveryV1` now retains framework tags and explicit-only facts on
actual discovered operations. Automatic discovery inspects DoNotDiscover before
suite construction. An exact `explicitSuites` request may enroll the named suite;
feature/wildcard queries and Slow selection alone do not enroll it. The opt-in
`standardFramework` query applies ordinary exclusions, with `includeSlow`
reserved for explicit selected scope. Existing purpose queries retain the
behavior of their deployed adapter.

Source-authored executable specifications cover class/per-test Slow,
DoNotDiscover construction exclusion, exact focused mode variants and real
harmless fixture consumption. Installed build/discovery applicability and
end-to-end merged-project qualification remain outstanding.
