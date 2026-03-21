# CNCF Meta API Specification

Status: draft

This document defines the `meta.*` runtime introspection API exposed by CNCF components.

## Namespace

meta.*

Core APIs currently exposed:

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

## Projection Principle

Meta responses are generated via projection over canonical runtime model objects.

Runtime Model
  -> Projection
  -> meta.* response

Direct ad-hoc formatting in command routing is not part of the API contract.

## meta.help

Human-oriented hierarchical help.

Examples:

cncf command meta.help
cncf command domain.meta.help

Output:

- default: YAML
- with `--json`: JSON

## meta.describe

Machine-oriented detailed description of component/service/operation targets.

Current safe usage patterns:

cncf command domain.meta.describe entity.createPerson
cncf command domain.meta.describe domain.entity.createPerson

## meta.components

Returns component-level structural view from subsystem context.

## meta.services

Returns service-level structural view for a component target.

## meta.operations

Returns operation-level structural view for a service target.

## meta.schema

Exports schema projection for component/service/operation targets.

## meta.openapi

Exports component interface as OpenAPI JSON projection.

## meta.mcp

Exports component interface as MCP tool JSON projection.

## meta.statemachine

Returns state-machine projection for subsystem/component selectors.

Current selector scope:

- subsystem (default)
- component

Output:

- default: YAML
- with `--json`: JSON

## meta.tree

Returns subsystem/component/service/operation hierarchy.

Output:

- default: YAML
- with `--json`: JSON

## meta.version

Returns component version/build metadata from configuration/resources/manifest.
