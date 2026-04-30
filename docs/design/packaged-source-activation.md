# Packaged Source Activation

Status: current

This document is the current design authority for packaged component and subsystem
search and activation.

## Model

The runtime distinguishes three concerns:

- discovery
  - how development-time classes are found
- search
  - where packaged `CAR` / `SAR` artifacts are searched
- activation
  - which packaged artifacts become active inputs to the running subsystem

These concerns are intentionally separate.

## Current Runtime Rules

### Development-time execution

The following are development-time execution paths:

- `--discover=classes`
  - discovers compiled classes from the current workspace
  - auto-activates the discovered components
- `--component-dev-dir <path>`
  - treats an sbt/cozy development directory as a CAR-equivalent component
    source
  - reads the development runtime classpath from
    `target/cncf.d/runtime-classpath.txt`
  - infers the component/subsystem name from the descriptor in `src/main/car`
    when available, so `--textus.component` is not required
  - uses `src/main/car` as the canonical CAR-root resources that will be
    packaged into the CAR root
  - uses `src/main/web` as the development and packaging source for the Web app
    surface
  - does not package or activate `docs/`; component-facing documentation must
    come from `src/main/car` or another `src/main` artifact source
- `--component-car-dir <path>`
  - explicitly runs an expanded CAR directory
  - intended for CAR loader debugging, archive inspection, and reproducing a
    packaged CAR without zipping it first
  - this is the route for a standalone `car.d` directory
- `--subsystem-dev-dir <path>`
  - treats an sbt/cozy application development root as a SAR-equivalent
    subsystem source
  - reads SAR settings from `<path>` or `<path>/subsystem`
  - reads component development output from `<path>/component`
  - infers the subsystem name from the subsystem descriptor, so
    `--textus.subsystem` is not required for the standard development root
- `--subsystem-sar-dir <path>`
  - expanded subsystem shape
  - explicitly runs an expanded SAR directory
  - intended for SAR loader debugging, archive inspection, and reproducing a
    packaged SAR without zipping it first
  - this is the route for a standalone `sar.d` directory

### Packaged search

`repository.d` is the local packaged search directory.

- `--repository-dir <path>`
  - adds packaged artifacts to the search set
- packaged artifacts in `repository.d`
  - are searchable
  - are not auto-activated by default

The standard component repository is also a packaged search source.

- default local cache root: `~/.cncf/repository`
- standard component layout:
  - `org/simplemodeling/car/<name>/<version>/<name>-<version>.car`
- standard subsystem layout:
  - `org/simplemodeling/sar/<name>/<version>/<name>-<version>.sar`
- subsystem descriptors may declare components by `name + version`
  and rely on this standard repository without extra runtime configuration
- component CAR assembly defaults may also declare required components by
  `name + version`; the runtime resolves those provider/application
  dependencies from the same search sources

### Packaged activation

`component.d` is the local active packaged directory.

- `--component-dir <path>`
  - adds packaged artifacts directly to the active set
- packaged artifacts in `component.d`
  - are treated as active inputs

## Name-Based Selection

The primary explicit activation mechanisms are:

- `--textus.component=<name>`
  - select a packaged component by name from search sources
- `--textus.subsystem=<name>`
  - select a subsystem by name from search sources

When a component or subsystem is selected by name, the runtime resolves the
corresponding packaged artifact from the search set and activates the resolved source.


## Repository-Based Production Startup

The production packaged model is name-based. Operators deploy component and
subsystem artifacts into the component repository, then start the runtime by
component name or subsystem name instead of passing direct artifact paths.

Example repository layout:

```text
~/.cncf/repository/
  org/simplemodeling/car/textus-user-account/<version>/textus-user-account-<version>.car
  org/simplemodeling/car/cwitter/<version>/cwitter-<version>.car
  org/simplemodeling/sar/cwitter/<version>/cwitter-<version>.sar
```

Component startup selects an application component CAR and treats its
component-local `assembly-descriptor.*` as deemed-subsystem defaults:

```bash
cncf --textus.component=cwitter server
```

Subsystem startup selects the deployed SAR and lets the SAR descriptor override
component-car defaults where needed:

```bash
cncf --textus.subsystem=cwitter server
```

Provider components such as `textus-user-account` are normal component CARs in
the same repository. They are resolved by name and version from descriptors;
they are not embedded inside the application CAR.

## Directory Roles

- `repository.d`
  - packaged search
- `component.d`
  - packaged activation
- `src/main/car`
  - canonical component-owned CAR-root resources
  - packaged into the CAR root by the build
  - used by `--component-dev-dir` without building a CAR first
- `src/main/web`
  - component Web app resources
  - kept separate from CAR metadata because it is the Web surface, not the
    archive descriptor layer
- `car.d`
  - expanded CAR directory for loader/debug/inspection use
  - no longer a default active component source
  - run explicitly with `--component-car-dir car.d`
- `sar.d`
  - expanded SAR directory for loader/debug/inspection use
  - no longer a default active subsystem source
  - run explicitly with `--subsystem-sar-dir sar.d`
- `subsystem/`
  - subsystem development module under an application root
  - contains SAR descriptor/configuration for `--subsystem-dev-dir`

## Development Startup Patterns

Component development should normally run from the development directory:

```bash
cncf --component-dev-dir . server
```

This path avoids building a CAR on every edit. It keeps local classpath entries
as development inputs, reads CAR-root metadata from `src/main/car`, and lets the
runtime infer the component/subsystem identity from that metadata.

Subsystem development should normally run from the application root:

```bash
cncf --subsystem-dev-dir . server
```

The root may contain `subsystem/` and `component/` modules. The subsystem
descriptor in the root or `subsystem/` selects the subsystem; the component
development output under `component/` supplies the component implementation.

Expanded archive directories are separate debugging inputs:

```bash
cncf --component-car-dir car.d server
cncf --subsystem-sar-dir sar.d server
```

Use these when validating the archive layout itself or comparing behavior with a
packaged `*.car` / `*.sar`. They are not the preferred edit/run loop for sbt or
Cozy projects.

## Operational Direction

The preferred operational model is:

1. use subsystem name or component name for explicit startup
2. use `repository.d` when packaged artifacts should be searchable but not automatically active
3. use `component.d` when packaged artifacts should be active inputs
4. use `--component-dev-dir`, `--component-car-dir`, `--subsystem-dev-dir`,
   `--subsystem-sar-dir`, or `--discover=classes` for development-time workflows

For a one-component application that depends on standard provider components,
the component CAR may be selected by component name in production or activated
with `--component-file` during local development. Required provider component
CARs should be resolved from the standard repository or from `repository.d`
during local development. Provider component CARs should not be embedded inside
the application CAR.

## Legacy Material

Earlier repository-only design drafts are kept under:

- `docs/design/history/2026/04/component-repository.md`
- `docs/design/history/2026/04/component-loading.md`
