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

### Development-time auto-activation

The following are development-time activation paths:

- `--discover=classes`
  - discovers compiled classes from the current workspace
  - auto-activates the discovered components
- `car.d`
  - expanded component shape
  - auto-activates
- `sar.d`
  - expanded subsystem shape
  - auto-activates

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
- `car.d`
  - expanded development/debug component activation
- `sar.d`
  - expanded development/debug subsystem activation

## Operational Direction

The preferred operational model is:

1. use subsystem name or component name for explicit startup
2. use `repository.d` when packaged artifacts should be searchable but not automatically active
3. use `component.d` when packaged artifacts should be active inputs
4. use `car.d`, `sar.d`, or `--discover=classes` for development-time workflows

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
