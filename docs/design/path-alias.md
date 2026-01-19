# Path Alias Behavior
status = draft
scope = design / specification (Phase 2.8)

## Purpose and Positioning

Path alias handling is the pre-normalization convenience layer that translates user-friendly selectors into canonical `component.service.operation` triples before the resolver core runs. This document captures the Phase 2.8 implementation and wiring for alias configuration, validation, and runtime gating while leaving the canonical-path rules unchanged in `docs/spec/path-resolution.md`.

## Relationship to Canonical Path Resolution

Alias logic operates between normalization (case, delimiters) and the resolver core. Consult `docs/spec/path-resolution.md` for the immutable ordering of normalization → omission → suffix resolution → canonical construction; aliases do not change those rules, they only rewrite selectors in a predictable way before the resolver stage described there.

## Configuration Model (`cncf.path.aliases`)

Aliases are declared under the `cncf.path.aliases` configuration key and are loaded by `org.goldenport.cncf.path.AliasLoader`. Each alias entry is an object with required `input`/`output` strings, optional `modes` (a list of `RunMode` names, defaulting to every mode), and optional `purpose` metadata.

The loader normalizes inputs case-insensitively, ensures outputs look like canonical identifiers (dot-separated), and records every alias in `org.goldenport.cncf.path.AliasResolver`. Invalid configurations—such as missing fields, duplicate inputs, identifier characters outside `[A-Za-z0-9_]`, references to unknown aliases, or cycles—raise `IllegalArgumentException` during startup, preventing the runtime from booting with bad alias data.

## RunMode Gating

Each alias lists the RunModes it supports. If no mode list is supplied, the alias is active in all modes (`RunMode.values`). When the runtime builds a `GlobalRuntimeContext`, the configuration loader builds a resolver and stills it in the global context. CLI, script, and HTTP flows consult the resolver with the currently executing `RunMode`, so aliases can be published in `cncf.path.aliases` and scoped to `Command`, `Script`, `Server`, or other modes as needed.

## Validation and Failure Semantics

The alias loader enforces:

- identifier pattern restrictions (`[A-Za-z0-9_]+`),
- unique normalized input names per table,
- non-empty `modes` lists if provided,
- alias graphs without cycles (both direct and indirect),
- existing outputs when they refer to other alias inputs.

Failure at any validation step halts subsystem creation with a clear `IllegalArgumentException` message, so misconfigured aliases never reach runtime.

## Resolution Behavior and Non-goals

`PathPreNormalizer.rewriteSelector` and `rewriteSegments` call `AliasResolver.resolve` before the resolver core sees the selector; this keeps alias handling centralized and consistent across CLI, script, and HTTP surfaces. Alias matching is case-insensitive and treats inputs as trimmed tokens (`_alias_normalization._normalize_input`), so `"Ping"`, `"PING"`, and `" ping "` all map to the same alias.

Alias resolution respects the canonical matching rules documented in `docs/design/canonical-alias-suffix-resolution.md`: the resolver builds a static table from every component/service/operation, expands aliases uniformly at each level, and enforces ambiguity reporting (list candidate FQNs) plus explicit error stages (component/service/operation). Prefix matches are optional and only considered when explicitly enabled in the resolver helper.

This document does not redefine canonical path construction, suffix handling (see `docs/journal/2026/01/phase-2.8-suffix-egress-design.md`), or the resolver table shape; it simply describes how alias configuration hooks into the runtime without violating `docs/spec/path-resolution.md`.

## References

- `docs/spec/path-resolution.md` (canonical path rules remaining authoritative)
- `docs/design/canonical-alias-suffix-resolution.md` (Phase 2.8 design intent, historical overview)
- `docs/journal/2026/01/canonical-alias-suffix-resolution-audit-and-proposal.md` (audit findings and resolver proposal supporting this behavior)
- `src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala` (executable validation of CLI/script alias wiring)
- `docs/journal/2026/01/phase-2.8-suffix-egress-design.md` (ensures suffix semantics remain separate)
