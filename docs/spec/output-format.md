Output Format Specification
===========================

status=draft
scope=system-wide

# Purpose

This specification defines the **canonical output format contract** for CNCF
operations and commands.

It establishes:
- what constitutes the semantic contract of an output,
- how human-readable default output is treated,
- how suffixes are used to explicitly select output formats,
- and how clients and automation should consume outputs.

This document is a **specification**, not a process rule or implementation guide.


# Definitions

## Canonical Output Format

The **canonical output format** is the authoritative, semantic representation
of an operation’s result.

- It defines the meaning and structure of the output.
- It is the only format that clients, automation, and tests may rely on.
- It is independent of presentation or transport.

Each operation MUST define exactly one canonical output format.


## Default Presentation

When no suffix is specified, an operation MAY return a
**human-readable presentation**.

- This presentation is for interactive use only.
- It is not a stable contract.
- Its structure, layout, or ordering MUST NOT be relied upon by automation.

Default presentation exists to optimize developer and operator experience.


## Suffix

A **suffix** is an explicit selector appended to an operation name or path
to request a specific output format.

Examples:

- `ping.json`
- `status.yaml`
- `/admin/system/status.json`

Suffixes bypass default presentation and request a defined output format.


# General Rules

1. Every output-producing operation MUST define a canonical output format.

2. The canonical output format represents the semantic contract of the operation.

3. When no suffix is specified:
   - A human-readable text presentation MAY be returned.
   - This presentation MUST NOT be treated as the canonical contract.

4. One or more suffixes MAY be defined to expose the canonical format or
   alternative structured representations.

5. Clients, automation, and integrations SHOULD use suffix-specified outputs
   and MUST NOT depend on default text presentation.


# Recommended Suffixes

Based on the canonical format, an operation MAY define
**recommended suffixes**.

Recommended suffixes indicate the preferred way to consume the canonical
representation.

Typical mappings:

| Canonical format | Recommended suffix |
|------------------|--------------------|
| text             | (none)             |
| json             | .json              |
| yaml             | .yaml              |
| csv              | .csv               |
| binary           | .bin               |

Operations MAY define additional suffixes, but SHOULD keep the set minimal.


# Client and Automation Guidance

- Interactive CLI usage SHOULD rely on default presentation.
- Programmatic usage (clients, scripts, CI, agents) SHOULD rely on
  canonical formats accessed via recommended suffixes.
- Tests and specifications SHOULD validate canonical output semantics,
  not presentation details.


# Relationship to Other Specifications

- Operation-specific field definitions belong to the operation’s own spec.
- Projection mechanics (CLI, HTTP, OpenAPI, MCP) are defined in design documents.
- Process rules, stages, and checklists are explicitly out of scope.


# Non-Goals

This specification does NOT define:
- operation-specific output fields,
- serialization libraries or encoders,
- transport-level negotiation mechanisms,
- process or stage management rules.
