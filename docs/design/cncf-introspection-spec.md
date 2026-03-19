# CNCF Introspection Specification

Status: draft

This document defines the introspection model and projection architecture used by CNCF.

## Runtime Hierarchy

Subsystem
  Component
    Service
      Operation

## Canonical Definitions

Primary structural definitions:

ServiceDefinition
OperationDefinition
ParameterDefinition

Supporting request/response definitions are used via operation specifications.

## Projection Architecture

Runtime Model
  -> Projection
  -> Output

Projection classes currently used:

HelpProjection
DescribeProjection
SchemaProjection
OpenApiProjection
McpProjection
TreeProjection

Projection models currently used:

HelpModel
TreeModel

## Output Channels

The same projected structure is rendered for multiple channels:

- CLI human output (YAML-oriented help/tree)
- CLI machine output (`--json`)
- Record-oriented responses for describe/schema/services/operations
- OpenAPI JSON export

## Consistency Rule

All introspection surfaces should derive from canonical model data and remain
compatible with selector-based discovery semantics.
