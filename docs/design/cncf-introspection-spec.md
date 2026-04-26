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

Aggregate/View metadata exposure:

- Component-level introspection (`meta.help`, `meta.describe`, `meta.schema`)
  includes generated aggregate/view collection metadata.
- Component `describe` and `schema` projections expose `entityCollections` with
  effective SimpleEntity storage-shape metadata. The metadata is projection-only
  and includes management/security expansion, compact permission storage, scalar
  columns, and delegated aggregate/view collections where known.
- Component Web manual pages render the same `entityCollections.storageShape`
  metadata as a read-only summary table. Raw Describe/Schema projection data
  remains secondary in JSON/YAML tabs; the manual does not expose permission bit
  internals or add storage-policy mutation controls.
- Component admin entity pages render the same storage-shape metadata for
  operator inspection. Admin rendering is read-only and does not add
  storage-policy mutation controls.
- OpenAPI projection includes CNCF vendor extensions:
  - `x-cncf-aggregate-collections`
  - `x-cncf-view-collections`
- Projection output order must be deterministic:
  - collections sorted by `name`
  - named views sorted and deduplicated

## Output Channels

The same projected structure is rendered for multiple channels:

- CLI human output (YAML-oriented help/tree)
- CLI machine output (`--json`)
- Record-oriented responses for describe/schema/services/operations
- OpenAPI JSON export

## Consistency Rule

All introspection surfaces should derive from canonical model data and remain
compatible with selector-based discovery semantics.
