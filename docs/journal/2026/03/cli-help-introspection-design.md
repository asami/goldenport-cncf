Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF CLI Help and Introspection Design

Date: 2026-03-05  
Location: doc/journal/2026/03/

## Background

The command:

```
command <component>.meta.help
```

is now available and returns the list of services and operations provided by a component.

Example:

```
command domain.meta.help
```

Output:

```
Component: domain

Services:
- entity
  - entity.createPerson
  - entity.createPersonRecord
  - entity.deletePerson
  - entity.loadPerson
  - entity.searchPerson
  - entity.storePerson
  - entity.updatePerson
- meta
  - meta.help
  - meta.version
- repository
  - repository.createPerson
  - repository.deletePerson
  - repository.loadPerson
  - repository.searchPerson
  - repository.storePerson
  - repository.updatePerson
- system
  - system.health
  - system.ping
```

This capability provides the foundation for a unified help / introspection mechanism for the CNCF runtime.

The goal is to define a consistent help model covering:

- CLI help
- subsystem help
- component help
- service help
- operation usage

---

# Design Principle

The key design rule is:

```
help       = CLI navigation
meta.help  = introspection API
```

In other words:

```
help is UI
meta.help is protocol
```

All structured information about subsystem, components, services, and operations should be provided through `meta.help`.

---

# Introspection Hierarchy

The help system follows the structural hierarchy of CNCF:

```
Subsystem
  Component
    Service
      Operation
```

Each level can be inspected through `meta.help`.

```
command meta.help
command meta.help <component>
command meta.help <component>.<service>
command meta.help <component>.<service>.<operation>
```

---

# CLI Help

The CLI `help` command acts only as an entry point and navigation aid.

```
command help
```

Example output:

```
CNCF Command Help

Use meta.help to inspect subsystem and components.

Examples:

  command meta.help
  command meta.help domain
  command meta.help domain.entity
  command meta.help domain.entity.createPerson
```

Argument-based help is redirected to `meta.help`.

Example:

```
command help domain
→ command meta.help domain
```

```
command help domain.entity.createPerson
→ command meta.help domain.entity.createPerson
```

---

# Subsystem Help

Subsystem help lists all components in the subsystem.

```
command meta.help
```

Example output:

```
Subsystem: sample

Components:
- domain
- repository
- job

Use:

  command <component>.meta.help
```

Subsystem also provides its own meta and system services.

```
meta.*
system.*
```

---

# Component Help

```
command meta.help domain
```

Example output:

```
Component: domain

Services:
- entity
- repository
- meta
- system
```

---

# Service Help

```
command meta.help domain.entity
```

Example output:

```
Service: entity

Operations:
- createPerson
- updatePerson
- loadPerson
- deletePerson
```

---

# Operation Help

```
command meta.help domain.entity.createPerson
```

Example output:

```
Operation: entity.createPerson

Summary:
Create a new Person entity.

Arguments:
- name : string (required)
- age  : int (optional)

Returns:
Person
```

This level provides the usage description for the operation.

---

# Subsystem Meta Services

The subsystem provides meta services similar to components.

Examples:

```
meta.help
meta.components
meta.version
```

These allow discovery of subsystem-level structure.

---

# Subsystem System Services

Subsystem-level runtime operations are exposed through `system.*`.

Examples:

```
system.health
system.ping
system.info
```

Example:

```
command system.health
```

Output example:

```
Subsystem: sample

domain       OK
repository   OK
job          OK
```

---

# Design Intent

This design achieves the following:

1. Keeps CLI help simple and minimal.
2. Centralizes introspection through `meta.*`.
3. Maintains symmetry between subsystem and component APIs.
4. Enables machine-readable discovery of components.
5. Supports future integration with AI, MCP, and API generation.

---

# Future Extensions

Additional introspection APIs may be introduced.

Examples:

```
meta.schema
meta.openapi
meta.services
meta.operations
```

These will allow CNCF components to expose machine-readable interfaces and facilitate automated integration with AI agents and tooling.

---

# Summary

The CNCF help system is structured around the following principle:

```
CLI help → navigation
meta.help → structured introspection
```

The introspection hierarchy follows the runtime architecture:

```
Subsystem → Component → Service → Operation
```

This structure provides a consistent and extensible foundation for help, discovery, and future automation.
```
