Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# CNCF CLI Grammar

## Overview

The CNCF CLI provides a uniform interface for invoking operations exposed by subsystems and components.

The CLI command structure follows the runtime hierarchy of CNCF and allows direct invocation of component operations as well as meta introspection APIs.

The grammar is designed to satisfy the following goals:

- simple command invocation
- deterministic selector resolution
- compatibility with CLI tooling
- introspection support
- machine-readable structure for AI tools

---

# Core Command Structure

The canonical CLI command form is:

command <selector> [arguments]

Example:

command domain.entity.createPerson

The selector identifies the operation to execute.

---

# Selector Grammar

The selector follows a hierarchical structure.

selector
  := subsystem?
     component
     service
     operation

Example:

domain.entity.createPerson

Structure:

component = domain  
service   = entity  
operation = createPerson

---

# Meta Namespace

Meta operations are exposed under the meta namespace.

component.meta.operation  
service.meta.operation

Examples:

domain.meta.help  
domain.entity.meta.operations  
domain.entity.meta.describe

Meta operations provide introspection of the runtime model.

---

# Subsystem Scope

Some commands operate at the subsystem level.

Examples:

meta.help  
meta.components

These commands inspect the entire subsystem.

---

# Command Grammar (EBNF)

command
  := "command" selector arguments?

selector
  := subsystemSelector
   | componentSelector
   | serviceSelector
   | operationSelector

subsystemSelector
  := "meta" "." subsystemMetaOperation

componentSelector
  := component "." "meta" "." componentMetaOperation
   | component "." service "." operation

serviceSelector
  := component "." service "." "meta" "." serviceMetaOperation

operationSelector
  := component "." service "." operation

arguments
  := argument*

---

# Reserved Namespaces

The following namespaces are reserved:

meta  
system  
doc

These namespaces provide standard services.

Example:

component.meta.*  
component.system.*  
component.doc.*

---

# Built-in CLI Redirections

The CLI provides several convenience shortcuts.

## help command

command help

is equivalent to

command meta.help

Examples:

command help domain  
→ command meta.help domain

command help domain.entity.createPerson  
→ command meta.help domain.entity.createPerson

---

# Selector Resolution

Selector resolution follows these rules:

1. Exact operation match

component.service.operation

2. Meta namespace

component.meta.operation  
component.service.meta.operation

3. Subsystem meta operations

meta.operation

---

# CLI Argument Handling

Arguments following the selector are passed to the operation.

Example:

command domain.entity.createPerson name=alice age=20

Special CLI flags used by the runtime (such as --no-exit) must not be interpreted as operation arguments.

They must be filtered before selector resolution.

---

# Introspection Integration

The CLI relies on the meta API for discovery.

Examples:

command meta.help  
command domain.meta.services  
command domain.entity.meta.operations

This allows the CLI to dynamically discover operations without hard-coded command definitions.

---

# Example Command Flow

Example:

command domain.entity.createPerson

Resolution:

selector  
  → component: domain  
  → service: entity  
  → operation: createPerson

Execution:

OperationDefinition  
  → operation executor

---

# Example Introspection Flow

Example:

command domain.entity.meta.operations

Resolution:

component = domain  
service   = entity  
meta operation = operations

Execution:

ServiceDefinition  
    ↓  
projection  
    ↓  
operation list

---

# Design Principle

The CNCF CLI is designed around the principle:

operation invocation  
and  
runtime introspection  
share the same namespace

This allows:

- CLI tooling
- documentation systems
- AI assistants

to operate on the same introspection interface.

---

# Summary

The CNCF CLI grammar defines a deterministic structure for invoking operations and performing runtime introspection.

Core structure:

command component.service.operation

Meta namespace:

component.meta.*  
component.service.meta.*  
meta.*

This structure provides a consistent interface for human users, tools, and AI systems interacting with CNCF subsystems.
