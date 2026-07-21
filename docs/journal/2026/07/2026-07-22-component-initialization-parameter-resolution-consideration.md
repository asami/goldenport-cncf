# Component Initialization Parameter Resolution Consideration (2026-07-22)

status=record
updated_at=2026-07-22
tag=component, configuration, initialization, parameter-resolution, textus-ai, cbd-support

## Position of This Record

This journal records the consideration result for a general CNCF mechanism that
interprets configuration and other property values in the correct component
context before component initialization.

The discussion started from Textus AI and CBD Support CAR Review AI settings:

- user-wide AI defaults;
- user settings for specific reviewed components;
- Textus AI runtime provider/profile settings; and
- CBD Support CAR Review component-specific AI policy.

The resulting direction is intentionally not Textus AI-specific. AI settings
are only the first motivating use case. The mechanism should become a general
CNCF component initialization parameter mechanism.

This file is a chronological record, not a normative specification.

## Problem

Component settings are currently at risk of being interpreted by individual
components after bootstrap. If each component reads raw configuration directly,
the system can drift in several ways:

- different components may invent different precedence rules;
- user-wide defaults and component-specific overrides may be applied
  inconsistently;
- test overlays may not match runtime behavior;
- secret and confidential values may leak through ad hoc access paths;
- observability may expose raw configuration rather than safe provenance; and
- reusable components may become dependent on ambient environment or filesystem
  state.

The immediate AI use case exposed this problem clearly. A user wants both
global AI defaults and settings for each reviewed component. Those settings
should not make Textus AI understand CBD Support's CAR Review component keys,
and CBD Support should not reimplement low-level CNCF configuration precedence.

## Existing Grounding

CNCF already separates raw configuration resolution from semantic
interpretation:

- `ResolvedConfiguration` is a flat resolved key/value store with trace. It is
  not supposed to own semantic validation.
- CNCF configuration source precedence is already stable:
  `HOME -> PROJECT -> CWD -> ENV -> ARGS`.
- Component-visible runtime configuration is declared through
  `ComponentConfigurationKey[A]` and accessed through
  `ComponentConfigurationAccess`.
- Declared component runtime configuration already avoids arbitrary key lookup
  and returns safe provenance.
- Component configuration sources are already separated into component,
  subsystem, and runtime layers.

Those pieces support the new direction, but they primarily cover runtime access
from a component operation. The missing piece is initialization-time
interpretation and injection.

## Decision Direction

CNCF should own a general component initialization parameter resolution
mechanism.

The conceptual flow is:

```text
ResolvedConfiguration
  -> ComponentParameterResolver
  -> ComponentInitializationParameters
  -> ComponentFactory.bootstrap / component.initialize
```

The component should receive resolved typed initialization parameters, not a
raw `ResolvedConfiguration` or arbitrary configuration map.

This mechanism should be general for CNCF components. Textus AI, CBD Support,
resource providers, process execution providers, and future components should
use the same initialization parameter contract rather than building separate
configuration readers.

## Proposed Vocabulary

The term `Parameter` is preferred for the initialization mechanism to avoid
confusion with existing runtime `ComponentConfigurationAccess`.

Candidate model names:

- `ComponentParameterContext`
- `ComponentParameterResolver`
- `ComponentInitializationParameters`
- `ComponentParameterKey[A]`
- `ComponentParameterResolution[A]`
- `ComponentParameterProvenance`

`ComponentConfigurationContext` is also possible, but it is more likely to be
confused with the existing declared runtime configuration API.

## Contract Shape

A future implementation can start from a resolver shaped like:

```scala
trait ComponentParameterResolver {
  def resolve(
    componentId: ComponentId,
    instanceId: ComponentInstanceId,
    descriptor: ComponentDescriptor,
    configuration: ResolvedConfiguration
  ): Consequence[ComponentInitializationParameters]
}
```

`ComponentInitializationParameters` should not be an untyped map. It should
support typed access, safe provenance, and component-specific projection into
initialization values.

Example conceptual access:

```scala
trait ComponentInitializationParameters {
  def resolve[A](
    key: ComponentParameterKey[A]
  ): Consequence[ComponentParameterResolution[A]]
}
```

Components with richer initialization contracts may project those parameters
into a dedicated init value:

```scala
final case class TextusAiInit(...)
final case class CbdSupportInit(...)
final case class SomeComponentInit(...)
```

The resolver owns the generic context and precedence. Component-specific
builders own only their domain-specific projection.

## Responsibility Boundary

CNCF owns:

- low-level configuration source precedence;
- interpretation context selection by `ComponentId` and
  `ComponentInstanceId`;
- separation of runtime, subsystem, component, and test overlay values;
- typed parameter decoding;
- required/optional semantics;
- safe provenance;
- secret and confidential value boundary enforcement; and
- bootstrap-time delivery to component initialization.

Individual components own:

- declaring the parameters they need;
- converting resolved generic parameters into component-specific init values;
- validating domain-specific combinations; and
- using the injected values rather than re-reading raw configuration.

Individual components should not:

- read environment variables, system properties, or configuration files to
  obtain ordinary component parameters;
- reinterpret low-level CNCF precedence;
- accept raw configuration maps as operational authority;
- expose confidential values through initialization diagnostics; or
- let request parameters override initialization parameters.

## AI Use Case Mapping

The AI configuration discussion becomes an application of the general
mechanism.

Conceptually:

```text
CNCF ComponentParameterResolver
  -> TextusAiInit(profile, execution policies, provider-neutral defaults)
  -> CbdSupportInit(review AI defaults, reviewed-component overlays)
```

Textus AI should receive AI runtime settings:

- runtime profile;
- execution-class policy;
- provider-neutral global defaults;
- application-purpose policy defaults; and
- provider connection settings through the normal secret/config boundary.

CBD Support should receive CAR Review settings:

- review AI defaults;
- reviewed-component-specific AI policy overlays;
- CAR Review prompt/schema policy references; and
- safe policy provenance for report metadata.

Textus AI should not understand CBD Support reviewed component keys such as
`organization/name/version`. CBD Support should not reimplement CNCF source
precedence or raw configuration assembly.

## Relation to Runtime Component Configuration

This initialization mechanism does not replace
`ComponentConfigurationKey[A]` and `ComponentConfigurationAccess`.

The intended split is:

```text
Initialization time:
  CNCF resolves and injects ComponentInitializationParameters.

Operation runtime:
  Component logic may request declared ComponentConfigurationKey[A] values
  through the protected component runtime boundary.
```

Both mechanisms should share the same principles:

- declared keys;
- typed decoding;
- structured failures;
- no arbitrary raw lookup;
- provenance without leaking unrelated source details;
- secret references instead of secret values; and
- isolation by `ComponentInstanceId`.

## Open Design Points

The next design pass should decide:

- whether `ComponentParameterKey[A]` reuses
  `ComponentConfigurationKey[A]` internally or remains a separate type;
- how component packaged defaults, assembly defaults, subsystem settings,
  runtime settings, and test overlays are represented in the resolver input;
- whether component-specific init values are created by CNCF core, component
  factories, or component-provided builders;
- how to expose safe initialization parameter facts in CallTree or bootstrap
  diagnostics;
- how to handle secret references during initialization without introducing a
  secret value accessor; and
- how generic SAR/component instance declarations should express
  component-instance parameter overrides.

## Recommended Development Item

Define a CNCF development item:

```text
Component Initialization Parameter Resolution
```

The first phase should produce a normative specification and a small runtime
slice that proves:

- `ComponentInstanceId` selects the applicable parameter context;
- component, subsystem, runtime, and test overlay values are resolved in one
  documented order;
- declared typed initialization parameters decode successfully or fail with
  structured `Consequence` failures;
- raw configuration is not exposed to component initialization code;
- safe provenance is available for diagnostics; and
- Textus AI or CBD Support can consume the mechanism without custom
  configuration-source precedence logic.
