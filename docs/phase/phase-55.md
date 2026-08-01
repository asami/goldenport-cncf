# Phase 55 - Typed Configuration Binding and Provenance Resolution

status=planned
planned_at=2026-07-30
replanned_at=2026-07-31
depends_on=[Phase 54](phase-54.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 55 Checklist](phase-55-checklist.md)
provisional_specification=[Configuration Binding Provisional Specification](../notes/phase-55-configuration-binding-provisional-specification.md)
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

Exact public names remain provisional until GCF-01 freezes the contract.

## Planned Core Objects

The provisional object roles are:

- `CanonicalParameterId`: validated semantic identity;
- `ConfigurationParameter[A]`: identity, admitted value type, and codec;
- `SubsystemInstanceId`: validated Subsystem identity plus instance name;
- `ConfigurationTarget`: initially Global, ComponentClass,
  SubsystemInstance, or a ComponentInstance qualified by both its containing
  SubsystemInstanceId and ComponentInstanceId;
- `ConfigurationProvenance`: physical source, layer, input spelling/path,
  ordering position, and bounded evidence;
- `ConfigurationBinding[A]`: parameter, target, typed value, provenance, and
  direct overridden binding;
- `ConfigurationBindingCandidates`: unresolved bindings from every admitted
  source; and
- `ConfigurationBindingCollection`: resolved effective binding indexed by
  canonical parameter identity.

`ConfigurationTrace` remains an external and diagnostic projection. It does
not participate in value selection and is not updated separately from the
effective binding collection.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| GCF-01 | Inventory and binding-contract freeze | Existing constructors, lookups, source loading, aliases, trace consumers, repositories, names, compatibility, redaction, and lifecycle invariants are fixed. | planned |
| GCF-02 | Failing-first typed binding contract | Executable specifications fix parameter/value typing, provenance, candidate/resolved separation, override-chain, conflict, lookup, and redaction behavior. | planned |
| GCF-03 | Typed parameter and binding core | Generic parameter, target, provenance, binding, factory, and typed lookup contracts are implemented without CNCF-specific semantics. | planned |
| GCF-04 | Source decoding and candidate construction | Each admitted source loads once and produces validated immutable candidates with canonical identity and complete provenance. | planned |
| GCF-05 | Deterministic resolution and override history | Global, ComponentClass, SubsystemInstance, and fully qualified ComponentInstance target selection plus source precedence produce one winner per parameter with an exact override chain. | planned |
| GCF-06 | Resolved collection and trace projection | Typed lookup and sanitized trace are derived from one effective binding authority. | planned |
| GCF-07 | Textus/CNCF parameter catalog adoption | Closed `textus.*` and `cncf.*` contracts, Phase 53 layering, StandaloneUserProfile, and runtime resolution use the generic binding model. | planned |
| GCF-08 | External codecs and boundary adapters | File, environment, argument, launcher, and diagnostic String forms round-trip through explicit codecs without becoming an internal authority. | planned |
| GCF-09 | Admitted consumer migration | Every frozen direct consumer migrates coherently; temporary internal String adapters are removed before closure. | planned |
| GCF-10 | Regression, review, and normative closure | Full validation, independent review, resource-safe diagnostics, design/spec promotion, and release evidence close the phase. | planned |

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
- Permanent aliases are not assumed. GCF-01 inventories current aliases and
  either removes them or records a bounded owner-defined migration condition.
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
4. implementation and review establish actual behavior;
5. full validation proves every admitted repository; and
6. verified behavior is promoted to normative `docs/design` and `docs/spec`.

Normative design and specification are intentionally written after
implementation and validation.

## Out of Scope

- Reopening Phase 53 ComponentStyle, ExecutionContext, StandaloneUserProfile, or
  ArtScene-specific semantics.
- Metadata Factory ComponentStyle contribution.
- User, WebApplication, or arbitrary multidimensional qualifier taxonomies
  without a separately proven use case.
- A heterogeneous general-purpose object graph merge language.
- Permanent dual String-keyed and typed-keyed authorities.
- Entity or collection identity.
- Subsystem datastore pool lifecycle owned by Phase 54.
- Speculative migration of repositories not admitted by GCF-01.

## Current Status

Phase 55 is planned. The ConfigurationBinding-centered direction is recorded;
GCF-01 has not started and no implementation repository set is frozen.
