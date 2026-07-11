# Typed Component API and Multi-Instance SPI

Status: Working implementation specification
Scope: CNCF component API consumption, SPI sockets, named component instances,
and assembly-time/runtime selection

## 1. Purpose

This note defines how one CNCF component consumes another component through a
typed API while preserving the CNCF operation boundary. It also defines the
target model for using multiple configured instances of the same component.

The motivating example is an application component using several
`textus-scraper` instances. Each instance may have different configuration and
rules, such as a static JSoup profile and a dynamic Playwright profile.

This is a working specification. It is expected to evolve as the corresponding
CNCF, Cozy, and component runtime features are implemented.

## 1.1 Document Lifecycle

This note is the mutable specification used while the feature is being
implemented. Its examples, names, descriptor shapes, and API boundaries may be
revised in response to implementation and executable-specification findings.

The documentation lifecycle is:

```text
journal consideration record
  -> notes working implementation specification
  -> implementation and executable verification
  -> design decided specification
```

After the feature is implemented and its acceptance criteria are verified, the
settled contract must be promoted to a document under `docs/design`. The design
document then becomes the authoritative decided specification. This note should
be reduced to an implementation-history pointer or clearly marked as superseded
by that design document.

The journal remains an immutable record of the reasoning that led to the
working specification. It must not be rewritten to match later implementation
details.

## 2. Core Principles

1. A consumer depends on a public typed component API, not on provider
   implementation classes.
2. A socket exposes a capability service or typed component API. It does not
   expose the provider `Component` object.
3. The canonical runtime execution boundary remains CNCF operation/action
   execution.
4. `Record` is the generic invocation and wire representation. Ordinary Scala
   application code should use typed request and response values.
5. Assembly defines the component instances that may participate, their
   configuration and rules, and the available bindings.
6. Component instance identity and provider variation are separate concepts.
7. Missing, ambiguous, incompatible, or unhealthy selections fail explicitly
   with `Consequence`.

## 3. Vocabulary

### 3.1 Component Type

A component type identifies the reusable component implementation and public
contract, for example `textus-scraper`.

### 3.2 Component Instance

A component instance is one runtime realization of a component type:

```text
component instance = component type + instance id + config + rules + metadata
```

Examples:

- `textus-scraper/static-default`
- `textus-scraper/dynamic-playwright`

Instances of the same component type have independent lifecycle, configuration,
rules, and runtime state.

### 3.3 Typed Component API

A typed component API is the application-facing Scala contract for public
component operations. For example:

```scala
trait TextusScraperApi {
  def fetchPage(
    request: FetchPageRequest
  )(using ExecutionContext): Consequence[FetchPageResponse]

  def navigateSite(
    request: NavigateSiteRequest
  )(using ExecutionContext): Consequence[NavigateSiteResponse]

  def extractEvents(
    request: ExtractEventsRequest
  )(using ExecutionContext): Consequence[ExtractEventsResponse]
}
```

The API belongs to the public contract layer. Consumers must not depend on
`textus-scraper.impl` or other provider internals.

### 3.4 SPI Socket

An SPI socket is an input port populated during assembly/runtime wiring. A
single socket has cardinality one and exposes one installed typed API.

### 3.5 SPI Socket Set

An SPI socket set has cardinality many. Assembly installs a bounded set of
typed APIs into it, one for each selected provider component instance. Consumer
code chooses one installed API by exact instance or an abstract selector.

### 3.6 Component API Resolver

A component API resolver performs programmatic typed resolution across the
component instances made available by the assembly. It is intended for dynamic
selection that is broader than one injected socket set.

### 3.7 SPI Invoker

An SPI invoker is the generic CNCF invocation surface. It accepts a resolved
binding or socket reference, an operation name, a `Record` request, and an
optional component selector. It returns `Consequence[Record]`.

The SPI invoker is runtime infrastructure. It is not the preferred
application-programming surface.

## 4. Component Instance Descriptor

The target assembly shape supports multiple entries with the same component
name and distinct instance ids:

```yaml
components:
  - name: textus-scraper
    instance: static-default
    version: 0.1.0-SNAPSHOT
    purposes:
      - official-site
      - lightweight-navigation
    tags:
      - static
      - jsoup
    priority: 100
    config:
      scraper.mode: static
      scraper.timeout-seconds: 10
    rules:
      navigation:
        same-origin: true
        max-pages: 8

  - name: textus-scraper
    instance: dynamic-playwright
    version: 0.1.0-SNAPSHOT
    purposes:
      - javascript-heavy-site
    tags:
      - dynamic
      - browser
    priority: 100
    config:
      scraper.mode: dynamic
      scraper.engine: playwright
    rules:
      browser:
        javascript: true
        block-images: true
```

The runtime identity is represented by `ComponentInstanceId`, for example:

```scala
ComponentInstanceId("textus-scraper", "dynamic-playwright")
```

`instance` identifies a runtime component. It must not be encoded in
`SpiSelection.provider`, `SpiSelection.mode`, or `SpiSelection.engine`.

### 4.1 Implemented TC-02 Baseline

The Phase 29 TC-02 implementation establishes the descriptor and creation
baseline:

- repeated `components` entries with the same component type are retained when
  their `instance` values differ;
- `config`, `rules`, `purposes`, `tags`, `priority`, and `default` are retained
  as instance metadata;
- generated component factories receive that metadata through
  `ComponentCreate` and create a stable `ComponentInstanceId`;
- instance config overlays packaged component config and becomes
  component-scoped resolved parameters whose parent is the global runtime
  parameter set;
- duplicate component/instance pairs, multiple declared defaults for one
  component type, and malformed instance names are rejected;
- components without an instance declaration continue to use the existing
  default-instance and name-based duplicate behavior.

Instance `rules` are isolated metadata in TC-02. Their selector and policy
evaluation semantics are introduced with the corresponding resolver slices;
TC-02 does not treat an arbitrary rule record as executable behavior.

### 4.2 Implemented TC-03 Exact Binding Baseline

The Phase 29 TC-03 implementation establishes exact single-socket resolution:

- assembly provider selectors may name a component instance;
- socket selectors may name the consumer instance and the socket itself;
- `SpiSocket.spiSocketName` defaults to `default`, while components may publish
  several named sockets through `Component.Port.input(...)`;
- exact provider instance selection uses canonical `ComponentInstanceId`
  identity and does not fall back to another instance;
- a component-only provider selector chooses a unique declared default, then a
  unique literal `default` instance, and otherwise reports ambiguity;
- input sockets participate in socket resolution but remain excluded from
  provider discovery;
- missing sockets/providers, incompatible providers, duplicate bindings, and
  ambiguous sockets/providers fail through deterministic `Consequence`
  boundaries.

### 4.3 Implemented TC-04 Socket Set Baseline

The Phase 29 TC-04 implementation adds assembly-bounded multi-provider
resolution:

- `SpiSocketSet[S]` receives `ResolvedSpiMember[S]` values with logical
  component instance, purpose, capability, tag, priority, default, and health
  metadata;
- `ComponentSelector` supports exact instance and abstract
  purpose/capability/tag filtering;
- `ComponentApiResolver` applies health and typed runtime policy before
  deterministic priority/default selection;
- assembly `cardinality: many` bindings may contribute several provider
  members to one named socket set, while single sockets continue to reject
  multiple bindings;
- required single, optional single, optional set, and required non-empty set
  cardinalities fail or remain empty deterministically;
- component-only set bindings expand compatible instances already admitted by
  the effective assembly; they never discover or load arbitrary components;
- error-health providers are excluded before service materialization. Warning
  health remains eligible and is retained in resolved member metadata.

Arbitrary instance `rules` records remain metadata and are not executed as
selection code. Custom runtime policy is expressed through the typed
`ComponentSelectionPolicy` boundary.

## 5. Consumption Modes

### 5.1 Single Socket Injection

Assembly binds one provider component instance to one consumer socket:

```yaml
spi:
  bindings:
    - socket:
        component: art-scene
        name: scraper
        contract: textus-scraper
      provider:
        component: textus-scraper
        instance: static-default
```

The consumer uses the injected typed API:

```scala
scraper.fetchPage(request)
```

Provider absence and ambiguity are detected during startup. This is the
default mode for fixed dependencies.

### 5.2 Socket Set Injection

Assembly binds multiple provider component instances to one socket set:

```yaml
spi:
  bindings:
    - socket:
        component: art-scene
        name: scrapers
        contract: textus-scraper
        cardinality: many
      provider:
        component: textus-scraper
        instance: static-default

    - socket:
        component: art-scene
        name: scrapers
        contract: textus-scraper
        cardinality: many
      provider:
        component: textus-scraper
        instance: dynamic-playwright
```

The consumer selects one typed API from the assembly-bounded set:

```scala
scrapers
  .resolve(ComponentSelector(
    purpose = Some("javascript-heavy-site")
  ))
  .flatMap(_.fetchPage(request))
```

This mode is preferred when one declared dependency has several configured
instances and request-specific rules choose among them.

### 5.3 Programmatic Typed Resolution

A consumer may resolve a typed API through CNCF runtime services:

```scala
componentApiResolver
  .resolve[TextusScraperApi](ComponentSelector(
    component = Some("textus-scraper"),
    purpose = Some("javascript-heavy-site")
  ))
  .flatMap(_.fetchPage(request))
```

Programmatic resolution does not allow the application to load arbitrary CARs,
construct components directly, or inspect provider implementation classes. It
selects from component instances admitted by the effective assembly and
runtime policy.

Dynamic component creation is a separate lifecycle concern and is outside this
note.

## 6. Exact and Abstract Selection

`ComponentSelector` supports exact and abstract selection. The target selector
shape may include:

```scala
final case class ComponentSelector(
  component: String,
  instance: Option[String] = None,
  purpose: Option[String] = None,
  capabilities: Set[String] = Set.empty,
  tags: Set[String] = Set.empty
)
```

Resolution follows this order:

1. If `instance` is specified, require an exact component instance match.
2. Otherwise filter by component type and API contract.
3. Filter by purpose, capability, tags, scope, health, and runtime policy.
4. Apply rules and priority/default metadata.
5. Return the only remaining candidate.
6. Return an ambiguity failure when multiple equal candidates remain.
7. Return an unavailable failure when no candidate remains.

Exact instance selection must still validate the requested API contract and
runtime policy.

## 7. Typed and Generic Invocation

Both invocation forms use the same resolved binding and operation execution
path.

### 7.1 Generic Route

Generic runtime tools, scripts, and rule engines may use a `Record` boundary:

```scala
spiInvoker.invoke(
  contract = TextusScraperApi.contract,
  socket = Some(SpiSocketRef("art-scene", "scrapers", TextusScraperApi.contract.name)),
  operation = SpiOperationSelector("fetch-page", Some("scraper")),
  request = requestRecord,
  selector = ComponentSelector(
    component = Some("textus-scraper"),
    purpose = Some("javascript-heavy-site")
  )
)
```

The result is `Consequence[Record]`.

### 7.2 Typed Route

Application code uses the typed component API. A generated proxy hides record
conversion and string operation selectors:

```scala
private final class GeneratedTextusScraperApiProxy(
  invoker: SpiInvoker,
  binding: ResolvedSpiBinding
) extends TextusScraperApi {
  def fetchPage(
    request: FetchPageRequest
  )(using ExecutionContext): Consequence[FetchPageResponse] =
    invoker
      .invoke(
        binding = binding,
        operation = SpiOperationSelector("fetch-page", Some("scraper")),
        request = request.toRecord
      )
      .flatMap(FetchPageResponse.fromRecord)
}
```

The application-facing call remains:

```scala
scraper.fetchPage(request)
```

Application code must not perform `toRecord` or `fromRecord` conversion for
ordinary typed component calls.

## 8. Contract Packaging and Generation

The typed API and its public request/response/value types belong to a contract
layer that is separate from the provider implementation.

### 8.1 CNCF Standard SPI Contracts

Fundamental provider-neutral SPI contracts are owned and published by CNCF
under `org.goldenport.cncf.spi`.

Examples include capabilities such as:

- AI runner;
- geographic resolver;
- toolchain runner;
- other infrastructure capabilities expected to have independent provider
  implementations across many components.

A contract belongs in the CNCF SPI package when:

- several unrelated consumer components need the capability;
- multiple interchangeable provider implementations are expected;
- provider-neutral request, response, selection, and error semantics can be
  kept stable;
- CNCF runtime needs first-class knowledge of the capability for resolution,
  tracing, policy, or compatibility.

CNCF owns the API trait, single socket trait, socket-set trait, contract
metadata, and provider-neutral types for these contracts. Provider components
implement the CNCF contract; they do not redefine it or choose which socket
cardinality consumers may use.

### 8.2 Cozy-Generated Component API Contracts

Not every component API should become a CNCF standard SPI. A component that is
normally consumed as one concrete component, or whose public API follows its
own CML application model, uses a Cozy-generated component API contract.

For example, `textus-scraper` defines its public operations in CML. Cozy should
generate:

- `TextusScraperApi`;
- typed operation request and response values;
- `TextusScraperSocket`;
- `TextusScraperSocketSet`;
- the typed proxy and generic operation metadata needed by CNCF invocation.

When a service requests a component-specific socket contract, Cozy generates
both the single socket and socket-set forms. The provider does not choose one
form. A consumer requirement selects the form through multiplicity.

The generated contract lives in a public component contract/API package. It
must not expose or require the provider `impl` package.

This category is appropriate when:

- the API is specific to one component's application responsibility;
- one concrete component implementation is the ordinary case;
- the operation shape is expected to evolve with the component CML;
- promoting the API into CNCF core would create unnecessary framework surface.

Assembly and runtime wiring are the same for CNCF standard SPI contracts and
Cozy-generated component API contracts. The distinction is ownership and
generation, not invocation semantics.

### 8.3 Consumer-Side Socket Declaration

The socket is semantically the consumer's input port. Mechanically, the socket
trait may be supplied by CNCF for a standard SPI or generated by Cozy from a
component API contract.

The consumer opts into the dependency by mixing in the generated socket trait
or by declaring a required component API in CML. Assembly then chooses the
provider component instance. The provider component publishes the output API
and must not mix in its own consumer socket solely to make itself discoverable.

### 8.4 Standard SPI and Component API Coexistence

The classification applies to each contract or port, not to the component as a
whole. One component may simultaneously:

- require one or more CNCF standard SPI contracts;
- provide one or more CNCF standard SPI contracts;
- publish one or more Cozy-generated component APIs;
- expose ordinary command, query, REST, and event operations that are not SPI
  contracts.

For example, a scraper component may consume a CNCF standard AI runner or
browser automation capability while publishing its richer component-specific
scraping API:

```text
textus-scraper component instance
  inputs
    - CNCF standard SPI: ai-runner
    - CNCF standard SPI: browser-runner
  outputs
    - component API: TextusScraperApi
    - optional CNCF standard SPI: web-content-fetcher
```

The canonical CML intent is expressed as properties of each service listed
under the component. It may combine declarations:

```cml
# COMPONENT

## Scraper

### SERVICE

#### Scraping

- spi-standard :: cncf.web-content-fetcher
- spi-direction :: provides
- spi-socket :: true

#### AiRunner

- spi-standard :: cncf.ai-runner
- spi-direction :: requires
- spi-multiplicity :: "*"
```

The top-level `# SERVICE / ## Scraping` section continues to define the service
operations. `# COMPONENT / ## Scraper / ### SERVICE / #### Scraping` defines
how that service participates in component composition and SPI exposure.

When one implementation behavior is exposed through both a standard SPI and a
component API, both adapters should delegate to the same application service or
operation implementation. They must not maintain separate business logic.
Contract versioning, selection, authorization, and calltree identity remain
independent for each exposed contract.

### 8.5 CML Service SPI Properties

The working CML property contract is namespaced with the `spi-` prefix because
component service metadata may also contain properties unrelated to SPI
composition:

| Property | Meaning |
|---|---|
| `spi-standard` | Existing CNCF standard SPI contract implemented or required by the service. |
| `spi-direction` | `provides` or `requires`; default is `provides`. |
| `spi-socket` | When `true`, generate the component-specific typed API, single socket, and socket set. |
| `spi-multiplicity` | Consumer cardinality: `1`, `?`, or `*`; valid for `requires`. |
| `spi-required` | When `true` with `*`, require at least one installed provider. |
| `spi-api-name` | Optional generated component-specific API name override. |
| `spi-component-api` | Fully qualified generated component API required by a consumer service. |

Examples:

```cml
# COMPONENT

## Scraper

### SERVICE

#### Scraping

- spi-standard :: cncf.web-content-fetcher
- spi-direction :: provides
- spi-socket :: true
- spi-api-name :: TextusScraper

#### AiRunners

- spi-standard :: cncf.ai-runner
- spi-direction :: requires
- spi-multiplicity :: "*"
- spi-required :: true

#### Scrapers

- spi-direction :: requires
- spi-component-api :: org.simplemodeling.textus.scraper.api.TextusScraperApi
- spi-multiplicity :: "*"
- spi-required :: true
```

The four provider-side service combinations are:

| `spi-standard` | `spi-socket` | Contract exposure |
|---|---:|---|
| absent | absent or `false` | Ordinary service operations only. |
| present | `false` | CNCF standard SPI only. |
| absent | `true` | Cozy-generated component-specific API only. |
| present | `true` | Both standard SPI and component-specific API. |

Every CNCF standard SPI contract publishes both its single socket and socket
set. Every Cozy-generated component-specific socket contract also publishes
both forms. Provider declarations do not restrict cardinality. Consumer
`spi-multiplicity` selects the runtime input-port form:

- `1`: required single socket;
- `?`: optional single socket;
- `*`: socket set;
- `*` with `spi-required :: true`: non-empty socket set.

`spi-standard` without `spi-socket :: true` does not generate a component-specific
API. `spi-socket :: true` does not promote a component API into
`org.goldenport.cncf.spi`.

Conceptually:

```text
textus-scraper contract
  - TextusScraperApi
  - FetchPageRequest / FetchPageResponse
  - NavigateSiteRequest / NavigateSiteResponse
  - ExtractEventsRequest / ExtractEventsResponse

textus-scraper CAR implementation
  - component factory and operation logic
  - JSoup and Playwright adapters
  - config and rule implementation
```

Cozy should generate component-specific typed API facades, sockets, socket sets,
and proxies from CML operation metadata. CNCF supplies the generic SPI/socket,
resolution, invocation, and execution infrastructure. Consumers depend on the
contract surface, not on the implementation package.

Contract version compatibility must be validated when a provider is bound or
resolved.

## 9. Operation Execution Semantics

A typed API method represents a public component operation, but it is not a
direct call to a provider component implementation method.

The canonical path is:

```text
typed Scala method
  -> generated component API proxy
  -> SPI invoker / resolved binding
  -> ComponentLogic request construction / ActionEngine
  -> provider component operation
```

This preserves:

- `ExecutionContext`;
- operation request/response validation;
- authorization;
- UnitOfWork and transaction behavior;
- jobs and events;
- calltree and runtime metrics;
- future local/remote transport substitution.

An in-process provider may optimize the final dispatch, but it must preserve
the externally observable operation contract and execution semantics.

## 10. Configuration and Rule Scope

Each component instance receives effective configuration and isolated rule
metadata. A resolver or policy applies only rule vocabularies it understands.
The intended precedence is:

1. component packaged defaults;
2. component-local assembly defaults;
3. subsystem/SAR assembly instance settings;
4. runtime profile or environment configuration;
5. explicit test descriptor overlay in tests.

Configuration and rules are isolated by `ComponentInstanceId`. An override for
`textus-scraper/dynamic-playwright` must not modify
`textus-scraper/static-default`.

## 11. Observability

Every typed and generic SPI invocation should add calltree metadata for:

- contract;
- operation;
- socket component and socket name;
- provider component and `ComponentInstanceId`;
- selector purpose, capabilities, and tags;
- selection result and applicable rule/profile;
- outcome, diagnostic key, and duration.

Sensitive config and rule values must follow existing calltree confidentiality
policy.

The runtime records the selected basis using a finite vocabulary:

- `assembly-binding`;
- `exact-instance`;
- `abstract-selector`;
- `priority`;
- `declared-default`;
- `conventional-default`;
- `sole-candidate`.

Provider resolution failures are traced even when no binding can be created.
Such traces use `provider_component = unresolved` and the requested finite
selection basis. SPI tracing never copies the request `Record`, provider raw
payload, or confidential configuration into SPI attributes.

SPI metrics use bounded dimensions only: contract, operation, provider
component type, socket component type, selection basis, outcome, and diagnostic
key. Instance identifiers, selector purpose/capability/tag values, request
fields, and provider payload values remain outside metrics labels.

## 12. Failure Semantics

Expected runtime failures use `Consequence`:

- socket not installed;
- no provider component instance;
- ambiguous provider component instances;
- incompatible contract version;
- provider unhealthy or disabled;
- selector rejected by policy;
- operation not exposed by the typed contract;
- request or response conversion failure;
- provider operation failure.

The resolver must not silently choose the first provider when multiple equal
candidates remain.

Unavailable, ambiguous, incompatible, unhealthy, and policy-rejected
selections retain distinct deterministic failure messages. A provider excluded
only because its health status is `error` is reported as unhealthy; a non-empty
candidate set reduced to zero by `ComponentSelectionPolicy` is reported as
policy-rejected.

## 13. Current Implementation Status

The current CNCF source already provides part of this model:

- `ComponentInstanceId` identifies a component instance;
- `ComponentSpace` stores components by instance id and groups them by
  component id;
- assembly descriptors create multiple named instances of one component type
  with isolated config and rule metadata;
- `SpiSocket[S]` installs one typed service;
- `SpiResolver` resolves one compatible provider and installs a traced service;
- assembly SPI bindings can select exact provider/consumer instances and named
  sockets while preserving componentlet participation under the owning logical
  component instance;
- `ResolvedSpiBinding` retains selected socket/provider identity, the exact
  assembly participant, and the contract operation catalog used for dispatch;
- `SpiOperationProvider` publishes the operations exposed by a provider
  contract; generic invocation rejects undeclared component operations;
- `SpiInvoker` invokes a selected provider operation through request
  validation, authorization, `ComponentLogic`, and `ActionEngine` using a
  provider-neutral `Record` boundary;
- generic invocation with a socket reference selects only providers actually
  bound to that assembly socket, while socket-free programmatic invocation can
  select from the subsystem's assembly-admitted provider catalog;
- resolved bindings remain owned by their source subsystem and cannot be
  invoked through a different subsystem;
- typed service resolution and generic operation invocation share the same
  health, policy, priority, default, and ambiguity rules within those bounds;
- resolved bindings retain a finite `SpiSelectionBasis`, and both successful
  invocation and pre-binding resolution failure produce safe SPI CallTree
  records;
- SPI runtime metrics include the finite selection basis but exclude instance
  and free-form selector values to avoid unbounded cardinality;
- `SpiBoundProvider` materializes a generated API proxy only after the runtime
  has selected a concrete provider and produced a `ResolvedSpiBinding`;
- CML component service composition uses `spi-*` properties so unrelated
  service metadata remains in a separate namespace;
- Cozy/SimpleModeler generate component-specific API traits under the public
  `.api` package, binding-aware proxies, provider adapters, and paired single
  and set socket classes;
- generated consumer declarations install `1`, `?`, or `*` input ports and
  expose typed accessors without handling the generic `Record` boundary;
- `StandardSpiSocketSet` supplies the reusable member-storage and selector
  baseline for CNCF-owned standard SPI contracts;
- AI runner, geographic resolver, and toolchain runner publish paired single
  and set socket forms while retaining their existing single-socket APIs;
- SPI calls such as `AiRunner` are ordinary typed Scala method calls.

Component implementations must not bypass the generated contract by importing
another component's implementation package or by silently selecting the first
component from `Subsystem.components`.

## 14. Acceptance Criteria

The feature is complete when:

1. Assembly can create two instances of one component type with independent
   config and rules.
2. A single socket can bind an exact named provider instance.
3. A socket set can bind several named provider instances of one contract.
4. Exact instance and abstract purpose selection both return the expected typed
   API.
5. Ambiguous and unavailable selections return structured failures.
6. A Scala consumer invokes component operations through typed methods without
   handling `Record`.
7. A generic runtime caller invokes the same operations through `SpiInvoker`
   and `Record`.
8. Both routes preserve operation/action semantics and produce equivalent
   calltree records.
9. Consumer code depends only on the public contract package.
10. An ArtScene development-driver smoke integrates `textus-scraper` after the
    framework feature is implemented and proves static and dynamic configured
    instances can be selected and invoked through the public typed API.
11. Existing CNCF standard SPI contracts remain CNCF-owned, while a
    component-specific contract can be generated without adding a new type to
    `org.goldenport.cncf.spi`.
12. One component can require or provide standard SPI contracts and publish a
    generated component API in the same assembly without contract ambiguity.
13. Standard and component-specific SPI contracts both make single and set
    socket forms available, while consumer multiplicity chooses which form is
    installed.
