# AI Operation Guidance

This document defines CNCF-local operational guidance for AI-assisted work
when shared directives must be applied from the mounted shared directive layer.

This file is the canonical bridge for such references inside CNCF.
Do not duplicate the same operational bridge in multiple documents.

## Shared Directive Reference

When updating Scala file version headers, agents must use the shared
operational directive:

- [version-update-instruction.md](/Users/asami/src/dev2025/cloud-native-component-framework/ai/directive/samples/version-update-instruction.md)

This shared directive is the authoritative operational reference for:

- how to update Scala `@version` headers
- how to preserve existing version history lines
- how to avoid destructive header rewrites

## Required Rule for Scala Header Updates

When a Scala file header is updated:

- use the shared `version-update-instruction.md`
- preserve existing history lines
- preserve existing history line order

## Prohibited Header Rewrite Patterns

The following are prohibited:

- replacing an existing `*  version ...` history line with a new `@version` line
- deleting an existing version history line
- reordering version history lines

## Commit-Preparation Check

Before committing staged Scala changes that include header updates:

1. confirm that the shared version-update directive was used
2. confirm that no existing version history line was removed
3. confirm that version history line order was preserved

This is an operational check only.
It does not replace the repository rules under `docs/rules/`.
