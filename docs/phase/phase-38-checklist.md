# Phase 38 - Static Web Execution-Context Projection Checklist

This checklist is the Phase 38 state ledger. The summary dashboard is
`phase-38.md`.

## SW-01: Contract Audit and Scope Freeze

Status: DONE

- [x] Inventory the current Static Web page context, formatting-context
  restoration, authentication projection, and template injection paths.
- [x] Define the typed Web-safe execution projection in design/spec documents.
- [x] Freeze mode-aware locale/timezone precedence and optional HTTP language
  negotiation policy.
- [x] Confirm the safe public capability and subject projection vocabulary.

Evidence:

- `docs/design/static-web-execution-context-projection.md` records the current
  runtime audit, ownership model, resolution policy, and implementation order.
- `docs/spec/static-web-execution-context-projection.md` fixes the public shape,
  default-deny capability projection, typed public subject source, excluded
  data, HTML escaping, and required executable examples.

## SW-02: Web-safe Execution Projection

Status: OPEN

- [ ] Add the immutable projection type without exposing `ExecutionContext`.
- [ ] Project locale, timezone, application display formats, application mode,
  safe subject fields, and public capabilities.
- [ ] Keep arbitrary request/session attributes outside the projection.
- [ ] Add deterministic Record/JSON projection evidence.

## SW-03: Locale, Timezone, and Display-format Resolution

Status: OPEN

- [ ] Resolve standalone configuration before runtime defaults.
- [ ] Resolve authenticated-user preferences before application/runtime
  fallbacks in multi-user mode.
- [ ] Prevent `Accept-Language` from overriding resolved execution policy.
- [ ] Keep explicit display override and HTTP negotiation opt-in and bounded.
- [ ] Keep application display formats distinct from diagnostics formatting.

## SW-04: Static Template Projection

Status: OPEN

- [ ] Make `pageContext.execution` available during first HTML generation.
- [ ] Emit matching `html[lang]` and semantic locale attributes.
- [ ] Emit one stable `textus-page-context` JSON script-data block.
- [ ] Escape hostile script-data values without application-local JavaScript.

## SW-05: Static Web Runtime Integration

Status: OPEN

- [ ] Integrate the projection after security/user execution context is
  resolved and before static HTML is returned.
- [ ] Preserve Static Web route, authorization, theme, and asset behavior.
- [ ] Keep application business state in application operations such as
  `DescribeApplication`.

## SW-06: Executable Security and Policy Evidence

Status: OPEN

- [ ] Cover configured standalone locale versus conflicting
  `Accept-Language`.
- [ ] Cover authenticated-user locale versus conflicting `Accept-Language`.
- [ ] Cover deterministic runtime-default fallback.
- [ ] Cover hostile display-name and script-data values.
- [ ] Prove session/token/internal-id/configuration/datastore/debug data is
  absent.
- [ ] Prove projection content exists before application JavaScript executes.

## SW-07: Developer and ArtScene Integration Guidance

Status: OPEN

- [ ] Update Static Web design/spec and component developer documentation.
- [ ] Document the client-side consumption contract and safe selector/block
  identifiers.
- [ ] Validate ArtScene Japanese standalone first-render behavior without an
  initial locale REST request.
- [ ] Record removal of browser-locale, local-storage, and delayed-render
  workarounds as downstream application work.

## SW-08: Verification and Closure

Status: OPEN

- [ ] Run focused Static Web, ingress security, formatting, escaping, and
  ArtScene integration specifications.
- [ ] Run `sbt --batch Test/compile` and the full CNCF test suite.
- [ ] Run scoped review and resolve actionable findings.
- [ ] Update strategy/phase closure evidence and close Phase 38.
