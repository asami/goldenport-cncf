# Static Web Execution-Context Projection Design

## Purpose

This document defines the CNCF design boundary for projecting effective
request execution state into the first HTML response of a Static Web App.

The projection lets a page select locale, timezone, application display
formatting, public subject state, and public capability-based presentation
without browser-owned state or an application-specific startup operation.

The normative behavior is specified by
`docs/spec/static-web-execution-context-projection.md`.

## Architectural Position

The projection is built after CNCF has resolved request security and the
effective `ExecutionContext`, and before Static Web template rendering.

```text
HTTP request
  -> ingress security and formatting resolution
  -> effective ExecutionContext
  -> framework-owned WebExecutionProjection
  -> Static Web page context
  -> first HTML response
```

It is a projection of selected execution facts. It is not serialization of
`ExecutionContext`, `RuntimeContext`, `SecurityContext`, an authentication
session, or arbitrary request attributes.

## Current Runtime Audit

The Phase 38 SW-01 audit found these existing boundaries:

- `IngressSecurityResolver._restore_formatting_context` resolves principal,
  ingress, and `Accept-Language` locale values into the request execution
  context. Its current unconditional browser-language fallback must become an
  explicit Web policy in SW-03.
- `RuntimeContext.FormattingContext` already owns resolved locale, timezone,
  application-facing date/time formatting, and separate ISO diagnostic
  formatting.
- `Http4sHttpServer._page_view_context` constructs flat string values for
  template placeholders and merges component `WebPageContextProvider` output.
- `WebPageContextProviderRuntime` executes with the effective request
  `ExecutionContext`, but provider values are extensible application data and
  are not an authority for framework execution metadata.
- the existing flat page context includes session/navigation helper values and
  a comma-separated capability value. Those keys are not the new typed
  projection and must not be copied wholesale into it.
- Static Web templates are rendered synchronously by
  `StaticFormAppRendererCorePart.renderStaticTemplate` before the response is
  returned.

## Ownership Model

`WebExecutionProjection` is framework-owned. A component
`WebPageContextProvider` cannot create, replace, or merge its fields.

The existing extensible `WebPageContext.values` remains application/template
input. The execution projection is a separate typed member of the page context
and has a dedicated rendering path. This prevents a query parameter, provider
result, or arbitrary session attribute from overriding locale, subject, or
capability metadata.

The conceptual model is:

```text
WebExecutionProjection
  locale: BCP 47 language tag
  timezone: IANA/ZoneId identifier
  format:
    date: stable public policy identifier
    dateTime: stable public policy identifier
  applicationMode: standalone | multi-user
  subject:
    authenticated: Boolean
    displayName: optional public text
  capabilities: ordered public capability names
```

The concrete Scala types are introduced in SW-02. Public field names are fixed
by the specification before implementation.

## Application Mode

Web application mode is independent from CNCF `OperationMode` and from page
display/layout mode. It controls which execution-owned identity and formatting
sources participate in Web projection resolution.

- `standalone` selects configured application/startup formatting and then
  runtime defaults.
- `multi-user` selects authenticated-user preferences, then configured
  application fallbacks, then runtime defaults.

The mode must be selected by explicit Web execution policy. It is not inferred
from whether one request happens to be authenticated. The compatibility
default for a Static Web App without that policy is `standalone`.

Descriptor/configuration syntax for selecting the policy is implemented in
SW-02/SW-03. The semantic values above are the stable contract.

## Locale and Timezone Resolution

The projection consumes the effective formatting policy selected by CNCF. It
does not independently parse browser state.

Standalone resolution is:

```text
allowed explicit display override
  -> configured application/startup value
  -> CNCF execution/runtime default
```

Multi-user resolution is:

```text
allowed explicit display override
  -> authenticated-user public preference
  -> configured application fallback
  -> CNCF execution/runtime default
```

An explicit display override is considered only when Web policy enables it.
`Accept-Language` is considered only when HTTP language negotiation is
explicitly enabled and no execution-owned value has resolved the field. It
cannot supersede configured standalone formatting or an authenticated-user
preference.

`navigator.language` and `localStorage` are browser state and never
participate in server resolution.

## Display Format Projection

`format.date` and `format.dateTime` are stable public policy identifiers. They
are not `DateTimeFormatter.toString`, implementation-specific pattern dumps,
or diagnostic timestamp formats.

The execution profile's date/time format policy and the Web display-format
policy resolve these identifiers. SW-02/SW-03 must define the initial
identifier vocabulary and deterministic fallback. Server-side formatting and
client-side presentation adapters consume the same identifiers.

Operational logs and diagnostics continue to use the existing ISO policy and
are never changed by page projection.

## Subject Projection

The subject projection is deliberately smaller than `SecuritySubject` or
`AuthComponent.SessionSummary`.

- `authenticated` is derived from the effective authenticated request
  subject.
- `displayName` is taken only from an explicit public field in the typed
  authentication/session projection.
- internal principal IDs, subject IDs, session IDs, access tokens, security
  levels, roles, privileges, and arbitrary attributes are not fallbacks for
  display name.

If no public display name is available, the field is absent/null. CNCF does
not expose an internal identifier to fill the UI.

## Public Capability Projection

The execution context may contain capabilities that reveal internal policy or
resource names. Static Web projection is therefore default-deny.

The effective subject capabilities are intersected with an explicit Web-public
capability allowlist. The result is normalized, deduplicated, and sorted.
Neither roles nor privileges are projected as capability substitutes.

The allowlist controls visibility metadata only. It does not grant authority,
replace server authorization, or allow client-side checks to become an
enforcement boundary.

## Rendering

Simple values may also be reflected in semantic attributes such as
`html[lang]` and `data-textus-locale`. Compound data is emitted once in a
stable script-data container:

```html
<script id="textus-page-context" type="application/json">
  {"execution": {}}
</script>
```

The renderer serializes the typed projection and escapes it for HTML
script-data context. It must neutralize sequences that can terminate a script
element or reinterpret JSON as markup. Application templates do not assemble
the JSON by string concatenation.

`html[lang]`, semantic attributes, and embedded JSON are generated from the
same projection instance.

## Security Boundary

The execution projection must not contain:

- session identifiers, tokens, cookies, or authentication headers;
- internal principal or subject identifiers;
- roles, privileges, security levels, or non-public capabilities;
- component/runtime configuration or secret references/values;
- datastore, provider, deployment, or topology details;
- CallTree, trace, metric payload, or debug diagnostics; or
- arbitrary request, query, session, or provider attributes.

The existing page-context extension mechanism remains separately reviewed by
its own contract. Its values are not automatically included in the execution
JSON.

## Integration Boundary

ArtScene consumes the projection before its first application render. It may
then remove browser-locale, local-storage, startup-locale REST, and delayed
visibility workarounds. `DescribeApplication` remains responsible for business
state such as workspace and feature availability.

ArtScene-specific translation catalogs, business state, and preference editing
remain outside CNCF.

## Implementation Sequence

1. Introduce typed projection and public policy models.
2. Resolve execution formatting and safe subject/capability metadata.
3. Bind the typed projection to the framework-owned page context.
4. Emit semantic attributes and escaped JSON.
5. Prove policy precedence, first-render consistency, and redaction.
6. Update developer guidance and run the ArtScene integration smoke.

## Related Documents

- `docs/design/web-layer.md`
- `docs/design/execution-context.md`
- `docs/design/execution-determinism.md`
- `docs/spec/static-web-execution-context-projection.md`
- `docs/phase/phase-38.md`
