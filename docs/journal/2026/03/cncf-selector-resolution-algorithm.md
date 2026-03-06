Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF Selector Resolution Algorithm

## Overview

The CNCF CLI resolves a selector string into a concrete runtime operation.

A selector identifies a runtime object within the CNCF hierarchy.

Runtime hierarchy:

Subsystem  
  Component  
    Service  
      Operation  

The selector resolution algorithm converts a textual selector into the corresponding runtime object.

---

# Input

CLI command format:

command <selector> [arguments]

Example:

command domain.entity.createPerson

Selector:

domain.entity.createPerson

---

# Preprocessing

Before selector resolution, the CLI performs preprocessing.

## Step 1 — Remove runtime flags

CLI runtime flags must be filtered out before selector parsing.

Examples:

--no-exit  
--json  
--debug  

Example:

command domain.meta.help --no-exit

After preprocessing:

selector = domain.meta.help

---

## Step 2 — Tokenization

The selector is split by `.`.

Example:

domain.entity.createPerson

Tokens:

domain  
entity  
createPerson

---

# Resolution Strategy

Selectors are resolved using the following precedence order.

1. Subsystem meta operation  
2. Component meta operation  
3. Service meta operation  
4. Operation invocation

---

# Resolution Algorithm

Pseudo algorithm:

```
resolve(selector):

tokens = split(selector, ".")

if tokens[0] == "meta":
    return resolveSubsystemMeta(tokens)

component = tokens[0]

if component not found:
    error "component not found"

if tokens length == 1:
    return component default operation

if tokens[1] == "meta":
    return resolveComponentMeta(component, tokens[2])

service = tokens[1]

if service not found:
    error "service not found"

if tokens length == 2:
    return service default operation

if tokens[2] == "meta":
    return resolveServiceMeta(component, service, tokens[3])

operation = tokens[2]

return resolveOperation(component, service, operation)
```

---

# Subsystem Meta Resolution

Selectors beginning with `meta` refer to subsystem introspection.

Examples:

meta.help  
meta.components  

Resolution:

Subsystem.meta.operation

Example:

meta.help  
→ Subsystem.meta.help

---

# Component Meta Resolution

Example:

domain.meta.help

Resolution:

Component = domain  
Meta operation = help

---

# Service Meta Resolution

Example:

domain.entity.meta.operations

Resolution:

Component = domain  
Service   = entity  
Meta operation = operations

---

# Operation Invocation

Example:

domain.entity.createPerson

Resolution:

Component = domain  
Service   = entity  
Operation = createPerson

Execution:

OperationDefinition  
  → executor

---

# Default Operation Resolution

When a selector resolves only to a component or service,
a default operation may be invoked.

Examples:

command domain

Possible resolution:

domain.main

Example:

command domain.entity

Possible resolution:

domain.entity.main

This behavior is optional and implementation dependent.

---

# Help Redirection

The CLI provides a built-in alias:

help → meta.help

Example:

command help  
→ command meta.help

Example:

command help domain.entity.createPerson  
→ command meta.help domain.entity.createPerson

This ensures a simple user-facing help interface.

---

# Ambiguity Handling

Selectors must resolve deterministically.

If multiple matches exist, the CLI returns an ambiguity error.

Example:

help

Possible matches:

admin.meta.help  
client.meta.help  
debug.meta.help  
domain.meta.help

Resolution:

User must specify:

command meta.help  
or

command domain.meta.help

---

# Error Handling

Possible resolution errors include:

Component not found

```
component not found: domainX
```

Service not found

```
service not found: entityX
```

Operation not found

```
operation not found: createPersonX
```

Ambiguous selector

```
ambiguous selector: help
```

Errors should include diagnostic information to assist debugging.

---

# Integration With Introspection

Selectors and introspection share the same model.

Example:

command domain.entity.meta.operations

Resolution:

ServiceDefinition  
    ↓
projection  
    ↓
operation list

This allows tools and AI systems to inspect the system using the same selector grammar.

---

# Summary

Selector resolution follows a deterministic hierarchy.

Resolution order:

1. Subsystem meta  
2. Component meta  
3. Service meta  
4. Operation

Selector example:

domain.entity.createPerson

Resolution result:

Component = domain  
Service   = entity  
Operation = createPerson

This algorithm provides a consistent method for mapping CLI selectors to runtime operations.
