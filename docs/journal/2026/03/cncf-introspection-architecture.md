Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF Introspection Architecture

Date: 2026-03-05  
Location: docs/journal/2026/03/

## Overview

CNCF provides a unified mechanism for discovering runtime structure,
documentation, and operational state of a subsystem.

The architecture separates the following concerns:

- runtime structure
- documentation navigation
- runtime control
- representation generation

This separation enables consistent discovery for CLI users,
tools, and AI agents.

---

# Architectural Layers

CNCF introspection is organized into four logical layers.

```
CLI / Interface Layer
        ↓
Service Layer (meta / doc / system)
        ↓
Projection Layer
        ↓
Model Layer
```

Each layer has a clearly defined responsibility.

---

# Model Layer

The model layer represents the canonical runtime structure of CNCF.

Core structural elements:

```
Subsystem
  Component
    Service
      Operation
```

Primary model definitions include:

- ServiceDefinition
- OperationDefinition
- ParameterDefinition
- RequestDefinition
- ResponseDefinition

These objects define the executable interface of components.

The model layer is independent of presentation formats
such as CLI output or API specifications.

---

# Projection Layer

The projection layer transforms model objects into
external representations.

```
model → projection → representation
```

Typical projections include:

- CLI help text
- JSON schema
- Markdown documentation
- OpenAPI specification
- AI interface schema

Example:

```
OperationDefinition
     ↓
HelpProjection
     ↓
CLI help text
```

Example:

```
OperationDefinition
     ↓
OpenApiProjection
     ↓
OpenAPI JSON
```

Projection ensures that all external representations
are generated from the same canonical model.

---

# Service Layer

The service layer exposes introspection and runtime capabilities.

Three service namespaces are defined.

## meta.*

Provides runtime structure introspection.

Examples:

```
meta.help
meta.components
meta.services
meta.operations
meta.openapi
```

Example usage:

```
command meta.help
command meta.help domain
command meta.help domain.entity
command meta.help domain.entity.createPerson
```

meta.* services expose the runtime model structure.

---

## doc.*

Provides documentation navigation.

Examples:

```
doc.help
doc.overview
doc.guide
doc.reference
```

Example usage:

```
command doc.help
command domain.doc.help
```

doc.* services guide users to conceptual documentation
rather than runtime structure.

---

## system.*

Provides runtime control and health information.

Examples:

```
system.health
system.ping
system.info
```

Example usage:

```
command system.health
```

system.* services expose operational status
and runtime management capabilities.

---

# CLI Layer

The CLI acts as a navigation interface.

It does not contain structural information itself.

Instead, it redirects to the appropriate services.

```
help → meta.help
doc  → doc.help
```

Example:

```
command help
```

redirects internally to

```
command meta.help
```

Example:

```
command doc
```

redirects to

```
command doc.help
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

---

# Introspection Hierarchy

The runtime discovery hierarchy is:

```
Subsystem
  Component
    Service
      Operation
```

Accessible through:

```
meta.help
```

Documentation follows a similar pattern:

```
Subsystem
  doc.*

Component
  doc.*
```

---

# Integration with Tools and AI

Because the runtime interface is defined in the model layer,
and representations are generated through projections,
the same information can be reused by multiple tools.

Examples include:

- CLI help
- API documentation
- OpenAPI generation
- AI interface discovery
- MCP service integration

```
OperationDefinition
        ↓
projection
        ↓
CLI / Docs / API / AI
```

This ensures consistency across all interaction channels.

---

# Summary

CNCF introspection architecture separates the system into:

```
Model
Projection
Services
CLI
```

Responsibilities are clearly divided:

```
meta.*   → runtime introspection
doc.*    → documentation navigation
system.* → runtime control
```

Projection ensures that all representations are derived
from the same canonical model.

This architecture enables consistent discovery,
automation, and integration with developer tools
and AI systems.
