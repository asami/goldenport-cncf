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

6. When both suffix and explicit format property exist,
   explicit format property takes precedence.

7. Deterministic precedence for format selection is:
   - explicit request property (for example `--format`)
   - selector/path suffix (for example `.json`, `.yaml`, `.text`)
   - mode default

8. **Explicit client output format (R8).** The normal CLI HTTP client MUST
   forward an explicitly requested format option, `--json`, or ordinary
   operation selector suffix as exactly one canonical `textus.format` query
   property. Canonical and legacy format
   aliases MUST use that same canonical wire key. An explicit format option
   takes precedence over `--json`, which takes precedence over the selector
   suffix (R7). Slash, dotted and split operation selectors retain their
   requested suffix before alias normalization; an alias-expanded suffix is
   used only when the input selector has none.
   Without an explicit format request,
   the client MUST leave the server's format selection unchanged rather than
   forwarding its generated runtime default. Existing debug forwarding MUST
   remain available; client-only no-exit and execution-mode controls MUST NOT
   be forwarded. The selected operation method, path and named input MUST
   remain unchanged, and the received HTTP response MUST be printed verbatim.


# Client Format Examples

The executable counterpart is
[ClientOutputFormatSpec](../../src/test/scala/org/goldenport/cncf/cli/ClientOutputFormatSpec.scala).

## E2 Operation format precedence and aliases (R7, R8)

Given the normal Admin/system/ping FQN, `--json` selects JSON,
`--json --format yaml` selects YAML, and both `--textus.format json` and
`--cncf.format json` send only canonical `textus.format=json`. The resolved
GET route and received response remain unchanged.

## E3 Unspecified client format (R8)

Given that FQN without a format option, together with `--no-exit`,
`textus.command.execution-mode=direct` and `--debug.trace-job`, the client
forwards debug trace-job while omitting canonical and legacy format keys,
no-exit and execution-mode controls. The server-default text is preserved
with successful exit.

## E4 Operation selector suffixes and precedence (R7, R8)

Given normal operation selectors, a slash `Admin/system/ping.json` selects
JSON, dotted `Admin.system.ping.yaml` selects YAML, and split `Admin system
ping.text` selects text. `--format yaml` overrides `.json`, and `--json`
overrides `.yaml`. Each request preserves the resolved GET route, sends one
canonical format property with an empty body, and prints the receiver's
selected response verbatim with successful exit.


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
