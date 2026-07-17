# Static Web Execution-Context Projection Handoff (2026-07-17)

## Position of This Record

This journal entry records an implementation handoff. It is not yet a
normative CNCF contract. Promote the accepted contract to `docs/design` and
`docs/spec` before treating it as a stable public Web API.

Planning annotation (Jul. 17, 2026): this handoff is now tracked by Phase 38,
`Static Web Execution-Context Projection`. The phase definition and checklist
are `docs/phase/phase-38.md` and `docs/phase/phase-38-checklist.md`. This journal
remains the non-normative source record; Phase 38 must promote accepted
decisions to design/spec documents and executable specifications.

## Problem

Static Web Apps currently have no synchronous, safe projection of the resolved
`ExecutionContext` when their HTML is rendered. Applications therefore tend to
choose locale and other initial presentation state in the browser, or wait for
an application REST operation after the page has loaded.

Both are incorrect for normal application rendering:

- browser `navigator.language` or `Accept-Language` can override the intended
  standalone startup locale or authenticated user's locale;
- `localStorage` makes presentation state browser-local rather than
  execution-context-owned;
- waiting for REST makes an English static shell visible before Japanese
  translations are applied;
- hiding a page until an application-specific REST response arrives is only an
  application workaround, not a framework solution.

ArtScene exposed this directly: the page was emitted with English static text,
then changed to Japanese only after its JavaScript obtained application state.

## Confirmed Current State

- `IngressSecurityResolver._restore_formatting_context` currently considers
  request attributes and `Accept-Language` while resolving formatting.
- `Http4sHttpServer` and the Static Web rendering path already construct a
  page context for authentication and navigation-related data.
- That page context does not expose a deliberate, safe execution-context
  projection for static application HTML.
- `ExecutionContext` is already resolved on the server before HTML is
  returned; no client-side discovery is needed.

The locale precedence used for normal static Web rendering must be explicit.
Browser language negotiation must not silently override the application
execution policy.

## Required Contract

CNCF Static Web rendering must provide a safe, resolved execution-context
projection as `pageContext.execution` to application templates.

The initial public fields are:

```text
pageContext.execution.locale
pageContext.execution.timezone
pageContext.execution.format.date
pageContext.execution.format.dateTime
pageContext.execution.applicationMode
pageContext.execution.subject.authenticated
pageContext.execution.subject.displayName
pageContext.execution.capabilities
```

The minimal first slice may expose only:

```text
pageContext.execution.locale
pageContext.execution.timezone
pageContext.execution.subject.authenticated
pageContext.execution.capabilities
```

The renderer must make the projection available during first HTML generation.
For simple static templates that can be attributes such as:

```html
<html lang="${pageContext.execution.locale}">
<body data-textus-locale="${pageContext.execution.locale}">
```

For compound values, use one escaped JSON script block rather than an
unbounded set of attributes:

```html
<script id="textus-page-context" type="application/json">
  ${pageContext.execution.json}
</script>
```

The JSON must be correctly escaped for an HTML script-data context. It is a
projection, not a serialization of `ExecutionContext` itself.

## Locale and Timezone Resolution

The renderer consumes the execution context that CNCF has resolved for the
request. The normal policy is:

1. standalone mode: configured startup locale, such as
   `textus.execution.locale`;
2. multi-user mode: authenticated user's locale preference;
3. unset: CNCF runtime default locale.

An explicit `?lang=ja` or `?lang=en` may remain an intentional temporary
display override, if the Web layer supports it. It must not become the normal
source of application locale.

`Accept-Language` and `navigator.language` must not override the above normal
policy. Review the existing `IngressSecurityResolver` precedence accordingly;
if HTTP language negotiation remains supported, it needs an explicit opt-in
policy that cannot supersede configured standalone or authenticated-user
formatting.

Timezone and date/time format values follow the same resolved context. The
framework must expose application display formats separately from log/debug
formats. For example, a Japanese application may use `7月17日 2時02分 (JST)`,
while diagnostic logs retain ISO timestamps.

## Security Boundary

`pageContext.execution` contains only data safe for page rendering. It must
not include:

- session identifiers, tokens, cookies, or authentication headers;
- internal principal identifiers;
- datastore connections or component configuration;
- runtime secrets or internal topology;
- call trees, trace data, or debug diagnostics.

Display name, authenticated state, and public capabilities must be explicitly
projected, not copied from arbitrary request/session attributes.

## ArtScene Integration Boundary

After this CNCF contract exists, ArtScene must:

1. read `pageContext.execution` synchronously before its first application
   render;
2. select translations, `html[lang]`, navigation visibility, and date/time
   formatting from that context;
3. use `DescribeApplication` only for business application state such as
   workspace and feature visibility;
4. remove browser-locale/local-storage locale selection, REST-based initial
   locale discovery, and the full-page visibility workaround introduced to
   suppress the initial language flash.

The CNCF change must be completed before ArtScene commits its current
application-level workaround as a permanent design.

## Suggested Implementation Order

1. Define an immutable Web-safe projection type near the existing static page
   context rather than exposing `ExecutionContext` directly.
2. Resolve its locale/timezone/format values from the effective request
   execution context after security/user context has been applied.
3. Add template/renderer support for `pageContext.execution` and escaped JSON
   emission.
4. Adjust locale precedence so configured standalone and authenticated-user
   settings are not overridden by browser negotiation.
5. Add runtime and static-Web executable specifications.
6. Update the static Web developer documentation and promote the settled
   contract to design/spec documents.

## Acceptance Criteria

- A standalone static Web app's first HTML response uses the configured
  execution locale.
- A multi-user static Web app's first HTML response uses the authenticated
  user's locale.
- The static renderer emits only the defined safe projection.
- `html[lang]` and the embedded page context agree.
- Application JavaScript needs no additional REST call to learn initial locale,
  timezone, authentication state, or public capabilities.
- Browser language does not override normal execution policy.
- Japanese application date/time output can use the resolved application format
  from first render; ISO remains available for diagnostics.
- Renderer tests prove that secrets and internal identifiers are absent.
- ArtScene can remove its temporary delayed-render locale workaround without a
  first-paint English-to-Japanese flash.

## Test Handoff

Add focused tests for:

- standalone configured locale versus conflicting `Accept-Language`;
- authenticated user locale versus conflicting `Accept-Language`;
- runtime default fallback when neither is configured;
- JSON script escaping for hostile display names and locale-adjacent values;
- absence of tokens, internal IDs, datastore fields, and debug context;
- static template output containing the projection before client JavaScript
  executes.

ArtScene should then add an integration smoke that fetches Japanese standalone
HTML and asserts its initial `lang`, embedded context, navigation text, and
application date format before any REST call.
