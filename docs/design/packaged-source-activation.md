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

### Packaged activation

`component.d` is the local active packaged directory.

- `--component-dir <path>`
  - adds packaged artifacts directly to the active set
- packaged artifacts in `component.d`
  - are treated as active inputs

## Name-Based Selection

The primary explicit activation mechanisms are:

- `--textus.runtime.component=<name>`
  - select a packaged component by name from search sources
- `--textus.runtime.subsystem=<name>`
  - select a subsystem by name from search sources

When a component or subsystem is selected by name, the runtime resolves the
corresponding packaged artifact from the search set and activates the resolved source.

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

## Legacy Material

Earlier repository-only design drafts are kept under:

- `docs/design/history/2026/04/component-repository.md`
- `docs/design/history/2026/04/component-loading.md`
