status = draft
owner = Phase 2.8
scope = design / specification (mutable)

======================================================================
Global Protocol Specification
======================================================================

Purpose
----------------------------------------------------------------------
This document defines the Global Protocol, a unified parameter vocabulary
and resolution model that governs how runtime options, configuration,
and domain-level arguments are interpreted across CNCF execution surfaces.

The Global Protocol is the single source of truth for:
- Parameter names and meanings
- Scope (runtime vs domain)
- Precedence rules (CLI / Config / Default)
- Expansion into RuntimeProtocol and DomainProtocol

This specification exists to prevent parameter leakage between protocol
stages and to ensure consistent behavior across command, server, client,
script, and server-emulation modes.


Design Motivation
----------------------------------------------------------------------
CNCF employs a two-stage protocol model:

1. Runtime Protocol (pre-resolution)
2. Domain Protocol (component/service/operation resolution)

However, without a shared parameter vocabulary, runtime options (e.g.
--baseurl) risk being misinterpreted as domain arguments.

The Global Protocol solves this by defining parameters once and expanding
them deterministically into each protocol stage.


Core Concepts
----------------------------------------------------------------------

Global Protocol
----------------------------------------------------------------------
The Global Protocol defines all recognized parameters and their semantics.

It does NOT parse arguments directly.
Instead, it provides a declarative model consumed by:
- RuntimeProtocol
- ConfigProtocol
- (indirectly) DomainProtocol


Global Parameter
----------------------------------------------------------------------
A Global Parameter is defined by:

- key            : canonical parameter name
- aliases        : optional alternative names
- scope          : Runtime | Domain
- datatype       : String | Int | Boolean | URL | Enum | ...
- default        : optional default value
- precedence     : CLI > Config > Default
- consumer       : responsible subsystem (e.g. logging, http)


Example (conceptual):

    GlobalParameter(
      key        = "baseurl",
      aliases    = Vector("base-url"),
      scope      = Runtime,
      datatype   = URL,
      default    = None,
      consumer   = HttpDriver
    )


Scope Rules
----------------------------------------------------------------------
Runtime-scoped parameters:
- Are consumed exclusively by the Runtime Protocol
- Are materialized into GlobalRuntimeContext
- MUST NOT be forwarded to the Domain Protocol

Domain-scoped parameters:
- Are not interpreted by the Runtime Protocol
- Are forwarded unchanged to Domain Protocol processing


Precedence Rules
----------------------------------------------------------------------
Parameter values are resolved in the following order:

1. CLI arguments
2. Configuration files
3. Default values

Once resolved, the effective value is fixed and immutable during
Domain Protocol execution.


Protocol Expansion Model
----------------------------------------------------------------------

GlobalProtocol
    |
    +--> RuntimeProtocol
    |       - consumes Runtime-scoped parameters
    |       - determines RunMode
    |       - constructs GlobalRuntimeContext
    |
    +--> ConfigProtocol
    |       - loads configuration sources (e.g. .cncf/config.yaml)
    |       - validates against Global Protocol definitions
    |
    +--> DomainProtocol
            - receives only non-consumed arguments
            - performs canonical / alias / suffix resolution


Two-Stage Argument Flow
----------------------------------------------------------------------
Given:

    cncf command --log-level trace --baseurl http://localhost:8080 admin.system.ping param1

Stage 1 (Runtime Protocol):
- Consumes:
    command
    --log-level trace
    --baseurl http://localhost:8080
- Produces:
    GlobalRuntimeContext(
      runMode  = command,
      logLevel = trace,
      baseUrl  = http://localhost:8080
    )

Stage 2 (Domain Protocol):
- Receives:
    admin.system.ping param1
- Performs canonical resolution and execution


Unconsumed Parameter Rule
----------------------------------------------------------------------
If a parameter is NOT defined in the Global Protocol:
- It MUST NOT be consumed by RuntimeProtocol
- It MUST be forwarded to DomainProtocol unchanged

This guarantees forward compatibility for domain-level extensions.


Relation to Configuration
----------------------------------------------------------------------
Configuration files (e.g. .cncf/config.yaml) are interpreted exclusively
through the Global Protocol.

Domain Protocol logic MUST NOT access configuration directly.

Example:

    runtime:
      baseurl: http://localhost:8080
      log-level: trace

These values populate GlobalRuntimeContext before any domain resolution.


Non-goals
----------------------------------------------------------------------
- No ad-hoc argument parsing outside the Global Protocol
- No protocol-specific parameter redefinition
- No domain resolution based on configuration state


Relation to Other Specifications
----------------------------------------------------------------------
- canonical-alias-suffix-resolution.md
    - Assumes Runtime Protocol completion before resolution
- path-alias.md
    - Operates entirely within Domain Protocol
- ProtocolEngine
    - Expected to consume Global Protocol definitions

----------------------------------------------------------------------
Historical Note
----------------------------------------------------------------------

For a concrete incident and design reasoning related to runtime-level
option leakage (e.g. --baseurl) observed during Phase 2.8 / 2.85,
see the following journal entry:

  docs/journal/2026/01/runtime-protocol-leak-and-closure.md

Status
----------------------------------------------------------------------
- Phase: 2.8
- State: ACTIVE DESIGN (mutable)
- Freeze: End of Phase 2.8

======================================================================
End of document
======================================================================
