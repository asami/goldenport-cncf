# Phase 55 ConfigurationBinding-Centered Replanning

Date: 2026-07-31

Status: planning decision record

## Context

Phase 53 identified generic configuration-framework candidates while fixing
ComponentStyle, StandaloneUserProfile, Textus/CNCF layering, and provenance. Those
candidates were moved to the Phase 55 scheduling frame so Phase 53 would not
grow into an unreviewed generic framework.

The first Phase 55 sketch separated:

- canonical parameter identity;
- scoped binding identity;
- candidate sets;
- resolved value maps; and
- trace maps.

It also explored `Global`, `Unqualified`, Subsystem, possible future
WebApplication qualification, reversible binding codecs, namespace catalogs,
and String-key migration.

## Problem Found During Reconsideration

Keeping resolved values and trace as separate authoritative maps permits
structural divergence:

- an effective value may lack matching trace;
- trace may carry a different key or final value;
- override history may not end at the actual winner; and
- typed and String indexes may evolve into parallel authorities.

Indexing the final configuration by a target-qualified binding key would also
require a consumer to know which target supplied the winning value. That
conflicts with the Phase 53 direction that Component code receives
resolved capabilities and values without branching on operation form or
configuration target.

`Global` and `Unqualified` were also found to describe different axes.
`Global` is a semantic target; `Unqualified` is only an external spelling
without an explicit qualifier.

## User-Proposed Center

The selected planning center was proposed as:

> Introduce a ConfigurationBinding object that manages a key, typed value,
> provenance, and the ConfigurationBinding it overrides. A
> ConfigurationBindingCollection manages the set of ConfigurationBindings.

This proposal makes the effective value and its explanation one immutable
unit instead of synchronizing two maps.

## Consolidated Direction

Phase 55 is replanned around these distinctions:

1. `ConfigurationParameter[A]` defines canonical identity and admitted value
   type.
2. `ConfigurationBinding[A]` binds that parameter to a typed value for
   Global, ComponentClass, SubsystemInstance, or a ComponentInstance qualified
   by its containing SubsystemInstance and carries provenance.
3. An effective binding records its directly overridden effective binding.
4. An unresolved candidate collection retains all admitted source/target
   bindings.
5. A resolved collection contains one effective binding per canonical
   parameter identity.
6. Typed lookup uses the parameter definition and does not require winning
   target knowledge.
7. Trace and `explain-config` are sanitized projections of the effective
   binding and override chain.

The exact type names remain subject to the GCF-01 inventory and naming review.

## Resolution Decision

The planning order is:

1. load each admitted source once;
2. decode and validate typed bindings;
3. normalize admitted aliases while retaining original spelling in
   provenance;
4. for the selected runtime context, apply matching target specificity from
   Global through ComponentClass and SubsystemInstance to ComponentInstance
   within the same source;
5. apply source precedence across sources;
6. link each new winner to the previous effective winner; and
7. publish one winner per canonical parameter.

Source precedence dominates target specificity across different sources. Phase
55 realizes this deterministic precedence over the distinct `.textus` and
`.cncf` layers admitted by Phase 53; Phase 53 itself neither selects a winner
nor assigns effective precedence.

## Component and Subsystem Target Refinement

The follow-up discussion found that `Component` alone is not a sufficient
target name. Configuration must distinguish:

- defaults shared by one Component class/type;
- one named Component instance;
- the containing Subsystem;
- and the Component instances configured inside that Subsystem.

A directly launched Component also has an implicit Component Subsystem. That
implicit Subsystem does not receive a special target kind. Once CNCF projects
its stable Subsystem identity and instance name, it uses the same
SubsystemInstance target and nested ComponentInstance addressing as an
explicit Subsystem.

The provisional target set therefore became:

```text
Global
ComponentClass(componentId)
SubsystemInstance(SubsystemInstanceId(subsystemId, instance))
ComponentInstance(
  SubsystemInstanceId(subsystemId, subsystemInstance),
  ComponentInstanceId(componentId, componentInstance)
)
```

## Consolidated and Split Textus Files

The user selected two equivalent physical authoring styles:

- all configuration may be written in `~/.textus/config.yaml`; and
- the same configuration may be divided under the reserved
  `~/.textus/components/*` and `~/.textus/subsystems/*` trees.

Top-level `components/<component-id>/config.yaml` selects ComponentClass.
`subsystems/<subsystem-id>/instances/<instance>/config.yaml` selects one
SubsystemInstance. A ComponentInstance is nested below that SubsystemInstance
as
`components/<component-id>/instances/<component-instance>/config.yaml`.
Consequently its target contains both the containing SubsystemInstanceId and
the existing ComponentInstanceId.

The consolidated YAML document mirrors the same `components`, `subsystems`,
and `instances` hierarchy. The initial canonical spelling keeps
`instances/default` explicit; any future omission is syntax sugar only.

Both forms are external projections into the same canonical parameter/target
binding model. An unqualified key receives its concrete target from its
document location. Defining the same canonical parameter for the same target
in both forms within one admitted layer is a duplicate/conflict, not an
unrecorded precedence rule.

## Instance Identity Finding

The existing runtime already represents a Component instance as
`ComponentInstanceId(componentName, instance)`. The current Subsystem model
uses `subsystemName` without an equivalent separate instance field. The
Phase 55 plan therefore introduces or maps to a validated
`SubsystemInstanceId(subsystemId, instance)` rather than continuing to treat a
bare Subsystem name as a sufficient configuration target. Exact public type
placement remains a GCF-01 contract-freeze decision.

## Directions Not Selected

The replanned phase does not use:

- a separately updated trace as an operational authority;
- final consumer lookup by winning target-qualified binding key;
- `Unqualified` as a semantic target;
- arbitrary User/WebApplication/multidimensional qualifiers without evidence;
- permanent parallel String-keyed and typed-keyed internal models; or
- generic-core knowledge of Textus, CNCF, StandaloneUserProfile, or ArtScene
  semantics.

## Ownership

The anticipated split is:

- `simplemodeling-lib` for the generic binding framework;
- CNCF/Textus for parameter definitions, namespaces, source admission, and
  runtime orchestration;
- launchers for exact String-boundary forwarding; and
- direct consumers for typed lookup migration.

The exact repository set remains a GCF-01 decision. Read-only inventory does
not authorize mutation of every discovered consumer.

## Documentation Decision

The discussion is recorded here as non-normative history. The provisional
contract is recorded in:

- `docs/notes/phase-55-configuration-binding-provisional-specification.md`.

The implementation plan and state ledger are:

- `docs/phase/phase-55.md`; and
- `docs/phase/phase-55-checklist.md`.

Normative `docs/design` and `docs/spec` are intentionally deferred until
failing-first acceptance, implementation, full validation, and review
establish actual behavior.

## Phase 53 CS-05B deferred-boundary intake, 2026-08-01

The owner explicitly deferred from Phase 53 the effective profile-binding and
precedence behavior, detailed layer/target/subsystem/field-path provenance,
explicit override representation, and ambient OS-environment conversion.
Phase 55 must inventory and freeze these public contracts under GCF-01, then
implement them through the ConfigurationBinding authority rather than adding
intermediate generic trace fields or a parallel profile-provenance API.

This intake preserves Phase 53's strict `StandaloneUserProfile` HOME-admission
and multi-user-exclusion semantics. It does not reopen those admission rules;
it supplies the later effective-binding implementation that consumes them.
