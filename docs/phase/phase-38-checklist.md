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

Status: DONE

- [x] Add the immutable projection type without exposing `ExecutionContext`.
- [x] Project locale, timezone, application display formats, application mode,
  safe subject fields, and public capabilities.
- [x] Keep arbitrary request/session attributes outside the projection.
- [x] Add deterministic Record/JSON projection evidence.

Evidence:

- `WebExecutionProjection` is an immutable typed public model and does not
  accept `ExecutionContext`, session attributes, principal identifiers, or
  provider maps.
- `WebExecutionProjectionPolicy` defaults to standalone mode and an empty
  public capability allowlist.
- `WebExecutionProjectionSpec` verifies the stable Record/JSON shape, typed
  display-name boundary, excluded internal data, canonical policy identifiers,
  and property-based deterministic capability selection.

## SW-03: Locale, Timezone, and Display-format Resolution

Status: DONE

- [x] Resolve standalone configuration before runtime defaults.
- [x] Resolve authenticated-user preferences before application/runtime
  fallbacks in multi-user mode.
- [x] Prevent `Accept-Language` from overriding resolved execution policy.
- [x] Keep explicit display override and HTTP negotiation opt-in and bounded.
- [x] Keep application display formats distinct from diagnostics formatting.

Evidence:

- `WebExecutionResolutionPolicy` decodes canonical runtime configuration and
  compatibility aliases with structured failures for malformed values.
- `WebExecutionResolver` applies mode-aware locale/timezone precedence and maps
  execution display policy to stable public format identifiers.
- `IngressSecurityResolver` no longer applies `Accept-Language` implicitly.
- `WebExecutionResolutionSpec` covers standalone, multi-user, opt-in override,
  bounded HTTP negotiation, strict config decoding, display-format mapping,
  and property-based browser-language precedence.

## SW-04: Static Template Projection

Status: DONE

- [x] Make `pageContext.execution` available during first HTML generation.
- [x] Emit matching `html[lang]` and semantic locale attributes.
- [x] Emit one stable `textus-page-context` JSON script-data block.
- [x] Escape hostile script-data values without application-local JavaScript.

Evidence:

- `WebPageContext` owns a typed framework execution member separately from
  provider-extensible flat values and preserves it across provider merge.
- `StaticFormAppRendererCorePart` derives read-only execution placeholders and
  first-render HTML from the same projection.
- `WebExecutionTemplateProjection` emits canonical semantic attributes and a
  single script-data-safe JSON block.
- `WebExecutionTemplateProjectionSpec` covers first-render consistency,
  reserved-element replacement, provider merge isolation, hostile text, and
  property-based serialization safety.

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
