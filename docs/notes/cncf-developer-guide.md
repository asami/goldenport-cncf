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

## Local Server Ports

CNCF assigns separate local server ranges so that multiple Textus artifacts can
run on one machine: CAR starts at `18000`, and SAR starts at `28000`. Each
production artifact records its assigned `textus.server.default-port` in
`project.yaml` (CAR) or `subsystem-descriptor.yaml` (SAR); the machine registry
is only the runtime mirror and undeclared-development fallback. Each
artifact receives one consecutive stable default in the machine-local
assignment registry. Simultaneous additional instances use the dynamic range
beginning at `38000`. Component code must not choose ports itself.

Use `textus.server.port` only when an application or operator requires a fixed
endpoint. An explicit value is authoritative and is not automatically moved to
another port. See `docs/design/server-port-allocation.md` for the complete
classification and precedence contract.

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

### Initialization Parameters

Use factory-owned initialization parameters when a component needs a typed,
stable value before it can complete initialization. Declare each key through
`initializationParameterDeclarations`, then resolve it from
`ComponentInit.initializationParameters` in the protected
`initialize_component_c` hook. Return a structured `Consequence` when a
component-domain combination is invalid.

Do not read `Subsystem.configuration`, a descriptor map, environment variables,
or request properties from component initialization code. CNCF selects the
component instance and applies packaged, assembly, instance, runtime, and
explicit test layers before invoking the hook. The snapshot has no raw-map or
arbitrary-name accessor. Use `ComponentConfigurationKey` and the protected
ActionCall DSL separately for operation-time configuration.

Factories without declarations receive
`ComponentInitializationParameters.empty`. New runtime integrations should
call `createPrimaryC`, `createComponentletC`, or bundle `createC` so failures
remain structured until subsystem admission.

Inside an `ActionCall`, use protected CNCF helper methods before reaching for
lower-level runtime objects. If a needed helper does not exist, add or improve
an internal DSL helper instead of copying runtime mechanics into component
code.

## Rules And Decision Tables

Use CNCF Rules when domain logic is a stable, explainable decision rather than
an incidental implementation branch: consumption-tax calculation, product
price lists, tiered discounts, eligibility, validation constraints, and derived
facts are the initial drivers. The authoritative model and behavior contract is
`docs/design/rule-engine-inference-runtime.md` and
`docs/spec/rule-engine-inference-runtime.md`.

Executable component code builds immutable `RuleSet`, `RuleProgram`, Facts, and
decision tables with CNCF-owned types, then evaluates them through a
`RuleEngineSocket`. Use an `InferenceEngineSocket` only for derivation-only
forward inference. This retains provider substitution, consumer-side SPI trace,
structured `Consequence` failures, and payload-safe diagnostics. Do not call a
provider implementation, repository, external rule runtime, or evaluator
internals from an ActionCall.

For table-oriented rules, bind each decision-table input column explicitly to a
`FactId`. A missing Fact is a normal no-match; do not create an implicit default
through datastore/configuration lookup. Table output remains a named decision
result and is not automatically merged into calculation values. Apply a
decision result to business state only through the normal ActionCall/internal
DSL path.

Subsystem descriptors may declare read-only RuleSet metadata at root
`ruleSets`, but Phase 32 deliberately does not define executable YAML/JSON rule
programs, table rows, expressions, or action plans. Keep executable program
construction in typed component code until a later declarative rule-language
slice defines syntax, validation, and review boundaries. Production Rule plans
are evaluated first and fired only through `RuleActionAdmission`; they must not
perform direct store mutation or thread creation.

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

- declared runtime configuration: `component_configuration` with a
  `ComponentConfigurationKey`;
- operation-input or transitional compatibility lookup: `config_string`,
  `config_int`, `config_double`, `config_boolean`;
- structured DSL/config parsing: `parse_dsl_document`;
- component application datastore selection:
  `use_component_application_datastore`, `component_datastore`;
- durable component state: a purpose-specific internal DSL or component-owned
  persistence port backed by the admitted component datastore;
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
- normally avoid opening a database, using JDBC/SQLite/vendor APIs,
  constructing SQL, or discovering a datastore path/credential from component
  application or domain logic; see `Durable Component Persistence` for the
  narrow exception rule;
- hand-roll tenant filters, lifecycle checks, logical delete filtering, or
  entity identity searches;
- call raw `DataStoreSpace` / unrestricted `EntityStoreSpace` from business
  logic.

### Component Initialization Parameters

Use a factory's `initializationParameterDeclarations` when a value must be
resolved once for one `ComponentInstanceId` before component-specific
initialization. Declare ordinary values with `ComponentParameterKey` typed
constructors. Declare credential locators only with
`ComponentParameterKey.requiredSecretReference` or
`optionalSecretReference`; initialization receives an opaque
`SecretReference`, never credential material.

Do not declare a credential locator with `requiredString` or a custom decoder.
Do not render a returned secret reference in a response, log, CallTree
attribute, diagnostic, or exception. `confidentialRequired` represents material
that is intentionally unavailable through the component initialization
boundary and therefore resolves as a structured failure. Secret material may
be consumed only by an authorized runtime-owned provider or driver.

Initialization parameters are not operation-time configuration. Request
properties, action input, ambient environment lookup, and
`ComponentConfigurationAccess` cannot override the immutable initialization
snapshot.

### Declared Runtime Configuration and External-tool Inputs

Use `component_configuration(key)` for component-owned runtime configuration
that must not be controlled by operation input. A
`ComponentConfigurationKey` fixes the expected type, required/optional state,
and confidentiality before an ActionCall begins. The runtime resolves declared
values in component, subsystem, then runtime precedence and returns safe
provenance with the typed result.

Use `config_*` only for an operation-specific setting or an explicitly
transitional compatibility route. It permits request property precedence and is
therefore not the canonical route for a protected provider mode, credential
locator, resource-root policy, or executable policy.

Use `ComponentConfigurationKey.requiredSecretReference` or
`optionalSecretReference` for credential locators. Component behavior receives
an opaque `SecretReference`, not the underlying secret. Do not include that
reference in a response, log, CallTree attribute, or ordinary failure display.

For a bounded external-tool input, use this sequence:

1. obtain a named, admitted snapshot through
   `read_resource_tree(reference, limits)`;
2. create `ProcessExecutionResourceTreeInput` with
   `createC(snapshot, workAreaRelativeTarget, requestedLimits)`;
3. place that logical input in `ProcessExecutionRequest`; and
4. call protected `process_exec(request)`.

The runtime owns the executable location, fixed argument template, fixed
environment bindings, admission grant, WorkArea materialization, cancellation,
and artifact cleanup. A component may tighten limits but must not broaden
them. A non-zero process exit remains a neutral terminal result until the
component/provider adapter interprets it. Never replace this route with a host
path, `ProcessBuilder`, shell command, raw driver call, or ambient environment
lookup.

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
- use declared configuration and bound execution assumptions rather than
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

For an end-to-end replay executable specification, create two independent
runtime/subsystem/component graphs from the same controlled profile and run the
same invocation sequence through the normal ComponentLogic and ActionCall/UoW
path. Compare business data plus CNCF-owned Job, Task, retry, Event, timestamp,
ID, ordering, and sanitized profile evidence. Do not reuse one mutable runtime
or share its random, ID, clock, or scheduler instances between replay runs.

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

## Durable Component Persistence

Use this decision flow before adding persistence to a component:

1. Keep state in memory only when it is explicitly transient and safely
   rebuildable.
2. For ordinary domain records, model generated entities and use the generated
   EntityStore/internal-DSL route.
3. For durable state that is not an EntityStore collection (for example,
   immutable review reports, provider cursors, or bounded application audit
   records), define a component-owned persistence port and purpose-specific
   internal DSL operations. Its framework adapter uses the admitted component
   datastore.

### Entity, Aggregate, and View

Ordinary component persistence starts with the Entity layer. Model the durable
domain records as Entities, let CNCF own their datastore mapping and lifecycle,
and keep the durable identity and record contract there. Do not replace that
layer with an application-specific database schema merely because one current
backend makes it convenient.

Use an Aggregate for a state-changing business operation that spans one or more
Entities, needs invariant enforcement, or requires an explicit consistency
boundary. The Aggregate receives the command through the internal DSL/
`UnitOfWork` path and coordinates Entity changes; it does not issue its own
database reads or writes.

Use a View for read-side queries, task-oriented projections, and derived
presentation state. A View projects admitted Entity/Aggregate state through the
runtime query boundary; it is not an independently maintained shadow database
or a reason to bypass Entity lifecycle, authorization, or datastore
observability.

This is also CNCF's CQRS responsiveness route. An Aggregate command establishes
the required durable Entity consistency boundary, then emits the admitted
change/projection work that updates reactive Views. A caller can receive an
immediate command outcome without waiting for unrelated read-model rendering or
downstream presentation work. Each View must state its freshness, ordering, and
failure semantics; an asynchronous projection must never pretend that a stale
View is a read-your-writes result.

CNCF may retain admitted Entity/working-set state in memory and serve eligible
Views from that managed state. This improves response latency and sustainable
throughput by avoiding unnecessary datastore round trips and by separating
write consistency from read projection. It is a runtime-managed persistence
optimization, not permission for a component to create an unbounded private
cache or a second source of truth. Memory limits, invalidation, recovery,
authorization, observability, and fallback-to-datastore behavior remain CNCF
responsibilities and require executable evidence for the selected profile.

This separation keeps command invariants, durable Entity identity, and read
projection explicit while preserving replacement of the underlying datastore.
Use a separate typed persistence port only for state that is genuinely outside
the Entity/Aggregate/View model, and document why it cannot be represented by
an Entity or derived View.

The strong default is not to make SQLite, JDBC, SQL, a database connection, a
database URL, a file path, or a vendor-specific migration part of component
behavior. The component's domain/application layer should depend on its typed
persistence port; the framework/infrastructure adapter normally uses
`DataStoreSpace` and datastore record/query operations.

This is a strong architectural recommendation, not mere API tidiness. Direct
backend dependence erodes the advantages gained by adopting component
technology: a component can no longer move unchanged between local and shared
storage, be independently assembled/deployed, remain isolated while sharing
infrastructure, or have its runtime backend substituted and tested through one
framework boundary. The immediate shortcut therefore becomes a component
lifecycle, portability, and operational-cost liability.

It is also a major security and observability concern. A direct connection can
bypass CNCF-owned credential handling, component/tenant authorization,
collection admission, secret redaction, audit policy, and the bounded failure
vocabulary. It likewise bypasses the `DataStoreSpace` CallTree/metrics/error
chokepoint, leaving storage latency, failures, retries, record scope, and the
causal relationship to an operation unobservable or inconsistently recorded.
For persistence with security, audit, or operational significance, the
datastore route is therefore the expected design, not an interchangeable
coding preference.

A common physical database is supported through one configured
`DataStoreSpace`. It is still not a shared application namespace: a component
may read and write only its own admitted component datastore and named
collections. It must not inspect, join, enumerate, modify, or migrate another
component's records. Record-model changes remain component-owned and use
supported datastore operations rather than backend DDL.

Tests should exercise the same internal-DSL/persistence-port route using a
configured datastore fixture. A local SQLite profile is valid integration
evidence, but component tests should not import SQLite/JDBC classes or open the
database directly.

An exception is possible only when a framework/infrastructure adapter cannot
express a required capability through the datastore abstraction. Keep it out of
domain/application code, document why the datastore route is insufficient,
bound it to one adapter, and add provider-specific integration evidence. Treat
the exception as a CNCF capability-gap candidate rather than a new default.

See `docs/notes/internal-dsl-guideline.md` and
`docs/journal/2026/07/2026-07-23-datastore-boundary-and-shared-database-decision.md`
for the framework-wide rule and rationale.

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

Framework-owned persistence adapters can request a named side-car datastore
through the internal DSL by passing a store name:

```scala
component_datastore("crawler-cache")
```

Avoid exposing this handle to domain/application algorithms. Prefer typed
component persistence operations instead; the adapter maps them to the
component-owned datastore collection(s). An exceptional direct adapter must
follow the documented exception rule above.

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

For newly declared component runtime configuration, prefer
`component_configuration(ComponentConfigurationKey...)` as described above.
The older `config_*` helpers remain valid for operation-input and compatibility
cases; their request-property precedence makes them inappropriate for settings
that must stay runtime-owned.

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

### Static Web Rendering and First-Render Context

Use Static Web as the normal human-facing component application path. The
server resolves the execution context, locale messages, route query, and page
View before sending HTML. Do not build a default-language HTML shell and use
browser REST calls to fetch its primary content, determine its workspace, or
replace its language after first paint.

Avoid browser REST for ordinary page rendering. A page with several hydrated
regions multiplies HTTP dispatch, serialization, authorization, and datastore
work for every user. Compose the full initial page View on the server and send
it in the document. REST is for external clients, automation, and carefully
bounded browser enhancements, not for initial application-page assembly.

Normal Web writes use aggregate-command forms and Post/Redirect/Get. REST and
Form API remain public integration surfaces and may support bounded progressive
enhancements, but they are not the bootstrap path for an ordinary page.

Use an asynchronous browser request only for a narrow exceptional region:
genuinely live state such as a notification badge, progress for a long-running
command, or a browser-only surface such as Canvas. Embed the initial model in
the rendered HTML whenever it was available to the server; do not make a
second request merely to retrieve the same page data.

CNCF injects one framework-owned JSON script-data block into a rendered Static
Web document:

```html
<script id="textus-page-context" type="application/json"></script>
```

Read it synchronously only when a bounded enhancement needs Web-safe execution
metadata; server templates already use the same resolved context for initial
locale messages and page rendering:

```javascript
function readPageContext(document) {
  const element = document.querySelector("#textus-page-context");
  return element ? JSON.parse(element.textContent || "{}") : {};
}

const execution = readPageContext(document).execution || {};
```

Use `execution.locale`, `execution.timezone`, `execution.applicationMode`, and
the other documented public fields only for local progressive enhancement. The
stable integration identifiers are `#textus-page-context` and its `execution`
member; do not depend on a generated DOM path or on the block's exact sibling
position. Treat additional JSON members as additive.

Do not call an application operation such as `DescribeApplication` to discover
execution locale or timezone. Such operations remain business-state APIs.
Browser language, local storage, client-side language replacement, and
hidden-until-fetch rendering are not fallbacks for CNCF execution context. An
enabled language query override produces a newly server-rendered document; it
does not redefine authorization context or translate an existing page in
place. See `docs/spec/static-web-application.md` for the full contract.

## Component-Local Embedded Datastore

The legacy embedded-datastore helper family is framework-owned local
provisioning infrastructure. Strongly avoid introducing new component
application logic that sends SQL statements through it. New durable non-entity
state should follow the `Durable Component Persistence` flow above: define a
typed persistence port and internal DSL operation, then implement the adapter
with the component datastore abstraction.

The launcher/runtime may still provision a local component datastore under a
user-local location for development. That path and its SQLite implementation
are not a component contract. The same persistence port must work unchanged
when the runtime instead binds an external or common datastore.

## Delegated Operations

When a component delegates to another CNCF operation, the delegated action
should still use the same internal DSL helpers. Do not pass framework settings
by re-parsing local files in the caller if the callee can obtain them from the
current `ActionCall` context.

If a delegated component needs provider keys or runtime settings, prefer:

1. explicit operation argument when the operation contract exposes one;
2. `component_configuration` with a declared key in the delegated
   `ActionCall`;
3. `config_*` only for a documented transitional compatibility fallback.

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

## Aggregate Mutation and OCC

Use the Aggregate DSL according to where the replacement Aggregate is built:

```scala
aggregate_update(
  "person",
  id,
  "updatePerson",
  Consequence.success(updated)
)

aggregate_command[Person]("person", id, "renamePerson") { current =>
  Consequence.success(current.rename(name))
}
```

`aggregate_update` is for CRUD/Form/REST-style replacement values.
`aggregate_command` is for domain behavior that must run against the
authoritative Aggregate loaded by CNCF. Ordinary application logic does not
pass a revision to either method.

OCC is opt-in through an explicit `Optimistic` Entity or collection concurrency
policy. Use `aggregate_update_observed` or `aggregate_command_observed` only at
a strict transport boundary that already has a caller-observed revision. Do not
add that revision to a domain command parameter.

Generated Web Form updates and REST requests with a strong `If-Match` validator
are framework ingress adapters. They select `Optimistic` for that strict
mutation attempt while leaving the Entity's ordinary `None` default unchanged.
Component code should use the protected DSL rather than constructing an
`EntityStoreSave`, `EntityStoreUpdate`, or `EntityStoreUpdateById` operation
directly.

For a managed `SimpleEntity`, ordinary `None + AlwaysWrite` DSL mutation may
use the provider-native direct path. Explicit optimistic/observed mutation may
use provider-native compare-and-set. `WriteIfChanged`, content-bearing
mutation, atomic side effects, and providers without the required native
capability remain on the guarded path. These are framework execution choices;
component code must not call `mutateEntityDirect` or `compareAndSetEntity`
directly.

The direct path does not bypass authorization, transition validation,
framework-managed revision advancement, or structured stale handling.
Record/snapshot-returning DSL routes request authoritative provider readback
when needed. A stale optimistic attempt evicts resident state before returning
failure.

When reusable domain behavior lives outside the concrete `ActionCall`, model it
as an `ActionBehavior` and pass the originating `ActionCall.Core` into that
behavior. The behavior can then use `entity_update` or
`entity_update_internal` while preserving the caller's component, execution,
correlation, authorization, UnitOfWork, and observability context. Do not make
shared helper methods construct `UnitOfWorkOp` directly, and do not reconstruct
`UnitOfWorkAuthorization` in application code.

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

The full CNCF suite completes with a 1 GiB sbt heap. Do not add `-J-Xmx4G` to
routine validation commands. A larger sbt JVM is a temporary diagnostic
override, not a test prerequisite.

For development-directory integration, run the component through the `cncf`
launcher with `--component-dev-dir` for each active sibling component.

## Review Checklist

Before accepting component implementation code, check:

- Does handwritten logic express domain intent rather than persistence or
  runtime mechanics?
- Are protected runtime settings obtained through declared
  `component_configuration` keys, with `config_*` limited to operation input
  or explicit compatibility behavior?
- Is structured DSL parsing done through `parse_dsl_document`?
- Does every durable non-entity state use a purpose-specific internal DSL or
  persistence port backed by the admitted component datastore?
- Are SQLite/JDBC/SQL, connections, backend paths, and raw stores strongly
  avoided in ordinary business logic and component tests? If an exception
  exists, is it infrastructure-only, documented, bounded, and covered by
  provider-specific integration evidence?
- Does the persistence design preserve the component benefits of backend
  substitution, independent assembly/deployment, component-data isolation, and
  common-infrastructure coexistence?
- Does it retain the CNCF security boundary for credentials, authorization,
  tenant/component scope, redaction, and audit rather than creating a direct
  backend bypass?
- Does it retain the datastore observability boundary so CallTree, metrics,
  failure classification, and operation-to-storage causality remain visible?
- When a common datastore is configured, does the component remain confined to
  its own component datastore and named collections?
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
