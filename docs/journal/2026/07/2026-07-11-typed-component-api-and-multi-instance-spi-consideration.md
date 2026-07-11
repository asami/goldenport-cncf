# Typed Component API and Multi-Instance SPI Consideration

## Context

ArtScene needs to reuse scraping functionality being moved into
`textus-scraper`. The initial implementation direction exposed a design smell:
ArtScene could import `textus-scraper.impl` and call implementation classes,
but that would bypass component configuration, rules, lifecycle, packaging,
operation semantics, and runtime assembly.

The discussion therefore examined two component consumption patterns:

1. one consumer connects to one provider component;
2. one consumer uses several instances of the same provider component, each
   with different configuration and rules.

The second pattern is important beyond scraping. AI runners, search engines,
vector stores, notification providers, and other infrastructure components may
also require several named instances in one subsystem.

## Existing CNCF Evidence

The current CNCF model already contains relevant foundations:

- `ComponentInstanceId(name, instance)` distinguishes runtime instances;
- `ComponentSpace` has indexes by instance id and component id;
- `SpiSocket[S]` installs a typed service;
- `SpiResolver` collects output providers and installs exactly one compatible
  provider into each unresolved socket;
- `SpiTraceSupport` wraps known installed SPI services and records calltree and
  runtime metrics;
- `AiRunnerSocket` demonstrates ordinary typed Scala calls through an installed
  SPI service.

The current runtime is still primarily a single-provider socket model. Two
providers for the same contract become ambiguous unless existing provider
selection narrows them. Assembly bindings identify provider components, but do
not yet identify named component instances.

## Discussion Progression

### Direct Component Library Import Was Rejected

Calling classes under another component's `impl` package was rejected as the
standard route. It creates compile-time coupling to provider internals and
prevents assembly from controlling configuration, rules, lifecycle, and
provider replacement.

The consumer should depend on a public contract and assembly should provide the
implementation.

### SPI Socket Was Positioned as an Assembly Input Port

An SPI socket is primarily part of the component structure established during
startup. It declares a required capability and receives a compatible provider
service from the effective assembly.

"Static" in this context means that the connection graph is resolved at
startup, not necessarily at compile time. Production assembly, runtime config,
and explicit test descriptor overlays may change the provider before startup
resolution completes.

### Component Identity Was Separated from Provider Variation

The discussion distinguished:

- `ComponentInstanceId`: identifies a concrete runtime component instance;
- `SpiSelection.provider/mode/engine`: chooses variation within a provider
  contract;
- `ComponentSelector`: selects a component instance exactly or abstractly.

Encoding component instance names in `SpiSelection` was rejected because it
would mix runtime component identity with provider-internal variation.

### Exact and Purpose-Based Selection Were Both Required

A caller may know the exact instance:

```text
textus-scraper/dynamic-playwright
```

Alternatively, the caller may only know its intent:

```text
purpose = javascript-heavy-site
```

The resolver should support both and always return a concrete
`ComponentInstanceId`. Purpose, capability, tags, rules, health, and priority
are selection inputs rather than substitutes for runtime identity.

### A Multi-Instance Socket Route Was Accepted

Using multiple configured component instances through the socket route is
valid, but single and multiple cardinality must be explicit:

- `SpiSocket[S]`: one installed provider service;
- `SpiSocketSet[S]`: several installed provider services selected at runtime.

The socket set remains assembly-bounded. It does not scan arbitrary CARs or
allow application code to construct provider components.

### The Socket Exposes an API, Not a Component Object

The consumer should call a typed service such as `TextusScraperApi`, not obtain
and cast a provider `Component`.

From application code, a component operation should look like an ordinary
Scala method:

```scala
scraper.fetchPage(request)
```

This is a typed facade over the component operation boundary. It is not a
direct call to an implementation method on the provider component.

### Generic and Typed Invocation Were Both Required

Two invocation levels were identified:

1. a generic CNCF runtime route using operation metadata and `Record`;
2. a typed application route using generated request/response/value types.

The generic route is useful for scripts, rules, tooling, and runtime engines.
The typed route is the normal Scala application API. Application code should
not contain operation-name strings or `toRecord`/`fromRecord` conversion.

The typed API proxy should delegate to the same generic invocation and
operation/action path so authorization, UnitOfWork, validation, jobs, events,
and observability remain consistent.

### Two Typed API Acquisition Patterns Were Accepted

The same typed API can be acquired in two primary ways:

1. declarative injection through an assembly-bound socket;
2. programmatic typed resolution using a component selector.

A socket set combines the patterns: assembly injects the allowed candidate
set and application code selects one candidate at runtime.

### Contract Ownership Was Split Between CNCF and Cozy

The discussion clarified that CNCF should not manually own a standard SPI type
for every component API.

Fundamental, provider-neutral, broadly reused contracts remain in
`org.goldenport.cncf.spi`. CNCF owns these contracts because independent
providers and consumers require stable shared semantics. `AiRunner` is the
representative example.

Component-specific APIs that are normally backed by one concrete component are
derived from that component's CML. Cozy generates the typed API, operation
types, single socket, socket set, and proxy. `TextusScraperApi` belongs to
this category unless scraping later proves to require a smaller, genuinely
provider-neutral CNCF core capability.

The socket remains semantically owned by the consumer as an input port even
when its reusable trait is generated from the provider component's public
contract.

### Contract Categories Were Determined Per Port

The standard-SPI/component-API distinction is not a classification of the
entire component. A single component may use both categories at the same time.

A component may consume a CNCF standard SPI for infrastructure, provide a
different CNCF standard SPI as an interchangeable adapter, and publish a richer
Cozy-generated component API for its ordinary consumers. It may also retain
public operations that are not exposed through either SPI route.

This allows a component such as `textus-scraper` to use standard AI or browser
capabilities internally while exposing `TextusScraperApi`. If part of its
behavior later satisfies a stable provider-neutral CNCF contract, the same
component may additionally provide that standard SPI through an adapter.

Where two contracts expose related behavior, they must share application logic
rather than duplicate it. Each contract keeps independent version and
observability identity.

### CML Placement Was Revised to Service Properties

The first proposal introduced a new top-level `COMPONENT API` declaration and
separate port declarations. This was replaced by a form that follows the
existing CML Literate Model hierarchy.

Under `# COMPONENT`, the existing `### SERVICE` composition section lists each
service as `#### <name>`. SPI behavior is expressed as metadata properties of
that service:

```cml
### SERVICE

#### Scraping

- spi-standard :: cncf.web-content-fetcher
- spi-direction :: provides
- spi-socket :: true
```

The top-level `# SERVICE / ## Scraping` definition remains the source of
operation structure. The component service entry controls composition and API
exposure only.

### Socket Cardinality Was Moved to the Consumer

Provider components do not select between a single socket and socket set.
There is no useful provider-side restriction: the same typed service can be
installed into either form, and future multi-instance use should not require a
provider contract change.

Every CNCF standard SPI therefore supplies both forms, such as
`AiRunnerSocket` and `AiRunnerSocketSet`. When `spi-socket :: true` is declared for
a component-specific service, Cozy likewise generates both forms.

The consumer selects one through requirement multiplicity:

- `1`: required single socket;
- `?`: optional single socket;
- `*`: socket set;
- `*` plus `spi-required :: true`: non-empty socket set.

## Decision

CNCF should evolve toward the following model:

```text
assembly component instances
  -> named instances with isolated config/rules/purpose/tags
  -> SPI binding or component API resolver
  -> typed component API
  -> generated proxy
  -> generic SPI invocation / OperationCall
  -> provider component operation
```

The application-facing object is a typed contract such as
`TextusScraperApi`. The generic `Record` route is infrastructure beneath that
contract.

The public contract must be separate from provider implementation packages.
Cozy/CNCF generation should eventually derive typed component APIs and proxies
from CML operation metadata.

CNCF standard SPI contracts and Cozy-generated component API contracts use the
same assembly, resolver, invocation, operation execution, and observability
infrastructure. They differ in contract ownership and generation policy.

## Documentation Lifecycle Decision

The documents have distinct lifecycle roles:

1. This journal preserves the consideration process and decisions at the time
   of discussion.
2. `docs/notes/typed-component-api-and-multi-instance-spi.md` is the mutable
   implementation specification and may change as CNCF and Cozy behavior is
   implemented and tested.
3. After implementation and executable verification settle the contract, the
   accepted specification is promoted to `docs/design`.
4. The resulting design document is the authoritative decided specification.

The working note must not be presented as a fixed final design before the
implementation proves its descriptor, resolution, invocation, and
observability contracts.

## Rejected Directions

- Importing `textus-scraper.impl` from ArtScene.
- Exposing a provider `Component` object through a socket.
- Selecting the first matching provider when several instances exist.
- Using `SpiSelection.mode` or `SpiSelection.provider` as a component instance
  identifier.
- Making application code construct or load arbitrary provider components.
- Requiring ordinary Scala application code to use `Record` directly.
- Bypassing OperationCall/ActionEngine semantics with implementation-method
  calls presented as component operations.
- Adding every component-specific API to `org.goldenport.cncf.spi`.

## Open Questions

- Whether typed component API contracts are emitted as a dedicated artifact or
  as a stable generated package shared by CAR consumers.
- The exact descriptor merge semantics for per-instance config and rules.
- Whether socket names belong in compiled socket metadata, assembly binding
  metadata, or both.
- Whether `SpiSocketSet[S]` stores resolved services directly or lightweight
  typed proxies keyed by `ComponentInstanceId`.
- How health changes after startup affect a previously resolved socket set.
- Whether runtime reconfiguration may replace an installed provider without a
  component restart.
- How remote component transport participates while preserving the same typed
  API.

## Suggested Implementation Sequence

1. Extend assembly component declarations with `instance`, isolated `config`,
   `rules`, `purposes`, `tags`, and priority/default metadata.
2. Create distinct component instances with stable `ComponentInstanceId`
   values and add exact instance lookup to public runtime APIs.
3. Extend SPI provider selectors with `provider.instance` and explicit socket
   names.
4. Add `ComponentSelector` and deterministic exact/abstract resolution.
5. Add `SpiSocketSet[S]` with explicit many-cardinality installation.
6. Add generic `SpiInvoker` using contract, operation metadata, `Record`, and
   the canonical operation/action path.
7. Generate typed component API facades/proxies from CML operations.
8. Generate both single-socket and socket-set contracts for each declared
   component-specific socket API, and let consumer multiplicity select the
   installed form.
9. Add `ComponentApiResolver.resolve[A]` for programmatic typed acquisition.
10. Add calltree metadata for selected component instance, selector, and rule.
11. Prove the design with ArtScene plus static and dynamic `textus-scraper`
    instances.

## Related Documents

- `docs/notes/typed-component-api-and-multi-instance-spi.md`
- `docs/design/component-port-wiring.md`
- `docs/design/assembly-descriptor.md`
- `docs/notes/cncf-developer-guide.md`
- `/Users/asami/src/dev2026/textus-scraper/docs/journal/2026/07/2026-07-10-component-consumption-patterns.md`
