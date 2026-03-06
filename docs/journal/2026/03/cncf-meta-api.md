Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF Meta API

Date: 2026-03-05  
Location: docs/journal/2026/03/

## Overview

The `meta.*` namespace provides runtime introspection for CNCF
subsystems and components.

These services expose the structural model of the runtime,
allowing discovery of:

- components
- services
- operations
- operation metadata
- interface schemas

The meta API forms the foundation of CNCF discovery mechanisms
used by CLI tools, documentation generators, and AI systems.

---

# Introspection Hierarchy

The meta API follows the runtime hierarchy of CNCF.

```
Subsystem
  Component
    Service
      Operation
```

Each level can be inspected through `meta.*` services.

---

# Core Introspection Entry Point

## meta.help

`meta.help` is the primary introspection entry point.

It returns information about the target object.

Targets may be:

```
subsystem
component
service
operation
```

### Examples

Subsystem:

```
command meta.help
```

Component:

```
command meta.help domain
```

Service:

```
command meta.help domain.entity
```

Operation:

```
command meta.help domain.entity.createPerson
```

### Output Responsibilities

Depending on the target level, `meta.help` returns:

Subsystem

```
Subsystem
Components
```

Component

```
Component
Services
```

Service

```
Service
Operations
```

Operation

```
Operation
Summary
Arguments
Return type
```

---

# Component Discovery

## meta.components

Returns the list of components in the subsystem.

Example:

```
command meta.components
```

Example output:

```
Components:
- domain
- repository
- job
```

---

# Service Discovery

## meta.services

Returns services provided by a component.

Example:

```
command domain.meta.services
```

Example output:

```
Services:
- entity
- repository
- meta
- system
```

---

# Operation Discovery

## meta.operations

Returns operations available in a service.

Example:

```
command domain.entity.meta.operations
```

Example output:

```
Operations:
- createPerson
- updatePerson
- loadPerson
- deletePerson
```

---

# Operation Metadata

Operation metadata is derived from the canonical model.

Core model objects:

```
OperationDefinition
ServiceDefinition
ParameterDefinition
```

These objects define the structure of the operation.

Example metadata fields:

```
name
summary
description
arguments
returnType
```

---


# meta.help Response Structure

`meta.help` returns a structured description of the target object.

The exact structure may evolve as the runtime model evolves, but the response
follows a common envelope that allows tools and CLI interfaces to navigate the
runtime hierarchy.

Common fields:

```
type
name
summary
children
details
```

Field meanings:

- `type` identifies the object category.

```
subsystem
component
service
operation
```

- `name` is the canonical identifier of the object.
- `summary` provides a short description.
- `children` lists the next level objects in the hierarchy.
- `details` contains type-specific metadata.

Example (operation):

```
{
  type: "operation",
  name: "entity.createPerson",
  summary: "Create a Person",
  details: {
    arguments: [
      { name: "name", type: "string", required: true },
      { name: "age", type: "int" }
    ],
    returns: "Person"
  }
}
```

The response structure is designed to support both human-oriented CLI help
and machine-oriented discovery by tools and AI systems.

In most cases `meta.help` is implemented as a projection over the canonical
runtime model.


# meta.describe

`meta.describe` returns a detailed machine-readable description of a
runtime object such as a component, service, or operation.

While `meta.help` focuses on hierarchical navigation, `meta.describe`
provides a full structural description suitable for tools, automation,
and AI systems.

Typical targets include:

```
component
service
operation
```

Example:

```
command domain.entity.meta.describe createPerson
```

Example response:

```
{
  type: "operation",
  name: "entity.createPerson",
  summary: "Create a Person",
  arguments: [
    { name: "name", type: "string", required: true },
    { name: "age", type: "int" }
  ],
  returns: {
    type: "Person"
  }
}
```

`meta.describe` exposes semantic information about the operation
rather than only listing children in the hierarchy.

Typical information returned may include:

- operation summary
- arguments and types
- return type
- possible errors
- additional metadata

In most implementations, `meta.describe` is generated from the canonical
runtime model (such as `OperationDefinition`) through a projection layer.

This makes it suitable as a stable interface for tooling and AI-based
introspection.

# Schema Export

## meta.schema

Exports the operation schema in machine-readable format.

Example:

```
command domain.entity.meta.schema
```

This may return:

- JSON schema
- CNCF schema format

---

# OpenAPI Export

## meta.openapi

Exports the component interface as an OpenAPI specification.

Example:

```
command domain.meta.openapi
```

This enables integration with HTTP tools and API gateways.

---

# Relationship to CLI

The CLI `help` command redirects to `meta.help`.

```
command help
→ command meta.help
```

Examples:

```
command help domain
→ command meta.help domain
```

```
command help domain.entity.createPerson
→ command meta.help domain.entity.createPerson
```

This keeps the CLI minimal while relying on the meta API
for structural discovery.

---

# Relationship to Projection

The meta API exposes the canonical model,
while projections generate representations.

```
OperationDefinition
        ↓
projection
        ↓
CLI help / JSON / OpenAPI / documentation
```

The meta API therefore acts as the gateway
to the runtime model.

## Projection Principle

All `meta.*` APIs are generated through the projection layer.

Rather than directly constructing responses in each service implementation,
`meta.*` responses should be derived from the canonical runtime model
(e.g., `ServiceDefinition`, `OperationDefinition`) using projection
mechanisms.

```
Runtime Model
    ↓
Projection
    ↓
meta.* response
```

This rule ensures that:

- CLI help
- machine-readable metadata
- documentation generation
- API specifications

are all derived from the same canonical model.

Typical projections include:

```
HelpProjection
DescribeProjection
SchemaProjection
OpenApiProjection
```

This design keeps the meta API consistent and prevents duplication of
formatting or metadata logic across different services.

---

# Design Goals

The CNCF meta API is designed to provide:

1. Consistent runtime discovery
2. Machine-readable system structure
3. Support for CLI help
4. Integration with documentation systems
5. Compatibility with AI-driven tooling

---

# Summary

The CNCF meta API provides runtime introspection through
the `meta.*` namespace.

Core entry points include:

```
meta.help
meta.components
meta.services
meta.operations
meta.schema
meta.openapi
```

These APIs expose the structure of CNCF subsystems
and components and enable unified discovery
for users, tools, and AI systems.
