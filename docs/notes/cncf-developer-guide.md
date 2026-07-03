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

Inside an `ActionCall`, use protected CNCF helper methods before reaching for
lower-level runtime objects. If a needed helper does not exist, add or improve
an internal DSL helper instead of copying runtime mechanics into component
code.

Examples of preferred helper routes:

- configuration lookup: `config_string`, `config_int`, `config_double`,
  `config_boolean`;
- structured DSL/config parsing: `parse_dsl_document`;
- component-local user data: `component_local_data_dir`,
  `embedded_datastore`, `embedded_datastore_read`,
  `embedded_datastore_update`, `embedded_datastore_migrate`;
- entity, blob, association, child binding, job, event, HTTP, and shell
  behavior through existing `ActionCallFeaturePart` helper families;
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
- open component-local embedded databases or user data files directly when a
  CNCF internal DSL helper exists;
- hand-roll tenant filters, lifecycle checks, logical delete filtering, or
  entity identity searches;
- call raw `DataStoreSpace` / unrestricted `EntityStoreSpace` from business
  logic.

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
requests clients, or similar direct outbound clients for normal runtime
behavior. If a lower-level helper is missing, add the internal DSL helper or a
small component-local adapter that delegates to `UnitOfWorkOp.Http*`.

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
