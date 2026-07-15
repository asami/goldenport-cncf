# CNCF Developer Guide

Status: current

## Purpose

This guide is the first reading point for developers building CNCF components
or framework extensions. It summarizes the current working rules and points to
the more detailed documents that own each topic.

CNCF component code should express domain intent. CNCF runtime helpers,
internal DSLs, and `UnitOfWork` own execution context interpretation,
configuration lookup, authorization metadata, lifecycle behavior, and low-level
storage/runtime effects.

## Start Here

Read these documents in this order:

1. `docs/notes/car-development-start-guide.md`
   - Project setup, source layout, local development startup, packaging, and
     publication checks for CAR projects.
2. `docs/notes/cml-application-modeling-guideline.md`
   - How to write the application model before adding handwritten logic.
3. `docs/notes/application-logic-guideline.md`
   - Rules for generated and handwritten component behavior.
4. `docs/notes/internal-dsl-guideline.md`
   - The protected internal DSL boundary used by `ActionCall` logic.
5. `docs/notes/unitofwork-guideline.md`
   - How framework-owned effects are represented and interpreted.
6. `docs/notes/component-developer-document-index.md`
   - Task-oriented links for Web, authorization, packaging, descriptors, and
     runtime wiring.

## Component Implementation Rule

Handwritten component logic should normally live in generated/custom
`ComponentFactory` extension points and generated `ActionCall` subclasses.

Keep `ComponentFactory` as a facade. Its role is to connect generated
Component/Service/Operation metadata to handwritten behavior, create the
component participant, install ports/SPI sockets, and return generated
`ActionCall` implementations. It should not become the place where domain
algorithms, provider-specific workflows, rendering, datastore schema logic, or
large prompt/DSL transformations accumulate.

When handwritten behavior grows beyond small action glue, move the behavior
into a `*Logic` module in the component implementation package. A `*Logic`
module is the domain/application behavior body behind the generated service
factory surface. It may bind configuration, providers, policies, and runtime
adapters so that behavior can vary without changing `ComponentFactory`.

`*Logic` modules should not capture a request `ExecutionContext` as long-lived
state. Bind stable behavior inputs there, but receive request/runtime context at
the generated `ActionCall` boundary through `ActionCall.Core` and the protected
internal DSL helpers. This keeps reusable behavior configuration-bound while
authorization, CallTree, sandboxing, configuration precedence, and runtime
effects remain per-call.

Use more specific suffixes inside or beside a `*Logic` module when the role is
clear:

- `*Store` for component-local storage and schema/query mechanics;
- `*Client` for typed access to another component or provider surface;
- `*Strategy` for selectable algorithms or provider choice;
- `*Policy` for decision rules;
- `*Renderer` for rendering and layout output;
- `*Workflow` for multi-step user-visible recovery or review flows.

Do not use `*Factory` for extracted domain behavior. The `Factory` suffix is
reserved for CNCF/Cozy construction and adapter concepts such as
`Component.Factory`, generated `FooComponent.Factory`, `Component.BundleFactory`,
and small factory adapters that connect generated metadata to logic.

Inside an `ActionCall`, use protected CNCF helper methods before reaching for
lower-level runtime objects. If a needed helper does not exist, add or improve
an internal DSL helper instead of copying runtime mechanics into component
code.

Locale, timezone, and formatting assumptions are runtime context, not domain
logic defaults. Component logic should read them from the current
`ExecutionContext` or protected runtime/context helpers, and operation inputs
for locale or timezone should stay optional unless the domain contract requires
the caller to choose them. Do not use JVM or host defaults such as
`Locale.getDefault`, `ZoneId.systemDefault`, or default date/number/currency
formatters in component behavior. When an entity needs a durable timezone or
regional interpretation, resolve the omitted value from the execution context
at creation time and persist the resolved value.

Examples of preferred helper routes:

- configuration lookup: `config_string`, `config_int`, `config_double`,
  `config_boolean`;
- structured DSL/config parsing: `parse_dsl_document`;
- component application datastore selection:
  `use_component_application_datastore`, `component_datastore`;
- component-local user data: `component_local_data_dir`,
  `embedded_datastore`, `embedded_datastore_read`,
  `embedded_datastore_update`, `embedded_datastore_migrate`;
- entity, blob, association, child binding, job, event, HTTP, locale,
  timezone, formatting, and shell behavior through existing
  `ActionCallFeaturePart` helper families;
- persistence and entity behavior through internal DSL / `UnitOfWork`, not raw
  stores.

Component code should not:

- read `ExecutionContext.runtime.resolvedParameters` directly for ordinary
  runtime settings;
- read `component.subsystem.configuration` directly as the first route;
- instantiate `RuntimeFileConfigLoader` or other config parsers directly in
  application logic;
- instantiate outbound HTTP clients directly for normal component/provider
  behavior;
- depend on JVM default locale, timezone, character encoding, date/time
  formatting, number formatting, or currency formatting;
- open component-local embedded databases or user data files directly when a
  CNCF internal DSL helper exists;
- hand-roll tenant filters, lifecycle checks, logical delete filtering, or
  entity identity searches;
- call raw `DataStoreSpace` / unrestricted `EntityStoreSpace` from business
  logic.

### Deterministic execution capabilities

Values that enter an operation response, Entity state, Event, Job/Task state,
or another component-visible decision must come from the current execution
capabilities. In handwritten `ActionCall` behavior:

- use `execution_clock`, `current_instant`, or `current_zoned_datetime` for
  semantic time;
- use `random_int`, `random_long`, `random_double`, or `random_boolean` with a
  stable purpose name for domain randomness;
- use `entity_id`, `collection_entity_id`, or `opaque_id` for generated IDs;
- represent delay and asynchronous continuation through Job/Event facilities,
  never `Thread.sleep` or an application-created executor;
- use resolved environment assumptions and `config_*` helpers rather than
  `System.getenv`, `System.getProperty`, or JVM defaults;
- use CNCF filesystem/datastore/provider boundaries rather than directly
  reading host files that affect component behavior.

The controlled profile orders CNCF-owned timers, Job queues, retries, and async
Event continuation Tasks. It does not make arbitrary component-created threads
deterministic. Concurrent access to one named random stream is supported only
when CNCF-managed Task ordering serializes the calls. Host bootstrap,
repository discovery, transport adaptation, provider effects, and monotonic
performance measurement remain distinct boundaries and must not be routed
blindly through an `ActionCall` merely to remove an ambient API call.

## CAR Source Layout And Assembly Defaults

For CAR projects, `packaging.kind: car` uses `src/main/car` as the default
CAR-root source directory. Ordinary component projects should omit
`packaging.car.source_dir`; set it only when the project deliberately uses a
non-standard CAR-root source layout.

Place component-local assembly defaults in:

```text
src/main/car/assembly-descriptor.yaml
```

That file is packaged as CAR-root `assembly-descriptor.yaml`. Use it to declare
component-provided runtime assembly defaults, including required provider
components, wiring defaults, and SPI/provider selection defaults. Do not embed
provider CAR artifacts there or inside the application CAR. Provider CARs
remain repository-resolved from the standard component repository,
`repository.d`, or explicit development overrides.

The assembly descriptor is part of component assembly, not handwritten domain
logic. If a component works only because a provider is present, make that
provider requirement visible in `assembly-descriptor.yaml` so development
startup, packaged startup, tests, and deployment review use the same wiring
model.

## Component Application Datastore Selection

When a component owns durable application records through generated entity
operations, use `use_component_application_datastore` before generated
EntityStore reads or writes. This keeps component code independent from a
specific database product while still allowing deployment-time database
selection.

The selection order is:

1. Resolve the datastore policy from runtime properties, then CAR component
   config, then the framework default.
2. Use a component named datastore when configured.
3. Otherwise use the basic runtime datastore when it is configured as a
   persistent datastore.
4. Otherwise apply the policy fallback.

Supported policies are:

- `local-default`: use the user-local component datastore when neither a
  named nor basic persistent datastore is configured.
- `external-default`: use an in-memory datastore when neither a named nor
  basic persistent datastore is configured. This is the framework default.
- `external-required`: fail startup/operation selection when no persistent
  datastore is configured.
- `local-only`: always use the user-local component datastore and ignore
  external datastore settings.

CAR projects declare their default policy in `project.yaml` under
`project.component.config` so the value is packaged into the component
descriptor:

```yaml
project:
  component:
    config:
      textus.component.<component>.datastores.application.policy: local-default
```

Named component datastore keys use the normalized component name and store
name. The generated entity store uses the `application` store:

```properties
textus.component.<component>.datastores.application.kind=sqlite
textus.component.<component>.datastores.application.sqlite.path=/path/to/component.db
textus.component.<component>.datastores.application.sql.normalize-column-names=true
```

For MySQL/JDBC deployments:

```properties
textus.component.<component>.datastores.application.kind=mysql
textus.component.<component>.datastores.application.jdbc.url=jdbc:mysql://localhost:3306/app
textus.component.<component>.datastores.application.jdbc.user=app
textus.component.<component>.datastores.application.jdbc.password=secret
```

The legacy `textus.component.<component>.datastore.*` alias remains accepted
for the `application` store.

The basic runtime datastore is used only when it is persistent, for example:

```properties
textus.datastore.sqlite.path=/path/to/runtime.db
```

An explicitly in-memory basic datastore is not considered suitable for
component application records:

```properties
textus.datastore.kind=in-memory
```

If the policy falls back to local data storage, the default location is:

```text
~/.cncf/<component>/application.db
```

The fallback location can be redirected with the existing local-data keys:

```properties
textus.local-data.root=/path/to/root
textus.local-data.<component>.dir=/path/to/component-dir
textus.local-data.<component>.application.path=/path/to/application.db
```

Handwritten component code can request side-car stores through the internal
DSL by passing a store name:

```scala
component_datastore("crawler-cache")
```

Use `cncf.*` keys only as compatibility aliases. New component code and
documentation should prefer `textus.*`.

## Component Factory Shape

Choose the factory shape by construction scope.

Use `Component.Factory` for a single runtime participant. Generated Cozy
factories such as `FooComponent.Factory` are `Component.Factory`
implementations and carry the generated protocol, service/operation metadata,
authorization hooks, projection hooks, and internal DSL integration points.
Handwritten component factories should extend or delegate to the generated
factory when customizing a generated component.

Use `Component.BundleFactory` when the component package needs to publish a
bundle: a primary component plus componentlets. Componentlets are the semantic
reason to introduce the bundle layer. `SinglePrimaryBundleFactory` is a
convenience for exposing a single primary component through a bundle entrypoint,
but it still adds bundle/ServiceLoader concepts and should not be treated as
equivalent to a plain `Component.Factory` in developer guidance.

CAR validation should therefore check that the package has an appropriate
factory entrypoint for its shape. It should not require every handwritten
`ComponentFactory.scala` to extend `Component.BundleFactory`; a plain
`Component.Factory` is correct for a single participant when no bundle
entrypoint or componentlet publication is intended.

Do not add Java `ServiceLoader` metadata for ordinary CAR projects. The normal
CNCF loading route uses CAR metadata, class scanning, the
`impl.ComponentFactory` naming convention, and the factory type contract. Most
component developers should not need to know about
`META-INF/services/org.goldenport.cncf.component.Component$BundleFactory`.

ServiceLoader declaration is an advanced, non-default loading policy. It is
appropriate only when the factory is outside CNCF's normal discovery path, when
startup discovery cost must be reduced by naming a factory directly, or when a
project deliberately wants strict explicit factory binding. If it is used, the
declared service and the implementation type must match: a
`Component$BundleFactory` declaration must point at a real
`Component.BundleFactory`.

Reflection-discovered factories must expose a JVM-visible zero-argument
constructor. Cozy-generated `impl.ComponentFactory` classes satisfy this by
default. If handwritten code adds constructor parameters for provider injection,
test fixtures, or configuration seams, Scala default parameters alone are not a
portable discovery contract; keep an explicit auxiliary constructor such as
`def this() = this(defaultProvider, None)` or provide an equivalent zero-argument
factory entrypoint.

## Configuration Access

Use `ActionCallFeaturePart` configuration helpers from component logic.

Current lookup order for `config_string(key)` is:

1. action request property;
2. component/subsystem configuration;
3. resolved runtime parameters from the current `ExecutionContext`.

This order lets operation-specific values override component defaults while
still preserving launcher/runtime configuration. Use the typed variants for
scalar conversion:

- `config_int(key)`;
- `config_double(key)`;
- `config_boolean(key)`.

Use the two-key overload for compatibility migrations:

```scala
config_string("new.key", "old.key")
```

Compatibility fallback to subsystem configuration may remain in transitional
code, but new component code should treat the protected helper as the canonical
entry point.

## Structured DSL Parsing

Use `parse_dsl_document` from `ActionCallFeaturePart` when component logic needs
to parse YAML/HOCON/XML/properties-style structured input.

Preferred forms:

```scala
parse_dsl_document(path)
parse_dsl_document(filename, content)
```

The helper owns the CNCF parsing route and records parsing under the
`cncf:dsl:parse` CallTree chokepoint. File input is read as UTF-8 by the CNCF
runtime config loader.

Component logic may still decide whether an operation argument is inline text
or a file path, but once it has the text/path, parsing should go through the
protected helper. This keeps future changes to config decoding, provenance,
CallTree, validation, or security policy inside CNCF.

## External HTTP

Use CNCF internal DSL routes for outbound HTTP.

Inside an `ActionCall`, prefer the protected helpers from
`ActionCallFeaturePart`:

```scala
http_get(url, headers)
http_post(url, Some(body), headers)
http_post_bag(url, bag, headers)
```

Code that runs outside the `ActionCallFeaturePart` helper surface, such as a
provider service returned from an SPI or extension point, should use the
`ExecutionContext` supplied by CNCF and execute the corresponding
`UnitOfWorkOp.Http*` operation through `executionContext.runtime`.

Provider services may capture the `ExecutionContext` received by
`ExtensionPoint.provide(...)(using ExecutionContext)` and use it later for
their HTTP work. This is the intended route for providers such as `textus-ai`
or GeoResolver drivers: the provider API can stay provider-oriented, while the
effect still passes through CNCF's CallTree, runtime HTTP driver, and future
sandbox or egress policy.

Component code should not create `java.net.http.HttpClient`, sttp clients,
requests clients, curl-style subprocesses, or similar direct outbound clients
for normal runtime behavior. HTTP driver selection, timeout policy,
observability, sandboxing, egress control, retry behavior, and deterministic
test substitution belong to CNCF runtime. If a lower-level helper is missing,
add the internal DSL helper or a small component-local adapter that delegates
to `UnitOfWorkOp.Http*`.

## Built-In CAR Web UI

When a CAR includes a built-in Web UI and the project has no stronger
product-specific frontend requirement, use Bootstrap plus Material Design. This
is a component developer recommendation, not a mandatory platform rule. It
keeps built-in UIs aligned with CNCF Web packaging, generated admin surfaces,
and the existing `textus-*` component style.

Declare the CNCF Web UX profile as `bootstrap-material` when the CAR uses
CNCF-hosted built-in Web UI:

```yaml
web:
  profile: bootstrap-material
```

Use Bootstrap layout, forms, buttons, tables, cards, and utility classes as the
HTML/CSS baseline. Use Material Icons or an equivalent Material icon set, and
use Material Design visual language for spacing, color, controls, and status
presentation. Package Web assets inside the CAR and serve them through CNCF Web
packaging; do not require external CDN access for the built-in UI.

Keep Web UI behavior on top of component operations, CNCF automatic REST,
client, command, and generated admin behavior. Do not create a separate UI-only
domain path. If a project needs a highly custom SPA, native frontend, or
external product platform, place that Web tier outside CNCF and use the CAR's
public operation surfaces.

Component-owned Static Form Web apps use a component-scoped canonical route:

```text
/web/{component}/{webApp}
/web/{component}/{webApp}/{page}
/web/{component}/{webApp}/assets/{asset}
```

For example, component `art-scene` with Web app `textus-art-scene` is exposed
at `/web/art-scene/textus-art-scene`. Do not rely on `/web/{webApp}` or
`/web/{component}` as implicit shortcuts. A component may expose
`/web/{component}`, `/web/{component}/index`, and `/web/{component}/index.html`
only by marking one Static Web app as the explicit component entry:

```yaml
web:
  apps:
    - name: textus-art-scene
      entry: true
```

Short top-level routes such as `/web/art` are valid only when the subsystem/SAR
Web descriptor declares them explicitly as aliases.

Generated operation form indexes are separate from Web app routes. Use:

```text
/form/{component}
/form/{component}/{service}/{operation}
```

The `/web` namespace is for Web pages, admin/manual/dashboard pages, static
assets, and explicit aliases. It must not fall back to generated component form
indexes. If a component needs a human navigation page, provide a real Web app
page under `/web/{component}/{webApp}` or an explicit component entry app, and
link from that page to `/form/{component}` or operation-specific `/form/...`
routes.

## Component-Local Embedded Datastore

Use the embedded datastore internal DSL when a component needs durable
user-local data that is not a CNCF EntityStore collection. A typical example is
a GeoResolver local Gazetteer.

Preferred route:

```scala
for {
  store <- embedded_datastore("gazetteer")
  _ <- embedded_datastore_migrate(store, schemaStatements)
  rows <- embedded_datastore_read(
    store,
    "SELECT * FROM location_entry WHERE normalized_name = ?",
    Vector(name)
  )
} yield rows
```

By default CNCF stores the database under:

```text
~/.cncf/<component-name>/<store-name>.db
```

Configuration can override the root, component directory, or store path with
`cncf.local-data.root`, `cncf.local-data.<component-name>.dir`, or
`cncf.local-data.<component-name>.<store-name>.path`. Component code should not
open SQLite/JDBC directly; SQLite is the first backend behind the embedded
datastore abstraction, not the component-facing API.

## Delegated Operations

When a component delegates to another CNCF operation, the delegated action
should still use the same internal DSL helpers. Do not pass framework settings
by re-parsing local files in the caller if the callee can obtain them from the
current `ActionCall` context.

If a delegated component needs provider keys or runtime settings, prefer:

1. explicit operation argument when the operation contract exposes one;
2. `config_*` helper in the delegated `ActionCall`;
3. documented transitional compatibility fallback.

## SPI Usage

Use CNCF SPI contracts for optional provider functionality shared across
components. SPI methods that need runtime context should take the CNCF
`ExecutionContext` through the SPI protocol rather than reaching into global
state.

For example, AI runner provider integration should be exposed through the CNCF
AI runner SPI and consumed through the socket trait on the component. Component
logic should not directly instantiate provider-specific AI clients.

The same rule applies to other shared provider capabilities. GeoResolver and
ToolchainRunner integrations should use the CNCF-owned SPI contracts under
`org.goldenport.cncf.spi.geo.resolver` and
`org.goldenport.cncf.spi.toolchain.runner`. Textus components may implement
those provider surfaces, but consumers should depend on the CNCF SPI request
and response models rather than generated Textus operation classes.

Browser-backed scraping follows both boundaries deliberately. Application
components call the generated `TextusScraperApi.RenderPage` operation because
Textus Scraper owns HTML normalization. Textus Scraper then calls the
provider-neutral `ToolchainRunner.renderWebPage` SPI because ToolchainRunner
owns browser/process execution. Do not invoke Playwright, Node.js, Docker, or a
browser process directly from application or scraper component logic.

The browser SPI call must carry the current `ExecutionContext`. CallTree may
record requested/final host, status, engine, browser, provider component, and
tool execution, but must not record rendered HTML, cookies, credentials,
request headers, or raw browser payloads. Static HTTP operations must not gain
an implicit browser fallback; deployments select browser-backed operations or
drivers explicitly.

### Component-Specific API Dependencies

Use a Cozy-generated component API when a consumer needs the public operations
of one concrete component rather than a provider-neutral CNCF SPI. Declare the
exact provider CAR in the consumer build:

```scala
cozyCarDependencies += CarDependency("textus-scraper", "0.1.0-SNAPSHOT")
```

Declare the required API and multiplicity in CML with `spi-component-api` and
`spi-multiplicity`. Do not add the provider source project or
`component/main.jar` to the consumer's production dependencies. sbt-cozy must
compile the consumer from the provider's contract-only `spi/*-api.jar`.

When developing against a SNAPSHOT provider, publish its CAR to the normal
local CAR repository first:

```bash
cd ../textus-scraper
sbt --batch publishLocal
```

Then generate, compile, and run the consumer through the standard launcher.
The expected verification route is `cncf . server` or an equivalent normal
launcher command with an explicit test descriptor for isolated state. A direct
Java command with a flattened consumer/provider classpath is not deployment
evidence.

Treat these startup failures as contract diagnostics rather than adding a
classpath workaround:

- required component API or declared API JAR missing from the provider CAR;
- declared CAR coordinate absent from configured repositories;
- API class content or ABI hash conflict;
- consumer assembly coordinate inconsistent with `cozyCarDependencies`;
- runtime ABI type loaded independently by component classloaders.

Component implementation-only Maven dependencies belong in
`project.yaml` under `packaging.car.dependencies.local`. Use `shared` only when
several components intentionally require one shared runtime type identity. Do
not rely on a library being present accidentally on the launcher classpath.

### Test SPI Selection

When a component CAR already contains a test SPI provider, test execution should
select that provider through a test descriptor instead of creating a separate
test-only CAR. The intended startup shape is:

```bash
cncf dev command ... --textus.test.descriptor=./test.yaml
```

The descriptor is explicit and test-only. CNCF must not auto-load `test.yaml`
or `test.json` from the working directory, because that would let test wiring
leak into production startup.

The preferred descriptor shape selects an existing provider binding:

```yaml
kind: test-descriptor

assembly:
  spi:
    bindings:
      - socket:
          component: target-component
          contract: ai-runner
        provider:
          component: target-component
        selection:
          mode: test
```

If the provider is packaged as another component in the same test assembly,
the provider side should name that component explicitly:

```yaml
kind: test-descriptor

assembly:
  spi:
    bindings:
      - socket:
          component: target-component
          contract: ai-runner
        provider:
          component: target-test-spi
```

This mechanism selects a provider that is already present in the test runtime
assembly. It does not add Scala traits or JVM methods to the component at
runtime. If component logic calls a socket trait method directly, the component
must still mix in that socket trait. The test descriptor controls runtime
wiring and provider selection, not the component's compiled type.

Use `assembly.spi.bindings` for the ordinary case where the component already
declares or implements the needed socket and the test only wants to select a
different provider. Reserve any future `assembly.spi.sockets` metadata for
cases where the runtime needs explicit test-only socket metadata; it must not
be used as a substitute for compiled component APIs.

The initial implementation filters providers by provider component and then
uses the ordinary SPI contract plus `provider` / `mode` / `engine` selection.
`provider.service` is reserved for future service-level matching and is
rejected when specified. Use selection values when a component carries multiple
providers for the same SPI contract.

### Test Datastore and Test Home Selection

Use `test.yaml` to replace test-owned datastores without replacing the JVM
home. This keeps the normal CNCF home, assembly defaults, and repository
resolution available while moving component data under `target/`:

```yaml
kind: test-descriptor

runtime:
  datastore:
    type: local
    path: target/cncf.d/runtime.db

components:
  art-scene:
    datastore:
      application:
        type: local
        path: target/cncf.d/art-scene/application.db
```

Run the test descriptor explicitly:

```bash
cncf dev server --textus.test.descriptor=./test.yaml
```

For fully isolated tests, use the CNCF test wrapper instead of setting
`-Duser.home`:

```bash
cncf test --test-config ./test.yaml --home target/cncf.d/stage-home server
cncf test --temporary-home server
```

`cncf test` is normalized to the ordinary runtime mode after it injects
test-only configuration. It does not add a production runtime mode. The test
home overlay can inherit runtime configuration and repositories while keeping
local component datastores under the test home by default.

## Tests

For every handwritten operation:

- add executable specs around the generated/custom `ComponentFactory`;
- include Given/When/Then descriptions where the repository uses that style;
- assert behavior at the operation/result boundary, not only through helper
  internals;
- add focused tests for new internal DSL helpers before relying on them from a
  component.

Useful validation commands:

```bash
sbt --batch test
sbt --batch cozyBuildCAR
git diff --check
```

For development-directory integration, run the component through the `cncf`
launcher with `--component-dev-dir` for each active sibling component.

## Review Checklist

Before accepting component implementation code, check:

- Does handwritten logic express domain intent rather than persistence or
  runtime mechanics?
- Are configuration values obtained through `config_*` helpers?
- Is structured DSL parsing done through `parse_dsl_document`?
- Is component-local durable user data accessed through
  `embedded_datastore_*` helpers?
- Are raw stores absent from ordinary business logic?
- Are tenant, lifecycle, authorization, and logical delete concerns delegated
  to CNCF internal DSL / `UnitOfWork`?
- Are provider integrations exposed through SPI or component operations rather
  than direct client construction?
- Do tests cover the operation behavior and the intended internal DSL route?
- Are semantic time, random values, and IDs obtained through execution
  capabilities rather than ambient JVM APIs?
- Are delays and concurrency Job/Event-managed instead of using host sleep,
  threads, or executors?
- Is every retained host environment/property/filesystem access clearly a
  bootstrap, transport, repository, or provider boundary rather than component
  behavior?
