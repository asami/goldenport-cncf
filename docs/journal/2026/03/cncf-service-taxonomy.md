Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF Service Taxonomy

Date: 2026-03-05  
Location: docs/journal/2026/03/

## Overview

CNCF defines a small set of service namespaces that provide
a consistent interface for runtime discovery, documentation navigation,
and system operations.

These namespaces form the core vocabulary of CNCF service APIs.

The three primary namespaces are:

```
meta.*
doc.*
system.*
```

Each namespace has a distinct responsibility.

```
meta.*   → runtime introspection
doc.*    → documentation navigation
system.* → runtime control
```

This taxonomy ensures that CNCF components expose
a predictable and discoverable interface.

---

# Service Namespace Structure

Service namespaces appear at two levels of the runtime hierarchy.

```
Subsystem
  meta.*
  doc.*
  system.*

Component
  meta.*
  doc.*
  system.*
```

This symmetry allows the same discovery and navigation mechanisms
to work across the entire system.

---

# meta.* — Introspection Services

The `meta.*` namespace provides runtime structure discovery.

These services expose the internal structure of the subsystem
and components.

Typical capabilities include:

- listing components
- listing services
- listing operations
- retrieving operation metadata
- exporting interface schemas

Examples:

```
meta.help
meta.components
meta.services
meta.operations
meta.openapi
meta.schema
```

Example usage:

```
command meta.help
command meta.help domain
command meta.help domain.entity
command meta.help domain.entity.createPerson
```

`meta.*` services operate on the runtime model
defined by ServiceDefinition and OperationDefinition.

They provide machine-readable introspection
for tools and automation.

---

# doc.* — Documentation Services

The `doc.*` namespace provides navigation for human-oriented documentation.

Unlike `meta.*`, which exposes runtime structure,
`doc.*` focuses on conceptual understanding and guidance.

Typical documentation topics include:

- system overview
- architecture explanation
- user guides
- developer references
- tutorials and examples

Examples:

```
doc.help
doc.overview
doc.guide
doc.reference
doc.examples
```

Example usage:

```
command doc.help
command domain.doc.help
```

`doc.*` services serve as entry points
to conceptual documentation resources.

---

# system.* — Runtime Services

The `system.*` namespace exposes operational capabilities
and runtime information.

These services allow inspection and management
of subsystem state.

Typical capabilities include:

- health checks
- runtime information
- diagnostics
- system status

Examples:

```
system.health
system.ping
system.info
system.status
```

Example usage:

```
command system.health
```

`system.*` services interact with the live runtime environment.

---

# Relationship to CLI Commands

The CNCF CLI provides simple entry points
that redirect to the service namespaces.

```
help → meta.help
doc  → doc.help
```

Example:

```
command help
→ command meta.help
```

```
command doc
→ command doc.help
```

Arguments follow the same rule.

```
command help domain
→ command meta.help domain
```

```
command doc domain
→ command domain.doc.help
```

This keeps the CLI lightweight while preserving
a consistent service architecture.

---

# Relationship to the Introspection Architecture

The service taxonomy corresponds to the CNCF introspection architecture.

```
meta.*   → model discovery
doc.*    → knowledge navigation
system.* → runtime control
```

These services expose different aspects of the system:

```
Model Layer
  exposed via meta.*

Knowledge Layer
  exposed via doc.*

Runtime Layer
  exposed via system.*
```

Together they provide a unified discovery interface.

---

# Design Goals

The CNCF service taxonomy aims to achieve the following goals:

1. Predictable service naming across components.
2. Clear separation of runtime structure, documentation, and operations.
3. Consistent introspection for CLI tools and automation.
4. Compatibility with AI-driven tooling and API discovery.
5. Minimal and extensible namespace design.

---

# Summary

CNCF defines three core service namespaces:

```
meta.*
doc.*
system.*
```

Their responsibilities are clearly separated:

```
meta.*   → runtime introspection
doc.*    → documentation navigation
system.* → runtime operations
```

These namespaces form the foundation of CNCF service APIs
and enable consistent discovery across the entire system.
