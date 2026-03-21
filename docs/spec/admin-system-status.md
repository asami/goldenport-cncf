Admin System Status Specification
================================

status=draft
scope=operation

# Purpose

This specification defines the `admin.system.status` operation,
intended for runtime inspection, diagnostics, and operational visibility.

# Canonical Output Format

Canonical output format: json
(as defined in docs/spec/output-format.md)

# Default Presentation

When no suffix is specified, a human-readable text summary MAY be returned.
This presentation is not a stable contract.

# Recommended Suffixes

- .json (canonical)
- .yaml (optional)

# Non-Goals

- Defining specific status fields
- Defining transport or projection mechanisms
- Implementation details or serialization libraries

# Minimum Status Schema

The operation MUST include the following fields:

- `status`
- `timestamp`
- `uptime`

When job metrics are available, it SHOULD also include:

- `jobsRunning`
- `jobsQueued`
- `jobsCompleted`
- `jobsFailed`
