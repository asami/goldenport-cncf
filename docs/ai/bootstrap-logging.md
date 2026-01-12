# Bootstrap Logging

This document explains why normal logging is unavailable during bootstrap and
how BootstrapLog is used as the temporary diagnostic channel.

Related:
- docs/design/component-factory.md
- docs/ai/ai-human-collaboration-convention.md

## Problem Statement

Component discovery and initialization occur before configuration and logging
are fully initialized. Standard logging backends may not be available at this
stage, which makes early failures difficult to diagnose.

## Why Naive Approaches Fail

- Using normal logging before configuration is loaded drops or misroutes logs.
- Ad-hoc println diagnostics lack context and are hard to correlate.
- Enabling full logging too early can conflict with later configuration.

## Adopted Design

- Use BootstrapLog as a temporary, opt-in diagnostic channel during bootstrap.
- BootstrapLog is allowed for component discovery and initialization paths
  where normal logging is unavailable.
- Diagnostics should include repository targets, class loader identity,
  attempted class names, and instantiation strategy paths, as described in
  docs/design/component-factory.md.
- BootstrapLog must have a clear migration path into config-driven logging.

## Operational Implications

- When debugging bootstrap issues, rely on BootstrapLog output first.
- AI reports must state when observations rely on bootstrap logging only.
- Environment-variable toggles for bootstrap logging are temporary and must be
  documented with their planned config equivalents.
