Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# Design Review: CNCF CLI / Introspection Consistency

Date: 2026-03-06
Scope: implementation + documentation consistency review (analysis only)

## 1. Summary

- Build status is green:
  - `sbt compile`: success
  - `sbt test`: success
- Core CLI help/introspection behaviors requested in recent work are implemented and covered by tests.
- Major documentation gap: the five authoritative design files named in this review request are not present in `docs/design/`:
  - `cncf-cli-spec.md`
  - `cncf-introspection-spec.md`
  - `cncf-meta-api-spec.md`
  - `cncf-selector-resolution-spec.md`
  - `cncf-architecture-overview.md`
- Practical review baseline was therefore taken from current implementation and existing March journal docs (`docs/journal/2026/03/*`).

## 2. CLI spec vs implementation

### Implemented and consistent

- Top-level command layer exists and routes:
  - `cncf command`
  - `cncf server`
  - `cncf client`
- Help commands are implemented:
  - `cncf help`
  - `cncf command help`
  - `cncf server help`
  - `cncf client help`
- Help aliases are implemented:
  - global: `cncf --help`, `cncf -h`
  - command-level: `<command> --help`, `<command> -h`
- Selector help alias is implemented:
  - `cncf command <selector> --help|-h`
  - rewritten to command-help path (`help <selector>` semantics) before selector resolution.

### Gaps / risks

- `CncfRuntime.scala` has duplicated logic in object/class paths (help normalization, selector parsing, usage printing). Current behavior is aligned but drift risk is high.
- CLI text examples are not internally uniform:
  - top help currently includes `cncf client domain.entity.createPerson`
  - other docs/notes often use `cncf client call ...` or `cncf client http get`.

## 3. Selector resolution spec vs implementation

### Implemented and consistent

- Grammar forms are supported:
  - `<component>.<service>.<operation>`
  - `<component>.meta.<operation>`
  - `<component>.<service>.meta.<operation>`
  - `meta.<operation>`
- Runtime flags (`--json`, `--debug`, `--no-exit`) are filtered before selector resolution.
- Deterministic precedence is implemented in normalization logic:
  - subsystem meta
  - component meta
  - service meta transform
  - operation invocation

### Gaps / risks

- Subsystem meta is implemented via "default component" fallback (`meta.<op>` -> `<first-component>.meta.<op>`). This is practical but semantically indirect vs docs that describe an explicit subsystem meta surface.
- Error reporting shape differs by path:
  - one parse path returns taxonomy/facet failures
  - another returns plain string errors (`"component not found: ..."` etc.).
- Because selector parsing/normalization exists in duplicated locations, any future algorithm change must be applied in both places.

## 4. Meta API spec vs implementation

### Implemented and consistent

- Meta APIs implemented on default component meta service:
  - `meta.help`
  - `meta.describe`
  - `meta.components`
  - `meta.services`
  - `meta.operations`
  - `meta.schema`
  - `meta.openapi`
  - `meta.tree`
- Projection usage is in place (Projection Principle mostly respected):
  - `HelpProjection`
  - `DescribeProjection`
  - `SchemaProjection`
  - `OpenApiProjection`
  - `TreeProjection`
- `meta.help` and `meta.tree` rendering mode:
  - default YAML
  - JSON with `--json`

### Gaps / risks

- `meta.components/services/operations` responses are projection-derived records, not always the simple list style shown in older docs.
- Service-level `meta.describe` argument composition appears inconsistent with current service-meta forwarding format:
  - forwarding now passes `component.service`
  - `_meta_describe_selector` composes as if first arg were only `service`
  - this can produce malformed selectors for some inputs.
- `meta.openapi` output is intentionally minimal JSON; if docs expect fuller OpenAPI details, this is a mismatch.

## 5. Introspection architecture consistency

### Consistent

- Model/projection separation is clearly present in code.
- Runtime introspection is generated from canonical operation/service definitions accessed via protocol model.
- CLI layer remains a dispatcher/normalizer and does not hardcode structural inventory data.

### Mismatch

- Several March docs describe `doc.*` namespace and CLI `doc` redirection (`doc -> doc.help`). No `doc.*` implementation exists in current runtime/CLI.

## 6. Documentation conflicts

1. Missing target design specs
- The five requested authoritative files under `docs/design/` are absent.

2. Journal docs used as pseudo-specs but include stale/aspirational content
- `docs/journal/2026/03/cncf-introspection-architecture.md` and `cncf-service-taxonomy.md` describe `doc.*` that is not implemented.

3. Terminology and examples are inconsistent
- `protocol` vs `command` terminology has mostly been corrected in runtime output, but older docs still mix terms.
- Client examples vary (`client call`, `client http get`, `client <selector>`).

4. Duplicate specification sources
- Similar normative-style content is split across multiple journal docs:
  - `cncf-cli-grammer.md` (typo in filename)
  - `cncf-selector-resolution-algorithm.md`
  - `cncf-meta-api.md`
  - `cncf-introspection-architecture.md`
  - `cncf-service-taxonomy.md`
- No single canonical design spec currently in `docs/design/` for these topics.

## 7. Recommended fixes

1. Create canonical design files under `docs/design/` for the five requested specs and mark March journal docs as draft/superseded.
2. Consolidate selector normalization and help-alias normalization into a single shared implementation to eliminate object/class drift in `CncfRuntime`.
3. Resolve `meta.describe` selector composition for service-level meta calls with current forwarding format.
4. Decide and document `doc.*` status explicitly:
   - either implement it,
   - or remove/defer it from architecture/taxonomy docs.
5. Standardize CLI examples (especially `client` command forms) across help text and docs.
6. Normalize error contract for selector resolution (taxonomy-based vs plain-string) and document one canonical behavior.
7. Rename `docs/journal/2026/03/cncf-cli-grammer.md` to `.../cncf-cli-grammar.md` and add redirects/notes to reduce duplicate/conflicting references.
