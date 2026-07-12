# CAR Development Start Guide

Status: current

## Purpose

This guide is the CNCF-side starting procedure for a new CAR component
project. It explains the expected development flow and points to the runtime
contracts that CNCF owns.

Cozy owns project scaffolding and CAR packaging commands. CNCF owns runtime
loading, component selection, repository resolution, activation, and execution
contracts. A new component project should therefore start from Cozy scaffolding
and then validate against the CNCF runtime rules documented here.

## When To Use This Guide

Use this guide when creating a new component repository that will publish a
CNCF Component Archive (`*.car`).

Do not use this guide for:

- a subsystem-only project (`*.sar`);
- a multi-module CAR+SAR application root, except as background for the
  component module;
- ad hoc classpath experiments that will not be packaged as CAR artifacts.

## Naming Inputs

Choose these values before running the scaffold command:

| Concern | Example | Rule |
|---|---|---|
| artifact name | `textus-sanpomap` | lower-kebab repository/artifact name |
| component name | `Sanpomap` | PascalCase CML component name |
| Scala package | `org.simplemodeling.textus.sanpomap` | stable generated/source package |
| organization | `org.textus` | sbt/Maven organization |
| version | `0.1.0-SNAPSHOT` | start with a snapshot during development |
| bounded context | `sanpomap` | domain boundary label |
| domain | `route-presentation` | runtime/catalog metadata label |

Keep the artifact name stable. Published CAR paths, catalog entries, launcher
selection, and local repository layouts use it.

## Scaffold The Project

Preferred command:

```bash
cozy init component \
  --save . \
  --name textus-sanpomap \
  --component-name Sanpomap \
  --display-name "Textus Sanpomap" \
  --organization org.textus \
  --package org.simplemodeling.textus.sanpomap \
  --version 0.1.0-SNAPSHOT \
  --kind car \
  --bounded-context sanpomap \
  --domain route-presentation \
  --gitignore \
  --readme \
  --tests \
  --overwrite-project-files
```

`cozy init component` is the project-initialization surface. It may read a
config file first and then apply CLI overrides.

The lower-level scaffold command is still valid when a CML model already
exists or when the caller needs the historical `car-sbt-project` behavior:

```bash
cozy car-sbt-project model.cml \
  --save . \
  --style car \
  --component Sanpomap \
  --package org.simplemodeling.textus.sanpomap \
  --organization org.textus \
  --name textus-sanpomap \
  --version 0.1.0-SNAPSHOT \
  --bounded-context sanpomap \
  --domain route-presentation \
  --gitignore \
  --readme \
  --tests \
  --overwrite-project-files
```

When no model file is passed, `car-sbt-project` creates a sample model. Replace
that sample immediately with the real component model.

## Expected Source Layout

A normal CAR project should contain these source inputs:

```text
build.sbt
project/plugins.sbt
project.yaml
conf/cozy/config.yaml                 # shared Cozy operation defaults, when needed
conf/cncf/launcher.yaml               # shared launcher defaults, when needed
src/main/cozy/<artifact>.cml           # component model
src/main/scala/<package>/impl/...      # handwritten implementation hooks
src/main/car/...                       # component-owned CAR-root resources
src/main/car/assembly-descriptor.yaml  # optional component-local assembly defaults
src/main/web/...                       # public Web app resources, when needed
src/main/web-inf/web.yaml              # Web descriptor source, when needed
src/main/web-inf/form.yaml             # Static Form descriptor source, when needed
src/main/web-inf/admin.yaml            # Admin descriptor source, when needed
src/test/scala/...                     # component/factory/action tests
```

Do not edit generated runtime descriptors inside a packaged CAR. Source Web
metadata belongs under `src/main/web-inf`; private Web helper resources belong
under `src/main/web/WEB-INF`; public pages and assets belong under
`src/main/web`.

For `packaging.kind: car`, the CAR-root source directory defaults to
`src/main/car`. Do not repeat `packaging.car.source_dir: src/main/car` in
ordinary CAR projects. Set `packaging.car.source_dir` only when the project
intentionally uses a non-standard CAR-root source layout.

Use `src/main/car/assembly-descriptor.yaml` when the component needs
component-local assembly defaults. This file is packaged at the CAR root as
`assembly-descriptor.yaml` and is the place for component-provided wiring,
SPI/provider defaults, and required provider component declarations. It is not
a place to embed provider CAR artifacts; those still come from the standard
component repository, `repository.d`, or an explicit development override.

Local-only files should stay out of git:

```text
.cozy/
.cncf/
.textus.conf
repository.d/
component.d/
car.d/
target/
```

## First Edit After Scaffolding

1. Replace the scaffold CML with the real component model.
2. Keep generated CRUD and entity operations in CML when the behavior is data
   management.
3. Add handwritten operations only for domain logic that cannot be represented
   by generated operation implementations.
4. Implement handwritten action calls in the generated/custom `ComponentFactory`
   extension points.
5. Keep `ComponentFactory` thin. Use it as the facade that connects generated
   metadata to handwritten behavior. Move growing domain/application behavior
   into `*Logic` modules under `src/main/scala/<package>/impl`.
6. Bind stable config, provider, policy, and adapter choices in `*Logic`; keep
   request `ExecutionContext` access at the generated `ActionCall` boundary via
   `ActionCall.Core` and CNCF internal DSL helpers.
7. Use role-specific helper modules below the logic layer when useful:
   `*Store`, `*Client`, `*Strategy`, `*Policy`, `*Renderer`, and `*Workflow`.
   Avoid naming domain behavior `*Factory`; factory names are reserved for
   CNCF/Cozy construction and adapter points.
8. Keep operation inputs and outputs as CML values or records; avoid command-
   local string parsing.
9. Add tests around component factory creation, facade boundaries, and each
   handwritten operation or extracted logic module.

For component logic rules, read:

- `docs/notes/cncf-developer-guide.md`
- `docs/notes/application-logic-guideline.md`
- `docs/notes/internal-dsl-guideline.md`
- `docs/notes/unitofwork-guideline.md`
- `docs/design/component-and-application-responsibilities.md`

Handwritten `ActionCall` logic should use protected CNCF internal DSL helpers
for runtime concerns. In particular, use `config_*` helpers for configuration
and `parse_dsl_document` for structured DSL/config parsing instead of directly
reading runtime parameter maps, subsystem configuration, or low-level config
loader classes.

## Development Loop

Compile and test the component first:

```bash
sbt --batch compile
sbt --batch test
```

Build a CAR when the archive layout must be checked:

```bash
sbt --batch cozyBuildCAR
```

During normal edit/run work, prefer development-directory startup. This avoids
rebuilding a CAR on every edit and lets CNCF read source CAR metadata from the
project:

```bash
cncf --component-dev-dir . server
```

Use an expanded CAR directory only for loader/debug inspection:

```bash
cncf --component-car-dir car.d server
```

Use packaged activation only when testing the packaged artifact itself:

```bash
cncf --component-dir component.d --textus.component=textus-sanpomap server
```

## Dependency And Provider Components

A CAR should normally stay lightweight. The CAR contains the component jar and
component resources; Maven-style dependencies should be declared for Cozy/CNCF
to resolve instead of copying every dependency into the archive.

For provider components:

1. Prefer published standard components from the default component repository.
2. Put fixed local CARs under `repository.d` when they should be searchable but
   not automatically active.
3. Declare typed provider CAR dependencies with `cozyCarDependencies`; publish
   SNAPSHOT providers locally before compiling the consumer.
4. Do not embed provider CARs inside the application component CAR.

Example consumer build declaration:

```scala
cozyCarDependencies += CarDependency("textus-scraper", "0.1.0-SNAPSHOT")
```

The provider CAR must publish `component-api-descriptor.json` and its declared
`spi/*-api.jar`. Consumer compilation uses only that API artifact, not the
provider source project or implementation JAR. Keep any direct source-project
dependency test-only and verify deployment separately through the standard
launcher.

For a provider implementation library such as JSoup, declare a CAR-local Maven
dependency in the provider's `project.yaml`:

```yaml
packaging:
  car:
    dependencies:
      local:
        - "org.jsoup:jsoup:1.17.2"
```

Use `dependencies.shared` only when assembly components deliberately share one
library type identity. Use CAR `lib/` only for a dependency that cannot be
published to a Maven-style repository.

Related authority documents:

- `docs/design/packaged-source-activation.md`
- `docs/design/component-dependency-loading.md`
- `docs/design/assembly-descriptor.md`

## Packaging And Publication Checks

Before publishing or handing off a CAR, run at least:

```bash
sbt --batch test
sbt --batch cozyBuildCAR
```

For local repository publication, use the sbt-cozy tasks provided by the
project scaffold:

```bash
sbt --batch cozyPublishLocalCar
```

For warehouse/distribution publication, use the project-local release procedure
and keep `project.yaml` current. A CAR project should declare:

```yaml
project:
  name: textus-sanpomap
  kind: car

packaging:
  kind: car

warehouse:
  repository_artifacts:
    include:
      - car
    modules:
      - textus-sanpomap
```

`packaging.car.source_dir` is intentionally omitted here. The default CAR-root
source for `kind: car` is `src/main/car`; only non-standard layouts should
override it.

## Runtime Selection Rules

CNCF distinguishes search from activation:

- `repository.d` and configured repositories are search sources.
- `component.d` is an active packaged component source.
- `--component-dev-dir` activates a development project as a CAR-equivalent
  source.
- `--component-car-dir` activates an expanded CAR directory for debugging.
- `--textus.component=<name>` selects a component by name from search sources.

For production-style startup, select by component name after the CAR is
published into the component repository or local cache:

```bash
cncf --textus.component=textus-sanpomap server
```

## Completion Checklist

A new CAR component is ready for normal feature work when:

- the Cozy scaffold exists and no sample names remain;
- `src/main/cozy/<artifact>.cml` names the real component, package, services,
  operations, values, and entities;
- `project.yaml` declares a CAR artifact and module name;
- handwritten implementation hooks compile;
- ServiceLoader / component factory tests pass;
- `sbt --batch test` passes;
- `sbt --batch cozyBuildCAR` produces a CAR;
- development startup works with `cncf --component-dev-dir . server` or the
  project-local run script;
- any typed provider component dependency is declared through
  `cozyCarDependencies` and repository-resolved instead of being embedded into
  the CAR;
- component-only third-party libraries are declared under
  `packaging.car.dependencies.local` rather than assumed from the launcher
  classpath.
