# Shared Directive Bridge

This document defines how CNCF consumes shared AI operational directives
mounted under `ai/directive/`.

The shared directive repository is authoritative for reusable AI operational
instructions.
CNCF must not redefine those shared directives locally.

Repository-local guidance in `docs/ai/` may only:

- tell agents when the shared directive must be used
- add CNCF-specific constraints around its use
- point to the mounted shared path

It must not fork or overwrite the shared directive content.

## Shared Directive Location

Shared AI directives are mounted in this repository at:

- `ai/directive/`

## Required Use: Scala Version Header Updates

When updating Scala file version headers, agents must use:

- [version-update-instruction.md](/Users/asami/src/dev2025/cloud-native-component-framework/ai/directive/samples/version-update-instruction.md)

## CNCF-Specific Constraints

When applying the shared version update directive in CNCF:

- preserve existing version history lines
- preserve existing version history line order
- do not replace an existing history line with a new `@version` line

## Commit-Preparation Check

Before committing staged Scala changes that include header updates:

1. confirm that the shared version update directive was used
2. confirm that no existing version history line was removed
3. confirm that version history line order was preserved

This bridge is operational guidance only.
Authoritative repository rules remain under `docs/rules/`.
