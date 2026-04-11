# CNCF Architecture Overview

Status: draft

This document provides an overview of the CNCF runtime command architecture relevant to CLI and introspection.

## Layers

CLI
  -> Command Dispatch
  -> Selector Resolution
  -> Runtime Model
  -> Introspection
  -> Projection

## CLI Layer

Responsibilities:

- argument normalization
- help aliases (`--help`, `-h`)
- command help routing
- selector help rewrite for `cncf command`

## Runtime Model

Subsystem
  Component
    Service
      Operation

## Subsystem Construction

Subsystem construction is performed from resolved Components.

The construction path is:

1. Resolve the subsystem descriptor from explicit descriptor configuration or
   subsystem name.
2. Convert descriptor component bindings into repository discovery parameters.
3. Discover matching Components from configured repositories.
4. Add built-in Components unless the descriptor excludes them.
5. Collapse duplicate Component names with deterministic assembly selection.
6. Add the resulting Components into the Subsystem-owned ComponentSpace.
7. Rebuild the OperationResolver and assembly/security wiring views from the
   installed Components.

The Subsystem owns Component installation into subsystem scope. Components own
their internal port/binding installation. Once a Component is added to
ComponentSpace, its protocol services become visible through
OperationResolver.build and can be selected by the runtime invocation path.

Duplicate Component names are handled before ComponentSpace installation.
AssemblyReport.selectPreferred determines the selected Component, and the
dropped candidate is recorded as an assembly warning for admin and diagnostic
surfaces.

Binding lifecycle is intentionally separated:

- Component port/binding installation happens inside the owning Component.
- Subsystem construction installs Components into subsystem scope.
- Runtime execution uses the rebuilt OperationResolver and does not reinstall
  bindings during invocation.

## Selector Resolution

Selector resolution is deterministic and follows precedence:

1. subsystem meta
2. component meta
3. service meta
4. operation invocation

## Introspection Layer

Primary introspection namespace:

meta.*

Endpoints currently exposed:

meta.help
meta.describe
meta.components
meta.services
meta.operations
meta.schema
meta.openapi
meta.mcp
meta.statemachine
meta.tree
meta.version

## Projection Layer

Projection converts runtime model into response representations for CLI and API
surfaces, preserving a single source of structural truth.
