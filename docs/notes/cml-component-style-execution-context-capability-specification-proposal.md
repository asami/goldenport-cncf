# CML ComponentStyle, ExecutionContext, and Capability Specification Proposal

Status: non-normative implementation proposal

Phase: [Phase 53](../phase/phase-53.md)

Source consideration:
[Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation](../journal/2026/07/2026-07-30-phase-53-component-style-execution-context-configuration-consolidation.md)

Earlier consideration:
[CML Application Mode Capability Consideration](../journal/2026/07/2026-07-30-cml-application-mode-capability-consideration.md)

## Authority and lifecycle

This note is the working implementation proposal for Phase 53. It is not a
normative CNCF design or specification.

Phase 53 uses this order:

1. preserve the proposal in `docs/notes`;
2. implement it with failing-first Executable Specifications;
3. verify generated/runtime behavior, ArtScene, and the extension-ready
   metadata boundary; and
4. only then promote observed behavior into `docs/design` and `docs/spec`.

Until step 4, existing design and specification remain authoritative where
they conflict with this note.

## Problem

ArtScene is a full-fledged, user-scoped domain component. It uses Entity,
Aggregate, Command/Query separation, domain events, projections, persistence,
transactions, and optimistic concurrency as one coherent model.

ArtScene must support two Web application operations:

- standalone operation with one configured fixed user; and
- multi-user operation with an authenticated request user.

Internally, both operations use the same user-scoped domain and component
implementation. Standalone is not a reduced domain model. It is multi-user
semantics with one fixed current user assigned to ExecutionContext.

The previous proposal incorrectly promoted `standalone` and `multi-user` into
a Component-visible `SubsystemMode`, allowed mode-specific Component
realization, and passed selected mode into ComponentFactory. That design would
make mode branching a permanent Component contract and is superseded.

## Non-negotiable Component boundary

Component implementation must not know operating mode.

```text
operation/configuration
  -> Subsystem, Ingress, Security, and WebApplication resolution
  -> resolved SecurityContext, RuntimeContext, and typed policies
  -> ExecutionContext
  -> mode-free Component execution
```

Permanent Component APIs, generated internal DSLs, ComponentFactory hooks, and
ActionCall implementations MUST NOT:

- receive or expose `ApplicationMode`, `ComponentMode`, `SubsystemMode`,
  `WebApplicationMode`, or `OperationMode`;
- read mode or datastore-policy configuration keys;
- branch on `standalone` or `multi-user`;
- create or hardcode a standalone UserId;
- choose a datastore, provider, path, endpoint, or pool from mode;
- derive locale, timezone, authorization, or user ownership from mode; or
- fork domain behavior by operating mode.

Component implementation consumes only resolved execution facts:

- current principal/user and capabilities;
- effective locale, timezone, and formatting;
- datastore/entity-store bindings;
- UnitOfWork and transaction semantics;
- resource access;
- authorization;
- typed, mode-free domain policies; and
- other runtime capabilities carried by ExecutionContext.

Mode-aware logic is limited to launcher, Subsystem assembly/resolution,
Ingress/Security resolution, ExecutionContext construction, WebApplication
adapters, and diagnostics. Web-specific behavior may use WebApplicationMode
inside the Web boundary, but that value is not forwarded into Component
execution.

## Selected model

```text
CML
  -> selects CNCF ComponentStyle
  -> generated Component descriptor
       -> provided ComponentCapabilities
       -> required SubsystemCapabilities

direct Component launch
  -> load root ImplicitSubsystemTemplate
  -> resolve dependency Component closure
  -> select WebApplication operation profile

standalone profile
  -> Subsystem resolves fixed-user evidence
  -> resolve ~/.textus FixedUserProfile baseline
  -> apply always-admitted ~/.cncf FixedUserProfile override
  -> fixed-user context provider
  -> SecurityContext + effective formatting

multi-user profile
  -> ignore both FixedUserProfile documents
  -> Subsystem resolves authenticated-user evidence
  -> authenticated principal/user preferences
  -> SecurityContext + effective formatting

both
  -> ExecutionContext
  -> identical Component execution contract
```

The Subsystem remains user-scoped and multi-user-capable in both profiles.
Standalone is a WebApplication/launch operation profile and a fixed-user
ExecutionContext binding, not a Component mode and not a separate Subsystem
user model.

## Terminology and ownership

`ComponentStyle` is a versioned CNCF catalog entry describing a standard
bundle of Component-provided capabilities.

`ComponentCapability` is a typed, versioned facility a Component provides to
its containing Subsystem or application assembly.

`DomainCapability` describes a domain-model facility. Detailed capabilities
may be grouped in a versioned `DomainCapabilityBundle`.

`user.multi-user@1` means that Component state and operations remain correctly
user-scoped for different current users. It does not require the Component to
authenticate users.

`user.fixed-context-compatible@1` means that the same Component implementation
can operate when the current user is supplied by a fixed-user ExecutionContext
provider. It does not authorize Component mode branching.

`ComponentSubsystemRequirement` is a typed runtime facility the Component
requires from its containing Subsystem.

`SubsystemCapability` is a runtime facility the resolved Subsystem provides,
including current-user context, datastore semantics, authorization, and
resource bindings.

`WebApplicationMode` remains the Web-specific `standalone` / `multi-user`
concept. It controls Web ingress and supplies Web-specific identity evidence
to the Subsystem. The Subsystem owns user-context-provider resolution.
`WebApplicationMode` remains inside the WebApplication boundary.

`FixedUserProfile` is stable user identity and preference data used to build
the current ExecutionContext for standalone or other fixed-user execution.

`ImplicitSubsystemTemplate` is packaged with a directly launchable root
Component. It defines common dependency topology and valid mode-free
ExecutionProfiles. WebApplication standalone/multi-user profiles project onto
those ExecutionProfiles. It has no style-level default profile.

`OperationMode` retains `production`, `demo`, `develop`, and `test` for
launcher/runtime execution posture. It is independent from WebApplicationMode
and is not exposed to Component domain logic. Any posture difference needed by
a Component is resolved into a typed, mode-free execution fact before
Component execution.

Phase 53 does not introduce a generic `ApplicationMode` or a canonical
`SubsystemMode`.

## Component-provided capability model

The Phase 53 ArtScene style is:

```text
ComponentStyle id: full-fledged-with-standalone

provided capabilities:
  domain.full@1
  user.multi-user@1
  user.fixed-context-compatible@1

required Subsystem capabilities:
  user-context.current@1
  datastore.persistent@1
  datastore.transactional@1
  datastore.optimistic-concurrency@1
```

The name means that a full-fledged multi-user-capable domain Component can run
unchanged in standalone operation because CNCF supplies one fixed current user
through ExecutionContext.

Runtime and generation consume structured capability metadata. They never
infer semantics by parsing the style identifier.

### DomainCapability bundle

`domain.full` is a named bundle, not a boolean or an opaque claim. The initial
`domain.full@1` bundle expands to:

- `domain.entity@1`
- `domain.aggregate@1`
- `domain.command@1`
- `domain.query@1`
- `domain.domain-event@1`
- `domain.projection@1`
- `domain.persistence@1`
- `domain.transaction@1`
- `domain.optimistic-concurrency@1`

An illustrative CNCF definition is:

```yaml
apiVersion: cncf.textus/v1
kind: ComponentCapabilityBundle

metadata:
  family: domain
  id: full
  version: 1
  provider: cncf

provides:
  - domain.aggregate@1
  - domain.command@1
  - domain.domain-event@1
  - domain.entity@1
  - domain.optimistic-concurrency@1
  - domain.persistence@1
  - domain.projection@1
  - domain.query@1
  - domain.transaction@1
```

Descriptors retain both bundle identity/version and the deterministic expanded
set. Changing bundle membership creates another bundle version.

### ComponentStyle catalog entry

```yaml
apiVersion: cncf.textus/v1
kind: ComponentStyle

metadata:
  id: full-fledged-with-standalone
  version: 1
  provider: cncf

provides:
  componentCapabilities:
    bundles:
      - domain.full@1
    capabilities:
      - user.fixed-context-compatible@1
      - user.multi-user@1

requires:
  subsystemCapabilities:
    - datastore.optimistic-concurrency@1
    - datastore.persistent@1
    - datastore.transactional@1
    - user-context.current@1
```

The catalog entry contains no mode parameter, mode realization hook,
datastore provider, credentials, fixed UserId, locale, or preferred operation
profile.

## Requirement/capability contract

Compatibility is checked in both directions:

```text
assembly-required ComponentCapabilities
  subset-of Component-provided ComponentCapabilities

Component-required SubsystemCapabilities
  subset-of Subsystem-provided SubsystemCapabilities
```

For standalone ArtScene:

```text
WebApplication standalone profile requires:
  domain.full@1
  user.multi-user@1
  user.fixed-context-compatible@1

ArtScene provides all three.

ArtScene requires:
  user-context.current@1
  datastore.persistent@1
  datastore.transactional@1
  datastore.optimistic-concurrency@1

FixedUserContextProvider and local datastore binding provide them.
```

For multi-user ArtScene:

```text
WebApplication multi-user profile requires:
  domain.full@1
  user.multi-user@1

ArtScene provides both.

ArtScene requires the same mode-free Subsystem capabilities.
AuthenticationProvider and shared datastore binding provide them.
```

Multi-user deployment may additionally require
`datastore.shared-durable@1` as an assembly/Subsystem operational requirement.
That requirement is not selected inside ArtScene.

## CML declaration

CML selects only ComponentStyle:

```dox
# COMPONENT

## ArtScene

### STYLE

full-fledged-with-standalone
```

CML does not declare fixed user, WebApplicationMode, locale, datastore
provider, or application operation default.

Required generation semantics are:

- explicit `COMPONENT` owns the style selection;
- the identifier resolves through the CNCF catalog;
- CML does not redefine style capabilities or requirements;
- unknown or unavailable style fails generation;
- `project.yaml` does not duplicate the selection; and
- explicit Component generation remains independent of service, operation,
  entity, and other CML declarations.

## Generated Component descriptor

An illustrative generated projection is:

```json
{
  "schemaVersion": 2,
  "name": "textus-art-scene",
  "componentStyle": {
    "id": "full-fledged-with-standalone",
    "provider": "cncf",
    "version": 1
  },
  "provides": {
    "componentCapabilities": {
      "bundles": [
        "domain.full@1"
      ],
      "effective": [
        "domain.aggregate@1",
        "domain.command@1",
        "domain.domain-event@1",
        "domain.entity@1",
        "domain.optimistic-concurrency@1",
        "domain.persistence@1",
        "domain.projection@1",
        "domain.query@1",
        "domain.transaction@1",
        "user.fixed-context-compatible@1",
        "user.multi-user@1"
      ]
    }
  },
  "requires": {
    "subsystemCapabilities": [
      "datastore.optimistic-concurrency@1",
      "datastore.persistent@1",
      "datastore.transactional@1",
      "user-context.current@1"
    ]
  }
}
```

The descriptor contains no Component mode, mode behavior, fixed UserId,
locale, or datastore policy. Development and packaged descriptors generated
from the same inputs are semantically identical.

## ExecutionContext absorption

### Standalone path

```text
standalone Web, CLI, job, or other fixed-user ingress
  -> Subsystem user-context-provider resolution
  -> stable Subsystem identity
  -> FixedUserProfileResolver
       -> ~/.textus common and matching subsystem fields
       -> ~/.cncf common and matching subsystem fields
       -> admitted common environment/argument input
       -> controlled explicit runtime/test override
  -> Principal + SecurityContext
  -> principal locale/timezone preferences
  -> RuntimeContext.FormattingContext
  -> resolved datastore and UnitOfWork capabilities
  -> ExecutionContext
  -> Component
```

### Multi-user path

```text
multi-user Web or another authenticated ingress
  -> authenticated invocation identity
  -> Subsystem user-context-provider resolution
  -> authenticated Principal + SecurityContext
  -> account locale/timezone preferences
  -> RuntimeContext.FormattingContext
  -> resolved datastore and UnitOfWork capabilities
  -> ExecutionContext
  -> Component
```

The Component-facing ExecutionContext contract is identical. The Component
must not be able to determine which construction path was used.

The effective current user must be available through canonical SecurityContext
and ExecutionContext access. Locale/timezone must be available through the
effective runtime formatting context. Datastore access must use the resolved
DataStoreSpace/EntityStoreSpace.

## Stable Subsystem identity

The stable Subsystem identifier is established before any subsystem-specific
FixedUserProfile or Web-operation lookup. Its authority is the containing
`GenericSubsystemDescriptor.subsystemName`:

- an explicit Subsystem uses its declared `subsystemName`;
- an implicit Subsystem uses the `subsystemName` projected by
  `GenericSubsystemDescriptor.fromComponentDescriptor`;
- that projection uses `ComponentDescriptor.subsystemName` when supplied,
  otherwise `ComponentDescriptor.componentName`, and only then the existing
  descriptor-name fallback; and
- repository paths, directory names, display names, CML headings, launcher
  arguments, and artifact coordinates do not independently redefine it.

Missing or conflicting descriptor-owned identity fails before
subsystem-qualified profile resolution. Development-directory and packaged-CAR
descriptors generated from the same CML must project the same stable
Subsystem identity.

## FixedUserProfile configuration and provenance

Normal standalone operation is configured in:

```text
~/.textus/user-profile.yaml
```

The public typed document is:

```yaml
apiVersion: textus/v1
kind: FixedUserProfile

user:
  id: local-user
  displayName: Local User
  locale: ja
  timezone: Asia/Tokyo

subsystems:
  textus-art-scene:
    locale: ja
```

`FixedUserProfile` is bootstrap configuration for the fixed current user. It
is distinct from the persisted `UserProfile` domain Entity supplied by
`textus-user-account`. It contains no WebApplicationMode, OperationMode,
datastore setting, credential, pool, diagnostic control, or CNCF
implementation parameter.

A partial higher-precedence override is always admitted from:

```text
~/.cncf/user-profile.yaml
```

The CNCF file is an overlay, not a second independent profile authority. It
uses the same schema and may be partial:

```yaml
apiVersion: textus/v1
kind: FixedUserProfile

user:
  id: local-development-user

subsystems:
  textus-art-scene:
    locale: en
```

Admission of `.cncf/user-profile.yaml` is not gated by `OperationMode`. Both
HOME profile documents participate only in fixed-user resolution. Multi-user
operation uses authenticated-user evidence and ignores both documents.

The typed document collection is named `subsystems`. Lookup uses the stable
Subsystem identifier established from the explicit or implicit Subsystem
descriptor, not an application display name, directory name, launcher
argument, or artifact coordinate.

The source-admission contract is:

| Source | FixedUserProfile admission |
| --- | --- |
| Contract defaults | Only for fields whose public schema defines a default |
| `HOME/.textus/user-profile.yaml` | Normal public baseline |
| `HOME/.cncf/user-profile.yaml` | Always-admitted higher-precedence overlay using the same schema; fixed-user resolution only |
| `PROJECT/.textus`, `PROJECT/.cncf` | Not admitted |
| `CWD/.textus`, `CWD/.cncf` | Not admitted |
| Environment | Existing canonical `textus.fixed-user.*` common-field input only |
| Explicit arguments | Existing canonical `textus.fixed-user.*` common-field input only |
| Explicit runtime/test override | Controlled selected-resolution injection recorded as `ConfigurationOrigin.ExplicitOverride` |

For an explicit override, `sourceType` distinguishes `runtime` and `test`, and
`sourceId` identifies the controlled caller or executable specification.

Resolution is a field-by-field overlay from low to high precedence:

1. explicit contract default for a field that defines one;
2. `~/.textus/user-profile.yaml` common value;
3. matching `~/.textus/user-profile.yaml` subsystem value;
4. `~/.cncf/user-profile.yaml` common value;
5. matching `~/.cncf/user-profile.yaml` subsystem value;
6. admitted common-field environment value;
7. admitted common-field explicit argument;
8. controlled explicit runtime/test override; and
9. missing required identity fails before Component invocation.

The initial external field mappings are:

| Typed field | Canonical parameter |
| --- | --- |
| `user.id` | `textus.fixed-user.id` |
| `user.displayName` | `textus.fixed-user.display-name` |
| `user.locale` | `textus.fixed-user.locale` |
| `user.timezone` | `textus.fixed-user.timezone` |

Every effective field retains configuration provenance:

- admitted source kind;
- `.textus` or `.cncf` layer;
- source path or explicit input identity;
- common or subsystem-specific target;
- stable Subsystem identity when applicable;
- exact logical field path or canonical parameter;
- overridden history; and
- final effective source.

Diagnostics expose non-secret effective values and this field-level provenance.
Secret values remain redacted while their source identity remains traceable.
Component execution receives only the resolved values, never source paths,
layer names, or configuration keys.

The existing `ResolvedConfiguration` and `ConfigurationTrace` remain the
canonical resolved-value and provenance authority. Phase 53 adds
`ConfigurationOrigin.ExplicitOverride` and only the source metadata needed to
distinguish the file, layer, field path, overlay target, and derived-default
evidence. It does not introduce typed generic keys, qualifiers, candidate
sets, namespace catalogs, generic aliases, or new binding codecs; those remain
Phase 55 candidates.

Resolved fixed-user identity must remain stable across restart. Changing an ID
that owns persisted data is a user-data migration, not an ordinary preference
change. Any override that changes UserId therefore requires isolated data
unless an explicit data migration is intended.

Multi-user operation never falls back to fixed-user configuration. Missing or
failed authentication remains an authentication failure.

## Public Web operation selection

Web operation selection is resolved before FixedUserProfile. It belongs to
the CNCF runtime bootstrap and the implicit or explicit Subsystem
WebApplication profile, not to FixedUserProfile, ComponentStyle, or a wrapper
launcher.

The canonical public parameter is:

```text
textus.web.application-mode
```

An admitted `.textus` source provides the public baseline. `.cncf`,
environment, arguments, and controlled explicit overrides follow the generic
source precedence. A subsystem-specific binding uses the same stable Subsystem
identifier as FixedUserProfile. No parallel
`cncf.web.application-mode` parameter is introduced.

For normal Textus direct-Component launch, CNCF contributes a traceable
`standalone` default only when no explicit value wins, the implicit Subsystem
has a valid standalone ExecutionProfile, and the Component provides
`user.fixed-context-compatible@1`. Otherwise absence of a valid selection
fails instead of silently choosing a mode.

The contributed value records:

```text
textus.web.application-mode = standalone
origin = Default
sourceType = derived-default
sourceId = textus-direct-component-standalone
```

Its evidence identifies both the implicit Subsystem ExecutionProfile and the
Component capability that admitted the default.

A `standalone` result starts fixed-user resolution. A `multi-user` result
requires authenticated-user evidence and does not read or fall back to
FixedUserProfile.

## Textus and CNCF configuration layers

The two configuration directories are used together:

| Directory | Responsibility |
| --- | --- |
| `.textus` | Canonical ordinary user/operator configuration |
| `.cncf` | Framework-internal, development, diagnostic, and operational override/configuration |

Both are always eligible sources; `OperationMode` does not switch either
directory on or off. A typed contract first admits or rejects sources, then
the generic low-to-high source order applies to those admitted inputs:

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

FixedUserProfile narrows this general order to the HOME-centered admission
contract above. Other typed contracts may admit PROJECT or CWD input.

Directory ownership and parameter namespace are independent:

- a public semantic uses one canonical `textus.*` parameter even when a value
  comes from `.cncf`;
- `cncf.*` is reserved for internal, diagnostic, development, or
  implementation controls; and
- the same semantic is not duplicated as separate `textus.*` and `cncf.*`
  parameters to obtain precedence.

Configuration files and CNCF-managed operational state remain distinct even
when both live under `.cncf`. Phase 53 inventories affected paths, owners,
lifecycle, permissions, backup expectations, and deletion safety. It does not
silently rename, migrate, overwrite, or delete unrelated CAR/SAR state,
caches, runtime state, databases, logs, or launcher-managed files.

## ComponentFactory and implementation contract

The generated ComponentFactory hook may provide typed, side-effect-free
Component parameters, mode-free Subsystem requirements, and implementation
evidence for provided DomainCapabilities.

It may not:

- receive WebApplicationMode or another operating-mode value;
- change style/provider/bundle identity;
- add or remove provided user-operation capabilities at runtime;
- select a user-context provider;
- read common or subsystem-specific FixedUserProfile configuration;
- choose Subsystem datastore operation;
- branch on fixed versus authenticated principal origin; or
- return mode-specific Component parameters.

Final capability validation may occur after ComponentFactory loading but before
component activation, datastore creation, server binding, or job admission.

If a temporary migration adapter is unavoidable, it must:

- live outside the permanent Component execution API;
- be marked deprecated and internal;
- have a named removal item;
- not appear in CML, ComponentStyle metadata, generated DSL, or normative
  design/specification; and
- not justify new Component mode branches.

## Implicit Subsystem template

Direct Component launch creates a Subsystem rooted at the selected Component
and includes its resolved dependency closure. It is not a single-Component
container.

The root template owns mode-free ExecutionProfiles and common dependency
topology. An ExecutionProfile declares ingress requirements and whether
current-user evidence must be fixed, authenticated, or explicitly injected
for a controlled test. WebApplication profiles project onto these
ExecutionProfiles; CLI, job, and test ingress do not invent a WebApplication.

An illustrative projection is:

```yaml
apiVersion: cncf.textus/v1
kind: ImplicitSubsystemTemplate

metadata:
  name: textus-art-scene

rootComponent: textus-art-scene

components:
  common:
    - textus-art-scene
    - textus-ai-runtime
    - textus-scraper
    - textus-toolchain-runner
    - textus-user-notification

executionProfiles:
  fixed-user:
    currentUserEvidence: fixed
    datastoreBinding: local-application
    requires:
      rootComponentCapabilities:
        - domain.full@1
        - user.fixed-context-compatible@1
        - user.multi-user@1

  authenticated-user:
    currentUserEvidence: authenticated
    components:
      - textus-user-account
    datastoreBinding: shared-application
    requires:
      rootComponentCapabilities:
        - domain.full@1
        - user.multi-user@1

webApplicationProfiles:
  standalone:
    mode: standalone
    executionProfile: fixed-user

  multi-user:
    mode: multi-user
    executionProfile: authenticated-user
```

The exact serialization is verified during implementation; the ownership
contract is fixed here. ExecutionProfile selection and user-context-provider
resolution belong to the Subsystem/ingress boundary. The template does not
define a generic ApplicationMode, ComponentMode, or SubsystemMode and does not
put operating mode into ComponentStyle.

The normal direct-Component `standalone` default is contributed by the CNCF
runtime under the conditions defined in Public Web operation selection. It is
not stored in FixedUserProfile and is not ComponentStyle semantics.

Dependency Components do not contribute or merge their own implicit templates.
Only the direct root template participates, while the resolved Component
dependency closure remains part of the implicit Subsystem.

## Subsystem assemblies

### Standalone Web application operation

```yaml
apiVersion: cncf.textus/v1
kind: SubsystemAssembly

metadata:
  name: textus-art-scene

rootComponent: textus-art-scene

components:
  - name: textus-art-scene
    requires:
      componentCapabilities:
        - domain.full@1
        - user.fixed-context-compatible@1
        - user.multi-user@1
  - name: textus-ai-runtime
  - name: textus-scraper
  - name: textus-toolchain-runner
  - name: textus-user-notification

webApplication:
  mode: standalone
  executionProfile: fixed-user

datastores:
  application:
    provider: embedded-sql
    scope: local
    durability: persistent
    path:
      source: user-data-directory
```

### Multi-user Web application operation

```yaml
apiVersion: cncf.textus/v1
kind: SubsystemAssembly

metadata:
  name: textus-art-scene

rootComponent: textus-art-scene

components:
  - name: textus-art-scene
    requires:
      componentCapabilities:
        - domain.full@1
        - user.multi-user@1
  - name: textus-ai-runtime
  - name: textus-scraper
  - name: textus-toolchain-runner
  - name: textus-user-account
  - name: textus-user-notification

webApplication:
  mode: multi-user
  executionProfile: authenticated-user

datastores:
  application:
    provider: external-sql
    scope: shared
    binding:
      source: configuration
      key: artscene.application-datastore

security:
  authentication:
    providerComponent: textus-user-account
    required: true
```

Assembly input selects providers and bindings. It does not self-declare the
capabilities those providers offer. CNCF reads provider/CAR metadata and
validates the effective SubsystemCapabilities.

## Datastore ownership

Datastore operation belongs to the explicit or implicit Subsystem.
Subsystem configuration owns:

- provider and binding;
- local/shared placement;
- credentials and secret references;
- path or endpoint;
- connection and pool lifecycle;
- migration/startup lifecycle; and
- operational diagnostics.

Component code and ComponentFactory do not choose any of these values.

The earlier `local-default` and `external-required` values mixed a placement
default with a hard requirement and are not retained as ComponentStyle
parameters.

Standalone may use a persistent local datastore. Multi-user operation may
require a shared durable datastore. Both provide the same mode-free datastore
interfaces to Component execution.

## WebApplication boundary

`WebApplication` and `WebApplicationMode` remain valid concepts.

WebApplicationMode owns Web ingress differences including:

- fixed-user versus authenticated-user identity-evidence requirement;
- projection onto the corresponding Subsystem ExecutionProfile;
- account/session entry behavior;
- Web navigation and presentation differences;
- Web cache/security behavior; and
- application-operation diagnostics.

The Web adapter may consume WebApplicationMode. Domain Component operations do
not.

WebApplicationMode is not copied into Component parameters, ComponentFactory
input, ActionCall configuration, or Entity/Aggregate logic.

## Resolved evidence

An illustrative standalone resolution record is:

```json
{
  "subsystem": "textus-art-scene",
  "webApplicationMode": "standalone",
  "userContext": {
    "provider": "fixed-user",
    "principalId": "local-development-user",
    "locale": "en",
    "provenance": {
      "principalId": {
        "source": "cncf-user-profile",
        "path": "~/.cncf/user-profile.yaml",
        "target": "common",
        "key": "user.id"
      },
      "locale": {
        "source": "cncf-user-profile",
        "path": "~/.cncf/user-profile.yaml",
        "target": "subsystem",
        "subsystem": "textus-art-scene",
        "key": "subsystems.textus-art-scene.locale"
      }
    }
  },
  "componentExecution": {
    "modeExposed": false
  },
  "requirementMatches": [
    {
      "requirement": "user.fixed-context-compatible@1",
      "providedBy": "textus-art-scene",
      "status": "satisfied"
    },
    {
      "requirement": "user-context.current@1",
      "providedBy": "fixed-user-context-provider",
      "status": "satisfied"
    },
    {
      "requirement": "datastore.persistent@1",
      "providedBy": "subsystem-datastore:application",
      "status": "satisfied"
    }
  ],
  "status": "ready"
}
```

Resolved evidence may expose boundary mode for operators. It must also prove
that no mode value entered the Component execution contract.

## Development-directory launch

A source-directory launch does not require `buildCar`. Phase 53 consumes the
existing `cozyPrepareRuntime` development-evidence route. It does not define a
parallel preparation mechanism.

The admitted route creates:

- `target/cncf.d/runtime-classpath.txt`;
- `target/cncf.d/car-runtime-manifest.json`;
- the development Component descriptor and implicit Subsystem projection; and
- any additional versioned evidence owned by the same route.

The launcher consumes one coherent generation. Missing, mixed-generation, or
stale evidence fails under the development runtime-evidence contract without
falling back to an older locally published CAR. Development and packaged
descriptors generated from the same CML must carry semantically equivalent
ComponentStyle, capability, and stable Subsystem identity evidence.

An illustrative direct launch is:

```text
cncf --web-application-mode standalone . server
```

When omitted, the traceable direct-Component default defined in Public Web
operation selection may supply `standalone`. FixedUserProfile does not own
that default. Exact CLI spelling is verified during implementation.

## ArtScene migration and acceptance

ArtScene selects `full-fledged-with-standalone` in CML and keeps the complete
`domain.full@1` domain contract in both operations.

Phase 53 removes from ArtScene Component implementation:

- private `ApplicationMode`;
- `textus.artscene.application.mode`;
- `textus.component.art-scene.datastores.application.policy`;
- hardcoded `STANDALONE_SUBJECT_ID`;
- ComponentFactory mode resolution;
- mode-dependent datastore validation;
- domain-operation `standalone` / `multi-user` branches; and
- mode-dependent user and locale construction.

Web-only presentation differences move to the WebApplication adapter.
User/principal, locale/timezone, authorization, datastore, and mode-dependent
policies resolve before Component execution.

The acceptance matrix is:

| OperationMode | WebApplicationMode | User-context provider | Domain contract | Component mode input | Datastore operation |
| --- | --- | --- | --- | --- | --- |
| `develop` | `standalone` | fixed user with common/subsystem overlay | `domain.full@1` | none | isolated persistent local development binding |
| `production` | `standalone` | fixed user with common/subsystem overlay | `domain.full@1` | none | user-owned persistent local binding |
| `develop` | `multi-user` | authenticated user | `domain.full@1` | none | explicit shared development binding |
| `production` | `multi-user` | authenticated user | `domain.full@1` | none | explicit shared production binding |

The same representative Component operations must produce equivalent domain
semantics when supplied equivalent ExecutionContexts through fixed-user and
authenticated-user construction paths.

## Diagnostics and inspection

Operator-facing inspection exposes:

- selected ComponentStyle/provider/schema;
- declared bundle and effective ComponentCapabilities;
- ComponentSubsystemRequirements;
- selected WebApplicationMode and source;
- selected user-context provider;
- effective non-secret fixed-user fields and their source path, layer,
  common/subsystem target, stable Subsystem identity, key, and field-level
  provenance;
- current principal identity and effective locale without secret data;
- effective datastore binding and SubsystemCapabilities;
- requirement/capability matches;
- implicit/explicit Subsystem origin; and
- development evidence freshness.

Component-facing diagnostics and generated DSL do not expose mode.

## Future Metadata Factory extension direction

Phase 53 implements CNCF built-in ComponentStyles only. Metadata Factory style
contribution remains a future development item.

Phase 53 fixes an extension-ready provider/schema/capability contract so future
work can add:

- registration and discovery;
- dependency packaging;
- provider loading;
- conflict resolution;
- bundle contribution/versioning; and
- generator/runtime parity acceptance.

A future provider never silently replaces a CNCF built-in.

## Compatibility and migration

Phase 53 does not preserve parallel mode or datastore-policy authorities.

Legacy Component-private mode keys and powertypes are migration inputs only and
are removed from the permanent contract. Canonical spelling is `multi-user`,
not `multi_user`.

`WebApplication` and `WebApplicationMode` remain. The removed authority is
Component-side mode selection and branching.

Temporary migration adapters do not establish compatibility guarantees and do
not enter normative specification.

## Implementation ownership

| Repository | Phase 53 responsibility |
| --- | --- |
| `cozy` | CML style selection, typed capability model, validation, and descriptor projection |
| `sbt-cozy` | extend the existing `cozyPrepareRuntime` route with development descriptor/implicit-template evidence and freshness |
| `simplemodeling-lib` | preserve String-keyed configuration and trace authority; add `ExplicitOverride` and only the minimal source metadata required by Phase 53 |
| `cloud-native-component-framework` | built-in catalog, capability matching, stable Subsystem identity, FixedUserProfile parsing/semantic resolution, fixed/authenticated ExecutionContext construction, Web operation default contribution, and diagnostics |
| `cncf-launcher` | runtime artifact selection, exact argument forwarding, and development-directory launch |
| `textus-launcher` | runtime artifact selection and exact argument forwarding without duplicating FixedUserProfile or Web-operation semantics |
| `textus-art-scene` | remove mode-aware Component implementation and prove identical mode-free execution |

`simplemodeling` is admitted only if implementation proves that affected
semantic-model ownership belongs there.

## Verification and promotion gate

Executable Specifications cover:

- built-in style discovery and unknown-style failure;
- `domain.full@1` deterministic expansion;
- generated descriptor determinism and development/packaged parity;
- forward and reverse capability matching;
- common fixed-user resolution;
- subsystem-specific fixed-user field overlay using stable Subsystem identity;
- always-admitted `~/.textus` baseline followed by field-level `~/.cncf`
  higher-precedence override during fixed-user resolution;
- rejection of PROJECT/CWD FixedUserProfile documents;
- common-field environment/argument precedence and controlled
  `ConfigurationOrigin.ExplicitOverride`;
- complete FixedUserProfile exclusion from multi-user resolution;
- resolved-value provenance through the existing trace authority for source
  path/input, layer, common/subsystem target, stable Subsystem identity, and
  field key;
- canonical `textus.web.application-mode` resolution before FixedUserProfile
  and traceable conditional standalone default contribution;
- stable fixed UserId and locale propagation through SecurityContext and
  RuntimeContext.FormattingContext;
- authenticated-user propagation through the same Component-facing contract;
- no fixed-user fallback in multi-user operation;
- no mode field in Component descriptor parameters, ComponentFactory input,
  generated internal DSL, ActionCall contract, or domain policy, including
  OperationMode;
- no Component-side mode/config-key lookup, hardcoded standalone UserId, or
  mode branch;
- fixed-user and authenticated-user execution of the same representative
  Component operations;
- WebApplication-only mode differences;
- provider capabilities discovered from provider/CAR metadata;
- Subsystem-owned datastore operation;
- root-based implicit Subsystem dependency closure;
- source launch without `buildCar`;
- use of the existing `cozyPrepareRuntime` evidence route;
- stale-evidence rejection; and
- the complete ArtScene acceptance matrix.

Phase 53 verification must also show that typed generic configuration keys,
generic qualifier/candidate resolution, namespace catalogs, general alias
normalization, and new binding/environment codecs were not introduced. Those
remain Phase 55 specification candidates.

After verified implementation and ArtScene acceptance, Phase 53 promotes the
observed contract into CNCF, Cozy/CML, launcher, WebApplication, and ArtScene
normative design/specification and operations documentation.
