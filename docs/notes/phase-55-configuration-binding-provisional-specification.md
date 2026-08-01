# Phase 55 Configuration Binding Provisional Specification

Date: 2026-07-31

Status: provisional planning contract

This note records the implementation-facing Phase 55 contract before
failing-first specifications and implementation establish normative behavior.
Names remain provisional until GCF-01 closes. This note is not `docs/spec` and
does not override verified design or implementation.

## Objective

Represent each effective configuration value as one typed binding that carries
its semantic parameter, selected target, provenance, and direct override
history. Keep one resolved binding authority and derive trace from it.

## Processing Model

```text
ConfigurationSource
  -> decode and validate once
  -> ConfigurationBindingCandidates
  -> resolve for selected runtime / Component context
  -> ConfigurationBindingCollection
  -> typed lookup / sanitized trace
```

## Provisional Types

```scala
trait ConfigurationParameter[A] {
  def id: CanonicalParameterId
  def codec: ConfigurationValueCodec[A]
}

enum ConfigurationTarget {
  case Global
  case ComponentClass(id: ComponentId)
  case SubsystemInstance(id: SubsystemInstanceId)
  case ComponentInstance(
    subsystem: SubsystemInstanceId,
    component: ComponentInstanceId
  )
}

final case class SubsystemInstanceId(
  subsystem: ConfigurationSubsystemId,
  instance: ConfigurationInstanceName
)

final class ConfigurationBinding[A] private (
  val parameter: ConfigurationParameter[A],
  val target: ConfigurationTarget,
  val value: A,
  val provenance: ConfigurationProvenance,
  val overridden: Option[ConfigurationBinding[A]]
)

final class ConfigurationBindingCandidates private (
  val bindings: Vector[ConfigurationBinding[?]]
)

final class ConfigurationBindingCollection private (
  val bindings: Map[CanonicalParameterId, ConfigurationBinding[?]]
)
```

The candidate representation may use a separate source-binding type if GCF-01
finds that an unresolved binding must not expose an `overridden` member. The
required semantic distinction is fixed even if the final names or class split
changes.

## Phase 53 Fixed-User Intake

Phase 55 receives stable fixed-user identity change diagnosis,
explicit-migration-or-isolation behavior, fixed-user formatting, and
secret-safe derived diagnostics. A fixed UserId is a typed profile value, not a
new `User` configuration qualifier. This intake preserves Phase 53 HOME
admission/multi-user exclusion and never permits silent data reuse or migration.

## Binding Invariants

- A parameter definition owns one canonical identity and one admitted value
  codec/type.
- A binding's value must conform to its parameter.
- A binding has one validated semantic target.
- A binding always has provenance.
- An effective binding may reference only the directly overridden effective
  binding.
- Winner and overridden binding have the same canonical parameter identity and
  admitted value type.
- Override chains are immutable and acyclic.
- Invalid, rejected, duplicate, or context-ineligible candidates do not become
  overridden winners.
- Confidential values may exist inside authorized runtime bindings but all
  trace and diagnostic projections redact them.

## Candidate and Resolved Collection Invariants

- Candidates may contain several sources and targets for one parameter.
- A resolved collection contains at most one effective binding per canonical
  parameter identity.
- Typed lookup accepts `ConfigurationParameter[A]` and returns only a binding
  or value of `A`.
- Callers do not supply or reconstruct the winning target to perform lookup.
- Different selected Subsystem instances and Component instances resolve
  independently from one immutable candidate collection.
- The resolved collection, not a trace map, is the effective-value authority.

## Provenance

Provenance must explain at least:

- origin kind;
- Textus/CNCF or other admitted layer;
- physical source identity;
- original external key or typed-document field path;
- Global, ComponentClass, SubsystemInstance, or ComponentInstance target;
- runtime-assigned source rank and stable ordinal;
- alias spelling before canonicalization;
- bounded contract/default evidence; and
- confidentiality/redaction classification.

Source order is runtime-owned. A physical source cannot raise its rank or
choose an ordinal.

## Resolution

For one parameter and a selected Component instance with its Component class
and containing Subsystem instance:

1. admit sources and load each exactly once;
2. canonicalize aliases and decode typed bindings;
3. reject malformed definitions and conflicting definitions inside one source
   or for the same target across equivalent documents in one layer;
4. retain only Global and targets matching the selected Component class,
   Subsystem instance, and Component instance;
5. inside one source, apply target specificity from Global through
   ComponentClass and SubsystemInstance to the exact ComponentInstance;
6. fold source winners from low to high precedence;
7. attach the previous effective winner as the new winner's direct
   `overridden` binding; and
8. publish the final winner under its canonical parameter identity.

Source precedence dominates semantic specificity across sources. A
higher-precedence Global binding may override a lower-precedence
ComponentClass, SubsystemInstance, or ComponentInstance binding. Target
specificity applies only within the same source.

## External Boundaries

Files, typed documents, environment variables, arguments, launchers, and
serialized diagnostics remain String boundaries. They use explicit reversible
codecs to enter or leave the typed model.

An absent external qualifier does not intrinsically mean Global. The owning
document location supplies a target-selection context, and binding
construction must materialize one concrete `ConfigurationTarget`.
`Unqualified` is syntax requiring contextual target inference, not a semantic
target.

Phase 55 admits two equivalent physical organization styles for normal Textus
HOME configuration:

1. all configuration may be written in the consolidated
   `~/.textus/config.yaml`; and
2. the same target-oriented configuration may be split into the reserved
   `~/.textus/components/*` and `~/.textus/subsystems/*` trees.

The consolidated document mirrors this target structure:

```yaml
global:
  config: {}
components:
  <component-id>:
    config: {}
subsystems:
  <subsystem-id>:
    instances:
      <subsystem-instance>:
        config: {}
        components:
          <component-id>:
            instances:
              <component-instance>:
                config: {}
```

The canonical split paths are:

```text
~/.textus/components/<component-id>/config.yaml
~/.textus/subsystems/<subsystem-id>/instances/<subsystem-instance>/config.yaml
~/.textus/subsystems/<subsystem-id>/instances/<subsystem-instance>/
  components/<component-id>/instances/<component-instance>/config.yaml
```

The top-level `components` tree supplies ComponentClass configuration. A
`components` tree nested under one Subsystem instance supplies
ComponentInstance configuration. The full ComponentInstance target therefore
contains both `SubsystemInstanceId` and the existing `ComponentInstanceId`.

An explicit Subsystem and an implicit Component Subsystem use the same
SubsystemInstance and nested ComponentInstance model. The implicit form is not
a separate target kind: CNCF first projects its stable Subsystem identity and
instance name, then addresses it through
`ConfigurationTarget.SubsystemInstance`. When no named instance is supplied,
the runtime identity is `default`.

The initial canonical file syntax keeps `instances/default` explicit. A
shorter default-instance spelling may be considered later as syntax sugar, but
it must normalize to the same instance identity and cannot form another
authority.

Both physical forms decode into the same canonical
`(parameter, ConfigurationTarget)` candidates. File layout does not create a
second value authority. If the consolidated and split forms define the same
canonical parameter for the same target in the same admitted layer, the input
is a structural duplicate/conflict rather than an implicit override. Explicit
overrides use the admitted source/layer precedence and remain visible in the
binding chain.

String convenience APIs may exist temporarily for migration or permanently as
explicit codecs at external boundaries. They must not form a second internal
configuration authority.

## Trace

Trace is derived from:

```text
effective binding
  + effective provenance
  + overridden binding chain
```

It is not consulted to choose a value. No independent update path may allow
trace key, final value, provenance, or history to diverge from the effective
binding.

## Ownership

- `simplemodeling-lib`: generic identities, typed parameters, bindings,
  candidates, resolver, provenance, generic catalog mechanism, codec
  abstractions, and trace projection.
- CNCF/Textus: parameter definitions, closed namespace policy, aliases,
  source admission, typed-document mapping, and runtime selection.
- launchers: exact external forwarding only.
- Components: typed resolved values and capabilities only.

## Deferred Generalization

The provisional contract does not admit:

- User or WebApplication semantic qualifiers;
- arbitrary multidimensional qualifier sets;
- a general heterogeneous object-graph merge language;
- permanent alias compatibility;
- Metadata Factory ComponentStyle contribution; or
- Component access to raw candidates, source paths, or trace authority.

## Decisions Remaining for GCF-01

- final public type names;
- whether unresolved and resolved bindings share one class;
- the exact heterogeneous typed-storage technique;
- final serialized field spellings and the exact validated identifier codecs
  for Component, Subsystem, and instance path segments;
- structured error/outcome types;
- bounded provenance and history serialization limits;
- alias removal and compatibility policy; and
- the exact admitted repository set.
