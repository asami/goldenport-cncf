# Phase 38 - Static Web Execution-Context Projection

Stage Status:
- Current status: CLOSED
- Current step: Complete
- Start condition: Phase 36 is closed and the existing Static Web rendering
  path can supply the effective request execution context.
- Dependency relation: Phase 38 may start independently of Phase 37.
- Owner: CNCF Static Web rendering and execution-context projection.
- Update rule: Update this block and `phase-38-checklist.md` whenever a stable
  work-item state changes. Close the phase only after the projection contract,
  locale policy, rendering integration, safety evidence, and documentation are
  complete.

status = closed

## 1. Purpose

Phase 38 provides Static Web Apps with a synchronous, Web-safe projection of
the effective request `ExecutionContext` during first HTML generation. Static
application pages can therefore render locale, timezone, public subject state,
public capabilities, and application display formats without browser-owned
locale selection or an application-specific startup REST request.

ArtScene is the first integration driver. The resulting contract is a CNCF
Web platform feature and must remain independent of ArtScene application
models and temporary delayed-render workarounds.

## 2. Reused Foundations

- Phase 21 provides the Static Web application and Web composition baseline.
- Phase 28 provides semantic Web UI and stable generated DOM foundations.
- Phase 31 provides deterministic execution context, locale/time, and request
  execution capabilities.
- Phase 36 confirms that component-visible runtime facts are explicit
  execution-context capabilities rather than ambient browser/JVM state.
- Existing Web authentication and formatting resolution already run before a
  static page is returned; Phase 38 projects the safe resolved result.

## 3. Scope

- Define an immutable Web-safe execution projection. Do not serialize
  `ExecutionContext` itself.
- Add `pageContext.execution` to Static Web first-render context.
- Project these initial public fields:
  - `locale`;
  - `timezone`;
  - `format.date` and `format.dateTime`;
  - `applicationMode`;
  - `subject.authenticated` and `subject.displayName`; and
  - explicitly public `capabilities`.
- Emit `html[lang]`, stable semantic attributes, and one escaped JSON
  script-data block from the same projection.
- Define mode-aware locale and timezone resolution so configured standalone
  state and authenticated-user preferences are not overridden by browser
  negotiation.
- Keep application display formats distinct from ISO log/debug formats.
- Integrate ArtScene as a downstream smoke driver after the CNCF contract is
  executable.

## 4. Security and Compatibility Boundaries

- The projection must not contain session identifiers, tokens, cookies,
  authentication headers, internal principal identifiers, secrets, component
  configuration, datastore details, topology, call trees, traces, or debug
  diagnostics.
- Display name, authentication state, and capabilities are projected from
  explicit safe fields. Arbitrary request/session attributes are not copied.
- JSON is escaped for HTML script-data context, including hostile text that
  could terminate or alter the script element.
- `Accept-Language` is not a normal authority over an execution-owned locale.
  HTTP language negotiation requires an explicit opt-in policy and cannot
  supersede configured standalone or authenticated-user formatting.
- `navigator.language` and `localStorage` are not CNCF execution-context
  sources.
- `DescribeApplication` remains a business-state operation; it is not used to
  discover initial execution locale, timezone, authentication, or capability
  state.
- This phase does not expose a general-purpose client serialization of
  `ExecutionContext`.

## 5. Planned Work Stack

- A (DONE): SW-01 - Audit Static Web page context and freeze the normative
  projection and resolution contract.
- B (DONE): SW-02 - Implement the immutable Web-safe execution projection.
- C (DONE): SW-03 - Implement mode-aware locale, timezone, and display-format
  resolution.
- D (DONE): SW-04 - Add first-render template and escaped JSON projection.
- E (DONE): SW-05 - Integrate the projection with Static Web routes and page
  context without changing application business operations.
- F (DONE): SW-06 - Add security, locale-precedence, and hostile-input
  executable specifications.
- G (DONE): SW-07 - Update Static Web developer guidance and validate the
  ArtScene integration handoff.
- H (DONE): SW-08 - Run full verification, review, and close Phase 38.

## 6. Locale and Timezone Resolution Contract

Resolution is based on the effective execution policy for the application
mode:

- standalone mode uses configured application/startup locale and timezone,
  then CNCF runtime defaults;
- multi-user mode uses authenticated-user preferences, then configured
  application fallbacks, then CNCF runtime defaults;
- an explicit display override such as `lang` is considered only when enabled
  by Web policy; and
- `Accept-Language` is considered only by an explicit HTTP negotiation policy
  and never overrides an already resolved execution-owned value.

The same resolved values populate `html[lang]`, semantic DOM attributes, and
the embedded JSON projection.

## 7. Public Rendering Contract

Static page templates receive `pageContext.execution`. Compound projection
data is emitted in one escaped JSON block with a stable identifier:

```html
<script id="textus-page-context" type="application/json">
  {"execution": {}}
</script>
```

The example shows the stable container only. Runtime values are generated from
the typed projection and escaped by the renderer; templates must not build the
JSON with string concatenation.

## 8. Completion Conditions

Phase 38 closes only when:

- standalone first HTML uses configured execution locale and timezone despite
  conflicting browser language headers;
- multi-user first HTML uses authenticated-user preferences despite
  conflicting browser language headers;
- runtime defaults apply deterministically when no stronger execution-owned
  value exists;
- `html[lang]`, semantic attributes, and embedded execution JSON agree;
- application JavaScript needs no startup REST call for the projected fields;
- hostile display values cannot escape the JSON script-data container;
- executable specifications prove tokens, internal identifiers, secrets,
  configuration, datastore fields, and debug context are absent;
- application display date/time formats are available at first render while
  diagnostic timestamps retain their existing ISO policy;
- Static Web developer documentation describes the contract; and
- the ArtScene integration smoke can remove browser-locale and delayed-render
  workarounds without a first-paint language change.

## 9. Source Record

The non-normative implementation handoff is
`docs/journal/2026/07/static-web-execution-context-projection-handoff-2026-07-17.md`.
Accepted decisions must be promoted to design/spec documents and executable
specifications before they are treated as a stable public CNCF Web contract.

SW-01 completed the runtime audit and promoted the accepted boundary to
`docs/design/static-web-execution-context-projection.md` and
`docs/spec/static-web-execution-context-projection.md`. The audit confirmed
that existing provider-owned flat page values are not a safe authority for the
new projection, `Accept-Language` currently participates too broadly in ingress
formatting, and the renderer already has the effective request execution
context before first HTML generation.

SW-02 added the framework-owned `WebExecutionProjection` model and its stable
Record/JSON projection. The implementation accepts only typed public subject
state, applies a default-deny public capability allowlist, emits canonical
locale/timezone/application-mode values, and uses stable display-format policy
identifiers instead of formatter implementation strings. Executable evidence
is in `WebExecutionProjectionSpec`, including property-based capability-order
and duplication checks.

SW-03 added `WebExecutionResolutionPolicy` and `WebExecutionResolver` with
strict configuration decoding, standalone and multi-user precedence,
opt-in-only display overrides and HTTP language negotiation, and deterministic
display-format policy mapping. Shared ingress no longer applies
`Accept-Language` implicitly. `WebExecutionResolutionSpec` and
`IngressSecurityResolverSpec` provide behavior and property-based precedence
evidence.

SW-04 added the typed framework member to `WebPageContext`, safe first-render
JSON and locale semantics, fragment normalization, reserved-element
replacement, and provider-merge isolation. SW-05 connected the projection to
component-owned Static Web request routes through the selected component
runtime `ExecutionContext`; it preserves structured policy failures and does
not call application business operations. Executable runtime evidence is in
`StaticWebExecutionProjectionIntegrationSpec`.

SW-06 added integrated runtime evidence for authenticated-user precedence,
execution-owned fallback formatting, public capability allowlisting, hostile
public display text, excluded security/runtime data, and page-context ordering
before application JavaScript. The template projection now preserves leading
head metadata such as `meta[charset]` and installs its framework-owned context
before the first application script rather than after startup code.

SW-07 documented `#textus-page-context` and its `execution` member as the
stable browser contract. ArtScene now applies the projected execution locale
synchronously before its first request and no longer depends on browser
locale, local storage, application-description locale, or hidden-until-fetch
rendering for initial localization.

## 10. Closure Record

Phase 38 closed on Jul. 17, 2026.

- The focused execution projection, resolution, template, runtime,
  integration, and ingress-security suites passed 48 tests.
- `sbt --batch Test/compile` passed and the full CNCF suite passed 1,986 tests
  in 289 suites with no failures.
- ArtScene's maintained role/browser acceptance proved Japanese execution
  locale is active before the first application request; its full suite passed
  335 tests in 38 suites with no failures.
- Scoped review found no actionable Static Web execution-projection finding.
- Deferred scope remains client-side preference editing, application-private
  page metadata, SPA-only rendering, and ArtScene-specific translation or
  business-state development.
