# Static Web Application Specification

## Status

IN PROGRESS. This specification defines the target contract for CNCF Static
Web Applications. The typed page View projection described in SWA-3 is
implemented; locale message resolution, aggregate form binding, and complete
cache policy remain follow-up work. This specification extends, but does not
replace, the execution-context projection contract in
`docs/spec/static-web-execution-context-projection.md`.

## SWA-1: Delivery Model

A Static Web Application is the normal human-facing delivery model for a CNCF
component application. CNCF MUST render the initial document on the server from
the resolved request execution context, the route/page query, locale message
catalogs, and a page View model.

The initial document MUST be meaningful without JavaScript and MUST NOT require
a browser REST or Form API fan-out to determine its primary page content,
locale, timezone, authorization-aware navigation, or workspace visibility.

REST and Form API remain canonical machine-facing operation surfaces. They are
not the required bootstrap mechanism for ordinary Static Web page rendering.

This is a scalability boundary, not merely a no-JavaScript fallback preference.
Browser REST hydration multiplies HTTP dispatch, serialization, authorization,
and datastore work across page regions and users. A normal page MUST compose
its data once on the server and send one document response instead of producing
an N+1 browser request pattern.

## SWA-2: Execution Context and Localization

The renderer MUST resolve the effective `ExecutionContext` before rendering the
document and MUST use that same resolved context for the locale message catalog,
`html[lang]`, `Content-Language`, application-facing time formatting,
subject-safe navigation, and page View authorization.

The execution-context projection specification owns standalone, multi-user,
display-override, and `Accept-Language` precedence. Browser
`navigator.language`, browser storage, and client-side REST MUST NOT select or
override the normal display locale.

An enabled explicit language override is a new server-rendered request in the
selected locale. It MUST NOT require translating an already-rendered document
in place. Templates MUST use framework-provided locale message resolution; a
component MUST NOT emit a default-language shell and replace static text with
JavaScript after first paint.

Components publish application message catalogs through
`Component.webMessageCatalogs`. CNCF selects root, language, and exact-locale
catalog layers from the already resolved request execution locale and exposes
the result as server-only `${message.<key>}` template properties. The exact
locale layer overrides the language layer, which overrides the root catalog;
later assembled component catalogs override earlier catalogs at the same
layer. Runtime-context messages remain the base layer. Message catalogs are
not added to the public page-context JSON merely to support server rendering.
A runtime message map participates only when its declared locale is root, the
selected locale, or the selected locale's language; messages from an unrelated
runtime locale MUST NOT leak into another user's rendered page.

The Static Web renderer carries the selected locale as typed page response
metadata. The HTTP adapter MUST use that metadata for `Content-Language`; it
MUST NOT independently negotiate or infer the response language after
rendering. Therefore template messages, `html[lang]`, `Content-Language`, and
the execution timezone are projections of the same resolved request context.

## SWA-3: Page View Binding

Each normal application page MUST bind to one explicit read-side page View or
page-context query. The binding receives the resolved execution context and
validated page query and returns a typed, template-safe page model.

The runtime binding uses `WebPageContextProvider.queries` to compose component
read-side queries once on the server. A provider returns the page model in
`WebPageContext.view` as a `Record`; application data MUST NOT replace the
framework-owned `WebPageContext.execution` projection. Multiple provider
results are merged by top-level View field with later providers replacing only
fields of the same name.

The Static Web renderer exposes that model to server-side widgets as
`pageContext.view`. Nested widget sources such as
`pageContext.view.exhibitions` resolve typed arrays and records before HTML is
sent. The renderer also emits the same model under `view` in the single
`textus-page-context` JSON script block. This second representation is for
bounded progressive enhancement and MUST NOT be used to replace meaningful
server-rendered primary content.

Both execution and View data use JSON script-data escaping. Components remain
responsible for returning only subject-authorized, Web-safe View fields; the
page model is not a serialization of component internals or the complete
`ExecutionContext`.

The Web renderer MUST NOT reconstruct page state through multiple unrelated
domain operations for headers, navigation, counters, filters, and primary
content. Templates render presentation only and MUST NOT contain domain
mutation, authorization, or aggregate/view policy logic.

## SWA-4: Aggregate Form Binding

Normal user-facing mutations MUST bind HTML forms to component aggregate
commands. CNCF MUST perform validation and authorization on the server and
complete successful form submissions with Post/Redirect/Get.

Static Web templates MAY use `textus:operation-form` to generate a typed HTML
form from component operation schema and `WebDescriptor` metadata. The widget
posts to the canonical `/form` ingress and MUST NOT depend on JavaScript or
`/form-api` for normal submission.

The redirected page MUST render the committed View and an appropriately
localized outcome or flash message. Validation failures MUST return a
localized, accessible form response without requiring JavaScript.

An operation form MAY declare `successMessageKey` and `failureMessageKey` in
its `WebDescriptor.Form` entry. When the corresponding redirect is selected,
CNCF carries only that bounded message key and a framework-selected visual
variant in a short-lived, component-scoped, `HttpOnly`, `SameSite=Lax` cookie.
The next Static Web document resolves the key through the already selected
request locale catalog, exposes it as server-only `pageContext.flash.*`
template properties, and expires the cookie in the same response. The cookie
MUST NOT carry operation result bodies, display text, subject data, or
authorization state.

Only message keys explicitly declared by a form belonging to the target
component are eligible for rendering. A flash is presentation feedback, not
proof that a command succeeded; authorization and committed state remain
owned by the command and redirected View. Static templates render the feedback
with `textus:flash`, so no browser REST request or client-side locale pass is
needed after Post/Redirect/Get.

CNCF owns the CSRF value used by `textus:operation-form`; page providers,
query parameters, and application templates do not choose it. A rendered form
receives the same strong token in a hidden `csrf` field and a runtime-scoped,
`HttpOnly`, `SameSite=Lax` cookie. The `/form` ingress MUST reject a missing,
mismatched, malformed, or stale token with HTTP 403 before validation or
operation dispatch. CSRF context remains framework context and MUST be removed
from operation arguments.

For an authenticated request, the token is cryptographically bound to the
current session identifier and therefore becomes invalid after session
rotation. For standalone requests without an authentication session, CNCF uses
the same server-issued random token as a double-submit cookie. Both forms are
stateless at the Web runtime, so verification does not require JVM-local
session or token storage and remains valid when GET and POST reach different
runtime instances.

The CSRF cookie scope is the same-origin CNCF Web runtime, not an individual
component. This permits a Static Web page to use the documented
`textus:operation-form component="..."` contract for another assembled
component; target-component authorization remains independent and mandatory.

Generated entity CRUD, automatic REST, and Form API remain valid for
administration, automation, diagnostics, and explicit integration. They are
not the required normal Web mutation model when an aggregate command exists.

## SWA-5: Progressive Enhancement

JavaScript MAY enhance a bounded page region after the server-rendered page is
usable. An enhancement MUST preserve a link/form/page fallback and MUST NOT
become the authority for domain policy, authorization, persistence, execution
context, or initial page rendering.

Permitted examples include local visualization controls, input assistance,
autocomplete, a genuinely live notification badge, long-running command
progress, and Canvas or another browser-only rendering surface.

An enhancement that renders asynchronously SHOULD receive its initial model in
the HTML response through typed page data or a safe script-data block. It MUST
NOT add a request merely to retrieve data that was available when the page was
rendered. A subsequent request is permitted only when freshness, command
progress, or browser-only capability makes it necessary, and it MUST be scoped
to that small region.

Application-wide client routing and client REST hydration are separate SPA
architecture choices, not Static Web Application behavior.

## SWA-6: Subject Safety and Caching

The renderer MUST authorize and scope a subject-specific page View before it is
rendered. It MUST NOT expose another subject's review, subscription, account,
notification, or administration data through markup, page context, client
bootstrap data, or a shared response cache.

CNCF MUST define cache policy for public, standalone, and authenticated
subject-specific pages. Subject-specific pages MUST be private or otherwise
keyed so they cannot be served across subjects. Components MUST NOT treat
browser-hidden controls as an authorization boundary.

The default Static Web cache policy is:

- a multi-user anonymous GET without a session or request/response cookie MAY
  use `Cache-Control: public, max-age=0, must-revalidate` and MUST vary on
  `Accept-Language`, `Cookie`, `Authorization`, and `X-Textus-Session` so a
  subject-bearing request cannot reuse the anonymous representation;
- standalone documents use `Cache-Control: private, no-store`, because the
  local operator remains the effective subject without an authentication
  session;
- authenticated or session-associated documents use
  `Cache-Control: private, no-store`;
- a document that consumes flash state, emits a CSRF token, or otherwise
  carries request/response cookie state uses `private, no-store`; and
- an unknown or unresolved execution mode fails closed to `private, no-store`.

Applications MUST NOT weaken this policy merely because markup omits a visible
subject identifier. Cache classification is derived from resolved execution
and HTTP state before the response leaves CNCF.

## SWA-7: Required Evidence

Every Static Web Application implementation MUST provide executable evidence
that:

- Japanese and English execution contexts receive matching localized HTML on
  first paint, without language-replacement flicker;
- ordinary initial page loads make no REST/Form API requests for primary page
  content or execution-context discovery;
- normal form actions work with JavaScript disabled and use PRG;
- page Views and aggregate forms enforce subject and capability boundaries on
  the server; and
- direct links, reload, browser back/forward, validation failures, and explicit
  display overrides preserve the server-rendered contract.

## Related Contracts

- `docs/design/web-layer.md`
- `docs/spec/static-web-execution-context-projection.md`
- `docs/design/static-web-execution-context-projection.md`
- `docs/notes/cncf-developer-guide.md`
