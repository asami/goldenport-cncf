# Phase 55 - Typed Configuration Binding and Provenance Resolution

status=in-progress
planned_at=2026-07-30
replanned_at=2026-07-31
depends_on=[Phase 54](phase-54.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 55 Checklist](phase-55-checklist.md)
provisional_specification=[Configuration Binding Provisional Specification](../notes/phase-55-configuration-binding-provisional-specification.md)
normative_design=[Phase 55 Configuration Binding](../design/configuration-binding.md)
normative_specification=[Configuration Resolution](../spec/config-resolution.md)
migration_guide=[Phase 55 Configuration Binding Migration Guide](../notes/phase-55-configuration-binding-migration-guide.md)
gcf10a_journal=[GCF-10A Normative Configuration Binding Promotion](../journal/2026/08/2026-08-04-phase-55-gcf10a-normative-configuration-binding-promotion.md)
decision_journal=[Configuration Binding-Centered Replanning](../journal/2026/07/2026-07-31-phase-55-configuration-binding-centered-replanning.md)

## Purpose

Replace parallel String-keyed value and trace authorities with one typed
configuration-binding model in which each effective value carries its
parameter identity, semantic target, provenance, and direct override history.

Phase 55 decodes physical configuration sources into immutable binding
candidates, resolves them deterministically for a selected context, and
produces one effective binding per canonical parameter. Configuration trace
and `explain-config` output are derived projections of those bindings rather
than independently maintained state.

## Dependency

Phase 55 begins after Phase 54 closes.

Phase 53 remains responsible for ComponentStyle, StandaloneUserProfile
admission, ExecutionContext boundaries, and ArtScene adoption. Phase 55 owns
the owner-deferred effective configuration behavior: field binding and
precedence, detailed provenance, explicit override representation, and
ambient environment conversion. It preserves Phase 53 admission semantics
without reopening them.

Phase 55 also receives Phase 53's unimplemented fixed-user intake: stable
identity/change diagnosis, explicit-migration-or-isolation behavior,
fixed-user formatting, and secret-safe derived diagnostics. These requirements
enter through the binding contract and never silently migrate or reuse data.

## Selected Planning Direction

The provisional center of the phase is:

```text
physical sources
  -> ConfigurationBindingCandidates
  -> deterministic binding resolution
  -> ConfigurationBindingCollection
  -> typed lookup and derived trace
```

The planned model has these properties:

- one typed parameter definition couples a canonical parameter identity to its
  admitted value type and codec;
- one `ConfigurationBinding` binds that parameter to a typed value for a
  semantic target and carries its own provenance;
- an effective binding records the immediately overridden effective binding,
  forming an immutable override chain;
- raw candidates and resolved bindings are different collection types;
- a resolved collection contains at most one effective binding for each
  canonical parameter identity;
- `Global`, `ComponentClass(componentId)`,
  `SubsystemInstance(SubsystemInstanceId)`, and
  `ComponentInstance(SubsystemInstanceId, ComponentInstanceId)` are the
  initial semantic targets;
- an implicit Component Subsystem materializes as an ordinary stable
  `SubsystemInstance` target;
- an unqualified external spelling receives the target selected by its
  document location; `Unqualified` is not a separate semantic target;
- `~/.textus/config.yaml` may contain the complete configuration, while the
  same content may be split under the reserved
  `~/.textus/components/*` and `~/.textus/subsystems/*` trees;
- consolidated and split documents normalize into the same target model and
  do not create parallel authorities;
- source precedence dominates across physical sources, while a matching
  more-specific target overrides a less-specific target only within the same
  source;
- typed lookup uses the parameter definition, so Component and Subsystem
  consumers do not need to know which target supplied the winning value;
- configuration trace, history, and diagnostics are derived from the winning
  binding and its override chain;
- String representations remain only at external file, environment,
  argument, launcher, and diagnostic boundaries; and
- no final implementation retains parallel authoritative String-keyed and
  typed-keyed configuration models.

The GCF-01 name, ownership, namespace, alias, and diagnostic decisions are
frozen in the [GCF-01 inventory and binding-contract record](../notes/phase-55-gcf01-inventory-and-binding-contract-freeze.md).

## Planned Core Objects

The provisional object roles are:

- `CanonicalParameterId`: validated semantic identity;
- `ConfigurationParameter[A]`: identity, admitted value type, and codec;
- `SubsystemInstanceId`: validated Subsystem identity plus instance name;
- `CncfConfigurationTarget`: CNCF-owned initially Global, ComponentClass,
  SubsystemInstance, or a ComponentInstance qualified by both its containing
  SubsystemInstanceId and ComponentInstanceId;
- `ConfigurationProvenance`: physical source, layer, input spelling/path,
  ordering position, and bounded evidence;
- `ConfigurationBinding[A, T]`: parameter, target, typed value, provenance, and
  direct overridden binding;
- `ConfigurationBindingCandidates[T]`: unresolved bindings from every admitted
  source; and
- `ConfigurationBindingCollection[T]`: resolved effective binding indexed by
  canonical parameter identity.

`ConfigurationTrace` remains an external and diagnostic projection. It does
not participate in value selection and is not updated separately from the
effective binding collection.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| GCF-01 | Inventory and binding-contract freeze | Existing constructors, lookups, source loading, aliases, trace consumers, repositories, names, compatibility, redaction, and lifecycle invariants are fixed. | done |
| GCF-02 | Failing-first typed binding contract | Generic target-parametrized scenario SPI and CNCF specialization register parameter/value typing, provenance, candidate/resolved separation, override-chain, conflict, lookup, alias, fixed-user, and redaction behavior; only structured `NotImplemented` is implemented. | done |
| GCF-03 | Typed parameter and binding core | Generic parameter, provenance, candidate/effective binding, collection, and typed lookup contracts plus CNCF's four validated targets are implemented; source/resolution/catalog behavior remains deferred. | done |
| GCF-04 | Source decoding and candidate construction | Each admitted source loads once and produces validated immutable candidates with canonical identity and complete provenance. | done |
| GCF-05 | Deterministic resolution and override history | Global, ComponentClass, SubsystemInstance, and fully qualified ComponentInstance target selection plus source precedence produce one winner per parameter with an exact override chain. | done |
| GCF-06 | Resolved collection and trace projection | Generic typed lookup and sanitized trace projection are derived from one effective binding authority. | done |
| GCF-07 | Textus/CNCF parameter catalog adoption | Closed `textus.*` and `cncf.*` contracts, Phase 53 layering, StandaloneUserProfile, Web execution policy, and runtime resolution use the generic binding model. GCF-07A–I establish catalog witnesses, a single-load runtime source projection, final fixed-standalone collection admission, fixed-only identity/formatting projection, typed Web policy/assembly-default adoption, and value-only runtime projections. | done |
| GCF-08 | External codecs and boundary adapters | GCF-08A–L complete canonical/environment/argv codecs, argv and environment partitioning, raw-preserving consolidated/split-file admission, opaque launcher envelope transport, and serialized trace diagnostics. | done |
| GCF-09 | Admitted consumer migration | Every frozen direct consumer migrates coherently; GCF-09A–R are accepted and the Step validation/commit evidence is recorded. | DONE |
| GCF-10 | Regression, review, and normative closure | Full validation, independent review, resource-safe diagnostics, and release evidence remain required for closure. | in progress |
| GCF-10A | Normative documentation promotion | Promote verified implementation behavior into the generic/CNCF design, specification, developer guidance, migration guide, phase ledger, checklist, and journal; add focused executable evidence for empty selected-source-set handling; and repair runtime projection so a genuinely empty admitted source set becomes canonical empty candidates without weakening decoder or supplemental-source validation. | DONE |

## Resolution Contract

For one selected Component instance, its class, containing Subsystem instance,
and one canonical parameter:

1. discover and load each admitted physical source once;
2. decode, validate, canonicalize aliases, and create typed candidates;
3. assign source rank and ordinal from runtime-owned source order;
4. reject duplicate or conflicting bindings inside one physical source and
   same-target collisions across equivalent documents in one layer;
5. within one source, apply matching target specificity from Global through
   ComponentClass and SubsystemInstance to the exact ComponentInstance;
6. fold per-source winners from lower to higher source precedence;
7. record the previous effective binding as the new winner's direct
   `overridden` binding; and
8. publish one effective binding indexed by canonical parameter identity.

The override chain contains only eligible bindings that successively held the
effective position. Rejected, invalid, or context-ineligible candidates remain
diagnostic candidate evidence and are not represented as overridden winners.

## Ownership

- `simplemodeling-lib` owns the generic typed binding, candidate, resolution,
  provenance, catalog mechanism, codec abstractions, and derived trace model.
- `cloud-native-component-framework` owns Textus/CNCF parameter definitions,
  namespace policy, source admission, Phase 53 profile semantics, and runtime
  orchestration.
- launchers own exact external forwarding and transport boundaries; they do
  not interpret parameter semantics.
- each admitted direct consumer owns migration to typed lookup.

The final repository set is frozen by GCF-01. Inspection does not itself admit
a repository for mutation.

## Compatibility and Migration Boundary

- External configuration files, typed documents, environment variables,
  arguments, and serialized diagnostics remain String boundaries.
- Internal typed parameter and binding APIs become the sole authority.
- A temporary String adapter may sequence compilation but must be explicitly
  non-authoritative and removed or reduced to a boundary codec before closure.
- Canonical external names are `textus.*`. `textus.runtime.*`, `cncf.*`, and
  `cncf.runtime.*` are decode-only aliases, rejected when co-present with the
  canonical spelling for one `(collisionDomain, parameter, target)` identity;
  GCF-09 removes admitted internal alias consumers.
- The same semantic is never published independently as both `textus.foo` and
  `cncf.foo`.

## Acceptance

- A parameter definition cannot produce a binding with an incompatible value.
- Every binding has valid provenance and a validated semantic target.
- An overridden binding has the same canonical parameter and admitted value
  type as its winner.
- Override chains are acyclic, deterministic, and complete.
- Candidate collections admit multiple sources and targets; resolved
  collections admit one winner per canonical parameter.
- One physical source is loaded at most once per resolution snapshot.
- Same-source target specificity and cross-source precedence follow the frozen
  resolution order.
- Separate SubsystemInstance and ComponentInstance resolutions from one
  immutable candidate collection do not contaminate one another.
- Consolidated `~/.textus/config.yaml` and split
  `~/.textus/components/*` / `~/.textus/subsystems/*` inputs project to the
  same canonical targets.
- Split ComponentClass configuration uses
  `components/<component-id>/config.yaml`.
- Split SubsystemInstance configuration uses
  `subsystems/<subsystem-id>/instances/<instance>/config.yaml`.
- Split ComponentInstance configuration is nested below its containing
  SubsystemInstance and explicitly names both Component ID and instance.
- Initial canonical documents and paths keep the `default` instance segment
  explicit.
- A duplicate canonical parameter/target in both physical forms within one
  admitted layer is rejected instead of becoming an invisible override.
- Typed lookup does not require the caller to know the winning target.
- Trace key, value, provenance, and history cannot diverge from the effective
  binding because trace is derived from it.
- Aliases normalize before binding construction and retain their original
  spelling in provenance.
- Canonical and environment binding codecs are reversible and collision-free.
- Confidential values and overridden confidential history remain redacted in
  diagnostics.
- No Component receives raw source paths, layer names, candidate collections,
  or arbitrary configuration access.
- All admitted repositories compile and pass applicable focused and full
  tests without a second internal String-key authority.

## Documentation Lifecycle

The order is:

1. journal records the discussion and decisions;
2. this phase, checklist, and provisional notes freeze the implementation plan;
3. failing-first Executable Specifications fix observable behavior;
4. implementation and focused validation establish behavior;
5. GCF-10A promotes the verified contract to normative `docs/design` and
   `docs/spec` plus developer guidance;
6. full Phase-wide validation proves every admitted repository; and
7. independent review and release evidence close the phase.

GCF-10A promotes documentation and focused executable evidence. It does not
claim Phase-wide suites, independent review, release, or phase closure.

## Out of Scope

- Reopening Phase 53 ComponentStyle, ExecutionContext, StandaloneUserProfile, or
  ArtScene-specific semantics.
- Metadata Factory ComponentStyle contribution.
- User, WebApplication, or arbitrary multidimensional qualifier taxonomies
  without a separately proven use case.
- A heterogeneous general-purpose object graph merge language.
- Permanent dual String-keyed and typed-keyed authorities.
- Entity or collection identity.
- SystemNode datastore pool lifecycle and Subsystem datastore bindings owned by
  Phase 54.
- Speculative migration of repositories not admitted by GCF-01.

## Current Status

Phase 55 is in progress. GCF-01 through GCF-07 are complete: the generic/CNCF
ownership split, typed parameter/binding core, source/candidate construction,
deterministic resolution, resolved collection/trace projection, and the CNCF
catalog/runtime adoption have passed their recorded independent reviews and
focused validation. GCF-07's final runtime projection leaves Components and
ExecutionContext without binding, candidate, target, source, layer, or trace
authority; its former `ResolvedConfiguration` consumers are GCF-09 migration
work, not open GCF-07 work.

GCF-08 is complete. GCF-08A–L completed the canonical binding-string,
environment-name, argv-envelope/admission, candidate bridge, runtime argv and
environment boundaries, raw-preserving consolidated and canonical Textus
split-file admission, opaque envelope transport through both launchers, and
the serialized diagnostic boundary. Generic file-source snapshots retain YAML
mapping-member multiplicity and order, and CNCF rejects duplicate canonical
bindings from that retained document without rereading the physical source.

GCF-09 is DONE. GCF-09A–R are accepted and the Step validation/commit evidence
is recorded in the checklist and the GCF-09 commit is
`f870be9498cc14226e377091e5192aff3a2fec0a` (ArtScene consumer commit
`947fde3824c9cf3d10944a97936e0168ac948234`).

GCF-10A is accepted. It updates the generic and CNCF normative
design/specification, developer index/guide, migration guidance, and
historical/progress ledgers to match
verified behavior, adds one focused executable specification scenario (E12) for
an empty selected-source set, and includes one bounded runtime projection
repair: after supplemental-source validation, a genuinely empty admitted source
set maps to canonical empty candidates. Decoder validation for physical batches
and supplemental-source validation remain unchanged. Independent review
convergence is clean; generic focused validation passed 40/40 with three
intentional pending scenarios, CNCF configuration validation passed 180/180,
and focused runtime/consumer validation passed 431/431.

GCF-10 remains in progress. The complete Step accumulator review is clean after
three bounded review-fix passes and focused re-review; Step commit validation,
full Phase-wide validation, release evidence, and Phase 55 closure remain
pending.

Earlier GCF-09A–F migrated repository bootstrap,
runtime Web policy and descriptor roots, service-container configuration,
component-development Web paths, and runtime repository/bootstrap projection
to admitted typed values. GCF-09H adds the canonical,
SubsystemInstance-only `textus.system-node.shutdown.drain-timeout-millis`
binding: it is a positive bounded millisecond `Long` with no aliases, resolves
to the 30000-ms default only when absent, and is selected before the runtime
constructs its SystemNode. The remainder of this paragraph records the
historical GCF-09H expectation that direct consumers, temporary adapters,
documentation updates, and closure evidence would follow. GCF-09 is now DONE;
GCF-10A is accepted, and GCF-10 is IN PROGRESS with the complete Step
accumulator review clean. Step commit validation, full validation, release
evidence, and Phase closure are still pending.

GCF-09I registers `textus.import.data.file` and
`textus.import.entity.file` as optional, String-typed,
SubsystemInstance-only values. Their three established compatibility spellings
are decode-only aliases. The final admitted collection projects only normalized
optional source strings into `StartupImportConfiguration`; the importer never
reads `ResolvedConfiguration`, bindings, candidates, aliases, or provenance.
For entity seed import, it receives only the entity-collection resolver
capability, not the enclosing `Subsystem`.
An admitted blank or missing value leaves the existing `data.d` / `entity.d`
fallback intact, while missing runtime admission fails structurally. This slice
does not change `application-mode`: it remains presentation-only vocabulary;
its removal is separately planned compatibility and CML work.

GCF-09J registers `textus.collaborator.repositories` as an optional,
comma-separated `Vector[String]` value admitted only at Global scope. The
established `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings are
decode-only aliases. Outer path whitespace is trimmed, interior whitespace is
preserved, and only commas split values. `RepositoryBootstrapPolicy` resolves
relative values against its bootstrap directory, normalizes and deduplicates
them, and supplies only those paths to collaborator discovery. Missing or
blank values select `<bootstrap-directory>/collaborator.d`; an explicit missing
directory remains an empty discovery set and never revives that fallback. The
raw `ResolvedConfiguration` collaborator overload remains compatibility-only,
outside the runtime bootstrap path. `application-mode` remains presentation-only
vocabulary and its removal remains separate compatibility/CML work.

GCF-09K retires the isolated deprecated CNCF `config.model`, `config.source`,
`config.trace`, resolver, merge, and resolved-value stack together with its
self-contained tests. The live runtime uses the generic configuration binding,
candidate, resolution, and trace stack instead. This deletion does not alter
canonical runtime admission, compatibility contracts, `application-mode`, or
the Phase 54 ownership topology.

GCF-09L implements the runtime operation and Web-authorization policy
projection. It admits the existing operation-mode, anonymous-admin,
demo-assist, production-admin, and three production-admin-role values at
`SubsystemInstance` scope, then project one value-only policy through
`Subsystem` atomically with final binding admission to its operation
authorization and HTTP consumers. The canonical
keys retain only their established `textus.runtime.*`, `cncf.*`, and
`cncf.runtime.*` decode-only aliases; malformed input must fail during
admission, while absence selects the current defaults without reviving raw
configuration. The slice does not remove `application-mode`, alter CML,
expand the generic SPI, alter Phase 54 ownership, or migrate the unrelated
RuntimeConfig families. Independent review required public default HTTP factory
admission; REVIEW_FIX admits the empty final collection before it returns an
engine, server, or loopback runtime. Focused re-review required the public
factory regression to leave the Phase 53 dispatch spec; the dedicated GCF-09L
executable specification is now in place and second focused re-review is
clean.

GCF-09M implements the runtime execution-profile configuration projection.
It admits the complete existing execution determinism family at
`SubsystemInstance` scope—profile, clock/time, random, id, scheduler,
ordering, locale/timezone/charset, line separator, math context, i18n, and
environment controls—and project one value-only configuration into execution
profile resolution before `GlobalRuntimeContext` construction. The selected
descriptor identity, candidate set, and resolved collection must be produced
once and carried from this pre-Subsystem boundary through final admission;
there must be no Global duplicate or raw execution-family fallback.
Established `textus.runtime.*`, `cncf.*`, and `cncf.runtime.*` spellings remain
decode-only aliases. `application-mode`, CML, Phase 54 ownership, generic SPI,
launchers, and unrelated RuntimeConfig families remain outside this slice.
The pre-Subsystem result now constructs the typed execution profile before
`RuntimeConfig` and `GlobalRuntimeContext`, then carries its candidates into
final fixed-user admission. Catalog/projection/bootstrap acceptance validation
is green. Independent review found typed time/start validation and typed
test-operation-mode activation gaps; REVIEW_FIX now restores structured
time/start failures and enables the already-authorized controlled profile for
typed test operation mode. Focused re-review remains required before the slice
is accepted. The first focused re-review found only a residual parameter
indentation P3 and missing manual-without-start regression. REVIEW_FIX corrects
both.
Final focused re-review is clean, so GCF-09M is accepted in the current
GCF-09 Step accumulator.

GCF-09N implements the smallest remaining direct-consumer slice: it admits
`textus.force-exit` and `textus.no-exit` as Global Boolean values, then
projects them with direct CLI-flag overrides into one value-only runtime
process-exit policy. One resolved Global collection feeds both repository
bootstrap and process-exit policy; the process-exit path no longer reads raw
`ResolvedConfiguration`. Existing force-exit precedence, `noExit` failure
behavior, and external `--force-exit`/`--no-exit` adapter controls are
preserved. Review-fix validation is green: wrong-target split files and
malformed/colliding Global values now remain structured bootstrap failures,
and auto-archive enrichment retains the already resolved Global policy rather
than resolving it again; the auto-archive executable specification now counts
that Global-policy projection and requires exactly one occurrence. A fresh
focused re-review remains the acceptance gate. `application-mode` and CML
deletion remain separate compatibility work;
unrelated front controls and RuntimeConfig families are excluded.
