# Phase 55 GCF-01 Inventory and Binding-Contract Freeze

Date: 2026-08-02

Status: frozen implementation contract

This implementation-facing record completes GCF-01. It is not a normative
design/specification and does not alter Phase 53 or Phase 54 behavior.

## Admitted repository and ownership set

| Repository | Frozen responsibility |
| --- | --- |
| `simplemodeling-lib` | generic typed binding core, source snapshot, resolution, provenance, and derived projection |
| `cloud-native-component-framework` | Textus/CNCF parameter catalog, four concrete targets, aliases, source admission, Phase 53 profile integration, runtime orchestration |
| `cncf-launcher` | external codec/transport only |
| `textus-launcher` | external codec/transport only |
| `textus-art-scene` | three direct consumer keys and typed-consumer migration |

No reverse dependency from `simplemodeling-lib` to CNCF/Textus is admitted.
No other repository is in scope for Phase 55 mutation without a later recorded
admission.

## Current authorities and migration boundary

The generic library has a String-keyed `Configuration` map, a separate
`ResolvedConfiguration.trace`, `ConfigurationResolver`, `MergePolicy`, source
loaders, and a trace printer. `MergePolicy` can load a source a second time;
GCF-02 must prove this failing first and GCF-04 removes it.

CNCF retains current `org.goldenport.configuration.*` users, deprecated
`org.goldenport.cncf.config.*` surfaces, `RuntimeConfig`, direct configuration
access, resolver/merge/trace wrappers, configuration-file/system-property/
environment/argument intake, and Phase 53 component initialization layers:
packaged default < assembly default < subsystem instance < runtime < test
overlay. Components must migrate from direct String/map access to the resolved
typed collection; the current layering is not reopened.

Launchers remain raw transport. ArtScene's admitted direct raw consumers are
`textus.artscene.application.mode`,
`textus.component.art-scene.datastores.application.policy`, and
`textus.execution.locale`.

## Frozen public contract

Generic names are `CanonicalParameterId`, `ConfigurationParameter[A]`,
`ConfigurationValueCodec[A]`, `ConfigurationProvenance`,
`ConfigurationBindingCandidate[A, T]`, `ConfigurationBindingCandidates[T]`,
`ConfigurationBinding[A, T]`, and `ConfigurationBindingCollection[T]`.
They are target-parametrized; they do not define a Textus/CNCF target.

CNCF owns `SubsystemInstanceId`, `ComponentInstanceId`, exactly four initial
target cases, and their document mappings: `Global`, `ComponentClass`,
`SubsystemInstance`, and fully-qualified
`ComponentInstance(containingSubsystemInstance, componentInstance)`. An absent
qualifier is document-location syntax, never a fifth target. Explicit
`instances/default` remains canonical.

An immutable candidate has no override member. An effective binding has only a
direct `overridden` link to the preceding effective binding for the same typed
parameter. The generic collection is the sole effective-value authority;
diagnostic trace is derived.

Every physical source loads once into an immutable snapshot. Runtime assigns
rank and stable ordinal. Same-source specificity is Global, ComponentClass,
SubsystemInstance, ComponentInstance; source precedence dominates across
sources. Duplicate canonical parameter/target entries in one source or across
the consolidated and split form of one layer fail structurally.

## Namespace, diagnostics, and Phase 53 intake

Canonical external parameter names are `textus.*`. `textus.runtime.*`,
`cncf.*`, and `cncf.runtime.*` are decode-only aliases. Canonical and alias
forms for the same parameter/target in one source are rejected; aliases retain
their input spelling in provenance and are removed by GCF-09.

The runtime holds the complete immutable override chain. External diagnostics
show at most the newest 16 entries, cap source identity text at 256 characters,
record any truncated count, and redact confidential values in both winner and
history. Phase 53 fixed-user identity changes must diagnose and require an
explicit migration or isolation; they may not silently reuse data. Phase 53
admission, layering, ExecutionContext, and multi-user boundaries remain fixed.

## GCF-02 failing-first acceptance

Executable specifications must fail before implementation for typed
parameter/value coupling; target/identity validation; duplicate rejection;
one-load snapshot; source and target ordering; direct acyclic override chain;
candidate/resolved separation; typed lookup without target knowledge; trace
derivation; confidential redaction and 16/256 bounded projection; aliases; and
fixed-user migration-or-isolation diagnosis.

## P55-GCF02-SEAM Decision

Decision date: 2026-08-02

Human decision: use a generic SPI.

GCF-02 may add a deliberately non-authoritative, target-parametrized generic
scenario request/report SPI in `simplemodeling-lib`, with CNCF specialization
for concrete target, external-document, alias, and fixed-user scenarios. Its
only GCF-02 outcome is a structured `notImplemented` result. It is an
executable-contract seam, not a second resolver, binding store, catalog, trace
authority, source decoder, or migration implementation. GCF-03 through GCF-07
own the behavior that later satisfies these scenarios.

The GCF-02 PLAN freezes the generic public seam in
`org.goldenport.configuration` as
`ConfigurationBindingScenarioRequest[T]`,
`ConfigurationBindingScenarioReport`,
`ConfigurationBindingScenarioReport.NotImplemented(scenarioId)`,
`ConfigurationBindingScenarioSpi[T]`, and
`ConfigurationBindingScenarioSpi.notImplemented[T]`. A request exposes only
its attributable `scenarioId`; the generic SPI returns the matching structured
`NotImplemented` report and has no configuration-resolution authority.

The CNCF public specialization in `org.goldenport.cncf.config` is
`CncfConfigurationTarget`,
`CncfConfigurationBindingScenarioRequest`, its four exact request families
`ConcreteTarget`, `ExternalDocument`, `Alias`, and
`FixedUserIdentityChange`, plus `CncfConfigurationBindingScenarioSpi`. The
specialization delegates to the generic structured outcome only. It does not
decode a document, canonicalize an alias, validate an identity, resolve a
binding, decide migration, or introduce the four semantic target cases before
GCF-03 through GCF-07.

## P55-GCF03-EVIDENCE Decision

Decision date: 2026-08-02

Human decision: adopt the recommended provenance-evidence bound.

`ConfigurationProvenance` retains at most 16 evidence entries. Each retained
entry is at most 256 Unicode code units, and construction records the number
of entries omitted by the bound. These are construction-time provenance
constraints, distinct from the Phase 55 external history-projection limits;
they do not define trace rendering or source loading behavior.

## Exclusions and coordination

Phase 54 SystemNode-owned pool lifecycle and Subsystem datastore binding/lease
behavior are excluded. GCF-01 does not change Phase 55 deferrals.

ArtScene Phase 12 may start its preparatory inventory after GCF-08 and the
coordinated development contract are available. Its migration and integration
remain GCF-09 and GCF-10 respectively; this removes the planning cycle without
claiming operational closure.
