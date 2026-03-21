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
