# Phase 55 GCF-01 Binding-Contract Freeze

Date: 2026-08-02

Phase 55 GCF-01 froze the implementation set to `simplemodeling-lib`, CNCF,
`cncf-launcher`, `textus-launcher`, and `textus-art-scene`. The generic core is
owned by `simplemodeling-lib`; CNCF owns the four initial concrete target cases
and Textus/CNCF catalog semantics. This preserves the dependency direction.

The binding model separates immutable candidates from effective bindings.
Only effective bindings carry a direct overridden binding. One source loads
once per resolution snapshot; rank dominates across sources and target
specificity applies within one source.

Canonical external names are `textus.*`. `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` remain decode-only aliases until GCF-09, and canonical/alias
coexistence for one source and target is structural failure. Runtime history is
complete; diagnostic projection is newest 16 entries, source identity at most
256 characters with truncation count, and confidential values are always
redacted.

ArtScene may prepare after GCF-08 and the coordinated development contract; its
consumer migration and integration are GCF-09 and GCF-10. This avoids a Phase
12 planning cycle without treating ArtScene as operationally complete.

P55-GCF02-SEAM was approved on the same date: GCF-02 uses a generic,
target-parametrized, non-authoritative scenario request/report SPI with a
structured `notImplemented` outcome. CNCF supplies specialization only. The
SPI is an executable-contract seam; GCF-03 through GCF-07 retain all resolver,
catalog, projection, and fixed-user migration behavior.

The revised GCF-02 plan froze the generic public names
`ConfigurationBindingScenarioRequest[T]`, `ConfigurationBindingScenarioReport`,
`ConfigurationBindingScenarioReport.NotImplemented(scenarioId)`,
`ConfigurationBindingScenarioSpi[T]`, and
`ConfigurationBindingScenarioSpi.notImplemented[T]`. CNCF adds only
`CncfConfigurationTarget`, `CncfConfigurationBindingScenarioRequest`, its
`ConcreteTarget`, `ExternalDocument`, `Alias`, and `FixedUserIdentityChange`
request families, and `CncfConfigurationBindingScenarioSpi`. All requests route
to the same structured `NotImplemented` outcome; they are not an early source
decoder, resolver, catalog, trace projection, or fixed-user migration engine.

GCF-02 focused validation completed with three active generic routing examples
and three intentional behavior deferrals, plus four active CNCF routing examples
and four intentional behavior deferrals. Independent review found and corrected
one stale checklist-status sentence; focused re-review was clean. GCF-02 is
therefore complete as an executable-contract stage, while GCF-03 through GCF-07
remain responsible for replacing deferred reports with real behavior.

Phase 53 admission semantics and Phase 54 SystemNode pool lifecycle/Subsystem
datastore binding behavior remain unchanged.

P55-GCF03-EVIDENCE was approved on 2026-08-02: generic provenance construction
retains at most 16 evidence entries, caps each retained entry at 256 Unicode
code units, and records omitted-entry count. This is an independent
construction constraint, not an implicit reuse of the external history and
source-identity projection policy.
