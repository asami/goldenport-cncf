# Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation

Date: 2026-07-30

Status: consolidated planning decision

## Purpose

This journal consolidates the Phase 53 direction discussed through:

- ComponentStyle and Component capability modeling;
- standalone and multi-user operation;
- implicit Subsystem configuration;
- fixed-user and authenticated-user `ExecutionContext` construction;
- Textus/CNCF home configuration layering;
- configuration provenance;
- ArtScene adoption; and
- post-implementation specification promotion.

It does not replace normative design or specification. Phase 53 implementation
and executable acceptance must verify this direction before it is promoted to
`docs/design` and `docs/spec`.

This journal separates:

- **Phase 53 delivery scope**, which is synchronized into the Phase 53 plan,
  checklist, notes, and acceptance; and
- **future expansion candidates**, which remain non-normative journal material
  and are not Phase 53 completion requirements. Their specification
  consideration and possible implementation are scheduled separately in
  Phase 55.

## Source History

This consolidation incorporates:

- [CML Application Mode Capability Consideration](2026-07-30-cml-application-mode-capability-consideration.md);
- [Textus and CNCF Home Configuration Layer Decision](2026-07-30-textus-cncf-home-configuration-layer-decision.md);
- [CAR Runtime Evidence Two-Route Repair Handoff](2026-07-29-car-runtime-evidence-two-route-repair-handoff.md);
- [CML ComponentStyle, ExecutionContext, and Capability Specification Proposal](../../../notes/cml-component-style-execution-context-capability-specification-proposal.md);
- [Phase 53](../../../phase/phase-53.md); and
- the subsequent discussion correcting Component-visible mode and independent
  configuration-provenance proposals.

The earlier ApplicationMode and ComponentMode proposals remain chronological
history. The consolidated direction below supersedes them for Phase 53
planning.

## Consolidated Problem

ArtScene is a full-fledged user-scoped domain Component. It uses Entity,
Aggregate, Command/Query separation, domain events, projections, persistence,
transactions, and optimistic concurrency.

It must operate in two Web forms:

- standalone operation for one configured current user; and
- multi-user operation for an authenticated request user.

The Component implementation should not contain two domain models or branch on
the operation form. The surrounding runtime must construct the correct current
user, locale, authorization, datastore, and other capabilities before invoking
the Component.

At the same time, CML needs one concise declaration of the Component's
implementation/capability family. Runtime and launcher configuration need
deterministic layering between ordinary Textus settings and CNCF development
overrides, with traceable parameter provenance.

## Phase 53 Delivery Scope

Phase 53 delivers only:

- the CNCF-provided built-in ComponentStyle and its capability contract;
- CML selection and development/package descriptor projection;
- implicit Subsystem ExecutionProfiles and capability matching;
- fixed-user and authenticated-user ExecutionContext construction without
  Component-visible mode branching;
- `~/.textus` ordinary configuration with `~/.cncf` development override;
- common and subsystem-specific StandaloneUserProfile resolution;
- provenance through the existing generic configuration trace facility;
- existing WebApplicationMode integration and Subsystem-owned datastore
  selection; and
- ArtScene adoption and acceptance.

Phase 53 makes only the minimal generic configuration changes proven necessary
for those behaviors. It does not use review findings as authorization to build
a new general-purpose configuration framework.

## Vocabulary and Ownership

### ComponentStyle

`ComponentStyle` is a versioned CNCF catalog entry describing a standard
bundle of capabilities provided by a Component and facilities required from
its containing Subsystem.

It is selected in CML. It is not:

- an ApplicationMode;
- a ComponentMode;
- a SubsystemMode;
- a datastore-placement switch; or
- an untyped policy bag.

### Component capability

The primary organization axis is what a Component provides.

A Subsystem contract asks whether the resolved Component set can provide the
capabilities the Subsystem operation requires. Capability identifiers are
typed and versioned. Grouped capabilities expand deterministically into
detailed capabilities.

### WebApplication

`WebApplication` remains a valid and necessary concept.
`WebApplicationMode` remains the Web-specific standalone/multi-user contract.
It owns Web ingress and account/session entry. It supplies Web-specific
identity evidence to the containing Subsystem, but it is not the universal
selector of a user-context provider. Non-Web ingress must be able to construct
the same Component-facing context without inventing a WebApplication.

The containing Subsystem owns the `user-context.current@1` requirement and
resolves an ingress-appropriate user-context provider. Web, CLI, job, test, and
other ingress adapters provide authenticated, fixed-user, or explicit test
identity evidence to that resolver. Neither the ingress kind nor the selected
provider enters domain Component execution.

### OperationMode

`OperationMode` continues to describe launcher/runtime posture such as
`production`, `demo`, `develop`, and `test`.

It is independent from `WebApplicationMode` and must not be exposed to
Component domain logic. Any posture-dependent fact needed by Component
execution is resolved into a typed, mode-free capability before invocation.

### No generic ApplicationMode

`Application` is misleading as a generic owner of these concerns. Concepts are
named according to their actual owner:

- Component for Component declarations and capabilities;
- Subsystem for assembly, datastore, provider, and lifecycle;
- WebApplication for Web ingress behavior; and
- User for identity and preferences.

Phase 53 does not introduce a generic `ApplicationMode`.
The same ownership rule applies to configuration: each typed contract names
its actual owner and overlay target. Phase 53 does not introduce a generic
configuration-scope taxonomy merely to anticipate future consumers.

## ArtScene ComponentStyle

ArtScene selects:

```text
full-fledged-with-standalone
```

The style name expresses the relevant combination:

- the Component has a full-fledged domain model;
- it is multi-user-capable;
- it also operates unchanged in standalone form; and
- standalone is not a reduced or preferred domain mode.

The initial provided capabilities are:

```text
domain.full@1
user.multi-user@1
user.fixed-context-compatible@1
```

The initial required Subsystem capabilities are:

```text
user-context.current@1
datastore.persistent@1
datastore.transactional@1
datastore.optimistic-concurrency@1
```

`domain.full@1` is a named versioned bundle. It expands to detailed
capabilities including:

- Entity;
- Aggregate;
- Command;
- Query;
- domain event;
- projection;
- persistence;
- transaction; and
- optimistic concurrency.

Generation and runtime use structured capability metadata. They do not infer
behavior by parsing the style name.

## CML Boundary

CML declares only the selected ComponentStyle:

```dox
# COMPONENT

## ArtScene

### STYLE

full-fledged-with-standalone
```

CML does not declare:

- fixed user;
- locale;
- WebApplicationMode;
- datastore provider or placement;
- Subsystem launch default; or
- runtime/development override values.

The explicit `COMPONENT` declaration generates a Component even when the CML
contains no service, operation, entity, or other declaration.

CNCF supplies the selectable ComponentStyles. A future Metadata Factory may
contribute additional styles through the same provider/schema/capability
contract, but registration, discovery, packaging, conflict handling, and
external-provider acceptance are future work rather than Phase 53
implementation.

## Permanent Component Boundary

Component implementation must not know the operating form.

Permanent Component APIs, generated internal DSLs, ComponentFactory hooks,
ActionCall implementations, and domain policies must not:

- receive `ApplicationMode`, `ComponentMode`, `SubsystemMode`,
  `WebApplicationMode`, or `OperationMode`;
- read mode or datastore-policy configuration keys;
- branch on standalone or multi-user;
- create or hardcode a standalone UserId;
- branch on fixed-user versus authenticated-user origin;
- choose a datastore/provider/path/endpoint/pool from mode; or
- derive locale, authorization, or user ownership from mode.

ComponentFactory may supply typed, side-effect-free Component parameters and
capability implementation evidence. It may not select a user-context provider,
datastore, or mode-specific parameter set.

Temporary migration adapters may exist only when unavoidable. Such an adapter
must remain internal and deprecated, have an explicit removal item, and remain
outside permanent CML, catalog, generated DSL, Component API, and normative
specification.

## ExecutionContext Absorption

All operating-form differences are absorbed before Component execution.
User-context resolution occurs at the ingress/Subsystem boundary.
`WebApplicationMode` is an input only when the ingress is a WebApplication; it
is not a general runtime mode and is not required for CLI, job, test, or other
execution.

### Fixed-user execution

```text
standalone Web, CLI, job, or other fixed-user ingress
  -> Subsystem user-context provider resolution
  -> fixed user resolved from admitted configuration
  -> SecurityContext
  -> user locale/timezone preferences
  -> resolved datastore and UnitOfWork capabilities
  -> ExecutionContext
  -> Component
```

### Authenticated-user execution

```text
multi-user Web or another authenticated ingress
  -> authenticated invocation identity
  -> Subsystem user-context provider resolution
  -> SecurityContext
  -> user locale/timezone preferences
  -> resolved datastore and UnitOfWork capabilities
  -> ExecutionContext
  -> Component
```

The Component-facing contract is identical. It does not expose an operating
mode, provider-origin field, or other construction-path discriminator.
SecurityContext may legitimately contain different claims and authorization
facts for different users, but Component domain logic must not treat those
facts as a proxy for fixed-user versus authenticated-user mode branching.

Standalone therefore uses multi-user domain semantics with one fixed current
user. An ingress that requires authentication never falls back to the fixed
user when authentication is missing or fails. Failure to satisfy the
Subsystem's `user-context.current@1` requirement fails before Component
invocation.

## Fixed User Profile

Ordinary standalone operation uses:

```text
~/.textus/user-profile.yaml
```

The document contract is:

```yaml
apiVersion: textus/v1
kind: StandaloneUserProfile
```

`StandaloneUserProfile` is bootstrap configuration for the fixed current user used
by standalone execution. It is distinct from the persisted `UserProfile`
domain entity supplied by `textus-user-account`.

The fixed profile supplies stable user-facing values such as:

- UserId;
- display name;
- locale;
- timezone; and
- other explicitly defined fixed-user preferences.

StandaloneUserProfile does not admit launcher or runtime operation settings. In
particular, `WebApplicationMode`, `OperationMode`, datastore provider,
endpoint/path, credentials, pool, diagnostic controls, and CNCF implementation
parameters do not belong to this document.

The initial externally addressable field mapping is:

| Typed field | Canonical parameter |
| --- | --- |
| `user.id` | `textus.fixed-user.id` |
| `user.displayName` | `textus.fixed-user.display-name` |
| `user.locale` | `textus.fixed-user.locale` |
| `user.timezone` | `textus.fixed-user.timezone` |

A subsystem-specific section may override selected common profile fields. The
typed document collection is named `subsystems`. Subsystem lookup uses a stable
subsystem identifier, not a display name.

A partial higher-precedence override is always admitted from:

```text
~/.cncf/user-profile.yaml
```

Its admission is not gated by `OperationMode`. It participates only when
resolving a fixed user. When the selected Web operation is `multi-user`, the
runtime uses authenticated-user evidence and ignores both
`~/.textus/user-profile.yaml` and `~/.cncf/user-profile.yaml`.

StandaloneUserProfile has an explicit source-admission contract:

| Source | StandaloneUserProfile admission |
| --- | --- |
| Contract defaults | Only for fields whose public schema defines a default |
| `HOME/.textus/user-profile.yaml` | Normal public baseline |
| `HOME/.cncf/user-profile.yaml` | Always-admitted higher-precedence override using the same schema; consulted only for fixed-user resolution |
| `PROJECT/.textus`, `PROJECT/.cncf` | Not admitted |
| `CWD/.textus`, `CWD/.cncf` | Not admitted |
| Environment | Existing canonical `textus.fixed-user.*` input for common fields only; no subsystem-specific transport added in Phase 53 |
| Explicit arguments | Existing canonical `textus.fixed-user.*` input for common fields only; no subsystem-specific transport added in Phase 53 |
| Explicit runtime/test override | Controlled injection for the selected fixed-user resolution, recorded as `ConfigurationOrigin.ExplicitOverride` |

PROJECT and CWD configuration must not change the fixed current user merely
because a user enters or launches from a project directory.

Within each admitted HOME user-profile document, the subsystem-specific
section overrides the common section. The effective field precedence is:

```text
contract default
  < ~/.textus common
  < ~/.textus subsystem
  < ~/.cncf common
  < ~/.cncf subsystem
  < environment
  < explicit arguments
  < explicit runtime/test override
```

Environment and argument values address common StandaloneUserProfile fields only in
Phase 53 and override HOME profile values according to source precedence. They
do not introduce a subsystem-specific transport. A controlled explicit
runtime/test override targets the selected fixed-user resolution after common
and subsystem HOME overlay. None of these inputs makes PROJECT or CWD profile
documents admissible.

The fixed UserId remains stable across restart. Changing an ID that owns
persisted data is a user-data migration. Any CNCF, environment, argument, or
runtime/test override that changes UserId therefore requires isolated data
unless migration is intentional.

## Public Web Operation Selection

Web operation selection is resolved before StandaloneUserProfile resolution.
It belongs to the `goldenport-cncf` runtime bootstrap and implicit or explicit
Subsystem WebApplication profile, not to a wrapper launcher, StandaloneUserProfile,
or ComponentStyle.

The stable subsystem identifier is established before any subsystem-specific
profile lookup. Its authority is the containing
`GenericSubsystemDescriptor.subsystemName`:

- an explicit Subsystem uses its declared `subsystemName`;
- the implicit Subsystem created for direct Component launch uses the
  `subsystemName` projected by
  `GenericSubsystemDescriptor.fromComponentDescriptor`;
- that projection uses `ComponentDescriptor.subsystemName` when supplied and
  otherwise the authoritative `ComponentDescriptor.componentName`, with the
  existing descriptor-name fallback only when componentName is absent; and
- directory names, display names, CML headings, launcher arguments, and
  locally published artifact coordinates do not independently redefine it.

If all descriptor-owned identities are absent, subsystem-qualified bootstrap
fails even if a repository loader can use a path-derived fallback for
general repository discovery.

The selected value is used consistently by Web operation and StandaloneUserProfile
resolution in that bootstrap. Missing, invalid, or conflicting identity fails
before profile resolution. Development-directory and packaged-CAR descriptors
must project the same subsystemName; parity acceptance compares that stable
identifier directly.

For the current Textus direct-Component operation, the normal launch policy
selects `standalone` when:

- no explicit Web operation is selected;
- the implicit Subsystem supplies a valid standalone ExecutionProfile; and
- the Component provides `user.fixed-context-compatible@1`.

This is a Textus normal-launch default, not evidence that standalone is the
preferred semantic form of the ComponentStyle.

When all three conditions hold, the runtime contributes the normal
standalone default for that implicit Subsystem:

```text
textus.web.application-mode = standalone
origin = Default
sourceType = derived-default
sourceId = textus-direct-component-standalone
```

Its provenance identifies the implicit Subsystem standalone ExecutionProfile
and the Component's `user.fixed-context-compatible@1` evidence. An explicit
value from an admitted configuration source overrides it. When the conditions
do not hold, no standalone default is synthesized; absence of an explicit
valid selection fails rather than silently choosing another mode.

An operator may explicitly select the public Web operation with the canonical
parameter:

```text
textus.web.application-mode
```

It may be supplied by an admitted `~/.textus/config.yaml` source and overridden
from `.cncf`, environment, arguments, or explicit override under the generic
precedence rules already supported by the runtime. A subsystem-specific profile
uses the stable subsystem identifier. No parallel
`cncf.web.application-mode` parameter is introduced.

The runtime resolves this selection first. A `standalone` result then requires
StandaloneUserProfile resolution. A `multi-user` result requires authenticated
identity evidence and does not read or fall back to StandaloneUserProfile.

## Textus and CNCF Configuration Layers

The two configuration directories are intentionally used together.

| Directory | Responsibility | Primary audience |
| --- | --- | --- |
| `.textus` | Canonical ordinary user/operator configuration | Textus user and operator |
| `.cncf` | Framework-internal, development, diagnostic, and operational override/configuration | component developer, operator, and CNCF runtime |

`.textus` is the public baseline. `.cncf` is a higher-precedence admitted
override, not merely a deprecated alias.

Both directories are always eligible configuration sources; their admission
is not switched by `OperationMode`. A typed contract may still ignore its
documents when that contract is irrelevant to the selected operation. In
particular, `StandaloneUserProfile` resolution uses both HOME profile documents for
standalone/fixed-user execution and uses neither document for multi-user
execution.

Precedence is source-location-first:

```text
contract defaults
  < HOME/.textus
  < HOME/.cncf
  < PROJECT/.textus
  < PROJECT/.cncf
  < CWD/.textus
  < CWD/.cncf
  < environment
  < explicit arguments
  < explicit runtime/test override
```

This is the generic ordering of admitted sources, not a declaration that every
schema or key accepts every source. Each typed contract defines its
source-admission policy first, and the generic ordering is then applied only to
the admitted sources. StandaloneUserProfile uses the HOME-centered admission table
above.

An explicit runtime/test override is an API-level injection used by a
controlled launcher or executable specification. It is not a hidden file
source. Phase 53 adds the minimal
`ConfigurationOrigin.ExplicitOverride`, ordered after
`ConfigurationOrigin.Arguments`, so its provenance is not mislabeled as an
argument or physical file. `sourceType` distinguishes `runtime` and `test`;
`sourceId` identifies the controlled caller or executable specification. This
origin addition does not introduce the deferred typed-key, qualifier, candidate,
namespace, alias, or codec framework.

Directory ownership and key namespace are independent:

- a user-facing semantic keeps its `textus.*` key even when its value is
  supplied by a `.cncf` file;
- `cncf.*` is reserved for an internal, diagnostic, development, or
  implementation control; and
- the same semantic is not duplicated as unrelated `textus.*` and `cncf.*`
  keys merely to obtain precedence.

This reverses the previous same-scope directory rule:

```text
previous: .cncf compatibility < .textus primary
selected: .textus public baseline < .cncf internal/development override
```

Existing key aliases do not determine directory precedence. Phase 53 does not
introduce a generic alias-normalization framework. Any directly affected
legacy spelling is handled by its owning contract and recorded separately.

## Future Generic Configuration Framework Candidates

The following ideas arose while reviewing Phase 53, but they are not required
to deliver its ComponentStyle, fixed-user, configuration-layering, provenance,
or ArtScene behavior. They are retained as candidates for the Phase 55 Generic
Configuration Framework Extension specification consideration. Phase 55 is
only a scheduling frame at this point, and Phase 53 must not implement these
candidates merely because they are described here:

- typed canonical parameter and binding identities;
- a generic semantic-scope or qualifier model;
- open/closed namespace catalogs;
- a candidate set that resolves several Subsystem contexts;
- new reversible binding and environment codecs;
- a generic alias-normalization framework; and
- repository-wide migration from String keys to typed keys.

The names, ownership, compatibility boundary, and necessity of these candidates
remain undecided. In particular, `Global`, `Unqualified`, Subsystem, and any
future WebApplication qualifier must not be treated as approved Phase 53
vocabulary.

### Candidate: Typed Parameter and Binding Identities

Phase 53 already requires one String spelling for each semantic:

- a user/operator-facing parameter uses `textus.*`;
- a CNCF-internal, diagnostic, development-control, or implementation
  parameter uses `cncf.*`; and
- the same semantic is not defined twice as `textus.foo` and `cncf.foo`.

The directory containing a value does not rename the parameter. For example,
a development override in `.cncf` of the public locale parameter still
overrides the canonical `textus.*` locale key; it does not introduce a
parallel `cncf.*` locale.

Typed Textus documents such as `user-profile.yaml` are namespaced by their
schema identity. Their YAML field paths do not need a mechanical `textus`
prefix on every field.

A future framework could formalize the distinction between canonical parameter
identity and scoped binding identity:

- `CanonicalParameterId` identifies one public or internal semantic, such as
  `textus.fixed-user.locale`;
- `ConfigurationBindingKey` identifies one supplied occurrence of that
  semantic, including its Subsystem qualifier when present; and
- multiple binding keys do not define multiple parameters when they reference
  the same CanonicalParameterId.

One possible future model would place typed value objects in
`simplemodeling-lib` and could be structurally equivalent to:

```scala
final class CanonicalParameterId private (val value: String)

object CanonicalParameterId {
  def parse(value: String): Consequence[CanonicalParameterId]
}

final class ConfigurationSubsystemId private (val value: String)

object ConfigurationSubsystemId {
  def parse(value: String): Consequence[ConfigurationSubsystemId]
}

enum ConfigurationSemanticScopeKind {
  case Global
  case Subsystem
}

enum ConfigurationSemanticScope {
  case Global
  case Subsystem(subsystemId: ConfigurationSubsystemId)
}

final class ConfigurationParameterDefinition private (
  val parameterId: CanonicalParameterId,
  val allowedSemanticScopes: Set[ConfigurationSemanticScopeKind]
)

object ConfigurationParameterDefinition {
  def create(
    parameterId: CanonicalParameterId,
    allowedSemanticScopes: Set[ConfigurationSemanticScopeKind]
  ): Consequence[ConfigurationParameterDefinition]
}

final class ConfigurationBindingKey private (
  val parameterId: CanonicalParameterId,
  val semanticScope: ConfigurationSemanticScope
)

object ConfigurationBindingKey {
  def create(
    parameterId: CanonicalParameterId,
    semanticScope: ConfigurationSemanticScope,
    catalog: ConfigurationParameterCatalog
  ): Consequence[ConfigurationBindingKey]
}
```

`CanonicalParameterId` validates the canonical dotted grammar
`[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+`. This generic value object does not
assign Textus/CNCF meaning to the first segment. Existing generic parameters
such as `service.enabled` and contract-owned namespaces remain valid.

The typed contract that publishes a parameter supplies its
`ParameterNamespacePolicy` and `ConfigurationParameterDefinition`. A namespace
policy declares whether its namespace is open or closed. The Textus/CNCF
policy uses closed namespaces and requires `textus.*` for public parameters
and `cncf.*` for CNCF-internal parameters. Every key in those namespaces must
be a published canonical parameter or an admitted temporary alias. A typo or
removed alias is therefore an unknown-key error even when it is Global.

Other contracts may own open or closed namespaces. An unregistered Global
parameter is admitted only in an open or unowned generic namespace;
`simplemodeling-lib` does not embed Textus/CNCF namespace semantics. Catalog
assembly rejects conflicting definitions or namespace ownership for the same
CanonicalParameterId.

`ConfigurationSubsystemId` construction NFC-normalizes its input and rejects
an empty value, leading or trailing whitespace, and control characters.
Equality and hashing are exact and case-sensitive on the normalized value.
`ConfigurationBindingKey` stores Subsystem identity structurally rather than
recovering it by splitting a flattened key.

Subsystem qualification is also contract-checked:

- an unregistered generic parameter may be bound globally only under the open
  namespace rule above;
- when a published `ConfigurationParameterDefinition` exists, every binding
  must use one of its allowed semantic-scope kinds;
- a subsystem-qualified binding requires a published
  `ConfigurationParameterDefinition` that admits
  `ConfigurationSemanticScopeKind.Subsystem`;
- the StandaloneUserProfile parameters and `textus.web.application-mode` publish
  both Global and Subsystem as allowed semantic scopes; and
- an unsupported subsystem-qualified binding fails before merge and trace
  construction.

The `CanonicalParameterId`, `ConfigurationSubsystemId`,
`ConfigurationParameterDefinition`, and `ConfigurationBindingKey`
constructors are non-public. Validated factories or opaque types are the only
creation path; public case-class `apply`/`copy` and unchecked convenience
constructors must not bypass grammar, NFC, namespace, catalog, or
semantic-scope validation. A parameter definition must admit at least one
semantic-scope kind. `Configuration`, merge, resolver, and trace APIs accept
only validated `ConfigurationBindingKey` values.

For example:

| Meaning | CanonicalParameterId | Canonical binding string |
| --- | --- | --- |
| Global fixed-user locale | `textus.fixed-user.locale` | `textus.fixed-user.locale` |
| ArtScene fixed-user locale | `textus.fixed-user.locale` | `@textus-art-scene:textus.fixed-user.locale` |
| Global Web operation | `textus.web.application-mode` | `textus.web.application-mode` |
| ArtScene Web operation | `textus.web.application-mode` | `@textus-art-scene:textus.web.application-mode` |

In that possible future model, exact binding merge and final semantic
resolution would be separate typed stages.
Before Subsystem selection, all decoded inputs live in an internal candidate
model:

```scala
final class ConfigurationSourcePosition private (
  val origin: ConfigurationOrigin,
  val rank: Int,
  val ordinal: Int
)

final class ConfigurationCandidate private (
  val bindingKey: ConfigurationBindingKey,
  val value: ConfigurationValue,
  val sourcePosition: ConfigurationSourcePosition,
  val sourceType: Option[String],
  val sourceId: Option[String],
  val inputKey: Option[String],
  val evidenceRefs: Vector[String]
)

final class ConfigurationCandidateSet private (
  val candidates: Vector[ConfigurationCandidate]
)
```

`ConfigurationCandidateSet` could be the sole operational authority for source
and semantic-scope selection. It retains Global and multiple Subsystem
bindings, complete source order, normalized aliases, and physical source
evidence.
Resolution takes an optional selected `ConfigurationSubsystemId`; without one,
only Global bindings are eligible. A runtime resolving more than one Subsystem
context materializes a separate resolved result for each selected Subsystem
from the same immutable candidate set.

`ConfigurationTrace` would remain descriptive evidence and would never be
consulted to choose an effective value. A future phase could change final
resolved values and trace to aligned typed indexes:

```scala
final case class Configuration(
  values: Map[ConfigurationBindingKey, ConfigurationValue]
)

final case class ConfigurationTrace(
  entries: Map[ConfigurationBindingKey, ConfigurationResolution]
)
```

The final `Configuration` contains exactly one winning binding per
CanonicalParameterId for that resolution request. Global and multiple
Subsystem values coexist only in `ConfigurationCandidateSet`, before
Subsystem selection. `ConfigurationResolution.key` is the same winning
typed `ConfigurationBindingKey`, from which CanonicalParameterId is obtained;
its history is derived from the overridden eligible candidates.
`ConfigurationResolution.evidenceRefs` retains bounded identifiers for
contract- or capability-derived candidates and is empty for ordinary physical
sources.

`ResolvedConfiguration.configuration.values` and
`ResolvedConfiguration.trace.entries` must have aligned binding-key identity.
Their key sets are equal, each trace map key equals its
`ConfigurationResolution.key`, and no two final entries reference the same
CanonicalParameterId. `ResolvedConfiguration` therefore means post-semantic
resolution, not an unresolved binding collection.
Typed lookup is the core API. String lookup is a boundary adapter through the
one codec and does not remain a second authoritative key model.

A single reversible `ConfigurationBindingKeyCodec` defines the canonical
binding-string grammar:

```text
global-binding     = canonical-parameter-id
subsystem-binding  = "@" percent-encoded-subsystem-id ":" canonical-parameter-id
```

`@` and `:` are outside the CanonicalParameterId grammar, so a Global
parameter cannot collide with a Subsystem binding. The Subsystem
qualifier uses canonical UTF-8 percent-encoding with ASCII
`[A-Za-z0-9_-]` as its exact unreserved set. Every other byte, including `.`,
`%`, `:`, and `@`, is encoded with uppercase hexadecimal digits. Malformed,
over-encoded, non-UTF-8, non-NFC, or otherwise non-canonical encodings fail;
decoding does not silently normalize an external spelling.
`decode(encode(key)) == key`, and two unequal binding keys must never encode to
the same string.

Arguments, diagnostics, and serialized trace output use this canonical form.
Environment variables use the separate reversible
`ConfigurationEnvironmentBindingCodec`, because shell identifier syntax cannot
represent the canonical form directly. Each ParameterNamespacePolicy publishes
one unique environment prefix matching `[A-Z][A-Z0-9]*`; the initial prefixes
are `TEXTUS` and `CNCF`. Catalog assembly rejects duplicate prefixes.

The environment grammar is:

```text
global-env =
  prefix "_BINDING_G_" encoded-canonical-parameter-id

subsystem-env =
  prefix "_BINDING_S_" uppercase-hex-utf8-subsystem-id
  "_" encoded-canonical-parameter-id
```

`encoded-canonical-parameter-id` maps lowercase ASCII letters to uppercase,
retains digits, maps `-` to `_H`, and maps `.` to `_D`. No other input
characters are admitted by CanonicalParameterId. The Subsystem identifier is
NFC-normalized before its UTF-8 bytes are encoded as uppercase hexadecimal, so
case-sensitive and non-ASCII identities round-trip without depending on shell
case behavior. Decoding rejects non-UTF-8 and non-NFC byte sequences rather
than silently normalizing them.

Examples:

```text
TEXTUS_BINDING_G_TEXTUS_DFIXED_HUSER_DLOCALE
TEXTUS_BINDING_S_7465787475732D6172742D7363656E65_TEXTUS_DFIXED_HUSER_DLOCALE
```

The codec validates that the decoded CanonicalParameterId belongs to the
namespace policy selected by the prefix, then delegates binding construction
to the same catalog and ConfigurationBindingKey factory.
`decode(encode(key, policy), catalog) == key`; unknown prefixes, lowercase hex,
invalid escapes, namespace-prefix mismatches, and collisions fail. Lossy
underscore replacement is prohibited. Runtime code does not parse Subsystem
identity with ad hoc string splitting.

### Candidate: Source and Semantic-Scope Resolution

Source precedence and Global/Subsystem specificity form one deterministic
resolution order. For a selected Subsystem and CanonicalParameterId:

1. source admission and alias normalization occur first;
2. each ordered physical source contributes its Global candidate and, when
   present, its matching Subsystem candidate;
3. within that one source, the matching Subsystem candidate overrides its
   Global candidate; and
4. the resulting per-source candidates are folded from low to high source
   precedence.

The resolver assigns ConfigurationSourcePosition from the fixed admitted
source sequence. A ConfigurationSource or caller cannot provide or raise its
own rank or ordinal.

Source precedence therefore dominates semantic specificity across different
sources. A higher-precedence Global value overrides a lower-precedence
Subsystem value, while a Subsystem value overrides Global only within the
same source. This applies uniformly to HOME, PROJECT, CWD, environment,
arguments, and explicit override after each contract's source-admission rules.

`ConfigurationCandidateSet` retains both the raw binding identity and the
complete source precedence position, including deterministic order within one
rank, for every candidate. The resolver chooses the winner from that state,
then derives the final Configuration and its exact ConfigurationTrace entry
from the same decision. Overridden eligible candidates become trace history;
trace is output evidence, not resolver input. Selection must not be
reimplemented as an unconditional Subsystem-before-Global lookup after exact
binding-key merge.

Permanent alias pairs for the same semantic are prohibited. If a legacy alias
must be admitted during migration, its parameter spelling is normalized to the
`CanonicalParameterId` before `ConfigurationBindingKey` construction, merge,
and trace construction.

Alias behavior is deterministic:

- the typed Textus or CNCF contract that owns a `CanonicalParameterId` also
  owns its temporary alias catalog and removal condition;
- `simplemodeling-lib` provides only the generic, caller-supplied
  canonicalization mechanism and trace fields, not Textus/CNCF alias
  semantics;
- supplying the canonical spelling and its alias for the same binding in one
  physical source is a structural configuration conflict;
- when different precedence sources use different spellings, each source is
  normalized first and ordinary canonical binding-key precedence applies; and
- trace history retains the original spelling and physical source while the
  effective entry is indexed by the canonical binding key.

After an alias reaches its removal condition, closed-namespace admission could
make its use an unknown-key error. A future phase would inventory existing
aliases before choosing removal or bounded temporary normalization.

### Candidate: Typed-Key Migration Boundary

Changing the core indexes from `String` to `ConfigurationBindingKey` is an
intentional breaking API migration, not a local representation change. Any
future phase selecting this candidate must inventory every direct constructor,
lookup, accessor, resolver, merge, and trace consumer that currently depends
on:

- `Configuration(Map[String, ConfigurationValue])`;
- `ConfigurationTrace(Map[String, ConfigurationResolution])`;
- `ConfigurationResolution.key: String`;
- `Configuration.values` or `ConfigurationTrace.entries` as String-keyed
  maps; and
- `get(String)` and equivalent String-keyed convenience APIs.

All affected repositories known from that inventory would need to be admitted
to that future phase before implementation. The typed core API and its known
consumers are migrated as one coherent change. External configuration files,
environment variables, arguments, and serialized diagnostics remain String
boundaries and enter or leave the typed model only through the canonical codec
or its catalog-backed environment transport adapter.

A temporary String adapter, if required to sequence compilation, would be
non-authoritative and would need to be removed or reduced to an explicit
boundary adapter before that future phase closes. Such a phase should not close
with parallel String-keyed and typed-keyed authorities. Every migrated
repository requires cross-repository compilation and its applicable full
tests.

## Configuration Provenance

The existing:

```text
org.goldenport.configuration.ResolvedConfiguration
org.goldenport.configuration.ConfigurationTrace
```

remain the canonical final configuration-resolution result and descriptive
provenance facility.
Phase 53 must not create an independent provenance model for user profiles,
ComponentStyle parameters, or launcher settings.

Every effective configuration field needs traceable evidence for:

- its admitted source kind;
- `.textus` or `.cncf` layer;
- physical file or explicit input identity;
- logical key or typed-document field path;
- common or subsystem-specific profile target, when applicable;
- contract- or capability-derived default identity, when applicable;
- overridden history; and
- final effective source.

The existing trace already carries key, effective value, origin, and history.
Phase 53 must verify and complete file-source metadata so `.textus` and
`.cncf` are distinguishable. StandaloneUserProfile common values and
subsystem-specific overlays remain explainable through that trace. Phase 53
extends existing trace metadata only where executable acceptance proves that
the source file, layer, field path, overlay target, or derived-default evidence
cannot otherwise be explained. The one required origin extension is
`ConfigurationOrigin.ExplicitOverride`; environment and arguments retain their
existing distinct origins. Phase 53 does not replace the existing String-keyed
configuration model or introduce an independent candidate/provenance system.

Operator diagnostics and `explain-config` may show a sanitized trace.
Confidential and secret values remain redacted. Component execution and
`ExecutionContext` receive only resolved values and capabilities; they do not
receive raw `ResolvedConfiguration`, configuration paths, layer names, source
history, or arbitrary configuration access.

### Configuration and User-Context Ownership

Phase 53 uses the following responsibility split:

- `cloud-native-component-framework`, in the `goldenport-cncf` artifact under
  `org.goldenport.cncf.config`, owns the typed public
  `apiVersion: textus/v1`, `kind: StandaloneUserProfile` document contract,
  canonical `textus.fixed-user.*` field mappings, validation rules,
  source-admission policy, and common/subsystem overlay contract;
- `simplemodeling-lib` retains ownership of the existing String-keyed
  `Configuration`, source merge, `ResolvedConfiguration`, and
  `ConfigurationTrace` facilities, plus
  `ConfigurationOrigin.ExplicitOverride` and only the minimal source metadata
  extension proven necessary by Phase 53 acceptance;
- each typed parameter-owning contract owns its canonical key spellings and
  validation; generic configuration code does not infer Textus/CNCF semantics;
- the `goldenport-cncf` runtime bootstrap owns `.textus`/`.cncf` source
  discovery, stable subsystem-identifier establishment,
  StandaloneUserProfile parsing and semantic resolution, derived Web-operation
  default contribution, configuration-resolution orchestration, Web
  operation selection, and Subsystem user-context-provider selection;
- `textus-launcher` and `cncf-launcher` own runtime artifact selection and
  exact argument forwarding; they do not parse StandaloneUserProfile or duplicate
  its schema;
- ingress adapters own authenticated/fixed/test identity evidence, while the
  CNCF runtime constructs `SecurityContext` and `ExecutionContext`; and
- Component implementations own none of the source, mode, provider, or
  provenance decisions.

Repository/package placement must preserve this split. In particular, CNCF
must not reimplement the generic trace facility, and `simplemodeling-lib` must
not acquire StandaloneUserProfile, WebApplication, Textus/CNCF, or ArtScene-specific
semantics. The wrapper launchers do not add a compile-time dependency on
`goldenport-cncf`; they select and invoke that runtime.

## Implicit Subsystem and Datastore

Direct Component launch runs the root Component inside an implicit Subsystem.
The implicit Subsystem includes the resolved dependency Component closure; it
is not assumed to contain only the root Component.

The root implicit Subsystem template owns valid mode-free `ExecutionProfile`
definitions and common dependency topology. An ExecutionProfile declares the
ingress requirement and whether current-user evidence must be fixed,
authenticated, or explicitly injected for a controlled test. A
WebApplication standalone/multi-user operation profile is one Web-specific
projection onto those ExecutionProfiles; CLI, job, and test launch do not need
to invent a WebApplication.

ExecutionProfile selection and user-context-provider resolution remain
Subsystem/ingress responsibilities. The template does not introduce a generic
ApplicationMode or SubsystemMode and does not put operating mode into the
ComponentStyle.

Datastore operation belongs to the explicit or implicit Subsystem.
Subsystem configuration owns:

- provider and binding;
- local/shared placement;
- credentials and secret references;
- path or endpoint;
- connection and pool lifecycle;
- migration/startup lifecycle; and
- operational diagnostics.

Standalone may use a persistent local datastore. Multi-user operation may
require a shared durable datastore. Both expose the same mode-free datastore
and EntityStore capabilities to Component execution.

Component-side `local-default` and `external-required` policy values are not
retained as permanent ComponentStyle parameters.

## CNCF Operational State

`~/.cncf` also contains CNCF-managed operational state, including possible:

- developer local CAR/SAR publication state;
- downloaded artifact caches;
- installed runtime state;
- local standalone/development databases;
- logs and diagnostics; and
- launcher/runtime-managed files.

Configuration files and runtime-managed state remain distinct even when they
share the `.cncf` tree.

Phase 53 begins with an inventory of exact paths, owners, lifecycle,
permissions, backup expectations, and deletion safety. It must not silently
rename, migrate, overwrite, or delete unrelated operational state while
changing configuration precedence.

Datastore pool ownership and shutdown closure remain separate Phase 54 work.

## Development-directory Launch

Phase 53 consumes, but does not reimplement, the development runtime-evidence
contract defined by
[CAR Runtime Evidence Two-Route Repair Handoff](2026-07-29-car-runtime-evidence-two-route-repair-handoff.md).
The existing `cozyPrepareRuntime` route owns preparation of
`target/cncf.d/runtime-classpath.txt`,
`target/cncf.d/car-runtime-manifest.json`, and related extensible evidence.
Missing, stale, or mixed-generation evidence fails under that contract; the
launcher does not silently fall back to an older locally published CAR.

Phase 53 adds only its ComponentStyle and implicit Subsystem projection to the
development descriptor path and verifies that development and packaged
descriptors generated from the same CML remain semantically equivalent.

## ArtScene Acceptance

ArtScene is the first production-shaped consumer.

It must:

- select `full-fledged-with-standalone` in CML;
- retain the complete `domain.full@1` behavior in both Web operations;
- remove private ApplicationMode/ComponentMode authority;
- remove private mode keys and local mode powertypes as competing authorities;
- remove hardcoded standalone UserId construction;
- remove Component-side datastore policy selection;
- remove standalone/multi-user branches from ComponentFactory, ActionCall, and
  domain operations;
- resolve `textus-art-scene` as the same stable subsystem identifier from both
  development-directory and packaged-CAR implicit Subsystem descriptors;
- receive current user and locale through the resolved ExecutionContext;
- use the common/subsystem Textus StandaloneUserProfile with an optional CNCF
  development override for standalone;
- explain the effective `textus-art-scene` subsystem-specific fixed-user
  locale and its `.textus` or `.cncf` source;
- explain the implicit standalone default and its source when no explicit Web
  mode wins;
- use the authenticated request user for multi-user;
- use Subsystem-owned local/shared datastore bindings; and
- pass both development-directory and packaged-CAR launch acceptance.

The initial matrix is:

| OperationMode | WebApplicationMode | User-context source | Component mode input | Datastore operation |
| --- | --- | --- | --- | --- |
| `develop` | `standalone` | fixed user resolved from Textus/CNCF profile layers | none | isolated persistent local development binding |
| `production` | `standalone` | fixed user resolved from Textus/CNCF profile layers | none | user-owned persistent local binding |
| `develop` | `multi-user` | authenticated request user | none | explicit shared development binding |
| `production` | `multi-user` | authenticated request user | none | explicit shared production binding |

Equivalent fixed-user and authenticated-user ExecutionContexts must produce
equivalent domain semantics for representative Component operations.

## Phase 53 Work Required by This Decision

Phase 53 must include:

1. inventory of the existing `.textus`/`.cncf` configuration sources and
   operational-state ownership affected by this phase;
2. failing-first evidence for `.textus` baseline and `.cncf` override behavior,
   existing common-field environment/argument precedence, controlled explicit
   override precedence, and rejection of PROJECT/CWD StandaloneUserProfile input;
3. preservation of the existing String-keyed generic configuration model and
   existing `ResolvedConfiguration`/`ConfigurationTrace` authority;
4. minimal trace completion for source file, `.textus`/`.cncf` layer, logical
   field, common/subsystem overlay target, override history, and effective
   source where Phase 53 acceptance requires it, plus the distinct
   `ConfigurationOrigin.ExplicitOverride`;
5. one canonical public `textus.*` spelling for each Phase 53 user-facing
   parameter and no duplicate `cncf.*` spelling for the same semantic;
6. the `goldenport-cncf` `org.goldenport.cncf.config`
   `StandaloneUserProfile` schema,
   `~/.textus/user-profile.yaml` and `~/.cncf/user-profile.yaml` discovery,
   common/subsystem overlay resolution, and published
   `textus.fixed-user.*` parameters;
7. stable subsystem identification from the authoritative explicit or implicit
   Subsystem descriptor, with development-directory and packaged-CAR parity;
8. use of the established `cozyPrepareRuntime` development evidence contract,
   without reimplementing it, plus Phase 53 ComponentStyle/implicit Subsystem
   projection and semantic parity with the packaged descriptor route;
9. resolution of `textus.web.application-mode` before StandaloneUserProfile and
   traceable normal standalone default selection;
10. ComponentStyle catalog, capability expansion, CML projection, and
   requirement matching;
11. ingress-independent fixed/authenticated user-context resolution,
    ExecutionContext construction, implicit Subsystem ExecutionProfiles, and
    Subsystem-owned datastore resolution;
12. secret-safe diagnostics, proof that raw trace and paths do not enter
    Component APIs, development/package parity, and ArtScene acceptance; and
13. post-implementation promotion of verified behavior into normative
    configuration, ComponentStyle, ExecutionContext, WebApplication, launcher,
    and ArtScene documents.

The canonical generic configuration implementation currently lives in
`simplemodeling-lib`. Phase 53 changes it only if the existing provenance
facility cannot represent the minimal evidence required above. It does not
perform a generic typed-key migration or admit every current configuration API
consumer into the phase. CNCF must not reimplement the generic trace facility.
The public StandaloneUserProfile schema and semantic resolver remain in
`cloud-native-component-framework`.

## Documentation Lifecycle

The order remains:

1. journal records the discussion and decisions;
2. the Phase 53 notes proposal and checklist define the implementation plan;
3. failing-first Executable Specifications fix observable acceptance;
4. implementation and review establish actual behavior;
5. full validation proves cross-repository behavior; and
6. only then are `docs/design` and `docs/spec` updated as normative authority.

Current normative configuration documents still describe `.cncf` as the
weaker compatibility input. That documented behavior remains the current
effective contract until Phase 53 implementation verifies the replacement and
the normative documents are promoted. This journal records the selected future
contract; it does not by itself change runtime behavior or normative
authority. Phase 53 must not claim completion while the selected direction
exists only in journals, notes, phase documents, or tests.

Only the Phase 53 delivery scope in this journal is promoted through that
lifecycle. The future configuration-framework candidates are assigned to
Phase 55, where a separate specification decision must select or reject them
before implementation.

## Deferred Work

The following remain outside Phase 53:

- Metadata Factory registration and third-party ComponentStyle contribution;
- generic typed parameter and binding keys;
- a generic Global/Unqualified/Subsystem/WebApplication qualifier taxonomy;
- multi-Subsystem candidate-set resolution;
- generic namespace catalogs and alias-normalization policy;
- new canonical binding-string or environment-variable codecs;
- repository-wide migration away from String-keyed configuration APIs;
- generalized fixed-user authorization-policy configuration;
- generic user-data migration infrastructure;
- unrelated Entity/collection identity work;
- datastore pool lifecycle work assigned to Phase 54;
- arbitrary runtime mutation of ComponentStyle definitions; and
- silent compatibility layers that preserve Component-visible mode branching.

The generic configuration items in this list are scheduled as Phase 55
candidates. This assignment does not approve their names, contracts, or
implementation.

## Current Handoff

The Phase 53 planning surfaces synchronize only the Phase 53 delivery scope:

1. `docs/phase/phase-53.md`;
2. `docs/phase/phase-53-checklist.md`;
3. `docs/notes/cml-component-style-execution-context-capability-specification-proposal.md`;
4. the CNCF strategy/roadmap entries that define Phase 53 and its displaced
   successors.

The future generic configuration-framework candidates are recorded separately
in the Phase 55 frame. Its planning references are:

1. `docs/phase/phase-55.md`;
2. `docs/phase/phase-55-checklist.md`; and
3. the generic configuration candidate sections retained in this journal.

Normative `docs/design` and `docs/spec` remain intentionally excluded from
this planning synchronization. They are written only after implementation,
review, and executable acceptance establish the actual contract.
Implementation begins only after Phase 52 closes and Phase 53 starts.

## 2026-08-01 CS-05B supersession

The owner corrected the selected boundary after this journal's earlier
future-contract discussion. Phase 53 implements only strict
`StandaloneUserProfile` decoding and ordered HOME admission, plus the existing
trace fields (`key`, `origin`, `sourceType`, `sourceId`, and `history`). It does
not implement effective field binding or precedence, detailed layer/target/
subsystem/field-path provenance, `ConfigurationOrigin.ExplicitOverride`, an
ambient OS-environment codec, or a parallel profile-provenance API.

Those items are deferred to Phase 55's ConfigurationBinding work. The
authoritative state is the Phase 53 checklist and the Phase 55 plan/checklist;
the earlier precedence and provenance passages in this journal are superseded
planning discussion, not Phase 53 implementation commitments.
